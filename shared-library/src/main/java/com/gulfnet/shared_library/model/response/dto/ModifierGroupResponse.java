package com.gulfnet.shared_library.model.response.dto;

import com.gulfnet.shared_library.enums.EntityStatus;
import com.gulfnet.shared_library.enums.ModifierType;
import lombok.*;

import java.util.List;
import java.util.UUID;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@ToString
public class ModifierGroupResponse {

    private UUID id;

    private ModifierType modifierType;

    private Boolean allowMultiSelect;

    private Integer minLimit;

    private Integer maxLimit;

    private EntityStatus status;

    private Boolean isDeleted;

    private List<ModifierGroupTranslationDto> translations;
}
