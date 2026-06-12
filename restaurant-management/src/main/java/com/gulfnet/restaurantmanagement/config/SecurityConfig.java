package com.gulfnet.restaurantmanagement.config;

import com.gulfnet.restaurantmanagement.security.JwtHeaderAuthenticationFilter;
import com.gulfnet.shared_library.security.PublicEndpointRequestMatcher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
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
     * Stateless REST API: JWT (or similar) in {@code Authorization} header, not cookie sessions.
     * CSRF targets browser-automatic cookie submission; disabling CSRF here matches Spring guidance for such APIs.
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
}
