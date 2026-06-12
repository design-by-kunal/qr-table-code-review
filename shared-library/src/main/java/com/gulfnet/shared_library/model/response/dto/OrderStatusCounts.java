package com.gulfnet.shared_library.model.response.dto;

import lombok.*;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@ToString
public class OrderStatusCounts {
    private Long openOrdersCount;
    private BigDecimal openOrdersPercentage;
    private Long completedOrdersCount;
    private BigDecimal completedOrdersPercentage;
    private Long pendingOrdersCount;
    private BigDecimal pendingOrdersPercentage;
    private Long cancelledOrdersCount;
    private BigDecimal cancelledOrdersPercentage;
    private Long refundedOrdersCount;
    private BigDecimal refundedOrdersPercentage;
}

