package com.gulfnet.restaurantmanagement.service.impl;

import com.fasterxml.jackson.databind.node.NullNode;
import com.gulfnet.restaurantmanagement.config.GmoLinkPlusProperties;
import com.gulfnet.restaurantmanagement.service.GmoLinkPlusNotificationService;
import com.gulfnet.restaurantmanagement.service.GmoLinkPlusSearchTradeService;
import com.gulfnet.restaurantmanagement.service.GmoService;
import com.gulfnet.restaurantmanagement.service.RefundService;
import com.gulfnet.shared_library.entity.Order;
import com.gulfnet.shared_library.entity.Transaction;
import com.gulfnet.shared_library.enums.TransactionStatus;
import com.gulfnet.shared_library.repository.OrderRepository;
import com.gulfnet.shared_library.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;
import java.util.Map;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class GmoLinkPlusNotificationServiceImpl implements GmoLinkPlusNotificationService {

    private static final String RESPONSE_OK = "0";
    private static final String RESPONSE_RETRY = "1";

    private final OrderRepository orderRepository;
    private final TransactionRepository transactionRepository;
    private final GmoPostPaymentService gmoPostPaymentService;
    private final GmoService gmoService;
    private final GmoLinkPlusProperties gmoLinkPlusProperties;
    private final GmoLinkPlusSearchTradeService gmoLinkPlusSearchTradeService;
    private final GmoLinkPlusTradeCredentialsAsyncService gmoLinkPlusTradeCredentialsAsyncService;

    @Autowired
    @Lazy
    private RefundService refundService;

    @Override
    @Transactional
    public String processResultNotification(Map<String, String> form) {
        log.info("[GMO LinkPlus notify] Processing payload: {}", formatPayloadForLog(form));
        logGmoTradeCredentials(form);
        try {
            String gmoOrderId = firstParam(form, "OrderID");
            if (gmoOrderId == null || gmoOrderId.isBlank()) {
                log.warn("[GMO LinkPlus notify] Missing OrderID; acknowledging to stop retries");
                return RESPONSE_OK;
            }
            gmoOrderId = gmoOrderId.trim();

            if (!isShopIdValid(form)) {
                return RESPONSE_OK;
            }

            Optional<Order> orderOpt = orderRepository.findByGmoLinkOrderId(gmoOrderId);
            if (orderOpt.isEmpty()) {
                log.warn("[GMO LinkPlus notify] No order for gmoLinkOrderId={}", gmoOrderId);
                return RESPONSE_OK;
            }
            Order order = orderOpt.get();

            Optional<Transaction> txOpt = transactionRepository.findByOrderId(order.getId());
            if (txOpt.isEmpty()) {
                log.warn("[GMO LinkPlus notify] No transaction for orderId={}", order.getId());
                return RESPONSE_OK;
            }
            Transaction transaction = txOpt.get();

            if (!isHostedCardPayment(transaction.getPaymentMethod())) {
                log.info("[GMO LinkPlus notify] Ignoring notify for transaction {} paymentMethod={}",
                        transaction.getId(), transaction.getPaymentMethod());
                return RESPONSE_OK;
            }

            String status = firstParam(form, "Status");
            String errCode = firstParam(form, "ErrCode");
            String errInfo = firstParam(form, "ErrInfo");

            log.info("[GMO LinkPlus notify] orderId={}, txId={}, Status={}, ErrCode={}, ErrInfo={}",
                    order.getId(), transaction.getId(), status, errCode, errInfo);

            if (isCaptureSuccess(status, errCode)) {
                if (!isCaptureConfirmedBySearchTrade(gmoOrderId, status, errCode)) {
                    log.warn("[GMO LinkPlus notify] CAPTURE not confirmed by SearchTrade for gmoOrderId={}", gmoOrderId);
                    return RESPONSE_OK;
                }
                gmoLinkPlusTradeCredentialsAsyncService.fetchAndPersistTradeCredentialsAsync(
                        transaction.getId(), gmoOrderId);
                persistGmoLinkPlusTradeCredentials(transaction, form, gmoOrderId);
                TransactionStatus currentStatus = transactionRepository.findById(transaction.getId())
                        .map(Transaction::getTransactionStatus)
                        .orElse(transaction.getTransactionStatus());
                if (currentStatus == TransactionStatus.COMPLETED) {
                    log.info("[GMO LinkPlus notify] Transaction {} already COMPLETED; idempotent ack",
                            transaction.getId());
                    return RESPONSE_OK;
                }
                gmoPostPaymentService.handleSuccess(transaction.getId(), Locale.ENGLISH, NullNode.getInstance());
                return RESPONSE_OK;
            }

            if (isReturnSuccess(status, errCode)) {
                log.info("[GMO LinkPlus notify] RETURN for tx {}; finalizing pending card refund", transaction.getId());
                refundService.completeCardRefundFromGmoNotify(transaction.getId());
                return RESPONSE_OK;
            }

            if (transaction.getTransactionStatus() == TransactionStatus.COMPLETED
                    || transaction.getTransactionStatus() == TransactionStatus.REFUNDED
                    || transaction.getTransactionStatus() == TransactionStatus.PARTIALLY_REFUNDED) {
                log.info("[GMO LinkPlus notify] Non-capture notify for tx {} status={}; ack",
                        transaction.getId(), transaction.getTransactionStatus());
                return RESPONSE_OK;
            }

            String reason = buildFailureReason(status, errCode, errInfo);
            gmoService.notifyHostedCardPaymentCanceled(transaction.getId(), Locale.ENGLISH, reason);
            return RESPONSE_OK;
        } catch (Exception e) {
            log.error("[GMO LinkPlus notify] Processing failed", e);
            return RESPONSE_RETRY;
        }
    }

    /**
     * Stores GMO {@code AccessID} / {@code AccessPass} (and {@code OrderID} as {@code gmo_order_id}) on the
     * transaction for later {@code AlterTran} card refunds.
     */
    private void persistGmoLinkPlusTradeCredentials(Transaction transaction, Map<String, String> form, String gmoOrderId) {
        String accessId = trimToNull(firstParam(form, "AccessID"));
        String accessPass = trimToNull(firstParam(form, "AccessPass"));
        if (accessId == null && accessPass == null) {
            log.warn("[GMO LinkPlus notify] CAPTURE notify missing AccessID and AccessPass for transaction {}",
                    transaction.getId());
            return;
        }

        boolean updated = false;
        if (accessId != null && !accessId.equals(transaction.getGmoAccessId())) {
            transaction.setGmoAccessId(accessId);
            updated = true;
        }
        if (accessPass != null
                && !GmoLinkPlusSearchTradeServiceImpl.isMaskedAccessPass(accessPass)
                && !accessPass.equals(transaction.getGmoAccessPass())) {
            transaction.setGmoAccessPass(accessPass);
            updated = true;
        }
        if (gmoOrderId != null
                && (transaction.getGmoOrderId() == null || transaction.getGmoOrderId().isBlank())) {
            transaction.setGmoOrderId(gmoOrderId);
            updated = true;
        }

        if (updated) {
            transactionRepository.save(transaction);
            log.info(
                    "[GMO LinkPlus notify] Stored GMO trade credentials on transaction {} (gmoOrderId={}, accessId={}, accessPassLength={})",
                    transaction.getId(),
                    gmoOrderId,
                    accessId,
                    accessPass != null ? accessPass.length() : 0);
        }
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    /**
     * Explicit log for refund-related GMO fields ({@code AccessID} / {@code AccessPass}) on result notification.
     */
    private void logGmoTradeCredentials(Map<String, String> form) {
        String accessId = firstParam(form, "AccessID");
        String accessPass = firstParam(form, "AccessPass");
        String tranId = firstParam(form, "TranID");

        boolean accessIdPresent = accessId != null && !accessId.isBlank();
        boolean accessPassPresent = accessPass != null && !accessPass.isBlank();

        log.info(
                "[GMO LinkPlus notify] Trade credentials from GMO: AccessID={}, AccessPass={}, TranID={}",
                accessIdPresent ? "present value=" + accessId.trim() : "MISSING",
                accessPassPresent
                        ? "present length=" + accessPass.trim().length() + " masked=" + maskSecret(accessPass)
                        : "MISSING",
                tranId != null && !tranId.isBlank() ? "present value=" + tranId.trim() : "MISSING");

        if (form != null && !form.isEmpty()) {
            log.info("[GMO LinkPlus notify] All notify parameter keys: {}", form.keySet());
        }
    }

    private static String maskSecret(String value) {
        if (value == null || value.isBlank()) {
            return "<empty>";
        }
        String trimmed = value.trim();
        if (trimmed.length() <= 4) {
            return "****";
        }
        return trimmed.substring(0, 4) + "****(len=" + trimmed.length() + ")";
    }

    private static boolean isSensitiveNotifyKey(String key) {
        if (key == null) {
            return false;
        }
        String k = key.trim();
        return "AccessPass".equalsIgnoreCase(k)
                || "ShopPass".equalsIgnoreCase(k)
                || "Pass".equalsIgnoreCase(k);
    }

    private static String formatPayloadForLog(Map<String, String> form) {
        if (form == null || form.isEmpty()) {
            return "{}";
        }
        StringBuilder sb = new StringBuilder("{");
        boolean first = true;
        for (Map.Entry<String, String> e : form.entrySet()) {
            if (!first) {
                sb.append(", ");
            }
            first = false;
            sb.append(e.getKey()).append('=');
            if (e.getValue() == null) {
                sb.append("<null>");
            } else if (isSensitiveNotifyKey(e.getKey())) {
                sb.append(maskSecret(e.getValue()));
            } else {
                sb.append(e.getValue());
            }
        }
        sb.append('}');
        return sb.toString();
    }

    private boolean isShopIdValid(Map<String, String> form) {
        if (!gmoLinkPlusProperties.isConfigured()) {
            return true;
        }
        String expected = gmoLinkPlusProperties.getShopId() != null ? gmoLinkPlusProperties.getShopId().trim() : "";
        if (expected.isEmpty()) {
            return true;
        }
        String notifyShopId = firstParam(form, "ShopID");
        if (notifyShopId == null || notifyShopId.isBlank()) {
            log.warn("[GMO LinkPlus notify] Missing ShopID while Link Plus is configured");
            return false;
        }
        if (!expected.equalsIgnoreCase(notifyShopId.trim())) {
            log.warn("[GMO LinkPlus notify] ShopID mismatch: expected={}, got={}", expected, notifyShopId);
            return false;
        }
        return true;
    }

    private boolean isCaptureConfirmedBySearchTrade(String gmoOrderId, String notifyStatus, String notifyErrCode) {
        Optional<GmoLinkPlusSearchTradeService.GmoLinkPlusTradeLookup> lookup =
                gmoLinkPlusSearchTradeService.searchTradeByOrderId(gmoOrderId);
        if (lookup.isEmpty()) {
            return false;
        }
        GmoLinkPlusSearchTradeService.GmoLinkPlusTradeLookup trade = lookup.get();
        if (!GmoLinkPlusTradeStatusSupport.isPaidTradeStatus(trade.status(), null)) {
            return false;
        }
        if (notifyStatus != null && trade.status() != null
                && !notifyStatus.trim().equalsIgnoreCase(trade.status().trim())) {
            log.warn("[GMO LinkPlus notify] Status mismatch between notify ({}) and SearchTrade ({}) for OrderID={}",
                    notifyStatus, trade.status(), gmoOrderId);
            return false;
        }
        if (notifyErrCode != null && !notifyErrCode.isBlank()) {
            return false;
        }
        return true;
    }

    private static String firstParam(Map<String, String> form, String key) {
        if (form == null || key == null) {
            return null;
        }
        if (form.containsKey(key)) {
            return form.get(key);
        }
        for (Map.Entry<String, String> e : form.entrySet()) {
            if (e.getKey() != null && e.getKey().equalsIgnoreCase(key)) {
                return e.getValue();
            }
        }
        return null;
    }

    private static boolean isHostedCardPayment(String paymentMethod) {
        if (paymentMethod == null) {
            return false;
        }
        String p = paymentMethod.trim();
        return "CARD".equalsIgnoreCase(p) || "CREDIT_CARD".equalsIgnoreCase(p) || "DEBIT_CARD".equalsIgnoreCase(p);
    }

    /**
     * Credit capture success for mul-pay result notification (typical {@code Status=CAPTURE}, empty {@code ErrCode}).
     */
    private static boolean isReturnSuccess(String status, String errCode) {
        if (errCode != null && !errCode.isBlank()) {
            return false;
        }
        return status != null && "RETURN".equalsIgnoreCase(status.trim());
    }

    private static boolean isCaptureSuccess(String status, String errCode) {
        return GmoLinkPlusTradeStatusSupport.isPaidTradeStatus(status, errCode);
    }

    private static String buildFailureReason(String status, String errCode, String errInfo) {
        StringBuilder sb = new StringBuilder("GMO LinkType Plus payment not completed");
        if (status != null && !status.isBlank()) {
            sb.append(" Status=").append(status.trim());
        }
        if (errCode != null && !errCode.isBlank()) {
            sb.append(" ErrCode=").append(errCode.trim());
        }
        if (errInfo != null && !errInfo.isBlank()) {
            sb.append(" ErrInfo=").append(errInfo.trim());
        }
        return sb.toString();
    }
}
