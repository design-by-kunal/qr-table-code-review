package com.gulfnet.shared_library.model.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.NotEmpty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TableMoveRequest {
    
    @NotEmpty(message = "{table.move.tableIds.required}")
    private List<UUID> tableIds;
    
    @NotNull(message = "{table.move.targetSectionId.required}")
    private UUID targetSectionId;

    /**
     * Optional. If provided, tables will be moved to this row within the target section.
     * If not provided, tables will be moved to the first (lowest order) row in the target section.
     */
    private UUID targetRowId;
    
    private String reason; // Optional reason for move

    // Some modules compile against sources without Lombok annotation processing enabled.
    // Keep explicit accessors for backward-compatible compilation.
    public UUID getTargetRowId() {
        return targetRowId;
    }

    public void setTargetRowId(UUID targetRowId) {
        this.targetRowId = targetRowId;
    }
}
