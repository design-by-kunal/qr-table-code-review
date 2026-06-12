package com.gulfnet.edgegateway.filter;
import com.gulfnet.edgegateway.util.JwtUtil;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.jsonwebtoken.Claims;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;

import reactor.core.publisher.Mono;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Component
public class JwtAuthenticationFilter implements GlobalFilter, Ordered {
        
    private static final Logger log = LoggerFactory.getLogger(JwtAuthenticationFilter.class);
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final String HEADER_WAITER_APP_VERSION = "WaiterAppVersion";
    private static final String HEADER_CASHIER_APP_VERSION = "CashierAppVersion";
    private static final String HEADER_KDS_APP_VERSION = "KDSAppVersion";
    private static final String HEADER_APP_TYPE = "App-Type";
    private static final String HEADER_APP_VERSION = "App-Version";
    private static final String HEADER_X_APP_NAME = "XApp-Name";
    private static final String HEADER_X_APP_VERSION = "XApp-Version";
    private static final int X_APP_JWT_LOG_VALUE_MAX_LEN = 40;
    private static final Duration SESSION_VALIDATION_TIMEOUT = Duration.ofSeconds(5);
    private static final Duration CUSTOMER_SESSION_CACHE_TTL = Duration.ofSeconds(30);
    private static final long CACHE_TTL_MILLIS = CUSTOMER_SESSION_CACHE_TTL.toMillis();

    @Autowired
    private JwtUtil jwtUtil;

    @Autowired
    private WebClient.Builder webClientBuilder;

    @Value("${security.ws-auth-failure-rate-limit.enabled:true}")
    private boolean wsAuthFailureRateLimitEnabled;

    @Value("${security.ws-auth-failure-rate-limit.max-attempts:1}")
    private int wsAuthFailureMaxAttempts;

    @Value("${security.ws-auth-failure-rate-limit.window-seconds:1}")
    private int wsAuthFailureWindowSeconds;

    @Value("${security.ws-auth-failure-rate-limit.cooldown-seconds:10}")
    private int wsAuthFailureCooldownSeconds;

    /**
     * Short-lived cache to avoid validating the same customer session on every request.
     * Key: sessionId, Value: epoch millis when this cache entry expires.
     */
    private final Map<String, Long> customerSessionValidationCache = new ConcurrentHashMap<>();
    private final Map<String, RateLimitCounter> wsAuthFailureCounterMap = new ConcurrentHashMap<>();
    private final Map<String, Long> wsAuthFailureBlockedUntilMap = new ConcurrentHashMap<>();

    private static final List<String> WHITELIST = List.of(
       
        
            // User
            "/user/api/v1/users/login",
            "/user/api/v1/auth/forgot-password",
            "/user/api/v1/auth/verify-otp",

            // Restaurant
            "/restaurant/api/v1/restaurantchain/config",
            "/restaurant/api/v1/restaurant/config",
            "^/restaurant/api/v1/table/[a-fA-F0-9\\-]{36}/restaurant/[a-fA-F0-9\\-]{36}/session$",
            "/restaurant/api/v1/kds/config/**",

            // Actuator & Docs
            "/user/actuator/**",
            "/user/swagger-ui/**",
            "/user/v3/api-docs/**",
            "/restaurant/swagger-ui/**",
            "/restaurant/v3/api-docs/**",
            "/integration/swagger-ui/**",
            "/integration/v3/api-docs/**",
            "/restaurant/api/v1/omise/webhook",
            "/restaurant/api/v1/gmo/link-plus/notify",
            "^/restaurant/api/v1/transactions/[a-fA-F0-9\\-]{36}/omise-qr$"
    );


    // JWT claim keys
    private static final String CLAIM_SESSION_ID = "sessionId";
    private static final String CLAIM_RESTAURANT_ID = "restaurantId";
    private static final String CLAIM_TABLE_ID = "tableId";
    private static final String CLAIM_ROLE = "role";

    /**
     * Centralized error messages for authentication and authorization failures.
     * Following industry standard practice of using constants for error messages.
     */
    private static class ErrorMessages {
        private static final String MISSING_OR_INVALID_TOKEN = "Missing or invalid token";
        private static final String INVALID_OR_EXPIRED_TOKEN = "Invalid or expired token";
        private static final String INVALID_TOKEN_FORMAT = "Invalid token format";
        private static final String INVALID_TOKEN_FORMAT_WEBSOCKET = "Invalid token format for WebSocket connection";
        private static final String CUSTOMER_SESSION_INVALID_OR_EXPIRED = "Customer session invalid or expired";
        private static final String USER_SESSION_INVALID_OR_EXPIRED = "User session invalid or expired";
        private static final String AUTH_SERVICE_UNAVAILABLE = "Authentication service temporarily unavailable";
        private static final String WEBSOCKET_SUFFIX = " for WebSocket connection";
        private static final String TOO_MANY_FAILED_WEBSOCKET_AUTH_ATTEMPTS =
                "Too many failed WebSocket authentication attempts. Retry later";
        
        private ErrorMessages() {
            // Utility class - prevent instantiation
        }
    }

    /**
     * Main filter method that processes all incoming requests through the API gateway.
     * Handles JWT authentication, whitelist checking, and routes requests to appropriate
     * validation methods based on request type (HTTP or WebSocket).
     *
     * @param exchange the server web exchange containing request and response
     * @param chain the gateway filter chain to continue processing
     * @return Mono<Void> indicating completion of the filter operation
     */
    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {

        String path = exchange.getRequest().getURI().getPath();
        String method = exchange.getRequest().getMethod().name();

        // Allow OPTIONS requests (CORS preflight) to pass through without authentication
        if ("OPTIONS".equalsIgnoreCase(method)) {
            return chain.filter(exchange);
        }

        // Check if this is a WebSocket upgrade request
        boolean isWebSocketUpgrade = isWebSocketUpgradeRequest(exchange);

        if (isWebSocketUpgrade && path.startsWith("/restaurant/ws") && isBlockedForFailedWebSocketAuth(exchange)) {
            return sendTooManyRequestsResponse(exchange, ErrorMessages.TOO_MANY_FAILED_WEBSOCKET_AUTH_ATTEMPTS);
        }

        // Check whitelist (skip for WebSocket as they need authentication)
        if (!isWebSocketUpgrade) {
            for (String allowed : WHITELIST) {
                if (allowed.endsWith("/**")) {
                    String prefix = allowed.substring(0, allowed.length() - 3);
                    if (path.startsWith(prefix)) {
                        return chain.filter(exchange);
                    }
                } else if (allowed.startsWith("^")) {
                    if (path.matches(allowed)) {
                        return chain.filter(exchange);
                    }
                } else {
                    if (path.equals(allowed)) {
                        return chain.filter(exchange);
                    }
                }
            }
        }

        // For WebSocket upgrade requests to /restaurant/ws, validate JWT and session status
        if (isWebSocketUpgrade && path.startsWith("/restaurant/ws")) {
            return validateWebSocketConnection(exchange, chain);
        }

        // For regular HTTP requests, use existing validation with session checks
        return validateHttpRequest(exchange, chain);
    }

    /**
     * Check if the request is a WebSocket upgrade request
     */
    private boolean isWebSocketUpgradeRequest(ServerWebExchange exchange) {
        String upgradeHeader = exchange.getRequest().getHeaders().getFirst("Upgrade");
        String connectionHeader = exchange.getRequest().getHeaders().getFirst("Connection");
        return "websocket".equalsIgnoreCase(upgradeHeader) 
            && connectionHeader != null 
            && connectionHeader.toLowerCase().contains("upgrade");
    }

    /**
     * Extract and validate JWT token from request
     * @return Mono containing TokenValidationResult or empty if validation fails
     */
    private Mono<TokenValidationResult> extractAndValidateToken(ServerWebExchange exchange, String errorContext) {
        String authHeader = exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            logJwtValidationFailure(exchange, "missing_or_invalid_authorization_header", null);
            return sendUnauthorizedResponse(exchange, ErrorMessages.MISSING_OR_INVALID_TOKEN + errorContext)
                    .then(Mono.empty());
        }

        String token = authHeader.substring(7);
        if (!jwtUtil.validateToken(token)) {
            logJwtValidationFailure(exchange, "invalid_or_expired_token", token);
            recordFailedWebSocketAuthAttempt(exchange, token);
            return sendUnauthorizedResponse(exchange, ErrorMessages.INVALID_OR_EXPIRED_TOKEN + errorContext)
                    .then(Mono.empty());
        }

        try {
            Claims claims = jwtUtil.getClaimsFromToken(token);
            String userRole = claims.get(CLAIM_ROLE, String.class);
            return Mono.just(new TokenValidationResult(token, claims, userRole, authHeader));
        } catch (Exception e) {
            log.error("Exception processing token: {}", e.getMessage(), e);
            return sendUnauthorizedResponse(exchange, ErrorMessages.INVALID_TOKEN_FORMAT + errorContext)
                    .then(Mono.empty());
        }
    }

    /**
     * Result class for token validation
     */
    private static class TokenValidationResult {
        final String token;
        final Claims claims;
        final String userRole;
        final String authHeader;
        
        TokenValidationResult(String token, Claims claims, String userRole, String authHeader) {
            this.token = token;
            this.claims = claims;
            this.userRole = userRole;
            this.authHeader = authHeader;
        }
    }

    /**
     * Validate WebSocket connection - checks JWT signature and validates session status.
     * Validates table sessions and user status to prevent expired sessions or disabled users
     * from establishing WebSocket connections.
     */
    private Mono<Void> validateWebSocketConnection(ServerWebExchange exchange, GatewayFilterChain chain) {
        return extractAndValidateToken(exchange, ErrorMessages.WEBSOCKET_SUFFIX)
                .flatMap(validation -> {
                    try {
                        Claims claims = validation.claims;
                        String userRole = validation.userRole;

                        if (claims.get(CLAIM_SESSION_ID) != null) {
                            // Customer session token flow - validate table session
                            String sessionId = claims.get(CLAIM_SESSION_ID, String.class);
                            String restaurantId = claims.get(CLAIM_RESTAURANT_ID, String.class);
                            String tableId = claims.get(CLAIM_TABLE_ID, String.class);
                            
                            return validateCustomerSession(exchange, chain, validation.authHeader, sessionId, restaurantId, tableId,
                                    ErrorMessages.WEBSOCKET_SUFFIX);
                            
                        } else {
                            // Internal user token flow - validate user session
                            String userId = jwtUtil.getUserIdFromToken(validation.token);
                            return validateUserSession(exchange, chain, validation.authHeader, userId, userRole,
                                    ErrorMessages.WEBSOCKET_SUFFIX);
                        }
                    } catch (Exception e) {
                        log.error("Exception processing WebSocket token: {}", e.getMessage(), e);
                        return sendUnauthorizedResponse(exchange, ErrorMessages.INVALID_TOKEN_FORMAT_WEBSOCKET);
                    }
                });
    }

    /**
     * Validate regular HTTP requests with session validation
     */
    private Mono<Void> validateHttpRequest(ServerWebExchange exchange, GatewayFilterChain chain) {
        return extractAndValidateToken(exchange, "")
                .flatMap(validation -> {
                    try {
                        Claims claims = validation.claims;
                        String userRole = validation.userRole;

                        if (claims.get(CLAIM_SESSION_ID) != null) {
                            // Customer session token flow
                            String sessionId = claims.get(CLAIM_SESSION_ID, String.class);
                            String restaurantId = claims.get(CLAIM_RESTAURANT_ID, String.class);
                            String tableId = claims.get(CLAIM_TABLE_ID, String.class);
                            
                            return validateCustomerSession(exchange, chain, validation.authHeader, sessionId, restaurantId, tableId, "");
                            
                        } else {
                            // Internal user token flow
                            String userId = jwtUtil.getUserIdFromToken(validation.token);
                            return validateUserSession(exchange, chain, validation.authHeader, userId, userRole, "");
                        }
                    } catch (Exception e) {
                        log.error("Exception extracting info from token: {}", e.getMessage(), e);
                        return sendUnauthorizedResponse(exchange, ErrorMessages.INVALID_TOKEN_FORMAT);
                    }
                });
    }

    /**
     * Validates a customer table session by calling the restaurant-management service.
     * If validation succeeds, adds session-related headers to the request and continues the filter chain.
     * If validation fails, returns an unauthorized response.
     *
     * @param exchange the server web exchange containing request and response
     * @param chain the gateway filter chain to continue processing
     * @param authHeader the authorization header value from the original request
     * @param sessionId the customer session ID extracted from the JWT token
     * @param restaurantId the restaurant ID extracted from the JWT token
     * @param tableId the table ID extracted from the JWT token
     * @param errorSuffix optional suffix to append to error messages (e.g., for WebSocket connections)
     * @return Mono<Void> indicating completion of the validation operation
     */
    private Mono<Void> validateCustomerSession(ServerWebExchange exchange, GatewayFilterChain chain,
                                              String authHeader, String sessionId, String restaurantId,
                                              String tableId, String errorSuffix) {
        if (isCustomerSessionRecentlyValidated(sessionId)) {
            ServerHttpRequest cachedValidatedRequest = exchange.getRequest().mutate()
                    .header("Session-ID", sessionId)
                    .header("Restaurant-ID", restaurantId)
                    .header("Table-ID", tableId)
                    .header("Authorization", authHeader)
                    .build();
            return chain.filter(exchange.mutate().request(cachedValidatedRequest).build());
        }

        return webClientBuilder.build()
                .get()
                .uri("lb://restaurant-management/api/v1/table/{sessionId}/validate", sessionId)
                .header(HttpHeaders.AUTHORIZATION, authHeader)
                .retrieve()
                .bodyToMono(Void.class)
                .timeout(SESSION_VALIDATION_TIMEOUT)
                .doOnSuccess(unused -> {
                    cacheValidatedCustomerSession(sessionId);
                })
                .then(Mono.defer(() -> {
                    ServerHttpRequest mutatedRequest = exchange.getRequest().mutate()
                            .header("Session-ID", sessionId)
                            .header("Restaurant-ID", restaurantId)
                            .header("Table-ID", tableId)
                            .header("Authorization", authHeader)
                            .build();
                    return chain.filter(exchange.mutate().request(mutatedRequest).build());
                }))
                .onErrorResume(e -> handleSessionValidationError(
                        exchange,
                        e,
                        ErrorMessages.CUSTOMER_SESSION_INVALID_OR_EXPIRED + errorSuffix));
    }

    private boolean isCustomerSessionRecentlyValidated(String sessionId) {
        if (sessionId == null || sessionId.isBlank()) {
            return false;
        }

        Long expiresAt = customerSessionValidationCache.get(sessionId);
        if (expiresAt == null) {
            return false;
        }

        long now = System.currentTimeMillis();
        if (expiresAt <= now) {
            customerSessionValidationCache.remove(sessionId);
            return false;
        }

        return true;
    }

    private void cacheValidatedCustomerSession(String sessionId) {
        if (sessionId == null || sessionId.isBlank()) {
            return;
        }
        customerSessionValidationCache.put(sessionId, System.currentTimeMillis() + CACHE_TTL_MILLIS);
    }

    /**
     * Validates a user session by calling the user-management service.
     * If validation succeeds, adds user-related headers to the request and continues the filter chain.
     * If validation fails, returns an unauthorized response.
     *
     * @param exchange the server web exchange containing request and response
     * @param chain the gateway filter chain to continue processing
     * @param authHeader the authorization header value from the original request
     * @param userId the user ID extracted from the JWT token
     * @param userRole the user role extracted from the JWT token
     * @param errorSuffix optional suffix to append to error messages (e.g., for WebSocket connections)
     * @return Mono<Void> indicating completion of the validation operation
     */
    private Mono<Void> validateUserSession(ServerWebExchange exchange, GatewayFilterChain chain,
                                           String authHeader, String userId, String userRole,
                                           String errorSuffix) {
        AppContextHeaders appCtx = extractAppContextHeaders(exchange.getRequest(), userRole);
        return webClientBuilder.build()
                .get()
                .uri("lb://user-management/api/v1/users/validate-session")
                .header(HttpHeaders.AUTHORIZATION, authHeader)
                .header(HEADER_APP_TYPE, appCtx.appType())
                .header(HEADER_APP_VERSION, appCtx.appVersion())
                .retrieve()
                .bodyToMono(Void.class)
                .timeout(SESSION_VALIDATION_TIMEOUT)
                .then(Mono.defer(() -> {
                    ServerHttpRequest mutatedRequest = exchange.getRequest().mutate()
                            .header("User-ID", userId)
                            .header("User-Role", userRole)
                            .header("Authorization", authHeader)
                            .header(HEADER_APP_TYPE, appCtx.appType())
                            .header(HEADER_APP_VERSION, appCtx.appVersion())
                            .build();
                    return chain.filter(exchange.mutate().request(mutatedRequest).build());
                }))
                .onErrorResume(e -> handleSessionValidationError(
                        exchange,
                        e,
                        ErrorMessages.USER_SESSION_INVALID_OR_EXPIRED + errorSuffix));
    }

    private AppContextHeaders extractAppContextHeaders(ServerHttpRequest request, String fallbackAppType) {
        String existingType = request.getHeaders().getFirst(HEADER_APP_TYPE);
        String existingVersion = request.getHeaders().getFirst(HEADER_APP_VERSION);
        if (existingType != null && !existingType.isBlank()) {
            return new AppContextHeaders(existingType.trim(), existingVersion != null ? existingVersion.trim() : "");
        }

        String waiterVersion = request.getHeaders().getFirst(HEADER_WAITER_APP_VERSION);
        if (waiterVersion != null && !waiterVersion.isBlank()) {
            return new AppContextHeaders("WAITER", waiterVersion.trim());
        }

        String cashierVersion = request.getHeaders().getFirst(HEADER_CASHIER_APP_VERSION);
        if (cashierVersion != null && !cashierVersion.isBlank()) {
            return new AppContextHeaders("CASHIER", cashierVersion.trim());
        }

        String kdsVersion = request.getHeaders().getFirst(HEADER_KDS_APP_VERSION);
        if (kdsVersion != null && !kdsVersion.isBlank()) {
            return new AppContextHeaders("KDS", kdsVersion.trim());
        }

        // Fall back to role-derived appType when version is not provided.
        return new AppContextHeaders(fallbackAppType != null ? fallbackAppType.trim() : "", "");
    }

    private record AppContextHeaders(String appType, String appVersion) {
    }

    /**
     * Maps session validation failures to an HTTP response: 401 for downstream 4xx, 503 otherwise.
     *
     * @param exchange               current gateway exchange
     * @param error                    failure from the user-management validate-session call
     * @param unauthorizedMessage      body message when treating the failure as unauthorized
     * @return a completed response mono (never propagates the original error)
     */
    private Mono<Void> handleSessionValidationError(ServerWebExchange exchange, Throwable error, String unauthorizedMessage) {
        if (isTimeoutRelatedError(error)) {
            log.error("Session validation timed out after {} ms: {}", SESSION_VALIDATION_TIMEOUT.toMillis(), error.getMessage());
            return sendServiceUnavailableResponse(exchange, ErrorMessages.AUTH_SERVICE_UNAVAILABLE);
        }

        if (error instanceof WebClientResponseException webClientResponseException) {
            HttpStatus status = HttpStatus.resolve(webClientResponseException.getStatusCode().value());
            if (status != null && status.is4xxClientError()) {
                log.warn("Session validation rejected by downstream with status {}: {}",
                        status.value(), webClientResponseException.getResponseBodyAsString());
                return sendUnauthorizedResponse(exchange, unauthorizedMessage);
            }

            log.error("Session validation downstream error with status {}: {}",
                    webClientResponseException.getStatusCode().value(),
                    webClientResponseException.getResponseBodyAsString());
            return sendServiceUnavailableResponse(exchange, ErrorMessages.AUTH_SERVICE_UNAVAILABLE);
        }

        log.error("Session validation call failed: {}", error.getMessage(), error);
        return sendServiceUnavailableResponse(exchange, ErrorMessages.AUTH_SERVICE_UNAVAILABLE);
    }

    private boolean isTimeoutRelatedError(Throwable error) {
        Throwable current = error;
        while (current != null) {
            String message = current.getMessage();
            if (message != null && message.toLowerCase().contains("timeout")) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private void logJwtValidationFailure(ServerWebExchange exchange, String reason, String token) {
        ServerHttpRequest request = exchange.getRequest();
        String path = request.getURI().getPath();
        String method = request.getMethod() != null ? request.getMethod().name() : "UNKNOWN";
        String clientIp = getClientIp(request);
        String userAgent = sanitizeHeaderValue(request.getHeaders().getFirst(HttpHeaders.USER_AGENT), 160);
        boolean websocketUpgrade = isWebSocketUpgradeRequest(exchange);
        String tokenFingerprint = token == null ? "none" : safeTokenFingerprint(token);
        String userId = extractUserIdForLogging(token);
        AppVersionLogContext appVersionLogContext = extractAppVersionLogContext(request);
        String xAppNameForLog = formatXAppHeaderStateForJwtLog(request, HEADER_X_APP_NAME);
        String xAppVersionForLog = formatXAppHeaderStateForJwtLog(request, HEADER_X_APP_VERSION);

        log.warn(
                "JWT validation failure: reason={}, method={}, path={}, clientIp={}, websocketUpgrade={}, tokenFp={}, userId={}, appVersionKey={}, appVersionValue={}, XApp-Name={}, XApp-Version={}, userAgent={}",
                reason, method, path, clientIp, websocketUpgrade, tokenFingerprint, userId,
                appVersionLogContext.headerKey(), appVersionLogContext.headerValue(),
                xAppNameForLog, xAppVersionForLog, userAgent);
    }

    private AppVersionLogContext extractAppVersionLogContext(ServerHttpRequest request) {
        String waiterVersion = sanitizeHeaderValue(request.getHeaders().getFirst(HEADER_WAITER_APP_VERSION), 40);
        if (!"none".equals(waiterVersion)) {
            return new AppVersionLogContext(HEADER_WAITER_APP_VERSION, waiterVersion);
        }

        String cashierVersion = sanitizeHeaderValue(request.getHeaders().getFirst(HEADER_CASHIER_APP_VERSION), 40);
        if (!"none".equals(cashierVersion)) {
            return new AppVersionLogContext(HEADER_CASHIER_APP_VERSION, cashierVersion);
        }

        String kdsVersion = sanitizeHeaderValue(request.getHeaders().getFirst(HEADER_KDS_APP_VERSION), 40);
        if (!"none".equals(kdsVersion)) {
            return new AppVersionLogContext(HEADER_KDS_APP_VERSION, kdsVersion);
        }

        return new AppVersionLogContext("none", "none");
    }

    private record AppVersionLogContext(String headerKey, String headerValue) {
    }

    /**
     * Observability-only: how {@code XApp-Name} / {@code XApp-Version} appear on the request (not forwarded by this filter).
     */
    private static String formatXAppHeaderStateForJwtLog(ServerHttpRequest request, String headerName) {
        List<String> values = request.getHeaders().get(headerName);
        if (values == null || values.isEmpty()) {
            return "<key absent>";
        }
        String raw = values.get(0);
        if (raw == null || raw.isBlank()) {
            return "<empty>";
        }
        String sanitized = raw.replaceAll("[\\r\\n\\t]", " ").trim();
        if (sanitized.length() <= X_APP_JWT_LOG_VALUE_MAX_LEN) {
            return sanitized;
        }
        return sanitized.substring(0, X_APP_JWT_LOG_VALUE_MAX_LEN) + "...";
    }

    private String extractUserIdForLogging(String token) {
        if (token == null || token.isBlank()) {
            return "unknown";
        }

        try {
            String userId = jwtUtil.getUserIdFromToken(token);
            if (userId != null && !userId.isBlank()) {
                return userId;
            }
        } catch (Exception ignored) {
            // Fall back to unsigned payload decoding for observability only.
        }

        try {
            String[] tokenParts = token.split("\\.");
            if (tokenParts.length < 2) {
                return "unknown";
            }
            byte[] payloadBytes = Base64.getUrlDecoder().decode(tokenParts[1]);
            JsonNode payload = OBJECT_MAPPER.readTree(payloadBytes);
            String userId = firstNonBlankField(payload, "userId", "sub", "uid");
            return userId != null ? userId : "unknown";
        } catch (Exception ignored) {
            return "unknown";
        }
    }

    private String firstNonBlankField(JsonNode payload, String... fieldNames) {
        for (String fieldName : fieldNames) {
            JsonNode valueNode = payload.get(fieldName);
            if (valueNode != null && !valueNode.isNull()) {
                String value = valueNode.asText();
                if (value != null && !value.isBlank()) {
                    return value;
                }
            }
        }
        return null;
    }

    private boolean isBlockedForFailedWebSocketAuth(ServerWebExchange exchange) {
        if (!wsAuthFailureRateLimitEnabled) {
            return false;
        }
        String key = buildWsFailureKey(exchange, null);
        long now = System.currentTimeMillis();
        Long blockedUntil = wsAuthFailureBlockedUntilMap.get(key);
        if (blockedUntil == null) {
            return false;
        }
        if (blockedUntil > now) {
            return true;
        }
        wsAuthFailureBlockedUntilMap.remove(key);
        wsAuthFailureCounterMap.remove(key);
        return false;
    }

    private void recordFailedWebSocketAuthAttempt(ServerWebExchange exchange, String token) {
        if (!wsAuthFailureRateLimitEnabled || !isWebSocketAuthPath(exchange)) {
            return;
        }

        String key = buildWsFailureKey(exchange, token);
        long now = System.currentTimeMillis();
        long windowMillis = Duration.ofSeconds(Math.max(1, wsAuthFailureWindowSeconds)).toMillis();
        long cooldownMillis = Duration.ofSeconds(Math.max(1, wsAuthFailureCooldownSeconds)).toMillis();
        int maxAttempts = Math.max(1, wsAuthFailureMaxAttempts);

        RateLimitCounter updatedCounter = wsAuthFailureCounterMap.compute(key, (unused, existing) -> {
            if (existing == null || now - existing.windowStartMillis >= windowMillis) {
                return new RateLimitCounter(now, 1);
            }
            existing.attemptCount.incrementAndGet();
            return existing;
        });

        if (updatedCounter != null && updatedCounter.attemptCount.get() >= maxAttempts) {
            wsAuthFailureBlockedUntilMap.put(key, now + cooldownMillis);
            wsAuthFailureCounterMap.remove(key);
        }
    }

    private boolean isWebSocketAuthPath(ServerWebExchange exchange) {
        String path = exchange.getRequest().getURI().getPath();
        return isWebSocketUpgradeRequest(exchange) && path != null && path.startsWith("/restaurant/ws");
    }

    private String buildWsFailureKey(ServerWebExchange exchange, String token) {
        ServerHttpRequest request = exchange.getRequest();
        String clientIp = getClientIp(request);
        return clientIp;
    }

    private static class RateLimitCounter {
        final long windowStartMillis;
        final AtomicInteger attemptCount;

        RateLimitCounter(long windowStartMillis, int initialAttempts) {
            this.windowStartMillis = windowStartMillis;
            this.attemptCount = new AtomicInteger(initialAttempts);
        }
    }

    private String getClientIp(ServerHttpRequest request) {
        String forwardedFor = request.getHeaders().getFirst("X-Forwarded-For");
        if (forwardedFor != null && !forwardedFor.isBlank()) {
            String firstIp = forwardedFor.split(",")[0].trim();
            if (!firstIp.isBlank()) {
                return firstIp;
            }
        }

        String realIp = request.getHeaders().getFirst("X-Real-IP");
        if (realIp != null && !realIp.isBlank()) {
            return realIp.trim();
        }

        InetSocketAddress remoteAddress = request.getRemoteAddress();
        if (remoteAddress != null && remoteAddress.getAddress() != null) {
            return remoteAddress.getAddress().getHostAddress();
        }

        return "unknown";
    }

    private String sanitizeHeaderValue(String rawValue, int maxLen) {
        if (rawValue == null || rawValue.isBlank()) {
            return "none";
        }
        String sanitized = rawValue.replaceAll("[\\r\\n\\t]", " ").trim();
        if (sanitized.length() <= maxLen) {
            return sanitized;
        }
        return sanitized.substring(0, maxLen) + "...";
    }

    private String safeTokenFingerprint(String token) {
        int prefixLength = Math.min(12, token.length());
        return token.substring(0, prefixLength) + "...";
    }

    private Mono<Void> sendUnauthorizedResponse(ServerWebExchange exchange, String message) {
        exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
        applyConnectionCloseForFailedWebSocketAuth(exchange);
        if (isWebSocketUpgradeRequest(exchange)) {
            return exchange.getResponse().setComplete();
        }
        exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);
        String body = String.format("{\"error\":\"Unauthorized: %s\"}", message);
        DataBuffer buffer = exchange.getResponse().bufferFactory()
                .wrap(body.getBytes(StandardCharsets.UTF_8));
        return exchange.getResponse().writeWith(Mono.just(buffer));
    }

    private Mono<Void> sendServiceUnavailableResponse(ServerWebExchange exchange, String message) {
        exchange.getResponse().setStatusCode(HttpStatus.SERVICE_UNAVAILABLE);
        applyConnectionCloseForFailedWebSocketAuth(exchange);
        if (isWebSocketUpgradeRequest(exchange)) {
            return exchange.getResponse().setComplete();
        }
        exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);
        String body = String.format("{\"error\":\"Service Unavailable: %s\"}", message);
        DataBuffer buffer = exchange.getResponse().bufferFactory().wrap(body.getBytes(StandardCharsets.UTF_8));
        return exchange.getResponse().writeWith(Mono.just(buffer));
    }

    private Mono<Void> sendTooManyRequestsResponse(ServerWebExchange exchange, String message) {
        exchange.getResponse().setStatusCode(HttpStatus.TOO_MANY_REQUESTS);
        applyConnectionCloseForFailedWebSocketAuth(exchange);
        if (isWebSocketUpgradeRequest(exchange)) {
            exchange.getResponse().getHeaders().add("Retry-After", String.valueOf(Math.max(1, wsAuthFailureCooldownSeconds)));
            return exchange.getResponse().setComplete();
        }
        exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);
        exchange.getResponse().getHeaders().add("Retry-After", String.valueOf(Math.max(1, wsAuthFailureCooldownSeconds)));
        String body = String.format("{\"error\":\"Too Many Requests: %s\"}", message);
        DataBuffer buffer = exchange.getResponse().bufferFactory().wrap(body.getBytes(StandardCharsets.UTF_8));
        return exchange.getResponse().writeWith(Mono.just(buffer));
    }

    private void applyConnectionCloseForFailedWebSocketAuth(ServerWebExchange exchange) {
        if (!isWebSocketUpgradeRequest(exchange)) {
            return;
        }
        exchange.getResponse().getHeaders().set(HttpHeaders.CONNECTION, "close");
    }

    @Override
    public int getOrder() {
        return -1;
    }
}
