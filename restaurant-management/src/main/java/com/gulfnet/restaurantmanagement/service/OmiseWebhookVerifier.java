package com.gulfnet.restaurantmanagement.service;

import java.util.Locale;

/**
 * Validates authenticity of inbound Omise webhook HTTP requests before processing.
 */
public interface OmiseWebhookVerifier {

    void verifySignatureOrThrow(String rawBody, String signatureHeader, String timestampHeader, Locale locale);
}
