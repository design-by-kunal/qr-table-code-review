package com.gulfnet.shared_library.exception;

/**
 * Exception thrown when email sending fails
 */
public class EmailSendingException extends RuntimeException {
    
    public EmailSendingException(String message) {
        super(message);
    }
    
    public EmailSendingException(String message, Throwable cause) {
        super(message, cause);
    }
}
