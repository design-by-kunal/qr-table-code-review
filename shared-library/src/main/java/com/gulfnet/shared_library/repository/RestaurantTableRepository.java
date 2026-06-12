package com.gulfnet.shared_library.repository;

import com.gulfnet.shared_library.entity.RestaurantTable;
import com.gulfnet.shared_library.enums.RequestStatus;
import com.gulfnet.shared_library.enums.TableStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface RestaurantTableRepository extends JpaRepository<RestaurantTable, UUID> {
    @Query("select coalesce(sum(t.capacity), 0) from RestaurantTable t join t.restaurantRow r join r.restaurantSection s join s.restaurantLayout l where l.restaurant.id = :restaurantId and (t.capacity is not null) and t.isDeleted = false")
    Integer getTotalSeatingCapacityByRestaurantId(@Param("restaurantId") UUID restaurantId);

    // Batch method to get seating capacity for multiple restaurants
    @Query("SELECT l.restaurant.id, COALESCE(SUM(t.capacity), 0) FROM RestaurantTable t " +
           "JOIN t.restaurantRow r JOIN r.restaurantSection s JOIN s.restaurantLayout l " +
           "WHERE l.restaurant.id IN :restaurantIds AND t.capacity IS NOT NULL AND t.isDeleted = false " +
           "GROUP BY l.restaurant.id")
    List<Object[]> getTotalSeatingCapacityByRestaurantIds(@Param("restaurantIds") List<UUID> restaurantIds);

    // Find tables with table/section requests
    Page<RestaurantTable> findByTableSectionRequestStatus(RequestStatus status, Pageable pageable);
    
    /**
     * Find tables by table/section request status, with optional status filter.
     * If status is null, returns all tables with request status != NONE.
     */
    @Query("SELECT t FROM RestaurantTable t WHERE " +
           "(:status IS NULL AND t.tableSectionRequestStatus != com.gulfnet.shared_library.enums.RequestStatus.NONE) OR " +
           "(:status IS NOT NULL AND t.tableSectionRequestStatus = :status)")
    Page<RestaurantTable> findByTableSectionRequestStatusOptional(
            @Param("status") RequestStatus status, 
            Pageable pageable);

    /**
     * Count total tables for a restaurant (non-deleted tables)
     */
    @Query("SELECT COUNT(t) FROM RestaurantTable t " +
           "JOIN t.restaurantRow r " +
           "JOIN r.restaurantSection s " +
           "JOIN s.restaurantLayout l " +
           "WHERE l.restaurant.id = :restaurantId " +
           "AND t.isDeleted = false")
    Long countTotalTablesByRestaurantId(@Param("restaurantId") UUID restaurantId);

    /**
     * Count total tables for a restaurant group (non-deleted tables)
     */
    @Query("SELECT COUNT(t) FROM RestaurantTable t " +
           "JOIN t.restaurantRow r " +
           "JOIN r.restaurantSection s " +
           "JOIN s.restaurantLayout l " +
           "JOIN l.restaurant res " +
           "WHERE res.restaurantGroup.id = :restaurantGroupId " +
           "AND t.isDeleted = false " +
           "AND (res.isDeleted IS NULL OR res.isDeleted = false)")
    Long countTotalTablesByRestaurantGroupId(@Param("restaurantGroupId") UUID restaurantGroupId);

    /**
     * Count total tables across all restaurants (non-deleted tables)
     */
    @Query("SELECT COUNT(t) FROM RestaurantTable t " +
           "JOIN t.restaurantRow r " +
           "JOIN r.restaurantSection s " +
           "JOIN s.restaurantLayout l " +
           "JOIN l.restaurant res " +
           "WHERE t.isDeleted = false " +
           "AND (res.isDeleted IS NULL OR res.isDeleted = false)")
    Long countTotalTables();

    /**
     * Finds restaurant tables with optional filtering by table statuses, section, restaurant,
     * and search term. The search matches against table order or table code. Uses JOIN FETCH
     * to eagerly load related entities. Returns paginated results.
     *
     * @param statuses    optional collection of table statuses to filter by, null returns all statuses
     * @param sectionId   optional section ID filter, null returns all sections
     * @param restaurantId optional restaurant ID filter, null returns all restaurants
     * @param search      optional search term to match against table order (as string) or table code (case-insensitive)
     * @param pageable    pagination and sorting parameters
     * @return paginated list of restaurant tables matching the filters with all relationships loaded
     */
    @Query(value = "SELECT DISTINCT t FROM RestaurantTable t " +
        "JOIN FETCH t.restaurantRow rr " +
        "JOIN FETCH rr.restaurantSection rs " +
        "JOIN FETCH rs.restaurantLayout rl " +
        "JOIN FETCH rl.restaurant r " +
        "LEFT JOIN FETCH rs.translations " +
        "WHERE ((:statuses) IS NULL OR t.tableStatus IN (:statuses)) " +
        "AND (:sectionId IS NULL OR rs.id = :sectionId) " +
        "AND (:restaurantId IS NULL OR r.id = :restaurantId) " +
        "AND (:search IS NULL OR CAST(t.tableOrder AS string) LIKE :search OR LOWER(t.tableCode) LIKE LOWER(:search)) " +
        "AND t.isDeleted = false",
        countQuery = "SELECT COUNT(DISTINCT t) FROM RestaurantTable t " +
        "JOIN t.restaurantRow rr " +
        "JOIN rr.restaurantSection rs " +
        "JOIN rs.restaurantLayout rl " +
        "JOIN rl.restaurant r " +
        "WHERE ((:statuses) IS NULL OR t.tableStatus IN (:statuses)) " +
        "AND (:sectionId IS NULL OR rs.id = :sectionId) " +
        "AND (:restaurantId IS NULL OR r.id = :restaurantId) " +
        "AND (:search IS NULL OR CAST(t.tableOrder AS string) LIKE :search OR LOWER(t.tableCode) LIKE LOWER(:search)) " +
        "AND t.isDeleted = false")
    Page<RestaurantTable> findByFiltersWithSearch(
            @Param("statuses") Collection<TableStatus> statuses,
            @Param("sectionId") UUID sectionId,
            @Param("restaurantId") UUID restaurantId,
            @Param("search") String search,
            Pageable pageable);

    /**
     * Finds restaurant tables with optional filtering by table statuses, section, restaurant,
     * and search term. The search matches against table order or table code. Uses JOIN FETCH
     * to eagerly load related entities. Returns a sorted list (no pagination).
     *
     * @param statuses    optional collection of table statuses to filter by, null returns all statuses
     * @param sectionId   optional section ID filter, null returns all sections
     * @param restaurantId optional restaurant ID filter, null returns all restaurants
     * @param search      optional search term to match against table order (as string) or table code (case-insensitive)
     * @param sort        sorting parameters
     * @return sorted list of restaurant tables matching the filters with all relationships loaded
     */
    @Query("SELECT DISTINCT t FROM RestaurantTable t " +
        "JOIN FETCH t.restaurantRow rr " +
        "JOIN FETCH rr.restaurantSection rs " +
        "JOIN FETCH rs.restaurantLayout rl " +
        "JOIN FETCH rl.restaurant r " +
        "LEFT JOIN FETCH rs.translations " +
        "WHERE ((:statuses) IS NULL OR t.tableStatus IN (:statuses)) " +
        "AND (:sectionId IS NULL OR rs.id = :sectionId) " +
        "AND (:restaurantId IS NULL OR r.id = :restaurantId) " +
        "AND (:search IS NULL OR CAST(t.tableOrder AS string) LIKE :search OR LOWER(t.tableCode) LIKE LOWER(:search)) " +
        "AND t.isDeleted = false")
    List<RestaurantTable> findByFiltersWithSearch(
            @Param("statuses") Collection<TableStatus> statuses,
            @Param("sectionId") UUID sectionId,
            @Param("restaurantId") UUID restaurantId,
            @Param("search") String search,
            Sort sort);

    /**
     * Finds restaurant tables with optional filtering by table statuses, section, and restaurant.
     * Uses JOIN FETCH to eagerly load related entities. Returns paginated results (no search term).
     *
     * @param statuses    optional collection of table statuses to filter by, null returns all statuses
     * @param sectionId   optional section ID filter, null returns all sections
     * @param restaurantId optional restaurant ID filter, null returns all restaurants
     * @param pageable    pagination and sorting parameters
     * @return paginated list of restaurant tables matching the filters with all relationships loaded
     */
    @Query(value = "SELECT DISTINCT t FROM RestaurantTable t " +
        "JOIN FETCH t.restaurantRow rr " +
        "JOIN FETCH rr.restaurantSection rs " +
        "JOIN FETCH rs.restaurantLayout rl " +
        "JOIN FETCH rl.restaurant r " +
        "LEFT JOIN FETCH rs.translations " +
        "WHERE ((:statuses) IS NULL OR t.tableStatus IN (:statuses)) " +
        "AND (:sectionId IS NULL OR rs.id = :sectionId) " +
        "AND (:restaurantId IS NULL OR r.id = :restaurantId) " +
        "AND t.isDeleted = false",
        countQuery = "SELECT COUNT(DISTINCT t) FROM RestaurantTable t " +
        "JOIN t.restaurantRow rr " +
        "JOIN rr.restaurantSection rs " +
        "JOIN rs.restaurantLayout rl " +
        "JOIN rl.restaurant r " +
        "WHERE ((:statuses) IS NULL OR t.tableStatus IN (:statuses)) " +
        "AND (:sectionId IS NULL OR rs.id = :sectionId) " +
        "AND (:restaurantId IS NULL OR r.id = :restaurantId) " +
        "AND t.isDeleted = false")
    Page<RestaurantTable> findByFilters(
            @Param("statuses") Collection<TableStatus> statuses,
            @Param("sectionId") UUID sectionId,
            @Param("restaurantId") UUID restaurantId,
            Pageable pageable);

    /**
     * Finds restaurant tables with optional filtering by table statuses, section, and restaurant.
     * Uses JOIN FETCH to eagerly load related entities. Returns a sorted list (no pagination, no search term).
     *
     * @param statuses    optional collection of table statuses to filter by, null returns all statuses
     * @param sectionId   optional section ID filter, null returns all sections
     * @param restaurantId optional restaurant ID filter, null returns all restaurants
     * @param sort        sorting parameters
     * @return sorted list of restaurant tables matching the filters with all relationships loaded
     */
    @Query("SELECT DISTINCT t FROM RestaurantTable t " +
        "JOIN FETCH t.restaurantRow rr " +
        "JOIN FETCH rr.restaurantSection rs " +
        "JOIN FETCH rs.restaurantLayout rl " +
        "JOIN FETCH rl.restaurant r " +
        "LEFT JOIN FETCH rs.translations " +
        "WHERE ((:statuses) IS NULL OR t.tableStatus IN (:statuses)) " +
        "AND (:sectionId IS NULL OR rs.id = :sectionId) " +
        "AND (:restaurantId IS NULL OR r.id = :restaurantId) " +
        "AND t.isDeleted = false")
    List<RestaurantTable> findByFilters(
            @Param("statuses") Collection<TableStatus> statuses,
            @Param("sectionId") UUID sectionId,
            @Param("restaurantId") UUID restaurantId,
            Sort sort);

    /**
     * Find RestaurantTable by ID with all relationships loaded using JOIN FETCH.
     * This prevents LazyInitializationException when accessing nested properties.
     */
    @Query("SELECT DISTINCT t FROM RestaurantTable t " +
        "JOIN FETCH t.restaurantRow rr " +
        "JOIN FETCH rr.restaurantSection rs " +
        "JOIN FETCH rs.restaurantLayout rl " +
        "JOIN FETCH rl.restaurant r " +
        "LEFT JOIN FETCH rs.translations " +
        "WHERE t.id = :id")
    Optional<RestaurantTable> findByIdWithRelationships(@Param("id") UUID id);

    /**
     * Find table IDs by table codes for a specific restaurant.
     * Table codes are unique within a restaurant layout.
     * Returns only non-deleted tables.
     * Note: The tableCodes parameter should already be converted to lowercase for case-insensitive matching.
     */
    @Query("SELECT t.id FROM RestaurantTable t " +
           "JOIN t.restaurantRow rr " +
           "JOIN rr.restaurantSection rs " +
           "JOIN rs.restaurantLayout rl " +
           "WHERE rl.restaurant.id = :restaurantId " +
           "AND LOWER(t.tableCode) IN :lowercaseTableCodes " +
           "AND t.isDeleted = false")
    List<UUID> findTableIdsByTableCodesAndRestaurantId(
            @Param("restaurantId") UUID restaurantId,
            @Param("lowercaseTableCodes") Collection<String> lowercaseTableCodes);

    /**
     * Find virtual table for a restaurant.
     * Returns the first non-deleted virtual table for the restaurant.
     */
    @Query("SELECT DISTINCT t FROM RestaurantTable t " +
           "JOIN FETCH t.restaurantRow rr " +
           "JOIN FETCH rr.restaurantSection rs " +
           "JOIN FETCH rs.restaurantLayout rl " +
           "JOIN FETCH rl.restaurant r " +
           "WHERE r.id = :restaurantId " +
           "AND t.isVirtual = true " +
           "AND t.isDeleted = false")
    Optional<RestaurantTable> findVirtualTableByRestaurantId(@Param("restaurantId") UUID restaurantId);

    /**
     * Find tables by restaurant layout ID that need QR code generation.
     * Returns non-deleted, non-virtual tables with null or empty QR code URL.
     */
    @Query("SELECT t FROM RestaurantTable t " +
           "JOIN t.restaurantRow rr " +
           "JOIN rr.restaurantSection rs " +
           "JOIN rs.restaurantLayout rl " +
           "WHERE rl.id = :layoutId " +
           "AND t.isDeleted = false " +
           "AND (t.isVirtual IS NULL OR t.isVirtual = false) " +
           "AND (t.qrCodeUrl IS NULL OR TRIM(t.qrCodeUrl) = '')")
    List<RestaurantTable> findTablesNeedingQrCodesByLayoutId(@Param("layoutId") UUID layoutId);

}
