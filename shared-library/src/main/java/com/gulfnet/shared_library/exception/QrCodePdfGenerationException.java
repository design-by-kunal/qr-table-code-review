package com.gulfnet.shared_library.exception;

/**
 * Exception thrown when QR code PDF generation fails.
 */
public class QrCodePdfGenerationException extends RuntimeException {

    public QrCodePdfGenerationException(String message) {
        super(message);
    }

    public QrCodePdfGenerationException(String message, Throwable cause) {
        super(message, cause);
    }
}
