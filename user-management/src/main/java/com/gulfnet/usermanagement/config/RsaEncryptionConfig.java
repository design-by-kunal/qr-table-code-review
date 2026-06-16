package com.gulfnet.usermanagement.config;

import com.gulfnet.shared_library.security.RSAKeyService;
import org.springframework.context.annotation.Configuration;

/**
 * Ensures user-management fails at startup when the RSA private key is not configured.
 */
@Configuration
public class RsaEncryptionConfig {

    public RsaEncryptionConfig(RSAKeyService rsaKeyService) {
        rsaKeyService.getPrivateKey();
    }
}
