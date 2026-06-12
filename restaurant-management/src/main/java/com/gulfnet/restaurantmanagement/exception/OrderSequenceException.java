package com.gulfnet.restaurantmanagement.exception;

/**
 * Exception thrown when order sequence generation fails.
 */
public class OrderSequenceException extends RuntimeException {

    public OrderSequenceException(String message, Throwable cause) {
        super(message, cause);
    }
}
