package com.gulfnet.shared_library.repository;

import com.gulfnet.shared_library.entity.CategoryTranslation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.data.repository.query.Param;
import java.util.Optional; // Add this import for Optional return type


import java.util.List;
import java.util.UUID;

public interface CategoryTranslationRepository extends JpaRepository<CategoryTranslation, UUID> {

    List<CategoryTranslation> findByCategoryId(UUID categoryId);

    @Modifying
    @Transactional
    void deleteByCategoryId(UUID categoryId);
    Optional<CategoryTranslation> findByCategoryIdAndLanguageCode(UUID categoryId, String languageCode);

    /**
     * Checks if a category translation name exists for a root category (no parent) in a specific
     * menu structure and language. Used for validation to ensure name uniqueness for root categories.
     *
     * @param name            the category translation name to check
     * @param languageCode    the language code of the translation
     * @param menuStructureId the menu structure ID to check within
     * @return true if the name exists for a root category in the specified menu structure and language, false otherwise
     */
    @Query("""
        SELECT CASE WHEN COUNT(ct) > 0 THEN true ELSE false END
        FROM CategoryTranslation ct
        WHERE ct.name = :name
          AND ct.languageCode = :languageCode
          AND ct.category.menuStructure.id = :menuStructureId
          AND ct.category.parentCategory IS NULL
          AND ct.category.isDeleted = false
    """)
    boolean existsByNameAndLanguageCodeAndCategory_MenuStructure_IdAndCategory_ParentCategoryIsNull(
        @Param("name") String name,
        @Param("languageCode") String languageCode,
        @Param("menuStructureId") UUID menuStructureId
    );

    /**
     * Checks if a category translation name exists for a subcategory (with a parent category)
     * in a specific language. Used for validation to ensure name uniqueness within a parent category.
     *
     * @param name            the category translation name to check
     * @param languageCode    the language code of the translation
     * @param parentCategoryId the parent category ID to check within
     * @return true if the name exists for a subcategory under the specified parent in the language, false otherwise
     */
    @Query("""
        SELECT CASE WHEN COUNT(ct) > 0 THEN true ELSE false END
        FROM CategoryTranslation ct
        WHERE ct.name = :name
          AND ct.languageCode = :languageCode
          AND ct.category.parentCategory.id = :parentCategoryId
          AND ct.category.isDeleted = false
    """)
    boolean existsByNameAndLanguageCodeAndCategory_ParentCategory_Id(
        @Param("name") String name,
        @Param("languageCode") String languageCode,
        @Param("parentCategoryId") UUID parentCategoryId
    );

    /**
     * Checks if a category translation name exists for another root category (excluding the
     * specified category) in a specific menu structure and language. Used for validation during
     * category updates to ensure name uniqueness while allowing updates to the same category.
     *
     * @param name            the category translation name to check
     * @param languageCode    the language code of the translation
     * @param menuStructureId the menu structure ID to check within
     * @param categoryId      the category ID to exclude from the check
     * @return true if the name exists for another root category in the specified menu structure and language, false otherwise
     */
    @Query("""
        SELECT CASE WHEN COUNT(ct) > 0 THEN true ELSE false END
        FROM CategoryTranslation ct
        WHERE ct.name = :name
          AND ct.languageCode = :languageCode
          AND ct.category.menuStructure.id = :menuStructureId
          AND ct.category.parentCategory IS NULL
          AND ct.category.id <> :categoryId
          AND ct.category.isDeleted = false
    """)
    boolean existsRootCategoryTranslationForUpdate(
        @Param("name") String name,
        @Param("languageCode") String languageCode,
        @Param("menuStructureId") UUID menuStructureId,
        @Param("categoryId") UUID categoryId
    );

    /**
     * Checks if a category translation name exists for another subcategory (excluding the
     * specified category) under a specific parent category in a specific language. Used for
     * validation during category updates to ensure name uniqueness while allowing updates to the same category.
     *
     * @param name            the category translation name to check
     * @param languageCode    the language code of the translation
     * @param parentCategoryId the parent category ID to check within
     * @param categoryId      the category ID to exclude from the check
     * @return true if the name exists for another subcategory under the specified parent in the language, false otherwise
     */
    @Query("""
        SELECT CASE WHEN COUNT(ct) > 0 THEN true ELSE false END
        FROM CategoryTranslation ct
        WHERE ct.name = :name
          AND ct.languageCode = :languageCode
          AND ct.category.parentCategory.id = :parentCategoryId
          AND ct.category.id <> :categoryId
          AND ct.category.isDeleted = false
    """)
    boolean existsSubCategoryTranslationForUpdate(
        @Param("name") String name,
        @Param("languageCode") String languageCode,
        @Param("parentCategoryId") UUID parentCategoryId,
        @Param("categoryId") UUID categoryId
    );

    @Modifying
    @Transactional
    void deleteAllByCategory_Id(UUID categoryId);

    boolean existsByCategoryIdAndLanguageCode(UUID categoryId, String languageCode);

    @Query("SELECT ct FROM CategoryTranslation ct WHERE ct.category.id IN :categoryIds AND ct.languageCode = :languageCode")
    List<CategoryTranslation> findAllByCategoryIdInAndLanguageCode(@Param("categoryIds") List<UUID> categoryIds, @Param("languageCode") String languageCode);

    @Query("SELECT ct FROM CategoryTranslation ct WHERE ct.category.id IN :categoryIds")
    List<CategoryTranslation> findAllByCategoryIdIn(@Param("categoryIds") List<UUID> categoryIds);

}

