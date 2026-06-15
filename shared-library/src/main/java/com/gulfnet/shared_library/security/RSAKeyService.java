package com.gulfnet.shared_library.security;

import com.gulfnet.shared_library.config.EncryptionProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

@Component
@ConditionalOnProperty(prefix = "app.encryption", name = "rsa-public-key")
@Slf4j
public class RSAKeyService {

    private static final String ALGORITHM = "RSA";
    private static final String PUBLIC_KEY_HEADER = "-----BEGIN PUBLIC KEY-----";
    private static final String PUBLIC_KEY_FOOTER = "-----END PUBLIC KEY-----";
    private static final String PRIVATE_KEY_HEADER = "-----BEGIN PRIVATE KEY-----";
    private static final String PRIVATE_KEY_FOOTER = "-----END PRIVATE KEY-----";

    private PublicKey publicKey;
    private PrivateKey privateKey;

    public RSAKeyService(EncryptionProperties encryptionProperties) {
        initializeKeys(encryptionProperties);
    }

    /**
     * Loads RSA keys from {@link EncryptionProperties}. When only the public key is configured,
     * the private key remains unset (e.g. restaurant-management). Services that decrypt payloads
     * must validate the private key at startup.
     */
    private void initializeKeys(EncryptionProperties encryptionProperties) {
        try {
            String publicKeyStr = encryptionProperties.getRsaPublicKey();
            String privateKeyStr = encryptionProperties.getRsaPrivateKey();

            boolean hasPublicKey = publicKeyStr != null && !publicKeyStr.trim().isEmpty();
            boolean hasPrivateKey = privateKeyStr != null && !privateKeyStr.trim().isEmpty();

            if (hasPublicKey && hasPrivateKey) {
                loadKeysFromString(publicKeyStr, privateKeyStr);
                log.info("RSA keys successfully loaded from configuration");
            } else if (hasPublicKey) {
                loadPublicKeyFromString(publicKeyStr);
                log.info("RSA public key successfully loaded from configuration");
            } else if (hasPrivateKey) {
                throw new IllegalStateException("RSA public key is missing. Set APP_ENCRYPTION_RSA_PUBLIC_KEY.");
            } else {
                throw new IllegalStateException(
                        "RSA keys are not configured. Set APP_ENCRYPTION_RSA_PUBLIC_KEY.");
            }
        } catch (IllegalStateException e) {
            throw e;
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to initialize RSA keys", e);
            throw new RuntimeException("Failed to initialize RSA keys: " + e.getMessage(), e);
        }
    }

    private void loadKeysFromString(String publicKeyStr, String privateKeyStr) throws Exception {
        KeyFactory keyFactory = KeyFactory.getInstance(ALGORITHM);

        byte[] publicKeyBytes = extractKeyBytes(publicKeyStr, PUBLIC_KEY_HEADER, PUBLIC_KEY_FOOTER);
        X509EncodedKeySpec publicKeySpec = new X509EncodedKeySpec(publicKeyBytes);
        this.publicKey = keyFactory.generatePublic(publicKeySpec);

        byte[] privateKeyBytes = extractKeyBytes(privateKeyStr, PRIVATE_KEY_HEADER, PRIVATE_KEY_FOOTER);
        PKCS8EncodedKeySpec privateKeySpec = new PKCS8EncodedKeySpec(privateKeyBytes);
        this.privateKey = keyFactory.generatePrivate(privateKeySpec);
    }

    private void loadPublicKeyFromString(String publicKeyStr) throws Exception {
        KeyFactory keyFactory = KeyFactory.getInstance(ALGORITHM);
        byte[] publicKeyBytes = extractKeyBytes(publicKeyStr, PUBLIC_KEY_HEADER, PUBLIC_KEY_FOOTER);
        X509EncodedKeySpec publicKeySpec = new X509EncodedKeySpec(publicKeyBytes);
        this.publicKey = keyFactory.generatePublic(publicKeySpec);
        this.privateKey = null;
    }

    private byte[] extractKeyBytes(String keyString, String header, String footer) {
        String cleaned = keyString.trim();

        if (cleaned.contains(header)) {
            cleaned = cleaned.replace(header, "")
                    .replace(footer, "")
                    .replaceAll("\\s", "");
        }

        return Base64.getDecoder().decode(cleaned);
    }

    public String getPublicKeyAsString() {
        return Base64.getEncoder().encodeToString(publicKey.getEncoded());
    }

    public String getPrivateKeyAsString() {
        if (privateKey == null) {
            throw new IllegalStateException("RSA private key is not configured");
        }
        return Base64.getEncoder().encodeToString(privateKey.getEncoded());
    }

    public PrivateKey getPrivateKey() {
        if (privateKey == null) {
            throw new IllegalStateException("RSA private key is not configured");
        }
        return privateKey;
    }
}
