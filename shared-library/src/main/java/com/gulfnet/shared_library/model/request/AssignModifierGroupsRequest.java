package com.gulfnet.shared_library.model.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AssignModifierGroupsRequest {

    @NotNull(message = "{assign.modifierGroups.itemId.required}")
    private UUID itemId;

    @NotEmpty(message = "{assign.modifierGroups.groups.required}")
    @Valid
    private List<ModifierGroupAssignment> modifierGroups;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ModifierGroupAssignment {
        @NotNull(message = "{assign.modifierGroups.groupId.required}")
        private UUID modifierGroupId;

        @NotNull(message = "{assign.modifierGroups.sortOrder.required}")
        private Integer sortOrder;
    }
}

