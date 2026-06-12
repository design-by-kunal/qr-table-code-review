package com.gulfnet.shared_library.repository;

import com.gulfnet.shared_library.entity.ComboTranslation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ComboTranslationRepository extends JpaRepository<ComboTranslation, UUID> {
    
    List<ComboTranslation> findByComboComboId(UUID comboId);
    
    // Batch fetch translations for multiple combos
    @Query("SELECT ct FROM ComboTranslation ct WHERE ct.combo.comboId IN :comboIds")
    List<ComboTranslation> findByComboComboIdIn(@Param("comboIds") List<UUID> comboIds);
    
    void deleteByComboComboId(UUID comboId);
    
    Optional<ComboTranslation> findByComboComboIdAndLanguageCode(UUID comboId, String languageCode);
}
