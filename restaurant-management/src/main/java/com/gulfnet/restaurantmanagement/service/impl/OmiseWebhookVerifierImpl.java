package com.gulfnet.restaurantmanagement.service.impl;

import com.gulfnet.restaurantmanagement.config.OmiseProperties;
import com.gulfnet.restaurantmanagement.service.OmiseWebhookVerifier;
import com.gulfnet.restaurantmanagement.util.MessageUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.Locale;

@Slf4j
@Service
@RequiredArgsConstructor
public class OmiseWebhookVerifierImpl implements OmiseWebhookVerifier {

    private static final String MSG_WEBHOOK_SECRET_NOT_CONFIGURED = "payment.omise.webhook.secret.not.configured";
    private static final String MSG_WEBHOOK_SIGNATURE_INVALID = "payment.omise.webhook.signature.invalid";

    private final OmiseProperties omiseProperties;
    private final MessageUtil messageUtil;

    @Override
    public void verifySignatureOrThrow(String rawBody, String signatureHeader, String timestampHeader, Locale locale) {
        if (!omiseProperties.isWebhookVerifySignature()) {
            log.warn("Omise webhook signature verification is disabled");
            return;
        }

        Locale resolvedLocale = locale != null ? locale : Locale.ENGLISH;
        var secrets = omiseProperties.getConfiguredWebhookSecrets();
        if (secrets.isEmpty()) {
            log.error(
                    "Omise webhook signature verification enabled but no webhook secrets are configured "
                            + "(OMISE_PAYPAY_WEBHOOK_SECRET, OMISE_PROMPTPAY_WEBHOOK_SECRET, OMISE_PAYNOW_WEBHOOK_SECRET)");
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    messageUtil.getMessage(MSG_WEBHOOK_SECRET_NOT_CONFIGURED, resolvedLocale));
        }

        for (String secret : secrets) {
            if (OmiseWebhookSignatureVerifier.isValid(rawBody, signatureHeader, timestampHeader, secret)) {
                return;
            }
        }

        log.warn("Rejected Omise webhook: signature did not match any configured account webhook secret");
        throw new ResponseStatusException(
                HttpStatus.UNAUTHORIZED,
                messageUtil.getMessage(MSG_WEBHOOK_SIGNATURE_INVALID, resolvedLocale));
    }
}
