package com.gulfnet.restaurantmanagement.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.gulfnet.restaurantmanagement.service.PaymentCredentialService;
import com.gulfnet.restaurantmanagement.service.OmiseService;
import com.gulfnet.restaurantmanagement.util.MessageUtil;
import com.gulfnet.shared_library.exception.InvalidPaymentTypeException;
import com.gulfnet.shared_library.exception.OmiseApiException;
import com.gulfnet.shared_library.exception.OmiseConfigurationException;
import com.gulfnet.shared_library.exception.OmiseResponseException;
import com.gulfnet.shared_library.exception.QrCodeGenerationException;
import com.gulfnet.shared_library.model.dto.PaymentCredentials;
import com.gulfnet.shared_library.model.omise.QrPaymentResponse;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.WriterException;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.net.URLEncoder;
import javax.imageio.ImageIO;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
public class OmiseServiceImpl implements OmiseService {
    
    private final RestTemplate restTemplate;
    private final PaymentCredentialService paymentCredentialService;
    private final MessageUtil messageUtil;
    
    public OmiseServiceImpl(
            @Qualifier("omiseRestTemplate") RestTemplate restTemplate,
            PaymentCredentialService paymentCredentialService,
            MessageUtil messageUtil) {
        this.restTemplate = restTemplate;
        this.paymentCredentialService = paymentCredentialService;
        this.messageUtil = messageUtil;
    }
    
    // API endpoints
    private static final String OMISE_API_BASE_URL = "https://api.omise.co";
    private static final String OMISE_ACCOUNT_URL = OMISE_API_BASE_URL + "/account";
    private static final String AUTH_ERROR_CODE = "authentication_failure";
    private static final String API_SOURCES = "/sources";
    private static final String API_CHARGES = "/charges";
    private static final String API_REFUNDS = "/refunds";
    
    // Return URI
    // NOTE: This must be a publicly accessible HTTPS URL (ngrok) that Omise can redirect the user to.
    private static final String PAYPAY_RETURN_URI = "https://vitrifiable-longheadedly-glennis.ngrok-free.dev/omise/paypay-return";
    private static final String QUERY_PARAM_ORDER_ID = "orderId=";
    
    // Payment configuration
    private static final String CURRENCY_JPY = "JPY";
    private static final String CURRENCY_THB = "THB";
    private static final String CURRENCY_SGD = "SGD";
    private static final String PAYMENT_TYPE_PAYPAY = "paypay";
    private static final String PAYMENT_TYPE_PROMPTPAY = "promptpay";
    private static final String PAYMENT_TYPE_PAYNOW = "paynow";
    
    // Request parameter keys
    private static final String PARAM_TYPE = "type";
    private static final String PARAM_AMOUNT = "amount";
    private static final String PARAM_CURRENCY = "currency";
    private static final String PARAM_RETURN_URI = "return_uri";
    private static final String PARAM_SOURCE = "source";
    private static final String PARAM_METADATA_ORDER_ID = "metadata[orderId]";
    
    // Response field keys
    private static final String FIELD_ID = "id";
    private static final String FIELD_STATUS = "status";
    private static final String FIELD_AUTHORIZE_URI = "authorize_uri";
    private static final String FIELD_AMOUNT = "amount";
    private static final String FIELD_SOURCE = "source";
    private static final String FIELD_SCANNABLE_CODE = "scannable_code";
    private static final String FIELD_IMAGE = "image";
    private static final String FIELD_DOWNLOAD_URI = "download_uri";
    
    // Default/fallback values
    private static final String UNKNOWN = "UNKNOWN";
    
    // HTTP headers
    private static final String HEADER_AUTHORIZATION = "Authorization";
    private static final String AUTH_BASIC_PREFIX = "Basic ";
    
    // QR code generation
    private static final String QR_IMAGE_FORMAT = "png";
    private static final String QR_BASE64_PREFIX = "data:image/png;base64,";
    
    /**
     * Creates a QR code payment using Omise payment gateway.
     * Supports PayPay (JPY), PromptPay (THB), and PayNow (SGD) payment types.
     * For PayPay, generates an authorization URI and creates a QR code.
     * For PromptPay, retrieves a download URI for the QR code image.
     *
     * @param restaurantId the restaurant ID to determine which credentials to use
     * @param amount       the payment amount
     * @param type         the payment type ("paypay", "promptpay", or "paynow")
     * @param orderId      the order ID associated with this payment
     * @return QrPaymentResponse containing charge ID, QR code URL, authorization URI, and status
     * @throws InvalidPaymentTypeException if payment type is invalid or unsupported
     * @throws OmiseApiException if Omise API call fails
     * @throws OmiseResponseException if Omise response is invalid or missing required fields
     * @throws QrCodeGenerationException if QR code generation fails
     */
    @Override
    public QrPaymentResponse createQrPayment(java.util.UUID restaurantId, BigDecimal amount, String type, String orderId) {
        if (type == null) {
            throw new InvalidPaymentTypeException("Payment type is required");
        }
        
        String normalizedType = type.trim().toLowerCase();
        
        if (PAYMENT_TYPE_PAYPAY.equals(normalizedType)) {
            return createPayPayPayment(restaurantId, amount, orderId);
        } else if (PAYMENT_TYPE_PROMPTPAY.equals(normalizedType)) {
            return createPromptPayPayment(restaurantId, amount, orderId);
        } else if (PAYMENT_TYPE_PAYNOW.equals(normalizedType)) {
            return createPayNowPayment(restaurantId, amount, orderId);
        } else {
            throw new InvalidPaymentTypeException("Invalid payment type: " + type + ". Only 'paypay', 'promptpay', and 'paynow' are supported.");
        }
    }
    
    /**
     * Creates a PayPay QR code payment using Omise payment gateway.
     * Creates an Omise source and charge, then generates a QR code from the authorization URI.
     * The payment amount is in JPY (smallest unit).
     *
     * @param restaurantId the restaurant ID to determine which credentials to use
     * @param amount       the payment amount in JPY
     * @param orderId      the order ID associated with this payment
     * @return {@link QrPaymentResponse} containing charge ID, QR code URL (Base64), authorization URI, and status
     * @throws OmiseApiException if Omise API call fails
     * @throws OmiseResponseException if Omise response is invalid or missing required fields
     * @throws QrCodeGenerationException if QR code generation fails
     */
    private QrPaymentResponse createPayPayPayment(java.util.UUID restaurantId, BigDecimal amount, String orderId) {
        String currency = CURRENCY_JPY;
        
        // Get payment credentials (restaurant-specific or chain-level)
        PaymentCredentials credentials = paymentCredentialService.getPaymentCredentials(restaurantId, PAYMENT_TYPE_PAYPAY);
        String secretKey = credentials.getSecretKey();
        
        log.info("Creating PayPay payment for restaurantId={} - Using {} credentials (isRestaurantSpecific={})",
                restaurantId, credentials.isRestaurantSpecific() ? "restaurant-specific" : "chain-level", credentials.isRestaurantSpecific());
        long smallestUnit = amount.longValue();

        // Include orderId in return_uri so our return handler can map back to the transaction.
        // Omise will redirect the user to this URL after they authorize/cancel in PayPay.
        String returnUriWithOrderId = PAYPAY_RETURN_URI + "?" + QUERY_PARAM_ORDER_ID +
                URLEncoder.encode(orderId, StandardCharsets.UTF_8);
    
        HttpHeaders headers = createAuthHeaders(secretKey);
        
        // Create SOURCE
        MultiValueMap<String, String> sourceRequest = new LinkedMultiValueMap<>();
        sourceRequest.add(PARAM_TYPE, PAYMENT_TYPE_PAYPAY);
        sourceRequest.add(PARAM_AMOUNT, String.valueOf(smallestUnit));
        sourceRequest.add(PARAM_CURRENCY, currency);
        sourceRequest.add(PARAM_RETURN_URI, returnUriWithOrderId);
    
        HttpEntity<MultiValueMap<String, String>> sourceEntity = new HttpEntity<>(sourceRequest, headers);
        
        ResponseEntity<JsonNode> sourceResponse;
        try {
            sourceResponse = restTemplate.postForEntity(
                    OMISE_API_BASE_URL + API_SOURCES,
                    sourceEntity,
                    JsonNode.class
            );
        } catch (org.springframework.web.client.HttpClientErrorException e) {
            log.error("Failed to create Omise source: {}", e.getResponseBodyAsString());
            throw new OmiseApiException("Failed to create Omise source: " + e.getResponseBodyAsString(), e);
        } catch (Exception e) {
            log.error("Error creating Omise source", e);
            throw new OmiseApiException("Failed to create Omise source: " + e.getMessage(), e);
        }
    
        JsonNode source = sourceResponse.getBody();
        if (source == null || !source.has(FIELD_ID)) {
            throw new OmiseResponseException("Source ID not found in Omise response");
        }
        
        String sourceId = source.get(FIELD_ID).asText();
    
        // Create CHARGE
        MultiValueMap<String, String> chargeRequest = new LinkedMultiValueMap<>();
        chargeRequest.add(PARAM_AMOUNT, String.valueOf(smallestUnit));
        chargeRequest.add(PARAM_CURRENCY, currency);
        chargeRequest.add(PARAM_SOURCE, sourceId);
        chargeRequest.add(PARAM_RETURN_URI, returnUriWithOrderId);
        chargeRequest.add(PARAM_METADATA_ORDER_ID, orderId);
    
        HttpEntity<MultiValueMap<String, String>> chargeEntity = new HttpEntity<>(chargeRequest, headers);
        
        ResponseEntity<JsonNode> chargeResponse;
        try {
            chargeResponse = restTemplate.postForEntity(
                    OMISE_API_BASE_URL + API_CHARGES,
                    chargeEntity,
                    JsonNode.class
            );
        } catch (org.springframework.web.client.HttpClientErrorException e) {
            log.error("Failed to create Omise charge: {}", e.getResponseBodyAsString());
            throw new OmiseApiException("Failed to create Omise charge: " + e.getResponseBodyAsString(), e);
        } catch (Exception e) {
            log.error("Error creating Omise charge", e);
            throw new OmiseApiException("Failed to create Omise charge: " + e.getMessage(), e);
        }
    
        JsonNode charge = chargeResponse.getBody();
        if (charge == null) {
            throw new OmiseResponseException("Omise charge creation returned null response");
        }
        
        String chargeId = charge.has(FIELD_ID) ? charge.get(FIELD_ID).asText() : UNKNOWN;
        String status = charge.has(FIELD_STATUS) ? charge.get(FIELD_STATUS).asText() : UNKNOWN;
        
        // Extract authorization URI and generate QR code
        String qrUrl = null;
        String authorizeUri = null;
        
        if (charge.has(FIELD_AUTHORIZE_URI)) {
            authorizeUri = charge.get(FIELD_AUTHORIZE_URI).asText();
            try {
                qrUrl = generateQrCodeAsBase64(authorizeUri);
            } catch (Exception e) {
                log.error("Failed to generate QR code from authorization URI", e);
                throw new QrCodeGenerationException("Failed to generate QR code: " + e.getMessage(), e);
            }
        } else {
            log.error("Authorization URI not found in charge response. Charge ID: {}", chargeId);
            throw new OmiseResponseException("Authorization URI not found in Omise charge response");
        }
    
        return QrPaymentResponse.builder()
                .chargeId(chargeId)
                .qrUrl(qrUrl)
                .authorizeUri(authorizeUri)
                .status(status)
                .build();
    }
    
    /**
     * Creates a PromptPay QR code payment using Omise payment gateway.
     * Creates an Omise source and charge, then retrieves the QR code image download URI.
     * The payment amount is converted from THB to satang (smallest unit).
     *
     * @param restaurantId the restaurant ID to determine which credentials to use
     * @param amount       the payment amount in THB
     * @param orderId      the order ID associated with this payment
     * @return {@link QrPaymentResponse} containing charge ID, QR code download URI, and status
     * @throws OmiseApiException if Omise API call fails
     * @throws OmiseResponseException if Omise response is invalid or missing required fields
     */
    private QrPaymentResponse createPromptPayPayment(java.util.UUID restaurantId, BigDecimal amount, String orderId) {
        return createOfflineScannableQrPayment(
                restaurantId, amount, orderId, PAYMENT_TYPE_PROMPTPAY, CURRENCY_THB);
    }

    /**
     * Creates a PayNow QR code payment using Omise (Singapore / SGD).
     * Same offline flow as PromptPay: source, charge, scannable QR download URI.
     */
    private QrPaymentResponse createPayNowPayment(java.util.UUID restaurantId, BigDecimal amount, String orderId) {
        return createOfflineScannableQrPayment(
                restaurantId, amount, orderId, PAYMENT_TYPE_PAYNOW, CURRENCY_SGD);
    }

    /**
     * Offline Omise QR flow (PromptPay, PayNow): create source, create charge, return download_uri for QR image.
     * Amount is converted from major currency units to smallest unit (×100 for THB/SGD).
     */
    private QrPaymentResponse createOfflineScannableQrPayment(
            java.util.UUID restaurantId,
            BigDecimal amount,
            String orderId,
            String paymentType,
            String currency) {

        PaymentCredentials credentials = paymentCredentialService.getPaymentCredentials(restaurantId, paymentType);
        String secretKey = credentials.getSecretKey();

        log.info("Creating {} payment for restaurantId={} - Using {} credentials (isRestaurantSpecific={})",
                paymentType, restaurantId,
                credentials.isRestaurantSpecific() ? "restaurant-specific" : "chain-level",
                credentials.isRestaurantSpecific());

        long smallestUnit = amount.multiply(BigDecimal.valueOf(100)).longValue();
        log.info("{} payment - Amount: {} {}, smallest unit: {}", paymentType, amount, currency, smallestUnit);

        HttpHeaders headers = createAuthHeaders(secretKey);

        MultiValueMap<String, String> sourceRequest = new LinkedMultiValueMap<>();
        sourceRequest.add(PARAM_TYPE, paymentType);
        sourceRequest.add(PARAM_AMOUNT, String.valueOf(smallestUnit));
        sourceRequest.add(PARAM_CURRENCY, currency);

        HttpEntity<MultiValueMap<String, String>> sourceEntity = new HttpEntity<>(sourceRequest, headers);

        log.info("Calling Omise API to create {} source - amount: {}, currency: {}", paymentType, smallestUnit, currency);

        ResponseEntity<JsonNode> sourceResponse;
        try {
            sourceResponse = restTemplate.postForEntity(
                    OMISE_API_BASE_URL + API_SOURCES,
                    sourceEntity,
                    JsonNode.class
            );
        } catch (org.springframework.web.client.HttpClientErrorException e) {
            log.error("Failed to create Omise {} source: {}", paymentType, e.getResponseBodyAsString());
            throw new OmiseApiException("Failed to create Omise " + paymentType + " source: " + e.getResponseBodyAsString(), e);
        } catch (Exception e) {
            log.error("Error creating Omise {} source", paymentType, e);
            throw new OmiseApiException("Failed to create Omise " + paymentType + " source: " + e.getMessage(), e);
        }

        JsonNode source = sourceResponse.getBody();
        if (source == null || !source.has(FIELD_ID)) {
            throw new OmiseResponseException("Source ID not found in Omise " + paymentType + " response");
        }

        String sourceId = source.get(FIELD_ID).asText();
        log.info("{} source created successfully - sourceId: {}", paymentType, sourceId);

        MultiValueMap<String, String> chargeRequest = new LinkedMultiValueMap<>();
        chargeRequest.add(PARAM_AMOUNT, String.valueOf(smallestUnit));
        chargeRequest.add(PARAM_CURRENCY, currency);
        chargeRequest.add(PARAM_SOURCE, sourceId);
        chargeRequest.add(PARAM_METADATA_ORDER_ID, orderId);

        HttpEntity<MultiValueMap<String, String>> chargeEntity = new HttpEntity<>(chargeRequest, headers);

        log.info("Calling Omise API to create {} charge - amount: {}, currency: {}, source: {}",
                paymentType, smallestUnit, currency, sourceId);

        ResponseEntity<JsonNode> chargeResponse;
        try {
            chargeResponse = restTemplate.postForEntity(
                    OMISE_API_BASE_URL + API_CHARGES,
                    chargeEntity,
                    JsonNode.class
            );
        } catch (org.springframework.web.client.HttpClientErrorException e) {
            log.error("Failed to create Omise {} charge: {}", paymentType, e.getResponseBodyAsString());
            throw new OmiseApiException("Failed to create Omise " + paymentType + " charge: " + e.getResponseBodyAsString(), e);
        } catch (Exception e) {
            log.error("Error creating Omise {} charge", paymentType, e);
            throw new OmiseApiException("Failed to create Omise " + paymentType + " charge: " + e.getMessage(), e);
        }

        JsonNode charge = chargeResponse.getBody();
        if (charge == null) {
            throw new OmiseResponseException("Omise " + paymentType + " charge creation returned null response");
        }

        String chargeId = charge.has(FIELD_ID) ? charge.get(FIELD_ID).asText() : UNKNOWN;
        String status = charge.has(FIELD_STATUS) ? charge.get(FIELD_STATUS).asText() : UNKNOWN;

        String downloadUri = extractScannableDownloadUri(charge);
        if (downloadUri == null) {
            log.error("Download URI not found in {} charge response. Charge ID: {}", paymentType, chargeId);
            throw new OmiseResponseException("Download URI not found in Omise " + paymentType + " charge response");
        }

        log.info("{} download URI extracted: {}", paymentType, downloadUri);

        return QrPaymentResponse.builder()
                .chargeId(chargeId)
                .qrUrl(downloadUri)
                .authorizeUri(downloadUri)
                .status(status)
                .build();
    }

    private String extractScannableDownloadUri(JsonNode charge) {
        if (!charge.has(FIELD_SOURCE)) {
            return null;
        }
        JsonNode sourceNode = charge.get(FIELD_SOURCE);
        if (!sourceNode.has(FIELD_SCANNABLE_CODE)) {
            return null;
        }
        JsonNode scannableCode = sourceNode.get(FIELD_SCANNABLE_CODE);
        if (!scannableCode.has(FIELD_IMAGE)) {
            return null;
        }
        JsonNode image = scannableCode.get(FIELD_IMAGE);
        if (!image.has(FIELD_DOWNLOAD_URI)) {
            return null;
        }
        return image.get(FIELD_DOWNLOAD_URI).asText();
    }

    /**
     * Creates a refund for an Omise charge.
     * Automatically determines the currency and selects the appropriate secret key
     * using restaurant-specific or chain-level credentials based on the restaurant and
     * original payment type (PayPay, PromptPay, or PayNow).
     *
     * @param restaurantId the restaurant ID to determine which credentials to use
     * @param chargeId     the Omise charge ID to refund
     * @param amount       the refund amount
     * @param orderId      optional order ID to include in refund metadata
     * @return JsonNode containing the refund response from Omise
     * @throws OmiseApiException if Omise API call fails or charge retrieval fails
     * @throws OmiseResponseException if Omise refund response is null
     */
    @Override
    public Optional<JsonNode> retrieveCharge(UUID restaurantId, String chargeId) {
        if (restaurantId == null || chargeId == null || chargeId.isBlank()) {
            return Optional.empty();
        }

        String[] candidateTypes = { PAYMENT_TYPE_PAYPAY, PAYMENT_TYPE_PROMPTPAY, PAYMENT_TYPE_PAYNOW };
        for (String candidateType : candidateTypes) {
            try {
                PaymentCredentials credentials = paymentCredentialService.getPaymentCredentials(restaurantId, candidateType);
                HttpHeaders getHeaders = createAuthHeaders(credentials.getSecretKey());
                ResponseEntity<JsonNode> chargeResponse = restTemplate.exchange(
                        OMISE_API_BASE_URL + API_CHARGES + "/" + chargeId,
                        HttpMethod.GET,
                        new HttpEntity<>(getHeaders),
                        JsonNode.class);

                JsonNode charge = chargeResponse.getBody();
                if (charge != null && charge.has(FIELD_ID)) {
                    log.info("Retrieved Omise charge {} for restaurantId={} using {} credentials",
                            chargeId, restaurantId, candidateType);
                    return Optional.of(charge);
                }
            } catch (Exception e) {
                log.info("Failed to retrieve charge {} with {} credentials for restaurantId {}: {}",
                        chargeId, candidateType, restaurantId, e.getMessage());
            }
        }
        return Optional.empty();
    }

    @Override
    public JsonNode createRefund(java.util.UUID restaurantId, String chargeId, BigDecimal amount, String orderId) {
        JsonNode charge = retrieveCharge(restaurantId, chargeId)
                .orElseThrow(() -> new OmiseApiException("Failed to retrieve Omise charge details for refund: " + chargeId, null));

        String currency = charge.has(PARAM_CURRENCY) ? charge.get(PARAM_CURRENCY).asText() : null;
        String secretKey = null;
        String[] candidateTypes = { PAYMENT_TYPE_PAYPAY, PAYMENT_TYPE_PROMPTPAY, PAYMENT_TYPE_PAYNOW };
        for (String candidateType : candidateTypes) {
            try {
                PaymentCredentials credentials = paymentCredentialService.getPaymentCredentials(restaurantId, candidateType);
                String candidateSecretKey = credentials.getSecretKey();
                HttpHeaders getHeaders = createAuthHeaders(candidateSecretKey);
                restTemplate.exchange(
                        OMISE_API_BASE_URL + API_CHARGES + "/" + chargeId,
                        HttpMethod.GET,
                        new HttpEntity<>(getHeaders),
                        JsonNode.class);
                secretKey = candidateSecretKey;
                break;
            } catch (Exception e) {
                log.info("Failed to confirm charge {} with {} credentials for restaurantId {}: {}",
                        chargeId, candidateType, restaurantId, e.getMessage());
            }
        }

        if (secretKey == null || currency == null) {
            log.error("Failed to retrieve charge {} for restaurantId {} using any configured credentials", chargeId, restaurantId);
            throw new OmiseApiException("Failed to retrieve Omise charge details for refund: " + chargeId, null);
        }

        // Convert amount to smallest currency unit based on currency
        long amountSmallestUnit;
        if (CURRENCY_THB.equals(currency)) {
            amountSmallestUnit = amount.multiply(BigDecimal.valueOf(100)).longValue();
            log.info("Converting refund amount {} THB to {} satang", amount, amountSmallestUnit);
        } else if (CURRENCY_SGD.equals(currency)) {
            amountSmallestUnit = amount.multiply(BigDecimal.valueOf(100)).longValue();
            log.info("Converting refund amount {} SGD to {} cents", amount, amountSmallestUnit);
        } else {
            amountSmallestUnit = amount.longValue();
            log.info("Using refund amount {} JPY as-is", amount);
        }

        HttpHeaders headers = createAuthHeaders(secretKey);

        MultiValueMap<String, String> refundRequest = new LinkedMultiValueMap<>();
        refundRequest.add(PARAM_AMOUNT, String.valueOf(amountSmallestUnit));
        refundRequest.add(PARAM_CURRENCY, currency);
        if (orderId != null && !orderId.isBlank()) {
            refundRequest.add(PARAM_METADATA_ORDER_ID, orderId);
        }

        HttpEntity<MultiValueMap<String, String>> refundEntity = new HttpEntity<>(refundRequest, headers);

        log.info("Creating Omise refund - restaurantId: {}, chargeId: {}, amount: {} ({} in smallest unit), currency: {}", 
                restaurantId, chargeId, amountSmallestUnit, currency, currency);

        ResponseEntity<JsonNode> refundResponse;
        try {
            refundResponse = restTemplate.postForEntity(
                    OMISE_API_BASE_URL + API_CHARGES + "/" + chargeId + API_REFUNDS,
                    refundEntity,
                    JsonNode.class
            );
        } catch (org.springframework.web.client.HttpClientErrorException e) {
            log.error("Failed to create Omise refund for charge {}: {}", chargeId, e.getResponseBodyAsString());
            throw new OmiseApiException("Failed to create Omise refund: " + e.getResponseBodyAsString(), e);
        } catch (Exception e) {
            log.error("Error creating Omise refund for charge {}", chargeId, e);
            throw new OmiseApiException("Failed to create Omise refund: " + e.getMessage(), e);
        }

        JsonNode refund = refundResponse.getBody();
        if (refund == null) {
            throw new OmiseResponseException("Omise refund creation returned null response");
        }

        log.info("Omise refund created successfully for charge {}: status={}, amount={}",
                chargeId,
                refund.has(FIELD_STATUS) ? refund.get(FIELD_STATUS).asText() : UNKNOWN,
                refund.has(FIELD_AMOUNT) ? refund.get(FIELD_AMOUNT).asLong() : null);

        return refund;
    }

    /**
     * Validates the provided Omise secret key by calling Omise:
     * GET https://api.omise.co/account (Basic Auth).
     *
     * Expected response for valid key:
     * - object = "account"
     *
     * Expected response for invalid key:
     * - object = "error", code = "authentication_failure"
     */
    @Override
    public void validateOmiseSecretKey(String secretKey, Locale locale) {
        if (secretKey == null || secretKey.trim().isEmpty()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    messageUtil.getMessage("restaurant.payment.account.secretKey.required", locale)
            );
        }

        String invalidSecretKeyMessage = messageUtil.getMessage("restaurant.payment.account.secretKey.invalid", locale);

        // Basic auth: base64("<secretKey>:")
        String auth = secretKey + ":";
        byte[] encodedAuth = Base64.getEncoder().encode(auth.getBytes(StandardCharsets.UTF_8));
        String authHeader = AUTH_BASIC_PREFIX + new String(encodedAuth);

        HttpHeaders headers = new HttpHeaders();
        headers.set(HttpHeaders.AUTHORIZATION, authHeader);
        headers.setContentType(MediaType.APPLICATION_JSON);

        HttpEntity<Void> entity = new HttpEntity<>(headers);

        try {
            ResponseEntity<JsonNode> response = restTemplate.exchange(
                    OMISE_ACCOUNT_URL,
                    HttpMethod.GET,
                    entity,
                    JsonNode.class
            );

            JsonNode body = response.getBody();
            if (body == null || !"account".equals(body.path("object").asText())) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        invalidSecretKeyMessage
                );
            }
        } catch (org.springframework.web.client.HttpClientErrorException e) {
            String respBody = e.getResponseBodyAsString();
            if (respBody != null && respBody.contains(AUTH_ERROR_CODE)) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        invalidSecretKeyMessage
                );
            }

            // Any other error is treated as invalid for key-upload use case.
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    invalidSecretKeyMessage
            );
        } catch (Exception e) {
            // Network/timeout/unexpected response: fail closed (do not save unknown keys).
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    invalidSecretKeyMessage
            );
        }
    }
    
    /**
     * Creates HTTP headers with Basic authentication for Omise API requests.
     * Encodes the secret key in Base64 format for Basic authentication.
     *
     * @param secretKey the Omise secret key (must not be null or empty)
     * @return HttpHeaders configured with Basic authentication
     * @throws OmiseConfigurationException if secret key is not configured
     */
    private HttpHeaders createAuthHeaders(String secretKey) {
        HttpHeaders headers = new HttpHeaders();
        
        if (secretKey == null || secretKey.trim().isEmpty()) {
            throw new OmiseConfigurationException("Omise secret key is not configured");
        }
        
        String auth = secretKey + ":";
        byte[] encodedAuth = Base64.getEncoder().encode(auth.getBytes(StandardCharsets.UTF_8));
        String authHeader = AUTH_BASIC_PREFIX + new String(encodedAuth);
        headers.set(HEADER_AUTHORIZATION, authHeader);
        
        return headers;
    }
    
    /**
     * Generates a QR code image from an authorization URI and returns it as a Base64-encoded data URL.
     * Creates a 250x250 pixel QR code image in PNG format.
     *
     * @param authorizationUri the URI to encode in the QR code
     * @return Base64-encoded data URL string (format: "data:image/png;base64,...")
     * @throws QrCodeGenerationException if QR code generation or encoding fails
     */
    private String generateQrCodeAsBase64(String authorizationUri) {
        try {
            QRCodeWriter qrCodeWriter = new QRCodeWriter();
            var bitMatrix = qrCodeWriter.encode(authorizationUri, BarcodeFormat.QR_CODE, 250, 250);
            BufferedImage qrImage = MatrixToImageWriter.toBufferedImage(bitMatrix);
            
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ImageIO.write(qrImage, QR_IMAGE_FORMAT, baos);
            byte[] bytes = baos.toByteArray();
            
            String base64Image = Base64.getEncoder().encodeToString(bytes);
            return QR_BASE64_PREFIX + base64Image;
            
        } catch (WriterException | java.io.IOException e) {
            log.error("Failed to generate QR code", e);
            throw new QrCodeGenerationException("Failed to generate QR code: " + e.getMessage(), e);
        }
    }
}
