package com.gulfnet.restaurantmanagement.service.impl;

import com.gulfnet.restaurantmanagement.service.DashboardService;
import com.gulfnet.restaurantmanagement.util.MessageUtil;
import com.gulfnet.shared_library.config.AWSService;
import com.gulfnet.shared_library.enums.EntityStatus;
import com.gulfnet.shared_library.enums.RestaurantMenuMappingStatus;
import com.gulfnet.shared_library.enums.TransactionStatus;
import com.gulfnet.shared_library.entity.Category;
import com.gulfnet.shared_library.entity.CategoryItemMapping;
import com.gulfnet.shared_library.entity.MenuPromotionMapping;
import com.gulfnet.shared_library.entity.MenuCategoryMapping;
import com.gulfnet.shared_library.entity.RestaurantPromotionMapping;
import com.gulfnet.shared_library.entity.CategoryTranslation;
import com.gulfnet.shared_library.entity.Item;
import com.gulfnet.shared_library.entity.ItemTranslation;
import com.gulfnet.shared_library.entity.Promotion;
import com.gulfnet.shared_library.entity.PromotionTranslation;
import com.gulfnet.shared_library.entity.Restaurant;
import com.gulfnet.shared_library.entity.RestaurantMenuMapping;
import com.gulfnet.shared_library.entity.RestaurantItemAvailability;
import com.gulfnet.shared_library.entity.RestaurantTranslation;
import com.gulfnet.shared_library.entity.Role;
import com.gulfnet.shared_library.entity.Shift;
import com.gulfnet.shared_library.entity.User;
import com.gulfnet.shared_library.entity.UserShiftMapping;
import com.gulfnet.shared_library.entity.UserShiftId;
import com.gulfnet.shared_library.enums.AppliedTo;
import com.gulfnet.shared_library.enums.MenuStatus;
import com.gulfnet.shared_library.enums.OrderStatus;
import com.gulfnet.shared_library.model.response.dto.DashboardResponse;
import com.gulfnet.shared_library.model.response.dto.DiscountStats;
import com.gulfnet.shared_library.model.response.dto.DiscountTypeStats;
import com.gulfnet.shared_library.model.response.dto.EmployeeRoleCount;
import com.gulfnet.shared_library.model.response.dto.EmployeeRoleCounts;
import com.gulfnet.shared_library.model.response.dto.MenuDashboardResponse;
import com.gulfnet.shared_library.model.response.dto.ManagerDetails;
import com.gulfnet.shared_library.model.response.dto.ManagerListResponse;
import com.gulfnet.shared_library.model.response.dto.MenuPerformance;
import com.gulfnet.shared_library.model.response.dto.OnShiftStaff;
import com.gulfnet.shared_library.model.response.dto.OnShiftStaffListResponse;
import com.gulfnet.shared_library.model.response.dto.OrderStatusBreakdown;
import com.gulfnet.shared_library.model.response.dto.PaginationMetaData;
import com.gulfnet.shared_library.model.response.dto.OrderStatusCounts;
import com.gulfnet.shared_library.model.response.dto.PeakHourAnalysis;
import com.gulfnet.shared_library.model.response.dto.PerformingItem;
import com.gulfnet.shared_library.model.response.dto.PeriodStatistics;
import com.gulfnet.shared_library.model.response.dto.PromotionDetail;
import com.gulfnet.shared_library.model.response.dto.PromotionStats;
import com.gulfnet.shared_library.model.response.dto.ResponseDto;
import com.gulfnet.shared_library.model.response.dto.RestaurantDashboardResponse;
import com.gulfnet.shared_library.model.response.dto.SalesDataPoint;
import com.gulfnet.shared_library.model.response.dto.SalesStats;
import com.gulfnet.shared_library.model.response.dto.TopItemPerformance;
import com.gulfnet.shared_library.model.response.dto.UnavailableItem;
import com.gulfnet.shared_library.model.response.dto.VoidManagement;
import com.gulfnet.shared_library.repository.CategoryRepository;
import com.gulfnet.shared_library.repository.CategoryTranslationRepository;
import com.gulfnet.shared_library.repository.DiscountRepository;
import com.gulfnet.shared_library.repository.ItemRepository;
import com.gulfnet.shared_library.repository.ItemTranslationRepository;
import com.gulfnet.shared_library.repository.MenuPromotionMappingRepository;
import com.gulfnet.shared_library.repository.RestaurantPromotionMappingRepository;
import com.gulfnet.shared_library.repository.MenuRepository;
import com.gulfnet.shared_library.repository.RestaurantMenuMappingRepository;
import com.gulfnet.shared_library.repository.MenuTranslationRepository;
import com.gulfnet.shared_library.repository.RestaurantTranslationRepository;
import com.gulfnet.shared_library.repository.RestaurantDiscountMappingRepository;
import com.gulfnet.shared_library.repository.MenuDiscountMappingRepository;
import com.gulfnet.shared_library.repository.OrderRepository;
import com.gulfnet.shared_library.repository.OrderDiscountUsageRepository;
import com.gulfnet.shared_library.repository.OrderedItemRepository;
import com.gulfnet.shared_library.repository.OrderedComboRepository;
import com.gulfnet.shared_library.repository.PromotionRepository;
import com.gulfnet.shared_library.repository.RefundRepository;
import com.gulfnet.shared_library.repository.PromotionTranslationRepository;
import com.gulfnet.shared_library.repository.RestaurantGroupRepository;
import com.gulfnet.shared_library.repository.RestaurantRepository;
import com.gulfnet.shared_library.repository.RoleRepository;
import com.gulfnet.shared_library.repository.TransactionRepository;
import com.gulfnet.shared_library.repository.CategoryItemMappingRepository;
import com.gulfnet.shared_library.repository.RestaurantItemAvailabilityRepository;
import com.gulfnet.shared_library.repository.RestaurantTableRepository;
import com.gulfnet.shared_library.repository.ShiftRepository;
import com.gulfnet.shared_library.repository.UserRepository;
import com.gulfnet.shared_library.repository.UserShiftMappingRepository;
import com.gulfnet.shared_library.util.TranslationUtils;
import com.gulfnet.restaurantmanagement.config.LocalizationProperties;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.math.BigDecimal;
import com.gulfnet.shared_library.util.CurrencyFormatter;
import com.gulfnet.restaurantmanagement.config.RestaurantChainConfigProperties;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
public class DashboardServiceImpl implements DashboardService {

    private static final Logger logger = LoggerFactory.getLogger(DashboardServiceImpl.class);
    private static final String MSG_DASHBOARD_ERROR_STARTDATE_REQUIRED = "dashboard.error.startdate.required";

    @Autowired
    private RestaurantRepository restaurantRepository;

    @Autowired
    private RestaurantGroupRepository restaurantGroupRepository;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private TransactionRepository transactionRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ItemRepository itemRepository;

    @Autowired
    private DiscountRepository discountRepository;

    @Autowired
    private PromotionRepository promotionRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private RestaurantPromotionMappingRepository restaurantPromotionMappingRepository;

    @Autowired
    private MenuPromotionMappingRepository menuPromotionMappingRepository;

    @Autowired
    private PromotionTranslationRepository promotionTranslationRepository;

    @Autowired
    private MenuTranslationRepository menuTranslationRepository;

    @Autowired
    private OrderedItemRepository orderedItemRepository;

    @Autowired
    private ItemTranslationRepository itemTranslationRepository;

    @Autowired
    private CategoryTranslationRepository categoryTranslationRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private LocalizationProperties localizationProperties;

    @Autowired
    private RestaurantChainConfigProperties restaurantChainConfigProperties;

    @Autowired
    private MessageUtil messageUtil;

    @Autowired
    private RestaurantItemAvailabilityRepository restaurantItemAvailabilityRepository;

    @Autowired
    private UserShiftMappingRepository userShiftMappingRepository;

    @Autowired
    private ShiftRepository shiftRepository;

    @Autowired
    private CategoryItemMappingRepository categoryItemMappingRepository;

    @Autowired
    private RestaurantTableRepository restaurantTableRepository;

    @Autowired
    private MenuRepository menuRepository;

    @Autowired
    private RestaurantMenuMappingRepository restaurantMenuMappingRepository;

    @Autowired
    private RestaurantTranslationRepository restaurantTranslationRepository;

    @Autowired
    private OrderDiscountUsageRepository orderDiscountUsageRepository;

    @Autowired
    private RestaurantDiscountMappingRepository restaurantDiscountMappingRepository;

    @Autowired
    private MenuDiscountMappingRepository menuDiscountMappingRepository;

    @Autowired
    private RefundRepository refundRepository;

    @Autowired
    private OrderedComboRepository orderedComboRepository;

    @Autowired
    private AWSService awsService;

    // Thread pool for parallel execution
    private static final ExecutorService executorService = Executors.newFixedThreadPool(10);

    // Constants for duplicated string literals
    private static final String PERIOD_3_MONTHS = "3_MONTHS";
    private static final String PERIOD_6_MONTHS = "6_MONTHS";
    private static final String PERIOD_WEEKLY = "WEEKLY";
    private static final String PERIOD_MONTHLY = "MONTHLY";
    private static final String PERIOD_CUSTOM = "CUSTOM";
    private static final String DATE_TIME_FORMAT_PATTERN = "yyyy-MM-dd HH:mm:ss";
    private static final String FILTER_PERIOD = "period";
    private static final String FILTER_START_DATE = "startDate";
    private static final String FILTER_END_DATE = "endDate";
    private static final String FILTER_RESTAURANT_GROUP_ID = "restaurantGroupId";
    private static final String CSV_HEADER_STATUS = "Status";
    private static final String CSV_HEADER_METRIC = "Metric";
    private static final String LABEL_TOTAL_SALES = "Total Sales";
    private static final String PERIOD_30_DAYS = "30_DAYS";
    private static final String CSV_HEADER_VALUE = "Value";
    private static final String FILTER_RESTAURANT_ID = "restaurantId";
    private static final String DEFAULT_PERCENTAGE = "0%";
    private static final UUID SENTINEL_UUID = UUID.fromString("00000000-0000-0000-0000-000000000000");

    /**
     * Aggregates high-level dashboard statistics across restaurants, optionally scoped and time-filtered.
     * <p>
     * Supports scoping by {@code restaurantId} (highest priority) or {@code restaurantGroupId}. Time filtering can be
     * provided either as a named period (e.g., 30 days / 3 months / 6 months) or an explicit start/end date range.
     * Computes counts (restaurants, employees, items, orders by status) and sales totals, along with discount/promotion
     * counts and other dashboard summaries.
     * </p>
     *
     * @param period           optional period identifier (when dates are not provided)
     * @param startDate        optional custom start date-time (UTC)
     * @param endDate          optional custom end date-time (UTC)
     * @param restaurantGroupId optional restaurant group scope
     * @param restaurantId     optional restaurant scope (overrides group scope)
     * @param salesStatsPeriod optional secondary period selector for sales sub-statistics
     * @param locale           locale tag used for message localization
     * @return response wrapper containing aggregated {@link DashboardResponse}
     * @throws ResponseStatusException when filters are invalid or an unexpected error occurs
     */
    @Override
    public ResponseDto<DashboardResponse> getDashboardStatistics(String period, LocalDateTime startDate, LocalDateTime endDate, UUID restaurantGroupId, UUID restaurantId, String salesStatsPeriod, String locale) {
        // Set locale context for message localization
        Locale localeObj = Locale.forLanguageTag(locale != null ? locale : "en");
        LocaleContextHolder.setLocale(localeObj);
        
        try {
            logger.info("Fetching dashboard statistics with period: {}, startDate: {}, endDate: {}, restaurantGroupId: {}, restaurantId: {}, salesStatsPeriod: {}, locale: {}", 
                    period, startDate, endDate, restaurantGroupId, restaurantId, salesStatsPeriod, locale);

            // Validate restaurant ID and restaurant group ID
            validateRestaurantFilters(restaurantId, restaurantGroupId, localeObj);

            // Validate date parameters
            validateDateParameters(period, startDate, endDate, localeObj);

            // Count restaurants - filter by restaurant ID (highest priority), then restaurant group if provided
            long totalRestaurants;
            if (restaurantId != null) {
                totalRestaurants = restaurantRepository.countByRestaurantIdAndIsDeletedFalse(restaurantId);
            } else if (restaurantGroupId != null) {
                totalRestaurants = restaurantRepository.countByRestaurantGroupIdAndIsDeletedFalse(restaurantGroupId);
            } else {
                totalRestaurants = restaurantRepository.countByIsDeletedFalse();
            }

            // Count active employees - filter by restaurant ID (highest priority), then restaurant group if provided
            long activeEmployeesCount;
            if (restaurantId != null) {
                activeEmployeesCount = userRepository.countByRestaurantIdAndStatusAndIsDeletedFalse(restaurantId, EntityStatus.ACTIVE);
            } else if (restaurantGroupId != null) {
                activeEmployeesCount = userRepository.countByRestaurantGroupIdAndStatusAndIsDeletedFalse(restaurantGroupId, EntityStatus.ACTIVE);
            } else {
                activeEmployeesCount = userRepository.countByStatusAndIsDeletedFalse(EntityStatus.ACTIVE);
            }

            // Count items - filter by restaurant ID (highest priority), then restaurant group if provided
            long totalItems;
            if (restaurantId != null) {
                totalItems = itemRepository.countByRestaurantId(restaurantId);
            } else if (restaurantGroupId != null) {
                totalItems = itemRepository.countByRestaurantGroupId(restaurantGroupId);
            } else {
                totalItems = itemRepository.countByIsDeletedFalse();
            }

            // Check if period or date filter is provided
            boolean hasFilter = (period != null) || (startDate != null && endDate != null);
            
            BigDecimal totalSales;
            long activeDiscountsCount;
            long activePromotionsCount;

            if (hasFilter) {
                // Calculate filtered statistics based on period or date range
                PeriodStatistics filteredStats = calculatePeriodStatistics(period, startDate, endDate, restaurantGroupId, restaurantId);
                if (filteredStats != null) {
                    totalSales = filteredStats.getTotalSales();
                    activeDiscountsCount = filteredStats.getActiveDiscountsCount();
                    activePromotionsCount = filteredStats.getActivePromotionsCount();
                } else {
                    // Fallback to all-time if calculation fails
                    if (restaurantId != null) {
                        totalSales = transactionRepository.sumTransactionAmountByRestaurantIdAndStatus(restaurantId, TransactionStatus.COMPLETED);
                        if (totalSales == null) {
                            totalSales = BigDecimal.ZERO;
                        }
                        activeDiscountsCount = discountRepository.countByRestaurantIdAndStatusAndIsDeletedFalse(restaurantId, EntityStatus.ACTIVE);
                        activePromotionsCount = promotionRepository.countByRestaurantIdAndStatusAndIsDeletedFalse(restaurantId, EntityStatus.ACTIVE);
                    } else if (restaurantGroupId != null) {
                        totalSales = transactionRepository.sumTransactionAmountByRestaurantGroupIdAndStatus(restaurantGroupId, TransactionStatus.COMPLETED);
                        if (totalSales == null) {
                            totalSales = BigDecimal.ZERO;
                        }
                        activeDiscountsCount = discountRepository.countByRestaurantGroupIdAndStatusAndIsDeletedFalse(restaurantGroupId, EntityStatus.ACTIVE);
                        activePromotionsCount = promotionRepository.countByRestaurantGroupIdAndStatusAndIsDeletedFalse(restaurantGroupId, EntityStatus.ACTIVE);
                    } else {
                        totalSales = transactionRepository.sumTransactionAmountByStatus(TransactionStatus.COMPLETED);
                        if (totalSales == null) {
                            totalSales = BigDecimal.ZERO;
                        }
                        activeDiscountsCount = discountRepository.countByStatusAndIsDeletedFalse(EntityStatus.ACTIVE);
                        activePromotionsCount = promotionRepository.countByStatusAndIsDeletedFalse(EntityStatus.ACTIVE);
                    }
                }
            } else {
                // No filter - show all-time statistics
                if (restaurantId != null) {
                    totalSales = transactionRepository.sumTransactionAmountByRestaurantIdAndStatus(restaurantId, TransactionStatus.COMPLETED);
                    if (totalSales == null) {
                        totalSales = BigDecimal.ZERO;
                    }
                    activeDiscountsCount = discountRepository.countByRestaurantIdAndStatusAndIsDeletedFalse(restaurantId, EntityStatus.ACTIVE);
                    activePromotionsCount = promotionRepository.countByRestaurantIdAndStatusAndIsDeletedFalse(restaurantId, EntityStatus.ACTIVE);
                } else if (restaurantGroupId != null) {
                    totalSales = transactionRepository.sumTransactionAmountByRestaurantGroupIdAndStatus(restaurantGroupId, TransactionStatus.COMPLETED);
                    if (totalSales == null) {
                        totalSales = BigDecimal.ZERO;
                    }
                    activeDiscountsCount = discountRepository.countByRestaurantGroupIdAndStatusAndIsDeletedFalse(restaurantGroupId, EntityStatus.ACTIVE);
                    activePromotionsCount = promotionRepository.countByRestaurantGroupIdAndStatusAndIsDeletedFalse(restaurantGroupId, EntityStatus.ACTIVE);
                } else {
                    totalSales = transactionRepository.sumTransactionAmountByStatus(TransactionStatus.COMPLETED);
                    if (totalSales == null) {
                        totalSales = BigDecimal.ZERO;
                    }
                    activeDiscountsCount = discountRepository.countByStatusAndIsDeletedFalse(EntityStatus.ACTIVE);
                    activePromotionsCount = promotionRepository.countByStatusAndIsDeletedFalse(EntityStatus.ACTIVE);
                }
            }

            // Calculate individual order status counts based on transaction status
            long openOrdersCount;
            long completedOrdersCount;
            long pendingOrdersCount;
            long cancelledOrdersCount;
            long refundedOrdersCount;
            
            if (hasFilter) {
                // Use date range for status counts
                LocalDateTime periodStartDate;
                LocalDateTime periodEndDate = LocalDateTime.now(ZoneOffset.UTC).withHour(23).withMinute(59).withSecond(59).withNano(999999999);
                
                if (startDate != null && endDate != null) {
                    periodStartDate = startDate.withHour(0).withMinute(0).withSecond(0).withNano(0);
                    periodEndDate = endDate.withHour(23).withMinute(59).withSecond(59).withNano(999999999);
                } else if (period != null) {
                    switch (period.toUpperCase()) {
                        case PERIOD_30_DAYS:
                            periodStartDate = LocalDateTime.now(ZoneOffset.UTC).minusDays(30).withHour(0).withMinute(0).withSecond(0).withNano(0);
                            break;
                        case PERIOD_3_MONTHS:
                            periodStartDate = LocalDateTime.now(ZoneOffset.UTC).minusMonths(3).withHour(0).withMinute(0).withSecond(0).withNano(0);
                            break;
                        case PERIOD_6_MONTHS:
                            periodStartDate = LocalDateTime.now(ZoneOffset.UTC).minusMonths(6).withHour(0).withMinute(0).withSecond(0).withNano(0);
                            break;
                        default:
                            periodStartDate = null;
                            periodEndDate = null;
                    }
                } else {
                    periodStartDate = null;
                    periodEndDate = null;
                }
                
                if (periodStartDate != null && periodEndDate != null) {
                    OffsetDateTime periodStartUtc = periodStartDate.atOffset(ZoneOffset.UTC);
                    OffsetDateTime periodEndUtc = periodEndDate.atOffset(ZoneOffset.UTC);
                    if (restaurantId != null) {
                        openOrdersCount = transactionRepository.countByRestaurantIdAndTransactionStatusAndDateRange(
                                restaurantId, TransactionStatus.OPEN, periodStartUtc, periodEndUtc);
                        completedOrdersCount = transactionRepository.countByRestaurantIdAndTransactionStatusAndDateRange(
                                restaurantId, TransactionStatus.COMPLETED, periodStartUtc, periodEndUtc);
                        pendingOrdersCount = transactionRepository.countByRestaurantIdAndTransactionStatusAndDateRange(
                                restaurantId, TransactionStatus.PENDING, periodStartUtc, periodEndUtc);
                        cancelledOrdersCount = transactionRepository.countByRestaurantIdAndTransactionStatusAndDateRange(
                                restaurantId, TransactionStatus.CANCELED, periodStartUtc, periodEndUtc);
                        refundedOrdersCount = transactionRepository.countByRestaurantIdAndTransactionStatusAndDateRange(
                                restaurantId, TransactionStatus.REFUNDED, periodStartUtc, periodEndUtc);
                    } else if (restaurantGroupId != null) {
                        openOrdersCount = transactionRepository.countByRestaurantGroupIdAndTransactionStatusAndDateRange(
                                restaurantGroupId, TransactionStatus.OPEN, periodStartUtc, periodEndUtc);
                        completedOrdersCount = transactionRepository.countByRestaurantGroupIdAndTransactionStatusAndDateRange(
                                restaurantGroupId, TransactionStatus.COMPLETED, periodStartUtc, periodEndUtc);
                        pendingOrdersCount = transactionRepository.countByRestaurantGroupIdAndTransactionStatusAndDateRange(
                                restaurantGroupId, TransactionStatus.PENDING, periodStartUtc, periodEndUtc);
                        cancelledOrdersCount = transactionRepository.countByRestaurantGroupIdAndTransactionStatusAndDateRange(
                                restaurantGroupId, TransactionStatus.CANCELED, periodStartUtc, periodEndUtc);
                        refundedOrdersCount = transactionRepository.countByRestaurantGroupIdAndTransactionStatusAndDateRange(
                                restaurantGroupId, TransactionStatus.REFUNDED, periodStartUtc, periodEndUtc);
                    } else {
                        openOrdersCount = transactionRepository.countByTransactionStatusAndDateRange(
                                TransactionStatus.OPEN, periodStartUtc, periodEndUtc);
                        completedOrdersCount = transactionRepository.countByTransactionStatusAndDateRange(
                                TransactionStatus.COMPLETED, periodStartUtc, periodEndUtc);
                        pendingOrdersCount = transactionRepository.countByTransactionStatusAndDateRange(
                                TransactionStatus.PENDING, periodStartUtc, periodEndUtc);
                        cancelledOrdersCount = transactionRepository.countByTransactionStatusAndDateRange(
                                TransactionStatus.CANCELED, periodStartUtc, periodEndUtc);
                        refundedOrdersCount = transactionRepository.countByTransactionStatusAndDateRange(
                                TransactionStatus.REFUNDED, periodStartUtc, periodEndUtc);
                    }
                } else {
                    // Fallback to all-time if period calculation fails
                    if (restaurantId != null) {
                        openOrdersCount = transactionRepository.countByRestaurantIdAndTransactionStatus(
                                restaurantId, TransactionStatus.OPEN);
                        completedOrdersCount = transactionRepository.countByRestaurantIdAndTransactionStatus(
                                restaurantId, TransactionStatus.COMPLETED);
                        pendingOrdersCount = transactionRepository.countByRestaurantIdAndTransactionStatus(
                                restaurantId, TransactionStatus.PENDING);
                        cancelledOrdersCount = transactionRepository.countByRestaurantIdAndTransactionStatus(
                                restaurantId, TransactionStatus.CANCELED);
                        refundedOrdersCount = transactionRepository.countByRestaurantIdAndTransactionStatus(
                                restaurantId, TransactionStatus.REFUNDED);
                    } else if (restaurantGroupId != null) {
                        openOrdersCount = transactionRepository.countByRestaurantGroupIdAndTransactionStatus(
                                restaurantGroupId, TransactionStatus.OPEN);
                        completedOrdersCount = transactionRepository.countByRestaurantGroupIdAndTransactionStatus(
                                restaurantGroupId, TransactionStatus.COMPLETED);
                        pendingOrdersCount = transactionRepository.countByRestaurantGroupIdAndTransactionStatus(
                                restaurantGroupId, TransactionStatus.PENDING);
                        cancelledOrdersCount = transactionRepository.countByRestaurantGroupIdAndTransactionStatus(
                                restaurantGroupId, TransactionStatus.CANCELED);
                        refundedOrdersCount = transactionRepository.countByRestaurantGroupIdAndTransactionStatus(
                                restaurantGroupId, TransactionStatus.REFUNDED);
                    } else {
                        openOrdersCount = transactionRepository.countByTransactionStatus(TransactionStatus.OPEN);
                        completedOrdersCount = transactionRepository.countByTransactionStatus(TransactionStatus.COMPLETED);
                        pendingOrdersCount = transactionRepository.countByTransactionStatus(TransactionStatus.PENDING);
                        cancelledOrdersCount = transactionRepository.countByTransactionStatus(TransactionStatus.CANCELED);
                        refundedOrdersCount = transactionRepository.countByTransactionStatus(TransactionStatus.REFUNDED);
                    }
                }
            } else {
                // No date filter - show all-time statistics
                if (restaurantId != null) {
                    openOrdersCount = transactionRepository.countByRestaurantIdAndTransactionStatus(
                            restaurantId, TransactionStatus.OPEN);
                    completedOrdersCount = transactionRepository.countByRestaurantIdAndTransactionStatus(
                            restaurantId, TransactionStatus.COMPLETED);
                    pendingOrdersCount = transactionRepository.countByRestaurantIdAndTransactionStatus(
                            restaurantId, TransactionStatus.PENDING);
                    cancelledOrdersCount = transactionRepository.countByRestaurantIdAndTransactionStatus(
                            restaurantId, TransactionStatus.CANCELED);
                    refundedOrdersCount = transactionRepository.countByRestaurantIdAndTransactionStatus(
                            restaurantId, TransactionStatus.REFUNDED);
                } else if (restaurantGroupId != null) {
                    openOrdersCount = transactionRepository.countByRestaurantGroupIdAndTransactionStatus(
                            restaurantGroupId, TransactionStatus.OPEN);
                    completedOrdersCount = transactionRepository.countByRestaurantGroupIdAndTransactionStatus(
                            restaurantGroupId, TransactionStatus.COMPLETED);
                    pendingOrdersCount = transactionRepository.countByRestaurantGroupIdAndTransactionStatus(
                            restaurantGroupId, TransactionStatus.PENDING);
                    cancelledOrdersCount = transactionRepository.countByRestaurantGroupIdAndTransactionStatus(
                            restaurantGroupId, TransactionStatus.CANCELED);
                    refundedOrdersCount = transactionRepository.countByRestaurantGroupIdAndTransactionStatus(
                            restaurantGroupId, TransactionStatus.REFUNDED);
                } else {
                    openOrdersCount = transactionRepository.countByTransactionStatus(TransactionStatus.OPEN);
                    completedOrdersCount = transactionRepository.countByTransactionStatus(TransactionStatus.COMPLETED);
                    pendingOrdersCount = transactionRepository.countByTransactionStatus(TransactionStatus.PENDING);
                    cancelledOrdersCount = transactionRepository.countByTransactionStatus(TransactionStatus.CANCELED);
                    refundedOrdersCount = transactionRepository.countByTransactionStatus(TransactionStatus.REFUNDED);
                }
            }
            
            // Calculate totalOrders as sum of all transaction status counts
            long totalOrders = openOrdersCount + completedOrdersCount + pendingOrdersCount + cancelledOrdersCount + refundedOrdersCount;

            // Calculate percentages for each order status
            BigDecimal totalOrdersDecimal = BigDecimal.valueOf(totalOrders);
            BigDecimal openOrdersPercentage = BigDecimal.ZERO;
            BigDecimal completedOrdersPercentage = BigDecimal.ZERO;
            BigDecimal pendingOrdersPercentage = BigDecimal.ZERO;
            BigDecimal cancelledOrdersPercentage = BigDecimal.ZERO;
            BigDecimal refundedOrdersPercentage = BigDecimal.ZERO;
            
            if (totalOrders > 0) {
                // Calculate percentage: (count / total) * 100, rounded to 2 decimal places
                openOrdersPercentage = BigDecimal.valueOf(openOrdersCount)
                        .divide(totalOrdersDecimal, 4, java.math.RoundingMode.HALF_UP)
                        .multiply(BigDecimal.valueOf(100))
                        .setScale(2, java.math.RoundingMode.HALF_UP);
                
                completedOrdersPercentage = BigDecimal.valueOf(completedOrdersCount)
                        .divide(totalOrdersDecimal, 4, java.math.RoundingMode.HALF_UP)
                        .multiply(BigDecimal.valueOf(100))
                        .setScale(2, java.math.RoundingMode.HALF_UP);
                
                pendingOrdersPercentage = BigDecimal.valueOf(pendingOrdersCount)
                        .divide(totalOrdersDecimal, 4, java.math.RoundingMode.HALF_UP)
                        .multiply(BigDecimal.valueOf(100))
                        .setScale(2, java.math.RoundingMode.HALF_UP);
                
                cancelledOrdersPercentage = BigDecimal.valueOf(cancelledOrdersCount)
                        .divide(totalOrdersDecimal, 4, java.math.RoundingMode.HALF_UP)
                        .multiply(BigDecimal.valueOf(100))
                        .setScale(2, java.math.RoundingMode.HALF_UP);
                
                refundedOrdersPercentage = BigDecimal.valueOf(refundedOrdersCount)
                        .divide(totalOrdersDecimal, 4, java.math.RoundingMode.HALF_UP)
                        .multiply(BigDecimal.valueOf(100))
                        .setScale(2, java.math.RoundingMode.HALF_UP);
            }

            // Build order status counts object with percentages
            OrderStatusCounts orderStatusCounts = OrderStatusCounts.builder()
                    .openOrdersCount(openOrdersCount)
                    .openOrdersPercentage(openOrdersPercentage)
                    .completedOrdersCount(completedOrdersCount)
                    .completedOrdersPercentage(completedOrdersPercentage)
                    .pendingOrdersCount(pendingOrdersCount)
                    .pendingOrdersPercentage(pendingOrdersPercentage)
                    .cancelledOrdersCount(cancelledOrdersCount)
                    .cancelledOrdersPercentage(cancelledOrdersPercentage)
                    .refundedOrdersCount(refundedOrdersCount)
                    .refundedOrdersPercentage(refundedOrdersPercentage)
                    .build();

            // Calculate employee role counts
            EmployeeRoleCounts employeeRoleCounts = calculateEmployeeRoleCounts(restaurantGroupId, restaurantId, activeEmployeesCount);

            // Calculate promotion statistics
            PromotionStats promotionStats = calculatePromotionStats(restaurantGroupId, restaurantId, locale);

            // Calculate discount statistics
            DiscountStats discountStats = calculateDiscountStats(restaurantGroupId, restaurantId, period, startDate, endDate);

            // Calculate menu performance (top 5 items)
            MenuPerformance menuPerformance = calculateMenuPerformance(restaurantGroupId, restaurantId, locale, period, startDate, endDate);

            // Calculate sales statistics (daily, weekly, or monthly)
            SalesStats salesStats = calculateSalesStats(salesStatsPeriod, restaurantGroupId, restaurantId);

            // Calculate total refund amount
            BigDecimal totalRefund = calculateTotalRefund(period, startDate, endDate, restaurantGroupId, restaurantId);

            // Calculate void management (wastage statistics)
            VoidManagement voidManagement = calculateVoidManagement(period, startDate, endDate, restaurantGroupId, restaurantId);

            String currency = restaurantChainConfigProperties.getChain() != null ? restaurantChainConfigProperties.getChain().getCurrency() : null;
            DashboardResponse dashboardResponse = DashboardResponse.builder()
                    .totalRestaurants(totalRestaurants)
                    .totalOrders(totalOrders)
                    .totalSales(totalSales != null ? CurrencyFormatter.formatAmount(totalSales, currency) : null)
                    .activeEmployeesCount(activeEmployeesCount)
                    .totalItems(totalItems)
                    .activeDiscountsCount(activeDiscountsCount)
                    .activePromotionsCount(activePromotionsCount)
                    .periodStatistics(null) // Always null - filtered data is in main fields
                    .orderStatusCounts(orderStatusCounts)
                    .employeeRoleCounts(employeeRoleCounts)
                    .promotionStats(promotionStats)
                    .discountStats(discountStats)
                    .menuPerformance(menuPerformance)
                    .salesStats(salesStats)
                    .totalRefund(totalRefund != null ? CurrencyFormatter.formatAmount(totalRefund, currency) : null)
                    .voidManagement(voidManagement)
                    .build();

            logger.info("Dashboard statistics retrieved successfully - Restaurants: {}, Orders: {}, Sales: {}, Active Employees: {}, Items: {}, Active Discounts: {}, Active Promotions: {}",
                    dashboardResponse.getTotalRestaurants(),
                    dashboardResponse.getTotalOrders(),
                    dashboardResponse.getTotalSales(),
                    dashboardResponse.getActiveEmployeesCount(),
                    dashboardResponse.getTotalItems(),
                    dashboardResponse.getActiveDiscountsCount(),
                    dashboardResponse.getActivePromotionsCount());

            return ResponseDto.<DashboardResponse>builder()
                    .data(dashboardResponse)
                    .message(messageUtil.getMessage("dashboard.statistics.retrieved.success", localeObj))
                    .build();

        } catch (ResponseStatusException e) {
            // Re-throw ResponseStatusException to preserve HTTP status codes
            throw e;
        } catch (Exception e) {
            logger.error("Error fetching dashboard statistics", e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    messageUtil.getMessage("dashboard.statistics.error.general", localeObj));
        }
    }

    /**
     * Validate restaurant ID and restaurant group ID parameters
     */
    private void validateRestaurantFilters(UUID restaurantId, UUID restaurantGroupId, Locale locale) {
        // Validate restaurant ID if provided
        if (restaurantId != null) {
            restaurantRepository.findByIdAndIsDeletedFalse(restaurantId)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                            messageUtil.getMessage("restaurant.get.error.notfound", locale)));
        }

        // Validate restaurant group ID if provided
        if (restaurantGroupId != null) {
            restaurantGroupRepository.findByIdAndIsDeletedFalse(restaurantGroupId)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                            messageUtil.getMessage("restaurant.group.not.found", locale)));
        }
    }

    /**
     * Validates the combination of period and explicit date parameters for dashboard queries.
     * <p>
     * Enforces that custom ranges provide both start and end, that start is not after end, and that dates are not
     * unreasonably far in the future or spanning an excessively large range. If no explicit dates are provided,
     * validates that {@code period} is one of the supported identifiers.
     * </p>
     *
     * @param period    optional period identifier
     * @param startDate optional start date-time (UTC)
     * @param endDate   optional end date-time (UTC)
     * @param locale    locale used for localized validation messages
     * @throws ResponseStatusException when validation fails
     */
    private void validateDateParameters(String period, LocalDateTime startDate, LocalDateTime endDate, Locale locale) {
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);

        // If custom date range is provided, both dates must be present
        if (startDate != null && endDate == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, 
                messageUtil.getMessage("dashboard.error.enddate.required", locale));
        }

        if (endDate != null && startDate == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, 
                messageUtil.getMessage(MSG_DASHBOARD_ERROR_STARTDATE_REQUIRED, locale));
        }

        // Validate custom date range
        if (startDate != null && endDate != null) {
            // Start date should not be after end date
            if (startDate.isAfter(endDate)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, 
                    messageUtil.getMessage("dashboard.error.startdate.after.enddate", locale));
            }

            // Dates should not be in the future
            if (startDate.isAfter(now)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, 
                    messageUtil.getMessage("dashboard.error.startdate.future", locale));
            }

            // Validate date range is not too large (e.g., more than 10 years)
            long daysBetween = java.time.temporal.ChronoUnit.DAYS.between(startDate, endDate);
            if (daysBetween > 3650) { // 10 years
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, 
                    messageUtil.getMessage("dashboard.error.daterange.exceeded", locale));
            }
        }

        // Validate period parameter if provided
        // Restaurant dashboard periods: WEEKLY, MONTHLY, CUSTOM
        // Other dashboard periods: 30_DAYS, 3_MONTHS, 6_MONTHS
        if (period != null && startDate == null && endDate == null
                && !PERIOD_30_DAYS.equalsIgnoreCase(period)
                && !PERIOD_3_MONTHS.equalsIgnoreCase(period)
                && !PERIOD_6_MONTHS.equalsIgnoreCase(period)
                && !PERIOD_WEEKLY.equalsIgnoreCase(period)
                && !PERIOD_MONTHLY.equalsIgnoreCase(period)
                && !PERIOD_CUSTOM.equalsIgnoreCase(period)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, 
                messageUtil.getMessage("dashboard.error.invalid.period", locale));
        }
        
        // Validate end date is not too far in the future
        if (endDate != null && endDate.isAfter(now.plusDays(1))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, 
                messageUtil.getMessage("dashboard.error.enddate.future", locale));
        }
    }

    /**
     * Validate date parameters for menu dashboard
     * Similar to validateDateParameters but adapted for dateRange parameter
     */
    private void validateDateParametersForMenuDashboard(String dateRange, LocalDateTime startDate, LocalDateTime endDate, Locale locale) {
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);

        // If CUSTOM date range is specified, both dates must be present
        if (PERIOD_CUSTOM.equalsIgnoreCase(dateRange) && (startDate == null || endDate == null)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    messageUtil.getMessage(MSG_DASHBOARD_ERROR_STARTDATE_REQUIRED, locale));
        }

        // If custom date range is provided (either via dateRange=CUSTOM or just startDate/endDate), both dates must be present
        if (startDate != null && endDate == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    messageUtil.getMessage("dashboard.error.enddate.required", locale));
        }

        if (endDate != null && startDate == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    messageUtil.getMessage(MSG_DASHBOARD_ERROR_STARTDATE_REQUIRED, locale));
        }

        // Validate custom date range
        if (startDate != null && endDate != null) {
            // Start date should not be after end date
            if (startDate.isAfter(endDate)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        messageUtil.getMessage("dashboard.error.startdate.after.enddate", locale));
            }

            // Dates should not be in the future
            if (startDate.isAfter(now)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        messageUtil.getMessage("dashboard.error.startdate.future", locale));
            }
            
            // End date should not be too far in the future (allow up to end of today)
            if (endDate.isAfter(now.plusDays(1))) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        messageUtil.getMessage("dashboard.error.enddate.future", locale));
            }

            // Validate date range is not too large (e.g., more than 10 years)
            long daysBetween = java.time.temporal.ChronoUnit.DAYS.between(startDate, endDate);
            if (daysBetween > 3650) { // 10 years
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        messageUtil.getMessage("dashboard.error.daterange.exceeded", locale));
            }
        }

        // Validate dateRange parameter if provided
        if (dateRange != null && !dateRange.isEmpty()) {
            String upperDateRange = dateRange.toUpperCase();
            if (!upperDateRange.equals(PERIOD_30_DAYS) &&
                !upperDateRange.equals("1_MONTH") &&
                !upperDateRange.equals(PERIOD_3_MONTHS) &&
                !upperDateRange.equals(PERIOD_CUSTOM)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        messageUtil.getMessage("dashboard.error.invalid.daterange", locale));
            }
        }
    }

    /**
     * Calculates time-windowed dashboard statistics for a given period or explicit start/end date range.
     * <p>
     * Determines the effective period start/end, then queries transaction/order counts and sums within the window,
     * scoped by restaurant or restaurant group when provided. Returns {@code null} when the requested period is not
     * recognized and no explicit date range was provided.
     * </p>
     *
     * @param period            optional period identifier (ignored when {@code startDate}/{@code endDate} are provided)
     * @param startDate         optional custom start date-time (UTC)
     * @param endDate           optional custom end date-time (UTC)
     * @param restaurantGroupId optional group scope
     * @param restaurantId      optional restaurant scope (overrides group scope)
     * @return computed period statistics or {@code null} when no valid period/range is available
     */
    private PeriodStatistics calculatePeriodStatistics(String period, LocalDateTime startDate, LocalDateTime endDate, UUID restaurantGroupId, UUID restaurantId) {
        LocalDateTime periodStartDate;
        LocalDateTime periodEndDate = LocalDateTime.now(ZoneOffset.UTC).withHour(23).withMinute(59).withSecond(59).withNano(999999999);

        // Determine date range based on period parameter
        if (startDate != null && endDate != null) {
            // Custom date range
            periodStartDate = startDate.withHour(0).withMinute(0).withSecond(0).withNano(0);
            periodEndDate = endDate.withHour(23).withMinute(59).withSecond(59).withNano(999999999);
        } else if (period != null) {
            switch (period.toUpperCase()) {
                case PERIOD_30_DAYS:
                    periodStartDate = LocalDateTime.now(ZoneOffset.UTC).minusDays(30).withHour(0).withMinute(0).withSecond(0).withNano(0);
                    break;
                case PERIOD_3_MONTHS:
                    periodStartDate = LocalDateTime.now(ZoneOffset.UTC).minusMonths(3).withHour(0).withMinute(0).withSecond(0).withNano(0);
                    break;
                case PERIOD_6_MONTHS:
                    periodStartDate = LocalDateTime.now(ZoneOffset.UTC).minusMonths(6).withHour(0).withMinute(0).withSecond(0).withNano(0);
                    break;
                default:
                    // If period is not recognized, return null period statistics
                    return null;
            }
        } else {
            // No period specified, return null
            return null;
        }

        logger.info("Calculating period statistics from {} to {} for restaurantGroupId: {}, restaurantId: {}", periodStartDate, periodEndDate, restaurantGroupId, restaurantId);

        try {
            // Count orders within the period from Transaction table - filter by restaurant ID (highest priority), then restaurant group if provided
            // Calculate as sum of all transaction status counts
            long periodOpenOrders;
            long periodCompletedOrders;
            long periodPendingOrders;
            long periodCancelledOrders;
            long periodRefundedOrders;
            
            OffsetDateTime periodStartUtc = periodStartDate.atOffset(ZoneOffset.UTC);
            OffsetDateTime periodEndUtc = periodEndDate.atOffset(ZoneOffset.UTC);
            if (restaurantId != null) {
                periodOpenOrders = transactionRepository.countByRestaurantIdAndTransactionStatusAndDateRange(
                        restaurantId, TransactionStatus.OPEN, periodStartUtc, periodEndUtc);
                periodCompletedOrders = transactionRepository.countByRestaurantIdAndTransactionStatusAndDateRange(
                        restaurantId, TransactionStatus.COMPLETED, periodStartUtc, periodEndUtc);
                periodPendingOrders = transactionRepository.countByRestaurantIdAndTransactionStatusAndDateRange(
                        restaurantId, TransactionStatus.PENDING, periodStartUtc, periodEndUtc);
                periodCancelledOrders = transactionRepository.countByRestaurantIdAndTransactionStatusAndDateRange(
                        restaurantId, TransactionStatus.CANCELED, periodStartUtc, periodEndUtc);
                periodRefundedOrders = transactionRepository.countByRestaurantIdAndTransactionStatusAndDateRange(
                        restaurantId, TransactionStatus.REFUNDED, periodStartUtc, periodEndUtc);
            } else if (restaurantGroupId != null) {
                periodOpenOrders = transactionRepository.countByRestaurantGroupIdAndTransactionStatusAndDateRange(
                        restaurantGroupId, TransactionStatus.OPEN, periodStartUtc, periodEndUtc);
                periodCompletedOrders = transactionRepository.countByRestaurantGroupIdAndTransactionStatusAndDateRange(
                        restaurantGroupId, TransactionStatus.COMPLETED, periodStartUtc, periodEndUtc);
                periodPendingOrders = transactionRepository.countByRestaurantGroupIdAndTransactionStatusAndDateRange(
                        restaurantGroupId, TransactionStatus.PENDING, periodStartUtc, periodEndUtc);
                periodCancelledOrders = transactionRepository.countByRestaurantGroupIdAndTransactionStatusAndDateRange(
                        restaurantGroupId, TransactionStatus.CANCELED, periodStartUtc, periodEndUtc);
                periodRefundedOrders = transactionRepository.countByRestaurantGroupIdAndTransactionStatusAndDateRange(
                        restaurantGroupId, TransactionStatus.REFUNDED, periodStartUtc, periodEndUtc);
            } else {
                periodOpenOrders = transactionRepository.countByTransactionStatusAndDateRange(
                        TransactionStatus.OPEN, periodStartUtc, periodEndUtc);
                periodCompletedOrders = transactionRepository.countByTransactionStatusAndDateRange(
                        TransactionStatus.COMPLETED, periodStartUtc, periodEndUtc);
                periodPendingOrders = transactionRepository.countByTransactionStatusAndDateRange(
                        TransactionStatus.PENDING, periodStartUtc, periodEndUtc);
                periodCancelledOrders = transactionRepository.countByTransactionStatusAndDateRange(
                        TransactionStatus.CANCELED, periodStartUtc, periodEndUtc);
                periodRefundedOrders = transactionRepository.countByTransactionStatusAndDateRange(
                        TransactionStatus.REFUNDED, periodStartUtc, periodEndUtc);
            }
            
            long periodTotalOrders = periodOpenOrders + periodCompletedOrders + periodPendingOrders + periodCancelledOrders + periodRefundedOrders;

            // Calculate total sales from completed transactions within the period - filter by restaurant ID (highest priority), then restaurant group if provided
            BigDecimal periodTotalSales;
            if (restaurantId != null) {
                periodTotalSales = transactionRepository.sumTransactionAmountByRestaurantIdAndStatusAndDateRange(
                        restaurantId, TransactionStatus.COMPLETED, periodStartUtc, periodEndUtc);
            } else if (restaurantGroupId != null) {
                periodTotalSales = transactionRepository.sumTransactionAmountByRestaurantGroupIdAndStatusAndDateRange(
                        restaurantGroupId, TransactionStatus.COMPLETED, periodStartUtc, periodEndUtc);
            } else {
                periodTotalSales = transactionRepository.sumTransactionAmountByStatusAndDateRange(
                        TransactionStatus.COMPLETED, periodStartUtc, periodEndUtc);
            }
            if (periodTotalSales == null) {
                periodTotalSales = BigDecimal.ZERO;
            }

            // Count active discounts used in transactions within the period - filter by restaurant ID (highest priority), then restaurant group if provided
            long periodActiveDiscountsCount;
            if (restaurantId != null) {
                periodActiveDiscountsCount = discountRepository.countByRestaurantIdAndStatusAndIsDeletedFalse(restaurantId, EntityStatus.ACTIVE);
            } else if (restaurantGroupId != null) {
                periodActiveDiscountsCount = discountRepository.countByRestaurantGroupIdAndStatusAndIsDeletedFalse(restaurantGroupId, EntityStatus.ACTIVE);
            } else {
                periodActiveDiscountsCount = discountRepository.countByStatusAndIsDeletedFalse(EntityStatus.ACTIVE);
            }

            // Count active promotions used in transactions within the period - filter by restaurant ID (highest priority), then restaurant group if provided
            long periodActivePromotionsCount;
            if (restaurantId != null) {
                periodActivePromotionsCount = promotionRepository.countByRestaurantIdAndStatusAndIsDeletedFalse(restaurantId, EntityStatus.ACTIVE);
            } else if (restaurantGroupId != null) {
                periodActivePromotionsCount = promotionRepository.countByRestaurantGroupIdAndStatusAndIsDeletedFalse(restaurantGroupId, EntityStatus.ACTIVE);
            } else {
                periodActivePromotionsCount = promotionRepository.countByStatusAndIsDeletedFalse(EntityStatus.ACTIVE);
            }

            // Return statistics with 0 values if no data found (instead of null)
            String currency = restaurantChainConfigProperties.getChain() != null ? restaurantChainConfigProperties.getChain().getCurrency() : null;
            return PeriodStatistics.builder()
                    .totalOrders(periodTotalOrders)
                    .totalSales(periodTotalSales != null ? CurrencyFormatter.formatAmount(periodTotalSales, currency) : null)
                    .activeDiscountsCount(periodActiveDiscountsCount)
                    .activePromotionsCount(periodActivePromotionsCount)
                    .build();
        } catch (Exception e) {
            logger.warn("Error calculating period statistics, returning zero values. Error: {}", e.getMessage());
            // Return zero values if there's any error in calculation
            String currency = restaurantChainConfigProperties.getChain() != null ? restaurantChainConfigProperties.getChain().getCurrency() : null;
            return PeriodStatistics.builder()
                    .totalOrders(0L)
                    .totalSales(CurrencyFormatter.formatAmount(BigDecimal.ZERO, currency))
                    .activeDiscountsCount(0L)
                    .activePromotionsCount(0L)
                    .build();
        }
    }

    /**
     * Calculate employee role counts (with or without restaurant group filter)
     * @param restaurantGroupId Optional restaurant group ID to filter by
     * @param totalActiveEmployees Total active employees count (already calculated) - used to ensure consistency
     */
    private EmployeeRoleCounts calculateEmployeeRoleCounts(UUID restaurantGroupId, UUID restaurantId, long totalActiveEmployees) {
        try {
            // Get all roles from the database
            List<Role> allRoles = roleRepository.findAll();
            
            // Get employee counts by role
            List<Object[]> roleCountResults;
            if (restaurantId != null) {
                roleCountResults = userRepository.countByRoleIdAndRestaurantIdAndStatusAndIsDeletedFalse(
                        restaurantId, EntityStatus.ACTIVE);
            } else if (restaurantGroupId != null) {
                roleCountResults = userRepository.countByRoleIdAndRestaurantGroupIdAndStatusAndIsDeletedFalse(
                        restaurantGroupId, EntityStatus.ACTIVE);
            } else {
                roleCountResults = userRepository.countByRoleIdAndStatusAndIsDeletedFalse(EntityStatus.ACTIVE);
            }
            
            // Create a map of roleId -> count for quick lookup
            Map<UUID, Long> roleCountMap = roleCountResults.stream()
                    .collect(Collectors.toMap(
                            result -> (UUID) result[0],
                            result -> ((Number) result[1]).longValue()
                    ));
            
            // Build list of EmployeeRoleCount objects
            List<EmployeeRoleCount> roleCounts = new ArrayList<>();
            
            // Calculate percentage for each role count
            BigDecimal totalEmployeesDecimal = BigDecimal.valueOf(totalActiveEmployees);
            BigDecimal rolePercentage = BigDecimal.ZERO;
            
            // First, add all roles from the role table (even if they have 0 employees)
            for (Role role : allRoles) {
                UUID roleId = role.getId();
                Long count = roleCountMap.getOrDefault(roleId, 0L);
                
                // Calculate percentage: (count / totalActiveEmployees) * 100, rounded to 2 decimal places
                if (totalActiveEmployees > 0) {
                    rolePercentage = BigDecimal.valueOf(count)
                            .divide(totalEmployeesDecimal, 4, java.math.RoundingMode.HALF_UP)
                            .multiply(BigDecimal.valueOf(100))
                            .setScale(2, java.math.RoundingMode.HALF_UP);
                } else {
                    rolePercentage = BigDecimal.ZERO;
                }
                
                roleCounts.add(EmployeeRoleCount.builder()
                        .roleName(role.getName())
                        .count(count)
                        .percentage(rolePercentage)
                        .build());
            }
            
            // Also include roles that have employees but might not be in the allRoles list
            // (in case of data inconsistency, though this should be rare)
            for (Object[] result : roleCountResults) {
                UUID roleId = (UUID) result[0];
                boolean roleExists = allRoles.stream().anyMatch(r -> r.getId().equals(roleId));
                if (!roleExists) {
                    // Role exists in user data but not in role table - still include it
                    Long count = ((Number) result[1]).longValue();
                    
                    // Calculate percentage: (count / totalActiveEmployees) * 100, rounded to 2 decimal places
                    if (totalActiveEmployees > 0) {
                        rolePercentage = BigDecimal.valueOf(count)
                                .divide(totalEmployeesDecimal, 4, java.math.RoundingMode.HALF_UP)
                                .multiply(BigDecimal.valueOf(100))
                                .setScale(2, java.math.RoundingMode.HALF_UP);
                    } else {
                        rolePercentage = BigDecimal.ZERO;
                    }
                    
                    roleCounts.add(EmployeeRoleCount.builder()
                            .roleName("Unknown Role") // Placeholder name
                            .count(count)
                            .percentage(rolePercentage)
                            .build());
                }
            }
            
            // Use the totalActiveEmployees passed as parameter to ensure consistency
            // (This includes all active employees, even those without roles)
            
            return EmployeeRoleCounts.builder()
                    .totalActiveEmployees(totalActiveEmployees)
                    .roleCounts(roleCounts)
                    .build();
                    
        } catch (Exception e) {
            logger.warn("Error calculating employee role counts, returning empty result. Error: {}", e.getMessage());
            // Return empty result if there's any error
            return EmployeeRoleCounts.builder()
                    .totalActiveEmployees(totalActiveEmployees) // Still use the passed value
                    .roleCounts(new ArrayList<>())
                    .build();
        }
    }

    /**
     * Promotion statistics source:
     * - if only restaurantGroupId is provided (restaurantId absent), use menu-level mapping availability
     *   from {@code menu_promotion_mapping} and skip active/inactive status filtering.
     * - otherwise, use {@code restaurant_promotion_mapping}.
     */
    private PromotionStats calculatePromotionStats(UUID restaurantGroupId, UUID restaurantId, String locale) {
        try {
            OffsetDateTime currentTime = OffsetDateTime.now(ZoneOffset.UTC);

            if (restaurantId == null && restaurantGroupId != null) {
                List<MenuPromotionMapping> allMpm = menuPromotionMappingRepository
                        .findAllForDashboardPromotionStatsByRestaurantGroupIdNoStatus(restaurantGroupId);
                List<MenuPromotionMapping> activeMpm = new ArrayList<>();
                List<MenuPromotionMapping> upcomingMpm = new ArrayList<>();

                for (MenuPromotionMapping mpm : allMpm) {
                    int bucket = classifyPromotionAvailabilityWindow(mpm.getValidFrom(), mpm.getValidTo(), currentTime);
                    if (bucket == 0) {
                        activeMpm.add(mpm);
                    } else if (bucket == 1) {
                        upcomingMpm.add(mpm);
                    }
                }

                List<PromotionDetail> activePromotionDetails = buildPromotionDetailsFromMenuMappings(
                        activeMpm, locale, "Active");
                List<PromotionDetail> upcomingPromotionDetails = buildPromotionDetailsFromMenuMappings(
                        upcomingMpm, locale, "Upcoming");

                return PromotionStats.builder()
                        .activePromotionsCount((long) activeMpm.size())
                        .upcomingPromotionsCount((long) upcomingMpm.size())
                        .activePromotions(activePromotionDetails)
                        .upcomingPromotions(upcomingPromotionDetails)
                        .build();
            } else {
                List<RestaurantPromotionMapping> allRpm = restaurantPromotionMappingRepository
                        .findAllForDashboardPromotionStats(restaurantGroupId, restaurantId);
                List<RestaurantPromotionMapping> activeRpm = new ArrayList<>();
                List<RestaurantPromotionMapping> upcomingRpm = new ArrayList<>();
                for (RestaurantPromotionMapping rpm : allRpm) {
                    int bucket = classifyPromotionAvailabilityWindow(rpm.getValidFrom(), rpm.getValidTo(), currentTime);
                    if (bucket == 0) {
                        activeRpm.add(rpm);
                    } else if (bucket == 1) {
                        upcomingRpm.add(rpm);
                    }
                }
                Set<UUID> restaurantIds = allRpm.stream()
                        .map(rpm -> rpm.getRestaurant().getId())
                        .collect(Collectors.toSet());
                Map<UUID, List<RestaurantTranslation>> translationsByRestaurantId = Collections.emptyMap();
                if (!restaurantIds.isEmpty()) {
                    List<RestaurantTranslation> batch = restaurantTranslationRepository.findAllByRestaurantIdIn(
                            new ArrayList<>(restaurantIds));
                    translationsByRestaurantId = batch.stream()
                            .collect(Collectors.groupingBy(rt -> rt.getRestaurant().getId()));
                }
                List<PromotionDetail> activePromotionDetails = buildPromotionDetailsFromRestaurantMappings(
                        activeRpm, locale, "Active", translationsByRestaurantId);
                List<PromotionDetail> upcomingPromotionDetails = buildPromotionDetailsFromRestaurantMappings(
                        upcomingRpm, locale, "Upcoming", translationsByRestaurantId);
                return PromotionStats.builder()
                        .activePromotionsCount((long) activeRpm.size())
                        .upcomingPromotionsCount((long) upcomingRpm.size())
                        .activePromotions(activePromotionDetails)
                        .upcomingPromotions(upcomingPromotionDetails)
                        .build();
            }

        } catch (Exception e) {
            logger.warn("Error calculating promotion statistics, returning empty result. Error: {}", e.getMessage());
            return PromotionStats.builder()
                    .activePromotionsCount(0L)
                    .upcomingPromotionsCount(0L)
                    .activePromotions(new ArrayList<>())
                    .upcomingPromotions(new ArrayList<>())
                    .build();
        }
    }

    /**
     * @return 0 = currently within {@code validFrom..validTo} (inclusive), 1 = upcoming ({@code validFrom} &gt; now),
     *         2 = expired or otherwise excluded
     */
    private int classifyPromotionAvailabilityWindow(OffsetDateTime validFrom, OffsetDateTime validTo, OffsetDateTime currentTimeUtc) {
        if (validFrom != null && validTo != null) {
            OffsetDateTime validFromUtc = validFrom.withOffsetSameInstant(ZoneOffset.UTC);
            OffsetDateTime validToUtc = validTo.withOffsetSameInstant(ZoneOffset.UTC);
            if ((validFromUtc.isBefore(currentTimeUtc) || validFromUtc.isEqual(currentTimeUtc))
                    && (validToUtc.isAfter(currentTimeUtc) || validToUtc.isEqual(currentTimeUtc))) {
                return 0;
            }
            if (validFromUtc.isAfter(currentTimeUtc)) {
                return 1;
            }
            return 2;
        }
        return 0;
    }

    /**
     * Build promotion details from {@link RestaurantPromotionMapping}; {@code menuName} carries the localized
     * restaurant name for this dashboard row.
     */
    private List<PromotionDetail> buildPromotionDetailsFromRestaurantMappings(
            List<RestaurantPromotionMapping> mappings,
            String locale,
            String status,
            Map<UUID, List<RestaurantTranslation>> translationsByRestaurantId) {
        List<PromotionDetail> details = new ArrayList<>();

        for (RestaurantPromotionMapping rpm : mappings) {
            try {
                Promotion promotion = rpm.getPromotion();
                UUID restaurantUuid = rpm.getRestaurant().getId();

                List<PromotionTranslation> translations = promotionTranslationRepository.findAllByPromotionId(promotion.getId());
                String promotionName = "";
                String description = "";

                if (!translations.isEmpty()) {
                    Optional<PromotionTranslation> translation = TranslationUtils.pickPreferredOrFromListNonBlank(
                            translations,
                            locale,
                            localizationProperties.getLanguages(),
                            PromotionTranslation::getLanguageCode,
                            PromotionTranslation::getName
                    );
                    if (translation.isPresent()) {
                        PromotionTranslation selected = translation.get();
                        promotionName = selected.getName() != null ? selected.getName() : "";
                        description = selected.getDescription() != null ? selected.getDescription() : "";
                    }
                }

                String restaurantDisplayName = "";
                List<RestaurantTranslation> restaurantTranslations =
                        translationsByRestaurantId.getOrDefault(restaurantUuid, Collections.emptyList());
                if (!restaurantTranslations.isEmpty()) {
                    Optional<RestaurantTranslation> rt = TranslationUtils.pickPreferredOrFromList(
                            restaurantTranslations,
                            locale,
                            localizationProperties.getLanguages(),
                            RestaurantTranslation::getLanguageCode
                    );
                    if (rt.isPresent()) {
                        restaurantDisplayName = rt.get().getName() != null ? rt.get().getName() : "";
                    } else {
                        restaurantDisplayName = restaurantTranslations.get(0).getName() != null
                                ? restaurantTranslations.get(0).getName()
                                : "";
                    }
                }

                OffsetDateTime validFrom = rpm.getValidFrom();
                OffsetDateTime validTo = rpm.getValidTo();

                details.add(PromotionDetail.builder()
                        .promotionName(promotionName)
                        .description(description)
                        .status(status)
                        .validFrom(validFrom)
                        .validTo(validTo)
                        .menuName(restaurantDisplayName)
                        .build());
            } catch (Exception e) {
                logger.warn("Error building details for RestaurantPromotionMapping restaurant={} promotion={}, skipping. Error: {}",
                        rpm.getRestaurant() != null ? rpm.getRestaurant().getId() : null,
                        rpm.getPromotion() != null ? rpm.getPromotion().getId() : null,
                        e.getMessage());
            }
        }

        return details;
    }

    private List<PromotionDetail> buildPromotionDetailsFromMenuMappings(
            List<MenuPromotionMapping> mappings,
            String locale,
            String status) {
        List<PromotionDetail> details = new ArrayList<>();

        for (MenuPromotionMapping mpm : mappings) {
            try {
                Promotion promotion = mpm.getPromotion();

                List<PromotionTranslation> translations = promotionTranslationRepository.findAllByPromotionId(promotion.getId());
                String promotionName = "";
                String description = "";

                if (!translations.isEmpty()) {
                    Optional<PromotionTranslation> translation = TranslationUtils.pickPreferredOrFromListNonBlank(
                            translations,
                            locale,
                            localizationProperties.getLanguages(),
                            PromotionTranslation::getLanguageCode,
                            PromotionTranslation::getName
                    );
                    if (translation.isPresent()) {
                        PromotionTranslation selected = translation.get();
                        promotionName = selected.getName() != null ? selected.getName() : "";
                        description = selected.getDescription() != null ? selected.getDescription() : "";
                    }
                }

                String menuDisplayName = "";
                if (mpm.getMenu() != null && mpm.getMenu().getId() != null) {
                    List<com.gulfnet.shared_library.entity.MenuTranslation> menuTranslations =
                            menuTranslationRepository.findByMenuId(mpm.getMenu().getId());
                    if (!menuTranslations.isEmpty()) {
                        Optional<com.gulfnet.shared_library.entity.MenuTranslation> selectedMenuTranslation =
                                TranslationUtils.pickPreferredOrFromList(
                                        menuTranslations,
                                        locale,
                                        localizationProperties.getLanguages(),
                                        com.gulfnet.shared_library.entity.MenuTranslation::getLanguageCode
                                );
                        if (selectedMenuTranslation.isPresent()) {
                            menuDisplayName = selectedMenuTranslation.get().getName() != null
                                    ? selectedMenuTranslation.get().getName()
                                    : "";
                        } else {
                            menuDisplayName = menuTranslations.get(0).getName() != null ? menuTranslations.get(0).getName() : "";
                        }
                    }
                }

                details.add(PromotionDetail.builder()
                        .promotionName(promotionName)
                        .description(description)
                        .status(status)
                        .validFrom(mpm.getValidFrom())
                        .validTo(mpm.getValidTo())
                        .menuName(menuDisplayName)
                        .build());
            } catch (Exception e) {
                logger.warn("Error building details for MenuPromotionMapping menu={} promotion={}, skipping. Error: {}",
                        mpm.getMenu() != null ? mpm.getMenu().getId() : null,
                        mpm.getPromotion() != null ? mpm.getPromotion().getId() : null,
                        e.getMessage());
            }
        }

        return details;
    }

    /**
     * Calculate discount statistics (active counts, usage, and revenue impact by type)
     */
    private DiscountStats calculateDiscountStats(UUID restaurantGroupId, UUID restaurantId, String period, LocalDateTime startDate, LocalDateTime endDate) {
        try {
            EntityStatus activeStatus = EntityStatus.ACTIVE;
            boolean hasDateFilter = (period != null && !period.isEmpty()) || (startDate != null && endDate != null);
            
            // Determine date range if filter is applied
            LocalDateTime filterStartDate = null;
            LocalDateTime filterEndDate = null;
            if (hasDateFilter) {
                if (period != null && !period.isEmpty()) {
                    LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
                    switch (period.toUpperCase()) {
                        case "30DAYS":
                            filterStartDate = now.minusDays(30);
                            filterEndDate = now;
                            break;
                        case "3MONTHS":
                            filterStartDate = now.minusMonths(3);
                            filterEndDate = now;
                            break;
                        case "6MONTHS":
                            filterStartDate = now.minusMonths(6);
                            filterEndDate = now;
                            break;
                        default:
                            logger.warn("Unrecognized discount period: {}", period);
                            break;
                    }
                } else if (startDate != null && endDate != null) {
                    filterStartDate = startDate;
                    filterEndDate = endDate;
                }
            }

            // Calculate ORDER discount statistics
            long orderDiscountActiveCount;
            long orderDiscountUsage;
            BigDecimal orderDiscountRevenueImpact;
            
            if (restaurantId != null) {
                // Count active ORDER discounts from RestaurantDiscountMapping with validity date checks
                Long count = restaurantDiscountMappingRepository.countActiveOrderDiscountsByRestaurantId(restaurantId);
                orderDiscountActiveCount = count != null ? count : 0L;
                if (hasDateFilter && filterStartDate != null && filterEndDate != null) {
                    orderDiscountUsage = orderRepository.countOrderDiscountUsageByRestaurantIdAndDateRange(restaurantId, activeStatus, filterStartDate, filterEndDate);
                    orderDiscountRevenueImpact = orderRepository.sumOrderDiscountRevenueImpactByRestaurantIdAndDateRange(restaurantId, activeStatus, filterStartDate, filterEndDate);
                } else {
                    orderDiscountUsage = orderRepository.countOrderDiscountUsageByRestaurantId(restaurantId, activeStatus);
                    orderDiscountRevenueImpact = orderRepository.sumOrderDiscountRevenueImpactByRestaurantId(restaurantId, activeStatus);
                }
            } else if (restaurantGroupId != null) {
                // Count active ORDER discounts from RestaurantDiscountMapping with validity date checks
                Long count = restaurantDiscountMappingRepository.countActiveOrderDiscountsByRestaurantGroupId(restaurantGroupId);
                orderDiscountActiveCount = count != null ? count : 0L;
                if (hasDateFilter && filterStartDate != null && filterEndDate != null) {
                    orderDiscountUsage = orderRepository.countOrderDiscountUsageByRestaurantGroupIdAndDateRange(restaurantGroupId, activeStatus, filterStartDate, filterEndDate);
                    orderDiscountRevenueImpact = orderRepository.sumOrderDiscountRevenueImpactByRestaurantGroupIdAndDateRange(restaurantGroupId, activeStatus, filterStartDate, filterEndDate);
                } else {
                    orderDiscountUsage = orderRepository.countOrderDiscountUsageByRestaurantGroupId(restaurantGroupId, activeStatus);
                    orderDiscountRevenueImpact = orderRepository.sumOrderDiscountRevenueImpactByRestaurantGroupId(restaurantGroupId, activeStatus);
                }
            } else {
                orderDiscountActiveCount = discountRepository.countByStatusAndAppliedToAndIsDeletedFalse(activeStatus, AppliedTo.ORDER);
                if (hasDateFilter && filterStartDate != null && filterEndDate != null) {
                    orderDiscountUsage = orderRepository.countOrderDiscountUsageByDateRange(activeStatus, filterStartDate, filterEndDate);
                    orderDiscountRevenueImpact = orderRepository.sumOrderDiscountRevenueImpactByDateRange(activeStatus, filterStartDate, filterEndDate);
                } else {
                    orderDiscountUsage = orderRepository.countOrderDiscountUsage(activeStatus);
                    orderDiscountRevenueImpact = orderRepository.sumOrderDiscountRevenueImpact(activeStatus);
                }
            }

            DiscountTypeStats orderDiscountStats = DiscountTypeStats.builder()
                    .activeCount(orderDiscountActiveCount)
                    .usageCount(orderDiscountUsage)
                    .revenueImpact(orderDiscountRevenueImpact != null ? orderDiscountRevenueImpact : BigDecimal.ZERO)
                    .build();

            // Calculate ITEM discount statistics
            long itemDiscountActiveCount;
            long itemDiscountUsage;
            BigDecimal itemDiscountRevenueImpact;
            
            if (restaurantId != null) {
                // Count active ITEM discounts from MenuDiscountMapping with validity date checks
                Long count = menuDiscountMappingRepository.countActiveItemDiscountsByRestaurantId(restaurantId);
                itemDiscountActiveCount = count != null ? count : 0L;
                if (hasDateFilter && filterStartDate != null && filterEndDate != null) {
                    itemDiscountUsage = orderedItemRepository.countItemDiscountUsageByRestaurantIdAndDateRange(restaurantId, activeStatus, filterStartDate, filterEndDate);
                    itemDiscountRevenueImpact = orderDiscountUsageRepository.sumItemDiscountRevenueImpactByRestaurantIdAndDateRange(restaurantId, filterStartDate, filterEndDate);
                } else {
                    itemDiscountUsage = orderedItemRepository.countItemDiscountUsageByRestaurantId(restaurantId, activeStatus);
                    itemDiscountRevenueImpact = orderDiscountUsageRepository.sumItemDiscountRevenueImpactByRestaurantId(restaurantId);
                }
            } else if (restaurantGroupId != null) {
                // Count active ITEM discounts from MenuDiscountMapping with validity date checks
                Long count = menuDiscountMappingRepository.countActiveItemDiscountsByRestaurantGroupId(restaurantGroupId);
                itemDiscountActiveCount = count != null ? count : 0L;
                if (hasDateFilter && filterStartDate != null && filterEndDate != null) {
                    itemDiscountUsage = orderedItemRepository.countItemDiscountUsageByRestaurantGroupIdAndDateRange(restaurantGroupId, activeStatus, filterStartDate, filterEndDate);
                    itemDiscountRevenueImpact = orderDiscountUsageRepository.sumItemDiscountRevenueImpactByRestaurantGroupIdAndDateRange(restaurantGroupId, filterStartDate, filterEndDate);
                } else {
                    itemDiscountUsage = orderedItemRepository.countItemDiscountUsageByRestaurantGroupId(restaurantGroupId, activeStatus);
                    itemDiscountRevenueImpact = orderDiscountUsageRepository.sumItemDiscountRevenueImpactByRestaurantGroupId(restaurantGroupId);
                }
            } else {
                itemDiscountActiveCount = discountRepository.countByStatusAndAppliedToAndIsDeletedFalse(activeStatus, AppliedTo.ITEM);
                if (hasDateFilter && filterStartDate != null && filterEndDate != null) {
                    itemDiscountUsage = orderedItemRepository.countItemDiscountUsageByDateRange(activeStatus, filterStartDate, filterEndDate);
                    itemDiscountRevenueImpact = orderDiscountUsageRepository.sumItemDiscountRevenueImpactByDateRange(filterStartDate, filterEndDate);
                } else {
                    itemDiscountUsage = orderedItemRepository.countItemDiscountUsage(activeStatus);
                    itemDiscountRevenueImpact = orderDiscountUsageRepository.sumItemDiscountRevenueImpact();
                }
            }

            DiscountTypeStats itemDiscountStats = DiscountTypeStats.builder()
                    .activeCount(itemDiscountActiveCount)
                    .usageCount(itemDiscountUsage)
                    .revenueImpact(itemDiscountRevenueImpact != null ? itemDiscountRevenueImpact : BigDecimal.ZERO)
                    .build();

            // Calculate CATEGORY discount statistics
            long categoryDiscountActiveCount;
            long categoryDiscountUsage;
            BigDecimal categoryDiscountRevenueImpact;
            
            if (restaurantId != null) {
                // Count active CATEGORY discounts from MenuDiscountMapping with validity date checks
                Long count = menuDiscountMappingRepository.countActiveCategoryDiscountsByRestaurantId(restaurantId);
                categoryDiscountActiveCount = count != null ? count : 0L;
                if (hasDateFilter && filterStartDate != null && filterEndDate != null) {
                    categoryDiscountUsage = orderedItemRepository.countCategoryDiscountUsageByRestaurantIdAndDateRange(restaurantId, activeStatus, filterStartDate, filterEndDate);
                    categoryDiscountRevenueImpact = orderDiscountUsageRepository.sumCategoryDiscountRevenueImpactByRestaurantIdAndDateRange(restaurantId, filterStartDate, filterEndDate);
                } else {
                    categoryDiscountUsage = orderedItemRepository.countCategoryDiscountUsageByRestaurantId(restaurantId, activeStatus);
                    categoryDiscountRevenueImpact = orderDiscountUsageRepository.sumCategoryDiscountRevenueImpactByRestaurantId(restaurantId);
                }
            } else if (restaurantGroupId != null) {
                // Count active CATEGORY discounts from MenuDiscountMapping with validity date checks
                Long count = menuDiscountMappingRepository.countActiveCategoryDiscountsByRestaurantGroupId(restaurantGroupId);
                categoryDiscountActiveCount = count != null ? count : 0L;
                if (hasDateFilter && filterStartDate != null && filterEndDate != null) {
                    categoryDiscountUsage = orderedItemRepository.countCategoryDiscountUsageByRestaurantGroupIdAndDateRange(restaurantGroupId, activeStatus, filterStartDate, filterEndDate);
                    categoryDiscountRevenueImpact = orderDiscountUsageRepository.sumCategoryDiscountRevenueImpactByRestaurantGroupIdAndDateRange(restaurantGroupId, filterStartDate, filterEndDate);
                } else {
                    categoryDiscountUsage = orderedItemRepository.countCategoryDiscountUsageByRestaurantGroupId(restaurantGroupId, activeStatus);
                    categoryDiscountRevenueImpact = orderDiscountUsageRepository.sumCategoryDiscountRevenueImpactByRestaurantGroupId(restaurantGroupId);
                }
            } else {
                categoryDiscountActiveCount = discountRepository.countByStatusAndAppliedToAndIsDeletedFalse(activeStatus, AppliedTo.CATEGORY);
                if (hasDateFilter && filterStartDate != null && filterEndDate != null) {
                    categoryDiscountUsage = orderedItemRepository.countCategoryDiscountUsageByDateRange(activeStatus, filterStartDate, filterEndDate);
                    categoryDiscountRevenueImpact = orderDiscountUsageRepository.sumCategoryDiscountRevenueImpactByDateRange(filterStartDate, filterEndDate);
                } else {
                    categoryDiscountUsage = orderedItemRepository.countCategoryDiscountUsage(activeStatus);
                    categoryDiscountRevenueImpact = orderDiscountUsageRepository.sumCategoryDiscountRevenueImpact();
                }
            }

            DiscountTypeStats categoryDiscountStats = DiscountTypeStats.builder()
                    .activeCount(categoryDiscountActiveCount)
                    .usageCount(categoryDiscountUsage)
                    .revenueImpact(categoryDiscountRevenueImpact != null ? categoryDiscountRevenueImpact : BigDecimal.ZERO)
                    .build();

            // Calculate totals
            long totalActiveDiscounts = orderDiscountActiveCount + itemDiscountActiveCount + categoryDiscountActiveCount;
            long totalUsage = orderDiscountUsage + itemDiscountUsage + categoryDiscountUsage;
            BigDecimal totalRevenueImpact = (orderDiscountRevenueImpact != null ? orderDiscountRevenueImpact : BigDecimal.ZERO)
                    .add(itemDiscountRevenueImpact != null ? itemDiscountRevenueImpact : BigDecimal.ZERO)
                    .add(categoryDiscountRevenueImpact != null ? categoryDiscountRevenueImpact : BigDecimal.ZERO);

            return DiscountStats.builder()
                    .orderDiscounts(orderDiscountStats)
                    .itemDiscounts(itemDiscountStats)
                    .categoryDiscounts(categoryDiscountStats)
                    .totalActiveDiscounts(totalActiveDiscounts)
                    .totalUsage(totalUsage)
                    .totalRevenueImpact(totalRevenueImpact)
                    .build();

        } catch (Exception e) {
            logger.warn("Error calculating discount statistics, returning empty result. Error: {}", e.getMessage());
            return DiscountStats.builder()
                    .orderDiscounts(DiscountTypeStats.builder().activeCount(0L).usageCount(0L).revenueImpact(BigDecimal.ZERO).build())
                    .itemDiscounts(DiscountTypeStats.builder().activeCount(0L).usageCount(0L).revenueImpact(BigDecimal.ZERO).build())
                    .categoryDiscounts(DiscountTypeStats.builder().activeCount(0L).usageCount(0L).revenueImpact(BigDecimal.ZERO).build())
                    .totalActiveDiscounts(0L)
                    .totalUsage(0L)
                    .totalRevenueImpact(BigDecimal.ZERO)
                    .build();
        }
    }

    /**
     * Calculate menu performance (top 5 items by order count with revenue)
     * Supports date filtering via period or explicit startDate/endDate
     */
    private MenuPerformance calculateMenuPerformance(UUID restaurantGroupId, UUID restaurantId, String locale, String period, LocalDateTime startDate, LocalDateTime endDate) {
        try {
            // Use sentinel UUID for null values (JPQL handles nulls, but using sentinel for consistency)
            UUID sentinelUuid = SENTINEL_UUID;
            UUID restaurantIdParam = restaurantId != null ? restaurantId : sentinelUuid;
            UUID restaurantGroupIdParam = restaurantGroupId != null ? restaurantGroupId : sentinelUuid;
            
            // Check if date filter is provided
            boolean hasDateFilter = (period != null) || (startDate != null && endDate != null);
            
            // Calculate date range if filter is provided
            LocalDateTime periodStartDate = null;
            LocalDateTime periodEndDate = null;
            
            if (hasDateFilter) {
                if (startDate != null && endDate != null) {
                    periodStartDate = startDate.withHour(0).withMinute(0).withSecond(0).withNano(0);
                    periodEndDate = endDate.withHour(23).withMinute(59).withSecond(59).withNano(999999999);
                } else if (period != null) {
                    periodEndDate = LocalDateTime.now(ZoneOffset.UTC).withHour(23).withMinute(59).withSecond(59).withNano(999999999);
                    switch (period.toUpperCase()) {
                        case PERIOD_30_DAYS:
                            periodStartDate = LocalDateTime.now(ZoneOffset.UTC).minusDays(30).withHour(0).withMinute(0).withSecond(0).withNano(0);
                            break;
                        case PERIOD_3_MONTHS:
                            periodStartDate = LocalDateTime.now(ZoneOffset.UTC).minusMonths(3).withHour(0).withMinute(0).withSecond(0).withNano(0);
                            break;
                        case PERIOD_6_MONTHS:
                            periodStartDate = LocalDateTime.now(ZoneOffset.UTC).minusMonths(6).withHour(0).withMinute(0).withSecond(0).withNano(0);
                            break;
                        default:
                            periodStartDate = null;
                            periodEndDate = null;
                    }
                }
            }
            
            // Use sentinel date for null values in date-filtered query
            LocalDateTime sentinelDate = LocalDateTime.of(1970, 1, 1, 0, 0, 0);
            LocalDateTime startDateParam = (periodStartDate != null) ? periodStartDate : sentinelDate;
            LocalDateTime endDateParam = (periodEndDate != null) ? periodEndDate : sentinelDate;
            
            // Get top 5 items by order count (quantity) with revenue
            // Use date-filtered query if date filter is provided, otherwise use all-time query
            List<Object[]> topItemsData;
            if (hasDateFilter && periodStartDate != null && periodEndDate != null) {
                topItemsData = orderedItemRepository.findTop5ItemsByOrderCountWithDateRange(
                        restaurantIdParam, restaurantGroupIdParam, startDateParam, endDateParam);
            } else {
                topItemsData = orderedItemRepository.findTop5ItemsByOrderCount(restaurantIdParam, restaurantGroupIdParam);
            }

            // Resolve "main category" (parent category if subcategory) for each top item based on the restaurant's LIVE menu.
            // This avoids returning an arbitrary subcategory / wrong menu category mapping for items like "Bento Chicken".
            Map<UUID, UUID> itemToMainCategoryId = new java.util.HashMap<>();
            resolveMenuPerformanceMainCategoriesBestEffort(restaurantId, topItemsData, itemToMainCategoryId);

            List<UUID> menuPerfItemIds = new ArrayList<>();
            if (topItemsData != null) {
                for (Object[] row : topItemsData) {
                    if (row != null && row.length > 0 && row[0] instanceof UUID) {
                        menuPerfItemIds.add((UUID) row[0]);
                    }
                }
            }
            Map<UUID, Item> menuPerfItemMap = menuPerfItemIds.isEmpty()
                    ? Collections.emptyMap()
                    : itemRepository.findAllById(menuPerfItemIds).stream()
                            .collect(Collectors.toMap(Item::getId, i -> i));
            
            List<TopItemPerformance> topItems = new ArrayList<>();
            String currency = restaurantChainConfigProperties.getChain() != null ? restaurantChainConfigProperties.getChain().getCurrency() : null;
            
            for (Object[] row : topItemsData) {
                try {
                    UUID itemId = (UUID) row[0];
                    UUID categoryId = (UUID) row[1];
                    Long totalQuantity = ((Number) row[2]).longValue();
                    BigDecimal totalRevenue = (BigDecimal) row[3];

                    // Prefer main category id resolved from restaurant's LIVE menu mapping (if available)
                    if (itemId != null && itemToMainCategoryId.containsKey(itemId)) {
                        categoryId = itemToMainCategoryId.get(itemId);
                    }
                    
                    // Get item name from translations
                    String itemName = "";
                    List<ItemTranslation> itemTranslations = itemTranslationRepository.findAllByItemId(itemId);
                    if (!itemTranslations.isEmpty()) {
                        // Try exact match first - use locale string directly
                        ItemTranslation exactMatch = itemTranslations.stream()
                                .filter(t -> t.getLanguageCode() != null && t.getLanguageCode().equalsIgnoreCase(locale))
                                .findFirst()
                                .orElse(null);
                        
                        if (exactMatch != null) {
                            itemName = exactMatch.getName() != null ? exactMatch.getName() : "";
                        } else {
                            // Fallback using TranslationUtils
                            Optional<ItemTranslation> itemTranslation = TranslationUtils.pickPreferredOrFromList(
                                    itemTranslations,
                                    locale,
                                    localizationProperties.getLanguages(),
                                    ItemTranslation::getLanguageCode
                            );
                            
                            if (itemTranslation.isPresent()) {
                                itemName = itemTranslation.get().getName() != null ? itemTranslation.get().getName() : "";
                            } else if (!itemTranslations.isEmpty()) {
                                // Last resort: first available translation
                                itemName = itemTranslations.get(0).getName() != null ? itemTranslations.get(0).getName() : "";
                            }
                        }
                    }
                    
                    // Get category name from translations
                    String categoryName = "";
                    if (categoryId != null) {
                        List<CategoryTranslation> categoryTranslations = categoryTranslationRepository.findByCategoryId(categoryId);
                        if (!categoryTranslations.isEmpty()) {
                            // Try exact match first - use locale string directly
                            CategoryTranslation exactMatch = categoryTranslations.stream()
                                    .filter(t -> t.getLanguageCode() != null && t.getLanguageCode().equalsIgnoreCase(locale))
                                    .findFirst()
                                    .orElse(null);
                            
                            if (exactMatch != null) {
                                categoryName = exactMatch.getName() != null ? exactMatch.getName() : "";
                            } else {
                                // Fallback using TranslationUtils
                                Optional<CategoryTranslation> categoryTranslation = TranslationUtils.pickPreferredOrFromList(
                                        categoryTranslations,
                                        locale,
                                        localizationProperties.getLanguages(),
                                        CategoryTranslation::getLanguageCode
                                );
                                
                                if (categoryTranslation.isPresent()) {
                                    categoryName = categoryTranslation.get().getName() != null ? categoryTranslation.get().getName() : "";
                                } else if (!categoryTranslations.isEmpty()) {
                                    // Last resort: first available translation
                                    categoryName = categoryTranslations.get(0).getName() != null ? categoryTranslations.get(0).getName() : "";
                                }
                            }
                        }
                    }

                    Item menuPerfItem = menuPerfItemMap.get(itemId);
                    String menuPerfItemCode = menuPerfItem != null ? menuPerfItem.getItemCode() : null;
                    
                    topItems.add(TopItemPerformance.builder()
                            .itemCode(menuPerfItemCode)
                            .itemName(itemName)
                            .categoryName(categoryName)
                            .orderCount(totalQuantity)
                            .revenue(totalRevenue != null ? CurrencyFormatter.formatAmount(totalRevenue, currency) : CurrencyFormatter.formatAmount(BigDecimal.ZERO, currency))
                            .build());
                            
                } catch (Exception e) {
                    logger.warn("Error building top item performance for item {}, skipping. Error: {}", row[0], e.getMessage());
                    // Continue with next item
                }
            }
            
            return MenuPerformance.builder()
                    .topItems(topItems)
                    .build();

        } catch (Exception e) {
            logger.warn("Error calculating menu performance, returning empty result. Error: {}", e.getMessage());
            return MenuPerformance.builder()
                    .topItems(new ArrayList<>())
                    .build();
        }
    }

    private void resolveMenuPerformanceMainCategoriesBestEffort(UUID restaurantId,
                                                                List<Object[]> topItemsData,
                                                                Map<UUID, UUID> itemToMainCategoryId) {
        if (restaurantId == null || topItemsData == null || topItemsData.isEmpty() || itemToMainCategoryId == null) {
            return;
        }
        try {
            List<UUID> topItemIds = new ArrayList<>();
            for (Object[] row : topItemsData) {
                if (row != null && row.length > 0 && row[0] instanceof UUID) {
                    topItemIds.add((UUID) row[0]);
                }
            }
            if (!topItemIds.isEmpty()) {
                resolveMainCategoriesViaLiveMenu(restaurantId, topItemIds, itemToMainCategoryId);
            }
        } catch (Exception e) {
            logger.warn("Error resolving main category for menu performance, falling back to query category. Error: {}", e.getMessage());
        }
    }

    /**
     * Calculate sales statistics (daily, weekly, or monthly)
     */
    private SalesStats calculateSalesStats(String salesStatsPeriod, UUID restaurantGroupId, UUID restaurantId) {
        if (salesStatsPeriod == null || salesStatsPeriod.trim().isEmpty()) {
            return null; // No sales stats requested
        }

        try {
            // Use sentinel UUID for null values
            UUID sentinelUuid = SENTINEL_UUID;
            String periodUpper = salesStatsPeriod.toUpperCase();
            List<Object[]> salesData;
            String period;

            UUID restaurantIdParam = restaurantId != null ? restaurantId : sentinelUuid;
            UUID restaurantGroupIdParam = restaurantGroupId != null ? restaurantGroupId : sentinelUuid;

            switch (periodUpper) {
                case "DAILY":
                    salesData = transactionRepository.getDailySalesStats(restaurantIdParam, restaurantGroupIdParam);
                    period = "DAILY";
                    break;
                case PERIOD_WEEKLY:
                    salesData = transactionRepository.getWeeklySalesStats(restaurantIdParam, restaurantGroupIdParam);
                    period = PERIOD_WEEKLY;
                    break;
                case PERIOD_MONTHLY:
                    salesData = transactionRepository.getMonthlySalesStats(restaurantIdParam, restaurantGroupIdParam);
                    period = PERIOD_MONTHLY;
                    break;
                default:
                    logger.warn("Invalid salesStatsPeriod: {}. Valid values are: DAILY, WEEKLY, MONTHLY", salesStatsPeriod);
                    return null;
            }

            List<SalesDataPoint> dataPoints = new ArrayList<>();
            
            for (Object[] row : salesData) {
                try {
                    // row[0] is the date (java.sql.Date or LocalDate)
                    // row[1] is the order count (Long or BigInteger)
                    // row[2] is the total sales (BigDecimal)
                    
                    java.time.LocalDate date;
                    if (row[0] instanceof java.sql.Date) {
                        date = ((java.sql.Date) row[0]).toLocalDate();
                    } else if (row[0] instanceof java.time.LocalDate) {
                        date = (java.time.LocalDate) row[0];
                    } else {
                        logger.warn("Unexpected date type: {}, skipping row", row[0].getClass().getName());
                        continue;
                    }
                    
                    Long orderCount = ((Number) row[1]).longValue();
                    BigDecimal totalSales = (BigDecimal) row[2];
                    
                    String currency = restaurantChainConfigProperties.getChain() != null ? restaurantChainConfigProperties.getChain().getCurrency() : null;
                    dataPoints.add(SalesDataPoint.builder()
                            .date(date)
                            .orderCount(orderCount)
                            .totalSales(totalSales != null ? CurrencyFormatter.formatAmount(totalSales, currency) : CurrencyFormatter.formatAmount(BigDecimal.ZERO, currency))
                            .build());
                            
                } catch (Exception e) {
                    logger.warn("Error building sales data point, skipping. Error: {}", e.getMessage());
                    // Continue with next row
                }
            }
            
            // Reverse to show oldest first (since query orders DESC)
            java.util.Collections.reverse(dataPoints);
            
            return SalesStats.builder()
                    .period(period)
                    .dataPoints(dataPoints)
                    .build();
                    
        } catch (Exception e) {
            logger.warn("Error calculating sales statistics, returning null. Error: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Calculate total refund amount based on period or date range
     */
    private BigDecimal calculateTotalRefund(String period, LocalDateTime startDate, LocalDateTime endDate, UUID restaurantGroupId, UUID restaurantId) {
        try {
            LocalDateTime periodStartDate;
            LocalDateTime periodEndDate = LocalDateTime.now(ZoneOffset.UTC).withHour(23).withMinute(59).withSecond(59).withNano(999999999);
            
            // Determine date range
            if (startDate != null && endDate != null) {
                periodStartDate = startDate.withHour(0).withMinute(0).withSecond(0).withNano(0);
                periodEndDate = endDate.withHour(23).withMinute(59).withSecond(59).withNano(999999999);
            } else if (period != null) {
                switch (period.toUpperCase()) {
                    case PERIOD_30_DAYS:
                        periodStartDate = LocalDateTime.now(ZoneOffset.UTC).minusDays(30).withHour(0).withMinute(0).withSecond(0).withNano(0);
                        break;
                    case PERIOD_3_MONTHS:
                        periodStartDate = LocalDateTime.now(ZoneOffset.UTC).minusMonths(3).withHour(0).withMinute(0).withSecond(0).withNano(0);
                        break;
                    case PERIOD_6_MONTHS:
                        periodStartDate = LocalDateTime.now(ZoneOffset.UTC).minusMonths(6).withHour(0).withMinute(0).withSecond(0).withNano(0);
                        break;
                    default:
                        periodStartDate = LocalDateTime.of(1970, 1, 1, 0, 0, 0);
                        periodEndDate = LocalDateTime.of(1970, 1, 1, 0, 0, 0);
                }
            } else {
                // All-time
                periodStartDate = LocalDateTime.of(1970, 1, 1, 0, 0, 0);
                periodEndDate = LocalDateTime.of(1970, 1, 1, 0, 0, 0);
            }
            
            BigDecimal totalRefund;
            if (restaurantId != null) {
                totalRefund = refundRepository.sumTotalRefundAmountByRestaurantId(restaurantId, periodStartDate, periodEndDate);
            } else if (restaurantGroupId != null) {
                totalRefund = refundRepository.sumTotalRefundAmountByRestaurantGroupId(restaurantGroupId, periodStartDate, periodEndDate);
            } else {
                totalRefund = refundRepository.sumTotalRefundAmount(periodStartDate, periodEndDate);
            }
            
            logger.debug("Total refund calculation - period: {}, startDate: {}, endDate: {}, totalRefund: {}", 
                    period, periodStartDate, periodEndDate, totalRefund);
            
            return totalRefund != null ? totalRefund : BigDecimal.ZERO;
            
        } catch (Exception e) {
            logger.warn("Error calculating total refund, returning zero. Error: {}", e.getMessage());
            return BigDecimal.ZERO;
        }
    }

    /**
     * Calculate void management (wastage statistics) based on period or date range
     */
    private VoidManagement calculateVoidManagement(String period, LocalDateTime startDate, LocalDateTime endDate, UUID restaurantGroupId, UUID restaurantId) {
        try {
            LocalDateTime periodStartDate;
            LocalDateTime periodEndDate = LocalDateTime.now(ZoneOffset.UTC).withHour(23).withMinute(59).withSecond(59).withNano(999999999);
            
            // Determine date range
            if (startDate != null && endDate != null) {
                periodStartDate = startDate.withHour(0).withMinute(0).withSecond(0).withNano(0);
                periodEndDate = endDate.withHour(23).withMinute(59).withSecond(59).withNano(999999999);
            } else if (period != null) {
                switch (period.toUpperCase()) {
                    case PERIOD_30_DAYS:
                        periodStartDate = LocalDateTime.now(ZoneOffset.UTC).minusDays(30).withHour(0).withMinute(0).withSecond(0).withNano(0);
                        break;
                    case PERIOD_3_MONTHS:
                        periodStartDate = LocalDateTime.now(ZoneOffset.UTC).minusMonths(3).withHour(0).withMinute(0).withSecond(0).withNano(0);
                        break;
                    case PERIOD_6_MONTHS:
                        periodStartDate = LocalDateTime.now(ZoneOffset.UTC).minusMonths(6).withHour(0).withMinute(0).withSecond(0).withNano(0);
                        break;
                    default:
                        periodStartDate = LocalDateTime.of(1970, 1, 1, 0, 0, 0);
                        periodEndDate = LocalDateTime.of(1970, 1, 1, 0, 0, 0);
                }
            } else {
                // All-time
                periodStartDate = LocalDateTime.of(1970, 1, 1, 0, 0, 0);
                periodEndDate = LocalDateTime.of(1970, 1, 1, 0, 0, 0);
            }
            
            // Get wastage summaries
            List<Object[]> itemWastageSummaryList = fetchItemWastageSummary(restaurantId, restaurantGroupId, periodStartDate, periodEndDate);
            List<Object[]> comboWastageSummaryList = fetchComboWastageSummary(restaurantId, restaurantGroupId, periodStartDate, periodEndDate);
            
            // Extract values from wastage summaries
            Object[] itemWastageValues = extractWastageValues(itemWastageSummaryList, "item");
            Long itemQuantity = (Long) itemWastageValues[0];
            BigDecimal itemWastageCost = (BigDecimal) itemWastageValues[1];
            
            Object[] comboWastageValues = extractWastageValues(comboWastageSummaryList, "combo");
            Long comboQuantity = (Long) comboWastageValues[0];
            BigDecimal comboWastageCost = (BigDecimal) comboWastageValues[1];
            
            logger.debug("Wastage calculation - Items: quantity={}, cost={}, Combos: quantity={}, cost={}", 
                    itemQuantity, itemWastageCost, comboQuantity, comboWastageCost);
            
            // Combine items and combos
            Long totalWastageItems = itemQuantity + comboQuantity;
            BigDecimal totalWastageAmount = itemWastageCost.add(comboWastageCost);
            
            return VoidManagement.builder()
                    .totalWastageAmount(totalWastageAmount)
                    .totalWastageItems(totalWastageItems)
                    .build();
                    
        } catch (Exception e) {
            logger.warn("Error calculating void management, returning empty result. Error: {}", e.getMessage());
            return VoidManagement.builder()
                    .totalWastageAmount(BigDecimal.ZERO)
                    .totalWastageItems(0L)
                    .build();
        }
    }

    /**
     * Builds the restaurant dashboard view for a given date range and scope.
     * <p>
     * Validates restaurant/group filters and date parameters, computes the effective date window (today/weekly/monthly/custom),
     * then executes multiple independent queries in parallel (revenue, active orders, managers, on-shift staff, etc.).
     * </p>
     *
     * @param dateRange          date-range selector (WEEKLY/MONTHLY/CUSTOM or empty for today)
     * @param startDate          custom range start (required when dateRange=CUSTOM)
     * @param endDate            custom range end (required when dateRange=CUSTOM)
     * @param restaurantGroupId  optional group scope
     * @param restaurantId       optional restaurant scope (overrides group scope)
     * @param onShiftStaffPage   paging for on-shift staff list (1-based)
     * @param onShiftStaffSize   page size for on-shift staff list
     * @param managersPage       paging for active managers list (1-based)
     * @param managersSize       page size for active managers list
     * @param locale             locale tag used for message localization and translation selection
     * @return response wrapper containing {@link RestaurantDashboardResponse}
     * @throws ResponseStatusException when validation fails or an unexpected error occurs
     */
    @Override
    public ResponseDto<RestaurantDashboardResponse> getRestaurantDashboard(
            String dateRange,
            LocalDateTime startDate,
            LocalDateTime endDate,
            UUID restaurantGroupId,
            UUID restaurantId,
            Integer onShiftStaffPage,
            Integer onShiftStaffSize,
            Integer managersPage,
            Integer managersSize,
            String locale) {
        // Set locale context for message localization
        Locale localeObj = Locale.forLanguageTag(locale != null ? locale : "en");
        LocaleContextHolder.setLocale(localeObj);
        
        try {
            logger.info("Fetching restaurant dashboard with dateRange: {}, startDate: {}, endDate: {}, restaurantGroupId: {}, restaurantId: {}, onShiftStaffPage: {}, onShiftStaffSize: {}, managersPage: {}, managersSize: {}, locale: {}", 
                    dateRange, startDate, endDate, restaurantGroupId, restaurantId,
                    onShiftStaffPage, onShiftStaffSize, managersPage, managersSize, locale);

            // Validate restaurant ID and restaurant group ID
            validateRestaurantFilters(restaurantId, restaurantGroupId, localeObj);

            // Validate date parameters
            validateDateParameters(dateRange, startDate, endDate, localeObj);

            // Calculate date range (default to today)
            LocalDateTime rangeStartDate;
            LocalDateTime rangeEndDate;
            LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
            
            if (dateRange != null && !dateRange.isEmpty()) {
                if (PERIOD_WEEKLY.equalsIgnoreCase(dateRange)) {
                    // Start of week (Monday) to end of week (Sunday)
                    java.time.DayOfWeek dayOfWeek = now.getDayOfWeek();
                    int daysToSubtract = dayOfWeek.getValue() - 1; // Monday = 1
                    rangeStartDate = now.toLocalDate().atStartOfDay().minusDays(daysToSubtract);
                    rangeEndDate = rangeStartDate.plusDays(6).withHour(23).withMinute(59).withSecond(59);
                } else if (PERIOD_MONTHLY.equalsIgnoreCase(dateRange)) {
                    // Start of month to end of month
                    rangeStartDate = now.withDayOfMonth(1).withHour(0).withMinute(0).withSecond(0);
                    rangeEndDate = now.withDayOfMonth(now.toLocalDate().lengthOfMonth()).withHour(23).withMinute(59).withSecond(59);
                } else if (PERIOD_CUSTOM.equalsIgnoreCase(dateRange)) {
                    // Use provided startDate and endDate
                    if (startDate == null || endDate == null) {
                        throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                                messageUtil.getMessage(MSG_DASHBOARD_ERROR_STARTDATE_REQUIRED, localeObj));
                    }
                    rangeStartDate = startDate;
                    rangeEndDate = endDate;
                } else {
                    // Default to today
                    rangeStartDate = now.toLocalDate().atStartOfDay();
                    rangeEndDate = now.toLocalDate().atTime(23, 59, 59);
                }
            } else {
                // Default to today
                rangeStartDate = now.toLocalDate().atStartOfDay();
                rangeEndDate = now.toLocalDate().atTime(23, 59, 59);
            }

            // Use sentinel UUID and sentinel date for null values
            UUID sentinelUuid = SENTINEL_UUID;
            LocalDateTime sentinelDate = LocalDateTime.of(1970, 1, 1, 0, 0, 0);
            LocalDateTime startDateParam = rangeStartDate != null ? rangeStartDate : sentinelDate;
            LocalDateTime endDateParam = rangeEndDate != null ? rangeEndDate : sentinelDate;

            // Execute all independent queries in parallel using CompletableFuture
            OffsetDateTime rangeStartUtc = rangeStartDate != null ? rangeStartDate.atOffset(ZoneOffset.UTC) : null;
            OffsetDateTime rangeEndUtc = rangeEndDate != null ? rangeEndDate.atOffset(ZoneOffset.UTC) : null;
            CompletableFuture<BigDecimal> dailyRevenueFuture = CompletableFuture.supplyAsync(() -> {
                BigDecimal revenue = BigDecimal.ZERO;
                if (rangeStartUtc != null && rangeEndUtc != null) {
                    if (restaurantId != null) {
                        revenue = transactionRepository.sumTransactionAmountByRestaurantIdAndStatusAndDateRange(
                                restaurantId, TransactionStatus.COMPLETED, rangeStartUtc, rangeEndUtc);
                    } else if (restaurantGroupId != null) {
                        revenue = transactionRepository.sumTransactionAmountByRestaurantGroupIdAndStatusAndDateRange(
                                restaurantGroupId, TransactionStatus.COMPLETED, rangeStartUtc, rangeEndUtc);
                    } else {
                        revenue = transactionRepository.sumTransactionAmountByStatusAndDateRange(
                                TransactionStatus.COMPLETED, rangeStartUtc, rangeEndUtc);
                    }
                }
                return revenue != null ? revenue : BigDecimal.ZERO;
            }, executorService);

            CompletableFuture<Long> completedTransactionCountFuture = CompletableFuture.supplyAsync(() -> {
                if (rangeStartUtc == null || rangeEndUtc == null) return 0L;
                if (restaurantId != null) {
                    return transactionRepository.countByRestaurantIdAndTransactionStatusAndDateRange(
                            restaurantId, TransactionStatus.COMPLETED, rangeStartUtc, rangeEndUtc);
                } else if (restaurantGroupId != null) {
                    return transactionRepository.countByRestaurantGroupIdAndTransactionStatusAndDateRange(
                            restaurantGroupId, TransactionStatus.COMPLETED, rangeStartUtc, rangeEndUtc);
                } else {
                    return transactionRepository.countByTransactionStatusAndDateRange(
                            TransactionStatus.COMPLETED, rangeStartUtc, rangeEndUtc);
                }
            }, executorService);

            CompletableFuture<Long> activeOrdersFuture = CompletableFuture.supplyAsync(() -> {
                long pushedCount = 0;
                long inProgressCount = 0;
                if (restaurantId != null) {
                    pushedCount = orderRepository.countByOrderStatusAndFilters(
                            OrderStatus.PUSHED.name(), restaurantId, sentinelUuid, startDateParam, endDateParam);
                    inProgressCount = orderRepository.countByOrderStatusAndFilters(
                            OrderStatus.IN_PROGRESS.name(), restaurantId, sentinelUuid, startDateParam, endDateParam);
                } else if (restaurantGroupId != null) {
                    pushedCount = orderRepository.countByOrderStatusAndFilters(
                            OrderStatus.PUSHED.name(), sentinelUuid, restaurantGroupId, startDateParam, endDateParam);
                    inProgressCount = orderRepository.countByOrderStatusAndFilters(
                            OrderStatus.IN_PROGRESS.name(), sentinelUuid, restaurantGroupId, startDateParam, endDateParam);
                } else {
                    pushedCount = orderRepository.countByOrderStatusAndFilters(
                            OrderStatus.PUSHED.name(), sentinelUuid, sentinelUuid, startDateParam, endDateParam);
                    inProgressCount = orderRepository.countByOrderStatusAndFilters(
                            OrderStatus.IN_PROGRESS.name(), sentinelUuid, sentinelUuid, startDateParam, endDateParam);
                }
                return pushedCount + inProgressCount;
            }, executorService);

            CompletableFuture<List<UnavailableItem>> recentlyUnavailableItemsFuture = 
                    CompletableFuture.supplyAsync(() -> getRecentlyUnavailableItems(restaurantId, restaurantGroupId, locale), executorService);

            CompletableFuture<ManagerListResponse> managersFuture = 
                    CompletableFuture.supplyAsync(() -> getActiveManagers(restaurantId, restaurantGroupId, managersPage, managersSize, locale), executorService);

            CompletableFuture<OrderStatusBreakdown> orderStatusBreakdownFuture = 
                    CompletableFuture.supplyAsync(() -> getOrderStatusBreakdown(restaurantId, restaurantGroupId, rangeStartDate, rangeEndDate), executorService);

            CompletableFuture<OnShiftStaffListResponse> onShiftStaffFuture = 
                    CompletableFuture.supplyAsync(() -> getOnShiftStaff(restaurantId, restaurantGroupId, onShiftStaffPage, onShiftStaffSize, locale), executorService);

            // Calculate top and least performing items synchronously (not async)
            List<PerformingItem> topPerformingItems = getTop2PerformingItems(restaurantId, restaurantGroupId, rangeStartDate, rangeEndDate, locale);
            PerformingItem leastPerformingItem = getLeastPerformingItem(restaurantId, restaurantGroupId, rangeStartDate, rangeEndDate, locale);

            // Calculate table occupancy percentage
            CompletableFuture<BigDecimal> tableOccupancyFuture = CompletableFuture.supplyAsync(() -> {
                try {
                    // Get total number of tables
                    Long totalTables = 0L;
                    if (restaurantId != null) {
                        totalTables = restaurantTableRepository.countTotalTablesByRestaurantId(restaurantId);
                    } else if (restaurantGroupId != null) {
                        totalTables = restaurantTableRepository.countTotalTablesByRestaurantGroupId(restaurantGroupId);
                    } else {
                        totalTables = restaurantTableRepository.countTotalTables();
                    }
                    
                    if (totalTables == null || totalTables == 0) {
                        return BigDecimal.ZERO;
                    }
                    
                    // Get number of distinct tables used in the date range
                    long usedTables = orderRepository.countDistinctTablesUsedInDateRange(
                            restaurantId != null ? restaurantId : sentinelUuid,
                            restaurantGroupId != null ? restaurantGroupId : sentinelUuid,
                            startDateParam,
                            endDateParam);
                    
                    // Calculate percentage: (used tables / total tables) * 100
                    return BigDecimal.valueOf(usedTables)
                            .divide(BigDecimal.valueOf(totalTables), 4, java.math.RoundingMode.HALF_UP)
                            .multiply(BigDecimal.valueOf(100))
                            .setScale(2, java.math.RoundingMode.HALF_UP);
                } catch (Exception e) {
                    logger.warn("Error calculating table occupancy: {}", e.getMessage());
                    return BigDecimal.ZERO;
                }
            }, executorService);

            // Calculate peak hour analysis
            CompletableFuture<List<PeakHourAnalysis>> peakHourAnalysisFuture = 
                    CompletableFuture.supplyAsync(() -> {
                try {
                    // Get hourly order counts
                    List<Object[]> hourlyData = orderRepository.getHourlyOrderCounts(
                            restaurantId != null ? restaurantId : sentinelUuid,
                            restaurantGroupId != null ? restaurantGroupId : sentinelUuid,
                            startDateParam,
                            endDateParam);
                    
                    if (hourlyData == null || hourlyData.isEmpty()) {
                        return new ArrayList<>();
                    }
                    
                    // Calculate total orders across all hours
                    long totalOrders = hourlyData.stream()
                            .mapToLong(row -> ((Number) row[1]).longValue())
                            .sum();
                    
                    if (totalOrders == 0) {
                        return new ArrayList<>();
                    }
                    
                    // Build peak hour analysis list
                    List<PeakHourAnalysis> peakHourAnalysis = new ArrayList<>();
                    
                    for (Object[] row : hourlyData) {
                        int hour = ((Number) row[0]).intValue();
                        long orderCount = ((Number) row[1]).longValue();
                        
                        // Format hour range (e.g., "9 AM - 10 AM")
                        String hourRange = formatHourRange(hour);
                        
                        // Calculate percentage
                        BigDecimal percentage = BigDecimal.valueOf(orderCount)
                                .divide(BigDecimal.valueOf(totalOrders), 4, java.math.RoundingMode.HALF_UP)
                                .multiply(BigDecimal.valueOf(100))
                                .setScale(2, java.math.RoundingMode.HALF_UP);
                        
                        peakHourAnalysis.add(PeakHourAnalysis.builder()
                                .hourRange(hourRange)
                                .percentage(percentage)
                                .build());
                    }
                    
                    return peakHourAnalysis;
                } catch (Exception e) {
                    logger.warn("Error calculating peak hour analysis: {}", e.getMessage());
                    return new ArrayList<>();
                }
            }, executorService);

            // Wait for all futures to complete
            CompletableFuture.allOf(dailyRevenueFuture, completedTransactionCountFuture, activeOrdersFuture,
                    recentlyUnavailableItemsFuture, managersFuture, orderStatusBreakdownFuture,
                    onShiftStaffFuture, tableOccupancyFuture, peakHourAnalysisFuture).join();

            // Get results
            BigDecimal dailyRevenue = dailyRevenueFuture.join();
            long completedTransactionCount = completedTransactionCountFuture.join();
            long activeOrders = activeOrdersFuture.join();
            BigDecimal avgBillValue = completedTransactionCount > 0 
                    ? dailyRevenue.divide(BigDecimal.valueOf(completedTransactionCount), 2, java.math.RoundingMode.HALF_UP)
                    : BigDecimal.ZERO;
            List<UnavailableItem> recentlyUnavailableItems = recentlyUnavailableItemsFuture.join();
            ManagerListResponse managers = managersFuture.join();
            OrderStatusBreakdown orderStatusBreakdown = orderStatusBreakdownFuture.join();
            OnShiftStaffListResponse onShiftStaff = onShiftStaffFuture.join();
            // topPerformingItems and leastPerformingItem are already calculated synchronously above
            BigDecimal tableOccupancy = tableOccupancyFuture.join();
            List<PeakHourAnalysis> peakHourAnalysis = peakHourAnalysisFuture.join();

            String currency = restaurantChainConfigProperties.getChain() != null ? restaurantChainConfigProperties.getChain().getCurrency() : null;
            RestaurantDashboardResponse response = RestaurantDashboardResponse.builder()
                    .dailyRevenue(dailyRevenue != null ? CurrencyFormatter.formatAmount(dailyRevenue, currency) : null)
                    .avgBillValue(avgBillValue != null ? CurrencyFormatter.formatAmount(avgBillValue, currency) : null)
                    .activeOrders(activeOrders)
                    .recentlyUnavailableItems(recentlyUnavailableItems)
                    .managers(managers)
                    .orderStatusBreakdown(orderStatusBreakdown)
                    .onShiftStaff(onShiftStaff)
                    .topPerformingItems(topPerformingItems)
                    .leastPerformingItem(leastPerformingItem)
                    .tableOccupancy(tableOccupancy)
                    .peakHourAnalysis(peakHourAnalysis)
                    .build();

            return ResponseDto.<RestaurantDashboardResponse>builder()
                    .data(response)
                    .message(messageUtil.getMessage("restaurant.dashboard.retrieved.success", localeObj))
                    .build();

        } catch (ResponseStatusException e) {
            throw e;
        } catch (Exception e) {
            logger.error("Error fetching restaurant dashboard", e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    messageUtil.getMessage("restaurant.dashboard.error.general", localeObj));
        }
    }

    /**
     * Retrieves up to the 5 most recently updated unavailable items for the given scope.
     * <p>
     * Uses a repository query to find recent unavailable {@link RestaurantItemAvailability} records, then batch-fetches
     * item and category translations to avoid per-row queries. Categories are resolved to a "main category" (parent
     * category when the mapping points to a subcategory) for display.
     * </p>
     *
     * @param restaurantId      optional restaurant scope
     * @param restaurantGroupId optional group scope
     * @param locale            locale tag used to select translated names
     * @return list of unavailable item summaries (empty on error)
     */
    private List<UnavailableItem> getRecentlyUnavailableItems(UUID restaurantId, UUID restaurantGroupId, String locale) {
        try {
            logger.info("Fetching recently unavailable items - restaurantId: {}, restaurantGroupId: {}, locale: {}", 
                    restaurantId, restaurantGroupId, locale);
            
            // If both restaurantId and restaurantGroupId are null, returns all unavailable items 
            // across all restaurants, ordered by updatedAt DESC (most recent first)
            // JPQL handles null parameters properly, so pass null directly instead of sentinel UUID
            List<RestaurantItemAvailability> unavailableItems = 
                    restaurantItemAvailabilityRepository.findRecentlyUnavailableItems(restaurantId, restaurantGroupId);
            
            logger.info("Found {} unavailable items from database query", unavailableItems.size());
            
            if (unavailableItems.isEmpty()) {
                logger.warn("No unavailable items found in database for restaurantId: {}, restaurantGroupId: {}", 
                        restaurantId, restaurantGroupId);
                return new ArrayList<>();
            }
            
            // Collect all item IDs and category IDs for batch fetching
            List<UUID> itemIds = new ArrayList<>();
            List<UUID> categoryIds = new ArrayList<>();
            List<RestaurantItemAvailability> validItems = new ArrayList<>();
            
            // Process up to 5 most recent unavailable items (or all if less than 5 available)
            int skippedCount = 0;
            for (RestaurantItemAvailability availability : unavailableItems) {
                if (validItems.size() >= 5) break;
                
                if (availability.getCategoryItemMapping() != null && 
                    availability.getCategoryItemMapping().getItem() != null) {
                    UUID itemId = availability.getCategoryItemMapping().getItem().getId();
                    itemIds.add(itemId);
                    
                    // Resolve main category (parent category if subcategory) from MenuCategoryMapping
                    if (availability.getCategoryItemMapping().getMenuCategoryMapping() != null) {
                        MenuCategoryMapping mcm = availability.getCategoryItemMapping().getMenuCategoryMapping();
                        // Get main category: parent category if exists, otherwise the category itself
                        Category mainCategory = (mcm.getParentCategory() != null) ? mcm.getParentCategory() : mcm.getCategory();
                        if (mainCategory != null && mainCategory.getId() != null) {
                            UUID categoryId = mainCategory.getId();
                            categoryIds.add(categoryId);
                        }
                    }
                    
                    validItems.add(availability);
                } else {
                    skippedCount++;
                    logger.debug("Skipping unavailable item - categoryItemMapping or item is null. Availability ID: {}", 
                            availability.getId());
                }
            }
            
            logger.info("Processed {} unavailable items: {} valid, {} skipped", 
                    unavailableItems.size(), validItems.size(), skippedCount);
            
            // Batch fetch all translations
            Map<UUID, List<ItemTranslation>> itemTranslationsMap = itemIds.isEmpty()
                    ? Collections.emptyMap()
                    : itemTranslationRepository.findAllByItemIdIn(itemIds)
                            .stream()
                            .collect(Collectors.groupingBy(t -> t.getItem().getId()));
            
            Map<UUID, List<CategoryTranslation>> categoryTranslationsMap = categoryIds.isEmpty()
                    ? Collections.emptyMap()
                    : categoryTranslationRepository.findAllByCategoryIdIn(categoryIds)
                            .stream()
                            .collect(Collectors.groupingBy(t -> t.getCategory().getId()));
            
            // Build result using batch-fetched data
            List<UnavailableItem> result = new ArrayList<>();
            
            for (RestaurantItemAvailability availability : validItems) {
                UUID itemId = availability.getCategoryItemMapping().getItem().getId();
                
                // Get item name from batch-fetched translations
                String itemName = "";
                List<ItemTranslation> itemTranslations = 
                        itemTranslationsMap.getOrDefault(itemId, Collections.emptyList());
                if (!itemTranslations.isEmpty()) {
                    ItemTranslation exactMatch = itemTranslations.stream()
                            .filter(t -> t.getLanguageCode() != null && t.getLanguageCode().equalsIgnoreCase(locale))
                            .findFirst()
                            .orElse(null);
                    
                    if (exactMatch != null) {
                        itemName = exactMatch.getName() != null ? exactMatch.getName() : "";
                    } else {
                        Optional<ItemTranslation> translation = 
                                TranslationUtils.pickPreferredOrFromList(
                                        itemTranslations, locale, localizationProperties.getLanguages(),
                                        ItemTranslation::getLanguageCode);
                        itemName = translation.map(t -> t.getName() != null ? t.getName() : "").orElse("");
                    }
                }
                
                // Get category name from batch-fetched translations
                // Resolve main category (parent category if subcategory) from MenuCategoryMapping
                String categoryName = "";
                if (availability.getCategoryItemMapping().getMenuCategoryMapping() != null) {
                    MenuCategoryMapping mcm = availability.getCategoryItemMapping().getMenuCategoryMapping();
                    // Get main category: parent category if exists, otherwise the category itself
                    Category mainCategory = (mcm.getParentCategory() != null) ? mcm.getParentCategory() : mcm.getCategory();
                    
                    if (mainCategory != null && mainCategory.getId() != null) {
                        UUID categoryId = mainCategory.getId();
                        List<CategoryTranslation> categoryTranslations = 
                                categoryTranslationsMap.getOrDefault(categoryId, Collections.emptyList());
                        if (!categoryTranslations.isEmpty()) {
                            CategoryTranslation exactMatch = categoryTranslations.stream()
                                    .filter(t -> t.getLanguageCode() != null && t.getLanguageCode().equalsIgnoreCase(locale))
                                    .findFirst()
                                    .orElse(null);
                            
                            if (exactMatch != null) {
                                categoryName = exactMatch.getName() != null ? exactMatch.getName() : "";
                            } else {
                                Optional<CategoryTranslation> translation = 
                                        TranslationUtils.pickPreferredOrFromList(
                                                categoryTranslations, locale, localizationProperties.getLanguages(),
                                                CategoryTranslation::getLanguageCode);
                                categoryName = translation.map(t -> t.getName() != null ? t.getName() : "").orElse("");
                            }
                        }
                    }
                }
                
                // Get image URL
                String imageUrl = awsService.getFullUrl(availability.getCategoryItemMapping().getItem().getImageUrl());
                
                result.add(UnavailableItem.builder()
                        .itemId(itemId)
                        .itemCode(availability.getCategoryItemMapping().getItem().getItemCode())
                        .itemName(itemName)
                        .categoryName(categoryName)
                        .imageUrl(imageUrl)
                        .madeUnavailableAt(availability.getUpdatedAt())
                        .build());
            }
            
            logger.info("Returning {} recently unavailable items", result.size());
            return result;
        } catch (Exception e) {
            logger.error("Error fetching recently unavailable items - restaurantId: {}, restaurantGroupId: {}, error: {}", 
                    restaurantId, restaurantGroupId, e.getMessage(), e);
            return new ArrayList<>();
        }
    }

    /**
     * Retrieves active managers for the given scope with paging metadata.
     * <p>
     * Resolves the MANAGER role, scopes users by restaurant or group (when provided), fetches shift mappings in bulk
     * to support on-shift calculations, then paginates the resulting manager list in-memory.
     * </p>
     *
     * @param restaurantId      optional restaurant scope
     * @param restaurantGroupId optional group scope
     * @param page              1-based page number
     * @param size              page size
     * @param locale            locale tag used for message localization
     * @return manager list response (empty result on error)
     */
    private ManagerListResponse getActiveManagers(UUID restaurantId, UUID restaurantGroupId, Integer page, Integer size, String locale) {
        try {
            logger.info("Getting active managers - restaurantId: {}, restaurantGroupId: {}", restaurantId, restaurantGroupId);
            
            // Find MANAGER role - try both exact match and case-insensitive
            Optional<Role> managerRoleOpt = roleRepository.findByName("MANAGER");
            if (managerRoleOpt.isEmpty()) {
                // Try case-insensitive search
                List<Role> allRoles = roleRepository.findAll();
                managerRoleOpt = allRoles.stream()
                        .filter(r -> r.getName() != null && "MANAGER".equalsIgnoreCase(r.getName()))
                        .findFirst();
            }
            if (managerRoleOpt.isEmpty()) {
                logger.error("MANAGER role not found in database. Available roles: {}",
                        roleRepository.findAll().stream().map(Role::getName).collect(Collectors.toList()));
                return ManagerListResponse.builder()
                        .managers(new ArrayList<>())
                        .count(0L)
                        .total(0L)
                        .metaData(PaginationMetaData.builder()
                                .page(page != null && page > 0 ? page : 1)
                                .size(size != null && size > 0 ? size : 10)
                                .totalPages(0)
                                .totalRecords(0L)
                                .build())
                        .build();
            }
            UUID managerRoleId = managerRoleOpt.get().getId();
            logger.info("Found MANAGER role with ID: {}", managerRoleId);
            
            // Get active managers
            List<User> managers;
            if (restaurantId != null) {
                managers = userRepository.findAllByRestaurantIdAndRoleIdAndIsDeletedFalse(restaurantId, managerRoleId).stream()
                        .filter(u -> u.getStatus() == EntityStatus.ACTIVE)
                        .collect(Collectors.toList());
                logger.debug("Found {} managers for restaurantId: {}", managers.size(), restaurantId);
            } else if (restaurantGroupId != null) {
                // Get all restaurants in the group
                List<Restaurant> restaurants =
                        restaurantRepository.findByRestaurantGroupIdAndIsDeletedFalse(restaurantGroupId);
                List<UUID> restaurantIds = restaurants.stream()
                        .map(Restaurant::getId)
                        .collect(Collectors.toList());
                logger.debug("Found {} restaurants in group: {}", restaurantIds.size(), restaurantGroupId);
                // Use efficient query instead of loading all users
                managers = userRepository.findAllByRoleIdAndStatusAndIsDeletedFalse(managerRoleId, EntityStatus.ACTIVE).stream()
                        .filter(u -> restaurantIds.contains(u.getRestaurantId()))
                        .collect(Collectors.toList());
                logger.debug("Found {} managers in restaurant group", managers.size());
            } else {
                // Use efficient query instead of loading all users
                managers = userRepository.findAllByRoleIdAndStatusAndIsDeletedFalse(managerRoleId, EntityStatus.ACTIVE);
                logger.debug("Found {} managers across all restaurants", managers.size());
            }
            
            logger.info("Found {} managers before shift mapping fetch", managers.size());
            
            // Batch fetch all shift mappings with JOIN FETCH to ensure shift is loaded
            // Then for each user, take the first one (matching findFirstByUser_Id behavior)
            Map<UUID, UserShiftMapping> shiftMappingMap = new java.util.HashMap<>();
            if (!managers.isEmpty()) {
                List<UUID> managerIds = managers.stream()
                        .map(User::getId)
                        .collect(Collectors.toList());
                
                // Use findAllByUser_IdIn which has JOIN FETCH to ensure shift is loaded in CompletableFuture context
                List<UserShiftMapping> allShiftMappings = userShiftMappingRepository.findAllByUser_IdIn(managerIds);
                
                // Group by userId and take the first one for each user (matching findFirstByUser_Id behavior)
                // findFirstByUser_Id orders by composite primary key (userId, shiftId)
                // We need to sort by the composite key to match exactly what findFirstByUser_Id does
                shiftMappingMap = allShiftMappings.stream()
                        .collect(Collectors.groupingBy(
                                usm -> usm.getUser().getId(),
                                Collectors.collectingAndThen(
                                        Collectors.toList(),
                                        list -> {
                                            // Sort by composite key (userId, shiftId) to match findFirstByUser_Id ordering
                                            // Spring Data JPA's findFirstBy orders by the composite primary key
                                            list.sort((a, b) -> {
                                                UserShiftId idA = a.getId();
                                                UserShiftId idB = b.getId();
                                                if (idA == null && idB == null) return 0;
                                                if (idA == null) return 1;
                                                if (idB == null) return -1;
                                                
                                                // Compare userId first (should be same for same user, but for consistency)
                                                int userIdCompare = idA.getUserId().compareTo(idB.getUserId());
                                                if (userIdCompare != 0) return userIdCompare;
                                                
                                                // Then compare shiftId - this determines the order for same user
                                                // This matches findFirstByUser_Id which orders by composite key
                                                return idA.getShiftId().compareTo(idB.getShiftId());
                                            });
                                            // Take the first one (same as findFirstByUser_Id)
                                            return list.isEmpty() ? null : list.get(0);
                                        }
                                )
                        ));
                
                // Remove null values
                shiftMappingMap.values().removeIf(java.util.Objects::isNull);
                
                logger.info("Found {} shift mappings for {} managers", shiftMappingMap.size(), managers.size());
            }
            
            if (managers.isEmpty()) {
                logger.warn("No active managers found for restaurantId: {}, restaurantGroupId: {}. Manager role ID: {}",
                        restaurantId, restaurantGroupId, managerRoleId);
                // Debug: Check if there are any managers at all (even inactive)
                if (restaurantId != null) {
                    long totalManagers = userRepository.findAllByRestaurantIdAndRoleIdAndIsDeletedFalse(restaurantId, managerRoleId).size();
                    logger.warn("Total managers (including inactive) for restaurant {}: {}", restaurantId, totalManagers);
                }
                return ManagerListResponse.builder()
                        .managers(new ArrayList<>())
                        .count(0L)
                        .total(0L)
                        .metaData(PaginationMetaData.builder()
                                .page(page != null && page > 0 ? page : 1)
                                .size(size != null && size > 0 ? size : 10)
                                .totalPages(0)
                                .totalRecords(0L)
                                .build())
                        .build();
            }
            
            // Batch fetch restaurant names
            List<UUID> managerRestaurantIds = managers.stream()
                    .map(User::getRestaurantId)
                    .filter(java.util.Objects::nonNull)
                    .distinct()
                    .collect(Collectors.toList());
            
            Map<UUID, String> restaurantNameMap = new java.util.HashMap<>();
            if (!managerRestaurantIds.isEmpty()) {
                // Batch fetch restaurants
                List<Restaurant> managerRestaurants = restaurantRepository.findAllById(managerRestaurantIds);
                
                // Batch fetch translations separately to avoid lazy loading issues
                Map<UUID, List<RestaurantTranslation>> translationsMap = restaurantTranslationRepository
                        .findAllByRestaurantIdIn(managerRestaurantIds)
                        .stream()
                        .collect(Collectors.groupingBy(rt -> rt.getRestaurant().getId()));
                
                String requestedLocale = (locale != null ? locale : "en").toLowerCase();
                String requestedLangCode = requestedLocale.contains("-")
                        ? requestedLocale.substring(0, requestedLocale.indexOf('-'))
                        : requestedLocale;
                
                for (Restaurant restaurant : managerRestaurants) {
                    String nameForLocale = null;
                    List<RestaurantTranslation> translations = translationsMap.get(restaurant.getId());
                    
                    if (translations != null && !translations.isEmpty()) {
                        // First try exact language_code match
                        for (RestaurantTranslation rt : translations) {
                            if (rt.getLanguageCode() != null &&
                                    rt.getLanguageCode().equalsIgnoreCase(requestedLangCode)) {
                                nameForLocale = rt.getName();
                                break;
                            }
                        }
                        
                        // If no exact match, use TranslationUtils for fallback
                        if (nameForLocale == null) {
                            Optional<RestaurantTranslation> translation = TranslationUtils.pickPreferredOrFromList(
                                    translations,
                                    locale,
                                    localizationProperties.getLanguages(),
                                    RestaurantTranslation::getLanguageCode
                            );
                            if (translation.isPresent()) {
                                nameForLocale = translation.get().getName();
                            }
                        }
                        
                        // Final fallback to first available translation
                        if (nameForLocale == null) {
                            nameForLocale = translations.get(0).getName();
                        }
                    }
                    
                    // Final fallback: restaurant code or group code
                    if (nameForLocale == null) {
                        if (restaurant.getRestaurantCode() != null) {
                            nameForLocale = restaurant.getRestaurantCode();
                        } else if (restaurant.getRestaurantGroup() != null && restaurant.getRestaurantGroup().getRestaurantGroupCode() != null) {
                            nameForLocale = restaurant.getRestaurantGroup().getRestaurantGroupCode();
                        } else {
                            nameForLocale = "";
                        }
                    }
                    
                    if (restaurant.getId() != null) {
                        restaurantNameMap.put(restaurant.getId(), nameForLocale);
                    }
                }
            }
            
            List<ManagerDetails> allManagerDetails = new ArrayList<>();
            
            for (User manager : managers) {
                logger.debug("Processing manager: {} (ID: {}), Status: {}, IsDeleted: {}", 
                        manager.getFirstName() + " " + manager.getLastName(), 
                        manager.getId(), manager.getStatus(), manager.getIsDeleted());
                // Get shift information from batch-fetched map
                // The shift is already loaded via JOIN FETCH, so we can access it directly
                String shiftTime = "";
                java.time.OffsetTime shiftStartTime = null;
                java.time.OffsetTime shiftEndTime = null;
                
                UserShiftMapping shiftMapping = shiftMappingMap.get(manager.getId());
                if (shiftMapping != null && shiftMapping.getShift() != null) {
                    Shift shift = shiftMapping.getShift();
                    // Get start_time and end_time directly from shift table
                    // Database has: 09:00:00+00 and 17:00:00+00 in UTC
                    // Hibernate may convert TIMETZ based on JVM timezone, so we need to normalize to UTC
                    if (shift.getStartTime() != null) {
                        // Hibernate converts TIMETZ from database based on JVM timezone
                        // Database has 09:00:00+00, but Hibernate reads it as 14:30Z (IST +05:30 converted)
                        // We need to reverse the conversion: subtract JVM timezone offset to get original UTC time
                        java.time.OffsetTime startTimeFromDb = shift.getStartTime();
                        // Get the local time (without timezone) and create new OffsetTime in UTC
                        // This preserves the actual time value from database
                        java.time.LocalTime localStartTime = startTimeFromDb.toLocalTime();
                        // Adjust for JVM timezone offset: if Hibernate converted +05:30, we subtract it
                        java.time.ZoneOffset jvmOffset = java.time.ZoneId.systemDefault().getRules().getOffset(java.time.Instant.now());
                        java.time.LocalTime adjustedStartTime = localStartTime.minusSeconds(jvmOffset.getTotalSeconds());
                        shiftStartTime = java.time.OffsetTime.of(adjustedStartTime, java.time.ZoneOffset.UTC);
                    }
                    if (shift.getEndTime() != null) {
                        // Same adjustment for end time
                        java.time.OffsetTime endTimeFromDb = shift.getEndTime();
                        java.time.LocalTime localEndTime = endTimeFromDb.toLocalTime();
                        java.time.ZoneOffset jvmOffset = java.time.ZoneId.systemDefault().getRules().getOffset(java.time.Instant.now());
                        java.time.LocalTime adjustedEndTime = localEndTime.minusSeconds(jvmOffset.getTotalSeconds());
                        shiftEndTime = java.time.OffsetTime.of(adjustedEndTime, java.time.ZoneOffset.UTC);
                    }
                    if (shiftStartTime != null && shiftEndTime != null) {
                        // Format as "09:00Z - 17:00Z" - OffsetTime.toString() formats with timezone
                        shiftTime = shiftStartTime.toString() + " - " + shiftEndTime.toString();
                        logger.info("Manager {} shift: shiftId={}, startTime={}, endTime={}, shiftTime={}", 
                                manager.getId(), shift.getId(), shiftStartTime, shiftEndTime, shiftTime);
                    } else {
                        logger.debug("Manager {} shift times are null: startTime={}, endTime={}", 
                                manager.getId(), shift.getStartTime(), shift.getEndTime());
                    }
                } else {
                    logger.debug("No shift mapping found for manager {}", manager.getId());
                }
                
                // Get restaurant name from batch-fetched map
                String restaurantName = manager.getRestaurantId() != null 
                        ? restaurantNameMap.getOrDefault(manager.getRestaurantId(), "")
                        : "";
                
                allManagerDetails.add(ManagerDetails.builder()
                        .managerId(manager.getId())
                        .name((manager.getFirstName() != null ? manager.getFirstName() : "") + 
                              " " + (manager.getLastName() != null ? manager.getLastName() : ""))
                        .contactNumber(manager.getContactNumber())
                        .email(manager.getEmail())
                        .shiftTime(shiftTime)
                        .shiftStartTime(shiftStartTime)
                        .shiftEndTime(shiftEndTime)
                        .photoUrl(awsService.getFullUrl(manager.getPhotoUrl()))
                        .restaurantName(restaurantName)
                        .build());
            }
            
            // Apply pagination
            int pageNumber = (page != null && page > 0) ? page - 1 : 0; // 0-based
            int pageSize = (size != null && size > 0) ? size : 10;

            int fromIndex = Math.min(pageNumber * pageSize, allManagerDetails.size());
            int toIndex = Math.min(fromIndex + pageSize, allManagerDetails.size());
            List<ManagerDetails> paginatedManagers;
            if (fromIndex >= allManagerDetails.size()) {
                paginatedManagers = new ArrayList<>();
            } else {
                paginatedManagers = allManagerDetails.subList(fromIndex, toIndex);
            }

            PaginationMetaData metaData = PaginationMetaData.builder()
                    .page(pageNumber + 1)
                    .size(pageSize)
                    .totalPages((int) Math.ceil((double) allManagerDetails.size() / pageSize))
                    .totalRecords((long) allManagerDetails.size())
                    .build();

            logger.info("Returning {} manager details (page {}, size {}, total: {})",
                    paginatedManagers.size(), pageNumber + 1, pageSize, allManagerDetails.size());
            if (paginatedManagers.isEmpty() && !managers.isEmpty()) {
                logger.warn("Managers were found but result is empty. This might indicate an issue with shift mapping or DTO building.");
            }
            return ManagerListResponse.builder()
                    .managers(paginatedManagers)
                    .count((long) paginatedManagers.size())
                    .total((long) allManagerDetails.size())
                    .metaData(metaData)
                    .build();
        } catch (Exception e) {
            logger.error("Error fetching active managers for restaurantId: {}, restaurantGroupId: {}. Error: {}",
                    restaurantId, restaurantGroupId, e.getMessage(), e);
            return ManagerListResponse.builder()
                    .managers(new ArrayList<>())
                    .count(0L)
                    .total(0L)
                    .metaData(PaginationMetaData.builder()
                            .page(page != null && page > 0 ? page : 1)
                            .size(size != null && size > 0 ? size : 10)
                            .totalPages(0)
                            .totalRecords(0L)
                            .build())
                    .build();
        }
    }

    /**
     * Computes order-status counts for the restaurant dashboard time window.
     * <p>
     * Uses sentinel values when filters are null, counts placed/pushed and served orders from Order-related queries,
     * counts cooking items from OrderedItem queries, and counts completed transactions from Transaction queries.
     * </p>
     *
     * @param restaurantId      optional restaurant scope
     * @param restaurantGroupId optional group scope
     * @param startDate         optional range start (UTC)
     * @param endDate           optional range end (UTC)
     * @return breakdown object (zeros on error)
     */
    private OrderStatusBreakdown getOrderStatusBreakdown(UUID restaurantId, UUID restaurantGroupId, LocalDateTime startDate, LocalDateTime endDate) {
        try {
            // Use sentinel UUID and sentinel date for null values
            UUID sentinelUuid = SENTINEL_UUID;
            LocalDateTime sentinelDate = LocalDateTime.of(1970, 1, 1, 0, 0, 0);
            long placedOrderCount = 0;
            long cookingCount = 0;
            long servedCount = 0;
            long completedCount = 0;
            
            LocalDateTime startDateParam = startDate != null ? startDate : sentinelDate;
            LocalDateTime endDateParam = endDate != null ? endDate : sentinelDate;
            
            OffsetDateTime startUtc = startDateParam.atOffset(ZoneOffset.UTC);
            OffsetDateTime endUtc = endDateParam.atOffset(ZoneOffset.UTC);
            if (restaurantId != null) {
                placedOrderCount = orderRepository.countByOrderStatusAndFilters(
                        OrderStatus.PUSHED.name(), restaurantId, sentinelUuid, startDateParam, endDateParam);
                cookingCount = orderedItemRepository.countCookingItemsByFilters(restaurantId, sentinelUuid, startDateParam, endDateParam);
                servedCount = orderRepository.countByOrderStatusAndFilters(
                        OrderStatus.SERVED.name(), restaurantId, sentinelUuid, startDateParam, endDateParam);
                completedCount = transactionRepository.countByRestaurantIdAndTransactionStatusAndDateRange(
                        restaurantId, TransactionStatus.COMPLETED, startUtc, endUtc);
            } else if (restaurantGroupId != null) {
                placedOrderCount = orderRepository.countByOrderStatusAndFilters(
                        OrderStatus.PUSHED.name(), sentinelUuid, restaurantGroupId, startDateParam, endDateParam);
                cookingCount = orderedItemRepository.countCookingItemsByFilters(sentinelUuid, restaurantGroupId, startDateParam, endDateParam);
                servedCount = orderRepository.countByOrderStatusAndFilters(
                        OrderStatus.SERVED.name(), sentinelUuid, restaurantGroupId, startDateParam, endDateParam);
                completedCount = transactionRepository.countByRestaurantGroupIdAndTransactionStatusAndDateRange(
                        restaurantGroupId, TransactionStatus.COMPLETED, startUtc, endUtc);
            } else {
                placedOrderCount = orderRepository.countByOrderStatusAndFilters(
                        OrderStatus.PUSHED.name(), sentinelUuid, sentinelUuid, startDateParam, endDateParam);
                cookingCount = orderedItemRepository.countCookingItemsByFilters(sentinelUuid, sentinelUuid, startDateParam, endDateParam);
                servedCount = orderRepository.countByOrderStatusAndFilters(
                        OrderStatus.SERVED.name(), sentinelUuid, sentinelUuid, startDateParam, endDateParam);
                completedCount = transactionRepository.countByTransactionStatusAndDateRange(
                        TransactionStatus.COMPLETED, startUtc, endUtc);
            }
            
            return OrderStatusBreakdown.builder()
                    .placedOrderCount(placedOrderCount)
                    .cookingCount(cookingCount)
                    .servedCount(servedCount)
                    .completedCount(completedCount)
                    .build();
        } catch (Exception e) {
            logger.warn("Error fetching order status breakdown: {}", e.getMessage());
            return OrderStatusBreakdown.builder()
                    .placedOrderCount(0L)
                    .cookingCount(0L)
                    .servedCount(0L)
                    .completedCount(0L)
                    .build();
        }
    }

    /**
     * Retrieves on-shift staff for the given scope and current UTC time.
     * <p>
     * Loads active users, joins shift mappings in bulk, determines who is currently on shift, enriches with role and
     * restaurant names, and then paginates results in-memory.
     * </p>
     *
     * @param restaurantId      optional restaurant scope
     * @param restaurantGroupId optional group scope
     * @param page              1-based page number
     * @param size              page size
     * @param locale            locale tag used for translation selection (e.g., restaurant names)
     * @return on-shift staff list response (empty result on error)
     */
    private OnShiftStaffListResponse getOnShiftStaff(UUID restaurantId, UUID restaurantGroupId, Integer page, Integer size, String locale) {
        try {
            // Get current time in UTC to compare with shift times stored in UTC
            java.time.LocalTime currentTime = java.time.OffsetDateTime.now(java.time.ZoneOffset.UTC).toLocalTime();
            
            // Get all active users
            List<User> allUsers;
            if (restaurantId != null) {
                allUsers = userRepository.findAllByRestaurantId(restaurantId).stream()
                        .filter(u -> u.getStatus() == EntityStatus.ACTIVE)
                        .filter(u -> !Boolean.TRUE.equals(u.getIsDeleted()))
                        .collect(Collectors.toList());
            } else if (restaurantGroupId != null) {
                List<UUID> restaurantIds = loadRestaurantIdsForGroup(restaurantGroupId);
                allUsers = userRepository.findAll().stream()
                        .filter(u -> u.getStatus() == EntityStatus.ACTIVE)
                        .filter(u -> !Boolean.TRUE.equals(u.getIsDeleted()))
                        .filter(u -> restaurantIds.contains(u.getRestaurantId()))
                        .collect(Collectors.toList());
            } else {
                allUsers = userRepository.findAll().stream()
                        .filter(u -> u.getStatus() == EntityStatus.ACTIVE)
                        .filter(u -> !Boolean.TRUE.equals(u.getIsDeleted()))
                        .collect(Collectors.toList());
            }
            
            logger.debug("Found {} active users for on-shift staff check", allUsers.size());
            
            if (allUsers.isEmpty()) {
                logger.warn("No active users found for restaurantId: {}, restaurantGroupId: {}", restaurantId, restaurantGroupId);
                return OnShiftStaffListResponse.builder()
                        .onShiftStaff(new ArrayList<>())
                        .count(0L)
                        .total(0L)
                        .metaData(PaginationMetaData.builder()
                                .page(page != null && page > 0 ? page : 1)
                                .size(size != null && size > 0 ? size : 10)
                                .totalPages(0)
                                .totalRecords(0L)
                                .build())
                        .build();
            }
            
            // Batch fetch all shift mappings
            List<UUID> userIds = allUsers.stream()
                    .map(User::getId)
                    .collect(Collectors.toList());
            
            Map<UUID, UserShiftMapping> shiftMappingMap = 
                    userShiftMappingRepository.findAllByUser_IdIn(userIds)
                            .stream()
                            .collect(Collectors.toMap(
                                    usm -> usm.getUser().getId(),
                                    usm -> usm,
                                    (existing, replacement) -> existing
                            ));
            
            logger.debug("Found {} shift mappings for {} users, current time: {}", shiftMappingMap.size(), allUsers.size(), currentTime);
            
            // Batch fetch all roles
            Set<UUID> roleIds = allUsers.stream()
                    .map(User::getRoleId)
                    .filter(java.util.Objects::nonNull)
                    .collect(Collectors.toSet());
            
            Map<UUID, Role> roleMap = roleIds.isEmpty()
                    ? Collections.emptyMap()
                    : roleRepository.findAllById(roleIds)
                            .stream()
                            .collect(Collectors.toMap(Role::getId, r -> r));

            // Batch fetch restaurant names for all users (for current locale)
            Map<UUID, String> restaurantNameMap = new java.util.HashMap<>();
            java.util.List<UUID> staffRestaurantIds = allUsers.stream()
                    .map(User::getRestaurantId)
                    .filter(java.util.Objects::nonNull)
                    .distinct()
                    .collect(Collectors.toList());

            if (!staffRestaurantIds.isEmpty()) {
                // Batch fetch restaurants
                java.util.List<Restaurant> staffRestaurants = restaurantRepository.findAllById(staffRestaurantIds);
                
                // Batch fetch translations separately to avoid lazy loading issues
                Map<UUID, List<RestaurantTranslation>> translationsMap = restaurantTranslationRepository
                        .findAllByRestaurantIdIn(staffRestaurantIds)
                        .stream()
                        .collect(Collectors.groupingBy(rt -> rt.getRestaurant().getId()));

                for (Restaurant restaurant : staffRestaurants) {
                    String nameForLocale = null;
                    java.util.List<RestaurantTranslation> translations = translationsMap.get(restaurant.getId());

                    if (translations != null && !translations.isEmpty()) {
                        // Prefer translation matching user's locale (language code part)
                        String requestedLocale = (locale != null ? locale : "en").toLowerCase();
                        String requestedLangCode = requestedLocale.contains("-")
                                ? requestedLocale.substring(0, requestedLocale.indexOf('-'))
                                : requestedLocale;

                        // First try exact language_code match
                        for (RestaurantTranslation rt : translations) {
                            if (rt.getLanguageCode() != null &&
                                    rt.getLanguageCode().equalsIgnoreCase(requestedLangCode)) {
                                nameForLocale = rt.getName();
                                break;
                            }
                        }

                        // Fallback to first available translation
                        if (nameForLocale == null) {
                            nameForLocale = translations.get(0).getName();
                        }
                    }

                    // Final fallback: restaurant code or group name
                    if (nameForLocale == null) {
                        nameForLocale = restaurant.getRestaurantCode() != null
                                ? restaurant.getRestaurantCode()
                                : restaurant.getRestaurantGroupName();
                    }

                    if (restaurant.getId() != null) {
                        restaurantNameMap.put(restaurant.getId(), nameForLocale);
                    }
                }
            }

            List<OnShiftStaff> allOnShiftStaff = new ArrayList<>();
            
            for (User user : allUsers) {
                try {
                    // Check if user is on shift using batch-fetched mapping
                    UserShiftMapping shiftMapping = shiftMappingMap.get(user.getId());
                    
                    if (shiftMapping != null) {
                        maybeAddOnShiftStaffBestEffort(allOnShiftStaff, user, shiftMapping, roleMap, restaurantNameMap, currentTime);
                    } else {
                        logger.debug("User {} has no shift mapping", user.getId());
                    }
                } catch (Exception e) {
                    logger.warn("Error processing user {}: {}", user.getId(), e.getMessage());
                    // Continue to next user
                }
            }
            
            // Apply pagination
            int pageNumber = (page != null && page > 0) ? page - 1 : 0; // Convert to 0-based
            int pageSize = (size != null && size > 0) ? size : 10; // Default to 10
            
            int fromIndex = Math.min(pageNumber * pageSize, allOnShiftStaff.size());
            int toIndex = Math.min(fromIndex + pageSize, allOnShiftStaff.size());
            List<OnShiftStaff> paginatedStaff;
            if (fromIndex >= allOnShiftStaff.size()) {
                paginatedStaff = new ArrayList<>();
            } else {
                paginatedStaff = allOnShiftStaff.subList(fromIndex, toIndex);
            }
            
            // Build pagination metadata
            PaginationMetaData metaData = PaginationMetaData.builder()
                    .page(pageNumber + 1)  // Convert back to 1-based
                    .size(pageSize)
                    .totalPages((int) Math.ceil((double) allOnShiftStaff.size() / pageSize))
                    .totalRecords((long) allOnShiftStaff.size())
                    .build();
            
            logger.debug("Returning {} on-shift staff members (page {}, size {}, total: {})", 
                    paginatedStaff.size(), pageNumber + 1, pageSize, allOnShiftStaff.size());
            
            return OnShiftStaffListResponse.builder()
                    .onShiftStaff(paginatedStaff)
                    .count((long) paginatedStaff.size())
                    .total((long) allOnShiftStaff.size())
                    .metaData(metaData)
                    .build();
        } catch (Exception e) {
            logger.error("Error fetching on-shift staff: {}", e.getMessage(), e);
            return OnShiftStaffListResponse.builder()
                    .onShiftStaff(new ArrayList<>())
                    .count(0L)
                    .total(0L)
                    .metaData(PaginationMetaData.builder()
                            .page(page != null && page > 0 ? page : 1)
                            .size(size != null && size > 0 ? size : 10)
                            .totalPages(0)
                            .totalRecords(0L)
                            .build())
                    .build();
        }
    }

    private List<UUID> loadRestaurantIdsForGroup(UUID restaurantGroupId) {
        List<Restaurant> restaurants = restaurantRepository.findByRestaurantGroupIdAndIsDeletedFalse(restaurantGroupId);
        return restaurants.stream()
                .map(Restaurant::getId)
                .filter(java.util.Objects::nonNull)
                .collect(Collectors.toList());
    }

    private void maybeAddOnShiftStaffBestEffort(List<OnShiftStaff> allOnShiftStaff,
                                                User user,
                                                UserShiftMapping shiftMapping,
                                                Map<UUID, Role> roleMap,
                                                Map<UUID, String> restaurantNameMap,
                                                java.time.LocalTime currentTime) {
        try {
            Shift shift = shiftMapping.getShift();
            if (shift == null) {
                return;
            }
            if (shift.getStatus() == null || shift.getStatus() != EntityStatus.ACTIVE) {
                logger.debug("User {} has shift with status: {}", user.getId(), shift.getStatus());
                return;
            }
            if (shift.getStartTime() == null || shift.getEndTime() == null) {
                logger.debug("User {} has shift without start/end time", user.getId());
                return;
            }

            java.time.LocalTime shiftStart = shift.getStartTime().toLocalTime();
            java.time.LocalTime shiftEnd = shift.getEndTime().toLocalTime();

            boolean isOnShift = isCurrentTimeWithinShift(currentTime, shiftStart, shiftEnd);
            if (!isOnShift) {
                logger.debug("User {} is not currently on shift (shift: {} - {}, current: {})",
                        user.getId(), shiftStart, shiftEnd, currentTime);
                return;
            }

            String roleName = "";
            if (user.getRoleId() != null) {
                Role role = roleMap.get(user.getRoleId());
                if (role != null) {
                    roleName = role.getName();
                }
            }

            String restaurantName = null;
            if (user.getRestaurantId() != null) {
                restaurantName = restaurantNameMap.get(user.getRestaurantId());
            }

            allOnShiftStaff.add(OnShiftStaff.builder()
                    .staffId(user.getId())
                    .name((user.getFirstName() != null ? user.getFirstName() : "") +
                            " " + (user.getLastName() != null ? user.getLastName() : ""))
                    .roleName(roleName)
                    .photoUrl(awsService.getFullUrl(user.getPhotoUrl()))
                    .restaurantName(restaurantName)
                    .build());
        } catch (Exception e) {
            logger.warn("Error accessing shift for user {}: {}", user.getId(), e.getMessage());
        }
    }

    private boolean isCurrentTimeWithinShift(java.time.LocalTime currentTime,
                                             java.time.LocalTime shiftStart,
                                             java.time.LocalTime shiftEnd) {
        if (shiftStart.isBefore(shiftEnd) || shiftStart.equals(shiftEnd)) {
            // Normal shift (e.g., 09:00 - 17:00)
            return (currentTime.isAfter(shiftStart) || currentTime.equals(shiftStart))
                    && (currentTime.isBefore(shiftEnd) || currentTime.equals(shiftEnd));
        }
        // Overnight shift (e.g., 22:00 - 06:00)
        return (currentTime.isAfter(shiftStart) || currentTime.equals(shiftStart))
                || (currentTime.isBefore(shiftEnd) || currentTime.equals(shiftEnd));
    }

    /**
     * Returns the top-performing items (by order count) for the given scope and time window.
     * <p>
     * Uses aggregated query results, then batch-fetches item/category translations and item metadata to build
     * display-ready responses including image URLs and formatted revenue.
     * </p>
     *
     * @param restaurantId      optional restaurant scope (or sentinel UUID)
     * @param restaurantGroupId optional group scope (or sentinel UUID)
     * @param startDate         range start (or sentinel date)
     * @param endDate           range end (or sentinel date)
     * @param locale            locale tag used for translation selection
     * @return list of up to two performing items (empty on error)
     */
    private List<PerformingItem> getTop2PerformingItems(UUID restaurantId, UUID restaurantGroupId, LocalDateTime startDate, LocalDateTime endDate, String locale) {
        try {
            // Use sentinel UUID and sentinel date for null values
            UUID sentinelUuid = SENTINEL_UUID;
            LocalDateTime sentinelDate = LocalDateTime.of(1970, 1, 1, 0, 0, 0);
            UUID restaurantIdParam = restaurantId != null ? restaurantId : sentinelUuid;
            UUID restaurantGroupIdParam = restaurantGroupId != null ? restaurantGroupId : sentinelUuid;
            LocalDateTime startDateParam = startDate != null ? startDate : sentinelDate;
            LocalDateTime endDateParam = endDate != null ? endDate : sentinelDate;
            
            List<Object[]> topItemsData = orderedItemRepository.findTop1ItemsByOrderCount(restaurantIdParam, restaurantGroupIdParam, startDateParam, endDateParam);
            
            if (topItemsData == null || topItemsData.isEmpty()) {
                return new ArrayList<>();
            }
            
            // Collect all item IDs and category IDs for batch fetching
            List<UUID> itemIds = new ArrayList<>();
            List<UUID> categoryIds = new ArrayList<>();
            List<Object[]> validRows = new ArrayList<>();
            
            for (Object[] row : topItemsData) {
                ParsedItemCategoryIds ids = parseItemAndCategoryIdsBestEffort(row);
                if (ids == null || ids.itemId() == null) {
                    continue;
                }
                itemIds.add(ids.itemId());
                validRows.add(row);
                if (ids.categoryId() != null) {
                    categoryIds.add(ids.categoryId());
                }
            }
            
            // Batch fetch all translations and items
            Map<UUID, List<ItemTranslation>> itemTranslationsMap = itemIds.isEmpty()
                    ? Collections.emptyMap()
                    : itemTranslationRepository.findAllByItemIdIn(itemIds)
                            .stream()
                            .collect(Collectors.groupingBy(t -> t.getItem().getId()));
            
            Map<UUID, List<CategoryTranslation>> categoryTranslationsMap = categoryIds.isEmpty()
                    ? Collections.emptyMap()
                    : categoryTranslationRepository.findAllByCategoryIdIn(categoryIds)
                            .stream()
                            .collect(Collectors.groupingBy(t -> t.getCategory().getId()));
            
            Map<UUID, Item> itemMap = itemIds.isEmpty()
                    ? Collections.emptyMap()
                    : itemRepository.findAllById(itemIds)
                            .stream()
                            .collect(Collectors.toMap(Item::getId, item -> item));
            
            List<PerformingItem> result = new ArrayList<>();
            String currency = restaurantChainConfigProperties.getChain() != null ? restaurantChainConfigProperties.getChain().getCurrency() : null;
            
            for (Object[] row : validRows) {
                try {
                    UUID itemId = parseUuidFromRow(row[0]);
                    UUID categoryId = row[1] != null ? parseUuidFromRow(row[1]) : null;
                    
                    Long totalQuantity = ((Number) row[2]).longValue();
                    BigDecimal revenue;
                    if (row[3] instanceof BigDecimal) {
                        revenue = (BigDecimal) row[3];
                    } else if (row[3] instanceof Number) {
                        revenue = BigDecimal.valueOf(((Number) row[3]).doubleValue());
                    } else {
                        revenue = BigDecimal.ZERO;
                    }
                    
                    // Get item name from batch-fetched translations
                    String itemName = "";
                    List<ItemTranslation> itemTranslations = 
                            itemTranslationsMap.getOrDefault(itemId, Collections.emptyList());
                    if (!itemTranslations.isEmpty()) {
                        ItemTranslation exactMatch = itemTranslations.stream()
                                .filter(t -> t.getLanguageCode() != null && t.getLanguageCode().equalsIgnoreCase(locale))
                                .findFirst()
                                .orElse(null);
                        
                        if (exactMatch != null) {
                            itemName = exactMatch.getName() != null ? exactMatch.getName() : "";
                        } else {
                            Optional<ItemTranslation> translation = 
                                    TranslationUtils.pickPreferredOrFromList(
                                            itemTranslations, locale, localizationProperties.getLanguages(),
                                            ItemTranslation::getLanguageCode);
                            itemName = translation.map(t -> t.getName() != null ? t.getName() : "").orElse("");
                        }
                    }
                    
                    // Get category name from batch-fetched translations
                    String categoryName = "";
                    if (categoryId != null) {
                        List<CategoryTranslation> categoryTranslations = 
                                categoryTranslationsMap.getOrDefault(categoryId, Collections.emptyList());
                        if (!categoryTranslations.isEmpty()) {
                            CategoryTranslation exactMatch = categoryTranslations.stream()
                                    .filter(t -> t.getLanguageCode() != null && t.getLanguageCode().equalsIgnoreCase(locale))
                                    .findFirst()
                                    .orElse(null);
                            
                            if (exactMatch != null) {
                                categoryName = exactMatch.getName() != null ? exactMatch.getName() : "";
                            } else {
                                Optional<CategoryTranslation> translation = 
                                        TranslationUtils.pickPreferredOrFromList(
                                                categoryTranslations, locale, localizationProperties.getLanguages(),
                                                CategoryTranslation::getLanguageCode);
                                categoryName = translation.map(t -> t.getName() != null ? t.getName() : "").orElse("");
                            }
                        }
                    }
                    
                    // Get image URL and price from batch-fetched items
                    String imageUrl = "";
                    BigDecimal price = BigDecimal.ZERO;
                    Item item = itemMap.get(itemId);
                    if (item != null) {
                        imageUrl = awsService.getFullUrl(item.getImageUrl());
                        if (item.getBasePrice() != null) {
                            price = BigDecimal.valueOf(item.getBasePrice());
                        }
                    }
                    
                    result.add(PerformingItem.builder()
                            .itemCode(item != null ? item.getItemCode() : null)
                            .itemName(itemName)
                            .categoryName(categoryName)
                            .imageUrl(imageUrl)
                            .totalSold(totalQuantity)
                            .revenue(revenue != null ? CurrencyFormatter.formatAmount(revenue, currency) : CurrencyFormatter.formatAmount(BigDecimal.ZERO, currency))
                            .price(price != null ? CurrencyFormatter.formatAmount(price, currency) : null)
                            .build());
                            
                } catch (Exception e) {
                    logger.warn("Error building performing item, skipping. Error: {}", e.getMessage());
                }
            }
            
            return result;
        } catch (Exception e) {
            logger.warn("Error fetching top performing items: {}", e.getMessage());
            return new ArrayList<>();
        }
    }

    /**
     * Returns the least-performing item (by order count) for the given scope and time window.
     * <p>
     * Attempts to resolve the "main category" via the restaurant’s LIVE menu mapping to avoid returning an arbitrary
     * subcategory, then batch-fetches translations and item metadata for response building.
     * </p>
     *
     * @param restaurantId      optional restaurant scope (or sentinel UUID)
     * @param restaurantGroupId optional group scope (or sentinel UUID)
     * @param startDate         range start (or sentinel date)
     * @param endDate           range end (or sentinel date)
     * @param locale            locale tag used for translation selection
     * @return least-performing item, or {@code null} when no data is available
     */
    private PerformingItem getLeastPerformingItem(UUID restaurantId, UUID restaurantGroupId, LocalDateTime startDate, LocalDateTime endDate, String locale) {
        try {
            // Use sentinel UUID and sentinel date for null values
            UUID sentinelUuid = SENTINEL_UUID;
            LocalDateTime sentinelDate = LocalDateTime.of(1970, 1, 1, 0, 0, 0);
            UUID restaurantIdParam = restaurantId != null ? restaurantId : sentinelUuid;
            UUID restaurantGroupIdParam = restaurantGroupId != null ? restaurantGroupId : sentinelUuid;
            LocalDateTime startDateParam = startDate != null ? startDate : sentinelDate;
            LocalDateTime endDateParam = endDate != null ? endDate : sentinelDate;
            
            List<Object[]> leastItemsData = orderedItemRepository.findLeastPerformingItemByOrderCount(restaurantIdParam, restaurantGroupIdParam, startDateParam, endDateParam);
            
            if (leastItemsData == null || leastItemsData.isEmpty()) {
                return null;
            }
            
            Object[] row = leastItemsData.get(0);
            try {
                // Handle native query results - UUIDs might come as String or UUID
                UUID itemId = parseUuidFromRow(row[0]);
                UUID categoryId = row[1] != null ? parseUuidFromRow(row[1]) : null;
                
                Long totalQuantity = ((Number) row[2]).longValue();
                BigDecimal revenue;
                if (row[3] instanceof BigDecimal) {
                    revenue = (BigDecimal) row[3];
                } else if (row[3] instanceof Number) {
                    revenue = BigDecimal.valueOf(((Number) row[3]).doubleValue());
                } else {
                    revenue = BigDecimal.ZERO;
                }
                
                // Resolve "main category" (parent category if subcategory) based on the restaurant's LIVE menu.
                if (restaurantId != null && !restaurantId.equals(sentinelUuid) && itemId != null) {
                    UUID resolvedCategoryId = resolveMainCategoryViaLiveMenu(restaurantId, itemId);
                    if (resolvedCategoryId != null) {
                        categoryId = resolvedCategoryId;
                    }
                } else if (restaurantGroupId != null && !restaurantGroupId.equals(sentinelUuid) && itemId != null) {
                    // When only restaurantGroupId is provided, check all restaurants in the group
                    try {
                        List<Restaurant> restaurants = restaurantRepository.findByRestaurantGroupIdAndIsDeletedFalse(restaurantGroupId);
                        for (Restaurant restaurant : restaurants) {
                            if (restaurant != null && restaurant.getId() != null) {
                                UUID resolvedCategoryId = resolveMainCategoryViaLiveMenu(restaurant.getId(), itemId);
                                if (resolvedCategoryId != null) {
                                    categoryId = resolvedCategoryId;
                                    break;
                                }
                            }
                        }
                    } catch (Exception e) {
                        logger.warn("Error resolving main category for least performing item (restaurantGroupId), falling back to query category. Error: {}", e.getMessage());
                    }
                } else if (itemId != null) {
                    // When neither restaurantId nor restaurantGroupId is provided, find item in LIVE menus
                    try {
                        UUID resolvedCategoryId = resolveCategoryViaAllMappings(itemId);
                        if (resolvedCategoryId != null) {
                            categoryId = resolvedCategoryId;
                        }
                    } catch (Exception e) {
                        logger.warn("Error resolving main category for least performing item (no restaurant filter), falling back to query category. Error: {}", e.getMessage());
                    }
                }
                
                // Batch fetch translations and item (even for single item, using batch methods is more efficient)
                List<UUID> itemIds = Collections.singletonList(itemId);
                List<UUID> categoryIds = categoryId != null ? Collections.singletonList(categoryId) : Collections.emptyList();
                
                Map<UUID, List<ItemTranslation>> itemTranslationsMap = 
                        itemTranslationRepository.findAllByItemIdIn(itemIds)
                                .stream()
                                .collect(Collectors.groupingBy(t -> t.getItem().getId()));
                
                Map<UUID, List<CategoryTranslation>> categoryTranslationsMap = categoryIds.isEmpty()
                        ? Collections.emptyMap()
                        : categoryTranslationRepository.findAllByCategoryIdIn(categoryIds)
                                .stream()
                                .collect(Collectors.groupingBy(t -> t.getCategory().getId()));
                
                Map<UUID, Item> itemMap = 
                        itemRepository.findAllById(itemIds)
                                .stream()
                                .collect(Collectors.toMap(Item::getId, item -> item));
                
                // Get item name from batch-fetched translations
                String itemName = "";
                List<ItemTranslation> itemTranslations = 
                        itemTranslationsMap.getOrDefault(itemId, Collections.emptyList());
                if (!itemTranslations.isEmpty()) {
                    ItemTranslation exactMatch = itemTranslations.stream()
                            .filter(t -> t.getLanguageCode() != null && t.getLanguageCode().equalsIgnoreCase(locale))
                            .findFirst()
                            .orElse(null);
                    
                    if (exactMatch != null) {
                        itemName = exactMatch.getName() != null ? exactMatch.getName() : "";
                    } else {
                        Optional<ItemTranslation> translation = 
                                TranslationUtils.pickPreferredOrFromList(
                                        itemTranslations, locale, localizationProperties.getLanguages(),
                                        ItemTranslation::getLanguageCode);
                        itemName = translation.map(t -> t.getName() != null ? t.getName() : "").orElse("");
                    }
                }
                
                // Get category name from batch-fetched translations
                String categoryName = "";
                if (categoryId != null) {
                    List<CategoryTranslation> categoryTranslations = 
                            categoryTranslationsMap.getOrDefault(categoryId, Collections.emptyList());
                    if (!categoryTranslations.isEmpty()) {
                        CategoryTranslation exactMatch = categoryTranslations.stream()
                                .filter(t -> t.getLanguageCode() != null && t.getLanguageCode().equalsIgnoreCase(locale))
                                .findFirst()
                                .orElse(null);
                        
                        if (exactMatch != null) {
                            categoryName = exactMatch.getName() != null ? exactMatch.getName() : "";
                        } else {
                            Optional<CategoryTranslation> translation = 
                                    TranslationUtils.pickPreferredOrFromList(
                                            categoryTranslations, locale, localizationProperties.getLanguages(),
                                            CategoryTranslation::getLanguageCode);
                            categoryName = translation.map(t -> t.getName() != null ? t.getName() : "").orElse("");
                        }
                    }
                }
                
                // Get image URL and price from batch-fetched item
                String imageUrl = "";
                BigDecimal price = BigDecimal.ZERO;
                Item item = itemMap.get(itemId);
                if (item != null) {
                    imageUrl = awsService.getFullUrl(item.getImageUrl());
                    if (item.getBasePrice() != null) {
                        price = BigDecimal.valueOf(item.getBasePrice());
                    }
                }
                
                String currency = restaurantChainConfigProperties.getChain() != null ? restaurantChainConfigProperties.getChain().getCurrency() : null;
                return PerformingItem.builder()
                        .itemCode(item != null ? item.getItemCode() : null)
                        .itemName(itemName)
                        .categoryName(categoryName)
                        .imageUrl(imageUrl)
                        .totalSold(totalQuantity)
                        .revenue(revenue != null ? CurrencyFormatter.formatAmount(revenue, currency) : CurrencyFormatter.formatAmount(BigDecimal.ZERO, currency))
                        .price(price != null ? CurrencyFormatter.formatAmount(price, currency) : null)
                        .build();
                        
            } catch (Exception e) {
                logger.warn("Error building least performing item, returning null. Error: {}", e.getMessage());
                return null;
            }
        } catch (Exception e) {
            logger.warn("Error fetching least performing item: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Format hour into a range string in UTC format (e.g., "7:00Z - 8:00Z")
     * @param hour The hour (0-23) in UTC
     * @return Formatted hour range string in UTC format
     */
    private String formatHourRange(int hour) {
        int nextHour = (hour + 1) % 24;
        String hourStr = String.format("%02d:00Z", hour);
        String nextHourStr = String.format("%02d:00Z", nextHour);
        return hourStr + " - " + nextHourStr;
    }

    /**
     * Format hour into 12-hour format with AM/PM (e.g., "9 AM", "10 PM")
     * @param hour The hour (0-23)
     * @return Formatted hour string
     */
    private String formatHour(int hour) {
        if (hour == 0) {
            return "12 AM";
        } else if (hour < 12) {
            return hour + " AM";
        } else if (hour == 12) {
            return "12 PM";
        } else {
            return (hour - 12) + " PM";
        }
    }

    /**
     * Builds the menu dashboard view for a given date range and scope.
     * <p>
     * Validates restaurant/group filters and menu-dashboard date parameters, computes the effective date window,
     * then aggregates menu metrics such as total menu items, published menu count, average order value, best-selling
     * items, and top/least performing items.
     * </p>
     *
     * @param dateRange         date-range selector (e.g., 30 days / 1 month / 3 months / CUSTOM)
     * @param startDate         custom start date-time (required when dateRange=CUSTOM)
     * @param endDate           custom end date-time (required when dateRange=CUSTOM)
     * @param restaurantGroupId optional group scope
     * @param restaurantId      optional restaurant scope (overrides group scope)
     * @param locale            locale tag used for message localization and translation selection
     * @return response wrapper containing {@link MenuDashboardResponse}
     * @throws ResponseStatusException when validation fails or an unexpected error occurs
     */
    @Override
    public ResponseDto<MenuDashboardResponse> getMenuDashboard(String dateRange, LocalDateTime startDate, LocalDateTime endDate, UUID restaurantGroupId, UUID restaurantId, String locale) {
        Locale localeObj = Locale.forLanguageTag(locale != null ? locale : "en");
        LocaleContextHolder.setLocale(localeObj);

        try {
            logger.info("Fetching menu dashboard with dateRange: {}, startDate: {}, endDate: {}, restaurantGroupId: {}, restaurantId: {}, locale: {}",
                    dateRange, startDate, endDate, restaurantGroupId, restaurantId, locale);

            // Validate restaurant filters
            validateRestaurantFilters(restaurantId, restaurantGroupId, localeObj);

            // Validate date parameters (similar to restaurant dashboard)
            validateDateParametersForMenuDashboard(dateRange, startDate, endDate, localeObj);

            // Calculate date range
            LocalDateTime rangeStartDate;
            LocalDateTime rangeEndDate;
            LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);

            if (dateRange != null && !dateRange.isEmpty()) {
                if (PERIOD_30_DAYS.equalsIgnoreCase(dateRange)) {
                    rangeStartDate = now.minusDays(30).withHour(0).withMinute(0).withSecond(0).withNano(0);
                    rangeEndDate = now.withHour(23).withMinute(59).withSecond(59).withNano(999999999);
                } else if ("1_MONTH".equalsIgnoreCase(dateRange)) {
                    rangeStartDate = now.minusMonths(1).withHour(0).withMinute(0).withSecond(0).withNano(0);
                    rangeEndDate = now.withHour(23).withMinute(59).withSecond(59).withNano(999999999);
                } else if (PERIOD_3_MONTHS.equalsIgnoreCase(dateRange)) {
                    rangeStartDate = now.minusMonths(3).withHour(0).withMinute(0).withSecond(0).withNano(0);
                    rangeEndDate = now.withHour(23).withMinute(59).withSecond(59).withNano(999999999);
                } else if (PERIOD_CUSTOM.equalsIgnoreCase(dateRange)) {
                    if (startDate == null || endDate == null) {
                        throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                                messageUtil.getMessage(MSG_DASHBOARD_ERROR_STARTDATE_REQUIRED, localeObj));
                    }
                    rangeStartDate = startDate.withHour(0).withMinute(0).withSecond(0).withNano(0);
                    rangeEndDate = endDate.withHour(23).withMinute(59).withSecond(59).withNano(999999999);
                } else {
                    // Default to all-time
                    rangeStartDate = null;
                    rangeEndDate = null;
                }
            } else if (startDate != null && endDate != null) {
                // Custom dates provided without dateRange parameter
                rangeStartDate = startDate.withHour(0).withMinute(0).withSecond(0).withNano(0);
                rangeEndDate = endDate.withHour(23).withMinute(59).withSecond(59).withNano(999999999);
            } else {
                // Default to all-time
                rangeStartDate = null;
                rangeEndDate = null;
            }

            // Use sentinel UUID and sentinel date for null values
            UUID sentinelUuid = SENTINEL_UUID;
            LocalDateTime sentinelDate = LocalDateTime.of(1970, 1, 1, 0, 0, 0);
            UUID restaurantIdParam = restaurantId != null ? restaurantId : sentinelUuid;
            UUID restaurantGroupIdParam = restaurantGroupId != null ? restaurantGroupId : sentinelUuid;
            LocalDateTime startDateParam = rangeStartDate != null ? rangeStartDate : sentinelDate;
            LocalDateTime endDateParam = rangeEndDate != null ? rangeEndDate : sentinelDate;

            // 1. Calculate total menu item counts
            long totalMenuItemCount;
            if (restaurantId != null) {
                totalMenuItemCount = itemRepository.countByRestaurantId(restaurantId);
            } else if (restaurantGroupId != null) {
                totalMenuItemCount = itemRepository.countByRestaurantGroupId(restaurantGroupId);
            } else {
                totalMenuItemCount = itemRepository.countByIsDeletedFalse();
            }

            // 2. Calculate published menu count (from Menu table with status = PUBLISHED)
            long publishedMenuCount;
            MenuStatus publishedStatus = MenuStatus.PUBLISHED;
            if (restaurantId != null) {
                // Count distinct published menus assigned to this restaurant
                publishedMenuCount = menuRepository.countPublishedMenusByRestaurantId(restaurantId, publishedStatus);
            } else if (restaurantGroupId != null) {
                // Count distinct published menus assigned to restaurants in this group
                publishedMenuCount = menuRepository.countPublishedMenusByRestaurantGroupId(restaurantGroupId, publishedStatus);
            } else {
                // Count all published menus from Menu table
                publishedMenuCount = menuRepository.countByStatusAndIsDeletedFalse(publishedStatus);
            }

            // 3. Calculate average order value
            BigDecimal avgOrderValue = BigDecimal.ZERO;
            BigDecimal totalSales;
            long completedTransactionCount;
            
            if (rangeStartDate != null && rangeEndDate != null) {
                OffsetDateTime rangeStartUtcPeak = rangeStartDate.atOffset(ZoneOffset.UTC);
                OffsetDateTime rangeEndUtcPeak = rangeEndDate.atOffset(ZoneOffset.UTC);
                if (restaurantId != null) {
                    totalSales = transactionRepository.sumTransactionAmountByRestaurantIdAndStatusAndDateRange(
                            restaurantId, TransactionStatus.COMPLETED, rangeStartUtcPeak, rangeEndUtcPeak);
                    completedTransactionCount = transactionRepository.countByRestaurantIdAndTransactionStatusAndDateRange(
                            restaurantId, TransactionStatus.COMPLETED, rangeStartUtcPeak, rangeEndUtcPeak);
                } else if (restaurantGroupId != null) {
                    totalSales = transactionRepository.sumTransactionAmountByRestaurantGroupIdAndStatusAndDateRange(
                            restaurantGroupId, TransactionStatus.COMPLETED, rangeStartUtcPeak, rangeEndUtcPeak);
                    completedTransactionCount = transactionRepository.countByRestaurantGroupIdAndTransactionStatusAndDateRange(
                            restaurantGroupId, TransactionStatus.COMPLETED, rangeStartUtcPeak, rangeEndUtcPeak);
                } else {
                    totalSales = transactionRepository.sumTransactionAmountByStatusAndDateRange(
                            TransactionStatus.COMPLETED, rangeStartUtcPeak, rangeEndUtcPeak);
                    completedTransactionCount = transactionRepository.countByTransactionStatusAndDateRange(
                            TransactionStatus.COMPLETED, rangeStartUtcPeak, rangeEndUtcPeak);
                }
            } else {
                if (restaurantId != null) {
                    totalSales = transactionRepository.sumTransactionAmountByRestaurantIdAndStatus(restaurantId, TransactionStatus.COMPLETED);
                    completedTransactionCount = transactionRepository.countByRestaurantIdAndTransactionStatus(restaurantId, TransactionStatus.COMPLETED);
                } else if (restaurantGroupId != null) {
                    totalSales = transactionRepository.sumTransactionAmountByRestaurantGroupIdAndStatus(restaurantGroupId, TransactionStatus.COMPLETED);
                    completedTransactionCount = transactionRepository.countByRestaurantGroupIdAndTransactionStatus(restaurantGroupId, TransactionStatus.COMPLETED);
                } else {
                    totalSales = transactionRepository.sumTransactionAmountByStatus(TransactionStatus.COMPLETED);
                    completedTransactionCount = transactionRepository.countByTransactionStatus(TransactionStatus.COMPLETED);
                }
            }
            
            if (totalSales == null) {
                totalSales = BigDecimal.ZERO;
            }
            
            if (completedTransactionCount > 0) {
                avgOrderValue = totalSales.divide(BigDecimal.valueOf(completedTransactionCount), 2, java.math.RoundingMode.HALF_UP);
            }

            // 4. Get best selling items (top 5)
            List<MenuDashboardResponse.BestSellingItem> bestSellingItems = getBestSellingItems(
                    restaurantIdParam, restaurantGroupIdParam, startDateParam, endDateParam, locale);

            // 5. Get top performing item (1 item)
            PerformingItem topPerformingItem = getTopPerformingItem(
                    restaurantIdParam, restaurantGroupIdParam, startDateParam, endDateParam, locale);

            // 6. Get least performing item (1 item)
            // Use sentinel UUID and sentinel date for null values
            UUID sentinelUuidForLeast = SENTINEL_UUID;
            LocalDateTime sentinelDateForLeast = LocalDateTime.of(1970, 1, 1, 0, 0, 0);
            UUID restaurantIdForLeast = restaurantId != null ? restaurantId : sentinelUuidForLeast;
            UUID restaurantGroupIdForLeast = restaurantGroupId != null ? restaurantGroupId : sentinelUuidForLeast;
            LocalDateTime startDateForLeast = rangeStartDate != null ? rangeStartDate : sentinelDateForLeast;
            LocalDateTime endDateForLeast = rangeEndDate != null ? rangeEndDate : sentinelDateForLeast;
            PerformingItem leastPerformingItem = getLeastPerformingItem(
                    restaurantIdForLeast, restaurantGroupIdForLeast, startDateForLeast, endDateForLeast, locale);

            MenuDashboardResponse.ItemPerformance itemPerformance = MenuDashboardResponse.ItemPerformance.builder()
                    .topPerformingItem(topPerformingItem)
                    .leastPerformingItem(leastPerformingItem)
                    .build();

            String currency = restaurantChainConfigProperties.getChain() != null ? restaurantChainConfigProperties.getChain().getCurrency() : null;
            MenuDashboardResponse response = MenuDashboardResponse.builder()
                    .totalMenuItemCount(totalMenuItemCount)
                    .publishedMenuCount(publishedMenuCount)
                    .avgOrderValue(avgOrderValue != null ? CurrencyFormatter.formatAmount(avgOrderValue, currency) : null)
                    .bestSellingItems(bestSellingItems)
                    .itemPerformance(itemPerformance)
                    .build();

            return ResponseDto.<MenuDashboardResponse>builder()
                    .data(response)
                    .message(messageUtil.getMessage("menu.dashboard.retrieved.success", localeObj))
                    .build();

        } catch (ResponseStatusException e) {
            throw e;
        } catch (Exception e) {
            logger.error("Error fetching menu dashboard", e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    messageUtil.getMessage("menu.dashboard.error.general", localeObj));
        }
    }

    /**
     * Computes best-selling items (top 5) for the given scope and date window.
     * <p>
     * Uses aggregated query results and resolves each item's "main category" via the LIVE menu mapping when possible.
     * Batch-fetches item and category translations to produce localized display names.
     * </p>
     *
     * @param restaurantId      restaurant scope (may be sentinel UUID depending on caller)
     * @param restaurantGroupId group scope (may be sentinel UUID depending on caller)
     * @param startDate         range start (may be sentinel date depending on caller)
     * @param endDate           range end (may be sentinel date depending on caller)
     * @param locale            locale tag used for translation selection
     * @return list of best-selling item summaries (empty on error)
     */
    private List<MenuDashboardResponse.BestSellingItem> getBestSellingItems(UUID restaurantId, UUID restaurantGroupId, 
                                                                             LocalDateTime startDate, LocalDateTime endDate, String locale) {
        try {
            List<Object[]> topItemsData = orderedItemRepository.findTop5ItemsByOrderCountWithDateRange(
                    restaurantId, restaurantGroupId, startDate, endDate);

            if (topItemsData == null || topItemsData.isEmpty()) {
                return new ArrayList<>();
            }

            // Collect all item IDs and category IDs for batch fetching
            List<UUID> itemIds = new ArrayList<>();
            List<UUID> categoryIds = new ArrayList<>();
            List<Object[]> validRows = new ArrayList<>();

            for (Object[] row : topItemsData) {
                if (row.length >= 4 && row[0] != null) {
                    UUID itemId = (UUID) row[0];
                    UUID categoryId = row[1] != null ? (UUID) row[1] : null;
                    itemIds.add(itemId);
                    if (categoryId != null) {
                        categoryIds.add(categoryId);
                    }
                    validRows.add(row);
                }
            }

            if (itemIds.isEmpty()) {
                return new ArrayList<>();
            }

            // Resolve "main category" (parent category if subcategory) for each item based on the restaurant's LIVE menu.
            // This avoids returning an arbitrary subcategory / wrong menu category mapping.
            Map<UUID, UUID> itemToMainCategoryId = new java.util.HashMap<>();
            resolveBestSellingItemsMainCategoriesBestEffort(restaurantId, restaurantGroupId, itemIds, itemToMainCategoryId);

            // Batch fetch items
            List<Item> items = itemRepository.findAllById(itemIds);
            Map<UUID, Item> itemMap = items.stream()
                    .collect(Collectors.toMap(Item::getId, item -> item));

            // Batch fetch item translations
            Map<UUID, List<ItemTranslation>> itemTranslationsMap = 
                    itemTranslationRepository.findAllByItemIdIn(itemIds)
                            .stream()
                            .collect(Collectors.groupingBy(t -> t.getItem().getId()));

            // Collect main category IDs (from resolved mapping or from query)
            Set<UUID> finalCategoryIds = new java.util.HashSet<>();
            for (Object[] row : validRows) {
                UUID itemId = (UUID) row[0];
                UUID categoryId = row[1] != null ? (UUID) row[1] : null;
                // Prefer main category id resolved from restaurant's LIVE menu mapping (if available)
                if (itemId != null && itemToMainCategoryId.containsKey(itemId)) {
                    finalCategoryIds.add(itemToMainCategoryId.get(itemId));
                } else if (categoryId != null) {
                    finalCategoryIds.add(categoryId);
                }
            }

            // Batch fetch category translations (all translations, not just for specific locale)
            Map<UUID, List<CategoryTranslation>> categoryTranslationsMap = finalCategoryIds.isEmpty()
                    ? Collections.emptyMap()
                    : categoryTranslationRepository.findAllByCategoryIdIn(new ArrayList<>(finalCategoryIds))
                            .stream()
                            .collect(Collectors.groupingBy(t -> t.getCategory().getId()));

            // Build response
            List<MenuDashboardResponse.BestSellingItem> result = new ArrayList<>();
            String currency = restaurantChainConfigProperties.getChain() != null ? restaurantChainConfigProperties.getChain().getCurrency() : null;
            for (Object[] row : validRows) {
                MenuDashboardResponse.BestSellingItem item =
                        buildBestSellingItemBestEffort(row, itemToMainCategoryId, itemTranslationsMap, categoryTranslationsMap, itemMap, locale, currency);
                if (item != null) {
                    result.add(item);
                }
            }

            return result;
        } catch (Exception e) {
            logger.warn("Error fetching best selling items: {}", e.getMessage());
            return new ArrayList<>();
        }
    }

    private MenuDashboardResponse.BestSellingItem buildBestSellingItemBestEffort(
            Object[] row,
            Map<UUID, UUID> itemToMainCategoryId,
            Map<UUID, List<ItemTranslation>> itemTranslationsMap,
            Map<UUID, List<CategoryTranslation>> categoryTranslationsMap,
            Map<UUID, Item> itemMap,
            String locale,
            String currency) {
        try {
            if (row == null || row.length < 4 || row[0] == null) {
                return null;
            }
            UUID itemId = (UUID) row[0];
            UUID categoryId = row[1] != null ? (UUID) row[1] : null;
            Long totalQuantity = row[2] != null ? ((Number) row[2]).longValue() : 0L;
            BigDecimal revenue = row[3] != null ? (BigDecimal) row[3] : BigDecimal.ZERO;

            if (itemId != null && itemToMainCategoryId != null && itemToMainCategoryId.containsKey(itemId)) {
                categoryId = itemToMainCategoryId.get(itemId);
            }

            String itemName = resolveItemName(itemId, itemTranslationsMap, locale);
            String categoryName = resolveCategoryName(categoryId, categoryTranslationsMap, locale);

            Item item = itemMap != null ? itemMap.get(itemId) : null;
            String imageUrl = item != null ? awsService.getFullUrl(item.getImageUrl()) : null;

            return MenuDashboardResponse.BestSellingItem.builder()
                    .itemCode(item != null ? item.getItemCode() : null)
                    .itemName(itemName)
                    .categoryName(categoryName)
                    .imageUrl(imageUrl)
                    .quantitySold(totalQuantity)
                    .revenue(revenue != null ? CurrencyFormatter.formatAmount(revenue, currency) : CurrencyFormatter.formatAmount(BigDecimal.ZERO, currency))
                    .build();
        } catch (Exception e) {
            logger.warn("Error building best selling item, skipping. Error: {}", e.getMessage());
            return null;
        }
    }

    private String resolveItemName(UUID itemId, Map<UUID, List<ItemTranslation>> itemTranslationsMap, String locale) {
        if (itemId == null || itemTranslationsMap == null) {
            return "";
        }
        List<ItemTranslation> itemTranslations = itemTranslationsMap.getOrDefault(itemId, Collections.emptyList());
        if (itemTranslations.isEmpty()) {
            return "";
        }
        ItemTranslation exactMatch = itemTranslations.stream()
                .filter(t -> t.getLanguageCode() != null && t.getLanguageCode().equalsIgnoreCase(locale))
                .findFirst()
                .orElse(null);
        if (exactMatch != null) {
            return exactMatch.getName() != null ? exactMatch.getName() : "";
        }
        Optional<ItemTranslation> translation =
                TranslationUtils.pickPreferredOrFromList(
                        itemTranslations, locale, localizationProperties.getLanguages(),
                        ItemTranslation::getLanguageCode);
        return translation.map(t -> t.getName() != null ? t.getName() : "").orElse("");
    }

    private String resolveCategoryName(UUID categoryId, Map<UUID, List<CategoryTranslation>> categoryTranslationsMap, String locale) {
        if (categoryId == null || categoryTranslationsMap == null) {
            return "";
        }
        List<CategoryTranslation> categoryTranslations = categoryTranslationsMap.getOrDefault(categoryId, Collections.emptyList());
        if (categoryTranslations.isEmpty()) {
            return "";
        }
        CategoryTranslation exactMatch = categoryTranslations.stream()
                .filter(t -> t.getLanguageCode() != null && t.getLanguageCode().equalsIgnoreCase(locale))
                .findFirst()
                .orElse(null);
        if (exactMatch != null) {
            return exactMatch.getName() != null ? exactMatch.getName() : "";
        }
        Optional<CategoryTranslation> translation =
                TranslationUtils.pickPreferredOrFromList(
                        categoryTranslations, locale, localizationProperties.getLanguages(),
                        CategoryTranslation::getLanguageCode);
        return translation.map(t -> t.getName() != null ? t.getName() : "").orElse("");
    }

    /**
     * Returns the top-performing item (by order count) for the given scope and time window.
     * <p>
     * Resolves the "main category" via the LIVE menu mapping where possible and builds a localized response with
     * image URL, formatted revenue, and base price.
     * </p>
     *
     * @param restaurantId      restaurant scope (may be sentinel UUID depending on caller)
     * @param restaurantGroupId group scope (may be sentinel UUID depending on caller)
     * @param startDate         range start
     * @param endDate           range end
     * @param locale            locale tag used for translation selection
     * @return top-performing item or {@code null} when no data exists
     */
    private PerformingItem getTopPerformingItem(UUID restaurantId, UUID restaurantGroupId,
                                                                                               LocalDateTime startDate, LocalDateTime endDate, String locale) {
        try {
            List<Object[]> topItemsData = orderedItemRepository.findTop1ItemsByOrderCount(
                    restaurantId, restaurantGroupId, startDate, endDate);

            if (topItemsData == null || topItemsData.isEmpty()) {
                return null;
            }

            Object[] row = topItemsData.get(0);
            if (row.length < 4 || row[0] == null) {
                return null;
            }

            UUID itemId = (UUID) row[0];
            UUID categoryId = row[1] != null ? (UUID) row[1] : null;
            Long totalQuantity = row[2] != null ? ((Number) row[2]).longValue() : 0L;
            BigDecimal revenue = row[3] != null ? (BigDecimal) row[3] : BigDecimal.ZERO;

            // Resolve "main category" (parent category if subcategory) based on the restaurant's LIVE menu.
            UUID sentinelUuid = SENTINEL_UUID;
            if (restaurantId != null && !restaurantId.equals(sentinelUuid) && itemId != null) {
                UUID resolvedCategoryId = resolveMainCategoryViaLiveMenu(restaurantId, itemId);
                if (resolvedCategoryId != null) {
                    categoryId = resolvedCategoryId;
                }
            } else if (restaurantGroupId != null && !restaurantGroupId.equals(sentinelUuid) && itemId != null) {
                // When only restaurantGroupId is provided, check all restaurants in the group
                UUID resolvedCategoryId = resolveMainCategoryForGroupBestEffort(restaurantGroupId, itemId);
                if (resolvedCategoryId != null) {
                    categoryId = resolvedCategoryId;
                }
            } else if (itemId != null) {
                // When neither restaurantId nor restaurantGroupId is provided, find item in LIVE menus
                try {
                    UUID resolvedCategoryId = resolveCategoryViaAllMappings(itemId);
                    if (resolvedCategoryId != null) {
                        categoryId = resolvedCategoryId;
                    }
                } catch (Exception e) {
                    logger.warn("Error resolving main category for top performing item (no restaurant filter), falling back to query category. Error: {}", e.getMessage());
                }
            }

            // Fetch item details
            Optional<Item> itemOpt = itemRepository.findById(itemId);
            String itemName = "Unknown Item";
            String imageUrl = null;
            BigDecimal price = BigDecimal.ZERO;

            if (itemOpt.isPresent()) {
                Item item = itemOpt.get();
                imageUrl = awsService.getFullUrl(item.getImageUrl());
                if (item.getBasePrice() != null) {
                    price = BigDecimal.valueOf(item.getBasePrice());
                }

                // Get item translation
                List<ItemTranslation> itemTranslations = itemTranslationRepository.findAllByItemId(itemId);
                if (!itemTranslations.isEmpty()) {
                    // Try exact match first - use locale string directly
                    ItemTranslation exactMatch = itemTranslations.stream()
                            .filter(t -> t.getLanguageCode() != null && t.getLanguageCode().equalsIgnoreCase(locale))
                            .findFirst()
                            .orElse(null);
                    
                    if (exactMatch != null) {
                        itemName = exactMatch.getName() != null ? exactMatch.getName() : "";
                    } else {
                        // Fallback using TranslationUtils
                        Optional<ItemTranslation> translation = TranslationUtils.pickPreferredOrFromList(
                                itemTranslations,
                                locale,
                                localizationProperties.getLanguages(),
                                ItemTranslation::getLanguageCode
                        );
                        
                        if (translation.isPresent()) {
                            itemName = translation.get().getName() != null ? translation.get().getName() : "";
                        } else if (!itemTranslations.isEmpty()) {
                            // Last resort: first available translation
                            itemName = itemTranslations.get(0).getName() != null ? itemTranslations.get(0).getName() : "";
                        }
                    }
                }
            }

            // Get category name from translations
            String categoryName = "";
            if (categoryId != null) {
                List<CategoryTranslation> categoryTranslations = categoryTranslationRepository.findByCategoryId(categoryId);
                if (!categoryTranslations.isEmpty()) {
                    // Try exact match first - use locale string directly
                    CategoryTranslation exactMatch = categoryTranslations.stream()
                            .filter(t -> t.getLanguageCode() != null && t.getLanguageCode().equalsIgnoreCase(locale))
                            .findFirst()
                            .orElse(null);
                    
                    if (exactMatch != null) {
                        categoryName = exactMatch.getName() != null ? exactMatch.getName() : "";
                    } else {
                        // Fallback using TranslationUtils
                        Optional<CategoryTranslation> categoryTranslation = TranslationUtils.pickPreferredOrFromList(
                                categoryTranslations,
                                locale,
                                localizationProperties.getLanguages(),
                                CategoryTranslation::getLanguageCode
                        );
                        
                        if (categoryTranslation.isPresent()) {
                            categoryName = categoryTranslation.get().getName() != null ? categoryTranslation.get().getName() : "";
                        } else if (!categoryTranslations.isEmpty()) {
                            // Last resort: first available translation
                            categoryName = categoryTranslations.get(0).getName() != null ? categoryTranslations.get(0).getName() : "";
                        }
                    }
                }
            }

            String currency = restaurantChainConfigProperties.getChain() != null ? restaurantChainConfigProperties.getChain().getCurrency() : null;
            return PerformingItem.builder()
                    .itemCode(itemOpt.map(Item::getItemCode).orElse(null))
                    .itemName(itemName)
                    .categoryName(categoryName)
                    .imageUrl(imageUrl)
                    .totalSold(totalQuantity)
                    .revenue(revenue != null ? CurrencyFormatter.formatAmount(revenue, currency) : CurrencyFormatter.formatAmount(BigDecimal.ZERO, currency))
                    .price(price != null ? CurrencyFormatter.formatAmount(price, currency) : null)
                    .build();

        } catch (Exception e) {
            logger.warn("Error fetching top performing item: {}", e.getMessage());
            return null;
        }
    }

    private UUID resolveMainCategoryForGroupBestEffort(UUID restaurantGroupId, UUID itemId) {
        try {
            List<Restaurant> restaurants = restaurantRepository.findByRestaurantGroupIdAndIsDeletedFalse(restaurantGroupId);
            for (Restaurant restaurant : restaurants) {
                UUID restaurantId = restaurant != null ? restaurant.getId() : null;
                if (restaurantId == null) {
                    continue;
                }
                UUID resolvedCategoryId = resolveMainCategoryViaLiveMenu(restaurantId, itemId);
                if (resolvedCategoryId != null) {
                    return resolvedCategoryId;
                }
            }
        } catch (Exception e) {
            logger.warn("Error resolving main category for top performing item (restaurantGroupId), falling back to query category. Error: {}", e.getMessage());
        }
        return null;
    }

    /**
     * Exports the dashboard statistics report to CSV and streams it to the HTTP response.
     * <p>
     * Delegates to {@link #getDashboardStatistics(String, LocalDateTime, LocalDateTime, UUID, UUID, String, String)}
     * to build the data, then writes a multi-section CSV (summary + breakdowns) with UTF-8 BOM for Excel compatibility.
     * </p>
     *
     * @param period            optional period identifier
     * @param startDate         optional custom start date-time (UTC)
     * @param endDate           optional custom end date-time (UTC)
     * @param restaurantGroupId optional group scope
     * @param restaurantId      optional restaurant scope (overrides group scope)
     * @param salesStatsPeriod  optional secondary period selector for sales sub-statistics
     * @param locale            locale tag used for messages
     * @param response          servlet response to write the CSV to
     * @throws IOException if writing to the servlet response fails
     * @throws ResponseStatusException when the report data cannot be produced
     */
    @Override
    public void exportDashboardStatisticsToCsv(String period, LocalDateTime startDate, LocalDateTime endDate, 
                                                UUID restaurantGroupId, UUID restaurantId, String salesStatsPeriod, 
                                                String locale, HttpServletResponse response) throws IOException {
        // Set locale context for message localization
        Locale localeObj = Locale.forLanguageTag(locale != null ? locale : "en");
        LocaleContextHolder.setLocale(localeObj);
        
        try {
            logger.info("Exporting dashboard statistics to CSV with period: {}, startDate: {}, endDate: {}, restaurantGroupId: {}, restaurantId: {}, salesStatsPeriod: {}, locale: {}", 
                    period, startDate, endDate, restaurantGroupId, restaurantId, salesStatsPeriod, locale);

            // Get dashboard statistics
            ResponseDto<DashboardResponse> dashboardResponse = getDashboardStatistics(
                    period, startDate, endDate, restaurantGroupId, restaurantId, salesStatsPeriod, locale);
            
            DashboardResponse data = dashboardResponse.getData();
            if (data == null) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                        messageUtil.getMessage("dashboard.statistics.error.notfound", localeObj));
            }

            // Generate filename with timestamp
            String timestamp = LocalDateTime.now(ZoneOffset.UTC).format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
            String filename = "dashboard_statistics_" + timestamp + ".csv";

            // Set response headers BEFORE getting output stream
            response.setContentType("text/csv;charset=UTF-8");
            response.setCharacterEncoding("UTF-8");
            response.setHeader("Content-Disposition", "attachment; filename=\"" + filename + "\"");

            // Get output stream and write BOM for Excel compatibility
            java.io.OutputStream outputStream = response.getOutputStream();
            // Write UTF-8 BOM (0xEF 0xBB 0xBF)
            outputStream.write(0xEF);
            outputStream.write(0xBB);
            outputStream.write(0xBF);

            // Create CSV printer using the same output stream
            try (CSVPrinter csvPrinter = new CSVPrinter(
                    new OutputStreamWriter(outputStream, StandardCharsets.UTF_8),
                    CSVFormat.DEFAULT)) {

                // Localized labels/headers (driven by `locale` header)
                final String headerMetric = messageUtil.getMessage("csv.dashboard.header.metric", localeObj);
                final String headerValue = messageUtil.getMessage("csv.dashboard.header.value", localeObj);
                final String headerStatus = messageUtil.getMessage("csv.dashboard.header.status", localeObj);
                final String headerCount = messageUtil.getMessage("csv.dashboard.header.count", localeObj);
                final String headerPercentage = messageUtil.getMessage("csv.dashboard.header.percentage", localeObj);

                // Write Summary Section
                csvPrinter.printRecord(messageUtil.getMessage("csv.dashboard.export.title", localeObj));
                csvPrinter.printRecord(
                        messageUtil.getMessage("csv.export.date", localeObj),
                        LocalDateTime.now(ZoneOffset.UTC).format(DateTimeFormatter.ofPattern(DATE_TIME_FORMAT_PATTERN)));
                csvPrinter.printRecord();
                
                // Write Summary Data
                csvPrinter.printRecord(messageUtil.getMessage("csv.dashboard.section.summary", localeObj));
                csvPrinter.printRecord(headerMetric, headerValue);
                csvPrinter.printRecord(messageUtil.getMessage("csv.dashboard.metric.total.restaurants", localeObj), data.getTotalRestaurants());
                csvPrinter.printRecord(messageUtil.getMessage("csv.dashboard.metric.total.orders", localeObj), data.getTotalOrders());
                csvPrinter.printRecord(messageUtil.getMessage("csv.dashboard.metric.total.sales", localeObj), data.getTotalSales() != null ? data.getTotalSales().toString() : "0.00");
                csvPrinter.printRecord(messageUtil.getMessage("csv.dashboard.metric.active.employees", localeObj), data.getActiveEmployeesCount());
                csvPrinter.printRecord(messageUtil.getMessage("csv.dashboard.metric.total.items", localeObj), data.getTotalItems());
                csvPrinter.printRecord(messageUtil.getMessage("csv.dashboard.metric.active.discounts", localeObj), data.getActiveDiscountsCount());
                csvPrinter.printRecord(messageUtil.getMessage("csv.dashboard.metric.active.promotions", localeObj), data.getActivePromotionsCount());
                csvPrinter.printRecord();

                // Write Order Status Breakdown
                if (data.getOrderStatusCounts() != null) {
                    csvPrinter.printRecord(messageUtil.getMessage("csv.dashboard.section.order.status.breakdown", localeObj));
                    csvPrinter.printRecord(headerStatus, headerCount, headerPercentage);
                    OrderStatusCounts orderStatus = data.getOrderStatusCounts();
                    csvPrinter.printRecord(messageUtil.getMessage("csv.dashboard.orderstatus.open", localeObj),
                            orderStatus.getOpenOrdersCount(),
                            orderStatus.getOpenOrdersPercentage() != null ? 
                                    orderStatus.getOpenOrdersPercentage().toString() + "%" : DEFAULT_PERCENTAGE);
                    csvPrinter.printRecord(messageUtil.getMessage("csv.dashboard.orderstatus.completed", localeObj),
                            orderStatus.getCompletedOrdersCount(),
                            orderStatus.getCompletedOrdersPercentage() != null ? 
                                    orderStatus.getCompletedOrdersPercentage().toString() + "%" : DEFAULT_PERCENTAGE);
                    csvPrinter.printRecord(messageUtil.getMessage("csv.dashboard.orderstatus.pending", localeObj),
                            orderStatus.getPendingOrdersCount(),
                            orderStatus.getPendingOrdersPercentage() != null ? 
                                    orderStatus.getPendingOrdersPercentage().toString() + "%" : DEFAULT_PERCENTAGE);
                    csvPrinter.printRecord(messageUtil.getMessage("csv.dashboard.orderstatus.cancelled", localeObj),
                            orderStatus.getCancelledOrdersCount(),
                            orderStatus.getCancelledOrdersPercentage() != null ? 
                                    orderStatus.getCancelledOrdersPercentage().toString() + "%" : DEFAULT_PERCENTAGE);
                    csvPrinter.printRecord(messageUtil.getMessage("csv.dashboard.orderstatus.refunded", localeObj),
                            orderStatus.getRefundedOrdersCount(),
                            orderStatus.getRefundedOrdersPercentage() != null ? 
                                    orderStatus.getRefundedOrdersPercentage().toString() + "%" : DEFAULT_PERCENTAGE);
                    csvPrinter.printRecord();
                }

                // Write Employee Role Counts
                if (data.getEmployeeRoleCounts() != null && data.getEmployeeRoleCounts().getRoleCounts() != null) {
                    csvPrinter.printRecord(messageUtil.getMessage("csv.dashboard.section.employee.role.counts", localeObj));
                    csvPrinter.printRecord(
                            messageUtil.getMessage("csv.dashboard.header.role", localeObj),
                            headerCount);
                    for (EmployeeRoleCount roleCount : data.getEmployeeRoleCounts().getRoleCounts()) {
                        csvPrinter.printRecord(roleCount.getRoleName(), roleCount.getCount());
                    }
                    csvPrinter.printRecord();
                }

                // Write Menu Performance (Top Items)
                if (data.getMenuPerformance() != null && data.getMenuPerformance().getTopItems() != null) {
                    csvPrinter.printRecord(messageUtil.getMessage("csv.dashboard.section.top.items", localeObj));
                    csvPrinter.printRecord(
                            messageUtil.getMessage("csv.dashboard.header.item.name", localeObj),
                            messageUtil.getMessage("csv.dashboard.header.item.code", localeObj),
                            messageUtil.getMessage("csv.dashboard.header.category", localeObj),
                            messageUtil.getMessage("csv.dashboard.header.order.count", localeObj),
                            messageUtil.getMessage("csv.dashboard.header.revenue", localeObj));
                    for (TopItemPerformance item : data.getMenuPerformance().getTopItems()) {
                        csvPrinter.printRecord(
                                item.getItemName() != null ? item.getItemName() : "",
                                item.getItemCode() != null ? item.getItemCode() : "",
                                item.getCategoryName() != null ? item.getCategoryName() : "",
                                item.getOrderCount() != null ? item.getOrderCount() : 0,
                                item.getRevenue() != null ? item.getRevenue().toString() : "0.00"
                        );
                    }
                    csvPrinter.printRecord();
                }

                // Write Sales Statistics
                if (data.getSalesStats() != null && data.getSalesStats().getDataPoints() != null) {
                    csvPrinter.printRecord(messageUtil.getMessage("csv.dashboard.section.sales.statistics.prefix", localeObj) + " " +
                            (data.getSalesStats().getPeriod() != null ? data.getSalesStats().getPeriod() : "N/A"));
                    csvPrinter.printRecord(
                            messageUtil.getMessage("csv.dashboard.header.date", localeObj),
                            messageUtil.getMessage("csv.dashboard.metric.total.sales", localeObj),
                            messageUtil.getMessage("csv.dashboard.header.order.count", localeObj));
                    for (SalesDataPoint dataPoint : data.getSalesStats().getDataPoints()) {
                        csvPrinter.printRecord(
                                dataPoint.getDate() != null ? dataPoint.getDate().toString() : "",
                                dataPoint.getTotalSales() != null ? dataPoint.getTotalSales().toString() : "0.00",
                                dataPoint.getOrderCount() != null ? dataPoint.getOrderCount() : 0
                        );
                    }
                    csvPrinter.printRecord();
                }

                // Write Promotion Statistics
                if (data.getPromotionStats() != null) {
                    csvPrinter.printRecord(messageUtil.getMessage("csv.dashboard.section.promotion.statistics", localeObj));
                    csvPrinter.printRecord(messageUtil.getMessage("csv.dashboard.metric.active.promotions.count", localeObj),
                            data.getPromotionStats().getActivePromotionsCount() != null ? 
                                    data.getPromotionStats().getActivePromotionsCount() : 0);
                    csvPrinter.printRecord(messageUtil.getMessage("csv.dashboard.metric.upcoming.promotions.count", localeObj),
                            data.getPromotionStats().getUpcomingPromotionsCount() != null ? 
                                    data.getPromotionStats().getUpcomingPromotionsCount() : 0);
                    csvPrinter.printRecord();
                    
                    if (data.getPromotionStats().getActivePromotions() != null && !data.getPromotionStats().getActivePromotions().isEmpty()) {
                        csvPrinter.printRecord(messageUtil.getMessage("csv.dashboard.section.active.promotions", localeObj));
                        csvPrinter.printRecord(
                                messageUtil.getMessage("csv.dashboard.header.promotion.name", localeObj),
                                messageUtil.getMessage("csv.dashboard.header.description", localeObj),
                                headerStatus,
                                messageUtil.getMessage("csv.dashboard.header.valid.from", localeObj),
                                messageUtil.getMessage("csv.dashboard.header.valid.to", localeObj),
                                messageUtil.getMessage("csv.dashboard.header.menu.name", localeObj));
                        for (PromotionDetail promotion : data.getPromotionStats().getActivePromotions()) {
                            csvPrinter.printRecord(
                                    promotion.getPromotionName() != null ? promotion.getPromotionName() : "",
                                    promotion.getDescription() != null ? promotion.getDescription() : "",
                                    promotion.getStatus() != null ? promotion.getStatus() : "",
                                    promotion.getValidFrom() != null ? promotion.getValidFrom().toString() : "",
                                    promotion.getValidTo() != null ? promotion.getValidTo().toString() : "",
                                    promotion.getMenuName() != null ? promotion.getMenuName() : ""
                            );
                        }
                        csvPrinter.printRecord();
                    }
                    
                    if (data.getPromotionStats().getUpcomingPromotions() != null && !data.getPromotionStats().getUpcomingPromotions().isEmpty()) {
                        csvPrinter.printRecord(messageUtil.getMessage("csv.dashboard.section.upcoming.promotions", localeObj));
                        csvPrinter.printRecord(
                                messageUtil.getMessage("csv.dashboard.header.promotion.name", localeObj),
                                messageUtil.getMessage("csv.dashboard.header.description", localeObj),
                                headerStatus,
                                messageUtil.getMessage("csv.dashboard.header.valid.from", localeObj),
                                messageUtil.getMessage("csv.dashboard.header.valid.to", localeObj),
                                messageUtil.getMessage("csv.dashboard.header.menu.name", localeObj));
                        for (PromotionDetail promotion : data.getPromotionStats().getUpcomingPromotions()) {
                            csvPrinter.printRecord(
                                    promotion.getPromotionName() != null ? promotion.getPromotionName() : "",
                                    promotion.getDescription() != null ? promotion.getDescription() : "",
                                    promotion.getStatus() != null ? promotion.getStatus() : "",
                                    promotion.getValidFrom() != null ? promotion.getValidFrom().toString() : "",
                                    promotion.getValidTo() != null ? promotion.getValidTo().toString() : "",
                                    promotion.getMenuName() != null ? promotion.getMenuName() : ""
                            );
                        }
                        csvPrinter.printRecord();
                    }
                }

                // Write Discount Statistics
                if (data.getDiscountStats() != null) {
                    csvPrinter.printRecord(messageUtil.getMessage("csv.dashboard.section.discount.statistics", localeObj));
                    csvPrinter.printRecord(headerMetric, headerValue);
                    csvPrinter.printRecord(messageUtil.getMessage("csv.dashboard.metric.total.active.discounts", localeObj),
                            data.getDiscountStats().getTotalActiveDiscounts() != null ? 
                                    data.getDiscountStats().getTotalActiveDiscounts() : 0);
                    csvPrinter.printRecord(messageUtil.getMessage("csv.dashboard.metric.total.usage", localeObj),
                            data.getDiscountStats().getTotalUsage() != null ? 
                                    data.getDiscountStats().getTotalUsage() : 0);
                    csvPrinter.printRecord(messageUtil.getMessage("csv.dashboard.metric.total.revenue.impact", localeObj),
                            data.getDiscountStats().getTotalRevenueImpact() != null ? 
                                    data.getDiscountStats().getTotalRevenueImpact().toString() : "0.00");
                    
                    csvPrinter.printRecord();
                    csvPrinter.printRecord(messageUtil.getMessage("csv.dashboard.section.discount.type.breakdown", localeObj));
                    csvPrinter.printRecord(
                            messageUtil.getMessage("csv.dashboard.header.discount.type", localeObj),
                            messageUtil.getMessage("csv.dashboard.header.active.count", localeObj),
                            messageUtil.getMessage("csv.dashboard.header.usage.count", localeObj),
                            messageUtil.getMessage("csv.dashboard.header.revenue.impact", localeObj));
                    
                    if (data.getDiscountStats().getOrderDiscounts() != null) {
                        DiscountTypeStats orderDiscounts = data.getDiscountStats().getOrderDiscounts();
                        csvPrinter.printRecord(messageUtil.getMessage("csv.dashboard.discount.order", localeObj),
                                orderDiscounts.getActiveCount() != null ? orderDiscounts.getActiveCount() : 0,
                                orderDiscounts.getUsageCount() != null ? orderDiscounts.getUsageCount() : 0,
                                orderDiscounts.getRevenueImpact() != null ? orderDiscounts.getRevenueImpact().toString() : "0.00");
                    }
                    
                    if (data.getDiscountStats().getItemDiscounts() != null) {
                        DiscountTypeStats itemDiscounts = data.getDiscountStats().getItemDiscounts();
                        csvPrinter.printRecord(messageUtil.getMessage("csv.dashboard.discount.item", localeObj),
                                itemDiscounts.getActiveCount() != null ? itemDiscounts.getActiveCount() : 0,
                                itemDiscounts.getUsageCount() != null ? itemDiscounts.getUsageCount() : 0,
                                itemDiscounts.getRevenueImpact() != null ? itemDiscounts.getRevenueImpact().toString() : "0.00");
                    }
                    
                    if (data.getDiscountStats().getCategoryDiscounts() != null) {
                        DiscountTypeStats categoryDiscounts = data.getDiscountStats().getCategoryDiscounts();
                        csvPrinter.printRecord(messageUtil.getMessage("csv.dashboard.discount.category", localeObj),
                                categoryDiscounts.getActiveCount() != null ? categoryDiscounts.getActiveCount() : 0,
                                categoryDiscounts.getUsageCount() != null ? categoryDiscounts.getUsageCount() : 0,
                                categoryDiscounts.getRevenueImpact() != null ? categoryDiscounts.getRevenueImpact().toString() : "0.00");
                    }
                }

                csvPrinter.flush();
            }
            
            // Ensure output stream is flushed
            outputStream.flush();

            logger.info("Dashboard statistics exported to CSV successfully");

        } catch (ResponseStatusException e) {
            // Re-throw ResponseStatusException to preserve HTTP status codes
            throw e;
        } catch (Exception e) {
            logger.error("Error exporting dashboard statistics to CSV", e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    messageUtil.getMessage("dashboard.export.error.general", localeObj));
        }
    }

    // ========== Inner Class: Dashboard Statistics Report Data Provider ==========
    // This implements ReportDataProvider interface for CSV export using shared library
    // Note: Dashboard statistics is complex with multiple sections, so this is a simplified version

    @org.springframework.stereotype.Component("dashboardStatisticsReportDataProvider")
    public class DashboardStatisticsReportDataProvider implements
            com.gulfnet.shared_library.service.export.ReportDataProvider<DashboardResponse>,
            com.gulfnet.shared_library.service.export.ReportTypeProvider {

        @Override
        public long getTotalCount(Map<String, Object> filters) {
            // Dashboard statistics is a single summary report, always returns 1
            return 1;
        }

        /**
         * Fetches a single "page" of dashboard statistics for the shared export engine.
         * <p>
         * Dashboard statistics are a single summary report, so only {@code page=0} returns data; other pages return empty.
         * </p>
         *
         * @param page     zero-based page index
         * @param pageSize requested page size (ignored; report is single-row)
         * @param filters  filter map containing period/dates/restaurant scoping values
         * @return singleton list containing {@link DashboardResponse} for page 0, otherwise empty
         */
        @Override
        public List<DashboardResponse> fetchPage(int page, int pageSize, Map<String, Object> filters) {
            if (page > 0) {
                return Collections.emptyList(); // Only one page of data
            }
            
            String period = (String) filters.get(FILTER_PERIOD);
            LocalDateTime startDate = (LocalDateTime) filters.get(FILTER_START_DATE);
            LocalDateTime endDate = (LocalDateTime) filters.get(FILTER_END_DATE);
            UUID restaurantGroupId = (UUID) filters.get(FILTER_RESTAURANT_GROUP_ID);
            UUID restaurantId = (UUID) filters.get(FILTER_RESTAURANT_ID);
            String salesStatsPeriod = (String) filters.get("salesStatsPeriod");
            String locale = (String) filters.getOrDefault("locale", "en");
            
            ResponseDto<DashboardResponse> response = DashboardServiceImpl.this.getDashboardStatistics(
                    period, startDate, endDate, restaurantGroupId, restaurantId, salesStatsPeriod, locale);
            
            if (response.getData() != null) {
                return Collections.singletonList(response.getData());
            }
            return Collections.emptyList();
        }

        @Override
        public String[] getColumnHeaders(String locale) {
            return new String[]{
                    CSV_HEADER_METRIC,
                    CSV_HEADER_VALUE,
                    "Details"
            };
        }

        /**
         * Converts the dashboard response into a flattened CSV row representation for the shared export engine.
         * <p>
         * This provider uses a simplified single-row format; detailed multi-section export is handled by
         * {@link #exportDashboardStatisticsToCsv(String, LocalDateTime, LocalDateTime, UUID, UUID, String, String, HttpServletResponse)}.
         * </p>
         *
         * @param data   dashboard response
         * @param locale locale tag (not used for this simplified row)
         * @return a single CSV row as string array
         */
        @Override
        public String[] convertToRow(DashboardResponse data, String locale) {
            // This is a simplified row representation
            // In practice, dashboard data would be flattened into multiple rows
            return new String[]{
                    "Dashboard Summary",
                    "See summary statistics section",
                    "Total Restaurants: " + (data.getTotalRestaurants() != null ? data.getTotalRestaurants() : 0) +
                            ", Total Orders: " + (data.getTotalOrders() != null ? data.getTotalOrders() : 0) +
                            ", Total Sales: " + (data.getTotalSales() != null ? data.getTotalSales().toString() : "0.00")
            };
        }

        /**
         * Builds metadata headers for the dashboard statistics report export.
         *
         * @param filters filter map containing period/dates/restaurant scoping values
         * @param locale  locale tag
         * @return ordered map of metadata keys/values to render in the export header
         */
        @Override
        public Map<String, String> getReportMetadata(Map<String, Object> filters, String locale) {
            Map<String, String> metadata = new LinkedHashMap<>();
            String period = (String) filters.get(FILTER_PERIOD);
            LocalDateTime startDate = (LocalDateTime) filters.get(FILTER_START_DATE);
            LocalDateTime endDate = (LocalDateTime) filters.get(FILTER_END_DATE);
            UUID restaurantGroupId = (UUID) filters.get(FILTER_RESTAURANT_GROUP_ID);
            UUID restaurantId = (UUID) filters.get(FILTER_RESTAURANT_ID);
            
            metadata.put("DASHBOARD STATISTICS REPORT", "");
            if (restaurantId != null) {
                metadata.put("Restaurant ID", restaurantId.toString());
            }
            if (restaurantGroupId != null) {
                metadata.put("Restaurant Group ID", restaurantGroupId.toString());
            }
            if (period != null) {
                metadata.put("Period", period);
            }
            metadata.put("Export Date", LocalDateTime.now(ZoneOffset.UTC).format(java.time.format.DateTimeFormatter.ofPattern(DATE_TIME_FORMAT_PATTERN)));
            if (startDate != null) {
                metadata.put("Start Date", startDate.format(java.time.format.DateTimeFormatter.ofPattern(DATE_TIME_FORMAT_PATTERN)));
            }
            if (endDate != null) {
                metadata.put("End Date", endDate.format(java.time.format.DateTimeFormatter.ofPattern(DATE_TIME_FORMAT_PATTERN)));
            }
            return metadata;
        }

        /**
         * Computes summary statistics for the dashboard export engine.
         * <p>
         * Delegates to the main dashboard statistics computation and extracts key summary values.
         * </p>
         *
         * @param filters filter map containing period/dates/restaurant scoping values
         * @param locale  locale tag
         * @return ordered map of summary fields to values
         */
        @Override
        public Map<String, String> getSummaryStatistics(Map<String, Object> filters, String locale) {
            String period = (String) filters.get(FILTER_PERIOD);
            LocalDateTime startDate = (LocalDateTime) filters.get(FILTER_START_DATE);
            LocalDateTime endDate = (LocalDateTime) filters.get(FILTER_END_DATE);
            UUID restaurantGroupId = (UUID) filters.get(FILTER_RESTAURANT_GROUP_ID);
            UUID restaurantId = (UUID) filters.get(FILTER_RESTAURANT_ID);
            String salesStatsPeriod = (String) filters.get("salesStatsPeriod");
            String localeStr = (String) filters.getOrDefault("locale", "en");
            
            ResponseDto<DashboardResponse> response = DashboardServiceImpl.this.getDashboardStatistics(
                    period, startDate, endDate, restaurantGroupId, restaurantId, salesStatsPeriod, localeStr);
            
            DashboardResponse data = response.getData();
            if (data == null) {
                return Collections.emptyMap();
            }
            
            Map<String, String> summary = new LinkedHashMap<>();
            summary.put("Total Restaurants", data.getTotalRestaurants() != null ? String.valueOf(data.getTotalRestaurants()) : "0");
            summary.put("Total Orders", data.getTotalOrders() != null ? String.valueOf(data.getTotalOrders()) : "0");
            summary.put(LABEL_TOTAL_SALES, data.getTotalSales() != null ? data.getTotalSales().toString() : "0.00");
            summary.put("Active Employees", data.getActiveEmployeesCount() != null ? String.valueOf(data.getActiveEmployeesCount()) : "0");
            summary.put("Total Items", data.getTotalItems() != null ? String.valueOf(data.getTotalItems()) : "0");
            summary.put("Active Discounts", data.getActiveDiscountsCount() != null ? String.valueOf(data.getActiveDiscountsCount()) : "0");
            summary.put("Active Promotions", data.getActivePromotionsCount() != null ? String.valueOf(data.getActivePromotionsCount()) : "0");
            
            return summary;
        }

        @Override
        public boolean supportsReportType(com.gulfnet.shared_library.enums.ReportType reportType) {
            return reportType == com.gulfnet.shared_library.enums.ReportType.DASHBOARD_STATISTICS;
        }
    }

    /**
     * Extracts the live menu ID from a list of restaurant menu mappings.
     * Returns null if the list is null or no live menu is found.
     */
    private UUID extractLiveMenuId(List<RestaurantMenuMapping> menuMappings) {
        if (menuMappings == null) {
            return null;
        }
        return menuMappings.stream()
                .filter(m -> m != null && m.getStatus() == RestaurantMenuMappingStatus.LIVE)
                .filter(m -> m.getId() != null)
                .map(m -> m.getId().getMenuId())
                .filter(Objects::nonNull)
                .findFirst()
                .orElse(null);
    }

    private record ParsedItemCategoryIds(UUID itemId, UUID categoryId) {}

    private ParsedItemCategoryIds parseItemAndCategoryIdsBestEffort(Object[] row) {
        if (row == null || row.length < 1) {
            return null;
        }
        try {
            UUID itemId = parseUuidFromRow(row[0]);
            UUID categoryId = (row.length > 1 && row[1] != null) ? parseUuidFromRow(row[1]) : null;
            return new ParsedItemCategoryIds(itemId, categoryId);
        } catch (Exception e) {
            logger.warn("Error parsing row data, skipping. Error: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Resolves the main category for an item from category-item mappings.
     * Returns the parent category if the item is in a subcategory, otherwise returns the direct category.
     */
    private UUID resolveMainCategoryForItem(UUID itemId, List<CategoryItemMapping> mappings) {
        if (mappings == null || itemId == null) {
            return null;
        }
        for (CategoryItemMapping cim : mappings) {
            boolean skip = cim == null
                    || cim.getItem() == null
                    || cim.getItem().getId() == null
                    || !itemId.equals(cim.getItem().getId())
                    || cim.getMenuCategoryMapping() == null;
            if (skip) {
                continue;
            }
            CategoryItemMapping nonNullCim = cim;
            MenuCategoryMapping mcm = nonNullCim.getMenuCategoryMapping();
            Category mainCategory = (mcm.getParentCategory() != null) ? mcm.getParentCategory() : mcm.getCategory();
            if (mainCategory != null && mainCategory.getId() != null) {
                return mainCategory.getId();
            }
        }
        return null;
    }

    /**
     * Resolves main categories for multiple items from category-item mappings.
     * Populates the itemToMainCategoryId map with item ID -> main category ID entries.
     */
    private void resolveMainCategoriesForItems(List<CategoryItemMapping> mappings,
                                               java.util.Collection<UUID> itemIds,
                                               Map<UUID, UUID> itemToMainCategoryId) {
        if (mappings == null || itemIds == null || itemToMainCategoryId == null) {
            return;
        }
        for (CategoryItemMapping cim : mappings) {
            UUID mappedItemId = (cim != null && cim.getItem() != null) ? cim.getItem().getId() : null;
            boolean skip = mappedItemId == null
                    || !itemIds.contains(mappedItemId)
                    || cim.getMenuCategoryMapping() == null;
            if (skip) {
                continue;
            }
            CategoryItemMapping nonNullCim = cim;
            MenuCategoryMapping mcm = nonNullCim.getMenuCategoryMapping();
            Category mainCategory = (mcm.getParentCategory() != null) ? mcm.getParentCategory() : mcm.getCategory();
            if (mainCategory != null && mainCategory.getId() != null) {
                itemToMainCategoryId.putIfAbsent(mappedItemId, mainCategory.getId());
            }
        }
    }

    /**
     * Resolves the live menu ID and main category for an item at a given restaurant.
     * Returns the resolved category ID, or null if not found.
     */
    private UUID resolveMainCategoryViaLiveMenu(UUID restaurantId, UUID itemId) {
        try {
            List<RestaurantMenuMapping> menuMappings = restaurantMenuMappingRepository.findById_RestaurantId(restaurantId);
            UUID liveMenuId = extractLiveMenuId(menuMappings);
            if (liveMenuId != null) {
                List<CategoryItemMapping> mappings =
                        categoryItemMappingRepository.findByMenuIdAndRestaurant(liveMenuId, restaurantId);
                return resolveMainCategoryForItem(itemId, mappings);
            }
        } catch (Exception e) {
            logger.debug("Error resolving main category for restaurant {}: {}", restaurantId, e.getMessage());
        }
        return null;
    }

    /**
     * Resolves main categories for multiple items via the live menu at a given restaurant.
     * Populates the itemToMainCategoryId map.
     */
    private void resolveMainCategoriesViaLiveMenu(UUID restaurantId,
                                                   java.util.Collection<UUID> itemIds,
                                                   Map<UUID, UUID> itemToMainCategoryId) {
        try {
            List<RestaurantMenuMapping> menuMappings = restaurantMenuMappingRepository.findById_RestaurantId(restaurantId);
            UUID liveMenuId = extractLiveMenuId(menuMappings);
            if (liveMenuId != null) {
                List<CategoryItemMapping> mappings =
                        categoryItemMappingRepository.findByMenuIdAndRestaurant(liveMenuId, restaurantId);
                resolveMainCategoriesForItems(mappings, itemIds, itemToMainCategoryId);
            }
        } catch (Exception e) {
            logger.debug("Error resolving main category for restaurant {}: {}", restaurantId, e.getMessage());
        }
    }

    private void resolveBestSellingItemsMainCategoriesBestEffort(UUID restaurantId,
                                                                 UUID restaurantGroupId,
                                                                 List<UUID> itemIds,
                                                                 Map<UUID, UUID> itemToMainCategoryId) {
        if (itemIds == null || itemIds.isEmpty() || itemToMainCategoryId == null) {
            return;
        }
        UUID sentinelUuid = SENTINEL_UUID;
        if (restaurantId != null && !restaurantId.equals(sentinelUuid)) {
            try {
                resolveMainCategoriesViaLiveMenu(restaurantId, itemIds, itemToMainCategoryId);
            } catch (Exception e) {
                logger.warn("Error resolving main category for best selling items, falling back to query category. Error: {}", e.getMessage());
            }
            return;
        }

        if (restaurantGroupId != null && !restaurantGroupId.equals(sentinelUuid)) {
            try {
                List<Restaurant> restaurants = restaurantRepository.findByRestaurantGroupIdAndIsDeletedFalse(restaurantGroupId);
                for (Restaurant restaurant : restaurants) {
                    UUID id = restaurant != null ? restaurant.getId() : null;
                    boolean skip = id == null;
                    if (skip) {
                        continue;
                    }
                    resolveMainCategoriesViaLiveMenu(id, itemIds, itemToMainCategoryId);
                }
            } catch (Exception e) {
                logger.warn("Error resolving main category for best selling items (restaurantGroupId), falling back to query category. Error: {}", e.getMessage());
            }
            return;
        }

        try {
            resolveCategoriesViaAllMappings(itemIds, itemToMainCategoryId);
        } catch (Exception e) {
            logger.warn("Error resolving main category for best selling items (no restaurant filter), falling back to query category. Error: {}", e.getMessage());
        }
    }

    /**
     * Fetches item wastage summary filtered by restaurant or restaurant group.
     */
    private List<Object[]> fetchItemWastageSummary(UUID restaurantId, UUID restaurantGroupId,
                                                    LocalDateTime startDate, LocalDateTime endDate) {
        try {
            if (restaurantId != null) {
                return orderedItemRepository.getWastageSummaryByRestaurantId(restaurantId, startDate, endDate);
            } else if (restaurantGroupId != null) {
                return orderedItemRepository.getWastageSummaryByRestaurantGroupId(restaurantGroupId, startDate, endDate);
            } else {
                return orderedItemRepository.getWastageSummary(startDate, endDate);
            }
        } catch (Exception e) {
            logger.warn("Error fetching item wastage summary: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Fetches combo wastage summary filtered by restaurant or restaurant group.
     */
    private List<Object[]> fetchComboWastageSummary(UUID restaurantId, UUID restaurantGroupId,
                                                     LocalDateTime startDate, LocalDateTime endDate) {
        try {
            if (restaurantId != null) {
                return orderedComboRepository.getComboWastageSummaryByRestaurantId(restaurantId, startDate, endDate);
            } else if (restaurantGroupId != null) {
                return orderedComboRepository.getComboWastageSummaryByRestaurantGroupId(restaurantGroupId, startDate, endDate);
            } else {
                return orderedComboRepository.getComboWastageSummary(startDate, endDate);
            }
        } catch (Exception e) {
            logger.warn("Error fetching combo wastage summary: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Extracts quantity and cost from a wastage summary row.
     * Returns an array of [quantity, cost].
     */
    private Object[] extractWastageValues(List<Object[]> wastageSummaryList, String type) {
        Long quantity = 0L;
        BigDecimal cost = BigDecimal.ZERO;
        if (wastageSummaryList != null && !wastageSummaryList.isEmpty()) {
            Object[] summary = wastageSummaryList.get(0);
            if (summary != null && summary.length >= 2) {
                if (summary[0] instanceof Number) {
                    quantity = ((Number) summary[0]).longValue();
                }
                if (summary[1] instanceof BigDecimal) {
                    cost = (BigDecimal) summary[1];
                } else if (summary[1] instanceof Number) {
                    cost = BigDecimal.valueOf(((Number) summary[1]).doubleValue());
                }
            }
        }
        return new Object[]{quantity, cost};
    }

    /**
     * Parses a UUID from a native query result row element.
     * Handles both UUID and String types.
     */
    private UUID parseUuidFromRow(Object value) {
        if (value instanceof UUID) {
            return (UUID) value;
        } else if (value instanceof String) {
            return UUID.fromString((String) value);
        }
        return null;
    }

    /**
     * Resolves the main category for an item by scanning all its CategoryItemMappings
     * and finding one that belongs to a LIVE menu.
     */
    private UUID resolveCategoryViaAllMappings(UUID itemId) {
        List<CategoryItemMapping> allMappings = categoryItemMappingRepository.findByItem_Id(itemId);
        for (CategoryItemMapping cim : allMappings) {
            boolean skip = cim == null
                    || cim.getMenuCategoryMapping() == null
                    || cim.getMenuCategoryMapping().getMenu() == null
                    || cim.getMenuCategoryMapping().getMenu().getId() == null;
            if (skip) {
                continue;
            }
            CategoryItemMapping nonNullCim = cim;
            MenuCategoryMapping mcm = nonNullCim.getMenuCategoryMapping();
            UUID menuId = mcm.getMenu().getId();
            boolean isLive = restaurantMenuMappingRepository.findById_MenuId(menuId).stream()
                    .anyMatch(m -> m != null && m.getStatus() == RestaurantMenuMappingStatus.LIVE);
            if (!isLive) {
                // not part of a LIVE menu; ignore
            } else {
                Category mainCategory = (mcm.getParentCategory() != null) ? mcm.getParentCategory() : mcm.getCategory();
                if (mainCategory != null && mainCategory.getId() != null) {
                    return mainCategory.getId();
                }
            }
        }
        return null;
    }

    /**
     * Resolves main categories for multiple items by scanning their CategoryItemMappings
     * and finding ones that belong to LIVE menus. Populates the itemToMainCategoryId map.
     */
    private void resolveCategoriesViaAllMappings(List<UUID> itemIds, Map<UUID, UUID> itemToMainCategoryId) {
        List<CategoryItemMapping> allMappings = categoryItemMappingRepository.findByItem_IdIn(itemIds);
        Set<UUID> menuIds = allMappings.stream()
                .filter(cim -> cim != null && cim.getMenuCategoryMapping() != null && cim.getMenuCategoryMapping().getMenu() != null)
                .map(cim -> cim.getMenuCategoryMapping().getMenu().getId())
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        for (UUID menuId : menuIds) {
            List<RestaurantMenuMapping> liveMenuMappings = restaurantMenuMappingRepository.findById_MenuId(menuId)
                    .stream()
                    .filter(m -> m != null && m.getStatus() == RestaurantMenuMappingStatus.LIVE)
                    .collect(Collectors.toList());
            if (liveMenuMappings.isEmpty()) {
                continue;
            }
            RestaurantMenuMapping firstLiveMapping = liveMenuMappings.get(0);
            UUID mappedRestaurantId = (firstLiveMapping.getId() != null) ? firstLiveMapping.getId().getRestaurantId() : null;
            if (mappedRestaurantId != null) {
                List<CategoryItemMapping> menuMappings =
                        categoryItemMappingRepository.findByMenuIdAndRestaurant(menuId, mappedRestaurantId);
                resolveMainCategoriesForItems(menuMappings, itemIds, itemToMainCategoryId);
            }
        }
    }

}

