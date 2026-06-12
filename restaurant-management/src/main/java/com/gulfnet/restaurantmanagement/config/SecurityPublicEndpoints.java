package com.gulfnet.restaurantmanagement.config;

import java.util.List;

/**
 * Public endpoints for restaurant-management that do not require JWT authentication
 * when the service is reached directly (bypassing edge-gateway).
 * Paths mirror edge-gateway whitelist without the {@code /restaurant} prefix.
 */
public final class SecurityPublicEndpoints {

    public static final List<String> PATHS = List.of(
            "/api/v1/restaurantchain/config",
            "/api/v1/restaurant/config",
            "^/api/v1/table/[a-fA-F0-9\\-]{36}/restaurant/[a-fA-F0-9\\-]{36}/session$",
            "/api/v1/kds/config/**",
            "/api/v1/omise/webhook",
            "/api/v1/gmo/link-plus/notify",
            "^/api/v1/transactions/[a-fA-F0-9\\-]{36}/omise-qr$",
            "/actuator/**",
            "/swagger-ui/**",
            "/v3/api-docs/**"
    );

    private SecurityPublicEndpoints() {
    }
}
