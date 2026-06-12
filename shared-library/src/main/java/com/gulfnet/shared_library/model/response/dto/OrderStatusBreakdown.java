package com.gulfnet.shared_library.model.response.dto;

import lombok.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@ToString
public class OrderStatusBreakdown {
    private Long placedOrderCount; // PUSHED status
    private Long cookingCount; // Items with COOKING status
    private Long servedCount; // SERVED status
    private Long completedCount; // COMPLETED transactions
}

