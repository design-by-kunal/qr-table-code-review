package com.gulfnet.shared_library.repository;

import com.gulfnet.shared_library.entity.ComboItemMapping;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface ComboItemMappingRepository extends JpaRepository<ComboItemMapping, UUID> {
    
    List<ComboItemMapping> findByComboGroupComboGroupId(UUID comboGroupId);
    
    void deleteByComboGroupComboGroupId(UUID comboGroupId);
    
    List<ComboItemMapping> findByComboGroupComboComboId(UUID comboId);
}
