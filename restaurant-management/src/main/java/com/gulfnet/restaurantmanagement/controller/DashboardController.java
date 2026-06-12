package com.gulfnet.restaurantmanagement.controller;

import com.gulfnet.restaurantmanagement.service.DashboardService;
import com.gulfnet.shared_library.enums.ReportType;
import com.gulfnet.shared_library.model.response.dto.DashboardResponse;
import com.gulfnet.shared_library.model.response.dto.MenuDashboardResponse;
import com.gulfnet.shared_library.model.response.dto.ResponseDto;
import com.gulfnet.shared_library.model.response.dto.RestaurantDashboardResponse;
import com.gulfnet.shared_library.service.export.ReportExportService;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.time.LocalDateTime;

@RestController
@RequestMapping("/api/v1/dashboard")
public class DashboardController {

    @Autowired
    private DashboardService dashboardService;

    @Autowired
    private ReportExportService reportExportService;

    /**
     * Retrieves dashboard statistics including sales, orders, and other key metrics.
     * Supports filtering by time period, date range, restaurant group, and restaurant.
     *
     * @param period           optional predefined time period (e.g., "today", "week", "month")
     * @param startDate        optional start date and time for custom date range (ISO date-time format)
     * @param endDate          optional end date and time for custom date range (ISO date-time format)
     * @param restaurantGroupId optional filter by restaurant group ID
     * @param restaurantId     optional filter by restaurant ID
     * @param salesStatsPeriod optional period for sales statistics calculation
     * @param locale           locale code for localized responses (default: "en")
     * @return response containing dashboard statistics and metrics
     */
    @GetMapping("/statistics")
    public ResponseEntity<ResponseDto<DashboardResponse>> getDashboardStatistics(
            @RequestParam(required = false) String period,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endDate,
            @RequestParam(required = false) java.util.UUID restaurantGroupId,
            @RequestParam(required = false) java.util.UUID restaurantId,
            @RequestParam(required = false) String salesStatsPeriod,
            @RequestHeader(value = "locale", defaultValue = "en") String locale) {
        
        ResponseDto<DashboardResponse> response = dashboardService.getDashboardStatistics(period, startDate, endDate, restaurantGroupId, restaurantId, salesStatsPeriod, locale);
        return ResponseEntity.status(HttpStatus.OK).body(response);
    }

    /**
     * Retrieves restaurant-specific dashboard data including staff information and metrics.
     * Supports filtering by date range, restaurant group, and restaurant.
     * Includes paginated lists of on-shift staff and managers.
     *
     * @param dateRange        optional predefined date range (e.g., "today", "week", "month")
     * @param startDate        optional start date and time for custom date range (ISO date-time format)
     * @param endDate          optional end date and time for custom date range (ISO date-time format)
     * @param restaurantGroupId optional filter by restaurant group ID
     * @param restaurantId     optional filter by restaurant ID
     * @param onShiftStaffPage page number for on-shift staff pagination (default: 1)
     * @param onShiftStaffSize page size for on-shift staff pagination (default: 10)
     * @param managersPage     page number for managers pagination (default: 1)
     * @param managersSize     page size for managers pagination (default: 10)
     * @param locale           locale code for localized responses (default: "en")
     * @return response containing restaurant dashboard data with staff and metrics
     */
    @GetMapping("/restaurant")
    public ResponseEntity<ResponseDto<RestaurantDashboardResponse>> getRestaurantDashboard(
            @RequestParam(required = false) String dateRange,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endDate,
            @RequestParam(required = false) java.util.UUID restaurantGroupId,
            @RequestParam(required = false) java.util.UUID restaurantId,
            @RequestParam(required = false, defaultValue = "1") Integer onShiftStaffPage,
            @RequestParam(required = false, defaultValue = "10") Integer onShiftStaffSize,
            @RequestParam(required = false, defaultValue = "1") Integer managersPage,
            @RequestParam(required = false, defaultValue = "10") Integer managersSize,
            @RequestHeader(value = "locale", defaultValue = "en") String locale) {
        
        ResponseDto<RestaurantDashboardResponse> response = dashboardService.getRestaurantDashboard(
                dateRange, startDate, endDate, restaurantGroupId, restaurantId,
                onShiftStaffPage, onShiftStaffSize, managersPage, managersSize, locale);
        return ResponseEntity.status(HttpStatus.OK).body(response);
    }

    /**
     * Retrieves menu-specific dashboard data including menu performance metrics.
     * Supports filtering by date range, restaurant group, and restaurant.
     *
     * @param dateRange        optional predefined date range (e.g., "today", "week", "month")
     * @param startDate        optional start date and time for custom date range (ISO date-time format)
     * @param endDate          optional end date and time for custom date range (ISO date-time format)
     * @param restaurantGroupId optional filter by restaurant group ID
     * @param restaurantId     optional filter by restaurant ID
     * @param locale           locale code for localized responses (default: "en")
     * @return response containing menu dashboard data and performance metrics
     */
    @GetMapping("/menu")
    public ResponseEntity<ResponseDto<MenuDashboardResponse>> getMenuDashboard(
            @RequestParam(required = false) String dateRange,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endDate,
            @RequestParam(required = false) java.util.UUID restaurantGroupId,
            @RequestParam(required = false) java.util.UUID restaurantId,
            @RequestHeader(value = "locale", defaultValue = "en") String locale) {
        
        ResponseDto<MenuDashboardResponse> response = dashboardService.getMenuDashboard(dateRange, startDate, endDate, restaurantGroupId, restaurantId, locale);
        return ResponseEntity.status(HttpStatus.OK).body(response);
    }

    /**
     * Exports dashboard statistics to a CSV file and streams it to the HTTP response.
     * Uses the same filtering parameters as getDashboardStatistics.
     *
     * @param period           optional predefined time period (e.g., "today", "week", "month")
     * @param startDate        optional start date and time for custom date range (ISO date-time format)
     * @param endDate          optional end date and time for custom date range (ISO date-time format)
     * @param restaurantGroupId optional filter by restaurant group ID
     * @param restaurantId     optional filter by restaurant ID
     * @param salesStatsPeriod optional period for sales statistics calculation
     * @param locale           locale code for localized responses (default: "en")
     * @param response         HTTP servlet response to stream the CSV file to
     * @throws IOException if an I/O error occurs during CSV generation or streaming
     */
    @GetMapping("/statistics/export")
    public void exportDashboardStatisticsToCsv(
            @RequestParam(required = false) String period,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endDate,
            @RequestParam(required = false) java.util.UUID restaurantGroupId,
            @RequestParam(required = false) java.util.UUID restaurantId,
            @RequestParam(required = false) String salesStatsPeriod,
            @RequestHeader(value = "locale", defaultValue = "en") String locale,
            HttpServletResponse response) throws IOException {
        
        // Use shared library ReportExportService
        java.util.Map<String, Object> filters = new java.util.HashMap<>();
        filters.put("period", period);
        filters.put("startDate", startDate);
        filters.put("endDate", endDate);
        filters.put("restaurantGroupId", restaurantGroupId);
        filters.put("restaurantId", restaurantId);
        filters.put("salesStatsPeriod", salesStatsPeriod);
        filters.put("locale", locale);
        
        reportExportService.exportReport(ReportType.DASHBOARD_STATISTICS, filters, locale, response);
    }
}

