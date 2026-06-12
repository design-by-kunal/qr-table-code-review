package com.gulfnet.shared_library.repository;

import com.gulfnet.shared_library.entity.Item;
import com.gulfnet.shared_library.enums.EntityStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.UUID;
import java.util.List;
@Repository
public interface ItemRepository extends JpaRepository<Item, UUID>, JpaSpecificationExecutor<Item> {

    /**
     * Finds all non-deleted items with optional filtering by status, modifier assignment, and search term.
     * The search term matches against item translation names in the specified locale.
     *
     * @param status             optional status filter (ACTIVE, INACTIVE, etc.), null returns all statuses
     * @param hasModifierAssigned optional filter for items with modifiers assigned, null returns all items
     * @param search             optional search term to match against item name (case-insensitive)
     * @param locale             the locale code for item translations
     * @return list of items matching the filters
     */
    @Query(value = "SELECT DISTINCT i.* FROM item i " +
    "LEFT JOIN item_translation t ON i.id = t.item_id " +
"WHERE (i.is_deleted IS NULL OR i.is_deleted = false) " + // Modified this line
    "AND (:status IS NULL OR i.status = :status) " +
    "AND (:hasModifierAssigned IS NULL OR i.has_modifier_assigned = :hasModifierAssigned) " +
    "AND (:search IS NULL OR " +
    "     t.name ILIKE '%' || CAST(:search AS text) || '%') " +
    "AND t.language_code = :locale",
    nativeQuery = true)
    List<Item> findAllActiveItemsWithFilters(
    @Param("status") String status,
    @Param("hasModifierAssigned") Boolean hasModifierAssigned,
    @Param("search") String search,
    @Param("locale") String locale);

    Page<Item> findByStatusAndIsDeletedFalse(EntityStatus status, Pageable pageable);

    Page<Item> findByIsDeletedFalse(Pageable pageable);

    @Query("SELECT COUNT(i) FROM Item i " +
    "JOIN CategoryItemMapping cim ON cim.item = i " +
    "WHERE cim.menuCategoryMapping.category.id = :categoryId " +
    "AND i.isDeleted = false")
    long countByCategoryAndIsDeletedFalse(@Param("categoryId") UUID categoryId);

    /**
     * Find items impacted by price override for MENU level
     * Items are in the specified menu AND the menu is assigned to the restaurant via RestaurantMenuMapping
     */
    @Query("SELECT DISTINCT i FROM Item i " +
           "JOIN CategoryItemMapping cim ON cim.item = i " +
           "JOIN cim.menuCategoryMapping mcm ON mcm.id = cim.menuCategoryMapping.id " +
           "JOIN RestaurantMenuMapping rmm ON rmm.id.menuId = mcm.menu.id " +
           "WHERE mcm.menu.id = :menuId " +
           "AND rmm.id.restaurantId = :restaurantId " +
           "AND (i.isDeleted IS NULL OR i.isDeleted = false)")
    List<Item> findItemsByMenuAndRestaurant(@Param("menuId") UUID menuId, @Param("restaurantId") UUID restaurantId);


    @Query("SELECT DISTINCT i FROM Item i " +
           "JOIN CategoryItemMapping cim ON cim.item = i " +
           "JOIN cim.menuCategoryMapping mcm ON mcm.id = cim.menuCategoryMapping.id " +
           "JOIN RestaurantMenuMapping rmm ON rmm.id.menuId = mcm.menu.id " +
           "WHERE mcm.category.id IN :categoryIds " +
           "AND rmm.id.restaurantId = :restaurantId " +
           "AND (i.isDeleted IS NULL OR i.isDeleted = false)")
    List<Item> findItemsByCategoriesAndRestaurant(@Param("categoryIds") List<UUID> categoryIds, @Param("restaurantId") UUID restaurantId);

    /**

     * Count all items that are not deleted
     */
    @Query("SELECT COUNT(i) FROM Item i WHERE (i.isDeleted IS NULL OR i.isDeleted = false)")
    long countByIsDeletedFalse();
    
    /**
     * Count distinct items in menus assigned to restaurants in a restaurant group
     */
    @Query("SELECT COUNT(DISTINCT i) FROM Item i " +
           "JOIN CategoryItemMapping cim ON cim.item = i " +
           "JOIN cim.menuCategoryMapping mcm ON mcm.id = cim.menuCategoryMapping.id " +
           "JOIN RestaurantMenuMapping rmm ON rmm.id.menuId = mcm.menu.id " +
           "JOIN Restaurant r ON r.id = rmm.id.restaurantId " +
           "WHERE r.restaurantGroup.id = :restaurantGroupId " +
           "AND (i.isDeleted IS NULL OR i.isDeleted = false) " +
           "AND (r.isDeleted IS NULL OR r.isDeleted = false)")
    long countByRestaurantGroupId(@Param("restaurantGroupId") UUID restaurantGroupId);
    
    /**
     * Count distinct items in menus assigned to a specific restaurant
     */
    @Query("SELECT COUNT(DISTINCT i) FROM Item i " +
           "JOIN CategoryItemMapping cim ON cim.item = i " +
           "JOIN cim.menuCategoryMapping mcm ON mcm.id = cim.menuCategoryMapping.id " +
           "JOIN RestaurantMenuMapping rmm ON rmm.id.menuId = mcm.menu.id " +
           "WHERE rmm.id.restaurantId = :restaurantId " +
           "AND (i.isDeleted IS NULL OR i.isDeleted = false)")
    long countByRestaurantId(@Param("restaurantId") UUID restaurantId);


    @Query("SELECT DISTINCT i FROM Item i " +
           "JOIN CategoryItemMapping cim ON cim.item = i " +
           "JOIN cim.menuCategoryMapping mcm " +
           "JOIN RestaurantMenuMapping rmm ON rmm.id.menuId = mcm.menu.id " +
           "WHERE mcm.id IN :menuCategoryMappingIds " +
           "AND rmm.id.restaurantId = :restaurantId " +
           "AND (i.isDeleted IS NULL OR i.isDeleted = false)")
    List<Item> findItemsByMenuCategoryMappingIdsAndRestaurant(@Param("menuCategoryMappingIds") List<UUID> menuCategoryMappingIds, @Param("restaurantId") UUID restaurantId);

    @Query("SELECT CASE WHEN COUNT(i) > 0 THEN true ELSE false END FROM Item i " +
           "WHERE i.itemCode IS NOT NULL AND LOWER(TRIM(i.itemCode)) = LOWER(TRIM(:code)) " +
           "AND (i.isDeleted IS NULL OR i.isDeleted = false)")
    boolean existsActiveItemByItemCode(@Param("code") String code);

    @Query("SELECT CASE WHEN COUNT(i) > 0 THEN true ELSE false END FROM Item i " +
           "WHERE i.itemCode IS NOT NULL AND LOWER(TRIM(i.itemCode)) = LOWER(TRIM(:code)) " +
           "AND (i.isDeleted IS NULL OR i.isDeleted = false) AND i.id <> :itemId")
    boolean existsActiveItemByItemCodeExcludingId(@Param("code") String code, @Param("itemId") UUID itemId);


}