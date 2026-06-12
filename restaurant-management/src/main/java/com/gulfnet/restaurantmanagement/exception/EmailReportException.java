package com.gulfnet.restaurantmanagement.exception;

/**
 * Exception thrown when scheduled email report generation or delivery fails.
 */
public class EmailReportException extends RuntimeException {

    public EmailReportException(String message) {
        super(message);
    }

    public EmailReportException(String message, Throwable cause) {
        super(message, cause);
    }
}
