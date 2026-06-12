package com.gulfnet.integrationmanagement.service;

import lombok.Builder;
import lombok.Data;
import lombok.NonNull;

import java.util.Map;

/**
 * Generic FCM message DTO for building Firebase Cloud Messaging requests
 */
@Data
@Builder
@SuppressWarnings("java:S1068") // Fields are used via Lombok-generated getters/setters and builder
public class FcmMessage {
    
    @NonNull
    private String title;
    
    @NonNull
    private String body;
    
    private Map<String, String> data;
    
    private String imageUrl;
    
    private String clickAction;
    
    private String sound;
    
    private String badge;
    
    private String color;
    
    private String tag;
    
    private String icon;
    
    private FcmMessagePriority priority;
    
    private FcmMessageType messageType;
    
    private Long timeToLive;
    
    private Boolean contentAvailable;
    
    private Boolean mutableContent;
    
    /**
     * Enum for FCM message priority levels
     */
    public enum FcmMessagePriority {
        NORMAL, HIGH
    }
    
    /**
     * Enum for different FCM message types
     */
    public enum FcmMessageType {
        NOTIFICATION, DATA, NOTIFICATION_WITH_DATA
    }
}

