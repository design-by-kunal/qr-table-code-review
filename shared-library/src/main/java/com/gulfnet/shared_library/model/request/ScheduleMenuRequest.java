package com.gulfnet.shared_library.model.request;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ScheduleMenuRequest {
    
    @NotNull(message = "{schedule.menu.menuId.required}")
    private UUID menuId;
    
    @NotEmpty(message = "{schedule.menu.restaurantIds.required}")
    private List<UUID> restaurantIds;
    
    private OffsetDateTime schedulePublishTime;

    public UUID getMenuId() { return menuId; }
    public List<UUID> getRestaurantIds() { return restaurantIds; }
    public OffsetDateTime getSchedulePublishTime() { return schedulePublishTime; }
    public void setMenuId(UUID menuId) { this.menuId = menuId; }
    public void setRestaurantIds(List<UUID> restaurantIds) { this.restaurantIds = restaurantIds; }
    public void setSchedulePublishTime(OffsetDateTime schedulePublishTime) { this.schedulePublishTime = schedulePublishTime; }
}
