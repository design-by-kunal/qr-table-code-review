package com.gulfnet.shared_library.model.response.dto;

import com.gulfnet.shared_library.enums.RequestStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PrintRequestResponse {

    private UUID id;
    private UUID restaurantId;
    private String restaurantCode;

    private UUID requestedById;
    private String requestedByName;
    private String requestedByRole;

    private UUID approvedById;
    private String approvedByName;
    private String approvedByRole;

    private RequestStatus requestStatus;
    private String fileUrl;
    private String orderNumber;
    private UUID orderId;
    private UUID restaurantTableId;
    private String tableCode;
    private UUID refundId;
    private String refundNumber;

    private OffsetDateTime requestedAt;
    private OffsetDateTime approvedAt;
    private OffsetDateTime completedAt;

    private String comments;
}


