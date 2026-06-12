package com.gulfnet.restaurantmanagement.config;

import com.gulfnet.restaurantmanagement.service.UserService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.StringHttpMessageConverter;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.web.servlet.LocaleResolver;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.charset.StandardCharsets;
import java.util.List;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Bean
    public LocaleResolver localeResolver(LocalizationProperties localizationProperties, UserService userService) {
        return new CustomLocaleResolver(localizationProperties, userService);
    }

    /**
     * Configures HTTP message converters to use UTF-8 encoding.
     * This ensures that Japanese, Thai, and other Unicode characters
     * are properly encoded in API responses.
     */
    @Override
    public void configureMessageConverters(List<HttpMessageConverter<?>> converters) {
        // Configure String converter with UTF-8
        StringHttpMessageConverter stringConverter = new StringHttpMessageConverter(StandardCharsets.UTF_8);
        converters.add(stringConverter);

        // Configure Jackson JSON converter with UTF-8
        MappingJackson2HttpMessageConverter jsonConverter = new MappingJackson2HttpMessageConverter();
        jsonConverter.setDefaultCharset(StandardCharsets.UTF_8);
        converters.add(jsonConverter);
    }

    /**
     * Extends existing message converters to ensure UTF-8 encoding.
     * This method is called after Spring Boot's default converters are added.
     */
    @Override
    public void extendMessageConverters(List<HttpMessageConverter<?>> converters) {
        for (HttpMessageConverter<?> converter : converters) {
            if (converter instanceof StringHttpMessageConverter stringConverter) {
                java.nio.charset.Charset currentCharset = stringConverter.getDefaultCharset();
                if (currentCharset == null || !currentCharset.equals(StandardCharsets.UTF_8)) {
                    // Replace with UTF-8 version
                    converters.remove(converter);
                    converters.add(new StringHttpMessageConverter(StandardCharsets.UTF_8));
                    break;
                }
            } else if (converter instanceof MappingJackson2HttpMessageConverter jsonConverter) {
                java.nio.charset.Charset currentCharset = jsonConverter.getDefaultCharset();
                if (currentCharset == null || !currentCharset.equals(StandardCharsets.UTF_8)) {
                    jsonConverter.setDefaultCharset(StandardCharsets.UTF_8);
                }
            }
        }
    }
} 