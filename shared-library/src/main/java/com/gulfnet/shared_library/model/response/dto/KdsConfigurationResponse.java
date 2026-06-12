package com.gulfnet.shared_library.model.response.dto;

import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class KdsConfigurationResponse {
    private UUID id;
    private UUID userId;
    private String userName;
    private UUID kdsId;
    private String kdsName;
    private String deviceCode;
    private LocalDateTime createdAt;
    private String createdBy;
    private LocalDateTime updatedAt;
    private String updatedBy;
}

