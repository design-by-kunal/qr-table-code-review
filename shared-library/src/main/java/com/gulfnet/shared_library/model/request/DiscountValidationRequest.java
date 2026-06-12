package com.gulfnet.shared_library.model.request;

import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DiscountValidationRequest {

    private String discountCode;

    private UUID discountId;

    @NotNull(message = "{discount.validation.error.menu.id.required}")
    private UUID menuId;

    private UUID restaurantId;

    @NotNull(message = "{discount.validation.error.subtotal.required}")
    @DecimalMin(value = "0.0", inclusive = false, message = "{discount.validation.error.subtotal.positive}")
    private BigDecimal subTotal;

    // Custom validation method to ensure either discountCode or discountId is provided
    @AssertTrue(message = "{discount.validation.error.either.code.or.id.required}")
    public boolean isEitherDiscountCodeOrIdProvided() {
        return (discountCode != null && !discountCode.trim().isEmpty()) || discountId != null;
    }
}
