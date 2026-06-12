package com.gulfnet.shared_library.model.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ModifierGroupTranslationRequestDto {

    @NotBlank
    @Size(max = 5)
    private String languageCode;

    @Size(max = 100)
    private String name;

    @Size(max = 255)
    private String description;

}
