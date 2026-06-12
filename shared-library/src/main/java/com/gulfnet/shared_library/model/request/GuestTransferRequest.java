package com.gulfnet.shared_library.model.request;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GuestTransferRequest {
    
    @NotNull(message = "{guest.transfer.sourceTableId.required}")
    private UUID sourceTableId;
    
    @NotNull(message = "{guest.transfer.targetTableId.required}")
    private UUID targetTableId;
    
    @NotNull(message = "{guest.transfer.sessionId.required}")
    private UUID sessionId;
    
    private String sourceRowId; // Optional source row ID
    
    private String targetRowId; // Optional target row ID
    
    private String reason; // Optional reason for transfer
}
