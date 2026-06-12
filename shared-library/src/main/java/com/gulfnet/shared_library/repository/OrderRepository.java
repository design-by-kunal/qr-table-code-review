package com.gulfnet.shared_library.repository;

import com.gulfnet.shared_library.entity.Order;
import com.gulfnet.shared_library.entity.TableAssignment;
import com.gulfnet.shared_library.enums.EntityStatus;
import com.gulfnet.shared_library.enums.OrderStatus;
import com.gulfnet.shared_library.enums.OrderType;
import com.gulfnet.shared_library.enums.RequestStatus;
import com.gulfnet.shared_library.enums.TransactionStatus;
 
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
import java.time.LocalDateTime;

@Repository
public interface OrderRepository extends JpaRepository<Order, UUID> {

    boolean existsByGmoLinkOrderId(String gmoLinkOrderId);

    Optional<Order> findByGmoLinkOrderId(String gmoLinkOrderId);
    
    /**
     * Static constant for the discounts promotions report query.
     * Using a constant instead of string concatenation in @Query annotation
     * to avoid Spring Data JPA processing issues with very long queries.
     */
    String DISCOUNTS_PROMOTIONS_REPORT_QUERY = 
        "WITH params AS ( " +
        "    SELECT " +
        "        CAST(:restaurantId AS uuid) AS restaurant_id, " +
        "        CAST(:startDate AS timestamp) AS start_date, " +
        "        CAST(:endDate AS timestamp) AS end_date " +
        "), usage_base AS ( " +
        "    SELECT " +
        "        u.discount_type, " +
        "        u.discount_code, " +
        "        COALESCE( " +
        "            (SELECT dt.name FROM discount_translation dt WHERE dt.discount_id = u.discount_id AND dt.language_code = 'en' LIMIT 1), " +
        "            (SELECT dt.name FROM discount_translation dt WHERE dt.discount_id = u.discount_id ORDER BY dt.language_code LIMIT 1), " +
        "            u.discount_code " +
        "        ) AS discount_name, " +
        "        u.applied_to, " +
        "        u.transaction_id, " +
        "        u.order_id, " +
        "        u.discount_amount " +
        "    FROM order_discount_usage u " +
        "    JOIN params p ON u.restaurant_id = p.restaurant_id " +
        "    JOIN transaction t ON t.id = u.transaction_id " +
        "    WHERE (p.start_date = TIMESTAMP '1970-01-01 00:00:00' OR u.created_at >= p.start_date) " +
        "      AND (p.end_date = TIMESTAMP '1970-01-01 00:00:00' OR u.created_at <= p.end_date) " +
        "      AND t.transaction_status IN ('COMPLETED', 'REFUNDED', 'PARTIALLY_REFUNDED') " +
        "), order_original_subtotals AS ( " +
        "    SELECT " +
        "        o.id as order_id, " +
        "        COALESCE((SELECT SUM(COALESCE(oi2.total_item_amount, 0)) FROM ordered_item oi2 WHERE oi2.order_id = o.id AND oi2.ordered_combo_id IS NULL), 0) + " +
        "        COALESCE((SELECT SUM(COALESCE(oc2.total_combo_amount, 0)) FROM ordered_combo oc2 WHERE oc2.order_id = o.id), 0) AS original_subtotal " +
        "    FROM orders o " +
        "), transaction_discount_totals AS ( " +
        "    SELECT " +
        "        ub.discount_type, " +
        "        ub.discount_code, " +
        "        ub.transaction_id, " +
        "        SUM(ub.discount_amount) AS total_discount_per_transaction " +
        "    FROM usage_base ub " +
        "    GROUP BY ub.discount_type, ub.discount_code, ub.transaction_id " +
        "), transaction_totals AS ( " +
        "    SELECT " +
        "        ub.transaction_id, " +
        "        SUM(ub.discount_amount) AS total_discount_all_discounts " +
        "    FROM usage_base ub " +
        "    GROUP BY ub.transaction_id " +
        "), order_item_discount_totals AS ( " +
        "    SELECT " +
        "        ub.transaction_id, " +
        "        ub.order_id, " +
        "        SUM(CASE WHEN ub.applied_to IN ('CATEGORY', 'ITEM') THEN ub.discount_amount ELSE 0 END) AS total_item_discounts " +
        "    FROM usage_base ub " +
        "    GROUP BY ub.transaction_id, ub.order_id " +
        "), usage_with_revenue AS ( " +
        "    SELECT " +
        "        ub.discount_type, " +
        "        ub.discount_code, " +
        "        ub.discount_name, " +
        "        ub.applied_to, " +
        "        ub.transaction_id, " +
        "        ub.order_id, " +
        "        ub.discount_amount, " +
        "        ROW_NUMBER() OVER (PARTITION BY ub.discount_type, ub.discount_code, ub.transaction_id ORDER BY ub.transaction_id) AS rn, " +
        "        COALESCE(t.transaction_amount, 0) AS transaction_revenue, " +
        "        CASE " +
        "            WHEN ub.applied_to = 'CATEGORY' OR ub.applied_to = 'ITEM' THEN " +
        "                COALESCE(os.original_subtotal, 0) + " +
        "                CASE " +
        "                    WHEN COALESCE(os.original_subtotal, 0) > 0 AND (COALESCE(os.original_subtotal, 0) - COALESCE(oidt.total_item_discounts, 0)) > 0 THEN " +
        "                        COALESCE(o.tax_amount, 0) * (COALESCE(os.original_subtotal, 0) / (COALESCE(os.original_subtotal, 0) - COALESCE(oidt.total_item_discounts, 0))) " +
        "                    ELSE COALESCE(o.tax_amount, 0) " +
        "                END + " +
        "                CASE " +
        "                    WHEN COALESCE(os.original_subtotal, 0) > 0 AND (COALESCE(os.original_subtotal, 0) - COALESCE(oidt.total_item_discounts, 0)) > 0 THEN " +
        "                        COALESCE(o.service_charge_amount, 0) * (COALESCE(os.original_subtotal, 0) / (COALESCE(os.original_subtotal, 0) - COALESCE(oidt.total_item_discounts, 0))) " +
        "                    ELSE COALESCE(o.service_charge_amount, 0) " +
        "                END + " +
        "                CASE " +
        "                    WHEN COALESCE(os.original_subtotal, 0) > 0 AND (COALESCE(os.original_subtotal, 0) - COALESCE(oidt.total_item_discounts, 0)) > 0 THEN " +
        "                        COALESCE(o.packing_charge_amount, 0) * (COALESCE(os.original_subtotal, 0) / (COALESCE(os.original_subtotal, 0) - COALESCE(oidt.total_item_discounts, 0))) " +
        "                    ELSE COALESCE(o.packing_charge_amount, 0) " +
        "                END " +
        "            ELSE " +
        "                COALESCE(os.original_subtotal, 0) + " +
        "                CASE " +
        "                    WHEN COALESCE(os.original_subtotal, 0) > 0 AND (COALESCE(os.original_subtotal, 0) - COALESCE(tt.total_discount_all_discounts, 0)) > 0 THEN " +
        "                        COALESCE(o.tax_amount, 0) * (COALESCE(os.original_subtotal, 0) / (COALESCE(os.original_subtotal, 0) - COALESCE(tt.total_discount_all_discounts, 0))) " +
        "                    ELSE COALESCE(o.tax_amount, 0) " +
        "                END + " +
        "                CASE " +
        "                    WHEN COALESCE(os.original_subtotal, 0) > 0 AND (COALESCE(os.original_subtotal, 0) - COALESCE(tt.total_discount_all_discounts, 0)) > 0 THEN " +
        "                        COALESCE(o.service_charge_amount, 0) * (COALESCE(os.original_subtotal, 0) / (COALESCE(os.original_subtotal, 0) - COALESCE(tt.total_discount_all_discounts, 0))) " +
        "                    ELSE COALESCE(o.service_charge_amount, 0) " +
        "                END + " +
        "                CASE " +
        "                    WHEN COALESCE(os.original_subtotal, 0) > 0 AND (COALESCE(os.original_subtotal, 0) - COALESCE(tt.total_discount_all_discounts, 0)) > 0 THEN " +
        "                        COALESCE(o.packing_charge_amount, 0) * (COALESCE(os.original_subtotal, 0) / (COALESCE(os.original_subtotal, 0) - COALESCE(tt.total_discount_all_discounts, 0))) " +
        "                    ELSE COALESCE(o.packing_charge_amount, 0) " +
        "                END " +
        "        END AS total_revenue_before_discount, " +
        "        CASE " +
        "            WHEN tt.total_discount_all_discounts > 0 THEN " +
        "                (ub.discount_amount / tt.total_discount_all_discounts) " +
        "            ELSE 0 " +
        "        END AS allocation_factor, " +
        "        COALESCE(t.transaction_amount, 0) * " +
        "        CASE " +
        "            WHEN tt.total_discount_all_discounts > 0 THEN " +
        "                (ub.discount_amount / tt.total_discount_all_discounts) " +
        "            ELSE 0 " +
        "        END AS allocated_transaction_revenue, " +
        "        (CASE " +
        "            WHEN ub.applied_to = 'CATEGORY' OR ub.applied_to = 'ITEM' THEN " +
        "                COALESCE(os.original_subtotal, 0) + " +
        "                CASE " +
        "                    WHEN COALESCE(os.original_subtotal, 0) > 0 AND (COALESCE(os.original_subtotal, 0) - COALESCE(oidt.total_item_discounts, 0)) > 0 THEN " +
        "                        COALESCE(o.tax_amount, 0) * (COALESCE(os.original_subtotal, 0) / (COALESCE(os.original_subtotal, 0) - COALESCE(oidt.total_item_discounts, 0))) " +
        "                    ELSE COALESCE(o.tax_amount, 0) " +
        "                END + " +
        "                CASE " +
        "                    WHEN COALESCE(os.original_subtotal, 0) > 0 AND (COALESCE(os.original_subtotal, 0) - COALESCE(oidt.total_item_discounts, 0)) > 0 THEN " +
        "                        COALESCE(o.service_charge_amount, 0) * (COALESCE(os.original_subtotal, 0) / (COALESCE(os.original_subtotal, 0) - COALESCE(oidt.total_item_discounts, 0))) " +
        "                    ELSE COALESCE(o.service_charge_amount, 0) " +
        "                END + " +
        "                CASE " +
        "                    WHEN COALESCE(os.original_subtotal, 0) > 0 AND (COALESCE(os.original_subtotal, 0) - COALESCE(oidt.total_item_discounts, 0)) > 0 THEN " +
        "                        COALESCE(o.packing_charge_amount, 0) * (COALESCE(os.original_subtotal, 0) / (COALESCE(os.original_subtotal, 0) - COALESCE(oidt.total_item_discounts, 0))) " +
        "                    ELSE COALESCE(o.packing_charge_amount, 0) " +
        "                END " +
        "            ELSE " +
        "                COALESCE(os.original_subtotal, 0) + " +
        "                CASE " +
        "                    WHEN COALESCE(os.original_subtotal, 0) > 0 AND (COALESCE(os.original_subtotal, 0) - COALESCE(tt.total_discount_all_discounts, 0)) > 0 THEN " +
        "                        COALESCE(o.tax_amount, 0) * (COALESCE(os.original_subtotal, 0) / (COALESCE(os.original_subtotal, 0) - COALESCE(tt.total_discount_all_discounts, 0))) " +
        "                    ELSE COALESCE(o.tax_amount, 0) " +
        "                END + " +
        "                CASE " +
        "                    WHEN COALESCE(os.original_subtotal, 0) > 0 AND (COALESCE(os.original_subtotal, 0) - COALESCE(tt.total_discount_all_discounts, 0)) > 0 THEN " +
        "                        COALESCE(o.service_charge_amount, 0) * (COALESCE(os.original_subtotal, 0) / (COALESCE(os.original_subtotal, 0) - COALESCE(tt.total_discount_all_discounts, 0))) " +
        "                    ELSE COALESCE(o.service_charge_amount, 0) " +
        "                END + " +
        "                CASE " +
        "                    WHEN COALESCE(os.original_subtotal, 0) > 0 AND (COALESCE(os.original_subtotal, 0) - COALESCE(tt.total_discount_all_discounts, 0)) > 0 THEN " +
        "                        COALESCE(o.packing_charge_amount, 0) * (COALESCE(os.original_subtotal, 0) / (COALESCE(os.original_subtotal, 0) - COALESCE(tt.total_discount_all_discounts, 0))) " +
        "                    ELSE COALESCE(o.packing_charge_amount, 0) " +
        "                END " +
        "        END) * " +
        "        CASE " +
        "            WHEN tt.total_discount_all_discounts > 0 THEN " +
        "                (ub.discount_amount / tt.total_discount_all_discounts) " +
        "            ELSE 0 " +
        "        END AS allocated_total_revenue_before_discount " +
        "    FROM usage_base ub " +
        "    LEFT JOIN transaction t ON t.id = ub.transaction_id " +
        "    LEFT JOIN refund rf ON rf.transaction_id = t.id " +
        "    LEFT JOIN orders o ON o.id = ub.order_id " +
        "    LEFT JOIN order_original_subtotals os ON os.order_id = o.id " +
        "    LEFT JOIN transaction_discount_totals tdt ON tdt.discount_type = ub.discount_type " +
        "        AND tdt.discount_code = ub.discount_code " +
        "        AND tdt.transaction_id = ub.transaction_id " +
        "    LEFT JOIN transaction_totals tt ON tt.transaction_id = ub.transaction_id " +
        "    LEFT JOIN order_item_discount_totals oidt ON oidt.transaction_id = ub.transaction_id AND oidt.order_id = ub.order_id " +
        ") " +
        "SELECT " +
        "    discount_type, " +
        "    discount_code, " +
        "    discount_name, " +
        "    COUNT(DISTINCT transaction_id) AS number_of_transactions, " +
        "    ROUND(SUM(discount_amount), 2) AS total_discount_applied, " +
        // Total revenue should represent actual transaction amount collected, allocated to this discount:
        // total_revenue = SUM(allocated_transaction_revenue) where allocated_transaction_revenue = transaction_amount * allocation_factor
        // This uses the actual transaction amount from the billing API, not a calculated value
        "    ROUND(SUM(CASE WHEN rn = 1 THEN allocated_transaction_revenue ELSE 0 END), 2) AS total_revenue, " +
        "    ROUND(SUM(CASE WHEN rn = 1 THEN allocated_total_revenue_before_discount ELSE 0 END), 2) AS total_revenue_before_discount, " +
        "    CASE " +
        "        WHEN SUM(CASE WHEN rn = 1 THEN allocated_total_revenue_before_discount ELSE 0 END) > 0 THEN " +
        "            ROUND((SUM(discount_amount) * 100.0) / SUM(CASE WHEN rn = 1 THEN allocated_total_revenue_before_discount ELSE 0 END), 2) " +
        "        ELSE 0 " +
        "    END AS discount_efficiency, " +
        "    applied_to " +
        "FROM usage_with_revenue " +
        "GROUP BY discount_type, discount_code, discount_name, applied_to " +
        "ORDER BY total_discount_applied DESC";

    @Query("SELECT DISTINCT o FROM Order o " +
       "LEFT JOIN FETCH o.orderedItems oi " +
       "LEFT JOIN FETCH oi.item " +
       "LEFT JOIN FETCH oi.orderedItemModifiers oim " +
       "LEFT JOIN FETCH oim.modifierGroup " +
       "LEFT JOIN FETCH oim.modifierItem " +
       "WHERE o.id = :id")
    Optional<Order> findByIdWithFullDetails(@Param("id") UUID id);

    @Query("SELECT DISTINCT o FROM Order o " +
           "LEFT JOIN FETCH o.restaurantTable rt " +
           "LEFT JOIN FETCH rt.restaurantRow rr " +
           "LEFT JOIN FETCH rr.restaurantSection rs " +
           "LEFT JOIN FETCH rs.translations " +
           "WHERE o.id = :id")
    Optional<Order> findByIdWithTableAndSection(@Param("id") UUID id);
    
    /**
     * Find order by ID with all relationships needed for additional discount request response.
     * Used after entity manager operations to ensure all lazy-loaded relationships are available.
     */
    @Query("SELECT DISTINCT o FROM Order o " +
           "LEFT JOIN FETCH o.restaurant r " +
           "LEFT JOIN FETCH r.translations " +
           "LEFT JOIN FETCH o.additionalDiscountRequestedBy " +
           "LEFT JOIN FETCH o.additionalDiscountReviewedBy " +
           "WHERE o.id = :id")
    Optional<Order> findByIdWithRelationshipsForAdditionalDiscountResponse(@Param("id") UUID id);

    /**
     * Find order by ID with all relationships needed for order cancellation request response.
     * Used after entity manager operations to ensure all lazy-loaded relationships are available.
     */
    @Query("SELECT DISTINCT o FROM Order o " +
           "LEFT JOIN FETCH o.restaurant r " +
           "LEFT JOIN FETCH r.translations " +
           "LEFT JOIN FETCH o.restaurantTable rt " +
           "LEFT JOIN FETCH o.cancellationRequestedBy " +
           "LEFT JOIN FETCH o.cancellationReviewedBy " +
           "WHERE o.id = :id")
    Optional<Order> findByIdWithRelationshipsForOrderCancellationResponse(@Param("id") UUID id);

    List<Order> findBySessionIdOrderByCreatedAtDesc(UUID sessionId);

    /**
     * Batch-friendly session order listing that eagerly loads key relationships
     * required by OrderServiceImpl.buildOrderResponse:
     * - session (for sessionId in response)
     * - restaurantTable (for tableId/tableCode and waiter assignment lookup)
     * - waiter (for waiterId when initialized)
     * - restaurant (for gstNumber)
     */
    @Query("SELECT DISTINCT o FROM Order o " +
           "LEFT JOIN FETCH o.session s " +
           "LEFT JOIN FETCH o.restaurantTable rt " +
           "LEFT JOIN FETCH o.waiter w " +
           "LEFT JOIN FETCH o.restaurant r " +
           "WHERE s.id = :sessionId " +
           "ORDER BY o.createdAt DESC")
    List<Order> findBySessionIdOrderByCreatedAtDescWithTableWaiterRestaurant(
            @Param("sessionId") UUID sessionId);

    /**
     * Batch fetch orders with ordered items for multiple session IDs.
     * Uses JOIN FETCH to avoid N+1 query problem.
     */
    @Query("SELECT DISTINCT o FROM Order o " +
           "LEFT JOIN FETCH o.orderedItems oi " +
           "LEFT JOIN FETCH o.session s " +
           "WHERE s.id IN :sessionIds " +
           "ORDER BY s.id, o.createdAt DESC")
    List<Order> findBySessionIdsWithOrderedItems(@Param("sessionIds") Collection<UUID> sessionIds);

    /**
     * Find orders by table ID with active sessions (non-expired sessions only).
     * Used to check for active orders before blocking a table.
     * Matches the logic used in SessionRepository.findByTableIdAndExpiredAtIsNull
     */
    @Query("SELECT DISTINCT o FROM Order o " +
           "JOIN o.session s " +
           "WHERE o.restaurantTable.id = :tableId " +
           "AND s.expiredAt IS NULL " +
           "ORDER BY o.createdAt DESC")
    List<Order> findByTableIdWithActiveSessions(@Param("tableId") UUID tableId);

    /**
     * Find latest order for each table ID with active sessions (batch query).
     * Returns the most recent order per table, ordered by createdAt DESC.
     * Used for batch filtering tables by order status.
     */
    @Query("SELECT DISTINCT o FROM Order o " +
           "JOIN o.session s " +
           "WHERE o.restaurantTable.id IN :tableIds " +
           "AND s.expiredAt IS NULL " +
           "ORDER BY o.restaurantTable.id, o.createdAt DESC")
    List<Order> findByTableIdsWithActiveSessions(@Param("tableIds") Collection<UUID> tableIds);

    /**
     * Find orders by table ID with specific order statuses, regardless of session expiration.
     * Used to check for active orders before blocking a table.
     * This is more reliable as it checks orders directly without relying on session status.
     */
    @Query("SELECT DISTINCT o FROM Order o " +
           "WHERE o.restaurantTable.id = :tableId " +
           "AND o.orderStatus IN :orderStatuses " +
           "ORDER BY o.createdAt DESC")
    List<Order> findByTableIdAndOrderStatusIn(@Param("tableId") UUID tableId, 
                                               @Param("orderStatuses") Collection<OrderStatus> orderStatuses);

    /**
     * Find orders by multiple table IDs with specific order statuses.
     * Used for validating operations that must not be performed while orders are in progress on any table.
     */
    @Query("SELECT DISTINCT o FROM Order o " +
            "WHERE o.restaurantTable.id IN :tableIds " +
            "AND o.orderStatus IN :orderStatuses " +
            "ORDER BY o.createdAt DESC")
    List<Order> findByTableIdsAndOrderStatusIn(
            @Param("tableIds") Collection<UUID> tableIds,
            @Param("orderStatuses") Collection<OrderStatus> orderStatuses);

    /**
     * Check if any orders exist for the given session ID
     * More efficient than loading all orders when only checking existence
     */
    boolean existsBySessionId(UUID sessionId);

    /**
     * Find order by order number.
     */
    Optional<Order> findByOrderNumber(String orderNumber);

    @Query("SELECT COUNT(o) FROM Order o WHERE o.restaurant.id = :restaurantId")
    Long countByRestaurantId(@Param("restaurantId") UUID restaurantId);

    /**
     * Get restaurant ID directly from orders table using native query.
     * This is useful when the restaurant entity is not loaded (lazy loading).
     */
    @Query(value = "SELECT restaurant_id FROM orders WHERE id = :orderId", nativeQuery = true)
    Optional<UUID> findRestaurantIdByOrderId(@Param("orderId") UUID orderId);

    /**
     * Get table order number directly from orders table using native query.
     * This is useful when the restaurant table entity is not loaded (lazy loading).
     */
    @Query(value = "SELECT rt.table_order FROM orders o JOIN restaurant_table rt ON o.restaurant_table_id = rt.id WHERE o.id = :orderId", nativeQuery = true)
    Optional<Integer> findTableOrderByOrderId(@Param("orderId") UUID orderId);

    /**
     * Get order number directly from orders table using native query.
     * This is useful when the order entity is not fully loaded (lazy loading).
     */
    @Query(value = "SELECT order_number FROM orders WHERE id = :orderId", nativeQuery = true)
    Optional<String> findOrderNumberByOrderId(@Param("orderId") UUID orderId);

    /**
     * Get order numbers for a restaurant, order type, and date range.
     * Used to extract max sequence number from existing order numbers.
     * Uses FOR UPDATE to ensure we see uncommitted transactions and serialize access.
     */
    @Query(value = "SELECT order_number FROM orders " +
           "WHERE restaurant_id = CAST(:restaurantId AS uuid) " +
           "AND order_type::text = :orderType " +
           "AND created_at >= :startDate AND created_at < :endDate " +
           "AND order_number IS NOT NULL " +
           "FOR UPDATE", nativeQuery = true)
    List<String> findOrderNumbersByRestaurantIdAndOrderTypeAndCreatedAtBetween(
            @Param("restaurantId") String restaurantId,
            @Param("orderType") String orderType,
            @Param("startDate") java.time.OffsetDateTime startDate,
            @Param("endDate") java.time.OffsetDateTime endDate);

    /**
     * Get all order numbers for a restaurant and order type (without date filter).
     * Used to extract max sequence number from all existing order numbers.
     * This is more reliable than date-filtered queries when order numbers don't contain date info.
     * Uses FOR UPDATE to ensure we see uncommitted transactions and serialize access.
     */
    @Query(value = "SELECT order_number FROM orders " +
           "WHERE restaurant_id = CAST(:restaurantId AS uuid) " +
           "AND order_type::text = :orderType " +
           "AND order_number IS NOT NULL " +
           "FOR UPDATE", nativeQuery = true)
    List<String> findAllOrderNumbersByRestaurantIdAndOrderType(
            @Param("restaurantId") String restaurantId,
            @Param("orderType") String orderType);

    /**
     * Get table ID directly from orders table using native query.
     * This is useful when the restaurant table entity is not loaded (lazy loading).
     */
    @Query(value = "SELECT restaurant_table_id FROM orders WHERE id = :orderId", nativeQuery = true)
    Optional<UUID> findTableIdByOrderId(@Param("orderId") UUID orderId);

    /**
     * Get table code directly from orders table using native query.
     * This is useful when the restaurant table entity is not loaded (lazy loading).
     */
    @Query(value = "SELECT rt.table_code FROM orders o JOIN restaurant_table rt ON o.restaurant_table_id = rt.id WHERE o.id = :orderId", nativeQuery = true)
    Optional<String> findTableCodeByOrderId(@Param("orderId") UUID orderId);

    /**
     * Acquire a row-level lock on any order row for the restaurant to serialize sequence generation.
     * Uses FOR UPDATE on a single row to avoid aggregates with FOR UPDATE (not supported in Postgres).
     * Returns Optional to handle case when no orders exist yet (lock is still acquired via the query execution).
     */
    @Query(value = "SELECT id FROM orders WHERE restaurant_id = :restaurantId FOR UPDATE LIMIT 1", nativeQuery = true)
    Optional<UUID> lockAnyByRestaurantIdForUpdate(@Param("restaurantId") UUID restaurantId);

    

    

    /**
     * Simplified live orders query:
     * - Filters: orderType, transactionStatus, paymentMethod
     * - Search: orderNumber or transactionNumber only
     * - Active sessions only
     */
    @Query(value =
            "SELECT o.* FROM orders o " +
            "JOIN sessions s ON s.id = o.session_id " +
            "LEFT JOIN transaction t ON t.order_id = o.id " +
            "WHERE o.restaurant_id = :restaurantId " +
            "AND (s.expired_at IS NULL OR s.expired_at > CURRENT_TIMESTAMP) " +
            "AND (:orderTypesCsv IS NULL OR o.order_type::text = ANY (string_to_array(:orderTypesCsv, ','))) " +
            "AND (:transactionStatusesCsv IS NULL OR t.transaction_status::text = ANY (string_to_array(:transactionStatusesCsv, ','))) " +
            "AND (:paymentMethodsCsv IS NULL OR t.payment_method = ANY (string_to_array(:paymentMethodsCsv, ','))) " +
            "AND (:search IS NULL OR (" +
            "     o.order_number ILIKE CONCAT('%', :search, '%') OR " +
            "     t.transaction_number ILIKE CONCAT('%', :search, '%')" +
            ")) ORDER BY o.created_at DESC",
            countQuery =
            "SELECT COUNT(o.id) FROM orders o " +
            "JOIN sessions s ON s.id = o.session_id " +
            "LEFT JOIN transaction t ON t.order_id = o.id " +
            "WHERE o.restaurant_id = :restaurantId " +
            "AND (s.expired_at IS NULL OR s.expired_at > CURRENT_TIMESTAMP) " +
            "AND (:orderTypesCsv IS NULL OR o.order_type::text = ANY (string_to_array(:orderTypesCsv, ','))) " +
            "AND (:transactionStatusesCsv IS NULL OR t.transaction_status::text = ANY (string_to_array(:transactionStatusesCsv, ','))) " +
            "AND (:paymentMethodsCsv IS NULL OR t.payment_method = ANY (string_to_array(:paymentMethodsCsv, ','))) " +
            "AND (:search IS NULL OR (" +
            "     o.order_number ILIKE CONCAT('%', :search, '%') OR " +
            "     t.transaction_number ILIKE CONCAT('%', :search, '%')" +
            "))",
            nativeQuery = true)
    Page<Order> findLiveOrdersSimple(
            @Param("restaurantId") UUID restaurantId,
            @Param("orderTypesCsv") String orderTypesCsv,
            @Param("transactionStatusesCsv") String transactionStatusesCsv,
            @Param("paymentMethodsCsv") String paymentMethodsCsv,
            @Param("search") String search,
            Pageable pageable);

    /**
     * JPQL version using typed enums and pageable - recommended approach
     * Filters by waiters from table_assignment table (supports multiple waiters per table)
     */
    @Query("SELECT DISTINCT o FROM Order o JOIN o.session s LEFT JOIN Transaction t ON t.order = o " +
           "JOIN o.restaurantTable rt JOIN rt.restaurantRow rr JOIN rr.restaurantSection rs " +
           "LEFT JOIN TableAssignment ta ON ta.restaurantTable.id = rt.id AND ta.unassignedAt IS NULL " +
           "WHERE o.restaurant.id = :restaurantId " +
           "AND (s.expiredAt IS NULL OR s.expiredAt > CURRENT_TIMESTAMP) " +
           "AND (:orderStatuses IS NULL OR o.orderStatus IN :orderStatuses) " +
           "AND (:orderTypes IS NULL OR o.orderType IN :orderTypes) " +
           "AND (:transactionStatuses IS NULL OR t.transactionStatus IN :transactionStatuses) " +
           "AND (:paymentMethods IS NULL OR t.paymentMethod IN :paymentMethods) " +
           "AND (:sectionId IS NULL OR rs.id = :sectionId) " +
           "AND (:waiterId IS NULL OR ta.waiter.id = :waiterId) " +
           "AND (:tableId IS NULL OR rt.id = :tableId) " +
           "AND (:likePatternLower IS NULL OR (LOWER(o.orderNumber) LIKE :likePatternLower " +
           "     OR LOWER(t.transactionNumber) LIKE :likePatternLower))")
    Page<Order> findLiveOrders(
            @Param("restaurantId") UUID restaurantId,
            @Param("orderStatuses") Collection<OrderStatus> orderStatuses,
            @Param("orderTypes") Collection<OrderType> orderTypes,
            @Param("transactionStatuses") Collection<TransactionStatus> transactionStatuses,
            @Param("paymentMethods") Collection<String> paymentMethods,
            @Param("sectionId") UUID sectionId,
            @Param("waiterId") UUID waiterId,
            @Param("tableId") UUID tableId,
            @Param("likePatternLower") String likePatternLower,
            Pageable pageable);


    // Note: Methods for waiter pending order checks are implemented in consuming services

    /**
     * Count orders created within a date range
     */
    @Query("SELECT COUNT(o) FROM Order o WHERE o.createdAt >= :startDate AND o.createdAt <= :endDate")
    long countByCreatedAtBetween(@Param("startDate") java.time.LocalDateTime startDate, @Param("endDate") java.time.LocalDateTime endDate);
    
    /**
     * Count orders by restaurant group created within a date range
     */
    @Query("SELECT COUNT(o) FROM Order o WHERE o.restaurant.restaurantGroup.id = :restaurantGroupId AND o.createdAt >= :startDate AND o.createdAt <= :endDate")
    long countByRestaurantGroupIdAndCreatedAtBetween(
            @Param("restaurantGroupId") java.util.UUID restaurantGroupId,
            @Param("startDate") java.time.LocalDateTime startDate,
            @Param("endDate") java.time.LocalDateTime endDate);
    
    /**
     * Count orders by restaurant group (all-time)
     */
    @Query("SELECT COUNT(o) FROM Order o WHERE o.restaurant.restaurantGroup.id = :restaurantGroupId")
    long countByRestaurantGroupId(@Param("restaurantGroupId") java.util.UUID restaurantGroupId);
    
    /**
     * Count ORDER discount usage (orders with ORDER discount applied) via transactions
     * Excludes only when full refund (RefundType = FULL), keeps order discount for partial refunds
     */
    @Query("SELECT COUNT(DISTINCT o.id) FROM Transaction t " +
           "JOIN t.order o " +
           "JOIN t.restaurant r " +
           "LEFT JOIN Refund rf ON rf.transaction.id = t.id " +
           "WHERE r.restaurantGroup.id = :restaurantGroupId " +
           "AND t.transactionStatus IN ('COMPLETED', 'REFUNDED', 'PARTIALLY_REFUNDED') " +
           "AND o.discount IS NOT NULL " +
           "AND o.discount.appliedTo = 'ORDER' " +
           "AND o.discount.status = :status " +
           "AND o.discount.isDeleted = false " +
           "AND (r.isDeleted IS NULL OR r.isDeleted = false) " +
           "AND (rf.refundType IS NULL OR rf.refundType != 'FULL')")
    long countOrderDiscountUsageByRestaurantGroupId(
            @Param("restaurantGroupId") UUID restaurantGroupId,
            @Param("status") EntityStatus status);
    
    /**
     * Count ORDER discount usage (orders with ORDER discount applied) via transactions within date range
     * Excludes only when full refund (RefundType = FULL), keeps order discount for partial refunds
     */
    @Query("SELECT COUNT(DISTINCT o.id) FROM Transaction t " +
           "JOIN t.order o " +
           "JOIN t.restaurant r " +
           "LEFT JOIN Refund rf ON rf.transaction.id = t.id " +
           "WHERE r.restaurantGroup.id = :restaurantGroupId " +
           "AND t.transactionStatus IN ('COMPLETED', 'REFUNDED', 'PARTIALLY_REFUNDED') " +
           "AND o.discount IS NOT NULL " +
           "AND o.discount.appliedTo = 'ORDER' " +
           "AND o.discount.status = :status " +
           "AND o.discount.isDeleted = false " +
           "AND t.createdAt >= :startDate " +
           "AND t.createdAt <= :endDate " +
           "AND (r.isDeleted IS NULL OR r.isDeleted = false) " +
           "AND (rf.refundType IS NULL OR rf.refundType != 'FULL')")
    long countOrderDiscountUsageByRestaurantGroupIdAndDateRange(
            @Param("restaurantGroupId") UUID restaurantGroupId,
            @Param("status") EntityStatus status,
            @Param("startDate") java.time.LocalDateTime startDate,
            @Param("endDate") java.time.LocalDateTime endDate);
    
    /**
     * Count ORDER discount usage (all-time, no restaurant group filter)
     * Excludes only when full refund (RefundType = FULL), keeps order discount for partial refunds
     */
    @Query("SELECT COUNT(DISTINCT o.id) FROM Transaction t " +
           "JOIN t.order o " +
           "LEFT JOIN Refund rf ON rf.transaction.id = t.id " +
           "WHERE t.transactionStatus IN ('COMPLETED', 'REFUNDED', 'PARTIALLY_REFUNDED') " +
           "AND o.discount IS NOT NULL " +
           "AND o.discount.appliedTo = 'ORDER' " +
           "AND o.discount.status = :status " +
           "AND o.discount.isDeleted = false " +
           "AND (rf.refundType IS NULL OR rf.refundType != 'FULL')")
    long countOrderDiscountUsage(@Param("status") EntityStatus status);
    
    /**
     * Count ORDER discount usage within date range (no restaurant group filter)
     * Excludes only when full refund (RefundType = FULL), keeps order discount for partial refunds
     */
    @Query("SELECT COUNT(DISTINCT o.id) FROM Transaction t " +
           "JOIN t.order o " +
           "LEFT JOIN Refund rf ON rf.transaction.id = t.id " +
           "WHERE t.transactionStatus IN ('COMPLETED', 'REFUNDED', 'PARTIALLY_REFUNDED') " +
           "AND o.discount IS NOT NULL " +
           "AND o.discount.appliedTo = 'ORDER' " +
           "AND o.discount.status = :status " +
           "AND o.discount.isDeleted = false " +
           "AND t.createdAt >= :startDate " +
           "AND t.createdAt <= :endDate " +
           "AND (rf.refundType IS NULL OR rf.refundType != 'FULL')")
    long countOrderDiscountUsageByDateRange(
            @Param("status") EntityStatus status,
            @Param("startDate") java.time.LocalDateTime startDate,
            @Param("endDate") java.time.LocalDateTime endDate);
    
    /**
     * Calculate ORDER discount revenue impact (sum of discount amounts) via transactions
     * Only subtracts discount amount when full refund (RefundType = FULL), keeps for partial refunds
     */
    @Query("SELECT COALESCE(SUM(" +
           "  CASE " +
           "    WHEN rf.refundType = 'FULL' THEN 0 " +
           "    ELSE o.discountAmount " +
           "  END" +
           "), 0) FROM Transaction t " +
           "JOIN t.order o " +
           "JOIN t.restaurant r " +
           "LEFT JOIN Refund rf ON rf.transaction.id = t.id " +
           "WHERE r.restaurantGroup.id = :restaurantGroupId " +
           "AND t.transactionStatus IN ('COMPLETED', 'REFUNDED', 'PARTIALLY_REFUNDED') " +
           "AND o.discount IS NOT NULL " +
           "AND o.discount.appliedTo = 'ORDER' " +
           "AND o.discount.status = :status " +
           "AND o.discount.isDeleted = false " +
           "AND o.discountAmount IS NOT NULL " +
           "AND (r.isDeleted IS NULL OR r.isDeleted = false)")
    java.math.BigDecimal sumOrderDiscountRevenueImpactByRestaurantGroupId(
            @Param("restaurantGroupId") UUID restaurantGroupId,
            @Param("status") EntityStatus status);
    
    /**
     * Calculate ORDER discount revenue impact within date range
     * Only subtracts discount amount when full refund (RefundType = FULL), keeps for partial refunds
     */
    @Query("SELECT COALESCE(SUM(" +
           "  CASE " +
           "    WHEN rf.refundType = 'FULL' THEN 0 " +
           "    ELSE o.discountAmount " +
           "  END" +
           "), 0) FROM Transaction t " +
           "JOIN t.order o " +
           "JOIN t.restaurant r " +
           "LEFT JOIN Refund rf ON rf.transaction.id = t.id " +
           "WHERE r.restaurantGroup.id = :restaurantGroupId " +
           "AND t.transactionStatus IN ('COMPLETED', 'REFUNDED', 'PARTIALLY_REFUNDED') " +
           "AND o.discount IS NOT NULL " +
           "AND o.discount.appliedTo = 'ORDER' " +
           "AND o.discount.status = :status " +
           "AND o.discount.isDeleted = false " +
           "AND o.discountAmount IS NOT NULL " +
           "AND t.createdAt >= :startDate " +
           "AND t.createdAt <= :endDate " +
           "AND (r.isDeleted IS NULL OR r.isDeleted = false)")
    java.math.BigDecimal sumOrderDiscountRevenueImpactByRestaurantGroupIdAndDateRange(
            @Param("restaurantGroupId") UUID restaurantGroupId,
            @Param("status") EntityStatus status,
            @Param("startDate") java.time.LocalDateTime startDate,
            @Param("endDate") java.time.LocalDateTime endDate);
    
    /**
     * Calculate ORDER discount revenue impact (all-time, no restaurant group filter)
     * Only subtracts discount amount when full refund (RefundType = FULL), keeps for partial refunds
     */
    @Query("SELECT COALESCE(SUM(" +
           "  CASE " +
           "    WHEN rf.refundType = 'FULL' THEN 0 " +
           "    ELSE o.discountAmount " +
           "  END" +
           "), 0) FROM Transaction t " +
           "JOIN t.order o " +
           "LEFT JOIN Refund rf ON rf.transaction.id = t.id " +
           "WHERE t.transactionStatus IN ('COMPLETED', 'REFUNDED', 'PARTIALLY_REFUNDED') " +
           "AND o.discount IS NOT NULL " +
           "AND o.discount.appliedTo = 'ORDER' " +
           "AND o.discount.status = :status " +
           "AND o.discount.isDeleted = false " +
           "AND o.discountAmount IS NOT NULL")
    java.math.BigDecimal sumOrderDiscountRevenueImpact(@Param("status") EntityStatus status);
    
    /**
     * Calculate ORDER discount revenue impact within date range (no restaurant group filter)
     * Only subtracts discount amount when full refund (RefundType = FULL), keeps for partial refunds
     */
    @Query("SELECT COALESCE(SUM(" +
           "  CASE " +
           "    WHEN rf.refundType = 'FULL' THEN 0 " +
           "    ELSE o.discountAmount " +
           "  END" +
           "), 0) FROM Transaction t " +
           "JOIN t.order o " +
           "LEFT JOIN Refund rf ON rf.transaction.id = t.id " +
           "WHERE t.transactionStatus IN ('COMPLETED', 'REFUNDED', 'PARTIALLY_REFUNDED') " +
           "AND o.discount IS NOT NULL " +
           "AND o.discount.appliedTo = 'ORDER' " +
           "AND o.discount.status = :status " +
           "AND o.discount.isDeleted = false " +
           "AND o.discountAmount IS NOT NULL " +
           "AND t.createdAt >= :startDate " +
           "AND t.createdAt <= :endDate")
    java.math.BigDecimal sumOrderDiscountRevenueImpactByDateRange(
            @Param("status") EntityStatus status,
            @Param("startDate") java.time.LocalDateTime startDate,
            @Param("endDate") java.time.LocalDateTime endDate);

            Page<Order> findByAdditionalDiscountRequestStatus(RequestStatus status, Pageable pageable);
    
    /**
     * Find orders by additional discount request status, with optional status filter.
     * If status is null, returns all orders with request status != NONE.
     */
    @Query("SELECT o FROM Order o WHERE " +
           "(:status IS NULL AND o.additionalDiscountRequestStatus != com.gulfnet.shared_library.enums.RequestStatus.NONE) OR " +
           "(:status IS NOT NULL AND o.additionalDiscountRequestStatus = :status)")
    Page<Order> findByAdditionalDiscountRequestStatusOptional(
            @Param("status") RequestStatus status, 
            Pageable pageable);

    /**
     * Find orders by cancellation request status
     */
    Page<Order> findByCancellationRequestStatus(RequestStatus status, Pageable pageable);

    /**
     * Find orders by cancellation request status, with optional status filter.
     * If status is null, returns all orders with request status != NONE.
     */
    @Query("SELECT o FROM Order o WHERE " +
           "(:status IS NULL AND o.cancellationRequestStatus != com.gulfnet.shared_library.enums.RequestStatus.NONE) OR " +
           "(:status IS NOT NULL AND o.cancellationRequestStatus = :status)")
    Page<Order> findByCancellationRequestStatusOptional(
            @Param("status") RequestStatus status, 
            Pageable pageable);

    /**
     * Find orders by restaurant ID and order statuses
     */
    @Query("SELECT o FROM Order o WHERE o.restaurant.id = :restaurantId AND o.orderStatus IN :orderStatuses")
    List<Order> findByRestaurantIdAndOrderStatusIn(
            @Param("restaurantId") UUID restaurantId,
            @Param("orderStatuses") Collection<OrderStatus> orderStatuses);

    /**
     * Find orders by restaurant with optional filters, search, and date range
     * Filters by order.createdAt (orderDateTime) instead of transaction.createdAt
     */
    @Query("SELECT o FROM Order o " +
           "LEFT JOIN Transaction t ON t.order = o " +
           "LEFT JOIN o.restaurantTable rt " +
           "LEFT JOIN rt.restaurantRow rr " +
           "LEFT JOIN rr.restaurantSection rs " +
           "WHERE o.restaurant.id = :restaurantId " +
           "AND ((:orderStatuses) IS NULL OR o.orderStatus IN (:orderStatuses)) " +
           "AND ((:orderTypes) IS NULL OR o.orderType IN (:orderTypes)) " +
           "AND ((:transactionStatuses) IS NULL OR t.transactionStatus IN (:transactionStatuses)) " +
           "AND ((:paymentMethods) IS NULL OR t.paymentMethod IN (:paymentMethods)) " +
           "AND (:sectionId IS NULL OR rs.id = :sectionId) " +
           "AND (:likePatternLower IS NULL OR :likePatternLower = '' OR (LOWER(o.orderNumber) LIKE :likePatternLower " +
           "     OR LOWER(t.transactionNumber) LIKE :likePatternLower " +
           "     OR (o.restaurantTable IS NOT NULL AND LOWER(o.restaurantTable.tableCode) LIKE :likePatternLower))) " +
           "AND (o.createdAt >= :startDate) " +
           "AND (o.createdAt <= :endDate) " +
           "AND (:onlyWithFeedback = false OR EXISTS (SELECT 1 FROM Rating r WHERE r.order.id = o.id))")
    Page<Order> findByRestaurantIdWithFilters(
            @Param("restaurantId") UUID restaurantId,
            @Param("orderStatuses") Collection<OrderStatus> orderStatuses,
            @Param("orderTypes") Collection<OrderType> orderTypes,
            @Param("transactionStatuses") Collection<TransactionStatus> transactionStatuses,
            @Param("paymentMethods") Collection<String> paymentMethods,
            @Param("sectionId") UUID sectionId,
            @Param("likePatternLower") String likePatternLower,
            @Param("startDate") java.time.OffsetDateTime startDate,
            @Param("endDate") java.time.OffsetDateTime endDate,
            @Param("onlyWithFeedback") boolean onlyWithFeedback,
            Pageable pageable);

    /**
     * Count ORDER discount usage by restaurant (orders with ORDER discount applied) via transactions
     * Excludes only when full refund (RefundType = FULL), keeps order discount for partial refunds
     */
    @Query("SELECT COUNT(DISTINCT o.id) FROM Transaction t " +
           "JOIN t.order o " +
           "JOIN t.restaurant r " +
           "LEFT JOIN Refund rf ON rf.transaction.id = t.id " +
           "WHERE r.id = :restaurantId " +
           "AND t.transactionStatus IN ('COMPLETED', 'REFUNDED', 'PARTIALLY_REFUNDED') " +
           "AND o.discount IS NOT NULL " +
           "AND o.discount.appliedTo = 'ORDER' " +
           "AND o.discount.status = :status " +
           "AND o.discount.isDeleted = false " +
           "AND (r.isDeleted IS NULL OR r.isDeleted = false) " +
           "AND (rf.refundType IS NULL OR rf.refundType != 'FULL')")
    long countOrderDiscountUsageByRestaurantId(
            @Param("restaurantId") UUID restaurantId,
            @Param("status") EntityStatus status);

    /**
     * Count ORDER discount usage by restaurant within date range
     * Excludes only when full refund (RefundType = FULL), keeps order discount for partial refunds
     */
    @Query("SELECT COUNT(DISTINCT o.id) FROM Transaction t " +
           "JOIN t.order o " +
           "JOIN t.restaurant r " +
           "LEFT JOIN Refund rf ON rf.transaction.id = t.id " +
           "WHERE r.id = :restaurantId " +
           "AND t.transactionStatus IN ('COMPLETED', 'REFUNDED', 'PARTIALLY_REFUNDED') " +
           "AND o.discount IS NOT NULL " +
           "AND o.discount.appliedTo = 'ORDER' " +
           "AND o.discount.status = :status " +
           "AND o.discount.isDeleted = false " +
           "AND t.createdAt >= :startDate " +
           "AND t.createdAt <= :endDate " +
           "AND (r.isDeleted IS NULL OR r.isDeleted = false) " +
           "AND (rf.refundType IS NULL OR rf.refundType != 'FULL')")
    long countOrderDiscountUsageByRestaurantIdAndDateRange(
            @Param("restaurantId") UUID restaurantId,
            @Param("status") EntityStatus status,
            @Param("startDate") java.time.LocalDateTime startDate,
            @Param("endDate") java.time.LocalDateTime endDate);

    /**
     * Calculate ORDER discount revenue impact by restaurant
     * Only subtracts discount amount when full refund (RefundType = FULL), keeps for partial refunds
     */
    @Query("SELECT COALESCE(SUM(" +
           "  CASE " +
           "    WHEN rf.refundType = 'FULL' THEN 0 " +
           "    ELSE o.discountAmount " +
           "  END" +
           "), 0) FROM Transaction t " +
           "JOIN t.order o " +
           "JOIN t.restaurant r " +
           "LEFT JOIN Refund rf ON rf.transaction.id = t.id " +
           "WHERE r.id = :restaurantId " +
           "AND t.transactionStatus IN ('COMPLETED', 'REFUNDED', 'PARTIALLY_REFUNDED') " +
           "AND o.discount IS NOT NULL " +
           "AND o.discount.appliedTo = 'ORDER' " +
           "AND o.discount.status = :status " +
           "AND o.discount.isDeleted = false " +
           "AND o.discountAmount IS NOT NULL " +
           "AND (r.isDeleted IS NULL OR r.isDeleted = false)")
    java.math.BigDecimal sumOrderDiscountRevenueImpactByRestaurantId(
            @Param("restaurantId") UUID restaurantId,
            @Param("status") EntityStatus status);

    /**
     * Calculate ORDER discount revenue impact by restaurant within date range
     * Only subtracts discount amount when full refund (RefundType = FULL), keeps for partial refunds
     */
    @Query("SELECT COALESCE(SUM(" +
           "  CASE " +
           "    WHEN rf.refundType = 'FULL' THEN 0 " +
           "    ELSE o.discountAmount " +
           "  END" +
           "), 0) FROM Transaction t " +
           "JOIN t.order o " +
           "JOIN t.restaurant r " +
           "LEFT JOIN Refund rf ON rf.transaction.id = t.id " +
           "WHERE r.id = :restaurantId " +
           "AND t.transactionStatus IN ('COMPLETED', 'REFUNDED', 'PARTIALLY_REFUNDED') " +
           "AND o.discount IS NOT NULL " +
           "AND o.discount.appliedTo = 'ORDER' " +
           "AND o.discount.status = :status " +
           "AND o.discount.isDeleted = false " +
           "AND o.discountAmount IS NOT NULL " +
           "AND t.createdAt >= :startDate " +
           "AND t.createdAt <= :endDate " +
           "AND (r.isDeleted IS NULL OR r.isDeleted = false)")
    java.math.BigDecimal sumOrderDiscountRevenueImpactByRestaurantIdAndDateRange(
            @Param("restaurantId") UUID restaurantId,
            @Param("status") EntityStatus status,
            @Param("startDate") java.time.LocalDateTime startDate,
            @Param("endDate") java.time.LocalDateTime endDate);

    /**
     * Count orders by status (PUSHED, IN_PROGRESS, SERVED, CANCELED)
     * Filtered by restaurant ID or restaurant group ID and date range
     * Uses sentinel UUID '00000000-0000-0000-0000-000000000000' to handle null parameters
     * Uses sentinel date '1970-01-01 00:00:00' to handle null date parameters
     * - Uses updated_at for date filtering to correctly count orders when they reached their final status
     *   (e.g., SERVED or CANCELED), not when they were initially created. This ensures accurate
     *   cancellation percentage calculations for orders that were cancelled or served on a different
     *   day than they were created.
     * - Date bounds are interpreted as UTC wall times so they match RestaurantAlertEvaluationService; avoids
     *   CAST(updated_at AS timestamp), which depends on the DB session TimeZone when updated_at is TIMESTAMPTZ.
     *   Upper bound remains inclusive (<= end) for callers that pass end-of-day (e.g. 23:59:59).
     */
    @Query(value = "SELECT COUNT(DISTINCT o.id) FROM orders o " +
           "JOIN restaurant r ON r.id = o.restaurant_id " +
           "WHERE o.order_status = :orderStatus " +
           "AND (CAST(:restaurantId AS uuid) = '00000000-0000-0000-0000-000000000000'::uuid OR r.id = CAST(:restaurantId AS uuid)) " +
           "AND (CAST(:restaurantGroupId AS uuid) = '00000000-0000-0000-0000-000000000000'::uuid OR r.restaurant_group_id = CAST(:restaurantGroupId AS uuid)) " +
           "AND (r.is_deleted IS NULL OR r.is_deleted = false) " +
           "AND (CAST(:startDate AS timestamp) = '1970-01-01 00:00:00'::timestamp OR o.updated_at >= (CAST(:startDate AS timestamp) AT TIME ZONE 'UTC')) " +
           "AND (CAST(:endDate AS timestamp) = '1970-01-01 00:00:00'::timestamp OR o.updated_at <= (CAST(:endDate AS timestamp) AT TIME ZONE 'UTC'))", nativeQuery = true)
    long countByOrderStatusAndFilters(
            @Param("orderStatus") String orderStatus,
            @Param("restaurantId") UUID restaurantId,
            @Param("restaurantGroupId") UUID restaurantGroupId,
            @Param("startDate") java.time.LocalDateTime startDate,
            @Param("endDate") java.time.LocalDateTime endDate);

    /**
     * Count CANCELED orders with non-zero total amount (excludes hold/push cancellations with $0 total).
     * Same filters as countByOrderStatusAndFilters; used for cancellation % so only revenue-relevant
     * cancellations are counted.
     */
    @Query(value = "SELECT COUNT(DISTINCT o.id) FROM orders o " +
           "JOIN restaurant r ON r.id = o.restaurant_id " +
           "WHERE o.order_status = 'CANCELED' " +
           "AND (o.total_amount IS NOT NULL AND o.total_amount > 0) " +
           "AND (CAST(:restaurantId AS uuid) = '00000000-0000-0000-0000-000000000000'::uuid OR r.id = CAST(:restaurantId AS uuid)) " +
           "AND (CAST(:restaurantGroupId AS uuid) = '00000000-0000-0000-0000-000000000000'::uuid OR r.restaurant_group_id = CAST(:restaurantGroupId AS uuid)) " +
           "AND (r.is_deleted IS NULL OR r.is_deleted = false) " +
           "AND (CAST(:startDate AS timestamp) = '1970-01-01 00:00:00'::timestamp OR o.updated_at >= (CAST(:startDate AS timestamp) AT TIME ZONE 'UTC')) " +
           "AND (CAST(:endDate AS timestamp) = '1970-01-01 00:00:00'::timestamp OR o.updated_at <= (CAST(:endDate AS timestamp) AT TIME ZONE 'UTC'))", nativeQuery = true)
    long countCanceledOrdersWithNonZeroTotalByFilters(
            @Param("restaurantId") UUID restaurantId,
            @Param("restaurantGroupId") UUID restaurantGroupId,
            @Param("startDate") java.time.LocalDateTime startDate,
            @Param("endDate") java.time.LocalDateTime endDate);

    /**
     * Count distinct tables used in orders within a date range
     * Filtered by restaurant ID or restaurant group ID
     * Uses sentinel UUID '00000000-0000-0000-0000-000000000000' to handle null parameters
     * Uses sentinel date '1970-01-01 00:00:00' to handle null date parameters
     */
    @Query(value = "SELECT COUNT(DISTINCT o.restaurant_table_id) FROM orders o " +
           "JOIN restaurant r ON r.id = o.restaurant_id " +
           "WHERE (CAST(:restaurantId AS uuid) = '00000000-0000-0000-0000-000000000000'::uuid OR r.id = CAST(:restaurantId AS uuid)) " +
           "AND (CAST(:restaurantGroupId AS uuid) = '00000000-0000-0000-0000-000000000000'::uuid OR r.restaurant_group_id = CAST(:restaurantGroupId AS uuid)) " +
           "AND (r.is_deleted IS NULL OR r.is_deleted = false) " +
           "AND (CAST(:startDate AS timestamp) = '1970-01-01 00:00:00'::timestamp OR o.created_at >= CAST(:startDate AS timestamp)) " +
           "AND (CAST(:endDate AS timestamp) = '1970-01-01 00:00:00'::timestamp OR o.created_at <= CAST(:endDate AS timestamp))", nativeQuery = true)
    long countDistinctTablesUsedInDateRange(
            @Param("restaurantId") UUID restaurantId,
            @Param("restaurantGroupId") UUID restaurantGroupId,
            @Param("startDate") java.time.LocalDateTime startDate,
            @Param("endDate") java.time.LocalDateTime endDate);

    /**
     * Get hourly order counts grouped by hour
     * Returns list of [hour, orderCount] as Object arrays
     * Filtered by restaurant ID or restaurant group ID and date range
     * Uses sentinel UUID '00000000-0000-0000-0000-000000000000' to handle null parameters
     * Uses sentinel date '1970-01-01 00:00:00' to handle null date parameters
     */
    @Query(value = "SELECT EXTRACT(HOUR FROM o.created_at) as hour, COUNT(o.id) as order_count " +
           "FROM orders o " +
           "JOIN restaurant r ON r.id = o.restaurant_id " +
           "WHERE (CAST(:restaurantId AS uuid) = '00000000-0000-0000-0000-000000000000'::uuid OR r.id = CAST(:restaurantId AS uuid)) " +
           "AND (CAST(:restaurantGroupId AS uuid) = '00000000-0000-0000-0000-000000000000'::uuid OR r.restaurant_group_id = CAST(:restaurantGroupId AS uuid)) " +
           "AND (r.is_deleted IS NULL OR r.is_deleted = false) " +
           "AND (CAST(:startDate AS timestamp) = '1970-01-01 00:00:00'::timestamp OR o.created_at >= CAST(:startDate AS timestamp)) " +
           "AND (CAST(:endDate AS timestamp) = '1970-01-01 00:00:00'::timestamp OR o.created_at <= CAST(:endDate AS timestamp)) " +
           "GROUP BY EXTRACT(HOUR FROM o.created_at) " +
           "ORDER BY hour", nativeQuery = true)
    List<Object[]> getHourlyOrderCounts(
            @Param("restaurantId") UUID restaurantId,
            @Param("restaurantGroupId") UUID restaurantGroupId,
            @Param("startDate") java.time.LocalDateTime startDate,
            @Param("endDate") java.time.LocalDateTime endDate);

    /**
     * Get table-wise sales report
     * Returns: [tableId, totalOrders, totalSales, averageOrderValue, totalTax, totalServiceCharge]
     * Filters by specific restaurantId only.
     *
     * IMPORTANT:
     * - Includes COMPLETED, REFUNDED and PARTIALLY_REFUNDED transactions.
     * - Treats refunds as adjustments, not deletions, by subtracting the refund amount from the original transaction amount.
     *   Net sales per order = t.transaction_amount - rf.total_refund_amount.
     *   Net tax = o.tax_amount - rf.tax_refund_amount.
     *   Net service charge = o.service_charge_amount - rf.service_charge_refund_amount.
     * Uses sentinel date '1970-01-01 00:00:00' to handle null date parameters.
     * Groups by table_id only to avoid duplicate table entries.
     */
    @Query(value = "WITH order_level_data AS ( " +
           "    SELECT " +
           "        o.id as order_id, " +
           "        o.restaurant_table_id, " +
           "        COALESCE(o.tax_amount, 0) as order_tax, " +
           "        COALESCE(o.service_charge_amount, 0) as order_service_charge " +
           "    FROM orders o " +
           "    JOIN restaurant r ON r.id = o.restaurant_id " +
           "    WHERE r.id = CAST(:restaurantId AS uuid) " +
           "    AND (r.is_deleted IS NULL OR r.is_deleted = false) " +
           "), " +
           "order_refund_aggregates AS ( " +
           "    SELECT " +
           "        t.order_id, " +
           "        SUM(t.transaction_amount - COALESCE(rf.total_refund_amount, 0)) as net_sales, " +
           "        SUM(COALESCE(rf.tax_refund_amount, 0)) as total_tax_refunded, " +
           "        SUM(COALESCE(rf.service_charge_refund_amount, 0)) as total_service_charge_refunded " +
           "    FROM transaction t " +
           "    LEFT JOIN refund rf ON rf.transaction_id = t.id " +
           "    WHERE t.transaction_status IN ('COMPLETED', 'REFUNDED', 'PARTIALLY_REFUNDED') " +
           "    AND (CAST(:startDate AS timestamp) = '1970-01-01 00:00:00'::timestamp OR t.created_at >= CAST(:startDate AS timestamp)) " +
           "    AND (CAST(:endDate AS timestamp) = '1970-01-01 00:00:00'::timestamp OR t.created_at <= CAST(:endDate AS timestamp)) " +
           "    GROUP BY t.order_id " +
           ") " +
           "SELECT " +
           "    old.restaurant_table_id as table_id, " +
           "    COUNT(DISTINCT old.order_id) as total_orders, " +
           "    ROUND(COALESCE(SUM(ora.net_sales), 0), 2) as total_sales, " +
           "    CASE " +
           "        WHEN COUNT(DISTINCT old.order_id) > 0 THEN ROUND(COALESCE(SUM(ora.net_sales), 0) / COUNT(DISTINCT old.order_id), 2) " +
           "        ELSE 0 " +
           "    END as average_order_value, " +
           "    ROUND(COALESCE(SUM(old.order_tax - ora.total_tax_refunded), 0), 2) as total_tax, " +
           "    ROUND(COALESCE(SUM(old.order_service_charge - ora.total_service_charge_refunded), 0), 2) as total_service_charge " +
           "FROM order_level_data old " +
           "JOIN order_refund_aggregates ora ON ora.order_id = old.order_id " +
           "GROUP BY old.restaurant_table_id " +
           "ORDER BY total_sales DESC",
           nativeQuery = true)
    List<Object[]> getTableWiseSalesReport(
            @Param("restaurantId") UUID restaurantId,
            @Param("startDate") java.time.LocalDateTime startDate,
            @Param("endDate") java.time.LocalDateTime endDate);

    /**
     * Get discounts and promotions report
     * Returns: [discountType, discountCode, numberOfTransactions, totalDiscountApplied, totalRevenue, totalRevenueBeforeDiscount, discountEfficiency, appliedTo]
     * - discountType: Category ("Order", "Additional Discount", "Item", or "Category")
     * - discountCode: Specific discount code (e.g., "SUMMER20", "VIP_OFFER") - each unique discount code is shown separately with its own aggregated statistics
     * Filters by specific restaurantId only.
     *
     * IMPORTANT:
     * - Includes COMPLETED, REFUNDED and PARTIALLY_REFUNDED transactions.
     * - Treats refunds as adjustments, not deletions:
     *     - Net revenue = t.transaction_amount - rf.total_refund_amount
     *     - Net discount applied = (o.discount_amount + o.additional_discount_amount)
     *                               - (rf.discount_refund_amount + rf.additional_discount_refund_amount)
     * - Includes all discount types: Order-level, Additional Discount, Item-level, and Category-level discounts
     * - For item/category discounts: discount amount = total_item_amount - total_discounted_item_amount
     * Uses sentinel date '1970-01-01 00:00:00' to handle null date parameters.
     */
    /**
     * Get discounts and promotions report
     * Returns: [discountType, discountCode, numberOfTransactions, totalDiscountApplied, totalRevenue, totalRevenueBeforeDiscount, discountEfficiency, appliedTo]
     * - discountType: Category ("Order", "Additional Discount", "Item", or "Category")
     * - discountCode: Specific discount code (e.g., "SUMMER20", "VIP_OFFER") - each unique discount code is shown separately with its own aggregated statistics
     * Filters by specific restaurantId only.
     *
     * IMPORTANT:
     * - Includes COMPLETED, REFUNDED and PARTIALLY_REFUNDED transactions.
     * - Treats refunds as adjustments, not deletions:
     *     - Net revenue = t.transaction_amount - rf.total_refund_amount
     *     - Net discount applied = (o.discount_amount + o.additional_discount_amount)
     *                               - (rf.discount_refund_amount + rf.additional_discount_refund_amount)
     * - Includes all discount types: Order-level, Additional Discount, Item-level, and Category-level discounts
     * - For item/category discounts: discount amount = total_item_amount - total_discounted_item_amount
     * Uses sentinel date '1970-01-01 00:00:00' to handle null date parameters.
     */
    @Query(value = DISCOUNTS_PROMOTIONS_REPORT_QUERY, nativeQuery = true)
    List<Object[]> getDiscountsPromotionsReport(
            @Param("restaurantId") UUID restaurantId,
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate);

    /**
     * Get waiter performance report
     * Returns: [waiterId, waiterFirstName, waiterLastName, waiterCode, totalOrders, totalSales, averageOrderValue, totalTablesServed]
     * Filters by specific restaurantId only
     * Uses sentinel date '1970-01-01 00:00:00' to handle null date parameters
     * Only includes orders with completed transactions.
     *
     * WAITER MAPPING LOGIC:
     * - Prefer the waiter explicitly stored on the order (orders.waiter_id), which represents
     *   the waiter who actually placed/handled the order.
     * - For legacy/customer orders where orders.waiter_id is null, fall back to the most recent
     *   active table assignment (unassigned_at IS NULL) for that table.
     */
    @Query(value =
           "WITH waiter_orders AS ( " +
           "    SELECT " +
           "        o.id AS order_id, " +
           "        COALESCE(o.waiter_id, ta.waiter_id) AS waiter_id, " +
           "        o.restaurant_table_id, " +
           "        t.transaction_amount, " +
           "        t.created_at " +
           "    FROM orders o " +
           "    JOIN transaction t ON t.order_id = o.id " +
           "    JOIN restaurant r ON r.id = o.restaurant_id " +
           "    JOIN restaurant_table rt ON rt.id = o.restaurant_table_id " +
           "    LEFT JOIN ( " +
           "        SELECT DISTINCT ON (ta_inner.restaurant_table_id) " +
           "               ta_inner.restaurant_table_id, " +
           "               ta_inner.waiter_id " +
           "        FROM table_assignment ta_inner " +
           "        WHERE ta_inner.unassigned_at IS NULL " +
           "        ORDER BY ta_inner.restaurant_table_id, ta_inner.assigned_at DESC NULLS LAST " +
           "    ) ta ON ta.restaurant_table_id = o.restaurant_table_id " +
           "    WHERE t.transaction_status = 'COMPLETED' " +
           "      AND r.id = CAST(:restaurantId AS uuid) " +
           "      AND (r.is_deleted IS NULL OR r.is_deleted = false) " +
           "      AND (CAST(:startDate AS timestamp) = '1970-01-01 00:00:00'::timestamp OR t.created_at >= CAST(:startDate AS timestamp)) " +
           "      AND (CAST(:endDate AS timestamp) = '1970-01-01 00:00:00'::timestamp OR t.created_at <= CAST(:endDate AS timestamp)) " +
           ") " +
           "SELECT " +
           "    wo.waiter_id, " +
           "    u.first_name, " +
           "    u.last_name, " +
           "    u.user_code, " +
           "    COUNT(DISTINCT wo.order_id) AS total_orders, " +
           "    ROUND(COALESCE(SUM(wo.transaction_amount), 0), 2) AS total_sales, " +
           "    CASE " +
           "        WHEN COUNT(DISTINCT wo.order_id) > 0 THEN ROUND(COALESCE(SUM(wo.transaction_amount), 0) / COUNT(DISTINCT wo.order_id), 2) " +
           "        ELSE 0 " +
           "    END AS average_order_value, " +
           "    COUNT(DISTINCT wo.restaurant_table_id) AS total_tables_served " +
           "FROM waiter_orders wo " +
           "JOIN users u ON u.id = wo.waiter_id " +
           "WHERE wo.waiter_id IS NOT NULL " +
           "GROUP BY wo.waiter_id, u.first_name, u.last_name, u.user_code " +
           "ORDER BY total_sales DESC",
           nativeQuery = true)
    List<Object[]> getWaiterPerformanceReport(
            @Param("restaurantId") UUID restaurantId,
            @Param("startDate") java.time.LocalDateTime startDate,
            @Param("endDate") java.time.LocalDateTime endDate);

}