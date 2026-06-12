package com.gulfnet.integrationmanagement.controller;

import com.gulfnet.integrationmanagement.service.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * FCM controller providing comprehensive Firebase Cloud Messaging API
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/fcm")
@RequiredArgsConstructor
public class FcmController {

    private final FcmService fcmService;
    
    /**
     * Response keys constants for FCM API responses.
     * Following industry standard practice of using constants for response field names.
     */
    private static class ResponseKeys {
        static final String SUCCESS = "success";
        static final String ERROR = "error";
        static final String MESSAGE_ID = "messageId";
        static final String SUCCESS_COUNT = "successCount";
        static final String FAILURE_COUNT = "failureCount";
        static final String FAILED_TOKENS = "failedTokens";
        static final String MESSAGE = "message";
        static final String VALID = "valid";
        
        private ResponseKeys() {
            // Utility class - prevent instantiation
        }
    }
    
    /**
     * Request parameter keys constants for FCM API requests.
     */
    private static class RequestKeys {
        static final String TOPIC = "topic";
        static final String TOKENS = "tokens";
        
        private RequestKeys() {
            // Utility class - prevent instantiation
        }
    }
    
    /**
     * Helper method to build FcmMessage from request map
     */
    private FcmMessage buildFcmMessage(Map<String, Object> request) {
        String title = (String) request.getOrDefault("title", "Default Title");
        String body = (String) request.getOrDefault("body", "Default Body");
        @SuppressWarnings("unchecked")
        Map<String, String> data = (Map<String, String>) request.get("data");
        
        return FcmMessage.builder()
                .title(title)
                .body(body)
                .data(data)
                .priority(FcmMessage.FcmMessagePriority.HIGH)
                .messageType(FcmMessage.FcmMessageType.NOTIFICATION_WITH_DATA)
                .build();
    }

    /**
     * Send FCM message to a single token
     */
    @PostMapping("/send/token")
    public ResponseEntity<Map<String, Object>> sendToToken(@RequestBody Map<String, Object> request) {
        try {
            String token = (String) request.get("token");
            FcmMessage message = buildFcmMessage(request);
            
            FcmSendResponse response = fcmService.sendToToken(token, message);
            
            return ResponseEntity.ok(Map.of(
                    ResponseKeys.SUCCESS, response.isSuccess(),
                    ResponseKeys.MESSAGE_ID, response.getMessageId() != null ? response.getMessageId() : "",
                    ResponseKeys.ERROR, response.getErrorMessage() != null ? response.getErrorMessage() : ""
            ));
        } catch (Exception e) {
            log.error("Failed to send FCM message to token: {}", e.getMessage(), e);
            return ResponseEntity.badRequest().body(Map.of(
                    ResponseKeys.SUCCESS, false,
                    ResponseKeys.ERROR, e.getMessage()
            ));
        }
    }
    
    /**
     * Send FCM message to multiple tokens
     */
    @PostMapping("/send/tokens")
    public ResponseEntity<Map<String, Object>> sendToTokens(@RequestBody Map<String, Object> request) {
        try {
            @SuppressWarnings("unchecked")
            List<String> tokens = (List<String>) request.get(RequestKeys.TOKENS);
            FcmMessage message = buildFcmMessage(request);
            
            FcmSendResponse response = fcmService.sendToTokens(tokens, message);
            
            return ResponseEntity.ok(Map.of(
                    ResponseKeys.SUCCESS, response.isSuccess(),
                    ResponseKeys.SUCCESS_COUNT, response.getSuccessCount(),
                    ResponseKeys.FAILURE_COUNT, response.getFailureCount(),
                    ResponseKeys.FAILED_TOKENS, response.getFailedTokens() != null ? response.getFailedTokens() : List.of(),
                    ResponseKeys.ERROR, response.getErrorMessage() != null ? response.getErrorMessage() : ""
            ));
            
        } catch (Exception e) {
            log.error("Failed to send FCM message to tokens: {}", e.getMessage(), e);
            return ResponseEntity.badRequest().body(Map.of(
                    ResponseKeys.SUCCESS, false,
                    ResponseKeys.ERROR, e.getMessage()
            ));
        }
    }
    
    /**
     * Send FCM message to a topic
     */
    @PostMapping("/send/topic")
    public ResponseEntity<Map<String, Object>> sendToTopic(@RequestBody Map<String, Object> request) {
        try {
            String topic = (String) request.get(RequestKeys.TOPIC);
            FcmMessage message = buildFcmMessage(request);
            
            FcmSendResponse response = fcmService.sendToTopic(topic, message);
            
            return ResponseEntity.ok(Map.of(
                    ResponseKeys.SUCCESS, response.isSuccess(),
                    ResponseKeys.MESSAGE_ID, response.getMessageId() != null ? response.getMessageId() : "",
                    ResponseKeys.ERROR, response.getErrorMessage() != null ? response.getErrorMessage() : ""
            ));
            
        } catch (Exception e) {
            log.error("Failed to send FCM message to topic: {}", e.getMessage(), e);
            return ResponseEntity.badRequest().body(Map.of(
                    ResponseKeys.SUCCESS, false,
                    ResponseKeys.ERROR, e.getMessage()
            ));
        }
    }
    
    /**
     * Send FCM message using a condition
     */
    @PostMapping("/send/condition")
    public ResponseEntity<Map<String, Object>> sendToCondition(@RequestBody Map<String, Object> request) {
        try {
            String condition = (String) request.get("condition");
            FcmMessage message = buildFcmMessage(request);
            
            FcmSendResponse response = fcmService.sendToCondition(condition, message);
            
            return ResponseEntity.ok(Map.of(
                    ResponseKeys.SUCCESS, response.isSuccess(),
                    ResponseKeys.MESSAGE_ID, response.getMessageId() != null ? response.getMessageId() : "",
                    ResponseKeys.ERROR, response.getErrorMessage() != null ? response.getErrorMessage() : ""
            ));
            
        } catch (Exception e) {
            log.error("Failed to send FCM message with condition: {}", e.getMessage(), e);
            return ResponseEntity.badRequest().body(Map.of(
                    ResponseKeys.SUCCESS, false,
                    ResponseKeys.ERROR, e.getMessage()
            ));
        }
    }
    
    /**
     * Subscribe tokens to a topic
     */
    @PostMapping("/topic/subscribe")
    public ResponseEntity<Map<String, Object>> subscribeToTopic(@RequestBody Map<String, Object> request) {
        try {
            @SuppressWarnings("unchecked")
            List<String> tokens = (List<String>) request.get(RequestKeys.TOKENS);
            String topic = (String) request.get(RequestKeys.TOPIC);
            
            boolean success = fcmService.subscribeToTopic(tokens, topic);
            
            return ResponseEntity.ok(Map.of(
                    ResponseKeys.SUCCESS, success,
                    ResponseKeys.MESSAGE, success ? "Successfully subscribed to topic" : "Failed to subscribe to topic"
            ));
            
        } catch (Exception e) {
            log.error("Failed to subscribe to topic: {}", e.getMessage(), e);
            return ResponseEntity.badRequest().body(Map.of(
                    ResponseKeys.SUCCESS, false,
                    ResponseKeys.ERROR, e.getMessage()
            ));
        }
    }
    
    /**
     * Unsubscribe tokens from a topic
     */
    @PostMapping("/topic/unsubscribe")
    public ResponseEntity<Map<String, Object>> unsubscribeFromTopic(@RequestBody Map<String, Object> request) {
        try {
            @SuppressWarnings("unchecked")
            List<String> tokens = (List<String>) request.get(RequestKeys.TOKENS);
            String topic = (String) request.get(RequestKeys.TOPIC);
            
            boolean success = fcmService.unsubscribeFromTopic(tokens, topic);
            
            return ResponseEntity.ok(Map.of(
                    ResponseKeys.SUCCESS, success,
                    ResponseKeys.MESSAGE, success ? "Successfully unsubscribed from topic" : "Failed to unsubscribe from topic"
            ));
            
        } catch (Exception e) {
            log.error("Failed to unsubscribe from topic: {}", e.getMessage(), e);
            return ResponseEntity.badRequest().body(Map.of(
                    ResponseKeys.SUCCESS, false,
                    ResponseKeys.ERROR, e.getMessage()
            ));
        }
    }
    
    /**
     * Validate a device token
     */
    @PostMapping("/validate/token")
    public ResponseEntity<Map<String, Object>> validateToken(@RequestBody Map<String, Object> request) {
        try {
            String token = (String) request.get("token");
            boolean isValid = fcmService.validateToken(token);
            
            return ResponseEntity.ok(Map.of(
                    ResponseKeys.VALID, isValid,
                    ResponseKeys.MESSAGE, isValid ? "Token is valid" : "Token is invalid"
            ));
            
        } catch (Exception e) {
            log.error("Failed to validate token: {}", e.getMessage(), e);
            return ResponseEntity.badRequest().body(Map.of(
                    ResponseKeys.VALID, false,
                    ResponseKeys.ERROR, e.getMessage()
            ));
        }
    }
}

