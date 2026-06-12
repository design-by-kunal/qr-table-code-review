package com.gulfnet.shared_library.model.request;

import com.gulfnet.shared_library.model.response.dto.TemplateLayoutTranslationDto;
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
public class TemplateLayoutRequest {

    @NotNull(message = "{template.layout.status.required}")
    private EntityStatus status;

    @NotNull(message = "{template.layout.translations.required}")
    @Size(min = 1, message = "{template.layout.translations.min.one}")
    private List<TemplateLayoutTranslationDto> translations;
}
