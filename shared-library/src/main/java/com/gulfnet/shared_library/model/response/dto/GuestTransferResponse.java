package com.gulfnet.shared_library.model.response.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GuestTransferResponse {
    
    private UUID sessionId;
    private UUID sourceTableId;
    private UUID targetTableId;
    private String sourceTableOrder;
    private String targetTableOrder;
    private String sourceRowId;
    private String targetRowId;
    private Integer sourceRowOrder;
    private Integer targetRowOrder;
    private LocalDateTime transferredAt;
    private String reason;
    private String previousWaiterId;
    private String newWaiterId;
    private String previousWaiterName;
    private String newWaiterName;
}
