package com.gulfnet.shared_library.model.request;

import com.gulfnet.shared_library.enums.TransactionStatus;
import jakarta.validation.constraints.NotBlank;
import lombok.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TransactionCancellationRequestDto {
    
    @NotBlank(message = "{transaction.cancellation.reason.required}")
    private String cancellationReason;
    
    private TransactionStatus requestedStatus; // The status that was requested (e.g., CANCELED)
}

