package com.gulfnet.restaurantmanagement.service;

import lombok.Builder;
import lombok.Data;
import lombok.NonNull;

import java.util.Map;

/**
 * Notification message DTO for message passing via RabbitMQ
 */
@Data
@Builder
@SuppressWarnings("java:S1068") // Fields are used via Lombok-generated getters/setters and builder
public class NotificationMessage {
    
    @NonNull
    private String title;
    
    @NonNull
    private String body;

    private String bodyKey;

    private String bodyArgs;
    
    private Map<String, String> data;
    
    private String imageUrl;
    
    private String clickAction;
    
    private String sound;
    
    private String badge;
    
    private String color;
    
    private String tag;
    
    private String icon;
    
    private NotificationPriority priority;
    
    private NotificationType messageType;
    
    private Long timeToLive;
    
    private Boolean contentAvailable;
    
    private Boolean mutableContent;
    
    /**
     * Enum for notification priority levels
     */
    public enum NotificationPriority {
        NORMAL, HIGH
    }
    
    /**
     * Enum for different notification types
     */
    public enum NotificationType {
        NOTIFICATION, DATA, NOTIFICATION_WITH_DATA
    }
}

