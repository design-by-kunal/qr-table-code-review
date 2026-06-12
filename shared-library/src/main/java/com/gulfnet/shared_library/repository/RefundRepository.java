package com.gulfnet.shared_library.repository;

import com.gulfnet.shared_library.entity.Refund;
import com.gulfnet.shared_library.enums.RequestStatus;
import com.gulfnet.shared_library.enums.TransactionStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface RefundRepository extends JpaRepository<Refund, UUID> {
    
    /**
     * Find refund by transaction ID
     */
    Optional<Refund> findByTransactionId(UUID transactionId);

    /**
     * Batch fetch refunds for multiple transactions.
     * Used by session/table order listing to avoid per-order refund N+1 queries.
     */
    @Query("SELECT r FROM Refund r LEFT JOIN FETCH r.transaction t WHERE t.id IN :transactionIds")
    List<Refund> findByTransactionIds(@Param("transactionIds") Collection<UUID> transactionIds);
    
    /**
     * Find refunds by transaction request status (request status is now in Transaction entity)
     */
    @Query("SELECT r FROM Refund r JOIN r.transaction t " +
           "WHERE t.requestStatus = :requestStatus")
    Page<Refund> findByRequestStatus(@Param("requestStatus") RequestStatus status, Pageable pageable);
    
    /**
     * Find refunds by transaction request status and transaction status
     */
    @Query("SELECT r FROM Refund r JOIN r.transaction t " +
           "WHERE t.requestStatus = :requestStatus " +
           "AND t.transactionStatus = :transactionStatus")
    Page<Refund> findByRequestStatusAndTransactionStatus(
            @Param("requestStatus") RequestStatus requestStatus,
            @Param("transactionStatus") TransactionStatus transactionStatus,
            Pageable pageable);
    
    /**
     * Find refunds by transaction request status and transaction status, with optional status filter.
     * If requestStatus is null, returns all refunds with transaction request status != NONE.
     */
    @Query("SELECT r FROM Refund r JOIN r.transaction t " +
           "WHERE ((:requestStatus IS NULL AND t.requestStatus != com.gulfnet.shared_library.enums.RequestStatus.NONE) OR " +
           "       (:requestStatus IS NOT NULL AND t.requestStatus = :requestStatus)) " +
           "AND t.transactionStatus = :transactionStatus")
    Page<Refund> findByRequestStatusAndTransactionStatusOptional(
            @Param("requestStatus") RequestStatus requestStatus,
            @Param("transactionStatus") TransactionStatus transactionStatus,
            Pageable pageable);

    /**
     * Get chargeback report (using refunds as chargebacks)
     * Returns: [transactionId, transactionNumber, refundCreatedAt, totalRefundAmount, paymentMethod, refundReason, bankStatus]
     * Filters by specific restaurantId only
     * Uses sentinel date '1970-01-01 00:00:00' to handle null date parameters
     */
    @Query(value = "SELECT " +
           "       t.id as transaction_id, " +
           "       t.transaction_number, " +
           "       r.created_at, " +
           "       r.total_refund_amount, " +
           "       t.payment_method, " +
           "       COALESCE(r.refund_reason, 'N/A') as reason, " +
           "       CASE WHEN r.completed_at IS NOT NULL THEN 'Completed' ELSE 'Pending' END as bank_status " +
           "FROM refund r " +
           "JOIN transaction t ON t.id = r.transaction_id " +
           "JOIN restaurant res ON res.id = t.restaurant_id " +
           "WHERE t.restaurant_id = CAST(:restaurantId AS uuid) " +
           "AND (res.is_deleted IS NULL OR res.is_deleted = false) " +
           "AND (CAST(:startDate AS timestamp) = '1970-01-01 00:00:00'::timestamp OR r.created_at >= CAST(:startDate AS timestamp)) " +
           "AND (CAST(:endDate AS timestamp) = '1970-01-01 00:00:00'::timestamp OR r.created_at <= CAST(:endDate AS timestamp)) " +
           "ORDER BY r.created_at DESC",
           nativeQuery = true)
    List<Object[]> getChargebackReport(
            @Param("restaurantId") UUID restaurantId,
            @Param("startDate") java.time.LocalDateTime startDate,
            @Param("endDate") java.time.LocalDateTime endDate);

    /**
     * Get today's approved refund amounts for a specific restaurant
     * Returns: List of totalRefundAmount values
     * Only includes refunds that have been APPROVED by manager AND completed by cashier.
     * Requires both: request_status = 'APPROVED' AND transaction_status IN ('REFUNDED', 'PARTIALLY_REFUNDED').
     * Includes both FULL and PARTIAL refunds.
     * Only includes refunds for transactions created today.
     */
    @Query(value = "SELECT " +
           "       r.total_refund_amount " +
           "FROM refund r " +
           "JOIN transaction t ON t.id = r.transaction_id " +
           "JOIN restaurant res ON res.id = t.restaurant_id " +
           "WHERE t.request_status = 'APPROVED' " +
           "AND t.transaction_status IN ('REFUNDED', 'PARTIALLY_REFUNDED') " +
           "AND t.restaurant_id = CAST(:restaurantId AS uuid) " +
           "AND DATE(t.created_at) = CURRENT_DATE " +
           "AND (res.is_deleted IS NULL OR res.is_deleted = false) " +
           "AND r.total_refund_amount IS NOT NULL",
           nativeQuery = true)
    List<BigDecimal> getTodayApprovedRefundAmounts(@Param("restaurantId") UUID restaurantId);

    /**
     * Get approved refund amounts for a specific restaurant in a half-open window
     * {@code [windowStart, windowEndExclusive)} (UTC). Matches today-sales cashier day (Rule B).
     * Only includes refunds that have been APPROVED by manager AND completed by cashier.
     * Requires both: request_status = 'APPROVED' AND transaction_status IN ('REFUNDED', 'PARTIALLY_REFUNDED').
     */
    @Query(value = "SELECT " +
           "       r.total_refund_amount " +
           "FROM refund r " +
           "JOIN transaction t ON t.id = r.transaction_id " +
           "JOIN restaurant res ON res.id = t.restaurant_id " +
           "WHERE t.request_status = 'APPROVED' " +
           "AND t.transaction_status IN ('REFUNDED', 'PARTIALLY_REFUNDED') " +
           "AND t.restaurant_id = CAST(:restaurantId AS uuid) " +
           "AND t.created_at >= COALESCE(CAST(:windowStart AS timestamp), '1970-01-01 00:00:00'::timestamp) " +
           "AND t.created_at < COALESCE(CAST(:windowEndExclusive AS timestamp), '9999-12-31 23:59:59'::timestamp) " +
           "AND (res.is_deleted IS NULL OR res.is_deleted = false) " +
           "AND r.total_refund_amount IS NOT NULL",
           nativeQuery = true)
    List<BigDecimal> getApprovedRefundAmountsInCashierDayWindow(@Param("restaurantId") UUID restaurantId,
                                                                @Param("windowStart") LocalDateTime windowStart,
                                                                @Param("windowEndExclusive") LocalDateTime windowEndExclusive);

    /**
     * Sum total refund amounts for a specific restaurant (cashier-completed refunds only).
     * Uses sentinel date '1970-01-01 00:00:00' to handle null date parameters
     * 
     * IMPORTANT: Filters by {@code r.completed_at IS NOT NULL} so HQ dashboard and refund-% alerts
     * exclude approved-but-not-yet-processed refunds (transaction often remains COMPLETED until completion).
     * Filters by transaction status IN ('COMPLETED', 'REFUNDED', 'PARTIALLY_REFUNDED')
     * to be consistent with getDailySalesSummary. This prevents inflated refund percentages
     * when transactions are cancelled (their sales are excluded from the denominator, so
     * refunds on cancelled transactions should also be excluded from the numerator).
     * - Uses updated_at for date filtering to correctly handle UPI and other PREPAID transactions
     *   that are created as PENDING and later updated to COMPLETED. For these transactions,
     *   updated_at represents the completion date, which is the correct business date.
     * Date bounds use AT TIME ZONE 'UTC' (same as getDailySalesSummary / OrderRepository alert queries).
     */
    @Query(value = "SELECT COALESCE(SUM(r.total_refund_amount), 0) " +
           "FROM refund r " +
           "JOIN transaction t ON t.id = r.transaction_id " +
           "JOIN restaurant res ON res.id = t.restaurant_id " +
           "WHERE t.restaurant_id = CAST(:restaurantId AS uuid) " +
           "AND t.transaction_status IN ('COMPLETED', 'REFUNDED', 'PARTIALLY_REFUNDED') " +
           "AND r.completed_at IS NOT NULL " +
           "AND (res.is_deleted IS NULL OR res.is_deleted = false) " +
           "AND r.total_refund_amount IS NOT NULL " +
           "AND (CAST(:startDate AS timestamp) = '1970-01-01 00:00:00'::timestamp OR t.updated_at >= (CAST(:startDate AS timestamp) AT TIME ZONE 'UTC')) " +
           "AND (CAST(:endDate AS timestamp) = '1970-01-01 00:00:00'::timestamp OR t.updated_at <= (CAST(:endDate AS timestamp) AT TIME ZONE 'UTC'))",
           nativeQuery = true)
    BigDecimal sumTotalRefundAmountByRestaurantId(
            @Param("restaurantId") UUID restaurantId,
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate);

    /**
     * Sum total refund amounts for a restaurant group (cashier-completed refunds only).
     * Uses sentinel date '1970-01-01 00:00:00' to handle null date parameters
     * - Same eligibility as {@link #sumTotalRefundAmountByRestaurantId}: {@code r.completed_at IS NOT NULL}
     *   and transaction status IN ('COMPLETED', 'REFUNDED', 'PARTIALLY_REFUNDED').
     * - Uses updated_at for date filtering to correctly handle UPI and other PREPAID transactions
     *   that are created as PENDING and later updated to COMPLETED. For these transactions,
     *   updated_at represents the completion date, which is the correct business date.
     */
    @Query(value = "SELECT COALESCE(SUM(r.total_refund_amount), 0) " +
           "FROM refund r " +
           "JOIN transaction t ON t.id = r.transaction_id " +
           "JOIN restaurant res ON res.id = t.restaurant_id " +
           "WHERE res.restaurant_group_id = CAST(:restaurantGroupId AS uuid) " +
           "AND t.transaction_status IN ('COMPLETED', 'REFUNDED', 'PARTIALLY_REFUNDED') " +
           "AND r.completed_at IS NOT NULL " +
           "AND (res.is_deleted IS NULL OR res.is_deleted = false) " +
           "AND r.total_refund_amount IS NOT NULL " +
           "AND (CAST(:startDate AS timestamp) = '1970-01-01 00:00:00'::timestamp OR t.updated_at >= (CAST(:startDate AS timestamp) AT TIME ZONE 'UTC')) " +
           "AND (CAST(:endDate AS timestamp) = '1970-01-01 00:00:00'::timestamp OR t.updated_at <= (CAST(:endDate AS timestamp) AT TIME ZONE 'UTC'))",
           nativeQuery = true)
    BigDecimal sumTotalRefundAmountByRestaurantGroupId(
            @Param("restaurantGroupId") UUID restaurantGroupId,
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate);

    /**
     * Sum total refund amounts for all restaurants (cashier-completed refunds only).
     * Uses sentinel date '1970-01-01 00:00:00' to handle null date parameters
     * Same eligibility as {@link #sumTotalRefundAmountByRestaurantId} for numerator consistency at HQ level.
     */
    @Query(value = "SELECT COALESCE(SUM(r.total_refund_amount), 0) " +
           "FROM refund r " +
           "JOIN transaction t ON t.id = r.transaction_id " +
           "JOIN restaurant res ON res.id = t.restaurant_id " +
           "WHERE (res.is_deleted IS NULL OR res.is_deleted = false) " +
           "AND t.transaction_status IN ('COMPLETED', 'REFUNDED', 'PARTIALLY_REFUNDED') " +
           "AND r.completed_at IS NOT NULL " +
           "AND r.total_refund_amount IS NOT NULL " +
           "AND (CAST(:startDate AS timestamp) = '1970-01-01 00:00:00'::timestamp OR t.created_at >= CAST(:startDate AS timestamp)) " +
           "AND (CAST(:endDate AS timestamp) = '1970-01-01 00:00:00'::timestamp OR t.created_at <= CAST(:endDate AS timestamp))",
           nativeQuery = true)
    BigDecimal sumTotalRefundAmount(
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate);
}

