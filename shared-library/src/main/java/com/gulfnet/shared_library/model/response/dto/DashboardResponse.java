package com.gulfnet.shared_library.model.response.dto;

import lombok.*;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@ToString
public class DashboardResponse {
    private Long totalRestaurants;
    private Long totalOrders;
    private BigDecimal totalSales;
    private Long activeEmployeesCount;
    private Long totalItems;
    private Long activeDiscountsCount;
    private Long activePromotionsCount;
    private PeriodStatistics periodStatistics;
    
    // Order status counts grouped in a separate object
    private OrderStatusCounts orderStatusCounts;
    
    // Employee role counts grouped in a separate object
    private EmployeeRoleCounts employeeRoleCounts;
    
    // Promotion statistics
    private PromotionStats promotionStats;
    
    // Discount statistics
    private DiscountStats discountStats;
    
    // Menu performance (top 5 items)
    private MenuPerformance menuPerformance;
    
    // Sales statistics (daily, weekly, or monthly)
    private SalesStats salesStats;
    
    // Total refund amount
    private BigDecimal totalRefund;
    
    // Void management (wastage statistics)
    private VoidManagement voidManagement;
}

