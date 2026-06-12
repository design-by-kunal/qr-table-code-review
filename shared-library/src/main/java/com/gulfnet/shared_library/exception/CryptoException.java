package com.gulfnet.shared_library.exception;

/**
 * Exception thrown when local AES encryption or decryption fails.
 */
public class CryptoException extends RuntimeException {

    public CryptoException(String message) {
        super(message);
    }

    public CryptoException(String message, Throwable cause) {
        super(message, cause);
    }
}

