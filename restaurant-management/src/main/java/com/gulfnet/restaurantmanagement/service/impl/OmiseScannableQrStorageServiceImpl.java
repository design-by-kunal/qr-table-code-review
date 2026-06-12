package com.gulfnet.restaurantmanagement.service.impl;

import com.gulfnet.restaurantmanagement.service.OmiseScannableQrStorageService;
import com.gulfnet.restaurantmanagement.service.PaymentCredentialService;
import com.gulfnet.shared_library.exception.OmiseApiException;
import com.gulfnet.shared_library.model.dto.PaymentCredentials;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Service
public class OmiseScannableQrStorageServiceImpl implements OmiseScannableQrStorageService {

    private static final Set<String> SUPPORTED_TYPES = Set.of("paynow", "promptpay");
    private static final String EXT_PNG = "png";
    private static final String EXT_SVG = "svg";
    private static final String QR_IMAGE_PATH_TEMPLATE = "/api/v1/transactions/%s/omise-qr";
    private static final String DATA_URL_PNG_PREFIX = "data:image/png;base64,";
    private static final String HEADER_AUTHORIZATION = "Authorization";
    private static final String AUTH_BASIC_PREFIX = "Basic ";
    private static final MediaType MEDIA_SVG = MediaType.parseMediaType("image/svg+xml");

    private final RestTemplate restTemplate;
    private final PaymentCredentialService paymentCredentialService;
    private final Path storageDirectory;
    private final String apiBaseUrl;

    public OmiseScannableQrStorageServiceImpl(
            @Qualifier("omiseRestTemplate") RestTemplate restTemplate,
            PaymentCredentialService paymentCredentialService,
            @Value("${payment.paynow.qr.storage-dir:./data/paynow-qr}") String storageDir,
            @Value("${payment.omise.qr.api-base-url:}") String apiBaseUrl) {
        this.restTemplate = restTemplate;
        this.paymentCredentialService = paymentCredentialService;
        this.apiBaseUrl = apiBaseUrl != null ? apiBaseUrl.trim() : "";
        this.storageDirectory = Path.of(storageDir).toAbsolutePath().normalize();
        try {
            Files.createDirectories(this.storageDirectory);
            log.info("Omise scannable QR storage directory: {}", this.storageDirectory);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to create Omise QR storage directory: " + this.storageDirectory, e);
        }
    }

    @Override
    public String cacheQrImageForPaymentResponse(
            UUID restaurantId,
            UUID transactionId,
            String omiseDownloadUri,
            String omisePaymentType) {
        CachedQrImage cached = downloadAndStore(restaurantId, transactionId, omiseDownloadUri, omisePaymentType);
        QrFileFormat format = QrFileFormat.fromMediaType(cached.mediaType());
        if (format == QrFileFormat.SVG) {
            String url = buildQrImageUrl(transactionId);
            log.info("PromptPay/SVG QR for transaction {} — returning short URL ({} bytes SVG)", transactionId, cached.bytes().length);
            return url;
        }
        log.info("PayNow/PNG QR for transaction {} — returning data URL ({} bytes PNG)", transactionId, cached.bytes().length);
        return DATA_URL_PNG_PREFIX + Base64.getEncoder().encodeToString(cached.bytes());
    }

    @Override
    public Optional<CachedQrImage> readCachedQr(UUID transactionId) {
        if (transactionId == null) {
            return Optional.empty();
        }
        for (QrFileFormat format : QrFileFormat.values()) {
            Path filePath = resolveFilePath(transactionId, format.extension());
            if (!Files.isRegularFile(filePath)) {
                continue;
            }
            try {
                byte[] bytes = Files.readAllBytes(filePath);
                QrFileFormat actual = detectFormat(bytes, null);
                return Optional.of(new CachedQrImage(bytes, actual.mediaType()));
            } catch (IOException e) {
                log.warn("Failed to read Omise QR file {}: {}", filePath, e.getMessage());
                return Optional.empty();
            }
        }
        return Optional.empty();
    }

    @Override
    public void deleteCachedQr(UUID transactionId) {
        if (transactionId == null) {
            return;
        }
        for (QrFileFormat format : QrFileFormat.values()) {
            Path filePath = resolveFilePath(transactionId, format.extension());
            try {
                if (Files.deleteIfExists(filePath)) {
                    log.info("Deleted Omise QR file {}", filePath);
                }
            } catch (IOException e) {
                log.warn("Failed to delete Omise QR file {}: {}", filePath, e.getMessage());
            }
        }
    }

    private CachedQrImage downloadAndStore(
            UUID restaurantId,
            UUID transactionId,
            String omiseDownloadUri,
            String omisePaymentType) {
        String paymentType = normalizePaymentType(omisePaymentType);

        if (omiseDownloadUri == null || omiseDownloadUri.isBlank()) {
            throw new OmiseApiException(paymentType + " download URI is missing", null);
        }

        PaymentCredentials credentials = paymentCredentialService.getPaymentCredentials(restaurantId, paymentType);
        HttpHeaders headers = createAuthHeaders(credentials.getSecretKey());
        headers.setAccept(MediaType.parseMediaTypes("image/png,image/svg+xml,image/*,*/*"));
        HttpEntity<Void> entity = new HttpEntity<>(headers);

        ResponseEntity<byte[]> response;
        try {
            response = restTemplate.exchange(omiseDownloadUri, HttpMethod.GET, entity, byte[].class);
        } catch (Exception e) {
            log.error("Failed to download {} QR from Omise for transaction {}", paymentType, transactionId, e);
            throw new OmiseApiException("Failed to download " + paymentType + " QR image: " + e.getMessage(), e);
        }

        byte[] imageBytes = response.getBody();
        if (imageBytes == null || imageBytes.length == 0) {
            throw new OmiseApiException(paymentType + " QR download returned empty body", null);
        }

        QrFileFormat format = detectFormat(imageBytes, response.getHeaders().getContentType());
        deleteCachedQr(transactionId);

        Path filePath = resolveFilePath(transactionId, format.extension());
        try {
            Files.write(filePath, imageBytes);
            log.info("Saved {} QR for transaction {} as {}", paymentType, transactionId, filePath);
        } catch (IOException e) {
            throw new OmiseApiException("Failed to save " + paymentType + " QR image locally: " + e.getMessage(), e);
        }

        return new CachedQrImage(imageBytes, format.mediaType());
    }

    private String buildQrImageUrl(UUID transactionId) {
        String path = String.format(QR_IMAGE_PATH_TEMPLATE, transactionId);
        if (apiBaseUrl.isEmpty()) {
            return path;
        }
        String base = apiBaseUrl.endsWith("/") ? apiBaseUrl.substring(0, apiBaseUrl.length() - 1) : apiBaseUrl;
        return base + path;
    }

    private static QrFileFormat detectFormat(byte[] imageBytes, MediaType responseContentType) {
        if (responseContentType != null) {
            if (MediaType.IMAGE_PNG.includes(responseContentType)) {
                return QrFileFormat.PNG;
            }
            if (MEDIA_SVG.includes(responseContentType) || MediaType.APPLICATION_XML.includes(responseContentType)) {
                return QrFileFormat.SVG;
            }
        }
        if (imageBytes.length >= 4
                && imageBytes[0] == (byte) 0x89
                && imageBytes[1] == 0x50
                && imageBytes[2] == 0x4E
                && imageBytes[3] == 0x47) {
            return QrFileFormat.PNG;
        }
        String prefix = new String(imageBytes, 0, Math.min(imageBytes.length, 256), StandardCharsets.UTF_8).trim();
        if (prefix.startsWith("<svg") || prefix.startsWith("<?xml") || prefix.contains("<svg")) {
            return QrFileFormat.SVG;
        }
        return QrFileFormat.PNG;
    }

    private static String normalizePaymentType(String omisePaymentType) {
        if (omisePaymentType == null || omisePaymentType.isBlank()) {
            throw new OmiseApiException("Omise payment type is required for QR download", null);
        }
        String normalized = omisePaymentType.trim().toLowerCase();
        if (!SUPPORTED_TYPES.contains(normalized)) {
            throw new OmiseApiException("Unsupported Omise scannable QR type: " + omisePaymentType, null);
        }
        return normalized;
    }

    private Path resolveFilePath(UUID transactionId, String extension) {
        return storageDirectory.resolve(transactionId + "." + extension);
    }

    private HttpHeaders createAuthHeaders(String secretKey) {
        HttpHeaders headers = new HttpHeaders();
        String auth = secretKey + ":";
        byte[] encodedAuth = Base64.getEncoder().encode(auth.getBytes(StandardCharsets.UTF_8));
        headers.set(HEADER_AUTHORIZATION, AUTH_BASIC_PREFIX + new String(encodedAuth));
        return headers;
    }

    private enum QrFileFormat {
        PNG(EXT_PNG, MediaType.IMAGE_PNG),
        SVG(EXT_SVG, MEDIA_SVG);

        private final String extension;
        private final MediaType mediaType;

        QrFileFormat(String extension, MediaType mediaType) {
            this.extension = extension;
            this.mediaType = mediaType;
        }

        String extension() {
            return extension;
        }

        MediaType mediaType() {
            return mediaType;
        }

        static QrFileFormat fromMediaType(MediaType mediaType) {
            if (MEDIA_SVG.includes(mediaType)) {
                return SVG;
            }
            return PNG;
        }
    }
}
