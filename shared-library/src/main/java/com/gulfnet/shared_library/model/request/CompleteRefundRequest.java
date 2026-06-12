package com.gulfnet.shared_library.model.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import java.math.BigDecimal;

/**
 * Request DTO for completing a refund (cash payment processing)
 */
@Data
public class CompleteRefundRequest {
    
    /**
     * Cash amount given to customer (refund offered)
     * Must be >= totalRefundAmount
     */
    @NotNull(message = "Refund offered amount is required")
    @DecimalMin(value = "0.01", message = "Refund offered must be greater than 0")
    private BigDecimal refundOffered;
    
    /**
     * Change collected from customer (optional)
     * Required if refundOffered > totalRefundAmount
     * Should equal: refundOffered - totalRefundAmount
     */
    @DecimalMin(value = "0.00", message = "Change collected cannot be negative")
    private BigDecimal changeCollected;
}

