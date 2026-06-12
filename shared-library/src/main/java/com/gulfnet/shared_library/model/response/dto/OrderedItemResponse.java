package com.gulfnet.shared_library.model.response.dto;

import com.gulfnet.shared_library.enums.BxgyRole;
import com.gulfnet.shared_library.enums.ItemStatus;
import com.gulfnet.shared_library.enums.AlcoholType;
import com.gulfnet.shared_library.enums.RequestStatus;
import lombok.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderedItemResponse {
    private UUID id;
    private UUID itemId;
    private String itemName;
    private String imageUrl;
    private AlcoholType alcoholType;
    private Integer quantity;
    private Boolean includedInPayment;
    private BigDecimal price;
    private BigDecimal discountedPrice;
    private BigDecimal totalItemAmount;
    private BigDecimal totalDiscountedItemAmount;
    private ItemStatus itemStatus;
    private String notes;
    private String reason;
    private RequestStatus requestStatus;
    private Boolean isAvailable;
    
    /**
     * Array of discount IDs applied to this item.
     * Includes both regular discounts (PERCENT/AMOUNT) and BXGY discounts.
     * When isBuyItem or isGetItem is true, the corresponding BXGY discount ID will always be included.
     */
    private List<UUID> discountIds;
    
    /**
     * Indicates if this item is a "buy" item in a BXGY discount.
     */
    private Boolean isBuyItem;
    
    /**
     * Indicates if this item is a "get" item in a BXGY discount.
     */
    private Boolean isGetItem;
    
    /**
     * Number of free items for this get item in a BXGY discount.
     * Only applicable when isGetItem is true.
     * This field shows how many items are free based on proportional distribution of free quantity across all get items.
     */
    private Integer freeQuantity;
    
    /**
     * BXGY role of this item (BUY or GET).
     * Only set when the item is part of a BXGY discount application.
     * 
     * - BUY: Item that must be purchased to qualify for the discount
     * - GET: Item that is received (free or discounted) as part of the BXGY offer
     * 
     * Use discountApplicationId to link GET items to their corresponding BUY items.
     */
    private BxgyRole bxgyRole;
    
    /**
     * Unique runtime ID for the BXGY discount application.
     * Links all BUY and GET items that belong to the same BXGY application.
     * Only set when the item is part of a BXGY discount.
     * 
     * To identify which GET items belong to which BUY items:
     * 1. Group all items by discountApplicationId
     * 2. Within each group, items with bxgyRole = BUY are the buy items
     * 3. Items with bxgyRole = GET in the same group are the get items for those buy items
     * 
     * Example:
     * - Item A: discountApplicationId = "abc-123", bxgyRole = BUY
     * - Item B: discountApplicationId = "abc-123", bxgyRole = GET
     * - Item C: discountApplicationId = "abc-123", bxgyRole = GET
     * 
     * Items B and C are GET items that belong to BUY item A (all share the same discountApplicationId).
     */
    private UUID discountApplicationId;
    
    /**
     * ID of the BXGY discount rule applied to this item.
     * Only set when the item is part of a BXGY discount.
     */
    private UUID discountId;
    
    private List<OrderedItemModifierResponse> orderedItemModifiers;
}
