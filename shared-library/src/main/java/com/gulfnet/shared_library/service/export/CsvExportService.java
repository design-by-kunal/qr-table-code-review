package com.gulfnet.shared_library.service.export;

import com.gulfnet.shared_library.enums.ReportType;
import com.gulfnet.shared_library.model.request.ReportExportRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Generic CSV export service with streaming and compression support.
 * Handles all report types through a unified interface.
 */
@Slf4j
@Service
public class CsvExportService {

    private static final DateTimeFormatter TIMESTAMP_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");
    private static final int DEFAULT_PAGE_SIZE = 1000;

    /**
     * Export report data to CSV with streaming support.
     * Automatically compresses if dataset exceeds threshold.
     *
     * @param request Export request with report type and filters
     * @param dataProvider Provider for fetching report data
     * @param response HTTP response to write CSV to
     * @param <T> Type of data objects
     * @throws IOException If export fails
     */
    public <T> void exportToCsv(
            ReportExportRequest request,
            ReportDataProvider<T> dataProvider,
            HttpServletResponse response) throws IOException {

        log.info("Starting CSV export for report type: {}", request.getReportType());

        // Get total count to determine if compression is needed
        long totalCount = dataProvider.getTotalCount(request.getFilters());
        log.info("Total records for export: {}", totalCount);

        // Determine if compression is needed
        boolean shouldCompress = request.isEnableCompression() &&
                totalCount > request.getCompressionThreshold();

        // Generate filename
        String baseFilename = generateFilename(request.getReportType(), shouldCompress);
        String contentType = shouldCompress ? "application/zip" : "text/csv;charset=UTF-8";
        String extension = shouldCompress ? ".zip" : ".csv";

        // Set response headers
        response.setContentType(contentType);
        response.setCharacterEncoding("UTF-8");
        response.setHeader("Content-Disposition", "attachment; filename=\"" + baseFilename + extension + "\"");

        // Get output stream
        OutputStream outputStream = response.getOutputStream();

        if (shouldCompress) {
            exportWithCompression(request, dataProvider, outputStream, baseFilename);
        } else {
            exportWithoutCompression(request, dataProvider, outputStream);
        }

        outputStream.flush();
        log.info("CSV export completed successfully. Total records: {}", totalCount);
    }

    /**
     * Export without compression (streaming directly to response).
     */
    private <T> void exportWithoutCompression(
            ReportExportRequest request,
            ReportDataProvider<T> dataProvider,
            OutputStream outputStream) throws IOException {

        try (Writer writer = new OutputStreamWriter(outputStream, java.nio.charset.StandardCharsets.UTF_8);
             CSVPrinter csvPrinter = new CSVPrinter(writer, getCsvFormat())) {

            // Write BOM for Excel compatibility
            writeBom(writer);

            // Write metadata
            Map<String, String> metadata = dataProvider.getReportMetadata(request.getFilters(), request.getLocale());
            if (metadata != null && !metadata.isEmpty()) {
                writeMetadata(csvPrinter, metadata);
            }

            // Write summary statistics
            Map<String, String> summary = dataProvider.getSummaryStatistics(request.getFilters(), request.getLocale());
            if (summary != null && !summary.isEmpty()) {
                writeSummary(csvPrinter, summary);
            }

            // Write column headers
            String[] headers = dataProvider.getColumnHeaders(request.getLocale());
            csvPrinter.printRecord((Object[]) headers);

            // Stream data in pages
            streamData(request, dataProvider, csvPrinter, writer, null,
                    "Exported {} records in {} pages");
        }
    }

    /**
     * Export with compression (for large datasets).
     */
    private <T> void exportWithCompression(
            ReportExportRequest request,
            ReportDataProvider<T> dataProvider,
            OutputStream outputStream,
            String baseFilename) throws IOException {

        try (ZipOutputStream zipOut = new ZipOutputStream(outputStream);
             Writer writer = new OutputStreamWriter(zipOut, java.nio.charset.StandardCharsets.UTF_8);
             CSVPrinter csvPrinter = new CSVPrinter(writer, getCsvFormat())) {

            // Create zip entry for CSV file
            String csvFilename = baseFilename.replace(".zip", ".csv");
            ZipEntry zipEntry = new ZipEntry(csvFilename);
            zipOut.putNextEntry(zipEntry);

            // Write BOM for Excel compatibility
            writeBom(writer);

            // Write metadata
            Map<String, String> metadata = dataProvider.getReportMetadata(request.getFilters(), request.getLocale());
            if (metadata != null && !metadata.isEmpty()) {
                writeMetadata(csvPrinter, metadata);
            }

            // Write summary statistics
            Map<String, String> summary = dataProvider.getSummaryStatistics(request.getFilters(), request.getLocale());
            if (summary != null && !summary.isEmpty()) {
                writeSummary(csvPrinter, summary);
            }

            // Write column headers
            String[] headers = dataProvider.getColumnHeaders(request.getLocale());
            csvPrinter.printRecord((Object[]) headers);

            // Stream data in pages
            streamData(
                    request,
                    dataProvider,
                    csvPrinter,
                    writer,
                    zipOut::flush,
                    "Exported and compressed {} records in {} pages"
            );
            zipOut.closeEntry();
            zipOut.finish();
        }
    }

    /**
     * Generate filename based on report type and timestamp.
     */
    private String generateFilename(ReportType reportType, boolean compressed) {
        String timestamp = LocalDateTime.now().format(TIMESTAMP_FORMATTER);
        String baseName = reportType.getCode() + "_" + timestamp;
        return sanitizeFilename(baseName);
    }

    // ========== CSV Formatting Utilities ==========

    /**
     * Write UTF-8 BOM to output stream for Excel compatibility.
     */
    private static void writeBom(Writer writer) throws IOException {
        writer.write('\uFEFF'); // UTF-8 BOM
    }

    /**
     * Write report metadata section to CSV.
     */
    private static void writeMetadata(CSVPrinter csvPrinter, Map<String, String> metadata) throws IOException {
        if (metadata == null || metadata.isEmpty()) {
            return;
        }
        for (Map.Entry<String, String> entry : metadata.entrySet()) {
            csvPrinter.printRecord(entry.getKey(), entry.getValue());
        }
        csvPrinter.printRecord(); // Empty line after metadata
    }

    /**
     * Write summary statistics section to CSV.
     */
    private static void writeSummary(CSVPrinter csvPrinter, Map<String, String> summary) throws IOException {
        if (summary == null || summary.isEmpty()) {
            return;
        }
        csvPrinter.printRecord("SUMMARY STATISTICS");
        csvPrinter.printRecord("Metric", "Value");
        for (Map.Entry<String, String> entry : summary.entrySet()) {
            csvPrinter.printRecord(entry.getKey(), entry.getValue());
        }
        csvPrinter.printRecord(); // Empty line after summary
    }

    /**
     * Get the standard CSV format configuration.
     */
    private static CSVFormat getCsvFormat() {
        return CSVFormat.DEFAULT
                .builder()
                .setRecordSeparator("\n")
                .setIgnoreEmptyLines(false)
                .setTrim(true)
                .build();
    }

    /**
     * Sanitize filename to remove invalid characters.
     */
    private static String sanitizeFilename(String filename) {
        if (filename == null) {
            return "export.csv";
        }
        return filename.replaceAll("[<>:\"/\\|?*]", "_");
    }

    /**
     * Shared streaming logic used by both compressed and non-compressed exports.
     */
    private <T> void streamData(
            ReportExportRequest request,
            ReportDataProvider<T> dataProvider,
            CSVPrinter csvPrinter,
            Writer writer,
            IOAction extraFlush,
            String logMessage) throws IOException {

        int page = 0;
        int pageSize = request.getPageSize() > 0 ? request.getPageSize() : DEFAULT_PAGE_SIZE;
        long exportedCount = 0;
        boolean hasMoreData = true;

        while (hasMoreData) {
            List<T> pageData = dataProvider.fetchPage(page, pageSize, request.getFilters());

            // Check if we have data to process
            if (pageData == null || pageData.isEmpty()) {
                hasMoreData = false;
            } else {
                // Write each row
                for (T data : pageData) {
                    String[] row = dataProvider.convertToRow(data, request.getLocale());
                    csvPrinter.printRecord((Object[]) row);
                    exportedCount++;
                }

                // Flush periodically to ensure streaming
                if (page % 10 == 0) {
                    csvPrinter.flush();
                    writer.flush();
                    if (extraFlush != null) {
                        extraFlush.run();
                    }
                }

                page++;

                // Safety check: if we got fewer records than page size, we're done
                if (pageData.size() < pageSize) {
                    hasMoreData = false;
                }
            }
        }

        csvPrinter.flush();
        writer.flush();
        if (extraFlush != null) {
            extraFlush.run();
        }
        log.info(logMessage, exportedCount, page);
    }

    @FunctionalInterface
    private interface IOAction {
        void run() throws IOException;
    }
}

