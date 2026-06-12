package com.gulfnet.integrationmanagement.service;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Firebase Cloud Messaging service interface
 * Provides comprehensive FCM functionality including single token, multicast, topic, and conditional messaging
 */
public interface FcmService {
    
    /**
     * Send FCM message to a single token
     * @param token Device token
     * @param message FCM message details
     * @return FCM send response
     */
    FcmSendResponse sendToToken(String token, FcmMessage message);
    
    /**
     * Send FCM message to a single token with custom configuration
     * @param token Device token
     * @param message FCM message details
     * @param config Custom FCM configuration
     * @return FCM send response
     */
    FcmSendResponse sendToToken(String token, FcmMessage message, FcmConfig config);
    
    /**
     * Send FCM message to multiple tokens (multicast)
     * @param tokens List of device tokens
     * @param message FCM message details
     * @return FCM send response with batch results
     */
    FcmSendResponse sendToTokens(List<String> tokens, FcmMessage message);
    
    /**
     * Send FCM message to multiple tokens with custom configuration
     * @param tokens List of device tokens
     * @param message FCM message details
     * @param config Custom FCM configuration
     * @return FCM send response with batch results
     */
    FcmSendResponse sendToTokens(List<String> tokens, FcmMessage message, FcmConfig config);
    
    /**
     * Send FCM message to a topic
     * @param topic Firebase topic name
     * @param message FCM message details
     * @return FCM send response
     */
    FcmSendResponse sendToTopic(String topic, FcmMessage message);
    
    /**
     * Send FCM message to a topic with custom configuration
     * @param topic Firebase topic name
     * @param message FCM message details
     * @param config Custom FCM configuration
     * @return FCM send response
     */
    FcmSendResponse sendToTopic(String topic, FcmMessage message, FcmConfig config);
    
    /**
     * Send FCM message using a condition
     * @param condition Firebase condition expression
     * @param message FCM message details
     * @return FCM send response
     */
    FcmSendResponse sendToCondition(String condition, FcmMessage message);
    
    /**
     * Send FCM message using a condition with custom configuration
     * @param condition Firebase condition expression
     * @param message FCM message details
     * @param config Custom FCM configuration
     * @return FCM send response
     */
    FcmSendResponse sendToCondition(String condition, FcmMessage message, FcmConfig config);
    
    /**
     * Send FCM message using a generic send request
     * @param request FCM send request with all details
     * @return FCM send response
     */
    FcmSendResponse send(FcmSendRequest request);
    
    /**
     * Send FCM message using a generic send request with custom configuration
     * @param request FCM send request with all details
     * @param config Custom FCM configuration
     * @return FCM send response
     */
    FcmSendResponse send(FcmSendRequest request, FcmConfig config);
    
    /**
     * Asynchronous version of sendToToken
     * @param token Device token
     * @param message FCM message details
     * @return CompletableFuture with FCM send response
     */
    CompletableFuture<FcmSendResponse> sendToTokenAsync(String token, FcmMessage message);
    
    /**
     * Asynchronous version of sendToTokens
     * @param tokens List of device tokens
     * @param message FCM message details
     * @return CompletableFuture with FCM send response
     */
    CompletableFuture<FcmSendResponse> sendToTokensAsync(List<String> tokens, FcmMessage message);
    
    /**
     * Asynchronous version of sendToTopic
     * @param topic Firebase topic name
     * @param message FCM message details
     * @return CompletableFuture with FCM send response
     */
    CompletableFuture<FcmSendResponse> sendToTopicAsync(String topic, FcmMessage message);
    
    /**
     * Subscribe tokens to a topic
     * @param tokens List of device tokens
     * @param topic Firebase topic name
     * @return Success status
     */
    boolean subscribeToTopic(List<String> tokens, String topic);
    
    /**
     * Unsubscribe tokens from a topic
     * @param tokens List of device tokens
     * @param topic Firebase topic name
     * @return Success status
     */
    boolean unsubscribeFromTopic(List<String> tokens, String topic);
    
    /**
     * Validate a device token
     * @param token Device token to validate
     * @return True if token is valid format
     */
    boolean validateToken(String token);
    
    /**
     * Get default FCM configuration
     * @return Default FCM configuration
     */
    FcmConfig getDefaultConfig();
}

