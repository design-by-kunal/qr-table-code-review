package com.gulfnet.shared_library.model.response.dto;

import com.gulfnet.shared_library.enums.DayOfWeek;
import java.time.OffsetDateTime;
import java.time.OffsetTime;
import java.util.List;
import java.util.UUID;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MenuDiscountAssignmentResponse {
    private UUID discountId;
    private UUID menuId;
    
    // Override fields for menu-specific discount validity
    private OffsetDateTime validFrom;
    private OffsetDateTime validTo;
    private OffsetTime startTime;
    private OffsetTime endTime;
    private List<DayOfWeek> daysOfWeek;
    private Boolean isHide;
} 