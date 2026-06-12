package com.gulfnet.shared_library.model.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Min;
import lombok.*;

import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateOrderedComboRequest {
    
    @NotNull
    private UUID menuId;

    @NotNull
    @Min(value = 1)
    private Integer quantity;

    private String notes;

    @Valid
    private List<OrderedComboGroupRequest> comboGroups;
}

