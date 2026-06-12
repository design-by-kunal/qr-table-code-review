package com.gulfnet.shared_library.model.request;

import lombok.*;

import java.util.List;
import java.util.UUID;
import com.gulfnet.shared_library.enums.TableStatus;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TableStatusPayload {

    private UUID tableId;
    private List<UUID> tableIds; // Optional field for bulk status update
    private TableStatus tableStatus;
    
    // Optional fields for blocking functionality
    private String reason;
    private String notes;
}
