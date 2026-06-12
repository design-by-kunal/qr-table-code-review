package com.gulfnet.shared_library.model.request;

import jakarta.validation.constraints.NotEmpty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BulkDeleteUsersRequest {
    @NotEmpty
    private List<UUID> userIds;
    
    private String deletedReason;
}