package com.gulfnet.shared_library.model.response.dto;

import com.gulfnet.shared_library.entity.Discount;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

/**
 * Information about a BXGY discount application
 */
public class BxgyDiscountInfo {
    private final Discount discount;
    private final int applicableSets;
    private final BigDecimal totalSavings;
    private final Map<UUID, BigDecimal> itemPrices;
    private final int freeGetQuantity;
    private final int paidGetQuantity;
    private final UUID discountApplicationId;

    public BxgyDiscountInfo(Discount discount, int applicableSets, BigDecimal totalSavings, 
                           Map<UUID, BigDecimal> itemPrices, int freeGetQuantity, int paidGetQuantity,
                           UUID discountApplicationId) {
        this.discount = discount;
        this.applicableSets = applicableSets;
        this.totalSavings = totalSavings;
        this.itemPrices = itemPrices;
        this.freeGetQuantity = freeGetQuantity;
        this.paidGetQuantity = paidGetQuantity;
        this.discountApplicationId = discountApplicationId;
    }

    public Discount getDiscount() { return discount; }
    public int getApplicableSets() { return applicableSets; }
    public BigDecimal getTotalSavings() { return totalSavings; }
    public Map<UUID, BigDecimal> getItemPrices() { return itemPrices; }
    public int getFreeGetQuantity() { return freeGetQuantity; }
    public int getPaidGetQuantity() { return paidGetQuantity; }
    public UUID getDiscountApplicationId() { return discountApplicationId; }
}

