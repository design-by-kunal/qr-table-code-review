package com.gulfnet.shared_library.model.request;

import com.gulfnet.shared_library.enums.EntityStatus;
import com.gulfnet.shared_library.enums.ModifierType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ModifierGroupRequestDto {

    private UUID id;

    @NotNull
    private ModifierType modifierType;

    @NotNull
    private Boolean allowMultiSelect;

    private Integer minLimit;

    private Integer maxLimit;

    @NotNull
    private EntityStatus status;

    @NotEmpty
    @Valid
    private List<ModifierGroupTranslationRequestDto> translations;
}
