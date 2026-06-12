package com.gulfnet.restaurantmanagement.service;

import com.gulfnet.shared_library.entity.Order;
import com.gulfnet.shared_library.entity.OrderedCombo;
import com.gulfnet.shared_library.entity.OrderedItem;
import com.gulfnet.shared_library.entity.User;
import com.gulfnet.shared_library.enums.ItemStatus;
import com.gulfnet.shared_library.enums.OrderStatus;
import com.gulfnet.shared_library.enums.TransactionStatus;
import com.gulfnet.shared_library.model.request.StatusEventMessage;

import java.util.Locale;
import java.util.UUID;

public interface OrderNotificationService {

    void sendItemStatusWebSocketNotification(Locale userLocale, UUID restaurantId, UUID itemId, ItemStatus newStatus, String itemType);

    void sendOrderStatusWebSocketNotification(Locale userLocale, UUID restaurantId, UUID orderId, OrderStatus newStatus);

    void sendTransactionStatusWebSocketNotification(Locale userLocale, UUID restaurantId, UUID transactionId, TransactionStatus newStatus);

    void sendCheckoutInitiatedTransactionStatusWebSocketNotification(Locale userLocale, UUID restaurantId,
            UUID transactionId, Order order, TransactionStatus newStatus);

    void sendTransactionUpiStatusWebSocketNotification(Locale userLocale, UUID restaurantId, UUID transactionId, TransactionStatus newStatus);

    void sendOrderCancellationWebSocketNotification(Locale userLocale, UUID restaurantId, UUID orderId, String cancellationReason);

    void publishToRabbitMQ(String topic, StatusEventMessage eventMessage);

    // ==================== STATUS UPDATE METHODS ====================
    
    void updateItemStatusAsync(UUID itemId, ItemStatus newStatus, UUID updateUserId, String reason);
    
    void updateComboStatusAsync(UUID comboId, ItemStatus newStatus, UUID updateUserId, String reason);

    // ==================== STATUS UPDATE WITH NOTIFICATION METHODS ====================
    
    UUID updateItemComboStatusWithNotification(OrderedItem orderedItem, ItemStatus newStatus,
                                               User authenticatedUser, boolean hasUserId, Locale userLocale,
                                               String reason);
    
    void updateItemStatusesWithNotification(java.util.Map<OrderedItem, ItemStatus> updates,
                                           User authenticatedUser, boolean hasUserId, Locale userLocale,
                                           String reason);
    
    UUID updateComboStatusWithNotification(OrderedCombo orderedCombo, ItemStatus newStatus,
                                          User authenticatedUser, boolean hasUserId, Locale userLocale,
                                          String reason);

    void updateComboStatusesWithNotification(java.util.Map<OrderedCombo, ItemStatus> updates,
                                            User authenticatedUser, boolean hasUserId, Locale userLocale,
                                            String reason);

    // ==================== HELPER METHODS ====================
    
    UUID getRestaurantIdFromOrder(Order order);
    
    UUID getRestaurantIdSafely(Order order);
    
    String sanitizeReason(String reason);
    
    void sendWaiterNotificationForItemStatus(OrderedItem orderedItem, ItemStatus newStatus, String reason, Locale userLocale);
}

