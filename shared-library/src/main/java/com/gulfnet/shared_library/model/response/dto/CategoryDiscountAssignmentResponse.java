package com.gulfnet.shared_library.model.response.dto;

import java.util.List;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.OffsetDateTime;
import java.time.OffsetTime;
import com.gulfnet.shared_library.enums.DayOfWeek;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor

public class CategoryDiscountAssignmentResponse {
    private UUID discountId;
     private List<UUID> assignedCategoryIds;
    private UUID menuId;
    private OffsetDateTime validFrom;
    private OffsetDateTime validTo;
    private OffsetTime startTime;
    private OffsetTime endTime;
    private List<DayOfWeek> daysOfWeek;

    
}
