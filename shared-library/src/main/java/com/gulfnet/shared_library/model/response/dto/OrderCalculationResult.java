package com.gulfnet.shared_library.model.response.dto;

import java.math.BigDecimal;

/**
 * Complete order calculation result containing all calculated amounts
 * This ensures consistency across all order-related APIs (calculateOrder, createOrder, updateOrder)
 */
public class OrderCalculationResult {
    private final BigDecimal subTotal; // Subtotal after item/category/BXGY discounts
    private final BigDecimal subtotalAfterDiscount; // Subtotal after order-level discount
    private final BigDecimal taxAmount;
    private final BigDecimal alcoholicTaxAmount;
    private final BigDecimal nonAlcoholicTaxAmount;
    private final BigDecimal alcoholicTaxableAmount;
    private final BigDecimal nonAlcoholicTaxableAmount;
    private final BigDecimal serviceChargeAmount;
    private final BigDecimal packingChargeAmount;
    private final BigDecimal additionalDiscountSavings;
    private final BigDecimal totalAmount;
    private final BigDecimal orderDiscountSavings;
    private final BxgyCalculationResult bxgyResult;

    /**
     * Constructs an order calculation result with all calculated financial amounts.
     * This result ensures consistency across all order-related APIs (calculateOrder, createOrder, updateOrder).
     *
     * @param subTotal                  subtotal after item/category/BXGY discounts are applied
     * @param subtotalAfterDiscount     subtotal after order-level discount is applied
     * @param taxAmount                 calculated tax amount
     * @param serviceChargeAmount        calculated service charge amount
     * @param packingChargeAmount        calculated packing charge amount
     * @param additionalDiscountSavings additional discount savings amount
     * @param totalAmount               final total amount to be paid
     * @param orderDiscountSavings      savings from order-level discounts
     * @param bxgyResult                buy-X-get-Y calculation result details
     */
    public OrderCalculationResult(BigDecimal subTotal, BigDecimal subtotalAfterDiscount, BigDecimal taxAmount,
                                BigDecimal alcoholicTaxAmount, BigDecimal nonAlcoholicTaxAmount,
                                BigDecimal alcoholicTaxableAmount, BigDecimal nonAlcoholicTaxableAmount,
                                BigDecimal serviceChargeAmount, BigDecimal packingChargeAmount,
                                BigDecimal additionalDiscountSavings, BigDecimal totalAmount,
                                BigDecimal orderDiscountSavings, BxgyCalculationResult bxgyResult) {
        this.subTotal = subTotal;
        this.subtotalAfterDiscount = subtotalAfterDiscount;
        this.taxAmount = taxAmount;
        this.alcoholicTaxAmount = alcoholicTaxAmount;
        this.nonAlcoholicTaxAmount = nonAlcoholicTaxAmount;
        this.alcoholicTaxableAmount = alcoholicTaxableAmount;
        this.nonAlcoholicTaxableAmount = nonAlcoholicTaxableAmount;
        this.serviceChargeAmount = serviceChargeAmount;
        this.packingChargeAmount = packingChargeAmount;
        this.additionalDiscountSavings = additionalDiscountSavings;
        this.totalAmount = totalAmount;
        this.orderDiscountSavings = orderDiscountSavings;
        this.bxgyResult = bxgyResult;
    }

    public BigDecimal getSubTotal() { return subTotal; }
    public BigDecimal getSubtotalAfterDiscount() { return subtotalAfterDiscount; }
    public BigDecimal getTaxAmount() { return taxAmount; }
    public BigDecimal getAlcoholicTaxAmount() { return alcoholicTaxAmount; }
    public BigDecimal getNonAlcoholicTaxAmount() { return nonAlcoholicTaxAmount; }
    public BigDecimal getAlcoholicTaxableAmount() { return alcoholicTaxableAmount; }
    public BigDecimal getNonAlcoholicTaxableAmount() { return nonAlcoholicTaxableAmount; }
    public BigDecimal getServiceChargeAmount() { return serviceChargeAmount; }
    public BigDecimal getPackingChargeAmount() { return packingChargeAmount; }
    public BigDecimal getAdditionalDiscountSavings() { return additionalDiscountSavings; }
    public BigDecimal getTotalAmount() { return totalAmount; }
    public BigDecimal getOrderDiscountSavings() { return orderDiscountSavings; }
    public BxgyCalculationResult getBxgyResult() { return bxgyResult; }
}

