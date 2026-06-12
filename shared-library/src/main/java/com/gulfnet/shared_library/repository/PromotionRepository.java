package com.gulfnet.shared_library.repository;

import com.gulfnet.shared_library.entity.Promotion;
import com.gulfnet.shared_library.enums.EntityStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface PromotionRepository extends JpaRepository<Promotion, UUID>, JpaSpecificationExecutor<Promotion> {
    
    @Query("SELECT p FROM Promotion p WHERE p.status = :status AND p.isDeleted = false")
    Page<Promotion> findAllByStatusAndIsDeletedFalse(@Param("status") EntityStatus status, org.springframework.data.domain.Pageable pageable);
    
    @Query("SELECT p FROM Promotion p WHERE p.isDeleted = false")
    Page<Promotion> findAllByIsDeletedFalse(org.springframework.data.domain.Pageable pageable);
    
    /**
     * Finds all non-deleted promotions with optional filtering by status, type, and search term.
     * The search term matches against promotion translation name or heading.
     *
     * @param status optional status filter (ACTIVE, INACTIVE, etc.), null returns all statuses
     * @param type   optional promotion type filter, null returns all types
     * @param search optional search term to match against promotion name or heading (case-insensitive)
     * @return list of promotions matching the filters
     */
    @Query(value = "SELECT DISTINCT p.* FROM promotion p " +
            "LEFT JOIN discount d ON d.id = p.discount_id " +
            "LEFT JOIN promotion_translation pt ON pt.promotion_id = p.id " +
            "WHERE p.is_deleted = false " +
            "AND (:status IS NULL OR p.status = :status) " +
            "AND (:type IS NULL OR p.type = :type) " +
            "AND (:search IS NULL OR " +
            "     pt.name ILIKE '%' || CAST(:search AS text) || '%' OR " +
            "     pt.heading ILIKE '%' || CAST(:search AS text) || '%')",
            nativeQuery = true)
    List<Promotion> findAllActivePromotionsWithFilters(
            @Param("status") String status,
            @Param("type") String type,
            @Param("search") String search);

    @Query("SELECT CASE WHEN COUNT(p) > 0 THEN true ELSE false END FROM Promotion p WHERE p.discount.id = :discountId AND p.isDeleted = false")
    boolean existsByDiscountIdAndIsDeletedFalse(@Param("discountId") UUID discountId);
    
    // Batch method to get discount IDs that have promotions
    @Query("SELECT DISTINCT p.discount.id FROM Promotion p WHERE p.discount.id IN :discountIds AND p.isDeleted = false")
    List<UUID> findDiscountIdsWithPromotions(@Param("discountIds") List<UUID> discountIds);

    @Query(value = "SELECT id as promotionId, discount_id as discountId FROM promotion WHERE is_deleted = false AND id IN :promotionIds", nativeQuery = true)
    List<Object[]> findPromotionDiscountMappings(@Param("promotionIds") List<UUID> promotionIds);
    
    /**
     * Count active promotions (status = ACTIVE and not deleted)
     */
    @Query("SELECT COUNT(p) FROM Promotion p WHERE p.status = :status AND p.isDeleted = false")
    long countByStatusAndIsDeletedFalse(@Param("status") EntityStatus status);
    
    /**
     * Count active promotions created within a date range
     */
    @Query("SELECT COUNT(p) FROM Promotion p WHERE p.status = :status AND p.isDeleted = false AND p.createdAt >= :startDate AND p.createdAt <= :endDate")
    long countByStatusAndIsDeletedFalseAndCreatedAtBetween(
            @Param("status") EntityStatus status,
            @Param("startDate") java.time.LocalDateTime startDate,
            @Param("endDate") java.time.LocalDateTime endDate);
    
    /**
     * Count distinct promotions used in transactions (via orders) for restaurants in a restaurant group
     * Promotions are tracked through their associated discount that was applied to orders
     * Only counts promotions from completed transactions
     */
    @Query("SELECT COUNT(DISTINCT p) FROM Transaction t " +
           "JOIN t.order o " +
           "JOIN t.restaurant r " +
           "JOIN Promotion p ON p.discount = o.discount " +
           "WHERE r.restaurantGroup.id = :restaurantGroupId " +
           "AND t.transactionStatus = 'COMPLETED' " +
           "AND o.discount IS NOT NULL " +
           "AND p.status = :status " +
           "AND p.isDeleted = false " +
           "AND (r.isDeleted IS NULL OR r.isDeleted = false)")
    long countByRestaurantGroupIdAndStatusAndIsDeletedFalse(
            @Param("restaurantGroupId") UUID restaurantGroupId,
            @Param("status") EntityStatus status);
    
    /**
     * Count distinct promotions used in transactions (via orders) for a specific restaurant
     * Promotions are tracked through their associated discount that was applied to orders
     * Only counts promotions from completed transactions
     */
    @Query("SELECT COUNT(DISTINCT p) FROM Transaction t " +
           "JOIN t.order o " +
           "JOIN t.restaurant r " +
           "JOIN Promotion p ON p.discount = o.discount " +
           "WHERE r.id = :restaurantId " +
           "AND t.transactionStatus = 'COMPLETED' " +
           "AND o.discount IS NOT NULL " +
           "AND p.status = :status " +
           "AND p.isDeleted = false " +
           "AND (r.isDeleted IS NULL OR r.isDeleted = false)")
    long countByRestaurantIdAndStatusAndIsDeletedFalse(
            @Param("restaurantId") UUID restaurantId,
            @Param("status") EntityStatus status);
    
    /**
     * Count distinct promotions used in transactions (via orders) for restaurants in a restaurant group, within a date range
     * Promotions are tracked through their associated discount that was applied to orders
     * Only counts promotions from completed transactions created within the date range
     */
    @Query("SELECT COUNT(DISTINCT p) FROM Transaction t " +
           "JOIN t.order o " +
           "JOIN t.restaurant r " +
           "JOIN Promotion p ON p.discount = o.discount " +
           "WHERE r.restaurantGroup.id = :restaurantGroupId " +
           "AND t.transactionStatus = 'COMPLETED' " +
           "AND o.discount IS NOT NULL " +
           "AND p.status = :status " +
           "AND p.isDeleted = false " +
           "AND t.createdAt >= :startDate " +
           "AND t.createdAt <= :endDate " +
           "AND (r.isDeleted IS NULL OR r.isDeleted = false)")
    long countByRestaurantGroupIdAndStatusAndIsDeletedFalseAndCreatedAtBetween(
            @Param("restaurantGroupId") UUID restaurantGroupId,
            @Param("status") EntityStatus status,
            @Param("startDate") java.time.LocalDateTime startDate,
            @Param("endDate") java.time.LocalDateTime endDate);
    
    /**
     * Get all active promotions (status = ACTIVE and not deleted)
     * Filtered by restaurant group or restaurant if provided (checks if promotion is linked to restaurants)
     */
    @Query("SELECT DISTINCT p FROM Promotion p " +
           "WHERE p.status = 'ACTIVE' " +
           "AND p.isDeleted = false " +
           "AND (:restaurantGroupId IS NULL OR EXISTS (" +
           "    SELECT 1 FROM MenuPromotionMapping mpm " +
           "    JOIN RestaurantMenuMapping rmm ON rmm.id.menuId = mpm.menu.id " +
           "    JOIN Restaurant r ON r.id = rmm.id.restaurantId " +
           "    WHERE mpm.promotion.id = p.id " +
           "    AND r.restaurantGroup.id = :restaurantGroupId " +
           "    AND (r.isDeleted IS NULL OR r.isDeleted = false)" +
           ")) " +
           "AND (:restaurantId IS NULL OR EXISTS (" +
           "    SELECT 1 FROM MenuPromotionMapping mpm " +
           "    JOIN RestaurantMenuMapping rmm ON rmm.id.menuId = mpm.menu.id " +
           "    WHERE mpm.promotion.id = p.id " +
           "    AND rmm.id.restaurantId = :restaurantId" +
           "))")
    List<Promotion> findAllActivePromotions(@Param("restaurantGroupId") UUID restaurantGroupId, @Param("restaurantId") UUID restaurantId);
    
    /**
     * Count conversions (completed transactions) for a promotion
     * Filtered by restaurant group if provided
     */
    @Query("SELECT COUNT(DISTINCT t.id) FROM Transaction t " +
           "JOIN t.order o " +
           "JOIN t.restaurant r " +
           "JOIN Promotion p ON p.discount = o.discount " +
           "WHERE p.id = :promotionId " +
           "AND t.transactionStatus = 'COMPLETED' " +
           "AND o.discount IS NOT NULL " +
           "AND (:restaurantGroupId IS NULL OR r.restaurantGroup.id = :restaurantGroupId) " +
           "AND (r.isDeleted IS NULL OR r.isDeleted = false)")
    long countConversionsByPromotionId(
            @Param("promotionId") UUID promotionId,
            @Param("restaurantGroupId") UUID restaurantGroupId);
    
    /**
     * Calculate revenue (sum of transaction amounts) for a promotion
     * Filtered by restaurant group if provided
     */
    @Query("SELECT COALESCE(SUM(t.transactionAmount), 0) FROM Transaction t " +
           "JOIN t.order o " +
           "JOIN t.restaurant r " +
           "JOIN Promotion p ON p.discount = o.discount " +
           "WHERE p.id = :promotionId " +
           "AND t.transactionStatus = 'COMPLETED' " +
           "AND o.discount IS NOT NULL " +
           "AND (:restaurantGroupId IS NULL OR r.restaurantGroup.id = :restaurantGroupId) " +
           "AND (r.isDeleted IS NULL OR r.isDeleted = false)")
    java.math.BigDecimal calculateRevenueByPromotionId(
            @Param("promotionId") UUID promotionId,
            @Param("restaurantGroupId") UUID restaurantGroupId);
} 