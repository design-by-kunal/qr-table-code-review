package com.gulfnet.shared_library.model.request;

import com.gulfnet.shared_library.enums.RequestStatus;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ApproveShiftRequest {

    /**
     * Manager action on the shift discrepancy request.
     * APPROVED = shift completed, DECLINED = remains PENDING for cashier to correct reason.
     */
    @NotNull
    private RequestStatus action;

    /**
     * Optional manager notes (will not overwrite cashier's discrepancy reason).
     */
    private String notes;
}

