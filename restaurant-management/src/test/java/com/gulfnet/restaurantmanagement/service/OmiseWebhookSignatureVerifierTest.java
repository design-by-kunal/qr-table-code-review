package com.gulfnet.restaurantmanagement.service;

import com.gulfnet.restaurantmanagement.service.impl.OmiseWebhookSignatureVerifier;
import org.junit.jupiter.api.Test;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OmiseWebhookSignatureVerifierTest {

    @Test
    void acceptsValidSignature() throws Exception {
        String secret = Base64.getEncoder().encodeToString("test-webhook-secret".getBytes(StandardCharsets.UTF_8));
        String timestamp = "1700000000";
        String body = "{\"key\":\"charge.complete\"}";
        String signedPayload = timestamp + "." + body;
        String signature = hmacHex(signedPayload, Base64.getDecoder().decode(secret));

        assertTrue(OmiseWebhookSignatureVerifier.isValid(body, signature, timestamp, secret));
    }

    @Test
    void rejectsTamperedBody() throws Exception {
        String secret = Base64.getEncoder().encodeToString("test-webhook-secret".getBytes(StandardCharsets.UTF_8));
        String timestamp = "1700000000";
        String body = "{\"key\":\"charge.complete\"}";
        String signedPayload = timestamp + "." + body;
        String signature = hmacHex(signedPayload, Base64.getDecoder().decode(secret));

        assertFalse(OmiseWebhookSignatureVerifier.isValid("{\"key\":\"charge.create\"}", signature, timestamp, secret));
    }

    @Test
    void acceptsAnyMatchingSignatureDuringRotation() throws Exception {
        String secret = Base64.getEncoder().encodeToString("rotation-secret".getBytes(StandardCharsets.UTF_8));
        String timestamp = "1700000001";
        String body = "{\"id\":\"evnt_test\"}";
        String signedPayload = timestamp + "." + body;
        String valid = hmacHex(signedPayload, Base64.getDecoder().decode(secret));
        String invalid = "a".repeat(valid.length());

        assertTrue(OmiseWebhookSignatureVerifier.isValid(body, invalid + "," + valid, timestamp, secret));
    }

    private static String hmacHex(String payload, byte[] secretBytes) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secretBytes, "HmacSHA256"));
        byte[] digest = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
        StringBuilder sb = new StringBuilder(digest.length * 2);
        for (byte b : digest) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }
}
