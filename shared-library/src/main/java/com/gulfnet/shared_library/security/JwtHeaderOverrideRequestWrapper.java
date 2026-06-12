package com.gulfnet.shared_library.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;

import java.util.Collections;
import java.util.Enumeration;
import java.util.List;

/**
 * Wraps an incoming request so {@code User-ID}, {@code User-Id}, and {@code User-Role}
 * are sourced from validated JWT claims instead of client-supplied headers.
 */
public class JwtHeaderOverrideRequestWrapper extends HttpServletRequestWrapper {

    private static final String HEADER_USER_ID = "User-ID";
    private static final String HEADER_USER_ROLE = "User-Role";

    private final String userId;
    private final String userRole;

    public JwtHeaderOverrideRequestWrapper(HttpServletRequest request, String userId, String userRole) {
        super(request);
        this.userId = userId;
        this.userRole = userRole;
    }

    @Override
    public String getHeader(String name) {
        if (name != null) {
            if (HEADER_USER_ID.equalsIgnoreCase(name)) {
                return userId;
            }
            if (HEADER_USER_ROLE.equalsIgnoreCase(name)) {
                return userRole;
            }
        }
        return super.getHeader(name);
    }

    @Override
    public Enumeration<String> getHeaders(String name) {
        String overridden = getHeader(name);
        if (overridden != null && isOverriddenHeader(name)) {
            return Collections.enumeration(List.of(overridden));
        }
        return super.getHeaders(name);
    }

    private boolean isOverriddenHeader(String name) {
        return HEADER_USER_ID.equalsIgnoreCase(name) || HEADER_USER_ROLE.equalsIgnoreCase(name);
    }
}
