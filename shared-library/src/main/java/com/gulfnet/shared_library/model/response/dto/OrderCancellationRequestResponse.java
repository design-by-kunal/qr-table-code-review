package com.gulfnet.shared_library.model.response.dto;

import com.gulfnet.shared_library.enums.OrderStatus;
import com.gulfnet.shared_library.enums.RequestStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderCancellationRequestResponse {
    private UUID orderId;
    private String orderNumber;
    private String transactionNumber;
    private OrderStatus currentOrderStatus;
    private String cancellationReason;
    private RequestStatus requestStatus;
    private LocalDateTime requestedAt;
    private UUID requestedBy;
    private String requestedByName;
    private String requestedByRole;
    private LocalDateTime reviewedAt;
    private UUID reviewedBy;
    private String reviewedByName;
    private String comments;
    private UUID restaurantId;
    private String restaurantName;
    private UUID tableId;
    private String tableName;
    private BigDecimal totalAmount;
}

