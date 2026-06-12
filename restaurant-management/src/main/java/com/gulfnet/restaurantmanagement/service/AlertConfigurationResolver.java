package com.gulfnet.restaurantmanagement.service;

import com.gulfnet.restaurantmanagement.config.RestaurantChainConfigProperties;
import com.gulfnet.shared_library.entity.Restaurant;
import com.gulfnet.shared_library.entity.RestaurantGroup;
import java.math.BigDecimal;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Resolves alert configuration for a restaurant using the hierarchy:
 * Restaurant (most specific) → Restaurant Group → Account (chain defaults).
 */
@Slf4j
@Service
@AllArgsConstructor
public class AlertConfigurationResolver {

    private final RestaurantChainConfigProperties chainConfigProperties;

    @Data
    @Builder
    public static class ResolvedAlertConfig {
        private BigDecimal salesAlertThreshold;
        private BigDecimal refundAlertPercentage;
        private BigDecimal cancellationAlertPercentage;
        private boolean alertsEnabled;
    }

    /**
     * Resolves alert configuration for a restaurant using the hierarchy:
     * Restaurant (most specific) → Restaurant Group → Account (chain defaults).
     * For threshold values (sales, refund, cancellation), uses the first non-null value from the hierarchy.
     * For alertsEnabled, uses AND logic: if any level explicitly disables alerts (false), alerts are disabled.
     *
     * @param restaurant the restaurant to resolve configuration for (may be null, in which case account defaults are returned)
     * @return a ResolvedAlertConfig containing the resolved alert thresholds and enabled status
     */
    public ResolvedAlertConfig resolveForRestaurant(Restaurant restaurant) {
        if (restaurant == null) {
            // fall back entirely to account-level defaults
            return resolveFromAccountDefaults();
        }

        RestaurantChainConfigProperties.RestaurantChainData chain =
                chainConfigProperties != null ? chainConfigProperties.getChain() : null;

        RestaurantGroup group = restaurant.getRestaurantGroup();
        
        // Log for debugging: check if group is loaded and has thresholds
        if (group == null) {
            log.debug("Restaurant {} has no restaurantGroup - will use chain defaults only", 
                    restaurant.getRestaurantCode());
        } else {
            log.debug("Restaurant {} belongs to group {} - checking group thresholds: sales={}, refund={}, cancellation={}", 
                    restaurant.getRestaurantCode(), 
                    group.getRestaurantGroupCode(),
                    group.getSalesAlertThreshold(),
                    group.getRefundAlertPercentage(),
                    group.getCancellationAlertPercentage());
        }

        BigDecimal salesThreshold =
                firstNonNull(restaurant.getSalesAlertThreshold(),
                        group != null ? group.getSalesAlertThreshold() : null,
                        chain != null ? chain.getDefaultSalesAlertThreshold() : null);

        BigDecimal refundPct =
                firstNonNull(restaurant.getRefundAlertPercentage(),
                        group != null ? group.getRefundAlertPercentage() : null,
                        chain != null ? chain.getDefaultRefundAlertPercentage() : null);

        BigDecimal cancellationPct =
                firstNonNull(restaurant.getCancellationAlertPercentage(),
                        group != null ? group.getCancellationAlertPercentage() : null,
                        chain != null ? chain.getDefaultCancellationAlertPercentage() : null);
        
        // Log resolved values for debugging (use INFO level for better visibility)
        log.info("🔍 Resolved thresholds for restaurant {}: sales={} (restaurant={}, group={}, chain={}), refund={}, cancellation={}", 
                restaurant.getRestaurantCode(),
                salesThreshold,
                restaurant.getSalesAlertThreshold(),
                group != null ? group.getSalesAlertThreshold() : null,
                chain != null ? chain.getDefaultSalesAlertThreshold() : null,
                refundPct,
                cancellationPct);

        // For alertsEnabled, use AND logic across the hierarchy:
        // If ANY level explicitly disables alerts (false), alerts are disabled.
        // This ensures disabling at account or group level always takes effect
        // for all child restaurants, even if the restaurant was created with the default (true).
        boolean alertsEnabled = resolveAlertsEnabled(restaurant, group, chain);

        return ResolvedAlertConfig.builder()
                .salesAlertThreshold(salesThreshold)
                .refundAlertPercentage(refundPct)
                .cancellationAlertPercentage(cancellationPct)
                .alertsEnabled(alertsEnabled)
                .build();
    }

    /**
     * Resolves alertsEnabled using AND logic across the hierarchy:
     * Account (chain) → Restaurant Group → Restaurant.
     * If any level explicitly sets alertsEnabled to false, alerts are disabled.
     * This ensures group-level or account-level disabling always takes effect.
     * 
     * Logic:
     * - Start with chain default (true if chain is null or not configured)
     * - If group explicitly sets alertsEnabled = false, disable
     * - If restaurant explicitly sets alertsEnabled = false, disable
     * - NULL values mean "inherit from parent" (don't override)
     */
    private boolean resolveAlertsEnabled(Restaurant restaurant, RestaurantGroup group,
                                         RestaurantChainConfigProperties.RestaurantChainData chain) {
        // Start with account-level default
        // If chain is null or defaultAlertsEnabled is not configured, default to true
        boolean enabled = chain == null || chain.isDefaultAlertsEnabled();
        
        log.debug("Alert enabled resolution - Chain default: {}, Chain configured: {}", 
                enabled, chain != null);

        // Group level: if group explicitly disables (alertsEnabled = false), override to false
        // NULL means "inherit from parent", so we only check if it's explicitly false
        if (group != null && group.getAlertsEnabled() != null) {
            if (Boolean.FALSE.equals(group.getAlertsEnabled())) {
                enabled = false;
                log.debug("Alert disabled at group level: {}", group.getRestaurantGroupCode());
            } else {
                enabled = true; // Group explicitly enables, override chain default
                log.debug("Alert enabled at group level: {}", group.getRestaurantGroupCode());
            }
        }

        // Restaurant level: if restaurant explicitly disables (alertsEnabled = false), override to false
        // NULL means "inherit from parent", so we only check if it's explicitly false
        if (restaurant.getAlertsEnabled() != null) {
            if (Boolean.FALSE.equals(restaurant.getAlertsEnabled())) {
                enabled = false;
                log.debug("Alert disabled at restaurant level: {}", restaurant.getRestaurantCode());
            } else {
                enabled = true; // Restaurant explicitly enables, override parent settings
                log.debug("Alert enabled at restaurant level: {}", restaurant.getRestaurantCode());
            }
        }
        
        log.info("🔔 Final alert enabled status for restaurant {}: {} (restaurant={}, group={}, chain={})", 
                restaurant.getRestaurantCode(), enabled,
                restaurant.getAlertsEnabled(), 
                group != null ? group.getAlertsEnabled() : null,
                chain != null ? chain.isDefaultAlertsEnabled() : null);

        return enabled;
    }

    /**
     * Resolves alert configuration from account-level (chain) defaults only.
     * Used as a fallback when restaurant is null or when resolving from the top of the hierarchy.
     *
     * @return a ResolvedAlertConfig containing account-level default alert thresholds and enabled status
     */
    private ResolvedAlertConfig resolveFromAccountDefaults() {
        RestaurantChainConfigProperties.RestaurantChainData chain =
                chainConfigProperties != null ? chainConfigProperties.getChain() : null;
        if (chain == null) {
            return ResolvedAlertConfig.builder()
                    .alertsEnabled(true)
                    .build();
        }

        return ResolvedAlertConfig.builder()
                .salesAlertThreshold(chain.getDefaultSalesAlertThreshold())
                .refundAlertPercentage(chain.getDefaultRefundAlertPercentage())
                .cancellationAlertPercentage(chain.getDefaultCancellationAlertPercentage())
                .alertsEnabled(chain.isDefaultAlertsEnabled())
                .build();
    }

    /**
     * Returns the first non-null value from the provided array, or null if all values are null.
     * Used for resolving configuration values from the hierarchy (restaurant → group → chain).
     *
     * @param <T> the type of values
     * @param values the array of values to check
     * @return the first non-null value, or null if all values are null or the array is null
     */
    @SafeVarargs
    private final <T> T firstNonNull(T... values) {
        if (values == null) {
            return null;
        }
        for (T v : values) {
            if (v != null) {
                return v;
            }
        }
        return null;
    }
}

