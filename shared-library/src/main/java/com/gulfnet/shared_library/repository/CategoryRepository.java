package com.gulfnet.shared_library.repository;

import com.gulfnet.shared_library.entity.Category;
import com.gulfnet.shared_library.enums.EntityStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;


import java.util.List;
import java.util.Optional;
import java.util.UUID;
import com.gulfnet.shared_library.entity.MenuStructure;

public interface CategoryRepository extends JpaRepository<Category, UUID> {

    // Ordered methods (preferred)
    List<Category> findByParentCategoryIdAndIsDeletedFalseOrderByDisplayOrderAsc(UUID parentCategoryId);
    List<Category> findByParentCategoryIdOrderByDisplayOrderAsc(UUID parentCategoryId);
    List<Category> findByIsDeletedFalseOrderByDisplayOrderAsc();
    List<Category> findByMenuStructureIdAndIsDeletedFalseOrderByDisplayOrderAsc(UUID menuStructureId);
    List<Category> findByMenuStructureAndIsDeletedFalseAndStatusOrderByDisplayOrderAsc(MenuStructure menuStructure, EntityStatus status);
    
    // Legacy methods for backward compatibility
    List<Category> findByParentCategoryIdAndIsDeletedFalse(UUID parentCategoryId);
    List<Category> findByParentCategoryId(UUID parentCategoryId);
    List<Category> findByIsDeletedFalse();
    List<Category> findByMenuStructureIdAndIsDeletedFalse(UUID menuStructureId);
    List<Category> findByMenuStructureAndIsDeletedFalseAndStatus(MenuStructure menuStructure, EntityStatus status);

    boolean existsByIdAndIsDeletedFalse(UUID id);
    Optional<Category> findByIdAndIsDeletedFalse(UUID id);

    @Query("SELECT COUNT(c) > 0 FROM Category c WHERE c.parentCategory.id = :parentCategoryId AND c.isDeleted = false")
    boolean existsByParentCategoryIdAndIsDeletedFalse(@Param("parentCategoryId") UUID parentCategoryId);

    @Query("SELECT CASE WHEN COUNT(c) > 0 THEN true ELSE false END FROM Category c WHERE c.parentCategory.id = :categoryId")
    boolean existsByParentCategoryId(@Param("categoryId") UUID categoryId);

    // Pagination and filtering methods
    Page<Category> findByIsDeletedFalse(Pageable pageable);
    
    @Query("SELECT c FROM Category c WHERE c.isDeleted = false AND " +
           "(:status IS NULL OR c.status = :status) AND " +
           "(:menuStructureId IS NULL OR c.menuStructure.id = :menuStructureId) AND " +
           "((:parentCategoryId IS NULL AND c.parentCategory IS NULL) OR (:parentCategoryId IS NOT NULL AND c.parentCategory.id = :parentCategoryId)) " +
           "ORDER BY c.displayOrder ASC, c.createdAt ASC")
    Page<Category> findByStatusAndMenuStructureAndParentCategory(
        @Param("status") EntityStatus status,
        @Param("menuStructureId") UUID menuStructureId,
        @Param("parentCategoryId") UUID parentCategoryId,
        Pageable pageable
    );

    /**
     * Finds categories with optional filtering by status, menu structure, parent category,
     * and search term. The search term matches against category translation names.
     * Results are ordered by display order and creation date. Only returns non-deleted categories.
     *
     * @param status          optional status filter (ACTIVE, INACTIVE, etc.), null returns all statuses
     * @param menuStructureId optional menu structure ID filter, null returns all menu structures
     * @param parentCategoryId optional parent category ID filter, null returns root categories (no parent)
     * @param search          optional search term to match against category translation names (case-insensitive)
     * @param pageable        pagination and sorting parameters
     * @return paginated list of categories matching the filters, ordered by displayOrder and createdAt
     */
    @Query("SELECT c FROM Category c WHERE c.isDeleted = false AND " +
           "(:status IS NULL OR c.status = :status) AND " +
           "(:menuStructureId IS NULL OR c.menuStructure.id = :menuStructureId) AND " +
           "((:parentCategoryId IS NULL AND c.parentCategory IS NULL) OR (:parentCategoryId IS NOT NULL AND c.parentCategory.id = :parentCategoryId)) AND " +
           "(:search IS NULL OR EXISTS (SELECT ct FROM CategoryTranslation ct WHERE ct.category = c AND LOWER(ct.name) LIKE LOWER(CONCAT('%', :search, '%')))) " +
           "ORDER BY c.displayOrder ASC, c.createdAt ASC")
    Page<Category> findByStatusAndMenuStructureAndParentCategoryWithSearch(
        @Param("status") EntityStatus status,
        @Param("menuStructureId") UUID menuStructureId,
        @Param("parentCategoryId") UUID parentCategoryId,
        @Param("search") String search,
        Pageable pageable
    );

    @Query("SELECT COUNT(c) FROM Category c WHERE c.parentCategory.id = :categoryId AND c.isDeleted = false")
    long countSubcategoriesByCategoryId(@Param("categoryId") UUID categoryId);

    // Add this method to CategoryRepository
    List<Category> findByParentCategoryAndIsDeletedFalseOrderByDisplayOrderAsc(Category parentCategory);
    
    // Legacy method for backward compatibility
    List<Category> findByParentCategoryAndIsDeletedFalse(Category parentCategory);

    // Uniqueness checks for displayOrder (create)
    boolean existsByMenuStructure_IdAndParentCategoryIsNullAndDisplayOrderAndIsDeletedFalse(UUID menuStructureId, Integer displayOrder);

    boolean existsByMenuStructure_IdAndParentCategory_IdAndDisplayOrderAndIsDeletedFalse(UUID menuStructureId, UUID parentCategoryId, Integer displayOrder);

    // Uniqueness checks for displayOrder (update) excluding current record
    @Query("SELECT CASE WHEN COUNT(c) > 0 THEN true ELSE false END FROM Category c " +
           "WHERE c.isDeleted = false AND c.menuStructure.id = :menuStructureId " +
           "AND c.parentCategory IS NULL AND c.displayOrder = :displayOrder AND c.id <> :excludeId")
    boolean existsRootDisplayOrderConflictExcludingId(@Param("menuStructureId") UUID menuStructureId,
                                                     @Param("displayOrder") Integer displayOrder,
                                                     @Param("excludeId") UUID excludeId);

    @Query("SELECT CASE WHEN COUNT(c) > 0 THEN true ELSE false END FROM Category c " +
           "WHERE c.isDeleted = false AND c.menuStructure.id = :menuStructureId " +
           "AND c.parentCategory.id = :parentCategoryId AND c.displayOrder = :displayOrder AND c.id <> :excludeId")
    boolean existsSubDisplayOrderConflictExcludingId(@Param("menuStructureId") UUID menuStructureId,
                                                    @Param("parentCategoryId") UUID parentCategoryId,
                                                    @Param("displayOrder") Integer displayOrder,
                                                    @Param("excludeId") UUID excludeId);
}
