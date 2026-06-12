package com.gulfnet.shared_library.model.request;

import com.gulfnet.shared_library.enums.EntityStatus;
import lombok.Data;

import java.time.OffsetDateTime;

@Data
public class UpdateRestaurantPromotionValidityRequest {
    private OffsetDateTime validFrom;
    private OffsetDateTime validTo;
    private EntityStatus status;
}


