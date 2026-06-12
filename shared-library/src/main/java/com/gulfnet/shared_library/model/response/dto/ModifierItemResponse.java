package com.gulfnet.shared_library.model.response.dto;


import lombok.*;

import java.math.BigDecimal;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ModifierItemResponse {
    private UUID id;
    private UUID modifierItemId;
    private String modifierItemName;
    private BigDecimal price;
}