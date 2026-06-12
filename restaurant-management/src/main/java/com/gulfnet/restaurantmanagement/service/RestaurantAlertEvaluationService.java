package com.gulfnet.restaurantmanagement.service;

import com.gulfnet.restaurantmanagement.service.AlertConfigurationResolver.ResolvedAlertConfig;
import com.gulfnet.shared_library.entity.Restaurant;
import com.gulfnet.shared_library.repository.NotificationRepository;
import com.gulfnet.shared_library.repository.RestaurantRepository;
import com.gulfnet.shared_library.repository.TransactionRepository;
import com.gulfnet.shared_library.repository.CashDrawerLogRepository;
import com.gulfnet.shared_library.repository.RefundRepository;
import com.gulfnet.shared_library.repository.OrderRepository;
import com.gulfnet.shared_library.enums.OrderStatus;
import com.gulfnet.shared_library.enums.TransactionStatus;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Evaluates per-restaurant business metrics and triggers HQ Admin alerts
 * (sales threshold, refund percentage, cancellation percentage).
 *
 * This service reuses existing reports/payment-and-financials logic so we don't
 * duplicate complex SQL calculations.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RestaurantAlertEvaluationService {

    private final AlertConfigurationResolver alertConfigurationResolver;
    private final NotificationService notificationService;
    private final NotificationRepository notificationRepository;
    private final RestaurantRepository restaurantRepository;
    private final OrderRepository orderRepository;
    private final TransactionRepository transactionRepository;
    private final CashDrawerLogRepository cashDrawerLogRepository;
    private final RefundRepository refundRepository;

    @PersistenceContext
    private EntityManager entityManager;

    /** In-JVM lock per (restaurantId, date) so concurrent evaluations for the same restaurant/date serialize and duplicate check sees committed data. */
    private static final ConcurrentHashMap<String, Object> EVALUATION_LOCK_MAP = new ConcurrentHashMap<>();

    /**
     * Minimum number of completed + cancelled orders required before evaluating
     * order cancellation percentage alerts (order-based: SERVED vs CANCELED).
     */
    private static final long MIN_ORDERS_FOR_CANCELLATION_ALERT = 3L;

    /**
     * Minimum number of completed + cancelled transactions required before evaluating
     * transaction cancellation percentage alerts (transaction-based: COMPLETED vs CANCELED).
     */
    private static final long MIN_TRANSACTIONS_FOR_CANCELLATION_ALERT = 3L;

    /**
     * Evaluate alerts for a single restaurant in real-time (for today's date).
     * Called after each transaction completion, refund, or cancellation.
     * Includes duplicate prevention - won't send the same alert twice in the same day.
     * 
     * Uses REQUIRES_NEW propagation to ensure any failures in alert evaluation
     * (e.g., reports service exceptions) don't mark the parent payment transaction rollback-only.
     * 
     * noRollbackFor prevents transaction rollback when reports service throws ResponseStatusException,
     * allowing sales threshold alerts to still be sent even if refund/cancellation metrics fail.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW, noRollbackFor = {org.springframework.web.server.ResponseStatusException.class})
    public void evaluateRestaurantAlertsRealtime(Restaurant restaurant, Locale locale) {
        log.info("🔔 Starting alert evaluation for restaurant: {} (ID: {})", 
                restaurant != null ? restaurant.getRestaurantCode() : "null",
                restaurant != null ? restaurant.getId() : "null");
        
        if (restaurant == null || restaurant.getId() == null) {
            log.warn("⚠️ Cannot evaluate alerts: restaurant is null");
            return;
        }

        try {
            // IMPORTANT: Re-fetch the Restaurant entity within this REQUIRES_NEW transaction.
            // The Restaurant passed from afterCommit() callbacks is a DETACHED entity from the
            // already-committed parent transaction. Its lazy associations (e.g. restaurantGroup,
            // translations) are uninitialized proxies that will throw LazyInitializationException
            // if accessed outside their original session. By re-fetching here, we get a fully
            // managed entity in the current persistence context where lazy loading works correctly.
            // Use findByIdWithGroup to eagerly fetch restaurantGroup so group-level thresholds are available.
            Restaurant managedRestaurant = restaurantRepository.findByIdWithGroup(restaurant.getId()).orElse(null);
            if (managedRestaurant == null) {
                log.warn("⚠️ Cannot evaluate alerts: restaurant {} not found in database", restaurant.getId());
                return;
            }
            log.info("📅 Re-fetched restaurant {} in REQUIRES_NEW transaction for alert evaluation", managedRestaurant.getRestaurantCode());

            LocalDate today = LocalDate.now(ZoneOffset.UTC);
            log.info("📅 Evaluating alerts for date: {} (UTC)", today);
            // Keep lock in map so concurrent evaluations for same restaurant+day serialize on the SAME lock.
            // Removing the lock after use would let a second thread get a new lock and run in parallel, causing
            // duplicate FCM (e.g. sales threshold popping twice) before the first run's DB save is visible.
            String lockKey = managedRestaurant.getId() + "_" + today;
            Object lock = EVALUATION_LOCK_MAP.computeIfAbsent(lockKey, k -> new Object());
            log.info("[SALES_THRESHOLD_DEBUG] EVALUATION_START ts={} restaurant={} lockKey={} - entering synchronized block",
                    System.currentTimeMillis(), managedRestaurant.getRestaurantCode(), lockKey);
            synchronized (lock) {
                evaluateRestaurantAlertsWithDeduplication(managedRestaurant, today, locale, true);
            }
            log.info("✅ Alert evaluation completed successfully for restaurant: {}", managedRestaurant.getRestaurantCode());
        } catch (Exception e) {
            log.error("❌ Failed to evaluate restaurant alerts for restaurant {}: {}", 
                    restaurant.getId(), e.getMessage(), e);
            // Don't rethrow - alert evaluation failures shouldn't affect the payment transaction
            // The REQUIRES_NEW propagation ensures failures here don't affect the parent transaction
        }
    }

    /**
     * Internal method that evaluates alerts with optional duplicate prevention.
     * @param restaurant Restaurant to evaluate
     * @param businessDate Date to evaluate for
     * @param locale Locale for messages
     * @param checkDuplicates If true, checks if alert was already sent today before sending
     */
    private void evaluateRestaurantAlertsWithDeduplication(Restaurant restaurant, LocalDate businessDate, Locale locale, boolean checkDuplicates) {
        // Acquire database-level lock on restaurant row to prevent duplicate alerts across multiple app instances.
        // The in-memory EVALUATION_LOCK_MAP only works within a single JVM; with horizontal scaling, two instances
        // could both pass the duplicate check before either commits. This pessimistic lock serializes evaluations
        // for the same restaurant across all instances until the transaction commits.
        if (entityManager != null) {
            try {
                entityManager.find(Restaurant.class, restaurant.getId(), LockModeType.PESSIMISTIC_WRITE);
            } catch (Exception e) {
                log.warn("Could not acquire DB lock for alert evaluation (restaurant {}), proceeding with in-memory lock only: {}",
                        restaurant.getId(), e.getMessage());
            }
        }

        ResolvedAlertConfig config = alertConfigurationResolver.resolveForRestaurant(restaurant);

        log.info("🔍 Alert configuration resolved for restaurant {}: alertsEnabled={}, salesThreshold={}, refundThreshold={}, cancellationThreshold={}", 
                restaurant.getRestaurantCode(), config.isAlertsEnabled(), 
                config.getSalesAlertThreshold(), config.getRefundAlertPercentage(), config.getCancellationAlertPercentage());

        if (!config.isAlertsEnabled()) {
            log.warn("⚠️ Alerts are DISABLED for restaurant {} - skipping all threshold evaluations", restaurant.getRestaurantCode());
            return;
        }

        // Use local date range for the business day (UTC localDateTime range)
        LocalDateTime startDateTime = businessDate.atStartOfDay();
        LocalDateTime endDateTime = businessDate.plusDays(1).atStartOfDay();

        UUID restaurantId = restaurant.getId();
        log.info("📊 Evaluating alerts for restaurant: {} (ID: {}), date range: {} to {}", 
                restaurant.getRestaurantCode(), restaurantId, startDateTime, endDateTime);

        // 1) Sales metrics (computed directly from transaction data)
        BigDecimal totalSales = getTotalSalesForRestaurant(restaurantId, startDateTime, endDateTime);
        log.info("💰 Total sales calculated: {} for restaurant: {}", totalSales, restaurant.getRestaurantCode());

        // 2) Refund / cancellation metrics (computed directly from repositories)
        // Wrap in try-catch so failures don't prevent sales threshold alerts from being sent
        BigDecimal refundPct = null;
        try {
            refundPct = getRefundPercentageForRestaurant(restaurantId, startDateTime, endDateTime, totalSales);
            log.info("💸 Refund percentage calculated: {}% for restaurant: {}", refundPct, restaurant.getRestaurantCode());
        } catch (Exception e) {
            log.warn("Failed to get refund percentage for restaurant {} - will skip refund alert but continue with other alerts: {}", 
                    restaurantId, e.getMessage());
        }
        
        BigDecimal orderCancelPct = null;
        try {
            orderCancelPct = getOrderCancellationPercentageForRestaurant(restaurantId, startDateTime, endDateTime);
            log.info("🚫 Order cancellation percentage calculated: {}% for restaurant: {}", orderCancelPct, restaurant.getRestaurantCode());
        } catch (Exception e) {
            log.warn("Failed to get order cancellation percentage for restaurant {} - will skip order cancellation alert: {}", 
                    restaurantId, e.getMessage());
        }

        BigDecimal transactionCancelPct = null;
        try {
            transactionCancelPct = getTransactionCancellationPercentageForRestaurant(restaurantId, startDateTime, endDateTime);
            log.info("🚫 Transaction cancellation percentage calculated: {}% for restaurant: {}", transactionCancelPct, restaurant.getRestaurantCode());
        } catch (Exception e) {
            log.warn("Failed to get transaction cancellation percentage for restaurant {} - will skip transaction cancellation alert: {}", 
                    restaurantId, e.getMessage());
        }

        // 3) Sales threshold alert
        log.info("🔍 Checking sales threshold - Total: {}, Threshold: {}, Alerts Enabled: {}", 
                totalSales, config.getSalesAlertThreshold(), config.isAlertsEnabled());
        
        if (config.getSalesAlertThreshold() != null
                && totalSales != null
                && totalSales.compareTo(config.getSalesAlertThreshold()) >= 0) {

            String alertType = "SALES_THRESHOLD_ALERT";
            boolean duplicateCheck = checkDuplicates && wasAlertSentToday(restaurant, alertType, businessDate);
            log.info("🚨 Sales threshold BREACHED! Total: {} >= Threshold: {}, Duplicate check: {}", 
                    totalSales, config.getSalesAlertThreshold(), duplicateCheck);
            
            if (!checkDuplicates || !duplicateCheck) {
                log.info("[SALES_THRESHOLD_DEBUG] EVALUATION_PASSED ts={} restaurant={} restaurantId={} total={} threshold={} - calling notifyHqAdminsSalesThresholdBreached",
                        System.currentTimeMillis(), restaurant.getRestaurantCode(), restaurant.getId(), totalSales, config.getSalesAlertThreshold());
                notificationService.notifyHqAdminsSalesThresholdBreached(restaurant, totalSales, locale);
                log.info("[SALES_THRESHOLD_DEBUG] EVALUATION_DONE ts={} restaurant={} - notifyHqAdminsSalesThresholdBreached completed",
                        System.currentTimeMillis(), restaurant.getRestaurantCode());
            } else {
                log.info("[SALES_THRESHOLD_DEBUG] EVALUATION_SKIPPED_DUPLICATE ts={} restaurantId={} - wasAlertSentToday=true, skipping", System.currentTimeMillis(), restaurant.getId());
            }
        } else {
            log.info("ℹ️ Sales threshold not breached - Total: {}, Threshold: {}", totalSales, config.getSalesAlertThreshold());
        }

        // 4) Refund % alert
        // NOTE: This alert is evaluated independently from cancellation alerts.
        // If both refund and cancellation thresholds are breached, both notifications will be sent.
        // This is correct behavior - each threshold should be evaluated separately.
        log.info("🔍 Checking refund percentage threshold - Refund %: {}, Threshold: {}, Alerts Enabled: {}", 
                refundPct, config.getRefundAlertPercentage(), config.isAlertsEnabled());
        
        if (config.getRefundAlertPercentage() != null
                && refundPct != null
                && refundPct.compareTo(config.getRefundAlertPercentage()) >= 0) {

            String alertType = "REFUND_PERCENTAGE_ALERT";
            boolean duplicateCheck = checkDuplicates && wasAlertSentToday(restaurant, alertType, businessDate);
            log.info("🚨 Refund percentage threshold BREACHED! Refund %: {} >= Threshold: {}, Duplicate check: {}", 
                    refundPct, config.getRefundAlertPercentage(), duplicateCheck);
            
            if (!checkDuplicates || !duplicateCheck) {
                log.info("📤 Sending refund percentage alert for restaurant {}: refundPct={} threshold={}",
                        restaurant.getId(), refundPct, config.getRefundAlertPercentage());
                notificationService.notifyHqAdminsRefundPercentageBreached(
                        restaurant, refundPct, config.getRefundAlertPercentage(), locale);
                log.info("✅ Refund percentage alert sent successfully for restaurant: {}", restaurant.getRestaurantCode());
            } else {
                log.info("⏭️ Refund percentage alert already sent today for restaurant {} - skipping duplicate", restaurant.getId());
            }
        } else {
            log.info("ℹ️ Refund percentage threshold not breached - Refund %: {}, Threshold: {}", refundPct, config.getRefundAlertPercentage());
        }

        // 5) Cancellation % alerts (order and/or transaction) – one push and one notification per day
        boolean orderBreached = config.getCancellationAlertPercentage() != null
                && orderCancelPct != null
                && orderCancelPct.compareTo(BigDecimal.ZERO) > 0
                && orderCancelPct.compareTo(config.getCancellationAlertPercentage()) >= 0;
        boolean transactionBreached = config.getCancellationAlertPercentage() != null
                && transactionCancelPct != null
                && transactionCancelPct.compareTo(BigDecimal.ZERO) > 0
                && transactionCancelPct.compareTo(config.getCancellationAlertPercentage()) >= 0;

        if (orderBreached || transactionBreached) {
            // Duplicate: skip if we already sent any cancellation alert today (single combined or legacy order/transaction)
            boolean cancellationAlreadySent = checkDuplicates && (
                    wasAlertSentToday(restaurant, "CANCELLATION_PERCENTAGE_ALERT", businessDate)
                    || wasAlertSentToday(restaurant, "ORDER_CANCELLATION_PERCENTAGE_ALERT", businessDate)
                    || wasAlertSentToday(restaurant, "TRANSACTION_CANCELLATION_PERCENTAGE_ALERT", businessDate));
            boolean skipPush = cancellationAlreadySent;
            if (!skipPush) {
                notificationService.notifyHqAdminsCancellationPercentageBreachedIfAny(
                        restaurant, orderCancelPct, transactionCancelPct, config.getCancellationAlertPercentage(), locale, orderBreached, transactionBreached);
            } else {
                log.info("⏭️ Cancellation alert(s) already sent today for restaurant {} - skipping duplicate", restaurant.getRestaurantCode());
            }
        } else {
            log.info("ℹ️ Cancellation thresholds not breached - Order cancel %: {}, Transaction cancel %: {}, Threshold: {}",
                    orderCancelPct, transactionCancelPct, config.getCancellationAlertPercentage());
        }
    }

    /**
     * Check if an alert of the given type was already sent today for this restaurant.
     * Identifies restaurant-specific alerts by checking the notification message/title
     * for restaurant code or restaurant group name. Normalizes by removing spaces so
     * display names like "Rosa Two" match code "rosatwo" (each type fires only once per day).
     */
    private boolean wasAlertSentToday(Restaurant restaurant, String alertType, LocalDate date) {
        try {
            if (restaurant == null) {
                log.warn("Cannot check for duplicate alert: restaurant is null");
                return false;
            }

            OffsetDateTime startOfDay = date.atStartOfDay().atOffset(ZoneOffset.UTC);
            OffsetDateTime endOfDay = date.plusDays(1).atStartOfDay().atOffset(ZoneOffset.UTC);

            // Query for notifications of this type created today
            List<com.gulfnet.shared_library.entity.Notification> existingAlerts =
                    notificationRepository.findByTypeAndCreatedAtBetween(alertType, startOfDay, endOfDay);

            if (existingAlerts.isEmpty()) {
                return false;
            }

            // Get restaurant identifiers to check in message content
            // The notification message format is: "Alert for restaurant {restaurantName}"
            // where restaurantName is either restaurantGroupName or restaurantCode
            String restaurantCode = restaurant.getRestaurantCode();
            String restaurantGroupName = restaurant.getRestaurantGroupName();
            
            // Check if any of the existing alerts are for this restaurant
            // by checking if the message/title contains the restaurant identifier.
            // Normalize by removing spaces so "Rosa Two" in message matches code "rosatwo"
            // (getRestaurantNameForLocale may return translated/display names with spaces).
            String codeNormalized = restaurantCode != null && !restaurantCode.isEmpty()
                    ? restaurantCode.toLowerCase().replaceAll("\\s+", "") : null;
            String groupNormalized = restaurantGroupName != null && !restaurantGroupName.isEmpty()
                    ? restaurantGroupName.toLowerCase().replaceAll("\\s+", "") : null;

            // Reliable match: HQ threshold alerts store restaurant ID in body as [rid:uuid] for deduplication
            String restaurantIdSentinel = "[rid:" + restaurant.getId() + "]";
            for (com.gulfnet.shared_library.entity.Notification alert : existingAlerts) {
                String message = alert.getMessage();
                String title = alert.getTitle();

                if (message != null && message.contains(restaurantIdSentinel)) {
                    log.debug("Found duplicate alert for restaurant {} (rid sentinel) in message",
                            restaurant.getId());
                    return true;
                }

                if (message != null) {
                    String messageNormalized = message.toLowerCase().replaceAll("\\s+", "");
                    if (codeNormalized != null && !codeNormalized.isEmpty() && messageNormalized.contains(codeNormalized)) {
                        log.debug("Found duplicate alert for restaurant {} (code: {}) in message: {}",
                                restaurant.getId(), restaurantCode, message);
                        return true;
                    }
                    if (groupNormalized != null && !groupNormalized.isEmpty() && messageNormalized.contains(groupNormalized)) {
                        log.debug("Found duplicate alert for restaurant {} (group: {}) in message: {}",
                                restaurant.getId(), restaurantGroupName, message);
                        return true;
                    }
                }

                if (title != null) {
                    String titleNormalized = title.toLowerCase().replaceAll("\\s+", "");
                    if (codeNormalized != null && !codeNormalized.isEmpty() && titleNormalized.contains(codeNormalized)) {
                        log.debug("Found duplicate alert for restaurant {} (code: {}) in title: {}",
                                restaurant.getId(), restaurantCode, title);
                        return true;
                    }
                    if (groupNormalized != null && !groupNormalized.isEmpty() && titleNormalized.contains(groupNormalized)) {
                        log.debug("Found duplicate alert for restaurant {} (group: {}) in title: {}",
                                restaurant.getId(), restaurantGroupName, title);
                        return true;
                    }
                }
            }

            // No matching alert found for this restaurant
            return false;

        } catch (Exception e) {
            log.error("Error checking for duplicate alert for restaurant {} type {}: {}",
                    restaurant != null ? restaurant.getId() : "null", alertType, e.getMessage(), e);
            // On error, allow the alert to be sent (fail open)
            return false;
        }
    }

    // ---------------- Metric helpers (direct repository-based) ----------------

    /**
     * Calculates total sales for a restaurant within a date range.
     * Uses the same native query as the daily sales summary report.
     *
     * @param restaurantId the restaurant ID to calculate sales for
     * @param start        start date and time of the range
     * @param end          end date and time of the range
     * @return total sales amount, or {@link BigDecimal#ZERO} if no sales found or on error
     */
    private BigDecimal getTotalSalesForRestaurant(UUID restaurantId,
                                                  LocalDateTime start,
                                                  LocalDateTime end) {
        try {
            log.debug("📈 Calculating total sales for restaurant: {}, date range: {} to {}", restaurantId, start, end);
            // Reuse the same native query used by reports: getDailySalesSummary
            // Returns: [totalSales, totalOrders, totalTablesServed]
            java.util.List<Object[]> results = transactionRepository.getDailySalesSummary(restaurantId, start, end);
            if (results == null || results.isEmpty() || results.get(0) == null) {
                log.warn("⚠️ No sales data found for restaurant: {} in date range: {} to {}", restaurantId, start, end);
                return BigDecimal.ZERO;
            }
            Object[] row = results.get(0);
            if (row.length == 0 || row[0] == null) {
                log.warn("⚠️ Sales data row is empty for restaurant: {}", restaurantId);
                return BigDecimal.ZERO;
            }
            BigDecimal sales = (row[0] instanceof BigDecimal)
                    ? (BigDecimal) row[0]
                    : BigDecimal.valueOf(((Number) row[0]).doubleValue());
            log.debug("✅ Total sales calculated: {} for restaurant: {}", sales, restaurantId);
            return sales;
        } catch (Exception e) {
            log.error("❌ Failed to get total sales for restaurant {} using TransactionRepository: {}", restaurantId, e.getMessage(), e);
            return BigDecimal.ZERO;
        }
    }

    /**
     * Calculates refund percentage for a restaurant within a date range.
     * Formula: (total refund amount / gross sales) * 100.
     * Uses cashier-completed refunds only (see {@code RefundRepository#sumTotalRefundAmountByRestaurantId}).
     *
     * @param restaurantId the restaurant ID to calculate refund percentage for
     * @param start        start date and time of the range
     * @param end          end date and time of the range
     * @param totalSales   total sales amount for the date range
     * @return refund percentage as a BigDecimal, or {@link BigDecimal#ZERO} if no refunds or on error
     */
    private BigDecimal getRefundPercentageForRestaurant(UUID restaurantId,
                                                        LocalDateTime start,
                                                        LocalDateTime end,
                                                        BigDecimal totalSales) {
        try {
            // Refund % = (total completed refund amount / gross sales) * 100
            BigDecimal totalRefundsPaid = refundRepository.sumTotalRefundAmountByRestaurantId(restaurantId, start, end);
            
            if (totalRefundsPaid == null || totalRefundsPaid.compareTo(BigDecimal.ZERO) <= 0) {
                log.debug("No refunds found for restaurant {} in date range {} to {}", restaurantId, start, end);
                return BigDecimal.ZERO;
            }

            // IMPORTANT: totalSales from getDailySalesSummary is NET sales (already has refunds subtracted).
            // To calculate refund percentage correctly, we need GROSS sales as the denominator.
            // grossSales = netSales + totalRefunds (adding back what was subtracted)
            // This avoids the issue where a full refund makes totalSales = 0, causing refund % = 0.
            BigDecimal grossSales = (totalSales != null ? totalSales : BigDecimal.ZERO).add(totalRefundsPaid);

            if (grossSales.compareTo(BigDecimal.ZERO) <= 0) {
                log.debug("Gross sales is zero or negative for restaurant {} - refund percentage will be zero", restaurantId);
                return BigDecimal.ZERO;
            }

            log.debug("Calculating refund percentage for restaurant {}: refunds={}, netSales={}, grossSales={}", 
                    restaurantId, totalRefundsPaid, totalSales, grossSales);

            BigDecimal refundPercentage = totalRefundsPaid
                    .divide(grossSales, 4, RoundingMode.HALF_UP)
                    .multiply(BigDecimal.valueOf(100))
                    .setScale(2, RoundingMode.HALF_UP);

            log.debug("Refund percentage calculated: {}% for restaurant {}", refundPercentage, restaurantId);
            return refundPercentage;

        } catch (Exception e) {
            log.error("Failed to get refund percentage for restaurant {} using RefundRepository: {}", restaurantId, e.getMessage(), e);
            return BigDecimal.ZERO;
        }
    }

    /**
     * Order cancellation rate: cancelled_orders_with_non_zero_total / (cancelled + served_orders) * 100.
     * Only counts cancelled orders with total_amount > 0 (excludes hold/push cancellations with $0 total).
     * Uses order status (SERVED vs CANCELED). Date filter: order updated_at.
     * Minimum volume: MIN_ORDERS_FOR_CANCELLATION_ALERT.
     */
    private BigDecimal getOrderCancellationPercentageForRestaurant(UUID restaurantId,
                                                                   LocalDateTime start,
                                                                   LocalDateTime end) {
        try {
            if (restaurantId == null) {
                return BigDecimal.ZERO;
            }
            UUID sentinelGroupId = UUID.fromString("00000000-0000-0000-0000-000000000000");
            long cancelledCount = orderRepository.countCanceledOrdersWithNonZeroTotalByFilters(
                    restaurantId, sentinelGroupId, start, end);
            long servedCount = orderRepository.countByOrderStatusAndFilters(
                    OrderStatus.SERVED.name(), restaurantId, sentinelGroupId, start, end);
            long total = cancelledCount + servedCount;
            if (total < MIN_ORDERS_FOR_CANCELLATION_ALERT || cancelledCount <= 0) {
                return BigDecimal.ZERO;
            }
            return BigDecimal.valueOf(cancelledCount)
                    .divide(BigDecimal.valueOf(total), 4, RoundingMode.HALF_UP)
                    .multiply(BigDecimal.valueOf(100))
                    .setScale(2, RoundingMode.HALF_UP);
        } catch (Exception e) {
            log.error("Failed to get order cancellation percentage for restaurant {}: {}", restaurantId, e.getMessage(), e);
            return BigDecimal.ZERO;
        }
    }

    /**
     * Transaction cancellation rate: cancelled_transactions_with_non_zero_amount / (cancelled + completed) * 100.
     * Only counts cancelled transactions with transaction_amount > 0 (excludes open/hold cancellations with $0).
     * Uses transaction status (COMPLETED vs CANCELED). Date filter: transaction updatedAt (when status was set),
     * so transactions completed or cancelled on the business day are counted (same as order cancellation using order.updated_at).
     * Minimum volume: MIN_TRANSACTIONS_FOR_CANCELLATION_ALERT.
     */
    private BigDecimal getTransactionCancellationPercentageForRestaurant(UUID restaurantId,
                                                                        LocalDateTime start,
                                                                        LocalDateTime end) {
        try {
            if (restaurantId == null) {
                return BigDecimal.ZERO;
            }

            OffsetDateTime startOffset = start.atOffset(ZoneOffset.UTC);
            OffsetDateTime endOffset = end.atOffset(ZoneOffset.UTC);

            long completedCount = transactionRepository.countByRestaurantIdAndTransactionStatusAndUpdatedAtBetween(
                    restaurantId,
                    TransactionStatus.COMPLETED,
                    startOffset,
                    endOffset
            );

            long cancelledCount = transactionRepository.countCanceledTransactionsWithNonZeroAmountByRestaurantIdAndUpdatedAtBetween(
                    restaurantId,
                    startOffset,
                    endOffset,
                    TransactionStatus.CANCELED
            );

            long totalCompletedOrCancelled = completedCount + cancelledCount;

            if (totalCompletedOrCancelled < MIN_TRANSACTIONS_FOR_CANCELLATION_ALERT) {
                return BigDecimal.ZERO;
            }

            if (cancelledCount <= 0) {
                return BigDecimal.ZERO;
            }

            BigDecimal cancelled = BigDecimal.valueOf(cancelledCount);
            BigDecimal total = BigDecimal.valueOf(totalCompletedOrCancelled);

            return cancelled
                    .divide(total, 4, RoundingMode.HALF_UP)
                    .multiply(BigDecimal.valueOf(100))
                    .setScale(2, RoundingMode.HALF_UP);

        } catch (Exception e) {
            log.error("Failed to get cancellation percentage for restaurant {} using TransactionRepository: {}", restaurantId, e.getMessage(), e);
            return BigDecimal.ZERO;
        }
    }
}

