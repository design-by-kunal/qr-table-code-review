package com.gulfnet.restaurantmanagement.service;

import com.gulfnet.shared_library.model.omise.OmiseWebhookEvent;

/**
 * Service for processing Omise webhook events
 */
public interface OmiseWebhookService {
    
    /**
     * Processes an Omise webhook event
     * @param event The webhook event from Omise
     */
    void processWebhookEvent(OmiseWebhookEvent event);
}
