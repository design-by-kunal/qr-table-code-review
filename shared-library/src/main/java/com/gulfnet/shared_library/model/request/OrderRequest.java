package com.gulfnet.shared_library.model.request;

import com.gulfnet.shared_library.enums.DiscountType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderRequest {

    @NotNull
    private UUID sessionId;

    private UUID restaurantId;

    @NotNull
    private UUID menuId;

    private UUID discountId;

    private String discountCode;

    private BigDecimal additionalDiscountValue;

    private DiscountType additionalDiscountType;

    @Valid
    private List<OrderedItemRequest> orderedItems;

    @Valid
    private List<OrderedComboRequest> orderedCombos;

    private String email;
}