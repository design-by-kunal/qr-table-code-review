package com.gulfnet.restaurantmanagement.util;

import com.gulfnet.restaurantmanagement.config.RestaurantChainConfigProperties;
import com.gulfnet.shared_library.entity.Restaurant;
import com.gulfnet.shared_library.util.CurrencyFormatter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;

/**
 * Utility class for receipt generation methods shared between ReceiptService and RefundReceiptService.
 * This class helps avoid code duplication for common receipt formatting and localization methods.
 */
@Slf4j
@Component
public class ReceiptUtil {

    /**
     * Gets the localized restaurant name based on the provided locale.
     * Falls back to restaurant code or "Restaurant" if translation is not available.
     *
     * @param restaurant The restaurant entity
     * @param locale The target locale
     * @return The localized restaurant name
     */
    public String getRestaurantName(Restaurant restaurant, Locale locale) {
        Locale target = locale != null ? locale : Locale.ENGLISH;
        return getLocalizedName(restaurant.getTranslations(), target,
                restaurant.getRestaurantCode() != null ? restaurant.getRestaurantCode() : "Restaurant");
    }

    /**
     * Resolves a payment method display name using chain configuration translations.
     * Falls back to the raw {@code paymentMethodType} when not configured.
     */
    public String getPaymentMethodDisplayName(String paymentMethodType,
                                              RestaurantChainConfigProperties.RestaurantChainData chain,
                                              Locale locale) {
        if (paymentMethodType == null) {
            return "";
        }
        String normalized = paymentMethodType.trim();
        if (normalized.isEmpty()) {
            return "";
        }
        if (chain == null || chain.getPaymentMethods() == null || chain.getPaymentMethods().isEmpty()) {
            return normalized;
        }

        for (RestaurantChainConfigProperties.PaymentMethod pm : chain.getPaymentMethods()) {
            if (pm == null || pm.getType() == null) {
                continue;
            }
            if (normalized.equalsIgnoreCase(pm.getType().trim())) {
                if (pm.getTranslations() == null || pm.getTranslations().isEmpty()) {
                    return pm.getType();
                }
                return getLocalizedName(pm.getTranslations(), locale, pm.getType());
            }
        }

        return normalized;
    }

    /**
     * Gets the localized name from a list of translation objects.
     * First tries to find a translation matching the provided locale,
     * then falls back to English, and finally to the default name.
     *
     * @param translations List of translation objects
     * @param locale The target locale
     * @param defaultName The default name to use if no translation is found
     * @return The localized name
     */
    public String getLocalizedName(List<? extends Object> translations, Locale locale, String defaultName) {
        try {
            String lang = locale != null ? locale.getLanguage() : "en";
            return translations.stream()
                    .map(t -> (Object) t)
                    .filter(t -> {
                        try {
                            String lc = (String) t.getClass().getMethod("getLanguageCode").invoke(t);
                            return lang.equalsIgnoreCase(lc);
                        } catch (Exception ignored) { return false; }
                    })
                    .findFirst()
                    .map(t -> {
                        try { return (String) t.getClass().getMethod("getName").invoke(t); }
                        catch (Exception e) { return defaultName; }
                    })
                    .orElseGet(() -> translations.stream()
                            .filter(t -> {
                                try {
                                    String lc = (String) t.getClass().getMethod("getLanguageCode").invoke(t);
                                    return "en".equalsIgnoreCase(lc);
                                } catch (Exception ignored) { return false; }
                            })
                            .findFirst()
                            .map(t -> {
                                try { return (String) t.getClass().getMethod("getName").invoke(t); }
                                catch (Exception e) { return defaultName; }
                            })
                            .orElse(defaultName));
        } catch (Exception e) {
            return defaultName;
        }
    }

    /**
     * Escapes HTML special characters in a string to prevent XSS attacks.
     *
     * @param text The text to escape
     * @return The escaped HTML string
     */
    public String escapeHtml(String text) {
        if (text == null) return "";
        return text.replace("&", "&amp;")
                   .replace("<", "&lt;")
                   .replace(">", "&gt;")
                   .replace("\"", "&quot;")
                   .replace("'", "&#39;");
    }

    /**
     * Appends a standard total line block to the HTML receipt.
     *
     * @param html           The StringBuilder for HTML content
     * @param label          The localized label to display (without trailing colon)
     * @param amount         The monetary amount to display
     * @param currencySymbol The currency symbol prefix
     * @param negative       Whether to prefix the amount with a minus sign (for discounts/refunds)
     * @param finalLine      Whether this is the final total line (renders with bold and total-final class)
     */
    public void appendTotalLine(StringBuilder html,
                                String label,
                                BigDecimal amount,
                                String currencySymbol,
                                boolean negative,
                                boolean finalLine) {
        if (amount == null) {
            return;
        }

        BigDecimal formattedAmount = CurrencyFormatter.formatAmount(amount, currencySymbol);
        String divClass = finalLine ? "total-line total-final" : "total-line";

        html.append("<div class='").append(divClass).append("'>");
        html.append("<span class='total-label'><strong>")
                .append(label)
                .append(":</strong></span>");

        html.append("<span class='total-value'>");
        if (negative) {
            html.append("-");
        }
        if (finalLine) {
            html.append("<strong>");
        }
        html.append(currencySymbol).append(formattedAmount);
        if (finalLine) {
            html.append("</strong>");
        }
        html.append("</span>");
        html.append("</div>");
    }
}
