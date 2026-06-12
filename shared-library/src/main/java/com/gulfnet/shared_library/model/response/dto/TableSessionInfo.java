package com.gulfnet.shared_library.model.response.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.ALWAYS)
public class TableSessionInfo {
    private UUID sessionId;
    private Integer sequenceNo;
    private UUID orderId;
    private String orderNumber;
    private String orderStatus;
    private Integer readyItems;
    private Integer pendingItems;
    private BigDecimal orderSubtotal;
    private OffsetDateTime startTime;
    private OffsetDateTime endTime;
}

