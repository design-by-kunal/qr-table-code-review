package com.gulfnet.restaurantmanagement.service.impl;

import com.gulfnet.restaurantmanagement.service.UserService;
import com.gulfnet.shared_library.entity.User;
import com.gulfnet.shared_library.repository.UserRepository;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

/**
 * Implementation that encapsulates UserRepository access
 * This centralizes all user-related database operations
 */
@Service
public class UserServiceImpl implements UserService {
    
    private final UserRepository userRepository;
    
    public UserServiceImpl(UserRepository userRepository) {
        this.userRepository = userRepository;
    }
    
    @Override
    @Transactional(readOnly = true)
    @Cacheable(value = "userLanguagePreferences", key = "#userId", unless = "#result.isEmpty()")
    public Optional<String> getUserLanguageCode(UUID userId) {
        return userRepository.findById(userId)
                .map(User::getLanguageCode);
    }
    
    @Override
    @Transactional(readOnly = true)
    @Cacheable(value = "userDisplayNames", key = "#userId", unless = "#result.isEmpty()")
    public Optional<String> getUserDisplayName(UUID userId) {
        return userRepository.findById(userId)
                .map(user -> user.getFirstName() + " " + user.getLastName());
    }
    
    @Override
    @Transactional(readOnly = true)
    @Cacheable(value = "userExistence", key = "#userId")
    public boolean userExists(UUID userId) {
        return userRepository.existsById(userId);
    }
}
