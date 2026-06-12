package com.gulfnet.shared_library.model.request;

import com.gulfnet.shared_library.enums.ReportType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * Request DTO for CSV report export.
 * Contains report type, filters, and export configuration.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReportExportRequest {
    /**
     * Type of report to export
     */
    private ReportType reportType;

    /**
     * Filter parameters specific to the report type.
     * Key-value pairs that will be passed to the ReportDataProvider.
     */
    private Map<String, Object> filters;

    /**
     * Locale for localized headers and values
     */
    private String locale;

    /**
     * Page size for streaming (default: 1000)
     */
    @Builder.Default
    private int pageSize = 1000;

    /**
     * Threshold for compression (default: 100000 rows).
     * If total rows exceed this, the CSV will be compressed.
     */
    @Builder.Default
    private long compressionThreshold = 100000L;

    /**
     * Whether to enable compression for large datasets
     */
    @Builder.Default
    private boolean enableCompression = true;
}

