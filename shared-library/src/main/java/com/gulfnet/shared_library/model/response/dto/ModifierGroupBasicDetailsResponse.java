package com.gulfnet.shared_library.model.response.dto;

import com.gulfnet.shared_library.enums.EntityStatus;
import com.gulfnet.shared_library.enums.ModifierType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ModifierGroupBasicDetailsResponse {
    private UUID id;
    private String name;
    private ModifierType modifierType;
    private Boolean allowMultiSelect;
    private EntityStatus status;
    private LocalDateTime createdAt;
    private String createdBy;
    private Boolean isAssigned;
    private Long menuCount;
}
