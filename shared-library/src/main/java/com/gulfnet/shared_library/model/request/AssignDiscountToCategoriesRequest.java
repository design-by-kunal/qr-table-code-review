package com.gulfnet.shared_library.model.request;

import com.gulfnet.shared_library.enums.DayOfWeek;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.OffsetDateTime;
import java.time.OffsetTime;
import java.util.List;
import java.util.UUID;

@Data
public class AssignDiscountToCategoriesRequest {
    @NotNull(message = "{discount.assignment.discountId.required}")
    private UUID discountId;
    
    private List<UUID> categoryIds;
    
    @NotNull(message = "{discount.assignment.menuId.required}")
    private UUID menuId;
    
    // Override fields for menu-specific discount validity
    @NotNull(message = "{discount.assignment.validFrom.required}")
    private OffsetDateTime validFrom;
    
    @NotNull(message = "{discount.assignment.validTo.required}")
    private OffsetDateTime validTo;
    
    private OffsetTime startTime;
    private OffsetTime endTime;
    private List<DayOfWeek> daysOfWeek;
    private List<UUID> restaurantIds; // Optional: if not provided, will be fetched from menu mapping
}
