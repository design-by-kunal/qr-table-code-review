package com.gulfnet.shared_library.model.response.dto;

import lombok.*;

import java.time.OffsetDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@ToString
public class UnavailableItem {
    private UUID itemId;
    private String itemCode;
    private String itemName;
    private String categoryName;
    private String imageUrl;
    private OffsetDateTime madeUnavailableAt;
}

