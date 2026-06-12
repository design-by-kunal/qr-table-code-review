// CategoryItemMappingRepository.java
package com.gulfnet.shared_library.repository;

import com.gulfnet.shared_library.entity.CategoryItemMapping;
import com.gulfnet.shared_library.entity.MenuCategoryMapping;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.Collection;
import java.util.List; 
import java.util.Set;
import java.util.UUID;
import java.util.Optional;

@Repository
public interface CategoryItemMappingRepository extends JpaRepository<CategoryItemMapping, UUID> {
    boolean existsByMenuCategoryMapping_IdAndItem_Id(UUID menuCategoryMappingId, UUID itemId);
    void deleteByMenuCategoryMappingIn(Set<MenuCategoryMapping> menuCategoryMappings);
    List<CategoryItemMapping> findByMenuCategoryMapping(MenuCategoryMapping menuCategoryMapping);
    List<CategoryItemMapping> findByMenuCategoryMappingIn(List<MenuCategoryMapping> menuCategoryMappings);
    List<CategoryItemMapping> findByItem_Id(UUID itemId);
    CategoryItemMapping findByMenuCategoryMapping_IdAndItem_Id(UUID menuCategoryMappingId, UUID itemId);
    @Query("SELECT c FROM CategoryItemMapping c WHERE c.menuCategoryMapping.menu.id = :menuId")
    List<CategoryItemMapping> findByMenuCategoryMappingMenuId(@Param("menuId") UUID menuId);


    @Query("SELECT cim FROM CategoryItemMapping cim " +
       "JOIN cim.menuCategoryMapping mcm " +
       "WHERE cim.item.id = :itemId AND mcm.menu.id = :menuId")
Optional<CategoryItemMapping> findByItemIdAndMenuCategoryMappingMenuId(
    @Param("itemId") UUID itemId, 
    @Param("menuId") UUID menuId
);

    @Query("SELECT c FROM CategoryItemMapping c WHERE c.menuCategoryMapping.menu.id = :menuId AND c.item.id = :itemId")
    Optional<CategoryItemMapping> findByMenuCategoryMappingMenuIdAndItemId(@Param("menuId") UUID menuId, @Param("itemId") UUID itemId);

    /**
     * All category placements of an item on a given menu (same item may appear under multiple categories).
     */
    @Query("SELECT c FROM CategoryItemMapping c WHERE c.menuCategoryMapping.menu.id = :menuId AND c.item.id = :itemId")
    List<CategoryItemMapping> findAllByMenuCategoryMappingMenuIdAndItemId(
            @Param("menuId") UUID menuId, @Param("itemId") UUID itemId);

    // Batch fetch methods for performance optimization
    @Query("SELECT DISTINCT cim FROM CategoryItemMapping cim " +
            "JOIN FETCH cim.item i " +
            "JOIN FETCH cim.menuCategoryMapping mcm " +
            "LEFT JOIN FETCH mcm.category c " +
            "LEFT JOIN FETCH c.parentCategory pc " +
            "LEFT JOIN FETCH mcm.menu m " +
            "WHERE i.id IN :itemIds")
    List<CategoryItemMapping> findByItem_IdIn(@Param("itemIds") List<UUID> itemIds);

    /**
     * Batch fetch CategoryItemMappings for specific items within a given menu.
     * Includes MenuCategoryMapping + Category + ParentCategory so callers can resolve "main" category for subcategory items.
     */
    @Query("SELECT cim FROM CategoryItemMapping cim " +
            "JOIN FETCH cim.item i " +
            "JOIN FETCH cim.menuCategoryMapping mcm " +
            "LEFT JOIN FETCH mcm.category c " +
            "LEFT JOIN FETCH mcm.parentCategory pc " +
            "WHERE mcm.menu.id = :menuId AND i.id IN :itemIds")
    List<CategoryItemMapping> findByMenuIdAndItemIdsWithCategoryHierarchy(
            @Param("menuId") UUID menuId,
            @Param("itemIds") List<UUID> itemIds);
    
    /**
     * Find CategoryItemMappings by menu_category_mapping_ids
     * Used to get items impacted by category-level price overrides
     */
    @Query("SELECT DISTINCT cim FROM CategoryItemMapping cim " +
           "JOIN FETCH cim.item i " +
           "JOIN FETCH cim.menuCategoryMapping mcm " +
           "JOIN mcm.menu m " +
           "JOIN RestaurantMenuMapping rmm ON rmm.id.menuId = m.id " +
           "WHERE mcm.id IN :menuCategoryMappingIds " +
           "AND rmm.id.restaurantId = :restaurantId " +
           "AND (i.isDeleted IS NULL OR i.isDeleted = false)")
    List<CategoryItemMapping> findByMenuCategoryMappingIdsAndRestaurant(
            @Param("menuCategoryMappingIds") List<UUID> menuCategoryMappingIds,
            @Param("restaurantId") UUID restaurantId);
    
    /**
     * Find CategoryItemMappings by menu_id
     * Used to get items impacted by menu-level price overrides
     */
    @Query("SELECT DISTINCT cim FROM CategoryItemMapping cim " +
           "JOIN FETCH cim.item i " +
           "JOIN FETCH cim.menuCategoryMapping mcm " +
           "JOIN mcm.menu m " +
           "JOIN RestaurantMenuMapping rmm ON rmm.id.menuId = m.id " +
           "WHERE m.id = :menuId " +
           "AND rmm.id.restaurantId = :restaurantId " +
           "AND (i.isDeleted IS NULL OR i.isDeleted = false)")
    List<CategoryItemMapping> findByMenuIdAndRestaurant(
            @Param("menuId") UUID menuId,
            @Param("restaurantId") UUID restaurantId);
    
    /**
     * Get menu_category_mapping_id directly from category_item_mapping table to avoid lazy loading issues
     * @param itemId The item ID
     * @return List of menu_category_mapping_id UUIDs
     */
    @Query(value = "SELECT menu_category_mapping_id FROM category_item_mapping WHERE item_id = :itemId", nativeQuery = true)
    List<UUID> findMenuCategoryMappingIdsByItemId(@Param("itemId") UUID itemId);
    
    /**
     * Count distinct published, non-deleted menus that contain a specific item
     * @param itemId The item ID
     * @return Count of distinct published menus containing the item
     */
    @Query("SELECT COUNT(DISTINCT mcm.menu.id) FROM CategoryItemMapping cim " +
           "JOIN cim.menuCategoryMapping mcm " +
           "JOIN mcm.menu m " +
           "WHERE cim.item.id = :itemId " +
           "AND (m.isDeleted IS NULL OR m.isDeleted = false) " +
           "AND m.status = com.gulfnet.shared_library.enums.MenuStatus.PUBLISHED")
    long countDistinctMenusByItemId(@Param("itemId") UUID itemId);

    /**
     * Batch-count distinct published, non-deleted menus that contain any of the given items.
     * Returns a list of Object[] where:
     *   [0] = UUID itemId
     *   [1] = Number countOfDistinctPublishedMenus
     */
    @Query("SELECT cim.item.id, COUNT(DISTINCT mcm.menu.id) " +
           "FROM CategoryItemMapping cim " +
           "JOIN cim.menuCategoryMapping mcm " +
           "JOIN mcm.menu m " +
           "WHERE cim.item.id IN :itemIds " +
           "AND (m.isDeleted IS NULL OR m.isDeleted = false) " +
           "AND m.status = com.gulfnet.shared_library.enums.MenuStatus.PUBLISHED " +
           "GROUP BY cim.item.id")
    List<Object[]> countMenusByItemIdsBatch(@Param("itemIds") List<UUID> itemIds);
    
    /**
     * Find CategoryItemMappings by item ID with eagerly fetched relationships.
     * Includes MenuCategoryMapping + Category + ParentCategory to avoid lazy loading issues.
     * Used for filtering items by category in KDS dashboard.
     */
    @Query("SELECT DISTINCT cim FROM CategoryItemMapping cim " +
            "JOIN FETCH cim.menuCategoryMapping mcm " +
            "LEFT JOIN FETCH mcm.category c " +
            "LEFT JOIN FETCH c.parentCategory pc " +
            "WHERE cim.item.id = :itemId")
    List<CategoryItemMapping> findByItemIdWithCategoryHierarchy(@Param("itemId") UUID itemId);

    /**
     * Batch variant of {@link #findByItemIdWithCategoryHierarchy(UUID)} for KDS/dashboard flows.
     */
    @Query("SELECT DISTINCT cim FROM CategoryItemMapping cim " +
            "JOIN FETCH cim.item i " +
            "JOIN FETCH cim.menuCategoryMapping mcm " +
            "LEFT JOIN FETCH mcm.category c " +
            "LEFT JOIN FETCH c.parentCategory pc " +
            "LEFT JOIN FETCH mcm.menu m " +
            "WHERE i.id IN :itemIds")
    List<CategoryItemMapping> findByItemIdsWithCategoryHierarchy(@Param("itemIds") Collection<UUID> itemIds);
    
    /**
     * Batch-count distinct published, non-deleted menus that contain items assigned to any of the given modifier groups.
     * Returns a list of Object[] where:
     *   [0] = UUID modifierGroupId
     *   [1] = Number countOfDistinctPublishedMenus
     */
    @Query("SELECT img.modifierGroup.id, COUNT(DISTINCT mcm.menu.id) " +
           "FROM com.gulfnet.shared_library.entity.CategoryItemMapping cim " +
           "JOIN cim.menuCategoryMapping mcm " +
           "JOIN mcm.menu m " +
           "JOIN com.gulfnet.shared_library.entity.ItemModifierGroup img ON img.item.id = cim.item.id " +
           "WHERE img.modifierGroup.id IN :modifierGroupIds " +
           "AND (img.isDeleted IS NULL OR img.isDeleted = false) " +
           "AND (m.isDeleted IS NULL OR m.isDeleted = false) " +
           "AND m.status = com.gulfnet.shared_library.enums.MenuStatus.PUBLISHED " +
           "GROUP BY img.modifierGroup.id")
    List<Object[]> countMenusByModifierGroupIdsBatch(@Param("modifierGroupIds") List<UUID> modifierGroupIds);
}