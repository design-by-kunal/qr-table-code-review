package com.gulfnet.restaurantmanagement.util;

import com.gulfnet.shared_library.security.JwtTokenClaims;
import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.security.Key;
import java.time.OffsetDateTime;
import java.util.Date;
import java.util.UUID;

@Component
public class JwtUtil implements JwtTokenClaims {

    @Value("${jwt.secret}")
    private String jwtSecret;

    private Key getSigningKey() {
        return Keys.hmacShaKeyFor(jwtSecret.getBytes());
    }

    /**
     * Generates a JWT token for customer sessions with session, restaurant, and table information.
     * The token includes session ID, restaurant ID, and table ID as claims, with an explicit expiration time.
     *
     * @param sessionId    the UUID of the customer session
     * @param restaurantId the UUID of the restaurant
     * @param tableId      the UUID of the table
     * @param expiry       token expiration time (must match {@code sessions.token_expiry_at})
     * @return JWT token string for the customer session
     */
    public String generateCustomerSessionToken(UUID sessionId, UUID restaurantId, UUID tableId, OffsetDateTime expiry) {
        Date expiryDate = Date.from(expiry.toInstant());
        return Jwts.builder()
                .setSubject(sessionId.toString())
                .claim("sessionId", sessionId.toString())
                .claim("restaurantId", restaurantId.toString())
                .claim("tableId", tableId.toString())
                .setIssuedAt(new Date())
                .setExpiration(expiryDate)
                .signWith(getSigningKey(), SignatureAlgorithm.HS256)
                .compact();
    }

    public Date getExpirationFromToken(String token) {
        Claims claims = Jwts.parserBuilder()
                .setSigningKey(getSigningKey())
                .build()
                .parseClaimsJws(token)
                .getBody();
        return claims.getExpiration();
    }

    @Override
    public boolean validateToken(String token) {
        try {
            Jwts.parserBuilder().setSigningKey(getSigningKey()).build().parseClaimsJws(token);
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            return false;
        }
    }

    public <T> T getClaimFromToken(String token, String claimKey, Class<T> requiredType) {
        Claims claims = Jwts.parserBuilder().setSigningKey(getSigningKey()).build().parseClaimsJws(token).getBody();
        return claims.get(claimKey, requiredType);
    }

    /**
     * Get userId from token (subject claim)
     */
    @Override
    public String getUserIdFromToken(String token) {
        Claims claims = Jwts.parserBuilder().setSigningKey(getSigningKey()).build().parseClaimsJws(token).getBody();
        return claims.getSubject();
    }

    /**
     * Get role from token
     */
    @Override
    public String getRoleFromToken(String token) {
        return getClaimFromToken(token, "role", String.class);
    }
}
