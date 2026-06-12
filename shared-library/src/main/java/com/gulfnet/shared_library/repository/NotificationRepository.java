package com.gulfnet.shared_library.repository;

import com.gulfnet.shared_library.entity.Notification;
import com.gulfnet.shared_library.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Repository
public interface NotificationRepository extends JpaRepository<Notification, UUID> {

    Page<Notification> findByUser_IdOrderByCreatedAtDesc(UUID userId, Pageable pageable);
 
    Page<Notification> findByUser_IdAndTypeInOrderByCreatedAtDesc(
            UUID userId, Iterable<String> types, Pageable pageable);
 
    long countByUser_IdAndReadFalse(UUID userId);
    
    /**
     * Find all notifications for a specific user
     */
    Page<Notification> findByUserOrderByCreatedAtDesc(User user, Pageable pageable);
    
    /**
     * Find all notifications for a specific user ID
     */
    Page<Notification> findByUserIdOrderByCreatedAtDesc(UUID userId, Pageable pageable);
    
    /**
     * Find unread notifications for a specific user
     */
    Page<Notification> findByUserAndReadFalseOrderByCreatedAtDesc(User user, Pageable pageable);
    
    /**
     * Find notifications by type for a specific user
     */
    Page<Notification> findByUserAndTypeOrderByCreatedAtDesc(User user, String type, Pageable pageable);
    
    /**
     * Count unread notifications for a user
     */
    long countByUserAndReadFalse(User user);
    
    /**
     * Find notifications by type (for role-based filtering)
     * This can be used to find all notifications of a specific type across users
     */
    @Query("SELECT n FROM Notification n WHERE n.type = :type ORDER BY n.createdAt DESC")
    Page<Notification> findByTypeOrderByCreatedAtDesc(@Param("type") String type, Pageable pageable);
    
    /**
     * Find read notifications for a specific user
     */
    Page<Notification> findByUser_IdAndReadIsTrueOrderByCreatedAtDesc(UUID userId, Pageable pageable);
    
    /**
     * Find unread notifications for a specific user
     */
    Page<Notification> findByUser_IdAndReadIsFalseOrderByCreatedAtDesc(UUID userId, Pageable pageable);
    
    /**
     * Find read notifications for a specific user with type filter
     */
    Page<Notification> findByUser_IdAndReadIsTrueAndTypeInOrderByCreatedAtDesc(
            UUID userId, Iterable<String> types, Pageable pageable);
    
    /**
     * Find unread notifications for a specific user with type filter
     */
    Page<Notification> findByUser_IdAndReadIsFalseAndTypeInOrderByCreatedAtDesc(
            UUID userId, Iterable<String> types, Pageable pageable);
    
    /**
     * Paginated notification IDs for a user (step 1 of two-step fetch; avoids JOIN FETCH + Page issues).
     * Sorting is handled by Pageable.
     */
    @Query("SELECT n.id FROM Notification n WHERE n.user.id = :userId")
    Page<UUID> findIdsByUser_Id(@Param("userId") UUID userId, Pageable pageable);

    @Query("SELECT n.id FROM Notification n WHERE n.user.id = :userId AND n.type IN :types")
    Page<UUID> findIdsByUser_IdAndTypeIn(
            @Param("userId") UUID userId, @Param("types") Iterable<String> types, Pageable pageable);

    @Query("SELECT n.id FROM Notification n WHERE n.user.id = :userId AND n.read = true")
    Page<UUID> findIdsByUser_IdAndReadIsTrue(@Param("userId") UUID userId, Pageable pageable);

    @Query("SELECT n.id FROM Notification n WHERE n.user.id = :userId AND n.read = false")
    Page<UUID> findIdsByUser_IdAndReadIsFalse(@Param("userId") UUID userId, Pageable pageable);

    @Query("SELECT n.id FROM Notification n WHERE n.user.id = :userId AND n.read = true AND n.type IN :types")
    Page<UUID> findIdsByUser_IdAndReadIsTrueAndTypeIn(
            @Param("userId") UUID userId, @Param("types") Iterable<String> types, Pageable pageable);

    @Query("SELECT n.id FROM Notification n WHERE n.user.id = :userId AND n.read = false AND n.type IN :types")
    Page<UUID> findIdsByUser_IdAndReadIsFalseAndTypeIn(
            @Param("userId") UUID userId, @Param("types") Iterable<String> types, Pageable pageable);

    /**
     * Load notifications with {@code createdBy} in one query (step 2). Caller should preserve order from step 1.
     * Uses {@code LEFT JOIN FETCH} because {@code created_by} is optional; an inner join would drop rows and break pagination.
     */
    @Query("SELECT n FROM Notification n LEFT JOIN FETCH n.createdBy WHERE n.id IN :ids")
    List<Notification> findByIdInWithCreatedBy(@Param("ids") List<UUID> ids);
    
    /**
     * Find notifications by type and date range for duplicate alert detection.
     * This is used to check if an alert of a specific type was already sent for a restaurant on a given date.
     */
    @Query("SELECT n FROM Notification n WHERE n.type = :type " +
           "AND n.createdAt >= :startDate AND n.createdAt < :endDate " +
           "ORDER BY n.createdAt DESC")
    List<Notification> findByTypeAndCreatedAtBetween(
            @Param("type") String type,
            @Param("startDate") OffsetDateTime startDate,
            @Param("endDate") OffsetDateTime endDate);
}

