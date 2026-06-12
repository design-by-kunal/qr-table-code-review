package com.gulfnet.shared_library.model.response.dto;

import com.gulfnet.shared_library.enums.EntityStatus;
import com.gulfnet.shared_library.enums.DietaryPreference;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PriceOverrideImpactedItemResponse {
    
    // Item Details
    private UUID itemId;
    private Double basePrice;
    private BigDecimal overriddenPrice;
    private Boolean outOfStock;
    private EntityStatus itemStatus;
    private DietaryPreference dietaryPreference;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private List<ItemTranslationDto> translations;
}

