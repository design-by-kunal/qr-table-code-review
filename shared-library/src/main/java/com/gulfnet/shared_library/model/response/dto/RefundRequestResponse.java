package com.gulfnet.shared_library.model.response.dto;

import com.gulfnet.shared_library.enums.RefundType;
import com.gulfnet.shared_library.enums.RequestStatus;
import com.gulfnet.shared_library.enums.TransactionStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RefundRequestResponse {
    private UUID refundId;
    private UUID transactionId;
    private UUID orderId;
    private String orderNumber;
    private String transactionNumber;
    private String paymentMethod;
    private String paymentApp;
    private BigDecimal transactionAmount;
    private LocalDate requestDate;
    private UUID raisedBy;
    
    // Refund Type and Method
    private RefundType refundType;
    private String refundMethod;
    
    // Refund Amount Breakdown (from request_data JSON)
    private BigDecimal totalRefundAmount;
    private BigDecimal subtotalRefundAmount;
    private BigDecimal taxRefundAmount;
    private BigDecimal alcoholicTaxRefundAmount;
    private BigDecimal nonAlcoholicTaxRefundAmount;
    private BigDecimal serviceChargeRefundAmount;
    private BigDecimal packingChargeRefundAmount;
    private BigDecimal discountRefundAmount;
    private BigDecimal additionalDiscountRefundAmount;
    
    private List<RefundItemResponse> refundItems;
    private String refundReason;
    private RequestStatus requestStatus;
    private LocalDateTime requestedAt;
    private UUID requestedBy;
    private String requestedByName;
    private String requestedByRole;
    private LocalDateTime reviewedAt;
    private UUID reviewedBy;
    private String reviewedByName;
    private String comments;
    private UUID restaurantId;
    private String restaurantName;
    private TransactionStatus transactionStatus;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RefundItemResponse {
        private UUID itemId;
        private String itemType; // "ITEM" or "COMBO"
        private String itemName;
        private Integer quantity;
        private BigDecimal refundAmount;
    }
}

