package com.gulfnet.shared_library.model.request;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CashDrawerTranslationRequest {

    @NotBlank(message = "Language code is required")
    private String languageCode;

    @NotBlank(message = "Drawer name is required")
    private String name;
}
