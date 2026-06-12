package com.gulfnet.shared_library.exception;

/**
 * Exception thrown when Omise API response is invalid or missing required data.
 */
public class OmiseResponseException extends RuntimeException {

    public OmiseResponseException(String message) {
        super(message);
    }

    public OmiseResponseException(String message, Throwable cause) {
        super(message, cause);
    }
}
