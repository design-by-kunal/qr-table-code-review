package com.gulfnet.shared_library.model.response.dto;

import com.gulfnet.shared_library.model.request.ItemStatusPayload;
import lombok.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ItemStatusResponseWrapper {
    private ItemStatusPayload itemStatus;
}
