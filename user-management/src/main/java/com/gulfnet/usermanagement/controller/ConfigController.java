package com.gulfnet.usermanagement.controller;

import com.gulfnet.shared_library.security.RSAKeyService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/encryption")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Encryption", description = "Encryption APIs")
public class ConfigController {

    private final RSAKeyService rsaKeyService;

    @GetMapping("/public-key")
    @Operation(summary = "Get RSA public key", 
               description = "Returns the RSA public key for encrypting login credentials. Clients use this to encrypt payload.")
    public ResponseEntity<Map<String, String>> getPublicKey() {
        log.info("Received request for RSA public key");
        return ResponseEntity.ok(Map.of("publicKey", rsaKeyService.getPublicKeyAsString()));
    }
}
