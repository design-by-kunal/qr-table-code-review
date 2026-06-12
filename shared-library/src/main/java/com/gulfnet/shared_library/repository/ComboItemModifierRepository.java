package com.gulfnet.shared_library.repository;

import com.gulfnet.shared_library.entity.ComboItemModifier;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface ComboItemModifierRepository extends JpaRepository<ComboItemModifier, UUID> {
    
    @Query("SELECT cim FROM ComboItemModifier cim " +
           "JOIN FETCH cim.modifierItem modifierItem " +
           "LEFT JOIN FETCH modifierItem.translations modifierTranslations " +
           "WHERE cim.comboItemMapping.comboGroup.combo.comboId = :comboId")
    List<ComboItemModifier> findComboItemModifiersByComboId(@Param("comboId") UUID comboId);
    
    List<ComboItemModifier> findByComboItemMappingId(UUID comboItemMappingId);
}
