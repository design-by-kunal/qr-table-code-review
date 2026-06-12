package com.gulfnet.restaurantmanagement.service;

import com.gulfnet.shared_library.model.dto.PaymentCredentials;

import java.util.UUID;

public interface PaymentCredentialService {
    /**
     * Gets payment credentials for a restaurant and payment type.
     * Returns restaurant-specific credentials if available, otherwise returns chain-level credentials.
     *
     * @param restaurantId the UUID of the restaurant
     * @param paymentType  the payment type (e.g., "paypay", "promptpay", "paynow")
     * @return PaymentCredentials containing public key, secret key, and whether it's restaurant-specific
     */
    PaymentCredentials getPaymentCredentials(UUID restaurantId, String paymentType);
}
