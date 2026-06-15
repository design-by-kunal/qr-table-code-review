package com.gulfnet.shared_library.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gulfnet.shared_library.exception.InvalidEncryptedPayloadException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Component;

import java.util.function.Function;

@Component
@ConditionalOnBean(RSAKeyService.class)
@RequiredArgsConstructor
@Slf4j
public class EncryptedPayloadDecoder {

    private final RSAKeyService rsaKeyService;
    private final ObjectMapper objectMapper;

    /**
     * Decrypts and deserializes an RSA-encrypted JSON payload when present; otherwise returns the original request.
     */
    public <T> T decodeIfPresent(T request, Function<T, String> payloadGetter, Class<T> type) {
        String payload = payloadGetter.apply(request);
        if (payload == null || payload.isBlank()) {
            return request;
        }

        log.debug("Decrypting RSA-encrypted payload for {}", type.getSimpleName());
        try {
            String decryptedJson = RSAUtil.decrypt(payload, rsaKeyService.getPrivateKey());
            T decrypted = objectMapper.readValue(decryptedJson, type);
            log.debug("Successfully decrypted payload for {}", type.getSimpleName());
            return decrypted;
        } catch (InvalidEncryptedPayloadException e) {
            throw e;
        } catch (Exception e) {
            log.warn("Failed to decrypt or parse encrypted payload for {}", type.getSimpleName(), e);
            throw new InvalidEncryptedPayloadException(e);
        }
    }
}
