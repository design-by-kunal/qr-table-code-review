package com.gulfnet.shared_library.repository;

import com.gulfnet.shared_library.entity.TableAssignment;
import com.gulfnet.shared_library.enums.TableStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface TableAssignmentRepository extends JpaRepository<TableAssignment, UUID> {

    boolean existsByWaiterIdAndRestaurantTableIdAndUnassignedAtIsNull(UUID waiterId, UUID restaurantTableId);

    Page<TableAssignment> findByWaiterIdAndUnassignedAtIsNull(UUID waiterId, Pageable pageable);

    List<TableAssignment> findByWaiterIdAndUnassignedAtIsNull(UUID waiterId);

    List<TableAssignment> findByRestaurantTableIdAndUnassignedAtIsNull(UUID restaurantTableId);

    /**
     * Batch fetch active assignments for multiple tables. Used for batch cleanup.
     */
    List<TableAssignment> findByRestaurantTableIdInAndUnassignedAtIsNull(Collection<UUID> tableIds);

    @Query("SELECT ta FROM TableAssignment ta " +
        "JOIN FETCH ta.waiter w " +
        "WHERE ta.restaurantTable.id = :tableId " +
        "AND ta.unassignedAt IS NULL")
    List<TableAssignment> findByRestaurantTableIdAndUnassignedAtIsNullWithWaiter(@Param("tableId") UUID tableId);

    @Query("SELECT DISTINCT ta FROM TableAssignment ta " +
        "JOIN FETCH ta.waiter w " +
        "WHERE ta.restaurantTable.id IN :tableIds " +
        "AND ta.unassignedAt IS NULL")
    List<TableAssignment> findByRestaurantTableIdInAndUnassignedAtIsNullWithWaiter(@Param("tableIds") List<UUID> tableIds);

    // MULTIPLE STATUS SUPPORT

    /**
     * Finds active table assignments with optional filtering by table statuses, section, restaurant,
     * and search term. The search matches against table order or table code. Uses JOIN FETCH
     * to eagerly load related entities. Only returns assignments that are currently active (unassignedAt IS NULL).
     * Returns paginated results.
     *
     * @param statuses    optional collection of table statuses to filter by, null returns all statuses
     * @param sectionId   optional section ID filter, null returns all sections
     * @param restaurantId optional restaurant ID filter, null returns all restaurants
     * @param search      optional search term to match against table order (as string) or table code (case-insensitive)
     * @param pageable    pagination and sorting parameters
     * @return paginated list of active table assignments matching the filters with all relationships loaded
     */
    @Query(value = "SELECT DISTINCT ta FROM TableAssignment ta " +
        "JOIN FETCH ta.restaurantTable rt " +
        "JOIN FETCH rt.restaurantRow rr " +
        "JOIN FETCH rr.restaurantSection rs " +
        "JOIN FETCH rs.restaurantLayout rl " +
        "JOIN FETCH rl.restaurant r " +
        "LEFT JOIN FETCH rs.translations " +
        "WHERE ((:statuses) IS NULL OR rt.tableStatus IN (:statuses)) " +
        "AND (:sectionId IS NULL OR rs.id = :sectionId) " +
        "AND (:restaurantId IS NULL OR r.id = :restaurantId) " +
        "AND (:search IS NULL OR CAST(rt.tableOrder AS string) LIKE :search OR LOWER(rt.tableCode) LIKE LOWER(:search)) " +
        "AND rt.isDeleted = false " +
        "AND ta.unassignedAt IS NULL",
        countQuery = "SELECT COUNT(DISTINCT ta) FROM TableAssignment ta " +
        "JOIN ta.restaurantTable rt " +
        "JOIN rt.restaurantRow rr " +
        "JOIN rr.restaurantSection rs " +
        "JOIN rs.restaurantLayout rl " +
        "JOIN rl.restaurant r " +
        "WHERE ((:statuses) IS NULL OR rt.tableStatus IN (:statuses)) " +
        "AND (:sectionId IS NULL OR rs.id = :sectionId) " +
        "AND (:restaurantId IS NULL OR r.id = :restaurantId) " +
        "AND (:search IS NULL OR CAST(rt.tableOrder AS string) LIKE :search OR LOWER(rt.tableCode) LIKE LOWER(:search)) " +
        "AND rt.isDeleted = false " +
        "AND ta.unassignedAt IS NULL")
    Page<TableAssignment> findByFilterWithSearch(
            @Param("statuses") Collection<TableStatus> statuses,
            @Param("sectionId") UUID sectionId,
            @Param("restaurantId") UUID restaurantId,
            @Param("search") String search,
            Pageable pageable);

    /**
     * Finds active table assignments with optional filtering by table statuses, section, restaurant,
     * and search term. The search matches against table order or table code. Uses JOIN FETCH
     * to eagerly load related entities. Only returns assignments that are currently active (unassignedAt IS NULL).
     * Returns a sorted list (no pagination).
     *
     * @param statuses    optional collection of table statuses to filter by, null returns all statuses
     * @param sectionId   optional section ID filter, null returns all sections
     * @param restaurantId optional restaurant ID filter, null returns all restaurants
     * @param search      optional search term to match against table order (as string) or table code (case-insensitive)
     * @param sort        sorting parameters
     * @return sorted list of active table assignments matching the filters with all relationships loaded
     */
    @Query("SELECT DISTINCT ta FROM TableAssignment ta " +
        "JOIN FETCH ta.restaurantTable rt " +
        "JOIN FETCH rt.restaurantRow rr " +
        "JOIN FETCH rr.restaurantSection rs " +
        "JOIN FETCH rs.restaurantLayout rl " +
        "JOIN FETCH rl.restaurant r " +
        "LEFT JOIN FETCH rs.translations " +
        "WHERE ((:statuses) IS NULL OR rt.tableStatus IN (:statuses)) " +
        "AND (:sectionId IS NULL OR rs.id = :sectionId) " +
        "AND (:restaurantId IS NULL OR r.id = :restaurantId) " +
        "AND (:search IS NULL OR CAST(rt.tableOrder AS string) LIKE :search OR LOWER(rt.tableCode) LIKE LOWER(:search)) " +
        "AND rt.isDeleted = false " +
        "AND ta.unassignedAt IS NULL")
    List<TableAssignment> findByFilterWithSearch(
            @Param("statuses") Collection<TableStatus> statuses,
            @Param("sectionId") UUID sectionId,
            @Param("restaurantId") UUID restaurantId,
            @Param("search") String search,
            Sort sort);

    /**
     * Finds active table assignments with optional filtering by table statuses, section, and restaurant.
     * Uses JOIN FETCH to eagerly load related entities. Only returns assignments that are currently
     * active (unassignedAt IS NULL). Returns paginated results (no search term).
     *
     * @param statuses    optional collection of table statuses to filter by, null returns all statuses
     * @param sectionId   optional section ID filter, null returns all sections
     * @param restaurantId optional restaurant ID filter, null returns all restaurants
     * @param pageable    pagination and sorting parameters
     * @return paginated list of active table assignments matching the filters with all relationships loaded
     */
    @Query(value = "SELECT DISTINCT ta FROM TableAssignment ta " +
        "JOIN FETCH ta.restaurantTable rt " +
        "JOIN FETCH rt.restaurantRow rr " +
        "JOIN FETCH rr.restaurantSection rs " +
        "JOIN FETCH rs.restaurantLayout rl " +
        "JOIN FETCH rl.restaurant r " +
        "LEFT JOIN FETCH rs.translations " +
        "WHERE ((:statuses) IS NULL OR rt.tableStatus IN (:statuses)) " +
        "AND (:sectionId IS NULL OR rs.id = :sectionId) " +
        "AND (:restaurantId IS NULL OR r.id = :restaurantId) " +
        "AND rt.isDeleted = false " +
        "AND ta.unassignedAt IS NULL",
        countQuery = "SELECT COUNT(DISTINCT ta) FROM TableAssignment ta " +
        "JOIN ta.restaurantTable rt " +
        "JOIN rt.restaurantRow rr " +
        "JOIN rr.restaurantSection rs " +
        "JOIN rs.restaurantLayout rl " +
        "JOIN rl.restaurant r " +
        "WHERE ((:statuses) IS NULL OR rt.tableStatus IN (:statuses)) " +
        "AND (:sectionId IS NULL OR rs.id = :sectionId) " +
        "AND (:restaurantId IS NULL OR r.id = :restaurantId) " +
        "AND rt.isDeleted = false " +
        "AND ta.unassignedAt IS NULL")
    Page<TableAssignment> findByFilter(
            @Param("statuses") Collection<TableStatus> statuses,
            @Param("sectionId") UUID sectionId,
            @Param("restaurantId") UUID restaurantId,
            Pageable pageable);

    /**
     * Finds active table assignments with optional filtering by table statuses, section, and restaurant.
     * Uses JOIN FETCH to eagerly load related entities. Only returns assignments that are currently
     * active (unassignedAt IS NULL). Returns a sorted list (no pagination, no search term).
     *
     * @param statuses    optional collection of table statuses to filter by, null returns all statuses
     * @param sectionId   optional section ID filter, null returns all sections
     * @param restaurantId optional restaurant ID filter, null returns all restaurants
     * @param sort        sorting parameters
     * @return sorted list of active table assignments matching the filters with all relationships loaded
     */
    @Query("SELECT DISTINCT ta FROM TableAssignment ta " +
        "JOIN FETCH ta.restaurantTable rt " +
        "JOIN FETCH rt.restaurantRow rr " +
        "JOIN FETCH rr.restaurantSection rs " +
        "JOIN FETCH rs.restaurantLayout rl " +
        "JOIN FETCH rl.restaurant r " +
        "LEFT JOIN FETCH rs.translations " +
        "WHERE ((:statuses) IS NULL OR rt.tableStatus IN (:statuses)) " +
        "AND (:sectionId IS NULL OR rs.id = :sectionId) " +
        "AND (:restaurantId IS NULL OR r.id = :restaurantId) " +
        "AND rt.isDeleted = false " +
        "AND ta.unassignedAt IS NULL")
    List<TableAssignment> findByFilter(
            @Param("statuses") Collection<TableStatus> statuses,
            @Param("sectionId") UUID sectionId,
            @Param("restaurantId") UUID restaurantId,
            Sort sort);

    /**
     * Finds active table assignments for a specific waiter with optional filtering by table statuses,
     * section, restaurant, and search term. The search matches against table order or table code.
     * Uses JOIN FETCH to eagerly load related entities. Only returns assignments that are currently
     * active (unassignedAt IS NULL). Returns paginated results.
     *
     * @param waiterId    the UUID of the waiter to filter assignments by
     * @param statuses    optional collection of table statuses to filter by, null returns all statuses
     * @param sectionId   optional section ID filter, null returns all sections
     * @param restaurantId optional restaurant ID filter, null returns all restaurants
     * @param search      optional search term to match against table order (as string) or table code (case-insensitive)
     * @param pageable    pagination and sorting parameters
     * @return paginated list of active table assignments for the waiter matching the filters with all relationships loaded
     */
    @Query(value = "SELECT DISTINCT ta FROM TableAssignment ta " +
        "JOIN FETCH ta.restaurantTable rt " +
        "JOIN FETCH rt.restaurantRow rr " +
        "JOIN FETCH rr.restaurantSection rs " +
        "JOIN FETCH rs.restaurantLayout rl " +
        "JOIN FETCH rl.restaurant r " +
        "LEFT JOIN FETCH rs.translations " +
        "WHERE ta.waiter.id = :waiterId " +
        "AND ((:statuses) IS NULL OR rt.tableStatus IN (:statuses)) " +
        "AND (:sectionId IS NULL OR rs.id = :sectionId) " +
        "AND (:restaurantId IS NULL OR r.id = :restaurantId) " +
        "AND (:search IS NULL OR CAST(rt.tableOrder AS string) LIKE :search OR LOWER(rt.tableCode) LIKE LOWER(:search)) " +
        "AND rt.isDeleted = false " +
        "AND ta.unassignedAt IS NULL",
        countQuery = "SELECT COUNT(DISTINCT ta) FROM TableAssignment ta " +
        "JOIN ta.restaurantTable rt " +
        "JOIN rt.restaurantRow rr " +
        "JOIN rr.restaurantSection rs " +
        "JOIN rs.restaurantLayout rl " +
        "JOIN rl.restaurant r " +
        "WHERE ta.waiter.id = :waiterId " +
        "AND ((:statuses) IS NULL OR rt.tableStatus IN (:statuses)) " +
        "AND (:sectionId IS NULL OR rs.id = :sectionId) " +
        "AND (:restaurantId IS NULL OR r.id = :restaurantId) " +
        "AND (:search IS NULL OR CAST(rt.tableOrder AS string) LIKE :search OR LOWER(rt.tableCode) LIKE LOWER(:search)) " +
        "AND rt.isDeleted = false " +
        "AND ta.unassignedAt IS NULL")
    Page<TableAssignment> findByWaiterIdAndFilters(
            @Param("waiterId") UUID waiterId,
            @Param("statuses") Collection<TableStatus> statuses,
            @Param("sectionId") UUID sectionId,
            @Param("restaurantId") UUID restaurantId,
            @Param("search") String search,
            Pageable pageable);

    /**
     * Finds active table assignments for a specific waiter with optional filtering by table statuses,
     * section, restaurant, and search term. The search matches against table order or table code.
     * Uses JOIN FETCH to eagerly load related entities. Only returns assignments that are currently
     * active (unassignedAt IS NULL). Returns a sorted list (no pagination).
     *
     * @param waiterId    the UUID of the waiter to filter assignments by
     * @param statuses    optional collection of table statuses to filter by, null returns all statuses
     * @param sectionId   optional section ID filter, null returns all sections
     * @param restaurantId optional restaurant ID filter, null returns all restaurants
     * @param search      optional search term to match against table order (as string) or table code (case-insensitive)
     * @param sort        sorting parameters
     * @return sorted list of active table assignments for the waiter matching the filters with all relationships loaded
     */
    @Query("SELECT DISTINCT ta FROM TableAssignment ta " +
        "JOIN FETCH ta.restaurantTable rt " +
        "JOIN FETCH rt.restaurantRow rr " +
        "JOIN FETCH rr.restaurantSection rs " +
        "JOIN FETCH rs.restaurantLayout rl " +
        "JOIN FETCH rl.restaurant r " +
        "LEFT JOIN FETCH rs.translations " +
        "WHERE ta.waiter.id = :waiterId " +
        "AND ((:statuses) IS NULL OR rt.tableStatus IN (:statuses)) " +
        "AND (:sectionId IS NULL OR rs.id = :sectionId) " +
        "AND (:restaurantId IS NULL OR r.id = :restaurantId) " +
        "AND (:search IS NULL OR CAST(rt.tableOrder AS string) LIKE :search OR LOWER(rt.tableCode) LIKE LOWER(:search)) " +
        "AND rt.isDeleted = false " +
        "AND ta.unassignedAt IS NULL")
    List<TableAssignment> findByWaiterIdAndFilters(
            @Param("waiterId") UUID waiterId,
            @Param("statuses") Collection<TableStatus> statuses,
            @Param("sectionId") UUID sectionId,
            @Param("restaurantId") UUID restaurantId,
            @Param("search") String search,
            Sort sort);

    // Methods without search parameter - to avoid null handling issues
    /**
     * Finds active table assignments for a specific waiter with optional filtering by table statuses,
     * section, and restaurant. Uses JOIN FETCH to eagerly load related entities. Only returns
     * assignments that are currently active (unassignedAt IS NULL). Returns paginated results (no search term).
     * This method avoids null handling issues that can occur with search parameters.
     *
     * @param waiterId    the UUID of the waiter to filter assignments by
     * @param statuses    optional collection of table statuses to filter by, null returns all statuses
     * @param sectionId   optional section ID filter, null returns all sections
     * @param restaurantId optional restaurant ID filter, null returns all restaurants
     * @param pageable    pagination and sorting parameters
     * @return paginated list of active table assignments for the waiter matching the filters with all relationships loaded
     */
    @Query(value = "SELECT DISTINCT ta FROM TableAssignment ta " +
        "JOIN FETCH ta.restaurantTable rt " +
        "JOIN FETCH rt.restaurantRow rr " +
        "JOIN FETCH rr.restaurantSection rs " +
        "JOIN FETCH rs.restaurantLayout rl " +
        "JOIN FETCH rl.restaurant r " +
        "LEFT JOIN FETCH rs.translations " +
        "WHERE ta.waiter.id = :waiterId " +
        "AND ((:statuses) IS NULL OR rt.tableStatus IN (:statuses)) " +
        "AND (:sectionId IS NULL OR rs.id = :sectionId) " +
        "AND (:restaurantId IS NULL OR r.id = :restaurantId) " +
        "AND rt.isDeleted = false " +
        "AND ta.unassignedAt IS NULL",
        countQuery = "SELECT COUNT(DISTINCT ta) FROM TableAssignment ta " +
        "JOIN ta.restaurantTable rt " +
        "JOIN rt.restaurantRow rr " +
        "JOIN rr.restaurantSection rs " +
        "JOIN rs.restaurantLayout rl " +
        "JOIN rl.restaurant r " +
        "WHERE ta.waiter.id = :waiterId " +
        "AND ((:statuses) IS NULL OR rt.tableStatus IN (:statuses)) " +
        "AND (:sectionId IS NULL OR rs.id = :sectionId) " +
        "AND (:restaurantId IS NULL OR r.id = :restaurantId) " +
        "AND rt.isDeleted = false " +
        "AND ta.unassignedAt IS NULL")
    Page<TableAssignment> findByWaiterIdAndFiltersNoSearch(
            @Param("waiterId") UUID waiterId,
            @Param("statuses") Collection<TableStatus> statuses,
            @Param("sectionId") UUID sectionId,
            @Param("restaurantId") UUID restaurantId,
            Pageable pageable);

    /**
     * Finds active table assignments for a specific waiter with optional filtering by table statuses,
     * section, and restaurant. Uses JOIN FETCH to eagerly load related entities. Only returns
     * assignments that are currently active (unassignedAt IS NULL). Returns a sorted list (no pagination, no search term).
     * This method avoids null handling issues that can occur with search parameters.
     *
     * @param waiterId    the UUID of the waiter to filter assignments by
     * @param statuses    optional collection of table statuses to filter by, null returns all statuses
     * @param sectionId   optional section ID filter, null returns all sections
     * @param restaurantId optional restaurant ID filter, null returns all restaurants
     * @param sort        sorting parameters
     * @return sorted list of active table assignments for the waiter matching the filters with all relationships loaded
     */
    @Query("SELECT DISTINCT ta FROM TableAssignment ta " +
        "JOIN FETCH ta.restaurantTable rt " +
        "JOIN FETCH rt.restaurantRow rr " +
        "JOIN FETCH rr.restaurantSection rs " +
        "JOIN FETCH rs.restaurantLayout rl " +
        "JOIN FETCH rl.restaurant r " +
        "LEFT JOIN FETCH rs.translations " +
        "WHERE ta.waiter.id = :waiterId " +
        "AND ((:statuses) IS NULL OR rt.tableStatus IN (:statuses)) " +
        "AND (:sectionId IS NULL OR rs.id = :sectionId) " +
        "AND (:restaurantId IS NULL OR r.id = :restaurantId) " +
        "AND rt.isDeleted = false " +
        "AND ta.unassignedAt IS NULL")
    List<TableAssignment> findByWaiterIdAndFiltersNoSearch(
            @Param("waiterId") UUID waiterId,
            @Param("statuses") Collection<TableStatus> statuses,
            @Param("sectionId") UUID sectionId,
            @Param("restaurantId") UUID restaurantId,
            Sort sort);
}
