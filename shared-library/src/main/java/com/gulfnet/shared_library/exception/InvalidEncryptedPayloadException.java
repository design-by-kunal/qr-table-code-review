package com.gulfnet.shared_library.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.BAD_REQUEST)
public class InvalidEncryptedPayloadException extends BadRequestException {

    public static final String CLIENT_MESSAGE = "Invalid encrypted payload";

    public InvalidEncryptedPayloadException(Throwable cause) {
        super(CLIENT_MESSAGE);
        initCause(cause);
    }
}
