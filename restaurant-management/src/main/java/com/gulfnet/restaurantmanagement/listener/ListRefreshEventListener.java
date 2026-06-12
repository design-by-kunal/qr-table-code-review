package com.gulfnet.restaurantmanagement.listener;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.UUID;

/**
 * RabbitMQ listener that consumes list refresh events from integration service
 * and sends WebSocket list refresh notifications to frontend clients.
 * 
 * This ensures that when FCM notifications are sent, the notification and request
 * lists are automatically refreshed via WebSocket without requiring manual page refresh.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ListRefreshEventListener {

    private final SimpMessagingTemplate messagingTemplate;
    
    /**
     * WebSocket topic for list refresh events.
     * Note: When using convertAndSendToUser(), Spring automatically prefixes this with /user/{userId}/,
     * so the actual topic sent to clients will be: /user/{userId}/topic/list-update
     */
    private static final String LIST_UPDATE_TOPIC = "/topic/list-update";

    /**
     * Listen to list refresh events from integration service
     * This is triggered after FCM notifications are successfully sent
     */
    @RabbitListener(queues = "websocket.list.refresh.queue")
    public void handleListRefreshEvent(Map<String, Object> message) {
        try {
            log.info("Received list refresh event from integration service: {}", message);
            
            // Extract userId
            String userIdStr = extractStringValue(message, "userId");
            if (userIdStr == null || userIdStr.trim().isEmpty()) {
                log.warn("List refresh event missing userId, skipping: {}", message);
                return;
            }
            
            UUID userId = parseUserId(userIdStr);
            if (userId == null) {
                return;
            }
            
            // Extract refresh flags
            Boolean refreshNotifications = extractBooleanValue(message, "refreshNotifications");
            Boolean refreshRequests = extractBooleanValue(message, "refreshRequests");
            
            // Default to refreshing both if not specified
            if (refreshNotifications == null) {
                refreshNotifications = true;
            }
            if (refreshRequests == null) {
                // Check notification type to determine if requests should be refreshed
                String notificationType = extractStringValue(message, "notificationType");
                if (notificationType != null) {
                    String typeUpper = notificationType.toUpperCase();
                    refreshRequests = typeUpper.contains("REQUEST") || 
                                     typeUpper.contains("APPROVED") || 
                                     typeUpper.contains("REJECTED") ||
                                     typeUpper.contains("DISCOUNT") ||
                                     typeUpper.contains("CANCELLATION") ||
                                     typeUpper.contains("REFUND") ||
                                     typeUpper.contains("PROFILE_UPDATE");
                } else {
                    refreshRequests = false;
                }
            }
            
            // Send list refresh events via WebSocket
            if (refreshNotifications) {
                String debugSource = extractStringValue(message, "debugSource");
                if ("SALES_THRESHOLD_ALERT".equals(debugSource)) {
                    log.info("[SALES_THRESHOLD_DEBUG] LIST_REFRESH_PERSISTED ts={} userId={} - sending WebSocket list refresh to frontend (may cause 2nd pop-up)",
                            System.currentTimeMillis(), userId);
                }
                sendListRefreshEvent(userId, "notifications");
                log.info("Sent notification list refresh event to user {}", userId);
            }
            
            if (refreshRequests) {
                sendListRefreshEvent(userId, "requests");
                log.info("Sent request list refresh event to user {}", userId);
            }
            
        } catch (Exception e) {
            log.error("Failed to handle list refresh event: {}", e.getMessage(), e);
        }
    }
    
    /**
     * Send WebSocket list refresh event to notify clients that a list needs to be refreshed.
     * This is used for notification lists and request lists.
     * 
     * @param userId The user ID to send the refresh event to
     * @param listType The type of list that needs refreshing ("notifications" or "requests")
     */
    private void sendListRefreshEvent(UUID userId, String listType) {
        if (userId == null) {
            log.debug("Cannot send list refresh event: userId is null");
            return;
        }
        
        try {
            Map<String, Object> refreshEvent = new java.util.HashMap<>();
            refreshEvent.put("type", "LIST_REFRESH");
            refreshEvent.put("listType", listType != null ? listType : "notifications");
            refreshEvent.put("userId", userId.toString());
            refreshEvent.put("timestamp", OffsetDateTime.now(ZoneOffset.UTC).format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
            
            // Send to user-specific topic for list updates
            // convertAndSendToUser automatically prefixes with /user/{userId}/, so the final topic is:
            // /user/{userId}/topic/list-update
            messagingTemplate.convertAndSendToUser(userId.toString(), LIST_UPDATE_TOPIC, refreshEvent);
            
            log.info("[Notification][WebSocket] sent userScoped userId={} destination=/user/{}{} notificationType=LIST_REFRESH listType={}",
                    userId, userId, LIST_UPDATE_TOPIC, listType);
        } catch (Exception e) {
            log.warn("Failed to send list refresh event to user {}: {}", userId, e.getMessage());
        }
    }
    
    /**
     * Extract string value from message map safely
     */
    private String extractStringValue(Map<String, Object> message, String key) {
        Object value = message.get(key);
        if (value == null) {
            return null;
        }
        return value.toString();
    }
    
    /**
     * Extract boolean value from message map safely
     */
    private Boolean extractBooleanValue(Map<String, Object> message, String key) {
        Object value = message.get(key);
        if (value == null) {
            return null;
        }
        if (value instanceof Boolean) {
            return (Boolean) value;
        }
        if (value instanceof String) {
            return Boolean.parseBoolean((String) value);
        }
        return null;
    }
    
    /**
     * Parse userId string to UUID, logging warning and returning null if invalid.
     *
     * @param userIdStr the userId string to parse
     * @return the parsed UUID, or null if parsing fails
     */
    private UUID parseUserId(String userIdStr) {
        try {
            return UUID.fromString(userIdStr);
        } catch (IllegalArgumentException e) {
            log.warn("Invalid userId format in list refresh event: {}", userIdStr);
            return null;
        }
    }
}
