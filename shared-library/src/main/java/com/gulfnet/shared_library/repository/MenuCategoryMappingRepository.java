package com.gulfnet.shared_library.repository;

import com.gulfnet.shared_library.entity.MenuCategoryMapping;
import com.gulfnet.shared_library.enums.EntityStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.transaction.annotation.Transactional;
import java.util.UUID;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.Collection;


@Repository
public interface MenuCategoryMappingRepository extends JpaRepository<MenuCategoryMapping, UUID> {
    // Check if mapping exists before saving
    @Query("select (count(m)>0) from MenuCategoryMapping m where m.menu.id = :menuId and m.category.id = :categoryId")
    boolean existsByMenuIdAndCategoryId(@Param("menuId") UUID menuId, @Param("categoryId") UUID categoryId);

    @Modifying
    @Transactional
    @Query("delete from MenuCategoryMapping m where m.menu.id = :menuId")
    void deleteByMenuId(@Param("menuId") UUID menuId);

    // Find mapping by menu ID and category ID
    @Query("select m from MenuCategoryMapping m where m.menu.id = :menuId and m.category.id = :categoryId")
    Optional<MenuCategoryMapping> findByMenuIdAndCategoryId(@Param("menuId") UUID menuId, @Param("categoryId") UUID categoryId);

    @Query("select m from MenuCategoryMapping m where m.menu.id = :menuId")
    List<MenuCategoryMapping> findByMenuId(@Param("menuId") UUID menuId);

    // Find mappings by category ID
    @Query("select m from MenuCategoryMapping m where m.category.id = :categoryId")
    List<MenuCategoryMapping> findByCategoryId(@Param("categoryId") UUID categoryId);

    @Query("select m from MenuCategoryMapping m where m.category.id in :categoryIds")
    List<MenuCategoryMapping> findByCategory_IdIn(@Param("categoryIds") List<UUID> categoryIds);
    
     // Find mappings by parent category ID (for reference when querying subcategory items)
     @Query("select m from MenuCategoryMapping m where m.parentCategory.id = :parentCategoryId")
     List<MenuCategoryMapping> findByParentCategoryId(@Param("parentCategoryId") UUID parentCategoryId);

     // Find mappings by menu ID and parent category ID (for reference when querying subcategory items)
     @Query("select m from MenuCategoryMapping m where m.menu.id = :menuId and m.parentCategory.id = :parentCategoryId")
     List<MenuCategoryMapping> findByMenuIdAndParentCategoryId(@Param("menuId") UUID menuId, @Param("parentCategoryId") UUID parentCategoryId);

     // Efficient existence check across multiple categories for published, not-deleted menus
     @Query("select (count(mcm) > 0) from MenuCategoryMapping mcm where mcm.category.id in :categoryIds and mcm.menu.status = com.gulfnet.shared_library.enums.MenuStatus.PUBLISHED and (mcm.menu.isDeleted is null or mcm.menu.isDeleted = false)")
     boolean existsPublishedMenuForCategories(@Param("categoryIds") Collection<UUID> categoryIds);

     // Find menu category mappings that can be used to reference subcategory items
     // This helps avoid issues with inactive subcategory mappings by using parent category reference
     @Query("select mcm from MenuCategoryMapping mcm where mcm.menu.id = :menuId and mcm.parentCategory.id = :parentCategoryId")
     List<MenuCategoryMapping> findMenuMappingsForSubcategoryItems(@Param("menuId") UUID menuId, @Param("parentCategoryId") UUID parentCategoryId);

     // Find mappings by menu ID and category IDs - ensures items are only from the specific menu
     @Query("select m from MenuCategoryMapping m where m.menu.id = :menuId and m.category.id in :categoryIds")
     List<MenuCategoryMapping> findByMenuIdAndCategory_IdIn(@Param("menuId") UUID menuId, @Param("categoryIds") List<UUID> categoryIds);

     // Get category_id and parent_category_id directly from database (avoids lazy loading)
     @Query(value = "SELECT category_id, parent_category_id FROM menu_category_mapping WHERE id = :id", nativeQuery = true)
     java.util.Optional<Object[]> findCategoryIdsById(@Param("id") UUID id);
     
     // Get category_id and parent_category_id for multiple menu category mappings
     @Query(value = "SELECT id, category_id, parent_category_id FROM menu_category_mapping WHERE id IN :ids", nativeQuery = true)
     List<Object[]> findCategoryIdsByIds(@Param("ids") List<UUID> ids);
     
     // ✅ NEW: Check if categories exist in ANY menu (regardless of menu status)
     @Query("select (count(mcm) > 0) from MenuCategoryMapping mcm where mcm.category.id in :categoryIds and (mcm.menu.isDeleted is null or mcm.menu.isDeleted = false)")
     boolean existsByCategory_IdIn(@Param("categoryIds") Collection<UUID> categoryIds);

     // Find active combo categories for a menu
     @Query("select mcm from MenuCategoryMapping mcm where mcm.menu.id = :menuId and mcm.status = :status and mcm.category.isCombo = true and mcm.category.isDeleted = false")
     List<MenuCategoryMapping> findByMenuIdAndStatusAndCategoryIsComboTrue(@Param("menuId") UUID menuId, @Param("status") EntityStatus status);

}