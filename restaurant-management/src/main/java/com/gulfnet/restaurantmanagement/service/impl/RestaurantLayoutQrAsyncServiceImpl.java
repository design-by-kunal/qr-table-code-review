package com.gulfnet.restaurantmanagement.service.impl;

import com.gulfnet.restaurantmanagement.config.AppProperties;
import com.gulfnet.restaurantmanagement.service.PrintQrCodeService;
import com.gulfnet.restaurantmanagement.service.RestaurantLayoutQrAsyncService;
import com.gulfnet.shared_library.config.AWSService;
import com.gulfnet.shared_library.entity.Restaurant;
import com.gulfnet.shared_library.entity.RestaurantTable;
import com.gulfnet.shared_library.repository.RestaurantRepository;
import com.gulfnet.shared_library.repository.RestaurantTableRepository;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.qrcode.QRCodeWriter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.awt.image.BufferedImage;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class RestaurantLayoutQrAsyncServiceImpl implements RestaurantLayoutQrAsyncService {

    private final AWSService awsService;
    private final AppProperties appProperties;
    private final PrintQrCodeService printQrCodeService;
    private final RestaurantRepository restaurantRepository;
    private final RestaurantTableRepository restaurantTableRepository;
    private final RestaurantTableQrUrlUpdater restaurantTableQrUrlUpdater;

    static class QrCodeGenerationException extends RuntimeException {
        QrCodeGenerationException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    @Override
    @Async("bulkUploadTaskExecutor")
    public void generateQrCodesForTablesAsync(UUID restaurantId, List<UUID> tableIds) {
        if (tableIds == null || tableIds.isEmpty()) {
            log.warn("generateQrCodesForTablesAsync called with empty or null tableIds for restaurant {}", restaurantId);
            return;
        }
        log.info("Starting async QR code generation for {} tables in restaurant {}. Table IDs: {}", 
                tableIds.size(), restaurantId, tableIds);
        
        // Important: avoid sleeping for "transaction commit" guesses.
        // If the caller needs after-commit semantics, it should schedule this async call in an afterCommit hook.
        generateQrCodesNoTransaction(restaurantId, tableIds);
    }
    
    private void generateQrCodesNoTransaction(UUID restaurantId, List<UUID> tableIds) {
        Restaurant restaurant = restaurantRepository.findById(restaurantId).orElse(null);
        if (restaurant == null) {
            log.error("Restaurant {} not found for async QR generation", restaurantId);
            return;
        }
        int successCount = 0;
        int skippedCount = 0;
        int errorCount = 0;
        
        for (UUID tableId : tableIds) {
            try {
                RestaurantTable table = restaurantTableRepository.findById(tableId).orElse(null);
                boolean shouldSkip = table == null
                        || Boolean.TRUE.equals(table.getIsVirtual())
                        || (table.getQrCodeUrl() != null && !table.getQrCodeUrl().trim().isEmpty());
                if (shouldSkip) {
                    logSkipReason(tableId, table);
                    skippedCount++;
                    continue; // single continue in loop
                }
                log.info("Generating QR code for table {} (code: {})", tableId, table != null ? table.getTableCode() : null);
                QrUrls urls = generateAndUploadQr(restaurant, tableId);
                if (urls != null) {
                    restaurantTableQrUrlUpdater.saveQrUrls(tableId, urls.qrCodeUrl(), urls.printQrCodeUrl());
                }
                successCount++;
                log.info("Successfully generated and saved QR code for table {} (code: {}). QR URL: {}",
                        tableId, table != null ? table.getTableCode() : null, urls != null ? urls.qrCodeUrl() : null);
            } catch (Exception e) {
                errorCount++;
                log.error("Failed to generate QR for table {}: {}", tableId, e.getMessage(), e);
            }
        }
        
        log.info("Async QR code generation completed for restaurant {} - Success: {}, Skipped: {}, Errors: {}", 
                restaurantId, successCount, skippedCount, errorCount);
    }

    private void logSkipReason(UUID tableId, RestaurantTable table) {
        if (table == null) {
            log.warn("Table {} not found during async QR generation. This may indicate the table was deleted or the transaction hasn't committed yet.", tableId);
            return;
        }
        if (Boolean.TRUE.equals(table.getIsVirtual())) {
            log.debug("Skipping virtual table {} during async QR generation", tableId);
            return;
        }
        if (table.getQrCodeUrl() != null && !table.getQrCodeUrl().trim().isEmpty()) {
            log.debug("Table {} already has QR code URL, skipping: {}", tableId, table.getQrCodeUrl());
        }
    }

    private record QrUrls(String qrCodeUrl, String printQrCodeUrl) {}

    private QrUrls generateAndUploadQr(Restaurant restaurant, UUID tableId) {
        UUID restaurantId = restaurant.getId();
        String qrContent = String.format("%s/customer/r/%s/%s", appProperties.getBaseUrl(), restaurantId, tableId);
        try {
            var bitMatrix = new QRCodeWriter().encode(qrContent, BarcodeFormat.QR_CODE, 250, 250);
            BufferedImage qrImage = MatrixToImageWriter.toBufferedImage(bitMatrix);
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ImageIO.write(qrImage, "png", baos);
            byte[] bytes = baos.toByteArray();
            InputStream inputStream = new ByteArrayInputStream(bytes);
            String fileName = "table-id_" + tableId + ".png";
            String s3Key = "qr-codes/" + restaurantId + "/" + fileName;
            String uploadedFileUrl = awsService.uploadFile(inputStream, s3Key, bytes.length);
            String pdfUrl = generateQrPdfBestEffort(restaurantId, tableId, uploadedFileUrl);
            return new QrUrls(uploadedFileUrl, pdfUrl);
        } catch (Exception e) {
            log.error("Failed to generate/upload QR code for table {}: {}", tableId, e.getMessage(), e);
            throw new QrCodeGenerationException("Failed to generate/upload QR code: " + e.getMessage(), e);
        }
    }

    private String generateQrPdfBestEffort(UUID restaurantId, UUID tableId, String qrCodeUrl) {
        try {
            Restaurant restaurant = restaurantRepository.findById(restaurantId).orElse(null);
            RestaurantTable table = restaurantTableRepository.findById(tableId).orElse(null);
            if (restaurant == null || table == null) {
                return null;
            }
            table.setQrCodeUrl(qrCodeUrl);
            String pdfUrl = printQrCodeService.generateQrCodePdf(restaurant, table);
            return pdfUrl;
        } catch (Exception e) {
            log.error("Failed to generate QR code PDF for table {}: {}", tableId, e.getMessage(), e);
            return null;
        }
    }
}
