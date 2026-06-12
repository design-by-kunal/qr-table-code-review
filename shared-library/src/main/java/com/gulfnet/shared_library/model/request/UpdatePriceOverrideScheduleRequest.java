package com.gulfnet.shared_library.model.request;

import jakarta.validation.constraints.NotNull;
import lombok.Data;
import java.time.OffsetDateTime;

@Data
public class UpdatePriceOverrideScheduleRequest {
    
    @NotNull(message = "{price.override.schedule.validFrom.required}")
    private OffsetDateTime validFrom;
    
    // validTo is optional - can be null for immediate activation without end time
    private OffsetDateTime validTo;
}

