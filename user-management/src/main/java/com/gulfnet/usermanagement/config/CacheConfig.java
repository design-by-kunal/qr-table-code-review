package com.gulfnet.usermanagement.config;

import org.springframework.context.annotation.Configuration;

/**
 * Cache configuration is handled by RedisCacheConfig in shared-library.
 * 
 * Redis caching is automatically enabled. Simply use annotations:
 * 
 * @Cacheable(value = "cacheName", key = "#param")
 * @CacheEvict(value = "cacheName", key = "#param")
 * @CachePut(value = "cacheName", key = "#param")
 * 
 * No additional code required!
 */
@Configuration
public class CacheConfig {
    // Redis cache configuration is provided by shared-library RedisCacheConfig
    // This class can be used for service-specific cache customizations if needed
}
