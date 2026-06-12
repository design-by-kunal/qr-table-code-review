package com.gulfnet.restaurantmanagement.exception;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.validation.FieldError;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.dao.DataIntegrityViolationException;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import java.util.Locale;
import org.springframework.transaction.TransactionSystemException;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

@RestControllerAdvice
public class GlobalExceptionHandler {

	private static final String RESPONSE_STATUS = "status";
	private static final String RESPONSE_MESSAGE = "message";
	private static final String RESPONSE_TIMESTAMP = "timestamp";
	private static final String CONSTRAINT_QUOTE_PREFIX = "constraint \"";
	private static final String MSG_KEY_RESTAURANT_GROUP_CODE_EXISTS = "restaurantgroup.create.error.code.exists";

	@Value("${bulk.upload.max-file-size:10485760}")
    private long maxFileSizeBytes;

	private static final Logger logger = LoggerFactory.getLogger(GlobalExceptionHandler.class);

	private final MessageSource messageSource;

	public GlobalExceptionHandler(MessageSource messageSource) {
		this.messageSource = messageSource;
	}

	/**
	 * Handles {@link ResponseStatusException} thrown by controllers/services and formats a consistent JSON body.
	 * <p>
	 * Uses the HTTP status from the exception and returns a body containing {@code status}, {@code message}, and
	 * {@code timestamp}. If the reason text contains placeholder-like braces, a best-effort extraction is applied to
	 * unwrap quoted message content.
	 * </p>
	 *
	 * @param ex the exception carrying status and reason
	 * @return response entity with status code and a serialized error body
	 */
	@ExceptionHandler(ResponseStatusException.class)
	public ResponseEntity<Map<String, Object>> handleResponseStatusException(ResponseStatusException ex) {
		logger.warn("Handled ResponseStatusException: {}", ex.getReason(), ex);
		Map<String, Object> body = new HashMap<>();
		body.put(RESPONSE_STATUS, ex.getStatusCode().value());
		
		// Format the message if it contains message source placeholders
		String message = ex.getReason();
		if (message != null && message.contains("{") && message.contains("}")) {
			// Extract the message key and parameters
			String[] parts = message.split("\"");
			if (parts.length >= 2) {
				message = parts[1]; // Get the actual message without quotes
			}
		}
		
		body.put(RESPONSE_MESSAGE, message);
		body.put(RESPONSE_TIMESTAMP, Instant.now());
		return ResponseEntity.status(ex.getStatusCode()).body(body);
	}

	/**
	 * Handles {@link MethodArgumentNotValidException} produced by {@code @Valid} request validation failures.
	 * <p>
	 * Returns {@code 400 Bad Request} with a localized top-level message and an {@code errors} map keyed by field name.
	 * </p>
	 *
	 * @param ex validation exception containing field errors
	 * @return response entity containing validation details
	 */
	@ExceptionHandler(MethodArgumentNotValidException.class)
	public ResponseEntity<Map<String, Object>> handleValidationExceptions(MethodArgumentNotValidException ex) {
		Locale locale = LocaleContextHolder.getLocale();
		Map<String, String> errors = ex.getBindingResult()
				.getFieldErrors()
				.stream()
				.collect(Collectors.toMap(
						FieldError::getField,
						error -> error.getDefaultMessage() != null ? error.getDefaultMessage() : messageSource.getMessage("error.validation", null, "Validation error", locale)
				));

		Map<String, Object> body = new HashMap<>();
		body.put(RESPONSE_STATUS, 400);
		body.put(RESPONSE_MESSAGE, messageSource.getMessage("error.validation", null, "Validation error", locale));
		body.put("errors", errors);
		body.put(RESPONSE_TIMESTAMP, Instant.now());
		
		return ResponseEntity.badRequest().body(body);
	}

	@ExceptionHandler(DataIntegrityViolationException.class)
	public ResponseEntity<Map<String, Object>> handleDataIntegrityViolation(DataIntegrityViolationException ex) {
		return conflictResponse(resolveConstraintName(ex), ex);
	}

	@ExceptionHandler({ ConstraintViolationException.class, TransactionSystemException.class })
	public ResponseEntity<Map<String, Object>> handleConstraintRelatedExceptions(Exception ex) {
		return conflictResponse(resolveConstraintName(ex), ex);
	}

	/**
	 * Handles {@link EntityNotFoundException} by returning {@code 404 Not Found}.
	 * <p>
	 * Uses the exception message when present; otherwise falls back to a localized generic not-found message.
	 * </p>
	 *
	 * @param ex not-found exception
	 * @return response entity with status 404 and a consistent error body
	 */
	@ExceptionHandler(EntityNotFoundException.class)
	public ResponseEntity<Map<String, Object>> handleEntityNotFoundException(EntityNotFoundException ex) {
		logger.warn("Entity not found: {}", ex.getMessage());
		Locale locale = LocaleContextHolder.getLocale();
		Map<String, Object> body = new HashMap<>();
		body.put(RESPONSE_STATUS, 404);
		// Use the exception message if it exists and is not empty, otherwise use generic message
		String message = ex.getMessage();
		if (message == null || message.trim().isEmpty()) {
			message = messageSource.getMessage("error.not.found", null, "Resource not found", locale);
		}
		body.put(RESPONSE_MESSAGE, message);
		body.put(RESPONSE_TIMESTAMP, Instant.now());
		return ResponseEntity.status(404).body(body);
	}

	@ExceptionHandler(Exception.class)
	public ResponseEntity<Map<String, Object>> handleGenericException(Exception ex) {
		logger.error("Unexpected error occurred: {}", ex.getMessage(), ex);
		Locale locale = LocaleContextHolder.getLocale();
		Map<String, Object> body = new HashMap<>();
		body.put(RESPONSE_STATUS, 500);
		body.put(RESPONSE_MESSAGE, messageSource.getMessage("error.internal", null, "An internal server error occurred.", locale));
		body.put(RESPONSE_TIMESTAMP, Instant.now());
		return ResponseEntity.status(500).body(body);
	}

	/**
	 * Builds a {@code 409 Conflict} response for constraint/uniqueness violations.
	 * <p>
	 * Maps a database constraint name (when available) to a message key and resolves it via {@link MessageSource}
	 * using the current request locale.
	 * </p>
	 *
	 * @param constraintName resolved constraint identifier (nullable)
	 * @param ex             originating exception
	 * @return response entity with status 409 and a localized message
	 */
	private ResponseEntity<Map<String, Object>> conflictResponse(String constraintName, Exception ex) {
		Locale locale = LocaleContextHolder.getLocale();
		String messageKey = mapConstraintToMessageKey(constraintName, ex);
		// Use a safe default literal as fallback to avoid NoSuchMessageException breaking the handler
		String localized = messageSource.getMessage(messageKey, null, "Bad request. Please check your input.", locale);
		logger.warn("Constraint violation [{}]: {}", constraintName, ex.getMessage());
		Map<String, Object> body = new HashMap<>();
		body.put(RESPONSE_STATUS, 409);
		body.put(RESPONSE_MESSAGE, localized);
		body.put(RESPONSE_TIMESTAMP, Instant.now());
		return ResponseEntity.status(409).body(body);
	}

	/**
	 * Attempts to resolve a database constraint name from a constraint-related exception chain.
	 * <p>
	 * Tries (in order): explicit name from {@link ConstraintViolationException}, common PostgreSQL message formats,
	 * then a last-resort inference based on known column identifiers present in the error message.
	 * </p>
	 *
	 * @param ex exception thrown during persistence/transaction commit
	 * @return resolved constraint name, or {@code null} if it cannot be determined
	 */
	private String resolveConstraintName(Exception ex) {
		Throwable t = ex;
		String fullMessage = null;
		while (t != null) {
			if (t instanceof ConstraintViolationException cve && cve.getConstraintName() != null) {
				logger.debug("Found constraint name from ConstraintViolationException: {}", cve.getConstraintName());
				return cve.getConstraintName();
			}
			String msg = t.getMessage();
			if (msg != null) {
				if (fullMessage == null) {
					fullMessage = msg;
				}
				// Try PostgreSQL format: "constraint \"constraint_name\""
				int idx = msg.indexOf(CONSTRAINT_QUOTE_PREFIX);
				if (idx >= 0) {
					int start = idx + CONSTRAINT_QUOTE_PREFIX.length();
					int end = msg.indexOf('"', start);
					if (end > start) {
						String constraintName = msg.substring(start, end);
						logger.debug("Extracted constraint name from PostgreSQL format: {}", constraintName);
						return constraintName;
					}
				}
				// Try format: "constraint constraint_name"
				idx = msg.indexOf("constraint ");
				if (idx >= 0 && msg.indexOf(CONSTRAINT_QUOTE_PREFIX) == -1) {
					int start = idx + "constraint ".length();
					int end = msg.indexOf(' ', start);
					if (end < 0) end = msg.length();
					if (end > start) {
						String constraintName = msg.substring(start, end).trim();
						// Remove trailing punctuation
						constraintName = constraintName.replaceAll("\\W", "");
						if (!constraintName.isEmpty()) {
							logger.debug("Extracted constraint name from generic format: {}", constraintName);
							return constraintName;
						}
					}
				}
				// Try format: "duplicate key value violates unique constraint \"constraint_name\""
				idx = msg.indexOf("unique constraint \"");
				if (idx >= 0) {
					int start = idx + "unique constraint \"".length();
					int end = msg.indexOf('"', start);
					if (end > start) {
						String constraintName = msg.substring(start, end);
						logger.debug("Extracted constraint name from unique constraint format: {}", constraintName);
						return constraintName;
					}
				}
			}
			t = t.getCause();
		}
		
		// Fallback: Try to infer constraint from exception message content
		if (fullMessage != null) {
			String lowerMsg = fullMessage.toLowerCase(Locale.ROOT);
			if (lowerMsg.contains("restaurant_group_code")) {
				logger.debug("Inferred constraint from message content: restaurant_group_code");
				return "uk_restaurant_group_code_case_insensitive";
			}
			if (lowerMsg.contains("restaurant_code")) {
				logger.debug("Inferred constraint from message content: restaurant_code");
				return "uk_restaurant_code_case_insensitive";
			}
		}
		
		logger.warn("Could not resolve constraint name from exception. Full message: {}", fullMessage);
		return null;
	}

	/**
	 * Maps a database constraint name (or failure message) to a localized message key.
	 * <p>
	 * When {@code constraintName} is {@code null}, this method attempts to infer a known constraint from the exception
	 * message; otherwise it matches known constraint identifiers and applies pattern-based fallbacks.
	 * </p>
	 *
	 * @param constraintName database constraint name (nullable)
	 * @param ex             originating exception (used for inference when constraint name is unavailable)
	 * @return message key to be resolved via {@link MessageSource}
	 */
	private String mapConstraintToMessageKey(String constraintName, Exception ex) {
		if (constraintName == null) {
			// Try to infer from exception message as last resort
			String msg = ex.getMessage();
			if (msg != null) {
				String lowerMsg = msg.toLowerCase(Locale.ROOT);
				if (lowerMsg.contains("restaurant_group_code")) {
					logger.debug("Inferred restaurant_group_code constraint from exception message");
					return MSG_KEY_RESTAURANT_GROUP_CODE_EXISTS;
				}
				if (lowerMsg.contains("restaurant_code")) {
					logger.debug("Inferred restaurant_code constraint from exception message");
					return "restaurant.create.error.code.exists";
				}
			}
			return "error.badrequest";
		}
		if ("uniq_menu_translation_name_lang".equalsIgnoreCase(constraintName)) {
			return "menus.error.duplicate.translation";
		}
		if ("uk_restaurant_group_translation_name_ci_global".equalsIgnoreCase(constraintName)) {
			return "restaurantgroup.create.error.name.exists";
		}
		if ("uk_restaurant_translation_name_ci_global".equalsIgnoreCase(constraintName)) {
			return "restaurant.create.error.name.exists";
		}
		if ("uk_restaurant_group_code_case_insensitive".equalsIgnoreCase(constraintName)) {
			return MSG_KEY_RESTAURANT_GROUP_CODE_EXISTS;
		}
		if ("uk_restaurant_code_case_insensitive".equalsIgnoreCase(constraintName)) {
			return "restaurant.create.error.code.exists";
		}
		// Fallbacks for environment-specific or auto-generated names
		String lower = constraintName.toLowerCase(Locale.ROOT);
		// More specific patterns to avoid false positives
		if (lower.matches(".*\\b(uk_|unique_|idx_|key_).*restaurant_code.*") || 
		    lower.matches(".*restaurant_code.*\\b(uk_|unique_|idx_|key_).*")) {
			return "restaurant.update.error.code.exists";
		}
		if (lower.matches(".*\\b(uk_|unique_|idx_|key_).*restaurant_group_code.*") || 
		    lower.matches(".*restaurant_group_code.*\\b(uk_|unique_|idx_|key_).*")) {
			return MSG_KEY_RESTAURANT_GROUP_CODE_EXISTS;
		}
		return "error.badrequest";
	}


	/**
	 * Handles {@link MaxUploadSizeExceededException} by returning {@code 413 Payload Too Large}.
	 * <p>
	 * Uses {@code bulk.upload.max-file-size} (bytes) to format a human readable size and injects it into the localized
	 * message.
	 * </p>
	 *
	 * @param ex thrown when multipart upload exceeds configured size limits
	 * @return response entity with status 413 and a localized message
	 */
	@ExceptionHandler(MaxUploadSizeExceededException.class)
	public ResponseEntity<Map<String, Object>> handleMaxUploadSizeExceeded(MaxUploadSizeExceededException ex) {
		Locale locale = LocaleContextHolder.getLocale();

		// Format the max file size nicely, e.g., "10 MB"
		String maxSizeFormatted = formatFileSize(maxFileSizeBytes);

		// Pass the size as parameter to the localized message
		String message = messageSource.getMessage(
			"error.file.size.exceeded",
			new Object[]{maxSizeFormatted},
			"File size exceeds the configured maximum limit of {0}.",
			locale
		);

		Map<String, Object> body = new HashMap<>();
		body.put(RESPONSE_STATUS, HttpStatus.PAYLOAD_TOO_LARGE.value());
		body.put(RESPONSE_MESSAGE, message);
		body.put(RESPONSE_TIMESTAMP, Instant.now());

		return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE).body(body);
	}

	private String formatFileSize(long bytes) {
		if (bytes < 1024) return bytes + " B";
		int exp = (int) (Math.log(bytes) / Math.log(1024));
		char pre = "KMGTPE".charAt(exp - 1);
		return String.format("%.1f %sB", bytes / Math.pow(1024, exp), pre);
	}


}

