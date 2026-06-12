package com.gulfnet.shared_library.security;

import java.util.List;

/**
 * Matches request paths against a whitelist of public endpoints.
 * Supports exact paths, {@code /**} prefix wildcards, and {@code ^...} regex patterns.
 */
public final class SecurityPublicEndpointMatcher {

    private SecurityPublicEndpointMatcher() {
    }

    public static boolean isPublicEndpoint(String path, List<String> publicEndpoints) {
        if (path == null || publicEndpoints == null) {
            return false;
        }
        for (String allowed : publicEndpoints) {
            if (allowed.endsWith("/**")) {
                String prefix = allowed.substring(0, allowed.length() - 3);
                if (path.startsWith(prefix)) {
                    return true;
                }
            } else if (allowed.startsWith("^")) {
                if (path.matches(allowed)) {
                    return true;
                }
            } else if (path.equals(allowed)) {
                return true;
            }
        }
        return false;
    }
}
