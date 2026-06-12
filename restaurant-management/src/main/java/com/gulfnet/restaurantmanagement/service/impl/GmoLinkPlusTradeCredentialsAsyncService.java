package com.gulfnet.restaurantmanagement.service.impl;

import com.gulfnet.restaurantmanagement.service.GmoLinkPlusSearchTradeService;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

/**
 * Fetches real GMO {@code AccessID}/{@code AccessPass} via {@code SearchTrade.idPass} without blocking notify handling.
 * Payment completion and audit are handled only by the LinkPlus result notification ({@link GmoPostPaymentService}).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GmoLinkPlusTradeCredentialsAsyncService {

    private final GmoLinkPlusSearchTradeService gmoLinkPlusSearchTradeService;
    private final EntityManager entityManager;

    @Async("notificationTaskExecutor")
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void fetchAndPersistTradeCredentialsAsync(UUID transactionId, String gmoOrderId) {
        if (transactionId == null || gmoOrderId == null || gmoOrderId.isBlank()) {
            return;
        }
        try {
            Optional<GmoLinkPlusSearchTradeService.GmoLinkPlusTradeLookup> lookup =
                    gmoLinkPlusSearchTradeService.searchTradeByOrderId(gmoOrderId);
            if (lookup.isEmpty()) {
                log.warn("[GMO LinkPlus SearchTrade] Async lookup returned no credentials for tx {} OrderID={}",
                        transactionId, gmoOrderId);
                return;
            }

            GmoLinkPlusSearchTradeService.GmoLinkPlusTradeLookup trade = lookup.get();
            if (trade.accessId() == null || trade.accessPass() == null) {
                log.warn("[GMO LinkPlus SearchTrade] Async lookup missing AccessID/AccessPass for tx {} OrderID={}",
                        transactionId, gmoOrderId);
                return;
            }

            int updated = entityManager.createQuery(
                            "UPDATE Transaction t SET t.gmoAccessId = :accessId, t.gmoAccessPass = :accessPass, "
                                    + "t.gmoOrderId = COALESCE(t.gmoOrderId, :gmoOrderId), t.updatedAt = :updatedAt "
                                    + "WHERE t.id = :id")
                    .setParameter("accessId", trade.accessId())
                    .setParameter("accessPass", trade.accessPass())
                    .setParameter("gmoOrderId", gmoOrderId.trim())
                    .setParameter("updatedAt", OffsetDateTime.now(ZoneOffset.UTC))
                    .setParameter("id", transactionId)
                    .executeUpdate();

            if (updated > 0) {
                log.info(
                        "[GMO LinkPlus SearchTrade] Stored trade credentials on tx {} (OrderID={}, accessId={}, accessPassLength={}, status={})",
                        transactionId,
                        gmoOrderId,
                        trade.accessId(),
                        trade.accessPass().length(),
                        trade.status());
            } else {
                log.warn("[GMO LinkPlus SearchTrade] Async credential update skipped — transaction {} not found",
                        transactionId);
                log.info("[GMO LinkPlus SearchTrade] Credentials unchanged for tx {} OrderID={}",
                        transactionId, gmoOrderId);
            }
        } catch (Exception e) {
            log.error("[GMO LinkPlus SearchTrade] Async persist failed for tx {} OrderID={}: {}",
                    transactionId, gmoOrderId, e.getMessage(), e);
        }
    }
}
