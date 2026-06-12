package com.gulfnet.integrationmanagement.service.impl;

import com.google.firebase.FirebaseApp;
import com.google.firebase.messaging.*;
import com.gulfnet.integrationmanagement.service.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/**
 * Firebase Cloud Messaging service implementation
 * Provides comprehensive FCM functionality with proper error handling and configuration support
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FcmServiceImpl implements FcmService {
    
    private final FirebaseApp firebaseApp;
    private final Executor fcmExecutor = Executors.newFixedThreadPool(10);
    
    private FirebaseMessaging getFirebaseMessaging() {
        return FirebaseMessaging.getInstance(firebaseApp);
    }
    
    /**
     * Sends an FCM notification to a single device token using the default configuration.
     *
     * @param token   the destination device token
     * @param message the message payload to send
     * @return {@link FcmSendResponse} containing the send result
     */
    @Override
    public FcmSendResponse sendToToken(String token, FcmMessage message) {
        return sendToToken(token, message, getDefaultConfig());
    }
    
    /**
     * Sends an FCM notification to a single device token using the provided configuration.
     * Validates the token format before attempting to send.
     *
     * @param token   the destination device token
     * @param message the message payload to send
     * @param config  the FCM configuration to apply for this send
     * @return {@link FcmSendResponse} containing the send result
     */
    @Override
    public FcmSendResponse sendToToken(String token, FcmMessage message, FcmConfig config) {
        if (!validateToken(token)) {
            return FcmSendResponse.failure("Invalid device token format");
        }
        
        try {
            Message.Builder messageBuilder = buildFirebaseMessage(message, config);
            messageBuilder.setToken(token);
            
            String messageId = getFirebaseMessaging().send(messageBuilder.build());
            log.info("Successfully sent FCM message (destination token length: {})", token.length());
            
            return FcmSendResponse.success(messageId);
            
        } catch (Exception e) {
            log.error("Failed to send FCM message (destination token length: {}): {}", token.length(), e.getMessage(), e);
            return FcmSendResponse.failure("Failed to send FCM message: " + e.getMessage(), e);
        }
    }
    
    @Override
    /**
     * Sends an FCM notification to multiple device tokens using the default configuration.
     *
     * @param tokens  list of destination device tokens
     * @param message the message payload to send
     * @return {@link FcmSendResponse} summarizing successes and failures
     */
    public FcmSendResponse sendToTokens(List<String> tokens, FcmMessage message) {
        return sendToTokens(tokens, message, getDefaultConfig());
    }
    
    /**
     * Sends an FCM notification to multiple device tokens using the provided configuration.
     * Filters and validates tokens, then sends as a multicast message.
     *
     * @param tokens  list of destination device tokens
     * @param message the message payload to send
     * @param config  the FCM configuration to apply for this send
     * @return {@link FcmSendResponse} summarizing successes and failures
     */
    @Override
    public FcmSendResponse sendToTokens(List<String> tokens, FcmMessage message, FcmConfig config) {
        if (tokens == null || tokens.isEmpty()) {
            return FcmSendResponse.failure("Token list cannot be null or empty");
        }
        
        List<String> validTokens = filterValidTokens(tokens);
        if (validTokens.isEmpty()) {
            return FcmSendResponse.failure("No valid tokens provided");
        }
        
        try {
            MulticastMessage multicastMessage = buildMulticastMessage(validTokens, message, config);
            BatchResponse batchResponse = getFirebaseMessaging().sendEachForMulticast(multicastMessage);
            return processBatchResponse(batchResponse, validTokens);
            
        } catch (Exception e) {
            log.error("Failed to send FCM multicast message: {}", e.getMessage(), e);
            return FcmSendResponse.failure("Failed to send FCM multicast message: " + e.getMessage(), e);
        }
    }
    
    /**
     * Filter and validate tokens
     */
    private List<String> filterValidTokens(List<String> tokens) {
        return tokens.stream()
                .filter(this::validateToken)
                .toList();
    }
    
    /**
     * Build multicast message with all configurations
     */
    private MulticastMessage buildMulticastMessage(List<String> validTokens, FcmMessage message, FcmConfig config) {
        MulticastMessage.Builder builder = MulticastMessage.builder()
                .addAllTokens(validTokens)
                .setNotification(buildFirebaseNotification(message));
        
        if (message.getData() != null && !message.getData().isEmpty()) {
            builder.putAllData(message.getData());
        }
        
        applyPlatformConfigs(builder, message, config);
        return builder.build();
    }
    
    /**
     * Apply platform-specific configurations to multicast message
     */
    private void applyPlatformConfigs(MulticastMessage.Builder builder, FcmMessage message, FcmConfig config) {
        if (config.isEnableAndroidConfig()) {
            builder.setAndroidConfig(buildAndroidConfig(message, config));
        }
        
        if (config.isEnableApnsConfig()) {
            builder.setApnsConfig(buildApnsConfig(message, config));
        }
        
        if (config.isEnableWebPush()) {
            builder.setWebpushConfig(buildWebpushConfig(message));
        }
    }
    
    /**
     * Build Android configuration for multicast message
     */
    private AndroidConfig buildAndroidConfig(FcmMessage message, FcmConfig config) {
        AndroidConfig.Priority priority = message.getPriority() == FcmMessage.FcmMessagePriority.HIGH 
                ? AndroidConfig.Priority.HIGH 
                : AndroidConfig.Priority.NORMAL;
        
        AndroidConfig.Builder androidConfigBuilder = AndroidConfig.builder()
                .setPriority(priority);
        
        Long ttl = message.getTimeToLive() != null ? message.getTimeToLive() : config.getDefaultTimeToLive();
        if (ttl != null) {
            androidConfigBuilder.setTtl(ttl);
        }
        
        return androidConfigBuilder.build();
    }
    
    /**
     * Process batch response and build FCM send response
     */
    private FcmSendResponse processBatchResponse(BatchResponse batchResponse, List<String> validTokens) {
        List<FcmSendResponse.FcmSendResult> results = new ArrayList<>();
        int successCount = 0;
        int failureCount = 0;
        List<String> failedTokens = new ArrayList<>();
        
        for (int i = 0; i < batchResponse.getResponses().size(); i++) {
            SendResponse response = batchResponse.getResponses().get(i);
            String token = validTokens.get(i);
            
            FcmSendResponse.FcmSendResult result = buildSendResult(response, token);
            results.add(result);
            
            if (response.isSuccessful()) {
                successCount++;
            } else {
                failureCount++;
                failedTokens.add(token);
                log.warn("Failed to send FCM message to one device (token length: {}): {}",
                        token.length(), response.getException().getMessage());
            }
        }
        
        log.info("Sent FCM multicast message: {} successful, {} failed", successCount, failureCount);
        
        return FcmSendResponse.builder()
                .success(failureCount == 0)
                .results(results)
                .successCount(successCount)
                .failureCount(failureCount)
                .failedTokens(failedTokens)
                .build();
    }
    
    /**
     * Build FCM send result from Firebase send response
     */
    private FcmSendResponse.FcmSendResult buildSendResult(SendResponse response, String token) {
        return FcmSendResponse.FcmSendResult.builder()
                .token(token)
                .success(response.isSuccessful())
                .messageId(response.getMessageId())
                .errorCode(response.getException() != null ? response.getException().getMessagingErrorCode().toString() : null)
                .errorMessage(response.getException() != null ? response.getException().getMessage() : null)
                .build();
    }
    
    @Override
    /**
     * Sends an FCM notification to a topic using the default configuration.
     *
     * @param topic   the topic name to publish to
     * @param message the message payload to send
     * @return {@link FcmSendResponse} containing the send result
     */
    public FcmSendResponse sendToTopic(String topic, FcmMessage message) {
        return sendToTopic(topic, message, getDefaultConfig());
    }
    
    /**
     * Sends an FCM notification to a topic using the provided configuration.
     *
     * @param topic   the topic name to publish to
     * @param message the message payload to send
     * @param config  the FCM configuration to apply for this send
     * @return {@link FcmSendResponse} containing the send result
     */
    @Override
    public FcmSendResponse sendToTopic(String topic, FcmMessage message, FcmConfig config) {
        if (topic == null || topic.trim().isEmpty()) {
            return FcmSendResponse.failure("Topic cannot be null or empty");
        }
        
        try {
            Message.Builder messageBuilder = buildFirebaseMessage(message, config);
            messageBuilder.setTopic(topic);
            
            String messageId = getFirebaseMessaging().send(messageBuilder.build());
            log.info("Successfully sent FCM message to topic: {}", topic);
            
            return FcmSendResponse.success(messageId);
            
        } catch (Exception e) {
            log.error("Failed to send FCM message to topic {}: {}", topic, e.getMessage(), e);
            return FcmSendResponse.failure("Failed to send FCM message to topic: " + e.getMessage(), e);
        }
    }
    
    @Override
    /**
     * Sends an FCM notification using a condition expression and the default configuration.
     *
     * @param condition the condition expression to evaluate for delivery
     * @param message   the message payload to send
     * @return {@link FcmSendResponse} containing the send result
     */
    public FcmSendResponse sendToCondition(String condition, FcmMessage message) {
        return sendToCondition(condition, message, getDefaultConfig());
    }
    
    /**
     * Sends an FCM notification using a condition expression and the provided configuration.
     *
     * @param condition the condition expression to evaluate for delivery
     * @param message   the message payload to send
     * @param config    the FCM configuration to apply for this send
     * @return {@link FcmSendResponse} containing the send result
     */
    @Override
    public FcmSendResponse sendToCondition(String condition, FcmMessage message, FcmConfig config) {
        if (condition == null || condition.trim().isEmpty()) {
            return FcmSendResponse.failure("Condition cannot be null or empty");
        }
        
        try {
            Message.Builder messageBuilder = buildFirebaseMessage(message, config);
            messageBuilder.setCondition(condition);
            
            String messageId = getFirebaseMessaging().send(messageBuilder.build());
            log.info("Successfully sent FCM message with condition: {}", condition);
            
            return FcmSendResponse.success(messageId);
            
        } catch (Exception e) {
            log.error("Failed to send FCM message with condition {}: {}", condition, e.getMessage(), e);
            return FcmSendResponse.failure("Failed to send FCM message with condition: " + e.getMessage(), e);
        }
    }
    
    @Override
    /**
     * Dispatches an FCM send request based on its target type (token, tokens, topic, or condition)
     * using the default configuration.
     *
     * @param request the high-level send request describing the target and message
     * @return {@link FcmSendResponse} containing the send result
     */
    public FcmSendResponse send(FcmSendRequest request) {
        return send(request, getDefaultConfig());
    }
    
    /**
     * Dispatches an FCM send request based on its target type (token, tokens, topic, or condition)
     * using the provided configuration.
     *
     * @param request the high-level send request describing the target and message
     * @param config  the FCM configuration to apply for this send
     * @return {@link FcmSendResponse} containing the send result
     */
    @Override
    public FcmSendResponse send(FcmSendRequest request, FcmConfig config) {
        if (request == null || request.getMessage() == null) {
            return FcmSendResponse.failure("FCM send request and message cannot be null");
        }
        
        switch (request.getTargetType()) {
            case TOKEN:
                if (request.getTokens() == null || request.getTokens().isEmpty()) {
                    return FcmSendResponse.failure("Token list cannot be null or empty for TOKEN target type");
                }
                return sendToToken(request.getTokens().get(0), request.getMessage(), config);
                
            case TOKENS:
                return sendToTokens(request.getTokens(), request.getMessage(), config);
                
            case TOPIC:
                return sendToTopic(request.getTopic(), request.getMessage(), config);
                
            case CONDITION:
                return sendToCondition(request.getCondition(), request.getMessage(), config);
                
            default:
                return FcmSendResponse.failure("Unsupported target type: " + request.getTargetType());
        }
    }
    
    @Override
    /**
     * Asynchronously sends an FCM notification to a single device token using the default configuration.
     *
     * @param token   the destination device token
     * @param message the message payload to send
     * @return {@link CompletableFuture} that completes with the send result
     */
    public CompletableFuture<FcmSendResponse> sendToTokenAsync(String token, FcmMessage message) {
        return CompletableFuture.supplyAsync(() -> sendToToken(token, message), fcmExecutor);
    }
    
    @Override
    /**
     * Asynchronously sends an FCM notification to multiple device tokens using the default configuration.
     *
     * @param tokens  list of destination device tokens
     * @param message the message payload to send
     * @return {@link CompletableFuture} that completes with the send result
     */
    public CompletableFuture<FcmSendResponse> sendToTokensAsync(List<String> tokens, FcmMessage message) {
        return CompletableFuture.supplyAsync(() -> sendToTokens(tokens, message), fcmExecutor);
    }
    
    @Override
    /**
     * Asynchronously sends an FCM notification to a topic using the default configuration.
     *
     * @param topic   the topic name to publish to
     * @param message the message payload to send
     * @return {@link CompletableFuture} that completes with the send result
     */
    public CompletableFuture<FcmSendResponse> sendToTopicAsync(String topic, FcmMessage message) {
        return CompletableFuture.supplyAsync(() -> sendToTopic(topic, message), fcmExecutor);
    }
    
    /**
     * Subscribes a list of device tokens to a topic.
     *
     * @param tokens list of device tokens to subscribe
     * @param topic  the topic name to subscribe to
     * @return {@code true} if all tokens were subscribed successfully, {@code false} otherwise
     */
    @Override
    public boolean subscribeToTopic(List<String> tokens, String topic) {
        try {
            TopicManagementResponse response = getFirebaseMessaging()
                    .subscribeToTopic(tokens, topic);
            
            log.info("Topic subscription result: {} successful, {} failed", 
                    response.getSuccessCount(), response.getFailureCount());
            
            return response.getFailureCount() == 0;
            
        } catch (Exception e) {
            log.error("Failed to subscribe tokens to topic {}: {}", topic, e.getMessage(), e);
            return false;
        }
    }
    
    /**
     * Unsubscribes a list of device tokens from a topic.
     *
     * @param tokens list of device tokens to unsubscribe
     * @param topic  the topic name to unsubscribe from
     * @return {@code true} if all tokens were unsubscribed successfully, {@code false} otherwise
     */
    @Override
    public boolean unsubscribeFromTopic(List<String> tokens, String topic) {
        try {
            TopicManagementResponse response = getFirebaseMessaging()
                    .unsubscribeFromTopic(tokens, topic);
            
            log.info("Topic unsubscription result: {} successful, {} failed", 
                    response.getSuccessCount(), response.getFailureCount());
            
            return response.getFailureCount() == 0;
            
        } catch (Exception e) {
            log.error("Failed to unsubscribe tokens from topic {}: {}", topic, e.getMessage(), e);
            return false;
        }
    }
    
    @Override
    public boolean validateToken(String token) {
        // Only check if token is not null or empty, no pattern validation
        return token != null && !token.trim().isEmpty();
    }
    
    /**
     * Returns the default FCM configuration used when no specific configuration
     * is provided for a send operation.
     *
     * @return default {@link FcmConfig} with standard settings
     */
    @Override
    public FcmConfig getDefaultConfig() {
        return FcmConfig.builder()
                .enableWebPush(true)
                .enableAndroidConfig(true)
                .enableApnsConfig(true)
                .defaultPriority(FcmMessage.FcmMessagePriority.HIGH)
                .defaultTimeToLive(86400L) // 24 hours
                .defaultContentAvailable(true)
                .defaultMutableContent(false)
                .build();
    }
    
    /**
     * Build Firebase Message from FcmMessage and FcmConfig
     */
    private Message.Builder buildFirebaseMessage(FcmMessage message, FcmConfig config) {
        Message.Builder builder = Message.builder();
        
        // Set notification
        builder.setNotification(buildFirebaseNotification(message));
        
        // Set data
        if (message.getData() != null && !message.getData().isEmpty()) {
            builder.putAllData(message.getData());
        }
        
        // Set priority
        AndroidConfig.Priority priority = message.getPriority() == FcmMessage.FcmMessagePriority.HIGH 
                ? AndroidConfig.Priority.HIGH 
                : AndroidConfig.Priority.NORMAL;
        
        // Set Android config
        if (config.isEnableAndroidConfig()) {
            AndroidConfig.Builder androidConfigBuilder = AndroidConfig.builder()
                    .setPriority(priority);
            Long ttl = message.getTimeToLive() != null ? message.getTimeToLive() : config.getDefaultTimeToLive();
            if (ttl != null) {
                androidConfigBuilder.setTtl(ttl);
            }
            builder.setAndroidConfig(androidConfigBuilder.build());
        }
        
        // Set APNS config
        if (config.isEnableApnsConfig()) {
            builder.setApnsConfig(buildApnsConfig(message, config));
        }
        
        // Set WebPush config
        if (config.isEnableWebPush()) {
            builder.setWebpushConfig(buildWebpushConfig(message));
        }
        
        return builder;
    }
    
    /**
     * Build APNS configuration from FcmMessage and config
     */
    private ApnsConfig buildApnsConfig(FcmMessage message, FcmConfig config) {
        Aps.Builder apsBuilder = Aps.builder()
                .setAlert(message.getBody());
        
        if (message.getBadge() != null && !message.getBadge().trim().isEmpty()) {
            try {
                apsBuilder.setBadge(Integer.parseInt(message.getBadge()));
            } catch (NumberFormatException e) {
                log.warn("Invalid badge format: {}", message.getBadge());
            }
        }
        
        if (message.getSound() != null) {
            apsBuilder.setSound(message.getSound());
        }
        
        Boolean contentAvailable = message.getContentAvailable() != null ? message.getContentAvailable() : config.getDefaultContentAvailable();
        if (contentAvailable != null) {
            apsBuilder.setContentAvailable(contentAvailable);
        }
        
        Boolean mutableContent = message.getMutableContent() != null ? message.getMutableContent() : config.getDefaultMutableContent();
        if (mutableContent != null) {
            apsBuilder.setMutableContent(mutableContent);
        }
        
        return ApnsConfig.builder()
                .setAps(apsBuilder.build())
                .build();
    }

    /**
     * Build WebPush configuration from FcmMessage.
     */
    private WebpushConfig buildWebpushConfig(FcmMessage message) {
        WebpushNotification webpushNotification = WebpushNotification.builder()
                .setTitle(message.getTitle())
                .setBody(message.getBody())
                .setIcon(message.getIcon())
                .setImage(message.getImageUrl())
                .setTag(message.getTag())
                .setRequireInteraction(false)
                .setSilent(false)
                .build();

        return WebpushConfig.builder()
                .setNotification(webpushNotification)
                .build();
    }

    /**
     * Build Firebase Notification from FcmMessage
     */
    private Notification buildFirebaseNotification(FcmMessage message) {
        return Notification.builder()
                .setTitle(message.getTitle())
                .setBody(message.getBody())
                .setImage(message.getImageUrl())
                .build();
    }
}

