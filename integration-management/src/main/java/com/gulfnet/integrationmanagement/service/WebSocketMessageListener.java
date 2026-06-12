package com.gulfnet.integrationmanagement.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;

import static com.gulfnet.integrationmanagement.config.RabbitMQConfig.*;

/**
 * Service to listen for notification messages from RabbitMQ
 * This service receives notification messages from restaurant service and sends FCM notifications
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class WebSocketMessageListener {

    private final FcmService fcmService;
    private final RabbitTemplate rabbitTemplate;
    
    /**
     * Message field keys constants for WebSocket/RabbitMQ messages.
     * Following industry standard practice of using constants for message field names.
     */
    private static class MessageKeys {
        static final String NOTIFICATION_TYPE = "notificationType";
        static final String TYPE = "type";
        static final String TITLE = "title";
        static final String BODY = "body";
        static final String DEVICE_TOKEN = "deviceToken";
        static final String USER_ID = "userId";
        static final String DATA = "data";
        static final String IMAGE_URL = "imageUrl";
        static final String CLICK_ACTION = "clickAction";
        static final String SOUND = "sound";
        static final String PRIORITY = "priority";
        static final String MESSAGE_TYPE = "messageType";
        static final String TIMESTAMP = "timestamp";
        static final String REFRESH_NOTIFICATIONS = "refreshNotifications";
        static final String REFRESH_REQUESTS = "refreshRequests";
        static final String TEMPLATE_ID = "templateId";
        
        // Message type values
        static final String LIST_REFRESH_TRIGGER = "LIST_REFRESH_TRIGGER";
        static final String SALES_THRESHOLD_ALERT = "SALES_THRESHOLD_ALERT";
        /** Template IDs for HQ threshold FCM alerts — list refresh after FCM would duplicate the in-app pop-up */
        static final String REFUND_PERCENTAGE_ALERT = "REFUND_PERCENTAGE_ALERT";
        static final String ORDER_CANCELLATION_PERCENTAGE_ALERT = "ORDER_CANCELLATION_PERCENTAGE_ALERT";
        static final String TRANSACTION_CANCELLATION_PERCENTAGE_ALERT = "TRANSACTION_CANCELLATION_PERCENTAGE_ALERT";
        static final String CANCELLATION_PERCENTAGE_COMBINED_ALERT = "CANCELLATION_PERCENTAGE_COMBINED_ALERT";
        
        private MessageKeys() {
            // Utility class - prevent instantiation
        }
    }

    /**
     * Listen to notification messages from RabbitMQ topic
     * This will receive messages sent from restaurant service and send FCM notifications
     */
    @RabbitListener(queues = "websocket.topic.messages")
    public void handleWebSocketMessage(Map<String, Object> message) {
        try {
            boolean isSalesThreshold = isSalesThresholdAlert(message);
            boolean isHqThreshold = isHqThresholdAlert(message);
            if (!validateMessageContent(message)) {
                return;
            }
            
            String deviceToken = extractAndValidateDeviceToken(message);
            if (deviceToken == null) {
                return;
            }
            
            FcmMessage fcmMessage = buildFcmMessage(message);
            sendFcmNotification(deviceToken, fcmMessage, message, isSalesThreshold, isHqThreshold);
            
        } catch (Exception e) {
            log.debug("Failed to handle WebSocket RabbitMQ message", e);
        }
    }
    
    /**
     * Validate message content (title and body)
     */
    private boolean validateMessageContent(Map<String, Object> message) {
        String title = extractStringValue(message, MessageKeys.TITLE);
        String body = extractStringValue(message, MessageKeys.BODY);
        
        return title != null && body != null;
    }
    
    /**
     * Extract and validate device token
     */
    private String extractAndValidateDeviceToken(Map<String, Object> message) {
        String deviceToken = extractStringValue(message, MessageKeys.DEVICE_TOKEN);
        
        if (deviceToken == null || deviceToken.trim().isEmpty()) {
            return null;
        }
        
        return deviceToken;
    }
    
    /**
     * Build FCM message from notification message
     */
    private FcmMessage buildFcmMessage(Map<String, Object> message) {
        String title = extractStringValue(message, MessageKeys.TITLE);
        String body = extractStringValue(message, MessageKeys.BODY);
        Map<String, String> data = extractDataMap(message);
        
        FcmMessage.FcmMessageBuilder messageBuilder = FcmMessage.builder()
                .title(title)
                .body(body)
                .data(data);
        
        setOptionalFields(messageBuilder, message);
        setPriority(messageBuilder, message);
        setMessageType(messageBuilder, message);
        
        return messageBuilder.build();
    }
    
    /**
     * Extract data map from message, converting Map<String, Object> to Map<String, String>
     */
    private Map<String, String> extractDataMap(Map<String, Object> message) {
        Object dataObj = message.get(MessageKeys.DATA);
        if (dataObj instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> dataMap = (Map<String, Object>) dataObj;
            Map<String, String> data = new HashMap<>();
            for (Map.Entry<String, Object> entry : dataMap.entrySet()) {
                data.put(entry.getKey(), entry.getValue() != null ? entry.getValue().toString() : "");
            }
            return data;
        }
        return null;
    }
    
    /**
     * Set optional fields on FCM message builder
     */
    private void setOptionalFields(FcmMessage.FcmMessageBuilder messageBuilder, Map<String, Object> message) {
        String imageUrl = extractStringValue(message, MessageKeys.IMAGE_URL);
        if (imageUrl != null) {
            messageBuilder.imageUrl(imageUrl);
        }
        
        String clickAction = extractStringValue(message, MessageKeys.CLICK_ACTION);
        if (clickAction != null) {
            messageBuilder.clickAction(clickAction);
        }
        
        String sound = extractStringValue(message, MessageKeys.SOUND);
        if (sound != null) {
            messageBuilder.sound(sound);
        }
    }
    
    /**
     * Send FCM notification and handle response
     */
    private void sendFcmNotification(String deviceToken, FcmMessage fcmMessage, Map<String, Object> message,
                                     boolean isSalesThreshold, boolean isHqThreshold) {
        FcmSendResponse response = fcmService.sendToToken(deviceToken, fcmMessage);
        
        if (response.isSuccess() && !isHqThreshold) {
            triggerWebSocketListRefresh(message, isSalesThreshold);
        }
    }
    
    /**
     * Check if message is a sales threshold alert (for debug logging).
     */
    private boolean isSalesThresholdAlert(Map<String, Object> message) {
        String notificationType = extractStringValue(message, MessageKeys.NOTIFICATION_TYPE);
        if (MessageKeys.SALES_THRESHOLD_ALERT.equals(notificationType)) {
            return true;
        }
        if (message.get(MessageKeys.DATA) instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> data = (Map<String, Object>) message.get(MessageKeys.DATA);
            if (data != null && MessageKeys.SALES_THRESHOLD_ALERT.equals(data.get(MessageKeys.TEMPLATE_ID))) {
                return true;
            }
        }
        String title = extractStringValue(message, MessageKeys.TITLE);
        return title != null && title.contains("Sales Threshold");
    }

    /**
     * HQ threshold alerts already persist with suppressed list-refresh on save; triggering list refresh
     * after FCM causes the client to show the same alert twice (push + refreshed notification list).
     */
    private boolean isHqThresholdAlert(Map<String, Object> message) {
        if (isSalesThresholdAlert(message)) {
            return true;
        }
        String notificationType = extractStringValue(message, MessageKeys.NOTIFICATION_TYPE);
        if (MessageKeys.REFUND_PERCENTAGE_ALERT.equals(notificationType)
                || MessageKeys.ORDER_CANCELLATION_PERCENTAGE_ALERT.equals(notificationType)
                || MessageKeys.TRANSACTION_CANCELLATION_PERCENTAGE_ALERT.equals(notificationType)
                || MessageKeys.CANCELLATION_PERCENTAGE_COMBINED_ALERT.equals(notificationType)) {
            return true;
        }
        if (message.get(MessageKeys.DATA) instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> data = (Map<String, Object>) message.get(MessageKeys.DATA);
            if (data != null) {
                Object tid = data.get(MessageKeys.TEMPLATE_ID);
                if (tid != null) {
                    String id = tid.toString();
                    return MessageKeys.REFUND_PERCENTAGE_ALERT.equals(id)
                            || MessageKeys.ORDER_CANCELLATION_PERCENTAGE_ALERT.equals(id)
                            || MessageKeys.TRANSACTION_CANCELLATION_PERCENTAGE_ALERT.equals(id)
                            || MessageKeys.CANCELLATION_PERCENTAGE_COMBINED_ALERT.equals(id);
                }
            }
        }
        return false;
    }
    
    /**
     * Trigger WebSocket list refresh events after FCM notification is sent.
     * This publishes a message to RabbitMQ that restaurant-management service will consume
     * to send WebSocket list refresh events to the frontend.
     * 
     * @param originalMessage The original notification message containing userId and notificationType
     * @param isSalesThreshold Whether this is a sales threshold alert (for debug logging)
     */
    private void triggerWebSocketListRefresh(Map<String, Object> originalMessage, boolean isSalesThreshold) {
        try {
            String userIdStr = extractStringValue(originalMessage, MessageKeys.USER_ID);
            if (userIdStr == null || userIdStr.trim().isEmpty()) {
                return;
            }
            
            // Extract notification type to determine which lists need refreshing
            String notificationType = extractStringValue(originalMessage, MessageKeys.NOTIFICATION_TYPE);
            if (notificationType == null && originalMessage.get(MessageKeys.DATA) instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> data = (Map<String, Object>) originalMessage.get(MessageKeys.DATA);
                if (data != null && data.get(MessageKeys.NOTIFICATION_TYPE) != null) {
                    notificationType = data.get(MessageKeys.NOTIFICATION_TYPE).toString();
                }
            }
            
            // Build list refresh event message
            Map<String, Object> refreshEvent = new HashMap<>();
            refreshEvent.put(MessageKeys.TYPE, MessageKeys.LIST_REFRESH_TRIGGER);
            refreshEvent.put(MessageKeys.USER_ID, userIdStr);
            refreshEvent.put(MessageKeys.TIMESTAMP, OffsetDateTime.now(ZoneOffset.UTC).format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
            
            // Determine which lists need refreshing based on notification type
            // Most notifications should refresh both lists, but we can be specific
            boolean refreshNotifications = true;
            boolean refreshRequests = false;
            
            // Request-related notifications should refresh requests list
            if (notificationType != null) {
                String typeUpper = notificationType.toUpperCase();
                if (typeUpper.contains("REQUEST") || 
                    typeUpper.contains("APPROVED") || 
                    typeUpper.contains("REJECTED") ||
                    typeUpper.contains("DISCOUNT") ||
                    typeUpper.contains("CANCELLATION") ||
                    typeUpper.contains("REFUND") ||
                    typeUpper.contains("PROFILE_UPDATE")) {
                    refreshRequests = true;
                }
            }
            
            refreshEvent.put(MessageKeys.REFRESH_NOTIFICATIONS, refreshNotifications);
            refreshEvent.put(MessageKeys.REFRESH_REQUESTS, refreshRequests);
            refreshEvent.put(MessageKeys.NOTIFICATION_TYPE, notificationType);
            if (isSalesThreshold) {
                refreshEvent.put("debugSource", MessageKeys.SALES_THRESHOLD_ALERT);
            }
            
            // Publish to RabbitMQ for restaurant-management service to consume
            rabbitTemplate.convertAndSend(WEBSOCKET_TOPIC_EXCHANGE, LIST_REFRESH_ROUTING_KEY, refreshEvent);
            
        } catch (Exception e) {
            log.debug("Failed to publish WebSocket list refresh after FCM", e);
        }
    }

    /**
     * Set priority on FCM message builder with safe parsing
     */
    private void setPriority(FcmMessage.FcmMessageBuilder messageBuilder, Map<String, Object> message) {
        String priorityStr = extractStringValue(message, MessageKeys.PRIORITY);
        if (priorityStr != null) {
            try {
                messageBuilder.priority(FcmMessage.FcmMessagePriority.valueOf(priorityStr));
            } catch (IllegalArgumentException e) {
                messageBuilder.priority(FcmMessage.FcmMessagePriority.HIGH);
            }
        } else {
            messageBuilder.priority(FcmMessage.FcmMessagePriority.HIGH);
        }
    }
    
    /**
     * Set message type on FCM message builder with safe parsing
     */
    private void setMessageType(FcmMessage.FcmMessageBuilder messageBuilder, Map<String, Object> message) {
        String messageTypeStr = extractStringValue(message, MessageKeys.MESSAGE_TYPE);
        if (messageTypeStr != null) {
            try {
                messageBuilder.messageType(FcmMessage.FcmMessageType.valueOf(messageTypeStr));
            } catch (IllegalArgumentException e) {
                messageBuilder.messageType(FcmMessage.FcmMessageType.NOTIFICATION_WITH_DATA);
            }
        } else {
            messageBuilder.messageType(FcmMessage.FcmMessageType.NOTIFICATION_WITH_DATA);
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
}

