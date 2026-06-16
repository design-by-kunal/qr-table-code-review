package com.gulfnet.shared_library.security;

import com.gulfnet.shared_library.exception.InvalidEncryptedPayloadException;

import javax.crypto.Cipher;
import javax.crypto.spec.OAEPParameterSpec;
import javax.crypto.spec.PSource;
import java.security.PrivateKey;
import java.security.spec.AlgorithmParameterSpec;
import java.security.spec.MGF1ParameterSpec;
import java.util.Base64;

public class RSAUtil {

    private static final String RSA_ALGORITHM = "RSA/ECB/OAEPWithSHA-256AndMGF1Padding";

    /**
     * OAEP SHA-256 with MGF1 SHA-256 — matches Web Crypto {@code RSA-OAEP} + {@code SHA-256}.
     * Package-visible for encrypt-side tests in the same module.
     */
    static final AlgorithmParameterSpec OAEP_SHA256_MGF1_SHA256 = new OAEPParameterSpec(
            "SHA-256",
            "MGF1",
            MGF1ParameterSpec.SHA256,
            PSource.PSpecified.DEFAULT);

    /**
     * Decrypts RSA-OAEP (SHA-256, MGF1 SHA-256) encrypted data using the private key.
     *
     * @param encrypted Base64-encoded encrypted string
     * @param privateKey RSA private key
     * @return Decrypted plain text
     */
    public static String decrypt(String encrypted, PrivateKey privateKey) {
        try {
            Cipher cipher = Cipher.getInstance(RSA_ALGORITHM);
            cipher.init(Cipher.DECRYPT_MODE, privateKey, OAEP_SHA256_MGF1_SHA256);

            byte[] decoded = Base64.getDecoder().decode(encrypted);
            byte[] decrypted = cipher.doFinal(decoded);

            return new String(decrypted);
        } catch (Exception e) {
            throw new InvalidEncryptedPayloadException(e);
        }
    }
}
