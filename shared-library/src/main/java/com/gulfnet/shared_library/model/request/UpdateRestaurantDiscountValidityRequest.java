package com.gulfnet.shared_library.model.request;

import com.gulfnet.shared_library.enums.DayOfWeek;
import com.gulfnet.shared_library.enums.EntityStatus;
import lombok.Data;

import java.time.OffsetDateTime;
import java.time.OffsetTime;
import java.util.List;

@Data
public class UpdateRestaurantDiscountValidityRequest {
    private OffsetDateTime validFrom;
    private OffsetDateTime validTo;
    private OffsetTime startTime;
    private OffsetTime endTime;
    private List<DayOfWeek> daysOfWeek;
    private EntityStatus status;
    private Boolean isHide;
}

