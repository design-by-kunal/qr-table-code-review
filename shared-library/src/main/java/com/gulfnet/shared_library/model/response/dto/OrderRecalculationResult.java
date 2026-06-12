package com.gulfnet.shared_library.model.response.dto;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Result class for order recalculation operations
 * Contains information about discount removal and recalculation status
 */
public class OrderRecalculationResult {
    private final boolean discountRemoved;
    private final String discountMessage;
    private final Map<UUID, Integer> freeQuantitiesByItemId; // Map of orderedItemId -> freeQuantity

    public OrderRecalculationResult(boolean discountRemoved, String discountMessage) {
        this.discountRemoved = discountRemoved;
        this.discountMessage = discountMessage;
        this.freeQuantitiesByItemId = new HashMap<>();
    }

    public OrderRecalculationResult(boolean discountRemoved, String discountMessage, Map<UUID, Integer> freeQuantitiesByItemId) {
        this.discountRemoved = discountRemoved;
        this.discountMessage = discountMessage;
        this.freeQuantitiesByItemId = freeQuantitiesByItemId != null ? freeQuantitiesByItemId : new HashMap<>();
    }

    public boolean isDiscountRemoved() { 
        return discountRemoved; 
    }
    
    public String getDiscountMessage() { 
        return discountMessage; 
    }
    
    public Map<UUID, Integer> getFreeQuantitiesByItemId() { 
        return freeQuantitiesByItemId; 
    }
}

