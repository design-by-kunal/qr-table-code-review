package com.gulfnet.shared_library.model.response.dto;

import com.gulfnet.shared_library.enums.EntityStatus;
import com.gulfnet.shared_library.enums.ModifierType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ModifierGroupDetailsResponse {
    private UUID id;
    private ModifierType modifierType;
    private Boolean allowMultiSelect;
    private Integer minLimit;
    private Integer maxLimit;
    private EntityStatus status;
    private LocalDateTime createdAt;
    private String createdBy;
    private LocalDateTime updatedAt;
    private String updatedBy;
    private List<ModifierGroupTranslationDto> translations;
}