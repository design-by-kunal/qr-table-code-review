package com.gulfnet.integrationmanagement.exception;


import com.gulfnet.shared_library.model.response.dto.ErrorDto;
import lombok.Getter;

import java.util.List;

@Getter
@SuppressWarnings("java:S1068") // Fields are used via Lombok-generated getters
public class ValidationException extends RuntimeException {

    private final String errorCode;
    private final String errorMessage;
    private final List<ErrorDto> errorMessages;

    public ValidationException(String errorCode, String errorMessage) {
        this.errorCode = errorCode;
        this.errorMessage = errorMessage;
        this.errorMessages = null;
    }

    public ValidationException(List<ErrorDto> errorMessages) {
        this.errorCode = null;
        this.errorMessage = null;
        this.errorMessages = errorMessages;
    }

}
