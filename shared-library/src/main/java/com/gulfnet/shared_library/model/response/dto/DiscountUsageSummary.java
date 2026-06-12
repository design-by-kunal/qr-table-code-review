package com.gulfnet.shared_library.model.response.dto;

import com.gulfnet.shared_library.entity.Discount;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.math.BigDecimal;

/**
 * Aggregated usage information for a single discount within a pricing calculation.
 * This is used to later persist summarized usages into OrderDiscountUsage.
 */
@Data
@AllArgsConstructor
public class DiscountUsageSummary {
    private Discount discount;
    private String discountType; // e.g. "Order", "Additional Discount", "Item", "Category", "BXGY"
    private String appliedTo;    // e.g. "ORDER", "ITEM", "CATEGORY"
    private BigDecimal amount;   // total discount amount for this discount in the current calculation
}

