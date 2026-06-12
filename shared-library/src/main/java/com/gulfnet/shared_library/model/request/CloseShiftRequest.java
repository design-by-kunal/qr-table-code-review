package com.gulfnet.shared_library.model.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.DecimalMin;
import lombok.*;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CloseShiftRequest {
    
    @NotNull(message = "Closing balance is required")
    @DecimalMin(value = "0.0", message = "Closing balance must be greater than or equal to 0")
    private BigDecimal closingBalance;
    
    private String notes; // Optional notes
}

