package com.gulfnet.shared_library.repository;

import com.gulfnet.shared_library.entity.ModifierGroupTranslation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

public interface ModifierGroupTranslationRepository extends JpaRepository<ModifierGroupTranslation, UUID> {
    
    @Modifying
    @Transactional
    void deleteByModifierGroupId(UUID modifierGroupId);

    boolean existsByNameIgnoreCaseAndLanguageCodeAndModifierGroupIsDeletedFalseAndModifierGroupIdNot(
    String name, String languageCode, UUID modifierGroupId
);


    boolean existsByNameIgnoreCaseAndLanguageCodeAndModifierGroupIsDeletedFalse(
        String name,
        String languageCode
    );

    @Modifying
    @Transactional
    void deleteAllByModifierGroup_Id(UUID modifierGroupId);
    
    // Batch loading method for N+1 query fixes
    @Query("SELECT mgt FROM ModifierGroupTranslation mgt WHERE mgt.modifierGroup.id IN :groupIds")
    List<ModifierGroupTranslation> findAllByModifierGroupIdIn(@Param("groupIds") List<UUID> groupIds);

}
