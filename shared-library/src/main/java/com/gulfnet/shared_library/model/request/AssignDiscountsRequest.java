package com.gulfnet.shared_library.model.request;

import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.UUID;

public class AssignDiscountsRequest {
    @NotNull
    private UUID itemId;
    @NotNull
    private List<UUID> discountIds;

    // getters and setters
    public UUID getItemId() { return itemId; }
    public void setItemId(UUID itemId) { this.itemId = itemId; }
    public List<UUID> getDiscountIds() { return discountIds; }
    public void setDiscountIds(List<UUID> discountIds) { this.discountIds = discountIds; }
}
