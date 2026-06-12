package com.gulfnet.shared_library.model.response.dto;

import lombok.*;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@ToString
public class TopItemPerformance {
    private String itemCode;
    private String itemName;
    private String categoryName;
    private Long orderCount; // Total quantity ordered
    private BigDecimal revenue; // Sum of totalDiscountedItemAmount
}

