package com.gulfnet.restaurantmanagement.config;

import org.springframework.core.env.PropertiesPropertySource;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.DefaultPropertySourceFactory;
import org.springframework.core.io.support.EncodedResource;

import jakarta.annotation.Nullable;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Properties;

/**
 * Custom PropertySourceFactory that ensures properties files are read with UTF-8 encoding.
 * This allows Japanese, Thai, and other Unicode characters to be written directly
 * in application.properties files without using Unicode escape sequences.
 * 
 * Usage: Use with @PropertySource annotation:
 * <pre>
 * {@code @PropertySource(value = "classpath:custom.properties", factory = Utf8PropertySourceFactory.class)}
 * </pre>
 */
public class Utf8PropertySourceFactory extends DefaultPropertySourceFactory {

    /**
     * Loads the backing resource as UTF-8 {@link Properties} and wraps them in a {@link PropertiesPropertySource}.
     *
     * @param name     logical property source name; defaults to the resource filename when null
     * @param resource encoded resource pointing at a {@code .properties} file
     * @return Spring {@link PropertySource} backed by parsed properties
     */
    @Override
    public PropertySource<?> createPropertySource(@Nullable String name, EncodedResource resource) throws IOException {
        if (resource == null) {
            throw new IllegalArgumentException("EncodedResource cannot be null");
        }
        
        String sourceName = name != null ? name : resource.getResource().getFilename();
        Resource resourceResource = resource.getResource();
        
        // Always use UTF-8 encoding for reading properties files
        Properties properties = new Properties();
        try (InputStreamReader reader = new InputStreamReader(
                resourceResource.getInputStream(), StandardCharsets.UTF_8)) {
            properties.load(reader);
        }
        
        return new PropertiesPropertySource(sourceName != null ? sourceName : "unknown", properties);
    }
}
