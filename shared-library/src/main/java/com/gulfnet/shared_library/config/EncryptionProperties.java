package com.gulfnet.shared_library.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "app.encryption")
@Data
public class EncryptionProperties {

    private String rsaPublicKey; // RSA public key (Base64 or PEM format)
    private String rsaPrivateKey; // RSA private key (Base64 or PEM format)

}
