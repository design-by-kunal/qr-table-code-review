package com.gulfnet.shared_library.model.request;

import jakarta.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Data;

import java.time.OffsetTime;
import java.util.List;
import java.util.UUID;

import com.gulfnet.shared_library.enums.DayOfWeek;

@Data
@Builder
public class RestaurantOperatingHoursRequest {
    @NotNull(message = "{restaurant.operatingHours.restaurantId.required}")
    private UUID restaurantId;

    // For single request
    private DayOfWeek dayOfWeek;
    private List<Slot> slots;
    private Boolean isClosed;

    // For batch request
    private List<RestaurantOperatingHoursRequest> operatingHours;

    @Data
    @Builder
    public static class Slot {
        private OffsetTime fromTime;
        private OffsetTime toTime;
    }
}
