package com.gulfnet.edgegateway.filter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

@Component
public class ApiRequestTimingGatewayFilter implements GlobalFilter, Ordered {

    private static final Logger log = LoggerFactory.getLogger(ApiRequestTimingGatewayFilter.class);
    private static final String HEADER_X_APP_NAME = "XApp-Name";
    private static final String HEADER_X_APP_VERSION = "XApp-Version";
    private static final int X_APP_LOG_VALUE_MAX_LEN = 120;

    @Value("${logging.api.slow.warn-threshold-ms:1000}")
    private long warnThresholdMs;

    @Value("${logging.api.slow.error-threshold-ms:3000}")
    private long errorThresholdMs;

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        long startNanos = System.nanoTime();
        String method = Optional.ofNullable(exchange.getRequest().getMethod())
                .map(Object::toString)
                .orElse("UNKNOWN");
        String path = exchange.getRequest().getURI().getRawPath();
        String query = exchange.getRequest().getURI().getRawQuery();
        String requestPath = query == null ? path : path + "?" + query;
        String clientIp = Optional.ofNullable(exchange.getRequest().getHeaders().getFirst("X-Forwarded-For"))
                .map(value -> value.split(",")[0].trim())
                .filter(value -> !value.isBlank())
                .orElseGet(() -> Optional.ofNullable(exchange.getRequest().getRemoteAddress())
                        .map(address -> Optional.ofNullable(address.getAddress())
                                .map(inetAddress -> inetAddress.getHostAddress())
                                .orElse("unknown"))
                        .orElse("unknown"));

        if (!"OPTIONS".equalsIgnoreCase(method)) {
            logXAppHeaders(exchange.getRequest(), method, requestPath, clientIp);
        }

        return chain.filter(exchange).doFinally(signalType -> {
            long elapsedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNanos);
            int status = Optional.ofNullable(exchange.getResponse().getStatusCode())
                    .map(code -> code.value())
                    .orElse(500);

            if (elapsedMs >= errorThresholdMs) {
                log.error("GATEWAY_API_CALL method={} path=\"{}\" status={} durationMs={} clientIp={} warnThresholdMs={} errorThresholdMs={}",
                        method, requestPath, status, elapsedMs, clientIp, warnThresholdMs, errorThresholdMs);
            } else if (elapsedMs >= warnThresholdMs) {
                log.warn("GATEWAY_API_CALL method={} path=\"{}\" status={} durationMs={} clientIp={} warnThresholdMs={} errorThresholdMs={}",
                        method, requestPath, status, elapsedMs, clientIp, warnThresholdMs, errorThresholdMs);
            }
        });
    }

    /**
     * Logs optional client {@code XApp-Name} / {@code XApp-Version} headers for observability only.
     * These headers are not forwarded or mutated by the gateway.
     */
    private void logXAppHeaders(ServerHttpRequest request, String method, String requestPath, String clientIp) {
        String namePart = formatXAppHeaderForLog(request, HEADER_X_APP_NAME);
        String versionPart = formatXAppHeaderForLog(request, HEADER_X_APP_VERSION);
        log.info("XApp headers {} {} method={} path=\"{}\" clientIp={}", namePart, versionPart, method, requestPath, clientIp);
    }

    private static String formatXAppHeaderForLog(ServerHttpRequest request, String headerName) {
        List<String> values = request.getHeaders().get(headerName);
        if (values == null || values.isEmpty()) {
            return headerName + "=<key absent>";
        }
        String raw = values.get(0);
        if (raw == null || raw.isBlank()) {
            return headerName + "=<empty>";
        }
        String sanitized = raw.length() > X_APP_LOG_VALUE_MAX_LEN
                ? raw.substring(0, X_APP_LOG_VALUE_MAX_LEN) + "..."
                : raw;
        return headerName + "=" + sanitized;
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }
}
