package com.gulfnet.shared_library.repository;

import com.gulfnet.shared_library.entity.RestaurantMenuMapping;
import com.gulfnet.shared_library.entity.RestaurantMenuId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.UUID;
import java.util.List;

@Repository
public interface RestaurantMenuMappingRepository extends JpaRepository<RestaurantMenuMapping, RestaurantMenuId> {
    List<RestaurantMenuMapping> findById_RestaurantId(UUID restaurantId);
    List<RestaurantMenuMapping> findById_MenuId(UUID menuId);
    long countByMenuIdAndRestaurantRestaurantGroupId(UUID menuId, UUID restaurantGroupId);
    List<RestaurantMenuMapping> findByMenuIdAndRestaurantRestaurantGroupId(UUID menuId, UUID restaurantGroupId);
    
    boolean existsById_MenuId(UUID menuId);
    List<RestaurantMenuMapping> findById_RestaurantIdIn(List<UUID> restaurantIds);
    
    @Query("SELECT CASE WHEN COUNT(rmm) > 0 THEN true ELSE false END FROM RestaurantMenuMapping rmm WHERE rmm.id.restaurantId = :restaurantId")
    boolean existsById_RestaurantId(@Param("restaurantId") UUID restaurantId);
    
    // More efficient existence check using LIMIT 1
    @Query(value = "SELECT 1 FROM restaurant_menu_mapping WHERE restaurant_id = :restaurantId LIMIT 1", nativeQuery = true)
    Integer existsById_RestaurantIdOptimized(@Param("restaurantId") UUID restaurantId);
    
    /**
     * Finds restaurant details for restaurants assigned to a specific menu with optional filtering.
     * Returns a list of Object arrays containing restaurant and restaurant group information
     * with localized names. Results are ordered by restaurant name.
     *
     * @param menuId           the UUID of the menu to find restaurants for
     * @param locale           the locale code for localized restaurant and group names
     * @param menuStatus       optional menu status filter (ACTIVE, INACTIVE, etc.)
     * @param restaurantGroupId optional restaurant group ID filter
     * @param search           optional search term to filter by restaurant name
     * @return list of Object arrays containing: restaurantId, restaurantName, restaurantCode,
     *         restaurantGroupId, restaurantGroupName, menuStatus, restaurantStatus, assignedAt,
     *         assignedBy, scheduledPublishTime
     */
    @Query(value = """
        SELECT 
            r.id as restaurantId,
            COALESCE(rt.name::text, 'Unknown Restaurant') as restaurantName,
            r.restaurant_code as restaurantCode,
            rg.id as restaurantGroupId,
            COALESCE(rgt.name::text, 'Unknown Restaurant Group') as restaurantGroupName,
            rmm.status as menuStatus,
            r.status as restaurantStatus,
            r.created_at as assignedAt,
            CONCAT(u.first_name, ' ', u.last_name) as assignedBy,
            rmm.scheduled_publish_time as scheduledPublishTime
        FROM restaurant_menu_mapping rmm
        JOIN restaurant r ON r.id = rmm.restaurant_id
        LEFT JOIN restaurant_translation rt ON rt.restaurant_id = r.id AND rt.language_code = :locale
        LEFT JOIN restaurant_group rg ON rg.id = r.restaurant_group_id
        LEFT JOIN restaurant_group_translation rgt ON rgt.restaurant_group_id = rg.id AND rgt.language_code = :locale
        JOIN menu m ON m.id = rmm.menu_id
        LEFT JOIN users u ON u.id = r.created_by
        WHERE rmm.menu_id = :menuId
        AND (r.is_deleted IS NULL OR r.is_deleted = false)
        AND (:menuStatus IS NULL OR rmm.status = :menuStatus)
        AND (:restaurantGroupId IS NULL OR r.restaurant_group_id = :restaurantGroupId)
        AND (:search IS NULL OR LOWER(COALESCE(rt.name::text, 'Unknown Restaurant')) LIKE LOWER('%' || :search || '%'))
        ORDER BY COALESCE(rt.name::text, 'Unknown Restaurant') ASC
        """, nativeQuery = true)
    List<Object[]> findRestaurantDetailsByMenuIdOptimized(
        @Param("menuId") UUID menuId,
        @Param("locale") String locale,
        @Param("menuStatus") String menuStatus,
        @Param("restaurantGroupId") UUID restaurantGroupId,
        @Param("search") String search
    );

    @Query("SELECT DISTINCT rmm.id.restaurantId FROM RestaurantMenuMapping rmm WHERE rmm.id.restaurantId IN :restaurantIds")
    List<UUID> findRestaurantIdsWithMenuAssignments(@Param("restaurantIds") List<UUID> restaurantIds);


}