package com.gulfnet.restaurantmanagement.service;

import com.gulfnet.shared_library.entity.OrderedItem;
import com.gulfnet.shared_library.entity.RestaurantTable;
import com.gulfnet.shared_library.entity.User;

import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;

/**
 * Unified notification service for all restaurant operations
 * Handles FCM and WebSocket notifications for managers, waiters, and other roles
 */
public interface NotificationService {

    // ==================== ITEM NOTIFICATIONS ====================
    
    /**
     * Notify waiter when an item is ready to be served
     */
    void notifyItemReady(OrderedItem orderedItem, User waiter, Locale userLocale);
    
    /**
     * Notify waiter when an item is delayed in preparation
     */
    void notifyItemDelayed(OrderedItem orderedItem, User waiter, String delayReason, Locale userLocale);
    
    /**
     * Notify waiter when an item is cancelled by manager
     */
    void notifyItemCancelledForWaiter(OrderedItem orderedItem, User waiter, Locale userLocale);
    
    /**
     * Notify KDS users when an item is served
     */
    void notifyItemServed(OrderedItem orderedItem, Locale userLocale);
    
    /**
     * Notify managers when an item cancellation request is opened
     * @param requester The user who created the cancellation request (can be null for backward compatibility)
     */
    void notifyCancellationRequestOpened(OrderedItem orderedItem, List<User> managers, User requester, Locale userLocale);
    
    /**
     * Notify managers when an item is canceled
     */
    void notifyItemCanceled(OrderedItem orderedItem, List<User> managers, Locale userLocale);
    
    /**
     * Notify waiter when their cancellation request is approved/rejected
     */
    void notifyCancellationDecision(OrderedItem orderedItem, User waiter, boolean isApproved, String comments, Locale userLocale);
    
    /**
     * Notify managers when a combo cancellation request is opened
     * @param requester The user who created the cancellation request (can be null for backward compatibility)
     */
    void notifyComboCancellationRequestOpened(com.gulfnet.shared_library.entity.OrderedCombo orderedCombo, List<User> managers, User requester, Locale userLocale);
    
    /**
     * Notify waiter when their combo cancellation request is approved/rejected
     */
    void notifyComboCancellationDecision(com.gulfnet.shared_library.entity.OrderedCombo orderedCombo, User waiter, boolean isApproved, String comments, Locale userLocale);

    // ==================== TRANSACTION NOTIFICATIONS ====================
    
    /**
     * Notify managers when a transaction cancellation request is opened
     * @param requester The user who created the cancellation request (can be null for backward compatibility)
     */
    void notifyTransactionCancellationRequestOpened(com.gulfnet.shared_library.entity.Transaction transaction, List<User> managers, User requester, Locale userLocale);

    /**
     * Notify managers when a refund request is opened for a transaction.
     * This is triggered when a cashier raises a refund request that requires manager approval
     * and the notification is also saved in the database for manager notification listing.
     *
     * @param transaction The transaction for which the refund is requested
     * @param managers    The list of manager users to notify
     * @param requester   The user who created the refund request (typically the cashier)
     * @param userLocale  The locale for message translation
     */
    void notifyRefundRequestOpened(com.gulfnet.shared_library.entity.Transaction transaction, List<User> managers, User requester, Locale userLocale);
    
    /**
     * Notify requester/cashier when their transaction cancellation request is approved/rejected
     */
    void notifyTransactionCancellationDecision(com.gulfnet.shared_library.entity.Transaction transaction, User requester, boolean isApproved, String comments, Locale userLocale);

    /**
     * Notify waiter when a transaction for their table has been cancelled
     */
    void notifyTransactionCancelledForWaiter(com.gulfnet.shared_library.entity.Transaction transaction, User waiter, Locale userLocale);

    // ==================== ORDER NOTIFICATIONS ====================
    
    /**
     * Notify waiter when a new order is placed for their assigned table
     */
    void notifyOrderPlaced(com.gulfnet.shared_library.entity.Order order, User waiter, Locale userLocale);
    
    /**
     * Notify waiter when an order is updated/modified for their assigned table
     */
    void notifyOrderUpdated(com.gulfnet.shared_library.entity.Order order, User waiter, Locale userLocale);
    
    /**
     * Notify waiter when an order is cancelled for their assigned table
     */
    void notifyOrderCancelled(com.gulfnet.shared_library.entity.Order order, User waiter, Locale userLocale);
    
    /**
     * Notify managers when an order cancellation request is opened
     * @param requester The user who created the cancellation request (can be null for backward compatibility)
     */
    void notifyOrderCancellationRequestOpened(com.gulfnet.shared_library.entity.Order order, List<User> managers, User requester, Locale userLocale);
    
    /**
     * Notify requester when their order cancellation request is approved/rejected
     */
    void notifyOrderCancellationDecision(com.gulfnet.shared_library.entity.Order order, User requester, boolean isApproved, String comments, Locale userLocale);
    
    /**
     * Send WebSocket notification to KDS when an order is canceled
     */
    void notifyKdsOrderCanceled(com.gulfnet.shared_library.entity.Order order, String cancellationReason, Locale userLocale);
    
    /**
     * Notify KDS users when an item is pushed (status changed to PUSHED)
     * Uses existing WebSocket topics - WebSocket notification is already sent via sendItemStatusWebSocketNotification
     * This method only handles push notifications (FCM) and database notifications
     */
    void notifyItemPushed(OrderedItem orderedItem, Locale userLocale);
    
    /**
     * Notify KDS users when an item status changes to COOKING, READY, or DELAYED.
     * Sends user-scoped WebSocket notifications to only the KDS users whose assigned
     * categories match the item, preventing cross-category KDS notification leaks.
     * Message locale follows each recipient's profile language (same rule as waiter notifications),
     * then {@code userLocale} when the profile has no language, then English.
     */
    void notifyKdsItemStatusChange(OrderedItem orderedItem, com.gulfnet.shared_library.enums.ItemStatus newStatus,
                                   Locale userLocale);

    // ==================== TABLE NOTIFICATIONS ====================
    
    /**
     * Notify waiter when a table is assigned to them
     */
    void notifyTableAssigned(RestaurantTable table, User waiter, Locale userLocale);
    
    /**
     * Notify waiter when a table is removed from their assignment
     */
    void notifyTableRemoved(RestaurantTable table, User waiter, Locale userLocale);
    
    /**
     * Notify waiters when guests are transferred between tables
     * @param sourceTable Source table from which guest is transferred
     * @param targetTable Target table to which guest is transferred
     * @param sourceWaiter Waiter assigned to source table (can be null)
     * @param targetWaiter Waiter assigned to target table (can be null)
     * @param userLocale User locale for message formatting
     */
    void notifyGuestTransfer(RestaurantTable sourceTable, RestaurantTable targetTable, 
                            User sourceWaiter, User targetWaiter, Locale userLocale);
    
    /**
     * Notify HQ Admin when a table/section request is opened
     */
    void notifyTableSectionRequestOpened(RestaurantTable table, List<User> hqAdmins, Locale userLocale);
    
    /**
     * Notify HQ Admin when a section request is opened
     */
    void notifyTableSectionRequestOpened(com.gulfnet.shared_library.entity.RestaurantSection section, List<User> hqAdmins, Locale userLocale);
    
    /**
     * Notify requester (manager) when their table/section request is created
     * Note: Currently not used - requesters are only notified on approval/decline
     */
    void notifyTableSectionRequestCreated(RestaurantTable table, User requester, Locale userLocale);
    
    /**
     * Notify requester (manager) when their section request is created
     * Note: Currently not used - requesters are only notified on approval/decline
     */
    void notifyTableSectionRequestCreated(com.gulfnet.shared_library.entity.RestaurantSection section, User requester, Locale userLocale);

    // ==================== USER NOTIFICATIONS ====================
    
    /**
     * Notify user when their password is updated
     */
    void notifyPasswordUpdated(User user, Locale userLocale);
    
    /**
     * Notify approvers (Manager or HQ Admin) when a profile update request is opened
     */
    void notifyProfileUpdateRequestOpened(User user, List<User> approvers, Locale userLocale);
    
    /**
     * Notify requester when their profile update request is created
     * Note: Currently not used - requesters are only notified on approval/decline
     */
    void notifyProfileUpdateRequestCreated(User user, User requester, Locale userLocale);
    
    /**
     * Notify requester when their profile update request is approved or declined
     */
    void notifyProfileUpdateRequestDecision(User user, User requester, boolean isApproved, String comments, Locale userLocale);
    
    void notifyProfileUpdateRequestDecision(User user, User requester, boolean isApproved, String comments, Locale userLocale, User manager);
    
    /**
     * Notify employee when their profile is updated directly by manager or HQ without a request
     */
    void notifyProfileUpdatedDirectly(User employee, User updater, Locale userLocale);
    
    /**
     * Notify manager when a new employee is assigned to their restaurant
     */
    void notifyEmployeeAssignedToRestaurant(User employee, com.gulfnet.shared_library.entity.Restaurant restaurant, List<User> managers, Locale userLocale);

    // ==================== MENU ASSIGNMENT NOTIFICATIONS ====================
    
    /**
     * Notify managers when a new menu is assigned to their restaurant
     * so they can update KDS device assignments
     *
     * @param menu The menu that was assigned
     * @param restaurant The restaurant the menu was assigned to
     * @param managers The managers of the restaurant to notify
     * @param userLocale The locale to use for message translation
     */
    void notifyMenuAssignedToRestaurant(com.gulfnet.shared_library.entity.Menu menu, 
                                        com.gulfnet.shared_library.entity.Restaurant restaurant, 
                                        List<User> managers, Locale userLocale);

    /**
     * Notify active restaurant managers when a menu is live for their restaurant (FCM, WebSocket, in-app list).
     *
     * @param menu         menu that went live
     * @param restaurant   restaurant for which the menu is live
     * @param managers     recipients (typically MANAGER, active)
     * @param userLocale   fallback locale when a manager has no language preference
     */
    void notifyMenuLiveAtRestaurant(com.gulfnet.shared_library.entity.Menu menu,
                                    com.gulfnet.shared_library.entity.Restaurant restaurant,
                                    List<User> managers,
                                    Locale userLocale);

    // ==================== PAYMENT NOTIFICATIONS ====================
    
    /**
     * Notify waiter when payment is completed for their table
     */
    void notifyPaymentCompleted(com.gulfnet.shared_library.entity.Order order, User waiter, String paymentMethod, 
                               BigDecimal amountPaid, Locale userLocale);
    
    /**
     * Notify cashier when payment error occurs (failed/declined transactions, insufficient funds, technical issues)
     */
    void notifyPaymentError(User cashier, com.gulfnet.shared_library.entity.Transaction transaction, 
                           String errorType, String errorMessage, Locale userLocale);

    /**
     * Notify waiter when payment error occurs (failed/expired). Uses FCM for pop-up on waiter app.
     */
    void notifyPaymentErrorToWaiter(User waiter, com.gulfnet.shared_library.entity.Transaction transaction, 
                                   String errorType, String errorMessage, Locale userLocale);

    // ==================== CASHIER NOTIFICATIONS ====================
    
    /**
     * Notify cashier when their discount request is approved/rejected by manager
     */
    void notifyDiscountRequestDecision(com.gulfnet.shared_library.entity.Order order, User cashier, 
                                      boolean isApproved, String comments, Locale userLocale);
    
    /**
     * Notify cashier when their cancellation request is approved/rejected by manager
     */
    void notifyCancellationRequestDecisionForCashier(com.gulfnet.shared_library.entity.Transaction transaction, 
                                                    User cashier, boolean isApproved, String comments, Locale userLocale);
    
    /**
     * Notify cashier when their refund request is approved/rejected by manager
     */
    void notifyRefundRequestDecision(com.gulfnet.shared_library.entity.Transaction transaction, User cashier, 
                                   boolean isApproved, String comments, Locale userLocale);
    
    /**
     * Notify cashier when device/integration error occurs (cash drawer, printer, payment terminal, API failures)
     */
    void notifyDeviceIntegrationError(User cashier, String deviceType, String errorMessage, 
                                     String additionalInfo, Locale userLocale);

    // ==================== HQ ADMIN ALERT NOTIFICATIONS ====================

    /**
     * Notify all HQ Admin users when a restaurant's sales reach or exceed the configured threshold.
     *
     * @param restaurant The restaurant where the threshold was breached
     * @param totalSales The total sales amount for the configured period
     * @param userLocale The locale to use for message translation
     */
    void notifyHqAdminsSalesThresholdBreached(com.gulfnet.shared_library.entity.Restaurant restaurant,
                                              java.math.BigDecimal totalSales,
                                              java.util.Locale userLocale);

    /**
     * Notify all HQ Admin users when a restaurant's refund percentage reaches or exceeds the configured threshold.
     *
     * @param restaurant The restaurant where the threshold was breached
     * @param refundPercentage The calculated refund percentage
     * @param thresholdPercentage The configured threshold percentage
     * @param userLocale The locale to use for message translation
     */
    void notifyHqAdminsRefundPercentageBreached(com.gulfnet.shared_library.entity.Restaurant restaurant,
                                                java.math.BigDecimal refundPercentage,
                                                java.math.BigDecimal thresholdPercentage,
                                                java.util.Locale userLocale);

    /**
     * Notify all HQ Admin users when a restaurant's cancellation percentage reaches or exceeds the configured threshold.
     *
     * @param restaurant The restaurant where the threshold was breached
     * @param cancellationPercentage The calculated cancellation percentage
     * @param thresholdPercentage The configured threshold percentage
     * @param userLocale The locale to use for message translation
     */
    /**
     * Notify HQ Admins when order and/or transaction cancellation percentage threshold is breached.
     * Sends a single push notification (avoids duplicate popups) and saves one DB record per breached type
     * (ORDER_CANCELLATION_PERCENTAGE_ALERT and/or TRANSACTION_CANCELLATION_PERCENTAGE_ALERT) per admin for notification listing.
     *
     * @param orderBreached       true if order cancellation % breached
     * @param transactionBreached true if transaction cancellation % breached
     */
    void notifyHqAdminsCancellationPercentageBreachedIfAny(com.gulfnet.shared_library.entity.Restaurant restaurant,
                                                           java.math.BigDecimal orderCancelPct,
                                                           java.math.BigDecimal transactionCancelPct,
                                                           java.math.BigDecimal thresholdPercentage,
                                                           java.util.Locale userLocale,
                                                           boolean orderBreached,
                                                           boolean transactionBreached);

    /**
     * Notify cashier when their cash drawer shift is closed by a manager
     * Sends WebSocket notification to cashier and saves to database
     */
    void notifyCashDrawerShiftClosedByManager(com.gulfnet.shared_library.entity.CashierShift cashierShift,
                                              User cashier,
                                              User manager,
                                              Locale userLocale);

    /**
     * Notify managers when a cashier starts a cash drawer shift
     * Sends FCM notification to managers and saves to database
     */
    void notifyCashDrawerShiftStarted(com.gulfnet.shared_library.entity.CashierShift cashierShift,
                                     User cashier,
                                     List<User> managers,
                                     Locale userLocale);

    /**
     * Notify managers when a cashier closes a cash drawer shift
     * Sends FCM notification to managers and saves to database
     */
    void notifyCashDrawerShiftClosed(com.gulfnet.shared_library.entity.CashierShift cashierShift,
                                    User cashier,
                                    List<User> managers,
                                    Locale userLocale);

    // ==================== GENERIC NOTIFICATION METHODS ====================
    
    /**
     * Send notification to a single user
     */
    void sendNotificationToUser(User user, String notificationType, Object[] messageArgs, 
                               java.util.Map<String, String> additionalData, Locale userLocale);
    
    /**
     * Send notification to multiple users
     */
    void sendNotificationToUsers(List<User> users, String notificationType, Object[] messageArgs, 
                                java.util.Map<String, String> additionalData, Locale userLocale);
    
    /**
     * Send notification to multiple users with createdBy
     * @param createdBy The user who created the notification (can be null)
     */
    void sendNotificationToUsers(List<User> users, String notificationType, Object[] messageArgs, 
                                java.util.Map<String, String> additionalData, User createdBy, Locale userLocale);
    
    /**
     * Send notification to users by role
     */
    void sendNotificationToRole(String role, String notificationType, Object[] messageArgs, 
                               java.util.Map<String, String> additionalData, Locale userLocale);
    
    /**
     * Notify all active managers when one manager resolves a request
     * This ensures all logged-in managers are aware when a request is approved/declined
     */
    void notifyManagersAboutRequestResolution(List<User> managers, User resolvingManager, 
                                             String requestType, String requestIdentifier, 
                                             boolean isApproved, String comments, Locale userLocale);
    
    // ==================== CASH DRAWER SHIFT DISCREPANCY NOTIFICATIONS ====================
    
    /**
     * Notify managers when a cash drawer shift discrepancy request is created
     * Sends FCM notification to managers and saves to database
     */
    void notifyCashDrawerShiftDiscrepancyRequest(com.gulfnet.shared_library.entity.CashierShift cashierShift, 
                                                User cashier, List<User> managers, Locale userLocale);
    
    /**
     * Notify cashier when their cash drawer shift discrepancy request is approved or declined
     * Sends WebSocket notification to cashier and saves to database
     */
    void notifyCashDrawerShiftDiscrepancyDecision(com.gulfnet.shared_library.entity.CashierShift cashierShift, 
                                                 User cashier, boolean isApproved, String comments, 
                                                 User manager, Locale userLocale);
    
    /**
     * Notify cashiers when a manager cancels an order that is in PENDING status (PUSHED or IN_PROGRESS)
     * Sends WebSocket notification to cashiers and saves to database
     */
    void notifyCashiersOrderCancelledByManager(com.gulfnet.shared_library.entity.Order order, 
                                              List<User> cashiers, String cancellationReason, 
                                              Locale userLocale);
}
