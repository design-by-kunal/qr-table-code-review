package com.gulfnet.shared_library.model.response.dto;

import com.gulfnet.shared_library.enums.OrderStatus;
import com.gulfnet.shared_library.enums.TransactionStatus;
import lombok.*;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InitiateCheckoutResponse {
    private UUID orderId;
    private UUID sessionId;
    private OrderStatus orderStatus;
    private UUID transactionId;
    private TransactionStatus transactionStatus;
}
