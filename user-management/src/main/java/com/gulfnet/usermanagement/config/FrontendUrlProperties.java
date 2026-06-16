package com.gulfnet.usermanagement.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "app.frontend.url")
public class FrontendUrlProperties {
    private String manager;
    private String hqAdmin;
    private String defaultUrl;
    
    /**
     * Get frontend URL based on role name
     * @param roleName The role name (e.g., "MANAGER", "HQ_ADMIN")
     * @return The frontend URL for the role
     */
    public String getUrlForRole(String roleName) {
        if (roleName == null) {
            return getDefaultUrl();
        }
        
        String upperRoleName = roleName.toUpperCase();
        
        if ("MANAGER".equals(upperRoleName)) {
            return getUrlOrDefault(manager);
        } else if ("HQ_ADMIN".equals(upperRoleName)) {
            return getUrlOrDefault(hqAdmin);
        }
        
        // For all other roles, return default URL
        return getDefaultUrl();
    }
    
    /**
     * Get URL if not null and not empty, otherwise return default URL
     */
    private String getUrlOrDefault(String url) {
        if (url != null && !url.trim().isEmpty()) {
            return url;
        }
        return getDefaultUrl();
    }
    
    /**
     * Get default URL or empty string if null
     */
    private String getDefaultUrl() {
        return defaultUrl != null ? defaultUrl : "";
    }
}
