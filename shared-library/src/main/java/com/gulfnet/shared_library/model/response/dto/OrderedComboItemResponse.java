package com.gulfnet.shared_library.model.response.dto;

import com.gulfnet.shared_library.enums.AlcoholType;
import lombok.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderedComboItemResponse {
    private UUID id;
    private UUID itemId;
    private String itemName;
    private String imageUrl;
    private AlcoholType alcoholType;
    private BigDecimal price;
    private BigDecimal totalItemAmount;
    private Boolean isDefault;
    private String reason;
    private Boolean isAvailable; // availability flag for the item
    private List<OrderedComboItemModifierResponse> orderedItemModifiers;
}
