package com.gulfnet.restaurantmanagement.security;

import com.gulfnet.restaurantmanagement.config.SecurityPublicEndpoints;
import com.gulfnet.restaurantmanagement.util.JwtUtil;
import org.springframework.stereotype.Component;

/**
 * JWT authentication filter for restaurant-management direct access.
 * Overrides {@code User-ID} and {@code User-Role} headers from validated JWT claims.
 * Customer session tokens (no {@code role} claim) are accepted as authenticated.
 */
@Component
public class JwtHeaderAuthenticationFilter extends com.gulfnet.shared_library.security.JwtHeaderAuthenticationFilter {

    public JwtHeaderAuthenticationFilter(JwtUtil jwtUtil) {
        super(jwtUtil, SecurityPublicEndpoints.PATHS, false);
    }
}
