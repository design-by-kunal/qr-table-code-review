package com.gulfnet.shared_library.model.request;

import jakarta.validation.constraints.NotNull;
import lombok.Data;
import java.time.OffsetDateTime;
import java.util.UUID;

@Data
public class MenuPromotionMappingRequest {
    @NotNull(message = "{promotion.assignment.menuId.required}")
    private UUID menuId;
    
    @NotNull(message = "{menu.promotion.mapping.promotionid.required}")
    private UUID promotionId;
    
    @NotNull(message = "{promotion.assignment.validFrom.required}")
    private OffsetDateTime validFrom;
    
    @NotNull(message = "{promotion.assignment.validTo.required}")
    private OffsetDateTime validTo;
    
    // Optional: Required when promotion type is COMBO
    private UUID comboId;
}
