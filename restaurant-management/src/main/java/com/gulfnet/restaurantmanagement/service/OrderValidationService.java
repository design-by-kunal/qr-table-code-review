package com.gulfnet.restaurantmanagement.service;

import com.gulfnet.restaurantmanagement.config.RestaurantChainConfigProperties;
import com.gulfnet.shared_library.entity.*;
import com.gulfnet.shared_library.model.response.dto.BxgyItemInfo;
import com.gulfnet.shared_library.enums.DiscountType;
import com.gulfnet.shared_library.enums.ItemStatus;
import com.gulfnet.shared_library.model.request.AdditionalDiscountRequest;
import com.gulfnet.shared_library.model.request.OrderedComboRequest;
import com.gulfnet.shared_library.model.request.OrderedItemModifierRequest;
import com.gulfnet.shared_library.model.request.OrderedItemRequest;
import com.gulfnet.shared_library.model.request.PaymentRequest;

import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

public interface OrderValidationService {

    void validateTransactionNotCompleted(Order order, Locale userLocale);

    /**
     * Validate that an item or combo can be “recooked” (status changed from SERVED back to COOKING/PUSHED)
     * only when business rules allow it.
     *
     * For POSTPAID dine-in orders with a COMPLETED transaction, recooking is not allowed.
     */
    void validateRecookNotAllowedForCompletedPostpaidTransaction(
            Order order,
            ItemStatus currentStatus,
            ItemStatus newStatus,
            Locale userLocale
    );

    Discount validateAndGetOrderDiscount(UUID discountId, Locale userLocale);

    void validateOrderLevelDiscountType(Discount discount, Locale userLocale);

    boolean isOrderDiscountStillValid(Discount discount);

    void validateOrderItems(List<OrderedItemRequest> orderedItems, Locale userLocale);

    void validateItemAvailabilityForRestaurant(List<OrderedItemRequest> orderedItems, UUID restaurantId, UUID menuId, Locale userLocale);

    void validateOrderedItemModifier(OrderedItemModifierRequest modifierRequest, Locale userLocale);

    void validateItemStatusForUpdate(ItemStatus currentItemStatus, Locale userLocale);

    void validateItemStatusTransition(ItemStatus currentStatus, ItemStatus newStatus, Locale userLocale);

    void validateOrderCombos(List<OrderedComboRequest> orderedCombos, UUID menuId, Locale userLocale);

    void validateOrderCombos(List<OrderedComboRequest> orderedCombos, UUID menuId, Locale userLocale, boolean validateAvailability);

    void validateCombo(OrderedComboRequest comboRequest, UUID menuId, Locale userLocale, boolean validateAvailability);

    void validateComboAvailability(com.gulfnet.shared_library.entity.Combo combo, Locale userLocale);

    void validateComboGroups(List<com.gulfnet.shared_library.model.request.OrderedComboGroupRequest> comboGroups, com.gulfnet.shared_library.entity.Combo combo, Locale userLocale);

    void validateComboGroups(List<com.gulfnet.shared_library.model.request.OrderedComboGroupRequest> comboGroups, com.gulfnet.shared_library.entity.Combo combo, Locale userLocale, boolean skipModifierValidation);

    void validateComboItems(List<com.gulfnet.shared_library.model.request.OrderedComboItemRequest> comboItems, com.gulfnet.shared_library.entity.ComboGroup comboGroup, Locale userLocale);

    void validateComboItems(List<com.gulfnet.shared_library.model.request.OrderedComboItemRequest> comboItems, com.gulfnet.shared_library.entity.ComboGroup comboGroup, Locale userLocale, boolean skipModifierValidation);

    void validateComboItemModifiers(List<com.gulfnet.shared_library.model.request.OrderedComboItemModifierRequest> modifiers, com.gulfnet.shared_library.entity.Item item, Locale userLocale);

    void validateComboStatusForUpdate(ItemStatus currentItemStatus, Locale userLocale);

    void validateBxgyGetItemsQuantity(List<OrderedItemRequest> orderedItems, UUID menuId, Locale userLocale);

    boolean isDiscountValidForMenuAndTime(UUID menuId, UUID discountId);
    
    boolean isDiscountValidForMenuAndTime(UUID menuId, UUID discountId, UUID restaurantId);

    // Session validation methods
    Session validateAndGetSession(UUID sessionId, Locale userLocale);

    void validateSessionNotExpired(Session session, Locale userLocale);

    void validateSingleOrderPerSession(UUID sessionId, Locale userLocale);

    // Menu validation methods
    Menu validateAndGetMenu(UUID menuId, Locale userLocale);

    // Restaurant validation methods
    Restaurant validateAndGetRestaurant(UUID restaurantId, Locale userLocale);

    void validateRestaurantActive(Restaurant restaurant, Locale userLocale);

    // RestaurantTable validation methods
    RestaurantTable validateAndGetRestaurantTable(UUID tableId, Locale userLocale);

    void validateRestaurantTableNotDeleted(RestaurantTable restaurantTable, Locale userLocale);

    // Order entity validation methods
    Order validateAndGetOrder(UUID orderId, Locale userLocale);

    // OrderedItem entity validation methods
    OrderedItem validateAndGetOrderedItem(UUID itemId, Locale userLocale);

    // OrderedCombo entity validation methods
    OrderedCombo validateAndGetOrderedCombo(UUID comboId, Locale userLocale);

    // ==================== PAYMENT VALIDATIONS ====================
    
    void validatePaymentRequest(PaymentRequest request, Locale userLocale);
    
    void validatePaymentOrderId(UUID orderId, Locale userLocale);
    
    void validatePaymentMethod(String paymentMethod, Locale userLocale);
    
    void validatePaymentAmount(BigDecimal amount, Locale userLocale);
    
    void validatePaymentMethodAgainstConfig(String paymentMethod, RestaurantChainConfigProperties.RestaurantChainData chainConfig, Locale userLocale);

    /**
     * For hosted card checkout (CARD / CREDIT_CARD / DEBIT_CARD): requires return URLs and {@link Order#getGmoLinkOrderId()}.
     */
    void validateGmoHostedCardPayment(PaymentRequest request, Order order, Locale userLocale);

    /**
     * Configured UUID for {@code transaction.cashier_id} when {@code payment_initiator_type=1}.
     * Taken from {@code payment.online-card.user-id}; no {@code users} row required.
     */
    UUID resolveOnlineCardCashierId(Locale userLocale);

    void validatePaymentAmountForMethod(String paymentMethod, BigDecimal amountPaid, BigDecimal orderTotal, Locale userLocale);

    /**
     * For CASH: physical tender ({@code cashReceived}, or {@code amountPaid} when {@code cashReceived} is null)
     * must be at least {@code amountPaid}.
     */
    void validateCashReceivedAgainstAmountPaid(PaymentRequest request, Locale userLocale);
    
    Transaction validateAndGetTransactionForPayment(UUID orderId, Locale userLocale);

    // ==================== USER VALIDATIONS ====================
    
    User validateAndGetUser(String userId, Locale userLocale);
    
    User validateAndGetUserOrNull(String userId, Locale userLocale);
    
    boolean isValidUserId(String userId);

    /**
     * Staff (valid {@code User-ID}): no session header required.
     * Customer (no staff user): {@code Session-ID} header required and must match {@code expectedSessionId}.
     */
    void validateCustomerOrStaffSessionAccess(String userId, String sessionIdHeader, UUID expectedSessionId, Locale userLocale);

    /**
     * Same as {@link #validateCustomerOrStaffSessionAccess} plus, for customers, the header must also match the order's session.
     */
    void validateCustomerOrStaffOrderSessionAccess(String userId, String sessionIdHeader, UUID requestSessionId,
                                                   UUID orderSessionId, Locale userLocale);

    // ==================== EMAIL VALIDATIONS ====================
    
    void validateEmailFormat(String email, Locale userLocale);
    
    boolean isValidEmailFormat(String email);

    // ==================== ADDITIONAL DISCOUNT VALIDATIONS ====================
    
    void validateAdditionalDiscountRequest(AdditionalDiscountRequest request, Locale userLocale);
    
    void validateAdditionalDiscountNotAlreadyApplied(Order order, Locale userLocale);
    
    void validateAdditionalDiscountType(DiscountType discountType, Locale userLocale);
    
    void validateAdditionalDiscountValue(BigDecimal value, DiscountType discountType, BigDecimal orderTotal, Locale userLocale);
    
    void validateAdditionalDiscountPercentRange(BigDecimal percentValue, Locale userLocale);
    
    void validateAdditionalDiscountFlatNotExceedingTotal(BigDecimal flatValue, BigDecimal orderTotal, Locale userLocale);

    // ==================== TRANSACTION VALIDATIONS ====================
    
    Transaction validateAndGetTransaction(UUID orderId, Locale userLocale);
    
    void validateTransactionStatusForPayment(Transaction transaction, Locale userLocale);

    // ==================== USER ROLE VALIDATION METHODS ====================
    
    boolean isCashier(User user);
    
    boolean isManager(User user);
    
    boolean isUserWaiter(User user);
    
    boolean requiresCancellationApproval(ItemStatus currentStatus);

    // ==================== TRANSLATION HELPER METHODS ====================
    
    /**
     * Resolves combo display name: request locale first, then each configured language in order (no other languages).
     *
     * @throws org.springframework.web.server.ResponseStatusException 404 if no non-blank name exists for those languages
     */
    String getComboName(Combo combo, Locale userLocale);
    
    String getComboGroupName(ComboGroup comboGroup, Locale userLocale);
    
    /**
     * Same resolution order as {@link #getComboName(Combo, Locale)} for item translations.
     *
     * @throws org.springframework.web.server.ResponseStatusException 404 if no non-blank name exists for those languages
     */
    String getItemName(Item item, Locale userLocale);
    
    String getModifierGroupName(ModifierGroup modifierGroup, Locale userLocale);
    
    String getModifierItemName(ModifierItem modifierItem, Locale userLocale);
    
    String getRestaurantName(Restaurant restaurant, Locale userLocale);

    // ==================== BXGY HELPER METHODS ====================
    
    BxgyItemInfo reconstructBxgyInfo(OrderedItem orderedItem, UUID menuId);
    
    CategoryItemMapping getCategoryItemMappingForOrderedItem(OrderedItem orderedItem, UUID menuId);

    // ==================== TABLE ASSIGNMENT HELPER METHODS ====================
    
    User getWaiterForTable(RestaurantTable table);
    
    List<User> getWaitersForTable(RestaurantTable table);

    // ==================== ORDER HELPER METHODS ====================
    
    UUID getMenuIdFromOrder(Order order);
    
    List<OrderedItemRequest> filterOutCancelledItems(List<OrderedItemRequest> orderedItems, Locale userLocale);
    
    List<OrderedComboRequest> filterOutCancelledCombos(List<OrderedComboRequest> orderedCombos, Locale userLocale);
}

