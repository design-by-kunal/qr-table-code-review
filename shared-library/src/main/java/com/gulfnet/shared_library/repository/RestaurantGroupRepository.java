package com.gulfnet.shared_library.repository;

import com.gulfnet.shared_library.entity.RestaurantGroup;
import com.gulfnet.shared_library.enums.EntityStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.Optional;

public interface RestaurantGroupRepository extends JpaRepository<RestaurantGroup, UUID>, JpaSpecificationExecutor<RestaurantGroup> {
    @Query(value = "SELECT r FROM RestaurantGroup r ORDER BY r.createdAt DESC",
            countQuery = "SELECT count(r) FROM RestaurantGroup r")
    Page<RestaurantGroup> findAllSortedByCreateDate(Pageable pageable);

    // Filter methods following the same pattern as UserRepository and RestaurantRepository
    Page<RestaurantGroup> findAllByStatusAndIsDeletedFalse(EntityStatus status, Pageable pageable);
    Page<RestaurantGroup> findAllByIsDeletedFalse(Pageable pageable);

    /**
     * Searches for restaurant groups by keyword, matching against restaurant group code
     * or translation names. Only returns non-deleted groups.
     *
     * @param search   the search keyword to match against code or name (case-insensitive)
     * @param pageable pagination and sorting parameters
     * @return paginated list of restaurant groups matching the search criteria
     */
    @Query("""
    SELECT r FROM RestaurantGroup r
    WHERE r.isDeleted = false
    AND (
        LOWER(r.restaurantGroupCode) LIKE LOWER(CONCAT('%', :search, '%'))
        OR EXISTS (
            SELECT 1 FROM RestaurantGroupTranslation t 
            WHERE t.restaurantGroup = r 
            AND LOWER(t.name) LIKE LOWER(CONCAT('%', :search, '%'))
        )
    )
    """)
    Page<RestaurantGroup> searchByKeyword(@Param("search") String search, Pageable pageable);

    /**
     * Searches for restaurant groups by keyword and status, matching against restaurant
     * group code or translation names. Only returns non-deleted groups with the specified status.
     *
     * @param search   the search keyword to match against code or name (case-insensitive)
     * @param status   the entity status to filter by (ACTIVE, INACTIVE, etc.)
     * @param pageable pagination and sorting parameters
     * @return paginated list of restaurant groups matching the search and status criteria
     */
    @Query("""
    SELECT r FROM RestaurantGroup r
    WHERE r.isDeleted = false
    AND r.status = :status
    AND (
        LOWER(r.restaurantGroupCode) LIKE LOWER(CONCAT('%', :search, '%'))
        OR EXISTS (
            SELECT 1 FROM RestaurantGroupTranslation t 
            WHERE t.restaurantGroup = r 
            AND LOWER(t.name) LIKE LOWER(CONCAT('%', :search, '%'))
        )
    )
    """)
    Page<RestaurantGroup> searchByKeywordAndStatus(@Param("search") String search, @Param("status") EntityStatus status, Pageable pageable);

    // Method to check if restaurant group code exists and is not deleted
    boolean existsByRestaurantGroupCodeAndIsDeletedFalse(String restaurantGroupCode);

    // Method to check if restaurant group code exists but is deleted
    boolean existsByRestaurantGroupCodeAndIsDeletedTrue(String restaurantGroupCode);
    
    // Method to check if restaurant group code exists (including deleted records)
    @Query("SELECT COUNT(r) > 0 FROM RestaurantGroup r WHERE r.restaurantGroupCode = :restaurantGroupCode")
    boolean existsByRestaurantGroupCode(@Param("restaurantGroupCode") String restaurantGroupCode);
    
    // Method to check if restaurant group code exists excluding a specific group ID (excluding deleted records)
    @Query("SELECT COUNT(r) > 0 FROM RestaurantGroup r WHERE r.restaurantGroupCode = :restaurantGroupCode AND r.id != :excludeId AND r.isDeleted = false")
    boolean existsByRestaurantGroupCodeExcludingId(@Param("restaurantGroupCode") String restaurantGroupCode, @Param("excludeId") UUID excludeId);

    // Method to check if restaurant group code exists in a DELETED group excluding current group ID
    @Query("SELECT COUNT(r) > 0 FROM RestaurantGroup r WHERE r.restaurantGroupCode = :restaurantGroupCode AND r.id != :excludeId AND r.isDeleted = true")
    boolean existsByRestaurantGroupCodeDeletedExcludingId(@Param("restaurantGroupCode") String restaurantGroupCode, @Param("excludeId") UUID excludeId);
    
    // Method to check if restaurant group code exists (including deleted records) excluding a specific group ID
    @Query("SELECT COUNT(r) > 0 FROM RestaurantGroup r WHERE r.restaurantGroupCode = :restaurantGroupCode AND r.id != :excludeId")
    boolean existsByRestaurantGroupCodeExcludingIdIncludingDeleted(@Param("restaurantGroupCode") String restaurantGroupCode, @Param("excludeId") UUID excludeId);
    
    // Method to find restaurant group by id and is not deleted
    Optional<RestaurantGroup> findByIdAndIsDeletedFalse(UUID id);

    Optional<RestaurantGroup> findByRestaurantGroupCodeAndIsDeletedFalse(String restaurantGroupCode);

    /**
     * Finds restaurant groups with optional filtering by status and search term.
     * The search term matches against translation names. Only returns non-deleted groups.
     * Supports pagination and sorting via Pageable.
     *
     * @param status  optional status filter (ACTIVE, INACTIVE, etc.), null returns all statuses
     * @param search  optional search term to match against translation names (case-insensitive),
     *                should be in format: '%keyword%' for LIKE matching
     * @param pageable pagination and sorting parameters
     * @return paginated list of restaurant groups matching the filters
     */
    @Query("""
    SELECT DISTINCT r FROM RestaurantGroup r
    WHERE r.isDeleted = false
    AND (:status IS NULL OR r.status = :status)
    AND (
        :search IS NULL OR EXISTS (
            SELECT 1 FROM RestaurantGroupTranslation t
            WHERE t.restaurantGroup = r AND LOWER(t.name) LIKE :search
        )
    )
    """)
    Page<RestaurantGroup> findByFilters(@Param("status") EntityStatus status, @Param("search") String search, Pageable pageable);
}
