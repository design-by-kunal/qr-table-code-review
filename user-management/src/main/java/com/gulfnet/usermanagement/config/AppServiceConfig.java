package com.gulfnet.usermanagement.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "app")
@Data
@SuppressWarnings("java:S1068") // Fields are used via Lombok-generated getters/setters
public class AppServiceConfig {
    private String bulkUploadPath;
    private boolean appOnPremises;
} 