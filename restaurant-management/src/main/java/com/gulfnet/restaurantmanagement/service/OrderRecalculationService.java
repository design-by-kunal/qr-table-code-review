package com.gulfnet.restaurantmanagement.service;

import com.gulfnet.shared_library.entity.Order;
import com.gulfnet.shared_library.entity.OrderedCombo;
import com.gulfnet.shared_library.entity.OrderedItem;
import com.gulfnet.shared_library.entity.Transaction;
import com.gulfnet.shared_library.entity.User;
import com.gulfnet.shared_library.enums.OrderStatus;
import com.gulfnet.shared_library.model.request.UpdateOrderedComboRequest;
import com.gulfnet.shared_library.model.request.UpdateOrderedItemRequest;
import com.gulfnet.shared_library.model.response.dto.BxgyCalculationResult;
import com.gulfnet.shared_library.model.response.dto.OrderRecalculationResult;

import java.util.List;
import java.util.Locale;
import java.util.UUID;

public interface OrderRecalculationService {

    /**
     * Handle item update and recalculate order totals
     */
    OrderRecalculationResult handleItemUpdate(OrderedItem orderedItem, UpdateOrderedItemRequest request, User authenticatedUser, boolean hasUserId, Locale userLocale);

    /**
     * Handle item cancellation and recalculate order totals
     */
    OrderRecalculationResult handleItemCancellation(OrderedItem orderedItem, User authenticatedUser, boolean hasUserId, Locale userLocale);

    /**
     * Handle combo cancellation and recalculate order totals
     */
    OrderRecalculationResult handleComboCancellation(OrderedCombo orderedCombo, User authenticatedUser, boolean hasUserId, Locale userLocale);

    /**
     * Handle combo update and recalculate order totals
     */
    OrderRecalculationResult handleComboUpdate(OrderedCombo orderedCombo, UpdateOrderedComboRequest request, User authenticatedUser, boolean hasUserId, Locale userLocale);

    /**
     * Recalculate order totals after item/combo changes
     */
    OrderRecalculationResult recalculateOrderAfterItemChange(Order order, User authenticatedUser, boolean hasUserId, Locale userLocale);

    /**
     * Recalculate order totals after item/combo changes with provided BXGY result
     */
    OrderRecalculationResult recalculateOrderAfterItemChange(Order order, User authenticatedUser, boolean hasUserId, Locale userLocale, BxgyCalculationResult providedBxgyResult);

    /**
     * Recalculate order totals after item/combo changes with provided BXGY result and items for calculation
     */
    OrderRecalculationResult recalculateOrderAfterItemChange(Order order, User authenticatedUser, boolean hasUserId, Locale userLocale, BxgyCalculationResult providedBxgyResult, List<com.gulfnet.shared_library.model.request.OrderedItemRequest> itemsForCalculation);

    /**
     * Deduct item amount from order (legacy method - kept for backward compatibility)
     */
    void deductItemAmountFromOrder(OrderedItem orderedItem, User authenticatedUser, boolean hasUserId, Locale userLocale);

    /**
     * Deduct multiple items amount from order and recalculate once
     */
    void deductItemsAmountFromOrder(java.util.Collection<OrderedItem> orderedItems, User authenticatedUser, boolean hasUserId, Locale userLocale);

    /**
     * Deduct combo amount from order (legacy method - kept for backward compatibility)
     */
    void deductComboAmountFromOrder(OrderedCombo orderedCombo, User authenticatedUser, boolean hasUserId, Locale userLocale);

    /**
     * Deduct multiple combos amount from order and recalculate once
     */
    void deductCombosAmountFromOrder(java.util.Collection<OrderedCombo> orderedCombos, User authenticatedUser, boolean hasUserId, Locale userLocale);

    /**
     * Determine order status based on item and combo statuses
     */
    OrderStatus determineOrderStatusBasedOnItems(UUID orderId);

    /**
     * Check and cancel order if all ON_HOLD/PUSHED items/combos are canceled
     */
    void checkAndCancelOrderIfAllHoldPushedItemsCanceled(Order order, User authenticatedUser, boolean hasUserId, Locale userLocale);

    /**
     * Update order status if it has changed
     */
    void updateOrderStatusIfChanged(Order order, OrderStatus newStatus, User authenticatedUser, boolean hasUserId, Locale userLocale);

    /**
     * For prepaid dine-in (per chain payment config) or takeaway, sets non-{@link com.gulfnet.shared_library.enums.ItemStatus#PUSHED}
     * line items to PUSHED, notifies clients, and reconciles aggregate {@link OrderStatus}.
     */
    void pushNonPushedOrderedItemsAfterPrepaidPaymentIfApplicable(
            Order order, Transaction transaction, UUID orderId, Locale locale);

    /**
     * Expires the order's table/QR session when the order is takeaway, order status is SERVED or CANCELED,
     * and the linked transaction is COMPLETED. No-op otherwise (including if the session is already expired).
     * When no orders on the same table still have an active (non-expired) session, sets the table to AVAILABLE
     * and broadcasts the same table-status WebSocket topic used elsewhere.
     * Intended to be called from the ordered-items status API flow only (after item/combo status drives order status).
     */
    void maybeExpireTakeawaySessionWhenOrderServedAndTransactionCompleted(UUID orderId);
}

