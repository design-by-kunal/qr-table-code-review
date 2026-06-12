package com.gulfnet.shared_library.util;

import com.gulfnet.shared_library.enums.RoundingMode;

import java.math.BigDecimal;

/**
 * Utility class for formatting currency amounts based on currency symbol.
 * Handles different formatting rules for various currencies:
 * - Yen (¥): No decimal values (0 decimal places)
 * - Dollar ($): Standard 2 decimal places
 * - Thai Baht (฿): Standard 2 decimal places
 */
public final class CurrencyFormatter {

    // Currency symbols
    private static final String YEN_SYMBOL = "¥";
    private static final String YEN_UNICODE = "\u00A5"; // Unicode for ¥
    private static final String DOLLAR_SYMBOL = "$";
    private static final String THAI_BAHT_SYMBOL = "฿";
    private static final String THAI_BAHT_UNICODE = "\u0E3F"; // Unicode for ฿

    /**
     * Default rounding policy used by {@link #formatAmount(BigDecimal, String)}.
     * This is intended to be set once during application startup from chain config.
     * Defaults to {@link RoundingMode#ROUND_DOWN} (truncate toward zero at the currency scale).
     */
    private static volatile RoundingMode defaultRoundingPolicy = RoundingMode.ROUND_DOWN;

    private CurrencyFormatter() {
        // Utility class - prevent instantiation
    }

    /**
     * Formats a BigDecimal amount based on the currency symbol.
     * Returns the amount with appropriate decimal places using the default rounding policy
     * ({@link RoundingMode#ROUND_DOWN} unless changed via {@link #setDefaultRoundingPolicy}).
     *
     * @param amount The amount to format
     * @param currencySymbol The currency symbol (e.g., "¥", "$", "฿")
     * @return Formatted BigDecimal with appropriate scale
     */
    public static BigDecimal formatAmount(BigDecimal amount, String currencySymbol) {
        return formatAmount(amount, currencySymbol, defaultRoundingPolicy);
    }

    /**
     * Sets the default rounding policy for {@link #formatAmount(BigDecimal, String)}.
     * If null, defaults to {@link RoundingMode#ROUND_DOWN}.
     */
    public static void setDefaultRoundingPolicy(RoundingMode policy) {
        defaultRoundingPolicy = (policy != null) ? policy : RoundingMode.ROUND_DOWN;
    }

    public static RoundingMode getDefaultRoundingPolicy() {
        return defaultRoundingPolicy;
    }

    /**
     * Formats a BigDecimal amount based on the currency symbol and tax rounding policy.
     * This is intended for amounts like tax lines where the rounding policy is configurable per chain.
     *
     * @param amount The amount to format
     * @param currencySymbol The currency symbol (e.g., "¥", "$", "฿")
     * @param policy The rounding policy (defaults to {@link RoundingMode#ROUND_DOWN} when null)
     * @return Formatted BigDecimal with appropriate scale
     */
    public static BigDecimal formatAmount(BigDecimal amount, String currencySymbol, RoundingMode policy) {
        return formatAmount(amount, currencySymbol, resolveRoundingMode(policy));
    }

    /**
     * Formats a BigDecimal amount based on the currency symbol and rounding mode.
     *
     * @param amount The amount to format
     * @param currencySymbol The currency symbol (e.g., "¥", "$", "฿")
     * @param roundingMode The rounding mode to apply when setting scale
     * @return Formatted BigDecimal with appropriate scale
     */
    public static BigDecimal formatAmount(BigDecimal amount, String currencySymbol, java.math.RoundingMode roundingMode) {
        if (amount == null) {
            return BigDecimal.ZERO;
        }

        if (roundingMode == null) {
            roundingMode = java.math.RoundingMode.DOWN;
        }

        if (currencySymbol == null || currencySymbol.trim().isEmpty()) {
            // Default to 2 decimal places if currency is not specified
            return amount.setScale(2, roundingMode);
        }

        String normalizedCurrency = currencySymbol.trim();

        // Check for Yen (¥) - no decimal places
        if (isYen(normalizedCurrency)) {
            return amount.setScale(0, roundingMode);
        }

        // Check for Dollar ($) - 2 decimal places
        if (isDollar(normalizedCurrency)) {
            return amount.setScale(2, roundingMode);
        }

        // Check for Thai Baht (฿) - 2 decimal places
        if (isThaiBaht(normalizedCurrency)) {
            return amount.setScale(2, roundingMode);
        }

        // Default to 2 decimal places for unknown currencies
        return amount.setScale(2, roundingMode);
    }

    /**
     * Maps a chain rounding policy to {@link java.math.RoundingMode}.
     * Defaults to {@link java.math.RoundingMode#DOWN} (truncate toward zero) when policy is null.
     */
    public static java.math.RoundingMode resolveRoundingMode(RoundingMode policy) {
        if (policy == null) {
            return java.math.RoundingMode.DOWN;
        }
        return switch (policy) {
            case ROUND_HALF_UP -> java.math.RoundingMode.HALF_UP;
            case ROUND_DOWN -> java.math.RoundingMode.DOWN;
            case ROUND_UP -> java.math.RoundingMode.CEILING;
        };
    }

    /**
     * Gets the number of decimal places for a given currency symbol.
     *
     * @param currencySymbol The currency symbol
     * @return Number of decimal places (0 for Yen, 2 for Dollar/Thai Baht, 2 as default)
     */
    public static int getDecimalPlaces(String currencySymbol) {
        if (currencySymbol == null || currencySymbol.trim().isEmpty()) {
            return 2; // Default
        }

        String normalizedCurrency = currencySymbol.trim();

        if (isYen(normalizedCurrency)) {
            return 0;
        } else if (isDollar(normalizedCurrency) || isThaiBaht(normalizedCurrency)) {
            return 2;
        }

        return 2; // Default
    }

    /**
     * Excel number format pattern for monetary columns (Apache POI {@code DataFormat}), derived from
     * {@link #getDecimalPlaces(String)} (e.g. {@code #,##0} for yen, {@code #,##0.00} otherwise).
     *
     * @param currencySymbol chain currency symbol (e.g. from restaurant chain config); null/empty defaults to 2 fraction digits
     */
    public static String getMonetaryExcelDataFormatPattern(String currencySymbol) {
        return getDecimalPlaces(currencySymbol) == 0 ? "#,##0" : "#,##0.00";
    }

    /**
     * Checks if the currency symbol represents Yen.
     *
     * @param currencySymbol The currency symbol to check
     * @return true if the symbol is Yen (¥)
     */
    public static boolean isYen(String currencySymbol) {
        if (currencySymbol == null) {
            return false;
        }
        String normalized = currencySymbol.trim();
        return YEN_SYMBOL.equals(normalized) || YEN_UNICODE.equals(normalized);
    }

    /**
     * Checks if the currency symbol represents Dollar.
     *
     * @param currencySymbol The currency symbol to check
     * @return true if the symbol is Dollar ($)
     */
    public static boolean isDollar(String currencySymbol) {
        if (currencySymbol == null) {
            return false;
        }
        return DOLLAR_SYMBOL.equals(currencySymbol.trim());
    }

    /**
     * Checks if the currency symbol represents Thai Baht.
     *
     * @param currencySymbol The currency symbol to check
     * @return true if the symbol is Thai Baht (฿)
     */
    public static boolean isThaiBaht(String currencySymbol) {
        if (currencySymbol == null) {
            return false;
        }
        String normalized = currencySymbol.trim();
        return THAI_BAHT_SYMBOL.equals(normalized) || THAI_BAHT_UNICODE.equals(normalized);
    }

    /**
     * Formats a monetary amount for CSV export using {@link #formatAmount(BigDecimal, String)} (yen: no
     * fraction digits; dollar/baht/default: two fraction digits).
     *
     * @param amount         the amount; {@code null} is treated as zero for display
     * @param currencySymbol chain currency symbol (e.g. from restaurant chain config)
     */
    public static String formatCsvMonetaryString(BigDecimal amount, String currencySymbol) {
        if (amount == null) {
            return "0";
        }
        return formatAmount(amount, currencySymbol).toPlainString();
    }

    /**
     * Formats a percentage value for CSV export without fractional digits (e.g. payment mix share).
     */
    public static String formatCsvPercentString(Double percentage) {
        if (percentage == null) {
            return "0";
        }
        return BigDecimal.valueOf(percentage).setScale(0, java.math.RoundingMode.HALF_UP).toPlainString();
    }
}

