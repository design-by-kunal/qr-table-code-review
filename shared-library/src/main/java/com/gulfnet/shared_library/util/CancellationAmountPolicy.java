package com.gulfnet.shared_library.util;

import com.gulfnet.shared_library.entity.Order;
import com.gulfnet.shared_library.enums.OrderType;
import com.gulfnet.shared_library.enums.PaymentSystemType;
import com.gulfnet.shared_library.enums.TransactionStatus;

import java.math.BigDecimal;

/**
 * Central rules for when order-level monetary totals should not be adjusted during cancellations.
 * Matches item cancellation {@code cancelItemWithoutDeduction} / {@code shouldSkipDeductionForCancellation} behavior.
 */
public final class CancellationAmountPolicy {

    private CancellationAmountPolicy() {
    }

    /**
     * When {@code true}, cancellation flows should not deduct from or zero out order monetary totals.
     * If the linked transaction is {@link TransactionStatus#COMPLETED}, do NOT change monetary totals.
     * This prevents retroactively changing paid/completed bills when canceling lines for operational reasons.
     *
     * @param orderType                 order type
     * @param chainPaymentType          chain payment system type (may be null)
     * @param linkedTransactionStatus   status of the order's transaction before cancellation is applied (may be null)
     */
    public static boolean shouldSkipOrderAmountAdjustmentOnCancellation(
            OrderType orderType,
            PaymentSystemType chainPaymentType,
            TransactionStatus linkedTransactionStatus) {
        return linkedTransactionStatus == TransactionStatus.COMPLETED;
    }

    /**
     * Zeros order-level monetary fields after all lines are cancelled (full order / transaction cancellation).
     * Does not modify audit timestamps, cancellation review fields, or status.
     */
    public static void resetOrderMonetaryTotalsForFullCancellation(Order order) {
        if (order == null) {
            return;
        }
        order.setSubTotal(BigDecimal.ZERO);
        order.setDiscountAmount(BigDecimal.ZERO);
        order.setTaxAmount(BigDecimal.ZERO);
        order.setAlcoholicTaxAmount(BigDecimal.ZERO);
        order.setNonAlcoholicTaxAmount(BigDecimal.ZERO);
        order.setAlcoholicTaxableAmount(BigDecimal.ZERO);
        order.setNonAlcoholicTaxableAmount(BigDecimal.ZERO);
        order.setServiceChargeAmount(BigDecimal.ZERO);
        order.setPackingChargeAmount(BigDecimal.ZERO);
        order.setAdditionalDiscountAmount(BigDecimal.ZERO);
        order.setTotalAmount(BigDecimal.ZERO);
        if (order.getDiscount() != null) {
            order.setDiscount(null);
            order.setDiscountCode(null);
            order.setDiscountValue(null);
            order.setDiscountType(null);
        }
    }
}
