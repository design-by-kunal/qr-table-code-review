package com.gulfnet.shared_library.repository;

import com.gulfnet.shared_library.entity.User;
import com.gulfnet.shared_library.enums.EmploymentType;
import com.gulfnet.shared_library.enums.EntityStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import java.util.UUID;
import java.util.Optional;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;
import java.time.OffsetDateTime;

public interface UserRepository extends JpaRepository<User, UUID>, JpaSpecificationExecutor<User> {
    Optional<User> findByEmail(String email);
    boolean existsByEmail(String email);
    
    // Case-insensitive email lookup
    @Query("SELECT u FROM User u WHERE LOWER(u.email) = LOWER(:email)")
    Optional<User> findByEmailIgnoreCase(@Param("email") String email);
    
    // Case-insensitive email existence check
    @Query("SELECT COUNT(u) > 0 FROM User u WHERE LOWER(u.email) = LOWER(:email)")
    boolean existsByEmailIgnoreCase(@Param("email") String email);
    
    // Check if email exists excluding a specific user (for update scenarios)
    @Query("SELECT COUNT(u) > 0 FROM User u WHERE LOWER(u.email) = LOWER(:email) AND u.id != :excludeUserId")
    boolean existsByEmailExcludingUser(@Param("email") String email, @Param("excludeUserId") UUID excludeUserId);
    boolean existsByUserCode(String userCode);
    
    // Case-insensitive userCode lookup
    @Query("SELECT COUNT(u) > 0 FROM User u WHERE LOWER(u.userCode) = LOWER(:userCode)")
    boolean existsByUserCodeIgnoreCase(@Param("userCode") String userCode);

    // Case-insensitive userCode lookup for ACTIVE & non-deleted users
    @Query("SELECT u FROM User u WHERE LOWER(u.userCode) = LOWER(:userCode) AND u.isDeleted = false AND u.status = :status")
    Optional<User> findByUserCodeIgnoreCaseAndIsDeletedFalseAndStatus(@Param("userCode") String userCode, @Param("status") EntityStatus status);

    // Case-insensitive userCode lookup regardless of status / deletion (for login diagnostics)
    @Query("SELECT u FROM User u WHERE LOWER(u.userCode) = LOWER(:userCode)")
    Optional<User> findByUserCodeIgnoreCase(@Param("userCode") String userCode);

    
    Page<User> findAllByRoleIdAndStatusAndEmploymentTypeAndIsDeletedFalse(UUID roleId, EntityStatus status, EmploymentType employmentType, Pageable pageable);
    Page<User> findAllByRoleIdAndStatusAndIsDeletedFalse(UUID roleId, EntityStatus status, Pageable pageable);
    Page<User> findAllByRoleIdAndEmploymentTypeAndIsDeletedFalse(UUID roleId, EmploymentType employmentType, Pageable pageable);
    Page<User> findAllByStatusAndEmploymentTypeAndIsDeletedFalse(EntityStatus status, EmploymentType employmentType, Pageable pageable);
    Page<User> findAllByRoleIdAndIsDeletedFalse(UUID roleId, Pageable pageable);
    Page<User> findAllByStatusAndIsDeletedFalse(EntityStatus status, Pageable pageable);
    Page<User> findAllByEmploymentTypeAndIsDeletedFalse(EmploymentType employmentType, Pageable pageable);
    Page<User> findAllByIsDeletedFalse(Pageable pageable);
// Method to count users by restaurant_id for dynamic employee count
    Integer countByRestaurantId(UUID restaurantId);

    // Method to count non-deleted users by restaurant_id for accurate employee count
    Integer countByRestaurantIdAndIsDeletedFalse(UUID restaurantId);

    // Batch method to count employees for multiple restaurants
    // Returns list of [restaurantId, count] pairs for restaurants that have employees
    // Note: Restaurants with 0 employees won't appear in results (use getOrDefault when building map)
    @Query("SELECT u.restaurantId, COUNT(u) FROM User u WHERE u.restaurantId IN :restaurantIds AND u.isDeleted = false GROUP BY u.restaurantId")
    List<Object[]> countEmployeesByRestaurantIds(@Param("restaurantIds") List<UUID> restaurantIds);

    /**
     * Searches for users by keyword, matching against full name, first name, last name,
     * email, or contact number. Only returns non-deleted users.
     *
     * @param search   the search keyword to match against multiple user fields (case-insensitive)
     * @param pageable pagination and sorting parameters
     * @return paginated list of users matching the search criteria
     */
    @Query("""
    SELECT u FROM User u
    WHERE u.isDeleted = false
    AND (
        LOWER(CONCAT(u.firstName, ' ', u.lastName)) LIKE LOWER(CONCAT('%', :search, '%'))
        OR LOWER(u.firstName) LIKE LOWER(CONCAT('%', :search, '%'))
        OR LOWER(u.lastName) LIKE LOWER(CONCAT('%', :search, '%'))
        OR LOWER(u.email) LIKE LOWER(CONCAT('%', :search, '%'))
        OR LOWER(u.contactNumber) LIKE LOWER(CONCAT('%', :search, '%'))
    )
    """)
    Page<User> searchByKeyword(@Param("search") String search, Pageable pageable);

    Optional<User> findByEmailAndIsDeletedFalseAndStatus(String email, EntityStatus status);

    Page<User> findAllByRestaurantIdIsNotNullAndIsDeletedFalse(Pageable pageable);
    Page<User> findAllByRestaurantIdIsNullAndIsDeletedFalse(Pageable pageable);
    Page<User> findAllByRestaurantIdAndIsDeletedFalse(UUID restaurantId, Pageable pageable);
    
    // Two filter combinations with restaurant assignment
    Page<User> findAllByRestaurantIdIsNotNullAndRoleIdAndIsDeletedFalse(UUID roleId, Pageable pageable);
    Page<User> findAllByRestaurantIdIsNullAndRoleIdAndIsDeletedFalse(UUID roleId, Pageable pageable);
    
    Page<User> findAllByRestaurantIdIsNotNullAndStatusAndIsDeletedFalse(EntityStatus status, Pageable pageable);
    Page<User> findAllByRestaurantIdIsNullAndStatusAndIsDeletedFalse(EntityStatus status, Pageable pageable);
    
    Page<User> findAllByRestaurantIdIsNotNullAndEmploymentTypeAndIsDeletedFalse(EmploymentType employmentType, Pageable pageable);
    Page<User> findAllByRestaurantIdIsNullAndEmploymentTypeAndIsDeletedFalse(EmploymentType employmentType, Pageable pageable);
    
    // Three filter combinations with restaurant assignment
    Page<User> findAllByRestaurantIdIsNotNullAndRoleIdAndStatusAndIsDeletedFalse(
            UUID roleId, EntityStatus status, Pageable pageable);
    Page<User> findAllByRestaurantIdIsNullAndRoleIdAndStatusAndIsDeletedFalse(
            UUID roleId, EntityStatus status, Pageable pageable);
    
    Page<User> findAllByRestaurantIdIsNotNullAndRoleIdAndEmploymentTypeAndIsDeletedFalse(
            UUID roleId, EmploymentType employmentType, Pageable pageable);
    Page<User> findAllByRestaurantIdIsNullAndRoleIdAndEmploymentTypeAndIsDeletedFalse(
            UUID roleId, EmploymentType employmentType, Pageable pageable);
    
    Page<User> findAllByRestaurantIdIsNotNullAndStatusAndEmploymentTypeAndIsDeletedFalse(
            EntityStatus status, EmploymentType employmentType, Pageable pageable);
    Page<User> findAllByRestaurantIdIsNullAndStatusAndEmploymentTypeAndIsDeletedFalse(
            EntityStatus status, EmploymentType employmentType, Pageable pageable);
    
    // Four filter combinations with restaurant assignment
    Page<User> findAllByRestaurantIdIsNotNullAndRoleIdAndStatusAndEmploymentTypeAndIsDeletedFalse(
            UUID roleId, EntityStatus status, EmploymentType employmentType, Pageable pageable);
    Page<User> findAllByRestaurantIdIsNullAndRoleIdAndStatusAndEmploymentTypeAndIsDeletedFalse(
            UUID roleId, EntityStatus status, EmploymentType employmentType, Pageable pageable);
            
    Page<User> findAllByRestaurantIdAndStatusAndIsDeletedFalse(UUID restaurantId, EntityStatus status, Pageable pageable);

    long countByIdInAndIsDeletedFalse(List<UUID> userIds);

    List<User> findByIdInAndIsDeletedFalse(List<UUID> userIds);

    /**
     * Returns a page of non-deleted {@link User} records for a restaurant and {@link EntityStatus},
     * where the keyword matches (case-insensitive {@code LIKE}) any of: full name
     * ({@code firstName + ' ' + lastName}), first name, last name, email, or contact number.
     *
     * @param search        substring to match; passed into {@code CONCAT('%', :search, '%')}
     * @param restaurantId  users must belong to this restaurant
     * @param status        users must be in this status
     * @param pageable      paging and sorting
     * @return matching users
     */
    @Query("""
    SELECT u FROM User u
    WHERE u.isDeleted = false
    AND u.restaurantId = :restaurantId
    AND u.status = :status
    AND (
        LOWER(CONCAT(u.firstName, ' ', u.lastName)) LIKE LOWER(CONCAT('%', :search, '%'))
        OR LOWER(u.firstName) LIKE LOWER(CONCAT('%', :search, '%'))
        OR LOWER(u.lastName) LIKE LOWER(CONCAT('%', :search, '%'))
        OR LOWER(u.email) LIKE LOWER(CONCAT('%', :search, '%'))
        OR LOWER(u.contactNumber) LIKE LOWER(CONCAT('%', :search, '%'))
    )
    """)
    Page<User> searchByKeywordAndRestaurantIdAndStatus(
        @Param("search") String search,
        @Param("restaurantId") UUID restaurantId,
        @Param("status") EntityStatus status,
        Pageable pageable
    );

    /**
     * Searches for users by keyword within a specific restaurant, matching against full name,
     * first name, last name, email, or contact number. Only returns non-deleted users.
     *
     * @param search      the search keyword to match against multiple user fields (case-insensitive)
     * @param restaurantId the UUID of the restaurant to filter users by
     * @param pageable    pagination and sorting parameters
     * @return paginated list of users matching the search and restaurant criteria
     */
    @Query("""
    SELECT u FROM User u
    WHERE u.isDeleted = false
    AND u.restaurantId = :restaurantId
    AND (
        LOWER(CONCAT(u.firstName, ' ', u.lastName)) LIKE LOWER(CONCAT('%', :search, '%'))
        OR LOWER(u.firstName) LIKE LOWER(CONCAT('%', :search, '%'))
        OR LOWER(u.lastName) LIKE LOWER(CONCAT('%', :search, '%'))
        OR LOWER(u.email) LIKE LOWER(CONCAT('%', :search, '%'))
        OR LOWER(u.contactNumber) LIKE LOWER(CONCAT('%', :search, '%'))
    )
    """)
    Page<User> searchByKeywordAndRestaurantId(
        @Param("search") String search,
        @Param("restaurantId") UUID restaurantId,
        Pageable pageable
    );
    List<User> findAllByRestaurantId(UUID restaurantId);
    
    List<User> findAllByRestaurantIdAndRoleIdAndIsDeletedFalse(UUID restaurantId, UUID roleId);
    
    @Query("SELECT u FROM User u WHERE u.roleId = :roleId AND u.status = :status AND u.isDeleted = false")
    List<User> findAllByRoleIdAndStatusAndIsDeletedFalse(@Param("roleId") UUID roleId, @Param("status") EntityStatus status);
    
    Optional<User> findByUserCodeAndIsDeletedFalseAndStatus(String userCode, EntityStatus status);
    
    /**
     * Count active employees (status = ACTIVE and not deleted)
     */
    @Query("SELECT COUNT(u) FROM User u WHERE u.status = :status AND u.isDeleted = false")
    long countByStatusAndIsDeletedFalse(@Param("status") EntityStatus status);
    
    /**
     * Count active employees by restaurant group (status = ACTIVE and not deleted)
     */
    @Query("SELECT COUNT(u) FROM User u WHERE u.status = :status AND u.isDeleted = false AND u.restaurantId IN (SELECT r.id FROM Restaurant r WHERE r.restaurantGroup.id = :restaurantGroupId AND r.isDeleted = false)")
    long countByRestaurantGroupIdAndStatusAndIsDeletedFalse(
            @Param("restaurantGroupId") UUID restaurantGroupId,
            @Param("status") EntityStatus status);
    
    /**
     * Count active employees by role (status = ACTIVE and not deleted)
     * Returns list of [roleId, count] pairs
     */
    @Query("SELECT u.roleId, COUNT(u) FROM User u WHERE u.status = :status AND u.isDeleted = false AND u.roleId IS NOT NULL GROUP BY u.roleId")
    List<Object[]> countByRoleIdAndStatusAndIsDeletedFalse(@Param("status") EntityStatus status);
    
    /**
     * Count active employees by role and restaurant group (status = ACTIVE and not deleted)
     * Returns list of [roleId, count] pairs
     */
    @Query("SELECT u.roleId, COUNT(u) FROM User u WHERE u.status = :status AND u.isDeleted = false AND u.roleId IS NOT NULL AND u.restaurantId IN (SELECT r.id FROM Restaurant r WHERE r.restaurantGroup.id = :restaurantGroupId AND r.isDeleted = false) GROUP BY u.roleId")
    List<Object[]> countByRoleIdAndRestaurantGroupIdAndStatusAndIsDeletedFalse(
            @Param("restaurantGroupId") UUID restaurantGroupId,
            @Param("status") EntityStatus status);
    
    /**
     * Count active employees by restaurant (status = ACTIVE and not deleted)
     */
    @Query("SELECT COUNT(u) FROM User u WHERE u.status = :status AND u.isDeleted = false AND u.restaurantId = :restaurantId")
    long countByRestaurantIdAndStatusAndIsDeletedFalse(
            @Param("restaurantId") UUID restaurantId,
            @Param("status") EntityStatus status);

    /**
     * Role name for the user in one round trip (avoids separate user + role lookups).
     */
    @Query("""
            SELECT r.name FROM Role r
            WHERE r.id = (SELECT u.roleId FROM User u WHERE u.id = :userId)
            """)
    Optional<String> findRoleNameByUserId(@Param("userId") UUID userId);

    /**
     * Lightweight lookup for user's assigned restaurant.
     * Used by reporting/export APIs to avoid loading the whole User row.
     */
    @Query("SELECT u.restaurantId FROM User u WHERE u.id = :userId")
    Optional<UUID> findRestaurantIdByUserId(@Param("userId") UUID userId);
    
    /**
     * Count active employees by role and restaurant (status = ACTIVE and not deleted)
     * Returns list of [roleId, count] pairs
     */
    @Query("SELECT u.roleId, COUNT(u) FROM User u WHERE u.status = :status AND u.isDeleted = false AND u.restaurantId = :restaurantId AND u.roleId IS NOT NULL GROUP BY u.roleId")
    List<Object[]> countByRoleIdAndRestaurantIdAndStatusAndIsDeletedFalse(
            @Param("restaurantId") UUID restaurantId,
            @Param("status") EntityStatus status);

    @Modifying
    @Transactional
    @Query("""
            UPDATE User u
            SET u.restaurantId = :restaurantId, u.updatedAt = :updatedAt
            WHERE u.id IN :userIds
            """)
    int assignRestaurantForUsers(@Param("restaurantId") UUID restaurantId,
                                 @Param("updatedAt") OffsetDateTime updatedAt,
                                 @Param("userIds") List<UUID> userIds);

    @Modifying
    @Transactional
    @Query("""
            UPDATE User u
            SET u.status = :status, u.updatedAt = :updatedAt
            WHERE u.id IN :userIds AND (u.isStatusLocked = false OR u.isStatusLocked IS NULL)
            """)
    int activateUnlockedUsers(@Param("status") EntityStatus status,
                              @Param("updatedAt") OffsetDateTime updatedAt,
                              @Param("userIds") List<UUID> userIds);
}
