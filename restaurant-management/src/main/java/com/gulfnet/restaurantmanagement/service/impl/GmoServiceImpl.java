package com.gulfnet.restaurantmanagement.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gulfnet.restaurantmanagement.config.GmoProperties;
import com.gulfnet.restaurantmanagement.service.GmoService;
import com.gulfnet.restaurantmanagement.service.OrderNotificationService;
import com.gulfnet.restaurantmanagement.service.OrderRecalculationService;
import com.gulfnet.restaurantmanagement.service.OrderValidationService;
import com.gulfnet.restaurantmanagement.service.ReceiptService;
import com.gulfnet.restaurantmanagement.service.NotificationService;
import com.gulfnet.restaurantmanagement.service.AuditTrailService;
import com.gulfnet.restaurantmanagement.service.RestaurantAlertEvaluationService;
import com.gulfnet.restaurantmanagement.config.RestaurantChainConfigProperties;
import com.gulfnet.shared_library.entity.Order;
import com.gulfnet.shared_library.entity.OrderedItem;
import com.gulfnet.shared_library.entity.Restaurant;
import com.gulfnet.shared_library.entity.Transaction;
import com.gulfnet.shared_library.entity.User;
import com.gulfnet.shared_library.enums.ActionType;
import com.gulfnet.shared_library.enums.ItemStatus;
import com.gulfnet.shared_library.enums.OrderStatus;
import com.gulfnet.shared_library.enums.OrderType;
import com.gulfnet.shared_library.enums.PaymentSystemType;
import com.gulfnet.shared_library.enums.RequestStatus;
import com.gulfnet.shared_library.enums.TransactionStatus;
import com.gulfnet.shared_library.model.response.PaymentResponse;
import com.gulfnet.shared_library.model.response.dto.ResponseDto;
import com.gulfnet.shared_library.repository.OrderRepository;
import com.gulfnet.shared_library.repository.OrderedItemRepository;
import com.gulfnet.shared_library.repository.TransactionRepository;
import com.gulfnet.restaurantmanagement.util.MessageUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.http.client.BufferingClientHttpRequestFactory;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadLocalRandom;

@Slf4j
@Service
@RequiredArgsConstructor
public class GmoServiceImpl implements GmoService {
    private final GmoProperties gmoProperties;
    private final TransactionRepository transactionRepository;
    private final OrderRepository orderRepository;
    private final OrderedItemRepository orderedItemRepository;
    private final OrderNotificationService orderNotificationService;
    private final ReceiptService receiptService;
    private final NotificationService notificationService;
    private final OrderValidationService orderValidationService;
    private final AuditTrailService auditTrailService;
    private final RestaurantAlertEvaluationService restaurantAlertEvaluationService;
    private final RestaurantChainConfigProperties restaurantChainConfigProperties;
    private final OrderRecalculationService orderRecalculationService;
    private final MessageUtil messageUtil;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private static final String LOGIN_PATH = "/gateway/api/v1/login";
    private static final String CREATE_QR_PATH = "/gateway/api/v1/qr/createqrcode";
    private static final String CHECK_ORDER_PATH = "/gateway/api/v1/qr/checkorder";
    private static final String REFUNDS_PATH = "/gateway/api/v1/qr/refunds";

    /** GMO JSON field names / success code (API contract, not i18n keys). */
    private static final String GMO_JSON_RETURN_CODE = "returnCode";
    private static final String GMO_JSON_RETURN_MESSAGE = "returnMessage";
    private static final String GMO_JSON_RESULT = "result";
    private static final String GMO_JSON_ORDER_ID = "order_id";
    private static final String GMO_RETURN_CODE_SUCCESS = "MP10000";

    private record GmoLoginResult(String credentialKey, String loginId, Integer merchantId) {}

    /**
     * Creates a GMO QR (UPI) payment for the given order/transaction and returns the QR payload for display.
     * <p>
     * Side effects:
     * - updates the provided {@code transaction} with a fresh GMO {@code orderReservationId} (and clears any prior GMO order id)
     * - calls GMO login and QR creation APIs
     * <p>
     * The returned response includes a Base64-encoded QR code image and an authorization URI.
     *
     * @param cashierId initiating cashier id (may be {@code null}; used by caller/business logic)
     * @param orderId order id (required)
     * @param orderNumber human-readable order number (may be {@code null})
     * @param amount payment amount (JPY, must be > 0)
     * @param upiType UPI provider/type (required; mapped to GMO {@code channel})
     * @param transaction transaction to bind this payment attempt to (required)
     * @param locale locale for localized error messages (may be {@code null})
     * @return payment response containing QR code content and identifiers
     * @throws ResponseStatusException if validation fails or GMO APIs fail
     */
    @Override
    public PaymentResponse createGmoQrPayment(String cashierId,
                                              UUID orderId,
                                              String orderNumber,
                                              BigDecimal amount,
                                              String upiType,
                                              Transaction transaction,
                                              Locale locale) {
        log.info("[GMO] Starting GMO QR payment creation for orderId={}, orderNumber={}, upiType={}, amount={}",
                orderId, orderNumber, upiType, amount);

        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Order not found"));

        // Validate transaction status before proceeding
        TransactionStatus status = transaction.getTransactionStatus();
        if (status == TransactionStatus.COMPLETED) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Transaction has already been completed for this order. No further payment allowed.");
        }
        if (status == TransactionStatus.REFUNDED || status == TransactionStatus.PARTIALLY_REFUNDED) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Transaction has already been refunded for this order. New payment is not allowed.");
        }

        // Generate/refresh GMO order reservation id
        String orderReservationId = generateGmoOrderReservationId();
        transaction.setGmoOrderReservationId(orderReservationId);
        transaction.setGmoOrderId(null);

        // Ensure amount is valid
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    messageUtil.getMessage("payment.amount.invalid", locale));
        }

        // Login to GMO
        GmoLoginResult loginResult = loginToGmo();

        // Build headers
        HttpHeaders headers = buildGmoHeaders(loginResult.credentialKey(), loginResult.loginId());

        // Map type -> channel
        String channel = mapTypeToChannel(upiType);

        Map<String, Object> body = new HashMap<>();
        body.put("order_reservation_id", orderReservationId);
        body.put("description", "Restaurant UPI payment for order " + order.getOrderNumber());
        body.put("price", amount.longValue());
        body.put("currency", "JPY");
        body.put("operator", loginResult.loginId());
        body.put("channel", channel);

        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);

        String url = gmoProperties.getBaseUrl() + CREATE_QR_PATH;
        log.info("[GMO] Calling createqrcode at {} with orderReservationId={} channel={} amount={}",
                url, orderReservationId, channel, amount);

        ResponseEntity<JsonNode> response;
        try {
            response = createPlainRestTemplate().exchange(url, HttpMethod.PUT, entity, JsonNode.class);
        } catch (Exception e) {
            log.error("[GMO] Error calling createqrcode for orderReservationId={}", orderReservationId, e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "Failed to create GMO QR payment: " + e.getMessage());
        }

        JsonNode bodyNode = response.getBody();
        if (bodyNode == null) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "Empty response from GMO createqrcode");
        }

        String returnCode = bodyNode.path(GMO_JSON_RETURN_CODE).asText(null);
        if (!GMO_RETURN_CODE_SUCCESS.equals(returnCode)) {
            String msg = bodyNode.path(GMO_JSON_RETURN_MESSAGE).asText("");
            log.error("[GMO] createqrcode failed for orderReservationId={} returnCode={} message={}",
                    orderReservationId, returnCode, msg);
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "GMO createqrcode failed: " + msg);
        }

        JsonNode resultNode = bodyNode.path(GMO_JSON_RESULT);
        if (resultNode.isMissingNode()) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "GMO createqrcode response missing result");
        }

        String qrcodeString = resultNode.path("qrcode_string").asText(null);
        String gmoOrderId = resultNode.path(GMO_JSON_ORDER_ID).asText(null);
        if (qrcodeString == null || qrcodeString.isBlank()) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "GMO createqrcode did not return qrcode_string");
        }

        // Update transaction to PENDING (cashier/initiator may already be set by caller)
        transaction.setPaymentMethod("UPI");
        transaction.setPaymentApp(upiType != null && !upiType.isBlank() ? upiType.trim().toLowerCase() : null);
        transaction.setTransactionAmount(amount);
        transaction.setTransactionStatus(TransactionStatus.PENDING);
        transaction.setUpdatedAt(OffsetDateTime.now(ZoneOffset.UTC));
        transaction.setGmoOrderId(gmoOrderId);
        if (transaction.getCashier() == null && cashierId != null && !cashierId.isBlank()) {
            try {
                User cashierRef = new User();
                cashierRef.setId(UUID.fromString(cashierId.trim()));
                transaction.setCashier(cashierRef);
                transaction.setPaymentInitiatorType(Transaction.PAYMENT_INITIATOR_CASHIER);
            } catch (IllegalArgumentException e) {
                log.warn("[GMO] Invalid cashierId for GMO QR payment: {}", cashierId);
            }
        }

        transactionRepository.saveAndFlush(transaction);

        // Generate QR image from qrcodeString (same size as Omise QR)
        String qrBase64 = generateQrCodeAsBase64(qrcodeString);

        UUID restaurantId = orderNotificationService.getRestaurantIdFromOrder(order);

        return PaymentResponse.builder()
                .orderId(orderId)
                .paymentMethod("UPI")
                .paymentApp(transaction.getPaymentApp())
                .amountPaid(amount)
                .transactionId(transaction.getId())
                .transactionNumber(transaction.getTransactionNumber())
                .transactionStatus(TransactionStatus.PENDING)
                .chargeId(orderReservationId)
                .qrCode(qrBase64)
                .authorizationUri(qrcodeString)
                .restaurantId(restaurantId)
                .build();
    }

    @Override
    public void startAsyncPolling(UUID transactionId, Locale locale, int maxDurationSeconds) {
        log.info("[GMO] Starting async polling for transactionId={} maxDurationSeconds={}", transactionId, maxDurationSeconds);
        CompletableFuture.runAsync(() -> pollUntilComplete(transactionId, locale, maxDurationSeconds));
    }

    /**
     * Calls GMO refund API for a previously created/paid GMO QR payment.
     * <p>
     * Uses {@code transaction.gmoOrderId} when present; otherwise falls back to {@code transaction.gmoOrderReservationId}.
     * Refund amount is treated as JPY integer units (no decimals).
     *
     * @param transaction transaction being refunded (required)
     * @param refund refund entity containing the refund amount (required)
     * @param gmoRefundId GMO refund id to submit (required)
     * @param locale locale for localized error messages (may be {@code null})
     * @throws ResponseStatusException if identifiers are missing/invalid or GMO returns a non-success code
     */
    @Override
    public void processGmoRefund(Transaction transaction, com.gulfnet.shared_library.entity.Refund refund, String gmoRefundId, Locale locale) {
        String gmoOrderId = transaction.getGmoOrderId();
        String orderReservationId = transaction.getGmoOrderReservationId();
        if ((gmoOrderId == null || gmoOrderId.isBlank()) && (orderReservationId == null || orderReservationId.isBlank())) {
            log.error("[GMO] Cannot process GMO refund: no gmoOrderId or gmoOrderReservationId for transaction {}", transaction.getId());
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    messageUtil.getMessage("refund.upi.missing.charge.id", locale));
        }

        // Amount is in JPY (no decimals)
        java.math.BigDecimal amount = refund.getTotalRefundAmount();
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    messageUtil.getMessage("refund.amount.invalid", locale));
        }
        long fee = amount.longValue(); // assuming totalRefundAmount already in JPY units

        GmoLoginResult loginResult = loginToGmo();
        HttpHeaders headers = buildGmoHeaders(loginResult.credentialKey(), loginResult.loginId());

        Map<String, Object> body = new HashMap<>();
        body.put("refund_id", gmoRefundId);
        // Prefer payment order_id when available, otherwise fallback to reservation id
        body.put(GMO_JSON_ORDER_ID, gmoOrderId != null && !gmoOrderId.isBlank() ? gmoOrderId : orderReservationId);
        body.put("fee", fee);

        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);
        String url = gmoProperties.getBaseUrl() + REFUNDS_PATH;

        log.info("[GMO] Initiating GMO refund for transactionId={} refundId={} orderId={} fee={}",
                transaction.getId(), gmoRefundId, body.get(GMO_JSON_ORDER_ID), fee);

        ResponseEntity<JsonNode> response;
        try {
            response = createPlainRestTemplate().exchange(url, HttpMethod.PUT, entity, JsonNode.class);
        } catch (Exception e) {
            log.error("[GMO] Error calling GMO refund API for transaction {}", transaction.getId(), e);
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    messageUtil.getMessage("refund.upi.gmo.error", locale) + ": " + e.getMessage());
        }

        JsonNode bodyNode = response.getBody();
        if (bodyNode == null) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    messageUtil.getMessage("refund.upi.gmo.error", locale) + ": empty response");
        }

        String returnCode = bodyNode.path(GMO_JSON_RETURN_CODE).asText(null);
        if (!GMO_RETURN_CODE_SUCCESS.equals(returnCode)) {
            String msg = bodyNode.path(GMO_JSON_RETURN_MESSAGE).asText("");
            log.error("[GMO] Refund failed for transactionId={} refundId={} returnCode={} message={}",
                    transaction.getId(), gmoRefundId, returnCode, msg);
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    messageUtil.getMessage("refund.upi.gmo.failed", locale) + ": " + msg);
        }

        JsonNode resultNode = bodyNode.path(GMO_JSON_RESULT);
        String resResultCode = resultNode.path("res_result_code").asText(null);
        log.info("[GMO] Refund response for transactionId={} refundId={} resResultCode={}",
                transaction.getId(), gmoRefundId, resResultCode);
        // GMO spec: SUCCESS/FINISHED or similar codes would indicate refund completion; treat MP10000 as success here.
    }

    @Override
    public void notifyQrPaymentCanceledOrTimedOut(UUID transactionId, Locale locale, String reason) {
        handleCancel(transactionId, locale, reason != null && !reason.isBlank() ? reason : "GMO payment canceled");
    }

    @Override
    public void notifyHostedCardPaymentCanceled(UUID transactionId, Locale locale, String reason) {
        handleHostedCardCancel(transactionId, locale,
                reason != null && !reason.isBlank() ? reason : "GMO hosted card payment canceled");
    }

    private void invokeHandleSuccessForPoll(UUID transactionId, Locale locale, JsonNode resultNode) {
        try {
            handleSuccess(transactionId, locale, resultNode);
        } catch (Exception ex) {
            log.error("[GMO] Error while handling success for transactionId={}", transactionId, ex);
        }
    }

    private void invokeHandleCancelForPoll(UUID transactionId, Locale locale) {
        try {
            handleCancel(transactionId, locale, "GMO payment canceled");
        } catch (Exception ex) {
            log.error("[GMO] Error while handling cancel for transactionId={}", transactionId, ex);
        }
    }

    /**
     * Polls GMO {@code /checkorder} until the payment is completed/canceled or a timeout is reached.
     * <p>
     * Notes:
     * - This method re-authenticates on each poll iteration for simplicity/robustness.
     * - For GMO {@code GET /checkorder}, {@code Content-Type} must not be set (GMO returns 415 otherwise).
     * - On timeout, the transaction is kept as PENDING (to allow retry) and cancellation handling is triggered.
     *
     * @param transactionId transaction id to poll for (required)
     * @param locale locale for notifications/messages (may be {@code null})
     * @param maxDurationSeconds maximum polling duration in seconds
     */
    private void pollUntilComplete(UUID transactionId, Locale locale, int maxDurationSeconds) {
        Optional<Transaction> txOpt = transactionRepository.findById(transactionId);
        if (txOpt.isEmpty()) {
            log.warn("[GMO] Polling aborted - transaction not found: {}", transactionId);
            return;
        }

        Transaction transaction = txOpt.get();
        String orderReservationId = transaction.getGmoOrderReservationId();
        if (orderReservationId == null || orderReservationId.isBlank()) {
            log.warn("[GMO] Polling aborted - gmoOrderReservationId is null for transaction {}", transactionId);
            return;
        }

        log.info("[GMO] Polling checkorder for transactionId={}, orderReservationId={}", transactionId, orderReservationId);

        long start = System.currentTimeMillis();
        long timeoutMs = maxDurationSeconds * 1000L;

        boolean finished = false;

        while (!finished && (System.currentTimeMillis() - start) < timeoutMs) {
            try {
                // Always login fresh for simplicity and robustness
                GmoLoginResult loginResult = loginToGmo();
                HttpHeaders headers = buildGmoHeaders(loginResult.credentialKey(), loginResult.loginId());
                // IMPORTANT: For GET /checkorder, do NOT send Content-Type, or GMO returns 415
                headers.setContentType(null);

                String url = gmoProperties.getBaseUrl() + CHECK_ORDER_PATH + "?storeOrderId=" + orderReservationId;
                HttpEntity<Void> entity = new HttpEntity<>(headers);

                ResponseEntity<JsonNode> response = createPlainRestTemplate().exchange(url, HttpMethod.GET, entity, JsonNode.class);
                JsonNode body = response.getBody();
                if (body == null) {
                    log.warn("[GMO] Empty response from checkorder for orderReservationId={}", orderReservationId);
                } else {
                    String returnCode = body.path(GMO_JSON_RETURN_CODE).asText(null);
                    JsonNode resultNode = body.path(GMO_JSON_RESULT);
                    String status = resultNode.path("status").asText(null);
                    String resResultCode = resultNode.path("res_result_code").asText(null);

                    log.info("[GMO] Poll result orderReservationId={} returnCode={} status={} resResultCode={}",
                            orderReservationId, returnCode, status, resResultCode);

                    if (!GMO_RETURN_CODE_SUCCESS.equals(returnCode)) {
                        // treat non-success return as pending and continue
                        log.warn("[GMO] Non-success returnCode from checkorder: {}", returnCode);
                    } else if ("PAY_SUCCESS".equalsIgnoreCase(status) ||
                            "COMPLETED".equalsIgnoreCase(resResultCode)) {
                        invokeHandleSuccessForPoll(transactionId, locale, resultNode);
                        finished = true;
                        break;
                    } else if ("PAY_CLOSED".equalsIgnoreCase(status) ||
                            "PAY_CANCEL".equalsIgnoreCase(status)) {
                        invokeHandleCancelForPoll(transactionId, locale);
                        finished = true;
                        break;
                    }
                }

                Thread.sleep(2500L);
            } catch (Exception e) {
                if (e instanceof InterruptedException) {
                    Thread.currentThread().interrupt();
                }
                log.error("[GMO] Error while polling checkorder for transactionId={}", transactionId, e);
                try {
                    Thread.sleep(2500L);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                }
            }
        }

        if (!finished) {
            log.info("[GMO] Polling timeout reached for transactionId={}, keeping transaction as PENDING", transactionId);
            handleCancel(transactionId, locale, "GMO payment timeout after 4 minutes 30 seconds");
        }
    }

    private final GmoPostPaymentService gmoPostPaymentService;

    private void handleSuccess(UUID transactionId, Locale locale, JsonNode resultNode) {
        // Delegate to transactional post-payment service to ensure Hibernate session is available
        gmoPostPaymentService.handleSuccess(transactionId, locale, resultNode);
    }

    /**
     * Handles a GMO cancel/closed/timeout outcome for a transaction.
     * <p>
     * Important behavior: the transaction is intentionally left as {@code PENDING} so the payment can be retried
     * (aligns with Omise UPI handling). Notifications are sent to the cashier and any assigned waiters.
     *
     * @param transactionId transaction id to update/notify for (required)
     * @param locale locale for notifications/messages (may be {@code null})
     * @param reason human-readable cancellation/timeout reason (required)
     */
    private void handleCancel(UUID transactionId, Locale locale, String reason) {
        log.info("[GMO] Handling cancel/timeout for transactionId={}, reason={}", transactionId, reason);
        Transaction transaction = loadTransactionWithOrder(transactionId);
        if (transaction == null) {
            return;
        }

        // Do NOT cancel the transaction; keep it as PENDING so payment can be retried,
        // aligning behavior with Omise UPI handling.
        transaction.setTransactionStatus(TransactionStatus.PENDING);
        transaction.setUpdatedAt(OffsetDateTime.now(ZoneOffset.UTC));
        transactionRepository.save(transaction);

        sendGmoPaymentCanceledNotifications(transaction, locale, reason);
    }

    /**
     * Hosted card LinkType Plus cancel/failure: mark {@link TransactionStatus#CANCELED} and clear all GMO
     * checkout/capture fields so the next pay attempt assigns a fresh {@code gmo_link_order_id} and link.
     */
    private void handleHostedCardCancel(UUID transactionId, Locale locale, String reason) {
        log.info("[GMO] Handling hosted card cancel for transactionId={}, reason={}", transactionId, reason);
        Transaction transaction = loadTransactionWithOrder(transactionId);
        if (transaction == null) {
            return;
        }

        transaction.setTransactionStatus(TransactionStatus.CANCELED);
        transaction.setGmoHostedPaymentUrl(null);
        transaction.setGmoHostedPaymentLinkCreatedAt(null);
        transaction.setGmoOrderId(null);
        transaction.setGmoAccessId(null);
        transaction.setGmoAccessPass(null);
        transaction.setUpdatedAt(OffsetDateTime.now(ZoneOffset.UTC));
        transactionRepository.save(transaction);

        Order order = transaction.getOrder();
        if (order != null) {
            String previousGmoLinkOrderId = order.getGmoLinkOrderId();
            order.setGmoLinkOrderId(null);
            order.setUpdatedAt(OffsetDateTime.now(ZoneOffset.UTC));
            orderRepository.save(order);
            log.info("[GMO] Cleared hosted card GMO ids for orderId={}, previousGmoLinkOrderId={}",
                    order.getId(), previousGmoLinkOrderId);
        }

        sendGmoPaymentCanceledNotifications(transaction, locale, reason);
    }

    private Transaction loadTransactionWithOrder(UUID transactionId) {
        Transaction transaction = transactionRepository.findById(transactionId).orElse(null);
        if (transaction == null) {
            log.warn("[GMO] Transaction not found: {}", transactionId);
            return null;
        }
        Order order = transaction.getOrder();
        if (order == null) {
            log.warn("[GMO] Order is null for transaction {}", transactionId);
            return null;
        }
        try {
            order.getOrderNumber();
        } catch (Exception e) {
            log.debug("[GMO] Failed to eagerly initialize Order proxy for transaction {}: {}", transactionId, e.getMessage());
        }
        return transaction;
    }

    private void sendGmoPaymentCanceledNotifications(Transaction transaction, Locale locale, String reason) {
        Order order = transaction.getOrder();
        UUID restaurantId = orderNotificationService.getRestaurantIdFromOrder(order);
        orderNotificationService.sendTransactionUpiStatusWebSocketNotification(
                locale, restaurantId, transaction.getId(), TransactionStatus.CANCELED);
        orderNotificationService.sendTransactionStatusWebSocketNotification(
                locale, restaurantId, transaction.getId(), TransactionStatus.CANCELED);

        try {
            User cashier = transaction.getCashier();
            if (cashier != null) {
                notificationService.notifyPaymentError(
                        cashier, transaction, "PAYMENT_CANCELED", reason, locale);
            }
        } catch (Exception e) {
            log.error("[GMO] Failed to send payment error notification for GMO cancel: {}", e.getMessage(), e);
        }

        try {
            if (order.getRestaurantTable() != null) {
                User cashier = transaction.getCashier();
                List<User> assignedWaiters = orderValidationService.getWaitersForTable(order.getRestaurantTable());
                if (assignedWaiters != null && !assignedWaiters.isEmpty()) {
                    int notifiedCount = 0;
                    for (User waiter : assignedWaiters) {
                        if (waiter != null && (cashier == null || !waiter.getId().equals(cashier.getId()))) {
                            try {
                                notificationService.notifyPaymentErrorToWaiter(
                                        waiter, transaction, "PAYMENT_CANCELED", reason, locale);
                                notifiedCount++;
                            } catch (Exception e) {
                                log.error("[GMO] Failed to send payment error notification to waiter {}: {}",
                                        waiter.getId(), e.getMessage());
                            }
                        }
                    }
                    log.info("[GMO] Sent payment error notifications to {} waiter(s) for GMO cancel: transaction {}",
                            notifiedCount, transaction.getId());
                } else {
                    log.warn("[GMO] No waiters assigned to table for order {} - skipping waiter cancel notifications",
                            order.getId());
                }
            }
        } catch (Exception e) {
            log.error("[GMO] Failed to send payment error notification to waiters for GMO cancel: {}", e.getMessage(), e);
        }
    }

    /**
     * Logs in to GMO and returns credentials required for subsequent API calls.
     *
     * @return login result including {@code credentialKey} and configured {@code loginId}
     * @throws ResponseStatusException if GMO login fails or the response is missing required fields
     */
    private GmoLoginResult loginToGmo() {
        String url = gmoProperties.getBaseUrl() + LOGIN_PATH;
        Map<String, Object> body = new HashMap<>();
        body.put("loginId", gmoProperties.getLoginId());
        body.put("userPassword", gmoProperties.getUserPassword());
        body.put("osName", gmoProperties.getOsName());
        body.put("osVersion", gmoProperties.getOsVersion());

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);

        log.info("[GMO] Logging in to GMO at {}", url);
        ResponseEntity<JsonNode> response;
        try {
            response = createPlainRestTemplate().postForEntity(url, entity, JsonNode.class);
        } catch (Exception e) {
            log.error("[GMO] Login error", e);
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Failed to login to GMO: " + e.getMessage());
        }

        JsonNode bodyNode = response.getBody();
        if (bodyNode == null) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Empty GMO login response");
        }

        String returnCode = bodyNode.path(GMO_JSON_RETURN_CODE).asText(null);
        if (!GMO_RETURN_CODE_SUCCESS.equals(returnCode)) {
            String msg = bodyNode.path(GMO_JSON_RETURN_MESSAGE).asText("");
            log.error("[GMO] Login failed returnCode={} message={}", returnCode, msg);
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "GMO login failed: " + msg);
        }

        JsonNode resultNode = bodyNode.path(GMO_JSON_RESULT);
        String credentialKey = resultNode.path("credentialKey").asText(null);
        Integer merchantId = resultNode.path("merchantId").isInt() ? resultNode.path("merchantId").asInt() : null;

        if (credentialKey == null || credentialKey.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "GMO login response missing credentialKey");
        }

        return new GmoLoginResult(credentialKey, gmoProperties.getLoginId(), merchantId);
    }

    /**
     * Creates a plain RestTemplate without LoadBalancer / Eureka,
     * so external GMO URLs are called directly.
     */
    private RestTemplate createPlainRestTemplate() {
        SimpleClientHttpRequestFactory baseFactory = new SimpleClientHttpRequestFactory();
        baseFactory.setConnectTimeout(5000);
        baseFactory.setReadTimeout(10000);
        ClientHttpRequestFactory factory = new BufferingClientHttpRequestFactory(baseFactory);
        return new RestTemplate(factory);
    }

    /**
     * Builds GMO request headers including time/nonce/signature fields.
     * <p>
     * Signature payload format:
     * {@code loginId & timeMs & nonce & credentialKey}
     * hashed with SHA-256 (hex).
     *
     * @param credentialKey credential key returned by GMO login (required)
     * @param loginId login id used for signature and header (required)
     * @return headers for GMO API calls
     */
    private HttpHeaders buildGmoHeaders(String credentialKey, String loginId) {
        long nowMs = System.currentTimeMillis();
        String nonce = UUID.randomUUID().toString().replace("-", "").substring(0, 16);

        String timeStr = String.valueOf(nowMs);
        String signPayload = loginId + "&" + timeStr + "&" + nonce + "&" + credentialKey;
        String sign = sha256Hex(signPayload);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("X-GCP-Time", timeStr);
        headers.set("X-GCP-NonceStr", nonce);
        headers.set("X-GCP-Sign", sign);
        headers.set("X-GCP-loginId", loginId);

        log.info("[GMO] Built headers: time={}, nonce={}, sign={}", timeStr, nonce, sign);

        return headers;
    }

    /**
     * Computes SHA-256 hash of the given value and returns it as a lowercase hex string.
     *
     * @param value input string (required)
     * @return SHA-256 hex digest
     * @throws IllegalStateException if the SHA-256 algorithm is unavailable
     */
    private String sha256Hex(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder(2 * hash.length);
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) {
                    hexString.append('0');
                }
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (Exception e) {
            throw new IllegalStateException("Failed to compute SHA-256 hash", e);
        }
    }

    /**
     * Maps a user-provided UPI type identifier to the GMO {@code channel} value.
     *
     * @param type UPI provider/type string (required)
     * @return GMO channel string
     * @throws ResponseStatusException if {@code type} is null or unsupported
     */
    private String mapTypeToChannel(String type) {
        if (type == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "UPI type is required");
        }
        String normalized = type.trim().toLowerCase();
        return switch (normalized) {
            case "paypay" -> "PayPay";
            case "aupay", "aupai" -> "auPAY";
            case "docomo", "dbarai", "d-barai" -> "Docomo";
            case "rakutenpay", "rpay" -> "RakutenPay";
            case "linepay" -> "LINEPay";
            case "merpay" -> "merpay";
            case "ginkopay" -> "GinkoPay";
            case "quopay" -> "QUOPay";
            case "jcoinpay" -> "JCoinPay";
            case "aeonpay" -> "AEONPay";
            default -> throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Unsupported UPI type for GMO: " + type);
        };
    }

    private String generateGmoOrderReservationId() {
        // Simple 20-digit numeric using current time and random
        String base = String.valueOf(System.currentTimeMillis())
                + String.valueOf(ThreadLocalRandom.current().nextInt(1_000_000_000));
        String numeric = base.replaceAll("\\D", "");
        if (numeric.length() < 20) {
            numeric = String.format("%-20s", numeric).replace(' ', '0');
        }
        return numeric.substring(0, 20);
    }

    private String generateTransactionNumber(Restaurant restaurant) {
        // Fallback simple generator if there is no central service for this in context
        String restaurantCode = restaurant != null ? restaurant.getRestaurantCode() : "REST";
        String timestamp = String.valueOf(System.currentTimeMillis());
        return restaurantCode + "-" + timestamp;
    }

    /**
     * Generates a PNG QR code for the given content and returns it as a data-URI Base64 string.
     *
     * @param content QR payload content (required)
     * @return string of the form {@code data:image/png;base64,<...>}
     * @throws ResponseStatusException if QR code generation fails
     */
    private String generateQrCodeAsBase64(String content) {
        try {
            com.google.zxing.qrcode.QRCodeWriter qrCodeWriter = new com.google.zxing.qrcode.QRCodeWriter();
            var bitMatrix = qrCodeWriter.encode(content, com.google.zxing.BarcodeFormat.QR_CODE, 250, 250);
            java.awt.image.BufferedImage qrImage = com.google.zxing.client.j2se.MatrixToImageWriter.toBufferedImage(bitMatrix);

            java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
            javax.imageio.ImageIO.write(qrImage, "png", baos);
            byte[] bytes = baos.toByteArray();

            String base64Image = Base64.getEncoder().encodeToString(bytes);
            return "data:image/png;base64," + base64Image;
        } catch (Exception e) {
            log.error("[GMO] Failed to generate QR code", e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "Failed to generate GMO QR code: " + e.getMessage());
        }
    }
}

