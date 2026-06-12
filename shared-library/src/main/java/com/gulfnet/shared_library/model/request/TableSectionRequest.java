package com.gulfnet.shared_library.model.request;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TableSectionRequest {
    
    @NotNull(message = "{table.section.request.entity.type.required}")
    private String entityType; // "Table" or "Section"
    
    @NotNull(message = "{table.section.request.entity.id.required}")
    private UUID entityId; // tableId or sectionId
    
    @NotNull(message = "{table.section.request.data.required}")
    private String requestData; // JSON string with request details (what changes are being requested)
    
    private String comments; // Optional reason for the request
}

