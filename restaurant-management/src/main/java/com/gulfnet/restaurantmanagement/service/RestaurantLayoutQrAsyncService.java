package com.gulfnet.restaurantmanagement.service;

import java.util.List;
import java.util.UUID;

/**
 * Async service for QR code generation to avoid blocking layout create/update API responses.
 */
public interface RestaurantLayoutQrAsyncService {

    /**
     * Generates and uploads QR codes for tables that don't have one. Runs asynchronously.
     * Fetches entities in the async thread to avoid detached entity issues.
     *
     * @param restaurantId the restaurant ID
     * @param tableIds table IDs that need QR codes (non-virtual, without qrCodeUrl)
     */
    void generateQrCodesForTablesAsync(UUID restaurantId, List<UUID> tableIds);
}
