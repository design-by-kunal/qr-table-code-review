package com.gulfnet.shared_library.repository;

import com.gulfnet.shared_library.entity.CashierShift;
import com.gulfnet.shared_library.enums.ShiftStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
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
public interface CashierShiftRepository extends JpaRepository<CashierShift, UUID> {

    /**
     * Find active shift for a cashier
     */
    @Query("SELECT cs FROM CashierShift cs WHERE cs.cashier.id = :cashierId AND cs.status = 'OPEN'")
    Optional<CashierShift> findActiveShiftByCashierId(@Param("cashierId") UUID cashierId);

    /**
     * Find active shift for a cashier on a specific drawer
     */
    @Query("SELECT cs FROM CashierShift cs WHERE cs.cashier.id = :cashierId AND cs.cashDrawer.id = :drawerId AND cs.status = 'OPEN'")
    Optional<CashierShift> findActiveShiftByCashierIdAndDrawerId(@Param("cashierId") UUID cashierId, @Param("drawerId") UUID drawerId);

    /**
     * Find all shifts for a cashier
     */
    List<CashierShift> findByCashierIdOrderByStartedAtDesc(UUID cashierId);

    /**
     * Find all shifts for a restaurant
     */
    List<CashierShift> findByRestaurantIdOrderByStartedAtDesc(UUID restaurantId);

    /**
     * Find shifts for a cashier with pagination
     */
    Page<CashierShift> findByCashierIdOrderByStartedAtDesc(UUID cashierId, Pageable pageable);

    /**
     * Find shifts for a restaurant with pagination
     */
    Page<CashierShift> findByRestaurantIdOrderByStartedAtDesc(UUID restaurantId, Pageable pageable);

    /**
     * Find shifts by status
     */
    List<CashierShift> findByStatusOrderByStartedAtDesc(ShiftStatus status);

    /**
     * Find shifts by status with pagination
     */
    Page<CashierShift> findByStatusOrderByStartedAtDesc(ShiftStatus status, Pageable pageable);

    /**
     * Find shifts for a restaurant filtered by a collection of statuses with pagination.
     * Used for manager cashier shift listing where we group several internal statuses
     * (e.g. CLOSED and APPROVED) under a single "Closed" bucket for the UI.
     */
    @Query("SELECT cs FROM CashierShift cs " +
            "WHERE cs.restaurant.id = :restaurantId " +
            "AND cs.status IN :statuses " +
            "ORDER BY cs.startedAt DESC")
    Page<CashierShift> findByRestaurantIdAndStatuses(
            @Param("restaurantId") UUID restaurantId,
            @Param("statuses") Collection<ShiftStatus> statuses,
            Pageable pageable
    );

    /**
     * Find shifts for a restaurant filtered by statuses and a startedAt date range with pagination.
     */
    @Query("SELECT cs FROM CashierShift cs " +
            "WHERE cs.restaurant.id = :restaurantId " +
            "AND cs.status IN :statuses " +
            "AND cs.startedAt BETWEEN :startDate AND :endDate " +
            "ORDER BY cs.startedAt DESC")
    Page<CashierShift> findByRestaurantIdAndStatusesAndDateRange(
            @Param("restaurantId") UUID restaurantId,
            @Param("statuses") Collection<ShiftStatus> statuses,
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate,
            Pageable pageable
    );

    /**
     * Find shifts pending approval for a restaurant
     */
    @Query("SELECT cs FROM CashierShift cs WHERE cs.restaurant.id = :restaurantId AND cs.status = 'PENDING_APPROVAL' ORDER BY cs.closedAt DESC")
    List<CashierShift> findPendingApprovalByRestaurantId(@Param("restaurantId") UUID restaurantId);

    /**
     * Find shifts with discrepancy statuses (PENDING_APPROVAL, APPROVED, REJECTED) for a restaurant
     * Used for manager request list to show all shift discrepancy requests
     * Only returns shifts that have a discrepancy_reason (actual discrepancy requests)
     */
    @Query("SELECT cs FROM CashierShift cs WHERE cs.restaurant.id = :restaurantId AND cs.status IN ('PENDING_APPROVAL', 'APPROVED', 'REJECTED') AND cs.discrepancyReason IS NOT NULL ORDER BY cs.closedAt DESC")
    List<CashierShift> findShiftDiscrepancyRequestsByRestaurantId(@Param("restaurantId") UUID restaurantId);

    /**
     * Find shifts by date range
     */
    @Query("SELECT cs FROM CashierShift cs WHERE cs.restaurant.id = :restaurantId AND cs.startedAt BETWEEN :startDate AND :endDate ORDER BY cs.startedAt DESC")
    List<CashierShift> findByRestaurantIdAndDateRange(
            @Param("restaurantId") UUID restaurantId,
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate
    );

    /**
     * Find shifts by date range with pagination
     */
    @Query("SELECT cs FROM CashierShift cs WHERE cs.restaurant.id = :restaurantId AND cs.startedAt BETWEEN :startDate AND :endDate ORDER BY cs.startedAt DESC")
    Page<CashierShift> findByRestaurantIdAndDateRange(
            @Param("restaurantId") UUID restaurantId,
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate,
            Pageable pageable
    );

    /**
     * Find shifts for a drawer
     */
    List<CashierShift> findByCashDrawerIdOrderByStartedAtDesc(UUID drawerId);

    /**
     * Count shifts for a drawer (used to block hard delete when count is positive)
     */
    @Query("SELECT COUNT(cs) FROM CashierShift cs WHERE cs.cashDrawer.id = :drawerId")
    long countByCashDrawerId(@Param("drawerId") UUID drawerId);

    /**
     * Check if cashier has active shift
     */
    @Query("SELECT COUNT(cs) > 0 FROM CashierShift cs WHERE cs.cashier.id = :cashierId AND cs.status = 'OPEN'")
    boolean hasActiveShift(@Param("cashierId") UUID cashierId);

    /**
     * Check if drawer has active shift (is assigned)
     */
    @Query("SELECT COUNT(cs) > 0 FROM CashierShift cs WHERE cs.cashDrawer.id = :drawerId AND cs.status = 'OPEN'")
    boolean hasActiveShiftByDrawerId(@Param("drawerId") UUID drawerId);

    /**
     * Find active shift for a drawer
     */
    @Query("SELECT cs FROM CashierShift cs WHERE cs.cashDrawer.id = :drawerId AND cs.status = 'OPEN'")
    Optional<CashierShift> findActiveShiftByDrawerId(@Param("drawerId") UUID drawerId);

    /**
     * Get sum of discrepancy amounts from closed/approved shifts for a restaurant within a date range.
     * Only includes shifts that are CLOSED or APPROVED (not PENDING_APPROVAL or REJECTED).
     */
    @Query("SELECT COALESCE(SUM(cs.discrepancyAmount), 0) FROM CashierShift cs " +
            "WHERE cs.restaurant.id = :restaurantId " +
            "AND cs.status IN ('CLOSED', 'APPROVED') " +
            "AND cs.closedAt BETWEEN :startDate AND :endDate " +
            "AND cs.discrepancyAmount IS NOT NULL")
    BigDecimal getTotalDiscrepancyAmountByRestaurantIdAndDateRange(
            @Param("restaurantId") UUID restaurantId,
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate
    );

    /**
     * Get sum of closing balances from closed/approved shifts for a restaurant within a date range.
     * This represents the actual cash counted when shifts were closed.
     */
    @Query("SELECT COALESCE(SUM(cs.closingBalance), 0) FROM CashierShift cs " +
            "WHERE cs.restaurant.id = :restaurantId " +
            "AND cs.status IN ('CLOSED', 'APPROVED') " +
            "AND cs.closedAt BETWEEN :startDate AND :endDate " +
            "AND cs.closingBalance IS NOT NULL")
    BigDecimal getTotalClosingBalanceByRestaurantIdAndDateRange(
            @Param("restaurantId") UUID restaurantId,
            @Param("startDate") OffsetDateTime startDate,
            @Param("endDate") OffsetDateTime endDate
    );

    /**
     * Find shifts for a restaurant with multiple filters (cash drawer, cashier, status, date range, and search)
     * Search applies to cashier name (firstName + lastName) and cash drawer name
     * Uses native query to handle bytea to text casting for firstName/lastName fields
     */
    @Query(value = "SELECT cs.* FROM cashier_shift cs " +
            "JOIN cash_drawers cd ON cd.id = cs.cash_drawer_id " +
            "JOIN users c ON c.id = cs.cashier_id " +
            "WHERE cs.restaurant_id = CAST(:restaurantId AS uuid) " +
            "AND (:cashDrawerId IS NULL OR cs.cash_drawer_id = CAST(:cashDrawerId AS uuid)) " +
            "AND (:cashierId IS NULL OR cs.cashier_id = CAST(:cashierId AS uuid)) " +
            "AND (:statuses IS NULL OR cs.status::text IN (:statuses)) " +
            "AND (CAST(:startDate AS timestamp) IS NULL OR cs.started_at >= CAST(:startDate AS timestamp)) " +
            "AND (CAST(:endDate AS timestamp) IS NULL OR cs.started_at <= CAST(:endDate AS timestamp)) " +
            "AND (:search IS NULL OR " +
            "     LOWER(CAST(COALESCE(c.first_name, '') AS text) || ' ' || CAST(COALESCE(c.last_name, '') AS text)) LIKE LOWER(CAST('%' || :search || '%' AS text)) OR " +
            "     EXISTS (SELECT 1 FROM cash_drawer_translation cdt WHERE cdt.cash_drawer_id = cd.id " +
            "             AND LOWER(CAST(cdt.name AS text)) LIKE LOWER(CAST('%' || :search || '%' AS text)))) " +
            "ORDER BY cs.started_at DESC",
            countQuery = "SELECT COUNT(cs.*) FROM cashier_shift cs " +
            "JOIN cash_drawers cd ON cd.id = cs.cash_drawer_id " +
            "JOIN users c ON c.id = cs.cashier_id " +
            "WHERE cs.restaurant_id = CAST(:restaurantId AS uuid) " +
            "AND (:cashDrawerId IS NULL OR cs.cash_drawer_id = CAST(:cashDrawerId AS uuid)) " +
            "AND (:cashierId IS NULL OR cs.cashier_id = CAST(:cashierId AS uuid)) " +
            "AND (:statuses IS NULL OR cs.status::text IN (:statuses)) " +
            "AND (CAST(:startDate AS timestamp) IS NULL OR cs.started_at >= CAST(:startDate AS timestamp)) " +
            "AND (CAST(:endDate AS timestamp) IS NULL OR cs.started_at <= CAST(:endDate AS timestamp)) " +
            "AND (:search IS NULL OR " +
            "     LOWER(CAST(COALESCE(c.first_name, '') AS text) || ' ' || CAST(COALESCE(c.last_name, '') AS text)) LIKE LOWER(CAST('%' || :search || '%' AS text)) OR " +
            "     EXISTS (SELECT 1 FROM cash_drawer_translation cdt WHERE cdt.cash_drawer_id = cd.id " +
            "             AND LOWER(CAST(cdt.name AS text)) LIKE LOWER(CAST('%' || :search || '%' AS text))))",
            nativeQuery = true)
    Page<CashierShift> findByRestaurantIdWithFilters(
            @Param("restaurantId") UUID restaurantId,
            @Param("cashDrawerId") UUID cashDrawerId,
            @Param("cashierId") UUID cashierId,
            @Param("statuses") Collection<String> statuses,
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate,
            @Param("search") String search,
            Pageable pageable
    );
}

