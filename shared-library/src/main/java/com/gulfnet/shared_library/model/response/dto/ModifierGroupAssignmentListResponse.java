package com.gulfnet.shared_library.model.response.dto;

import lombok.*;

import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ModifierGroupAssignmentListResponse {
    private UUID itemId;
    private List<AssignedModifierGroup> assignedModifierGroups;
    private Long count;
    private Long total;
    private PaginationMetaData metaData;
    private List<ErrorDto> errors;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AssignedModifierGroup {
        private UUID modifierGroupId;
        private String modifierGroupName;
        private String modifierGroupDescription;
        private Integer sortOrder;
        private Integer status;
    }
}

