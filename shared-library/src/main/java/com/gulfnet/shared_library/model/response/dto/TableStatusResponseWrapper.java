package com.gulfnet.shared_library.model.response.dto;

import com.gulfnet.shared_library.model.request.TableStatusPayload;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TableStatusResponseWrapper {
    private TableStatusPayload tableStatus;
    
    // Additional fields for blocking functionality
    private String previousStatus;
    private String updatedBy;
    private LocalDateTime updatedAt;
    
    // Optional field for bulk updates (multiple tables)
    private List<TableStatusResponseWrapper> tableStatuses;
}
