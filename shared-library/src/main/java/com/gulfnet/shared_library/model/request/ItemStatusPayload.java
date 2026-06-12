package com.gulfnet.shared_library.model.request;

import com.gulfnet.shared_library.enums.ItemStatus;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.*;

import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ItemStatusPayload {
    private UUID orderedItemId;
    private ItemStatus itemStatus;
    private List<UUID> orderedItemIds;
    private UUID orderedComboId;
    private List<UUID> orderedComboIds;
    private String reason; // Optional reason for cancellation
}
