package com.gulfnet.shared_library.repository;

import com.gulfnet.shared_library.entity.ModifierItemTranslation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;  
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ModifierItemTranslationRepository extends JpaRepository<ModifierItemTranslation, UUID> {
    
    List<ModifierItemTranslation> findAllByModifierItem_Id(UUID modifierItemId);
    List<ModifierItemTranslation> findAllByModifierItem_IdIn(List<UUID> modifierItemIds);
    
    Optional<ModifierItemTranslation> findByModifierItem_IdAndLanguageCode(UUID modifierItemId, String languageCode);
    
    boolean existsByNameAndLanguageCodeAndModifierItem_ModifierGroup_Id(String name, String languageCode, UUID modifierGroupId);
    
    boolean existsByNameAndLanguageCodeAndModifierItem_ModifierGroup_IdAndModifierItem_IdNot(
            String name, String languageCode, UUID modifierGroupId, UUID modifierItemId);
    
    @Modifying
    @Transactional
    void deleteAllByModifierItem_Id(UUID modifierItemId);
    
}