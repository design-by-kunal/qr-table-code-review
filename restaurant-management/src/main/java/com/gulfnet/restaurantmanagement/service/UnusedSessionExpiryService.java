package com.gulfnet.restaurantmanagement.service;

import com.gulfnet.restaurantmanagement.util.MessageUtil;
import com.gulfnet.shared_library.entity.RestaurantTable;
import com.gulfnet.shared_library.entity.Session;
import com.gulfnet.shared_library.enums.TableStatus;
import com.gulfnet.shared_library.model.request.StatusEventMessage;
import com.gulfnet.shared_library.repository.RestaurantTableRepository;
import com.gulfnet.shared_library.repository.SessionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Expires active table sessions that have no orders once operating-hours closing + extend hours
 * has passed for the session's issue date. When all sessions on a table are expired, sets the table to AVAILABLE.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UnusedSessionExpiryService {

    private static final String MSG_TABLE_STATUS_UPDATED = "table.status.updated";

    private final SessionRepository sessionRepository;
    private final RestaurantTableRepository restaurantTableRepository;
    private final OperatingHoursCutoffService operatingHoursCutoffService;
    private final SimpMessagingTemplate messagingTemplate;
    private final MessageUtil messageUtil;

    @Transactional
    public int expireUnusedSessionsForRestaurant(UUID restaurantId) {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        List<Session> candidates = sessionRepository.findActiveSessionsWithoutOrdersByRestaurantId(restaurantId);
        if (candidates.isEmpty()) {
            return 0;
        }
        return expireSessions(candidates, now);
    }

    private int expireSessions(List<Session> candidates, OffsetDateTime now) {
        int expiredCount = 0;
        Map<UUID, UUID> affectedTables = new HashMap<>();
        for (Session session : candidates) {
            if (session.getIssuedAt() == null) {
                continue;
            }
            LocalDate issuedDate = session.getIssuedAt().atZoneSameInstant(ZoneOffset.UTC).toLocalDate();
            Optional<OffsetDateTime> cutoff =
                    operatingHoursCutoffService.resolveCutoffInstant(session.getRestaurantId(), issuedDate);
            if (cutoff.isEmpty() || now.isBefore(cutoff.get())) {
                continue;
            }
            session.setExpiredAt(now);
            sessionRepository.save(session);
            expiredCount++;
            affectedTables.put(session.getTableId(), session.getRestaurantId());
            log.info(
                    "Expired unused session {} (table {}, restaurant {}) — issued {}, cutoff {}",
                    session.getId(),
                    session.getTableId(),
                    session.getRestaurantId(),
                    session.getIssuedAt(),
                    cutoff.get());
        }
        for (Map.Entry<UUID, UUID> affectedTable : affectedTables.entrySet()) {
            maybeSetTableAvailableWhenNoActiveSessions(
                    affectedTable.getKey(), affectedTable.getValue(), now);
        }
        if (expiredCount > 0) {
            log.info("Expired {} unused session(s) past operating-hours cutoff", expiredCount);
        }
        return expiredCount;
    }

    private void maybeSetTableAvailableWhenNoActiveSessions(
            UUID tableId, UUID restaurantId, OffsetDateTime now) {
        if (!sessionRepository.findByTableIdAndExpiredAtIsNull(tableId).isEmpty()) {
            return;
        }

        RestaurantTable table = restaurantTableRepository.findById(tableId).orElse(null);
        if (table == null || table.getTableStatus() != TableStatus.OCCUPIED) {
            return;
        }

        table.setTableStatus(TableStatus.AVAILABLE);
        table.setUpdatedAt(now);
        restaurantTableRepository.save(table);
        log.info(
                "Set table {} to AVAILABLE after unused session expiry (restaurant {})",
                tableId,
                restaurantId);
        sendTableStatusWebSocketNotificationBestEffort(restaurantId, tableId);
    }

    private void sendTableStatusWebSocketNotificationBestEffort(UUID restaurantId, UUID tableId) {
        try {
            Locale locale = Locale.ENGLISH;
            String topic = "/topic/restaurant/" + restaurantId + "/table-status";
            StatusEventMessage eventMessage = StatusEventMessage.builder()
                    .message(messageUtil.getMessage(MSG_TABLE_STATUS_UPDATED, locale))
                    .build();
            messagingTemplate.convertAndSend(topic, eventMessage);
            log.info("[Notification][WebSocket] broadcast topic={} tableId={} status={} restaurantId={}",
                    topic, tableId, TableStatus.AVAILABLE, restaurantId);
        } catch (Exception e) {
            log.error("Failed to send WebSocket notification for table {} after unused session expiry: {}",
                    tableId, e.getMessage());
        }
    }
}
