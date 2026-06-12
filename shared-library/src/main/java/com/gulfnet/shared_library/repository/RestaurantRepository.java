package com.gulfnet.shared_library.repository;

import com.gulfnet.shared_library.entity.Restaurant;
import com.gulfnet.shared_library.enums.EntityStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import java.util.UUID;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import java.util.Optional;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RestaurantRepository extends JpaRepository<Restaurant, UUID>, JpaSpecificationExecutor<Restaurant> {
    List<Restaurant> findByRestaurantGroupIdAndIsDeletedFalseAndStatus(UUID restaurantGroupId, EntityStatus status);
    List<Restaurant> findByRestaurantGroup_IdAndIsDeletedFalseAndStatus(UUID restaurantGroupId, EntityStatus status);
    List<Restaurant> findByRestaurantGroup_Id(UUID restaurantGroupId);

    Page<Restaurant> findByRestaurantGroupId(UUID groupId, Pageable pageable);
    List<Restaurant> findByRestaurantGroupId(UUID groupId);
    long countByRestaurantGroupId(UUID restaurantGroupId);
    Page<Restaurant> findAllByIsDeletedFalse(Pageable pageable);

    @Query("SELECT r.id FROM Restaurant r WHERE r.isDeleted = false")
    Page<UUID> findAllActiveIds(Pageable pageable);
    Optional<Restaurant> findByIdAndIsDeletedFalse(UUID id);
    List<Restaurant> findByRestaurantGroupIdAndIsDeletedFalse(UUID groupId);
    @Query("SELECT r FROM Restaurant r WHERE r.isDeleted = false ORDER BY r.createdAt DESC")
    Page<Restaurant> findAllActiveSortedByCreateDate(Pageable pageable);
    @Query("SELECT r FROM Restaurant r WHERE r.isDeleted = false ORDER BY r.createdAt DESC")
    Page<Restaurant> findAllSortedByCreateDate(Pageable pageable);

    // Filter methods following the same pattern as UserRepository
    Page<Restaurant> findAllByRestaurantGroupIdAndStatusAndIsDeletedFalse(UUID restaurantGroupId, EntityStatus status, Pageable pageable);
    Page<Restaurant> findAllByStatusAndIsDeletedFalse(EntityStatus status, Pageable pageable);
    Page<Restaurant> findAllByRestaurantGroupIdAndIsDeletedFalse(UUID restaurantGroupId, Pageable pageable);

    /**
     * Searches for restaurants by keyword, matching against restaurant code, location fields
     * (city, area, state, address), restaurant group name, or translation names.
     * Only returns non-deleted restaurants.
     *
     * @param search   the search keyword to match against multiple fields (case-insensitive)
     * @param pageable pagination and sorting parameters
     * @return paginated list of restaurants matching the search criteria
     */
    @Query("""
    SELECT r FROM Restaurant r
    WHERE r.isDeleted = false
    AND (
        LOWER(r.restaurantCode) LIKE LOWER(CONCAT('%', :search, '%'))
        OR LOWER(r.city) LIKE LOWER(CONCAT('%', :search, '%'))
        OR LOWER(r.area) LIKE LOWER(CONCAT('%', :search, '%'))
        OR LOWER(r.state) LIKE LOWER(CONCAT('%', :search, '%'))
        OR LOWER(r.address1) LIKE LOWER(CONCAT('%', :search, '%'))
        OR LOWER(r.address2) LIKE LOWER(CONCAT('%', :search, '%'))
        OR LOWER(r.restaurantGroupName) LIKE LOWER(CONCAT('%', :search, '%'))
        OR EXISTS (
            SELECT 1 FROM RestaurantTranslation t 
            WHERE t.restaurant = r 
            AND LOWER(t.name) LIKE LOWER(CONCAT('%', :search, '%'))
        )
    )
    """)
    Page<Restaurant> searchByKeyword(@Param("search") String search, Pageable pageable);

    /**
     * Searches for restaurants by keyword and status, matching against restaurant code,
     * location fields, restaurant group name, or translation names. Only returns
     * non-deleted restaurants with the specified status.
     *
     * @param search   the search keyword to match against multiple fields (case-insensitive)
     * @param status   the entity status to filter by (ACTIVE, INACTIVE, etc.)
     * @param pageable pagination and sorting parameters
     * @return paginated list of restaurants matching the search and status criteria
     */
    @Query("""
    SELECT r FROM Restaurant r
    WHERE r.isDeleted = false
    AND r.status = :status
    AND (
        LOWER(r.restaurantCode) LIKE LOWER(CONCAT('%', :search, '%'))
        OR LOWER(r.city) LIKE LOWER(CONCAT('%', :search, '%'))
        OR LOWER(r.area) LIKE LOWER(CONCAT('%', :search, '%'))
        OR LOWER(r.state) LIKE LOWER(CONCAT('%', :search, '%'))
        OR LOWER(r.address1) LIKE LOWER(CONCAT('%', :search, '%'))
        OR LOWER(r.address2) LIKE LOWER(CONCAT('%', :search, '%'))
        OR LOWER(r.restaurantGroupName) LIKE LOWER(CONCAT('%', :search, '%'))
        OR EXISTS (
            SELECT 1 FROM RestaurantTranslation t 
            WHERE t.restaurant = r 
            AND LOWER(t.name) LIKE LOWER(CONCAT('%', :search, '%'))
        )
    )
    """)
    
    Page<Restaurant> searchByKeywordAndStatus(@Param("search") String search, @Param("status") EntityStatus status, Pageable pageable);

    /**
     * Searches for restaurants by keyword within a specific restaurant group, matching
     * against restaurant code, location fields, restaurant group name, or translation names.
     * Only returns non-deleted restaurants.
     *
     * @param search           the search keyword to match against multiple fields (case-insensitive)
     * @param restaurantGroupId the UUID of the restaurant group to filter by
     * @param pageable         pagination and sorting parameters
     * @return paginated list of restaurants in the specified group matching the search criteria
     */
    @Query("""
    SELECT r FROM Restaurant r
    WHERE r.isDeleted = false
    AND r.restaurantGroup.id = :restaurantGroupId
    AND (
        LOWER(r.restaurantCode) LIKE LOWER(CONCAT('%', :search, '%'))
        OR LOWER(r.city) LIKE LOWER(CONCAT('%', :search, '%'))
        OR LOWER(r.area) LIKE LOWER(CONCAT('%', :search, '%'))
        OR LOWER(r.state) LIKE LOWER(CONCAT('%', :search, '%'))
        OR LOWER(r.address1) LIKE LOWER(CONCAT('%', :search, '%'))
        OR LOWER(r.address2) LIKE LOWER(CONCAT('%', :search, '%'))
        OR LOWER(r.restaurantGroupName) LIKE LOWER(CONCAT('%', :search, '%'))
        OR EXISTS (
            SELECT 1 FROM RestaurantTranslation t 
            WHERE t.restaurant = r 
            AND LOWER(t.name) LIKE LOWER(CONCAT('%', :search, '%'))
        )
    )
    """)
    Page<Restaurant> searchByKeywordAndRestaurantGroup(@Param("search") String search, @Param("restaurantGroupId") UUID restaurantGroupId, Pageable pageable);

    /**
     * Searches for restaurants by keyword with both status and restaurant group filters,
     * matching against restaurant code, location fields, restaurant group name, or translation names.
     * Only returns non-deleted restaurants with the specified status in the specified group.
     *
     * @param search           the search keyword to match against multiple fields (case-insensitive)
     * @param status           the entity status to filter by (ACTIVE, INACTIVE, etc.)
     * @param restaurantGroupId the UUID of the restaurant group to filter by
     * @param pageable         pagination and sorting parameters
     * @return paginated list of restaurants matching all filters (search, status, and group)
     */
    @Query("""
    SELECT r FROM Restaurant r
    WHERE r.isDeleted = false
    AND r.status = :status
    AND r.restaurantGroup.id = :restaurantGroupId
    AND (
        LOWER(r.restaurantCode) LIKE LOWER(CONCAT('%', :search, '%'))
        OR LOWER(r.city) LIKE LOWER(CONCAT('%', :search, '%'))
        OR LOWER(r.area) LIKE LOWER(CONCAT('%', :search, '%'))
        OR LOWER(r.state) LIKE LOWER(CONCAT('%', :search, '%'))
        OR LOWER(r.address1) LIKE LOWER(CONCAT('%', :search, '%'))
        OR LOWER(r.address2) LIKE LOWER(CONCAT('%', :search, '%'))
        OR LOWER(r.restaurantGroupName) LIKE LOWER(CONCAT('%', :search, '%'))
        OR EXISTS (
            SELECT 1 FROM RestaurantTranslation t 
            WHERE t.restaurant = r 
            AND LOWER(t.name) LIKE LOWER(CONCAT('%', :search, '%'))
        )
    )
    """)
    Page<Restaurant> searchByKeywordAndStatusAndRestaurantGroup(@Param("search") String search, @Param("status") EntityStatus status, @Param("restaurantGroupId") UUID restaurantGroupId, Pageable pageable);

    // Method to check if restaurant code exists and is not deleted (case-insensitive)
    @Query("SELECT COUNT(r) > 0 FROM Restaurant r WHERE LOWER(r.restaurantCode) = LOWER(:restaurantCode) AND r.isDeleted = false")
    boolean existsByRestaurantCodeAndIsDeletedFalse(@Param("restaurantCode") String restaurantCode);
    
    // Method to find restaurant by code and is not deleted (case-insensitive)
    @Query("SELECT r FROM Restaurant r WHERE LOWER(r.restaurantCode) = LOWER(:restaurantCode) AND r.isDeleted = false")
    Optional<Restaurant> findByRestaurantCodeAndIsDeletedFalse(@Param("restaurantCode") String restaurantCode);
    
    // Method to check if restaurant code exists (including deleted ones) (case-insensitive)
    @Query("SELECT COUNT(r) > 0 FROM Restaurant r WHERE LOWER(r.restaurantCode) = LOWER(:restaurantCode)")
    boolean existsByRestaurantCode(@Param("restaurantCode") String restaurantCode);
    
    // Method to find restaurant by code (including deleted ones) (case-insensitive)
    @Query("SELECT r FROM Restaurant r WHERE LOWER(r.restaurantCode) = LOWER(:restaurantCode)")
    Optional<Restaurant> findByRestaurantCode(@Param("restaurantCode") String restaurantCode);
    
    // Method to check if GST number exists (excluding deleted restaurants and optionally excluding a specific restaurant ID)
    @Query("SELECT COUNT(r) > 0 FROM Restaurant r WHERE r.gstNumber = :gstNumber AND r.isDeleted = false AND (:excludeId IS NULL OR r.id != :excludeId)")
    boolean existsByGstNumberAndIsDeletedFalse(@Param("gstNumber") String gstNumber, @Param("excludeId") UUID excludeId);
    
    // Method to find restaurant by GST number (excluding deleted restaurants)
    @Query("SELECT r FROM Restaurant r WHERE r.gstNumber = :gstNumber AND r.isDeleted = false")
    Optional<Restaurant> findByGstNumberAndIsDeletedFalse(@Param("gstNumber") String gstNumber);


    // Method to find restaurants not assigned to any group
    @Query("SELECT r FROM Restaurant r WHERE r.restaurantGroup IS NULL AND r.isDeleted = false")
    List<Restaurant> findByRestaurantGroupIsNullAndIsDeletedFalse();
    
    /**
     * Finds restaurants with optional filtering by status and search term.
     * The search term matches against restaurant translation names. Only returns non-deleted restaurants.
     * Supports pagination and sorting via Pageable.
     *
     * @param status  optional status filter (ACTIVE, INACTIVE, etc.), null returns all statuses
     * @param search  optional search term to match against translation names (case-insensitive),
     *                should be in format: '%keyword%' for LIKE matching
     * @param pageable pagination and sorting parameters
     * @return paginated list of restaurants matching the filters
     */
    @Query("""
    SELECT DISTINCT r FROM Restaurant r
    WHERE r.isDeleted = false
    AND (:status IS NULL OR r.status = :status)
    AND (
        :search IS NULL OR EXISTS (
            SELECT 1 FROM RestaurantTranslation t
            WHERE t.restaurant = r AND LOWER(t.name) LIKE :search
        )
    )
    """)
    Page<Restaurant> findByFilters(@Param("status") EntityStatus status, @Param("search") String search, Pageable pageable);

    // Method to find restaurants not assigned to any group with pagination
    @Query("SELECT r FROM Restaurant r WHERE r.restaurantGroup IS NULL AND r.isDeleted = false")
    Page<Restaurant> findByRestaurantGroupIsNullAndIsDeletedFalse(Pageable pageable);
    
    // Method to find restaurants not assigned to any group with status filter
    @Query("SELECT r FROM Restaurant r WHERE r.restaurantGroup IS NULL AND r.isDeleted = false AND r.status = :status")
    Page<Restaurant> findByRestaurantGroupIsNullAndIsDeletedFalseAndStatus(@Param("status") EntityStatus status, Pageable pageable);
    
    /**
     * Searches for restaurants that are not assigned to any restaurant group by keyword,
     * matching against restaurant code, location fields, or translation names.
     * Only returns non-deleted restaurants.
     *
     * @param search   the search keyword to match against multiple fields (case-insensitive)
     * @param pageable pagination and sorting parameters
     * @return paginated list of unassigned restaurants matching the search criteria
     */
    @Query("""
    SELECT r FROM Restaurant r
    WHERE r.restaurantGroup IS NULL 
    AND r.isDeleted = false
    AND (
        LOWER(r.restaurantCode) LIKE LOWER(CONCAT('%', :search, '%'))
        OR LOWER(r.city) LIKE LOWER(CONCAT('%', :search, '%'))
        OR LOWER(r.area) LIKE LOWER(CONCAT('%', :search, '%'))
        OR LOWER(r.state) LIKE LOWER(CONCAT('%', :search, '%'))
        OR LOWER(r.address1) LIKE LOWER(CONCAT('%', :search, '%'))
        OR LOWER(r.address2) LIKE LOWER(CONCAT('%', :search, '%'))
        OR EXISTS (
            SELECT 1 FROM RestaurantTranslation t 
            WHERE t.restaurant = r 
            AND LOWER(t.name) LIKE LOWER(CONCAT('%', :search, '%'))
        )
    )
    """)
    Page<Restaurant> searchUnassignedRestaurantsByKeyword(@Param("search") String search, Pageable pageable);
    
    /**
     * Searches for restaurants that are not assigned to any restaurant group by keyword and status,
     * matching against restaurant code, location fields, or translation names.
     * Only returns non-deleted restaurants with the specified status.
     *
     * @param search   the search keyword to match against multiple fields (case-insensitive)
     * @param status   the entity status to filter by (ACTIVE, INACTIVE, etc.)
     * @param pageable pagination and sorting parameters
     * @return paginated list of unassigned restaurants matching the search and status criteria
     */
    @Query("""
    SELECT r FROM Restaurant r
    WHERE r.restaurantGroup IS NULL 
    AND r.isDeleted = false
    AND r.status = :status
    AND (
        LOWER(r.restaurantCode) LIKE LOWER(CONCAT('%', :search, '%'))
        OR LOWER(r.city) LIKE LOWER(CONCAT('%', :search, '%'))
        OR LOWER(r.area) LIKE LOWER(CONCAT('%', :search, '%'))
        OR LOWER(r.state) LIKE LOWER(CONCAT('%', :search, '%'))
        OR LOWER(r.address1) LIKE LOWER(CONCAT('%', :search, '%'))
        OR LOWER(r.address2) LIKE LOWER(CONCAT('%', :search, '%'))
        OR EXISTS (
            SELECT 1 FROM RestaurantTranslation t 
            WHERE t.restaurant = r 
            AND LOWER(t.name) LIKE LOWER(CONCAT('%', :search, '%'))
        )
    )
    """)
    Page<Restaurant> searchUnassignedRestaurantsByKeywordAndStatus(@Param("search") String search, @Param("status") EntityStatus status, Pageable pageable);
    
    // Method to count all restaurants that are not deleted
    @Query("SELECT COUNT(r) FROM Restaurant r WHERE r.isDeleted = false")
    long countByIsDeletedFalse();
    
    // Method to count restaurants by restaurant group that are not deleted
    @Query("SELECT COUNT(r) FROM Restaurant r WHERE r.isDeleted = false AND r.restaurantGroup.id = :restaurantGroupId")
    long countByRestaurantGroupIdAndIsDeletedFalse(@Param("restaurantGroupId") UUID restaurantGroupId);
    
    // Method to count restaurants by restaurant ID (should be 1 if exists and not deleted, 0 otherwise)
    @Query("SELECT COUNT(r) FROM Restaurant r WHERE r.id = :restaurantId AND r.isDeleted = false")
    long countByRestaurantIdAndIsDeletedFalse(@Param("restaurantId") UUID restaurantId);

    // Method to find restaurant by ID with restaurantGroup eagerly fetched (for alert evaluation)
    @Query("SELECT r FROM Restaurant r LEFT JOIN FETCH r.restaurantGroup WHERE r.id = :restaurantId")
    Optional<Restaurant> findByIdWithGroup(@Param("restaurantId") UUID restaurantId);

}