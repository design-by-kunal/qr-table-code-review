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
public class TableCapacityResponse {
    
    private UUID tableId;
    private String tableOrder;
    private Integer previousCapacity;
    private Integer newCapacity;
    private String reason;
    private String updatedBy;
    private LocalDateTime updatedAt;
}
