package com.gulfnet.shared_library.service.export;

import com.gulfnet.shared_library.enums.ReportType;
import com.gulfnet.shared_library.model.request.ReportExportRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for CsvExportService.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CsvExportServiceTest {

    private static final String HEADER_COLUMN1 = "Column1";
    private static final String HEADER_COLUMN2 = "Column2";

    @Mock
    private ReportDataProvider<TestData> mockDataProvider;

    @Mock
    private HttpServletResponse response;

    private CsvExportService csvExportService;
    private ByteArrayOutputStream outputStream;
    private ReportExportRequest request;

    /**
     * Constructs {@link CsvExportService}, wires the mocked {@link HttpServletResponse} to write into
     * an in-memory {@link ByteArrayOutputStream}, stubs UTF-8 encoding, and seeds a default
     * {@link ReportExportRequest} (compression enabled with a high threshold so typical tests emit plain CSV).
     *
     * @throws IOException if wiring the servlet output stream fails
     */
    @BeforeEach
    void setUp() throws IOException {
        csvExportService = new CsvExportService();
        outputStream = new ByteArrayOutputStream();
        when(response.getOutputStream()).thenReturn(new jakarta.servlet.ServletOutputStream() {
            @Override
            public void write(int b) throws IOException {
                outputStream.write(b);
            }

            @Override
            public boolean isReady() {
                return true;
            }

            @Override
            public void setWriteListener(jakarta.servlet.WriteListener listener) {
                // Not needed for testing
            }
        });
        when(response.getCharacterEncoding()).thenReturn("UTF-8");

        request = ReportExportRequest.builder()
                .reportType(ReportType.DISCOUNTS_OFFERS)
                .filters(new HashMap<>())
                .locale("en")
                .pageSize(1000)
                .compressionThreshold(100000L)
                .enableCompression(true)
                .build();
    }

    /**
     * Verifies a standard CSV export: response content type is {@code text/csv;charset=UTF-8}, the
     * written body is non-empty, and column headers from {@link ReportDataProvider#getColumnHeaders} appear in the output.
     */
    @Test
    void testExportToCsv_BasicExport() throws IOException {
        // Arrange
        when(mockDataProvider.getTotalCount(any())).thenReturn(100L);
        when(mockDataProvider.getReportMetadata(any(), any())).thenReturn(createMetadata());
        when(mockDataProvider.getSummaryStatistics(any(), any())).thenReturn(createSummary());
        when(mockDataProvider.getColumnHeaders(any())).thenReturn(new String[]{HEADER_COLUMN1, HEADER_COLUMN2});
        when(mockDataProvider.fetchPage(eq(0), eq(1000), any())).thenReturn(createTestData(100));
        when(mockDataProvider.convertToRow(any(), any())).thenAnswer(invocation -> {
            TestData data = invocation.getArgument(0);
            return new String[]{data.getId(), data.getName()};
        });

        // Act
        csvExportService.exportToCsv(request, mockDataProvider, response);

        // Assert
        verify(response).setContentType("text/csv;charset=UTF-8");
        assertTrue(outputStream.size() > 0);
        String output = outputStream.toString("UTF-8");
        assertTrue(output.contains(HEADER_COLUMN1));
        assertTrue(output.contains(HEADER_COLUMN2));
    }

    /**
     * Verifies the ZIP path when {@link ReportDataProvider#getTotalCount} exceeds
     * {@link ReportExportRequest#getCompressionThreshold} (with compression enabled): response content type
     * is {@code application/zip} instead of plain CSV.
     */
    @Test
    void testExportToCsv_WithCompression() throws IOException {
        // Arrange
        request = ReportExportRequest.builder()
                .reportType(ReportType.DISCOUNTS_OFFERS)
                .filters(new HashMap<>())
                .locale("en")
                .pageSize(1000)
                .compressionThreshold(100L)
                .enableCompression(true)
                .build();

        when(mockDataProvider.getTotalCount(any())).thenReturn(200L);
        when(mockDataProvider.getReportMetadata(any(), any())).thenReturn(createMetadata());
        when(mockDataProvider.getSummaryStatistics(any(), any())).thenReturn(createSummary());
        when(mockDataProvider.getColumnHeaders(any())).thenReturn(new String[]{HEADER_COLUMN1, HEADER_COLUMN2});
        when(mockDataProvider.fetchPage(eq(0), eq(1000), any())).thenReturn(createTestData(100));
        when(mockDataProvider.convertToRow(any(), any())).thenAnswer(invocation -> {
            TestData data = invocation.getArgument(0);
            return new String[]{data.getId(), data.getName()};
        });

        // Act
        csvExportService.exportToCsv(request, mockDataProvider, response);

        // Assert
        verify(response).setContentType("application/zip");
    }

    // Helper methods
    private Map<String, String> createMetadata() {
        Map<String, String> metadata = new LinkedHashMap<>();
        metadata.put("Report Title", "Test Report");
        return metadata;
    }

    private Map<String, String> createSummary() {
        Map<String, String> summary = new LinkedHashMap<>();
        summary.put("Total Records", "100");
        return summary;
    }

    private List<TestData> createTestData(int count) {
        List<TestData> data = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            TestData item = new TestData();
            item.setId("ID-" + i);
            item.setName("Name-" + i);
            data.add(item);
        }
        return data;
    }


    // Test data class
    private static class TestData {
        private String id;
        private String name;

        public String getId() {
            return id;
        }

        public void setId(String id) {
            this.id = id;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }
    }
}
