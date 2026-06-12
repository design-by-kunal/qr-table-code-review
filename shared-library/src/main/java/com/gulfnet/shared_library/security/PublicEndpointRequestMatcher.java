package com.gulfnet.shared_library.security;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.web.util.matcher.RequestMatcher;

import java.util.List;

/**
 * Spring Security {@link RequestMatcher} that delegates to {@link SecurityPublicEndpointMatcher}.
 */
public class PublicEndpointRequestMatcher implements RequestMatcher {

    private final List<String> publicEndpoints;

    public PublicEndpointRequestMatcher(List<String> publicEndpoints) {
        this.publicEndpoints = publicEndpoints;
    }

    @Override
    public boolean matches(HttpServletRequest request) {
        return SecurityPublicEndpointMatcher.isPublicEndpoint(request.getRequestURI(), publicEndpoints);
    }
}
