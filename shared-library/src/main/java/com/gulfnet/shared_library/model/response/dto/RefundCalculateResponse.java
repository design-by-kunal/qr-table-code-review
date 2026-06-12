 package com.gulfnet.shared_library.model.response.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Response model for refund calculation.
 * Contains proportional refund amounts for tax, service charge, and packaging charges.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RefundCalculateResponse {

    /**
     * Transaction ID for which the calculation was performed.
     */
    private UUID transactionId;

    /**
     * Order ID associated with the transaction.
     */
    private UUID orderId;

    /**
     * Subtotal refund amount (sum of items and combos being refunded).
     */
    private BigDecimal subtotalRefundAmount;

    /**
     * Proportional tax refund amount.
     */
    private BigDecimal taxRefundAmount;

    /**
     * Proportional alcoholic tax refund amount.
     */
    private BigDecimal alcoholicTaxRefundAmount;

    /**
     * Proportional alcoholic consumption-tax taxable base (what tax is calculated from).
     */
    private BigDecimal alcoholicTaxableRefundAmount;

    /**
     * Proportional non-alcoholic tax refund amount.
     */
    private BigDecimal nonAlcoholicTaxRefundAmount;

    /**
     * Proportional non-alcoholic consumption-tax taxable base (what tax is calculated from).
     */
    private BigDecimal nonAlcoholicTaxableRefundAmount;

    /**
     * Proportional service charge refund amount.
     */
    private BigDecimal serviceChargeRefundAmount;

    /**
     * Proportional packing charge refund amount.
     */
    private BigDecimal packingChargeRefundAmount;

    /**
     * Proportional discount refund amount (to be subtracted).
     */
    private BigDecimal discountRefundAmount;

    /**
     * Proportional additional discount refund amount (to be subtracted).
     */
    private BigDecimal additionalDiscountRefundAmount;

    /**
     * Total refund amount (subtotal + tax + service charge + packing - discounts).
     */
    private BigDecimal totalRefundAmount;

    /**
     * Original order subtotal (for reference).
     */
    private BigDecimal originalOrderSubtotal;

    /**
     * Original order tax amount (for reference).
     */
    private BigDecimal originalOrderTaxAmount;

    /**
     * Original order service charge amount (for reference).
     */
    private BigDecimal originalOrderServiceChargeAmount;

    /**
     * Original order packing charge amount (for reference).
     */
    private BigDecimal originalOrderPackingChargeAmount;

    /**
     * Refund ratio used for calculation (subtotalRefundAmount / originalOrderSubtotal).
     */
    private BigDecimal refundRatio;
}

