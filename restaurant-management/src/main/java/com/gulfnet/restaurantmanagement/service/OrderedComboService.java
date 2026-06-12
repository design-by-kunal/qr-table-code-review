package com.gulfnet.restaurantmanagement.service;

import com.gulfnet.shared_library.entity.Combo;
import com.gulfnet.shared_library.entity.Order;
import com.gulfnet.shared_library.entity.OrderedCombo;
import com.gulfnet.shared_library.entity.OrderedItem;
import com.gulfnet.shared_library.entity.User;
import com.gulfnet.shared_library.entity.ComboItemModifier;
import com.gulfnet.shared_library.model.request.ItemStatusPayload;
import com.gulfnet.shared_library.model.request.OrderedComboItemModifierRequest;
import com.gulfnet.shared_library.model.request.OrderedComboRequest;
import com.gulfnet.shared_library.model.response.dto.ItemStatusResponseWrapper;
import com.gulfnet.shared_library.model.response.dto.ResponseDto;
import com.gulfnet.shared_library.model.response.dto.OrderedComboGroupResponse;
import com.gulfnet.shared_library.model.response.dto.OrderedComboItemModifierResponse;
import com.gulfnet.shared_library.model.response.dto.OrderedComboItemResponse;
import com.gulfnet.shared_library.model.response.dto.OrderedComboResponse;

import java.util.List;
import java.util.Locale;
import java.util.UUID;

public interface OrderedComboService {

    void createFixedOrderedItems(OrderedCombo orderedCombo, OrderedComboRequest comboRequest, User createdBy, Locale userLocale);

    void createChoiceOrderedItems(OrderedCombo orderedCombo, OrderedComboRequest comboRequest, User createdBy, Locale userLocale);

    void createMixedOrderedItems(OrderedCombo orderedCombo, OrderedComboRequest comboRequest, User createdBy, Locale userLocale);

    void createFixedGroupOrderedItems(OrderedCombo orderedCombo, com.gulfnet.shared_library.entity.ComboGroup comboGroup, User createdBy, Locale userLocale);

    void createChoiceGroupOrderedItems(OrderedCombo orderedCombo, com.gulfnet.shared_library.model.request.OrderedComboGroupRequest groupRequest, User createdBy, Locale userLocale);

    void createPredefinedOrderedItemModifiers(OrderedItem orderedItem, 
                                               List<com.gulfnet.shared_library.entity.ComboItemModifier> predefinedModifiers, 
                                               User createdBy, Locale userLocale);


    OrderedComboResponse buildOrderedComboResponse(OrderedCombo orderedCombo, Locale userLocale);

    OrderedComboGroupResponse buildOrderedComboGroupResponse(com.gulfnet.shared_library.entity.ComboGroup comboGroup, OrderedCombo orderedCombo, Locale userLocale);

    OrderedComboItemResponse buildOrderedComboItemResponse(OrderedItem orderedItem, Locale userLocale, Boolean isDefault);

    OrderedCombo createNewOrderedCombo(Order order, OrderedComboRequest comboRequest, UUID menuId, User createdBy, Locale userLocale);

    OrderedCombo createFixedOrderedCombo(Order order, OrderedComboRequest comboRequest, User createdBy, Locale userLocale);

    OrderedCombo createChoiceOrderedCombo(Order order, OrderedComboRequest comboRequest, User createdBy, Locale userLocale);

    OrderedCombo createMixedOrderedCombo(Order order, OrderedComboRequest comboRequest, User createdBy, Locale userLocale);

    void checkAndUpdateComboStatusWhenAllItemsCooking(OrderedItem orderedItem, User authenticatedUser, boolean hasUserId, Locale userLocale);

    void checkAndUpdateComboStatusWhenAllItemsReady(OrderedItem orderedItem, User authenticatedUser, boolean hasUserId, Locale userLocale);

    void checkAndUpdateComboStatusWhenAllItemsServed(OrderedItem orderedItem, User authenticatedUser, boolean hasUserId, Locale userLocale);

    void checkAndUpdateComboStatusWhenAllItemsCanceled(OrderedItem orderedItem, User authenticatedUser, boolean hasUserId, Locale userLocale);

    void updateComboStatusFromItems(OrderedCombo combo, List<OrderedItem> updatedItems, User authenticatedUser, boolean hasUserId, Locale userLocale);

    // ==================== CALCULATION RESPONSE BUILDERS ====================

    List<OrderedComboResponse> buildCalculationComboResponses(List<OrderedComboRequest> comboRequests, UUID restaurantId, UUID menuId, Locale userLocale);

    List<OrderedComboGroupResponse> buildCalculationComboGroups(Combo combo, OrderedComboRequest comboRequest, UUID restaurantId, UUID menuId, Locale userLocale);

    List<OrderedComboItemModifierResponse> buildCalculationComboItemModifiers(List<ComboItemModifier> predefinedModifiers, Locale userLocale);

    List<OrderedComboItemModifierResponse> buildCalculationComboItemModifiersFromRequest(List<OrderedComboItemModifierRequest> modifierRequests, Locale userLocale);

    // ==================== AVAILABILITY CALCULATION ====================

    Boolean calculateComboAvailability(Combo combo, UUID restaurantId, UUID menuId);

    // ==================== HELPER METHODS ====================

    String applyReasonIfProvided(OrderedCombo orderedCombo, String reason);

    // ==================== CANCELLATION REQUEST HANDLER ====================

    ResponseDto<ItemStatusResponseWrapper> handleComboCancellationRequest(OrderedCombo orderedCombo, ItemStatusPayload payload, User authenticatedUser, Locale userLocale);
}

