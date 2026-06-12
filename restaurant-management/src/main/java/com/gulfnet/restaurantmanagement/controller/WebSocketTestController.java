package com.gulfnet.restaurantmanagement.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.web.bind.annotation.*;
import static com.gulfnet.restaurantmanagement.config.RabbitMQConfig.*;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Test controller for WebSocket and RabbitMQ integration
 * This endpoint allows testing WebSocket messages that are also published to RabbitMQ
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/test/websocket")
@RequiredArgsConstructor
public class WebSocketTestController {

    private final SimpMessagingTemplate messagingTemplate;
    private final RabbitTemplate rabbitTemplate;

    /**
     * Constants for message map keys.
     */
    private static class MessageKeys {
        static final String USER_ID = "userId";
        static final String TOPIC = "topic";
        static final String TITLE = "title";
        static final String BODY = "body";
        static final String DATA = "data";
        static final String TIMESTAMP = "timestamp";
        static final String TYPE = "type";
        static final String SUCCESS = "success";
        static final String MESSAGE = "message";
        static final String ERROR = "error";
        
        private MessageKeys() {
            // Utility class - prevent instantiation
        }
    }

    /**
     * Constants for default message values.
     */
    private static class MessageValues {
        static final String DEFAULT_TITLE = "Test WebSocket Message";
        static final String DEFAULT_BODY = "This is a test message sent via WebSocket and RabbitMQ";
        static final String SIMPLE_TITLE = "Simple Test Message";
        static final String SIMPLE_BODY = "This is a simple test message";
        static final String SUCCESS_MESSAGE = "WebSocket message sent and published to RabbitMQ";
        
        private MessageValues() {
            // Utility class - prevent instantiation
        }
    }

    /**
     * Constants for WebSocket topics.
     */
    private static class Topics {
        static final String DEFAULT_TEST_TOPIC = "/topic/test";
        
        private Topics() {
            // Utility class - prevent instantiation
        }
    }

    /**
     * Constants for message types.
     */
    private static class MessageTypes {
        static final String WEBSOCKET_TEST_NOTIFICATION = "websocket_test_notification";
        
        private MessageTypes() {
            // Utility class - prevent instantiation
        }
    }

    /**
     * Test endpoint to send a WebSocket message
     * This will send via WebSocket AND publish to RabbitMQ for integration service
     * 
     * @param request Contains userId, topic, title, body, and optional data
     * @return Response indicating success
     */
    @PostMapping("/send")
    public ResponseEntity<Map<String, Object>> sendTestWebSocketMessage(@RequestBody Map<String, Object> request) {
        try {
            String userId = (String) request.getOrDefault(MessageKeys.USER_ID, UUID.randomUUID().toString());
            String topic = (String) request.getOrDefault(MessageKeys.TOPIC, Topics.DEFAULT_TEST_TOPIC);
            String title = (String) request.getOrDefault(MessageKeys.TITLE, MessageValues.DEFAULT_TITLE);
            String body = (String) request.getOrDefault(MessageKeys.BODY, MessageValues.DEFAULT_BODY);
            
            @SuppressWarnings("unchecked")
            Map<String, String> data = (Map<String, String>) request.get(MessageKeys.DATA);
            
            // Build WebSocket message
            Map<String, Object> wsMessage = new HashMap<>();
            wsMessage.put(MessageKeys.TITLE, title);
            wsMessage.put(MessageKeys.BODY, body);
            wsMessage.put(MessageKeys.DATA, data != null ? data : new HashMap<>());
            wsMessage.put(MessageKeys.TIMESTAMP, LocalDateTime.now(ZoneOffset.UTC).format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
            wsMessage.put(MessageKeys.USER_ID, userId);
            wsMessage.put(MessageKeys.TOPIC, topic);
            wsMessage.put(MessageKeys.TYPE, MessageTypes.WEBSOCKET_TEST_NOTIFICATION);
            
            // Send via WebSocket
            messagingTemplate.convertAndSendToUser(userId, topic, wsMessage);
            log.info("[Notification][WebSocket] test send userScoped userId={} destination=/user/{}{} type={}",
                    userId, userId, topic, MessageTypes.WEBSOCKET_TEST_NOTIFICATION);
            
            // Also publish to RabbitMQ for integration service to log
            publishToRabbitMQ(wsMessage, userId, topic);
            
            return ResponseEntity.ok(Map.of(
                    MessageKeys.SUCCESS, true,
                    MessageKeys.MESSAGE, MessageValues.SUCCESS_MESSAGE,
                    MessageKeys.USER_ID, userId,
                    MessageKeys.TOPIC, topic,
                    MessageKeys.TIMESTAMP, wsMessage.get(MessageKeys.TIMESTAMP)
            ));
            
        } catch (Exception e) {
            log.error("Failed to send test WebSocket message: {}", e.getMessage(), e);
            return ResponseEntity.badRequest().body(Map.of(
                    MessageKeys.SUCCESS, false,
                    MessageKeys.ERROR, e.getMessage()
            ));
        }
    }

    /**
     * Simple test endpoint that sends a default test message
     */
    @PostMapping("/send/simple")
    public ResponseEntity<Map<String, Object>> sendSimpleTestMessage() {
        Map<String, Object> request = new HashMap<>();
        request.put(MessageKeys.USER_ID, UUID.randomUUID().toString());
        request.put(MessageKeys.TOPIC, Topics.DEFAULT_TEST_TOPIC);
        request.put(MessageKeys.TITLE, MessageValues.SIMPLE_TITLE);
        request.put(MessageKeys.BODY, MessageValues.SIMPLE_BODY);
        
        return sendTestWebSocketMessage(request);
    }

    /**
     * Publishes WebSocket message to RabbitMQ for integration service logging.
     * Handles exceptions gracefully without affecting the main flow.
     * 
     * @param wsMessage The WebSocket message to publish
     */
    private void publishToRabbitMQ(Map<String, Object> wsMessage, String userId, String topic) {
        try {
            rabbitTemplate.convertAndSend(WEBSOCKET_TOPIC_EXCHANGE, WEBSOCKET_ROUTING_KEY, wsMessage);
            log.info("[Notification][FCM] rabbitPublish test exchange={} routingKey={} userId={} payloadWsTopic={} type={}",
                    WEBSOCKET_TOPIC_EXCHANGE, WEBSOCKET_ROUTING_KEY, userId, topic, MessageTypes.WEBSOCKET_TEST_NOTIFICATION);
        } catch (Exception e) {
            log.warn("[Notification][FCM] rabbitPublish test failed payloadWsTopic={}: {}", topic, e.getMessage());
        }
    }
}

