package com.gulfnet.shared_library.repository;

import com.gulfnet.shared_library.entity.TemplateSectionTranslation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.UUID;

public interface TemplateSectionTranslationRepository extends JpaRepository<TemplateSectionTranslation, UUID> {



       @Query("SELECT CASE WHEN COUNT(tst) > 0 THEN true ELSE false END " +
       "FROM TemplateSectionTranslation tst " +
       "WHERE LOWER(tst.name) = LOWER(:name) " +
       "AND tst.languageCode = :languageCode " +
       "AND tst.templateSection.layoutTemplate.id = :templateLayoutId " +
       "AND tst.templateSection.isDeleted = false")
       boolean existsByNameLanguageAndLayout(@Param("name") String name,
                                          @Param("languageCode") String languageCode,
                                          @Param("templateLayoutId") UUID templateLayoutId);

       @Query("SELECT CASE WHEN COUNT(tst) > 0 THEN true ELSE false END " +
              "FROM TemplateSectionTranslation tst " +
              "WHERE LOWER(tst.name) = LOWER(:name) " +
              "AND tst.languageCode = :languageCode " +
              "AND tst.templateSection.layoutTemplate.id = :templateLayoutId " +
              "AND tst.templateSection.id <> :sectionId " +
              "AND tst.templateSection.isDeleted = false")
       boolean existsByNameLanguageAndLayoutAndSectionNot(@Param("name") String name,
                                                          @Param("languageCode") String languageCode,
                                                          @Param("templateLayoutId") UUID templateLayoutId,
                                                          @Param("sectionId") UUID sectionId);

}

        


