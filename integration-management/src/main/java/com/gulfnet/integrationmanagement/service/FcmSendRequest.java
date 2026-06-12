package com.gulfnet.integrationmanagement.service;

import lombok.Builder;
import lombok.Data;
import lombok.NonNull;

import java.util.List;
import java.util.Map;

/**
 * FCM send request containing target information and message details
 */
@Data
@Builder
@SuppressWarnings("java:S1068") // Fields are used via Lombok-generated getters/setters and builder
public class FcmSendRequest {
    
    @NonNull
    private FcmMessage message;
    
    private List<String> tokens;
    
    private String topic;
    
    private String condition;
    
    private FcmTargetType targetType;
    
    private Map<String, Object> customOptions;
    
    /**
     * Enum for different FCM target types
     */
    public enum FcmTargetType {
        TOKEN, TOKENS, TOPIC, CONDITION
    }
    
    /**
     * Builder helper methods for common scenarios
     */
    public static FcmSendRequestBuilder toToken(String token, FcmMessage message) {
        return FcmSendRequest.builder()
                .message(message)
                .tokens(List.of(token))
                .targetType(FcmTargetType.TOKEN);
    }
    
    public static FcmSendRequestBuilder toTokens(List<String> tokens, FcmMessage message) {
        return FcmSendRequest.builder()
                .message(message)
                .tokens(tokens)
                .targetType(FcmTargetType.TOKENS);
    }
    
    public static FcmSendRequestBuilder toTopic(String topic, FcmMessage message) {
        return FcmSendRequest.builder()
                .message(message)
                .topic(topic)
                .targetType(FcmTargetType.TOPIC);
    }
    
    public static FcmSendRequestBuilder toCondition(String condition, FcmMessage message) {
        return FcmSendRequest.builder()
                .message(message)
                .condition(condition)
                .targetType(FcmTargetType.CONDITION);
    }
}

