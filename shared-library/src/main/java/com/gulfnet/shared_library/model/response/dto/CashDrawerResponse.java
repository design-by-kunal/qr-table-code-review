package com.gulfnet.shared_library.model.response.dto;

import com.gulfnet.shared_library.enums.EntityStatus;
import lombok.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CashDrawerResponse {
    private UUID id;
    private UUID restaurantId;
    /** Resolved display name for the request locale (see translations for all languages). */
    private String name;
    private List<CashDrawerTranslationResponse> translations;
    private String serialNumber;
    private EntityStatus status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}

