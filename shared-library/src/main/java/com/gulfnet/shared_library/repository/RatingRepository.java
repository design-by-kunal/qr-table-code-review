package com.gulfnet.shared_library.repository;

import com.gulfnet.shared_library.entity.Rating;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface RatingRepository extends JpaRepository<Rating, UUID> {
    
    Optional<Rating> findByOrderId(UUID orderId);

    /**
     * Batch fetch ratings for multiple orders.
     * Used by session/table order listing to avoid per-order rating N+1 queries.
     */
    @Query("SELECT DISTINCT ra FROM Rating ra " +
            "LEFT JOIN FETCH ra.order o " +
            "WHERE o.id IN :orderIds")
    List<Rating> findByOrderIds(@Param("orderIds") Collection<UUID> orderIds);
    
    boolean existsByOrderId(UUID orderId);

    /**
     * Get customer rating distribution report
     * Returns: [rating, count]
     * Filters by specific restaurantId only
     * Uses sentinel date '1970-01-01 00:00:00' to handle null date parameters
     * Counts all three rating types (experience, food, service) as separate rating entries
     * Each order contributes up to 3 ratings (one for each type)
     */
    @Query(value = "SELECT " +
           "       rating_value as rating, " +
           "       COUNT(*) as count " +
           "FROM (" +
           "    SELECT r.experience as rating_value " +
           "    FROM rating r " +
           "    JOIN orders o ON o.id = r.order_id " +
           "    JOIN restaurant res ON res.id = o.restaurant_id " +
           "    WHERE r.experience IS NOT NULL " +
           "    AND res.id = CAST(:restaurantId AS uuid) " +
           "    AND (res.is_deleted IS NULL OR res.is_deleted = false) " +
           "    AND (CAST(:startDate AS timestamp) = '1970-01-01 00:00:00'::timestamp OR r.created_at >= CAST(:startDate AS timestamp)) " +
           "    AND (CAST(:endDate AS timestamp) = '1970-01-01 00:00:00'::timestamp OR r.created_at <= CAST(:endDate AS timestamp)) " +
           "    UNION ALL " +
           "    SELECT r.food as rating_value " +
           "    FROM rating r " +
           "    JOIN orders o ON o.id = r.order_id " +
           "    JOIN restaurant res ON res.id = o.restaurant_id " +
           "    WHERE r.food IS NOT NULL " +
           "    AND res.id = CAST(:restaurantId AS uuid) " +
           "    AND (res.is_deleted IS NULL OR res.is_deleted = false) " +
           "    AND (CAST(:startDate AS timestamp) = '1970-01-01 00:00:00'::timestamp OR r.created_at >= CAST(:startDate AS timestamp)) " +
           "    AND (CAST(:endDate AS timestamp) = '1970-01-01 00:00:00'::timestamp OR r.created_at <= CAST(:endDate AS timestamp)) " +
           "    UNION ALL " +
           "    SELECT r.service as rating_value " +
           "    FROM rating r " +
           "    JOIN orders o ON o.id = r.order_id " +
           "    JOIN restaurant res ON res.id = o.restaurant_id " +
           "    WHERE r.service IS NOT NULL " +
           "    AND res.id = CAST(:restaurantId AS uuid) " +
           "    AND (res.is_deleted IS NULL OR res.is_deleted = false) " +
           "    AND (CAST(:startDate AS timestamp) = '1970-01-01 00:00:00'::timestamp OR r.created_at >= CAST(:startDate AS timestamp)) " +
           "    AND (CAST(:endDate AS timestamp) = '1970-01-01 00:00:00'::timestamp OR r.created_at <= CAST(:endDate AS timestamp)) " +
           ") AS all_ratings " +
           "GROUP BY rating_value " +
           "ORDER BY rating_value DESC",
           nativeQuery = true)
    List<Object[]> getCustomerRatingDistribution(
            @Param("restaurantId") UUID restaurantId,
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate);
}

