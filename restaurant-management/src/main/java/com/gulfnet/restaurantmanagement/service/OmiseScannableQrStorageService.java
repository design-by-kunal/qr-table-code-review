package com.gulfnet.restaurantmanagement.service;

import org.springframework.http.MediaType;

import java.util.Optional;
import java.util.UUID;

/**
 * Downloads Omise offline QR images (PayNow, PromptPay) from {@code download_uri},
 * stores them locally until payment completes.
 * <p>
 * PromptPay (SVG) returns a short HTTP URL — SVG as base64 is too large for browsers/APIs.
 * PayNow (PNG) returns a {@code data:image/png;base64,...} data URL.
 */
public interface OmiseScannableQrStorageService {

    record CachedQrImage(byte[] bytes, MediaType mediaType) {}

    /**
     * @param omisePaymentType {@code paynow} or {@code promptpay}
     * @return short URL for SVG, or PNG data URL for raster QR
     */
    String cacheQrImageForPaymentResponse(
            UUID restaurantId,
            UUID transactionId,
            String omiseDownloadUri,
            String omisePaymentType);

    Optional<CachedQrImage> readCachedQr(UUID transactionId);

    void deleteCachedQr(UUID transactionId);
}
