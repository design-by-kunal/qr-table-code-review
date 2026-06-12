package com.gulfnet.shared_library.model.response.dto;

import com.gulfnet.shared_library.enums.RequestStatus;
import com.gulfnet.shared_library.enums.TransactionStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RequestListItemResponse {
    private UUID requestId;
    private String requestType; // "Profile Update", "Additional Discount", "Table/Section", "Refund", or "Item Cancellation"
    private String raisedBy; // Full name of the requester
    private String role; // Role of the person who created the request
    private String restaurant; // Restaurant name (for additional discount) or null (for profile update)
    private LocalDateTime requestDate;
    private RequestStatus status;
    private UUID entityId; // userId for profile update, orderId for additional discount
    private UUID refundId; // refundId for refund requests (null if refund not yet created/approved)
    private String transactionNumber; // Transaction number for refund requests
    private TransactionStatus transactionStatus; // Transaction status for refund requests
}

