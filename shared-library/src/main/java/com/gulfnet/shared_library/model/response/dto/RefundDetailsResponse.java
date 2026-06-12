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

/**
 * Response DTO for refund details needed for cashier completion screen
 * Contains essential data required for the refund completion flow
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RefundDetailsResponse {
    
    // Refund Identification
    private UUID refundId;
    private String refundNumber;
    
    // Essential Amount Information
    private BigDecimal totalRefundAmount;

    // Taxable base breakdown used for consumption tax calculation
    private BigDecimal alcoholicTaxableRefundAmount;
    private BigDecimal nonAlcoholicTaxableRefundAmount;
    
    // Refund Method (must be CASH for completion)
    private String refundMethod;
    
    // Request Status (must be APPROVED for completion)
    private RequestStatus requestStatus;
    
    // Transaction Information (for reference)
    private UUID transactionId;
    private UUID orderId;
    private String transactionNumber;
    private TransactionStatus transactionStatus;
    private String orderNumber;
    
    // Request Workflow Information
    private LocalDateTime requestedAt;
    private UUID requestedBy;
    private String requestedByName;
    private String requestedByRole;
    private LocalDateTime reviewedAt;
    private UUID reviewedBy;
    private String reviewedByName;
    private String requestComments;
}
