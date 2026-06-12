package com.gulfnet.shared_library.model.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderedComboItemRequest {

    @NotNull(message = "{ordered.combo.item.itemId.required}")
    private UUID itemId;

    @Valid
    private List<OrderedComboItemModifierRequest> orderedItemModifiers;
}
