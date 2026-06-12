package com.gulfnet.shared_library.model.request;

import com.gulfnet.shared_library.enums.RefundType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.UUID;

/**
 * Request model for raising a refund request.
 * 
 * Supports both FULL and PARTIAL refunds:
 * - FULL: Refund entire transaction, items lists are optional
 * - PARTIAL: Refund specific items/combos, at least one list must be non-empty
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RaiseRefundRequest {

    /**
     * Type of refund requested.
     * - FULL: Entire transaction/order is refunded
     * - PARTIAL: Only specific items/quantities are refunded
     */
    @NotNull(message = "{refund.request.type.required}")
    private RefundType refundType;

    /**
     * Ordered items to refund (for PARTIAL refunds).
     * For FULL refunds, this can be null/empty and backend will compute from order.
     */
    private List<OrderedItemRefund> orderedItems;

    /**
     * Ordered combos to refund (for PARTIAL refunds).
     * For FULL refunds, this can be null/empty and backend will compute from order.
     */
    private List<OrderedComboRefund> orderedCombos;

    /**
     * Mandatory high-level refund reason.
     */
    @NotBlank(message = "{refund.request.reason.required}")
    private String refundReason;

    /**
     * Request for refunding a specific ordered item.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class OrderedItemRefund {
        /**
         * ID of the OrderedItem to refund.
         */
        @NotNull(message = "{refund.request.ordered.item.id.required}")
        private UUID orderedItemId;

        /**
         * Quantity to refund (can be partial, e.g., 1 out of 3).
         */
        @NotNull(message = "{refund.request.quantity.required}")
        private Integer quantity;

        /**
         * Optional item-level reason (if different from overall refundReason).
         */
        private String itemReason;
    }

    /**
     * Request for refunding a specific ordered combo.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class OrderedComboRefund {
        /**
         * ID of the OrderedCombo to refund.
         */
        @NotNull(message = "{refund.request.ordered.combo.id.required}")
        private UUID orderedComboId;

        /**
         * Quantity to refund (can be partial, e.g., 1 out of 2).
         */
        @NotNull(message = "{refund.request.quantity.required}")
        private Integer quantity;

        /**
         * Optional combo-level reason (if different from overall refundReason).
         */
        private String itemReason;
    }
}

