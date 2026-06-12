package com.gulfnet.shared_library.repository;

import com.gulfnet.shared_library.entity.PromotionMenuComboMapping;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PromotionMenuComboMappingRepository extends JpaRepository<PromotionMenuComboMapping, UUID> {
    
    // Simple finder by promotion ID (used in listing/lookup scenarios)
    List<PromotionMenuComboMapping> findByPromotion_Id(UUID promotionId);
    
    // Optimized query to fetch mapping with combo and menu in one go
    // Note: Using DISTINCT to avoid duplicate results from multiple joins
    @Query("SELECT DISTINCT pmcm FROM PromotionMenuComboMapping pmcm " +
           "JOIN FETCH pmcm.menuCategoryComboMapping mccm " +
           "JOIN FETCH mccm.combo c " +
           "JOIN FETCH mccm.menuCategoryMapping mcm " +
           "JOIN FETCH mcm.menu m " +
           "WHERE pmcm.promotion.id = :promotionId " +
           "AND (:menuId IS NULL OR mcm.menu.id = :menuId)")
    List<PromotionMenuComboMapping> findByPromotionIdWithComboAndMenu(@Param("promotionId") UUID promotionId, @Param("menuId") UUID menuId);
    
    // Find mappings by menu category combo mapping ID
    List<PromotionMenuComboMapping> findByMenuCategoryComboMapping_Id(UUID menuCategoryComboMappingId);
    
    // Find mapping by promotion ID and menu category combo mapping ID
    List<PromotionMenuComboMapping> findByPromotion_IdAndMenuCategoryComboMapping_Id(UUID promotionId, UUID menuCategoryComboMappingId);
    
    // Delete mappings by promotion ID
    void deleteByPromotion_Id(UUID promotionId);
    
    // Optimized query to find combo mapping by promotion ID and menu ID directly
    @Query("SELECT pmcm FROM PromotionMenuComboMapping pmcm " +
           "JOIN FETCH pmcm.menuCategoryComboMapping mccm " +
           "JOIN FETCH mccm.menuCategoryMapping mcm " +
           "WHERE pmcm.promotion.id = :promotionId " +
           "AND mcm.menu.id = :menuId")
    Optional<PromotionMenuComboMapping> findByPromotionIdAndMenuId(@Param("promotionId") UUID promotionId, @Param("menuId") UUID menuId);
    
    // Delete mapping by promotion ID and menu ID
    @Modifying
    @Query("DELETE FROM PromotionMenuComboMapping pmcm " +
           "WHERE pmcm.promotion.id = :promotionId " +
           "AND EXISTS (SELECT 1 FROM MenuCategoryComboMapping mccm " +
           "            JOIN mccm.menuCategoryMapping mcm " +
           "            WHERE mccm.id = pmcm.menuCategoryComboMapping.id " +
           "            AND mcm.menu.id = :menuId)")
    void deleteByPromotionIdAndMenuId(@Param("promotionId") UUID promotionId, @Param("menuId") UUID menuId);
}
