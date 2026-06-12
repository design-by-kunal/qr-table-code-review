package com.gulfnet.shared_library.repository;

import com.gulfnet.shared_library.entity.TemplateLayout;
import com.gulfnet.shared_library.enums.EntityStatus;

import jakarta.transaction.Transactional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface TemplateLayoutRepository extends JpaRepository<TemplateLayout, UUID>, JpaSpecificationExecutor<TemplateLayout> {
    
    @Query("SELECT CASE WHEN COUNT(t) > 0 THEN true ELSE false END FROM TemplateLayout t " +
           "JOIN t.translations tr " +
           "WHERE LOWER(tr.name) = LOWER(:name) AND tr.languageCode = :languageCode AND t.isDeleted = false")
    boolean existsByTranslations_NameAndTranslations_LanguageCodeAndIsDeletedFalse(
            @Param("name") String name, 
            @Param("languageCode") String languageCode);
            
    Optional<TemplateLayout> findByIdAndIsDeletedFalse(UUID id);
    
    Page<TemplateLayout> findByIsDeletedFalse(Pageable pageable);


    @Query("SELECT DISTINCT tl FROM TemplateLayout tl " +
           "LEFT JOIN FETCH tl.translations tr " +
           "LEFT JOIN FETCH tl.createdBy " +
           "WHERE tl.isDeleted = false " +
           "AND (:status IS NULL OR tl.status = :status) " +
           "AND (:languageCode IS NULL OR tr.languageCode = :languageCode) " +
           "AND (:search IS NULL OR LOWER(tr.name) LIKE LOWER(CONCAT('%', :search, '%')))")
        List<TemplateLayout> findByStatusAndLanguageCodeAndSearch(
                @Param("status") EntityStatus status,
                @Param("languageCode") String languageCode,
                @Param("search") String search);

    @Query("SELECT DISTINCT tl FROM TemplateLayout tl " +
           "LEFT JOIN FETCH tl.translations tr " +
           "LEFT JOIN FETCH tl.createdBy " +
           "WHERE tl.isDeleted = false " +
           "AND (:status IS NULL OR tl.status = :status) " +
           "AND (:languageCode IS NULL OR tr.languageCode = :languageCode)")
        List<TemplateLayout> findByStatus(
                @Param("status") EntityStatus status,
                @Param("languageCode") String languageCode);

    @Query(value = "SELECT tl.id, " +
           "COALESCE(SUM(CASE WHEN ts.is_deleted = false AND trw.is_deleted = false AND tt.is_deleted = false THEN COALESCE(tt.capacity, 0) ELSE 0 END), 0) as totalSeatingCapacity, " +
           "COUNT(DISTINCT CASE WHEN ts.is_deleted = false THEN ts.id END) as sectionCount " +
           "FROM template_layout tl " +
           "LEFT JOIN template_section ts ON ts.template_layout_id = tl.id " +
           "LEFT JOIN template_row trw ON trw.template_section_id = ts.id " +
           "LEFT JOIN template_table tt ON tt.template_row_id = trw.id " +
           "WHERE tl.id IN :layoutIds " +
           "GROUP BY tl.id",
           nativeQuery = true)
    List<Object[]> findCapacityAndSectionCountByLayoutIds(@Param("layoutIds") List<UUID> layoutIds);

    @Query("SELECT CASE WHEN COUNT(t) > 0 THEN true ELSE false END FROM TemplateLayout t " +
           "JOIN t.translations tr " +
           "WHERE LOWER(tr.name) = LOWER(:name) AND tr.languageCode = :languageCode AND t.isDeleted = false AND t.id <> :excludeId")
        boolean existsByTranslations_NameAndTranslations_LanguageCodeAndIsDeletedFalseAndIdNot(
                @Param("name") String name,
                @Param("languageCode") String languageCode,
                @Param("excludeId") UUID excludeId);


    @Modifying
    @Transactional
    @Query("DELETE FROM TemplateLayoutTranslation tr WHERE tr.template.id = :templateId")
    void deleteTranslationsByTemplateId(@Param("templateId") UUID templateId);

    /**
     * Check if a table code exists in the template layout (case-insensitive).
     * Includes both active and soft-deleted tables to prevent reuse (since tables can be restored).
     * 
     * @param templateLayoutId The template layout ID to check within
     * @param tableCode The table code to check (will be normalized to lowercase)
     * @param excludeTableId Optional table ID to exclude from the check (for updates)
     * @return true if the table code exists, false otherwise
     */
    @Query("SELECT CASE WHEN COUNT(tt) > 0 THEN true ELSE false END FROM TemplateTable tt " +
           "JOIN tt.templateRow tr " +
           "JOIN tr.templateSection ts " +
           "JOIN ts.layoutTemplate tl " +
           "WHERE tl.id = :templateLayoutId " +
           "AND LOWER(tt.tableCode) = LOWER(:tableCode) " +
           "AND (:excludeTableId IS NULL OR tt.id <> :excludeTableId)")
    boolean existsTableCodeInTemplateLayout(
            @Param("templateLayoutId") UUID templateLayoutId,
            @Param("tableCode") String tableCode,
            @Param("excludeTableId") UUID excludeTableId);

    /**
     * Check if a table code exists in a deleted template table within the template layout (case-insensitive).
     * Used to provide a specific message telling the user to restore the deleted table instead of reusing the code.
     *
     * @param templateLayoutId The template layout ID to check within
     * @param tableCode The table code to check (case-insensitive)
     * @param excludeTableId Optional table ID to exclude from the check (for updates)
     * @return true if the table code exists in a deleted table, false otherwise
     */
    @Query("SELECT CASE WHEN COUNT(tt) > 0 THEN true ELSE false END FROM TemplateTable tt " +
           "JOIN tt.templateRow tr " +
           "JOIN tr.templateSection ts " +
           "JOIN ts.layoutTemplate tl " +
           "WHERE tl.id = :templateLayoutId " +
           "AND LOWER(tt.tableCode) = LOWER(:tableCode) " +
           "AND (tt.isDeleted = true OR tt.isDeleted IS NULL) " +
           "AND (:excludeTableId IS NULL OR tt.id <> :excludeTableId)")
    boolean existsDeletedTableCodeInTemplateLayout(
            @Param("templateLayoutId") UUID templateLayoutId,
            @Param("tableCode") String tableCode,
            @Param("excludeTableId") UUID excludeTableId);

    /**
     * Find all table codes (normalized to lowercase) that exist in the template layout.
     * Includes both active and soft-deleted tables.
     * Used for batch validation to avoid multiple database queries.
     * 
     * @param templateLayoutId The template layout ID
     * @param excludeTableIds Optional list of table IDs to exclude from the check (for updates)
     * @return List of normalized (lowercase) table codes
     */
    @Query("SELECT LOWER(tt.tableCode) FROM TemplateTable tt " +
           "JOIN tt.templateRow tr " +
           "JOIN tr.templateSection ts " +
           "JOIN ts.layoutTemplate tl " +
           "WHERE tl.id = :templateLayoutId " +
           "AND tt.tableCode IS NOT NULL " +
           "AND (:excludeTableIds IS NULL OR tt.id NOT IN :excludeTableIds)")
    List<String> findAllTableCodesInTemplateLayout(
            @Param("templateLayoutId") UUID templateLayoutId,
            @Param("excludeTableIds") List<UUID> excludeTableIds);



}
