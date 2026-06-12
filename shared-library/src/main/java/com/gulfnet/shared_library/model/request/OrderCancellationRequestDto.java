package com.gulfnet.shared_library.model.request;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.gulfnet.shared_library.enums.OrderStatus;
import jakarta.validation.constraints.NotBlank;
import lombok.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class OrderCancellationRequestDto {
    
    @NotBlank(message = "{order.cancellation.reason.required}")
    private String cancellationReason;
    
    private OrderStatus requestedStatus; // The status that was requested (e.g., CANCELED)
}

