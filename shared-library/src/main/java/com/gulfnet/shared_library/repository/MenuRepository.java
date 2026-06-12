package com.gulfnet.shared_library.repository;    
import com.gulfnet.shared_library.entity.Menu;
import com.gulfnet.shared_library.enums.MenuStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

@Repository
public interface MenuRepository extends JpaRepository<Menu, UUID>,JpaSpecificationExecutor<Menu> {

    // find all menus by published status
    List<Menu> findByIsPublished(boolean isPublished);

    // find latest published version by menu structure
    Menu findFirstByMenuStructureIdAndIsPublishedOrderByVersionDesc(UUID menuStructureId, boolean isPublished);

    // check duplicate draft for menu structure
    boolean existsByMenuStructureIdAndStatus(UUID menuStructureId, MenuStatus status);

    // check if there are any published menus for a menu structure
    boolean existsByMenuStructureIdAndIsPublishedTrueAndIsDeletedFalse(UUID menuStructureId);

    // find all menus by menu master id
    List<Menu> findByMenuMasterIdAndIsDeletedFalseOrderByVersionDesc(UUID menuMasterId);

    // find all menus by menu master id and status
    List<Menu> findByMenuMasterIdAndStatusAndIsDeletedFalseOrderByVersionDesc(UUID menuMasterId, MenuStatus status);

    // find all menus by menu structure id ordered by creation date
    List<Menu> findByMenuStructureIdOrderByCreatedAtAsc(UUID menuStructureId);

    // check if there are any non-deleted menus for a menu structure
    boolean existsByMenuStructureIdAndIsDeletedFalse(UUID menuStructureId);
    
    // count non-deleted menus for a menu structure
    @Query("SELECT COUNT(m) FROM Menu m " +
           "WHERE m.menuStructure.id = :menuStructureId " +
           "AND (m.isDeleted IS NULL OR m.isDeleted = false)")
    long countByMenuStructureIdAndIsDeletedFalse(@Param("menuStructureId") UUID menuStructureId);

    // check if there are any active menus for a menu structure
    // Active means status = PUBLISHED AND isDeleted = false
    @Query("SELECT CASE WHEN COUNT(m) > 0 THEN true ELSE false END FROM Menu m " +
           "WHERE m.menuStructure.id = :menuStructureId " +
           "AND m.status = :status " +
           "AND (m.isDeleted IS NULL OR m.isDeleted = false)")
    boolean existsByMenuStructureIdAndStatusAndIsDeletedFalse(
            @Param("menuStructureId") UUID menuStructureId, 
            @Param("status") MenuStatus status);

    @Query("SELECT CASE WHEN COUNT(t) > 0 THEN true ELSE false END FROM MenuTranslation t " +
       "WHERE LOWER(t.name) = LOWER(:name) AND t.menu.isDeleted = false")
boolean existsByNameIgnoreCase(@Param("name") String name);

    // Find all menus by status
    List<Menu> findByStatusAndIsDeletedFalse(MenuStatus status);

    // Count menus by status
    long countByStatusAndIsDeletedFalse(MenuStatus status);

    /**
     * Count distinct published menus assigned to a specific restaurant
     * Only counts menus with LIVE status in RestaurantMenuMapping to exclude deleted/historical mappings
     */
    @Query("SELECT COUNT(DISTINCT m.id) FROM Menu m " +
           "JOIN RestaurantMenuMapping rmm ON rmm.id.menuId = m.id " +
           "WHERE rmm.id.restaurantId = :restaurantId " +
           "AND rmm.status = 'LIVE' " +
           "AND m.status = :status " +
           "AND (m.isDeleted IS NULL OR m.isDeleted = false)")
    long countPublishedMenusByRestaurantId(@Param("restaurantId") UUID restaurantId, @Param("status") MenuStatus status);

    /**
     * Count distinct published menus assigned to restaurants in a restaurant group
     * Only counts menus with LIVE status in RestaurantMenuMapping to exclude deleted/historical mappings
     */
    @Query("SELECT COUNT(DISTINCT m.id) FROM Menu m " +
           "JOIN RestaurantMenuMapping rmm ON rmm.id.menuId = m.id " +
           "JOIN Restaurant r ON r.id = rmm.id.restaurantId " +
           "WHERE r.restaurantGroup.id = :restaurantGroupId " +
           "AND rmm.status = 'LIVE' " +
           "AND m.status = :status " +
           "AND (m.isDeleted IS NULL OR m.isDeleted = false) " +
           "AND (r.isDeleted IS NULL OR r.isDeleted = false)")
    long countPublishedMenusByRestaurantGroupId(@Param("restaurantGroupId") UUID restaurantGroupId, @Param("status") MenuStatus status);
}
