package com.gulfnet.shared_library.repository;

import com.gulfnet.shared_library.entity.CategoryDiscountMapping;
import com.gulfnet.shared_library.entity.Discount;
import com.gulfnet.shared_library.entity.MenuCategoryMapping;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.UUID;

public interface CategoryDiscountMappingRepository extends JpaRepository<CategoryDiscountMapping, UUID> {
    List<CategoryDiscountMapping> findByDiscount(Discount discount);

    // Methods using menu_category_mapping_id
    void deleteByMenuCategoryMappingAndDiscount(MenuCategoryMapping menuCategoryMapping, Discount discount);
    List<CategoryDiscountMapping> findByMenuCategoryMapping(MenuCategoryMapping menuCategoryMapping);
    
    // Batch fetch method for performance optimization
    @Query("SELECT cdm FROM CategoryDiscountMapping cdm WHERE cdm.menuCategoryMapping.id IN :menuCategoryMappingIds AND cdm.discount.id = :discountId")
    List<CategoryDiscountMapping> findByMenuCategoryMappingIdsAndDiscountId(
            @Param("menuCategoryMappingIds") List<UUID> menuCategoryMappingIds,
            @Param("discountId") UUID discountId);
}
