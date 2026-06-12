package com.gulfnet.restaurantmanagement.service.impl;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.HashSet;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.regex.Pattern;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import com.gulfnet.shared_library.model.request.RestaurantFailedRecord;
import com.gulfnet.restaurantmanagement.service.BulkRestaurantUploadService;
import com.gulfnet.restaurantmanagement.util.MessageUtil;
import com.gulfnet.restaurantmanagement.validator.RestaurantBulkUploadValidator;
import com.gulfnet.restaurantmanagement.validator.RestaurantCsvFileValidator;
import com.gulfnet.shared_library.util.BulkUploadImageFilenameUtils;
import com.gulfnet.shared_library.util.ImageThumbnailUtil;
import com.gulfnet.shared_library.config.AWSService;
import com.gulfnet.shared_library.entity.BulkUpload;
import com.gulfnet.shared_library.entity.Restaurant;
import com.gulfnet.shared_library.entity.RestaurantTranslation;
import com.gulfnet.shared_library.entity.User;
import com.gulfnet.shared_library.enums.BulkUploadStatus;
import com.gulfnet.shared_library.enums.EntityStatus;
import com.gulfnet.shared_library.enums.QrCodeType;
import com.gulfnet.shared_library.enums.UploadType;
import com.gulfnet.shared_library.exception.ValidationException;
import com.gulfnet.shared_library.model.request.BulkRestaurantUploadRequest;
import com.gulfnet.shared_library.model.request.CustomMultipartFile;
import com.gulfnet.shared_library.model.response.dto.BulkUploadWithPresignedUrls;
import com.gulfnet.shared_library.model.response.dto.ResponseDto;
import com.gulfnet.shared_library.repository.BulkUploadRepository;
import com.gulfnet.shared_library.repository.RestaurantGroupRepository;
import com.gulfnet.shared_library.repository.RestaurantRepository;
import com.gulfnet.shared_library.repository.RestaurantTranslationRepository;
import com.gulfnet.shared_library.repository.UserRepository;
import com.gulfnet.restaurantmanagement.config.LanguageConfiguration;
import com.opencsv.CSVReader;

import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class BulkRestaurantUploadServiceImpl implements BulkRestaurantUploadService {

    private final RestaurantGroupRepository restaurantGroupRepository;
    private final RestaurantRepository restaurantRepository;
    private final RestaurantTranslationRepository restaurantTranslationRepository;
    private final BulkUploadRepository bulkUploadRepository;
    private final UserRepository userRepository;
    private final MessageUtil messageUtil;
    private final AWSService awsService;
    private final RestaurantBulkUploadValidator restaurantValidator;
    private final RestaurantCsvFileValidator restaurantCsvFileValidator;
    private final LanguageConfiguration languageConfiguration;
    private final ImageThumbnailUtil imageThumbnailUtil;

    
    private static final int BATCH_SIZE = 50;
    private static final Pattern GST_NUMBER_PATTERN = Pattern.compile("^[A-Z0-9]{1,30}$");

    /**
     * Get restaurant bulk upload headers dynamically based on language configuration
     */
    public String[] getRestaurantBulkUploadHeaders() {
        return restaurantCsvFileValidator.getDynamicHeaders();
    }
    
    /**
     * Get supported languages from configuration
     */
    private List<LanguageConfiguration.LanguageConfig> getSupportedLanguages() {
        return languageConfiguration.getSupportedLanguages() != null ? 
            languageConfiguration.getSupportedLanguages() : new ArrayList<>();
    }

    /**
     * Downloads a CSV template file for bulk restaurant uploads.
     * The template includes dynamic headers based on supported languages and sample data.
     * Sets appropriate HTTP headers for CSV file download with UTF-8 encoding and BOM.
     *
     * @param response the HTTP servlet response to write the CSV template to
     * @return a ResponseEntity with status OK
     * @throws IOException if writing to the response fails
     */
    @Override
    public ResponseEntity<Void> downloadRestaurantTemplate(HttpServletResponse response) throws IOException {
        String filename = "restaurant_bulk_upload_template.csv";

        response.setContentType("text/csv");
        response.setCharacterEncoding("UTF-8");
        response.setHeader("Content-Disposition", "attachment; filename=\"" + filename + "\"");

        response.getWriter().write('\ufeff');

        String[] dynamicHeaders = getDynamicHeaders();

        try (CSVPrinter csvPrinter = new CSVPrinter(
                response.getWriter(),
                CSVFormat.DEFAULT.builder().setHeader(dynamicHeaders).build())) {

            List<String> sampleData = createSampleData();
            csvPrinter.printRecord(sampleData);

            csvPrinter.flush();
        }

        return ResponseEntity.ok().build();
    }


    /**
     * Processes a bulk restaurant upload from a CSV file, optionally with a ZIP file containing restaurant logos.
     * Validates the CSV file, extracts images from ZIP if provided, uploads the CSV to S3,
     * creates a bulk upload record, and processes restaurants asynchronously.
     * Returns pre-signed URLs for the uploaded CSV and error CSV (if any failures occurred).
     *
     * @param file the CSV file containing restaurant data
     * @param imageZipFile optional ZIP file containing restaurant logo images
     * @param userId the ID of the user performing the bulk upload
     * @return a response containing bulk upload details and pre-signed URLs for CSV files
     * @throws IOException if file processing fails
     * @throws ResponseStatusException if validation fails or file is empty
     */
    @Override
    @CacheEvict(value = "restaurants", allEntries = true)
    public ResponseDto<BulkUploadWithPresignedUrls> processRestaurantBulkUpload(MultipartFile file, MultipartFile imageZipFile, String userId) throws IOException {
        Locale userLocale = LocaleContextHolder.getLocale();
        String language = userLocale.getLanguage();


        if (file == null || file.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                messageUtil.getMessage("bulk.restaurant.upload.file.required", userLocale));
        }

        UUID currentUserId = UUID.fromString(userId);

        // Process image ZIP file if provided
        Map<String, byte[]> imageMap = null;
        Map<String, String> imageMapping = null;
        if (imageZipFile != null && !imageZipFile.isEmpty()) {
            try {
                imageMapping = new HashMap<>();
                imageMap = extractImagesFromZip(imageZipFile, userLocale, imageMapping);
            } catch (Exception e) {
                log.error("Failed to extract images from ZIP: {}", e.getMessage(), e);
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, 
                        messageUtil.getMessage("bulk.upload.zip.extraction.failed", userLocale));
            }
        }

        String s3Url;

        String originalFileName = file.getOriginalFilename();
        String fileExtension = "";

        if (originalFileName != null && originalFileName.contains(".")) {
            fileExtension = originalFileName.substring(originalFileName.lastIndexOf("."));
        }

        String baseName;
        if (originalFileName != null && originalFileName.contains(".")) {
            baseName = originalFileName.substring(0, originalFileName.lastIndexOf("."));
        } else {
            baseName = originalFileName != null ? originalFileName : "upload";
        }

        String timestamp = LocalDateTime.now(ZoneOffset.UTC).format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
        String newFileName = baseName + "_" + timestamp + fileExtension;
        String s3Key = "bulk-upload/restaurants/" + currentUserId + "/" + newFileName;

        try {
            s3Url = awsService.uploadFile(file.getInputStream(), s3Key, file.getSize());
        } catch (Exception e) {
            log.error("Failed to upload restaurant file to S3", e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                messageUtil.getMessage("bulk.restaurant.upload.s3.upload.failed", userLocale));
        }

        BulkUpload bulkUpload = new BulkUpload();
        bulkUpload.setCreatedBy(currentUserId);
        bulkUpload.setStatus(BulkUploadStatus.PENDING);
        bulkUpload.setFilePath(s3Url);
        bulkUpload.setCreatedAt(OffsetDateTime.now(ZoneOffset.UTC));
        bulkUpload.setUpdatedAt(null);
        bulkUpload.setUploadType(UploadType.RESTAURANT);

        bulkUpload = bulkUploadRepository.save(bulkUpload);

        List<String[]> records = readCsvFile(file);

        BulkUploadWithPresignedUrls responseDto = BulkUploadWithPresignedUrls.builder()
            .id(bulkUpload.getId())
            .originalFileName(originalFileName)
            .originalFilePresignedUrl(awsService.getPreSignedUrl(bulkUpload.getFilePath()))
            .status(bulkUpload.getStatus().name())
            .createdAt(bulkUpload.getCreatedAt() != null ? bulkUpload.getCreatedAt().toLocalDateTime() : null)
            .createdBy(bulkUpload.getCreatedBy())
            .build();

        // Process restaurants asynchronously with or without images
        if (imageMap != null && imageMapping != null) {
            processRestaurantsWithImagesAsync(records, imageMap, imageMapping, userId, language, bulkUpload.getId());
        } else {
            processRestaurantsAsync(records, userId, language, bulkUpload.getId());
        }

        return ResponseDto.<BulkUploadWithPresignedUrls>builder()
                .data(responseDto)
                .message(messageUtil.getMessage("bulk.upload.initiated", userLocale))
                .build();
    }

    /**
     * Generate error file name with timestamp for failed restaurant records
     */
    private static String generateErrorFileNameWithTimestamp(MultipartFile file) {
        String originalFilename = file.getOriginalFilename();
        if (originalFilename == null) {
            originalFilename = "Error";
        }
        String extension = originalFilename.contains(".")
            ? originalFilename.substring(originalFilename.lastIndexOf("."))
            : ".csv";
        String baseName = originalFilename.contains(".")
            ? originalFilename.substring(0, originalFilename.lastIndexOf("."))
            : originalFilename;

        String timestamp = LocalDateTime.now(ZoneOffset.UTC).format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));

        return baseName + "_error_" + timestamp + extension;
    }




    /**
     * Initializes bulk upload processing: validates bulk upload, extracts header, filters non-blank rows.
     * 
     * @param records CSV records (first row is header)
     * @param bulkUploadId The bulk upload ID
     * @param userLocale User locale for error messages
     * @return BulkUploadProcessingContext containing bulkUpload, errorRecords, header, and nonBlankRows
     */
    private BulkUploadProcessingContext initializeBulkUploadProcessing(List<String[]> records, UUID bulkUploadId, Locale userLocale) {
        Optional<BulkUpload> bulkUploadOpt = bulkUploadRepository.findById(bulkUploadId);
        if (bulkUploadOpt.isEmpty()) {
            log.error("Bulk upload not found: {}", bulkUploadId);
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                messageUtil.getMessage("bulk.item.upload.error.not.found", userLocale));
        }

        BulkUpload bulkUpload = bulkUploadOpt.get();
        List<RestaurantFailedRecord> errorRecords = new ArrayList<>();

        String[] header = records.isEmpty() ? new String[0] : records.get(0);
        if (!records.isEmpty()) {
            records.remove(0);
        }

        List<String[]> nonBlankRows = records.stream()
                .filter(row -> !Arrays.stream(row).allMatch(s -> s == null || s.trim().isEmpty()))
                .collect(Collectors.toList());

        bulkUpload.setTotalRecordCount(nonBlankRows.size());

        return new BulkUploadProcessingContext(bulkUpload, errorRecords, header, nonBlankRows);
    }

    /**
     * Finalizes bulk upload processing: handles error files, updates status, and saves bulk upload.
     * 
     * @param bulkUpload The bulk upload entity
     * @param errorRecords List of error records
     * @param header CSV header row
     * @param nonBlankRowsCount Count of non-blank rows processed
     */
    private void finalizeBulkUploadProcessing(BulkUpload bulkUpload, List<RestaurantFailedRecord> errorRecords, 
                                             String[] header, int nonBlankRowsCount) {
        bulkUpload.setFailureRecordCount(errorRecords.size());
        bulkUpload.setSuccessRecordCount(nonBlankRowsCount - errorRecords.size());

        String originalFileNameWithTimestamp = extractFileName(bulkUpload.getFilePath());
        String originalFileName = originalFileNameWithTimestamp.replaceAll("_(\\d{8}_\\d{6})(?=\\.)", "");

        if (!errorRecords.isEmpty()) {
            MultipartFile errorFile = writeFailedRestaurantsToCsv(errorRecords, header, originalFileName);
            try {
                String errorFileName = generateErrorFileNameWithTimestamp(errorFile);
                String s3Key = "bulk-upload/restaurants/error/" + errorFileName;
                String errorFileUrl = awsService.uploadFile(errorFile.getInputStream(), s3Key, errorFile.getSize());
                bulkUpload.setErrorFilePath(errorFileUrl);
                bulkUpload.setStatus(BulkUploadStatus.FAILURE);
                bulkUpload.setReason("Bulk upload completed with errors.");
            } catch (Exception e) {
                log.error("Failed to upload error file", e);
            }
        } else {
            bulkUpload.setErrorFilePath(null);
            bulkUpload.setStatus(BulkUploadStatus.SUCCESS);
            bulkUpload.setReason("Bulk upload completed successfully.");
        }

        bulkUpload.setUpdatedAt(OffsetDateTime.now(ZoneOffset.UTC));
        bulkUploadRepository.save(bulkUpload);
    }

    /**
     * Context class for bulk upload processing to pass multiple values between methods.
     */
    private static class BulkUploadProcessingContext {
        final BulkUpload bulkUpload;
        final List<RestaurantFailedRecord> errorRecords;
        final String[] header;
        final List<String[]> nonBlankRows;

        BulkUploadProcessingContext(BulkUpload bulkUpload, List<RestaurantFailedRecord> errorRecords, 
                                   String[] header, List<String[]> nonBlankRows) {
            this.bulkUpload = bulkUpload;
            this.errorRecords = errorRecords;
            this.header = header;
            this.nonBlankRows = nonBlankRows;
        }
    }

    @Async("bulkUploadTaskExecutor")
    public void processRestaurantsAsync(List<String[]> records, String userId, String language, UUID bulkUploadId) {
        Locale userLocale = LocaleContextHolder.getLocale();

        BulkUploadProcessingContext context = initializeBulkUploadProcessing(records, bulkUploadId, userLocale);

        List<Restaurant> restaurants = createRestaurants(context.nonBlankRows, context.errorRecords, userId, context.header);
        batchInsertRestaurants(restaurants, context.errorRecords);

        finalizeBulkUploadProcessing(context.bulkUpload, context.errorRecords, context.header, context.nonBlankRows.size());
    }

    private List<String[]> readCsvFile(MultipartFile file) throws IOException {
        Locale userLocale = LocaleContextHolder.getLocale();
        try (CSVReader reader = new CSVReader(new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8))) {
            return reader.readAll();
        } catch (Exception e) {
            log.error("Error reading CSV file", e);
            throw new IOException(messageUtil.getMessage("bulk.item.upload.error.csv.read", 
                userLocale, e.getMessage()), e);
        }
    }

    private String extractFileName(String filePath) {
        return com.gulfnet.restaurantmanagement.util.FileNameUtil.extractFileName(filePath);
    }



    @Transactional
    private List<Restaurant> createRestaurants(List<String[]> rows, List<RestaurantFailedRecord> errorRecords,
                                               String currentUserId, String[] csvHeader) {
        return createRestaurantsInternal(rows, errorRecords, currentUserId, null, null, csvHeader);
    }

    /**
     * Checks whether a CSV header contains the {@code phone_number} column.
     * <p>
     * Header cells are normalized by stripping non-ASCII control characters and trimming whitespace before comparison.
     * </p>
     *
     * @param header CSV header row
     * @return {@code true} when a header cell equals {@code "phone_number"} after normalization
     */
    /**
     * Checks whether the provided CSV header contains the {@code phone_number} column.
     * <p>
     * The bulk restaurant template optionally includes {@code phone_number} after {@code gst_number}. This helper
     * normalizes header cells by stripping non-ASCII control characters and trimming whitespace before comparison.
     * </p>
     *
     * @param header CSV header row cells
     * @return {@code true} if a normalized header cell equals {@code "phone_number"}, otherwise {@code false}
     */
    private static boolean csvHeaderHasPhoneNumber(String[] header) {
        if (header == null) {
            return false;
        }
        for (String h : header) {
            String c = h != null ? h.replaceAll("[^\\x20-\\x7E]", "").trim() : "";
            if ("phone_number".equals(c)) {
                return true;
            }
        }
        return false;
    }

    private String safeGet(String[] row, int idx) {
        return (row.length > idx && row[idx] != null) ? row[idx].trim() : "";
    }

    /**
     * Creates a BulkRestaurantUploadRequest DTO from a CSV row.
     * Extracts language-specific restaurant names (based on supported languages),
     * restaurant code, restaurant group code, location fields, QR code type, status, logo name, and GST number.
     *
     * @param row the CSV row data as a string array
     * @param hasPhoneColumn whether the CSV includes a {@code phone_number} column (after {@code gst_number})
     * @return a BulkRestaurantUploadRequest DTO populated from the row data
     */
    private BulkRestaurantUploadRequest createRestaurantRequest(String[] row, boolean hasPhoneColumn) {
        BulkRestaurantUploadRequest request = new BulkRestaurantUploadRequest();
        
        List<LanguageConfiguration.LanguageConfig> languages = getSupportedLanguages();
        
        int languageIndex = 0;
        for (LanguageConfiguration.LanguageConfig lang : languages) {
            String languageCode = lang.getLanguageCode();
            String name = safeGet(row, languageIndex);
            request.setNameForLanguage(languageCode, name);
            languageIndex++;
        }
        
        int offset = languages.size();
        
        request.setRestaurantCode(safeGet(row, offset));
        request.setRestaurantGroupCode(safeGet(row, offset + 1));
        request.setCity(safeGet(row, offset + 2));
        request.setArea(safeGet(row, offset + 3));
        request.setState(safeGet(row, offset + 4));
        request.setAddressLine1(safeGet(row, offset + 5));
        request.setAddressLine2(safeGet(row, offset + 6));
        request.setLocationPin(safeGet(row, offset + 7));
        request.setQrCodeType(safeGet(row, offset + 8));
        request.setStatus(safeGet(row, offset + 9));
        request.setLogoName(safeGet(row, offset + 10));
        request.setGstNumber(safeGet(row, offset + 11));
        if (hasPhoneColumn) {
            request.setPhoneNumber(safeGet(row, offset + 12));
        } else {
            request.setPhoneNumber("");
        }
        
        return request;
    }


    /**
     * Converts a BulkRestaurantUploadRequest to a Restaurant entity.
     * Sets all restaurant fields, handles restaurant group assignment, and uploads logo/thumbnail to S3 if provided.
     *
     * @param request the bulk restaurant upload request DTO
     * @param creatorId the ID of the user creating the restaurant
     * @param imageMap optional map of image data (key: filename, value: image bytes)
     * @param imageMapping optional map of original to renamed filenames
     * @return a Restaurant entity ready to be persisted
     * @throws ValidationException if the creator user is not found
     */
    private Restaurant convertToRestaurant(BulkRestaurantUploadRequest request, UUID creatorId, 
                                                   Map<String, byte[]> imageMap, Map<String, String> imageMapping) {
        Locale userLocale = LocaleContextHolder.getLocale();

        Restaurant restaurant = new Restaurant();
        restaurant.setRestaurantCode(request.getRestaurantCode());
        restaurant.setCity(request.getCity());
        restaurant.setArea(request.getArea());
        restaurant.setState(request.getState());
        restaurant.setAddress1(request.getAddressLine1());
        restaurant.setAddress2(request.getAddressLine2());
        restaurant.setLatitude(null);
        restaurant.setLongitude(null);
        restaurant.setLocationPin(request.getLocationPin());
        restaurant.setStatus(EntityStatus.valueOf(request.getStatus().toUpperCase()));
        restaurant.setTableQrCodeType(QrCodeType.valueOf(request.getQrCodeType().toUpperCase()));
        restaurant.setGstNumber(request.getGstNumber());
        restaurant.setPhoneNumber(normalizeBulkPhone(request.getPhoneNumber()));
        restaurant.setIsDeleted(false);
        restaurant.setCreatedAt(OffsetDateTime.now(ZoneOffset.UTC));
        restaurant.setUpdatedAt(null);
        
        // Set default alert configuration (can be updated later via update API)
        // Leave alertsEnabled as null to inherit from group/account level
        restaurant.setSalesAlertThreshold(null);
        restaurant.setRefundAlertPercentage(null);
        restaurant.setCancellationAlertPercentage(null);
        restaurant.setAlertsEnabled(null);

        User creator = userRepository.findById(creatorId)
            .orElseThrow(() -> new ValidationException(
                messageUtil.getMessage("user.not.found", userLocale)));
        restaurant.setCreatedBy(creator);
        restaurant.setUpdatedBy(null);

        if (request.getRestaurantGroupCode() != null && !request.getRestaurantGroupCode().trim().isEmpty()) {
            restaurantGroupRepository.findByRestaurantGroupCodeAndIsDeletedFalse(request.getRestaurantGroupCode())
                .ifPresent(restaurant::setRestaurantGroup);
        }

        // Handle logo upload if logo name is provided
        if (request.getLogoName() != null && !request.getLogoName().trim().isEmpty() && 
            imageMap != null && imageMapping != null) {
            try {
                // Get the renamed filename from the mapping
                String renamedLogoFileName = imageMapping.get(request.getLogoName().trim());
                
                // Get image data from the map using renamed filename
                byte[] imageData = imageMap.get(renamedLogoFileName);
                
                if (imageData != null) {
                    String logoUrl = uploadLogoToS3(imageData, renamedLogoFileName, creatorId.toString());
                    restaurant.setLogoUrl(logoUrl);

                    String logoThumbUrl = uploadLogoThumbnailToS3(imageData, renamedLogoFileName, creatorId.toString());
                    restaurant.setLogoThumbnailUrl(logoThumbUrl);
                } else {
                    throw new ValidationException("Logo image data not found for restaurant: " + request.getRestaurantCode());
                }
                        
            } catch (Exception e) {
                throw new ValidationException("Logo upload failed: " + e.getMessage());
            }
        }

        Restaurant savedRestaurant = restaurantRepository.save(restaurant);

        saveRestaurantTranslations(request, savedRestaurant);

        return savedRestaurant;
    }

    private static String normalizeBulkPhone(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    /**
     * Creates a thumbnail from restaurant logo image data and uploads it to S3.
     * Generates a thumbnail using ImageThumbnailUtil and uploads it with a "thumb_" prefix.
     *
     * @param imageData the original image data as a byte array
     * @param originalFileName the original filename of the image
     * @param userId the ID of the user (used for S3 path organization)
     * @return the S3 key/URL of the uploaded thumbnail
     * @throws java.io.IOException if thumbnail creation or S3 upload fails
     */
    private String uploadLogoThumbnailToS3(byte[] imageData, String originalFileName, String userId) throws java.io.IOException {
        try (java.io.ByteArrayInputStream ignored = new java.io.ByteArrayInputStream(imageData)) {
            String extension = originalFileName.substring(originalFileName.lastIndexOf('.') + 1).toLowerCase();
            byte[] thumbBytes = imageThumbnailUtil.createThumbnail(imageData, extension);

            String baseName = originalFileName.substring(0, originalFileName.lastIndexOf('.'));
            String thumbFileName = "thumb_" + baseName + "." + extension;
            String s3Key = "restaurant/logo-images/" + thumbFileName;
            return awsService.uploadFile(new java.io.ByteArrayInputStream(thumbBytes), s3Key, thumbBytes.length);
        } catch (Exception e) {
            log.error("Failed to upload restaurant logo thumbnail to S3: {}", e.getMessage(), e);
            throw new java.io.IOException("Failed to upload thumbnail to S3: " + e.getMessage(), e);
        }
    }

    

    /**
     * Inserts restaurants into the database in batches for better performance.
     * Processes restaurants in batches of BATCH_SIZE and collects successfully inserted restaurants.
     * Errors in individual batches are logged but don't stop the overall process.
     *
     * @param restaurants the list of restaurants to insert
     * @param errorRecords the list of error records (used for tracking, not modified here)
     * @return a list of successfully inserted restaurants
     */
    @Transactional
    private List<Restaurant> batchInsertRestaurants(List<Restaurant> restaurants,
                                                List<RestaurantFailedRecord> errorRecords) {
        List<Restaurant> successRestaurants = new ArrayList<>();

        for (int i = 0; i < restaurants.size(); i += BATCH_SIZE) {
            int end = Math.min(i + BATCH_SIZE, restaurants.size());
            List<Restaurant> batch = restaurants.subList(i, end);

            try {
                restaurantRepository.saveAll(batch);
                synchronized (successRestaurants) {
                    successRestaurants.addAll(batch);
                }
            } catch (Exception e) {
                log.error("Batch insert failed for batch {}", i / BATCH_SIZE, e);
            }
        }

        return successRestaurants;
    }


    /**
     * Writes failed restaurant records to a CSV file with an additional "Error" column.
     * Includes all original data plus the error message for each failed record.
     * Returns the CSV as a MultipartFile for easy upload to S3.
     *
     * @param errorRecords the list of failed restaurant records with error messages
     * @param header the original CSV header array
     * @param originalFilename the original filename (used for naming the error file)
     * @return a MultipartFile containing the error CSV, or null if writing fails
     */
    private MultipartFile writeFailedRestaurantsToCsv(List<RestaurantFailedRecord> errorRecords, String[] header,  String originalFilename) {
        boolean hasPhoneColumn = csvHeaderHasPhoneNumber(header);
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
            CSVPrinter printer = new CSVPrinter(new OutputStreamWriter(baos, StandardCharsets.UTF_8),
                    CSVFormat.DEFAULT.builder().setHeader(appendErrorColumn(header)).build())) {

            for (RestaurantFailedRecord rec : errorRecords) {
                List<String> rowValues = new ArrayList<>();
                
                // Add language names dynamically
                List<LanguageConfiguration.LanguageConfig> languages = getSupportedLanguages();
                for (LanguageConfiguration.LanguageConfig lang : languages) {
                    String languageCode = lang.getLanguageCode();
                    String name = rec.getRestaurantRequest().getNameForLanguage(languageCode);
                    rowValues.add(name != null ? name : "");
                }
                
                rowValues.add(rec.getRestaurantRequest().getRestaurantCode());
                rowValues.add(rec.getRestaurantRequest().getRestaurantGroupCode());
                rowValues.add(rec.getRestaurantRequest().getCity());
                rowValues.add(rec.getRestaurantRequest().getArea());
                rowValues.add(rec.getRestaurantRequest().getState());
                rowValues.add(rec.getRestaurantRequest().getAddressLine1());
                rowValues.add(rec.getRestaurantRequest().getAddressLine2());
                rowValues.add(rec.getRestaurantRequest().getLocationPin());
                rowValues.add(rec.getRestaurantRequest().getQrCodeType());
                rowValues.add(rec.getRestaurantRequest().getStatus());
                rowValues.add(rec.getRestaurantRequest().getLogoName());
                rowValues.add(rec.getRestaurantRequest().getGstNumber());
                if (hasPhoneColumn) {
                    String phone = rec.getRestaurantRequest().getPhoneNumber();
                    rowValues.add(phone != null ? phone : "");
                }
                rowValues.add(rec.getErrorMessage());
                
                printer.printRecord(rowValues);
            }
            printer.flush();


            String errorFileName = originalFilename != null ? originalFilename : "Error.csv";

            return new CustomMultipartFile(baos.toByteArray(), "csvFile", errorFileName, "text/csv");            
        } catch (IOException e) {
            log.error("Error generating error CSV: ", e);
            return null;
        }
    }

    private String[] appendErrorColumn(String[] header) {
        String[] newHeader = Arrays.copyOf(header, header.length + 1);
        newHeader[header.length] = "Error";
        return newHeader;
    }

    /**
     * Generate dynamic headers based on configured languages and required fields
     */
    private String[] getDynamicHeaders() {
        List<String> headers = new ArrayList<>();
        
        List<LanguageConfiguration.LanguageConfig> languages = getSupportedLanguages();
        for (LanguageConfiguration.LanguageConfig lang : languages) {
            String headerName = "name_" + lang.getLanguageCode() + (lang.isCompulsory() ? "*" : "");
            headers.add(headerName);
        }
        
        // Add other required headers
        headers.addAll(Arrays.asList(
            "restaurant_code*",
            "restaurant_group_code*",
            "city*",
            "area*",
            "state*",
            "address_line_1*",
            "address_line_2",
            "location_pin*",
            "qr_code_type*",
            "status*",
            "logo_name",
            "gst_number*",
            "phone_number"
        ));
        
        return headers.toArray(new String[0]);
    }
    
    /**
     * Create sample data for CSV template based on configured languages
     */
    private List<String> createSampleData() {
        List<String> sampleData = new ArrayList<>();
        
        // Add sample names for each configured language
        List<LanguageConfiguration.LanguageConfig> languages = getSupportedLanguages();
        for (LanguageConfiguration.LanguageConfig lang : languages) {
            String languageCode = lang.getLanguageCode();
            String sampleName = getSampleNameForLanguage(languageCode);
            sampleData.add(sampleName);
        }
        
        // Add other sample data
        sampleData.addAll(Arrays.asList(
            "R001",                        // restaurant_code
            "GRP001",                     // restaurant_group_code
            "Tokyo",                      // city
            "Shibuya",                    // area
            "Tokyo-to",                   // state
            "1-2-3 Shibuya Center Street", // address_line_1
            "Near Shibuya Station",       // address_line_2
            "1500001",                    // location_pin
            "STATIC",                     // qr_code_type
            "ACTIVE",                     // status
            "restaurant_logo.png",        // logo_name
            "27AAPFU0939F1ZV",            // gst_number
            "+66 2 123 4567"              // phone_number (optional value)
        ));
        
        return sampleData;
    }
    
    /**
     * Get sample name for a specific language - completely dynamic
     */
    private String getSampleNameForLanguage(String languageCode) {
        return "Restaurant Name " + languageCode.toUpperCase();
    }
    
    /**
     * Save restaurant translations for all configured languages with duplicate name validation
     */
    private void saveRestaurantTranslations(BulkRestaurantUploadRequest request, Restaurant savedRestaurant) {
        List<LanguageConfiguration.LanguageConfig> languages = getSupportedLanguages();
        
        for (LanguageConfiguration.LanguageConfig lang : languages) {
            String languageCode = lang.getLanguageCode();
            String name = request.getNameForLanguage(languageCode);
            
            if (name != null && !name.trim().isEmpty()) {
                // Check for duplicate names
                if (savedRestaurant.getRestaurantGroup() != null) {
                    // Check within the same group, excluding the current restaurant
                    if (restaurantTranslationRepository.existsByNameInSameGroupForOtherRestaurants(
                            name.trim(), 
                            languageCode,
                            savedRestaurant.getRestaurantGroup().getId(),
                            savedRestaurant.getId())) {
                        throw new ValidationException(String.format("Restaurant name '%s' already exists in another restaurant in this group", 
                            name.trim()));
                    }
                } else {
                    // If no group, check globally excluding the current restaurant
                    if (restaurantTranslationRepository.existsByNameInOtherRestaurants(
                        name.trim(),
                        languageCode,
                        savedRestaurant.getId())) {
                        throw new ValidationException(String.format("Restaurant name '%s' already exists in another restaurant", 
                            name.trim()));
                    }
                }
                
                // Save translation
                RestaurantTranslation translation = new RestaurantTranslation();
                translation.setRestaurant(savedRestaurant);
                translation.setLanguageCode(languageCode);
                translation.setName(name.trim());
                restaurantTranslationRepository.save(translation);
            }
        }
    }

    /**
     * Asynchronously processes restaurant bulk upload with image support.
     * Initializes bulk upload processing context, creates restaurants with images,
     * batch inserts them into the database, and finalizes the bulk upload processing.
     * This method runs in a separate thread pool to avoid blocking the main request.
     *
     * @param records the CSV row data as a list of string arrays
     * @param imageMap the map of image data (key: filename, value: image bytes)
     * @param imageMapping the map of original to renamed filenames
     * @param userId the ID of the user performing the bulk upload
     * @param language the language code for localized error messages
     * @param bulkUploadId the ID of the bulk upload record
     */
    @Async("bulkUploadTaskExecutor")
    public void processRestaurantsWithImagesAsync(List<String[]> records, Map<String, byte[]> imageMap, 
                                                Map<String, String> imageMapping, String userId, String language, UUID bulkUploadId) {
        Locale userLocale = LocaleContextHolder.getLocale();

        BulkUploadProcessingContext context = initializeBulkUploadProcessing(records, bulkUploadId, userLocale);

        List<Restaurant> restaurants = createRestaurantsWithImages(context.nonBlankRows, context.errorRecords, userId, imageMap, imageMapping, context.header);
        batchInsertRestaurants(restaurants, context.errorRecords);

        finalizeBulkUploadProcessing(context.bulkUpload, context.errorRecords, context.header, context.nonBlankRows.size());
    }

    @Transactional
    private List<Restaurant> createRestaurantsWithImages(List<String[]> rows, List<RestaurantFailedRecord> errorRecords, 
                                                        String currentUserId, Map<String, byte[]> imageMap, 
                                                        Map<String, String> imageMapping, String[] csvHeader) {
        return createRestaurantsInternal(rows, errorRecords, currentUserId, imageMap, imageMapping, csvHeader);
    }

    /**
     * Unified method to create restaurants from CSV rows with optional image support.
     * Handles GST validation, duplicate checking, and restaurant creation.
     * 
     * @param rows CSV row data
     * @param errorRecords List to collect failed records
     * @param currentUserId User ID creating the restaurants
     * @param imageMap Optional map of image data (key: filename, value: image bytes)
     * @param imageMapping Optional map of original to renamed filenames
     * @return List of successfully created restaurants
     */
    @Transactional
    private List<Restaurant> createRestaurantsInternal(List<String[]> rows, List<RestaurantFailedRecord> errorRecords, 
                                                       String currentUserId, Map<String, byte[]> imageMap, 
                                                       Map<String, String> imageMapping, String[] csvHeader) {
        UUID creatorId = UUID.fromString(currentUserId);
        Locale userLocale = LocaleContextHolder.getLocale();
        boolean hasPhoneColumn = csvHeaderHasPhoneNumber(csvHeader);

        // First pass: Check GST number format and duplicates
        Set<Integer> allErrorIndices = validateGstNumbers(rows, errorRecords, userLocale, hasPhoneColumn);

        // Second pass: Process rows that don't have format or duplicate errors
        return IntStream.range(0, rows.size())
                .filter(i -> !allErrorIndices.contains(i))
                .mapToObj(i -> {
                    String[] row = rows.get(i);
                    BulkRestaurantUploadRequest request = createRestaurantRequest(row, hasPhoneColumn);
                    try {
                        // Use appropriate validator method based on whether images are provided
                        if (imageMap != null && imageMapping != null) {
                            restaurantValidator.validate(request, imageMap, imageMapping);
                            return convertToRestaurant(request, creatorId, imageMap, imageMapping);
                        } else {
                            restaurantValidator.validate(request);
                            return convertToRestaurant(request, creatorId, null, null);
                        }
                    } catch (Exception e) {
                        BulkRestaurantUploadRequest failedRequest = createRestaurantRequest(row, hasPhoneColumn);
                        errorRecords.add(new RestaurantFailedRecord(failedRequest, e.getMessage()));
                        return null;
                    }
                })
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    /**
     * Validates GST numbers for format and duplicates across all rows.
     * Priority: format validation > duplicate check
     * 
     * @param rows CSV row data
     * @param errorRecords List to collect failed records
     * @param userLocale User locale for error messages
     * @return Set of row indices that have GST validation errors
     */
    private Set<Integer> validateGstNumbers(List<String[]> rows, List<RestaurantFailedRecord> errorRecords,
                                            Locale userLocale, boolean hasPhoneColumn) {
        Map<String, List<Integer>> gstNumberIndices = new HashMap<>();
        Set<Integer> formatErrorIndices = new HashSet<>();
        
        // First pass: Check GST number format and collect valid ones for duplicate checking
        for (int i = 0; i < rows.size(); i++) {
            String[] row = rows.get(i);
            BulkRestaurantUploadRequest request = createRestaurantRequest(row, hasPhoneColumn);
            String gstNumber = request.getGstNumber();
            if (gstNumber != null && !gstNumber.trim().isEmpty()) {
                String trimmedGstNumber = gstNumber.trim();
                
                // Check format first
                boolean isValidFormat = GST_NUMBER_PATTERN.matcher(trimmedGstNumber).matches();
                
                if (!isValidFormat) {
                    // Mark all rows with invalid format GST number
                    formatErrorIndices.add(i);
                } else {
                    // Only track valid format GST numbers for duplicate check
                    gstNumberIndices.computeIfAbsent(trimmedGstNumber, k -> new ArrayList<>()).add(i);
                }
            }
        }

        // Mark duplicate GST numbers as errors (only for valid format GST numbers)
        // Keep first occurrence, mark others as duplicates
        Set<Integer> duplicateIndices = new HashSet<>();
        for (Map.Entry<String, List<Integer>> entry : gstNumberIndices.entrySet()) {
            List<Integer> indices = entry.getValue();
            if (indices.size() > 1) {
                // Keep first occurrence, mark others as duplicates
                for (int i = 1; i < indices.size(); i++) {
                    duplicateIndices.add(indices.get(i));
                }
            }
        }

        // Combine all error indices
        Set<Integer> allErrorIndices = new HashSet<>();
        allErrorIndices.addAll(formatErrorIndices);
        allErrorIndices.addAll(duplicateIndices);
        
        // Add error records using generic message for both format and duplicate issues
        for (Integer idx : allErrorIndices) {
            String[] row = rows.get(idx);
            BulkRestaurantUploadRequest failedRequest = createRestaurantRequest(row, hasPhoneColumn);
            String errorMsg = messageUtil.getMessage("bulk.restaurant.upload.error.gst.number.invalid", 
                userLocale);
            errorRecords.add(new RestaurantFailedRecord(failedRequest, errorMsg));
        }
        
        return allErrorIndices;
    }

    /**
     * Extract images from ZIP file and create mapping for restaurant logos
     */
    private Map<String, byte[]> extractImagesFromZip(MultipartFile zipFile, Locale userLocale, 
                                                   Map<String, String> imageMapping) throws IOException {
        Map<String, byte[]> imageMap = new HashMap<>();
        
        try (ZipInputStream zipInputStream = new ZipInputStream(zipFile.getInputStream())) {
            ZipEntry entry;
            while ((entry = zipInputStream.getNextEntry()) != null) {
                if (!entry.isDirectory()) {
                    String originalFileName = entry.getName();
                    
                    if (!BulkUploadImageFilenameUtils.isValidImageFile(originalFileName)) {
                        continue;
                    }
                    
                    String uniqueFileName = BulkUploadImageFilenameUtils.generateBulkUploadImageFileName(originalFileName);
                    
                    ByteArrayOutputStream baos = new ByteArrayOutputStream();
                    byte[] buffer = new byte[1024];
                    int len;
                    while ((len = zipInputStream.read(buffer)) > 0) {
                        baos.write(buffer, 0, len);
                    }
                    
                    byte[] imageData = baos.toByteArray();
                    imageMap.put(uniqueFileName, imageData);
                    imageMapping.put(originalFileName, uniqueFileName);
                }
                zipInputStream.closeEntry();
            }
        }
        
        return imageMap;
    }

    /**
     * Upload restaurant logo to S3 storage
     */
    private String uploadLogoToS3(byte[] imageData, String fileName, String userId) throws IOException {
        try (ByteArrayInputStream inputStream = new ByteArrayInputStream(imageData)) {
            String s3Key = "profile-images/restaurant/" + fileName;
            return awsService.uploadFile(inputStream, s3Key, imageData.length);
        } catch (Exception e) {
            throw new IOException("Failed to upload logo to S3: " + e.getMessage(), e);
        }
    }

}

