package com.gulfnet.shared_library.model.response.dto;

import lombok.*;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@ToString
public class DiscountTypeStats {
    private Long activeCount;
    private Long usageCount;
    private BigDecimal revenueImpact;
}


