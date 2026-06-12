package com.gulfnet.shared_library.exception;

/**
 * Exception thrown when an invalid payment type is provided.
 */
public class InvalidPaymentTypeException extends RuntimeException {

    public InvalidPaymentTypeException(String message) {
        super(message);
    }

    public InvalidPaymentTypeException(String message, Throwable cause) {
        super(message, cause);
    }
}
