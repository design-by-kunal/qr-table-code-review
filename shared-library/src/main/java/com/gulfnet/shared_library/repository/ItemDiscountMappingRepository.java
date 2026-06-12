package com.gulfnet.shared_library.repository;

import com.gulfnet.shared_library.entity.ItemDiscountMapping;
import com.gulfnet.shared_library.entity.CategoryItemMapping;
import com.gulfnet.shared_library.entity.Discount;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface ItemDiscountMappingRepository extends JpaRepository<ItemDiscountMapping, UUID> {
    List<ItemDiscountMapping> findByCategoryItemMapping(CategoryItemMapping categoryItemMapping);
    List<ItemDiscountMapping> findByDiscount(Discount discount);
    void deleteByCategoryItemMappingAndDiscount(CategoryItemMapping categoryItemMapping, Discount discount);
    
    // Batch fetch methods for performance optimization
    List<ItemDiscountMapping> findByCategoryItemMappingIn(List<CategoryItemMapping> categoryItemMappings);
}
