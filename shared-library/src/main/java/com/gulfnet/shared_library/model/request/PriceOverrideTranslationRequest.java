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
public class PriceOverrideTranslationRequest {

    @NotBlank(message = "{price.override.error.translation.language.required}")
    @Size(min = 2, max = 5, message = "{price.override.error.translation.language.invalid}")
    private String languageCode;

    @Size(max = 255, message = "{price.override.error.translation.name.length}")
    private String name;

    @Size(max = 1000, message = "{price.override.error.translation.reason.length}")
    private String reason;
}

