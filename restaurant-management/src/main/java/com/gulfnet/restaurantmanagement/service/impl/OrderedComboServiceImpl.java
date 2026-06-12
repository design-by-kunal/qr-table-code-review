package com.gulfnet.restaurantmanagement.service.impl;

import com.gulfnet.restaurantmanagement.config.RestaurantChainConfigProperties;
import com.gulfnet.restaurantmanagement.service.NotificationService;
import com.gulfnet.restaurantmanagement.service.OrderedComboService;
import com.gulfnet.restaurantmanagement.service.OrderedItemService;
import com.gulfnet.restaurantmanagement.service.OrderNotificationService;
import com.gulfnet.restaurantmanagement.service.OrderPricingService;
import com.gulfnet.restaurantmanagement.service.OrderValidationService;
import com.gulfnet.restaurantmanagement.service.AuditTrailService;
import com.gulfnet.restaurantmanagement.util.MessageUtil;
import com.gulfnet.shared_library.entity.*;
import com.gulfnet.shared_library.enums.ComboGroupType;
import com.gulfnet.shared_library.enums.ComboType;
import com.gulfnet.shared_library.enums.ItemStatus;
import com.gulfnet.shared_library.enums.RequestStatus;
import com.gulfnet.shared_library.enums.ActionType;
import com.gulfnet.shared_library.model.request.ItemCancellationRequestDto;
import com.gulfnet.shared_library.model.request.ItemStatusPayload;
import com.gulfnet.shared_library.model.request.OrderedComboGroupRequest;
import com.gulfnet.shared_library.model.request.OrderedComboItemModifierRequest;
import com.gulfnet.shared_library.model.request.OrderedComboItemRequest;
import com.gulfnet.shared_library.model.request.OrderedComboRequest;
import com.gulfnet.shared_library.model.response.dto.ItemStatusResponseWrapper;
import com.gulfnet.shared_library.model.response.dto.ResponseDto;
import com.gulfnet.shared_library.model.response.dto.ModifierItemResponse;
import com.gulfnet.shared_library.model.response.dto.OrderedComboGroupResponse;
import com.gulfnet.shared_library.model.response.dto.OrderedComboItemModifierResponse;
import com.gulfnet.shared_library.model.response.dto.OrderedComboItemResponse;
import com.gulfnet.shared_library.model.response.dto.OrderedComboResponse;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gulfnet.shared_library.repository.*;
import com.gulfnet.shared_library.util.CurrencyFormatter;
import com.gulfnet.shared_library.config.AWSService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.*;
import java.util.Comparator;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrderedComboServiceImpl implements OrderedComboService {

    private static final String ERROR_PERSIST_TOTALS_COMBO_ITEM = "Failed to persist totals for combo ordered item {}: {}";

    private final OrderedItemRepository orderedItemRepository;
    private final OrderedItemModifierRepository orderedItemModifierRepository;
    private final OrderedComboRepository orderedComboRepository;
    private final ComboRepository comboRepository;
    private final ItemRepository itemRepository;
    private final ModifierItemRepository modifierItemRepository;
    private final ComboItemModifierRepository comboItemModifierRepository;
    private final ComboGroupRepository comboGroupRepository;
    private final ModifierGroupRepository modifierGroupRepository;
    private final RestaurantItemAvailabilityRepository restaurantItemAvailabilityRepository;
    private final RoleRepository roleRepository;
    private final UserRepository userRepository;
    private final RestaurantChainConfigProperties restaurantChainConfigProperties;
    private final OrderedItemService orderedItemService;
    private final OrderNotificationService orderNotificationService;
    private final OrderPricingService orderPricingService;
    private final OrderValidationService orderValidationService;
    private final NotificationService notificationService;
    private final MessageUtil messageUtil;
    private final AWSService awsService;
    private final AuditTrailService auditTrailService;

    // Message keys
    private static final String MSG_ITEM_NOT_FOUND = "item.not.found";
    private static final String MSG_COMBO_NOT_FOUND = "combo.not.found";
    private static final String MSG_MODIFIER_ITEM_NOT_FOUND = "modifier.item.not.found";

    // ==================== COMBO CREATION METHODS ====================

    /**
     * Creates ordered items for a FIXED combo using predetermined items from all combo groups.
     * For each combo group, creates ordered items for all predefined item mappings with their
     * predefined modifiers. Calculates item prices including modifier prices and assigns items to KDS.
     *
     * @param orderedCombo the ordered combo entity
     * @param comboRequest the combo request (not used for FIXED combos, items are predetermined)
     * @param createdBy    the user creating the ordered items
     * @param userLocale   locale for localized error messages
     */
    @Override
    public void createFixedOrderedItems(OrderedCombo orderedCombo, OrderedComboRequest comboRequest, User createdBy, Locale userLocale) {
        // For FIXED combos, use predetermined items from all combo groups
        List<ComboGroup> comboGroups = orderedCombo.getCombo().getComboGroups();
        
        for (ComboGroup comboGroup : comboGroups) {
            // Get predefined item mappings for this group
            List<ComboItemMapping> predefinedMappings = comboGroup.getComboItemMappings();
            
            for (ComboItemMapping predefinedMapping : predefinedMappings) {
                Item item = predefinedMapping.getCategoryItemMapping().getItem();

                // Calculate item price (base price + predefined modifiers)
                BigDecimal itemPrice = BigDecimal.valueOf(item.getBasePrice());
                
                // Add predefined modifier prices
                List<ComboItemModifier> predefinedModifiers = comboItemModifierRepository
                    .findByComboItemMappingId(predefinedMapping.getId());
                
                for (ComboItemModifier predefinedModifier : predefinedModifiers) {
                    BigDecimal modifierPrice = predefinedModifier.getModifierItem().getPrice();
                    if (modifierPrice != null) {
                        itemPrice = itemPrice.add(modifierPrice);
                    }
                }

                // Create and persist OrderedItem (combo item) with computed price
                OrderedItem orderedItem = createAndPersistComboOrderedItem(orderedCombo, item, itemPrice, createdBy);
                
                // Create OrderedItemModifiers for predefined modifiers
                createPredefinedOrderedItemModifiers(orderedItem, predefinedModifiers, createdBy, userLocale);
                
                // Assign combo item to KDS based on item category
                UUID restaurantId = orderNotificationService.getRestaurantIdFromOrder(orderedCombo.getOrder());
                orderedItemService.assignItemToKds(orderedItem, restaurantId);
            }
        }
    }

    /**
     * Creates ordered items for a CHOICE combo using customer-selected items from the request.
     * For each combo group in the request, creates ordered items for selected items with their
     * selected modifiers. Calculates item prices including modifier prices and assigns items to KDS.
     *
     * @param orderedCombo the ordered combo entity
     * @param comboRequest the combo request containing selected items and modifiers
     * @param createdBy    the user creating the ordered items
     * @param userLocale   locale for localized error messages
     * @throws ResponseStatusException if item not found
     */
    @Override
    public void createChoiceOrderedItems(OrderedCombo orderedCombo, OrderedComboRequest comboRequest, User createdBy, Locale userLocale) {
        Map<UUID, ComboGroup> comboGroupMap = orderedCombo.getCombo().getComboGroups().stream()
            .collect(Collectors.toMap(ComboGroup::getComboGroupId, Function.identity()));
        
        for (OrderedComboGroupRequest groupRequest : comboRequest.getComboGroups()) {
            ComboGroup comboGroup = comboGroupMap.get(groupRequest.getComboGroupId());
            
            // Create OrderedItems for selected items (not predefined)
            for (OrderedComboItemRequest itemRequest : groupRequest.getOrderedItems()) {
                Item item = itemRepository.findById(itemRequest.getItemId())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        messageUtil.getMessage(MSG_ITEM_NOT_FOUND, userLocale)));
                
                // Calculate item price (base price + selected modifiers)
                BigDecimal itemPrice = calculateItemPriceWithModifiers(item, itemRequest, userLocale);

                // Create and persist OrderedItem with computed price
                OrderedItem orderedItem = createAndPersistComboOrderedItem(orderedCombo, item, itemPrice, createdBy);

                // Create OrderedItemModifiers for selected modifiers
                orderedItemService.createSelectedOrderedItemModifiers(orderedItem, itemRequest.getOrderedItemModifiers(), createdBy, userLocale);
                
                // Assign combo item to KDS based on item category
                assignComboItemToKds(orderedCombo, orderedItem);
            }
        }
    }

    /**
     * Creates ordered items for a MIXED combo by processing both FIXED and CHOICE groups.
     * For FIXED groups, uses predetermined items from the combo definition.
     * For CHOICE groups, uses customer-selected items from the request.
     *
     * @param orderedCombo the ordered combo entity
     * @param comboRequest the combo request containing selected items for CHOICE groups
     * @param createdBy    the user creating the ordered items
     * @param userLocale   locale for localized error messages
     * @throws ResponseStatusException if combo group not found
     */
    @Override
    public void createMixedOrderedItems(OrderedCombo orderedCombo, OrderedComboRequest comboRequest, User createdBy, Locale userLocale) {
        Combo combo = orderedCombo.getCombo();
        
        // Process all groups from the request
        for (OrderedComboGroupRequest groupRequest : comboRequest.getComboGroups()) {
            ComboGroup comboGroup = comboGroupRepository.findById(groupRequest.getComboGroupId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                    messageUtil.getMessage("combo.group.not.found", userLocale)));
            
            if (comboGroup.getGroupType() == ComboGroupType.FIXED) {
                // For FIXED groups, ignore the request items and use predetermined items
                log.info("Processing FIXED group {} for MIXED combo {}. Ignoring request items and using predetermined items.", 
                        comboGroup.getComboGroupId(), combo.getComboId());
                createFixedGroupOrderedItems(orderedCombo, comboGroup, createdBy, userLocale);
            } else if (comboGroup.getGroupType() == ComboGroupType.CHOICE) {
                // For CHOICE groups, use customer selected items
                log.info("Processing CHOICE group {} for MIXED combo {}. Using customer selected items.", 
                        comboGroup.getComboGroupId(), combo.getComboId());
                createChoiceGroupOrderedItems(orderedCombo, groupRequest, createdBy, userLocale);
            }
        }
    }

    /**
     * Creates and persists an OrderedItem for a combo with formatted prices and status.
     * Sets item status to PUSHED for cashiers, ON_HOLD for others. Persists total item amount
     * including modifiers. Combo items are always quantity 1.
     *
     * @param orderedCombo the ordered combo this item belongs to
     * @param item         the item entity to create ordered item from
     * @param itemPrice    the total item price including modifiers
     * @param createdBy    the user creating the ordered item
     * @return the created and persisted OrderedItem
     */
    private OrderedItem createAndPersistComboOrderedItem(OrderedCombo orderedCombo, Item item, BigDecimal itemPrice, User createdBy) {
        // Get currency for formatting
        String currency = restaurantChainConfigProperties.getChain().getCurrency();

        // Format prices according to currency
        BigDecimal formattedBasePrice = CurrencyFormatter.formatAmount(BigDecimal.valueOf(item.getBasePrice()), currency);
        BigDecimal formattedItemPrice = CurrencyFormatter.formatAmount(itemPrice, currency);

        // Determine item status: PUSHED for cashiers, ON_HOLD for others (keep ON_HOLD for anonymous TAKEAWAY)
        ItemStatus itemStatus = determineItemStatus(orderedCombo.getOrder(), createdBy);

        // Create OrderedItem
        OrderedItem orderedItem = OrderedItem.builder()
            .order(orderedCombo.getOrder())
            .orderedCombo(orderedCombo)
            .item(item)
            .quantity(1) // Combo items are always quantity 1
            .price(formattedBasePrice)
            .itemStatus(itemStatus)
            .notes(null)
            .createdAt(OffsetDateTime.now(ZoneOffset.UTC))
            .createdBy(orderedCombo.getCreatedBy())
            .updatedAt(OffsetDateTime.now(ZoneOffset.UTC))
            .updatedBy(createdBy)
            .build();

        orderedItem = orderedItemRepository.save(orderedItem);

        // Persist totals for combo item (gross with modifiers), no discount
        try {
            orderedItem.setTotalItemAmount(formattedItemPrice);
            orderedItem.setDiscountedPrice(null);
            orderedItem.setTotalDiscountedItemAmount(null);
            orderedItemRepository.save(orderedItem);
        } catch (Exception e) {
            log.error(ERROR_PERSIST_TOTALS_COMBO_ITEM, orderedItem.getId(), e.getMessage(), e);
        }

        return orderedItem;
    }

    /**
     * Builds a persisted-ready {@link OrderedItem} row for a combo line (quantity 1, base price, combo link, audit fields).
     *
     * @param orderedCombo parent combo instance
     * @param item         catalog item for the line
     * @param itemStatus   initial kitchen/status for the line
     * @param createdBy    user stamping create/update metadata
     */
    private OrderedItem buildComboOrderedItemShell(
            OrderedCombo orderedCombo, Item item, ItemStatus itemStatus, User createdBy) {
        return OrderedItem.builder()
                .order(orderedCombo.getOrder())
                .item(item)
                .quantity(1)
                .price(BigDecimal.valueOf(item.getBasePrice()))
                .itemStatus(itemStatus)
                .notes(null)
                .createdAt(OffsetDateTime.now(ZoneOffset.UTC))
                .createdBy(createdBy)
                .updatedAt(OffsetDateTime.now(ZoneOffset.UTC))
                .updatedBy(createdBy)
                .orderedCombo(orderedCombo)
                .build();
    }

    /**
     * Creates ordered items for a FIXED combo group using predetermined items.
     * Creates ordered items for all predefined item mappings in the group with their
     * predefined modifiers. Calculates total prices and assigns items to KDS.
     *
     * @param orderedCombo the ordered combo entity
     * @param comboGroup   the combo group with fixed items
     * @param createdBy    the user creating the ordered items
     * @param userLocale   locale for localized error messages
     */
    @Override
    public void createFixedGroupOrderedItems(OrderedCombo orderedCombo, ComboGroup comboGroup, User createdBy, Locale userLocale) {
        List<ComboItemMapping> fixedMappings = comboGroup.getComboItemMappings();
        
        for (ComboItemMapping mapping : fixedMappings) {
            Item item = mapping.getCategoryItemMapping().getItem();
            // Compute total with predefined modifiers
            List<ComboItemModifier> predefinedModifiers = comboItemModifierRepository
                .findByComboItemMappingId(mapping.getId());
            BigDecimal itemTotal = BigDecimal.valueOf(item.getBasePrice());
            for (ComboItemModifier modifier : predefinedModifiers) {
                itemTotal = itemTotal.add(modifier.getModifierItem().getPrice());
            }
            
            // Determine item status: PUSHED for cashiers, ON_HOLD for others (keep ON_HOLD for anonymous TAKEAWAY)
            ItemStatus itemStatus = determineItemStatus(orderedCombo.getOrder(), createdBy);
            
            OrderedItem orderedItem = orderedItemRepository.save(
                    buildComboOrderedItemShell(orderedCombo, item, itemStatus, createdBy));
            // Populate totals for combo item (gross with predefined modifiers), no discount
            try {
                orderedItem.setTotalItemAmount(itemTotal);
                orderedItem.setDiscountedPrice(null);
                orderedItem.setTotalDiscountedItemAmount(null);
                orderedItemRepository.save(orderedItem);
            } catch (Exception e) {
                log.error(ERROR_PERSIST_TOTALS_COMBO_ITEM, orderedItem.getId(), e.getMessage(), e);
            }

            // Create predefined modifiers
            createPredefinedOrderedItemModifiers(orderedItem, predefinedModifiers, createdBy, userLocale);
            
            // Assign combo item to KDS based on item category
            UUID restaurantId = orderNotificationService.getRestaurantIdFromOrder(orderedCombo.getOrder());
            orderedItemService.assignItemToKds(orderedItem, restaurantId);
        }
    }

    /**
     * Creates ordered items for a CHOICE combo group using customer-selected items.
     * Creates ordered items for each selected item in the request with their selected modifiers.
     * Calculates item prices including modifiers and assigns items to KDS.
     *
     * @param orderedCombo the ordered combo entity
     * @param groupRequest the combo group request with selected items and modifiers
     * @param createdBy    the user creating the ordered items
     * @param userLocale   locale for localized error messages
     * @throws ResponseStatusException if item not found
     */
    @Override
    public void createChoiceGroupOrderedItems(OrderedCombo orderedCombo, OrderedComboGroupRequest groupRequest, User createdBy, Locale userLocale) {
        for (OrderedComboItemRequest itemRequest : groupRequest.getOrderedItems()) {
            Item item = itemRepository.findById(itemRequest.getItemId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                    messageUtil.getMessage(MSG_ITEM_NOT_FOUND, userLocale)));
            
            // Calculate item price with modifiers
            BigDecimal itemPrice = calculateItemPriceWithModifiers(item, itemRequest, userLocale);
            
            // Determine item status: PUSHED for cashiers, ON_HOLD for others (keep ON_HOLD for anonymous TAKEAWAY)
            ItemStatus itemStatus = determineItemStatus(orderedCombo.getOrder(), createdBy);
            
            OrderedItem orderedItem = orderedItemRepository.save(
                    buildComboOrderedItemShell(orderedCombo, item, itemStatus, createdBy));
            // Persist total for combo item (gross with modifiers), no discount
            try {
                orderedItem.setTotalItemAmount(itemPrice);
                orderedItem.setDiscountedPrice(null);
                orderedItem.setTotalDiscountedItemAmount(null);
                orderedItemRepository.save(orderedItem);
            } catch (Exception e) {
                log.error(ERROR_PERSIST_TOTALS_COMBO_ITEM, orderedItem.getId(), e.getMessage(), e);
            }
            
            // Create selected modifiers
            if (itemRequest.getOrderedItemModifiers() != null) {
                orderedItemService.createSelectedOrderedItemModifiers(orderedItem, itemRequest.getOrderedItemModifiers(), createdBy, userLocale);
            }
            
            // Assign combo item to KDS based on item category
            assignComboItemToKds(orderedCombo, orderedItem);
        }
    }

    /**
     * Calculate the total price of a combo item, including selected modifiers.
     * Null modifier prices are treated as zero.
     */
    private BigDecimal calculateItemPriceWithModifiers(Item item,
                                                       OrderedComboItemRequest itemRequest,
                                                       Locale userLocale) {
        BigDecimal itemPrice = BigDecimal.valueOf(item.getBasePrice());
        if (itemRequest.getOrderedItemModifiers() != null) {
            for (OrderedComboItemModifierRequest modifierRequest : itemRequest.getOrderedItemModifiers()) {
                for (UUID modifierItemId : modifierRequest.getModifierItemIds()) {
                    ModifierItem modifierItem = modifierItemRepository.findById(modifierItemId)
                        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                            messageUtil.getMessage(MSG_MODIFIER_ITEM_NOT_FOUND, userLocale)));
                    // Null-safe price handling: treat null prices as zero
                    BigDecimal modifierItemPrice = modifierItem.getPrice() != null ? modifierItem.getPrice() : BigDecimal.ZERO;
                    itemPrice = itemPrice.add(modifierItemPrice);
                }
            }
        }
        return itemPrice;
    }

    /**
     * Determine item status for ordered combo items.
     * Returns PUSHED for cashiers and for anonymous customer flows when waiter dependency is disabled,
     * except for TAKEAWAY orders (keep ON_HOLD).
     */
    private ItemStatus determineItemStatus(Order order, User createdBy) {
        // For TAKEAWAY orders, never auto-push at placement time (regardless of who creates the order).
        if (order != null && order.getOrderType() == com.gulfnet.shared_library.enums.OrderType.TAKEAWAY) {
            return ItemStatus.ON_HOLD;
        }
        if (createdBy != null && orderValidationService.isCashier(createdBy)) {
            return ItemStatus.PUSHED;
        }
        if (!restaurantChainConfigProperties.isWaiterDependencyEnabled()
                && createdBy == null
                && order != null
                && order.getOrderType() != com.gulfnet.shared_library.enums.OrderType.TAKEAWAY) {
            return ItemStatus.PUSHED;
        }
        return ItemStatus.ON_HOLD;
    }

    /**
     * Assign combo item to KDS based on item category.
     */
    private void assignComboItemToKds(OrderedCombo orderedCombo, OrderedItem orderedItem) {
        UUID restaurantId = orderNotificationService.getRestaurantIdFromOrder(orderedCombo.getOrder());
        orderedItemService.assignItemToKds(orderedItem, restaurantId);
    }

    /**
     * Creates OrderedItemModifier entities for predefined modifiers from a combo definition.
     * Links modifiers to the ordered item and persists them to the database.
     *
     * @param orderedItem        the ordered item to attach modifiers to
     * @param predefinedModifiers list of predefined modifiers from combo definition
     * @param createdBy          the user creating the modifiers (not used but kept for consistency)
     * @param userLocale         locale for localized error messages (not used but kept for consistency)
     */
    @Override
    public void createPredefinedOrderedItemModifiers(OrderedItem orderedItem, 
                                                      List<ComboItemModifier> predefinedModifiers, 
                                                      User createdBy, Locale userLocale) {
        for (ComboItemModifier predefinedModifier : predefinedModifiers) {
            OrderedItemModifier orderedItemModifier = OrderedItemModifier.builder()
                .orderedItem(orderedItem)
                .modifierGroup(predefinedModifier.getModifierItem().getModifierGroup())
                .modifierItem(predefinedModifier.getModifierItem())
                .price(predefinedModifier.getModifierItem().getPrice())
                .build();
            
            orderedItemModifierRepository.save(orderedItemModifier);
        }
    }

    // ==================== HELPER METHODS ====================
    // Method moved to OrderValidationService - removed duplicate implementation:
    // - isCashier

    // ==================== RESPONSE BUILDER METHODS ====================

    /**
     * Builds an OrderedComboResponse DTO from an OrderedCombo entity.
     * Includes combo details, groups, items, modifiers, formatted prices, and status.
     * Uses stored prices from database with fallback to base prices for legacy data.
     *
     * @param orderedCombo the ordered combo entity to convert
     * @param userLocale   locale for localized names and formatting
     * @return OrderedComboResponse with all combo details and nested groups/items
     */
    @Override
    public OrderedComboResponse buildOrderedComboResponse(OrderedCombo orderedCombo, Locale userLocale) {
        Combo combo = orderedCombo.getCombo();
        
        // Get currency for formatting
        String currency = restaurantChainConfigProperties.getChain() != null ? restaurantChainConfigProperties.getChain().getCurrency() : null;
        
        // Build combo groups
        List<OrderedComboGroupResponse> comboGroupResponses = combo.getComboGroups().stream()
            .map(comboGroup -> buildOrderedComboGroupResponse(comboGroup, orderedCombo, userLocale))
            .collect(Collectors.toList());
        
        // Get total combo amount (includes quantity), fallback to calculating from base price if null
        BigDecimal totalComboAmount = orderedCombo.getTotalComboAmount();
        if (totalComboAmount == null) {
            // Fallback for legacy data: calculate from base price × quantity
            totalComboAmount = orderedCombo.getPrice().multiply(BigDecimal.valueOf(orderedCombo.getQuantity()));
        }
        totalComboAmount = CurrencyFormatter.formatAmount(totalComboAmount, currency);
        
        // Use stored price from database (price at order time), fallback to combo base price for legacy data
        BigDecimal price;
        if (orderedCombo.getPrice() != null) {
            price = CurrencyFormatter.formatAmount(orderedCombo.getPrice(), currency);
        } else {
            // Fallback for legacy data: use combo base price
            price = CurrencyFormatter.formatAmount(combo.getBasePrice() != null ? combo.getBasePrice() : BigDecimal.ZERO, currency);
        }
        
        return OrderedComboResponse.builder()
            .id(orderedCombo.getId())
            .comboId(combo.getComboId())
            .comboName(orderValidationService.getComboName(combo, userLocale))
            .comboImageUrl(combo.getComboImageUrl() != null && !combo.getComboImageUrl().isEmpty() ? 
                         awsService.getPreSignedUrl(combo.getComboImageUrl()) : null)
            .comboType(combo.getType()) // Add combo type
            .quantity(orderedCombo.getQuantity())
            // Needed for refund eligibility of cancelled lines in session/table responses.
            .includedInPayment(orderedCombo.getIncludedInPayment() != null ? orderedCombo.getIncludedInPayment() : Boolean.FALSE)
            .price(price) // Use stored price from database (price at order time)
            .totalComboAmount(totalComboAmount) // Total price including quantity
            .itemStatus(orderedCombo.getItemStatus())
            .notes(orderedCombo.getNotes())
            .reason(orderedCombo.getReason())
            .requestStatus(orderedCombo.getCancellationRequestStatus() != null ? orderedCombo.getCancellationRequestStatus() : RequestStatus.NONE)
            .comboGroups(comboGroupResponses)
            .build();
    }

    /**
     * Builds an OrderedComboGroupResponse DTO from a ComboGroup and OrderedCombo.
     * For FIXED groups, shows all predetermined items. For CHOICE groups, assigns items
     * based on creation order and group position. Includes group metadata and ordered items.
     *
     * @param comboGroup  the combo group entity
     * @param orderedCombo the ordered combo entity
     * @param userLocale  locale for localized names
     * @return OrderedComboGroupResponse with group details and ordered items
     */
    @Override
    public OrderedComboGroupResponse buildOrderedComboGroupResponse(ComboGroup comboGroup, OrderedCombo orderedCombo, Locale userLocale) {
        // Load only items for the current ordered combo to avoid scanning the whole ordered_item table.
        List<OrderedItem> allComboItems = orderedItemRepository.findByOrderedComboId(orderedCombo.getId());
        
        // For CHOICE groups, we need to be more careful about which items belong to which group
        // Since the same item can be in multiple groups, we need to use a different approach
        List<OrderedItem> comboItems;
        
        if (comboGroup.getGroupType() == ComboGroupType.FIXED) {
            // For FIXED groups, show all predetermined items
            comboItems = allComboItems.stream()
                .filter(item -> {
                    boolean belongsToGroup = comboGroup.getComboItemMappings().stream()
                        .anyMatch(mapping -> mapping.getCategoryItemMapping().getItem().getId().equals(item.getItem().getId()));
                    log.info("FIXED: Item {} belongs to group {}: {}", item.getItem().getId(), comboGroup.getComboGroupId(), belongsToGroup);
                    return belongsToGroup;
                })
                .collect(Collectors.toList());
        } else {
            // For CHOICE groups, we need to be more selective
            // The issue is that we need to show only items that were explicitly selected for this group
            // Since we don't have a direct relationship, we'll use a different approach:
            // We'll show items that are in this group's mappings AND haven't been assigned to other groups yet
            
            // Get all combo groups for this combo
            List<ComboGroup> allComboGroups = orderedCombo.getCombo().getComboGroups();
            
            // Find the index of current group in the combo groups list
            int currentGroupIndex = allComboGroups.indexOf(comboGroup);
            
            // For CHOICE groups, we'll assign items based on creation order
            // Items created earlier belong to earlier groups
            List<OrderedItem> sortedItems = allComboItems.stream()
                .sorted(Comparator.comparing(OrderedItem::getCreatedAt))
                .collect(Collectors.toList());
            
            // Calculate which items belong to this group based on the group's position
            comboItems = new ArrayList<>();
            int itemsPerGroup = sortedItems.size() / allComboGroups.size();
            int startIndex = currentGroupIndex * itemsPerGroup;
            int endIndex = (currentGroupIndex == allComboGroups.size() - 1) ? sortedItems.size() : (currentGroupIndex + 1) * itemsPerGroup;
            
            for (int i = startIndex; i < endIndex; i++) {
                if (i < sortedItems.size()) {
                    OrderedItem item = sortedItems.get(i);
                    // Verify that this item is actually in this group's mappings
                    boolean belongsToGroup = comboGroup.getComboItemMappings().stream()
                        .anyMatch(mapping -> mapping.getCategoryItemMapping().getItem().getId().equals(item.getItem().getId()));
                    
                    if (belongsToGroup) {
                        comboItems.add(item);
                        log.info("CHOICE: Assigned item {} to group {} (index {})", item.getItem().getId(), comboGroup.getComboGroupId(), i);
                    }
                }
            }
        }
        
        log.info("Filtered to {} items for combo group {}", comboItems.size(), comboGroup.getComboGroupId());
        
        List<OrderedComboItemResponse> comboItemResponses = comboItems.stream()
            .map(item -> {
                // Find the ComboItemMapping to get the isDefault flag
                Boolean isDefault = comboGroup.getComboItemMappings().stream()
                    .filter(mapping -> mapping.getCategoryItemMapping().getItem().getId().equals(item.getItem().getId()))
                    .findFirst()
                    .map(ComboItemMapping::getIsDefault)
                    .orElse(false);
                
                return buildOrderedComboItemResponse(item, userLocale, isDefault);
            })
            .collect(Collectors.toList());
        
        return OrderedComboGroupResponse.builder()
            .comboGroupId(comboGroup.getComboGroupId())
            .comboGroupName(orderValidationService.getComboGroupName(comboGroup, userLocale))
            .comboGroupType(comboGroup.getGroupType().toString())
            .minSelect(comboGroup.getMinSelect())
            .maxSelect(comboGroup.getMaxSelect())
            .orderedItems(comboItemResponses)
            .build();
    }
    
    /**
     * Builds an OrderedComboItemResponse DTO from an OrderedItem entity.
     * Includes item details, modifiers grouped by modifier group, formatted prices,
     * and image URLs with pre-signed S3 URLs. Uses stored prices with fallback for legacy data.
     *
     * @param orderedItem the ordered item entity to convert
     * @param userLocale  locale for localized names
     * @param isDefault   whether this item is a default item in the combo group
     * @return OrderedComboItemResponse with item details and modifiers
     */
    @Override
    public OrderedComboItemResponse buildOrderedComboItemResponse(OrderedItem orderedItem, Locale userLocale, Boolean isDefault) {
        Item item = orderedItem.getItem();
        
        // Get currency for formatting
        String currency = restaurantChainConfigProperties.getChain() != null ? restaurantChainConfigProperties.getChain().getCurrency() : null;
        
        // Load modifiers for this ordered item
        List<OrderedItemModifier> modifiers = orderedItemModifierRepository.findByOrderedItemId(orderedItem.getId());
        
        List<OrderedComboItemModifierResponse> modifierResponses = modifiers.stream()
            .collect(Collectors.groupingBy(OrderedItemModifier::getModifierGroup))
            .entrySet().stream()
            .map(entry -> {
                ModifierGroup modifierGroup = entry.getKey();
                List<ModifierItemResponse> modifierItemResponses = entry.getValue().stream()
                    .map(modifier -> ModifierItemResponse.builder()
                        .id(modifier.getModifierItem().getId())
                        .modifierItemId(modifier.getModifierItem().getId())
                        .modifierItemName(orderValidationService.getModifierItemName(modifier.getModifierItem(), userLocale))
                        .price(modifier.getPrice())
                        .build())
                    .collect(Collectors.toList());
                
                return OrderedComboItemModifierResponse.builder()
                    .modifierGroupId(modifierGroup.getId())
                    .modifierGroupName(orderValidationService.getModifierGroupName(modifierGroup, userLocale))
                    .modifierItems(modifierItemResponses)
                    .build();
            })
            .collect(Collectors.toList());
        
        // Use stored price from database (price at order time), fallback to item base price for legacy data
        BigDecimal price;
        if (orderedItem.getPrice() != null) {
            price = CurrencyFormatter.formatAmount(orderedItem.getPrice(), currency);
        } else {
            // Fallback for legacy data: use item base price
            price = CurrencyFormatter.formatAmount(BigDecimal.valueOf(item.getBasePrice()), currency);
        }
        
        // Use stored total item amount from database, fallback to calculating if not stored
        BigDecimal totalItemAmount;
        if (orderedItem.getTotalItemAmount() != null) {
            totalItemAmount = CurrencyFormatter.formatAmount(orderedItem.getTotalItemAmount(), currency);
        } else {
            // Fallback for legacy data: calculate from stored price + modifier prices
            totalItemAmount = price;
            for (OrderedItemModifier modifier : modifiers) {
                totalItemAmount = totalItemAmount.add(modifier.getPrice());
            }
            totalItemAmount = CurrencyFormatter.formatAmount(totalItemAmount, currency);
        }
        
        OrderedComboItemResponse.OrderedComboItemResponseBuilder responseBuilder = OrderedComboItemResponse.builder()
            .id(orderedItem.getId())
            .itemId(item.getId())
            .itemName(orderValidationService.getItemName(item, userLocale))
            .imageUrl(item.getImageUrl() != null && !item.getImageUrl().isEmpty() ? 
                     awsService.getPreSignedUrl(item.getImageUrl()) : null)
            .alcoholType(orderedItem.getAlcoholType()) // Include alcohol type for receipt markers
            .price(price) // Use stored price from database (price at order time)
            .totalItemAmount(totalItemAmount) // Use stored total from database
            .isDefault(isDefault) // Set the isDefault flag
            .reason(orderedItem.getReason())
            .orderedItemModifiers(modifierResponses);
        
        return responseBuilder.build();
    }

    // Methods moved to OrderValidationService - removed duplicate implementations:
    // - getComboName
    // - getComboGroupName
    // - getItemName
    // - getModifierGroupName
    // - getModifierItemName

    // ==================== COMBO ENTITY CREATION METHODS ====================

    /**
     * Common helper to build and persist an {@link OrderedCombo} with the correct base price,
     * status and total amount, ensuring consistent behavior across FIXED, CHOICE and MIXED combos.
     */
    private OrderedCombo buildAndPersistOrderedCombo(
            Order order,
            Combo combo,
            OrderedComboRequest comboRequest,
            User createdBy,
            BigDecimal totalComboAmount
    ) {
        // Get currency for formatting
        String currency = restaurantChainConfigProperties.getChain().getCurrency();

        // Format base price per unit
        BigDecimal formattedBasePrice = CurrencyFormatter.formatAmount(combo.getBasePrice(), currency);

        ItemStatus comboStatus = determineItemStatus(order, createdBy);

        // Create OrderedCombo entity
        OrderedCombo orderedCombo = OrderedCombo.builder()
            .order(order)
            .combo(combo)
            .quantity(comboRequest.getQuantity())
            .price(formattedBasePrice) // Base price per unit (formatted)
            .itemStatus(comboStatus)
            .notes(comboRequest.getNotes())
            .createdAt(OffsetDateTime.now(ZoneOffset.UTC))
            .createdBy(order.getCreatedBy())
            .updatedAt(OffsetDateTime.now(ZoneOffset.UTC))
            .updatedBy(createdBy)
            .build();

        orderedCombo = orderedComboRepository.save(orderedCombo);
        // Persist total combo amount exactly as calculated by the caller
        orderedCombo.setTotalComboAmount(totalComboAmount);
        orderedComboRepository.save(orderedCombo);

        return orderedCombo;
    }

    /**
     * Creates a new OrderedCombo entity based on combo type (FIXED, CHOICE, or MIXED).
     * Routes to the appropriate creation method based on the combo type and creates
     * all associated ordered items.
     *
     * @param order       the order this combo belongs to
     * @param comboRequest the combo request with combo ID, quantity, and item selections
     * @param menuId      the menu ID (not used but kept for interface compatibility)
     * @param createdBy   the user creating the combo
     * @param userLocale  locale for localized error messages
     * @return the created OrderedCombo entity
     * @throws ResponseStatusException if combo not found or combo type is invalid
     */
    @Override
    public OrderedCombo createNewOrderedCombo(Order order, OrderedComboRequest comboRequest, UUID menuId, User createdBy, Locale userLocale) {
        Combo combo = comboRepository.findById(comboRequest.getComboId())
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                messageUtil.getMessage(MSG_COMBO_NOT_FOUND, userLocale)));
        
        // Route to appropriate handler based on combo type
        OrderedCombo orderedCombo;
        switch (combo.getType()) {
            case FIXED:
                orderedCombo = createFixedOrderedCombo(order, comboRequest, createdBy, userLocale);
                break;
            case CHOICE:
                orderedCombo = createChoiceOrderedCombo(order, comboRequest, createdBy, userLocale);
                break;
            case MIXED:
                orderedCombo = createMixedOrderedCombo(order, comboRequest, createdBy, userLocale);
                break;
            default:
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    messageUtil.getMessage("combo.type.invalid", userLocale));
        }
        
        return orderedCombo;
    }

    /**
     * Creates a FIXED OrderedCombo with predetermined items from all combo groups.
     * Calculates total price as base price multiplied by quantity. Creates ordered items
     * for all predefined items in all groups with their predefined modifiers.
     *
     * @param order       the order this combo belongs to
     * @param comboRequest the combo request with combo ID and quantity
     * @param createdBy   the user creating the combo
     * @param userLocale  locale for localized error messages
     * @return the created OrderedCombo entity
     * @throws ResponseStatusException if combo not found
     */
    @Override
    public OrderedCombo createFixedOrderedCombo(Order order, OrderedComboRequest comboRequest, User createdBy, Locale userLocale) {
        Combo combo = comboRepository.findById(comboRequest.getComboId())
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                messageUtil.getMessage(MSG_COMBO_NOT_FOUND, userLocale)));

        // For FIXED combo, calculate total price including quantity
        BigDecimal comboPrice = CurrencyFormatter.formatAmount(
            combo.getBasePrice().multiply(BigDecimal.valueOf(comboRequest.getQuantity())),
            restaurantChainConfigProperties.getChain().getCurrency());

        // Build and persist combo with shared helper
        OrderedCombo orderedCombo = buildAndPersistOrderedCombo(order, combo, comboRequest, createdBy, comboPrice);
        
        // Create OrderedItems for predefined items
        createFixedOrderedItems(orderedCombo, comboRequest, createdBy, userLocale);
        
        return orderedCombo;
    }

    /**
     * Creates a CHOICE OrderedCombo with customer-selected items.
     * Calculates total price based on selected items and modifiers using OrderPricingService.
     * Creates ordered items for selected items in each choice group.
     *
     * @param order       the order this combo belongs to
     * @param comboRequest the combo request with selected items and modifiers
     * @param createdBy   the user creating the combo
     * @param userLocale  locale for localized error messages
     * @return the created OrderedCombo entity
     * @throws ResponseStatusException if combo not found
     */
    @Override
    public OrderedCombo createChoiceOrderedCombo(Order order, OrderedComboRequest comboRequest, User createdBy, Locale userLocale) {
        Combo combo = comboRepository.findById(comboRequest.getComboId())
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                messageUtil.getMessage(MSG_COMBO_NOT_FOUND, userLocale)));

        // Calculate CHOICE combo price based on selected items
        BigDecimal calculatedComboPrice = orderPricingService.calculateChoiceComboPrice(combo, comboRequest, userLocale);

        // Build and persist combo with shared helper
        OrderedCombo orderedCombo = buildAndPersistOrderedCombo(order, combo, comboRequest, createdBy, calculatedComboPrice);
        
        // Create OrderedItems for selected items
        createChoiceOrderedItems(orderedCombo, comboRequest, createdBy, userLocale);
        
        log.info("Created CHOICE OrderedCombo: {} for Order: {} with price: {} (quantity: {})", 
            orderedCombo.getId(), order.getId(), calculatedComboPrice, comboRequest.getQuantity());
        
        return orderedCombo;
    }

    /**
     * Creates a MIXED OrderedCombo with both FIXED and CHOICE groups.
     * Calculates total price using OrderPricingService for mixed combo pricing.
     * Creates ordered items for FIXED groups (predetermined) and CHOICE groups (selected).
     *
     * @param order       the order this combo belongs to
     * @param comboRequest the combo request with selected items for CHOICE groups
     * @param createdBy   the user creating the combo
     * @param userLocale  locale for localized error messages
     * @return the created OrderedCombo entity
     * @throws ResponseStatusException if combo not found
     */
    @Override
    public OrderedCombo createMixedOrderedCombo(Order order, OrderedComboRequest comboRequest, User createdBy, Locale userLocale) {
        Combo combo = comboRepository.findById(comboRequest.getComboId())
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                messageUtil.getMessage(MSG_COMBO_NOT_FOUND, userLocale)));

        // Calculate MIXED combo price
        BigDecimal comboPrice = orderPricingService.calculateMixedComboPrice(combo, comboRequest, userLocale);

        // Build and persist combo with shared helper
        OrderedCombo orderedCombo = buildAndPersistOrderedCombo(order, combo, comboRequest, createdBy, comboPrice);

        // Create ordered items for both FIXED and CHOICE groups
        createMixedOrderedItems(orderedCombo, comboRequest, createdBy, userLocale);

        log.info("Created MIXED OrderedCombo: {} for Order: {} with price: {} (quantity: {})", 
            orderedCombo.getId(), order.getId(), comboPrice, comboRequest.getQuantity());
        
        return orderedCombo;
    }

    // ==================== COMBO STATUS UPDATE METHODS ====================

    /**
     * Checks if all non-canceled items in a combo are COOKING and updates combo status accordingly.
     * Only updates if combo is currently PUSHED. Updates combo status to COOKING and sends notifications.
     *
     * @param orderedItem      the ordered item that was updated (triggers the check)
     * @param authenticatedUser the user who triggered the status update
     * @param hasUserId        whether the user ID is available
     * @param userLocale       locale for localized notifications
     */
    @Override
    public void checkAndUpdateComboStatusWhenAllItemsCooking(OrderedItem orderedItem, User authenticatedUser, 
                                                              boolean hasUserId, Locale userLocale) {
        // Only check if item belongs to a combo
        if (orderedItem.getOrderedCombo() == null) {
            return;
        }
        
        OrderedCombo combo = orderedItem.getOrderedCombo();
        
        // Only update if combo is currently PUSHED
        if (combo.getItemStatus() != ItemStatus.PUSHED) {
            return;
        }
        
        List<OrderedItem> comboItems = getComboItemsWithUpdatedStatus(orderedItem, combo);
        if (comboItems.isEmpty()) {
            return;
        }
        
        // Check if all non-canceled items are in COOKING status
        boolean allItemsCooking = allNonCanceledItemsHaveStatus(comboItems, ItemStatus.COOKING);
        
        if (allItemsCooking) {
            log.info("All items in combo {} are now COOKING, updating combo status from PUSHED to COOKING", combo.getId());
            orderNotificationService.updateComboStatusWithNotification(combo, ItemStatus.COOKING, authenticatedUser, hasUserId, userLocale, null);
        }
    }

    /**
     * Checks if all non-canceled items in a combo are READY and updates combo status accordingly.
     * Only updates if combo is currently COOKING. Updates combo status to READY and sends notifications.
     *
     * @param orderedItem      the ordered item that was updated (triggers the check)
     * @param authenticatedUser the user who triggered the status update
     * @param hasUserId        whether the user ID is available
     * @param userLocale       locale for localized notifications
     */
    @Override
    public void checkAndUpdateComboStatusWhenAllItemsReady(OrderedItem orderedItem, User authenticatedUser, 
                                                             boolean hasUserId, Locale userLocale) {
        // Only check if item belongs to a combo
        if (orderedItem.getOrderedCombo() == null) {
            return;
        }
        
        OrderedCombo combo = orderedItem.getOrderedCombo();
        
        // Only update if combo is currently COOKING
        if (combo.getItemStatus() != ItemStatus.COOKING) {
            return;
        }
        
        // Get all items in the combo from database
        List<OrderedItem> comboItems = getComboItemsWithUpdatedStatus(orderedItem, combo);
        
        if (comboItems.isEmpty()) {
            return;
        }
        
        // Check if all non-canceled items are in READY status
        boolean allItemsReady = allNonCanceledItemsHaveStatus(comboItems, ItemStatus.READY);
        
        if (allItemsReady) {
            log.info("All items in combo {} are now READY, updating combo status from COOKING to READY", combo.getId());
            orderNotificationService.updateComboStatusWithNotification(combo, ItemStatus.READY, authenticatedUser, hasUserId, userLocale, null);
        }
    }

    /**
     * Checks if all non-canceled items in a combo are SERVED and updates combo status accordingly.
     * Only updates if combo is currently READY. Updates combo status to SERVED and sends notifications.
     *
     * @param orderedItem      the ordered item that was updated (triggers the check)
     * @param authenticatedUser the user who triggered the status update
     * @param hasUserId        whether the user ID is available
     * @param userLocale       locale for localized notifications
     */
    @Override
    public void checkAndUpdateComboStatusWhenAllItemsServed(OrderedItem orderedItem, User authenticatedUser, 
                                                              boolean hasUserId, Locale userLocale) {
        // Only check if item belongs to a combo
        if (orderedItem.getOrderedCombo() == null) {
            return;
        }
        
        OrderedCombo combo = orderedItem.getOrderedCombo();
        
        // Only update if combo is currently READY
        if (combo.getItemStatus() != ItemStatus.READY) {
            return;
        }
        
        List<OrderedItem> comboItems = getComboItemsWithUpdatedStatus(orderedItem, combo);
        
        if (comboItems.isEmpty()) {
            return;
        }
        
        // Check if all non-canceled items are in SERVED status
        boolean allItemsServed = allNonCanceledItemsHaveStatus(comboItems, ItemStatus.SERVED);
        
        if (allItemsServed) {
            log.info("All items in combo {} are now SERVED, updating combo status from READY to SERVED", combo.getId());
            orderNotificationService.updateComboStatusWithNotification(combo, ItemStatus.SERVED, authenticatedUser, hasUserId, userLocale, null);
        }
    }

    /**
     * Checks if all items in a combo are CANCELED and updates combo status accordingly.
     * Updates combo status to CANCELED and sends notifications. Does not update if combo is already CANCELED.
     *
     * @param orderedItem      the ordered item that was updated (triggers the check)
     * @param authenticatedUser the user who triggered the status update
     * @param hasUserId        whether the user ID is available
     * @param userLocale       locale for localized notifications
     */
    @Override
    public void checkAndUpdateComboStatusWhenAllItemsCanceled(OrderedItem orderedItem, User authenticatedUser, 
                                                                 boolean hasUserId, Locale userLocale) {
        // Only check if item belongs to a combo
        if (orderedItem.getOrderedCombo() == null) {
            return;
        }
        
        OrderedCombo combo = orderedItem.getOrderedCombo();
        
        // Don't update if combo is already CANCELED
        if (combo.getItemStatus() == ItemStatus.CANCELED) {
            return;
        }
        
        List<OrderedItem> comboItems = getComboItemsWithUpdatedStatus(orderedItem, combo);
        
        if (comboItems.isEmpty()) {
            return;
        }
        
        // Check if all items are in CANCELED status
        boolean allItemsCanceled = allItemsHaveStatus(comboItems, ItemStatus.CANCELED);
        
        if (allItemsCanceled) {
            log.info("All items in combo {} are now CANCELED, updating combo status to CANCELED", combo.getId());
            orderNotificationService.updateComboStatusWithNotification(combo, ItemStatus.CANCELED, authenticatedUser, hasUserId, userLocale, null);
        }
    }

    /**
     * Load all items for a combo and replace the updated one with its in-memory state.
     */
    private List<OrderedItem> getComboItemsWithUpdatedStatus(OrderedItem orderedItem, OrderedCombo combo) {
        List<OrderedItem> comboItems = orderedItemRepository.findByOrderedComboId(combo.getId());
        if (comboItems.isEmpty()) {
            return comboItems;
        }

        UUID updatedItemId = orderedItem.getId();
        for (int i = 0; i < comboItems.size(); i++) {
            if (comboItems.get(i).getId().equals(updatedItemId)) {
                comboItems.set(i, orderedItem);
                break;
            }
        }
        return comboItems;
    }

    /**
     * Check if all non-canceled items in the list have the given status.
     */
    private boolean allNonCanceledItemsHaveStatus(List<OrderedItem> items, ItemStatus status) {
        return items.stream()
                .filter(item -> item.getItemStatus() != ItemStatus.CANCELED)
                .allMatch(item -> item.getItemStatus() == status);
    }

    /**
     * Check if all items in the list have the given status.
     */
    private boolean allItemsHaveStatus(List<OrderedItem> items, ItemStatus status) {
        return items.stream()
                .allMatch(item -> item.getItemStatus() == status);
    }

    /**
     * Updates combo status based on the status of all items in the combo (bulk update).
     * Checks statuses in priority order: CANCELED (highest), COOKING, READY, SERVED.
     * Merges in-memory item states with database states before checking.
     *
     * @param combo            the ordered combo to update
     * @param updatedItems     list of items with updated statuses (in-memory state)
     * @param authenticatedUser the user who triggered the update
     * @param hasUserId        whether the user ID is available
     * @param userLocale       locale for localized notifications
     */
    @Override
    public void updateComboStatusFromItems(OrderedCombo combo, List<OrderedItem> updatedItems, User authenticatedUser, boolean hasUserId, Locale userLocale) {
        if (combo == null || updatedItems == null || updatedItems.isEmpty()) {
            return;
        }

        // Get all items in the combo from database
        List<OrderedItem> comboItems = orderedItemRepository.findByOrderedComboId(combo.getId());
        if (comboItems.isEmpty()) {
            return;
        }

        // Map updated items by ID for quick replacement
        Map<UUID, OrderedItem> updatedItemsMap = updatedItems.stream()
                .collect(Collectors.toMap(OrderedItem::getId, Function.identity()));

        // Merge in-memory states
        for (int i = 0; i < comboItems.size(); i++) {
            OrderedItem updatedItem = updatedItemsMap.get(comboItems.get(i).getId());
            if (updatedItem != null) {
                comboItems.set(i, updatedItem);
            }
        }

        // 1. Check for CANCELED status (highest priority)
        if (combo.getItemStatus() != ItemStatus.CANCELED) {
            boolean allItemsCanceled = comboItems.stream()
                    .allMatch(item -> item.getItemStatus() == ItemStatus.CANCELED);
            if (allItemsCanceled) {
                log.info("Bulk update: All items in combo {} are now CANCELED, updating combo status to CANCELED", combo.getId());
                orderNotificationService.updateComboStatusWithNotification(combo, ItemStatus.CANCELED, authenticatedUser, hasUserId, userLocale, null);
                return; // Once canceled, no need to check other statuses
            }
        }

        // 2. Check for PUSHED status (when items are pushed, combo header must also move from ON_HOLD -> PUSHED)
        // This is especially important for TAKEAWAY / PREPAID flows where items can be auto-pushed after payment.
        if (combo.getItemStatus() == ItemStatus.ON_HOLD) {
            boolean allNonCanceledItemsPushed = comboItems.stream()
                    .filter(item -> item.getItemStatus() != ItemStatus.CANCELED)
                    .allMatch(item -> item.getItemStatus() == ItemStatus.PUSHED);
            if (allNonCanceledItemsPushed) {
                log.info("Bulk update: All non-canceled items in combo {} are now PUSHED, updating combo status from ON_HOLD to PUSHED", combo.getId());
                orderNotificationService.updateComboStatusWithNotification(combo, ItemStatus.PUSHED, authenticatedUser, hasUserId, userLocale, null);
                return;
            }
        }

        // 3. Check for COOKING status
        if (combo.getItemStatus() == ItemStatus.PUSHED) {
            boolean allItemsCooking = comboItems.stream()
                    .filter(item -> item.getItemStatus() != ItemStatus.CANCELED)
                    .allMatch(item -> item.getItemStatus() == ItemStatus.COOKING);
            if (allItemsCooking) {
                log.info("Bulk update: All items in combo {} are now COOKING, updating combo status from PUSHED to COOKING", combo.getId());
                orderNotificationService.updateComboStatusWithNotification(combo, ItemStatus.COOKING, authenticatedUser, hasUserId, userLocale, null);
                return;
            }
        }

        // 4. Check for READY status
        if (combo.getItemStatus() == ItemStatus.COOKING) {
            boolean allItemsReady = comboItems.stream()
                    .filter(item -> item.getItemStatus() != ItemStatus.CANCELED)
                    .allMatch(item -> item.getItemStatus() == ItemStatus.READY);
            if (allItemsReady) {
                log.info("Bulk update: All items in combo {} are now READY, updating combo status from COOKING to READY", combo.getId());
                orderNotificationService.updateComboStatusWithNotification(combo, ItemStatus.READY, authenticatedUser, hasUserId, userLocale, null);
                return;
            }
        }

        // 5. Check for SERVED status
        if (combo.getItemStatus() == ItemStatus.READY) {
            boolean allItemsServed = comboItems.stream()
                    .filter(item -> item.getItemStatus() != ItemStatus.CANCELED)
                    .allMatch(item -> item.getItemStatus() == ItemStatus.SERVED);
            if (allItemsServed) {
                log.info("Bulk update: All items in combo {} are now SERVED, updating combo status from READY to SERVED", combo.getId());
                orderNotificationService.updateComboStatusWithNotification(combo, ItemStatus.SERVED, authenticatedUser, hasUserId, userLocale, null);
            }
        }
    }

    // ==================== CALCULATION RESPONSE BUILDERS ====================

    /**
     * Builds calculation responses for combo requests without creating actual orders.
     * Calculates prices for each combo based on type (FIXED, CHOICE, MIXED) and includes
     * availability information. Used for order calculation/preview before order creation.
     *
     * @param comboRequests list of combo requests to calculate
     * @param restaurantId the restaurant ID for availability checks
     * @param menuId        the menu ID (not used but kept for interface compatibility)
     * @param userLocale    locale for localized names and formatting
     * @return list of OrderedComboResponse with calculated prices and availability
     * @throws ResponseStatusException if combo not found or combo type is invalid
     */
    @Override
    public List<OrderedComboResponse> buildCalculationComboResponses(List<OrderedComboRequest> comboRequests, UUID restaurantId, UUID menuId, Locale userLocale) {
        List<OrderedComboResponse> responses = new ArrayList<>();
        if (comboRequests == null || comboRequests.isEmpty()) {
            return responses;
        }

        for (OrderedComboRequest comboRequest : comboRequests) {
            Combo combo = comboRepository.findById(comboRequest.getComboId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                    messageUtil.getMessage(MSG_COMBO_NOT_FOUND, userLocale)));

            BigDecimal totalComboAmount;
            switch (combo.getType()) {
                case FIXED -> totalComboAmount = combo.getBasePrice().multiply(BigDecimal.valueOf(comboRequest.getQuantity()));
                case CHOICE -> totalComboAmount = orderPricingService.calculateChoiceComboPrice(combo, comboRequest, userLocale);
                case MIXED -> totalComboAmount = orderPricingService.calculateMixedComboPrice(combo, comboRequest, userLocale);
                default -> throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        messageUtil.getMessage("combo.type.invalid", userLocale));
            }

            // Build combo groups with items and modifiers
            List<OrderedComboGroupResponse> comboGroupResponses = buildCalculationComboGroups(combo, comboRequest, restaurantId, menuId, userLocale);

            // Calculate combo availability
            Boolean comboIsAvailable = calculateComboAvailability(combo, restaurantId, menuId);

            responses.add(OrderedComboResponse.builder()
                .id(null)
                .comboId(combo.getComboId())
                .comboName(orderValidationService.getComboName(combo, userLocale))
                .comboImageUrl(combo.getComboImageUrl() != null && !combo.getComboImageUrl().isEmpty() ?
                        awsService.getPreSignedUrl(combo.getComboImageUrl()) : null)
                .comboType(combo.getType())
                .quantity(comboRequest.getQuantity())
                .price(combo.getBasePrice())
                .totalComboAmount(totalComboAmount)
                .itemStatus(ItemStatus.ON_HOLD)
                .notes(comboRequest.getNotes())
                .requestStatus(RequestStatus.NONE) // No request status for calculation responses
                .isAvailable(comboIsAvailable)
                .comboGroups(comboGroupResponses)
                .build());
        }

        return responses;
    }

    /**
     * Builds a single combo line for price/availability preview (no persisted ordered-item id).
     *
     * @param item               catalog item
     * @param totalItemAmount    line total including modifiers
     * @param isDefault          whether the mapping is the combo default selection
     * @param itemIsAvailable    restaurant-scoped availability, or null when unknown
     * @param modifierResponses  modifier breakdown for the line
     * @param userLocale         locale for localized item naming
     */
    private OrderedComboItemResponse buildOrderedComboCalculationItemResponse(
            Item item,
            BigDecimal totalItemAmount,
            boolean isDefault,
            Boolean itemIsAvailable,
            List<OrderedComboItemModifierResponse> modifierResponses,
            Locale userLocale) {
        return OrderedComboItemResponse.builder()
                .id(null)
                .itemId(item.getId())
                .itemName(orderValidationService.getItemName(item, userLocale))
                .imageUrl(item.getImageUrl() != null && !item.getImageUrl().isEmpty()
                        ? awsService.getPreSignedUrl(item.getImageUrl()) : null)
                .alcoholType(item.getAlcoholType())
                .price(BigDecimal.valueOf(item.getBasePrice()))
                .totalItemAmount(totalItemAmount)
                .isDefault(isDefault)
                .isAvailable(itemIsAvailable)
                .orderedItemModifiers(modifierResponses)
                .build();
    }

    /**
     * Builds calculation responses for combo groups with items and modifiers.
     * For FIXED groups, uses predetermined items. For CHOICE groups, uses items from request.
     * Includes item availability checks and calculated prices including modifiers.
     *
     * @param combo         the combo entity
     * @param comboRequest  the combo request with selected items for CHOICE groups
     * @param restaurantId  the restaurant ID for availability checks
     * @param menuId        the menu ID (not used but kept for interface compatibility)
     * @param userLocale    locale for localized names
     * @return list of OrderedComboGroupResponse with items, modifiers, and availability
     * @throws ResponseStatusException if item not found or modifier item not found
     */
    @Override
    public List<OrderedComboGroupResponse> buildCalculationComboGroups(Combo combo, OrderedComboRequest comboRequest, UUID restaurantId, UUID menuId, Locale userLocale) {
        List<OrderedComboGroupResponse> groupResponses = new ArrayList<>();
        
        // Map request groups by ID for quick lookup
        Map<UUID, OrderedComboGroupRequest> requestGroupMap = (comboRequest.getComboGroups() == null) 
            ? new HashMap<>()
            : comboRequest.getComboGroups().stream()
                .collect(Collectors.toMap(OrderedComboGroupRequest::getComboGroupId, Function.identity()));
        
        // Build response for each combo group in the combo definition
        for (ComboGroup comboGroup : combo.getComboGroups()) {
            List<OrderedComboItemResponse> itemResponses = new ArrayList<>();
            
            if (comboGroup.getGroupType() == ComboGroupType.FIXED) {
                // For FIXED groups, use predetermined items from combo definition
                for (ComboItemMapping mapping : comboGroup.getComboItemMappings()) {
                    Item item = mapping.getCategoryItemMapping().getItem();
                    BigDecimal itemPrice = BigDecimal.valueOf(item.getBasePrice());
                    
                    // Add predefined modifier prices
                    List<ComboItemModifier> predefinedModifiers = comboItemModifierRepository
                        .findByComboItemMappingId(mapping.getId());
                    
                    for (ComboItemModifier predefinedModifier : predefinedModifiers) {
                        // Null-safe price handling: treat null prices as zero
                        BigDecimal modifierItemPrice = predefinedModifier.getModifierItem().getPrice() != null 
                            ? predefinedModifier.getModifierItem().getPrice() : BigDecimal.ZERO;
                        itemPrice = itemPrice.add(modifierItemPrice);
                    }
                    
                    // Build modifier responses
                    List<OrderedComboItemModifierResponse> modifierResponses = buildCalculationComboItemModifiers(
                        predefinedModifiers, userLocale);
                    
                    // Calculate item availability using restaurant_item_availability
                    Boolean itemIsAvailable = orderedItemService.calculateItemAvailability(mapping.getCategoryItemMapping(), restaurantId);
                    
                    itemResponses.add(buildOrderedComboCalculationItemResponse(
                            item, itemPrice, mapping.getIsDefault(), itemIsAvailable, modifierResponses, userLocale));
                }
            } else {
                // For CHOICE groups, use items from request
                OrderedComboGroupRequest requestGroup = requestGroupMap.get(comboGroup.getComboGroupId());
                if (requestGroup != null && requestGroup.getOrderedItems() != null) {
                    for (OrderedComboItemRequest itemRequest : requestGroup.getOrderedItems()) {
                        Item item = itemRepository.findById(itemRequest.getItemId())
                            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                                messageUtil.getMessage(MSG_ITEM_NOT_FOUND, userLocale)));
                        
                        BigDecimal itemPrice = calculateItemPriceWithModifiers(item, itemRequest, userLocale);
                        
                        // Build modifier responses from request
                        List<OrderedComboItemModifierResponse> modifierResponses = buildCalculationComboItemModifiersFromRequest(
                            itemRequest.getOrderedItemModifiers(), userLocale);
                        
                        // Check if this item is default in the combo group
                        Boolean isDefault = comboGroup.getComboItemMappings().stream()
                            .filter(mapping -> mapping.getCategoryItemMapping().getItem().getId().equals(item.getId()))
                            .findFirst()
                            .map(ComboItemMapping::getIsDefault)
                            .orElse(false);
                        
                        // Find CategoryItemMapping for this item to check availability
                        CategoryItemMapping categoryItemMapping = comboGroup.getComboItemMappings().stream()
                            .filter(mapping -> mapping.getCategoryItemMapping().getItem().getId().equals(item.getId()))
                            .findFirst()
                            .map(ComboItemMapping::getCategoryItemMapping)
                            .orElse(null);
                        
                        // Calculate item availability using restaurant_item_availability
                        Boolean itemIsAvailable = orderedItemService.calculateItemAvailability(categoryItemMapping, restaurantId);
                        
                        itemResponses.add(buildOrderedComboCalculationItemResponse(
                                item, itemPrice, isDefault, itemIsAvailable, modifierResponses, userLocale));
                    }
                }
            }
            
            groupResponses.add(OrderedComboGroupResponse.builder()
                .comboGroupId(comboGroup.getComboGroupId())
                .comboGroupName(orderValidationService.getComboGroupName(comboGroup, userLocale))
                .comboGroupType(comboGroup.getGroupType().toString())
                .minSelect(comboGroup.getMinSelect())
                .maxSelect(comboGroup.getMaxSelect())
                .orderedItems(itemResponses)
                .build());
        }
        
        return groupResponses;
    }

    /**
     * Builds calculation responses for predefined modifiers from combo definition.
     * Groups modifiers by modifier group and includes localized names and prices.
     *
     * @param predefinedModifiers list of predefined modifiers from combo definition
     * @param userLocale          locale for localized modifier names
     * @return list of OrderedComboItemModifierResponse grouped by modifier group
     */
    @Override
    public List<OrderedComboItemModifierResponse> buildCalculationComboItemModifiers(
            List<ComboItemModifier> predefinedModifiers, Locale userLocale) {
        if (predefinedModifiers == null || predefinedModifiers.isEmpty()) {
            return new ArrayList<>();
        }
        
        Map<UUID, List<ComboItemModifier>> modifiersByGroup = predefinedModifiers.stream()
            .collect(Collectors.groupingBy(mod -> mod.getModifierItem().getModifierGroup().getId()));
        
        return modifiersByGroup.entrySet().stream()
            .map(entry -> {
                UUID groupId = entry.getKey();
                List<ComboItemModifier> groupModifiers = entry.getValue();
                
                ModifierGroup modifierGroup = groupModifiers.get(0).getModifierItem().getModifierGroup();
                String groupName = orderValidationService.getModifierGroupName(modifierGroup, userLocale);
                
                List<ModifierItemResponse> modifierItemResponses = groupModifiers.stream()
                    .map(comboMod -> ModifierItemResponse.builder()
                        .id(comboMod.getModifierItem().getId())
                        .modifierItemId(comboMod.getModifierItem().getId())
                        .modifierItemName(orderValidationService.getModifierItemName(comboMod.getModifierItem(), userLocale))
                        .price(comboMod.getModifierItem().getPrice())
                        .build())
                    .collect(Collectors.toList());
                
                return OrderedComboItemModifierResponse.builder()
                    .modifierGroupId(groupId)
                    .modifierGroupName(groupName)
                    .modifierItems(modifierItemResponses)
                    .build();
            })
            .collect(Collectors.toList());
    }

    /**
     * Builds calculation responses for selected modifiers from combo request.
     * Groups modifiers by modifier group and includes localized names and prices.
     * Handles null prices as zero for calculation purposes.
     *
     * @param modifierRequests list of modifier requests with selected modifier item IDs
     * @param userLocale      locale for localized modifier names
     * @return list of OrderedComboItemModifierResponse grouped by modifier group
     * @throws ResponseStatusException if modifier group not found or modifier item not found
     */
    @Override
    public List<OrderedComboItemModifierResponse> buildCalculationComboItemModifiersFromRequest(
            List<OrderedComboItemModifierRequest> modifierRequests, Locale userLocale) {
        if (modifierRequests == null || modifierRequests.isEmpty()) {
            return new ArrayList<>();
        }
        
        List<OrderedComboItemModifierResponse> responses = new ArrayList<>();
        
        for (OrderedComboItemModifierRequest modifierRequest : modifierRequests) {
            ModifierGroup modifierGroup = modifierGroupRepository.findById(modifierRequest.getModifierGroupId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                    messageUtil.getMessage("modifier.group.not.found", userLocale)));
            
            String groupName = orderValidationService.getModifierGroupName(modifierGroup, userLocale);
            
            List<ModifierItemResponse> modifierItemResponses = new ArrayList<>();
            for (UUID modifierItemId : modifierRequest.getModifierItemIds()) {
                ModifierItem modifierItem = modifierItemRepository.findById(modifierItemId)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        messageUtil.getMessage(MSG_MODIFIER_ITEM_NOT_FOUND, userLocale)));
                
                // Null-safe price handling: treat null prices as zero for response
                BigDecimal modifierItemPrice = modifierItem.getPrice() != null ? modifierItem.getPrice() : BigDecimal.ZERO;
                modifierItemResponses.add(ModifierItemResponse.builder()
                    .id(modifierItem.getId())
                    .modifierItemId(modifierItem.getId())
                    .modifierItemName(orderValidationService.getModifierItemName(modifierItem, userLocale))
                    .price(modifierItemPrice)
                    .build());
            }
            
            responses.add(OrderedComboItemModifierResponse.builder()
                .modifierGroupId(modifierRequest.getModifierGroupId())
                .modifierGroupName(groupName)
                .modifierItems(modifierItemResponses)
                .build());
        }
        
        return responses;
    }

    /**
     * @return {@code true} when every mapping resolves to an item that is available for {@code restaurantId}
     */
    private boolean allComboItemMappingsAvailable(List<ComboItemMapping> mappings, UUID restaurantId) {
        for (ComboItemMapping itemMapping : mappings) {
            CategoryItemMapping categoryItemMapping = itemMapping.getCategoryItemMapping();
            if (categoryItemMapping == null || categoryItemMapping.getId() == null) {
                return false;
            }
            Boolean itemAvailable = orderedItemService.calculateItemAvailability(categoryItemMapping, restaurantId);
            if (!Boolean.TRUE.equals(itemAvailable)) {
                return false;
            }
        }
        return true;
    }

    /**
     * @return {@code true} when at least one mapping in the choice group is available for {@code restaurantId}
     */
    private boolean choiceGroupHasAvailableItem(List<ComboItemMapping> groupItems, UUID restaurantId) {
        for (ComboItemMapping itemMapping : groupItems) {
            CategoryItemMapping categoryItemMapping = itemMapping.getCategoryItemMapping();
            if (categoryItemMapping != null && categoryItemMapping.getId() != null) {
                Boolean itemAvailable = orderedItemService.calculateItemAvailability(categoryItemMapping, restaurantId);
                if (Boolean.TRUE.equals(itemAvailable)) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Calculates combo availability based on item availability in restaurant.
     * For FIXED combos: all items must be available.
     * For CHOICE combos: each choice group must have at least one available item.
     * For MIXED combos: FIXED groups require all items available, CHOICE groups require at least one available item.
     *
     * @param combo        the combo entity to check availability for
     * @param restaurantId the restaurant ID for availability checks
     * @param menuId       the menu ID (not used but kept for interface compatibility)
     * @return true if combo is available, false otherwise (defaults to true if unable to determine)
     */
    @Override
    public Boolean calculateComboAvailability(Combo combo, UUID restaurantId, UUID menuId) {
        if (combo == null || restaurantId == null) {
            return true; // Default to available if we can't determine
        }
        
        try {
            // Fetch combo groups and item mappings
            List<ComboGroup> comboGroups = comboGroupRepository.findByComboComboId(combo.getComboId());
            List<ComboItemMapping> comboItemMappings = comboRepository.findComboItemMappingsWithItems(combo.getComboId());
            
            if (combo.getType() == ComboType.FIXED) {
                return allComboItemMappingsAvailable(comboItemMappings, restaurantId);
            } else if (combo.getType() == ComboType.CHOICE) {
                // For CHOICE combo: each choice group must have at least one available item
                Map<UUID, List<ComboItemMapping>> groupItemsMap = comboItemMappings.stream()
                        .collect(Collectors.groupingBy(cim -> cim.getComboGroup().getComboGroupId()));
                
                for (ComboGroup group : comboGroups) {
                    if (group.getGroupType() == ComboGroupType.CHOICE) {
                        List<ComboItemMapping> groupItems = groupItemsMap.getOrDefault(group.getComboGroupId(), new ArrayList<>());
                        if (!choiceGroupHasAvailableItem(groupItems, restaurantId)) {
                            return false;
                        }
                    }
                }
                return true;
            } else if (combo.getType() == ComboType.MIXED) {
                // For MIXED combo: combine logic of FIXED and CHOICE groups
                Map<UUID, List<ComboItemMapping>> groupItemsMap = comboItemMappings.stream()
                        .collect(Collectors.groupingBy(cim -> cim.getComboGroup().getComboGroupId()));
                
                for (ComboGroup group : comboGroups) {
                    List<ComboItemMapping> groupItems = groupItemsMap.getOrDefault(group.getComboGroupId(), new ArrayList<>());
                    
                    if (group.getGroupType() == ComboGroupType.FIXED
                            && !allComboItemMappingsAvailable(groupItems, restaurantId)) {
                        return false;
                    } else if (group.getGroupType() == ComboGroupType.CHOICE
                            && !choiceGroupHasAvailableItem(groupItems, restaurantId)) {
                        return false;
                    }
                }
                return true;
            }
            
            return true; // Default to available for unknown combo types
        } catch (Exception e) {
            log.warn("Error checking combo availability for combo {} in restaurant {}: {}", 
                    combo.getComboId(), restaurantId, e.getMessage());
            return false;
        }
    }

    @Override
    public String applyReasonIfProvided(OrderedCombo orderedCombo, String reason) {
        String sanitizedReason = orderNotificationService.sanitizeReason(reason);
        if (sanitizedReason != null) {
            orderedCombo.setReason(sanitizedReason);
        }
        return sanitizedReason;
    }

    /**
     * Handles a combo cancellation request by creating a cancellation request record.
     * Validates that no pending cancellation request exists. Stores cancellation reason,
     * creates audit trail, and notifies managers. Sets request status to OPEN.
     *
     * @param orderedCombo     the ordered combo to cancel
     * @param payload          the status payload with cancellation reason
     * @param authenticatedUser the user requesting cancellation
     * @param userLocale       locale for localized error messages and notifications
     * @return ResponseDto with cancellation request status
     * @throws ResponseStatusException if cancellation request already pending or JSON processing fails
     */
    @Override
    public ResponseDto<ItemStatusResponseWrapper> handleComboCancellationRequest(OrderedCombo orderedCombo, ItemStatusPayload payload, User authenticatedUser, Locale userLocale) {
        // Check if there's already a pending cancellation request
        if (orderedCombo.getCancellationRequestStatus() == RequestStatus.OPEN) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    messageUtil.getMessage("item.cancellation.request.already.pending", userLocale));
        }
        
        String sanitizedReason = orderNotificationService.sanitizeReason(payload.getReason());
        applyReasonIfProvided(orderedCombo, sanitizedReason);

        // Create cancellation request data
        try {
            ObjectMapper objectMapper = new ObjectMapper();
            ItemCancellationRequestDto requestDto = ItemCancellationRequestDto.builder()
                    .cancellationReason(sanitizedReason)
                    .requestedStatus(payload.getItemStatus()) // Store the requested status (CANCELED)
                    .build();
            String requestData = objectMapper.writeValueAsString(requestDto);
            
            orderedCombo.setCancellationRequestStatus(RequestStatus.OPEN);
            orderedCombo.setCancellationRequestData(requestData);
            orderedCombo.setCancellationRequestedAt(OffsetDateTime.now(ZoneOffset.UTC));
            orderedCombo.setCancellationRequestedBy(authenticatedUser);
            // Clear previous review information when creating a new request
            orderedCombo.setCancellationComments(null);
            orderedCombo.setCancellationReviewedAt(null);
            orderedCombo.setCancellationReviewedBy(null);
            orderedCombo.setUpdatedAt(OffsetDateTime.now(ZoneOffset.UTC));
            orderedCombo.setUpdatedBy(authenticatedUser);
            
            orderedComboRepository.save(orderedCombo);

            // Create audit trail for combo cancellation request
            createComboCancellationAuditTrail(orderedCombo, authenticatedUser, sanitizedReason);

            // Notify managers about newly opened cancellation request
            notifyManagersAboutComboCancellationRequest(orderedCombo, userLocale);

            return ResponseDto.<ItemStatusResponseWrapper>builder()
                    .message(messageUtil.getMessage("item.cancellation.request.created", userLocale))
                    .data(ItemStatusResponseWrapper.builder()
                            .itemStatus(ItemStatusPayload.builder()
                                    .orderedComboId(orderedCombo.getId())
                                    .itemStatus(orderedCombo.getItemStatus())
                                    .reason(orderedCombo.getReason())
                                    .build())
                            .build())
                    .build();
                    
        } catch (JsonProcessingException e) {
            log.error("Error creating combo cancellation request data: {}", e.getMessage());
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    messageUtil.getMessage("item.cancellation.request.error", userLocale));
        }
    }

    /**
     * Create audit trail for combo cancellation request.
     *
     * @param orderedCombo the ordered combo for which the cancellation request was created
     * @param authenticatedUser the user who created the cancellation request
     * @param sanitizedReason the sanitized cancellation reason
     */
    private void createComboCancellationAuditTrail(OrderedCombo orderedCombo, User authenticatedUser, String sanitizedReason) {
        try {
            Restaurant restaurant = orderedCombo.getOrder() != null ? orderedCombo.getOrder().getRestaurant() : null;
            String auditReason = sanitizedReason != null && !sanitizedReason.isBlank() ? sanitizedReason : "N/A";
            auditTrailService.createAuditTrail(
                    authenticatedUser,
                    ActionType.CANCELLATION,
                    restaurant,
                    RequestStatus.OPEN,
                    null, // ipAddress
                    null, // userAgent
                    orderedCombo.getId(),
                    "ITEM",
                    String.format("Combo cancellation request created. Reason: %s", auditReason)
            );
        } catch (Exception e) {
            log.error("Failed to create audit trail for combo cancellation request: {}", e.getMessage(), e);
        }
    }

    /**
     * Notify managers about a newly opened combo cancellation request.
     *
     * @param orderedCombo the ordered combo for which the cancellation request was created
     * @param userLocale the user locale for notifications
     */
    private void notifyManagersAboutComboCancellationRequest(OrderedCombo orderedCombo, Locale userLocale) {
        try {
            UUID restaurantId = orderNotificationService.getRestaurantIdFromOrder(orderedCombo.getOrder());
            UUID managerRoleId = roleRepository.findByName("MANAGER").map(r -> r.getId()).orElse(null);
            if (managerRoleId != null) {
                List<User> managers = userRepository.findAllByRestaurantIdAndRoleIdAndIsDeletedFalse(restaurantId, managerRoleId);
                if (!managers.isEmpty()) {
                    notificationService.notifyComboCancellationRequestOpened(orderedCombo, managers, orderedCombo.getCancellationRequestedBy(), userLocale);
                    log.info("Combo cancellation request created for combo {} - managers notified", orderedCombo.getId());
                }
            }
        } catch (Exception e) {
            log.error("Failed to send manager notification for combo cancellation request: {}", e.getMessage(), e);
        }
    }
}

