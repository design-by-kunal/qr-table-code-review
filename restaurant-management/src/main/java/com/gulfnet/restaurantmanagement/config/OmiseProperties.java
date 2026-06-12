package com.gulfnet.restaurantmanagement.config;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
@Getter
@Slf4j
public class OmiseProperties {

    // PayPay keys - loaded from application.properties (which reads from environment variables)
    @Value("${omise.paypay.secret-key}")
    private String paypaySecretKey;

    @Value("${omise.paypay.public-key}")
    private String paypayPublicKey;

    @Value("${omise.paypay.webhook-secret:}")
    private String paypayWebhookSecret;

    // PromptPay keys - loaded from application.properties (which reads from environment variables)
    @Value("${omise.promptpay.secret-key}")
    private String promptpaySecretKey;

    @Value("${omise.promptpay.public-key}")
    private String promptpayPublicKey;

    @Value("${omise.promptpay.webhook-secret:}")
    private String promptpayWebhookSecret;

    // PayNow keys (Singapore / SGD)
    @Value("${omise.paynow.secret-key}")
    private String paynowSecretKey;

    @Value("${omise.paynow.public-key}")
    private String paynowPublicKey;

    @Value("${omise.paynow.webhook-secret:}")
    private String paynowWebhookSecret;

    @Value("${omise.webhook.verify-signature:true}")
    private boolean webhookVerifySignature;

    /**
     * Non-blank webhook signing secrets for each Omise account (PayPay, PromptPay, PayNow).
     * The shared webhook endpoint tries each secret until one validates the signature.
     */
    public List<String> getConfiguredWebhookSecrets() {
        List<String> secrets = new ArrayList<>(3);
        addIfConfigured(secrets, paypayWebhookSecret);
        addIfConfigured(secrets, promptpayWebhookSecret);
        addIfConfigured(secrets, paynowWebhookSecret);
        return secrets;
    }

    private static void addIfConfigured(List<String> secrets, String secret) {
        if (secret != null && !secret.isBlank()) {
            secrets.add(secret.trim());
        }
    }
}
