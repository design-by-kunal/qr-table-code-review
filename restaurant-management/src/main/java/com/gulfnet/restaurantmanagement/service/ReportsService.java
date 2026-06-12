package com.gulfnet.restaurantmanagement.service;

import com.gulfnet.shared_library.model.response.dto.ReportsOverviewResponse;
import com.gulfnet.shared_library.model.response.dto.ResponseDto;
import org.springframework.data.domain.Sort;
import com.gulfnet.shared_library.model.response.dto.PaymentAndFinancialsResponse;
import com.gulfnet.shared_library.model.response.dto.PerformanceResponse;
import com.gulfnet.shared_library.model.response.dto.TodaySalesResponse;

import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.UUID;

public interface ReportsService {
    /**
     * Retrieves a comprehensive reports overview including itemized sales, table-wise sales,
     * and discounts applied. Each section has independent pagination and sorting.
     * Results are filtered based on user role and access permissions.
     *
     * @param period                 optional predefined time period (e.g., "today", "week", "month")
     * @param startDate              optional start date and time for custom date range
     * @param endDate                optional end date and time for custom date range
     * @param restaurantId           optional filter by restaurant ID
     * @param restaurantGroupId      optional filter by restaurant group ID
     * @param itemizedPage           page number for itemized sales pagination
     * @param itemizedSize           page size for itemized sales pagination
     * @param itemizedSortBy         optional field to sort itemized sales by
     * @param itemizedSortDirection  sort direction for itemized sales
     * @param tableWisePage          page number for table-wise sales pagination
     * @param tableWiseSize          page size for table-wise sales pagination
     * @param tableWiseSortBy        optional field to sort table-wise sales by
     * @param tableWiseSortDirection sort direction for table-wise sales
     * @param discountsPage          page number for discounts pagination
     * @param discountsSize          page size for discounts pagination
     * @param discountsSortBy        optional field to sort discounts by
     * @param discountsSortDirection sort direction for discounts
     * @param userId                 user ID for access control
     * @param userRole               user role for access control
     * @param locale                 locale code for localized responses
     * @return response containing reports overview with itemized sales, table-wise sales, and discounts
     */
    ResponseDto<ReportsOverviewResponse> getReportsOverview(
            String period,
            OffsetDateTime startDate,
            OffsetDateTime endDate,
            UUID restaurantId,
            UUID restaurantGroupId,
            Integer itemizedPage,
            Integer itemizedSize,
            String itemizedSortBy,
            Sort.Direction itemizedSortDirection,
            Integer tableWisePage,
            Integer tableWiseSize,
            String tableWiseSortBy,
            Sort.Direction tableWiseSortDirection,
            Integer discountsPage,
            Integer discountsSize,
            String discountsSortBy,
            Sort.Direction discountsSortDirection,
            String userId,
            String userRole,
            String locale);

    /** Exports the same scope as {@link #getReportsOverview} to Excel. */
    void exportReportsToExcel(
            String period,
            OffsetDateTime startDate,
            OffsetDateTime endDate,
            UUID restaurantId,
            UUID restaurantGroupId,
            String userId,
            String userRole,
            String locale,
            HttpServletResponse response) throws IOException;

    /** Exports the same scope as {@link #getReportsOverview} to CSV. */
    void exportReportsToCsv(
            String period,
            OffsetDateTime startDate,
            OffsetDateTime endDate,
            UUID restaurantId,
            UUID restaurantGroupId,
            String userId,
            String userRole,
            String locale,
            HttpServletResponse response) throws IOException;

    /**
     * Retrieves payment and financials reports including cancellations, chargebacks, wastage,
     * and cash drawer shifts. Each section has independent pagination and sorting.
     * Results are filtered based on user role and access permissions.
     *
     * @param period                    optional predefined time period (e.g., "today", "week", "month")
     * @param startDate                 optional start date and time for custom date range
     * @param endDate                   optional end date and time for custom date range
     * @param restaurantId              optional filter by restaurant ID
     * @param restaurantGroupId         optional filter by restaurant group ID
     * @param cancellationPage          page number for cancellations pagination
     * @param cancellationSize          page size for cancellations pagination
     * @param cancellationSortBy        optional field to sort cancellations by
     * @param cancellationSortDirection  sort direction for cancellations
     * @param chargebackPage            page number for chargebacks pagination
     * @param chargebackSize           page size for chargebacks pagination
     * @param chargebackSortBy          optional field to sort chargebacks by
     * @param chargebackSortDirection   sort direction for chargebacks
     * @param wastagePage               page number for wastage pagination
     * @param wastageSize               page size for wastage pagination
     * @param wastageSortBy             optional field to sort wastage by
     * @param wastageSortDirection      sort direction for wastage
     * @param shiftsPage                page number for shifts pagination
     * @param shiftsSize                page size for shifts pagination
     * @param shiftsSortBy              optional field to sort shifts by
     * @param shiftsSortDirection       sort direction for shifts
     * @param shiftsStatus              optional filter by shift status
     * @param shiftsCashDrawerId        optional filter by cash drawer ID
     * @param shiftsCashierId           optional filter by cashier ID
     * @param shiftsSearch              optional search term for shifts
     * @param shiftsStartDate           optional filter by shift start date and time
     * @param shiftsEndDate             optional filter by shift end date and time
     * @param userId                    user ID for access control
     * @param userRole                  user role for access control
     * @param locale                    locale code for localized responses
     * @return response containing payment and financials reports with all sections
     */
    ResponseDto<PaymentAndFinancialsResponse> getPaymentAndFinancials(
            String period,
            LocalDateTime startDate,
            LocalDateTime endDate,
            UUID restaurantId,
            UUID restaurantGroupId,
            Integer cancellationPage,
            Integer cancellationSize,
            String cancellationSortBy,
            Sort.Direction cancellationSortDirection,
            Integer chargebackPage,
            Integer chargebackSize,
            String chargebackSortBy,
            Sort.Direction chargebackSortDirection,
            Integer wastagePage,
            Integer wastageSize,
            String wastageSortBy,
            Sort.Direction wastageSortDirection,
            Integer shiftsPage,
            Integer shiftsSize,
            String shiftsSortBy,
            Sort.Direction shiftsSortDirection,
            String shiftsStatus,
            UUID shiftsCashDrawerId,
            UUID shiftsCashierId,
            String shiftsSearch,
            LocalDateTime shiftsStartDate,
            LocalDateTime shiftsEndDate,
            String userId,
            String userRole,
            String locale);

    /** Exports the same scope as {@link #getPaymentAndFinancials} to Excel. */
    void exportPaymentAndFinancialsToExcel(
            String period,
            LocalDateTime startDate,
            LocalDateTime endDate,
            UUID restaurantId,
            UUID restaurantGroupId,
            String userId,
            String userRole,
            String locale,
            HttpServletResponse response) throws IOException;

    /** Exports the same scope as {@link #getPaymentAndFinancials} to CSV. */
    void exportPaymentAndFinancialsToCsv(
            String period,
            LocalDateTime startDate,
            LocalDateTime endDate,
            UUID restaurantId,
            UUID restaurantGroupId,
            String userId,
            String userRole,
            String locale,
            HttpServletResponse response) throws IOException;

    /**
     * Retrieves performance reports including server performance metrics and other performance indicators.
     * Each section has independent pagination and sorting. Results are filtered based on user role and access permissions.
     *
     * @param period              optional predefined time period (e.g., "today", "week", "month")
     * @param startDate           optional start date and time for custom date range
     * @param endDate             optional end date and time for custom date range
     * @param restaurantId        optional filter by restaurant ID
     * @param restaurantGroupId   optional filter by restaurant group ID
     * @param serverPage          page number for server performance pagination
     * @param serverSize          page size for server performance pagination
     * @param serverSortBy        optional field to sort server performance by
     * @param serverSortDirection sort direction for server performance
     * @param page                page number for general performance pagination
     * @param size                page size for general performance pagination
     * @param sortBy              optional field to sort general performance by
     * @param sortDirection       sort direction for general performance
     * @param userId              user ID for access control
     * @param userRole            user role for access control
     * @param locale              locale code for localized responses
     * @return response containing performance reports with all sections
     */
    ResponseDto<PerformanceResponse> getPerformance(
            String period,
            LocalDateTime startDate,
            LocalDateTime endDate,
            UUID restaurantId,
            UUID restaurantGroupId,
            Integer serverPage,
            Integer serverSize,
            String serverSortBy,
            Sort.Direction serverSortDirection,
            Integer page,
            Integer size,
            String sortBy,
            Sort.Direction sortDirection,
            String userId,
            String userRole,
            String locale);

    /** Exports the same scope as {@link #getPerformance} to Excel. */
    void exportPerformanceToExcel(
            String period,
            LocalDateTime startDate,
            LocalDateTime endDate,
            UUID restaurantId,
            UUID restaurantGroupId,
            String userId,
            String userRole,
            String locale,
            HttpServletResponse response) throws IOException;

    /** Exports the same scope as {@link #getPerformance} to CSV. */
    void exportPerformanceToCsv(
            String period,
            LocalDateTime startDate,
            LocalDateTime endDate,
            UUID restaurantId,
            UUID restaurantGroupId,
            String userId,
            String userRole,
            String locale,
            HttpServletResponse response) throws IOException;

    ResponseDto<TodaySalesResponse> getTodaySales(UUID restaurantId, UUID restaurantGroupId, 
            String userId, String userRole, String locale);

    ResponseDto<com.gulfnet.shared_library.model.response.dto.EmailScheduleResponse> setDailySummaryReportSchedule(
            UUID restaurantId, 
            java.time.OffsetTime scheduledTime, // Time in UTC format with timezone offset from frontend
            String userId, 
            String userRole, 
            String locale);
}

