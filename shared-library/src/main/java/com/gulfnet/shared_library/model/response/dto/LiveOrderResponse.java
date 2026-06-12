package com.gulfnet.shared_library.model.response.dto;

import com.gulfnet.shared_library.enums.OrderStatus;
import com.gulfnet.shared_library.enums.OrderType;
import com.gulfnet.shared_library.enums.TransactionStatus;
import lombok.*;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LiveOrderResponse {
    private UUID orderId;
    private String orderNumber;
    private OrderStatus orderStatus;
    private OrderType orderType;
    private String orderBy;
    private BigDecimal totalAmount;
    private UUID tableId;
    private String tableCode;
    private Integer tableOrder;
    private Integer rowOrder;
    private UUID sectionId;
    private String sectionName;
    private UUID transactionId;
    private TransactionStatus transactionStatus;
    private String transactionNumber;
    private String paymentMethod;
    private String paymentApp;
    private UUID sessionId;
    private Boolean allowCookingRequest;
    private UUID refundId;
    private OffsetDateTime orderDateTime;
    private List<WaiterInfo> waiters;
}
