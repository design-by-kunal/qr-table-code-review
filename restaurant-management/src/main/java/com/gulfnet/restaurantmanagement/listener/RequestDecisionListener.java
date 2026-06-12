package com.gulfnet.restaurantmanagement.listener;

import com.gulfnet.restaurantmanagement.service.NotificationService;
import com.gulfnet.restaurantmanagement.service.AuditTrailService;
import com.gulfnet.restaurantmanagement.service.RestaurantAlertEvaluationService;
import com.gulfnet.shared_library.entity.AuditTrail;
import com.gulfnet.shared_library.entity.Order;
import com.gulfnet.shared_library.entity.OrderedCombo;
import com.gulfnet.shared_library.entity.OrderedItem;
import com.gulfnet.shared_library.entity.TableAssignment;
import com.gulfnet.shared_library.entity.Transaction;
import com.gulfnet.shared_library.entity.User;
import com.gulfnet.shared_library.entity.Restaurant;
import com.gulfnet.shared_library.enums.ActionType;
import com.gulfnet.shared_library.enums.RequestStatus;
import com.gulfnet.shared_library.enums.EntityStatus;
import com.gulfnet.shared_library.repository.OrderRepository;
import com.gulfnet.shared_library.repository.OrderedComboRepository;
import com.gulfnet.shared_library.repository.OrderedItemRepository;
import com.gulfnet.shared_library.repository.TableAssignmentRepository;
import com.gulfnet.shared_library.repository.TransactionRepository;
import com.gulfnet.shared_library.repository.UserRepository;
import com.gulfnet.shared_library.repository.RestaurantRepository;
import com.gulfnet.shared_library.repository.RoleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Unified RabbitMQ listener to consume request decision messages from user-management
 * and send WebSocket notifications to cashiers/users
 * 
 * Supports multiple request types:
 * - DISCOUNT_REQUEST: Additional discount requests
 * - CANCELLATION_REQUEST: Transaction cancellation requests
 * - REFUND_REQUEST: Transaction refund requests
 * - PROFILE_UPDATE_REQUEST: Profile update requests
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RequestDecisionListener {

    // Message field keys
    private static final String FIELD_REQUEST_TYPE = "requestType";
    private static final String FIELD_IS_APPROVED = "isApproved";
    private static final String FIELD_COMMENTS = "comments";
    private static final String FIELD_LOCALE = "locale";
    private static final String FIELD_ORDER_ID = "orderId";
    private static final String FIELD_CASHIER_ID = "cashierId";
    private static final String FIELD_TRANSACTION_ID = "transactionId";
    private static final String FIELD_USER_ID = "userId";
    private static final String FIELD_REQUESTER_ID = "requesterId";
    private static final String FIELD_UPDATER_ID = "updaterId";
    private static final String FIELD_EMPLOYEE_ID = "employeeId";
    private static final String FIELD_RESTAURANT_ID = "restaurantId";
    private static final String FIELD_MANAGER_ID = "managerId";
    private static final String FIELD_ORDERED_ITEM_ID = "orderedItemId";
    private static final String FIELD_ORDERED_COMBO_ID = "orderedComboId";
    private static final String FIELD_TYPE = "type";

    // Default values
    private static final String DEFAULT_LOCALE = "en";
    private static final String DEFAULT_COMMENT = "N/A";
    private static final String STRING_NULL = "null";

    // Status strings
    private static final String STATUS_APPROVED = "approved";
    private static final String STATUS_DECLINED = "declined";

    // Entity types
    private static final String ENTITY_TYPE_TRANSACTION = "TRANSACTION";
    private static final String ENTITY_TYPE_ITEM = "ITEM";

    // Role names
    private static final String ROLE_MANAGER = "MANAGER";

    // Request types
    private static final String REQUEST_TYPE_DISCOUNT_REQUEST = "DISCOUNT_REQUEST";
    private static final String LEGACY_TYPE_DISCOUNT_REQUEST_DECISION = "discount_request_decision";
    private static final String FIELD_APPROVED_LEGACY = "approved";

    // SQL parameter names
    // (moved to RequestDecisionAuditTrailUpdater)
    
    // Error message constants
    private static final String ERROR_MESSAGE_CAUSED_BY = "Message that caused the error: {}";

    private final NotificationService notificationService;
    private final OrderRepository orderRepository;
    private final OrderedComboRepository orderedComboRepository;
    private final OrderedItemRepository orderedItemRepository;
    private final TransactionRepository transactionRepository;
    private final UserRepository userRepository;
    private final TableAssignmentRepository tableAssignmentRepository;
    private final RestaurantRepository restaurantRepository;
    private final RoleRepository roleRepository;
    private final AuditTrailService auditTrailService;
    private final RestaurantAlertEvaluationService restaurantAlertEvaluationService;
    private final RequestDecisionAuditTrailUpdater requestDecisionAuditTrailUpdater;

    @PersistenceContext
    private EntityManager entityManager;

    /**
     * Listen to unified request decision queue from user-management
     * Routes to appropriate notification method based on requestType
     */
    /**
     * Important: do not wrap the entire listener in a DB transaction.
     * Listener processing may perform external I/O (WebSocket/Rabbit/FCM) which must not hold a JDBC connection open.
     */
    @RabbitListener(queues = "request.decision.queue")
    public void handleRequestDecision(Map<String, Object> message) {
        try {
            log.info("Received request decision message from RabbitMQ: {}", message);
            
            // Extract common message data
            String requestType = (String) message.get(FIELD_REQUEST_TYPE);
            Boolean isApproved = parseIsApproved(message);
            String comments = (String) message.get(FIELD_COMMENTS);
            String localeStr = (String) message.getOrDefault(FIELD_LOCALE, DEFAULT_LOCALE);
            
            if (requestType == null || isApproved == null) {
                log.warn("Invalid request decision message: missing requestType or isApproved. keys={}, isApprovedRaw={}, requestTypeRaw={}",
                        message != null ? message.keySet() : null,
                        message != null ? message.get(FIELD_IS_APPROVED) : null,
                        message != null ? message.get(FIELD_REQUEST_TYPE) : null);
                return;
            }
            
            Locale locale = localeFromMessageTag(localeStr);
            
            log.info("[CASHIER-NOTIFY] restaurant-management: request.decision.queue received requestType={} approved={} transactionId={} cashierId={} orderId={}",
                    requestType, isApproved, message.get(FIELD_TRANSACTION_ID), message.get(FIELD_CASHIER_ID), message.get(FIELD_ORDER_ID));
            
            // Route to appropriate handler based on request type
            routeToHandler(requestType, message, isApproved, comments, locale);
            
        } catch (Exception e) {
            log.error("Failed to process request decision message: {}", e.getMessage(), e);
            // Let the listener container handle retry / DLQ.
            throw e;
        }
    }

    /**
     * Listen to legacy discount request decision queue (for backward compatibility)
     * This handles the old format where requestType is inferred from message structure
     */
    @RabbitListener(queues = "discount.request.decision.queue")
    public void handleLegacyDiscountRequestDecision(Map<String, Object> message) {
        try {
            log.info("Received legacy discount request decision message from RabbitMQ: {}", message);
            
            // Check if this is the old format (has "type" field) or new format (has "requestType")
            String requestType = (String) message.get(FIELD_REQUEST_TYPE);
            if (requestType == null) {
                // Old format - infer from message structure
                String type = (String) message.get(FIELD_TYPE);
                if (LEGACY_TYPE_DISCOUNT_REQUEST_DECISION.equals(type) || message.containsKey(FIELD_ORDER_ID)) {
                    requestType = REQUEST_TYPE_DISCOUNT_REQUEST;
                }
            }
            
            Boolean isApproved = parseIsApproved(message);
            String comments = (String) message.get(FIELD_COMMENTS);
            String localeStr = (String) message.getOrDefault(FIELD_LOCALE, DEFAULT_LOCALE);
            
            if (requestType == null || isApproved == null) {
                log.warn("Invalid legacy discount request decision message: missing required fields");
                return;
            }
            
            Locale locale = localeFromMessageTag(localeStr);
            routeToHandler(requestType, message, isApproved, comments, locale);
            
        } catch (Exception e) {
            log.error("Failed to process legacy discount request decision message: {}", e.getMessage(), e);
            throw e;
        }
    }

    /**
     * Route message to appropriate handler based on request type
     */
    private void routeToHandler(String requestType, Map<String, Object> message, 
                                Boolean isApproved, String comments, Locale locale) {
        switch (requestType.toUpperCase()) {
            case REQUEST_TYPE_DISCOUNT_REQUEST:
            case "ADDITIONAL_DISCOUNT_REQUEST":
                handleDiscountRequestDecision(message, isApproved, comments, locale);
                break;
                
            case "CANCELLATION_REQUEST":
            case "TRANSACTION_CANCELLATION_REQUEST":
                handleCancellationRequestDecision(message, isApproved, comments, locale);
                break;
                
            case "REFUND_REQUEST":
            case "TRANSACTION_REFUND_REQUEST":
                handleRefundRequestDecision(message, isApproved, comments, locale);
                break;
                
            case "PROFILE_UPDATE_REQUEST":
                handleProfileUpdateRequestDecision(message, isApproved, comments, locale);
                break;
                
            case "ITEM_CANCELLATION_REQUEST":
                handleItemCancellationRequestDecision(message, isApproved, comments, locale);
                break;

            case "COMBO_CANCELLATION_REQUEST":
                handleComboCancellationRequestDecision(message, isApproved, comments, locale);
                break;
                
            case "ORDER_CANCELLATION_REQUEST":
                handleOrderCancellationRequestDecision(message, isApproved, comments, locale);
                break;
                
            default:
                log.warn("Unknown request type: {}", requestType);
        }
    }

    /**
     * Listen to profile updated directly queue from user-management
     * This handles notifications when manager or HQ updates employee profile without request
     */
    @RabbitListener(queues = "profile.updated.directly.queue")
    public void handleProfileUpdatedDirectly(Map<String, Object> message) {
        try {
            log.info("Received profile updated directly message from RabbitMQ: {}", message);
            
            String userIdStr = (String) message.get(FIELD_USER_ID);
            String updaterIdStr = (String) message.get(FIELD_UPDATER_ID);
            String localeStr = (String) message.getOrDefault(FIELD_LOCALE, DEFAULT_LOCALE);
            
            if (userIdStr == null) {
                log.warn("Invalid profile updated directly message: missing userId. Message: {}", message);
                return;
            }
            
            UUID userId = UUID.fromString(userIdStr);
            Locale locale = Locale.forLanguageTag(localeStr);
            
            log.info("Looking up employee {} and updater {} for profile updated directly notification", 
                    userId, updaterIdStr);
            
            Optional<User> employeeOpt = userRepository.findById(userId);
            if (employeeOpt.isEmpty()) {
                log.error("Employee not found for ID: {}. Cannot process profile updated directly notification.", userId);
                return;
            }
            
            User employee = employeeOpt.get();
            
            // Get updater if available
            User updater = resolveUpdater(updaterIdStr);
            
            log.info("Calling notifyProfileUpdatedDirectly - Employee: {}, Updater: {}", 
                    employee.getId(), updater != null ? updater.getId() : STRING_NULL);
            
            notificationService.notifyProfileUpdatedDirectly(employee, updater, locale);
            
            log.info("Successfully processed profile updated directly notification - Employee: {}. Notification should be saved to database.", 
                    employee.getId());
                    
        } catch (Exception e) {
            log.error("Failed to handle profile updated directly message: {}", e.getMessage(), e);
            log.error(ERROR_MESSAGE_CAUSED_BY, message, e);
        }
    }

    /**
     * Listen to employee assigned to restaurant queue from user-management.
     * This handles notifications when HQ Admin creates a new employee and directly
     * assigns them to a restaurant on the registration page.
     */
    @RabbitListener(queues = "employee.assigned.to.restaurant.queue")
    public void handleEmployeeAssignedToRestaurant(Map<String, Object> message) {
        try {
            log.info("Received employee assigned to restaurant message from RabbitMQ: {}", message);

            String employeeIdStr = (String) message.get(FIELD_EMPLOYEE_ID);
            String restaurantIdStr = (String) message.get(FIELD_RESTAURANT_ID);
            String localeStr = (String) message.getOrDefault(FIELD_LOCALE, DEFAULT_LOCALE);

            if (employeeIdStr == null || restaurantIdStr == null) {
                log.warn("Invalid employee assigned message: missing employeeId or restaurantId. Message: {}", message);
                return;
            }

            UUID employeeId = UUID.fromString(employeeIdStr);
            UUID restaurantId = UUID.fromString(restaurantIdStr);
            Locale locale = Locale.forLanguageTag(localeStr);

            log.info("Looking up employee {} and restaurant {} for employee assigned notification", 
                    employeeId, restaurantId);

            Optional<User> employeeOpt = userRepository.findById(employeeId);
            if (employeeOpt.isEmpty()) {
                log.error("Employee not found for ID: {}. Cannot process employee assigned notification.", employeeId);
                return;
            }

            Optional<Restaurant> restaurantOpt = restaurantRepository.findById(restaurantId);
            if (restaurantOpt.isEmpty()) {
                log.error("Restaurant not found for ID: {}. Cannot process employee assigned notification.", restaurantId);
                return;
            }

            User employee = employeeOpt.get();
            Restaurant restaurant = restaurantOpt.get();

            // Find MANAGER role
            Optional<com.gulfnet.shared_library.entity.Role> managerRoleOpt = roleRepository.findByName(ROLE_MANAGER);
            if (managerRoleOpt.isEmpty()) {
                log.warn("MANAGER role not found when processing employee assigned notification for employee {}", employeeId);
                return;
            }

            UUID managerRoleId = managerRoleOpt.get().getId();

            // Find active managers for the restaurant
            List<User> managers = userRepository
                    .findAllByRestaurantIdAndRoleIdAndIsDeletedFalse(restaurantId, managerRoleId)
                    .stream()
                    .filter(u -> u.getStatus() == EntityStatus.ACTIVE)
                    .collect(java.util.stream.Collectors.toList());

            if (managers.isEmpty()) {
                log.info("No active managers found for restaurant {} when processing employee assigned notification", restaurantId);
                return;
            }

            log.info("Calling notifyEmployeeAssignedToRestaurant - Employee: {}, Restaurant: {}, Managers: {}", 
                    employee.getId(), restaurant.getId(), managers.size());

            notificationService.notifyEmployeeAssignedToRestaurant(employee, restaurant, managers, locale);

            log.info("Successfully processed employee assigned notification - Employee: {}, Restaurant: {}", 
                    employee.getId(), restaurant.getId());

        } catch (Exception e) {
            log.error("Failed to handle employee assigned to restaurant message: {}", e.getMessage(), e);
            log.error(ERROR_MESSAGE_CAUSED_BY, message, e);
        }
    }

    /**
     * Handle discount request decision
     * Required fields: orderId, cashierId
     */
    private void handleDiscountRequestDecision(Map<String, Object> message, Boolean isApproved, 
                                               String comments, Locale locale) {
        try {
            String orderIdStr = (String) message.get(FIELD_ORDER_ID);
            String cashierIdStr = (String) message.get(FIELD_CASHIER_ID);
            
            if (orderIdStr == null || cashierIdStr == null) {
                log.warn("Invalid discount request decision message: missing orderId or cashierId. keys={}", message != null ? message.keySet() : null);
                return;
            }
            
            UUID orderId = UUID.fromString(orderIdStr);
            UUID cashierId = UUID.fromString(cashierIdStr);
            
            Optional<Order> orderOpt = orderRepository.findById(orderId);
            Optional<User> cashierOpt = userRepository.findById(cashierId);
            
            if (orderOpt.isEmpty()) {
                log.warn("Order not found for ID: {}", orderId);
                return;
            }
            
            if (cashierOpt.isEmpty()) {
                log.warn("Cashier not found for ID: {}", cashierId);
                return;
            }
            
            Order order = orderOpt.get();
            User cashier = cashierOpt.get();
            
            notificationService.notifyDiscountRequestDecision(order, cashier, isApproved, comments, locale);
            
            log.info("Successfully sent WebSocket notification for discount request decision - Order: {}, Cashier: {}, Approved: {}", 
                    order.getOrderNumber(), cashier.getId(), isApproved);

            // Note: Audit trail entry for cashier is already created/updated in UserServiceImpl.approveOrDeclineRequest()
            // when manager approves/rejects the discount request. No need to create duplicate entry here.
                    
        } catch (Exception e) {
            log.error("Failed to handle discount request decision: {}", e.getMessage(), e);
        }
    }

    /**
     * Handle cancellation request decision
     * Required fields: transactionId, cashierId
     */
    private void handleCancellationRequestDecision(Map<String, Object> message, Boolean isApproved,
                                                   String comments, Locale locale) {
        try {
            DecisionContext context = resolveTransactionAndCashier(message, "cancellation");
            if (context == null) {
                log.warn("[CASHIER-NOTIFY] restaurant-management: cancellation handler aborted (resolveTransactionAndCashier returned null)");
                return;
            }

            Transaction transaction = context.transaction();
            User cashier = context.cashier();
            
            notificationService.notifyCancellationRequestDecisionForCashier(transaction, cashier, isApproved, comments, locale);
            
            log.info("Successfully sent WebSocket notification for cancellation request decision - Transaction: {}, Cashier: {}, Approved: {}", 
                    transaction.getId(), cashier.getId(), isApproved);

            // Create audit trail entry for cashier when cancellation request is approved/declined by manager
            createAndUpdateTransactionAuditTrail(cashier, transaction, ActionType.CANCELLATION, isApproved, comments, "cancellation");

            // Notify waiter about transaction cancellation if approved
            if (isApproved != null && isApproved) {
                notifyWaiterSafely(transaction, locale);
            }

            // Trigger cancellation threshold alert evaluation when transaction cancellation is approved.
            // When approval is done from user-management, the bean is not available there; this listener
            // runs in restaurant-management so we run the evaluation here to ensure the notification is sent.
            if (isApproved != null && isApproved) {
                evaluateCancellationThresholdAlertsAfterApproval(transaction, locale);
            }
                    
        } catch (Exception e) {
            log.error("Failed to handle cancellation request decision: {}", e.getMessage(), e);
        }
    }

    /**
     * Runs real-time restaurant alert evaluation after a cancellation request is approved so threshold rules fire
     * in restaurant-management (where this bean exists), including approvals originating from user-management.
     *
     * @param transaction affected transaction
     * @param locale      locale for evaluation messages
     */
    private void evaluateCancellationThresholdAlertsAfterApproval(Transaction transaction, Locale locale) {
        try {
            Restaurant restaurant = transaction.getRestaurant();
            if (restaurant != null && restaurantAlertEvaluationService != null) {
                log.info("Triggering alert evaluation for restaurant {} after transaction cancellation approval (from request decision)",
                        restaurant.getRestaurantCode());
                restaurantAlertEvaluationService.evaluateRestaurantAlertsRealtime(restaurant, locale);
                log.info("Alert evaluation completed for restaurant {} after transaction cancellation approval",
                        restaurant.getRestaurantCode());
            }
        } catch (Exception e) {
            log.error("Failed to evaluate cancellation threshold after transaction cancellation approval: {}", e.getMessage(), e);
        }
    }
    
    /**
     * Notify assigned waiter (if any) that a transaction for their table has been cancelled
     * This method is called when a transaction cancellation is approved
     */
    private void notifyWaiterAboutTransactionCancellation(Transaction transaction, Locale userLocale) {
        try {
            log.info("Attempting to notify waiter about transaction cancellation for transaction: {}", transaction.getId());
            
            Set<User> waiters = findWaitersForTransaction(transaction);
            if (waiters.isEmpty()) {
                return;
            }

            log.info("Notifying {} unique waiter(s) about transaction cancellation for transaction {}", 
                    waiters.size(), transaction.getId());
            sendCancellationNotificationsToWaiters(transaction, waiters, userLocale);
        } catch (Exception e) {
            log.error("Failed to notify waiter about transaction cancellation for transaction {}: {}", 
                    transaction.getId(), e.getMessage(), e);
        }
    }

    /**
     * Find all unique waiters assigned to the table for the given transaction.
     *
     * @param transaction the transaction to find waiters for
     * @return set of unique waiters, or empty set if none found
     */
    private Set<User> findWaitersForTransaction(Transaction transaction) {
        Order order = transaction.getOrder();
        if (order == null || order.getRestaurantTable() == null) {
            log.warn("No order or table associated with transaction {} for waiter notification. Order: {}, Table: {}", 
                    transaction.getId(), order != null ? order.getId() : STRING_NULL, 
                    order != null && order.getRestaurantTable() != null ? order.getRestaurantTable().getId() : STRING_NULL);
            return Collections.emptySet();
        }

        com.gulfnet.shared_library.entity.RestaurantTable table = order.getRestaurantTable();
        log.info("Looking for waiter assignment for table {} (table order: {}) for transaction {}", 
                table.getId(), table.getTableOrder(), transaction.getId());
        
        List<TableAssignment> tableAssignments = tableAssignmentRepository
                .findByRestaurantTableIdAndUnassignedAtIsNullWithWaiter(table.getId());

        if (tableAssignments == null || tableAssignments.isEmpty()) {
            log.warn("No active waiter assignment found for table {} (table order: {}) for transaction {}", 
                    table.getId(), table.getTableOrder(), transaction.getId());
            return Collections.emptySet();
        }

        log.info("Found {} active waiter assignment(s) for table {}", tableAssignments.size(), table.getId());
        return collectUniqueWaiters(tableAssignments, table.getId(), transaction.getId());
    }

    /**
     * Collect unique waiters from table assignments.
     *
     * @param tableAssignments list of table assignments
     * @param tableId table ID for logging
     * @param transactionId transaction ID for logging
     * @return set of unique waiters
     */
    private Set<User> collectUniqueWaiters(List<TableAssignment> tableAssignments, UUID tableId, UUID transactionId) {
        Set<User> uniqueWaiters = new HashSet<>();
        for (TableAssignment assignment : tableAssignments) {
            User waiter = assignment.getWaiter();
            if (waiter != null) {
                uniqueWaiters.add(waiter);
            } else {
                log.warn("Table assignment {} has null waiter for table {} and transaction {}", 
                        assignment.getId(), tableId, transactionId);
            }
        }

        if (uniqueWaiters.isEmpty()) {
            log.warn("No valid waiters found in table assignments for table {} and transaction {}", 
                    tableId, transactionId);
        }
        return uniqueWaiters;
    }

    /**
     * Send cancellation notifications to all waiters.
     *
     * @param transaction the cancelled transaction
     * @param waiters set of waiters to notify
     * @param userLocale locale for notifications
     */
    private void sendCancellationNotificationsToWaiters(Transaction transaction, Set<User> waiters, Locale userLocale) {
        for (User waiter : waiters) {
            try {
                log.info("Sending transaction cancellation notification to waiter {} ({} {}) for transaction {}", 
                        waiter.getId(), waiter.getFirstName(), waiter.getLastName(), transaction.getId());
                notificationService.notifyTransactionCancelledForWaiter(transaction, waiter, userLocale);
                log.info("Successfully sent transaction cancellation notification to waiter {} for transaction {}", 
                        waiter.getId(), transaction.getId());
            } catch (Exception e) {
                log.error("Failed to send transaction cancellation notification to waiter {} for transaction {}: {}", 
                        waiter.getId(), transaction.getId(), e.getMessage(), e);
            }
        }
    }

    /**
     * Create and update audit trail for transaction-related request decisions (cancellation, refund).
     * This method handles both the creation of the audit trail entry and the update with request/review information.
     *
     * @param user the user (cashier) for whom the audit trail is created
     * @param transaction the transaction related to the request
     * @param actionType the action type (CANCELLATION or REFUND)
     * @param isApproved whether the request was approved
     * @param comments comments from the decision
     * @param actionTag used for logging context (e.g. "cancellation", "refund")
     */
    private void createAndUpdateTransactionAuditTrail(User user, Transaction transaction, ActionType actionType,
                                                       Boolean isApproved, String comments, String actionTag) {
        try {
            RequestStatus status = (isApproved != null && isApproved)
                    ? RequestStatus.APPROVED
                    : RequestStatus.DECLINED;
            String auditComments = (comments != null && !comments.trim().isEmpty()) ? comments.trim() : DEFAULT_COMMENT;

            // Determine initiator (createdBy)
            // For cancellation: use transaction.reviewedBy as createdBy when available, otherwise use user
            // For refund: use null (not passed to createAuditTrail)
            User initiator = null;
            if (actionType == ActionType.CANCELLATION) {
                initiator = transaction.getReviewedBy();
                if (initiator == null) {
                    initiator = user;
                }
            }

            // Build audit message based on action type
            String auditMessage;
            if (actionType == ActionType.CANCELLATION) {
                auditMessage = String.format("Transaction cancellation request %s by manager. Comments: %s",
                        (isApproved != null && isApproved) ? STATUS_APPROVED : STATUS_DECLINED,
                        auditComments);
            } else {
                auditMessage = String.format("Refund request %s by manager. Comments: %s",
                        (isApproved != null && isApproved) ? STATUS_APPROVED : STATUS_DECLINED,
                        auditComments);
            }

            AuditTrail auditTrail = auditTrailService.createAuditTrail(
                    user,
                    actionType,
                    transaction.getRestaurant(),
                    status,
                    null,                          // ipAddress
                    null,                          // userAgent
                    transaction.getId(),
                    ENTITY_TYPE_TRANSACTION,
                    auditMessage,
                    null,                          // openingBalance
                    null,                          // closingBalance
                    null,                          // expectedBalance
                    null,                          // discrepancyAmount
                    null,                          // discrepancyReason
                    initiator                      // createdBy (manager as initiator for cancellation, null for refund)
            );

            // Update audit trail with request and review information for QA tracking
            if (auditTrail != null && auditTrail.getId() != null) {
                updateAuditTrailWithRequestInfo(auditTrail, transaction, actionTag);
            }
        } catch (Exception e) {
            log.error("Failed to create audit trail for {} when {} request was approved/declined: {}", 
                    user.getId(), actionTag, e.getMessage(), e);
        }
    }

    /**
     * Handle refund request decision
     * Required fields: transactionId, cashierId
     */
    private void handleRefundRequestDecision(Map<String, Object> message, Boolean isApproved,
                                             String comments, Locale locale) {
        try {
            DecisionContext context = resolveTransactionAndCashier(message, "refund");
            if (context == null) {
                log.warn("[CASHIER-NOTIFY] restaurant-management: refund handler aborted (resolveTransactionAndCashier returned null)");
                return;
            }

            Transaction transaction = context.transaction();
            User cashier = context.cashier();
            
            notificationService.notifyRefundRequestDecision(transaction, cashier, isApproved, comments, locale);
            
            log.info("Successfully sent WebSocket notification for refund request decision - Transaction: {}, Cashier: {}, Approved: {}", 
                    transaction.getId(), cashier.getId(), isApproved);

            // Create audit trail entry for cashier when refund request is approved/declined
            createAndUpdateTransactionAuditTrail(cashier, transaction, ActionType.REFUND, isApproved, comments, "refund");
                    
        } catch (Exception e) {
            log.error("Failed to handle refund request decision: {}", e.getMessage(), e);
        }
    }

    /**
     * Resolve transaction and cashier from a decision message.
     * Shared logic for cancellation and refund request decision handlers.
     *
     * @param message   the incoming message payload
     * @param actionTag used for logging context (e.g. "cancellation", "refund")
     * @return {@link DecisionContext} with transaction and cashier, or {@code null} if resolution failed
     */
    private DecisionContext resolveTransactionAndCashier(Map<String, Object> message, String actionTag) {
        String transactionIdStr = (String) message.get(FIELD_TRANSACTION_ID);
        String cashierIdStr = (String) message.get(FIELD_CASHIER_ID);

        if (transactionIdStr == null || cashierIdStr == null) {
            log.warn("Invalid {} request decision message: missing transactionId or cashierId. keys={}, transactionIdStr={}, cashierIdStr={}",
                    actionTag, message != null ? message.keySet() : null, transactionIdStr, cashierIdStr);
            return null;
        }

        UUID transactionId = UUID.fromString(transactionIdStr);
        UUID cashierId = UUID.fromString(cashierIdStr);

        // Use findByIdWithOrderAndTable to eagerly fetch order and table relationships
        // This prevents LazyInitializationException when accessing transaction.getOrder().getRestaurantTable()
        Optional<Transaction> transactionOpt = transactionRepository.findByIdWithOrderAndTable(transactionId);
        Optional<User> cashierOpt = userRepository.findById(cashierId);

        if (transactionOpt.isEmpty()) {
            log.warn("Transaction not found for ID: {} ({} request decision)", transactionId, actionTag);
            return null;
        }

        if (cashierOpt.isEmpty()) {
            log.warn("Cashier not found for ID: {} ({} request decision)", cashierId, actionTag);
            return null;
        }

        return new DecisionContext(transactionOpt.get(), cashierOpt.get());
    }

    /**
     * Simple holder for transaction and cashier used in request decision handlers.
     */
    private record DecisionContext(Transaction transaction, User cashier) {}

    /**
     * Handle profile update request decision
     * Required fields: userId, requesterId
     */
    private void handleProfileUpdateRequestDecision(Map<String, Object> message, Boolean isApproved, 
                                                    String comments, Locale locale) {
        try {
            log.info("Processing profile update request decision message: {}", message);
            
            String userIdStr = (String) message.get(FIELD_USER_ID);
            String requesterIdStr = (String) message.get(FIELD_REQUESTER_ID);
            
            if (userIdStr == null || requesterIdStr == null) {
                log.warn("Invalid profile update request decision message: missing userId or requesterId. Message: {}", message);
                return;
            }
            
            UUID userId = UUID.fromString(userIdStr);
            UUID requesterId = UUID.fromString(requesterIdStr);
            
            log.info("Looking up user {} and requester {} for profile update request decision", userId, requesterId);
            
            Optional<User> userOpt = userRepository.findById(userId);
            Optional<User> requesterOpt = userRepository.findById(requesterId);
            
            if (userOpt.isEmpty()) {
                log.error("User not found for ID: {}. Cannot process profile update request decision notification.", userId);
                return;
            }
            
            if (requesterOpt.isEmpty()) {
                log.error("Requester not found for ID: {}. Cannot process profile update request decision notification.", requesterId);
                return;
            }
            
            User user = userOpt.get();
            User requester = requesterOpt.get();
            
            log.info("Found user {} and requester {} for profile update request decision. User role: {}, Requester role: {}", 
                    user.getId(), requester.getId(), 
                    user.getRoleId() != null ? user.getRoleId() : STRING_NULL,
                    requester.getRoleId() != null ? requester.getRoleId() : STRING_NULL);
            
            // Get manager ID from message if available (for createdBy in notification)
            String managerIdStr = (String) message.get(FIELD_MANAGER_ID);
            User manager = resolveManager(managerIdStr);
            
            log.info("Calling notifyProfileUpdateRequestDecision - User: {}, Requester: {}, Approved: {}, Manager: {}", 
                    user.getId(), requester.getId(), isApproved, manager != null ? manager.getId() : STRING_NULL);
            
            notificationService.notifyProfileUpdateRequestDecision(user, requester, isApproved, comments, locale, manager);
            
            log.info("Successfully processed profile update request decision notification - User: {}, Requester: {}, Approved: {}. Notification should be saved to database.", 
                    user.getId(), requester.getId(), isApproved);
                    
        } catch (Exception e) {
            log.error("Failed to handle profile update request decision: {}", e.getMessage(), e);
            log.error(ERROR_MESSAGE_CAUSED_BY, message, e);
        }
    }

    /**
     * Handle item cancellation request decision
     * Required fields: orderedItemId, requesterId
     */
    private void handleItemCancellationRequestDecision(Map<String, Object> message, Boolean isApproved, 
                                                       String comments, Locale locale) {
        try {
            String orderedItemIdStr = (String) message.get(FIELD_ORDERED_ITEM_ID);
            String requesterIdStr = (String) message.get(FIELD_REQUESTER_ID);
            
            if (orderedItemIdStr == null || requesterIdStr == null) {
                log.warn("Invalid item cancellation request decision message: missing orderedItemId or requesterId");
                return;
            }
            
            UUID orderedItemId = UUID.fromString(orderedItemIdStr);
            UUID requesterId = UUID.fromString(requesterIdStr);
            
            Optional<OrderedItem> orderedItemOpt = orderedItemRepository.findById(orderedItemId);
            Optional<User> requesterOpt = userRepository.findById(requesterId);
            
            if (orderedItemOpt.isEmpty()) {
                log.warn("OrderedItem not found for ID: {}", orderedItemId);
                return;
            }
            
            if (requesterOpt.isEmpty()) {
                log.warn("Requester not found for ID: {}", requesterId);
                return;
            }
            
            OrderedItem orderedItem = orderedItemOpt.get();
            User requester = requesterOpt.get();
            
            notificationService.notifyCancellationDecision(orderedItem, requester, isApproved, comments, locale);
            
            log.info("Successfully sent WebSocket notification for item cancellation request decision - OrderedItem: {}, Requester: {}, Approved: {}", 
                    orderedItem.getId(), requester.getId(), isApproved);

            // Create audit trail entry for requester when item cancellation request is approved/declined
            createItemCancellationAuditTrail(orderedItem, requester, isApproved, comments);
                    
        } catch (Exception e) {
            log.error("Failed to handle item cancellation request decision: {}", e.getMessage(), e);
        }
    }

    /**
     * Handle combo cancellation request decision
     * Required fields: orderedComboId, requesterId
     */
    private void handleComboCancellationRequestDecision(Map<String, Object> message, Boolean isApproved,
                                                        String comments, Locale locale) {
        try {
            String orderedComboIdStr = (String) message.get(FIELD_ORDERED_COMBO_ID);
            String requesterIdStr = (String) message.get(FIELD_REQUESTER_ID);

            if (orderedComboIdStr == null || requesterIdStr == null) {
                log.warn("Invalid combo cancellation request decision message: missing orderedComboId or requesterId");
                return;
            }

            UUID orderedComboId = UUID.fromString(orderedComboIdStr);
            UUID requesterId = UUID.fromString(requesterIdStr);

            Optional<OrderedCombo> orderedComboOpt = orderedComboRepository.findById(orderedComboId);
            Optional<User> requesterOpt = userRepository.findById(requesterId);

            if (orderedComboOpt.isEmpty()) {
                log.warn("OrderedCombo not found for ID: {}", orderedComboId);
                return;
            }

            if (requesterOpt.isEmpty()) {
                log.warn("Requester not found for ID: {}", requesterId);
                return;
            }

            OrderedCombo orderedCombo = orderedComboOpt.get();
            User requester = requesterOpt.get();

            notificationService.notifyComboCancellationDecision(orderedCombo, requester, isApproved, comments, locale);

            log.info("Successfully sent WebSocket notification for combo cancellation request decision - OrderedCombo: {}, Requester: {}, Approved: {}",
                    orderedCombo.getId(), requester.getId(), isApproved);
        } catch (Exception e) {
            log.error("Failed to handle combo cancellation request decision: {}", e.getMessage(), e);
        }
    }

    /**
     * Handle order cancellation request decision
     * Required fields: orderId, requesterId
     */
    private void handleOrderCancellationRequestDecision(Map<String, Object> message, Boolean isApproved, 
                                                        String comments, Locale locale) {
        try {
            String orderIdStr = (String) message.get(FIELD_ORDER_ID);
            String requesterIdStr = (String) message.get(FIELD_REQUESTER_ID);
            
            if (orderIdStr == null || requesterIdStr == null) {
                log.warn("Invalid order cancellation request decision message: missing orderId or requesterId");
                return;
            }
            
            UUID orderId = UUID.fromString(orderIdStr);
            UUID requesterId = UUID.fromString(requesterIdStr);
            
            Optional<Order> orderOpt = orderRepository.findById(orderId);
            Optional<User> requesterOpt = userRepository.findById(requesterId);
            
            if (orderOpt.isEmpty()) {
                log.warn("Order not found for ID: {}", orderId);
                return;
            }
            
            if (requesterOpt.isEmpty()) {
                log.warn("Requester not found for ID: {}", requesterId);
                return;
            }
            
            Order order = orderOpt.get();
            User requester = requesterOpt.get();
            
            notificationService.notifyOrderCancellationDecision(order, requester, isApproved, comments, locale);
            
            log.info("Successfully sent WebSocket notification for order cancellation request decision - Order: {}, Requester: {}, Approved: {} (includes KDS notification)", 
                    order.getId(), requester.getId(), isApproved);
                    
        } catch (Exception e) {
            log.error("Failed to handle order cancellation request decision: {}", e.getMessage(), e);
        }
    }
    
    /**
     * Resolve updater from message if available.
     */
    private User resolveUpdater(String updaterIdStr) {
        if (updaterIdStr == null) {
            log.debug("No updaterId in profile updated directly message");
            return null;
        }
        try {
            UUID updaterId = UUID.fromString(updaterIdStr);
            Optional<User> updaterOpt = userRepository.findById(updaterId);
            if (updaterOpt.isPresent()) {
                User updater = updaterOpt.get();
                log.info("Found updater {} for profile updated directly notification", updater.getId());
                return updater;
            } else {
                log.warn("Updater not found for ID: {} in profile updated directly message", updaterId);
                return null;
            }
        } catch (Exception e) {
            log.warn("Invalid updaterId in profile updated directly message: {}", updaterIdStr, e);
            return null;
        }
    }
    
    /**
     * Safely notify waiter about transaction cancellation.
     */
    private void notifyWaiterSafely(Transaction transaction, Locale locale) {
        try {
            notifyWaiterAboutTransactionCancellation(transaction, locale);
        } catch (Exception e) {
            log.error("Failed to notify waiter about transaction cancellation: {}", e.getMessage(), e);
        }
    }
    
    /**
     * Update audit trail with request and review information for QA tracking.
     */
    private void updateAuditTrailWithRequestInfo(AuditTrail auditTrail, Transaction transaction, String actionTag) {
        if (requestDecisionAuditTrailUpdater == null) {
            return;
        }
        try {
            requestDecisionAuditTrailUpdater.updateAuditTrailWithRequestInfo(auditTrail, transaction, actionTag);
        } catch (Exception e) {
            // Keep the decision processing visible; failures should trigger retry/DLQ via caller rethrow.
            throw e;
        }
    }
    
    /**
     * Resolve manager from message if available.
     */
    private User resolveManager(String managerIdStr) {
        if (managerIdStr == null) {
            log.debug("No managerId in profile update request decision message");
            return null;
        }
        try {
            UUID managerId = UUID.fromString(managerIdStr);
            Optional<User> managerOpt = userRepository.findById(managerId);
            if (managerOpt.isPresent()) {
                User manager = managerOpt.get();
                log.info("Found manager {} for profile update request decision notification", manager.getId());
                return manager;
            } else {
                log.warn("Manager not found for ID: {} in profile update request decision message", managerId);
                return null;
            }
        } catch (Exception e) {
            log.warn("Invalid managerId in profile update request decision message: {}", managerIdStr, e);
            return null;
        }
    }
    
    /**
     * Create audit trail for item cancellation request decision.
     */
    private void createItemCancellationAuditTrail(OrderedItem orderedItem, User requester, Boolean isApproved, String comments) {
        try {
            RequestStatus status = (isApproved != null && isApproved)
                    ? RequestStatus.APPROVED
                    : RequestStatus.DECLINED;
            String auditComments = (comments != null && !comments.trim().isEmpty()) ? comments.trim() : DEFAULT_COMMENT;
            // Resolve restaurant without lazy Order#getRestaurant() (RabbitMQ listener has no Hibernate session)
            Restaurant restaurant = null;
            if (orderedItem.getOrder() != null) {
                java.util.UUID orderId = orderedItem.getOrder().getId();
                java.util.Optional<java.util.UUID> restaurantIdOpt = orderRepository.findRestaurantIdByOrderId(orderId);
                if (restaurantIdOpt.isPresent()) {
                    restaurant = restaurantRepository.findById(restaurantIdOpt.get()).orElse(null);
                }
            }
            auditTrailService.createAuditTrail(
                    requester,
                    ActionType.CANCELLATION,
                    restaurant,
                    status,
                    null, // ipAddress
                    null, // userAgent
                    orderedItem.getId(),
                    ENTITY_TYPE_ITEM,
                    String.format("Item cancellation request %s by manager. Comments: %s",
                            (isApproved != null && isApproved) ? STATUS_APPROVED : STATUS_DECLINED,
                            auditComments)
            );
        } catch (Exception e) {
            log.error("Failed to create audit trail for item cancellation request decision: {}", e.getMessage(), e);
        }
    }

    /**
     * JSON deserialization may yield Boolean, Integer, or String for the same logical field.
     */
    private static Boolean parseIsApproved(Map<String, Object> message) {
        Object v = message.get(FIELD_IS_APPROVED);
        if (v == null) {
            v = message.get(FIELD_APPROVED_LEGACY);
        }
        if (v instanceof Boolean b) {
            return b;
        }
        if (v instanceof Number n) {
            return n.intValue() != 0;
        }
        if (v instanceof String s) {
            if ("true".equalsIgnoreCase(s) || "1".equals(s)) {
                return Boolean.TRUE;
            }
            if ("false".equalsIgnoreCase(s) || "0".equals(s)) {
                return Boolean.FALSE;
            }
        }
        return null;
    }

    /**
     * Publisher may send {@link Locale#toString()} (underscore) or {@link Locale#toLanguageTag()} (hyphen).
     */
    private static Locale localeFromMessageTag(String localeStr) {
        if (localeStr == null || localeStr.isBlank() || STRING_NULL.equalsIgnoreCase(localeStr)) {
            return Locale.forLanguageTag(DEFAULT_LOCALE);
        }
        return Locale.forLanguageTag(localeStr.trim().replace('_', '-'));
    }
}

