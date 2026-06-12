package com.gulfnet.shared_library.model.response.dto;

import lombok.*;

import java.math.BigDecimal;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@ToString
public class RestaurantDashboardResponse {
    // Daily metrics
    private BigDecimal dailyRevenue;
    private BigDecimal avgBillValue;
    private Long activeOrders;
    
    // Recently unavailable items (5 most recent)
    private List<UnavailableItem> recentlyUnavailableItems;
    
    // Active managers details (paginated)
    private ManagerListResponse managers;
    
    // Order status breakdown
    private OrderStatusBreakdown orderStatusBreakdown;
    
    // On shift staff (paginated)
    private OnShiftStaffListResponse onShiftStaff;
    
    // Top 1 performing item
    private List<PerformingItem> topPerformingItems;
    
    // Least performing item
    private PerformingItem leastPerformingItem;
    
    // Table occupancy percentage (used tables / total tables * 100)
    private BigDecimal tableOccupancy;
    
    // Peak hour analysis - hourly breakdown of orders with percentages
    private List<PeakHourAnalysis> peakHourAnalysis;
}

