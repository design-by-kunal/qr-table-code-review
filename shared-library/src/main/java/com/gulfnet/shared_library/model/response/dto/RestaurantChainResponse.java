package com.gulfnet.shared_library.model.response.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.gulfnet.shared_library.enums.EntityStatus;
import com.gulfnet.shared_library.enums.QrCodeType;
import com.gulfnet.shared_library.enums.PaymentSystemType;
import com.gulfnet.shared_library.enums.RoundingMode;

import lombok.*;
import java.util.List;

@AllArgsConstructor
@Getter
@Setter
@ToString
@Builder
@NoArgsConstructor
public class RestaurantChainResponse {
    private String countryCode;
    private String countryName;
    private String timezone;
    private List<PaymentMethodDto> paymentMethods;
    private List<PaymentGatewayConfigDto> paymentGateways;
    private PaymentSystemType paymentType; // PREPAID or POSTPAID
    private String logoUrl;
    private EntityStatus status;
    private List<RestaurantChainTranslationDto> translations;
    private List<RestaurantChainResponse.SupportedLanguage> supportedLanguages;
    private String defaultLanguageCode;
    private QrCodeType qrCodeType;
    private PaymentSystemType paymentSystemType;
    private String currency;
    /**
     * Monetary rounding policy for tax/total line formatting (see {@link com.gulfnet.shared_library.util.CurrencyFormatter}).
     */
    private RoundingMode roundingMode;
    private String currencyName;
    private String kdsAppVersion;
    private String cashierAppVersion;
    private String waiterAppVersion;
    private Boolean waiterDependency;
    private int autoLogoutCashierInMinutes;
    private ThemeConfigDto themeConfig;
    private java.util.List<PaymentSettingDto> paymentSettings;
    private ReceiptPageSizeDto receiptPageSize;
    @JsonInclude(JsonInclude.Include.ALWAYS)
    private String encryptionPublicKey; // RSA public key for encrypting login credentials

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ThemeConfigDto {
        private String primaryColor;
        private String secondaryColor;
        private String accentColor;
        private String backgroundColor;
        private String foregroundColor;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PaymentGatewayConfigDto {
        private String type;
        private boolean enabled;
        private java.util.List<PaymentAppDto> supportedPaymentApps;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PaymentAppDto {
        private String paymentAppCode;
        private String logoUrl;
        private boolean fullRefund;
        private boolean partialRefund;
        private java.util.List<TranslationResponse> translations;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SupportedLanguage {
        private String languageCode;
        private boolean compulsory;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PaymentSettingDto {
        private String gatewayCode;
        private Boolean isEnabled;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ReceiptPageSizeDto {
        private int widthMm;
        private int maxHeightMm;
    }
} 