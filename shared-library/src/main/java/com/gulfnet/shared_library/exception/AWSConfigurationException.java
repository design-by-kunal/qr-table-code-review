package com.gulfnet.shared_library.exception;

/**
 * Exception thrown when AWS configuration is missing or invalid
 */
public class AWSConfigurationException extends IllegalStateException {
    
    public AWSConfigurationException(String message) {
        super(message);
    }
    
    public AWSConfigurationException(String message, Throwable cause) {
        super(message, cause);
    }
}
