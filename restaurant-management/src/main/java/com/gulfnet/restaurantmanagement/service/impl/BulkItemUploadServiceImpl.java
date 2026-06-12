package com.gulfnet.restaurantmanagement.service.impl;

import com.gulfnet.restaurantmanagement.service.BulkItemUploadService;
import com.gulfnet.restaurantmanagement.config.RestaurantChainConfigProperties;
import com.gulfnet.restaurantmanagement.util.MessageUtil;
import com.gulfnet.shared_library.util.BulkUploadImageFilenameUtils;
import com.gulfnet.shared_library.util.ImageThumbnailUtil;
import com.gulfnet.shared_library.entity.BulkUpload;
import com.gulfnet.shared_library.entity.User;
import com.gulfnet.shared_library.repository.UserRepository;
import com.gulfnet.shared_library.model.request.ItemFailedRecord;
import com.gulfnet.shared_library.entity.Item;
import com.gulfnet.shared_library.entity.ItemTranslation;
import com.gulfnet.shared_library.enums.BulkUploadStatus;
import com.gulfnet.shared_library.enums.DietaryPreference;
import com.gulfnet.shared_library.enums.EntityStatus;
import com.gulfnet.shared_library.enums.AlcoholType;
import com.gulfnet.shared_library.enums.ItemOrderType;
import com.gulfnet.shared_library.exception.ValidationException;
import com.gulfnet.shared_library.model.request.BulkItemUploadRequest;
import com.gulfnet.shared_library.model.request.CustomMultipartFile;
import com.gulfnet.shared_library.model.response.dto.ResponseDto;
import com.gulfnet.shared_library.repository.BulkUploadRepository;
import com.gulfnet.shared_library.repository.ItemRepository;
import com.gulfnet.shared_library.repository.ItemTranslationRepository;
import com.gulfnet.shared_library.config.AWSService;
import com.gulfnet.shared_library.exception.BadRequestException;
import com.gulfnet.restaurantmanagement.validator.ItemCsvFileUploadValidator;

import com.gulfnet.shared_library.enums.UploadType;
import com.opencsv.CSVReader;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;
import java.util.concurrent.ConcurrentHashMap;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

@Slf4j
@Service
@RequiredArgsConstructor
public class BulkItemUploadServiceImpl implements BulkItemUploadService {
    private final ImageThumbnailUtil imageThumbnailUtil;
    private final BulkUploadRepository bulkUploadRepository;
    private final ItemRepository itemRepository;
    private final ItemTranslationRepository itemTranslationRepository;
    private final MessageUtil messageUtil;
    private final AWSService awsService;
    private final UserRepository userRepository;
    private final ItemCsvFileUploadValidator itemCsvFileUploadValidator;
    private final RestaurantChainConfigProperties restaurantChainConfigProperties;
    
    private static final String ERROR_COLUMN = "Error";
    private static final int BUFFER_SIZE = 8192;
    private static final int BATCH_SIZE = 50;
    private static final int MAX_NAME_LENGTH = 50;
    private static final int MAX_DESCRIPTION_LENGTH = 250;
    private static final long MAX_CSV_FILE_SIZE = 10L * 1024 * 1024; // 10MB in bytes
    private static final long MAX_ZIP_FILE_SIZE = 30L * 1024 * 1024; // 30MB in bytes
    private static final String COLUMN_PREFIX_NAME = "name_";
    private static final String COLUMN_PREFIX_DESCRIPTION = "description_";
    private static final String COLUMN_IMAGE_NAME = "image_name";
    private static final String COLUMN_ITEM_CODE = "item_code";
    private static final int MAX_ITEM_CODE_LENGTH = 64;
    private static final java.util.regex.Pattern ITEM_CODE_PATTERN =
            java.util.regex.Pattern.compile("^[A-Za-z0-9][A-Za-z0-9_-]{0,63}$");
    private static final String CONTENT_TYPE_CSV = "text/csv";
    private static final String S3_ERROR_PATH = "bulk-upload/items/error/";
    private static final String COLUMN_STATUS = "status";
    private static final String COLUMN_BASE_PRICE = "base_price";
    private static final String COLUMN_ALCOHOL_TYPE = "alcohol_type";
    private static final String COLUMN_ITEM_ORDER_TYPE = "item_order_type";
    private static final String DATE_TIME_PATTERN = "yyyyMMdd_HHmmss";
    private static final String MSG_BULK_UPLOAD_FILE_SIZE_EXCEEDED = "bulk.upload.file.size.exceeded";
    private static final ConcurrentHashMap<Integer, Map<String, String>> NAME_BY_LANG_STORE = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<Integer, Map<String, String>> DESC_BY_LANG_STORE = new ConcurrentHashMap<>();

    private static final class ExtractedImages {
        private final Map<String, byte[]> imageMap;
        private final Map<String, String> imageMapping;

        private ExtractedImages(Map<String, byte[]> imageMap, Map<String, String> imageMapping) {
            this.imageMap = imageMap;
            this.imageMapping = imageMapping;
        }
    }
    
    /**
     * Streams a CSV template for bulk item upload to the HTTP response.
     * <p>
     * The header is built dynamically from chain supported languages, marking compulsory language columns with {@code *}.
     * A sample row is included to illustrate expected values and optional image-name usage.
     * </p>
     *
     * @param response servlet response to write the CSV to
     * @param locale   locale header value (template generation uses config + UTF-8 BOM for Excel friendliness)
     * @return an empty {@link ResponseEntity} with HTTP 200 on success
     * @throws IOException if writing to the response fails
     */
    @Override
    public ResponseEntity<Void> downloadTemplate(HttpServletResponse response, String locale) throws IOException {
        log.info("Generating bulk item upload template for locale: {}", locale);
        
        String filename = "item_bulk_upload_template.csv";
        
        // Build headers dynamically from supported languages in config
        List<String> headerList = new ArrayList<>();
        headerList.add("item_code*");
        headerList.add("base_price*");
        headerList.add("status*");
        headerList.add("alcohol_type*");

        RestaurantChainConfigProperties.RestaurantChainData chain = restaurantChainConfigProperties.getChain();
        List<RestaurantChainConfigProperties.SupportedLanguage> supported = chain != null ? chain.getSupportedLanguages() : Collections.emptyList();
        if (supported != null) {
            for (RestaurantChainConfigProperties.SupportedLanguage lang : supported) {
                String code = lang.getLanguageCode();
                boolean compulsory = lang.isCompulsory();
                String suffix = compulsory ? "*" : "";
                headerList.add(COLUMN_PREFIX_NAME + code + suffix);
                headerList.add(COLUMN_PREFIX_DESCRIPTION + code + suffix);
            }
        }
        // Add image_name column (optional)
        headerList.add(COLUMN_IMAGE_NAME);
        String[] headers = headerList.toArray(new String[0]);
        
        response.setContentType(CONTENT_TYPE_CSV);
        response.setCharacterEncoding("UTF-8");
        response.setHeader("Content-Disposition", "attachment; filename=\"" + filename + "\"");
        response.getWriter().write('\ufeff');
        
        try (CSVPrinter csvPrinter = new CSVPrinter(response.getWriter(), CSVFormat.DEFAULT.withHeader(headers))) {
            // Add a sample row
            List<Object> sample = new ArrayList<>();
            sample.add("SKU001");
            sample.add("10.99");
            sample.add("ACTIVE");
            sample.add("ALCOHOLIC");
            if (supported != null) {
                for (RestaurantChainConfigProperties.SupportedLanguage lang : supported) {
                    String code = lang.getLanguageCode();
                    if ("en".equalsIgnoreCase(code)) {
                        sample.add("Chicken Curry");
                        sample.add("Spicy chicken curry with rice");
                    } else if ("ja".equalsIgnoreCase(code)) {
                        sample.add("チキンカレー");
                        sample.add("ライス付きスパイシーチキンカレー");
                    } else if ("th".equalsIgnoreCase(code)) {
                        sample.add("แกงไก่");
                        sample.add("แกงไก่เผ็ดเสิร์ฟพร้อมข้าว");
                    } else {
                        sample.add("");
                        sample.add("");
                    }
                }
            }
            // Add sample image name
            sample.add("chicken_curry.jpg");
            csvPrinter.printRecord(sample);
            
            csvPrinter.flush();
        }
        
        log.info("Successfully generated bulk item upload template");
        return ResponseEntity.ok().build();
    }

    /**
     * Accepts a bulk item upload CSV (and optional ZIP of images), validates it, stores the original file in S3,
     * creates a {@link BulkUpload} tracking record, and starts asynchronous processing.
     * <p>
     * If an image ZIP is provided, images are extracted/renamed and an original-to-renamed mapping is created for later
     * validation and upload. CSV structure is validated up-front; when validation fails, an error CSV is generated and
     * uploaded to S3 and the {@link BulkUpload} is updated with the error file path.
     * </p>
     *
     * @param file         CSV file containing items to create/update
     * @param imageZipFile optional ZIP file containing item images referenced by {@code image_name}
     * @param action       upload action (business-specific, passed through for processing)
     * @param utfType      optional encoding hint (CSV reader also detects BOM)
     * @param language     language hint for processing/validation (falls back to current locale)
     * @param userId       current user id (string UUID)
     * @param userRole     current user role (used by downstream validations/authorization)
     * @param localeHeader locale header value from request
     * @return response wrapper containing the created {@link BulkUpload} record
     * @throws IOException if reading/uploading the input files fails
     * @throws ResponseStatusException for invalid inputs (missing file, invalid user id, invalid zip, etc.)
     */
    @Override
    public ResponseDto<BulkUpload> processBulkUpload(MultipartFile file, MultipartFile imageZipFile, String action, 
            String utfType, String language, String userId, String userRole, 
            String localeHeader) throws IOException {
        Locale userLocale = LocaleContextHolder.getLocale();
        String resolvedLanguage = resolveLanguage(language, userLocale);

        log.info("Processing bulk item upload. Action: {}, File: {}, UTF Type: {}, Language: {}, Has Images: {}",
                action, file != null ? file.getOriginalFilename() : null, utfType, resolvedLanguage,
                imageZipFile != null && !imageZipFile.isEmpty());

        validateCsvUploadFile(file, userLocale);
        UUID currentUserId = parseUserId(userId, userLocale);
        ExtractedImages extractedImages = extractImagesIfPresent(imageZipFile, userLocale);

        String s3Url = uploadCsvToS3(file, currentUserId, userLocale);
        BulkUpload bulkUpload = createInitialBulkUpload(currentUserId, s3Url);

        try {
            itemCsvFileUploadValidator.validate(file);
            List<String[]> records = readCsvFile(file);
            int totalRecords = calculateNonBlankTotalRecords(records);
            bulkUpload = updateBulkUploadCounts(bulkUpload, totalRecords);

            startAsyncProcessing(records, extractedImages, currentUserId, resolvedLanguage, bulkUpload.getId());
            return buildInitiatedResponse(bulkUpload, userLocale);
        } catch (BadRequestException e) {
            log.error("CSV validation failed: {}", e.getMessage());
            return handleCsvStructureValidationFailure(bulkUpload, e, userLocale);
        }
    }

    private String resolveLanguage(String language, Locale userLocale) {
        if (language == null || language.trim().isEmpty()) {
            return userLocale.getLanguage();
        }
        return language;
    }

    private void validateCsvUploadFile(MultipartFile file, Locale userLocale) {
        if (file == null || file.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    messageUtil.getMessage("bulk.upload.file.required", userLocale));
        }
        if (file.getSize() > MAX_CSV_FILE_SIZE) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    messageUtil.getMessage(MSG_BULK_UPLOAD_FILE_SIZE_EXCEEDED, userLocale, "10MB"));
        }
    }

    private UUID parseUserId(String userId, Locale userLocale) {
        try {
            return UUID.fromString(userId);
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    messageUtil.getMessage("bulk.upload.invalid.user.id", userLocale));
        }
    }

    /**
     * When an image ZIP is supplied, extracts image bytes and filename mapping; returns {@code null} when absent or empty.
     */
    private ExtractedImages extractImagesIfPresent(MultipartFile imageZipFile, Locale userLocale) {
        if (imageZipFile == null || imageZipFile.isEmpty()) {
            return null;
        }
        try {
            Map<String, String> imageMapping = new HashMap<>();
            Map<String, byte[]> imageMap = extractImagesFromZip(imageZipFile, userLocale, imageMapping);
            log.info("Successfully extracted {} images from ZIP file", imageMap.size());
            return new ExtractedImages(imageMap, imageMapping);
        } catch (Exception e) {
            log.error("Failed to extract images from ZIP: {}", e.getMessage(), e);
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    messageUtil.getMessage("bulk.upload.zip.extraction.failed", userLocale));
        }
    }

    /**
     * Uploads the CSV to S3 under a user-scoped bulk-upload key with a UTC timestamp suffix, returning the object URL.
     */
    private String uploadCsvToS3(MultipartFile file, UUID currentUserId, Locale userLocale) {
        try {
            String originalFileName = file.getOriginalFilename();
            if (originalFileName == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        messageUtil.getMessage("bulk.upload.invalid.filename", userLocale));
            }

            int dot = originalFileName.lastIndexOf(".");
            String fileNameWithoutExt = dot > 0 ? originalFileName.substring(0, dot) : originalFileName;
            String fileExtension = dot > 0 ? originalFileName.substring(dot) : "";

            String timestamp = LocalDateTime.now(ZoneOffset.UTC).format(DateTimeFormatter.ofPattern(DATE_TIME_PATTERN));
            String newFileName = String.format("%s_%s%s", fileNameWithoutExt, timestamp, fileExtension);
            String s3Key = "bulk-upload/items/" + currentUserId + "/" + newFileName;

            String s3Url = awsService.uploadFile(file.getInputStream(), s3Key, file.getSize());
            log.info("Successfully uploaded file to S3. Key: {}, URL: {}", s3Key, s3Url);
            return s3Url;
        } catch (ResponseStatusException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to upload file to S3", e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    messageUtil.getMessage("bulk.upload.s3.upload.failed", userLocale));
        }
    }

    private BulkUpload createInitialBulkUpload(UUID currentUserId, String s3Url) {
        BulkUpload bulkUpload = new BulkUpload();
        bulkUpload.setCreatedBy(currentUserId);
        bulkUpload.setStatus(BulkUploadStatus.PENDING);
        bulkUpload.setFilePath(s3Url);
        bulkUpload.setCreatedAt(OffsetDateTime.now(ZoneOffset.UTC));
        bulkUpload.setUpdatedAt(OffsetDateTime.now(ZoneOffset.UTC));
        bulkUpload.setUploadType(UploadType.ITEM);
        return bulkUploadRepository.save(bulkUpload);
    }

    private int calculateNonBlankTotalRecords(List<String[]> recordsIncludingHeader) {
        List<String[]> nonBlankRecords = filterNonBlankRows(recordsIncludingHeader);
        return Math.max(0, nonBlankRecords.size() - 1);
    }

    private static List<String[]> filterNonBlankRows(List<String[]> rows) {
        return rows.stream()
                .filter(row -> !Arrays.stream(row).allMatch(s -> s == null || s.trim().isEmpty()))
                .collect(Collectors.toList());
    }

    private BulkUpload updateBulkUploadCounts(BulkUpload bulkUpload, int totalRecords) {
        bulkUpload.setTotalRecordCount(totalRecords);
        bulkUpload.setSuccessRecordCount(0);
        bulkUpload.setFailureRecordCount(0);
        bulkUpload.setUploadType(UploadType.ITEM);
        bulkUpload.setUpdatedAt(OffsetDateTime.now(ZoneOffset.UTC));
        return bulkUploadRepository.save(bulkUpload);
    }

    private void startAsyncProcessing(List<String[]> records, ExtractedImages extractedImages,
                                      UUID currentUserId, String language, UUID bulkUploadId) {
        if (extractedImages != null) {
            processItemsWithImagesAsync(records, extractedImages.imageMap, extractedImages.imageMapping,
                    currentUserId.toString(), language, bulkUploadId);
        } else {
            processItemsAsync(records, currentUserId.toString(), language, bulkUploadId);
        }
    }

    private ResponseDto<BulkUpload> buildInitiatedResponse(BulkUpload bulkUpload, Locale userLocale) {
        return ResponseDto.<BulkUpload>builder()
                .data(bulkUpload)
                .message(messageUtil.getMessage("bulk.upload.initiated", userLocale))
                .build();
    }

    /**
     * Marks the bulk upload failed, generates a one-row error CSV with the validation message, uploads it when possible,
     * and returns the persisted entity to the client.
     */
    private ResponseDto<BulkUpload> handleCsvStructureValidationFailure(BulkUpload bulkUpload,
                                                                        BadRequestException e,
                                                                        Locale userLocale) {
        bulkUpload.setStatus(BulkUploadStatus.FAILURE);
        bulkUpload.setReason(e.getMessage());
        bulkUpload.setTotalRecordCount(0);
        bulkUpload.setSuccessRecordCount(0);
        bulkUpload.setFailureRecordCount(0);
        bulkUpload.setUploadType(UploadType.ITEM);
        bulkUpload.setUpdatedAt(OffsetDateTime.now(ZoneOffset.UTC));

        String[] headers = buildValidationFailureErrorCsvHeaders();
        try {
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            try (CSVPrinter csvPrinter = new CSVPrinter(new OutputStreamWriter(outputStream, StandardCharsets.UTF_8),
                    CSVFormat.DEFAULT.withHeader(headers))) {
                List<String> row = new ArrayList<>();
                for (int i = 0; i < headers.length - 1; i++) {
                    row.add("");
                }
                row.add(e.getMessage());
                csvPrinter.printRecord(row);
                csvPrinter.flush();
            }

            MultipartFile errorFile = new CustomMultipartFile(
                    outputStream.toByteArray(),
                    "csvFile",
                    "Error.csv",
                    CONTENT_TYPE_CSV
            );
            String errorFileUrl = uploadValidationErrorCsvToS3(errorFile);
            bulkUpload.setErrorFilePath(errorFileUrl);
        } catch (Exception ex) {
            log.error("Failed to create/upload validation error file", ex);
        }

        BulkUpload saved = bulkUploadRepository.save(bulkUpload);
        return ResponseDto.<BulkUpload>builder()
                .data(saved)
                .message(e.getMessage())
                .build();
    }

    /**
     * Column headers for validation error CSVs: fixed columns plus dynamic supported-language name/description columns.
     */
    private String[] buildValidationFailureErrorCsvHeaders() {
        List<String> headerList = new ArrayList<>();
        headerList.add(COLUMN_ITEM_CODE);
        headerList.add(COLUMN_BASE_PRICE);
        headerList.add(COLUMN_STATUS);
        headerList.add(COLUMN_ALCOHOL_TYPE);
        headerList.add(COLUMN_ITEM_ORDER_TYPE);

        RestaurantChainConfigProperties.RestaurantChainData chain = restaurantChainConfigProperties.getChain();
        List<RestaurantChainConfigProperties.SupportedLanguage> supported =
                chain != null ? chain.getSupportedLanguages() : Collections.emptyList();
        if (supported != null) {
            for (RestaurantChainConfigProperties.SupportedLanguage lang : supported) {
                String code = lang.getLanguageCode();
                headerList.add(COLUMN_PREFIX_NAME + code);
                headerList.add(COLUMN_PREFIX_DESCRIPTION + code);
            }
        }
        headerList.add(COLUMN_IMAGE_NAME);
        headerList.add(ERROR_COLUMN);
        return headerList.toArray(new String[0]);
    }

    private String uploadValidationErrorCsvToS3(MultipartFile errorFile) throws IOException {
        String timestamp = LocalDateTime.now(ZoneOffset.UTC).format(DateTimeFormatter.ofPattern(DATE_TIME_PATTERN));
        String errorFileName = "validation_error_" + timestamp + ".csv";
        String errorS3Key = S3_ERROR_PATH + errorFileName;
        return awsService.uploadFile(errorFile.getInputStream(), errorS3Key, errorFile.getSize());
    }

    /**
     * Reads all rows from a CSV file, handling BOM detection for UTF-8 and UTF-16 (BE/LE).
     *
     * @param file uploaded CSV multipart file
     * @return list of CSV records (each row is a {@code String[]} as returned by the CSV reader)
     * @throws IOException if the file cannot be read
     * @throws ResponseStatusException if parsing fails due to invalid CSV content
     */
    private List<String[]> readCsvFile(MultipartFile file) throws IOException {
        Locale userLocale = LocaleContextHolder.getLocale();
        
        // Read the file content as bytes first
        byte[] fileContent = file.getInputStream().readAllBytes();
        
        // Try to detect BOM and handle encoding
        String content;
        if (fileContent.length >= 3 && 
            fileContent[0] == (byte) 0xEF && 
            fileContent[1] == (byte) 0xBB && 
            fileContent[2] == (byte) 0xBF) {
            // UTF-8 BOM detected, skip first 3 bytes
            content = new String(fileContent, 3, fileContent.length - 3, StandardCharsets.UTF_8);
            log.info("Detected UTF-8 BOM in CSV file");
        } else if (fileContent.length >= 2 && 
                   fileContent[0] == (byte) 0xFE && 
                   fileContent[1] == (byte) 0xFF) {
            // UTF-16 BE BOM detected
            content = new String(fileContent, 2, fileContent.length - 2, StandardCharsets.UTF_16BE);
            log.info("Detected UTF-16 BE BOM in CSV file");
        } else if (fileContent.length >= 2 && 
                   fileContent[0] == (byte) 0xFF && 
                   fileContent[1] == (byte) 0xFE) {
            // UTF-16 LE BOM detected
            content = new String(fileContent, 2, fileContent.length - 2, StandardCharsets.UTF_16LE);
            log.info("Detected UTF-16 LE BOM in CSV file");
        } else {
            // No BOM detected, try UTF-8
            content = new String(fileContent, StandardCharsets.UTF_8);
            log.info("No BOM detected, using UTF-8 encoding");
        }
        
        try (CSVReader reader = new CSVReader(new StringReader(content))) {
            List<String[]> records = reader.readAll();
            log.info("Successfully read {} records from CSV file", records.size());
            return records;
        } catch (Exception e) {
            log.error("Error reading CSV file", e);
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                messageUtil.getMessage("bulk.item.upload.error.csv.read", 
                userLocale, e.getMessage()));
        }
    }

    private String extractFileName(String filePath) {
        return com.gulfnet.restaurantmanagement.util.FileNameUtil.extractFileName(filePath);
    }

    private static class BulkUploadContext {
        private final String[] header;
        private final List<String[]> nonBlankRows;
        private final String originalFileName;

        private BulkUploadContext(String[] header, List<String[]> nonBlankRows, String originalFileName) {
            this.header = header;
            this.nonBlankRows = nonBlankRows;
            this.originalFileName = originalFileName;
        }

        public String[] getHeader() {
            return header;
        }

        public List<String[]> getNonBlankRows() {
            return nonBlankRows;
        }

        public String getOriginalFileName() {
            return originalFileName;
        }
    }

    /**
     * Initializes the bulk upload working set by extracting the header row, removing blank rows, and updating counts.
     * <p>
     * Also normalizes the original filename (removes timestamp suffixes) for use when generating error files.
     * When the CSV contains no data, the bulk upload record is marked as failure.
     * </p>
     *
     * @param records    CSV records including the header row at index 0
     * @param bulkUpload persistent bulk-upload tracking record to update
     * @return context containing header and non-blank rows, or {@code null} when no processable records exist
     */
    private BulkUploadContext initializeBulkUploadRecords(List<String[]> records, BulkUpload bulkUpload) {
        String originalFilePath = bulkUpload.getFilePath();
        String originalFileName = extractFileName(originalFilePath);

        originalFileName = originalFileName.replaceAll("_(\\d{8}_\\d{6})(?=\\.)", "");
        if (records.isEmpty()) {
            bulkUpload.setStatus(BulkUploadStatus.FAILURE);
            bulkUpload.setReason("No data found in CSV file");
            bulkUpload.setUpdatedAt(OffsetDateTime.now(ZoneOffset.UTC));
            bulkUploadRepository.save(bulkUpload);
            return null;
        }

        String[] header = records.get(0);
        records.remove(0);

        // Filter out blank rows before processing
        List<String[]> nonBlankRows = filterNonBlankRows(records);

        bulkUpload.setTotalRecordCount(nonBlankRows.size());

        return new BulkUploadContext(header, nonBlankRows, originalFileName);
    }

    /**
     * Helper method to initialize bulk upload processing context
     */
    private BulkUploadProcessingContext initializeBulkUploadProcessing(
            List<String[]> records, UUID bulkUploadId, String logMessage) {
        Locale userLocale = LocaleContextHolder.getLocale();
        log.info(logMessage, records.size() - 1, bulkUploadId);
        
        Optional<BulkUpload> bulkUploadOpt = bulkUploadRepository.findById(bulkUploadId);
        if (bulkUploadOpt.isEmpty()) {
            log.error("Bulk upload not found: {}", bulkUploadId);
            throw new BadRequestException(messageUtil.getMessage("bulk.item.upload.error.not.found", userLocale));
        }
        
        BulkUpload bulkUpload = bulkUploadOpt.get();
        List<ItemFailedRecord> errorRecords = new ArrayList<>();

        BulkUploadContext context = initializeBulkUploadRecords(records, bulkUpload);
        if (context == null) {
            return null;
        }

        String[] header = context.getHeader();
        List<String[]> nonBlankRows = context.getNonBlankRows();
        String originalFileName = context.getOriginalFileName();
        
        return new BulkUploadProcessingContext(bulkUpload, errorRecords, header, nonBlankRows, originalFileName);
    }
    
    /**
     * Context class for bulk upload processing
     */
    private static class BulkUploadProcessingContext {
        final BulkUpload bulkUpload;
        final List<ItemFailedRecord> errorRecords;
        final String[] header;
        final List<String[]> nonBlankRows;
        final String originalFileName;
        
        BulkUploadProcessingContext(BulkUpload bulkUpload, List<ItemFailedRecord> errorRecords,
                String[] header, List<String[]> nonBlankRows, String originalFileName) {
            this.bulkUpload = bulkUpload;
            this.errorRecords = errorRecords;
            this.header = header;
            this.nonBlankRows = nonBlankRows;
            this.originalFileName = originalFileName;
        }
    }

    /**
     * Asynchronously validates and processes a bulk upload CSV (without images).
     * <p>
     * Performs row validation, converts valid rows into {@link Item} entities, aggregates failed records,
     * uploads an error CSV to S3 when needed, and updates the {@link BulkUpload} status and counts.
     * </p>
     *
     * @param records      CSV records including header at index 0
     * @param userId       current user id as string UUID
     * @param language     language hint for validations/translations
     * @param bulkUploadId bulk upload tracking record id
     */
    @Async("bulkUploadTaskExecutor")
    public void processItemsAsync(List<String[]> records, String userId, String language, UUID bulkUploadId) {
        BulkUploadProcessingContext processingContext = initializeBulkUploadProcessing(
                records, bulkUploadId, "Starting async processing of {} items for bulk upload ID: {}");
        if (processingContext == null) {
            return;
        }
        
        BulkUpload bulkUpload = processingContext.bulkUpload;
        List<ItemFailedRecord> errorRecords = processingContext.errorRecords;
        String[] header = processingContext.header;
        List<String[]> nonBlankRows = processingContext.nonBlankRows;
        String originalFileName = processingContext.originalFileName;

        log.info("Starting validation for {} non-blank rows (original: {} rows)", nonBlankRows.size(), records.size());
        List<Item> items = validateAndCreateItems(nonBlankRows, language, errorRecords, userId, header);
        log.info("Validation completed. Valid items: {}, Errors: {}", items.size(), errorRecords.size());

        batchInsertItems(items, errorRecords);

        bulkUpload.setFailureRecordCount(errorRecords.size());
        bulkUpload.setSuccessRecordCount(nonBlankRows.size() - errorRecords.size());
        log.info("Final results - Total: {}, Success: {}, Failure: {}", 
                bulkUpload.getTotalRecordCount(), bulkUpload.getSuccessRecordCount(), bulkUpload.getFailureRecordCount());

        if (!errorRecords.isEmpty()) {
            MultipartFile errorFile = writeFailedRecordsToCsv(errorRecords, header, originalFileName);
            try {
                String errorFileName = generateErrorFileNameWithTimestamp(errorFile);
                // Update the path to include 'error' folder inside 'items'
                String s3Key = S3_ERROR_PATH + errorFileName;
                String downloadErrorPath = awsService.uploadFile(errorFile.getInputStream(), 
                    s3Key, errorFile.getSize());
                bulkUpload.setErrorFilePath(downloadErrorPath);
                bulkUpload.setStatus(BulkUploadStatus.FAILURE);
                bulkUpload.setReason("Bulk Upload completed, Success count " + bulkUpload.getSuccessRecordCount() + 
                    " and failure count " + bulkUpload.getFailureRecordCount() + " out of " + bulkUpload.getTotalRecordCount() + " total records");
            } catch (Exception e) {
                log.error("Failed to upload error file", e);
            }
        } else {
            bulkUpload.setErrorFilePath("");
            bulkUpload.setStatus(BulkUploadStatus.SUCCESS);
            bulkUpload.setReason("Bulk Upload completed successfully.");
        }
        
        bulkUploadRepository.save(bulkUpload);
        log.info("Bulk upload processing completed for ID: {}. Success: {}, Errors: {}", 
                bulkUploadId, bulkUpload.getSuccessRecordCount(), errorRecords.size());
    }

    /**
     * Asynchronously validates and processes a bulk upload CSV with an accompanying ZIP of images.
     * <p>
     * In addition to base validation, verifies that referenced images exist in the ZIP, uploads item images and
     * thumbnails to S3, and clears extracted image data after processing to reduce memory pressure.
     * </p>
     *
     * @param records       CSV records including header at index 0
     * @param imageMap      extracted image bytes keyed by renamed filename
     * @param imageMapping  original-to-renamed filename mapping for ZIP entries
     * @param userId        current user id as string UUID
     * @param language      language hint for validations/translations
     * @param bulkUploadId  bulk upload tracking record id
     */
    @Async("bulkUploadTaskExecutor")
    public void processItemsWithImagesAsync(List<String[]> records, Map<String, byte[]> imageMap, 
            Map<String, String> imageMapping, String userId, String language, UUID bulkUploadId) {
        BulkUploadProcessingContext processingContext = initializeBulkUploadProcessing(
                records, bulkUploadId, "Starting async processing of {} items with images for bulk upload ID: {}");
        if (processingContext == null) {
            return;
        }
        
        BulkUpload bulkUpload = processingContext.bulkUpload;
        List<ItemFailedRecord> errorRecords = processingContext.errorRecords;
        String[] header = processingContext.header;
        List<String[]> nonBlankRows = processingContext.nonBlankRows;
        String originalFileName = processingContext.originalFileName;

        log.info("Starting validation for {} non-blank rows with images (original: {} rows)", nonBlankRows.size(), records.size());
        List<Item> items = validateAndCreateItemsWithImages(nonBlankRows, language, errorRecords, userId, header, imageMap, imageMapping);
        log.info("Validation completed. Valid items: {}, Errors: {}", items.size(), errorRecords.size());

        batchInsertItems(items, errorRecords);

        bulkUpload.setFailureRecordCount(errorRecords.size());
        bulkUpload.setSuccessRecordCount(nonBlankRows.size() - errorRecords.size());
        log.info("Final results - Total: {}, Success: {}, Failure: {}", 
                bulkUpload.getTotalRecordCount(), bulkUpload.getSuccessRecordCount(), bulkUpload.getFailureRecordCount());

        if (!errorRecords.isEmpty()) {
            MultipartFile errorFile = writeFailedRecordsToCsv(errorRecords, header, originalFileName);
            try {
                String errorFileName = generateErrorFileNameWithTimestamp(errorFile);
                // Update the path to include 'error' folder inside 'items'
                String s3Key = S3_ERROR_PATH + errorFileName;
                String downloadErrorPath = awsService.uploadFile(errorFile.getInputStream(), 
                    s3Key, errorFile.getSize());
                bulkUpload.setErrorFilePath(downloadErrorPath);
                bulkUpload.setStatus(BulkUploadStatus.FAILURE);
                bulkUpload.setReason("Bulk Upload completed, Success count " + bulkUpload.getSuccessRecordCount() + 
                    " and failure count " + bulkUpload.getFailureRecordCount() + " out of " + bulkUpload.getTotalRecordCount() + " total records");
            } catch (Exception e) {
                log.error("Failed to upload error file", e);
            }
        } else {
            bulkUpload.setErrorFilePath("");
            bulkUpload.setStatus(BulkUploadStatus.SUCCESS);
            bulkUpload.setReason("Bulk Upload completed successfully.");
        }
        
        bulkUpload.setUpdatedAt(OffsetDateTime.now(ZoneOffset.UTC));
        bulkUploadRepository.save(bulkUpload);
        
        // Memory cleanup: clear image data after processing to prevent memory leaks
        if (imageMap != null) {
            imageMap.clear();
            log.debug("Cleared image data from memory after processing");
        }
        
        log.info("Bulk upload with images processing completed for ID: {}. Success: {}, Errors: {}", 
                bulkUploadId, bulkUpload.getSuccessRecordCount(), errorRecords.size());
    }

    /**
     * Validates CSV rows and converts valid records into {@link Item} entities (without image handling).
     * <p>
     * Validation errors are collected as {@link ItemFailedRecord} entries; valid items are created in parallel.
     * Name and item-code uniqueness are enforced within the file and against the database.
     * </p>
     *
     * @param rows          non-blank CSV rows (header excluded)
     * @param language      language hint (passed through; translations are derived from CSV columns)
     * @param errorRecords  mutable list to collect failed records into
     * @param currentUserId current user id as string UUID (used as createdBy/updatedBy)
     * @param header        CSV header row (used to map dynamic language columns)
     * @return list of valid {@link Item} entities created from the input rows
     */
    @Transactional
    private List<Item> validateAndCreateItems(List<String[]> rows, String language, 
                                         List<ItemFailedRecord> errorRecords, String currentUserId, String[] header) {
        // Track seen names per language within this CSV (case-insensitive, per upload)
        Set<String> seenEnNames = Collections.newSetFromMap(new ConcurrentHashMap<>());
        Set<String> seenJaNames = Collections.newSetFromMap(new ConcurrentHashMap<>());
        Set<String> seenThNames = Collections.newSetFromMap(new ConcurrentHashMap<>());
        Set<String> seenItemCodes = Collections.newSetFromMap(new ConcurrentHashMap<>());

        return rows.parallelStream()
                .map(row -> createItemRequest(row, header))
                .map(itemRequest -> {
                    try {
                        validateItemRequest(itemRequest, seenEnNames, seenJaNames, seenThNames, seenItemCodes);
                        return convertToItem(itemRequest, UUID.fromString(currentUserId));
                    } catch (Exception e) {
                        synchronized(errorRecords) {
                            errorRecords.add(new ItemFailedRecord(itemRequest, e.getMessage()));
                        }
                        return null;
                    }
                })
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    /**
     * Validates CSV rows and converts valid records into {@link Item} entities while handling image upload.
     * <p>
     * Validation includes base field checks and verifying that {@code image_name} exists in the supplied ZIP.
     * For valid rows, images and thumbnails are uploaded to S3 and URLs are stored on the item.
     * </p>
     *
     * @param rows          non-blank CSV rows (header excluded)
     * @param language      language hint (passed through; translations are derived from CSV columns)
     * @param errorRecords  mutable list to collect failed records into
     * @param currentUserId current user id as string UUID (used as createdBy/updatedBy)
     * @param header        CSV header row (used to map dynamic language columns)
     * @param imageMap      extracted image bytes keyed by renamed filename
     * @param imageMapping  original-to-renamed filename mapping for ZIP entries
     * @return list of valid {@link Item} entities created from the input rows
     */
    @Transactional
    private List<Item> validateAndCreateItemsWithImages(List<String[]> rows, String language, 
                                         List<ItemFailedRecord> errorRecords, String currentUserId, String[] header,
                                         Map<String, byte[]> imageMap, Map<String, String> imageMapping) {
        // Track seen names per language within this CSV (case-insensitive, per upload)
        Set<String> seenEnNames = Collections.newSetFromMap(new ConcurrentHashMap<>());
        Set<String> seenJaNames = Collections.newSetFromMap(new ConcurrentHashMap<>());
        Set<String> seenThNames = Collections.newSetFromMap(new ConcurrentHashMap<>());
        Set<String> seenItemCodes = Collections.newSetFromMap(new ConcurrentHashMap<>());

        return rows.parallelStream()
                .map(row -> createItemRequest(row, header))
                .map(itemRequest -> {
                    try {
                        validateItemRequestWithImages(itemRequest, imageMap, imageMapping,
                                seenEnNames, seenJaNames, seenThNames, seenItemCodes);
                        return convertToItemWithImage(itemRequest, UUID.fromString(currentUserId), imageMap, imageMapping);
                    } catch (Exception e) {
                        synchronized(errorRecords) {
                            errorRecords.add(new ItemFailedRecord(itemRequest, e.getMessage()));
                        }
                        return null;
                    }
                })
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    /**
     * Creates BulkItemUploadRequest from CSV record data.
     * Maps CSV columns to request fields and builds language-specific name/description maps.
     */
    private BulkItemUploadRequest createItemRequest(String[] csvRow, String[] header) {
        Map<String, Integer> indexMap = new HashMap<>();
        for (int i = 0; i < header.length; i++) {
            String key = header[i] != null ? header[i].replace("*", "").trim().toLowerCase() : "";
            indexMap.put(key, i);
        }

        BulkItemUploadRequest itemRequest = new BulkItemUploadRequest();

        itemRequest.setItemCode(getByKey(csvRow, indexMap, COLUMN_ITEM_CODE));
        itemRequest.setBasePrice(getByKey(csvRow, indexMap, COLUMN_BASE_PRICE));
        itemRequest.setOutOfStock(getByKey(csvRow, indexMap, "out_of_stock"));
        itemRequest.setStatus(getByKey(csvRow, indexMap, COLUMN_STATUS));
        itemRequest.setAlcoholType(getByKey(csvRow, indexMap, COLUMN_ALCOHOL_TYPE));
        itemRequest.setNameEn(getByKey(csvRow, indexMap, "name_en"));
        itemRequest.setDescriptionEn(getByKey(csvRow, indexMap, "description_en"));
        itemRequest.setNameJa(getByKey(csvRow, indexMap, "name_ja"));
        itemRequest.setDescriptionJa(getByKey(csvRow, indexMap, "description_ja"));
        itemRequest.setNameTh(getByKey(csvRow, indexMap, "name_th"));
        itemRequest.setDescriptionTh(getByKey(csvRow, indexMap, "description_th"));
        itemRequest.setImageName(getByKey(csvRow, indexMap, COLUMN_IMAGE_NAME));

        // Fill generic maps for any language present in header
        Map<String, String> nameByLanguage = new HashMap<>();
        Map<String, String> descriptionByLanguage = new HashMap<>();
        for (Map.Entry<String, Integer> e : indexMap.entrySet()) {
            String key = e.getKey();
            if (key.startsWith(COLUMN_PREFIX_NAME)) {
                String code = key.substring(COLUMN_PREFIX_NAME.length());
                String value = getByKey(csvRow, indexMap, key);
                nameByLanguage.put(code, value);
            } else if (key.startsWith(COLUMN_PREFIX_DESCRIPTION)) {
                String code = key.substring(COLUMN_PREFIX_DESCRIPTION.length());
                String value = getByKey(csvRow, indexMap, key);
                descriptionByLanguage.put(code, value);
            }
        }
        int keyId = System.identityHashCode(itemRequest);
        NAME_BY_LANG_STORE.put(keyId, nameByLanguage);
        DESC_BY_LANG_STORE.put(keyId, descriptionByLanguage);

        return itemRequest;
    }

    private String getByKey(String[] csvRow, Map<String, Integer> indexMap, String key) {
        Integer i = indexMap.get(key);
        if (i == null || i < 0 || i >= csvRow.length) return null;
        return cleanValue(csvRow[i]);
    }

    private String cleanValue(String value) {
        if (value == null) return null;
        return value.trim();
    }

    /**
     * Performs batch-level persistence bookkeeping for validated items.
     * <p>
     * Items are processed in batches of {@link #BATCH_SIZE}. When an exception occurs for a batch,
     * individual item failures are recorded via {@link #handleBatchError(List, List, Exception)}.
     * </p>
     *
     * @param items        validated items to persist/record as successful
     * @param errorRecords mutable list to append batch failures to
     * @return list of items considered successful for this batch insert pass
     */
    @Transactional
    private List<Item> batchInsertItems(List<Item> items, List<ItemFailedRecord> errorRecords) {
        List<Item> successItems = new ArrayList<>();
        log.info("Starting batch insert for {} items", items.size());
        
        for (int i = 0; i < items.size(); i += BATCH_SIZE) {
            int end = Math.min(i + BATCH_SIZE, items.size());
            List<Item> batchedItems = items.subList(i, end);
            int batchIndex = i / BATCH_SIZE;
            log.info("Processing batch {} ({} items)", batchIndex, batchedItems.size());
                
            try {
                // Items and translations are already saved in convertToItem
                synchronized (successItems) {
                    successItems.addAll(batchedItems);
                }
                log.info("Successfully saved {} items in batch", batchedItems.size());
            } catch (Exception e) {
                log.error("Batch insert failed for batch {}", batchIndex, e);
                handleBatchError(batchedItems, errorRecords, e);
            }
        }
        
        log.info("Batch insert completed. Success: {}, Errors: {}", successItems.size(), errorRecords.size());
        return successItems;
    }

    /**
     * Records a batch insert failure by projecting each {@link Item} back into a {@link BulkItemUploadRequest}
     * and storing the exception message alongside it.
     *
     * @param items        items in the failed batch
     * @param errorRecords mutable list to append failed-record entries to
     * @param e            exception that caused the batch failure
     */
    private void handleBatchError(List<Item> items, List<ItemFailedRecord> errorRecords, Exception e) {
        items.forEach(item -> {
            BulkItemUploadRequest itemRequest = new BulkItemUploadRequest();
            // Map back the item to request for error recording
            itemRequest.setItemCode(item.getItemCode());
            itemRequest.setBasePrice(String.valueOf(item.getBasePrice()));
            itemRequest.setStatus(item.getStatus().name());
            if (item.getAlcoholType() != null) {
                itemRequest.setAlcoholType(item.getAlcoholType().name());
            }
            if (item.getItemOrderType() != null) {
                itemRequest.setItemOrderType(item.getItemOrderType().name());
            }
            // Get translations if available
            if (item.getTranslations() != null) {  // Add null check here
                item.getTranslations().forEach(translation -> {
                    switch (translation.getLanguageCode()) {
                        case "en":
                            itemRequest.setNameEn(translation.getName());
                            itemRequest.setDescriptionEn(translation.getDescription());
                            break;
                        case "ja":
                            itemRequest.setNameJa(translation.getName());
                            itemRequest.setDescriptionJa(translation.getDescription());
                            break;
                        case "th":
                            itemRequest.setNameTh(translation.getName());
                            itemRequest.setDescriptionTh(translation.getDescription());
                            break;
                    }
                });
            }
            // Set image name if available
            itemRequest.setImageName(item.getImageUrl());
            synchronized (errorRecords) {
                errorRecords.add(new ItemFailedRecord(itemRequest, e.getMessage()));
            }
        });
    }

    /**
     * Validates a single item request and throws a {@link ValidationException} when any errors are found.
     * <p>
     * Delegates to shared validation logic which accumulates errors (code, price, status, compulsory languages,
     * and uniqueness checks), then throws a combined error message.
     * </p>
     *
     * @param request       parsed item request from CSV row
     * @param seenEnNames   per-upload set for within-file uniqueness checks (English)
     * @param seenJaNames   per-upload set for within-file uniqueness checks (Japanese)
     * @param seenThNames   per-upload set for within-file uniqueness checks (Thai)
     * @param seenItemCodes per-upload set for within-file item-code uniqueness checks (case-insensitive)
     * @throws ValidationException when validation fails
     */
    private void validateItemRequest(BulkItemUploadRequest request,
                                     Set<String> seenEnNames,
                                     Set<String> seenJaNames,
                                     Set<String> seenThNames,
                                     Set<String> seenItemCodes) {
        List<String> errors = new ArrayList<>();
        
        // Use the shared validation logic
        validateItemRequestAndCollectErrors(request, errors, seenEnNames, seenJaNames, seenThNames, seenItemCodes);
        
        // Throw exception if any errors found
        if (!errors.isEmpty()) {
            throw new ValidationException(String.join("; ", errors));
        }
    }

    /**
     * Validates item request with additional image validation.
     * Performs base validation first, then validates that the specified image exists in the ZIP file.
     * Throws ValidationException with all collected errors if validation fails.
     */
    private void validateItemRequestWithImages(BulkItemUploadRequest request,
                                               Map<String, byte[]> imageMap,
                                               Map<String, String> imageMapping,
                                               Set<String> seenEnNames,
                                               Set<String> seenJaNames,
                                               Set<String> seenThNames,
                                               Set<String> seenItemCodes) {
        Locale userLocale = LocaleContextHolder.getLocale();
        List<String> errors = new ArrayList<>();

        // Run base validation and collect errors without throwing exception
        validateItemRequestAndCollectErrors(request, errors, seenEnNames, seenJaNames, seenThNames, seenItemCodes);

        // Additional image validation (only run if image parameters are provided)
        if (imageMap != null && imageMapping != null && request.getImageName() != null && !request.getImageName().trim().isEmpty()) {
            String imageName = request.getImageName().trim();
            
            try {
                // Get the renamed filename from the mapping
                String renamedImageFileName = imageMapping.get(imageName);
                
                // Validate that the image filename exists in the ZIP
                if (renamedImageFileName == null) {
                    errors.add(messageUtil.getMessage("bulk.upload.image.file.not.found", userLocale, imageName));
                } else {
                    // Get image data from the map using renamed filename
                    byte[] imageData = imageMap.get(renamedImageFileName);
                    
                    if (imageData == null) {
                        errors.add(messageUtil.getMessage("bulk.upload.image.file.not.found", userLocale, imageName));
                    }
                    // Per-image size validation (<= 1MB)
                    if (imageData != null && imageData.length > 1_048_576) {
                        errors.add(messageUtil.getMessage(MSG_BULK_UPLOAD_FILE_SIZE_EXCEEDED, userLocale, "1MB"));
                    }
                }
            } catch (Exception e) {
                errors.add(messageUtil.getMessage("bulk.upload.image.validation.failed", userLocale, e.getMessage()));
            }
        }

        // Throw exception with all collected errors (both base validation and image validation)
        if (!errors.isEmpty()) {
            throw new ValidationException(String.join("; ", errors));
        }
    }

    /**
     * Validates an item code for bulk upload.
     * <p>
     * Enforces presence, length, pattern constraints, within-file uniqueness (case-insensitive),
     * and database-level uniqueness for active items.
     * </p>
     *
     * @param itemCodeRaw   raw item code from CSV
     * @param seenItemCodes per-upload set tracking normalized codes for within-file duplicates
     * @param errors        mutable error list to append validation messages to
     * @param userLocale    locale used for localized message generation
     */
    private void validateItemCodeForBulk(String itemCodeRaw, Set<String> seenItemCodes,
                                         List<String> errors, Locale userLocale) {
        if (itemCodeRaw == null || itemCodeRaw.trim().isEmpty()) {
            errors.add(messageUtil.getMessage("item.itemCode.required", userLocale));
            return;
        }
        String code = itemCodeRaw.trim();
        if (code.length() > MAX_ITEM_CODE_LENGTH) {
            errors.add(messageUtil.getMessage("item.itemCode.size", userLocale));
            return;
        }
        if (!ITEM_CODE_PATTERN.matcher(code).matches()) {
            errors.add(messageUtil.getMessage("item.itemCode.pattern", userLocale));
            return;
        }
        String normalized = code.toLowerCase(Locale.ROOT);
        if (!seenItemCodes.add(normalized)) {
            errors.add(messageUtil.getMessage("item.itemCode.exists", userLocale, code));
            return;
        }
        if (itemRepository.existsActiveItemByItemCode(code)) {
            errors.add(messageUtil.getMessage("item.itemCode.exists", userLocale, code));
        }
    }

    /**
     * Validates name uniqueness for a given language (both within-file and DB-level checks).
     * 
     * @param name The name to validate
     * @param languageCode The language code (e.g., "en", "ja", "th")
     * @param seenNames Set of already seen names for within-file duplicate checking
     * @param errors List to collect validation errors
     * @param userLocale User locale for error messages
     * @param errorMessageKey Message key for duplicate name errors
     */
    private void validateNameUniqueness(String name, String languageCode, Set<String> seenNames, 
                                       List<String> errors, Locale userLocale, String errorMessageKey) {
        if (name != null && !name.isEmpty()) {
            String normalizedName = name.trim().toLowerCase();

            // Within-file duplicate check (case-insensitive for this CSV upload)
            if (!seenNames.add(normalizedName)) {
                errors.add(messageUtil.getMessage(errorMessageKey, userLocale, name));
            }

            // DB-level uniqueness check
            if (itemTranslationRepository.existsByNameIgnoreCaseAndLanguageCodeAndNotDeleted(name.trim(), languageCode)) {
                errors.add(messageUtil.getMessage(errorMessageKey, userLocale, name));
            }
        }
    }

    /**
     * Validates item request data and collects validation errors without throwing exceptions.
     * This method performs comprehensive validation including price, status, language fields,
     * and name uniqueness checks, adding any errors to the provided errors list.
     */
    private void validateItemRequestAndCollectErrors(BulkItemUploadRequest request,
                                                     List<String> errors,
                                                     Set<String> seenEnNames,
                                                     Set<String> seenJaNames,
                                                     Set<String> seenThNames,
                                                     Set<String> seenItemCodes) {
        Locale userLocale = LocaleContextHolder.getLocale();

        validateItemCodeForBulk(request.getItemCode(), seenItemCodes, errors, userLocale);

        validateBasePrice(request, errors, userLocale);
        validateStatus(request, errors, userLocale);
        validateOutOfStock(request, errors);
        validateCompulsoryLanguages(request, errors, userLocale);
        validateOptionalLanguageLengths(request, errors, userLocale);

        // Validate name uniqueness for each language (DB-level and within this CSV upload)
        validateNameUniqueness(request.getNameEn(), "en", seenEnNames, errors, userLocale, "bulk.item.upload.error.name.en.exists");
        validateNameUniqueness(request.getNameJa(), "ja", seenJaNames, errors, userLocale, "bulk.item.upload.error.name.ja.exists");
        validateNameUniqueness(request.getNameTh(), "th", seenThNames, errors, userLocale, "bulk.item.upload.error.name.th.exists");

        validateDietaryPreference(request, errors, userLocale);
        validateAlcoholType(request, errors, userLocale);
        validateItemOrderType(request, errors, userLocale);
    }

    /**
     * Validates {@code basePrice} is present, non-negative, numeric, and has at most two decimal places.
     */
    private void validateBasePrice(BulkItemUploadRequest request, List<String> errors, Locale userLocale) {
        try {
            String basePrice = request.getBasePrice();
            if (basePrice == null || basePrice.isEmpty()) {
                errors.add(messageUtil.getMessage("bulk.item.upload.error.base.price.required", userLocale));
                return;
            }
            double price = Double.parseDouble(basePrice);
            if (price < 0) {
                errors.add(messageUtil.getMessage("bulk.item.upload.error.base.price.positive", userLocale));
                return;
            }
            String[] parts = basePrice.split("\\.");
            if (parts.length > 1 && parts[1].length() > 2) {
                errors.add(messageUtil.getMessage("bulk.item.upload.error.base.price.decimal", userLocale));
            }
        } catch (NumberFormatException e) {
            errors.add(messageUtil.getMessage("bulk.item.upload.error.base.price.invalid", userLocale));
        }
    }

    /**
     * Validates item {@code status} against {@code EntityStatus} enum names.
     */
    private void validateStatus(BulkItemUploadRequest request, List<String> errors, Locale userLocale) {
        String status = request.getStatus();
        if (status == null || status.isEmpty()) {
            errors.add(messageUtil.getMessage("status.required", userLocale));
            return;
        }
        try {
            EntityStatus.valueOf(status.toUpperCase());
        } catch (IllegalArgumentException e) {
            errors.add(messageUtil.getMessage("bulk.item.upload.error.status.invalid", userLocale));
        }
    }

    private void validateOutOfStock(BulkItemUploadRequest request, List<String> errors) {
        String outOfStock = request.getOutOfStock();
        if (outOfStock == null || outOfStock.isEmpty()) {
            return;
        }
        String normalized = outOfStock.toLowerCase();
        if (!normalized.equals("true") && !normalized.equals("false")) {
            errors.add(messageUtil.getMessage("bulk.item.upload.error.out.of.stock.invalid", LocaleContextHolder.getLocale()));
        }
    }

    /**
     * For each configured compulsory language, ensures name/description are present and within max lengths.
     */
    private void validateCompulsoryLanguages(BulkItemUploadRequest request, List<String> errors, Locale userLocale) {
        try {
            RestaurantChainConfigProperties.RestaurantChainData chain = restaurantChainConfigProperties.getChain();
            List<RestaurantChainConfigProperties.SupportedLanguage> supported =
                    chain != null ? chain.getSupportedLanguages() : Collections.emptyList();
            if (supported == null) {
                return;
            }

            Map<String, String> nameMap = NAME_BY_LANG_STORE.get(System.identityHashCode(request));
            Map<String, String> descMap = DESC_BY_LANG_STORE.get(System.identityHashCode(request));

            for (RestaurantChainConfigProperties.SupportedLanguage lang : supported) {
                if (!lang.isCompulsory()) {
                    continue;
                }
                String code = lang.getLanguageCode();
                validateCompulsoryLanguageFields(code, nameMap, descMap, errors, userLocale);
            }
        } catch (Exception e) {
            log.error("Error validating compulsory languages", e);
        }
    }

    /**
     * Validates required name/description for a single compulsory {@code languageCode} using CSV thread-local maps.
     */
    private void validateCompulsoryLanguageFields(String languageCode,
                                                  Map<String, String> nameMap,
                                                  Map<String, String> descMap,
                                                  List<String> errors,
                                                  Locale userLocale) {
        String nameVal = nameMap != null ? nameMap.get(languageCode) : null;
        String descVal = descMap != null ? descMap.get(languageCode) : null;

        if (nameVal == null || nameVal.trim().isEmpty()) {
            String errorMsg = "Name is required for language: " + languageCode;
            log.warn("Validation failed for {}: {}", languageCode, errorMsg);
            errors.add(errorMsg);
        } else if (nameVal.length() > MAX_NAME_LENGTH) {
            errors.add(messageUtil.getMessage("bulk.item.upload.error.name.length", userLocale));
        }

        if (descVal == null || descVal.trim().isEmpty()) {
            String errorMsg = "Description is required for language: " + languageCode;
            log.warn("Validation failed for {}: {}", languageCode, errorMsg);
            errors.add(errorMsg);
        } else if (descVal.length() > MAX_DESCRIPTION_LENGTH) {
            errors.add(messageUtil.getMessage("bulk.item.upload.error.description.length", userLocale));
        }
    }

    /**
     * Delegates optional JA/TH name and description columns to length checks when values are present.
     */
    private void validateOptionalLanguageLengths(BulkItemUploadRequest request, List<String> errors, Locale userLocale) {
        validateOptionalLanguageLengths(request.getNameJa(), request.getDescriptionJa(),
                "bulk.item.upload.error.name.ja.length", "bulk.item.upload.error.description.ja.length",
                errors, userLocale);
        validateOptionalLanguageLengths(request.getNameTh(), request.getDescriptionTh(),
                "bulk.item.upload.error.name.th.length", "bulk.item.upload.error.description.th.length",
                errors, userLocale);
    }

    /**
     * Validates max length for a single optional language pair using the supplied message keys.
     */
    private void validateOptionalLanguageLengths(String name, String description,
                                                 String nameLengthMessageKey,
                                                 String descriptionLengthMessageKey,
                                                 List<String> errors,
                                                 Locale userLocale) {
        if (name == null || name.isEmpty()) {
            return;
        }
        if (name.length() > MAX_NAME_LENGTH) {
            errors.add(messageUtil.getMessage(nameLengthMessageKey, userLocale));
        }
        if (description != null && description.length() > MAX_DESCRIPTION_LENGTH) {
            errors.add(messageUtil.getMessage(descriptionLengthMessageKey, userLocale));
        }
    }

    private void validateDietaryPreference(BulkItemUploadRequest request, List<String> errors, Locale userLocale) {
        String dietaryPreference = request.getDietaryPreference();
        if (dietaryPreference == null || dietaryPreference.isEmpty()) {
            return;
        }
        try {
            DietaryPreference.valueOf(dietaryPreference.toUpperCase());
        } catch (IllegalArgumentException e) {
            errors.add(messageUtil.getMessage("bulk.item.upload.error.dietary.preference.invalid", userLocale));
        }
    }

    /**
     * Requires {@code alcoholType} and validates it against {@code AlcoholType} enum names.
     */
    private void validateAlcoholType(BulkItemUploadRequest request, List<String> errors, Locale userLocale) {
        String alcoholType = request.getAlcoholType();
        if (alcoholType == null || alcoholType.isEmpty()) {
            errors.add(messageUtil.getMessage("bulk.item.upload.error.alcohol.type.required", userLocale));
        } else {
            try {
                AlcoholType.valueOf(request.getAlcoholType().toUpperCase());
            } catch (IllegalArgumentException e) {
                errors.add(messageUtil.getMessage("bulk.item.upload.error.alcohol.flag.invalid", userLocale));
            }
        }
    }

    /**
     * Requires {@code itemOrderType} and validates it against {@code ItemOrderType} enum names.
     */
    private void validateItemOrderType(BulkItemUploadRequest request, List<String> errors, Locale userLocale) {
        String itemOrderType = request.getItemOrderType();
        if (itemOrderType == null || itemOrderType.isEmpty()) {
            errors.add(messageUtil.getMessage("bulk.item.upload.error.item.order.type.required", userLocale));
            return;
        }
        try {
            ItemOrderType.valueOf(itemOrderType.toUpperCase());
        } catch (IllegalArgumentException e) {
            errors.add(messageUtil.getMessage("item.error.invalid.itemOrderType", userLocale));
        }
    }

    
    /**
     * Builds the base {@link Item} entity from a validated bulk-upload request.
     * <p>
     * Parses required enums and primitives, sets timestamps and deletion flags, and resolves {@code createdBy/updatedBy}
     * via {@link UserRepository} to ensure managed {@link User} entities are associated.
     * </p>
     *
     * @param request   validated bulk-upload request
     * @param creatorId id of the user creating the item
     * @return newly constructed item entity (not yet translated)
     * @throws ValidationException when the creator user cannot be resolved
     */
    private Item buildBaseItem(BulkItemUploadRequest request, UUID creatorId) {
        Locale userLocale = LocaleContextHolder.getLocale();

        Item item = new Item();
        item.setItemCode(request.getItemCode().trim());
        item.setBasePrice(Double.parseDouble(request.getBasePrice()));
        item.setOutOfStock(request.getOutOfStock() != null && !request.getOutOfStock().isEmpty() 
            && Boolean.parseBoolean(request.getOutOfStock()));
        item.setStatus(EntityStatus.valueOf(request.getStatus().toUpperCase()));
        item.setIsDeleted(false);  // ✅ Always false on create
        if (request.getDietaryPreference() != null && !request.getDietaryPreference().isEmpty()) {
            item.setDietaryPreference(DietaryPreference.valueOf(request.getDietaryPreference().toUpperCase()));
        }
        if (request.getAlcoholType() != null && !request.getAlcoholType().isEmpty()) {
            item.setAlcoholType(AlcoholType.valueOf(request.getAlcoholType().toUpperCase()));
        }
        item.setCreatedAt(OffsetDateTime.now(ZoneOffset.UTC));
        item.setUpdatedAt(OffsetDateTime.now(ZoneOffset.UTC));
        
        // Fix User/UUID issue by fetching User entity
        User creator = userRepository.findById(creatorId)
            .orElseThrow(() -> new ValidationException(
                messageUtil.getMessage("user.not.found", userLocale)));
        item.setCreatedBy(creator);
        item.setUpdatedBy(creator);

        return item;
    }

    /**
     * Persists an {@link Item} and creates {@link ItemTranslation} rows for all populated languages in the request.
     *
     * @param item    item entity to persist
     * @param request request that contains language-specific name/description maps
     * @return the saved item with its translations attached
     */
    private Item saveItemWithTranslations(Item item, BulkItemUploadRequest request) {
        // Save the item first
        Item savedItem = itemRepository.save(item);

        // Create translations
        List<ItemTranslation> translations = new ArrayList<>();

        // Add translations for any language present
        Map<String, String> nameMap = NAME_BY_LANG_STORE.get(System.identityHashCode(request));
        Map<String, String> descMap = DESC_BY_LANG_STORE.get(System.identityHashCode(request));
        if (nameMap != null) {
            for (Map.Entry<String, String> e : nameMap.entrySet()) {
                String code = e.getKey();
                String name = e.getValue();
                if (name != null && !name.trim().isEmpty()) {
                    ItemTranslation t = new ItemTranslation();
                    t.setItem(savedItem);
                    t.setLanguageCode(code);
                    t.setName(name.trim());
                    String desc = descMap != null ? descMap.get(code) : null;
                    t.setDescription(desc != null ? desc.trim() : null);
                    translations.add(itemTranslationRepository.save(t));
                }
            }
        }

        // Set translations to the item
        savedItem.setTranslations(translations);

        return savedItem;
    }

    private Item convertToItem(BulkItemUploadRequest request, UUID creatorId) {
        Item item = buildBaseItem(request, creatorId);
        return saveItemWithTranslations(item, request);
    }
    
    /**
     * Converts a validated bulk-upload request into an {@link Item}, uploads an optional image + thumbnail to S3,
     * and persists translations.
     *
     * @param request      validated request parsed from CSV
     * @param creatorId    user id creating the item
     * @param imageMap     extracted image bytes keyed by renamed filename
     * @param imageMapping original-to-renamed filename mapping for ZIP entries
     * @return saved item with translation rows (and image URLs when applicable)
     * @throws ValidationException when image upload fails
     */
    private Item convertToItemWithImage(BulkItemUploadRequest request, UUID creatorId, Map<String, byte[]> imageMap, Map<String, String> imageMapping) {
        Item item = buildBaseItem(request, creatorId);

        // Handle image upload if image name is provided
        if (request.getImageName() != null && !request.getImageName().trim().isEmpty()) {
            try {
                // Get the renamed filename from the mapping
                String renamedImageFileName = imageMapping.get(request.getImageName().trim());
                
                // Get image data from the map using renamed filename
                byte[] imageData = imageMap.get(renamedImageFileName);
                
                // Upload image to S3 using new S3 key pattern
                String imageUrl = uploadImageToS3(imageData, renamedImageFileName);
                item.setImageUrl(imageUrl);

                // Create and upload thumbnail
                String thumbnailUrl = uploadThumbnailToS3(imageData, renamedImageFileName);
                item.setThumbnailUrl(thumbnailUrl);
                log.info("Successfully uploaded image for item {}: {} -> {} (S3 URL: {})", 
                        request.getNameEn(), request.getImageName(), renamedImageFileName, imageUrl);
                        
            } catch (Exception e) {
                log.error("Failed to upload image for item {}: {}", request.getNameEn(), e.getMessage(), e);
                // Re-throw the exception to fail the record with specific error message
                throw new ValidationException("Image upload failed: " + e.getMessage());
            }
        }

        return saveItemWithTranslations(item, request);
    }

    /**
     * Create thumbnail and upload with thumb_ prefix in same directory as original
     */
    private String uploadThumbnailToS3(byte[] imageData, String originalFileName) throws IOException {
        try (java.io.ByteArrayInputStream ignored = new java.io.ByteArrayInputStream(imageData)) {
            String extension = originalFileName.substring(originalFileName.lastIndexOf('.') + 1).toLowerCase();
            byte[] thumbBytes = imageThumbnailUtil.createThumbnail(imageData, extension);

            String fileNameWithoutExt = originalFileName.substring(0, originalFileName.lastIndexOf('.'));
            String thumbFileName = "thumb_" + fileNameWithoutExt + "." + extension;
            String s3Key = "restaurant/item-images/" + thumbFileName;
            return awsService.uploadFile(new java.io.ByteArrayInputStream(thumbBytes), s3Key, thumbBytes.length);
        } catch (Exception e) {
            log.error("Failed to upload thumbnail to S3: {}", e.getMessage(), e);
            throw new IOException("Failed to upload thumbnail to S3: " + e.getMessage(), e);
        }
    }

    

    /**
     * Generates a CSV error file containing the original columns plus an {@code Error} column with failure messages.
     *
     * @param failedRecords    list of failed records to write
     * @param headers          original CSV headers
     * @param originalFilename original filename (used to name the generated error file)
     * @return multipart file containing the error CSV content, or {@code null} when generation fails
     */
    private MultipartFile writeFailedRecordsToCsv(List<ItemFailedRecord> failedRecords, String[] headers, String originalFilename) {
        String[] header = Arrays.copyOf(headers, headers.length + 1);
        header[headers.length] = ERROR_COLUMN;
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        try (CSVPrinter csvPrinter = new CSVPrinter(new OutputStreamWriter(outputStream, StandardCharsets.UTF_8),
                CSVFormat.DEFAULT.withHeader(header))) {
            for (ItemFailedRecord failedRecord : failedRecords) {
                List<Object> row = new ArrayList<>();
                Object itemRequest = failedRecord.getItemRequest();
                BulkItemUploadRequest item = itemRequest instanceof BulkItemUploadRequest
                        ? (BulkItemUploadRequest) itemRequest
                        : null;
                appendFailedRecordRowCells(row, header, item);
                row.add(failedRecord.getErrorMessage());
                csvPrinter.printRecord(row);
            }
            csvPrinter.flush();

            String errorFilename = originalFilename != null ? originalFilename : "Error.csv";

            return new CustomMultipartFile(
                    outputStream.toByteArray(),
                    "csvFile",
                    errorFilename,
                    CONTENT_TYPE_CSV
            );
        } catch (IOException e) {
            log.error("Failed to generate error file", e);
            return null;
        }
    }

    private void appendFailedRecordRowCells(List<Object> row, String[] header, BulkItemUploadRequest item) {
        for (int i = 0; i < header.length - 1; i++) {
            String normalizedHeader = normalizeHeader(header[i]);
            row.add(resolveCellValue(normalizedHeader, item));
        }
    }

    private String normalizeHeader(String rawHeader) {
        return rawHeader != null ? rawHeader.replace("*", "").trim().toLowerCase() : "";
    }

    /**
     * Maps a normalized CSV header to the corresponding field value on {@code item} for error export rows.
     */
    private Object resolveCellValue(String normalizedHeader, BulkItemUploadRequest item) {
        if (item == null) {
            return "";
        }
        switch (normalizedHeader) {
            case COLUMN_ITEM_CODE:
                return item.getItemCode();
            case COLUMN_BASE_PRICE:
                return item.getBasePrice();
            case "out_of_stock":
                return item.getOutOfStock();
            case COLUMN_STATUS:
                return item.getStatus();
            case COLUMN_ALCOHOL_TYPE:
                return item.getAlcoholType();
            case COLUMN_ITEM_ORDER_TYPE:
                return item.getItemOrderType();
            case "name_en":
                return item.getNameEn();
            case "description_en":
                return item.getDescriptionEn();
            case COLUMN_IMAGE_NAME:
                return item.getImageName();
            default:
                return resolveDynamicLanguageCell(normalizedHeader, item);
        }
    }

    /**
     * Resolves dynamic {@code name_<lang>} / {@code description_<lang>} columns from per-request language maps.
     */
    private Object resolveDynamicLanguageCell(String normalizedHeader, BulkItemUploadRequest item) {
        if (normalizedHeader.startsWith(COLUMN_PREFIX_NAME)) {
            String code = normalizedHeader.substring(COLUMN_PREFIX_NAME.length());
            Map<String, String> nm = NAME_BY_LANG_STORE.get(System.identityHashCode(item));
            return nm != null ? nm.get(code) : null;
        }
        if (normalizedHeader.startsWith(COLUMN_PREFIX_DESCRIPTION)) {
            String code = normalizedHeader.substring(COLUMN_PREFIX_DESCRIPTION.length());
            Map<String, String> dm = DESC_BY_LANG_STORE.get(System.identityHashCode(item));
            return dm != null ? dm.get(code) : null;
        }
        return "";
    }

    /**
     * Generates an error CSV filename by appending a UTC timestamp before the extension.
     *
     * @param file source multipart file (used for base name/extension)
     * @return filename in the form {@code <base>_error_<yyyyMMdd_HHmmss><ext>}
     */
    private static String generateErrorFileNameWithTimestamp(MultipartFile file) {
        String originalFilename = file.getOriginalFilename();
        if (originalFilename == null) {
            originalFilename = ERROR_COLUMN;
        }
        String extension = originalFilename.contains(".")
            ? originalFilename.substring(originalFilename.lastIndexOf("."))
            : ".csv";
        String baseName = originalFilename.contains(".")
            ? originalFilename.substring(0, originalFilename.lastIndexOf("."))
            : originalFilename;

        String timestamp = LocalDateTime.now(ZoneOffset.UTC).format(DateTimeFormatter.ofPattern(DATE_TIME_PATTERN));

        return baseName + "_error_" + timestamp + extension;
    }


    /**
     * Extracts images from ZIP file and creates mapping from original to renamed filenames.
     * Validates image file extensions and generates unique filenames to prevent conflicts.
     * Stores the original-to-renamed filename mapping for later validation.
     */
    private Map<String, byte[]> extractImagesFromZip(MultipartFile zipFile, Locale locale, Map<String, String> imageMapping) throws IOException {
        Map<String, byte[]> imageMap = new HashMap<>();
        
        // Validate ZIP file
        validateZipFile(zipFile, locale);
        
        try (ZipInputStream zipInputStream = new ZipInputStream(zipFile.getInputStream())) {
            ZipEntry entry;
            
            while ((entry = zipInputStream.getNextEntry()) != null) {
                if (!entry.isDirectory()) {
                    String originalFileName = entry.getName();
                    
                    // Validate image file extension
                    if (BulkUploadImageFilenameUtils.isValidImageFile(originalFileName)) {
                        // Generate renamed filename using same logic as AttachmentService
                        String renamedFileName = BulkUploadImageFilenameUtils.generateBulkUploadImageFileName(originalFileName);
                        
                        // Store the original to renamed mapping
                        imageMapping.put(originalFileName, renamedFileName);
                        
                        // Read image data
                        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
                        byte[] buffer = new byte[BUFFER_SIZE];
                        int length;
                        
                        while ((length = zipInputStream.read(buffer)) > 0) {
                            outputStream.write(buffer, 0, length);
                        }
                        
                        byte[] imageData = outputStream.toByteArray();
                        
                        // Store with renamed filename
                        imageMap.put(renamedFileName, imageData);
                    } else {
                        log.warn("Skipping non-image file: {}", originalFileName);
                    }
                }
                zipInputStream.closeEntry();
            }
        }
        
        if (imageMap.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, 
                    messageUtil.getMessage("bulk.upload.no.images.found", locale));
        }
        
        log.info("Successfully extracted and renamed {} images from ZIP file", imageMap.size());
        return imageMap;
    }
    
    
    /**
     * Validate ZIP file
     */
    private void validateZipFile(MultipartFile zipFile, Locale locale) {
        // Validate ZIP file format
        String zipOriginalFilename = zipFile.getOriginalFilename();
        if (zipOriginalFilename == null || !zipOriginalFilename.toLowerCase().endsWith(".zip")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, 
                    messageUtil.getMessage("bulk.upload.invalid.zip.file", locale));
        }
        
        // Validate ZIP file size (30MB limit for ZIP files)
        long maxZipFileSize = MAX_ZIP_FILE_SIZE;
        if (zipFile.getSize() > maxZipFileSize) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                messageUtil.getMessage(MSG_BULK_UPLOAD_FILE_SIZE_EXCEEDED, locale, "30MB"));
        }
    }
    
    /**
     * Upload image to S3
     */
    private String uploadImageToS3(byte[] imageData, String fileName) throws IOException {
        try (ByteArrayInputStream inputStream = new ByteArrayInputStream(imageData)) {
            // Use new S3 key pattern: restaurant/item-images/
            String s3Key = "restaurant/item-images/" + fileName;
            
            // Upload to S3 using existing AWSService
            String s3Url = awsService.uploadFile(inputStream, s3Key, imageData.length);
            
            log.info("Successfully uploaded image to S3. Key: {}, URL: {}", s3Key, s3Url);
            return s3Url;
            
        } catch (Exception e) {
            log.error("Failed to upload image to S3: {}", e.getMessage(), e);
            throw new IOException("Failed to upload image to S3: " + e.getMessage(), e);
        }
    }
}
