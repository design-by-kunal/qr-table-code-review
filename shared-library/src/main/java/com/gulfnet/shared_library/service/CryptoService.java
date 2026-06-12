package com.gulfnet.shared_library.service;

import com.gulfnet.shared_library.exception.CryptoException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * Symmetric AES-256 encryption/decryption service.
 * <p>
 * Uses a 32-byte master key provided via configuration property:
 *   crypto.master-key = Base64-encoded 32-byte key
 * <p>
 * The ciphertext format is: Base64( IV(12 bytes) || GCM(ciphertext+tag) ).
 */
@Slf4j
@Service
public class CryptoService {

    private static final String AES_ALGORITHM = "AES";
    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int GCM_TAG_LENGTH_BITS = 128;
    private static final int IV_LENGTH_BYTES = 12;
    private static final int KEY_LENGTH_BYTES = 32; // 256 bits

    @Value("${crypto.master-key:}")
    private String masterKeyBase64;

    private final SecureRandom secureRandom = new SecureRandom();

    /**
     * Encrypts plain text using AES-256 GCM.
     *
     * @param plainText text to encrypt
     * @return Base64 encoded (IV || ciphertext+tag)
     */
    public String encrypt(String plainText) {
        if (plainText == null) {
            return null;
        }

        try {
            SecretKey key = resolveKey();

            byte[] iv = new byte[IV_LENGTH_BYTES];
            secureRandom.nextBytes(iv);

            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            GCMParameterSpec spec = new GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv);
            cipher.init(Cipher.ENCRYPT_MODE, key, spec);

            byte[] cipherBytes = cipher.doFinal(plainText.getBytes(java.nio.charset.StandardCharsets.UTF_8));

            // prepend IV to ciphertext
            byte[] combined = new byte[IV_LENGTH_BYTES + cipherBytes.length];
            System.arraycopy(iv, 0, combined, 0, IV_LENGTH_BYTES);
            System.arraycopy(cipherBytes, 0, combined, IV_LENGTH_BYTES, cipherBytes.length);

            return Base64.getEncoder().encodeToString(combined);
        } catch (Exception e) {
            log.error("Failed to encrypt using AES-256", e);
            throw new CryptoException("Failed to encrypt value", e);
        }
    }

    /**
     * Decrypts a Base64 encoded (IV || ciphertext+tag) string using AES-256 GCM.
     *
     * @param cipherText Base64 encoded ciphertext
     * @return decrypted plain text
     */
    public String decrypt(String cipherText) {
        if (cipherText == null) {
            return null;
        }

        try {
            SecretKey key = resolveKey();

            byte[] combined = Base64.getDecoder().decode(cipherText);
            if (combined.length <= IV_LENGTH_BYTES) {
                throw new CryptoException("Cipher text too short");
            }

            byte[] iv = new byte[IV_LENGTH_BYTES];
            byte[] cipherBytes = new byte[combined.length - IV_LENGTH_BYTES];

            System.arraycopy(combined, 0, iv, 0, IV_LENGTH_BYTES);
            System.arraycopy(combined, IV_LENGTH_BYTES, cipherBytes, 0, cipherBytes.length);

            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            GCMParameterSpec spec = new GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv);
            cipher.init(Cipher.DECRYPT_MODE, key, spec);

            byte[] plainBytes = cipher.doFinal(cipherBytes);
            return new String(plainBytes, java.nio.charset.StandardCharsets.UTF_8);
        } catch (CryptoException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to decrypt using AES-256", e);
            throw new CryptoException("Failed to decrypt value", e);
        }
    }

    /**
     * Materializes the AES-256 key from {@code crypto.master-key}: Base64-decodes the configured value
     * and wraps it as a {@link SecretKeySpec}. The decoded key must be exactly {@value #KEY_LENGTH_BYTES} bytes.
     *
     * @return secret key for {@value #TRANSFORMATION}
     * @throws CryptoException if the property is missing/blank, not valid Base64, or not 32 bytes after decoding
     */
    private SecretKey resolveKey() {
        if (masterKeyBase64 == null || masterKeyBase64.isBlank()) {
            throw new CryptoException("crypto.master-key is not configured");
        }

        byte[] keyBytes;
        try {
            keyBytes = Base64.getDecoder().decode(masterKeyBase64);
        } catch (IllegalArgumentException e) {
            throw new CryptoException("crypto.master-key must be Base64-encoded", e);
        }

        if (keyBytes.length != KEY_LENGTH_BYTES) {
            throw new CryptoException("crypto.master-key must be 32 bytes (256 bits) after Base64 decoding");
        }

        return new SecretKeySpec(keyBytes, AES_ALGORITHM);
    }
}

