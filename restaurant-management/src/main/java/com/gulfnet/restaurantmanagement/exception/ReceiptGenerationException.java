package com.gulfnet.restaurantmanagement.exception;

/**
 * Exception thrown when receipt generation fails.
 */
public class ReceiptGenerationException extends RuntimeException {

    public ReceiptGenerationException(String message, Throwable cause) {
        super(message, cause);
    }
}
