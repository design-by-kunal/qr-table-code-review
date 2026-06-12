package com.gulfnet.integrationmanagement.security;

import com.gulfnet.integrationmanagement.config.SecurityPublicEndpoints;
import com.gulfnet.integrationmanagement.util.JwtUtil;
import org.springframework.stereotype.Component;

/**
 * JWT authentication filter for integration-management direct access.
 * Requires internal-user tokens with a {@code role} claim.
 */
@Component
public class JwtHeaderAuthenticationFilter extends com.gulfnet.shared_library.security.JwtHeaderAuthenticationFilter {

    public JwtHeaderAuthenticationFilter(JwtUtil jwtUtil) {
        super(jwtUtil, SecurityPublicEndpoints.PATHS, true);
    }
}
