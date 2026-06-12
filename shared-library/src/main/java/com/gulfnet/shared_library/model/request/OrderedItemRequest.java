package com.gulfnet.shared_library.model.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Min;
import lombok.*;

import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderedItemRequest {

    /**
     * Optional. When present, indicates this row is updating an existing OrderedItem.
     * When null, the row represents a new OrderedItem to be created.
     */
    private UUID orderedItemId;

    @NotNull
    private UUID itemId;

    /**
     * Array of discount IDs applied to this item.
     * Can include both regular discounts (PERCENT/AMOUNT) and BXGY discounts.
     * Empty list means no discounts applied.
     * When isBuyItem or isGetItem is true, the corresponding BXGY discount ID will always be included.
     */
    private List<UUID> discountIds;

    /**
     * Indicates if this item is a "buy" item in a BXGY discount.
     * When true, this item is part of the "buy X" portion of a BXGY discount.
     */
    private Boolean isBuyItem;

    /**
     * Indicates if this item is a "get" item in a BXGY discount.
     * When true, this item is part of the "get Y" portion of a BXGY discount.
     */
    private Boolean isGetItem;

    /**
     * Number of free items for this get item in a BXGY discount.
     * Only applicable when isGetItem is true.
     * This field is calculated based on proportional distribution of free quantity across all get items.
     */
    private Integer freeQuantity;

    @NotNull
    private Integer quantity;

    private String notes;

    @Valid
    private List<OrderedItemModifierRequest> orderedItemModifiers;
}
