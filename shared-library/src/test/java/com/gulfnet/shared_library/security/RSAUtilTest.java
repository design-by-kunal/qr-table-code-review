package com.gulfnet.shared_library.security;

import com.gulfnet.shared_library.exception.InvalidEncryptedPayloadException;
import org.junit.jupiter.api.Test;

import javax.crypto.Cipher;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RSAUtilTest {

    @Test
    void decrypt_roundTrip_withOaepSha256() throws Exception {
        KeyPair keyPair = generateKeyPair();
        String plain = "{\"email\":\"user@example.com\",\"password\":\"secret\"}";

        Cipher cipher = Cipher.getInstance("RSA/ECB/OAEPWithSHA-256AndMGF1Padding");
        cipher.init(Cipher.ENCRYPT_MODE, keyPair.getPublic());
        String encrypted = Base64.getEncoder().encodeToString(
                cipher.doFinal(plain.getBytes(StandardCharsets.UTF_8)));

        String decrypted = RSAUtil.decrypt(encrypted, keyPair.getPrivate());
        assertEquals(plain, decrypted);
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
