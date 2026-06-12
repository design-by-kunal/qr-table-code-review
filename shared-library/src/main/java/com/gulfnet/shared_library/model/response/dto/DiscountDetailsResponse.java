package com.gulfnet.shared_library.model.response.dto;

import com.gulfnet.shared_library.enums.DayOfWeek;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;
import java.time.OffsetTime;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DiscountDetailsResponse {
    private UUID discountId;
    private List<UUID> categoryIds;  // Only populated if appliedTo = CATEGORY
    private List<UUID> itemIds;      // Only populated if appliedTo = ITEM (regular discounts)
    private List<UUID> buyItemIds;   // Only populated for BXGY discounts
    private List<UUID> getItemIds;   // Only populated for BXGY discounts
    private UUID menuId;
    private OffsetDateTime validFrom;
    private OffsetDateTime validTo;
    private OffsetTime startTime;
    private OffsetTime endTime;
    private List<DayOfWeek> daysOfWeek;
    private Boolean isHide;
}
