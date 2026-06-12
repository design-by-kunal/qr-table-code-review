package com.gulfnet.shared_library.model.request;

import com.gulfnet.shared_library.enums.EntityStatus;
import com.gulfnet.shared_library.model.response.dto.ModifierItemTranslationDto;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ModifierItemRequestDto {

    @NotNull(message = "{modifier.item.modifierGroupId.required}")
    private UUID modifierGroupId;

    @NotBlank(message = "{modifier.item.modifierCode.required}")
    @Size(min = 1, max = 64, message = "{modifier.item.modifierCode.size}")
    @Pattern(regexp = "^[A-Za-z0-9][A-Za-z0-9_-]{0,63}$", message = "{modifier.item.modifierCode.pattern}")
    private String modifierCode;

    private String imageUrl;

    private BigDecimal price;

    @NotNull(message = "{modifier.item.sortOrder.required}")
    private Integer sortOrder;

    private Boolean isDefault;

    @NotNull(message = "{modifier.item.status.required}")
    private EntityStatus status;

    @NotEmpty(message = "{modifier.item.translations.required}")
    @Valid
    private List<ModifierItemTranslationDto> translations;
}