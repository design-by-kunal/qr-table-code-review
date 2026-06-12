package com.gulfnet.shared_library.repository;

import com.gulfnet.shared_library.entity.RestaurantItemAvailability;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface RestaurantItemAvailabilityRepository extends JpaRepository<RestaurantItemAvailability, UUID> {
    
    /**
     * Check if availability override exists for restaurant and category item mapping
     */
    boolean existsByRestaurantIdAndCategoryItemMappingId(UUID restaurantId, UUID categoryItemMappingId);
    
    /**
     * Find availability override by restaurant and category item mapping (without time filter)
     */
    Optional<RestaurantItemAvailability> findByRestaurantIdAndCategoryItemMappingId(
            UUID restaurantId, UUID categoryItemMappingId);
    
    /**
     * Find availability overrides by restaurant and multiple category item mappings
     */
    List<RestaurantItemAvailability> findByRestaurantIdAndCategoryItemMappingIdIn(
            UUID restaurantId, List<UUID> categoryItemMappingIds);
    
    /**
     * Delete availability record by restaurant and category item mapping
     */
    void deleteByRestaurantIdAndCategoryItemMappingId(UUID restaurantId, UUID categoryItemMappingId);
    
    /**
     * Find recently unavailable items (isAvailable = false) ordered by updatedAt DESC
     * Filtered by restaurant ID or restaurant group ID
     * JPQL handles null parameters properly, so null can be passed directly
     * Uses JOIN FETCH to eagerly load relationships to avoid LazyInitializationException
     */
    @Query("SELECT DISTINCT ria FROM RestaurantItemAvailability ria " +
           "JOIN FETCH ria.restaurant r " +
           "LEFT JOIN FETCH ria.categoryItemMapping cim " +
           "LEFT JOIN FETCH cim.item i " +
           "LEFT JOIN FETCH cim.menuCategoryMapping mcm " +
           "LEFT JOIN FETCH mcm.category c " +
           "WHERE ria.isAvailable = false " +
           "AND (:restaurantId IS NULL OR r.id = :restaurantId) " +
           "AND (:restaurantGroupId IS NULL OR r.restaurantGroup.id = :restaurantGroupId) " +
           "AND (r.isDeleted IS NULL OR r.isDeleted = false) " +
           "ORDER BY ria.updatedAt DESC")
    List<RestaurantItemAvailability> findRecentlyUnavailableItems(
            @Param("restaurantId") UUID restaurantId,
            @Param("restaurantGroupId") UUID restaurantGroupId);
    
    /**
     * Find all restaurant item availability records by restaurant ID
     * Uses JOIN FETCH to eagerly load relationships to avoid LazyInitializationException
     */
    @Query("SELECT DISTINCT ria FROM RestaurantItemAvailability ria " +
           "JOIN FETCH ria.restaurant r " +
           "LEFT JOIN FETCH ria.categoryItemMapping cim " +
           "LEFT JOIN FETCH cim.item i " +
           "LEFT JOIN FETCH cim.menuCategoryMapping mcm " +
           "WHERE r.id = :restaurantId " +
           "AND (r.isDeleted IS NULL OR r.isDeleted = false)")
    List<RestaurantItemAvailability> findByRestaurantId(@Param("restaurantId") UUID restaurantId);
    
    /**
     * Find all restaurant item availability records by category item mapping IDs
     * This is used to delete all availability records before deleting category_item_mapping records
     */
    @Query("SELECT ria FROM RestaurantItemAvailability ria " +
           "WHERE ria.categoryItemMapping.id IN :categoryItemMappingIds")
    List<RestaurantItemAvailability> findByCategoryItemMappingIdIn(@Param("categoryItemMappingIds") List<UUID> categoryItemMappingIds);
}
