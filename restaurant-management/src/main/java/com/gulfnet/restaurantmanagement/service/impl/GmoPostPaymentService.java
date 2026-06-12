package com.gulfnet.restaurantmanagement.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.gulfnet.restaurantmanagement.config.OnlineCardPaymentProperties;
import com.gulfnet.restaurantmanagement.service.AuditTrailService;
import com.gulfnet.restaurantmanagement.service.OrderRecalculationService;
import com.gulfnet.restaurantmanagement.service.OrderNotificationService;
import com.gulfnet.restaurantmanagement.service.OrderValidationService;
import com.gulfnet.restaurantmanagement.service.ReceiptService;
import com.gulfnet.restaurantmanagement.service.RestaurantAlertEvaluationService;
import com.gulfnet.restaurantmanagement.service.NotificationService;
import com.gulfnet.shared_library.entity.Order;
import com.gulfnet.shared_library.entity.OrderedItem;
import com.gulfnet.shared_library.entity.Restaurant;
import com.gulfnet.shared_library.entity.Transaction;
import com.gulfnet.shared_library.entity.User;
import com.gulfnet.shared_library.enums.*;
import com.gulfnet.shared_library.repository.AuditTrailRepository;
import com.gulfnet.shared_library.repository.OrderRepository;
import com.gulfnet.shared_library.repository.OrderedItemRepository;
import com.gulfnet.shared_library.repository.OrderedComboRepository;
import com.gulfnet.shared_library.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class GmoPostPaymentService {

    private final TransactionRepository transactionRepository;
    private final AuditTrailRepository auditTrailRepository;
    private final OrderRepository orderRepository;
    private final OrderedItemRepository orderedItemRepository;
    private final OrderedComboRepository orderedComboRepository;
    private final ReceiptService receiptService;
    private final NotificationService notificationService;
    private final AuditTrailService auditTrailService;
    private final OrderValidationService orderValidationService;
    private final OrderRecalculationService orderRecalculationService;
    private final RestaurantAlertEvaluationService restaurantAlertEvaluationService;
    private final OrderNotificationService orderNotificationService;
    private final OnlineCardPaymentProperties onlineCardPaymentProperties;

    private static User userReference(UUID userId) {
        User ref = new User();
        ref.setId(userId);
        return ref;
    }

    /**
     * Completes the post-authorization flow after a successful GMO/UPI payment: marks the
     * {@link Transaction} {@link TransactionStatus#COMPLETED}, notifies clients over websocket, generates
     * and stores a receipt PDF (if missing), emails the receipt when the order has an email, may auto-push
     * {@link OrderedItem} rows to {@link ItemStatus#PUSHED} for prepaid dine-in or takeaway per chain
     * configuration, recalculates {@link OrderStatus}, writes a {@link ActionType#PAYMENT} audit entry,
     * notifies cashier and table waiters, and schedules {@link RestaurantAlertEvaluationService}
     * after transaction commit when a Spring transaction is active.
     *
     * @param transactionId persisted transaction to finalize; no-op with a warning if missing
     * @param locale        locale for user-visible notifications and messaging
     * @param resultNode    GMO callback JSON (reserved for future use; not read by this method)
     */
    @Transactional
    public void handleSuccess(UUID transactionId, Locale locale, JsonNode resultNode) {
        log.info("[GMO] (post) Handling success for transactionId={}", transactionId);

        Transaction transaction = transactionRepository.findByIdWithOrderAndTable(transactionId)
                .or(() -> transactionRepository.findById(transactionId))
                .orElse(null);
        if (transaction == null) {
            log.warn("[GMO] (post) Transaction not found after completion claim: {}", transactionId);
            return;
        }

        // Idempotent status update: if already COMPLETED (e.g., previous notify/poll), keep status and continue
        // with side effects; otherwise, mark as COMPLETED and clear hosted payment metadata.
        TransactionStatus currentStatus = transaction.getTransactionStatus();
        if (currentStatus != TransactionStatus.COMPLETED) {
            OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
            transaction.setTransactionStatus(TransactionStatus.COMPLETED);
            transaction.setGmoHostedPaymentUrl(null);
            transaction.setGmoHostedPaymentLinkCreatedAt(null);
            transaction.setUpdatedAt(now);
            transactionRepository.save(transaction);
            log.info("[GMO] (post) Transaction {} marked COMPLETED from status {}", transactionId, currentStatus);
        } else {
            log.info("[GMO] (post) Transaction {} already COMPLETED; running side effects idempotently", transactionId);
        }

        Order order = transaction.getOrder();
        if (order == null) {
            log.warn("[GMO] (post) Order is null for transaction {}", transactionId);
            return;
        }

        UUID orderId = order.getId();

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
            log.error("[GMO] Failed to mark includedInPayment for order {}: {}", orderId, e.getMessage(), e);
        }

        // Broadcast UPI transaction completion to restaurant-scoped subscribers.
        try {
            UUID restaurantId = orderNotificationService.getRestaurantIdFromOrder(order);
            orderNotificationService.sendTransactionUpiStatusWebSocketNotification(
                    locale, restaurantId, transaction.getId(), TransactionStatus.COMPLETED);
        } catch (Exception e) {
            log.error("[GMO] Failed to send UPI transaction status websocket update for transaction {}: {}",
                    transactionId, e.getMessage(), e);
        }

        // Receipt generation
        try {
            if (transaction.getReceiptUrl() == null || transaction.getReceiptUrl().isBlank()) {
                List<OrderedItem> orderedItems = orderedItemRepository.findByOrderId(orderId);
                String receiptUrl = receiptService.generateReceiptPdf(order, transaction, order.getRestaurant(), orderedItems);
                transaction.setReceiptUrl(receiptUrl);
                transactionRepository.save(transaction);
            }
        } catch (Exception e) {
            log.error("[GMO] Error generating receipt after success for order {}", orderId, e);
        }

        // Email
        try {
            String email = order.getEmail();
            if (email != null && !email.trim().isEmpty()) {
                receiptService.sendReceiptEmail(email, order, transaction, receiptService.receiptLocaleFromChainConfig());
            }
        } catch (Exception e) {
            log.error("[GMO] Failed to send receipt email for order {}", orderId, e);
        }

        // Auto-push items / KDS
        try {
            orderRecalculationService.pushNonPushedOrderedItemsAfterPrepaidPaymentIfApplicable(order, transaction, orderId, locale);
        } catch (Exception e) {
            log.error("[GMO] Failed to auto-push item statuses after GMO payment for order {}: {}", orderId, e.getMessage(), e);
        }

        // Audit trail (+ cashier notification for staff-initiated GMO UPI only)
        boolean customerInitiated = transaction.getPaymentInitiatorType() != null
                && transaction.getPaymentInitiatorType() == Transaction.PAYMENT_INITIATOR_CUSTOMER;
        try {
            if (auditTrailRepository.existsByEntityIdAndActionType(transaction.getId(), ActionType.PAYMENT)) {
                log.info("[GMO] (post) PAYMENT audit already exists for tx {}; skipping duplicate audit",
                        transactionId);
            } else {
                String transactionNumber = transaction.getTransactionNumber();
                if (customerInitiated) {
                    UUID onlineCardActorId = orderValidationService.resolveOnlineCardCashierId(locale);
                    String actorLabel = onlineCardPaymentProperties.getUserName() != null
                            && !onlineCardPaymentProperties.getUserName().isBlank()
                            ? onlineCardPaymentProperties.getUserName().trim()
                            : "Online Card Payment";
                    User onlineCardActor = userReference(onlineCardActorId);
                    auditTrailService.createAuditTrail(
                            onlineCardActor,
                            ActionType.PAYMENT,
                            order.getRestaurant(),
                            RequestStatus.NA,
                            null,
                            null,
                            transaction.getId(),
                            "TRANSACTION",
                            String.format("Customer card payment via GMO (%s, id=%s): Method %s, Amount %s, Transaction Number %s",
                                    actorLabel, onlineCardActorId, transaction.getPaymentMethod(),
                                    transaction.getTransactionAmount(), transactionNumber)
                    );
                    log.info("[GMO] Audit trail created for customer card payment txId={}, actorId={}, actorLabel={}",
                            transactionId, onlineCardActorId, actorLabel);
                } else {
                    User cashier = transaction.getCashier();
                    if (cashier != null) {
                        String wallet = transaction.getPaymentApp();
                        String walletLabel = wallet != null && !wallet.isBlank() ? wallet : "UPI";
                        auditTrailService.createAuditTrail(
                                cashier,
                                ActionType.PAYMENT,
                                order.getRestaurant(),
                                RequestStatus.NA,
                                null,
                                null,
                                transaction.getId(),
                                "TRANSACTION",
                                String.format("UPI payment processed via GMO (%s): Method %s, Amount %s, Transaction Number %s",
                                        walletLabel, transaction.getPaymentMethod(), transaction.getTransactionAmount(),
                                        transactionNumber)
                        );
                        notificationService.notifyPaymentCompleted(order, cashier, transaction.getPaymentMethod(),
                                transaction.getTransactionAmount(), locale);
                        log.info("[GMO] Audit trail created for GMO UPI payment txId={}, cashierId={}, wallet={}",
                                transactionId, cashier.getId(), walletLabel);
                    } else {
                        log.warn("[GMO] Skipped audit trail for GMO UPI payment txId={}: cashier not set on transaction",
                                transactionId);
                    }
                }
            }
        } catch (Exception e) {
            log.error("[GMO] Failed to create audit trail / notify cashier for GMO payment: {}", e.getMessage(), e);
        }

        // Waiter notifications
        try {
            notifyWaitersPaymentCompletedForGmo(order, transaction, locale);
        } catch (Exception e) {
            log.error("[GMO] Failed to send waiter notifications for GMO payment: {}", e.getMessage(), e);
        }

        // Alert evaluation
        try {
            final Restaurant restaurant = order.getRestaurant();
            final Locale finalLocale = locale;
            if (restaurant != null && restaurantAlertEvaluationService != null) {
                if (TransactionSynchronizationManager.isActualTransactionActive()) {
                    TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                        @Override
                        public void afterCommit() {
                            try {
                                restaurantAlertEvaluationService.evaluateRestaurantAlertsRealtime(restaurant, finalLocale);
                            } catch (Exception e) {
                                log.error("[GMO] Failed to evaluate real-time alerts after GMO payment commit: {}", e.getMessage(), e);
                            }
                        }
                    });
                } else {
                    restaurantAlertEvaluationService.evaluateRestaurantAlertsRealtime(restaurant, finalLocale);
                }
            }
        } catch (Exception e) {
            log.error("[GMO] Error during alert evaluation after GMO payment: {}", e.getMessage(), e);
        }
    }

    /**
     * Notifies all waiters assigned to the order's table that GMO card payment completed.
     *
     * @param order         paid order (ignored when not tied to a table)
     * @param transaction   completed transaction carrying amount and method
     * @param locale        locale for notification content
     */
    private void notifyWaitersPaymentCompletedForGmo(Order order, Transaction transaction, Locale locale) {
        if (order.getRestaurantTable() == null) {
            return;
        }
        List<User> assignedWaiters = orderValidationService.getWaitersForTable(order.getRestaurantTable());
        if (assignedWaiters == null || assignedWaiters.isEmpty()) {
            return;
        }
        for (User waiter : assignedWaiters) {
            notifySingleWaiterPaymentCompleted(order, waiter, transaction, locale);
        }
    }

    /**
     * Sends the payment-completed notification to a single waiter, logging and swallowing delivery errors.
     */
    private void notifySingleWaiterPaymentCompleted(Order order, User waiter, Transaction transaction, Locale locale) {
        if (waiter == null) {
            return;
        }
        try {
            notificationService.notifyPaymentCompleted(order, waiter, transaction.getPaymentMethod(),
                    transaction.getTransactionAmount(), locale);
        } catch (Exception e) {
            log.error("[GMO] Failed to send payment completion notification to waiter {}: {}",
                    waiter.getId(), e.getMessage(), e);
        }
    }
}

