package com.gulfnet.shared_library.model.request;

import com.gulfnet.shared_library.model.response.dto.MenuStructureTranslationDto;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import com.gulfnet.shared_library.enums.EntityStatus;
import lombok.*;

import java.util.List;

@AllArgsConstructor
@Getter
@Setter
@ToString
@Builder
@NoArgsConstructor

public class MenuStructureRequest {

    @NotNull(message = "{menu.structure.status.required}")
    private EntityStatus status;

    private Boolean isDeleted;

    @NotNull(message = "{menu.structure.translations.required}")
    @Size(min = 1, message = "{menu.structure.translations.min.one}")
    private List<MenuStructureTranslationDto> translations;

    public List<MenuStructureTranslationDto> getTranslations() {
        return translations;

}
}
