package com.gulfnet.shared_library.model.response.dto;

import java.util.List;
import java.util.UUID;

public class DiscountAssignmentListResponse {
    private UUID discountId;
    private List<UUID> assignedItemIds;
    private List<UUID> assignedMenuIds;

    // getters and setters
    public UUID getDiscountId() { return discountId; }
    public void setDiscountId(UUID discountId) { this.discountId = discountId; }
    public List<UUID> getAssignedItemIds() { return assignedItemIds; }
    public void setAssignedItemIds(List<UUID> assignedItemIds) { this.assignedItemIds = assignedItemIds; }
    public List<UUID> getAssignedMenuIds() { return assignedMenuIds; }
    public void setAssignedMenuIds(List<UUID> assignedMenuIds) { this.assignedMenuIds = assignedMenuIds; }
}
