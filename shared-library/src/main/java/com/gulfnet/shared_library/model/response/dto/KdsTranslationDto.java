package com.gulfnet.shared_library.model.response.dto;

import jakarta.validation.constraints.Size;
import lombok.*;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@ToString
public class KdsTranslationDto {
    
    private String languageCode;

    @Size(max = 255, message = "Name must not exceed 255 characters")
    private String name;
}

