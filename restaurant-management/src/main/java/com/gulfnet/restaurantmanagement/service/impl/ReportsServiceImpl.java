package com.gulfnet.restaurantmanagement.service.impl;

import com.gulfnet.restaurantmanagement.service.ReportsService;
import com.gulfnet.restaurantmanagement.util.MessageUtil;
import com.gulfnet.restaurantmanagement.util.PaymentMethodDisplaySupport;
import com.gulfnet.shared_library.entity.CategoryTranslation;
import com.gulfnet.shared_library.entity.Item;
import com.gulfnet.shared_library.entity.ItemTranslation;
import com.gulfnet.shared_library.entity.ComboTranslation;
import com.gulfnet.shared_library.entity.Restaurant;
import com.gulfnet.shared_library.entity.RestaurantGroup;
import com.gulfnet.shared_library.entity.RestaurantTable;
import com.gulfnet.shared_library.entity.Transaction;
import com.gulfnet.shared_library.entity.User;
import com.gulfnet.shared_library.entity.Order;
import com.gulfnet.shared_library.entity.CashDrawerTranslation;
import com.gulfnet.shared_library.entity.CashierShift;
import com.gulfnet.shared_library.enums.ReportType;
import com.gulfnet.shared_library.enums.TransactionStatus;
import com.gulfnet.shared_library.enums.ShiftStatus;
import com.gulfnet.shared_library.service.export.ReportDataProvider;
import com.gulfnet.shared_library.service.export.ReportTypeProvider;
import com.gulfnet.shared_library.model.response.dto.DiscountOfferReportListDto;
import com.gulfnet.shared_library.model.response.dto.DiscountOfferReportResponse;
import com.gulfnet.shared_library.model.response.dto.PaginationMetaData;
import com.gulfnet.shared_library.model.response.dto.ReportsOverviewResponse;
import com.gulfnet.shared_library.model.response.dto.ResponseDto;
import com.gulfnet.shared_library.model.response.dto.TodaySalesResponse;
import com.gulfnet.shared_library.model.response.dto.CashierShiftListResponse;
import com.gulfnet.shared_library.model.response.dto.CashierShiftResponse;
import com.gulfnet.shared_library.repository.*;
import com.gulfnet.shared_library.model.response.dto.PaymentAndFinancialsResponse;
import com.gulfnet.shared_library.model.response.dto.PerformanceResponse;
import com.gulfnet.shared_library.util.CashDrawerTranslationUtil;
import com.gulfnet.shared_library.util.CurrencyFormatter;
import com.gulfnet.shared_library.util.TranslationUtils;
import com.gulfnet.restaurantmanagement.config.LocalizationProperties;
import com.gulfnet.restaurantmanagement.config.RestaurantChainConfigProperties;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationContext;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.OffsetTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
public class ReportsServiceImpl implements ReportsService {

    private static final Logger logger = LoggerFactory.getLogger(ReportsServiceImpl.class);
    private static final LocalDateTime SENTINEL_DATE = LocalDateTime.of(1970, 1, 1, 0, 0, 0);
    
    // Date format constants for consistency across all reports
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter DATETIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final DateTimeFormatter FILENAME_TIMESTAMP_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");
    
    // Period constants
    private static final String PERIOD_TODAY = "TODAY";
    private static final String PERIOD_30_DAYS = "30_DAYS";
    private static final String PERIOD_3_MONTHS = "3_MONTHS";
    private static final String PERIOD_6_MONTHS = "6_MONTHS";
    
    // Status constants
    private static final String STATUS_BALANCED = "balanced";
    
    // HTTP/Excel constants
    private static final String CONTENT_TYPE_EXCEL = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
    private static final String HEADER_CONTENT_DISPOSITION = "Content-Disposition";
    private static final String ATTACHMENT_FILENAME_PREFIX = "attachment; filename=\"";
    private static final String FILE_EXTENSION_XLSX = ".xlsx";
    
    
    // Sort/Filter key constants
    private static final String SORT_KEY_TOTAL_SALES = "total_sales";
    private static final String SORT_KEY_TOTALSALES = "totalsales";
    private static final String SORT_KEY_DATETIME = "datetime";
    private static final String SORT_KEY_DATE_TIME = "date_time";
    private static final String FILTER_KEY_TRANSACTION_STATUSES = "transactionStatuses";
    private static final String FILTER_KEY_START_DATE_TIME = "startDateTime";
    private static final String FILTER_KEY_END_DATE_TIME = "endDateTime";
    private static final String FILTER_KEY_RESTAURANT_ID = "restaurantId";
    
    // Default value constants
    private static final String DEFAULT_UNKNOWN_ITEM = "Unknown Item";
    private static final String DEFAULT_UNKNOWN_CATEGORY = "Unknown Category";
    private static final String DEFAULT_PERCENTAGE = "0.00%";
    
    // Additional header constants
    private static final String HEADER_TOTAL_SALES = "Total Sales";
    private static final String HEADER_TOTAL_ORDERS = "Total Orders";
    private static final String HEADER_TOTAL_DISCOUNT_APPLIED = "Total Discount Applied";
    
    // Period constants
    private static final String PERIOD_DAILY = "DAILY";
    
    // Default values
    private static final String DEFAULT_UNKNOWN_COMBO = "Unknown Combo";
    
    private static final String msgReportsGetError = "reports.get.error";
    private static final String msgReportsGetSuccess = "reports.get.success";
    private static final String msgReportsExportError = "reports.export.error";
    private static final String msgReportsExportFilenameOverview = "reports.export.filename.overview";
    private static final String msgReportsExportFilenamePaymentAndFinancials = "reports.export.filename.paymentAndFinancials";
    private static final String msgReportsExportFilenamePerformance = "reports.export.filename.performance";
    private static final String msgReportsExportFilenameDailySummary = "reports.export.filename.dailySummary";
    private static final String msgReportsErrorInvalidPeriod = "reports.error.invalid.period";
    private static final String msgReportsErrorRestaurantIdRequired = "reports.error.restaurantid.required";
    private static final String msgRestaurantGetErrorNotFound = "restaurant.get.error.notfound";

    @Value("${reports.export.timing-log.enabled:true}")
    private boolean reportExportTimingLogEnabled;

    @Autowired
    private TransactionRepository transactionRepository;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private OrderedItemRepository orderedItemRepository;

    @Autowired
    private ItemTranslationRepository itemTranslationRepository;

    @Autowired
    private ItemRepository itemRepository;

    @Autowired
    private CategoryTranslationRepository categoryTranslationRepository;

    @Autowired
    private OrderedComboRepository orderedComboRepository;

    @Autowired
    private ComboTranslationRepository comboTranslationRepository;

    @Autowired
    private RestaurantTableRepository restaurantTableRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RestaurantRepository restaurantRepository;

    @Autowired
    private RestaurantGroupRepository restaurantGroupRepository;

    @Autowired
    private LocalizationProperties localizationProperties;

    @Autowired
    private RestaurantChainConfigProperties restaurantChainConfigProperties;

    @Autowired
    private MessageUtil messageUtil;

    @Autowired
    private PaymentMethodDisplaySupport paymentMethodDisplaySupport;

    @Autowired
    private CashDrawerLogRepository cashDrawerLogRepository;

    @Autowired
    private CashierShiftRepository cashierShiftRepository;

    @Autowired
    private CashDrawerTranslationRepository cashDrawerTranslationRepository;

    @Autowired
    private ShiftTranslationRepository shiftTranslationRepository;

    @Autowired
    private RefundRepository refundRepository;

    @Autowired
    private RatingRepository ratingRepository;

    @Autowired
    private TransactionServiceImpl transactionService;

    @Autowired
    private com.gulfnet.restaurantmanagement.service.EmailScheduleService emailScheduleService;

    @Autowired
    private ApplicationContext applicationContext;

    @Value("${reports.export.auto-size-columns:false}")
    private boolean reportExportAutoSizeColumns;

    /**
     * Retrieves a comprehensive reports overview including daily sales summary, payment types breakdown,
     * itemized sales report, table-wise sales report, and discounts/promotions report.
     * Supports filtering by period or custom date range, restaurant or restaurant group.
     * Each sub-report supports independent pagination and sorting.
     *
     * @param period                  optional period filter (TODAY, 30_DAYS, 3_MONTHS, 6_MONTHS, or CUSTOM)
     * @param startDate               optional start date for custom date range
     * @param endDate                 optional end date for custom date range
     * @param restaurantId            optional filter by specific restaurant ID
     * @param restaurantGroupId      optional filter by restaurant group ID
     * @param itemizedPage            page number for itemized sales report
     * @param itemizedSize            page size for itemized sales report
     * @param itemizedSortBy          sort field for itemized sales report
     * @param itemizedSortDirection   sort direction for itemized sales report
     * @param tableWisePage           page number for table-wise sales report
     * @param tableWiseSize           page size for table-wise sales report
     * @param tableWiseSortBy         sort field for table-wise sales report
     * @param tableWiseSortDirection sort direction for table-wise sales report
     * @param discountsPage           page number for discounts/promotions report
     * @param discountsSize           page size for discounts/promotions report
     * @param discountsSortBy         sort field for discounts/promotions report
     * @param discountsSortDirection  sort direction for discounts/promotions report
     * @param userId                  user ID for access control
     * @param userRole                user role for access control
     * @param locale                  locale code for localized responses
     * @return ResponseDto containing comprehensive reports overview
     * @throws ResponseStatusException if validation fails, access denied, or restaurant not found
     */
    @Override
    public ResponseDto<ReportsOverviewResponse> getReportsOverview(
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
            String locale) {

        Locale localeObj = Locale.forLanguageTag(locale != null ? locale : "en");
        LocaleContextHolder.setLocale(localeObj);

        try {
            // Validate role-based access
            validateReportAccess(userRole, localeObj);

            // Get restaurant IDs based on role and filters
            // Skip restaurant existence validation query here; export loads the restaurant for display next.
            List<UUID> restaurantIds = getRestaurantIdsForReport(restaurantId, restaurantGroupId, userId, userRole, localeObj, false);

            if (restaurantIds.isEmpty()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        messageUtil.getMessage(msgReportsErrorRestaurantIdRequired, localeObj));
            }

            // For single restaurant reports, use the first restaurant ID
            UUID primaryRestaurantId = restaurantIds.get(0);

            // Validate date parameters
            validateDateParameters(period, startDate, endDate, localeObj);

            // Calculate date range based on period parameter (similar to dashboard)
            LocalDateTime startDateTime;
            LocalDateTime endDateTime = LocalDateTime.now(ZoneOffset.UTC).withHour(23).withMinute(59).withSecond(59).withNano(999999999);

            if (startDate != null && endDate != null) {
                // Custom date range provided
                // Respect the exact instants sent by the frontend (do not override time).
                startDateTime = startDate.withOffsetSameInstant(ZoneOffset.UTC).toLocalDateTime();
                endDateTime = endDate.withOffsetSameInstant(ZoneOffset.UTC).toLocalDateTime();
            } else if (period != null && !period.isEmpty()) {
                // Period parameter provided - calculate date range
                switch (period.toUpperCase()) {
                    case PERIOD_DAILY:
                    case PERIOD_TODAY:
                        // Single day - today
                        startDateTime = LocalDateTime.now(ZoneOffset.UTC).withHour(0).withMinute(0).withSecond(0).withNano(0);
                        endDateTime = LocalDateTime.now(ZoneOffset.UTC).withHour(23).withMinute(59).withSecond(59).withNano(999999999);
                        break;
                    case PERIOD_30_DAYS:
                        startDateTime = LocalDateTime.now(ZoneOffset.UTC).minusDays(30).withHour(0).withMinute(0).withSecond(0).withNano(0);
                        break;
                    case PERIOD_3_MONTHS:
                        startDateTime = LocalDateTime.now(ZoneOffset.UTC).minusMonths(3).withHour(0).withMinute(0).withSecond(0).withNano(0);
                        break;
                    case PERIOD_6_MONTHS:
                        startDateTime = LocalDateTime.now(ZoneOffset.UTC).minusMonths(6).withHour(0).withMinute(0).withSecond(0).withNano(0);
                        break;
                    default:
                        throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                                messageUtil.getMessage(msgReportsErrorInvalidPeriod, localeObj));
                }
            } else {
                // No period or dates provided - use sentinel date for start (all-time from beginning)
                // and current date/time for end (all-time up to now)
                startDateTime = SENTINEL_DATE;
                endDateTime = LocalDateTime.now(ZoneOffset.UTC).withHour(23).withMinute(59).withSecond(59).withNano(999999999);
            }

            // Build response - handle single or multiple restaurants
            ReportsOverviewResponse response;
            if (restaurantIds.size() == 1) {
                // Single restaurant - use existing methods
                UUID singleRestaurantId = restaurantIds.get(0);
                response = ReportsOverviewResponse.builder()
                        .dailySalesSummary(getDailySalesSummary(singleRestaurantId, startDateTime, endDateTime))
                        .paymentTypesBreakdown(getPaymentTypesBreakdown(singleRestaurantId, startDateTime, endDateTime))
                        .itemizedSalesReport(getItemizedSalesReport(singleRestaurantId, startDateTime, endDateTime, itemizedPage, itemizedSize, itemizedSortBy, itemizedSortDirection, locale))
                        .tableWiseSalesReport(getTableWiseSalesReport(singleRestaurantId, startDateTime, endDateTime, tableWisePage, tableWiseSize, tableWiseSortBy, tableWiseSortDirection))
                        .discountsPromotionsReport(getDiscountsPromotionsReport(singleRestaurantId, startDateTime, endDateTime, discountsPage, discountsSize, discountsSortBy, discountsSortDirection))
                        .build();
            } else {
                // Multiple restaurants - aggregate data
                response = ReportsOverviewResponse.builder()
                        .dailySalesSummary(getDailySalesSummaryForRestaurants(restaurantIds, startDateTime, endDateTime))
                        .paymentTypesBreakdown(getPaymentTypesBreakdownForRestaurants(restaurantIds, startDateTime, endDateTime))
                        // For multi-restaurant, itemized/table-wise/discounts reports are complex - 
                        // for now, return empty or aggregate from first restaurant
                        .itemizedSalesReport(getItemizedSalesReportForRestaurants(restaurantIds, startDateTime, endDateTime, itemizedPage, itemizedSize, itemizedSortBy, itemizedSortDirection, locale))
                        .tableWiseSalesReport(getTableWiseSalesReportForRestaurants(restaurantIds, startDateTime, endDateTime, tableWisePage, tableWiseSize, tableWiseSortBy, tableWiseSortDirection))
                        .discountsPromotionsReport(getDiscountsPromotionsReportForRestaurants(restaurantIds, startDateTime, endDateTime, discountsPage, discountsSize, discountsSortBy, discountsSortDirection))
                        .build();
            }

            return ResponseDto.<ReportsOverviewResponse>builder()
                    .message(messageUtil.getMessage(msgReportsGetSuccess, localeObj))
                    .data(response)
                    .build();

        } catch (ResponseStatusException ex) {
            // Re-throw ResponseStatusException as-is (preserves 400, 403, etc.)
            logger.error("Error fetching reports overview: {}", ex.getReason(), ex);
            throw ex;
        } catch (Exception e) {
            logger.error("Error fetching reports overview", e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    messageUtil.getMessage(msgReportsGetError, Locale.forLanguageTag(locale != null ? locale : "en")));
        }
    }

    /**
     * Calculates daily sales summary for a restaurant within a date range.
     * Includes total sales, total orders, total tables served, and average order value.
     *
     * @param restaurantId the UUID of the restaurant
     * @param startDate    start date/time of the period (inclusive)
     * @param endDate      end date/time of the period (inclusive)
     * @return DailySalesSummary with aggregated sales metrics
     */
    private ReportsOverviewResponse.DailySalesSummary getDailySalesSummary(
            UUID restaurantId, LocalDateTime startDate, LocalDateTime endDate) {

        List<Object[]> results = transactionRepository.getDailySalesSummary(
                restaurantId, startDate, endDate);

        if (results.isEmpty() || results.get(0) == null) {
            return ReportsOverviewResponse.DailySalesSummary.builder()
                    .totalSales(BigDecimal.ZERO)
                    .totalOrders(0L)
                    .totalTablesServed(0L)
                    .avgOrderValue(BigDecimal.ZERO)
                    .build();
        }

        Object[] row = results.get(0);
        BigDecimal totalSales = ((Number) row[0]).doubleValue() != 0 
                ? BigDecimal.valueOf(((Number) row[0]).doubleValue()) 
                : BigDecimal.ZERO;
        Long totalOrders = ((Number) row[1]).longValue();
        Long totalTablesServed = ((Number) row[2]).longValue();
        BigDecimal avgOrderValue = totalOrders > 0 
                ? totalSales.divide(BigDecimal.valueOf(totalOrders), 2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;

        return ReportsOverviewResponse.DailySalesSummary.builder()
                .totalSales(totalSales)
                .totalOrders(totalOrders)
                .totalTablesServed(totalTablesServed)
                .avgOrderValue(avgOrderValue)
                .build();
    }

    /**
     * Calculates payment types breakdown for a restaurant within a date range.
     * Groups sales by payment method (Cash, Card, UPI, etc.) and calculates percentages.
     *
     * @param restaurantId the UUID of the restaurant
     * @param startDate    start date/time of the period (inclusive)
     * @param endDate      end date/time of the period (inclusive)
     * @return list of PaymentTypeBreakdown with payment method, total sales, and percentage
     */
    private List<ReportsOverviewResponse.PaymentTypeBreakdown> getPaymentTypesBreakdown(
            UUID restaurantId, LocalDateTime startDate, LocalDateTime endDate) {

        List<Object[]> results = transactionRepository.getPaymentTypesBreakdown(
                restaurantId, startDate, endDate);

        if (results.isEmpty()) {
            return Collections.emptyList();
        }

        // Calculate total sales for percentage calculation
        BigDecimal totalSales = results.stream()
                .map(row -> BigDecimal.valueOf(((Number) row[1]).doubleValue()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return results.stream()
                .map(row -> {
                    String paymentMethod = (String) row[0];
                    BigDecimal sales = BigDecimal.valueOf(((Number) row[1]).doubleValue());
                    Double percentage = totalSales.compareTo(BigDecimal.ZERO) > 0
                            ? sales.divide(totalSales, 4, RoundingMode.HALF_UP)
                                    .multiply(BigDecimal.valueOf(100))
                                    .doubleValue()
                            : 0.0;

                    // Map payment method to display name
                    String displayName = mapPaymentMethodToDisplayName(paymentMethod);

                    return ReportsOverviewResponse.PaymentTypeBreakdown.builder()
                            .paymentMethod(displayName)
                            .totalSales(sales)
                            .percentage(percentage)
                            .build();
                })
                .collect(Collectors.toList());
    }

    /**
     * Maps payment method code to human-readable display name.
     * Groups CREDIT_CARD and DEBIT_CARD as "Card".
     *
     * @param paymentMethod the payment method code (e.g., "CASH", "CREDIT_CARD", "UPI")
     * @return human-readable display name (e.g., "Cash", "Card", "Mobile Wallet")
     */
    private String mapPaymentMethodToDisplayName(String paymentMethod) {
        if (paymentMethod == null) return "Unknown";
        switch (paymentMethod.toUpperCase()) {
            case "CASH":
                return "Cash";
            case "CREDIT_CARD":
            case "DEBIT_CARD":
                return "Card";
            case "UPI":
                return "Mobile Wallet";
            default:
                return paymentMethod;
        }
    }

    private static String itemizedSalesMergeKey(ReportsOverviewResponse.ItemizedSalesItem item) {
        if (item.getItemCode() != null && !item.getItemCode().isBlank()) {
            return "code:" + item.getItemCode().trim().toLowerCase(Locale.ROOT);
        }
        return "name:" + (item.getItemName() != null ? item.getItemName() : "");
    }

    /**
     * Generates itemized sales report for a restaurant within a date range.
     * Combines regular items and combos, includes translations, and calculates percentages.
     * Supports pagination and sorting.
     *
     * @param restaurantId the UUID of the restaurant
     * @param startDate    start date/time of the period (inclusive)
     * @param endDate      end date/time of the period (inclusive)
     * @param page         page number for pagination (null returns all)
     * @param size         page size for pagination (null returns all)
     * @param sortBy       field to sort by
     * @param sortDirection sort direction
     * @param locale       locale code for localized item/category names
     * @return ItemizedSalesReport with paginated list of items/combos and sales metrics
     */
    private ReportsOverviewResponse.ItemizedSalesReport getItemizedSalesReport(
            UUID restaurantId, LocalDateTime startDate, LocalDateTime endDate,
            Integer page, Integer size, String sortBy, Sort.Direction sortDirection, String locale) {

        // Fetch regular items (excluding combo items)
        List<Object[]> itemResults = orderedItemRepository.getItemizedSalesReport(
                restaurantId, startDate, endDate);

        // Fetch combos
        List<Object[]> comboResults = orderedComboRepository.getItemizedComboSalesReport(
                restaurantId, startDate, endDate);

        // Combine both lists
        List<ReportsOverviewResponse.ItemizedSalesItem> items = new ArrayList<>();

        // Process regular items
        if (!itemResults.isEmpty()) {
            // Get all item IDs and category IDs for batch translation lookup
            Set<UUID> itemIds = itemResults.stream()
                    .map(row -> (UUID) row[0])
                    .collect(Collectors.toSet());
            Set<UUID> categoryIds = itemResults.stream()
                    .map(row -> (UUID) row[1])
                    .filter(Objects::nonNull)
                    .collect(Collectors.toSet());

            // Batch fetch translations
            Map<UUID, List<ItemTranslation>> itemTranslationsMap = itemIds.isEmpty()
                    ? Collections.emptyMap()
                    : itemTranslationRepository.findAllByItemIdIn(new ArrayList<>(itemIds))
                            .stream()
                            .collect(Collectors.groupingBy(t -> t.getItem().getId()));

            Map<UUID, List<CategoryTranslation>> categoryTranslationsMap = categoryIds.isEmpty()
                    ? Collections.emptyMap()
                    : categoryTranslationRepository.findAllByCategoryIdIn(new ArrayList<>(categoryIds))
                            .stream()
                            .collect(Collectors.groupingBy(t -> t.getCategory().getId()));

            Map<UUID, String> itemCodeByItemId = new HashMap<>();
            if (!itemIds.isEmpty()) {
                for (Item entity : itemRepository.findAllById(new ArrayList<>(itemIds))) {
                    itemCodeByItemId.put(entity.getId(), entity.getItemCode());
                }
            }

            // Map item results
            List<ReportsOverviewResponse.ItemizedSalesItem> itemList = itemResults.stream()
                    .map(row -> {
                        UUID itemId = (UUID) row[0];
                        UUID categoryId = row[1] != null ? (UUID) row[1] : null;
                        Integer quantitySold = row[2] != null ? ((Number) row[2]).intValue() : 0;
                        BigDecimal unitPrice = row[3] != null 
                                ? BigDecimal.valueOf(((Number) row[3]).doubleValue())
                                        .setScale(2, RoundingMode.HALF_UP)
                                : BigDecimal.ZERO;
                        BigDecimal itemTotalSales = row[4] != null 
                                ? BigDecimal.valueOf(((Number) row[4]).doubleValue())
                                        .setScale(2, RoundingMode.HALF_UP)
                                : BigDecimal.ZERO;

                        // Get item name from translations
                        String itemName = getItemName(itemId, itemTranslationsMap, locale);
                        // Get category name from translations
                        String categoryName = getCategoryName(categoryId, categoryTranslationsMap, locale);

                        return ReportsOverviewResponse.ItemizedSalesItem.builder()
                                .itemCode(itemCodeByItemId.get(itemId))
                                .itemName(itemName)
                                .category(categoryName)
                                .quantitySold(quantitySold)
                                .unitPrice(unitPrice)
                                .totalSales(itemTotalSales)
                                .percentageOfTotalSales(0.0) // Will be calculated after combining with combos
                                .build();
                    })
                    .collect(Collectors.toList());
            items.addAll(itemList);
        }

        // Process combos
        if (!comboResults.isEmpty()) {
            // Get all combo IDs and category IDs for batch translation lookup
            Set<UUID> comboIds = comboResults.stream()
                    .map(row -> (UUID) row[0])
                    .collect(Collectors.toSet());
            Set<UUID> comboCategoryIds = comboResults.stream()
                    .map(row -> (UUID) row[1])
                    .filter(Objects::nonNull)
                    .collect(Collectors.toSet());

            // Batch fetch combo translations
            Map<UUID, List<ComboTranslation>> comboTranslationsMap = comboIds.isEmpty()
                    ? Collections.emptyMap()
                    : comboTranslationRepository.findByComboComboIdIn(new ArrayList<>(comboIds))
                            .stream()
                            .collect(Collectors.groupingBy(t -> t.getCombo().getComboId()));

            // Batch fetch category translations for combos (reuse existing categoryTranslationsMap if available)
            Map<UUID, List<CategoryTranslation>> comboCategoryTranslationsMap = comboCategoryIds.isEmpty()
                    ? Collections.emptyMap()
                    : categoryTranslationRepository.findAllByCategoryIdIn(new ArrayList<>(comboCategoryIds))
                            .stream()
                            .collect(Collectors.groupingBy(t -> t.getCategory().getId()));

            // Map combo results
            List<ReportsOverviewResponse.ItemizedSalesItem> comboList = comboResults.stream()
                    .map(row -> {
                        UUID comboId = (UUID) row[0];
                        UUID categoryId = row[1] != null ? (UUID) row[1] : null;
                        Integer quantitySold = row[2] != null ? ((Number) row[2]).intValue() : 0;
                        BigDecimal unitPrice = row[3] != null 
                                ? BigDecimal.valueOf(((Number) row[3]).doubleValue())
                                        .setScale(2, RoundingMode.HALF_UP)
                                : BigDecimal.ZERO;
                        BigDecimal comboTotalSales = row[4] != null 
                                ? BigDecimal.valueOf(((Number) row[4]).doubleValue())
                                        .setScale(2, RoundingMode.HALF_UP)
                                : BigDecimal.ZERO;

                        // Get combo name from translations
                        String comboName = getComboName(comboId, comboTranslationsMap, locale);
                        // Get category name from translations
                        String categoryName = getCategoryName(categoryId, comboCategoryTranslationsMap, locale);

                        return ReportsOverviewResponse.ItemizedSalesItem.builder()
                                .itemCode(null)
                                .itemName(comboName)
                                .category(categoryName)
                                .quantitySold(quantitySold)
                                .unitPrice(unitPrice)
                                .totalSales(comboTotalSales)
                                .percentageOfTotalSales(0.0) // Will be calculated after combining
                                .build();
                    })
                    .collect(Collectors.toList());
            items.addAll(comboList);
        }

        // Remove inconsistent/non-sellable rows from report output.
        // These can occur when refund quantity and refund amount are out of sync in historical data.
        items = items.stream()
                .filter(item -> item.getQuantitySold() != null && item.getQuantitySold() > 0)
                .filter(item -> item.getTotalSales() != null && item.getTotalSales().compareTo(BigDecimal.ZERO) > 0)
                .collect(Collectors.toList());

        if (items.isEmpty()) {
            PaginationMetaData metaData = createPaginationMetaData(0, page, size);
            return ReportsOverviewResponse.ItemizedSalesReport.builder()
                    .items(Collections.emptyList())
                    .count(0L)
                    .total(0L)
                    .metaData(metaData)
                    .build();
        }

        // Calculate total sales for percentage calculation (after combining items and combos)
        BigDecimal totalSales = items.stream()
                .map(ReportsOverviewResponse.ItemizedSalesItem::getTotalSales)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // Update percentages for all items
        items.forEach(item -> {
            Double percentage = totalSales.compareTo(BigDecimal.ZERO) > 0 && item.getTotalSales().compareTo(BigDecimal.ZERO) > 0
                    ? item.getTotalSales().divide(totalSales, 4, RoundingMode.HALF_UP)
                            .multiply(BigDecimal.valueOf(100))
                            .doubleValue()
                    : 0.0;
            item.setPercentageOfTotalSales(percentage);
        });

        // Apply sorting
        if (sortBy != null && !sortBy.trim().isEmpty()) {
            String sortField = sortBy.trim().toLowerCase();
            Comparator<ReportsOverviewResponse.ItemizedSalesItem> comparator = switch (sortField) {
                case "itemname", "item_name" -> Comparator.comparing(
                        ReportsOverviewResponse.ItemizedSalesItem::getItemName,
                        Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER));
                case "itemcode", "item_code" -> Comparator.comparing(
                        ReportsOverviewResponse.ItemizedSalesItem::getItemCode,
                        Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER));
                case "category" -> Comparator.comparing(
                        ReportsOverviewResponse.ItemizedSalesItem::getCategory,
                        Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER));
                case "quantitysold", "quantity_sold" -> Comparator.comparing(
                        ReportsOverviewResponse.ItemizedSalesItem::getQuantitySold,
                        Comparator.nullsLast(Comparator.naturalOrder()));
                case "unitprice", "unit_price" -> Comparator.comparing(
                        ReportsOverviewResponse.ItemizedSalesItem::getUnitPrice,
                        Comparator.nullsLast(Comparator.naturalOrder()));
                case SORT_KEY_TOTALSALES, SORT_KEY_TOTAL_SALES -> Comparator.comparing(
                        ReportsOverviewResponse.ItemizedSalesItem::getTotalSales,
                        Comparator.nullsLast(Comparator.naturalOrder()));
                case "percentageoftotalsales", "percentage_of_total_sales" -> Comparator.comparing(
                        ReportsOverviewResponse.ItemizedSalesItem::getPercentageOfTotalSales,
                        Comparator.nullsLast(Comparator.naturalOrder()));
                default -> Comparator.comparing(
                        ReportsOverviewResponse.ItemizedSalesItem::getTotalSales,
                        Comparator.nullsLast(Comparator.naturalOrder())); // Default: sort by totalSales
            };

            if (sortDirection == Sort.Direction.DESC) {
                comparator = comparator.reversed();
            }

            items.sort(comparator);
        } else {
            // Default sorting: by totalSales descending
            items.sort(Comparator.comparing(
                    ReportsOverviewResponse.ItemizedSalesItem::getTotalSales,
                    Comparator.nullsLast(Comparator.reverseOrder())));
        }

        // Apply pagination (if page/size are null, return all results for export)
        List<ReportsOverviewResponse.ItemizedSalesItem> paginatedItems;
        PaginationMetaData metaData;
        if (page == null || size == null) {
            // No pagination - return all results
            paginatedItems = items;
            metaData = null;
        } else {
            int pageNumber = (page != null && page > 0) ? page - 1 : 0; // 0-based
            int pageSize = (size != null && size > 0) ? size : 10;
            
            int fromIndex = Math.min(pageNumber * pageSize, items.size());
            int toIndex = Math.min(fromIndex + pageSize, items.size());
            if (fromIndex >= items.size()) {
                paginatedItems = new ArrayList<>();
            } else {
                paginatedItems = items.subList(fromIndex, toIndex);
            }
            
            metaData = PaginationMetaData.builder()
                    .page(pageNumber + 1)
                    .size(pageSize)
                    .totalPages((int) Math.ceil((double) items.size() / pageSize))
                    .totalRecords((long) items.size())
                    .build();
        }
        
        return ReportsOverviewResponse.ItemizedSalesReport.builder()
                .items(paginatedItems)
                .count((long) paginatedItems.size())
                .total((long) items.size())
                .metaData(metaData)
                .build();
    }

    /**
     * Gets localized item name from translations map with fallback logic.
     * Tries exact locale match first, then falls back to default language or first available translation.
     *
     * @param itemId         the UUID of the item
     * @param translationsMap batch-loaded map of item translations by item ID
     * @param locale         locale code for selecting translation
     * @return localized item name or default fallback
     */
    private String getItemName(UUID itemId, Map<UUID, List<ItemTranslation>> translationsMap, String locale) {
        List<ItemTranslation> translations = translationsMap.getOrDefault(itemId, Collections.emptyList());
        if (translations.isEmpty()) {
            return DEFAULT_UNKNOWN_ITEM;
        }

        ItemTranslation exactMatch = translations.stream()
                .filter(t -> t.getLanguageCode() != null && t.getLanguageCode().equalsIgnoreCase(locale))
                .findFirst()
                .orElse(null);

        if (exactMatch != null) {
            return exactMatch.getName() != null ? exactMatch.getName() : DEFAULT_UNKNOWN_ITEM;
        }

        Optional<ItemTranslation> fallback = TranslationUtils.pickPreferredOrFromList(
                translations, locale, localizationProperties.getLanguages(),
                ItemTranslation::getLanguageCode);

        return fallback.map(t -> t.getName() != null ? t.getName() : DEFAULT_UNKNOWN_ITEM)
                .orElse(DEFAULT_UNKNOWN_ITEM);
    }

    /**
     * Gets localized category name from translations map with fallback logic.
     * Tries exact locale match first, then falls back to default language or first available translation.
     * Returns "Uncategorized" if categoryId is null.
     *
     * @param categoryId     the UUID of the category (null returns "Uncategorized")
     * @param translationsMap batch-loaded map of category translations by category ID
     * @param locale          locale code for selecting translation
     * @return localized category name or default fallback
     */
    private String getCategoryName(UUID categoryId, Map<UUID, List<CategoryTranslation>> translationsMap, String locale) {
        if (categoryId == null) {
            return "Uncategorized";
        }

        List<CategoryTranslation> translations = translationsMap.getOrDefault(categoryId, Collections.emptyList());
        if (translations.isEmpty()) {
            return DEFAULT_UNKNOWN_CATEGORY;
        }

        CategoryTranslation exactMatch = translations.stream()
                .filter(t -> t.getLanguageCode() != null && t.getLanguageCode().equalsIgnoreCase(locale))
                .findFirst()
                .orElse(null);

        if (exactMatch != null) {
            return exactMatch.getName() != null ? exactMatch.getName() : DEFAULT_UNKNOWN_CATEGORY;
        }

        Optional<CategoryTranslation> fallback = TranslationUtils.pickPreferredOrFromList(
                translations, locale, localizationProperties.getLanguages(),
                CategoryTranslation::getLanguageCode);

        return fallback.map(t -> t.getName() != null ? t.getName() : DEFAULT_UNKNOWN_CATEGORY)
                .orElse(DEFAULT_UNKNOWN_CATEGORY);
    }

    /**
     * Gets localized combo name from translations map with fallback logic.
     * Tries exact locale match first, then falls back to default language or first available translation.
     *
     * @param comboId        the UUID of the combo
     * @param translationsMap batch-loaded map of combo translations by combo ID
     * @param locale         locale code for selecting translation
     * @return localized combo name or default fallback
     */
    private String getComboName(UUID comboId, Map<UUID, List<ComboTranslation>> translationsMap, String locale) {
        List<ComboTranslation> translations = translationsMap.getOrDefault(comboId, Collections.emptyList());
        if (translations.isEmpty()) {
            return DEFAULT_UNKNOWN_COMBO;
        }

        return TranslationUtils.pickPreferredOrFromListNonBlank(
                        translations,
                        locale,
                        localizationProperties.getLanguages(),
                        ComboTranslation::getLanguageCode,
                        ComboTranslation::getName)
                .map(ComboTranslation::getName)
                .orElse(DEFAULT_UNKNOWN_COMBO);
    }

    /**
     * Generates table-wise sales report for a restaurant within a date range.
     * Aggregates sales by table, grouping by table code. Includes total orders, sales, tax, and service charges.
     * Supports pagination and sorting.
     *
     * @param restaurantId the UUID of the restaurant
     * @param startDate     start date/time of the period (inclusive)
     * @param endDate       end date/time of the period (inclusive)
     * @param page          page number for pagination (null returns all)
     * @param size          page size for pagination (null returns all)
     * @param sortBy        field to sort by
     * @param sortDirection sort direction
     * @return TableWiseSalesReport with paginated list of tables and sales metrics
     */
    private ReportsOverviewResponse.TableWiseSalesReport getTableWiseSalesReport(
            UUID restaurantId, LocalDateTime startDate, LocalDateTime endDate,
            Integer page, Integer size, String sortBy, Sort.Direction sortDirection) {

        List<Object[]> results = orderRepository.getTableWiseSalesReport(
                restaurantId, startDate, endDate);

        if (results.isEmpty()) {
            PaginationMetaData metaData = createPaginationMetaData(0, page, size);
            return ReportsOverviewResponse.TableWiseSalesReport.builder()
                    .tables(Collections.emptyList())
                    .count(0L)
                    .total(0L)
                    .metaData(metaData)
                    .build();
        }

        // Get all table IDs for batch lookup
        Set<UUID> tableIds = results.stream()
                .map(row -> (UUID) row[0])
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        // Batch fetch tables
        Map<UUID, RestaurantTable> tableMap = restaurantTableRepository.findAllById(tableIds)
                .stream()
                .collect(Collectors.toMap(RestaurantTable::getId, t -> t));

        // First, map results to items with table numbers
        List<ReportsOverviewResponse.TableWiseSalesItem> itemsWithTableNumbers = results.stream()
                .map(row -> {
                    UUID tableId = (UUID) row[0];
                    Long totalOrders = ((Number) row[1]).longValue();
                    BigDecimal totalSales = BigDecimal.valueOf(((Number) row[2]).doubleValue())
                            .setScale(2, RoundingMode.HALF_UP);
                    BigDecimal averageOrderValue = BigDecimal.valueOf(((Number) row[3]).doubleValue())
                            .setScale(2, RoundingMode.HALF_UP);
                    BigDecimal totalTax = BigDecimal.valueOf(((Number) row[4]).doubleValue())
                            .setScale(2, RoundingMode.HALF_UP);
                    BigDecimal totalServiceCharge = BigDecimal.valueOf(((Number) row[5]).doubleValue())
                            .setScale(2, RoundingMode.HALF_UP);

                    // Get table identifier - use unique tableCode as business key
                    String tableNo = "Unknown";
                    RestaurantTable table = tableMap.get(tableId);
                    if (table != null) {
                        if (table.getTableCode() != null && !table.getTableCode().isEmpty()) {
                            tableNo = table.getTableCode();
                        } else if (table.getTableOrder() != null) {
                            tableNo = String.valueOf(table.getTableOrder());
                        } else {
                            tableNo = "N/A";
                        }
                    }

                    return ReportsOverviewResponse.TableWiseSalesItem.builder()
                            .tableNo(tableNo)
                            .totalOrders(totalOrders)
                            .totalSales(totalSales)
                            .averageOrderValue(averageOrderValue)
                            .totalTax(totalTax)
                            .totalServiceCharge(totalServiceCharge)
                            .build();
                })
                .collect(Collectors.toList());

        // Group by table number and aggregate the values
        Map<String, ReportsOverviewResponse.TableWiseSalesItem> aggregatedByTableNo = new LinkedHashMap<>();
        
        for (ReportsOverviewResponse.TableWiseSalesItem item : itemsWithTableNumbers) {
            String tableNo = item.getTableNo();
            
            if (aggregatedByTableNo.containsKey(tableNo)) {
                // Aggregate existing entry
                ReportsOverviewResponse.TableWiseSalesItem existing = aggregatedByTableNo.get(tableNo);
                
                // Sum totals
                Long combinedOrders = existing.getTotalOrders() + item.getTotalOrders();
                BigDecimal combinedSales = existing.getTotalSales().add(item.getTotalSales());
                BigDecimal combinedTax = existing.getTotalTax().add(item.getTotalTax());
                BigDecimal combinedServiceCharge = existing.getTotalServiceCharge().add(item.getTotalServiceCharge());
                
                // Recalculate average order value
                BigDecimal newAverageOrderValue = combinedOrders > 0
                        ? combinedSales.divide(BigDecimal.valueOf(combinedOrders), 2, RoundingMode.HALF_UP)
                        : BigDecimal.ZERO;
                
                // Update aggregated entry
                aggregatedByTableNo.put(tableNo, ReportsOverviewResponse.TableWiseSalesItem.builder()
                        .tableNo(tableNo)
                        .totalOrders(combinedOrders)
                        .totalSales(combinedSales)
                        .averageOrderValue(newAverageOrderValue)
                        .totalTax(combinedTax)
                        .totalServiceCharge(combinedServiceCharge)
                        .build());
            } else {
                // First entry for this table number
                aggregatedByTableNo.put(tableNo, item);
            }
        }

        // Convert to list
        List<ReportsOverviewResponse.TableWiseSalesItem> aggregatedTables = new ArrayList<>(aggregatedByTableNo.values());
        
        // Apply sorting
        if (sortBy != null && !sortBy.trim().isEmpty()) {
            String sortField = sortBy.trim().toLowerCase();
            Comparator<ReportsOverviewResponse.TableWiseSalesItem> comparator = switch (sortField) {
                case "tableno", "table_no", "tablenumber", "table_number" -> Comparator.comparing(
                        ReportsOverviewResponse.TableWiseSalesItem::getTableNo,
                        Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER));
                case "totalorders", "total_orders" -> Comparator.comparing(
                        ReportsOverviewResponse.TableWiseSalesItem::getTotalOrders,
                        Comparator.nullsLast(Comparator.naturalOrder()));
                case SORT_KEY_TOTALSALES, SORT_KEY_TOTAL_SALES -> Comparator.comparing(
                        ReportsOverviewResponse.TableWiseSalesItem::getTotalSales,
                        Comparator.nullsLast(Comparator.naturalOrder()));
                case "averageordervalue", "average_order_value" -> Comparator.comparing(
                        ReportsOverviewResponse.TableWiseSalesItem::getAverageOrderValue,
                        Comparator.nullsLast(Comparator.naturalOrder()));
                case "totaltax", "total_tax" -> Comparator.comparing(
                        ReportsOverviewResponse.TableWiseSalesItem::getTotalTax,
                        Comparator.nullsLast(Comparator.naturalOrder()));
                case "totalservicecharge", "total_service_charge" -> Comparator.comparing(
                        ReportsOverviewResponse.TableWiseSalesItem::getTotalServiceCharge,
                        Comparator.nullsLast(Comparator.naturalOrder()));
                default -> Comparator.comparing(
                        ReportsOverviewResponse.TableWiseSalesItem::getTotalSales,
                        Comparator.nullsLast(Comparator.naturalOrder())); // Default: sort by totalSales
            };

            if (sortDirection == Sort.Direction.DESC) {
                comparator = comparator.reversed();
            }

            aggregatedTables.sort(comparator);
        } else {
            // Default sorting: by totalSales descending
            aggregatedTables.sort(Comparator.comparing(
                    ReportsOverviewResponse.TableWiseSalesItem::getTotalSales,
                    Comparator.nullsLast(Comparator.reverseOrder())));
        }

        // Apply pagination (if page/size are null, return all results for export)
        List<ReportsOverviewResponse.TableWiseSalesItem> paginatedTables;
        PaginationMetaData metaData;
        if (page == null || size == null) {
            // No pagination - return all results
            paginatedTables = aggregatedTables;
            metaData = null;
        } else {
            int pageNumber = (page != null && page > 0) ? page - 1 : 0; // 0-based
            int pageSize = (size != null && size > 0) ? size : 10;
            
            int fromIndex = Math.min(pageNumber * pageSize, aggregatedTables.size());
            int toIndex = Math.min(fromIndex + pageSize, aggregatedTables.size());
            if (fromIndex >= aggregatedTables.size()) {
                paginatedTables = new ArrayList<>();
            } else {
                paginatedTables = aggregatedTables.subList(fromIndex, toIndex);
            }
            
            metaData = PaginationMetaData.builder()
                    .page(pageNumber + 1)
                    .size(pageSize)
                    .totalPages((int) Math.ceil((double) aggregatedTables.size() / pageSize))
                    .totalRecords((long) aggregatedTables.size())
                    .build();
        }
        
        return ReportsOverviewResponse.TableWiseSalesReport.builder()
                .tables(paginatedTables)
                .count((long) paginatedTables.size())
                .total((long) aggregatedTables.size())
                .metaData(metaData)
                .build();
    }

    /**
     * Generates discounts and promotions report for a restaurant within a date range.
     * Combines discount and promotion items, calculates usage counts and total discount amounts.
     * Supports pagination and sorting.
     *
     * @param restaurantId the UUID of the restaurant
     * @param startDate    start date/time of the period (inclusive)
     * @param endDate      end date/time of the period (inclusive)
     * @param page         page number for pagination (null returns all)
     * @param size         page size for pagination (null returns all)
     * @param sortBy       field to sort by
     * @param sortDirection sort direction
     * @return DiscountsPromotionsReport with paginated list of discounts/promotions and usage metrics
     */
    private ReportsOverviewResponse.DiscountsPromotionsReport getDiscountsPromotionsReport(
            UUID restaurantId, LocalDateTime startDate, LocalDateTime endDate,
            Integer page, Integer size, String sortBy, Sort.Direction sortDirection) {

        logger.debug("Getting discounts promotions report - restaurantId: {}, startDate: {}, endDate: {}", 
                restaurantId, startDate, endDate);

        List<Object[]> results = orderRepository.getDiscountsPromotionsReport(
                restaurantId, startDate, endDate);

        logger.debug("Discounts promotions report query returned {} results", results.size());

        if (results.isEmpty()) {
            logger.debug("No discount results found for restaurant: {}", restaurantId);
            PaginationMetaData metaData = createPaginationMetaData(0, page, size);
            return ReportsOverviewResponse.DiscountsPromotionsReport.builder()
                    .discounts(Collections.emptyList())
                    .count(0L)
                    .total(0L)
                    .metaData(metaData)
                    .build();
        }

        // Map all results to discount items first (before sorting and pagination)
        List<ReportsOverviewResponse.DiscountPromotionItem> discounts = results.stream()
                .map(row -> {
                    String discountType = (String) row[0]; // Category: "Order", "Additional Discount", "Item", or "Category"
                    String discountCode = (String) row[1]; // Specific discount code (e.g., "SUMMER20", "VIP_OFFER")
                    String discountName = (String) row[2]; // Discount name from translation (fallback to discount code if not available)
                    Long numberOfTransactions = ((Number) row[3]).longValue();
                    BigDecimal totalDiscountApplied = BigDecimal.valueOf(((Number) row[4]).doubleValue())
                            .setScale(2, RoundingMode.HALF_UP);
                    BigDecimal totalRevenue = BigDecimal.valueOf(((Number) row[5]).doubleValue())
                            .setScale(2, RoundingMode.HALF_UP);
                    BigDecimal totalRevenueBeforeDiscount = BigDecimal.valueOf(((Number) row[6]).doubleValue())
                            .setScale(2, RoundingMode.HALF_UP);
                    // We will recompute discount efficiency in Java as:
                    // (Total Discount Applied / Total Revenue Before Discount) * 100 for each row
                    BigDecimal discountEfficiency = BigDecimal.ZERO;
                    String appliedTo = (String) row[8];

                    return ReportsOverviewResponse.DiscountPromotionItem.builder()
                            .discountType(discountType != null && !discountType.isEmpty() ? discountType : "Unknown Discount")
                            .discountCode(discountCode) // Preserve specific discount code for frontend display
                            .discountName(discountName != null && !discountName.isEmpty() ? discountName : discountCode) // Use discount name, fallback to code
                            .numberOfTransactions(numberOfTransactions)
                            .totalDiscountApplied(totalDiscountApplied)
                            .totalRevenue(totalRevenue)
                            .totalRevenueBeforeDiscount(totalRevenueBeforeDiscount)
                            .discountEfficiency(discountEfficiency)
                            .appliedTo(appliedTo != null ? appliedTo : "ORDER")
                            .build();
                })
                .collect(Collectors.toList());

        // Recalculate discount efficiency according to business standard:
        // Discount Efficiency = (Total Discount Applied / Total Revenue Before Discount) * 100
        // Formula: (discount / revenueBeforeDiscount) * 100
        discounts.forEach(item -> {
            BigDecimal rowDiscount = item.getTotalDiscountApplied() != null
                    ? item.getTotalDiscountApplied()
                    : BigDecimal.ZERO;
            BigDecimal revenueBeforeDiscount = item.getTotalRevenueBeforeDiscount() != null
                    ? item.getTotalRevenueBeforeDiscount()
                    : BigDecimal.ZERO;
            
            if (revenueBeforeDiscount.compareTo(BigDecimal.ZERO) > 0) {
                // Use higher precision for division, then round to 2 decimal places
                // Formula: (discount / revenueBeforeDiscount) * 100
                BigDecimal efficiency = rowDiscount
                        .divide(revenueBeforeDiscount, 10, RoundingMode.HALF_UP) // Use 10 decimal places for precision
                        .multiply(BigDecimal.valueOf(100))
                        .setScale(2, RoundingMode.HALF_UP); // Round final result to 2 decimal places
                item.setDiscountEfficiency(efficiency);
            } else {
                item.setDiscountEfficiency(BigDecimal.ZERO);
            }
        });

        // Apply sorting
        if (sortBy != null && !sortBy.trim().isEmpty()) {
            String sortField = sortBy.trim().toLowerCase();
            Comparator<ReportsOverviewResponse.DiscountPromotionItem> comparator = switch (sortField) {
                case "discounttype", "discount_type" -> Comparator.comparing(
                        ReportsOverviewResponse.DiscountPromotionItem::getDiscountType,
                        Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER));
                case "numberoftransactions", "number_of_transactions" -> Comparator.comparing(
                        ReportsOverviewResponse.DiscountPromotionItem::getNumberOfTransactions,
                        Comparator.nullsLast(Comparator.naturalOrder()));
                case "totaldiscountapplied", "total_discount_applied" -> Comparator.comparing(
                        ReportsOverviewResponse.DiscountPromotionItem::getTotalDiscountApplied,
                        Comparator.nullsLast(Comparator.naturalOrder()));
                case "totalrevenue", "total_revenue" -> Comparator.comparing(
                        ReportsOverviewResponse.DiscountPromotionItem::getTotalRevenue,
                        Comparator.nullsLast(Comparator.naturalOrder()));
                case "totalrevenuebeforediscount", "total_revenue_before_discount" -> Comparator.comparing(
                        ReportsOverviewResponse.DiscountPromotionItem::getTotalRevenueBeforeDiscount,
                        Comparator.nullsLast(Comparator.naturalOrder()));
                case "discountefficiency", "discount_efficiency" -> Comparator.comparing(
                        ReportsOverviewResponse.DiscountPromotionItem::getDiscountEfficiency,
                        Comparator.nullsLast(Comparator.naturalOrder()));
                case "appliedto", "applied_to" -> Comparator.comparing(
                        ReportsOverviewResponse.DiscountPromotionItem::getAppliedTo,
                        Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER));
                default -> Comparator.comparing(
                        ReportsOverviewResponse.DiscountPromotionItem::getTotalDiscountApplied,
                        Comparator.nullsLast(Comparator.naturalOrder())); // Default: sort by totalDiscountApplied
            };

            if (sortDirection == Sort.Direction.DESC) {
                comparator = comparator.reversed();
            }

            discounts.sort(comparator);
        } else {
            // Default sorting: by totalDiscountApplied descending
            discounts.sort(Comparator.comparing(
                    ReportsOverviewResponse.DiscountPromotionItem::getTotalDiscountApplied,
                    Comparator.nullsLast(Comparator.reverseOrder())));
        }

        // Apply pagination (if page/size are null, return all results for export)
        List<ReportsOverviewResponse.DiscountPromotionItem> paginatedDiscounts;
        PaginationMetaData metaData;
        if (page == null || size == null) {
            // No pagination - return all results
            paginatedDiscounts = discounts;
            metaData = null;
        } else {
            int pageNumber = (page != null && page > 0) ? page - 1 : 0; // 0-based
            int pageSize = (size != null && size > 0) ? size : 10;
            
            int fromIndex = Math.min(pageNumber * pageSize, discounts.size());
            int toIndex = Math.min(fromIndex + pageSize, discounts.size());
            if (fromIndex >= discounts.size()) {
                paginatedDiscounts = new ArrayList<>();
            } else {
                paginatedDiscounts = discounts.subList(fromIndex, toIndex);
            }
            
            metaData = PaginationMetaData.builder()
                    .page(pageNumber + 1)
                    .size(pageSize)
                    .totalPages((int) Math.ceil((double) discounts.size() / pageSize))
                    .totalRecords((long) discounts.size())
                    .build();
        }
        
        return ReportsOverviewResponse.DiscountsPromotionsReport.builder()
                .discounts(paginatedDiscounts)
                .count((long) paginatedDiscounts.size())
                .total((long) discounts.size())
                .metaData(metaData)
                .build();
    }

    /**
     * Generates sales by server (waiter) report for a restaurant within a date range.
     * Aggregates sales metrics by waiter including total orders, sales, average order value, and tables served.
     * Supports pagination and sorting.
     *
     * @param restaurantId the UUID of the restaurant
     * @param startDate     start date/time of the period (inclusive)
     * @param endDate       end date/time of the period (inclusive)
     * @param page          page number for pagination (null returns all)
     * @param size          page size for pagination (null returns all)
     * @param sortBy        field to sort by
     * @param sortDirection sort direction
     * @return SalesByServerReport with paginated list of servers and performance metrics
     */
    private PerformanceResponse.SalesByServerReport getSalesByServerReport(
            UUID restaurantId, LocalDateTime startDate, LocalDateTime endDate,
            Integer page, Integer size, String sortBy, Sort.Direction sortDirection) {

        List<Object[]> results = orderRepository.getWaiterPerformanceReport(
                restaurantId, startDate, endDate);

        if (results.isEmpty()) {
            PaginationMetaData metaData = createPaginationMetaData(0, page, size);
            return PerformanceResponse.SalesByServerReport.builder()
                    .servers(Collections.emptyList())
                    .count(0L)
                    .total(0L)
                    .metaData(metaData)
                    .build();
        }

        // Map all results to server items first (before sorting and pagination)
        List<PerformanceResponse.SalesByServerItem> servers = results.stream()
                .map(row -> {
                    String firstName = row[1] != null ? (String) row[1] : "";
                    String lastName = row[2] != null ? (String) row[2] : "";
                    String serverCode = row[3] != null ? (String) row[3] : "N/A";
                    Long totalOrders = ((Number) row[4]).longValue();
                    BigDecimal totalSales = BigDecimal.valueOf(((Number) row[5]).doubleValue())
                            .setScale(2, RoundingMode.HALF_UP);
                    BigDecimal averageOrderValue = BigDecimal.valueOf(((Number) row[6]).doubleValue())
                            .setScale(2, RoundingMode.HALF_UP);
                    Long totalTablesServed = ((Number) row[7]).longValue();

                    String serverName = (firstName + " " + lastName).trim();
                    if (serverName.isEmpty()) {
                        serverName = "Unknown Server";
                    }

                    return PerformanceResponse.SalesByServerItem.builder()
                            .serverName(serverName)
                            .serverCode(serverCode)
                            .totalOrders(totalOrders)
                            .totalSales(totalSales)
                            .averageOrderValue(averageOrderValue)
                            .totalTablesServed(totalTablesServed)
                            .build();
                })
                .collect(Collectors.toList());

        // Apply sorting
        if (sortBy != null && !sortBy.trim().isEmpty()) {
            String sortField = sortBy.trim().toLowerCase();
            Comparator<PerformanceResponse.SalesByServerItem> comparator = switch (sortField) {
                case "servername", "server_name" -> Comparator.comparing(
                        PerformanceResponse.SalesByServerItem::getServerName,
                        Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER));
                case "servercode", "server_code" -> Comparator.comparing(
                        PerformanceResponse.SalesByServerItem::getServerCode,
                        Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER));
                case "totalorders", "total_orders" -> Comparator.comparing(
                        PerformanceResponse.SalesByServerItem::getTotalOrders,
                        Comparator.nullsLast(Comparator.naturalOrder()));
                case SORT_KEY_TOTALSALES, SORT_KEY_TOTAL_SALES -> Comparator.comparing(
                        PerformanceResponse.SalesByServerItem::getTotalSales,
                        Comparator.nullsLast(Comparator.naturalOrder()));
                case "averageordervalue", "average_order_value" -> Comparator.comparing(
                        PerformanceResponse.SalesByServerItem::getAverageOrderValue,
                        Comparator.nullsLast(Comparator.naturalOrder()));
                case "totaltablesserved", "total_tables_served" -> Comparator.comparing(
                        PerformanceResponse.SalesByServerItem::getTotalTablesServed,
                        Comparator.nullsLast(Comparator.naturalOrder()));
                default -> Comparator.comparing(
                        PerformanceResponse.SalesByServerItem::getTotalSales,
                        Comparator.nullsLast(Comparator.naturalOrder())); // Default: sort by totalSales
            };

            if (sortDirection == Sort.Direction.DESC) {
                comparator = comparator.reversed();
            }

            servers.sort(comparator);
        } else {
            // Default sorting: by totalSales descending
            servers.sort(Comparator.comparing(
                    PerformanceResponse.SalesByServerItem::getTotalSales,
                    Comparator.nullsLast(Comparator.reverseOrder())));
        }

        // Apply pagination (if page/size are null, return all results for export)
        List<PerformanceResponse.SalesByServerItem> paginatedServers;
        PaginationMetaData metaData;
        if (page == null || size == null) {
            // No pagination - return all results
            paginatedServers = servers;
            metaData = null;
        } else {
            int pageNumber = (page != null && page > 0) ? page - 1 : 0; // 0-based
            int pageSize = (size != null && size > 0) ? size : 10;
            
            int fromIndex = Math.min(pageNumber * pageSize, servers.size());
            int toIndex = Math.min(fromIndex + pageSize, servers.size());
            if (fromIndex >= servers.size()) {
                paginatedServers = new ArrayList<>();
            } else {
                paginatedServers = servers.subList(fromIndex, toIndex);
            }
            
            metaData = PaginationMetaData.builder()
                    .page(pageNumber + 1)
                    .size(pageSize)
                    .totalPages((int) Math.ceil((double) servers.size() / pageSize))
                    .totalRecords((long) servers.size())
                    .build();
        }
        
        return PerformanceResponse.SalesByServerReport.builder()
                .servers(paginatedServers)
                .count((long) paginatedServers.size())
                .total((long) servers.size())
                .metaData(metaData)
                .build();
    }

    /**
     * Generates customer rating distribution report for a restaurant within a date range.
     * Groups ratings by rating value (1-5) and calculates counts and percentages.
     * Supports pagination and sorting.
     *
     * @param restaurantId the UUID of the restaurant
     * @param startDate     start date/time of the period (inclusive)
     * @param endDate       end date/time of the period (inclusive)
     * @param page          page number for pagination (null returns all)
     * @param size          page size for pagination (null returns all)
     * @param sortBy        field to sort by
     * @param sortDirection sort direction
     * @return CustomerRatingDistribution with paginated list of rating distribution items
     */
    private PerformanceResponse.CustomerRatingDistribution getCustomerRatingDistribution(
            UUID restaurantId, LocalDateTime startDate, LocalDateTime endDate,
            Integer page, Integer size, String sortBy, Sort.Direction sortDirection) {

        List<Object[]> results = ratingRepository.getCustomerRatingDistribution(
                restaurantId, startDate, endDate);

        if (results.isEmpty()) {
            return PerformanceResponse.CustomerRatingDistribution.builder()
                    .distribution(Collections.emptyList())
                    .build();
        }

        // Calculate total count for percentage calculation (before pagination)
        long totalCount = results.stream()
                .mapToLong(row -> ((Number) row[1]).longValue())
                .sum();

        // Map all results to distribution items first (before sorting and pagination)
        List<PerformanceResponse.RatingDistributionItem> distribution = results.stream()
                .map(row -> {
                    Integer rating = ((Number) row[0]).intValue();
                    Long count = ((Number) row[1]).longValue();
                    Double percentage = totalCount > 0
                            ? (count.doubleValue() / totalCount) * 100.0
                            : 0.0;

                    return PerformanceResponse.RatingDistributionItem.builder()
                            .rating(rating)
                            .count(count)
                            .percentage(percentage)
                            .build();
                })
                .collect(Collectors.toList());

        // Apply sorting
        if (sortBy != null && !sortBy.trim().isEmpty()) {
            String sortField = sortBy.trim().toLowerCase();
            Comparator<PerformanceResponse.RatingDistributionItem> comparator = switch (sortField) {
                case "rating" -> Comparator.comparing(
                        PerformanceResponse.RatingDistributionItem::getRating,
                        Comparator.nullsLast(Comparator.naturalOrder()));
                case "count" -> Comparator.comparing(
                        PerformanceResponse.RatingDistributionItem::getCount,
                        Comparator.nullsLast(Comparator.naturalOrder()));
                case "percentage" -> Comparator.comparing(
                        PerformanceResponse.RatingDistributionItem::getPercentage,
                        Comparator.nullsLast(Comparator.naturalOrder()));
                default -> Comparator.comparing(
                        PerformanceResponse.RatingDistributionItem::getRating,
                        Comparator.nullsLast(Comparator.reverseOrder())); // Default: sort by rating descending
            };

            if (sortDirection == Sort.Direction.DESC) {
                comparator = comparator.reversed();
            }

            distribution.sort(comparator);
        } else {
            // Default sorting: by rating descending
            distribution.sort(Comparator.comparing(
                    PerformanceResponse.RatingDistributionItem::getRating,
                    Comparator.nullsLast(Comparator.reverseOrder())));
        }

        // Apply pagination (if page/size are null, return all results for export)
        List<PerformanceResponse.RatingDistributionItem> paginatedDistribution;
        PaginationMetaData metaData;
        if (page == null || size == null) {
            // No pagination - return all results
            paginatedDistribution = distribution;
            metaData = null;
        } else {
            int pageNumber = (page != null && page > 0) ? page - 1 : 0; // 0-based
            int pageSize = (size != null && size > 0) ? size : 10;

            int fromIndex = Math.min(pageNumber * pageSize, distribution.size());
            int toIndex = Math.min(fromIndex + pageSize, distribution.size());
            if (fromIndex >= distribution.size()) {
                paginatedDistribution = new ArrayList<>();
            } else {
                paginatedDistribution = distribution.subList(fromIndex, toIndex);
            }
            
            metaData = PaginationMetaData.builder()
                    .page(pageNumber + 1)
                    .size(pageSize)
                    .totalPages((int) Math.ceil((double) distribution.size() / pageSize))
                    .totalRecords((long) distribution.size())
                    .build();
        }

        return PerformanceResponse.CustomerRatingDistribution.builder()
                .distribution(paginatedDistribution)
                .metaData(metaData)
                .build();
    }

    /**
     * Creates pagination metadata from total elements, page number, and page size.
     * Handles null or invalid values by providing defaults (page: 1, size: 10).
     *
     * @param totalElements total number of elements across all pages
     * @param page          optional page number (defaults to 1 if null or invalid)
     * @param size          optional page size (defaults to 10 if null or invalid)
     * @return {@link PaginationMetaData} with calculated pagination information
     */
    private PaginationMetaData createPaginationMetaData(long totalElements, Integer page, Integer size) {
        int pageNum = (page != null && page > 0) ? page : 1;
        int pageSize = (size != null && size > 0) ? size : 10;
        int totalPages = (int) Math.ceil((double) totalElements / pageSize);

        return PaginationMetaData.builder()
                .page(pageNum)
                .size(pageSize)
                .totalRecords(totalElements)
                .totalPages(totalPages)
                .build();
    }

    /**
     * Validate date parameters similar to dashboard service
     */
    private void validateDateParameters(String period, LocalDateTime startDate, LocalDateTime endDate, Locale locale) {
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);

        // Validate period parameter if provided
        if (period != null && !period.isEmpty()) {
            String upperPeriod = period.toUpperCase();
            if (!upperPeriod.equals(PERIOD_DAILY) &&
                !upperPeriod.equals(PERIOD_TODAY) &&
                !upperPeriod.equals(PERIOD_30_DAYS) &&
                !upperPeriod.equals(PERIOD_3_MONTHS) &&
                !upperPeriod.equals(PERIOD_6_MONTHS) &&
                !upperPeriod.equals("CUSTOM")) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        messageUtil.getMessage(msgReportsErrorInvalidPeriod, locale));
            }
        }

        // Validate custom date range
        if (startDate != null && endDate != null) {
            // Start date should not be after end date
            if (startDate.isAfter(endDate)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        messageUtil.getMessage("dashboard.error.startdate.after.enddate", locale));
            }

            // Dates should not be in the future
            // Allow start date to be today (even if it's in the future relative to current time)
            LocalDateTime endOfToday = now.toLocalDate().atTime(23, 59, 59, 999999999);
            if (startDate.isAfter(endOfToday)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        messageUtil.getMessage("dashboard.error.startdate.future", locale));
            }

            // Allow end date to be up to end of today (for scheduled reports that need full day data)
            if (endDate.isAfter(endOfToday)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        messageUtil.getMessage("dashboard.error.enddate.future", locale));
            }

            // Validate date range is not too large (e.g., more than 10 years)
            long daysBetween = java.time.temporal.ChronoUnit.DAYS.between(startDate, endDate);
            if (daysBetween > 3650) { // 10 years
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        messageUtil.getMessage("reports.error.daterange.exceeded", locale));
            }
        }

        // If period is CUSTOM, startDate and endDate must be provided
        if (period != null && period.equalsIgnoreCase("CUSTOM") && (startDate == null || endDate == null)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    messageUtil.getMessage("reports.error.custom.dates.required", locale));
        }
    }

    /**
     * Validate date parameters for timezone-aware datetimes (overview/export endpoints).
     * This respects the exact time portion sent by the frontend.
     */
    private void validateDateParameters(String period, OffsetDateTime startDate, OffsetDateTime endDate, Locale locale) {
        OffsetDateTime nowUtc = OffsetDateTime.now(ZoneOffset.UTC);

        // Validate period parameter if provided
        if (period != null && !period.isEmpty()) {
            String upperPeriod = period.toUpperCase();
            if (!upperPeriod.equals(PERIOD_DAILY) &&
                !upperPeriod.equals(PERIOD_TODAY) &&
                !upperPeriod.equals(PERIOD_30_DAYS) &&
                !upperPeriod.equals(PERIOD_3_MONTHS) &&
                !upperPeriod.equals(PERIOD_6_MONTHS) &&
                !upperPeriod.equals("CUSTOM")) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        messageUtil.getMessage(msgReportsErrorInvalidPeriod, locale));
            }
        }

        if (startDate != null && endDate != null) {
            if (startDate.isAfter(endDate)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        messageUtil.getMessage("dashboard.error.startdate.after.enddate", locale));
            }

            // Disallow future timestamps beyond end of today (UTC).
            OffsetDateTime endOfTodayUtc = nowUtc.toLocalDate()
                    .atTime(23, 59, 59, 999_999_999)
                    .atOffset(ZoneOffset.UTC);

            if (startDate.isAfter(endOfTodayUtc)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        messageUtil.getMessage("dashboard.error.startdate.future", locale));
            }
            if (endDate.isAfter(endOfTodayUtc)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        messageUtil.getMessage("dashboard.error.enddate.future", locale));
            }

            long daysBetween = java.time.temporal.ChronoUnit.DAYS.between(startDate, endDate);
            if (daysBetween > 3650) { // 10 years
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        messageUtil.getMessage("reports.error.daterange.exceeded", locale));
            }
        }

        if (period != null && period.equalsIgnoreCase("CUSTOM") && (startDate == null || endDate == null)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    messageUtil.getMessage("reports.error.custom.dates.required", locale));
        }
    }

    /**
     * Exports reports overview to Excel format with multiple sheets.
     * Creates sheets for: Summary & Payment Types, Itemized Sales, Table-wise Sales, and Discounts & Promotions.
     * Validates access, calculates date range, and writes Excel workbook to HTTP response.
     *
     * @param period          optional period filter (TODAY, 30_DAYS, 3_MONTHS, 6_MONTHS, or CUSTOM)
     * @param startDate       optional start date for custom date range
     * @param endDate         optional end date for custom date range
     * @param restaurantId    optional filter by specific restaurant ID
     * @param restaurantGroupId optional filter by restaurant group ID
     * @param userId          user ID for access control
     * @param userRole        user role for access control
     * @param locale          locale code for localized responses
     * @param response        HTTP servlet response to write Excel file to
     * @throws IOException if Excel file writing fails
     * @throws ResponseStatusException if validation fails, access denied, or restaurant not found
     */
    @Override
    @Transactional(readOnly = true)
    public void exportReportsToExcel(
            String period,
            OffsetDateTime startDate,
            OffsetDateTime endDate,
            UUID restaurantId,
            UUID restaurantGroupId,
            String userId,
            String userRole,
            String locale,
            HttpServletResponse response) throws IOException {

        Locale localeObj = Locale.forLanguageTag(locale != null ? locale : "en");
        LocaleContextHolder.setLocale(localeObj);

        try {
            long t0 = System.nanoTime();
            long tLast = t0;

            // Validate role-based access
            validateReportAccess(userRole, localeObj);
            if (reportExportTimingLogEnabled) {
                logger.info("Reports export timing: accessValidatedMs={}",
                        java.util.concurrent.TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - tLast));
                tLast = System.nanoTime();
            }

            // Get restaurant IDs based on role and filters.
            // Skip restaurant existence validation here; we load the restaurant for display immediately after.
            List<UUID> restaurantIds = getRestaurantIdsForReport(restaurantId, restaurantGroupId, userId, userRole, localeObj, false);
            if (reportExportTimingLogEnabled) {
                logger.info("Reports export timing: resolveRestaurantIdsMs={} count={}",
                        java.util.concurrent.TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - tLast),
                        restaurantIds != null ? restaurantIds.size() : 0);
                tLast = System.nanoTime();
            }

            if (restaurantIds.isEmpty()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        messageUtil.getMessage(msgReportsErrorRestaurantIdRequired, localeObj));
            }

            // For export, get restaurant for display purposes
            // If single restaurant, use it; if multiple (restaurant group), use first one but we'll show group info
            UUID primaryRestaurantId = restaurantIds.get(0);
            Restaurant restaurant = restaurantRepository.findByIdAndIsDeletedFalse(primaryRestaurantId)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                            messageUtil.getMessage(msgRestaurantGetErrorNotFound, localeObj)));
            if (reportExportTimingLogEnabled) {
                logger.info("Reports export timing: loadRestaurantMs={}",
                        java.util.concurrent.TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - tLast));
                tLast = System.nanoTime();
            }
            
            // If we have multiple restaurants, it means it's a restaurant group
            // Get restaurant group for display purposes
            RestaurantGroup restaurantGroup = null;
            if (restaurantIds.size() > 1 && restaurantGroupId != null) {
                restaurantGroup = restaurantGroupRepository.findById(restaurantGroupId).orElse(null);
            }

            // Validate date parameters
            validateDateParameters(period, startDate, endDate, localeObj);
            if (reportExportTimingLogEnabled) {
                logger.info("Reports export timing: validateDatesMs={}",
                        java.util.concurrent.TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - tLast));
                tLast = System.nanoTime();
            }

            // Calculate date range
            LocalDateTime startDateTime;
            LocalDateTime endDateTime = LocalDateTime.now(ZoneOffset.UTC).withHour(23).withMinute(59).withSecond(59).withNano(999999999);

            if (startDate != null && endDate != null) {
                startDateTime = startDate.withOffsetSameInstant(ZoneOffset.UTC).toLocalDateTime();
                endDateTime = endDate.withOffsetSameInstant(ZoneOffset.UTC).toLocalDateTime();
            } else if (period != null && !period.isEmpty()) {
                switch (period.toUpperCase()) {
                    case PERIOD_DAILY:
                    case PERIOD_TODAY:
                        // Single day - today
                        startDateTime = LocalDateTime.now(ZoneOffset.UTC).withHour(0).withMinute(0).withSecond(0).withNano(0);
                        endDateTime = LocalDateTime.now(ZoneOffset.UTC).withHour(23).withMinute(59).withSecond(59).withNano(999999999);
                        break;
                    case PERIOD_30_DAYS:
                        startDateTime = LocalDateTime.now(ZoneOffset.UTC).minusDays(30).withHour(0).withMinute(0).withSecond(0).withNano(0);
                        break;
                    case PERIOD_3_MONTHS:
                        startDateTime = LocalDateTime.now(ZoneOffset.UTC).minusMonths(3).withHour(0).withMinute(0).withSecond(0).withNano(0);
                        break;
                    case PERIOD_6_MONTHS:
                        startDateTime = LocalDateTime.now(ZoneOffset.UTC).minusMonths(6).withHour(0).withMinute(0).withSecond(0).withNano(0);
                        break;
                    default:
                        throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                                messageUtil.getMessage(msgReportsErrorInvalidPeriod, localeObj));
                }
            } else {
                startDateTime = SENTINEL_DATE;
                endDateTime = SENTINEL_DATE;
            }

            // Get all report data (without pagination for export)
            // Handle single restaurant vs multiple restaurants (restaurant group)
            ReportsOverviewResponse.DailySalesSummary dailySalesSummary;
            List<ReportsOverviewResponse.PaymentTypeBreakdown> paymentTypesBreakdown;
            ReportsOverviewResponse.ItemizedSalesReport itemizedReport;
            ReportsOverviewResponse.TableWiseSalesReport tableWiseReport;
            ReportsOverviewResponse.DiscountsPromotionsReport discountsReport;

            if (restaurantIds.size() == 1) {
                // Single restaurant - use existing methods
                UUID singleRestaurantId = restaurantIds.get(0);
                dailySalesSummary = getDailySalesSummary(singleRestaurantId, startDateTime, endDateTime);
                if (reportExportTimingLogEnabled) {
                    logger.info("Reports export timing: dailySalesSummaryMs={}",
                            java.util.concurrent.TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - tLast));
                    tLast = System.nanoTime();
                }
                paymentTypesBreakdown = getPaymentTypesBreakdown(singleRestaurantId, startDateTime, endDateTime);
                if (reportExportTimingLogEnabled) {
                    logger.info("Reports export timing: paymentTypesBreakdownMs={}",
                            java.util.concurrent.TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - tLast));
                    tLast = System.nanoTime();
                }
                itemizedReport = getItemizedSalesReport(singleRestaurantId, startDateTime, endDateTime, null, null, null, null, locale);
                if (reportExportTimingLogEnabled) {
                    logger.info("Reports export timing: itemizedReportMs={}",
                            java.util.concurrent.TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - tLast));
                    tLast = System.nanoTime();
                }
                tableWiseReport = getTableWiseSalesReport(singleRestaurantId, startDateTime, endDateTime, null, null, null, null);
                if (reportExportTimingLogEnabled) {
                    logger.info("Reports export timing: tableWiseReportMs={}",
                            java.util.concurrent.TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - tLast));
                    tLast = System.nanoTime();
                }
                discountsReport = getDiscountsPromotionsReport(singleRestaurantId, startDateTime, endDateTime, null, null, null, null);
                if (reportExportTimingLogEnabled) {
                    logger.info("Reports export timing: discountsReportMs={}",
                            java.util.concurrent.TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - tLast));
                    tLast = System.nanoTime();
                }
            } else {
                // Multiple restaurants - aggregate data
                dailySalesSummary = getDailySalesSummaryForRestaurants(restaurantIds, startDateTime, endDateTime);
                paymentTypesBreakdown = getPaymentTypesBreakdownForRestaurants(restaurantIds, startDateTime, endDateTime);
                itemizedReport = getItemizedSalesReportForRestaurants(restaurantIds, startDateTime, endDateTime, null, null, null, null, locale);
                tableWiseReport = getTableWiseSalesReportForRestaurants(restaurantIds, startDateTime, endDateTime, null, null, null, null);
                discountsReport = getDiscountsPromotionsReportForRestaurants(restaurantIds, startDateTime, endDateTime, null, null, null, null);
                if (reportExportTimingLogEnabled) {
                    logger.info("Reports export timing: multiRestaurantDataFetchMs={}",
                            java.util.concurrent.TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - tLast));
                    tLast = System.nanoTime();
                }
            }

            setExcelExportResponseHeaders(response, msgReportsExportFilenameOverview, localeObj);

            // Create workbook
            try (Workbook workbook = new XSSFWorkbook()) {
                if (reportExportTimingLogEnabled) {
                    logger.info("Reports export timing: workbookCreateMs={}",
                            java.util.concurrent.TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - tLast));
                    tLast = System.nanoTime();
                }
                // Create styles
                CellStyle headerStyle = createHeaderStyle(workbook);
                CellStyle titleStyle = createTitleStyle(workbook);
                String currencySymbol = getChainCurrencySymbol();
                CellStyle monetaryStyle = createMonetaryNumberStyle(workbook, currencySymbol);
                CellStyle percentageDecimalStyle = createNumberStyle(workbook);
                if (reportExportTimingLogEnabled) {
                    logger.info("Reports export timing: stylesCreateMs={}",
                            java.util.concurrent.TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - tLast));
                    tLast = System.nanoTime();
                }

                // Sheet 1: Daily Sales Summary + Payment Types Breakdown
                createSummarySheet(workbook, dailySalesSummary, paymentTypesBreakdown, 
                        restaurant, restaurantGroup, startDateTime, endDateTime, headerStyle, titleStyle,
                        currencySymbol, monetaryStyle, percentageDecimalStyle);
                if (reportExportTimingLogEnabled) {
                    logger.info("Reports export timing: sheetSummaryMs={}",
                            java.util.concurrent.TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - tLast));
                    tLast = System.nanoTime();
                }

                // Sheet 2: Itemized Sales Report
                createItemizedSalesSheet(workbook, itemizedReport, restaurant, restaurantGroup, 
                        startDateTime, endDateTime, headerStyle, titleStyle,
                        currencySymbol, monetaryStyle, percentageDecimalStyle);
                if (reportExportTimingLogEnabled) {
                    logger.info("Reports export timing: sheetItemizedMs={}",
                            java.util.concurrent.TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - tLast));
                    tLast = System.nanoTime();
                }

                // Sheet 3: Table-wise Sales Report
                createTableWiseSalesSheet(workbook, tableWiseReport, restaurant, restaurantGroup, 
                        startDateTime, endDateTime, headerStyle, titleStyle, currencySymbol, monetaryStyle);
                if (reportExportTimingLogEnabled) {
                    logger.info("Reports export timing: sheetTableWiseMs={}",
                            java.util.concurrent.TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - tLast));
                    tLast = System.nanoTime();
                }

                // Sheet 4: Discounts & Promotions Report
                createDiscountsPromotionsSheet(workbook, discountsReport, restaurant, restaurantGroup, 
                        startDateTime, endDateTime, headerStyle, titleStyle,
                        currencySymbol, monetaryStyle, percentageDecimalStyle);
                if (reportExportTimingLogEnabled) {
                    logger.info("Reports export timing: sheetDiscountsMs={}",
                            java.util.concurrent.TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - tLast));
                    tLast = System.nanoTime();
                }

                // Write workbook to response
                workbook.write(response.getOutputStream());
                if (reportExportTimingLogEnabled) {
                    logger.info("Reports export timing: workbookWriteMs={} totalMs={}",
                            java.util.concurrent.TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - tLast),
                            java.util.concurrent.TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - t0));
                }
            }

            logger.info("Reports exported to Excel successfully for restaurant: {}", primaryRestaurantId);

        } catch (ResponseStatusException e) {
            throw e;
        } catch (Exception e) {
            logger.error("Error exporting reports to Excel", e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    messageUtil.getMessage(msgReportsExportError, localeObj));
        }
    }

    /**
     * Exports reports overview to CSV format.
     * Validates access, calculates date range, and writes CSV file to HTTP response.
     * Includes all report sections with proper CSV formatting and escaping.
     *
     * @param period          optional period filter (TODAY, 30_DAYS, 3_MONTHS, 6_MONTHS, or CUSTOM)
     * @param startDate       optional start date for custom date range
     * @param endDate         optional end date for custom date range
     * @param restaurantId    optional filter by specific restaurant ID
     * @param restaurantGroupId optional filter by restaurant group ID
     * @param userId          user ID for access control
     * @param userRole        user role for access control
     * @param locale          locale code for localized responses
     * @param response        HTTP servlet response to write CSV file to
     * @throws IOException if CSV file writing fails
     * @throws ResponseStatusException if validation fails, access denied, or restaurant not found
     */
    @Override
    public void exportReportsToCsv(
            String period,
            OffsetDateTime startDate,
            OffsetDateTime endDate,
            UUID restaurantId,
            UUID restaurantGroupId,
            String userId,
            String userRole,
            String locale,
            HttpServletResponse response) throws IOException {
        Locale localeObj = Locale.forLanguageTag(locale != null ? locale : "en");
        LocaleContextHolder.setLocale(localeObj);

        try {
            // Get restaurant/group info
            Restaurant restaurant = null;
            RestaurantGroup restaurantGroup = null;
            List<UUID> restaurantIds = new ArrayList<>();

            if (restaurantId != null) {
                restaurant = restaurantRepository.findById(restaurantId)
                        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                                messageUtil.getMessage("restaurant.not.found", localeObj)));
                restaurantIds.add(restaurantId);
            } else if (restaurantGroupId != null) {
                restaurantGroup = restaurantGroupRepository.findById(restaurantGroupId)
                        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                                messageUtil.getMessage("restaurant.group.not.found", localeObj)));
                // Get restaurants from repository instead of entity
                List<Restaurant> restaurants = restaurantRepository.findByRestaurantGroupIdAndIsDeletedFalse(restaurantGroupId);
                restaurantIds = restaurants.stream()
                        .map(Restaurant::getId)
                        .collect(Collectors.toList());
            }

            // Validate date parameters
            validateDateParameters(period, startDate, endDate, localeObj);

            // Calculate date range
            LocalDateTime startDateTime;
            LocalDateTime endDateTime = LocalDateTime.now(ZoneOffset.UTC).withHour(23).withMinute(59).withSecond(59).withNano(999999999);

            if (startDate != null && endDate != null) {
                startDateTime = startDate.withOffsetSameInstant(ZoneOffset.UTC).toLocalDateTime();
                endDateTime = endDate.withOffsetSameInstant(ZoneOffset.UTC).toLocalDateTime();
            } else if (period != null && !period.isEmpty()) {
                switch (period.toUpperCase()) {
                    case PERIOD_DAILY:
                    case PERIOD_TODAY:
                        // Single day - today
                        startDateTime = LocalDateTime.now(ZoneOffset.UTC).withHour(0).withMinute(0).withSecond(0).withNano(0);
                        endDateTime = LocalDateTime.now(ZoneOffset.UTC).withHour(23).withMinute(59).withSecond(59).withNano(999999999);
                        break;
                    case PERIOD_30_DAYS:
                        startDateTime = LocalDateTime.now(ZoneOffset.UTC).minusDays(30).withHour(0).withMinute(0).withSecond(0).withNano(0);
                        break;
                    case PERIOD_3_MONTHS:
                        startDateTime = LocalDateTime.now(ZoneOffset.UTC).minusMonths(3).withHour(0).withMinute(0).withSecond(0).withNano(0);
                        break;
                    case PERIOD_6_MONTHS:
                        startDateTime = LocalDateTime.now(ZoneOffset.UTC).minusMonths(6).withHour(0).withMinute(0).withSecond(0).withNano(0);
                        break;
                    default:
                        throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                                messageUtil.getMessage(msgReportsErrorInvalidPeriod, localeObj));
                }
            } else {
                startDateTime = SENTINEL_DATE;
                endDateTime = SENTINEL_DATE;
            }

            // Get report data
            ReportsOverviewResponse.DailySalesSummary dailySalesSummary;
            List<ReportsOverviewResponse.PaymentTypeBreakdown> paymentTypesBreakdown;

            if (restaurantIds.size() == 1) {
                dailySalesSummary = getDailySalesSummary(restaurantIds.get(0), startDateTime, endDateTime);
                paymentTypesBreakdown = getPaymentTypesBreakdown(restaurantIds.get(0), startDateTime, endDateTime);
            } else {
                dailySalesSummary = getDailySalesSummaryForRestaurants(restaurantIds, startDateTime, endDateTime);
                paymentTypesBreakdown = getPaymentTypesBreakdownForRestaurants(restaurantIds, startDateTime, endDateTime);
            }

            String currencySymbol = restaurantChainConfigProperties != null
                    && restaurantChainConfigProperties.getChain() != null
                    ? restaurantChainConfigProperties.getChain().getCurrency()
                    : null;

            setCsvExportResponseHeaders(response, msgReportsExportFilenameDailySummary, localeObj);
            response.setContentType("text/csv; charset=UTF-8");
            response.setCharacterEncoding("UTF-8");

            // Write CSV content
            try (java.io.PrintWriter writer = response.getWriter()) {
                // Write BOM for Excel compatibility
                writer.write('\ufeff');
                
                // Write header
                writer.println("Daily Sales Summary Report");
                writer.println();
                
                // Restaurant info
                if (restaurant != null) {
                    writer.println(messageUtil.getMessage("reports.export.label.restaurant") + "," + (restaurant.getTranslations() != null && !restaurant.getTranslations().isEmpty()
                            ? restaurant.getTranslations().get(0).getName() : "N/A"));
                } else if (restaurantGroup != null) {
                    writer.println(messageUtil.getMessage("reports.export.label.restaurantGroup") + "," + (restaurantGroup.getTranslations() != null && !restaurantGroup.getTranslations().isEmpty()
                            ? restaurantGroup.getTranslations().get(0).getName() : "N/A"));
                }
                writer.println(messageUtil.getMessage("reports.export.label.dateRange") + "," + (startDateTime != SENTINEL_DATE && endDateTime != SENTINEL_DATE
                        ? startDateTime.format(DATE_FORMAT) + messageUtil.getMessage("reports.export.value.toSeparator") + endDateTime.format(DATE_FORMAT)
                        : messageUtil.getMessage("reports.export.value.allTime")));
                writer.println(messageUtil.getMessage("csv.export.date") + "," + LocalDateTime.now(ZoneOffset.UTC).format(DATETIME_FORMAT));
                writer.println();
                
                // Daily Sales Summary
                writer.println(messageUtil.getMessage("reports.export.section.dailySalesSummary"));
                writer.println(messageUtil.getMessage("csv.dashboard.header.metric") + "," + messageUtil.getMessage("csv.dashboard.header.value"));
                writer.println(messageUtil.getMessage("csv.dashboard.metric.total.sales") + "," + CurrencyFormatter.formatCsvMonetaryString(
                        dailySalesSummary.getTotalSales(), currencySymbol));
                writer.println(messageUtil.getMessage("csv.dashboard.metric.total.orders") + "," + dailySalesSummary.getTotalOrders());
                writer.println(messageUtil.getMessage("reports.export.header.totalTablesServed") + "," + dailySalesSummary.getTotalTablesServed());
                writer.println(messageUtil.getMessage("reports.export.header.averageOrderValue") + "," + CurrencyFormatter.formatCsvMonetaryString(
                        dailySalesSummary.getAvgOrderValue(), currencySymbol));
                writer.println();
                
                // Payment Types Breakdown
                writer.println(messageUtil.getMessage("reports.export.section.paymentTypesBreakdown"));
                writer.println(
                        messageUtil.getMessage("reports.export.header.paymentMethod") + "," +
                        messageUtil.getMessage("csv.dashboard.metric.total.sales") + "," +
                        messageUtil.getMessage("reports.export.header.percentage"));
                for (ReportsOverviewResponse.PaymentTypeBreakdown payment : paymentTypesBreakdown) {
                    writer.println(String.format("%s,%s,%s",
                            escapeCsvField(payment.getPaymentMethod() != null ? payment.getPaymentMethod() : "N/A"),
                            CurrencyFormatter.formatCsvMonetaryString(payment.getTotalSales(), currencySymbol),
                            CurrencyFormatter.formatCsvPercentString(payment.getPercentage())));
                }
            }

            logger.info("Successfully exported daily summary report to CSV for restaurantId: {}, restaurantGroupId: {}", 
                    restaurantId, restaurantGroupId);
        } catch (Exception e) {
            logger.error("Error exporting reports to CSV", e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    messageUtil.getMessage(msgReportsExportError, localeObj));
        }
    }

    private String escapeCsvField(String field) {
        if (field == null) {
            return "";
        }
        // If field contains comma, quote, or newline, wrap in quotes and escape quotes
        if (field.contains(",") || field.contains("\"") || field.contains("\n")) {
            return "\"" + field.replace("\"", "\"\"") + "\"";
        }
        return field;
    }

    /**
     * Exports payment and financials report to CSV format.
     * Currently not implemented - throws NOT_IMPLEMENTED exception.
     *
     * @param period          optional period filter
     * @param startDate       optional start date for custom date range
     * @param endDate         optional end date for custom date range
     * @param restaurantId    optional filter by specific restaurant ID
     * @param restaurantGroupId optional filter by restaurant group ID
     * @param userId          user ID for access control
     * @param userRole        user role for access control
     * @param locale          locale code for localized responses
     * @param response        HTTP servlet response to write CSV file to
     * @throws IOException if CSV file writing fails
     * @throws ResponseStatusException NOT_IMPLEMENTED (method not yet implemented)
     */
    @Override
    public void exportPaymentAndFinancialsToCsv(
            String period,
            LocalDateTime startDate,
            LocalDateTime endDate,
            UUID restaurantId,
            UUID restaurantGroupId,
            String userId,
            String userRole,
            String locale,
            HttpServletResponse response) throws IOException {
        // CSV export implementation - uses same data as Excel but formats as CSV
        Locale localeObj = Locale.forLanguageTag(locale != null ? locale : "en");
        LocaleContextHolder.setLocale(localeObj);
        throw new ResponseStatusException(HttpStatus.NOT_IMPLEMENTED,
                messageUtil.getMessage("reports.csv.export.not.implemented", localeObj));
    }

    /**
     * Exports performance report to CSV format.
     * Currently not implemented - throws NOT_IMPLEMENTED exception.
     *
     * @param period          optional period filter
     * @param startDate       optional start date for custom date range
     * @param endDate         optional end date for custom date range
     * @param restaurantId    optional filter by specific restaurant ID
     * @param restaurantGroupId optional filter by restaurant group ID
     * @param userId          user ID for access control
     * @param userRole        user role for access control
     * @param locale          locale code for localized responses
     * @param response        HTTP servlet response to write CSV file to
     * @throws IOException if CSV file writing fails
     * @throws ResponseStatusException NOT_IMPLEMENTED (method not yet implemented)
     */
    @Override
    public void exportPerformanceToCsv(
            String period,
            LocalDateTime startDate,
            LocalDateTime endDate,
            UUID restaurantId,
            UUID restaurantGroupId,
            String userId,
            String userRole,
            String locale,
            HttpServletResponse response) throws IOException {
        // CSV export implementation - uses same data as Excel but formats as CSV
        Locale localeObj = Locale.forLanguageTag(locale != null ? locale : "en");
        LocaleContextHolder.setLocale(localeObj);
        throw new ResponseStatusException(HttpStatus.NOT_IMPLEMENTED,
                messageUtil.getMessage("reports.csv.export.not.implemented", localeObj));
    }

    /**
     * Creates a cell style for Excel header rows.
     * Applies bold font, grey background, borders, and center alignment.
     *
     * @param workbook the Excel workbook to create style in
     * @return CellStyle configured for headers
     */
    private CellStyle createHeaderStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        Font font = workbook.createFont();
        font.setBold(true);
        font.setFontHeightInPoints((short) 11);
        style.setFont(font);
        style.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
        style.setAlignment(HorizontalAlignment.CENTER);
        return style;
    }

    private CellStyle createTitleStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        Font font = workbook.createFont();
        font.setBold(true);
        font.setFontHeightInPoints((short) 14);
        style.setFont(font);
        return style;
    }

    private CellStyle createNumberStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        DataFormat format = workbook.createDataFormat();
        style.setDataFormat(format.getFormat("#,##0.00"));
        return style;
    }

    private String getChainCurrencySymbol() {
        if (restaurantChainConfigProperties != null && restaurantChainConfigProperties.getChain() != null) {
            return restaurantChainConfigProperties.getChain().getCurrency();
        }
        return null;
    }

    private CellStyle createMonetaryNumberStyle(Workbook workbook, String currencySymbol) {
        CellStyle style = workbook.createCellStyle();
        DataFormat format = workbook.createDataFormat();
        style.setDataFormat(format.getFormat(CurrencyFormatter.getMonetaryExcelDataFormatPattern(currencySymbol)));
        return style;
    }

    /**
     * Creates Excel sheet for daily sales summary and payment types breakdown.
     * Includes restaurant info, date range, summary metrics, and payment method breakdown.
     *
     * @param workbook            the Excel workbook to add sheet to
     * @param dailySalesSummary   daily sales summary data
     * @param paymentTypesBreakdown list of payment type breakdowns
     * @param restaurant          restaurant entity (for display)
     * @param restaurantGroup     restaurant group entity (for display, may be null)
     * @param startDateTime       report start date/time
     * @param endDateTime         report end date/time
     * @param headerStyle               cell style for headers
     * @param titleStyle                cell style for titles
     * @param currencySymbol            chain currency for {@link CurrencyFormatter}
     * @param monetaryStyle             cell style for money columns (yen: no decimals)
     * @param percentageDecimalStyle    cell style for percentage columns (two decimals)
     */
    private void createSummarySheet(Workbook workbook, 
                                   ReportsOverviewResponse.DailySalesSummary dailySalesSummary,
                                   List<ReportsOverviewResponse.PaymentTypeBreakdown> paymentTypesBreakdown,
                                   Restaurant restaurant,
                                   RestaurantGroup restaurantGroup,
                                   LocalDateTime startDateTime,
                                   LocalDateTime endDateTime,
                                   CellStyle headerStyle,
                                   CellStyle titleStyle,
                                   String currencySymbol,
                                   CellStyle monetaryStyle,
                                   CellStyle percentageDecimalStyle) {
        Sheet sheet = workbook.createSheet(messageUtil.getMessage("reports.export.sheet.summaryPaymentTypes"));

        int rowNum = 0;

        // Title
        Row titleRow = sheet.createRow(rowNum++);
        Cell titleCell = titleRow.createCell(0);
        titleCell.setCellValue(messageUtil.getMessage("reports.export.title"));
        titleCell.setCellStyle(titleStyle);

        // Restaurant info
        rowNum = addReportInfo(sheet, rowNum, restaurant, restaurantGroup, startDateTime, endDateTime);

        // Daily Sales Summary Section
        rowNum++;
        Row summaryHeaderRow = sheet.createRow(rowNum++);
        Cell summaryHeaderCell = summaryHeaderRow.createCell(0);
        summaryHeaderCell.setCellValue(messageUtil.getMessage("reports.export.section.dailySalesSummary"));
        summaryHeaderCell.setCellStyle(titleStyle);

        Row summaryLabelsRow = sheet.createRow(rowNum++);
        Row summaryValuesRow = sheet.createRow(rowNum++);

        String[] summaryLabels = {
                messageUtil.getMessage("csv.dashboard.metric.total.sales"),
                messageUtil.getMessage("csv.dashboard.metric.total.orders"),
                messageUtil.getMessage("reports.export.header.totalTablesServed"),
                messageUtil.getMessage("reports.export.header.averageOrderValue")
        };
        Object[] summaryValues = {
            dailySalesSummary.getTotalSales(),
            dailySalesSummary.getTotalOrders(),
            dailySalesSummary.getTotalTablesServed(),
            dailySalesSummary.getAvgOrderValue()
        };

        for (int i = 0; i < summaryLabels.length; i++) {
            Cell labelCell = summaryLabelsRow.createCell(i);
            labelCell.setCellValue(summaryLabels[i]);
            labelCell.setCellStyle(headerStyle);

            Cell valueCell = summaryValuesRow.createCell(i);
            if (summaryValues[i] instanceof BigDecimal) {
                valueCell.setCellValue(CurrencyFormatter.formatAmount((BigDecimal) summaryValues[i], currencySymbol).doubleValue());
                valueCell.setCellStyle(monetaryStyle);
            } else if (summaryValues[i] instanceof Long) {
                valueCell.setCellValue(((Long) summaryValues[i]).doubleValue());
            }
        }

        // Payment Types Breakdown Section
        rowNum += 2;
        Row paymentHeaderRow = sheet.createRow(rowNum++);
        Cell paymentHeaderCell = paymentHeaderRow.createCell(0);
        paymentHeaderCell.setCellValue(messageUtil.getMessage("reports.export.section.paymentTypesBreakdown"));
        paymentHeaderCell.setCellStyle(titleStyle);

        Row paymentHeaderDataRow = sheet.createRow(rowNum++);
        String[] paymentHeaders = {
                messageUtil.getMessage("reports.export.header.paymentMethod"),
                messageUtil.getMessage("csv.dashboard.metric.total.sales"),
                messageUtil.getMessage("reports.export.header.percentage")
        };
        for (int i = 0; i < paymentHeaders.length; i++) {
            Cell cell = paymentHeaderDataRow.createCell(i);
            cell.setCellValue(paymentHeaders[i]);
            cell.setCellStyle(headerStyle);
        }

        for (ReportsOverviewResponse.PaymentTypeBreakdown payment : paymentTypesBreakdown) {
            Row row = sheet.createRow(rowNum++);
            row.createCell(0).setCellValue(payment.getPaymentMethod() != null ? payment.getPaymentMethod() : "N/A");
            Cell salesCell = row.createCell(1);
            if (payment.getTotalSales() != null) {
                salesCell.setCellValue(CurrencyFormatter.formatAmount(payment.getTotalSales(), currencySymbol).doubleValue());
                salesCell.setCellStyle(monetaryStyle);
            }
            Cell percentageCell = row.createCell(2);
            if (payment.getPercentage() != null) {
                percentageCell.setCellValue(payment.getPercentage());
                percentageCell.setCellStyle(percentageDecimalStyle);
            }
        }

        optimizeSheetColumns(sheet, 3, 24);
    }

    /**
     * Creates Excel sheet for itemized sales report.
     * Includes restaurant info, date range, and detailed item/combo sales with quantities and prices.
     *
     * @param workbook        the Excel workbook to add sheet to
     * @param itemizedReport  itemized sales report data
     * @param restaurant     restaurant entity (for display)
     * @param restaurantGroup restaurant group entity (for display, may be null)
     * @param startDateTime  report start date/time
     * @param endDateTime    report end date/time
     * @param headerStyle                cell style for headers
     * @param titleStyle                 cell style for titles
     * @param currencySymbol             chain currency for {@link CurrencyFormatter}
     * @param monetaryStyle              cell style for money columns
     * @param percentageDecimalStyle     cell style for percentage column
     */
    private void createItemizedSalesSheet(Workbook workbook,
                                         ReportsOverviewResponse.ItemizedSalesReport itemizedReport,
                                         Restaurant restaurant,
                                         RestaurantGroup restaurantGroup,
                                         LocalDateTime startDateTime,
                                         LocalDateTime endDateTime,
                                         CellStyle headerStyle,
                                         CellStyle titleStyle,
                                         String currencySymbol,
                                         CellStyle monetaryStyle,
                                         CellStyle percentageDecimalStyle) {
        Sheet sheet = workbook.createSheet(messageUtil.getMessage("reports.export.sheet.itemizedSales"));

        int rowNum = 0;
        rowNum = addReportInfo(sheet, rowNum, restaurant, restaurantGroup, startDateTime, endDateTime);

        rowNum++;
        Row headerRow = sheet.createRow(rowNum++);
        String[] headers = {
                messageUtil.getMessage("csv.dashboard.header.item.name"),
                messageUtil.getMessage("csv.dashboard.header.item.code"),
                messageUtil.getMessage("csv.dashboard.header.category"),
                messageUtil.getMessage("reports.export.header.quantitySold"),
                messageUtil.getMessage("reports.export.header.unitPrice"),
                messageUtil.getMessage("csv.dashboard.metric.total.sales"),
                messageUtil.getMessage("reports.export.header.percentageOfTotalSales")
        };
        for (int i = 0; i < headers.length; i++) {
            Cell cell = headerRow.createCell(i);
            cell.setCellValue(headers[i]);
            cell.setCellStyle(headerStyle);
        }

        for (ReportsOverviewResponse.ItemizedSalesItem item : itemizedReport.getItems()) {
            Row row = sheet.createRow(rowNum++);
            row.createCell(0).setCellValue(item.getItemName() != null ? item.getItemName() : "N/A");
            row.createCell(1).setCellValue(item.getItemCode() != null ? item.getItemCode() : "");
            row.createCell(2).setCellValue(item.getCategory() != null ? item.getCategory() : "N/A");
            row.createCell(3).setCellValue(item.getQuantitySold() != null ? item.getQuantitySold() : 0);
            
            Cell unitPriceCell = row.createCell(4);
            if (item.getUnitPrice() != null) {
                unitPriceCell.setCellValue(CurrencyFormatter.formatAmount(item.getUnitPrice(), currencySymbol).doubleValue());
                unitPriceCell.setCellStyle(monetaryStyle);
            }
            
            Cell totalSalesCell = row.createCell(5);
            if (item.getTotalSales() != null) {
                totalSalesCell.setCellValue(CurrencyFormatter.formatAmount(item.getTotalSales(), currencySymbol).doubleValue());
                totalSalesCell.setCellStyle(monetaryStyle);
            }
            
            Cell percentageCell = row.createCell(6);
            if (item.getPercentageOfTotalSales() != null) {
                percentageCell.setCellValue(item.getPercentageOfTotalSales());
                percentageCell.setCellStyle(percentageDecimalStyle);
            }
        }

        optimizeSheetColumns(sheet, headers.length, 24);
    }

    /**
     * Creates an Excel sheet for table-wise sales report with detailed sales data per table.
     * Includes report header information, table sales summary, and auto-sizes columns.
     *
     * @param workbook        the Excel workbook to add the sheet to
     * @param tableWiseReport  the table-wise sales report data
     * @param restaurant      the restaurant entity (optional, for header info)
     * @param restaurantGroup the restaurant group entity (optional, for header info)
     * @param startDateTime   report start date and time
     * @param endDateTime     report end date and time
     * @param headerStyle      cell style for header rows
     * @param titleStyle       cell style for title rows
     * @param currencySymbol   chain currency for {@link CurrencyFormatter}
     * @param monetaryStyle    cell style for money columns
     */
    private void createTableWiseSalesSheet(Workbook workbook,
                                           ReportsOverviewResponse.TableWiseSalesReport tableWiseReport,
                                           Restaurant restaurant,
                                           RestaurantGroup restaurantGroup,
                                           LocalDateTime startDateTime,
                                           LocalDateTime endDateTime,
                                           CellStyle headerStyle,
                                           CellStyle titleStyle,
                                           String currencySymbol,
                                           CellStyle monetaryStyle) {
        Sheet sheet = workbook.createSheet(messageUtil.getMessage("reports.export.sheet.tableWiseSales"));

        int rowNum = 0;
        rowNum = addReportInfo(sheet, rowNum, restaurant, restaurantGroup, startDateTime, endDateTime);

        rowNum++;
        Row headerRow = sheet.createRow(rowNum++);
        String[] headers = {
                messageUtil.getMessage("reports.export.header.tableNo"),
                messageUtil.getMessage("csv.dashboard.metric.total.orders"),
                messageUtil.getMessage("csv.dashboard.metric.total.sales"),
                messageUtil.getMessage("reports.export.header.averageOrderValue"),
                messageUtil.getMessage("reports.export.header.totalTax"),
                messageUtil.getMessage("reports.export.header.totalServiceCharge")
        };
        for (int i = 0; i < headers.length; i++) {
            Cell cell = headerRow.createCell(i);
            cell.setCellValue(headers[i]);
            cell.setCellStyle(headerStyle);
        }

        for (ReportsOverviewResponse.TableWiseSalesItem table : tableWiseReport.getTables()) {
            Row row = sheet.createRow(rowNum++);
            row.createCell(0).setCellValue(table.getTableNo() != null ? table.getTableNo() : "N/A");
            row.createCell(1).setCellValue(table.getTotalOrders() != null ? table.getTotalOrders() : 0);
            
            Cell totalSalesCell = row.createCell(2);
            if (table.getTotalSales() != null) {
                totalSalesCell.setCellValue(CurrencyFormatter.formatAmount(table.getTotalSales(), currencySymbol).doubleValue());
                totalSalesCell.setCellStyle(monetaryStyle);
            }
            
            Cell avgOrderValueCell = row.createCell(3);
            if (table.getAverageOrderValue() != null) {
                avgOrderValueCell.setCellValue(CurrencyFormatter.formatAmount(table.getAverageOrderValue(), currencySymbol).doubleValue());
                avgOrderValueCell.setCellStyle(monetaryStyle);
            }
            
            Cell totalTaxCell = row.createCell(4);
            if (table.getTotalTax() != null) {
                totalTaxCell.setCellValue(CurrencyFormatter.formatAmount(table.getTotalTax(), currencySymbol).doubleValue());
                totalTaxCell.setCellStyle(monetaryStyle);
            }
            
            Cell totalServiceChargeCell = row.createCell(5);
            if (table.getTotalServiceCharge() != null) {
                totalServiceChargeCell.setCellValue(CurrencyFormatter.formatAmount(table.getTotalServiceCharge(), currencySymbol).doubleValue());
                totalServiceChargeCell.setCellStyle(monetaryStyle);
            }
        }

        optimizeSheetColumns(sheet, headers.length, 24);
    }

    /**
     * Creates an Excel sheet for discounts and promotions report with detailed discount data.
     * Includes report header information, discount breakdown, and auto-sizes columns.
     *
     * @param workbook        the Excel workbook to add the sheet to
     * @param discountsReport the discounts and promotions report data
     * @param restaurant      the restaurant entity (optional, for header info)
     * @param restaurantGroup the restaurant group entity (optional, for header info)
     * @param startDateTime   report start date and time
     * @param endDateTime     report end date and time
     * @param headerStyle                cell style for header rows
     * @param titleStyle                 cell style for title rows
     * @param currencySymbol             chain currency for {@link CurrencyFormatter}
     * @param monetaryStyle              cell style for money columns
     * @param percentageDecimalStyle     cell style for discount efficiency (percentage)
     */
    private void createDiscountsPromotionsSheet(Workbook workbook,
                                                ReportsOverviewResponse.DiscountsPromotionsReport discountsReport,
                                                Restaurant restaurant,
                                                RestaurantGroup restaurantGroup,
                                                LocalDateTime startDateTime,
                                                LocalDateTime endDateTime,
                                                CellStyle headerStyle,
                                                CellStyle titleStyle,
                                                String currencySymbol,
                                                CellStyle monetaryStyle,
                                                CellStyle percentageDecimalStyle) {
        Sheet sheet = workbook.createSheet(messageUtil.getMessage("reports.export.sheet.discountsPromotions"));

        int rowNum = 0;
        rowNum = addReportInfo(sheet, rowNum, restaurant, restaurantGroup, startDateTime, endDateTime);

        rowNum++;
        Row headerRow = sheet.createRow(rowNum++);
        String[] headers = {
                messageUtil.getMessage("csv.dashboard.header.discount.type"),
                messageUtil.getMessage("reports.export.header.discountCode"),
                messageUtil.getMessage("reports.export.header.discountName"),
                messageUtil.getMessage("reports.export.header.numberOfTransactions"),
                messageUtil.getMessage("reports.export.header.totalDiscountApplied"),
                messageUtil.getMessage("reports.export.header.totalRevenue"),
                messageUtil.getMessage("reports.export.header.totalRevenueBeforeDiscount"),
                messageUtil.getMessage("reports.export.header.discountEfficiency"),
                messageUtil.getMessage("reports.export.header.appliedTo")
        };
        for (int i = 0; i < headers.length; i++) {
            Cell cell = headerRow.createCell(i);
            cell.setCellValue(headers[i]);
            cell.setCellStyle(headerStyle);
        }

        for (ReportsOverviewResponse.DiscountPromotionItem discount : discountsReport.getDiscounts()) {
            Row row = sheet.createRow(rowNum++);
            row.createCell(0).setCellValue(discount.getDiscountType() != null ? discount.getDiscountType() : "N/A");
            row.createCell(1).setCellValue(discount.getDiscountCode() != null ? discount.getDiscountCode() : "N/A");
            
            // Extract nested ternary to if-else for better readability
            String discountNameValue;
            if (discount.getDiscountName() != null) {
                discountNameValue = discount.getDiscountName();
            } else if (discount.getDiscountCode() != null) {
                discountNameValue = discount.getDiscountCode();
            } else {
                discountNameValue = "N/A";
            }
            row.createCell(2).setCellValue(discountNameValue);
            row.createCell(3).setCellValue(discount.getNumberOfTransactions() != null ? discount.getNumberOfTransactions() : 0);
            
            Cell discountAppliedCell = row.createCell(4);
            if (discount.getTotalDiscountApplied() != null) {
                discountAppliedCell.setCellValue(CurrencyFormatter.formatAmount(discount.getTotalDiscountApplied(), currencySymbol).doubleValue());
                discountAppliedCell.setCellStyle(monetaryStyle);
            }
            
            Cell totalRevenueCell = row.createCell(5);
            if (discount.getTotalRevenue() != null) {
                totalRevenueCell.setCellValue(CurrencyFormatter.formatAmount(discount.getTotalRevenue(), currencySymbol).doubleValue());
                totalRevenueCell.setCellStyle(monetaryStyle);
            }
            
            Cell revenueBeforeDiscountCell = row.createCell(6);
            if (discount.getTotalRevenueBeforeDiscount() != null) {
                revenueBeforeDiscountCell.setCellValue(CurrencyFormatter.formatAmount(discount.getTotalRevenueBeforeDiscount(), currencySymbol).doubleValue());
                revenueBeforeDiscountCell.setCellStyle(monetaryStyle);
            }
            
            Cell efficiencyCell = row.createCell(7);
            if (discount.getDiscountEfficiency() != null) {
                efficiencyCell.setCellValue(discount.getDiscountEfficiency().doubleValue());
                efficiencyCell.setCellStyle(percentageDecimalStyle);
            }
            
            row.createCell(8).setCellValue(discount.getAppliedTo() != null ? discount.getAppliedTo() : "N/A");
        }

        optimizeSheetColumns(sheet, headers.length, 24);
    }

    /**
     * Optimizes sheet columns for export performance.
     * Auto-sizing can be enabled via property, but fixed widths are faster for large datasets.
     */
    private void optimizeSheetColumns(Sheet sheet, int columnCount, int defaultWidthChars) {
        if (reportExportAutoSizeColumns) {
            for (int i = 0; i < columnCount; i++) {
                sheet.autoSizeColumn(i);
            }
            return;
        }

        int widthUnits = Math.max(defaultWidthChars, 1) * 256;
        for (int i = 0; i < columnCount; i++) {
            sheet.setColumnWidth(i, widthUnits);
        }
    }

    /**
     * Adds report information header to an Excel sheet (overload without restaurant group).
     * Delegates to the main method with null restaurant group.
     *
     * @param sheet        the Excel sheet to add info to
     * @param startRow     starting row number
     * @param restaurant   restaurant entity (for display)
     * @param startDateTime report start date/time
     * @param endDateTime   report end date/time
     * @return next available row number after adding info
     */
    private int addReportInfo(Sheet sheet, int startRow, Restaurant restaurant, LocalDateTime startDateTime, LocalDateTime endDateTime) {
        return addReportInfo(sheet, startRow, restaurant, null, startDateTime, endDateTime);
    }

    /**
     * Adds report information header to an Excel sheet.
     * Includes restaurant/restaurant group name, date range, and export date.
     *
     * @param sheet          the Excel sheet to add info to
     * @param startRow       starting row number
     * @param restaurant     restaurant entity (for display)
     * @param restaurantGroup restaurant group entity (for display, may be null)
     * @param startDateTime  report start date/time
     * @param endDateTime    report end date/time
     * @return next available row number after adding info
     */
    private int addReportInfo(Sheet sheet, int startRow, Restaurant restaurant, RestaurantGroup restaurantGroup, LocalDateTime startDateTime, LocalDateTime endDateTime) {
        int rowNum = startRow;
        
        Row restaurantRow = sheet.createRow(rowNum++);
        String localeLanguage = org.springframework.context.i18n.LocaleContextHolder.getLocale() != null
                ? org.springframework.context.i18n.LocaleContextHolder.getLocale().getLanguage()
                : null;

        if (restaurantGroup != null) {
            // Show restaurant group name
            restaurantRow.createCell(0).setCellValue(messageUtil.getMessage("reports.export.label.restaurantGroup"));
            String groupName = (restaurantGroup.getTranslations() == null || restaurantGroup.getTranslations().isEmpty())
                    ? null
                    : restaurantGroup.getTranslations().stream()
                            .filter(t -> t.getName() != null && !t.getName().isBlank())
                            .filter(t -> localeLanguage == null || (t.getLanguageCode() != null && t.getLanguageCode().equals(localeLanguage)))
                            .map(com.gulfnet.shared_library.entity.RestaurantGroupTranslation::getName)
                            .findFirst()
                            .orElseGet(() -> restaurantGroup.getTranslations().stream()
                                    .filter(t -> t.getName() != null && !t.getName().isBlank())
                                    .map(com.gulfnet.shared_library.entity.RestaurantGroupTranslation::getName)
                                    .findFirst()
                                    .orElse(null));
            if (groupName == null) {
                groupName = restaurantGroup.getRestaurantGroupCode() != null ? restaurantGroup.getRestaurantGroupCode() : "N/A";
            }
            restaurantRow.createCell(1).setCellValue(groupName);
        } else {
            // Show single restaurant
            restaurantRow.createCell(0).setCellValue(messageUtil.getMessage("reports.export.label.restaurant"));
            String restaurantName = (restaurant == null || restaurant.getTranslations() == null || restaurant.getTranslations().isEmpty())
                    ? null
                    : restaurant.getTranslations().stream()
                            .filter(t -> t.getName() != null && !t.getName().isBlank())
                            .filter(t -> localeLanguage == null || (t.getLanguageCode() != null && t.getLanguageCode().equals(localeLanguage)))
                            .map(com.gulfnet.shared_library.entity.RestaurantTranslation::getName)
                            .findFirst()
                            .orElseGet(() -> restaurant.getTranslations().stream()
                                    .filter(t -> t.getName() != null && !t.getName().isBlank())
                                    .map(com.gulfnet.shared_library.entity.RestaurantTranslation::getName)
                                    .findFirst()
                                    .orElse(null));
            if (restaurantName == null) {
                restaurantName = (restaurant != null && restaurant.getRestaurantCode() != null) ? restaurant.getRestaurantCode() : "N/A";
            }
            restaurantRow.createCell(1).setCellValue(restaurantName);
        }

        Row dateRangeRow = sheet.createRow(rowNum++);
        dateRangeRow.createCell(0).setCellValue(messageUtil.getMessage("reports.export.label.dateRange"));
        String dateRange;
        if (startDateTime.equals(SENTINEL_DATE) && endDateTime.equals(SENTINEL_DATE)) {
            dateRange = messageUtil.getMessage("reports.export.value.allTime");
        } else {
            String toSeparator = messageUtil.getMessage("reports.export.value.toSeparator");
            dateRange = startDateTime.format(DATE_FORMAT) + toSeparator + endDateTime.format(DATE_FORMAT);
        }
        dateRangeRow.createCell(1).setCellValue(dateRange);

        Row exportDateRow = sheet.createRow(rowNum++);
        exportDateRow.createCell(0).setCellValue(messageUtil.getMessage("csv.export.date"));
        exportDateRow.createCell(1).setCellValue(LocalDateTime.now(ZoneOffset.UTC).format(DATETIME_FORMAT));

        return rowNum;
    }

    /**
     * Creates Excel sheet for payment reconciliation and cash drawer reconciliation reports.
     * Includes restaurant info, date range, payment method breakdown, and cash drawer summary.
     *
     * @param workbook         the Excel workbook to add sheet to
     * @param paymentReport    payment reconciliation report data
     * @param cashDrawerReport cash drawer reconciliation report data
     * @param restaurant       restaurant entity (for display)
     * @param startDateTime    report start date/time
     * @param endDateTime      report end date/time
     * @param headerStyle      cell style for headers
     * @param titleStyle       cell style for titles
     * @param monetaryStyle    cell style for monetary values (chain currency, e.g. yen without decimals)
     * @param currencySymbol   chain currency symbol for {@link CurrencyFormatter}
     */
    private void createPaymentAndCashDrawerSheet(Workbook workbook,
                                                PaymentAndFinancialsResponse.PaymentReconciliationReport paymentReport,
                                                PaymentAndFinancialsResponse.CashDrawerReconciliationReport cashDrawerReport,
                                                Restaurant restaurant,
                                                LocalDateTime startDateTime,
                                                LocalDateTime endDateTime,
                                                CellStyle headerStyle,
                                                CellStyle titleStyle,
                                                CellStyle monetaryStyle,
                                                String currencySymbol) {
        Sheet sheet = workbook.createSheet(messageUtil.getMessage("reports.export.sheet.paymentCashDrawer"));

        int rowNum = 0;
        rowNum = addReportInfo(sheet, rowNum, restaurant, startDateTime, endDateTime);

        // Payment Reconciliation Section
        rowNum++;
        Row paymentHeaderRow = sheet.createRow(rowNum++);
        Cell paymentHeaderCell = paymentHeaderRow.createCell(0);
        paymentHeaderCell.setCellValue(messageUtil.getMessage("reports.export.section.paymentReconciliationReport"));
        paymentHeaderCell.setCellStyle(titleStyle);

        Row paymentDataHeaderRow = sheet.createRow(rowNum++);
        String[] paymentHeaders = {
                messageUtil.getMessage("reports.export.header.paymentMethod"),
                messageUtil.getMessage("reports.export.header.totalTransactions"),
                messageUtil.getMessage("reports.export.header.totalAmount")
        };
        for (int i = 0; i < paymentHeaders.length; i++) {
            Cell cell = paymentDataHeaderRow.createCell(i);
            cell.setCellValue(paymentHeaders[i]);
            cell.setCellStyle(headerStyle);
        }

        for (PaymentAndFinancialsResponse.PaymentReconciliationItem payment : paymentReport.getPayments()) {
            Row row = sheet.createRow(rowNum++);
            row.createCell(0).setCellValue(payment.getPaymentMethod() != null ? payment.getPaymentMethod() : "N/A");
            row.createCell(1).setCellValue(payment.getTotalTransactions() != null ? payment.getTotalTransactions() : 0);
            Cell amountCell = row.createCell(2);
            if (payment.getTotalAmount() != null) {
                amountCell.setCellValue(CurrencyFormatter.formatAmount(payment.getTotalAmount(), currencySymbol).doubleValue());
                amountCell.setCellStyle(monetaryStyle);
            }
        }

        // Cash Drawer Reconciliation Section
        rowNum += 2;
        Row cashDrawerHeaderRow = sheet.createRow(rowNum++);
        Cell cashDrawerHeaderCell = cashDrawerHeaderRow.createCell(0);
        cashDrawerHeaderCell.setCellValue(messageUtil.getMessage("reports.export.section.cashDrawerReconciliationReport"));
        cashDrawerHeaderCell.setCellStyle(titleStyle);

        Row cashDrawerDataHeaderRow = sheet.createRow(rowNum++);
        String[] cashDrawerHeaders = {
                messageUtil.getMessage("reports.export.header.metric"),
                messageUtil.getMessage("reports.export.header.value")
        };
        for (int i = 0; i < cashDrawerHeaders.length; i++) {
            Cell cell = cashDrawerDataHeaderRow.createCell(i);
            cell.setCellValue(cashDrawerHeaders[i]);
            cell.setCellStyle(headerStyle);
        }

        String[] metrics = {
                messageUtil.getMessage("reports.export.cashDrawer.metric.openingBalance"),
                messageUtil.getMessage("reports.export.cashDrawer.metric.totalCashSalesNet"),
                messageUtil.getMessage("reports.export.cashDrawer.metric.cashSalesCustomerGaveGrossIn"),
                messageUtil.getMessage("reports.export.cashDrawer.metric.cashSalesChangeReturnedGrossOut"),
                messageUtil.getMessage("reports.export.cashDrawer.metric.totalCashRefundsPaidNet"),
                messageUtil.getMessage("reports.export.cashDrawer.metric.cashRefundsPaidOutGrossOut"),
                messageUtil.getMessage("reports.export.cashDrawer.metric.cashRefundsChangeCollectedGrossIn"),
                messageUtil.getMessage("reports.export.cashDrawer.metric.cashWithdrawal"),
                messageUtil.getMessage("reports.export.cashDrawer.metric.expectedCashBalance"),
                messageUtil.getMessage("reports.export.cashDrawer.metric.actualCashBalance"),
                messageUtil.getMessage("reports.export.cashDrawer.metric.discrepancyAmount"),
                messageUtil.getMessage("reports.export.cashDrawer.metric.status")
        };
        BigDecimal[] values = {
                cashDrawerReport.getOpeningBalance(),
                cashDrawerReport.getTotalCashSales(),
                cashDrawerReport.getCashSalesGrossIn(),
                cashDrawerReport.getCashSalesGrossOut(),
                cashDrawerReport.getTotalCashRefundsPaid(),
                cashDrawerReport.getCashRefundsGrossOut(),
                cashDrawerReport.getCashRefundsGrossIn(),
                cashDrawerReport.getCashWithdrawal(),
                cashDrawerReport.getExpectedCashBalance(),
                cashDrawerReport.getActualCashBalance(),
                cashDrawerReport.getDiscrepancyAmount(),
                null // Status is a string
        };
        String statusValue = cashDrawerReport.getStatus() != null ? cashDrawerReport.getStatus() : STATUS_BALANCED;

        for (int i = 0; i < metrics.length; i++) {
            Row row = sheet.createRow(rowNum++);
            row.createCell(0).setCellValue(metrics[i]);
            Cell valueCell = row.createCell(1);
            if (i == metrics.length - 1) {
                // Status is a string
                valueCell.setCellValue(statusValue);
            } else if (values[i] != null) {
                valueCell.setCellValue(CurrencyFormatter.formatAmount(values[i], currencySymbol).doubleValue());
                valueCell.setCellStyle(monetaryStyle);
            } else {
                valueCell.setCellValue(CurrencyFormatter.formatAmount(BigDecimal.ZERO, currencySymbol).doubleValue());
                valueCell.setCellStyle(monetaryStyle);
            }
        }

        // Auto-size columns
        for (int i = 0; i < 3; i++) {
            sheet.autoSizeColumn(i);
        }
    }

    /**
     * Creates Excel sheet for cancellation report.
     * Includes restaurant info, date range, and detailed cancellation transactions.
     *
     * @param workbook          the Excel workbook to add sheet to
     * @param cancellationReport cancellation report data
     * @param restaurant        restaurant entity (for display)
     * @param startDateTime     report start date/time
     * @param endDateTime       report end date/time
     * @param headerStyle       cell style for headers
     * @param titleStyle        cell style for titles
     * @param monetaryStyle     cell style for monetary values (chain currency)
     * @param currencySymbol    chain currency symbol for {@link CurrencyFormatter}
     */
    private void createCancellationSheet(Workbook workbook,
                                        PaymentAndFinancialsResponse.CancellationReport cancellationReport,
                                        Restaurant restaurant,
                                        LocalDateTime startDateTime,
                                        LocalDateTime endDateTime,
                                        CellStyle headerStyle,
                                        CellStyle titleStyle,
                                        CellStyle monetaryStyle,
                                        String currencySymbol) {
        Sheet sheet = workbook.createSheet(messageUtil.getMessage("reports.export.sheet.cancellationReport"));

        int rowNum = 0;
        rowNum = addReportInfo(sheet, rowNum, restaurant, startDateTime, endDateTime);

        rowNum++;
        Row headerRow = sheet.createRow(rowNum++);
        String[] headers = {
                messageUtil.getMessage("reports.export.header.id"),
                messageUtil.getMessage("reports.export.header.type"),
                messageUtil.getMessage("reports.export.header.dateTime"),
                messageUtil.getMessage("reports.export.header.amount"),
                messageUtil.getMessage("reports.export.header.paymentMethod"),
                messageUtil.getMessage("reports.export.header.reason"),
                messageUtil.getMessage("reports.export.header.initiatedBy"),
                messageUtil.getMessage("reports.export.header.cancelledBy")
        };
        for (int i = 0; i < headers.length; i++) {
            Cell cell = headerRow.createCell(i);
            cell.setCellValue(headers[i]);
            cell.setCellStyle(headerStyle);
        }

        for (PaymentAndFinancialsResponse.CancellationItem cancellation : cancellationReport.getCancellations()) {
            Row row = sheet.createRow(rowNum++);
            row.createCell(0).setCellValue(cancellation.getId() != null ? cancellation.getId() : "N/A");
            row.createCell(1).setCellValue(cancellation.getType() != null ? cancellation.getType() : "N/A");
            row.createCell(2).setCellValue(cancellation.getDateTime() != null ?
                    cancellation.getDateTime().format(DATETIME_FORMAT) : "N/A");
            Cell amountCell = row.createCell(3);
            if (cancellation.getAmount() != null) {
                amountCell.setCellValue(CurrencyFormatter.formatAmount(cancellation.getAmount(), currencySymbol).doubleValue());
                amountCell.setCellStyle(monetaryStyle);
            }
            row.createCell(4).setCellValue(cancellation.getPaymentMethod() != null ? cancellation.getPaymentMethod() : "N/A");
            row.createCell(5).setCellValue(cancellation.getReason() != null ? cancellation.getReason() : "N/A");
            row.createCell(6).setCellValue(cancellation.getInitiatedBy() != null ? cancellation.getInitiatedBy() : "N/A");
            row.createCell(7).setCellValue(cancellation.getCancelledBy() != null ? cancellation.getCancelledBy() : "N/A");
        }

        for (int i = 0; i < headers.length; i++) {
            sheet.autoSizeColumn(i);
        }
    }

    /**
     * Creates Excel sheet for chargeback (refund) report.
     * Includes restaurant info, date range, and detailed refund transactions.
     *
     * @param workbook        the Excel workbook to add sheet to
     * @param chargebackReport chargeback report data
     * @param restaurant       restaurant entity (for display)
     * @param startDateTime    report start date/time
     * @param endDateTime      report end date/time
     * @param headerStyle      cell style for headers
     * @param titleStyle       cell style for titles
     * @param monetaryStyle    cell style for monetary values (chain currency)
     * @param currencySymbol   chain currency symbol for {@link CurrencyFormatter}
     */
    private void createChargebackSheet(Workbook workbook,
                                      PaymentAndFinancialsResponse.ChargebackReport chargebackReport,
                                      Restaurant restaurant,
                                      LocalDateTime startDateTime,
                                      LocalDateTime endDateTime,
                                      CellStyle headerStyle,
                                      CellStyle titleStyle,
                                      CellStyle monetaryStyle,
                                      String currencySymbol) {
        Sheet sheet = workbook.createSheet(messageUtil.getMessage("reports.export.sheet.refundReport"));

        int rowNum = 0;
        rowNum = addReportInfo(sheet, rowNum, restaurant, startDateTime, endDateTime);

        rowNum++;
        Row headerRow = sheet.createRow(rowNum++);
        String[] headers = {
                messageUtil.getMessage("reports.export.header.transactionId"),
                messageUtil.getMessage("reports.export.header.dateTime"),
                messageUtil.getMessage("reports.export.header.amount"),
                messageUtil.getMessage("reports.export.header.paymentMethod"),
                messageUtil.getMessage("reports.export.header.reason"),
                messageUtil.getMessage("reports.export.header.bankStatus")
        };
        for (int i = 0; i < headers.length; i++) {
            Cell cell = headerRow.createCell(i);
            cell.setCellValue(headers[i]);
            cell.setCellStyle(headerStyle);
        }

        for (PaymentAndFinancialsResponse.ChargebackItem chargeback : chargebackReport.getChargebacks()) {
            Row row = sheet.createRow(rowNum++);
            row.createCell(0).setCellValue(chargeback.getTransactionId() != null ? chargeback.getTransactionId() : "N/A");
            row.createCell(1).setCellValue(chargeback.getDateTime() != null ?
                    chargeback.getDateTime().format(DATETIME_FORMAT) : "N/A");
            Cell amountCell = row.createCell(2);
            if (chargeback.getAmount() != null) {
                amountCell.setCellValue(CurrencyFormatter.formatAmount(chargeback.getAmount(), currencySymbol).doubleValue());
                amountCell.setCellStyle(monetaryStyle);
            }
            row.createCell(3).setCellValue(chargeback.getPaymentMethod() != null ? chargeback.getPaymentMethod() : "N/A");
            row.createCell(4).setCellValue(chargeback.getReason() != null ? chargeback.getReason() : "N/A");
            row.createCell(5).setCellValue(chargeback.getBankStatus() != null ? chargeback.getBankStatus() : "Pending");
        }

        for (int i = 0; i < headers.length; i++) {
            sheet.autoSizeColumn(i);
        }
    }

    /**
     * Creates Excel sheet for wastage report.
     * Includes restaurant info, date range, and detailed wastage items grouped by category.
     *
     * @param workbook      the Excel workbook to add sheet to
     * @param wastageReport wastage report data
     * @param restaurant    restaurant entity (for display)
     * @param startDateTime report start date/time
     * @param endDateTime   report end date/time
     * @param headerStyle   cell style for headers
     * @param titleStyle    cell style for titles
     * @param monetaryStyle cell style for monetary values (chain currency)
     * @param currencySymbol chain currency symbol for {@link CurrencyFormatter}
     */
    private void createWastageSheet(Workbook workbook,
                                    PaymentAndFinancialsResponse.WastageReport wastageReport,
                                    Restaurant restaurant,
                                    LocalDateTime startDateTime,
                                    LocalDateTime endDateTime,
                                    CellStyle headerStyle,
                                    CellStyle titleStyle,
                                    CellStyle monetaryStyle,
                                    String currencySymbol) {
        Sheet sheet = workbook.createSheet(messageUtil.getMessage("reports.export.sheet.wastageReport"));

        int rowNum = 0;
        rowNum = addReportInfo(sheet, rowNum, restaurant, startDateTime, endDateTime);

        rowNum++;
        Row headerRow = sheet.createRow(rowNum++);
        String[] headers = {
                messageUtil.getMessage("csv.dashboard.header.item.name"),
                messageUtil.getMessage("csv.dashboard.header.category"),
                messageUtil.getMessage("reports.export.header.quantityWasted"),
                messageUtil.getMessage("reports.export.header.totalWastageCost"),
                messageUtil.getMessage("reports.export.header.dateTime"),
                messageUtil.getMessage("reports.export.header.reason")
        };
        for (int i = 0; i < headers.length; i++) {
            Cell cell = headerRow.createCell(i);
            cell.setCellValue(headers[i]);
            cell.setCellStyle(headerStyle);
        }

        for (PaymentAndFinancialsResponse.WastageItem wastage : wastageReport.getWastageItems()) {
            Row row = sheet.createRow(rowNum++);
            row.createCell(0).setCellValue(wastage.getItemName() != null ? wastage.getItemName() : "N/A");
            row.createCell(1).setCellValue(wastage.getCategory() != null ? wastage.getCategory() : "N/A");
            row.createCell(2).setCellValue(wastage.getQuantityWasted() != null ? wastage.getQuantityWasted() : 0);
            Cell costCell = row.createCell(3);
            if (wastage.getTotalWastageCost() != null) {
                costCell.setCellValue(CurrencyFormatter.formatAmount(wastage.getTotalWastageCost(), currencySymbol).doubleValue());
                costCell.setCellStyle(monetaryStyle);
            }
            row.createCell(4).setCellValue(wastage.getDateTime() != null ?
                    wastage.getDateTime().format(DATETIME_FORMAT) : "N/A");
            row.createCell(5).setCellValue(wastage.getReason() != null ? wastage.getReason() : "N/A");
        }

        for (int i = 0; i < headers.length; i++) {
            sheet.autoSizeColumn(i);
        }
    }

    /**
     * Exports the Payment & Financials report to an Excel workbook written to the HTTP response.
     * <p>
     * The workbook contains multiple sheets (e.g. Payment & Cash Drawer, Cancellation, Refund/Chargeback, Wastage).
     * Access is validated, the effective date range is resolved from {@code period}/{@code startDate}/{@code endDate},
     * and the workbook is streamed to the caller.
     *
     * @param period optional period filter (e.g. TODAY, 30_DAYS, 3_MONTHS, 6_MONTHS, CUSTOM)
     * @param startDate optional start date for custom date range
     * @param endDate optional end date for custom date range
     * @param restaurantId optional filter by specific restaurant id
     * @param restaurantGroupId optional filter by restaurant group id
     * @param userId user id for access control
     * @param userRole user role for access control
     * @param locale locale tag used for messages (defaults to "en" when {@code null})
     * @param response servlet response to write the Excel file to
     * @throws IOException if writing the workbook fails
     * @throws ResponseStatusException if validation fails, access is denied, or restaurant is not found
     */
    @Override
    public void exportPaymentAndFinancialsToExcel(
            String period,
            LocalDateTime startDate,
            LocalDateTime endDate,
            UUID restaurantId,
            UUID restaurantGroupId,
            String userId,
            String userRole,
            String locale,
            HttpServletResponse response) throws IOException {

        Locale localeObj = Locale.forLanguageTag(locale != null ? locale : "en");
        LocaleContextHolder.setLocale(localeObj);

        try {
            // Validate role-based access
            validateReportAccess(userRole, localeObj);

            // Get restaurant IDs based on role and filters
            List<UUID> restaurantIds = getRestaurantIdsForReport(restaurantId, restaurantGroupId, userId, userRole, localeObj);

            if (restaurantIds.isEmpty()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        messageUtil.getMessage(msgReportsErrorRestaurantIdRequired, localeObj));
            }

            // For export, use first restaurant (multi-restaurant aggregation can be added later)
            UUID primaryRestaurantId = restaurantIds.get(0);
            Restaurant restaurant = restaurantRepository.findByIdAndIsDeletedFalse(primaryRestaurantId)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                            messageUtil.getMessage(msgRestaurantGetErrorNotFound, localeObj)));

            validateDateParameters(period, startDate, endDate, localeObj);

            LocalDateTime startDateTime;
            LocalDateTime endDateTime = LocalDateTime.now(ZoneOffset.UTC).withHour(23).withMinute(59).withSecond(59).withNano(999999999);

            if (startDate != null && endDate != null) {
                startDateTime = startDate.withHour(0).withMinute(0).withSecond(0).withNano(0);
                endDateTime = endDate.withHour(23).withMinute(59).withSecond(59).withNano(999999999);
            } else if (period != null && !period.isEmpty()) {
                switch (period.toUpperCase()) {
                    case PERIOD_DAILY:
                    case PERIOD_TODAY:
                        // Single day - today
                        startDateTime = LocalDateTime.now(ZoneOffset.UTC).withHour(0).withMinute(0).withSecond(0).withNano(0);
                        endDateTime = LocalDateTime.now(ZoneOffset.UTC).withHour(23).withMinute(59).withSecond(59).withNano(999999999);
                        break;
                    case PERIOD_30_DAYS:
                        startDateTime = LocalDateTime.now(ZoneOffset.UTC).minusDays(30).withHour(0).withMinute(0).withSecond(0).withNano(0);
                        break;
                    case PERIOD_3_MONTHS:
                        startDateTime = LocalDateTime.now(ZoneOffset.UTC).minusMonths(3).withHour(0).withMinute(0).withSecond(0).withNano(0);
                        break;
                    case PERIOD_6_MONTHS:
                        startDateTime = LocalDateTime.now(ZoneOffset.UTC).minusMonths(6).withHour(0).withMinute(0).withSecond(0).withNano(0);
                        break;
                    default:
                        throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                                messageUtil.getMessage(msgReportsErrorInvalidPeriod, localeObj));
                }
            } else {
                startDateTime = SENTINEL_DATE;
                endDateTime = SENTINEL_DATE;
            }

            // Get all report data (without pagination for export)
            PaymentAndFinancialsResponse.PaymentReconciliationReport paymentReport = getPaymentReconciliationReport(primaryRestaurantId, startDateTime, endDateTime);
            PaymentAndFinancialsResponse.CashDrawerReconciliationReport cashDrawerReport = getCashDrawerReconciliationReport(primaryRestaurantId, startDateTime, endDateTime);
            PaymentAndFinancialsResponse.CancellationReport cancellationReport = getCancellationReport(primaryRestaurantId, startDateTime, endDateTime, null, null, null, null);
            PaymentAndFinancialsResponse.ChargebackReport chargebackReport = getChargebackReport(primaryRestaurantId, startDateTime, endDateTime, null, null, null, null);
            PaymentAndFinancialsResponse.WastageReport wastageReport = getWastageReport(primaryRestaurantId, startDateTime, endDateTime, null, null, null, null);

            setExcelExportResponseHeaders(response, msgReportsExportFilenamePaymentAndFinancials, localeObj);

            // Create workbook
            try (Workbook workbook = new XSSFWorkbook()) {
                // Create styles
                CellStyle headerStyle = createHeaderStyle(workbook);
                CellStyle titleStyle = createTitleStyle(workbook);
                String currencySymbol = getChainCurrencySymbol();
                CellStyle monetaryStyle = createMonetaryNumberStyle(workbook, currencySymbol);

                // Sheet 1: Payment Reconciliation + Cash Drawer Reconciliation
                createPaymentAndCashDrawerSheet(workbook, paymentReport, cashDrawerReport, restaurant,
                        startDateTime, endDateTime, headerStyle, titleStyle, monetaryStyle, currencySymbol);

                // Sheet 2: Cancellation Report
                createCancellationSheet(workbook, cancellationReport, restaurant,
                        startDateTime, endDateTime, headerStyle, titleStyle, monetaryStyle, currencySymbol);

                // Sheet 3: Refund Report
                createChargebackSheet(workbook, chargebackReport, restaurant,
                        startDateTime, endDateTime, headerStyle, titleStyle, monetaryStyle, currencySymbol);

                // Sheet 4: Wastage Report
                createWastageSheet(workbook, wastageReport, restaurant,
                        startDateTime, endDateTime, headerStyle, titleStyle, monetaryStyle, currencySymbol);

                // Write workbook to response
                workbook.write(response.getOutputStream());
            }

            logger.info("Payment and financials exported to Excel successfully for restaurant: {}", primaryRestaurantId);

        } catch (ResponseStatusException e) {
            throw e;
        } catch (Exception e) {
            logger.error("Error exporting payment and financials to Excel", e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    messageUtil.getMessage(msgReportsExportError, localeObj));
        }
    }

    /**
     * Retrieves performance report including sales by server and customer rating distribution.
     * Supports filtering by period or custom date range, restaurant or restaurant group.
     * Each sub-report supports independent pagination and sorting.
     *
     * @param period            optional period filter (TODAY, 30_DAYS, 3_MONTHS, 6_MONTHS, or CUSTOM)
     * @param startDate         optional start date for custom date range
     * @param endDate           optional end date for custom date range
     * @param restaurantId      optional filter by specific restaurant ID
     * @param restaurantGroupId optional filter by restaurant group ID
     * @param serverPage        page number for sales by server report
     * @param serverSize        page size for sales by server report
     * @param serverSortBy      sort field for sales by server report
     * @param serverSortDirection sort direction for sales by server report
     * @param page              page number for customer rating distribution
     * @param size              page size for customer rating distribution
     * @param sortBy            sort field for customer rating distribution
     * @param sortDirection     sort direction for customer rating distribution
     * @param userId            user ID for access control
     * @param userRole          user role for access control
     * @param locale            locale code for localized responses
     * @return ResponseDto containing performance report with sales by server and rating distribution
     * @throws ResponseStatusException if validation fails, access denied, or restaurant not found
     */
    @Override
    public ResponseDto<PerformanceResponse> getPerformance(
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
            String locale) {

        Locale localeObj = Locale.forLanguageTag(locale != null ? locale : "en");
        LocaleContextHolder.setLocale(localeObj);

        try {
            // Validate role-based access
            validateReportAccess(userRole, localeObj);

            // Get restaurant IDs based on role and filters
            List<UUID> restaurantIds = getRestaurantIdsForReport(restaurantId, restaurantGroupId, userId, userRole, localeObj);

            if (restaurantIds.isEmpty()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        messageUtil.getMessage(msgReportsErrorRestaurantIdRequired, localeObj));
            }

            // For now, use first restaurant (multi-restaurant aggregation can be added later)
            UUID primaryRestaurantId = restaurantIds.get(0);
            restaurantRepository.findByIdAndIsDeletedFalse(primaryRestaurantId)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                            messageUtil.getMessage(msgRestaurantGetErrorNotFound, localeObj)));

            validateDateParameters(period, startDate, endDate, localeObj);

            LocalDateTime startDateTime;
            LocalDateTime endDateTime = LocalDateTime.now(ZoneOffset.UTC).withHour(23).withMinute(59).withSecond(59).withNano(999999999);

            if (startDate != null && endDate != null) {
                startDateTime = startDate.withHour(0).withMinute(0).withSecond(0).withNano(0);
                endDateTime = endDate.withHour(23).withMinute(59).withSecond(59).withNano(999999999);
            } else if (period != null && !period.isEmpty()) {
                switch (period.toUpperCase()) {
                    case PERIOD_DAILY:
                    case PERIOD_TODAY:
                        // Single day - today
                        startDateTime = LocalDateTime.now(ZoneOffset.UTC).withHour(0).withMinute(0).withSecond(0).withNano(0);
                        endDateTime = LocalDateTime.now(ZoneOffset.UTC).withHour(23).withMinute(59).withSecond(59).withNano(999999999);
                        break;
                    case PERIOD_30_DAYS:
                        startDateTime = LocalDateTime.now(ZoneOffset.UTC).minusDays(30).withHour(0).withMinute(0).withSecond(0).withNano(0);
                        break;
                    case PERIOD_3_MONTHS:
                        startDateTime = LocalDateTime.now(ZoneOffset.UTC).minusMonths(3).withHour(0).withMinute(0).withSecond(0).withNano(0);
                        break;
                    case PERIOD_6_MONTHS:
                        startDateTime = LocalDateTime.now(ZoneOffset.UTC).minusMonths(6).withHour(0).withMinute(0).withSecond(0).withNano(0);
                        break;
                    default:
                        throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                                messageUtil.getMessage(msgReportsErrorInvalidPeriod, localeObj));
                }
            } else {
                startDateTime = SENTINEL_DATE;
                endDateTime = SENTINEL_DATE;
            }

            PerformanceResponse response = PerformanceResponse.builder()
                    .salesByServerReport(getSalesByServerReport(primaryRestaurantId, startDateTime, endDateTime, serverPage, serverSize, serverSortBy, serverSortDirection))
                    .customerRatingDistribution(getCustomerRatingDistribution(primaryRestaurantId, startDateTime, endDateTime, page, size, sortBy, sortDirection))
                    .build();

            return ResponseDto.<PerformanceResponse>builder()
                    .message(messageUtil.getMessage(msgReportsGetSuccess, localeObj))
                    .data(response)
                    .build();

        } catch (Exception e) {
            logger.error("Error fetching staff performance", e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    messageUtil.getMessage(msgReportsGetError, Locale.forLanguageTag(locale != null ? locale : "en")));
        }
    }

    /**
     * Retrieves payment and financials report including payment reconciliation, cash drawer reconciliation,
     * cancellation report, chargeback (refund) report, wastage report, and cashier shifts report.
     * Supports filtering by period or custom date range, restaurant or restaurant group.
     * Each sub-report supports independent pagination and sorting.
     *
     * @param period                  optional period filter (TODAY, 30_DAYS, 3_MONTHS, 6_MONTHS, or CUSTOM)
     * @param startDate               optional start date for custom date range
     * @param endDate                 optional end date for custom date range
     * @param restaurantId            optional filter by specific restaurant ID
     * @param restaurantGroupId      optional filter by restaurant group ID
     * @param cancellationPage        page number for cancellation report
     * @param cancellationSize        page size for cancellation report
     * @param cancellationSortBy      sort field for cancellation report
     * @param cancellationSortDirection sort direction for cancellation report
     * @param chargebackPage          page number for chargeback report
     * @param chargebackSize          page size for chargeback report
     * @param chargebackSortBy        sort field for chargeback report
     * @param chargebackSortDirection sort direction for chargeback report
     * @param wastagePage             page number for wastage report
     * @param wastageSize             page size for wastage report
     * @param wastageSortBy           sort field for wastage report
     * @param wastageSortDirection    sort direction for wastage report
     * @param shiftsPage              page number for cashier shifts report
     * @param shiftsSize              page size for cashier shifts report
     * @param shiftsSortBy            sort field for cashier shifts report
     * @param shiftsSortDirection     sort direction for cashier shifts report
     * @param shiftsStatus            optional filter by shift status
     * @param shiftsCashDrawerId      optional filter by cash drawer ID
     * @param shiftsCashierId         optional filter by cashier ID
     * @param shiftsSearch            optional search term for cashier shifts
     * @param shiftsStartDate         optional start date for cashier shifts filter
     * @param shiftsEndDate           optional end date for cashier shifts filter
     * @param userId                  user ID for access control
     * @param userRole                user role for access control
     * @param locale                  locale code for localized responses
     * @return ResponseDto containing payment and financials report with all sub-reports
     * @throws ResponseStatusException if validation fails, access denied, or restaurant not found
     */
    @Override
    @Transactional(readOnly = true, noRollbackFor = ResponseStatusException.class)
    public ResponseDto<PaymentAndFinancialsResponse> getPaymentAndFinancials(
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
            String locale) {

        Locale localeObj = Locale.forLanguageTag(locale != null ? locale : "en");
        LocaleContextHolder.setLocale(localeObj);

        try {
            // Validate role-based access
            validateReportAccess(userRole, localeObj);

            // Get restaurant IDs based on role and filters
            List<UUID> restaurantIds = getRestaurantIdsForReport(restaurantId, restaurantGroupId, userId, userRole, localeObj);

            if (restaurantIds.isEmpty()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        messageUtil.getMessage(msgReportsErrorRestaurantIdRequired, localeObj));
            }

            // For now, use first restaurant (multi-restaurant aggregation can be added later)
            UUID primaryRestaurantId = restaurantIds.get(0);
            restaurantRepository.findByIdAndIsDeletedFalse(primaryRestaurantId)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                            messageUtil.getMessage(msgRestaurantGetErrorNotFound, localeObj)));

            validateDateParameters(period, startDate, endDate, localeObj);

            LocalDateTime startDateTime;
            LocalDateTime endDateTime = LocalDateTime.now(ZoneOffset.UTC).withHour(23).withMinute(59).withSecond(59).withNano(999999999);

            if (startDate != null && endDate != null) {
                startDateTime = startDate.withHour(0).withMinute(0).withSecond(0).withNano(0);
                endDateTime = endDate.withHour(23).withMinute(59).withSecond(59).withNano(999999999);
            } else if (period != null && !period.isEmpty()) {
                switch (period.toUpperCase()) {
                    case PERIOD_DAILY:
                    case PERIOD_TODAY:
                        // Single day - today
                        startDateTime = LocalDateTime.now(ZoneOffset.UTC).withHour(0).withMinute(0).withSecond(0).withNano(0);
                        endDateTime = LocalDateTime.now(ZoneOffset.UTC).withHour(23).withMinute(59).withSecond(59).withNano(999999999);
                        break;
                    case PERIOD_30_DAYS:
                        startDateTime = LocalDateTime.now(ZoneOffset.UTC).minusDays(30).withHour(0).withMinute(0).withSecond(0).withNano(0);
                        break;
                    case PERIOD_3_MONTHS:
                        startDateTime = LocalDateTime.now(ZoneOffset.UTC).minusMonths(3).withHour(0).withMinute(0).withSecond(0).withNano(0);
                        break;
                    case PERIOD_6_MONTHS:
                        startDateTime = LocalDateTime.now(ZoneOffset.UTC).minusMonths(6).withHour(0).withMinute(0).withSecond(0).withNano(0);
                        break;
                    default:
                        throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                                messageUtil.getMessage(msgReportsErrorInvalidPeriod, localeObj));
                }
            } else {
                startDateTime = SENTINEL_DATE;
                endDateTime = SENTINEL_DATE;
            }

            // Get cashier shifts report (with error handling in case of issues)
            CashierShiftListResponse cashierShiftsReport = getCashierShiftsReportWithErrorHandling(
                    primaryRestaurantId, shiftsPage, shiftsSize, shiftsSortBy, shiftsSortDirection,
                    shiftsStatus, shiftsCashDrawerId, shiftsCashierId, shiftsSearch,
                    shiftsStartDate, shiftsEndDate, startDateTime, endDateTime, localeObj);

            // Add detailed logging around each sub-report so we can pinpoint failures
            logger.info("PaymentAndFinancials: building sub-reports for restaurant {} between {} and {}",
                    primaryRestaurantId, startDateTime, endDateTime);

            logger.info("PaymentAndFinancials: start paymentReconciliationReport");
            PaymentAndFinancialsResponse.PaymentReconciliationReport paymentReconciliationReport =
                    getPaymentReconciliationReport(primaryRestaurantId, startDateTime, endDateTime);

            logger.info("PaymentAndFinancials: start cashDrawerReconciliationReport");
            PaymentAndFinancialsResponse.CashDrawerReconciliationReport cashDrawerReconciliationReport =
                    getCashDrawerReconciliationReport(primaryRestaurantId, startDateTime, endDateTime);

            logger.info("PaymentAndFinancials: start cancellationReport");
            PaymentAndFinancialsResponse.CancellationReport cancellationReport =
                    getCancellationReport(primaryRestaurantId, startDateTime, endDateTime,
                            cancellationPage, cancellationSize, cancellationSortBy, cancellationSortDirection);

            logger.info("PaymentAndFinancials: start chargebackReport");
            PaymentAndFinancialsResponse.ChargebackReport chargebackReport =
                    getChargebackReport(primaryRestaurantId, startDateTime, endDateTime,
                            chargebackPage, chargebackSize, chargebackSortBy, chargebackSortDirection);

            logger.info("PaymentAndFinancials: start wastageReport");
            PaymentAndFinancialsResponse.WastageReport wastageReport =
                    getWastageReport(primaryRestaurantId, startDateTime, endDateTime,
                            wastagePage, wastageSize, wastageSortBy, wastageSortDirection);

            PaymentAndFinancialsResponse response = PaymentAndFinancialsResponse.builder()
                    .paymentReconciliationReport(paymentReconciliationReport)
                    .cashDrawerReconciliationReport(cashDrawerReconciliationReport)
                    .cancellationReport(cancellationReport)
                    .chargebackReport(chargebackReport)
                    .wastageReport(wastageReport)
                    .cashierShiftsReport(cashierShiftsReport)
                    .build();

            return ResponseDto.<PaymentAndFinancialsResponse>builder()
                    .message(messageUtil.getMessage(msgReportsGetSuccess, localeObj))
                    .data(response)
                    .build();

        } catch (Exception e) {
            logger.error("Error fetching payment and financials", e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    messageUtil.getMessage(msgReportsGetError, Locale.forLanguageTag(locale != null ? locale : "en")));
        }
    }

    /**
     * Exports the Performance report to an Excel workbook written to the HTTP response.
     * <p>
     * The workbook contains multiple sheets such as "Sales By Server" and "Customer Rating Distribution".
     * Access is validated, the effective date range is resolved from {@code period}/{@code startDate}/{@code endDate},
     * and the workbook is streamed to the caller.
     *
     * @param period optional period filter (e.g. TODAY, 30_DAYS, 3_MONTHS, 6_MONTHS, CUSTOM)
     * @param startDate optional start date for custom date range
     * @param endDate optional end date for custom date range
     * @param restaurantId optional filter by specific restaurant id
     * @param restaurantGroupId optional filter by restaurant group id
     * @param userId user id for access control
     * @param userRole user role for access control
     * @param locale locale tag used for messages (defaults to "en" when {@code null})
     * @param response servlet response to write the Excel file to
     * @throws IOException if writing the workbook fails
     * @throws ResponseStatusException if validation fails, access is denied, or restaurant is not found
     */
    @Override
    public void exportPerformanceToExcel(
            String period,
            LocalDateTime startDate,
            LocalDateTime endDate,
            UUID restaurantId,
            UUID restaurantGroupId,
            String userId,
            String userRole,
            String locale,
            HttpServletResponse response) throws IOException {

        Locale localeObj = Locale.forLanguageTag(locale != null ? locale : "en");
        LocaleContextHolder.setLocale(localeObj);

        try {
            // Validate role-based access
            validateReportAccess(userRole, localeObj);

            // Get restaurant IDs based on role and filters
            List<UUID> restaurantIds = getRestaurantIdsForReport(restaurantId, restaurantGroupId, userId, userRole, localeObj);

            if (restaurantIds.isEmpty()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        messageUtil.getMessage(msgReportsErrorRestaurantIdRequired, localeObj));
            }

            // For export, use first restaurant (multi-restaurant aggregation can be added later)
            UUID primaryRestaurantId = restaurantIds.get(0);
            Restaurant restaurant = restaurantRepository.findByIdAndIsDeletedFalse(primaryRestaurantId)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                            messageUtil.getMessage(msgRestaurantGetErrorNotFound, localeObj)));

            validateDateParameters(period, startDate, endDate, localeObj);

            LocalDateTime startDateTime;
            LocalDateTime endDateTime = LocalDateTime.now(ZoneOffset.UTC).withHour(23).withMinute(59).withSecond(59).withNano(999999999);

            if (startDate != null && endDate != null) {
                startDateTime = startDate.withHour(0).withMinute(0).withSecond(0).withNano(0);
                endDateTime = endDate.withHour(23).withMinute(59).withSecond(59).withNano(999999999);
            } else if (period != null && !period.isEmpty()) {
                switch (period.toUpperCase()) {
                    case PERIOD_DAILY:
                    case PERIOD_TODAY:
                        // Single day - today
                        startDateTime = LocalDateTime.now(ZoneOffset.UTC).withHour(0).withMinute(0).withSecond(0).withNano(0);
                        endDateTime = LocalDateTime.now(ZoneOffset.UTC).withHour(23).withMinute(59).withSecond(59).withNano(999999999);
                        break;
                    case PERIOD_30_DAYS:
                        startDateTime = LocalDateTime.now(ZoneOffset.UTC).minusDays(30).withHour(0).withMinute(0).withSecond(0).withNano(0);
                        break;
                    case PERIOD_3_MONTHS:
                        startDateTime = LocalDateTime.now(ZoneOffset.UTC).minusMonths(3).withHour(0).withMinute(0).withSecond(0).withNano(0);
                        break;
                    case PERIOD_6_MONTHS:
                        startDateTime = LocalDateTime.now(ZoneOffset.UTC).minusMonths(6).withHour(0).withMinute(0).withSecond(0).withNano(0);
                        break;
                    default:
                        throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                                messageUtil.getMessage(msgReportsErrorInvalidPeriod, localeObj));
                }
            } else {
                startDateTime = SENTINEL_DATE;
                endDateTime = SENTINEL_DATE;
            }

            // Get all report data (without pagination for export)
            PerformanceResponse.SalesByServerReport salesByServerReport = getSalesByServerReport(primaryRestaurantId, startDateTime, endDateTime, null, null, null, null);
            PerformanceResponse.CustomerRatingDistribution customerRatingDistribution = getCustomerRatingDistribution(primaryRestaurantId, startDateTime, endDateTime, null, null, null, null);

            setExcelExportResponseHeaders(response, msgReportsExportFilenamePerformance, localeObj);

            // Create workbook
            try (Workbook workbook = new XSSFWorkbook()) {
                // Create styles
                CellStyle headerStyle = createHeaderStyle(workbook);
                CellStyle titleStyle = createTitleStyle(workbook);
                String currencySymbol = getChainCurrencySymbol();
                CellStyle monetaryStyle = createMonetaryNumberStyle(workbook, currencySymbol);
                CellStyle percentageDecimalStyle = createNumberStyle(workbook);

                // Sheet 1: Sales By Server Report
                createSalesByServerSheet(workbook, salesByServerReport, restaurant,
                        startDateTime, endDateTime, headerStyle, titleStyle, monetaryStyle, currencySymbol);

                // Sheet 2: Customer Rating Distribution (percentages — fixed two decimals)
                createCustomerRatingSheet(workbook, customerRatingDistribution, restaurant,
                        startDateTime, endDateTime, headerStyle, titleStyle, percentageDecimalStyle);

                // Write workbook to response
                workbook.write(response.getOutputStream());
            }

            logger.info("Performance exported to Excel successfully for restaurant: {}", primaryRestaurantId);

        } catch (ResponseStatusException e) {
            throw e;
        } catch (Exception e) {
            logger.error("Error exporting performance to Excel", e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    messageUtil.getMessage(msgReportsExportError, localeObj));
        }
    }

    /**
     * Creates Excel sheet for sales by server report.
     * Includes restaurant info, date range, and detailed server performance metrics.
     *
     * @param workbook            the Excel workbook to add sheet to
     * @param salesByServerReport sales by server report data
     * @param restaurant          restaurant entity (for display)
     * @param startDateTime       report start date/time
     * @param endDateTime         report end date/time
     * @param headerStyle         cell style for headers
     * @param titleStyle          cell style for titles
     * @param monetaryStyle       cell style for money columns (chain currency)
     * @param currencySymbol      chain currency symbol for {@link CurrencyFormatter}
     */
    private void createSalesByServerSheet(Workbook workbook,
                                         PerformanceResponse.SalesByServerReport salesByServerReport,
                                         Restaurant restaurant,
                                         LocalDateTime startDateTime,
                                         LocalDateTime endDateTime,
                                         CellStyle headerStyle,
                                         CellStyle titleStyle,
                                         CellStyle monetaryStyle,
                                         String currencySymbol) {
        Sheet sheet = workbook.createSheet(messageUtil.getMessage("reports.export.sheet.salesByServer"));

        int rowNum = 0;
        rowNum = addReportInfo(sheet, rowNum, restaurant, startDateTime, endDateTime);

        rowNum++;
        Row headerRow = sheet.createRow(rowNum++);
        String[] headers = {
                messageUtil.getMessage("reports.export.header.serverName"),
                messageUtil.getMessage("reports.export.header.serverCode"),
                messageUtil.getMessage("csv.dashboard.metric.total.orders"),
                messageUtil.getMessage("csv.dashboard.metric.total.sales"),
                messageUtil.getMessage("reports.export.header.averageOrderValue"),
                messageUtil.getMessage("reports.export.header.totalTablesServed")
        };
        for (int i = 0; i < headers.length; i++) {
            Cell cell = headerRow.createCell(i);
            cell.setCellValue(headers[i]);
            cell.setCellStyle(headerStyle);
        }

        for (PerformanceResponse.SalesByServerItem server : salesByServerReport.getServers()) {
            Row row = sheet.createRow(rowNum++);
            row.createCell(0).setCellValue(server.getServerName() != null ? server.getServerName() : "N/A");
            row.createCell(1).setCellValue(server.getServerCode() != null ? server.getServerCode() : "N/A");
            row.createCell(2).setCellValue(server.getTotalOrders() != null ? server.getTotalOrders() : 0);
            Cell salesCell = row.createCell(3);
            if (server.getTotalSales() != null) {
                salesCell.setCellValue(CurrencyFormatter.formatAmount(server.getTotalSales(), currencySymbol).doubleValue());
                salesCell.setCellStyle(monetaryStyle);
            }
            Cell avgOrderCell = row.createCell(4);
            if (server.getAverageOrderValue() != null) {
                avgOrderCell.setCellValue(CurrencyFormatter.formatAmount(server.getAverageOrderValue(), currencySymbol).doubleValue());
                avgOrderCell.setCellStyle(monetaryStyle);
            }
            row.createCell(5).setCellValue(server.getTotalTablesServed() != null ? server.getTotalTablesServed() : 0);
        }

        for (int i = 0; i < headers.length; i++) {
            sheet.autoSizeColumn(i);
        }
    }

    /**
     * Creates Excel sheet for customer rating distribution report.
     * Includes restaurant info, date range, and rating distribution by rating value (1-5).
     *
     * @param workbook                  the Excel workbook to add sheet to
     * @param customerRatingDistribution customer rating distribution report data
     * @param restaurant                restaurant entity (for display)
     * @param startDateTime             report start date/time
     * @param endDateTime               report end date/time
     * @param headerStyle               cell style for headers
     * @param titleStyle                cell style for titles
     * @param percentageDecimalStyle    cell style for percentage column (two decimals)
     */
    private void createCustomerRatingSheet(Workbook workbook,
                                           PerformanceResponse.CustomerRatingDistribution customerRatingDistribution,
                                           Restaurant restaurant,
                                           LocalDateTime startDateTime,
                                           LocalDateTime endDateTime,
                                           CellStyle headerStyle,
                                           CellStyle titleStyle,
                                           CellStyle percentageDecimalStyle) {
        Sheet sheet = workbook.createSheet(messageUtil.getMessage("reports.export.sheet.customerRatingDistribution"));

        int rowNum = 0;
        rowNum = addReportInfo(sheet, rowNum, restaurant, startDateTime, endDateTime);

        rowNum++;
        Row headerRow = sheet.createRow(rowNum++);
        String[] headers = {
                messageUtil.getMessage("reports.export.header.rating"),
                messageUtil.getMessage("reports.export.header.count"),
                messageUtil.getMessage("reports.export.header.percentage")
        };
        for (int i = 0; i < headers.length; i++) {
            Cell cell = headerRow.createCell(i);
            cell.setCellValue(headers[i]);
            cell.setCellStyle(headerStyle);
        }

        for (PerformanceResponse.RatingDistributionItem rating : customerRatingDistribution.getDistribution()) {
            Row row = sheet.createRow(rowNum++);
            row.createCell(0).setCellValue(rating.getRating() != null ? String.valueOf(rating.getRating()) : "N/A");
            row.createCell(1).setCellValue(rating.getCount() != null ? rating.getCount() : 0);
            Cell percentageCell = row.createCell(2);
            if (rating.getPercentage() != null) {
                percentageCell.setCellValue(rating.getPercentage());
                percentageCell.setCellStyle(percentageDecimalStyle);
            }
        }

        for (int i = 0; i < headers.length; i++) {
            sheet.autoSizeColumn(i);
        }
    }

    /**
     * Retrieves the "today sales" summary for the selected restaurant (or restaurant group).
     * <p>
     * Uses the <strong>current</strong> cashier day (UTC): let {@code E} be the most recent
     * {@code cashier_live_dashboard_reset_time} instant on or before "now". Sales and refunds are aggregated
     * for the half-open window {@code [E, E plus 24 hours)}.
     *
     * @param restaurantId optional filter by specific restaurant id
     * @param restaurantGroupId optional filter by restaurant group id
     * @param userId user id for access control
     * @param userRole user role for access control
     * @param locale locale tag used for messages (defaults to "en" when {@code null})
     * @return wrapper containing today's sales summary, including payment-method totals and refunds
     * @throws ResponseStatusException if validation fails, access is denied, or restaurant is not found
     */
    @Override
    public ResponseDto<TodaySalesResponse> getTodaySales(UUID restaurantId, UUID restaurantGroupId, 
            String userId, String userRole, String locale) {
        Locale localeObj = Locale.forLanguageTag(locale != null ? locale : "en");
        LocaleContextHolder.setLocale(localeObj);

        try {
            // Validate restaurantId is provided when restaurantGroupId is null
            if (restaurantId == null && restaurantGroupId == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        messageUtil.getMessage(msgReportsErrorRestaurantIdRequired, localeObj));
            }

            // Validate role-based access
            validateReportAccess(userRole, localeObj);

            // Get restaurant IDs based on role and filters
            List<UUID> restaurantIds = getRestaurantIdsForReport(restaurantId, restaurantGroupId, userId, userRole, localeObj);

            if (restaurantIds.isEmpty()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        messageUtil.getMessage(msgReportsErrorRestaurantIdRequired, localeObj));
            }

            // For now, use first restaurant (multi-restaurant aggregation can be added later)
            UUID primaryRestaurantId = restaurantIds.get(0);
            Restaurant primaryRestaurant = restaurantRepository.findByIdAndIsDeletedFalse(primaryRestaurantId)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                            messageUtil.getMessage(msgRestaurantGetErrorNotFound, localeObj)));

            // Current cashier day: [E, E + 24h) where E = latest reset instant <= now (UTC)
            LocalDateTime windowStart = calculateCashierResetTime(primaryRestaurant);
            LocalDateTime windowEndExclusive = windowStart.plusDays(1);

            List<Object[]> paymentMethodSales = transactionRepository.getSalesByPaymentMethodInCashierDayWindow(
                    primaryRestaurantId, windowStart, windowEndExclusive);

            // Initialize sales amounts
            BigDecimal totalSales = BigDecimal.ZERO;
            BigDecimal cashSales = BigDecimal.ZERO;
            BigDecimal cardSales = BigDecimal.ZERO;
            BigDecimal upiSales = BigDecimal.ZERO;

            // Process payment method sales
            for (Object[] row : paymentMethodSales) {
                String paymentMethod = (String) row[0];
                BigDecimal salesAmount = row[1] != null
                        ? BigDecimal.valueOf(((Number) row[1]).doubleValue()).setScale(2, RoundingMode.HALF_UP)
                        : BigDecimal.ZERO;

                if (salesAmount != null) {
                    totalSales = totalSales.add(salesAmount);

                    // Group card types (CREDIT_CARD, DEBIT_CARD) as CARD
                    if ("CASH".equalsIgnoreCase(paymentMethod)) {
                        cashSales = cashSales.add(salesAmount);
                    } else if ("CREDIT_CARD".equalsIgnoreCase(paymentMethod)
                            || "DEBIT_CARD".equalsIgnoreCase(paymentMethod)
                            || "CARD".equalsIgnoreCase(paymentMethod)) {
                        cardSales = cardSales.add(salesAmount);
                    } else if ("UPI".equalsIgnoreCase(paymentMethod)) {
                        upiSales = upiSales.add(salesAmount);
                    }
                }
            }

            // Get today's approved refund amounts (both full and partial refunds)
            // Only includes refunds that have been APPROVED by manager (requestStatus = 'APPROVED')
            // Declined refunds are not included
            List<BigDecimal> approvedRefundAmounts = refundRepository.getApprovedRefundAmountsInCashierDayWindow(
                    primaryRestaurantId, windowStart, windowEndExclusive);
            BigDecimal totalRefund = BigDecimal.ZERO;
            
            // Sum up all approved refund amounts (includes both full and partial refunds)
            for (BigDecimal refundAmount : approvedRefundAmounts) {
                if (refundAmount != null) {
                    totalRefund = totalRefund.add(refundAmount.setScale(2, RoundingMode.HALF_UP));
                }
            }

            // Build response
            TodaySalesResponse response = TodaySalesResponse.builder()
                    .totalSales(totalSales)
                    .cashSales(cashSales)
                    .cardSales(cardSales)
                    .upiSales(upiSales)
                    .refund(totalRefund)
                    .build();

            return ResponseDto.<TodaySalesResponse>builder()
                    .message(messageUtil.getMessage("reports.today.sales.success", localeObj))
                    .data(response)
                    .build();

        } catch (ResponseStatusException e) {
            throw e;
        } catch (Exception e) {
            logger.error("Error fetching today's sales for restaurant: {}", restaurantId, e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    messageUtil.getMessage("reports.today.sales.error", localeObj));
        }
    }

    /**
     * Latest cashier dashboard reset instant at or before "now" (UTC wall clock).
     * Used as the start of the current cashier-day window.
     * Defaults to midnight UTC if restaurant doesn't have a value set.
     */
    private LocalDateTime calculateCashierResetTime(Restaurant restaurant) {
        if (restaurant == null || restaurant.getCashierLiveDashboardResetTime() == null) {
            return LocalDate.now(ZoneOffset.UTC).atStartOfDay();
        }

        try {
            OffsetTime resetOffsetTime = restaurant.getCashierLiveDashboardResetTime()
                    .withOffsetSameInstant(ZoneOffset.UTC);

            LocalDateTime nowUtc = LocalDateTime.now(ZoneOffset.UTC);
            LocalDateTime resetDateTime = LocalDate.now(ZoneOffset.UTC).atTime(resetOffsetTime.toLocalTime());

            // If today's reset time hasn't occurred yet, use yesterday's reset time
            if (resetDateTime.isAfter(nowUtc)) {
                resetDateTime = resetDateTime.minusDays(1);
            }

            return resetDateTime;
        } catch (Exception e) {
            logger.warn("Failed to parse cashier reset time for restaurant {}. Falling back to midnight UTC. Error: {}",
                    restaurant != null ? restaurant.getId() : "unknown", e.getMessage());
            return LocalDate.now(ZoneOffset.UTC).atStartOfDay();
        }
    }

    /**
     * Generates payment reconciliation report for a restaurant within a date range.
     * Groups transactions by payment method and calculates total transactions and amounts.
     * Results are sorted by total amount descending.
     *
     * @param restaurantId the UUID of the restaurant
     * @param startDate    start date/time of the period (inclusive)
     * @param endDate      end date/time of the period (inclusive)
     * @return PaymentReconciliationReport with payment method breakdown
     */
    private PaymentAndFinancialsResponse.PaymentReconciliationReport getPaymentReconciliationReport(
            UUID restaurantId, LocalDateTime startDate, LocalDateTime endDate) {

        List<Object[]> results = transactionRepository.getPaymentReconciliationReport(
                restaurantId, startDate, endDate);

        if (results.isEmpty()) {
            return PaymentAndFinancialsResponse.PaymentReconciliationReport.builder()
                    .payments(Collections.emptyList())
                    .build();
        }

        // Map all results to payment items
        List<PaymentAndFinancialsResponse.PaymentReconciliationItem> payments = results.stream()
                .map(row -> {
                    String paymentMethod = row[0] != null ? (String) row[0] : "N/A";
                    Long totalTransactions = row[1] != null ? ((Number) row[1]).longValue() : 0L;
                    BigDecimal totalAmount = row[2] != null ? BigDecimal.valueOf(((Number) row[2]).doubleValue())
                            .setScale(2, RoundingMode.HALF_UP) : BigDecimal.ZERO;

                    return PaymentAndFinancialsResponse.PaymentReconciliationItem.builder()
                            .paymentMethod(paymentMethodDisplaySupport.toDisplayName(paymentMethod))
                            .totalTransactions(totalTransactions)
                            .totalAmount(totalAmount)
                            .build();
                })
                .collect(Collectors.toList());

        // Default sorting: by totalAmount descending
        payments.sort(Comparator.comparing(
                PaymentAndFinancialsResponse.PaymentReconciliationItem::getTotalAmount,
                Comparator.nullsLast(Comparator.reverseOrder())));

        return PaymentAndFinancialsResponse.PaymentReconciliationReport.builder()
                .payments(payments)
                .build();
    }

    /**
     * Generates cash drawer reconciliation report for a restaurant within a date range.
     * Calculates opening balance, cash sales, refunds, withdrawals, deposits, adjustments,
     * expected/actual cash balance, and discrepancy amount.
     *
     * @param restaurantId the UUID of the restaurant
     * @param startDate    start date/time of the period (inclusive)
     * @param endDate      end date/time of the period (inclusive)
     * @return CashDrawerReconciliationReport with cash drawer summary and reconciliation status
     */
    private PaymentAndFinancialsResponse.CashDrawerReconciliationReport getCashDrawerReconciliationReport(
            UUID restaurantId, LocalDateTime startDate, LocalDateTime endDate) {

        logger.info("Getting cash drawer reconciliation report for restaurantId: {}, startDate: {}, endDate: {}", 
                restaurantId, startDate, endDate);
        
        // Log a test query to see if data exists
        logger.info("Checking if cash drawer logs exist for restaurantId: {}", restaurantId);
        
        List<Object[]> results = cashDrawerLogRepository.getCashDrawerReconciliationSummary(
                restaurantId, startDate, endDate);

        logger.info("Cash drawer reconciliation query returned {} results", results.size());
        if (!results.isEmpty() && results.get(0) != null) {
            logger.info("First result row: {}", java.util.Arrays.toString(results.get(0)));
            logger.info("Opening balance: {}, Total cash sales: {}, Total deposits: {}", 
                    results.get(0)[0], results.get(0)[1], results.get(0).length > 5 ? results.get(0)[5] : "N/A");
        } else {
            logger.warn("Cash drawer reconciliation query returned empty results or null row");
        }

        // Calculate adjustment amounts
        BigDecimal adjustmentApproved = cashDrawerLogRepository.getSumOfAdjustmentApproved(restaurantId, startDate, endDate);
        if (adjustmentApproved == null) {
            adjustmentApproved = BigDecimal.ZERO;
        }
        adjustmentApproved = adjustmentApproved.setScale(2, RoundingMode.HALF_UP);

        BigDecimal adjustmentRejected = cashDrawerLogRepository.getSumOfAdjustmentRejected(restaurantId, startDate, endDate);
        if (adjustmentRejected == null) {
            adjustmentRejected = BigDecimal.ZERO;
        }
        adjustmentRejected = adjustmentRejected.setScale(2, RoundingMode.HALF_UP);

        BigDecimal adjustmentPending = cashDrawerLogRepository.getSumOfAdjustmentPending(restaurantId, startDate, endDate);
        if (adjustmentPending == null) {
            adjustmentPending = BigDecimal.ZERO;
        }
        adjustmentPending = adjustmentPending.setScale(2, RoundingMode.HALF_UP);

        if (results.isEmpty() || results.get(0) == null) {
            return PaymentAndFinancialsResponse.CashDrawerReconciliationReport.builder()
                    .openingBalance(BigDecimal.ZERO)
                    .totalCashSales(BigDecimal.ZERO)
                    .totalCashRefundsPaid(BigDecimal.ZERO)
                    .cashWithdrawal(BigDecimal.ZERO)
                    .expectedCashBalance(BigDecimal.ZERO)
                    .actualCashBalance(BigDecimal.ZERO)
                    .discrepancyAmount(BigDecimal.ZERO)
                    .adjustmentPending(adjustmentPending)
                    .adjustmentApproved(adjustmentApproved)
                    .adjustmentRejected(adjustmentRejected)
                    .status(STATUS_BALANCED)
                    .build();
        }

        Object[] row = results.get(0);
        BigDecimal openingBalance = row[0] != null ? BigDecimal.valueOf(((Number) row[0]).doubleValue()).setScale(2, RoundingMode.HALF_UP) : BigDecimal.ZERO;
        BigDecimal totalCashSales = row[1] != null ? BigDecimal.valueOf(((Number) row[1]).doubleValue()).setScale(2, RoundingMode.HALF_UP) : BigDecimal.ZERO;
        BigDecimal totalCashRefundsPaid = row[2] != null ? BigDecimal.valueOf(((Number) row[2]).doubleValue()).setScale(2, RoundingMode.HALF_UP) : BigDecimal.ZERO;
        BigDecimal totalCashRefundsReceived = row[3] != null ? BigDecimal.valueOf(((Number) row[3]).doubleValue()).setScale(2, RoundingMode.HALF_UP) : BigDecimal.ZERO;
        BigDecimal cashWithdrawal = row[4] != null ? BigDecimal.valueOf(((Number) row[4]).doubleValue()).setScale(2, RoundingMode.HALF_UP) : BigDecimal.ZERO;
        BigDecimal totalDeposits = row.length > 5 && row[5] != null
                ? BigDecimal.valueOf(((Number) row[5]).doubleValue()).setScale(2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;
        BigDecimal totalActualFlow = row.length > 6 && row[6] != null
                ? BigDecimal.valueOf(((Number) row[6]).doubleValue()).setScale(2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;
        BigDecimal cashSalesGrossIn = row.length > 7 && row[7] != null
                ? BigDecimal.valueOf(((Number) row[7]).doubleValue()).setScale(2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;
        BigDecimal cashSalesGrossOut = row.length > 8 && row[8] != null
                ? BigDecimal.valueOf(((Number) row[8]).doubleValue()).setScale(2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;
        BigDecimal cashRefundsGrossOut = row.length > 9 && row[9] != null
                ? BigDecimal.valueOf(((Number) row[9]).doubleValue()).setScale(2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;
        BigDecimal cashRefundsGrossIn = row.length > 10 && row[10] != null
                ? BigDecimal.valueOf(((Number) row[10]).doubleValue()).setScale(2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;

        // Calculate expected balance according to business standard (Ideal):
        // opening + sales (ideal) + deposits - refundsPaid (ideal) - withdrawals
        BigDecimal expectedCashBalance = openingBalance
                .add(totalCashSales)
                .add(totalDeposits)
                .subtract(totalCashRefundsPaid)
                .subtract(cashWithdrawal);

        // Get actual cash balance from cashier_shift table (sum of closing balances from all closed/approved shifts)
        // This represents the actual cash counted when shifts were closed
        // Convert LocalDateTime to OffsetDateTime (closedAt is OffsetDateTime in entity)
        BigDecimal actualCashBalance = cashierShiftRepository.getTotalClosingBalanceByRestaurantIdAndDateRange(
                restaurantId, startDate.atOffset(ZoneOffset.UTC), endDate.atOffset(ZoneOffset.UTC));
        // Use only closing balance - if no closed shifts exist, it will be 0
        if (actualCashBalance == null) {
            actualCashBalance = BigDecimal.ZERO;
        }
        actualCashBalance = actualCashBalance.setScale(2, RoundingMode.HALF_UP);
        
        // Calculate discrepancy as actual - expected
        // This should match the sum of discrepancy_amount from cashier_shift table
        BigDecimal discrepancyAmount = actualCashBalance.subtract(expectedCashBalance);
        discrepancyAmount = discrepancyAmount.setScale(2, RoundingMode.HALF_UP);
        
        // Calculate net discrepancy after approved adjustments
        // If approved adjustments offset the discrepancy, status should be "balanced"
        BigDecimal netDiscrepancy = discrepancyAmount.add(adjustmentApproved);
        netDiscrepancy = netDiscrepancy.setScale(2, RoundingMode.HALF_UP);
        
        // Status is "balanced" if net discrepancy (after approved adjustments) equals zero
        // Since all amounts are rounded to 2 decimal places, we can do exact comparison
        // Also check if there are no pending adjustments that need attention
        String status = (netDiscrepancy.compareTo(BigDecimal.ZERO) == 0 
                && adjustmentPending.compareTo(BigDecimal.ZERO) == 0) 
                ? STATUS_BALANCED : "discrepancy";

        return PaymentAndFinancialsResponse.CashDrawerReconciliationReport.builder()
                .openingBalance(openingBalance)
                .totalCashSales(totalCashSales)
                .totalCashRefundsPaid(totalCashRefundsPaid)
                .cashWithdrawal(cashWithdrawal)
                .totalCashDeposits(totalDeposits)
                .cashSalesGrossIn(cashSalesGrossIn)
                .cashSalesGrossOut(cashSalesGrossOut)
                .cashRefundsGrossOut(cashRefundsGrossOut)
                .cashRefundsGrossIn(cashRefundsGrossIn)
                .expectedCashBalance(expectedCashBalance)
                .actualCashBalance(actualCashBalance)
                .discrepancyAmount(discrepancyAmount)
                .adjustmentPending(adjustmentPending)
                .adjustmentApproved(adjustmentApproved)
                .adjustmentRejected(adjustmentRejected)
                .status(status)
                .build();
    }

    /**
     * Generates cancellation report for a restaurant within a date range.
     * Only includes cancellations that originate from ordered items and ordered combos (not order-level cancellations).
     * Supports pagination and sorting.
     *
     * @param restaurantId the UUID of the restaurant
     * @param startDate    start date/time of the period (inclusive)
     * @param endDate      end date/time of the period (inclusive)
     * @param page         page number for pagination (null returns all)
     * @param size         page size for pagination (null returns all)
     * @param sortBy       field to sort by
     * @param sortDirection sort direction
     * @return CancellationReport with paginated list of cancellation transactions
     */
    private PaymentAndFinancialsResponse.CancellationReport getCancellationReport(
            UUID restaurantId, LocalDateTime startDate, LocalDateTime endDate,
            Integer page, Integer size, String sortBy, Sort.Direction sortDirection) {

        List<Object[]> results = transactionRepository.getCancellationReport(
                restaurantId, startDate, endDate);

        // Business requirement: for the Payment & Financials cancellation report,
        // only include cancellations that originate from ordered items and ordered combos.
        // The native query returns a "type" column as the last field of each row:
        //  - "transaction" for transaction-level cancellations
        //  - "order"       for order-level cancellations
        //  - "item"        for ordered_item level cancellations
        //  - "combo"       for ordered_combo level cancellations
        // Here we filter to keep only "item" and "combo" rows.
        results = results.stream()
                .filter(row -> {
                    String type = row[10] != null ? (String) row[10] : null;
                    return "item".equalsIgnoreCase(type) || "combo".equalsIgnoreCase(type);
                })
                .collect(Collectors.toList());

        if (results.isEmpty()) {
            PaginationMetaData metaData = createPaginationMetaData(0, page, size);
            return PaymentAndFinancialsResponse.CancellationReport.builder()
                    .cancellations(Collections.emptyList())
                    .count(0L)
                    .total(0L)
                    .metaData(metaData)
                    .build();
        }

        // Map all results to cancellation items first (before sorting and pagination)
        List<PaymentAndFinancialsResponse.CancellationItem> cancellations = results.stream()
                .map(row -> {
                    UUID transactionId = (UUID) row[0];
                    String transactionNumber = row[1] != null ? (String) row[1] : "N/A";
                    LocalDateTime dateTime = row[2] != null ? convertToLocalDateTime(row[2]) : null;
                    BigDecimal amount = row[3] != null ? BigDecimal.valueOf(((Number) row[3]).doubleValue()).setScale(2, RoundingMode.HALF_UP) : BigDecimal.ZERO;
                    String paymentMethod = row[4] != null ? (String) row[4] : "N/A";
                    String reason = row[5] != null ? (String) row[5] : "N/A";
                    String requestedByFirstName = row[6] != null ? (String) row[6] : "";
                    String requestedByLastName = row[7] != null ? (String) row[7] : "";
                    String reviewedByFirstName = row[8] != null ? (String) row[8] : "";
                    String reviewedByLastName = row[9] != null ? (String) row[9] : "";
                    String type = row[10] != null ? (String) row[10] : "N/A";

                    String initiatedBy = (requestedByFirstName + " " + requestedByLastName).trim();
                    if (initiatedBy.isEmpty()) {
                        initiatedBy = "N/A";
                    }

                    String cancelledBy = (reviewedByFirstName + " " + reviewedByLastName).trim();
                    if (cancelledBy.isEmpty()) {
                        cancelledBy = "N/A";
                    }

                    return PaymentAndFinancialsResponse.CancellationItem.builder()
                            .id(transactionId.toString())
                            .type(type)
                            .dateTime(dateTime)
                            .amount(amount)
                            .paymentMethod(paymentMethodDisplaySupport.toDisplayName(paymentMethod))
                            .reason(reason)
                            .initiatedBy(initiatedBy)
                            .cancelledBy(cancelledBy)
                            .build();
                })
                .collect(Collectors.toList());

        // Apply sorting
        if (sortBy != null && !sortBy.trim().isEmpty()) {
            String sortField = sortBy.trim().toLowerCase();
            Comparator<PaymentAndFinancialsResponse.CancellationItem> comparator = switch (sortField) {
                case "id" -> Comparator.comparing(
                        PaymentAndFinancialsResponse.CancellationItem::getId,
                        Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER));
                case "type" -> Comparator.comparing(
                        PaymentAndFinancialsResponse.CancellationItem::getType,
                        Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER));
                case SORT_KEY_DATETIME, SORT_KEY_DATE_TIME -> Comparator.comparing(
                        PaymentAndFinancialsResponse.CancellationItem::getDateTime,
                        Comparator.nullsLast(Comparator.naturalOrder()));
                case "amount" -> Comparator.comparing(
                        PaymentAndFinancialsResponse.CancellationItem::getAmount,
                        Comparator.nullsLast(Comparator.naturalOrder()));
                case "paymentmethod", "payment_method" -> Comparator.comparing(
                        PaymentAndFinancialsResponse.CancellationItem::getPaymentMethod,
                        Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER));
                default -> Comparator.comparing(
                        PaymentAndFinancialsResponse.CancellationItem::getDateTime,
                        Comparator.nullsLast(Comparator.reverseOrder())); // Default: sort by dateTime descending
            };

            if (sortDirection == Sort.Direction.DESC) {
                comparator = comparator.reversed();
            }

            cancellations.sort(comparator);
        } else {
            // Default sorting: by dateTime descending
            cancellations.sort(Comparator.comparing(
                    PaymentAndFinancialsResponse.CancellationItem::getDateTime,
                    Comparator.nullsLast(Comparator.reverseOrder())));
        }

        // Apply pagination (if page/size are null, return all results for export)
        List<PaymentAndFinancialsResponse.CancellationItem> paginatedCancellations;
        PaginationMetaData metaData;
        if (page == null || size == null) {
            // No pagination - return all results
            paginatedCancellations = cancellations;
            metaData = null;
        } else {
            int pageNumber = (page != null && page > 0) ? page - 1 : 0; // 0-based
            int pageSize = (size != null && size > 0) ? size : 10;
            
            int fromIndex = Math.min(pageNumber * pageSize, cancellations.size());
            int toIndex = Math.min(fromIndex + pageSize, cancellations.size());
            if (fromIndex >= cancellations.size()) {
                paginatedCancellations = new ArrayList<>();
            } else {
                paginatedCancellations = cancellations.subList(fromIndex, toIndex);
            }
            
            metaData = PaginationMetaData.builder()
                    .page(pageNumber + 1)
                    .size(pageSize)
                    .totalPages((int) Math.ceil((double) cancellations.size() / pageSize))
                    .totalRecords((long) cancellations.size())
                    .build();
        }

        return PaymentAndFinancialsResponse.CancellationReport.builder()
                .cancellations(paginatedCancellations)
                .count((long) paginatedCancellations.size())
                .total((long) cancellations.size())
                .metaData(metaData)
                .build();
    }

    /**
     * Generates chargeback (refund) report for a restaurant within a date range.
     * Includes refund transactions with amounts, reasons, and statuses.
     * Supports pagination and sorting.
     *
     * @param restaurantId the UUID of the restaurant
     * @param startDate    start date/time of the period (inclusive)
     * @param endDate      end date/time of the period (inclusive)
     * @param page         page number for pagination (null returns all)
     * @param size         page size for pagination (null returns all)
     * @param sortBy       field to sort by
     * @param sortDirection sort direction
     * @return ChargebackReport with paginated list of refund transactions
     */
    private PaymentAndFinancialsResponse.ChargebackReport getChargebackReport(
            UUID restaurantId, LocalDateTime startDate, LocalDateTime endDate,
            Integer page, Integer size, String sortBy, Sort.Direction sortDirection) {

        List<Object[]> results = refundRepository.getChargebackReport(
                restaurantId, startDate, endDate);

        if (results.isEmpty()) {
            PaginationMetaData metaData = createPaginationMetaData(0, page, size);
            return PaymentAndFinancialsResponse.ChargebackReport.builder()
                    .chargebacks(Collections.emptyList())
                    .count(0L)
                    .total(0L)
                    .metaData(metaData)
                    .build();
        }

        // Map all results to chargeback items first (before sorting and pagination)
        List<PaymentAndFinancialsResponse.ChargebackItem> chargebacks = results.stream()
                .map(row -> {
                    UUID transactionId = (UUID) row[0];
                    String transactionNumber = row[1] != null ? (String) row[1] : "N/A";
                    // Chargeback "dateTime" must always reflect refund.created_at (DB).
                    // Do NOT fall back to "now" as it produces incorrect report dates.
                    LocalDateTime dateTime = row[2] != null ? convertToLocalDateTime(row[2]) : null;
                    BigDecimal amount = row[3] != null ? BigDecimal.valueOf(((Number) row[3]).doubleValue()).setScale(2, RoundingMode.HALF_UP) : BigDecimal.ZERO;
                    String paymentMethod = row[4] != null ? (String) row[4] : "N/A";
                    String reason = row[5] != null ? (String) row[5] : "N/A";
                    String bankStatus = row[6] != null ? (String) row[6] : "Pending";

                    return PaymentAndFinancialsResponse.ChargebackItem.builder()
                            .transactionId(transactionId.toString())
                            .dateTime(dateTime)
                            .amount(amount)
                            .paymentMethod(paymentMethodDisplaySupport.toDisplayName(paymentMethod))
                            .reason(reason)
                            .bankStatus(bankStatus)
                            .build();
                })
                .collect(Collectors.toList());

        // Apply sorting
        if (sortBy != null && !sortBy.trim().isEmpty()) {
            String sortField = sortBy.trim().toLowerCase();
            Comparator<PaymentAndFinancialsResponse.ChargebackItem> comparator = switch (sortField) {
                case "transactionid", "transaction_id" -> Comparator.comparing(
                        PaymentAndFinancialsResponse.ChargebackItem::getTransactionId,
                        Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER));
                case SORT_KEY_DATETIME, SORT_KEY_DATE_TIME, "createdat", "created_at" -> Comparator.comparing(
                        PaymentAndFinancialsResponse.ChargebackItem::getDateTime,
                        Comparator.nullsLast(Comparator.naturalOrder()));
                case "amount" -> Comparator.comparing(
                        PaymentAndFinancialsResponse.ChargebackItem::getAmount,
                        Comparator.nullsLast(Comparator.naturalOrder()));
                case "paymentmethod", "payment_method" -> Comparator.comparing(
                        PaymentAndFinancialsResponse.ChargebackItem::getPaymentMethod,
                        Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER));
                case "bankstatus", "bank_status" -> Comparator.comparing(
                        PaymentAndFinancialsResponse.ChargebackItem::getBankStatus,
                        Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER));
                default -> Comparator.comparing(
                        PaymentAndFinancialsResponse.ChargebackItem::getDateTime,
                        Comparator.nullsLast(Comparator.naturalOrder())); // Default: sort by dateTime (asc; direction applied below)
            };

            if (sortDirection == Sort.Direction.DESC) {
                comparator = comparator.reversed();
            }

            chargebacks.sort(comparator);
        } else {
            // Default sorting: by dateTime descending
            chargebacks.sort(Comparator.comparing(
                    PaymentAndFinancialsResponse.ChargebackItem::getDateTime,
                    Comparator.nullsLast(Comparator.reverseOrder())));
        }

        // Apply pagination (if page/size are null, return all results for export)
        List<PaymentAndFinancialsResponse.ChargebackItem> paginatedChargebacks;
        PaginationMetaData metaData;
        if (page == null || size == null) {
            // No pagination - return all results
            paginatedChargebacks = chargebacks;
            metaData = null;
        } else {
            int pageNumber = (page != null && page > 0) ? page - 1 : 0; // 0-based
            int pageSize = (size != null && size > 0) ? size : 10;
            
            int fromIndex = Math.min(pageNumber * pageSize, chargebacks.size());
            int toIndex = Math.min(fromIndex + pageSize, chargebacks.size());
            if (fromIndex >= chargebacks.size()) {
                paginatedChargebacks = new ArrayList<>();
            } else {
                paginatedChargebacks = chargebacks.subList(fromIndex, toIndex);
            }
            
            metaData = PaginationMetaData.builder()
                    .page(pageNumber + 1)
                    .size(pageSize)
                    .totalPages((int) Math.ceil((double) chargebacks.size() / pageSize))
                    .totalRecords((long) chargebacks.size())
                    .build();
        }

        return PaymentAndFinancialsResponse.ChargebackReport.builder()
                .chargebacks(paginatedChargebacks)
                .count((long) paginatedChargebacks.size())
                .total((long) chargebacks.size())
                .metaData(metaData)
                .build();
    }

    /**
     * Generates wastage report for a restaurant within a date range.
     * Groups wastage by category (items cancelled after being cooked/ready/served).
     * Calculates quantity wasted, total wastage cost, and percentage of total waste.
     * Supports pagination and sorting.
     *
     * @param restaurantId the UUID of the restaurant
     * @param startDate    start date/time of the period (inclusive)
     * @param endDate      end date/time of the period (inclusive)
     * @param page         page number for pagination (null returns all)
     * @param size         page size for pagination (null returns all)
     * @param sortBy       field to sort by
     * @param sortDirection sort direction
     * @return WastageReport with paginated list of wastage items grouped by category
     */
    private PaymentAndFinancialsResponse.WastageReport getWastageReport(
            UUID restaurantId, LocalDateTime startDate, LocalDateTime endDate,
            Integer page, Integer size, String sortBy, Sort.Direction sortDirection) {

        List<Object[]> results = orderedItemRepository.getWastageReport(
                restaurantId, startDate, endDate);

        if (results.isEmpty()) {
            PaginationMetaData metaData = createPaginationMetaData(0, page, size);
            return PaymentAndFinancialsResponse.WastageReport.builder()
                    .wastageItems(Collections.emptyList())
                    .count(0L)
                    .total(0L)
                    .metaData(metaData)
                    .build();
        }

        // Collect category ids for translations (itemId is NULL since we group by category only)
        Set<UUID> categoryIds = results.stream()
                .map(row -> (UUID) row[1])
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        Map<UUID, List<CategoryTranslation>> categoryTranslationsMap = categoryIds.isEmpty()
                ? Collections.emptyMap()
                : categoryTranslationRepository.findAllByCategoryIdIn(new ArrayList<>(categoryIds))
                        .stream()
                        .collect(Collectors.groupingBy(t -> t.getCategory().getId()));

        // Map to DTOs and group by category name to avoid duplicates
        // Note: itemId is now NULL since we group by category only
        // Group by category name to consolidate categories with same name but different IDs
        Map<String, PaymentAndFinancialsResponse.WastageItem> wastageItemsMap = new HashMap<>();
        
        for (Object[] row : results) {
            UUID itemId = (UUID) row[0]; // This will be NULL now
            UUID categoryId = (UUID) row[1];
            Integer quantityWasted = ((Number) row[2]).intValue();
            BigDecimal totalWastageCost = BigDecimal.valueOf(((Number) row[3]).doubleValue())
                    .setScale(2, RoundingMode.HALF_UP);
            LocalDateTime dateTime = row[4] != null ? convertToLocalDateTime(row[4]) : LocalDateTime.now(ZoneOffset.UTC);

            // Since we're grouping by category, itemName will be the category name
            // Note: The query uses parent categories (COALESCE(c.parent_category_id, mcm.category_id))
            // so subcategories are grouped under their parent categories. This is expected behavior.
            String categoryName = getCategoryName(categoryId, categoryTranslationsMap, LocaleContextHolder.getLocale().getLanguage());
            String itemName = categoryName != null ? categoryName : DEFAULT_UNKNOWN_CATEGORY;
            
            // Use category name as key to group duplicates
            String categoryKey = categoryName != null ? categoryName : DEFAULT_UNKNOWN_CATEGORY;

            // Reason is best-effort here – items cancelled after being cooked/ready/served
            String reason = "Cancelled after preparation (status: COOKING/READY/SERVED)";

            // If category already exists, aggregate the values
            if (wastageItemsMap.containsKey(categoryKey)) {
                PaymentAndFinancialsResponse.WastageItem existing = wastageItemsMap.get(categoryKey);
                existing.setQuantityWasted(existing.getQuantityWasted() + quantityWasted);
                existing.setTotalWastageCost(existing.getTotalWastageCost().add(totalWastageCost));
                // Keep the most recent date
                if (dateTime != null && (existing.getDateTime() == null || dateTime.isAfter(existing.getDateTime()))) {
                    existing.setDateTime(dateTime);
                }
            } else {
                wastageItemsMap.put(categoryKey, PaymentAndFinancialsResponse.WastageItem.builder()
                        .itemName(itemName)
                        .category(categoryName)
                        .quantityWasted(quantityWasted)
                        .totalWastageCost(totalWastageCost)
                        .dateTime(dateTime)
                        .reason(reason)
                        .build());
            }
        }
        
        List<PaymentAndFinancialsResponse.WastageItem> wastageItems = new ArrayList<>(wastageItemsMap.values());

        // Calculate total wastage cost across all items for percentage calculation
        BigDecimal totalWastageCost = wastageItems.stream()
                .map(PaymentAndFinancialsResponse.WastageItem::getTotalWastageCost)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // Calculate and set percentage of total waste for each item
        for (PaymentAndFinancialsResponse.WastageItem item : wastageItems) {
            BigDecimal percentage = BigDecimal.ZERO;
            if (totalWastageCost.compareTo(BigDecimal.ZERO) > 0 
                    && item.getTotalWastageCost() != null) {
                percentage = item.getTotalWastageCost()
                        .divide(totalWastageCost, 4, RoundingMode.HALF_UP)
                        .multiply(BigDecimal.valueOf(100))
                        .setScale(2, RoundingMode.HALF_UP);
            }
            item.setPercentageOfTotalWaste(percentage);
        }

        // Apply sorting
        if (sortBy != null && !sortBy.trim().isEmpty()) {
            String sortField = sortBy.trim().toLowerCase();
            Comparator<PaymentAndFinancialsResponse.WastageItem> comparator = switch (sortField) {
                case "itemname", "item_name" -> Comparator.comparing(
                        PaymentAndFinancialsResponse.WastageItem::getItemName,
                        Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER));
                case "category" -> Comparator.comparing(
                        PaymentAndFinancialsResponse.WastageItem::getCategory,
                        Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER));
                case "quantitywasted", "quantity_wasted" -> Comparator.comparing(
                        PaymentAndFinancialsResponse.WastageItem::getQuantityWasted,
                        Comparator.nullsLast(Comparator.naturalOrder()));
                case "totalwastagecost", "total_wastage_cost" -> Comparator.comparing(
                        PaymentAndFinancialsResponse.WastageItem::getTotalWastageCost,
                        Comparator.nullsLast(Comparator.naturalOrder()));
                case SORT_KEY_DATETIME, SORT_KEY_DATE_TIME -> Comparator.comparing(
                        PaymentAndFinancialsResponse.WastageItem::getDateTime,
                        Comparator.nullsLast(Comparator.naturalOrder()));
                case "reason" -> Comparator.comparing(
                        PaymentAndFinancialsResponse.WastageItem::getReason,
                        Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER));
                default -> Comparator.comparing(
                        PaymentAndFinancialsResponse.WastageItem::getTotalWastageCost,
                        Comparator.nullsLast(Comparator.naturalOrder())); // default: by cost
            };

            if (sortDirection == Sort.Direction.DESC) {
                comparator = comparator.reversed();
            }

            wastageItems.sort(comparator);
        } else {
            // Default sorting: by totalWastageCost descending
            wastageItems.sort(Comparator.comparing(
                    PaymentAndFinancialsResponse.WastageItem::getTotalWastageCost,
                    Comparator.nullsLast(Comparator.reverseOrder())));
        }

        // Apply pagination (if page/size are null, return all results)
        List<PaymentAndFinancialsResponse.WastageItem> paginatedItems;
        PaginationMetaData metaData;
        if (page == null || size == null) {
            paginatedItems = wastageItems;
            metaData = null;
        } else {
            int pageNumber = (page != null && page > 0) ? page - 1 : 0; // 0-based
            int pageSize = (size != null && size > 0) ? size : 10;
            
            int fromIndex = Math.min(pageNumber * pageSize, wastageItems.size());
            int toIndex = Math.min(fromIndex + pageSize, wastageItems.size());
            if (fromIndex >= wastageItems.size()) {
                paginatedItems = new ArrayList<>();
            } else {
                paginatedItems = wastageItems.subList(fromIndex, toIndex);
            }
            
            metaData = PaginationMetaData.builder()
                    .page(pageNumber + 1)
                    .size(pageSize)
                    .totalPages((int) Math.ceil((double) wastageItems.size() / pageSize))
                    .totalRecords((long) wastageItems.size())
                    .build();
        }

        return PaymentAndFinancialsResponse.WastageReport.builder()
                .wastageItems(paginatedItems)
                .count((long) paginatedItems.size())
                .total((long) wastageItems.size())
                .metaData(metaData)
                .build();
    }

    /**
     * Fetches cashier shifts report with error handling.
     * Returns an empty report if an error occurs instead of failing the entire request.
     */
    private CashierShiftListResponse getCashierShiftsReportWithErrorHandling(
            UUID restaurantId,
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
            LocalDateTime defaultStartDateTime,
            LocalDateTime defaultEndDateTime,
            Locale locale) {
        try {
            // Use the same date range as the main report if shifts date range is not provided
            LocalDateTime shiftsStartDateTime = shiftsStartDate != null ? shiftsStartDate : defaultStartDateTime;
            LocalDateTime shiftsEndDateTime = shiftsEndDate != null ? shiftsEndDate : defaultEndDateTime;
            return getCashierShiftsReport(restaurantId, shiftsPage, shiftsSize, shiftsSortBy, shiftsSortDirection,
                    shiftsStatus, shiftsCashDrawerId, shiftsCashierId, shiftsSearch,
                    shiftsStartDateTime, shiftsEndDateTime, locale);
        } catch (Exception e) {
            logger.error("Error fetching cashier shifts report for restaurant: {}", restaurantId, e);
            // Return empty shifts report instead of failing the entire request
            PaginationMetaData emptyPagination = PaginationMetaData.builder()
                    .page(shiftsPage != null && shiftsPage > 0 ? shiftsPage : 1)
                    .size(shiftsSize != null && shiftsSize > 0 ? shiftsSize : 10)
                    .totalRecords(0L)
                    .totalPages(0)
                    .build();
            return CashierShiftListResponse.builder()
                    .shifts(Collections.emptyList())
                    .pagination(emptyPagination)
                    .build();
        }
    }

    /**
     * Generates cashier shifts report for a restaurant with filtering and pagination.
     * Includes shift details, cash drawer info, cashier info, balances, and discrepancy information.
     * Supports filtering by status, cash drawer, cashier, search term, and date range.
     *
     * @param restaurantId the UUID of the restaurant
     * @param page         page number for pagination
     * @param size         page size for pagination
     * @param sortBy       field to sort by (mapped to entity field names)
     * @param sortDirection sort direction
     * @param status       optional filter by shift status
     * @param cashDrawerId optional filter by cash drawer ID
     * @param cashierId    optional filter by cashier ID
     * @param search       optional search term
     * @param startDate    optional start date for date range filter
     * @param endDate      optional end date for date range filter
     * @return CashierShiftListResponse with paginated list of cashier shifts
     */
    private CashierShiftListResponse getCashierShiftsReport(
            UUID restaurantId,
            Integer page,
            Integer size,
            String sortBy,
            Sort.Direction sortDirection,
            String status,
            UUID cashDrawerId,
            UUID cashierId,
            String search,
            LocalDateTime startDate,
            LocalDateTime endDate,
            Locale locale) {

        // Determine which internal statuses to include
        List<ShiftStatus> statusFilters;
        if (status == null || status.isBlank() || status.equalsIgnoreCase("ALL")) {
            statusFilters = List.of(ShiftStatus.OPEN, ShiftStatus.CLOSED, ShiftStatus.APPROVED);
        } else if (status.equalsIgnoreCase("OPEN")) {
            statusFilters = List.of(ShiftStatus.OPEN);
        } else if (status.equalsIgnoreCase("CLOSED")) {
            // "Closed" in the UI includes internally CLOSED and APPROVED
            statusFilters = List.of(ShiftStatus.CLOSED, ShiftStatus.APPROVED);
        } else {
            // Invalid status, return empty result
            PaginationMetaData pagination = PaginationMetaData.builder()
                    .page(page != null && page > 0 ? page : 1)
                    .size(size != null && size > 0 ? size : 10)
                    .totalRecords(0L)
                    .totalPages(0)
                    .build();
            return CashierShiftListResponse.builder()
                    .shifts(Collections.emptyList())
                    .pagination(pagination)
                    .build();
        }

        // Normalize search term (trim and set to null if empty)
        String searchTerm = (search != null && !search.trim().isEmpty()) ? search.trim() : null;

        // Apply default pagination values if not provided
        int pageNumber = (page != null && page > 0) ? page : 1;
        int pageSize = (size != null && size > 0) ? size : 10;
        
        // Ensure pageSize has a reasonable maximum limit
        if (pageSize > 100) {
            pageSize = 100;
        }

        // Note: The repository native query has a hardcoded ORDER BY cs.started_at DESC
        // Dynamic sorting via Pageable is limited with native queries, so we use unsorted Pageable
        // The query will always return results sorted by started_at DESC
        Pageable pageable = PageRequest.of(pageNumber - 1, pageSize);

        // Convert enum collection to string names for native query compatibility
        List<String> statusFilterStrings = statusFilters.stream()
                .map(Enum::name)
                .collect(Collectors.toList());

        // Use the repository method that handles all filters at DB level
        Page<CashierShift> shiftPage = cashierShiftRepository.findByRestaurantIdWithFilters(
                restaurantId,
                cashDrawerId,
                cashierId,
                statusFilterStrings,
                startDate,
                endDate,
                searchTerm,
                pageable
        );

        // Map APPROVED status to CLOSED for UI display (both represent closed shifts)
        List<CashierShiftResponse> shiftResponses = shiftPage.getContent().stream()
                .map(shift -> {
                    try {
                        CashierShiftResponse response = buildCashierShiftResponse(shift);
                        if (response == null) {
                            return null;
                        }
                        // Map APPROVED to CLOSED for consistency with manager listing
                        if (response.getStatus() == ShiftStatus.APPROVED) {
                            response.setStatus(ShiftStatus.CLOSED);
                        }
                        return response;
                    } catch (Exception e) {
                        logger.error("Error building cashier shift response for shift: {}", shift != null ? shift.getId() : "null", e);
                        return null;
                    }
                })
                .filter(Objects::nonNull)
                .collect(Collectors.toList());

        // Build pagination metadata
        PaginationMetaData pagination = PaginationMetaData.builder()
                .page(pageNumber)
                .size(pageSize)
                .totalRecords(shiftPage.getTotalElements())
                .totalPages(shiftPage.getTotalPages())
                .build();

        return CashierShiftListResponse.builder()
                .shifts(shiftResponses)
                .pagination(pagination)
                .build();
    }

    /**
     * Builds a CashierShiftResponse DTO from a CashierShift entity.
     * Includes cashier name, approved by name, shift name, and all shift details.
     *
     * @param shift the CashierShift entity to convert
     * @return CashierShiftResponse with all shift details, or null if shift is null
     */
    private CashierShiftResponse buildCashierShiftResponse(CashierShift shift) {
        if (shift == null) {
            return null;
        }
        
        String cashierName = null;
        if (shift.getCashier() != null) {
            String firstName = shift.getCashier().getFirstName() != null ? shift.getCashier().getFirstName() : "";
            String lastName = shift.getCashier().getLastName() != null ? shift.getCashier().getLastName() : "";
            cashierName = (firstName + " " + lastName).trim();
            if (cashierName.isEmpty()) {
                cashierName = null;
            }
        }
        
        String approvedByName = null;
        if (shift.getApprovedBy() != null) {
            String firstName = shift.getApprovedBy().getFirstName() != null ? shift.getApprovedBy().getFirstName() : "";
            String lastName = shift.getApprovedBy().getLastName() != null ? shift.getApprovedBy().getLastName() : "";
            approvedByName = (firstName + " " + lastName).trim();
            if (approvedByName.isEmpty()) {
                approvedByName = null;
            }
        }
        
        return CashierShiftResponse.builder()
                .id(shift.getId())
                .cashDrawerId(shift.getCashDrawer() != null ? shift.getCashDrawer().getId() : null)
                .cashDrawerName(resolveCashDrawerNameForReport(shift.getCashDrawer()))
                .cashierId(shift.getCashier() != null ? shift.getCashier().getId() : null)
                .cashierName(cashierName)
                .restaurantId(shift.getRestaurant() != null ? shift.getRestaurant().getId() : null)
                .shiftId(shift.getShift() != null ? shift.getShift().getId() : null)
                .shiftName(shift.getShift() != null ? getShiftNameFromShift(shift.getShift()) : null)
                .status(shift.getStatus())
                .openingBalance(shift.getOpeningBalance())
                .closingBalance(shift.getClosingBalance())
                .expectedClosingBalance(shift.getExpectedClosingBalance())
                .discrepancyAmount(shift.getDiscrepancyAmount())
                .discrepancyReason(shift.getDiscrepancyReason())
                .startedAt(shift.getStartedAt() != null ? shift.getStartedAt().toLocalDateTime() : null)
                .closedAt(shift.getClosedAt() != null ? shift.getClosedAt().toLocalDateTime() : null)
                .approvedBy(shift.getApprovedBy() != null ? shift.getApprovedBy().getId() : null)
                .approvedByName(approvedByName)
                .approvedAt(shift.getApprovedAt() != null ? shift.getApprovedAt().toLocalDateTime() : null)
                .createdAt(shift.getCreatedAt() != null ? shift.getCreatedAt().toLocalDateTime() : null)
                .updatedAt(shift.getUpdatedAt() != null ? shift.getUpdatedAt().toLocalDateTime() : null)
                .build();
    }

    private String resolveCashDrawerNameForReport(com.gulfnet.shared_library.entity.CashDrawer drawer) {
        if (drawer == null) {
            return null;
        }
        List<CashDrawerTranslation> list =
                cashDrawerTranslationRepository.findAllByCashDrawer_IdOrderByLanguageCodeAsc(drawer.getId());
        String name = CashDrawerTranslationUtil.resolveName(list, LocaleContextHolder.getLocale());
        return name.isEmpty() ? null : name;
    }

    /**
     * Maps frontend sort field names to entity field names for cashier shifts.
     * Handles various naming conventions (camelCase, snake_case, lowercase).
     *
     * @param sortBy the frontend sort field name
     * @return corresponding entity field name (defaults to "startedAt" if unknown)
     */
    private String mapSortField(String sortBy) {
        // Map frontend sort field names to entity field names
        String lowerSortBy = sortBy.toLowerCase();
        return switch (lowerSortBy) {
            case "startedat", "started_at" -> "startedAt";
            case "closedat", "closed_at" -> "closedAt";
            case "cashiername", "cashier_name" -> "cashier.firstName"; // Note: complex sorting may need custom query
            case "cashdrawername", "cash_drawer_name" -> "cashDrawer.name";
            case "status" -> "status";
            case "openingbalance", "opening_balance" -> "openingBalance";
            case "closingbalance", "closing_balance" -> "closingBalance";
            case "discrepancyamount", "discrepancy_amount" -> "discrepancyAmount";
            default -> "startedAt"; // default fallback
        };
    }

    // ========== Discount Offers Applied Report Methods (for CSV Export) ==========

    /**
     * Get total count of transactions with discounts for CSV export.
     * This method is used by DiscountOfferReportDataProvider.
     */
    public long getDiscountOffersTotalCount(UUID restaurantId, Collection<TransactionStatus> transactionStatuses,
                                           LocalDateTime startDateTime, LocalDateTime endDateTime) {
        Pageable pageable = Pageable.unpaged();
        Page<Transaction> page = transactionRepository.findTransactionsWithDiscounts(
                restaurantId, transactionStatuses,
                startDateTime.atOffset(java.time.ZoneOffset.UTC), endDateTime.atOffset(java.time.ZoneOffset.UTC), pageable);
        return page.getTotalElements();
    }

    /**
     * Fetch a page of transactions with discounts for CSV export.
     * This method is used by DiscountOfferReportDataProvider for streaming.
     */
    public List<Transaction> fetchDiscountOffersPage(int page, int pageSize, UUID restaurantId,
                                                    Collection<TransactionStatus> transactionStatuses,
                                                    LocalDateTime startDateTime, LocalDateTime endDateTime) {
        Pageable pageable = PageRequest.of(page, pageSize);
        Page<Transaction> transactionsPage = transactionRepository.findTransactionsWithDiscounts(
                restaurantId, transactionStatuses,
                startDateTime.atOffset(java.time.ZoneOffset.UTC), endDateTime.atOffset(java.time.ZoneOffset.UTC), pageable);
        return transactionsPage.getContent();
    }

    /**
     * Get all transactions with discounts for summary calculation.
     * This method is used by DiscountOfferReportDataProvider.
     */
    public List<Transaction> getAllDiscountOffersTransactions(UUID restaurantId,
                                                             Collection<TransactionStatus> transactionStatuses,
                                                             LocalDateTime startDateTime, LocalDateTime endDateTime) {
        Pageable pageable = Pageable.unpaged();
        Page<Transaction> transactionsPage = transactionRepository.findTransactionsWithDiscounts(
                restaurantId, transactionStatuses,
                startDateTime.atOffset(java.time.ZoneOffset.UTC), endDateTime.atOffset(java.time.ZoneOffset.UTC), pageable);
        return transactionsPage.getContent();
    }

    /**
     * Convert Transaction to DiscountOfferReportResponse.
     * Delegates to TransactionServiceImpl to reuse existing logic.
     * This method is used by DiscountOfferReportDataProvider.
     */
    public DiscountOfferReportResponse convertTransactionToDiscountOfferResponse(Transaction transaction) {
        return transactionService.convertToDiscountOfferReportResponse(transaction);
    }

    /**
     * Calculate summary statistics for discounts and offers report.
     * Delegates to TransactionServiceImpl to reuse existing logic.
     * This method is used by DiscountOfferReportDataProvider.
     */
    public DiscountOfferReportListDto.SummaryStatistics calculateDiscountOffersSummaryStatistics(List<Transaction> transactions) {
        return transactionService.calculateSummaryStatistics(transactions);
    }

    // ========== Inner Class: Discount Offers Report Data Provider ==========
    // This implements ReportDataProvider interface for CSV export
    // All report logic is in ReportsServiceImpl, no separate file needed

    @Component("discountOfferReportDataProvider")
    public class DiscountOfferReportDataProvider implements 
            com.gulfnet.shared_library.service.export.ReportDataProvider<DiscountOfferReportResponse>,
            com.gulfnet.shared_library.service.export.ReportTypeProvider {

        private static final DateTimeFormatter DATETIME_FORMATTER = DATETIME_FORMAT;

        /**
         * Gets the total count of discount offer report records matching the specified filters.
         *
         * @param filters Map containing filter criteria including restaurant ID, transaction statuses, start date/time, and end date/time
         * @return Total count of discount offer records matching the filters
         */
        @Override
        public long getTotalCount(Map<String, Object> filters) {
            UUID restaurantId = (UUID) filters.get(FILTER_KEY_RESTAURANT_ID);
            @SuppressWarnings("unchecked")
            Collection<TransactionStatus> transactionStatuses = (Collection<TransactionStatus>) filters.get(FILTER_KEY_TRANSACTION_STATUSES);
            LocalDateTime startDateTime = (LocalDateTime) filters.get(FILTER_KEY_START_DATE_TIME);
            LocalDateTime endDateTime = (LocalDateTime) filters.get(FILTER_KEY_END_DATE_TIME);

            // Use ReportsServiceImpl methods - all report logic is here
            return ReportsServiceImpl.this.getDiscountOffersTotalCount(
                    restaurantId, transactionStatuses, startDateTime, endDateTime);
        }

        /**
         * Fetches a paginated page of discount offer report records matching the specified filters.
         *
         * @param page Page number (0-based)
         * @param pageSize Number of records per page
         * @param filters Map containing filter criteria including restaurant ID, transaction statuses, start date/time, and end date/time
         * @return List of discount offer report responses for the requested page
         */
        @Override
        public List<DiscountOfferReportResponse> fetchPage(int page, int pageSize, Map<String, Object> filters) {
            UUID restaurantId = (UUID) filters.get(FILTER_KEY_RESTAURANT_ID);
            @SuppressWarnings("unchecked")
            Collection<TransactionStatus> transactionStatuses = (Collection<TransactionStatus>) filters.get(FILTER_KEY_TRANSACTION_STATUSES);
            LocalDateTime startDateTime = (LocalDateTime) filters.get(FILTER_KEY_START_DATE_TIME);
            LocalDateTime endDateTime = (LocalDateTime) filters.get(FILTER_KEY_END_DATE_TIME);

            // Use ReportsServiceImpl methods - all report logic is here
            List<Transaction> transactions = ReportsServiceImpl.this.fetchDiscountOffersPage(
                    page, pageSize, restaurantId, transactionStatuses, startDateTime, endDateTime);

            return transactions.stream()
                    .map(ReportsServiceImpl.this::convertTransactionToDiscountOfferResponse)
                    .collect(Collectors.toList());
        }

        /**
         * Gets the column headers for the discount offer report CSV export.
         *
         * @param locale Locale for localization (currently not used for headers)
         * @return Array of column header strings
         */
        @Override
        public String[] getColumnHeaders(String locale) {
            return new String[]{
                    "Order Number",
                    "Transaction Number",
                    "Transaction Date",
                    messageUtil.getMessage("reports.export.header.discountCode"),
                    messageUtil.getMessage("csv.dashboard.header.discount.type"),
                    "Discount Value",
                    "Discount Amount",
                    "Additional Discount Value",
                    "Additional Discount Type",
                    "Additional Discount Amount",
                    "Additional Discount Reason",
                    "Subtotal",
                    "Total Amount",
                    "Total Discount Amount"
            };
        }

        /**
         * Converts a discount offer report response to a CSV row array.
         *
         * @param data Discount offer report response to convert
         * @param locale Locale for localization (currently not used)
         * @return Array of strings representing a CSV row
         */
        @Override
        public String[] convertToRow(DiscountOfferReportResponse data, String locale) {
            return new String[]{
                    data.getOrderNumber() != null ? data.getOrderNumber() : "",
                    data.getTransactionNumber() != null ? data.getTransactionNumber() : "",
                    data.getTransactionDateTime() != null ?
                            data.getTransactionDateTime().format(DATETIME_FORMATTER) : "",
                    data.getDiscountCode() != null ? data.getDiscountCode() : "",
                    data.getDiscountType() != null ? data.getDiscountType().toString() : "",
                    data.getDiscountValue() != null ? data.getDiscountValue().toString() : "",
                    data.getDiscountAmount() != null ? data.getDiscountAmount().toString() : "",
                    data.getAdditionalDiscountValue() != null ? data.getAdditionalDiscountValue().toString() : "",
                    data.getAdditionalDiscountType() != null ? data.getAdditionalDiscountType().toString() : "",
                    data.getAdditionalDiscountAmount() != null ? data.getAdditionalDiscountAmount().toString() : "",
                    data.getAdditionalDiscountReason() != null ? data.getAdditionalDiscountReason() : "",
                    data.getSubTotal() != null ? data.getSubTotal().toString() : "",
                    data.getTotalAmount() != null ? data.getTotalAmount().toString() : "",
                    data.getTotalDiscountAmount() != null ? data.getTotalDiscountAmount().toString() : ""
            };
        }

        /**
         * Gets metadata for the discount offer report, including report title, restaurant name, export date, and date range.
         *
         * @param filters Map containing filter criteria including restaurant ID, date, start date/time, and end date/time
         * @param locale Locale for localization of restaurant names
         * @return Map of metadata key-value pairs
         */
        @Override
        public Map<String, String> getReportMetadata(Map<String, Object> filters, String locale) {
            Map<String, String> metadata = new LinkedHashMap<>();
            UUID restaurantId = (UUID) filters.get(FILTER_KEY_RESTAURANT_ID);
            LocalDate date = (LocalDate) filters.get("date");
            LocalDateTime startDateTime = (LocalDateTime) filters.get(FILTER_KEY_START_DATE_TIME);
            LocalDateTime endDateTime = (LocalDateTime) filters.get(FILTER_KEY_END_DATE_TIME);

            metadata.put("DISCOUNTS AND OFFERS APPLIED REPORT", "");

            if (restaurantId != null) {
                Restaurant restaurant = restaurantRepository.findById(restaurantId).orElse(null);
                if (restaurant != null && restaurant.getTranslations() != null) {
                    String restaurantName = restaurant.getTranslations().stream()
                            .filter(t -> t.getLanguageCode() != null && t.getLanguageCode().equals(locale))
                            .map(t -> t.getName())
                            .findFirst()
                            .orElse(restaurant.getRestaurantCode() != null ? restaurant.getRestaurantCode() : "");
                    metadata.put(messageUtil.getMessage("reports.export.label.restaurant"), restaurantName);
                } else if (restaurant != null) {
                    metadata.put(messageUtil.getMessage("reports.export.label.restaurant"),
                            restaurant.getRestaurantCode() != null ? restaurant.getRestaurantCode() : "");
                }
            }

            metadata.put(messageUtil.getMessage("csv.export.date"),
                    LocalDateTime.now(ZoneOffset.UTC).format(DATETIME_FORMATTER));

            if (date != null) {
                metadata.put("Report Date", date.toString());
            } else {
                if (startDateTime != null) {
                    metadata.put(messageUtil.getMessage("reports.export.header.startDate"),
                            startDateTime.format(DATETIME_FORMATTER));
                }
                if (endDateTime != null) {
                    metadata.put(messageUtil.getMessage("reports.export.header.endDate"),
                            endDateTime.format(DATETIME_FORMATTER));
                }
            }

            return metadata;
        }

        /**
         * Gets summary statistics for the discount offer report, including total transactions, total discount amounts, and total revenue.
         *
         * @param filters Map containing filter criteria including restaurant ID, transaction statuses, start date/time, and end date/time
         * @param locale Locale for localization (currently not used)
         * @return Map of summary statistics key-value pairs
         */
        @Override
        public Map<String, String> getSummaryStatistics(Map<String, Object> filters, String locale) {
            UUID restaurantId = (UUID) filters.get(FILTER_KEY_RESTAURANT_ID);
            @SuppressWarnings("unchecked")
            Collection<TransactionStatus> transactionStatuses = (Collection<TransactionStatus>) filters.get(FILTER_KEY_TRANSACTION_STATUSES);
            LocalDateTime startDateTime = (LocalDateTime) filters.get(FILTER_KEY_START_DATE_TIME);
            LocalDateTime endDateTime = (LocalDateTime) filters.get(FILTER_KEY_END_DATE_TIME);

            // Use ReportsServiceImpl methods - all report logic is here
            List<Transaction> transactions = ReportsServiceImpl.this.getAllDiscountOffersTransactions(
                    restaurantId, transactionStatuses, startDateTime, endDateTime);

            DiscountOfferReportListDto.SummaryStatistics summary = 
                    ReportsServiceImpl.this.calculateDiscountOffersSummaryStatistics(transactions);

            Map<String, String> summaryMap = new LinkedHashMap<>();
            summaryMap.put("Total Orders with Discounts", String.valueOf(summary.getTotalOrdersWithDiscounts()));
            summaryMap.put("Total Discount Amount", summary.getTotalDiscountAmount() != null ?
                    summary.getTotalDiscountAmount().toString() : "0.00");
            summaryMap.put("Total Additional Discount Amount", summary.getTotalAdditionalDiscountAmount() != null ?
                    summary.getTotalAdditionalDiscountAmount().toString() : "0.00");
            summaryMap.put("Total Combined Discount Amount", summary.getTotalCombinedDiscountAmount() != null ?
                    summary.getTotalCombinedDiscountAmount().toString() : "0.00");
            summaryMap.put("Orders with Order-Level Discount", String.valueOf(summary.getOrdersWithOrderLevelDiscount()));
            summaryMap.put("Orders with Additional Discount", String.valueOf(summary.getOrdersWithAdditionalDiscount()));
            summaryMap.put("Orders with Both Discounts", String.valueOf(summary.getOrdersWithBothDiscounts()));

            return summaryMap;
        }

        @Override
        public boolean supportsReportType(com.gulfnet.shared_library.enums.ReportType reportType) {
            return reportType == com.gulfnet.shared_library.enums.ReportType.DISCOUNTS_OFFERS;
        }
    }

    // ========== Itemized Sales Report Methods (for CSV Export) ==========

    /**
     * Get total count of itemized sales items for CSV export.
     */
    public long getItemizedSalesTotalCount(UUID restaurantId, LocalDateTime startDateTime, LocalDateTime endDateTime) {
        List<Object[]> results = orderedItemRepository.getItemizedSalesReport(restaurantId, startDateTime, endDateTime);
        return results.size();
    }

    /**
     * Fetch a page of itemized sales items for CSV export.
     */
    public List<ReportsOverviewResponse.ItemizedSalesItem> fetchItemizedSalesPage(int page, int pageSize, 
                                                                                  UUID restaurantId, 
                                                                                  LocalDateTime startDateTime, 
                                                                                  LocalDateTime endDateTime, 
                                                                                  String locale) {
        ReportsOverviewResponse.ItemizedSalesReport report = getItemizedSalesReport(
                restaurantId, startDateTime, endDateTime, page + 1, pageSize, null, null, locale);
        return report.getItems() != null ? report.getItems() : Collections.emptyList();
    }

    // ========== Table-wise Sales Report Methods (for CSV Export) ==========

    /**
     * Get total count of table-wise sales items for CSV export.
     */
    public long getTableWiseSalesTotalCount(UUID restaurantId, LocalDateTime startDateTime, LocalDateTime endDateTime) {
        List<Object[]> results = orderRepository.getTableWiseSalesReport(restaurantId, startDateTime, endDateTime);
        return results.size();
    }

    /**
     * Fetch a page of table-wise sales items for CSV export.
     */
    public List<ReportsOverviewResponse.TableWiseSalesItem> fetchTableWiseSalesPage(int page, int pageSize,
                                                                                     UUID restaurantId,
                                                                                     LocalDateTime startDateTime,
                                                                                     LocalDateTime endDateTime) {
        ReportsOverviewResponse.TableWiseSalesReport report = getTableWiseSalesReport(
                restaurantId, startDateTime, endDateTime, page + 1, pageSize, null, null);
        return report.getTables() != null ? report.getTables() : Collections.emptyList();
    }

    // ========== Discounts & Promotions Report Methods (for CSV Export) ==========

    /**
     * Get total count of discounts & promotions items for CSV export.
     */
    public long getDiscountsPromotionsTotalCount(UUID restaurantId, LocalDateTime startDateTime, LocalDateTime endDateTime) {
        List<Object[]> results = orderRepository.getDiscountsPromotionsReport(restaurantId, startDateTime, endDateTime);
        return results.size();
    }

    /**
     * Fetch a page of discounts & promotions items for CSV export.
     */
    public List<ReportsOverviewResponse.DiscountPromotionItem> fetchDiscountsPromotionsPage(int page, int pageSize,
                                                                                              UUID restaurantId,
                                                                                              LocalDateTime startDateTime,
                                                                                              LocalDateTime endDateTime) {
        ReportsOverviewResponse.DiscountsPromotionsReport report = getDiscountsPromotionsReport(
                restaurantId, startDateTime, endDateTime, page + 1, pageSize, null, null);
        return report.getDiscounts() != null ? report.getDiscounts() : Collections.emptyList();
    }

    // ========== Payment Types Breakdown Report Methods (for CSV Export) ==========

    /**
     * Get total count of payment types for CSV export.
     */
    public long getPaymentTypesBreakdownTotalCount(UUID restaurantId, LocalDateTime startDateTime, LocalDateTime endDateTime) {
        List<ReportsOverviewResponse.PaymentTypeBreakdown> breakdown = getPaymentTypesBreakdown(
                restaurantId, startDateTime, endDateTime);
        return breakdown.size();
    }

    /**
     * Fetch all payment types breakdown for CSV export (small dataset, no pagination needed).
     */
    public List<ReportsOverviewResponse.PaymentTypeBreakdown> getAllPaymentTypesBreakdown(UUID restaurantId,
                                                                                          LocalDateTime startDateTime,
                                                                                          LocalDateTime endDateTime) {
        return getPaymentTypesBreakdown(restaurantId, startDateTime, endDateTime);
    }

    // ========== Inner Classes: Report Data Providers ==========

    @Component("itemizedSalesReportDataProvider")
    public class ItemizedSalesReportDataProvider implements
            com.gulfnet.shared_library.service.export.ReportDataProvider<ReportsOverviewResponse.ItemizedSalesItem>,
            com.gulfnet.shared_library.service.export.ReportTypeProvider {

        /**
         * Gets the total count of itemized sales report records matching the specified filters.
         *
         * @param filters Map containing filter criteria including restaurant ID, start date/time, and end date/time
         * @return Total count of itemized sales records matching the filters
         */
        @Override
        public long getTotalCount(Map<String, Object> filters) {
            UUID restaurantId = (UUID) filters.get(FILTER_KEY_RESTAURANT_ID);
            LocalDateTime startDateTime = (LocalDateTime) filters.get(FILTER_KEY_START_DATE_TIME);
            LocalDateTime endDateTime = (LocalDateTime) filters.get(FILTER_KEY_END_DATE_TIME);
            return ReportsServiceImpl.this.getItemizedSalesTotalCount(restaurantId, startDateTime, endDateTime);
        }

        /**
         * Fetches a paginated page of itemized sales report records matching the specified filters.
         *
         * @param page Page number (0-based)
         * @param pageSize Number of records per page
         * @param filters Map containing filter criteria including restaurant ID, start date/time, end date/time, and locale
         * @return List of itemized sales report responses for the requested page
         */
        @Override
        public List<ReportsOverviewResponse.ItemizedSalesItem> fetchPage(int page, int pageSize, Map<String, Object> filters) {
            UUID restaurantId = (UUID) filters.get(FILTER_KEY_RESTAURANT_ID);
            LocalDateTime startDateTime = (LocalDateTime) filters.get(FILTER_KEY_START_DATE_TIME);
            LocalDateTime endDateTime = (LocalDateTime) filters.get(FILTER_KEY_END_DATE_TIME);
            String locale = (String) filters.getOrDefault("locale", "en");
            return ReportsServiceImpl.this.fetchItemizedSalesPage(page, pageSize, restaurantId, startDateTime, endDateTime, locale);
        }

        /**
         * Gets the column headers for the itemized sales report CSV export.
         *
         * @param locale Locale for localization (currently not used for headers)
         * @return Array of column header strings
         */
        @Override
        public String[] getColumnHeaders(String locale) {
            Locale localeObj = Locale.forLanguageTag(locale != null ? locale : "en");
            return new String[]{
                    messageUtil.getMessage("csv.dashboard.header.item.name", localeObj),
                    messageUtil.getMessage("csv.dashboard.header.item.code", localeObj),
                    messageUtil.getMessage("csv.dashboard.header.category", localeObj),
                    messageUtil.getMessage("reports.export.header.quantitySold", localeObj),
                    messageUtil.getMessage("reports.export.header.unitPrice", localeObj),
                    messageUtil.getMessage("csv.dashboard.metric.total.sales", localeObj),
                    messageUtil.getMessage("reports.export.header.percentageOfTotalSales", localeObj)
            };
        }

        /**
         * Converts an itemized sales item to a CSV row array.
         *
         * @param data Itemized sales item to convert
         * @param locale Locale for localization (currently not used)
         * @return Array of strings representing a CSV row
         */
        @Override
        public String[] convertToRow(ReportsOverviewResponse.ItemizedSalesItem data, String locale) {
            return new String[]{
                    data.getItemName() != null ? data.getItemName() : "",
                    data.getItemCode() != null ? data.getItemCode() : "",
                    data.getCategory() != null ? data.getCategory() : "",
                    data.getQuantitySold() != null ? String.valueOf(data.getQuantitySold()) : "0",
                    data.getUnitPrice() != null ? data.getUnitPrice().toString() : "0.00",
                    data.getTotalSales() != null ? data.getTotalSales().toString() : "0.00",
                    data.getPercentageOfTotalSales() != null ? String.format("%.2f%%", data.getPercentageOfTotalSales()) : DEFAULT_PERCENTAGE
            };
        }

        /**
         * Gets metadata for the itemized sales report, including report title, restaurant name, export date, and date range.
         *
         * @param filters Map containing filter criteria including restaurant ID, start date/time, and end date/time
         * @param locale Locale for localization (currently not used)
         * @return Map of metadata key-value pairs
         */
        @Override
        public Map<String, String> getReportMetadata(Map<String, Object> filters, String locale) {
            Locale localeObj = Locale.forLanguageTag(locale != null ? locale : "en");
            Map<String, String> metadata = new LinkedHashMap<>();
            UUID restaurantId = (UUID) filters.get(FILTER_KEY_RESTAURANT_ID);
            LocalDateTime startDateTime = (LocalDateTime) filters.get(FILTER_KEY_START_DATE_TIME);
            LocalDateTime endDateTime = (LocalDateTime) filters.get(FILTER_KEY_END_DATE_TIME);

            metadata.put(messageUtil.getMessage("email.scheduled.report.type.ITEMIZED_SALES", localeObj), "");
            if (restaurantId != null) {
                Restaurant restaurant = restaurantRepository.findById(restaurantId).orElse(null);
                if (restaurant != null) {
                    String restaurantName = restaurant.getRestaurantCode() != null ? restaurant.getRestaurantCode() : "";
                    metadata.put(messageUtil.getMessage("reports.export.label.restaurant", localeObj), restaurantName);
                }
            }
            metadata.put(messageUtil.getMessage("csv.export.date", localeObj), LocalDateTime.now(ZoneOffset.UTC).format(DATETIME_FORMAT));
            if (startDateTime != null) {
                metadata.put(messageUtil.getMessage("reports.export.label.dateRange", localeObj), startDateTime.format(DATETIME_FORMAT));
            }
            if (endDateTime != null) {
                metadata.put(messageUtil.getMessage("reports.export.label.dateRange", localeObj), endDateTime.format(DATETIME_FORMAT));
            }
            return metadata;
        }

        /**
         * Gets summary statistics for the itemized sales report, including total items, total quantity sold, and total sales.
         *
         * @param filters Map containing filter criteria including restaurant ID, start date/time, end date/time, and locale
         * @param locale Locale for localization (currently not used)
         * @return Map of summary statistics key-value pairs
         */
        @Override
        public Map<String, String> getSummaryStatistics(Map<String, Object> filters, String locale) {
            Locale localeObj = Locale.forLanguageTag(locale != null ? locale : "en");
            // Calculate summary from all items
            UUID restaurantId = (UUID) filters.get(FILTER_KEY_RESTAURANT_ID);
            LocalDateTime startDateTime = (LocalDateTime) filters.get(FILTER_KEY_START_DATE_TIME);
            LocalDateTime endDateTime = (LocalDateTime) filters.get(FILTER_KEY_END_DATE_TIME);
            String localeStr = (String) filters.getOrDefault("locale", "en");

            ReportsOverviewResponse.ItemizedSalesReport report = getItemizedSalesReport(
                    restaurantId, startDateTime, endDateTime, null, null, null, null, localeStr);
            
            List<ReportsOverviewResponse.ItemizedSalesItem> allItems = report.getItems() != null ? report.getItems() : Collections.emptyList();
            
            BigDecimal totalSales = allItems.stream()
                    .map(ReportsOverviewResponse.ItemizedSalesItem::getTotalSales)
                    .filter(Objects::nonNull)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            
            Integer totalQuantity = allItems.stream()
                    .map(ReportsOverviewResponse.ItemizedSalesItem::getQuantitySold)
                    .filter(Objects::nonNull)
                    .reduce(0, Integer::sum);

            Map<String, String> summary = new LinkedHashMap<>();
            summary.put(messageUtil.getMessage("csv.dashboard.metric.total.items", localeObj), String.valueOf(allItems.size()));
            summary.put(messageUtil.getMessage("reports.csv.summary.totalQuantitySold", localeObj), String.valueOf(totalQuantity));
            summary.put(messageUtil.getMessage("csv.dashboard.metric.total.sales", localeObj), totalSales.toString());
            return summary;
        }

        @Override
        public boolean supportsReportType(com.gulfnet.shared_library.enums.ReportType reportType) {
            return reportType == com.gulfnet.shared_library.enums.ReportType.ITEMIZED_SALES;
        }
    }

    @Component("tableWiseSalesReportDataProvider")
    public class TableWiseSalesReportDataProvider implements
            com.gulfnet.shared_library.service.export.ReportDataProvider<ReportsOverviewResponse.TableWiseSalesItem>,
            com.gulfnet.shared_library.service.export.ReportTypeProvider {

        /**
         * Gets the total count of table-wise sales report records matching the specified filters.
         *
         * @param filters Map containing filter criteria including restaurant ID, start date/time, and end date/time
         * @return Total count of table-wise sales records matching the filters
         */
        @Override
        public long getTotalCount(Map<String, Object> filters) {
            UUID restaurantId = (UUID) filters.get(FILTER_KEY_RESTAURANT_ID);
            LocalDateTime startDateTime = (LocalDateTime) filters.get(FILTER_KEY_START_DATE_TIME);
            LocalDateTime endDateTime = (LocalDateTime) filters.get(FILTER_KEY_END_DATE_TIME);
            return ReportsServiceImpl.this.getTableWiseSalesTotalCount(restaurantId, startDateTime, endDateTime);
        }

        /**
         * Fetches a paginated page of table-wise sales report records matching the specified filters.
         *
         * @param page Page number (0-based)
         * @param pageSize Number of records per page
         * @param filters Map containing filter criteria including restaurant ID, start date/time, and end date/time
         * @return List of table-wise sales report responses for the requested page
         */
        @Override
        public List<ReportsOverviewResponse.TableWiseSalesItem> fetchPage(int page, int pageSize, Map<String, Object> filters) {
            UUID restaurantId = (UUID) filters.get(FILTER_KEY_RESTAURANT_ID);
            LocalDateTime startDateTime = (LocalDateTime) filters.get(FILTER_KEY_START_DATE_TIME);
            LocalDateTime endDateTime = (LocalDateTime) filters.get(FILTER_KEY_END_DATE_TIME);
            return ReportsServiceImpl.this.fetchTableWiseSalesPage(page, pageSize, restaurantId, startDateTime, endDateTime);
        }

        /**
         * Gets the column headers for the table-wise sales report CSV export.
         *
         * @param locale Locale for localization (currently not used for headers)
         * @return Array of column header strings
         */
        @Override
        public String[] getColumnHeaders(String locale) {
            Locale localeObj = Locale.forLanguageTag(locale != null ? locale : "en");
            return new String[]{
                    messageUtil.getMessage("reports.export.header.tableNo", localeObj),
                    messageUtil.getMessage("csv.dashboard.metric.total.orders", localeObj),
                    messageUtil.getMessage("csv.dashboard.metric.total.sales", localeObj),
                    messageUtil.getMessage("reports.export.header.averageOrderValue", localeObj),
                    messageUtil.getMessage("reports.export.header.totalTax", localeObj),
                    messageUtil.getMessage("reports.export.header.totalServiceCharge", localeObj)
            };
        }

        /**
         * Converts a table-wise sales item to a CSV row array.
         *
         * @param data Table-wise sales item to convert
         * @param locale Locale for localization (currently not used)
         * @return Array of strings representing a CSV row
         */
        @Override
        public String[] convertToRow(ReportsOverviewResponse.TableWiseSalesItem data, String locale) {
            return new String[]{
                    data.getTableNo() != null ? data.getTableNo() : "",
                    data.getTotalOrders() != null ? String.valueOf(data.getTotalOrders()) : "0",
                    data.getTotalSales() != null ? data.getTotalSales().toString() : "0.00",
                    data.getAverageOrderValue() != null ? data.getAverageOrderValue().toString() : "0.00",
                    data.getTotalTax() != null ? data.getTotalTax().toString() : "0.00",
                    data.getTotalServiceCharge() != null ? data.getTotalServiceCharge().toString() : "0.00"
            };
        }

        /**
         * Gets metadata for the table-wise sales report, including report title, restaurant name, export date, and date range.
         *
         * @param filters Map containing filter criteria including restaurant ID, start date/time, and end date/time
         * @param locale Locale for localization (currently not used)
         * @return Map of metadata key-value pairs
         */
        @Override
        public Map<String, String> getReportMetadata(Map<String, Object> filters, String locale) {
            Map<String, String> metadata = new LinkedHashMap<>();
            UUID restaurantId = (UUID) filters.get(FILTER_KEY_RESTAURANT_ID);
            LocalDateTime startDateTime = (LocalDateTime) filters.get(FILTER_KEY_START_DATE_TIME);
            LocalDateTime endDateTime = (LocalDateTime) filters.get(FILTER_KEY_END_DATE_TIME);

            metadata.put("TABLE-WISE SALES REPORT", "");
            if (restaurantId != null) {
                Restaurant restaurant = restaurantRepository.findById(restaurantId).orElse(null);
                if (restaurant != null) {
                    String restaurantName = restaurant.getRestaurantCode() != null ? restaurant.getRestaurantCode() : "";
                    metadata.put(messageUtil.getMessage("reports.export.label.restaurant"), restaurantName);
                }
            }
            metadata.put(messageUtil.getMessage("csv.export.date"),
                    LocalDateTime.now(ZoneOffset.UTC).format(DATETIME_FORMAT));
            if (startDateTime != null) {
                metadata.put(messageUtil.getMessage("reports.export.header.startDate"), startDateTime.format(DATETIME_FORMAT));
            }
            if (endDateTime != null) {
                metadata.put(messageUtil.getMessage("reports.export.header.endDate"), endDateTime.format(DATETIME_FORMAT));
            }
            return metadata;
        }

        /**
         * Gets summary statistics for the table-wise sales report, including total tables, total orders, and total sales.
         *
         * @param filters Map containing filter criteria including restaurant ID, start date/time, and end date/time
         * @param locale Locale for localization (currently not used)
         * @return Map of summary statistics key-value pairs
         */
        @Override
        public Map<String, String> getSummaryStatistics(Map<String, Object> filters, String locale) {
            Locale localeObj = Locale.forLanguageTag(locale != null ? locale : "en");
            UUID restaurantId = (UUID) filters.get(FILTER_KEY_RESTAURANT_ID);
            LocalDateTime startDateTime = (LocalDateTime) filters.get(FILTER_KEY_START_DATE_TIME);
            LocalDateTime endDateTime = (LocalDateTime) filters.get(FILTER_KEY_END_DATE_TIME);

            ReportsOverviewResponse.TableWiseSalesReport report = getTableWiseSalesReport(
                    restaurantId, startDateTime, endDateTime, null, null, null, null);
            
            List<ReportsOverviewResponse.TableWiseSalesItem> allTables = report.getTables() != null ? report.getTables() : Collections.emptyList();
            
            BigDecimal totalSales = allTables.stream()
                    .map(ReportsOverviewResponse.TableWiseSalesItem::getTotalSales)
                    .filter(Objects::nonNull)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            
            Long totalOrders = allTables.stream()
                    .map(ReportsOverviewResponse.TableWiseSalesItem::getTotalOrders)
                    .filter(Objects::nonNull)
                    .reduce(0L, Long::sum);

            Map<String, String> summary = new LinkedHashMap<>();
            summary.put(messageUtil.getMessage("reports.csv.summary.totalTables", localeObj), String.valueOf(allTables.size()));
            summary.put(messageUtil.getMessage("csv.dashboard.metric.total.orders", localeObj), String.valueOf(totalOrders));
            summary.put(messageUtil.getMessage("csv.dashboard.metric.total.sales", localeObj), totalSales.toString());
            return summary;
        }

        @Override
        public boolean supportsReportType(com.gulfnet.shared_library.enums.ReportType reportType) {
            return reportType == com.gulfnet.shared_library.enums.ReportType.TABLE_WISE_SALES;
        }
    }

    @Component("discountsPromotionsReportDataProvider")
    public class DiscountsPromotionsReportDataProvider implements
            com.gulfnet.shared_library.service.export.ReportDataProvider<ReportsOverviewResponse.DiscountPromotionItem>,
            com.gulfnet.shared_library.service.export.ReportTypeProvider {

        /**
         * Gets the total count of discounts & promotions report records matching the specified filters.
         *
         * @param filters Map containing filter criteria including restaurant ID, start date/time, and end date/time
         * @return Total count of discounts & promotions records matching the filters
         */
        @Override
        public long getTotalCount(Map<String, Object> filters) {
            UUID restaurantId = (UUID) filters.get(FILTER_KEY_RESTAURANT_ID);
            LocalDateTime startDateTime = (LocalDateTime) filters.get(FILTER_KEY_START_DATE_TIME);
            LocalDateTime endDateTime = (LocalDateTime) filters.get(FILTER_KEY_END_DATE_TIME);
            return ReportsServiceImpl.this.getDiscountsPromotionsTotalCount(restaurantId, startDateTime, endDateTime);
        }

        /**
         * Fetches a paginated page of discounts & promotions report records matching the specified filters.
         *
         * @param page Page number (0-based)
         * @param pageSize Number of records per page
         * @param filters Map containing filter criteria including restaurant ID, start date/time, and end date/time
         * @return List of discounts & promotions report responses for the requested page
         */
        @Override
        public List<ReportsOverviewResponse.DiscountPromotionItem> fetchPage(int page, int pageSize, Map<String, Object> filters) {
            UUID restaurantId = (UUID) filters.get(FILTER_KEY_RESTAURANT_ID);
            LocalDateTime startDateTime = (LocalDateTime) filters.get(FILTER_KEY_START_DATE_TIME);
            LocalDateTime endDateTime = (LocalDateTime) filters.get(FILTER_KEY_END_DATE_TIME);
            return ReportsServiceImpl.this.fetchDiscountsPromotionsPage(page, pageSize, restaurantId, startDateTime, endDateTime);
        }

        /**
         * Gets the column headers for the discounts & promotions report CSV export.
         *
         * @param locale Locale for localization (currently not used for headers)
         * @return Array of column header strings
         */
        @Override
        public String[] getColumnHeaders(String locale) {
            Locale localeObj = Locale.forLanguageTag(locale != null ? locale : "en");
            return new String[]{
                    messageUtil.getMessage("csv.dashboard.header.discount.type", localeObj),
                    messageUtil.getMessage("reports.export.header.discountCode", localeObj),
                    messageUtil.getMessage("reports.export.header.discountName", localeObj),
                    messageUtil.getMessage("reports.export.header.numberOfTransactions", localeObj),
                    messageUtil.getMessage("reports.export.header.totalDiscountApplied", localeObj),
                    messageUtil.getMessage("reports.export.header.totalRevenue", localeObj),
                    messageUtil.getMessage("reports.export.header.totalRevenueBeforeDiscount", localeObj),
                    messageUtil.getMessage("reports.export.header.discountEfficiency", localeObj),
                    messageUtil.getMessage("reports.export.header.appliedTo", localeObj)
            };
        }

        /**
         * Converts a discounts & promotions item to a CSV row array.
         *
         * @param data Discounts & promotions item to convert
         * @param locale Locale for localization (currently not used)
         * @return Array of strings representing a CSV row
         */
        @Override
        public String[] convertToRow(ReportsOverviewResponse.DiscountPromotionItem data, String locale) {
            // Extract nested ternary to if-else for better readability
            String discountNameValue;
            if (data.getDiscountName() != null) {
                discountNameValue = data.getDiscountName();
            } else if (data.getDiscountCode() != null) {
                discountNameValue = data.getDiscountCode();
            } else {
                discountNameValue = "";
            }
            
            return new String[]{
                    data.getDiscountType() != null ? data.getDiscountType() : "",
                    data.getDiscountCode() != null ? data.getDiscountCode() : "",
                    discountNameValue,
                    data.getNumberOfTransactions() != null ? String.valueOf(data.getNumberOfTransactions()) : "0",
                    data.getTotalDiscountApplied() != null ? data.getTotalDiscountApplied().toString() : "0.00",
                    data.getTotalRevenue() != null ? data.getTotalRevenue().toString() : "0.00",
                    data.getTotalRevenueBeforeDiscount() != null ? data.getTotalRevenueBeforeDiscount().toString() : "0.00",
                    data.getDiscountEfficiency() != null ? data.getDiscountEfficiency().toString() + "%" : DEFAULT_PERCENTAGE,
                    data.getAppliedTo() != null ? data.getAppliedTo() : ""
            };
        }

        /**
         * Gets metadata for the discounts & promotions report, including report title, restaurant name, export date, and date range.
         *
         * @param filters Map containing filter criteria including restaurant ID, start date/time, and end date/time
         * @param locale Locale for localization (currently not used)
         * @return Map of metadata key-value pairs
         */
        @Override
        public Map<String, String> getReportMetadata(Map<String, Object> filters, String locale) {
            Map<String, String> metadata = new LinkedHashMap<>();
            UUID restaurantId = (UUID) filters.get(FILTER_KEY_RESTAURANT_ID);
            LocalDateTime startDateTime = (LocalDateTime) filters.get(FILTER_KEY_START_DATE_TIME);
            LocalDateTime endDateTime = (LocalDateTime) filters.get(FILTER_KEY_END_DATE_TIME);

            metadata.put("DISCOUNTS & PROMOTIONS REPORT", "");
            if (restaurantId != null) {
                Restaurant restaurant = restaurantRepository.findById(restaurantId).orElse(null);
                if (restaurant != null) {
                    String restaurantName = restaurant.getRestaurantCode() != null ? restaurant.getRestaurantCode() : "";
                    metadata.put(messageUtil.getMessage("reports.export.label.restaurant"), restaurantName);
                }
            }
            metadata.put(messageUtil.getMessage("csv.export.date"),
                    LocalDateTime.now(ZoneOffset.UTC).format(DATETIME_FORMAT));
            if (startDateTime != null) {
                metadata.put(messageUtil.getMessage("reports.export.header.startDate"), startDateTime.format(DATETIME_FORMAT));
            }
            if (endDateTime != null) {
                metadata.put(messageUtil.getMessage("reports.export.header.endDate"), endDateTime.format(DATETIME_FORMAT));
            }
            return metadata;
        }

        /**
         * Gets summary statistics for the discounts & promotions report, including total discount types, total transactions, total discount applied, and total revenue.
         *
         * @param filters Map containing filter criteria including restaurant ID, start date/time, and end date/time
         * @param locale Locale for localization (currently not used)
         * @return Map of summary statistics key-value pairs
         */
        @Override
        public Map<String, String> getSummaryStatistics(Map<String, Object> filters, String locale) {
            UUID restaurantId = (UUID) filters.get(FILTER_KEY_RESTAURANT_ID);
            LocalDateTime startDateTime = (LocalDateTime) filters.get(FILTER_KEY_START_DATE_TIME);
            LocalDateTime endDateTime = (LocalDateTime) filters.get(FILTER_KEY_END_DATE_TIME);

            ReportsOverviewResponse.DiscountsPromotionsReport report = getDiscountsPromotionsReport(
                    restaurantId, startDateTime, endDateTime, null, null, null, null);
            
            List<ReportsOverviewResponse.DiscountPromotionItem> allDiscounts = report.getDiscounts() != null ? report.getDiscounts() : Collections.emptyList();
            
            BigDecimal totalDiscountApplied = allDiscounts.stream()
                    .map(ReportsOverviewResponse.DiscountPromotionItem::getTotalDiscountApplied)
                    .filter(Objects::nonNull)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            
            BigDecimal totalRevenue = allDiscounts.stream()
                    .map(ReportsOverviewResponse.DiscountPromotionItem::getTotalRevenue)
                    .filter(Objects::nonNull)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            
            Long totalTransactions = allDiscounts.stream()
                    .map(ReportsOverviewResponse.DiscountPromotionItem::getNumberOfTransactions)
                    .filter(Objects::nonNull)
                    .reduce(0L, Long::sum);

            Map<String, String> summary = new LinkedHashMap<>();
            summary.put("Total Discount Types", String.valueOf(allDiscounts.size()));
            summary.put("Total Transactions", String.valueOf(totalTransactions));
            summary.put(messageUtil.getMessage("reports.export.header.totalDiscountApplied"), totalDiscountApplied.toString());
            summary.put(messageUtil.getMessage("reports.export.header.totalRevenue"), totalRevenue.toString());
            return summary;
        }

        @Override
        public boolean supportsReportType(com.gulfnet.shared_library.enums.ReportType reportType) {
            return reportType == com.gulfnet.shared_library.enums.ReportType.DISCOUNTS_PROMOTIONS;
        }
    }

    @Component("paymentTypesBreakdownReportDataProvider")
    public class PaymentTypesBreakdownReportDataProvider implements
            com.gulfnet.shared_library.service.export.ReportDataProvider<ReportsOverviewResponse.PaymentTypeBreakdown>,
            com.gulfnet.shared_library.service.export.ReportTypeProvider {

        /**
         * Gets the total count of payment types breakdown report records matching the specified filters.
         *
         * @param filters Map containing filter criteria including restaurant ID, start date/time, and end date/time
         * @return Total count of payment types breakdown records matching the filters
         */
        @Override
        public long getTotalCount(Map<String, Object> filters) {
            UUID restaurantId = (UUID) filters.get(FILTER_KEY_RESTAURANT_ID);
            LocalDateTime startDateTime = (LocalDateTime) filters.get(FILTER_KEY_START_DATE_TIME);
            LocalDateTime endDateTime = (LocalDateTime) filters.get(FILTER_KEY_END_DATE_TIME);
            return ReportsServiceImpl.this.getPaymentTypesBreakdownTotalCount(restaurantId, startDateTime, endDateTime);
        }

        /**
         * Fetches a paginated page of payment types breakdown report records matching the specified filters.
         * Since payment types breakdown is typically small, all data is fetched and then paginated in-memory.
         *
         * @param page Page number (0-based)
         * @param pageSize Number of records per page
         * @param filters Map containing filter criteria including restaurant ID, start date/time, and end date/time
         * @return List of payment types breakdown report responses for the requested page
         */
        @Override
        public List<ReportsOverviewResponse.PaymentTypeBreakdown> fetchPage(int page, int pageSize, Map<String, Object> filters) {
            // Payment types breakdown is small, return all data
            UUID restaurantId = (UUID) filters.get(FILTER_KEY_RESTAURANT_ID);
            LocalDateTime startDateTime = (LocalDateTime) filters.get(FILTER_KEY_START_DATE_TIME);
            LocalDateTime endDateTime = (LocalDateTime) filters.get(FILTER_KEY_END_DATE_TIME);
            List<ReportsOverviewResponse.PaymentTypeBreakdown> all = ReportsServiceImpl.this.getAllPaymentTypesBreakdown(
                    restaurantId, startDateTime, endDateTime);
            // Simple pagination
            int start = page * pageSize;
            int end = Math.min(start + pageSize, all.size());
            return start < all.size() ? all.subList(start, end) : Collections.emptyList();
        }

        /**
         * Gets the column headers for the payment types breakdown report CSV export.
         *
         * @param locale Locale for localization (currently not used for headers)
         * @return Array of column header strings
         */
        @Override
        public String[] getColumnHeaders(String locale) {
            return new String[]{
                    messageUtil.getMessage("reports.export.header.paymentMethod"),
                    messageUtil.getMessage("csv.dashboard.metric.total.sales"),
                    messageUtil.getMessage("reports.export.header.percentage")
            };
        }

        /**
         * Converts a payment type breakdown to a CSV row array.
         *
         * @param data Payment type breakdown to convert
         * @param locale Locale for localization (currently not used)
         * @return Array of strings representing a CSV row
         */
        @Override
        public String[] convertToRow(ReportsOverviewResponse.PaymentTypeBreakdown data, String locale) {
            return new String[]{
                    data.getPaymentMethod() != null ? data.getPaymentMethod() : "",
                    data.getTotalSales() != null ? data.getTotalSales().toString() : "0.00",
                    data.getPercentage() != null ? String.format("%.2f%%", data.getPercentage()) : DEFAULT_PERCENTAGE
            };
        }

        /**
         * Gets metadata for the payment types breakdown report, including report title, restaurant name, export date, and date range.
         *
         * @param filters Map containing filter criteria including restaurant ID, start date/time, and end date/time
         * @param locale Locale for localization (currently not used)
         * @return Map of metadata key-value pairs
         */
        @Override
        public Map<String, String> getReportMetadata(Map<String, Object> filters, String locale) {
            Map<String, String> metadata = new LinkedHashMap<>();
            UUID restaurantId = (UUID) filters.get(FILTER_KEY_RESTAURANT_ID);
            LocalDateTime startDateTime = (LocalDateTime) filters.get(FILTER_KEY_START_DATE_TIME);
            LocalDateTime endDateTime = (LocalDateTime) filters.get(FILTER_KEY_END_DATE_TIME);

            metadata.put("PAYMENT TYPES BREAKDOWN REPORT", "");
            if (restaurantId != null) {
                Restaurant restaurant = restaurantRepository.findById(restaurantId).orElse(null);
                if (restaurant != null) {
                    String restaurantName = restaurant.getRestaurantCode() != null ? restaurant.getRestaurantCode() : "";
                    metadata.put(messageUtil.getMessage("reports.export.label.restaurant"), restaurantName);
                }
            }
            metadata.put(messageUtil.getMessage("csv.export.date"),
                    LocalDateTime.now(ZoneOffset.UTC).format(DATETIME_FORMAT));
            if (startDateTime != null) {
                metadata.put(messageUtil.getMessage("reports.export.header.startDate"), startDateTime.format(DATETIME_FORMAT));
            }
            if (endDateTime != null) {
                metadata.put(messageUtil.getMessage("reports.export.header.endDate"), endDateTime.format(DATETIME_FORMAT));
            }
            return metadata;
        }

        /**
         * Gets summary statistics for the payment types breakdown report, including total payment methods and total sales.
         *
         * @param filters Map containing filter criteria including restaurant ID, start date/time, and end date/time
         * @param locale Locale for localization (currently not used)
         * @return Map of summary statistics key-value pairs
         */
        @Override
        public Map<String, String> getSummaryStatistics(Map<String, Object> filters, String locale) {
            UUID restaurantId = (UUID) filters.get(FILTER_KEY_RESTAURANT_ID);
            LocalDateTime startDateTime = (LocalDateTime) filters.get(FILTER_KEY_START_DATE_TIME);
            LocalDateTime endDateTime = (LocalDateTime) filters.get(FILTER_KEY_END_DATE_TIME);

            List<ReportsOverviewResponse.PaymentTypeBreakdown> all = getAllPaymentTypesBreakdown(
                    restaurantId, startDateTime, endDateTime);
            
            BigDecimal totalSales = all.stream()
                    .map(ReportsOverviewResponse.PaymentTypeBreakdown::getTotalSales)
                    .filter(Objects::nonNull)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            Map<String, String> summary = new LinkedHashMap<>();
            summary.put("Total Payment Methods", String.valueOf(all.size()));
            summary.put(HEADER_TOTAL_SALES, totalSales.toString());
            return summary;
        }

        @Override
        public boolean supportsReportType(com.gulfnet.shared_library.enums.ReportType reportType) {
            return reportType == com.gulfnet.shared_library.enums.ReportType.PAYMENT_TYPES_BREAKDOWN;
        }
    }

    // Helper methods for HQ Admin support

    /**
     * Validates user has permission to access reports
     */
    private void validateReportAccess(String userRole, Locale locale) {
        if (userRole == null) {
            logger.warn("User-Role header is missing");
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    messageUtil.getMessage("reports.error.user.role.required", locale));
        }
        
        if (!"HQ_ADMIN".equalsIgnoreCase(userRole) && !"MANAGER".equalsIgnoreCase(userRole) && !"CASHIER".equalsIgnoreCase(userRole)) {
            logger.warn("Invalid user role for reports access: {}", userRole);
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    messageUtil.getMessage("reports.access.unauthorized", locale));
        }
    }

    /**
     * Gets restaurant IDs based on role and filters
     * MANAGER/CASHIER: can only access their assigned restaurant
     * HQ_ADMIN: can access specific restaurant, restaurant group, or all restaurants
     */
    private List<UUID> getRestaurantIdsForReport(UUID restaurantId, UUID restaurantGroupId,
                                                 String userId, String userRole, Locale locale) {
        return getRestaurantIdsForReport(restaurantId, restaurantGroupId, userId, userRole, locale, true);
    }

    /**
     * Variant of {@link #getRestaurantIdsForReport(UUID, UUID, String, String, Locale)} that can skip the
     * restaurant existence validation query when the caller will load the restaurant anyway.
     */
    private List<UUID> getRestaurantIdsForReport(UUID restaurantId, UUID restaurantGroupId,
                                                 String userId, String userRole, Locale locale,
                                                 boolean validateRestaurantExists) {
        List<UUID> restaurantIds = new ArrayList<>();

        if ("MANAGER".equalsIgnoreCase(userRole) || "CASHIER".equalsIgnoreCase(userRole)) {
            // Manager/Cashier can only access their assigned restaurant
            if (userId == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        messageUtil.getMessage("user.id.required", locale));
            }

            UUID userUuid = UUID.fromString(userId);
            UUID userRestaurantId = userRepository.findRestaurantIdByUserId(userUuid)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                            messageUtil.getMessage("user.not.found", locale)));

            if (userRestaurantId == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        messageUtil.getMessage("reports.error.manager.no.restaurant", locale));
            }

            // If restaurantId is provided, validate it matches the user's assigned restaurant
            if (restaurantId != null && !restaurantId.equals(userRestaurantId)) {
                logger.warn("Manager {} requested restaurantId {} but is assigned to restaurantId {}. Using assigned restaurant.",
                        userId, restaurantId, userRestaurantId);
            }

            // Validate restaurant exists (optional: export endpoint will load the restaurant for display anyway)
            if (validateRestaurantExists) {
                restaurantRepository.findByIdAndIsDeletedFalse(userRestaurantId)
                        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                                messageUtil.getMessage(msgRestaurantGetErrorNotFound, locale)));
            }

            restaurantIds.add(userRestaurantId);
        } else if ("HQ_ADMIN".equalsIgnoreCase(userRole)) {
            // HQ_ADMIN can access specific restaurant, restaurant group, or all restaurants
            if (restaurantId != null) {
                // Specific restaurant
                if (validateRestaurantExists) {
                    restaurantRepository.findByIdAndIsDeletedFalse(restaurantId)
                            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                                    messageUtil.getMessage(msgRestaurantGetErrorNotFound, locale)));
                }
                restaurantIds.add(restaurantId);
            } else if (restaurantGroupId != null) {
                // Restaurant group - get all restaurants in the group
                restaurantGroupRepository.findById(restaurantGroupId)
                        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                                messageUtil.getMessage("restaurant.group.not.found", locale)));
                List<Restaurant> restaurants = restaurantRepository.findByRestaurantGroupIdAndIsDeletedFalse(restaurantGroupId);
                restaurantIds = restaurants.stream()
                        .map(Restaurant::getId)
                        .collect(Collectors.toList());
            } else {
                // No filter - HQ_ADMIN can see all restaurants (for now, require at least one filter)
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        messageUtil.getMessage("reports.error.restaurantid.or.group.required", locale));
            }
        } else {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    messageUtil.getMessage("reports.access.unauthorized", locale));
        }

        return restaurantIds;
    }

    /**
     * Aggregates daily sales summary across multiple restaurants
     */
    private ReportsOverviewResponse.DailySalesSummary getDailySalesSummaryForRestaurants(
            List<UUID> restaurantIds, LocalDateTime startDate, LocalDateTime endDate) {
        BigDecimal totalSales = BigDecimal.ZERO;
        long totalOrders = 0L;
        long totalTablesServed = 0L;

        for (UUID restaurantId : restaurantIds) {
            ReportsOverviewResponse.DailySalesSummary summary = getDailySalesSummary(restaurantId, startDate, endDate);
            totalSales = totalSales.add(summary.getTotalSales());
            totalOrders += summary.getTotalOrders();
            totalTablesServed += summary.getTotalTablesServed();
        }

        BigDecimal avgOrderValue = totalOrders > 0
                ? totalSales.divide(BigDecimal.valueOf(totalOrders), 2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;

        return ReportsOverviewResponse.DailySalesSummary.builder()
                .totalSales(totalSales)
                .totalOrders(totalOrders)
                .totalTablesServed(totalTablesServed)
                .avgOrderValue(avgOrderValue)
                .build();
    }

    /**
     * Aggregates payment types breakdown across multiple restaurants
     */
    private List<ReportsOverviewResponse.PaymentTypeBreakdown> getPaymentTypesBreakdownForRestaurants(
            List<UUID> restaurantIds, LocalDateTime startDate, LocalDateTime endDate) {
        Map<String, BigDecimal> paymentMethodTotals = new HashMap<>();
        BigDecimal[] grandTotal = {BigDecimal.ZERO}; // Use array to make it effectively final

        for (UUID restaurantId : restaurantIds) {
            List<ReportsOverviewResponse.PaymentTypeBreakdown> breakdown = getPaymentTypesBreakdown(restaurantId, startDate, endDate);
            for (ReportsOverviewResponse.PaymentTypeBreakdown item : breakdown) {
                String method = item.getPaymentMethod();
                BigDecimal amount = item.getTotalSales();
                paymentMethodTotals.merge(method, amount, BigDecimal::add);
                grandTotal[0] = grandTotal[0].add(amount);
            }
        }

        final BigDecimal finalGrandTotal = grandTotal[0];
        return paymentMethodTotals.entrySet().stream()
                .map(entry -> {
                    Double percentage = finalGrandTotal.compareTo(BigDecimal.ZERO) > 0
                            ? entry.getValue().divide(finalGrandTotal, 4, RoundingMode.HALF_UP)
                                    .multiply(BigDecimal.valueOf(100))
                                    .doubleValue()
                            : 0.0;
                    return ReportsOverviewResponse.PaymentTypeBreakdown.builder()
                            .paymentMethod(entry.getKey())
                            .totalSales(entry.getValue())
                            .percentage(percentage)
                            .build();
                })
                .sorted(Comparator.comparing(ReportsOverviewResponse.PaymentTypeBreakdown::getTotalSales).reversed())
                .collect(Collectors.toList());
    }

    /**
     * Aggregates itemized sales report across multiple restaurants
     */
    private ReportsOverviewResponse.ItemizedSalesReport getItemizedSalesReportForRestaurants(
            List<UUID> restaurantIds, LocalDateTime startDate, LocalDateTime endDate,
            Integer page, Integer size, String sortBy, Sort.Direction sortDirection, String locale) {
        // For multi-restaurant, aggregate all items across restaurants
        Map<String, ReportsOverviewResponse.ItemizedSalesItem> itemMap = new HashMap<>();

        for (UUID restaurantId : restaurantIds) {
            ReportsOverviewResponse.ItemizedSalesReport report = getItemizedSalesReport(
                    restaurantId, startDate, endDate, null, null, null, null, locale);
            for (ReportsOverviewResponse.ItemizedSalesItem item : report.getItems()) {
                String key = itemizedSalesMergeKey(item);
                itemMap.merge(key, item, (existing, newItem) -> {
                    Integer totalQuantity = (existing.getQuantitySold() != null ? existing.getQuantitySold() : 0) +
                            (newItem.getQuantitySold() != null ? newItem.getQuantitySold() : 0);
                    BigDecimal totalSales = existing.getTotalSales().add(newItem.getTotalSales());
                    BigDecimal unitPrice = totalQuantity > 0
                            ? totalSales.divide(BigDecimal.valueOf(totalQuantity), 2, RoundingMode.HALF_UP)
                            : BigDecimal.ZERO;
                    return ReportsOverviewResponse.ItemizedSalesItem.builder()
                            .itemCode(existing.getItemCode())
                            .itemName(existing.getItemName())
                            .category(existing.getCategory())
                            .quantitySold(totalQuantity)
                            .unitPrice(unitPrice)
                            .totalSales(totalSales)
                            .percentageOfTotalSales(existing.getPercentageOfTotalSales()) // Will be recalculated if needed
                            .build();
                });
            }
        }

        List<ReportsOverviewResponse.ItemizedSalesItem> items = new ArrayList<>(itemMap.values());
        // Apply sorting and pagination
        if (sortBy != null && !sortBy.trim().isEmpty()) {
            // Apply sorting logic similar to single restaurant
        }
        // Apply pagination
        int pageNumber = (page != null && page > 0) ? page - 1 : 0; // 0-based
        int pageSize = (size != null && size > 0) ? size : 10;
        
        int fromIndex = Math.min(pageNumber * pageSize, items.size());
        int toIndex = Math.min(fromIndex + pageSize, items.size());
        List<ReportsOverviewResponse.ItemizedSalesItem> paginatedItems;
        if (fromIndex >= items.size()) {
            paginatedItems = new ArrayList<>();
        } else {
            paginatedItems = items.subList(fromIndex, toIndex);
        }

        PaginationMetaData metaData = PaginationMetaData.builder()
                .page(pageNumber + 1)
                .size(pageSize)
                .totalPages((int) Math.ceil((double) items.size() / pageSize))
                .totalRecords((long) items.size())
                .build();

        return ReportsOverviewResponse.ItemizedSalesReport.builder()
                .items(paginatedItems)
                .count((long) paginatedItems.size())
                .total((long) items.size())
                .metaData(metaData)
                .build();
    }

    /**
     * Aggregates table-wise sales report across multiple restaurants
     */
    private ReportsOverviewResponse.TableWiseSalesReport getTableWiseSalesReportForRestaurants(
            List<UUID> restaurantIds, LocalDateTime startDate, LocalDateTime endDate,
            Integer page, Integer size, String sortBy, Sort.Direction sortDirection) {
        List<ReportsOverviewResponse.TableWiseSalesItem> allItems = new ArrayList<>();

        for (UUID restaurantId : restaurantIds) {
            ReportsOverviewResponse.TableWiseSalesReport report = getTableWiseSalesReport(
                    restaurantId, startDate, endDate, null, null, null, null);
            if (report.getTables() != null) {
                allItems.addAll(report.getTables());
            }
        }

        // Apply sorting and pagination
        int pageNumber = (page != null && page > 0) ? page - 1 : 0; // 0-based
        int pageSize = (size != null && size > 0) ? size : 10;
        
        int fromIndex = Math.min(pageNumber * pageSize, allItems.size());
        int toIndex = Math.min(fromIndex + pageSize, allItems.size());
        List<ReportsOverviewResponse.TableWiseSalesItem> paginatedItems;
        if (fromIndex >= allItems.size()) {
            paginatedItems = new ArrayList<>();
        } else {
            paginatedItems = allItems.subList(fromIndex, toIndex);
        }

        PaginationMetaData metaData = PaginationMetaData.builder()
                .page(pageNumber + 1)
                .size(pageSize)
                .totalPages((int) Math.ceil((double) allItems.size() / pageSize))
                .totalRecords((long) allItems.size())
                .build();

        return ReportsOverviewResponse.TableWiseSalesReport.builder()
                .tables(paginatedItems)
                .count((long) paginatedItems.size())
                .total((long) allItems.size())
                .metaData(metaData)
                .build();
    }

    /**
     * Aggregates discounts and promotions report across multiple restaurants
     */
    private ReportsOverviewResponse.DiscountsPromotionsReport getDiscountsPromotionsReportForRestaurants(
            List<UUID> restaurantIds, LocalDateTime startDate, LocalDateTime endDate,
            Integer page, Integer size, String sortBy, Sort.Direction sortDirection) {
        List<ReportsOverviewResponse.DiscountPromotionItem> allItems = new ArrayList<>();

        for (UUID restaurantId : restaurantIds) {
            ReportsOverviewResponse.DiscountsPromotionsReport report = getDiscountsPromotionsReport(
                    restaurantId, startDate, endDate, null, null, null, null);
            if (report.getDiscounts() != null) {
                allItems.addAll(report.getDiscounts());
            }
        }

        // Apply sorting and pagination
        int pageNumber = (page != null && page > 0) ? page - 1 : 0; // 0-based
        int pageSize = (size != null && size > 0) ? size : 10;
        
        int fromIndex = Math.min(pageNumber * pageSize, allItems.size());
        int toIndex = Math.min(fromIndex + pageSize, allItems.size());
        List<ReportsOverviewResponse.DiscountPromotionItem> paginatedItems;
        if (fromIndex >= allItems.size()) {
            paginatedItems = new ArrayList<>();
        } else {
            paginatedItems = allItems.subList(fromIndex, toIndex);
        }

        PaginationMetaData metaData = PaginationMetaData.builder()
                .page(pageNumber + 1)
                .size(pageSize)
                .totalPages((int) Math.ceil((double) allItems.size() / pageSize))
                .totalRecords((long) allItems.size())
                .build();

        return ReportsOverviewResponse.DiscountsPromotionsReport.builder()
                .discounts(paginatedItems)
                .count((long) paginatedItems.size())
                .total((long) allItems.size())
                .metaData(metaData)
                .build();
    }

    /**
     * Converts various date/time object types to LocalDateTime.
     * Supports conversion from Timestamp, LocalDateTime, OffsetDateTime, and Date.
     * Returns null if the object type is not recognized (to avoid incorrect "now" timestamps in reports).
     *
     * @param obj Object to convert (can be Timestamp, LocalDateTime, OffsetDateTime, or Date)
     * @return LocalDateTime representation of the input object, or null if conversion fails
     */
    private LocalDateTime convertToLocalDateTime(Object obj) {
        if (obj == null) return null;
        if (obj instanceof java.sql.Timestamp) {
            return ((java.sql.Timestamp) obj).toLocalDateTime();
        } else if (obj instanceof LocalDateTime) {
            return (LocalDateTime) obj;
        } else if (obj instanceof java.time.OffsetDateTime) {
            return ((java.time.OffsetDateTime) obj).toLocalDateTime();
        } else if (obj instanceof java.time.ZonedDateTime) {
            return ((java.time.ZonedDateTime) obj).toLocalDateTime();
        } else if (obj instanceof java.time.Instant) {
            return LocalDateTime.ofInstant((java.time.Instant) obj, ZoneOffset.UTC);
        } else if (obj instanceof java.util.Date) {
            return ((java.util.Date) obj).toInstant().atZone(ZoneOffset.UTC).toLocalDateTime();
        }
        return null;
    }

    /**
     * Sets or updates a daily summary report email schedule for a restaurant.
     * If a schedule already exists, it is deleted and replaced with the new schedule.
     * Creates a Quartz job to execute the scheduled email report.
     *
     * @param restaurantId UUID of the restaurant for which to schedule the report
     * @param scheduledTime Time in UTC format with timezone offset from frontend when the report should be sent
     * @param userId ID of the user creating/updating the schedule
     * @param userRole Role of the user creating/updating the schedule
     * @param locale Locale for error messages and localization
     * @return ResponseDto containing the created/updated email schedule response
     */
    @Override
    @Transactional
    public ResponseDto<com.gulfnet.shared_library.model.response.dto.EmailScheduleResponse> setDailySummaryReportSchedule(
            UUID restaurantId, 
            java.time.OffsetTime scheduledTime, // Time in UTC format with timezone offset from frontend
            String userId, 
            String userRole, 
            String locale) {
        
        Locale localeObj = Locale.forLanguageTag(locale != null ? locale : "en");
        LocaleContextHolder.setLocale(localeObj);

        // Normalize OffsetTime to UTC if provided
        java.time.OffsetTime scheduledTimeUtc = scheduledTime != null 
                ? scheduledTime.withOffsetSameInstant(java.time.ZoneOffset.UTC)
                : java.time.OffsetTime.of(0, 0, 0, 0, java.time.ZoneOffset.UTC);

        logger.info("Setting daily summary report schedule - restaurantId: {}, scheduledTime: {} (normalized to UTC: {}), userId: {}, userRole: {}", 
                restaurantId, scheduledTime, scheduledTimeUtc, userId, userRole);

        // Validate restaurant exists
        Restaurant restaurant = restaurantRepository.findById(restaurantId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        messageUtil.getMessage("restaurant.not.found", localeObj)));

        // Get repositories and services
        com.gulfnet.shared_library.repository.EmailScheduleRepository emailScheduleRepository = 
                applicationContext.getBean(com.gulfnet.shared_library.repository.EmailScheduleRepository.class);

                com.gulfnet.shared_library.repository.UserRepository localUserRepository = 
                applicationContext.getBean(com.gulfnet.shared_library.repository.UserRepository.class);
        com.gulfnet.restaurantmanagement.service.ScheduleManagerService scheduleManagerService = 
                applicationContext.getBean(com.gulfnet.restaurantmanagement.service.ScheduleManagerService.class);

        // Get creator user
        User creator = localUserRepository.findById(UUID.fromString(userId))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        messageUtil.getMessage("user.not.found", localeObj)));

        // Validate creator has email
        if (creator.getEmail() == null || creator.getEmail().trim().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    messageUtil.getMessage("employee.profile.email.required", localeObj));
        }

        // Check if a schedule already exists for this restaurant with DAILY_SALES_SUMMARY
        List<com.gulfnet.shared_library.entity.EmailSchedule> existingSchedules = 
                emailScheduleRepository.findByRestaurant_IdAndIsActiveTrue(restaurantId);
        
        com.gulfnet.shared_library.entity.EmailSchedule existingSchedule = existingSchedules.stream()
                .filter(s -> s.getReportType() == com.gulfnet.shared_library.enums.ReportType.DAILY_SALES_SUMMARY 
                        && s.getFrequency() == com.gulfnet.shared_library.enums.ScheduleFrequency.DAILY)
                .findFirst()
                .orElse(null);

        // If schedule exists, delete it first (including Quartz job)
        if (existingSchedule != null) {
            logger.info("Deleting existing daily summary schedule: {}", existingSchedule.getId());
            try {
                if (existingSchedule.getQuartzJobKey() != null) {
                    scheduleManagerService.deleteQuartzJob(existingSchedule.getQuartzJobKey());
                }
            } catch (Exception e) {
                logger.warn("Failed to delete Quartz job for existing schedule: {}", existingSchedule.getId(), e);
            }
            emailScheduleRepository.delete(existingSchedule);
        }

        // Calculate next execution time using ScheduleManagerService
        OffsetDateTime nextExecutionAt = scheduleManagerService.calculateNextExecutionTime(
                com.gulfnet.shared_library.entity.EmailSchedule.builder()
                        .frequency(com.gulfnet.shared_library.enums.ScheduleFrequency.DAILY)
                        .scheduledTime(scheduledTimeUtc)
                        .scheduledDay(null)
                        .build());

        // Create EmailSchedule entity directly (without using existing createSchedule method)
        com.gulfnet.shared_library.entity.EmailSchedule emailSchedule = com.gulfnet.shared_library.entity.EmailSchedule.builder()
                .scheduleName("Daily Summary Report - " + (restaurant.getTranslations() != null && !restaurant.getTranslations().isEmpty() 
                        ? restaurant.getTranslations().get(0).getName() : messageUtil.getMessage("reports.export.label.restaurant")))
                .reportType(com.gulfnet.shared_library.enums.ReportType.DAILY_SALES_SUMMARY)
                .frequency(com.gulfnet.shared_library.enums.ScheduleFrequency.DAILY)
                .scheduledTime(scheduledTimeUtc) // Store UTC OffsetTime
                .scheduledDay(null) // Not needed for DAILY frequency
                .restaurant(restaurant)
                .restaurantGroup(null)
                .recipientEmail(creator.getEmail())
                .isActive(true)
                .createdBy(creator)
                .nextExecutionAt(nextExecutionAt)
                .period(null)
                .startDate(null)
                .endDate(null)
                .build();

        // Save entity first to get ID
        emailSchedule = emailScheduleRepository.save(emailSchedule);

        // Create Quartz job using dedicated method
        try {
            createDailySummaryQuartzJob(emailSchedule, scheduleManagerService);
            String jobKey = "email-schedule-" + emailSchedule.getId().toString();
            emailSchedule.setQuartzJobKey(jobKey);
            emailSchedule = emailScheduleRepository.save(emailSchedule);
        } catch (Exception e) {
            logger.error("Failed to create Quartz job for daily summary schedule: {}", emailSchedule.getId(), e);
            // Delete the schedule if Quartz job creation fails
            emailScheduleRepository.delete(emailSchedule);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "Failed to create schedule: " + e.getMessage());
        }

        com.gulfnet.shared_library.model.response.dto.EmailScheduleResponse response = buildEmailScheduleResponse(emailSchedule);

        return ResponseDto.<com.gulfnet.shared_library.model.response.dto.EmailScheduleResponse>builder()
                .message(messageUtil.getMessage("email.schedule.created.success", localeObj))
                .data(response)
                .build();
    }

    /**
     * Dedicated method to calculate next execution time for daily summary reports
     * This method is separate from existing scheduling methods
     * 
     * @param scheduledTime Time in UTC format received from frontend - used directly as UTC
     */
    private LocalDateTime calculateNextDailyExecutionTime(java.time.LocalTime scheduledTime) {
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        
        // Use scheduled time if provided (already in UTC from frontend), otherwise default to midnight UTC
        int hour = scheduledTime != null ? scheduledTime.getHour() : 0;
        int minute = scheduledTime != null ? scheduledTime.getMinute() : 0;
        int second = scheduledTime != null ? scheduledTime.getSecond() : 0;

        // Next execution is today or tomorrow at the specified time UTC
        // scheduledTime is already in UTC format from frontend, so we use it directly
        LocalDateTime todayAtScheduledTime = now.toLocalDate()
                .atTime(hour, minute, second)
                .atZone(ZoneOffset.UTC)
                .toLocalDateTime();
        
        if (now.isBefore(todayAtScheduledTime)) {
            // Today's scheduled time hasn't passed yet
            return todayAtScheduledTime;
        } else {
            // Today's scheduled time has passed, schedule for tomorrow
            return now.toLocalDate().plusDays(1)
                    .atTime(hour, minute, second)
                    .atZone(ZoneOffset.UTC)
                    .toLocalDateTime();
        }
    }

    /**
     * Dedicated method to create Quartz job for daily summary reports
     * This method is separate from existing scheduling methods
     * 
     * @param schedule EmailSchedule with scheduledTime in UTC format (received from frontend as UTC)
     */
    private void createDailySummaryQuartzJob(
            com.gulfnet.shared_library.entity.EmailSchedule schedule,
            com.gulfnet.restaurantmanagement.service.ScheduleManagerService scheduleManagerService) throws Exception {
        
        // Build cron expression for daily schedule at specified time
        // scheduledTime is already in UTC format from frontend, so we use it directly
        OffsetTime scheduledTime = schedule.getScheduledTime();
        int hour = scheduledTime != null ? scheduledTime.getHour() : 0;
        int minute = scheduledTime != null ? scheduledTime.getMinute() : 0;
        int second = scheduledTime != null ? scheduledTime.getSecond() : 0;
        
        // Cron expression format: second minute hour day month day-of-week
        // Time values are in UTC (received from frontend as UTC)
        String cronExpression = String.format("%d %d %d * * ?", second, minute, hour);

        // Get scheduler from ScheduleManagerService
        org.quartz.Scheduler scheduler = applicationContext.getBean(org.quartz.Scheduler.class);
        
        String jobKey = "email-schedule-" + schedule.getId().toString();
        String triggerKey = "email-schedule-trigger-" + schedule.getId().toString();

        // Create job detail
        org.quartz.JobDetail jobDetail = org.quartz.JobBuilder.newJob(
                com.gulfnet.restaurantmanagement.job.ScheduledEmailReportJob.class)
                .withIdentity(jobKey, "email-schedules")
                .usingJobData("scheduleId", schedule.getId().toString())
                .storeDurably(false)
                .build();

        // Create cron trigger with UTC timezone
        org.quartz.CronTrigger trigger = org.quartz.TriggerBuilder.newTrigger()
                .withIdentity(triggerKey, "email-schedules")
                .withSchedule(org.quartz.CronScheduleBuilder.cronSchedule(cronExpression)
                        .inTimeZone(java.util.TimeZone.getTimeZone("UTC"))
                        .withMisfireHandlingInstructionFireAndProceed())
                .build();

        // Schedule the job
        scheduler.scheduleJob(jobDetail, trigger);
        logger.info("Created Quartz job for daily summary schedule {} with cron expression: {} (scheduled time: {})", 
                schedule.getId(), cronExpression, scheduledTime);
    }

    /**
     * Builds an EmailScheduleResponse DTO from an EmailSchedule entity.
     * Includes fetching and mapping translations associated with the schedule.
     *
     * @param schedule EmailSchedule entity to convert
     * @return EmailScheduleResponse DTO with all schedule details and translations
     */
    private com.gulfnet.shared_library.model.response.dto.EmailScheduleResponse buildEmailScheduleResponse(
            com.gulfnet.shared_library.entity.EmailSchedule schedule) {
        // Load translations for this schedule
        com.gulfnet.shared_library.repository.EmailScheduleTranslationRepository emailScheduleTranslationRepository = 
                applicationContext.getBean(com.gulfnet.shared_library.repository.EmailScheduleTranslationRepository.class);
        
        List<com.gulfnet.shared_library.entity.EmailScheduleTranslation> translations =
                emailScheduleTranslationRepository.findAllByScheduleId(schedule.getId());

        List<com.gulfnet.shared_library.model.response.dto.EmailScheduleTranslationDto> translationDtos = new ArrayList<>();
        if (translations != null && !translations.isEmpty()) {
            for (com.gulfnet.shared_library.entity.EmailScheduleTranslation t : translations) {
                translationDtos.add(com.gulfnet.shared_library.model.response.dto.EmailScheduleTranslationDto.builder()
                        .languageCode(t.getLanguageCode())
                        .name(t.getName())
                        .build());
            }
        }

        return com.gulfnet.shared_library.model.response.dto.EmailScheduleResponse.builder()
                .id(schedule.getId())
                .reportType(schedule.getReportType())
                .frequency(schedule.getFrequency())
                .scheduledTime(schedule.getScheduledTime())
                .scheduledDay(schedule.getScheduledDay())
                .restaurantId(schedule.getRestaurant() != null ? schedule.getRestaurant().getId() : null)
                .restaurantName(schedule.getRestaurant() != null && schedule.getRestaurant().getTranslations() != null
                        && !schedule.getRestaurant().getTranslations().isEmpty()
                        ? schedule.getRestaurant().getTranslations().get(0).getName() : null)
                .restaurantGroupId(schedule.getRestaurantGroup() != null ? schedule.getRestaurantGroup().getId() : null)
                .restaurantGroupName(schedule.getRestaurantGroup() != null && schedule.getRestaurantGroup().getTranslations() != null
                        && !schedule.getRestaurantGroup().getTranslations().isEmpty()
                        ? schedule.getRestaurantGroup().getTranslations().get(0).getName() : null)
                .recipientEmail(schedule.getRecipientEmail())
                .isActive(schedule.getIsActive())
                .createdById(schedule.getCreatedBy() != null ? schedule.getCreatedBy().getId() : null)
                .createdByName(schedule.getCreatedBy() != null
                        ? schedule.getCreatedBy().getFirstName() + " " + schedule.getCreatedBy().getLastName() : null)
                .createdAt(schedule.getCreatedAt() != null ? schedule.getCreatedAt().toLocalDateTime() : null)
                .updatedById(schedule.getUpdatedBy() != null ? schedule.getUpdatedBy().getId() : null)
                .updatedByName(schedule.getUpdatedBy() != null
                        ? schedule.getUpdatedBy().getFirstName() + " " + schedule.getUpdatedBy().getLastName() : null)
                .updatedAt(schedule.getUpdatedAt() != null ? schedule.getUpdatedAt().toLocalDateTime() : null)
                .lastExecutedAt(schedule.getLastExecutedAt())
                .nextExecutionAt(schedule.getNextExecutionAt())
                .quartzJobKey(schedule.getQuartzJobKey())
                .period(schedule.getPeriod())
                .startDate(schedule.getStartDate())
                .endDate(schedule.getEndDate())
                .translations(translationDtos)
                .build();
    }

    /**
     * Helper method to get shift name from translations
     */
    private String getShiftNameFromShift(com.gulfnet.shared_library.entity.Shift shift) {
        if (shift == null) {
            return null;
        }
        
        List<com.gulfnet.shared_library.entity.ShiftTranslation> translations = shiftTranslationRepository.findAllByShiftId(shift.getId());
        if (translations == null || translations.isEmpty()) {
            return "";
        }
        
        String preferredLocale = LocaleContextHolder.getLocale().getLanguage();
        String defaultLanguage = localizationProperties.getLanguages() != null && !localizationProperties.getLanguages().isEmpty()
                ? localizationProperties.getLanguages().get(0) : "en";
        
        Optional<com.gulfnet.shared_library.entity.ShiftTranslation> translation = TranslationUtils.pickPreferredOrFromList(
                translations,
                preferredLocale,
                localizationProperties.getLanguages(),
                com.gulfnet.shared_library.entity.ShiftTranslation::getLanguageCode
        );
        
        if (translation.isPresent()) {
            return translation.get().getName();
        }
        
        // Fallback to default language
        if (defaultLanguage != null) {
            Optional<com.gulfnet.shared_library.entity.ShiftTranslation> defaultTranslation = translations.stream()
                    .filter(t -> defaultLanguage.equalsIgnoreCase(t.getLanguageCode()))
                    .findFirst();
            if (defaultTranslation.isPresent()) {
                return defaultTranslation.get().getName();
            }
        }
        
        // Last resort: return first available translation
        return translations.get(0).getName();
    }

    private String buildExportFilename(String messageKey, Locale locale, String extension) {
        String timestamp = LocalDateTime.now(ZoneOffset.UTC).format(FILENAME_TIMESTAMP_FORMAT);
        String baseName = sanitizeExportFilename(messageUtil.getMessage(messageKey, locale));
        return baseName + "_" + timestamp + extension;
    }

    private void setExcelExportResponseHeaders(HttpServletResponse response, String messageKey, Locale locale) {
        response.setContentType(CONTENT_TYPE_EXCEL);
        response.setHeader(HEADER_CONTENT_DISPOSITION,
                ATTACHMENT_FILENAME_PREFIX + buildExportFilename(messageKey, locale, FILE_EXTENSION_XLSX) + "\"");
    }

    private void setCsvExportResponseHeaders(HttpServletResponse response, String messageKey, Locale locale) {
        response.setHeader(HEADER_CONTENT_DISPOSITION,
                ATTACHMENT_FILENAME_PREFIX + buildExportFilename(messageKey, locale, ".csv") + "\"");
    }

    private static String sanitizeExportFilename(String filename) {
        if (filename == null || filename.isBlank()) {
            return "export";
        }
        return filename.trim().replaceAll("[<>:\"/\\\\|?*]", "_");
    }
}

