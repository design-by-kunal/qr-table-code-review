package com.gulfnet.usermanagement.config;

import com.gulfnet.shared_library.security.PublicEndpointRequestMatcher;
import com.gulfnet.usermanagement.security.JwtHeaderAuthenticationFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
public class SecurityConfig {

    private final JwtHeaderAuthenticationFilter jwtHeaderAuthenticationFilter;

    public SecurityConfig(JwtHeaderAuthenticationFilter jwtHeaderAuthenticationFilter) {
        this.jwtHeaderAuthenticationFilter = jwtHeaderAuthenticationFilter;
    }

    /**
     * Configures the main Spring Security filter chain for the user-management service.
     * <p>
     * CSRF protection is disabled because this service is a stateless REST API: clients authenticate
     * with bearer tokens (JWT) in the {@code Authorization} header, not browser-managed session cookies.
     * CSRF primarily targets cookie-based sessions; token-in-header APIs are not vulnerable in the same way
     * when CORS is configured appropriately at the gateway or edge.
     * </p>
     *
     * @param http the {@link HttpSecurity} to configure
     * @return a built {@link SecurityFilterChain} instance
     * @throws Exception if the security configuration fails
     */
    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .csrf(AbstractHttpConfigurer::disable)
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers(new PublicEndpointRequestMatcher(SecurityPublicEndpoints.PATHS)).permitAll()
                .anyRequest().authenticated()
            )
            .addFilterBefore(jwtHeaderAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration authenticationConfiguration) throws Exception {
        return authenticationConfiguration.getAuthenticationManager();
    }
}
