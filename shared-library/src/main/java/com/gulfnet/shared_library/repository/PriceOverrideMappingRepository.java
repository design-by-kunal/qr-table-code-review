package com.gulfnet.shared_library.repository;

import com.gulfnet.shared_library.entity.PriceOverrideMapping;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface PriceOverrideMappingRepository extends JpaRepository<PriceOverrideMapping, UUID> {
    
    boolean existsByRestaurant_IdAndMenu_IdAndPriceOverride_Id(UUID restaurantId, UUID menuId, UUID priceOverrideId);
    
    @Query("SELECT m FROM PriceOverrideMapping m WHERE m.priceOverride.id = :priceOverrideId")
    List<PriceOverrideMapping> findByPriceOverrideId(@Param("priceOverrideId") UUID priceOverrideId);
    
    @Query("SELECT m FROM PriceOverrideMapping m " +
           "LEFT JOIN FETCH m.restaurant " +
           "LEFT JOIN FETCH m.menu " +
           "WHERE m.priceOverride.id = :priceOverrideId")
    List<PriceOverrideMapping> findByPriceOverrideIdWithRelations(@Param("priceOverrideId") UUID priceOverrideId);
    
    @Query("SELECT DISTINCT m.priceOverride FROM PriceOverrideMapping m " +
           "WHERE m.restaurant.id = :restaurantId " +
           "AND m.priceOverride.isDeleted = false")
    List<com.gulfnet.shared_library.entity.PriceOverride> findDistinctPriceOverridesByRestaurantId(@Param("restaurantId") UUID restaurantId);
    
    @Query("SELECT m FROM PriceOverrideMapping m " +
           "LEFT JOIN FETCH m.priceOverride " +
           "WHERE m.restaurant.id IN :restaurantIds")
    List<PriceOverrideMapping> findByRestaurantIdIn(@Param("restaurantIds") List<UUID> restaurantIds);
    
    /**
     * Batch fetch all mappings for multiple price override IDs with relations
     * This avoids N+1 query problem when loading mappings for multiple overrides
     */
    @Query("SELECT m FROM PriceOverrideMapping m " +
           "LEFT JOIN FETCH m.restaurant " +
           "LEFT JOIN FETCH m.menu " +
           "WHERE m.priceOverride.id IN :priceOverrideIds")
    List<PriceOverrideMapping> findByPriceOverrideIdInWithRelations(@Param("priceOverrideIds") List<UUID> priceOverrideIds);
}
