package com.gulfnet.restaurantmanagement.service;

import com.gulfnet.shared_library.entity.CategoryItemMapping;
import com.gulfnet.shared_library.entity.Item;
import com.gulfnet.shared_library.entity.ModifierGroup;
import com.gulfnet.shared_library.entity.ModifierItem;
import com.gulfnet.shared_library.entity.Order;
import com.gulfnet.shared_library.entity.OrderedItem;
import com.gulfnet.shared_library.entity.OrderedItemModifier;
import com.gulfnet.shared_library.entity.User;
import com.gulfnet.shared_library.model.request.ItemStatusPayload;
import com.gulfnet.shared_library.model.request.OrderedComboItemModifierRequest;
import com.gulfnet.shared_library.model.request.OrderedItemModifierRequest;
import com.gulfnet.shared_library.model.request.OrderedItemRequest;
import com.gulfnet.shared_library.model.response.dto.ItemPriceCalculationResult;
import com.gulfnet.shared_library.model.response.dto.ItemStatusResponseWrapper;
import com.gulfnet.shared_library.model.response.dto.ResponseDto;
import com.gulfnet.shared_library.model.response.dto.OrderedItemModifierResponse;
import com.gulfnet.shared_library.model.response.dto.OrderedItemResponse;
import com.gulfnet.restaurantmanagement.util.PriceOverrideHelper;

import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

public interface OrderedItemService {

    /**
     * Creates and persists a new {@link OrderedItem} for an order based on the incoming request.
     * <p>
     * Implementations are expected to:
     * </p>
     * <ul>
     *   <li>Resolve pricing (including price overrides and BXGY handling) using the provided precomputed maps.</li>
     *   <li>Create and attach modifier selections ({@link OrderedItemModifier}) when present.</li>
     *   <li>Populate audit fields using {@code updatedBy} and return the created {@link OrderedItem} entity.</li>
     * </ul>
     *
     * @param order                  owning order
     * @param itemRequest             item request payload
     * @param menuId                  menu identifier for pricing/validation context
     * @param restaurantId            restaurant identifier for availability/override context
     * @param activeOverrideIndex     active overrides index (optional)
     * @param updatedBy               user to set as created/updated by
     * @param userLocale              locale for localized validation messages
     * @param itemPrices              computed per-item totals (buy/regular items)
     * @param getItemPrices           computed per-item totals for BXGY get-items (used when item is both buy and get)
     * @param paidQuantitiesByRequest computed paid quantities keyed by request signature for BXGY get-items
     * @param bxgyInfoByRequest       BXGY metadata keyed by request signature
     * @return created ordered item entity
     */
    OrderedItem createNewOrderedItem(
            Order order,
            OrderedItemRequest itemRequest,
            UUID menuId,
            UUID restaurantId,
            PriceOverrideHelper.ActiveOverrideIndex activeOverrideIndex,
            User updatedBy,
            Locale userLocale,
            Map<UUID, BigDecimal> itemPrices,
            Map<UUID, BigDecimal> getItemPrices,
            Map<String, Integer> paidQuantitiesByRequest,
            Map<String, com.gulfnet.shared_library.model.response.dto.BxgyItemInfo> bxgyInfoByRequest);

    /**
     * Same contract as {@link #createNewOrderedItem(Order, OrderedItemRequest, UUID, UUID, PriceOverrideHelper.ActiveOverrideIndex, User, Locale, Map, Map, Map, Map)}
     * with optional batch-loaded {@link Item}, {@link ModifierItem}, and {@link ModifierGroup} maps to avoid N+1 lookups
     * (maps may be {@code null} to fall back to per-entity loading in the implementation).
     */
    OrderedItem createNewOrderedItem(
            Order order,
            OrderedItemRequest itemRequest,
            UUID menuId,
            UUID restaurantId,
            PriceOverrideHelper.ActiveOverrideIndex activeOverrideIndex,
            User updatedBy,
            Locale userLocale,
            Map<UUID, BigDecimal> itemPrices,
            Map<UUID, BigDecimal> getItemPrices,
            Map<String, Integer> paidQuantitiesByRequest,
            Map<UUID, Item> itemsMap,
            Map<UUID, ModifierItem> modifierItemsMap,
            Map<UUID, ModifierGroup> modifierGroupsMap,
            Map<String, com.gulfnet.shared_library.model.response.dto.BxgyItemInfo> bxgyInfoByRequest);

    /**
     * Creates OrderedItemModifier entities for combo items
     * 
     * @param orderedItem The ordered item
     * @param selectedModifiers List of modifier requests
     * @param createdBy User creating the modifiers
     * @param userLocale User locale
     */
    void createSelectedOrderedItemModifiers(
            OrderedItem orderedItem,
            List<OrderedComboItemModifierRequest> selectedModifiers,
            User createdBy,
            Locale userLocale);

    OrderedItemResponse buildOrderedItemResponse(OrderedItem orderedItem, UUID restaurantId, Locale userLocale);

    OrderedItemResponse buildOrderedItemResponse(
            OrderedItem orderedItem,
            OrderedItemRequest originalRequest,
            UUID restaurantId,
            UUID menuId,
            Locale userLocale);

    /**
     * Overload for batch response building: allows callers to provide preloaded modifiers
     * to avoid a DB query per ordered item.
     *
     * <p>If {@code preloadedModifiers} is {@code null}, implementations should fall back
     * to loading modifiers as usual.</p>
     */
    OrderedItemResponse buildOrderedItemResponse(
            OrderedItem orderedItem,
            OrderedItemRequest originalRequest,
            UUID restaurantId,
            UUID menuId,
            Locale userLocale,
            List<OrderedItemModifier> preloadedModifiers);

    /**
     * Overload for batch response building: allows callers to provide preloaded modifiers,
     * request-level caches (presigned URLs) and precomputed availability.
     *
     * <p>Any optional argument may be {@code null} to fall back to default behavior.</p>
     */
    OrderedItemResponse buildOrderedItemResponse(
            OrderedItem orderedItem,
            OrderedItemRequest originalRequest,
            UUID restaurantId,
            UUID menuId,
            Locale userLocale,
            List<OrderedItemModifier> preloadedModifiers,
            Map<String, String> presignedUrlCache,
            Boolean precomputedAvailability);

    OrderedItemResponse buildCalculationItemResponse(
            OrderedItemRequest itemRequest,
            Map<UUID, BigDecimal> itemPrices,
            Map<UUID, BigDecimal> getItemPrices,
            Map<String, Integer> paidQuantitiesByRequest,
            UUID menuId,
            UUID restaurantId,
            PriceOverrideHelper.ActiveOverrideIndex activeOverrideIndex,
            Locale userLocale);

    List<OrderedItemModifierResponse> buildModifierResponses(
            List<OrderedItemModifierRequest> modifierRequests,
            Locale userLocale);

    ItemPriceCalculationResult calculateItemPriceForExistingOrder(
            OrderedItem orderedItem,
            List<OrderedItemModifier> modifiers);

    List<OrderedItemModifierRequest> getExistingItemModifiers(OrderedItem existingItem);

    Boolean checkItemAvailabilityForOrderedItem(Item item, UUID restaurantId, UUID menuId);

    // ==================== KDS ASSIGNMENT ====================
    
    void assignItemToKds(OrderedItem orderedItem, UUID restaurantId);

    // ==================== AVAILABILITY CALCULATION ====================

    Boolean calculateItemAvailability(CategoryItemMapping categoryItemMapping, UUID restaurantId);

    // ==================== HELPER METHODS ====================

    String applyReasonIfProvided(OrderedItem orderedItem, String reason);

    // ==================== CANCELLATION REQUEST HANDLER ====================

    ResponseDto<ItemStatusResponseWrapper> handleCancellationRequest(OrderedItem orderedItem, ItemStatusPayload payload, User authenticatedUser, Locale userLocale);
}

