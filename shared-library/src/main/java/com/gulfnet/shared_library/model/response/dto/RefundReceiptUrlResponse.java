package com.gulfnet.shared_library.model.response.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Response DTO for refund receipt presigned URL
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RefundReceiptUrlResponse {
    private String refundId;
    private String refundNumber;
    private String receiptUrl;  // Presigned URL for the receipt PDF
    private String downloadReceiptUrl;  // Presigned URL for the receipt PDF (download)
}

