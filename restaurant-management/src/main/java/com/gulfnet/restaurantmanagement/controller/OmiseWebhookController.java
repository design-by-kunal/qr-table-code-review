package com.gulfnet.restaurantmanagement.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gulfnet.shared_library.model.omise.OmiseWebhookEvent;
import com.gulfnet.restaurantmanagement.service.OmiseWebhookService;
import com.gulfnet.restaurantmanagement.service.OmiseWebhookVerifier;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.LocaleResolver;

import java.util.Locale;

/**
 * Omise Webhook Controller
 *
 * Webhook endpoint URL format:
 * - Production: https://your-domain.com/restaurant/api/v1/omise/webhook
 * - Local Development (ngrok): https://your-ngrok-url.ngrok.io/restaurant/api/v1/omise/webhook
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/omise/webhook")
@RequiredArgsConstructor
public class OmiseWebhookController {

    private final OmiseWebhookService omiseWebhookService;
    private final OmiseWebhookVerifier omiseWebhookVerifier;
    private final ObjectMapper objectMapper;
    private final LocaleResolver localeResolver;

    @PostMapping
    public ResponseEntity<String> handleWebhook(
            HttpServletRequest request,
            @RequestBody String rawBody,
            @RequestHeader(value = "Omise-Signature", required = false) String omiseSignature,
            @RequestHeader(value = "X-Omise-Signature", required = false) String xOmiseSignature,
            @RequestHeader(value = "Omise-Signature-Timestamp", required = false) String omiseTimestamp,
            @RequestHeader(value = "X-Omise-Signature-Timestamp", required = false) String xOmiseTimestamp) {

        String signature = firstNonBlank(omiseSignature, xOmiseSignature);
        String timestamp = firstNonBlank(omiseTimestamp, xOmiseTimestamp);
        Locale locale = localeResolver.resolveLocale(request);

        try {
            omiseWebhookVerifier.verifySignatureOrThrow(rawBody, signature, timestamp, locale);

            OmiseWebhookEvent event = objectMapper.readValue(rawBody, OmiseWebhookEvent.class);
            log.info("Received Omise webhook event: key={}, chargeId={}",
                    event.getKey(),
                    event.getData() != null ? event.getData().getId() : "null");

            omiseWebhookService.processWebhookEvent(event);
            return ResponseEntity.ok("Webhook processed successfully");
        } catch (ResponseStatusException e) {
            log.warn("Omise webhook rejected: {}", e.getReason());
            return ResponseEntity.status(e.getStatusCode()).body(e.getReason());
        } catch (Exception e) {
            log.error("Error processing Omise webhook", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Error processing webhook: " + e.getMessage());
        }
    }

    private static String firstNonBlank(String primary, String fallback) {
        if (primary != null && !primary.isBlank()) {
            return primary;
        }
        return fallback;
    }
}
