package com.gulfnet.shared_library.model.response.dto;

import lombok.*;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@ToString
public class DiscountStats {
    // Counts by type
    private DiscountTypeStats orderDiscounts;
    private DiscountTypeStats itemDiscounts;
    private DiscountTypeStats categoryDiscounts;
    
    // Total statistics
    private Long totalActiveDiscounts;
    private Long totalUsage;
    private BigDecimal totalRevenueImpact;
}


