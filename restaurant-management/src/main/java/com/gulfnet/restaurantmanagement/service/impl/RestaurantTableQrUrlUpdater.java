package com.gulfnet.restaurantmanagement.service.impl;

import com.gulfnet.shared_library.entity.RestaurantTable;
import com.gulfnet.shared_library.repository.RestaurantTableRepository;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class RestaurantTableQrUrlUpdater {

    private final RestaurantTableRepository restaurantTableRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void saveQrUrls(UUID tableId, String qrCodeUrl, String printQrCodeUrl) {
        if (tableId == null) {
            return;
        }
        RestaurantTable table = restaurantTableRepository.findById(tableId).orElse(null);
        if (table == null) {
            return;
        }
        table.setQrCodeUrl(qrCodeUrl);
        table.setPrintQrCodeUrl(printQrCodeUrl);
        restaurantTableRepository.save(table);
        restaurantTableRepository.flush();
    }
}

