package com.gulfnet.shared_library.model.request;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AssignCategoriesItemsRequest {
    
    @NotNull(message = "{menu.id.required}")
    private UUID menuId;
    
    @NotNull(message = "{menu.structure.id.required}")
    private UUID menuStructureId;
    
    @NotEmpty(message = "{category.assignments.required}")
    private List<CategoryAssignment> categoryAssignments;
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CategoryAssignment {
        @NotNull(message = "{category.id.required}")
        private UUID categoryId;
        
        @NotEmpty(message = "{category.items.required}")
        private List<UUID> itemIds;
    }
}