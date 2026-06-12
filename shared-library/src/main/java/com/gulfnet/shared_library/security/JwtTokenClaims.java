package com.gulfnet.shared_library.security;

/**
 * Minimal contract for JWT validation and claim extraction used by service security filters.
 */
public interface JwtTokenClaims {

    boolean validateToken(String token);

    String getUserIdFromToken(String token);

    String getRoleFromToken(String token);
}
