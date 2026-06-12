package com.gulfnet.shared_library.model.request;
import com.gulfnet.shared_library.enums.DayOfWeek;
import jakarta.validation.constraints.NotNull;
import java.time.OffsetDateTime;
import java.time.OffsetTime;
import java.util.List;
import java.util.UUID;
import lombok.Data;

@Data
public class AssignDiscountToItemsRequest {
    @NotNull(message = "{discount.assignment.discountId.required}")
    private UUID discountId;
    
    private List<UUID> itemIds;  // Keep existing field for backward compatibility
    private List<UUID> buyItemIds;  // New field for BXGY buy items
    private List<UUID> getItemIds;
    
    @NotNull(message = "{discount.assignment.menuId.required}")
    private UUID menuId;
    
    @NotNull(message = "{discount.assignment.validFrom.required}")
    private OffsetDateTime validFrom;
    
    @NotNull(message = "{discount.assignment.validTo.required}")
    private OffsetDateTime validTo;
    
    private OffsetTime startTime;
    private OffsetTime endTime;
    private List<DayOfWeek> daysOfWeek;
    private List<UUID> restaurantIds; // Optional: if not provided, will be fetched from menu mapping
}
