package com.gulfnet.shared_library.model.response.dto;

import lombok.*;

import java.math.BigDecimal;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@ToString
public class MenuDashboardResponse {
    // Total menu item counts
    private Long totalMenuItemCount;
    
    // Published menu count
    private Long publishedMenuCount;
    
    // Average order value
    private BigDecimal avgOrderValue;
    
    // Best Selling Items (top 5 items with quantity sold)
    private List<BestSellingItem> bestSellingItems;
    
    // Item Performance
    private ItemPerformance itemPerformance;
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @ToString
    public static class BestSellingItem {
        private String itemCode;
        private String itemName;
        private String categoryName;
        private String imageUrl;
        private Long quantitySold;
        private BigDecimal revenue;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @ToString
    public static class ItemPerformance {
        // Top-performing item (1 item)
        private PerformingItem topPerformingItem;
        
        // Least-performing item (1 item)
        private PerformingItem leastPerformingItem;
    }
}

