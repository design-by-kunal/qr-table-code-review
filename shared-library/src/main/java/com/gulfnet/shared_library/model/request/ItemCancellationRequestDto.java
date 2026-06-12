package com.gulfnet.shared_library.model.request;

import com.gulfnet.shared_library.enums.ItemStatus;
import jakarta.validation.constraints.NotBlank;
import lombok.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ItemCancellationRequestDto {
    
    @NotBlank(message = "{item.cancellation.reason.required}")
    private String cancellationReason;
    
    private ItemStatus requestedStatus; // The status that was requested (e.g., CANCELED)
}
