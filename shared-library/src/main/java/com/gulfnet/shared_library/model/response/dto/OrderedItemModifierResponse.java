package com.gulfnet.shared_library.model.response.dto;

import lombok.*;

import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderedItemModifierResponse {
    private UUID modifierGroupId;
    private String modifierGroupName;
    private List<ModifierItemResponse> modifierItems;
}
