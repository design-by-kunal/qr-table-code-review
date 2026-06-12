package com.gulfnet.shared_library.model.response.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.*;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@ToString
public class ComboGroupTranslationDto {
    
    @NotBlank(message = "Language code is required")
    @Size(max = 5, message = "Language code must not exceed 5 characters")
    private String languageCode;

    @Size(max = 100, message = "Group name must not exceed 100 characters")
    private String groupName;
}
