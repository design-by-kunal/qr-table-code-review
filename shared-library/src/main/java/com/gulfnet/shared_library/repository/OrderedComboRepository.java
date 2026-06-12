package com.gulfnet.shared_library.repository;

import com.gulfnet.shared_library.entity.OrderedCombo;
import com.gulfnet.shared_library.enums.RequestStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface OrderedComboRepository extends JpaRepository<OrderedCombo, UUID> {
    List<OrderedCombo> findByOrderId(UUID orderId);

    /**
     * Batch-load ordered combos with parent order eagerly fetched.
     * Avoids N+1 lazy loads during bulk status updates.
     */
    @Query("SELECT DISTINCT oc FROM OrderedCombo oc JOIN FETCH oc.order WHERE oc.id IN :ids")
    List<OrderedCombo> findAllByIdWithOrderFetched(@Param("ids") Collection<UUID> ids);

    /**
     * Batch-load ordered combos for multiple orders.
     * Fetches combo translations up-front to avoid N+1 during response building.
     */
    @Query("SELECT DISTINCT oc FROM OrderedCombo oc " +
           "LEFT JOIN FETCH oc.combo c " +
           "LEFT JOIN FETCH c.translations " +
           "LEFT JOIN FETCH oc.order o " +
           "WHERE o.id IN :orderIds")
    List<OrderedCombo> findByOrderIds(@Param("orderIds") List<UUID> orderIds);
    
    Page<OrderedCombo> findByCancellationRequestStatus(RequestStatus status, Pageable pageable);
    
    /**
     * Find ordered combos by cancellation request status, with optional status filter.
     * If status is null, returns all combos with request status != NONE.
     */
    @Query("SELECT oc FROM OrderedCombo oc WHERE " +
           "(:status IS NULL AND oc.cancellationRequestStatus != com.gulfnet.shared_library.enums.RequestStatus.NONE) OR " +
           "(:status IS NOT NULL AND oc.cancellationRequestStatus = :status)")
    Page<OrderedCombo> findByCancellationRequestStatusOptional(
            @Param("status") RequestStatus status, 
            Pageable pageable);
    
    /**
     * Find ordered combo by ID with all relationships needed for cancellation request response.
     * Used after entity manager clear to ensure all lazy-loaded relationships are available.
     */
    @Query("SELECT DISTINCT oc FROM OrderedCombo oc " +
           "LEFT JOIN FETCH oc.combo c " +
           "LEFT JOIN FETCH c.translations " +
           "LEFT JOIN FETCH oc.order o " +
           "LEFT JOIN FETCH o.restaurant r " +
           "LEFT JOIN FETCH r.translations " +
           "LEFT JOIN FETCH oc.cancellationRequestedBy " +
           "LEFT JOIN FETCH oc.cancellationReviewedBy " +
           "WHERE oc.id = :id")
    Optional<OrderedCombo> findByIdWithRelationshipsForCancellationResponse(@Param("id") UUID id);

    @Query("SELECT oc.combo.comboId FROM OrderedCombo oc WHERE oc.id = :orderedComboId")
    Optional<UUID> findMenuComboIdByOrderedComboId(@Param("orderedComboId") UUID orderedComboId);

    /**
     * Count ready combos (sum of quantity) for an order
     * Returns the sum of quantities for combos with READY status
     */
    @Query(value = "SELECT COALESCE(SUM(COALESCE(oc.quantity, 1)), 0) FROM ordered_combo oc " +
           "WHERE oc.order_id = :orderId " +
           "AND oc.item_status = 'READY'",
           nativeQuery = true)
    Long countReadyCombosByOrderId(@Param("orderId") UUID orderId);

    /**
     * Count pending combos (sum of quantity) for an order
     * Returns the sum of quantities for combos with PUSHED, COOKING, or DELAYED status
     */
    @Query(value = "SELECT COALESCE(SUM(COALESCE(oc.quantity, 1)), 0) FROM ordered_combo oc " +
           "WHERE oc.order_id = :orderId " +
           "AND oc.item_status IN ('PUSHED', 'COOKING', 'DELAYED')",
           nativeQuery = true)
    Long countPendingCombosByOrderId(@Param("orderId") UUID orderId);

    /**
     * Calculate total wastage cost and count for combos for a specific restaurant
     * Includes combos that were cancelled after being in COOKING, READY or SERVED status,
     * or combos from cancelled transactions (regardless of combo status)
     * Uses sentinel date '1970-01-01 00:00:00' to handle null date parameters
     */
    @Query(value = "WITH wastage_combos AS ( " +
           "    SELECT " +
           "           oc.id as ordered_combo_id, " +
           "           oc.quantity, " +
           "           COALESCE(oc.total_combo_amount, oc.price * oc.quantity, 0) as combo_amount " +
           "    FROM ordered_combo oc " +
           "    JOIN orders o ON o.id = oc.order_id " +
           "    LEFT JOIN transaction t ON t.order_id = o.id " +
           "    JOIN restaurant r ON r.id = o.restaurant_id " +
           "    WHERE oc.item_status = 'CANCELED' " +
           "    AND (oc.wastage_source_status IN ('COOKING', 'READY', 'SERVED') " +
           "         OR (oc.wastage_source_status IS NULL " +
           "             AND (o.order_status IN ('SERVED', 'READY', 'COOKING') " +
           "                  OR (oc.total_combo_amount IS NOT NULL AND oc.total_combo_amount > 0) " +
           "                  OR (oc.price IS NOT NULL AND oc.quantity IS NOT NULL AND oc.price * oc.quantity > 0))) " +
           "         OR t.transaction_status = 'CANCELED') " +
           "    AND r.id = CAST(:restaurantId AS uuid) " +
           "    AND (r.is_deleted IS NULL OR r.is_deleted = false) " +
           "    AND (CAST(:startDate AS timestamp) = '1970-01-01 00:00:00'::timestamp OR " +
           "         (CASE WHEN t.transaction_status = 'CANCELED' THEN t.created_at ELSE oc.updated_at END) >= CAST(:startDate AS timestamp)) " +
           "    AND (CAST(:endDate AS timestamp) = '1970-01-01 00:00:00'::timestamp OR " +
           "         (CASE WHEN t.transaction_status = 'CANCELED' THEN t.created_at ELSE oc.updated_at END) <= CAST(:endDate AS timestamp)) " +
           ") " +
           "SELECT " +
           "       COALESCE(SUM(wc.quantity), 0) as total_quantity, " +
           "       COALESCE(SUM(wc.combo_amount), 0) as total_wastage_cost " +
           "FROM wastage_combos wc",
           nativeQuery = true)
    List<Object[]> getComboWastageSummaryByRestaurantId(
            @Param("restaurantId") UUID restaurantId,
            @Param("startDate") java.time.LocalDateTime startDate,
            @Param("endDate") java.time.LocalDateTime endDate);

    /**
     * Calculate total wastage cost and count for combos for a restaurant group
     * Includes combos that were cancelled after being in COOKING, READY or SERVED status,
     * or combos from cancelled transactions (regardless of combo status)
     * Uses sentinel date '1970-01-01 00:00:00' to handle null date parameters
     */
    @Query(value = "WITH wastage_combos AS ( " +
           "    SELECT " +
           "           oc.id as ordered_combo_id, " +
           "           oc.quantity, " +
           "           COALESCE(oc.total_combo_amount, oc.price * oc.quantity, 0) as combo_amount " +
           "    FROM ordered_combo oc " +
           "    JOIN orders o ON o.id = oc.order_id " +
           "    LEFT JOIN transaction t ON t.order_id = o.id " +
           "    JOIN restaurant r ON r.id = o.restaurant_id " +
           "    WHERE oc.item_status = 'CANCELED' " +
           "    AND (oc.wastage_source_status IN ('COOKING', 'READY', 'SERVED') " +
           "         OR (oc.wastage_source_status IS NULL " +
           "             AND (o.order_status IN ('SERVED', 'READY', 'COOKING') " +
           "                  OR (oc.total_combo_amount IS NOT NULL AND oc.total_combo_amount > 0) " +
           "                  OR (oc.price IS NOT NULL AND oc.quantity IS NOT NULL AND oc.price * oc.quantity > 0))) " +
           "         OR t.transaction_status = 'CANCELED') " +
           "    AND r.restaurant_group_id = CAST(:restaurantGroupId AS uuid) " +
           "    AND (r.is_deleted IS NULL OR r.is_deleted = false) " +
           "    AND (CAST(:startDate AS timestamp) = '1970-01-01 00:00:00'::timestamp OR " +
           "         (CASE WHEN t.transaction_status = 'CANCELED' THEN t.created_at ELSE oc.updated_at END) >= CAST(:startDate AS timestamp)) " +
           "    AND (CAST(:endDate AS timestamp) = '1970-01-01 00:00:00'::timestamp OR " +
           "         (CASE WHEN t.transaction_status = 'CANCELED' THEN t.created_at ELSE oc.updated_at END) <= CAST(:endDate AS timestamp)) " +
           ") " +
           "SELECT " +
           "       COALESCE(SUM(wc.quantity), 0) as total_quantity, " +
           "       COALESCE(SUM(wc.combo_amount), 0) as total_wastage_cost " +
           "FROM wastage_combos wc",
           nativeQuery = true)
    List<Object[]> getComboWastageSummaryByRestaurantGroupId(
            @Param("restaurantGroupId") UUID restaurantGroupId,
            @Param("startDate") java.time.LocalDateTime startDate,
            @Param("endDate") java.time.LocalDateTime endDate);

    /**
     * Calculate total wastage cost and count for combos for all restaurants
     * Includes combos that were cancelled after being in COOKING, READY or SERVED status,
     * or combos from cancelled transactions (regardless of combo status)
     * Uses sentinel date '1970-01-01 00:00:00' to handle null date parameters
     */
    @Query(value = "WITH wastage_combos AS ( " +
           "    SELECT " +
           "           oc.id as ordered_combo_id, " +
           "           oc.quantity, " +
           "           COALESCE(oc.total_combo_amount, oc.price * oc.quantity, 0) as combo_amount " +
           "    FROM ordered_combo oc " +
           "    JOIN orders o ON o.id = oc.order_id " +
           "    LEFT JOIN transaction t ON t.order_id = o.id " +
           "    JOIN restaurant r ON r.id = o.restaurant_id " +
           "    WHERE oc.item_status = 'CANCELED' " +
           "    AND (oc.wastage_source_status IN ('COOKING', 'READY', 'SERVED') " +
           "         OR (oc.wastage_source_status IS NULL " +
           "             AND (o.order_status IN ('SERVED', 'READY', 'COOKING') " +
           "                  OR (oc.total_combo_amount IS NOT NULL AND oc.total_combo_amount > 0) " +
           "                  OR (oc.price IS NOT NULL AND oc.quantity IS NOT NULL AND oc.price * oc.quantity > 0))) " +
           "         OR t.transaction_status = 'CANCELED') " +
           "    AND (r.is_deleted IS NULL OR r.is_deleted = false) " +
           "    AND (CAST(:startDate AS timestamp) = '1970-01-01 00:00:00'::timestamp OR " +
           "         (CASE WHEN t.transaction_status = 'CANCELED' THEN t.created_at ELSE oc.updated_at END) >= CAST(:startDate AS timestamp)) " +
           "    AND (CAST(:endDate AS timestamp) = '1970-01-01 00:00:00'::timestamp OR " +
           "         (CASE WHEN t.transaction_status = 'CANCELED' THEN t.created_at ELSE oc.updated_at END) <= CAST(:endDate AS timestamp)) " +
           ") " +
           "SELECT " +
           "       COALESCE(SUM(wc.quantity), 0) as total_quantity, " +
           "       COALESCE(SUM(wc.combo_amount), 0) as total_wastage_cost " +
           "FROM wastage_combos wc",
           nativeQuery = true)
    List<Object[]> getComboWastageSummary(
            @Param("startDate") java.time.LocalDateTime startDate,
            @Param("endDate") java.time.LocalDateTime endDate);

    /**
     * Get itemized sales report for combos
     * Returns: [comboId, categoryId, quantitySold, unitPrice, totalSales]
     * Filters by specific restaurantId only.
     *
     * IMPORTANT:
     * - Includes COMPLETED, REFUNDED and PARTIALLY_REFUNDED transactions.
     * - Aggregates combos by combo_id to show each combo as a single entry.
     * - Treats refunds as adjustments, not deletions, by subtracting per-line refund amounts
     *   from the net total sales, but NOT from the base/unit price.
     * - Uses total_combo_amount for total sales calculation.
     * - Unit price is calculated as total_combo_amount / quantity.
     * - Gets category from menu_category_combo_mapping -> menu_category_mapping -> category.
     *   Uses parent category if it exists, otherwise uses the category itself (same logic as items).
     * - Combos without category mappings will appear with NULL category_id.
     * - Uses sentinel date '1970-01-01 00:00:00' to handle null date parameters.
     */
    @Query(value = "SELECT DISTINCT ON (combo_totals.combo_id) " +
           "       combo_totals.combo_id, " +
           "       COALESCE(c.parent_category_id, mcm.category_id) as main_category_id, " +
           "       combo_totals.quantity_sold, " +
           "       CASE " +
           "           WHEN combo_totals.quantity_sold > 0 AND combo_totals.base_total_amount > 0 " +
           "               THEN ROUND((combo_totals.base_total_amount / combo_totals.quantity_sold)::numeric, 2) " +
           "           ELSE 0 " +
           "       END as unit_price, " +
           "       combo_totals.total_sales " +
           "FROM ( " +
           "    SELECT " +
           "           oc.combo_id, " +
           "           COALESCE(SUM(GREATEST(COALESCE(oc.quantity, 0) - COALESCE(ri_agg.refund_quantity, 0), 0)), 0) as quantity_sold, " +
           "           COALESCE(SUM(GREATEST(COALESCE(oc.total_combo_amount, oc.price * oc.quantity, 0) - COALESCE(ri_agg.refund_amount, 0), 0)), 0) as total_sales, " +
           "           COALESCE(SUM(GREATEST( " +
           "               COALESCE(oc.price * oc.quantity, 0) - " +
           "               (CASE " +
           "                   WHEN COALESCE(oc.quantity, 0) > 0 " +
           "                       THEN COALESCE(oc.price * oc.quantity, 0) * (COALESCE(ri_agg.refund_quantity, 0)::numeric / oc.quantity) " +
           "                   ELSE 0 " +
           "               END), 0 " +
           "           )), 0) as base_total_amount " +
           "    FROM ordered_combo oc " +
           "    JOIN orders o ON o.id = oc.order_id " +
           "    JOIN transaction t ON t.order_id = o.id " +
           "    JOIN restaurant r ON r.id = o.restaurant_id " +
           "    LEFT JOIN ( " +
           "        SELECT ri.ordered_combo_id, SUM(COALESCE(ri.quantity, 0)) as refund_quantity, SUM(COALESCE(ri.refund_amount, 0)) as refund_amount " +
           "        FROM refund_item ri " +
           "        WHERE ri.ordered_combo_id IS NOT NULL " +
           "        GROUP BY ri.ordered_combo_id " +
           "    ) ri_agg ON ri_agg.ordered_combo_id = oc.id " +
           "    WHERE t.transaction_status IN ('COMPLETED', 'REFUNDED', 'PARTIALLY_REFUNDED') " +
           "           AND r.id = CAST(:restaurantId AS uuid) " +
           "           AND (r.is_deleted IS NULL OR r.is_deleted = false) " +
           "           AND (CAST(:startDate AS timestamp) = '1970-01-01 00:00:00'::timestamp OR t.created_at >= CAST(:startDate AS timestamp)) " +
           "           AND (CAST(:endDate AS timestamp) = '1970-01-01 00:00:00'::timestamp OR t.created_at <= CAST(:endDate AS timestamp)) " +
           "    GROUP BY oc.combo_id " +
           "    HAVING COALESCE(SUM(GREATEST(COALESCE(oc.total_combo_amount, oc.price * oc.quantity, 0) - COALESCE(ri_agg.refund_amount, 0), 0)), 0) > 0 " +
           ") combo_totals " +
           "LEFT JOIN menu_category_combo_mapping mccm ON mccm.combo_id = combo_totals.combo_id " +
           "LEFT JOIN menu_category_mapping mcm ON mcm.id = mccm.menu_category_mapping_id " +
           "       AND EXISTS ( " +
           "           SELECT 1 FROM restaurant_menu_mapping rmm " +
           "           WHERE rmm.restaurant_id = CAST(:restaurantId AS uuid) " +
           "             AND rmm.menu_id = mcm.menu_id " +
           "             AND rmm.status = 'LIVE' " +
           "       ) " +
           "LEFT JOIN category c ON c.id = mcm.category_id " +
           "ORDER BY combo_totals.combo_id, " +
           "         CASE WHEN c.parent_category_id IS NOT NULL THEN 0 ELSE 1 END, " +
           "         COALESCE(c.parent_category_id, mcm.category_id) NULLS LAST",
           nativeQuery = true)
    List<Object[]> getItemizedComboSalesReport(
            @Param("restaurantId") UUID restaurantId,
            @Param("startDate") java.time.LocalDateTime startDate,
            @Param("endDate") java.time.LocalDateTime endDate);
}
