package com.gulfnet.shared_library.model.response.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.*;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@ToString
public class TemplateLayoutTranslationDto {

    @NotBlank(message = "Language code must not be blank")
    @Size(max = 5, message = "Language code must not exceed 5 characters")
    private String languageCode;

    @Size(max = 255, message = "Name must not exceed 255 characters")
    private String name;
}
