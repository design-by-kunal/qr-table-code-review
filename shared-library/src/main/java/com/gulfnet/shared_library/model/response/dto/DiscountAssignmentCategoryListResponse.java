package com.gulfnet.shared_library.model.response.dto;

import java.util.List;
import java.util.UUID;

public class DiscountAssignmentCategoryListResponse {
    private UUID discountId;
    private List<UUID> assignedCategoryIds;
    private List<UUID> assignedMenuIds;

    // getters and setters
    public UUID getDiscountId() { return discountId; }
    public void setDiscountId(UUID discountId) { this.discountId = discountId; }
    public List<UUID> getAssignedCategoryIds() { return assignedCategoryIds; }
    public void setAssignedCategoryIds(List<UUID> assignedCategoryIds) { this.assignedCategoryIds = assignedCategoryIds; }
    public List<UUID> getAssignedMenuIds() { return assignedMenuIds; }
    public void setAssignedMenuIds(List<UUID> assignedMenuIds) { this.assignedMenuIds = assignedMenuIds; }
}
