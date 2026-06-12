package com.gulfnet.shared_library.repository;

import com.gulfnet.shared_library.entity.DiscountBxgyItem;
import com.gulfnet.shared_library.entity.CategoryItemMapping;
import com.gulfnet.shared_library.enums.DiscountType;
import com.gulfnet.shared_library.enums.EntityStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface DiscountBxgyItemRepository extends JpaRepository<DiscountBxgyItem, UUID> {
    List<DiscountBxgyItem> findByDiscountId(UUID discountId);
    List<DiscountBxgyItem> findByBuyItemMappingId(UUID buyItemMappingId);
    List<DiscountBxgyItem> findByGetItemMappingId(UUID getItemMappingId);
    List<DiscountBxgyItem> findByBuyItemMapping(CategoryItemMapping buyItemMapping);
    List<DiscountBxgyItem> findByGetItemMapping(CategoryItemMapping getItemMapping);
    
    /**
     * Find BXGY items where the buy_item_ids matches any of the provided CategoryItemMapping IDs
     * and the discount is assigned to the specified menu
     * CRITICAL: Also verifies that the CategoryItemMapping belongs to the specified menu
     */
    @Query("SELECT dbxi FROM DiscountBxgyItem dbxi " +
           "JOIN dbxi.discount d " +
           "JOIN MenuDiscountMapping mdm ON mdm.discount.id = d.id " +
           "JOIN dbxi.buyItemMapping bim " +
           "JOIN bim.menuCategoryMapping mcm ON mcm.id = bim.menuCategoryMapping.id " +
           "WHERE dbxi.buyItemMapping.id IN :categoryItemMappingIds " +
           "AND mdm.menu.id = :menuId " +
           "AND mcm.menu.id = :menuId " +
           "AND d.discountType = :discountType " +
           "AND d.status = :status " +
           "AND (d.isDeleted = false OR d.isDeleted IS NULL)")
    List<DiscountBxgyItem> findByBuyItemMappingIdsAndMenuId(
            @Param("categoryItemMappingIds") List<UUID> categoryItemMappingIds,
            @Param("menuId") UUID menuId,
            @Param("discountType") DiscountType discountType,
            @Param("status") EntityStatus status);
    
    /**
     * Find BXGY items where the get_item_ids matches any of the provided CategoryItemMapping IDs
     * and the discount is assigned to the specified menu
     * CRITICAL: Also verifies that the CategoryItemMapping belongs to the specified menu
     */
    @Query("SELECT dbxi FROM DiscountBxgyItem dbxi " +
           "JOIN dbxi.discount d " +
           "JOIN MenuDiscountMapping mdm ON mdm.discount.id = d.id " +
           "JOIN dbxi.getItemMapping gim " +
           "JOIN gim.menuCategoryMapping mcm ON mcm.id = gim.menuCategoryMapping.id " +
           "WHERE dbxi.getItemMapping.id IN :categoryItemMappingIds " +
           "AND mdm.menu.id = :menuId " +
           "AND mcm.menu.id = :menuId " +
           "AND d.discountType = :discountType " +
           "AND d.status = :status " +
           "AND (d.isDeleted = false OR d.isDeleted IS NULL)")
    List<DiscountBxgyItem> findByGetItemMappingIdsAndMenuId(
            @Param("categoryItemMappingIds") List<UUID> categoryItemMappingIds,
            @Param("menuId") UUID menuId,
            @Param("discountType") DiscountType discountType,
            @Param("status") EntityStatus status);
    
    /**
     * Batch fetch BXGY items for multiple discounts with all relationships
     * Optimized to avoid N+1 queries
     */
    @Query("SELECT DISTINCT bxi FROM DiscountBxgyItem bxi " +
           "LEFT JOIN FETCH bxi.buyItemMapping bim " +
           "LEFT JOIN FETCH bim.item bi " +
           "LEFT JOIN FETCH bim.menuCategoryMapping bmcm " +
           "LEFT JOIN FETCH bmcm.menu bm " +
           "LEFT JOIN FETCH bxi.getItemMapping gim " +
           "LEFT JOIN FETCH gim.item gi " +
           "LEFT JOIN FETCH gim.menuCategoryMapping gmcm " +
           "LEFT JOIN FETCH gmcm.menu gm " +
           "WHERE bxi.discount.id IN :discountIds")
    List<DiscountBxgyItem> findByDiscountIdsWithRelations(@Param("discountIds") List<UUID> discountIds);
    
    /**
     * Find BXGY items that conflict with the given Item IDs in the specified menu
     * This checks if any buy or get items (by Item ID) are already assigned to other BXGY discounts in this menu
     */
    @Query("SELECT DISTINCT bxi FROM DiscountBxgyItem bxi " +
           "JOIN bxi.discount d " +
           "JOIN MenuDiscountMapping mdm ON mdm.discount.id = d.id " +
           "LEFT JOIN bxi.buyItemMapping bim " +
           "LEFT JOIN bim.item buyItem " +
           "LEFT JOIN bxi.getItemMapping gim " +
           "LEFT JOIN gim.item getItem " +
           "WHERE mdm.menu.id = :menuId " +
           "AND d.discountType = :discountType " +
           "AND d.id != :excludeDiscountId " +
           "AND d.status = :status " +
           "AND (d.isDeleted = false OR d.isDeleted IS NULL) " +
           "AND (buyItem.id IN :itemIds OR getItem.id IN :itemIds)")
    List<DiscountBxgyItem> findConflictingBxgyItems(
            @Param("menuId") UUID menuId,
            @Param("itemIds") List<UUID> itemIds,
            @Param("discountType") DiscountType discountType,
            @Param("status") EntityStatus status,
            @Param("excludeDiscountId") UUID excludeDiscountId);
    
    /**
     * Find BXGY items where the buy item (by Item ID) matches and the discount is assigned to the specified menu
     */
    @Query("SELECT DISTINCT dbxi FROM DiscountBxgyItem dbxi " +
           "JOIN dbxi.discount d " +
           "JOIN MenuDiscountMapping mdm ON mdm.discount.id = d.id " +
           "JOIN dbxi.buyItemMapping bim " +
           "JOIN bim.item buyItem " +
           "WHERE buyItem.id = :itemId " +
           "AND mdm.menu.id = :menuId " +
           "AND d.discountType = :discountType " +
           "AND d.status = :status " +
           "AND (d.isDeleted = false OR d.isDeleted IS NULL)")
    List<DiscountBxgyItem> findByBuyItemIdAndMenuId(
            @Param("itemId") UUID itemId,
            @Param("menuId") UUID menuId,
            @Param("discountType") DiscountType discountType,
            @Param("status") EntityStatus status);
    
    /**
     * Find BXGY items where the get item (by Item ID) matches and the discount is assigned to the specified menu
     */
    @Query("SELECT DISTINCT dbxi FROM DiscountBxgyItem dbxi " +
           "JOIN dbxi.discount d " +
           "JOIN MenuDiscountMapping mdm ON mdm.discount.id = d.id " +
           "JOIN dbxi.getItemMapping gim " +
           "JOIN gim.item getItem " +
           "WHERE getItem.id = :itemId " +
           "AND mdm.menu.id = :menuId " +
           "AND d.discountType = :discountType " +
           "AND d.status = :status " +
           "AND (d.isDeleted = false OR d.isDeleted IS NULL)")
    List<DiscountBxgyItem> findByGetItemIdAndMenuId(
            @Param("itemId") UUID itemId,
            @Param("menuId") UUID menuId,
            @Param("discountType") DiscountType discountType,
            @Param("status") EntityStatus status);
} 