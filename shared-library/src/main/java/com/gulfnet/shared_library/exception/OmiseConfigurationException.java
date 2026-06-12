package com.gulfnet.shared_library.exception;

/**
 * Exception thrown when Omise configuration is invalid or missing.
 */
public class OmiseConfigurationException extends RuntimeException {

    public OmiseConfigurationException(String message) {
        super(message);
    }

    public OmiseConfigurationException(String message, Throwable cause) {
        super(message, cause);
    }
}
