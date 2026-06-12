package com.gulfnet.shared_library.model.response.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Response DTO for refund completion
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RefundCompletionResponse {
    private UUID refundId;
    private String refundNumber;
    private BigDecimal refundAmount;          // totalRefundAmount
    private BigDecimal refundOffered;         // Cash given to customer
    private BigDecimal changeExpected;       // refundOffered - refundAmount
    private BigDecimal changeCollected;       // Actual change collected
    private BigDecimal discrepancyAmount;    // changeCollected - changeExpected
    private String discrepancyReason;
    private LocalDateTime completedAt;
    private UUID completedBy;
    private String completedByName;
    private RefundReceiptResponse receipt;
    private UUID cashDrawerLogId;
    
    /**
     * Receipt information
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RefundReceiptResponse {
        private String receiptUrl;           // S3 URL (stored in refund entity)
        private String receiptNumber;
    }
}

