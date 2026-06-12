package com.gulfnet.shared_library.model.response.dto;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class NotificationResponseDto {
    private UUID id;
    private String title;
    private String type;
    private String message;
    private String createdBy;
    private Boolean read;
    private OffsetDateTime createdAt;
    /** Request/entity ID for navigation to request details (GET /api/v1/users/requests/{requestId}/details). Present when notification is request-related. */
    private UUID requestId;
    /** KDS station UUIDs this notification applies to; KDS clients filter by active station. */
    private List<UUID> targetKdsIds;
}

 