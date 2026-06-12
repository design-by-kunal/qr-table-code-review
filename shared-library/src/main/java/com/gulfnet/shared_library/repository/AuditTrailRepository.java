package com.gulfnet.shared_library.repository;

import com.gulfnet.shared_library.entity.AuditTrail;
import com.gulfnet.shared_library.enums.RequestStatus;
import com.gulfnet.shared_library.enums.ActionType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Repository
public interface AuditTrailRepository extends JpaRepository<AuditTrail, UUID> {

    /**
     * Find audit trails by user ID
     */
    @Query("SELECT at FROM AuditTrail at WHERE at.user.id = :userId ORDER BY at.createdAt DESC")
    Page<AuditTrail> findByUserIdOrderByCreatedAtDesc(@Param("userId") UUID userId, Pageable pageable);

    /**
     * Find audit trails by restaurant ID
     */
    @Query("SELECT at FROM AuditTrail at WHERE at.restaurant.id = :restaurantId ORDER BY at.createdAt DESC")
    Page<AuditTrail> findByRestaurantIdOrderByCreatedAtDesc(@Param("restaurantId") UUID restaurantId, Pageable pageable);

    /**
     * Find audit trails by action type
     */
    Page<AuditTrail> findByActionTypeOrderByCreatedAtDesc(ActionType actionType, Pageable pageable);

    /**
     * Find audit trails by status
     */
    Page<AuditTrail> findByStatusOrderByCreatedAtDesc(RequestStatus status, Pageable pageable);

    /**
     * Find audit trails with filters (all parameters optional)
     * Note: Using native query to handle bytea to text casting for log_number field
     * Using CASE statements to avoid PostgreSQL parameter type inference issues
     */
    @Query(value = "SELECT at.* FROM audit_trail at " +
           "LEFT JOIN users u ON u.id = at.user_id " +
           "LEFT JOIN role r ON r.id = u.role_id " +
           "LEFT JOIN restaurant res ON res.id = at.restaurant_id " +
           "WHERE at.status IN ('NONE', 'OPEN', 'APPROVED', 'DECLINED', 'NA') AND " +
           "(CAST(:userId AS UUID) IS NULL OR at.user_id = CAST(:userId AS UUID)) AND " +
           "(CAST(:restaurantId AS UUID) IS NULL OR at.restaurant_id = CAST(:restaurantId AS UUID)) AND " +
           "(CAST(:restaurantGroupId AS UUID) IS NULL OR res.restaurant_group_id = CAST(:restaurantGroupId AS UUID)) AND " +
           "(CAST(:actionType AS VARCHAR) IS NULL OR at.action_type = CAST(:actionType AS VARCHAR)) AND " +
           "(CAST(:status AS VARCHAR) IS NULL OR at.status = CAST(:status AS VARCHAR)) AND " +
           "(CAST(:module AS VARCHAR) IS NULL OR LOWER(COALESCE(at.entity_type, '')) = LOWER(CAST(:module AS VARCHAR))) AND " +
           "(CAST(:role AS VARCHAR) IS NULL OR (r.name IS NOT NULL AND LOWER(r.name) = LOWER(CAST(:role AS VARCHAR)))) AND " +
           "(CAST(:startDate AS TIMESTAMP) IS NULL OR at.created_at >= CAST(:startDate AS TIMESTAMP)) AND " +
           "(CAST(:endDate AS TIMESTAMP) IS NULL OR at.created_at <= CAST(:endDate AS TIMESTAMP)) AND " +
           "(CAST(:search AS VARCHAR) IS NULL OR (" +
           "LOWER(CAST(at.log_number AS TEXT)) LIKE LOWER('%' || CAST(:search AS VARCHAR) || '%') OR " +
           "(u.id IS NOT NULL AND (LOWER(COALESCE(u.first_name, '') || ' ' || COALESCE(u.last_name, '')) LIKE LOWER('%' || CAST(:search AS VARCHAR) || '%') OR " +
           "LOWER(COALESCE(u.user_code, '')) LIKE LOWER('%' || CAST(:search AS VARCHAR) || '%'))) OR " +
           "(at.restaurant_id IS NOT NULL AND EXISTS (" +
           "    SELECT 1 FROM restaurant_translation rt " +
           "    WHERE rt.restaurant_id = at.restaurant_id " +
           "    AND LOWER(rt.name) LIKE LOWER('%' || CAST(:search AS VARCHAR) || '%')" +
           "))" +
           "))",
           countQuery = "SELECT COUNT(*) FROM audit_trail at " +
           "LEFT JOIN users u ON u.id = at.user_id " +
           "LEFT JOIN role r ON r.id = u.role_id " +
           "LEFT JOIN restaurant res ON res.id = at.restaurant_id " +
           "WHERE at.status IN ('NONE', 'OPEN', 'APPROVED', 'DECLINED', 'NA') AND " +
           "(CAST(:userId AS UUID) IS NULL OR at.user_id = CAST(:userId AS UUID)) AND " +
           "(CAST(:restaurantId AS UUID) IS NULL OR at.restaurant_id = CAST(:restaurantId AS UUID)) AND " +
           "(CAST(:restaurantGroupId AS UUID) IS NULL OR res.restaurant_group_id = CAST(:restaurantGroupId AS UUID)) AND " +
           "(CAST(:actionType AS VARCHAR) IS NULL OR at.action_type = CAST(:actionType AS VARCHAR)) AND " +
           "(CAST(:status AS VARCHAR) IS NULL OR at.status = CAST(:status AS VARCHAR)) AND " +
           "(CAST(:module AS VARCHAR) IS NULL OR LOWER(COALESCE(at.entity_type, '')) = LOWER(CAST(:module AS VARCHAR))) AND " +
           "(CAST(:role AS VARCHAR) IS NULL OR (r.name IS NOT NULL AND LOWER(r.name) = LOWER(CAST(:role AS VARCHAR)))) AND " +
           "(CAST(:startDate AS TIMESTAMP) IS NULL OR at.created_at >= CAST(:startDate AS TIMESTAMP)) AND " +
           "(CAST(:endDate AS TIMESTAMP) IS NULL OR at.created_at <= CAST(:endDate AS TIMESTAMP)) AND " +
           "(CAST(:search AS VARCHAR) IS NULL OR (" +
           "LOWER(CAST(at.log_number AS TEXT)) LIKE LOWER('%' || CAST(:search AS VARCHAR) || '%') OR " +
           "(u.id IS NOT NULL AND (LOWER(COALESCE(u.first_name, '') || ' ' || COALESCE(u.last_name, '')) LIKE LOWER('%' || CAST(:search AS VARCHAR) || '%') OR " +
           "LOWER(COALESCE(u.user_code, '')) LIKE LOWER('%' || CAST(:search AS VARCHAR) || '%'))) OR " +
           "(at.restaurant_id IS NOT NULL AND EXISTS (" +
           "    SELECT 1 FROM restaurant_translation rt " +
           "    WHERE rt.restaurant_id = at.restaurant_id " +
           "    AND LOWER(rt.name) LIKE LOWER('%' || CAST(:search AS VARCHAR) || '%')" +
           "))" +
           "))",
           nativeQuery = true)
    Page<AuditTrail> findWithFilters(
            @Param("userId") UUID userId,
            @Param("restaurantId") UUID restaurantId,
            @Param("restaurantGroupId") UUID restaurantGroupId,
            @Param("actionType") String actionType,
            @Param("status") String status,
            @Param("module") String module,
            @Param("role") String role,
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate,
            @Param("search") String search,
            Pageable pageable);

    /**
     * Same as findWithFilters but excludes given action types (e.g. for MANAGER role).
     * Use this when caller needs DB-level exclusion so pagination returns the requested page size.
     *
     * @param excludedActionTypes non-empty list of action_type values to exclude
     */
    @Query(value = "SELECT at.* FROM audit_trail at " +
           "LEFT JOIN users u ON u.id = at.user_id " +
           "LEFT JOIN role r ON r.id = u.role_id " +
           "LEFT JOIN restaurant res ON res.id = at.restaurant_id " +
           "WHERE at.status IN ('NONE', 'OPEN', 'APPROVED', 'DECLINED', 'NA') AND " +
           "at.action_type NOT IN (:excludedActionTypes) AND " +
           "(CAST(:userId AS UUID) IS NULL OR at.user_id = CAST(:userId AS UUID)) AND " +
           "(CAST(:restaurantId AS UUID) IS NULL OR at.restaurant_id = CAST(:restaurantId AS UUID)) AND " +
           "(CAST(:restaurantGroupId AS UUID) IS NULL OR res.restaurant_group_id = CAST(:restaurantGroupId AS UUID)) AND " +
           "(CAST(:actionType AS VARCHAR) IS NULL OR at.action_type = CAST(:actionType AS VARCHAR)) AND " +
           "(CAST(:status AS VARCHAR) IS NULL OR at.status = CAST(:status AS VARCHAR)) AND " +
           "(CAST(:module AS VARCHAR) IS NULL OR LOWER(COALESCE(at.entity_type, '')) = LOWER(CAST(:module AS VARCHAR))) AND " +
           "(CAST(:role AS VARCHAR) IS NULL OR (r.name IS NOT NULL AND LOWER(r.name) = LOWER(CAST(:role AS VARCHAR)))) AND " +
           "(CAST(:startDate AS TIMESTAMP) IS NULL OR at.created_at >= CAST(:startDate AS TIMESTAMP)) AND " +
           "(CAST(:endDate AS TIMESTAMP) IS NULL OR at.created_at <= CAST(:endDate AS TIMESTAMP)) AND " +
           "(CAST(:search AS VARCHAR) IS NULL OR (" +
           "LOWER(CAST(at.log_number AS TEXT)) LIKE LOWER('%' || CAST(:search AS VARCHAR) || '%') OR " +
           "(u.id IS NOT NULL AND (LOWER(COALESCE(u.first_name, '') || ' ' || COALESCE(u.last_name, '')) LIKE LOWER('%' || CAST(:search AS VARCHAR) || '%') OR " +
           "LOWER(COALESCE(u.user_code, '')) LIKE LOWER('%' || CAST(:search AS VARCHAR) || '%'))) OR " +
           "(at.restaurant_id IS NOT NULL AND EXISTS (" +
           "    SELECT 1 FROM restaurant_translation rt " +
           "    WHERE rt.restaurant_id = at.restaurant_id " +
           "    AND LOWER(rt.name) LIKE LOWER('%' || CAST(:search AS VARCHAR) || '%')" +
           "))" +
           "))",
           countQuery = "SELECT COUNT(*) FROM audit_trail at " +
           "LEFT JOIN users u ON u.id = at.user_id " +
           "LEFT JOIN role r ON r.id = u.role_id " +
           "LEFT JOIN restaurant res ON res.id = at.restaurant_id " +
           "WHERE at.status IN ('NONE', 'OPEN', 'APPROVED', 'DECLINED', 'NA') AND " +
           "at.action_type NOT IN (:excludedActionTypes) AND " +
           "(CAST(:userId AS UUID) IS NULL OR at.user_id = CAST(:userId AS UUID)) AND " +
           "(CAST(:restaurantId AS UUID) IS NULL OR at.restaurant_id = CAST(:restaurantId AS UUID)) AND " +
           "(CAST(:restaurantGroupId AS UUID) IS NULL OR res.restaurant_group_id = CAST(:restaurantGroupId AS UUID)) AND " +
           "(CAST(:actionType AS VARCHAR) IS NULL OR at.action_type = CAST(:actionType AS VARCHAR)) AND " +
           "(CAST(:status AS VARCHAR) IS NULL OR at.status = CAST(:status AS VARCHAR)) AND " +
           "(CAST(:module AS VARCHAR) IS NULL OR LOWER(COALESCE(at.entity_type, '')) = LOWER(CAST(:module AS VARCHAR))) AND " +
           "(CAST(:role AS VARCHAR) IS NULL OR (r.name IS NOT NULL AND LOWER(r.name) = LOWER(CAST(:role AS VARCHAR)))) AND " +
           "(CAST(:startDate AS TIMESTAMP) IS NULL OR at.created_at >= CAST(:startDate AS TIMESTAMP)) AND " +
           "(CAST(:endDate AS TIMESTAMP) IS NULL OR at.created_at <= CAST(:endDate AS TIMESTAMP)) AND " +
           "(CAST(:search AS VARCHAR) IS NULL OR (" +
           "LOWER(CAST(at.log_number AS TEXT)) LIKE LOWER('%' || CAST(:search AS VARCHAR) || '%') OR " +
           "(u.id IS NOT NULL AND (LOWER(COALESCE(u.first_name, '') || ' ' || COALESCE(u.last_name, '')) LIKE LOWER('%' || CAST(:search AS VARCHAR) || '%') OR " +
           "LOWER(COALESCE(u.user_code, '')) LIKE LOWER('%' || CAST(:search AS VARCHAR) || '%'))) OR " +
           "(at.restaurant_id IS NOT NULL AND EXISTS (" +
           "    SELECT 1 FROM restaurant_translation rt " +
           "    WHERE rt.restaurant_id = at.restaurant_id " +
           "    AND LOWER(rt.name) LIKE LOWER('%' || CAST(:search AS VARCHAR) || '%')" +
           "))" +
           "))",
           nativeQuery = true)
    Page<AuditTrail> findWithFiltersExcludingActionTypes(
            @Param("userId") UUID userId,
            @Param("restaurantId") UUID restaurantId,
            @Param("restaurantGroupId") UUID restaurantGroupId,
            @Param("actionType") String actionType,
            @Param("status") String status,
            @Param("module") String module,
            @Param("role") String role,
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate,
            @Param("search") String search,
            @Param("excludedActionTypes") List<String> excludedActionTypes,
            Pageable pageable);

    /**
     * Find audit trail by log number
     */
    AuditTrail findByLogNumber(String logNumber);

    /**
     * Count trails by status
     */
    @Query("SELECT COUNT(at) FROM AuditTrail at WHERE at.status = :status")
    long countByStatus(@Param("status") RequestStatus status);

    /**
     * Count trails by restaurant and status
     */
    @Query("SELECT COUNT(at) FROM AuditTrail at WHERE at.restaurant.id = :restaurantId AND at.status = :status")
    long countByRestaurantIdAndStatus(@Param("restaurantId") UUID restaurantId, @Param("status") RequestStatus status);

    /**
     * Count trails by action type
     */
    @Query("SELECT COUNT(at) FROM AuditTrail at WHERE at.actionType = :actionType")
    long countByActionType(@Param("actionType") ActionType actionType);

    /**
     * Count trails by restaurant group and status
     */
    @Query("SELECT COUNT(at) FROM AuditTrail at WHERE at.restaurant.restaurantGroup.id = :restaurantGroupId AND at.status = :status")
    long countByRestaurantGroupIdAndStatus(@Param("restaurantGroupId") UUID restaurantGroupId, @Param("status") RequestStatus status);

    /**
     * Whether a PAYMENT audit already exists for this transaction (prevents duplicate GMO success logs).
     */
    boolean existsByEntityIdAndActionType(UUID entityId, ActionType actionType);

    /**
     * Find audit trail by entity ID, action type, and status
     * Used to find existing audit trail entries for updating instead of creating duplicates
     */
    @Query("SELECT at FROM AuditTrail at WHERE at.entityId = :entityId AND at.actionType = :actionType AND at.status = :status ORDER BY at.createdAt DESC")
    List<AuditTrail> findByEntityIdAndActionTypeAndStatus(
            @Param("entityId") UUID entityId,
            @Param("actionType") ActionType actionType,
            @Param("status") RequestStatus status);

    /**
     * Get distinct action types from audit trail table
     * Returns all action types that actually exist in the database
     */
    @Query("SELECT DISTINCT at.actionType FROM AuditTrail at ORDER BY at.actionType")
    List<ActionType> findDistinctActionTypes();

    /**
     * Get next value from audit_trail_seq sequence
     * This provides a thread-safe way to generate unique log numbers
     */
    @Query(value = "SELECT nextval('audit_trail_seq')", nativeQuery = true)
    Long getNextSequenceValue();
}

