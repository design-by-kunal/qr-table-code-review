package com.gulfnet.shared_library.enums;

/**
 * Configurable rounding mode applied when formatting monetary amounts at the currency scale
 * (e.g. JPY = 0 decimals, USD = 2 decimals).
 *
 * <p>Note: This is a domain/config enum and is intentionally distinct from {@link java.math.RoundingMode}.
 */
public enum RoundingMode {
    /**
     * Round to nearest, halves round up (e.g. 10.5 -> 11).
     */
    ROUND_HALF_UP,
    /**
     * Round down by truncating fractional digits (e.g. 10.9 -> 10).
     */
    ROUND_DOWN,
    /**
     * Round up to the next unit when there is any fractional part (e.g. 10.1 -> 11).
     */
    ROUND_UP
}

