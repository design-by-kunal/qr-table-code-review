package com.gulfnet.shared_library.model.request;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonSetter;
import com.gulfnet.shared_library.enums.AppliedTo;
import com.gulfnet.shared_library.enums.DiscountType;
import com.gulfnet.shared_library.enums.EntityStatus;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.validator.constraints.Length;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DiscountRequest {

    @NotBlank(message = "{discount.error.code.required}")
    @Length(max = 10)
    private String discountCode;

    @NotNull(message = "{discount.error.type.required}")
    private DiscountType discountType;

    @NotNull(message = "{discount.error.applied.to.required}")
    private AppliedTo appliedTo;

    @DecimalMin(value = "0.0", inclusive = false, message = "{discount.error.value.negative}")
    private BigDecimal value;

    @DecimalMin(value = "0.0", inclusive = true, message = "{discount.error.threshold.negative}")
    private BigDecimal orderValueThreshold;

    @DecimalMin(value = "0.0", inclusive = false, message = "{discount.error.max.discount.value.negative}")
    private BigDecimal maxDiscountValue;

    @Min(value = 1, message = "{discount.error.buy.quantity.min}")
    private Integer buyQuantity;

    @Min(value = 1, message = "{discount.error.get.quantity.min}")
    private Integer getQuantity;

    // maxUses is required for ORDER discounts, optional for ITEM and CATEGORY discounts
    // maxUses = 0 means unlimited uses
    @JsonAlias("maxUsage")
    @Min(value = 0, message = "{discount.error.max.uses.min}")
    private Integer maxUses;


    @NotNull(message = "{discount.error.status.required}")
    private EntityStatus status;

    private UUID purchasedItemId;
    private UUID freeItemId;

    @NotEmpty(message = "{discount.error.translations.required}")
    @Valid
    private List<DiscountTranslationRequest> translations;

    // Trim discountCode on deserialization
    @JsonSetter("discountCode")
    public void setDiscountCode(String code) {
        this.discountCode = code != null ? code.trim() : null;
    }
}