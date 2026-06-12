package com.gulfnet.restaurantmanagement.service.impl;

import com.gulfnet.restaurantmanagement.config.LocalizationProperties;
import com.gulfnet.restaurantmanagement.config.OnlineCardPaymentProperties;
import com.gulfnet.restaurantmanagement.config.RestaurantChainConfigProperties;
import com.gulfnet.restaurantmanagement.service.OrderValidationService;
import com.gulfnet.restaurantmanagement.util.MessageUtil;
import com.gulfnet.shared_library.entity.*;
import com.gulfnet.shared_library.enums.*;
import com.gulfnet.shared_library.model.request.*;
import com.gulfnet.shared_library.model.response.dto.BxgyItemInfo;
import com.gulfnet.shared_library.repository.*;
import com.gulfnet.shared_library.util.CurrencyFormatter;
import com.gulfnet.shared_library.util.TranslationUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.OffsetTime;
import java.time.ZoneOffset;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class OrderValidationServiceImpl implements OrderValidationService {

    private final ItemRepository itemRepository;
    private final ComboRepository comboRepository;
    private final ModifierGroupRepository modifierGroupRepository;
    private final ModifierItemRepository modifierItemRepository;
    private final DiscountRepository discountRepository;
    private final MenuDiscountMappingRepository menuDiscountMappingRepository;
    private final RestaurantDiscountMappingRepository restaurantDiscountMappingRepository;
    private final CategoryItemMappingRepository categoryItemMappingRepository;
    private final RestaurantItemAvailabilityRepository restaurantItemAvailabilityRepository;
    private final ItemModifierGroupRepository itemModifierGroupRepository;
    private final TransactionRepository transactionRepository;
    private final SessionRepository sessionRepository;
    private final MenuRepository menuRepository;
    private final RestaurantRepository restaurantRepository;
    private final RestaurantTableRepository restaurantTableRepository;
    private final OrderRepository orderRepository;
    private final OrderedItemRepository orderedItemRepository;
    private final OrderedComboRepository orderedComboRepository;
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final DiscountBxgyItemRepository discountBxgyItemRepository;
    private final MenuCategoryMappingRepository menuCategoryMappingRepository;
    private final TableAssignmentRepository tableAssignmentRepository;
    private final RestaurantChainConfigProperties restaurantChainConfigProperties;
    private final OnlineCardPaymentProperties onlineCardPaymentProperties;
    private final LocalizationProperties localizationProperties;
    private final MessageUtil messageUtil;

    private static final String msgItemNameNotFound = "item.name.not.found";
    private static final String msgModifierItemNameNotFound = "modifier.item.name.not.found";

    /**
     * Validates that the order's transaction is not in COMPLETED status.
     * Throws an exception if the transaction is completed, preventing order updates.
     *
     * @param order      the order to validate
     * @param userLocale locale for localized error messages
     * @throws ResponseStatusException if the transaction is already completed
     */
    @Override
    public void validateTransactionNotCompleted(Order order, Locale userLocale) {
        if (order == null) {
            return;
        }
        
        Optional<Transaction> transactionOpt = transactionRepository.findByOrderId(order.getId());
        if (transactionOpt.isPresent()) {
            Transaction transaction = transactionOpt.get();
            if (transaction.getTransactionStatus() == TransactionStatus.COMPLETED) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        messageUtil.getMessage("order.cannot.update.transaction.completed", userLocale));
            }
        }
    }

    /**
     * Validates that recooking (transitioning from SERVED back to COOKING/PUSHED) is not allowed
     * for POSTPAID orders with completed, refunded, or partially refunded transactions.
     * Only applies to dine-in orders with POSTPAID payment system.
     *
     * @param order         the order containing the item
     * @param currentStatus the current status of the item
     * @param newStatus     the new status being transitioned to
     * @param userLocale    locale for localized error messages
     * @throws ResponseStatusException if recook is attempted on a completed POSTPAID transaction
     */
    @Override
    public void validateRecookNotAllowedForCompletedPostpaidTransaction(
            Order order,
            ItemStatus currentStatus,
            ItemStatus newStatus,
            Locale userLocale
    ) {
        if (order == null) {
            return;
        }

        // Only care about transitions from SERVED back to COOKING/PUSHED (recook scenarios)
        if (currentStatus != ItemStatus.SERVED ||
                (newStatus != ItemStatus.COOKING && newStatus != ItemStatus.PUSHED)) {
            return;
        }

        // Determine if this order should be treated as POSTPAID (dine-in with POSTPAID payment system)
        RestaurantChainConfigProperties.RestaurantChainData chainConfig = restaurantChainConfigProperties.getChain();
        PaymentSystemType paymentSystemType = chainConfig != null ? chainConfig.getPaymentType() : null;

        // TAKEAWAY orders are always treated as PREPAID regardless of chain configuration
        if (paymentSystemType != PaymentSystemType.POSTPAID ||
                order.getOrderType() == OrderType.TAKEAWAY) {
            return;
        }

        // For POSTPAID orders: block recook when transaction is already COMPLETED, REFUNDED, or PARTIALLY_REFUNDED
        Optional<Transaction> transactionOpt = transactionRepository.findByOrderId(order.getId());
        if (transactionOpt.isPresent()) {
            TransactionStatus status = transactionOpt.get().getTransactionStatus();
            if (status == TransactionStatus.COMPLETED
                    || status == TransactionStatus.REFUNDED
                    || status == TransactionStatus.PARTIALLY_REFUNDED) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        messageUtil.getMessage("transaction.completed.item.cannot.recook", userLocale));
            }
        }
    }

    /**
     * Validates and retrieves an order-level discount by ID.
     * Ensures the discount exists, is active, not deleted, and hasn't exceeded usage limits.
     *
     * @param discountId the UUID of the discount to validate and retrieve
     * @param userLocale locale for localized error messages
     * @return the validated {@link Discount} entity
     * @throws ResponseStatusException if discount is not found, inactive, deleted, or usage limit exceeded
     */
    @Override
    public Discount validateAndGetOrderDiscount(UUID discountId, Locale userLocale) {
        Discount discount = discountRepository.findById(discountId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        messageUtil.getMessage("discount.not.found", userLocale)));
        
        if (discount.getStatus() != EntityStatus.ACTIVE || discount.getIsDeleted()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    messageUtil.getMessage("discount.not.active", userLocale));
        }
        
        if (discount.getMaxUses() != null && discount.getMaxUses() > 0 && discount.getCurrentUsage() >= discount.getMaxUses()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    messageUtil.getMessage("discount.usage.limit.exceeded", userLocale));
        }
        
        return discount;
    }

    @Override
    public void validateOrderLevelDiscountType(Discount discount, Locale userLocale) {
        if (discount != null && discount.getAppliedTo() != AppliedTo.ORDER) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    messageUtil.getMessage("discount.not.order.level", userLocale));
        }
    }

    /**
     * Checks if an order discount is still valid for use.
     * Validates that the discount is active, not deleted, and hasn't exceeded usage limits.
     *
     * @param discount the discount to validate
     * @return {@code true} if the discount is valid, {@code false} otherwise
     */
    @Override
    public boolean isOrderDiscountStillValid(Discount discount) {
        return discount != null
                && discount.getStatus() == EntityStatus.ACTIVE
                && !Boolean.TRUE.equals(discount.getIsDeleted())
                && !(discount.getMaxUses() != null && discount.getMaxUses() > 0
                        && discount.getCurrentUsage() >= discount.getMaxUses());
    }

    /**
     * Validates a list of ordered item requests.
     * Checks that each item has required fields and validates item availability for the restaurant.
     *
     * @param orderedItems list of ordered item requests to validate
     * @param userLocale   locale for localized error messages
     * @throws ResponseStatusException if any item validation fails
     */
    @Override
    public void validateOrderItems(List<OrderedItemRequest> orderedItems, Locale userLocale) {
        // Allow null or empty orderedItems - users can place orders with only combos
        if (orderedItems == null || orderedItems.isEmpty()) {
            return;
        }

        for (OrderedItemRequest itemRequest : orderedItems) {
            Item item = itemRepository.findById(itemRequest.getItemId())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                            messageUtil.getMessage(msgItemNameNotFound, userLocale)));
            if (item.getStatus() != EntityStatus.ACTIVE) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        messageUtil.getMessage("item.not.active", userLocale));
            }
            if (itemRequest.getQuantity() == null || itemRequest.getQuantity() <= 0) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        messageUtil.getMessage("item.quantity.invalid", userLocale));
            }
            // Skip modifier validation for previously ordered items (those with orderedItemId)
            // Previously ordered items were already validated when the order was created,
            // so we don't need to re-validate their modifier availability
            if (itemRequest.getOrderedItemId() == null && itemRequest.getOrderedItemModifiers() != null) {
                for (OrderedItemModifierRequest modifierRequest : itemRequest.getOrderedItemModifiers()) {
                    validateOrderedItemModifier(modifierRequest, userLocale);
                }
            }
        }
    }

    /**
     * Validates item availability for a specific restaurant and menu.
     * Checks restaurant-specific availability overrides and throws an exception if any item is unavailable.
     *
     * @param orderedItems list of ordered item requests to validate
     * @param restaurantId the restaurant ID to check availability for
     * @param menuId        the menu ID containing the items
     * @param userLocale    locale for localized error messages
     * @throws ResponseStatusException if any item is unavailable for the restaurant
     */
    @Override
    public void validateItemAvailabilityForRestaurant(List<OrderedItemRequest> orderedItems, UUID restaurantId, UUID menuId, Locale userLocale) {
        if (orderedItems == null || orderedItems.isEmpty()) {
            return; // No items to validate
        }
        
        // Skip validation if restaurantId is not provided
        if (restaurantId == null) {
            log.info("RestaurantId not provided, skipping item availability validation");
            return;
        }

        // Get all item IDs from the request
        List<UUID> itemIds = orderedItems.stream()
                .map(OrderedItemRequest::getItemId)
                .collect(Collectors.toList());

        // Get CategoryItemMapping for all items in this menu
        List<CategoryItemMapping> categoryItemMappings = categoryItemMappingRepository.findByMenuCategoryMappingMenuId(menuId);
        
        // Filter mappings for requested items
        List<UUID> categoryItemMappingIds = categoryItemMappings.stream()
                .filter(mapping -> itemIds.contains(mapping.getItem().getId()))
                .map(CategoryItemMapping::getId)
                .collect(Collectors.toList());

        if (categoryItemMappingIds.isEmpty()) {
            log.warn("No category item mappings found for requested items in menu: {}", menuId);
            return;
        }

        // Check restaurant-specific availability overrides
        List<RestaurantItemAvailability> availabilityOverrides = restaurantItemAvailabilityRepository
                .findByRestaurantIdAndCategoryItemMappingIdIn(restaurantId, categoryItemMappingIds);

        // Create a map for quick lookup
        Map<UUID, Boolean> availabilityMap = availabilityOverrides.stream()
                .collect(Collectors.toMap(
                        availability -> availability.getCategoryItemMapping().getId(),
                        RestaurantItemAvailability::getIsAvailable
                ));

        // Validate each item
        for (OrderedItemRequest itemRequest : orderedItems) {
            UUID itemId = itemRequest.getItemId();
            
            // Find the CategoryItemMapping for this item
            Optional<CategoryItemMapping> categoryItemMapping = categoryItemMappings.stream()
                    .filter(mapping -> mapping.getItem().getId().equals(itemId))
                    .findFirst();

            if (categoryItemMapping.isPresent()) {
                UUID categoryItemMappingId = categoryItemMapping.get().getId();
                
                // Check if there's a restaurant-specific override
                if (availabilityMap.containsKey(categoryItemMappingId)) {
                    Boolean isAvailable = availabilityMap.get(categoryItemMappingId);
                    if (!isAvailable) {
                        log.error("Item {} is not available for restaurant {} (restaurant-specific override)", 
                                itemId, restaurantId);
                        throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                                messageUtil.getMessage("item.not.available.for.restaurant", userLocale));
                    }
                }
                // If no override exists, item is available by default (no validation needed)
            } else {
                log.warn("Item {} not found in menu {} category mappings", itemId, menuId);
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        messageUtil.getMessage("item.not.found.in.menu", userLocale));
            }
        }
        
        log.info("Item availability validation passed for restaurant {} with {} items", 
                restaurantId, orderedItems.size());
    }

    /**
     * Validates an ordered item modifier request.
     * Checks modifier group existence, status, modifier items, and validates SUBSTITUTE type requires single item.
     *
     * @param modifierRequest the modifier request to validate
     * @param userLocale      locale for localized error messages
     * @throws ResponseStatusException if modifier group/item not found, inactive, or validation fails
     */
    @Override
    public void validateOrderedItemModifier(OrderedItemModifierRequest modifierRequest, Locale userLocale) {
        ModifierGroup modifierGroup = modifierGroupRepository.findByIdAndIsDeletedFalse(modifierRequest.getModifierGroupId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        messageUtil.getMessage("modifier.group.not.found", userLocale)));
        if (modifierGroup.getStatus() != EntityStatus.ACTIVE) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    messageUtil.getMessage("modifier.group.inactive", userLocale));
        }
        
        // Check if modifier type is SUBSTITUTE and validate single modifier item
        if (modifierGroup.getModifierType() == ModifierType.SUBSTITUTE
                && modifierRequest.getModifierItemIds().size() != 1) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    messageUtil.getMessage("modifier.substitute.single.item.required", userLocale));
        }
        
        for (UUID modifierItemId : modifierRequest.getModifierItemIds()) {
            ModifierItem modifierItem = modifierItemRepository.findById(modifierItemId)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                            messageUtil.getMessage(msgModifierItemNameNotFound, userLocale)));
            if (modifierItem.getStatus() != EntityStatus.ACTIVE) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        messageUtil.getMessage("item.not.active", userLocale));
            }
            if (!modifierItem.getModifierGroup().getId().equals(modifierGroup.getId())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        messageUtil.getMessage("modifier.item.not.belongs.to.group", userLocale));
            }
            // Validate that ADD_ON modifier items have prices (SUBSTITUTE can have null prices)
            if (modifierGroup.getModifierType() == ModifierType.ADD_ON && modifierItem.getPrice() == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        messageUtil.getMessage("modifier.item.price.required", userLocale));
            }
        }
    }

    /**
     * Validates that an ordered item can be updated based on its current status.
     * Prevents updates when item is in COOKING, DELAYED, or READY status.
     *
     * @param currentItemStatus the current status of the ordered item
     * @param userLocale        locale for localized error messages
     * @throws ResponseStatusException if the item status does not allow updates
     */
    @Override
    public void validateItemStatusForUpdate(ItemStatus currentItemStatus, Locale userLocale) {
        if (currentItemStatus == ItemStatus.COOKING) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    messageUtil.getMessage("ordered.item.cannot.update.cooking", userLocale));
        } else if (currentItemStatus == ItemStatus.DELAYED) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    messageUtil.getMessage("ordered.item.cannot.update.delayed", userLocale));
        } else if (currentItemStatus == ItemStatus.READY) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    messageUtil.getMessage("ordered.item.cannot.update.ready", userLocale));
        } else if (currentItemStatus == ItemStatus.SERVED) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    messageUtil.getMessage("ordered.item.cannot.update.served", userLocale));
        } else if (currentItemStatus == ItemStatus.CANCELED) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    messageUtil.getMessage("ordered.item.cannot.update.canceled", userLocale));
        }
    }

    /**
     * Validates that a status transition from current to new status is allowed.
     * Enforces business rules: CANCELED is final, SERVED can only transition to specific statuses, etc.
     *
     * @param currentStatus the current status of the item
     * @param newStatus     the new status being transitioned to
     * @param userLocale    locale for localized error messages
     * @throws ResponseStatusException if the status transition is not allowed
     */
    @Override
    public void validateItemStatusTransition(ItemStatus currentStatus, ItemStatus newStatus, Locale userLocale) {
        // CANCELED status is always final - cannot change from CANCELED
        if (currentStatus == ItemStatus.CANCELED) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    messageUtil.getMessage("item.status.final", userLocale));
        }
        
        // SERVED status can be changed to CANCELED (via cancellation request), COOKING, or PUSHED
        // All other transitions from SERVED are blocked
        if (currentStatus == ItemStatus.SERVED && 
            newStatus != ItemStatus.CANCELED && 
            newStatus != ItemStatus.COOKING && 
            newStatus != ItemStatus.PUSHED) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    messageUtil.getMessage("item.status.final", userLocale));
        }
        
     }

    @Override
    public void validateOrderCombos(List<OrderedComboRequest> orderedCombos, UUID menuId, Locale userLocale) {
        validateOrderCombos(orderedCombos, menuId, userLocale, true);
    }
    
    @Override
    public void validateOrderCombos(List<OrderedComboRequest> orderedCombos, UUID menuId, Locale userLocale, boolean validateAvailability) {
        if (orderedCombos == null || orderedCombos.isEmpty()) {
            return;
        }
        
        for (OrderedComboRequest comboRequest : orderedCombos) {
            validateCombo(comboRequest, menuId, userLocale, validateAvailability);
        }
    }
    
    /**
     * Validates an ordered combo request including combo existence, availability, groups, items, and modifiers.
     *
     * @param comboRequest       the combo request to validate
     * @param menuId             the menu ID containing the combo
     * @param userLocale         locale for localized error messages
     * @param validateAvailability whether to validate combo availability (time-based and status checks)
     * @throws ResponseStatusException if validation fails
     */
    @Override
    public void validateCombo(OrderedComboRequest comboRequest, UUID menuId, Locale userLocale, boolean validateAvailability) {
        // 1. Validate combo exists
        Combo combo = comboRepository.findById(comboRequest.getComboId())
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                messageUtil.getMessage("combo.not.found", userLocale)));
        
        // 2. Validate combo is not deleted
        if (combo.getIsDeleted()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                messageUtil.getMessage("combo.deleted", userLocale));
        }
        
        // 3. Validate combo status is active
        if (combo.getStatus() != EntityStatus.ACTIVE) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                messageUtil.getMessage("combo.inactive", userLocale));
        }
        
        // 4. Validate combo belongs to the correct menu
        if (!combo.getMenu().getId().equals(menuId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                messageUtil.getMessage("combo.not.available.in.menu", userLocale));
        }
        
        // 5. Validate combo is available for current date/time (only if validateAvailability is true)
        if (validateAvailability) {
            validateComboAvailability(combo, userLocale);
        }
        
        // 6. Validate combo groups and selections
        // Skip modifier validation for previously ordered combos (those with orderedComboId)
        boolean skipModifierValidation = comboRequest.getOrderedComboId() != null;
        validateComboGroups(comboRequest.getComboGroups(), combo, userLocale, skipModifierValidation);
        
        // 7. Validate quantity
        if (comboRequest.getQuantity() <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                messageUtil.getMessage("combo.quantity.invalid", userLocale));
        }
    }

    /**
     * Validates combo availability based on date range, day of week, and time window.
     * Uses UTC timezone for all time-based validations.
     *
     * @param combo      the combo to validate availability for
     * @param userLocale locale for localized error messages
     * @throws ResponseStatusException if combo is not available (outside date range, wrong day, or outside time window)
     */
    @Override
    public void validateComboAvailability(Combo combo, Locale userLocale) {
        // Use UTC timezone for all validations
        LocalDateTime nowUtc = LocalDateTime.now(ZoneOffset.UTC);
        
        // Debug logging
        java.time.DayOfWeek javaDayOfWeek = nowUtc.getDayOfWeek();
        com.gulfnet.shared_library.enums.DayOfWeek customDayOfWeek = convertToCustomDayOfWeek(javaDayOfWeek);
        log.info("Validating combo availability for comboId: {}, javaDayOfWeek: {}, customDayOfWeek: {}, daysOfWeek: {}", 
                combo.getComboId(), javaDayOfWeek, customDayOfWeek, combo.getDaysOfWeek());
        
        // Check if combo is within valid date range (using UTC)
        if (combo.getValidFrom() != null && nowUtc.isBefore(combo.getValidFrom().toLocalDateTime())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                messageUtil.getMessage("combo.not.available.yet", userLocale));
        }
        
        if (combo.getValidTo() != null && nowUtc.isAfter(combo.getValidTo().toLocalDateTime())) {
            String comboName = getComboName(combo, userLocale);
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                messageUtil.getMessage("combo.expired", userLocale, comboName));
        }
        
        // Check if combo is available for current day of week
        // If daysOfWeek is null or empty, combo is available all days
        if (combo.getDaysOfWeek() != null && !combo.getDaysOfWeek().isEmpty() && !combo.getDaysOfWeek().contains(customDayOfWeek)) {
            log.error("Combo {} not available today. Current day: {}, Available days: {}", 
                     combo.getComboId(), customDayOfWeek, combo.getDaysOfWeek());
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                messageUtil.getMessage("combo.not.available.today", userLocale));
        }
        
        // Check if combo is available for current time (using UTC)
        if (combo.getStartTime() != null && combo.getEndTime() != null) {
            // Use UTC time for comparison
            LocalTime currentTimeUtc = nowUtc.toLocalTime();
            
            // Use the raw times from database - they are already in UTC
            LocalTime startTimeUtc = combo.getStartTime().toLocalTime();
            LocalTime endTimeUtc = combo.getEndTime().toLocalTime();
            
            log.info("Time validation (UTC) - Current time: {}, Start time: {}, End time: {}, Combo: {}", 
                    currentTimeUtc, startTimeUtc, endTimeUtc, combo.getComboId());
            log.info("Raw combo times - Start: {}, End: {}", combo.getStartTime(), combo.getEndTime());
            
            // Check if time range is valid (end time should be after start time)
            if (endTimeUtc.isBefore(startTimeUtc)) {
                log.warn("Combo {} has invalid time range (end before start). Skipping time validation. Start: {}, End: {}", 
                        combo.getComboId(), startTimeUtc, endTimeUtc);
                // Skip time validation for invalid ranges - assume available 24/7
            } else {
                // Only validate if time range is valid
                if (currentTimeUtc.isBefore(startTimeUtc) || currentTimeUtc.isAfter(endTimeUtc)) {
                    log.error("Combo {} not available at this time. Current: {}, Range: {} - {}", 
                             combo.getComboId(), currentTimeUtc, startTimeUtc, endTimeUtc);
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        messageUtil.getMessage("combo.not.available.now", userLocale));
                }
            }
        }
    }

    /**
     * Validates combo groups for an ordered combo.
     * Delegates to the overloaded method with skipModifierValidation set to false.
     *
     * @param comboGroups list of combo group requests to validate
     * @param combo       the combo entity containing the groups
     * @param userLocale  locale for localized error messages
     */
    @Override
    public void validateComboGroups(List<OrderedComboGroupRequest> comboGroups, Combo combo, Locale userLocale) {
        validateComboGroups(comboGroups, combo, userLocale, false);
    }

    /**
     * Validates combo groups for an ordered combo, checking group existence, required groups,
     * item selection requirements, and optionally modifier validation.
     *
     * @param comboGroups           list of combo group requests to validate
     * @param combo                 the combo entity containing the groups
     * @param userLocale            locale for localized error messages
     * @param skipModifierValidation whether to skip modifier validation (for previously ordered items)
     * @throws ResponseStatusException if validation fails
     */
    @Override
    public void validateComboGroups(List<OrderedComboGroupRequest> comboGroups, Combo combo, Locale userLocale, boolean skipModifierValidation) {
        Map<UUID, ComboGroup> comboGroupMap = combo.getComboGroups().stream()
            .collect(Collectors.toMap(ComboGroup::getComboGroupId, Function.identity()));
        
        // For FIXED type combos, no combo groups should be provided as items are predetermined
        if (combo.getType() == ComboType.FIXED) {
            if (comboGroups != null && !comboGroups.isEmpty()) {
                log.warn("FIXED combo {} should not have combo groups provided. Items are predetermined.", combo.getComboId());
                // For FIXED combos, we'll ignore the provided groups and use predetermined items
            }
            return; // Skip group validation for FIXED combos
        }
        
        // For MIXED combos, validate all groups are present (but handle FIXED groups differently)
        if (combo.getType() == ComboType.MIXED) {
            assertOrderedComboGroupIdsMatchComboDefinition(comboGroups, combo, userLocale);
            return; // Skip further validation for MIXED combos
        }
        
        // For CHOICE combos, validate all required groups are present
        assertOrderedComboGroupIdsMatchComboDefinition(comboGroups, combo, userLocale);
        
        for (OrderedComboGroupRequest groupRequest : comboGroups) {
            ComboGroup comboGroup = comboGroupMap.get(groupRequest.getComboGroupId());
            if (comboGroup == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    messageUtil.getMessage("combo.group.not.found", userLocale));
            }
            
            // Validate min/max selections
            int selectedCount = groupRequest.getOrderedItems().size();
            if (selectedCount < comboGroup.getMinSelect()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    messageUtil.getMessage("combo.group.min.selection.not.met", userLocale));
            }
            
            if (selectedCount > comboGroup.getMaxSelect()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    messageUtil.getMessage("combo.group.max.selection.exceeded", userLocale));
            }
            
            // Validate items exist in combo group
            validateComboItems(groupRequest.getOrderedItems(), comboGroup, userLocale, skipModifierValidation);
        }
    }

    /**
     * Ensures the combo groups in the request exactly match the combo definition (same ids, no extras or omissions).
     *
     * @throws ResponseStatusException with {@code BAD_REQUEST} when the id sets differ
     */
    private void assertOrderedComboGroupIdsMatchComboDefinition(
            List<OrderedComboGroupRequest> comboGroups,
            Combo combo,
            Locale userLocale) {
        Set<UUID> requiredGroupIds = combo.getComboGroups().stream()
                .map(ComboGroup::getComboGroupId)
                .collect(Collectors.toSet());

        Set<UUID> providedGroupIds = comboGroups.stream()
                .map(OrderedComboGroupRequest::getComboGroupId)
                .collect(Collectors.toSet());

        if (!providedGroupIds.equals(requiredGroupIds)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    messageUtil.getMessage("combo.groups.mismatch", userLocale));
        }
    }

    /**
     * Validates combo items for a combo group.
     * Delegates to the overloaded method with skipModifierValidation set to false.
     *
     * @param comboItems list of combo item requests to validate
     * @param comboGroup the combo group entity containing the items
     * @param userLocale locale for localized error messages
     */
    @Override
    public void validateComboItems(List<OrderedComboItemRequest> comboItems, ComboGroup comboGroup, Locale userLocale) {
        validateComboItems(comboItems, comboGroup, userLocale, false);
    }

    /**
     * Validates combo items for a combo group, checking item availability in the group,
     * item status, and optionally modifier validation.
     *
     * @param comboItems            list of combo item requests to validate
     * @param comboGroup            the combo group entity containing the items
     * @param userLocale            locale for localized error messages
     * @param skipModifierValidation whether to skip modifier validation (for previously ordered items)
     * @throws ResponseStatusException if validation fails
     */
    @Override
    public void validateComboItems(List<OrderedComboItemRequest> comboItems, ComboGroup comboGroup, Locale userLocale, boolean skipModifierValidation) {
        Set<UUID> availableItemIds = comboGroup.getComboItemMappings().stream()
            .map(mapping -> mapping.getCategoryItemMapping().getItem().getId())
            .collect(Collectors.toSet());
        
        for (OrderedComboItemRequest itemRequest : comboItems) {
            // Validate item exists in combo group
            if (!availableItemIds.contains(itemRequest.getItemId())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    messageUtil.getMessage("combo.item.not.available", userLocale));
            }
            
            // Validate item is active
            Item item = itemRepository.findById(itemRequest.getItemId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                    messageUtil.getMessage(msgItemNameNotFound, userLocale)));
            
            if (item.getStatus() != EntityStatus.ACTIVE) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    messageUtil.getMessage("combo.item.inactive", userLocale));
            }
            
            if (item.getIsDeleted()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    messageUtil.getMessage("combo.item.deleted", userLocale));
            }
            
            if (item.getOutOfStock()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    messageUtil.getMessage("combo.item.out.of.stock", userLocale));
            }
            
            // Skip modifier validation for previously ordered combo items
            // Previously ordered combo items were already validated when the combo was created,
            // so we don't need to re-validate their modifier availability
            if (!skipModifierValidation && itemRequest.getOrderedItemModifiers() != null) {
                validateComboItemModifiers(itemRequest.getOrderedItemModifiers(), item, userLocale);
            }
        }
    }

    /**
     * Validates that the requested modifier groups and modifier items are applicable to the given combo item.
     * <p>
     * Checks:
     * - modifier group is assigned to the item
     * - modifier group and modifier items exist and are ACTIVE / not deleted
     * - requested modifier items belong to the requested modifier group
     *
     * @param modifiers requested modifier selections for a combo item (non-null/iterable)
     * @param item combo item being validated (required)
     * @param userLocale locale for localized error messages (may be {@code null})
     * @throws ResponseStatusException when a requested group/item is invalid or unavailable
     */
    @Override
    public void validateComboItemModifiers(List<OrderedComboItemModifierRequest> modifiers, Item item, Locale userLocale) {
        // Get available modifier groups for this item
        List<ItemModifierGroup> itemModifierGroups = itemModifierGroupRepository.findByItemIdAndIsDeletedFalse(item.getId());
        Set<UUID> availableModifierGroupIds = itemModifierGroups.stream()
            .map(ItemModifierGroup::getModifierGroup)
            .map(ModifierGroup::getId)
            .collect(Collectors.toSet());
        
        for (OrderedComboItemModifierRequest modifierRequest : modifiers) {
            // Validate modifier group is assigned to item
            if (!availableModifierGroupIds.contains(modifierRequest.getModifierGroupId())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    messageUtil.getMessage("modifier.group.not.assigned.to.item", userLocale));
            }
            
            ModifierGroup modifierGroup = modifierGroupRepository.findById(modifierRequest.getModifierGroupId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                    messageUtil.getMessage("modifier.group.not.found", userLocale)));
            
            if (modifierGroup.getStatus() != EntityStatus.ACTIVE) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    messageUtil.getMessage("modifier.group.inactive", userLocale));
            }
            
            // Validate modifier items - use repository to get all non-deleted modifier items for the group
            // This ensures we get all active items even if the lazy collection isn't loaded
            List<ModifierItem> availableModifierItems = modifierItemRepository.findByModifierGroup_IdAndIsDeletedFalse(modifierRequest.getModifierGroupId());
            Set<UUID> availableModifierItemIds = availableModifierItems.stream()
                .map(ModifierItem::getId)
                .collect(Collectors.toSet());
            
            for (UUID modifierItemId : modifierRequest.getModifierItemIds()) {
                if (!availableModifierItemIds.contains(modifierItemId)) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        messageUtil.getMessage("modifier.item.not.available", userLocale));
                }
                
                ModifierItem modifierItem = modifierItemRepository.findById(modifierItemId)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        messageUtil.getMessage(msgModifierItemNameNotFound, userLocale)));
                
                if (modifierItem.getStatus() != EntityStatus.ACTIVE) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        messageUtil.getMessage("modifier.item.inactive", userLocale));
                }
                
                // Validate that modifier item belongs to the modifier group
                if (!modifierItem.getModifierGroup().getId().equals(modifierGroup.getId())) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        messageUtil.getMessage("modifier.item.not.belongs.to.group", userLocale));
                }
            }
        }
    }

    /**
     * Validates whether a combo can be updated based on its current workflow status.
     *
     * @param currentItemStatus current status of the ordered combo (required)
     * @param userLocale locale for localized error messages (may be {@code null})
     * @throws ResponseStatusException if the combo is already in a non-updatable status (e.g. COOKING/READY/SERVED/CANCELED)
     */
    @Override
    public void validateComboStatusForUpdate(ItemStatus currentItemStatus, Locale userLocale) {
        if (currentItemStatus == ItemStatus.COOKING) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    messageUtil.getMessage("ordered.combo.cannot.update.cooking", userLocale));
        } else if (currentItemStatus == ItemStatus.DELAYED) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    messageUtil.getMessage("ordered.combo.cannot.update.delayed", userLocale));
        } else if (currentItemStatus == ItemStatus.READY) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    messageUtil.getMessage("ordered.combo.cannot.update.ready", userLocale));
        } else if (currentItemStatus == ItemStatus.SERVED) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    messageUtil.getMessage("ordered.combo.cannot.update.served", userLocale));
        } else if (currentItemStatus == ItemStatus.CANCELED) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    messageUtil.getMessage("ordered.combo.cannot.update.canceled", userLocale));
        }
    }

    /**
     * Validates "GET" (free) quantities for BXGY discounts across a set of ordered items.
     * <p>
     * For each BXGY discount, computes:
     * \(maxFree = floor(totalBuyQty / buyQty) * getQty\)
     * and rejects requests where the total requested free quantity exceeds this maximum.
     *
     * @param orderedItems ordered items that may carry BXGY discount ids/roles (may be {@code null} or empty)
     * @param menuId menu id used to validate discount applicability (may be {@code null})
     * @param userLocale locale for localized error messages (may be {@code null})
     * @throws ResponseStatusException when requested free quantity exceeds the computed maximum
     */
    @Override
    public void validateBxgyGetItemsQuantity(
            List<OrderedItemRequest> orderedItems,
            UUID menuId,
            Locale userLocale) {
        
        if (orderedItems == null || orderedItems.isEmpty()) {
            return; // No items to validate
        }
        
        // Group items by discount
        Map<UUID, List<OrderedItemRequest>> buyItemsByDiscount = new HashMap<>();
        Map<UUID, List<OrderedItemRequest>> getItemsByDiscount = new HashMap<>();
        Map<UUID, Discount> discountCache = new HashMap<>();
        
        for (OrderedItemRequest itemRequest : orderedItems) {
            if (itemRequest.getDiscountIds() != null && !itemRequest.getDiscountIds().isEmpty()) {
                for (UUID discountId : itemRequest.getDiscountIds()) {
                    accumulateBxgyItemsForDiscountId(
                            discountId, itemRequest, menuId, discountCache, buyItemsByDiscount, getItemsByDiscount, userLocale);
                }
            }
        }
        
        // Validate get item quantities using floor formula
        for (UUID discountId : buyItemsByDiscount.keySet()) {
            List<OrderedItemRequest> buyItems = buyItemsByDiscount.get(discountId);
            List<OrderedItemRequest> getItems = getItemsByDiscount.get(discountId);
            Discount discount = discountCache.get(discountId);
            
            // Only validate if there are get items
            if (getItems == null || getItems.isEmpty()) {
                continue;
            }
            
            // Validate requested FREE quantities using floor formula
            // Formula for maximum FREE items:
            //   max_free_quantity = floor(total_buy_quantity / buy_quantity) * get_quantity
            int buyQuantity = discount.getBuyQuantity() != null ? discount.getBuyQuantity() : 1;
            int getQuantity = discount.getGetQuantity() != null ? discount.getGetQuantity() : 1;
            
            // Calculate total buy quantity
            int totalBuyQuantity = buyItems.stream()
                .mapToInt(OrderedItemRequest::getQuantity)
                .sum();
            
            // Calculate total requested free quantity from all GET items (treat null as 0)
            int totalRequestedFreeQuantity = getItems.stream()
                .map(OrderedItemRequest::getFreeQuantity)
                .filter(Objects::nonNull)
                .mapToInt(Integer::intValue)
                .sum();
            
            int maxAllowedFreeQuantity = (int) Math.floor((double) totalBuyQuantity / buyQuantity) * getQuantity;
            
            log.debug("BXGY freeQuantity validation - discountId: {}, totalBuyQuantity: {}, buyQuantity: {}, getQuantity: {}, totalRequestedFreeQuantity: {}, maxAllowedFreeQuantity: {}", 
                discountId, totalBuyQuantity, buyQuantity, getQuantity, totalRequestedFreeQuantity, maxAllowedFreeQuantity);
            
            if (totalRequestedFreeQuantity > maxAllowedFreeQuantity) {
                throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    messageUtil.getMessage("bxgy.free.quantity.exceeds.maximum", userLocale, 
                        totalRequestedFreeQuantity, maxAllowedFreeQuantity)
                );
            }
        }
    }

    /**
     * When {@code discountId} resolves to an active BXGY discount for {@code menuId}, appends {@code itemRequest}
     * to the buy- or get-item lists keyed by discount id.
     *
     * @param discountCache      reuses loaded {@link Discount} entities per id
     * @param buyItemsByDiscount collector for items flagged as buy side
     * @param getItemsByDiscount collector for items flagged as get (free) side
     */
    private void accumulateBxgyItemsForDiscountId(
            UUID discountId,
            OrderedItemRequest itemRequest,
            UUID menuId,
            Map<UUID, Discount> discountCache,
            Map<UUID, List<OrderedItemRequest>> buyItemsByDiscount,
            Map<UUID, List<OrderedItemRequest>> getItemsByDiscount,
            Locale userLocale) {
        Discount discount = discountCache.computeIfAbsent(discountId, id ->
                discountRepository.findById(id)
                        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                                messageUtil.getMessage("discount.not.found", userLocale))));
        if (discount.getDiscountType() != DiscountType.BXGY) {
            return;
        }
        if (!isDiscountValidForMenuAndTime(menuId, discountId)) {
            return;
        }
        if (Boolean.TRUE.equals(itemRequest.getIsBuyItem())) {
            buyItemsByDiscount.computeIfAbsent(discountId, k -> new ArrayList<>()).add(itemRequest);
        }
        if (Boolean.TRUE.equals(itemRequest.getIsGetItem())) {
            getItemsByDiscount.computeIfAbsent(discountId, k -> new ArrayList<>()).add(itemRequest);
        }
    }

    /**
     * Checks whether a discount is valid for the given menu at the current time (UTC).
     * <p>
     * This overload keeps backward compatibility by delegating to the restaurant-aware overload with
     * a {@code null} restaurant id.
     *
     * @param menuId menu id (may be {@code null})
     * @param discountId discount id (required)
     * @return {@code true} if the discount is considered active/applicable; {@code false} otherwise
     */
    @Override
    public boolean isDiscountValidForMenuAndTime(UUID menuId, UUID discountId) {
        // Delegate to the overloaded method with null restaurantId for backward compatibility
        return isDiscountValidForMenuAndTime(menuId, discountId, null);
    }

    /**
     * Checks whether a discount is valid for the given menu and (optionally) restaurant at the current time (UTC).
     * <p>
     * Validation includes:
     * - discount exists, ACTIVE, and not deleted
     * - usage limit not exceeded
     * - mapping-based validity windows/time windows/day-of-week, preferring {@code RestaurantDiscountMapping} when
     *   {@code restaurantId} is provided, otherwise falling back to {@code MenuDiscountMapping}
     * <p>
     * If no mapping is found, the method returns {@code true} for backward compatibility.
     *
     * @param menuId menu id to check {@code MenuDiscountMapping} (may be {@code null})
     * @param discountId discount id (required)
     * @param restaurantId restaurant id to check {@code RestaurantDiscountMapping} first (may be {@code null})
     * @return {@code true} if the discount is considered active/applicable; {@code false} otherwise
     */
    @Override
    public boolean isDiscountValidForMenuAndTime(UUID menuId, UUID discountId, UUID restaurantId) {
        log.debug("Validating discount {} for menu {} and restaurant {} at current time", discountId, menuId, restaurantId);
        
        // Check if discount exists
        Optional<Discount> discountOpt = discountRepository.findById(discountId);
        if (discountOpt.isEmpty()) {
            log.warn("Discount not found: {}", discountId);
            return false;
        }
        
        Discount discount = discountOpt.get();
        
        // First check: discount is active and not deleted
        if (discount.getIsDeleted() || discount.getStatus() != EntityStatus.ACTIVE) {
            log.debug("Discount {} is deleted or not active. isDeleted={}, status={}", 
                    discount.getId(), discount.getIsDeleted(), discount.getStatus());
            return false;
        }
        
        // Usage limit check: consider expired when maxUses reached
        // maxUses = 0 means unlimited, so only check if maxUses > 0
        if (discount.getMaxUses() != null && discount.getMaxUses() > 0 && discount.getCurrentUsage() >= discount.getMaxUses()) {
            log.debug("Discount {} has reached max usage limit", discount.getId());
            return false;
        }
        
        OffsetDateTime nowUtc = OffsetDateTime.now(ZoneOffset.UTC).truncatedTo(java.time.temporal.ChronoUnit.SECONDS);
        
        // Check restaurant discount mapping first if restaurantId is provided
        if (restaurantId != null) {
            RestaurantDiscountId restaurantDiscountId = new RestaurantDiscountId();
            restaurantDiscountId.setRestaurantId(restaurantId);
            restaurantDiscountId.setDiscountId(discount.getId());
            Optional<RestaurantDiscountMapping> restaurantDiscountMappingOpt = restaurantDiscountMappingRepository.findById(restaurantDiscountId);
            
            log.debug("Checking RestaurantDiscountMapping for discount {} and restaurant {}: {}", 
                    discount.getId(), restaurantId, restaurantDiscountMappingOpt.isPresent() ? "FOUND" : "NOT FOUND");
            
            if (restaurantDiscountMappingOpt.isPresent()) {
                RestaurantDiscountMapping restaurantDiscountMapping = restaurantDiscountMappingOpt.get();
                
                // Check status - if INACTIVE, discount is not valid for this restaurant
                if (restaurantDiscountMapping.getStatus() != null && restaurantDiscountMapping.getStatus() != EntityStatus.ACTIVE) {
                    log.debug("Restaurant discount mapping for discount {} is not active", discount.getId());
                    return false;
                }
                
                // Check restaurant-specific validity period (using UTC)
                if (restaurantDiscountMapping.getValidFrom() != null && nowUtc.isBefore(restaurantDiscountMapping.getValidFrom())) {
                    log.debug("Discount {} validFrom {} is after current time {}", discount.getId(), restaurantDiscountMapping.getValidFrom(), nowUtc);
                    return false;
                }
                
                if (restaurantDiscountMapping.getValidTo() != null && nowUtc.isAfter(restaurantDiscountMapping.getValidTo())) {
                    log.debug("Discount {} validTo {} is before current time {}", discount.getId(), restaurantDiscountMapping.getValidTo(), nowUtc);
                    return false;
                }
                
                // Check restaurant-specific time restrictions (using UTC)
                if (restaurantDiscountMapping.getStartTime() != null && restaurantDiscountMapping.getEndTime() != null) {
                    OffsetTime currentTime = nowUtc.toOffsetTime();
                    OffsetTime startTime = restaurantDiscountMapping.getStartTime();
                    OffsetTime endTime = restaurantDiscountMapping.getEndTime();
                    
                    boolean isTimeValid = false;
                    if (startTime.isBefore(endTime) || startTime.equals(endTime)) {
                        // Normal case: start <= end (e.g., 12:00 to 18:00 or 12:00 to 12:00 for 24-hour)
                        isTimeValid = !currentTime.isBefore(startTime) && !currentTime.isAfter(endTime);
                    } else {
                        // Overnight case: start > end (e.g., 23:00 to 02:00)
                        // Active if currentTime >= startTime OR currentTime <= endTime
                        isTimeValid = !currentTime.isBefore(startTime) || !currentTime.isAfter(endTime);
                    }
                    
                    if (!isTimeValid) {
                        log.debug("Discount {} time restriction not met. Current: {}, Start: {}, End: {}", 
                            discount.getId(), currentTime, startTime, endTime);
                        return false;
                    }
                }
                
                // Check restaurant-specific day-of-week restrictions
                if (restaurantDiscountMapping.getDaysOfWeek() != null && !restaurantDiscountMapping.getDaysOfWeek().isEmpty()) {
                    com.gulfnet.shared_library.enums.DayOfWeek currentDay = convertToDayOfWeek(nowUtc.getDayOfWeek());
                    if (!restaurantDiscountMapping.getDaysOfWeek().contains(currentDay)) {
                        log.debug("Discount {} not valid on current day {}", discount.getId(), currentDay);
                        return false;
                    }
                }
                
                // Restaurant mapping exists and is valid
                log.debug("Discount {} is active for restaurant {}", discount.getId(), restaurantId);
                return true;
            }
            // If restaurantId is provided but no RestaurantDiscountMapping exists, fall through to check MenuDiscountMapping
        }
        
        // Check menu discount mapping (either restaurantId is null or RestaurantDiscountMapping doesn't exist)
        if (menuId != null) {
            MenuDiscountId menuDiscountId = new MenuDiscountId(menuId, discountId);
            Optional<MenuDiscountMapping> menuDiscountMappingOpt = menuDiscountMappingRepository.findById(menuDiscountId);
            
            log.debug("Checking MenuDiscountMapping for discount {} and menu {}: {}", 
                    discount.getId(), menuId, menuDiscountMappingOpt.isPresent() ? "FOUND" : "NOT FOUND");
            
            if (menuDiscountMappingOpt.isPresent()) {
                MenuDiscountMapping menuDiscountMapping = menuDiscountMappingOpt.get();
                
                // Check menu-specific validity period (using UTC)
                if (menuDiscountMapping.getValidFrom() != null && nowUtc.isBefore(menuDiscountMapping.getValidFrom())) {
                    log.debug("Menu discount {} validFrom {} is after current time {}", discount.getId(), menuDiscountMapping.getValidFrom(), nowUtc);
                    return false;
                }
                
                if (menuDiscountMapping.getValidTo() != null && nowUtc.isAfter(menuDiscountMapping.getValidTo())) {
                    log.debug("Menu discount {} validTo {} is before current time {}", discount.getId(), menuDiscountMapping.getValidTo(), nowUtc);
                    return false;
                }
                
                // Check menu-specific time restrictions (using UTC)
                if (menuDiscountMapping.getStartTime() != null && menuDiscountMapping.getEndTime() != null) {
                    OffsetTime currentTime = nowUtc.toOffsetTime();
                    OffsetTime startTime = menuDiscountMapping.getStartTime();
                    OffsetTime endTime = menuDiscountMapping.getEndTime();
                    
                    boolean isTimeValid = false;
                    if (startTime.isBefore(endTime) || startTime.equals(endTime)) {
                        // Normal case: start <= end (e.g., 12:00 to 18:00 or 12:00 to 12:00 for 24-hour)
                        isTimeValid = !currentTime.isBefore(startTime) && !currentTime.isAfter(endTime);
                    } else {
                        // Overnight case: start > end (e.g., 23:00 to 02:00)
                        // Active if currentTime >= startTime OR currentTime <= endTime
                        isTimeValid = !currentTime.isBefore(startTime) || !currentTime.isAfter(endTime);
                    }
                    
                    if (!isTimeValid) {
                        log.debug("Menu discount {} time restriction not met. Current: {}, Start: {}, End: {}", 
                            discount.getId(), currentTime, startTime, endTime);
                        return false;
                    }
                }
                
                // Check menu-specific day-of-week restrictions
                if (menuDiscountMapping.getDaysOfWeek() != null && !menuDiscountMapping.getDaysOfWeek().isEmpty()) {
                    com.gulfnet.shared_library.enums.DayOfWeek currentDay = convertToDayOfWeek(nowUtc.getDayOfWeek());
                    log.debug("DAY VALIDATION - CurrentDay: {}, AllowedDays: {}", currentDay, menuDiscountMapping.getDaysOfWeek());
                    if (!menuDiscountMapping.getDaysOfWeek().contains(currentDay)) {
                        log.debug("Menu discount {} not valid on current day {}", discount.getId(), currentDay);
                        return false;
                    }
                }
                
                // Menu mapping exists and is valid
                log.debug("Discount {} is active for menu {}", discount.getId(), menuId);
                return true;
            }
        }
        
        // If no mapping found, consider discount active (backward compatibility)
        log.debug("No discount mapping found for discount {}, menu {}, restaurant {}. Considering active (backward compatibility).", 
            discount.getId(), menuId, restaurantId);
        return true;
    }

    /**
     * Maps Java's {@link java.time.DayOfWeek} to the shared-library {@code DayOfWeek} enum used in discount mappings.
     *
     * @param javaDayOfWeek Java day of week (required)
     * @return equivalent shared-library day of week
     * @throws IllegalArgumentException if an unexpected day value is encountered
     */
    private com.gulfnet.shared_library.enums.DayOfWeek convertToDayOfWeek(java.time.DayOfWeek javaDayOfWeek) {
        switch (javaDayOfWeek) {
            case SUNDAY: return com.gulfnet.shared_library.enums.DayOfWeek.SUNDAY;
            case MONDAY: return com.gulfnet.shared_library.enums.DayOfWeek.MONDAY;
            case TUESDAY: return com.gulfnet.shared_library.enums.DayOfWeek.TUESDAY;
            case WEDNESDAY: return com.gulfnet.shared_library.enums.DayOfWeek.WEDNESDAY;
            case THURSDAY: return com.gulfnet.shared_library.enums.DayOfWeek.THURSDAY;
            case FRIDAY: return com.gulfnet.shared_library.enums.DayOfWeek.FRIDAY;
            case SATURDAY: return com.gulfnet.shared_library.enums.DayOfWeek.SATURDAY;
            default: 
                throw new IllegalArgumentException("Unexpected DayOfWeek value: " + javaDayOfWeek);
        }
    }

    private com.gulfnet.shared_library.enums.DayOfWeek convertToCustomDayOfWeek(java.time.DayOfWeek javaDayOfWeek) {
        return switch (javaDayOfWeek) {
            case SUNDAY -> com.gulfnet.shared_library.enums.DayOfWeek.SUNDAY;
            case MONDAY -> com.gulfnet.shared_library.enums.DayOfWeek.MONDAY;
            case TUESDAY -> com.gulfnet.shared_library.enums.DayOfWeek.TUESDAY;
            case WEDNESDAY -> com.gulfnet.shared_library.enums.DayOfWeek.WEDNESDAY;
            case THURSDAY -> com.gulfnet.shared_library.enums.DayOfWeek.THURSDAY;
            case FRIDAY -> com.gulfnet.shared_library.enums.DayOfWeek.FRIDAY;
            case SATURDAY -> com.gulfnet.shared_library.enums.DayOfWeek.SATURDAY;
        };
    }

    // Method moved to public interface - removed duplicate private implementation

    // ==================== SESSION VALIDATION METHODS ====================

    @Override
    public Session validateAndGetSession(UUID sessionId, Locale userLocale) {
        return sessionRepository.findById(sessionId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        messageUtil.getMessage("session.not.found", userLocale)));
    }

    @Override
    public void validateSessionNotExpired(Session session, Locale userLocale) {
        java.time.OffsetDateTime now = java.time.OffsetDateTime.now(java.time.ZoneOffset.UTC);
        if (session.getExpiredAt() != null && session.getExpiredAt().isBefore(now)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    messageUtil.getMessage("session.expired", userLocale));
        }
        if (session.getTokenExpiryAt() == null || session.getTokenExpiryAt().isBefore(now)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    messageUtil.getMessage("session.token.expired", userLocale));
        }
    }

    @Override
    public void validateSingleOrderPerSession(UUID sessionId, Locale userLocale) {
        if (!orderRepository.findBySessionIdOrderByCreatedAtDesc(sessionId).isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    messageUtil.getMessage("session.already.has.order", userLocale));
        }
    }

    // ==================== MENU VALIDATION METHODS ====================

    @Override
    public Menu validateAndGetMenu(UUID menuId, Locale userLocale) {
        return menuRepository.findById(menuId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        messageUtil.getMessage("menu.not.found", userLocale)));
    }

    // ==================== RESTAURANT VALIDATION METHODS ====================

    @Override
    public Restaurant validateAndGetRestaurant(UUID restaurantId, Locale userLocale) {
        return restaurantRepository.findById(restaurantId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        messageUtil.getMessage("restaurant.not.found", userLocale)));
    }

    @Override
    public void validateRestaurantActive(Restaurant restaurant, Locale userLocale) {
        if (restaurant.getStatus() != EntityStatus.ACTIVE) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    messageUtil.getMessage("restaurant.not.active", userLocale));
        }
    }

    // ==================== RESTAURANT TABLE VALIDATION METHODS ====================

    @Override
    public RestaurantTable validateAndGetRestaurantTable(UUID tableId, Locale userLocale) {
        return restaurantTableRepository.findById(tableId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        messageUtil.getMessage("table.not.found", userLocale)));
    }

    @Override
    public void validateRestaurantTableNotDeleted(RestaurantTable restaurantTable, Locale userLocale) {
        if (Boolean.TRUE.equals(restaurantTable.getIsDeleted())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    messageUtil.getMessage("table.deleted", userLocale));
        }
    }

    // ==================== ORDER ENTITY VALIDATION METHODS ====================

    @Override
    public Order validateAndGetOrder(UUID orderId, Locale userLocale) {
        return orderRepository.findById(orderId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        messageUtil.getMessage("order.not.found", userLocale)));
    }

    // ==================== ORDERED ITEM ENTITY VALIDATION METHODS ====================

    @Override
    public OrderedItem validateAndGetOrderedItem(UUID itemId, Locale userLocale) {
        return orderedItemRepository.findById(itemId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        messageUtil.getMessage("ordered.item.not.found", userLocale)));
    }

    // ==================== ORDERED COMBO ENTITY VALIDATION METHODS ====================

    @Override
    public OrderedCombo validateAndGetOrderedCombo(UUID comboId, Locale userLocale) {
        return orderedComboRepository.findById(comboId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        messageUtil.getMessage("ordered.combo.not.found", userLocale)));
    }

    // ==================== PAYMENT VALIDATIONS ====================

    /**
     * Validates a payment request payload for required fields and basic invariants.
     * <p>
     * For CASH payments, also validates that the received/tendered amount is at least the amount paid.
     *
     * @param request payment request (required)
     * @param userLocale locale for localized error messages (may be {@code null})
     * @throws ResponseStatusException when required fields are missing or values are invalid
     */
    @Override
    public void validatePaymentRequest(PaymentRequest request, Locale userLocale) {
        if (request == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    messageUtil.getMessage("payment.request.required", userLocale));
        }
        validatePaymentOrderId(request.getOrderId(), userLocale);
        validatePaymentMethod(request.getPaymentMethod(), userLocale);
        validatePaymentAmount(request.getAmountPaid(), userLocale);
        if ("CASH".equalsIgnoreCase(request.getPaymentMethod())) {
            validateCashReceivedAgainstAmountPaid(request, userLocale);
        }
    }

    @Override
    public void validatePaymentOrderId(UUID orderId, Locale userLocale) {
        if (orderId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    messageUtil.getMessage("payment.order.id.required", userLocale));
        }
    }

    @Override
    public void validatePaymentMethod(String paymentMethod, Locale userLocale) {
        if (paymentMethod == null || paymentMethod.trim().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    messageUtil.getMessage("payment.method.required", userLocale));
        }
    }

    @Override
    public void validatePaymentAmount(BigDecimal amount, Locale userLocale) {
        if (amount == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    messageUtil.getMessage("payment.amount.required", userLocale));
        }
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    messageUtil.getMessage("payment.amount.invalid", userLocale));
        }
    }

    /**
     * Validates that the provided payment method is allowed by the chain configuration.
     *
     * @param paymentMethod payment method identifier (required)
     * @param chainConfig chain configuration containing supported payment methods (required)
     * @param userLocale locale for localized error messages (may be {@code null})
     * @throws ResponseStatusException if config is missing or the payment method is not supported
     */
    @Override
    public void validatePaymentMethodAgainstConfig(String paymentMethod, RestaurantChainConfigProperties.RestaurantChainData chainConfig, Locale userLocale) {
        if (chainConfig == null || chainConfig.getPaymentMethods() == null) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    messageUtil.getMessage("payment.config.not.found", userLocale));
        }
        
        List<String> validPaymentMethods = chainConfig.getPaymentMethods().stream()
                .map(RestaurantChainConfigProperties.PaymentMethod::getType)
                .collect(Collectors.toList());
        
        boolean allowed = validPaymentMethods.stream()
                .anyMatch(cfg -> paymentMethodMatchesConfigEntry(paymentMethod, cfg));
        if (!allowed) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    messageUtil.getMessage("payment.method.not.supported", userLocale, paymentMethod));
        }
    }

    /**
     * Allows API value {@code CARD} to match chain config {@code CREDIT_CARD} / {@code DEBIT_CARD} / {@code CARD}.
     */
    private boolean paymentMethodMatchesConfigEntry(String requestMethod, String configuredType) {
        if (requestMethod == null || configuredType == null) {
            return false;
        }
        if (requestMethod.equalsIgnoreCase(configuredType)) {
            return true;
        }
        if ("CARD".equalsIgnoreCase(requestMethod)) {
            return "CREDIT_CARD".equalsIgnoreCase(configuredType)
                    || "DEBIT_CARD".equalsIgnoreCase(configuredType)
                    || "CARD".equalsIgnoreCase(configuredType);
        }
        if ("CREDIT_CARD".equalsIgnoreCase(requestMethod) || "DEBIT_CARD".equalsIgnoreCase(requestMethod)) {
            return "CARD".equalsIgnoreCase(configuredType)
                    || "CREDIT_CARD".equalsIgnoreCase(configuredType)
                    || "DEBIT_CARD".equalsIgnoreCase(configuredType);
        }
        return false;
    }

    @Override
    public UUID resolveOnlineCardCashierId(Locale userLocale) {
        if (!onlineCardPaymentProperties.isConfigured()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    messageUtil.getMessage("payment.online-card.user.not.configured", userLocale));
        }
        try {
            return UUID.fromString(onlineCardPaymentProperties.getUserId().trim());
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    messageUtil.getMessage("payment.online-card.user.invalid", userLocale));
        }
    }

    @Override
    public void validateGmoHostedCardPayment(PaymentRequest request, Order order, Locale userLocale) {
        if (request == null || order == null || !isGmoHostedCardPayment(request.getPaymentMethod())) {
            return;
        }
        if (isBlank(request.getRetUrl()) || isBlank(request.getCompleteUrl()) || isBlank(request.getCancelUrl())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    messageUtil.getMessage("payment.card.hosted.urls.required", userLocale));
        }
        if (!isHttpUrl(request.getRetUrl().trim()) || !isHttpUrl(request.getCompleteUrl().trim())
                || !isHttpUrl(request.getCancelUrl().trim())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    messageUtil.getMessage("payment.card.hosted.urls.invalid", userLocale));
        }
        if (order.getGmoLinkOrderId() == null || order.getGmoLinkOrderId().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    messageUtil.getMessage("payment.card.gmo.order.id.missing", userLocale));
        }
        if (request.getResultSkipFlag() != null && !request.getResultSkipFlag().isBlank()) {
            String f = request.getResultSkipFlag().trim();
            if (!"0".equals(f) && !"1".equals(f)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        messageUtil.getMessage("payment.card.result.skip.flag.invalid", userLocale));
            }
        }
    }

    private static boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }

    private static boolean isHttpUrl(String s) {
        String lower = s.toLowerCase();
        return lower.startsWith("https://") || lower.startsWith("http://");
    }

    private static boolean isGmoHostedCardPayment(String paymentMethod) {
        if (paymentMethod == null) {
            return false;
        }
        String p = paymentMethod.trim();
        return "CARD".equalsIgnoreCase(p) || "CREDIT_CARD".equalsIgnoreCase(p) || "DEBIT_CARD".equalsIgnoreCase(p);
    }

    @Override
    public void validatePaymentAmountForMethod(String paymentMethod, BigDecimal amountPaid, BigDecimal orderTotal, Locale userLocale) {
        if ("CASH".equals(paymentMethod) && amountPaid.compareTo(orderTotal) < 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    messageUtil.getMessage("payment.insufficient.amount", userLocale));
        }
        // For other payment methods (CARD, etc.), amount validation is handled elsewhere
    }

    /**
     * Validates that the cash received/tendered value is not less than the amount paid.
     * <p>
     * Uses {@code cashReceived} when present; otherwise treats the tender as {@code amountPaid}.
     *
     * @param request payment request containing amount paid and optional cash received (required)
     * @param userLocale locale for localized error messages (may be {@code null})
     * @throws ResponseStatusException if cash received is less than amount paid
     */
    @Override
    public void validateCashReceivedAgainstAmountPaid(PaymentRequest request, Locale userLocale) {
        BigDecimal amountPaid = request.getAmountPaid();
        if (amountPaid == null) {
            return;
        }
        BigDecimal tender = request.getCashReceived() != null ? request.getCashReceived() : amountPaid;
        if (tender.compareTo(amountPaid) < 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    messageUtil.getMessage("payment.cash.received.insufficient", userLocale));
        }
    }

    @Override
    public Transaction validateAndGetTransactionForPayment(UUID orderId, Locale userLocale) {
        Optional<Transaction> transactionOpt = transactionRepository.findByOrderId(orderId);
        if (transactionOpt.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                    messageUtil.getMessage("transaction.not.found", userLocale));
        }
        return transactionOpt.get();
    }

    // ==================== USER VALIDATIONS ====================

    /**
     * Validates a user id string and returns the corresponding user.
     *
     * @param userId user id as string UUID (required)
     * @param userLocale locale for localized error messages (may be {@code null})
     * @return resolved user
     * @throws ResponseStatusException if the id is missing/invalid or user is not found
     */
    /**
     * Runs outside the caller's transaction so a caught {@link ResponseStatusException} does not
     * mark the outer transaction rollback-only (customer session UUIDs are valid User-ID values
     * but are not rows in {@code users}).
     */
    @Override
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public User validateAndGetUser(String userId, Locale userLocale) {
        if (userId == null || userId.trim().isEmpty() || "null".equalsIgnoreCase(userId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    messageUtil.getMessage("user.id.required", userLocale));
        }
        
        try {
            return userRepository.findById(UUID.fromString(userId))
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                            messageUtil.getMessage("user.not.found", userLocale)));
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    messageUtil.getMessage("invalid.user.id", userLocale));
        }
    }

    /**
     * Attempts to resolve a user by id; returns {@code null} for blank/"null"/invalid ids or when the user doesn't exist.
     * <p>
     * This is intended for optional user references where absence is not an error.
     *
     * @param userId user id as string UUID (may be {@code null})
     * @param userLocale locale for logging/messages (may be {@code null})
     * @return resolved user or {@code null}
     */
    @Override
    public User validateAndGetUserOrNull(String userId, Locale userLocale) {
        if (userId == null || userId.trim().isEmpty() || "null".equalsIgnoreCase(userId)) {
            return null;
        }
        
        try {
            return userRepository.findById(UUID.fromString(userId)).orElse(null);
        } catch (IllegalArgumentException e) {
            log.warn("Invalid userId format: {}, returning null", userId);
            return null;
        }
    }

    /**
     * Checks whether a user id string is a non-empty, non-"null" valid UUID.
     *
     * @param userId user id string
     * @return {@code true} if {@code userId} parses as a UUID; {@code false} otherwise
     */
    @Override
    public boolean isValidUserId(String userId) {
        if (userId == null || userId.trim().isEmpty() || "null".equalsIgnoreCase(userId)) {
            return false;
        }
        try {
            UUID.fromString(userId);
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    @Override
    public void validateCustomerOrStaffSessionAccess(String userId, String sessionIdHeader, UUID expectedSessionId,
                                                     Locale userLocale) {
        if (isStaffAuthenticated(userId, userLocale)) {
            return;
        }
        // Direct service access: customer JWT subject is exposed as User-ID (session UUID).
        if (matchesExpectedSessionId(userId, expectedSessionId)) {
            return;
        }
        validateCustomerSessionHeader(sessionIdHeader, expectedSessionId, userLocale);
    }

    @Override
    public void validateCustomerOrStaffOrderSessionAccess(String userId, String sessionIdHeader, UUID requestSessionId,
                                                          UUID orderSessionId, Locale userLocale) {
        validateCustomerOrStaffSessionAccess(userId, sessionIdHeader, requestSessionId, userLocale);
        if (!isStaffAuthenticated(userId, userLocale) && !matchesExpectedSessionId(userId, orderSessionId)) {
            validateCustomerSessionHeader(sessionIdHeader, orderSessionId, userLocale);
        }
    }

    private boolean isStaffAuthenticated(String userId, Locale userLocale) {
        return isValidUserId(userId) && validateAndGetUserOrNull(userId, userLocale) != null;
    }

    private boolean matchesExpectedSessionId(String value, UUID expectedSessionId) {
        if (value == null || value.trim().isEmpty() || "null".equalsIgnoreCase(value) || expectedSessionId == null) {
            return false;
        }
        try {
            return UUID.fromString(value.trim()).equals(expectedSessionId);
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    private void validateCustomerSessionHeader(String sessionIdHeader, UUID expectedSessionId, Locale userLocale) {
        if (expectedSessionId == null) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    messageUtil.getMessage("session.access.denied", userLocale));
        }
        if (sessionIdHeader == null || sessionIdHeader.trim().isEmpty() || "null".equalsIgnoreCase(sessionIdHeader)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,
                    messageUtil.getMessage("session.access.unauthorized", userLocale));
        }
        UUID headerSessionId;
        try {
            headerSessionId = UUID.fromString(sessionIdHeader.trim());
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,
                    messageUtil.getMessage("session.access.unauthorized", userLocale));
        }
        if (!headerSessionId.equals(expectedSessionId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    messageUtil.getMessage("session.access.denied", userLocale));
        }
    }

    // ==================== EMAIL VALIDATIONS ====================

    /**
     * Validates that an email is present and matches a basic email pattern.
     *
     * @param email email address (required)
     * @param userLocale locale for localized error messages (may be {@code null})
     * @throws ResponseStatusException if missing or invalid
     */
    @Override
    public void validateEmailFormat(String email, Locale userLocale) {
        if (email == null || email.trim().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    messageUtil.getMessage("employee.profile.email.required", userLocale));
        }
        
        // Basic email format validation - can be enhanced with regex
        if (!email.contains("@") || !email.contains(".")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    messageUtil.getMessage("employee.profile.email.invalid", userLocale));
        }
        
        // Enhanced validation: check for proper email format
        String emailRegex = "^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$";
        if (!email.matches(emailRegex)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    messageUtil.getMessage("employee.profile.email.invalid", userLocale));
        }
    }

    /**
     * Returns whether the email string matches the service's validation rules.
     *
     * @param email email address
     * @return {@code true} if non-blank and matches the regex; {@code false} otherwise
     */
    @Override
    public boolean isValidEmailFormat(String email) {
        if (email == null || email.trim().isEmpty()) {
            return false;
        }
        
        // Basic check
        if (!email.contains("@") || !email.contains(".")) {
            return false;
        }
        
        // Enhanced validation
        String emailRegex = "^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$";
        return email.matches(emailRegex);
    }

    // ==================== ADDITIONAL DISCOUNT VALIDATIONS ====================

    /**
     * Validates that an additional-discount request contains the required fields and a supported type.
     *
     * @param request additional discount request (required)
     * @param userLocale locale for localized error messages (may be {@code null})
     * @throws ResponseStatusException if required fields are missing/invalid
     */
    @Override
    public void validateAdditionalDiscountRequest(AdditionalDiscountRequest request, Locale userLocale) {
        if (request == null || request.getAdditionalDiscountType() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    messageUtil.getMessage("additional.discount.type.required", userLocale));
        }
        
        if (request.getAdditionalDiscountValue() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    messageUtil.getMessage("additional.discount.value.required", userLocale));
        }
        
        validateAdditionalDiscountType(request.getAdditionalDiscountType(), userLocale);
    }

    @Override
    public void validateAdditionalDiscountNotAlreadyApplied(Order order, Locale userLocale) {
        if (order.getAdditionalDiscountAmount() != null && 
            order.getAdditionalDiscountAmount().compareTo(BigDecimal.ZERO) > 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    messageUtil.getMessage("additional.discount.already.applied", userLocale));
        }
    }

    /**
     * Validates that the additional discount type is one of the supported types (PERCENT or FLAT).
     *
     * @param discountType discount type (required)
     * @param userLocale locale for localized error messages (may be {@code null})
     * @throws ResponseStatusException if type is missing or unsupported
     */
    @Override
    public void validateAdditionalDiscountType(DiscountType discountType, Locale userLocale) {
        if (discountType == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    messageUtil.getMessage("additional.discount.type.required", userLocale));
        }
        
        if (discountType != DiscountType.PERCENT && discountType != DiscountType.FLAT) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    messageUtil.getMessage("additional.discount.type.invalid", userLocale));
        }
    }

    /**
     * Validates an additional discount value against its type and the order total.
     * <p>
     * - PERCENT must be within [0, 100]
     * - FLAT must not exceed {@code orderTotal}
     *
     * @param value additional discount value (required)
     * @param discountType discount type (required)
     * @param orderTotal order total used for FLAT validation (required)
     * @param userLocale locale for localized error messages (may be {@code null})
     * @throws ResponseStatusException if value is missing/out of range/not applicable
     */
    @Override
    public void validateAdditionalDiscountValue(BigDecimal value, DiscountType discountType, BigDecimal orderTotal, Locale userLocale) {
        if (value == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    messageUtil.getMessage("additional.discount.value.required", userLocale));
        }
        
        if (value.compareTo(BigDecimal.ZERO) <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    messageUtil.getMessage("additional.discount.value.positive", userLocale));
        }
        
        if (orderTotal == null || orderTotal.compareTo(BigDecimal.ZERO) == 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    messageUtil.getMessage("additional.discount.not.applicable.zero.total", userLocale));
        }
        
        if (discountType == DiscountType.PERCENT) {
            validateAdditionalDiscountPercentRange(value, userLocale);
        } else if (discountType == DiscountType.FLAT) {
            validateAdditionalDiscountFlatNotExceedingTotal(value, orderTotal, userLocale);
        }
    }

    @Override
    public void validateAdditionalDiscountPercentRange(BigDecimal percentValue, Locale userLocale) {
        if (percentValue.compareTo(BigDecimal.ZERO) < 0 || percentValue.compareTo(BigDecimal.valueOf(100)) > 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    messageUtil.getMessage("additional.discount.percent.range", userLocale));
        }
    }

    @Override
    public void validateAdditionalDiscountFlatNotExceedingTotal(BigDecimal flatValue, BigDecimal orderTotal, Locale userLocale) {
        if (flatValue.compareTo(orderTotal) > 0) {
            String currency = restaurantChainConfigProperties != null && 
                             restaurantChainConfigProperties.getChain() != null ? 
                             restaurantChainConfigProperties.getChain().getCurrency() : null;
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    messageUtil.getMessage("additional.discount.flat.exceeds.total", userLocale, 
                            CurrencyFormatter.formatAmount(orderTotal, currency)));
        }
    }

    // ==================== TRANSACTION VALIDATIONS ====================

    @Override
    public Transaction validateAndGetTransaction(UUID orderId, Locale userLocale) {
        Optional<Transaction> transactionOpt = transactionRepository.findByOrderId(orderId);
        if (transactionOpt.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                    messageUtil.getMessage("transaction.not.found", userLocale));
        }
        return transactionOpt.get();
    }

    /**
     * Validates that a transaction is in a status that allows accepting/initiating payment.
     *
     * @param transaction transaction to validate (required)
     * @param userLocale locale for localized error messages (may be {@code null})
     * @throws ResponseStatusException if transaction is missing or already processed/closed
     */
    @Override
    public void validateTransactionStatusForPayment(Transaction transaction, Locale userLocale) {
        if (transaction == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    messageUtil.getMessage("transaction.required", userLocale));
        }
        
        // Validate transaction status (must be OPEN or PENDING for payment)
        if (transaction.getTransactionStatus() != TransactionStatus.OPEN && 
            transaction.getTransactionStatus() != TransactionStatus.PENDING) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    messageUtil.getMessage("transaction.already.processed", userLocale));
        }
    }

    // ==================== USER ROLE VALIDATION METHODS ====================

    /**
     * Determines whether the given user has the CASHIER role.
     *
     * @param user user to check (may be {@code null})
     * @return {@code true} if the user's role name is "CASHIER"; otherwise {@code false}
     */
    @Override
    public boolean isCashier(User user) {
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
     * Determines whether the given user has the MANAGER role.
     *
     * @param user user to check (may be {@code null})
     * @return {@code true} if the user's role name is "MANAGER"; otherwise {@code false}
     */
    @Override
    public boolean isManager(User user) {
        if (user == null || user.getRoleId() == null) {
            return false;
        }
        try {
            Role role = roleRepository.findById(user.getRoleId()).orElse(null);
            return role != null && "MANAGER".equals(role.getName());
        } catch (Exception e) {
            log.debug("Failed to check if user is manager: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Determines whether the given user has the WAITER role.
     *
     * @param user user to check (may be {@code null})
     * @return {@code true} if the user's role name equals "WAITER" (case-insensitive); otherwise {@code false}
     */
    @Override
    public boolean isUserWaiter(User user) {
        if (user == null || user.getRoleId() == null) {
            return false;
        }
        
        try {
            Optional<Role> roleOpt = roleRepository.findById(user.getRoleId());
            if (roleOpt.isPresent()) {
                String roleName = roleOpt.get().getName();
                return "WAITER".equalsIgnoreCase(roleName);
            }
        } catch (Exception e) {
            log.error("Error checking if user {} is a waiter: {}", user.getId(), e.getMessage(), e);
        }
        
        return false;
    }

    @Override
    public boolean requiresCancellationApproval(ItemStatus currentStatus) {
        return currentStatus != ItemStatus.PUSHED && currentStatus != ItemStatus.ON_HOLD;
    }

    // ==================== TRANSLATION HELPER METHODS ====================

    /**
     * Returns a localized combo name using translation fallback order.
     *
     * @param combo combo whose translations are queried (required)
     * @param userLocale preferred locale (may be {@code null})
     * @return localized combo name
     * @throws ResponseStatusException if no name is available
     */
    @Override
    @Transactional(propagation = Propagation.SUPPORTS, noRollbackFor = Exception.class)
    public String getComboName(Combo combo, Locale userLocale) {
        if (combo == null || combo.getTranslations() == null || combo.getTranslations().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                    messageUtil.getMessage("combo.name.not.found", userLocale));
        }
        String preferred = userLocale != null ? userLocale.getLanguage() : null;
        return TranslationUtils.pickPreferredOrFromListNonBlank(
                        combo.getTranslations(),
                        preferred,
                        localizationProperties.getLanguages(),
                        ComboTranslation::getLanguageCode,
                        ComboTranslation::getName)
                .map(ComboTranslation::getName)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        messageUtil.getMessage("combo.name.not.found", userLocale)));
    }

    /**
     * Returns a localized combo-group name using translation fallback order.
     * <p>
     * If no translation is available, returns a localized "not found" message rather than throwing.
     *
     * @param comboGroup combo group (required)
     * @param userLocale preferred locale (may be {@code null})
     * @return localized combo group name or a localized fallback message
     */
    @Override
    public String getComboGroupName(ComboGroup comboGroup, Locale userLocale) {
        if (comboGroup.getTranslations() == null || comboGroup.getTranslations().isEmpty()) {
            return messageUtil.getMessage("combo.group.name.not.found", userLocale);
        }
        String locale = userLocale != null ? userLocale.getLanguage() : null;
        for (String lang : buildLanguageFallbackOrder(locale)) {
            for (ComboGroupTranslation translation : comboGroup.getTranslations()) {
                if (translation.getLanguageCode() != null
                        && translation.getLanguageCode().equalsIgnoreCase(lang)) {
                    String name = translation.getGroupName();
                    if (name != null && !name.isBlank()) {
                        return name;
                    }
                    break;
                }
            }
        }
        return comboGroup.getTranslations().stream()
                .map(ComboGroupTranslation::getGroupName)
                .filter(n -> n != null && !n.isBlank())
                .findFirst()
                .orElseGet(() -> messageUtil.getMessage("combo.group.name.not.found", userLocale));
    }

    /**
     * Preferred locale first, then each configured language in order (aligns with combo menu APIs),
     * without duplicates. Ensures e.g. {@code ja} then {@code en} in config so English is used when
     * Japanese is missing.
     */
    private List<String> buildLanguageFallbackOrder(String preferredLocale) {
        List<String> order = new ArrayList<>();
        if (preferredLocale != null && !preferredLocale.isBlank()) {
            order.add(preferredLocale);
        }
        if (localizationProperties.getLanguages() != null) {
            for (String lang : localizationProperties.getLanguages()) {
                if (lang == null || lang.isBlank()) {
                    continue;
                }
                boolean duplicate = order.stream().anyMatch(existing -> existing.equalsIgnoreCase(lang));
                if (!duplicate) {
                    order.add(lang);
                }
            }
        }
        return order;
    }

    /**
     * Returns a localized item name using translation fallback order.
     *
     * @param item item whose translations are queried (required)
     * @param userLocale preferred locale (may be {@code null})
     * @return localized item name
     * @throws ResponseStatusException if no translation name is available
     */
    @Override
    @Transactional(propagation = Propagation.SUPPORTS, noRollbackFor = Exception.class)
    public String getItemName(Item item, Locale userLocale) {
        if (item == null || item.getTranslations() == null || item.getTranslations().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                    messageUtil.getMessage(msgItemNameNotFound, userLocale));
        }
        String preferred = userLocale != null ? userLocale.getLanguage() : null;
        for (String lang : buildLanguageFallbackOrder(preferred)) {
            for (ItemTranslation translation : item.getTranslations()) {
                if (translation == null || translation.getLanguageCode() == null) {
                    continue;
                }
                if (translation.getLanguageCode().equalsIgnoreCase(lang)) {
                    String name = translation.getName();
                    if (name != null && !name.isBlank()) {
                        return name;
                    }
                    break;
                }
            }
        }
        throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                messageUtil.getMessage(msgItemNameNotFound, userLocale));
    }

    /**
     * Returns a localized modifier-group name.
     * <p>
     * Attempts the requested locale first, then the first configured language, then falls back to a localized message.
     *
     * @param modifierGroup modifier group (required)
     * @param userLocale preferred locale (required)
     * @return localized name or fallback message
     */
    @Override
    public String getModifierGroupName(ModifierGroup modifierGroup, Locale userLocale) {
        String locale = userLocale.getLanguage();
        
        // First try to find the specific locale
        for (ModifierGroupTranslation translation : modifierGroup.getTranslations()) {
            if (translation.getLanguageCode() != null && translation.getLanguageCode().equalsIgnoreCase(locale)) {
                return translation.getName();
            }
        }
        
        // If no translation found for the requested locale, use config's first language
        if (!modifierGroup.getTranslations().isEmpty()) {
            String configuredDefaultLang = (localizationProperties.getLanguages() != null && !localizationProperties.getLanguages().isEmpty())
                    ? localizationProperties.getLanguages().get(0)
                    : null;
            if (configuredDefaultLang != null) {
                for (ModifierGroupTranslation translation : modifierGroup.getTranslations()) {
                    if (translation.getLanguageCode() != null && translation.getLanguageCode().equalsIgnoreCase(configuredDefaultLang)) {
                        return translation.getName();
                    }
                }
            }
        }
        
        // Fallback to message if no translations exist
        return messageUtil.getMessage("modifier.group.name.not.found", userLocale);
    }

    /**
     * Returns a localized modifier-item name.
     * <p>
     * Attempts the requested locale first, then the first configured language, then falls back to a localized message.
     *
     * @param modifierItem modifier item (required)
     * @param userLocale preferred locale (required)
     * @return localized name or fallback message
     */
    @Override
    public String getModifierItemName(ModifierItem modifierItem, Locale userLocale) {
        String locale = userLocale.getLanguage();
        
        // First try to find the specific locale
        for (ModifierItemTranslation translation : modifierItem.getTranslations()) {
            if (translation.getLanguageCode() != null && translation.getLanguageCode().equalsIgnoreCase(locale)) {
                return translation.getName();
            }
        }
        
        // If no translation found for the requested locale, use config's first language
        if (!modifierItem.getTranslations().isEmpty()) {
            String configuredDefaultLang = (localizationProperties.getLanguages() != null && !localizationProperties.getLanguages().isEmpty())
                    ? localizationProperties.getLanguages().get(0)
                    : null;
            if (configuredDefaultLang != null) {
                for (ModifierItemTranslation translation : modifierItem.getTranslations()) {
                    if (translation.getLanguageCode() != null && translation.getLanguageCode().equalsIgnoreCase(configuredDefaultLang)) {
                        return translation.getName();
                    }
                }
            }
        }
        
        // Fallback to message if no translations exist
        return messageUtil.getMessage(msgModifierItemNameNotFound, userLocale);
    }

    @Override
    public String getRestaurantName(Restaurant restaurant, Locale userLocale) {
        if (restaurant == null) {
            return "Restaurant";
        }
        return restaurant.getTranslations().stream()
            .filter(t -> "en".equals(t.getLanguageCode()))
            .findFirst()
            .map(RestaurantTranslation::getName)
            .orElse(restaurant.getRestaurantCode() != null ? restaurant.getRestaurantCode() : "Restaurant");
    }

    // ==================== BXGY HELPER METHODS ====================

    /**
     * Reconstructs BXGY runtime info for an existing ordered item from persisted fields.
     * <p>
     * If the required fields are not present on the ordered item, returns {@code null}.
     *
     * @param orderedItem ordered item potentially carrying BXGY persisted fields (required)
     * @param menuId menu id (currently unused; reserved for future reconstruction logic)
     * @return reconstructed BXGY info or {@code null} if not available
     */
    @Override
    public BxgyItemInfo reconstructBxgyInfo(OrderedItem orderedItem, UUID menuId) {
        // If the orderedItem has BXGY information stored, return it
        // Otherwise return null (can't reconstruct runtime discount application info)
        if (orderedItem.getDiscountApplicationId() != null && 
            orderedItem.getDiscountId() != null && 
            orderedItem.getBxgyRole() != null) {
            return new BxgyItemInfo(
                orderedItem.getDiscountApplicationId(),
                orderedItem.getDiscountId(),
                orderedItem.getBxgyRole(),
                orderedItem.getFreeQuantity() != null ? orderedItem.getFreeQuantity() : 0
            );
        }
        return null;
    }

    /**
     * Finds the {@link CategoryItemMapping} for an ordered item within a specific menu.
     *
     * @param orderedItem ordered item whose item id is used for lookup (required)
     * @param menuId menu id (may be {@code null})
     * @return mapping if found; otherwise {@code null}
     */
    @Override
    public CategoryItemMapping getCategoryItemMappingForOrderedItem(OrderedItem orderedItem, UUID menuId) {
        if (menuId == null) {
            return null;
        }
        
        List<MenuCategoryMapping> menuCategoryMappings = menuCategoryMappingRepository.findByMenuId(menuId);
        
        for (MenuCategoryMapping menuCategoryMapping : menuCategoryMappings) {
            CategoryItemMapping mapping = categoryItemMappingRepository
                .findByMenuCategoryMapping_IdAndItem_Id(menuCategoryMapping.getId(), orderedItem.getItem().getId());
            if (mapping != null) {
                return mapping;
            }
        }
        
        return null;
    }

    /**
     * Returns the currently assigned waiter for the given table, if any.
     * <p>
     * Uses the most recent active assignment (unassignedAt is null) when multiple exist.
     *
     * @param table restaurant table (required)
     * @return waiter user or {@code null} if not assigned or on error
     */
    @Override
    public User getWaiterForTable(RestaurantTable table) {
        try {
            List<TableAssignment> tableAssignments = tableAssignmentRepository.findByRestaurantTableIdAndUnassignedAtIsNullWithWaiter(table.getId());
            if (!tableAssignments.isEmpty()) {
                // Sort by assigned_at descending to get the most recent assignment
                tableAssignments.sort((ta1, ta2) -> {
                    if (ta1.getAssignedAt() == null && ta2.getAssignedAt() == null) return 0;
                    if (ta1.getAssignedAt() == null) return 1;
                    if (ta2.getAssignedAt() == null) return -1;
                    return ta2.getAssignedAt().compareTo(ta1.getAssignedAt());
                });
                return tableAssignments.get(0).getWaiter();
            }
        } catch (Exception e) {
            log.error("Error getting waiter for table {}: {}", table.getId(), e.getMessage(), e);
        }
        return null;
    }

    /**
     * Returns all currently assigned waiters for the given table.
     *
     * @param table restaurant table (required)
     * @return distinct list of assigned waiters; empty list when none or on error
     */
    @Override
    public List<User> getWaitersForTable(RestaurantTable table) {
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

    // ==================== ORDER HELPER METHODS ====================

    @Override
    public UUID getMenuIdFromOrder(Order order) {
        if (order == null) {
            return null;
        }
        
        // Try to get from session - Session doesn't have direct menu reference
        // Menu is typically accessed through restaurant or other relationships
        // For now, return null and let reconstruction work without menuId
        return null;
    }

    /**
     * Filters out ordered-item requests that point to existing items already cancelled in persistence.
     * <p>
     * New item requests (without {@code orderedItemId}) are always kept.
     *
     * @param orderedItems incoming ordered-item requests (may be {@code null} or empty)
     * @param userLocale locale for logging/messages (may be {@code null})
     * @return list containing only non-cancelled items (never {@code null})
     */
    @Override
    public List<OrderedItemRequest> filterOutCancelledItems(List<OrderedItemRequest> orderedItems, Locale userLocale) {
        if (orderedItems == null || orderedItems.isEmpty()) {
            return new ArrayList<>();
        }

        List<OrderedItemRequest> activeItems = new ArrayList<>();
        
        for (OrderedItemRequest itemRequest : orderedItems) {
            // If item has orderedItemId, it's an existing item - check its status
            if (itemRequest.getOrderedItemId() != null) {
                try {
                    OrderedItem existingItem = orderedItemRepository.findById(itemRequest.getOrderedItemId())
                            .orElse(null);
                    
                    if (existingItem != null) {
                        // Exclude cancelled items
                        if (existingItem.getItemStatus() == ItemStatus.CANCELED) {
                            log.info("CALCULATE ORDER - Excluding cancelled item {} from calculation", 
                                itemRequest.getOrderedItemId());
                            continue;
                        }
                    } else {
                        log.warn("CALCULATE ORDER - OrderedItem {} not found, including in calculation", 
                            itemRequest.getOrderedItemId());
                    }
                } catch (Exception e) {
                    log.error("CALCULATE ORDER - Error checking item status for {}: {}", 
                        itemRequest.getOrderedItemId(), e.getMessage());
                    // Include item if we can't check status (fail-safe)
                }
            }
            // New items (without orderedItemId) are always included
            activeItems.add(itemRequest);
        }
        
        return activeItems;
    }

    /**
     * Filters out ordered-combo requests that point to existing combos already cancelled in persistence.
     * <p>
     * New combo requests (without {@code orderedComboId}) are always kept.
     *
     * @param orderedCombos incoming ordered-combo requests (may be {@code null} or empty)
     * @param userLocale locale for logging/messages (may be {@code null})
     * @return list containing only non-cancelled combos (never {@code null})
     */
    @Override
    public List<OrderedComboRequest> filterOutCancelledCombos(List<OrderedComboRequest> orderedCombos, Locale userLocale) {
        if (orderedCombos == null || orderedCombos.isEmpty()) {
            return new ArrayList<>();
        }

        List<OrderedComboRequest> activeCombos = new ArrayList<>();
        
        for (OrderedComboRequest comboRequest : orderedCombos) {
            // If combo has orderedComboId, it's an existing combo - check its status
            if (comboRequest.getOrderedComboId() != null) {
                try {
                    OrderedCombo existingCombo = orderedComboRepository.findById(comboRequest.getOrderedComboId())
                            .orElse(null);
                    
                    if (existingCombo != null) {
                        // Exclude cancelled combos
                        if (existingCombo.getItemStatus() == ItemStatus.CANCELED) {
                            log.info("CALCULATE ORDER - Excluding cancelled combo {} from calculation", 
                                comboRequest.getOrderedComboId());
                            continue;
                        }
                    } else {
                        log.warn("CALCULATE ORDER - OrderedCombo {} not found, including in calculation", 
                            comboRequest.getOrderedComboId());
                    }
                } catch (Exception e) {
                    log.error("CALCULATE ORDER - Error checking combo status for {}: {}", 
                        comboRequest.getOrderedComboId(), e.getMessage());
                    // Include combo if we can't check status (fail-safe)
                }
            }
            // New combos (without orderedComboId) are always included
            activeCombos.add(comboRequest);
        }
        
        return activeCombos;
    }
}

