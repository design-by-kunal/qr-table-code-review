package com.gulfnet.shared_library.model.request;

import com.gulfnet.shared_library.enums.OverrideLevel;
import com.gulfnet.shared_library.enums.OverrideType;
import com.gulfnet.shared_library.enums.PriceOverrideStatus;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.Valid;
import lombok.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PriceOverrideRequest {

    @NotNull(message = "{price.override.error.level.required}")
    private OverrideLevel overrideLevel;

    @NotNull(message = "{price.override.error.type.required}")
    private OverrideType overrideType;

    @NotNull(message = "{price.override.error.value.required}")
    @DecimalMin(value = "0.0", inclusive = false, message = "{price.override.error.value.positive}")
    private BigDecimal overrideValue;

    @NotEmpty(message = "{price.override.error.translations.required}")
    @Valid
    private List<PriceOverrideTranslationRequest> translations;

    @NotNull(message = "{price.override.error.restaurant.required}")
    private UUID restaurantId;

    private UUID menuId;

    private List<UUID> categoryIds;

    private List<UUID> subcategoryIds;

    private PriceOverrideStatus status;
}

