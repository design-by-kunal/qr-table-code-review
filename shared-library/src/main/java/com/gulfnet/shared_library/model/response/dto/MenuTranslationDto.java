package com.gulfnet.shared_library.model.response.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import lombok.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor

public class MenuTranslationDto {
    private String languageCode;
    @Size(max=250)
    private String name;
    private String description;
}
