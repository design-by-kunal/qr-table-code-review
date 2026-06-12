package com.gulfnet.shared_library.repository;

import com.gulfnet.shared_library.entity.ComboGroupTranslation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ComboGroupTranslationRepository extends JpaRepository<ComboGroupTranslation, UUID> {
    
    List<ComboGroupTranslation> findByComboGroupComboGroupId(UUID comboGroupId);
    
    void deleteByComboGroupComboGroupId(UUID comboGroupId);
    
    Optional<ComboGroupTranslation> findByComboGroupComboGroupIdAndLanguageCode(UUID comboGroupId, String languageCode);
}
