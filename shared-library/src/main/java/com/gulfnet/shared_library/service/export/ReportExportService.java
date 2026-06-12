package com.gulfnet.shared_library.service.export;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gulfnet.shared_library.enums.ReportType;
import com.gulfnet.shared_library.model.request.ReportExportRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * Generic CSV export service that coordinates report exports by mapping report types to their data providers.
 * This service is in shared-library for use across all microservices.
 * 
 * Data providers are auto-discovered from the Spring application context.
 * Each service should implement ReportDataProvider and annotate it with @Component.
 */
@Slf4j
@Service
public class ReportExportService {

    private final CsvExportService csvExportService;
    private final ApplicationContext applicationContext;
    private final ObjectMapper objectMapper;
    private final Map<ReportType, ReportDataProvider<?>> providerCache = new HashMap<>();

    @Autowired
    public ReportExportService(CsvExportService csvExportService, ApplicationContext applicationContext, ObjectMapper objectMapper) {
        this.csvExportService = csvExportService;
        this.applicationContext = applicationContext;
        this.objectMapper = objectMapper;
    }

    /**
     * Export a report to CSV using the generic export engine.
     * This method will automatically find the appropriate data provider for the report type.
     *
     * @param reportType Type of report to export
     * @param filters Filter parameters
     * @param locale Locale for localization
     * @param response HTTP response
     * @throws IOException If export fails
     */
    public void exportReport(ReportType reportType, Map<String, Object> filters, String locale, HttpServletResponse response) throws IOException {
        ReportDataProvider<?> dataProvider = getDataProvider(reportType);

        ReportExportRequest request = ReportExportRequest.builder()
                .reportType(reportType)
                .filters(filters)
                .locale(locale != null ? locale : "en")
                .pageSize(1000)
                .compressionThreshold(100000L)
                .enableCompression(true)
                .build();

        csvExportService.exportToCsv(request, dataProvider, response);
    }

    /**
     * Export a report to CSV with custom configuration.
     *
     * @param reportType Type of report to export
     * @param filters Filter parameters
     * @param locale Locale for localization
     * @param pageSize Page size for streaming (default: 1000)
     * @param compressionThreshold Threshold for compression (default: 100000)
     * @param enableCompression Whether to enable compression
     * @param response HTTP response
     * @throws IOException If export fails
     */
    public void exportReport(ReportType reportType, Map<String, Object> filters, String locale,
                            Integer pageSize, Long compressionThreshold, Boolean enableCompression,
                            HttpServletResponse response) throws IOException {
        ReportDataProvider<?> dataProvider = getDataProvider(reportType);

        ReportExportRequest request = ReportExportRequest.builder()
                .reportType(reportType)
                .filters(filters)
                .locale(locale != null ? locale : "en")
                .pageSize(pageSize != null && pageSize > 0 ? pageSize : 1000)
                .compressionThreshold(compressionThreshold != null && compressionThreshold > 0 ? compressionThreshold : 100000L)
                .enableCompression(enableCompression == null || enableCompression)
                .build();

        csvExportService.exportToCsv(request, dataProvider, response);
    }

    /**
     * Export a report to CSV using JSON input for filters.
     * This method accepts filters as a JSON string and parses them into a Map.
     *
     * @param reportType Type of report to export
     * @param filtersJson JSON string containing filter parameters (e.g., {"restaurantId": "...", "startDate": "..."})
     * @param locale Locale for localization
     * @param response HTTP response
     * @throws IOException If export fails or JSON parsing fails
     * @throws IllegalArgumentException If JSON is invalid
     */
    public void exportReportFromJson(ReportType reportType, String filtersJson, String locale, HttpServletResponse response) throws IOException {
        Map<String, Object> filters = parseJsonToFilters(filtersJson);
        exportReport(reportType, filters, locale, response);
    }

    /**
     * Export a report to CSV using JSON input with custom configuration.
     *
     * @param reportType Type of report to export
     * @param filtersJson JSON string containing filter parameters
     * @param locale Locale for localization
     * @param pageSize Page size for streaming (default: 1000)
     * @param compressionThreshold Threshold for compression (default: 100000)
     * @param enableCompression Whether to enable compression
     * @param response HTTP response
     * @throws IOException If export fails or JSON parsing fails
     * @throws IllegalArgumentException If JSON is invalid
     */
    public void exportReportFromJson(ReportType reportType, String filtersJson, String locale,
                                    Integer pageSize, Long compressionThreshold, Boolean enableCompression,
                                    HttpServletResponse response) throws IOException {
        Map<String, Object> filters = parseJsonToFilters(filtersJson);
        exportReport(reportType, filters, locale, pageSize, compressionThreshold, enableCompression, response);
    }

    /**
     * Parse JSON string to Map of filters.
     *
     * @param filtersJson JSON string containing filter parameters
     * @return Map of filter key-value pairs
     * @throws IOException If JSON parsing fails
     * @throws IllegalArgumentException If JSON is null or empty
     */
    private Map<String, Object> parseJsonToFilters(String filtersJson) throws IOException {
        if (filtersJson == null || filtersJson.trim().isEmpty()) {
            log.warn("Empty or null filters JSON provided, using empty filters map");
            return new HashMap<>();
        }

        try {
            TypeReference<Map<String, Object>> typeRef = new TypeReference<Map<String, Object>>() {};
            Map<String, Object> filters = objectMapper.readValue(filtersJson, typeRef);
            log.debug("Successfully parsed JSON filters: {}", filters);
            return filters;
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            log.error("Failed to parse filters JSON: {}", filtersJson, e);
            throw new IllegalArgumentException("Invalid JSON format for filters: " + e.getMessage(), e);
        }
    }

    /**
     * Get the appropriate data provider for a report type.
     * Uses caching to avoid repeated lookups.
     * 
     * @param reportType The report type
     * @return The data provider for the report type
     * @throws IllegalArgumentException If no data provider is found for the report type
     */
    private ReportDataProvider<?> getDataProvider(ReportType reportType) {
        // Check cache first
        if (providerCache.containsKey(reportType)) {
            return providerCache.get(reportType);
        }

        // Discover all ReportDataProvider beans from application context
        @SuppressWarnings("unchecked")
        Map<String, ReportDataProvider<?>> providers = (Map<String, ReportDataProvider<?>>) (Map<?, ?>) applicationContext.getBeansOfType(ReportDataProvider.class);
        
        log.debug("Found {} ReportDataProvider beans in application context", providers.size());

        // Try to find a provider that supports this report type
        // First, check if any provider implements ReportTypeProvider interface
        for (ReportDataProvider<?> provider : providers.values()) {
            if (provider instanceof ReportTypeProvider) {
                ReportTypeProvider typeProvider = (ReportTypeProvider) provider;
                if (typeProvider.supportsReportType(reportType)) {
                    providerCache.put(reportType, provider);
                    log.info("Found data provider for report type {}: {}", reportType, provider.getClass().getSimpleName());
                    return provider;
                }
            }
        }

        // Fallback: Use naming convention or manual mapping
        // This is a fallback for providers that don't implement ReportTypeProvider
        ReportDataProvider<?> provider = findProviderByConvention(reportType, providers);
        if (provider != null) {
            providerCache.put(reportType, provider);
            log.info("Found data provider for report type {} using convention: {}", reportType, provider.getClass().getSimpleName());
            return provider;
        }

        throw new IllegalArgumentException("No data provider found for report type: " + reportType + 
                ". Please implement ReportDataProvider and annotate it with @Component, " +
                "and optionally implement ReportTypeProvider interface to declare supported report types.");
    }

    /**
     * Fallback method to find provider by naming convention.
     * Looks for beans named like: {ReportType}ReportDataProvider
     */
    private ReportDataProvider<?> findProviderByConvention(ReportType reportType, Map<String, ReportDataProvider<?>> providers) {
        String expectedName = reportType.getCode().toLowerCase().replace("_", "") + "ReportDataProvider";
        
        for (Map.Entry<String, ReportDataProvider<?>> entry : providers.entrySet()) {
            String beanName = entry.getKey().toLowerCase();
            if (beanName.contains(expectedName) || beanName.contains(reportType.getCode().toLowerCase().replace("_", ""))) {
                return entry.getValue();
            }
        }
        
        return null;
    }

    /**
     * Clear the provider cache. Useful for testing or when providers are dynamically registered.
     */
    public void clearProviderCache() {
        providerCache.clear();
        log.debug("ReportExportService provider cache cleared");
    }
}

