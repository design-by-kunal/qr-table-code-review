package com.gulfnet.shared_library.model.request;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderedComboItemModifierRequest {

    @NotNull
    private UUID modifierGroupId;

    @NotEmpty
    private List<UUID> modifierItemIds;
}
