package com.gulfnet.shared_library.model.response.dto;

import java.math.BigDecimal;

/**
 * Result of item price calculation with modifiers
 */
public record ItemPriceCalculationResult(
    BigDecimal basePricePerUnit,
    BigDecimal discountedPricePerUnit,  // null if no discount
    BigDecimal totalAmountWithoutDiscount,  // always set
    BigDecimal totalAmountWithDiscount  // null if no discount
) {}

