package com.gulfnet.shared_library.exception;

/**
 * Exception thrown when Omise API operations fail.
 */
public class OmiseApiException extends RuntimeException {

    public OmiseApiException(String message) {
        super(message);
    }

    public OmiseApiException(String message, Throwable cause) {
        super(message, cause);
    }
}
