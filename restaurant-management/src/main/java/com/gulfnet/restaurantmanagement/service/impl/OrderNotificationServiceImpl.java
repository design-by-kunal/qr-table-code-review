package com.gulfnet.restaurantmanagement.service.impl;

import com.gulfnet.restaurantmanagement.config.RabbitMQConfig;
import com.gulfnet.restaurantmanagement.service.NotificationService;
import com.gulfnet.restaurantmanagement.service.OrderNotificationService;
import com.gulfnet.restaurantmanagement.service.OrderValidationService;
import com.gulfnet.restaurantmanagement.util.MessageUtil;
import com.gulfnet.shared_library.entity.Order;
import com.gulfnet.shared_library.entity.OrderedCombo;
import com.gulfnet.shared_library.entity.OrderedItem;
import com.gulfnet.shared_library.entity.RestaurantTable;
import com.gulfnet.shared_library.entity.TableAssignment;
import com.gulfnet.shared_library.entity.User;
import com.gulfnet.shared_library.enums.ItemStatus;
import com.gulfnet.shared_library.enums.OrderStatus;
import com.gulfnet.shared_library.enums.TransactionStatus;
import com.gulfnet.shared_library.model.request.StatusEventMessage;
import com.gulfnet.shared_library.repository.OrderRepository;
import com.gulfnet.shared_library.repository.OrderedComboRepository;
import com.gulfnet.shared_library.repository.OrderedItemRepository;
import com.gulfnet.shared_library.repository.TableAssignmentRepository;
import com.gulfnet.shared_library.repository.TransactionRepository;
import com.gulfnet.shared_library.repository.UserRepository;
import com.gulfnet.shared_library.entity.Transaction;
import java.util.Objects;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.Hibernate;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrderNotificationServiceImpl implements OrderNotificationService {

    // String literal constants to avoid duplication
    private static final String KEY_ORDER_ID = "orderId";
    private static final String KEY_STATUS = "status";
    private static final String KEY_NOTIFICATION_TYPE = "notificationType";
    private static final String KEY_RESTAURANT_ID = "restaurantId";
    private static final String TOPIC_RESTAURANT_PREFIX = "/topic/restaurant/";
    private static final String KEY_TIMESTAMP = "timestamp";
    private static final String NOTIF_ORDER_STATUS_UPDATE = "ORDER_STATUS_UPDATE";
    private static final String ITEM_TYPE_COMBO = "combo";
    private static final String KEY_TRANSACTION_ID = "transactionId";
    private static final String NOTIF_TRANSACTION_STATUS_UPDATE = "TRANSACTION_STATUS_UPDATE";

    private final SimpMessagingTemplate messagingTemplate;
    private final MessageUtil messageUtil;
    private final RabbitTemplate rabbitTemplate;
    private final OrderedItemRepository orderedItemRepository;
    private final OrderedComboRepository orderedComboRepository;
    private final OrderRepository orderRepository;
    private final UserRepository userRepository;
    private final NotificationService notificationService;
    private final TableAssignmentRepository tableAssignmentRepository;
    private final TransactionRepository transactionRepository;
    private final OrderValidationService orderValidationService;

    /**
     * Sends a WebSocket notification for item status updates to all subscribers of the restaurant's item-status topic.
     * Also publishes the notification to RabbitMQ for integration service logging.
     *
     * @param userLocale Locale for message localization
     * @param restaurantId UUID of the restaurant
     * @param itemId UUID of the item (ordered item or combo)
     * @param newStatus New status of the item
     * @param itemType Type of item ("item" or "combo")
     */
    @Override
    public void sendItemStatusWebSocketNotification(Locale userLocale, UUID restaurantId, UUID itemId, ItemStatus newStatus, String itemType) {
        // ON_HOLD is an intermediate waiter-side state and should not trigger broadcast popups.
        // KDS users should only be notified when the line is pushed/processed in KDS workflow.
        if (newStatus == ItemStatus.ON_HOLD) {
            log.debug("Skipping WebSocket broadcast for {} {} with ON_HOLD status", itemType, itemId);
            return;
        }

        // Validate required parameters
        if (restaurantId == null) {
            log.error("Cannot send WebSocket notification for {} status update: restaurantId is null for item {}", itemType, itemId);
            return;
        }
        if (itemId == null) {
            log.error("Cannot send WebSocket notification for {} status update: itemId is null", itemType);
            return;
        }
        if (newStatus == null) {
            log.error("Cannot send WebSocket notification for {} status update: newStatus is null for item {}", itemType, itemId);
            return;
        }
        
        try {
            String topic = TOPIC_RESTAURANT_PREFIX + restaurantId + "/item-status";
            Map<String, Object> itemData = new HashMap<>();
            itemData.put("itemId", itemId.toString());
            itemData.put("itemType", itemType); // "item" or "combo"
            itemData.put(KEY_STATUS, newStatus.toString());
            itemData.put(KEY_RESTAURANT_ID, restaurantId.toString());
            itemData.put(KEY_NOTIFICATION_TYPE, "ITEM_STATUS_UPDATE");
            itemData.put(KEY_TIMESTAMP, LocalDateTime.now(ZoneOffset.UTC).format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));

            final String wsTitle;
            final String wsMessage;
            if (newStatus == ItemStatus.PUSHED) {
                String displayName = pushedLineLabelForMessage(itemId, itemType, userLocale);
                wsTitle = messageUtil.getMessage("notification.item.pushed.title", userLocale);
                wsMessage = messageUtil.getMessage("notification.item.pushed.body", userLocale, displayName);
            } else if (newStatus == ItemStatus.COOKING || newStatus == ItemStatus.READY || newStatus == ItemStatus.DELAYED
                    || newStatus == ItemStatus.SERVED) {
                // Match user-scoped KDS copy; broadcast subscribers otherwise only got generic item.status.updated.
                String displayName = pushedLineLabelForMessage(itemId, itemType, userLocale);
                if (newStatus == ItemStatus.COOKING) {
                    wsTitle = messageUtil.getMessage("notification.kds.cooking.title", userLocale);
                    wsMessage = messageUtil.getMessage("notification.kds.cooking.body", userLocale, displayName);
                } else if (newStatus == ItemStatus.READY) {
                    wsTitle = messageUtil.getMessage("notification.kds.ready.title", userLocale);
                    wsMessage = messageUtil.getMessage("notification.kds.ready.body", userLocale, displayName);
                } else if (newStatus == ItemStatus.DELAYED) {
                    wsTitle = messageUtil.getMessage("notification.kds.delayed.title", userLocale);
                    wsMessage = messageUtil.getMessage("notification.kds.delayed.body", userLocale, displayName);
                } else {
                    wsTitle = messageUtil.getMessage("notification.kds.served.title", userLocale);
                    wsMessage = messageUtil.getMessage("notification.kds.served.body", userLocale, displayName);
                }
            } else {
                wsTitle = messageUtil.getMessage("notification.item.status.update.title", userLocale);
                wsMessage = messageUtil.getMessage("item.status.updated", userLocale);
            }
            
            StatusEventMessage eventMessage = StatusEventMessage.builder()
                    .title(wsTitle)
                    .message(wsMessage)
                    .notificationType("ITEM_STATUS_UPDATE")
                    .itemId(itemId.toString())
                    .status(newStatus.toString())
                    .data(itemData)
                    .build();
            messagingTemplate.convertAndSend(topic, eventMessage);
            log.info("[Notification][WebSocket] broadcast topic={} notificationType=ITEM_STATUS_UPDATE itemType={} itemId={} status={} restaurantId={}",
                    topic, itemType, itemId, newStatus, restaurantId);
            
            // Also publish to RabbitMQ for integration service to log
            publishToRabbitMQ(topic, eventMessage);
        } catch (Exception e) {
            log.error("Failed to send WebSocket notification for {} status update: itemId={}, status={}, restaurantId={}, error={}", 
                    itemType, itemId, newStatus, restaurantId, e.getMessage(), e);
        }
    }

    /**
     * Resolved display name for PUSHED/KDS broadcast lines: user locale, then configured languages only.
     * Missing translations surface as {@link ResponseStatusException} (404) from {@link OrderValidationService}.
     */
    private String resolvePushedLineDisplayName(UUID itemId, String itemType, Locale locale) {
        try {
            if (ITEM_TYPE_COMBO.equals(itemType)) {
                // Notification label resolution only needs combo translations; avoid heavy fetch joins.
                var ocOpt = orderedComboRepository.findById(itemId);
                if (ocOpt.isEmpty() || ocOpt.get().getCombo() == null) {
                    throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                            messageUtil.getMessage("combo.name.not.found", locale));
                }
                return orderValidationService.getComboName(ocOpt.get().getCombo(), locale);
            }
            var oiOpt = orderedItemRepository.findByIdWithWaiterInfo(itemId);
            if (oiOpt.isEmpty() || oiOpt.get().getItem() == null) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                        messageUtil.getMessage("item.name.not.found", locale));
            }
            return orderValidationService.getItemName(oiOpt.get().getItem(), locale);
        } catch (ResponseStatusException e) {
            throw e;
        } catch (Exception e) {
            log.debug("resolvePushedLineDisplayName failed for {} {}: {}", itemType, itemId, e.getMessage());
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    messageUtil.getMessage("error.internal", locale), e);
        }
    }

    /** Non-null line label for message templates (resolved name or empty). */
    private String pushedLineLabelForMessage(UUID itemId, String itemType, Locale locale) {
        String resolved = resolvePushedLineDisplayName(itemId, itemType, locale);
        return resolved != null ? resolved : "";
    }

    /**
     * Sends a WebSocket notification for order status updates to all subscribers of the restaurant's order-status topic.
     * Also publishes the notification to RabbitMQ for integration service logging.
     *
     * @param userLocale Locale for message localization
     * @param restaurantId UUID of the restaurant
     * @param orderId UUID of the order
     * @param newStatus New status of the order
     */
    @Override
    public void sendOrderStatusWebSocketNotification(Locale userLocale, UUID restaurantId, UUID orderId, OrderStatus newStatus) {
        try {
            String topic = TOPIC_RESTAURANT_PREFIX + restaurantId + "/order-status";
            Map<String, Object> orderData = new HashMap<>();
            orderData.put(KEY_ORDER_ID, orderId.toString());
            orderData.put(KEY_STATUS, newStatus.toString());
            orderData.put(KEY_RESTAURANT_ID, restaurantId.toString());
            orderData.put(KEY_NOTIFICATION_TYPE, NOTIF_ORDER_STATUS_UPDATE);
            orderData.put(KEY_TIMESTAMP, LocalDateTime.now(ZoneOffset.UTC).format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
            
            StatusEventMessage eventMessage = StatusEventMessage.builder()
                    .title(messageUtil.getMessage("notification.order.updated.title", userLocale))
                    .message(messageUtil.getMessage("order.update.success", userLocale))
                    .notificationType(NOTIF_ORDER_STATUS_UPDATE)
                    .orderId(orderId.toString())
                    .status(newStatus.toString())
                    .data(orderData)
                    .build();
            messagingTemplate.convertAndSend(topic, eventMessage);
            log.info("[Notification][WebSocket] broadcast topic={} notificationType={} orderId={} status={} restaurantId={}",
                    topic, NOTIF_ORDER_STATUS_UPDATE, orderId, newStatus, restaurantId);
            
            // Also publish to RabbitMQ for integration service to log
            publishToRabbitMQ(topic, eventMessage);
        } catch (Exception e) {
            log.error("Failed to send WebSocket notification for order status update: {}", e.getMessage());
        }
    }

    /**
     * Sends a WebSocket notification for transaction status updates to all subscribers of the restaurant's transaction-status topic.
     * Fetches the associated order ID from the transaction and includes it in the notification.
     * Also publishes the notification to RabbitMQ for integration service logging.
     *
     * @param userLocale Locale for message localization
     * @param restaurantId UUID of the restaurant
     * @param transactionId UUID of the transaction
     * @param newStatus New status of the transaction
     */
    @Override
    public void sendTransactionStatusWebSocketNotification(Locale userLocale, UUID restaurantId, UUID transactionId, TransactionStatus newStatus) {
        try {
            String topic = TOPIC_RESTAURANT_PREFIX + restaurantId + "/transaction-status";
            
            // Get order ID from transaction if available
            String orderId = fetchOrderIdFromTransaction(transactionId);
            
            Map<String, Object> transactionData = new HashMap<>();
            transactionData.put(KEY_TRANSACTION_ID, transactionId.toString());
            transactionData.put(KEY_STATUS, newStatus.toString());
            transactionData.put(KEY_RESTAURANT_ID, restaurantId.toString());
            transactionData.put(KEY_NOTIFICATION_TYPE, NOTIF_TRANSACTION_STATUS_UPDATE);
            if (orderId != null) {
                transactionData.put(KEY_ORDER_ID, orderId);
            }
            transactionData.put(KEY_TIMESTAMP, LocalDateTime.now(ZoneOffset.UTC).format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
            
            StatusEventMessage eventMessage = StatusEventMessage.builder()
                    .title(messageUtil.getMessage("notification.transaction.status.update.title", userLocale))
                    .message(messageUtil.getMessage("transaction.status.updated", userLocale))
                    .notificationType(NOTIF_TRANSACTION_STATUS_UPDATE)
                    .status(newStatus.toString())
                    .orderId(orderId)
                    .data(transactionData)
                    .build();
            messagingTemplate.convertAndSend(topic, eventMessage);
            log.info("[Notification][WebSocket] broadcast topic={} notificationType={} transactionId={} status={} restaurantId={} orderId={}",
                    topic, NOTIF_TRANSACTION_STATUS_UPDATE, transactionId, newStatus, restaurantId, orderId);
            
            // Also publish to RabbitMQ for integration service to log
            publishToRabbitMQ(topic, eventMessage);
        } catch (Exception e) {
            log.error("Failed to send WebSocket notification for transaction status update: {}", e.getMessage());
        }
    }

    /**
     * Publishes a checkout-initiated transaction status event on the restaurant WebSocket topic and to RabbitMQ.
     *
     * @param userLocale     locale for notification title/body
     * @param restaurantId   restaurant scope for the STOMP topic
     * @param transactionId  transaction being reported
     * @param order            order context when known (order id embedded in payload)
     * @param newStatus        next transaction status
     */
    @Override
    public void sendCheckoutInitiatedTransactionStatusWebSocketNotification(Locale userLocale, UUID restaurantId,
            UUID transactionId, Order order, TransactionStatus newStatus) {
        try {
            String topic = TOPIC_RESTAURANT_PREFIX + restaurantId + "/transaction-status";
            String orderIdStr = order != null && order.getId() != null
                    ? order.getId().toString()
                    : fetchOrderIdFromTransaction(transactionId);

            Map<String, Object> transactionData = new HashMap<>();
            transactionData.put(KEY_TRANSACTION_ID, transactionId.toString());
            transactionData.put(KEY_STATUS, newStatus.toString());
            transactionData.put(KEY_RESTAURANT_ID, restaurantId.toString());
            transactionData.put(KEY_NOTIFICATION_TYPE, NOTIF_TRANSACTION_STATUS_UPDATE);
            if (orderIdStr != null) {
                transactionData.put(KEY_ORDER_ID, orderIdStr);
            }
            transactionData.put(KEY_TIMESTAMP, LocalDateTime.now(ZoneOffset.UTC).format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));

            String title = messageUtil.getMessage("notification.checkout.initiated.transaction.title", userLocale);
            String message = messageUtil.getMessage("notification.checkout.initiated.transaction.body", userLocale);

            StatusEventMessage eventMessage = StatusEventMessage.builder()
                    .title(title)
                    .message(message)
                    .notificationType(NOTIF_TRANSACTION_STATUS_UPDATE)
                    .status(newStatus.toString())
                    .orderId(orderIdStr)
                    .data(transactionData)
                    .build();
            messagingTemplate.convertAndSend(topic, eventMessage);
            log.info("[Notification][WebSocket] broadcast topic={} notificationType={} transactionId={} status={} restaurantId={} orderId={}",
                    topic, NOTIF_TRANSACTION_STATUS_UPDATE, transactionId, newStatus, restaurantId, orderIdStr);

            publishToRabbitMQ(topic, eventMessage);
        } catch (Exception e) {
            log.error("Failed to send WebSocket notification for checkout-initiated transaction: {}", e.getMessage());
        }
    }

    /**
     * Sends a WebSocket notification for UPI transaction status updates to all subscribers of the restaurant's transaction-upi-status topic.
     * Uses custom messages based on transaction status (completed vs failed).
     * Fetches the associated order ID from the transaction and includes it in the notification.
     * Also publishes the notification to RabbitMQ for integration service logging.
     *
     * @param userLocale Locale for message localization
     * @param restaurantId UUID of the restaurant
     * @param transactionId UUID of the transaction
     * @param newStatus New status of the transaction
     */
    @Override
    public void sendTransactionUpiStatusWebSocketNotification(Locale userLocale, UUID restaurantId, UUID transactionId, TransactionStatus newStatus) {
        try {
            String topic = TOPIC_RESTAURANT_PREFIX + restaurantId + "/transaction-upi-status";
            
            // Get order ID from transaction if available
            String orderId = fetchOrderIdFromTransaction(transactionId);
            
            Map<String, Object> transactionData = new HashMap<>();
            transactionData.put(KEY_TRANSACTION_ID, transactionId.toString());
            transactionData.put(KEY_STATUS, newStatus.toString());
            transactionData.put(KEY_RESTAURANT_ID, restaurantId.toString());
            transactionData.put(KEY_NOTIFICATION_TYPE, "TRANSACTION_UPI_STATUS_UPDATE");
            if (orderId != null) {
                transactionData.put(KEY_ORDER_ID, orderId);
            }
            transactionData.put(KEY_TIMESTAMP, LocalDateTime.now(ZoneOffset.UTC).format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
            
            // Custom messages based on status
            String message;
            if (newStatus == TransactionStatus.COMPLETED) {
                message = messageUtil.getMessage("transaction.upi.completed.successfully", userLocale);
            } else {
                message = messageUtil.getMessage("transaction.upi.failed", userLocale);
            }
            
            StatusEventMessage eventMessage = StatusEventMessage.builder()
                    .title(messageUtil.getMessage("notification.transaction.upi.status.update.title", userLocale))
                    .message(message)
                    .notificationType("TRANSACTION_UPI_STATUS_UPDATE")
                    .status(newStatus.toString())
                    .orderId(orderId)
                    .data(transactionData)
                    .build();
            messagingTemplate.convertAndSend(topic, eventMessage);
            log.info("[Notification][WebSocket] broadcast topic={} notificationType=TRANSACTION_UPI_STATUS_UPDATE transactionId={} status={} restaurantId={} orderId={}",
                    topic, transactionId, newStatus, restaurantId, orderId);
            
            // Also publish to RabbitMQ for integration service to log
            publishToRabbitMQ(topic, eventMessage);
        } catch (Exception e) {
            log.error("Failed to send WebSocket notification for UPI transaction status update: {}", e.getMessage());
        }
    }

    /**
     * Sends a WebSocket notification for order cancellation to all subscribers of the restaurant's order-status topic.
     * Also publishes the notification to RabbitMQ for integration service logging.
     *
     * @param userLocale Locale for message localization
     * @param restaurantId UUID of the restaurant
     * @param orderId UUID of the cancelled order
     * @param cancellationReason Reason for the cancellation
     */
    @Override
    public void sendOrderCancellationWebSocketNotification(Locale userLocale, UUID restaurantId, UUID orderId, String cancellationReason) {
        try {
            // Send to general order-status topic for all consumers (non-KDS specific)
            String generalTopic = TOPIC_RESTAURANT_PREFIX + restaurantId + "/order-status";
            Map<String, Object> generalData = new HashMap<>();
            generalData.put(KEY_ORDER_ID, orderId.toString());
            generalData.put(KEY_STATUS, OrderStatus.CANCELED.toString());
            generalData.put(KEY_RESTAURANT_ID, restaurantId.toString());
            generalData.put(KEY_NOTIFICATION_TYPE, NOTIF_ORDER_STATUS_UPDATE);
            generalData.put(KEY_TIMESTAMP, LocalDateTime.now(ZoneOffset.UTC).format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
            
            StatusEventMessage generalMessage = StatusEventMessage.builder()
                    .title(messageUtil.getMessage("notification.order.updated.title", userLocale))
                    .message(messageUtil.getMessage("order.update.success", userLocale))
                    .notificationType(NOTIF_ORDER_STATUS_UPDATE)
                    .orderId(orderId.toString())
                    .status(OrderStatus.CANCELED.toString())
                    .data(generalData)
                    .build();
            messagingTemplate.convertAndSend(generalTopic, generalMessage);
            log.info("[Notification][WebSocket] broadcast topic={} notificationType={} orderId={} status={} restaurantId={}",
                    generalTopic, NOTIF_ORDER_STATUS_UPDATE, orderId, OrderStatus.CANCELED, restaurantId);
            
            // Also publish to RabbitMQ for integration service to log
            publishToRabbitMQ(generalTopic, generalMessage);
        } catch (Exception e) {
            log.error("Failed to send WebSocket notification for order cancellation: {}", e.getMessage());
        }
    }

    /**
     * Publishes a WebSocket notification message to RabbitMQ for integration service logging.
     * Includes all available fields from the event message (notification type, order ID, item ID, status, data).
     *
     * @param topic WebSocket topic for the notification
     * @param eventMessage Status event message to publish
     */
    @Override
    public void publishToRabbitMQ(String topic, StatusEventMessage eventMessage) {
        if (rabbitTemplate != null) {
            try {
                Map<String, Object> wsMessage = new HashMap<>();
                wsMessage.put("topic", topic);
                if (eventMessage.getTitle() != null && !eventMessage.getTitle().isEmpty()) {
                    wsMessage.put("title", eventMessage.getTitle());
                }
                wsMessage.put("message", eventMessage.getMessage());
                wsMessage.put(KEY_TIMESTAMP, LocalDateTime.now(ZoneOffset.UTC).format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
                wsMessage.put("type", "websocket_notification");
                
                // Add enhanced fields if available
                if (eventMessage.getNotificationType() != null) {
                    wsMessage.put(KEY_NOTIFICATION_TYPE, eventMessage.getNotificationType());
                }
                if (eventMessage.getOrderId() != null) {
                    wsMessage.put(KEY_ORDER_ID, eventMessage.getOrderId());
                }
                if (eventMessage.getItemId() != null) {
                    wsMessage.put("itemId", eventMessage.getItemId());
                }
                if (eventMessage.getStatus() != null) {
                    wsMessage.put(KEY_STATUS, eventMessage.getStatus());
                }
                if (eventMessage.getData() != null) {
                    wsMessage.put("data", eventMessage.getData());
                }
                wsMessage.put(RabbitMQConfig.WEBSOCKET_MSG_SUPPRESS_LOCAL_FORWARD, Boolean.TRUE);

                rabbitTemplate.convertAndSend(RabbitMQConfig.WEBSOCKET_TOPIC_EXCHANGE, RabbitMQConfig.WEBSOCKET_ROUTING_KEY, wsMessage);
                log.info("[Notification][FCM] rabbitPublish exchange={} routingKey={} payloadWsTopic={} notificationType={} orderId={} itemId={}",
                        RabbitMQConfig.WEBSOCKET_TOPIC_EXCHANGE,
                        RabbitMQConfig.WEBSOCKET_ROUTING_KEY,
                        topic,
                        eventMessage.getNotificationType(),
                        eventMessage.getOrderId(),
                        eventMessage.getItemId());
            } catch (Exception e) {
                log.warn("[Notification][FCM] rabbitPublish failed payloadWsTopic={}: {}", topic, e.getMessage());
            }
        } else {
            log.debug("RabbitTemplate not available, skipping RabbitMQ publish payloadWsTopic={}", topic);
        }
    }

    // ==================== STATUS UPDATE METHODS ====================

    /**
     * Updates an ordered item's status in the database synchronously.
     * Updates the status, timestamp, updatedBy user, and reason fields.
     *
     * @param itemId UUID of the ordered item to update
     * @param newStatus New status to set
     * @param updateUserId UUID of the user performing the update (can be null)
     * @param reason Reason for the status change (can be null)
     */
    @Override
    public void updateItemStatusAsync(UUID itemId, ItemStatus newStatus, UUID updateUserId, String reason) {
        try {
            OrderedItem item = orderedItemRepository.findById(itemId)
                    .orElseThrow(() -> new RuntimeException("OrderedItem not found: " + itemId));
            applyOrderedItemStatusUpdate(item, newStatus, updateUserId, reason);
            orderedItemRepository.save(item);
            log.debug("Successfully updated item status in database: {} to {}", itemId, newStatus);
        } catch (Exception e) {
            log.error("Failed to update item status in database for item {}: {}", itemId, e.getMessage(), e);
            throw e; // Re-throw to ensure caller is aware of the failure
        }
    }

    private void applyOrderedItemStatusUpdate(OrderedItem item, ItemStatus newStatus, UUID updateUserId, String reason) {
        item.setItemStatus(newStatus);
        item.setUpdatedAt(OffsetDateTime.now(ZoneOffset.UTC));
        if (updateUserId != null) {
            User user = userRepository.findById(updateUserId).orElse(null);
            item.setUpdatedBy(user);
        }
        if (reason != null) {
            item.setReason(reason);
        }
    }

    /**
     * Updates an ordered combo's status in the database synchronously.
     * When combo is pushed, also updates all items within the combo from ON_HOLD to PUSHED.
     * Updates the status, timestamp, updatedBy user, and reason fields.
     *
     * @param comboId UUID of the ordered combo to update
     * @param newStatus New status to set
     * @param updateUserId UUID of the user performing the update (can be null)
     * @param reason Reason for the status change (can be null)
     */
    @Override
    public void updateComboStatusAsync(UUID comboId, ItemStatus newStatus, UUID updateUserId, String reason) {
        try {
            OrderedCombo combo = orderedComboRepository.findById(comboId)
                    .orElseThrow(() -> new RuntimeException("OrderedCombo not found: " + comboId));
            combo.setItemStatus(newStatus);
            combo.setUpdatedAt(OffsetDateTime.now(ZoneOffset.UTC));
            if (updateUserId != null) {
                User user = userRepository.findById(updateUserId).orElse(null);
                combo.setUpdatedBy(user);
            }
            if (reason != null) {
                combo.setReason(reason);
            }
            orderedComboRepository.save(combo);
            log.debug("Successfully updated combo status in database: {} to {}", comboId, newStatus);
            
            // When combo is pushed, update all items within the combo from ON_HOLD to PUSHED
            if (newStatus == ItemStatus.PUSHED) {
                List<OrderedItem> comboItems = orderedItemRepository.findByOrderedComboId(comboId);
                for (OrderedItem comboItem : comboItems) {
                    // Only update items that are currently ON_HOLD
                    if (comboItem.getItemStatus() == ItemStatus.ON_HOLD) {
                        comboItem.setItemStatus(ItemStatus.PUSHED);
                        comboItem.setUpdatedAt(OffsetDateTime.now(ZoneOffset.UTC));
                        if (updateUserId != null) {
                            User user = userRepository.findById(updateUserId).orElse(null);
                            comboItem.setUpdatedBy(user);
                        }
                        if (reason != null) {
                            comboItem.setReason(reason);
                        }
                        orderedItemRepository.save(comboItem);
                        log.debug("Successfully updated combo item {} status from ON_HOLD to PUSHED in database", 
                                comboItem.getId());
                    }
                }
            }
        } catch (Exception e) {
            log.error("Failed to update combo status in database for combo {}: {}", comboId, e.getMessage(), e);
            throw e; // Re-throw to ensure caller is aware of the failure
        }
    }

    // ==================== STATUS UPDATE WITH NOTIFICATION METHODS ====================

    /**
     * Updates an ordered item's status with WebSocket notifications and database persistence.
     * Sends broadcast WebSocket notification only for ON_HOLD status (non-KDS).
     * For KDS-specific statuses (PUSHED, COOKING, READY, DELAYED), sends user-scoped notifications to assigned KDS users only.
     * Updates the database synchronously and updates the local object for immediate response.
     *
     * @param orderedItem The ordered item to update
     * @param newStatus New status to set
     * @param authenticatedUser The authenticated user performing the update (can be null)
     * @param hasUserId Whether the authenticated user has a valid ID
     * @param userLocale Locale for message localization
     * @param reason Reason for the status change (can be null)
     * @return UUID of the user who performed the update, or null if no user
     */
    @Override
    public UUID updateItemComboStatusWithNotification(OrderedItem orderedItem, ItemStatus newStatus,
                                                      User authenticatedUser, boolean hasUserId, Locale userLocale,
                                                      String reason) {
        // Get restaurant ID from order (safely handle null restaurant entity)
        UUID restaurantId = getRestaurantIdFromOrder(orderedItem.getOrder());
        
        // Send WebSocket broadcast notification for status changes except ON_HOLD.
        // This broadcast is for waiters/cashiers to see status updates in real-time.
        // The broadcast topic (/topic/restaurant/{restaurantId}/item-status) is subscribed by waiters/cashiers.
        // KDS apps should NOT subscribe to this broadcast topic - they use user-specific topics instead.
        sendItemStatusWebSocketNotification(userLocale, restaurantId, orderedItem.getId(), newStatus, "item");
        
        // Prepare for database update
        UUID updateUserId = hasUserId && authenticatedUser != null ? authenticatedUser.getId() : null;
        
        // Update database synchronously (reuse loaded entity — avoids extra SELECT by id)
        String sanitizedReason = sanitizeReason(reason);
        applyOrderedItemStatusUpdate(orderedItem, newStatus, updateUserId, sanitizedReason);
        orderedItemRepository.save(orderedItem);
        
        // Send user-scoped KDS notifications for KDS-relevant statuses.
        // Each method uses findKdsRecipientsForItem to resolve assigned KDS users and target KDS ids by category.
        if (newStatus == ItemStatus.PUSHED) {
            CompletableFuture.runAsync(() -> notifyItemPushedSafely(orderedItem, userLocale));
        } else if (newStatus == ItemStatus.COOKING || newStatus == ItemStatus.READY || newStatus == ItemStatus.DELAYED) {
            // Send user-scoped KDS WebSocket notification for COOKING/READY/DELAYED
            // so only the assigned KDS users (by category) receive the status change
            CompletableFuture.runAsync(() -> {
                try {
                    notificationService.notifyKdsItemStatusChange(orderedItem, newStatus, userLocale);
                } catch (Exception e) {
                    log.error("Failed to send KDS status change notification for item {} ({}): {}", 
                            orderedItem.getId(), newStatus, e.getMessage(), e);
                }
            });
        }
        
        return updateUserId;
    }

    /**
     * Updates multiple ordered items' statuses with WebSocket notifications and database persistence in a batch operation.
     * Sends broadcast WebSocket notifications immediately for ON_HOLD status (non-KDS).
     * For KDS-specific statuses, sends user-scoped notifications to assigned KDS users only.
     * Performs database updates and KDS notifications asynchronously in a single batch.
     *
     * @param updates Map of ordered items to their new statuses
     * @param authenticatedUser The authenticated user performing the update (can be null)
     * @param hasUserId Whether the authenticated user has a valid ID
     * @param userLocale Locale for message localization
     * @param reason Reason for the status change (can be null)
     */
    @Override
    public void updateItemStatusesWithNotification(Map<OrderedItem, ItemStatus> updates,
                                                   User authenticatedUser, boolean hasUserId, Locale userLocale,
                                                   String reason) {
        if (updates == null || updates.isEmpty()) {
            return;
        }

        UUID updateUserId = hasUserId && authenticatedUser != null ? authenticatedUser.getId() : null;
        String sanitizedReason = sanitizeReason(reason);

        // IMPORTANT:
        // OrderedItem is a mutable entity and Lombok @Data generates equals/hashCode using mutable fields
        // (including itemStatus). If we mutate a key and later do updates.get(key), HashMap lookups can fail
        // and return null. That can lead to persisting NULL item_status. To avoid this, never re-lookup by key
        // after mutating it; instead, work off a snapshot of the entry set.
        final List<Map.Entry<OrderedItem, ItemStatus>> entries = new ArrayList<>(updates.entrySet());

        // 1. Send WebSocket broadcast notifications immediately (optimistic update) except ON_HOLD.
        // This broadcast is for waiters/cashiers to see status updates in real-time.
        // The broadcast topic (/topic/restaurant/{restaurantId}/item-status) is subscribed by waiters/cashiers.
        // KDS apps should NOT subscribe to this broadcast topic - they use user-specific topics instead.
        for (Map.Entry<OrderedItem, ItemStatus> entry : entries) {
            OrderedItem item = entry.getKey();
            ItemStatus newStatus = entry.getValue();
            UUID restaurantId = getRestaurantIdSafely(item.getOrder());
            if (restaurantId != null) {
                sendItemStatusWebSocketNotification(userLocale, restaurantId, item.getId(), newStatus, "item");
            }
            
            // 2. Update local objects for immediate response consistency
            item.setItemStatus(newStatus);
            if (sanitizedReason != null) {
                item.setReason(sanitizedReason);
            }
        }

        // 3. Perform database updates synchronously to ensure status is persisted before API returns 200 OK
        try {
            User user = updateUserId != null ? userRepository.findById(updateUserId).orElse(null) : null;
            OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);

            List<OrderedItem> itemsToPersist = new ArrayList<>(entries.size());
            for (Map.Entry<OrderedItem, ItemStatus> entry : entries) {
                OrderedItem item = entry.getKey();
                ItemStatus newStatus = entry.getValue();
                item.setItemStatus(newStatus);
                item.setUpdatedAt(now);
                item.setUpdatedBy(user);
                if (sanitizedReason != null) {
                    item.setReason(sanitizedReason);
                }
                itemsToPersist.add(item);
            }
            orderedItemRepository.saveAll(itemsToPersist);
            orderedItemRepository.flush();
            log.info("Successfully processed batch status update for {} items synchronously", updates.size());
        } catch (Exception e) {
            log.error("Failed to process batch item status updates: {}", e.getMessage(), e);
            throw e; // Re-throw to ensure caller is aware of the failure
        }

        // 4. Send KDS notifications asynchronously (notifications only, DB already updated)
        CompletableFuture.runAsync(() -> {
            try {
                for (Map.Entry<OrderedItem, ItemStatus> entry : entries) {
                    OrderedItem item = entry.getKey();
                    ItemStatus newStatus = entry.getValue();
                    
                    // Send user-scoped KDS notification based on status
                    if (newStatus == ItemStatus.PUSHED) {
                        notifyItemPushedSafely(item, userLocale);
                    } else if (newStatus == ItemStatus.COOKING || newStatus == ItemStatus.READY || newStatus == ItemStatus.DELAYED) {
                        try {
                            notificationService.notifyKdsItemStatusChange(item, newStatus, userLocale);
                        } catch (Exception e) {
                            log.error("Failed to send KDS status change notification for item {} ({}) in batch: {}", item.getId(), newStatus, e.getMessage());
                        }
                    }
                }
            } catch (Exception e) {
                log.error("Failed to process batch item status updates: {}", e.getMessage(), e);
            }
        });
    }

    /**
     * Updates an ordered combo's status with WebSocket notifications and database persistence.
     * Sends broadcast WebSocket notification only for ON_HOLD status (non-KDS).
     * When combo is pushed, updates all items within the combo from ON_HOLD to PUSHED.
     * When combo is served, updates all non-canceled items within the combo to SERVED.
     * For KDS-specific statuses (COOKING, READY, DELAYED), sends user-scoped notifications to assigned KDS users only.
     * Updates the database synchronously and updates the local object for immediate response.
     *
     * @param orderedCombo The ordered combo to update
     * @param newStatus New status to set
     * @param authenticatedUser The authenticated user performing the update (can be null)
     * @param hasUserId Whether the authenticated user has a valid ID
     * @param userLocale Locale for message localization
     * @param reason Reason for the status change (can be null)
     * @return UUID of the user who performed the update, or null if no user
     */
    @Override
    public UUID updateComboStatusWithNotification(OrderedCombo orderedCombo, ItemStatus newStatus,
                                                  User authenticatedUser, boolean hasUserId, Locale userLocale,
                                                  String reason) {
        // Get restaurant ID from order (safely handle null restaurant entity)
        UUID restaurantId = getRestaurantIdFromOrder(orderedCombo.getOrder());
        
        // Send WebSocket broadcast notification for status changes except ON_HOLD.
        // This broadcast is for waiters/cashiers to see status updates in real-time.
        // The broadcast topic (/topic/restaurant/{restaurantId}/item-status) is subscribed by waiters/cashiers.
        // KDS apps should NOT subscribe to this broadcast topic - they use user-specific topics instead.
        sendItemStatusWebSocketNotification(userLocale, restaurantId, orderedCombo.getId(), newStatus, ITEM_TYPE_COMBO);
        
        // Prepare for database update
        UUID updateUserId = hasUserId && authenticatedUser != null ? authenticatedUser.getId() : null;
        
        // Update database synchronously
        String sanitizedReason = sanitizeReason(reason);
        updateComboStatusAsync(orderedCombo.getId(), newStatus, updateUserId, sanitizedReason);
        
        // When combo is pushed, update all items within the combo from ON_HOLD to PUSHED
        if (newStatus == ItemStatus.PUSHED) {
            List<OrderedItem> comboItems = orderedItemRepository.findByOrderedComboId(orderedCombo.getId());
            for (OrderedItem comboItem : comboItems) {
                // Only update items that are currently ON_HOLD
                if (comboItem.getItemStatus() == ItemStatus.ON_HOLD) {
                    // Skip broadcast for PUSHED status — user-scoped notification is sent via notifyItemPushed below,
                    // which targets only KDS users assigned to this item's category. Broadcasting would leak to all KDS.
                    log.debug("Skipping broadcast item-status WebSocket for PUSHED combo item {}. " +
                            "User-specific notification sent via notifyItemPushed.", comboItem.getId());
                    // Update item status synchronously
                    updateItemStatusAsync(comboItem.getId(), ItemStatus.PUSHED, updateUserId, sanitizedReason);
                    // Update local object for response
                    comboItem.setItemStatus(ItemStatus.PUSHED);
                    if (sanitizedReason != null) {
                        comboItem.setReason(sanitizedReason);
                    }
                    log.debug("Updated combo item {} status from ON_HOLD to PUSHED when combo {} was pushed", 
                            comboItem.getId(), orderedCombo.getId());
                }
            }

            // Always send user-scoped KDS push notification for pushed combo lines.
            // This ensures waiter combo push mirrors single-item push routing to
            // /topic/restaurant/{restaurantId}/kds/{kdsStationId}/item-status (legacy .../kds/item-status if no targets).
            int notifiedCount = 0;
            for (OrderedItem comboItem : comboItems) {
                if (comboItem.getItemStatus() == ItemStatus.PUSHED) {
                    notifyItemPushedSafely(comboItem, userLocale);
                    notifiedCount++;
                }
            }
            log.info("Sent PUSHED KDS notifications for {} combo item(s) in combo {}", notifiedCount, orderedCombo.getId());
        }
        
        // When combo is served, update all non-canceled items within the combo to SERVED
        if (newStatus == ItemStatus.SERVED) {
            List<OrderedItem> comboItems = orderedItemRepository.findByOrderedComboId(orderedCombo.getId());
            for (OrderedItem comboItem : comboItems) {
                // Only update items that are not already CANCELED or SERVED
                if (comboItem.getItemStatus() != ItemStatus.CANCELED && comboItem.getItemStatus() != ItemStatus.SERVED) {
                    // Skip broadcast for SERVED status — user-scoped notification is sent via notifyItemServed,
                    // which targets only KDS users assigned to this item's category. Broadcasting would leak to all KDS.
                    log.debug("Skipping broadcast item-status WebSocket for SERVED combo item {}. " +
                            "User-specific notification sent via notifyItemServed.", comboItem.getId());
                    // Update item status synchronously
                    updateItemStatusAsync(comboItem.getId(), ItemStatus.SERVED, updateUserId, sanitizedReason);
                    // Update local object for response
                    comboItem.setItemStatus(ItemStatus.SERVED);
                    if (sanitizedReason != null) {
                        comboItem.setReason(sanitizedReason);
                    }
                    log.debug("Updated combo item {} status to SERVED when combo {} was served", 
                            comboItem.getId(), orderedCombo.getId());
                    
                    // Send user-scoped notification to assigned KDS users only
                    notifyItemServedSafely(comboItem, userLocale);
                }
            }
        }
        
        // When combo status is COOKING/READY/DELAYED, update combo items and send user-scoped KDS notifications
        if (newStatus == ItemStatus.COOKING || newStatus == ItemStatus.READY || newStatus == ItemStatus.DELAYED) {
            List<OrderedItem> comboItems = orderedItemRepository.findByOrderedComboId(orderedCombo.getId());
            for (OrderedItem comboItem : comboItems) {
                if (comboItem.getItemStatus() != ItemStatus.CANCELED && comboItem.getItemStatus() != newStatus) {
                    updateItemStatusAsync(comboItem.getId(), newStatus, updateUserId, sanitizedReason);
                    comboItem.setItemStatus(newStatus);
                    if (sanitizedReason != null) {
                        comboItem.setReason(sanitizedReason);
                    }
                    log.debug("Updated combo item {} status to {} when combo {} changed", 
                            comboItem.getId(), newStatus, orderedCombo.getId());
                    
                    // Send user-scoped KDS notification to assigned KDS users only
                    try {
                        notificationService.notifyKdsItemStatusChange(comboItem, newStatus, userLocale);
                    } catch (Exception e) {
                        log.error("Failed to send KDS status change notification for combo item {} ({}): {}", 
                                comboItem.getId(), newStatus, e.getMessage());
                    }
                }
            }
        }
        
        // Update local object for response
        orderedCombo.setItemStatus(newStatus);
        if (sanitizedReason != null) {
            orderedCombo.setReason(sanitizedReason);
        }
        
        return updateUserId;
    }

    /**
     * Updates multiple ordered combos' statuses with WebSocket notifications and database persistence in a batch operation.
     * Sends broadcast WebSocket notifications immediately for ON_HOLD status (non-KDS).
     * For KDS-specific statuses, sends user-scoped notifications to assigned KDS users only.
     * When combos are pushed, updates all items within the combos from ON_HOLD to PUSHED.
     * When combos are served, updates all non-canceled items within the combos to SERVED.
     * Performs database updates and KDS notifications asynchronously in a single batch.
     *
     * @param updates Map of ordered combos to their new statuses
     * @param authenticatedUser The authenticated user performing the update (can be null)
     * @param hasUserId Whether the authenticated user has a valid ID
     * @param userLocale Locale for message localization
     * @param reason Reason for the status change (can be null)
     */
    @Override
    public void updateComboStatusesWithNotification(Map<OrderedCombo, ItemStatus> updates,
                                                    User authenticatedUser, boolean hasUserId, Locale userLocale,
                                                    String reason) {
        if (updates == null || updates.isEmpty()) {
            return;
        }

        UUID updateUserId = hasUserId && authenticatedUser != null ? authenticatedUser.getId() : null;
        String sanitizedReason = sanitizeReason(reason);

        // 1. Send WebSocket broadcast notifications except ON_HOLD.
        // This broadcast is for waiters/cashiers to see status updates in real-time.
        // The broadcast topic (/topic/restaurant/{restaurantId}/item-status) is subscribed by waiters/cashiers.
        // KDS apps should NOT subscribe to this broadcast topic - they use user-specific topics instead.
        for (Map.Entry<OrderedCombo, ItemStatus> entry : updates.entrySet()) {
            OrderedCombo combo = entry.getKey();
            ItemStatus newStatus = entry.getValue();
            UUID restaurantId = getRestaurantIdSafely(combo.getOrder());
            if (restaurantId != null) {
                sendItemStatusWebSocketNotification(userLocale, restaurantId, combo.getId(), newStatus, ITEM_TYPE_COMBO);
            }
            
            combo.setItemStatus(newStatus);
            if (sanitizedReason != null) {
                combo.setReason(sanitizedReason);
            }
        }

        // 2. Perform database updates synchronously to ensure status is persisted before API returns 200 OK
        try {
            User user = updateUserId != null ? userRepository.findById(updateUserId).orElse(null) : null;
            OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);

            for (Map.Entry<OrderedCombo, ItemStatus> entry : updates.entrySet()) {
                OrderedCombo combo = entry.getKey();
                ItemStatus newStatus = entry.getValue();
                
                // Update combo database fields synchronously
                combo.setItemStatus(newStatus);
                combo.setUpdatedAt(now);
                combo.setUpdatedBy(user);
                if (sanitizedReason != null) {
                    combo.setReason(sanitizedReason);
                }
                orderedComboRepository.save(combo);

                // Handle PUSHED status for combo items - update synchronously
                if (newStatus == ItemStatus.PUSHED) {
                    List<OrderedItem> comboItems = orderedItemRepository.findByOrderedComboId(combo.getId());
                    for (OrderedItem comboItem : comboItems) {
                        if (comboItem.getItemStatus() == ItemStatus.ON_HOLD) {
                            comboItem.setItemStatus(ItemStatus.PUSHED);
                            comboItem.setUpdatedAt(now);
                            comboItem.setUpdatedBy(user);
                            if (sanitizedReason != null) {
                                comboItem.setReason(sanitizedReason);
                            }
                            orderedItemRepository.save(comboItem);
                        }
                    }
                }
                
                // Handle SERVED status for combo items - update synchronously
                if (newStatus == ItemStatus.SERVED) {
                    List<OrderedItem> comboItems = orderedItemRepository.findByOrderedComboId(combo.getId());
                    for (OrderedItem comboItem : comboItems) {
                        if (comboItem.getItemStatus() != ItemStatus.CANCELED && comboItem.getItemStatus() != ItemStatus.SERVED) {
                            comboItem.setItemStatus(ItemStatus.SERVED);
                            comboItem.setUpdatedAt(now);
                            comboItem.setUpdatedBy(user);
                            if (sanitizedReason != null) {
                                comboItem.setReason(sanitizedReason);
                            }
                            orderedItemRepository.save(comboItem);
                        }
                    }
                }
                
                // Handle COOKING/READY/DELAYED status for combo items - update synchronously
                if (newStatus == ItemStatus.COOKING || newStatus == ItemStatus.READY || newStatus == ItemStatus.DELAYED) {
                    List<OrderedItem> comboItems = orderedItemRepository.findByOrderedComboId(combo.getId());
                    for (OrderedItem comboItem : comboItems) {
                        if (comboItem.getItemStatus() != ItemStatus.CANCELED && comboItem.getItemStatus() != newStatus) {
                            comboItem.setItemStatus(newStatus);
                            comboItem.setUpdatedAt(now);
                            comboItem.setUpdatedBy(user);
                            if (sanitizedReason != null) {
                                comboItem.setReason(sanitizedReason);
                            }
                            orderedItemRepository.save(comboItem);
                        }
                    }
                }
            }
            
            // Final flush to ensure all batched updates are sent to DB synchronously
            orderedComboRepository.flush();
            orderedItemRepository.flush();
            log.info("Successfully processed batch status update for {} combos synchronously", updates.size());
        } catch (Exception e) {
            log.error("Failed to process batch combo status updates: {}", e.getMessage(), e);
            throw e; // Re-throw to ensure caller is aware of the failure
        }

        // 3. Send KDS notifications asynchronously (notifications only, DB already updated)
        CompletableFuture.runAsync(() -> {
            try {
                for (Map.Entry<OrderedCombo, ItemStatus> entry : updates.entrySet()) {
                    OrderedCombo combo = entry.getKey();
                    ItemStatus newStatus = entry.getValue();

                    // Handle PUSHED status for combo items - send notifications async
                    if (newStatus == ItemStatus.PUSHED) {
                        List<OrderedItem> comboItems = orderedItemRepository.findByOrderedComboId(combo.getId());
                        for (OrderedItem comboItem : comboItems) {
                            if (comboItem.getItemStatus() == ItemStatus.PUSHED) {
                                // Send user-scoped push notification to assigned KDS users only
                                notifyItemPushedSafely(comboItem, userLocale);
                            }
                        }
                    }
                    
                    // Handle SERVED status for combo items - send notifications async
                    if (newStatus == ItemStatus.SERVED) {
                        List<OrderedItem> comboItems = orderedItemRepository.findByOrderedComboId(combo.getId());
                        for (OrderedItem comboItem : comboItems) {
                            if (comboItem.getItemStatus() == ItemStatus.SERVED) {
                                // Send user-scoped notification to assigned KDS users only
                                notifyItemServedSafely(comboItem, userLocale);
                            }
                        }
                    }
                    
                    // Handle COOKING/READY/DELAYED status for combo items - send notifications async
                    if (newStatus == ItemStatus.COOKING || newStatus == ItemStatus.READY || newStatus == ItemStatus.DELAYED) {
                        List<OrderedItem> comboItems = orderedItemRepository.findByOrderedComboId(combo.getId());
                        for (OrderedItem comboItem : comboItems) {
                            if (comboItem.getItemStatus() == newStatus) {
                                // Send user-scoped KDS notification to assigned KDS users only
                                try {
                                    notificationService.notifyKdsItemStatusChange(comboItem, newStatus, userLocale);
                                } catch (Exception e) {
                                    log.error("Failed to send KDS status change notification for combo item {} ({}) in batch: {}", 
                                            comboItem.getId(), newStatus, e.getMessage());
                                }
                            }
                        }
                    }
                }
            } catch (Exception e) {
                log.error("Failed to process batch combo status updates: {}", e.getMessage(), e);
            }
        });
    }

    // ==================== HELPER METHODS ====================

    /**
     * Resolves the restaurant id for an order in a way that is safe with lazy-loaded associations.
     * <p>
     * First attempts to read {@code order.getRestaurant().getId()} only when Hibernate reports the association is initialized,
     * to avoid {@link org.hibernate.LazyInitializationException} when the persistence context is closed/cleared.
     * If the association is not initialized (or access fails), falls back to querying the restaurant id directly from the database
     * via {@code orderRepository.findRestaurantIdByOrderId(orderId)}.
     *
     * @param order order entity (required)
     * @return restaurant id for the given order
     * @throws IllegalStateException if {@code order} is {@code null} or the restaurant id cannot be resolved
     */
    @Override
    public UUID getRestaurantIdFromOrder(Order order) {
        if (order == null) {
            log.error("Order is null when trying to get restaurant ID");
            throw new IllegalStateException("Order cannot be null");
        }
        
        // Check if restaurant proxy is initialized before accessing it
        // This prevents LazyInitializationException when session is closed
        try {
            if (Hibernate.isInitialized(order.getRestaurant()) && order.getRestaurant() != null) {
                return order.getRestaurant().getId();
            }
        } catch (Exception e) {
            // Proxy not initialized or lazy loading failed - fall through to database query
            log.debug("Restaurant proxy not initialized for order {}, will query database: {}", order.getId(), e.getMessage());
        }
        
        // If restaurant entity is not loaded or proxy not initialized, query restaurant_id directly from database
        // This handles cases where the restaurant relationship is lazy-loaded and not initialized
        // This can happen when entity manager is cleared or session is closed
        try {
            log.debug("Querying restaurant_id directly from database for order {}", order.getId());
            return orderRepository.findRestaurantIdByOrderId(order.getId())
                    .orElseThrow(() -> new IllegalStateException("Restaurant ID not found for order: " + order.getId()));
        } catch (Exception e) {
            log.error("Failed to get restaurant ID for order {}: {}", order.getId(), e.getMessage(), e);
            throw new IllegalStateException("Failed to get restaurant ID for order: " + order.getId(), e);
        }
    }

    /**
     * Gets the restaurant ID from an order safely, returning null if the order is null or if an error occurs.
     * This is a safe wrapper around getRestaurantIdFromOrder that catches exceptions.
     *
     * @param order The order from which to get the restaurant ID (can be null)
     * @return UUID of the restaurant, or null if order is null or an error occurs
     */
    @Override
    public UUID getRestaurantIdSafely(Order order) {
        if (order == null) {
            return null;
        }
        try {
            return getRestaurantIdFromOrder(order);
        } catch (Exception e) {
            log.warn("Could not get restaurant ID from order {}: {}", order.getId(), e.getMessage());
            return null;
        }
    }

    @Override
    public String sanitizeReason(String reason) {
        if (reason == null) {
            return null;
        }
        String trimmed = reason.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    /**
     * Sends waiter notifications for item status changes asynchronously.
     * Gets all waiters assigned to the table and sends appropriate notifications based on the status.
     * For READY and DELAYED statuses, also ensures KDS users receive notifications even when no waiters are assigned.
     *
     * @param orderedItem The ordered item whose status changed
     * @param newStatus New status of the item
     * @param reason Reason for the status change (can be null)
     * @param userLocale Locale for message localization
     */
    @Override
    public void sendWaiterNotificationForItemStatus(OrderedItem orderedItem, ItemStatus newStatus, String reason, Locale userLocale) {
        CompletableFuture.runAsync(() -> {
            try {
                log.info("Sending waiter notification for item {} status change to {} for table {}", 
                        orderedItem.getId(), newStatus, orderedItem.getOrder().getRestaurantTable().getTableOrder());
                // Get all waiters assigned to the table
                List<User> waiters = getWaitersForTable(orderedItem.getOrder().getRestaurantTable());
                
                if (waiters != null && !waiters.isEmpty()) {
                    for (User waiter : waiters) {
                        if (waiter != null) {
                            sendWaiterNotificationByStatus(orderedItem, waiter, newStatus, reason, userLocale);
                        }
                    }
                    log.info("Sent notifications to {} waiters for table {} item status change to {}", 
                            waiters.size(), orderedItem.getOrder().getRestaurantTable().getTableOrder(), newStatus);
                } else {
                    log.debug("No waiters assigned to table {} for item status notification, but will still notify KDS users", 
                            orderedItem.getOrder().getRestaurantTable().getTableOrder());
                    
                    // Even when no waiters are assigned, ensure KDS users receive notifications for READY and DELAYED statuses
                    // This fixes the issue where KDS notifications were not being saved when items are marked as ready or delayed
                    // The notification service methods now handle null waiters and still save KDS notifications
                    if (newStatus == ItemStatus.READY || newStatus == ItemStatus.DELAYED) {
                        sendKdsNotificationForNoWaiters(orderedItem, newStatus, reason, userLocale);
                    }
                }
            } catch (Exception e) {
                log.error("Failed to send waiter notification for item status change asynchronously: {}", e.getMessage(), e);
            }
        });
    }


    // ==================== EXTRACTED HELPER METHODS ====================

    /**
     * Fetches the order ID associated with a transaction.
     * Returns null if the transaction is not found or has no associated order.
     */
    private String fetchOrderIdFromTransaction(UUID transactionId) {
        try {
            Optional<Transaction> transactionOpt = transactionRepository.findById(transactionId);
            if (transactionOpt.isPresent()) {
                Order order = transactionOpt.get().getOrder();
                if (order != null) {
                    return order.getId().toString();
                }
            }
        } catch (Exception e) {
            log.warn("Could not fetch order ID for transaction {}: {}", transactionId, e.getMessage());
        }
        return null;
    }

    /**
     * Sends an item-pushed notification safely, catching and logging any exceptions.
     */
    private void notifyItemPushedSafely(OrderedItem item, Locale userLocale) {
        try {
            notificationService.notifyItemPushed(item, userLocale);
        } catch (Exception e) {
            log.error("Failed to send item pushed notification for item {}: {}", item.getId(), e.getMessage());
        }
    }

    /**
     * Sends an item-served notification safely, catching and logging any exceptions.
     */
    private void notifyItemServedSafely(OrderedItem comboItem, Locale userLocale) {
        try {
            notificationService.notifyItemServed(comboItem, userLocale);
        } catch (Exception e) {
            log.error("Failed to send item served notification for combo item {}: {}", comboItem.getId(), e.getMessage());
        }
    }

    /**
     * Sends a waiter notification based on the item status, catching and logging any exceptions.
     */
    private void sendWaiterNotificationByStatus(OrderedItem orderedItem, User waiter, ItemStatus newStatus,
                                                String reason, Locale userLocale) {
        try {
            switch (newStatus) {
                case READY:
                    notificationService.notifyItemReady(orderedItem, waiter, userLocale);
                    break;
                case DELAYED:
                    notificationService.notifyItemDelayed(orderedItem, waiter, reason, userLocale);
                    break;
                case SERVED:
                    // SERVED status notifications are sent to KDS users, not waiters
                    break;
                default:
                    // No specific notification for other statuses
                    break;
            }
        } catch (Exception e) {
            log.error("Failed to send notification to waiter {} for item status change: {}",
                    waiter != null ? waiter.getId() : "null", e.getMessage(), e);
        }
    }

    /**
     * Sends KDS notifications when no waiters are assigned to the table.
     */
    private void sendKdsNotificationForNoWaiters(OrderedItem orderedItem, ItemStatus newStatus,
                                                  String reason, Locale userLocale) {
        try {
            if (newStatus == ItemStatus.READY) {
                notificationService.notifyItemReady(orderedItem, null, userLocale);
            } else if (newStatus == ItemStatus.DELAYED) {
                notificationService.notifyItemDelayed(orderedItem, null, reason, userLocale);
            }
        } catch (Exception e) {
            log.error("Failed to save KDS notification for item status change when no waiters assigned: {}",
                    e.getMessage(), e);
        }
    }

    /**
     * Gets all waiters currently assigned to a table.
     * Queries active table assignments (where unassignedAt is null) and extracts unique waiters.
     *
     * @param table The restaurant table for which to get waiters
     * @return List of waiters assigned to the table, or empty list if none found or an error occurs
     */
    private List<User> getWaitersForTable(RestaurantTable table) {
        try {
            List<TableAssignment> tableAssignments = tableAssignmentRepository.findByRestaurantTableIdAndUnassignedAtIsNullWithWaiter(table.getId());
            if (!tableAssignments.isEmpty()) {
                return tableAssignments.stream()
                        .map(TableAssignment::getWaiter)
                        .filter(Objects::nonNull)
                        .distinct() // Remove duplicate waiters if any
                        .collect(Collectors.toList());
            }
        } catch (Exception e) {
            log.error("Error getting waiters for table {}: {}", table.getId(), e.getMessage(), e);
        }
        return new ArrayList<>();
    }
}

