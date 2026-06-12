package com.gulfnet.shared_library.model.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class TemplateSectionTranslationDto {
    
    @NotBlank(message = "{template.section.translation.language.blank}")
    private String languageCode;

    @NotNull(message = "{template.section.translation.name.null}")
    private String name;
}
