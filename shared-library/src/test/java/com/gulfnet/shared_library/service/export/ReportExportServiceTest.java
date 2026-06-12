package com.gulfnet.shared_library.service.export;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gulfnet.shared_library.enums.ReportType;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationContext;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Unit tests for ReportExportService.
 */
@ExtendWith(MockitoExtension.class)
class ReportExportServiceTest {

    @Mock
    private CsvExportService csvExportService;

    @Mock
    private ApplicationContext applicationContext;

    @Mock
    private HttpServletResponse response;

    @Mock
    private ReportDataProvider<Object> mockDataProvider;

    private ObjectMapper objectMapper;
    private ReportExportService reportExportService;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        reportExportService = new ReportExportService(csvExportService, applicationContext, objectMapper);
    }

    /**
     * When {@code filtersJson} is valid, {@link ReportExportService#exportReportFromJson} resolves a
     * {@link ReportDataProvider} from the Spring context and delegates CSV generation to
     * {@link CsvExportService#exportToCsv} exactly once with the HTTP response.
     */
    @Test
    void testExportReportFromJson_ValidJson() throws IOException {
        // Arrange
        ReportType reportType = ReportType.DISCOUNTS_OFFERS;
        String filtersJson = "{\"restaurantId\":\"123e4567-e89b-12d3-a456-426614174000\",\"startDate\":\"2024-01-01\"}";
        String locale = "en";

        // Create a provider that implements both interfaces
        TestReportDataProvider provider = new TestReportDataProvider();
        @SuppressWarnings("rawtypes")
        Map<String, ReportDataProvider> providers = new HashMap<>();
        providers.put("testProvider", provider);
        
        when(applicationContext.getBeansOfType(ReportDataProvider.class))
                .thenReturn(providers);

        // Act
        reportExportService.exportReportFromJson(reportType, filtersJson, locale, response);

        // Assert - Verify CSV service was called
        verify(csvExportService, times(1)).exportToCsv(any(), any(), eq(response));
    }

    /**
     * When {@code filtersJson} cannot be parsed, {@link ReportExportService#exportReportFromJson}
     * fails fast with {@link IllegalArgumentException} and does not invoke CSV export.
     */
    @Test
    void testExportReportFromJson_InvalidJson() {
        // Arrange
        ReportType reportType = ReportType.DISCOUNTS_OFFERS;
        String filtersJson = "{invalid json}";
        String locale = "en";

        // Act & Assert
        assertThrows(IllegalArgumentException.class, () ->
                reportExportService.exportReportFromJson(reportType, filtersJson, locale, response));
    }

    // Test helper class
    private static class TestReportDataProvider implements ReportDataProvider<Object>, ReportTypeProvider {
        @Override
        public boolean supportsReportType(ReportType reportType) {
            return reportType == ReportType.DISCOUNTS_OFFERS;
        }

        @Override
        public long getTotalCount(Map<String, Object> filters) {
            return 0;
        }

        @Override
        public java.util.List<Object> fetchPage(int page, int pageSize, Map<String, Object> filters) {
            return java.util.Collections.emptyList();
        }

        @Override
        public String[] getColumnHeaders(String locale) {
            return new String[]{"Column1"};
        }

        @Override
        public String[] convertToRow(Object data, String locale) {
            return new String[]{"value"};
        }

        @Override
        public Map<String, String> getReportMetadata(Map<String, Object> filters, String locale) {
            return new HashMap<>();
        }
    }
}
