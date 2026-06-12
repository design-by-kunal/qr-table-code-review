package com.gulfnet.shared_library.repository;

import com.gulfnet.shared_library.entity.OrderedItem;
import com.gulfnet.shared_library.enums.RequestStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface OrderedItemRepository extends JpaRepository<OrderedItem, UUID> {

    List<OrderedItem> findByOrderId(UUID orderId);

    /**
     * Batch-load ordered items with {@link Order} eagerly fetched.
     * Avoids N+1 lazy loads when updating many items (e.g. bulk status API).
     */
    @Query("SELECT DISTINCT oi FROM OrderedItem oi JOIN FETCH oi.order WHERE oi.id IN :ids")
    List<OrderedItem> findAllByIdWithOrderFetched(@Param("ids") Collection<UUID> ids);
    
    @Query("SELECT oi FROM OrderedItem oi WHERE oi.orderedCombo.id = :orderedComboId")
    List<OrderedItem> findByOrderedComboId(@Param("orderedComboId") UUID orderedComboId);
    
    /**
     * Find all ordered items with the same discount application ID.
     * Used to find related BUY and GET items in a BXGY discount application.
     */
    List<OrderedItem> findByDiscountApplicationId(UUID discountApplicationId);
    
    /**
     * Find ordered item by ID with waiter information eagerly fetched.
     * Used for ticket details API to ensure waiter info is available.
     */
    @Query("SELECT DISTINCT oi FROM OrderedItem oi " +
           "LEFT JOIN FETCH oi.order o " +
           "LEFT JOIN FETCH o.waiter " +
           "LEFT JOIN FETCH o.restaurantTable rt " +
           "LEFT JOIN FETCH rt.restaurantRow rr " +
           "LEFT JOIN FETCH rr.restaurantSection rs " +
           "LEFT JOIN FETCH oi.item " +
           "LEFT JOIN FETCH oi.updatedBy " +
           "WHERE oi.id = :id")
    Optional<OrderedItem> findByIdWithWaiterInfo(@Param("id") UUID id);
    
    /**
     * Find ordered item by ID with all relationships needed for cancellation request response.
     * Used after entity manager clear to ensure all lazy-loaded relationships are available.
     */
    @Query("SELECT DISTINCT oi FROM OrderedItem oi " +
           "LEFT JOIN FETCH oi.item i " +
           "LEFT JOIN FETCH i.translations " +
           "LEFT JOIN FETCH oi.order o " +
           "LEFT JOIN FETCH o.restaurant r " +
           "LEFT JOIN FETCH r.translations " +
           "LEFT JOIN FETCH oi.cancellationRequestedBy " +
           "LEFT JOIN FETCH oi.cancellationReviewedBy " +
           "WHERE oi.id = :id")
    Optional<OrderedItem> findByIdWithRelationshipsForCancellationResponse(@Param("id") UUID id);

    @Query("SELECT oi.item.id FROM OrderedItem oi WHERE oi.id = :orderedItemId")
    Optional<UUID> findMenuItemIdByOrderedItemId(@Param("orderedItemId") UUID orderedItemId);

    /**
     * Batch-load regular ordered items (exclude combo items) for multiple orders.
     * Fetches item translations up-front to avoid N+1 during response building.
     */
    @Query("SELECT DISTINCT oi FROM OrderedItem oi " +
           "LEFT JOIN FETCH oi.item i " +
           "LEFT JOIN FETCH i.translations " +
           "LEFT JOIN FETCH oi.order o " +
           "WHERE o.id IN :orderIds " +
           "AND oi.orderedCombo IS NULL")
    List<OrderedItem> findRegularByOrderIds(@Param("orderIds") List<UUID> orderIds);
    
    Page<OrderedItem> findByCancellationRequestStatus(RequestStatus status, Pageable pageable);
    
    /**
     * Find ordered items by cancellation request status, with optional status filter.
     * If status is null, returns all items with request status != NONE.
     */
    @Query("SELECT oi FROM OrderedItem oi WHERE " +
           "(:status IS NULL AND oi.cancellationRequestStatus != com.gulfnet.shared_library.enums.RequestStatus.NONE) OR " +
           "(:status IS NOT NULL AND oi.cancellationRequestStatus = :status)")
    Page<OrderedItem> findByCancellationRequestStatusOptional(
            @Param("status") RequestStatus status, 
            Pageable pageable);

    /**
     * Find ordered items for ticket dashboard with comprehensive filtering
     * Base query - additional filtering will be done in service layer
     * Note: KDS dashboard should show items based on reset time only, not session expiration status
     * This query explicitly does NOT filter by session expiration (s.expired_at) to ensure items remain visible
     * even when sessions are expired (e.g., when tables are marked cleanup/available after checkout)
     * KDS dashboard should only reset based on configured reset time in HQ admin app, not table status changes
     * 
     * Fix: Include orders where either:
     * 1. Order was created after reset time, OR
     * 2. Session was issued after reset time (to handle cases where table was occupied before reset time
     *    but order is pushed after reset time, or order is pushed before reset time but session is after reset time), OR
     * 3. The ordered item itself was created after reset time (to handle cases where items are added to 
     *    existing orders that were created before reset time), OR
     * 4. The ordered item was updated after reset time (to handle cases where items were created yesterday
     *    but pushed to KDS today - status updated to PUSHED after reset time)
     * 
     * IMPORTANT: Eagerly fetch section relationship to avoid lazy loading issues when filtering by section.
     * This ensures getSectionIdFromItem() can reliably access the section without triggering lazy load exceptions.
     * 
     * Note: Uses native query to handle LocalDateTime to OffsetDateTime conversion correctly via CAST.
     * The section relationships are eagerly loaded via a separate batch fetch in the service layer.
     */
    @Query(value = "SELECT DISTINCT oi.* FROM ordered_item oi " +
           "JOIN orders o ON o.id = oi.order_id " +
           "JOIN sessions s ON s.id = o.session_id " +
           "WHERE o.restaurant_id = :restaurantId " +
           "AND (o.created_at >= COALESCE(CAST(:resetTime AS timestamp), '1970-01-01'::timestamp) " +
           "     OR s.issued_at >= COALESCE(CAST(:resetTime AS timestamp), '1970-01-01'::timestamp) " +
           "     OR oi.created_at >= COALESCE(CAST(:resetTime AS timestamp), '1970-01-01'::timestamp) " +
           "     OR oi.updated_at >= COALESCE(CAST(:resetTime AS timestamp), '1970-01-01'::timestamp))",
           nativeQuery = true)
    List<OrderedItem> findTicketDashboardItemsBase(
            @Param("restaurantId") UUID restaurantId,
            @Param("resetTime") LocalDateTime resetTime);

    /**
     * Same as {@link #findTicketDashboardItemsBase(UUID, LocalDateTime)} but optionally filters by item status in the DB.
     * <p>
     * IMPORTANT: Pass {@code null} for {@code itemStatuses} to disable the filter. Do NOT pass an empty list because
     * most SQL dialects treat {@code IN ()} as a syntax error.
     * </p>
     */
    @Query(value = "SELECT DISTINCT oi.* FROM ordered_item oi " +
           "JOIN orders o ON o.id = oi.order_id " +
           "JOIN sessions s ON s.id = o.session_id " +
           "WHERE o.restaurant_id = :restaurantId " +
           "AND (o.created_at >= COALESCE(CAST(:resetTime AS timestamp), '1970-01-01'::timestamp) " +
           "     OR s.issued_at >= COALESCE(CAST(:resetTime AS timestamp), '1970-01-01'::timestamp) " +
           "     OR oi.created_at >= COALESCE(CAST(:resetTime AS timestamp), '1970-01-01'::timestamp) " +
           "     OR oi.updated_at >= COALESCE(CAST(:resetTime AS timestamp), '1970-01-01'::timestamp)) " +
           "AND (:itemStatuses IS NULL OR oi.item_status IN (:itemStatuses))",
           nativeQuery = true)
    List<OrderedItem> findTicketDashboardItemsBaseWithItemStatuses(
            @Param("restaurantId") UUID restaurantId,
            @Param("resetTime") LocalDateTime resetTime,
            @Param("itemStatuses") List<String> itemStatuses);
    
    /**
     * Count ITEM discount usage (ordered items with item-level discount applied)
     * Matches the actual discount ID from OrderDiscountUsage to item's discount mappings
     * to determine which discount was actually applied (system chooses best discount)
     * Includes both regular item discounts and BXGY discounts
     * BXGY takes priority over item/category discounts
     * Excludes fully refunded items (where refund amount >= item amount)
     */
    @Query("SELECT COUNT(DISTINCT oi.id) FROM OrderedItem oi " +
           "JOIN oi.order o " +
           "JOIN Transaction t ON t.order.id = o.id " +
           "JOIN o.restaurant r " +
           "LEFT JOIN Refund rf ON rf.transaction.id = t.id " +
           "LEFT JOIN RefundItem ri ON ri.refund.id = rf.id AND ri.orderedItem.id = oi.id " +
           "WHERE r.restaurantGroup.id = :restaurantGroupId " +
           "AND t.transactionStatus IN ('COMPLETED', 'REFUNDED', 'PARTIALLY_REFUNDED') " +
           "AND oi.totalItemAmount IS NOT NULL " +
           "AND (r.isDeleted IS NULL OR r.isDeleted = false) " +
           "AND (rf.refundType IS NULL OR rf.refundType != 'FULL') " +
           "AND (ri.refundAmount IS NULL OR ri.refundAmount < oi.totalItemAmount) " +
           "AND (" +
           "  (oi.totalDiscountedItemAmount IS NOT NULL " +
           "   AND oi.totalItemAmount > oi.totalDiscountedItemAmount) " +
           "  OR " +
           "  (oi.totalDiscountedItemAmount IS NULL)) " +
           "AND EXISTS (SELECT 1 FROM OrderDiscountUsage odu " +
           "            WHERE odu.order.id = o.id " +
           "            AND odu.appliedTo = 'ITEM' " +
           "            AND odu.transaction.id = t.id " +
           "            AND (" +
           "              EXISTS (SELECT 1 FROM ItemDiscountMapping idm " +
           "                      WHERE idm.categoryItemMapping.item.id = oi.item.id " +
           "                      AND idm.discount.id = odu.discount.id " +
           "                      AND idm.discount.status = :status " +
           "                      AND idm.discount.isDeleted = false) " +
           "              OR " +
           "              EXISTS (SELECT 1 FROM DiscountBxgyItem dbxi " +
           "                      JOIN dbxi.getItemMapping gim " +
           "                      WHERE gim.item.id = oi.item.id " +
           "                      AND dbxi.discount.id = odu.discount.id " +
           "                      AND dbxi.discount.status = :status " +
           "                      AND dbxi.discount.isDeleted = false)))")
    long countItemDiscountUsageByRestaurantGroupId(
            @Param("restaurantGroupId") UUID restaurantGroupId,
            @Param("status") com.gulfnet.shared_library.enums.EntityStatus status);
    
    /**
     * Count ITEM discount usage within date range
     * Matches the actual discount ID from OrderDiscountUsage to item's discount mappings
     * to determine which discount was actually applied (system chooses best discount)
     * Includes both regular item discounts and BXGY discounts
     * BXGY takes priority over item/category discounts
     * Excludes fully refunded items (where refund amount >= item amount)
     */
    @Query("SELECT COUNT(DISTINCT oi.id) FROM OrderedItem oi " +
           "JOIN oi.order o " +
           "JOIN Transaction t ON t.order.id = o.id " +
           "JOIN o.restaurant r " +
           "LEFT JOIN Refund rf ON rf.transaction.id = t.id " +
           "LEFT JOIN RefundItem ri ON ri.refund.id = rf.id AND ri.orderedItem.id = oi.id " +
           "WHERE r.restaurantGroup.id = :restaurantGroupId " +
           "AND t.transactionStatus IN ('COMPLETED', 'REFUNDED', 'PARTIALLY_REFUNDED') " +
           "AND oi.totalItemAmount IS NOT NULL " +
           "AND t.createdAt >= :startDate " +
           "AND t.createdAt <= :endDate " +
           "AND (r.isDeleted IS NULL OR r.isDeleted = false) " +
           "AND (rf.refundType IS NULL OR rf.refundType != 'FULL') " +
           "AND (ri.refundAmount IS NULL OR ri.refundAmount < oi.totalItemAmount) " +
           "AND (" +
           "  (oi.totalDiscountedItemAmount IS NOT NULL " +
           "   AND oi.totalItemAmount > oi.totalDiscountedItemAmount) " +
           "  OR " +
           "  (oi.totalDiscountedItemAmount IS NULL)) " +
           "AND EXISTS (SELECT 1 FROM OrderDiscountUsage odu " +
           "            WHERE odu.order.id = o.id " +
           "            AND odu.appliedTo = 'ITEM' " +
           "            AND odu.transaction.id = t.id " +
           "            AND (" +
           "              EXISTS (SELECT 1 FROM ItemDiscountMapping idm " +
           "                      WHERE idm.categoryItemMapping.item.id = oi.item.id " +
           "                      AND idm.discount.id = odu.discount.id " +
           "                      AND idm.discount.status = :status " +
           "                      AND idm.discount.isDeleted = false) " +
           "              OR " +
           "              EXISTS (SELECT 1 FROM DiscountBxgyItem dbxi " +
           "                      JOIN dbxi.getItemMapping gim " +
           "                      WHERE gim.item.id = oi.item.id " +
           "                      AND dbxi.discount.id = odu.discount.id " +
           "                      AND dbxi.discount.status = :status " +
           "                      AND dbxi.discount.isDeleted = false)))")
    long countItemDiscountUsageByRestaurantGroupIdAndDateRange(
            @Param("restaurantGroupId") UUID restaurantGroupId,
            @Param("status") com.gulfnet.shared_library.enums.EntityStatus status,
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate);
    
    /**
     * Count ITEM discount usage (all-time, no restaurant group filter)
     * Matches OrderDiscountUsage discount IDs to ItemDiscountMapping or DiscountBxgyItem to determine which discount was applied
     * Includes both regular item discounts and BXGY discounts
     * Excludes fully refunded items (where refund amount >= item amount)
     */
    @Query("SELECT COUNT(DISTINCT oi.id) FROM OrderedItem oi " +
           "JOIN oi.order o " +
           "JOIN Transaction t ON t.order.id = o.id " +
           "LEFT JOIN Refund rf ON rf.transaction.id = t.id " +
           "LEFT JOIN RefundItem ri ON ri.refund.id = rf.id AND ri.orderedItem.id = oi.id " +
           "WHERE t.transactionStatus IN ('COMPLETED', 'REFUNDED', 'PARTIALLY_REFUNDED') " +
           "AND oi.totalItemAmount IS NOT NULL " +
           "AND (rf.refundType IS NULL OR rf.refundType != 'FULL') " +
           "AND (ri.refundAmount IS NULL OR ri.refundAmount < oi.totalItemAmount) " +
           "AND (" +
           "  (oi.totalDiscountedItemAmount IS NOT NULL " +
           "   AND oi.totalItemAmount > oi.totalDiscountedItemAmount) " +
           "  OR " +
           "  (oi.totalDiscountedItemAmount IS NULL)) " +
           "AND EXISTS (SELECT 1 FROM OrderDiscountUsage odu " +
           "            WHERE odu.order.id = o.id " +
           "            AND odu.appliedTo = 'ITEM' " +
           "            AND odu.transaction.id = t.id " +
           "            AND (" +
           "              EXISTS (SELECT 1 FROM ItemDiscountMapping idm " +
           "                      WHERE idm.categoryItemMapping.item.id = oi.item.id " +
           "                      AND idm.discount.id = odu.discount.id) " +
           "              OR " +
           "              EXISTS (SELECT 1 FROM DiscountBxgyItem dbxi " +
           "                      JOIN dbxi.getItemMapping gim " +
           "                      WHERE gim.item.id = oi.item.id " +
           "                      AND dbxi.discount.id = odu.discount.id)))")
    long countItemDiscountUsage(@Param("status") com.gulfnet.shared_library.enums.EntityStatus status);
    
    /**
     * Count ITEM discount usage within date range (no restaurant group filter)
     * Matches OrderDiscountUsage discount IDs to ItemDiscountMapping or DiscountBxgyItem to determine which discount was applied
     * Includes both regular item discounts and BXGY discounts
     * Excludes fully refunded items (where refund amount >= item amount)
     */
    @Query("SELECT COUNT(DISTINCT oi.id) FROM OrderedItem oi " +
           "JOIN oi.order o " +
           "JOIN Transaction t ON t.order.id = o.id " +
           "LEFT JOIN Refund rf ON rf.transaction.id = t.id " +
           "LEFT JOIN RefundItem ri ON ri.refund.id = rf.id AND ri.orderedItem.id = oi.id " +
           "WHERE t.transactionStatus IN ('COMPLETED', 'REFUNDED', 'PARTIALLY_REFUNDED') " +
           "AND oi.totalItemAmount IS NOT NULL " +
           "AND t.createdAt >= :startDate " +
           "AND t.createdAt <= :endDate " +
           "AND (rf.refundType IS NULL OR rf.refundType != 'FULL') " +
           "AND (ri.refundAmount IS NULL OR ri.refundAmount < oi.totalItemAmount) " +
           "AND (" +
           "  (oi.totalDiscountedItemAmount IS NOT NULL " +
           "   AND oi.totalItemAmount > oi.totalDiscountedItemAmount) " +
           "  OR " +
           "  (oi.totalDiscountedItemAmount IS NULL)) " +
           "AND EXISTS (SELECT 1 FROM OrderDiscountUsage odu " +
           "            WHERE odu.order.id = o.id " +
           "            AND odu.appliedTo = 'ITEM' " +
           "            AND odu.transaction.id = t.id " +
           "            AND (" +
           "              EXISTS (SELECT 1 FROM ItemDiscountMapping idm " +
           "                      WHERE idm.categoryItemMapping.item.id = oi.item.id " +
           "                      AND idm.discount.id = odu.discount.id) " +
           "              OR " +
           "              EXISTS (SELECT 1 FROM DiscountBxgyItem dbxi " +
           "                      JOIN dbxi.getItemMapping gim " +
           "                      WHERE gim.item.id = oi.item.id " +
           "                      AND dbxi.discount.id = odu.discount.id)))")
    long countItemDiscountUsageByDateRange(
            @Param("status") com.gulfnet.shared_library.enums.EntityStatus status,
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate);
    
    /**
     * Count CATEGORY discount usage (ordered items with category-level discount applied)
     * Matches the actual discount ID from OrderDiscountUsage to item's category discount mappings
     * to determine which discount was actually applied (system chooses best discount)
     * Only counts if the discount ID matches category discount (not item discount or BXGY)
     * Excludes fully refunded items (where refund amount >= item amount)
     */
    @Query("SELECT COUNT(DISTINCT oi.id) FROM OrderedItem oi " +
           "JOIN oi.order o " +
           "JOIN Transaction t ON t.order.id = o.id " +
           "JOIN o.restaurant r " +
           "JOIN CategoryItemMapping cim ON cim.item.id = oi.item.id " +
           "LEFT JOIN Refund rf ON rf.transaction.id = t.id " +
           "LEFT JOIN RefundItem ri ON ri.refund.id = rf.id AND ri.orderedItem.id = oi.id " +
           "WHERE r.restaurantGroup.id = :restaurantGroupId " +
           "AND t.transactionStatus IN ('COMPLETED', 'REFUNDED', 'PARTIALLY_REFUNDED') " +
           "AND oi.totalItemAmount IS NOT NULL " +
           "AND oi.totalDiscountedItemAmount IS NOT NULL " +
           "AND oi.totalItemAmount > oi.totalDiscountedItemAmount " +
           "AND (r.isDeleted IS NULL OR r.isDeleted = false) " +
           "AND (rf.refundType IS NULL OR rf.refundType != 'FULL') " +
           "AND (ri.refundAmount IS NULL OR ri.refundAmount < oi.totalItemAmount) " +
           "AND EXISTS (SELECT 1 FROM OrderDiscountUsage odu " +
           "            JOIN CategoryDiscountMapping cdm ON cdm.discount.id = odu.discount.id " +
           "            WHERE odu.order.id = o.id " +
           "            AND odu.appliedTo = 'CATEGORY' " +
           "            AND odu.transaction.id = t.id " +
           "            AND cdm.menuCategoryMapping.id = cim.menuCategoryMapping.id " +
           "            AND cdm.discount.status = :status " +
           "            AND cdm.discount.isDeleted = false " +
           "            AND NOT EXISTS (SELECT 1 FROM ItemDiscountMapping idm " +
           "                          WHERE idm.categoryItemMapping.item.id = oi.item.id " +
           "                          AND idm.discount.id = odu.discount.id " +
           "                          AND idm.discount.status = :status " +
           "                          AND idm.discount.isDeleted = false) " +
           "            AND NOT EXISTS (SELECT 1 FROM DiscountBxgyItem dbxi " +
           "                          JOIN dbxi.getItemMapping gim " +
           "                          WHERE gim.item.id = oi.item.id " +
           "                          AND dbxi.discount.id = odu.discount.id " +
           "                          AND dbxi.discount.status = :status " +
           "                          AND dbxi.discount.isDeleted = false))")
    long countCategoryDiscountUsageByRestaurantGroupId(
            @Param("restaurantGroupId") UUID restaurantGroupId,
            @Param("status") com.gulfnet.shared_library.enums.EntityStatus status);
    
    /**
     * Count CATEGORY discount usage within date range
     * Matches the actual discount ID from OrderDiscountUsage to item's category discount mappings
     * to determine which discount was actually applied (system chooses best discount)
     * Only counts if the discount ID matches category discount (not item discount or BXGY)
     * Excludes fully refunded items (where refund amount >= item amount)
     */
    @Query("SELECT COUNT(DISTINCT oi.id) FROM OrderedItem oi " +
           "JOIN oi.order o " +
           "JOIN Transaction t ON t.order.id = o.id " +
           "JOIN o.restaurant r " +
           "JOIN CategoryItemMapping cim ON cim.item.id = oi.item.id " +
           "LEFT JOIN Refund rf ON rf.transaction.id = t.id " +
           "LEFT JOIN RefundItem ri ON ri.refund.id = rf.id AND ri.orderedItem.id = oi.id " +
           "WHERE r.restaurantGroup.id = :restaurantGroupId " +
           "AND t.transactionStatus IN ('COMPLETED', 'REFUNDED', 'PARTIALLY_REFUNDED') " +
           "AND oi.totalItemAmount IS NOT NULL " +
           "AND oi.totalDiscountedItemAmount IS NOT NULL " +
           "AND oi.totalItemAmount > oi.totalDiscountedItemAmount " +
           "AND t.createdAt >= :startDate " +
           "AND t.createdAt <= :endDate " +
           "AND (r.isDeleted IS NULL OR r.isDeleted = false) " +
           "AND (rf.refundType IS NULL OR rf.refundType != 'FULL') " +
           "AND (ri.refundAmount IS NULL OR ri.refundAmount < oi.totalItemAmount) " +
           "AND EXISTS (SELECT 1 FROM OrderDiscountUsage odu " +
           "            JOIN CategoryDiscountMapping cdm ON cdm.discount.id = odu.discount.id " +
           "            WHERE odu.order.id = o.id " +
           "            AND odu.appliedTo = 'CATEGORY' " +
           "            AND odu.transaction.id = t.id " +
           "            AND cdm.menuCategoryMapping.id = cim.menuCategoryMapping.id " +
           "            AND cdm.discount.status = :status " +
           "            AND cdm.discount.isDeleted = false " +
           "            AND NOT EXISTS (SELECT 1 FROM ItemDiscountMapping idm " +
           "                          WHERE idm.categoryItemMapping.item.id = oi.item.id " +
           "                          AND idm.discount.id = odu.discount.id " +
           "                          AND idm.discount.status = :status " +
           "                          AND idm.discount.isDeleted = false) " +
           "            AND NOT EXISTS (SELECT 1 FROM DiscountBxgyItem dbxi " +
           "                          JOIN dbxi.getItemMapping gim " +
           "                          WHERE gim.item.id = oi.item.id " +
           "                          AND dbxi.discount.id = odu.discount.id " +
           "                          AND dbxi.discount.status = :status " +
           "                          AND dbxi.discount.isDeleted = false))")
    long countCategoryDiscountUsageByRestaurantGroupIdAndDateRange(
            @Param("restaurantGroupId") UUID restaurantGroupId,
            @Param("status") com.gulfnet.shared_library.enums.EntityStatus status,
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate);
    
    /**
     * Count CATEGORY discount usage (all-time, no restaurant group filter)
     * Matches OrderDiscountUsage discount IDs to CategoryDiscountMapping to determine which discount was applied
     * Excludes fully refunded items (where refund amount >= item amount)
     */
    @Query("SELECT COUNT(DISTINCT oi.id) FROM OrderedItem oi " +
           "JOIN oi.order o " +
           "JOIN Transaction t ON t.order.id = o.id " +
           "JOIN CategoryItemMapping cim ON cim.item.id = oi.item.id " +
           "LEFT JOIN Refund rf ON rf.transaction.id = t.id " +
           "LEFT JOIN RefundItem ri ON ri.refund.id = rf.id AND ri.orderedItem.id = oi.id " +
           "WHERE t.transactionStatus IN ('COMPLETED', 'REFUNDED', 'PARTIALLY_REFUNDED') " +
           "AND oi.totalItemAmount IS NOT NULL " +
           "AND oi.totalDiscountedItemAmount IS NOT NULL " +
           "AND oi.totalItemAmount > oi.totalDiscountedItemAmount " +
           "AND (rf.refundType IS NULL OR rf.refundType != 'FULL') " +
           "AND (ri.refundAmount IS NULL OR ri.refundAmount < oi.totalItemAmount) " +
           "AND EXISTS (SELECT 1 FROM OrderDiscountUsage odu " +
           "            JOIN CategoryDiscountMapping cdm ON cdm.discount.id = odu.discount.id " +
           "            WHERE odu.order.id = o.id " +
           "            AND odu.appliedTo = 'CATEGORY' " +
           "            AND odu.transaction.id = t.id " +
           "            AND cdm.menuCategoryMapping.id = cim.menuCategoryMapping.id)")
    long countCategoryDiscountUsage(@Param("status") com.gulfnet.shared_library.enums.EntityStatus status);
    
    /**
     * Count CATEGORY discount usage within date range (no restaurant group filter)
     * Matches OrderDiscountUsage discount IDs to CategoryDiscountMapping to determine which discount was applied
     * Excludes fully refunded items (where refund amount >= item amount)
     */
    @Query("SELECT COUNT(DISTINCT oi.id) FROM OrderedItem oi " +
           "JOIN oi.order o " +
           "JOIN Transaction t ON t.order.id = o.id " +
           "JOIN CategoryItemMapping cim ON cim.item.id = oi.item.id " +
           "LEFT JOIN Refund rf ON rf.transaction.id = t.id " +
           "LEFT JOIN RefundItem ri ON ri.refund.id = rf.id AND ri.orderedItem.id = oi.id " +
           "WHERE t.transactionStatus IN ('COMPLETED', 'REFUNDED', 'PARTIALLY_REFUNDED') " +
           "AND oi.totalItemAmount IS NOT NULL " +
           "AND oi.totalDiscountedItemAmount IS NOT NULL " +
           "AND oi.totalItemAmount > oi.totalDiscountedItemAmount " +
           "AND t.createdAt >= :startDate " +
           "AND t.createdAt <= :endDate " +
           "AND (rf.refundType IS NULL OR rf.refundType != 'FULL') " +
           "AND (ri.refundAmount IS NULL OR ri.refundAmount < oi.totalItemAmount) " +
           "AND EXISTS (SELECT 1 FROM OrderDiscountUsage odu " +
           "            JOIN CategoryDiscountMapping cdm ON cdm.discount.id = odu.discount.id " +
           "            WHERE odu.order.id = o.id " +
           "            AND odu.appliedTo = 'CATEGORY' " +
           "            AND odu.transaction.id = t.id " +
           "            AND cdm.menuCategoryMapping.id = cim.menuCategoryMapping.id)")
    long countCategoryDiscountUsageByDateRange(
            @Param("status") com.gulfnet.shared_library.enums.EntityStatus status,
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate);
    
    /**
     * Calculate ITEM discount revenue impact (sum of discount amounts)
     * Includes both regular item discounts (totalItemAmount - totalDiscountedItemAmount) 
     * and BXGY discounts (totalItemAmount for free items where totalDiscountedItemAmount IS NULL)
     * Subtracts proportional refund amounts from discount impact
     */
    @Query("SELECT COALESCE(SUM(" +
           "  CASE " +
           "    WHEN oi.totalDiscountedItemAmount IS NOT NULL THEN " +
           "      (oi.totalItemAmount - oi.totalDiscountedItemAmount) - " +
           "      COALESCE((ri.refundAmount * (oi.totalItemAmount - oi.totalDiscountedItemAmount) / NULLIF(oi.totalItemAmount, 0)), 0) " +
           "    ELSE " +
           "      oi.totalItemAmount - COALESCE(ri.refundAmount, 0) " +
           "  END" +
           "), 0) FROM OrderedItem oi " +
           "JOIN oi.order o2 " +
           "JOIN Transaction t2 ON t2.order.id = o2.id " +
           "JOIN o2.restaurant r2 " +
           "LEFT JOIN Refund rf ON rf.transaction.id = t2.id " +
           "LEFT JOIN RefundItem ri ON ri.refund.id = rf.id AND ri.orderedItem.id = oi.id " +
           "WHERE r2.restaurantGroup.id = :restaurantGroupId " +
           "AND t2.transactionStatus IN ('COMPLETED', 'REFUNDED', 'PARTIALLY_REFUNDED') " +
           "AND oi.totalItemAmount IS NOT NULL " +
           "AND (r2.isDeleted IS NULL OR r2.isDeleted = false) " +
           "AND (rf.refundType IS NULL OR rf.refundType != 'FULL') " +
           "AND (ri.refundAmount IS NULL OR ri.refundAmount < oi.totalItemAmount) " +
           "AND (" +
           "  (oi.totalDiscountedItemAmount IS NOT NULL " +
           "   AND oi.totalItemAmount > oi.totalDiscountedItemAmount " +
           "   AND EXISTS (SELECT 1 FROM ItemDiscountMapping idm " +
           "               JOIN idm.discount d " +
           "               WHERE idm.categoryItemMapping.item.id = oi.item.id " +
           "               AND d.appliedTo = 'ITEM' " +
           "               AND d.status = :status " +
           "               AND d.isDeleted = false)) " +
           "  OR " +
           "  (oi.totalDiscountedItemAmount IS NULL " +
           "   AND EXISTS (SELECT 1 FROM DiscountBxgyItem dbxi " +
           "               JOIN dbxi.discount d " +
           "               JOIN dbxi.getItemMapping gim " +
           "               WHERE gim.item.id = oi.item.id " +
           "               AND d.discountType = 'BXGY' " +
           "               AND d.status = :status " +
           "               AND d.isDeleted = false))" +
           ")")
    java.math.BigDecimal sumItemDiscountRevenueImpactByRestaurantGroupId(
            @Param("restaurantGroupId") UUID restaurantGroupId,
            @Param("status") com.gulfnet.shared_library.enums.EntityStatus status);
    
    /**
     * Calculate ITEM discount revenue impact within date range
     * Includes both regular item discounts (totalItemAmount - totalDiscountedItemAmount) 
     * and BXGY discounts (totalItemAmount for free items where totalDiscountedItemAmount IS NULL)
     * Subtracts proportional refund amounts from discount impact
     */
    @Query("SELECT COALESCE(SUM(" +
           "  CASE " +
           "    WHEN oi.totalDiscountedItemAmount IS NOT NULL THEN " +
           "      (oi.totalItemAmount - oi.totalDiscountedItemAmount) - " +
           "      COALESCE((ri.refundAmount * (oi.totalItemAmount - oi.totalDiscountedItemAmount) / NULLIF(oi.totalItemAmount, 0)), 0) " +
           "    ELSE " +
           "      oi.totalItemAmount - COALESCE(ri.refundAmount, 0) " +
           "  END" +
           "), 0) FROM OrderedItem oi " +
           "JOIN oi.order o2 " +
           "JOIN Transaction t2 ON t2.order.id = o2.id " +
           "JOIN o2.restaurant r2 " +
           "LEFT JOIN Refund rf ON rf.transaction.id = t2.id " +
           "LEFT JOIN RefundItem ri ON ri.refund.id = rf.id AND ri.orderedItem.id = oi.id " +
           "WHERE r2.restaurantGroup.id = :restaurantGroupId " +
           "AND t2.transactionStatus IN ('COMPLETED', 'REFUNDED', 'PARTIALLY_REFUNDED') " +
           "AND oi.totalItemAmount IS NOT NULL " +
           "AND t2.createdAt >= :startDate " +
           "AND t2.createdAt <= :endDate " +
           "AND (r2.isDeleted IS NULL OR r2.isDeleted = false) " +
           "AND (ri.refundAmount IS NULL OR ri.refundAmount < oi.totalItemAmount) " +
           "AND (" +
           "  (oi.totalDiscountedItemAmount IS NOT NULL " +
           "   AND oi.totalItemAmount > oi.totalDiscountedItemAmount " +
           "   AND EXISTS (SELECT 1 FROM ItemDiscountMapping idm " +
           "               JOIN idm.discount d " +
           "               WHERE idm.categoryItemMapping.item.id = oi.item.id " +
           "               AND d.appliedTo = 'ITEM' " +
           "               AND d.status = :status " +
           "               AND d.isDeleted = false)) " +
           "  OR " +
           "  (oi.totalDiscountedItemAmount IS NULL " +
           "   AND EXISTS (SELECT 1 FROM DiscountBxgyItem dbxi " +
           "               JOIN dbxi.discount d " +
           "               JOIN dbxi.getItemMapping gim " +
           "               WHERE gim.item.id = oi.item.id " +
           "               AND d.discountType = 'BXGY' " +
           "               AND d.status = :status " +
           "               AND d.isDeleted = false))" +
           ")")
    java.math.BigDecimal sumItemDiscountRevenueImpactByRestaurantGroupIdAndDateRange(
            @Param("restaurantGroupId") UUID restaurantGroupId,
            @Param("status") com.gulfnet.shared_library.enums.EntityStatus status,
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate);
    
    /**
     * Calculate ITEM discount revenue impact (all-time, no restaurant group filter)
     * Includes both regular item discounts (totalItemAmount - totalDiscountedItemAmount) 
     * and BXGY discounts (totalItemAmount for free items where totalDiscountedItemAmount IS NULL)
     * Subtracts proportional refund amounts from discount impact
     */
    @Query("SELECT COALESCE(SUM(" +
           "  CASE " +
           "    WHEN oi.totalDiscountedItemAmount IS NOT NULL THEN " +
           "      (oi.totalItemAmount - oi.totalDiscountedItemAmount) - " +
           "      COALESCE((ri.refundAmount * (oi.totalItemAmount - oi.totalDiscountedItemAmount) / NULLIF(oi.totalItemAmount, 0)), 0) " +
           "    ELSE " +
           "      oi.totalItemAmount - COALESCE(ri.refundAmount, 0) " +
           "  END" +
           "), 0) FROM OrderedItem oi " +
           "JOIN oi.order o2 " +
           "JOIN Transaction t2 ON t2.order.id = o2.id " +
           "LEFT JOIN Refund rf ON rf.transaction.id = t2.id " +
           "LEFT JOIN RefundItem ri ON ri.refund.id = rf.id AND ri.orderedItem.id = oi.id " +
           "WHERE t2.transactionStatus IN ('COMPLETED', 'REFUNDED', 'PARTIALLY_REFUNDED') " +
           "AND oi.totalItemAmount IS NOT NULL " +
           "AND (rf.refundType IS NULL OR rf.refundType != 'FULL') " +
           "AND (ri.refundAmount IS NULL OR ri.refundAmount < oi.totalItemAmount) " +
           "AND (" +
           "  (oi.totalDiscountedItemAmount IS NOT NULL " +
           "   AND oi.totalItemAmount > oi.totalDiscountedItemAmount " +
           "   AND EXISTS (SELECT 1 FROM ItemDiscountMapping idm " +
           "               JOIN idm.discount d " +
           "               WHERE idm.categoryItemMapping.item.id = oi.item.id " +
           "               AND d.appliedTo = 'ITEM' " +
           "               AND d.status = :status " +
           "               AND d.isDeleted = false)) " +
           "  OR " +
           "  (oi.totalDiscountedItemAmount IS NULL " +
           "   AND EXISTS (SELECT 1 FROM DiscountBxgyItem dbxi " +
           "               JOIN dbxi.discount d " +
           "               JOIN dbxi.getItemMapping gim " +
           "               WHERE gim.item.id = oi.item.id " +
           "               AND d.discountType = 'BXGY' " +
           "               AND d.status = :status " +
           "               AND d.isDeleted = false))" +
           ")")
    java.math.BigDecimal sumItemDiscountRevenueImpact(@Param("status") com.gulfnet.shared_library.enums.EntityStatus status);
    
    /**
     * Calculate ITEM discount revenue impact within date range (no restaurant group filter)
     * Includes both regular item discounts (totalItemAmount - totalDiscountedItemAmount) 
     * and BXGY discounts (totalItemAmount for free items where totalDiscountedItemAmount IS NULL)
     * Subtracts proportional refund amounts from discount impact
     */
    @Query("SELECT COALESCE(SUM(" +
           "  CASE " +
           "    WHEN oi.totalDiscountedItemAmount IS NOT NULL THEN " +
           "      (oi.totalItemAmount - oi.totalDiscountedItemAmount) - " +
           "      COALESCE((ri.refundAmount * (oi.totalItemAmount - oi.totalDiscountedItemAmount) / NULLIF(oi.totalItemAmount, 0)), 0) " +
           "    ELSE " +
           "      oi.totalItemAmount - COALESCE(ri.refundAmount, 0) " +
           "  END" +
           "), 0) FROM OrderedItem oi " +
           "JOIN oi.order o2 " +
           "JOIN Transaction t2 ON t2.order.id = o2.id " +
           "LEFT JOIN Refund rf ON rf.transaction.id = t2.id " +
           "LEFT JOIN RefundItem ri ON ri.refund.id = rf.id AND ri.orderedItem.id = oi.id " +
           "WHERE t2.transactionStatus IN ('COMPLETED', 'REFUNDED', 'PARTIALLY_REFUNDED') " +
           "AND oi.totalItemAmount IS NOT NULL " +
           "AND t2.createdAt >= :startDate " +
           "AND t2.createdAt <= :endDate " +
           "AND (rf.refundType IS NULL OR rf.refundType != 'FULL') " +
           "AND (ri.refundAmount IS NULL OR ri.refundAmount < oi.totalItemAmount) " +
           "AND (" +
           "  (oi.totalDiscountedItemAmount IS NOT NULL " +
           "   AND oi.totalItemAmount > oi.totalDiscountedItemAmount " +
           "   AND EXISTS (SELECT 1 FROM ItemDiscountMapping idm " +
           "               JOIN idm.discount d " +
           "               WHERE idm.categoryItemMapping.item.id = oi.item.id " +
           "               AND d.appliedTo = 'ITEM' " +
           "               AND d.status = :status " +
           "               AND d.isDeleted = false)) " +
           "  OR " +
           "  (oi.totalDiscountedItemAmount IS NULL " +
           "   AND EXISTS (SELECT 1 FROM DiscountBxgyItem dbxi " +
           "               JOIN dbxi.discount d " +
           "               JOIN dbxi.getItemMapping gim " +
           "               WHERE gim.item.id = oi.item.id " +
           "               AND d.discountType = 'BXGY' " +
           "               AND d.status = :status " +
           "               AND d.isDeleted = false))" +
           ")")
    java.math.BigDecimal sumItemDiscountRevenueImpactByDateRange(
            @Param("status") com.gulfnet.shared_library.enums.EntityStatus status,
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate);
    
    /**
     * Calculate CATEGORY discount revenue impact by restaurant group
     * Subtracts proportional refund amounts from discount impact
     */
    @Query("SELECT COALESCE(SUM(" +
           "  (oi.totalItemAmount - oi.totalDiscountedItemAmount) - " +
           "  COALESCE((ri.refundAmount * (oi.totalItemAmount - oi.totalDiscountedItemAmount) / NULLIF(oi.totalItemAmount, 0)), 0)" +
           "), 0) FROM OrderedItem oi " +
           "JOIN oi.order o2 " +
           "JOIN Transaction t2 ON t2.order.id = o2.id " +
           "JOIN o2.restaurant r2 " +
           "JOIN CategoryItemMapping cim ON cim.item.id = oi.item.id " +
           "JOIN CategoryDiscountMapping cdm ON cdm.menuCategoryMapping.id = cim.menuCategoryMapping.id " +
           "JOIN cdm.discount d " +
           "LEFT JOIN Refund rf ON rf.transaction.id = t2.id " +
           "LEFT JOIN RefundItem ri ON ri.refund.id = rf.id AND ri.orderedItem.id = oi.id " +
           "WHERE r2.restaurantGroup.id = :restaurantGroupId " +
           "AND t2.transactionStatus IN ('COMPLETED', 'REFUNDED', 'PARTIALLY_REFUNDED') " +
           "AND oi.totalItemAmount IS NOT NULL " +
           "AND oi.totalDiscountedItemAmount IS NOT NULL " +
           "AND oi.totalItemAmount > oi.totalDiscountedItemAmount " +
           "AND d.appliedTo = 'CATEGORY' " +
           "AND d.status = :status " +
           "AND d.isDeleted = false " +
           "AND (r2.isDeleted IS NULL OR r2.isDeleted = false) " +
           "AND (rf.refundType IS NULL OR rf.refundType != 'FULL') " +
           "AND (ri.refundAmount IS NULL OR ri.refundAmount < oi.totalItemAmount)")
    java.math.BigDecimal sumCategoryDiscountRevenueImpactByRestaurantGroupId(
            @Param("restaurantGroupId") UUID restaurantGroupId,
            @Param("status") com.gulfnet.shared_library.enums.EntityStatus status);
    
    /**
     * Calculate CATEGORY discount revenue impact by restaurant group within date range
     * Subtracts proportional refund amounts from discount impact
     */
    @Query("SELECT COALESCE(SUM(" +
           "  (oi.totalItemAmount - oi.totalDiscountedItemAmount) - " +
           "  COALESCE((ri.refundAmount * (oi.totalItemAmount - oi.totalDiscountedItemAmount) / NULLIF(oi.totalItemAmount, 0)), 0)" +
           "), 0) FROM OrderedItem oi " +
           "JOIN oi.order o2 " +
           "JOIN Transaction t2 ON t2.order.id = o2.id " +
           "JOIN o2.restaurant r2 " +
           "JOIN CategoryItemMapping cim ON cim.item.id = oi.item.id " +
           "JOIN CategoryDiscountMapping cdm ON cdm.menuCategoryMapping.id = cim.menuCategoryMapping.id " +
           "JOIN cdm.discount d " +
           "LEFT JOIN Refund rf ON rf.transaction.id = t2.id " +
           "LEFT JOIN RefundItem ri ON ri.refund.id = rf.id AND ri.orderedItem.id = oi.id " +
           "WHERE r2.restaurantGroup.id = :restaurantGroupId " +
           "AND t2.transactionStatus IN ('COMPLETED', 'REFUNDED', 'PARTIALLY_REFUNDED') " +
           "AND oi.totalItemAmount IS NOT NULL " +
           "AND oi.totalDiscountedItemAmount IS NOT NULL " +
           "AND oi.totalItemAmount > oi.totalDiscountedItemAmount " +
           "AND d.appliedTo = 'CATEGORY' " +
           "AND d.status = :status " +
           "AND d.isDeleted = false " +
           "AND t2.createdAt >= :startDate " +
           "AND t2.createdAt <= :endDate " +
           "AND (r2.isDeleted IS NULL OR r2.isDeleted = false) " +
           "AND (rf.refundType IS NULL OR rf.refundType != 'FULL') " +
           "AND (ri.refundAmount IS NULL OR ri.refundAmount < oi.totalItemAmount)")
    java.math.BigDecimal sumCategoryDiscountRevenueImpactByRestaurantGroupIdAndDateRange(
            @Param("restaurantGroupId") UUID restaurantGroupId,
            @Param("status") com.gulfnet.shared_library.enums.EntityStatus status,
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate);
    
    /**
     * Calculate CATEGORY discount revenue impact (all-time, no restaurant group filter)
     * Subtracts proportional refund amounts from discount impact
     */
    @Query("SELECT COALESCE(SUM(" +
           "  (oi.totalItemAmount - oi.totalDiscountedItemAmount) - " +
           "  COALESCE((ri.refundAmount * (oi.totalItemAmount - oi.totalDiscountedItemAmount) / NULLIF(oi.totalItemAmount, 0)), 0)" +
           "), 0) FROM OrderedItem oi " +
           "JOIN oi.order o2 " +
           "JOIN Transaction t2 ON t2.order.id = o2.id " +
           "JOIN CategoryItemMapping cim ON cim.item.id = oi.item.id " +
           "JOIN CategoryDiscountMapping cdm ON cdm.menuCategoryMapping.id = cim.menuCategoryMapping.id " +
           "JOIN cdm.discount d " +
           "LEFT JOIN Refund rf ON rf.transaction.id = t2.id " +
           "LEFT JOIN RefundItem ri ON ri.refund.id = rf.id AND ri.orderedItem.id = oi.id " +
           "WHERE t2.transactionStatus IN ('COMPLETED', 'REFUNDED', 'PARTIALLY_REFUNDED') " +
           "AND oi.totalItemAmount IS NOT NULL " +
           "AND oi.totalDiscountedItemAmount IS NOT NULL " +
           "AND oi.totalItemAmount > oi.totalDiscountedItemAmount " +
           "AND d.appliedTo = 'CATEGORY' " +
           "AND d.status = :status " +
           "AND d.isDeleted = false " +
           "AND (rf.refundType IS NULL OR rf.refundType != 'FULL') " +
           "AND (ri.refundAmount IS NULL OR ri.refundAmount < oi.totalItemAmount)")
    java.math.BigDecimal sumCategoryDiscountRevenueImpact(@Param("status") com.gulfnet.shared_library.enums.EntityStatus status);
    
    /**
     * Calculate CATEGORY discount revenue impact within date range (no restaurant group filter)
     * Subtracts proportional refund amounts from discount impact
     */
    @Query("SELECT COALESCE(SUM(" +
           "  (oi.totalItemAmount - oi.totalDiscountedItemAmount) - " +
           "  COALESCE((ri.refundAmount * (oi.totalItemAmount - oi.totalDiscountedItemAmount) / NULLIF(oi.totalItemAmount, 0)), 0)" +
           "), 0) FROM OrderedItem oi " +
           "JOIN oi.order o2 " +
           "JOIN Transaction t2 ON t2.order.id = o2.id " +
           "JOIN CategoryItemMapping cim ON cim.item.id = oi.item.id " +
           "JOIN CategoryDiscountMapping cdm ON cdm.menuCategoryMapping.id = cim.menuCategoryMapping.id " +
           "JOIN cdm.discount d " +
           "LEFT JOIN Refund rf ON rf.transaction.id = t2.id " +
           "LEFT JOIN RefundItem ri ON ri.refund.id = rf.id AND ri.orderedItem.id = oi.id " +
           "WHERE t2.transactionStatus IN ('COMPLETED', 'REFUNDED', 'PARTIALLY_REFUNDED') " +
           "AND oi.totalItemAmount IS NOT NULL " +
           "AND oi.totalDiscountedItemAmount IS NOT NULL " +
           "AND oi.totalItemAmount > oi.totalDiscountedItemAmount " +
           "AND d.appliedTo = 'CATEGORY' " +
           "AND d.status = :status " +
           "AND d.isDeleted = false " +
           "AND t2.createdAt >= :startDate " +
           "AND t2.createdAt <= :endDate " +
           "AND (rf.refundType IS NULL OR rf.refundType != 'FULL') " +
           "AND (ri.refundAmount IS NULL OR ri.refundAmount < oi.totalItemAmount)")
    java.math.BigDecimal sumCategoryDiscountRevenueImpactByDateRange(
            @Param("status") com.gulfnet.shared_library.enums.EntityStatus status,
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate);

    /**
     * Count ITEM discount usage by restaurant (ordered items with item-level discount applied)
     * Matches discount ID from OrderDiscountUsage to item's discount mappings to determine which discount was applied
     * Includes both regular item discounts and BXGY discounts
     * Excludes items from fully refunded orders (RefundType = FULL) and fully refunded items
     */
    @Query("SELECT COUNT(DISTINCT oi.id) FROM OrderedItem oi " +
           "JOIN oi.order o " +
           "JOIN Transaction t ON t.order.id = o.id " +
           "JOIN o.restaurant r " +
           "LEFT JOIN Refund rf ON rf.transaction.id = t.id " +
           "LEFT JOIN RefundItem ri ON ri.refund.id = rf.id AND ri.orderedItem.id = oi.id " +
           "WHERE r.id = :restaurantId " +
           "AND t.transactionStatus IN ('COMPLETED', 'REFUNDED', 'PARTIALLY_REFUNDED') " +
           "AND oi.totalItemAmount IS NOT NULL " +
           "AND (r.isDeleted IS NULL OR r.isDeleted = false) " +
           "AND (rf.refundType IS NULL OR rf.refundType != 'FULL') " +
           "AND (ri.refundAmount IS NULL OR ri.refundAmount < oi.totalItemAmount) " +
           "AND (" +
           "  (oi.totalDiscountedItemAmount IS NOT NULL " +
           "   AND oi.totalItemAmount > oi.totalDiscountedItemAmount) " +
           "  OR " +
           "  (oi.totalDiscountedItemAmount IS NULL)) " +
           "AND EXISTS (SELECT 1 FROM OrderDiscountUsage odu " +
           "            WHERE odu.order.id = o.id " +
           "            AND odu.appliedTo = 'ITEM' " +
           "            AND odu.transaction.id = t.id " +
           "            AND (" +
           "              EXISTS (SELECT 1 FROM ItemDiscountMapping idm " +
           "                      WHERE idm.categoryItemMapping.item.id = oi.item.id " +
           "                      AND idm.discount.id = odu.discount.id " +
           "                      AND idm.discount.status = :status " +
           "                      AND idm.discount.isDeleted = false) " +
           "              OR " +
           "              EXISTS (SELECT 1 FROM DiscountBxgyItem dbxi " +
           "                      JOIN dbxi.getItemMapping gim " +
           "                      WHERE gim.item.id = oi.item.id " +
           "                      AND dbxi.discount.id = odu.discount.id " +
           "                      AND dbxi.discount.status = :status " +
           "                      AND dbxi.discount.isDeleted = false)))")
    long countItemDiscountUsageByRestaurantId(
            @Param("restaurantId") UUID restaurantId,
            @Param("status") com.gulfnet.shared_library.enums.EntityStatus status);

    /**
     * Count ITEM discount usage by restaurant within date range
     * Matches discount ID from OrderDiscountUsage to item's discount mappings to determine which discount was applied
     * Includes both regular item discounts and BXGY discounts
     * Excludes items from fully refunded orders (RefundType = FULL) and fully refunded items
     */
    @Query("SELECT COUNT(DISTINCT oi.id) FROM OrderedItem oi " +
           "JOIN oi.order o " +
           "JOIN Transaction t ON t.order.id = o.id " +
           "JOIN o.restaurant r " +
           "LEFT JOIN Refund rf ON rf.transaction.id = t.id " +
           "LEFT JOIN RefundItem ri ON ri.refund.id = rf.id AND ri.orderedItem.id = oi.id " +
           "WHERE r.id = :restaurantId " +
           "AND t.transactionStatus IN ('COMPLETED', 'REFUNDED', 'PARTIALLY_REFUNDED') " +
           "AND oi.totalItemAmount IS NOT NULL " +
           "AND t.createdAt >= :startDate " +
           "AND t.createdAt <= :endDate " +
           "AND (r.isDeleted IS NULL OR r.isDeleted = false) " +
           "AND (rf.refundType IS NULL OR rf.refundType != 'FULL') " +
           "AND (ri.refundAmount IS NULL OR ri.refundAmount < oi.totalItemAmount) " +
           "AND (" +
           "  (oi.totalDiscountedItemAmount IS NOT NULL " +
           "   AND oi.totalItemAmount > oi.totalDiscountedItemAmount) " +
           "  OR " +
           "  (oi.totalDiscountedItemAmount IS NULL)) " +
           "AND EXISTS (SELECT 1 FROM OrderDiscountUsage odu " +
           "            WHERE odu.order.id = o.id " +
           "            AND odu.appliedTo = 'ITEM' " +
           "            AND odu.transaction.id = t.id " +
           "            AND (" +
           "              EXISTS (SELECT 1 FROM ItemDiscountMapping idm " +
           "                      WHERE idm.categoryItemMapping.item.id = oi.item.id " +
           "                      AND idm.discount.id = odu.discount.id " +
           "                      AND idm.discount.status = :status " +
           "                      AND idm.discount.isDeleted = false) " +
           "              OR " +
           "              EXISTS (SELECT 1 FROM DiscountBxgyItem dbxi " +
           "                      JOIN dbxi.getItemMapping gim " +
           "                      WHERE gim.item.id = oi.item.id " +
           "                      AND dbxi.discount.id = odu.discount.id " +
           "                      AND dbxi.discount.status = :status " +
           "                      AND dbxi.discount.isDeleted = false)))")
    long countItemDiscountUsageByRestaurantIdAndDateRange(
            @Param("restaurantId") UUID restaurantId,
            @Param("status") com.gulfnet.shared_library.enums.EntityStatus status,
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate);

    /**
     * Count CATEGORY discount usage by restaurant (ordered items with category-level discount applied)
     * Matches discount ID from OrderDiscountUsage to item's category discount mappings to determine which discount was applied
     * Excludes fully refunded items (where refund amount >= item amount)
     */
    @Query("SELECT COUNT(DISTINCT oi.id) FROM OrderedItem oi " +
           "JOIN oi.order o " +
           "JOIN Transaction t ON t.order.id = o.id " +
           "JOIN o.restaurant r " +
           "JOIN CategoryItemMapping cim ON cim.item.id = oi.item.id " +
           "LEFT JOIN Refund rf ON rf.transaction.id = t.id " +
           "LEFT JOIN RefundItem ri ON ri.refund.id = rf.id AND ri.orderedItem.id = oi.id " +
           "WHERE r.id = :restaurantId " +
           "AND t.transactionStatus IN ('COMPLETED', 'REFUNDED', 'PARTIALLY_REFUNDED') " +
           "AND oi.totalItemAmount IS NOT NULL " +
           "AND oi.totalDiscountedItemAmount IS NOT NULL " +
           "AND oi.totalItemAmount > oi.totalDiscountedItemAmount " +
           "AND (r.isDeleted IS NULL OR r.isDeleted = false) " +
           "AND (rf.refundType IS NULL OR rf.refundType != 'FULL') " +
           "AND (ri.refundAmount IS NULL OR ri.refundAmount < oi.totalItemAmount) " +
           "AND EXISTS (SELECT 1 FROM OrderDiscountUsage odu " +
           "            JOIN CategoryDiscountMapping cdm ON cdm.discount.id = odu.discount.id " +
           "            WHERE odu.order.id = o.id " +
           "            AND odu.appliedTo = 'CATEGORY' " +
           "            AND odu.transaction.id = t.id " +
           "            AND cdm.menuCategoryMapping.id = cim.menuCategoryMapping.id " +
           "            AND cdm.discount.status = :status " +
           "            AND cdm.discount.isDeleted = false " +
           "            AND NOT EXISTS (SELECT 1 FROM ItemDiscountMapping idm " +
           "                          WHERE idm.categoryItemMapping.item.id = oi.item.id " +
           "                          AND idm.discount.id = odu.discount.id " +
           "                          AND idm.discount.status = :status " +
           "                          AND idm.discount.isDeleted = false) " +
           "            AND NOT EXISTS (SELECT 1 FROM DiscountBxgyItem dbxi " +
           "                          JOIN dbxi.getItemMapping gim " +
           "                          WHERE gim.item.id = oi.item.id " +
           "                          AND dbxi.discount.id = odu.discount.id " +
           "                          AND dbxi.discount.status = :status " +
           "                          AND dbxi.discount.isDeleted = false))")
    long countCategoryDiscountUsageByRestaurantId(
            @Param("restaurantId") UUID restaurantId,
            @Param("status") com.gulfnet.shared_library.enums.EntityStatus status);

    /**
     * Count CATEGORY discount usage by restaurant within date range
     * Matches discount ID from OrderDiscountUsage to item's category discount mappings to determine which discount was applied
     * Excludes fully refunded items (where refund amount >= item amount)
     */
    @Query("SELECT COUNT(DISTINCT oi.id) FROM OrderedItem oi " +
           "JOIN oi.order o " +
           "JOIN Transaction t ON t.order.id = o.id " +
           "JOIN o.restaurant r " +
           "JOIN CategoryItemMapping cim ON cim.item.id = oi.item.id " +
           "LEFT JOIN Refund rf ON rf.transaction.id = t.id " +
           "LEFT JOIN RefundItem ri ON ri.refund.id = rf.id AND ri.orderedItem.id = oi.id " +
           "WHERE r.id = :restaurantId " +
           "AND t.transactionStatus IN ('COMPLETED', 'REFUNDED', 'PARTIALLY_REFUNDED') " +
           "AND oi.totalItemAmount IS NOT NULL " +
           "AND oi.totalDiscountedItemAmount IS NOT NULL " +
           "AND oi.totalItemAmount > oi.totalDiscountedItemAmount " +
           "AND t.createdAt >= :startDate " +
           "AND t.createdAt <= :endDate " +
           "AND (r.isDeleted IS NULL OR r.isDeleted = false) " +
           "AND (rf.refundType IS NULL OR rf.refundType != 'FULL') " +
           "AND (ri.refundAmount IS NULL OR ri.refundAmount < oi.totalItemAmount) " +
           "AND EXISTS (SELECT 1 FROM OrderDiscountUsage odu " +
           "            JOIN CategoryDiscountMapping cdm ON cdm.discount.id = odu.discount.id " +
           "            WHERE odu.order.id = o.id " +
           "            AND odu.appliedTo = 'CATEGORY' " +
           "            AND odu.transaction.id = t.id " +
           "            AND cdm.menuCategoryMapping.id = cim.menuCategoryMapping.id " +
           "            AND cdm.discount.status = :status " +
           "            AND cdm.discount.isDeleted = false " +
           "            AND NOT EXISTS (SELECT 1 FROM ItemDiscountMapping idm " +
           "                          WHERE idm.categoryItemMapping.item.id = oi.item.id " +
           "                          AND idm.discount.id = odu.discount.id " +
           "                          AND idm.discount.status = :status " +
           "                          AND idm.discount.isDeleted = false) " +
           "            AND NOT EXISTS (SELECT 1 FROM DiscountBxgyItem dbxi " +
           "                          JOIN dbxi.getItemMapping gim " +
           "                          WHERE gim.item.id = oi.item.id " +
           "                          AND dbxi.discount.id = odu.discount.id " +
           "                          AND dbxi.discount.status = :status " +
           "                          AND dbxi.discount.isDeleted = false))")
    long countCategoryDiscountUsageByRestaurantIdAndDateRange(
            @Param("restaurantId") UUID restaurantId,
            @Param("status") com.gulfnet.shared_library.enums.EntityStatus status,
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate);

    /**
     * Calculate ITEM discount revenue impact by restaurant
     * Includes both regular item discounts (totalItemAmount - totalDiscountedItemAmount) 
     * and BXGY discounts (totalItemAmount for free items where totalDiscountedItemAmount IS NULL)
     * Excludes items from fully refunded orders (RefundType = FULL) and subtracts proportional refund amounts
     */
    @Query("SELECT COALESCE(SUM(" +
           "  CASE " +
           "    WHEN oi.totalDiscountedItemAmount IS NOT NULL THEN " +
           "      (oi.totalItemAmount - oi.totalDiscountedItemAmount) - " +
           "      COALESCE((ri.refundAmount * (oi.totalItemAmount - oi.totalDiscountedItemAmount) / NULLIF(oi.totalItemAmount, 0)), 0) " +
           "    ELSE " +
           "      oi.totalItemAmount - COALESCE(ri.refundAmount, 0) " +
           "  END" +
           "), 0) FROM OrderedItem oi " +
           "JOIN oi.order o2 " +
           "JOIN Transaction t2 ON t2.order.id = o2.id " +
           "JOIN o2.restaurant r2 " +
           "LEFT JOIN Refund rf ON rf.transaction.id = t2.id " +
           "LEFT JOIN RefundItem ri ON ri.refund.id = rf.id AND ri.orderedItem.id = oi.id " +
           "WHERE r2.id = :restaurantId " +
           "AND t2.transactionStatus IN ('COMPLETED', 'REFUNDED', 'PARTIALLY_REFUNDED') " +
           "AND oi.totalItemAmount IS NOT NULL " +
           "AND (r2.isDeleted IS NULL OR r2.isDeleted = false) " +
           "AND (rf.refundType IS NULL OR rf.refundType != 'FULL') " +
           "AND (ri.refundAmount IS NULL OR ri.refundAmount < oi.totalItemAmount) " +
           "AND (" +
           "  (oi.totalDiscountedItemAmount IS NOT NULL " +
           "   AND oi.totalItemAmount > oi.totalDiscountedItemAmount " +
           "   AND EXISTS (SELECT 1 FROM ItemDiscountMapping idm " +
           "               JOIN idm.discount d " +
           "               WHERE idm.categoryItemMapping.item.id = oi.item.id " +
           "               AND d.appliedTo = 'ITEM' " +
           "               AND d.status = :status " +
           "               AND d.isDeleted = false)) " +
           "  OR " +
           "  (oi.totalDiscountedItemAmount IS NULL " +
           "   AND EXISTS (SELECT 1 FROM DiscountBxgyItem dbxi " +
           "               JOIN dbxi.discount d " +
           "               JOIN dbxi.getItemMapping gim " +
           "               WHERE gim.item.id = oi.item.id " +
           "               AND d.discountType = 'BXGY' " +
           "               AND d.status = :status " +
           "               AND d.isDeleted = false))" +
           ")")
    java.math.BigDecimal sumItemDiscountRevenueImpactByRestaurantId(
            @Param("restaurantId") UUID restaurantId,
            @Param("status") com.gulfnet.shared_library.enums.EntityStatus status);

    /**
     * Calculate ITEM discount revenue impact by restaurant within date range
     * Includes both regular item discounts (totalItemAmount - totalDiscountedItemAmount) 
     * and BXGY discounts (totalItemAmount for free items where totalDiscountedItemAmount IS NULL)
     * Excludes items from fully refunded orders (RefundType = FULL) and subtracts proportional refund amounts
     */
    @Query("SELECT COALESCE(SUM(" +
           "  CASE " +
           "    WHEN oi.totalDiscountedItemAmount IS NOT NULL THEN " +
           "      (oi.totalItemAmount - oi.totalDiscountedItemAmount) - " +
           "      COALESCE((ri.refundAmount * (oi.totalItemAmount - oi.totalDiscountedItemAmount) / NULLIF(oi.totalItemAmount, 0)), 0) " +
           "    ELSE " +
           "      oi.totalItemAmount - COALESCE(ri.refundAmount, 0) " +
           "  END" +
           "), 0) FROM OrderedItem oi " +
           "JOIN oi.order o2 " +
           "JOIN Transaction t2 ON t2.order.id = o2.id " +
           "JOIN o2.restaurant r2 " +
           "LEFT JOIN Refund rf ON rf.transaction.id = t2.id " +
           "LEFT JOIN RefundItem ri ON ri.refund.id = rf.id AND ri.orderedItem.id = oi.id " +
           "WHERE r2.id = :restaurantId " +
           "AND t2.transactionStatus IN ('COMPLETED', 'REFUNDED', 'PARTIALLY_REFUNDED') " +
           "AND oi.totalItemAmount IS NOT NULL " +
           "AND t2.createdAt >= :startDate " +
           "AND t2.createdAt <= :endDate " +
           "AND (r2.isDeleted IS NULL OR r2.isDeleted = false) " +
           "AND (rf.refundType IS NULL OR rf.refundType != 'FULL') " +
           "AND (ri.refundAmount IS NULL OR ri.refundAmount < oi.totalItemAmount) " +
           "AND (" +
           "  (oi.totalDiscountedItemAmount IS NOT NULL " +
           "   AND oi.totalItemAmount > oi.totalDiscountedItemAmount " +
           "   AND EXISTS (SELECT 1 FROM ItemDiscountMapping idm " +
           "               JOIN idm.discount d " +
           "               WHERE idm.categoryItemMapping.item.id = oi.item.id " +
           "               AND d.appliedTo = 'ITEM' " +
           "               AND d.status = :status " +
           "               AND d.isDeleted = false)) " +
           "  OR " +
           "  (oi.totalDiscountedItemAmount IS NULL " +
           "   AND EXISTS (SELECT 1 FROM DiscountBxgyItem dbxi " +
           "               JOIN dbxi.discount d " +
           "               JOIN dbxi.getItemMapping gim " +
           "               WHERE gim.item.id = oi.item.id " +
           "               AND d.discountType = 'BXGY' " +
           "               AND d.status = :status " +
           "               AND d.isDeleted = false))" +
           ")")
    java.math.BigDecimal sumItemDiscountRevenueImpactByRestaurantIdAndDateRange(
            @Param("restaurantId") UUID restaurantId,
            @Param("status") com.gulfnet.shared_library.enums.EntityStatus status,
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate);

    /**
     * Calculate CATEGORY discount revenue impact by restaurant
     * Subtracts proportional refund amounts from discount impact
     */
    @Query("SELECT COALESCE(SUM(" +
           "  (oi.totalItemAmount - oi.totalDiscountedItemAmount) - " +
           "  COALESCE((ri.refundAmount * (oi.totalItemAmount - oi.totalDiscountedItemAmount) / NULLIF(oi.totalItemAmount, 0)), 0)" +
           "), 0) FROM OrderedItem oi " +
           "JOIN oi.order o2 " +
           "JOIN Transaction t2 ON t2.order.id = o2.id " +
           "JOIN o2.restaurant r2 " +
           "JOIN CategoryItemMapping cim ON cim.item.id = oi.item.id " +
           "JOIN CategoryDiscountMapping cdm ON cdm.menuCategoryMapping.id = cim.menuCategoryMapping.id " +
           "JOIN cdm.discount d " +
           "LEFT JOIN Refund rf ON rf.transaction.id = t2.id " +
           "LEFT JOIN RefundItem ri ON ri.refund.id = rf.id AND ri.orderedItem.id = oi.id " +
           "WHERE r2.id = :restaurantId " +
           "AND t2.transactionStatus IN ('COMPLETED', 'REFUNDED', 'PARTIALLY_REFUNDED') " +
           "AND oi.totalItemAmount IS NOT NULL " +
           "AND oi.totalDiscountedItemAmount IS NOT NULL " +
           "AND oi.totalItemAmount > oi.totalDiscountedItemAmount " +
           "AND d.appliedTo = 'CATEGORY' " +
           "AND d.status = :status " +
           "AND d.isDeleted = false " +
           "AND (r2.isDeleted IS NULL OR r2.isDeleted = false) " +
           "AND (rf.refundType IS NULL OR rf.refundType != 'FULL') " +
           "AND (ri.refundAmount IS NULL OR ri.refundAmount < oi.totalItemAmount)")
    java.math.BigDecimal sumCategoryDiscountRevenueImpactByRestaurantId(
            @Param("restaurantId") UUID restaurantId,
            @Param("status") com.gulfnet.shared_library.enums.EntityStatus status);

    /**
     * Calculate CATEGORY discount revenue impact by restaurant within date range
     * Subtracts proportional refund amounts from discount impact
     */
    @Query("SELECT COALESCE(SUM(" +
           "  (oi.totalItemAmount - oi.totalDiscountedItemAmount) - " +
           "  COALESCE((ri.refundAmount * (oi.totalItemAmount - oi.totalDiscountedItemAmount) / NULLIF(oi.totalItemAmount, 0)), 0)" +
           "), 0) FROM OrderedItem oi " +
           "JOIN oi.order o2 " +
           "JOIN Transaction t2 ON t2.order.id = o2.id " +
           "JOIN o2.restaurant r2 " +
           "JOIN CategoryItemMapping cim ON cim.item.id = oi.item.id " +
           "JOIN CategoryDiscountMapping cdm ON cdm.menuCategoryMapping.id = cim.menuCategoryMapping.id " +
           "JOIN cdm.discount d " +
           "LEFT JOIN Refund rf ON rf.transaction.id = t2.id " +
           "LEFT JOIN RefundItem ri ON ri.refund.id = rf.id AND ri.orderedItem.id = oi.id " +
           "WHERE r2.id = :restaurantId " +
           "AND t2.transactionStatus IN ('COMPLETED', 'REFUNDED', 'PARTIALLY_REFUNDED') " +
           "AND oi.totalItemAmount IS NOT NULL " +
           "AND oi.totalDiscountedItemAmount IS NOT NULL " +
           "AND oi.totalItemAmount > oi.totalDiscountedItemAmount " +
           "AND d.appliedTo = 'CATEGORY' " +
           "AND d.status = :status " +
           "AND d.isDeleted = false " +
           "AND t2.createdAt >= :startDate " +
           "AND t2.createdAt <= :endDate " +
           "AND (r2.isDeleted IS NULL OR r2.isDeleted = false) " +
           "AND (rf.refundType IS NULL OR rf.refundType != 'FULL') " +
           "AND (ri.refundAmount IS NULL OR ri.refundAmount < oi.totalItemAmount)")
    java.math.BigDecimal sumCategoryDiscountRevenueImpactByRestaurantIdAndDateRange(
            @Param("restaurantId") UUID restaurantId,
            @Param("status") com.gulfnet.shared_library.enums.EntityStatus status,
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate);

    /**
     * Get top 10 items by order count (quantity) with revenue
     * Revenue is calculated as sum of totalDiscountedItemAmount
     * Only includes items from COMPLETED transactions
     * Groups by item only (not category) to aggregate all orders for the same item
     * Returns list of [itemId, categoryId, totalQuantity, totalRevenue] as Object arrays
     * categoryId is picked using consistent ordering (by category id) to get deterministic result for items in multiple categories
     */
    @Query("SELECT oi.item.id, " +
           "       (SELECT cim2.menuCategoryMapping.category.id FROM CategoryItemMapping cim2 " +
           "        WHERE cim2.item.id = oi.item.id " +
           "        ORDER BY cim2.menuCategoryMapping.category.id ASC " +
           "        LIMIT 1), " +
           "       SUM(oi.quantity), " +
           "       COALESCE(SUM(COALESCE(oi.totalDiscountedItemAmount, oi.totalItemAmount) - COALESCE(ri.refundAmount, 0)), 0) " +
           "FROM OrderedItem oi " +
           "JOIN oi.order o " +
           "JOIN Transaction t ON t.order.id = o.id " +
           "JOIN o.restaurant r " +
           "LEFT JOIN Refund rf ON rf.transaction.id = t.id " +
           "LEFT JOIN RefundItem ri ON ri.refund.id = rf.id AND ri.orderedItem.id = oi.id " +
           "WHERE t.transactionStatus IN ('COMPLETED', 'REFUNDED', 'PARTIALLY_REFUNDED') " +
           "AND oi.totalItemAmount IS NOT NULL " +
           "AND oi.orderedCombo IS NULL " + // Exclude combo items
           "AND EXISTS (SELECT 1 FROM CategoryItemMapping cim WHERE cim.item.id = oi.item.id) " +
           "AND (:restaurantId IS NULL OR r.id = :restaurantId) " +
           "AND (:restaurantGroupId IS NULL OR r.restaurantGroup.id = :restaurantGroupId) " +
           "AND (r.isDeleted IS NULL OR r.isDeleted = false) " +
           "AND (rf.refundType IS NULL OR rf.refundType != 'FULL') " +
           "AND (ri.refundAmount IS NULL OR ri.refundAmount < oi.totalItemAmount) " +
           "GROUP BY oi.item.id " +
           "ORDER BY SUM(oi.quantity) DESC " +
           "LIMIT 10")
    List<Object[]> findTop5ItemsByOrderCount(
            @Param("restaurantId") UUID restaurantId,
            @Param("restaurantGroupId") UUID restaurantGroupId);

    /**
     * Get top 10 items by order count (quantity) with revenue and date range
     * Revenue is calculated as sum of totalDiscountedItemAmount
     * Only includes items from COMPLETED transactions
     * Groups by item only (not category) to aggregate all orders for the same item
     * Returns list of [itemId, categoryId, totalQuantity, totalRevenue] as Object arrays
     * Uses sentinel UUID '00000000-0000-0000-0000-000000000000' to handle null parameters
     * Uses sentinel date '1970-01-01 00:00:00' to handle null date parameters
     */
    @Query(value = "SELECT oi.item_id, " +
           "       (SELECT mcm.category_id " +
           "        FROM category_item_mapping cim2 " +
           "        JOIN menu_category_mapping mcm ON mcm.id = cim2.menu_category_mapping_id " +
           "        WHERE cim2.item_id = oi.item_id " +
           "        ORDER BY mcm.category_id ASC " +
           "        LIMIT 1), " +
           "       SUM(oi.quantity), " +
           "       COALESCE(SUM(COALESCE(oi.total_discounted_item_amount, oi.total_item_amount) - COALESCE(ri.refund_amount, 0)), 0) " +
           "FROM ordered_item oi " +
           "JOIN orders o ON o.id = oi.order_id " +
           "JOIN transaction t ON t.order_id = o.id " +
           "JOIN restaurant r ON r.id = o.restaurant_id " +
           "LEFT JOIN refund rf ON rf.transaction_id = t.id " +
           "LEFT JOIN refund_item ri ON ri.refund_id = rf.id AND ri.ordered_item_id = oi.id " +
           "WHERE t.transaction_status IN ('COMPLETED', 'REFUNDED', 'PARTIALLY_REFUNDED') " +
           "AND oi.total_item_amount IS NOT NULL " +
           "AND oi.ordered_combo_id IS NULL " +
           "AND EXISTS (SELECT 1 FROM category_item_mapping cim WHERE cim.item_id = oi.item_id) " +
           "AND (:restaurantId = CAST('00000000-0000-0000-0000-000000000000' AS uuid) OR r.id = :restaurantId) " +
           "AND (:restaurantGroupId = CAST('00000000-0000-0000-0000-000000000000' AS uuid) OR r.restaurant_group_id = :restaurantGroupId) " +
           "AND (r.is_deleted IS NULL OR r.is_deleted = false) " +
           "AND (rf.refund_type IS NULL OR rf.refund_type != 'FULL') " +
           "AND (ri.refund_amount IS NULL OR ri.refund_amount < oi.total_item_amount) " +
           "AND (:startDate = CAST('1970-01-01 00:00:00' AS timestamp) OR t.created_at >= :startDate) " +
           "AND (:endDate = CAST('1970-01-01 00:00:00' AS timestamp) OR t.created_at <= :endDate) " +
           "GROUP BY oi.item_id " +
           "ORDER BY SUM(oi.quantity) DESC " +
           "LIMIT 10", nativeQuery = true)
    List<Object[]> findTop5ItemsByOrderCountWithDateRange(
            @Param("restaurantId") UUID restaurantId,
            @Param("restaurantGroupId") UUID restaurantGroupId,
            @Param("startDate") java.time.LocalDateTime startDate,
            @Param("endDate") java.time.LocalDateTime endDate);

    /**
     * Get top 1 item by order count (quantity) with revenue
     * Revenue is calculated as sum of totalDiscountedItemAmount or totalItemAmount (if discount is null)
     * Only includes items from COMPLETED transactions
     * Groups by item only (not category) to aggregate all orders for the same item
     * Returns list of [itemId, categoryId, totalQuantity, totalRevenue] as Object arrays
     * Uses sentinel UUID '00000000-0000-0000-0000-000000000000' to handle null parameters
     * Uses sentinel date '1970-01-01 00:00:00' to handle null date parameters
     */
    @Query(value = "SELECT oi.item_id, " +
           "       (SELECT mcm.category_id " +
           "        FROM category_item_mapping cim2 " +
           "        JOIN menu_category_mapping mcm ON mcm.id = cim2.menu_category_mapping_id " +
           "        WHERE cim2.item_id = oi.item_id " +
           "        ORDER BY mcm.category_id ASC " +
           "        LIMIT 1), " +
           "       SUM(oi.quantity), " +
           "       COALESCE(SUM(COALESCE(oi.total_discounted_item_amount, oi.total_item_amount)), 0) " +
           "FROM ordered_item oi " +
           "JOIN orders o ON o.id = oi.order_id " +
           "JOIN transaction t ON t.order_id = o.id " +
           "JOIN restaurant r ON r.id = o.restaurant_id " +
           "WHERE t.transaction_status = 'COMPLETED' " +
           "AND oi.total_item_amount IS NOT NULL " +
           "AND oi.ordered_combo_id IS NULL " +
           "AND EXISTS (SELECT 1 FROM category_item_mapping cim WHERE cim.item_id = oi.item_id) " +
           "AND (:restaurantId = CAST('00000000-0000-0000-0000-000000000000' AS uuid) OR r.id = :restaurantId) " +
           "AND (:restaurantGroupId = CAST('00000000-0000-0000-0000-000000000000' AS uuid) OR r.restaurant_group_id = :restaurantGroupId) " +
           "AND (r.is_deleted IS NULL OR r.is_deleted = false) " +
           "AND (:startDate = CAST('1970-01-01 00:00:00' AS timestamp) OR t.created_at >= :startDate) " +
           "AND (:endDate = CAST('1970-01-01 00:00:00' AS timestamp) OR t.created_at <= :endDate) " +
           "GROUP BY oi.item_id " +
           "ORDER BY SUM(oi.quantity) DESC " +
           "LIMIT 1", nativeQuery = true)
    List<Object[]> findTop1ItemsByOrderCount(
            @Param("restaurantId") UUID restaurantId,
            @Param("restaurantGroupId") UUID restaurantGroupId,
            @Param("startDate") java.time.LocalDateTime startDate,
            @Param("endDate") java.time.LocalDateTime endDate);

    /**
     * Get least performing 1 item by order count (quantity) with revenue
     * Revenue is calculated as sum of totalDiscountedItemAmount or totalItemAmount (if discount is null)
     * Only includes items from COMPLETED transactions
     * Groups by item only (not category) to aggregate all orders for the same item
     * Returns list of [itemId, categoryId, totalQuantity, totalRevenue] as Object arrays
     * Uses sentinel UUID '00000000-0000-0000-0000-000000000000' to handle null parameters
     * Uses sentinel date '1970-01-01 00:00:00' to handle null date parameters
     */
    @Query(value = "SELECT oi.item_id, " +
           "       (SELECT mcm.category_id " +
           "        FROM category_item_mapping cim2 " +
           "        JOIN menu_category_mapping mcm ON mcm.id = cim2.menu_category_mapping_id " +
           "        WHERE cim2.item_id = oi.item_id " +
           "        ORDER BY mcm.category_id ASC " +
           "        LIMIT 1), " +
           "       SUM(oi.quantity), " +
           "       COALESCE(SUM(COALESCE(oi.total_discounted_item_amount, oi.total_item_amount)), 0) " +
           "FROM ordered_item oi " +
           "JOIN orders o ON o.id = oi.order_id " +
           "JOIN transaction t ON t.order_id = o.id " +
           "JOIN restaurant r ON r.id = o.restaurant_id " +
           "WHERE t.transaction_status = 'COMPLETED' " +
           "AND oi.total_item_amount IS NOT NULL " +
           "AND oi.ordered_combo_id IS NULL " +
           "AND EXISTS (SELECT 1 FROM category_item_mapping cim WHERE cim.item_id = oi.item_id) " +
           "AND (:restaurantId = CAST('00000000-0000-0000-0000-000000000000' AS uuid) OR r.id = :restaurantId) " +
           "AND (:restaurantGroupId = CAST('00000000-0000-0000-0000-000000000000' AS uuid) OR r.restaurant_group_id = :restaurantGroupId) " +
           "AND (r.is_deleted IS NULL OR r.is_deleted = false) " +
           "AND (:startDate = CAST('1970-01-01 00:00:00' AS timestamp) OR t.created_at >= :startDate) " +
           "AND (:endDate = CAST('1970-01-01 00:00:00' AS timestamp) OR t.created_at <= :endDate) " +
           "GROUP BY oi.item_id " +
           "ORDER BY SUM(oi.quantity) ASC " +
           "LIMIT 1", nativeQuery = true)
    List<Object[]> findLeastPerformingItemByOrderCount(
            @Param("restaurantId") UUID restaurantId,
            @Param("restaurantGroupId") UUID restaurantGroupId,
            @Param("startDate") java.time.LocalDateTime startDate,
            @Param("endDate") java.time.LocalDateTime endDate);

    /**
     * Count items with COOKING status
     * Filtered by restaurant ID or restaurant group ID and date range
     * Uses sentinel UUID '00000000-0000-0000-0000-000000000000' to handle null parameters
     * Uses sentinel date '1970-01-01 00:00:00' to handle null date parameters
     */
    @Query(value = "SELECT COUNT(DISTINCT oi.id) FROM ordered_item oi " +
           "JOIN orders o ON o.id = oi.order_id " +
           "JOIN restaurant r ON r.id = o.restaurant_id " +
           "WHERE oi.item_status = 'COOKING' " +
           "AND (CAST(:restaurantId AS uuid) = '00000000-0000-0000-0000-000000000000'::uuid OR r.id = CAST(:restaurantId AS uuid)) " +
           "AND (CAST(:restaurantGroupId AS uuid) = '00000000-0000-0000-0000-000000000000'::uuid OR r.restaurant_group_id = CAST(:restaurantGroupId AS uuid)) " +
           "AND (r.is_deleted IS NULL OR r.is_deleted = false) " +
           "AND (CAST(:startDate AS timestamp) = '1970-01-01 00:00:00'::timestamp OR o.created_at >= CAST(:startDate AS timestamp)) " +
           "AND (CAST(:endDate AS timestamp) = '1970-01-01 00:00:00'::timestamp OR o.created_at <= CAST(:endDate AS timestamp))", nativeQuery = true)
    long countCookingItemsByFilters(
            @Param("restaurantId") UUID restaurantId,
            @Param("restaurantGroupId") UUID restaurantGroupId,
            @Param("startDate") java.time.LocalDateTime startDate,
            @Param("endDate") java.time.LocalDateTime endDate);


    /**
     * Count ready items (sum of quantity) for a table from all active sessions
     * Returns the sum of quantities for items with READY status
     */
    @Query(value = "SELECT COALESCE(SUM(COALESCE(oi.quantity, 1)), 0) FROM ordered_item oi " +
           "JOIN orders o ON o.id = oi.order_id " +
           "JOIN sessions s ON s.id = o.session_id " +
           "WHERE s.table_id = :tableId " +
           "AND s.expired_at IS NULL " +
           "AND oi.item_status = 'READY'",
           nativeQuery = true)
    Long countReadyItemsByTableId(@Param("tableId") UUID tableId);

    /**
     * Count pending items (sum of quantity) for a table from all active sessions
     * Returns the sum of quantities for items with PUSHED, COOKING, or DELAYED status
     */
    @Query(value = "SELECT COALESCE(SUM(COALESCE(oi.quantity, 1)), 0) FROM ordered_item oi " +
           "JOIN orders o ON o.id = oi.order_id " +
           "JOIN sessions s ON s.id = o.session_id " +
           "WHERE s.table_id = :tableId " +
           "AND s.expired_at IS NULL " +
           "AND oi.item_status IN ('PUSHED', 'COOKING', 'DELAYED')",
           nativeQuery = true)
    Long countPendingItemsByTableId(@Param("tableId") UUID tableId);

    /**
     * Count ready items (sum of quantity) for an order
     * Returns the sum of quantities for items with READY status (excluding combo items)
     */
    @Query(value = "SELECT COALESCE(SUM(COALESCE(oi.quantity, 1)), 0) FROM ordered_item oi " +
           "WHERE oi.order_id = :orderId " +
           "AND oi.ordered_combo_id IS NULL " +
           "AND oi.item_status = 'READY'",
           nativeQuery = true)
    Long countReadyItemsByOrderId(@Param("orderId") UUID orderId);

    /**
     * Count pending items (sum of quantity) for an order
     * Returns the sum of quantities for items with PUSHED, COOKING, or DELAYED status (excluding combo items)
     */
    @Query(value = "SELECT COALESCE(SUM(COALESCE(oi.quantity, 1)), 0) FROM ordered_item oi " +
           "WHERE oi.order_id = :orderId " +
           "AND oi.ordered_combo_id IS NULL " +
           "AND oi.item_status IN ('PUSHED', 'COOKING', 'DELAYED')",
           nativeQuery = true)
    Long countPendingItemsByOrderId(@Param("orderId") UUID orderId);

    /**
     * Get itemized sales report
     * Returns: [itemId, categoryId, quantitySold, unitPrice, totalSales]
     * Filters by specific restaurantId only.
     *
     * IMPORTANT:
     * - Includes COMPLETED, REFUNDED and PARTIALLY_REFUNDED transactions.
     * - Includes ONLY regular items (excludes combo items - items that are part of combos).
     *   Combo items are handled separately via getItemizedComboSalesReport().
     * - Treats refunds as adjustments, not deletions, by subtracting per-line refund amounts
     *   from the net total sales, but NOT from the base/unit price.
     *   Net total sales per item = total_sales - SUM(refund_item.refund_amount).
     * - For lines with a regular per-item discount, we use total_discounted_item_amount.
     * - For non-discounted lines and BXGY buy items (where total_discounted_item_amount is NULL),
     *   we fall back to total_item_amount so that all items are included in the report.
     * - First aggregates ordered_items by item_id to get correct totals, then joins with categories.
     *   Uses DISTINCT ON to ensure only one row per item. For category selection, uses the main category:
     *   if the item's category has a parent (subcategory), uses the parent category; otherwise uses the category itself.
     *   Items without category mappings will still appear with NULL category_id.
     * - The unit_price is calculated from base_total_amount, which always uses total_item_amount (base price before discount),
     *   not the discounted amount. This ensures unit_price reflects the original item price regardless of discounts.
     * - Uses sentinel date '1970-01-01 00:00:00' to handle null date parameters.
     */
    @Query(value = "SELECT DISTINCT ON (item_totals.item_id) " +
           "       item_totals.item_id, " +
           "       COALESCE(c.parent_category_id, mcm.category_id) as main_category_id, " +
           "       item_totals.quantity_sold, " +
           "       CASE " +
           "           WHEN item_totals.quantity_sold > 0 AND item_totals.base_total_amount > 0 " +
           "               THEN ROUND((item_totals.base_total_amount / item_totals.quantity_sold)::numeric, 2) " +
           "           ELSE 0 " +
           "       END as unit_price, " +
           "       item_totals.total_sales " +
           "FROM ( " +
           "    SELECT " +
           "           oi.item_id, " +
           "           COALESCE(SUM(GREATEST(COALESCE(oi.quantity, 0) - COALESCE(ri_agg.refund_quantity, 0), 0)), 0) as quantity_sold, " +
           "           COALESCE(SUM(GREATEST( " +
           "               (CASE " +
           "                   WHEN oi.total_discounted_item_amount IS NOT NULL AND oi.total_discounted_item_amount > 0 " +
           "                       THEN oi.total_discounted_item_amount " +
           "                   ELSE oi.total_item_amount " +
           "               END) - COALESCE(ri_agg.refund_amount, 0), 0 " +
           "           )), 0) as total_sales, " +
           "           COALESCE(SUM(GREATEST( " +
           "               COALESCE(oi.total_item_amount, 0) - " +
           "               (CASE " +
           "                   WHEN COALESCE(oi.quantity, 0) > 0 " +
           "                       THEN COALESCE(oi.total_item_amount, 0) * (COALESCE(ri_agg.refund_quantity, 0)::numeric / oi.quantity) " +
           "                   ELSE 0 " +
           "               END), 0 " +
           "           )), 0) as base_total_amount " +
           "    FROM ordered_item oi " +
           "    JOIN item it ON it.id = oi.item_id " +
           "    JOIN orders o ON o.id = oi.order_id " +
           "    JOIN transaction t ON t.order_id = o.id " +
           "    JOIN restaurant r ON r.id = o.restaurant_id " +
           "    LEFT JOIN ( " +
           "        SELECT ri.ordered_item_id, SUM(COALESCE(ri.quantity, 0)) as refund_quantity, SUM(COALESCE(ri.refund_amount, 0)) as refund_amount " +
           "        FROM refund_item ri " +
           "        WHERE ri.ordered_item_id IS NOT NULL " +
           "        GROUP BY ri.ordered_item_id " +
           "    ) ri_agg ON ri_agg.ordered_item_id = oi.id " +
           "    WHERE t.transaction_status IN ('COMPLETED', 'REFUNDED', 'PARTIALLY_REFUNDED') " +
           "           AND oi.ordered_combo_id IS NULL " + // Exclude combo items - they're handled separately
           "           AND r.id = CAST(:restaurantId AS uuid) " +
           "           AND (r.is_deleted IS NULL OR r.is_deleted = false) " +
           "           AND (CAST(:startDate AS timestamp) = '1970-01-01 00:00:00'::timestamp OR t.created_at >= CAST(:startDate AS timestamp)) " +
           "           AND (CAST(:endDate AS timestamp) = '1970-01-01 00:00:00'::timestamp OR t.created_at <= CAST(:endDate AS timestamp)) " +
           "    GROUP BY oi.item_id " +
           "    HAVING COALESCE(SUM(GREATEST( " +
           "               (CASE " +
           "                   WHEN oi.total_discounted_item_amount IS NOT NULL AND oi.total_discounted_item_amount > 0 " +
           "                       THEN oi.total_discounted_item_amount " +
           "                   ELSE oi.total_item_amount " +
           "               END) - COALESCE(ri_agg.refund_amount, 0), 0 " +
           "           )), 0) > 0 " +
           ") item_totals " +
           "LEFT JOIN category_item_mapping cim ON cim.item_id = item_totals.item_id " +
           "LEFT JOIN menu_category_mapping mcm ON mcm.id = cim.menu_category_mapping_id " +
           "       AND EXISTS ( " +
           "           SELECT 1 FROM restaurant_menu_mapping rmm " +
           "           WHERE rmm.restaurant_id = CAST(:restaurantId AS uuid) " +
           "             AND rmm.menu_id = mcm.menu_id " +
           "             AND rmm.status = 'LIVE' " +
           "       ) " +
           "LEFT JOIN category c ON c.id = mcm.category_id " +
           "ORDER BY item_totals.item_id, " +
           "         CASE WHEN c.parent_category_id IS NOT NULL THEN 0 ELSE 1 END, " +
           "         COALESCE(c.parent_category_id, mcm.category_id) NULLS LAST",
           nativeQuery = true)
    List<Object[]> getItemizedSalesReport(
            @Param("restaurantId") UUID restaurantId,
            @Param("startDate") java.time.LocalDateTime startDate,
            @Param("endDate") java.time.LocalDateTime endDate);

    /**
     * Get wastage report based on cancelled items which were already in preparation/served states.
     * Definition: items that were cancelled after being in COOKING, READY or SERVED status.
     * Includes:
     * 1. Items from cancelled transactions (transaction_status = 'CANCELED')
     * 2. Items cancelled individually (item_status = 'CANCELED' with wastage_source_status set)
     * 3. Items with NULL wastage_source_status but order status suggests they were prepared (fallback for legacy data)
     *
     * Returns: [itemId, categoryId, quantityWasted, totalWastageCost, lastWastageAt]
     * Filters by specific restaurantId only
     * Uses sentinel date '1970-01-01 00:00:00' to handle null date parameters
     */
    @Query(value = "WITH wastage_items AS ( " +
           "    SELECT " +
           "           oi.id as ordered_item_id, " +
           "           oi.item_id, " +
           "           oi.quantity, " +
           "           COALESCE(oi.total_item_amount, oi.total_discounted_item_amount, 0) as total_item_amount, " +
           "           COALESCE(oi.updated_at, oi.created_at) as last_wastage_at " +
           "    FROM ordered_item oi " +
           "    JOIN orders o ON o.id = oi.order_id " +
           "    LEFT JOIN transaction t ON t.order_id = o.id " +
           "    JOIN restaurant r ON r.id = o.restaurant_id " +
           "    WHERE oi.item_status = 'CANCELED' " +
           "    AND (oi.wastage_source_status IN ('COOKING', 'READY', 'SERVED') " +
           "         OR (oi.wastage_source_status IS NULL " +
           "             AND (o.order_status IN ('SERVED', 'READY', 'COOKING') " +
           "                  OR (oi.total_item_amount IS NOT NULL AND oi.total_item_amount > 0) " +
           "                  OR (oi.total_discounted_item_amount IS NOT NULL AND oi.total_discounted_item_amount > 0)))) " +
           "    AND r.id = CAST(:restaurantId AS uuid) " +
           "    AND (r.is_deleted IS NULL OR r.is_deleted = false) " +
           "    AND (CAST(:startDate AS timestamp) = '1970-01-01 00:00:00'::timestamp OR " +
           "         (CASE WHEN t.transaction_status = 'CANCELED' THEN t.created_at ELSE oi.updated_at END) >= CAST(:startDate AS timestamp)) " +
           "    AND (CAST(:endDate AS timestamp) = '1970-01-01 00:00:00'::timestamp OR " +
           "         (CASE WHEN t.transaction_status = 'CANCELED' THEN t.created_at ELSE oi.updated_at END) <= CAST(:endDate AS timestamp)) " +
           "), " +
           "item_categories AS ( " +
           "    SELECT DISTINCT ON (wi.ordered_item_id, COALESCE(c.parent_category_id, mcm.category_id)) " +
           "           wi.ordered_item_id, " +
           "           wi.item_id, " +
           "           wi.quantity, " +
           "           wi.total_item_amount, " +
           "           wi.last_wastage_at, " +
           "           COALESCE(c.parent_category_id, mcm.category_id) as category_id " +
           "    FROM wastage_items wi " +
           "    JOIN category_item_mapping cim ON cim.item_id = wi.item_id " +
           "    JOIN menu_category_mapping mcm ON mcm.id = cim.menu_category_mapping_id " +
           "    LEFT JOIN category c ON c.id = mcm.category_id " +
           "    JOIN restaurant_menu_mapping rmm ON rmm.menu_id = mcm.menu_id " +
           "         AND rmm.restaurant_id = CAST(:restaurantId AS uuid) " +
           "    ORDER BY wi.ordered_item_id, COALESCE(c.parent_category_id, mcm.category_id) " +
           ") " +
           "SELECT " +
           "       NULL as item_id, " +
           "       ic.category_id, " +
           "       COALESCE(SUM(ic.quantity), 0) as quantity_wasted, " +
           "       COALESCE(SUM(ic.total_item_amount), 0) as total_wastage_cost, " +
           "       MAX(ic.last_wastage_at) as last_wastage_at " +
           "FROM item_categories ic " +
           "GROUP BY ic.category_id " +
           "ORDER BY total_wastage_cost DESC",
           nativeQuery = true)
    List<Object[]> getWastageReport(
            @Param("restaurantId") UUID restaurantId,
            @Param("startDate") java.time.LocalDateTime startDate,
            @Param("endDate") java.time.LocalDateTime endDate);

    /**
     * Calculate total wastage cost and count for a specific restaurant
     * Includes items that were cancelled after being in COOKING, READY or SERVED status,
     * or items from cancelled transactions (regardless of item status)
     * Uses sentinel date '1970-01-01 00:00:00' to handle null date parameters
     */
    @Query(value = "WITH wastage_items AS ( " +
           "    SELECT " +
           "           oi.id as ordered_item_id, " +
           "           oi.quantity, " +
           "           COALESCE(oi.total_discounted_item_amount, oi.total_item_amount, 0) as item_amount " +
           "    FROM ordered_item oi " +
           "    JOIN orders o ON o.id = oi.order_id " +
           "    LEFT JOIN transaction t ON t.order_id = o.id " +
           "    JOIN restaurant r ON r.id = o.restaurant_id " +
           "    WHERE oi.item_status = 'CANCELED' " +
           "    AND (oi.wastage_source_status IN ('COOKING', 'READY', 'SERVED') " +
           "         OR (oi.wastage_source_status IS NULL " +
           "             AND (o.order_status IN ('SERVED', 'READY', 'COOKING') " +
           "                  OR (oi.total_discounted_item_amount IS NOT NULL AND oi.total_discounted_item_amount > 0) " +
           "                  OR (oi.total_item_amount IS NOT NULL AND oi.total_item_amount > 0))) " +
           "         OR t.transaction_status = 'CANCELED') " +
           "    AND r.id = CAST(:restaurantId AS uuid) " +
           "    AND (r.is_deleted IS NULL OR r.is_deleted = false) " +
           "    AND (CAST(:startDate AS timestamp) = '1970-01-01 00:00:00'::timestamp OR " +
           "         (CASE WHEN t.transaction_status = 'CANCELED' THEN t.created_at ELSE oi.updated_at END) >= CAST(:startDate AS timestamp)) " +
           "    AND (CAST(:endDate AS timestamp) = '1970-01-01 00:00:00'::timestamp OR " +
           "         (CASE WHEN t.transaction_status = 'CANCELED' THEN t.created_at ELSE oi.updated_at END) <= CAST(:endDate AS timestamp)) " +
           ") " +
           "SELECT " +
           "       COALESCE(SUM(wi.quantity), 0) as total_quantity, " +
           "       COALESCE(SUM(wi.item_amount), 0) as total_wastage_cost " +
           "FROM wastage_items wi",
           nativeQuery = true)
    List<Object[]> getWastageSummaryByRestaurantId(
            @Param("restaurantId") UUID restaurantId,
            @Param("startDate") java.time.LocalDateTime startDate,
            @Param("endDate") java.time.LocalDateTime endDate);

    /**
     * Calculate total wastage cost and count for a restaurant group
     * Includes items that were cancelled after being in COOKING, READY or SERVED status,
     * or items from cancelled transactions (regardless of item status)
     * Uses sentinel date '1970-01-01 00:00:00' to handle null date parameters
     */
    @Query(value = "WITH wastage_items AS ( " +
           "    SELECT " +
           "           oi.id as ordered_item_id, " +
           "           oi.quantity, " +
           "           COALESCE(oi.total_discounted_item_amount, oi.total_item_amount, 0) as item_amount " +
           "    FROM ordered_item oi " +
           "    JOIN orders o ON o.id = oi.order_id " +
           "    LEFT JOIN transaction t ON t.order_id = o.id " +
           "    JOIN restaurant r ON r.id = o.restaurant_id " +
           "    WHERE oi.item_status = 'CANCELED' " +
           "    AND (oi.wastage_source_status IN ('COOKING', 'READY', 'SERVED') " +
           "         OR (oi.wastage_source_status IS NULL " +
           "             AND (o.order_status IN ('SERVED', 'READY', 'COOKING') " +
           "                  OR (oi.total_discounted_item_amount IS NOT NULL AND oi.total_discounted_item_amount > 0) " +
           "                  OR (oi.total_item_amount IS NOT NULL AND oi.total_item_amount > 0))) " +
           "         OR t.transaction_status = 'CANCELED') " +
           "    AND r.restaurant_group_id = CAST(:restaurantGroupId AS uuid) " +
           "    AND (r.is_deleted IS NULL OR r.is_deleted = false) " +
           "    AND (CAST(:startDate AS timestamp) = '1970-01-01 00:00:00'::timestamp OR " +
           "         (CASE WHEN t.transaction_status = 'CANCELED' THEN t.created_at ELSE oi.updated_at END) >= CAST(:startDate AS timestamp)) " +
           "    AND (CAST(:endDate AS timestamp) = '1970-01-01 00:00:00'::timestamp OR " +
           "         (CASE WHEN t.transaction_status = 'CANCELED' THEN t.created_at ELSE oi.updated_at END) <= CAST(:endDate AS timestamp)) " +
           ") " +
           "SELECT " +
           "       COALESCE(SUM(wi.quantity), 0) as total_quantity, " +
           "       COALESCE(SUM(wi.item_amount), 0) as total_wastage_cost " +
           "FROM wastage_items wi",
           nativeQuery = true)
    List<Object[]> getWastageSummaryByRestaurantGroupId(
            @Param("restaurantGroupId") UUID restaurantGroupId,
            @Param("startDate") java.time.LocalDateTime startDate,
            @Param("endDate") java.time.LocalDateTime endDate);

    /**
     * Calculate total wastage cost and count for all restaurants
     * Includes items that were cancelled after being in COOKING, READY or SERVED status,
     * or items from cancelled transactions (regardless of item status)
     * Uses sentinel date '1970-01-01 00:00:00' to handle null date parameters
     */
    @Query(value = "WITH wastage_items AS ( " +
           "    SELECT " +
           "           oi.id as ordered_item_id, " +
           "           oi.quantity, " +
           "           COALESCE(oi.total_discounted_item_amount, oi.total_item_amount, 0) as item_amount " +
           "    FROM ordered_item oi " +
           "    JOIN orders o ON o.id = oi.order_id " +
           "    LEFT JOIN transaction t ON t.order_id = o.id " +
           "    JOIN restaurant r ON r.id = o.restaurant_id " +
           "    WHERE oi.item_status = 'CANCELED' " +
           "    AND (oi.wastage_source_status IN ('COOKING', 'READY', 'SERVED') " +
           "         OR (oi.wastage_source_status IS NULL " +
           "             AND (o.order_status IN ('SERVED', 'READY', 'COOKING') " +
           "                  OR (oi.total_discounted_item_amount IS NOT NULL AND oi.total_discounted_item_amount > 0) " +
           "                  OR (oi.total_item_amount IS NOT NULL AND oi.total_item_amount > 0))) " +
           "         OR t.transaction_status = 'CANCELED') " +
           "    AND (r.is_deleted IS NULL OR r.is_deleted = false) " +
           "    AND (CAST(:startDate AS timestamp) = '1970-01-01 00:00:00'::timestamp OR " +
           "         (CASE WHEN t.transaction_status = 'CANCELED' THEN t.created_at ELSE oi.updated_at END) >= CAST(:startDate AS timestamp)) " +
           "    AND (CAST(:endDate AS timestamp) = '1970-01-01 00:00:00'::timestamp OR " +
           "         (CASE WHEN t.transaction_status = 'CANCELED' THEN t.created_at ELSE oi.updated_at END) <= CAST(:endDate AS timestamp)) " +
           ") " +
           "SELECT " +
           "       COALESCE(SUM(wi.quantity), 0) as total_quantity, " +
           "       COALESCE(SUM(wi.item_amount), 0) as total_wastage_cost " +
           "FROM wastage_items wi",
           nativeQuery = true)
    List<Object[]> getWastageSummary(
            @Param("startDate") java.time.LocalDateTime startDate,
            @Param("endDate") java.time.LocalDateTime endDate);


}
