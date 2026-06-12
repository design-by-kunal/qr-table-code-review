package com.gulfnet.restaurantmanagement.service.impl;

import com.gulfnet.restaurantmanagement.config.RestaurantChainConfigProperties;
import com.gulfnet.restaurantmanagement.service.OrderRecalculationService;
import com.gulfnet.restaurantmanagement.service.OrderValidationService;
import com.gulfnet.restaurantmanagement.service.OrderNotificationService;
import com.gulfnet.restaurantmanagement.service.OrderPricingService;
import com.gulfnet.restaurantmanagement.service.OrderedItemService;
import com.gulfnet.restaurantmanagement.service.OrderedComboService;
import com.gulfnet.restaurantmanagement.service.NotificationService;
import com.gulfnet.restaurantmanagement.util.PriceOverrideHelper;
import com.gulfnet.restaurantmanagement.util.MessageUtil;
import com.gulfnet.shared_library.service.TakeawaySessionTableReleaseService;
import com.gulfnet.shared_library.entity.*;
import com.gulfnet.shared_library.enums.BxgyRole;
import com.gulfnet.shared_library.enums.DiscountType;
import com.gulfnet.shared_library.enums.ItemStatus;
import com.gulfnet.shared_library.enums.OrderStatus;
import com.gulfnet.shared_library.enums.OrderType;
import com.gulfnet.shared_library.enums.PaymentSystemType;
import com.gulfnet.shared_library.enums.TableStatus;
import com.gulfnet.shared_library.enums.TransactionStatus;
import com.gulfnet.shared_library.model.request.UpdateOrderedComboRequest;
import com.gulfnet.shared_library.model.request.UpdateOrderedItemRequest;
import com.gulfnet.shared_library.model.request.OrderedItemRequest;
import com.gulfnet.shared_library.model.request.OrderedComboRequest;
import com.gulfnet.shared_library.model.request.OrderedItemModifierRequest;
import com.gulfnet.shared_library.model.request.StatusEventMessage;
import com.gulfnet.shared_library.model.response.dto.BxgyCalculationResult;
import com.gulfnet.shared_library.model.response.dto.BxgyItemInfo;
import com.gulfnet.shared_library.model.response.dto.OrderRecalculationResult;
import com.gulfnet.shared_library.repository.*;
import com.gulfnet.shared_library.util.CurrencyFormatter;
import com.gulfnet.restaurantmanagement.service.AuditTrailService;
import com.gulfnet.shared_library.enums.ActionType;
import com.gulfnet.shared_library.enums.RequestStatus;
import com.gulfnet.shared_library.entity.Restaurant;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.Hibernate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrderRecalculationServiceImpl implements OrderRecalculationService {

    private static final String LOG_KDS_ITEM_CANCEL_FAILED =
            "Failed to send KDS notification for item {} cancellation: {}";
    private static final String LOG_KDS_COMBO_ITEM_CANCEL_FAILED =
            "Failed to send KDS notification for combo item {} cancellation: {}";
    private static final String LOG_KDS_COMBO_CANCEL_FAILED =
            "Failed to send KDS notifications for combo {} cancellation: {}";

    private final OrderRepository orderRepository;
    private final OrderedItemRepository orderedItemRepository;
    private final OrderedItemModifierRepository orderedItemModifierRepository;
    private final OrderedComboRepository orderedComboRepository;
    private final DiscountRepository discountRepository;
    private final DiscountBxgyItemRepository discountBxgyItemRepository;
    private final MenuCategoryMappingRepository menuCategoryMappingRepository;
    private final CategoryItemMappingRepository categoryItemMappingRepository;
    private final TransactionRepository transactionRepository;
    private final TakeawaySessionTableReleaseService takeawaySessionTableReleaseService;
    private final SimpMessagingTemplate messagingTemplate;
    private final ModifierItemRepository modifierItemRepository;
    private final ComboRepository comboRepository;
    private final ComboGroupRepository comboGroupRepository;
    private final ComboItemModifierRepository comboItemModifierRepository;
    private final ItemRepository itemRepository;
    private final RoleRepository roleRepository;
    @Lazy
    @Autowired
    private OrderedItemService orderedItemService;
    private final OrderedComboService orderedComboService;
    private final RestaurantChainConfigProperties restaurantChainConfigProperties;
    private final PriceOverrideHelper priceOverrideHelper;
    private final OrderValidationService orderValidationService;
    private final OrderNotificationService orderNotificationService;
    private final OrderPricingService orderPricingService;
    private final MessageUtil messageUtil;
    private final AuditTrailService auditTrailService;
    @Lazy
    @Autowired
    private NotificationService notificationService;

    @PersistenceContext
    private EntityManager entityManager;

    // Constants
    private static final String ERROR_ORDER_NOT_FOUND_PREFIX = "Order not found: ";
    private static final String ERROR_ORDERED_ITEM_NOT_FOUND_PREFIX = "OrderedItem not found: ";
    private static final String ERROR_ORDERED_COMBO_NOT_FOUND_PREFIX = "OrderedCombo not found: ";
    private static final String ITEM_TYPE_COMBO = "combo";
    private static final String UNKNOWN_TABLE = "unknown";

    // ==================== SIMPLE METHODS ====================

    /**
     * Updates an order's status when it changes and notifies clients.
     * <p>
     * Behavior:
     * - emits an optimistic WebSocket status update for the restaurant topic
     * - performs a synchronous DB update by re-fetching the order to avoid overwriting recalculated totals
     * - only updates order status and audit fields, preserving totals/discount fields
     *
     * @param order order being updated (required)
     * @param newStatus new status to apply (required)
     * @param authenticatedUser actor user (may be {@code null})
     * @param hasUserId whether to set {@code updatedBy} from {@code authenticatedUser}
     * @param userLocale locale for notifications/messages (may be {@code null})
     */
    @Override
    public void updateOrderStatusIfChanged(Order order, OrderStatus newStatus, User authenticatedUser, 
                                           boolean hasUserId, Locale userLocale) {
        if (newStatus != order.getOrderStatus()) {
            // Get restaurant ID from order (safely handle null restaurant entity)
            UUID restaurantId = orderNotificationService.getRestaurantIdFromOrder(order);
            
            // Send WebSocket notification (optimistic update)
            orderNotificationService.sendOrderStatusWebSocketNotification(userLocale, restaurantId, order.getId(), newStatus);
            
            // CRITICAL: Update database synchronously to avoid race conditions with order recalculation
            // Refetch the order to ensure we have the latest totals before updating status
            UUID orderId = order.getId();
            Order orderToUpdate = orderRepository.findById(orderId)
                    .orElseThrow(() -> new RuntimeException(ERROR_ORDER_NOT_FOUND_PREFIX + orderId));
            
            // Only update status and audit fields, preserve all other fields (totals, discounts, etc.)
            orderToUpdate.setOrderStatus(newStatus);
            orderToUpdate.setUpdatedAt(OffsetDateTime.now(ZoneOffset.UTC));
            if (hasUserId && authenticatedUser != null) {
                orderToUpdate.setUpdatedBy(authenticatedUser);
            }
            
            // Save only the status update - this preserves all other fields including recalculated totals
            orderRepository.save(orderToUpdate);
            orderRepository.flush(); // Ensure the status update is persisted
            
            // Update local object for response
            order.setOrderStatus(newStatus);
            
            log.debug("Successfully updated order status synchronously: {} to {}", orderId, newStatus);
        }
    }

    /**
     * For prepaid dine-in (chain prepaid) or takeaway after payment, pushes non-pushed line items to {@code PUSHED},
     * sends KDS notifications, and reconciles order status from item states.
     */
    @Override
    public void pushNonPushedOrderedItemsAfterPrepaidPaymentIfApplicable(
            Order order, Transaction transaction, UUID orderId, Locale locale) {
        RestaurantChainConfigProperties.RestaurantChainData chainConfig = restaurantChainConfigProperties.getChain();
        boolean shouldPushItems = (order.getOrderType() == OrderType.DINE_IN
                && chainConfig != null && chainConfig.getPaymentType() == PaymentSystemType.PREPAID)
                || order.getOrderType() == OrderType.TAKEAWAY;

        if (!shouldPushItems) {
            return;
        }

        List<OrderedItem> orderItems = orderedItemRepository.findByOrderId(orderId);
        Map<OrderedItem, ItemStatus> itemsToPush = new HashMap<>();

        for (OrderedItem item : orderItems) {
            if (item.getItemStatus() != ItemStatus.PUSHED) {
                itemsToPush.put(item, ItemStatus.PUSHED);
            }
        }

        if (itemsToPush.isEmpty()) {
            return;
        }

        for (Map.Entry<OrderedItem, ItemStatus> entry : itemsToPush.entrySet()) {
            entry.getKey().setItemStatus(entry.getValue());
        }

        orderedItemRepository.saveAll(itemsToPush.keySet());
        User cashier = transaction.getCashier();
        orderNotificationService.updateItemStatusesWithNotification(itemsToPush, cashier, true, locale, null);

        OrderStatus newStatus = determineOrderStatusBasedOnItems(orderId);
        updateOrderStatusIfChanged(order, newStatus, cashier, true, locale);
    }

    @Override
    public void maybeExpireTakeawaySessionWhenOrderServedAndTransactionCompleted(UUID orderId) {
        // For TAKEAWAY: SERVED + COMPLETED transaction, or CANCELED — expire session and free table when applicable
        takeawaySessionTableReleaseService.maybeExpireTakeawaySessionWhenOrderServedAndTransactionCompleted(
                orderId, this::sendTableStatusUpdatedWebSocketBestEffort);
    }

    /**
     * Broadcasts a best-effort table-status WebSocket message after a table becomes available (errors are logged only).
     *
     * @param order   order supplying restaurant context
     * @param tableId table whose status changed
     */
    private void sendTableStatusUpdatedWebSocketBestEffort(Order order, UUID tableId) {
        try {
            UUID restaurantId = orderNotificationService.getRestaurantIdFromOrder(order);
            Locale locale = LocaleContextHolder.getLocale();
            String topic = "/topic/restaurant/" + restaurantId + "/table-status";
            StatusEventMessage eventMessage = StatusEventMessage.builder()
                    .message(messageUtil.getMessage("table.status.updated", locale))
                    .build();
            messagingTemplate.convertAndSend(topic, eventMessage);
            log.info("[Notification][WebSocket] broadcast topic={} tableId={} status={} restaurantId={}",
                    topic, tableId, TableStatus.AVAILABLE, restaurantId);
        } catch (Exception e) {
            log.error("Failed to send WebSocket notification for table {} after takeaway completion: {}",
                    tableId, e.getMessage());
        }
    }

    /**
     * Derives an {@link OrderStatus} from the current statuses of all ordered items and ordered combos.
     * <p>
     * Rules prioritize:
     * - CANCELED (all canceled)
     * - SERVED (all served, or all non-canceled served, or mix of served + on-hold with no other active statuses)
     * - IN_PROGRESS (any cooking, or mixed statuses beyond pushed/on-hold)
     * - PUSHED (all pushed, or fallback)
     *
     * @param orderId order id to evaluate (required)
     * @return computed order status
     */
    @Override
    public OrderStatus determineOrderStatusBasedOnItems(UUID orderId) {
        List<OrderedItem> allOrderedItems = orderedItemRepository.findByOrderId(orderId);
        List<OrderedCombo> allOrderedCombos = orderedComboRepository.findByOrderId(orderId);
        
        // Combine items and combos for status determination
        long totalItems = allOrderedItems.size();
        long totalCombos = allOrderedCombos.size();
        long totalEntities = totalItems + totalCombos;
        
        if (totalEntities == 0) {
            return OrderStatus.PUSHED; // Default status for empty orders
        }
        
        // Count items by status
        long pushedItems = allOrderedItems.stream()
                .filter(item -> item.getItemStatus() == ItemStatus.PUSHED)
                .count();
        long servedItems = allOrderedItems.stream()
                .filter(item -> item.getItemStatus() == ItemStatus.SERVED)
                .count();
        long canceledItems = allOrderedItems.stream()
                .filter(item -> item.getItemStatus() == ItemStatus.CANCELED)
                .count();
        long cookingItems = allOrderedItems.stream()
                .filter(item -> item.getItemStatus() == ItemStatus.COOKING)
                .count();
        long onHoldItems = allOrderedItems.stream()
                .filter(item -> item.getItemStatus() == ItemStatus.ON_HOLD)
                .count();
        
        // Count combos by status
        long pushedCombos = allOrderedCombos.stream()
                .filter(combo -> combo.getItemStatus() == ItemStatus.PUSHED)
                .count();
        long servedCombos = allOrderedCombos.stream()
                .filter(combo -> combo.getItemStatus() == ItemStatus.SERVED)
                .count();
        long canceledCombos = allOrderedCombos.stream()
                .filter(combo -> combo.getItemStatus() == ItemStatus.CANCELED)
                .count();
        long cookingCombos = allOrderedCombos.stream()
                .filter(combo -> combo.getItemStatus() == ItemStatus.COOKING)
                .count();
        long onHoldCombos = allOrderedCombos.stream()
                .filter(combo -> combo.getItemStatus() == ItemStatus.ON_HOLD)
                .count();
        
        // Combined counts
        long totalPushed = pushedItems + pushedCombos;
        long totalServed = servedItems + servedCombos;
        long totalCanceled = canceledItems + canceledCombos;
        long totalCooking = cookingItems + cookingCombos;
        long totalOnHold = onHoldItems + onHoldCombos;
        
        // Rule 1: If all items/combos are CANCELED, order status is CANCELED
        if (totalCanceled == totalEntities) {
            return OrderStatus.CANCELED;
        }
        
        // Rule 2: If all items/combos are SERVED, order status is SERVED
        if (totalServed == totalEntities) {
            return OrderStatus.SERVED;
        }
        
        // Rule 2a: If all non-cancelled items are SERVED, order status is SERVED
        long nonCanceledItems = totalItems - canceledItems;
        long nonCanceledCombos = totalCombos - canceledCombos;
        long totalNonCanceled = nonCanceledItems + nonCanceledCombos;
        if (totalNonCanceled > 0 && totalServed == totalNonCanceled) {
            return OrderStatus.SERVED;
        }
        
        // Rule 2b: If some items are SERVED and remaining non-cancelled items are only ON_HOLD (or SERVED), order status is SERVED
        // This handles the case: some SERVED, some CANCELLED, some ON_HOLD -> order should be SERVED
        if (totalServed > 0 && totalNonCanceled > 0) {
            // Check if all non-cancelled items/combos are either SERVED or ON_HOLD
            long nonCanceledNonServedNonOnHold = totalEntities - totalCanceled - totalServed - totalOnHold;
            if (nonCanceledNonServedNonOnHold == 0 && totalServed > 0) {
                // All non-cancelled items are either SERVED or ON_HOLD, and at least one is SERVED
                return OrderStatus.SERVED;
            }
        }
        
        // Rule 3: If ANY item/combo is COOKING, order status is IN_PROGRESS (highest priority after CANCELED/SERVED)
        if (totalCooking > 0) {
            return OrderStatus.IN_PROGRESS;
        }
        
        // Rule 4: If all items/combos are PUSHED (and none are COOKING), order status is PUSHED
        if (totalPushed == totalEntities) {
            return OrderStatus.PUSHED;
        }
        
        // Rule 5: If any item/combo is in other statuses (DELAYED, READY, or mixed PUSHED with others), order status is IN_PROGRESS
        // Note: ON_HOLD is now handled separately in Rule 2b
        long otherStatusEntities = totalEntities - totalPushed - totalServed - totalCanceled - totalCooking - totalOnHold;
        if (otherStatusEntities > 0 || totalPushed > 0) {
            return OrderStatus.IN_PROGRESS;
        }
        
        // Default fallback
        return OrderStatus.PUSHED;
    }

    /**
     * Cancels an order (and its transaction) when all remaining entities are either CANCELED or were ON_HOLD/PUSHED and are now canceled.
     * <p>
     * Guardrails:
     * - if any item/combo is in a status outside {ON_HOLD, PUSHED, CANCELED}, the order is not auto-canceled
     * - empty orders (no items and no combos) are not canceled
     * <p>
     * Side effects:
     * - re-fetches the order to avoid stale totals, sets order status to CANCELED, flushes
     * - cancels the transaction if present and not already canceled/refunded
     * - notifies assigned waiters (best-effort)
     *
     * @param order order to evaluate (may be {@code null})
     * @param authenticatedUser actor user (may be {@code null})
     * @param hasUserId whether to set {@code updatedBy} from {@code authenticatedUser}
     * @param userLocale locale for notifications/messages (may be {@code null})
     */
    @Override
    public void checkAndCancelOrderIfAllHoldPushedItemsCanceled(Order order, User authenticatedUser, boolean hasUserId, Locale userLocale) {
        if (order == null) {
            return;
        }
        
        UUID orderId = order.getId();
        
        // Get ALL items in the order (excluding combo items, as they are part of combos)
        List<OrderedItem> allItems = orderedItemRepository.findByOrderId(orderId).stream()
                .filter(item -> item.getOrderedCombo() == null) // Only regular items, not combo items
                .collect(Collectors.toList());
        
        // Get ALL combos in the order
        List<OrderedCombo> allCombos = orderedComboRepository.findByOrderId(orderId);
        
        // Check if there are any items/combos in statuses other than ON_HOLD, PUSHED, or CANCELED
        // If there are items in COOKING, READY, SERVED, DELAYED, etc., do NOT cancel the order
        boolean hasItemsInOtherStatuses = allItems.stream()
                .anyMatch(item -> item.getItemStatus() != ItemStatus.ON_HOLD 
                        && item.getItemStatus() != ItemStatus.PUSHED 
                        && item.getItemStatus() != ItemStatus.CANCELED);
        
        boolean hasCombosInOtherStatuses = allCombos.stream()
                .anyMatch(combo -> combo.getItemStatus() != ItemStatus.ON_HOLD 
                        && combo.getItemStatus() != ItemStatus.PUSHED 
                        && combo.getItemStatus() != ItemStatus.CANCELED);
        
        // If there are items/combos in other statuses, do NOT cancel the order
        if (hasItemsInOtherStatuses || hasCombosInOtherStatuses) {
            log.debug("Order {} has items/combos in statuses other than ON_HOLD/PUSHED/CANCELED. Order will not be canceled.", orderId);
            return;
        }
        
        // At this point, all items/combos are either ON_HOLD, PUSHED, or CANCELED
        // Check if all ON_HOLD/PUSHED items/combos have been canceled
        List<OrderedItem> remainingHoldPushedItems = allItems.stream()
                .filter(item -> item.getItemStatus() == ItemStatus.ON_HOLD || item.getItemStatus() == ItemStatus.PUSHED)
                .collect(Collectors.toList());
        
        List<OrderedCombo> remainingHoldPushedCombos = allCombos.stream()
                .filter(combo -> combo.getItemStatus() == ItemStatus.ON_HOLD || combo.getItemStatus() == ItemStatus.PUSHED)
                .collect(Collectors.toList());
        
        // If there are no remaining items/combos in ON_HOLD or PUSHED status,
        // and all items/combos are either CANCELED or were ON_HOLD/PUSHED (now canceled),
        // then cancel the order and transaction
        if (remainingHoldPushedItems.isEmpty() && remainingHoldPushedCombos.isEmpty()) {
            // Verify that there are actually items/combos in the order (not an empty order)
            if (allItems.isEmpty() && allCombos.isEmpty()) {
                log.debug("Order {} has no items or combos. Skipping cancellation check.", orderId);
                return;
            }
            
            cancelOrderAndTransactionAndNotifyWaiters(orderId, authenticatedUser, hasUserId, userLocale);
        }
    }

    /**
     * Marks the order canceled, cancels an open transaction when present, and notifies assigned waiters.
     */
    private void cancelOrderAndTransactionAndNotifyWaiters(UUID orderId, User authenticatedUser, boolean hasUserId, Locale userLocale) {
        log.info("All items/combos in order {} are either CANCELED or were ON_HOLD/PUSHED (now canceled). Canceling order and transaction.", orderId);

        Order freshOrder = orderRepository.findById(orderId)
                .orElseThrow(() -> new RuntimeException(ERROR_ORDER_NOT_FOUND_PREFIX + orderId));

        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        freshOrder.setOrderStatus(OrderStatus.CANCELED);
        freshOrder.setUpdatedAt(now);
        if (hasUserId && authenticatedUser != null) {
            freshOrder.setUpdatedBy(authenticatedUser);
        }
        orderRepository.save(freshOrder);
        orderRepository.flush();

        cancelTransactionIfPresent(orderId, now);
        notifyAssignedWaitersOrderCancelledBestEffort(freshOrder, orderId, userLocale);
    }

    /**
     * Sets the order's transaction to {@link TransactionStatus#CANCELED} when one exists and it is not already canceled or refunded.
     */
    private void cancelTransactionIfPresent(UUID orderId, OffsetDateTime now) {
        Optional<Transaction> transactionOpt = transactionRepository.findByOrderId(orderId);
        if (transactionOpt.isEmpty()) {
            return;
        }
        Transaction transaction = transactionOpt.get();
        TransactionStatus currentStatus = transaction.getTransactionStatus();
        // Never downgrade a COMPLETED transaction to CANCELED.
        // Also avoid canceling if already canceled/refunded/partially refunded.
        if (currentStatus == TransactionStatus.CANCELED
                || currentStatus == TransactionStatus.REFUNDED
                || currentStatus == TransactionStatus.PARTIALLY_REFUNDED
                || currentStatus == TransactionStatus.COMPLETED) {
            return;
        }
        transaction.setTransactionStatus(TransactionStatus.CANCELED);
        transaction.setUpdatedAt(now);
        transactionRepository.save(transaction);
        log.info("Transaction {} canceled automatically due to all items being ON_HOLD/PUSHED and canceled", transaction.getId());
    }

    /**
     * Notifies waiters assigned to the order table that the order was auto-canceled; failures are logged without throwing.
     */
    private void notifyAssignedWaitersOrderCancelledBestEffort(Order freshOrder, UUID orderId, Locale userLocale) {
        if (freshOrder.getRestaurantTable() == null || notificationService == null) {
            return;
        }
        try {
            List<User> assignedWaiters = orderValidationService.getWaitersForTable(freshOrder.getRestaurantTable());
            if (assignedWaiters == null || assignedWaiters.isEmpty()) {
                String tableOrder = getTableOrderString(freshOrder);
                log.debug("No waiters assigned to table {} for order {} automatic cancellation - skipping waiter notification",
                        tableOrder, orderId);
                return;
            }
            for (User assignedWaiter : assignedWaiters) {
                if (assignedWaiter != null) {
                    notifyWaiterOrderCancelled(freshOrder, assignedWaiter, orderId, userLocale);
                }
            }
            String tableOrder = getTableOrderString(freshOrder);
            log.info("Sent order cancellation notifications to {} waiter(s) for order {} at table {} (automatic cancellation)",
                    assignedWaiters.size(), orderId, tableOrder);
        } catch (Exception e) {
            log.error("Failed to send order cancellation notification to waiters for order {}: {}", orderId, e.getMessage(), e);
        }
    }

    // ==================== HELPER METHODS ====================

    /**
     * Inner class for BXGY item information
     */
    // BxgyItemInfo moved to shared-library: com.gulfnet.shared_library.model.response.dto.BxgyItemInfo

    private UUID getMenuIdFromOrder(Order order) {
        if (order == null) {
            return null;
        }
        
        // Try to get from session - Session doesn't have direct menu reference
        // Menu is typically accessed through restaurant or other relationships
        // For now, return null and let reconstruction work without menuId
        return null;
    }

    // Methods moved to OrderValidationService - removed duplicate implementations:
    // - getCategoryItemMappingForOrderedItem
    // - reconstructBxgyInfo

    /**
     * Converts a persisted {@link OrderedItem} into an {@link OrderedItemRequest} suitable for recalculation.
     * <p>
     * Includes reconstruction of BXGY metadata from persisted fields and uses modifier repository reads to rebuild
     * modifier selections. If an {@code updateRequest} is provided, it is used as the source of truth for the updated
     * item (quantity/notes/discount flags/modifiers/free quantity).
     *
     * @param orderedItem persisted ordered item (required)
     * @param menuId menu id used for BXGY/id completion (may be {@code null})
     * @param updateRequest optional update payload for the target item
     * @return request representation of the ordered item for calculation
     */
    private OrderedItemRequest convertOrderedItemToRequest(OrderedItem orderedItem, UUID menuId, UpdateOrderedItemRequest updateRequest) {
        // Reconstruct BXGY info from database
        BxgyItemInfo bxgyInfo = orderValidationService.reconstructBxgyInfo(orderedItem, menuId);
        
        // Get modifiers for this item
        List<OrderedItemModifier> modifiers = orderedItemModifierRepository.findByOrderedItemId(orderedItem.getId());
        List<OrderedItemModifierRequest> modifierRequests = new ArrayList<>();
        
        if (modifiers != null && !modifiers.isEmpty()) {
            // Group modifiers by modifier group
            Map<UUID, List<OrderedItemModifier>> modifiersByGroup = modifiers.stream()
                .collect(Collectors.groupingBy(m -> m.getModifierGroup().getId()));
            
            for (Map.Entry<UUID, List<OrderedItemModifier>> entry : modifiersByGroup.entrySet()) {
                List<UUID> modifierItemIds = entry.getValue().stream()
                    .map(m -> m.getModifierItem().getId())
                    .collect(Collectors.toList());
                
                modifierRequests.add(OrderedItemModifierRequest.builder()
                    .modifierGroupId(entry.getKey())
                    .modifierItemIds(modifierItemIds)
                    .build());
            }
        }
        
        // Use update request values if provided, otherwise use existing values from database
        List<UUID> discountIds = resolveDiscountIds(updateRequest, orderedItem);
        Boolean isBuyItem = resolveIsBuyItem(updateRequest, orderedItem);
        Boolean isGetItem = resolveIsGetItem(updateRequest, orderedItem);
        Integer quantity = resolveQuantity(updateRequest, orderedItem);
        String notes = resolveNotes(updateRequest, orderedItem);
        List<OrderedItemModifierRequest> orderedItemModifiers = resolveOrderedItemModifiers(updateRequest, modifierRequests);
        Integer freeQuantity = resolveFreeQuantity(updateRequest, orderedItem);
        
        // Ensure BXGY discount IDs are included when isBuyItem or isGetItem is true
        if ((Boolean.TRUE.equals(isBuyItem) || Boolean.TRUE.equals(isGetItem)) && menuId != null) {
            OrderedItemRequest tempRequest = OrderedItemRequest.builder()
                .itemId(orderedItem.getItem().getId())
                .quantity(quantity)
                .discountIds(discountIds)
                .isBuyItem(isBuyItem)
                .isGetItem(isGetItem)
                .build();
            discountIds = orderPricingService.ensureBxgyDiscountIdsIncluded(tempRequest, menuId);
        }
        
        return OrderedItemRequest.builder()
            .orderedItemId(orderedItem.getId())
            .itemId(orderedItem.getItem().getId())
            .quantity(quantity)
            .notes(notes)
            .freeQuantity(freeQuantity)
            .discountIds(discountIds.isEmpty() ? null : discountIds)
            .isBuyItem(isBuyItem)
            .isGetItem(isGetItem)
            .orderedItemModifiers(orderedItemModifiers)
            .build();
    }

    private List<UUID> resolveDiscountIds(UpdateOrderedItemRequest updateRequest, OrderedItem orderedItem) {
        if (updateRequest != null && updateRequest.getDiscountIds() != null) {
            return updateRequest.getDiscountIds();
        }
        if (orderedItem.getDiscountId() != null) {
            return java.util.Collections.singletonList(orderedItem.getDiscountId());
        }
        return new ArrayList<>();
    }

    private Boolean resolveIsBuyItem(UpdateOrderedItemRequest updateRequest, OrderedItem orderedItem) {
        if (updateRequest != null && updateRequest.getIsBuyItem() != null) {
            return updateRequest.getIsBuyItem();
        }
        return orderedItem.getBxgyRole() == BxgyRole.BUY;
    }

    private Boolean resolveIsGetItem(UpdateOrderedItemRequest updateRequest, OrderedItem orderedItem) {
        if (updateRequest != null && updateRequest.getIsGetItem() != null) {
            return updateRequest.getIsGetItem();
        }
        return orderedItem.getBxgyRole() == BxgyRole.GET;
    }

    private Integer resolveQuantity(UpdateOrderedItemRequest updateRequest, OrderedItem orderedItem) {
        if (updateRequest != null && updateRequest.getQuantity() != null) {
            return updateRequest.getQuantity();
        }
        return orderedItem.getQuantity();
    }

    private String resolveNotes(UpdateOrderedItemRequest updateRequest, OrderedItem orderedItem) {
        if (updateRequest != null && updateRequest.getNotes() != null) {
            return updateRequest.getNotes();
        }
        return orderedItem.getNotes();
    }

    private List<OrderedItemModifierRequest> resolveOrderedItemModifiers(UpdateOrderedItemRequest updateRequest,
                                                                        List<OrderedItemModifierRequest> modifierRequests) {
        if (updateRequest != null && updateRequest.getOrderedItemModifiers() != null) {
            return updateRequest.getOrderedItemModifiers();
        }
        return modifierRequests.isEmpty() ? null : modifierRequests;
    }

    private Integer resolveFreeQuantity(UpdateOrderedItemRequest updateRequest, OrderedItem orderedItem) {
        if (updateRequest != null) {
            return updateRequest.getFreeQuantity();
        }
        return orderedItem.getFreeQuantity();
    }

    /**
     * Converts a list of persisted ordered items into calculation requests.
     * <p>
     * For the item matching {@code updatedItemId}, uses {@code updateRequest} to reflect the new state; for all other items,
     * creates a synthetic update request based on current persisted values.
     *
     * @param orderedItems items to convert (required)
     * @param menuId menu id for calculation context (may be {@code null})
     * @param updateRequest update request to apply to {@code updatedItemId} (may be {@code null})
     * @param updatedItemId id of the item being updated (required)
     * @return list of ordered-item requests
     */
    private List<OrderedItemRequest> convertOrderedItemsToRequests(
            List<OrderedItem> orderedItems, UUID menuId, UpdateOrderedItemRequest updateRequest, UUID updatedItemId) {
        return orderedItems.stream()
            .map(item -> {
                // For the item being updated, use the update request values
                if (item.getId().equals(updatedItemId) && updateRequest != null) {
                    return convertOrderedItemToRequest(item, menuId, updateRequest);
                } else {
                    // For other items, use existing values (no update request)
                    return convertOrderedItemToRequest(item, menuId, 
                        UpdateOrderedItemRequest.builder()
                            .menuId(menuId)
                            .quantity(item.getQuantity())
                            .notes(item.getNotes())
                            .build());
                }
            })
            .collect(Collectors.toList());
    }

    private OrderedComboRequest convertToComboRequest(UpdateOrderedComboRequest updateRequest, UUID comboId) {
        return OrderedComboRequest.builder()
                .comboId(comboId)
                .quantity(updateRequest.getQuantity())
                .notes(updateRequest.getNotes())
                .comboGroups(updateRequest.getComboGroups())
                .build();
    }

    // ==================== WASTAGE STATUS HELPER METHODS ====================

    /**
     * Captures the current item status as wastage source status before cancellation.
     * This preserves the status (COOKING/READY/SERVED) for wastage reporting.
     * Only sets wastage_source_status if:
     * 1. Current status is one of the wastage-eligible statuses (COOKING, READY, SERVED)
     * 2. wastage_source_status is not already set (to preserve existing value)
     */
    private void captureWastageSourceStatus(OrderedItem item) {
        if (item == null) return;
        
        // Only set wastage_source_status if:
        // 1. Current status is not null and not already CANCELED
        // 2. Current status is one of the wastage-eligible statuses
        // 3. wastage_source_status is not already set (preserve existing value)
        if (item.getItemStatus() != null 
                && item.getItemStatus() != ItemStatus.CANCELED
                && (item.getItemStatus() == ItemStatus.COOKING 
                    || item.getItemStatus() == ItemStatus.READY 
                    || item.getItemStatus() == ItemStatus.SERVED)
                && item.getWastageSourceStatus() == null) {
            item.setWastageSourceStatus(item.getItemStatus());
            log.debug("Captured wastage source status {} for item {}", item.getItemStatus(), item.getId());
        }
    }

    /**
     * Captures the current combo status as wastage source status before cancellation.
     * This preserves the status (COOKING/READY/SERVED) for wastage reporting.
     * Only sets wastage_source_status if:
     * 1. Current status is one of the wastage-eligible statuses (COOKING, READY, SERVED)
     * 2. wastage_source_status is not already set (to preserve existing value)
     */
    private void captureWastageSourceStatus(OrderedCombo combo) {
        if (combo == null) return;
        
        // Only set wastage_source_status if:
        // 1. Current status is not null and not already CANCELED
        // 2. Current status is one of the wastage-eligible statuses
        // 3. wastage_source_status is not already set (preserve existing value)
        if (combo.getItemStatus() != null 
                && combo.getItemStatus() != ItemStatus.CANCELED
                && (combo.getItemStatus() == ItemStatus.COOKING 
                    || combo.getItemStatus() == ItemStatus.READY 
                    || combo.getItemStatus() == ItemStatus.SERVED)
                && combo.getWastageSourceStatus() == null) {
            combo.setWastageSourceStatus(combo.getItemStatus());
            log.debug("Captured wastage source status {} for combo {}", combo.getItemStatus(), combo.getId());
        }
    }

    // ==================== COMPLEX RECALCULATION METHODS ====================

    /**
     * Adjusts GET items in a BXGY discount application based on mathematical eligibility.
     * 
     * Formula: eligibleFreeQty = floor(activeBuyQty / buyQuantity) * getQuantity
     * If activeGetQty > eligibleFreeQty, cancels or reduces GET items to match eligible quantity.
     * 
     * @param discountApplicationId The BXGY discount application ID
     * @param discountId The discount ID (to fetch buyQuantity and getQuantity)
     * @param authenticatedUser User performing the action
     * @param hasUserId Whether user ID should be set
     * @param userLocale User locale for notifications
     */
    private void adjustBxgyGetItemsAfterBuyCancellation(
            UUID discountApplicationId,
            UUID discountId,
            User authenticatedUser,
            boolean hasUserId,
            Locale userLocale) {
        
        if (discountApplicationId == null || discountId == null) {
            log.warn("Cannot adjust BXGY GET items: discountApplicationId or discountId is null");
            return;
        }
        
        // Step 1: Fetch all items in this discount application
        List<OrderedItem> items = orderedItemRepository.findByDiscountApplicationId(discountApplicationId);
        
        if (items.isEmpty()) {
            log.warn("No items found for discount_application_id: {}", discountApplicationId);
            return;
        }
        
        // Step 2: Fetch discount to get buyQuantity and getQuantity
        Discount discount = discountRepository.findById(discountId).orElse(null);
        if (discount == null) {
            log.warn("Discount {} not found for discount_application_id: {}", discountId, discountApplicationId);
            return;
        }
        
        int buyQuantityRequired = discount.getBuyQuantity() != null ? discount.getBuyQuantity() : 1;
        int getQuantityFree = discount.getGetQuantity() != null ? discount.getGetQuantity() : 1;
        
        // Step 3: Calculate active BUY and GET quantities (excluding cancelled items)
        int activeBuyQty = items.stream()
                .filter(i -> i.getBxgyRole() == BxgyRole.BUY)
                .filter(i -> i.getItemStatus() != ItemStatus.CANCELED)
                .mapToInt(OrderedItem::getQuantity)
                .sum();
        
        int activeGetQty = items.stream()
                .filter(i -> i.getBxgyRole() == BxgyRole.GET)
                .filter(i -> i.getItemStatus() != ItemStatus.CANCELED)
                .mapToInt(OrderedItem::getQuantity)
                .sum();
        
        // Step 4: Compute eligible free quantity
        int eligibleFreeQty = (activeBuyQty / buyQuantityRequired) * getQuantityFree;
        
        log.info("BXGY GET adjustment calculation - discountApplicationId: {}, activeBuyQty: {}, activeGetQty: {}, buyQuantityRequired: {}, getQuantityFree: {}, eligibleFreeQty: {}",
                discountApplicationId, activeBuyQty, activeGetQty, buyQuantityRequired, getQuantityFree, eligibleFreeQty);
        
        // Step 5: If excess GET exists, adjust quantity
        if (activeGetQty > eligibleFreeQty) {
            int excessQty = activeGetQty - eligibleFreeQty;
            
            log.info("Excess GET quantity detected: {}. Adjusting GET items for discount_application_id: {}", 
                    excessQty, discountApplicationId);
            
            // Get all active GET items
            List<OrderedItem> getItems = items.stream()
                    .filter(i -> i.getBxgyRole() == BxgyRole.GET)
                    .filter(i -> i.getItemStatus() != ItemStatus.CANCELED)
                    .collect(Collectors.toList());
            
            // Process GET items to cancel or reduce excess quantity
            for (OrderedItem getItem : getItems) {
                if (excessQty <= 0) {
                    break;
                }
                
                int itemQty = getItem.getQuantity();
                
                if (itemQty <= excessQty) {
                    // Cancel full row
                    log.info("Cancelling full GET item {} (quantity: {}) due to excess quantity", 
                            getItem.getId(), itemQty);
                    captureWastageSourceStatus(getItem);
                    getItem.setItemStatus(ItemStatus.CANCELED);
                    getItem.setUpdatedAt(OffsetDateTime.now(ZoneOffset.UTC));
                    if (hasUserId && authenticatedUser != null) {
                        getItem.setUpdatedBy(authenticatedUser);
                    }
                    excessQty -= itemQty;
                    
                    // Send KDS notification for cancelled GET item
                    try {
                        notificationService.notifyItemCanceled(getItem, java.util.Collections.emptyList(), userLocale);
                        log.info("Sent KDS notification for cancelled GET item: {}", getItem.getId());
                    } catch (Exception e) {
                        log.warn("Failed to send KDS notification for cancelled GET item {}: {}", 
                                getItem.getId(), e.getMessage());
                    }
                } else {
                    // Partial reduction
                    int newQty = itemQty - excessQty;
                    log.info("Reducing GET item {} quantity from {} to {} due to excess quantity", 
                            getItem.getId(), itemQty, newQty);
                    getItem.setQuantity(newQty);
                    getItem.setUpdatedAt(OffsetDateTime.now(ZoneOffset.UTC));
                    if (hasUserId && authenticatedUser != null) {
                        getItem.setUpdatedBy(authenticatedUser);
                    }
                    excessQty = 0;
                }
                
                orderedItemRepository.save(getItem);
            }
            
            log.info("Completed BXGY GET adjustment for discount_application_id: {}. Remaining excess: {}", 
                    discountApplicationId, excessQty);
        } else {
            log.info("No excess GET quantity. activeGetQty ({}) <= eligibleFreeQty ({})", 
                    activeGetQty, eligibleFreeQty);
        }
    }

    /**
     * Bulk-cancels ordered items and recalculates each affected order once.
     * <p>
     * Side effects per item:
     * - sets item status to CANCELED (capturing wastage source status where applicable)
     * - auto-adjusts BXGY GET items when canceling a BUY item (best-effort; operates on persisted rows)
     * - sends KDS-scoped cancellation notification (best-effort)
     * <p>
     * After all items are updated, performs a single flush/clear to enable batching, then recalculates each affected order.
     *
     * @param orderedItems items to cancel (may be {@code null} or empty)
     * @param authenticatedUser actor user (may be {@code null})
     * @param hasUserId whether to set {@code updatedBy} from {@code authenticatedUser}
     * @param userLocale locale for notifications/messages (may be {@code null})
     */
    @Override
    public void deductItemsAmountFromOrder(Collection<OrderedItem> orderedItems, User authenticatedUser, boolean hasUserId, Locale userLocale) {
        if (orderedItems == null || orderedItems.isEmpty()) {
            return;
        }

        Set<UUID> affectedOrderIds = new HashSet<>();
        
        for (OrderedItem orderedItem : orderedItems) {
            UUID orderId = orderedItem.getOrder().getId();
            UUID itemId = orderedItem.getId();
            log.info("Batch deduction: Preparing order {} after cancelling item {}", orderId, itemId);
            
            // Get restaurant ID for WebSocket notification
            UUID restaurantId = orderNotificationService.getRestaurantIdFromOrder(orderedItem.getOrder());
            
            // Get restaurant for audit trail (before entity manager is cleared)
            Restaurant restaurant = orderedItem.getOrder() != null && orderedItem.getOrder().getRestaurant() != null
                ? orderedItem.getOrder().getRestaurant()
                : null;
            
            // Set item status to cancelled
            captureWastageSourceStatus(orderedItem);
            orderedItem.setItemStatus(ItemStatus.CANCELED);
            orderedItem.setUpdatedAt(OffsetDateTime.now(ZoneOffset.UTC));
            if (hasUserId && authenticatedUser != null) {
                orderedItem.setUpdatedBy(authenticatedUser);
            }
            orderedItemRepository.save(orderedItem);
            
            // Auto-adjust GET items if this is a BUY item in a BXGY discount
            if (orderedItem.getDiscountApplicationId() != null && 
                orderedItem.getBxgyRole() == BxgyRole.BUY &&
                orderedItem.getDiscountId() != null) {
                log.info("BUY item {} cancelled (bulk operation) - adjusting related GET items with discount_application_id: {}", 
                    itemId, orderedItem.getDiscountApplicationId());
                
                adjustBxgyGetItemsAfterBuyCancellation(
                    orderedItem.getDiscountApplicationId(),
                    orderedItem.getDiscountId(),
                    authenticatedUser,
                    hasUserId,
                    userLocale
                );
            }
            
            // Create audit trail for item cancellation
            try {
                auditTrailService.createAuditTrail(
                        authenticatedUser,
                        ActionType.CANCELLATION,
                        restaurant,
                        RequestStatus.NA, // Direct cancellation doesn't require approval
                        null, // ipAddress
                        null, // userAgent
                        itemId,
                        "ITEM",
                        "Item cancelled in bulk operation"
                );
            } catch (Exception e) {
                log.error("Failed to create audit trail for item cancellation (item {}): {}", itemId, e.getMessage(), e);
                // Don't break the flow if audit trail fails
            }
            
            // Send KDS-specific notification (user-scoped, not broadcast)
            // Skip broadcast to /topic/restaurant/{id}/item-status for CANCELED — broadcasting would
            // leak to all KDS. Instead, notify only assigned KDS users via notifyItemCanceled.
            notifyItemCanceledBestEffort(orderedItem, userLocale);
            
            affectedOrderIds.add(orderId);
        }
        
        // Single flush and clear for all items to enable JDBC batching (configured with batch_size=20)
        orderedItemRepository.flush();
        entityManager.clear();
        
        recalculateOrdersAfterBatchChange(
                affectedOrderIds, authenticatedUser, hasUserId, userLocale, "bulk item cancellation");
    }

    /**
     * Bulk-cancels ordered combos and recalculates each affected order once.
     * <p>
     * Side effects per combo:
     * - sets combo status to CANCELED (capturing wastage source status where applicable)
     * - sends KDS-scoped cancellation notifications for each combo item (best-effort)
     * <p>
     * After all combos are updated, performs a single flush/clear to enable batching, then recalculates each affected order.
     *
     * @param orderedCombos combos to cancel (may be {@code null} or empty)
     * @param authenticatedUser actor user (may be {@code null})
     * @param hasUserId whether to set {@code updatedBy} from {@code authenticatedUser}
     * @param userLocale locale for notifications/messages (may be {@code null})
     */
    @Override
    public void deductCombosAmountFromOrder(Collection<OrderedCombo> orderedCombos, User authenticatedUser, boolean hasUserId, Locale userLocale) {
        if (orderedCombos == null || orderedCombos.isEmpty()) {
            return;
        }

        Set<UUID> affectedOrderIds = new HashSet<>();
        
        for (OrderedCombo orderedCombo : orderedCombos) {
            UUID orderId = orderedCombo.getOrder().getId();
            UUID comboId = orderedCombo.getId();
            log.info("Batch deduction: Preparing order {} after cancelling combo {}", orderId, comboId);
            
            // Get restaurant ID for WebSocket notification
            UUID restaurantId = orderNotificationService.getRestaurantIdFromOrder(orderedCombo.getOrder());
            
            // Set combo status to cancelled
            captureWastageSourceStatus(orderedCombo);
            orderedCombo.setItemStatus(ItemStatus.CANCELED);
            orderedCombo.setUpdatedAt(OffsetDateTime.now(ZoneOffset.UTC));
            if (hasUserId && authenticatedUser != null) {
                orderedCombo.setUpdatedBy(authenticatedUser);
            }
            orderedComboRepository.save(orderedCombo);
            
            // Send KDS-specific notification for each combo item (user-scoped, not broadcast)
            // Skip broadcast to /topic/restaurant/{id}/item-status for CANCELED — broadcasting would
            // leak to all KDS. Instead, notify only assigned KDS users via notifyItemCanceled.
            notifyComboCanceledBestEffort(comboId, userLocale);
            
            affectedOrderIds.add(orderId);
        }
        
        // Single flush and clear for all combos to enable JDBC batching
        orderedComboRepository.flush();
        entityManager.clear();
        
        recalculateOrdersAfterBatchChange(
                affectedOrderIds, authenticatedUser, hasUserId, userLocale, "bulk combo cancellation");
    }

    /**
     * Cancels a single ordered item and triggers a full order recalculation.
     * <p>
     * This method is careful about persistence ordering:
     * - flushes the item cancellation before recalculation
     * - optionally adjusts BXGY GET items when canceling a BUY item
     * - clears and re-reads entities to avoid stale persistence-context results
     *
     * @param orderedItem item to cancel (required)
     * @param authenticatedUser actor user (may be {@code null})
     * @param hasUserId whether to set {@code updatedBy} from {@code authenticatedUser}
     * @param userLocale locale for notifications/messages (may be {@code null})
     */
    @Override
    public void deductItemAmountFromOrder(OrderedItem orderedItem, User authenticatedUser, boolean hasUserId, Locale userLocale) {
        UUID orderId = orderedItem.getOrder().getId();
        UUID itemId = orderedItem.getId();
        log.info("Recalculating order {} after cancelling item {}", orderId, itemId);
        
        // Step 1: Get restaurant ID for WebSocket notification before any entity operations
        UUID restaurantId = orderNotificationService.getRestaurantIdFromOrder(orderedItem.getOrder());
        
        // Step 2: Set item status to cancelled and persist immediately
        captureWastageSourceStatus(orderedItem);
        orderedItem.setItemStatus(ItemStatus.CANCELED);
        orderedItem.setUpdatedAt(OffsetDateTime.now(ZoneOffset.UTC));
        if (hasUserId && authenticatedUser != null) {
            orderedItem.setUpdatedBy(authenticatedUser);
        }
        orderedItemRepository.save(orderedItem);
        orderedItemRepository.flush(); // CRITICAL: Force immediate database write
        
        // Step 2.5: Auto-adjust GET items if this is a BUY item in a BXGY discount
        if (orderedItem.getDiscountApplicationId() != null && 
            orderedItem.getBxgyRole() == BxgyRole.BUY &&
            orderedItem.getDiscountId() != null) {
            log.info("BUY item {} cancelled - adjusting related GET items with discount_application_id: {}", 
                itemId, orderedItem.getDiscountApplicationId());
            
            adjustBxgyGetItemsAfterBuyCancellation(
                orderedItem.getDiscountApplicationId(),
                orderedItem.getDiscountId(),
                authenticatedUser,
                hasUserId,
                userLocale
            );
            
            // Flush to ensure GET items are persisted
            orderedItemRepository.flush();
        }
        
        // Step 3: Send KDS-specific notification (user-scoped, not broadcast)
        // Skip broadcast to /topic/restaurant/{id}/item-status for CANCELED — broadcasting would
        // leak to all KDS. Instead, notify only assigned KDS users via notifyItemCanceled.
        notifyItemCanceledBestEffort(orderedItem, userLocale);
        
        // Step 4: CRITICAL - Verify the item status was persisted before proceeding
        // Re-fetch the item directly to ensure the cancelled status is committed
        entityManager.clear();
        OrderedItem verifiedItem = orderedItemRepository.findById(itemId)
                .orElseThrow(() -> new RuntimeException(ERROR_ORDERED_ITEM_NOT_FOUND_PREFIX + itemId));
        
        if (verifiedItem.getItemStatus() != ItemStatus.CANCELED) {
            log.error("CRITICAL: Item {} status was not properly persisted as CANCELED. Current status: {}", 
                    itemId, verifiedItem.getItemStatus());
            // Force update and flush again
            if (verifiedItem.getWastageSourceStatus() == null && verifiedItem.getItemStatus() != null 
                    && verifiedItem.getItemStatus() != ItemStatus.CANCELED) {
                verifiedItem.setWastageSourceStatus(verifiedItem.getItemStatus());
            }
            verifiedItem.setItemStatus(ItemStatus.CANCELED);
            verifiedItem.setUpdatedAt(OffsetDateTime.now(ZoneOffset.UTC));
            if (hasUserId && authenticatedUser != null) {
                verifiedItem.setUpdatedBy(authenticatedUser);
            }
            orderedItemRepository.save(verifiedItem);
            orderedItemRepository.flush();
            entityManager.clear();
        }
        
        log.info("Verified item {} status is CANCELED, proceeding with order recalculation", itemId);
        
        // Step 5: Re-fetch order from database to ensure we have latest state
        // This is critical - we need fresh data after the flush and clear
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new RuntimeException(ERROR_ORDER_NOT_FOUND_PREFIX + orderId));
        
        // Step 6: Use the comprehensive recalculation method that properly handles BXGY discounts
        // This method will:
        // - Reload all items and combos from database (excluding cancelled ones)
        // - Recalculate BXGY discounts from scratch based on remaining active items
        // - Recalculate all totals (subtotal, discounts, taxes, service charges, etc.)
        // - Update and save the order
        recalculateOrderAfterItemChange(order, authenticatedUser, hasUserId, userLocale);
        
        log.info("Order {} updated successfully after item cancellation", orderId);
    }

    /**
     * Cancels a single ordered combo and triggers a full order recalculation.
     * <p>
     * Ensures combo cancellation is persisted before recalculation by flushing and re-reading the combo.
     * Also sends KDS-scoped cancellation notifications for each combo item (best-effort).
     *
     * @param orderedCombo combo to cancel (required)
     * @param authenticatedUser actor user (may be {@code null})
     * @param hasUserId whether to set {@code updatedBy} from {@code authenticatedUser}
     * @param userLocale locale for notifications/messages (may be {@code null})
     */
    @Override
    public void deductComboAmountFromOrder(OrderedCombo orderedCombo, User authenticatedUser, boolean hasUserId, Locale userLocale) {
        UUID orderId = orderedCombo.getOrder().getId();
        UUID comboId = orderedCombo.getId();
        log.info("Recalculating order {} after cancelling combo {}", orderId, comboId);
        
        // Step 1: Get restaurant ID for WebSocket notification before any entity operations
        UUID restaurantId = orderNotificationService.getRestaurantIdFromOrder(orderedCombo.getOrder());
        
        // Step 2: Set combo status to cancelled and persist immediately
        captureWastageSourceStatus(orderedCombo);
        orderedCombo.setItemStatus(ItemStatus.CANCELED);
        orderedCombo.setUpdatedAt(OffsetDateTime.now(ZoneOffset.UTC));
        if (hasUserId && authenticatedUser != null) {
            orderedCombo.setUpdatedBy(authenticatedUser);
        }
        orderedComboRepository.save(orderedCombo);
        orderedComboRepository.flush(); // CRITICAL: Force immediate database write
        
        // Step 3: Send KDS-specific notification for each combo item (user-scoped, not broadcast)
        // Skip broadcast to /topic/restaurant/{id}/item-status for CANCELED — broadcasting would
        // leak to all KDS. Instead, notify only assigned KDS users via notifyItemCanceled.
        notifyComboCanceledBestEffort(comboId, userLocale);
        
        // Step 4: CRITICAL - Verify the combo status was persisted before proceeding
        // Re-fetch the combo directly to ensure the cancelled status is committed
        entityManager.clear();
        OrderedCombo verifiedCombo = orderedComboRepository.findById(comboId)
                .orElseThrow(() -> new RuntimeException(ERROR_ORDERED_COMBO_NOT_FOUND_PREFIX + comboId));
        
        if (verifiedCombo.getItemStatus() != ItemStatus.CANCELED) {
            log.error("CRITICAL: Combo {} status was not properly persisted as CANCELED. Current status: {}", 
                    comboId, verifiedCombo.getItemStatus());
            // Force update and flush again
            if (verifiedCombo.getWastageSourceStatus() == null && verifiedCombo.getItemStatus() != null 
                    && verifiedCombo.getItemStatus() != ItemStatus.CANCELED) {
                verifiedCombo.setWastageSourceStatus(verifiedCombo.getItemStatus());
            }
            verifiedCombo.setItemStatus(ItemStatus.CANCELED);
            verifiedCombo.setUpdatedAt(OffsetDateTime.now(ZoneOffset.UTC));
            if (hasUserId && authenticatedUser != null) {
                verifiedCombo.setUpdatedBy(authenticatedUser);
            }
            orderedComboRepository.save(verifiedCombo);
            orderedComboRepository.flush();
            entityManager.clear();
        }
        
        log.info("Verified combo {} status is CANCELED, proceeding with order recalculation", comboId);
        
        // Step 5: Re-fetch order from database to ensure we have latest state
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new RuntimeException(ERROR_ORDER_NOT_FOUND_PREFIX + orderId));
        
        recalculateOrderAfterItemChange(order, authenticatedUser, hasUserId, userLocale);
        
        log.info("Order {} updated successfully after combo cancellation", orderId);
    }

    /**
     * Cancels an item (typically via cancellation request flow) and performs an order recalculation.
     * <p>
     * Includes:
     * - BXGY GET adjustment when canceling a BUY item
     * - KDS-scoped cancellation notification (best-effort)
     * - flush + entityManager.clear to avoid stale entities during recalculation
     *
     * @param orderedItem item being canceled (required)
     * @param authenticatedUser actor user (may be {@code null})
     * @param hasUserId whether to set {@code updatedBy} from {@code authenticatedUser}
     * @param userLocale locale for notifications/messages (may be {@code null})
     * @return recalculation result describing whether discounts were removed/recalculated
     */
    @Override
    public OrderRecalculationResult handleItemCancellation(OrderedItem orderedItem, User authenticatedUser, boolean hasUserId, Locale userLocale) {
        log.info("Item {} cancelled for order {}", orderedItem.getId(), orderedItem.getOrder().getId());
        
        // Get restaurant ID for WebSocket notification (safely handle null restaurant entity)
        UUID restaurantId = orderNotificationService.getRestaurantIdFromOrder(orderedItem.getOrder());
        
        // Set item status to cancelled
        captureWastageSourceStatus(orderedItem);
        orderedItem.setItemStatus(ItemStatus.CANCELED);
        orderedItem.setUpdatedAt(OffsetDateTime.now(ZoneOffset.UTC));
        if (hasUserId && authenticatedUser != null) {
            orderedItem.setUpdatedBy(authenticatedUser);
        }
        orderedItemRepository.save(orderedItem);
        
        // Auto-adjust GET items if this is a BUY item in a BXGY discount
        UUID itemId = orderedItem.getId();
        if (orderedItem.getDiscountApplicationId() != null && 
            orderedItem.getBxgyRole() == BxgyRole.BUY &&
            orderedItem.getDiscountId() != null) {
            log.info("BUY item {} cancelled (via cancellation request) - adjusting related GET items with discount_application_id: {}", 
                itemId, orderedItem.getDiscountApplicationId());
            
            adjustBxgyGetItemsAfterBuyCancellation(
                orderedItem.getDiscountApplicationId(),
                orderedItem.getDiscountId(),
                authenticatedUser,
                hasUserId,
                userLocale
            );
            
            // Flush to ensure GET items are persisted
            orderedItemRepository.flush();
        }

        // Send KDS-specific notification for item cancellation (user-scoped, not broadcast)
        // Skip broadcast to /topic/restaurant/{id}/item-status for CANCELED — broadcasting would
        // leak to all KDS. Instead, notify only assigned KDS users via notifyItemCanceled.
        notifyItemCanceledBestEffort(orderedItem, userLocale);
        
        // CRITICAL: Flush to ensure status is persisted to database before recalculation
        orderedItemRepository.flush();
        
        // CRITICAL: Clear entity manager cache to ensure fresh data is fetched
        // This prevents intermittent issues where findByOrderId() returns stale entities from persistence context
        entityManager.clear();
        
        // CRITICAL: Fetch fresh Order entity to avoid cached orderedItems collection in persistence context
        // This prevents intermittent issues where findByOrderId() returns stale entities from Order's cached collection
        Order order = orderRepository.findById(orderedItem.getOrder().getId())
                    .orElseThrow(() -> new RuntimeException(ERROR_ORDER_NOT_FOUND_PREFIX + orderedItem.getOrder().getId()));
        
        // Complete order recalculation including discount validation
        // Note: Discount fetching is handled inside recalculateOrderAfterItemChange to avoid lazy loading issues
        return recalculateOrderAfterItemChange(order, authenticatedUser, hasUserId, userLocale);
    }

    /**
     * Cancels a combo (typically via cancellation request flow) and performs an order recalculation.
     * <p>
     * Includes:
     * - KDS-scoped cancellation notifications for each combo item (best-effort)
     * - flush + entityManager.clear to avoid stale entities during recalculation
     *
     * @param orderedCombo combo being canceled (required)
     * @param authenticatedUser actor user (may be {@code null})
     * @param hasUserId whether to set {@code updatedBy} from {@code authenticatedUser}
     * @param userLocale locale for notifications/messages (may be {@code null})
     * @return recalculation result describing whether discounts were removed/recalculated
     */
    @Override
    public OrderRecalculationResult handleComboCancellation(OrderedCombo orderedCombo, User authenticatedUser, boolean hasUserId, Locale userLocale) {
        log.info("Combo {} cancelled for order {}", orderedCombo.getId(), orderedCombo.getOrder().getId());
        
        // Get restaurant ID for WebSocket notification (safely handle null restaurant entity)
        UUID restaurantId = orderNotificationService.getRestaurantIdFromOrder(orderedCombo.getOrder());
        
        // Set combo status to cancelled
        captureWastageSourceStatus(orderedCombo);
        orderedCombo.setItemStatus(ItemStatus.CANCELED);
        orderedCombo.setUpdatedAt(OffsetDateTime.now(ZoneOffset.UTC));
        if (hasUserId && authenticatedUser != null) {
            orderedCombo.setUpdatedBy(authenticatedUser);
        }
        orderedComboRepository.save(orderedCombo);
        
        // Also cancel child items belonging to this combo (if not already cancelled)
        // Keep item rows consistent with combo cancellation to avoid orphan "active" combo items.
        try {
            List<OrderedItem> comboItems = orderedItemRepository.findByOrderedComboId(orderedCombo.getId());
            if (comboItems != null && !comboItems.isEmpty()) {
                OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
                String comboReason = orderedCombo.getReason();
                for (OrderedItem comboItem : comboItems) {
                    if (comboItem == null) {
                        continue;
                    }
                    if (comboItem.getItemStatus() == ItemStatus.CANCELED) {
                        continue;
                    }
                    comboItem.setItemStatus(ItemStatus.CANCELED);
                    comboItem.setUpdatedAt(now);
                    if (hasUserId && authenticatedUser != null) {
                        comboItem.setUpdatedBy(authenticatedUser);
                    }
                    if (comboReason != null && (comboItem.getReason() == null || comboItem.getReason().trim().isEmpty())) {
                        comboItem.setReason(comboReason);
                    }
                }
                orderedItemRepository.saveAll(comboItems);
            }
        } catch (Exception e) {
            log.warn("Failed to cascade cancellation to combo items for combo {}: {}", orderedCombo.getId(), e.getMessage());
        }
        
        // Send KDS-specific notification for each combo item (user-scoped, not broadcast)
        // Skip broadcast to /topic/restaurant/{id}/item-status for CANCELED — broadcasting would
        // leak to all KDS. Instead, notify only assigned KDS users via notifyItemCanceled.
        notifyComboCanceledBestEffort(orderedCombo.getId(), userLocale);
        
        // CRITICAL: Flush to ensure status is persisted to database before recalculation
        orderedComboRepository.flush();
        orderedItemRepository.flush();
        
        // CRITICAL: Clear entity manager cache to ensure fresh data is fetched
        // This prevents intermittent issues where findByOrderId() returns stale entities from persistence context
        entityManager.clear();
        
        Order order = orderRepository.findById(orderedCombo.getOrder().getId())
                    .orElseThrow(() -> new RuntimeException(ERROR_ORDER_NOT_FOUND_PREFIX + orderedCombo.getOrder().getId()));
        
        return recalculateOrderAfterItemChange(order, authenticatedUser, hasUserId, userLocale);
    }

    private void notifyItemCanceledBestEffort(OrderedItem orderedItem, Locale userLocale) {
        if (notificationService == null || orderedItem == null) {
            return;
        }
        try {
            notificationService.notifyItemCanceled(orderedItem, java.util.Collections.emptyList(), userLocale);
        } catch (Exception e) {
            UUID itemId = orderedItem.getId();
            log.warn(LOG_KDS_ITEM_CANCEL_FAILED, itemId, e.getMessage());
        }
    }

    private void notifyComboCanceledBestEffort(UUID comboId, Locale userLocale) {
        if (notificationService == null || comboId == null) {
            return;
        }
        try {
            List<OrderedItem> comboItems = orderedItemRepository.findByOrderedComboId(comboId);
            notifyComboItemsCanceledBestEffort(comboItems, userLocale);
        } catch (Exception e) {
            log.warn(LOG_KDS_COMBO_CANCEL_FAILED, comboId, e.getMessage());
        }
    }

    /**
     * Sends per-line cancel notifications for items belonging to a combo, continuing on individual failures.
     */
    private void notifyComboItemsCanceledBestEffort(List<OrderedItem> comboItems, Locale userLocale) {
        if (comboItems == null || comboItems.isEmpty()) {
            return;
        }
        for (OrderedItem comboItem : comboItems) {
            if (comboItem == null || comboItem.getItem() == null) {
                continue;
            }
            try {
                notificationService.notifyItemCanceled(comboItem, java.util.Collections.emptyList(), userLocale);
            } catch (Exception e) {
                log.warn(LOG_KDS_COMBO_ITEM_CANCEL_FAILED, comboItem.getId(), e.getMessage());
            }
        }
    }

    /**
     * Recalculates each affected order after a batch item change (e.g. bulk cancel), logging the cancellation kind.
     */
    private void recalculateOrdersAfterBatchChange(
            Set<UUID> affectedOrderIds,
            User authenticatedUser,
            boolean hasUserId,
            Locale userLocale,
            String cancellationKindForLog) {
        for (UUID orderId : affectedOrderIds) {
            log.info("Batch deduction: Recalculating order {} after {}", orderId, cancellationKindForLog);
            Order order = orderRepository.findById(orderId)
                    .orElseThrow(() -> new RuntimeException(ERROR_ORDER_NOT_FOUND_PREFIX + orderId));
            recalculateOrderAfterItemChange(order, authenticatedUser, hasUserId, userLocale);
        }
    }

    // ==================== MAIN RECALCULATION METHOD ====================

    /**
     * Recalculates order totals, discounts (including BXGY), and derived tax/service-charge fields after an order change.
     * <p>
     * This method intentionally uses flush + {@code entityManager.clear()} + re-fetch patterns to avoid stale collections
     * and to ensure calculations reflect the latest committed item/combo statuses (especially during bulk cancellations).
     *
     * @param order order to recalculate (required)
     * @param authenticatedUser actor user (may be {@code null})
     * @param hasUserId whether to set {@code updatedBy} from {@code authenticatedUser}
     * @param userLocale locale for messages/notifications (may be {@code null})
     * @return recalculation result indicating discount removal/recalculation details
     */
    @Override
    public OrderRecalculationResult recalculateOrderAfterItemChange(Order order, User authenticatedUser, boolean hasUserId, Locale userLocale) {
        return recalculateOrderAfterItemChange(order, authenticatedUser, hasUserId, userLocale, null, null);
    }

    /**
     * {@inheritDoc}
     *
     * @param providedBxgyResult when non-null, reused instead of recomputing BXGY for this pass
     */
    @Override
    public OrderRecalculationResult recalculateOrderAfterItemChange(Order order, User authenticatedUser, boolean hasUserId, Locale userLocale, BxgyCalculationResult providedBxgyResult) {
        return recalculateOrderAfterItemChange(order, authenticatedUser, hasUserId, userLocale, providedBxgyResult, null);
    }

    /**
     * {@inheritDoc}
     *
     * @param providedBxgyResult     optional precomputed BXGY result
     * @param itemsForCalculation    when non-null and non-empty, new line items whose amounts are already persisted
     */
    @Override
    public OrderRecalculationResult recalculateOrderAfterItemChange(Order order, User authenticatedUser, boolean hasUserId, Locale userLocale, BxgyCalculationResult providedBxgyResult, List<OrderedItemRequest> itemsForCalculation) {
        log.info("Recalculating order {} after item change", order.getId());
        
        // CRITICAL: Get discount ID before clearing entity manager to avoid lazy loading issues
        // We need to access the discount ID while the entity is still managed
        UUID discountId = null;
        if (order.getDiscount() != null) {
            try {
                // Try to get the discount ID - this may trigger lazy loading, which is fine here
                discountId = order.getDiscount().getId();
            } catch (Exception e) {
                // If we can't get the ID (proxy not initialized), try to initialize it
                Hibernate.initialize(order.getDiscount());
                if (order.getDiscount() != null) {
                    discountId = order.getDiscount().getId();
                }
            }
        }
        
        // CRITICAL: Store order ID in final variable before clearing entity manager
        final UUID orderId = order.getId();
        
        // CRITICAL: Clear entity manager cache before querying to ensure we get fresh data from database
        // This is especially important in bulk operations where multiple items are cancelled
        entityManager.clear();
        
        // CRITICAL: Flush any pending changes to ensure database is up-to-date
        // This ensures that item status changes are committed before we query
        orderedItemRepository.flush();
        orderedComboRepository.flush();
        
        // CRITICAL: Re-fetch Order after clearing entity manager to ensure fresh data
        order = orderRepository.findById(orderId)
                .orElseThrow(() -> new RuntimeException(ERROR_ORDER_NOT_FOUND_PREFIX + orderId));
        
        // CRITICAL: Fetch Discount separately using the saved discount ID to avoid lazy loading issues
        Discount currentOrderDiscount = null;
        if (discountId != null) {
            currentOrderDiscount = discountRepository.findById(discountId).orElse(null);
        }
        
        // ==================== RECALCULATE BXGY FOR ENTIRE ORDER ====================
        // CRITICAL: Query items AFTER flush to ensure we see the latest committed statuses
        // Clear entity manager again to ensure no cached results
        entityManager.clear();

        List<OrderedItem> activeOrderedItems = loadActiveNonComboOrderedItemsForCalculation(orderId);
        
        log.info("Order {} - Active (non-cancelled, non-combo) items for calculation: {}", 
                orderId, activeOrderedItems.size());
        
        UUID restaurantId = orderNotificationService.getRestaurantIdSafely(order);
        UUID menuId = getMenuIdFromOrder(order);
        
        // ==================== RECALCULATE SUBTOTAL ====================
        // Use stored amounts for existing items (with orderedItemId), only recalculate for new items
        BigDecimal itemsSubTotal;
        BxgyCalculationResult bxgyResult = providedBxgyResult; // Use provided result if available
        
        log.info("Order {} - Starting subtotal calculation. itemsForCalculation: {}, activeOrderedItems: {}", 
                order.getId(), 
                itemsForCalculation != null ? itemsForCalculation.size() : 0, 
                activeOrderedItems.size());
        
        if (itemsForCalculation != null && !itemsForCalculation.isEmpty()) {
            // itemsForCalculation now only contains new items (without orderedItemId)
            // All items in itemsForCalculation are new and already saved with their calculated amounts
            // We'll use stored amounts for all items (both existing and newly created)
            log.info("New items detected ({} items). Using stored amounts for all items (existing + new)", itemsForCalculation.size());
        }
        
        itemsSubTotal = calculateItemsSubTotalFromStoredAmounts(order.getId(), activeOrderedItems);
        
        // Add combo prices to subtotal (use totalComboAmount which includes quantity, fallback to price if null)
        // Exclude cancelled combos from subtotal calculation
        // CRITICAL: Use a fresh query that bypasses cache to ensure we see latest combo statuses
        List<OrderedCombo> orderedCombos = loadOrderedCombosBypassCache(orderId);
        BigDecimal combosSubTotal = calculateCombosSubTotalFromList(orderedCombos);
        
        BigDecimal newSubTotal = itemsSubTotal.add(combosSubTotal);
        
        log.info("Order {} new subtotal after item change - Items: {}, Combos: {}, Total: {}", 
                order.getId(), itemsSubTotal, combosSubTotal, newSubTotal);
        
        // ==================== VALIDATE ORDER-LEVEL DISCOUNT ====================
        // currentOrderDiscount was fetched separately above to avoid lazy loading issues
        BigDecimal newDiscountAmount = BigDecimal.ZERO;
        BigDecimal newSubtotalAfterDiscount = newSubTotal;
        String newDiscountCode = null;
        BigDecimal newDiscountValue = null;
        DiscountType newDiscountType = null;
        BigDecimal newAdditionalDiscountValue = null;
        DiscountType newAdditionalDiscountType = null;
        BigDecimal newAdditionalDiscountAmount = null;
        String discountMessage = null;
        boolean discountRemoved = false;
        
        if (currentOrderDiscount != null) {
            // Check if discount threshold is still met
            if (currentOrderDiscount.getOrderValueThreshold() != null && 
                newSubTotal.compareTo(currentOrderDiscount.getOrderValueThreshold()) < 0) {
                
                log.warn("Order {} discount threshold no longer met. Removing ALL discount fields. Threshold: {}, Current Subtotal: {}", 
                        order.getId(), currentOrderDiscount.getOrderValueThreshold(), newSubTotal);
                
                // ==================== REMOVE ALL DISCOUNT-RELATED FIELDS ====================
                order.setDiscount(null);
                order.setDiscountCode(null);
                order.setDiscountValue(null);
                order.setDiscountAmount(BigDecimal.ZERO);
                order.setDiscountType(null);
                order.setAdditionalDiscountValue(null);
                order.setAdditionalDiscountType(null);
                order.setAdditionalDiscountAmount(BigDecimal.ZERO);
                
                discountRemoved = true;
                discountMessage = messageUtil.getMessage("order.discount.removed.threshold", userLocale);
                
            } else {
                // Recalculate discount with new subtotal using existing method
                com.gulfnet.shared_library.model.response.dto.OrderDiscountResult discountResult = orderPricingService.applyOrderLevelDiscount(currentOrderDiscount, newSubTotal, userLocale);
                newDiscountAmount = discountResult.getDiscountSavings();
                newSubtotalAfterDiscount = discountResult.getFinalSubTotal();
                newDiscountCode = currentOrderDiscount.getDiscountCode();
                newDiscountValue = currentOrderDiscount.getValue();
                newDiscountType = currentOrderDiscount.getDiscountType();
                
                // Set the discount relationship to the separately fetched discount entity
                order.setDiscount(currentOrderDiscount);
                
                // Keep additional discount fields as they are (if any)
                newAdditionalDiscountValue = order.getAdditionalDiscountValue();
                newAdditionalDiscountType = order.getAdditionalDiscountType();
                newAdditionalDiscountAmount = order.getAdditionalDiscountAmount();
                
                discountMessage = messageUtil.getMessage("order.discount.recalculated", userLocale);
            }
        } else {
            // No order-level discount was applied, clear only order-level discount fields
            // Note: We do NOT clear additionalDiscountValue and additionalDiscountType 
            // as those are separate fields that may be updated independently
            order.setDiscount(null);
            order.setDiscountCode(null);
            order.setDiscountValue(null);
            order.setDiscountAmount(BigDecimal.ZERO);
            order.setDiscountType(null);
            // Preserve additional discount fields - they should only be cleared via removeAdditionalDiscount endpoint
        }
        
        // ==================== RECALCULATE TOTALS USING ITEM-AWARE CALCULATOR ====================
        // This ensures alcoholic/non-alcoholic tax breakdown is derived from each item's stored alcohol type
        // (including combo tax breakdown for already-stored combos).
        Discount discountForCalculation = (discountRemoved || currentOrderDiscount == null) ? null : currentOrderDiscount;
        
        // Build calculation requests from active persisted entities (no need to include modifiers for totals).
        List<OrderedItemRequest> orderedItemRequests = activeOrderedItems.stream()
                .map(oi -> OrderedItemRequest.builder()
                        .orderedItemId(oi.getId())
                        .itemId(oi.getItem() != null ? oi.getItem().getId() : null)
                        .quantity(oi.getQuantity())
                        .notes(oi.getNotes())
                        .discountIds(oi.getDiscountId() != null ? java.util.Collections.singletonList(oi.getDiscountId()) : null)
                        .isBuyItem(oi.getBxgyRole() == BxgyRole.BUY)
                        .isGetItem(oi.getBxgyRole() == BxgyRole.GET)
                        .freeQuantity(oi.getFreeQuantity())
                        .build())
                .filter(req -> req.getItemId() != null && req.getQuantity() != null)
                .collect(Collectors.toList());
        
        List<OrderedComboRequest> orderedComboRequests = orderedCombos.stream()
                .map(oc -> OrderedComboRequest.builder()
                        .orderedComboId(oc.getId())
                        .comboId(oc.getCombo() != null ? oc.getCombo().getComboId() : null)
                        .quantity(oc.getQuantity())
                        .notes(oc.getNotes())
                        .build())
                .filter(req -> req.getComboId() != null && req.getQuantity() != null)
                .collect(Collectors.toList());
        
        com.gulfnet.shared_library.model.response.dto.OrderCalculationResult calculationResult = orderPricingService.calculateCompleteOrderTotals(
                orderedItemRequests,
                orderedComboRequests,
                menuId,
                restaurantId,
                null, // No price override context available during recalculation; stored amounts are used.
                discountForCalculation,
                order.getAdditionalDiscountValue(),
                order.getAdditionalDiscountType(),
                order.getOrderType(),
                userLocale);

        // Update order with calculated amounts
        String currency = restaurantChainConfigProperties.getChain().getCurrency();
        order.setSubTotal(CurrencyFormatter.formatAmount(calculationResult.getSubTotal(), currency));
        order.setDiscountAmount(calculationResult.getOrderDiscountSavings());
        order.setTaxAmount(calculationResult.getTaxAmount());
        order.setAlcoholicTaxAmount(calculationResult.getAlcoholicTaxAmount());
        order.setNonAlcoholicTaxAmount(calculationResult.getNonAlcoholicTaxAmount());
        order.setAlcoholicTaxableAmount(calculationResult.getAlcoholicTaxableAmount());
        order.setNonAlcoholicTaxableAmount(calculationResult.getNonAlcoholicTaxableAmount());
        order.setServiceChargeAmount(calculationResult.getServiceChargeAmount());
        order.setPackingChargeAmount(calculationResult.getPackingChargeAmount());
        order.setAdditionalDiscountAmount(calculationResult.getAdditionalDiscountSavings());
        order.setTotalAmount(calculationResult.getTotalAmount());
        
        // Update discount fields if discount was recalculated
        if (currentOrderDiscount != null && !discountRemoved) {
            order.setDiscountCode(newDiscountCode);
            order.setDiscountValue(newDiscountValue);
            order.setDiscountType(newDiscountType);
        }
        
        // Save updated order
        order.setUpdatedAt(OffsetDateTime.now(ZoneOffset.UTC));
        if (hasUserId && authenticatedUser != null) {
            order.setUpdatedBy(authenticatedUser);
        }
        orderRepository.save(order);
        
        // CRITICAL: Flush to ensure changes are persisted and visible to subsequent queries
        // This ensures that when getOrdersBySessionId or getOrdersByTableId is called,
        // the updated values are fetched from the database
        orderRepository.flush();
        
        log.info("Order {} recalculated successfully. New totals - SubTotal: {}, Discount: {}, Tax: {} (Alcoholic: {}, Non-Alcoholic: {}), ServiceCharge: {}, PackingCharge: {}, AdditionalDiscount: {}, Total: {}", 
                order.getId(), calculationResult.getSubTotal(), calculationResult.getOrderDiscountSavings(), 
                calculationResult.getTaxAmount(), 
                calculationResult.getAlcoholicTaxAmount(),
                calculationResult.getNonAlcoholicTaxAmount(),
                calculationResult.getServiceChargeAmount(), 
                calculationResult.getPackingChargeAmount(), calculationResult.getAdditionalDiscountSavings(), 
                calculationResult.getTotalAmount());
        
        return new OrderRecalculationResult(discountRemoved, discountMessage);
    }

    /**
     * Loads non-canceled, non-combo ordered items for {@code orderId} using a cache-bypassing persistence query.
     */
    private List<OrderedItem> loadActiveNonComboOrderedItemsForCalculation(UUID orderId) {
        // CRITICAL: Use a fresh query that bypasses any query cache
        jakarta.persistence.Query query = entityManager.createQuery(
                "SELECT oi FROM OrderedItem oi WHERE oi.order.id = :orderId", OrderedItem.class);
        query.setParameter("orderId", orderId);
        query.setHint("jakarta.persistence.cache.retrieveMode", jakarta.persistence.CacheRetrieveMode.BYPASS);
        query.setHint("jakarta.persistence.cache.storeMode", jakarta.persistence.CacheStoreMode.BYPASS);
        @SuppressWarnings("unchecked")
        List<OrderedItem> allOrderedItems = query.getResultList();

        log.info("Order {} - All items queried: {}", orderId, allOrderedItems.size());
        for (OrderedItem item : allOrderedItems) {
            log.info("  Item {} - Status: {}, IsComboItem: {}, TotalDiscountedAmount: {}",
                    item.getId(), item.getItemStatus(), item.getOrderedCombo() != null,
                    item.getTotalDiscountedItemAmount());
        }

        return allOrderedItems.stream()
                .filter(item -> isActiveNonComboItemForCalculation(orderId, item))
                .collect(Collectors.toList());
    }

    private boolean isActiveNonComboItemForCalculation(UUID orderId, OrderedItem item) {
        boolean isNotCanceled = item.getItemStatus() != ItemStatus.CANCELED;
        boolean isNotComboItem = item.getOrderedCombo() == null;
        if (!isNotCanceled) {
            log.warn("Order {} - Excluding cancelled item {} from calculation", orderId, item.getId());
        }
        if (!isNotComboItem) {
            log.debug("Order {} - Excluding combo item {} from calculation", orderId, item.getId());
        }
        return isNotCanceled && isNotComboItem;
    }

    private BigDecimal calculateItemsSubTotalFromStoredAmounts(UUID orderId, List<OrderedItem> activeOrderedItems) {
        log.info("Order {} - Calculating subtotal from {} active items using stored amounts", orderId, activeOrderedItems.size());
        BigDecimal itemsSubTotal = activeOrderedItems.stream()
                .map(this::resolveOrderedItemAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        log.info("Order {} using stored amounts for all items: {} (from {} items)",
                orderId, itemsSubTotal, activeOrderedItems.size());
        return itemsSubTotal;
    }

    /**
     * Picks discounted line amount when set, otherwise gross line total, otherwise {@code price * quantity}, else zero.
     */
    private BigDecimal resolveOrderedItemAmount(OrderedItem oi) {
        BigDecimal amount;
        if (oi.getTotalDiscountedItemAmount() != null) {
            amount = oi.getTotalDiscountedItemAmount();
        } else if (oi.getTotalItemAmount() != null) {
            amount = oi.getTotalItemAmount();
        } else if (oi.getPrice() != null && oi.getQuantity() != null) {
            amount = oi.getPrice().multiply(BigDecimal.valueOf(oi.getQuantity()));
        } else {
            amount = BigDecimal.ZERO;
        }
        log.debug("  Item {} - totalDiscountedItemAmount: {}, totalItemAmount: {}, calculated amount: {}",
                oi.getId(), oi.getTotalDiscountedItemAmount(), oi.getTotalItemAmount(), amount);
        return amount;
    }

    private List<OrderedCombo> loadOrderedCombosBypassCache(UUID orderId) {
        jakarta.persistence.Query comboQuery = entityManager.createQuery(
                "SELECT oc FROM OrderedCombo oc WHERE oc.order.id = :orderId", OrderedCombo.class);
        comboQuery.setParameter("orderId", orderId);
        comboQuery.setHint("jakarta.persistence.cache.retrieveMode", jakarta.persistence.CacheRetrieveMode.BYPASS);
        comboQuery.setHint("jakarta.persistence.cache.storeMode", jakarta.persistence.CacheStoreMode.BYPASS);
        @SuppressWarnings("unchecked")
        List<OrderedCombo> orderedCombos = comboQuery.getResultList();
        return orderedCombos;
    }

    private BigDecimal calculateCombosSubTotalFromList(List<OrderedCombo> orderedCombos) {
        return orderedCombos.stream()
                .filter(oc -> oc.getItemStatus() != ItemStatus.CANCELED)
                .map(oc -> oc.getTotalComboAmount() != null ? oc.getTotalComboAmount() : oc.getPrice())
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /**
     * Applies an update to a persisted ordered item (quantity/notes/modifiers), recalculates its prices, then recalculates the order.
     * <p>
     * This method:
     * - updates modifiers (replacing existing selections when provided)
     * - recalculates BXGY across the order when BUY/GET flags apply
     * - recalculates the updated item's stored totals (gross and discounted) including modifiers
     * - flushes item changes before delegating to {@link #recalculateOrderAfterItemChange(Order, User, boolean, Locale, BxgyCalculationResult)}
     *
     * @param orderedItem item to update (required)
     * @param request update request (required)
     * @param authenticatedUser actor user (may be {@code null})
     * @param hasUserId whether to set {@code updatedBy} from {@code authenticatedUser}
     * @param userLocale locale for messages/validation (may be {@code null})
     * @return recalculation result indicating discount removal/recalculation details
     * @throws ResponseStatusException if modifier validation fails or referenced modifier items are missing
     */
    @Override
    public OrderRecalculationResult handleItemUpdate(OrderedItem orderedItem, UpdateOrderedItemRequest request, User authenticatedUser, boolean hasUserId, Locale userLocale) {
        log.info("Item {} updated for order {}", orderedItem.getId(), orderedItem.getOrder().getId());
        
        // ==================== STORE CURRENT VALUES ====================
        // Calculate current modifier total per item
        List<OrderedItemModifier> currentModifiers = orderedItemModifierRepository.findByOrderedItemId(orderedItem.getId());
        BigDecimal currentModifierPricePerItem = currentModifiers.stream()
                .map(OrderedItemModifier::getPrice)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        
        // ==================== UPDATE ITEM DETAILS ====================
        if (request.getQuantity() != null) {
            orderedItem.setQuantity(request.getQuantity());
        }
        if (request.getNotes() != null) {
            orderedItem.setNotes(request.getNotes());
        }
        
        // ==================== UPDATE MODIFIERS ====================
        BigDecimal newModifierPricePerItem = BigDecimal.ZERO;
        if (request.getOrderedItemModifiers() != null) {
            // Remove existing modifiers
            orderedItemModifierRepository.deleteAll(currentModifiers);
            
            // Add new modifiers
            for (OrderedItemModifierRequest modifierRequest : request.getOrderedItemModifiers()) {
                orderValidationService.validateOrderedItemModifier(modifierRequest, userLocale);
                
                for (UUID modifierItemId : modifierRequest.getModifierItemIds()) {
                    ModifierItem modifierItem = modifierItemRepository.findById(modifierItemId)
                            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                                    messageUtil.getMessage("modifier.item.name.not.found", userLocale)));
                    
                    // Null-safe price handling: treat null prices as zero for calculations
                    BigDecimal itemPrice = modifierItem.getPrice() != null ? modifierItem.getPrice() : BigDecimal.ZERO;
                    
                    OrderedItemModifier modifier = OrderedItemModifier.builder()
                            .orderedItem(orderedItem)
                            .modifierGroup(modifierItem.getModifierGroup())
                            .modifierItem(modifierItem)
                            .price(itemPrice)
                            .build();
                    
                    orderedItemModifierRepository.save(modifier);
                    newModifierPricePerItem = newModifierPricePerItem.add(itemPrice);
                }
            }
        } else {
            // Keep existing modifiers
            newModifierPricePerItem = currentModifierPricePerItem;
        }
        
        // ==================== BUILD ACTIVE PRICE OVERRIDE INDEX ====================
        Order order = orderedItem.getOrder();
        UUID restaurantId = orderNotificationService.getRestaurantIdSafely(order);
        PriceOverrideHelper.ActiveOverrideIndex activeOverrideIndex = null;
        if (restaurantId != null) {
            activeOverrideIndex = priceOverrideHelper.buildActiveOverrideIndex(restaurantId);
        }
        
        // ==================== RECALCULATE BXGY FOR ENTIRE ORDER ====================
        // Get all active items in the order (excluding cancelled items and combo items)
        List<OrderedItem> allOrderedItems = orderedItemRepository.findByOrderId(order.getId());
        List<OrderedItem> activeOrderedItems = allOrderedItems.stream()
            .filter(item -> item.getItemStatus() != ItemStatus.CANCELED && item.getOrderedCombo() == null)
            .collect(Collectors.toList());
        
        // Convert to OrderedItemRequest format for BXGY calculation
        List<OrderedItemRequest> itemRequests = convertOrderedItemsToRequests(
            activeOrderedItems, request.getMenuId(), request, orderedItem.getId());
        
        // Recalculate BXGY discounts for entire order
        BxgyCalculationResult bxgyResult = null;
        boolean hasBxgyItems = itemRequests.stream()
            .anyMatch(item -> Boolean.TRUE.equals(item.getIsBuyItem()) || Boolean.TRUE.equals(item.getIsGetItem()));
        
        if (hasBxgyItems && restaurantId != null && activeOverrideIndex != null) {
            log.info("Recalculating BXGY discounts for order {} after item update", order.getId());
            bxgyResult = orderPricingService.calculateSubTotalWithBxgyDiscounts(
                itemRequests, null, request.getMenuId(), restaurantId, activeOverrideIndex);
        }
        
        // ==================== RECALCULATE PRICES FOR UPDATED ITEM ====================
        int qty = orderedItem.getQuantity();
        // Calculate discounted total for the item (excluding modifiers) using menu context with price override
        com.gulfnet.shared_library.model.response.dto.DiscountCalculationResult updatedDiscount;
        if (restaurantId != null && activeOverrideIndex != null) {
            updatedDiscount = orderPricingService.calculateItemPriceWithOverride(
                    request.getMenuId(), orderedItem.getItem().getId(), qty, restaurantId, activeOverrideIndex);
        } else {
            updatedDiscount = orderPricingService.calculateItemPrice(request.getMenuId(), orderedItem.getItem().getId(), qty);
        }
        
        // Get currency for formatting
        String currency = restaurantChainConfigProperties.getChain().getCurrency();
        
        // Extract base price per unit from updatedDiscount (this accounts for price override)
        BigDecimal basePriceTotal = updatedDiscount.getOriginalPrice();
        BigDecimal basePricePerUnit = CurrencyFormatter.formatAmount(
            basePriceTotal.divide(BigDecimal.valueOf(qty), 10, RoundingMode.HALF_UP), 
            currency);
        
        // Check if this is a BXGY GET item
        Boolean isGetItem = request.getIsGetItem() != null && request.getIsGetItem();
        Boolean isBuyItem = request.getIsBuyItem() != null && request.getIsBuyItem();
        boolean isBxgyGetItem = Boolean.TRUE.equals(isGetItem);
        
        // Calculate total modifier price
        BigDecimal totalModifierPrice = newModifierPricePerItem.multiply(BigDecimal.valueOf(qty));
        
        BigDecimal discountedPerUnit = null;
        BigDecimal grossTotal;
        BigDecimal netTotal = null;
        
        if (isBxgyGetItem && bxgyResult != null) {
            // ==================== BXGY GET ITEM HANDLING ====================
            // Calculate paid quantity from request: quantity - freeQuantity (same logic as calculateOrder)
            // Use freeQuantity from request directly, treating null as 0
            Integer freeQuantity = request.getFreeQuantity();
            int freeQty = (freeQuantity != null) ? freeQuantity : 0;
            // Ensure freeQuantity doesn't exceed quantity
            freeQty = Math.min(freeQty, qty);
            int paidQuantity = Math.max(0, qty - freeQty);
            
            log.info("BXGY GET ITEM UPDATE - Item: {}, Quantity: {}, FreeQuantity (from request): {}, PaidQuantity: {}", 
                orderedItem.getItem().getId(), qty, freeQty, paidQuantity);
            
            // Get price for paid items from BXGY result
            BigDecimal getItemPriceFromMap = BigDecimal.ZERO;
            if (bxgyResult.getGetItemPrices() != null && bxgyResult.getGetItemPrices().containsKey(orderedItem.getItem().getId())) {
                getItemPriceFromMap = bxgyResult.getGetItemPrices().get(orderedItem.getItem().getId());
            }
            
            // Calculate paid item price
            BigDecimal paidItemPrice = CurrencyFormatter.formatAmount(
                basePricePerUnit.multiply(BigDecimal.valueOf(paidQuantity)),
                currency);
            
            // For GET items with paid quantities: discountedPrice = price per paid item
            if (paidQuantity > 0) {
                // Check if regular discount applies to paid items
                if (updatedDiscount.getAppliedDiscount() != null) {
                    // Regular discount applies - use discounted price for paid items
                    BigDecimal discountedItemTotal = CurrencyFormatter.formatAmount(updatedDiscount.getFinalPrice(), currency);
                    BigDecimal discountedPriceTotalForPaid = CurrencyFormatter.formatAmount(
                        discountedItemTotal
                            .divide(BigDecimal.valueOf(qty), 10, RoundingMode.HALF_UP)
                            .multiply(BigDecimal.valueOf(paidQuantity)),
                        currency);
                    discountedPerUnit = CurrencyFormatter.formatAmount(
                        discountedPriceTotalForPaid.divide(BigDecimal.valueOf(paidQuantity), 10, RoundingMode.HALF_UP),
                        currency);
                } else {
                    // No regular discount - use base price for paid items
                    discountedPerUnit = basePricePerUnit;
                }
            } else {
                // All items are free
                discountedPerUnit = BigDecimal.ZERO;
            }
            
            // totalItemAmount = (base price * total quantity) + modifiers
            grossTotal = CurrencyFormatter.formatAmount(
                basePriceTotal.add(totalModifierPrice),
                currency);
            
            // totalDiscountedItemAmount = (paid quantity * discountedPrice) + modifiers
            if (paidQuantity > 0 && discountedPerUnit != null) {
                BigDecimal paidItemTotal = discountedPerUnit.multiply(BigDecimal.valueOf(paidQuantity));
                netTotal = CurrencyFormatter.formatAmount(
                    paidItemTotal.add(totalModifierPrice),
                    currency);
            } else {
                // All items are free, only modifiers are charged
                netTotal = CurrencyFormatter.formatAmount(totalModifierPrice, currency);
            }
            
            log.info("BXGY GET ITEM PRICE CALCULATION - Item: {}, BasePricePerUnit: {}, DiscountedPricePerUnit: {}, PaidQuantity: {}, TotalItemAmount: {}, TotalDiscountedItemAmount: {}",
                orderedItem.getItem().getId(), basePricePerUnit, discountedPerUnit, paidQuantity, grossTotal, netTotal);
            
        } else {
            // ==================== REGULAR ITEM OR BUY ITEM HANDLING ====================
            // Extract discounted price per unit from updatedDiscount (if discount exists)
            BigDecimal discountedItemTotal = CurrencyFormatter.formatAmount(updatedDiscount.getFinalPrice(), currency);
            boolean hasDiscount = updatedDiscount.getAppliedDiscount() != null;
            
            if (hasDiscount) {
                discountedPerUnit = CurrencyFormatter.formatAmount(
                    discountedItemTotal.divide(BigDecimal.valueOf(qty), 10, RoundingMode.HALF_UP), 
                    currency);
            }

            // Calculate totalItemAmount = (base price per unit + modifier price per item) * quantity
            grossTotal = CurrencyFormatter.formatAmount(
                (basePricePerUnit.add(newModifierPricePerItem)).multiply(BigDecimal.valueOf(qty)), 
                currency);
            
            // Calculate totalDiscountedItemAmount = (discounted base price per unit + modifier price per item) * quantity
            // Only set if discount exists, otherwise null
            if (hasDiscount && discountedPerUnit != null) {
                netTotal = CurrencyFormatter.formatAmount(
                    (discountedPerUnit.add(newModifierPricePerItem)).multiply(BigDecimal.valueOf(qty)), 
                    currency);
            }
        }

        // Persist base unit into price and the rest into their fields
        orderedItem.setPrice(basePricePerUnit);
        orderedItem.setDiscountedPrice(discountedPerUnit);  // Set correctly for GET items with paid quantities
        orderedItem.setTotalItemAmount(grossTotal);
        orderedItem.setTotalDiscountedItemAmount(netTotal);
        orderedItem.setUpdatedAt(OffsetDateTime.now(ZoneOffset.UTC));
        if (hasUserId && authenticatedUser != null) {
            orderedItem.setUpdatedBy(authenticatedUser);
        }
        orderedItemRepository.save(orderedItem);
        
        // CRITICAL: Flush to ensure item changes are persisted before entityManager.clear() in recalculateOrderAfterItemChange
        // This ensures that when we query items in recalculateOrderAfterItemChange, we see the updated totalItemAmount
        orderedItemRepository.flush();
        
        log.info("Item {} updated - Quantity: {}, Price: {}, TotalItemAmount: {}, TotalDiscountedItemAmount: {}", 
                orderedItem.getId(), orderedItem.getQuantity(), basePricePerUnit, grossTotal, netTotal);
        
        // Complete order recalculation including BXGY recalculation
        return recalculateOrderAfterItemChange(orderedItem.getOrder(), authenticatedUser, hasUserId, userLocale, bxgyResult);
    }

    /**
     * Applies an update to a persisted ordered combo (quantity/notes/selected groups), rebuilds combo items, then recalculates the order.
     * <p>
     * Behavior varies by combo type:
     * - FIXED: price is basePrice × quantity; fixed items are recreated
     * - CHOICE/MIXED: price is recalculated from selected groups; choice/mixed items are recreated
     * <p>
     * Flushes combo changes before delegating to {@link #recalculateOrderAfterItemChange(Order, User, boolean, Locale)}.
     *
     * @param orderedCombo combo to update (required)
     * @param request update request (required)
     * @param authenticatedUser actor user (may be {@code null})
     * @param hasUserId whether to set {@code updatedBy} from {@code authenticatedUser}
     * @param userLocale locale for messages/validation (may be {@code null})
     * @return recalculation result indicating discount removal/recalculation details
     * @throws ResponseStatusException if required combo groups are missing or combo type is invalid
     */
    @Override
    public OrderRecalculationResult handleComboUpdate(OrderedCombo orderedCombo, UpdateOrderedComboRequest request, User authenticatedUser, boolean hasUserId, Locale userLocale) {
        log.info("Combo {} updated for order {}", orderedCombo.getId(), orderedCombo.getOrder().getId());
        
        Combo combo = orderedCombo.getCombo();
        
        // ==================== UPDATE COMBO DETAILS ====================
        if (request.getQuantity() != null) {
            orderedCombo.setQuantity(request.getQuantity());
        }
        if (request.getNotes() != null) {
            orderedCombo.setNotes(request.getNotes());
        }
        
        // ==================== UPDATE COMBO ITEMS BASED ON COMBO TYPE ====================
        // Delete existing combo items and their modifiers
        List<OrderedItem> existingComboItems = orderedItemRepository.findByOrderedComboId(orderedCombo.getId());
        for (OrderedItem comboItem : existingComboItems) {
            // Delete modifiers first (cascade should handle this, but being explicit)
            List<OrderedItemModifier> modifiers = orderedItemModifierRepository.findByOrderedItemId(comboItem.getId());
            if (modifiers != null && !modifiers.isEmpty()) {
                orderedItemModifierRepository.deleteAll(modifiers);
            }
            orderedItemRepository.delete(comboItem);
        }
        
        // Recalculate combo price based on type
        // Get currency for formatting
        String currency = restaurantChainConfigProperties.getChain().getCurrency();
        
        BigDecimal newComboPrice;
        User updatedBy = orderedCombo.getUpdatedBy() != null ? orderedCombo.getUpdatedBy() : orderedCombo.getCreatedBy();
        
        switch (combo.getType()) {
            case FIXED:
                // For FIXED combo, price is basePrice × quantity
                newComboPrice = CurrencyFormatter.formatAmount(
                    combo.getBasePrice().multiply(BigDecimal.valueOf(orderedCombo.getQuantity())), 
                    currency);
                // Recreate fixed items
                OrderedComboRequest fixedComboRequest = convertToComboRequest(request, combo.getComboId());
                orderedComboService.createFixedOrderedItems(orderedCombo, fixedComboRequest, updatedBy, userLocale);
                break;
                
            case CHOICE:
                // For CHOICE combo, calculate price based on selected items
                if (request.getComboGroups() == null || request.getComboGroups().isEmpty()) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                            messageUtil.getMessage("ordered.combo.choice.groups.required", userLocale));
                }
                OrderedComboRequest choiceComboRequest = convertToComboRequest(request, combo.getComboId());
                BigDecimal calculatedChoicePrice = orderPricingService.calculateChoiceComboPrice(combo, choiceComboRequest, userLocale);
                newComboPrice = calculatedChoicePrice; // Already formatted in calculateChoiceComboPrice
                // Recreate choice items
                orderedComboService.createChoiceOrderedItems(orderedCombo, choiceComboRequest, updatedBy, userLocale);
                break;
                
            case MIXED:
                // For MIXED combo, calculate price based on fixed + choice items
                if (request.getComboGroups() == null || request.getComboGroups().isEmpty()) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                            messageUtil.getMessage("ordered.combo.mixed.groups.required", userLocale));
                }
                OrderedComboRequest mixedComboRequest = convertToComboRequest(request, combo.getComboId());
                BigDecimal calculatedMixedPrice = orderPricingService.calculateMixedComboPrice(combo, mixedComboRequest, userLocale);
                newComboPrice = calculatedMixedPrice; // Already formatted in calculateMixedComboPrice
                // Recreate mixed items
                orderedComboService.createMixedOrderedItems(orderedCombo, mixedComboRequest, updatedBy, userLocale);
                break;
                
            default:
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        messageUtil.getMessage("combo.type.invalid", userLocale));
        }
        
        // Update combo price (base price per unit) and persist total combo amount (total including quantity)
        BigDecimal formattedBasePrice = CurrencyFormatter.formatAmount(combo.getBasePrice(), currency);
        orderedCombo.setPrice(formattedBasePrice);
        orderedCombo.setTotalComboAmount(newComboPrice);
        orderedCombo.setUpdatedAt(OffsetDateTime.now(ZoneOffset.UTC));
        if (hasUserId && authenticatedUser != null) {
            orderedCombo.setUpdatedBy(authenticatedUser);
        }
        orderedComboRepository.save(orderedCombo);
        
        // CRITICAL: Flush to ensure combo changes are persisted before entityManager.clear() in recalculateOrderAfterItemChange
        orderedComboRepository.flush();
        
        log.info("Combo {} updated - Quantity: {}, Price: {}, TotalComboAmount: {}", 
                orderedCombo.getId(), orderedCombo.getQuantity(), formattedBasePrice, newComboPrice);
        
        // Complete order recalculation including discount validation
        return recalculateOrderAfterItemChange(orderedCombo.getOrder(), authenticatedUser, hasUserId, userLocale);
    }

    /**
     * Helper method to notify a waiter about order cancellation.
     */
    private void notifyWaiterOrderCancelled(Order order, User waiter, UUID orderId, Locale userLocale) {
        try {
            notificationService.notifyOrderCancelled(order, waiter, userLocale);
            String tableOrder = getTableOrderString(order);
            log.info("Sent order cancellation notification to waiter {} for order {} at table {} (automatic cancellation)", 
                    waiter.getId(), orderId, tableOrder);
        } catch (Exception e) {
            log.error("Failed to send order cancellation notification to waiter {}: {}", waiter.getId(), e.getMessage(), e);
        }
    }

    /**
     * Helper method to get table order string or default to UNKNOWN_TABLE.
     */
    private String getTableOrderString(Order order) {
        if (order.getRestaurantTable() != null && order.getRestaurantTable().getTableOrder() != null) {
            return order.getRestaurantTable().getTableOrder().toString();
        }
        return UNKNOWN_TABLE;
    }

}

