package com.gulfnet.restaurantmanagement.config;

import com.gulfnet.restaurantmanagement.util.JwtUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;
import org.springframework.web.socket.server.HandshakeInterceptor;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.security.Principal;
import java.util.Map;

@Slf4j
@Configuration
@EnableWebSocketMessageBroker
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private static final String USER_ID_CLAIM = "userId";
    private static final String SESSION_PENDING_LOCALE = "wsHandshakeLocale";

    private final JwtUtil jwtUtil;
    private final WebSocketClientLocaleRegistry webSocketClientLocaleRegistry;

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws")
                .setAllowedOriginPatterns("*")
                .addInterceptors(new UserIdHandshakeInterceptor(jwtUtil))
                .withSockJS()
                .setSuppressCors(true);
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.setApplicationDestinationPrefixes("/app");
        registry.enableSimpleBroker("/topic");
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(new PrincipalLocaleChannelInterceptor(webSocketClientLocaleRegistry));
    }

    private static class UserIdHandshakeInterceptor implements HandshakeInterceptor {

        private final JwtUtil jwtUtil;

        UserIdHandshakeInterceptor(JwtUtil jwtUtil) {
            this.jwtUtil = jwtUtil;
        }

        /**
         * Extracts {@code userId} from a Bearer JWT (when valid) and optional {@code locale} from the query string
         * into handshake attributes; always returns {@code true} so the socket may still connect without auth.
         */
        @Override
        public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                       WebSocketHandler wsHandler, Map<String, Object> attributes) {
            String authHeader = request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION);

            if (authHeader != null && authHeader.startsWith("Bearer ")) {
                String token = authHeader.substring(7);
                try {
                    if (jwtUtil.validateToken(token)) {
                        String userId = jwtUtil.getClaimFromToken(token, USER_ID_CLAIM, String.class);
                        if (userId == null || userId.trim().isEmpty()) {
                            userId = jwtUtil.getClaimFromToken(token, "sub", String.class);
                        }

                        if (userId != null && !userId.trim().isEmpty()) {
                            attributes.put(USER_ID_CLAIM, userId);
                            log.info("WebSocket handshake: userId extracted from JWT and stored in session attributes - userId: {}", userId);
                        } else {
                            log.debug("WebSocket handshake: No userId claim found in JWT (likely customer session or unauthenticated)");
                        }
                    } else {
                        log.warn("WebSocket handshake: JWT token is invalid or expired");
                    }
                } catch (Exception e) {
                    log.warn("WebSocket handshake: Failed to extract userId from JWT - {}", e.getMessage());
                }
            } else {
                log.debug("WebSocket handshake: No Authorization header found (customer session or unauthenticated connection)");
            }

            String localeFromQuery = extractQueryParam(request.getURI(), "locale");
            if (localeFromQuery != null && !localeFromQuery.isBlank()) {
                attributes.put(SESSION_PENDING_LOCALE, localeFromQuery.trim());
                log.debug("WebSocket handshake: locale query param present: {}", localeFromQuery.trim());
            }

            return true;
        }

        @Override
        public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                   WebSocketHandler wsHandler, Exception exception) {
            if (exception != null) {
                log.error("WebSocket handshake failed: {}", exception.getMessage(), exception);
            }
        }

        /**
         * Returns the first decoded query parameter value for {@code name}, or {@code null} when absent.
         *
         * @param uri  request URI (may be null)
         * @param name case-insensitive parameter name
         */
        private static String extractQueryParam(URI uri, String name) {
            if (uri == null || uri.getQuery() == null) {
                return null;
            }
            for (String pair : uri.getQuery().split("&")) {
                int i = pair.indexOf('=');
                if (i > 0 && name.equalsIgnoreCase(pair.substring(0, i))) {
                    return URLDecoder.decode(pair.substring(i + 1), StandardCharsets.UTF_8);
                }
            }
            return null;
        }
    }

    private static class PrincipalLocaleChannelInterceptor implements ChannelInterceptor {

        private final WebSocketClientLocaleRegistry webSocketClientLocaleRegistry;

        PrincipalLocaleChannelInterceptor(WebSocketClientLocaleRegistry webSocketClientLocaleRegistry) {
            this.webSocketClientLocaleRegistry = webSocketClientLocaleRegistry;
        }

        /**
         * On STOMP {@code CONNECT}, sets the Spring {@link Principal} from session attributes and records locale
         * for later resolution; other commands pass through unchanged.
         */
        @Override
        public Message<?> preSend(Message<?> message, MessageChannel channel) {
            StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);

            if (accessor != null && StompCommand.CONNECT.equals(accessor.getCommand())) {
                Map<String, Object> sessionAttributes = accessor.getSessionAttributes();
                String sessionId = accessor.getSessionId();

                if (sessionAttributes != null) {
                    String userId = (String) sessionAttributes.get(USER_ID_CLAIM);

                    if (userId != null && !userId.trim().isEmpty()) {
                        Principal userPrincipal = () -> userId;
                        accessor.setUser(userPrincipal);

                        String stompLocale = accessor.getFirstNativeHeader("locale");
                        if (stompLocale == null || stompLocale.isBlank()) {
                            Object pending = sessionAttributes.get(SESSION_PENDING_LOCALE);
                            if (pending != null) {
                                stompLocale = pending.toString();
                            }
                        }
                        if (stompLocale != null && !stompLocale.isBlank()) {
                            webSocketClientLocaleRegistry.recordLocale(userId, stompLocale);
                            log.info("WebSocket CONNECT: locale recorded for userId={}, locale={}, SessionId={}",
                                    userId, stompLocale.trim(), sessionId);
                        }

                        log.info("WebSocket CONNECT: Principal set successfully - SessionId: {}, UserId: {}, Principal.getName(): {}",
                                sessionId, userId, userPrincipal.getName());
                    } else {
                        log.debug("WebSocket CONNECT: No userId found in session attributes - SessionId: {} (customer session or unauthenticated)",
                                sessionId);
                    }
                } else {
                    log.warn("WebSocket CONNECT: Session attributes are null - SessionId: {}", sessionId);
                }
            } else if (accessor != null) {
                StompCommand command = accessor.getCommand();
                Principal principal = accessor.getUser();
                String sessionId = accessor.getSessionId();

                if (command != null && principal != null) {
                    log.debug("WebSocket {}: SessionId: {}, Principal: {} ({})",
                            command, sessionId, principal.getName(), principal.getClass().getSimpleName());
                }
            }

            return message;
        }
    }
}
