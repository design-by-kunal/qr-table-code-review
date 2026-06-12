package com.gulfnet.shared_library.model.request;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TableBlockRequest {
    
    @NotBlank(message = "{table.block.reason.required}")
    private String reason;
    
    private String notes; // Optional additional notes
}
