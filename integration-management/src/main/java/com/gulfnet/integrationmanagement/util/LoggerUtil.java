package com.gulfnet.integrationmanagement.util;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class LoggerUtil {

    private static boolean isLoggingEnabled;

    @Value("${custom.logging.enabled}")
    private boolean loggingEnabledProperty;

    @PostConstruct
    public void init() {
        setLoggingEnabled(loggingEnabledProperty);
    }
    
    private static void setLoggingEnabled(boolean enabled) {
        isLoggingEnabled = enabled;
    }

    public static void info(String message, Object... args) {
        if (isLoggingEnabled) {
            if (args != null && args.length > 0) {
                log.info(message, args);
            } else {
                log.info(message);
            }
        }
    }

    public static void debug(String message, Object... args) {
        if (isLoggingEnabled) {
            if (args != null && args.length > 0) {
                log.debug(message, args);
            } else {
                log.debug(message);
            }
        }
    }

    public static void error(String message, Object... args) {
        if (isLoggingEnabled) {
            if (args != null && args.length > 0) {
                log.error(message, args);
            } else {
                log.error(message);
            }
        }
    }

    public static void warn(String message, Object... args) {
        if (isLoggingEnabled) {
            if (args != null && args.length > 0) {
                log.warn(message, args);
            } else {
                log.warn(message);
            }
        }
    }

    public static void trace(String message, Object... args) {
        if (isLoggingEnabled) {
            if (args != null && args.length > 0) {
                log.trace(message, args);
            } else {
                log.trace(message);
            }
        }
    }
}
