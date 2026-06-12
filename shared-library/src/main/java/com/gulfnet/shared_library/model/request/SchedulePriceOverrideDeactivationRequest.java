package com.gulfnet.shared_library.model.request;

import jakarta.validation.constraints.NotNull;
import lombok.Data;
import java.time.OffsetDateTime;

@Data
public class SchedulePriceOverrideDeactivationRequest {
    
    @NotNull(message = "{price.override.schedule.validTo.required}")
    private OffsetDateTime validTo;
}

