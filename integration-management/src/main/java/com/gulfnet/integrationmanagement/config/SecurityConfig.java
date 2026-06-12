package com.gulfnet.integrationmanagement.config;

import com.gulfnet.integrationmanagement.security.JwtHeaderAuthenticationFilter;
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
