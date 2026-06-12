package com.gulfnet.restaurantmanagement.service;

import java.util.Optional;
import java.util.UUID;

/**
 * Abstraction layer for user-related operations
 * This hides the UserRepository dependency from business logic
 */
public interface UserService {
    
    /**
     * Get user language preference
     */
    Optional<String> getUserLanguageCode(UUID userId);
    
    /**
     * Get user name for audit purposes
     */
    Optional<String> getUserDisplayName(UUID userId);
    
    /**
     * Check if user exists
     */
    boolean userExists(UUID userId);
}
