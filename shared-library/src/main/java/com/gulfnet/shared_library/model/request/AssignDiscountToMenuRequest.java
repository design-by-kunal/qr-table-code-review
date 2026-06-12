package com.gulfnet.shared_library.model.request;

import com.gulfnet.shared_library.enums.DayOfWeek;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.OffsetDateTime;
import java.time.OffsetTime;
import java.util.List;
import java.util.UUID;

@Data
public class AssignDiscountToMenuRequest {
    @NotNull
    private UUID discountId;
    @NotNull
    private UUID menuId;
    private OffsetDateTime validFrom;
    private OffsetDateTime validTo;
    private OffsetTime startTime;
    private OffsetTime endTime;
    private List<DayOfWeek> daysOfWeek;
    private Boolean isHide;
    private List<UUID> restaurantIds; // Optional: if not provided, will be fetched from menu mapping
} 