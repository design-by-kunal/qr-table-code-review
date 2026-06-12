package com.gulfnet.shared_library.model.request;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.UUID;

/**
 * Request model for calculating refund amounts.
 * Calculates proportional tax, service charge, and packaging charges
 * based on the specified items and combos.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RefundCalculateRequest {

    /**
     * Transaction ID to calculate refund for.
     */
    @NotNull(message = "{refund.calculate.transaction.id.required}")
    private UUID transactionId;

    /**
     * List of ordered item IDs to include in the refund calculation.
     * If null or empty, no items will be included.
     */
    private List<ItemRefundCalculate> orderedItems;

    /**
     * List of ordered combo IDs to include in the refund calculation.
     * If null or empty, no combos will be included.
     */
    private List<ComboRefundCalculate> orderedCombos;

    /**
     * Request for calculating refund for a specific ordered item.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ItemRefundCalculate {
        /**
         * ID of the OrderedItem to calculate refund for.
         */
        @NotNull(message = "{refund.calculate.ordered.item.id.required}")
        private UUID orderedItemId;

        /**
         * Quantity to calculate refund for (optional, defaults to full quantity).
         * If null, will use the full quantity of the item.
         */
        private Integer quantity;
    }

    /**
     * Request for calculating refund for a specific ordered combo.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ComboRefundCalculate {
        /**
         * ID of the OrderedCombo to calculate refund for.
         */
        @NotNull(message = "{refund.calculate.ordered.combo.id.required}")
        private UUID orderedComboId;

        /**
         * Quantity to calculate refund for (optional, defaults to full quantity).
         * If null, will use the full quantity of the combo.
         */
        private Integer quantity;
    }
}

