package com.gulfnet.shared_library.model.response.dto;

import com.gulfnet.shared_library.enums.RefundType;
import com.gulfnet.shared_library.enums.RequestStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Response DTO for refund request.
 * Contains all details about the refund request including transaction, order, items, and amounts.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RaiseRefundResponse {

    // Refund Identification
    private UUID refundId;
    private String refundNumber;
    private RefundType refundType;

    // Transaction & Order Details
    private UUID transactionId;
    private UUID orderId;
    private String orderNumber;
    private String transactionNumber;
    private String paymentMethod;
    private String paymentApp;
    private BigDecimal transactionAmount;

    // Refund Amount Breakdown
    private BigDecimal totalRefundAmount;
    private BigDecimal subtotalRefundAmount;
    private BigDecimal taxRefundAmount;
    private BigDecimal serviceChargeRefundAmount;
    private BigDecimal packingChargeRefundAmount;
    private BigDecimal discountRefundAmount;
    private BigDecimal additionalDiscountRefundAmount;

    // Refund Details
    private String refundReason;
    private String refundMethod;

    // Refund Items
    private List<OrderedItemRefundResponse> orderedItems;
    private List<OrderedComboRefundResponse> orderedCombos;

    // Request Workflow (stored in Transaction entity)
    private RequestStatus requestStatus;
    private LocalDateTime requestedAt;
    private UUID requestedBy;
    private String requestedByName;
    private String requestedByRole;
    private LocalDateTime reviewedAt;
    private UUID reviewedBy;
    private String reviewedByName;
    private String requestComments;

    // Restaurant Details
    private UUID restaurantId;
    private String restaurantName;

    /**
     * Response for refunded ordered item.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class OrderedItemRefundResponse {
        private UUID orderedItemId;
        private String itemName;
        private Integer quantity;
        private BigDecimal refundAmount;
        private String itemReason; // Optional item-level reason
    }

    /**
     * Response for refunded ordered combo.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class OrderedComboRefundResponse {
        private UUID orderedComboId;
        private String comboName;
        private Integer quantity;
        private BigDecimal refundAmount;
        private String itemReason; // Optional combo-level reason
    }
}

