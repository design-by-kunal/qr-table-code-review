package com.gulfnet.shared_library.model.response.dto;

import com.gulfnet.shared_library.enums.RequestStatus;
import com.gulfnet.shared_library.enums.TransactionStatus;
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
public class TransactionCancellationRequestResponse {
    private UUID transactionId;
    private UUID orderId;
    private String orderNumber;
    private String transactionNumber;
    private String paymentMethod;
    private String paymentApp;
    private BigDecimal transactionAmount;
    private TransactionStatus currentTransactionStatus;
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
}

