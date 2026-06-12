package com.gulfnet.restaurantmanagement.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@ConfigurationProperties(prefix = "restaurant.chain")
@Data
@SuppressWarnings("java:S1068") // Fields are used via Lombok-generated getters/setters
public class LanguageConfiguration {
    
    private List<LanguageConfig> supportedLanguages;
    
    @Data
    @SuppressWarnings("java:S1068")
    public static class LanguageConfig {
        private String languageCode;
        private boolean compulsory;
    }
}
