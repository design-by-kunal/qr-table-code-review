package com.gulfnet.shared_library.repository;

import com.gulfnet.shared_library.entity.RestaurantGroupMenuMapping;
import com.gulfnet.shared_library.entity.RestaurantGroupMenuId;
import com.gulfnet.shared_library.model.response.dto.MenuRestaurantGroupDetailsResponseDto;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.UUID;
import java.util.List;
import java.util.Optional; 


public interface RestaurantGroupMenuMappingRepository extends JpaRepository<RestaurantGroupMenuMapping, RestaurantGroupMenuId> {
    List<RestaurantGroupMenuMapping> findById_RestaurantGroupId(UUID restaurantGroupId);
    List<RestaurantGroupMenuMapping> findById_MenuId(UUID menuId);
    Optional<RestaurantGroupMenuMapping> findByMenuIdAndRestaurantGroupId(UUID menuId, UUID restaurantGroupId);
    
    boolean existsById_MenuId(UUID menuId);

    /**
     * Finds restaurant group details for groups assigned to a specific menu with optional filtering.
     * Returns a list of Object arrays containing restaurant group information with localized names
     * and counts of assigned restaurants. Results are ordered by restaurant group name.
     *
     * @param menuId  the UUID of the menu to find restaurant groups for
     * @param locale  the locale code for localized restaurant group names
     * @param status  optional status filter (ACTIVE, INACTIVE, etc.), null returns all statuses
     * @param search  optional search term to filter by restaurant group name (case-insensitive)
     * @return list of Object arrays containing: id, name, status, assignedRestaurantCount
     */
    @Query(value = """
        SELECT 
            rg.id as id,
            COALESCE(rgt.name::text, 'Unknown Restaurant Group') as name,
            rg.status as status,
            COUNT(DISTINCT rm.restaurant_id) as assignedRestaurantCount
        FROM restaurant_group_menu_mapping rgmm
        JOIN restaurant_group rg ON rg.id = rgmm.restaurant_group_id
        LEFT JOIN restaurant_group_translation rgt ON rgt.restaurant_group_id = rg.id AND rgt.language_code = :locale
        LEFT JOIN restaurant_menu_mapping rm ON rm.menu_id = :menuId 
            AND rm.restaurant_id IN (
                SELECT r.id FROM restaurant r 
                WHERE r.restaurant_group_id = rg.id 
                AND (r.is_deleted IS NULL OR r.is_deleted = false)
            )
        WHERE rgmm.menu_id = :menuId
        AND (:status IS NULL OR rg.status = :status)
        AND (:search IS NULL OR LOWER(COALESCE(rgt.name::text, 'Unknown Restaurant Group')) LIKE LOWER('%' || :search || '%'))
        GROUP BY rg.id, rgt.name, rg.status
        ORDER BY COALESCE(rgt.name::text, 'Unknown Restaurant Group') ASC
        """, nativeQuery = true)
    List<Object[]> findRestaurantGroupDetailsByMenuIdOptimizedNative(
        @Param("menuId") UUID menuId,
        @Param("locale") String locale,
        @Param("status") String status,
        @Param("search") String search
    );
}