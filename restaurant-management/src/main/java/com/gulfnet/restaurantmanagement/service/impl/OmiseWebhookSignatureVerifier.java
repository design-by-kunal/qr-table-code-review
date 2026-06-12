package com.gulfnet.restaurantmanagement.service.impl;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * Verifies Omise webhook {@code Omise-Signature} headers per Omise API docs:
 * HMAC-SHA256 over {@code "{timestamp}.{rawBody}"} using the base64-decoded webhook secret.
 */
public final class OmiseWebhookSignatureVerifier {

    private OmiseWebhookSignatureVerifier() {
    }

    public static boolean isValid(String rawBody, String signatureHeader, String timestampHeader, String webhookSecret) {
        if (webhookSecret == null || webhookSecret.isBlank()) {
            return false;
        }
        if (signatureHeader == null || signatureHeader.isBlank()
                || timestampHeader == null || timestampHeader.isBlank()) {
            return false;
        }
        if (rawBody == null) {
            return false;
        }

        String signedPayload = timestampHeader.trim() + "." + rawBody;
        byte[] secretBytes = decodeWebhookSecret(webhookSecret.trim());
        byte[] expected = hmacSha256(signedPayload, secretBytes);

        for (String signaturePart : signatureHeader.split(",")) {
            if (signaturePart == null || signaturePart.isBlank()) {
                continue;
            }
            byte[] provided = decodeHex(signaturePart.trim());
            if (provided != null && constantTimeEquals(provided, expected)) {
                return true;
            }
        }
        return false;
    }

    private static byte[] decodeWebhookSecret(String webhookSecret) {
        try {
            byte[] decoded = Base64.getDecoder().decode(webhookSecret);
            if (decoded.length > 0) {
                return decoded;
            }
        } catch (IllegalArgumentException ignored) {
            // fall through — treat as raw UTF-8 secret for older/plain env values
        }
        return webhookSecret.getBytes(StandardCharsets.UTF_8);
    }

    private static byte[] hmacSha256(String payload, byte[] secretBytes) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secretBytes, "HmacSHA256"));
            return mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new IllegalStateException("Failed to compute Omise webhook HMAC", e);
        }
    }

    private static byte[] decodeHex(String hex) {
        if (hex.length() % 2 != 0) {
            return null;
        }
        byte[] out = new byte[hex.length() / 2];
        for (int i = 0; i < hex.length(); i += 2) {
            int hi = Character.digit(hex.charAt(i), 16);
            int lo = Character.digit(hex.charAt(i + 1), 16);
            if (hi < 0 || lo < 0) {
                return null;
            }
            out[i / 2] = (byte) ((hi << 4) + lo);
        }
        return out;
    }

    private static boolean constantTimeEquals(byte[] a, byte[] b) {
        return MessageDigest.isEqual(a, b);
    }
}
