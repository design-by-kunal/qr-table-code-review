package com.gulfnet.shared_library.model.response.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BxgyItemDto {
    private UUID itemId;
    private String itemName;
    private BigDecimal basePrice;
    private BigDecimal price;
    private List<ItemTranslationDto> translations;
    private UUID defaultModifierItemId;
    private String defaultModifierItemName;
    private String presignedUrl;
    private Boolean isAvailable;
}
