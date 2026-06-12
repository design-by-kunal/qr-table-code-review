package com.gulfnet.restaurantmanagement.service;

import com.gulfnet.restaurantmanagement.config.OmiseProperties;
import com.gulfnet.restaurantmanagement.service.impl.OmiseWebhookVerifierImpl;
import com.gulfnet.restaurantmanagement.util.MessageUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OmiseWebhookVerifierImplTest {

    private static final Locale LOCALE = Locale.ENGLISH;

    @Mock
    private MessageUtil messageUtil;

    private OmiseProperties omiseProperties;
    private OmiseWebhookVerifierImpl verifier;

    @BeforeEach
    void setUp() {
        omiseProperties = new OmiseProperties();
        ReflectionTestUtils.setField(omiseProperties, "webhookVerifySignature", true);
        verifier = new OmiseWebhookVerifierImpl(omiseProperties, messageUtil);
    }

    @Test
    void acceptsSignatureWhenPaypaySecretMatches() throws Exception {
        String secret = base64Secret("paypay-webhook-secret");
        ReflectionTestUtils.setField(omiseProperties, "paypayWebhookSecret", secret);
        String body = "{\"key\":\"charge.complete\"}";
        String timestamp = "1700000000";
        String signature = sign(body, timestamp, secret);

        assertDoesNotThrow(() -> verifier.verifySignatureOrThrow(body, signature, timestamp, LOCALE));
    }

    @Test
    void acceptsSignatureWhenPromptpaySecretMatchesAmongMultipleAccounts() throws Exception {
        String paypaySecret = base64Secret("paypay-secret");
        String promptpaySecret = base64Secret("promptpay-secret");
        ReflectionTestUtils.setField(omiseProperties, "paypayWebhookSecret", paypaySecret);
        ReflectionTestUtils.setField(omiseProperties, "promptpayWebhookSecret", promptpaySecret);

        String body = "{\"key\":\"charge.complete\"}";
        String timestamp = "1700000001";
        String signature = sign(body, timestamp, promptpaySecret);

        assertDoesNotThrow(() -> verifier.verifySignatureOrThrow(body, signature, timestamp, LOCALE));
    }

    @Test
    void rejectsWhenNoWebhookSecretsConfigured() {
        when(messageUtil.getMessage(eq("payment.omise.webhook.secret.not.configured"), eq(LOCALE)))
                .thenReturn("Webhook not configured");

        ResponseStatusException ex = assertThrows(
                ResponseStatusException.class,
                () -> verifier.verifySignatureOrThrow("{}", "sig", "1700000000", LOCALE));
        assertEquals(HttpStatus.UNAUTHORIZED, ex.getStatusCode());
        assertEquals("Webhook not configured", ex.getReason());
    }

    @Test
    void rejectsWhenSignatureMatchesNoConfiguredSecret() throws Exception {
        String paypaySecret = base64Secret("paypay-secret");
        String promptpaySecret = base64Secret("promptpay-secret");
        ReflectionTestUtils.setField(omiseProperties, "paypayWebhookSecret", paypaySecret);
        ReflectionTestUtils.setField(omiseProperties, "promptpayWebhookSecret", promptpaySecret);
        when(messageUtil.getMessage(eq("payment.omise.webhook.signature.invalid"), any(Locale.class)))
                .thenReturn("Invalid webhook signature");

        String body = "{\"key\":\"charge.complete\"}";
        String timestamp = "1700000002";
        String wrongSecret = base64Secret("other-account-secret");
        String signature = sign(body, timestamp, wrongSecret);

        ResponseStatusException ex = assertThrows(
                ResponseStatusException.class,
                () -> verifier.verifySignatureOrThrow(body, signature, timestamp, LOCALE));
        assertEquals(HttpStatus.UNAUTHORIZED, ex.getStatusCode());
        assertEquals("Invalid webhook signature", ex.getReason());
    }

    private static String base64Secret(String raw) {
        return Base64.getEncoder().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }

    private static String sign(String body, String timestamp, String secret) throws Exception {
        String signedPayload = timestamp + "." + body;
        byte[] secretBytes = Base64.getDecoder().decode(secret);
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secretBytes, "HmacSHA256"));
        byte[] digest = mac.doFinal(signedPayload.getBytes(StandardCharsets.UTF_8));
        StringBuilder sb = new StringBuilder(digest.length * 2);
        for (byte b : digest) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }
}
