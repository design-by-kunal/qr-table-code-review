package com.gulfnet.shared_library.model.response.dto;

import com.gulfnet.shared_library.entity.Discount;
import com.gulfnet.shared_library.enums.AppliedTo;

import java.math.BigDecimal;

/**
 * Result of discount calculation for an item
 */
public class DiscountCalculationResult {
    private final BigDecimal originalPrice;
    private final BigDecimal finalPrice;
    private final Discount appliedDiscount;
    private final AppliedTo discountLevel;

    public DiscountCalculationResult(BigDecimal originalPrice, BigDecimal finalPrice,
                                    Discount appliedDiscount, AppliedTo discountLevel) {
        this.originalPrice = originalPrice;
        this.finalPrice = finalPrice;
        this.appliedDiscount = appliedDiscount;
        this.discountLevel = discountLevel;
    }
    
    public BigDecimal getOriginalPrice() { return originalPrice; }
    public BigDecimal getFinalPrice() { return finalPrice; }
    public Discount getAppliedDiscount() { return appliedDiscount; }
    public AppliedTo getDiscountLevel() { return discountLevel; }
    public BigDecimal getDiscountAmount() {
        return originalPrice.subtract(finalPrice);
    }
}

