package com.gulfnet.restaurantmanagement.exception;

/**
 * Exception thrown when refund receipt generation fails.
 */
public class RefundReceiptException extends RuntimeException {

    public RefundReceiptException(String message, Throwable cause) {
        super(message, cause);
    }
}
