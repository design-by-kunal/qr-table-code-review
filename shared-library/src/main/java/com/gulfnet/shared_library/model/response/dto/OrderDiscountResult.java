package com.gulfnet.shared_library.model.response.dto;

import com.gulfnet.shared_library.entity.Discount;

import java.math.BigDecimal;

/**
 * Result of order-level discount application
 */
public class OrderDiscountResult {
    private final BigDecimal finalSubTotal;
    private final BigDecimal discountSavings;
    private final Discount appliedDiscount;

    public OrderDiscountResult(BigDecimal finalSubTotal, BigDecimal discountSavings, Discount appliedDiscount) {
        this.finalSubTotal = finalSubTotal;
        this.discountSavings = discountSavings;
        this.appliedDiscount = appliedDiscount;
    }

    public BigDecimal getFinalSubTotal() { return finalSubTotal; }
    public BigDecimal getDiscountSavings() { return discountSavings; }
    public Discount getAppliedDiscount() { return appliedDiscount; }
}

