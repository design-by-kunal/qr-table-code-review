package com.gulfnet.restaurantmanagement.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.gulfnet.shared_library.model.omise.OmiseWebhookEvent;
import com.gulfnet.restaurantmanagement.service.OmiseService;
import com.gulfnet.restaurantmanagement.service.OmiseWebhookService;
import com.gulfnet.restaurantmanagement.service.OrderNotificationService;
import com.gulfnet.restaurantmanagement.service.OmiseScannableQrStorageService;
import com.gulfnet.restaurantmanagement.service.ReceiptService;
import com.gulfnet.restaurantmanagement.service.NotificationService;
import com.gulfnet.restaurantmanagement.service.OrderValidationService;
import com.gulfnet.restaurantmanagement.service.AuditTrailService;
import com.gulfnet.restaurantmanagement.service.RestaurantAlertEvaluationService;
import com.gulfnet.shared_library.entity.Order;
import com.gulfnet.shared_library.entity.OrderedItem;
import com.gulfnet.shared_library.entity.Restaurant;
import com.gulfnet.shared_library.entity.Transaction;
import com.gulfnet.shared_library.entity.User;
import com.gulfnet.shared_library.enums.TransactionStatus;
import com.gulfnet.shared_library.enums.ActionType;
import com.gulfnet.shared_library.enums.RequestStatus;
import com.gulfnet.shared_library.enums.ItemStatus;
import com.gulfnet.shared_library.enums.OrderStatus;
import com.gulfnet.shared_library.repository.OrderRepository;
import com.gulfnet.shared_library.repository.TransactionRepository;
import com.gulfnet.shared_library.repository.OrderedItemRepository;
import com.gulfnet.shared_library.repository.OrderedComboRepository;
import com.gulfnet.restaurantmanagement.service.OrderRecalculationService;
import java.util.List;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class OmiseWebhookServiceImpl implements OmiseWebhookService {

    private static final String OMISE_STATUS_FAILED = "failed";
    
    private final TransactionRepository transactionRepository;
    private final OrderRepository orderRepository;
    private final OrderNotificationService orderNotificationService;
    private final ReceiptService receiptService;
    private final OrderedItemRepository orderedItemRepository;
    private final OrderedComboRepository orderedComboRepository;
    private final NotificationService notificationService;
    private final OrderValidationService orderValidationService;
    private final AuditTrailService auditTrailService;
    private final RestaurantAlertEvaluationService restaurantAlertEvaluationService;
    private final OrderRecalculationService orderRecalculationService;
    private final OmiseScannableQrStorageService omiseScannableQrStorageService;
    private final OmiseService omiseService;

    /**
     * Processes an Omise payment gateway webhook event.
     * Updates transaction status based on charge status (successful/paid -> COMPLETED, failed/expired -> PENDING),
     * generates receipt PDF and sends receipt email for successful payments,
     * sends WebSocket notifications for status changes,
     * evaluates restaurant alerts in real-time after transaction commit,
     * creates audit trails, and sends notifications to waiters/cashiers.
     *
     * @param event the Omise webhook event containing charge data and metadata
     * @throws ResponseStatusException if event is invalid, order ID not found in metadata, transaction not found, or order ID format is invalid
     */
    @Override
    @Transactional
    public void processWebhookEvent(OmiseWebhookEvent event) {

        WebhookContext ctx = parseAndValidate(event);
        Transaction transaction = loadTransactionOrThrow(ctx.orderId());
        verifyChargeWithOmiseApi(ctx, transaction);

        if (isSuccessfulStatus(ctx.status())) {
            handleSuccessfulPayment(ctx, transaction);
        } else if (isFailedOrExpiredStatus(ctx.status())) {
            handleFailedOrExpiredPayment(ctx, transaction);
        } else if (isPendingStatus(ctx.status())) {
            log.info("Payment still pending for charge: {}, order: {}", ctx.chargeId(), ctx.orderId());
        } else {
            log.warn("Unknown payment status from Omise: {} for charge: {}, order: {}",
                    ctx.status(), ctx.chargeId(), ctx.orderId());
        }

        log.info("Webhook processing completed for charge: {}, order: {}, final status: {}",
                ctx.chargeId(), ctx.orderId(), transaction.getTransactionStatus());
    }

    private record WebhookContext(UUID orderId, String chargeId, String status) {}

    private WebhookContext parseAndValidate(OmiseWebhookEvent event) {
        if (event == null || event.getData() == null) {
            log.error("Invalid webhook event: event or data is null");
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid webhook event");
        }

        OmiseWebhookEvent.ChargeData chargeData = event.getData();
        String chargeId = chargeData.getId();
        String status = chargeData.getStatus();

        log.info("🔍 DEBUG: chargeData.getMetadata() = {}", chargeData.getMetadata());
        if (chargeData.getMetadata() != null) {
            log.info("🔍 DEBUG: metadata.getOrderId() = {}", chargeData.getMetadata().getOrderId());
        }

        String orderIdStr = chargeData.getMetadata() != null ? chargeData.getMetadata().getOrderId() : null;
        log.info("Processing Omise webhook: chargeId={}, status={}, orderId={}",
                chargeId, status, orderIdStr);

        if (orderIdStr == null) {
            log.error("Order ID not found in webhook metadata for charge: {}", chargeId);
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Order ID not found in webhook metadata");
        }

        UUID orderId;
        try {
            orderId = UUID.fromString(orderIdStr);
        } catch (IllegalArgumentException e) {
            log.error("Invalid order ID format in webhook: {}", orderIdStr);
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid order ID format");
        }

        return new WebhookContext(orderId, chargeId, status);
    }

    private void verifyChargeWithOmiseApi(WebhookContext ctx, Transaction transaction) {
        Optional<Order> orderOpt = orderRepository.findById(ctx.orderId());
        if (orderOpt.isEmpty() || orderOpt.get().getRestaurant() == null) {
            log.error("Cannot verify Omise charge — order or restaurant missing for orderId={}", ctx.orderId());
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Order not found for webhook verification");
        }

        UUID restaurantId = orderOpt.get().getRestaurant().getId();
        JsonNode charge = omiseService.retrieveCharge(restaurantId, ctx.chargeId())
                .orElseThrow(() -> {
                    log.error("Omise charge {} not found via API for restaurant {}", ctx.chargeId(), restaurantId);
                    return new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Omise charge could not be verified");
                });

        String apiStatus = charge.path("status").asText(null);
        if (apiStatus == null || !apiStatus.equalsIgnoreCase(ctx.status())) {
            log.error("Omise charge status mismatch for {}: webhook={}, api={}", ctx.chargeId(), ctx.status(), apiStatus);
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Omise charge status mismatch");
        }

        String apiOrderId = charge.path("metadata").path("orderId").asText(null);
        if (apiOrderId == null) {
            apiOrderId = charge.path("metadata").path("order_id").asText(null);
        }
        if (apiOrderId == null || !ctx.orderId().toString().equalsIgnoreCase(apiOrderId.trim())) {
            log.error("Omise charge metadata orderId mismatch for charge {}", ctx.chargeId());
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Omise charge order metadata mismatch");
        }
    }

    private Transaction loadTransactionOrThrow(UUID orderId) {
        Optional<Transaction> transactionOpt = transactionRepository.findByOrderId(orderId);
        if (transactionOpt.isEmpty()) {
            log.error("Transaction not found for order: {}", orderId);
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Transaction not found for order");
        }
        return transactionOpt.get();
    }

    private boolean isSuccessfulStatus(String status) {
        return "successful".equalsIgnoreCase(status) || "paid".equalsIgnoreCase(status);
    }

    private boolean isFailedOrExpiredStatus(String status) {
        return OMISE_STATUS_FAILED.equalsIgnoreCase(status) || "expired".equalsIgnoreCase(status);
    }

    private boolean isPendingStatus(String status) {
        return "pending".equalsIgnoreCase(status);
    }

    private boolean usesOmiseScannableQr(Transaction transaction) {
        if (transaction == null || transaction.getPaymentApp() == null) {
            return false;
        }
        String app = transaction.getPaymentApp().trim();
        return "paynow".equalsIgnoreCase(app) || "promptpay".equalsIgnoreCase(app);
    }

    private void handleSuccessfulPayment(WebhookContext ctx, Transaction transaction) {
        UUID orderId = ctx.orderId();
        String chargeId = ctx.chargeId();

        log.info("Payment successful for charge: {}, order: {}", chargeId, orderId);

        transaction.setTransactionStatus(TransactionStatus.COMPLETED);
        transaction.setOmiseChargeId(chargeId);
        transaction.setUpdatedAt(OffsetDateTime.now(OffsetDateTime.now().getOffset()));
        transactionRepository.save(transaction);

        if (usesOmiseScannableQr(transaction)) {
            omiseScannableQrStorageService.deleteCachedQr(transaction.getId());
        }

        // Mark which lines were included in this completed payment (set once; never unset by later cancellations).
        try {
            List<OrderedItem> orderItemsForPayment = orderedItemRepository.findByOrderId(orderId);
            for (OrderedItem item : orderItemsForPayment) {
                if (item != null && item.getItemStatus() != ItemStatus.CANCELED) {
                    item.setIncludedInPayment(true);
                }
            }
            orderedItemRepository.saveAll(orderItemsForPayment);

            List<com.gulfnet.shared_library.entity.OrderedCombo> orderCombosForPayment = orderedComboRepository.findByOrderId(orderId);
            for (com.gulfnet.shared_library.entity.OrderedCombo combo : orderCombosForPayment) {
                if (combo != null && combo.getItemStatus() != ItemStatus.CANCELED) {
                    combo.setIncludedInPayment(true);
                }
            }
            orderedComboRepository.saveAll(orderCombosForPayment);
        } catch (Exception e) {
            log.error("Failed to mark includedInPayment for order {} after Omise completion: {}", orderId, e.getMessage(), e);
        }

        Optional<Order> orderOpt = orderRepository.findById(orderId);
        if (orderOpt.isEmpty()) {
            return;
        }

        Order order = orderOpt.get();
        Locale receiptLocale = receiptService.receiptLocaleFromChainConfig();

        UUID restaurantId = orderNotificationService.getRestaurantIdFromOrder(order);
        orderNotificationService.sendTransactionUpiStatusWebSocketNotification(
                receiptLocale, restaurantId, transaction.getId(), TransactionStatus.COMPLETED);

        generateReceiptIfMissingBestEffort(order, transaction, orderId);
        sendReceiptEmailIfPresentBestEffort(order, transaction, receiptLocale, orderId);
        autoPushItemsIfNeededBestEffort(order, transaction, orderId);
        evaluateAlertsAfterCommitBestEffort(order, Locale.ENGLISH);
        createPaymentAuditTrailBestEffort(order, transaction);
        notifyCashierPaymentCompletedBestEffort(order, transaction, orderId);
        notifyAssignedWaitersPaymentCompletedBestEffort(order, transaction, orderId);
    }

    private void handleFailedOrExpiredPayment(WebhookContext ctx, Transaction transaction) {
        UUID orderId = ctx.orderId();
        String chargeId = ctx.chargeId();
        String status = ctx.status();

        log.info("Payment not completed for charge: {}, order: {}, status: {}. Keeping transaction as PENDING.",
                chargeId, orderId, status);

        transaction.setTransactionStatus(TransactionStatus.PENDING);
        transaction.setUpdatedAt(OffsetDateTime.now(OffsetDateTime.now().getOffset()));
        transactionRepository.save(transaction);

        Optional<Order> orderOpt = orderRepository.findById(orderId);
        if (orderOpt.isEmpty()) {
            return;
        }

        Order order = orderOpt.get();
        UUID restaurantId = orderNotificationService.getRestaurantIdFromOrder(order);
        orderNotificationService.sendTransactionUpiStatusWebSocketNotification(
                Locale.ENGLISH, restaurantId, transaction.getId(), TransactionStatus.PENDING);

        String errorType = OMISE_STATUS_FAILED.equalsIgnoreCase(status) ? "PAYMENT_FAILED" : "PAYMENT_EXPIRED";
        String errorMessage = OMISE_STATUS_FAILED.equalsIgnoreCase(status)
                ? "Payment failed for this transaction"
                : "Payment expired for this transaction";

        notifyCashierPaymentErrorBestEffort(transaction, errorType, errorMessage);
        notifyAssignedWaitersPaymentErrorBestEffort(order, transaction, errorType, errorMessage, orderId);
    }

    private void generateReceiptIfMissingBestEffort(Order order, Transaction transaction, UUID orderId) {
        try {
            if (transaction.getReceiptUrl() != null && !transaction.getReceiptUrl().isBlank()) {
                log.info("Receipt already exists for transaction {}, skipping regeneration", transaction.getId());
                return;
            }
            List<OrderedItem> orderedItems = orderedItemRepository.findByOrderId(orderId);
            String receiptUrl = receiptService.generateReceiptPdf(order, transaction, order.getRestaurant(), orderedItems);
            transaction.setReceiptUrl(receiptUrl);
            transactionRepository.save(transaction);
        } catch (Exception e) {
            log.error("Error in post-payment processing for order: {}", orderId, e);
        }
    }

    private void sendReceiptEmailIfPresentBestEffort(Order order, Transaction transaction, Locale receiptLocale, UUID orderId) {
        if (order.getEmail() == null || order.getEmail().trim().isEmpty()) {
            log.info("No email found in Order for UPI payment - skipping email sending. Order: {}", orderId);
            return;
        }
        try {
            log.info("Email found in Order for UPI payment: '{}' - proceeding to send receipt email", order.getEmail());
            receiptService.sendReceiptEmail(order.getEmail(), order, transaction, receiptLocale);
            log.info("UPI receipt email sent successfully for order: {}", orderId);
        } catch (Exception e) {
            log.error("Failed to send receipt email for UPI payment order: {}", orderId, e);
        }
    }

    private void autoPushItemsIfNeededBestEffort(Order order, Transaction transaction, UUID orderId) {
        try {
            orderRecalculationService.pushNonPushedOrderedItemsAfterPrepaidPaymentIfApplicable(
                    order, transaction, orderId, Locale.ENGLISH);
        } catch (Exception e) {
            log.error("Failed to auto-push item statuses after UPI payment for order {}: {}", orderId, e.getMessage(), e);
        }
    }

    private void evaluateAlertsAfterCommitBestEffort(Order order, Locale userLocale) {
        final Restaurant restaurant = order.getRestaurant();
        if (restaurant == null) {
            log.warn("⚠️ Cannot evaluate alerts: restaurant is null for order: {}", order.getId());
            return;
        }

        if (restaurantAlertEvaluationService == null) {
            log.warn("⚠️ RestaurantAlertEvaluationService is null, skipping alert evaluation for restaurant: {}",
                    restaurant.getRestaurantCode());
            return;
        }

        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    evaluateRestaurantAlertsRealtimeWithLogs(
                            restaurant,
                            userLocale,
                            " after UPI payment transaction commit",
                            " after UPI payment transaction commit");
                }
            });
            log.info("📋 Registered alert evaluation to run after transaction commit for restaurant: {}", restaurant.getRestaurantCode());
            return;
        }

        evaluateRestaurantAlertsRealtimeWithLogs(
                restaurant,
                userLocale,
                " (no active transaction)",
                "");
    }

    private void evaluateRestaurantAlertsRealtimeWithLogs(
            Restaurant restaurant,
            Locale userLocale,
            String startLogSuffix,
            String errorLogSuffix) {
        try {
            log.info("🔔 Triggering alert evaluation for restaurant: {}{}", restaurant.getRestaurantCode(), startLogSuffix);
            restaurantAlertEvaluationService.evaluateRestaurantAlertsRealtime(restaurant, userLocale);
            log.info("✅ Alert evaluation completed for restaurant: {}", restaurant.getRestaurantCode());
        } catch (Exception e) {
            if (errorLogSuffix.isEmpty()) {
                log.error("❌ Failed to evaluate real-time alerts: {}", e.getMessage(), e);
            } else {
                log.error("❌ Failed to evaluate real-time alerts{}: {}", errorLogSuffix, e.getMessage(), e);
            }
        }
    }

    private void createPaymentAuditTrailBestEffort(Order order, Transaction transaction) {
        try {
            User cashier = transaction.getCashier();
            if (cashier == null) {
                log.warn("Cashier is null for transaction {} - skipping audit trail creation", transaction.getId());
                return;
            }
            String transactionNumber = transaction.getTransactionNumber();
            auditTrailService.createAuditTrail(
                    cashier,
                    ActionType.PAYMENT,
                    order.getRestaurant(),
                    RequestStatus.NA,
                    null,
                    null,
                    transaction.getId(),
                    "TRANSACTION",
                    String.format("UPI payment processed: Method %s, Amount %s, Transaction Number %s",
                            transaction.getPaymentMethod(), transaction.getTransactionAmount(), transactionNumber)
            );
            log.info("Created audit trail for UPI payment: transaction {}", transaction.getId());
        } catch (Exception e) {
            log.error("Failed to create audit trail for UPI payment: {}", e.getMessage());
        }
    }

    private void notifyCashierPaymentCompletedBestEffort(Order order, Transaction transaction, UUID orderId) {
        try {
            User cashier = transaction.getCashier();
            if (cashier == null) {
                log.warn("Cashier is null for transaction {} - skipping payment completion notification to cashier",
                        transaction.getId());
                return;
            }
            notificationService.notifyPaymentCompleted(order, cashier, transaction.getPaymentMethod(),
                    transaction.getTransactionAmount(), Locale.ENGLISH);
            log.info("Sent payment completion notification to cashier {} for UPI payment order {}",
                    cashier.getId(), orderId);
        } catch (Exception e) {
            log.error("Failed to send payment completion notification to cashier for UPI payment: {}", e.getMessage(), e);
        }
    }

    private void notifyAssignedWaitersPaymentCompletedBestEffort(Order order, Transaction transaction, UUID orderId) {
        try {
            if (order.getRestaurantTable() == null) {
                return;
            }
            User cashier = transaction.getCashier();
            List<User> assignedWaiters = orderValidationService.getWaitersForTable(order.getRestaurantTable());
            if (assignedWaiters == null || assignedWaiters.isEmpty()) {
                log.warn("No waiters assigned to table {} for order {} - skipping payment completion notification",
                        order.getRestaurantTable().getTableOrder(), orderId);
                return;
            }
            int notifiedCount = 0;
            for (User waiter : assignedWaiters) {
                if (waiter != null && (cashier == null || !waiter.getId().equals(cashier.getId()))) {
                    notifiedCount += notifyWaiterPaymentCompletedBestEffort(order, waiter, transaction);
                }
            }
            log.info("Sent payment completion notifications to {} waiter(s) for UPI payment order {} at table {}",
                    notifiedCount, orderId, order.getRestaurantTable().getTableOrder());
        } catch (Exception e) {
            log.error("Failed to send payment completion notification for UPI payment: {}", e.getMessage(), e);
        }
    }

    private void notifyCashierPaymentErrorBestEffort(Transaction transaction, String errorType, String errorMessage) {
        try {
            User cashier = transaction.getCashier();
            if (cashier == null) {
                log.warn("Cashier is null for transaction {} - cannot send payment error notification", transaction.getId());
                return;
            }
            notificationService.notifyPaymentError(cashier, transaction, errorType, errorMessage, Locale.ENGLISH);
            log.info("Sent payment error notification to cashier {} for failed/expired UPI payment: transaction {}",
                    cashier.getId(), transaction.getId());
        } catch (Exception e) {
            log.error("Failed to send payment error notification for failed/expired UPI payment: {}", e.getMessage(), e);
        }
    }

    private void notifyAssignedWaitersPaymentErrorBestEffort(Order order,
                                                             Transaction transaction,
                                                             String errorType,
                                                             String errorMessage,
                                                             UUID orderId) {
        try {
            if (order.getRestaurantTable() == null) {
                return;
            }
            User cashier = transaction.getCashier();
            List<User> assignedWaiters = orderValidationService.getWaitersForTable(order.getRestaurantTable());
            if (assignedWaiters == null || assignedWaiters.isEmpty()) {
                log.warn("No waiters assigned to table {} for order {} - skipping payment error notification to waiters",
                        order.getRestaurantTable().getTableOrder(), orderId);
                return;
            }
            int notifiedCount = 0;
            for (User waiter : assignedWaiters) {
                if (waiter != null && (cashier == null || !waiter.getId().equals(cashier.getId()))) {
                    notifiedCount += notifyWaiterPaymentErrorBestEffort(waiter, transaction, errorType, errorMessage);
                }
            }
            log.info("Sent payment error notifications to {} waiter(s) for failed/expired UPI payment: transaction {}",
                    notifiedCount, transaction.getId());
        } catch (Exception e) {
            log.error("Failed to send payment error notification to waiters for failed/expired UPI payment: {}", e.getMessage(), e);
        }
    }

    private int notifyWaiterPaymentCompletedBestEffort(Order order, User waiter, Transaction transaction) {
        try {
            notificationService.notifyPaymentCompleted(order, waiter, transaction.getPaymentMethod(),
                    transaction.getTransactionAmount(), Locale.ENGLISH);
            return 1;
        } catch (Exception e) {
            log.error("Failed to send payment completion notification to waiter {}: {}",
                    waiter.getId(), e.getMessage(), e);
            return 0;
        }
    }

    private int notifyWaiterPaymentErrorBestEffort(User waiter,
                                                   Transaction transaction,
                                                   String errorType,
                                                   String errorMessage) {
        try {
            notificationService.notifyPaymentErrorToWaiter(waiter, transaction, errorType, errorMessage, Locale.ENGLISH);
            return 1;
        } catch (Exception e) {
            log.error("Failed to send payment error notification to waiter {}: {}",
                    waiter.getId(), e.getMessage(), e);
            return 0;
        }
    }
}
