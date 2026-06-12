package com.gulfnet.shared_library.model.response.dto;

import com.gulfnet.shared_library.enums.DayOfWeek;
import java.time.OffsetDateTime;
import java.time.OffsetTime;
import java.util.List;
import java.util.UUID;

public class ItemDiscountAssignmentResponse {
    private UUID discountId;
    private List<UUID> assignedItemIds;
    private UUID menuId;
    
    // Override fields for menu-specific discount validity
    private OffsetDateTime validFrom;
    private OffsetDateTime validTo;
    private OffsetTime startTime;
    private OffsetTime endTime;
    private List<DayOfWeek> daysOfWeek;

    public UUID getDiscountId() { return discountId; }
    public void setDiscountId(UUID discountId) { this.discountId = discountId; }
    public List<UUID> getAssignedItemIds() { return assignedItemIds; }
    public void setAssignedItemIds(List<UUID> assignedItemIds) { this.assignedItemIds = assignedItemIds; }
    public UUID getMenuId() { return menuId; }
    public void setMenuId(UUID menuId) { this.menuId = menuId; }
    
    public OffsetDateTime getValidFrom() { return validFrom; }
    public void setValidFrom(OffsetDateTime validFrom) { this.validFrom = validFrom; }
    public OffsetDateTime getValidTo() { return validTo; }
    public void setValidTo(OffsetDateTime validTo) { this.validTo = validTo; }
    public OffsetTime getStartTime() { return startTime; }
    public void setStartTime(OffsetTime startTime) { this.startTime = startTime; }
    public OffsetTime getEndTime() { return endTime; }
    public void setEndTime(OffsetTime endTime) { this.endTime = endTime; }
    public List<DayOfWeek> getDaysOfWeek() { return daysOfWeek; }
    public void setDaysOfWeek(List<DayOfWeek> daysOfWeek) { this.daysOfWeek = daysOfWeek; }
}
