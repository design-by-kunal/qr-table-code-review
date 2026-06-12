package com.gulfnet.shared_library.repository;

import com.gulfnet.shared_library.entity.TemplateTable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface TemplateTableRepository extends JpaRepository<TemplateTable, UUID> {

    /**
     * DB-level check: does a table code already exist within a given template layout (case-insensitive)?
     * Includes soft-deleted tables as well (since deleted tables can be restored).
     *
     * @param templateLayoutId template layout id
     * @param tableCode        requested table code
     * @param excludeTableId   optional table id to exclude (when updating/moving an existing table)
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
     * DB-level check: does a deleted table with the given code exist within a given template layout (case-insensitive)?
     * Used to provide a specific error message when user tries to use a code from a deleted table.
     *
     * @param templateLayoutId template layout id
     * @param tableCode        requested table code
     * @param excludeTableId   optional table id to exclude (when updating/moving an existing table)
     * @return true if a deleted table with this code exists, false otherwise
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
}

