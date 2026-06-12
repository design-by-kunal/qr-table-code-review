package com.gulfnet.shared_library.model.response.dto;

import com.gulfnet.shared_library.enums.RequestStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TableSectionRequestResponse {
    private UUID entityId; // tableId or sectionId
    private String entityType; // "Table" or "Section"
    private String entityName; // Table identifier or Section name
    private String restaurantName;
    private UUID restaurantId;
    private String requestData; // JSON string with request details
    private RequestStatus requestStatus;
    private LocalDateTime requestedAt;
    private UUID requestedBy;
    private String requestedByName;
    private String requestedByRole;
    private LocalDateTime reviewedAt;
    private UUID reviewedBy;
    private String reviewedByName;
    private String comments;
    private String reason; // Reason for the table/section request
}

