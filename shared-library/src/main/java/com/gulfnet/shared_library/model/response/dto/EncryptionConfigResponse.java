package com.gulfnet.shared_library.model.response.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EncryptionConfigResponse {
    private String publicKey;
    private String publicKeyPEM;
    private String algorithm;
    private Integer keySize;
}
