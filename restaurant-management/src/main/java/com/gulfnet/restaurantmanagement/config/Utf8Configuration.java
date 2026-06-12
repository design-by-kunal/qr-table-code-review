package com.gulfnet.restaurantmanagement.config;

import org.springframework.context.annotation.Configuration;

import jakarta.annotation.PostConstruct;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.logging.Logger;

/**
 * Configuration to verify UTF-8 encoding is properly configured.
 * 
 * Note: The actual UTF-8 setup is done by Utf8EnvironmentPostProcessor
 * which runs earlier in the Spring Boot lifecycle. This class provides
 * a secondary verification after bean initialization.
 */
@Configuration
public class Utf8Configuration {

    private static final Logger log = Logger.getLogger(Utf8Configuration.class.getName());

    /**
     * Verifies UTF-8 encoding is properly configured after bean initialization.
     * This serves as a secondary check to ensure the encoding is correct.
     */
    @PostConstruct
    public void verifyUtf8Encoding() {
        Charset defaultCharset = Charset.defaultCharset();
        
        if (defaultCharset.equals(StandardCharsets.UTF_8)) {
            if (log.isLoggable(java.util.logging.Level.FINE)) {
                log.fine("UTF-8 encoding verification passed.");
            }
        } else {
            String warningMessage = String.format(
                "UTF-8 encoding verification failed. Default charset is %s. " +
                "This may cause issues with Unicode characters in application.properties. " +
                "Ensure the application is started with -Dfile.encoding=UTF-8",
                defaultCharset
            );
            log.warning(warningMessage);
        }
    }
}
