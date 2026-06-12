package com.gulfnet.shared_library.repository;

import com.gulfnet.shared_library.entity.Transaction;
import com.gulfnet.shared_library.enums.OrderStatus;
import com.gulfnet.shared_library.enums.OrderType;
import com.gulfnet.shared_library.enums.RequestStatus;
import com.gulfnet.shared_library.enums.TransactionStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface TransactionRepository extends JpaRepository<Transaction, UUID> {
    
    /**
     * Find the first OPEN transaction for a session (for POSTPAID orders)
     */
    @Query("SELECT t FROM Transaction t WHERE t.session.id = :sessionId AND t.transactionStatus = 'OPEN' ORDER BY t.createdAt ASC")
    Optional<Transaction> findFirstOpenTransactionBySessionId(@Param("sessionId") UUID sessionId);
    
    /**
     * Find all transactions for a session
     */
    List<Transaction> findBySessionIdOrderByCreatedAtAsc(UUID sessionId);
    
    /**
     * Find all transactions with a specific status for a session
     */
    List<Transaction> findBySessionIdAndTransactionStatusOrderByCreatedAtAsc(UUID sessionId, TransactionStatus status);
    
    /**
     * Find transaction by order ID
     */
    Optional<Transaction> findByOrderId(UUID orderId);

    /**
     * Batch fetch transactions for multiple orders.
     * Used by session order listing to avoid per-order transaction N+1 queries.
     */
    @Query("SELECT DISTINCT t FROM Transaction t " +
            "LEFT JOIN FETCH t.reviewedBy " +
            "LEFT JOIN FETCH t.order o " +
            "WHERE o.id IN :orderIds")
    List<Transaction> findByOrderIds(@Param("orderIds") Collection<UUID> orderIds);

    /**
     * Find transaction by Omise charge ID (used for UPI/PayPay refunds via Omise).
     */
    Optional<Transaction> findByOmiseChargeId(String omiseChargeId);
    
    /**
     * Find transaction by ID with order, table, and restaurant relationships loaded using JOIN FETCH.
     * This prevents LazyInitializationException when accessing nested properties
     * (e.g. restaurant code for alert evaluation after cancellation approval).
     */
    @Query("SELECT DISTINCT t FROM Transaction t " +
           "LEFT JOIN FETCH t.order o " +
           "LEFT JOIN FETCH o.restaurantTable rt " +
           "LEFT JOIN FETCH o.restaurant " +
           "LEFT JOIN FETCH t.restaurant " +
           "WHERE t.id = :id")
    Optional<Transaction> findByIdWithOrderAndTable(@Param("id") UUID id);

    /**
     * Atomically completes a {@link TransactionStatus#PENDING} payment (GMO card/UPI success).
     * Returns {@code 1} if this caller won the race, {@code 0} if already completed or not pending.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE Transaction t SET t.transactionStatus = :completed, "
            + "t.gmoHostedPaymentUrl = NULL, t.gmoHostedPaymentLinkCreatedAt = NULL, t.updatedAt = :updatedAt "
            + "WHERE t.id = :id AND t.transactionStatus = :pending")
    int claimPendingPaymentCompletion(
            @Param("id") UUID id,
            @Param("pending") TransactionStatus pending,
            @Param("completed") TransactionStatus completed,
            @Param("updatedAt") OffsetDateTime updatedAt);

    /**
     * Updates only GMO LinkPlus trade credentials without touching transaction status.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE Transaction t SET t.gmoAccessId = :accessId, t.gmoAccessPass = :accessPass, "
            + "t.gmoOrderId = COALESCE(t.gmoOrderId, :gmoOrderId), t.updatedAt = :updatedAt "
            + "WHERE t.id = :id")
    int updateGmoTradeCredentials(
            @Param("id") UUID id,
            @Param("accessId") String accessId,
            @Param("accessPass") String accessPass,
            @Param("gmoOrderId") String gmoOrderId,
            @Param("updatedAt") OffsetDateTime updatedAt);
    
    /**
     * Find transaction by ID with all relationships needed for refund request response.
     * Used after entity manager operations to ensure all lazy-loaded relationships are available.
     */
    @Query("SELECT DISTINCT t FROM Transaction t " +
           "LEFT JOIN FETCH t.restaurant r " +
           "LEFT JOIN FETCH r.translations " +
           "LEFT JOIN FETCH t.order o " +
           "LEFT JOIN FETCH t.requestedBy " +
           "LEFT JOIN FETCH t.reviewedBy " +
           "WHERE t.id = :id")
    Optional<Transaction> findByIdWithRelationshipsForRefundResponse(@Param("id") UUID id);
    
    /**
     * Find all transactions by restaurant ID and transaction status list
     */
    @Query("SELECT t FROM Transaction t WHERE t.restaurant.id = :restaurantId AND t.transactionStatus IN :statuses")
    List<Transaction> findByRestaurantIdAndTransactionStatusIn(
            @Param("restaurantId") UUID restaurantId,
            @Param("statuses") Collection<TransactionStatus> statuses);
    
    /**
     * Find all transactions by restaurant ID
     */
    @Query("SELECT t FROM Transaction t WHERE t.restaurant.id = :restaurantId")
    List<Transaction> findByRestaurantId(@Param("restaurantId") UUID restaurantId);
    
    /**
     * Find transactions by restaurant with optional filters, search, and date range
     * Excludes OPEN and PENDING transactions
     */
    @Query("SELECT t FROM Transaction t JOIN t.order o " +
           "WHERE t.restaurant.id = :restaurantId " +
           "AND t.transactionStatus NOT IN ('OPEN', 'PENDING') " +
           "AND ((:orderStatuses) IS NULL OR o.orderStatus IN (:orderStatuses)) " +
           "AND ((:orderTypes) IS NULL OR o.orderType IN (:orderTypes)) " +
           "AND ((:transactionStatuses) IS NULL OR t.transactionStatus IN (:transactionStatuses)) " +
           "AND ((:paymentMethods) IS NULL OR t.paymentMethod IN (:paymentMethods)) " +
           "AND (:likePatternLower IS NULL OR :likePatternLower = '' OR (LOWER(t.transactionNumber) LIKE :likePatternLower " +
           "     OR LOWER(o.orderNumber) LIKE :likePatternLower)) " +
           "AND (t.createdAt >= :startDate) " +
           "AND (t.createdAt <= :endDate)")
    Page<Transaction> findByRestaurantIdWithFilters(
            @Param("restaurantId") UUID restaurantId,
            @Param("orderStatuses") Collection<OrderStatus> orderStatuses,
            @Param("orderTypes") Collection<OrderType> orderTypes,
            @Param("transactionStatuses") Collection<TransactionStatus> transactionStatuses,
            @Param("paymentMethods") Collection<String> paymentMethods,
            @Param("likePatternLower") String likePatternLower,
            @Param("startDate") OffsetDateTime startDate,
            @Param("endDate") OffsetDateTime endDate,
            Pageable pageable);

    /**
     * Same as {@link #findByRestaurantIdWithFilters(UUID, Collection, Collection, Collection, Collection, String, OffsetDateTime, OffsetDateTime, Pageable)}
     * but orders by effective time (updatedAt fallback to createdAt).
     * <p>
     * This is needed to get a true fallback sort (COALESCE) rather than grouping NULL updatedAt values together.
     * </p>
     */
    @Query("SELECT t FROM Transaction t JOIN t.order o " +
            "WHERE t.restaurant.id = :restaurantId " +
            "AND t.transactionStatus NOT IN ('OPEN', 'PENDING') " +
            "AND ((:orderStatuses) IS NULL OR o.orderStatus IN (:orderStatuses)) " +
            "AND ((:orderTypes) IS NULL OR o.orderType IN (:orderTypes)) " +
            "AND ((:transactionStatuses) IS NULL OR t.transactionStatus IN (:transactionStatuses)) " +
            "AND ((:paymentMethods) IS NULL OR t.paymentMethod IN (:paymentMethods)) " +
            "AND (:likePatternLower IS NULL OR :likePatternLower = '' OR (LOWER(t.transactionNumber) LIKE :likePatternLower " +
            "     OR LOWER(o.orderNumber) LIKE :likePatternLower)) " +
            "AND (t.createdAt >= :startDate) " +
            "AND (t.createdAt <= :endDate) " +
            "ORDER BY COALESCE(t.updatedAt, t.createdAt) DESC")
    Page<Transaction> findByRestaurantIdWithFiltersOrderByEffectiveTimeDesc(
            @Param("restaurantId") UUID restaurantId,
            @Param("orderStatuses") Collection<OrderStatus> orderStatuses,
            @Param("orderTypes") Collection<OrderType> orderTypes,
            @Param("transactionStatuses") Collection<TransactionStatus> transactionStatuses,
            @Param("paymentMethods") Collection<String> paymentMethods,
            @Param("likePatternLower") String likePatternLower,
            @Param("startDate") OffsetDateTime startDate,
            @Param("endDate") OffsetDateTime endDate,
            Pageable pageable);

    /**
     * Ascending variant of effective-time sort (COALESCE(updatedAt, createdAt)).
     */
    @Query("SELECT t FROM Transaction t JOIN t.order o " +
            "WHERE t.restaurant.id = :restaurantId " +
            "AND t.transactionStatus NOT IN ('OPEN', 'PENDING') " +
            "AND ((:orderStatuses) IS NULL OR o.orderStatus IN (:orderStatuses)) " +
            "AND ((:orderTypes) IS NULL OR o.orderType IN (:orderTypes)) " +
            "AND ((:transactionStatuses) IS NULL OR t.transactionStatus IN (:transactionStatuses)) " +
            "AND ((:paymentMethods) IS NULL OR t.paymentMethod IN (:paymentMethods)) " +
            "AND (:likePatternLower IS NULL OR :likePatternLower = '' OR (LOWER(t.transactionNumber) LIKE :likePatternLower " +
            "     OR LOWER(o.orderNumber) LIKE :likePatternLower)) " +
            "AND (t.createdAt >= :startDate) " +
            "AND (t.createdAt <= :endDate) " +
            "ORDER BY COALESCE(t.updatedAt, t.createdAt) ASC")
    Page<Transaction> findByRestaurantIdWithFiltersOrderByEffectiveTimeAsc(
            @Param("restaurantId") UUID restaurantId,
            @Param("orderStatuses") Collection<OrderStatus> orderStatuses,
            @Param("orderTypes") Collection<OrderType> orderTypes,
            @Param("transactionStatuses") Collection<TransactionStatus> transactionStatuses,
            @Param("paymentMethods") Collection<String> paymentMethods,
            @Param("likePatternLower") String likePatternLower,
            @Param("startDate") OffsetDateTime startDate,
            @Param("endDate") OffsetDateTime endDate,
            Pageable pageable);

    /**
     * Find transactions by request status (currently used for cancellation requests)
     */
    Page<Transaction> findByRequestStatus(RequestStatus status, Pageable pageable);
    
    /**
     * Find transactions by request status, with optional status filter.
     * If status is null, returns all transactions with request status != NONE.
     */
    @Query("SELECT t FROM Transaction t WHERE " +
           "(:status IS NULL AND t.requestStatus != com.gulfnet.shared_library.enums.RequestStatus.NONE) OR " +
           "(:status IS NOT NULL AND t.requestStatus = :status)")
    Page<Transaction> findByRequestStatusOptional(
            @Param("status") RequestStatus status, 
            Pageable pageable);
    
    /**

     * Calculate total sales from completed transactions, subtracting refund amounts
     * For COMPLETED status, includes COMPLETED, REFUNDED, and PARTIALLY_REFUNDED transactions
     * For other statuses, filters by that specific status
     */
    @Query("SELECT COALESCE(SUM(t.transactionAmount - COALESCE(CASE WHEN t.requestStatus = 'APPROVED' AND t.transactionStatus IN ('REFUNDED', 'PARTIALLY_REFUNDED') THEN rf.totalRefundAmount ELSE 0 END, 0)), 0) " +
           "FROM Transaction t " +
           "LEFT JOIN Refund rf ON rf.transaction.id = t.id " +
           "WHERE (t.transactionStatus = :status " +
           "       OR (:status = 'COMPLETED' AND t.transactionStatus IN ('COMPLETED', 'REFUNDED', 'PARTIALLY_REFUNDED')))")
    BigDecimal sumTransactionAmountByStatus(@Param("status") TransactionStatus status);
    
    /**
     * Calculate total sales from completed transactions within a date range, subtracting refund amounts
     * For COMPLETED status, includes COMPLETED, REFUNDED, and PARTIALLY_REFUNDED transactions
     * For other statuses, filters by that specific status
     * Only subtracts refunds that have been APPROVED by manager AND completed by cashier.
     * Requires both: request_status = 'APPROVED' AND transaction_status IN ('REFUNDED', 'PARTIALLY_REFUNDED').
     */
    @Query("SELECT COALESCE(SUM(t.transactionAmount - COALESCE(CASE WHEN t.requestStatus = 'APPROVED' AND t.transactionStatus IN ('REFUNDED', 'PARTIALLY_REFUNDED') THEN rf.totalRefundAmount ELSE 0 END, 0)), 0) " +
           "FROM Transaction t " +
           "LEFT JOIN Refund rf ON rf.transaction.id = t.id " +
           "WHERE (t.transactionStatus = :status " +
           "       OR (:status = 'COMPLETED' AND t.transactionStatus IN ('COMPLETED', 'REFUNDED', 'PARTIALLY_REFUNDED'))) " +
           "AND t.createdAt >= :startDate AND t.createdAt <= :endDate")
    BigDecimal sumTransactionAmountByStatusAndDateRange(
            @Param("status") TransactionStatus status,
            @Param("startDate") OffsetDateTime startDate,
            @Param("endDate") OffsetDateTime endDate);
    
    /**
     * Calculate total sales from completed transactions by restaurant group within a date range, subtracting refund amounts
     * For COMPLETED status, includes COMPLETED, REFUNDED, and PARTIALLY_REFUNDED transactions
     * For other statuses, filters by that specific status
     * Only subtracts refunds that have been APPROVED by manager AND completed by cashier.
     * Requires both: request_status = 'APPROVED' AND transaction_status IN ('REFUNDED', 'PARTIALLY_REFUNDED').
     */
    @Query("SELECT COALESCE(SUM(t.transactionAmount - COALESCE(CASE WHEN t.requestStatus = 'APPROVED' AND t.transactionStatus IN ('REFUNDED', 'PARTIALLY_REFUNDED') THEN rf.totalRefundAmount ELSE 0 END, 0)), 0) " +
           "FROM Transaction t " +
           "LEFT JOIN Refund rf ON rf.transaction.id = t.id " +
           "WHERE (t.transactionStatus = :status " +
           "       OR (:status = 'COMPLETED' AND t.transactionStatus IN ('COMPLETED', 'REFUNDED', 'PARTIALLY_REFUNDED'))) " +
           "AND t.restaurant.restaurantGroup.id = :restaurantGroupId " +
           "AND t.createdAt >= :startDate AND t.createdAt <= :endDate")
    BigDecimal sumTransactionAmountByRestaurantGroupIdAndStatusAndDateRange(
            @Param("restaurantGroupId") java.util.UUID restaurantGroupId,
            @Param("status") TransactionStatus status,
            @Param("startDate") OffsetDateTime startDate,
            @Param("endDate") OffsetDateTime endDate);
    
    /**
     * Calculate total sales from completed transactions by restaurant group (all-time), subtracting refund amounts
     * For COMPLETED status, includes COMPLETED, REFUNDED, and PARTIALLY_REFUNDED transactions
     * For other statuses, filters by that specific status
     * Only subtracts refunds that have been APPROVED by manager AND completed by cashier.
     * Requires both: request_status = 'APPROVED' AND transaction_status IN ('REFUNDED', 'PARTIALLY_REFUNDED').
     */
    @Query("SELECT COALESCE(SUM(t.transactionAmount - COALESCE(CASE WHEN t.requestStatus = 'APPROVED' AND t.transactionStatus IN ('REFUNDED', 'PARTIALLY_REFUNDED') THEN rf.totalRefundAmount ELSE 0 END, 0)), 0) " +
           "FROM Transaction t " +
           "LEFT JOIN Refund rf ON rf.transaction.id = t.id " +
           "WHERE (t.transactionStatus = :status " +
           "       OR (:status = 'COMPLETED' AND t.transactionStatus IN ('COMPLETED', 'REFUNDED', 'PARTIALLY_REFUNDED'))) " +
           "AND t.restaurant.restaurantGroup.id = :restaurantGroupId")
    BigDecimal sumTransactionAmountByRestaurantGroupIdAndStatus(
            @Param("restaurantGroupId") java.util.UUID restaurantGroupId,
            @Param("status") TransactionStatus status);
    
    /**
     * Count transactions by status and restaurant group (all-time)
     */
    @Query("SELECT COUNT(t) FROM Transaction t WHERE t.transactionStatus = :status AND t.restaurant.restaurantGroup.id = :restaurantGroupId")
    long countByRestaurantGroupIdAndTransactionStatus(
            @Param("restaurantGroupId") UUID restaurantGroupId,
            @Param("status") TransactionStatus status);
    
    /**
     * Count transactions by status and restaurant group within a date range
     */
    @Query("SELECT COUNT(t) FROM Transaction t WHERE t.transactionStatus = :status AND t.restaurant.restaurantGroup.id = :restaurantGroupId AND t.createdAt >= :startDate AND t.createdAt <= :endDate")
    long countByRestaurantGroupIdAndTransactionStatusAndDateRange(
            @Param("restaurantGroupId") UUID restaurantGroupId,
            @Param("status") TransactionStatus status,
            @Param("startDate") OffsetDateTime startDate,
            @Param("endDate") OffsetDateTime endDate);
    
    /**
     * Count transactions by status (all-time, no restaurant group filter)
     */
    @Query("SELECT COUNT(t) FROM Transaction t WHERE t.transactionStatus = :status")
    long countByTransactionStatus(@Param("status") TransactionStatus status);
    
    /**
     * Count transactions by status within a date range (no restaurant group filter)
     */
    @Query("SELECT COUNT(t) FROM Transaction t WHERE t.transactionStatus = :status AND t.createdAt >= :startDate AND t.createdAt <= :endDate")
    long countByTransactionStatusAndDateRange(
            @Param("status") TransactionStatus status,
            @Param("startDate") OffsetDateTime startDate,
            @Param("endDate") OffsetDateTime endDate);

    /**
     * Calculate total sales from completed transactions by restaurant (all-time), subtracting refund amounts
     * For COMPLETED status, includes COMPLETED, REFUNDED, and PARTIALLY_REFUNDED transactions
     * For other statuses, filters by that specific status
     * Only subtracts refunds that have been APPROVED by manager AND completed by cashier.
     * Requires both: request_status = 'APPROVED' AND transaction_status IN ('REFUNDED', 'PARTIALLY_REFUNDED').
     */
    @Query("SELECT COALESCE(SUM(t.transactionAmount - COALESCE(CASE WHEN t.requestStatus = 'APPROVED' AND t.transactionStatus IN ('REFUNDED', 'PARTIALLY_REFUNDED') THEN rf.totalRefundAmount ELSE 0 END, 0)), 0) " +
           "FROM Transaction t " +
           "LEFT JOIN Refund rf ON rf.transaction.id = t.id " +
           "WHERE (t.transactionStatus = :status " +
           "       OR (:status = 'COMPLETED' AND t.transactionStatus IN ('COMPLETED', 'REFUNDED', 'PARTIALLY_REFUNDED'))) " +
           "AND t.restaurant.id = :restaurantId")
    BigDecimal sumTransactionAmountByRestaurantIdAndStatus(
            @Param("restaurantId") UUID restaurantId,
            @Param("status") TransactionStatus status);

    /**
     * Calculate total sales from completed transactions by restaurant within a date range, subtracting refund amounts
     * For COMPLETED status, includes COMPLETED, REFUNDED, and PARTIALLY_REFUNDED transactions
     * For other statuses, filters by that specific status
     * Only subtracts refunds that have been APPROVED by manager AND completed by cashier.
     * Requires both: request_status = 'APPROVED' AND transaction_status IN ('REFUNDED', 'PARTIALLY_REFUNDED').
     */
    @Query("SELECT COALESCE(SUM(t.transactionAmount - COALESCE(CASE WHEN t.requestStatus = 'APPROVED' AND t.transactionStatus IN ('REFUNDED', 'PARTIALLY_REFUNDED') THEN rf.totalRefundAmount ELSE 0 END, 0)), 0) " +
           "FROM Transaction t " +
           "LEFT JOIN Refund rf ON rf.transaction.id = t.id " +
           "WHERE (t.transactionStatus = :status " +
           "       OR (:status = 'COMPLETED' AND t.transactionStatus IN ('COMPLETED', 'REFUNDED', 'PARTIALLY_REFUNDED'))) " +
           "AND t.restaurant.id = :restaurantId " +
           "AND t.createdAt >= :startDate AND t.createdAt <= :endDate")
    BigDecimal sumTransactionAmountByRestaurantIdAndStatusAndDateRange(
            @Param("restaurantId") UUID restaurantId,
            @Param("status") TransactionStatus status,
            @Param("startDate") OffsetDateTime startDate,
            @Param("endDate") OffsetDateTime endDate);

    /**
     * Count transactions by status and restaurant (all-time)
     */
    @Query("SELECT COUNT(t) FROM Transaction t WHERE t.transactionStatus = :status AND t.restaurant.id = :restaurantId")
    long countByRestaurantIdAndTransactionStatus(
            @Param("restaurantId") UUID restaurantId,
            @Param("status") TransactionStatus status);

    /**
     * Count transactions by status and restaurant within a date range
     */
    @Query("SELECT COUNT(t) FROM Transaction t WHERE t.transactionStatus = :status AND t.restaurant.id = :restaurantId AND t.createdAt >= :startDate AND t.createdAt <= :endDate")
    long countByRestaurantIdAndTransactionStatusAndDateRange(
            @Param("restaurantId") UUID restaurantId,
            @Param("status") TransactionStatus status,
            @Param("startDate") OffsetDateTime startDate,
            @Param("endDate") OffsetDateTime endDate);

    /**
     * Count CANCELED transactions with non-zero amount (excludes open/hold cancellations with $0).
     * Used for cancellation % so only revenue-relevant cancellations are counted.
     * Uses createdAt for date range (legacy).
     */
    @Query("SELECT COUNT(t) FROM Transaction t WHERE t.transactionStatus = 'CANCELED' AND t.restaurant.id = :restaurantId AND t.createdAt >= :startDate AND t.createdAt <= :endDate AND (t.transactionAmount IS NOT NULL AND t.transactionAmount > 0)")
    long countCanceledTransactionsWithNonZeroAmountByRestaurantIdAndDateRange(
            @Param("restaurantId") UUID restaurantId,
            @Param("startDate") OffsetDateTime startDate,
            @Param("endDate") OffsetDateTime endDate);

    /**
     * Count transactions by status and restaurant where updated_at falls in the given range.
     * Used for cancellation % so we count transactions that reached this status on the business day
     * (e.g. completed or cancelled today), matching order cancellation logic which uses order.updated_at.
     */
    @Query("SELECT COUNT(t) FROM Transaction t WHERE t.transactionStatus = :status AND t.restaurant.id = :restaurantId AND t.updatedAt >= :startDate AND t.updatedAt < :endDate")
    long countByRestaurantIdAndTransactionStatusAndUpdatedAtBetween(
            @Param("restaurantId") UUID restaurantId,
            @Param("status") TransactionStatus status,
            @Param("startDate") OffsetDateTime startDate,
            @Param("endDate") OffsetDateTime endDate);

    /**
     * Count CANCELED transactions with non-zero amount where updated_at falls in the given range.
     * Ensures transactions cancelled today are counted in today's cancellation %, same as order cancellation.
     * Uses enum parameter for status to avoid JPQL literal interpretation issues across persistence providers.
     */
    @Query("SELECT COUNT(t) FROM Transaction t WHERE t.transactionStatus = :status AND t.restaurant.id = :restaurantId AND t.updatedAt >= :startDate AND t.updatedAt < :endDate AND (t.transactionAmount IS NOT NULL AND t.transactionAmount > 0)")
    long countCanceledTransactionsWithNonZeroAmountByRestaurantIdAndUpdatedAtBetween(
            @Param("restaurantId") UUID restaurantId,
            @Param("startDate") OffsetDateTime startDate,
            @Param("endDate") OffsetDateTime endDate,
            @Param("status") TransactionStatus status);

       
    Page<Transaction> findByRequestStatusAndTransactionStatus(RequestStatus requestStatus, TransactionStatus transactionStatus, Pageable pageable);

    /**
     * Get daily sales statistics (grouped by date)
     * Returns list of [date, orderCount, totalSales] as Object arrays
     * Only includes COMPLETED transactions
     * Uses sentinel UUID '00000000-0000-0000-0000-000000000000' to handle null parameters
     */
    @Query(value = "SELECT DATE(t.created_at) as sale_date, " +
           "       COUNT(t.id) as order_count, " +
           "       COALESCE(SUM(t.transaction_amount), 0) as total_sales " +
           "FROM transaction t " +
           "JOIN restaurant r ON r.id = t.restaurant_id " +
           "WHERE t.transaction_status = 'COMPLETED' " +
           "AND (CAST(:restaurantId AS uuid) = '00000000-0000-0000-0000-000000000000'::uuid OR t.restaurant_id = CAST(:restaurantId AS uuid)) " +
           "AND (CAST(:restaurantGroupId AS uuid) = '00000000-0000-0000-0000-000000000000'::uuid OR r.restaurant_group_id = CAST(:restaurantGroupId AS uuid)) " +
           "AND (r.is_deleted IS NULL OR r.is_deleted = false) " +
           "GROUP BY DATE(t.created_at) " +
           "ORDER BY DATE(t.created_at) DESC " +
           "LIMIT 30", nativeQuery = true)
    List<Object[]> getDailySalesStats(
            @Param("restaurantId") UUID restaurantId,
            @Param("restaurantGroupId") UUID restaurantGroupId);

    /**
     * Get weekly sales statistics (grouped by week)
     * Returns list of [weekStartDate, orderCount, totalSales] as Object arrays
     * Only includes COMPLETED transactions
     * Week starts on Monday (ISO 8601)
     * Uses sentinel UUID '00000000-0000-0000-0000-000000000000' to handle null parameters
     */
    @Query(value = "SELECT DATE_TRUNC('week', t.created_at)::date as week_start, " +
           "       COUNT(t.id) as order_count, " +
           "       COALESCE(SUM(t.transaction_amount), 0) as total_sales " +
           "FROM transaction t " +
           "JOIN restaurant r ON r.id = t.restaurant_id " +
           "WHERE t.transaction_status = 'COMPLETED' " +
           "AND (CAST(:restaurantId AS uuid) = '00000000-0000-0000-0000-000000000000'::uuid OR t.restaurant_id = CAST(:restaurantId AS uuid)) " +
           "AND (CAST(:restaurantGroupId AS uuid) = '00000000-0000-0000-0000-000000000000'::uuid OR r.restaurant_group_id = CAST(:restaurantGroupId AS uuid)) " +
           "AND (r.is_deleted IS NULL OR r.is_deleted = false) " +
           "GROUP BY DATE_TRUNC('week', t.created_at) " +
           "ORDER BY DATE_TRUNC('week', t.created_at) DESC " +
           "LIMIT 12", nativeQuery = true)
    List<Object[]> getWeeklySalesStats(
            @Param("restaurantId") UUID restaurantId,
            @Param("restaurantGroupId") UUID restaurantGroupId);

    /**
     * Get monthly sales statistics (grouped by month)
     * Returns list of [monthStartDate, orderCount, totalSales] as Object arrays
     * Only includes COMPLETED transactions
     * Uses sentinel UUID '00000000-0000-0000-0000-000000000000' to handle null parameters
     */
    @Query(value = "SELECT DATE_TRUNC('month', t.created_at)::date as month_start, " +
           "       COUNT(t.id) as order_count, " +
           "       COALESCE(SUM(t.transaction_amount), 0) as total_sales " +
           "FROM transaction t " +
           "JOIN restaurant r ON r.id = t.restaurant_id " +
           "WHERE t.transaction_status = 'COMPLETED' " +
           "AND (CAST(:restaurantId AS uuid) = '00000000-0000-0000-0000-000000000000'::uuid OR t.restaurant_id = CAST(:restaurantId AS uuid)) " +
           "AND (CAST(:restaurantGroupId AS uuid) = '00000000-0000-0000-0000-000000000000'::uuid OR r.restaurant_group_id = CAST(:restaurantGroupId AS uuid)) " +
           "AND (r.is_deleted IS NULL OR r.is_deleted = false) " +
           "GROUP BY DATE_TRUNC('month', t.created_at) " +
           "ORDER BY DATE_TRUNC('month', t.created_at) DESC " +
           "LIMIT 12", nativeQuery = true)
    List<Object[]> getMonthlySalesStats(
            @Param("restaurantId") UUID restaurantId,
            @Param("restaurantGroupId") UUID restaurantGroupId);

    /**
     * Find transactions with discounts applied (order-level or additional discounts)
     * Filters by restaurant, date range, and transaction status
     * Includes orders with: discount entity, discount code, discount amount > 0, or additional discount amount > 0
     */
    @Query("SELECT t FROM Transaction t JOIN t.order o " +
           "WHERE t.restaurant.id = :restaurantId " +
           "AND ((:transactionStatuses) IS NULL OR t.transactionStatus IN (:transactionStatuses)) " +
           "AND (t.createdAt >= :startDate) " +
           "AND (t.createdAt <= :endDate) " +
           "AND (o.discount IS NOT NULL " +
           "     OR o.discountCode IS NOT NULL " +
           "     OR (o.discountAmount IS NOT NULL AND o.discountAmount > 0) " +
           "     OR (o.additionalDiscountAmount IS NOT NULL AND o.additionalDiscountAmount > 0)) " +
           "ORDER BY t.createdAt DESC")
    Page<Transaction> findTransactionsWithDiscounts(
            @Param("restaurantId") UUID restaurantId,
            @Param("transactionStatuses") Collection<TransactionStatus> transactionStatuses,
            @Param("startDate") OffsetDateTime startDate,
            @Param("endDate") OffsetDateTime endDate,
            Pageable pageable);

    /**
     * Get daily sales summary for reports
     * Returns: [totalSales, totalOrders, totalTablesServed]
     * Filters by specific restaurantId only.
     *
     * IMPORTANT:
     * - Includes COMPLETED, REFUNDED and PARTIALLY_REFUNDED transactions.
     * - Treats refunds as adjustments, not deletions, by subtracting the refund amount from the original transaction amount.
     * - Only subtracts refunds that have been APPROVED by manager (requestStatus = 'APPROVED' or 'NONE').
     * - Pending refund requests (requestStatus = 'OPEN') and declined requests (requestStatus = 'DECLINED') are not subtracted.
     * - Fully refunded orders (net_amount <= 0) are excluded from total_orders so that
     *   avgOrderValue = totalSales / totalOrders remains consistent with net sales.
     * - Uses updated_at for date filtering to correctly handle UPI and other PREPAID transactions
     *   that are created as PENDING and later updated to COMPLETED. For these transactions,
     *   updated_at represents the completion date, which is the correct business date.
     * Uses sentinel date '1970-01-01 00:00:00' to handle null date parameters.
     * Date bounds use AT TIME ZONE 'UTC' so they match RestaurantAlertEvaluationService and OrderRepository
     * (avoids CAST(updated_at AS timestamp), which depends on the DB session TimeZone when updated_at is TIMESTAMPTZ).
     */
    @Query(value =
           "WITH tx AS ( " +
           "    SELECT " +
           "        t.order_id, " +
           "        o.restaurant_table_id, " +
           "        t.restaurant_id, " +
           "        t.created_at, " +
           "        (t.transaction_amount - COALESCE(CASE WHEN t.request_status IN ('APPROVED', 'NONE') THEN rf.total_refund_amount ELSE 0 END, 0)) AS net_amount " +
           "    FROM transaction t " +
           "    JOIN orders o ON o.id = t.order_id " +
           "    JOIN restaurant r ON r.id = t.restaurant_id " +
           "    LEFT JOIN refund rf ON rf.transaction_id = t.id " +
           "    WHERE t.transaction_status IN ('COMPLETED', 'REFUNDED', 'PARTIALLY_REFUNDED') " +
           "      AND t.restaurant_id = CAST(:restaurantId AS uuid) " +
           "      AND (r.is_deleted IS NULL OR r.is_deleted = false) " +
           "      AND (CAST(:startDate AS timestamp) = '1970-01-01 00:00:00'::timestamp OR t.updated_at >= (CAST(:startDate AS timestamp) AT TIME ZONE 'UTC')) " +
           "      AND (CAST(:endDate AS timestamp) = '1970-01-01 00:00:00'::timestamp OR t.updated_at <= (CAST(:endDate AS timestamp) AT TIME ZONE 'UTC')) " +
           ") " +
           "SELECT " +
           "    COALESCE(SUM(net_amount), 0) AS total_sales, " +
           "    COUNT(DISTINCT CASE WHEN net_amount > 0 THEN order_id END) AS total_orders, " +
           "    COUNT(DISTINCT restaurant_table_id) AS total_tables_served " +
           "FROM tx",
           nativeQuery = true)
    List<Object[]> getDailySalesSummary(
            @Param("restaurantId") UUID restaurantId,
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate);

    /**
     * Get payment types breakdown for reports
     * Returns: [paymentMethod, totalSales]
     * Filters by specific restaurantId only
     * Uses sentinel date '1970-01-01 00:00:00' to handle null date parameters
     */
    @Query(value = "SELECT " +
           "       t.payment_method, " +
           "       COALESCE(SUM(t.transaction_amount), 0) as total_sales " +
           "FROM transaction t " +
           "JOIN restaurant r ON r.id = t.restaurant_id " +
           "WHERE t.transaction_status = 'COMPLETED' " +
           "AND t.payment_method IS NOT NULL " +
           "AND t.restaurant_id = CAST(:restaurantId AS uuid) " +
           "AND (r.is_deleted IS NULL OR r.is_deleted = false) " +
           "AND (CAST(:startDate AS timestamp) = '1970-01-01 00:00:00'::timestamp OR t.created_at >= CAST(:startDate AS timestamp)) " +
           "AND (CAST(:endDate AS timestamp) = '1970-01-01 00:00:00'::timestamp OR t.created_at <= CAST(:endDate AS timestamp)) " +
           "GROUP BY t.payment_method " +
           "ORDER BY total_sales DESC",
           nativeQuery = true)
    List<Object[]> getPaymentTypesBreakdown(
            @Param("restaurantId") UUID restaurantId,
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate);

    /**
     * Get cashier performance report
     * Returns: [cashierId, cashierFirstName, cashierLastName, cashierCode, totalTransactions, totalAmount, averageTransactionValue]
     * Filters by specific restaurantId only
     * Uses sentinel date '1970-01-01 00:00:00' to handle null date parameters
     * Only includes completed transactions
     */
    @Query(value = "SELECT " +
           "       t.cashier_id, " +
           "       u.first_name, " +
           "       u.last_name, " +
           "       u.user_code, " +
           "       COUNT(t.id) as total_transactions, " +
           "       ROUND(COALESCE(SUM(t.transaction_amount), 0), 2) as total_amount, " +
           "       CASE " +
           "           WHEN COUNT(t.id) > 0 THEN ROUND(COALESCE(SUM(t.transaction_amount), 0) / COUNT(t.id), 2) " +
           "           ELSE 0 " +
           "       END as average_transaction_value " +
           "FROM transaction t " +
           "JOIN restaurant r ON r.id = t.restaurant_id " +
           "LEFT JOIN users u ON u.id = t.cashier_id " +
           "WHERE t.transaction_status = 'COMPLETED' " +
           "AND t.cashier_id IS NOT NULL " +
           "AND t.restaurant_id = CAST(:restaurantId AS uuid) " +
           "AND (r.is_deleted IS NULL OR r.is_deleted = false) " +
           "AND (CAST(:startDate AS timestamp) = '1970-01-01 00:00:00'::timestamp OR t.created_at >= CAST(:startDate AS timestamp)) " +
           "AND (CAST(:endDate AS timestamp) = '1970-01-01 00:00:00'::timestamp OR t.created_at <= CAST(:endDate AS timestamp)) " +
           "GROUP BY t.cashier_id, u.first_name, u.last_name, u.user_code " +
           "ORDER BY total_amount DESC",
           nativeQuery = true)
    List<Object[]> getCashierPerformanceReport(
            @Param("restaurantId") UUID restaurantId,
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate);

    /**
     * Get payment reconciliation report
     * Returns: [paymentMethod, totalTransactions, totalAmount]
     * Filters by specific restaurantId only
     * Uses sentinel date '1970-01-01 00:00:00' to handle null date parameters
     */
    @Query(value = "SELECT " +
           "       t.payment_method, " +
           "       COUNT(t.id) as total_transactions, " +
           "       ROUND(COALESCE(SUM(t.transaction_amount), 0), 2) as total_amount " +
           "FROM transaction t " +
           "JOIN restaurant r ON r.id = t.restaurant_id " +
           "WHERE t.transaction_status = 'COMPLETED' " +
           "AND t.payment_method IS NOT NULL " +
           "AND t.restaurant_id = CAST(:restaurantId AS uuid) " +
           "AND (r.is_deleted IS NULL OR r.is_deleted = false) " +
           "AND (CAST(:startDate AS timestamp) = '1970-01-01 00:00:00'::timestamp OR t.created_at >= CAST(:startDate AS timestamp)) " +
           "AND (CAST(:endDate AS timestamp) = '1970-01-01 00:00:00'::timestamp OR t.created_at <= CAST(:endDate AS timestamp)) " +
           "GROUP BY t.payment_method " +
           "ORDER BY total_amount DESC",
           nativeQuery = true)
    List<Object[]> getPaymentReconciliationReport(
            @Param("restaurantId") UUID restaurantId,
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate);

    /**
     * Get cancellation report
     * Returns: [transactionId, transactionNumber, createdAt, transactionAmount, paymentMethod, requestComments, requestedByFirstName, requestedByLastName, reviewedByFirstName, reviewedByLastName]
     * Filters by specific restaurantId only
     * Uses sentinel date '1970-01-01 00:00:00' to handle null date parameters
     */
    @Query(value = "SELECT " +
           "       t.id as transaction_id, " +
           "       t.transaction_number, " +
           "       CAST(t.reviewed_at AS timestamp) as date_time, " +
           "       t.transaction_amount as amount, " +
           "       t.payment_method, " +
           "       COALESCE(t.request_data ->> 'cancellationReason', NULLIF(t.request_comments, ''), 'N/A') as reason, " +
           "       u1.first_name as requested_by_first_name, " +
           "       u1.last_name as requested_by_last_name, " +
           "       u2.first_name as reviewed_by_first_name, " +
           "       u2.last_name as reviewed_by_last_name, " +
           "       'transaction' as type " +
           "FROM transaction t " +
           "JOIN restaurant r ON r.id = t.restaurant_id " +
           "LEFT JOIN users u1 ON u1.id = t.requested_by " +
           "LEFT JOIN users u2 ON u2.id = t.reviewed_by " +
           "WHERE t.transaction_status = 'CANCELED' AND t.request_status = 'APPROVED' " +
           "AND t.restaurant_id = CAST(:restaurantId AS uuid) " +
           "AND (r.is_deleted IS NULL OR r.is_deleted = false) " +
           "AND (CAST(:startDate AS timestamp) = '1970-01-01 00:00:00'::timestamp OR t.reviewed_at >= CAST(:startDate AS timestamp)) " +
           "AND (CAST(:endDate AS timestamp) = '1970-01-01 00:00:00'::timestamp OR t.reviewed_at <= CAST(:endDate AS timestamp)) " +
           "UNION ALL " +
           "SELECT " +
           "       o.id as transaction_id, " +
           "       COALESCE(t.transaction_number, 'N/A') as transaction_number, " +
           "       CAST(COALESCE(o.cancellation_reviewed_at, CAST(o.updated_at AS timestamp)) AS timestamp) as date_time, " +
           "       o.total_amount as amount, " +
           "       COALESCE(t.payment_method, 'N/A') as payment_method, " +
           "       COALESCE(o.cancellation_request_data ->> 'cancellationReason', t.request_data ->> 'cancellationReason', NULLIF(t.request_comments, ''), 'N/A') as reason, " +
           "       COALESCE(u1.first_name, u6.first_name, u4.first_name, u3.first_name) as requested_by_first_name, " +
           "       COALESCE(u1.last_name, u6.last_name, u4.last_name, u3.last_name) as requested_by_last_name, " +
           "       COALESCE(u2.first_name, u7.first_name, u5.first_name, u3.first_name) as reviewed_by_first_name, " +
           "       COALESCE(u2.last_name, u7.last_name, u5.last_name, u3.last_name) as reviewed_by_last_name, " +
           "       'order' as type " +
           "FROM orders o " +
           "JOIN restaurant r ON r.id = o.restaurant_id " +
           "LEFT JOIN transaction t ON t.order_id = o.id " +
           "LEFT JOIN users u1 ON u1.id = o.cancellation_requested_by " +
           "LEFT JOIN users u2 ON u2.id = o.cancellation_reviewed_by " +
           "LEFT JOIN users u3 ON u3.id = o.updated_by " +
           "LEFT JOIN users u4 ON u4.id = t.requested_by " +
           "LEFT JOIN users u5 ON u5.id = t.reviewed_by " +
           "LEFT JOIN users u6 ON u6.id = t.requested_by " +
           "LEFT JOIN users u7 ON u7.id = t.reviewed_by " +
           "WHERE o.order_status = 'CANCELED' AND (o.cancellation_request_status = 'APPROVED' OR o.cancellation_request_status = 'NONE') " +
           "AND o.restaurant_id = CAST(:restaurantId AS uuid) " +
           "AND (r.is_deleted IS NULL OR r.is_deleted = false) " +
           "AND (CAST(:startDate AS timestamp) = '1970-01-01 00:00:00'::timestamp OR COALESCE(o.cancellation_reviewed_at, CAST(o.updated_at AS timestamp)) >= CAST(:startDate AS timestamp)) " +
           "AND (CAST(:endDate AS timestamp) = '1970-01-01 00:00:00'::timestamp OR COALESCE(o.cancellation_reviewed_at, CAST(o.updated_at AS timestamp)) <= CAST(:endDate AS timestamp)) " +
           "UNION ALL " +
           "SELECT " +
           "       oi.id as transaction_id, " +
           "       COALESCE(t.transaction_number, 'N/A') as transaction_number, " +
           "       CAST(COALESCE(oi.cancellation_reviewed_at, CAST(oi.updated_at AS timestamp)) AS timestamp) as date_time, " +
           "       CASE WHEN oi.cancellation_request_status = 'NONE' THEN COALESCE(oi.total_discounted_item_amount, oi.total_item_amount) ELSE oi.total_item_amount END as amount, " +
           "       COALESCE(t.payment_method, 'N/A') as payment_method, " +
           "       COALESCE(oi.cancellation_request_data ->> 'cancellationReason', o.cancellation_request_data ->> 'cancellationReason', t.request_data ->> 'cancellationReason', NULLIF(t.request_comments, ''), oi.reason, 'N/A') as reason, " +
           "       COALESCE(u1.first_name, u6.first_name, u4.first_name, u3.first_name) as requested_by_first_name, " +
           "       COALESCE(u1.last_name, u6.last_name, u4.last_name, u3.last_name) as requested_by_last_name, " +
           "       COALESCE(u2.first_name, u7.first_name, u5.first_name, u3.first_name) as reviewed_by_first_name, " +
           "       COALESCE(u2.last_name, u7.last_name, u5.last_name, u3.last_name) as reviewed_by_last_name, " +
           "       'item' as type " +
           "FROM ordered_item oi " +
           "JOIN orders o ON o.id = oi.order_id " +
           "JOIN restaurant r ON r.id = o.restaurant_id " +
           "LEFT JOIN transaction t ON t.order_id = o.id " +
           "LEFT JOIN users u1 ON u1.id = oi.cancellation_requested_by " +
           "LEFT JOIN users u2 ON u2.id = oi.cancellation_reviewed_by " +
           "LEFT JOIN users u3 ON u3.id = oi.updated_by " +
           "LEFT JOIN users u4 ON u4.id = t.requested_by " +
           "LEFT JOIN users u5 ON u5.id = t.reviewed_by " +
           "LEFT JOIN users u6 ON u6.id = o.cancellation_requested_by " +
           "LEFT JOIN users u7 ON u7.id = o.cancellation_reviewed_by " +
           "WHERE oi.item_status = 'CANCELED' AND (oi.cancellation_request_status = 'APPROVED' OR oi.cancellation_request_status = 'NONE') " +
           "AND o.restaurant_id = CAST(:restaurantId AS uuid) " +
           "AND (r.is_deleted IS NULL OR r.is_deleted = false) " +
           "AND (CAST(:startDate AS timestamp) = '1970-01-01 00:00:00'::timestamp OR COALESCE(oi.cancellation_reviewed_at, CAST(oi.updated_at AS timestamp)) >= CAST(:startDate AS timestamp)) " +
           "AND (CAST(:endDate AS timestamp) = '1970-01-01 00:00:00'::timestamp OR COALESCE(oi.cancellation_reviewed_at, CAST(oi.updated_at AS timestamp)) <= CAST(:endDate AS timestamp)) " +
           "UNION ALL " +
           "SELECT " +
           "       oc.id as transaction_id, " +
           "       COALESCE(t.transaction_number, 'N/A') as transaction_number, " +
           "       CAST(COALESCE(oc.cancellation_reviewed_at, CAST(oc.updated_at AS timestamp)) AS timestamp) as date_time, " +
           "       oc.total_combo_amount as amount, " +
           "       COALESCE(t.payment_method, 'N/A') as payment_method, " +
           "       COALESCE(oc.cancellation_request_data ->> 'cancellationReason', o.cancellation_request_data ->> 'cancellationReason', t.request_data ->> 'cancellationReason', NULLIF(t.request_comments, ''), oc.reason, 'N/A') as reason, " +
           "       COALESCE(u1.first_name, u6.first_name, u4.first_name, u3.first_name) as requested_by_first_name, " +
           "       COALESCE(u1.last_name, u6.last_name, u4.last_name, u3.last_name) as requested_by_last_name, " +
           "       COALESCE(u2.first_name, u7.first_name, u5.first_name, u3.first_name) as reviewed_by_first_name, " +
           "       COALESCE(u2.last_name, u7.last_name, u5.last_name, u3.last_name) as reviewed_by_last_name, " +
           "       'combo' as type " +
           "FROM ordered_combo oc " +
           "JOIN orders o ON o.id = oc.order_id " +
           "JOIN restaurant r ON r.id = o.restaurant_id " +
           "LEFT JOIN transaction t ON t.order_id = o.id " +
           "LEFT JOIN users u1 ON u1.id = oc.cancellation_requested_by " +
           "LEFT JOIN users u2 ON u2.id = oc.cancellation_reviewed_by " +
           "LEFT JOIN users u3 ON u3.id = oc.updated_by " +
           "LEFT JOIN users u4 ON u4.id = t.requested_by " +
           "LEFT JOIN users u5 ON u5.id = t.reviewed_by " +
           "LEFT JOIN users u6 ON u6.id = o.cancellation_requested_by " +
           "LEFT JOIN users u7 ON u7.id = o.cancellation_reviewed_by " +
           "WHERE oc.item_status = 'CANCELED' AND (oc.cancellation_request_status = 'APPROVED' OR oc.cancellation_request_status = 'NONE') " +
           "AND o.restaurant_id = CAST(:restaurantId AS uuid) " +
           "AND (r.is_deleted IS NULL OR r.is_deleted = false) " +
           "AND (CAST(:startDate AS timestamp) = '1970-01-01 00:00:00'::timestamp OR COALESCE(oc.cancellation_reviewed_at, CAST(oc.updated_at AS timestamp)) >= CAST(:startDate AS timestamp)) " +
           "AND (CAST(:endDate AS timestamp) = '1970-01-01 00:00:00'::timestamp OR COALESCE(oc.cancellation_reviewed_at, CAST(oc.updated_at AS timestamp)) <= CAST(:endDate AS timestamp)) " +
           "ORDER BY date_time DESC",
            nativeQuery = true)
    List<Object[]> getCancellationReport(
            @Param("restaurantId") UUID restaurantId,
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate);

    /**
     * Get today's sales by payment method for a specific restaurant
     * Returns: [paymentMethod, totalSales]
     * Includes COMPLETED, REFUNDED, and PARTIALLY_REFUNDED transactions for today.
     * For refunded transactions, subtracts the refund amount from the transaction amount.
     * Only subtracts refunds that have been APPROVED by manager AND completed by cashier.
     * Requires both: request_status = 'APPROVED' AND transaction_status IN ('REFUNDED', 'PARTIALLY_REFUNDED').
     * Pending refund requests (requestStatus = 'OPEN') and declined requests (requestStatus = 'DECLINED') are not subtracted.
     */
    @Query(value = "SELECT " +
           "       t.payment_method, " +
           "       COALESCE(SUM(t.transaction_amount - COALESCE(CASE WHEN t.request_status = 'APPROVED' AND t.transaction_status IN ('REFUNDED', 'PARTIALLY_REFUNDED') THEN rf.total_refund_amount ELSE 0 END, 0)), 0) as total_sales " +
           "FROM transaction t " +
           "JOIN restaurant r ON r.id = t.restaurant_id " +
           "LEFT JOIN refund rf ON rf.transaction_id = t.id " +
           "WHERE t.transaction_status IN ('COMPLETED', 'REFUNDED', 'PARTIALLY_REFUNDED') " +
           "AND t.restaurant_id = CAST(:restaurantId AS uuid) " +
           "AND DATE(t.created_at) = CURRENT_DATE " +
           "AND (r.is_deleted IS NULL OR r.is_deleted = false) " +
           "AND t.payment_method IS NOT NULL " +
           "GROUP BY t.payment_method",
           nativeQuery = true)
    List<Object[]> getTodaySalesByPaymentMethod(@Param("restaurantId") UUID restaurantId);

    /**
     * Get sales by payment method for a specific restaurant in a half-open time window
     * {@code [windowStart, windowEndExclusive)} (UTC). Used for today-sales: last completed
     * cashier day (Rule B), ending at the most recent reset instant at or before "now".
     * Only subtracts refunds that have been APPROVED by manager AND completed by cashier.
     * Requires both: request_status = 'APPROVED' AND transaction_status IN ('REFUNDED', 'PARTIALLY_REFUNDED').
     */
    @Query(value = "SELECT " +
           "       t.payment_method, " +
           "       COALESCE(SUM(t.transaction_amount - COALESCE(CASE WHEN t.request_status = 'APPROVED' AND t.transaction_status IN ('REFUNDED', 'PARTIALLY_REFUNDED') THEN rf.total_refund_amount ELSE 0 END, 0)), 0) as total_sales " +
           "FROM transaction t " +
           "JOIN restaurant r ON r.id = t.restaurant_id " +
           "LEFT JOIN refund rf ON rf.transaction_id = t.id " +
           "WHERE t.transaction_status IN ('COMPLETED', 'REFUNDED', 'PARTIALLY_REFUNDED') " +
           "AND t.restaurant_id = CAST(:restaurantId AS uuid) " +
           "AND t.created_at >= COALESCE(CAST(:windowStart AS timestamp), '1970-01-01 00:00:00'::timestamp) " +
           "AND t.created_at < COALESCE(CAST(:windowEndExclusive AS timestamp), '9999-12-31 23:59:59'::timestamp) " +
           "AND (r.is_deleted IS NULL OR r.is_deleted = false) " +
           "AND t.payment_method IS NOT NULL " +
           "GROUP BY t.payment_method",
           nativeQuery = true)
    List<Object[]> getSalesByPaymentMethodInCashierDayWindow(@Param("restaurantId") UUID restaurantId,
                                                             @Param("windowStart") LocalDateTime windowStart,
                                                             @Param("windowEndExclusive") LocalDateTime windowEndExclusive);

}
