package com.gulfnet.shared_library.model.request;

import com.gulfnet.shared_library.enums.RequestStatus;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ItemCancellationApprovalRequest {
    
    @NotNull(message = "{item.cancellation.approval.action.required}")
    private RequestStatus action; // APPROVED or DECLINED
    
    @Size(max = 500, message = "{approval.comments.max.length}")
    private String comments; // Optional reason for approval/decline
}

