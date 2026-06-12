package com.gulfnet.shared_library.service.export;

import com.gulfnet.shared_library.enums.ReportType;

/**
 * Optional interface that ReportDataProvider implementations can implement
 * to explicitly declare which report types they support.
 * 
 * This allows the ReportExportService to automatically discover and map
 * data providers to their report types.
 */
public interface ReportTypeProvider {
    
    /**
     * Check if this provider supports the given report type.
     * 
     * @param reportType The report type to check
     * @return true if this provider supports the report type, false otherwise
     */
    boolean supportsReportType(ReportType reportType);
}

