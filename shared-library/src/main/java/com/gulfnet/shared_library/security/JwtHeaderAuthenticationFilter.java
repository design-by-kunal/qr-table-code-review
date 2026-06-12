package com.gulfnet.shared_library.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;

/**
 * Validates JWT bearer tokens for direct service access, overrides identity headers from claims,
 * and populates the Spring Security context.
 */
@RequiredArgsConstructor
public class JwtHeaderAuthenticationFilter extends OncePerRequestFilter {

    private final JwtTokenClaims jwtTokenClaims;
    private final List<String> publicEndpoints;
    private final boolean requireInternalRoleClaim;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            filterChain.doFilter(request, response);
            return;
        }

        String path = request.getRequestURI();
        if (SecurityPublicEndpointMatcher.isPublicEndpoint(path, publicEndpoints)) {
            filterChain.doFilter(request, response);
            return;
        }

        String authHeader = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            sendUnauthorized(response, "Missing or invalid token");
            return;
        }

        String token = authHeader.substring(7);
        if (!jwtTokenClaims.validateToken(token)) {
            sendUnauthorized(response, "Invalid or expired token");
            return;
        }

        String userId;
        String userRole;
        try {
            userId = jwtTokenClaims.getUserIdFromToken(token);
            userRole = jwtTokenClaims.getRoleFromToken(token);
        } catch (Exception e) {
            sendUnauthorized(response, "Invalid token format");
            return;
        }

        if (requireInternalRoleClaim && (userRole == null || userRole.isBlank())) {
            sendUnauthorized(response, "Internal role claim required");
            return;
        }

        List<SimpleGrantedAuthority> authorities = userRole != null && !userRole.isBlank()
                ? List.of(new SimpleGrantedAuthority("ROLE_" + userRole))
                : Collections.emptyList();

        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(userId != null ? userId : "authenticated", null, authorities);
        SecurityContextHolder.getContext().setAuthentication(authentication);

        JwtHeaderOverrideRequestWrapper wrappedRequest =
                new JwtHeaderOverrideRequestWrapper(request, userId, userRole);
        filterChain.doFilter(wrappedRequest, response);
    }

    private void sendUnauthorized(HttpServletResponse response, String message) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        String body = String.format("{\"error\":\"Unauthorized: %s\"}", message);
        response.getOutputStream().write(body.getBytes(StandardCharsets.UTF_8));
    }
}
