package com.gulfnet.integrationmanagement.config;

import java.util.List;

/**
 * Public endpoints for integration-management (documentation and health checks only).
 */
public final class SecurityPublicEndpoints {

    public static final List<String> PATHS = List.of(
            "/actuator/**",
            "/swagger-ui/**",
            "/v3/api-docs/**"
    );

    private SecurityPublicEndpoints() {
    }
}
