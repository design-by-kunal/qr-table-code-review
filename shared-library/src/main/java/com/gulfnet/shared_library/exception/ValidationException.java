package com.gulfnet.shared_library.exception;

import com.gulfnet.shared_library.model.response.dto.ErrorDto;
import java.util.List;

public class ValidationException extends RuntimeException {
    
    private final List<ErrorDto> errors;
    
    public ValidationException(List<ErrorDto> errors) {
        super("Validation failed");
        this.errors = errors;
    }
    
    public ValidationException(String message) {
        super(message);
        this.errors = null;
    }
    
    public List<ErrorDto> getErrors() {
        return errors;
    }
} 