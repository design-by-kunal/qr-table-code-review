package com.gulfnet.restaurantmanagement.controller;

import com.gulfnet.restaurantmanagement.service.EmailScheduleService;
import com.gulfnet.restaurantmanagement.service.ReportsService;
import com.gulfnet.shared_library.enums.ReportType;
import com.gulfnet.shared_library.model.response.dto.EmailScheduleListResponse;
import com.gulfnet.shared_library.model.response.dto.ReportsOverviewResponse;
import com.gulfnet.shared_library.model.response.dto.ResponseDto;
import com.gulfnet.shared_library.model.response.dto.TodaySalesResponse;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.OffsetTime;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/v1/reports")
public class ReportsController {

    @Autowired
    private ReportsService reportsService;

    @Autowired
    private EmailScheduleService emailScheduleService;

    /**
     * Retrieves a comprehensive reports overview including itemized sales, table-wise sales,
     * and discounts applied. Supports filtering by restaurant, restaurant group, time period,
     * and date range. Each section has independent pagination and sorting.
     * User and role are required via {@code User-ID} and {@code User-Role} headers.
     *
     * @param restaurantId           optional filter by restaurant ID (at least one of restaurantId or restaurantGroupId required)
     * @param restaurantGroupId     optional filter by restaurant group ID (at least one of restaurantId or restaurantGroupId required)
     * @param period                 optional predefined time period (e.g., "today", "week", "month")
     * @param startDate              optional start date and time for custom date range (ISO date-time format)
     * @param endDate                optional end date and time for custom date range (ISO date-time format)
     * @param itemizedPage           page number for itemized sales pagination (default: 1)
     * @param itemizedSize           page size for itemized sales pagination (default: 10)
     * @param itemizedSortBy         optional field to sort itemized sales by
     * @param itemizedSortDirection  sort direction for itemized sales (default: DESC)
     * @param tableWisePage          page number for table-wise sales pagination (default: 1)
     * @param tableWiseSize          page size for table-wise sales pagination (default: 10)
     * @param tableWiseSortBy        optional field to sort table-wise sales by
     * @param tableWiseSortDirection sort direction for table-wise sales (default: DESC)
     * @param discountsPage          page number for discounts pagination (default: 1)
     * @param discountsSize          page size for discounts pagination (default: 10)
     * @param discountsSortBy        optional field to sort discounts by
     * @param discountsSortDirection sort direction for discounts (default: DESC)
     * @param userId                 the user ID from the request header (required)
     * @param userRole               the user role from the request header (required)
     * @param locale                 locale code for localized responses (default: "en")
     * @return response containing reports overview with itemized sales, table-wise sales, and discounts
     */
    @GetMapping("/overview")
    public ResponseEntity<ResponseDto<ReportsOverviewResponse>> getReportsOverview(
            @RequestParam(required = false) UUID restaurantId,
            @RequestParam(required = false) UUID restaurantGroupId,
            @RequestParam(required = false) String period,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime endDate,
            @RequestParam(required = false, defaultValue = "1") Integer itemizedPage,
            @RequestParam(required = false, defaultValue = "10") Integer itemizedSize,
            @RequestParam(required = false) String itemizedSortBy,
            @RequestParam(required = false, defaultValue = "DESC") Sort.Direction itemizedSortDirection,
            @RequestParam(required = false, defaultValue = "1") Integer tableWisePage,
            @RequestParam(required = false, defaultValue = "10") Integer tableWiseSize,
            @RequestParam(required = false) String tableWiseSortBy,
            @RequestParam(required = false, defaultValue = "DESC") Sort.Direction tableWiseSortDirection,
            @RequestParam(required = false, defaultValue = "1") Integer discountsPage,
            @RequestParam(required = false, defaultValue = "10") Integer discountsSize,
            @RequestParam(required = false) String discountsSortBy,
            @RequestParam(required = false, defaultValue = "DESC") Sort.Direction discountsSortDirection,
            @RequestHeader("User-ID") String userId,
            @RequestHeader("User-Role") String userRole,
            @RequestHeader(value = "locale", defaultValue = "en") String locale) {

        log.info("Request received to get reports overview - restaurantId: {}, restaurantGroupId: {}, period: {}, userRole: {}", 
                restaurantId, restaurantGroupId, period, userRole);

        // Validate at least one restaurant identifier is provided
        validateRestaurantIdentifiers(restaurantId, restaurantGroupId);

        ResponseDto<ReportsOverviewResponse> response = reportsService.getReportsOverview(
                period, startDate, endDate, restaurantId, restaurantGroupId,
                itemizedPage, itemizedSize, itemizedSortBy, itemizedSortDirection,
                tableWisePage, tableWiseSize, tableWiseSortBy, tableWiseSortDirection,
                discountsPage, discountsSize, discountsSortBy, discountsSortDirection,
                userId, userRole, locale);

        log.info("Successfully retrieved reports overview");
        return ResponseEntity.status(HttpStatus.OK).body(response);
    }

    /**
     * Export reports overview to an Excel workbook (.xlsx), including all sheets (summary, itemized, table-wise, discounts).
     * GET /api/v1/reports/export?restaurantId={id}&restaurantGroupId={id}&period={period}&startDate={date}&endDate={date}
     */
    @GetMapping("/export")
    public void exportReportsToExcel(
            @RequestParam(required = false) UUID restaurantId,
            @RequestParam(required = false) UUID restaurantGroupId,
            @RequestParam(required = false) String period,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime endDate,
            @RequestHeader("User-ID") String userId,
            @RequestHeader("User-Role") String userRole,
            @RequestHeader(value = "locale", defaultValue = "en") String locale,
            HttpServletResponse response) throws IOException {

        log.info("Request received to export reports to xlsx for restaurantId: {}, restaurantGroupId: {}, period: {}, userRole: {}",
                restaurantId, restaurantGroupId, period, userRole);

        // Validate at least one restaurant identifier is provided
        validateRestaurantIdentifiers(restaurantId, restaurantGroupId);

        reportsService.exportReportsToExcel(period, startDate, endDate, restaurantId, restaurantGroupId,
                userId, userRole, locale, response);

        log.info("Successfully exported reports to xlsx for restaurantId: {}, restaurantGroupId: {}",
                restaurantId, restaurantGroupId);
    }

    /**
     * Get Payment and Financials reports
     * GET /api/v1/reports/payment-and-financials?restaurantId={id}&restaurantGroupId={id}&period={period}&startDate={date}&endDate={date}
     */
    @GetMapping("/payment-and-financials")
    public ResponseEntity<ResponseDto<com.gulfnet.shared_library.model.response.dto.PaymentAndFinancialsResponse>> getPaymentAndFinancials(
            @RequestParam(required = false) UUID restaurantId,
            @RequestParam(required = false) UUID restaurantGroupId,
            @RequestParam(required = false) String period,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endDate,
            @RequestParam(required = false, defaultValue = "1") Integer cancellationPage,
            @RequestParam(required = false, defaultValue = "10") Integer cancellationSize,
            @RequestParam(required = false) String cancellationSortBy,
            @RequestParam(required = false, defaultValue = "DESC") Sort.Direction cancellationSortDirection,
            @RequestParam(required = false, defaultValue = "1") Integer chargebackPage,
            @RequestParam(required = false, defaultValue = "10") Integer chargebackSize,
            @RequestParam(required = false) String chargebackSortBy,
            @RequestParam(required = false, defaultValue = "DESC") Sort.Direction chargebackSortDirection,
            @RequestParam(required = false, defaultValue = "1") Integer wastagePage,
            @RequestParam(required = false, defaultValue = "10") Integer wastageSize,
            @RequestParam(required = false) String wastageSortBy,
            @RequestParam(required = false, defaultValue = "DESC") Sort.Direction wastageSortDirection,
            @RequestParam(required = false, defaultValue = "1") Integer shiftsPage,
            @RequestParam(required = false, defaultValue = "10") Integer shiftsSize,
            @RequestParam(required = false) String shiftsSortBy,
            @RequestParam(required = false, defaultValue = "DESC") Sort.Direction shiftsSortDirection,
            @RequestParam(required = false) String shiftsStatus,
            @RequestParam(required = false) UUID shiftsCashDrawerId,
            @RequestParam(required = false) UUID shiftsCashierId,
            @RequestParam(required = false) String shiftsSearch,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime shiftsStartDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime shiftsEndDate,
            @RequestHeader("User-ID") String userId,
            @RequestHeader("User-Role") String userRole,
            @RequestHeader(value = "locale", defaultValue = "en") String locale) {

        log.info("Request received to get payment and financials - restaurantId: {}, restaurantGroupId: {}, period: {}, userRole: {}", 
                restaurantId, restaurantGroupId, period, userRole);

        // Validate at least one restaurant identifier is provided
        validateRestaurantIdentifiers(restaurantId, restaurantGroupId);

        // Enforce chargeback sorting: newest first (createdAt DESC)
        String finalChargebackSortBy = "createdAt";
        Sort.Direction finalChargebackSortDirection = Sort.Direction.DESC;

        ResponseDto<com.gulfnet.shared_library.model.response.dto.PaymentAndFinancialsResponse> response =
                reportsService.getPaymentAndFinancials(
                        period, startDate, endDate, restaurantId, restaurantGroupId,
                        cancellationPage, cancellationSize, cancellationSortBy, cancellationSortDirection,
                        chargebackPage, chargebackSize, finalChargebackSortBy, finalChargebackSortDirection,
                        wastagePage, wastageSize, wastageSortBy, wastageSortDirection,
                        shiftsPage, shiftsSize, shiftsSortBy, shiftsSortDirection,
                        shiftsStatus, shiftsCashDrawerId, shiftsCashierId, shiftsSearch,
                        shiftsStartDate, shiftsEndDate,
                        userId, userRole, locale);

        log.info("Successfully retrieved payment and financials");
        return ResponseEntity.status(HttpStatus.OK).body(response);
    }

    /**
     * Export Payment and Financials reports to an Excel workbook (.xlsx).
     * GET /api/v1/reports/payment-and-financials/export?restaurantId={id}&restaurantGroupId={id}&period={period}&startDate={date}&endDate={date}
     */
    @GetMapping("/payment-and-financials/export")
    public void exportPaymentAndFinancialsToExcel(
            @RequestParam(required = false) UUID restaurantId,
            @RequestParam(required = false) UUID restaurantGroupId,
            @RequestParam(required = false) String period,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endDate,
            @RequestHeader("User-ID") String userId,
            @RequestHeader("User-Role") String userRole,
            @RequestHeader(value = "locale", defaultValue = "en") String locale,
            HttpServletResponse response) throws IOException {

        log.info("Request received to export payment and financials to xlsx for restaurantId: {}, restaurantGroupId: {}, period: {}, userRole: {}",
                restaurantId, restaurantGroupId, period, userRole);

        // Validate at least one restaurant identifier is provided
        validateRestaurantIdentifiers(restaurantId, restaurantGroupId);

        reportsService.exportPaymentAndFinancialsToExcel(period, startDate, endDate, restaurantId, restaurantGroupId,
                userId, userRole, locale, response);

        log.info("Successfully exported payment and financials to xlsx for restaurantId: {}, restaurantGroupId: {}",
                restaurantId, restaurantGroupId);
    }

    /**
     * Get Performance reports
     * GET /api/v1/reports/performance?restaurantId={id}&restaurantGroupId={id}&period={period}&startDate={date}&endDate={date}
     */
    @GetMapping("/performance")
    public ResponseEntity<ResponseDto<com.gulfnet.shared_library.model.response.dto.PerformanceResponse>> getPerformance(
            @RequestParam(required = false) UUID restaurantId,
            @RequestParam(required = false) UUID restaurantGroupId,
            @RequestParam(required = false) String period,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endDate,
            @RequestParam(required = false, defaultValue = "1") Integer serverPage,
            @RequestParam(required = false, defaultValue = "10") Integer serverSize,
            @RequestParam(required = false) String serverSortBy,
            @RequestParam(required = false, defaultValue = "DESC") Sort.Direction serverSortDirection,
            @RequestParam(required = false, defaultValue = "1") Integer page,
            @RequestParam(required = false, defaultValue = "10") Integer size,
            @RequestParam(required = false) String sortBy,
            @RequestParam(required = false, defaultValue = "DESC") Sort.Direction sortDirection,
            @RequestHeader("User-ID") String userId,
            @RequestHeader("User-Role") String userRole,
            @RequestHeader(value = "locale", defaultValue = "en") String locale) {

        log.info("Request received to get performance - restaurantId: {}, restaurantGroupId: {}, period: {}, userRole: {}", 
                restaurantId, restaurantGroupId, period, userRole);

        // Validate at least one restaurant identifier is provided
        validateRestaurantIdentifiers(restaurantId, restaurantGroupId);

        ResponseDto<com.gulfnet.shared_library.model.response.dto.PerformanceResponse> response = reportsService.getPerformance(
                period, startDate, endDate, restaurantId, restaurantGroupId,
                serverPage, serverSize, serverSortBy, serverSortDirection,
                page, size, sortBy, sortDirection,
                userId, userRole, locale);

        log.info("Successfully retrieved performance");
        return ResponseEntity.status(HttpStatus.OK).body(response);
    }

    /**
     * Export Performance reports to an Excel workbook (.xlsx).
     * GET /api/v1/reports/performance/export?restaurantId={id}&restaurantGroupId={id}&period={period}&startDate={date}&endDate={date}
     */
    @GetMapping("/performance/export")
    public void exportPerformanceToExcel(
            @RequestParam(required = false) UUID restaurantId,
            @RequestParam(required = false) UUID restaurantGroupId,
            @RequestParam(required = false) String period,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endDate,
            @RequestHeader("User-ID") String userId,
            @RequestHeader("User-Role") String userRole,
            @RequestHeader(value = "locale", defaultValue = "en") String locale,
            HttpServletResponse response) throws IOException {

        log.info("Request received to export performance to xlsx for restaurantId: {}, restaurantGroupId: {}, period: {}, userRole: {}",
                restaurantId, restaurantGroupId, period, userRole);

        // Validate at least one restaurant identifier is provided
        validateRestaurantIdentifiers(restaurantId, restaurantGroupId);

        reportsService.exportPerformanceToExcel(period, startDate, endDate, restaurantId, restaurantGroupId,
                userId, userRole, locale, response);

        log.info("Successfully exported performance to xlsx for restaurantId: {}, restaurantGroupId: {}",
                restaurantId, restaurantGroupId);
    }

    /**
     * Get sales for the last completed cashier day (fixed 24h window ending at the latest reset &lt;= now, UTC).
     * GET /api/v1/reports/today-sales?restaurantId={id}
     */
    @GetMapping("/today-sales")
    public ResponseEntity<ResponseDto<TodaySalesResponse>> getTodaySales(
            @RequestParam(required = false) UUID restaurantId,
            @RequestParam(required = false) UUID restaurantGroupId,
            @RequestHeader("User-ID") String userId,
            @RequestHeader("User-Role") String userRole,
            @RequestHeader(value = "locale", defaultValue = "en") String locale) {

        log.info("Request received to get today's sales - restaurantId: {}, restaurantGroupId: {}, userRole: {}", 
                restaurantId, restaurantGroupId, userRole);

        // Validate at least one restaurant identifier is provided
        validateRestaurantIdentifiers(restaurantId, restaurantGroupId);

        ResponseDto<TodaySalesResponse> response = reportsService.getTodaySales(restaurantId, restaurantGroupId, 
                userId, userRole, locale);

        log.info("Successfully retrieved today's sales");
        return ResponseEntity.status(HttpStatus.OK).body(response);
    }

    /**
     * Get email schedules for a restaurant
     * This endpoint returns all email schedule configurations for the specified restaurant
     * GET /api/v1/reports/email-schedules?restaurantId={id}
     * 
     * @param restaurantId UUID of the restaurant (required)
     * @param reportType Optional report type filter (e.g., DAILY_SALES_SUMMARY, SALES, etc.)
     * @param userId User ID from User-ID header
     * @param userRole User role from User-Role header
     * @param locale Locale for messages
     * @return List of email schedules for the restaurant
     */
    @GetMapping("/email-schedules")
    public ResponseEntity<ResponseDto<EmailScheduleListResponse>> getEmailSchedules(
            @RequestParam UUID restaurantId,
            @RequestParam(required = false) ReportType reportType,
            @RequestHeader(value = "User-ID") String userId,
            @RequestHeader(value = "User-Role") String userRole,
            @RequestHeader(value = "locale", defaultValue = "en") String locale) {

        log.info("Request received to get email schedules - restaurantId: {}, reportType: {}, userId: {}, userRole: {}", 
                restaurantId, reportType, userId, userRole);

        ResponseDto<EmailScheduleListResponse> response = emailScheduleService.getAllSchedules(
                userId, userRole, restaurantId, null, reportType, null, null, null, null, null, locale);

        log.info("Successfully retrieved email schedules for restaurantId: {}, count: {}", 
                restaurantId, response.getData() != null ? response.getData().getCount() : 0);
        return ResponseEntity.status(HttpStatus.OK).body(response);
    }

    /**
     * Internal API: Set daily summary report schedule time for a restaurant
     * This endpoint should be called internally to configure when the manager receives daily summary reports
     * POST /api/v1/reports/internal/set-daily-summary-schedule
     * Frontend will select a time, convert it to UTC format, and send it (similar to menu scheduling).
     * 
     * @param restaurantId UUID of the restaurant
     * @param scheduledTime Time in UTC format with timezone offset (e.g., "08:20:00+00:00" or "08:20:00Z") - received from frontend in UTC, used directly as UTC
     * @param userId User ID of the manager
     * @param userRole Role of the user (MANAGER or HQ_ADMIN)
     * @param locale Locale for messages
     */
    @PostMapping("/internal/set-daily-summary-schedule")
    public ResponseEntity<ResponseDto<com.gulfnet.shared_library.model.response.dto.EmailScheduleResponse>> setDailySummarySchedule(
            @RequestParam UUID restaurantId,
            @RequestParam String scheduledTime, // Accepts formats: "HH:mm:ss+00:00", "HH:mm:ssZ", or "HH:mm:ss 00:00"
            @RequestHeader("User-ID") String userId,
            @RequestHeader("User-Role") String userRole,
            @RequestHeader(value = "locale", defaultValue = "en") String locale) {

        log.info("Internal request to set daily summary schedule - restaurantId: {}, scheduledTime: {} (UTC)", 
                restaurantId, scheduledTime);

        if (restaurantId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "restaurantId is required");
        }

        if (scheduledTime == null || scheduledTime.trim().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "scheduledTime is required");
        }

        // Parse the time string - handle both space-separated and ISO-8601 formats
        OffsetTime offsetTime;
        try {
            // Try parsing as ISO-8601 first (e.g., "08:20:00+00:00" or "08:20:00Z")
            offsetTime = OffsetTime.parse(scheduledTime);
        } catch (Exception e) {
            try {
                // If that fails, try parsing space-separated format (e.g., "08:20:00 00:00")
                // Replace space with + to convert to ISO-8601 format
                String normalizedTime = scheduledTime.replace(" ", "+");
                offsetTime = OffsetTime.parse(normalizedTime);
            } catch (Exception e2) {
                log.error("Failed to parse scheduledTime: {}", scheduledTime, e2);
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, 
                    "Invalid scheduledTime format. Expected formats: 'HH:mm:ss+00:00', 'HH:mm:ssZ', or 'HH:mm:ss 00:00'");
            }
        }

        // This will be implemented in ReportsService
        ResponseDto<com.gulfnet.shared_library.model.response.dto.EmailScheduleResponse> response = 
                reportsService.setDailySummaryReportSchedule(restaurantId, offsetTime, userId, userRole, locale);

        log.info("Successfully set daily summary schedule for restaurantId: {}", restaurantId);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * Common validation for endpoints that require at least one of restaurantId or restaurantGroupId.
     */
    private void validateRestaurantIdentifiers(UUID restaurantId, UUID restaurantGroupId) {
        if (restaurantId == null && restaurantGroupId == null) {
            log.warn("Missing both restaurantId and restaurantGroupId parameters");
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Either restaurantId or restaurantGroupId must be provided");
        }
    }
}

