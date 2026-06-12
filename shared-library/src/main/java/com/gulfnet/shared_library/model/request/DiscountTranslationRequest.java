package com.gulfnet.shared_library.model.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DiscountTranslationRequest {

    @NotBlank(message = "{discount.error.translation.language.required}")
    @Size(min = 2, max = 5, message = "{discount.error.translation.language.invalid}")
    private String languageCode;

    @NotBlank(message = "{discount.error.translation.name.required}")
    @Size(max = 255, message = "{discount.error.translation.name.length}")
    private String name;

    @Size(max = 1000, message = "{discount.error.translation.description.length}")
    private String description;
} 