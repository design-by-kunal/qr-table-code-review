package com.gulfnet.shared_library.model.response.dto;

import lombok.*;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@ToString
public class PeriodStatistics {
    private Long totalOrders;
    private BigDecimal totalSales;
    private Long activeDiscountsCount;
    private Long activePromotionsCount;
}

