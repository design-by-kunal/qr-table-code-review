package com.gulfnet.restaurantmanagement.service.impl;

import com.gulfnet.restaurantmanagement.config.GmoLinkPlusProperties;
import com.gulfnet.restaurantmanagement.config.RestaurantChainConfigProperties;
import com.gulfnet.restaurantmanagement.service.OrderService;
import com.gulfnet.restaurantmanagement.service.OrderValidationService;
import com.gulfnet.restaurantmanagement.service.OrderNotificationService;
import com.gulfnet.restaurantmanagement.service.OrderPricingService;
import com.gulfnet.restaurantmanagement.service.OrderRecalculationService;
import com.gulfnet.restaurantmanagement.service.OrderedItemService;
import com.gulfnet.restaurantmanagement.service.OrderedComboService;
import com.gulfnet.restaurantmanagement.service.ReceiptService;
import com.gulfnet.restaurantmanagement.service.GmoService;
import com.gulfnet.restaurantmanagement.service.NotificationService;
import com.gulfnet.restaurantmanagement.service.RestaurantAlertEvaluationService;
import com.gulfnet.restaurantmanagement.service.OmiseService;
import com.gulfnet.restaurantmanagement.service.OmiseScannableQrStorageService;
import com.gulfnet.shared_library.entity.*;
import com.gulfnet.shared_library.entity.CashDrawerLog;
import com.gulfnet.shared_library.entity.CashierShift;
import com.gulfnet.shared_library.entity.ComboTranslation;
import com.gulfnet.shared_library.enums.*;
import com.gulfnet.shared_library.enums.ActionType;
import com.gulfnet.shared_library.model.request.*;
import com.gulfnet.shared_library.model.request.PaymentRequest;
import com.gulfnet.shared_library.model.request.OrderedComboRequest;
import com.gulfnet.shared_library.model.request.OrderedComboGroupRequest;
import com.gulfnet.shared_library.model.request.OrderedComboItemRequest;
import com.gulfnet.shared_library.model.request.OrderedComboItemModifierRequest;
import com.gulfnet.shared_library.model.request.UpdateOrderedComboRequest;
import com.gulfnet.shared_library.model.response.dto.*;
import com.gulfnet.shared_library.model.response.dto.BxgyItemInfo;
import com.gulfnet.shared_library.model.request.OrderCancellationRequestDto;
import com.gulfnet.shared_library.model.response.dto.OrderCancellationRequestResponse;
import com.gulfnet.shared_library.model.response.dto.OrderedComboResponse;
import com.gulfnet.shared_library.model.response.dto.OrderedComboGroupResponse;
import com.gulfnet.shared_library.model.response.dto.OrderedComboItemResponse;
import com.gulfnet.shared_library.model.response.dto.OrderedComboItemModifierResponse;
import com.gulfnet.shared_library.model.request.ItemCancellationRequestDto;
import com.gulfnet.shared_library.model.request.RatingRequest;
import com.gulfnet.shared_library.model.response.PaymentResponse;
import com.gulfnet.shared_library.model.response.dto.RatingDto;
import com.gulfnet.shared_library.model.response.dto.RatingResponse;
import com.gulfnet.shared_library.repository.*;
import com.gulfnet.shared_library.repository.RoleRepository;
import com.gulfnet.shared_library.repository.RatingRepository;
import com.gulfnet.shared_library.repository.RefundRepository;
import com.gulfnet.shared_library.repository.OrderDiscountUsageRepository;
import com.gulfnet.restaurantmanagement.service.OperatingHoursCutoffService;
import com.gulfnet.restaurantmanagement.service.OrderSequenceService;
import com.gulfnet.shared_library.entity.Role;
import com.gulfnet.shared_library.util.CancellationAmountPolicy;
import com.gulfnet.shared_library.util.CurrencyFormatter;
import com.gulfnet.shared_library.util.GmoLinkOrderIdGenerator;
import com.gulfnet.restaurantmanagement.service.AuditTrailService;
import com.gulfnet.restaurantmanagement.service.GmoLinkPlusPaymentService;
import com.gulfnet.restaurantmanagement.util.MessageUtil;
import com.gulfnet.restaurantmanagement.util.PriceOverrideHelper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import org.springframework.dao.DataIntegrityViolationException;
import com.gulfnet.shared_library.config.AWSService;
import com.gulfnet.shared_library.util.EmailSender;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.hibernate.Hibernate;

import java.util.Set;
import java.util.HashSet;
import java.util.List;
import java.util.ArrayList;
import java.util.Objects;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.Map;
import java.util.HashMap;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.Collections;
import java.util.function.Supplier;
import java.util.function.Consumer;
import java.util.function.Function;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.ThreadLocalRandom;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrderServiceImpl implements OrderService {

    private static final String LOG_RESTAURANT_ID_MISMATCH_FOR_OVERRIDE =
            "Restaurant ID mismatch! Session: {}, Request: {}. Using request ID for price override lookup.";
    private static final String LOG_ORDER_STATUS_AUTO_UPDATED = "Order {} status automatically updated to: {}";
    private static final String LOG_INVALID_USER_ID_FORMAT = "Invalid userId format: {}";
    private static final String LOG_ERROR_FETCHING_REFUND_FOR_TRANSACTION = "Error fetching refund for transaction {}: {}";
    private static final String LOG_AUDIT_TRAIL_ITEM_CANCELLATION_FAILED = "Failed to create audit trail for item cancellation: {}";

    private static final String TYPE_COMBO = "combo";
    private static final String TYPE_ITEM = "item";

    private static final String ROLE_MANAGER = "MANAGER";
    private static final String ROLE_HQ_ADMIN = "HQ_ADMIN";
    private static final String ROLE_CASHIER = "CASHIER";

    private static final String ENTITY_TYPE_ORDER = "ORDER";
    private static final String SORT_FIELD_CREATED_AT = "createdAt";

    private static final String MSG_ORDER_NOT_FOUND = "order.not.found";
    private static final String MSG_ORDER_LIST_EMPTY = "order.list.empty";
    private static final String MSG_ORDER_LIST_SUCCESS = "order.list.success";
    private static final String MSG_ORDERED_ITEM_NOT_FOUND = "ordered.item.not.found";
    private static final String MSG_ITEM_CANCELLED_DIRECTLY = "item.cancelled.directly";
    private static final String MSG_ITEM_STATUS_UPDATED = "item.status.updated";

    private static final String PARAM_CANCELLATION_REASON = "cancellationReason";
    private static final String PARAM_REQUEST_TYPE = "requestType";
    private static final String MSG_AUTO_DECLINED_TRANSACTION_COMPLETED =
            "Auto-declined: Transaction was completed before manager could review the request.";

    /** JPA reference for {@code transaction.cashier_id} only; no {@code users} row required. */
    private static User cashierReference(UUID cashierId) {
        User ref = new User();
        ref.setId(cashierId);
        return ref;
    }

    private static boolean isGmoHostedCardPayment(String paymentMethod) {
        if (paymentMethod == null) {
            return false;
        }
        String p = paymentMethod.trim();
        return "CARD".equalsIgnoreCase(p) || "CREDIT_CARD".equalsIgnoreCase(p) || "DEBIT_CARD".equalsIgnoreCase(p);
    }

    /**
     * Persists which app/wallet was used: UPI {@code type} (paypay, promptpay, paynow), or {@code CASH} / {@code CARD}.
     */
    private static String resolvePaymentApp(String paymentMethod, String type) {
        if (paymentMethod == null) {
            return null;
        }
        if ("UPI".equalsIgnoreCase(paymentMethod)) {
            return type != null && !type.isBlank() ? type.trim().toLowerCase() : null;
        }
        if ("CASH".equalsIgnoreCase(paymentMethod)) {
            return "CASH";
        }
        if (isGmoHostedCardPayment(paymentMethod)) {
            return "CARD";
        }
        return null;
    }

    private final OrderRepository orderRepository;
    private final OrderedItemRepository orderedItemRepository;
    private final OrderedItemModifierRepository orderedItemModifierRepository;
    private final SessionRepository sessionRepository;
    private final UserRepository userRepository;
    private final RestaurantRepository restaurantRepository;
    private final TableAssignmentRepository tableAssignmentRepository;
    private final ItemRepository itemRepository;
    private final ModifierGroupRepository modifierGroupRepository;
    private final ModifierItemRepository modifierItemRepository;
    private final DiscountRepository discountRepository;
    private final MenuRepository menuRepository;
    private final MenuCategoryMappingRepository menuCategoryMappingRepository;
    private final CategoryItemMappingRepository categoryItemMappingRepository;
    private final DiscountBxgyItemRepository discountBxgyItemRepository;
    private final TransactionRepository transactionRepository;
    private final OrderDiscountUsageRepository orderDiscountUsageRepository;
    private final MessageUtil messageUtil;
    private final RestaurantChainConfigProperties restaurantChainConfigProperties;
    private final GmoService gmoService;
    private final AWSService awsService;
    private final ReceiptService receiptService;
    private final EmailSender emailSender;
    private final NotificationService notificationService;
    private final @Qualifier("notificationTaskExecutor") Executor notificationTaskExecutor;
    private final RoleRepository roleRepository;
    private final ComboRepository comboRepository;
    private final OrderedComboRepository orderedComboRepository;
    private final ComboItemModifierRepository comboItemModifierRepository;
    private final ComboGroupRepository comboGroupRepository;
    private final RestaurantItemAvailabilityRepository restaurantItemAvailabilityRepository;
    private final AuditTrailService auditTrailService;
    private final PriceOverrideHelper priceOverrideHelper;
    private final RatingRepository ratingRepository;
    private final RefundRepository refundRepository;
    private final OrderValidationService orderValidationService;
    private final OrderNotificationService orderNotificationService;
    private final OrderPricingService orderPricingService;
    private final OrderRecalculationService orderRecalculationService;
    private final ReceiptGenerationAsyncService receiptGenerationAsyncService;
    private final OrderedItemService orderedItemService;
    private final OrderedComboService orderedComboService;
    private final CashDrawerLogRepository cashDrawerLogRepository;
    private final CashierShiftRepository cashierShiftRepository;
    private final OmiseService omiseService;
    private final OmiseScannableQrStorageService omiseScannableQrStorageService;
    private final GmoLinkPlusPaymentService gmoLinkPlusPaymentService;
    private final GmoLinkPlusProperties gmoLinkPlusProperties;

    private final OperatingHoursCutoffService operatingHoursCutoffService;

    /**
     * Request-scoped cache to batch-load data for order response building.
     * Used only by endpoints that explicitly set it (e.g., session orders).
     */
    private final ThreadLocal<OrderResponseBatchContext> orderResponseBatchCtxHolder = new ThreadLocal<>();

    @Autowired
    private OrderSequenceService orderSequenceService;

    
    @Autowired
    @Lazy
    private RestaurantAlertEvaluationService restaurantAlertEvaluationService;
    
    @PersistenceContext
    private EntityManager entityManager;

    @Override
    @Transactional
    public ResponseDto<OrderDto<OrderResponse>> createOrder(String userId, String sessionIdHeader, OrderRequest request, OrderType orderType) {
        Locale userLocale = LocaleContextHolder.getLocale();

        orderValidationService.validateCustomerOrStaffSessionAccess(
                userId, sessionIdHeader, request.getSessionId(), userLocale);

        // ==================== HANDLE AUTHENTICATION CONTEXT ====================
        User authenticatedUser = null;
        boolean hasUserId = false;

        // ==================== HANDLE AUTHENTICATION CONTEXT ====================
        // Resolve staff user if present; customer JWTs carry session UUID as User-ID (not in users table)
        if (orderValidationService.isValidUserId(userId)) {
            authenticatedUser = orderValidationService.validateAndGetUserOrNull(userId, userLocale);
            if (authenticatedUser != null) {
                hasUserId = true;
                log.info("Order created by authenticated user: {}", authenticatedUser.getId());
            } else {
                hasUserId = false;
                log.info("User id {} not found in users table, treating as anonymous order", userId);
            }
        } else {
            hasUserId = false;
            log.info("Order created anonymously (no userId provided)");
        }

        // ==================== VALIDATE SESSION ====================
        Session session = orderValidationService.validateAndGetSession(request.getSessionId(), userLocale);
        orderValidationService.validateSessionNotExpired(session, userLocale);
        orderValidationService.validateSingleOrderPerSession(request.getSessionId(), userLocale);

        // ==================== VALIDATE MENU ====================
        Menu menu = orderValidationService.validateAndGetMenu(request.getMenuId(), userLocale);

        // ==================== VALIDATE RESTAURANT ====================
        Restaurant restaurant = orderValidationService.validateAndGetRestaurant(session.getRestaurantId(), userLocale);
        orderValidationService.validateRestaurantActive(restaurant, userLocale);

        // ==================== VALIDATE RESTAURANT TABLE ====================
        RestaurantTable restaurantTable = orderValidationService.validateAndGetRestaurantTable(session.getTableId(), userLocale);
        orderValidationService.validateRestaurantTableNotDeleted(restaurantTable, userLocale);

        // ==================== BLOCK CUSTOMER ORDERS WITHOUT WAITER ASSIGNMENT ====================
        // Only enforce waiter assignment when waiter dependency is enabled.
        boolean waiterDependencyEnabled = restaurantChainConfigProperties.isWaiterDependencyEnabled();
        if (waiterDependencyEnabled
                && !(hasUserId && authenticatedUser != null && orderValidationService.isUserWaiter(authenticatedUser))) {
            List<User> assignedWaiters = orderValidationService.getWaitersForTable(restaurantTable);
            if (assignedWaiters == null || assignedWaiters.isEmpty()) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                        messageUtil.getMessage("table.no.waiter.assigned.cannot.order", userLocale));
            }
        }

        // ==================== DETERMINE WAITER FOR ORDER ====================
        // Prefer the authenticated user when they are a waiter (order placed from waiter app).
        // For customer-placed orders, waiter will remain null and reports will fall back to table assignments.
        User waiter = null;
        if (hasUserId && authenticatedUser != null && orderValidationService.isUserWaiter(authenticatedUser)) {
            waiter = authenticatedUser;
            log.info("Assigning waiter {} to order based on authenticated user context", waiter.getId());
        }

        // ==================== DETERMINE CREATED_BY AND UPDATED_BY ====================
        User createdBy = null;
        User updatedBy = null;

        if (hasUserId && authenticatedUser != null) {
            // Authenticated user: Use authenticated user as createdBy/updatedBy
            createdBy = authenticatedUser;
            updatedBy = authenticatedUser;
            log.info("Using authenticated user as createdBy: {}", authenticatedUser.getId());
        } else {
            // Anonymous order: Keep createdBy/updatedBy as null
            createdBy = null;
            updatedBy = null;
            log.info("Anonymous order - createdBy/updatedBy will be null");
        }

        // Validate ordered items
        orderValidationService.validateOrderItems(request.getOrderedItems(), userLocale);
        
        // Validate item availability for restaurant
        orderValidationService.validateItemAvailabilityForRestaurant(request.getOrderedItems(), request.getRestaurantId(), request.getMenuId(), userLocale);
        
        // Validate ordered combos
        orderValidationService.validateOrderCombos(request.getOrderedCombos(), request.getMenuId(), userLocale);
        
        // ==================== BUILD ACTIVE PRICE OVERRIDE INDEX ====================
        // Determine restaurant ID for price override lookup (use request restaurantId if provided, otherwise session restaurantId)
        UUID restaurantId = restaurant.getId();
        
        UUID restaurantIdForOverride = restaurantId;
        if (request.getRestaurantId() != null &&
            !request.getRestaurantId().equals(restaurantId)) {
            log.warn(LOG_RESTAURANT_ID_MISMATCH_FOR_OVERRIDE,
                restaurantId, request.getRestaurantId());
            restaurantIdForOverride = request.getRestaurantId();
        }
        
        PriceOverrideHelper.ActiveOverrideIndex activeOverrideIndex = 
                priceOverrideHelper.buildActiveOverrideIndex(restaurantIdForOverride);

        // ==================== ENSURE BXGY DISCOUNT IDs ARE INCLUDED ====================
        // Ensure BXGY discount IDs are always included when isBuyItem or isGetItem is true
        if (request.getOrderedItems() != null && !request.getOrderedItems().isEmpty()) {
            for (OrderedItemRequest itemRequest : request.getOrderedItems()) {
                List<UUID> updatedDiscountIds = orderPricingService.ensureBxgyDiscountIdsIncluded(itemRequest, request.getMenuId());
                if (!updatedDiscountIds.equals(itemRequest.getDiscountIds())) {
                    itemRequest.setDiscountIds(updatedDiscountIds);
                }
            }
        }

        // ==================== VALIDATE BXGY GET ITEMS QUANTITY ====================
        orderValidationService.validateBxgyGetItemsQuantity(request.getOrderedItems(), request.getMenuId(), userLocale);

        // ==================== ORDER-LEVEL DISCOUNT VALIDATION ====================
        Discount orderDiscount = null;
        String discountCode = null;
        BigDecimal discountAmount = null;
        DiscountType discountType = null;

        if (request.getDiscountId() != null) {
            orderDiscount = orderValidationService.validateAndGetOrderDiscount(request.getDiscountId(), userLocale);
            
            // Validate that this is an ORDER-level discount
            orderValidationService.validateOrderLevelDiscountType(orderDiscount, userLocale);
            
            discountCode = orderDiscount.getDiscountCode();
            discountAmount = orderDiscount.getValue();
            discountType = orderDiscount.getDiscountType();
        } else if (request.getDiscountCode() != null && !request.getDiscountCode().trim().isEmpty()) {
            // Support discount code lookup (for cases where discount code is provided instead of ID)
            Optional<Discount> discountOpt = discountRepository.findByDiscountCodeAndIsDeletedFalse(request.getDiscountCode());
            
            if (discountOpt.isEmpty()) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                        messageUtil.getMessage("discount.not.found", userLocale));
            }
            
            orderDiscount = discountOpt.get();
            
            // Validate discount is active and not deleted
            if (orderDiscount.getStatus() != EntityStatus.ACTIVE || orderDiscount.getIsDeleted()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        messageUtil.getMessage("discount.not.active", userLocale));
            }
            
            // Validate usage limits
            if (orderDiscount.getMaxUses() != null && orderDiscount.getMaxUses() > 0 
                    && orderDiscount.getCurrentUsage() >= orderDiscount.getMaxUses()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        messageUtil.getMessage("discount.usage.limit.exceeded", userLocale));
            }
            
            // Validate that this is an ORDER-level discount
            orderValidationService.validateOrderLevelDiscountType(orderDiscount, userLocale);
            
            discountCode = orderDiscount.getDiscountCode();
            discountAmount = orderDiscount.getValue();
            discountType = orderDiscount.getDiscountType();
        }

        // ==================== CALCULATE COMPLETE ORDER TOTALS USING GENERIC METHOD ====================
        com.gulfnet.shared_library.model.response.dto.OrderCalculationResult calculationResult = orderPricingService.calculateCompleteOrderTotals(
                request.getOrderedItems(),
                request.getOrderedCombos(),
                request.getMenuId(),
                restaurantId,
                activeOverrideIndex,
                orderDiscount,
                request.getAdditionalDiscountValue(),
                request.getAdditionalDiscountType(),
                orderType,
                userLocale);
        
        // Extract calculated values
        BigDecimal subTotal = calculationResult.getSubTotal();
        BigDecimal subtotalAfterDiscount = calculationResult.getSubtotalAfterDiscount();
        BigDecimal taxAmount = calculationResult.getTaxAmount();
        BigDecimal alcoholicTaxAmount = calculationResult.getAlcoholicTaxAmount();
        BigDecimal nonAlcoholicTaxAmount = calculationResult.getNonAlcoholicTaxAmount();
        BigDecimal alcoholicTaxableAmount = calculationResult.getAlcoholicTaxableAmount();
        BigDecimal nonAlcoholicTaxableAmount = calculationResult.getNonAlcoholicTaxableAmount();
        BigDecimal serviceChargeAmount = calculationResult.getServiceChargeAmount();
        BigDecimal packingChargeAmount = calculationResult.getPackingChargeAmount();
        BigDecimal additionalDiscountSavings = calculationResult.getAdditionalDiscountSavings();
        BigDecimal totalAmount = calculationResult.getTotalAmount();
        BigDecimal orderDiscountSavings = calculationResult.getOrderDiscountSavings();
        com.gulfnet.shared_library.model.response.dto.BxgyCalculationResult bxgyResult = calculationResult.getBxgyResult();

        // ==================== GENERATE ORDER NUMBER ====================
        String orderNumber = generateOrderNumber(restaurant, orderType);
        String gmoLinkOrderId = generateUniqueGmoLinkOrderId();

        // ==================== CREATE ORDER ENTITY ====================
        Order order = Order.builder()
                .orderNumber(orderNumber)
                .gmoLinkOrderId(gmoLinkOrderId)
                .session(session)
                .restaurant(restaurant)
                .restaurantTable(restaurantTable)
                .waiter(waiter)
                .discount(orderDiscount)
                .orderStatus(OrderStatus.PUSHED)
                .orderType(orderType)
                .subTotal(subTotal) // Subtotal after item discounts only
                .discountCode(discountCode)
                .discountValue(orderDiscount != null ? orderDiscount.getValue() : null)
                .discountAmount(orderDiscountSavings) // Order-level discount savings
                .discountType(discountType)
                .taxAmount(taxAmount)
                .alcoholicTaxAmount(alcoholicTaxAmount)
                .nonAlcoholicTaxAmount(nonAlcoholicTaxAmount)
                .alcoholicTaxableAmount(calculationResult.getAlcoholicTaxableAmount())
                .nonAlcoholicTaxableAmount(calculationResult.getNonAlcoholicTaxableAmount())
                .serviceChargeAmount(serviceChargeAmount)
                .packingChargeAmount(packingChargeAmount)
                .additionalDiscountValue(request.getAdditionalDiscountValue())
                .additionalDiscountType(request.getAdditionalDiscountType())
                .additionalDiscountAmount(additionalDiscountSavings)
                .totalAmount(totalAmount)
                .email(request.getEmail())
                .createdAt(OffsetDateTime.now(ZoneOffset.UTC))
                .createdBy(createdBy)
                .updatedAt(OffsetDateTime.now(ZoneOffset.UTC))
                .updatedBy(updatedBy)
                .build();

        // Save order
        // Sequence generation uses atomic INSERT ... ON CONFLICT ... RETURNING
        // which ensures thread-safety at the database level, eliminating the need for retry loops
        order = orderRepository.save(order);

        // ==================== WEBSOCKET NOTIFICATION - ORDER STATUS ====================
        /*
         * Disabled: do not broadcast order-status websocket updates when a waiter places an order.
         *
         * try {
         *     orderNotificationService.sendOrderStatusWebSocketNotification(userLocale, restaurantId, order.getId(), order.getOrderStatus());
         * } catch (Exception e) {
         *     log.error("Failed to send WebSocket notification for order creation: {}", e.getMessage());
         * }
         */

        // ==================== CREATE ORDERED ITEMS ====================
        // Maintain mapping of created item IDs to original requests for response building
        Map<UUID, OrderedItemRequest> itemRequestMap = new HashMap<>();
        List<OrderedItem> createdOrderedItems = new ArrayList<>();
        List<OrderedCombo> createdOrderedCombos = new ArrayList<>();
        
        // Track if we need to send KDS notification for PUSHED items/combos
        boolean hasPushedItems = false;
        boolean hasPushedCombos = false;
        
        // ==================== BATCH LOAD ENTITIES TO AVOID N+1 QUERIES ====================
        // Load all entities upfront in batch queries (fresh from database, not a persistent cache)
        // This ensures we get updated data while avoiding N+1 query performance issues
        Map<UUID, Item> itemsMap = new HashMap<>();
        Map<UUID, ModifierItem> modifierItemsMap = new HashMap<>();
        Map<UUID, ModifierGroup> modifierGroupsMap = new HashMap<>();
        
        if (request.getOrderedItems() != null && !request.getOrderedItems().isEmpty()) {
            // Collect all unique item IDs
            List<UUID> itemIds = request.getOrderedItems().stream()
                    .map(OrderedItemRequest::getItemId)
                    .distinct()
                    .collect(Collectors.toList());
            
            // Batch load all items in one query (fresh from database)
            if (!itemIds.isEmpty()) {
                List<Item> items = itemRepository.findAllById(itemIds);
                itemsMap = items.stream()
                        .collect(Collectors.toMap(Item::getId, Function.identity()));
            }
            
            // Collect all modifier item IDs and modifier group IDs
            Set<UUID> modifierItemIds = new HashSet<>();
            Set<UUID> modifierGroupIds = new HashSet<>();
            
            for (OrderedItemRequest itemRequest : request.getOrderedItems()) {
                if (itemRequest.getOrderedItemModifiers() != null) {
                    for (OrderedItemModifierRequest modifierRequest : itemRequest.getOrderedItemModifiers()) {
                        modifierGroupIds.add(modifierRequest.getModifierGroupId());
                        if (modifierRequest.getModifierItemIds() != null) {
                            modifierItemIds.addAll(modifierRequest.getModifierItemIds());
                        }
                    }
                }
            }
            
            // Batch load all modifier items in one query (fresh from database)
            if (!modifierItemIds.isEmpty()) {
                List<ModifierItem> modifierItems = modifierItemRepository.findAllById(modifierItemIds);
                modifierItemsMap = modifierItems.stream()
                        .collect(Collectors.toMap(ModifierItem::getId, Function.identity()));
            }
            
            // Batch load all modifier groups in one query (fresh from database)
            if (!modifierGroupIds.isEmpty()) {
                List<ModifierGroup> modifierGroups = modifierGroupRepository.findAllById(modifierGroupIds);
                modifierGroupsMap = modifierGroups.stream()
                        .collect(Collectors.toMap(ModifierGroup::getId, Function.identity()));
            }
        }
        
        if (request.getOrderedItems() != null && !request.getOrderedItems().isEmpty()) {
            for (OrderedItemRequest itemRequest : request.getOrderedItems()) {
                OrderedItem createdItem = orderedItemService.createNewOrderedItem(order, itemRequest, request.getMenuId(), restaurantId, activeOverrideIndex, createdBy, userLocale, bxgyResult.getItemPrices(), bxgyResult.getGetItemPrices(), bxgyResult.getPaidQuantitiesByRequest(), itemsMap, modifierItemsMap, modifierGroupsMap, bxgyResult.getBxgyInfoByRequest());
                createdOrderedItems.add(createdItem);
                
                // Map created item ID to original request
                itemRequestMap.put(createdItem.getId(), itemRequest);
                
                // ==================== WEBSOCKET NOTIFICATION - ITEM STATUS ====================
                // Only broadcast ON_HOLD status (non-KDS). All KDS-specific statuses (PUSHED, SERVED,
                // CANCELED, COOKING, READY, DELAYED) are sent via user-scoped methods to prevent
                // cross-category KDS notification leaks (e.g., bar KDS seeing kitchen items).
                try {
                    if (createdItem.getItemStatus() == ItemStatus.ON_HOLD) {
                        orderNotificationService.sendItemStatusWebSocketNotification(userLocale, restaurantId, createdItem.getId(), createdItem.getItemStatus(), "item");
                    }
                } catch (Exception e) {
                    log.error("Failed to send WebSocket notification for item creation: {}", e.getMessage());
                }
                
                // Track if any item has PUSHED status (for batch KDS notification)
                if (createdItem.getItemStatus() == ItemStatus.PUSHED) {
                    hasPushedItems = true;
                }
            }
        }
        
        // ==================== CREATE ORDERED COMBOS ====================
        if (request.getOrderedCombos() != null && !request.getOrderedCombos().isEmpty()) {
            for (OrderedComboRequest comboRequest : request.getOrderedCombos()) {
                OrderedCombo createdCombo = orderedComboService.createNewOrderedCombo(order, comboRequest, request.getMenuId(), createdBy, userLocale);
                createdOrderedCombos.add(createdCombo);
                
                // ==================== WEBSOCKET NOTIFICATION - COMBO STATUS (as item-status) ====================
                // Only broadcast ON_HOLD status. All KDS-specific statuses are sent via user-scoped methods.
                try {
                    if (createdCombo.getItemStatus() == ItemStatus.ON_HOLD) {
                        orderNotificationService.sendItemStatusWebSocketNotification(userLocale, restaurantId, createdCombo.getId(), createdCombo.getItemStatus(), TYPE_COMBO);
                    }
                } catch (Exception e) {
                    log.error("Failed to send WebSocket notification for combo creation: {}", e.getMessage());
                }
                
                // Track if any combo has PUSHED status (for batch KDS notification)
                if (createdCombo.getItemStatus() == ItemStatus.PUSHED) {
                    hasPushedCombos = true;
                }
            }
        }
        
        // ==================== KDS NOTIFICATION - ITEM PUSHED (PER ITEM, CATEGORY-SCOPED) ====================
        // Send KDS push notification for EACH PUSHED item individually.
        // notifyItemPushed uses findKdsRecipientsForItem which resolves assigned KDS users and target station ids based on
        // the specific item's category. Each item may belong to a different category (e.g., Bar vs Kitchen),
        // so we MUST call it per item to ensure each KDS device only gets notifications for its
        // assigned categories.
        if ((hasPushedItems || hasPushedCombos) && order != null) {
            try {
                List<OrderedItem> pushedItems = createdOrderedItems.stream()
                        .filter(item -> item.getItemStatus() == ItemStatus.PUSHED)
                        .collect(Collectors.toList());

                if (hasPushedCombos) {
                    for (OrderedCombo pushedCombo : createdOrderedCombos) {
                        if (pushedCombo.getItemStatus() != ItemStatus.PUSHED) {
                            continue;
                        }
                        List<OrderedItem> comboItems = orderedItemRepository.findByOrderedComboId(pushedCombo.getId());
                        if (comboItems != null && !comboItems.isEmpty()) {
                            pushedItems.addAll(comboItems.stream()
                                    .filter(comboItem -> comboItem.getItemStatus() == ItemStatus.PUSHED)
                                    .collect(Collectors.toList()));
                        }
                    }
                }
                
                for (OrderedItem pushedItem : pushedItems) {
                    notifyItemPushedBestEffort(pushedItem, userLocale);
                }
            } catch (Exception e) {
                log.error("Failed to send item pushed notifications to KDS users: {}", e.getMessage());
            }
        }
        
        // ==================== RECALCULATE SUBTOTAL FROM STORED PRICES ====================
        List<OrderedItem> savedOrderedItems = createdOrderedItems.stream()
                .filter(item -> item.getOrderedCombo() == null) // Only regular items, not combo items
                .collect(Collectors.toList());
        // CRITICAL: Use totalDiscountedItemAmount for all items (both get and buy items)
        // For get items: totalDiscountedItemAmount = modifier price * quantity (item is free)
        // For buy items: totalDiscountedItemAmount = discounted price + modifier price * quantity
        // This ensures the subtotal matches what was calculated in calculateSubTotalWithBxgyDiscounts
        BigDecimal recalculatedSubTotal = savedOrderedItems.stream()
                .map(this::resolveSubtotalItemAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        
        // Add combo prices to subtotal (price already includes quantity)
        List<OrderedCombo> savedOrderedCombos = createdOrderedCombos;
        BigDecimal comboSubTotal = savedOrderedCombos.stream()
                .map(oc -> oc.getTotalComboAmount() != null ? oc.getTotalComboAmount() : oc.getPrice())
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        
        recalculatedSubTotal = recalculatedSubTotal.add(comboSubTotal);
        
        // ==================== RECALCULATE TOTALS FROM STORED ENTITIES (ITEM-AWARE SPLIT) ====================
        // calculateCompleteOrderTotals uses stored item alcohol types to produce an accurate breakdown.
        List<OrderedItemRequest> calculationOrderedItemRequests = savedOrderedItems.stream()
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
        
        List<OrderedComboRequest> calculationOrderedComboRequests = savedOrderedCombos.stream()
                .map(oc -> OrderedComboRequest.builder()
                        .orderedComboId(oc.getId())
                        .comboId(oc.getCombo() != null ? oc.getCombo().getComboId() : null)
                        .quantity(oc.getQuantity())
                        .notes(oc.getNotes())
                        .build())
                .filter(req -> req.getComboId() != null && req.getQuantity() != null)
                .collect(Collectors.toList());
        
        com.gulfnet.shared_library.model.response.dto.OrderCalculationResult recalculatedResult = orderPricingService.calculateCompleteOrderTotals(
                calculationOrderedItemRequests,
                calculationOrderedComboRequests,
                request.getMenuId(),
                restaurantId,
                activeOverrideIndex,
                orderDiscount,
                request.getAdditionalDiscountValue(),
                request.getAdditionalDiscountType(),
                orderType,
                userLocale);
        
        // Format subtotal according to currency after calculation
        String currency = restaurantChainConfigProperties.getChain().getCurrency();
        order.setSubTotal(CurrencyFormatter.formatAmount(recalculatedResult.getSubTotal(), currency));
        
        // Update order with recalculated amounts (including alcohol split)
        order.setDiscountAmount(recalculatedResult.getOrderDiscountSavings());
        order.setTaxAmount(recalculatedResult.getTaxAmount());
        order.setAlcoholicTaxAmount(recalculatedResult.getAlcoholicTaxAmount());
        order.setNonAlcoholicTaxAmount(recalculatedResult.getNonAlcoholicTaxAmount());
        order.setAlcoholicTaxableAmount(recalculatedResult.getAlcoholicTaxableAmount());
        order.setNonAlcoholicTaxableAmount(recalculatedResult.getNonAlcoholicTaxableAmount());
        order.setServiceChargeAmount(recalculatedResult.getServiceChargeAmount());
        order.setPackingChargeAmount(recalculatedResult.getPackingChargeAmount());
        order.setAdditionalDiscountAmount(recalculatedResult.getAdditionalDiscountSavings());
        order.setTotalAmount(recalculatedResult.getTotalAmount());
        
        
        order = orderRepository.save(order);
        
        // ==================== INCREMENT DISCOUNT USAGE COUNT & LOG ACTUAL USAGE ====================
        if (orderDiscount != null && orderDiscount.getMaxUses() != null) {
            // Check if this is the last available use
            boolean isLastUse = (orderDiscount.getCurrentUsage() + 1) >= orderDiscount.getMaxUses();
            
            // Increment current usage count for order-level discount
            orderDiscount.setCurrentUsage(orderDiscount.getCurrentUsage() + 1);
            discountRepository.save(orderDiscount);
            
            if (isLastUse) {
                log.warn("Discount {} has reached maximum usage limit: {}/{}", 
                        orderDiscount.getId(), orderDiscount.getCurrentUsage(), orderDiscount.getMaxUses());
            }
        }
        
        // ==================== CREATE TRANSACTION RECORD ====================
        Transaction transaction = createTransactionRecord(order, session, restaurant, waiter, createdBy, userLocale);

        // For TAKEAWAY orders, transaction is created as PENDING at order placement time.
        // Send the same checkout-initiated websocket notification used by initiateCheckout (OPEN -> PENDING),
        // so clients see the transaction move into "checkout initiated" state immediately.
        try {
            if (orderType == OrderType.TAKEAWAY && transaction.getTransactionStatus() == TransactionStatus.PENDING) {
                UUID checkoutRestaurantId = orderNotificationService.getRestaurantIdFromOrder(order);
                orderNotificationService.sendCheckoutInitiatedTransactionStatusWebSocketNotification(
                        userLocale, checkoutRestaurantId, transaction.getId(), order, TransactionStatus.PENDING);
            }
        } catch (Exception e) {
            log.warn("Failed to send checkout-initiated transaction websocket for TAKEAWAY order {}: {}",
                    order != null ? order.getId() : null, e.getMessage());
        }

        // ==================== LOG ORDER-LEVEL DISCOUNT USAGE FOR REPORTS ====================
        if (orderDiscount != null && recalculatedResult.getOrderDiscountSavings() != null
                && recalculatedResult.getOrderDiscountSavings().compareTo(BigDecimal.ZERO) > 0) {
            try {
                OrderDiscountUsage usage = OrderDiscountUsage.builder()
                        .restaurant(restaurant)
                        .order(order)
                        .transaction(transaction)
                        .orderedItem(null)
                        .discount(orderDiscount)
                        .discountCode(orderDiscount.getDiscountCode())
                        .discountType("Order")
                        .appliedTo(ENTITY_TYPE_ORDER)
                        .discountAmount(recalculatedResult.getOrderDiscountSavings())
                        .build();
                orderDiscountUsageRepository.save(usage);
            } catch (Exception e) {
                log.error("Failed to persist order discount usage for order {} and discount {}: {}",
                        order.getId(), orderDiscount.getId(), e.getMessage(), e);
            }
        }

        // ==================== LOG ADDITIONAL DISCOUNT USAGE FOR REPORTS ====================
        if (recalculatedResult.getAdditionalDiscountSavings() != null
                && recalculatedResult.getAdditionalDiscountSavings().compareTo(BigDecimal.ZERO) > 0) {
            try {
                OrderDiscountUsage usage = OrderDiscountUsage.builder()
                        .restaurant(restaurant)
                        .order(order)
                        .transaction(transaction)
                        .orderedItem(null)
                        .discount(null)
                        .discountCode("ADDITIONAL")
                        .discountType("Additional Discount")
                        .appliedTo(ENTITY_TYPE_ORDER)
                        .discountAmount(recalculatedResult.getAdditionalDiscountSavings())
                        .build();
                orderDiscountUsageRepository.save(usage);
            } catch (Exception e) {
                log.error("Failed to persist additional discount usage for order {}: {}",
                        order.getId(), e.getMessage(), e);
            }
        }

        // ==================== LOG ITEM/CATEGORY/BXGY DISCOUNT USAGE FOR REPORTS ====================
        if (bxgyResult != null && bxgyResult.getDiscountUsages() != null) {
            for (DiscountUsageSummary summary : bxgyResult.getDiscountUsages()) {
                if (summary == null || summary.getAmount() == null
                        || summary.getAmount().compareTo(BigDecimal.ZERO) <= 0
                        || summary.getDiscount() == null) {
                    continue;
                }
                try {
                    OrderDiscountUsage usage = OrderDiscountUsage.builder()
                            .restaurant(restaurant)
                            .order(order)
                            .transaction(transaction)
                            .orderedItem(null) // aggregated per discount per order
                            .discount(summary.getDiscount())
                            .discountCode(summary.getDiscount().getDiscountCode())
                            .discountType(summary.getDiscountType())
                            .appliedTo(summary.getAppliedTo())
                            .discountAmount(summary.getAmount())
                            .build();
                    orderDiscountUsageRepository.save(usage);
                } catch (Exception e) {
                    log.error("Failed to persist item/category/BXGY discount usage for order {} and discount {}: {}",
                            order.getId(),
                            summary.getDiscount() != null ? summary.getDiscount().getId() : null,
                            e.getMessage(), e);
                }
            }
        }
        
        // ==================== WEBSOCKET NOTIFICATION - TRANSACTION STATUS ====================
        /*
         * Disabled: do not broadcast transaction-status websocket updates when a waiter places an order.
         *
         * try {
         *     orderNotificationService.sendTransactionStatusWebSocketNotification(userLocale, restaurantId, transaction.getId(), transaction.getTransactionStatus());
         *     log.info("Sent WebSocket notification for transaction creation: {} with status: {} for restaurant: {}",
         *             transaction.getId(), transaction.getTransactionStatus(), restaurantId);
         * } catch (Exception e) {
         *     log.error("Failed to send WebSocket notification for transaction creation: {}", e.getMessage());
         * }
         */
        
        order = orderRepository.findById(order.getId()).orElse(order);

        // ==================== NOTIFY WAITER - ORDER PLACED ====================
        // Only notify if order is placed by customer (not by waiter themselves)
        try {
            if (order.getRestaurantTable() != null) {
                // Check if the order was placed by a waiter - if so, skip notification
                boolean isPlacedByWaiter = false;
                if (hasUserId && authenticatedUser != null && authenticatedUser.getRoleId() != null) {
                    isPlacedByWaiter = orderValidationService.isUserWaiter(authenticatedUser);
                }
                
                if (!isPlacedByWaiter) {
                    // Get all waiters assigned to the table to notify all of them
                    final Order orderForNotification = order;
                    List<User> assignedWaiters = orderValidationService.getWaitersForTable(order.getRestaurantTable());
                    notifyAssignedWaitersSafe(assignedWaiters, order.getId(), order.getRestaurantTable(),
                            waiter1 -> notificationService.notifyOrderPlaced(orderForNotification, waiter1, userLocale),
                            "order placed");
                } else {
                    log.info("Order {} placed by waiter {} - skipping notification", order.getId(), authenticatedUser.getId());
                }
            }
        } catch (Exception e) {
            log.error("Failed to send order placed notification: {}", e.getMessage(), e);
        }

        // Build response with request mapping for BXGY info
        OrderResponse orderResponse = buildOrderResponse(order, subtotalAfterDiscount, itemRequestMap);
        OrderDto<OrderResponse> orderDto = OrderDto.<OrderResponse>builder().order(orderResponse).build();

        return ResponseDto.<OrderDto<OrderResponse>>builder()
                .message(messageUtil.getMessage("order.create.success", userLocale))
                .data(orderDto)
                .build();
    }

    @Override
    @Transactional
    public ResponseDto<OrderDto<OrderResponse>> updateOrder(String userId, String sessionIdHeader, UUID orderId, OrderRequest request, OrderType orderType) {
        Locale userLocale = LocaleContextHolder.getLocale();

        // ==================== VALIDATE EXISTING ORDER ====================
        Order existingOrder = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        messageUtil.getMessage(MSG_ORDER_NOT_FOUND, userLocale)));

        UUID existingOrderSessionId = existingOrder.getSession() != null ? existingOrder.getSession().getId() : null;
        orderValidationService.validateCustomerOrStaffOrderSessionAccess(
                userId, sessionIdHeader, request.getSessionId(), existingOrderSessionId, userLocale);

        // ==================== VALIDATE TRANSACTION STATUS ====================
        orderValidationService.validateTransactionNotCompleted(existingOrder, userLocale);


        // ==================== HANDLE AUTHENTICATION CONTEXT ====================
        User authenticatedUser = null;
        boolean hasUserId = false;

        // Check if userId is provided and not empty
        // ==================== HANDLE AUTHENTICATION CONTEXT ====================
        // Resolve staff user if present; customer JWTs carry session UUID as User-ID (not in users table)
        if (orderValidationService.isValidUserId(userId)) {
            authenticatedUser = orderValidationService.validateAndGetUserOrNull(userId, userLocale);
            if (authenticatedUser != null) {
                hasUserId = true;
                log.info("Order updated by authenticated user: {}", authenticatedUser.getId());
            } else {
                hasUserId = false;
                log.info("User id {} not found in users table, treating as anonymous order update", userId);
            }
        } else {
            hasUserId = false;
            log.info("Order updated anonymously (no userId provided)");
        }

        // ==================== VALIDATE SESSION ====================
        Session session = orderValidationService.validateAndGetSession(request.getSessionId(), userLocale);
        orderValidationService.validateSessionNotExpired(session, userLocale);

        // ==================== VALIDATE MENU ====================
        Menu menu = orderValidationService.validateAndGetMenu(request.getMenuId(), userLocale);

        // ==================== VALIDATE RESTAURANT AND TABLE ====================
        Restaurant restaurant = orderValidationService.validateAndGetRestaurant(session.getRestaurantId(), userLocale);
        orderValidationService.validateRestaurantActive(restaurant, userLocale);

        RestaurantTable restaurantTable = orderValidationService.validateAndGetRestaurantTable(session.getTableId(), userLocale);
        orderValidationService.validateRestaurantTableNotDeleted(restaurantTable, userLocale);

        // ==================== DETERMINE UPDATED_BY ====================
        User updatedBy = null;
        if (hasUserId && authenticatedUser != null) {
            updatedBy = authenticatedUser;
            log.info("Using authenticated user as updatedBy: {}", authenticatedUser.getId());
        } else {
            updatedBy = null;
            log.info("Anonymous order update - updatedBy will be null");
        }

        // ==================== VALIDATE ORDERED ITEMS ====================
        orderValidationService.validateOrderItems(request.getOrderedItems(), userLocale);
        
        // Validate item availability for restaurant - ONLY for new items (those without orderedItemId)
        // Existing items were already validated when order was created, so we don't re-validate them
        List<OrderedItemRequest> newItems = request.getOrderedItems() != null
            ? request.getOrderedItems().stream()
                .filter(item -> item.getOrderedItemId() == null) // Only new items
                .collect(Collectors.toList())
            : new ArrayList<>();
        
        if (!newItems.isEmpty()) {
            orderValidationService.validateItemAvailabilityForRestaurant(newItems, request.getRestaurantId(), request.getMenuId(), userLocale);
        }

        // ==================== VALIDATE ORDERED COMBOS ====================
        // Skip availability validation for updateOrder - allow updating orders even if combo is no longer available
        orderValidationService.validateOrderCombos(request.getOrderedCombos(), request.getMenuId(), userLocale, false);
        
        // ==================== BUILD ACTIVE PRICE OVERRIDE INDEX ====================
        // Determine restaurant ID for price override lookup (use request restaurantId if provided, otherwise session restaurantId)
        UUID restaurantId = restaurant.getId();
        
        // ==================== ENSURE BXGY DISCOUNT IDs ARE INCLUDED ====================
        // Ensure BXGY discount IDs are always included when isBuyItem or isGetItem is true
        if (request.getOrderedItems() != null && !request.getOrderedItems().isEmpty()) {
            for (OrderedItemRequest itemRequest : request.getOrderedItems()) {
                List<UUID> updatedDiscountIds = orderPricingService.ensureBxgyDiscountIdsIncluded(itemRequest, request.getMenuId());
                if (!updatedDiscountIds.equals(itemRequest.getDiscountIds())) {
                    itemRequest.setDiscountIds(updatedDiscountIds);
                }
            }
        }
        
        UUID restaurantIdForOverride = restaurantId;
        if (request.getRestaurantId() != null &&
            !request.getRestaurantId().equals(restaurantId)) {
            log.warn(LOG_RESTAURANT_ID_MISMATCH_FOR_OVERRIDE,
                restaurantId, request.getRestaurantId());
            restaurantIdForOverride = request.getRestaurantId();
        }
        
        PriceOverrideHelper.ActiveOverrideIndex activeOverrideIndex = 
                priceOverrideHelper.buildActiveOverrideIndex(restaurantIdForOverride);

        // ==================== CALCULATE BXGY DISCOUNTS FOR ALL ITEMS ====================
        // Calculate BXGY prices for all items (existing + new) to use when creating new items
        com.gulfnet.shared_library.model.response.dto.BxgyCalculationResult bxgyResult = orderPricingService.calculateSubTotalWithBxgyDiscounts(
                request.getOrderedItems(), 
                request.getOrderedCombos(), 
                request.getMenuId(),
                restaurantId,
                activeOverrideIndex);

        // ==================== PROCESS ITEMS FOR TOTAL CALCULATION ====================
        // Only consider REGULAR ordered items here (exclude combo items)
        List<OrderedItem> existingOrderedItems = orderedItemRepository.findByOrderId(orderId).stream()
                .filter(item -> item.getOrderedCombo() == null)
                .collect(Collectors.toList());
        
        // Build map of existing regular items by ID for lookup
        Map<UUID, OrderedItem> existingItemsMap = existingOrderedItems.stream()
                .collect(Collectors.toMap(OrderedItem::getId, item -> item));
        
        // Track items to include in total calculation
        List<OrderedItemRequest> itemsForCalculation = new ArrayList<>();
        
        // Track which existing items are being referenced (not deleted)
        Set<UUID> updatedItemIds = new HashSet<>();
        
        // Maintain mapping of created item IDs to original requests for response building
        Map<UUID, OrderedItemRequest> itemRequestMap = new HashMap<>();
        
        // Track if new items are being added (for cashier transaction status logic)
        boolean newItemsAdded = false;
        
        // ==================== PROCESS EACH REQUEST ITEM ====================
        if (request.getOrderedItems() != null && !request.getOrderedItems().isEmpty()) {
            for (OrderedItemRequest itemRequest : request.getOrderedItems()) {
                if (itemRequest.getOrderedItemId() != null) {
                    // Existing item - use stored amounts from DB, don't recalculate
                    OrderedItem existingItem = existingItemsMap.get(itemRequest.getOrderedItemId());
                    if (existingItem != null) {
                        // Mark this item as referenced (not to be deleted)
                        updatedItemIds.add(existingItem.getId());
                        // Don't add to itemsForCalculation - will use stored amounts from DB
                        log.info("Existing item {} will use stored amounts from DB (totalDiscountedItemAmount: {}, totalItemAmount: {})", 
                                existingItem.getId(), existingItem.getTotalDiscountedItemAmount(), existingItem.getTotalItemAmount());
                    }
                } else {
                    // Create new item and include in calculation
                    newItemsAdded = true; // Track that new items are being added
                    OrderedItem createdItem = orderedItemService.createNewOrderedItem(existingOrder, itemRequest, request.getMenuId(), restaurantIdForOverride, 
                            activeOverrideIndex, updatedBy, userLocale, bxgyResult.getItemPrices(), bxgyResult.getGetItemPrices(), bxgyResult.getPaidQuantitiesByRequest(), bxgyResult.getBxgyInfoByRequest());
                    
                    // Map created item ID to original request
                    itemRequestMap.put(createdItem.getId(), itemRequest);
                    
                    // ==================== WEBSOCKET NOTIFICATION - ITEM STATUS ====================
                    // Broadcast ON_HOLD and PUSHED to waiters/cashiers via /topic/restaurant/{restaurantId}/item-status.
                    // KDS-specific handling is still done via user-scoped notifications.
                    try {
                        if (createdItem.getItemStatus() == ItemStatus.ON_HOLD || createdItem.getItemStatus() == ItemStatus.PUSHED) {
                            orderNotificationService.sendItemStatusWebSocketNotification(userLocale, restaurantId, createdItem.getId(), createdItem.getItemStatus(), "item");
                            log.debug("Sent WebSocket notification for new item in order update: {} with status: {} for restaurant: {}", 
                                    createdItem.getId(), createdItem.getItemStatus(), restaurantId);
                        }
                    } catch (Exception e) {
                        log.error("Failed to send WebSocket notification for new item in order update: {}", e.getMessage());
                    }
                    
                    // Send user-specific KDS WebSocket notification for PUSHED items added during order update
                    // KDS: /topic/restaurant/{restaurantId}/kds/{kdsStationId}/item-status (see NotificationServiceImpl)
                    if (createdItem.getItemStatus() == ItemStatus.PUSHED) {
                        try {
                            notificationService.notifyItemPushed(createdItem, userLocale);
                            log.info("Sent KDS WebSocket notification (station-scoped topic) for PUSHED item {} (restaurant {}) added by cashier",
                                    createdItem.getId(), restaurantId);
                        } catch (Exception e) {
                            log.error("Failed to send KDS item pushed notification for new item {} in order update: {}", 
                                    createdItem.getId(), e.getMessage());
                        }
                    }
                    
                    // Only add new items to itemsForCalculation - existing items will use stored amounts
                    itemsForCalculation.add(itemRequest);
                }
                
                // Also add to mapping for existing items that are being updated
                if (itemRequest.getOrderedItemId() != null) {
                    itemRequestMap.put(itemRequest.getOrderedItemId(), itemRequest);
                }
            }
        }

        // ==================== HARD DELETE REGULAR ITEMS NOT IN REQUEST ====================
        // Only delete regular items; combo items are handled via combo reconciliation
        for (OrderedItem existingItem : existingOrderedItems) {
            if (!updatedItemIds.contains(existingItem.getId())) {
                // Delete modifiers first (foreign key constraint)
                List<OrderedItemModifier> existingModifiers = orderedItemModifierRepository.findByOrderedItemId(existingItem.getId());
                orderedItemModifierRepository.deleteAll(existingModifiers);
                
                // Delete the ordered item
                orderedItemRepository.delete(existingItem);
            }
        }

        // ==================== PROCESS COMBOS FOR TOTAL CALCULATION ====================
        List<OrderedCombo> existingOrderedCombos = orderedComboRepository.findByOrderId(orderId);
        
        // Build map of existing combos by ID for lookup
        Map<UUID, OrderedCombo> existingCombosMap = existingOrderedCombos.stream()
                .collect(Collectors.toMap(OrderedCombo::getId, combo -> combo));
        
        // Track which existing combos are being referenced (not deleted)
        Set<UUID> updatedComboIds = new HashSet<>();
        
        // ==================== PROCESS EACH REQUEST COMBO ====================
        if (request.getOrderedCombos() != null && !request.getOrderedCombos().isEmpty()) {
            for (OrderedComboRequest comboRequest : request.getOrderedCombos()) {
                if (comboRequest.getOrderedComboId() != null) {
                    // Keep existing combo as-is (mark as referenced, don't modify)
                    OrderedCombo existingCombo = existingCombosMap.get(comboRequest.getOrderedComboId());
                    if (existingCombo != null) {
                        // Mark this combo as referenced (not to be deleted)
                        updatedComboIds.add(existingCombo.getId());
                        // Ignore any changes to quantity, notes, or combo groups
                        log.info("Keeping existing combo {} as-is (ignoring any changes in payload)", existingCombo.getId());
                    }
                } else {
                    // Create new combo
                    newItemsAdded = true; // Track that new combos are being added
                    OrderedCombo createdCombo = orderedComboService.createNewOrderedCombo(existingOrder, comboRequest, request.getMenuId(), updatedBy, userLocale);
                    
                    // ==================== WEBSOCKET NOTIFICATION - COMBO STATUS (as item-status) ====================
                    // Broadcast ON_HOLD and PUSHED to waiters/cashiers via /topic/restaurant/{restaurantId}/item-status.
                    // KDS-specific handling is still done via user-scoped notifications.
                    sendComboStatusWsBestEffort(userLocale, restaurantId, createdCombo);
                    
                    // Send user-specific KDS WebSocket notification for PUSHED combos added during order update
                    // KDS: /topic/restaurant/{restaurantId}/kds/{kdsStationId}/item-status (see NotificationServiceImpl)
                    if (createdCombo.getItemStatus() == ItemStatus.PUSHED) {
                        try {
                            // Notify each PUSHED item in this combo individually for category-scoped KDS notification
                            List<OrderedItem> comboItems = orderedItemRepository.findByOrderedComboId(createdCombo.getId());
                            int pushedItemsCount = 0;
                            for (OrderedItem comboItem : comboItems) {
                                if (comboItem.getItemStatus() == ItemStatus.PUSHED) {
                                    try {
                                        // Also broadcast pushed combo items to waiters/cashiers
                                        orderNotificationService.sendItemStatusWebSocketNotification(
                                                userLocale, restaurantId, comboItem.getId(), comboItem.getItemStatus(), TYPE_ITEM);

                                        notifyItemPushedBestEffort(comboItem, userLocale);
                                        pushedItemsCount++;
                                    } catch (Exception e) {
                                        log.error("Failed to send KDS item pushed notification for combo item {} in order update: {}", 
                                                comboItem.getId(), e.getMessage());
                                    }
                                }
                            }
                            if (pushedItemsCount > 0) {
                                log.info("Sent KDS WebSocket notifications (station-scoped topic) for {} PUSHED item(s) in combo {} (restaurant {}) added by cashier",
                                        pushedItemsCount, createdCombo.getId(), restaurantId);
                            }
                        } catch (Exception e) {
                            log.error("Failed to send KDS item pushed notification for new combo {} in order update: {}", 
                                    createdCombo.getId(), e.getMessage());
                        }
                    }
                }
            }
            
            // Only delete combos if the request explicitly specifies which ones to keep
            // If request has orderedCombos array, delete the ones not mentioned
            for (OrderedCombo existingCombo : existingOrderedCombos) {
                if (!updatedComboIds.contains(existingCombo.getId())) {
                    log.info("Deleting combo {} because it's not in the update request", existingCombo.getId());
                    // Delete combo items first (due to foreign key constraint)
                    List<OrderedItem> comboItems = orderedItemRepository.findByOrderedComboId(existingCombo.getId());
                    for (OrderedItem comboItem : comboItems) {
                        // Delete modifiers first
                        List<OrderedItemModifier> modifiers = orderedItemModifierRepository.findByOrderedItemId(comboItem.getId());
                        orderedItemModifierRepository.deleteAll(modifiers);
                        
                        // Delete the combo item
                        orderedItemRepository.delete(comboItem);
                    }
                    
                    // Delete the ordered combo
                    orderedComboRepository.delete(existingCombo);
                }
            }
        } else {
            // If no combos in request, keep all existing combos (don't delete anything)
            log.info("No combos in update request, keeping all existing combos");
        }
        
        // Flush to ensure all combo changes are persisted before order recalculation
        orderRepository.flush();

        // ==================== STORE PREVIOUS DISCOUNT INFO BEFORE UPDATE ====================
        // Store discount ID and maxUses before entity manager is cleared to avoid lazy loading issues
        UUID previousDiscountIdTemp = null;
        Integer previousDiscountMaxUsesTemp = null;
        Integer previousDiscountCurrentUsageTemp = null;
        
        Discount existingDiscount = existingOrder.getDiscount();
        if (existingDiscount != null) {
            try {
                // Try to get the discount ID - this may trigger lazy loading, which is fine here
                previousDiscountIdTemp = existingDiscount.getId();
                previousDiscountMaxUsesTemp = existingDiscount.getMaxUses();
                previousDiscountCurrentUsageTemp = existingDiscount.getCurrentUsage();
            } catch (Exception e) {
                // If discount is a proxy and not initialized, try to initialize it
                Hibernate.initialize(existingDiscount);
                if (existingDiscount != null) {
                    previousDiscountIdTemp = existingDiscount.getId();
                    previousDiscountMaxUsesTemp = existingDiscount.getMaxUses();
                    previousDiscountCurrentUsageTemp = existingDiscount.getCurrentUsage();
                }
            }
        }
        
        // Make final copies for use in later code
        final UUID previousDiscountId = previousDiscountIdTemp;
        final Integer previousDiscountMaxUses = previousDiscountMaxUsesTemp;
        final Integer previousDiscountCurrentUsage = previousDiscountCurrentUsageTemp;
        
        // ==================== UPDATE ORDER ENTITY (fields from request) ====================
        existingOrder.setSession(session);
        existingOrder.setRestaurant(restaurant);
        existingOrder.setRestaurantTable(restaurantTable);
        existingOrder.setOrderType(orderType);
        existingOrder.setAdditionalDiscountValue(request.getAdditionalDiscountValue());
        existingOrder.setAdditionalDiscountType(request.getAdditionalDiscountType());
        existingOrder.setEmail(request.getEmail());
        existingOrder.setUpdatedAt(OffsetDateTime.now(ZoneOffset.UTC));
        existingOrder.setUpdatedBy(updatedBy);

        // ==================== APPLY/SET ORDER-LEVEL DISCOUNT IF PROVIDED ====================
        if (request.getDiscountId() != null) {
            // Validate and fetch discount
            Discount orderDiscount = orderValidationService.validateAndGetOrderDiscount(request.getDiscountId(), userLocale);

            // Must be ORDER-level discount
            if (orderDiscount.getAppliedTo() != AppliedTo.ORDER) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        messageUtil.getMessage("discount.not.order.level", userLocale));
            }

            // Attach discount to order so the recalculation path can apply it
            existingOrder.setDiscount(orderDiscount);
            existingOrder.setDiscountCode(orderDiscount.getDiscountCode());
            existingOrder.setDiscountValue(orderDiscount.getValue());
            existingOrder.setDiscountType(orderDiscount.getDiscountType());
        } else {
            // If discountId is not in payload, remove existing discount and clear discount fields
            if (previousDiscountId != null) {
                log.info("Removing discount {} from order {} because discountId is not in update payload", 
                        previousDiscountId, existingOrder.getId());
            }
            
            // Clear all order-level discount fields
            existingOrder.setDiscount(null);
            existingOrder.setDiscountCode(null);
            existingOrder.setDiscountValue(null);
            existingOrder.setDiscountType(null);
            existingOrder.setDiscountAmount(BigDecimal.ZERO);
            
            // Note: We do NOT clear additionalDiscountValue and additionalDiscountType 
            // as those are separate fields that may be updated independently
        }

        existingOrder = orderRepository.save(existingOrder);

        // ==================== FLUSH ALL CHANGES BEFORE RECALCULATION ====================
        // Ensure all new items and their amounts are persisted before recalculation
        orderedItemRepository.flush();
        orderedItemModifierRepository.flush();
        
        // ==================== RECALCULATE ORDER TOTALS (SAME AS CREATE ORDER) ====================
        // This will calculate subtotal, discounts, taxes, service charges, and total amount
        // Pass itemsForCalculation to preserve BXGY info (isBuyItem, isGetItem, freeQuantity) for new items
        orderRecalculationService.recalculateOrderAfterItemChange(existingOrder, updatedBy, updatedBy != null, userLocale, null, itemsForCalculation);
        
        // ==================== RE-FETCH ORDER AFTER RECALCULATION ====================
        // RecalculateOrderAfterItemChange updates and saves the order, so we need to re-fetch it
        // to get the updated values (subtotal, tax, etc.)
        // Clear entity manager to ensure we get fresh data
        entityManager.clear();
        existingOrder = orderRepository.findById(existingOrder.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        messageUtil.getMessage(MSG_ORDER_NOT_FOUND, userLocale)));
        
        log.info("Order {} re-fetched after recalculation - SubTotal: {}, TotalAmount: {}", 
                existingOrder.getId(), existingOrder.getSubTotal(), existingOrder.getTotalAmount());
        
        // ==================== SET ORDER STATUS TO IN_PROGRESS ====================
        existingOrder.setOrderStatus(OrderStatus.IN_PROGRESS);
        existingOrder.setUpdatedAt(OffsetDateTime.now(ZoneOffset.UTC));
        if (updatedBy != null) {
            existingOrder.setUpdatedBy(updatedBy);
        }
        orderRepository.save(existingOrder);
        log.info("Order {} status set to IN_PROGRESS after update", existingOrder.getId());
        
        // ==================== SET TRANSACTION STATUS TO OPEN ====================
        // Skip transaction status update if cashier is adding new items/combos OR applying/changing order-level discount
        // (cashier discount application should not force transaction back to OPEN)
        boolean isCashier = updatedBy != null && orderValidationService.isCashier(updatedBy);
        boolean isCashierAddingItems = newItemsAdded && isCashier;
        boolean isCashierApplyingDiscount =
                isCashier &&
                request.getDiscountId() != null &&
                !Objects.equals(request.getDiscountId(), previousDiscountId);
        // Waiter-added lines are ON_HOLD until pushed; skip cashier pop-ups for order + transaction WS on that path only.
        boolean isWaiterAddingNewLines = newItemsAdded && hasUserId && authenticatedUser != null
                && authenticatedUser.getRoleId() != null
                && orderValidationService.isUserWaiter(authenticatedUser);

        if (isCashierAddingItems || isCashierApplyingDiscount) {
            UUID updatedByIdForLog = updatedBy != null ? updatedBy.getId() : null;
            if (isCashierAddingItems) {
                log.info("Skipping transaction status update - cashier {} is adding new items/combos to order {}",
                        updatedByIdForLog, existingOrder.getId());
            } else {
                log.info("Skipping transaction status update - cashier {} is applying/changing order discount {} (previous: {}) for order {}",
                        updatedByIdForLog, request.getDiscountId(), previousDiscountId, existingOrder.getId());
            }
        } else {
            Optional<Transaction> transactionOpt = transactionRepository.findByOrderId(existingOrder.getId());
            Transaction transaction;
            
            if (transactionOpt.isPresent()) {
                // Update existing transaction to OPEN if not already OPEN
                transaction = transactionOpt.get();
                if (transaction.getTransactionStatus() != TransactionStatus.OPEN) {
                    transaction.setTransactionStatus(TransactionStatus.OPEN);
                    transaction.setUpdatedAt(OffsetDateTime.now(ZoneOffset.UTC));
                    transactionRepository.save(transaction);
                    log.info("Transaction {} status set to OPEN for order {}", transaction.getId(), existingOrder.getId());
                    
                    // ==================== WEBSOCKET NOTIFICATION - TRANSACTION STATUS ====================
                    if (!isWaiterAddingNewLines) {
                        try {
                            orderNotificationService.sendTransactionStatusWebSocketNotification(userLocale, restaurantId, transaction.getId(), transaction.getTransactionStatus());
                            log.info("Sent WebSocket notification for transaction status update: {} to {} for restaurant: {}", 
                                    transaction.getId(), transaction.getTransactionStatus(), restaurantId);
                        } catch (Exception e) {
                            log.error("Failed to send WebSocket notification for transaction status update: {}", e.getMessage());
                        }
                    } else {
                        log.debug("Skipping transaction status WebSocket for waiter ON_HOLD add order {}", existingOrder.getId());
                    }
                }
            } else {
                // Create new transaction with OPEN status
                transaction = Transaction.builder()
                        .order(existingOrder)
                        .restaurant(existingOrder.getRestaurant())
                        .session(existingOrder.getSession())
                        .transactionNumber(generateTransactionNumber(existingOrder.getRestaurant()))
                        .paymentMethod(null)
                        .transactionStatus(TransactionStatus.OPEN)
                        .cashier(null)
                        .createdAt(OffsetDateTime.now(ZoneOffset.UTC))
                        .updatedAt(OffsetDateTime.now(ZoneOffset.UTC))
                        .build();
                transaction = transactionRepository.save(transaction);
                log.info("Created new transaction with OPEN status for order {}", existingOrder.getId());
                
                // ==================== WEBSOCKET NOTIFICATION - TRANSACTION STATUS ====================
                if (!isWaiterAddingNewLines) {
                    try {
                        orderNotificationService.sendTransactionStatusWebSocketNotification(userLocale, restaurantId, transaction.getId(), transaction.getTransactionStatus());
                        log.info("Sent WebSocket notification for transaction creation in order update: {} with status: {} for restaurant: {}", 
                                transaction.getId(), transaction.getTransactionStatus(), restaurantId);
                    } catch (Exception e) {
                        log.error("Failed to send WebSocket notification for transaction creation in order update: {}", e.getMessage());
                    }
                } else {
                    log.debug("Skipping transaction status WebSocket for waiter ON_HOLD add order {}", existingOrder.getId());
                }
            }
        }
        
        // ==================== HANDLE DISCOUNT USAGE COUNT CHANGES ====================
        // Get the current discount after recalculation
        Discount currentDiscount = existingOrder.getDiscount();
        UUID currentDiscountId = currentDiscount != null ? currentDiscount.getId() : null;
        
        // If there was a previous discount and it's different from the new one, decrement its usage
        if (previousDiscountId != null && currentDiscountId != null && 
            !previousDiscountId.equals(currentDiscountId) && 
            previousDiscountMaxUses != null) {
            
            // Re-fetch the previous discount to avoid lazy loading issues with detached entity
            Discount previousDiscount = discountRepository.findById(previousDiscountId)
                    .orElseThrow(() -> new RuntimeException("Previous discount not found: " + previousDiscountId));
            
            // Decrement previous discount usage count
            int newUsage = Math.max(0, previousDiscount.getCurrentUsage() - 1);
            previousDiscount.setCurrentUsage(newUsage);
            discountRepository.save(previousDiscount);
            log.info("Decremented usage count for previous discount {}: {}/{}", 
                    previousDiscountId, newUsage, previousDiscountMaxUses);
        }
        
        // If there was a previous discount but no new discount, just decrement the previous one
        if (previousDiscountId != null && currentDiscountId == null && previousDiscountMaxUses != null) {
            // Re-fetch the previous discount to avoid lazy loading issues with detached entity
            Discount previousDiscount = discountRepository.findById(previousDiscountId)
                    .orElseThrow(() -> new RuntimeException("Previous discount not found: " + previousDiscountId));
            
            // Decrement previous discount usage count
            int newUsage = Math.max(0, previousDiscount.getCurrentUsage() - 1);
            previousDiscount.setCurrentUsage(newUsage);
            discountRepository.save(previousDiscount);
            log.info("Decremented usage count for removed discount {}: {}/{}", 
                    previousDiscountId, newUsage, previousDiscountMaxUses);
        }
        
        // Only increment usage count if there's a NEW discount (different from previous or no previous discount)
        if (currentDiscount != null && currentDiscount.getMaxUses() != null) {
            boolean isNewDiscount = (previousDiscountId == null) || 
                                  (!previousDiscountId.equals(currentDiscountId));
            
            if (isNewDiscount) {
                // Check if this is the last available use
                boolean isLastUse = (currentDiscount.getCurrentUsage() + 1) >= currentDiscount.getMaxUses();
                
                // Increment current usage count for new order-level discount
                currentDiscount.setCurrentUsage(currentDiscount.getCurrentUsage() + 1);
                discountRepository.save(currentDiscount);
                
                if (isLastUse) {
                    log.warn("Discount {} has reached maximum usage limit: {}/{}", 
                            currentDiscountId, currentDiscount.getCurrentUsage(), currentDiscount.getMaxUses());
                } else {
                    log.info("Incremented usage count for new discount {}: {}/{}", 
                            currentDiscountId, currentDiscount.getCurrentUsage(), currentDiscount.getMaxUses());
                }
            } else {
                log.info("Discount {} is the same as previous, no usage count change needed", currentDiscountId);
            }
        }

        // Fetch transaction for logging usages (if any)
        Transaction usageTransaction = null;
        try {
            usageTransaction = transactionRepository.findByOrderId(existingOrder.getId()).orElse(null);
        } catch (Exception e) {
            log.warn("Could not fetch transaction for order {} while logging discount usage: {}",
                    existingOrder.getId(), e.getMessage());
        }

        // ==================== LOG ORDER-LEVEL DISCOUNT USAGE FOR REPORTS (UPDATE PATH) ====================
        BigDecimal updatedOrderDiscountAmount = existingOrder.getDiscountAmount();
        if (currentDiscount != null
                && updatedOrderDiscountAmount != null
                && updatedOrderDiscountAmount.compareTo(BigDecimal.ZERO) > 0) {
            try {
                OrderDiscountUsage usage = OrderDiscountUsage.builder()
                        .restaurant(existingOrder.getRestaurant())
                        .order(existingOrder)
                        .transaction(usageTransaction)
                        .orderedItem(null)
                        .discount(currentDiscount)
                        .discountCode(currentDiscount.getDiscountCode())
                        .discountType("Order")
                        .appliedTo(ENTITY_TYPE_ORDER)
                        .discountAmount(updatedOrderDiscountAmount)
                        .build();
                orderDiscountUsageRepository.save(usage);
            } catch (Exception e) {
                log.error("Failed to persist order discount usage (update) for order {} and discount {}: {}",
                        existingOrder.getId(),
                        currentDiscount.getId(),
                        e.getMessage(), e);
            }
        }

        // ==================== LOG ADDITIONAL DISCOUNT USAGE FOR REPORTS (UPDATE PATH) ====================
        BigDecimal updatedAdditionalDiscountAmount = existingOrder.getAdditionalDiscountAmount();
        if (updatedAdditionalDiscountAmount != null
                && updatedAdditionalDiscountAmount.compareTo(BigDecimal.ZERO) > 0) {
            try {
                OrderDiscountUsage usage = OrderDiscountUsage.builder()
                        .restaurant(existingOrder.getRestaurant())
                        .order(existingOrder)
                        .transaction(usageTransaction)
                        .orderedItem(null)
                        .discount(null)
                        .discountCode("ADDITIONAL")
                        .discountType("Additional Discount")
                        .appliedTo(ENTITY_TYPE_ORDER)
                        .discountAmount(updatedAdditionalDiscountAmount)
                        .build();
                orderDiscountUsageRepository.save(usage);
            } catch (Exception e) {
                log.error("Failed to persist additional discount usage (update) for order {}: {}",
                        existingOrder.getId(), e.getMessage(), e);
            }
        }

        // ==================== LOG ITEM/CATEGORY/BXGY DISCOUNT USAGE FOR REPORTS (UPDATE PATH) ====================
        if (bxgyResult != null && bxgyResult.getDiscountUsages() != null) {
            for (DiscountUsageSummary summary : bxgyResult.getDiscountUsages()) {
                if (summary == null || summary.getAmount() == null
                        || summary.getAmount().compareTo(BigDecimal.ZERO) <= 0
                        || summary.getDiscount() == null) {
                    continue;
                }
                try {
                    OrderDiscountUsage usage = OrderDiscountUsage.builder()
                            .restaurant(existingOrder.getRestaurant())
                            .order(existingOrder)
                            .transaction(usageTransaction)
                            .orderedItem(null)
                            .discount(summary.getDiscount())
                            .discountCode(summary.getDiscount().getDiscountCode())
                            .discountType(summary.getDiscountType())
                            .appliedTo(summary.getAppliedTo())
                            .discountAmount(summary.getAmount())
                            .build();
                    orderDiscountUsageRepository.save(usage);
                } catch (Exception e) {
                    log.error("Failed to persist item/category/BXGY discount usage (update) for order {} and discount {}: {}",
                            existingOrder.getId(),
                            summary.getDiscount() != null ? summary.getDiscount().getId() : null,
                            e.getMessage(), e);
                }
            }
        }
        
        // Create audit trail for ORDER_MODIFICATION action
        if (hasUserId && authenticatedUser != null) {
            try {
                auditTrailService.createAuditTrail(
                        authenticatedUser,
                        ActionType.ORDER_MODIFICATION,
                        existingOrder.getRestaurant(),
                        RequestStatus.NA, // Non-request action - always NA
                        null, // IP address not available
                        null, // User agent not available
                        existingOrder.getId(),
                        ENTITY_TYPE_ORDER,
                        String.format("Order updated: Order ID %s", existingOrder.getId())
                );
            } catch (Exception e) {
                log.error("Failed to create audit trail for order modification: {}", e.getMessage());
            }
        }

        // ==================== WEBSOCKET NOTIFICATION - ORDER UPDATE ====================
        if (!isWaiterAddingNewLines) {
            try {
                restaurantId = existingOrder.getRestaurant().getId();
                orderNotificationService.sendOrderStatusWebSocketNotification(userLocale, restaurantId, existingOrder.getId(), existingOrder.getOrderStatus());
            } catch (Exception e) {
                log.error("Failed to send WebSocket notification for order update: {}", e.getMessage());
            }
        } else {
            log.debug("Skipping order status WebSocket for waiter ON_HOLD add order {}", existingOrder.getId());
        }

        // ==================== NOTIFY WAITER - ORDER UPDATED ====================
        // Only notify if order is updated by customer (not by waiter themselves)
        try {
            if (existingOrder.getRestaurantTable() != null) {
                // Check if the order was updated by a waiter - if so, skip notification
                boolean isUpdatedByWaiter = false;
                if (hasUserId && authenticatedUser != null && authenticatedUser.getRoleId() != null) {
                    isUpdatedByWaiter = orderValidationService.isUserWaiter(authenticatedUser);
                }
                
                if (!isUpdatedByWaiter) {
                    // Get all waiters assigned to the table to notify all of them
                    final Order existingOrderForNotification = existingOrder;
                    List<User> assignedWaiters = orderValidationService.getWaitersForTable(existingOrder.getRestaurantTable());
                    notifyAssignedWaitersSafe(assignedWaiters, existingOrder.getId(), existingOrder.getRestaurantTable(),
                            waiter1 -> notificationService.notifyOrderUpdated(existingOrderForNotification, waiter1, userLocale),
                            "order updated");
                } else {
                    log.info("Order {} updated by waiter {} - skipping notification", existingOrder.getId(), authenticatedUser.getId());
                }
            }
        } catch (Exception e) {
            log.error("Failed to send order updated notification: {}", e.getMessage(), e);
        }

        // Calculate subtotal after discount for response
        BigDecimal subtotalAfterDiscount = null;
        if (existingOrder.getDiscount() != null && existingOrder.getDiscountAmount() != null && 
            existingOrder.getDiscountAmount().compareTo(BigDecimal.ZERO) > 0) {
            subtotalAfterDiscount = existingOrder.getSubTotal().subtract(existingOrder.getDiscountAmount());
        }
        
        // Build response with request mapping for BXGY info
        OrderResponse orderResponse = buildOrderResponse(existingOrder, subtotalAfterDiscount, itemRequestMap);
        OrderDto<OrderResponse> orderDto = OrderDto.<OrderResponse>builder().order(orderResponse).build();

        return ResponseDto.<OrderDto<OrderResponse>>builder()
                .message(messageUtil.getMessage("order.update.success", userLocale))
                .data(orderDto)
                .build();
    }

    /**
     * Calculates order totals including items, combos, discounts, taxes, and charges.
     * Validates items and combos, applies discounts, and computes final totals without creating an order.
     *
     * @param userId    the ID of the user calculating the order
     * @param request   the order request containing items, combos, and discount information
     * @param orderType the type of order (DINE_IN or TAKEAWAY)
     * @return {@link ResponseDto} containing calculated order totals and item details
     */
    @Override
    public ResponseDto<OrderDto<OrderResponse>> calculateOrder(String userId, String sessionIdHeader, OrderRequest request, OrderType orderType) {
        log.debug("Calculating order for user: {} with order type: {}", userId, orderType);
        
        Locale userLocale = LocaleContextHolder.getLocale();

        orderValidationService.validateCustomerOrStaffSessionAccess(
                userId, sessionIdHeader, request.getSessionId(), userLocale);
        
        // ==================== VALIDATE REQUEST ====================
        orderValidationService.validateOrderItems(request.getOrderedItems(), userLocale);
        
        
        // ==================== VALIDATE ORDERED COMBOS (SKIP AVAILABILITY FOR EXISTING COMBOS) ====================
        // Validate all combos, but skip availability validation for existing ones (those with orderedComboId)
        if (request.getOrderedCombos() != null && !request.getOrderedCombos().isEmpty()) {
            for (OrderedComboRequest comboRequest : request.getOrderedCombos()) {
                // Skip availability validation if this is an existing combo (has orderedComboId)
                boolean skipAvailability = comboRequest.getOrderedComboId() != null;
                orderValidationService.validateCombo(comboRequest, request.getMenuId(), userLocale, !skipAvailability);
            }
        }
        
        // ==================== VALIDATE BXGY GET ITEMS QUANTITY ====================
        // Filter out existing items (those with orderedItemId) before validating BXGY
        List<OrderedItemRequest> itemsForBxgyValidation = request.getOrderedItems() != null 
            ? request.getOrderedItems().stream()
                .filter(item -> item.getOrderedItemId() == null) // Only validate new items
                .collect(Collectors.toList())
            : new ArrayList<>();
        
        if (!itemsForBxgyValidation.isEmpty()) {
            orderValidationService.validateBxgyGetItemsQuantity(itemsForBxgyValidation, request.getMenuId(), userLocale);
        }
        
        // ==================== GET SESSION AND RESTAURANT ====================
        Session session = orderValidationService.validateAndGetSession(request.getSessionId(), userLocale);
        
        Restaurant restaurant = orderValidationService.validateAndGetRestaurant(session.getRestaurantId(), userLocale);
        
        UUID restaurantId = restaurant.getId();
        
        // If request provides restaurantId, use it for price override lookup if different from session
        // This allows price overrides to be looked up for a different restaurant if specified
        UUID restaurantIdForOverride = restaurantId;
        if (request.getRestaurantId() != null && !restaurantId.equals(request.getRestaurantId())) {
            log.warn(LOG_RESTAURANT_ID_MISMATCH_FOR_OVERRIDE,
                restaurantId, request.getRestaurantId());
            if (restaurantRepository.findById(request.getRestaurantId()).isPresent()) {
                restaurantIdForOverride = request.getRestaurantId();
            } else {
                log.warn("Request restaurant ID {} not found, using session restaurant ID {}",
                        request.getRestaurantId(), restaurantId);
            }
        }
        
        // ==================== GET RESTAURANT TABLE ====================
        RestaurantTable restaurantTable = orderValidationService.validateAndGetRestaurantTable(session.getTableId(), userLocale);
        orderValidationService.validateRestaurantTableNotDeleted(restaurantTable, userLocale);
        
        // ==================== GET WAITER FROM TABLE ASSIGNMENT ====================
        // COMMENTED OUT: Waiter assignment logic to avoid multiple results error
        User waiter = null;
        
        // ==================== BUILD ACTIVE PRICE OVERRIDE INDEX ====================
        // Use restaurantIdForOverride which may be from request if provided and different
        PriceOverrideHelper.ActiveOverrideIndex activeOverrideIndex = 
                priceOverrideHelper.buildActiveOverrideIndex(restaurantIdForOverride);
        
        // ==================== ORDER-LEVEL DISCOUNT VALIDATION ====================
        Discount orderDiscount = null;
        String discountCode = null;
        BigDecimal discountAmount = null;
        DiscountType discountType = null;

        if (request.getDiscountId() != null) {
            orderDiscount = orderValidationService.validateAndGetOrderDiscount(request.getDiscountId(), userLocale);
            
            // Validate that this is an ORDER-level discount
            orderValidationService.validateOrderLevelDiscountType(orderDiscount, userLocale);
            
            discountCode = orderDiscount.getDiscountCode();
            discountAmount = orderDiscount.getValue();
            discountType = orderDiscount.getDiscountType();
        } else if (request.getDiscountCode() != null && !request.getDiscountCode().trim().isEmpty()) {
            // Support discount code lookup (for cases where discount code is provided instead of ID)
            Optional<Discount> discountOpt = discountRepository.findByDiscountCodeAndIsDeletedFalse(request.getDiscountCode());
            
            if (discountOpt.isEmpty()) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                        messageUtil.getMessage("discount.not.found", userLocale));
            }
            
            orderDiscount = discountOpt.get();
            
            // Validate discount is active and not deleted
            if (orderDiscount.getStatus() != EntityStatus.ACTIVE || orderDiscount.getIsDeleted()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        messageUtil.getMessage("discount.not.active", userLocale));
            }
            
            // Validate usage limits
            if (orderDiscount.getMaxUses() != null && orderDiscount.getMaxUses() > 0 
                    && orderDiscount.getCurrentUsage() >= orderDiscount.getMaxUses()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        messageUtil.getMessage("discount.usage.limit.exceeded", userLocale));
            }
            
            // Validate that this is an ORDER-level discount
            orderValidationService.validateOrderLevelDiscountType(orderDiscount, userLocale);
            
            discountCode = orderDiscount.getDiscountCode();
            discountAmount = orderDiscount.getValue();
            discountType = orderDiscount.getDiscountType();
        }

        // ==================== FILTER OUT CANCELLED ITEMS AND COMBOS ====================
        // Exclude cancelled items from calculation - they should not be included in order total
        List<OrderedItemRequest> activeOrderedItems = orderValidationService.filterOutCancelledItems(request.getOrderedItems(), userLocale);
        List<OrderedComboRequest> activeOrderedCombos = orderValidationService.filterOutCancelledCombos(request.getOrderedCombos(), userLocale);
        
        log.debug("CALCULATE ORDER - Filtered items: {} active out of {} total, Filtered combos: {} active out of {} total",
            activeOrderedItems.size(), 
            request.getOrderedItems() != null ? request.getOrderedItems().size() : 0,
            activeOrderedCombos.size(),
            request.getOrderedCombos() != null ? request.getOrderedCombos().size() : 0);

        // ==================== CALCULATE COMPLETE ORDER TOTALS USING GENERIC METHOD ====================
        com.gulfnet.shared_library.model.response.dto.OrderCalculationResult calculationResult = orderPricingService.calculateCompleteOrderTotals(
                activeOrderedItems,
                activeOrderedCombos,
                request.getMenuId(),
                restaurantId,
                activeOverrideIndex,
                orderDiscount,
                request.getAdditionalDiscountValue(),
                request.getAdditionalDiscountType(),
                orderType,
                userLocale);
        
        // Extract calculated values
        BigDecimal subTotal = calculationResult.getSubTotal();
        BigDecimal subtotalAfterDiscount = calculationResult.getSubtotalAfterDiscount();
        BigDecimal taxAmount = calculationResult.getTaxAmount();
        BigDecimal alcoholicTaxAmount = calculationResult.getAlcoholicTaxAmount();
        BigDecimal nonAlcoholicTaxAmount = calculationResult.getNonAlcoholicTaxAmount();
        BigDecimal alcoholicTaxableAmount = calculationResult.getAlcoholicTaxableAmount();
        BigDecimal nonAlcoholicTaxableAmount = calculationResult.getNonAlcoholicTaxableAmount();
        BigDecimal serviceChargeAmount = calculationResult.getServiceChargeAmount();
        BigDecimal packingChargeAmount = calculationResult.getPackingChargeAmount();
        BigDecimal additionalDiscountSavings = calculationResult.getAdditionalDiscountSavings();
        BigDecimal totalAmount = calculationResult.getTotalAmount();
        BigDecimal orderDiscountSavings = calculationResult.getOrderDiscountSavings();
        com.gulfnet.shared_library.model.response.dto.BxgyCalculationResult bxgyResult = calculationResult.getBxgyResult();
        
        // ==================== BUILD CALCULATION RESPONSE ====================
        Map<String, Integer> paidQuantitiesMap = bxgyResult.getPaidQuantitiesByRequest();
        log.debug("CALCULATE ORDER - Passing paidQuantitiesByRequest to buildCalculationResponse, map size: {}, keys: {}", 
            paidQuantitiesMap != null ? paidQuantitiesMap.size() : 0, 
            paidQuantitiesMap != null ? paidQuantitiesMap.keySet() : "null");
        CalculationResponseContext responseContext = new CalculationResponseContext(
                request, session, restaurant, restaurantTable, waiter, orderType,
                subTotal, subtotalAfterDiscount,
                discountCode, discountAmount, discountType, orderDiscountSavings,
                taxAmount, alcoholicTaxAmount, nonAlcoholicTaxAmount,
                alcoholicTaxableAmount, nonAlcoholicTaxableAmount,
                serviceChargeAmount, packingChargeAmount,
                additionalDiscountSavings, totalAmount,
                activeOrderedItems,
                bxgyResult.getItemPrices(), bxgyResult.getGetItemPrices(), paidQuantitiesMap,
                request.getMenuId(), restaurantId, activeOverrideIndex, userLocale
        );
        OrderResponse orderResponse = buildCalculationResponse(responseContext);
        
        return ResponseDto.<OrderDto<OrderResponse>>builder()
                .message(messageUtil.getMessage("order.calculation.success", userLocale))
                .data(OrderDto.<OrderResponse>builder()
                        .order(orderResponse)
                        .build())
                .build();
    }

    /**
     * Retrieves all orders associated with a specific session ID.
     * Returns orders grouped by session with order details.
     *
     * @param userId    the ID of the user requesting the orders
     * @param sessionId the UUID of the session to get orders for
     * @return {@link ResponseDto} containing list of orders for the session
     */
    @Override
    public ResponseDto<OrderDto<List<OrderResponse>>> getOrdersBySessionId(String userId, String sessionIdHeader, UUID sessionId) {
        log.info("Getting orders for session: {} by user: {}", sessionId, userId);
        
        Locale userLocale = LocaleContextHolder.getLocale();

        orderValidationService.validateCustomerOrStaffSessionAccess(userId, sessionIdHeader, sessionId, userLocale);
        
        // ==================== VALIDATE SESSION ====================
        Session session = orderValidationService.validateAndGetSession(sessionId, userLocale);
        
        // ==================== GET ORDERS FOR SESSION ====================
        List<Order> orders = orderRepository.findBySessionIdOrderByCreatedAtDescWithTableWaiterRestaurant(sessionId);
        
        if (orders.isEmpty()) {
            return ResponseDto.<OrderDto<List<OrderResponse>>>builder()
                    .message(messageUtil.getMessage(MSG_ORDER_LIST_EMPTY, userLocale))
                    .data(OrderDto.<List<OrderResponse>>builder()
                            .order(new ArrayList<>())
                            .build())
                    .build();
        }
        
        // Calculate session start and end times
        OffsetDateTime startTime = session.getIssuedAt();
        OffsetDateTime endTime = resolveSessionEndTime(orders);

        // ==================== BUILD ORDER RESPONSES ====================
        OrderResponseBatchContext batchCtx = buildOrderResponseBatchContext(orders);
        List<OrderResponse> orderResponses = withOrderResponseBatchContext(batchCtx, () ->
                orders.stream()
                        .map(order -> {
                            // Get subtotal after discount for each order
                            BigDecimal subtotalAfterDiscount = null;
                            if (order.getDiscount() != null && order.getDiscountAmount() != null &&
                                    order.getDiscountAmount().compareTo(BigDecimal.ZERO) > 0) {
                                subtotalAfterDiscount = order.getSubTotal().subtract(order.getDiscountAmount());
                            }

                            OrderResponse response = buildOrderResponse(order, subtotalAfterDiscount);
                            response.setStartTime(startTime);
                            response.setEndTime(endTime);
                            return response;
                        })
                        .collect(Collectors.toList()));
        
        return ResponseDto.<OrderDto<List<OrderResponse>>>builder()
                .message(messageUtil.getMessage(MSG_ORDER_LIST_SUCCESS, userLocale))
                .data(OrderDto.<List<OrderResponse>>builder()
                        .order(orderResponses)
                        .build())
                .build();
    }

    /**
     * Retrieves all orders for a specific table, handling both static and dynamic sessions.
     * Groups orders by session and returns table information with order details.
     *
     * @param userId  the ID of the user requesting the orders
     * @param tableId the UUID of the table to get orders for
     * @return {@link ResponseDto} containing table information and grouped orders by session
     */
    @Override
    public ResponseDto<TableDto<TableOrderResponseDto>> getOrdersByTableId(String userId, UUID tableId) {
        log.info("Getting orders for table: {} by user: {}", tableId, userId);
        
        Locale userLocale = LocaleContextHolder.getLocale();
        
        // ==================== FIND ACTIVE SESSIONS FOR TABLE ====================
        List<Session> activeSessions = findActiveSessionsForTable(tableId, userLocale);
        
        // ==================== GET TABLE INFORMATION ====================
        RestaurantTable table = orderValidationService.validateAndGetRestaurantTable(tableId, userLocale);
        
        // Build table information
        TableListResponse tableResponse = buildTableListResponse(table, activeSessions, userLocale);
        
        // ==================== DETERMINE QR TYPE AND HANDLE ACCORDINGLY ====================
        QrCodeType qrCodeType = activeSessions.get(0).getQrCodeType();
        
        ResponseDto<TableOrderResponseDto> innerResponse;
        if (qrCodeType == QrCodeType.STATIC) {
            // STATIC: Only 1 session, get orders for that session
            innerResponse = getOrdersForStaticSessionGrouped(activeSessions.get(0), userLocale, tableResponse);
        } else {
            // DYNAMIC: Multiple sessions possible, get orders grouped by session
            innerResponse = getOrdersForDynamicSessionsGrouped(activeSessions, userLocale, tableResponse);
        }
        
        // Wrap in TableDto
        return ResponseDto.<TableDto<TableOrderResponseDto>>builder()
                .message(innerResponse.getMessage())
                .data(TableDto.<TableOrderResponseDto>builder()
                        .table(innerResponse.getData())
                        .build())
                .build();
    }
    
    /**
     * Finds all active (non-expired) sessions for a specific table.
     * Validates that the table exists before querying sessions.
     *
     * @param tableId    the UUID of the table to find sessions for
     * @param userLocale locale for localized error messages
     * @return list of active {@link Session} entities for the table
     * @throws ResponseStatusException if table is not found
     */
    private List<Session> findActiveSessionsForTable(UUID tableId, Locale userLocale) {
        // 1. Validate table exists
        orderValidationService.validateAndGetRestaurantTable(tableId, userLocale);
        
        // 2. Find active sessions for the table (expiredAt IS NULL)
        List<Session> activeSessions = sessionRepository.findByTableIdAndExpiredAtIsNull(tableId);
        
        if (activeSessions.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                    messageUtil.getMessage("no.active.session.for.table", userLocale));
        }
        
        log.info("Found {} active sessions for table {}", activeSessions.size(), tableId);
        return activeSessions;
    }

    /**
     * Builds a table list response DTO from table entity and active sessions.
     * Includes table details, section information, and session summaries.
     *
     * @param table          the restaurant table entity
     * @param activeSessions  list of active sessions for the table
     * @param userLocale     locale for localized names
     * @return {@link TableListResponse} with table and session information
     */
    private TableListResponse buildTableListResponse(RestaurantTable table, List<Session> activeSessions, Locale userLocale) {
        log.info("Building table response for tableId={}", table.getId());
        
        // Access lazy-loaded relationships
        RestaurantRow row = table.getRestaurantRow();
        RestaurantSection section = row != null ? row.getRestaurantSection() : null;
        
        // Get section name with fallback logic
        final String[] sectionName = {"NA"};
        if (section != null && section.getTranslations() != null && !section.getTranslations().isEmpty()) {
            // Try exact language match first
            section.getTranslations().stream()
                    .filter(t -> t.getLanguageCode() != null && t.getLanguageCode().equals(userLocale.getLanguage()))
                    .findFirst()
                    .ifPresentOrElse(
                            t -> sectionName[0] = t.getName() != null && !t.getName().trim().isEmpty() ? t.getName() : "NA",
                            () -> section.getTranslations().stream()
                                        .filter(t -> t.getName() != null && !t.getName().trim().isEmpty())
                                        .findFirst()
                                        .ifPresent(t -> sectionName[0] = t.getName())
                    );
        }
        
        // Get waiters assigned to the table
        List<WaiterInfo> waiters = new ArrayList<>();
        try {
            List<TableAssignment> tableAssignments = tableAssignmentRepository
                    .findByRestaurantTableIdAndUnassignedAtIsNullWithWaiter(table.getId());
            waiters = tableAssignments.stream()
                    .map(ta -> {
                        User waiter = ta.getWaiter();
                        if (waiter == null) return null;
                        String firstName = waiter.getFirstName() != null ? waiter.getFirstName() : "";
                        String lastName = waiter.getLastName() != null ? waiter.getLastName() : "";
                        String waiterName = (firstName + " " + lastName).trim();
                        return WaiterInfo.builder()
                                .id(waiter.getId())
                                .userCode(waiter.getUserCode())
                                .waiterName(waiterName)
                                .build();
                    })
                    .filter(java.util.Objects::nonNull)
                    .collect(Collectors.toList());
        } catch (Exception e) {
            log.error("Error getting waiters for table {}: {}", table.getId(), e.getMessage(), e);
        }
        
        // Determine order status, ready/pending items, and occupiedAt
        String orderStatus = null;
        int readyItems = 0;
        int pendingItems = 0;
        LocalDateTime occupiedAt = null;
        List<TableSessionInfo> sessions = new ArrayList<>();
        
        if (!activeSessions.isEmpty()) {
            // Get the earliest session issuedAt as occupiedAt
            occupiedAt = activeSessions.stream()
                    .map(Session::getIssuedAt)
                    .filter(java.util.Objects::nonNull)
                    .min(OffsetDateTime::compareTo)
                    .map(OffsetDateTime::toLocalDateTime)
                    .orElse(null);
            
            // Get item counts
            try {
                Long readyItemsCount = orderedItemRepository.countReadyItemsByTableId(table.getId());
                Long pendingItemsCount = orderedItemRepository.countPendingItemsByTableId(table.getId());
                readyItems = readyItemsCount != null ? readyItemsCount.intValue() : 0;
                pendingItems = pendingItemsCount != null ? pendingItemsCount.intValue() : 0;
            } catch (Exception e) {
                log.error("Error getting item counts for table {}: {}", table.getId(), e.getMessage(), e);
            }
            
            // Build session info list
            List<UUID> sessionIds = activeSessions.stream()
                    .map(Session::getId)
                    .collect(Collectors.toList());
            
            try {
                List<Order> allOrders = orderRepository.findBySessionIdsWithOrderedItems(sessionIds);
                Map<UUID, List<Order>> ordersBySession = allOrders.stream()
                        .collect(Collectors.groupingBy(order -> order.getSession().getId()));
                
                Order latestOrder = null;
                for (Session session : activeSessions) {
                    List<Order> sessionOrders = ordersBySession.get(session.getId());
                    OffsetDateTime sessionEndTime = resolveSessionEndTime(sessionOrders);
                    if (sessionOrders != null && !sessionOrders.isEmpty()) {
                        sessionOrders.sort((o1, o2) -> {
                            if (o1.getCreatedAt() == null && o2.getCreatedAt() == null) return 0;
                            if (o1.getCreatedAt() == null) return 1;
                            if (o2.getCreatedAt() == null) return -1;
                            return o2.getCreatedAt().compareTo(o1.getCreatedAt());
                        });
                        Order sessionLatestOrder = sessionOrders.get(0);
                        
                        if (latestOrder == null || 
                            (sessionLatestOrder.getCreatedAt() != null && latestOrder.getCreatedAt() != null &&
                             sessionLatestOrder.getCreatedAt().isAfter(latestOrder.getCreatedAt()))) {
                            latestOrder = sessionLatestOrder;
                        }
                        
                        TableSessionInfo sessionInfo = TableSessionInfo.builder()
                                .sessionId(session.getId())
                                .sequenceNo(session.getSequenceNo())
                                .orderId(sessionLatestOrder.getId())
                                .orderNumber(sessionLatestOrder.getOrderNumber())
                                .orderStatus(sessionLatestOrder.getOrderStatus() != null ? 
                                        sessionLatestOrder.getOrderStatus().name() : null)
                                .orderSubtotal(sessionLatestOrder.getSubTotal())
                                .startTime(session.getIssuedAt())
                                .endTime(sessionEndTime)
                                .build();
                        sessions.add(sessionInfo);
                    } else {
                        TableSessionInfo sessionInfo = TableSessionInfo.builder()
                                .sessionId(session.getId())
                                .sequenceNo(session.getSequenceNo())
                                .orderId(null)
                                .orderNumber(null)
                                .orderStatus(null)
                                .orderSubtotal(null)
                                .startTime(session.getIssuedAt())
                                .endTime(sessionEndTime)
                                .build();
                        sessions.add(sessionInfo);
                    }
                }
                
                if (latestOrder != null && latestOrder.getOrderStatus() != null) {
                    orderStatus = latestOrder.getOrderStatus().name();
                }
            } catch (Exception e) {
                log.error("Error building session info for table {}: {}", table.getId(), e.getMessage(), e);
            }
        }
        
        return TableListResponse.builder()
                .id(table.getId().toString())
                .tableCode(table.getTableCode())
                .tableOrder(table.getTableOrder())
                .rowOrder(row != null ? row.getRowOrder() : null)
                .rowId(row != null ? row.getId().toString() : null)
                .sectionId(section != null ? section.getId().toString() : null)
                .sectionName(sectionName[0])
                .capacity(table.getCapacity())
                .tableStatus(table.getTableStatus() != null ? table.getTableStatus().name() : null)
                .blockReason(table.getBlockReason())
                .orderStatus(orderStatus)
                .readyItems(readyItems)
                .pendingItems(pendingItems)
                .occupiedAt(occupiedAt)
                .sessions(sessions)
                .waiters(waiters)
                .build();
    }

    /**
     * Retrieves and groups orders for a static session (single session per table).
     * Returns orders for the session with table information.
     *
     * @param session      the static session to get orders for
     * @param userLocale   locale for localized names
     * @param tableResponse table information response
     * @return {@link ResponseDto} containing table order response with session orders
     */
    private ResponseDto<TableOrderResponseDto> getOrdersForStaticSessionGrouped(Session session, Locale userLocale, TableListResponse tableResponse) {
        log.info("Processing STATIC session {} for table {}", session.getId(), session.getTableId());
        
        // Get orders for the single session
        List<Order> orders = orderRepository.findBySessionIdOrderByCreatedAtDesc(session.getId());
        
        if (orders.isEmpty()) {
            return ResponseDto.<TableOrderResponseDto>builder()
                    .message(messageUtil.getMessage(MSG_ORDER_LIST_EMPTY, userLocale))
                    .data(buildTableOrderResponseDto(tableResponse, new ArrayList<>()))
                    .build();
        }
        
        // Calculate session start and end times
        OffsetDateTime startTime = session.getIssuedAt();
        OffsetDateTime endTime = resolveSessionEndTime(orders);
        
        // Build order responses using existing method
        List<OrderResponse> orderResponses = orders.stream()
                .map(order -> {
                    // Get subtotal after discount for each order
                    BigDecimal subtotalAfterDiscount = null;
                    if (order.getDiscount() != null && order.getDiscountAmount() != null && 
                        order.getDiscountAmount().compareTo(BigDecimal.ZERO) > 0) {
                        subtotalAfterDiscount = order.getSubTotal().subtract(order.getDiscountAmount());
                    }
                    
                    return buildOrderResponse(order, subtotalAfterDiscount);
                                    })
                .collect(Collectors.toList());
        
        // Create session wrapper
        SessionOrderWrapper sessionWrapper = SessionOrderWrapper.builder()
                .sessionId(session.getId())
                .sessionSequenceNo(null) // STATIC doesn't have sequence
                .orders(orderResponses)
                .startTime(startTime)
                .endTime(endTime)
                .build();
        
        return ResponseDto.<TableOrderResponseDto>builder()
                .message(messageUtil.getMessage(MSG_ORDER_LIST_SUCCESS, userLocale))
                .data(buildTableOrderResponseDto(tableResponse, Arrays.asList(sessionWrapper)))
                .build();
    }

    /**
     * Retrieves and groups orders for multiple dynamic sessions (multiple sessions per table).
     * Sorts sessions by sequence number and groups orders by session.
     *
     * @param sessions      list of dynamic sessions to get orders for
     * @param userLocale    locale for localized names
     * @param tableResponse table information response
     * @return {@link ResponseDto} containing table order response with grouped session orders
     */
    private ResponseDto<TableOrderResponseDto> getOrdersForDynamicSessionsGrouped(List<Session> sessions, Locale userLocale, TableListResponse tableResponse) {
        log.info("Processing {} DYNAMIC sessions for table {}", sessions.size(), sessions.get(0).getTableId());
        
        // Sort sessions by sequence number for proper ordering (handle null sequence numbers)
        sessions.sort(Comparator.comparing(Session::getSequenceNo, Comparator.nullsLast(Comparator.naturalOrder())));
        
        List<SessionOrderWrapper> sessionWrappers = new ArrayList<>();
        
        for (Session session : sessions) {
            log.info("Processing session {} with sequenceNo {}", session.getId(), session.getSequenceNo());
            
            // Get orders for this specific session
            List<Order> sessionOrders = orderRepository.findBySessionIdOrderByCreatedAtDesc(session.getId());
            
            if (!sessionOrders.isEmpty()) {
                // Calculate session start and end times
                OffsetDateTime startTime = session.getIssuedAt();
                OffsetDateTime endTime = resolveSessionEndTime(sessionOrders);
                
                // Build order responses using existing method
                List<OrderResponse> orderResponses = sessionOrders.stream()
                        .map(order -> {
                            // Get subtotal after discount for each order
                            BigDecimal subtotalAfterDiscount = null;
                            if (order.getDiscount() != null && order.getDiscountAmount() != null && 
                                order.getDiscountAmount().compareTo(BigDecimal.ZERO) > 0) {
                                subtotalAfterDiscount = order.getSubTotal().subtract(order.getDiscountAmount());
                            }
                            
                            OrderResponse response = buildOrderResponse(order, subtotalAfterDiscount);
                            response.setStartTime(startTime);
                            response.setEndTime(endTime);
                            return response;
                        })
                        .collect(Collectors.toList());
                
                // Create session wrapper for this session
                SessionOrderWrapper sessionWrapper = SessionOrderWrapper.builder()
                        .sessionId(session.getId())
                        .sessionSequenceNo(session.getSequenceNo())
                        .orders(orderResponses)
                        .startTime(startTime)
                        .endTime(endTime)
                        .build();
                
                sessionWrappers.add(sessionWrapper);
            }
        }
        
        if (sessionWrappers.isEmpty()) {
            return ResponseDto.<TableOrderResponseDto>builder()
                    .message(messageUtil.getMessage(MSG_ORDER_LIST_EMPTY, userLocale))
                    .data(buildTableOrderResponseDto(tableResponse, new ArrayList<>()))
                    .build();
        }
        
        return ResponseDto.<TableOrderResponseDto>builder()
                .message(messageUtil.getMessage(MSG_ORDER_LIST_SUCCESS, userLocale))
                .data(buildTableOrderResponseDto(tableResponse, sessionWrappers))
                .build();
    }

    /**
     * Builds a table order response DTO from table information and session order wrappers.
     *
     * @param tableResponse   table information response
     * @param sessionWrappers list of session order wrappers containing session and order information
     * @return {@link TableOrderResponseDto} with table and grouped session orders
     */
    private TableOrderResponseDto buildTableOrderResponseDto(TableListResponse tableResponse, List<SessionOrderWrapper> sessionWrappers) {
        return TableOrderResponseDto.builder()
                .id(tableResponse.getId())
                .tableCode(tableResponse.getTableCode())
                .tableOrder(tableResponse.getTableOrder())
                .sectionId(tableResponse.getSectionId())
                .sectionName(tableResponse.getSectionName())
                .capacity(tableResponse.getCapacity())
                .orderStatus(tableResponse.getOrderStatus())
                .tableStatus(tableResponse.getTableStatus())
                .readyItems(tableResponse.getReadyItems())
                .pendingItems(tableResponse.getPendingItems())
                .rowOrder(tableResponse.getRowOrder())
                .rowId(tableResponse.getRowId())
                .occupiedAt(tableResponse.getOccupiedAt())
                .session(sessionWrappers)
                .build();
    }

    private OrderResponse buildOrderResponse(Order order, BigDecimal subtotalAfterDiscount) {
        return buildOrderResponse(order, subtotalAfterDiscount, null);
    }
    
    /**
     * Builds an order response DTO from an order entity with calculated subtotal.
     * Includes items, combos, prices, discounts, taxes, and charges.
     *
     * @param order              the order entity to convert
     * @param subtotalAfterDiscount the calculated subtotal after applying discounts
     * @param itemRequestMap      optional map of item IDs to requests for additional context
     * @return {@link OrderResponse} with complete order details
     */
    private OrderResponse buildOrderResponse(Order order, BigDecimal subtotalAfterDiscount, Map<UUID, OrderedItemRequest> itemRequestMap) {
        OrderResponseBatchContext batchCtx = orderResponseBatchCtxHolder.get();

        // Get currency for formatting
        String currency = restaurantChainConfigProperties.getChain() != null ? restaurantChainConfigProperties.getChain().getCurrency() : null;
        
        BigDecimal alcoholicTaxableAmount = order.getAlcoholicTaxableAmount();
        BigDecimal nonAlcoholicTaxableAmount = order.getNonAlcoholicTaxableAmount();
        // Load only regular ordered items (not combo items) to show only what user explicitly ordered
        // Sort by createdAt descending so newest items appear on top
        List<OrderedItem> orderedItems = (batchCtx != null
                ? batchCtx.orderedItemsByOrderId().getOrDefault(order.getId(), Collections.emptyList())
                : orderedItemRepository.findByOrderId(order.getId()).stream()
                        .filter(item -> item.getOrderedCombo() == null) // Only regular items, not combo items
                        .collect(Collectors.toList()))
                .stream()
                .sorted(Comparator.comparing(OrderedItem::getCreatedAt, Comparator.nullsLast(Comparator.reverseOrder())))
                .collect(Collectors.toList());
        
        UUID restaurantId = orderNotificationService.getRestaurantIdFromOrder(order);
        UUID menuId = orderValidationService.getMenuIdFromOrder(order);
        
        List<OrderedItemResponse> orderedItemResponses = orderedItems.stream()
            .map(orderedItem -> {
                // Get original request if available (for create/update), otherwise reconstruct (for GET)
                OrderedItemRequest originalRequest = (itemRequestMap != null) 
                    ? itemRequestMap.get(orderedItem.getId()) 
                    : null;

                if (batchCtx == null) {
                    return orderedItemService.buildOrderedItemResponse(
                            orderedItem, originalRequest, restaurantId, menuId, LocaleContextHolder.getLocale());
                }

                List<OrderedItemModifier> preloadedModifiers =
                        batchCtx.modifiersByOrderedItemId().getOrDefault(orderedItem.getId(), Collections.emptyList());

                return orderedItemService.buildOrderedItemResponse(
                        orderedItem,
                        originalRequest,
                        restaurantId,
                        menuId,
                        LocaleContextHolder.getLocale(),
                        preloadedModifiers,
                        batchCtx.presignedUrlCache(),
                        null);
            })
            .collect(Collectors.toList());

        // Load ordered combos separately to avoid circular references
        // Sort by createdAt descending so newest combos appear on top
        List<OrderedCombo> orderedCombos = (batchCtx != null
                ? batchCtx.orderedCombosByOrderId().getOrDefault(order.getId(), Collections.emptyList())
                : orderedComboRepository.findByOrderId(order.getId()))
                .stream()
                .sorted(Comparator.comparing(OrderedCombo::getCreatedAt, Comparator.nullsLast(Comparator.reverseOrder())))
                .collect(Collectors.toList());
        
        List<OrderedComboResponse> orderedComboResponses = orderedCombos.stream()
            .map(orderedCombo -> orderedComboService.buildOrderedComboResponse(orderedCombo, LocaleContextHolder.getLocale()))
            .collect(Collectors.toList());

        // Determine if order discount was applied
        boolean hasOrderDiscount = order.getDiscount() != null && order.getDiscountAmount() != null && order.getDiscountAmount().compareTo(BigDecimal.ZERO) > 0;
        
        // Define valid discount variables
        UUID validDiscountId = hasOrderDiscount ? order.getDiscount().getId() : null;
        // Only set subtotalAfterDiscount if order discount exists
        BigDecimal validSubtotalAfterDiscount = hasOrderDiscount && subtotalAfterDiscount != null 
            ? CurrencyFormatter.formatAmount(subtotalAfterDiscount, currency) : null;
        String validDiscountCode = hasOrderDiscount ? order.getDiscount().getDiscountCode() : null;
        BigDecimal validDiscountValue = hasOrderDiscount && order.getDiscount().getValue() != null
            ? CurrencyFormatter.formatAmount(order.getDiscount().getValue(), currency) : null;
        BigDecimal validDiscountAmount = hasOrderDiscount && order.getDiscountAmount() != null
            ? CurrencyFormatter.formatAmount(order.getDiscountAmount(), currency) : null;
        DiscountType validDiscountType = hasOrderDiscount ? order.getDiscount().getDiscountType() : null;
        
        // Get transaction details
        Transaction transaction = (batchCtx != null)
                ? batchCtx.transactionByOrderId().get(order.getId())
                : transactionRepository.findByOrderId(order.getId()).orElse(null);
        
        // Get refundId from transaction using repository to avoid lazy loading issues
        UUID refundId = null;
        if (transaction != null) {
            try {
                if (batchCtx != null) {
                    refundId = batchCtx.refundIdByTransactionId().get(transaction.getId());
                } else {
                    Optional<Refund> refundOpt = refundRepository.findByTransactionId(transaction.getId());
                    if (refundOpt.isPresent()) {
                        refundId = refundOpt.get().getId();
                    }
                }
            } catch (Exception e) {
                log.debug(LOG_ERROR_FETCHING_REFUND_FOR_TRANSACTION, transaction.getId(), e.getMessage());
            }
        }
        
        // Get rating details
        OrderResponse.OrderRating rating = null;
        Rating orderRating = (batchCtx != null)
                ? batchCtx.ratingByOrderId().get(order.getId())
                : ratingRepository.findByOrderId(order.getId()).orElse(null);
        if (orderRating != null) {
            rating = OrderResponse.OrderRating.builder()
                    .experience(orderRating.getExperience())
                    .food(orderRating.getFood())
                    .service(orderRating.getService())
                    .feedback(orderRating.getFeedback())
                    .build();
        }
        
        // Calculate and display additional discount for APPROVED requests
        // Also calculate and display discount amount for OPEN requests (pending approval) for preview
        // Also display discount when directly applied by manager (status is NONE but fields are populated)
        // Do not show discount information for DECLINED requests
        BigDecimal additionalDiscountAmount = null;
        BigDecimal additionalDiscountValue = null;
        DiscountType additionalDiscountType = null;
        String additionalDiscountReason = null;
        
        if (order.getAdditionalDiscountRequestStatus() == RequestStatus.APPROVED
                || (order.getAdditionalDiscountRequestStatus() == RequestStatus.NONE
                    && (order.getAdditionalDiscountType() != null || order.getAdditionalDiscountValue() != null
                        || order.getAdditionalDiscountAmount() != null))) {
            // Request is approved, or discount was directly applied by manager (status NONE but fields populated)
            // Use stored discount amount or calculate if not set
            additionalDiscountAmount = order.getAdditionalDiscountAmount();
            if ((additionalDiscountAmount == null || additionalDiscountAmount.compareTo(BigDecimal.ZERO) == 0)
                    && order.getAdditionalDiscountType() != null 
                    && order.getAdditionalDiscountValue() != null) {
                BigDecimal subtotalForDiscount = subtotalAfterDiscount != null ? subtotalAfterDiscount : BigDecimal.ZERO;
                BigDecimal taxAmount = order.getTaxAmount() != null ? order.getTaxAmount() : BigDecimal.ZERO;
                BigDecimal serviceChargeAmount = order.getServiceChargeAmount() != null ? order.getServiceChargeAmount() : BigDecimal.ZERO;
                BigDecimal packingChargeAmount = order.getPackingChargeAmount() != null ? order.getPackingChargeAmount() : BigDecimal.ZERO;
                BigDecimal totalBeforeAdditionalDiscount = subtotalForDiscount.add(taxAmount).add(serviceChargeAmount).add(packingChargeAmount);
                
                if (totalBeforeAdditionalDiscount.compareTo(BigDecimal.ZERO) > 0) {
                    if (order.getAdditionalDiscountType() == DiscountType.PERCENT) {
                        additionalDiscountAmount = totalBeforeAdditionalDiscount.multiply(order.getAdditionalDiscountValue())
                                .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
                    } else if (order.getAdditionalDiscountType() == DiscountType.FLAT) {
                        additionalDiscountAmount = order.getAdditionalDiscountValue();
                    }
                }
            }
            additionalDiscountValue = order.getAdditionalDiscountValue();
            additionalDiscountType = order.getAdditionalDiscountType();
            additionalDiscountReason = order.getAdditionalDiscountReason();
        } else if (order.getAdditionalDiscountRequestStatus() == RequestStatus.OPEN
                && order.getAdditionalDiscountType() != null
                && order.getAdditionalDiscountValue() != null) {
            // Request is OPEN (pending approval) - calculate discount amount for preview
            BigDecimal subtotalForDiscount = subtotalAfterDiscount != null ? subtotalAfterDiscount : BigDecimal.ZERO;
            BigDecimal taxAmount = order.getTaxAmount() != null ? order.getTaxAmount() : BigDecimal.ZERO;
            BigDecimal serviceChargeAmount = order.getServiceChargeAmount() != null ? order.getServiceChargeAmount() : BigDecimal.ZERO;
            BigDecimal packingChargeAmount = order.getPackingChargeAmount() != null ? order.getPackingChargeAmount() : BigDecimal.ZERO;
            BigDecimal totalBeforeAdditionalDiscount = subtotalForDiscount.add(taxAmount).add(serviceChargeAmount).add(packingChargeAmount);

            if (totalBeforeAdditionalDiscount.compareTo(BigDecimal.ZERO) > 0) {
                if (order.getAdditionalDiscountType() == DiscountType.PERCENT) {
                    additionalDiscountAmount = totalBeforeAdditionalDiscount.multiply(order.getAdditionalDiscountValue())
                            .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
                } else if (order.getAdditionalDiscountType() == DiscountType.FLAT) {
                    additionalDiscountAmount = order.getAdditionalDiscountValue();
                }
            }

            additionalDiscountValue = order.getAdditionalDiscountValue();
            additionalDiscountType = order.getAdditionalDiscountType();
            additionalDiscountReason = order.getAdditionalDiscountReason();
        }
        // If status is DECLINED, all discount fields remain null (not displayed)
        
        // Extract cancellation reason from cancellation request data
        String cancellationReason = null;
        if (order.getCancellationRequestData() != null) {
            try {
                ObjectMapper objectMapper = new ObjectMapper();
                // Parse as Map first since the stored JSON has requestType, cancellationReason, and requestedStatus
                Map<String, Object> requestDataMap = objectMapper.readValue(
                        order.getCancellationRequestData(), 
                        new TypeReference<Map<String, Object>>() {});
                cancellationReason = (String) requestDataMap.get(PARAM_CANCELLATION_REASON);
            } catch (JsonProcessingException e) {
                log.warn("Failed to parse cancellation request data for order {}: {}", order.getId(), e.getMessage());
            }
        }
        
        // Safely get lazy-loaded relationship IDs
        UUID sessionId = null;
        try {
            if (Hibernate.isInitialized(order.getSession()) && order.getSession() != null) {
                sessionId = order.getSession().getId();
            }
        } catch (Exception e) {
            log.debug("Session proxy not initialized for order {}, will query database: {}", order.getId(), e.getMessage());
            // Fallback: query session ID from database if needed
            // For now, we'll leave it null if not initialized
        }
        
        UUID restaurantTableId = null;
        String tableCode = null;
        
        // Try to get tableId from order's restaurantTable relationship (if loaded)
        // Same approach as convertToLiveOrderResponse in liveOrder API
        try {
            if (Hibernate.isInitialized(order.getRestaurantTable()) && order.getRestaurantTable() != null) {
                restaurantTableId = order.getRestaurantTable().getId();
                tableCode = order.getRestaurantTable().getTableCode();
            }
        } catch (Exception e) {
            log.debug("RestaurantTable proxy not initialized for order {}: {}", order.getId(), e.getMessage());
        }
        
        // Fallback: Get tableId from repository if restaurantTable relationship is not loaded
        // Same approach as convertToLiveOrderResponse in liveOrder API
        if (restaurantTableId == null) {
            try {
                Optional<UUID> tableIdOpt = orderRepository.findTableIdByOrderId(order.getId());
                if (tableIdOpt.isPresent()) {
                    restaurantTableId = tableIdOpt.get();
                    log.debug("Using fallback tableId {} from repository for order {}", restaurantTableId, order.getId());
                }
            } catch (Exception e) {
                log.debug("Error fetching tableId from repository for order {}: {}", order.getId(), e.getMessage());
            }
        }
        
        UUID waiterId = null;
        try {
            if (Hibernate.isInitialized(order.getWaiter()) && order.getWaiter() != null) {
                waiterId = order.getWaiter().getId();
            }
        } catch (Exception e) {
            log.debug("Waiter proxy not initialized for order {}: {}", order.getId(), e.getMessage());
        }
        
        String gstNumber = null;
        try {
            if (Hibernate.isInitialized(order.getRestaurant()) && order.getRestaurant() != null) {
                gstNumber = order.getRestaurant().getGstNumber();
            }
        } catch (Exception e) {
            log.debug("Restaurant proxy not initialized for order {} when getting GST number: {}", order.getId(), e.getMessage());
        }
        
        // Get all active waiters assigned to the table
        List<WaiterInfo> waiters = new ArrayList<>();
        if (restaurantTableId != null) {
            try {
                List<TableAssignment> tableAssignments = (batchCtx != null)
                        ? batchCtx.waitersByRestaurantTableId().getOrDefault(restaurantTableId, Collections.emptyList())
                        : tableAssignmentRepository.findByRestaurantTableIdAndUnassignedAtIsNullWithWaiter(restaurantTableId);
                
                waiters = tableAssignments.stream()
                        .filter(ta -> ta.getWaiter() != null)
                        .map(ta -> {
                            User waiter = ta.getWaiter();
                            String firstName = waiter.getFirstName() != null ? waiter.getFirstName() : "";
                            String lastName = waiter.getLastName() != null ? waiter.getLastName() : "";
                            String waiterName = (firstName + " " + lastName).trim();
                            return WaiterInfo.builder()
                                    .id(waiter.getId())
                                    .userCode(waiter.getUserCode())
                                    .waiterName(waiterName)
                                    .build();
                        })
                        .collect(Collectors.toList());
            } catch (Exception e) {
                log.debug("Error fetching waiters for table {}: {}", restaurantTableId, e.getMessage());
            }
        }
        
        return OrderResponse.builder()
            .id(order.getId())
            .sessionId(sessionId)
            .restaurantId(orderNotificationService.getRestaurantIdFromOrder(order))
            .restaurantTableId(restaurantTableId)
            .tableCode(tableCode)
            .waiterId(waiterId)
            .discountId(validDiscountId) // Only include if discount is still valid
            .orderStatus(order.getOrderStatus())
            .orderType(order.getOrderType())
            .subTotal(order.getSubTotal() != null ? CurrencyFormatter.formatAmount(order.getSubTotal(), currency) : null) // Subtotal after item discounts only
            .subtotalAfterDiscount(validSubtotalAfterDiscount) // Only set if discount is still valid
            .discountCode(validDiscountCode) // Only include if discount is still valid
            .discountValue(validDiscountValue) // Only include if discount is still valid
            .discountAmount(validDiscountAmount) // Only include if discount is still valid
            .discountType(validDiscountType) // Only include if discount is still valid
            .taxAmount(order.getTaxAmount() != null ? CurrencyFormatter.formatAmount(order.getTaxAmount(), currency) : null)
            .alcoholicTaxAmount(order.getAlcoholicTaxAmount() != null ? CurrencyFormatter.formatAmount(order.getAlcoholicTaxAmount(), currency) : null)
            .nonAlcoholicTaxAmount(order.getNonAlcoholicTaxAmount() != null ? CurrencyFormatter.formatAmount(order.getNonAlcoholicTaxAmount(), currency) : null)
            .alcoholicTaxableAmount(alcoholicTaxableAmount != null ? CurrencyFormatter.formatAmount(alcoholicTaxableAmount, currency) : null)
            .nonAlcoholicTaxableAmount(nonAlcoholicTaxableAmount != null ? CurrencyFormatter.formatAmount(nonAlcoholicTaxableAmount, currency) : null)
            .serviceChargeAmount(order.getServiceChargeAmount() != null ? CurrencyFormatter.formatAmount(order.getServiceChargeAmount(), currency) : null)
            .packingChargeAmount(order.getPackingChargeAmount() != null ? CurrencyFormatter.formatAmount(order.getPackingChargeAmount(), currency) : null)
            .additionalDiscountValue(additionalDiscountValue != null ? CurrencyFormatter.formatAmount(additionalDiscountValue, currency) : null) // Set if approved or OPEN (pending)
            .additionalDiscountType(additionalDiscountType) // Set if approved or OPEN (pending)
            .additionalDiscountAmount(additionalDiscountAmount != null ? CurrencyFormatter.formatAmount(additionalDiscountAmount, currency) : null) // Set if approved or OPEN (pending) - calculated for preview
            .additionalDiscountReason(additionalDiscountReason) // Set if approved or OPEN (pending)
            .totalAmount(order.getTotalAmount() != null ? CurrencyFormatter.formatAmount(order.getTotalAmount(), currency) : null)
            .orderNumber(order.getOrderNumber())
            .gmoLinkOrderId(order.getGmoLinkOrderId())
            .transactionId(transaction != null ? transaction.getId() : null)
            .transactionNumber(transaction != null ? transaction.getTransactionNumber() : null)
            .transactionStatus(transaction != null ? transaction.getTransactionStatus() : null)
            .transactionRequestStatus(transaction != null && transaction.getRequestStatus() != null ? transaction.getRequestStatus() : RequestStatus.NONE)
            .refundId(refundId)
            .orderedItems(orderedItemResponses)
            .orderedCombos(orderedComboResponses)
            .gstNumber(gstNumber)
            .email(order.getEmail())
            .requestStatus(order.getCancellationRequestStatus() != null ? order.getCancellationRequestStatus() : RequestStatus.NONE)
            .additionalDiscountRequestStatus(order.getAdditionalDiscountRequestStatus() != null ? order.getAdditionalDiscountRequestStatus() : RequestStatus.NONE)
            .cancellationReason(cancellationReason)
            .rating(rating)
            .waiters(waiters)
            .isInitiated(transaction != null && transaction.getReviewedBy() != null)
            .build();
    }

    private record OrderResponseBatchContext(
            Map<UUID, List<OrderedItem>> orderedItemsByOrderId,
            Map<UUID, List<OrderedItemModifier>> modifiersByOrderedItemId,
            Map<UUID, List<OrderedCombo>> orderedCombosByOrderId,
            Map<UUID, Transaction> transactionByOrderId,
            Map<UUID, UUID> refundIdByTransactionId,
            Map<UUID, Rating> ratingByOrderId,
            Map<UUID, List<TableAssignment>> waitersByRestaurantTableId,
            Map<String, String> presignedUrlCache) {
    }

    private <T> T withOrderResponseBatchContext(OrderResponseBatchContext batchCtx, Supplier<T> supplier) {
        orderResponseBatchCtxHolder.set(batchCtx);
        try {
            return supplier.get();
        } finally {
            orderResponseBatchCtxHolder.remove();
        }
    }

    private OrderResponseBatchContext buildOrderResponseBatchContext(List<Order> orders) {
        if (orders == null || orders.isEmpty()) {
            return new OrderResponseBatchContext(
                    Collections.emptyMap(),
                    Collections.emptyMap(),
                    Collections.emptyMap(),
                    Collections.emptyMap(),
                    Collections.emptyMap(),
                    Collections.emptyMap(),
                    Collections.emptyMap(),
                    new HashMap<>());
        }

        List<UUID> orderIds = orders.stream().map(Order::getId).filter(Objects::nonNull).distinct().collect(Collectors.toList());
        List<UUID> tableIds = orders.stream()
                .map(o -> o.getRestaurantTable() != null ? o.getRestaurantTable().getId() : null)
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.toList());

        Map<UUID, List<OrderedItem>> orderedItemsByOrderId = new HashMap<>();
        Map<UUID, List<OrderedItemModifier>> modifiersByOrderedItemId = new HashMap<>();
        Map<UUID, List<OrderedCombo>> orderedCombosByOrderId = new HashMap<>();
        Map<UUID, Transaction> transactionByOrderId = new HashMap<>();
        Map<UUID, UUID> refundIdByTransactionId = new HashMap<>();
        Map<UUID, Rating> ratingByOrderId = new HashMap<>();
        Map<UUID, List<TableAssignment>> waitersByRestaurantTableId = new HashMap<>();
        Map<String, String> presignedUrlCache = new HashMap<>();

        if (!orderIds.isEmpty()) {
            // Regular items
            List<OrderedItem> regularItems = orderedItemRepository.findRegularByOrderIds(orderIds);
            orderedItemsByOrderId = regularItems.stream()
                    .filter(oi -> oi.getOrder() != null && oi.getOrder().getId() != null)
                    .collect(Collectors.groupingBy(oi -> oi.getOrder().getId()));

            // Modifiers
            List<UUID> orderedItemIds = regularItems.stream().map(OrderedItem::getId).filter(Objects::nonNull).distinct().collect(Collectors.toList());
            if (!orderedItemIds.isEmpty()) {
                List<OrderedItemModifier> modifiers = orderedItemModifierRepository.findByOrderedItemIdInWithRelations(orderedItemIds);
                modifiersByOrderedItemId = modifiers.stream()
                        .filter(m -> m.getOrderedItem() != null && m.getOrderedItem().getId() != null)
                        .collect(Collectors.groupingBy(m -> m.getOrderedItem().getId()));
            }

            // Combos
            List<OrderedCombo> combos = orderedComboRepository.findByOrderIds(orderIds);
            orderedCombosByOrderId = combos.stream()
                    .filter(oc -> oc.getOrder() != null && oc.getOrder().getId() != null)
                    .collect(Collectors.groupingBy(oc -> oc.getOrder().getId()));

            // Transactions (+ reviewedBy eager loaded via repository query)
            List<Transaction> transactions = transactionRepository.findByOrderIds(orderIds);
            transactionByOrderId = transactions.stream()
                    .filter(t -> t.getOrder() != null && t.getOrder().getId() != null)
                    .collect(Collectors.toMap(t -> t.getOrder().getId(), Function.identity(), (a, b) -> a));

            // Refund IDs
            List<UUID> transactionIds = transactions.stream().map(Transaction::getId).filter(Objects::nonNull).distinct().collect(Collectors.toList());
            if (!transactionIds.isEmpty()) {
                List<Refund> refunds = refundRepository.findByTransactionIds(transactionIds);
                refundIdByTransactionId = refunds.stream()
                        .filter(r -> r.getTransaction() != null && r.getTransaction().getId() != null && r.getId() != null)
                        .collect(Collectors.toMap(r -> r.getTransaction().getId(), Refund::getId, (a, b) -> a));
            }

            // Ratings
            List<Rating> ratings = ratingRepository.findByOrderIds(orderIds);
            ratingByOrderId = ratings.stream()
                    .filter(r -> r.getOrder() != null && r.getOrder().getId() != null)
                    .collect(Collectors.toMap(r -> r.getOrder().getId(), Function.identity(), (a, b) -> a));
        }

        if (!tableIds.isEmpty()) {
            List<TableAssignment> tableAssignments =
                    tableAssignmentRepository.findByRestaurantTableIdInAndUnassignedAtIsNullWithWaiter(tableIds);
            waitersByRestaurantTableId = tableAssignments.stream()
                    .filter(ta -> ta.getRestaurantTable() != null && ta.getRestaurantTable().getId() != null)
                    .collect(Collectors.groupingBy(ta -> ta.getRestaurantTable().getId()));
        }

        return new OrderResponseBatchContext(
                orderedItemsByOrderId,
                modifiersByOrderedItemId,
                orderedCombosByOrderId,
                transactionByOrderId,
                refundIdByTransactionId,
                ratingByOrderId,
                waitersByRestaurantTableId,
                presignedUrlCache);
    }

    /**
     * Builds an order response for calculation API with all calculated totals and item details.
     * Includes BXGY information, discounts, taxes, charges, and formatted prices.
     *
     * @param request                  the order request containing items and combos
     * @param session                  the session associated with the order
     * @param restaurant               the restaurant entity
     * @param restaurantTable          the table entity
     * @param waiter                   the waiter user entity
     * @param orderType                the type of order (DINE_IN or TAKEAWAY)
     * @param subTotal                 calculated subtotal before discounts
     * @param subtotalAfterDiscount    calculated subtotal after order-level discount
     * @param discountCode             optional discount code applied
     * @param discountAmount           order-level discount amount
     * @param discountType             type of discount applied
     * @param orderDiscountSavings     total savings from order-level discount
     * @param taxAmount                calculated tax amount
     * @param serviceChargeAmount      calculated service charge amount
     * @param packingChargeAmount      calculated packing charge amount
     * @param additionalDiscountSavings total savings from additional discount
     * @param totalAmount              final total amount
     * @param orderedItems             list of ordered item requests
     * @param itemPrices               map of item IDs to calculated prices
     * @param getItemPrices            map of item IDs to BXGY get item prices
     * @param paidQuantitiesByRequest  map of request identifiers to paid quantities for BXGY
     * @param menuId                   the menu ID
     * @param restaurantId             the restaurant ID
     * @param activeOverrideIndex      active price override index helper
     * @param userLocale               locale for localized names
     * @return {@link OrderResponse} with complete calculated order details
     */
    private record CalculationResponseContext(
            OrderRequest request,
            Session session,
            Restaurant restaurant,
            RestaurantTable restaurantTable,
            User waiter,
            OrderType orderType,
            BigDecimal subTotal,
            BigDecimal subtotalAfterDiscount,
            String discountCode,
            BigDecimal discountAmount,
            DiscountType discountType,
            BigDecimal orderDiscountSavings,
            BigDecimal taxAmount,
            BigDecimal alcoholicTaxAmount,
            BigDecimal nonAlcoholicTaxAmount,
            BigDecimal alcoholicTaxableAmount,
            BigDecimal nonAlcoholicTaxableAmount,
            BigDecimal serviceChargeAmount,
            BigDecimal packingChargeAmount,
            BigDecimal additionalDiscountSavings,
            BigDecimal totalAmount,
            List<OrderedItemRequest> orderedItems,
            Map<UUID, BigDecimal> itemPrices,
            Map<UUID, BigDecimal> getItemPrices,
            Map<String, Integer> paidQuantitiesByRequest,
            UUID menuId,
            UUID restaurantId,
            PriceOverrideHelper.ActiveOverrideIndex activeOverrideIndex,
            Locale userLocale
    ) {}

    private OrderResponse buildCalculationResponse(CalculationResponseContext ctx) {
        
        // Handle null or empty orderedItems - allow orders with only combos
        List<OrderedItemRequest> orderedItems = ctx.orderedItems();
        List<OrderedItemResponse> orderedItemResponses = (orderedItems != null && !orderedItems.isEmpty())
            ? orderedItems.stream()
                .map(itemRequest -> orderedItemService.buildCalculationItemResponse(
                        itemRequest,
                        ctx.itemPrices(),
                        ctx.getItemPrices(),
                        ctx.paidQuantitiesByRequest(),
                        ctx.menuId(),
                        ctx.restaurantId(),
                        ctx.activeOverrideIndex(),
                        ctx.userLocale()))
                .collect(Collectors.toList())
            : new ArrayList<>();

        // Build combo responses for calculation (non-persistent preview)
        List<OrderedComboResponse> orderedComboResponses = (ctx.request().getOrderedCombos() == null)
            ? new ArrayList<>()
            : orderedComboService.buildCalculationComboResponses(ctx.request().getOrderedCombos(), ctx.restaurantId(), ctx.menuId(), ctx.userLocale());
        
        // Get currency for formatting
        String currency = restaurantChainConfigProperties.getChain() != null ? restaurantChainConfigProperties.getChain().getCurrency() : null;
        return OrderResponse.builder()
                .id(null) // No ID for calculation
                .sessionId(ctx.session().getId())
                .restaurantId(ctx.restaurant().getId())
                .restaurantTableId(ctx.restaurantTable().getId())
                .tableCode(ctx.restaurantTable().getTableCode())
                .waiterId(ctx.waiter() != null ? ctx.waiter().getId() : null)
                .discountId(ctx.request().getDiscountId())
                .orderStatus(null)
                .orderType(ctx.orderType())
                .subTotal(ctx.subTotal() != null ? CurrencyFormatter.formatAmount(ctx.subTotal(), currency) : null) // Subtotal after item discounts only
                .subtotalAfterDiscount(ctx.orderDiscountSavings() != null
                        && ctx.orderDiscountSavings().compareTo(BigDecimal.ZERO) > 0
                        && ctx.subtotalAfterDiscount() != null
                    ? CurrencyFormatter.formatAmount(ctx.subtotalAfterDiscount(), currency) : null)
                .discountCode(ctx.discountCode())
                .discountValue(ctx.discountAmount() != null ? CurrencyFormatter.formatAmount(ctx.discountAmount(), currency) : null)
                .discountAmount(ctx.orderDiscountSavings() != null ? CurrencyFormatter.formatAmount(ctx.orderDiscountSavings(), currency) : null) // Order-level discount savings
                .discountType(ctx.discountType())
                .taxAmount(ctx.taxAmount() != null ? CurrencyFormatter.formatAmount(ctx.taxAmount(), currency) : null)
                .alcoholicTaxAmount(ctx.alcoholicTaxAmount() != null ? CurrencyFormatter.formatAmount(ctx.alcoholicTaxAmount(), currency) : null)
                .nonAlcoholicTaxAmount(ctx.nonAlcoholicTaxAmount() != null ? CurrencyFormatter.formatAmount(ctx.nonAlcoholicTaxAmount(), currency) : null)
                .alcoholicTaxableAmount(ctx.alcoholicTaxableAmount() != null ? CurrencyFormatter.formatAmount(ctx.alcoholicTaxableAmount(), currency) : null)
                .nonAlcoholicTaxableAmount(ctx.nonAlcoholicTaxableAmount() != null ? CurrencyFormatter.formatAmount(ctx.nonAlcoholicTaxableAmount(), currency) : null)
                .serviceChargeAmount(ctx.serviceChargeAmount() != null ? CurrencyFormatter.formatAmount(ctx.serviceChargeAmount(), currency) : null)
                .packingChargeAmount(ctx.packingChargeAmount() != null ? CurrencyFormatter.formatAmount(ctx.packingChargeAmount(), currency) : null)
                .additionalDiscountValue(ctx.request().getAdditionalDiscountValue() != null
                        ? CurrencyFormatter.formatAmount(ctx.request().getAdditionalDiscountValue(), currency) : null)
                .additionalDiscountType(ctx.request().getAdditionalDiscountType())
                .additionalDiscountAmount(ctx.additionalDiscountSavings() != null ? CurrencyFormatter.formatAmount(ctx.additionalDiscountSavings(), currency) : null)
                .additionalDiscountReason(null) // No additional discount reason for calculation
                .totalAmount(ctx.totalAmount() != null ? CurrencyFormatter.formatAmount(ctx.totalAmount(), currency) : null)
                .orderNumber(null) // No order number for calculation
                .gmoLinkOrderId(null)
                .transactionNumber(null) // No transaction number for calculation
                .transactionStatus(null) // No transaction status for calculation
                .orderedItems(orderedItemResponses)
                .orderedCombos(orderedComboResponses)
                .gstNumber(ctx.restaurant() != null ? ctx.restaurant().getGstNumber() : null)
                .email(ctx.request().getEmail())
                .requestStatus(RequestStatus.NONE) // No request status for calculation responses
                .build();
    }


    /**
     * Applies an additional discount to an order.
     * Validates the discount request, applies the discount, and recalculates order totals.
     *
     * @param userId  the ID of the user applying the discount
     * @param orderId the UUID of the order to apply discount to
     * @param request the additional discount request containing discount type and value
     * @return {@link ResponseDto} containing the updated order with applied discount
     * @throws ResponseStatusException if order not found, validation fails, or discount application fails
     */
    @Override
    @Transactional
    public ResponseDto<OrderDto<OrderResponse>> applyAdditionalDiscount(String userId, UUID orderId, AdditionalDiscountRequest request) {
        Locale userLocale = LocaleContextHolder.getLocale();

        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        messageUtil.getMessage(MSG_ORDER_NOT_FOUND, userLocale)));

        // ==================== VALIDATE ADDITIONAL DISCOUNT NOT ALREADY APPLIED ====================
        orderValidationService.validateAdditionalDiscountNotAlreadyApplied(order, userLocale);

        // ==================== VALIDATE AND GET USER (OPTIONAL) ====================
        User appliedBy = orderValidationService.validateAndGetUserOrNull(userId, userLocale);

        // ==================== VALIDATE ADDITIONAL DISCOUNT REQUEST ====================
        orderValidationService.validateAdditionalDiscountRequest(request, userLocale);

        // ==================== VALIDATE ADDITIONAL DISCOUNT VALUE ====================
        BigDecimal currentTotal = order.getTotalAmount() != null ? order.getTotalAmount() : BigDecimal.ZERO;
        orderValidationService.validateAdditionalDiscountValue(
                request.getAdditionalDiscountValue(), 
                request.getAdditionalDiscountType(), 
                currentTotal, 
                userLocale);

        // Savings/total for audit (manager/HQ only); cashier path stays zero until approval elsewhere.
        BigDecimal additionalDiscountSavings = BigDecimal.ZERO;

        // Use UTC timezone to match the rest of the application
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);

        // Store request data as JSON
        ObjectMapper objectMapper = new ObjectMapper();
        String requestDataJson = null;
        try {
            Map<String, Object> requestData = new HashMap<>();
            // Add requestType flag to distinguish from cancellation requests
            requestData.put(PARAM_REQUEST_TYPE, "ADDITIONAL_DISCOUNT");
            requestData.put("additionalDiscountType", request.getAdditionalDiscountType().toString());
            requestData.put("additionalDiscountValue", request.getAdditionalDiscountValue());
            requestData.put("additionalDiscountReason", request.getAdditionalDiscountReason());
            requestDataJson = objectMapper.writeValueAsString(requestData);
        } catch (JsonProcessingException e) {
            log.warn("Failed to serialize additional discount request data: {}", e.getMessage());
        }

        // Determine user's role and apply appropriate logic
        String userRoleName = null;
        if (appliedBy != null && appliedBy.getRoleId() != null) {
            Optional<Role> userRole = roleRepository.findById(appliedBy.getRoleId());
            if (userRole.isPresent()) {
                userRoleName = userRole.get().getName();
            }
        }

        // Only MANAGER and HQ_ADMIN can apply directly; CASHIER needs approval; other roles cannot raise requests
        if (userRoleName == null) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    messageUtil.getMessage("additional.discount.unauthorized", userLocale));
        }

        // Check if there's already an existing request (OPEN, APPROVED, or DECLINED)
        // If there's an existing APPROVED/DECLINED request, clear review fields to create a new request
        if (order.getAdditionalDiscountRequestStatus() != null && 
            order.getAdditionalDiscountRequestStatus() != RequestStatus.OPEN) {
            // Clear review fields from previous APPROVED/DECLINED request to ensure this is treated as a new request
            order.setAdditionalDiscountReviewedAt(null);
            order.setAdditionalDiscountReviewedBy(null);
        }

        if (ROLE_MANAGER.equals(userRoleName) || ROLE_HQ_ADMIN.equals(userRoleName)) {
            // MANAGER and HQ_ADMIN can apply directly - no request workflow needed
            // Additional discount applies only to the current final total; use chain rounding + CurrencyFormatter.
            RestaurantChainConfigProperties.RestaurantChainData chain =
                    restaurantChainConfigProperties.getChain();
            String currency = (chain != null && chain.getCurrency() != null && !chain.getCurrency().isBlank())
                    ? chain.getCurrency()
                    : "¥";
            com.gulfnet.shared_library.enums.RoundingMode roundingPolicy =
                    chain != null ? chain.getRoundingMode() : null;
            java.math.RoundingMode divideRm = CurrencyFormatter.resolveRoundingMode(
                    roundingPolicy != null ? roundingPolicy : CurrencyFormatter.getDefaultRoundingPolicy());

            if (request.getAdditionalDiscountType() == DiscountType.PERCENT) {
                additionalDiscountSavings = CurrencyFormatter.formatAmount(
                        currentTotal.multiply(request.getAdditionalDiscountValue())
                                .divide(BigDecimal.valueOf(100), 10, divideRm),
                        currency,
                        roundingPolicy);
            } else if (request.getAdditionalDiscountType() == DiscountType.FLAT) {
                additionalDiscountSavings = CurrencyFormatter.formatAmount(
                        request.getAdditionalDiscountValue(), currency, roundingPolicy);
            }
            BigDecimal totalAmount = CurrencyFormatter.formatAmount(
                    currentTotal.subtract(additionalDiscountSavings), currency, roundingPolicy);
            if (totalAmount.compareTo(BigDecimal.ZERO) < 0) {
                totalAmount = BigDecimal.ZERO;
            }

            order.setAdditionalDiscountType(request.getAdditionalDiscountType());
            order.setAdditionalDiscountValue(request.getAdditionalDiscountValue());
            order.setAdditionalDiscountAmount(additionalDiscountSavings);
            order.setAdditionalDiscountReason(request.getAdditionalDiscountReason());
            order.setTotalAmount(totalAmount);
            order.setUpdatedAt(now);
            order.setUpdatedBy(appliedBy);

            // Do NOT set request-related fields for managers - they apply directly, not through request workflow
            // Explicitly set status to NONE to ensure it doesn't appear in request list
            // This also clears any previous request status (e.g., from a cashier's declined request)
            order.setAdditionalDiscountRequestStatus(RequestStatus.NONE);
            // Clear any existing request fields to ensure clean state
            order.setAdditionalDiscountRequestData(null);
            order.setAdditionalDiscountRequestedAt(null);
            order.setAdditionalDiscountRequestedBy(null);
            order.setAdditionalDiscountReviewedAt(null);
            order.setAdditionalDiscountReviewedBy(null);
            
            // Create audit trail for manager directly adding discount
            try {
                auditTrailService.createAuditTrail(
                        appliedBy,
                        ActionType.ORDER_DISCOUNT_ADD,
                        order.getRestaurant(),
                        RequestStatus.NA,
                        null, // ipAddress
                        null, // userAgent
                        order.getId(),
                        ENTITY_TYPE_ORDER,
                        String.format("Additional discount added: %s %s. Reason: %s", 
                                request.getAdditionalDiscountValue(),
                                request.getAdditionalDiscountType() == DiscountType.PERCENT ? "%" : "flat",
                                request.getAdditionalDiscountReason() != null ? request.getAdditionalDiscountReason() : "N/A")
                );
            } catch (Exception e) {
                log.error("Failed to create audit trail for order discount add: {}", e.getMessage());
            }
        } else if (ROLE_CASHIER.equals(userRoleName)) {
            // CASHIER needs approval - create request with OPEN status
            // Store request data but don't apply discount yet (will be applied on approval)
            order.setAdditionalDiscountType(request.getAdditionalDiscountType());
            order.setAdditionalDiscountValue(request.getAdditionalDiscountValue());
            order.setAdditionalDiscountReason(request.getAdditionalDiscountReason());
            // Don't set additionalDiscountAmount or update totalAmount yet - wait for approval
            order.setUpdatedAt(now);
            order.setUpdatedBy(appliedBy);

            // Create request that needs approval
            order.setAdditionalDiscountRequestStatus(RequestStatus.OPEN); // Needs approval
            order.setAdditionalDiscountRequestData(requestDataJson);
            order.setAdditionalDiscountRequestedAt(now);
            order.setAdditionalDiscountRequestedBy(appliedBy);
            // reviewedAt and reviewedBy will be set when request is approved/declined
        } else {
            // Other roles (WAITER, KDS, etc.) cannot raise requests
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    messageUtil.getMessage("additional.discount.unauthorized.role", userLocale));
        }

        order = orderRepository.save(order);

        // Notify managers when CASHIER creates an additional discount request
        if (ROLE_CASHIER.equals(userRoleName) && order.getAdditionalDiscountRequestStatus() == RequestStatus.OPEN) {
            try {
                UUID restaurantId = orderNotificationService.getRestaurantIdFromOrder(order);
                Optional<Role> managerRoleOpt = roleRepository.findByName(ROLE_MANAGER);
                if (managerRoleOpt.isPresent()) {
                    UUID managerRoleId = managerRoleOpt.get().getId();
                    List<User> managers = userRepository.findAllByRestaurantIdAndRoleIdAndIsDeletedFalse(restaurantId, managerRoleId);
                    if (!managers.isEmpty()) {
                        // Create notification data
                        Map<String, String> additionalData = new HashMap<>();
                        additionalData.put("orderId", order.getId().toString());
                        additionalData.put("orderNumber", order.getOrderNumber());
                        additionalData.put("requestId", order.getId().toString());
                        additionalData.put("type", "ADDITIONAL_DISCOUNT_REQUEST_OPENED");
                        
                        Object[] messageArgs = new Object[]{
                            order.getOrderNumber(),
                            appliedBy.getFirstName() + " " + appliedBy.getLastName()
                        };
                        
                        notificationService.sendNotificationToUsers(
                            managers,
                            "ADDITIONAL_DISCOUNT_REQUEST_OPENED",
                            messageArgs,
                            additionalData,
                            order.getAdditionalDiscountRequestedBy(),
                            userLocale
                        );
                        log.info("Sent additional discount request notification to {} managers for order {}", managers.size(), order.getOrderNumber());
                    }
                }
            } catch (Exception e) {
                log.error("Failed to send additional discount request notification to managers: {}", e.getMessage(), e);
            }
        }

        // Create audit trail for DISCOUNT action
        try {
            RequestStatus auditStatus = (ROLE_MANAGER.equals(userRoleName) || ROLE_HQ_ADMIN.equals(userRoleName)) 
                    ? RequestStatus.APPROVED : RequestStatus.OPEN;
            String auditMessage;
            if (ROLE_MANAGER.equals(userRoleName) || ROLE_HQ_ADMIN.equals(userRoleName)) {
                // Manager/HQ_ADMIN applied discount directly
                auditMessage = String.format("Additional discount applied: Type %s, Value %s, Amount %s, Reason: %s", 
                        request.getAdditionalDiscountType(), request.getAdditionalDiscountValue(), 
                        additionalDiscountSavings, request.getAdditionalDiscountReason());
            } else {
                // Cashier requested discount (not applied yet)
                auditMessage = String.format("Additional discount requested: Type %s, Value %s, Reason: %s", 
                        request.getAdditionalDiscountType(), request.getAdditionalDiscountValue(), 
                        request.getAdditionalDiscountReason());
            }
            auditTrailService.createAuditTrail(
                    appliedBy,
                    ActionType.DISCOUNT,
                    order.getRestaurant(),
                    auditStatus,
                    null, // IP address not available
                    null, // User agent not available
                    order.getId(),
                    ENTITY_TYPE_ORDER,
                    auditMessage
            );
        } catch (Exception e) {
            log.error("Failed to create audit trail for discount: {}", e.getMessage());
        }

        // subtotalAfterDiscount in response is used only when order-level discount exists; reuse order.subTotal - discountAmount
        BigDecimal subtotalAfterDiscount = order.getSubTotal() != null && order.getDiscountAmount() != null
                ? order.getSubTotal().subtract(order.getDiscountAmount())
                : order.getSubTotal();
        if (subtotalAfterDiscount == null) {
            subtotalAfterDiscount = BigDecimal.ZERO;
        }
        if (subtotalAfterDiscount.compareTo(BigDecimal.ZERO) < 0) {
            subtotalAfterDiscount = BigDecimal.ZERO;
        }
        OrderResponse orderResponse = buildOrderResponse(order, subtotalAfterDiscount);
        OrderDto<OrderResponse> dto = OrderDto.<OrderResponse>builder().order(orderResponse).build();
        
        // Return appropriate message based on whether discount was applied or request was created
        String messageKey = (ROLE_MANAGER.equals(userRoleName) || ROLE_HQ_ADMIN.equals(userRoleName))
                ? "additional.discount.applied.success" 
                : "additional.discount.request.created";
        
        return ResponseDto.<OrderDto<OrderResponse>>builder()
                .message(messageUtil.getMessage(messageKey, userLocale))
                .data(dto)
                .build();
    }

    /**
     * Removes the additional discount from an order.
     * Recalculates order totals without the additional discount.
     *
     * @param userId  the ID of the user removing the discount
     * @param orderId the UUID of the order to remove discount from
     * @return {@link ResponseDto} containing the updated order without additional discount
     * @throws ResponseStatusException if order not found or discount removal fails
     */
    @Override
    @Transactional
    public ResponseDto<OrderDto<OrderResponse>> removeAdditionalDiscount(String userId, UUID orderId) {
        Locale userLocale = LocaleContextHolder.getLocale();

        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        messageUtil.getMessage(MSG_ORDER_NOT_FOUND, userLocale)));

        // Check if additional discount exists
        if (order.getAdditionalDiscountAmount() == null || order.getAdditionalDiscountAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    messageUtil.getMessage("additional.discount.not.found", userLocale));
        }

        // ==================== VALIDATE AND GET USER (OPTIONAL) ====================
        User removedBy = orderValidationService.validateAndGetUserOrNull(userId, userLocale);

        // Check user's role - only MANAGER and HQ_ADMIN can remove additional discount
        String userRoleName = null;
        if (removedBy != null && removedBy.getRoleId() != null) {
            Optional<Role> userRole = roleRepository.findById(removedBy.getRoleId());
            if (userRole.isPresent()) {
                userRoleName = userRole.get().getName();
            }
        }

        if (userRoleName == null || (!ROLE_MANAGER.equals(userRoleName) && !ROLE_HQ_ADMIN.equals(userRoleName))) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    messageUtil.getMessage("additional.discount.remove.unauthorized", userLocale));
        }

        // Store the discount amount that was applied (for audit trail)
        BigDecimal removedDiscountAmount = order.getAdditionalDiscountAmount();
        DiscountType removedDiscountType = order.getAdditionalDiscountType();
        BigDecimal removedDiscountValue = order.getAdditionalDiscountValue();
        String removedDiscountReason = order.getAdditionalDiscountReason();

        BigDecimal currentTotal = order.getTotalAmount() != null ? order.getTotalAmount() : BigDecimal.ZERO;
        RestaurantChainConfigProperties.RestaurantChainData chain =
                restaurantChainConfigProperties.getChain();
        String currency = (chain != null && chain.getCurrency() != null && !chain.getCurrency().isBlank())
                ? chain.getCurrency()
                : "¥";
        com.gulfnet.shared_library.enums.RoundingMode roundingPolicy =
                chain != null ? chain.getRoundingMode() : null;
        BigDecimal originalTotal = CurrencyFormatter.formatAmount(
                currentTotal.add(removedDiscountAmount), currency, roundingPolicy);

        // Use UTC timezone to match the rest of the application
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);

        // Clear all additional discount fields
        order.setAdditionalDiscountType(null);
        order.setAdditionalDiscountValue(null);
        order.setAdditionalDiscountAmount(null);
        order.setAdditionalDiscountReason(null);
        order.setTotalAmount(originalTotal);
        order.setUpdatedAt(now);
        order.setUpdatedBy(removedBy);

        // Clear request-related fields if they exist
        order.setAdditionalDiscountRequestStatus(RequestStatus.NONE);
        order.setAdditionalDiscountRequestData(null);
        order.setAdditionalDiscountRequestedAt(null);
        order.setAdditionalDiscountRequestedBy(null);
        order.setAdditionalDiscountReviewedAt(null);
        order.setAdditionalDiscountReviewedBy(null);

        order = orderRepository.save(order);

        // Create audit trail for REMOVING discount
        try {
            auditTrailService.createAuditTrail(
                    removedBy,
                    ActionType.DISCOUNT,
                    order.getRestaurant(),
                    RequestStatus.NONE, // No request status for removal
                    null, // IP address not available
                    null, // User agent not available
                    order.getId(),
                    ENTITY_TYPE_ORDER,
                    String.format("Additional discount removed: Type %s, Value %s, Amount %s, Reason: %s", 
                            removedDiscountType, removedDiscountValue, 
                            removedDiscountAmount, removedDiscountReason != null ? removedDiscountReason : "N/A")
            );
        } catch (Exception e) {
            log.error("Failed to create audit trail for discount removal: {}", e.getMessage());
        }

        // Calculate subtotalAfterDiscount for response
        BigDecimal subtotalAfterDiscount = order.getSubTotal() != null && order.getDiscountAmount() != null
                ? order.getSubTotal().subtract(order.getDiscountAmount())
                : order.getSubTotal();
        if (subtotalAfterDiscount == null) {
            subtotalAfterDiscount = BigDecimal.ZERO;
        }
        if (subtotalAfterDiscount.compareTo(BigDecimal.ZERO) < 0) {
            subtotalAfterDiscount = BigDecimal.ZERO;
        }
        OrderResponse orderResponse = buildOrderResponse(order, subtotalAfterDiscount);
        OrderDto<OrderResponse> dto = OrderDto.<OrderResponse>builder().order(orderResponse).build();
        
        return ResponseDto.<OrderDto<OrderResponse>>builder()
                .message(messageUtil.getMessage("additional.discount.removed.success", userLocale))
                .data(dto)
                .build();
    }

    /**
     * Updates an existing ordered item (quantity, modifiers, notes).
     * Validates the update request, recalculates prices, and updates order totals.
     *
     * @param userId        the ID of the user updating the item
     * @param orderedItemId the UUID of the ordered item to update
     * @param request       the update request containing new quantity, modifiers, and notes
     * @return {@link ResponseDto} containing the updated order
     * @throws ResponseStatusException if ordered item not found, validation fails, or update fails
     */
    @Override
    @Transactional
    public ResponseDto<OrderDto<OrderResponse>> updateOrderedItem(String userId, UUID orderedItemId, UpdateOrderedItemRequest request) {
        log.info("Updating ordered item: {} by user: {}", orderedItemId, userId);
        
        Locale userLocale = LocaleContextHolder.getLocale();
        
        // ==================== VALIDATE ORDERED ITEM EXISTS ====================
        OrderedItem orderedItem = orderValidationService.validateAndGetOrderedItem(orderedItemId, userLocale);
        
        // ==================== VALIDATE TRANSACTION STATUS ====================
        Order order = orderedItem.getOrder();
        orderValidationService.validateTransactionNotCompleted(order, userLocale);
        
        // ==================== VALIDATE MENU ====================
        Menu menu = menuRepository.findById(request.getMenuId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        messageUtil.getMessage("menu.not.found", userLocale)));
        
        // ==================== VALIDATE ITEM STATUS FOR UPDATE ====================
        orderValidationService.validateItemStatusForUpdate(orderedItem.getItemStatus(), userLocale);
        
        // ==================== VALIDATE QUANTITY ====================
        if (request.getQuantity() != null && request.getQuantity() <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    messageUtil.getMessage("item.quantity.invalid", userLocale));
        }
        
        // ==================== HANDLE AUTHENTICATION CONTEXT ====================
        User authenticatedUser = null;
        boolean hasUserId = false;

        if (userId != null && !userId.trim().isEmpty() && !"null".equalsIgnoreCase(userId)) {
            try {
                authenticatedUser = userRepository.findById(UUID.fromString(userId)).orElse(null);
                hasUserId = true;
            } catch (IllegalArgumentException e) {
                log.warn(LOG_INVALID_USER_ID_FORMAT, userId);
            }
        }
        
        // ==================== HANDLE ITEM UPDATE WITH ORDER RECALCULATION ====================
        OrderRecalculationResult recalculationResult = orderRecalculationService.handleItemUpdate(orderedItem, request, authenticatedUser, hasUserId, userLocale);
        
        // ==================== RE-FETCH ORDER AFTER ENTITY MANAGER CLEAR ====================
        // After entityManager.clear() in recalculateOrderAfterItemChange, the order entity is detached.
        // Re-fetch it to ensure we can access lazy-loaded relationships when building the response.
        final UUID orderId = order.getId();
        order = orderValidationService.validateAndGetOrder(orderId, userLocale);
        
        // ==================== AUTOMATIC ORDER STATUS UPDATE ====================
        OrderStatus newOrderStatus = orderRecalculationService.determineOrderStatusBasedOnItems(order.getId());
        OrderStatus oldStatus = order.getOrderStatus();
        orderRecalculationService.updateOrderStatusIfChanged(order, newOrderStatus, authenticatedUser, hasUserId, userLocale);
        if (newOrderStatus != oldStatus) {
            log.info(LOG_ORDER_STATUS_AUTO_UPDATED, order.getId(), newOrderStatus);
        }
        
        // ==================== BUILD RESPONSE ====================
        String responseMessage = messageUtil.getMessage("ordered.item.update.success", userLocale);
        if (recalculationResult.isDiscountRemoved()) {
            responseMessage += " " + recalculationResult.getDiscountMessage();
        }
        
        OrderResponse orderResponse = buildOrderResponse(order, null);
        
        return ResponseDto.<OrderDto<OrderResponse>>builder()
                .message(responseMessage)
                .data(OrderDto.<OrderResponse>builder()
                        .order(orderResponse)
                        .build())
                .build();
    }

    /**
     * Updates an existing ordered combo (quantity, selected items, modifiers, notes).
     * Validates the update request, recalculates prices, and updates order totals.
     *
     * @param userId       the ID of the user updating the combo
     * @param orderedComboId the UUID of the ordered combo to update
     * @param request      the update request containing new quantity, items, modifiers, and notes
     * @return {@link ResponseDto} containing the updated order
     * @throws ResponseStatusException if ordered combo not found, validation fails, or update fails
     */
    @Override
    @Transactional
    public ResponseDto<OrderDto<OrderResponse>> updateOrderedCombo(String userId, UUID orderedComboId, UpdateOrderedComboRequest request) {
        log.info("Updating ordered combo: {} by user: {}", orderedComboId, userId);
        
        Locale userLocale = LocaleContextHolder.getLocale();
        
        // ==================== VALIDATE ORDERED COMBO EXISTS ====================
        OrderedCombo orderedCombo = orderValidationService.validateAndGetOrderedCombo(orderedComboId, userLocale);
        
        // ==================== VALIDATE TRANSACTION STATUS ====================
        Order order = orderedCombo.getOrder();
        orderValidationService.validateTransactionNotCompleted(order, userLocale);
        
        // ==================== VALIDATE MENU ====================
        Menu menu = menuRepository.findById(request.getMenuId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        messageUtil.getMessage("menu.not.found", userLocale)));
        
        // ==================== VALIDATE COMBO STATUS FOR UPDATE ====================
        orderValidationService.validateComboStatusForUpdate(orderedCombo.getItemStatus(), userLocale);
        
        // ==================== VALIDATE QUANTITY ====================
        if (request.getQuantity() != null && request.getQuantity() <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    messageUtil.getMessage("ordered.combo.quantity.invalid", userLocale));
        }
        
        // ==================== HANDLE AUTHENTICATION CONTEXT ====================
        User authenticatedUser = null;
        boolean hasUserId = false;

        if (userId != null && !userId.trim().isEmpty() && !"null".equalsIgnoreCase(userId)) {
            try {
                authenticatedUser = userRepository.findById(UUID.fromString(userId)).orElse(null);
                hasUserId = true;
            } catch (IllegalArgumentException e) {
                log.warn(LOG_INVALID_USER_ID_FORMAT, userId);
            }
        }
        
        // ==================== HANDLE COMBO UPDATE WITH ORDER RECALCULATION ====================
        OrderRecalculationResult recalculationResult = orderRecalculationService.handleComboUpdate(orderedCombo, request, authenticatedUser, hasUserId, userLocale);
        
        // ==================== AUTOMATIC ORDER STATUS UPDATE ====================
        OrderStatus newOrderStatus = orderRecalculationService.determineOrderStatusBasedOnItems(order.getId());
        OrderStatus oldStatus = order.getOrderStatus();
        orderRecalculationService.updateOrderStatusIfChanged(order, newOrderStatus, authenticatedUser, hasUserId, userLocale);
        if (newOrderStatus != oldStatus) {
            log.info(LOG_ORDER_STATUS_AUTO_UPDATED, order.getId(), newOrderStatus);
        }
        
        // ==================== BUILD RESPONSE ====================
        String responseMessage = messageUtil.getMessage("ordered.combo.update.success", userLocale);
        if (recalculationResult != null && recalculationResult.isDiscountRemoved()) {
            responseMessage += " " + recalculationResult.getDiscountMessage();
        }
        
        OrderResponse orderResponse = buildOrderResponse(order, null);
        
        return ResponseDto.<OrderDto<OrderResponse>>builder()
                .message(responseMessage)
                .data(OrderDto.<OrderResponse>builder()
                        .order(orderResponse)
                        .build())
                .build();
    }

    private String sanitizeReason(String reason) {
        return orderNotificationService.sanitizeReason(reason);
    }

    private BigDecimal resolveSubtotalItemAmount(OrderedItem orderedItem) {
        if (orderedItem == null) {
            return BigDecimal.ZERO;
        }
        BigDecimal discounted = orderedItem.getTotalDiscountedItemAmount();
        if (discounted != null) {
            return discounted;
        }
        if (orderedItem.getTotalItemAmount() != null) {
            return orderedItem.getTotalItemAmount();
        }
        if (orderedItem.getPrice() != null && orderedItem.getQuantity() != null) {
            return orderedItem.getPrice().multiply(BigDecimal.valueOf(orderedItem.getQuantity()));
        }
        return BigDecimal.ZERO;
    }

    private void notifyItemPushedBestEffort(OrderedItem orderedItem, Locale userLocale) {
        if (orderedItem == null) {
            return;
        }
        try {
            notificationService.notifyItemPushed(orderedItem, userLocale);
        } catch (Exception e) {
            log.error("Failed to send item pushed notification for item {}: {}", orderedItem.getId(), e.getMessage());
        }
    }

    private void sendComboStatusWsBestEffort(Locale userLocale, UUID restaurantId, OrderedCombo createdCombo) {
        if (createdCombo == null) {
            return;
        }
        try {
            if (createdCombo.getItemStatus() == ItemStatus.ON_HOLD || createdCombo.getItemStatus() == ItemStatus.PUSHED) {
                orderNotificationService.sendItemStatusWebSocketNotification(
                        userLocale, restaurantId, createdCombo.getId(), createdCombo.getItemStatus(), TYPE_COMBO);
                log.debug("Sent WebSocket notification for new combo in order update: {} with status: {} for restaurant: {}",
                        createdCombo.getId(), createdCombo.getItemStatus(), restaurantId);
            }
        } catch (Exception e) {
            log.error("Failed to send WebSocket notification for new combo in order update: {}", e.getMessage());
        }
    }

    private record OpenItemCancellationRequestResult(UUID orderId) {}

    private OpenItemCancellationRequestResult openItemCancellationRequestBestEffort(OrderedItem orderedItem,
                                                                                   User authenticatedUser,
                                                                                   String itemReason,
                                                                                   ItemStatus requestedStatus,
                                                                                   Locale userLocale) {
        try {
            ObjectMapper objectMapper = new ObjectMapper();
            ItemCancellationRequestDto requestDto = ItemCancellationRequestDto.builder()
                    .cancellationReason(itemReason)
                    .requestedStatus(requestedStatus)
                    .build();
            String requestData = objectMapper.writeValueAsString(requestDto);

            orderedItem.setCancellationRequestStatus(RequestStatus.OPEN);
            orderedItem.setCancellationRequestData(requestData);
            orderedItem.setCancellationRequestedAt(OffsetDateTime.now(ZoneOffset.UTC));
            orderedItem.setCancellationRequestedBy(authenticatedUser);
            orderedItem.setUpdatedAt(OffsetDateTime.now(ZoneOffset.UTC));
            orderedItem.setUpdatedBy(authenticatedUser);

            orderedItemRepository.save(orderedItem);

            createCancellationAuditTrailSafe(authenticatedUser,
                    orderedItem.getOrder() != null ? orderedItem.getOrder().getRestaurant() : null,
                    orderedItem.getId(), "ITEM",
                    String.format("Item cancellation request created. Reason: %s",
                            itemReason != null ? itemReason : "N/A"));

            notifyManagersAboutCancellationRequest(orderedItem, userLocale);

            UUID orderId = orderedItem.getOrder() != null ? orderedItem.getOrder().getId() : null;
            return new OpenItemCancellationRequestResult(orderId);
        } catch (Exception e) {
            log.error("Error creating item cancellation request data: {}", e.getMessage());
            return null;
        }
    }

    private void evaluateAlertsAfterOrderCancellationCommitBestEffort(Restaurant restaurant, Locale userLocale) {
        if (restaurant == null || restaurantAlertEvaluationService == null) {
            return;
        }
        try {
            log.info("🔔 Triggering alert evaluation for restaurant: {} after order cancellation commit",
                    restaurant.getRestaurantCode());
            restaurantAlertEvaluationService.evaluateRestaurantAlertsRealtime(restaurant, userLocale);
            log.info("✅ Alert evaluation completed for restaurant: {} after order cancellation commit",
                    restaurant.getRestaurantCode());
        } catch (Exception e) {
            log.error("❌ Failed to evaluate real-time alerts after order cancellation commit: {}", e.getMessage(), e);
        }
    }

    private void notifyItemCanceledBestEffort(OrderedItem orderedItem,
                                              Locale userLocale,
                                              UUID orderedItemIdForLog,
                                              boolean isComboItem) {
        if (notificationService == null || orderedItem == null) {
            return;
        }
        try {
            notificationService.notifyItemCanceled(orderedItem, java.util.Collections.emptyList(), userLocale);
        } catch (Exception e) {
            if (isComboItem) {
                log.warn("Failed to send KDS notification for combo item {} cancellation: {}", orderedItemIdForLog, e.getMessage());
            } else {
                log.warn("Failed to send KDS notification for item {} cancellation: {}", orderedItemIdForLog, e.getMessage());
            }
        }
    }


    @Override
    @Transactional
    public ResponseDto<ItemStatusResponseWrapper> updateItemStatus(String userId, ItemStatusPayload payload) {
        Locale userLocale = LocaleContextHolder.getLocale();
        String sanitizedReason = sanitizeReason(payload.getReason());
        
        // Get bulk IDs directly - support combined requests with both items and combos
        List<UUID> bulkItemIds = payload.getOrderedItemIds();
        List<UUID> bulkComboIds = payload.getOrderedComboIds();
        
        boolean hasBulkItems = bulkItemIds != null && !bulkItemIds.isEmpty();
        boolean hasBulkCombos = bulkComboIds != null && !bulkComboIds.isEmpty();

        // Handle bulk updates (items and/or combos) - supports combined requests
        if (hasBulkItems || hasBulkCombos) {
            if (hasBulkItems && hasBulkCombos) {
                log.info("Bulk update request with {} items and {} combos to status: {} by user: {}", 
                        bulkItemIds.size(), bulkComboIds.size(), payload.getItemStatus(), userId);
            } else if (hasBulkItems) {
                log.info("Bulk update request with {} items to status: {} by user: {}", 
                        bulkItemIds.size(), payload.getItemStatus(), userId);
            } else {
                log.info("Bulk update request with {} combos to status: {} by user: {}", 
                        bulkComboIds.size(), payload.getItemStatus(), userId);
            }
            List<ItemStatusPayload> updatedStatuses = new ArrayList<>();
            String responseMessage = messageUtil.getMessage(MSG_ITEM_STATUS_UPDATED, userLocale);
            boolean hasDirectCancellation = false; // Track if any direct cancellation happened

            User authenticatedUser = null;
            boolean hasUserId = false;
            if (userId != null && !userId.trim().isEmpty() && !"null".equalsIgnoreCase(userId)) {
                try {
                    authenticatedUser = userRepository.findById(UUID.fromString(userId)).orElse(null);
                    hasUserId = true;
                } catch (IllegalArgumentException e) {
                    log.warn(LOG_INVALID_USER_ID_FORMAT, userId);
                }
            }

            Set<UUID> affectedOrderIds = new HashSet<>();
            Map<UUID, Order> affectedOrders = new HashMap<>();

            // Process bulk items (can be combined with combos in the same request)
            if (hasBulkItems) {
                // OPTIMIZATION: Batch fetch all items in ONE query instead of N queries
                List<OrderedItem> orderedItems = orderedItemRepository.findAllByIdWithOrderFetched(bulkItemIds);
                
                // Validate all items exist
                if (orderedItems.size() != bulkItemIds.size()) {
                    Set<UUID> foundIds = orderedItems.stream()
                            .map(OrderedItem::getId)
                            .collect(Collectors.toSet());
                    List<UUID> missingIds = bulkItemIds.stream()
                            .filter(id -> !foundIds.contains(id))
                            .collect(Collectors.toList());
                    throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                            messageUtil.getMessage(MSG_ORDERED_ITEM_NOT_FOUND, userLocale));
                }

                // Create Map for O(1) lookup during processing
                Map<UUID, OrderedItem> itemMap = orderedItems.stream()
                        .collect(Collectors.toMap(OrderedItem::getId, Function.identity()));
                
                // Collect affected combos and their updated items for consolidated status check
                Set<OrderedCombo> affectedItemCombos = new HashSet<>();
                Map<UUID, List<OrderedItem>> itemsByCombo = new HashMap<>();
                
                // Collect items for different types of updates
                List<OrderedItem> itemsForDirectCancellation = new ArrayList<>();
                Map<OrderedItem, ItemStatus> itemStatusUpdatesMap = new HashMap<>();
                
                // Process items using Map lookup (maintains original order)
                for (UUID orderedItemId : bulkItemIds) {
                    try {
                        OrderedItem orderedItem = itemMap.get(orderedItemId);
                        boolean skip = orderedItem == null;
                        String itemReason = null;
                        if (!skip) {
                            itemReason = orderedItemService.applyReasonIfProvided(orderedItem, sanitizedReason);
                            if (payload.getItemStatus() == ItemStatus.CANCELED
                                    && orderedItem.getItemStatus() == ItemStatus.CANCELED) {
                                log.warn("Item {} is already cancelled, skipping", orderedItemId);
                                skip = true;
                            }
                        }
                        if (skip) {
                            continue;
                        }

                        orderValidationService.validateItemStatusTransition(orderedItem.getItemStatus(), payload.getItemStatus(), userLocale);

                        if (payload.getItemStatus() == ItemStatus.CANCELED) {
                            // Check if user is MANAGER - managers can cancel directly without creating request
                            if (orderValidationService.isManager(authenticatedUser)) {
                                itemsForDirectCancellation.add(orderedItem);
                                affectedOrderIds.add(orderedItem.getOrder().getId());
                                if (orderedItem.getOrderedCombo() != null) {
                                    affectedItemCombos.add(orderedItem.getOrderedCombo());
                                    itemsByCombo.computeIfAbsent(orderedItem.getOrderedCombo().getId(), k -> new ArrayList<>()).add(orderedItem);
                                }
                                
                                updatedStatuses.add(ItemStatusPayload.builder()
                                        .orderedItemId(orderedItem.getId())
                                        .itemStatus(ItemStatus.CANCELED)
                                        .reason(orderedItem.getReason())
                                        .build());
                                
                                if (!responseMessage.contains("cancelled")) {
                                    responseMessage = messageUtil.getMessage(MSG_ITEM_CANCELLED_DIRECTLY, userLocale);
                                }
                            } else if (orderValidationService.requiresCancellationApproval(orderedItem.getItemStatus())) {
                                // Non-manager users need approval - create cancellation request
                                boolean skipCancellationRequest = orderedItem.getCancellationRequestStatus() == RequestStatus.OPEN;
                                if (skipCancellationRequest) {
                                    log.warn("Item {} already has a pending cancellation request, skipping", orderedItemId);
                                }
                                
                                if (!skipCancellationRequest) {
                                    OpenItemCancellationRequestResult opened = openItemCancellationRequestBestEffort(
                                            orderedItem, authenticatedUser, itemReason, payload.getItemStatus(), userLocale);
                                    if (opened != null && opened.orderId() != null) {
                                        affectedOrderIds.add(opened.orderId());
                                    }
                                    if (!responseMessage.contains("cancellation.request")) {
                                        responseMessage = messageUtil.getMessage("item.cancellation.request.created", userLocale);
                                    }

                                    updatedStatuses.add(ItemStatusPayload.builder()
                                            .orderedItemId(orderedItem.getId())
                                            .itemStatus(orderedItem.getItemStatus())
                                            .reason(orderedItem.getReason())
                                            .build());
                                }
                            } else {
                                // Direct cancellation - no approval needed
                                itemsForDirectCancellation.add(orderedItem);
                                affectedOrderIds.add(orderedItem.getOrder().getId());
                                if (orderedItem.getOrderedCombo() != null) {
                                    affectedItemCombos.add(orderedItem.getOrderedCombo());
                                    itemsByCombo.computeIfAbsent(orderedItem.getOrderedCombo().getId(), k -> new ArrayList<>()).add(orderedItem);
                                }
                                responseMessage = messageUtil.getMessage(MSG_ITEM_CANCELLED_DIRECTLY, userLocale);
                                
                                updatedStatuses.add(ItemStatusPayload.builder()
                                        .orderedItemId(orderedItem.getId())
                                        .itemStatus(ItemStatus.CANCELED)
                                        .reason(orderedItem.getReason())
                                        .build());
                            }
                        } else {
                            // Status update (non-cancellation)
                            if (orderedItem.getItemStatus() != payload.getItemStatus()) {
                                itemStatusUpdatesMap.put(orderedItem, payload.getItemStatus());
                                
                                // Send waiter notifications (kept per-item but now consolidated)
                                orderNotificationService.sendWaiterNotificationForItemStatus(orderedItem, payload.getItemStatus(), orderedItem.getReason(), userLocale);
                                
                                // Send notification to KDS if SERVED
                                if (payload.getItemStatus() == ItemStatus.SERVED) {
                                    notifyKDSAboutServedItem(orderedItem, userLocale);
                                }
                            }
                            
                            if (orderedItem.getOrderedCombo() != null) {
                                affectedItemCombos.add(orderedItem.getOrderedCombo());
                                itemsByCombo.computeIfAbsent(orderedItem.getOrderedCombo().getId(), k -> new ArrayList<>()).add(orderedItem);
                            }
                            
                            updatedStatuses.add(ItemStatusPayload.builder()
                                    .orderedItemId(orderedItem.getId())
                                    .itemStatus(payload.getItemStatus())
                                    .reason(orderedItem.getReason())
                                    .build());
                            
                            affectedOrderIds.add(orderedItem.getOrder().getId());
                        }
                    } catch (ResponseStatusException ex) {
                        throw ex;
                    } catch (Exception e) {
                        log.error("Error processing item {} for bulk update: {}", orderedItemId, e.getMessage(), e);
                    }
                }
                
                // Execute bulk operations for items
                if (!itemsForDirectCancellation.isEmpty()) {
                    // Separate items by whether deduction should be skipped
                    List<OrderedItem> itemsForDeduction = new ArrayList<>();
                    List<OrderedItem> itemsWithoutDeduction = new ArrayList<>();
                    
                    for (OrderedItem item : itemsForDirectCancellation) {
                        Order orderForCheck = item.getOrder();
                        if (shouldSkipDeductionForCancellation(orderForCheck)) {
                            itemsWithoutDeduction.add(item);
                        } else {
                            itemsForDeduction.add(item);
                        }
                    }
                    
                    // Cancel items without deduction (TAKEAWAY or PREPAID)
                    for (OrderedItem item : itemsWithoutDeduction) {
                        cancelItemWithoutDeduction(item, authenticatedUser, hasUserId, userLocale);
                    }
                    
                    // Recalculate order amounts for items that need deduction
                    if (!itemsForDeduction.isEmpty()) {
                        orderRecalculationService.deductItemsAmountFromOrder(itemsForDeduction, authenticatedUser, hasUserId, userLocale);
                    }
                    
                    // Notify waiters and KDS about item cancellations (for items that had deduction)
                    // Items without deduction already sent notifications in cancelItemWithoutDeduction
                    try {
                        if (!itemsForDeduction.isEmpty()) {
                            List<UUID> cancelledItemIds = itemsForDeduction.stream()
                                    .filter(Objects::nonNull)
                                    .map(OrderedItem::getId)
                                    .filter(Objects::nonNull)
                                    .distinct()
                                    .collect(Collectors.toList());

                            runAfterCommitAsync(() -> {
                                for (UUID cancelledItemId : cancelledItemIds) {
                                    notifyWaitersAboutItemCancellationSafe(cancelledItemId, userLocale);
                                    sendKdsItemCancellationNotificationSafe(cancelledItemId, userLocale);
                                }
                            });
                        }
                    } catch (Exception e) {
                        log.error("Failed to send bulk item cancellation notifications: {}", e.getMessage(), e);
                    }
                }
                
                if (!itemStatusUpdatesMap.isEmpty()) {
                    orderNotificationService.updateItemStatusesWithNotification(itemStatusUpdatesMap, authenticatedUser, hasUserId, userLocale, sanitizedReason);
                }

                // Consolidated combo status updates
                for (OrderedCombo combo : affectedItemCombos) {
                    List<OrderedItem> updatedItemsInCombo = itemsByCombo.get(combo.getId());
                    orderedComboService.updateComboStatusFromItems(combo, updatedItemsInCombo, authenticatedUser, hasUserId, userLocale);
                }
            }

            // Process bulk combos (can be combined with items in the same request)
            if (hasBulkCombos) {
                // OPTIMIZATION: Batch fetch all combos in ONE query instead of N queries
                List<OrderedCombo> orderedCombos = orderedComboRepository.findAllByIdWithOrderFetched(bulkComboIds);
                
                // Validate all combos exist
                if (orderedCombos.size() != bulkComboIds.size()) {
                    Set<UUID> foundIds = orderedCombos.stream()
                            .map(OrderedCombo::getId)
                            .collect(Collectors.toSet());
                    List<UUID> missingIds = bulkComboIds.stream()
                            .filter(id -> !foundIds.contains(id))
                            .collect(Collectors.toList());
                    throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                            messageUtil.getMessage("ordered.combo.not.found", userLocale));
                }
                
                // Create Map for O(1) lookup during processing
                Map<UUID, OrderedCombo> comboMap = orderedCombos.stream()
                        .collect(Collectors.toMap(OrderedCombo::getId, Function.identity()));
                
                // Process combos using Map lookup (maintains original order)
                for (UUID orderedComboId : bulkComboIds) {
                        OrderedCombo orderedCombo = comboMap.get(orderedComboId);
                        String comboReason = orderedComboService.applyReasonIfProvided(orderedCombo, sanitizedReason);

                        // Check if combo is already cancelled when trying to cancel again
                        if (payload.getItemStatus() == ItemStatus.CANCELED && 
                            orderedCombo.getItemStatus() == ItemStatus.CANCELED) {
                            log.warn("Combo {} is already cancelled, skipping", orderedComboId);
                            continue;
                        }

                        orderValidationService.validateItemStatusTransition(orderedCombo.getItemStatus(), payload.getItemStatus(), userLocale);

                        if (payload.getItemStatus() == ItemStatus.CANCELED) {
                            // Check if cancellation requires approval BEFORE cancelling the combo
                            if (orderValidationService.requiresCancellationApproval(orderedCombo.getItemStatus())) {
                                // For bulk operations, we still create cancellation requests
                                // Check if there's already a pending cancellation request
                                if (orderedCombo.getCancellationRequestStatus() == RequestStatus.OPEN) {
                                    log.warn("Combo {} already has a pending cancellation request, skipping", orderedComboId);
                                    continue;
                                }
                                
                                // Create cancellation request for this combo
                                try {
                                    ObjectMapper objectMapper = new ObjectMapper();
                                    ItemCancellationRequestDto requestDto = ItemCancellationRequestDto.builder()
                                            .cancellationReason(comboReason)
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
                                    
                                    // Track order ID for status updates later (even though no recalculation happened yet)
                                    UUID orderId = orderedCombo.getOrder().getId();
                                    affectedOrderIds.add(orderId);
                                    
                                    // Notify managers about newly opened cancellation request
                                    notifyManagersAboutComboCancellationSafe(orderedCombo, orderedComboId, userLocale);
                                    
                                    if (!responseMessage.contains("cancellation.request")) {
                                        responseMessage = messageUtil.getMessage("item.cancellation.request.created", userLocale);
                                    }
                                    
                                    log.info("Created cancellation request for combo {}", orderedComboId);
                                } catch (JsonProcessingException e) {
                                    log.error("Error creating combo cancellation request data: {}", e.getMessage());
                                    throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                                            messageUtil.getMessage("item.cancellation.request.error", userLocale));
                                }
                            } else {
                                // Direct cancellation - no approval needed
                                hasDirectCancellation = true;
                                
                                // Store order ID before cancellation
                                UUID orderId = orderedCombo.getOrder().getId();
                                Order orderForCheck = orderedCombo.getOrder();
                                
                                // Check if deduction should be skipped (TAKEAWAY or PREPAID)
                                if (shouldSkipDeductionForCancellation(orderForCheck)) {
                                    // Cancel without deduction - only update status and send notifications
                                    cancelComboWithoutDeduction(orderedCombo, authenticatedUser, hasUserId, userLocale);
                                } else {
                                    // Set combo status to cancelled first
                                    // Capture status before cancellation for wastage reporting
                                    if (orderedCombo.getItemStatus() != null && orderedCombo.getItemStatus() != ItemStatus.CANCELED) {
                                        orderedCombo.setWastageSourceStatus(orderedCombo.getItemStatus());
                                    }
                                    orderedCombo.setItemStatus(ItemStatus.CANCELED);
                                    orderedComboRepository.save(orderedCombo);

                                    // Also cancel child items belonging to this combo (if not already cancelled)
                                    try {
                                        List<OrderedItem> comboItems = orderedItemRepository.findByOrderedComboId(orderedCombo.getId());
                                        if (comboItems != null && !comboItems.isEmpty()) {
                                            OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
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
                                    
                                    // Skip broadcast WebSocket for CANCELED combo — KDS-specific notifications
                                    // are sent via dedicated methods (notifyItemCanceled/notifyKdsOrderCanceled)
                                    // targeting only assigned KDS users. Broadcasting would leak to all KDS.
                                    
                                    // Direct deduction from order - simple and robust
                                    orderRecalculationService.deductComboAmountFromOrder(orderedCombo, authenticatedUser, hasUserId, userLocale);
                                }
                                
                                String cancellationMessage = messageUtil.getMessage(MSG_ITEM_CANCELLED_DIRECTLY, userLocale);
                                responseMessage = cancellationMessage;
                                
                                // Track order ID for status updates
                                affectedOrderIds.add(orderId);
                            }
                        } else {
                            // Update combo status with WebSocket notification and async database update
                            orderNotificationService.updateComboStatusWithNotification(orderedCombo, payload.getItemStatus(), authenticatedUser, hasUserId, userLocale, comboReason);
                            
                            // Track order ID for status updates later
                            UUID orderId = orderedCombo.getOrder().getId();
                            affectedOrderIds.add(orderId);
                        }

                        updatedStatuses.add(ItemStatusPayload.builder()
                                .orderedComboId(orderedCombo.getId())
                                .itemStatus(orderedCombo.getItemStatus())
                                .reason(orderedCombo.getReason())
                                .build());
                }
            }

            // OPTIMIZATION: Batch fetch all affected orders in ONE query instead of N queries
            if (!affectedOrderIds.isEmpty()) {
                List<Order> refreshedOrders = orderRepository.findAllById(affectedOrderIds);
                affectedOrders = refreshedOrders.stream()
                        .collect(Collectors.toMap(Order::getId, Function.identity()));
            }
            
            // Recalculate order status for affected orders and notify
            for (UUID orderId : affectedOrderIds) {
                Order order = affectedOrders.get(orderId);
                if (order == null) {
                    log.error("Order {} not found during status recalculation", orderId);
                    continue;
                }
                
                OrderStatus newOrderStatus = orderRecalculationService.determineOrderStatusBasedOnItems(order.getId());
                orderRecalculationService.updateOrderStatusIfChanged(order, newOrderStatus, authenticatedUser, hasUserId, userLocale);
                orderRecalculationService.maybeExpireTakeawaySessionWhenOrderServedAndTransactionCompleted(orderId);
                
                // Check if all items in ON_HOLD or PUSHED status are canceled
                // If so, automatically cancel the order and transaction
                if (payload.getItemStatus() == ItemStatus.CANCELED) {
                    orderRecalculationService.checkAndCancelOrderIfAllHoldPushedItemsCanceled(order, authenticatedUser, hasUserId, userLocale);
                }
                
                // Check if order status is CANCELED (regardless of previous item statuses)
                // If so, also cancel the transaction if it exists and is not already canceled/refunded/partially refunded
                if (newOrderStatus == OrderStatus.CANCELED) {
                    cancelTransactionIfOrderCanceled(orderId);
                }
            }

            // Build response with both item and combo IDs (if provided)
            // Prefer the computed result (may differ from requested status when opening cancellation requests).
            ItemStatus statusToReturn = payload.getItemStatus();
            if (!updatedStatuses.isEmpty()) {
                ItemStatusPayload first = updatedStatuses.get(0);
                if (first != null && first.getItemStatus() != null) {
                    statusToReturn = first.getItemStatus();
                }
            }
            
            ItemStatusPayload bulkResponsePayload = ItemStatusPayload.builder()
                    .itemStatus(statusToReturn)
                    .orderedItemIds(bulkItemIds)
                    .orderedComboIds(bulkComboIds)
                    .reason(sanitizedReason)
                    .build();
            
            return ResponseDto.<ItemStatusResponseWrapper>builder()
                    .message(responseMessage)
                    .data(ItemStatusResponseWrapper.builder()
                            .itemStatus(bulkResponsePayload)
                            .build())
                    .build();
        }

        // Single item or combo path
        if (payload.getOrderedItemId() != null) {
            // Single item path
            log.info("Updating item status: {} to {} by user: {}", payload.getOrderedItemId(), payload.getItemStatus(), userId);
        
        OrderedItem orderedItem = orderedItemRepository.findById(payload.getOrderedItemId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        messageUtil.getMessage(MSG_ORDERED_ITEM_NOT_FOUND, userLocale)));
        String itemReason = orderedItemService.applyReasonIfProvided(orderedItem, sanitizedReason);
        
        // Check if there's already a pending cancellation request when trying to cancel
        if (payload.getItemStatus() == ItemStatus.CANCELED && 
            orderedItem.getCancellationRequestStatus() == RequestStatus.OPEN) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    messageUtil.getMessage("item.cancellation.request.already.pending", userLocale));
        }
        
        // Check if item is already cancelled when trying to cancel again
        if (payload.getItemStatus() == ItemStatus.CANCELED && 
            orderedItem.getItemStatus() == ItemStatus.CANCELED) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    messageUtil.getMessage("item.already.cancelled", userLocale));
        }
        
        orderValidationService.validateItemStatusTransition(orderedItem.getItemStatus(), payload.getItemStatus(), userLocale);
        
        User authenticatedUser = null;
        boolean hasUserId = false;
        if (userId != null && !userId.trim().isEmpty() && !"null".equalsIgnoreCase(userId)) {
            try {
                authenticatedUser = userRepository.findById(UUID.fromString(userId)).orElse(null);
                hasUserId = true;
            } catch (IllegalArgumentException e) {
                log.warn(LOG_INVALID_USER_ID_FORMAT, userId);
            }
        }
        
        String responseMessage;
        OrderRecalculationResult recalculationResult = null;
        
        Order order;
        if (payload.getItemStatus() == ItemStatus.CANCELED) {
            // Check if user is MANAGER - managers can cancel directly without creating request
            if (orderValidationService.isManager(authenticatedUser)) {
                // Manager can cancel directly - no request needed
                UUID orderId = orderedItem.getOrder().getId();
                Order orderForCheck = orderedItem.getOrder();
                Restaurant restaurant = orderForCheck != null && orderForCheck.getRestaurant() != null
                    ? orderForCheck.getRestaurant()
                    : null;
                
                // Check if deduction should be skipped (TAKEAWAY or PREPAID)
                if (shouldSkipDeductionForCancellation(orderForCheck)) {
                    // Cancel without deduction - only update status and send notifications
                    cancelItemWithoutDeduction(orderedItem, authenticatedUser, hasUserId, userLocale);
                    
                    // Refetch item after cancellation
                    OrderedItem canceledItem = orderedItemRepository.findById(orderedItem.getId())
                            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                                    messageUtil.getMessage(MSG_ORDERED_ITEM_NOT_FOUND, userLocale)));
                    
                    // Create audit trail for item cancellation
                    try {
                        auditTrailService.createAuditTrail(
                                authenticatedUser,
                                ActionType.CANCELLATION,
                                restaurant,
                                RequestStatus.NA, // Direct cancellation doesn't require approval
                                null, // ipAddress
                                null, // userAgent
                                canceledItem.getId(),
                                "ITEM",
                                String.format("Item cancelled directly by manager (no deduction - TAKEAWAY/PREPAID). Reason: %s", 
                                        itemReason != null ? itemReason : "N/A")
                        );
                    } catch (Exception e) {
                        log.error(LOG_AUDIT_TRAIL_ITEM_CANCELLATION_FAILED, e.getMessage(), e);
                        // Don't break the flow if audit trail fails
                    }
                    
                    // Refresh order for response
                    order = orderRepository.findById(orderId)
                            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                                    messageUtil.getMessage(MSG_ORDER_NOT_FOUND, userLocale)));
                } else {
                    // Use direct deduction method - simple and robust (includes WebSocket notification)
                    orderRecalculationService.deductItemAmountFromOrder(orderedItem, authenticatedUser, hasUserId, userLocale);
                    
                    // Refetch item after cancellation to ensure we have latest state (entity manager was cleared)
                    OrderedItem canceledItem = orderedItemRepository.findById(orderedItem.getId())
                            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                                    messageUtil.getMessage(MSG_ORDERED_ITEM_NOT_FOUND, userLocale)));
                    
                    // Create audit trail for item cancellation
                    try {
                        auditTrailService.createAuditTrail(
                                authenticatedUser,
                                ActionType.CANCELLATION,
                                restaurant,
                                RequestStatus.NA, // Direct cancellation doesn't require approval
                                null, // ipAddress
                                null, // userAgent
                                canceledItem.getId(),
                                "ITEM",
                                String.format("Item cancelled directly by manager. Reason: %s", 
                                        itemReason != null ? itemReason : "N/A")
                        );
                    } catch (Exception e) {
                        log.error(LOG_AUDIT_TRAIL_ITEM_CANCELLATION_FAILED, e.getMessage(), e);
                        // Don't break the flow if audit trail fails
                    }
                    
                    // Check if all items in combo are now CANCELED and update combo status if needed
                    orderedComboService.checkAndUpdateComboStatusWhenAllItemsCanceled(canceledItem, authenticatedUser, hasUserId, userLocale);
                    
                    // Send KDS notification for item cancellation
                    runAfterCommitAsync(() -> {
                        notifyKdsItemCancellationNotificationBestEffort(canceledItem.getId(), userLocale);
                        notifyWaitersAboutItemCancellationSafe(canceledItem.getId(), userLocale);
                    });

                    // Refresh order for response
                    order = orderRepository.findById(orderId)
                            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                                    messageUtil.getMessage(MSG_ORDER_NOT_FOUND, userLocale)));
                }
                
                responseMessage = messageUtil.getMessage(MSG_ITEM_CANCELLED_DIRECTLY, userLocale);
            } else if (orderValidationService.requiresCancellationApproval(orderedItem.getItemStatus())) {
                // Non-manager users need approval - create cancellation request
                return orderedItemService.handleCancellationRequest(orderedItem, payload, authenticatedUser, userLocale);
            } else {
                // Direct cancellation - no approval needed (cashier or other roles that don't need approval)
                // Store order ID before cancellation
                UUID orderId = orderedItem.getOrder().getId();
                Order orderForCheck = orderedItem.getOrder();
                Restaurant restaurant = orderForCheck != null && orderForCheck.getRestaurant() != null
                    ? orderForCheck.getRestaurant()
                    : null;
                
                // Check if deduction should be skipped (TAKEAWAY or PREPAID)
                if (shouldSkipDeductionForCancellation(orderForCheck)) {
                    // Cancel without deduction - only update status and send notifications
                    cancelItemWithoutDeduction(orderedItem, authenticatedUser, hasUserId, userLocale);
                    
                    // Refetch item after cancellation
                    OrderedItem canceledItem = orderedItemRepository.findById(orderedItem.getId())
                            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                                    messageUtil.getMessage(MSG_ORDERED_ITEM_NOT_FOUND, userLocale)));
                    
                    // Create audit trail for item cancellation
                    try {
                        // Determine user role for audit trail message
                        String userRole = "user";
                        if (orderValidationService.isCashier(authenticatedUser)) {
                            userRole = "cashier";
                        }
                        
                        auditTrailService.createAuditTrail(
                                authenticatedUser,
                                ActionType.CANCELLATION,
                                restaurant,
                                RequestStatus.NA, // Direct cancellation doesn't require approval
                                null, // ipAddress
                                null, // userAgent
                                canceledItem.getId(),
                                "ITEM",
                                String.format("Item cancelled directly by %s (no deduction - TAKEAWAY/PREPAID). Reason: %s", 
                                        userRole,
                                        itemReason != null ? itemReason : "N/A")
                        );
                    } catch (Exception e) {
                        log.error(LOG_AUDIT_TRAIL_ITEM_CANCELLATION_FAILED, e.getMessage(), e);
                        // Don't break the flow if audit trail fails
                    }
                    
                    // Refresh order for response
                    order = orderRepository.findById(orderId)
                            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                                    messageUtil.getMessage(MSG_ORDER_NOT_FOUND, userLocale)));
                } else {
                    // Use direct deduction method - simple and robust (includes WebSocket notification)
                    orderRecalculationService.deductItemAmountFromOrder(orderedItem, authenticatedUser, hasUserId, userLocale);
                    
                    // Refetch item after cancellation to ensure we have latest state (entity manager was cleared)
                    OrderedItem canceledItem = orderedItemRepository.findById(orderedItem.getId())
                            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                                    messageUtil.getMessage(MSG_ORDERED_ITEM_NOT_FOUND, userLocale)));
                    
                    // Create audit trail for item cancellation
                    try {
                        // Determine user role for audit trail message
                        String userRole = "user";
                        if (orderValidationService.isCashier(authenticatedUser)) {
                            userRole = "cashier";
                        }
                        
                        auditTrailService.createAuditTrail(
                                authenticatedUser,
                                ActionType.CANCELLATION,
                                restaurant,
                                RequestStatus.NA, // Direct cancellation doesn't require approval
                                null, // ipAddress
                                null, // userAgent
                                canceledItem.getId(),
                                "ITEM",
                                String.format("Item cancelled directly by %s. Reason: %s", 
                                        userRole,
                                        itemReason != null ? itemReason : "N/A")
                        );
                    } catch (Exception e) {
                        log.error(LOG_AUDIT_TRAIL_ITEM_CANCELLATION_FAILED, e.getMessage(), e);
                        // Don't break the flow if audit trail fails
                    }
                    
                    // Check if all items in combo are now CANCELED and update combo status if needed
                    orderedComboService.checkAndUpdateComboStatusWhenAllItemsCanceled(canceledItem, authenticatedUser, hasUserId, userLocale);
                    
                    // Refresh order from database after recalculation to get updated totals
                    order = orderRepository.findById(orderId)
                            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                                    messageUtil.getMessage(MSG_ORDER_NOT_FOUND, userLocale)));
                }
                
                responseMessage = messageUtil.getMessage(MSG_ITEM_CANCELLED_DIRECTLY, userLocale);
            }
        } else {
            // Store current status before updating to check if it actually changed
            ItemStatus currentStatus = orderedItem.getItemStatus();
            
            // Update item status with WebSocket notification and async database update
            orderNotificationService.updateItemComboStatusWithNotification(orderedItem, payload.getItemStatus(), authenticatedUser, hasUserId, userLocale, itemReason);
            responseMessage = messageUtil.getMessage(MSG_ITEM_STATUS_UPDATED, userLocale);
            
            // Check if all items in combo are now COOKING and update combo status if needed
            if (payload.getItemStatus() == ItemStatus.COOKING) {
                orderedComboService.checkAndUpdateComboStatusWhenAllItemsCooking(orderedItem, authenticatedUser, hasUserId, userLocale);
            }
            
            // Check if all items in combo are now READY and update combo status if needed
            if (payload.getItemStatus() == ItemStatus.READY) {
                orderedComboService.checkAndUpdateComboStatusWhenAllItemsReady(orderedItem, authenticatedUser, hasUserId, userLocale);
            }
            
            // Check if all items in combo are now SERVED and update combo status if needed
            if (payload.getItemStatus() == ItemStatus.SERVED) {
                orderedComboService.checkAndUpdateComboStatusWhenAllItemsServed(orderedItem, authenticatedUser, hasUserId, userLocale);
            }
            
            // Send waiter notifications for item status changes only if status actually changed
            if (currentStatus != payload.getItemStatus()) {
                orderNotificationService.sendWaiterNotificationForItemStatus(orderedItem, payload.getItemStatus(), orderedItem.getReason(), userLocale);
                
                // Send notification to KDS users when item is marked as SERVED
                if (payload.getItemStatus() == ItemStatus.SERVED) {
                    try {
                        notificationService.notifyItemServed(orderedItem, userLocale);
                    } catch (Exception e) {
                        log.error("Failed to send item served notification to KDS users: {}", e.getMessage(), e);
                    }
                }
            } else {
                log.debug("Item {} status unchanged ({}), skipping notification", payload.getOrderedItemId(), currentStatus);
            }
            
            // Get order for status update
            order = orderedItem.getOrder();
        }
        
        // CRITICAL: After recalculation, refetch the order to ensure we have the latest recalculated totals
        // The order object might be stale after entity manager clears in recalculateOrderAfterItemChange
        UUID finalOrderId = order.getId();
        order = orderRepository.findById(finalOrderId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        messageUtil.getMessage(MSG_ORDER_NOT_FOUND, userLocale)));
        
        OrderStatus newOrderStatus = orderRecalculationService.determineOrderStatusBasedOnItems(order.getId());
        OrderStatus oldStatus = order.getOrderStatus();
        orderRecalculationService.updateOrderStatusIfChanged(order, newOrderStatus, authenticatedUser, hasUserId, userLocale);
        if (newOrderStatus != oldStatus) {
            log.info(LOG_ORDER_STATUS_AUTO_UPDATED, order.getId(), newOrderStatus);
        }
        orderRecalculationService.maybeExpireTakeawaySessionWhenOrderServedAndTransactionCompleted(finalOrderId);
        
        // Check if all items in ON_HOLD or PUSHED status are canceled
        // If so, automatically cancel the order and transaction
        if (payload.getItemStatus() == ItemStatus.CANCELED) {
            // CRITICAL: Refetch order again before checking cancellation to ensure we have latest totals
            // checkAndCancelOrderIfAllHoldPushedItemsCanceled will also refetch, but we want to be safe
            order = orderRepository.findById(finalOrderId)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                            messageUtil.getMessage(MSG_ORDER_NOT_FOUND, userLocale)));
            orderRecalculationService.checkAndCancelOrderIfAllHoldPushedItemsCanceled(order, authenticatedUser, hasUserId, userLocale);
        }
        
        // Check if order status is CANCELED (regardless of previous item statuses)
        // If so, also cancel the transaction if it exists and is not already canceled/refunded/partially refunded
        if (newOrderStatus == OrderStatus.CANCELED) {
            cancelTransactionIfOrderCanceled(finalOrderId);
        }
        
            ItemStatusPayload responsePayload = ItemStatusPayload.builder()
                    .orderedItemId(payload.getOrderedItemId())
                    .itemStatus(orderedItem.getItemStatus())
                    .reason(orderedItem.getReason())
                    .build();
            
            return ResponseDto.<ItemStatusResponseWrapper>builder()
                    .message(responseMessage)
                    .data(ItemStatusResponseWrapper.builder()
                            .itemStatus(responsePayload)
                            .build())
                    .build();
        } else if (payload.getOrderedComboId() != null) {
            // Single combo path
            log.info("Updating combo status: {} to {} by user: {}", payload.getOrderedComboId(), payload.getItemStatus(), userId);
            
            OrderedCombo orderedCombo = orderedComboRepository.findById(payload.getOrderedComboId())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                            messageUtil.getMessage("ordered.combo.not.found", userLocale)));
            String comboReason = orderedComboService.applyReasonIfProvided(orderedCombo, sanitizedReason);
            
            // Check if combo is already cancelled when trying to cancel again
            if (payload.getItemStatus() == ItemStatus.CANCELED && 
                orderedCombo.getItemStatus() == ItemStatus.CANCELED) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        messageUtil.getMessage("item.already.cancelled", userLocale));
            }
            
            orderValidationService.validateItemStatusTransition(orderedCombo.getItemStatus(), payload.getItemStatus(), userLocale);
            
            User authenticatedUser = null;
            boolean hasUserId = false;
            if (userId != null && !userId.trim().isEmpty() && !"null".equalsIgnoreCase(userId)) {
                try {
                    authenticatedUser = userRepository.findById(UUID.fromString(userId)).orElse(null);
                    hasUserId = true;
                } catch (IllegalArgumentException e) {
                    log.warn(LOG_INVALID_USER_ID_FORMAT, userId);
                }
            }
            
            String responseMessage;
            OrderRecalculationResult recalculationResult = null;
            Order order;
            
            if (payload.getItemStatus() == ItemStatus.CANCELED) {
                // Check if cancellation requires approval BEFORE cancelling the combo
                if (orderValidationService.requiresCancellationApproval(orderedCombo.getItemStatus())) {
                    // Create cancellation request instead of direct cancellation
                    return orderedComboService.handleComboCancellationRequest(orderedCombo, payload, authenticatedUser, userLocale);
                } else {
                    // Direct cancellation - no approval needed
                    // Store order ID before cancellation (entity manager might be cleared)
                    UUID orderId = orderedCombo.getOrder().getId();
                    Order orderForCheck = orderedCombo.getOrder();
                    
                    // Check if deduction should be skipped (TAKEAWAY or PREPAID)
                    if (shouldSkipDeductionForCancellation(orderForCheck)) {
                        // Cancel without deduction - only update status and send notifications
                        cancelComboWithoutDeduction(orderedCombo, authenticatedUser, hasUserId, userLocale);
                        responseMessage = messageUtil.getMessage(MSG_ITEM_CANCELLED_DIRECTLY, userLocale);
                        
                        // Refresh order for response
                        order = orderRepository.findById(orderId)
                                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                                        messageUtil.getMessage(MSG_ORDER_NOT_FOUND, userLocale)));
                    } else {
                        recalculationResult = orderRecalculationService.handleComboCancellation(orderedCombo, authenticatedUser, hasUserId, userLocale);
                        responseMessage = messageUtil.getMessage(MSG_ITEM_CANCELLED_DIRECTLY, userLocale);
                        
                        if (recalculationResult.isDiscountRemoved()) {
                            responseMessage += " " + recalculationResult.getDiscountMessage();
                        }
                        
                        // CRITICAL: Refresh order from database after recalculation to get updated totals
                        // handleComboCancellation clears entity manager, so we need to fetch fresh order
                        order = orderRepository.findById(orderId)
                                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                                        messageUtil.getMessage(MSG_ORDER_NOT_FOUND, userLocale)));
                    }
                }
            } else {
                // Update combo status with WebSocket notification and async database update
                orderNotificationService.updateComboStatusWithNotification(orderedCombo, payload.getItemStatus(), authenticatedUser, hasUserId, userLocale, comboReason);
                responseMessage = messageUtil.getMessage(MSG_ITEM_STATUS_UPDATED, userLocale);
                
                // Get order for status update
                order = orderedCombo.getOrder();
            }
            
            // CRITICAL: After recalculation, refetch the order to ensure we have the latest recalculated totals
            // The order object might be stale after entity manager clears in handleComboCancellation
            UUID finalOrderId = order.getId();
            order = orderRepository.findById(finalOrderId)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                            messageUtil.getMessage(MSG_ORDER_NOT_FOUND, userLocale)));
            
            OrderStatus newOrderStatus = orderRecalculationService.determineOrderStatusBasedOnItems(order.getId());
            OrderStatus oldStatus = order.getOrderStatus();
            orderRecalculationService.updateOrderStatusIfChanged(order, newOrderStatus, authenticatedUser, hasUserId, userLocale);
            if (newOrderStatus != oldStatus) {
                log.info(LOG_ORDER_STATUS_AUTO_UPDATED, order.getId(), newOrderStatus);
            }
            orderRecalculationService.maybeExpireTakeawaySessionWhenOrderServedAndTransactionCompleted(finalOrderId);
            
            // Check if all items in ON_HOLD or PUSHED status are canceled
            // If so, automatically cancel the order and transaction
            if (payload.getItemStatus() == ItemStatus.CANCELED) {
                // CRITICAL: Refetch order again before checking cancellation to ensure we have latest totals
                order = orderRepository.findById(finalOrderId)
                        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                                messageUtil.getMessage(MSG_ORDER_NOT_FOUND, userLocale)));
                orderRecalculationService.checkAndCancelOrderIfAllHoldPushedItemsCanceled(order, authenticatedUser, hasUserId, userLocale);
            }
            
            // Check if order status is CANCELED (regardless of previous item statuses)
            // If so, also cancel the transaction if it exists and is not already canceled/refunded/partially refunded
            if (newOrderStatus == OrderStatus.CANCELED) {
                cancelTransactionIfOrderCanceled(finalOrderId);
            }
            
            ItemStatusPayload responsePayload = ItemStatusPayload.builder()
                    .orderedComboId(payload.getOrderedComboId())
                    .itemStatus(orderedCombo.getItemStatus())
                    .reason(orderedCombo.getReason())
                    .build();
            
            return ResponseDto.<ItemStatusResponseWrapper>builder()
                    .message(responseMessage)
                    .data(ItemStatusResponseWrapper.builder()
                            .itemStatus(responsePayload)
                            .build())
                    .build();
        } else {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    messageUtil.getMessage(MSG_ORDERED_ITEM_NOT_FOUND, userLocale));
        }
    }


    /**
     * Creates a transaction record for an order with appropriate status based on payment system type.
     * Sets transaction status to PENDING for POSTPAID or COMPLETED for PREPAID systems.
     *
     * @param order      the order to create transaction for
     * @param session    the session associated with the order
     * @param restaurant the restaurant entity
     * @param waiter     the waiter user entity
     * @param createdBy  the user creating the transaction
     * @param userLocale locale for localized error messages
     * @return the created {@link Transaction} entity
     */
    private Transaction createTransactionRecord(Order order, Session session, Restaurant restaurant, User waiter, User createdBy, Locale userLocale) {
        log.info("Creating transaction record for order: {}", order.getId());
        
        // Get payment system type from configuration
        RestaurantChainConfigProperties.RestaurantChainData chainConfig = restaurantChainConfigProperties.getChain();
        PaymentSystemType paymentSystemType = chainConfig != null ? chainConfig.getPaymentType() : null;
        
        // Determine transaction status:
        // - For TAKEAWAY orders: always treat as PREPAID (PENDING), regardless of chain configuration
        // - For DINE_IN (or other types): follow paymentSystemType (PREPAID -> PENDING, POSTPAID -> OPEN)
        TransactionStatus transactionStatus;
        String transactionNumber = generateTransactionNumber(restaurant);
        
        if (order.getOrderType() == OrderType.TAKEAWAY) {
            // TAKEAWAY: always behave as PREPAID
            transactionStatus = TransactionStatus.PENDING;
        } else {
            if (paymentSystemType == PaymentSystemType.PREPAID) {
                transactionStatus = TransactionStatus.PENDING;
                // For PREPAID, transaction number will be assigned when status becomes COMPLETED
            } else if (paymentSystemType == PaymentSystemType.POSTPAID) {
                transactionStatus = TransactionStatus.OPEN;
                // For POSTPAID, transaction number will be assigned when status becomes COMPLETED
                // Multiple orders in same session will share the same transaction number
            } else {
                // Default to OPEN if payment system type is not set
                log.warn("Payment system type not configured, defaulting to OPEN status for order: {}", order.getId());
                transactionStatus = TransactionStatus.OPEN;
            }
        }
        
        // Create transaction record
        Transaction transaction = Transaction.builder()
                .order(order)
                .restaurant(restaurant)
                .session(session)
                .transactionNumber(transactionNumber)
                .paymentMethod(null) // Will be set when actual payment is processed
                .transactionStatus(transactionStatus)
                .cashier(null) // Will be set when actual payment is processed
                .createdAt(OffsetDateTime.now(ZoneOffset.UTC))
                .updatedAt(OffsetDateTime.now(ZoneOffset.UTC))
                .build();
        
        transaction = transactionRepository.save(transaction);
        log.info("Transaction record created with status: {} for order: {} (transactionNumber={})",
                transactionStatus, order.getId(), transaction.getTransactionNumber());
        
        return transaction;
    }
    
    /**
     * Generates a unique order number based on restaurant, order type, and sequence.
     * Format: {PREFIX}{YYYYMMDD}{SEQUENCE} where prefix is D for DINE_IN, T for TAKEAWAY.
     * Uses effective date based on operating hours closing plus
     * {@code restaurant.chain.operatingHoursExtendHoursAfterClose} for sequence reset.
     *
     * @param restaurant the restaurant entity to generate order number for
     * @param orderType  the type of order (DINE_IN or TAKEAWAY)
     * @return unique order number string
     */
    private String generateOrderNumber(Restaurant restaurant, OrderType orderType) {
        // Determine prefix based on order type: D for DINE_IN, T for TAKEAWAY
        String prefix = orderType == OrderType.DINE_IN ? "D" : "T";
        
        // Get the effective date for sequence reset (closing + configured extend hours)
        LocalDate effectiveDate = operatingHoursCutoffService.resolveEffectiveBusinessDate(
                restaurant, OffsetDateTime.now(ZoneOffset.UTC));
        
        log.info("Generating order number - restaurantId: {}, orderType: {}, effectiveDate: {}", 
                restaurant.getId(), orderType, effectiveDate);
        
        // Get next sequence number for this restaurant, order type, and date
        Long sequenceNumber = getNextSequenceForRestaurantAndOrderType(restaurant.getId(), orderType, effectiveDate);
        
        // Format: D-01, T-02, etc. (2 digits, resets daily)
        String orderNumber = String.format("%s-%02d", prefix, sequenceNumber);
        
        log.info("Generated order number: {} (sequence: {}) for restaurantId: {}, orderType: {}, effectiveDate: {}", 
                orderNumber, sequenceNumber, restaurant.getId(), orderType, effectiveDate);
        
        return orderNumber;
    }

    /**
     * Unique ID for GMO PG LinkType Plus {@code transaction.OrderID} (max 27 half-width chars).
     * Uses a random 104-bit suffix to make collisions astronomically unlikely; the database
     * unique constraint on {@code orders.gmo_link_order_id} is the ultimate source of truth.
     * <p>
     * Note: This helper performs an existence check to reduce the chance of hitting the unique
     * constraint, but it is not fully atomic under concurrency – callers must still be prepared
     * to handle {@link org.springframework.dao.DataIntegrityViolationException} on persist.
     */
    private String generateUniqueGmoLinkOrderId() {
        int collisions = 0;
        while (true) {
            String candidate = GmoLinkOrderIdGenerator.generateCandidate();
            if (!orderRepository.existsByGmoLinkOrderId(candidate)) {
                return candidate;
            }
            collisions++;
            if (collisions == 1 || collisions % 5 == 0) {
                log.warn("gmoLinkOrderId collision (count={}), regenerating", collisions);
            }
        }
    }

    /**
     * Assigns a new {@code gmo_link_order_id} when missing (e.g. after hosted-card cancel webhook cleared it).
     */
    private void ensureGmoLinkOrderIdAssigned(Order order) {
        if (order.getGmoLinkOrderId() != null && !order.getGmoLinkOrderId().isBlank()) {
            return;
        }
        String newId = generateUniqueGmoLinkOrderId();
        order.setGmoLinkOrderId(newId);
        order.setUpdatedAt(OffsetDateTime.now(ZoneOffset.UTC));
        orderRepository.save(order);
        log.info("Assigned new gmo_link_order_id for order {}: {}", order.getId(), newId);
    }

    /**
     * Calls GMO LinkType Plus checkout URL API with this order's {@code gmo_link_order_id}.
     * If GMO returns EZ4135014 (OrderID still in use), the caller handles it with a user-facing message
     * (no {@code gmo_link_order_id} rotation).
     */
    private String createGmoHostedCheckoutUrl(Order order,
                                              BigDecimal amountPaid,
                                              BigDecimal taxForGmo,
                                              String retUrl,
                                              String completeUrl,
                                              String cancelUrl,
                                              String resultSkipFlag,
                                              Locale displayLocale) {
        String gmoOrderId = order.getGmoLinkOrderId();
        if (gmoOrderId == null || gmoOrderId.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Order is missing GMO LinkType Plus OrderID");
        }
        return gmoLinkPlusPaymentService.createHostedCheckoutUrl(
                gmoOrderId.trim(), amountPaid, taxForGmo, retUrl, completeUrl, cancelUrl, resultSkipFlag, displayLocale);
    }
    
    /**
     * Get the next sequence number for a restaurant, order type, and date.
     * Uses OrderSequenceService for thread-safe sequence generation.
     * The sequence resets daily based on the effective date calculation.
     */
    private Long getNextSequenceForRestaurantAndOrderType(UUID restaurantId, OrderType orderType, LocalDate effectiveDate) {
        return orderSequenceService.getNextSequence(
                restaurantId,
                orderType.name(),
                effectiveDate
        );
    }

    private String generateTransactionNumber(Restaurant restaurant) {
        String restaurantCode = restaurant.getRestaurantCode();
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        String timestamp = now.format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
        String random = String.format("%04d", ThreadLocalRandom.current().nextInt(10000));
        
        return String.format("%s-TXN-%s-%s", restaurantCode, timestamp, random);
    }

    /**
     * Returns a paginated view of "live" orders for a restaurant with optional filters.
     * <p>
     * Supports comma-separated filtering for order status, order type, transaction status, and payment method, plus
     * optional section/waiter/table/search filters. Inputs are parsed/validated and invalid enum values result in
     * {@link ResponseStatusException} with {@code 400 BAD_REQUEST}. The restaurant must exist.
     * </p>
     *
     * @param restaurantId       restaurant identifier
     * @param page               1-based page number (optional)
     * @param size               page size (optional)
     * @param sortBy             sort field (optional)
     * @param direction          sort direction (optional)
     * @param orderStatus        optional comma-separated {@link OrderStatus} values
     * @param orderType          optional comma-separated {@link OrderType} values
     * @param transactionStatus  optional comma-separated {@link TransactionStatus} values
     * @param paymentMethod      optional comma-separated payment method values
     * @param sectionId          optional section id filter (string UUID)
     * @param waiterId           optional waiter id filter (string UUID)
     * @param tableId            optional table id filter (string UUID)
     * @param search             optional free-text search filter
     * @return response containing live orders list DTO
     * @throws ResponseStatusException when restaurant is not found or any filter value is invalid
     */
    @Override
    @Transactional(readOnly = true)
    /**
     * Retrieves a filtered list of “live” orders for a restaurant.
     * <p>
     * Supports optional filters such as order status/type, transaction status, payment method, section/waiter/table
     * scopes, and free-text search. Also supports pagination and sorting. Filter values provided as comma-separated
     * strings are parsed into enum sets where applicable and validated; invalid values result in {@code 400 BAD_REQUEST}.
     * </p>
     *
     * @param restaurantId       restaurant identifier
     * @param page               1-based page number (optional)
     * @param size               page size (optional)
     * @param sortBy             sort field (optional)
     * @param direction          sort direction (optional)
     * @param orderStatus        optional comma-separated {@link OrderStatus} values
     * @param orderType          optional comma-separated {@link OrderType} values
     * @param transactionStatus  optional comma-separated {@link TransactionStatus} values
     * @param paymentMethod      optional comma-separated payment method values
     * @param sectionId          optional section id filter (string UUID)
     * @param waiterId           optional waiter id filter (string UUID)
     * @param tableId            optional table id filter (string UUID)
     * @param search             optional search term
     * @return response containing the live order list and metadata
     * @throws ResponseStatusException when the restaurant cannot be found or filter values are invalid
     */
    public ResponseDto<LiveOrderListDto> getLiveOrders(UUID restaurantId, Integer page, Integer size, String sortBy, String direction, 
                                                      String orderStatus, String orderType, String transactionStatus, 
                                                      String paymentMethod, String sectionId, String waiterId, String tableId, String search) {
        log.info("Getting live orders for restaurant: {} (page: {}, size: {}, sortBy: {}, direction: {}, " +
                "orderStatus: {}, orderType: {}, transactionStatus: {}, paymentMethod: {}, sectionId: {}, waiterId: {}, tableId: {}, search: {})", 
                restaurantId, page, size, sortBy, direction, orderStatus, orderType, transactionStatus, paymentMethod, sectionId, waiterId, tableId, search);

        Locale userLocale = LocaleContextHolder.getLocale();

        // Validate restaurant
        Restaurant restaurant = restaurantRepository.findById(restaurantId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        messageUtil.getMessage("restaurant.not.found", userLocale)));

        // ==================== Simplified Filters ====================
        // Filters: orderStatus, orderType, transactionStatus, paymentMethod
        Collection<OrderStatus> orderStatuses = null;
        if (orderStatus != null && !orderStatus.isBlank()) {
            try {
                orderStatuses = Arrays.stream(orderStatus.split(","))
                        .map(String::trim)
                        .filter(s -> !s.isBlank())
                        .map(s -> OrderStatus.valueOf(s.toUpperCase()))
                        .collect(Collectors.toSet());
                if (orderStatuses.isEmpty()) orderStatuses = null;
            } catch (IllegalArgumentException e) {
                String errorMessage = messageUtil.getMessage("error.invalid.orderStatus", userLocale, orderStatus);
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, errorMessage);
            }
        }
        Collection<OrderType> orderTypes = null;
        if (orderType != null && !orderType.isBlank()) {
            try {
                orderTypes = Arrays.stream(orderType.split(","))
                        .map(String::trim)
                        .filter(s -> !s.isBlank())
                        .map(s -> OrderType.valueOf(s.toUpperCase()))
                        .collect(Collectors.toSet());
                if (orderTypes.isEmpty()) orderTypes = null;
            } catch (IllegalArgumentException e) {
                String errorMessage = messageUtil.getMessage("error.invalid.orderType", userLocale, orderType);
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, errorMessage);
            }
        }
        Collection<TransactionStatus> transactionStatuses = null;
        if (transactionStatus != null && !transactionStatus.isBlank()) {
            try {
                transactionStatuses = Arrays.stream(transactionStatus.split(","))
                        .map(String::trim)
                        .filter(s -> !s.isBlank())
                        .map(s -> TransactionStatus.valueOf(s.toUpperCase()))
                        .collect(Collectors.toSet());
                if (transactionStatuses.isEmpty()) transactionStatuses = null;
            } catch (IllegalArgumentException e) {
                String errorMessage = messageUtil.getMessage("error.invalid.transactionStatus", userLocale, transactionStatus);
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, errorMessage);
            }
        }
        Collection<String> paymentMethods = null;
        if (paymentMethod != null && !paymentMethod.isBlank()) {
            paymentMethods = Arrays.stream(paymentMethod.split(","))
                    .map(String::trim)
                    .filter(s -> !s.isBlank())
                    .collect(Collectors.toSet());
            if (paymentMethods.isEmpty()) paymentMethods = null;
        }
        
        // Parse sectionId filter
        UUID sectionIdFilter = null;
        if (sectionId != null && !sectionId.isBlank()) {
            try {
                sectionIdFilter = UUID.fromString(sectionId.trim());
            } catch (IllegalArgumentException e) {
                String errorMessage = messageUtil.getMessage("error.invalid.sectionId", userLocale, sectionId);
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, errorMessage);
            }
        }
        
        // Parse waiterId filter
        UUID waiterIdFilter = null;
        if (waiterId != null && !waiterId.isBlank()) {
            try {
                waiterIdFilter = UUID.fromString(waiterId.trim());
            } catch (IllegalArgumentException e) {
                String errorMessage = messageUtil.getMessage("error.invalid.waiterId", userLocale, waiterId);
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, errorMessage);
            }
        }
        
        // Parse tableId filter
        UUID tableIdFilter = null;
        if (tableId != null && !tableId.isBlank()) {
            try {
                tableIdFilter = UUID.fromString(tableId.trim());
            } catch (IllegalArgumentException e) {
                String errorMessage = messageUtil.getMessage("error.invalid.tableId", userLocale, tableId);
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, errorMessage);
            }
        }

        // Search only by orderNumber or transactionNumber (case-insensitive)
        String searchTerm = (search != null && !search.isBlank()) ? search.trim() : null;
        String likePatternLower = (searchTerm == null ? null : "%" + searchTerm.toLowerCase() + "%");

        // Pagination - support both paged and unpaged requests
        boolean noPaging = (page == null || size == null || page <= 0 || size <= 0);
        
        // Sorting - map sortBy to database field and apply sort direction
        // Default: sort by createdAt DESC (latest orders first) when no parameters are passed
        String dbSortField = SORT_FIELD_CREATED_AT; // Default sort by order creation time
        if (sortBy != null && !sortBy.isBlank()) {
            // Map common sort field names to actual entity field names
            if ("orderDateTime".equalsIgnoreCase(sortBy) || "time".equalsIgnoreCase(sortBy) || SORT_FIELD_CREATED_AT.equalsIgnoreCase(sortBy)) {
                dbSortField = SORT_FIELD_CREATED_AT; // Map to Order.createdAt field
            } else {
                // If sortBy is provided but doesn't match known fields, use it as-is (for future extensibility)
                dbSortField = sortBy;
            }
        }
        
        // Determine sort direction - default to DESC (latest first) when no direction is provided
        String sortDirectionStr = (direction == null || direction.isBlank()) ? "DESC" : direction;
        Sort.Direction sortDirection = 
            sortDirectionStr.equalsIgnoreCase("ASC") ? 
            Sort.Direction.ASC : 
            Sort.Direction.DESC;
        Sort sort = Sort.by(sortDirection, dbSortField);
        
        Pageable pageable;
        if (!noPaging) {
            pageable = PageRequest.of(page - 1, size, sort);
        } else {
            // Even when no pagination, apply sorting by using a large page size
            pageable = PageRequest.of(0, Integer.MAX_VALUE, sort);
        }

        Page<Order> ordersPage = orderRepository.findLiveOrders(
                restaurantId, orderStatuses, orderTypes, transactionStatuses, paymentMethods, sectionIdFilter, waiterIdFilter, tableIdFilter, likePatternLower, pageable);

        List<LiveOrderResponse> liveOrders = ordersPage.getContent().stream()
                .map(this::convertToLiveOrderResponse)
                .collect(Collectors.toList());

        LiveOrderListDto dto = LiveOrderListDto.builder()
                .liveOrders(liveOrders)
                .count((long) liveOrders.size())
                .total(ordersPage.getTotalElements())
                .metaData(noPaging ? null : PaginationMetaData.builder()
                        .page(page)
                        .size(size)
                        .totalPages(ordersPage.getTotalPages())
                        .totalRecords(ordersPage.getTotalElements())
                        .build())
                .build();

        return ResponseDto.<LiveOrderListDto>builder()
                .message(messageUtil.getMessage("live.orders.retrieved.success", userLocale))
                .data(dto)
                .build();
    }
    
    /**
     * Converts an order entity to a live order response DTO.
     * Includes order details, transaction information, and order-by information (waiter name or "Customer").
     *
     * @param order the order entity to convert
     * @return {@link LiveOrderResponse} with order and transaction details
     */
    private LiveOrderResponse convertToLiveOrderResponse(Order order) {
        // Get transaction for this order
        Optional<Transaction> transaction = transactionRepository.findByOrderId(order.getId());
        
        // Determine orderBy: if createdBy (waiter) exists -> use name; else -> Customer
        String orderBy = "Customer";
        if (order.getCreatedBy() != null) {
            String firstName = order.getCreatedBy().getFirstName() != null ? order.getCreatedBy().getFirstName() : "";
            String lastName = order.getCreatedBy().getLastName() != null ? order.getCreatedBy().getLastName() : "";
            String fullName = (firstName + " " + lastName).trim();
            if (!fullName.isEmpty()) {
                orderBy = fullName;
            }
        }
        
        // Get table information
        Integer tableOrder = null;
        String tableCode = null;
        Integer rowOrder = null;
        UUID sectionId = null;
        String sectionName = null;
        
        if (order.getRestaurantTable() != null) {
            tableOrder = order.getRestaurantTable().getTableOrder();
            tableCode = order.getRestaurantTable().getTableCode();
            if (order.getRestaurantTable().getRestaurantRow() != null) {
                rowOrder = order.getRestaurantTable().getRestaurantRow().getRowOrder();
                if (order.getRestaurantTable().getRestaurantRow().getRestaurantSection() != null) {
                    // Get section information
                    RestaurantSection section = order.getRestaurantTable().getRestaurantRow().getRestaurantSection();
                    sectionId = section.getId();
                    
                    // Get section name from translations (same logic as TableServiceImpl)
                    Locale userLocale = LocaleContextHolder.getLocale();
                    
                    sectionName = section.getTranslations().stream()
                            .filter(t -> userLocale.getLanguage().equalsIgnoreCase(t.getLanguageCode()))
                            .map(RestaurantSectionTranslation::getName)
                            .findFirst()
                            .orElse(section.getTranslations().isEmpty() ? "" : section.getTranslations().get(0).getName());
                }
            }
        }
        
        // Fetch allowCookingRequest from restaurant chain config
        boolean allowCookingRequest = restaurantChainConfigProperties.getChain() != null
                && restaurantChainConfigProperties.getChain().isAllowCookingRequest();
        
        // Get refund ID only if transaction exists and status is REFUNDED or PARTIALLY_REFUNDED
        UUID refundId = null;
        if (transaction.isPresent()) {
            TransactionStatus transactionStatus = transaction.get().getTransactionStatus();
            if (transactionStatus == TransactionStatus.REFUNDED || transactionStatus == TransactionStatus.PARTIALLY_REFUNDED) {
                try {
                    refundId = refundRepository.findByTransactionId(transaction.get().getId())
                            .map(Refund::getId)
                            .orElse(null);
                } catch (Exception e) {
                    log.debug(LOG_ERROR_FETCHING_REFUND_FOR_TRANSACTION, transaction.get().getId(), e.getMessage());
                }
            }
        }
        
        // Get orderDateTime from order creation time
        OffsetDateTime orderDateTime = order.getCreatedAt();
        
        // Get all active waiters assigned to the table
        List<WaiterInfo> waiters = new ArrayList<>();
        UUID restaurantTableId = null;
        
        // Try to get tableId from order's restaurantTable relationship (if loaded)
        try {
            if (Hibernate.isInitialized(order.getRestaurantTable()) && order.getRestaurantTable() != null) {
                restaurantTableId = order.getRestaurantTable().getId();
            }
        } catch (Exception e) {
            log.debug("RestaurantTable proxy not initialized for order {}: {}", order.getId(), e.getMessage());
        }
        
        // Fallback: Get tableId from repository if restaurantTable relationship is not loaded
        if (restaurantTableId == null) {
            try {
                Optional<UUID> tableIdOpt = orderRepository.findTableIdByOrderId(order.getId());
                if (tableIdOpt.isPresent()) {
                    restaurantTableId = tableIdOpt.get();
                    log.debug("Using fallback tableId {} from repository for order {}", restaurantTableId, order.getId());
                }
            } catch (Exception e) {
                log.debug("Error fetching tableId from repository for order {}: {}", order.getId(), e.getMessage());
            }
        }
        
        // Fetch waiters if we have a valid tableId
        if (restaurantTableId != null) {
            try {
                List<TableAssignment> tableAssignments = tableAssignmentRepository
                        .findByRestaurantTableIdAndUnassignedAtIsNullWithWaiter(restaurantTableId);
                
                waiters = tableAssignments.stream()
                        .filter(ta -> ta.getWaiter() != null)
                        .map(ta -> {
                            User waiter = ta.getWaiter();
                            String firstName = waiter.getFirstName() != null ? waiter.getFirstName() : "";
                            String lastName = waiter.getLastName() != null ? waiter.getLastName() : "";
                            String waiterName = (firstName + " " + lastName).trim();
                            return WaiterInfo.builder()
                                    .id(waiter.getId())
                                    .userCode(waiter.getUserCode())
                                    .waiterName(waiterName)
                                    .build();
                        })
                        .collect(Collectors.toList());
            } catch (Exception e) {
                log.debug("Error fetching waiters for table {}: {}", restaurantTableId, e.getMessage());
            }
        }
        
        return LiveOrderResponse.builder()
                .orderId(order.getId())
                .orderNumber(order.getOrderNumber())
                .orderStatus(order.getOrderStatus())
                .orderType(order.getOrderType())
                .orderBy(orderBy)
                .totalAmount(order.getTotalAmount())
                .tableId(order.getRestaurantTable() != null ? order.getRestaurantTable().getId() : null)
                .tableCode(tableCode)
                .tableOrder(tableOrder)
                .rowOrder(rowOrder)
                .sectionId(sectionId)
                .sectionName(sectionName)
                .transactionId(transaction.map(Transaction::getId).orElse(null))
                .transactionStatus(transaction.map(Transaction::getTransactionStatus).orElse(null))
                .transactionNumber(transaction.map(Transaction::getTransactionNumber).orElse(null))
                .paymentMethod(transaction.map(Transaction::getPaymentMethod).orElse(null))
                .paymentApp(transaction.map(Transaction::getPaymentApp).orElse(null))
                .sessionId(order.getSession() != null ? order.getSession().getId() : null)
                .allowCookingRequest(allowCookingRequest)
                .refundId(refundId)
                .orderDateTime(orderDateTime)
                .waiters(waiters)
                .build();
    }

    @Override
    @Transactional
    public ResponseDto<PaymentResponse> processPayment(String userId, String sessionIdHeader, PaymentRequest request, Locale paymentLocale) {
        Locale userLocale = paymentLocale != null ? paymentLocale : Locale.ENGLISH;
        log.info("=== PAYMENT FLOW START === orderId={}, paymentMethod={}, amountPaid={}, email='{}', userId={}, sessionId={}",
                request.getOrderId(),
                request.getPaymentMethod(),
                request.getAmountPaid(),
                request.getEmail() != null ? request.getEmail() : "null",
                userId,
                sessionIdHeader);
        
        // ==================== VALIDATE PAYMENT REQUEST ====================
        orderValidationService.validatePaymentRequest(request, userLocale);
        
        // ==================== VALIDATE AND GET ORDER ====================
        Order order = orderValidationService.validateAndGetOrder(request.getOrderId(), userLocale);

        UUID orderSessionId = order.getSession() != null ? order.getSession().getId() : null;
        orderValidationService.validateCustomerOrStaffSessionAccess(userId, sessionIdHeader, orderSessionId, userLocale);

        User staffCashier = orderValidationService.validateAndGetUserOrNull(userId, userLocale);
        boolean isStaffPayment = staffCashier != null;
        UUID onlineCashierId = orderValidationService.resolveOnlineCardCashierId(userLocale);
        User paymentCashier = isStaffPayment ? staffCashier : cashierReference(onlineCashierId);
        int paymentInitiatorType = isStaffPayment
                ? Transaction.PAYMENT_INITIATOR_CASHIER
                : Transaction.PAYMENT_INITIATOR_CUSTOMER;
        String paymentActorIdForGateway = isStaffPayment ? userId : onlineCashierId.toString();
        
        // ==================== VALIDATE AND GET TRANSACTION ====================
        Transaction transaction = orderValidationService.validateAndGetTransactionForPayment(request.getOrderId(), userLocale);

        if (transaction.getTransactionStatus() == TransactionStatus.COMPLETED) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    messageUtil.getMessage("payment.already.completed", userLocale));
        }
        
        RestaurantChainConfigProperties.RestaurantChainData chainConfig = restaurantChainConfigProperties.getChain();

        // ==================== HANDLE CARD (GMO LinkType Plus hosted checkout) ====================
        if (isGmoHostedCardPayment(request.getPaymentMethod())) {
            if (transaction.getTransactionStatus() == TransactionStatus.CANCELED) {
                transaction.setTransactionStatus(TransactionStatus.PENDING);
                transaction.setUpdatedAt(OffsetDateTime.now(ZoneOffset.UTC));
                transactionRepository.save(transaction);
            }

            String storedLink = transaction.getGmoHostedPaymentUrl();
            if (isResumableHostedCardLink(transaction, storedLink)) {
                log.info("Reusing stored GMO hosted payment URL for orderId={}, transactionId={} (skipping GMO link API)",
                        request.getOrderId(), transaction.getId());
                return buildHostedCardPaymentResponse(userLocale, request, order, transaction, storedLink.trim());
            }

            UUID onlineCardCashierId = orderValidationService.resolveOnlineCardCashierId(userLocale);

            try {
                orderValidationService.validatePaymentMethodAgainstConfig(request.getPaymentMethod(), chainConfig, userLocale);
            } catch (ResponseStatusException e) {
                try {
                    notificationService.notifyPaymentError(cashierReference(onlineCardCashierId), transaction, "UNSUPPORTED_PAYMENT_METHOD",
                            messageUtil.getMessage("payment.method.not.supported", userLocale, request.getPaymentMethod()), userLocale);
                } catch (Exception ex) {
                    log.error("Failed to send payment error notification: {}", ex.getMessage());
                }
                throw e;
            }

            log.info("Entering GMO hosted card payment branch for orderId={}, transactionId={}, onlineCardCashierId={}, payment_initiator_type=1",
                    request.getOrderId(), transaction.getId(), onlineCardCashierId);

            ensureGmoLinkOrderIdAssigned(order);
            orderValidationService.validateGmoHostedCardPayment(request, order, userLocale);

            if (!gmoLinkPlusPaymentService.isConfigured()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        messageUtil.getMessage("payment.linkplus.unconfigured", userLocale));
            }

            if (request.getAmountPaid() == null || request.getAmountPaid().compareTo(BigDecimal.ZERO) <= 0) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        messageUtil.getMessage("payment.amount.invalid", userLocale));
            }

            try {
                if (request.getEmail() != null && !request.getEmail().trim().isEmpty()) {
                    order.setEmail(request.getEmail());
                    orderRepository.save(order);
                    log.info("Email saved to Order for hosted card payment: '{}'", request.getEmail());
                }

                BigDecimal taxForGmo = BigDecimal.ZERO;
                String linkUrl = createGmoHostedCheckoutUrl(
                        order,
                        request.getAmountPaid(),
                        taxForGmo,
                        request.getRetUrl().trim(),
                        request.getCompleteUrl().trim(),
                        request.getCancelUrl().trim(),
                        request.getResultSkipFlag(),
                        userLocale);

                transaction.setPaymentMethod(request.getPaymentMethod());
                transaction.setPaymentApp(resolvePaymentApp(request.getPaymentMethod(), request.getType()));
                transaction.setTransactionAmount(request.getAmountPaid());
                transaction.setTransactionStatus(TransactionStatus.PENDING);
                transaction.setGmoHostedPaymentUrl(linkUrl);
                transaction.setGmoHostedPaymentLinkCreatedAt(OffsetDateTime.now(ZoneOffset.UTC));
                transaction.setCashier(cashierReference(onlineCardCashierId));
                transaction.setPaymentInitiatorType(Transaction.PAYMENT_INITIATOR_CUSTOMER);
                transaction.setUpdatedAt(OffsetDateTime.now(ZoneOffset.UTC));
                transactionRepository.save(transaction);
                transactionRepository.flush();

                return buildHostedCardPaymentResponse(userLocale, request, order, transaction, linkUrl);
            } catch (GmoLinkPlusTradeStatusSupport.OrderIdInUseException e) {
                log.info("GMO OrderID still in use (EZ4135014) for orderId={}", request.getOrderId());
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        messageUtil.getMessage("payment.card.checkout.session.open", userLocale));
            } catch (GmoLinkPlusTradeStatusSupport.DoubleSubmissionException e) {
                log.info("GMO double submission for orderId={}; waiting for peer hosted card link", request.getOrderId());
                String peerLink = resolvePeerHostedCardLinkAfterDoubleSubmission(transaction.getId());
                if (peerLink != null) {
                    Transaction refreshed = transactionRepository.findById(transaction.getId()).orElse(transaction);
                    return buildHostedCardPaymentResponse(userLocale, request, order, refreshed, peerLink);
                }
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        messageUtil.getMessage("payment.card.link.in.progress", userLocale));
            } catch (ResponseStatusException e) {
                throw e;
            } catch (Exception e) {
                log.error("Error processing hosted card payment for order: {}", request.getOrderId(), e);
                throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                        messageUtil.getMessage("notification.payment.error.title", userLocale) + ": " + e.getMessage());
            }
        }

        User cashier = paymentCashier;

        try {
            orderValidationService.validatePaymentMethodAgainstConfig(request.getPaymentMethod(), chainConfig, userLocale);
        } catch (ResponseStatusException e) {
            try {
                notificationService.notifyPaymentError(cashier, transaction, "UNSUPPORTED_PAYMENT_METHOD",
                        messageUtil.getMessage("payment.method.not.supported", userLocale, request.getPaymentMethod()), userLocale);
            } catch (Exception ex) {
                log.error("Failed to send payment error notification: {}", ex.getMessage());
            }
            throw e;
        }
        
        // ==================== HANDLE UPI PAYMENTS (OMISE / GMO) ====================
        if ("UPI".equalsIgnoreCase(request.getPaymentMethod())) {
            log.info("Entering UPI payment branch for orderId={}, transactionId={}",
                    request.getOrderId(), transaction.getId());
            
            // Validate Omise-specific fields (type is required for UPI payments)
            if (request.getType() == null || request.getType().trim().isEmpty()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        messageUtil.getMessage("payment.omise.fields.required", userLocale));
            }
            
            String type = request.getType().toLowerCase();
            
            // Validate amountPaid is greater than zero
            if (request.getAmountPaid() == null || request.getAmountPaid().compareTo(BigDecimal.ZERO) <= 0) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        messageUtil.getMessage("payment.amount.invalid", userLocale));
            }
            
            try {
                boolean gmoEnabled = restaurantChainConfigProperties.isPaymentGatewayEnabled(PaymentGatewayCode.GMO);
                boolean omiseEnabled = restaurantChainConfigProperties.isPaymentGatewayEnabled(PaymentGatewayCode.OMISE);

                if (!gmoEnabled && !omiseEnabled) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                            "No UPI payment gateway configured. Please enable OMISE or GMO.");
                }

                if (gmoEnabled) {
                    log.info("Using GMO gateway for UPI payment (GMO enabled={}, Omise enabled={})", gmoEnabled, omiseEnabled);

                    if (request.getAmountPaid() == null || request.getAmountPaid().compareTo(BigDecimal.ZERO) <= 0) {
                        throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                                messageUtil.getMessage("payment.amount.invalid", userLocale));
                    }

                    if (request.getEmail() != null && !request.getEmail().trim().isEmpty()) {
                        order.setEmail(request.getEmail());
                        orderRepository.save(order);
                        log.info("Email saved to Order for GMO UPI payment: '{}'", request.getEmail());
                    }

                    transaction.setPaymentMethod(request.getPaymentMethod());
                    transaction.setPaymentApp(resolvePaymentApp(request.getPaymentMethod(), type));
                    transaction.setTransactionAmount(request.getAmountPaid());
                    transaction.setCashier(cashier);
                    transaction.setPaymentInitiatorType(paymentInitiatorType);
                    transaction.setUpdatedAt(OffsetDateTime.now(ZoneOffset.UTC));

                    PaymentResponse paymentResponse = gmoService.createGmoQrPayment(
                            paymentActorIdForGateway,
                            order.getId(),
                            order.getOrderNumber(),
                            request.getAmountPaid(),
                            type,
                            transaction,
                            userLocale
                    );

                    // Start async polling for 4 minutes 30 seconds (270 seconds)
                    gmoService.startAsyncPolling(transaction.getId(), userLocale, 270);

                    return ResponseDto.<PaymentResponse>builder()
                            .message(messageUtil.getMessage("payment.initiated.successfully", userLocale))
                            .data(paymentResponse)
                            .build();

                } else {
                    log.info("Using Omise gateway for UPI payment (GMO enabled={}, Omise enabled={})", gmoEnabled, omiseEnabled);

                    BigDecimal omiseAmount = request.getAmountPaid();
                    UUID restaurantIdForPayment = order.getRestaurant().getId();
                    com.gulfnet.shared_library.model.omise.QrPaymentResponse qrPaymentResponse =
                            omiseService.createQrPayment(
                                    restaurantIdForPayment,
                                    omiseAmount,
                                    type,
                                    request.getOrderId().toString()
                            );

                    transaction.setPaymentMethod(request.getPaymentMethod());
                    transaction.setPaymentApp(resolvePaymentApp(request.getPaymentMethod(), type));
                    transaction.setTransactionAmount(request.getAmountPaid());
                    transaction.setTransactionStatus(TransactionStatus.PENDING);
                    transaction.setCashier(cashier);
                    transaction.setPaymentInitiatorType(paymentInitiatorType);
                    transaction.setUpdatedAt(OffsetDateTime.now(ZoneOffset.UTC));

                    String transactionNumber = transaction.getTransactionNumber();

                    if (request.getEmail() != null && !request.getEmail().trim().isEmpty()) {
                        order.setEmail(request.getEmail());
                        orderRepository.save(order);
                        log.info("Email saved to Order for UPI payment: '{}'", request.getEmail());
                    }

                    transactionRepository.save(transaction);
                    transactionRepository.flush();

                    UUID restaurantId = orderNotificationService.getRestaurantIdFromOrder(order);
                    orderNotificationService.sendTransactionStatusWebSocketNotification(userLocale, restaurantId, transaction.getId(), TransactionStatus.PENDING);

                    String qrCodeForResponse = qrPaymentResponse.getQrUrl();
                    if ("paynow".equals(type) || "promptpay".equals(type)) {
                        qrCodeForResponse = omiseScannableQrStorageService.cacheQrImageForPaymentResponse(
                                restaurantIdForPayment,
                                transaction.getId(),
                                qrPaymentResponse.getQrUrl(),
                                type);
                    }

                    PaymentResponse paymentResponse = PaymentResponse.builder()
                            .orderId(request.getOrderId())
                            .paymentMethod(request.getPaymentMethod())
                            .paymentApp(transaction.getPaymentApp())
                            .amountPaid(request.getAmountPaid())
                            .cashReceived(request.getCashReceived())
                            .changeReturned(request.getChangeReturned())
                            .transactionId(transaction.getId())
                            .transactionNumber(transactionNumber)
                            .transactionStatus(TransactionStatus.PENDING)
                            .chargeId(qrPaymentResponse.getChargeId())
                            .qrCode(qrCodeForResponse)
                            .authorizationUri(qrPaymentResponse.getAuthorizeUri())
                            .restaurantId(restaurantId)
                            .build();

                    return ResponseDto.<PaymentResponse>builder()
                            .message(messageUtil.getMessage("payment.initiated.successfully", userLocale))
                            .data(paymentResponse)
                            .build();
                }

            } catch (ResponseStatusException e) {
                throw e;
            } catch (Exception e) {
                log.error("Error processing UPI payment for order: {}", request.getOrderId(), e);
                throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                        messageUtil.getMessage("notification.payment.error.title", userLocale) + ": " + e.getMessage());
            }
        }
        
        // ==================== HANDLE OTHER PAYMENT METHODS (CASH, CARD, etc.) ====================
        log.info("Entering NON-UPI payment branch for orderId={}, transactionId={}, paymentMethod={}",
                request.getOrderId(), transaction.getId(), request.getPaymentMethod());
        // Update transaction
        transaction.setPaymentMethod(request.getPaymentMethod());
        transaction.setPaymentApp(resolvePaymentApp(request.getPaymentMethod(), request.getType()));
        transaction.setTransactionAmount(request.getAmountPaid());
        transaction.setTransactionStatus(TransactionStatus.COMPLETED);
        transaction.setGmoHostedPaymentUrl(null);
        transaction.setGmoHostedPaymentLinkCreatedAt(null);
        transaction.setCashier(cashier);
        transaction.setPaymentInitiatorType(paymentInitiatorType);
        transaction.setUpdatedAt(OffsetDateTime.now(ZoneOffset.UTC));

        // Mark which lines were included in this completed payment (set once; never unset by later cancellations).
        try {
            List<OrderedItem> orderItemsForPayment = orderedItemRepository.findByOrderId(order.getId());
            for (OrderedItem item : orderItemsForPayment) {
                if (item != null && item.getItemStatus() != ItemStatus.CANCELED) {
                    item.setIncludedInPayment(true);
                }
            }
            orderedItemRepository.saveAll(orderItemsForPayment);

            List<OrderedCombo> orderCombosForPayment = orderedComboRepository.findByOrderId(order.getId());
            for (OrderedCombo combo : orderCombosForPayment) {
                if (combo != null && combo.getItemStatus() != ItemStatus.CANCELED) {
                    combo.setIncludedInPayment(true);
                }
            }
            orderedComboRepository.saveAll(orderCombosForPayment);
        } catch (Exception e) {
            log.error("Failed to mark includedInPayment for order {}: {}", order.getId(), e.getMessage(), e);
        }
        
        // Auto-decline pending cancellation request if transaction is being completed
        // This ensures manager cannot act on requests after transaction is closed
        boolean transactionRequestDeclined = false;
        if (transaction.getRequestStatus() == RequestStatus.OPEN && transaction.getRequestData() != null) {
            try {
                ObjectMapper objectMapper = new ObjectMapper();
                Map<String, Object> requestData = objectMapper.readValue(transaction.getRequestData(), Map.class);
                String requestType = (String) requestData.get(PARAM_REQUEST_TYPE);
                // Only auto-decline cancellation requests, not refund requests
                if (requestType == null || !"REFUND".equals(requestType)) {
                    // This is a cancellation request (or old format without requestType)
                    transaction.setRequestStatus(RequestStatus.DECLINED);
                    transaction.setReviewedAt(OffsetDateTime.now(ZoneOffset.UTC));
                    transaction.setRequestComments(MSG_AUTO_DECLINED_TRANSACTION_COMPLETED);
                    transactionRequestDeclined = true;
                    log.info("Auto-declined pending cancellation request for transaction {} as transaction was completed. Request status changed from OPEN to DECLINED.", transaction.getId());
                } else {
                    log.debug("Skipping auto-decline for transaction {} - requestType is REFUND, not cancellation", transaction.getId());
                }
            } catch (JsonProcessingException e) {
                // If requestData is not valid JSON, treat it as old format cancellation request
                transaction.setRequestStatus(RequestStatus.DECLINED);
                transaction.setReviewedAt(OffsetDateTime.now(ZoneOffset.UTC));
                transaction.setRequestComments(MSG_AUTO_DECLINED_TRANSACTION_COMPLETED);
                transactionRequestDeclined = true;
                log.info("Auto-declined pending cancellation request (old format) for transaction {} as transaction was completed. Request status changed from OPEN to DECLINED.", transaction.getId());
            }
        } else {
            log.debug("No pending cancellation request to auto-decline for transaction {} - requestStatus: {}, requestData: {}", 
                    transaction.getId(), transaction.getRequestStatus(), transaction.getRequestData() != null ? "present" : "null");
        }
        
        // Auto-decline pending additional discount request if transaction is being completed
        boolean orderModified = false;
        if (order.getAdditionalDiscountRequestStatus() == RequestStatus.OPEN) {
            order.setAdditionalDiscountRequestStatus(RequestStatus.DECLINED);
            order.setAdditionalDiscountReviewedAt(OffsetDateTime.now(ZoneOffset.UTC));
            order.setAdditionalDiscountRequestComments(MSG_AUTO_DECLINED_TRANSACTION_COMPLETED);
            orderModified = true;
            log.info("Auto-declined pending additional discount request for order {} as transaction was completed", order.getId());
        }
        
        String transactionNumber = transaction.getTransactionNumber();
        
        // Save transaction state first; receipt generation is scheduled asynchronously after commit.
        Transaction savedTransaction = transactionRepository.save(transaction);
        transactionRepository.flush();
        if (transactionRequestDeclined) {
            log.info("Transaction {} saved with requestStatus={} after auto-decline", 
                    savedTransaction.getId(), savedTransaction.getRequestStatus());
        }
        
        // Save order if it was modified (e.g., additional discount request auto-declined)
        if (orderModified) {
            orderRepository.save(order);
            orderRepository.flush();
            log.info("Saved order {} after auto-declining additional discount request", order.getId());
        }

        scheduleOrderReceiptGenerationAfterCommit(transaction.getId());

        // ==================== UPDATE ITEM STATUSES FOR PREPAID FLOWS ====================
        try {
            // Determine payment system type from chain config
            PaymentSystemType paymentSystemType = chainConfig != null ? chainConfig.getPaymentType() : null;

            // For DINE_IN: only auto-push items when payment system is PREPAID
            // For TAKEAWAY: always auto-push items (TAKEAWAY is treated as prepaid by default)
            boolean shouldPushItems = (order.getOrderType() == OrderType.DINE_IN && paymentSystemType == PaymentSystemType.PREPAID)
                    || order.getOrderType() == OrderType.TAKEAWAY;

            if (shouldPushItems) {
                List<OrderedItem> orderItems = orderedItemRepository.findByOrderId(order.getId());
                Map<OrderedItem, ItemStatus> itemsToPush = new HashMap<>();

                for (OrderedItem item : orderItems) {
                    if (item.getItemStatus() != ItemStatus.PUSHED) {
                        itemsToPush.put(item, ItemStatus.PUSHED);
                    }
                }

                List<OrderedCombo> orderCombos = orderedComboRepository.findByOrderId(order.getId());
                Map<OrderedCombo, ItemStatus> combosToPush = new HashMap<>();

                for (OrderedCombo combo : orderCombos) {
                    if (combo.getItemStatus() != ItemStatus.PUSHED) {
                        combosToPush.put(combo, ItemStatus.PUSHED);
                    }
                }

                if (!itemsToPush.isEmpty() || !combosToPush.isEmpty()) {
                    // Save updates synchronously to ensure DB is updated before order status recalculation
                    if (!itemsToPush.isEmpty()) {
                        for (Map.Entry<OrderedItem, ItemStatus> entry : itemsToPush.entrySet()) {
                            entry.getKey().setItemStatus(entry.getValue());
                            entry.getKey().setUpdatedAt(OffsetDateTime.now(ZoneOffset.UTC));
                            entry.getKey().setUpdatedBy(cashier);
                        }
                        orderedItemRepository.saveAll(new ArrayList<>(itemsToPush.keySet()));
                        orderedItemRepository.flush();
                        // Broadcast + KDS notifications for pushed items
                        orderNotificationService.updateItemStatusesWithNotification(itemsToPush, cashier, true, userLocale, null);
                    }

                    if (!combosToPush.isEmpty()) {
                        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
                        for (Map.Entry<OrderedCombo, ItemStatus> entry : combosToPush.entrySet()) {
                            entry.getKey().setItemStatus(entry.getValue());
                            entry.getKey().setUpdatedAt(now);
                            entry.getKey().setUpdatedBy(cashier);
                        }
                        orderedComboRepository.saveAll(new ArrayList<>(combosToPush.keySet()));
                        orderedComboRepository.flush();
                        // Broadcast + KDS notifications for pushed combos (also pushes ON_HOLD combo items to PUSHED)
                        orderNotificationService.updateComboStatusesWithNotification(combosToPush, cashier, true, userLocale, null);
                    }

                    // After items/combos are pushed, recalculate and update order status as needed
                    OrderStatus newStatus = orderRecalculationService.determineOrderStatusBasedOnItems(order.getId());
                    orderRecalculationService.updateOrderStatusIfChanged(order, newStatus, cashier, true, userLocale);
                }
            }
        } catch (Exception e) {
            log.error("Failed to auto-push item statuses after payment for order {}: {}", order.getId(), e.getMessage(), e);
            // Do not fail payment if status update fails
        }
        
        // ==================== REAL-TIME HQ ALERT EVALUATION ====================
        // Check if sales/refund/cancellation thresholds are breached after this transaction
        // Note: This runs AFTER transaction commit using TransactionSynchronizationManager
        // so it can see the committed transaction data
        final Restaurant restaurant = order.getRestaurant();
        final Locale finalUserLocale = userLocale;
        if (restaurant != null) {
            // Check if alert evaluation service is available (lazy injection)
            if (restaurantAlertEvaluationService == null) {
                log.warn("⚠️ RestaurantAlertEvaluationService is null (lazy injection not initialized), skipping alert evaluation for restaurant: {}", 
                        restaurant.getRestaurantCode());
            } else {
                if (TransactionSynchronizationManager.isActualTransactionActive()) {
                    TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                        /**
                         * Executes after the payment transaction commits, evaluating restaurant alerts in real-time.
                         * This ensures alert evaluation sees the committed transaction data.
                         */
                        @Override
                        public void afterCommit() {
                            try {
                                log.info("🔔 Triggering alert evaluation for restaurant: {} after payment transaction commit", restaurant.getRestaurantCode());
                                if (restaurantAlertEvaluationService == null) {
                                    log.error("❌ RestaurantAlertEvaluationService is null in afterCommit callback - lazy injection failed");
                                    return;
                                }
                                restaurantAlertEvaluationService.evaluateRestaurantAlertsRealtime(restaurant, finalUserLocale);
                                log.info("✅ Alert evaluation completed for restaurant: {}", restaurant.getRestaurantCode());
                            } catch (Exception e) {
                                log.error("❌ Failed to evaluate real-time alerts after transaction commit: {}", e.getMessage(), e);
                                // Don't fail the payment if alert evaluation fails
                            }
                        }
                    });
                    log.info("📋 Registered alert evaluation to run after transaction commit for restaurant: {}", restaurant.getRestaurantCode());
                } else {
                    // No active transaction - run immediately
                    try {
                        log.info("🔔 Triggering alert evaluation for restaurant: {} (no active transaction)", restaurant.getRestaurantCode());
                        if (restaurantAlertEvaluationService == null) {
                            log.error("❌ RestaurantAlertEvaluationService is null (no active transaction) - lazy injection failed");
                            // Continue execution - alert evaluation failure shouldn't block payment processing
                        } else {
                            restaurantAlertEvaluationService.evaluateRestaurantAlertsRealtime(restaurant, finalUserLocale);
                            log.info("✅ Alert evaluation completed for restaurant: {}", restaurant.getRestaurantCode());
                        }
                    } catch (Exception e) {
                        log.error("❌ Failed to evaluate real-time alerts: {}", e.getMessage(), e);
                        // Continue execution - alert evaluation failure shouldn't block payment processing
                    }
                }
            }
        } else {
            log.warn("⚠️ Cannot evaluate alerts: restaurant is null for order: {}", order.getId());
        }

        log.info("=== PAYMENT FLOW END === orderId={}, transactionId={}, transactionNumber={}, paymentMethod={}, transactionStatus={}, finalReceiptUrl={}",
                order.getId(),
                transaction.getId(),
                transaction.getTransactionNumber(),
                transaction.getPaymentMethod(),
                transaction.getTransactionStatus(),
                transaction.getReceiptUrl());
        
        // Create audit trail for PAYMENT action
        try {
            auditTrailService.createAuditTrail(
                    cashier,
                    ActionType.PAYMENT,
                    order.getRestaurant(),
                    RequestStatus.NA, // Non-request action - always NA
                    null, // IP address not available
                    null, // User agent not available
                    transaction.getId(),
                    "TRANSACTION",
                    String.format("Payment processed: Method %s, Amount %s, Transaction Number %s", 
                            request.getPaymentMethod(), request.getAmountPaid(), transactionNumber)
            );
        } catch (Exception e) {
            log.error("Failed to create audit trail for payment: {}", e.getMessage());
        }
        
        // Create SALE_INFLOW cash drawer log for CASH payments
        if ("CASH".equalsIgnoreCase(request.getPaymentMethod())) {
            try {
                Optional<CashierShift> activeShiftOpt = cashierShiftRepository.findActiveShiftByCashierId(cashier.getId());
                if (activeShiftOpt.isPresent()) {
                    CashierShift activeShift = activeShiftOpt.get();

                    // Net impact to drawer is the order total. Tender details are tracked separately.
                    // Backward compatible: if cashReceived not provided, treat it as amountPaid.
                    BigDecimal orderTotal = order.getTotalAmount() != null ? order.getTotalAmount() : BigDecimal.ZERO;
                    BigDecimal cashReceived = request.getCashReceived() != null ? request.getCashReceived() : request.getAmountPaid();
                    if (cashReceived == null) {
                        cashReceived = request.getAmountPaid();
                    }
                    if (cashReceived == null) {
                        cashReceived = orderTotal;
                    }
                    BigDecimal computedChangeReturned = cashReceived.subtract(orderTotal);
                    if (computedChangeReturned.compareTo(BigDecimal.ZERO) < 0) {
                        computedChangeReturned = BigDecimal.ZERO;
                    }
                    BigDecimal changeReturned = request.getChangeReturned() != null ? request.getChangeReturned() : computedChangeReturned;
                    if (changeReturned.compareTo(BigDecimal.ZERO) < 0) {
                        changeReturned = BigDecimal.ZERO;
                    }
                    // If caller provided changeReturned, keep it but don't allow it to exceed cashReceived.
                    if (changeReturned.compareTo(cashReceived) > 0) {
                        changeReturned = cashReceived;
                    }

                    CashDrawerLog saleInflowLog = CashDrawerLog.builder()
                            .shift(activeShift)
                            .drawer(activeShift.getCashDrawer())
                            .user(cashier)
                            .eventType(DrawerEventType.SALE_INFLOW)
                            // Net increase in drawer (what matters for expected cash balance).
                            .amount(orderTotal)
                            .expectedAmount(orderTotal)
                            // Physical cash movement:
                            // grossIn = tendered cash, grossOut = change returned.
                            .grossIn(cashReceived)
                            .grossOut(changeReturned)
                            .transaction(transaction)
                            .notes("Cash payment for transaction: " + transactionNumber)
                            .createdBy(cashier)
                            .build();
                    cashDrawerLogRepository.save(saleInflowLog);
                    log.info("Created SALE_INFLOW cash drawer log for transaction: {} (net={}, cashReceived={}, changeReturned={})",
                            transactionNumber, orderTotal, cashReceived, changeReturned);
                } else {
                    log.warn("No active shift found for cashier: {}. SALE_INFLOW log not created for transaction: {}", 
                            cashier.getId(), transactionNumber);
                }
            } catch (Exception e) {
                log.error("Failed to create SALE_INFLOW cash drawer log for transaction: {}. Error: {}", 
                        transactionNumber, e.getMessage(), e);
                // Don't fail the payment if cash drawer log creation fails
            }
        }
        
        // Send receipt email if email is provided
        if (request.getEmail() != null && !request.getEmail().trim().isEmpty()) {
            try {
                log.info("Email provided in payment request: '{}' - proceeding to send receipt email", request.getEmail());
                receiptService.sendReceiptEmail(request.getEmail(), order, transaction,
                        receiptService.receiptLocaleFromChainConfig());
                log.info("Receipt email sent successfully for order: {}", order.getId());
            } catch (Exception e) {
                log.error("Failed to send receipt email for order: {}", order.getId(), e);
                // Don't fail the payment if email sending fails
            }
        } else {
            log.info("No email provided in payment request - skipping email sending. Email field: '{}'", 
                    request.getEmail() != null ? request.getEmail() : "null");
        }
        
        // Send payment completion notification to the cashier who processed the payment
        try {
            notificationService.notifyPaymentCompleted(order, cashier, request.getPaymentMethod(), request.getAmountPaid(), userLocale);
            log.info("Sent payment completion notification to cashier {} for order {}", cashier.getId(), order.getId());
        } catch (Exception e) {
            log.error("Failed to send payment completion notification to cashier {}: {}", cashier.getId(), e.getMessage(), e);
        }
        
        // Send notification to all waiters assigned to the table about payment completion
        // Exclude cashier from waiter list to avoid duplicate notification when cashier is also assigned to the table
        try {
            if (order.getRestaurantTable() != null) {
                List<User> assignedWaiters = orderValidationService.getWaitersForTable(order.getRestaurantTable());
                List<User> waitersExcludingCashier = assignedWaiters != null
                        ? assignedWaiters.stream()
                                .filter(w -> w != null && !w.getId().equals(cashier.getId()))
                                .toList()
                        : List.of();
                notifyAssignedWaitersSafe(waitersExcludingCashier, order.getId(), order.getRestaurantTable(),
                        waiter1 -> notificationService.notifyPaymentCompleted(order, waiter1, request.getPaymentMethod(), request.getAmountPaid(), userLocale),
                        "payment completion");
            }
        } catch (Exception e) {
            log.error("Failed to send payment completion notification: {}", e.getMessage(), e);
        }
        
        log.info("Payment processed successfully for order: {} with transaction number: {}", 
                order.getId(), transactionNumber);

        // Build PaymentResponse (aligned with updated DTO)
        PaymentResponse paymentResponse = PaymentResponse.builder()
                .orderId(request.getOrderId())
                .paymentMethod(request.getPaymentMethod())
                .paymentApp(transaction.getPaymentApp())
                .amountPaid(request.getAmountPaid())
                .cashReceived(request.getCashReceived())
                .changeReturned(request.getChangeReturned())
                .transactionId(transaction.getId())
                .transactionNumber(transactionNumber)
                .transactionStatus(transaction.getTransactionStatus())
                .receiptUrl(transaction.getReceiptUrl() != null ? awsService.getPreSignedUrlForPdf(transaction.getReceiptUrl()) : null)
                .build();

        return ResponseDto.<PaymentResponse>builder()
                .message(messageUtil.getMessage("payment.processed.successfully", userLocale))
                .data(paymentResponse)
                .build();
    }

    /**
     * Reuse stored GMO URL only while it matches {@code gmo.link-plus.payment-expires-minutes}
     * (same window sent to GMO as {@code PaymentExpireDate}).
     */
    private boolean isResumableHostedCardLink(Transaction transaction, String storedLink) {
        if (storedLink == null || storedLink.isBlank()) {
            return false;
        }
        int expiresMinutes = gmoLinkPlusProperties.getPaymentExpiresMinutes();
        if (expiresMinutes <= 0) {
            return true;
        }
        OffsetDateTime createdAt = transaction.getGmoHostedPaymentLinkCreatedAt();
        if (createdAt == null) {
            return false;
        }
        OffsetDateTime expiresAt = createdAt.plusMinutes(expiresMinutes);
        return OffsetDateTime.now(ZoneOffset.UTC).isBefore(expiresAt);
    }

    /**
     * After GMO {@code E90010001}, the other client's request may have saved the checkout URL; poll briefly.
     */
    private String resolvePeerHostedCardLinkAfterDoubleSubmission(UUID transactionId) {
        final int attempts = 6;
        final long delayMs = 200L;
        for (int i = 0; i < attempts; i++) {
            Optional<Transaction> opt = transactionRepository.findById(transactionId);
            if (opt.isPresent()) {
                Transaction tx = opt.get();
                String stored = tx.getGmoHostedPaymentUrl();
                if (isResumableHostedCardLink(tx, stored)) {
                    return stored.trim();
                }
            }
            if (i < attempts - 1) {
                try {
                    Thread.sleep(delayMs);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }
        return null;
    }

    private ResponseDto<PaymentResponse> buildHostedCardPaymentResponse(Locale userLocale,
                                                                        PaymentRequest request,
                                                                        Order order,
                                                                        Transaction transaction,
                                                                        String linkUrl) {
        UUID restaurantId = orderNotificationService.getRestaurantIdFromOrder(order);
        orderNotificationService.sendTransactionStatusWebSocketNotification(
                userLocale, restaurantId, transaction.getId(), TransactionStatus.PENDING);

        PaymentResponse paymentResponse = PaymentResponse.builder()
                .orderId(request.getOrderId())
                .paymentMethod(transaction.getPaymentMethod() != null
                        ? transaction.getPaymentMethod() : request.getPaymentMethod())
                .paymentApp(transaction.getPaymentApp() != null
                        ? transaction.getPaymentApp()
                        : resolvePaymentApp(request.getPaymentMethod(), request.getType()))
                .amountPaid(transaction.getTransactionAmount() != null
                        ? transaction.getTransactionAmount() : request.getAmountPaid())
                .cashReceived(request.getCashReceived())
                .changeReturned(request.getChangeReturned())
                .transactionId(transaction.getId())
                .transactionNumber(transaction.getTransactionNumber())
                .transactionStatus(transaction.getTransactionStatus())
                .linkUrl(linkUrl)
                .authorizationUri(linkUrl)
                .gmoLinkOrderId(order.getGmoLinkOrderId())
                .restaurantId(restaurantId)
                .build();

        return ResponseDto.<PaymentResponse>builder()
                .message(messageUtil.getMessage("payment.initiated.successfully", userLocale))
                .data(paymentResponse)
                .build();
    }

    private void scheduleOrderReceiptGenerationAfterCommit(UUID transactionId) {
        if (transactionId == null) {
            return;
        }
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    receiptGenerationAsyncService.generateOrderReceiptAfterPayment(transactionId);
                }
            });
            return;
        }
        receiptGenerationAsyncService.generateOrderReceiptAfterPayment(transactionId);
    }
    /**
     * Initiates checkout process for an order, creating or updating transaction and generating payment options.
     * For UPI payments, creates QR code payment; for other methods, prepares transaction for payment.
     *
     * @param userId  the ID of the user initiating checkout
     * @param orderId the UUID of the order to checkout
     * @return {@link ResponseDto} containing checkout response with payment options
     * @throws ResponseStatusException if order not found, validation fails, or checkout initiation fails
     */
    @Override
    public ResponseDto<InitiateCheckoutResponse> initiateCheckout(String userId, String sessionIdHeader, UUID orderId) {
        Locale userLocale = LocaleContextHolder.getLocale();
        
        log.info("Initiating checkout for order: {} by user: {}", orderId, userId);
        
        // Find the order
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        messageUtil.getMessage(MSG_ORDER_NOT_FOUND, userLocale)));

        UUID orderSessionId = order.getSession() != null ? order.getSession().getId() : null;
        orderValidationService.validateCustomerOrStaffSessionAccess(userId, sessionIdHeader, orderSessionId, userLocale);
        
        // Get authenticated user
        User authenticatedUser = null;
        boolean hasUserId = false;
        if (orderValidationService.isValidUserId(userId)) {
            authenticatedUser = orderValidationService.validateAndGetUserOrNull(userId, userLocale);
            hasUserId = authenticatedUser != null;
        }
        
        // Find the transaction for this order
        Transaction transaction = transactionRepository.findByOrderId(orderId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        messageUtil.getMessage("transaction.not.found", userLocale)));
        
        // Check if transaction status is OPEN
        TransactionStatus transactionStatus = transaction.getTransactionStatus();
        if (transactionStatus != TransactionStatus.OPEN) {
            String messageKey;
            switch (transactionStatus) {
                case PENDING:
                    messageKey = "checkout.not.allowed.transaction.pending";
                    break;
                case COMPLETED:
                    messageKey = "checkout.not.allowed.transaction.completed";
                    break;
                case REFUNDED:
                    messageKey = "checkout.not.allowed.transaction.refunded";
                    break;
                case CANCELED:
                    messageKey = "checkout.not.allowed.transaction.canceled";
                    break;
                default:
                    messageKey = "checkout.already.initiated";
                    break;
            }
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    messageUtil.getMessage(messageKey, userLocale));
        }
        
        // Get all ordered items and combos
        List<OrderedItem> orderedItems = orderedItemRepository.findByOrderId(orderId);
        List<OrderedCombo> orderedCombos = orderedComboRepository.findByOrderId(orderId);
        
        // Filter for regular items only (not combo items, as they are validated through combos)
        List<OrderedItem> regularItems = orderedItems.stream()
                .filter(item -> item.getOrderedCombo() == null) // Only regular items, not combo items
                .collect(Collectors.toList());

        // Check if order has a pending cancellation request
        if (order.getCancellationRequestStatus() == RequestStatus.OPEN) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    messageUtil.getMessage("checkout.not.allowed.order.cancellation.pending", userLocale));
        }
        
        // Check if any item has a pending cancellation request
        // Filter for regular items only (not combo items, as they are validated through combos)
        boolean hasItemCancellationRequest = orderedItems.stream()
                .filter(item -> item.getOrderedCombo() == null) // Only regular items, not combo items
                .anyMatch(item -> item.getCancellationRequestStatus() == RequestStatus.OPEN);
        
        // Check if any combo has a pending cancellation request
        boolean hasComboCancellationRequest = orderedCombos.stream()
                .anyMatch(combo -> combo.getCancellationRequestStatus() == RequestStatus.OPEN);
        
        if (hasItemCancellationRequest || hasComboCancellationRequest) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    messageUtil.getMessage("checkout.not.allowed.item.cancellation.pending", userLocale));
        }
        
        // Update transaction status to PENDING
        transaction.setTransactionStatus(TransactionStatus.PENDING);
        transaction.setUpdatedAt(OffsetDateTime.now(ZoneOffset.UTC));
        transactionRepository.save(transaction);
        
        log.info("Successfully updated transaction status from OPEN to PENDING for order: {}", orderId);
        
        UUID checkoutRestaurantId = orderNotificationService.getRestaurantIdFromOrder(order);
        orderNotificationService.sendCheckoutInitiatedTransactionStatusWebSocketNotification(
                userLocale, checkoutRestaurantId, transaction.getId(), order, TransactionStatus.PENDING);
        
        // Build InitiateCheckoutResponse
        InitiateCheckoutResponse checkoutResponse = InitiateCheckoutResponse.builder()
                .orderId(order.getId())
                .sessionId(order.getSession().getId())
                .orderStatus(order.getOrderStatus())
                .transactionId(transaction.getId())
                .transactionStatus(transaction.getTransactionStatus())
                .build();
        
        return ResponseDto.<InitiateCheckoutResponse>builder()
                .data(checkoutResponse)
                .message(messageUtil.getMessage("checkout.initiated.successfully", userLocale))
                .build();
    }

    /**
     * Generates a pre-signed URL for downloading the receipt PDF for an order.
     * The receipt is generated on-demand and uploaded to S3 if it doesn't exist.
     *
     * @param orderId the UUID of the order to get receipt URL for
     * @return {@link ResponseDto} containing the pre-signed URL for the receipt
     * @throws ResponseStatusException if order not found, transaction not found, or receipt generation fails
     */
    @Override
    public ResponseDto<ReceiptUrlResponse> getReceiptPresignedUrl(UUID orderId) {
        Locale userLocale = LocaleContextHolder.getLocale();
        
        log.info("Request received to get receipt presigned URL for order: {}", orderId);
        
        // Find the order
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        messageUtil.getMessage(MSG_ORDER_NOT_FOUND, userLocale)));
        
        // Find the transaction for this order
        Transaction transaction = transactionRepository.findByOrderId(orderId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        messageUtil.getMessage("transaction.not.found", userLocale)));
        
        // Check if transaction has a receipt URL
        String receiptUrl = transaction.getReceiptUrl();
        if (receiptUrl == null || receiptUrl.trim().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                    messageUtil.getMessage("order.receipt.not.found", userLocale));
        }
        
        try {
            // Generate presigned URL for the receipt with PDF-specific headers for inline preview
            String presignedUrl = awsService.getPreSignedUrlForPdf(receiptUrl);
            String presignedUrlAttachment = awsService.getPreSignedUrlForPdfAttachment(receiptUrl);
            
            ReceiptUrlResponse response = ReceiptUrlResponse.builder()
                    .orderId(order.getId().toString())
                    .orderNumber(order.getOrderNumber())
                    .receiptUrl(presignedUrl)
                    .downloadReceiptUrl(presignedUrlAttachment)
                    .build();
            
            log.info("Successfully generated presigned URL for order: {}", orderId);
            return ResponseDto.<ReceiptUrlResponse>builder()
                    .data(response)
                    .message(messageUtil.getMessage("order.receipt.url.generated.successfully", userLocale))
                    .build();
                    
        } catch (Exception e) {
            log.error("Error generating presigned URL for order: {}", orderId, e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    messageUtil.getMessage("order.receipt.url.generation.failed", userLocale));
        }
    }

    /**
     * Creates a cancellation request for an order.
     * For managers, cancels directly; for other users, creates a cancellation request requiring approval.
     *
     * @param userId  the ID of the user requesting cancellation
     * @param orderId the UUID of the order to cancel
     * @param request the cancellation request containing reason and comments
     * @return {@link ResponseDto} containing cancellation request result
     * @throws ResponseStatusException if order not found, validation fails, or cancellation fails
     */
    @Override
    @Transactional
    public ResponseDto<OrderCancellationRequestResponse> requestOrderCancellation(String userId, UUID orderId, OrderCancellationRequestDto request) {
        Locale userLocale = LocaleContextHolder.getLocale();
        
        // Get authenticated user
        User authenticatedUser = null;
        if (userId != null && !userId.trim().isEmpty() && !"null".equalsIgnoreCase(userId)) {
            try {
                authenticatedUser = userRepository.findById(UUID.fromString(userId))
                        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                                messageUtil.getMessage("user.not.found", userLocale)));
            } catch (IllegalArgumentException e) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        messageUtil.getMessage("invalid.user.id", userLocale));
            }
        } else {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,
                    messageUtil.getMessage("user.authentication.required", userLocale));
        }
        
        // Get order
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        messageUtil.getMessage(MSG_ORDER_NOT_FOUND, userLocale)));
        
        // Check if user is MANAGER - managers can cancel orders directly without creating request
        if (orderValidationService.isManager(authenticatedUser)) {
            // Manager can cancel order directly - no request needed
            // Check if order is already cancelled
            if (order.getOrderStatus() == OrderStatus.CANCELED) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        messageUtil.getMessage("order.already.cancelled", userLocale));
            }
            
            // Capture order status before cancellation to check if it was in PENDING status (PUSHED or IN_PROGRESS)
            OrderStatus previousOrderStatus = order.getOrderStatus();
            
            // Use UTC timezone to match the rest of the application
            OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
            
            // Store cancellation reason in cancellationRequestData for audit purposes only
            // Do NOT set cancellationRequestStatus - managers cancel directly, no request is created
            if (request.getCancellationReason() != null && !request.getCancellationReason().trim().isEmpty()) {
                try {
                    ObjectMapper objectMapper = new ObjectMapper();
                    Map<String, Object> requestDataMap = new HashMap<>();
                    requestDataMap.put(PARAM_REQUEST_TYPE, "ORDER_CANCELLATION");
                    requestDataMap.put(PARAM_CANCELLATION_REASON, request.getCancellationReason());
                    requestDataMap.put("requestedStatus", OrderStatus.CANCELED.toString());
                    String requestData = objectMapper.writeValueAsString(requestDataMap);
                    order.setCancellationRequestData(requestData);
                    // Do NOT set cancellationRequestStatus - keep it as NONE since this is not a request
                    // Do NOT set cancellationRequestedAt or cancellationRequestedBy
                } catch (JsonProcessingException e) {
                    log.warn("Failed to store cancellation reason for order {}: {}", orderId, e.getMessage());
                }
            }
            
            log.info("=== CASHIER NOTIFICATION CHECK: Manager {} requested direct cancellation for order {} (previousOrderStatus={}) ===",
                    authenticatedUser.getId(), orderId, previousOrderStatus);

            Optional<Transaction> transactionOpt = transactionRepository.findByOrderId(orderId);
            TransactionStatus transactionStatusBeforeCancel = transactionOpt.map(Transaction::getTransactionStatus).orElse(null);
            RestaurantChainConfigProperties.RestaurantChainData chainDataForPolicy = restaurantChainConfigProperties.getChain();
            PaymentSystemType chainPaymentTypeForPolicy = chainDataForPolicy != null ? chainDataForPolicy.getPaymentType() : null;
            boolean skipOrderAmountAdjustment = CancellationAmountPolicy.shouldSkipOrderAmountAdjustmentOnCancellation(
                    order.getOrderType(), chainPaymentTypeForPolicy, transactionStatusBeforeCancel);

            // Cancel the order directly
            order.setOrderStatus(OrderStatus.CANCELED);
            order.setUpdatedAt(now);
            order.setUpdatedBy(authenticatedUser);
            
            // Cancel all items and combos in the order
            cancelAllOrderItemsAndCombos(order, authenticatedUser, userLocale);
            
            // Cancel the transaction if it exists
            if (transactionOpt.isPresent()) {
                Transaction transaction = transactionOpt.get();

                TransactionStatus currentStatus = transaction.getTransactionStatus();
                // Never downgrade a COMPLETED transaction to CANCELED.
                // Also avoid canceling if already canceled/refunded/partially refunded.
                if (currentStatus != TransactionStatus.CANCELED &&
                    currentStatus != TransactionStatus.REFUNDED &&
                    currentStatus != TransactionStatus.PARTIALLY_REFUNDED &&
                    currentStatus != TransactionStatus.COMPLETED) {

                    // Capture previous status before updating
                    TransactionStatus previousStatus = transaction.getTransactionStatus();
                    log.info("=== CASHIER NOTIFICATION CHECK: Transaction {} for order {} changing status {} -> {} (manager direct cancel) ===",
                            transaction.getId(), orderId, previousStatus, TransactionStatus.CANCELED);
                    
                    transaction.setTransactionStatus(TransactionStatus.CANCELED);
                    transaction.setUpdatedAt(now);
                    transactionRepository.save(transaction);
                    log.info("Transaction {} canceled when order {} was cancelled by manager", transaction.getId(), orderId);
                } else {
                    log.info("=== CASHIER NOTIFICATION CHECK: Transaction {} for order {} already in terminal status ({}), skipping manager direct-cancel cashier flow ===",
                            transaction.getId(), orderId, transaction.getTransactionStatus());
                }
            }

            if (!skipOrderAmountAdjustment) {
                CancellationAmountPolicy.resetOrderMonetaryTotalsForFullCancellation(order);
                log.info("Order {} monetary totals reset after manager direct cancellation (policy: adjust amounts)", orderId);
            } else {
                log.info("Order {} monetary totals unchanged after manager direct cancellation (same skip policy as item no-deduction)", orderId);
            }
            
            orderRepository.save(order);
            
            // Create audit trail for order cancellation by manager (manager entry)
            try {
                auditTrailService.createAuditTrail(
                        authenticatedUser,
                        ActionType.ORDER_CANCEL,
                        order.getRestaurant(),
                        RequestStatus.NA,
                        null, // ipAddress
                        null, // userAgent
                        order.getId(),
                        ENTITY_TYPE_ORDER,
                        String.format("Order cancelled by manager. Reason: %s", 
                                request.getCancellationReason() != null ? request.getCancellationReason() : "N/A")
                );
            } catch (Exception e) {
                log.error("Failed to create audit trail for order cancellation: {}", e.getMessage());
            }
            
            // Send WebSocket notification for order cancellation (KDS)
            try {
                UUID restaurantId = orderNotificationService.getRestaurantIdFromOrder(order);
                log.info("About to invoke sendOrderCancellationWebSocketNotification for order {} at restaurant {} with reason '{}'",
                        order.getId(), restaurantId, request.getCancellationReason());
                orderNotificationService.sendOrderCancellationWebSocketNotification(userLocale, restaurantId, order.getId(), request.getCancellationReason());
                log.info("Sent WebSocket notification for order cancellation: {} for restaurant {}", order.getId(), restaurantId);
            } catch (Exception e) {
                log.error("Failed to send WebSocket notification for order cancellation: {}", e.getMessage());
            }
            
            // Send KDS notification for order cancellation (includes database notifications)
            try {
                notificationService.notifyKdsOrderCanceled(order, request.getCancellationReason(), userLocale);
                log.info("Sent KDS notification for order cancellation by manager: {} for restaurant {}", order.getId(), order.getRestaurant() != null ? order.getRestaurant().getId() : "unknown");
            } catch (Exception e) {
                log.error("Failed to send KDS notification for order cancellation: {}", e.getMessage(), e);
            }
            
            // ==================== NOTIFY WAITERS - ORDER CANCELLED ====================
            try {
                if (order.getRestaurantTable() != null) {
                    List<User> assignedWaiters = orderValidationService.getWaitersForTable(order.getRestaurantTable());
                    notifyAssignedWaitersSafe(assignedWaiters, order.getId(), order.getRestaurantTable(),
                            waiter1 -> notificationService.notifyOrderCancelled(order, waiter1, userLocale),
                            "order cancellation");
                }
            } catch (Exception e) {
                log.error("Failed to send order cancellation notification to waiters: {}", e.getMessage(), e);
            }
            
            // ==================== NOTIFY ALL RESTAURANT CASHIERS - ORDER CANCELLED BY MANAGER ====================
            // Notify ALL active cashiers of this order's restaurant when a manager directly cancels an order.
            try {
                UUID restaurantId = order.getRestaurant() != null ? order.getRestaurant().getId() : null;
                if (restaurantId != null) {
                    log.info("=== CASHIER NOTIFICATION: Manager-direct cancel for order {} with previousOrderStatus={}. Resolving all restaurant cashiers for restaurant {} ===",
                            orderId, previousOrderStatus, restaurantId);

                    Optional<Role> cashierRoleOpt = roleRepository.findByName(ROLE_CASHIER);
                    if (cashierRoleOpt.isPresent()) {
                        UUID cashierRoleId = cashierRoleOpt.get().getId();
                        List<User> restaurantCashiers = userRepository.findAllByRestaurantIdAndRoleIdAndIsDeletedFalse(restaurantId, cashierRoleId);
                        
                        if (restaurantCashiers != null && !restaurantCashiers.isEmpty()) {
                            log.info("=== CASHIER NOTIFICATION: About to notify {} restaurant cashiers for order {} cancellation by manager. Cashier IDs: {} ===",
                                    restaurantCashiers.size(),
                                    orderId,
                                    restaurantCashiers.stream().map(User::getId).toList());
                            String reason = request.getCancellationReason();
                            notificationService.notifyCashiersOrderCancelledByManager(
                                    order,
                                    new ArrayList<>(restaurantCashiers),
                                    reason,
                                    userLocale
                            );
                            log.info("Notified {} restaurant cashier(s) about order {} cancellation by manager",
                                    restaurantCashiers.size(), orderId);
                        } else {
                            log.info("=== CASHIER NOTIFICATION: No active restaurant cashiers found for restaurant {} when cancelling order {} by manager ===",
                                    restaurantId, orderId);
                        }
                    } else {
                        log.warn("=== CASHIER NOTIFICATION: CASHIER role not found when attempting to notify restaurant cashiers for order {} cancellation by manager ===",
                                orderId);
                    }
                } else {
                    log.warn("=== CASHIER NOTIFICATION: Restaurant is null for order {} when attempting to notify restaurant cashiers ===",
                            orderId);
                }
            } catch (Exception e) {
                log.error("Failed to notify restaurant cashiers about order cancellation for order {}: {}", orderId, e.getMessage(), e);
            }
            
            // ==================== REAL-TIME HQ ALERT EVALUATION ====================
            // Check if sales/refund/cancellation thresholds are breached after this order cancellation.
            // Must run AFTER transaction commits so the REQUIRES_NEW alert transaction can see the
            // cancelled order data (order status change to CANCELED).
            try {
                Restaurant restaurant = order.getRestaurant();
                if (restaurant != null) {
                    final Restaurant finalRestaurant = restaurant;
                    final Locale finalLocale = userLocale;

                    if (TransactionSynchronizationManager.isActualTransactionActive()) {
                        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                            /**
                             * Executes after the order cancellation transaction commits, evaluating restaurant alerts in real-time.
                             * This ensures alert evaluation sees the committed cancellation data.
                             */
                            @Override
                            public void afterCommit() {
                                evaluateAlertsAfterOrderCancellationCommitBestEffort(finalRestaurant, finalLocale);
                            }
                        });
                        log.info("📋 Registered alert evaluation to run after order cancellation commit for restaurant: {}",
                                restaurant.getRestaurantCode());
                    } else {
                        log.info("🔔 Triggering alert evaluation for restaurant: {} after order cancellation (no active transaction)",
                                restaurant.getRestaurantCode());
                        restaurantAlertEvaluationService.evaluateRestaurantAlertsRealtime(restaurant, userLocale);
                        log.info("✅ Alert evaluation completed for restaurant: {} after order cancellation (no active transaction)",
                                restaurant.getRestaurantCode());
                    }
                }
            } catch (Exception e) {
                log.error("Failed to evaluate real-time alerts after order cancellation: {}", e.getMessage(), e);
                // Don't fail the order cancellation if alert evaluation fails
            }

            orderRecalculationService.maybeExpireTakeawaySessionWhenOrderServedAndTransactionCompleted(orderId);
            
            log.info("Manager directly cancelled order {}", orderId);
            
            return ResponseDto.<OrderCancellationRequestResponse>builder()
                    .message(messageUtil.getMessage("order.fully.cancelled", userLocale))
                    .data(OrderCancellationRequestResponse.builder()
                            .orderId(order.getId())
                            .orderNumber(order.getOrderNumber())
                            .transactionNumber(transactionRepository.findByOrderId(order.getId()).map(Transaction::getTransactionNumber).orElse(null))
                            .currentOrderStatus(order.getOrderStatus())
                            .cancellationReason(request.getCancellationReason())
                            .requestStatus(RequestStatus.NONE) // No request created when manager cancels directly
                            .build())
                    .build();
        }
        
        // Check if there's already a pending cancellation request
        if (order.getCancellationRequestStatus() == RequestStatus.OPEN) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    messageUtil.getMessage("order.cancellation.request.already.pending", userLocale));
        }
        
        // Check if order is already cancelled
        if (order.getOrderStatus() == OrderStatus.CANCELED) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    messageUtil.getMessage("order.already.cancelled", userLocale));
        }
        
        // Create cancellation request data
        try {
            ObjectMapper objectMapper = new ObjectMapper();
            OrderCancellationRequestDto requestDto = OrderCancellationRequestDto.builder()
                    .cancellationReason(request.getCancellationReason())
                    .requestedStatus(OrderStatus.CANCELED) // Store the requested status (CANCELED)
                    .build();
            // Add requestType flag to distinguish from additional discount requests
            Map<String, Object> requestDataMap = new HashMap<>();
            requestDataMap.put(PARAM_REQUEST_TYPE, "ORDER_CANCELLATION");
            requestDataMap.put(PARAM_CANCELLATION_REASON, requestDto.getCancellationReason());
            requestDataMap.put("requestedStatus", requestDto.getRequestedStatus() != null ? requestDto.getRequestedStatus().toString() : null);
            String requestData = objectMapper.writeValueAsString(requestDataMap);
            
            // Use UTC timezone to match the rest of the application
            OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
            
            order.setCancellationRequestStatus(RequestStatus.OPEN);
            order.setCancellationRequestData(requestData);
            order.setCancellationRequestedAt(now);
            order.setCancellationRequestedBy(authenticatedUser);
            // Clear previous review information when creating a new request
            order.setCancellationComments(null);
            order.setCancellationReviewedAt(null);
            order.setCancellationReviewedBy(null);
            order.setUpdatedAt(now);
            order.setUpdatedBy(authenticatedUser);
            
            orderRepository.save(order);
            
            // Notify managers about newly opened cancellation request
            notifyManagersAboutOrderCancellationSafe(order, userLocale);
            
            // Send WebSocket notifications to all three topics (item-status, order-status, transaction-status) for order cancellation request creation
            try {
                UUID restaurantId = orderNotificationService.getRestaurantIdFromOrder(order);
                if (restaurantId != null) {
                    // Send to item-status topic - only broadcast ON_HOLD status.
                    // All KDS-specific statuses are excluded to prevent cross-category KDS notification leaks.
                    List<OrderedItem> orderedItems = orderedItemRepository.findByOrderId(order.getId());
                    for (OrderedItem orderedItem : orderedItems) {
                        if (orderedItem.getItemStatus() != null
                                && orderedItem.getItemStatus() == ItemStatus.ON_HOLD) {
                            orderNotificationService.sendItemStatusWebSocketNotification(userLocale, restaurantId, orderedItem.getId(), orderedItem.getItemStatus(), "item");
                        }
                    }
                    // Also notify for ON_HOLD combos in the order
                    List<OrderedCombo> orderedCombos = orderedComboRepository.findByOrderId(order.getId());
                    for (OrderedCombo orderedCombo : orderedCombos) {
                        if (orderedCombo.getItemStatus() != null
                                && orderedCombo.getItemStatus() == ItemStatus.ON_HOLD) {
                            orderNotificationService.sendItemStatusWebSocketNotification(userLocale, restaurantId, orderedCombo.getId(), orderedCombo.getItemStatus(), TYPE_COMBO);
                        }
                    }
                    
                    // Send to order-status topic
                    orderNotificationService.sendOrderStatusWebSocketNotification(userLocale, restaurantId, order.getId(), order.getOrderStatus());
                    
                    // Send to transaction-status topic
                    Optional<Transaction> transactionOpt = transactionRepository.findByOrderId(order.getId());
                    if (transactionOpt.isPresent()) {
                        orderNotificationService.sendTransactionStatusWebSocketNotification(userLocale, restaurantId, transactionOpt.get().getId(), transactionOpt.get().getTransactionStatus());
                    }
                }
            } catch (Exception e) {
                log.warn("Failed to send WebSocket notifications for order cancellation request: {}", e.getMessage());
            }
            
            log.info("Created cancellation request for order {}", orderId);
            
            return ResponseDto.<OrderCancellationRequestResponse>builder()
                    .message(messageUtil.getMessage("order.cancellation.request.created", userLocale))
                    .data(buildOrderCancellationRequestResponse(order))
                    .build();
            
        } catch (JsonProcessingException e) {
            log.error("Error creating cancellation request data: {}", e.getMessage());
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    messageUtil.getMessage("order.cancellation.request.error", userLocale));
        }
    }

    /**
     * Cancels all items and combos in an order.
     * Used when canceling an entire order to cancel all associated items and combos.
     *
     * @param order     the order containing items and combos to cancel
     * @param user      the user performing the cancellation
     * @param userLocale locale for localized error messages
     */
    private void cancelAllOrderItemsAndCombos(Order order, User user, Locale userLocale) {
        if (order == null) {
            log.warn("Cannot cancel items/combos: order is null");
            return;
        }

        UUID orderId = order.getId();
        // Use UTC timezone to match the rest of the application
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);

        // Cancel all orderedItems (only regular items, not combo items)
        List<OrderedItem> orderedItems = orderedItemRepository.findByOrderId(orderId).stream()
                .filter(item -> item.getOrderedCombo() == null) // Only regular items
                .filter(item -> item.getItemStatus() != ItemStatus.CANCELED) // Skip already canceled
                .collect(Collectors.toList());

        for (OrderedItem item : orderedItems) {
            // Capture status before cancellation for wastage reporting
            if (item.getItemStatus() != null && item.getItemStatus() != ItemStatus.CANCELED) {
                item.setWastageSourceStatus(item.getItemStatus());
            }
            item.setItemStatus(ItemStatus.CANCELED);
            item.setUpdatedAt(now);
            item.setUpdatedBy(user);
            orderedItemRepository.save(item);
            log.info("Cancelled orderedItem {} for order {}", item.getId(), orderId);
            
            // Create audit trail for item cancellation
            try {
                Restaurant restaurant = order.getRestaurant();
                auditTrailService.createAuditTrail(
                        user,
                        ActionType.CANCELLATION,
                        restaurant,
                        RequestStatus.NA, // Direct cancellation doesn't require approval
                        null, // ipAddress
                        null, // userAgent
                        item.getId(),
                        "ITEM",
                        "Item cancelled due to order cancellation"
                );
            } catch (Exception e) {
                log.error("Failed to create audit trail for item cancellation (item {}): {}", item.getId(), e.getMessage(), e);
                // Don't break the flow if audit trail fails
            }
        }

        // Cancel all orderedCombos
        List<OrderedCombo> orderedCombos = orderedComboRepository.findByOrderId(orderId).stream()
                .filter(combo -> combo.getItemStatus() != ItemStatus.CANCELED) // Skip already canceled
                .collect(Collectors.toList());

        for (OrderedCombo combo : orderedCombos) {
            // Capture status before cancellation for wastage reporting
            if (combo.getItemStatus() != null && combo.getItemStatus() != ItemStatus.CANCELED) {
                combo.setWastageSourceStatus(combo.getItemStatus());
            }
            combo.setItemStatus(ItemStatus.CANCELED);
            combo.setUpdatedAt(now);
            combo.setUpdatedBy(user);
            orderedComboRepository.save(combo);
            log.info("Cancelled orderedCombo {} for order {}", combo.getId(), orderId);
        }
    }
    
    /**
     * Builds a cancellation request response DTO from an order entity.
     * Includes cancellation reason, comments, and request status.
     *
     * @param order the order entity to build response from
     * @return {@link OrderCancellationRequestResponse} with cancellation details
     */
    private OrderCancellationRequestResponse buildOrderCancellationRequestResponse(Order order) {
        Locale userLocale = LocaleContextHolder.getLocale();
        
        String cancellationReason = null;
        if (order.getCancellationRequestData() != null) {
            try {
                ObjectMapper objectMapper = new ObjectMapper();
                OrderCancellationRequestDto requestDto = objectMapper.readValue(
                        order.getCancellationRequestData(), OrderCancellationRequestDto.class);
                cancellationReason = requestDto.getCancellationReason();
            } catch (JsonProcessingException e) {
                log.warn("Failed to parse cancellation request data for order {}: {}", order.getId(), e.getMessage());
            }
        }
        
        String requestedByName = null;
        String requestedByRole = null;
        if (order.getCancellationRequestedBy() != null && order.getCancellationRequestedBy().getRoleId() != null) {
            requestedByName = order.getCancellationRequestedBy().getFirstName() + " " + 
                    order.getCancellationRequestedBy().getLastName();
            Role requesterRole = roleRepository.findById(order.getCancellationRequestedBy().getRoleId()).orElse(null);
            if (requesterRole != null) {
                requestedByRole = requesterRole.getName();
            }
        }
        
        String reviewedByName = null;
        if (order.getCancellationReviewedBy() != null) {
            reviewedByName = order.getCancellationReviewedBy().getFirstName() + " " + 
                    order.getCancellationReviewedBy().getLastName();
        }

        String transactionNumber = null;
        try {
            transactionNumber = transactionRepository.findByOrderId(order.getId())
                    .map(Transaction::getTransactionNumber)
                    .orElse(null);
        } catch (Exception e) {
            log.debug("Could not fetch transactionNumber for order {}: {}", order.getId(), e.getMessage());
        }
        
        return OrderCancellationRequestResponse.builder()
                .orderId(order.getId())
                .orderNumber(order.getOrderNumber())
                .transactionNumber(transactionNumber)
                .currentOrderStatus(order.getOrderStatus())
                .cancellationReason(cancellationReason)
                .requestStatus(order.getCancellationRequestStatus())
                .requestedAt(order.getCancellationRequestedAt() != null ? order.getCancellationRequestedAt().toLocalDateTime() : null)
                .requestedBy(order.getCancellationRequestedBy() != null ? order.getCancellationRequestedBy().getId() : null)
                .requestedByName(requestedByName)
                .requestedByRole(requestedByRole)
                .reviewedAt(order.getCancellationReviewedAt() != null ? order.getCancellationReviewedAt().toLocalDateTime() : null)
                .reviewedBy(order.getCancellationReviewedBy() != null ? order.getCancellationReviewedBy().getId() : null)
                .reviewedByName(reviewedByName)
                .comments(order.getCancellationComments())
                .restaurantId(orderNotificationService.getRestaurantIdSafely(order))
                .restaurantName(order.getRestaurant() != null ? order.getRestaurant().getRestaurantCode() : null)
                .tableId(order.getRestaurantTable() != null ? order.getRestaurantTable().getId() : null)
                .tableName(order.getRestaurantTable() != null ? order.getRestaurantTable().getTableOrder().toString() : null)
                .totalAmount(order.getTotalAmount())
                .build();
    }


    /**
     * Submits a rating for an order.
     * Validates that the order exists and can be rated, then saves the rating.
     *
     * @param userId  the ID of the user submitting the rating
     * @param orderId the UUID of the order to rate
     * @param request the rating request containing rating value and optional comments
     * @return {@link ResponseDto} containing the submitted rating details
     * @throws ResponseStatusException if order not found, validation fails, or rating submission fails
     */
    @Override
    @Transactional
    public ResponseDto<RatingDto<RatingResponse>> submitRating(String userId, String sessionIdHeader, UUID orderId, RatingRequest request) {
        Locale userLocale = LocaleContextHolder.getLocale();
        
        log.info("Submitting rating for order: {} by user: {}", orderId, userId);
        
        // ==================== VALIDATE ORDER ====================
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        messageUtil.getMessage(MSG_ORDER_NOT_FOUND, userLocale)));

        UUID orderSessionId = order.getSession() != null ? order.getSession().getId() : null;
        orderValidationService.validateCustomerOrStaffSessionAccess(userId, sessionIdHeader, orderSessionId, userLocale);
        
        // ==================== CHECK IF RATING ALREADY EXISTS ====================
        if (ratingRepository.existsByOrderId(orderId)) {
            log.warn("Rating already exists for order: {}", orderId);
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    messageUtil.getMessage("rating.already.exists", userLocale));
        }
        
        // ==================== CREATE RATING ====================
        Rating rating = Rating.builder()
                .order(order)
                .experience(request.getExperience())
                .food(request.getFood())
                .service(request.getService())
                .feedback(request.getFeedback())
                .build();
        
        rating = ratingRepository.save(rating);
        
        // ==================== BUILD RESPONSE ====================
        RatingResponse ratingResponse = RatingResponse.builder()
                .id(rating.getId())
                .orderId(rating.getOrder().getId())
                .experience(rating.getExperience())
                .food(rating.getFood())
                .service(rating.getService())
                .feedback(rating.getFeedback())
                .createdAt(rating.getCreatedAt() != null ? rating.getCreatedAt().toLocalDateTime() : null)
                .updatedAt(rating.getUpdatedAt() != null ? rating.getUpdatedAt().toLocalDateTime() : null)
                .build();
        
        RatingDto<RatingResponse> ratingDto = RatingDto.<RatingResponse>builder()
                .rating(ratingResponse)
                .build();
        
        return ResponseDto.<RatingDto<RatingResponse>>builder()
                .message(messageUtil.getMessage("rating.submitted.successfully", userLocale))
                .data(ratingDto)
                .build();
    }

    /**
     * Retrieves paginated order history for a restaurant with multiple filters.
     * Supports filtering by order status, order type, transaction status, and payment method.
     *
     * @param restaurantId     the restaurant ID to get order history for
     * @param page             page number (1-based)
     * @param size             page size
     * @param orderStatus      optional filter by order status
     * @param orderType        optional filter by order type
     * @param transactionStatus optional filter by transaction status
     * @param paymentMethod    optional filter by payment method
     * @param sortBy           field to sort by
     * @param direction        sort direction (ASC or DESC)
     * @return {@link ResponseDto} containing paginated list of order history
     */
    @Override
    @Transactional(readOnly = true)
    public ResponseDto<OrderHistoryListDto> getOrderHistory(
            UUID restaurantId,
            Integer page,
            Integer size,
            String orderStatus,
            String orderType,
            String transactionStatus,
            String paymentMethod,
            String sortBy,
            String direction,
            String search,
            String sectionId,
            LocalDate date,
            LocalDateTime startDate,
            LocalDateTime endDate,
            Boolean hasFeedback,
            String locale) {
        
        log.info("Getting order history for restaurant: {} (page: {}, size: {}, sortBy: {}, direction: {}, " +
                "orderStatus: {}, orderType: {}, transactionStatus: {}, paymentMethod: {}, search: {}, sectionId: {}, date: {}, startDate: {}, endDate: {}, hasFeedback: {})", 
                restaurantId, page, size, sortBy, direction, orderStatus, orderType, transactionStatus, paymentMethod, search, sectionId, date, startDate, endDate, hasFeedback);

        Locale userLocale = LocaleContextHolder.getLocale();

        Restaurant restaurant = restaurantRepository.findById(restaurantId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        messageUtil.getMessage("restaurant.not.found", userLocale)));

        Collection<OrderStatus> orderStatuses = null;
        if (orderStatus != null && !orderStatus.isBlank()) {
            try {
                orderStatuses = Arrays.stream(orderStatus.split(","))
                        .map(String::trim)
                        .filter(s -> !s.isBlank())
                        .map(s -> OrderStatus.valueOf(s.toUpperCase()))
                        .collect(Collectors.toSet());
                if (orderStatuses.isEmpty()) orderStatuses = null;
            } catch (IllegalArgumentException e) {
                String errorMessage = messageUtil.getMessage("error.invalid.orderStatus", userLocale, orderStatus);
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, errorMessage);
            }
        }
        
        Collection<OrderType> orderTypes = null;
        if (orderType != null && !orderType.isBlank()) {
            try {
                orderTypes = Arrays.stream(orderType.split(","))
                        .map(String::trim)
                        .filter(s -> !s.isBlank())
                        .map(s -> OrderType.valueOf(s.toUpperCase()))
                        .collect(Collectors.toSet());
                if (orderTypes.isEmpty()) orderTypes = null;
            } catch (IllegalArgumentException e) {
                String errorMessage = messageUtil.getMessage("error.invalid.orderType", userLocale, orderType);
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, errorMessage);
            }
        }
        
        Collection<TransactionStatus> transactionStatuses = null;
        if (transactionStatus != null && !transactionStatus.isBlank()) {
            try {
                transactionStatuses = Arrays.stream(transactionStatus.split(","))
                        .map(String::trim)
                        .filter(s -> !s.isBlank())
                        .map(s -> TransactionStatus.valueOf(s.toUpperCase()))
                        .collect(Collectors.toSet());
                if (transactionStatuses.isEmpty()) transactionStatuses = null;
            } catch (IllegalArgumentException e) {
                String errorMessage = messageUtil.getMessage("error.invalid.transactionStatus", userLocale, transactionStatus);
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, errorMessage);
            }
        }
        
        Collection<String> paymentMethods = null;
        if (paymentMethod != null && !paymentMethod.isBlank()) {
            paymentMethods = Arrays.stream(paymentMethod.split(","))
                    .map(String::trim)
                    .filter(s -> !s.isBlank())
                    .collect(Collectors.toSet());
            if (paymentMethods.isEmpty()) paymentMethods = null;
        }

        UUID parsedSectionId = null;
        if (sectionId != null && !sectionId.isBlank()) {
            try {
                parsedSectionId = UUID.fromString(sectionId);
            } catch (IllegalArgumentException e) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid sectionId format: " + sectionId);
            }
        }

        String searchTerm = (search != null && !search.isBlank()) ? search.trim() : null;
        String likePatternLower = (searchTerm == null ? null : "%" + searchTerm.toLowerCase() + "%");

        OffsetDateTime startDateTime;
        OffsetDateTime endDateTime;
        
        if (date != null) {
            // If 'date' is provided, filter for that entire day
            startDateTime = date.atStartOfDay().atOffset(ZoneOffset.UTC); // 00:00:00 UTC
            endDateTime = date.atTime(23, 59, 59, 999999000).atOffset(ZoneOffset.UTC); // 23:59:59.999999 UTC
        } else {
            // Otherwise, use startDate and endDate if provided
            // If null, use reasonable min/max dates (PostgreSQL timestamp range: 4713 BC to 294276 AD)
            // Using 1900-01-01 and 2100-12-31 as safe bounds
            if (startDate != null) {
                // Normalize startDate to start of day for inclusive filtering
                startDateTime = startDate.toLocalDate().atStartOfDay().atOffset(ZoneOffset.UTC);
            } else {
                startDateTime = LocalDateTime.of(1900, 1, 1, 0, 0, 0).atOffset(ZoneOffset.UTC);
            }
            if (endDate != null) {
                // If endDate is provided, set it to end of day for inclusive filtering
                endDateTime = endDate.toLocalDate().atTime(23, 59, 59, 999999000).atOffset(ZoneOffset.UTC);
            } else {
                // Use a far future date within PostgreSQL's valid range
                endDateTime = LocalDateTime.of(2100, 12, 31, 23, 59, 59, 999999000).atOffset(ZoneOffset.UTC);
            }
        }
        
        // Pagination - support both paged and unpaged requests
        boolean noPaging = (page == null || size == null || page <= 0 || size <= 0);
        Pageable pageable;
        
        String dbSortField = sortBy;
        if (sortBy != null && !sortBy.isBlank()) {
            if ("orderDateTime".equalsIgnoreCase(sortBy)) {
                dbSortField = SORT_FIELD_CREATED_AT; // Map orderDateTime to Order.createdAt
            }
        } else {
            dbSortField = SORT_FIELD_CREATED_AT; // Default sort by order creation time
        }
        
        // Determine sort direction
        if (direction == null || direction.isBlank()) {
            direction = "DESC";
        }
        Sort.Direction sortDirection = 
            direction.equalsIgnoreCase("ASC") ? 
            Sort.Direction.ASC : 
            Sort.Direction.DESC;
        Sort sort = Sort.by(sortDirection, dbSortField);
        
        if (!noPaging) {
            pageable = PageRequest.of(page - 1, size, sort);
        } else {
            // Even when no pagination, apply sorting by using a large page size
            pageable = PageRequest.of(0, Integer.MAX_VALUE, sort);
        }

        Page<Order> ordersPage = orderRepository.findByRestaurantIdWithFilters(
                restaurantId, orderStatuses, orderTypes, transactionStatuses, paymentMethods, 
                parsedSectionId, likePatternLower, startDateTime, endDateTime,
                Boolean.TRUE.equals(hasFeedback), pageable);

        String currency = restaurantChainConfigProperties.getChain() != null 
                ? restaurantChainConfigProperties.getChain().getCurrency() : null;

        List<OrderHistoryResponse> orders = ordersPage.getContent().stream()
                .map(order -> convertToOrderHistoryResponse(order, currency))
                .collect(Collectors.toList());

        OrderHistoryListDto dto = OrderHistoryListDto.builder()
                .orders(orders)
                .count((long) orders.size())
                .total(ordersPage.getTotalElements())
                .metaData(noPaging ? null : PaginationMetaData.builder()
                        .page(page)
                        .size(size)
                        .totalPages(ordersPage.getTotalPages())
                        .totalRecords(ordersPage.getTotalElements())
                        .build())
                .build();

        return ResponseDto.<OrderHistoryListDto>builder()
                .message(messageUtil.getMessage("order.history.list.success", userLocale))
                .data(dto)
                .build();
    }

    /**
     * Converts an order entity to an order history response DTO.
     * Includes order details, transaction information, and formatted amounts.
     *
     * @param order    the order entity to convert
     * @param currency the currency code for formatting amounts
     * @return {@link OrderHistoryResponse} with order and transaction details
     */
    private OrderHistoryResponse convertToOrderHistoryResponse(Order order, String currency) {
        Transaction transaction = null;
        try {
            transaction = transactionRepository.findByOrderId(order.getId()).orElse(null);
        } catch (Exception e) {
            log.debug("No transaction found for order: {}", order.getId());
        }
        
        String orderBy = "Customer";
        if (order.getCreatedBy() != null) {
            String firstName = order.getCreatedBy().getFirstName() != null 
                    ? order.getCreatedBy().getFirstName() : "";
            String lastName = order.getCreatedBy().getLastName() != null 
                    ? order.getCreatedBy().getLastName() : "";
            String fullName = (firstName + " " + lastName).trim();
            if (!fullName.isEmpty()) {
                orderBy = fullName;
            }
        }
        
        String tableCode = null;
        Integer tableOrder = null;
        Integer rowOrder = null;
        UUID sectionId = null;
        String sectionName = null;
        
        if (order.getRestaurantTable() != null) {
            tableCode = order.getRestaurantTable().getTableCode();
            tableOrder = order.getRestaurantTable().getTableOrder();
            if (order.getRestaurantTable().getRestaurantRow() != null) {
                rowOrder = order.getRestaurantTable().getRestaurantRow().getRowOrder();
                if (order.getRestaurantTable().getRestaurantRow().getRestaurantSection() != null) {
                    RestaurantSection section = order.getRestaurantTable()
                            .getRestaurantRow().getRestaurantSection();
                    sectionId = section.getId();
                    
                    Locale userLocale = LocaleContextHolder.getLocale();
                    sectionName = section.getTranslations().stream()
                            .filter(t -> userLocale.getLanguage().equalsIgnoreCase(t.getLanguageCode()))
                            .map(RestaurantSectionTranslation::getName)
                            .findFirst()
                            .orElse(section.getTranslations().isEmpty() ? "" 
                                    : section.getTranslations().get(0).getName());
                }
            }
        }
        
        // Calculate session start and end times
        OffsetDateTime startTime = null;
        OffsetDateTime endTime = null;
        
        if (order.getSession() != null) {
            Session session = order.getSession();
            // Start time is the session issuedAt
            startTime = session.getIssuedAt();
            
            // End time: get all orders for this session and calculate end time
            try {
                List<Order> sessionOrders = orderRepository.findBySessionIdOrderByCreatedAtDesc(session.getId());
                endTime = resolveSessionEndTime(sessionOrders);
            } catch (Exception e) {
                log.debug("Error calculating session end time for order {}: {}", order.getId(), e.getMessage());
            }
        }
        
        // Get refund ID only if transaction exists and status is REFUNDED or PARTIALLY_REFUNDED
        UUID refundId = null;
        if (transaction != null) {
            TransactionStatus transactionStatus = transaction.getTransactionStatus();
            if (transactionStatus == TransactionStatus.REFUNDED || transactionStatus == TransactionStatus.PARTIALLY_REFUNDED) {
                try {
                    refundId = refundRepository.findByTransactionId(transaction.getId())
                            .map(Refund::getId)
                            .orElse(null);
                } catch (Exception e) {
                    log.debug(LOG_ERROR_FETCHING_REFUND_FOR_TRANSACTION, transaction.getId(), e.getMessage());
                }
            }
        }
        
        return OrderHistoryResponse.builder()
                .orderId(order.getId())
                .orderNumber(order.getOrderNumber())
                .orderStatus(order.getOrderStatus())
                .orderType(order.getOrderType())
                .orderBy(orderBy)
                .totalAmount(order.getTotalAmount() != null 
                        ? CurrencyFormatter.formatAmount(order.getTotalAmount(), currency) : null)
                .tableId(order.getRestaurantTable() != null ? order.getRestaurantTable().getId() : null)
                .tableCode(tableCode)
                .tableOrder(tableOrder)
                .rowOrder(rowOrder)
                .sectionId(sectionId)
                .sectionName(sectionName)
                .transactionId(transaction != null ? transaction.getId() : null)
                .transactionStatus(transaction != null ? transaction.getTransactionStatus() : null)
                .transactionNumber(transaction != null ? transaction.getTransactionNumber() : null)
                .paymentMethod(transaction != null ? transaction.getPaymentMethod() : null)
                .paymentApp(transaction != null ? transaction.getPaymentApp() : null)
                .sessionId(order.getSession() != null ? order.getSession().getId() : null)
                .orderDateTime(order.getCreatedAt() != null ? order.getCreatedAt().toLocalDateTime() : null)
                .startTime(startTime)
                .endTime(endTime)
                .refundId(refundId)
                .build();
    }

    /**
     * Determine session end time based on order completion.
     * Returns the most recent timestamp (updatedAt, falling back to createdAt) of any order
     * whose status is SERVED or CANCELED and whose items are all in a final state.
     */
    private OffsetDateTime resolveSessionEndTime(List<Order> sessionOrders) {
        if (sessionOrders == null || sessionOrders.isEmpty()) {
            return null;
        }

        OffsetDateTime endTime = null;
        for (Order order : sessionOrders) {
            boolean skip = order == null || order.getOrderStatus() == null;
            OrderStatus status = null;
            if (!skip) {
                status = order.getOrderStatus();
                if (status != OrderStatus.SERVED && status != OrderStatus.CANCELED) {
                    skip = true;
                }
            }

            boolean allItemsCompleted = true;
            if (!skip && order.getOrderedItems() != null && !order.getOrderedItems().isEmpty()) {
                allItemsCompleted = order.getOrderedItems().stream()
                        .filter(java.util.Objects::nonNull)
                        .allMatch(oi -> oi.getItemStatus() == ItemStatus.SERVED || oi.getItemStatus() == ItemStatus.CANCELED);
            }

            if (!skip && !allItemsCompleted) {
                skip = true;
            }

            OffsetDateTime candidate = null;
            if (!skip) {
                candidate = order.getUpdatedAt() != null ? order.getUpdatedAt() : order.getCreatedAt();
                if (candidate == null) {
                    skip = true;
                }
            }

            if (skip) {
                continue;
            }

            if (endTime == null || candidate.isAfter(endTime)) {
                endTime = candidate;
            }
        }

        return endTime;
    }

    /**
     * Filter out cancelled combos from the request list.
     * For combos with orderedComboId, check their status from database.
     * New combos (without orderedComboId) are included as they don't have a status yet.
     * 
     * @param orderedCombos List of ordered combo requests
     * @param userLocale User locale for error messages
     * @return List of active (non-cancelled) ordered combos
     */
    private List<OrderedComboRequest> filterOutCancelledCombos(List<OrderedComboRequest> orderedCombos, Locale userLocale) {
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

    /**
     * Sends notification to managers about an item cancellation request.
     * Finds managers for the restaurant and sends FCM notifications.
     *
     * @param orderedItem the ordered item with cancellation request
     * @param userLocale  locale for localized notification messages
     */
    private void notifyManagersAboutCancellationRequest(OrderedItem orderedItem, Locale userLocale) {
        try {
            UUID restaurantId = orderNotificationService.getRestaurantIdFromOrder(orderedItem.getOrder());
            UUID managerRoleId = roleRepository.findByName(ROLE_MANAGER).map(r -> r.getId()).orElse(null);
            if (managerRoleId != null) {
                List<User> managers = userRepository.findAllByRestaurantIdAndRoleIdAndIsDeletedFalse(restaurantId, managerRoleId);
                if (!managers.isEmpty()) {
                    CompletableFuture.runAsync(() -> {
                        try {
                            notificationService.notifyCancellationRequestOpened(orderedItem, managers, orderedItem.getCancellationRequestedBy(), userLocale);
                        } catch (Exception e) {
                            log.error("Failed to send manager notification for item cancellation request asynchronously: {}", e.getMessage(), e);
                        }
                    });
                }
            }
        } catch (Exception e) {
            log.error("Failed to prepare manager notification for item cancellation request: {}", e.getMessage(), e);
        }
    }

    private void notifyKDSAboutServedItem(OrderedItem orderedItem, Locale userLocale) {
        CompletableFuture.runAsync(() -> {
            try {
                notificationService.notifyItemServed(orderedItem, userLocale);
            } catch (Exception e) {
                log.error("Failed to send item served notification to KDS users asynchronously: {}", e.getMessage(), e);
            }
        });
    }

    /**
     * Sends notification to managers about a combo cancellation request.
     * Finds managers for the restaurant and sends FCM notifications.
     *
     * @param orderedCombo the ordered combo with cancellation request
     * @param userLocale  locale for localized notification messages
     */
    private void notifyManagersAboutComboCancellationRequest(OrderedCombo orderedCombo, Locale userLocale) {
        try {
            UUID restaurantId = orderNotificationService.getRestaurantIdFromOrder(orderedCombo.getOrder());
            UUID managerRoleId = roleRepository.findByName(ROLE_MANAGER).map(r -> r.getId()).orElse(null);
            if (managerRoleId != null) {
                List<User> managers = userRepository.findAllByRestaurantIdAndRoleIdAndIsDeletedFalse(restaurantId, managerRoleId);
                if (!managers.isEmpty()) {
                    CompletableFuture.runAsync(() -> {
                        try {
                            notificationService.notifyComboCancellationRequestOpened(orderedCombo, managers, orderedCombo.getCancellationRequestedBy(), userLocale);
                        } catch (Exception e) {
                            log.error("Failed to send manager notification for combo cancellation request asynchronously: {}", e.getMessage(), e);
                        }
                    });
                }
            }
        } catch (Exception e) {
            log.error("Failed to prepare manager notification for combo cancellation request: {}", e.getMessage(), e);
        }
    }

    /**
     * Checks if order amount deduction should be skipped for cancellation.
     * Deduction is skipped if:
     * - Order type is TAKEAWAY, OR
     * - Payment type is PREPAID (from chain config)
     * 
     * @param order The order to check
     * @return true if deduction should be skipped, false otherwise
     */
    private boolean shouldSkipDeductionForCancellation(Order order) {
        if (order == null) {
            return false;
        }
        Optional<Transaction> transactionOpt = transactionRepository.findByOrderId(order.getId());
        TransactionStatus linkedStatus = transactionOpt.map(Transaction::getTransactionStatus).orElse(null);
        RestaurantChainConfigProperties.RestaurantChainData chainConfig = restaurantChainConfigProperties.getChain();
        PaymentSystemType chainPaymentType = chainConfig != null ? chainConfig.getPaymentType() : null;
        boolean skip = CancellationAmountPolicy.shouldSkipOrderAmountAdjustmentOnCancellation(
                order.getOrderType(), chainPaymentType, linkedStatus);
        if (skip) {
            log.info("Skipping deduction for order {} - completed transaction and (TAKEAWAY or PREPAID chain)", order.getId());
        }
        return skip;
    }

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
                .filter(i -> i.getBxgyRole() == com.gulfnet.shared_library.enums.BxgyRole.BUY)
                .filter(i -> i.getItemStatus() != ItemStatus.CANCELED)
                .mapToInt(OrderedItem::getQuantity)
                .sum();
        
        int activeGetQty = items.stream()
                .filter(i -> i.getBxgyRole() == com.gulfnet.shared_library.enums.BxgyRole.GET)
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
                    .filter(i -> i.getBxgyRole() == com.gulfnet.shared_library.enums.BxgyRole.GET)
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
                    if (getItem.getItemStatus() != null && getItem.getItemStatus() != ItemStatus.CANCELED) {
                        getItem.setWastageSourceStatus(getItem.getItemStatus());
                    }
                    getItem.setItemStatus(ItemStatus.CANCELED);
                    getItem.setUpdatedAt(OffsetDateTime.now(ZoneOffset.UTC));
                    if (hasUserId && authenticatedUser != null) {
                        getItem.setUpdatedBy(authenticatedUser);
                    }
                    excessQty -= itemQty;
                    
                    // Send KDS notification for cancelled GET item
                    runAfterCommitAsync(() -> notifyKdsItemCancellationNotificationBestEffort(getItem.getId(), userLocale));
                    
                    // Notify waiters about cancelled GET item
                    runAfterCommitAsync(() -> notifyWaitersAboutItemCancellationSafe(getItem.getId(), userLocale));
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
     * Cancels an item without deducting amount from order (for TAKEAWAY or PREPAID orders).
     * Only updates status, sends notifications, and updates combo status if needed.
     * 
     * @param orderedItem The item to cancel
     * @param authenticatedUser The user performing the cancellation
     * @param hasUserId Whether user ID is available
     * @param userLocale Locale for notifications
     */
    private void cancelItemWithoutDeduction(OrderedItem orderedItem, User authenticatedUser, boolean hasUserId, Locale userLocale) {
        UUID itemId = orderedItem.getId();
        UUID orderId = orderedItem.getOrder().getId();
        log.info("Cancelling item {} for order {} without deduction (TAKEAWAY or PREPAID)", itemId, orderId);
        
        // Capture wastage source status
        if (orderedItem.getItemStatus() != null && orderedItem.getItemStatus() != ItemStatus.CANCELED) {
            orderedItem.setWastageSourceStatus(orderedItem.getItemStatus());
        }
        
        // Set item status to cancelled
        orderedItem.setItemStatus(ItemStatus.CANCELED);
        orderedItem.setUpdatedAt(OffsetDateTime.now(ZoneOffset.UTC));
        if (hasUserId && authenticatedUser != null) {
            orderedItem.setUpdatedBy(authenticatedUser);
        }
        orderedItemRepository.save(orderedItem);
        orderedItemRepository.flush();
        
        // Auto-adjust GET items if this is a BUY item in a BXGY discount
        if (orderedItem.getDiscountApplicationId() != null && 
            orderedItem.getBxgyRole() == com.gulfnet.shared_library.enums.BxgyRole.BUY &&
            orderedItem.getDiscountId() != null) {
            log.info("BUY item {} cancelled (no deduction) - adjusting related GET items with discount_application_id: {}", 
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
        
        runAfterCommitAsync(() -> notifyKdsItemCancellationNotificationBestEffort(itemId, userLocale));
        
        // Check if all items in combo are now CANCELED and update combo status if needed
        orderedComboService.checkAndUpdateComboStatusWhenAllItemsCanceled(orderedItem, authenticatedUser, hasUserId, userLocale);
        
        // Notify waiters
        runAfterCommitAsync(() -> notifyWaitersAboutItemCancellationSafe(itemId, userLocale));
    }

    /**
     * Cancels a combo without deducting amount from order (for TAKEAWAY or PREPAID orders).
     * Only updates status and sends notifications.
     * 
     * @param orderedCombo The combo to cancel
     * @param authenticatedUser The user performing the cancellation
     * @param hasUserId Whether user ID is available
     * @param userLocale Locale for notifications
     */
    private void cancelComboWithoutDeduction(OrderedCombo orderedCombo, User authenticatedUser, boolean hasUserId, Locale userLocale) {
        UUID comboId = orderedCombo.getId();
        UUID orderId = orderedCombo.getOrder().getId();
        log.info("Cancelling combo {} for order {} without deduction (TAKEAWAY or PREPAID)", comboId, orderId);
        
        // Capture wastage source status
        if (orderedCombo.getItemStatus() != null && orderedCombo.getItemStatus() != ItemStatus.CANCELED) {
            orderedCombo.setWastageSourceStatus(orderedCombo.getItemStatus());
        }
        
        // Set combo status to cancelled
        orderedCombo.setItemStatus(ItemStatus.CANCELED);
        orderedCombo.setUpdatedAt(OffsetDateTime.now(ZoneOffset.UTC));
        if (hasUserId && authenticatedUser != null) {
            orderedCombo.setUpdatedBy(authenticatedUser);
        }
        orderedComboRepository.save(orderedCombo);
        orderedComboRepository.flush();

        // Also cancel child items belonging to this combo (if not already cancelled)
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
                orderedItemRepository.flush();
            }
        } catch (Exception e) {
            log.warn("Failed to cascade cancellation to combo items for combo {}: {}", orderedCombo.getId(), e.getMessage());
        }

        // Send KDS notification for each combo item
        try {
            List<OrderedItem> comboItems = orderedItemRepository.findByOrderedComboId(orderedCombo.getId());
            for (OrderedItem comboItem : comboItems) {
                if (comboItem.getItem() != null) {
                    notifyItemCanceledBestEffort(comboItem, userLocale, comboItem.getId(), true);
                }
            }
        } catch (Exception e) {
            log.warn("Failed to send KDS notifications for combo {} cancellation: {}", orderedCombo.getId(), e.getMessage());
        }
    }

    /**
     * Cancels the transaction associated with an order if the order status is CANCELED.
     * Only cancels the transaction if it's not already canceled/refunded/partially refunded,
     * and never downgrades a COMPLETED transaction to CANCELED.
     * 
     * @param orderId The ID of the order
     */
    private void cancelTransactionIfOrderCanceled(UUID orderId) {
        if (orderId == null) {
            return;
        }
        
        Optional<Transaction> transactionOpt = transactionRepository.findByOrderId(orderId);
        if (transactionOpt.isPresent()) {
            Transaction transaction = transactionOpt.get();
            TransactionStatus currentStatus = transaction.getTransactionStatus();
            // Only cancel if transaction is not already canceled, refunded, partially refunded, or completed
            if (currentStatus != TransactionStatus.CANCELED &&
                currentStatus != TransactionStatus.REFUNDED &&
                currentStatus != TransactionStatus.PARTIALLY_REFUNDED &&
                currentStatus != TransactionStatus.COMPLETED) {
                OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
                transaction.setTransactionStatus(TransactionStatus.CANCELED);
                transaction.setUpdatedAt(now);
                transactionRepository.save(transaction);
                log.info("Transaction {} canceled automatically due to all items being cancelled in order {}", transaction.getId(), orderId);
            } else if (currentStatus == TransactionStatus.COMPLETED) {
                log.info("Order {} is cancelled but transaction {} is COMPLETED; leaving transaction status unchanged.", orderId, transaction.getId());
            }
        }
    }

    /**
     * Safely notify all assigned waiters with a given notification action.
     * Handles null checks, iteration, per-waiter error handling, and summary logging.
     */
    private void notifyAssignedWaitersSafe(List<User> assignedWaiters, UUID orderId,
            RestaurantTable table, Consumer<User> notificationAction, String notificationType) {
        if (assignedWaiters != null && !assignedWaiters.isEmpty()) {
            for (User assignedWaiter : assignedWaiters) {
                if (assignedWaiter != null) {
                    try {
                        notificationAction.accept(assignedWaiter);
                    } catch (Exception e) {
                        log.error("Failed to send {} notification to waiter {}: {}",
                                notificationType, assignedWaiter.getId(), e.getMessage(), e);
                    }
                }
            }
            log.info("Sent {} notifications to {} waiter(s) for order {} at table {}",
                    notificationType, assignedWaiters.size(), orderId, table.getTableOrder());
        } else {
            log.warn("No waiters assigned to table {} for order {} - skipping {} notification",
                    table.getTableOrder(), orderId, notificationType);
        }
    }

    /**
     * Safely notify waiters about an item cancellation during bulk operations.
     */
    private void notifyWaitersAboutItemCancellationSafe(UUID cancelledItemId, Locale userLocale) {
        try {
            if (cancelledItemId == null) {
                return;
            }
            OrderedItem cancelledItem = orderedItemRepository.findByIdWithWaiterInfo(cancelledItemId).orElse(null);
            if (cancelledItem == null || cancelledItem.getOrder() == null || cancelledItem.getOrder().getRestaurantTable() == null) {
                return;
            }
            List<User> assignedWaiters = orderValidationService.getWaitersForTable(
                    cancelledItem.getOrder().getRestaurantTable());
            notifyAssignedWaitersSafe(assignedWaiters, cancelledItem.getOrder().getId(),
                    cancelledItem.getOrder().getRestaurantTable(),
                    waiter -> notificationService.notifyItemCancelledForWaiter(cancelledItem, waiter, userLocale),
                    "item cancellation");
        } catch (Exception e) {
            log.error("Failed to notify waiters for item {} during item cancellation: {}",
                    cancelledItemId, e.getMessage(), e);
        }
    }

    /**
     * Safely send KDS notification for an item cancellation.
     */
    private void sendKdsItemCancellationNotificationSafe(UUID cancelledItemId, Locale userLocale) {
        try {
            notifyKdsItemCancellationNotificationBestEffort(cancelledItemId, userLocale);
        } catch (Exception e) {
            log.error("Failed to send KDS notification for item cancellation {}: {}",
                    cancelledItemId, e.getMessage(), e);
        }
    }

    private void notifyKdsItemCancellationNotificationBestEffort(UUID cancelledItemId, Locale userLocale) {
        if (cancelledItemId == null) {
            return;
        }
        OrderedItem cancelledItem = orderedItemRepository.findByIdWithWaiterInfo(cancelledItemId).orElse(null);
        if (cancelledItem == null) {
            return;
        }
        try {
            notificationService.notifyItemCanceled(cancelledItem, java.util.Collections.emptyList(), userLocale);
            log.info("Sent KDS notification for item cancellation: {} in order {}",
                    cancelledItem.getId(),
                    cancelledItem.getOrder() != null ? cancelledItem.getOrder().getId() : "unknown");
        } catch (Exception e) {
            log.error("Failed to send KDS notification for item cancellation {}: {}",
                    cancelledItemId, e.getMessage(), e);
        }
    }

    private void runAfterCommitAsync(Runnable task) {
        if (task == null) {
            return;
        }
        Runnable safeTask = () -> {
            try {
                task.run();
            } catch (Exception e) {
                log.error("After-commit task failed: {}", e.getMessage(), e);
            }
        };

        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    CompletableFuture.runAsync(safeTask, notificationTaskExecutor);
                }
            });
        } else {
            CompletableFuture.runAsync(safeTask, notificationTaskExecutor);
        }
    }

    /**
     * Safely create a cancellation audit trail entry.
     */
    private void createCancellationAuditTrailSafe(User authenticatedUser, Restaurant restaurant,
            UUID entityId, String entityType, String description) {
        try {
            auditTrailService.createAuditTrail(
                    authenticatedUser,
                    ActionType.CANCELLATION,
                    restaurant,
                    RequestStatus.OPEN,
                    null, // ipAddress
                    null, // userAgent
                    entityId,
                    entityType,
                    description
            );
        } catch (Exception e) {
            log.error("Failed to create cancellation audit trail for {} {}: {}", entityType, entityId, e.getMessage(), e);
        }
    }

    /**
     * Safely notify managers about a combo cancellation request.
     */
    private void notifyManagersAboutComboCancellationSafe(OrderedCombo orderedCombo, UUID orderedComboId, Locale userLocale) {
        try {
            UUID restaurantId = orderNotificationService.getRestaurantIdFromOrder(orderedCombo.getOrder());
            UUID managerRoleId = roleRepository.findByName(ROLE_MANAGER).map(r -> r.getId()).orElse(null);
            if (managerRoleId != null) {
                List<User> managers = userRepository.findAllByRestaurantIdAndRoleIdAndIsDeletedFalse(restaurantId, managerRoleId);
                if (!managers.isEmpty()) {
                    CompletableFuture.runAsync(() -> {
                        try {
                            notificationService.notifyComboCancellationRequestOpened(orderedCombo, managers, orderedCombo.getCancellationRequestedBy(), userLocale);
                            log.info("Combo cancellation request created for combo {} - managers notified asynchronously", orderedComboId);
                        } catch (Exception e) {
                            log.error("Failed to send manager notification for combo cancellation request asynchronously: {}", e.getMessage(), e);
                        }
                    });
                }
            }
        } catch (Exception e) {
            log.error("Failed to prepare manager notification for combo cancellation request: {}", e.getMessage(), e);
        }
    }

    /**
     * Safely notify managers about an order cancellation request.
     */
    private void notifyManagersAboutOrderCancellationSafe(Order order, Locale userLocale) {
        try {
            UUID restaurantId = orderNotificationService.getRestaurantIdFromOrder(order);
            Optional<Role> managerRoleOpt = roleRepository.findByName(ROLE_MANAGER);
            if (managerRoleOpt.isPresent()) {
                UUID managerRoleId = managerRoleOpt.get().getId();
                List<User> managers = userRepository.findAllByRestaurantIdAndRoleIdAndIsDeletedFalse(restaurantId, managerRoleId);
                if (!managers.isEmpty()) {
                    notificationService.notifyOrderCancellationRequestOpened(order, managers, order.getCancellationRequestedBy(), userLocale);
                }
            }
        } catch (Exception e) {
            log.error("Failed to send manager notification for order cancellation request: {}", e.getMessage(), e);
        }
    }

}
