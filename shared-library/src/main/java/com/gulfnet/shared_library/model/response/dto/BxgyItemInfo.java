package com.gulfnet.shared_library.model.response.dto;

import com.gulfnet.shared_library.enums.BxgyRole;

import java.util.UUID;

/**
 * BXGY discount information for a specific ordered item request
 */
public class BxgyItemInfo {
    private final UUID discountApplicationId;
    private final UUID discountId;
    private final BxgyRole bxgyRole;
    private final Integer freeQuantity;

    public BxgyItemInfo(UUID discountApplicationId, UUID discountId, BxgyRole bxgyRole, Integer freeQuantity) {
        this.discountApplicationId = discountApplicationId;
        this.discountId = discountId;
        this.bxgyRole = bxgyRole;
        this.freeQuantity = freeQuantity;
    }

    public UUID getDiscountApplicationId() { return discountApplicationId; }
    public UUID getDiscountId() { return discountId; }
    public BxgyRole getBxgyRole() { return bxgyRole; }
    public Integer getFreeQuantity() { return freeQuantity; }
}
