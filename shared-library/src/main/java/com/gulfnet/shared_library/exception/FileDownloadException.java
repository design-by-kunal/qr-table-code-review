package com.gulfnet.shared_library.exception;

/**
 * Exception thrown when file download from S3 fails
 */
public class FileDownloadException extends RuntimeException {
    
    public FileDownloadException(String message) {
        super(message);
    }
    
    public FileDownloadException(String message, Throwable cause) {
        super(message, cause);
    }
}
