package com.gulfnet.shared_library.repository;

import com.gulfnet.shared_library.entity.CashDrawerLog;
import com.gulfnet.shared_library.enums.DrawerEventType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Repository
public interface CashDrawerLogRepository extends JpaRepository<CashDrawerLog, UUID> {

    /**
     * Find all logs for a shift
     */
    List<CashDrawerLog> findByShiftIdOrderByCreatedAtAsc(UUID shiftId);

    /**
     * Find logs for a shift with pagination
     */
    Page<CashDrawerLog> findByShiftIdOrderByCreatedAtAsc(UUID shiftId, Pageable pageable);

    /**
     * Find logs for a shift ordered by creation time descending (latest first)
     */
    List<CashDrawerLog> findByShiftIdOrderByCreatedAtDesc(UUID shiftId);

    /**
     * Find logs for a shift with pagination ordered by creation time descending (latest first)
     */
    Page<CashDrawerLog> findByShiftIdOrderByCreatedAtDesc(UUID shiftId, Pageable pageable);

    /**
     * Find logs by event type for a shift
     */
    List<CashDrawerLog> findByShiftIdAndEventTypeOrderByCreatedAtAsc(UUID shiftId, DrawerEventType eventType);

    /**
     * Find logs by event type for a shift ordered by creation time descending (latest first)
     */
    List<CashDrawerLog> findByShiftIdAndEventTypeOrderByCreatedAtDesc(UUID shiftId, DrawerEventType eventType);

    /**
     * Find logs for a drawer
     */
    List<CashDrawerLog> findByDrawerIdOrderByCreatedAtDesc(UUID drawerId);

    /**
     * Find logs for a drawer with pagination
     */
    Page<CashDrawerLog> findByDrawerIdOrderByCreatedAtDesc(UUID drawerId, Pageable pageable);

    /**
     * Find logs by user
     */
    List<CashDrawerLog> findByUserIdOrderByCreatedAtDesc(UUID userId);

    /**
     * Find logs by user with pagination
     */
    Page<CashDrawerLog> findByUserIdOrderByCreatedAtDesc(UUID userId, Pageable pageable);

    /**
     * Find logs by event type
     */
    List<CashDrawerLog> findByEventTypeOrderByCreatedAtDesc(DrawerEventType eventType);

    /**
     * Find logs by date range
     */
    @Query("SELECT cdl FROM CashDrawerLog cdl WHERE cdl.createdAt BETWEEN :startDate AND :endDate ORDER BY cdl.createdAt DESC")
    List<CashDrawerLog> findByDateRange(
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate
    );

    /**
     * Find logs by date range with pagination
     */
    @Query("SELECT cdl FROM CashDrawerLog cdl WHERE cdl.createdAt BETWEEN :startDate AND :endDate ORDER BY cdl.createdAt DESC")
    Page<CashDrawerLog> findByDateRange(
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate,
            Pageable pageable
    );

    /**
     * Find logs for a shift by event type
     */
    @Query("SELECT cdl FROM CashDrawerLog cdl WHERE cdl.shift.id = :shiftId AND cdl.eventType IN :eventTypes ORDER BY cdl.createdAt ASC")
    List<CashDrawerLog> findByShiftIdAndEventTypes(
            @Param("shiftId") UUID shiftId,
            @Param("eventTypes") List<DrawerEventType> eventTypes
    );

    /**
     * Calculate total inflow for a shift (SALE_INFLOW + MANUAL_DEPOSIT)
     */
    @Query("SELECT COALESCE(SUM(cdl.amount), 0) FROM CashDrawerLog cdl WHERE cdl.shift.id = :shiftId AND cdl.eventType IN ('SALE_INFLOW', 'MANUAL_DEPOSIT', 'OPENING_BALANCE')")
    BigDecimal calculateTotalInflowForShift(@Param("shiftId") UUID shiftId);

    /**
     * Calculate total outflow for a shift (SALE_REFUND + MANUAL_WITHDRAWAL)
     */
    @Query("SELECT COALESCE(SUM(ABS(cdl.amount)), 0) FROM CashDrawerLog cdl WHERE cdl.shift.id = :shiftId AND cdl.eventType IN ('SALE_REFUND', 'MANUAL_WITHDRAWAL')")
    BigDecimal calculateTotalOutflowForShift(@Param("shiftId") UUID shiftId);

    /**
     * Find logs linked to a transaction
     */
    List<CashDrawerLog> findByTransactionIdOrderByCreatedAtDesc(UUID transactionId);

    /**
     * Find logs linked to a refund
     */
    List<CashDrawerLog> findByRefundIdOrderByCreatedAtDesc(UUID refundId);

    /**
     * Find logs for a restaurant
     */
    @Query("SELECT cdl FROM CashDrawerLog cdl WHERE cdl.drawer.restaurant.id = :restaurantId ORDER BY cdl.createdAt DESC")
    List<CashDrawerLog> findByRestaurantId(@Param("restaurantId") UUID restaurantId);

    /**
     * Find logs for a restaurant with pagination
     */
    @Query("SELECT cdl FROM CashDrawerLog cdl WHERE cdl.drawer.restaurant.id = :restaurantId ORDER BY cdl.createdAt DESC")
    Page<CashDrawerLog> findByRestaurantId(@Param("restaurantId") UUID restaurantId, Pageable pageable);

    /**
     * Get cash drawer reconciliation summary for a restaurant.
     *
     * Returns:
     * [openingBalance,
     *  totalCashSales,
     *  totalCashRefundsPaid,
     *  totalCashRefundsReceived,
     *  cashWithdrawal,
     *  totalDeposits,
     *  totalActualFlow,
     *  cashSalesGrossIn,
     *  cashSalesGrossOut,
     *  cashRefundsGrossOut,
     *  cashRefundsGrossIn]
     * Filters by specific restaurantId only
     * Uses sentinel date '1970-01-01 00:00:00' to handle null date parameters
     *
     * Notes:
     * - Opening balance sums all opening balances from shifts that started within the period.
     *   Opening balances are filtered by shift start date (cs.started_at), not log creation date.
     * - If only 1 opening balance exists in the period, it will be summed (resulting in that single value).
     * - Deposits are treated as additional inflows into the drawer (separate from sales).
     * - Total cash sales = SALE_INFLOW amounts minus SALE_REFUND amounts for transactions that had cash sales.
     *   This ensures refunds for cash transactions are subtracted from total sales.
     * - Expected cash balance is computed in the service as:
     *     openingBalance
     *       + totalCashSales
     *       + totalDeposits
     *       - totalCashRefundsPaid
     *       + totalCashRefundsReceived
     *       - cashWithdrawal
     */
    @Query(value = "SELECT " +
           "       COALESCE((" +
           "           SELECT SUM(cdl2.amount) " +
           "           FROM cash_drawer_logs cdl2 " +
           "           JOIN cash_drawers cd2 ON cd2.id = cdl2.drawer_id " +
           "           JOIN cashier_shift cs ON cs.id = cdl2.shift_id " +
           "           WHERE cd2.restaurant_id = CAST(:restaurantId AS uuid) " +
           "           AND cdl2.event_type = 'OPENING_BALANCE' " +
           "           AND (CAST(:startDate AS timestamp) = '1970-01-01 00:00:00'::timestamp OR cs.started_at >= CAST(:startDate AS timestamp)) " +
           "           AND (CAST(:endDate AS timestamp) = '1970-01-01 00:00:00'::timestamp OR cs.started_at <= CAST(:endDate AS timestamp)) " +
           "       ), 0) as opening_balance, " +
           "       COALESCE((" +
           "           SELECT SUM(CASE WHEN cdl.event_type = 'SALE_INFLOW' THEN COALESCE(cdl.expected_amount, cdl.amount) ELSE 0 END) " +
           "           FROM cash_drawer_logs cdl " +
           "           JOIN cash_drawers cd ON cd.id = cdl.drawer_id " +
           "           WHERE cd.restaurant_id = CAST(:restaurantId AS uuid) " +
           "           AND (CAST(:startDate AS timestamp) = '1970-01-01 00:00:00'::timestamp OR cdl.created_at >= CAST(:startDate AS timestamp)) " +
           "           AND (CAST(:endDate AS timestamp) = '1970-01-01 00:00:00'::timestamp OR cdl.created_at <= CAST(:endDate AS timestamp)) " +
           "       ) - COALESCE((" +
           "           SELECT SUM(ABS(COALESCE(cdl_refund.expected_amount, cdl_refund.amount))) " +
           "           FROM cash_drawer_logs cdl_refund " +
           "           JOIN cash_drawers cd_refund ON cd_refund.id = cdl_refund.drawer_id " +
           "           JOIN refund r ON r.id = cdl_refund.refund_id " +
           "           WHERE cd_refund.restaurant_id = CAST(:restaurantId AS uuid) " +
           "           AND cdl_refund.event_type = 'SALE_REFUND' " +
           "           AND r.transaction_id IN (" +
           "               SELECT DISTINCT cdl_inflow.transaction_id " +
           "               FROM cash_drawer_logs cdl_inflow " +
           "               JOIN cash_drawers cd_inflow ON cd_inflow.id = cdl_inflow.drawer_id " +
           "               WHERE cd_inflow.restaurant_id = CAST(:restaurantId AS uuid) " +
           "               AND cdl_inflow.event_type = 'SALE_INFLOW' " +
           "               AND cdl_inflow.transaction_id IS NOT NULL " +
           "               AND (CAST(:startDate AS timestamp) = '1970-01-01 00:00:00'::timestamp OR cdl_inflow.created_at >= CAST(:startDate AS timestamp)) " +
           "               AND (CAST(:endDate AS timestamp) = '1970-01-01 00:00:00'::timestamp OR cdl_inflow.created_at <= CAST(:endDate AS timestamp)) " +
           "           ) " +
           "           AND (CAST(:startDate AS timestamp) = '1970-01-01 00:00:00'::timestamp OR cdl_refund.created_at >= CAST(:startDate AS timestamp)) " +
           "           AND (CAST(:endDate AS timestamp) = '1970-01-01 00:00:00'::timestamp OR cdl_refund.created_at <= CAST(:endDate AS timestamp)) " +
           "       ), 0), 0) as total_cash_sales, " +
           "       COALESCE((SELECT SUM(CASE WHEN cdl.event_type = 'SALE_REFUND' AND COALESCE(cdl.expected_amount, cdl.amount) < 0 THEN ABS(COALESCE(cdl.expected_amount, cdl.amount)) ELSE 0 END) " +
           "                  FROM cash_drawer_logs cdl " +
           "                  JOIN cash_drawers cd ON cd.id = cdl.drawer_id " +
           "                  WHERE cd.restaurant_id = CAST(:restaurantId AS uuid) " +
           "                  AND (CAST(:startDate AS timestamp) = '1970-01-01 00:00:00'::timestamp OR cdl.created_at >= CAST(:startDate AS timestamp)) " +
           "                  AND (CAST(:endDate AS timestamp) = '1970-01-01 00:00:00'::timestamp OR cdl.created_at <= CAST(:endDate AS timestamp))), 0) as total_cash_refunds_paid, " +
           "       COALESCE((SELECT SUM(CASE WHEN cdl.event_type = 'SALE_REFUND' AND COALESCE(cdl.expected_amount, cdl.amount) > 0 THEN COALESCE(cdl.expected_amount, cdl.amount) ELSE 0 END) " +
           "                  FROM cash_drawer_logs cdl " +
           "                  JOIN cash_drawers cd ON cd.id = cdl.drawer_id " +
           "                  WHERE cd.restaurant_id = CAST(:restaurantId AS uuid) " +
           "                  AND (CAST(:startDate AS timestamp) = '1970-01-01 00:00:00'::timestamp OR cdl.created_at >= CAST(:startDate AS timestamp)) " +
           "                  AND (CAST(:endDate AS timestamp) = '1970-01-01 00:00:00'::timestamp OR cdl.created_at <= CAST(:endDate AS timestamp))), 0) as total_cash_refunds_received, " +
           "       COALESCE((SELECT SUM(CASE WHEN cdl.event_type = 'MANUAL_WITHDRAWAL' THEN ABS(cdl.amount) ELSE 0 END) " +
           "                  FROM cash_drawer_logs cdl " +
           "                  JOIN cash_drawers cd ON cd.id = cdl.drawer_id " +
           "                  WHERE cd.restaurant_id = CAST(:restaurantId AS uuid) " +
           "                  AND (CAST(:startDate AS timestamp) = '1970-01-01 00:00:00'::timestamp OR cdl.created_at >= CAST(:startDate AS timestamp)) " +
           "                  AND (CAST(:endDate AS timestamp) = '1970-01-01 00:00:00'::timestamp OR cdl.created_at <= CAST(:endDate AS timestamp))), 0) as cash_withdrawal, " +
           "       COALESCE((SELECT SUM(CASE WHEN cdl.event_type = 'MANUAL_DEPOSIT' THEN cdl.amount ELSE 0 END) " +
           "                  FROM cash_drawer_logs cdl " +
           "                  JOIN cash_drawers cd ON cd.id = cdl.drawer_id " +
           "                  WHERE cd.restaurant_id = CAST(:restaurantId AS uuid) " +
           "                  AND (CAST(:startDate AS timestamp) = '1970-01-01 00:00:00'::timestamp OR cdl.created_at >= CAST(:startDate AS timestamp)) " +
           "                  AND (CAST(:endDate AS timestamp) = '1970-01-01 00:00:00'::timestamp OR cdl.created_at <= CAST(:endDate AS timestamp))), 0) as total_deposits, " +
           "       COALESCE((SELECT SUM(CASE WHEN cdl.event_type NOT IN ('OPENING_BALANCE', 'CLOSING_BALANCE') THEN cdl.amount ELSE 0 END) " +
           "                  FROM cash_drawer_logs cdl " +
           "                  JOIN cash_drawers cd ON cd.id = cdl.drawer_id " +
           "                  WHERE cd.restaurant_id = CAST(:restaurantId AS uuid) " +
           "                  AND (CAST(:startDate AS timestamp) = '1970-01-01 00:00:00'::timestamp OR cdl.created_at >= CAST(:startDate AS timestamp)) " +
           "                  AND (CAST(:endDate AS timestamp) = '1970-01-01 00:00:00'::timestamp OR cdl.created_at <= CAST(:endDate AS timestamp))), 0) as total_actual_flow, " +
           "       COALESCE((SELECT SUM(CASE WHEN cdl.event_type = 'SALE_INFLOW' THEN COALESCE(cdl.gross_in, 0) ELSE 0 END) " +
           "                  FROM cash_drawer_logs cdl " +
           "                  JOIN cash_drawers cd ON cd.id = cdl.drawer_id " +
           "                  WHERE cd.restaurant_id = CAST(:restaurantId AS uuid) " +
           "                  AND (CAST(:startDate AS timestamp) = '1970-01-01 00:00:00'::timestamp OR cdl.created_at >= CAST(:startDate AS timestamp)) " +
           "                  AND (CAST(:endDate AS timestamp) = '1970-01-01 00:00:00'::timestamp OR cdl.created_at <= CAST(:endDate AS timestamp))), 0) as cash_sales_gross_in, " +
           "       COALESCE((SELECT SUM(CASE WHEN cdl.event_type = 'SALE_INFLOW' THEN COALESCE(cdl.gross_out, 0) ELSE 0 END) " +
           "                  FROM cash_drawer_logs cdl " +
           "                  JOIN cash_drawers cd ON cd.id = cdl.drawer_id " +
           "                  WHERE cd.restaurant_id = CAST(:restaurantId AS uuid) " +
           "                  AND (CAST(:startDate AS timestamp) = '1970-01-01 00:00:00'::timestamp OR cdl.created_at >= CAST(:startDate AS timestamp)) " +
           "                  AND (CAST(:endDate AS timestamp) = '1970-01-01 00:00:00'::timestamp OR cdl.created_at <= CAST(:endDate AS timestamp))), 0) as cash_sales_gross_out, " +
           "       COALESCE((SELECT SUM(CASE WHEN cdl.event_type = 'SALE_REFUND' THEN COALESCE(cdl.gross_out, 0) ELSE 0 END) " +
           "                  FROM cash_drawer_logs cdl " +
           "                  JOIN cash_drawers cd ON cd.id = cdl.drawer_id " +
           "                  WHERE cd.restaurant_id = CAST(:restaurantId AS uuid) " +
           "                  AND (CAST(:startDate AS timestamp) = '1970-01-01 00:00:00'::timestamp OR cdl.created_at >= CAST(:startDate AS timestamp)) " +
           "                  AND (CAST(:endDate AS timestamp) = '1970-01-01 00:00:00'::timestamp OR cdl.created_at <= CAST(:endDate AS timestamp))), 0) as cash_refunds_gross_out, " +
           "       COALESCE((SELECT SUM(CASE WHEN cdl.event_type = 'SALE_REFUND' THEN COALESCE(cdl.gross_in, 0) ELSE 0 END) " +
           "                  FROM cash_drawer_logs cdl " +
           "                  JOIN cash_drawers cd ON cd.id = cdl.drawer_id " +
           "                  WHERE cd.restaurant_id = CAST(:restaurantId AS uuid) " +
           "                  AND (CAST(:startDate AS timestamp) = '1970-01-01 00:00:00'::timestamp OR cdl.created_at >= CAST(:startDate AS timestamp)) " +
           "                  AND (CAST(:endDate AS timestamp) = '1970-01-01 00:00:00'::timestamp OR cdl.created_at <= CAST(:endDate AS timestamp))), 0) as cash_refunds_gross_in " +
           "FROM (SELECT 1) AS dummy",
           nativeQuery = true)
    List<Object[]> getCashDrawerReconciliationSummary(
            @Param("restaurantId") UUID restaurantId,
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate);

    /**
     * Get sum of ADJUSTMENT_APPROVED amounts for a restaurant within a date range
     */
    @Query(value = "SELECT COALESCE(SUM(cdl.amount), 0) FROM cash_drawer_logs cdl " +
           "JOIN cash_drawers cd ON cd.id = cdl.drawer_id " +
           "WHERE cd.restaurant_id = CAST(:restaurantId AS uuid) " +
           "AND cdl.event_type = 'ADJUSTMENT_APPROVED' " +
           "AND (CAST(:startDate AS timestamp) = '1970-01-01 00:00:00'::timestamp OR cdl.created_at >= CAST(:startDate AS timestamp)) " +
           "AND (CAST(:endDate AS timestamp) = '1970-01-01 00:00:00'::timestamp OR cdl.created_at <= CAST(:endDate AS timestamp))",
           nativeQuery = true)
    BigDecimal getSumOfAdjustmentApproved(
            @Param("restaurantId") UUID restaurantId,
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate);

    /**
     * Get sum of ADJUSTMENT_REJECTED amounts for a restaurant within a date range
     */
    @Query(value = "SELECT COALESCE(SUM(cdl.amount), 0) FROM cash_drawer_logs cdl " +
           "JOIN cash_drawers cd ON cd.id = cdl.drawer_id " +
           "WHERE cd.restaurant_id = CAST(:restaurantId AS uuid) " +
           "AND cdl.event_type = 'ADJUSTMENT_REJECTED' " +
           "AND (CAST(:startDate AS timestamp) = '1970-01-01 00:00:00'::timestamp OR cdl.created_at >= CAST(:startDate AS timestamp)) " +
           "AND (CAST(:endDate AS timestamp) = '1970-01-01 00:00:00'::timestamp OR cdl.created_at <= CAST(:endDate AS timestamp))",
           nativeQuery = true)
    BigDecimal getSumOfAdjustmentRejected(
            @Param("restaurantId") UUID restaurantId,
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate);

    /**
     * Get sum of ADJUSTMENT_PENDING amounts from shifts that only have pending (no approved/rejected)
     * Only counts shifts that have ADJUSTMENT_PENDING but do NOT have ADJUSTMENT_APPROVED or ADJUSTMENT_REJECTED
     */
    @Query(value = "SELECT COALESCE(SUM(cdl.amount), 0) FROM cash_drawer_logs cdl " +
           "JOIN cash_drawers cd ON cd.id = cdl.drawer_id " +
           "WHERE cd.restaurant_id = CAST(:restaurantId AS uuid) " +
           "AND cdl.event_type = 'ADJUSTMENT_PENDING' " +
           "AND (CAST(:startDate AS timestamp) = '1970-01-01 00:00:00'::timestamp OR cdl.created_at >= CAST(:startDate AS timestamp)) " +
           "AND (CAST(:endDate AS timestamp) = '1970-01-01 00:00:00'::timestamp OR cdl.created_at <= CAST(:endDate AS timestamp)) " +
           "AND NOT EXISTS (" +
           "    SELECT 1 FROM cash_drawer_logs cdl2 " +
           "    WHERE cdl2.shift_id = cdl.shift_id " +
           "    AND cdl2.event_type IN ('ADJUSTMENT_APPROVED', 'ADJUSTMENT_REJECTED')" +
           ")",
           nativeQuery = true)
    BigDecimal getSumOfAdjustmentPending(
            @Param("restaurantId") UUID restaurantId,
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate);
}

