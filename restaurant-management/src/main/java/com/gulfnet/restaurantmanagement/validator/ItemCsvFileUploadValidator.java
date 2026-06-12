package com.gulfnet.restaurantmanagement.validator;

import com.gulfnet.restaurantmanagement.util.MessageUtil;
import com.gulfnet.restaurantmanagement.config.RestaurantChainConfigProperties;
import com.gulfnet.shared_library.exception.BadRequestException;
import com.gulfnet.shared_library.model.response.dto.ErrorDto;
import com.opencsv.CSVReader;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
@Slf4j
public class ItemCsvFileUploadValidator {

    private final MessageUtil messageUtil;
    private final RestaurantChainConfigProperties restaurantChainConfigProperties;

    @Value("${bulk.upload.max-file-size:10485760}") // 10MB default
    private long maxFileSize;

    @Value("${bulk.upload.max-record-count:1000}") // 1000 records default
    private int maxRecordCount;

    @Value("${bulk.upload.allowed-extensions:csv}")
    private String allowedExtensions;

    /**
     * Builds the expected CSV header dynamically based on configured languages.
     * Includes base_price, status, and language-specific name and description columns.
     * Compulsory languages are marked with an asterisk (*).
     *
     * @return array of expected header column names
     */
    private String[] getExpectedHeader() {
        java.util.List<String> header = new java.util.ArrayList<>();
        header.add("item_code*");
        header.add("base_price*");
        header.add("status*");
        header.add("alcohol_type*");
        RestaurantChainConfigProperties.RestaurantChainData chain = restaurantChainConfigProperties.getChain();
        java.util.List<RestaurantChainConfigProperties.SupportedLanguage> supported = chain != null ? chain.getSupportedLanguages() : java.util.Collections.emptyList();
        if (supported != null) {
            for (RestaurantChainConfigProperties.SupportedLanguage lang : supported) {
                String code = lang.getLanguageCode();
                boolean compulsory = lang.isCompulsory();
                String suffix = compulsory ? "*" : "";
                header.add("name_" + code + suffix);
                header.add("description_" + code + suffix);
            }
        }
        // Add image_name column (optional)
        header.add("image_name");
        return header.toArray(new String[0]);
    }

    /**
     * Main method to validate the uploaded CSV file.
     */
    public void validate(MultipartFile file) {
        Locale userLocale = LocaleContextHolder.getLocale();
        List<ErrorDto> errors = new ArrayList<>();

        validateFileExtensionAndSize(file, errors, userLocale);

        if (errors.isEmpty()) {
            validateCsvContent(file, errors, userLocale);
        }

        throwValidationExceptionIfNeeded(file, errors);
    }

    /**
     * Validates file extension and size.
     */
    private void validateFileExtensionAndSize(MultipartFile file, List<ErrorDto> errors, Locale userLocale) {
        String extension = getFileExtension(file);
        if (extension == null || !getAllowedFileExtensions().contains(extension.toLowerCase())) {
            errors.add(new ErrorDto("INVALID_FILE_TYPE",
                    messageUtil.getMessage("bulk.upload.error.file.type", userLocale)));
        }

        if (file.getSize() > maxFileSize) {
            errors.add(new ErrorDto("FILE_TOO_LARGE",
                    messageUtil.getMessage("bulk.upload.error.file.size", userLocale,
                            formatFileSize(maxFileSize))));
        }
    }

    /**
     * Validates CSV content including header and record count.
     */
    private void validateCsvContent(MultipartFile file, List<ErrorDto> errors, Locale userLocale) {
        try {
            String content = readFileContentWithBomHandling(file);
            processCsvContent(content, errors, userLocale);
        } catch (Exception e) {
            log.error("Error while reading CSV file '{}': {}", file.getOriginalFilename(),
                    e.getMessage(), e);
            throw new BadRequestException(
                    messageUtil.getMessage("bulk.upload.error.file.read",
                            userLocale, e.getMessage()));
        }
    }

    /**
     * Reads file content and handles BOM detection.
     */
    private String readFileContentWithBomHandling(MultipartFile file) throws Exception {
        byte[] fileContent = file.getInputStream().readAllBytes();
        
        if (isUtf8Bom(fileContent)) {
            String content = new String(fileContent, 3, fileContent.length - 3, StandardCharsets.UTF_8);
            log.info("Detected UTF-8 BOM in CSV file during validation");
            return content;
        }
        
        if (isUtf16BeBom(fileContent)) {
            String content = new String(fileContent, 2, fileContent.length - 2, StandardCharsets.UTF_16BE);
            log.info("Detected UTF-16 BE BOM in CSV file during validation");
            return content;
        }
        
        if (isUtf16LeBom(fileContent)) {
            String content = new String(fileContent, 2, fileContent.length - 2, StandardCharsets.UTF_16LE);
            log.info("Detected UTF-16 LE BOM in CSV file during validation");
            return content;
        }
        
        log.info("No BOM detected, using UTF-8 encoding during validation");
        return new String(fileContent, StandardCharsets.UTF_8);
    }

    /**
     * Checks if file content has UTF-8 BOM.
     */
    private boolean isUtf8Bom(byte[] fileContent) {
        return fileContent.length >= 3 && 
               fileContent[0] == (byte) 0xEF && 
               fileContent[1] == (byte) 0xBB && 
               fileContent[2] == (byte) 0xBF;
    }

    /**
     * Checks if file content has UTF-16 BE BOM.
     */
    private boolean isUtf16BeBom(byte[] fileContent) {
        return fileContent.length >= 2 && 
               fileContent[0] == (byte) 0xFE && 
               fileContent[1] == (byte) 0xFF;
    }

    /**
     * Checks if file content has UTF-16 LE BOM.
     */
    private boolean isUtf16LeBom(byte[] fileContent) {
        return fileContent.length >= 2 && 
               fileContent[0] == (byte) 0xFF && 
               fileContent[1] == (byte) 0xFE;
    }

    /**
     * Processes CSV content: validates header and counts records.
     */
    private void processCsvContent(String content, List<ErrorDto> errors, Locale userLocale) throws Exception {
        try (CSVReader reader = new CSVReader(new StringReader(content))) {
            String[] header = reader.readNext();
            if (header == null) {
                errors.add(new ErrorDto("EMPTY_FILE",
                        messageUtil.getMessage("bulk.upload.error.file.empty", userLocale)));
                return;
            }

            validateHeader(header, errors, userLocale);
            int recordCount = countNonBlankRecords(reader);
            validateRecordCount(recordCount, errors, userLocale);
        }
    }

    /**
     * Counts non-blank records in the CSV file.
     */
    private int countNonBlankRecords(CSVReader reader) throws Exception {
        int recordCount = 0;
        String[] row;
        while ((row = reader.readNext()) != null) {
            if (!isBlankRow(row)) {
                recordCount++;
            }
        }
        return recordCount;
    }

    /**
     * Checks if a row is blank (all cells are null or empty).
     */
    private boolean isBlankRow(String[] row) {
        return Arrays.stream(row)
                .allMatch(s -> s == null || s.trim().isEmpty());
    }

    /**
     * Validates record count against limits.
     */
    private void validateRecordCount(int recordCount, List<ErrorDto> errors, Locale userLocale) {
        if (recordCount > maxRecordCount) {
            errors.add(new ErrorDto("TOO_MANY_RECORDS",
                    messageUtil.getMessage("bulk.upload.error.file.record.limit",
                            userLocale, recordCount, maxRecordCount)));
        }
        if (recordCount == 0) {
            errors.add(new ErrorDto("NO_RECORDS",
                    messageUtil.getMessage("bulk.upload.error.file.no.records", userLocale)));
        }
    }

    /**
     * Throws validation exception if there are any errors.
     */
    private void throwValidationExceptionIfNeeded(MultipartFile file, List<ErrorDto> errors) {
        if (errors.isEmpty()) {
            return;
        }

        log.error("CSV validation failed for file '{}' with {} error(s)",
                file.getOriginalFilename(), errors.size());
        String combinedMessage = errors.stream()
                .map(ErrorDto::getErrorMessage)
                .collect(Collectors.joining("; "));
        throw new BadRequestException(combinedMessage);
    }

    /**
     * Validates CSV header against expected columns.
     */
    private void validateHeader(String[] header, List<ErrorDto> errors, Locale userLocale) {
        // Clean header
        String[] cleanedHeader = Arrays.stream(header)
                .map(h -> h != null ? h.replaceAll("[^\\x20-\\x7E]", "").trim() : "")
                .toArray(String[]::new);

        String[] expectedHeader = getExpectedHeader();
        // Column count check
        if (cleanedHeader.length != expectedHeader.length) {
            errors.add(new ErrorDto("INVALID_HEADER_COUNT",
                    messageUtil.getMessage("bulk.upload.error.header.count",
                            userLocale, expectedHeader.length, cleanedHeader.length)));
            return; // Stop further header name checks
        }

        // Name check
        for (int i = 0; i < expectedHeader.length; i++) {
            if (!expectedHeader[i].equalsIgnoreCase(cleanedHeader[i])) {
                errors.add(new ErrorDto("INVALID_HEADER",
                        messageUtil.getMessage("bulk.upload.error.header.mismatch",
                                userLocale, i + 1, expectedHeader[i], cleanedHeader[i])));
            }
        }
    }

    /**
     * Extracts the file extension.
     */
    private String getFileExtension(MultipartFile file) {
        String originalFilename = file.getOriginalFilename();
        if (originalFilename == null || !originalFilename.contains(".")) {
            return null;
        }
        return originalFilename.substring(originalFilename.lastIndexOf('.') + 1);
    }

    /**
     * Returns allowed file extensions as lowercase list.
     */
    private List<String> getAllowedFileExtensions() {
        return Arrays.asList(allowedExtensions.toLowerCase().split(","));
    }

    /**
     * Formats file size into a human-readable string.
     */
    private String formatFileSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return (bytes / 1024) + " KB";
        if (bytes < 1024 * 1024 * 1024) return (bytes / (1024 * 1024)) + " MB";
        return (bytes / (1024 * 1024 * 1024)) + " GB";
    }
}
