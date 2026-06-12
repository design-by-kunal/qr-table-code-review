package com.gulfnet.restaurantmanagement.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import java.util.List;

@Data
@Component
@ConfigurationProperties(prefix = "localization")
@SuppressWarnings("java:S1068") // Fields are used via Lombok-generated getters/setters
public class LocalizationProperties {
    private List<String> languages;
}
