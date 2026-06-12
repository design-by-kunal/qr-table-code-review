package com.gulfnet.shared_library.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class ApiRequestTimingFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(ApiRequestTimingFilter.class);
    @Value("${logging.api.slow.warn-threshold-ms:1000}")
    private long warnThresholdMs;

    @Value("${logging.api.slow.error-threshold-ms:3000}")
    private long errorThresholdMs;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        long startNanos = System.nanoTime();
        String method = request.getMethod();
        String path = request.getRequestURI();
        String query = request.getQueryString();
        String clientIp = resolveClientIp(request);

        try {
            filterChain.doFilter(request, response);
        } finally {
            long elapsedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNanos);
            int status = response.getStatus();
            String requestPath = query == null ? path : path + "?" + query;

            if (elapsedMs >= errorThresholdMs) {
                log.error("API_CALL method={} path=\"{}\" status={} durationMs={} clientIp={} warnThresholdMs={} errorThresholdMs={}",
                        method, requestPath, status, elapsedMs, clientIp, warnThresholdMs, errorThresholdMs);
            } else if (elapsedMs >= warnThresholdMs) {
                log.warn("API_CALL method={} path=\"{}\" status={} durationMs={} clientIp={} warnThresholdMs={} errorThresholdMs={}",
                        method, requestPath, status, elapsedMs, clientIp, warnThresholdMs, errorThresholdMs);
            }
        }
    }

    private String resolveClientIp(HttpServletRequest request) {
        return Optional.ofNullable(request.getHeader("X-Forwarded-For"))
                .map(value -> value.split(",")[0].trim())
                .filter(value -> !value.isBlank())
                .orElseGet(() -> Optional.ofNullable(request.getRemoteAddr()).orElse("unknown"));
    }
}
