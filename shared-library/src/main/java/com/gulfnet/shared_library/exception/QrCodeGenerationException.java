package com.gulfnet.shared_library.exception;

/**
 * Exception thrown when QR code generation fails.
 */
public class QrCodeGenerationException extends RuntimeException {

    public QrCodeGenerationException(String message) {
        super(message);
    }

    public QrCodeGenerationException(String message, Throwable cause) {
        super(message, cause);
    }
}
