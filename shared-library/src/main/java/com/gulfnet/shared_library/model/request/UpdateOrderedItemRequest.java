package com.gulfnet.shared_library.model.request;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;


import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateOrderedItemRequest {
    
    @NotNull
    private UUID menuId;

    @NotNull
    private Integer quantity;

    private String notes;

    @Valid
    private List<OrderedItemModifierRequest> orderedItemModifiers;
    
    private List<UUID> discountIds;
    
    private Boolean isBuyItem;
    
    private Boolean isGetItem;
    
    /**
     * Number of free items for this get item in a BXGY discount.
     * Only applicable when isGetItem is true.
     * This field is calculated based on proportional distribution of free quantity across all get items.
     */
    private Integer freeQuantity;
}
