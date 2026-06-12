package com.gulfnet.shared_library.model.request;

import com.gulfnet.shared_library.enums.OrderStatus;
import lombok.*;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderStatusPayload {
    private UUID orderId;
    private OrderStatus orderStatus;
}
