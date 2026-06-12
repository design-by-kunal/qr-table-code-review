package com.gulfnet.shared_library.model.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.DecimalMin;
import lombok.*;

import java.math.BigDecimal;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StartShiftRequest {
    
    @NotNull(message = "Cash drawer ID is required")
    private UUID cashDrawerId;
    
    @NotNull(message = "Opening balance is required")
    @DecimalMin(value = "0.0", message = "Opening balance must be greater than or equal to 0")
    private BigDecimal openingBalance;
    
    private UUID shiftId; // Optional: Link to shift definition (Morning Shift, Evening Shift, etc.)
    
    private String notes; // Optional notes
}

