package com.gulfnet.shared_library.model.response.dto;

import com.gulfnet.shared_library.enums.DiscountType;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DiscountOfferReportResponse {
    private UUID orderId;
    private String orderNumber;
    private UUID transactionId;
    private String transactionNumber;
    private LocalDateTime transactionDateTime;
    private UUID restaurantId;
    private String restaurantName;
    
    // Order-level discount information
    private UUID discountId;
    private String discountCode;
    private DiscountType discountType;
    private BigDecimal discountValue;
    private BigDecimal discountAmount;
    
    // Additional discount information
    private BigDecimal additionalDiscountValue;
    private DiscountType additionalDiscountType;
    private BigDecimal additionalDiscountAmount;
    private String additionalDiscountReason;
    
    // Order totals
    private BigDecimal subTotal;
    private BigDecimal totalAmount;
    
    // Total discount (order discount + additional discount)
    private BigDecimal totalDiscountAmount;
}

