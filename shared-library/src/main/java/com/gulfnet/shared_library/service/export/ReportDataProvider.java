package com.gulfnet.shared_library.service.export;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Interface for providing report data in a paginated, streaming-friendly manner.
 * Implementations should fetch data in chunks to support large datasets without memory issues.
 *
 * @param <T> The type of data objects to be exported
 */
public interface ReportDataProvider<T> {

    /**
     * Get the total count of records available for export.
     * This is used to determine if compression is needed and for progress tracking.
     *
     * @param filters Filter parameters specific to the report type
     * @return Total number of records
     */
    long getTotalCount(Map<String, Object> filters);

    /**
     * Fetch a page of data for export.
     * This method should be called repeatedly with increasing page numbers until
     * an empty list is returned.
     *
     * @param page Page number (0-indexed)
     * @param pageSize Number of records per page
     * @param filters Filter parameters specific to the report type
     * @return List of data objects for the requested page
     */
    List<T> fetchPage(int page, int pageSize, Map<String, Object> filters);

    /**
     * Get the column headers for the CSV export.
     * Headers should be in the order they will appear in the CSV.
     *
     * @param locale Locale for localized header names
     * @return Array of column header names
     */
    String[] getColumnHeaders(String locale);

    /**
     * Convert a data object to an array of string values for CSV export.
     * Values should be in the same order as the column headers.
     *
     * @param data The data object to convert
     * @param locale Locale for localized value formatting
     * @return Array of string values representing the data object
     */
    String[] convertToRow(T data, String locale);

    /**
     * Get metadata information for the report (e.g., report title, export date, filters applied).
     * This will be written at the top of the CSV file.
     *
     * @param filters Filter parameters used for the report
     * @param locale Locale for localized metadata
     * @return Map of metadata key-value pairs
     */
    Map<String, String> getReportMetadata(Map<String, Object> filters, String locale);

    /**
     * Get summary statistics for the report (optional).
     * This will be written after metadata and before the data rows.
     *
     * @param filters Filter parameters used for the report
     * @param locale Locale for localized summary
     * @return Map of summary statistics (key-value pairs), or empty map if no summary
     */
    default Map<String, String> getSummaryStatistics(Map<String, Object> filters, String locale) {
        return Collections.emptyMap();
    }
}

