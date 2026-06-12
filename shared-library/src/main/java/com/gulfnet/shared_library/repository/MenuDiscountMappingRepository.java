package com.gulfnet.shared_library.repository;

import com.gulfnet.shared_library.entity.MenuDiscountMapping;
import com.gulfnet.shared_library.entity.MenuDiscountId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface MenuDiscountMappingRepository extends JpaRepository<MenuDiscountMapping, MenuDiscountId> {
    
    List<MenuDiscountMapping> findByDiscountId(UUID discountId);
    
    @Query("SELECT CASE WHEN COUNT(mdm) > 0 THEN true ELSE false END FROM MenuDiscountMapping mdm " +
           "WHERE mdm.discount.id = :discountId " +
           "AND mdm.menu.status = 'PUBLISHED' " +
           "AND (mdm.menu.isDeleted = false OR mdm.menu.isDeleted IS NULL)")
    boolean isDiscountUsedInPublishedMenu(@Param("discountId") UUID discountId);
    
    List<MenuDiscountMapping> findByMenuId(UUID menuId);
    
    @Query("SELECT CASE WHEN COUNT(mdm) > 0 THEN true ELSE false END FROM MenuDiscountMapping mdm " +
           "WHERE mdm.discount.id = :discountId " +
           "AND mdm.menu.id = :menuId " +
           "AND (mdm.menu.isDeleted = false OR mdm.menu.isDeleted IS NULL)")
    boolean isDiscountAssignedToMenu(@Param("discountId") UUID discountId, @Param("menuId") UUID menuId);

    @Query("SELECT COUNT(mdm) FROM MenuDiscountMapping mdm " +
           "JOIN RestaurantMenuMapping rmm ON rmm.id.menuId = mdm.menu.id " +
           "WHERE rmm.id.restaurantId = :restaurantId " +
           "AND mdm.discount.status = 'ACTIVE' " +
           "AND mdm.discount.isDeleted = false " +
           "AND mdm.menu.isDeleted = false")
    Long countActiveDiscountsByRestaurantId(@Param("restaurantId") UUID restaurantId);

    // Batch method to count active discounts for multiple restaurants
    @Query("SELECT rmm.id.restaurantId, COUNT(mdm) FROM MenuDiscountMapping mdm " +
           "JOIN RestaurantMenuMapping rmm ON rmm.id.menuId = mdm.menu.id " +
           "WHERE rmm.id.restaurantId IN :restaurantIds " +
           "AND mdm.discount.status = 'ACTIVE' " +
           "AND mdm.discount.isDeleted = false " +
           "AND mdm.menu.isDeleted = false " +
           "GROUP BY rmm.id.restaurantId")
    List<Object[]> countActiveDiscountsByRestaurantIds(@Param("restaurantIds") List<UUID> restaurantIds);
    
    // Batch method to count menu assignments for multiple discount IDs
    @Query("SELECT mdm.discount.id, COUNT(mdm) FROM MenuDiscountMapping mdm " +
           "WHERE mdm.discount.id IN :discountIds " +
           "GROUP BY mdm.discount.id")
    List<Object[]> countMenuAssignmentsByDiscountIds(@Param("discountIds") List<UUID> discountIds);
    
    /**
     * Count active ITEM discounts by restaurant with validity date checks
     */
    @Query("SELECT COUNT(DISTINCT mdm.discount.id) FROM MenuDiscountMapping mdm " +
           "JOIN RestaurantMenuMapping rmm ON rmm.id.menuId = mdm.menu.id " +
           "WHERE rmm.id.restaurantId = :restaurantId " +
           "AND mdm.discount.status = 'ACTIVE' " +
           "AND mdm.discount.isDeleted = false " +
           "AND mdm.discount.appliedTo = 'ITEM' " +
           "AND mdm.menu.isDeleted = false " +
           "AND (mdm.validFrom IS NULL OR mdm.validFrom <= CURRENT_TIMESTAMP) " +
           "AND (mdm.validTo IS NULL OR mdm.validTo >= CURRENT_TIMESTAMP)")
    Long countActiveItemDiscountsByRestaurantId(@Param("restaurantId") UUID restaurantId);
    
    /**
     * Count active CATEGORY discounts by restaurant with validity date checks
     */
    @Query("SELECT COUNT(DISTINCT mdm.discount.id) FROM MenuDiscountMapping mdm " +
           "JOIN RestaurantMenuMapping rmm ON rmm.id.menuId = mdm.menu.id " +
           "WHERE rmm.id.restaurantId = :restaurantId " +
           "AND mdm.discount.status = 'ACTIVE' " +
           "AND mdm.discount.isDeleted = false " +
           "AND mdm.discount.appliedTo = 'CATEGORY' " +
           "AND mdm.menu.isDeleted = false " +
           "AND (mdm.validFrom IS NULL OR mdm.validFrom <= CURRENT_TIMESTAMP) " +
           "AND (mdm.validTo IS NULL OR mdm.validTo >= CURRENT_TIMESTAMP)")
    Long countActiveCategoryDiscountsByRestaurantId(@Param("restaurantId") UUID restaurantId);
    
    /**
     * Count active ITEM discounts by restaurant group with validity date checks
     */
    @Query("SELECT COUNT(DISTINCT mdm.discount.id) FROM MenuDiscountMapping mdm " +
           "JOIN RestaurantMenuMapping rmm ON rmm.id.menuId = mdm.menu.id " +
           "JOIN Restaurant r ON r.id = rmm.id.restaurantId " +
           "WHERE r.restaurantGroup.id = :restaurantGroupId " +
           "AND mdm.discount.status = 'ACTIVE' " +
           "AND mdm.discount.isDeleted = false " +
           "AND mdm.discount.appliedTo = 'ITEM' " +
           "AND mdm.menu.isDeleted = false " +
           "AND (r.isDeleted IS NULL OR r.isDeleted = false) " +
           "AND (mdm.validFrom IS NULL OR mdm.validFrom <= CURRENT_TIMESTAMP) " +
           "AND (mdm.validTo IS NULL OR mdm.validTo >= CURRENT_TIMESTAMP)")
    Long countActiveItemDiscountsByRestaurantGroupId(@Param("restaurantGroupId") UUID restaurantGroupId);
    
    /**
     * Count active CATEGORY discounts by restaurant group with validity date checks
     */
    @Query("SELECT COUNT(DISTINCT mdm.discount.id) FROM MenuDiscountMapping mdm " +
           "JOIN RestaurantMenuMapping rmm ON rmm.id.menuId = mdm.menu.id " +
           "JOIN Restaurant r ON r.id = rmm.id.restaurantId " +
           "WHERE r.restaurantGroup.id = :restaurantGroupId " +
           "AND mdm.discount.status = 'ACTIVE' " +
           "AND mdm.discount.isDeleted = false " +
           "AND mdm.discount.appliedTo = 'CATEGORY' " +
           "AND mdm.menu.isDeleted = false " +
           "AND (r.isDeleted IS NULL OR r.isDeleted = false) " +
           "AND (mdm.validFrom IS NULL OR mdm.validFrom <= CURRENT_TIMESTAMP) " +
           "AND (mdm.validTo IS NULL OR mdm.validTo >= CURRENT_TIMESTAMP)")
    Long countActiveCategoryDiscountsByRestaurantGroupId(@Param("restaurantGroupId") UUID restaurantGroupId);
} 