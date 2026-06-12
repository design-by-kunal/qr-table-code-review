package com.gulfnet.shared_library.config;

import com.gulfnet.shared_library.enums.RoundingMode;
import com.gulfnet.shared_library.util.CurrencyFormatter;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

import jakarta.annotation.PostConstruct;

/**
 * Wires application configuration into {@link CurrencyFormatter}'s default rounding policy.
 *
 * <p>Any service that includes shared-library can set:
 * {@code restaurant.chain.roundingMode=ROUND_HALF_UP|ROUND_DOWN|ROUND_UP}
 * to affect {@link CurrencyFormatter#formatAmount(java.math.BigDecimal, String)} globally.
 */
@Configuration
public class CurrencyFormatterConfig {

    private final Environment environment;

    public CurrencyFormatterConfig(Environment environment) {
        this.environment = environment;
    }

    /**
     * Reads {@code restaurant.chain.roundingMode} and applies it as {@link CurrencyFormatter}'s default rounding mode.
     * Invalid or blank values leave the built-in default unchanged.
     */
    @PostConstruct
    public void applyDefaultRoundingPolicyFromConfig() {
        String raw = environment.getProperty("restaurant.chain.roundingMode");
        if (raw == null || raw.isBlank()) {
            return;
        }
        try {
            CurrencyFormatter.setDefaultRoundingPolicy(RoundingMode.valueOf(raw.trim()));
        } catch (IllegalArgumentException ignored) {
            // Keep built-in default when value is invalid.
        }
    }
}

