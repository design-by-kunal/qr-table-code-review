package com.gulfnet.restaurantmanagement.exception;

/**
 * Exception thrown when an email fails to send.
 */
public class EmailSendException extends RuntimeException {

    public EmailSendException(String message, Throwable cause) {
        super(message, cause);
    }
}
