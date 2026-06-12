package com.gulfnet.usermanagement.service.impl;

import com.gulfnet.shared_library.entity.*;
import com.gulfnet.shared_library.enums.ActionType;
import com.gulfnet.shared_library.enums.BulkUploadStatus;
import com.gulfnet.shared_library.enums.EmploymentType;
import com.gulfnet.shared_library.enums.EntityStatus;
import com.gulfnet.shared_library.enums.RequestStatus;
import com.gulfnet.shared_library.enums.UploadType;
import com.gulfnet.shared_library.exception.BadRequestException;
import com.gulfnet.shared_library.exception.EmailSendingException;
import com.gulfnet.shared_library.model.request.BulkUserUploadRequest;
import com.gulfnet.shared_library.model.request.CustomMultipartFile;
import com.gulfnet.shared_library.model.request.FailedRecord;
import com.gulfnet.shared_library.model.response.dto.*;
import com.gulfnet.shared_library.repository.*;
import com.gulfnet.shared_library.repository.AuditTrailRepository;
import com.gulfnet.shared_library.repository.ShiftTranslationRepository;
import com.gulfnet.shared_library.config.AWSService;
import com.gulfnet.usermanagement.service.BulkUserUploadService;
import com.gulfnet.shared_library.util.BulkUploadImageFilenameUtils;
import com.gulfnet.shared_library.util.ImageThumbnailUtil;
import com.gulfnet.shared_library.util.PasswordGeneratorUtil;
import com.gulfnet.shared_library.util.EmailSender;
import com.gulfnet.usermanagement.config.EmailProperties;
import com.gulfnet.usermanagement.config.FrontendUrlProperties;
import com.gulfnet.usermanagement.util.BulkUploadSortUtil;
import com.gulfnet.usermanagement.util.RegistrationEmailHtmlFormatter;
import com.gulfnet.usermanagement.util.MessageUtil;
import com.gulfnet.usermanagement.validator.BulkUploadValidator;
import com.gulfnet.usermanagement.validator.CsvFileUploadValidator;
import org.springframework.security.crypto.password.PasswordEncoder;
import com.opencsv.CSVReader;
import com.opencsv.exceptions.CsvException;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.*;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.OffsetDateTime;
import java.util.*;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
@Slf4j
public class BulkUserUploadServiceImpl implements BulkUserUploadService {

    private final BulkUploadRepository bulkUploadRepository;
    private final BulkUploadValidator bulkUploadValidator;
    private final CsvFileUploadValidator csvFileUploadValidator;
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final ShiftRepository shiftRepository;
    private final ShiftTranslationRepository shiftTranslationRepository;
    private final UserShiftMappingRepository userShiftMappingRepository;
    private final RestaurantRepository restaurantRepository;
    private final RestaurantGroupRepository restaurantGroupRepository;
    private final MessageUtil messageUtil;
    private final AWSService awsService;
    private final ImageThumbnailUtil imageThumbnailUtil;
    private final AuditTrailRepository auditTrailRepository;
    private final EmailSender emailSender;
    private final EmailProperties emailProperties;
    private final PasswordEncoder passwordEncoder;
    private final FrontendUrlProperties frontendUrlProperties;
    private final RegistrationEmailHtmlFormatter registrationEmailHtmlFormatter;

   

    private static final String ERROR_COLUMN = "Error";
    private static final String BULK_UPLOAD_DIR = "bulk-upload";
    private static final int BATCH_SIZE = 50;
    private static final String UNKNOWN_ROLE = "UNKNOWN";
    private static final int BUFFER_SIZE = 8192;
    private static final long MAX_ZIP_FILE_SIZE = 30L * 1024 * 1024; // 30MB in bytes
    
    /**
     * Retrieves a paginated list of bulk upload records with optional filters for
     * status, search term, sort options, and upload type. Applies custom in-memory
     * sorting when filename/name-based sorting is requested.
     *
     * @param status       optional bulk upload status filter
     * @param page         page number (1-based)
     * @param size         page size
     * @param search       optional search keyword
     * @param sortBy       field to sort by
     * @param sortDirection sort direction (ASC or DESC)
     * @param uploadType   optional upload type filter
     * @return {@link ResponseDto} containing {@link BulkUploadListResponse} with pagination metadata
     */
    public ResponseDto<BulkUploadListResponse> getBulkUploads(
            BulkUploadStatus status,
            int page,
            int size,
            String search,
            String sortBy,
            String sortDirection,
            UploadType uploadType
    ) {
        Locale userLocale = LocaleContextHolder.getLocale();

        Sort.Direction direction = Sort.Direction.fromString(sortDirection);

        Page<BulkUpload> bulkUploadsPage;
        long totalElements;
        int totalPages;

        // For filename / name based sorting use custom in-memory sorting with proper pagination
        if (BulkUploadSortUtil.requiresCustomSorting(sortBy)) {
            int zeroBasedPage = page > 0 ? page - 1 : 0;

            // Get all filtered results without pagination
            Pageable allPageRequest = PageRequest.of(0, Integer.MAX_VALUE);
            Page<BulkUpload> allResults = BulkUploadSortUtil.getFilteredBulkUploads(
                    bulkUploadRepository,
                    status != null ? status.name() : null,
                    search,
                    uploadType,
                    allPageRequest
            );

            // Apply custom filename/name sorting with pagination
            bulkUploadsPage = BulkUploadSortUtil.applyCustomSortingWithPagination(
                    allResults, sortBy, direction, zeroBasedPage, size);

            totalElements = allResults.getTotalElements();
            totalPages = (int) Math.ceil((double) totalElements / size);
        } else {
            // Use normal database sorting for other fields
            Pageable pageable = BulkUploadSortUtil.createPageable(page - 1, size, sortBy, direction);

            bulkUploadsPage = BulkUploadSortUtil.getFilteredBulkUploads(
                    bulkUploadRepository,
                    status != null ? status.name() : null,
                    search,
                    uploadType,
                    pageable
            );

            totalElements = bulkUploadsPage.getTotalElements();
            totalPages = bulkUploadsPage.getTotalPages();
        }

        // Convert to response DTOs
        List<BulkUploadWithPresignedUrls> bulkUploadsWithUrls = bulkUploadsPage.getContent().stream()
                .map(this::convertToResponseWithPresignedUrls)
                .collect(Collectors.toList());

        // Create response with the potentially sorted list
        BulkUploadListResponse response = BulkUploadListResponse.builder()
                .bulkUploads(bulkUploadsWithUrls)
                .count((long) bulkUploadsPage.getNumberOfElements())
                .total(totalElements)
                .metaData(PaginationMetaData.builder()
                        .page(page)
                        .size(size)
                        .totalPages(totalPages)
                        .totalRecords(totalElements)
                        .build())
                .errors(null)
                .build();
        String message = messageUtil.getMessage("bulk.upload.list.success", userLocale);

        return ResponseDto.<BulkUploadListResponse>builder()
                .message(message)
                .data(response)
                .build();
    }
    
    /**
     * Converts a {@link BulkUpload} entity into a DTO enriched with pre-signed URLs
     * for the original and error files, as well as basic metadata fields.
     *
     * @param bulkUpload the bulk upload entity to convert
     * @return a {@link BulkUploadWithPresignedUrls} DTO with pre-signed URLs and metadata
     */
    private BulkUploadWithPresignedUrls convertToResponseWithPresignedUrls(BulkUpload bulkUpload) {
        return BulkUploadSortUtil.populateBulkUploadWithPresignedUrlsMetadata(
                BulkUploadWithPresignedUrls.builder()
                        .id(bulkUpload.getId())
                        .originalFilePresignedUrl(
                                bulkUpload.getFilePath() != null ? awsService.getPreSignedUrl(bulkUpload.getFilePath()) : null)
                        .errorFilePresignedUrl(
                                bulkUpload.getErrorFilePath() != null ? awsService.getPreSignedUrl(bulkUpload.getErrorFilePath()) : null)
                        .originalFileName(extractFileName(bulkUpload.getFilePath())),
                bulkUpload)
                .build();
    }

    private String extractFileName(String filePath) {
        if (filePath == null || filePath.isEmpty()) {
            return null;
        }
        int lastSlashIndex = Math.max(filePath.lastIndexOf('/'), filePath.lastIndexOf('\\'));
        String fileName = lastSlashIndex >= 0 ? filePath.substring(lastSlashIndex + 1) : filePath;

        return fileName.replaceAll("_(\\d{8}_\\d{6})(?=\\.)", "");
    }


    /**
     * Asynchronously processes and saves users from a locally stored CSV file and
     * an optional image ZIP, updating the bulk upload record with success/failure
     * counts, handling error reporting, and cleaning up temporary resources.
     *
     * @param localFilePath      path to the temporary CSV file on the server
     * @param imageZipFileBytes  optional ZIP file bytes containing user images
     * @param imageZipFileName   original ZIP filename for logging
     * @param userId             ID of the user who initiated the bulk upload
     * @param language           preferred language code for messages
     * @param bulkUploadId       ID of the bulk upload record to update
     * @param totalRecords       string representation of total records (for logging/consistency)
     */
    @Override
    @Async("bulkUploadTaskExecutor")
    public void processAndSaveUsersFromLocalFile(String localFilePath, byte[] imageZipFileBytes, String imageZipFileName, String userId, String language, 
                                                 UUID bulkUploadId, String totalRecords) {
        try {
            BulkUpload bulkUpload = loadBulkUpload(bulkUploadId);
            if (bulkUpload == null) {
                return;
            }
            
            List<FailedRecord> errorRecords = new ArrayList<>();
            ImageProcessingResult imageResult = processImages(imageZipFileBytes, imageZipFileName, bulkUpload);
            if (imageResult == null) {
                return;
            }
            
            CsvProcessingResult csvResult = processCsvFile(localFilePath, bulkUpload);
            if (csvResult == null) {
                return;
            }
            
            processUsers(csvResult.getNonBlankRows(), language, errorRecords, userId, 
                    imageResult.getImageMap(), imageResult.getImageMapping(), csvResult.getHeader());
            
            updateBulkUploadStatus(bulkUpload, errorRecords, csvResult.getNonBlankRows().size(), userId, csvResult.getHeader());
            createAuditTrailForBulkUpload(bulkUpload, userId, bulkUploadId);
            cleanupImageData(imageResult.getImageMap());
            
            log.info("Bulk upload processing completed for ID: {}. Success: {}, Errors: {}", 
                    bulkUploadId, bulkUpload.getSuccessRecordCount(), errorRecords.size());
        } finally {
            deleteLocalFile(localFilePath);
        }
    }
    
    /**
     * Loads bulk upload entity from repository
     */
    private BulkUpload loadBulkUpload(UUID bulkUploadId) {
        Optional<BulkUpload> bulkUploadOpt = bulkUploadRepository.findById(bulkUploadId);
        if (bulkUploadOpt.isEmpty()) {
            log.error("Bulk upload not found: {}", bulkUploadId);
            return null;
        }
        return bulkUploadOpt.get();
    }
    
    /**
     * Result class for image processing
     */
    private static class ImageProcessingResult {
        private final Map<String, byte[]> imageMap;
        private final Map<String, String> imageMapping;
        
        ImageProcessingResult(Map<String, byte[]> imageMap, Map<String, String> imageMapping) {
            this.imageMap = imageMap;
            this.imageMapping = imageMapping;
        }
        
        Map<String, byte[]> getImageMap() {
            return imageMap;
        }
        
        Map<String, String> getImageMapping() {
            return imageMapping;
        }
    }
    
    /**
     * Processes images from ZIP file if provided
     */
    private ImageProcessingResult processImages(byte[] imageZipFileBytes, String imageZipFileName, BulkUpload bulkUpload) {
        if (imageZipFileBytes == null || imageZipFileBytes.length == 0) {
            log.info("No ZIP file provided or ZIP file is empty");
            return new ImageProcessingResult(null, null);
        }
        
        log.info("ZIP file provided: {} (size: {} bytes)", imageZipFileName, imageZipFileBytes.length);
        try {
            Map<String, String> imageMapping = new HashMap<>();
            Map<String, byte[]> imageMap = extractImagesFromZipBytes(imageZipFileBytes, imageZipFileName, 
                    LocaleContextHolder.getLocale(), imageMapping);
            log.info("Successfully extracted {} images from ZIP file", imageMap.size());
            log.info("Image mapping: {}", imageMapping);
            return new ImageProcessingResult(imageMap, imageMapping);
        } catch (Exception e) {
            log.error("Failed to extract images from ZIP: {}", e.getMessage(), e);
            setBulkUploadFailure(bulkUpload, "Failed to extract images from ZIP file: " + e.getMessage());
            return null;
        }
    }
    
    /**
     * Result class for CSV processing
     */
    private static class CsvProcessingResult {
        private final String[] header;
        private final List<String[]> nonBlankRows;
        
        CsvProcessingResult(String[] header, List<String[]> nonBlankRows) {
            this.header = header;
            this.nonBlankRows = nonBlankRows;
        }
        
        String[] getHeader() {
            return header;
        }
        
        List<String[]> getNonBlankRows() {
            return nonBlankRows;
        }
    }
    
    /**
     * Processes CSV file: reads, validates headers, and filters blank rows
     */
    private CsvProcessingResult processCsvFile(String localFilePath, BulkUpload bulkUpload) {
        List<String[]> rows = readCsvFileFromPath(localFilePath, StandardCharsets.UTF_8);
        if (rows.isEmpty()) {
            setBulkUploadFailure(bulkUpload, "No data found in CSV file");
            return null;
        }
        
        String[] header = rows.get(0);
        String[] cleanedHeader = cleanCsvHeader(header);
        
        if (!validateCsvHeader(cleanedHeader, bulkUpload)) {
            return null;
        }
        
        rows.remove(0);
        List<String[]> nonBlankRows = filterBlankRows(rows);
        bulkUpload.setTotalRecordCount(nonBlankRows.size());
        
        if (nonBlankRows.isEmpty()) {
            setBulkUploadFailure(bulkUpload, "No valid data rows found in CSV file");
            return null;
        }
        
        log.info("Starting validation for {} non-blank rows (original: {} rows)", nonBlankRows.size(), rows.size());
        return new CsvProcessingResult(header, nonBlankRows);
    }
    
    /**
     * Cleans CSV header by removing non-printable characters and BOM
     */
    private String[] cleanCsvHeader(String[] header) {
        return Arrays.stream(header)
                .map(h -> h != null ? h.replace("\uFEFF", "").replaceAll("[^\\x20-\\x7E]", "").trim() : "")
                .toArray(String[]::new);
    }
    
    /**
     * Validates CSV header against expected headers
     */
    private boolean validateCsvHeader(String[] cleanedHeader, BulkUpload bulkUpload) {
        if (cleanedHeader.length != EXPECTED_HEADERS.length) {
            String errorMessage = String.format(
                    "Invalid CSV header: column count mismatch. Expected %d columns but found %d columns. " +
                    "Expected columns: %s. Found columns: %s",
                    EXPECTED_HEADERS.length, cleanedHeader.length,
                    String.join(", ", EXPECTED_HEADERS),
                    String.join(", ", cleanedHeader)
            );
            setBulkUploadFailure(bulkUpload, errorMessage);
            log.error("CSV header validation failed in background job - column count mismatch. Expected: {}, Found: {}", 
                    EXPECTED_HEADERS.length, cleanedHeader.length);
            log.error("Expected headers: {}", Arrays.toString(EXPECTED_HEADERS));
            log.error("Found headers: {}", Arrays.toString(cleanedHeader));
            return false;
        }
        
        for (int i = 0; i < EXPECTED_HEADERS.length; i++) {
            if (!EXPECTED_HEADERS[i].equalsIgnoreCase(cleanedHeader[i])) {
                String errorMessage = String.format(
                        "Invalid CSV header: expected '%s' at column %d but found '%s'. " +
                        "All expected columns: %s",
                        EXPECTED_HEADERS[i], (i + 1), cleanedHeader[i],
                        String.join(", ", EXPECTED_HEADERS)
                );
                setBulkUploadFailure(bulkUpload, errorMessage);
                log.error("CSV header validation failed in background job - expected '{}' at column {} but found '{}'", 
                        EXPECTED_HEADERS[i], i+1, cleanedHeader[i]);
                return false;
            }
        }
        return true;
    }
    
    /**
     * Filters out blank rows from CSV data
     */
    private List<String[]> filterBlankRows(List<String[]> rows) {
        return rows.stream()
                .filter(row -> !Arrays.stream(row).allMatch(s -> s == null || s.trim().isEmpty()))
                .collect(Collectors.toList());
    }
    
    /**
     * Processes users: validates, creates, saves, and sends emails
     */
    private void processUsers(List<String[]> nonBlankRows, String language, List<FailedRecord> errorRecords, 
            String userId, Map<String, byte[]> imageMap, Map<String, String> imageMapping, String[] header) {
        Set<Integer> duplicateRowIndices = checkDuplicateEmailsInBatch(nonBlankRows, language, errorRecords);
        Map<String, String> userShiftMap = extractShiftData(nonBlankRows);
        
        Map<String, UserPasswordPair> userCodeToPairMap = validateAndCreateUsers(nonBlankRows, language, errorRecords, 
                userId, imageMap, imageMapping, duplicateRowIndices);
        List<User> users = userCodeToPairMap.values().stream()
                .map(UserPasswordPair::getUser)
                .collect(Collectors.toList());
        log.info("Validation completed. Valid users: {}, Errors: {}", users.size(), errorRecords.size());
        
        List<User> savedUsers = batchInsertUsers(users, errorRecords);
        sendRegistrationEmailsForBulkUpload(savedUsers, userCodeToPairMap, language);
        processShiftMappings(savedUsers, userShiftMap);
    }
    
    /**
     * Extracts shift data from CSV rows
     */
    private Map<String, String> extractShiftData(List<String[]> nonBlankRows) {
        Map<String, String> userShiftMap = new HashMap<>();
        for (String[] row : nonBlankRows) {
            BulkUserUploadRequest userRequest = createUserPostRequest(row);
            if (userRequest.getShift() != null && !userRequest.getShift().trim().isEmpty()) {
                userShiftMap.put(userRequest.getUserCode(), userRequest.getShift());
            }
        }
        return userShiftMap;
    }
    
    /**
     * Updates bulk upload status and handles error file upload
     */
    private void updateBulkUploadStatus(BulkUpload bulkUpload, List<FailedRecord> errorRecords, int totalRows, 
            String userId, String[] header) {
        bulkUpload.setFailureRecordCount(errorRecords.size());
        bulkUpload.setSuccessRecordCount(totalRows - errorRecords.size());
        
        String originalFileNameWithTimestamp = extractFileName(bulkUpload.getFilePath());
        String originalFileName = originalFileNameWithTimestamp.replaceAll("_(\\d{8}_\\d{6})(?=\\.)", "");
        
        log.info("Final results - Total: {}, Success: {}, Failure: {}", 
                bulkUpload.getTotalRecordCount(), bulkUpload.getSuccessRecordCount(), bulkUpload.getFailureRecordCount());
        
        if (!errorRecords.isEmpty()) {
            handleErrorFileUpload(bulkUpload, errorRecords, header, originalFileName, userId);
        } else {
            bulkUpload.setErrorFilePath("");
            bulkUpload.setStatus(BulkUploadStatus.SUCCESS);
            bulkUpload.setReason("Bulk Upload completed successfully.");
        }
        
        bulkUploadRepository.save(bulkUpload);
    }
    
    /**
     * Handles error file generation and upload to S3
     */
    private void handleErrorFileUpload(BulkUpload bulkUpload, List<FailedRecord> errorRecords, String[] header, 
            String originalFileName, String userId) {
        MultipartFile errorFile = writeFailedRecordsToCsv(errorRecords, header, originalFileName);
        
        if (errorFile != null) {
            try {
                String errorFileName = generateFileName(errorFile, "error");
                String s3Key = BULK_UPLOAD_DIR + "/employee/error-reports/" + userId + "/" + errorFileName;
                
                log.info("Uploading error file to S3. Key: {}, Size: {} bytes", s3Key, errorFile.getSize());
                String downloadErrorPath = awsService.uploadFile(errorFile.getInputStream(), s3Key, errorFile.getSize());
                
                bulkUpload.setErrorFilePath(downloadErrorPath);
                bulkUpload.setStatus(BulkUploadStatus.FAILURE);
                bulkUpload.setReason("Bulk Upload completed, Success count " + bulkUpload.getSuccessRecordCount() + 
                        " and failure count " + bulkUpload.getFailureRecordCount() + " out of " + bulkUpload.getTotalRecordCount() + " total records");
            } catch (Exception e) {
                log.error("Failed to upload error file to S3", e);
                bulkUpload.setErrorFilePath("");
            }
        } else {
            log.error("Failed to generate error file - writeFailedRecordsToCsv returned null");
            bulkUpload.setErrorFilePath("");
        }
    }
    
    /**
     * Creates audit trail for bulk upload operation
     */
    private void createAuditTrailForBulkUpload(BulkUpload bulkUpload, String userId, UUID bulkUploadId) {
        try {
            User manager = userRepository.findById(UUID.fromString(userId)).orElse(null);
            if (manager == null) {
                return;
            }
            
            Restaurant restaurant = manager.getRestaurantId() != null ? 
                    restaurantRepository.findById(manager.getRestaurantId()).orElse(null) : null;
            
            String notes = String.format("Bulk upload completed. Total: %d, Success: %d, Failed: %d", 
                    bulkUpload.getTotalRecordCount(), 
                    bulkUpload.getSuccessRecordCount(), 
                    bulkUpload.getFailureRecordCount());
            
            createAuditTrail(
                    manager,
                    ActionType.EMPLOYEE_BULK_UPLOAD,
                    restaurant,
                    RequestStatus.NA,
                    null, // ipAddress
                    null, // userAgent
                    bulkUploadId,
                    "BULK_UPLOAD",
                    notes,
                    null, // requestedBy
                    null, // requestedAt
                    null, // reviewedBy
                    null  // reviewedAt
            );
        } catch (Exception e) {
            log.error("Failed to create audit trail for bulk upload: {}", e.getMessage());
        }
    }
    
    /**
     * Cleans up image data from memory
     */
    private void cleanupImageData(Map<String, byte[]> imageMap) {
        if (imageMap != null) {
            imageMap.clear();
            log.debug("Cleared image data from memory after processing");
        }
    }

    /**
     * Helper method to delete temporary local file after processing.
     * This ensures we don't accumulate unnecessary files on the server.
     */
    private void deleteLocalFile(String filePath) {
        if (filePath == null || filePath.trim().isEmpty()) {
            return;
        }
        
        try {
            Path path = Paths.get(filePath);
            Files.delete(path);
            log.info("Successfully deleted temporary file: {}", filePath);
        } catch (NoSuchFileException e) {
            log.debug("Temporary file does not exist (may have been already deleted): {}", filePath);
        } catch (Exception e) {
            log.error("Error deleting temporary file: {} - {}", filePath, e.getMessage(), e);
            // Don't throw exception - file cleanup failure shouldn't break the flow
        }
    }

    /**
     * Helper method to set bulk upload status to failure and save.
     * Reduces code duplication across multiple validation failure points.
     */
    private void setBulkUploadFailure(BulkUpload bulkUpload, String reason) {
        bulkUpload.setStatus(BulkUploadStatus.FAILURE);
        bulkUpload.setReason(reason);
        bulkUpload.setUpdatedAt(OffsetDateTime.now(ZoneOffset.UTC));
        bulkUploadRepository.save(bulkUpload);
    }

    private static final String[] EXPECTED_HEADERS = {
        "user_code*", "first_name*", "last_name*", "email*", "mobile_number*", 
        "role*", "employment_type*", "restaurant_code", "restaurant_group_code", 
        "language_code*", "status*", "shift*", "image_name"
    };


    /**
     * Streams a CSV template for bulk user uploads to the HTTP response, including
     * a header row and a single example data row to guide clients.
     *
     * @param response the servlet response used to write the CSV content
     * @return an empty {@link ResponseEntity} indicating success
     * @throws IOException if writing to the response fails
     */
    @Override
    public ResponseEntity<Void> downloadTemplate(HttpServletResponse response) throws IOException {
        Locale userLocale = LocaleContextHolder.getLocale();
        
        String filename = "user_bulk_upload_template.csv";
        
        response.setContentType("text/csv");
        response.setHeader("Content-Disposition", "attachment; filename=\"" + filename + "\"");
        
        try (CSVPrinter csvPrinter = new CSVPrinter(response.getWriter(), 
                CSVFormat.DEFAULT.withHeader(EXPECTED_HEADERS))) {
            
            csvPrinter.printRecord("EMP1999", "Alice", "Johnson", "alice.johnson@example.com", 
                "81908234567", "WAITER", "FULL_TIME", "REST003", "GROUP001", "en", "ACTIVE", "Morning Shift", "alice_profile.jpg");
            
            csvPrinter.flush();
        }
        
        return ResponseEntity.ok().build();
    }

    /**
     * Reads all records from an uploaded CSV file using the given charset, returning
     * the raw rows as a list of string arrays. Uses {@code getBytes()} to avoid
     * issues with consumed input streams.
     *
     * @param file    the multipart CSV file to read
     * @param charset the character set to use when decoding the file
     * @return list of CSV records as string arrays; empty list if an error occurs
     */
    @Override
    public List<String[]> readCsvFile(MultipartFile file, Charset charset) {
        try {
            // Use getBytes() instead of getInputStream() to avoid stream consumption issues
            // This is especially important on servers where the stream might be consumed by middleware
            byte[] fileBytes = file.getBytes();
            try (CSVReader reader = new CSVReader(new InputStreamReader(
                    new java.io.ByteArrayInputStream(fileBytes), charset))) {
                return reader.readAll();
            }
        } catch (IOException | CsvException e) {
            log.error("Failed to read CSV file: {}", e.getMessage(), e);
            return Collections.emptyList();
        } catch (Exception e) {
            log.error("Unexpected error reading CSV file: {}", e.getMessage(), e);
            return Collections.emptyList();
        }
    }

    private List<String[]> readCsvFileFromPath(String filePath, Charset charset) {
        try (CSVReader reader = new CSVReader(new FileReader(filePath, charset))) {
            return reader.readAll();
        } catch (IOException | CsvException e) {
            log.error("Failed to read CSV file from path: {}", filePath, e);
            return Collections.emptyList();
        }
    }



    /**
     * Checks for duplicate emails within the CSV batch.
     * Marks only subsequent occurrences as errors (first occurrence will be processed normally).
     * Returns a set of row indices that should be skipped (duplicate occurrences).
     */
    private Set<Integer> checkDuplicateEmailsInBatch(List<String[]> rows, String language, List<FailedRecord> errorRecords) {
        Locale userLocale = determineLocaleFromLanguage(language);
        
        // Map to track email -> list of row indices where it appears
        Map<String, List<Integer>> emailToRowIndices = new HashMap<>();
        Set<Integer> duplicateRowIndices = new HashSet<>();
        
        // First pass: collect all emails and their row indices
        for (int i = 0; i < rows.size(); i++) {
            String[] row = rows.get(i);
            if (row.length > 3) {
                String email = cleanValue(row[3]); // email is at index 3
                if (email != null && !email.trim().isEmpty()) {
                    String emailLower = email.toLowerCase().trim();
                    emailToRowIndices.computeIfAbsent(emailLower, k -> new ArrayList<>()).add(i);
                }
            }
        }
        
        // Identify duplicates and add errors only for subsequent occurrences (not the first one)
        for (Map.Entry<String, List<Integer>> entry : emailToRowIndices.entrySet()) {
            String emailLower = entry.getKey();
            List<Integer> rowIndices = entry.getValue();
            
            if (rowIndices.size() > 1) {
                // Get the original email from the first occurrence for the error message
                String originalEmail = rows.get(rowIndices.get(0)).length > 3 ? 
                    cleanValue(rows.get(rowIndices.get(0))[3]) : emailLower;
                
                // Add error only for duplicate occurrences (skip the first one)
                // Process rowIndices starting from index 1 (second occurrence onwards)
                for (int i = 1; i < rowIndices.size(); i++) {
                    int rowIndex = rowIndices.get(i);
                    duplicateRowIndices.add(rowIndex);
                    
                    String[] row = rows.get(rowIndex);
                    BulkUserUploadRequest userRequest = createUserPostRequest(row);
                    String errorMessage = messageUtil.getMessage("bulk.upload.error.duplicate.email", userLocale, originalEmail);
                    errorRecords.add(new FailedRecord(userRequest, errorMessage));
                }
            }
        }
        
        if (!duplicateRowIndices.isEmpty()) {
            log.warn("Found {} duplicate email occurrence(s) within CSV batch (first occurrences will be processed)", duplicateRowIndices.size());
        }
        
        return duplicateRowIndices;
    }

    /**
     * Validates and creates user entities from CSV row data, skipping duplicate email occurrences.
     * Processes each row by creating a user request, validating it, and converting to a User entity
     * with generated password. Returns a map of userCode to UserPasswordPair for successful creations.
     *
     * @param rows                list of CSV row data arrays
     * @param language            language code for validation messages
     * @param errorRecords        list to collect failed record information
     * @param currentUserId       ID of the user performing the bulk upload
     * @param imageMap            map of renamed image filenames to image data bytes
     * @param imageMapping        map of original to renamed image filenames
     * @param duplicateRowIndices set of row indices to skip (duplicate email occurrences)
     * @return map of userCode to UserPasswordPair for successfully created users
     */
    @Transactional
    private Map<String, UserPasswordPair> validateAndCreateUsers(List<String[]> rows, String language, 
                                             List<FailedRecord> errorRecords, String currentUserId,
                                             Map<String, byte[]> imageMap, Map<String, String> imageMapping,
                                             Set<Integer> duplicateRowIndices) {
        Map<String, UserPasswordPair> userCodeToPairMap = new HashMap<>();
        for (int i = 0; i < rows.size(); i++) {
            // Skip validation for rows that are duplicate occurrences (already added to errorRecords)
            if (duplicateRowIndices.contains(i)) {
                log.debug("Skipping validation for row {} - duplicate email occurrence", i);
                continue;
            }
            
            String[] row = rows.get(i);
            BulkUserUploadRequest userRequest = createUserPostRequest(row);
            
            // Process first occurrence of email (or unique emails) - will go through normal validation
            UserPasswordPair pair = saveUser(userRequest, language, errorRecords, currentUserId, imageMap, imageMapping);
            if (pair != null && pair.getUser() != null && pair.getUser().getUserCode() != null) {
                // Use userCode as key for reliable lookup after database save
                userCodeToPairMap.put(pair.getUser().getUserCode(), pair);
            }
        }
        return userCodeToPairMap;
    }
    
    /**
     * Helper class to hold User and password together
     */
    private static class UserPasswordPair {
        private final User user;
        private final String password;
        
        public UserPasswordPair(User user, String password) {
            this.user = user;
            this.password = password;
        }
        
        public User getUser() {
            return user;
        }
        
        public String getPassword() {
            return password;
        }
    }


    /**
     * Creates a {@link BulkUserUploadRequest} object from CSV row data by mapping
     * each column to the corresponding request field, with default language code handling.
     *
     * @param rowData array of string values from a CSV row, expected to match EXPECTED_HEADERS order
     * @return {@link BulkUserUploadRequest} populated with cleaned and validated values
     */
    private BulkUserUploadRequest createUserPostRequest(String[] rowData) {
        log.info("Processing record: {}", Arrays.toString(rowData));
        BulkUserUploadRequest userPostRequest = new BulkUserUploadRequest();
        
        userPostRequest.setUserCode(cleanValue(rowData[0]));
        userPostRequest.setFirstName(cleanValue(rowData[1]));
        userPostRequest.setLastName(cleanValue(rowData[2]));
        userPostRequest.setEmail(cleanValue(rowData[3]));
        userPostRequest.setMobileNumber(cleanValue(rowData[4]));
        userPostRequest.setRole(cleanValue(rowData[5]));
        userPostRequest.setEmploymentType(cleanValue(rowData[6]));
        userPostRequest.setRestaurantCode(cleanValue(rowData[7]));
        userPostRequest.setRestaurantGroupCode(cleanValue(rowData[8]));
        
        String languageCode = cleanValue(rowData[9]);
        if (languageCode == null || languageCode.isEmpty()) {
            languageCode = "en";
            log.info("Language code was null/empty, defaulting to 'en' for user: {}", rowData[0]);
        }
        userPostRequest.setLanguageCode(languageCode);
        
        userPostRequest.setStatus(cleanValue(rowData[10]));
        userPostRequest.setShift(cleanValue(rowData[11]));
        userPostRequest.setImageName(rowData.length > 12 ? cleanValue(rowData[12]) : null);
    
        
        log.info("Created request for user: {} with restaurant: {}, group: {}, shift: {}, language: {}, role: {}, image: {}", 
                userPostRequest.getUserCode(), userPostRequest.getRestaurantCode(), 
                userPostRequest.getRestaurantGroupCode(), userPostRequest.getShift(),
                userPostRequest.getLanguageCode(), userPostRequest.getRole(), userPostRequest.getImageName());
        
        return userPostRequest;
    }

    private String cleanValue(String value) {
        if (value == null) return null;
        return value.trim();
    }

    /**
     * Validates a user request and converts it to a User entity with generated password.
     * Handles validation errors by adding them to the error records list instead of throwing.
     *
     * @param userPostRequest the user data request to validate and save
     * @param language        language code for validation messages
     * @param errorRecords    list to add validation errors if any occur
     * @param currentUserId   ID of the user performing the bulk upload
     * @param imageMap        map of renamed image filenames to image data bytes
     * @param imageMapping    map of original to renamed image filenames
     * @return {@link UserPasswordPair} containing the User entity and generated password if successful, null otherwise
     */
    @Transactional
    private UserPasswordPair saveUser(BulkUserUploadRequest userPostRequest, String language, 
                         List<FailedRecord> errorRecords, String currentUserId,
                         Map<String, byte[]> imageMap, Map<String, String> imageMapping) {
        try {
            if (imageMap != null && imageMapping != null) {
                bulkUploadValidator.validateUserRequestWithImages(userPostRequest, language, imageMap, imageMapping);
            } else {
                bulkUploadValidator.validateUserRequest(userPostRequest, language);
            }
            
            return convertToUser(userPostRequest, UUID.fromString(currentUserId), imageMap, imageMapping);
        } catch (Exception e) {
            errorRecords.add(new FailedRecord(userPostRequest, e.getMessage()));
            return null;
        }
    }

    /**
     * Inserts users into the database in batches to optimize performance.
     * Processes users in configurable batch sizes, handling failures gracefully
     * by adding failed users to the error records list.
     *
     * @param users       list of User entities to insert
     * @param errorRecords list to collect failed user records with error messages
     * @return list of successfully saved User entities
     */
    @Transactional
    private List<User> batchInsertUsers(List<User> users, List<FailedRecord> errorRecords) {
        List<User> successUsers = new ArrayList<>();
        log.info("Starting batch insert for {} users", users.size());
        
        for (int i = 0; i < users.size(); i += BATCH_SIZE) {
            int end = Math.min(i + BATCH_SIZE, users.size());
            List<User> batchedUsers = users.subList(i, end);
            int batchIndex = i / BATCH_SIZE;
            log.info("Processing batch {} ({} users)", batchIndex, batchedUsers.size());
                
            try {
                batchedUsers.parallelStream().forEach(user -> 
                    log.info("Before save - User: {}, languageCode: '{}', firstName: '{}', lastName: '{}'", 
                            user.getUserCode(), user.getLanguageCode(), user.getFirstName(), user.getLastName())
                );
                
                List<User> savedUsers = userRepository.saveAll(batchedUsers);
                
                synchronized (successUsers) {
                    successUsers.addAll(savedUsers);
                }
                log.info("Successfully saved {} users in batch", savedUsers.size());
            } catch (Exception e) {
                log.error("Batch insert failed for batch {}", batchIndex, e);
                batchedUsers.parallelStream().forEach(user -> {
                    log.error("Failed to save user: {}", user.getUserCode());
                    BulkUserUploadRequest userRequest = new BulkUserUploadRequest();
                    userRequest.setUserCode(user.getUserCode());
                    userRequest.setFirstName(user.getFirstName());
                    userRequest.setLastName(user.getLastName());
                    userRequest.setEmail(user.getEmail());
                    userRequest.setMobileNumber(user.getContactNumber());
                    userRequest.setLanguageCode(user.getLanguageCode());
                    userRequest.setRestaurantCode(null);
                    userRequest.setRestaurantGroupCode(null);
                    synchronized (errorRecords) {
                        errorRecords.add(new FailedRecord(userRequest, e.getMessage()));
                    }
                });
            }
        }
        
        log.info("Batch insert completed. Success: {}, Errors: {}", successUsers.size(), errorRecords.size());
        return successUsers;
    }

    /**
     * Sends registration emails to users created via bulk upload
     * Uses the same logic as registerUser method
     */
    private void sendRegistrationEmailsForBulkUpload(List<User> savedUsers, Map<String, UserPasswordPair> userCodeToPairMap, String language) {
        if (savedUsers == null || savedUsers.isEmpty()) {
            log.info("[BULK UPLOAD EMAIL] No users to send registration emails for");
            return;
        }

        log.info("[BULK UPLOAD EMAIL] Starting to send registration emails for {} users via bulk upload", savedUsers.size());
        int successCount = 0;
        int failureCount = 0;

        for (User user : savedUsers) {
            // Use userCode to lookup password since User objects may differ after database save
            String userCode = user.getUserCode();
            UserPasswordPair pair = userCode != null ? userCodeToPairMap.get(userCode) : null;
            String generatedPassword = pair != null ? pair.getPassword() : null;
            
            // Validate user data before processing - use single continue for all validation failures
            if (userCode == null) {
                log.warn("[BULK UPLOAD EMAIL] User has no userCode, skipping email for user ID: {}", user.getId());
                failureCount++;
            } else if (pair == null) {
                log.warn("[BULK UPLOAD EMAIL] No password found for user: {}, skipping email", userCode);
                failureCount++;
            } else if (generatedPassword == null) {
                log.warn("[BULK UPLOAD EMAIL] Password is null for user: {}, skipping email", userCode);
                failureCount++;
            } else {
                // All validations passed, proceed with email sending
                try {
                    // Determine locale from user's language code or provided language
                    Locale userLocale = determineLocale(user, language);
                    
                    log.info("[BULK UPLOAD EMAIL] Attempting to send registration email for user: {} (Name: {} {}, Role: {})", 
                            userCode, user.getFirstName(), user.getLastName() != null ? user.getLastName() : "", 
                            getRoleName(user));
                    
                    sendRegistrationEmail(user, generatedPassword, userLocale);
                    successCount++;
                    log.info("[BULK UPLOAD EMAIL] ✓ Registration email sent successfully for user: {}", userCode);
                } catch (Exception e) {
                    failureCount++;
                    log.error("[BULK UPLOAD EMAIL] ✗ Failed to send registration email for user {}: {}", userCode, e.getMessage(), e);
                    // Continue processing other users even if one fails
                }
            }
        }

        log.info("[BULK UPLOAD EMAIL] Registration email sending completed. Total: {}, Success: {}, Failed: {}", 
                savedUsers.size(), successCount, failureCount);
    }
    
    /**
     * Helper method to get role name for logging
     */
    private String getRoleName(User user) {
        if (user.getRoleId() == null) {
            return UNKNOWN_ROLE;
        }
        try {
            Role role = roleRepository.findById(user.getRoleId()).orElse(null);
            return role != null ? role.getName() : UNKNOWN_ROLE;
        } catch (Exception e) {
            log.debug("Failed to get role name for user {}: {}", user.getId(), e.getMessage());
            return UNKNOWN_ROLE;
        }
    }

    /**
     * Determines locale from a language string.
     * Returns Locale based on language code, defaults to 'en' if not recognized.
     */
    private Locale determineLocaleFromLanguage(String language) {
        if (language != null && !language.trim().isEmpty()) {
            String lang = language.trim().toLowerCase();
            if ("ja".equals(lang)) {
                return new Locale("ja");
            } else if ("th".equals(lang)) {
                return new Locale("th");
            } else {
                return new Locale("en");
            }
        }
        return new Locale("en");
    }

    /**
     * Determines locale from user's language code or provided language parameter
     */
    private Locale determineLocale(User user, String language) {
        String languageCode = user.getLanguageCode();
        if (languageCode != null && !languageCode.trim().isEmpty()) {
            return new Locale(languageCode);
        }
        if (language != null && !language.trim().isEmpty()) {
            return new Locale(language);
        }
        return LocaleContextHolder.getLocale();
    }

    /**
     * Helper class to hold default email fallback result
     */
    private static class DefaultEmailResult {
        boolean isDefaultEmailUsed;
        String recipientType;
        
        DefaultEmailResult(boolean isDefaultEmailUsed, String recipientType) {
            this.isDefaultEmailUsed = isDefaultEmailUsed;
            this.recipientType = recipientType;
        }
    }

    /**
     * Helper method to add default email as fallback when no recipients are found.
     * Returns result containing whether default email was used and the recipient type.
     */
    private DefaultEmailResult addDefaultEmailAsFallback(Set<String> recipientEmails, User user, String fallbackReason) {
        String defaultEmail = emailProperties.getEmail();
        if (defaultEmail != null && !defaultEmail.trim().isEmpty()) {
            recipientEmails.add(defaultEmail);
            String recipientType = "DEFAULT EMAIL (" + fallbackReason + ")";
            log.warn("[BULK UPLOAD EMAIL] {}. Using default email: {} for user: {}", 
                    fallbackReason, defaultEmail, user.getUserCode());
            return new DefaultEmailResult(true, recipientType);
        } else {
            log.error("[BULK UPLOAD EMAIL] {} AND default email is not configured. Cannot send email for user: {}", 
                    fallbackReason, user.getUserCode());
            return new DefaultEmailResult(false, null);
        }
    }

    /**
     * Sends registration email to a user (same logic as registerUser method)
     */
    private void sendRegistrationEmail(User user, String generatedPassword, Locale userLocale) {
        String subject = messageUtil.getMessage("user.registration.email.subject", userLocale);
        
        UserRoleInfo roleInfo = determineUserRoleInfo(user);
        RecipientInfo recipientInfo = determineRecipients(user, roleInfo);
        String htmlBody = buildEmailBody(user, generatedPassword, userLocale, roleInfo, recipientInfo);
        sendEmailsToRecipients(user, subject, htmlBody, recipientInfo, roleInfo.getUserRoleName());
    }
    
    /**
     * Determines user role information and app type
     */
    private UserRoleInfo determineUserRoleInfo(User user) {
        String userRoleName = null;
        boolean isWebAppUser = false;
        String loginUrl = null;
        
        if (user.getRoleId() != null) {
            Role userRole = roleRepository.findById(user.getRoleId()).orElse(null);
            if (userRole != null) {
                userRoleName = userRole.getName();
                isWebAppUser = "HQ_ADMIN".equals(userRoleName) || "MANAGER".equals(userRoleName);
                
                if (isWebAppUser) {
                    String baseUrl = frontendUrlProperties.getUrlForRole(userRoleName);
                    loginUrl = baseUrl != null && !baseUrl.isEmpty() ? baseUrl : null;
                }
            }
        }
        
        boolean isMobileAppUser = "WAITER".equals(userRoleName) || 
                                 "CASHIER".equals(userRoleName) || 
                                 "KDS".equals(userRoleName);
        
        return new UserRoleInfo(userRoleName, isWebAppUser, isMobileAppUser, loginUrl);
    }
    
    /**
     * Determines recipient emails based on user role
     */
    private RecipientInfo determineRecipients(User user, UserRoleInfo roleInfo) {
        RecipientInfo recipientInfo;
        
        if (roleInfo.isMobileAppUser()) {
            // Same priority as UserServiceImpl:
            // 1) user's own email (if present), otherwise
            // 2) manager(s), otherwise
            // 3) default email
            String userEmail = user.getEmail();
            if (userEmail != null && !userEmail.trim().isEmpty()) {
                Set<String> recipientEmails = new LinkedHashSet<>();
                recipientEmails.add(userEmail);
                recipientInfo = new RecipientInfo(
                        recipientEmails,
                        false,
                        "USER'S OWN EMAIL",
                        true
                );
            } else {
                recipientInfo = findManagerRecipients(user, roleInfo.getUserRoleName());
            }
        } else {
            recipientInfo = findWebAppUserRecipients(user, roleInfo.getUserRoleName());
        }
        
        logRecipientSummary(user, recipientInfo.getRecipientEmails(), recipientInfo.getRecipientType());
        return recipientInfo;
    }
    
    /**
     * Finds manager recipients for mobile app users
     */
    private RecipientInfo findManagerRecipients(User user, String userRoleName) {
        Set<String> recipientEmails = new LinkedHashSet<>();
        String recipientType = "MANAGER(S)";
        boolean isDefaultEmailUsed = false;
        
        log.info("[BULK UPLOAD EMAIL] User {} ({}) is a mobile app user. Looking for manager(s) for restaurant: {}", 
                user.getUserCode(), userRoleName, user.getRestaurantId());
        
        if (user.getRestaurantId() != null) {
            Optional<Role> managerRoleOpt = roleRepository.findByName("MANAGER");
            if (managerRoleOpt.isPresent()) {
                UUID managerRoleId = managerRoleOpt.get().getId();
                List<User> managers = userRepository.findAllByRestaurantIdAndRoleIdAndIsDeletedFalse(
                        user.getRestaurantId(), managerRoleId);
                
                log.info("[BULK UPLOAD EMAIL] Found {} manager(s) for restaurant {} (user: {})", 
                        managers.size(), user.getRestaurantId(), user.getUserCode());
                
                addManagerEmails(managers, recipientEmails);
            } else {
                log.warn("[BULK UPLOAD EMAIL] MANAGER role not found in database");
            }
        } else {
            log.warn("[BULK UPLOAD EMAIL] User {} has no restaurant ID assigned", user.getUserCode());
        }
        
        if (recipientEmails.isEmpty()) {
            DefaultEmailResult result = addDefaultEmailAsFallback(recipientEmails, user, 
                    "No managers found for restaurant " + user.getRestaurantId());
            if (result.isDefaultEmailUsed) {
                isDefaultEmailUsed = true;
                recipientType = result.recipientType;
            }
        }
        
        return new RecipientInfo(recipientEmails, isDefaultEmailUsed, recipientType, false);
    }
    
    /**
     * Adds manager emails to recipient set
     */
    private void addManagerEmails(List<User> managers, Set<String> recipientEmails) {
        int duplicateCount = 0;
        int managersWithEmail = 0;
        
        for (User manager : managers) {
            if (manager.getEmail() != null && !manager.getEmail().trim().isEmpty()) {
                managersWithEmail++;
                boolean wasNew = recipientEmails.add(manager.getEmail());
                if (!wasNew) {
                    duplicateCount++;
                    log.debug("[BULK UPLOAD EMAIL] Duplicate email detected for manager {}: {}", manager.getId(), manager.getEmail());
                } else {
                    log.info("[BULK UPLOAD EMAIL] Adding manager email: {} (Manager: {} {})", 
                            manager.getEmail(), manager.getFirstName(), 
                            manager.getLastName() != null ? manager.getLastName() : "");
                }
            } else {
                log.warn("[BULK UPLOAD EMAIL] Manager {} ({}) has no email address", 
                        manager.getUserCode(), manager.getFirstName());
            }
        }
        
        if (duplicateCount > 0) {
            log.info("[BULK UPLOAD EMAIL] Filtered {} duplicate email(s) from {} manager(s). Unique emails: {}", 
                    duplicateCount, managers.size(), recipientEmails.size());
        }
        
        if (managersWithEmail == 0) {
            log.warn("[BULK UPLOAD EMAIL] No managers with email addresses found for restaurant");
        }
    }
    
    /**
     * Finds recipients for web app users
     */
    private RecipientInfo findWebAppUserRecipients(User user, String userRoleName) {
        Set<String> recipientEmails = new LinkedHashSet<>();
        String recipientType = "USER'S OWN EMAIL";
        boolean isDefaultEmailUsed = false;
        
        log.info("[BULK UPLOAD EMAIL] User {} ({}) is a web app user. Sending to their own email", 
                user.getUserCode(), userRoleName);
        
        String userEmail = user.getEmail();
        if (userEmail == null || userEmail.trim().isEmpty()) {
            DefaultEmailResult result = addDefaultEmailAsFallback(recipientEmails, user, "User has no email");
            if (result.isDefaultEmailUsed) {
                isDefaultEmailUsed = true;
                recipientType = result.recipientType;
            }
        } else {
            recipientEmails.add(userEmail);
            log.info("[BULK UPLOAD EMAIL] User {} email address: {}", user.getUserCode(), userEmail);
        }
        
        return new RecipientInfo(recipientEmails, isDefaultEmailUsed, recipientType, true);
    }
    
    /**
     * Logs recipient summary
     */
    private void logRecipientSummary(User user, Set<String> recipientEmails, String recipientType) {
        if (!recipientEmails.isEmpty()) {
            log.info("[BULK UPLOAD EMAIL] Email will be sent to {} recipient(s) for user {} ({}): {}", 
                    recipientEmails.size(), user.getUserCode(), recipientType, String.join(", ", recipientEmails));
        } else {
            log.error("[BULK UPLOAD EMAIL] No recipients found for user {}. Email will NOT be sent.", user.getUserCode());
        }
    }
    
    /**
     * Builds email body based on recipient type
     */
    private String buildEmailBody(User user, String generatedPassword, Locale userLocale, 
                                  UserRoleInfo roleInfo, RecipientInfo recipientInfo) {
        // Reuse the same table-based HTML formats as UserServiceImpl.
        return registrationEmailHtmlFormatter.buildRegistrationEmailHtml(
                user,
                generatedPassword,
                userLocale,
                roleInfo.getUserRoleName(),
                roleInfo.isMobileAppUser(),
                recipientInfo.isSendingToUserEmail(),
                roleInfo.isWebAppUser(),
                roleInfo.getLoginUrl(),
                recipientInfo.isDefaultEmailUsed()
        );
    }
    
    /**
     * Sends emails to all recipients
     */
    private void sendEmailsToRecipients(User user, String subject, String htmlBody, 
                                       RecipientInfo recipientInfo, String userRoleName) {
        Set<String> recipientEmails = recipientInfo.getRecipientEmails();
        int totalRecipients = recipientEmails.size();
        int successfulSends = 0;
        int failedSends = 0;
        boolean emailSent = false;
        
        log.info("[BULK UPLOAD EMAIL] Starting to send email(s) for user: {} (Role: {}) to {} recipient(s)", 
                user.getUserCode(), userRoleName, totalRecipients);
        
        for (String recipientEmail : recipientEmails) {
            try {
                log.info("[BULK UPLOAD EMAIL] Sending email to: {} for user: {} (Role: {})", 
                        recipientEmail, user.getUserCode(), userRoleName);
                
                emailSender.sendEmail(recipientEmail, subject, htmlBody);
                
                successfulSends++;
                emailSent = true;
                log.info("[BULK UPLOAD EMAIL] ✓ Email sent successfully to: {} for user: {} (Role: {})", 
                        recipientEmail, user.getUserCode(), userRoleName);
            } catch (Exception e) {
                failedSends++;
                log.error("[BULK UPLOAD EMAIL] ✗ Failed to send email to: {} for user: {} (Role: {}). Error: {}", 
                        recipientEmail, user.getUserCode(), userRoleName, e.getMessage(), e);
            }
        }
        
        handleEmailSendingResult(user, userRoleName, recipientEmails, totalRecipients, successfulSends, failedSends, emailSent);
    }
    
    /**
     * Handles email sending result and throws exceptions if needed
     */
    private void handleEmailSendingResult(User user, String userRoleName, Set<String> recipientEmails, 
                                         int totalRecipients, int successfulSends, int failedSends, boolean emailSent) {
        if (emailSent) {
            log.info("[BULK UPLOAD EMAIL] Email sending summary for user: {} (Role: {}) - Total recipients: {}, Successful: {}, Failed: {}", 
                    user.getUserCode(), userRoleName, totalRecipients, successfulSends, failedSends);
        } else if (!recipientEmails.isEmpty()) {
            log.error("[BULK UPLOAD EMAIL] ✗ Failed to send email to ANY recipient for user: {} (Role: {}). Total recipients: {}, All failed", 
                    user.getUserCode(), userRoleName, totalRecipients);
            throw new EmailSendingException(String.format("Failed to send registration email to any recipient for user: %s (Role: %s). All %d recipient(s) failed.", 
                    user.getUserCode(), userRoleName, totalRecipients));
        } else {
            log.error("[BULK UPLOAD EMAIL] ✗ No recipients available for user: {} (Role: {}). Email was NOT sent", 
                    user.getUserCode(), userRoleName);
            throw new EmailSendingException(String.format("No recipients available for user: %s (Role: %s). Email was NOT sent.", 
                    user.getUserCode(), userRoleName));
        }
    }
    
    /**
     * Helper class to hold user role information
     */
    private static class UserRoleInfo {
        private final String userRoleName;
        private final boolean isWebAppUser;
        private final boolean isMobileAppUser;
        private final String loginUrl;
        
        UserRoleInfo(String userRoleName, boolean isWebAppUser, boolean isMobileAppUser, String loginUrl) {
            this.userRoleName = userRoleName;
            this.isWebAppUser = isWebAppUser;
            this.isMobileAppUser = isMobileAppUser;
            this.loginUrl = loginUrl;
        }
        
        String getUserRoleName() { return userRoleName; }
        boolean isWebAppUser() { return isWebAppUser; }
        boolean isMobileAppUser() { return isMobileAppUser; }
        String getLoginUrl() { return loginUrl; }
    }
    
    /**
     * Helper class to hold recipient information
     */
    private static class RecipientInfo {
        private final Set<String> recipientEmails;
        private final boolean isDefaultEmailUsed;
        private final String recipientType;
        private final boolean isSendingToUserEmail;
        
        RecipientInfo(Set<String> recipientEmails, boolean isDefaultEmailUsed, String recipientType, boolean isSendingToUserEmail) {
            this.recipientEmails = recipientEmails;
            this.isDefaultEmailUsed = isDefaultEmailUsed;
            this.recipientType = recipientType;
            this.isSendingToUserEmail = isSendingToUserEmail;
        }
        
        Set<String> getRecipientEmails() { return recipientEmails; }
        boolean isDefaultEmailUsed() { return isDefaultEmailUsed; }
        String getRecipientType() { return recipientType; }
        boolean isSendingToUserEmail() { return isSendingToUserEmail; }
    }

    /**
     * Generates a CSV file containing failed records with error messages appended as an additional column.
     * Creates a multipart file representation of the error CSV for upload to S3.
     *
     * @param failedRecords   list of failed records with user data and error messages
     * @param headers         original CSV header array
     * @param originalFilename original filename to use for the error file
     * @return {@link MultipartFile} containing the error CSV data, or null if generation fails
     */
    private MultipartFile writeFailedRecordsToCsv(List<FailedRecord> failedRecords, String[] headers,  String originalFilename) {
        log.info("Generating error CSV file for {} failed records", failedRecords.size());
        
        String[] header = Arrays.copyOf(headers, headers.length + 1);
        header[headers.length] = ERROR_COLUMN;
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        
        try (CSVPrinter csvPrinter = new CSVPrinter(new OutputStreamWriter(outputStream, StandardCharsets.UTF_8),
                CSVFormat.DEFAULT.withHeader(header))) {
            
            for (FailedRecord failedRecord : failedRecords) {
                BulkUserUploadRequest user = failedRecord.getUserRequest();
                csvPrinter.printRecord(user.getUserCode(), user.getFirstName(), user.getLastName(), 
                    user.getEmail(), user.getMobileNumber(), user.getRole(), user.getEmploymentType(),
                    user.getRestaurantCode(), user.getRestaurantGroupCode(), user.getLanguageCode(), 
                    user.getStatus(), user.getShift(), user.getImageName(), failedRecord.getErrorMessage());
            }
            csvPrinter.flush();

            String errorFileName = originalFilename != null ? originalFilename : "Error.csv";
            byte[] csvData = outputStream.toByteArray();
            
            log.info("Generated error CSV file: {} ({} bytes)", errorFileName, csvData.length);
            
            return new CustomMultipartFile(
                csvData,
                "csvFile",
                errorFileName,
                "text/csv"
            );
        } catch (IOException e) {
            log.error("Failed to generate error file", e);
            return null;
        }
    }

    private String generateFileName(MultipartFile file, String attachmentType) {
        String originalFilename = file.getOriginalFilename();
        String extension = originalFilename != null ?
            ("." + BulkUploadImageFilenameUtils.getFileExtension(originalFilename)) : ".csv";
        String baseName = originalFilename != null ?
            BulkUploadImageFilenameUtils.stripFileExtension(originalFilename) : ERROR_COLUMN;
        return baseName + "_" + attachmentType + "_" + System.currentTimeMillis() + extension;
    }



    /**
     * Converts a validated bulk user upload request into a User entity with generated password.
     * Validates role, restaurant, and restaurant group existence, generates a secure password,
     * and handles optional profile image upload to S3 with thumbnail generation.
     *
     * @param request      the validated user request data
     * @param creatorId    ID of the user creating this record (not set on the entity)
     * @param imageMap     map of renamed image filenames to image data bytes
     * @param imageMapping map of original to renamed image filenames
     * @return {@link UserPasswordPair} containing the User entity and plain-text generated password
     * @throws BadRequestException if role, restaurant, or restaurant group is not found, or image upload fails
     */
    @Transactional
    private UserPasswordPair convertToUser(BulkUserUploadRequest request, UUID creatorId,
                              Map<String, byte[]> imageMap, Map<String, String> imageMapping) {
        String languageCode = request.getLanguageCode();
        log.info("Setting language code for user {}: {}", request.getUserCode(), languageCode);
        
        Role role = roleRepository.findByName(request.getRole())
                .orElseThrow(() -> new BadRequestException("Role not found: " + request.getRole()));
        
        if (request.getRestaurantGroupCode() != null && !request.getRestaurantGroupCode().trim().isEmpty()) {
            boolean restaurantGroupExists = restaurantGroupRepository.existsByRestaurantGroupCodeAndIsDeletedFalse(request.getRestaurantGroupCode());
            if (!restaurantGroupExists) {
                throw new BadRequestException("Restaurant group not found: " + request.getRestaurantGroupCode());
            }
        }
        
        UUID restaurantId = null;
        if (request.getRestaurantCode() != null && !request.getRestaurantCode().trim().isEmpty()) {
            List<Restaurant> restaurants = restaurantRepository.findAll().stream()
                    .filter(r -> request.getRestaurantCode().equals(r.getRestaurantCode()) && !r.getIsDeleted())
                    .toList();
            
            if (restaurants.isEmpty()) {
                throw new BadRequestException("Restaurant not found: " + request.getRestaurantCode());
            }
            
            Restaurant restaurant = restaurants.get(0);
            restaurantId = restaurant.getId();
        }
        
        // Generate password for user
        String generatedPassword = PasswordGeneratorUtil.generatePassword(12);
        
        User user = new User();
        // Store the original userCode (trimmed) to preserve case
        // Uniqueness is already validated earlier in the bulk upload process
        String originalUserCode = request.getUserCode() != null ? request.getUserCode().trim() : null;
        user.setUserCode(originalUserCode); // Store original case
        user.setFirstName(request.getFirstName());
        user.setLastName(request.getLastName());
        user.setEmail(request.getEmail());
        user.setContactNumber(request.getMobileNumber());
        user.setLanguageCode(languageCode);
        user.setEmploymentType(EmploymentType.valueOf(request.getEmploymentType()));
        user.setStatus(EntityStatus.ACTIVE);
        user.setRoleId(role.getId());
        user.setRestaurantId(restaurantId);
        user.setCreatedAt(OffsetDateTime.now(ZoneOffset.UTC));
        user.setUpdatedAt(OffsetDateTime.now(ZoneOffset.UTC));
        user.setIsDeleted(false);
        user.setIsStatusLocked(false);
        user.setProfileUpdateRequestStatus(RequestStatus.NONE);
        user.setPassword(passwordEncoder.encode(generatedPassword));
        
        // Handle image upload if provided
        if (imageMap != null && imageMapping != null && request.getImageName() != null && !request.getImageName().trim().isEmpty()) {
            try {
                String imageName = request.getImageName().trim();
                String renamedImageFileName = imageMapping.get(imageName);
                byte[] imageData = imageMap.get(renamedImageFileName);
                
                // Upload image to S3
                String imageUrl = uploadImageToS3(imageData, renamedImageFileName);
                user.setPhotoUrl(imageUrl);

                // Upload thumbnail to S3
                String thumbUrl = uploadThumbnailToS3(imageData, renamedImageFileName);
                user.setPhotoThumbnailUrl(thumbUrl);
            } catch (Exception e) {
                log.error("Failed to upload profile image for user {}: {}", request.getUserCode(), e.getMessage(), e);
                throw new BadRequestException("Failed to upload profile image: " + e.getMessage());
            }
        } else {
            user.setPhotoUrl(null);
        }
        
        user.setCreatedBy(null);
        user.setUpdatedBy(null);
        
        log.info("Converted user: {} with language: {}", user.getUserCode(), user.getLanguageCode());
        
        return new UserPasswordPair(user, generatedPassword);
    }

    /**
     * Processes shift mappings for a list of users by creating UserShiftMapping entities
     * for each user that has an associated shift name in the provided map.
     *
     * @param users       list of User entities to process shift mappings for
     * @param userShiftMap map of userCode to shift name from CSV data
     */
    @Transactional
    private void processShiftMappings(List<User> users, Map<String, String> userShiftMap) {
        log.info("Processing shift mappings for {} users", users.size());
        
        for (User user : users) {
            String shiftName = userShiftMap.get(user.getUserCode());
            if (shiftName != null && !shiftName.trim().isEmpty()) {
                createUserShiftMapping(user, shiftName);
            }
        }
        
        log.info("Completed shift mapping processing");
    }

    /**
     * Creates a UserShiftMapping entity linking a user to a shift by finding the shift
     * using its translated name (preferring English translation, then any available translation).
     *
     * @param user      the User entity to associate with the shift
     * @param shiftName the shift name to look up (can be in any language)
     * @throws BadRequestException if the shift is not found by the provided name
     */
    @Transactional
    private void createUserShiftMapping(User user, String shiftName) {
        if (shiftName != null && !shiftName.trim().isEmpty()) {
            log.info("Creating shift mapping for user: {} with shift: {}", user.getUserCode(), shiftName);
            // Find shift by translation name (prefer 'en' language, then any language)
            List<Shift> shifts = shiftTranslationRepository.findShiftsByName(shiftName);
            if (shifts.isEmpty()) {
                log.error("Shift not found: {}", shiftName);
                throw new BadRequestException("Shift not found: " + shiftName);
            }
            Shift shift = shifts.get(0); // Get first match (preferentially 'en' due to ORDER BY)

            UserShiftMapping mapping = new UserShiftMapping();
            mapping.setUser(user);
            mapping.setShift(shift);
            userShiftMappingRepository.save(mapping);
            
            // Get shift name for logging
            List<ShiftTranslation> translations = shiftTranslationRepository.findAllByShiftId(shift.getId());
            String shiftNameForLog = translations.stream()
                    .filter(t -> "en".equalsIgnoreCase(t.getLanguageCode()))
                    .findFirst()
                    .map(ShiftTranslation::getName)
                    .orElse(translations.isEmpty() ? "" : translations.get(0).getName());
            log.info("Successfully created shift mapping for user: {} with shift: {}", user.getUserCode(), shiftNameForLog);
        } else {
            log.warn("Shift name is null or empty for user: {}", user.getUserCode());
        }
    }

    /**
     * Extracts images from ZIP file bytes and creates mapping from original to renamed filenames.
     * Stores the original-to-renamed filename mapping for later validation.
     * Images are uploaded to S3 path: user/profile-images/
     */
    private Map<String, byte[]> extractImagesFromZipBytes(byte[] zipFileBytes, String zipFileName, Locale locale, Map<String, String> imageMapping) throws IOException {
        Map<String, byte[]> imageMap = new HashMap<>();
        
        log.info("Starting ZIP extraction for file: {} (size: {} bytes)", zipFileName, zipFileBytes.length);
        
        // Validate ZIP file
        validateZipFileBytes(zipFileBytes, zipFileName, locale);
        
        try (ZipInputStream zipInputStream = new ZipInputStream(new ByteArrayInputStream(zipFileBytes))) {
            ZipEntry entry;
            int totalEntries = 0;
            int imageEntries = 0;
            
            while ((entry = zipInputStream.getNextEntry()) != null) {
                totalEntries++;
                if (!entry.isDirectory()) {
                    String originalFileName = entry.getName();
                    log.info("Processing ZIP entry: {}", originalFileName);
                    
                    // Validate image file extension
                    if (BulkUploadImageFilenameUtils.isValidImageFile(originalFileName)) {
                        imageEntries++;
                        String renamedFileName = BulkUploadImageFilenameUtils.generateBulkUploadImageFileName(originalFileName);
                        
                        log.info("Processing image: {} -> {}", originalFileName, renamedFileName);
                        
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
                        
                        log.info("Successfully processed image: {} ({} bytes)", renamedFileName, imageData.length);
                    } else {
                        log.warn("Skipping non-image file: {}", originalFileName);
                    }
                }
                zipInputStream.closeEntry();
            }
            
            log.info("ZIP extraction completed. Total entries: {}, Image entries: {}", totalEntries, imageEntries);
        }
        
        if (imageMap.isEmpty()) {
            log.warn("No valid images found in ZIP file. Continuing without image validation.");
            // Don't throw exception - just return empty map
            // The calling code will handle this gracefully
        }
        
        log.info("Successfully extracted and renamed {} images from ZIP file", imageMap.size());
        return imageMap;
    }
    
    /**
     * Validate ZIP file format and size
     */
    private void validateZipFileBytes(byte[] zipFileBytes, String zipFileName, Locale locale) {
        // Validate ZIP file format
        if (zipFileName == null || !zipFileName.toLowerCase().endsWith(".zip")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, 
                    messageUtil.getMessage("bulk.upload.invalid.zip.file", locale));
        }
        
        // Validate ZIP file magic bytes (PK\x03\x04)
        if (!isZipMagicBytes(zipFileBytes)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                messageUtil.getMessage("bulk.upload.invalid.zip.file", locale));
        }

        // Validate ZIP file size (30MB limit for ZIP files)
        if (zipFileBytes.length > MAX_ZIP_FILE_SIZE) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                messageUtil.getMessage("bulk.upload.file.size.exceeded", locale, "30MB"));
        }
    }

    /**
     * Check ZIP file magic bytes (PK\x03\x04)
     */
    private boolean isZipMagicBytes(byte[] fileBytes) {
        if (fileBytes == null || fileBytes.length < 4) {
            return false;
        }
        // ZIP files start with: 0x50 0x4B 0x03 0x04
        return (fileBytes[0] == 0x50 && fileBytes[1] == 0x4B &&
                fileBytes[2] == 0x03 && fileBytes[3] == 0x04);
    }
    
    /**
     * Upload image to S3 for user profile
     * S3 path: user/profile-images/
     */
    private String uploadImageToS3(byte[] imageData, String fileName) throws IOException {
        try (ByteArrayInputStream inputStream = new ByteArrayInputStream(imageData)) {
            // Use S3 key pattern for user profile images: user/profile-images/
            String s3Key = "profile-images/employee/" + fileName;
            
            // Upload to S3 using existing AWSService
            return awsService.uploadFile(inputStream, s3Key, imageData.length);
            
        } catch (Exception e) {
            log.error("Failed to upload user profile image to S3: {}", e.getMessage(), e);
            throw new IOException("Failed to upload image to S3: " + e.getMessage(), e);
        }
    }

    /**
     * Create thumbnail and upload with thumb_ prefix in same directory as original (profile-images/employee)
     */
    private String uploadThumbnailToS3(byte[] imageData, String originalFileName) throws IOException {
        try (java.io.ByteArrayInputStream ignored = new java.io.ByteArrayInputStream(imageData)) {
            String extension = BulkUploadImageFilenameUtils.getFileExtension(originalFileName).toLowerCase();
            byte[] thumbBytes = imageThumbnailUtil.createThumbnail(imageData, extension);

            String baseName = BulkUploadImageFilenameUtils.stripFileExtension(originalFileName);
            String thumbFileName = "thumb_" + baseName + "." + extension;
            String s3Key = "profile-images/employee/" + thumbFileName;
            return awsService.uploadFile(new java.io.ByteArrayInputStream(thumbBytes), s3Key, thumbBytes.length);
        } catch (Exception e) {
            log.error("Failed to upload user thumbnail to S3: {}", e.getMessage(), e);
            throw new IOException("Failed to upload thumbnail to S3: " + e.getMessage(), e);
        }
    }

    /**
     * Creates an audit trail record
     * Uses AuditTrailRepository directly since user-management cannot access restaurant-management services
     */
    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.REQUIRES_NEW)
    private AuditTrail createAuditTrail(
            User user,
            ActionType actionType,
            Restaurant restaurant,
            RequestStatus status,
            String ipAddress,
            String userAgent,
            UUID entityId,
            String entityType,
            String notes,
            User requestedBy,
            OffsetDateTime requestedAt,
            User reviewedBy,
            OffsetDateTime reviewedAt) {
        try {
            String logNumber = generateLogNumber();
            
            // Manager actions are non-request actions, always use NA status
            RequestStatus finalStatus = RequestStatus.NA;
            
            AuditTrail auditTrail = AuditTrail.builder()
                    .logNumber(logNumber)
                    .user(user)
                    .actionType(actionType)
                    .restaurant(restaurant)
                    .status(finalStatus)
                    .ipAddress(ipAddress)
                    .userAgent(userAgent)
                    .entityId(entityId)
                    .entityType(entityType)
                    .notes(notes)
                    .requestedBy(requestedBy)
                    .requestedAt(requestedAt)
                    .reviewedBy(reviewedBy)
                    .reviewedAt(reviewedAt)
                    .createdBy(user)
                    .createdAt(OffsetDateTime.now(ZoneOffset.UTC))
                    .updatedAt(OffsetDateTime.now(ZoneOffset.UTC))
                    .build();

            AuditTrail saved = auditTrailRepository.save(auditTrail);
            log.info("Audit trail created: {} - {} by user {} (logNumber: {})", 
                    actionType, entityType != null ? entityType : "N/A", 
                    user.getUserCode(), logNumber);
            
            return saved;
        } catch (Exception e) {
            log.error("Failed to create audit trail for action {} by user {}: {}", 
                    actionType, user != null ? user.getUserCode() : "unknown", e.getMessage(), e);
            return null;
        }
    }

    /**
     * Generates a unique log number in format: REQ + sequence number
     * Uses database sequence audit_trail_seq for thread-safe unique number generation
     */
    private String generateLogNumber() {
        try {
            // Get next value from database sequence (thread-safe)
            Long sequenceNumber = auditTrailRepository.getNextSequenceValue();
            return String.format("REQ%05d", sequenceNumber);
        } catch (Exception e) {
            log.error("Error generating log number from sequence, using timestamp-based fallback: {}", e.getMessage());
            return "REQ" + (System.currentTimeMillis() % 100000);
        }
    }
    
}
