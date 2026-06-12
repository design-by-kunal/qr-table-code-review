package com.gulfnet.shared_library.model.response.dto;

import com.gulfnet.shared_library.enums.DrawerEventType;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CashDrawerLogResponse {
    private UUID id;
    private UUID shiftId;
    private UUID drawerId;
    private String drawerName;
    private UUID userId;
    private String userName;
    private DrawerEventType eventType;
    private BigDecimal amount;
    private BigDecimal expectedAmount;
    private BigDecimal grossIn;
    private BigDecimal grossOut;
    private UUID transactionId;
    private String transactionNumber;
    private UUID refundId;
    private String refundNumber;
    private String reason;
    private String notes;
    private LocalDateTime createdAt;
    private UUID createdBy;
    private String createdByName;
}

