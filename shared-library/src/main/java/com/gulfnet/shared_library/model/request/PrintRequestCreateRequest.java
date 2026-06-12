package com.gulfnet.shared_library.model.request;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PrintRequestCreateRequest {

    /**
     * Order ID (UUID) associated with this print request.
     */
    private UUID orderId;

    /**
     * Restaurant Table ID (UUID) associated with this print request.
     */
    private UUID restaurantTableId;

    /**
     * Refund ID (UUID) associated with this print request.
     */
    private UUID refundId;
}

