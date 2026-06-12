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
public class PrintRequestDecisionRequest {

    /**
     * Optional comments from cashier (e.g., printer issue, printed time etc.)
     */
    private String comments;
}


