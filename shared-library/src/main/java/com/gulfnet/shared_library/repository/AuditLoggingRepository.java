package com.gulfnet.shared_library.repository;

import com.gulfnet.shared_library.entity.AuditLogging;
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
public interface AuditLoggingRepository extends JpaRepository<AuditLogging, UUID> {

    /**
     * Find audit records by manager ID
     */
    @Query("SELECT a FROM AuditLogging a WHERE a.manager.id = :managerId ORDER BY a.createdAt DESC")
    Page<AuditLogging> findByManagerIdOrderByCreatedAtDesc(@Param("managerId") UUID managerId, Pageable pageable);

    /**
     * Find audit records by employee ID
     */
    @Query("SELECT a FROM AuditLogging a WHERE a.employee.id = :employeeId ORDER BY a.createdAt DESC")
    Page<AuditLogging> findByEmployeeIdOrderByCreatedAtDesc(@Param("employeeId") UUID employeeId, Pageable pageable);

    /**
     * Find audit records by restaurant ID
     */
    Page<AuditLogging> findByRestaurantIdOrderByCreatedAtDesc(UUID restaurantId, Pageable pageable);

    /**
     * Find audit records by action type
     */
    Page<AuditLogging> findByActionOrderByCreatedAtDesc(AuditLogging.AuditAction action, Pageable pageable);

    /**
     * Find audit records by manager and restaurant
     */
    @Query("SELECT a FROM AuditLogging a WHERE a.manager.id = :managerId AND a.restaurantId = :restaurantId ORDER BY a.createdAt DESC")
    Page<AuditLogging> findByManagerIdAndRestaurantIdOrderByCreatedAtDesc(
            @Param("managerId") UUID managerId, @Param("restaurantId") UUID restaurantId, Pageable pageable);

    /**
     * Find audit records within date range
     */
    @Query("SELECT a FROM AuditLogging a WHERE a.createdAt BETWEEN :startDate AND :endDate ORDER BY a.createdAt DESC")
    Page<AuditLogging> findByCreatedAtBetween(
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate,
            Pageable pageable);

    /**
     * Find recent actions by manager for a specific employee
     */
    @Query("SELECT a FROM AuditLogging a WHERE a.manager.id = :managerId AND a.employee.id = :employeeId ORDER BY a.createdAt DESC")
    List<AuditLogging> findRecentActionsByManagerForEmployee(
            @Param("managerId") UUID managerId,
            @Param("employeeId") UUID employeeId,
            Pageable pageable);

    /**
     * Count actions by manager and action type
     */
    @Query("SELECT COUNT(a) FROM AuditLogging a WHERE a.manager.id = :managerId AND a.action = :action")
    long countByManagerIdAndAction(@Param("managerId") UUID managerId, @Param("action") AuditLogging.AuditAction action);

    /**
     * Find audit records with filters
     */
    @Query("SELECT a FROM AuditLogging a WHERE " +
           "(:managerId IS NULL OR a.manager.id = :managerId) AND " +
           "(:employeeId IS NULL OR a.employee.id = :employeeId) AND " +
           "(:restaurantId IS NULL OR a.restaurantId = :restaurantId) AND " +
           "(:action IS NULL OR a.action = :action) AND " +
           "(:startDate IS NULL OR a.createdAt >= :startDate) AND " +
           "(:endDate IS NULL OR a.createdAt <= :endDate) " +
           "ORDER BY a.createdAt DESC")
    Page<AuditLogging> findWithFilters(
            @Param("managerId") UUID managerId,
            @Param("employeeId") UUID employeeId,
            @Param("restaurantId") UUID restaurantId,
            @Param("action") AuditLogging.AuditAction action,
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate,
            Pageable pageable);
}
