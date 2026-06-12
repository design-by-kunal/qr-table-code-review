package com.gulfnet.shared_library.model.response.dto;

import lombok.*;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@ToString
public class PerformingItem {
    private String itemCode;
    private String itemName;
    private String categoryName;
    private String imageUrl;
    private Long totalSold; // Total quantity ordered
    private BigDecimal revenue; // Total revenue from this item
    private BigDecimal price; // Exact price of the item
}

