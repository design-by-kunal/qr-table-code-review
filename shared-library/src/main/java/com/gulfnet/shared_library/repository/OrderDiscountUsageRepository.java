package com.gulfnet.shared_library.repository;

import com.gulfnet.shared_library.entity.OrderDiscountUsage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Repository
public interface OrderDiscountUsageRepository extends JpaRepository<OrderDiscountUsage, UUID> {
    
    /**
     * Sum discount amount for ITEM discounts by restaurant group
     */
    @Query("SELECT COALESCE(SUM(odu.discountAmount), 0) FROM OrderDiscountUsage odu " +
           "JOIN odu.restaurant r " +
           "JOIN odu.transaction t " +
           "LEFT JOIN Refund rf ON rf.transaction.id = t.id " +
           "WHERE r.restaurantGroup.id = :restaurantGroupId " +
           "AND odu.appliedTo = 'ITEM' " +
           "AND t.transactionStatus IN ('COMPLETED', 'REFUNDED', 'PARTIALLY_REFUNDED') " +
           "AND (r.isDeleted IS NULL OR r.isDeleted = false) " +
           "AND (rf.refundType IS NULL OR rf.refundType != 'FULL')")
    BigDecimal sumItemDiscountRevenueImpactByRestaurantGroupId(
            @Param("restaurantGroupId") UUID restaurantGroupId);
    
    /**
     * Sum discount amount for ITEM discounts by restaurant group within date range
     */
    @Query("SELECT COALESCE(SUM(odu.discountAmount), 0) FROM OrderDiscountUsage odu " +
           "JOIN odu.restaurant r " +
           "JOIN odu.transaction t " +
           "LEFT JOIN Refund rf ON rf.transaction.id = t.id " +
           "WHERE r.restaurantGroup.id = :restaurantGroupId " +
           "AND odu.appliedTo = 'ITEM' " +
           "AND t.transactionStatus IN ('COMPLETED', 'REFUNDED', 'PARTIALLY_REFUNDED') " +
           "AND t.createdAt >= :startDate " +
           "AND t.createdAt <= :endDate " +
           "AND (r.isDeleted IS NULL OR r.isDeleted = false) " +
           "AND (rf.refundType IS NULL OR rf.refundType != 'FULL')")
    BigDecimal sumItemDiscountRevenueImpactByRestaurantGroupIdAndDateRange(
            @Param("restaurantGroupId") UUID restaurantGroupId,
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate);
    
    /**
     * Sum discount amount for ITEM discounts (all-time, no restaurant group filter)
     */
    @Query("SELECT COALESCE(SUM(odu.discountAmount), 0) FROM OrderDiscountUsage odu " +
           "JOIN odu.transaction t " +
           "LEFT JOIN Refund rf ON rf.transaction.id = t.id " +
           "WHERE odu.appliedTo = 'ITEM' " +
           "AND t.transactionStatus IN ('COMPLETED', 'REFUNDED', 'PARTIALLY_REFUNDED') " +
           "AND (rf.refundType IS NULL OR rf.refundType != 'FULL')")
    BigDecimal sumItemDiscountRevenueImpact();
    
    /**
     * Sum discount amount for ITEM discounts within date range (no restaurant group filter)
     */
    @Query("SELECT COALESCE(SUM(odu.discountAmount), 0) FROM OrderDiscountUsage odu " +
           "JOIN odu.transaction t " +
           "LEFT JOIN Refund rf ON rf.transaction.id = t.id " +
           "WHERE odu.appliedTo = 'ITEM' " +
           "AND t.transactionStatus IN ('COMPLETED', 'REFUNDED', 'PARTIALLY_REFUNDED') " +
           "AND t.createdAt >= :startDate " +
           "AND t.createdAt <= :endDate " +
           "AND (rf.refundType IS NULL OR rf.refundType != 'FULL')")
    BigDecimal sumItemDiscountRevenueImpactByDateRange(
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate);
    
    /**
     * Sum discount amount for CATEGORY discounts by restaurant group
     */
    @Query("SELECT COALESCE(SUM(odu.discountAmount), 0) FROM OrderDiscountUsage odu " +
           "JOIN odu.restaurant r " +
           "JOIN odu.transaction t " +
           "LEFT JOIN Refund rf ON rf.transaction.id = t.id " +
           "WHERE r.restaurantGroup.id = :restaurantGroupId " +
           "AND odu.appliedTo = 'CATEGORY' " +
           "AND t.transactionStatus IN ('COMPLETED', 'REFUNDED', 'PARTIALLY_REFUNDED') " +
           "AND (r.isDeleted IS NULL OR r.isDeleted = false) " +
           "AND (rf.refundType IS NULL OR rf.refundType != 'FULL')")
    BigDecimal sumCategoryDiscountRevenueImpactByRestaurantGroupId(
            @Param("restaurantGroupId") UUID restaurantGroupId);
    
    /**
     * Sum discount amount for CATEGORY discounts by restaurant group within date range
     */
    @Query("SELECT COALESCE(SUM(odu.discountAmount), 0) FROM OrderDiscountUsage odu " +
           "JOIN odu.restaurant r " +
           "JOIN odu.transaction t " +
           "LEFT JOIN Refund rf ON rf.transaction.id = t.id " +
           "WHERE r.restaurantGroup.id = :restaurantGroupId " +
           "AND odu.appliedTo = 'CATEGORY' " +
           "AND t.transactionStatus IN ('COMPLETED', 'REFUNDED', 'PARTIALLY_REFUNDED') " +
           "AND t.createdAt >= :startDate " +
           "AND t.createdAt <= :endDate " +
           "AND (r.isDeleted IS NULL OR r.isDeleted = false) " +
           "AND (rf.refundType IS NULL OR rf.refundType != 'FULL')")
    BigDecimal sumCategoryDiscountRevenueImpactByRestaurantGroupIdAndDateRange(
            @Param("restaurantGroupId") UUID restaurantGroupId,
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate);
    
    /**
     * Sum discount amount for CATEGORY discounts (all-time, no restaurant group filter)
     */
    @Query("SELECT COALESCE(SUM(odu.discountAmount), 0) FROM OrderDiscountUsage odu " +
           "JOIN odu.transaction t " +
           "LEFT JOIN Refund rf ON rf.transaction.id = t.id " +
           "WHERE odu.appliedTo = 'CATEGORY' " +
           "AND t.transactionStatus IN ('COMPLETED', 'REFUNDED', 'PARTIALLY_REFUNDED') " +
           "AND (rf.refundType IS NULL OR rf.refundType != 'FULL')")
    BigDecimal sumCategoryDiscountRevenueImpact();
    
    /**
     * Sum discount amount for CATEGORY discounts within date range (no restaurant group filter)
     */
    @Query("SELECT COALESCE(SUM(odu.discountAmount), 0) FROM OrderDiscountUsage odu " +
           "JOIN odu.transaction t " +
           "LEFT JOIN Refund rf ON rf.transaction.id = t.id " +
           "WHERE odu.appliedTo = 'CATEGORY' " +
           "AND t.transactionStatus IN ('COMPLETED', 'REFUNDED', 'PARTIALLY_REFUNDED') " +
           "AND t.createdAt >= :startDate " +
           "AND t.createdAt <= :endDate " +
           "AND (rf.refundType IS NULL OR rf.refundType != 'FULL')")
    BigDecimal sumCategoryDiscountRevenueImpactByDateRange(
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate);
    
    /**
     * Sum discount amount for ITEM discounts by restaurant
     */
    @Query("SELECT COALESCE(SUM(odu.discountAmount), 0) FROM OrderDiscountUsage odu " +
           "JOIN odu.restaurant r " +
           "JOIN odu.transaction t " +
           "LEFT JOIN Refund rf ON rf.transaction.id = t.id " +
           "WHERE r.id = :restaurantId " +
           "AND odu.appliedTo = 'ITEM' " +
           "AND t.transactionStatus IN ('COMPLETED', 'REFUNDED', 'PARTIALLY_REFUNDED') " +
           "AND (r.isDeleted IS NULL OR r.isDeleted = false) " +
           "AND (rf.refundType IS NULL OR rf.refundType != 'FULL')")
    BigDecimal sumItemDiscountRevenueImpactByRestaurantId(
            @Param("restaurantId") UUID restaurantId);
    
    /**
     * Sum discount amount for ITEM discounts by restaurant within date range
     */
    @Query("SELECT COALESCE(SUM(odu.discountAmount), 0) FROM OrderDiscountUsage odu " +
           "JOIN odu.restaurant r " +
           "JOIN odu.transaction t " +
           "LEFT JOIN Refund rf ON rf.transaction.id = t.id " +
           "WHERE r.id = :restaurantId " +
           "AND odu.appliedTo = 'ITEM' " +
           "AND t.transactionStatus IN ('COMPLETED', 'REFUNDED', 'PARTIALLY_REFUNDED') " +
           "AND t.createdAt >= :startDate " +
           "AND t.createdAt <= :endDate " +
           "AND (r.isDeleted IS NULL OR r.isDeleted = false) " +
           "AND (rf.refundType IS NULL OR rf.refundType != 'FULL')")
    BigDecimal sumItemDiscountRevenueImpactByRestaurantIdAndDateRange(
            @Param("restaurantId") UUID restaurantId,
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate);
    
    /**
     * Sum discount amount for CATEGORY discounts by restaurant
     */
    @Query("SELECT COALESCE(SUM(odu.discountAmount), 0) FROM OrderDiscountUsage odu " +
           "JOIN odu.restaurant r " +
           "JOIN odu.transaction t " +
           "LEFT JOIN Refund rf ON rf.transaction.id = t.id " +
           "WHERE r.id = :restaurantId " +
           "AND odu.appliedTo = 'CATEGORY' " +
           "AND t.transactionStatus IN ('COMPLETED', 'REFUNDED', 'PARTIALLY_REFUNDED') " +
           "AND (r.isDeleted IS NULL OR r.isDeleted = false) " +
           "AND (rf.refundType IS NULL OR rf.refundType != 'FULL')")
    BigDecimal sumCategoryDiscountRevenueImpactByRestaurantId(
            @Param("restaurantId") UUID restaurantId);
    
    /**
     * Sum discount amount for CATEGORY discounts by restaurant within date range
     */
    @Query("SELECT COALESCE(SUM(odu.discountAmount), 0) FROM OrderDiscountUsage odu " +
           "JOIN odu.restaurant r " +
           "JOIN odu.transaction t " +
           "LEFT JOIN Refund rf ON rf.transaction.id = t.id " +
           "WHERE r.id = :restaurantId " +
           "AND odu.appliedTo = 'CATEGORY' " +
           "AND t.transactionStatus IN ('COMPLETED', 'REFUNDED', 'PARTIALLY_REFUNDED') " +
           "AND t.createdAt >= :startDate " +
           "AND t.createdAt <= :endDate " +
           "AND (r.isDeleted IS NULL OR r.isDeleted = false) " +
           "AND (rf.refundType IS NULL OR rf.refundType != 'FULL')")
    BigDecimal sumCategoryDiscountRevenueImpactByRestaurantIdAndDateRange(
            @Param("restaurantId") UUID restaurantId,
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate);
}

