package com.gulfnet.shared_library.repository;

import com.gulfnet.shared_library.entity.RestaurantPromotionMapping;
import com.gulfnet.shared_library.entity.RestaurantPromotionId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface RestaurantPromotionMappingRepository extends JpaRepository<RestaurantPromotionMapping, RestaurantPromotionId> {
    List<RestaurantPromotionMapping> findById_RestaurantId(UUID restaurantId);
    List<RestaurantPromotionMapping> findById_PromotionId(UUID promotionId);
    List<RestaurantPromotionMapping> findById_RestaurantIdIn(List<UUID> restaurantIds);
    
    @Query("SELECT COUNT(rpm) FROM RestaurantPromotionMapping rpm " +
           "WHERE rpm.id.restaurantId = :restaurantId " +
           "AND rpm.promotion.status = 'ACTIVE' " +
           "AND rpm.promotion.isDeleted = false " +
           "AND (rpm.validFrom IS NULL OR rpm.validFrom <= CURRENT_TIMESTAMP) " +
           "AND (rpm.validTo IS NULL OR rpm.validTo >= CURRENT_TIMESTAMP)")
    Long countActivePromotionsByRestaurantId(@Param("restaurantId") UUID restaurantId);
    
    // Batch method to count active promotions for multiple restaurants
    @Query("SELECT rpm.id.restaurantId, COUNT(rpm) FROM RestaurantPromotionMapping rpm " +
           "WHERE rpm.id.restaurantId IN :restaurantIds " +
           "AND rpm.promotion.status = 'ACTIVE' " +
           "AND rpm.promotion.isDeleted = false " +
           "AND (rpm.validFrom IS NULL OR rpm.validFrom <= CURRENT_TIMESTAMP) " +
           "AND (rpm.validTo IS NULL OR rpm.validTo >= CURRENT_TIMESTAMP) " +
           "GROUP BY rpm.id.restaurantId")
    List<Object[]> countActivePromotionsByRestaurantIds(@Param("restaurantIds") List<UUID> restaurantIds);

    /**
     * Dashboard promotion stats: always from {@code restaurant_promotion_mapping}.
     * Optional filters: {@code restaurantId}, {@code restaurantGroupId}; when both are null, all
     * non-deleted restaurants with {@code rpm.status = ACTIVE} and active promotion are included.
     */
    @Query("SELECT rpm FROM RestaurantPromotionMapping rpm "
            + "JOIN rpm.promotion p "
            + "JOIN rpm.restaurant r "
            + "WHERE p.status = 'ACTIVE' "
            + "AND p.isDeleted = false "
            + "AND rpm.status = 'ACTIVE' "
            + "AND (r.isDeleted = false OR r.isDeleted IS NULL) "
            + "AND (:restaurantId IS NULL OR r.id = :restaurantId) "
            + "AND (:restaurantGroupId IS NULL OR r.restaurantGroup.id = :restaurantGroupId)")
    List<RestaurantPromotionMapping> findAllForDashboardPromotionStats(
            @Param("restaurantGroupId") UUID restaurantGroupId,
            @Param("restaurantId") UUID restaurantId);
}
