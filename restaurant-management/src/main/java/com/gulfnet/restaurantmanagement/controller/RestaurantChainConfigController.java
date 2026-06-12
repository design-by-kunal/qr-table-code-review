package com.gulfnet.restaurantmanagement.controller;

import com.gulfnet.restaurantmanagement.config.RestaurantChainConfigProperties;
import com.gulfnet.shared_library.config.AWSService;
import com.gulfnet.shared_library.enums.PaymentGatewayCode;
import com.gulfnet.shared_library.model.response.dto.AccountSettingsDto;
import com.gulfnet.shared_library.model.response.dto.PaymentMethodDto;
import com.gulfnet.shared_library.model.response.dto.ResponseDto;
import com.gulfnet.shared_library.model.response.dto.RestaurantChainDto;
import com.gulfnet.shared_library.model.response.dto.RestaurantChainResponse;
import com.gulfnet.shared_library.model.response.dto.RestaurantChainTranslationDto;
import com.gulfnet.shared_library.model.response.dto.TranslationResponse;
import com.gulfnet.shared_library.security.RSAKeyService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.time.Instant;
import java.util.stream.Collectors;

import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;

@Slf4j
@RestController
@RequestMapping("/api/v1/restaurantchain")
@RequiredArgsConstructor
public class RestaurantChainConfigController {
    private static final long PRESIGNED_URL_CACHE_TTL_SECONDS = 600; // 10 minutes
    private static final Map<String, CachedPresignedUrl> PRESIGNED_URL_CACHE = new ConcurrentHashMap<>();

    private final RestaurantChainConfigProperties configProperties;
    private final MessageSource messageSource;
    private final AWSService awsService;
    private final RSAKeyService rsaKeyService;

    /**
     * Retrieves the complete restaurant chain configuration including translations, supported languages,
     * payment methods, payment gateway enable flags, payment apps, payment settings, the default
     * theme configuration, and receipt page size. Payment method and payment app logos are returned as pre-signed URLs.
     *
     * @return response containing restaurant chain configuration with all settings and account settings
     */
    @GetMapping("/config")
    public ResponseEntity<ResponseDto<RestaurantChainDto<RestaurantChainResponse>>> getChainsConfig() {
        RestaurantChainConfigProperties.RestaurantChainData config = configProperties.getChain();

        List<RestaurantChainTranslationDto> translationDtos = buildTranslationDtos(config);
        List<RestaurantChainResponse.SupportedLanguage> supportedLanguageDtos = buildAndReorderSupportedLanguages(config);
        List<PaymentMethodDto> paymentMethodDtos = buildPaymentMethodDtos(config);
        List<RestaurantChainResponse.PaymentGatewayConfigDto> paymentGatewayDtos = buildPaymentGatewayDtos(config);
        List<RestaurantChainResponse.PaymentSettingDto> paymentSettings = buildPaymentSettings();
        RestaurantChainResponse.ThemeConfigDto themeConfigDto = buildThemeConfig(config);
        RestaurantChainResponse.ReceiptPageSizeDto receiptPageSizeDto = buildReceiptPageSize(config);
        AccountSettingsDto accountSettings = buildAccountSettings(config);

        // Get encryption public key directly from application properties
        String encryptionPublicKey = getEncryptionPublicKey();
        
        RestaurantChainResponse chainResponse = buildChainResponse(config, translationDtos, supportedLanguageDtos,
                paymentMethodDtos, paymentGatewayDtos, paymentSettings, themeConfigDto,
                receiptPageSizeDto, encryptionPublicKey);

        RestaurantChainDto<RestaurantChainResponse> dto = new RestaurantChainDto<>();
        dto.setRestaurantChain(chainResponse);
        dto.setAccountSettings(accountSettings);

        return buildSuccessResponse(dto);
    }

    /**
     * Builds translation DTOs from config.
     */
    private List<RestaurantChainTranslationDto> buildTranslationDtos(
            RestaurantChainConfigProperties.RestaurantChainData config) {
        if (config.getTranslations() == null) {
            return null;
        }
        return config.getTranslations().stream()
                .map(t -> RestaurantChainTranslationDto.builder()
                        .languageCode(t.getLanguageCode())
                        .name(t.getName())
                        .build())
                .collect(Collectors.toList());
    }

    /**
     * Builds and reorders supported languages with default language first.
     */
    private List<RestaurantChainResponse.SupportedLanguage> buildAndReorderSupportedLanguages(
            RestaurantChainConfigProperties.RestaurantChainData config) {
        if (config.getSupportedLanguages() == null) {
            return null;
        }

        List<RestaurantChainResponse.SupportedLanguage> supportedLanguageDtos = config.getSupportedLanguages().stream()
                .map(sl -> RestaurantChainResponse.SupportedLanguage.builder()
                        .languageCode(sl.getLanguageCode())
                        .compulsory(sl.isCompulsory())
                        .build())
                .collect(Collectors.toList());

        if (config.getDefaultLanguageCode() != null) {
            return reorderLanguagesWithDefaultFirst(supportedLanguageDtos, config.getDefaultLanguageCode());
        }
        return supportedLanguageDtos;
    }

    /**
     * Reorders languages to put default language first.
     */
    private List<RestaurantChainResponse.SupportedLanguage> reorderLanguagesWithDefaultFirst(
            List<RestaurantChainResponse.SupportedLanguage> languages, String defaultLanguageCode) {
        List<RestaurantChainResponse.SupportedLanguage> ordered = new java.util.ArrayList<>();
        for (RestaurantChainResponse.SupportedLanguage lang : languages) {
            if (defaultLanguageCode.equalsIgnoreCase(lang.getLanguageCode())) {
                ordered.add(lang);
            }
        }
        for (RestaurantChainResponse.SupportedLanguage lang : languages) {
            if (!defaultLanguageCode.equalsIgnoreCase(lang.getLanguageCode())) {
                ordered.add(lang);
            }
        }
        return ordered;
    }

    /**
     * Builds payment method DTOs with pre-signed URLs.
     */
    private List<PaymentMethodDto> buildPaymentMethodDtos(
            RestaurantChainConfigProperties.RestaurantChainData config) {
        if (config.getPaymentMethods() == null) {
            return null;
        }
        return config.getPaymentMethods().stream()
                .map(this::buildPaymentMethodDto)
                .collect(Collectors.toList());
    }

    private List<RestaurantChainResponse.PaymentGatewayConfigDto> buildPaymentGatewayDtos(
            RestaurantChainConfigProperties.RestaurantChainData config) {
        if (config.getPaymentGateways() == null) {
            return null;
        }
        return config.getPaymentGateways().stream()
                .map(this::buildPaymentGatewayDto)
                .collect(Collectors.toList());
    }

    private RestaurantChainResponse.PaymentGatewayConfigDto buildPaymentGatewayDto(
            RestaurantChainConfigProperties.PaymentGateway gateway) {
        return RestaurantChainResponse.PaymentGatewayConfigDto.builder()
                .type(gateway.getType())
                .enabled(gateway.isEnabled())
                .supportedPaymentApps(buildPaymentAppDtos(gateway.getSupportedPaymentApps()))
                .build();
    }

    /**
     * Builds a single payment method DTO with pre-signed URL.
     */
    private PaymentMethodDto buildPaymentMethodDto(
            RestaurantChainConfigProperties.PaymentMethod paymentMethod) {
        String logoUrl = getPreSignedUrlSafely(paymentMethod.getLogoUrl(), "payment method");
        List<PaymentMethodDto.PaymentMethodTranslationDto> paymentMethodTranslationDtos = buildPaymentMethodTranslations(paymentMethod);
        
        return PaymentMethodDto.builder()
                .type(paymentMethod.getType())
                .logoUrl(logoUrl)
                .translations(paymentMethodTranslationDtos)
                .build();
    }

    /**
     * Builds payment method translation DTOs.
     */
    private List<PaymentMethodDto.PaymentMethodTranslationDto> buildPaymentMethodTranslations(
            RestaurantChainConfigProperties.PaymentMethod paymentMethod) {
        if (paymentMethod.getTranslations() == null) {
            return null;
        }
        return paymentMethod.getTranslations().stream()
                .map(t -> PaymentMethodDto.PaymentMethodTranslationDto.builder()
                        .languageCode(t.getLanguageCode())
                        .name(t.getName())
                        .build())
                .collect(Collectors.toList());
    }

    /**
     * Builds payment app DTOs with pre-signed URLs.
     */
    private List<RestaurantChainResponse.PaymentAppDto> buildPaymentAppDtos(
            List<RestaurantChainConfigProperties.SupportedPaymentApp> apps) {
        if (apps == null) {
            return null;
        }
        return apps.stream()
                .map(this::buildPaymentAppDto)
                .collect(Collectors.toList());
    }

    /**
     * Builds a single payment app DTO with pre-signed URL.
     */
    private RestaurantChainResponse.PaymentAppDto buildPaymentAppDto(
            RestaurantChainConfigProperties.SupportedPaymentApp app) {
        String logoUrl = getPreSignedUrlSafely(app.getLogoUrl(), "payment app");
        List<TranslationResponse> translations = buildPaymentAppTranslations(app);
        
        return RestaurantChainResponse.PaymentAppDto.builder()
                .paymentAppCode(app.getPaymentAppCode())
                .logoUrl(logoUrl)
                .fullRefund(app.isFullRefund())
                .partialRefund(app.isPartialRefund())
                .translations(translations)
                .build();
    }

    /**
     * Builds payment app translation DTOs.
     */
    private List<TranslationResponse> buildPaymentAppTranslations(
            RestaurantChainConfigProperties.SupportedPaymentApp app) {
        if (app.getTranslations() == null) {
            return null;
        }
        return app.getTranslations().stream()
                .map(t -> TranslationResponse.builder()
                        .languageCode(t.getLanguageCode())
                        .name(t.getName())
                        .build())
                .collect(Collectors.toList());
    }

    /**
     * Safely gets pre-signed URL, returning original URL if generation fails.
     */
    private String getPreSignedUrlSafely(String logoUrl, String context) {
        if (logoUrl == null || logoUrl.isEmpty()) {
            return logoUrl;
        }
        CachedPresignedUrl cached = PRESIGNED_URL_CACHE.get(logoUrl);
        Instant now = Instant.now();
        if (cached != null && cached.expiresAt().isAfter(now)) {
            return cached.url();
        }
        try {
            String signedUrl = awsService.getPreSignedUrl(logoUrl);
            PRESIGNED_URL_CACHE.put(logoUrl, new CachedPresignedUrl(
                    signedUrl,
                    now.plusSeconds(PRESIGNED_URL_CACHE_TTL_SECONDS)
            ));
            return signedUrl;
        } catch (Exception e) {
            log.warn("Failed to generate pre-signed URL for {} logo: {}", context, logoUrl, e);
            return logoUrl;
        }
    }

    private record CachedPresignedUrl(String url, Instant expiresAt) {
    }

    /**
     * Builds payment settings for all gateway codes.
     */
    private List<RestaurantChainResponse.PaymentSettingDto> buildPaymentSettings() {
        return Arrays.stream(PaymentGatewayCode.values())
                .map(this::buildPaymentSettingDto)
                .collect(Collectors.toList());
    }

    /**
     * Builds a single payment setting DTO from chain application properties ({@code restaurant.chain.paymentGateways}).
     */
    private RestaurantChainResponse.PaymentSettingDto buildPaymentSettingDto(PaymentGatewayCode gatewayCode) {
        return RestaurantChainResponse.PaymentSettingDto.builder()
                .gatewayCode(gatewayCode.name())
                .isEnabled(configProperties.isPaymentGatewayEnabled(gatewayCode))
                .build();
    }

    /**
     * Builds theme configuration DTO.
     */
    private RestaurantChainResponse.ThemeConfigDto buildThemeConfig(
            RestaurantChainConfigProperties.RestaurantChainData config) {
        if (config.getThemeConfig() == null) {
            return null;
        }
        RestaurantChainConfigProperties.ThemeConfig themeConfig = config.getThemeConfig();
        return RestaurantChainResponse.ThemeConfigDto.builder()
                .primaryColor(themeConfig.getPrimaryColor())
                .secondaryColor(themeConfig.getSecondaryColor())
                .accentColor(themeConfig.getAccentColor())
                .backgroundColor(themeConfig.getBackgroundColor())
                .foregroundColor(themeConfig.getForegroundColor())
                .build();
    }

    /**
     * Builds receipt page size DTO.
     */
    private RestaurantChainResponse.ReceiptPageSizeDto buildReceiptPageSize(
            RestaurantChainConfigProperties.RestaurantChainData config) {
        if (config.getReceiptPageSize() == null) {
            return null;
        }
        return RestaurantChainResponse.ReceiptPageSizeDto.builder()
                .widthMm(config.getReceiptPageSize().getWidthMm())
                .maxHeightMm(config.getReceiptPageSize().getMaxHeightMm())
                .build();
    }

    /**
     * Gets encryption public key directly from application properties via RSAKeyService.
     */
    private String getEncryptionPublicKey() {
        try {
            String publicKey = rsaKeyService.getPublicKeyAsString();
            if (publicKey != null && !publicKey.trim().isEmpty()) {
                log.debug("Successfully retrieved encryption public key from application properties, length: {}", publicKey.length());
                return publicKey;
            } else {
                log.warn("Encryption public key is null or empty in application properties");
            }
        } catch (Exception e) {
            log.error("Failed to get encryption public key from application properties: {}", e.getMessage(), e);
        }
        log.warn("Returning null for encryption public key - API will continue without it");
        return null; // Return null if retrieval fails (optional field)
    }

    /**
     * Builds chain response DTO.
     */
    private RestaurantChainResponse buildChainResponse(
            RestaurantChainConfigProperties.RestaurantChainData config,
            List<RestaurantChainTranslationDto> translationDtos,
            List<RestaurantChainResponse.SupportedLanguage> supportedLanguageDtos,
            List<PaymentMethodDto> paymentMethodDtos,
            List<RestaurantChainResponse.PaymentGatewayConfigDto> paymentGatewayDtos,
            List<RestaurantChainResponse.PaymentSettingDto> paymentSettings,
            RestaurantChainResponse.ThemeConfigDto themeConfigDto,
            RestaurantChainResponse.ReceiptPageSizeDto receiptPageSizeDto,
            String encryptionPublicKey) {
        return RestaurantChainResponse.builder()
                .countryCode(config.getCountryCode())
                .countryName(config.getCountryName())
                .timezone(config.getTimezone())
                .paymentMethods(paymentMethodDtos)
                .paymentGateways(paymentGatewayDtos)
                .paymentType(config.getPaymentType())
                .translations(translationDtos)
                .supportedLanguages(supportedLanguageDtos)
                .defaultLanguageCode(config.getDefaultLanguageCode())
                .qrCodeType(config.getQrCodeType())
                .currency(config.getCurrency())
                .roundingMode(config.getRoundingMode())
                .currencyName(config.getCurrencyName())
                .paymentSettings(paymentSettings)
                .kdsAppVersion(config.getKdsAppVersion())
                .cashierAppVersion(config.getCashierAppVersion())
                .waiterAppVersion(config.getWaiterAppVersion())
                .autoLogoutCashierInMinutes(config.getAutoLogoutCashierInMinutes())
                .themeConfig(themeConfigDto)
                .receiptPageSize(receiptPageSizeDto)
                .encryptionPublicKey(encryptionPublicKey)
                .build();
    }

    /**
     * Builds account settings DTO.
     */
    private AccountSettingsDto buildAccountSettings(
            RestaurantChainConfigProperties.RestaurantChainData config) {
        AccountSettingsDto.TaxSetup taxSetupDto = buildTaxSetup(config);
        AccountSettingsDto.ServiceChargesForDineIn serviceChargesForDineInDto = buildServiceChargesForDineIn(config);
        AccountSettingsDto.ImageDimensions itemImageDimensionsDto = buildImageDimensions(config.getItemImageDimensions());
        AccountSettingsDto.ImageDimensions promotionImageDimensionsDto = buildImageDimensions(config.getPromotionImageDimensions());

        AccountSettingsDto.PackingChargesForTakeaway packingChargesDto = null;
        if (config.getPackingChargesForTakeaway() != null) {
            packingChargesDto = AccountSettingsDto.PackingChargesForTakeaway.builder()
                    .value(config.getPackingChargesForTakeaway().getValue())
                    .type(config.getPackingChargesForTakeaway().getType())
                    .build();
        }
        
        return AccountSettingsDto.builder()
                .itemQuantityLimit(config.getItemQuantityLimit())
                .maxItemsInCombo(config.getMaxItemsInCombo())
                .includePackingChargesForTakeaway(config.isIncludePackingChargesForTakeaway())
                .packingChargesForTakeaway(packingChargesDto)
                .taxSetup(taxSetupDto)
                .serviceChargesForDineIn(serviceChargesForDineInDto)
                .kdsLiveDashboardResetTime(config.getKdsLiveDashboardResetTime())
                .cashierLiveDashboardResetTime(config.getCashierLiveDashboardResetTime())
                .liveDashboardsResetTime(config.getLiveDashboardsResetTime())
                .upperLimitMenuCategoryLevels(config.getUpperLimitMenuCategoryLevels())
                .itemImageDimensions(itemImageDimensionsDto)
                .promotionImageDimensions(promotionImageDimensionsDto)
                .allowCookingRequest(config.isAllowCookingRequest())
                .build();
    }

    /**
     * Builds tax setup DTO.
     */
    private AccountSettingsDto.TaxSetup buildTaxSetup(
            RestaurantChainConfigProperties.RestaurantChainData config) {
        if (config.getTaxSetup() == null) {
            return null;
        }
        RestaurantChainConfigProperties.TaxSetup taxSetup = config.getTaxSetup();
        
        AccountSettingsDto.TaxSetup.DineInTax dineInTax = null;
        if (taxSetup.getDineIn() != null) {
            AccountSettingsDto.TaxSetup.TaxCharge alcoholicTax = null;
            if (taxSetup.getDineIn().getAlcoholic() != null) {
                alcoholicTax = AccountSettingsDto.TaxSetup.TaxCharge.builder()
                        .value(taxSetup.getDineIn().getAlcoholic().getValue())
                        .type(taxSetup.getDineIn().getAlcoholic().getType())
                        .build();
            }
            AccountSettingsDto.TaxSetup.TaxCharge nonAlcoholicTax = null;
            if (taxSetup.getDineIn().getNonAlcoholic() != null) {
                nonAlcoholicTax = AccountSettingsDto.TaxSetup.TaxCharge.builder()
                        .value(taxSetup.getDineIn().getNonAlcoholic().getValue())
                        .type(taxSetup.getDineIn().getNonAlcoholic().getType())
                        .build();
            }
            dineInTax = AccountSettingsDto.TaxSetup.DineInTax.builder()
                    .alcoholic(alcoholicTax)
                    .nonAlcoholic(nonAlcoholicTax)
                    .build();
        }
        
        AccountSettingsDto.TaxSetup.TakeAwayTax takeAwayTax = null;
        if (taxSetup.getTakeAway() != null) {
            AccountSettingsDto.TaxSetup.TaxCharge alcoholicTax = null;
            if (taxSetup.getTakeAway().getAlcoholic() != null) {
                alcoholicTax = AccountSettingsDto.TaxSetup.TaxCharge.builder()
                        .value(taxSetup.getTakeAway().getAlcoholic().getValue())
                        .type(taxSetup.getTakeAway().getAlcoholic().getType())
                        .build();
            }
            AccountSettingsDto.TaxSetup.TaxCharge nonAlcoholicTax = null;
            if (taxSetup.getTakeAway().getNonAlcoholic() != null) {
                nonAlcoholicTax = AccountSettingsDto.TaxSetup.TaxCharge.builder()
                        .value(taxSetup.getTakeAway().getNonAlcoholic().getValue())
                        .type(taxSetup.getTakeAway().getNonAlcoholic().getType())
                        .build();
            }
            takeAwayTax = AccountSettingsDto.TaxSetup.TakeAwayTax.builder()
                    .alcoholic(alcoholicTax)
                    .nonAlcoholic(nonAlcoholicTax)
                    .build();
        }
        
        return AccountSettingsDto.TaxSetup.builder()
                .dineIn(dineInTax)
                .takeAway(takeAwayTax)
                .build();
    }

    /**
     * Builds service charges for dine-in DTO.
     */
    private AccountSettingsDto.ServiceChargesForDineIn buildServiceChargesForDineIn(
            RestaurantChainConfigProperties.RestaurantChainData config) {
        if (config.getServiceChargesForDineIn() == null) {
            return null;
        }
        return AccountSettingsDto.ServiceChargesForDineIn.builder()
                .value(config.getServiceChargesForDineIn().getValue())
                .type(config.getServiceChargesForDineIn().getType())
                .build();
    }

    /**
     * Builds image dimensions DTO.
     */
    private AccountSettingsDto.ImageDimensions buildImageDimensions(
            RestaurantChainConfigProperties.ImageDimensions imageDimensions) {
        if (imageDimensions == null) {
            return null;
        }
        return AccountSettingsDto.ImageDimensions.builder()
                .width(imageDimensions.getWidth())
                .height(imageDimensions.getHeight())
                .build();
    }

    /**
     * Builds success response with localized message.
     * Explicitly sets Content-Type header with UTF-8 charset to ensure proper encoding
     * of Japanese, Thai, and other Unicode characters.
     */
    private ResponseEntity<ResponseDto<RestaurantChainDto<RestaurantChainResponse>>> buildSuccessResponse(
            RestaurantChainDto<RestaurantChainResponse> dto) {
        ResponseDto<RestaurantChainDto<RestaurantChainResponse>> response = ResponseDto
                .<RestaurantChainDto<RestaurantChainResponse>>builder()
                .message(messageSource.getMessage("restaurantchain.config.get.success", null, LocaleContextHolder.getLocale()))
                .data(dto)
                .build();
        
        HttpHeaders headers = new HttpHeaders();
        // Explicitly set Content-Type with UTF-8 charset to ensure proper encoding
        headers.set(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE + ";charset=UTF-8");
        
        return ResponseEntity.ok()
                .headers(headers)
                .body(response);
    }

    /**
     * Retrieves account settings including tax setup, service charges setup, item limits,
     * packing charges configuration, dashboard reset times, image dimensions, and other system settings.
     *
     * @return response containing account settings configuration
     */
    @GetMapping("/account-settings")
    public ResponseEntity<ResponseDto<AccountSettingsDto>> getAccountSettings() {
        RestaurantChainConfigProperties.RestaurantChainData config = configProperties.getChain();

        // tax_service_charges_setup / default_tax_rate / service_charge
        AccountSettingsDto.TaxSetup taxSetup = buildTaxSetup(config);

        AccountSettingsDto.ServiceChargesForDineIn serviceChargesForDineIn = buildServiceChargesForDineIn(config);
        
        AccountSettingsDto.PackingChargesForTakeaway packingChargesForTakeaway = null;
        if (config.getPackingChargesForTakeaway() != null) {
            packingChargesForTakeaway = AccountSettingsDto.PackingChargesForTakeaway.builder()
                    .value(config.getPackingChargesForTakeaway().getValue())
                    .type(config.getPackingChargesForTakeaway().getType())
                    .build();
        }

        // image_dimensions / item_image_dimensions / promotion_image_dimensions
        AccountSettingsDto.ImageDimensions itemImageDimensions = null;
        if (config.getItemImageDimensions() != null) {
            itemImageDimensions = AccountSettingsDto.ImageDimensions.builder()
                .width(config.getItemImageDimensions().getWidth())
                .height(config.getItemImageDimensions().getHeight())
                .build();
        }

        AccountSettingsDto.ImageDimensions promotionImageDimensions = null;
        if (config.getPromotionImageDimensions() != null) {
            promotionImageDimensions = AccountSettingsDto.ImageDimensions.builder()
                .width(config.getPromotionImageDimensions().getWidth())
                .height(config.getPromotionImageDimensions().getHeight())
                .build();
        }

        // dashboard_reset_time, kds_live_dashboards_reset_time, cashier_live_dashboards_reset_time
        // item_quantity_limit, max_items_in_combo, item_combo_limits
        // packing_charges, include_packing_charges_for_takeaway, packing_charges_for_takeaway
        // menu_limits, allow_cooking_request
        AccountSettingsDto dto = AccountSettingsDto.builder()
            .itemQuantityLimit(config.getItemQuantityLimit())
            .maxItemsInCombo(config.getMaxItemsInCombo())
            .includePackingChargesForTakeaway(config.isIncludePackingChargesForTakeaway())
            .packingChargesForTakeaway(packingChargesForTakeaway)
            .taxSetup(taxSetup)
            .serviceChargesForDineIn(serviceChargesForDineIn)
            .kdsLiveDashboardResetTime(config.getKdsLiveDashboardResetTime())
            .cashierLiveDashboardResetTime(config.getCashierLiveDashboardResetTime())
            .liveDashboardsResetTime(config.getLiveDashboardsResetTime())
            .upperLimitMenuCategoryLevels(config.getUpperLimitMenuCategoryLevels())
            .itemImageDimensions(itemImageDimensions)
            .promotionImageDimensions(promotionImageDimensions)
            .allowCookingRequest(config.isAllowCookingRequest())
            .build();

        ResponseDto<AccountSettingsDto> response = ResponseDto.<AccountSettingsDto>builder()
            .message("Success")
            .data(dto)
            .build();

        return ResponseEntity.ok(response);
    }
}
