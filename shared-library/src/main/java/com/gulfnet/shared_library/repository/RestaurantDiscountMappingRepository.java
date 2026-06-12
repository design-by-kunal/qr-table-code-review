package com.gulfnet.shared_library.repository;

import com.gulfnet.shared_library.entity.RestaurantDiscountMapping;
import com.gulfnet.shared_library.entity.RestaurantDiscountId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface RestaurantDiscountMappingRepository extends JpaRepository<RestaurantDiscountMapping, RestaurantDiscountId> {
    List<RestaurantDiscountMapping> findById_RestaurantId(UUID restaurantId);
    List<RestaurantDiscountMapping> findById_DiscountId(UUID discountId);
    List<RestaurantDiscountMapping> findById_RestaurantIdIn(List<UUID> restaurantIds);
    
    @Query("SELECT COUNT(rdm) FROM RestaurantDiscountMapping rdm " +
           "WHERE rdm.id.restaurantId = :restaurantId " +
           "AND rdm.discount.status = 'ACTIVE' " +
           "AND rdm.discount.isDeleted = false " +
           "AND (rdm.validFrom IS NULL OR rdm.validFrom <= CURRENT_TIMESTAMP) " +
           "AND (rdm.validTo IS NULL OR rdm.validTo >= CURRENT_TIMESTAMP)")
    Long countActiveDiscountsByRestaurantId(@Param("restaurantId") UUID restaurantId);
    
    // Batch method to count active discounts for multiple restaurants
    @Query("SELECT rdm.id.restaurantId, COUNT(rdm) FROM RestaurantDiscountMapping rdm " +
           "WHERE rdm.id.restaurantId IN :restaurantIds " +
           "AND rdm.discount.status = 'ACTIVE' " +
           "AND rdm.discount.isDeleted = false " +
           "AND (rdm.validFrom IS NULL OR rdm.validFrom <= CURRENT_TIMESTAMP) " +
           "AND (rdm.validTo IS NULL OR rdm.validTo >= CURRENT_TIMESTAMP) " +
           "GROUP BY rdm.id.restaurantId")
    List<Object[]> countActiveDiscountsByRestaurantIds(@Param("restaurantIds") List<UUID> restaurantIds);
    
    /**
     * Count active ORDER discounts by restaurant with validity date checks
     */
    @Query("SELECT COUNT(rdm) FROM RestaurantDiscountMapping rdm " +
           "WHERE rdm.id.restaurantId = :restaurantId " +
           "AND rdm.discount.status = 'ACTIVE' " +
           "AND rdm.discount.isDeleted = false " +
           "AND rdm.discount.appliedTo = 'ORDER' " +
           "AND (rdm.status IS NULL OR rdm.status = 'ACTIVE') " +
           "AND (rdm.validFrom IS NULL OR rdm.validFrom <= CURRENT_TIMESTAMP) " +
           "AND (rdm.validTo IS NULL OR rdm.validTo >= CURRENT_TIMESTAMP)")
    Long countActiveOrderDiscountsByRestaurantId(@Param("restaurantId") UUID restaurantId);
    
    /**
     * Count active ORDER discounts by restaurant group with validity date checks
     */
    @Query("SELECT COUNT(DISTINCT rdm.discount.id) FROM RestaurantDiscountMapping rdm " +
           "JOIN Restaurant r ON r.id = rdm.id.restaurantId " +
           "WHERE r.restaurantGroup.id = :restaurantGroupId " +
           "AND rdm.discount.status = 'ACTIVE' " +
           "AND rdm.discount.isDeleted = false " +
           "AND rdm.discount.appliedTo = 'ORDER' " +
           "AND (rdm.status IS NULL OR rdm.status = 'ACTIVE') " +
           "AND (rdm.validFrom IS NULL OR rdm.validFrom <= CURRENT_TIMESTAMP) " +
           "AND (rdm.validTo IS NULL OR rdm.validTo >= CURRENT_TIMESTAMP) " +
           "AND (r.isDeleted IS NULL OR r.isDeleted = false)")
    Long countActiveOrderDiscountsByRestaurantGroupId(@Param("restaurantGroupId") UUID restaurantGroupId);
}

