package com.gulfnet.shared_library.model.response.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.*;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@ToString
public class ComboTranslationDto {
    
    @NotBlank(message = "Language code is required")
    @Size(max = 5, message = "Language code must not exceed 5 characters")
    private String languageCode;

    @NotBlank(message = "Name must not be blank")
    @Size(max = 255, message = "Name must not exceed 255 characters")
    private String name;

    @Size(max = 1000, message = "Description must not exceed 1000 characters")
    private String description;
}
