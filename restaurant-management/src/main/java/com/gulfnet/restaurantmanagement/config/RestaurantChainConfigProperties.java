package com.gulfnet.restaurantmanagement.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import jakarta.annotation.PostConstruct;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.logging.Logger;

import com.gulfnet.shared_library.enums.QrCodeType;
import com.gulfnet.shared_library.enums.PaymentSystemType;
import com.gulfnet.shared_library.enums.PaymentGatewayCode;
import com.gulfnet.shared_library.enums.ChargeType;
import com.gulfnet.shared_library.enums.RoundingMode;
 

@Configuration
@ConfigurationProperties(prefix = "restaurant")
@Data
@SuppressWarnings("java:S1068") // Fields are used via Lombok-generated getters/setters
public class RestaurantChainConfigProperties {
    
    private static final Logger log = Logger.getLogger(RestaurantChainConfigProperties.class.getName());
    
    private RestaurantChainData chain;

    /**
     * Whether a payment gateway is enabled for this chain, from {@code restaurant.chain.paymentGateways}
     * ({@code type} must match {@link PaymentGatewayCode} name, case-insensitive).
     */
    public boolean isPaymentGatewayEnabled(PaymentGatewayCode code) {
        if (chain == null || chain.getPaymentGateways() == null) {
            return false;
        }
        String codeName = code.name();
        for (PaymentGateway pg : chain.getPaymentGateways()) {
            if (pg.getType() != null && codeName.equalsIgnoreCase(pg.getType().trim())) {
                return pg.isEnabled();
            }
        }
        return false;
    }

    /**
     * Whether customer orders require waiter review before reaching KDS.
     * Defaults to true to preserve the existing behavior when not configured.
     */
    public boolean isWaiterDependencyEnabled() {
        if (chain == null || chain.getWaiterDependency() == null) {
            return true;
        }
        return chain.getWaiterDependency();
    }
    
    /**
     * Fixes encoding issues after properties are loaded.
     * If properties were loaded with wrong encoding (e.g., ISO-8859-1 instead of UTF-8),
     * this method attempts to correct them by re-encoding.
     */
    @PostConstruct
    public void fixEncoding() {
        if (chain != null) {
            fixChainEncoding(chain);
        }
    }

    /**
     * Normalizes display-name strings on the bound {@code restaurant.chain} graph after YAML/properties
     * binding, using {@link #fixStringEncoding(String)} on each {@link Translation#getName()} for:
     * payment method translations, chain-level translations, supported payment apps (chain and per-gateway),
     * via {@link #fixSupportedPaymentAppEncoding(SupportedPaymentApp)} for nested app rows.
     *
     * @param chain populated chain configuration to mutate in place
     */
    private void fixChainEncoding(RestaurantChainData chain) {
        // Fix payment method translations
        if (chain.getPaymentMethods() != null) {
            for (PaymentMethod pm : chain.getPaymentMethods()) {
                if (pm.getTranslations() != null) {
                    for (Translation t : pm.getTranslations()) {
                        t.setName(fixStringEncoding(t.getName()));
                    }
                }
            }
        }
        
        // Fix chain translations
        if (chain.getTranslations() != null) {
            for (Translation t : chain.getTranslations()) {
                t.setName(fixStringEncoding(t.getName()));
            }
        }
        
        // Fix payment app translations (legacy chain-level) + gateway-nested
        if (chain.getSupportedPaymentApps() != null) {
            for (SupportedPaymentApp app : chain.getSupportedPaymentApps()) {
                fixSupportedPaymentAppEncoding(app);
            }
        }
        if (chain.getPaymentGateways() != null) {
            for (PaymentGateway pg : chain.getPaymentGateways()) {
                if (pg.getSupportedPaymentApps() != null) {
                    for (SupportedPaymentApp app : pg.getSupportedPaymentApps()) {
                        fixSupportedPaymentAppEncoding(app);
                    }
                }
            }
        }
    }

    private void fixSupportedPaymentAppEncoding(SupportedPaymentApp app) {
        if (app == null || app.getTranslations() == null) {
            return;
        }
        for (Translation t : app.getTranslations()) {
            t.setName(fixStringEncoding(t.getName()));
        }
    }
    
    /**
     * Attempts to fix incorrectly encoded strings.
     * If UTF-8 text was read as ISO-8859-1 (or similar single-byte interpretation), each byte becomes
     * a U+00xx code point; the result looks like gibberish (e.g. {@code QRã‚³ãƒ¼ãƒ‰æ±ºæ¸ˆ} instead of {@code QRコード決済}).
     * <p>
     * The previous heuristic returned early whenever any char was {@code > 127}, which matched Latin-1 mojibake
     * too and skipped the fix. We only attempt ISO-8859-1 → UTF-8 when the string looks like that case
     * (several U+0080–U+00FF chars and no real CJK/Thai scripts), and we accept the result only if the
     * recovered bytes are strict, well-formed UTF-8 and decode without replacement characters.
     */
    private String fixStringEncoding(String value) {
        if (value == null || value.isEmpty()) {
            return value;
        }

        if (!looksLikeUtf8MisreadAsLatin1(value)) {
            return value;
        }

        try {
            byte[] isoBytes = value.getBytes(StandardCharsets.ISO_8859_1);
            if (!isStrictWellFormedUtf8(isoBytes)) {
                return value;
            }
            String fixed = new String(isoBytes, StandardCharsets.UTF_8);
            if (fixed.equals(value)) {
                return value;
            }
            if (fixed.indexOf('\uFFFD') >= 0) {
                return value;
            }
            if (log.isLoggable(java.util.logging.Level.FINE)) {
                log.fine("Fixed encoding: " + value + " -> " + fixed);
            }
            return fixed;
        } catch (Exception e) {
            if (log.isLoggable(java.util.logging.Level.FINEST)) {
                log.finest("Could not fix encoding for: " + value);
            }
            return value;
        }
    }

    /**
     * True when the string likely holds UTF-8 octets wrongly mapped to Latin-1 code points (mojibake),
     * not when it already contains real Japanese/Thai/CJK codepoints.
     */
    private boolean looksLikeUtf8MisreadAsLatin1(String str) {
        if (str == null || str.length() < 2) {
            return false;
        }
        int latin1SupplementCount = 0;
        for (int i = 0; i < str.length(); i++) {
            char c = str.charAt(i);
            if (c >= 0x80 && c <= 0xFF) {
                latin1SupplementCount++;
            }
            if (containsEastAsianOrThaiScriptChar(c)) {
                return false;
            }
        }
        // Need at least two bytes in 0x80–0xFF to be plausible UTF-8 multibyte noise; avoids "café"-style single accent.
        return latin1SupplementCount >= 2;
    }

    /**
     * Whether {@code c} lies in a Unicode block that indicates real East Asian or Thai script (as opposed
     * to Latin-1 supplement mojibake in the U+0080–U+00FF range). Used by {@link #looksLikeUtf8MisreadAsLatin1}
     * to bail out when the string already contains legitimate CJK/Thai characters.
     *
     * @param c code unit to classify
     * @return {@code true} if {@code c} is Hiragana, Katakana, CJK ideographs (incl. extensions A/B), CJK
     *         symbols/punctuation, halfwidth/fullwidth forms, or Thai
     */
    private boolean containsEastAsianOrThaiScriptChar(char c) {
        Character.UnicodeBlock block = Character.UnicodeBlock.of(c);
        if (block == null) {
            return false;
        }
        return block == Character.UnicodeBlock.HIRAGANA
                || block == Character.UnicodeBlock.KATAKANA
                || block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS
                || block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_A
                || block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_B
                || block == Character.UnicodeBlock.CJK_SYMBOLS_AND_PUNCTUATION
                || block == Character.UnicodeBlock.HALFWIDTH_AND_FULLWIDTH_FORMS
                || block == Character.UnicodeBlock.THAI;
    }

    private static boolean isStrictWellFormedUtf8(byte[] bytes) {
        CharsetDecoder decoder = StandardCharsets.UTF_8.newDecoder();
        decoder.onMalformedInput(CodingErrorAction.REPORT);
        decoder.onUnmappableCharacter(CodingErrorAction.REPORT);
        try {
            decoder.decode(ByteBuffer.wrap(bytes));
            return true;
        } catch (CharacterCodingException e) {
            return false;
        }
    }

    @Data
    @SuppressWarnings("java:S1068")
    public static class RestaurantChainData {
        private String countryCode;
        private String countryName;
        private String timezone;
        private List<PaymentMethod> paymentMethods;
        private List<PaymentGateway> paymentGateways;
        private PaymentSystemType paymentType;
        private String currency;
        private String currencyName;
        /**
         * Rounding mode applied when formatting calculated tax amounts at the currency scale.
         * Defaults to {@link RoundingMode#ROUND_HALF_UP} when not configured.
         */
        private RoundingMode roundingMode;
        private List<Translation> translations;
        private List<SupportedLanguage> supportedLanguages;
        private String defaultLanguageCode;
        private int itemQuantityLimit;
        private int maxItemsInCombo;
        private boolean includePackingChargesForTakeaway;
        private PackingChargesForTakeaway packingChargesForTakeaway;
        private TaxSetup taxSetup;
        private ServiceChargesForDineIn serviceChargesForDineIn;
        private String kdsLiveDashboardResetTime;
        private String cashierLiveDashboardResetTime;
        private String liveDashboardsResetTime;
        /**
         * Hours added after the latest operating-hours closing ({@code toTime}) before the business day
         * rolls over for order-number sequencing and unused-session expiry. Defaults to 1 when unset.
         */
        private int operatingHoursExtendHoursAfterClose = 1;
        private int upperLimitMenuCategoryLevels;
        private ImageDimensions itemImageDimensions;
        private ImageDimensions promotionImageDimensions;
        private boolean allowCookingRequest;
        private QrCodeType qrCodeType;
        private List<SupportedPaymentApp> supportedPaymentApps;
        private String kdsAppVersion;
        private String cashierAppVersion;
        private String waiterAppVersion;
        private Boolean waiterDependency;
        private int autoLogoutCashierInMinutes;
        private ThemeConfig themeConfig;
        private ReceiptPageSize receiptPageSize;
        
        // Account-level default alert configuration for HQ Admin notifications
        private java.math.BigDecimal defaultSalesAlertThreshold;
        private java.math.BigDecimal defaultRefundAlertPercentage;
        private java.math.BigDecimal defaultCancellationAlertPercentage;
        private boolean defaultAlertsEnabled;
    }

    @Data
    @SuppressWarnings("java:S1068")
    public static class SupportedPaymentApp {
        /** Matches request {@code type} / transaction {@code payment_app} (e.g. paypay, card). */
        private String paymentAppCode;
        private String logoUrl;
        private boolean fullRefund;
        private boolean partialRefund;
        private List<Translation> translations;
    }

    @Data
    @SuppressWarnings("java:S1068")
    public static class PaymentMethod {
        private String type;
        private List<Translation> translations;
        private String logoUrl;
    }

    @Data
    @SuppressWarnings("java:S1068")
    public static class PaymentGateway {
        private String type;
        private boolean enabled;
        private List<SupportedPaymentApp> supportedPaymentApps;
    }

    @Data
    @SuppressWarnings("java:S1068")
    public static class Translation {
        private String languageCode;
        private String name;
    }

    @Data
    @SuppressWarnings("java:S1068")
    public static class SupportedLanguage {
        private String languageCode;
        private boolean compulsory;
    }

    @Data
    public static class PackingChargesForTakeaway {
        private double value;
        private ChargeType type;
    }

    @Data
    @SuppressWarnings("java:S1068")
    public static class TaxSetup {
        private DineInTax dineIn;
        private TakeAwayTax takeAway;
        
        @Data
        public static class DineInTax {
            private TaxCharge alcoholic;
            private TaxCharge nonAlcoholic;
        }
        
        @Data
        public static class TakeAwayTax {
            private TaxCharge alcoholic;
            private TaxCharge nonAlcoholic;
        }
        
        @Data
        public static class TaxCharge {
            private double value;
            private ChargeType type;
        }
    }

    @Data
    @SuppressWarnings("java:S1068")
    public static class ServiceChargesForDineIn {
            private double value;
            private ChargeType type;
    }

    @Data
    @SuppressWarnings("java:S1068")
    public static class ImageDimensions {
        private int width;
        private int height;
    }

    @Data
    @SuppressWarnings("java:S1068")
    public static class ThemeConfig {
        private String primaryColor;
        private String secondaryColor;
        private String accentColor;
        private String backgroundColor;
        private String foregroundColor;
    }

    @Data
    @SuppressWarnings("java:S1068")
    public static class ReceiptPageSize {
        private int widthMm; // Receipt width in millimeters (72 or 80)
        private int maxHeightMm; // Maximum height in millimeters (0 for unlimited/continuous)
    }
} 