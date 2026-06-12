package com.gulfnet.restaurantmanagement.listener;

import com.gulfnet.restaurantmanagement.config.RabbitMQConfig;
import com.gulfnet.shared_library.model.request.StatusEventMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * RabbitMQ listener that consumes WebSocket messages from user-management
 * and forwards them to WebSocket clients connected to restaurant-management.
 * 
 * This follows the same pattern as RequestDecisionListener:
 * - Consumes messages from RabbitMQ queue
 * - Routes based on message content
 * - Forwards to WebSocket clients using SimpMessagingTemplate
 * 
 * Topics supported:
 * - /topic/restaurant/{restaurantId}/item-status
 * - /topic/restaurant/{restaurantId}/order-status
 * - /topic/restaurant/{restaurantId}/transaction-status
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WebSocketMessageForwarder {

    private final SimpMessagingTemplate messagingTemplate;

    /**
     * Listen to WebSocket topic messages queue from user-management
     * Forwards messages to WebSocket clients connected to restaurant-management
     * 
     * This follows the same pattern as RequestDecisionListener.handleRequestDecision()
     */
    @RabbitListener(queues = "websocket.topic.messages.restaurant")
    public void handleWebSocketMessage(Map<String, Object> message) {
        try {
            log.info("Received WebSocket message from RabbitMQ: {}", message);
            
            // Extract topic from message
            String topic = (String) message.get("topic");
            if (topic == null || topic.isEmpty()) {
                log.warn("WebSocket message missing topic field, skipping: {}", message);
                return;
            }

            // restaurant-management already broadcast these via STOMP before publishing to RabbitMQ for
            // integration/FCM; re-forwarding would duplicate pop-ups (e.g. cashier "order status" toast).
            if (Boolean.TRUE.equals(message.get(RabbitMQConfig.WEBSOCKET_MSG_SUPPRESS_LOCAL_FORWARD))) {
                log.debug("Skipping WebSocket forward (already sent locally): topic={}", topic);
                return;
            }
            
            // Only forward messages for restaurant topics (item-status, order-status, transaction-status)
            if (!topic.startsWith("/topic/restaurant/")) {
                log.debug("Skipping non-restaurant topic: {}", topic);
                return;
            }
            
            // Skip KDS-specific topics — these are already sent as user-scoped WebSocket
            // messages via convertAndSendToUser() in NotificationServiceImpl. Re-broadcasting
            // them here via convertAndSend() would leak notifications to ALL KDS devices
            // instead of only the assigned ones.
            if (topic.contains("/kds/")) {
                log.debug("Skipping KDS-specific topic (already sent user-scoped): {}", topic);
                return;
            }
            
            // Build StatusEventMessage from the RabbitMQ message
            StatusEventMessage eventMessage = buildStatusEventMessage(message);
            
            // Forward to WebSocket clients (same pattern as NotificationService)
            messagingTemplate.convertAndSend(topic, eventMessage);
            
            log.info("[Notification][WebSocket] forward-from-rabbit broadcast topic={} notificationType={} status={}",
                    topic,
                    eventMessage.getNotificationType(),
                    eventMessage.getStatus());
                    
        } catch (Exception e) {
            log.error("Failed to forward WebSocket message: {}", e.getMessage(), e);
        }
    }
    
    /**
     * Build StatusEventMessage from RabbitMQ message map
     * Similar to how RequestDecisionListener extracts data from messages
     */
    private StatusEventMessage buildStatusEventMessage(Map<String, Object> message) {
        StatusEventMessage.StatusEventMessageBuilder builder = StatusEventMessage.builder();
        
        // Extract message fields
        if (message.containsKey("title")) {
            builder.title((String) message.get("title"));
        }
        if (message.containsKey("message")) {
            builder.message((String) message.get("message"));
        }
        
        if (message.containsKey("notificationType")) {
            builder.notificationType((String) message.get("notificationType"));
        }
        
        if (message.containsKey("orderId")) {
            builder.orderId((String) message.get("orderId"));
        }
        
        if (message.containsKey("itemId")) {
            builder.itemId((String) message.get("itemId"));
        }
        
        if (message.containsKey("userId")) {
            builder.userId((String) message.get("userId"));
        }
        
        if (message.containsKey("status")) {
            builder.status((String) message.get("status"));
        }
        
        if (message.containsKey("data")) {
            @SuppressWarnings("unchecked")
            Map<String, Object> data = (Map<String, Object>) message.get("data");
            builder.data(data);
        }
        
        return builder.build();
    }
}
