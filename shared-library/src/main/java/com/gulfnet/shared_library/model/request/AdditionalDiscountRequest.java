package com.gulfnet.shared_library.model.request;

import com.gulfnet.shared_library.enums.DiscountType;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdditionalDiscountRequest {

    @NotNull
    private DiscountType additionalDiscountType; // PERCENT or FLAT

    @NotNull
    @DecimalMin(value = "0.0", inclusive = false)
    private BigDecimal additionalDiscountValue; // percent (0-100) or flat amount

    private String additionalDiscountReason; // optional free-text reason
}


