package com.gulfnet.integrationmanagement.service;

import lombok.Builder;
import lombok.Data;

import java.util.Map;

/**
 * FCM configuration options for customizing message behavior
 */
@Data
@Builder
@SuppressWarnings("java:S1068") // Fields are used via Lombok-generated getters/setters and builder
public class FcmConfig {
    
    private boolean enableWebPush;
    
    private boolean enableAndroidConfig;
    
    private boolean enableApnsConfig;
    
    private FcmWebPushConfig webPushConfig;
    
    private FcmAndroidConfig androidConfig;
    
    private FcmApnsConfig apnsConfig;
    
    private Long defaultTimeToLive;
    
    private Boolean defaultContentAvailable;
    
    private Boolean defaultMutableContent;
    
    private FcmMessage.FcmMessagePriority defaultPriority;
    
    private Map<String, Object> customConfig;
    
    /**
     * WebPush specific configuration
     */
    @Data
    @Builder
    @SuppressWarnings("java:S1068")
    public static class FcmWebPushConfig {
        private String title;
        private String body;
        private String icon;
        private String badge;
        private String image;
        private String tag;
        private String url;
        private String action;
        private Map<String, Object> data;
        private boolean requireInteraction;
        private boolean silent;
        private int[] vibrate;
        private long timestamp;
    }
    
    /**
     * Android specific configuration
     */
    @Data
    @Builder
    @SuppressWarnings("java:S1068")
    public static class FcmAndroidConfig {
        private String collapseKey;
        private FcmMessage.FcmMessagePriority priority;
        private Long timeToLive;
        private String restrictedPackageName;
        private Map<String, String> data;
        private FcmAndroidNotificationConfig notification;
    }
    
    /**
     * Android notification configuration
     */
    @Data
    @Builder
    @SuppressWarnings("java:S1068")
    public static class FcmAndroidNotificationConfig {
        private String title;
        private String body;
        private String icon;
        private String color;
        private String sound;
        private String tag;
        private String clickAction;
        private String bodyLocKey;
        private String[] bodyLocArgs;
        private String titleLocKey;
        private String[] titleLocArgs;
        private String channelId;
        private String ticker;
        private boolean sticky;
        private String eventTime;
        private boolean localOnly;
        private String notificationPriority;
        private String defaultSound;
        private String defaultVibrateTimings;
        private String defaultLightSettings;
        private String[] vibrateTimings;
        private String visibility;
        private int notificationCount;
        private String image;
    }
    
    /**
     * APNS (iOS) specific configuration
     */
    @Data
    @Builder
    @SuppressWarnings("java:S1068")
    public static class FcmApnsConfig {
        private Map<String, Object> headers;
        private FcmApnsPayload payload;
    }
    
    /**
     * APNS payload configuration
     */
    @Data
    @Builder
    @SuppressWarnings("java:S1068")
    public static class FcmApnsPayload {
        private FcmApnsAps aps;
        private Map<String, Object> customData;
    }
    
    /**
     * APNS aps configuration
     */
    @Data
    @Builder
    @SuppressWarnings("java:S1068")
    public static class FcmApnsAps {
        private String alert;
        private int badge;
        private String sound;
        private boolean contentAvailable;
        private boolean mutableContent;
        private String category;
        private String threadId;
        private Map<String, Object> customData;
    }
}

