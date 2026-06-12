package com.gulfnet.shared_library.repository;

import com.gulfnet.shared_library.entity.MenuStructureTranslation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.repository.query.Param; 
import org.springframework.data.jpa.repository.Query;  
import java.util.Optional; 
import java.util.List; 
import java.util.UUID;

public interface MenuStructureTranslationRepository extends JpaRepository<MenuStructureTranslation, UUID>{
    @Query("SELECT t FROM MenuStructureTranslation t WHERE t.menuStructure.id = :menuStructureId")
    List<MenuStructureTranslation> findAllByMenuStructureIdWithLanguage(UUID menuStructureId);

    @Query("SELECT t FROM MenuStructureTranslation t WHERE t.menuStructure.id = :menuStructureId AND t.languageCode = :languageCode")
    Optional<MenuStructureTranslation> findByMenuStructureIdAndLanguageCode(
        @Param("menuStructureId") UUID menuStructureId, 
        @Param("languageCode") String languageCode
    );

    @Query("SELECT CASE WHEN COUNT(t) > 0 THEN true ELSE false END FROM MenuStructureTranslation t " +
    "WHERE LOWER(t.name) = LOWER(:name) AND t.languageCode = :languageCode " +
    "AND t.menuStructure.isDeleted = false")
    boolean existsByNameAndLanguageCode(@Param("name") String name, @Param("languageCode") String languageCode);

    @Query("SELECT t FROM MenuStructureTranslation t WHERE t.menuStructure.id = :menuStructureId")
    List<MenuStructureTranslation> findAllByMenuStructureId(@Param("menuStructureId") UUID menuStructureId);

    @Query("SELECT CASE WHEN COUNT(t) > 0 THEN true ELSE false END FROM MenuStructureTranslation t " +
           "WHERE LOWER(t.name) = LOWER(:name) AND t.languageCode = :languageCode " +
           "AND t.menuStructure.id != :menuStructureId AND t.menuStructure.isDeleted = false")
    boolean existsByNameAndLanguageCodeAndMenuStructureIdNot(
        @Param("name") String name, 
        @Param("languageCode") String languageCode, 
        @Param("menuStructureId") UUID menuStructureId
    );

    List<MenuStructureTranslation> findAllByMenuStructureIdAndLanguageCode(UUID menuStructureId, String languageCode);
}