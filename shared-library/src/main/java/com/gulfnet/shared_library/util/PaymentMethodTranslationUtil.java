package com.gulfnet.shared_library.util;

import java.util.Locale;

/**
 * Maps stored payment method codes (e.g. CASH, UPI, CARD) to i18n message keys.
 */
public final class PaymentMethodTranslationUtil {

    public static final String MESSAGE_KEY_PREFIX = "payment.method.";

    public static final String MSG_KEY_CASH = "payment.method.CASH";
    public static final String MSG_KEY_UPI = "payment.method.UPI";
    public static final String MSG_KEY_CARD = "payment.method.CARD";

    private PaymentMethodTranslationUtil() {
    }

    /**
     * Normalizes DB / API payment method codes to a canonical type for display lookup.
     * CREDIT_CARD and DEBIT_CARD are grouped under CARD.
     */
    public static String normalizeCode(String paymentMethod) {
        if (paymentMethod == null) {
            return null;
        }
        String trimmed = paymentMethod.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        String upper = trimmed.toUpperCase(Locale.ROOT);
        return switch (upper) {
            case "CREDIT_CARD", "DEBIT_CARD" -> "CARD";
            default -> upper;
        };
    }

    /**
     * Returns a messages.properties key for known payment methods, or null if unknown.
     */
    public static String messageKeyFor(String paymentMethod) {
        String code = normalizeCode(paymentMethod);
        if (code == null) {
            return null;
        }
        return switch (code) {
            case "CASH" -> MSG_KEY_CASH;
            case "UPI" -> MSG_KEY_UPI;
            case "CARD" -> MSG_KEY_CARD;
            default -> null;
        };
    }
}
