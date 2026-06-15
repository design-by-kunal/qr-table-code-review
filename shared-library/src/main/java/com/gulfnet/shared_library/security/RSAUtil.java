package com.gulfnet.shared_library.security;

import com.gulfnet.shared_library.exception.InvalidEncryptedPayloadException;

import javax.crypto.Cipher;
import java.security.PrivateKey;
import java.util.Base64;

public class RSAUtil {

    private static final String RSA_ALGORITHM = "RSA/ECB/OAEPWithSHA-256AndMGF1Padding";

    /**
     * Decrypts RSA-OAEP (SHA-256) encrypted data using the private key.
     *
     * @param encrypted Base64-encoded encrypted string
     * @param privateKey RSA private key
     * @return Decrypted plain text
     */
    public static String decrypt(String encrypted, PrivateKey privateKey) {
        try {
            Cipher cipher = Cipher.getInstance(RSA_ALGORITHM);
            cipher.init(Cipher.DECRYPT_MODE, privateKey);

            byte[] decoded = Base64.getDecoder().decode(encrypted);
            byte[] decrypted = cipher.doFinal(decoded);

            return new String(decrypted);
        } catch (Exception e) {
            throw new InvalidEncryptedPayloadException(e);
        }
    }
}
