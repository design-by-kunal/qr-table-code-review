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
public class OrderedComboGroupRequest {

    @NotNull
    private UUID comboGroupId;

    @Valid
    private List<OrderedComboItemRequest> orderedItems;
}
