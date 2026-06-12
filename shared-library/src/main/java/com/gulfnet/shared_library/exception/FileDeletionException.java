package com.gulfnet.shared_library.exception;

/**
 * Exception thrown when file deletion from S3 fails
 */
public class FileDeletionException extends RuntimeException {
    
    public FileDeletionException(String message) {
        super(message);
    }
    
    public FileDeletionException(String message, Throwable cause) {
        super(message, cause);
    }
}
