package com.gulfnet.shared_library.model.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateCashDrawerRequest {

    @NotEmpty(message = "At least one translation is required")
    @Valid
    private List<CashDrawerTranslationRequest> translations;

    @NotBlank(message = "Serial number is required")
    private String serialNumber;
}
