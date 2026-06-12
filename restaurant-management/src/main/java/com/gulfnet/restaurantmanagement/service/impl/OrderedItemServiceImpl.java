package com.gulfnet.restaurantmanagement.service.impl;

import com.gulfnet.restaurantmanagement.config.RestaurantChainConfigProperties;
import com.gulfnet.restaurantmanagement.service.NotificationService;
import com.gulfnet.restaurantmanagement.service.OrderNotificationService;
import com.gulfnet.restaurantmanagement.service.OrderRecalculationService;
import com.gulfnet.restaurantmanagement.service.OrderedItemService;
import com.gulfnet.restaurantmanagement.service.OrderPricingService;
import com.gulfnet.restaurantmanagement.service.OrderValidationService;
import com.gulfnet.restaurantmanagement.service.AuditTrailService;
import com.gulfnet.restaurantmanagement.util.PriceOverrideHelper;
import com.gulfnet.shared_library.entity.*;
import com.gulfnet.shared_library.entity.Role;
import com.gulfnet.shared_library.enums.ItemStatus;
import com.gulfnet.shared_library.enums.RequestStatus;
import com.gulfnet.shared_library.enums.ActionType;
import com.gulfnet.shared_library.enums.DiscountType;
import com.gulfnet.shared_library.model.request.ItemCancellationRequestDto;
import com.gulfnet.shared_library.model.request.ItemStatusPayload;
import com.gulfnet.shared_library.model.request.OrderedComboItemModifierRequest;
import com.gulfnet.shared_library.model.request.OrderedItemModifierRequest;
import com.gulfnet.shared_library.model.request.OrderedItemRequest;
import com.gulfnet.shared_library.model.response.dto.ItemPriceCalculationResult;
import com.gulfnet.shared_library.model.response.dto.ItemStatusResponseWrapper;
import com.gulfnet.shared_library.model.response.dto.ModifierItemResponse;
import com.gulfnet.shared_library.model.response.dto.OrderRecalculationResult;
import com.gulfnet.shared_library.model.response.dto.ResponseDto;
import com.gulfnet.shared_library.model.response.dto.OrderedItemModifierResponse;
import com.gulfnet.shared_library.model.response.dto.OrderedItemResponse;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gulfnet.shared_library.repository.*;
import com.gulfnet.shared_library.util.CurrencyFormatter;
import com.gulfnet.restaurantmanagement.util.MessageUtil;
import com.gulfnet.shared_library.config.AWSService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrderedItemServiceImpl implements OrderedItemService {

    // Constants for message keys
    private static final String MSG_MODIFIER_ITEM_NOT_FOUND = "modifier.item.not.found";

    private final OrderedItemRepository orderedItemRepository;
    private final OrderedItemModifierRepository orderedItemModifierRepository;
    private final ItemRepository itemRepository;
    private final ModifierGroupRepository modifierGroupRepository;
    private final ModifierItemRepository modifierItemRepository;
    private final CategoryItemMappingRepository categoryItemMappingRepository;
    private final RestaurantItemAvailabilityRepository restaurantItemAvailabilityRepository;
    private final KdsRepository kdsRepository;
    private final CategoryKdsRepository categoryKdsRepository;
    private final RestaurantChainConfigProperties restaurantChainConfigProperties;
    private final OrderPricingService orderPricingService;
    private final OrderValidationService orderValidationService;
    private final OrderNotificationService orderNotificationService;
    @Lazy
    @Autowired
    private OrderRecalculationService orderRecalculationService;
    private final NotificationService notificationService;
    private final UserRepository userRepository;
    private final MessageUtil messageUtil;
    private final AWSService awsService;
    private final RoleRepository roleRepository;
    private final AuditTrailService auditTrailService;
    private final DiscountRepository discountRepository;

    /**
     * Checks item availability for a specific restaurant and optional menu.
     * Checks restaurant-specific availability overrides, defaulting to available if no override exists.
     *
     * @param item         the item to check availability for
     * @param restaurantId the restaurant ID to check availability for
     * @param menuId       optional menu ID to filter category item mappings
     * @return {@code true} if item is available, {@code false} if explicitly unavailable, defaults to {@code true} on error
     */
    @Override
    public Boolean checkItemAvailabilityForOrderedItem(Item item, UUID restaurantId, UUID menuId) {
        if (item == null || restaurantId == null) {
            // Default to available if we can't determine
            return true;
        }

        try {
            // Find CategoryItemMappings for this item
            List<CategoryItemMapping> categoryItemMappings;
            
            if (menuId != null) {
                // Filter to only CategoryItemMappings in the specific menu
                categoryItemMappings = categoryItemMappingRepository
                        .findByMenuCategoryMappingMenuId(menuId)
                        .stream()
                        .filter(mapping -> mapping.getItem().getId().equals(item.getId()))
                        .collect(Collectors.toList());
            } else {
                // Find all CategoryItemMappings for this item (across all menus)
                categoryItemMappings = categoryItemMappingRepository
                        .findByItem_Id(item.getId());
            }

            if (categoryItemMappings.isEmpty()) {
                // No mappings found; default to available
                return true;
            }

            // Get all categoryItemMappingIds
            List<UUID> categoryItemMappingIds = categoryItemMappings.stream()
                    .map(CategoryItemMapping::getId)
                    .collect(Collectors.toList());

            // Check restaurant-specific availability overrides
            List<RestaurantItemAvailability> availabilityOverrides = restaurantItemAvailabilityRepository
                    .findByRestaurantIdAndCategoryItemMappingIdIn(restaurantId, categoryItemMappingIds);

            if (!availabilityOverrides.isEmpty()) {
                // Use the first available override (should be one per restaurant+categoryItemMapping)
                // If multiple exist for the same item in different categories, use the first one found
                Boolean isAvailable = availabilityOverrides.get(0).getIsAvailable();
                return isAvailable != null ? isAvailable : Boolean.TRUE;
            }

            // No restaurant-specific override; default to available
            return true;
        } catch (Exception e) {
            log.warn("Error checking item availability for item {} in restaurant {} (menu: {}): {}", 
                    item.getId(), restaurantId, menuId, e.getMessage());
            // Default to available on error
            return true;
        }
    }

    /**
     * Retrieves existing modifiers for an ordered item and converts them to modifier requests.
     * Groups modifiers by modifier group and collects modifier item IDs for each group.
     *
     * @param existingItem the ordered item to get modifiers for
     * @return list of {@link OrderedItemModifierRequest} objects grouped by modifier group
     */
    @Override
    public List<OrderedItemModifierRequest> getExistingItemModifiers(OrderedItem existingItem) {
        List<OrderedItemModifier> modifiers = orderedItemModifierRepository.findByOrderedItemId(existingItem.getId());
        
        if (modifiers.isEmpty()) {
            return new ArrayList<>();
        }
        
        // Group modifiers by modifier group
        Map<UUID, List<OrderedItemModifier>> modifiersByGroup = modifiers.stream()
                .collect(Collectors.groupingBy(m -> m.getModifierGroup().getId()));
        
        List<OrderedItemModifierRequest> modifierRequests = new ArrayList<>();
        
        for (Map.Entry<UUID, List<OrderedItemModifier>> entry : modifiersByGroup.entrySet()) {
            UUID groupId = entry.getKey();
            List<UUID> modifierItemIds = entry.getValue().stream()
                    .map(m -> m.getModifierItem().getId())
                    .collect(Collectors.toList());
            
            OrderedItemModifierRequest modifierRequest = OrderedItemModifierRequest.builder()
                    .modifierGroupId(groupId)
                    .modifierItemIds(modifierItemIds)
                    .build();
            
            modifierRequests.add(modifierRequest);
        }
        
        return modifierRequests;
    }

    /**
     * Calculates item price for an existing ordered item using stored database values.
     * Uses the stored price, discounted price, and total amounts from the OrderedItem entity.
     *
     * @param orderedItem the existing ordered item to calculate price for
     * @param modifiers   list of modifiers associated with the ordered item
     * @return {@link ItemPriceCalculationResult} containing calculated prices and totals
     * @throws IllegalArgumentException if quantity is zero or negative
     */
    @Override
    public ItemPriceCalculationResult calculateItemPriceForExistingOrder(
            OrderedItem orderedItem,
            List<OrderedItemModifier> modifiers) {
        if (orderedItem.getQuantity() <= 0) {
            throw new IllegalArgumentException("Quantity must be greater than zero for price calculation.");
        }

        // Get currency for formatting
        String currency = restaurantChainConfigProperties.getChain().getCurrency();
        
        // Use database values directly from OrderedItem entity
        // Base price per unit - use stored price from database, fallback to Item.basePrice for legacy data
        BigDecimal basePricePerUnit;
        if (orderedItem.getPrice() != null) {
            basePricePerUnit = CurrencyFormatter.formatAmount(orderedItem.getPrice(), currency);
        } else {
            // Fallback for legacy data
            basePricePerUnit = CurrencyFormatter.formatAmount(
                BigDecimal.valueOf(orderedItem.getItem().getBasePrice()), 
                currency);
        }

        // Discounted price per unit - use stored discountedPrice from database
        // If null, it means no discount was applied (per requirements)
        BigDecimal discountedPricePerUnit = null;
        if (orderedItem.getDiscountedPrice() != null) {
            discountedPricePerUnit = CurrencyFormatter.formatAmount(orderedItem.getDiscountedPrice(), currency);
        }
        // If discountedPrice is null, it means no discount was applied, so keep it as null

        // Total amount without discount - use stored totalItemAmount from database
        BigDecimal totalAmountWithoutDiscount;
        if (orderedItem.getTotalItemAmount() != null) {
            totalAmountWithoutDiscount = CurrencyFormatter.formatAmount(orderedItem.getTotalItemAmount(), currency);
        } else {
            // Fallback: calculate from base price and modifiers
            BigDecimal quantity = BigDecimal.valueOf(orderedItem.getQuantity());
            BigDecimal perUnitModifierPrice = modifiers.stream()
                .map(OrderedItemModifier::getPrice)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
            perUnitModifierPrice = CurrencyFormatter.formatAmount(perUnitModifierPrice, currency);
            totalAmountWithoutDiscount = CurrencyFormatter.formatAmount(
                basePricePerUnit.add(perUnitModifierPrice).multiply(quantity), 
                currency);
        }

        // Total amount with discount - use stored totalDiscountedItemAmount from database
        // If null, it means no discount was applied (per requirements)
        BigDecimal totalAmountWithDiscount = null;
        if (orderedItem.getTotalDiscountedItemAmount() != null) {
            totalAmountWithDiscount = CurrencyFormatter.formatAmount(orderedItem.getTotalDiscountedItemAmount(), currency);
        }
        // If totalDiscountedItemAmount is null, it means no discount was applied, so keep it as null

        return new ItemPriceCalculationResult(
            basePricePerUnit,
            discountedPricePerUnit,
            totalAmountWithoutDiscount,
            totalAmountWithDiscount
        );
    }

    /**
     * Builds modifier response DTOs from modifier requests with localized names and prices.
     * Retrieves modifier groups and items, applies translations, and formats prices.
     *
     * @param modifierRequests list of modifier requests to convert to responses
     * @param userLocale       locale for localized modifier group and item names
     * @return list of {@link OrderedItemModifierResponse} with localized names and prices
     * @throws ResponseStatusException if modifier group or item is not found
     */
    @Override
    public List<OrderedItemModifierResponse> buildModifierResponses(
            List<OrderedItemModifierRequest> modifierRequests,
            Locale userLocale) {
        if (modifierRequests == null || modifierRequests.isEmpty()) {
            return new ArrayList<>();
        }
        
        String currency = restaurantChainConfigProperties.getChain() != null ? restaurantChainConfigProperties.getChain().getCurrency() : null;
        
        List<OrderedItemModifierResponse> modifierResponses = new ArrayList<>();
        for (OrderedItemModifierRequest modifierRequest : modifierRequests) {
            ModifierGroup modifierGroup = modifierGroupRepository.findById(modifierRequest.getModifierGroupId())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                            messageUtil.getMessage("modifier.group.not.found", userLocale)));
            
            String groupName = modifierGroup.getTranslations().stream()
                    .filter(t -> t.getLanguageCode().equals(userLocale.getLanguage()))
                    .findFirst()
                    .map(ModifierGroupTranslation::getName)
                    .orElse(modifierGroup.getTranslations().isEmpty() ? "Modifier Group" : modifierGroup.getTranslations().get(0).getName());
            
            List<ModifierItemResponse> modifierItemResponses = new ArrayList<>();
            for (UUID modifierItemId : modifierRequest.getModifierItemIds()) {
                ModifierItem modifierItem = modifierItemRepository.findById(modifierItemId)
                        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                                messageUtil.getMessage(MSG_MODIFIER_ITEM_NOT_FOUND, userLocale)));
                
                modifierItemResponses.add(ModifierItemResponse.builder()
                    .id(null) // No ID for calculation
                    .modifierItemId(modifierItem.getId())
                    .modifierItemName(modifierItem.getTranslations().stream()
                            .filter(t -> t.getLanguageCode().equals(userLocale.getLanguage()))
                            .findFirst()
                            .map(ModifierItemTranslation::getName)
                            .orElse(modifierItem.getTranslations().isEmpty() ? 
                                "Modifier Item" : modifierItem.getTranslations().get(0).getName()))
                    .price(modifierItem.getPrice() != null ? CurrencyFormatter.formatAmount(modifierItem.getPrice(), currency) : BigDecimal.ZERO)
                    .build());
            }
            
            modifierResponses.add(OrderedItemModifierResponse.builder()
                .modifierGroupId(modifierRequest.getModifierGroupId())
                .modifierGroupName(groupName)
                .modifierItems(modifierItemResponses)
                .build());
        }
        
        return modifierResponses;
    }

    // ==================== PLACEHOLDER METHODS (TO BE IMPLEMENTED) ====================
    
    /**
     * {@inheritDoc}
     * <p>
     * Delegates to the batch-map overload with {@code null} entity maps.
     */
    @Override
    public OrderedItem createNewOrderedItem(
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
            Map<String, com.gulfnet.shared_library.model.response.dto.BxgyItemInfo> bxgyInfoByRequest) {
        // Delegate to overloaded method with null maps (backward compatibility)
        // Other endpoints will query individually, which is fine for single-item operations
        return createNewOrderedItem(order, itemRequest, menuId, restaurantId, activeOverrideIndex, 
                updatedBy, userLocale, itemPrices, getItemPrices, paidQuantitiesByRequest, 
                null, null, null, bxgyInfoByRequest);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public OrderedItem createNewOrderedItem(
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
            Map<String, com.gulfnet.shared_library.model.response.dto.BxgyItemInfo> bxgyInfoByRequest) {
        Item item = resolveItemForOrderedItem(itemRequest, itemsMap, userLocale);
        com.gulfnet.shared_library.model.response.dto.BxgyItemInfo bxgyInfo =
                resolveBxgyInfoForRequest(itemRequest, bxgyInfoByRequest);
        com.gulfnet.shared_library.model.response.dto.DiscountCalculationResult discountResult =
                calculateDiscountResultForNewItem(menuId, item, itemRequest.getQuantity(), restaurantId, activeOverrideIndex);
        
        boolean isBxgyGetItem = Boolean.TRUE.equals(itemRequest.getIsGetItem());
        boolean isBxgyBuyItem = Boolean.TRUE.equals(itemRequest.getIsBuyItem());
        BigDecimal itemPrice = resolveItemPriceForNewOrderedItem(itemRequest, itemPrices, discountResult, isBxgyBuyItem, isBxgyGetItem);
        
        log.debug("DISCOUNT CALCULATION DEBUG - Item: {}, OriginalPrice: {}, FinalPrice: {}, Discount: {}, AppliedTo: {}",
                itemRequest.getItemId(),
                discountResult.getOriginalPrice(),
                discountResult.getFinalPrice(),
                discountResult.getAppliedDiscount() != null ? discountResult.getAppliedDiscount().getValue() : "None",
                discountResult.getDiscountLevel());

        BigDecimal modifierPricePerItem = calculateModifierPricePerItem(itemRequest, modifierItemsMap, userLocale);

        // Calculate total modifier price for this item
        BigDecimal totalModifierPrice = modifierPricePerItem.multiply(BigDecimal.valueOf(itemRequest.getQuantity()));
        
        // Get currency for formatting
        String currency = restaurantChainConfigProperties.getChain() != null ? restaurantChainConfigProperties.getChain().getCurrency() : null;
        
        // Determine item status:
        // - always PUSHED for cashiers
        // - customer orders are PUSHED when waiter dependency is disabled (except TAKEAWAY)
        // - otherwise ON_HOLD
        ItemStatus itemStatus = determineInitialItemStatus(order, updatedBy);
        
        CreateNewOrderedItemContext ctx = new CreateNewOrderedItemContext(
                order,
                item,
                itemRequest,
                restaurantId,
                updatedBy,
                userLocale,
                itemPrices,
                getItemPrices,
                modifierItemsMap,
                modifierGroupsMap,
                bxgyInfo,
                discountResult,
                itemPrice,
                modifierPricePerItem,
                totalModifierPrice,
                currency,
                itemStatus,
                isBxgyGetItem,
                isBxgyBuyItem
        );

        if (ctx.isBxgyGetItem()) {
            return createBxgyGetOrderedItem(ctx);
        }
        return createRegularOrderedItem(ctx);
    }

    private com.gulfnet.shared_library.model.response.dto.DiscountCalculationResult calculateDiscountResultForNewItem(
            UUID menuId,
            Item item,
            int quantity,
            UUID restaurantId,
            PriceOverrideHelper.ActiveOverrideIndex activeOverrideIndex) {
        // Use overloaded method that accepts Item to avoid duplicate query in calculateItemPriceWithOverride.
        if (restaurantId != null && activeOverrideIndex != null) {
            return orderPricingService.calculateItemPriceWithOverride(
                    menuId, item, quantity, restaurantId, activeOverrideIndex);
        }
        return orderPricingService.calculateItemPriceWithOverride(
                menuId, item, quantity, null, null);
    }

    private ItemStatus determineInitialItemStatus(Order order, User updatedBy) {
        // For TAKEAWAY orders, never auto-push at placement time (regardless of who creates the order).
        if (order != null && order.getOrderType() == com.gulfnet.shared_library.enums.OrderType.TAKEAWAY) {
            return ItemStatus.ON_HOLD;
        }
        if (updatedBy != null && isCashier(updatedBy)) {
            return ItemStatus.PUSHED;
        }
        boolean waiterDependencyEnabled = restaurantChainConfigProperties.isWaiterDependencyEnabled();
        if (!waiterDependencyEnabled
                && updatedBy == null
                && order != null
                && order.getOrderType() != com.gulfnet.shared_library.enums.OrderType.TAKEAWAY) {
            return ItemStatus.PUSHED;
        }
        return ItemStatus.ON_HOLD;
    }

    private BigDecimal calculateModifierPricePerItem(OrderedItemRequest itemRequest,
                                                    Map<UUID, ModifierItem> modifierItemsMap,
                                                    Locale userLocale) {
        if (itemRequest.getOrderedItemModifiers() == null) {
            return BigDecimal.ZERO;
        }
        BigDecimal modifierPricePerItem = BigDecimal.ZERO;
        for (OrderedItemModifierRequest modifierRequest : itemRequest.getOrderedItemModifiers()) {
            orderValidationService.validateOrderedItemModifier(modifierRequest, userLocale);
            for (UUID modifierItemId : modifierRequest.getModifierItemIds()) {
                ModifierItem modifierItem = getModifierItem(modifierItemId, modifierItemsMap, userLocale);
                BigDecimal modifierItemPrice = modifierItem.getPrice() != null ? modifierItem.getPrice() : BigDecimal.ZERO;
                modifierPricePerItem = modifierPricePerItem.add(modifierItemPrice);
            }
        }
        return modifierPricePerItem;
    }

    private record CreateNewOrderedItemContext(
            Order order,
            Item item,
            OrderedItemRequest itemRequest,
            UUID restaurantId,
            User updatedBy,
            Locale userLocale,
            Map<UUID, BigDecimal> itemPrices,
            Map<UUID, BigDecimal> getItemPrices,
            Map<UUID, ModifierItem> modifierItemsMap,
            Map<UUID, ModifierGroup> modifierGroupsMap,
            com.gulfnet.shared_library.model.response.dto.BxgyItemInfo bxgyInfo,
            com.gulfnet.shared_library.model.response.dto.DiscountCalculationResult discountResult,
            BigDecimal itemPrice,
            BigDecimal modifierPricePerItem,
            BigDecimal totalModifierPrice,
            String currency,
            ItemStatus itemStatus,
            boolean isBxgyGetItem,
            boolean isBxgyBuyItem
    ) {}

    private BigDecimal resolveItemPriceForNewOrderedItem(OrderedItemRequest itemRequest,
                                                        Map<UUID, BigDecimal> itemPrices,
                                                        com.gulfnet.shared_library.model.response.dto.DiscountCalculationResult discountResult,
                                                        boolean isBxgyBuyItem,
                                                        boolean isBxgyGetItem) {
        if (itemPrices != null && itemPrices.containsKey(itemRequest.getItemId())) {
            BigDecimal priceFromMap = itemPrices.get(itemRequest.getItemId());
            log.debug("Using BXGY-adjusted price for item {}: {} (isBuyItem: {}, isGetItem: {})",
                    itemRequest.getItemId(), priceFromMap, isBxgyBuyItem, isBxgyGetItem);
            return priceFromMap;
        }
        return discountResult.getFinalPrice();
    }

    private OrderedItem.OrderedItemBuilder baseOrderedItemBuilder(CreateNewOrderedItemContext ctx, BigDecimal basePricePerUnit) {
        return OrderedItem.builder()
                .order(ctx.order())
                .item(ctx.item())
                .alcoholType(ctx.item().getAlcoholType())
                .quantity(ctx.itemRequest().getQuantity())
                .price(basePricePerUnit)
                .itemStatus(ctx.itemStatus())
                .notes(ctx.itemRequest().getNotes())
                .createdAt(OffsetDateTime.now(ZoneOffset.UTC))
                .createdBy(ctx.order().getCreatedBy())
                .updatedAt(OffsetDateTime.now(ZoneOffset.UTC))
                .updatedBy(ctx.updatedBy());
    }

    private OrderedItem createBxgyGetOrderedItem(CreateNewOrderedItemContext ctx) {
        BigDecimal basePriceTotal = ctx.discountResult().getOriginalPrice();
        if (basePriceTotal.compareTo(BigDecimal.ZERO) <= 0) {
            basePriceTotal = CurrencyFormatter.formatAmount(
                    BigDecimal.valueOf(ctx.item().getBasePrice())
                            .multiply(BigDecimal.valueOf(ctx.itemRequest().getQuantity())),
                    ctx.currency());
        }

        BigDecimal basePricePerUnit = basePriceTotal.divide(
                BigDecimal.valueOf(ctx.itemRequest().getQuantity()), 2, RoundingMode.HALF_UP);

        BigDecimal totalItemAmount = CurrencyFormatter.formatAmount(basePriceTotal.add(ctx.totalModifierPrice()), ctx.currency());

        int paidQuantity = calculatePaidQuantity(ctx.itemRequest().getQuantity(), ctx.itemRequest().getFreeQuantity());
        BigDecimal paidItemPrice = CurrencyFormatter.formatAmount(
                basePricePerUnit.multiply(BigDecimal.valueOf(paidQuantity)),
                ctx.currency());

        BigDecimal totalDiscountedItemAmount = CurrencyFormatter.formatAmount(paidItemPrice.add(ctx.totalModifierPrice()), ctx.currency());

        OrderedItem.OrderedItemBuilder itemBuilder = baseOrderedItemBuilder(ctx, basePricePerUnit)
                .discountedPrice(BigDecimal.ZERO)
                .totalItemAmount(totalItemAmount)
                .totalDiscountedItemAmount(totalDiscountedItemAmount);

        applyBxgyFieldsIfPresent(itemBuilder, ctx.bxgyInfo());

        OrderedItem newItem = orderedItemRepository.save(itemBuilder.build());
        saveModifiersAndAssignKds(new SaveModifiersContext(
                newItem,
                ctx.itemRequest(),
                ctx.order(),
                ctx.updatedBy(),
                ctx.restaurantId(),
                ctx.modifierGroupsMap(),
                ctx.modifierItemsMap(),
                ctx.userLocale()
        ));
        return newItem;
    }

    private OrderedItem createRegularOrderedItem(CreateNewOrderedItemContext ctx) {
        BigDecimal basePriceTotal = ctx.discountResult().getOriginalPrice();
        BigDecimal basePricePerUnit = basePriceTotal.divide(
                BigDecimal.valueOf(ctx.itemRequest().getQuantity()), 2, RoundingMode.HALF_UP);

        boolean hasRegularDiscount = ctx.discountResult().getAppliedDiscount() != null;
        boolean isBxgyBuyItemOnly = ctx.isBxgyBuyItem() && !ctx.isBxgyGetItem()
                && ctx.itemPrices() != null
                && ctx.itemPrices().containsKey(ctx.itemRequest().getItemId());

        BigDecimal discountedPricePerUnit = null;
        BigDecimal totalDiscountedItemAmount = null;
        if (hasRegularDiscount && ctx.itemRequest().getQuantity() > 0 && !isBxgyBuyItemOnly) {
            BigDecimal discountedPriceTotal = ctx.discountResult().getFinalPrice();
            discountedPricePerUnit = discountedPriceTotal.divide(
                    BigDecimal.valueOf(ctx.itemRequest().getQuantity()), 2, RoundingMode.HALF_UP);
            totalDiscountedItemAmount = CurrencyFormatter.formatAmount(discountedPriceTotal.add(ctx.totalModifierPrice()), ctx.currency());
        }

        BigDecimal totalItemAmount = CurrencyFormatter.formatAmount(basePriceTotal.add(ctx.totalModifierPrice()), ctx.currency());

        OrderedItem.OrderedItemBuilder itemBuilder = baseOrderedItemBuilder(ctx, basePricePerUnit);

        applyBxgyFieldsIfPresent(itemBuilder, ctx.bxgyInfo());

        OrderedItem newItem = orderedItemRepository.save(itemBuilder.build());
        applyDiscountFieldsToPersistedItem(newItem, hasRegularDiscount, isBxgyBuyItemOnly, discountedPricePerUnit, totalDiscountedItemAmount, totalItemAmount);
        saveModifiersAndAssignKds(new SaveModifiersContext(
                newItem,
                ctx.itemRequest(),
                ctx.order(),
                ctx.updatedBy(),
                ctx.restaurantId(),
                ctx.modifierGroupsMap(),
                ctx.modifierItemsMap(),
                ctx.userLocale()
        ));
        return newItem;
    }

    private int calculatePaidQuantity(int itemQuantity, Integer freeQuantity) {
        int freeQty = freeQuantity != null ? freeQuantity : 0;
        freeQty = Math.min(freeQty, itemQuantity);
        return Math.max(0, itemQuantity - freeQty);
    }

    private void applyBxgyFieldsIfPresent(OrderedItem.OrderedItemBuilder itemBuilder,
                                         com.gulfnet.shared_library.model.response.dto.BxgyItemInfo bxgyInfo) {
        if (bxgyInfo == null) {
            return;
        }
        itemBuilder.bxgyRole(bxgyInfo.getBxgyRole())
                .discountApplicationId(bxgyInfo.getDiscountApplicationId())
                .discountId(bxgyInfo.getDiscountId())
                .freeQuantity(bxgyInfo.getFreeQuantity());
    }

    private void applyDiscountFieldsToPersistedItem(OrderedItem newItem,
                                                    boolean hasRegularDiscount,
                                                    boolean isBxgyBuyItemOnly,
                                                    BigDecimal discountedPricePerUnit,
                                                    BigDecimal totalDiscountedItemAmount,
                                                    BigDecimal totalItemAmount) {
        if (hasRegularDiscount && !isBxgyBuyItemOnly) {
            newItem.setDiscountedPrice(discountedPricePerUnit);
            newItem.setTotalDiscountedItemAmount(totalDiscountedItemAmount);
        } else {
            newItem.setDiscountedPrice(null);
            newItem.setTotalDiscountedItemAmount(null);
        }
        newItem.setTotalItemAmount(totalItemAmount);
        orderedItemRepository.save(newItem);
    }

    private Item resolveItemForOrderedItem(OrderedItemRequest itemRequest,
                                          Map<UUID, Item> itemsMap,
                                          Locale userLocale) {
        // Items in the map are loaded fresh from database at the start of the request
        UUID itemId = itemRequest.getItemId();
        if (itemsMap != null) {
            Item fromMap = itemsMap.get(itemId);
            if (fromMap != null) {
                return fromMap;
            }
        }
        return itemRepository.findById(itemId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        messageUtil.getMessage("item.name.not.found", userLocale)));
    }

    private com.gulfnet.shared_library.model.response.dto.BxgyItemInfo resolveBxgyInfoForRequest(
            OrderedItemRequest itemRequest,
            Map<String, com.gulfnet.shared_library.model.response.dto.BxgyItemInfo> bxgyInfoByRequest) {
        if (bxgyInfoByRequest == null) {
            return null;
        }
        boolean isBuyItem = Boolean.TRUE.equals(itemRequest.getIsBuyItem());
        boolean isGetItem = Boolean.TRUE.equals(itemRequest.getIsGetItem());
        String requestKey = buildBxgyRequestKey(itemRequest.getItemId(), itemRequest.getQuantity(), isBuyItem, isGetItem);
        com.gulfnet.shared_library.model.response.dto.BxgyItemInfo bxgyInfo = bxgyInfoByRequest.get(requestKey);
        if (bxgyInfo != null) {
            log.info("Found BXGY info for item request - RequestKey: {}, DiscountApplicationId: {}, DiscountId: {}, BxgyRole: {}, FreeQuantity: {}",
                    requestKey, bxgyInfo.getDiscountApplicationId(), bxgyInfo.getDiscountId(), bxgyInfo.getBxgyRole(), bxgyInfo.getFreeQuantity());
        }
        return bxgyInfo;
    }

    private String buildBxgyRequestKey(UUID itemId, int quantity, boolean isBuyItem, boolean isGetItem) {
        return String.format("%s:%d:%s:%s", itemId, quantity, isBuyItem, isGetItem);
    }

    private BigDecimal resolveBxgyCalculatedItemPrice(OrderedItemRequest itemRequest,
                                                     Map<UUID, BigDecimal> itemPrices,
                                                     Map<UUID, BigDecimal> getItemPrices,
                                                     boolean isBxgyBuyItem,
                                                     boolean isBxgyGetItem,
                                                     boolean isBothBuyAndGet) {
        // Get the calculated price for this item from BXGY calculation (same as create order).
        // Use the request flags (isBuyItem, isGetItem) to determine which map to check.
        BigDecimal itemPrice = null;

        if (isBxgyGetItem && getItemPrices != null) {
            // For get items, check getItemPrices map first.
            itemPrice = getItemPrices.get(itemRequest.getItemId());
            if (itemPrice != null) {
                log.debug("Get item {} found in getItemPrices map: {}", itemRequest.getItemId(), itemPrice);
            }
        }

        // For buy items (that are not also get items), check itemPrices.
        if (itemPrice == null && isBxgyBuyItem && !isBxgyGetItem && itemPrices != null) {
            itemPrice = itemPrices.get(itemRequest.getItemId());
            if (itemPrice != null) {
                log.debug("Buy item {} found in itemPrices map: {}", itemRequest.getItemId(), itemPrice);
            }
        }

        // For items that are both buy and get, buy price is in itemPrices, get price is in getItemPrices.
        // For response building, if it's marked as get item, use getItemPrices.
        if (itemPrice == null && isBothBuyAndGet && getItemPrices != null) {
            itemPrice = getItemPrices.get(itemRequest.getItemId());
            if (itemPrice != null) {
                log.debug("Item {} is both buy and get - using get item price from getItemPrices map: {}",
                        itemRequest.getItemId(), itemPrice);
            }
        }

        // For non-BXGY items, check itemPrices.
        if (itemPrice == null && !isBxgyBuyItem && !isBxgyGetItem && itemPrices != null) {
            itemPrice = itemPrices.get(itemRequest.getItemId());
        }

        return itemPrice;
    }

    // ==================== PRIVATE HELPER METHODS ====================
    
    /**
     * Check if a user is a cashier
     * @param user The user to check
     * @return true if the user is a cashier, false otherwise
     */
    private boolean isCashier(User user) {
        if (user == null || user.getRoleId() == null) {
            return false;
        }
        try {
            Role role = roleRepository.findById(user.getRoleId()).orElse(null);
            return role != null && "CASHIER".equals(role.getName());
        } catch (Exception e) {
            log.debug("Failed to check if user is cashier: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Save ordered item modifiers and assign item to KDS if needed.
     * Shared between BXGY and regular item flows.
     */
    private record SaveModifiersContext(
            OrderedItem newItem,
            OrderedItemRequest itemRequest,
            Order order,
            User updatedBy,
            UUID restaurantId,
            Map<UUID, ModifierGroup> modifierGroupsMap,
            Map<UUID, ModifierItem> modifierItemsMap,
            Locale userLocale
    ) {}

    private void saveModifiersAndAssignKds(SaveModifiersContext ctx) {

        // Save modifiers for the new item
        if (ctx.itemRequest().getOrderedItemModifiers() != null) {
            for (OrderedItemModifierRequest modifierRequest : ctx.itemRequest().getOrderedItemModifiers()) {
                // Get modifier group from batch-loaded map or query fresh if not provided
                ModifierGroup modifierGroup;
                if (ctx.modifierGroupsMap() != null && ctx.modifierGroupsMap().containsKey(modifierRequest.getModifierGroupId())) {
                    modifierGroup = ctx.modifierGroupsMap().get(modifierRequest.getModifierGroupId());
                } else {
                    // Fallback: query individually if map not provided (for other endpoints)
                    modifierGroup = modifierGroupRepository.findById(modifierRequest.getModifierGroupId())
                            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                                    messageUtil.getMessage("modifier.group.not.found", ctx.userLocale())));
                }

                for (UUID modifierItemId : modifierRequest.getModifierItemIds()) {
                    // Get modifier item from batch-loaded map or query fresh if not provided
                    ModifierItem modifierItem = getModifierItem(modifierItemId, ctx.modifierItemsMap(), ctx.userLocale());

                    // Null-safe price handling: treat null prices as zero
                    BigDecimal modifierItemPrice = modifierItem.getPrice() != null
                            ? modifierItem.getPrice()
                            : BigDecimal.ZERO;

                    OrderedItemModifier orderedItemModifier = OrderedItemModifier.builder()
                            .orderedItem(ctx.newItem())
                            .modifierGroup(modifierGroup)
                            .modifierItem(modifierItem)
                            .price(modifierItemPrice)
                            .createdAt(OffsetDateTime.now(ZoneOffset.UTC))
                            .createdBy(ctx.order().getCreatedBy())
                            .updatedAt(OffsetDateTime.now(ZoneOffset.UTC))
                            .updatedBy(ctx.updatedBy())
                            .build();

                    orderedItemModifierRepository.save(orderedItemModifier);
                }
            }
        }

        // Assign item to KDS stations if status is PUSHED (cashier added item)
        // This ensures items added by cashiers appear on KDS app
        if (ctx.newItem().getItemStatus() == ItemStatus.PUSHED && ctx.restaurantId() != null) {
            assignItemToKds(ctx.newItem(), ctx.restaurantId());
        }
    }

    /**
     * Creates ordered item modifiers from combo item modifier requests.
     * Used when creating ordered items from combos with selected modifiers.
     *
     * @param orderedItem      the ordered item to attach modifiers to
     * @param selectedModifiers list of combo item modifier requests to create modifiers from
     * @param createdBy        user creating the modifiers
     * @param userLocale       locale for localized error messages
     * @throws ResponseStatusException if modifier item is not found
     */
    @Override
    public void createSelectedOrderedItemModifiers(
            OrderedItem orderedItem,
            List<OrderedComboItemModifierRequest> selectedModifiers,
            User createdBy,
            Locale userLocale) {
        if (selectedModifiers == null) {
            return;
        }
        
        for (OrderedComboItemModifierRequest modifierRequest : selectedModifiers) {
            for (UUID modifierItemId : modifierRequest.getModifierItemIds()) {
                ModifierItem modifierItem = modifierItemRepository.findById(modifierItemId)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        messageUtil.getMessage("modifier.item.name.not.found", userLocale)));
                
                // Null-safe price handling: treat null prices as zero
                BigDecimal modifierItemPrice = modifierItem.getPrice() != null ? modifierItem.getPrice() : BigDecimal.ZERO;
                OrderedItemModifier orderedItemModifier = OrderedItemModifier.builder()
                    .orderedItem(orderedItem)
                    .modifierGroup(modifierItem.getModifierGroup())
                    .modifierItem(modifierItem)
                    .price(modifierItemPrice)
                    .build();
                
                orderedItemModifierRepository.save(orderedItemModifier);
            }
        }
    }

    /**
     * Builds an ordered item response DTO from an ordered item entity.
     * Convenience method that delegates to the overloaded method with null original request and menu ID.
     *
     * @param orderedItem  the ordered item entity to convert
     * @param restaurantId the restaurant ID for availability checking
     * @param userLocale   locale for localized names
     * @return {@link OrderedItemResponse} with item details, prices, modifiers, and availability
     */
    @Override
    public OrderedItemResponse buildOrderedItemResponse(OrderedItem orderedItem, UUID restaurantId, Locale userLocale) {
        return buildOrderedItemResponse(orderedItem, null, restaurantId, null, userLocale);
    }

    /**
     * Builds an ordered item response DTO from an ordered item entity with optional original request.
     * Includes localized names, prices, modifiers, availability, and cancellation request status.
     *
     * @param orderedItem     the ordered item entity to convert
     * @param originalRequest optional original request for additional context
     * @param restaurantId    the restaurant ID for availability checking
     * @param menuId          optional menu ID for availability checking
     * @param userLocale      locale for localized names
     * @return {@link OrderedItemResponse} with complete item details
     */
    @Override
    public OrderedItemResponse buildOrderedItemResponse(
            OrderedItem orderedItem,
            OrderedItemRequest originalRequest,
            UUID restaurantId,
            UUID menuId,
            Locale userLocale) {
        return buildOrderedItemResponse(orderedItem, originalRequest, restaurantId, menuId, userLocale, null);
    }

    @Override
    public OrderedItemResponse buildOrderedItemResponse(
            OrderedItem orderedItem,
            OrderedItemRequest originalRequest,
            UUID restaurantId,
            UUID menuId,
            Locale userLocale,
            List<OrderedItemModifier> preloadedModifiers) {
        return buildOrderedItemResponse(
                orderedItem,
                originalRequest,
                restaurantId,
                menuId,
                userLocale,
                preloadedModifiers,
                null,
                null);
    }

    @Override
    public OrderedItemResponse buildOrderedItemResponse(
            OrderedItem orderedItem,
            OrderedItemRequest originalRequest,
            UUID restaurantId,
            UUID menuId,
            Locale userLocale,
            List<OrderedItemModifier> preloadedModifiers,
            Map<String, String> presignedUrlCache,
            Boolean precomputedAvailability) {
        // Load modifiers separately (or reuse batch preloaded modifiers)
        List<OrderedItemModifier> modifiers = preloadedModifiers != null
                ? preloadedModifiers
                : orderedItemModifierRepository.findByOrderedItemId(orderedItem.getId());
        
        // Group modifiers by modifier group to avoid repeating the same group
        Map<UUID, List<OrderedItemModifier>> modifiersByGroup = modifiers.stream()
            .collect(Collectors.groupingBy(m -> m.getModifierGroup().getId()));

        List<OrderedItemModifierResponse> modifierResponses = modifiersByGroup.entrySet().stream()
            .map(entry -> {
                UUID groupId = entry.getKey();
                List<OrderedItemModifier> groupModifiers = entry.getValue();

                // All in the group share the same group
                ModifierGroup group = groupModifiers.get(0).getModifierGroup();
                String groupName = group.getTranslations().stream()
                        .filter(t -> t.getLanguageCode().equals(userLocale.getLanguage()))
                        .findFirst()
                        .map(ModifierGroupTranslation::getName)
                        .orElse(group.getTranslations().isEmpty() ? "Modifier Group" : group.getTranslations().get(0).getName());

                List<ModifierItemResponse> modifierItemResponses = groupModifiers.stream()
                    .map(mod -> ModifierItemResponse.builder()
                        .id(mod.getId())
                        .modifierItemId(mod.getModifierItem().getId())
                        .modifierItemName(mod.getModifierItem().getTranslations().stream()
                                .filter(t -> t.getLanguageCode().equals(userLocale.getLanguage()))
                                .findFirst()
                                .map(ModifierItemTranslation::getName)
                                .orElse(mod.getModifierItem().getTranslations().isEmpty() ? 
                                    "Modifier Item" : mod.getModifierItem().getTranslations().get(0).getName()))
                        .price(CurrencyFormatter.formatAmount(mod.getPrice(), restaurantChainConfigProperties.getChain().getCurrency()))
                        .build())
                    .collect(Collectors.toList());

                return OrderedItemModifierResponse.builder()
                    .modifierGroupId(groupId)
                    .modifierGroupName(groupName)
                    .modifierItems(modifierItemResponses)
                    .build();
            })
            .collect(Collectors.toList());

        // Use unified price calculation method for existing orders
        ItemPriceCalculationResult priceResult = calculateItemPriceForExistingOrder(orderedItem, modifiers);
        
        // Check item availability (menuId not available for existing orders, so check all menus)
        Boolean isAvailable = precomputedAvailability != null
                ? precomputedAvailability
                : checkItemAvailabilityForOrderedItem(orderedItem.getItem(), restaurantId, menuId);
        
        // Determine reason: use cancellationComments if request is DECLINED, otherwise use reason
        String reasonValue = orderedItem.getReason();
        if (orderedItem.getCancellationRequestStatus() == RequestStatus.DECLINED && 
            orderedItem.getCancellationComments() != null && !orderedItem.getCancellationComments().isEmpty()) {
            reasonValue = orderedItem.getCancellationComments();
        }
        
        // BXGY information (discountIds, isBuyItem, isGetItem, freeQuantity) is only included in calculate API response
        // Not included in regular order responses (getOrderBySessionId, getOrderByTableId, createOrder, updateOrder, etc.)
        
        String rawImageUrl = orderedItem.getItem().getImageUrl();
        String resolvedImageUrl = null;
        if (rawImageUrl != null && !rawImageUrl.isEmpty()) {
            if (presignedUrlCache != null) {
                resolvedImageUrl = presignedUrlCache.computeIfAbsent(rawImageUrl, awsService::getPreSignedUrl);
            } else {
                resolvedImageUrl = awsService.getPreSignedUrl(rawImageUrl);
            }
        }

        OrderedItemResponse.OrderedItemResponseBuilder responseBuilder = OrderedItemResponse.builder()
            .id(orderedItem.getId())
            .itemId(orderedItem.getItem().getId())
            .alcoholType(orderedItem.getAlcoholType() != null ? orderedItem.getAlcoholType() : orderedItem.getItem().getAlcoholType())
            .itemName(orderedItem.getItem().getTranslations().stream()
                    .filter(t -> t.getLanguageCode().equals(userLocale.getLanguage()))
                    .findFirst()
                    .map(ItemTranslation::getName)
                    .orElse(orderedItem.getItem().getTranslations().isEmpty() ? 
                        "Item" : orderedItem.getItem().getTranslations().get(0).getName()))
            .imageUrl(resolvedImageUrl)
            .quantity(orderedItem.getQuantity())
            // Needed for refund eligibility of cancelled items in session/table responses.
            .includedInPayment(orderedItem.getIncludedInPayment() != null ? orderedItem.getIncludedInPayment() : Boolean.FALSE)
            .price(priceResult.basePricePerUnit()) // Base price per unit
            .discountedPrice(priceResult.discountedPricePerUnit()) // Discounted price per unit
            .itemStatus(orderedItem.getItemStatus())
            .notes(orderedItem.getNotes())
            .reason(reasonValue)
            .requestStatus(orderedItem.getCancellationRequestStatus() != null ? orderedItem.getCancellationRequestStatus() : RequestStatus.NONE)
            .totalItemAmount(priceResult.totalAmountWithoutDiscount()) // Total without discount
            .totalDiscountedItemAmount(priceResult.totalAmountWithDiscount()) // Total with discount
            .isAvailable(isAvailable)
            .orderedItemModifiers(modifierResponses);
        
        // Add BXGY fields if not null
        if (orderedItem.getBxgyRole() != null) {
            responseBuilder.bxgyRole(orderedItem.getBxgyRole());
        }
        if (orderedItem.getDiscountApplicationId() != null) {
            responseBuilder.discountApplicationId(orderedItem.getDiscountApplicationId());
        }
        if (orderedItem.getDiscountId() != null) {
            responseBuilder.discountId(orderedItem.getDiscountId());
        }
        if (orderedItem.getFreeQuantity() != null) {
            responseBuilder.freeQuantity(orderedItem.getFreeQuantity());
        }
        
        return responseBuilder.build();
    }

    /**
     * Builds an ordered item response for price calculation API.
     * Calculates prices including discounts, BXGY discounts, modifiers, and price overrides.
     * Includes BXGY information (discountIds, isBuyItem, isGetItem, freeQuantity) in the response.
     *
     * @param itemRequest            the item request to calculate prices for
     * @param itemPrices             map of item IDs to calculated prices
     * @param getItemPrices          map of item IDs to BXGY get item prices
     * @param paidQuantitiesByRequest map of request identifiers to paid quantities for BXGY
     * @param menuId                 the menu ID containing the item
     * @param restaurantId           the restaurant ID
     * @param activeOverrideIndex    active price override index helper
     * @param userLocale             locale for localized names
     * @return {@link OrderedItemResponse} with calculated prices and BXGY information
     * @throws ResponseStatusException if item is not found
     */
    @Override
    public OrderedItemResponse buildCalculationItemResponse(
            OrderedItemRequest itemRequest,
            Map<UUID, BigDecimal> itemPrices,
            Map<UUID, BigDecimal> getItemPrices,
            Map<String, Integer> paidQuantitiesByRequest,
            UUID menuId,
            UUID restaurantId,
            PriceOverrideHelper.ActiveOverrideIndex activeOverrideIndex,
            Locale userLocale) {
        
        Item item = itemRepository.findById(itemRequest.getItemId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        messageUtil.getMessage("item.name.not.found", userLocale)));
        
        boolean isBxgyGetItem = Boolean.TRUE.equals(itemRequest.getIsGetItem());
        boolean isBxgyBuyItem = Boolean.TRUE.equals(itemRequest.getIsBuyItem());
        boolean isBothBuyAndGet = isBxgyGetItem && isBxgyBuyItem;

        BigDecimal itemPrice = resolveBxgyCalculatedItemPrice(
                itemRequest, itemPrices, getItemPrices, isBxgyBuyItem, isBxgyGetItem, isBothBuyAndGet);

        boolean hasBxgyDiscountInRequest = hasBxgyDiscountInRequest(itemRequest);
        
        com.gulfnet.shared_library.model.response.dto.DiscountCalculationResult discountResult =
                calculateDiscountResultForCalculation(menuId, itemRequest, restaurantId, activeOverrideIndex);
        
        boolean isBxgyBuyItemOnly = isBxgyBuyItemOnly(itemRequest, itemPrices, isBxgyBuyItem, isBxgyGetItem, hasBxgyDiscountInRequest);
        itemPrice = finalizeItemPriceForCalculation(itemPrice, itemRequest, discountResult, isBxgyGetItem, isBxgyBuyItemOnly);
        
        String currency = restaurantChainConfigProperties.getChain().getCurrency();
        BigDecimal overriddenBasePrice = calculateOverriddenBasePricePerUnit(discountResult.getOriginalPrice(), itemRequest.getQuantity(), currency);
        
        log.info("BUILD CALCULATION ITEM RESPONSE - Item: {}, Quantity: {}, IsGetItem: {}", 
            itemRequest.getItemId(), itemRequest.getQuantity(), isBxgyGetItem);
        log.info("  - Discount Result Original Price (total): {}", discountResult.getOriginalPrice());
        log.info("  - Discount Result Final Price (total): {}", discountResult.getFinalPrice());
        log.info("  - Overridden Base Price Per Unit: {}", overriddenBasePrice);
        log.info("  - Item Price (from itemPrices map): {} {}", itemPrice, isBxgyGetItem ? "(BXGY Get Item - may include paid portion)" : "");
        
        boolean hasDiscount = resolveHasDiscountForCalculation(discountResult, itemRequest.getItemId(), isBxgyBuyItemOnly, hasBxgyDiscountInRequest, isBxgyBuyItem, isBxgyGetItem);
        
        // Use unified price calculation method with overridden base price and discount info
        // For get items, pass itemPrice (paid portion) so the calculation reflects paid items correctly
        // Pass itemPrices map to check for BXGY buy items in calculateItemPriceWithModifiers
        ItemPriceCalculationResult priceResult = orderPricingService.calculateItemPriceWithModifiers(
            item, itemRequest, itemPrice, overriddenBasePrice, hasDiscount, isBxgyGetItem, paidQuantitiesByRequest, userLocale, discountResult, itemPrices);
        
        log.info("FINAL PRICE RESULT - Item: {}", itemRequest.getItemId());
        log.info("  - price (basePricePerUnit): {}", priceResult.basePricePerUnit());
        log.info("  - discountedPrice (discountedPricePerUnit): {}", priceResult.discountedPricePerUnit());
        log.info("  - totalItemAmount: {}", priceResult.totalAmountWithoutDiscount());
        log.info("  - totalDiscountedItemAmount: {}", priceResult.totalAmountWithDiscount());
        
        // Build modifier responses with localized names
        List<OrderedItemModifierResponse> modifierResponses = buildModifierResponses(
            itemRequest.getOrderedItemModifiers(), userLocale);
        
        // Check item availability for the specific menu
        Boolean isAvailable = checkItemAvailabilityForOrderedItem(item, restaurantId, menuId);
        
        // Ensure BXGY discount IDs are included when isBuyItem or isGetItem is true
        List<UUID> discountIds = orderPricingService.ensureBxgyDiscountIdsIncluded(itemRequest, menuId);
        
        return buildCalculationOrderedItemResponse(item, itemRequest, priceResult, modifierResponses, isAvailable, discountIds, userLocale);
    }

    private boolean hasBxgyDiscountInRequest(OrderedItemRequest itemRequest) {
        if (itemRequest.getDiscountIds() == null || itemRequest.getDiscountIds().isEmpty()) {
            return false;
        }
        for (UUID discountId : itemRequest.getDiscountIds()) {
            Optional<Discount> discountOpt = discountRepository.findById(discountId);
            if (discountOpt.isPresent() && discountOpt.get().getDiscountType() == DiscountType.BXGY) {
                log.info("BXGY discount {} found in request for item {}", discountId, itemRequest.getItemId());
                return true;
            }
        }
        return false;
    }

    private com.gulfnet.shared_library.model.response.dto.DiscountCalculationResult calculateDiscountResultForCalculation(
            UUID menuId,
            OrderedItemRequest itemRequest,
            UUID restaurantId,
            PriceOverrideHelper.ActiveOverrideIndex activeOverrideIndex) {
        if (restaurantId != null && activeOverrideIndex != null) {
            return orderPricingService.calculateItemPriceWithOverride(
                    menuId, itemRequest.getItemId(), itemRequest.getQuantity(),
                    restaurantId, activeOverrideIndex);
        }
        return orderPricingService.calculateItemPrice(menuId, itemRequest.getItemId(), itemRequest.getQuantity());
    }

    private boolean isBxgyBuyItemOnly(OrderedItemRequest itemRequest,
                                      Map<UUID, BigDecimal> itemPrices,
                                      boolean isBxgyBuyItem,
                                      boolean isBxgyGetItem,
                                      boolean hasBxgyDiscountInRequest) {
        return (isBxgyBuyItem || hasBxgyDiscountInRequest)
                && !isBxgyGetItem
                && itemPrices != null
                && itemPrices.containsKey(itemRequest.getItemId());
    }

    private BigDecimal finalizeItemPriceForCalculation(BigDecimal resolvedFromBxgyMaps,
                                                       OrderedItemRequest itemRequest,
                                                       com.gulfnet.shared_library.model.response.dto.DiscountCalculationResult discountResult,
                                                       boolean isBxgyGetItem,
                                                       boolean isBxgyBuyItemOnly) {
        if (isBxgyBuyItemOnly) {
            BigDecimal basePrice = discountResult.getOriginalPrice();
            log.info("BXGY Buy Item - Using base price (originalPrice) to ensure no item/category discounts: {} for item: {}",
                    basePrice, itemRequest.getItemId());
            return basePrice;
        }
        if (isBxgyGetItem && resolvedFromBxgyMaps == null) {
            log.debug("BXGY Get Item - price not found in map, setting to 0 for item: {}", itemRequest.getItemId());
            return BigDecimal.ZERO;
        }
        if (resolvedFromBxgyMaps == null) {
            return discountResult.getFinalPrice();
        }
        if (isBxgyGetItem) {
            log.info("BXGY Get Item detected - using price from map: {} for item: {}", resolvedFromBxgyMaps, itemRequest.getItemId());
        }
        return resolvedFromBxgyMaps;
    }

    private BigDecimal calculateOverriddenBasePricePerUnit(BigDecimal originalPriceTotal, int quantity, String currency) {
        BigDecimal overriddenBasePrice = originalPriceTotal
                .divide(BigDecimal.valueOf(quantity), 10, java.math.RoundingMode.HALF_UP);
        return CurrencyFormatter.formatAmount(overriddenBasePrice, currency);
    }

    private boolean resolveHasDiscountForCalculation(
            com.gulfnet.shared_library.model.response.dto.DiscountCalculationResult discountResult,
            UUID itemId,
            boolean isBxgyBuyItemOnly,
            boolean hasBxgyDiscountInRequest,
            boolean isBxgyBuyItem,
            boolean isBxgyGetItem) {
        boolean hasDiscount = discountResult.getAppliedDiscount() != null;
        if (isBxgyBuyItemOnly || (hasBxgyDiscountInRequest && isBxgyBuyItem && !isBxgyGetItem)) {
            log.info("BXGY Buy Item - Setting hasDiscount to false to ensure discountedPrice and totalDiscountedItemAmount are null for item: {}", itemId);
            return false;
        }
        return hasDiscount;
    }

    private OrderedItemResponse buildCalculationOrderedItemResponse(
            Item item,
            OrderedItemRequest itemRequest,
            ItemPriceCalculationResult priceResult,
            List<OrderedItemModifierResponse> modifierResponses,
            Boolean isAvailable,
            List<UUID> discountIds,
            Locale userLocale) {
        String itemName = item.getTranslations().stream()
                .filter(t -> t.getLanguageCode().equals(userLocale.getLanguage()))
                .findFirst()
                .map(ItemTranslation::getName)
                .orElse(item.getTranslations().isEmpty() ? "Item" : item.getTranslations().get(0).getName());

        String imageUrl = item.getImageUrl() != null && !item.getImageUrl().isEmpty()
                ? awsService.getPreSignedUrl(item.getImageUrl())
                : null;

        return OrderedItemResponse.builder()
                .id(null)
                .itemId(item.getId())
                .alcoholType(item.getAlcoholType())
                .itemName(itemName)
                .imageUrl(imageUrl)
                .quantity(itemRequest.getQuantity())
                .price(priceResult.basePricePerUnit())
                .discountedPrice(priceResult.discountedPricePerUnit())
                .itemStatus(ItemStatus.ON_HOLD)
                .notes(itemRequest.getNotes())
                .reason(null)
                .requestStatus(RequestStatus.NONE)
                .totalItemAmount(priceResult.totalAmountWithoutDiscount())
                .totalDiscountedItemAmount(priceResult.totalAmountWithDiscount())
                .isAvailable(isAvailable)
                .discountIds(discountIds.isEmpty() ? null : discountIds)
                .isBuyItem(Boolean.TRUE.equals(itemRequest.getIsBuyItem()))
                .isGetItem(Boolean.TRUE.equals(itemRequest.getIsGetItem()))
                .freeQuantity(itemRequest.getFreeQuantity())
                .orderedItemModifiers(modifierResponses)
                .build();
    }

    /**
     * Assigns an ordered item to KDS (Kitchen Display System) stations based on item category.
     * Finds KDS stations that have the item's category assigned and creates assignments.
     * Handles both regular items and combo items.
     *
     * @param orderedItem the ordered item to assign to KDS stations
     * @param restaurantId the restaurant ID to find KDS stations for
     */
    @Override
    public void assignItemToKds(OrderedItem orderedItem, UUID restaurantId) {
        if (!canAssignItemToKds(orderedItem, restaurantId)) {
            return;
        }

        try {
            Item item = Objects.requireNonNull(orderedItem.getItem());
            UUID itemId = item.getId();
            String itemType = orderedItem.getOrderedCombo() != null ? "combo item" : "regular item";
            String comboInfo = orderedItem.getOrderedCombo() != null ? " (from combo " + orderedItem.getOrderedCombo().getId() + ")" : "";

            List<CategoryItemMapping> categoryMappings = categoryItemMappingRepository.findByItem_Id(itemId);
            if (categoryMappings == null || categoryMappings.isEmpty()) {
                log.warn("{} {} (Item ID: {}) has no category mappings - will not appear on any KDS",
                        itemType, orderedItem.getId(), itemId);
                return;
            }

            List<Kds> restaurantKdss = loadRestaurantNonDefaultKdss(restaurantId);
            if (restaurantKdss.isEmpty()) {
                log.warn("No non-default KDS stations found for restaurant {} - {} {} will not appear on any KDS",
                        restaurantId, itemType, itemId);
                return;
            }

            List<UUID> assignedKdsIds = resolveAssignedKdsIds(categoryMappings, restaurantKdss);
            logKdsAssignmentResult(itemType, orderedItem, itemId, comboInfo, assignedKdsIds);
        } catch (Exception e) {
            log.error("Error assigning item {} to KDS: {}", orderedItem.getId(), e.getMessage(), e);
        }
    }

    private boolean canAssignItemToKds(OrderedItem orderedItem, UUID restaurantId) {
        if (orderedItem == null || orderedItem.getItem() == null) {
            log.warn("Cannot assign item to KDS: orderedItem or item is null");
            return false;
        }
        if (restaurantId == null) {
            log.warn("Cannot assign item to KDS: restaurantId is null");
            return false;
        }
        return true;
    }

    private List<Kds> loadRestaurantNonDefaultKdss(UUID restaurantId) {
        // Default KDS is created with all categories and all employees - it should not be used for item assignment routing.
        return kdsRepository.findAll().stream()
                .filter(kds -> restaurantId.equals(kds.getRestaurantId())
                        && Boolean.FALSE.equals(kds.getIsDeleted())
                        && Boolean.FALSE.equals(kds.getIsDefault()))
                .collect(Collectors.toList());
    }

    private List<UUID> resolveAssignedKdsIds(List<CategoryItemMapping> categoryMappings, List<Kds> restaurantKdss) {
        List<UUID> assignedKdsIds = new ArrayList<>();
        Set<UUID> processedCategoryIds = new HashSet<>();

        for (CategoryItemMapping categoryMapping : categoryMappings) {
            UUID mainCategoryId = resolveMainCategoryId(categoryMapping);
            boolean shouldSkip = mainCategoryId == null || processedCategoryIds.contains(mainCategoryId);
            if (shouldSkip) {
                continue;
            }
            processedCategoryIds.add(mainCategoryId);

            for (Kds kds : restaurantKdss) {
                boolean kdsHasCategory = isKdsAssignedToMainCategory(kds, mainCategoryId);
                if (!kdsHasCategory) {
                    continue;
                }
                if (!assignedKdsIds.contains(kds.getId())) {
                    assignedKdsIds.add(kds.getId());
                }
            }
        }

        return assignedKdsIds;
    }

    private void logKdsAssignmentResult(String itemType,
                                        OrderedItem orderedItem,
                                        UUID itemId,
                                        String comboInfo,
                                        List<UUID> assignedKdsIds) {
        if (assignedKdsIds.isEmpty()) {
            log.warn("{} {} (Item ID: {}{}) not assigned to any KDS - no matching categories found",
                    itemType, orderedItem.getId(), itemId, comboInfo);
            return;
        }

        log.info("{} {} (Item ID: {}{}) successfully assigned to {} KDS station(s): {}",
                itemType, orderedItem.getId(), itemId, comboInfo, assignedKdsIds.size(), assignedKdsIds);
    }

    private UUID resolveMainCategoryId(CategoryItemMapping categoryMapping) {
        if (categoryMapping == null) {
            return null;
        }
        MenuCategoryMapping menuCategoryMapping = categoryMapping.getMenuCategoryMapping();
        if (menuCategoryMapping == null) {
            return null;
        }
        Category category = menuCategoryMapping.getCategory();
        if (category == null) {
            return null;
        }
        Category parent = category.getParentCategory();
        return parent != null ? parent.getId() : category.getId();
    }

    private boolean isKdsAssignedToMainCategory(Kds kds, UUID mainCategoryId) {
        if (kds == null || kds.getId() == null || mainCategoryId == null) {
            return false;
        }
        List<CategoryKds> categoryKdsMappings = categoryKdsRepository.findByKdsId(kds.getId());
        if (categoryKdsMappings == null || categoryKdsMappings.isEmpty()) {
            return false;
        }
        for (CategoryKds categoryKds : categoryKdsMappings) {
            UUID kdsMainCategoryId = resolveMainCategoryIdFromKdsMapping(categoryKds);
            if (mainCategoryId.equals(kdsMainCategoryId)) {
                return true;
            }
        }
        return false;
    }

    private UUID resolveMainCategoryIdFromKdsMapping(CategoryKds categoryKds) {
        if (categoryKds == null) {
            return null;
        }
        MenuCategoryMapping kdsMenuCategoryMapping = categoryKds.getMenuCategoryMapping();
        if (kdsMenuCategoryMapping == null) {
            return null;
        }
        Category kdsCategory = kdsMenuCategoryMapping.getCategory();
        if (kdsCategory == null) {
            return null;
        }
        Category parent = kdsCategory.getParentCategory();
        return parent != null ? parent.getId() : kdsCategory.getId();
    }

    // ==================== AVAILABILITY CALCULATION ====================

    /**
     * Calculates item availability for a specific restaurant based on category item mapping.
     * Checks restaurant-specific availability overrides, defaulting to available if no override exists.
     *
     * @param categoryItemMapping the category item mapping to check availability for
     * @param restaurantId       the restaurant ID to check availability for
     * @return {@code true} if item is available, {@code false} if explicitly unavailable, defaults to {@code true} on error
     */
    @Override
    public Boolean calculateItemAvailability(CategoryItemMapping categoryItemMapping, UUID restaurantId) {
        if (categoryItemMapping == null || categoryItemMapping.getId() == null || restaurantId == null) {
            return true; // Default to available if we can't determine
        }
        
        try {
            Optional<RestaurantItemAvailability> availabilityOpt = restaurantItemAvailabilityRepository
                    .findByRestaurantIdAndCategoryItemMappingId(restaurantId, categoryItemMapping.getId());
            
            if (availabilityOpt.isPresent()) {
                RestaurantItemAvailability availability = availabilityOpt.get();
                return availability != null && Boolean.TRUE.equals(availability.getIsAvailable());
            } else {
                // If no availability record exists, consider item as available (fallback to default)
                return true;
            }
        } catch (Exception e) {
            log.warn("Error checking item availability for categoryItemMapping {} in restaurant {}: {}", 
                    categoryItemMapping.getId(), restaurantId, e.getMessage());
            return false;
        }
    }

    // ==================== HELPER METHODS ====================

    @Override
    public String applyReasonIfProvided(OrderedItem orderedItem, String reason) {
        String sanitizedReason = orderNotificationService.sanitizeReason(reason);
        if (sanitizedReason != null) {
            orderedItem.setReason(sanitizedReason);
        }
        return sanitizedReason;
    }

    // ==================== CANCELLATION REQUEST HANDLER ====================

    /**
     * Handles cancellation request for an ordered item.
     * Managers can cancel directly without creating a request, while other users create a cancellation request.
     * Recalculates order totals after cancellation and sends notifications.
     *
     * @param orderedItem     the ordered item to cancel or request cancellation for
     * @param payload         the cancellation request payload
     * @param authenticatedUser the user making the cancellation request
     * @param userLocale      locale for localized messages
     * @return {@link ResponseDto} containing the cancellation result and updated order information
     * @throws ResponseStatusException if order is not found or validation fails
     */
    @Override
    public ResponseDto<ItemStatusResponseWrapper> handleCancellationRequest(OrderedItem orderedItem, ItemStatusPayload payload, User authenticatedUser, Locale userLocale) {
        // If user is MANAGER, they should cancel directly, not create a request
        if (orderValidationService.isManager(authenticatedUser)) {
            // Manager can cancel directly - no request needed
            OrderRecalculationResult recalculationResult = orderRecalculationService.handleItemCancellation(orderedItem, authenticatedUser, authenticatedUser != null, userLocale);
            String responseMessage = messageUtil.getMessage("item.cancelled.directly", userLocale);
            
            if (recalculationResult.isDiscountRemoved()) {
                responseMessage += " " + recalculationResult.getDiscountMessage();
            }
            
            return ResponseDto.<ItemStatusResponseWrapper>builder()
                    .message(responseMessage)
                    .data(ItemStatusResponseWrapper.builder()
                            .itemStatus(ItemStatusPayload.builder()
                                    .orderedItemId(orderedItem.getId())
                                    .itemStatus(ItemStatus.CANCELED)
                                    .reason(orderedItem.getReason())
                                    .build())
                            .build())
                    .build();
        }
        
        String sanitizedReason = orderNotificationService.sanitizeReason(payload.getReason());
        applyReasonIfProvided(orderedItem, sanitizedReason);

        // Check if there's already a pending cancellation request - update it instead of throwing error
        boolean isUpdatingExistingRequest = orderedItem.getCancellationRequestStatus() == RequestStatus.OPEN;
        
        // Create or update cancellation request data
        try {
            ObjectMapper objectMapper = new ObjectMapper();
            ItemCancellationRequestDto requestDto = ItemCancellationRequestDto.builder()
                    .cancellationReason(sanitizedReason)
                    .requestedStatus(payload.getItemStatus()) // Store the requested status (CANCELED)
                    .build();
            String requestData = objectMapper.writeValueAsString(requestDto);
            
            orderedItem.setCancellationRequestStatus(RequestStatus.OPEN);
            orderedItem.setCancellationRequestData(requestData);
            orderedItem.setCancellationRequestedAt(OffsetDateTime.now(ZoneOffset.UTC));
            orderedItem.setCancellationRequestedBy(authenticatedUser);
            // Clear previous review information when creating or updating a request
            orderedItem.setCancellationComments(null);
            orderedItem.setCancellationReviewedAt(null);
            orderedItem.setCancellationReviewedBy(null);
            orderedItem.setUpdatedAt(OffsetDateTime.now(ZoneOffset.UTC));
            orderedItem.setUpdatedBy(authenticatedUser);
            
            orderedItemRepository.save(orderedItem);

            // Create audit trail for item cancellation request (only for new requests)
            if (!isUpdatingExistingRequest) {
                createItemCancellationRequestAuditTrailBestEffort(orderedItem, authenticatedUser, sanitizedReason);
            }

            // Notify managers about newly opened cancellation request (only for new requests, not updates)
            if (!isUpdatingExistingRequest) {
                notifyManagersCancellationRequestOpenedBestEffort(orderedItem, userLocale);
            }

            // Return appropriate message based on whether we're creating or updating
            String responseMessage = isUpdatingExistingRequest 
                    ? messageUtil.getMessage("item.cancellation.request.updated", userLocale)
                    : messageUtil.getMessage("item.cancellation.request.created", userLocale);

            return ResponseDto.<ItemStatusResponseWrapper>builder()
                    .message(responseMessage)
                    .data(ItemStatusResponseWrapper.builder()
                            .itemStatus(ItemStatusPayload.builder()
                                    .orderedItemId(orderedItem.getId())
                                    .itemStatus(orderedItem.getItemStatus())
                                    .reason(orderedItem.getReason())
                                    .build())
                            .build())
                    .build();
                    
        } catch (JsonProcessingException e) {
            log.error("Error creating cancellation request data: {}", e.getMessage());
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    messageUtil.getMessage("item.cancellation.request.error", userLocale));
        }
    }

    private void createItemCancellationRequestAuditTrailBestEffort(OrderedItem orderedItem,
                                                                   User authenticatedUser,
                                                                   String sanitizedReason) {
        try {
            Restaurant restaurant = orderedItem.getOrder() != null ? orderedItem.getOrder().getRestaurant() : null;
            String auditReason = sanitizedReason != null && !sanitizedReason.isBlank() ? sanitizedReason : "N/A";
            auditTrailService.createAuditTrail(
                    authenticatedUser,
                    ActionType.CANCELLATION,
                    restaurant,
                    RequestStatus.OPEN,
                    null, // ipAddress
                    null, // userAgent
                    orderedItem.getId(),
                    "ITEM",
                    String.format("Item cancellation request created. Reason: %s", auditReason)
            );
        } catch (Exception e) {
            log.error("Failed to create audit trail for item cancellation request: {}", e.getMessage(), e);
        }
    }

    private void notifyManagersCancellationRequestOpenedBestEffort(OrderedItem orderedItem, Locale userLocale) {
        try {
            UUID restaurantId = orderNotificationService.getRestaurantIdFromOrder(orderedItem.getOrder());
            UUID managerRoleId = roleRepository.findByName("MANAGER").map(Role::getId).orElse(null);
            if (managerRoleId == null) {
                return;
            }
            List<User> managers = userRepository.findAllByRestaurantIdAndRoleIdAndIsDeletedFalse(restaurantId, managerRoleId);
            if (managers.isEmpty()) {
                return;
            }
            notificationService.notifyCancellationRequestOpened(orderedItem, managers, orderedItem.getCancellationRequestedBy(), userLocale);
        } catch (Exception e) {
            log.error("Failed to send manager notification for cancellation request: {}", e.getMessage(), e);
        }
    }

    /**
     * Helper method to get modifier item from batch-loaded map or query fresh if not provided
     * @param modifierItemId The modifier item ID
     * @param modifierItemsMap The batch-loaded map of modifier items (can be null)
     * @param userLocale The user locale for error messages
     * @return The ModifierItem
     */
    private ModifierItem getModifierItem(UUID modifierItemId, Map<UUID, ModifierItem> modifierItemsMap, Locale userLocale) {
        if (modifierItemsMap != null && modifierItemsMap.containsKey(modifierItemId)) {
            return modifierItemsMap.get(modifierItemId);
        } else {
            // Fallback: query individually if map not provided (for other endpoints)
            return modifierItemRepository.findById(modifierItemId)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                            messageUtil.getMessage("modifier.item.name.not.found", userLocale)));
        }
    }
}

