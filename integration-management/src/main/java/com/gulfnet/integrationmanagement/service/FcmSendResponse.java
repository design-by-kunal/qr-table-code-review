package com.gulfnet.integrationmanagement.service;

import lombok.Builder;
import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * FCM send response containing results and metadata
 */
@Data
@Builder
@SuppressWarnings("java:S1068") // Fields are used via Lombok-generated getters/setters and builder
public class FcmSendResponse {
    
    private boolean success;
    
    private String messageId;
    
    private List<String> messageIds;
    
    private List<FcmSendResult> results;
    
    private int successCount;
    
    private int failureCount;
    
    private List<String> failedTokens;
    
    private Map<String, Object> metadata;
    
    private String errorMessage;
    
    private Throwable exception;
    
    /**
     * Individual send result for batch operations
     */
    @Data
    @Builder
    @SuppressWarnings("java:S1068")
    public static class FcmSendResult {
        private boolean success;
        private String messageId;
        private String token;
        private String errorCode;
        private String errorMessage;
    }
    
    /**
     * Helper methods for response creation
     */
    public static FcmSendResponse success(String messageId) {
        return FcmSendResponse.builder()
                .success(true)
                .messageId(messageId)
                .successCount(1)
                .failureCount(0)
                .build();
    }
    
    public static FcmSendResponse failure(String errorMessage) {
        return FcmSendResponse.builder()
                .success(false)
                .errorMessage(errorMessage)
                .successCount(0)
                .failureCount(1)
                .build();
    }
    
    public static FcmSendResponse failure(String errorMessage, Throwable exception) {
        return FcmSendResponse.builder()
                .success(false)
                .errorMessage(errorMessage)
                .exception(exception)
                .successCount(0)
                .failureCount(1)
                .build();
    }
}

