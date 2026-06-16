package com.gulfnet.usermanagement.service;

import com.gulfnet.shared_library.entity.Notification;
import com.gulfnet.shared_library.entity.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;

import static com.gulfnet.usermanagement.config.RabbitMQConfig.*;

/**
 * Service to publish notification messages to RabbitMQ for integration service to process FCM notifications
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class NotificationPublisherService {

    /**
     * Constants for message map keys.
     * Following industry standard practice of using constants for map keys.
     */
    private static class MessageKeys {
        static final String TITLE = "title";
        static final String BODY = "body";
        static final String DEVICE_TOKEN = "deviceToken";
        static final String USER_ID = "userId";
        static final String NOTIFICATION_ID = "notificationId";
        static final String TYPE = "type";
        static final String TOPIC = "topic";
        static final String TIMESTAMP = "timestamp";
        static final String NOTIFICATION_TYPE = "notificationType";
        static final String CREATED_BY_ID = "createdById";
        static final String DATA = "data";
        static final String PRIORITY = "priority";
        static final String MESSAGE_TYPE = "messageType";
        
        private MessageKeys() {
            // Utility class - prevent instantiation
        }
    }

    /**
     * Constants for message values.
     * Following industry standard practice of using constants for message values.
     */
    private static class MessageValues {
        static final String DEFAULT_TITLE = "Notification";
        static final String WEBSOCKET_NOTIFICATION = "websocket_notification";
        static final String HIGH_PRIORITY = "HIGH";
        static final String NOTIFICATION_WITH_DATA = "NOTIFICATION_WITH_DATA";
        
        private MessageValues() {
            // Utility class - prevent instantiation
        }
    }

    /**
     * Constants for notification types.
     * Following industry standard practice of using constants for notification types.
     */
    private static class NotificationTypes {
        static final String ITEM_CANCELLATION = "ITEM_CANCELLATION";
        static final String CANCELLATION = "CANCELLATION";
        static final String PROFILE_UPDATE = "PROFILE_UPDATE";
        
        private NotificationTypes() {
            // Utility class - prevent instantiation
        }
    }

    /**
     * Constants for WebSocket topics.
     * Following industry standard practice of using constants for WebSocket topics.
     */
    private static class WebSocketTopics {
        static final String USER_UPDATES = "/topic/user/updates";
        
        private WebSocketTopics() {
            // Utility class - prevent instantiation
        }
    }

    private final RabbitTemplate rabbitTemplate;

    /**
     * Publish notification to RabbitMQ for FCM processing
     * Enhanced to include WebSocket topic information for proper notification delivery
     * @param notification The notification entity
     * @param user The user who should receive the notification
     */
    public void publishNotification(Notification notification, User user) {
        if (notification == null || user == null) {
            log.warn("Cannot publish notification: notification or user is null");
            return;
        }

        try {
            boolean isItemCancellationNotification = isItemCancellationNotification(notification);
            logItemCancellationStart(notification, user, isItemCancellationNotification);
            
            String deviceToken = validateAndGetDeviceToken(user, isItemCancellationNotification);
            if (deviceToken == null) {
                return;
            }

            String topic = determineWebSocketTopic(notification.getType());
            logItemCancellationDeviceToken(deviceToken, topic, isItemCancellationNotification);
            
            Map<String, Object> message = buildNotificationMessage(notification, user, deviceToken, topic);
            Map<String, String> data = buildNotificationData(notification);
            message.put(MessageKeys.DATA, data);
            setDefaultPriorityAndType(message);

            logItemCancellationMessageDetails(message, isItemCancellationNotification);
            publishToRabbitMQ(message, notification, user, topic, isItemCancellationNotification);

        } catch (Exception e) {
            handlePublishError(notification, user, e);
        }
    }
    
    /**
     * Checks if the notification is an item cancellation notification
     */
    private boolean isItemCancellationNotification(Notification notification) {
        return notification.getType() != null && 
                (notification.getType().contains(NotificationTypes.ITEM_CANCELLATION) || 
                 notification.getType().contains(NotificationTypes.CANCELLATION));
    }
    
    /**
     * Logs item cancellation notification start
     */
    private void logItemCancellationStart(Notification notification, User user, boolean isItemCancellationNotification) {
        if (isItemCancellationNotification) {
            log.info("=== ITEM CANCELLATION REQUESTx FCM - RabbitMQ Publish START ===");
            log.info("=== ITEM CANCELLATION REQUEST FCM - Notification ID: {}, Type: {}, User ID: {}, User Name: {} {} ===",
                    notification.getId(), notification.getType(), user.getId(), 
                    user.getFirstName(), user.getLastName());
        }
    }
    
    /**
     * Validates and retrieves device token from user
     */
    private String validateAndGetDeviceToken(User user, boolean isItemCancellationNotification) {
        String deviceToken = user.getDeviceToken();
        log.debug("Publishing notification for user {} - Device token present: {}", 
                user.getId(), deviceToken != null && !deviceToken.trim().isEmpty());
        
        if (deviceToken == null || deviceToken.trim().isEmpty()) {
            if (isItemCancellationNotification) {
                log.warn("=== ITEM CANCELLATION REQUEST FCM - User {} has no device token, skipping FCM notification publish ===", user.getId());
            } else {
                log.warn("User {} has no device token, skipping FCM notification publish", user.getId());
            }
            return null;
        }
        return deviceToken;
    }
    
    /**
     * Logs device token information for item cancellation notifications
     */
    private void logItemCancellationDeviceToken(String deviceToken, String topic, boolean isItemCancellationNotification) {
        if (isItemCancellationNotification) {
            log.info("=== ITEM CANCELLATION REQUEST FCM - Device Token: PRESENT (length: {}) ===", deviceToken.length());
            log.info("=== ITEM CANCELLATION REQUEST FCM - WebSocket Topic: {} ===", topic);
        }
    }
    
    /**
     * Builds the main notification message map
     */
    private Map<String, Object> buildNotificationMessage(Notification notification, User user, String deviceToken, String topic) {
        Map<String, Object> message = new HashMap<>();
        message.put(MessageKeys.TITLE, notification.getTitle() != null ? notification.getTitle() : MessageValues.DEFAULT_TITLE);
        message.put(MessageKeys.BODY, notification.getMessage() != null ? notification.getMessage() : "");
        message.put(MessageKeys.DEVICE_TOKEN, deviceToken);
        message.put(MessageKeys.USER_ID, user.getId().toString());
        message.put(MessageKeys.NOTIFICATION_ID, notification.getId().toString());
        message.put(MessageKeys.TYPE, MessageValues.WEBSOCKET_NOTIFICATION);
        message.put(MessageKeys.TOPIC, topic);
        message.put(MessageKeys.TIMESTAMP, LocalDateTime.now(ZoneOffset.UTC).format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
        
        log.debug("Including FCM device token in message (token length: {})", deviceToken.length());
        
        return message;
    }
    
    /**
     * Builds the data map from notification
     */
    private Map<String, String> buildNotificationData(Notification notification) {
        Map<String, String> data = new HashMap<>();
        if (notification.getType() != null) {
            data.put(MessageKeys.NOTIFICATION_TYPE, notification.getType());
        }
        if (notification.getId() != null) {
            data.put(MessageKeys.NOTIFICATION_ID, notification.getId().toString());
        }
        if (notification.getCreatedBy() != null) {
            data.put(MessageKeys.CREATED_BY_ID, notification.getCreatedBy().getId().toString());
        }
        data.put(MessageKeys.TIMESTAMP, notification.getCreatedAt() != null 
                ? notification.getCreatedAt().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME) 
                : LocalDateTime.now(ZoneOffset.UTC).format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
        return data;
    }
    
    /**
     * Sets default priority and message type on the message
     */
    private void setDefaultPriorityAndType(Map<String, Object> message) {
        message.put(MessageKeys.PRIORITY, MessageValues.HIGH_PRIORITY);
        message.put(MessageKeys.MESSAGE_TYPE, MessageValues.NOTIFICATION_WITH_DATA);
    }
    
    /**
     * Logs detailed message information for item cancellation notifications
     */
    private void logItemCancellationMessageDetails(Map<String, Object> message, boolean isItemCancellationNotification) {
        if (isItemCancellationNotification) {
            log.info("=== ITEM CANCELLATION REQUEST FCM - Message Title: {} ===", message.get(MessageKeys.TITLE));
            log.info("=== ITEM CANCELLATION REQUEST FCM - Message Body: {} ===", message.get(MessageKeys.BODY));
            log.info("=== ITEM CANCELLATION REQUEST FCM - Device Token in message: {} ===",
                    message.get(MessageKeys.DEVICE_TOKEN) != null ? "PRESENT (length: " + ((String)message.get(MessageKeys.DEVICE_TOKEN)).length() + ")" : "MISSING");
            log.info("=== ITEM CANCELLATION REQUEST FCM - User ID in message: {} ===", message.get(MessageKeys.USER_ID));
            log.info("=== ITEM CANCELLATION REQUEST FCM - Message Priority: {}, Message Type: {} ===", 
                    message.get(MessageKeys.PRIORITY), message.get(MessageKeys.MESSAGE_TYPE));
            log.info("=== ITEM CANCELLATION REQUEST FCM - Publishing to RabbitMQ Exchange: {}, Routing Key: {} ===",
                    NOTIFICATION_TOPIC_EXCHANGE, NOTIFICATION_ROUTING_KEY);
        }
    }
    
    /**
     * Publishes message to RabbitMQ and logs success
     */
    private void publishToRabbitMQ(Map<String, Object> message, Notification notification, User user, 
            String topic, boolean isItemCancellationNotification) {
        rabbitTemplate.convertAndSend(NOTIFICATION_TOPIC_EXCHANGE, NOTIFICATION_ROUTING_KEY, message);
        
        if (isItemCancellationNotification) {
            log.info("=== ITEM CANCELLATION REQUEST FCM - Successfully published to RabbitMQ ===");
            log.info("=== ITEM CANCELLATION REQUEST FCM - RabbitMQ Publish END ===");
        } else {
            log.info("Published notification to RabbitMQ - Notification ID: {}, User ID: {}, Type: {}, Topic: {}", 
                    notification.getId(), user.getId(), notification.getType(), topic);
        }
    }
    
    /**
     * Handles errors during notification publishing
     */
    private void handlePublishError(Notification notification, User user, Exception e) {
        boolean isItemCancellationNotification = isItemCancellationNotification(notification);
        
        if (isItemCancellationNotification) {
            log.error("=== ITEM CANCELLATION REQUEST FCM - Failed to publish to RabbitMQ - Notification ID: {}, User ID: {}, Error: {} ===", 
                    notification.getId(), user != null ? user.getId() : "null", e.getMessage(), e);
        } else {
            log.error("Failed to publish notification to RabbitMQ - Notification ID: {}, User ID: {}, Error: {}", 
                    notification.getId(), user != null ? user.getId() : "null", e.getMessage(), e);
        }
    }
    
    /**
     * Determine the appropriate WebSocket topic based on notification type
     * Currently all user-related notifications use USER_UPDATES topic
     * @param notificationType The type of notification (unused currently, reserved for future extensibility)
     * @return The WebSocket topic to use
     */
    private String determineWebSocketTopic(String notificationType) {
        // All user-related notifications currently use the same topic
        // This method is kept for future extensibility when different notification types
        // may need different topics
        return WebSocketTopics.USER_UPDATES;
    }

    /**
     * Publish notification with custom data
     * Enhanced to include WebSocket topic information for proper notification delivery
     * @param user The user who should receive the notification
     * @param title Notification title
     * @param body Notification body
     * @param notificationType Notification type
     * @param additionalData Additional data to include
     */
    public void publishNotification(User user, String title, String body, String notificationType, 
                                    Map<String, String> additionalData) {
        if (user == null || title == null || body == null) {
            log.warn("Cannot publish notification: user, title, or body is null");
            return;
        }

        try {
            // Get device token from user
            String deviceToken = user.getDeviceToken();
            if (deviceToken == null || deviceToken.trim().isEmpty()) {
                log.debug("User {} has no device token, skipping FCM notification publish", user.getId());
                return;
            }

            // Determine WebSocket topic based on notification type
            String topic = determineWebSocketTopic(notificationType);

            // Build notification message for RabbitMQ in the same format as NotificationServiceImpl
            Map<String, Object> message = new HashMap<>();
            message.put(MessageKeys.TITLE, title);
            message.put(MessageKeys.BODY, body);
            message.put(MessageKeys.DEVICE_TOKEN, deviceToken);
            message.put(MessageKeys.USER_ID, user.getId().toString());
            message.put(MessageKeys.TYPE, MessageValues.WEBSOCKET_NOTIFICATION); // Changed from "notification" to match NotificationServiceImpl
            message.put(MessageKeys.TOPIC, topic); // Add WebSocket topic for proper routing
            message.put(MessageKeys.TIMESTAMP, LocalDateTime.now(ZoneOffset.UTC).format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));

            // Build data map
            Map<String, String> data = new HashMap<>();
            if (notificationType != null) {
                data.put(MessageKeys.NOTIFICATION_TYPE, notificationType);
            }
            if (additionalData != null) {
                data.putAll(additionalData);
            }
            data.put(MessageKeys.TIMESTAMP, LocalDateTime.now(ZoneOffset.UTC).format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
            
            message.put(MessageKeys.DATA, data);

            // Set default priority and message type
            message.put(MessageKeys.PRIORITY, MessageValues.HIGH_PRIORITY);
            message.put(MessageKeys.MESSAGE_TYPE, MessageValues.NOTIFICATION_WITH_DATA);

            // Publish to RabbitMQ
            rabbitTemplate.convertAndSend(NOTIFICATION_TOPIC_EXCHANGE, NOTIFICATION_ROUTING_KEY, message);
            
            log.info("Published notification to RabbitMQ - User ID: {}, Type: {}, Topic: {}", 
                    user.getId(), notificationType, topic);

        } catch (Exception e) {
            log.error("Failed to publish notification to RabbitMQ - User ID: {}, Error: {}", 
                    user.getId(), e.getMessage(), e);
        }
    }
}

