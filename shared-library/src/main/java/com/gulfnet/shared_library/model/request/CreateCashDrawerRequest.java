package com.gulfnet.shared_library.model.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateCashDrawerRequest {

    @NotNull(message = "Restaurant ID is required")
    private UUID restaurantId;

    @NotEmpty(message = "At least one translation is required")
    @Valid
    private List<CashDrawerTranslationRequest> translations;

    @NotBlank(message = "Serial number is required")
    private String serialNumber;
}
