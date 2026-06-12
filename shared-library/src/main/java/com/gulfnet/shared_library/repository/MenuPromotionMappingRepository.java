package com.gulfnet.shared_library.repository;

import com.gulfnet.shared_library.entity.MenuPromotionMapping;
import com.gulfnet.shared_library.entity.MenuPromotionId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.UUID;
import java.util.List;
import java.util.Optional;

@Repository
public interface MenuPromotionMappingRepository extends JpaRepository<MenuPromotionMapping, MenuPromotionId> {
    Optional<MenuPromotionMapping> findById(MenuPromotionId id);
    List<MenuPromotionMapping> findByMenu_Id(UUID menuId);
    List<MenuPromotionMapping> findByPromotion_Id(UUID promotionId);
    void deleteByMenu_Id(UUID menuId);
    void deleteByPromotion_Id(UUID promotionId);
    long countByPromotionId(UUID promotionId);
    
    @Query("SELECT COUNT(mpm) FROM MenuPromotionMapping mpm " +
           "JOIN RestaurantMenuMapping rmm ON rmm.id.menuId = mpm.menu.id " +
           "WHERE rmm.id.restaurantId = :restaurantId " +
           "AND mpm.promotion.status = 'ACTIVE' " +
           "AND mpm.promotion.isDeleted = false " +
           "AND mpm.menu.isDeleted = false")
    Long countActivePromotionsByRestaurantId(@Param("restaurantId") UUID restaurantId);

    // Batch method to count active promotions for multiple restaurants
    @Query("SELECT rmm.id.restaurantId, COUNT(mpm) FROM MenuPromotionMapping mpm " +
           "JOIN RestaurantMenuMapping rmm ON rmm.id.menuId = mpm.menu.id " +
           "WHERE rmm.id.restaurantId IN :restaurantIds " +
           "AND mpm.promotion.status = 'ACTIVE' " +
           "AND mpm.promotion.isDeleted = false " +
           "AND mpm.menu.isDeleted = false " +
           "GROUP BY rmm.id.restaurantId")
    List<Object[]> countActivePromotionsByRestaurantIds(@Param("restaurantIds") List<UUID> restaurantIds);

    // Check if promotion is assigned to any published menus
    @Query("SELECT COUNT(mpm) > 0 FROM MenuPromotionMapping mpm " +
           "WHERE mpm.promotion.id = :promotionId " +
           "AND mpm.menu.status = 'PUBLISHED' " +
           "AND mpm.menu.isDeleted = false")
    boolean existsByPromotionIdAndMenuIsPublished(@Param("promotionId") UUID promotionId);
    
    /**
     * Get the earliest validFrom and latest validTo for a promotion
     * Returns [validFrom, validTo] as Object array
     */
    @Query(value = "SELECT MIN(valid_from), MAX(valid_to) FROM menu_promotion_mapping " +
           "WHERE promotion_id = :promotionId", nativeQuery = true)
    Object[] getPromotionDateRange(@Param("promotionId") java.util.UUID promotionId);
    
    /**
     * Get all MenuPromotionMapping for active promotions, filtered by restaurant group or restaurant
     * Joins through RestaurantMenuMapping to filter by restaurant
     */
    @Query("SELECT mpm FROM MenuPromotionMapping mpm " +
           "JOIN mpm.promotion p " +
           "JOIN mpm.menu m " +
           "WHERE p.status = 'ACTIVE' " +
           "AND p.isDeleted = false " +
           "AND m.isDeleted = false " +
           "AND (:restaurantGroupId IS NULL OR EXISTS (" +
           "    SELECT 1 FROM RestaurantMenuMapping rmm " +
           "    JOIN Restaurant r ON r.id = rmm.id.restaurantId " +
           "    WHERE rmm.id.menuId = m.id " +
           "    AND r.restaurantGroup.id = :restaurantGroupId " +
           "    AND (r.isDeleted IS NULL OR r.isDeleted = false)" +
           ")) " +
           "AND (:restaurantId IS NULL OR EXISTS (" +
           "    SELECT 1 FROM RestaurantMenuMapping rmm " +
           "    WHERE rmm.id.menuId = m.id " +
           "    AND rmm.id.restaurantId = :restaurantId" +
           "))")
    List<MenuPromotionMapping> findAllActiveMenuPromotionMappings(
            @Param("restaurantGroupId") UUID restaurantGroupId,
            @Param("restaurantId") UUID restaurantId);

    /**
     * Dashboard promotion availability source for group-level requests where restaurantId is not provided.
     * Intentionally does not filter by promotion/menu status (active/inactive).
     */
    @Query("SELECT DISTINCT mpm FROM MenuPromotionMapping mpm " +
           "JOIN mpm.promotion p " +
           "JOIN mpm.menu m " +
           "JOIN RestaurantMenuMapping rmm ON rmm.id.menuId = m.id " +
           "JOIN Restaurant r ON r.id = rmm.id.restaurantId " +
           "WHERE r.restaurantGroup.id = :restaurantGroupId " +
           "AND (r.isDeleted IS NULL OR r.isDeleted = false) " +
           "AND p.isDeleted = false " +
           "AND m.isDeleted = false")
    List<MenuPromotionMapping> findAllForDashboardPromotionStatsByRestaurantGroupIdNoStatus(
            @Param("restaurantGroupId") UUID restaurantGroupId);
}
