package com.gulfnet.shared_library.repository;

import com.gulfnet.shared_library.entity.Discount;
import com.gulfnet.shared_library.enums.EntityStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface DiscountRepository extends JpaRepository<Discount, UUID>, JpaSpecificationExecutor<Discount> {
    Page<Discount> findByStatusAndIsDeletedFalse(EntityStatus status, Pageable pageable);

    @Query("SELECT CASE WHEN COUNT(d) > 0 THEN true ELSE false END FROM Discount d WHERE LOWER(d.discountCode) = LOWER(:discountCode) AND d.isDeleted = false")
    boolean existsByDiscountCodeAndIsDeletedFalse(@Param("discountCode") String discountCode);

    @Query("SELECT d FROM Discount d WHERE LOWER(d.discountCode) = LOWER(:discountCode) AND d.isDeleted = false")
    Optional<Discount> findByDiscountCodeAndIsDeletedFalse(@Param("discountCode") String discountCode);
    
    /**
     * Count active discounts (status = ACTIVE and not deleted)
     */
    @Query("SELECT COUNT(d) FROM Discount d WHERE d.status = :status AND d.isDeleted = false")
    long countByStatusAndIsDeletedFalse(@Param("status") EntityStatus status);
    
    /**
     * Count active discounts created within a date range
     */
    @Query("SELECT COUNT(d) FROM Discount d WHERE d.status = :status AND d.isDeleted = false AND d.createdAt >= :startDate AND d.createdAt <= :endDate")
    long countByStatusAndIsDeletedFalseAndCreatedAtBetween(
            @Param("status") EntityStatus status,
            @Param("startDate") java.time.LocalDateTime startDate,
            @Param("endDate") java.time.LocalDateTime endDate);
    
    /**
     * Count distinct discounts used in transactions (via orders) for restaurants in a restaurant group
     * Only counts discounts from completed transactions
     */
    @Query("SELECT COUNT(DISTINCT o.discount) FROM Transaction t " +
           "JOIN t.order o " +
           "JOIN t.restaurant r " +
           "WHERE r.restaurantGroup.id = :restaurantGroupId " +
           "AND t.transactionStatus = 'COMPLETED' " +
           "AND o.discount IS NOT NULL " +
           "AND o.discount.status = :status " +
           "AND o.discount.isDeleted = false " +
           "AND (r.isDeleted IS NULL OR r.isDeleted = false)")
    long countByRestaurantGroupIdAndStatusAndIsDeletedFalse(
            @Param("restaurantGroupId") UUID restaurantGroupId,
            @Param("status") EntityStatus status);
    
    /**
     * Count distinct discounts used in transactions (via orders) for restaurants in a restaurant group, within a date range
     * Only counts discounts from completed transactions created within the date range
     */
    @Query("SELECT COUNT(DISTINCT o.discount) FROM Transaction t " +
           "JOIN t.order o " +
           "JOIN t.restaurant r " +
           "WHERE r.restaurantGroup.id = :restaurantGroupId " +
           "AND t.transactionStatus = 'COMPLETED' " +
           "AND o.discount IS NOT NULL " +
           "AND o.discount.status = :status " +
           "AND o.discount.isDeleted = false " +
           "AND t.createdAt >= :startDate " +
           "AND t.createdAt <= :endDate " +
           "AND (r.isDeleted IS NULL OR r.isDeleted = false)")
    long countByRestaurantGroupIdAndStatusAndIsDeletedFalseAndCreatedAtBetween(
            @Param("restaurantGroupId") UUID restaurantGroupId,
            @Param("status") EntityStatus status,
            @Param("startDate") java.time.LocalDateTime startDate,
            @Param("endDate") java.time.LocalDateTime endDate);
    
    /**
     * Count active discounts by appliedTo type (ORDER, ITEM, CATEGORY)
     */
    @Query("SELECT COUNT(d) FROM Discount d WHERE d.status = :status AND d.isDeleted = false AND d.appliedTo = :appliedTo")
    long countByStatusAndAppliedToAndIsDeletedFalse(
            @Param("status") EntityStatus status,
            @Param("appliedTo") com.gulfnet.shared_library.enums.AppliedTo appliedTo);
    
    /**
     * Count active ITEM discounts by restaurant group
     * Joins through ItemDiscountMapping -> CategoryItemMapping -> MenuCategoryMapping -> Menu -> RestaurantMenuMapping -> Restaurant
     */
    @Query("SELECT COUNT(DISTINCT d) FROM Discount d " +
           "JOIN ItemDiscountMapping idm ON idm.discount.id = d.id " +
           "JOIN idm.categoryItemMapping cim " +
           "JOIN cim.menuCategoryMapping mcm " +
           "JOIN RestaurantMenuMapping rmm ON rmm.id.menuId = mcm.menu.id " +
           "JOIN Restaurant r ON r.id = rmm.id.restaurantId " +
           "WHERE r.restaurantGroup.id = :restaurantGroupId " +
           "AND d.status = :status " +
           "AND d.isDeleted = false " +
           "AND d.appliedTo = :appliedTo " +
           "AND (r.isDeleted IS NULL OR r.isDeleted = false)")
    long countItemDiscountsByRestaurantGroupIdAndStatus(
            @Param("restaurantGroupId") UUID restaurantGroupId,
            @Param("status") EntityStatus status,
            @Param("appliedTo") com.gulfnet.shared_library.enums.AppliedTo appliedTo);
    
    /**
     * Count active CATEGORY discounts by restaurant group
     * Joins through CategoryDiscountMapping -> MenuCategoryMapping -> Menu -> RestaurantMenuMapping -> Restaurant
     */
    @Query("SELECT COUNT(DISTINCT d) FROM Discount d " +
           "JOIN CategoryDiscountMapping cdm ON cdm.discount.id = d.id " +
           "JOIN cdm.menuCategoryMapping mcm " +
           "JOIN RestaurantMenuMapping rmm ON rmm.id.menuId = mcm.menu.id " +
           "JOIN Restaurant r ON r.id = rmm.id.restaurantId " +
           "WHERE r.restaurantGroup.id = :restaurantGroupId " +
           "AND d.status = :status " +
           "AND d.isDeleted = false " +
           "AND d.appliedTo = :appliedTo " +
           "AND (r.isDeleted IS NULL OR r.isDeleted = false)")
    long countCategoryDiscountsByRestaurantGroupIdAndStatus(
            @Param("restaurantGroupId") UUID restaurantGroupId,
            @Param("status") EntityStatus status,
            @Param("appliedTo") com.gulfnet.shared_library.enums.AppliedTo appliedTo);
    
    /**
     * Count active discounts by appliedTo type and restaurant group
     * For ITEM discounts: joins through ItemDiscountMapping -> CategoryItemMapping -> MenuCategoryMapping -> Menu -> RestaurantMenuMapping -> Restaurant
     * For CATEGORY discounts: joins through CategoryDiscountMapping -> MenuCategoryMapping -> Menu -> RestaurantMenuMapping -> Restaurant
     * This method delegates to the appropriate specific method based on appliedTo type
     */
    default long countByRestaurantGroupIdAndStatusAndAppliedToAndIsDeletedFalseForItemCategory(
            UUID restaurantGroupId, EntityStatus status, com.gulfnet.shared_library.enums.AppliedTo appliedTo) {
        if (appliedTo == com.gulfnet.shared_library.enums.AppliedTo.ITEM) {
            return countItemDiscountsByRestaurantGroupIdAndStatus(restaurantGroupId, status, appliedTo);
        } else if (appliedTo == com.gulfnet.shared_library.enums.AppliedTo.CATEGORY) {
            return countCategoryDiscountsByRestaurantGroupIdAndStatus(restaurantGroupId, status, appliedTo);
        }
        return 0L;
    }
    
    /**
     * Count active ORDER discounts by restaurant group (via Order table)
     */
    @Query("SELECT COUNT(DISTINCT o.discount) FROM Transaction t " +
           "JOIN t.order o " +
           "JOIN t.restaurant r " +
           "WHERE r.restaurantGroup.id = :restaurantGroupId " +
           "AND t.transactionStatus = 'COMPLETED' " +
           "AND o.discount IS NOT NULL " +
           "AND o.discount.status = :status " +
           "AND o.discount.appliedTo = 'ORDER' " +
           "AND o.discount.isDeleted = false " +
           "AND (r.isDeleted IS NULL OR r.isDeleted = false)")
    long countOrderDiscountsByRestaurantGroupIdAndStatus(
            @Param("restaurantGroupId") UUID restaurantGroupId,
            @Param("status") EntityStatus status);
    
    /**
     * Count distinct discounts used in transactions (via orders) for a specific restaurant
     * Only counts discounts from completed transactions
     */
    @Query("SELECT COUNT(DISTINCT o.discount) FROM Transaction t " +
           "JOIN t.order o " +
           "JOIN t.restaurant r " +
           "WHERE r.id = :restaurantId " +
           "AND t.transactionStatus = 'COMPLETED' " +
           "AND o.discount IS NOT NULL " +
           "AND o.discount.status = :status " +
           "AND o.discount.isDeleted = false " +
           "AND (r.isDeleted IS NULL OR r.isDeleted = false)")
    long countByRestaurantIdAndStatusAndIsDeletedFalse(
            @Param("restaurantId") UUID restaurantId,
            @Param("status") EntityStatus status);
    
    /**
     * Count active ITEM discounts by restaurant
     */
    @Query("SELECT COUNT(DISTINCT d) FROM Discount d " +
           "JOIN ItemDiscountMapping idm ON idm.discount.id = d.id " +
           "JOIN idm.categoryItemMapping cim " +
           "JOIN cim.menuCategoryMapping mcm " +
           "JOIN RestaurantMenuMapping rmm ON rmm.id.menuId = mcm.menu.id " +
           "WHERE rmm.id.restaurantId = :restaurantId " +
           "AND d.status = :status " +
           "AND d.isDeleted = false " +
           "AND d.appliedTo = :appliedTo")
    long countItemDiscountsByRestaurantIdAndStatus(
            @Param("restaurantId") UUID restaurantId,
            @Param("status") EntityStatus status,
            @Param("appliedTo") com.gulfnet.shared_library.enums.AppliedTo appliedTo);
    
    /**
     * Count active CATEGORY discounts by restaurant
     */
    @Query("SELECT COUNT(DISTINCT d) FROM Discount d " +
           "JOIN CategoryDiscountMapping cdm ON cdm.discount.id = d.id " +
           "JOIN cdm.menuCategoryMapping mcm " +
           "JOIN RestaurantMenuMapping rmm ON rmm.id.menuId = mcm.menu.id " +
           "WHERE rmm.id.restaurantId = :restaurantId " +
           "AND d.status = :status " +
           "AND d.isDeleted = false " +
           "AND d.appliedTo = :appliedTo")
    long countCategoryDiscountsByRestaurantIdAndStatus(
            @Param("restaurantId") UUID restaurantId,
            @Param("status") EntityStatus status,
            @Param("appliedTo") com.gulfnet.shared_library.enums.AppliedTo appliedTo);
    
    /**
     * Count active ORDER discounts by restaurant (via Order table)
     */
    @Query("SELECT COUNT(DISTINCT o.discount) FROM Transaction t " +
           "JOIN t.order o " +
           "JOIN t.restaurant r " +
           "WHERE r.id = :restaurantId " +
           "AND t.transactionStatus = 'COMPLETED' " +
           "AND o.discount IS NOT NULL " +
           "AND o.discount.status = :status " +
           "AND o.discount.appliedTo = 'ORDER' " +
           "AND o.discount.isDeleted = false " +
           "AND (r.isDeleted IS NULL OR r.isDeleted = false)")
    long countOrderDiscountsByRestaurantIdAndStatus(
            @Param("restaurantId") UUID restaurantId,
            @Param("status") EntityStatus status);
    
    /**
     * Count active discounts by appliedTo type and restaurant
     * Delegates to appropriate method based on appliedTo type
     */
    default long countByRestaurantIdAndStatusAndAppliedToAndIsDeletedFalseForItemCategory(
            UUID restaurantId, EntityStatus status, com.gulfnet.shared_library.enums.AppliedTo appliedTo) {
        if (appliedTo == com.gulfnet.shared_library.enums.AppliedTo.ITEM) {
            return countItemDiscountsByRestaurantIdAndStatus(restaurantId, status, appliedTo);
        } else if (appliedTo == com.gulfnet.shared_library.enums.AppliedTo.CATEGORY) {
            return countCategoryDiscountsByRestaurantIdAndStatus(restaurantId, status, appliedTo);
        }
        return 0L;
    }
    
    /**
     * Optimized query to fetch discounts by IDs with translations and user relationships eagerly loaded
     * Note: Cannot fetch multiple bags (translations and bxgyItems) in one query due to Hibernate limitation
     * BXGY items will be loaded separately via batch fetching
     */
    @Query("SELECT DISTINCT d FROM Discount d " +
           "LEFT JOIN FETCH d.translations " +
           "LEFT JOIN FETCH d.createdBy " +
           "LEFT JOIN FETCH d.updatedBy " +
           "WHERE d.id IN :discountIds")
    List<Discount> findAllByIdWithRelations(@Param("discountIds") List<UUID> discountIds);
} 