package com.gulfnet.shared_library.security;

import com.gulfnet.shared_library.exception.InvalidEncryptedPayloadException;
import org.junit.jupiter.api.Test;

import javax.crypto.Cipher;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.spec.AlgorithmParameterSpec;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RSAUtilTest {

    @Test
    void decrypt_roundTrip_withOaepSha256Mgf1Sha256() throws Exception {
        KeyPair keyPair = generateKeyPair();
        String plain = "{\"email\":\"user@example.com\",\"password\":\"secret\"}";
        AlgorithmParameterSpec oaepParams = RSAUtil.OAEP_SHA256_MGF1_SHA256;

        Cipher cipher = Cipher.getInstance("RSA/ECB/OAEPWithSHA-256AndMGF1Padding");
        cipher.init(Cipher.ENCRYPT_MODE, keyPair.getPublic(), oaepParams);
        String encrypted = Base64.getEncoder().encodeToString(
                cipher.doFinal(plain.getBytes(StandardCharsets.UTF_8)));

        String decrypted = RSAUtil.decrypt(encrypted, keyPair.getPrivate());
        assertEquals(plain, decrypted);
    }

    @Test
    void decrypt_rejectsLegacyOaepSha256Mgf1Sha1Ciphertext() throws Exception {
        KeyPair keyPair = generateKeyPair();
        String plain = "{\"email\":\"user@example.com\",\"password\":\"secret\"}";

        // Legacy Java default: OAEP SHA-256 + MGF1 SHA-1 (no explicit OAEPParameterSpec).
        Cipher legacyCipher = Cipher.getInstance("RSA/ECB/OAEPWithSHA-256AndMGF1Padding");
        legacyCipher.init(Cipher.ENCRYPT_MODE, keyPair.getPublic());
        String legacyEncrypted = Base64.getEncoder().encodeToString(
                legacyCipher.doFinal(plain.getBytes(StandardCharsets.UTF_8)));

        assertThrows(
                InvalidEncryptedPayloadException.class,
                () -> RSAUtil.decrypt(legacyEncrypted, keyPair.getPrivate()));
    }

    @Test
    void decrypt_throwsInvalidEncryptedPayloadException_onCorruptData() throws Exception {
        KeyPair keyPair = generateKeyPair();

        assertThrows(
                InvalidEncryptedPayloadException.class,
                () -> RSAUtil.decrypt("not-valid-base64-ciphertext", keyPair.getPrivate()));
    }

    private static KeyPair generateKeyPair() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        return generator.generateKeyPair();
    }
}
