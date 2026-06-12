package com.gulfnet.restaurantmanagement.config;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.env.ConfigurableEnvironment;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * EnvironmentPostProcessor that ensures UTF-8 encoding is used throughout the application.
 * This runs early in Spring Boot startup (before properties are loaded) to ensure
 * Japanese, Thai, and other Unicode characters in application.properties are read correctly.
 * 
 * This processor:
 * 1. Sets the JVM file.encoding system property to UTF-8
 * 2. Verifies UTF-8 is available and logs warnings if not properly configured
 * 
 * Note: For this to work effectively, the JVM should be started with -Dfile.encoding=UTF-8
 * (configured in build.gradle for bootRun task).
 */
public class Utf8EnvironmentPostProcessor implements EnvironmentPostProcessor {

    private static final Logger log = Logger.getLogger(Utf8EnvironmentPostProcessor.class.getName());

    /**
     * Runs during Spring Boot environment preparation (before binding most user configuration).
     * Forces {@code file.encoding=UTF-8}, then checks {@link Charset#defaultCharset()} against
     * {@link StandardCharsets#UTF_8} and logs guidance if the JVM default is not UTF-8. Also logs
     * a severe message if {@code UTF-8} were unsupported (unlikely on standard JVMs).
     *
     * @param environment the mutable environment (unused; present for {@link EnvironmentPostProcessor} contract)
     * @param application the starting application (unused; present for contract)
     */
    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, 
                                       SpringApplication application) {
        // Set file.encoding system property early (before Spring Boot loads properties)
        System.setProperty("file.encoding", "UTF-8");
        
        // Verify UTF-8 is available and set as default
        Charset defaultCharset = Charset.defaultCharset();
        
        if (defaultCharset.equals(StandardCharsets.UTF_8)) {
            log.info("UTF-8 encoding is properly configured. Unicode characters in application.properties will be read correctly.");
        } else if (log.isLoggable(Level.WARNING)) {
            log.warning(String.format(
                "Default charset is %s, not UTF-8. " +
                "Some Unicode characters in application.properties may not be read correctly. " +
                "Ensure JVM is started with -Dfile.encoding=UTF-8",
                defaultCharset
            ));
        }
        
        // Additional verification: check if UTF-8 charset is available
        if (!Charset.isSupported("UTF-8") && log.isLoggable(java.util.logging.Level.SEVERE)) {
            log.severe("UTF-8 charset is not supported on this JVM. Unicode characters will not work correctly.");
        }
    }
}
