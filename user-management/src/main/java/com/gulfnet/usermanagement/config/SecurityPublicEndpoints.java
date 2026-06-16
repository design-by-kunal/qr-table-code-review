package com.gulfnet.usermanagement.config;

import java.util.List;

/**
 * Public endpoints for user-management that do not require JWT authentication
 * when the service is reached directly (bypassing edge-gateway).
 */
public final class SecurityPublicEndpoints {

    public static final List<String> PATHS = List.of(
            "/api/v1/users/login",
            "/api/v1/auth/forgot-password",
            "/api/v1/auth/verify-otp",
            "/actuator/**",
            "/swagger-ui/**",
            "/v3/api-docs/**"
    );

    private SecurityPublicEndpoints() {
    }
}
