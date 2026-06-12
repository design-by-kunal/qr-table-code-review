package com.gulfnet.shared_library.model.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class UpdatePreferredLanguageRequest {

    @NotBlank(message = "{user.language.code.blank}")
    private String languageCode;
}

