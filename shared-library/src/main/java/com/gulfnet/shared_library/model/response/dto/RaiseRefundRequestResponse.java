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
 * Response DTO for refund request creation (before approval).
 * 
 * This is returned when a refund request is first created.
 * All refund data is stored in Transaction.requestData (JSON format), NOT in Refund/RefundItem entities.
 * 
 * Data Storage Structure:
 * - Transaction.requestData (JSONB): Contains the refund request data in JSON format with all details:
 *   {
 *     "requestType": "REFUND",
 *     "refundType": "FULL" | "PARTIAL",
 *     "refundReason": "Customer dissatisfaction",
 *     "refundAmount": 150.00,
 *     "paymentMethod": "CASH" | "CREDIT_CARD" | "DEBIT_CARD" | "UPI",
 *     "transactionId": "uuid",
 *     "orderId": "uuid",
 *     "orderNumber": "ORD-20251201-0001",
 *     "transactionNumber": "TXN-20251201-0001",
 *     "orderedItems": [
 *       {
 *         "orderedItemId": "uuid",
 *         "itemName": "Pizza Margherita",
 *         "originalQuantity": 2,
 *         "refundQuantity": 2,
 *         "unitPrice": 100.00,
 *         "totalItemAmount": 200.00,
 *         "refundAmount": 200.00,
 *         "itemReason": "optional"
 *       }
 *     ],
 *     "orderedCombos": [
 *       {
 *         "orderedComboId": "uuid",
 *         "comboName": "Family Combo",
 *         "originalQuantity": 1,
 *         "refundQuantity": 1,
 *         "unitPrice": 100.00,
 *         "totalComboAmount": 100.00,
 *         "refundAmount": 100.00,
 *         "itemReason": "optional"
 *       }
 *     ]
 *   }
 * - Transaction.requestStatus: "OPEN"
 * - Transaction.requestedAt, Transaction.requestedBy: Request metadata
 * 
 * This response DTO is built by:
 * 1. Parsing Transaction.requestData JSON to extract refund details
 * 2. Enriching with item/combo names and amounts from OrderedItem/OrderedCombo entities
 * 3. Adding Transaction entity fields (requestStatus, requestedAt, etc.)
 * 
 * Refund and RefundItem entities are created ONLY after manager approval.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RaiseRefundRequestResponse {

    // Request Identification (from Transaction entity)
    private UUID transactionId;
    private UUID orderId;
    private String orderNumber;
    private String transactionNumber;

    // Transaction Details (from Transaction entity)
    private String paymentMethod;
    private String paymentApp;
    private BigDecimal transactionAmount;

    // Refund Data (parsed from Transaction.requestData JSON)
    // These fields match Refund entity structure (will be stored in Refund entity after approval)
    private RefundType refundType; // Maps to Refund.refundType
    private String refundReason; // Maps to Refund.refundReason
    private String refundMethod; // Maps to Refund.refundMethod
    
    // Refund Amount Breakdown (matches Refund entity fields)
    private BigDecimal totalRefundAmount; // Maps to Refund.totalRefundAmount
    private BigDecimal subtotalRefundAmount; // Maps to Refund.subtotalRefundAmount
    private BigDecimal taxRefundAmount; // Maps to Refund.taxRefundAmount
    private BigDecimal alcoholicTaxRefundAmount; // Maps to Refund.alcoholicTaxRefundAmount
    private BigDecimal nonAlcoholicTaxRefundAmount; // Maps to Refund.nonAlcoholicTaxRefundAmount
    private BigDecimal serviceChargeRefundAmount; // Maps to Refund.serviceChargeRefundAmount
    private BigDecimal packingChargeRefundAmount; // Maps to Refund.packingChargeRefundAmount
    private BigDecimal discountRefundAmount; // Maps to Refund.discountRefundAmount
    private BigDecimal additionalDiscountRefundAmount; // Maps to Refund.additionalDiscountRefundAmount
    
    // Refund Items (matches RefundItem entity structure)
    private List<OrderedItemRefundResponse> orderedItems; // From requestData JSON
    private List<OrderedComboRefundResponse> orderedCombos; // From requestData JSON

    // Request Workflow (from Transaction entity fields)
    private RequestStatus requestStatus; // Will be "OPEN"
    private LocalDateTime requestedAt;
    private UUID requestedBy;
    private String requestedByName;
    private String requestedByRole;

    // Restaurant Details
    private UUID restaurantId;
    private String restaurantName;

    /**
     * Response for refunded ordered item.
     * Fields match RefundItem entity structure.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class OrderedItemRefundResponse {
        private UUID orderedItemId; // Maps to RefundItem.orderedItem.id
        private Integer quantity; // Maps to RefundItem.quantity
        private BigDecimal refundAmount; // Maps to RefundItem.refundAmount
        private String imageUrl; // Item image URL (from Item entity)
    }

    /**
     * Response for refunded ordered combo.
     * Fields match RefundItem entity structure.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class OrderedComboRefundResponse {
        private UUID orderedComboId; // Maps to RefundItem.orderedCombo.id
        private Integer quantity; // Maps to RefundItem.quantity
        private BigDecimal refundAmount; // Maps to RefundItem.refundAmount
        private String imageUrl; // Combo image URL (from Combo entity)
    }
}

