package com.gulfnet.shared_library.model.request;

import com.gulfnet.shared_library.enums.TransactionStatus;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TransactionStatusPayload {
    
    @NotNull(message = "{transaction.status.required}")
    private TransactionStatus transactionStatus;
    
    private String reason; // Cancellation reason
}

