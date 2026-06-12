package com.gulfnet.restaurantmanagement.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.gulfnet.shared_library.model.omise.QrPaymentResponse;

import java.math.BigDecimal;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

/**
 * Service for interacting with Omise payment gateway
 */
public interface OmiseService {
    
    /**
     * Creates a QR payment in Omise (PayPay, PromptPay, or PayNow)
     * @param restaurantId Restaurant ID to determine which credentials to use (restaurant-specific or chain-level)
     * @param amount Amount in original currency (JPY for PayPay, THB for PromptPay, SGD for PayNow)
     * @param type Payment type ("paypay", "promptpay", or "paynow")
     * @param orderId Order ID to include in charge metadata for webhook processing
     * @return QrPaymentResponse containing charge ID, QR code, authorization/download URI, and status
     */
    QrPaymentResponse createQrPayment(java.util.UUID restaurantId, BigDecimal amount, String type, String orderId);

    /**
     * Creates a refund for a given Omise charge.
     * Used for non-cash (e.g., UPI/PayPay/PromptPay) refunds.
     * Determines whether to use restaurant-specific or chain-level Omise credentials
     * based on the restaurant and the original payment type.
     *
     * @param restaurantId Restaurant ID to determine which credentials to use
     * @param chargeId     Omise charge ID to refund
     * @param amount       Amount in original currency (e.g. JPY or THB). Will be converted to smallest unit based on charge currency.
     * @param orderId      Order ID to include in refund metadata for webhook processing
     * @return Raw Omise refund response as JsonNode
     */
    JsonNode createRefund(java.util.UUID restaurantId, String chargeId, BigDecimal amount, String orderId);

    /**
     * Retrieves an Omise charge by ID using restaurant-specific or chain-level credentials.
     */
    Optional<JsonNode> retrieveCharge(UUID restaurantId, String chargeId);

    /**
     * Validates a provided Omise secret key by calling Omise:
     * {@code GET https://api.omise.co/account} using Basic auth.
     *
     * @param secretKey Omise secret key to validate
     * @param locale Locale for localized error messages
     */
    void validateOmiseSecretKey(String secretKey, Locale locale);
}
