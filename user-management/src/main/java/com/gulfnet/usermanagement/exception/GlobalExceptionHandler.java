package com.gulfnet.usermanagement.exception;

import org.springframework.http.HttpStatus;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.server.ResponseStatusException;
import com.gulfnet.shared_library.exception.BadRequestException;
import com.gulfnet.shared_library.exception.InvalidEncryptedPayloadException;

import com.gulfnet.usermanagement.util.MessageUtil;

import java.time.Instant;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

@ControllerAdvice
public class GlobalExceptionHandler {

    /**
     * Constants for error response map keys.
     * Following industry standard practice of using constants for response keys.
     */
    private static class ResponseKeys {
        static final String STATUS = "status";
        static final String MESSAGE = "message";
        static final String TIMESTAMP = "timestamp";
        
        private ResponseKeys() {
            // Utility class - prevent instantiation
        }
    }

    /**
     * Constants for error messages.
     * Following industry standard practice of using constants for error messages.
     */
    private static class ErrorMessages {
        static final String UNEXPECTED_ERROR = "An unexpected error occurred.";
        
        private ErrorMessages() {
            // Utility class - prevent instantiation
        }
    }

    private final MessageUtil messageUtil;

    @Value("${bulk.upload.max-file-size:10485760}")
    private String maxFileSize;

    public GlobalExceptionHandler(MessageUtil messageUtil) {
        this.messageUtil = messageUtil;
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, Object>> handleResponseStatusException(ResponseStatusException ex) {
        Map<String, Object> body = new HashMap<>();
        body.put(ResponseKeys.STATUS, ex.getStatusCode().value());
        body.put(ResponseKeys.MESSAGE, ex.getReason());
        body.put(ResponseKeys.TIMESTAMP, Instant.now());
        return ResponseEntity.status(ex.getStatusCode()).body(body);
    }

    @ExceptionHandler(BadRequestException.class)
    public ResponseEntity<Map<String, Object>> handleBadRequestException(BadRequestException ex) {
        Map<String, Object> body = new HashMap<>();
        body.put(ResponseKeys.STATUS, HttpStatus.BAD_REQUEST.value());
        body.put(ResponseKeys.MESSAGE, ex.getMessage()); 
        body.put(ResponseKeys.TIMESTAMP, Instant.now());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
    }

    @ExceptionHandler(InvalidEncryptedPayloadException.class)
    public ResponseEntity<Map<String, Object>> handleInvalidEncryptedPayload(InvalidEncryptedPayloadException ex) {
        Map<String, Object> body = new HashMap<>();
        body.put(ResponseKeys.STATUS, HttpStatus.BAD_REQUEST.value());
        body.put(ResponseKeys.MESSAGE, InvalidEncryptedPayloadException.CLIENT_MESSAGE);
        body.put(ResponseKeys.TIMESTAMP, Instant.now());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
    }

    /**
     * Handles uncaught {@link RuntimeException} instances for this application as client errors.
     */
    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<Map<String, Object>> handleRuntimeException(RuntimeException ex) {
        Map<String, Object> body = new HashMap<>();
        body.put(ResponseKeys.STATUS, HttpStatus.BAD_REQUEST.value());
        body.put(ResponseKeys.MESSAGE, ex.getMessage() != null ? ex.getMessage() : "Invalid request");
        body.put(ResponseKeys.TIMESTAMP, Instant.now());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleGenericException(Exception ex) {
        Map<String, Object> body = new HashMap<>();
        body.put(ResponseKeys.STATUS, 500);
        body.put(ResponseKeys.MESSAGE, ErrorMessages.UNEXPECTED_ERROR);
        body.put(ResponseKeys.TIMESTAMP, Instant.now());
        return ResponseEntity.status(500).body(body);
    }

    /**
     * Handles MaxUploadSizeExceededException by returning a localized error message
     * indicating the maximum allowed file size. The message is formatted using the
     * configured max file size from application properties.
     *
     * @param ex the MaxUploadSizeExceededException that was thrown
     * @return {@link ResponseEntity} with HTTP 400 status and error details including
     *         localized message about file size limit
     */
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<Map<String, Object>> handleMaxUploadSizeException(MaxUploadSizeExceededException ex) {
        Map<String, Object> body = new HashMap<>();
        Locale locale = LocaleContextHolder.getLocale();

        body.put(ResponseKeys.STATUS, 400);
        body.put(ResponseKeys.MESSAGE, messageUtil.getMessage(
            "bulk.upload.error.file.size",
            locale,
            formatFileSize(Long.parseLong(maxFileSize))
        ));
        body.put(ResponseKeys.TIMESTAMP, Instant.now());
        return ResponseEntity.badRequest().body(body);
    }

    private String formatFileSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return (bytes / 1024) + " KB";
        if (bytes < 1024 * 1024 * 1024) return (bytes / (1024 * 1024)) + " MB";
        return (bytes / (1024 * 1024 * 1024)) + " GB";
    }
}
