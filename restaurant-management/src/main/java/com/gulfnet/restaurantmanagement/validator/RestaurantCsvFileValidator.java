package com.gulfnet.restaurantmanagement.validator;

import com.gulfnet.restaurantmanagement.util.MessageUtil;
import com.gulfnet.restaurantmanagement.config.LanguageConfiguration;
import com.opencsv.CSVReader;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import lombok.Data;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.function.UnaryOperator;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
@Slf4j
public class RestaurantCsvFileValidator {

    private static final String LOGO_NAME_COLUMN = "logo_name";
    private static final String PHONE_NUMBER_COLUMN = "phone_number";

    private final MessageUtil messageUtil;
    private final LanguageConfiguration languageConfiguration;

    @Value("${bulk.upload.max-file-size:10485760}")
    private long maxFileSizeBytes;

    @Value("${bulk.upload.max-record-count:1000}")
    private int maxRecordCount;

    @Value("${bulk.upload.allowed-extensions:csv}")
    private String allowedExtensions;
    
    /**
     * Get supported languages from configuration
     */
    protected List<LanguageConfiguration.LanguageConfig> getSupportedLanguages() {
        return languageConfiguration.getSupportedLanguages() != null ? 
            languageConfiguration.getSupportedLanguages() : new ArrayList<>();
    }

    /**
     * Restaurant bulk upload headers.
     * This field is maintained for backward compatibility but getDynamicHeaders() should be used instead.
     * Note: This field is final and cannot be modified after initialization.
     */
    public static final String[] RESTAURANT_BULK_UPLOAD_HEADERS = null;
    
    /**
     * Get dynamic headers based on configured languages
     */
    public String[] getDynamicHeaders() {
        List<String> headers = new ArrayList<>();
        
        // Add name headers for each configured language
        List<LanguageConfiguration.LanguageConfig> languages = getSupportedLanguages();
        log.info("Configured languages: {}", languages);
        
        for (LanguageConfiguration.LanguageConfig lang : languages) {
            String headerName = "name_" + lang.getLanguageCode() + (lang.isCompulsory() ? "*" : "");
            headers.add(headerName);
            log.info("Added header: {} for language: {} (compulsory: {})", headerName, lang.getLanguageCode(), lang.isCompulsory());
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
            LOGO_NAME_COLUMN,
            "gst_number*",
            PHONE_NUMBER_COLUMN
        ));
        
        log.info("Final dynamic headers: {}", headers);
        return headers.toArray(new String[0]);
    }
    
    /**
     * Get supported language codes
     */
    public List<String> getSupportedLanguageCodes() {
        return getSupportedLanguages().stream()
                .map(LanguageConfiguration.LanguageConfig::getLanguageCode)
                .collect(Collectors.toList());
    }
    
    /**
     * Get compulsory language codes
     */
    public List<String> getCompulsoryLanguageCodes() {
        return getSupportedLanguages().stream()
                .filter(LanguageConfiguration.LanguageConfig::isCompulsory)
                .map(LanguageConfiguration.LanguageConfig::getLanguageCode)
                .collect(Collectors.toList());
    }
    
    /**
     * Check if a language is compulsory
     */
    public boolean isLanguageCompulsory(String languageCode) {
        return getSupportedLanguages().stream()
                .anyMatch(lang -> lang.getLanguageCode().equals(languageCode) && lang.isCompulsory());
    }

    /**
     * Validates a restaurant CSV file for bulk upload.
     * Checks file extension, file size, header format (dynamically based on configured languages),
     * record count, and ensures the file is not empty.
     *
     * @param file the multipart CSV file to validate
     * @throws ResponseStatusException with BAD_REQUEST status if validation fails
     */
    public void validate(MultipartFile file) {
        Locale userLocale = LocaleContextHolder.getLocale();

        String extension = getFileExtension(file);
        List<String> allowedExtList = Arrays.stream(allowedExtensions.split(","))
                .map(String::trim)
                .map(String::toLowerCase)
                .collect(Collectors.toList());

        if (extension == null || !allowedExtList.contains(extension.toLowerCase())) {
            String msg = messageUtil.getMessage("bulk.restaurant.upload.error.file.type", userLocale);
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, msg);
        }

        if (file.getSize() > maxFileSizeBytes) {
            String msg = messageUtil.getMessage("bulk.restaurant.upload.error.file.size", userLocale, formatFileSize(maxFileSizeBytes));
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, msg);
        }

        try (CSVReader reader = new CSVReader(new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8))) {
            List<String[]> records = reader.readAll();

            if (records.isEmpty()) {
                String msg = messageUtil.getMessage("bulk.restaurant.upload.error.file.empty", userLocale);
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, msg);
            }

            String[] header = records.get(0);
            UnaryOperator<String> cleanCell = s -> s != null ? s.replaceAll("[^\\x20-\\x7E]", "").trim() : "";

            List<String> cleanedHeader = Arrays.stream(header)
                    .map(cleanCell)
                    .collect(Collectors.toList());

            // Use dynamic headers based on configuration
            List<String> expectedHeader = Arrays.asList(getDynamicHeaders());
            
            // Check if logo_name column is present (optional)
            boolean hasLogoColumn = cleanedHeader.contains(LOGO_NAME_COLUMN);
            boolean hasPhoneColumn = cleanedHeader.contains(PHONE_NUMBER_COLUMN);
            
            // Remove optional columns from expected header for comparison if not present in CSV
            List<String> expectedHeaderForComparison = new ArrayList<>(expectedHeader);
            if (!hasLogoColumn) {
                expectedHeaderForComparison.remove(LOGO_NAME_COLUMN);
            }
            if (!hasPhoneColumn) {
                expectedHeaderForComparison.remove(PHONE_NUMBER_COLUMN);
            }

            if (!cleanedHeader.equals(expectedHeaderForComparison)) {
                String msg = messageUtil.getMessage("bulk.restaurant.upload.error.header.mismatch", userLocale,
                        String.join(", ", expectedHeaderForComparison),
                        String.join(", ", cleanedHeader));
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, msg);
            }

            List<String[]> dataRows = records.stream()
                    .skip(1)
                    .filter(row -> !Arrays.stream(row).allMatch(cell -> cell == null || cell.trim().isEmpty()))
                    .collect(Collectors.toList());

            if (dataRows.isEmpty()) {
                String msg = messageUtil.getMessage("bulk.restaurant.upload.error.no.records", userLocale);
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, msg);
            }

            if (dataRows.size() > maxRecordCount) {
                String msg = messageUtil.getMessage("bulk.restaurant.upload.error.max.records", userLocale, maxRecordCount, dataRows.size());
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, msg);
            }

        } catch (ResponseStatusException rse) {
            throw rse;
        } catch (Exception e) {
            log.error("Error reading CSV file", e);
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Failed to read CSV: " + e.getMessage());
        }
    }

    private String getFileExtension(MultipartFile file) {
        String originalFilename = file.getOriginalFilename();
        if (originalFilename == null) {
            return null;
        }
        int lastDot = originalFilename.lastIndexOf('.');
        return (lastDot > 0) ? originalFilename.substring(lastDot + 1).toLowerCase() : null;
    }

    private String formatFileSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return (bytes / 1024) + " KB";
        if (bytes < 1024 * 1024 * 1024) return (bytes / (1024 * 1024)) + " MB";
        return (bytes / (1024 * 1024 * 1024)) + " GB";
    }
}
