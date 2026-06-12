package com.gulfnet.shared_library.repository;

import com.gulfnet.shared_library.entity.MenuCategoryComboMapping;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface MenuCategoryComboMappingRepository extends JpaRepository<MenuCategoryComboMapping, UUID> {
    
    // Find mapping by combo ID
    List<MenuCategoryComboMapping> findByCombo_ComboId(UUID comboId);
    
    // Find mapping by menu category mapping ID
    List<MenuCategoryComboMapping> findByMenuCategoryMapping_Id(UUID menuCategoryMappingId);
    
    // Find mapping by combo ID and menu category mapping ID
    Optional<MenuCategoryComboMapping> findByCombo_ComboIdAndMenuCategoryMapping_Id(UUID comboId, UUID menuCategoryMappingId);
    
    // Find mapping by combo ID and menu ID (through menu category mapping)
    @Query("SELECT mccm FROM MenuCategoryComboMapping mccm " +
           "WHERE mccm.combo.comboId = :comboId " +
           "AND mccm.menuCategoryMapping.menu.id = :menuId")
    List<MenuCategoryComboMapping> findByCombo_ComboIdAndMenuCategoryMapping_Menu_Id(UUID comboId, UUID menuId);
    
    // Delete mappings by combo ID
    void deleteByCombo_ComboId(UUID comboId);
}

