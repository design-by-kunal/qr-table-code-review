package com.gulfnet.shared_library.security;

import javax.crypto.Cipher;
import java.security.PrivateKey;
import java.util.Base64;

public class RSAUtil {

    private static final String RSA_ALGORITHM = "RSA/ECB/PKCS1Padding";

    /**
     * Decrypts RSA-encrypted data using the private key.
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
        } catch (javax.crypto.BadPaddingException e) {
            throw new RuntimeException("RSA decryption failed: Invalid key or corrupted data", e);
        } catch (Exception e) {
            throw new RuntimeException("RSA decryption failed: " + e.getMessage(), e);
        }
    }
}
