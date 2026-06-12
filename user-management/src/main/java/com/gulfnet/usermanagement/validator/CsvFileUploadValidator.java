package com.gulfnet.usermanagement.validator;

import com.gulfnet.shared_library.exception.BadRequestException;
import com.gulfnet.shared_library.model.response.dto.ErrorDto;
import com.gulfnet.usermanagement.util.MessageUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.ObjectUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.util.Locale;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.UnaryOperator;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
@Slf4j
public class CsvFileUploadValidator {

    private final MessageUtil messageUtil;

    @Value("${bulk.upload.max-file-size:10485760}") // 10MB default
    private String maxFileSize;

    @Value("${bulk.upload.max-record-count:1000}") // 1000 records default
    private String maxRecordCount;

    @Value("${bulk.upload.allowed-extensions:csv}")
    private String allowedExtensions;

    // Build expected header dynamically
    private String[] getExpectedHeader() {
        return new String[] {
            "user_code*", "first_name*", "last_name*", "email*", "mobile_number*", 
            "role*", "employment_type*", "restaurant_code", "restaurant_group_code", 
            "language_code*", "status*", "shift*", "image_name"
        };
    }

    /**
     * Validates the uploaded CSV file for allowed extension, maximum file size,
     * header structure, and total record count. Aggregates validation errors
     * and throws a {@link BadRequestException} if any constraint is violated.
     *
     * @param file     the multipart CSV file to validate
     * @param language optional language code (currently unused, locale taken from context)
     * @throws BadRequestException if the file fails any validation rule
     */
    public void validate(MultipartFile file, String language) {
        Locale userLocale = LocaleContextHolder.getLocale();
        List<ErrorDto> errors = new ArrayList<>();

        // Validate file extension
        String extension = getFileExtension(file).toLowerCase();
        if (!getAllowedFileExtensions().contains(extension)) {
            errors.add(new ErrorDto("INVALID_FILE_TYPE", 
                messageUtil.getMessage("bulk.upload.error.file.type", userLocale)));
        }

        // Validate file size
        if (file.getSize() > Long.parseLong(maxFileSize)) {
            errors.add(new ErrorDto("FILE_TOO_LARGE", 
                messageUtil.getMessage("bulk.upload.error.file.size", userLocale, formatFileSize(Long.parseLong(maxFileSize)))));
        }

        // Validate file headers and record count
        List<ErrorDto> headerErrors = validateFileHeaderAndTotalRecordCount(file);
        if (ObjectUtils.isNotEmpty(headerErrors)) {
            errors.addAll(headerErrors);
        }

        if (!errors.isEmpty()) {
            log.error("CSV validation failed with {} errors", errors.size());
            throw new BadRequestException("CSV validation failed: " + errors.get(0).getErrorMessage());
        }
    }

    /**
     * Validates the CSV header against the expected columns and checks that the
     * total number of non-blank records does not exceed configured limits.
     *
     * @param file the multipart CSV file whose header and record count are to be validated
     * @return list of {@link ErrorDto} describing header or record-count issues; empty if valid
     * @throws ResponseStatusException or {@link BadRequestException} for serious validation failures
     */
    private List<ErrorDto> validateFileHeaderAndTotalRecordCount(MultipartFile file) {
        List<ErrorDto> errors = new ArrayList<>();

        try {
            // Read file bytes to avoid stream consumption issues
            byte[] fileBytes = file.getBytes();
            
            try (com.opencsv.CSVReader reader = new com.opencsv.CSVReader(
                    new InputStreamReader(new java.io.ByteArrayInputStream(fileBytes), StandardCharsets.UTF_8))) {

                List<String[]> records = reader.readAll();

                // Validate header first - this is critical and should fail fast
                if (records.isEmpty()) {
                    errors.add(new ErrorDto("EMPTY_FILE", 
                        "File is empty. Please provide a valid CSV file with headers and data."));
                    return errors;
                }

                String[] header = records.get(0);
                String[] expectedHeader = getExpectedHeader(); // Dynamic header retrieval

                // Clean header by removing non-printable characters and BOM
                UnaryOperator<String> removeNonPrintableChars = str -> {
                    if (str == null) return "";
                    // Remove BOM and non-printable characters
                    str = str.replace("\uFEFF", ""); // Remove BOM
                    return str.replaceAll("[^\\x20-\\x7E]", "").trim();
                };

                String[] cleanedHeader = Arrays.stream(header)
                    .map(removeNonPrintableChars)
                    .toArray(String[]::new);

                // Validate column count - this is the first check
                if (cleanedHeader.length != expectedHeader.length) {
                    String errorMessage = String.format(
                        "Invalid number of columns. Expected %d columns but found %d columns. " +
                        "Expected columns: %s. " +
                        "Found columns: %s",
                        expectedHeader.length, 
                        cleanedHeader.length,
                        String.join(", ", expectedHeader),
                        String.join(", ", cleanedHeader)
                    );
                    errors.add(new ErrorDto("INVALID_HEADER_COUNT", errorMessage));
                    log.error("Header validation failed - Column count mismatch. Expected: {}, Found: {}", 
                        expectedHeader.length, cleanedHeader.length);
                    log.error("Expected headers: {}", Arrays.toString(expectedHeader));
                    log.error("Found headers: {}", Arrays.toString(cleanedHeader));
                    return errors;
                }

                // Validate each header column name
                for (int i = 0; i < expectedHeader.length; i++) {
                    String expected = expectedHeader[i].trim();
                    String found = cleanedHeader[i];
                    
                    if (!expected.equalsIgnoreCase(found)) {
                        String errorMessage = String.format(
                            "Invalid column name at position %d. Expected '%s' but found '%s'. " +
                            "All expected columns: %s",
                            (i + 1), expected, found, String.join(", ", expectedHeader)
                        );
                        errors.add(new ErrorDto("INVALID_HEADER", errorMessage));
                        log.error("Header validation failed - Column name mismatch at position {}. Expected: '{}', Found: '{}'", 
                            (i + 1), expected, found);
                        return errors;
                    }
                }

                // Filter out blank rows for accurate record count
                List<String[]> nonBlankRecords = records.stream()
                    .filter(row -> !Arrays.stream(row).allMatch(s -> s == null || s.trim().isEmpty()))
                    .collect(Collectors.toList());

                // Validate record count (excluding header and blank rows)
                long totalRecords = (long) nonBlankRecords.size() - 1;
                if (totalRecords > Long.parseLong(maxRecordCount)) {
                    String localizedMessage = messageUtil.getMessage(
                        "bulk.upload.error.max.records",
                        LocaleContextHolder.getLocale(),
                        Long.parseLong(maxRecordCount),
                        totalRecords
                    );

                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, localizedMessage);
                }

                // Validate minimum records
                if (totalRecords == 0) {
                    errors.add(new ErrorDto("NO_RECORDS", 
                        "File contains no data records. Please add at least one record."));
                }

                return errors;
            }

        } catch (ResponseStatusException rse) {
            throw rse;
        } catch (java.io.IOException e) {
            log.error("Error while reading CSV file", e);
            throw new BadRequestException("Error while reading CSV file: " + e.getMessage());
        } catch (Exception e) {
            log.error("Unexpected error while validating CSV file", e);
            throw new BadRequestException("Error while validating CSV file: " + e.getMessage());
        }
    }

    public String getFileExtension(MultipartFile file) {
        String originalFilename = file.getOriginalFilename();
        if (originalFilename == null) {
            return null;
        }
        int lastDotIndex = originalFilename.lastIndexOf('.');
        return lastDotIndex > 0 ? originalFilename.substring(lastDotIndex + 1) : null;
    }

    private List<String> getAllowedFileExtensions() {
        return Arrays.asList(allowedExtensions.toLowerCase().split(","));
    }

    private String formatFileSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return (bytes / 1024) + " KB";
        if (bytes < 1024 * 1024 * 1024) return (bytes / (1024 * 1024)) + " MB";
        return (bytes / (1024 * 1024 * 1024)) + " GB";
    }
} 