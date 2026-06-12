package com.gulfnet.edgegateway.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.reactive.CorsWebFilter;
import org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource;

import java.util.List;

@Data
@Configuration
@ConfigurationProperties(prefix = "cors")
public class CorsConfig {

    private List<String> allowedOriginPatterns = List.of();
    private List<String> allowedMethods = List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS", "HEAD");
    /** Use "*" to allow any request header on preflight (requires allowCredentials=false). */
    private List<String> allowedHeaders = List.of("*");
    private List<String> exposedHeaders = List.of();
    private boolean allowCredentials = false;
    private long maxAge = 3600L;

    @Bean
    public CorsWebFilter corsWebFilter() {
        CorsConfiguration config = new CorsConfiguration();
        allowedOriginPatterns.forEach(config::addAllowedOriginPattern);
        config.setAllowedMethods(allowedMethods);
        config.setAllowedHeaders(allowedHeaders);
        config.setExposedHeaders(exposedHeaders);
        config.setAllowCredentials(allowCredentials);
        config.setMaxAge(maxAge);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return new CorsWebFilter(source);
    }
}
