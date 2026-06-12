package com.gulfnet.shared_library.security;

import com.gulfnet.shared_library.config.EncryptionProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
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
    private static final int KEY_SIZE = 2048;
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
     * Loads RSA public and private keys from {@link EncryptionProperties} when both PEM/Base64
     * strings are configured; otherwise generates an ephemeral {@value #KEY_SIZE}-bit pair for local
     * development and logs a warning. Called once from the constructor.
     *
     * @param encryptionProperties source for {@code rsa-public-key} and {@code rsa-private-key}
     * @throws RuntimeException if configured keys are present but invalid or cannot be parsed
     */
    private void initializeKeys(EncryptionProperties encryptionProperties) {
        try {
            String publicKeyStr = encryptionProperties.getRsaPublicKey();
            String privateKeyStr = encryptionProperties.getRsaPrivateKey();

            // Check if keys are provided
            boolean hasPublicKey = publicKeyStr != null && !publicKeyStr.trim().isEmpty();
            boolean hasPrivateKey = privateKeyStr != null && !privateKeyStr.trim().isEmpty();

            if (hasPublicKey && hasPrivateKey) {
                // Load keys from environment variables
                try {
                    loadKeysFromString(publicKeyStr, privateKeyStr);
                    log.info("RSA keys successfully loaded from environment variables");
                } catch (Exception e) {
                    log.error("Failed to load RSA keys from environment variables. Please check if the keys are valid Base64 or PEM format. Error: {}", e.getMessage());
                    throw new RuntimeException("Failed to load RSA keys from environment variables: " + e.getMessage(), e);
                }
            } else {
                // Generate new keys (for development/testing only)
                log.warn("RSA keys not found or empty in environment variables (APP_ENCRYPTION_RSA_PUBLIC_KEY and APP_ENCRYPTION_RSA_PRIVATE_KEY). " +
                        "Generating new keys. This should only happen in development. For production, ensure these environment variables are set.");
                generateKeys();
                logPublicKeyForEnvironment();
            }
        } catch (RuntimeException e) {
            // Re-throw runtime exceptions (including our own)
            throw e;
        } catch (Exception e) {
            log.error("Failed to initialize RSA keys", e);
            throw new RuntimeException("Failed to initialize RSA keys: " + e.getMessage(), e);
        }
    }

    /**
     * Parses configured key material into {@link java.security.PublicKey} and {@link java.security.PrivateKey}
     * using {@value #ALGORITHM}. Each string may be raw Base64 or PEM with the standard BEGIN/END lines;
     * decoding is delegated to {@link #extractKeyBytes(String, String, String)}.
     *
     * @param publicKeyStr  X.509 SubjectPublicKeyInfo (PEM or Base64)
     * @param privateKeyStr PKCS#8 private key (PEM or Base64)
     * @throws Exception if decoding or {@link java.security.KeyFactory} generation fails
     */
    private void loadKeysFromString(String publicKeyStr, String privateKeyStr) throws Exception {
        KeyFactory keyFactory = KeyFactory.getInstance(ALGORITHM);

        // Load public key
        byte[] publicKeyBytes = extractKeyBytes(publicKeyStr, PUBLIC_KEY_HEADER, PUBLIC_KEY_FOOTER);
        X509EncodedKeySpec publicKeySpec = new X509EncodedKeySpec(publicKeyBytes);
        this.publicKey = keyFactory.generatePublic(publicKeySpec);

        // Load private key
        byte[] privateKeyBytes = extractKeyBytes(privateKeyStr, PRIVATE_KEY_HEADER, PRIVATE_KEY_FOOTER);
        PKCS8EncodedKeySpec privateKeySpec = new PKCS8EncodedKeySpec(privateKeyBytes);
        this.privateKey = keyFactory.generatePrivate(privateKeySpec);
    }

    /**
     * Normalizes a key string for Base64 decoding: trims whitespace, and when {@code keyString}
     * contains {@code header}, removes the PEM header/footer lines and all remaining whitespace before
     * {@link Base64#getDecoder() decoding}. If no PEM markers are present, the trimmed string is decoded as-is.
     *
     * @param keyString PEM block or plain Base64
     * @param header    PEM begin marker (e.g. {@code -----BEGIN PUBLIC KEY-----})
     * @param footer    PEM end marker matching {@code header}
     * @return decoded DER bytes for {@link java.security.spec.X509EncodedKeySpec} or {@link java.security.spec.PKCS8EncodedKeySpec}
     */
    private byte[] extractKeyBytes(String keyString, String header, String footer) {
        String cleaned = keyString.trim();
        
        // Remove PEM headers/footers if present
        if (cleaned.contains(header)) {
            cleaned = cleaned.replace(header, "")
                            .replace(footer, "")
                            .replaceAll("\\s", "");
        }
        
        // Decode Base64
        return Base64.getDecoder().decode(cleaned);
    }

    private void generateKeys() throws Exception {
        KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance(ALGORITHM);
        keyPairGenerator.initialize(KEY_SIZE);
        KeyPair keyPair = keyPairGenerator.generateKeyPair();

        this.publicKey = keyPair.getPublic();
        this.privateKey = keyPair.getPrivate();
    }

    private void logPublicKeyForEnvironment() {
        // Avoid logging sensitive key material (especially private keys) to application logs.
        log.debug("Generated RSA keypair for development. Set APP_ENCRYPTION_RSA_PUBLIC_KEY and APP_ENCRYPTION_RSA_PRIVATE_KEY in env for production.");
    }

    public String getPublicKeyAsString() {
        return Base64.getEncoder().encodeToString(publicKey.getEncoded());
    }

    public String getPrivateKeyAsString() {
        return Base64.getEncoder().encodeToString(privateKey.getEncoded());
    }

    public PrivateKey getPrivateKey() {
        return privateKey;
    }
}
