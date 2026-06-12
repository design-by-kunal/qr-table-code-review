package com.gulfnet.shared_library.model.response.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SessionOrderWrapper {
    private UUID sessionId;
    private Integer sessionSequenceNo;
    private List<OrderResponse> orders;  // Use existing OrderResponse DTO
    private OffsetDateTime startTime;
    private OffsetDateTime endTime;
}
