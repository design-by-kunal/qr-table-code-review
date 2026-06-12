package com.gulfnet.shared_library.model.request;

import com.gulfnet.shared_library.enums.DrawerEventType;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.DecimalMin;
import lombok.*;

import java.math.BigDecimal;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ManualDrawerEventRequest {
    
    @NotNull(message = "Event type is required")
    private DrawerEventType eventType; // DEPOSIT or WITHDRAWAL
    
    @NotNull(message = "Amount is required")
    @DecimalMin(value = "0.01", message = "Amount must be greater than 0")
    private BigDecimal amount;
    
    private UUID transactionId;
    
    private String reason;
    
    private String notes;
}

