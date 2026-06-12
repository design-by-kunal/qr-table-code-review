package com.gulfnet.usermanagement.util;

import com.gulfnet.shared_library.security.JwtTokenClaims;
import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.security.Key;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Date;
import java.util.UUID;

@Component
@Slf4j
public class JwtUtil implements JwtTokenClaims {

    @Value("${jwt.secret}")
    private String jwtSecret;

    private Key getSigningKey() {
        return Keys.hmacShaKeyFor(jwtSecret.getBytes());
    }

    /**
     * Generates a signed JWT for the given user, embedding the user ID as the subject
     * and including email and role as claims, with an explicit expiration time.
     *
     * @param userId     the unique identifier of the user (used as JWT subject)
     * @param email      the user's email address to include as a claim
     * @param roleName   the user's role name to include as a claim
     * @param expiryTime the token expiration time as a {@link LocalDateTime}
     * @return a compact JWT string signed with HS256
     */
    public String generateToken(UUID userId, String email, String roleName, LocalDateTime expiryTime) {
        Date expiryDate = Date.from(expiryTime.atOffset(ZoneOffset.UTC).toInstant());
        log.debug("JWT step: generating token for userId={}, role={}, expiry={}", userId, roleName, expiryTime);

        return Jwts.builder()
                .setSubject(userId.toString())
                .claim("email", email)
                .claim("role", roleName)
                .setIssuedAt(new Date())
                .setExpiration(expiryDate)
                .signWith(getSigningKey(), SignatureAlgorithm.HS256)
                .compact();
    }

    @Override
    public boolean validateToken(String token) {
        try {
            Jwts.parserBuilder()
                    .setSigningKey(getSigningKey())
                    .build()
                    .parseClaimsJws(token);
            log.debug("JWT step: token validation success");
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            log.warn("JWT step: token validation failed: {}", e.getMessage());
            return false;
        }
    }

    @Override
    public String getUserIdFromToken(String token) {
        Claims claims = extractAllClaims(token);
        log.debug("JWT step: extracted subject(userId) from token");
        return claims.getSubject();
    }

    @Override
    public String getRoleFromToken(String token) {
        Claims claims = extractAllClaims(token);
        return claims.get("role", String.class);
    }

    public Date getExpirationFromToken(String token) {
        Claims claims = extractAllClaims(token);
        log.debug("JWT step: extracted expiration from token: {}", claims.getExpiration());
        return claims.getExpiration();
    }

    private Claims extractAllClaims(String token) {
        return Jwts.parserBuilder()
                .setSigningKey(getSigningKey())
                .build()
                .parseClaimsJws(token)
                .getBody();
    }
}
