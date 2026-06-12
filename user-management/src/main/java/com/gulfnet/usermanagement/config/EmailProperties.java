package com.gulfnet.usermanagement.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "app.default.registration")
@SuppressWarnings("java:S1068") // Fields are used via Lombok-generated getters/setters
public class EmailProperties {
    private String email;
}
