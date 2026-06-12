package com.gulfnet.restaurantmanagement.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gulfnet.restaurantmanagement.config.WebSocketClientLocaleRegistry;
import com.gulfnet.restaurantmanagement.config.RestaurantChainConfigProperties;
import com.gulfnet.restaurantmanagement.service.NotificationService;
import com.gulfnet.restaurantmanagement.service.OrderValidationService;
import com.gulfnet.restaurantmanagement.service.AlertConfigurationResolver;
import com.gulfnet.restaurantmanagement.service.NotificationBuilderService;
import com.gulfnet.restaurantmanagement.service.NotificationMessage;
import com.gulfnet.restaurantmanagement.service.NotificationTemplate;
import com.gulfnet.restaurantmanagement.util.MessageUtil;
import com.gulfnet.shared_library.entity.Notification;
import com.gulfnet.shared_library.entity.OrderedItem;
import com.gulfnet.shared_library.entity.LoginAudit;
import com.gulfnet.shared_library.entity.RestaurantTable;
import com.gulfnet.shared_library.entity.User;
import com.gulfnet.shared_library.entity.Restaurant;
import com.gulfnet.shared_library.enums.ItemStatus;
import com.gulfnet.shared_library.entity.CashDrawerTranslation;
import com.gulfnet.shared_library.repository.CashDrawerTranslationRepository;
import com.gulfnet.shared_library.repository.LoginAuditRepository;
import com.gulfnet.shared_library.repository.NotificationRepository;
import com.gulfnet.shared_library.util.CurrencyFormatter;
import com.gulfnet.shared_library.util.CashDrawerTranslationUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import static com.gulfnet.restaurantmanagement.config.RabbitMQConfig.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Unified notification service implementation
 * Handles all notifications via WebSocket and message passing (RabbitMQ) for the restaurant management system
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationServiceImpl implements NotificationService {
    
    private final NotificationBuilderService notificationBuilder;
    private final SimpMessagingTemplate messagingTemplate;
    private final WebSocketClientLocaleRegistry webSocketClientLocaleRegistry;
    private final MessageUtil messageUtil;
    private final NotificationRepository notificationRepository;
    private final AlertConfigurationResolver alertConfigurationResolver;
    private final CashDrawerTranslationRepository cashDrawerTranslationRepository;
    private final OrderValidationService orderValidationService;
    private final RestaurantChainConfigProperties restaurantChainConfigProperties;
    
    @Autowired(required = false)
    private RabbitTemplate rabbitTemplate;
    
    @Autowired(required = false)
    private com.gulfnet.shared_library.repository.UserRepository userRepository;
    
    @Autowired(required = false)
    private com.gulfnet.shared_library.repository.RoleRepository roleRepository;
    
    @Autowired(required = false)
    private LoginAuditRepository loginAuditRepository;
    
    @Autowired(required = false)
    private com.gulfnet.shared_library.repository.CategoryKdsRepository categoryKdsRepository;
    
    @Autowired(required = false)
    private com.gulfnet.shared_library.repository.CategoryItemMappingRepository categoryItemMappingRepository;
    
    @Autowired(required = false)
    private com.gulfnet.shared_library.repository.KdsRepository kdsRepository;
    
    @Autowired(required = false)
    private com.gulfnet.shared_library.repository.KdsConfigurationRepository kdsConfigurationRepository;
    
    @Autowired(required = false)
    private com.gulfnet.shared_library.repository.ItemTranslationRepository itemTranslationRepository;
    
    @Autowired(required = false)
    private com.gulfnet.shared_library.repository.OrderRepository orderRepository;
    
    @Autowired(required = false)
    private com.gulfnet.shared_library.repository.MenuCategoryMappingRepository menuCategoryMappingRepository;
    
    @Autowired(required = false)
    private com.gulfnet.shared_library.repository.CategoryRepository categoryRepository;
    
    @Autowired(required = false)
    private com.gulfnet.shared_library.repository.OrderedItemRepository orderedItemRepository;
    
    @Autowired(required = false)
    private com.gulfnet.shared_library.repository.OrderedComboRepository orderedComboRepository;
    
    // WebSocket topics
    private static final String MANAGER_TOPIC = "/topic/manager/notifications";
    private static final String WAITER_TOPIC = "/topic/waiter/notifications";
    private static final String CASHIER_TOPIC = "/topic/cashier/notifications";
    private static final String ORDER_UPDATE_TOPIC = "/topic/order/updates";
    private static final String TABLE_UPDATE_TOPIC = "/topic/table/updates";
    private static final String USER_UPDATE_TOPIC = "/topic/user/updates";

    private static final String LOG_DEVICE_TOKEN_PRESENT = "present";
    private static final String LOG_DEVICE_TOKEN_ABSENT = "absent";
    private static final String LOG_PLACEHOLDER_UNKNOWN = "unknown";
    private static final String LOG_COULD_NOT_GET_ORDER_NUMBER_KDS = "Could not get order number for KDS notification: {}";

    // Notification type constants
    private static final String NOTIF_ITEM_READY = "ITEM_READY";
    private static final String NOTIF_ITEM_DELAYED = "ITEM_DELAYED";
    private static final String NOTIF_ITEM_SERVED = "ITEM_SERVED";
    private static final String NOTIF_ITEM_CANCELED = "ITEM_CANCELED";
    private static final String NOTIF_CANCEL_REQUEST_OPENED = "CANCEL_REQUEST_OPENED";
    private static final String NOTIF_CANCELLATION_APPROVED = "CANCELLATION_APPROVED";
    private static final String NOTIF_CANCELLATION_REJECTED = "CANCELLATION_REJECTED";
    private static final String NOTIF_ITEM_CANCELLATION_APPROVED = "ITEM_CANCELLATION_APPROVED";
    private static final String NOTIF_ITEM_CANCELLATION_DECLINED = "ITEM_CANCELLATION_DECLINED";
    private static final String NOTIF_ORDER_PLACED = "ORDER_PLACED";
    private static final String NOTIF_ITEM_PUSHED = "ITEM_PUSHED";
    private static final String NOTIF_ITEM_STATUS_UPDATE = "ITEM_STATUS_UPDATE";
    private static final String NOTIF_KDS_COOKING = "KDS_COOKING";
    private static final String NOTIF_KDS_READY = "KDS_READY";
    private static final String NOTIF_KDS_DELAYED = "KDS_DELAYED";
    private static final String NOTIF_ORDER_UPDATED = "ORDER_UPDATED";
    private static final String NOTIF_ORDER_CANCELLATION_APPROVED = "ORDER_CANCELLATION_APPROVED";
    private static final String NOTIF_ORDER_CANCELED = "ORDER_CANCELED";
    private static final String NOTIF_PAYMENT_COMPLETED = "PAYMENT_COMPLETED";
    private static final String NOTIF_PAYMENT_ERROR = "PAYMENT_ERROR";
    private static final String NOTIF_PAYMENT_FAILED = "PAYMENT_FAILED";
    private static final String NOTIF_PAYMENT_EXPIRED = "PAYMENT_EXPIRED";
    private static final String NOTIF_TABLE_ASSIGNED = "TABLE_ASSIGNED";
    private static final String NOTIF_TABLE_REMOVED = "TABLE_REMOVED";
    private static final String NOTIF_TABLE_SECTION_REQUEST_OPENED = "TABLE_SECTION_REQUEST_OPENED";
    private static final String NOTIF_TABLE_SECTION_REQUEST_CREATED = "TABLE_SECTION_REQUEST_CREATED";
    private static final String NOTIF_PASSWORD_UPDATED = "PASSWORD_UPDATED";
    private static final String NOTIF_PROFILE_UPDATE_REQUEST_OPENED = "PROFILE_UPDATE_REQUEST_OPENED";
    private static final String NOTIF_PROFILE_UPDATE_REQUEST_APPROVED = "PROFILE_UPDATE_REQUEST_APPROVED";
    private static final String NOTIF_PROFILE_UPDATE_REQUEST_DECLINED = "PROFILE_UPDATE_REQUEST_DECLINED";
    private static final String NOTIF_PROFILE_UPDATE_REQUEST_CREATED = "PROFILE_UPDATE_REQUEST_CREATED";
    private static final String NOTIF_PROFILE_UPDATED_DIRECTLY = "PROFILE_UPDATED_DIRECTLY";
    private static final String NOTIF_EMPLOYEE_ASSIGNED_TO_RESTAURANT = "EMPLOYEE_ASSIGNED_TO_RESTAURANT";
    private static final String NOTIF_CASH_DRAWER_SHIFT_CLOSED = "CASH_DRAWER_SHIFT_CLOSED";
    private static final String NOTIF_CASH_DRAWER_SHIFT_DISCREPANCY_APPROVED = "CASH_DRAWER_SHIFT_DISCREPANCY_APPROVED";
    private static final String NOTIF_CASH_DRAWER_SHIFT_DISCREPANCY_REQUEST = "CASH_DRAWER_SHIFT_DISCREPANCY_REQUEST";
    private static final String NOTIF_CASH_DRAWER_SHIFT_DISCREPANCY_DECLINED = "CASH_DRAWER_SHIFT_DISCREPANCY_DECLINED";
    private static final String NOTIF_CASH_DRAWER_SHIFT_STARTED = "CASH_DRAWER_SHIFT_STARTED";
    private static final String NOTIF_REFUND_REQUEST_DECLINED = "REFUND_REQUEST_DECLINED";
    private static final String NOTIF_REFUND_REQUEST_APPROVED = "REFUND_REQUEST_APPROVED";
    private static final String NOTIF_DEVICE_INTEGRATION_ERROR = "DEVICE_INTEGRATION_ERROR";
    private static final String NOTIF_MENU_ASSIGNED_TO_RESTAURANT = "MENU_ASSIGNED_TO_RESTAURANT";
    private static final String NOTIF_MENU_LIVE_AT_RESTAURANT = "MENU_LIVE_AT_RESTAURANT";

    // Data key constants
    private static final String KEY_ORDER_ID = "orderId";
    private static final String KEY_ORDER_NUMBER = "orderNumber";
    private static final String KEY_RESTAURANT_ID = "restaurantId";
    /** Suffix appended to HQ threshold alert bodies for duplicate detection (one alert per restaurant per type per day). */
    private static final String ALERT_DEDUP_RESTAURANT_ID_SUFFIX_FORMAT = " [rid:%s]";
    private static final String ALERT_RID_SENTINEL_FORMAT = "[rid:%s]";
    private static final String KEY_TABLE_ID = "tableId";
    private static final String KEY_TABLE_CODE = "tableCode";
    private static final String KEY_TABLE_NUMBER = "tableNumber";
    private static final String KEY_NOTIFICATION_TYPE = "notificationType";
    private static final String KEY_IS_APPROVED = "isApproved";
    private static final String KEY_STATUS = "status";
    private static final String KEY_TIMESTAMP = "timestamp";
    private static final String KEY_MESSAGE = "message";
    private static final String KEY_TOPIC = "topic";
    private static final String KEY_TITLE = "title";
    private static final String KEY_DATA = "data";
    private static final String KEY_TYPE = "type";
    private static final String KEY_ORDERED_ITEM_ID = "orderedItemId";
    private static final String KEY_ITEM_ID = "itemId";
    private static final String KEY_CANCELLATION_PERCENTAGE = "cancellationPercentage";
    private static final String KEY_REQUEST_ID = "requestId";

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    
    // Log message constants
    private static final String LOG_RELOADED_USER_DEVICE_TOKEN = "Reloaded user {} - device token: {}";
    private static final String LOG_FAILED_TO_RELOAD_USER = "Failed to reload user {} to get device token: {}";
    private static final String LOG_COULD_NOT_GET_RESTAURANT_ID = "Could not get restaurant ID from order for KDS notification: {}";
    private static final String LOG_COULD_NOT_GET_TABLE_ID = "Could not get table ID or table code for KDS notification: {}";
    private static final String LOG_ROLE_OR_USER_REPO_NOT_AVAILABLE = "RoleRepository or UserRepository is not available - cannot resolve HQ Admin users";
    private static final String KEY_REQUEST_TYPE = "requestType";
    private static final String KEY_COMMENTS = "comments";
    private static final String KEY_MANAGER_COMMENTS = "managerComments";
    private static final String KEY_APPROVED = "approved";
    private static final String KEY_USER_ID = "userId";
    private static final String KEY_WAITER_ID = "waiterId";
    private static final String KEY_WAITER_NAME = "waiterName";
    private static final String KEY_CASHIER_NAME = "cashierName";
    private static final String KEY_CASHIER_ID = "cashierId";
    private static final String KEY_SHIFT_ID = "shiftId";
    private static final String KEY_TOTAL_AMOUNT = "totalAmount";
    private static final String KEY_ORDER_STATUS = "orderStatus";
    private static final String KEY_SUB_TOTAL = "subTotal";
    private static final String KEY_ADDITIONAL_INFO = "additionalInfo";
    /** Comma-separated KDS station UUIDs for KDS clients to filter pop-ups / listing by active station. */
    private static final String KEY_TARGET_KDS_IDS = "targetKdsIds";
    private static final String KEY_ROLE_ID = "roleId";
    private static final String KEY_USER_NAME = "userName";
    private static final String KEY_EMAIL = "email";
    private static final String KEY_FIRST_NAME = "firstName";
    private static final String KEY_LAST_NAME = "lastName";
    private static final String KEY_RESTAURANT_CODE = "restaurantCode";
    private static final String KEY_REQUESTER_NAME = "requesterName";

    // List type constants
    private static final String LIST_NOTIFICATIONS = "notifications";
    private static final String LIST_REQUESTS = "requests";

    // Status and value constants
    private static final String STATUS_FOUND = "FOUND";
    private static final String STATUS_NOT_FOUND = "NOT FOUND";
    private static final String VALUE_DECLINED = "DECLINED";
    private static final String VALUE_APPROVED = "APPROVED";
    private static final String VALUE_UNKNOWN = "UNKNOWN";
    private static final String VALUE_FALSE = "false";
    private static final String ROLE_HQ_ADMIN = "HQ_ADMIN";

    // WebSocket constants
    private static final String RESTAURANT_TOPIC_PREFIX = "/topic/restaurant/";
    private static final String TYPE_WEBSOCKET_NOTIFICATION = "websocket_notification";

    /**
     * KDS item-status events: {@code /topic/restaurant/{restaurantId}/kds/{kdsStationId}/item-status} so each device
     * subscribes only for its active station (avoids every KDS seeing the same user-scoped queue when sharing one login).
     */
    private static String buildKdsItemStatusTopic(java.util.UUID restaurantId, java.util.UUID kdsStationId) {
        return RESTAURANT_TOPIC_PREFIX + restaurantId + "/kds/" + kdsStationId + "/item-status";
    }

    /** Fallback when routing produced no station ids (backward compatibility). */
    private static String legacyKdsItemStatusTopic(java.util.UUID restaurantId) {
        return RESTAURANT_TOPIC_PREFIX + restaurantId + "/kds/item-status";
    }

    private static java.util.List<String> resolveKdsItemStatusTopicDestinations(
            java.util.UUID restaurantId, java.util.Set<java.util.UUID> targetKdsStationIds) {
        if (restaurantId == null) {
            return java.util.List.of();
        }
        if (targetKdsStationIds == null || targetKdsStationIds.isEmpty()) {
            return java.util.List.of(legacyKdsItemStatusTopic(restaurantId));
        }
        java.util.List<String> out = new java.util.ArrayList<>();
        for (java.util.UUID id : targetKdsStationIds) {
            if (id != null) {
                out.add(buildKdsItemStatusTopic(restaurantId, id));
            }
        }
        if (out.isEmpty()) {
            out.add(legacyKdsItemStatusTopic(restaurantId));
        }
        return out;
    }

    /**
     * Locale for push/popup and persisted notification text: prefer the recipient's {@link User#getLanguageCode()},
     * then the request/trigger locale, then English.
     * <p>Waiters use {@link #localeForWaiterRecipient(User, Locale)} (profile {@code languageCode}, then WebSocket {@code locale}, then request locale).
     * KDS uses {@link #localeForKdsRecipient(User, Locale)} (profile {@code languageCode}, then WebSocket {@code locale}, then request locale).
     */
    private static Locale localeForRecipient(User recipient, Locale requestLocale) {
        if (recipient != null && recipient.getLanguageCode() != null && !recipient.getLanguageCode().trim().isEmpty()) {
            return Locale.forLanguageTag(recipient.getLanguageCode().trim());
        }
        if (requestLocale != null) {
            return requestLocale;
        }
        return Locale.ENGLISH;
    }

    /**
     * Waiter WebSocket/FCM: recipient profile {@link User#getLanguageCode()} first (so KDS-triggered pushes are not in the KDS user's language),
     * then WebSocket-reported {@code locale}, then triggering HTTP locale, then English.
     */
    private Locale localeForWaiterRecipient(User waiter, Locale triggerLocale) {
        if (waiter != null && waiter.getLanguageCode() != null && !waiter.getLanguageCode().trim().isEmpty()) {
            return Locale.forLanguageTag(waiter.getLanguageCode().trim());
        }
        if (waiter != null && waiter.getId() != null) {
            Locale wired = webSocketClientLocaleRegistry.getRecordedLocale(waiter.getId().toString());
            if (wired != null) {
                return wired;
            }
        }
        if (triggerLocale != null) {
            return triggerLocale;
        }
        return Locale.ENGLISH;
    }

    /**
     * KDS pop-ups: recipient profile {@link User#getLanguageCode()}, then WebSocket {@code locale},
     * then triggering HTTP locale, then English.
     */
    private Locale localeForKdsRecipient(User kdsUser, Locale triggerLocale) {
        if (kdsUser != null && kdsUser.getLanguageCode() != null && !kdsUser.getLanguageCode().trim().isEmpty()) {
            return Locale.forLanguageTag(kdsUser.getLanguageCode().trim());
        }
        if (kdsUser != null && kdsUser.getId() != null) {
            return webSocketClientLocaleRegistry.resolveLocaleForKds(kdsUser.getId().toString(), triggerLocale);
        }
        return triggerLocale != null ? triggerLocale : Locale.ENGLISH;
    }

    /**
     * Serializes notification body-argument values for persistence.
     * <p>
     * Stored form is a JSON array of strings (each element is {@code toString()} of the original arg; {@code null} becomes
     * an empty string). This is used when persisting {@code bodyKey + bodyArgs} so the body can be resolved later for the
     * viewer's locale.
     *
     * @param args raw message arguments (may be {@code null} or empty)
     * @return JSON array string, or {@code null} if {@code args} is {@code null}/empty or serialization fails
     */
    private String serializeNotificationBodyArgs(Object[] args) {
        if (args == null || args.length == 0) {
            return null;
        }
        try {
            String[] stringArgs = java.util.Arrays.stream(args)
                    .map(a -> a != null ? a.toString() : "")
                    .toArray(String[]::new);
            return OBJECT_MAPPER.writeValueAsString(stringArgs);
        } catch (JsonProcessingException e) {
            log.warn("Failed to serialize notification body args: {}", e.getMessage());
            return null;
        }
    }

    // ==================== ITEM NOTIFICATIONS ====================
    
    /**
     * Sends a notification to the assigned waiter when an item is ready for serving.
     * The notification is sent via WebSocket and saved to the database.
     *
     * @param orderedItem The ordered item that is ready
     * @param waiter The waiter assigned to the order (can be null)
     * @param userLocale Locale for message localization
     */
    @Override
    public void notifyItemReady(OrderedItem orderedItem, User waiter, Locale userLocale) {
        try {
            Locale loc = waiter != null ? localeForWaiterRecipient(waiter, userLocale) : userLocale;
            Map<String, String> itemData = buildItemData(orderedItem, NOTIF_ITEM_READY, null, loc);
            
            String tableCode = getTableCodeFromOrder(orderedItem.getOrder());
            NotificationMessage message = notificationBuilder.buildMessage(
                    NotificationTemplate.Templates.ITEM_READY,
                    loc,
                    new Object[]{
                            getItemName(orderedItem.getItem(), loc),
                            tableCode
                    },
                    itemData
            );

            // Send notification to waiter if waiter is assigned
            if (waiter != null) {
                // Send notification via WebSocket and RabbitMQ
                sendToUser(waiter, message, WAITER_TOPIC);
                
                // Save notification to database for waiter
                saveNotificationToDatabase(waiter, message, NOTIF_ITEM_READY, null);
            } else {
                log.debug("No waiter assigned for item ready notification");
            }

            // Don't send notification to KDS for item ready - this status is changed by KDS user only
            
        } catch (Exception e) {
            if (waiter != null) {
                log.error("Failed to send item ready notification to waiter {}: {}", waiter.getId(), e.getMessage(), e);
            } else {
                log.error("Failed to send item ready notification: {}", e.getMessage(), e);
            }
        }
    }
    
    /**
     * Sends a notification to the assigned waiter when an item is delayed.
     * Includes the delay reason in the notification message.
     *
     * @param orderedItem The ordered item that is delayed
     * @param waiter The waiter assigned to the order (can be null)
     * @param delayReason Reason for the delay
     * @param userLocale Locale for message localization
     */
    @Override
    public void notifyItemDelayed(OrderedItem orderedItem, User waiter, String delayReason, Locale userLocale) {
        try {
            Locale loc = waiter != null ? localeForWaiterRecipient(waiter, userLocale) : userLocale;
            Map<String, String> itemData = buildItemData(orderedItem, NOTIF_ITEM_DELAYED, delayReason, loc);
            
            String tableCode = getTableCodeFromOrder(orderedItem.getOrder());
            NotificationMessage message = notificationBuilder.buildMessage(
                    NotificationTemplate.Templates.ITEM_DELAYED,
                    loc,
                    new Object[]{
                            getItemName(orderedItem.getItem(), loc),
                            tableCode,
                            delayReason != null ? delayReason : messageUtil.getMessage("notification.no.reason", loc)
                    },
                    itemData
            );

            // Send notification to waiter if waiter is assigned
            if (waiter != null) {
                sendToUser(waiter, message, WAITER_TOPIC);
                
                // Save notification to database for waiter
                saveNotificationToDatabase(waiter, message, NOTIF_ITEM_DELAYED, null);
            } else {
                log.debug("No waiter assigned for item delayed notification");
            }

            // Don't send notification to KDS for item delayed - this status is changed by KDS user only
            
        } catch (Exception e) {
            if (waiter != null) {
                log.error("Failed to send item delayed notification to waiter {}: {}", waiter.getId(), e.getMessage(), e);
            } else {
                log.error("Failed to send item delayed notification: {}", e.getMessage(), e);
            }
        }
    }
    
    /**
     * Sends a notification to the waiter when an item is cancelled.
     * Requires a non-null waiter to send the notification.
     *
     * @param orderedItem The ordered item that was cancelled
     * @param waiter The waiter assigned to the order (must not be null)
     * @param userLocale Locale for message localization
     */
    @Override
    public void notifyItemCancelledForWaiter(OrderedItem orderedItem, User waiter, Locale userLocale) {
        if (waiter == null) {
            log.warn("Cannot send item cancelled notification: waiter is null");
            return;
        }
        
        try {
            Locale loc = localeForWaiterRecipient(waiter, userLocale);
            Map<String, String> itemData = buildItemData(orderedItem, "ITEM_CANCELLED", null, loc);
            
            String tableCode = getTableCodeFromOrder(orderedItem.getOrder());
            String itemName = getItemName(orderedItem.getItem(), loc);
            NotificationMessage message = notificationBuilder.buildMessage(
                    NotificationTemplate.Templates.ITEM_CANCELLED,
                    loc,
                    new Object[]{
                            itemName,
                            tableCode
                    },
                    itemData
            );

            sendToUser(waiter, message, WAITER_TOPIC);
            
            // Save notification to database
            saveNotificationToDatabase(waiter, message, "ITEM_CANCELLED", null);
            
        } catch (Exception e) {
            log.error("Failed to send item cancelled notification to waiter {}: {}", waiter.getId(), e.getMessage(), e);
        }
    }
    
    /**
     * Sends a notification to KDS users when an item is served.
     * Finds KDS users assigned to the item's KDS station and sends notifications to them.
     *
     * @param orderedItem The ordered item that was served
     * @param userLocale Locale for message localization
     */
    @Override
    public void notifyItemServed(OrderedItem orderedItem, Locale userLocale) {
        if (orderedItem == null) {
            log.warn("Cannot send item served notification: orderedItem is null");
            return;
        }

        log.info("[KDS WebSocket] notifyItemServed enter orderedItemId={}", orderedItem.getId());
        
        try {
            // Send notification to KDS users assigned to this specific item's KDS station
            if (orderedItem.getItem() != null && orderedItem.getOrder() != null) {
                try {
                    java.util.UUID restaurantId = getRestaurantIdFromOrder(orderedItem.getOrder());
                    java.util.List<KdsItemRecipient> kdsRecipients =
                            findKdsRecipientsForItem(orderedItem.getItem(), restaurantId);
                    if (restaurantId == null) {
                        log.warn("[KDS WebSocket] notifyItemServed: restaurantId null (orderedItemId={}, orderId={})",
                                orderedItem.getId(), orderedItem.getOrder().getId());
                    } else if (kdsRecipients.isEmpty()) {
                        log.warn("[KDS WebSocket] notifyItemServed: findKdsRecipientsForItem returned 0 users (orderedItemId={}, menuItemId={}, restaurantId={})",
                                orderedItem.getId(), orderedItem.getItem().getId(), restaurantId);
                    }
                    
                    for (KdsItemRecipient rec : kdsRecipients) {
                        com.gulfnet.shared_library.entity.User kdsUser = rec.user();
                        if (kdsUser == null) {
                            continue;
                        }
                        try {
                            Locale loc = localeForKdsRecipient(kdsUser, userLocale);
                            Map<String, String> itemData = buildItemData(orderedItem, NOTIF_ITEM_SERVED, null, loc);
                            NotificationMessage message = notificationBuilder.buildMessage(
                                    NotificationTemplate.Templates.ITEM_SERVED,
                                    loc,
                                    new Object[]{getItemName(orderedItem.getItem(), loc)},
                                    itemData
                            );
                            attachTargetKdsIdsToNotificationMessageData(message, rec.targetKdsStationIds());
                            saveNotificationToDatabase(kdsUser, message, NOTIF_ITEM_SERVED, null);
                            if (restaurantId != null) {
                                sendKdsItemStatusWebSocket(orderedItem, restaurantId,
                                        java.util.List.of(rec),
                                        com.gulfnet.shared_library.enums.ItemStatus.SERVED, NOTIF_ITEM_SERVED, message);
                            }
                        } catch (Exception e) {
                            log.warn("Failed to save item served notification for KDS user {}: {}", kdsUser.getId(), e.getMessage());
                        }
                    }
                    if (!kdsRecipients.isEmpty()) {
                        log.info("Saved item served notification to {} category-specific KDS user(s) assigned to item {} (restaurant: {})",
                                kdsRecipients.size(), orderedItem.getItem().getId(), restaurantId);
                    }
                } catch (Exception e) {
                    log.error("Failed to send item served notification for item {}: {}", 
                            orderedItem.getItem() != null ? orderedItem.getItem().getId() : LOG_PLACEHOLDER_UNKNOWN, e.getMessage(), e);
                }
            } else {
                log.warn("[KDS WebSocket] notifyItemServed skip: order or item null (orderedItemId={}, orderNull={}, itemNull={})",
                        orderedItem.getId(), orderedItem.getOrder() == null, orderedItem.getItem() == null);
            }

        } catch (Exception e) {
            log.error("Failed to send item served notification: {}", e.getMessage(), e);
        }
    }
    
    /**
     * Sends notifications to managers when a cancellation request is opened for an item.
     * Notifies all provided managers about the pending cancellation request.
     *
     * @param orderedItem The ordered item for which cancellation is requested
     * @param managers List of managers to notify (must not be null or empty)
     * @param requester The user who requested the cancellation
     * @param userLocale Locale for message localization
     */
    @Override
    public void notifyCancellationRequestOpened(OrderedItem orderedItem, List<User> managers, User requester, Locale userLocale) {
        if (managers == null || managers.isEmpty()) {
            log.warn("No managers provided for cancellation request notification");
            return;
        }

        try {
            User createdBy = requester != null ? requester : orderedItem.getCancellationRequestedBy();
            for (User manager : managers) {
                if (manager == null) {
                    continue;
                }
                Locale loc = localeForRecipient(manager, userLocale);
                Map<String, String> itemData = buildItemData(orderedItem, NOTIF_CANCEL_REQUEST_OPENED, null, loc);
                itemData.put(KEY_REQUEST_ID, orderedItem.getId().toString());
                String itemName = getItemName(orderedItem.getItem(), loc);
                String tableCode = getTableCodeFromOrder(orderedItem.getOrder());
                NotificationMessage message = notificationBuilder.buildMessage(
                        NotificationTemplate.Templates.MANAGER_CANCEL_REQUEST_NOTIFICATION,
                        loc,
                        new Object[]{itemName, tableCode},
                        itemData
                );
                sendToUser(manager, message, MANAGER_TOPIC);
                saveNotificationToDatabase(manager, message, "ITEM_CANCELLATION_REQUEST", createdBy);
            }
            
            // Send list refresh events for requests to managers
            sendListRefreshEventToUsers(managers, LIST_REQUESTS);

        } catch (Exception e) {
            log.error("Failed to send cancellation request notification to managers: {}", e.getMessage(), e);
        }
    }
    
    /**
     * Sends notifications to managers and KDS users when an item is cancelled.
     * Notifies managers via manager topic and KDS users via restaurant-specific KDS topic.
     *
     * @param orderedItem The ordered item that was cancelled
     * @param managers List of managers to notify (can be null or empty)
     * @param userLocale Locale for message localization
     */
    @Override
    public void notifyItemCanceled(OrderedItem orderedItem, List<User> managers, Locale userLocale) {
        if (managers == null) {
            managers = java.util.Collections.emptyList();
        }
        
        try {
            // Notify managers only when provided
            if (!managers.isEmpty()) {
                for (User manager : managers) {
                    if (manager == null) {
                        continue;
                    }
                    Locale loc = localeForRecipient(manager, userLocale);
                    Map<String, String> itemData = buildItemData(orderedItem, NOTIF_ITEM_CANCELED, null, loc);
                    String itemName = getItemName(orderedItem.getItem(), loc);
                    String tableCode = orderedItem.getOrder() != null ? getTableCodeFromOrder(orderedItem.getOrder()) : "";
                    NotificationMessage message = notificationBuilder.buildMessage(
                            NotificationTemplate.Templates.MANAGER_ITEM_CANCELED_NOTIFICATION,
                            loc,
                            new Object[]{itemName, tableCode},
                            itemData
                    );
                    sendToUser(manager, message, MANAGER_TOPIC);
                    saveNotificationToDatabase(manager, message, "MANAGER_ITEM_CANCELED_NOTIFICATION", null);
                }
            } else {
                log.debug("No managers provided for item canceled notification – skipping manager notifications");
            }
            
            // Also notify KDS users assigned to this item/order
            // Note: General WebSocket notification is already sent via sendItemStatusWebSocketNotification to /topic/restaurant/{restaurantId}/item-status
            // But we also need to send KDS-specific WebSocket notification to /topic/restaurant/{restaurantId}/kds/item-cancellation
            if (orderedItem.getOrder() != null && orderedItem.getItem() != null) {
                try {
                    // Get restaurant ID for KDS notification
                    java.util.UUID restaurantId = null;
                    try {
                        restaurantId = getRestaurantIdFromOrder(orderedItem.getOrder());
                    } catch (Exception e) {
                        log.debug(LOG_COULD_NOT_GET_RESTAURANT_ID, e.getMessage());
                    }
                    if (restaurantId == null) {
                        log.warn("[KDS WebSocket] notifyItemCanceled: cannot resolve restaurantId for orderedItemId={}, orderId={} — skipping KDS item-cancellation WebSocket",
                                orderedItem.getId(),
                                orderedItem.getOrder() != null ? orderedItem.getOrder().getId() : null);
                    }
                    
                    java.util.List<KdsItemRecipient> kdsRecipients = new java.util.ArrayList<>();
                    if (restaurantId != null) {
                        kdsRecipients = findKdsRecipientsForItem(orderedItem.getItem(), restaurantId);
                    }
                    if (restaurantId != null && kdsRecipients.isEmpty()) {
                        log.warn("[KDS WebSocket] notifyItemCanceled: findKdsRecipientsForItem returned 0 users (orderedItemId={}, menuItemId={}, restaurantId={}) — no KDS WebSocket",
                                orderedItem.getId(), orderedItem.getItem().getId(), restaurantId);
                    }
                    
                    java.util.UUID orderedItemId = orderedItem.getId();
                    OffsetDateTime oneMinuteAgo = OffsetDateTime.now(ZoneOffset.UTC).minusMinutes(1);
                    int savedCount = 0;
                    int skippedCount = 0;
                    int failedCount = 0;
                    boolean publishedKdsRabbitMq = false;

                    if (restaurantId != null && !kdsRecipients.isEmpty()) {
                        try {
                            String kdsTopic = RESTAURANT_TOPIC_PREFIX + restaurantId + "/kds/item-cancellation";
                            java.util.UUID orderId = orderedItem.getOrder() != null ? orderedItem.getOrder().getId() : null;
                            String orderNumber = "";
                            if (orderId != null && orderRepository != null) {
                                try {
                                    orderNumber = orderRepository.findOrderNumberByOrderId(orderId).orElse("");
                                } catch (Exception e) {
                                    log.debug(LOG_COULD_NOT_GET_ORDER_NUMBER_KDS, e.getMessage());
                                }
                            }
                            String tableCodeForKds = "";
                            if (orderId != null) {
                                try {
                                    tableCodeForKds = getTableCodeFromOrder(orderedItem.getOrder());
                                } catch (Exception e) {
                                    log.debug("Could not get table code for KDS notification: {}", e.getMessage());
                                }
                            }

                            for (KdsItemRecipient rec : kdsRecipients) {
                                com.gulfnet.shared_library.entity.User kdsUser = rec.user();
                                if (kdsUser == null || kdsUser.getId() == null) {
                                    continue;
                                }
                                try {
                                    if (notificationRepository != null && orderedItemId != null) {
                                        try {
                                            org.springframework.data.domain.Pageable pageable =
                                                    org.springframework.data.domain.PageRequest.of(0, 10);
                                            org.springframework.data.domain.Page<com.gulfnet.shared_library.entity.Notification> recentNotificationsPage =
                                                    notificationRepository.findByUser_IdAndTypeInOrderByCreatedAtDesc(
                                                            kdsUser.getId(),
                                                            java.util.Collections.singletonList(NOTIF_ITEM_CANCELED),
                                                            pageable);
                                            boolean duplicateFound = false;
                                            if (recentNotificationsPage != null && recentNotificationsPage.hasContent()) {
                                                String orderedItemIdStr = orderedItemId.toString();
                                                for (com.gulfnet.shared_library.entity.Notification recentNotif : recentNotificationsPage.getContent()) {
                                                    if (recentNotif.getCreatedAt() != null
                                                            && recentNotif.getCreatedAt().isAfter(oneMinuteAgo)
                                                            && recentNotif.getMessage() != null
                                                            && recentNotif.getMessage().contains(orderedItemIdStr)) {
                                                        duplicateFound = true;
                                                        break;
                                                    }
                                                }
                                            }
                                            if (duplicateFound) {
                                                skippedCount++;
                                                continue;
                                            }
                                        } catch (Exception e) {
                                            log.debug("Could not check for duplicate notifications, proceeding: {}", e.getMessage());
                                        }
                                    }

                                    Locale kdsLoc = localeForKdsRecipient(kdsUser, userLocale);
                                    Map<String, String> itemDataKds = buildItemData(orderedItem, NOTIF_ITEM_CANCELED, null, kdsLoc);
                                    String itemNameKds = getItemName(orderedItem.getItem(), kdsLoc);
                                    String tableCodeRow = orderedItem.getOrder() != null ? getTableCodeFromOrder(orderedItem.getOrder()) : "";
                                    NotificationMessage kdsMessage = notificationBuilder.buildMessage(
                                            NotificationTemplate.Templates.MANAGER_ITEM_CANCELED_NOTIFICATION,
                                            kdsLoc,
                                            new Object[]{itemNameKds, tableCodeRow},
                                            itemDataKds
                                    );
                                    attachTargetKdsIdsToNotificationMessageData(kdsMessage, rec.targetKdsStationIds());

                                    Map<String, Object> kdsData = new HashMap<>();
                                    kdsData.put(KEY_ORDERED_ITEM_ID, orderedItem.getId().toString());
                                    kdsData.put(KEY_ITEM_ID, orderedItem.getItem() != null ? orderedItem.getItem().getId().toString() : "");
                                    kdsData.put(KEY_ORDER_ID, orderId != null ? orderId.toString() : "");
                                    kdsData.put(KEY_ORDER_NUMBER, orderNumber);
                                    kdsData.put("itemStatus", com.gulfnet.shared_library.enums.ItemStatus.CANCELED.toString());
                                    kdsData.put(KEY_RESTAURANT_ID, restaurantId.toString());
                                    kdsData.put(KEY_NOTIFICATION_TYPE, NOTIF_ITEM_CANCELED);
                                    kdsData.put("isDirectCancellation", "true");
                                    if (orderId != null && orderRepository != null) {
                                        try {
                                            java.util.Optional<java.util.UUID> tableIdOpt = orderRepository.findTableIdByOrderId(orderId);
                                            tableIdOpt.ifPresent(uuid -> kdsData.put(KEY_TABLE_ID, uuid.toString()));
                                            java.util.Optional<String> tableCodeOpt = orderRepository.findTableCodeByOrderId(orderId);
                                            tableCodeOpt.ifPresent(code -> kdsData.put(KEY_TABLE_CODE, code));
                                        } catch (Exception e) {
                                            log.debug(LOG_COULD_NOT_GET_TABLE_ID, e.getMessage());
                                        }
                                    }
                                    if (!tableCodeForKds.isEmpty()) {
                                        kdsData.put(KEY_TABLE_NUMBER, tableCodeForKds);
                                    }
                                    kdsData.put(KEY_TIMESTAMP, OffsetDateTime.now(ZoneOffset.UTC).format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
                                    putTargetKdsIdsOnKdsDataMap(kdsData, rec.targetKdsStationIds());

                                    com.gulfnet.shared_library.model.request.StatusEventMessage eventMessage =
                                            com.gulfnet.shared_library.model.request.StatusEventMessage.builder()
                                                    .title(kdsMessage.getTitle())
                                                    .message(kdsMessage.getBody())
                                                    .notificationType(NOTIF_ITEM_CANCELED)
                                                    .orderId(orderId != null ? orderId.toString() : "")
                                                    .userId(kdsUser.getId().toString())
                                                    .status(com.gulfnet.shared_library.enums.ItemStatus.CANCELED.toString())
                                                    .data(kdsData)
                                                    .build();

                                    // User-scoped: subscribe to /user/topic/restaurant/{restaurantId}/kds/item-cancellation
                                    messagingTemplate.convertAndSendToUser(kdsUser.getId().toString(), kdsTopic, eventMessage);
                                    log.info("[KDS WebSocket] delivered ITEM_CANCELED user-scoped kdsUserId={}, orderedItemId={}, stompSubscribeHint=/user{}",
                                            kdsUser.getId(), orderedItem.getId(), kdsTopic);

                                    if (rabbitTemplate != null && !publishedKdsRabbitMq) {
                                        try {
                                            Map<String, Object> wsMessage = new HashMap<>();
                                            wsMessage.put(KEY_TOPIC, kdsTopic);
                                            if (eventMessage.getTitle() != null && !eventMessage.getTitle().isEmpty()) {
                                                wsMessage.put(KEY_TITLE, eventMessage.getTitle());
                                            }
                                            wsMessage.put(KEY_MESSAGE, eventMessage.getMessage());
                                            wsMessage.put(KEY_NOTIFICATION_TYPE, eventMessage.getNotificationType());
                                            wsMessage.put(KEY_ORDER_ID, eventMessage.getOrderId());
                                            wsMessage.put(KEY_STATUS, eventMessage.getStatus());
                                            wsMessage.put(KEY_DATA, eventMessage.getData());
                                            wsMessage.put(KEY_TIMESTAMP, OffsetDateTime.now(ZoneOffset.UTC).format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
                                            wsMessage.put(KEY_TYPE, TYPE_WEBSOCKET_NOTIFICATION);
                                            rabbitTemplate.convertAndSend(WEBSOCKET_TOPIC_EXCHANGE, WEBSOCKET_ROUTING_KEY, wsMessage);
                                            publishedKdsRabbitMq = true;
                                            log.info("[Notification][FCM] rabbitPublish exchange={} routingKey={} payloadWsTopic={} notificationType={} (KDS item cancel, deduped)",
                                                    WEBSOCKET_TOPIC_EXCHANGE, WEBSOCKET_ROUTING_KEY, kdsTopic, NOTIF_ITEM_CANCELED);
                                        } catch (Exception e) {
                                            log.warn("Failed to publish KDS item cancellation WebSocket message to RabbitMQ: {}", e.getMessage());
                                        }
                                    }

                                    saveNotificationToDatabase(kdsUser, kdsMessage, NOTIF_ITEM_CANCELED, null);
                                    savedCount++;
                                } catch (Exception e) {
                                    failedCount++;
                                    log.warn("Failed to save item canceled notification for KDS user {}: {}", kdsUser.getId(), e.getMessage());
                                }
                            }
                            log.debug("Sent KDS WebSocket notification for item cancellation to {} KDS user(s) via topic {}",
                                    kdsRecipients.size(), kdsTopic);
                        } catch (Exception e) {
                            log.warn("Failed to send KDS WebSocket notification for item cancellation: {}", e.getMessage());
                        }
                    }
                    if (savedCount > 0) {
                        log.info("Saved item canceled notification to {} unique category-specific KDS user(s) assigned to item {} in order {} (restaurant: {})",
                                savedCount, orderedItem.getItem().getId(), orderedItem.getOrder().getId(),
                                restaurantId != null ? restaurantId.toString() : LOG_PLACEHOLDER_UNKNOWN);
                    }
                    if (skippedCount > 0) {
                        log.debug("Skipped {} duplicate item canceled notification(s) for ordered item {}", skippedCount, orderedItemId);
                    }
                    if (failedCount > 0) {
                        log.warn("Failed to save item canceled notification for {} KDS user(s)", failedCount);
                    }
                } catch (Exception e) {
                    log.warn("Failed to notify KDS users for item cancellation: {}", e.getMessage());
                }
            }

        } catch (Exception e) {
            log.error("Failed to send item canceled notification: {}", e.getMessage(), e);
        }
    }
    
    /**
     * Sends a notification to the waiter about the manager's decision on an item cancellation request.
     * Also notifies KDS users if the cancellation is approved.
     * Uses different topics for cashiers vs waiters.
     *
     * @param orderedItem The ordered item for which cancellation was requested
     * @param waiter The waiter who requested the cancellation (must not be null)
     * @param isApproved Whether the cancellation was approved or rejected
     * @param comments Manager's comments on the decision
     * @param userLocale Locale for message localization
     */
    @Override
    public void notifyCancellationDecision(OrderedItem orderedItem, User waiter, boolean isApproved, String comments, Locale userLocale) {
        if (waiter == null) {
            log.warn("Cannot send cancellation decision notification: waiter is null");
            return;
        }
        
        try {
            Locale waiterLoc = localeForWaiterRecipient(waiter, userLocale);
            Map<String, String> itemData;
            NotificationMessage message;
            
            String tableCode = getTableCodeFromOrder(orderedItem.getOrder());
            if (isApproved) {
                itemData = buildItemData(orderedItem, NOTIF_CANCELLATION_APPROVED, comments, waiterLoc);
                itemData.put(KEY_IS_APPROVED, "true");
                message = notificationBuilder.buildMessage(
                        NotificationTemplate.Templates.CANCELLATION_APPROVED,
                        waiterLoc,
                        new Object[]{
                                getItemName(orderedItem.getItem(), waiterLoc),
                                tableCode,
                                comments != null ? comments : ""
                        },
                        itemData
                );
            } else {
                itemData = buildItemData(orderedItem, NOTIF_CANCELLATION_REJECTED, comments, waiterLoc);
                itemData.put(KEY_IS_APPROVED, VALUE_FALSE);
                message = notificationBuilder.buildMessage(
                        NotificationTemplate.Templates.CANCELLATION_REJECTED,
                        waiterLoc,
                        new Object[]{
                                getItemName(orderedItem.getItem(), waiterLoc),
                                tableCode,
                                comments != null ? comments : ""
                        },
                        itemData
                );
            }

            String notificationType = isApproved ? NOTIF_CANCELLATION_APPROVED : NOTIF_CANCELLATION_REJECTED;
            
            // Check if requester is cashier - send to CASHIER_TOPIC (WebSocket only)
            if (isCashier(waiter)) {
                // Windows app doesn't support FCM
                sendWebSocketNotification(waiter.getId(), CASHIER_TOPIC, message, null, notificationType);
                log.info("Sent item cancellation decision notification to cashier {} via CASHIER_TOPIC", waiter.getId());
            } else {
                // For waiters, FCM push + database notification are handled centrally in UserServiceImpl
                // to avoid duplicate mobile notifications. Here we only send WebSocket for real-time UI.
                sendWebSocketNotification(waiter.getId(), WAITER_TOPIC, message, null, notificationType);
                log.info("Sent item cancellation decision notification to waiter {} via WAITER_TOPIC (WebSocket only)", waiter.getId());
            }
            
            // Note: Database notification and FCM push for the requester are handled by
            // saveItemCancellationRequestNotification() in UserServiceImpl to avoid duplicates.
            
            // Also notify KDS users assigned to this specific item when manager approves/declines cancellation request
            // Only KDS users assigned to the canceled item's category will be notified
            if (orderedItem.getOrder() != null && orderedItem.getItem() != null) {
                try {
                    // Get restaurant ID for KDS notification using helper method
                    java.util.UUID restaurantId = null;
                    try {
                        restaurantId = getRestaurantIdFromOrder(orderedItem.getOrder());
                    } catch (Exception e) {
                        log.debug(LOG_COULD_NOT_GET_RESTAURANT_ID, e.getMessage());
                    }
                    
                    java.util.List<KdsItemRecipient> kdsRecipients = new java.util.ArrayList<>();
                    if (restaurantId != null) {
                        kdsRecipients = findKdsRecipientsForItem(orderedItem.getItem(), restaurantId);
                    }
                    if (restaurantId == null) {
                        log.warn("[KDS WebSocket] notifyCancellationDecision: restaurantId null for orderedItemId={} — skipping KDS WebSocket",
                                orderedItem.getId());
                    } else if (kdsRecipients.isEmpty()) {
                        log.warn("[KDS WebSocket] notifyCancellationDecision: no KDS users for menuItemId={}, restaurantId={}, orderedItemId={}",
                                orderedItem.getItem().getId(), restaurantId, orderedItem.getId());
                    }
                    
                    String kdsNotificationType = isApproved ? NOTIF_ITEM_CANCELLATION_APPROVED : NOTIF_ITEM_CANCELLATION_DECLINED;
                    
                    // Send KDS WebSocket notification for both approved and declined (so KDS shows pop-up in both cases)
                    if (restaurantId != null) {
                        try {
                            String kdsTopic = RESTAURANT_TOPIC_PREFIX + restaurantId + "/kds/item-cancellation";
                            Map<String, Object> kdsData = new HashMap<>();
                            kdsData.put(KEY_ORDERED_ITEM_ID, orderedItem.getId().toString());
                            kdsData.put(KEY_ITEM_ID, orderedItem.getItem() != null ? orderedItem.getItem().getId().toString() : "");
                            java.util.UUID orderId = orderedItem.getOrder() != null ? orderedItem.getOrder().getId() : null;
                            kdsData.put(KEY_ORDER_ID, orderId != null ? orderId.toString() : "");
                            
                            // Get order number using repository to avoid lazy loading
                            String orderNumber = "";
                            if (orderId != null && orderRepository != null) {
                                try {
                                    java.util.Optional<String> orderNumberOpt = orderRepository.findOrderNumberByOrderId(orderId);
                                    orderNumber = orderNumberOpt.orElse("");
                                } catch (Exception e) {
                                    log.debug(LOG_COULD_NOT_GET_ORDER_NUMBER_KDS, e.getMessage());
                                }
                            }
                            kdsData.put(KEY_ORDER_NUMBER, orderNumber);
                            
                            String itemStatusForKds = isApproved
                                    ? com.gulfnet.shared_library.enums.ItemStatus.CANCELED.toString()
                                    : (orderedItem.getItemStatus() != null ? orderedItem.getItemStatus().toString() : "");
                            kdsData.put("itemStatus", itemStatusForKds);
                            kdsData.put(KEY_RESTAURANT_ID, restaurantId.toString());
                            kdsData.put(KEY_NOTIFICATION_TYPE, kdsNotificationType);
                            kdsData.put(KEY_IS_APPROVED, isApproved ? "true" : VALUE_FALSE);
                            
                            // Get table code for KDS notification (falls back to table order if code not available)
                            String tableCodeForKds = "";
                            if (orderId != null) {
                                try {
                                    tableCodeForKds = getTableCodeFromOrder(orderedItem.getOrder());
                                } catch (Exception e) {
                                    log.debug("Could not get table code for KDS notification: {}", e.getMessage());
                                }
                            }
                            
                            // Try to get table ID and table code using repository if available
                            if (orderId != null && orderRepository != null) {
                                try {
                                    java.util.Optional<java.util.UUID> tableIdOpt = orderRepository.findTableIdByOrderId(orderId);
                                    if (tableIdOpt.isPresent()) {
                                        kdsData.put(KEY_TABLE_ID, tableIdOpt.get().toString());
                                    }
                                    java.util.Optional<String> tableCodeOpt = orderRepository.findTableCodeByOrderId(orderId);
                                    if (tableCodeOpt.isPresent()) {
                                        kdsData.put(KEY_TABLE_CODE, tableCodeOpt.get());
                                    }
                                } catch (Exception e) {
                                    log.debug(LOG_COULD_NOT_GET_TABLE_ID, e.getMessage());
                                }
                            }
                            if (!tableCodeForKds.isEmpty()) {
                                kdsData.put(KEY_TABLE_NUMBER, tableCodeForKds);
                            }
                            
                            if (comments != null && !comments.trim().isEmpty()) {
                                kdsData.put(KEY_COMMENTS, comments);
                            }
                            kdsData.put(KEY_TIMESTAMP, OffsetDateTime.now(ZoneOffset.UTC).format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));

                            java.util.Map<String, Object> kdsDataBase = new java.util.HashMap<>(kdsData);
                            
                            boolean publishedRabbitMq = false;
                            for (KdsItemRecipient rec : kdsRecipients) {
                                com.gulfnet.shared_library.entity.User kdsUser = rec.user();
                                if (kdsUser == null) {
                                    continue;
                                }
                                java.util.Map<String, Object> kdsPayload = new java.util.HashMap<>(kdsDataBase);
                                putTargetKdsIdsOnKdsDataMap(kdsPayload, rec.targetKdsStationIds());
                                Locale kdsLoc = localeForKdsRecipient(kdsUser, userLocale);
                                Map<String, String> itemDataKds;
                                NotificationMessage kdsMessage;
                                if (isApproved) {
                                    itemDataKds = buildItemData(orderedItem, NOTIF_CANCELLATION_APPROVED, comments, kdsLoc);
                                    itemDataKds.put(KEY_IS_APPROVED, "true");
                                    kdsMessage = notificationBuilder.buildMessage(
                                            NotificationTemplate.Templates.CANCELLATION_APPROVED,
                                            kdsLoc,
                                            new Object[]{
                                                    getItemName(orderedItem.getItem(), kdsLoc),
                                                    tableCode,
                                                    comments != null ? comments : ""
                                            },
                                            itemDataKds
                                    );
                                } else {
                                    itemDataKds = buildItemData(orderedItem, NOTIF_CANCELLATION_REJECTED, comments, kdsLoc);
                                    itemDataKds.put(KEY_IS_APPROVED, VALUE_FALSE);
                                    kdsMessage = notificationBuilder.buildMessage(
                                            NotificationTemplate.Templates.CANCELLATION_REJECTED,
                                            kdsLoc,
                                            new Object[]{
                                                    getItemName(orderedItem.getItem(), kdsLoc),
                                                    tableCode,
                                                    comments != null ? comments : ""
                                            },
                                            itemDataKds
                                    );
                                }
                                attachTargetKdsIdsToNotificationMessageData(kdsMessage, rec.targetKdsStationIds());
                                com.gulfnet.shared_library.model.request.StatusEventMessage eventMessage =
                                        com.gulfnet.shared_library.model.request.StatusEventMessage.builder()
                                                .title(kdsMessage.getTitle())
                                                .message(kdsMessage.getBody())
                                                .notificationType(kdsNotificationType)
                                                .orderId(orderedItem.getOrder().getId().toString())
                                                .userId(kdsUser.getId().toString())
                                                .status(itemStatusForKds)
                                                .data(kdsPayload)
                                                .build();
                                try {
                                    // User-scoped: /user/topic/restaurant/{restaurantId}/kds/item-cancellation
                                    messagingTemplate.convertAndSendToUser(kdsUser.getId().toString(), kdsTopic, eventMessage);
                                    log.info("[KDS WebSocket] delivered {} user-scoped kdsUserId={}, orderedItemId={}, approved={}, stompSubscribeHint=/user{}",
                                            kdsNotificationType, kdsUser.getId(), orderedItem.getId(), isApproved, kdsTopic);
                                } catch (Exception e) {
                                    log.warn("Failed to send KDS WebSocket notification to user {} for item cancellation decision: {}",
                                            kdsUser.getId(), e.getMessage());
                                }
                                if (rabbitTemplate != null && !publishedRabbitMq) {
                                    try {
                                        Map<String, Object> wsMessage = new HashMap<>();
                                        wsMessage.put(KEY_TOPIC, kdsTopic);
                                        if (eventMessage.getTitle() != null && !eventMessage.getTitle().isEmpty()) {
                                            wsMessage.put(KEY_TITLE, eventMessage.getTitle());
                                        }
                                        wsMessage.put(KEY_MESSAGE, eventMessage.getMessage());
                                        wsMessage.put(KEY_NOTIFICATION_TYPE, eventMessage.getNotificationType());
                                        wsMessage.put(KEY_ORDER_ID, eventMessage.getOrderId());
                                        wsMessage.put(KEY_STATUS, eventMessage.getStatus());
                                        wsMessage.put(KEY_DATA, eventMessage.getData());
                                        wsMessage.put(KEY_TIMESTAMP, OffsetDateTime.now(ZoneOffset.UTC).format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
                                        wsMessage.put(KEY_TYPE, TYPE_WEBSOCKET_NOTIFICATION);
                                        rabbitTemplate.convertAndSend(WEBSOCKET_TOPIC_EXCHANGE, WEBSOCKET_ROUTING_KEY, wsMessage);
                                        publishedRabbitMq = true;
                                        log.info("[Notification][FCM] rabbitPublish exchange={} routingKey={} payloadWsTopic={} notificationType={} (KDS cancellation decision, deduped)",
                                                WEBSOCKET_TOPIC_EXCHANGE, WEBSOCKET_ROUTING_KEY, kdsTopic, kdsNotificationType);
                                    } catch (Exception e) {
                                        log.warn("Failed to publish KDS item cancellation WebSocket message to RabbitMQ: {}", e.getMessage());
                                    }
                                }
                                try {
                                    saveNotificationToDatabase(kdsUser, kdsMessage, kdsNotificationType, null);
                                } catch (Exception e) {
                                    log.error("Failed to save cancellation decision notification for KDS user {}: {}",
                                            kdsUser.getId(), e.getMessage());
                                }
                            }
                            
                            log.debug("Sent KDS WebSocket notification for item cancellation {} to {} KDS user(s) via topic {}",
                                    isApproved ? "approval" : "declined", kdsRecipients.size(), kdsTopic);
                        } catch (Exception e) {
                            log.warn("Failed to send KDS WebSocket notification for item cancellation decision: {}", e.getMessage());
                        }
                    }
                    if (!kdsRecipients.isEmpty()) {
                        log.info("Saved cancellation decision notification to {} category-specific KDS user(s) for item {} (restaurant: {})",
                                kdsRecipients.size(), orderedItem.getItem().getId(), restaurantId != null ? restaurantId.toString() : LOG_PLACEHOLDER_UNKNOWN);
                    }
                } catch (Exception e) {
                    log.warn("Failed to save item cancellation decision notification for KDS users: {}", e.getMessage());
                }
            }
            
        } catch (Exception e) {
            log.error("Failed to send cancellation decision notification to waiter {}: {}", waiter.getId(), e.getMessage(), e);
        }
    }
    
    /**
     * Sends notifications to managers when a cancellation request is opened for a combo.
     * Notifies all provided managers about the pending combo cancellation request.
     *
     * @param orderedCombo The ordered combo for which cancellation is requested
     * @param managers List of managers to notify (must not be null or empty)
     * @param requester The user who requested the cancellation
     * @param userLocale Locale for message localization
     */
    @Override
    public void notifyComboCancellationRequestOpened(com.gulfnet.shared_library.entity.OrderedCombo orderedCombo, List<User> managers, User requester, Locale userLocale) {
        if (managers == null || managers.isEmpty()) {
            log.warn("No managers provided for combo cancellation request notification");
            return;
        }

        try {
            User createdBy = requester != null ? requester : orderedCombo.getCancellationRequestedBy();
            for (User manager : managers) {
                if (manager == null) {
                    continue;
                }
                Locale loc = localeForRecipient(manager, userLocale);
                Map<String, String> comboData = buildComboData(orderedCombo, NOTIF_CANCEL_REQUEST_OPENED, null, loc);
                comboData.put(KEY_REQUEST_ID, orderedCombo.getId().toString());
                String comboName = getComboName(orderedCombo.getCombo(), loc);
                String tableCode = getTableCodeFromOrder(orderedCombo.getOrder());
                NotificationMessage message = notificationBuilder.buildMessage(
                        NotificationTemplate.Templates.MANAGER_CANCEL_REQUEST_NOTIFICATION,
                        loc,
                        new Object[]{comboName, tableCode},
                        comboData
                );
                sendToUser(manager, message, MANAGER_TOPIC);
                saveNotificationToDatabase(manager, message, "COMBO_CANCELLATION_REQUEST", createdBy);
            }
            
            // Send list refresh events for requests to managers
            sendListRefreshEventToUsers(managers, LIST_REQUESTS);

        } catch (Exception e) {
            log.error("Failed to send combo cancellation request notification to managers: {}", e.getMessage(), e);
        }
    }
    
    /**
     * Sends a notification to the waiter about the manager's decision on a combo cancellation request.
     * Uses different topics for cashiers vs waiters.
     *
     * @param orderedCombo The ordered combo for which cancellation was requested
     * @param waiter The waiter who requested the cancellation (must not be null)
     * @param isApproved Whether the cancellation was approved or rejected
     * @param comments Manager's comments on the decision
     * @param userLocale Locale for message localization
     */
    @Override
    public void notifyComboCancellationDecision(com.gulfnet.shared_library.entity.OrderedCombo orderedCombo, User waiter, boolean isApproved, String comments, Locale userLocale) {
        if (waiter == null) {
            log.warn("Cannot send combo cancellation decision notification: waiter is null");
            return;
        }
        
        try {
            Locale loc = localeForWaiterRecipient(waiter, userLocale);
            Map<String, String> comboData;
            NotificationMessage message;
            
            String tableCode = orderedCombo.getOrder() != null ? getTableCodeFromOrder(orderedCombo.getOrder()) : "";
            if (isApproved) {
                comboData = buildComboData(orderedCombo, NOTIF_CANCELLATION_APPROVED, comments, loc);
                comboData.put(KEY_IS_APPROVED, "true");
                message = notificationBuilder.buildMessage(
                        NotificationTemplate.Templates.CANCELLATION_APPROVED,
                        loc,
                        new Object[]{
                                getComboName(orderedCombo.getCombo(), loc),
                                tableCode,
                                comments != null ? comments : ""
                        },
                        comboData
                );
            } else {
                comboData = buildComboData(orderedCombo, NOTIF_CANCELLATION_REJECTED, comments, loc);
                comboData.put(KEY_IS_APPROVED, VALUE_FALSE);
                message = notificationBuilder.buildMessage(
                        NotificationTemplate.Templates.CANCELLATION_REJECTED,
                        loc,
                        new Object[]{
                                getComboName(orderedCombo.getCombo(), loc),
                                tableCode,
                                comments != null ? comments : ""
                        },
                        comboData
                );
            }

            String notificationType = isApproved ? NOTIF_CANCELLATION_APPROVED : NOTIF_CANCELLATION_REJECTED;
            
            // Check if requester is cashier - send to CASHIER_TOPIC
            if (isCashier(waiter)) {
                // Send via WebSocket to cashier (Windows app doesn't support FCM)
                sendWebSocketNotification(waiter.getId(), CASHIER_TOPIC, message, null, notificationType);
                log.info("Sent combo cancellation decision notification to cashier {} via CASHIER_TOPIC", waiter.getId());
            } else {
                // Send to waiter topic (for FCM push notifications)
                if (waiter.getDeviceToken() != null) {
                    sendToUser(waiter, message, WAITER_TOPIC);
                } else {
                    // If no device token, still send via WebSocket to waiter topic
                    sendWebSocketNotification(waiter.getId(), WAITER_TOPIC, message, null, notificationType);
                }
                log.info("Sent combo cancellation decision notification to waiter {} via WAITER_TOPIC", waiter.getId());
            }
            
            // Save notification to database
            saveNotificationToDatabase(waiter, message, notificationType, null);

            // Also notify KDS users when combo cancellation request is approved/declined.
            // This mirrors item cancellation decision behavior so kitchen receives the same decision signal.
            if (orderedCombo.getOrder() != null) {
                try {
                    java.util.UUID restaurantId = null;
                    try {
                        restaurantId = getRestaurantIdFromOrder(orderedCombo.getOrder());
                    } catch (Exception e) {
                        log.debug(LOG_COULD_NOT_GET_RESTAURANT_ID, e.getMessage());
                    }

                    if (restaurantId == null) {
                        log.warn("[KDS WebSocket] notifyComboCancellationDecision: restaurantId null for orderedComboId={} — skipping KDS WebSocket",
                                orderedCombo.getId());
                    } else {
                        java.util.List<KdsItemRecipient> comboRecipientsAcc = new java.util.ArrayList<>();
                        try {
                            if (orderedItemRepository != null) {
                                java.util.List<com.gulfnet.shared_library.entity.OrderedItem> comboItems =
                                        orderedItemRepository.findByOrderedComboId(orderedCombo.getId());
                                for (com.gulfnet.shared_library.entity.OrderedItem comboItem : comboItems) {
                                    if (comboItem != null && comboItem.getItem() != null) {
                                        comboRecipientsAcc.addAll(findKdsRecipientsForItem(comboItem.getItem(), restaurantId));
                                    }
                                }
                            }
                        } catch (Exception e) {
                            log.warn("[KDS WebSocket] notifyComboCancellationDecision: failed resolving KDS users for combo {}: {}",
                                    orderedCombo.getId(), e.getMessage());
                        }

                        java.util.List<KdsItemRecipient> mergedComboRecipients = mergeKdsRecipientsByUser(comboRecipientsAcc);
                        if (mergedComboRecipients.isEmpty()) {
                            log.warn("[KDS WebSocket] notifyComboCancellationDecision: no KDS users resolved for orderedComboId={}, restaurantId={}",
                                    orderedCombo.getId(), restaurantId);
                        } else {
                            String kdsTopic = RESTAURANT_TOPIC_PREFIX + restaurantId + "/kds/item-cancellation";
                            String kdsNotificationType = isApproved
                                    ? "COMBO_CANCELLATION_APPROVED"
                                    : "COMBO_CANCELLATION_DECLINED";

                            for (KdsItemRecipient rec : mergedComboRecipients) {
                                com.gulfnet.shared_library.entity.User kdsUser = rec.user();
                                if (kdsUser == null || kdsUser.getId() == null) {
                                    continue;
                                }
                                try {
                                    java.util.Map<String, Object> kdsData = new java.util.HashMap<>();
                                    kdsData.put("orderedComboId", orderedCombo.getId().toString());
                                    if (orderedCombo.getCombo() != null && orderedCombo.getCombo().getComboId() != null) {
                                        kdsData.put("comboId", orderedCombo.getCombo().getComboId().toString());
                                    }
                                    if (orderedCombo.getOrder() != null && orderedCombo.getOrder().getId() != null) {
                                        kdsData.put(KEY_ORDER_ID, orderedCombo.getOrder().getId().toString());
                                    }
                                    kdsData.put(KEY_RESTAURANT_ID, restaurantId.toString());
                                    kdsData.put(KEY_NOTIFICATION_TYPE, kdsNotificationType);
                                    kdsData.put(KEY_IS_APPROVED, Boolean.toString(isApproved));
                                    kdsData.put(KEY_TIMESTAMP, OffsetDateTime.now(ZoneOffset.UTC).format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
                                    putTargetKdsIdsOnKdsDataMap(kdsData, rec.targetKdsStationIds());

                                    com.gulfnet.shared_library.model.request.StatusEventMessage eventMessage =
                                            com.gulfnet.shared_library.model.request.StatusEventMessage.builder()
                                            .title(message.getTitle())
                                            .message(message.getBody())
                                            .notificationType(kdsNotificationType)
                                            .orderId(orderedCombo.getOrder() != null && orderedCombo.getOrder().getId() != null
                                                    ? orderedCombo.getOrder().getId().toString() : "")
                                            .userId(kdsUser.getId().toString())
                                            .status(isApproved ? "APPROVED" : "DECLINED")
                                            .data(kdsData)
                                            .build();

                                    messagingTemplate.convertAndSendToUser(kdsUser.getId().toString(), kdsTopic, eventMessage);
                                    log.info("[KDS WebSocket] delivered COMBO_CANCELLATION decision user-scoped kdsUserId={}, orderedComboId={}, decision={}",
                                            kdsUser.getId(), orderedCombo.getId(), isApproved ? "approved" : "declined");
                                } catch (Exception e) {
                                    log.warn("[KDS WebSocket] failed to notify KDS user {} for combo {} decision: {}",
                                            kdsUser.getId(), orderedCombo.getId(), e.getMessage());
                                }
                            }
                        }
                    }
                } catch (Exception e) {
                    log.warn("Failed to send KDS notifications for combo cancellation decision: {}", e.getMessage());
                }
            }
            
        } catch (Exception e) {
            log.error("Failed to send combo cancellation decision notification to waiter {}: {}", waiter.getId(), e.getMessage(), e);
        }
    }

    // ==================== TRANSACTION NOTIFICATIONS ====================
    
    /**
     * Sends notifications to managers when a transaction cancellation request is opened.
     * Notifies all provided managers about the pending transaction cancellation request.
     *
     * @param transaction The transaction for which cancellation is requested
     * @param managers List of managers to notify (must not be null or empty)
     * @param requester The user who requested the cancellation
     * @param userLocale Locale for message localization
     */
    @Override
    public void notifyTransactionCancellationRequestOpened(com.gulfnet.shared_library.entity.Transaction transaction, List<User> managers, User requester, Locale userLocale) {
        if (managers == null || managers.isEmpty()) {
            log.warn("No managers provided for transaction cancellation request notification");
            return;
        }

        try {
            User createdBy = requester != null ? requester : transaction.getRequestedBy();
            for (User manager : managers) {
                if (manager == null) {
                    continue;
                }
                Locale loc = localeForRecipient(manager, userLocale);
                Map<String, String> transactionData = buildTransactionData(transaction, NOTIF_CANCEL_REQUEST_OPENED, null, loc);
                String transactionNumber = transaction.getTransactionNumber() != null ? transaction.getTransactionNumber() : "";
                String tableCode = transaction.getOrder() != null ? getTableCodeFromOrder(transaction.getOrder()) : "";
                NotificationMessage message = notificationBuilder.buildMessage(
                        NotificationTemplate.Templates.MANAGER_TRANSACTION_CANCEL_REQUEST_NOTIFICATION,
                        loc,
                        new Object[]{transactionNumber, tableCode},
                        transactionData
                );
                sendToUser(manager, message, MANAGER_TOPIC);
                saveNotificationToDatabase(manager, message, "TRANSACTION_CANCELLATION_REQUEST", createdBy);
            }
            
            // Send list refresh events for requests to managers
            sendListRefreshEventToUsers(managers, LIST_REQUESTS);

        } catch (Exception e) {
            log.error("Failed to send transaction cancellation request notification to managers: {}", e.getMessage(), e);
        }
    }

    /**
     * Sends notifications to managers when a refund request is opened.
     * Notifies all provided managers about the pending refund request.
     *
     * @param transaction The transaction for which refund is requested
     * @param managers List of managers to notify (must not be null or empty)
     * @param requester The user who requested the refund
     * @param userLocale Locale for message localization
     */
    @Override
    public void notifyRefundRequestOpened(com.gulfnet.shared_library.entity.Transaction transaction, List<User> managers, User requester, Locale userLocale) {
        if (managers == null || managers.isEmpty()) {
            log.warn("No managers provided for refund request notification");
            return;
        }

        try {
            User createdBy = requester != null ? requester : transaction.getRequestedBy();
            for (User manager : managers) {
                if (manager == null) {
                    continue;
                }
                Locale loc = localeForRecipient(manager, userLocale);
                Map<String, String> transactionData = buildTransactionData(transaction, "REFUND_REQUEST_OPENED", null, loc);
                String transactionNumber = transaction.getTransactionNumber() != null ? transaction.getTransactionNumber() : "";
                String tableCode = transaction.getOrder() != null ? getTableCodeFromOrder(transaction.getOrder()) : "";
                NotificationMessage message = notificationBuilder.buildMessage(
                        NotificationTemplate.Templates.MANAGER_REFUND_REQUEST_NOTIFICATION,
                        loc,
                        new Object[]{transactionNumber, tableCode},
                        transactionData
                );
                sendToUser(manager, message, MANAGER_TOPIC);
                saveNotificationToDatabase(manager, message, "REFUND_REQUEST", createdBy);
            }
            
            // Send list refresh events for requests to managers
            sendListRefreshEventToUsers(managers, LIST_REQUESTS);

        } catch (Exception e) {
            log.error("Failed to send refund request notification to managers: {}", e.getMessage(), e);
        }
    }
    
    /**
     * Sends a notification to the requester about the manager's decision on a transaction cancellation request.
     * Uses different topics based on the requester's role (cashier vs waiter).
     *
     * @param transaction The transaction for which cancellation was requested
     * @param requester The user who requested the cancellation (must not be null)
     * @param isApproved Whether the cancellation was approved or rejected
     * @param comments Manager's comments on the decision
     * @param userLocale Locale for message localization
     */
    @Override
    public void notifyTransactionCancellationDecision(com.gulfnet.shared_library.entity.Transaction transaction, User requester, boolean isApproved, String comments, Locale userLocale) {
        if (requester == null) {
            log.warn("Cannot send transaction cancellation decision notification: requester is null");
            return;
        }
        
        try {
            Locale loc = localeForRecipient(requester, userLocale);
            Map<String, String> transactionData;
            NotificationMessage message;
            
            String tableCode = transaction.getOrder() != null ? getTableCodeFromOrder(transaction.getOrder()) : "";
            if (isApproved) {
                transactionData = buildTransactionData(transaction, NOTIF_CANCELLATION_APPROVED, comments, loc);
                transactionData.put(KEY_IS_APPROVED, "true");
                message = notificationBuilder.buildMessage(
                        NotificationTemplate.Templates.CANCELLATION_APPROVED,
                        loc,
                        new Object[]{
                                transaction.getTransactionNumber() != null ? transaction.getTransactionNumber() : transaction.getId().toString(),
                                tableCode,
                                comments != null ? comments : ""
                        },
                        transactionData
                );
            } else {
                transactionData = buildTransactionData(transaction, NOTIF_CANCELLATION_REJECTED, comments, loc);
                transactionData.put(KEY_IS_APPROVED, VALUE_FALSE);
                message = notificationBuilder.buildMessage(
                        NotificationTemplate.Templates.CANCELLATION_REJECTED,
                        loc,
                        new Object[]{
                                transaction.getTransactionNumber() != null ? transaction.getTransactionNumber() : transaction.getId().toString(),
                                tableCode,
                                comments != null ? comments : ""
                        },
                        transactionData
                );
            }

            String notificationType = isApproved ? NOTIF_CANCELLATION_APPROVED : NOTIF_CANCELLATION_REJECTED;
            
            // Check if requester is cashier - send to CASHIER_TOPIC
            if (isCashier(requester)) {
                // Send via WebSocket to cashier (Windows app doesn't support FCM)
                sendWebSocketNotification(requester.getId(), CASHIER_TOPIC, message, null, notificationType);
                log.info("Sent transaction cancellation decision notification to cashier {} via CASHIER_TOPIC", requester.getId());
            } else {
                // Send to waiter topic (for FCM push notifications)
                if (requester.getDeviceToken() != null) {
                    sendToUser(requester, message, WAITER_TOPIC);
                } else {
                    // If no device token, still send via WebSocket to waiter topic
                    sendWebSocketNotification(requester.getId(), WAITER_TOPIC, message, null, notificationType);
                }
                log.info("Sent transaction cancellation decision notification to waiter {} via WAITER_TOPIC", requester.getId());
            }
            
            // Save notification to database
            saveNotificationToDatabase(requester, message, notificationType, null);
            
        } catch (Exception e) {
            log.error("Failed to send transaction cancellation decision notification to requester {}: {}", requester.getId(), e.getMessage(), e);
        }
    }

    /**
     * Sends a notification to the waiter when a transaction is cancelled.
     * Includes transaction details and table information.
     *
     * @param transaction The transaction that was cancelled
     * @param waiter The waiter assigned to the order (must not be null)
     * @param userLocale Locale for message localization
     */
    @Override
    public void notifyTransactionCancelledForWaiter(com.gulfnet.shared_library.entity.Transaction transaction, User waiter, Locale userLocale) {
        if (waiter == null) {
            log.warn("Cannot send transaction cancelled notification: waiter is null");
            return;
        }

        log.info("[Notification] notifyTransactionCancelledForWaiter waiterId={} transactionId={} webSocketTopic={} deviceToken={}",
                waiter.getId(), transaction.getId(), WAITER_TOPIC,
                waiter.getDeviceToken() != null && !waiter.getDeviceToken().trim().isEmpty() ? LOG_DEVICE_TOKEN_PRESENT : LOG_DEVICE_TOKEN_ABSENT);

        try {
            Locale loc = localeForWaiterRecipient(waiter, userLocale);
            Map<String, String> transactionData = buildTransactionData(transaction, "TRANSACTION_CANCELLED_FOR_WAITER", null, loc);
            transactionData.put(KEY_IS_APPROVED, "true");

            String tableCode = transaction.getOrder() != null ? getTableCodeFromOrder(transaction.getOrder()) : "";
            // Use human-readable identifier: transaction number, then order number, never raw UUID
            String identifier = getTransactionIdentifierForNotification(transaction);

            NotificationMessage message = notificationBuilder.buildMessage(
                    NotificationTemplate.Templates.CANCELLATION_APPROVED,
                    loc,
                    new Object[]{
                            identifier,
                            tableCode,
                            "" // no comments for waiter, they just need to know it was cancelled
                    },
                    transactionData
            );

            // Waiter-specific message: "Transaction cancelled" (not "your request approved" — waiter did not request it)
            String title = messageUtil.getMessage("notification.transaction.cancelled.for.waiter.title", loc);
            String body = messageUtil.getMessage("notification.transaction.cancelled.for.waiter.body", loc,
                    identifier, tableCode);
            message.setTitle(title);
            message.setBody(body);

            sendToUser(waiter, message, WAITER_TOPIC);

            // Save notification to database for waiter with a dedicated type
            saveNotificationToDatabase(waiter, message, "TRANSACTION_CANCELLED_FOR_WAITER", null);
            log.info("[Notification] notifyTransactionCancelledForWaiter completed waiterId={} notificationType=TRANSACTION_CANCELLED_FOR_WAITER", waiter.getId());
        } catch (Exception e) {
            log.error("Failed to send transaction cancelled notification to waiter {}: {}", waiter.getId(), e.getMessage(), e);
        }
    }

    // ==================== ORDER NOTIFICATIONS ====================
    
    /**
     * Sends a notification to the waiter when an order is placed.
     * Includes order number and table code in the notification.
     *
     * @param order The order that was placed
     * @param waiter The waiter assigned to the order (must not be null)
     * @param userLocale Locale for message localization
     */
    @Override
    public void notifyOrderPlaced(com.gulfnet.shared_library.entity.Order order, User waiter, Locale userLocale) {
        if (waiter == null) {
            log.warn("Cannot send order placed notification: waiter is null");
            return;
        }
        
        try {
            Locale loc = localeForWaiterRecipient(waiter, userLocale);
            Map<String, String> orderData = buildOrderData(order, NOTIF_ORDER_PLACED, null, loc);
            
            String tableCode = getTableCodeFromOrder(order);
            NotificationMessage message = notificationBuilder.buildMessage(
                    NotificationTemplate.Templates.ORDER_PLACED,
                    loc,
                    new Object[]{
                            order.getOrderNumber() != null ? order.getOrderNumber() : order.getId().toString(),
                            tableCode
                    },
                    orderData
            );

            sendToUser(waiter, message, WAITER_TOPIC);
            
            // Save notification to database for waiter
            saveNotificationToDatabase(waiter, message, NOTIF_ORDER_PLACED, null);
            
            // Don't send notification to KDS for order placed - KDS users change item statuses themselves
            
        } catch (Exception e) {
            log.error("Failed to send order placed notification to waiter {}: {}", waiter.getId(), e.getMessage(), e);
        }
    }
    
    /**
     * Sends a notification to the waiter when an order is updated.
     * Includes order number and table code in the notification.
     *
     * @param order The order that was updated
     * @param waiter The waiter assigned to the order (must not be null)
     * @param userLocale Locale for message localization
     */
    @Override
    public void notifyOrderUpdated(com.gulfnet.shared_library.entity.Order order, User waiter, Locale userLocale) {
        if (waiter == null) {
            log.warn("Cannot send order updated notification: waiter is null");
            return;
        }
        
        try {
            Locale loc = localeForWaiterRecipient(waiter, userLocale);
            Map<String, String> orderData = buildOrderData(order, NOTIF_ORDER_UPDATED, null, loc);
            
            String tableCode = getTableCodeFromOrder(order);
            NotificationMessage message = notificationBuilder.buildMessage(
                    NotificationTemplate.Templates.ORDER_UPDATED,
                    loc,
                    new Object[]{
                            order.getOrderNumber() != null ? order.getOrderNumber() : order.getId().toString(),
                            tableCode
                    },
                    orderData
            );

            sendToUser(waiter, message, WAITER_TOPIC);
            
            // Save notification to database
            saveNotificationToDatabase(waiter, message, NOTIF_ORDER_UPDATED, null);
            
        } catch (Exception e) {
            log.error("Failed to send order updated notification to waiter {}: {}", waiter.getId(), e.getMessage(), e);
        }
    }
    
    /**
     * Sends a notification to the waiter when an order is cancelled.
     * Includes order number and table code in the notification.
     *
     * @param order The order that was cancelled
     * @param waiter The waiter assigned to the order (must not be null)
     * @param userLocale Locale for message localization
     */
    @Override
    public void notifyOrderCancelled(com.gulfnet.shared_library.entity.Order order, User waiter, Locale userLocale) {
        if (waiter == null) {
            log.warn("Cannot send order cancelled notification: waiter is null");
            return;
        }
        
        try {
            Locale loc = localeForWaiterRecipient(waiter, userLocale);
            Map<String, String> orderData = buildOrderData(order, "ORDER_CANCELLED", null, loc);
            
            String tableCode = getTableCodeFromOrder(order);
            NotificationMessage message = notificationBuilder.buildMessage(
                    NotificationTemplate.Templates.ORDER_CANCELLED,
                    loc,
                    new Object[]{
                            order.getOrderNumber() != null ? order.getOrderNumber() : order.getId().toString(),
                            tableCode
                    },
                    orderData
            );

            sendToUser(waiter, message, WAITER_TOPIC);
            
            // Save notification to database
            saveNotificationToDatabase(waiter, message, "ORDER_CANCELLED", null);
            
        } catch (Exception e) {
            log.error("Failed to send order cancelled notification to waiter {}: {}", waiter.getId(), e.getMessage(), e);
        }
    }
    
    /**
     * Sends notifications to managers when an order cancellation request is opened.
     * Notifies all provided managers about the pending order cancellation request.
     *
     * @param order The order for which cancellation is requested
     * @param managers List of managers to notify (must not be null or empty)
     * @param requester The user who requested the cancellation
     * @param userLocale Locale for message localization
     */
    @Override
    public void notifyOrderCancellationRequestOpened(com.gulfnet.shared_library.entity.Order order, List<User> managers, User requester, Locale userLocale) {
        if (managers == null || managers.isEmpty()) {
            log.warn("No managers provided for order cancellation request notification");
            return;
        }

        try {
            User createdBy = requester != null ? requester : order.getCancellationRequestedBy();
            for (User manager : managers) {
                if (manager == null) {
                    continue;
                }
                Locale loc = localeForRecipient(manager, userLocale);
                Map<String, String> orderData = buildOrderData(order, NOTIF_CANCEL_REQUEST_OPENED, null, loc);
                String orderNumber = order.getOrderNumber() != null ? order.getOrderNumber() : "";
                String tableCode = getTableCodeFromOrder(order);
                NotificationMessage message = notificationBuilder.buildMessage(
                        NotificationTemplate.Templates.MANAGER_ORDER_CANCEL_REQUEST_NOTIFICATION,
                        loc,
                        new Object[]{orderNumber, tableCode},
                        orderData
                );
                sendToUser(manager, message, MANAGER_TOPIC);
                saveNotificationToDatabase(manager, message, "ORDER_CANCELLATION_REQUEST", createdBy);
            }
            
            // Send list refresh events for requests to managers
            sendListRefreshEventToUsers(managers, LIST_REQUESTS);

        } catch (Exception e) {
            log.error("Failed to send order cancellation request notification to managers: {}", e.getMessage(), e);
        }
    }
    
    /**
     * Sends a notification to the requester about the manager's decision on an order cancellation request.
     * Also notifies KDS users if the cancellation is approved.
     * Uses different topics based on the requester's role.
     *
     * @param order The order for which cancellation was requested
     * @param requester The user who requested the cancellation (can be null)
     * @param isApproved Whether the cancellation was approved or rejected
     * @param comments Manager's comments on the decision
     * @param userLocale Locale for message localization
     */
    @Override
    public void notifyOrderCancellationDecision(com.gulfnet.shared_library.entity.Order order, User requester, boolean isApproved, String comments, Locale userLocale) {
        log.info("notifyOrderCancellationDecision called: orderId={}, isApproved={}, requester={}", 
                order != null ? order.getId() : "null", isApproved, requester != null ? requester.getId() : "null");
        
        if (order == null) {
            log.warn("Cannot send order cancellation decision notification: order is null");
            return;
        }
        
        try {
            // Safely get order number - fallback to order ID if order number is null or "null"
            String orderNumber = order.getOrderNumber() != null && !order.getOrderNumber().trim().isEmpty() 
                    && !"null".equalsIgnoreCase(order.getOrderNumber()) 
                    ? order.getOrderNumber() 
                    : (order.getId() != null ? order.getId().toString() : "");
            
            // Safely get table code - use helper method to handle lazy loading, falls back to table order if code not available
            String tableCode = getTableCodeFromOrder(order);
            
            // Safely get comments - use empty string if null
            String notificationComments = comments != null && !comments.trim().isEmpty() ? comments : "";
            
            if (requester == null) {
                log.debug("Requester is null for order cancellation decision; requester FCM is handled in UserServiceImpl when applicable. Sending KDS notifications only.");
            }
            // For waiters with a requester, FCM + DB are handled in UserServiceImpl via saveOrderCancellationRequestNotification()
            // to avoid duplicate mobile notifications; this method only persists/sends KDS per-user below.
            
            // Also notify KDS users assigned to this order when manager approves/declines cancellation request
            if (order != null) {
                try {
                    // Get restaurant ID for KDS notification using helper method
                    java.util.UUID restaurantId = null;
                    try {
                        restaurantId = getRestaurantIdFromOrder(order);
                    } catch (Exception e) {
                        log.debug(LOG_COULD_NOT_GET_RESTAURANT_ID, e.getMessage());
                    }
                    
                    java.util.List<KdsItemRecipient> kdsRecipients = new java.util.ArrayList<>();
                    int kdsStationCount = 0;
                    if (restaurantId != null) {
                        kdsRecipients = findKdsRecipientsForOrder(order);
                        java.util.Set<java.util.UUID> distinctTargets = new java.util.HashSet<>();
                        for (KdsItemRecipient r : kdsRecipients) {
                            if (r.targetKdsStationIds() != null) {
                                distinctTargets.addAll(r.targetKdsStationIds());
                            }
                        }
                        kdsStationCount = distinctTargets.size();
                        log.debug("Order Cancellation KDS Notification - Order ID: {}, Restaurant ID: {}, distinctTargetKdsStations: {}, KDS Recipients: {}",
                                order.getId(), restaurantId, kdsStationCount, kdsRecipients.size());
                    }
                    
                    String kdsNotificationType = isApproved ? NOTIF_ORDER_CANCELLATION_APPROVED : "ORDER_CANCELLATION_DECLINED";

                    // Per-recipient locale for KDS DB + WebSocket (aligned with notification listing)
                    Map<String, Object> kdsDataForWs = null;
                    String kdsTopic = null;
                    if (isApproved && restaurantId != null) {
                        kdsTopic = RESTAURANT_TOPIC_PREFIX + restaurantId + "/kds/order-cancellation";
                        kdsDataForWs = new HashMap<>();
                        kdsDataForWs.put(KEY_ORDER_ID, order.getId().toString());
                        kdsDataForWs.put(KEY_ORDER_NUMBER, orderNumber);
                        kdsDataForWs.put(KEY_ORDER_STATUS, com.gulfnet.shared_library.enums.OrderStatus.CANCELED.toString());
                        kdsDataForWs.put(KEY_RESTAURANT_ID, restaurantId.toString());
                        kdsDataForWs.put(KEY_NOTIFICATION_TYPE, NOTIF_ORDER_CANCELLATION_APPROVED);
                        kdsDataForWs.put(KEY_IS_APPROVED, "true");
                        String tableCodeForKds = getTableCodeFromOrder(order);
                        if (!tableCodeForKds.isEmpty()) {
                            kdsDataForWs.put(KEY_TABLE_NUMBER, tableCodeForKds);
                        }
                        if (orderRepository != null) {
                            try {
                                java.util.Optional<java.util.UUID> tableIdOpt = orderRepository.findTableIdByOrderId(order.getId());
                                if (tableIdOpt.isPresent()) {
                                    kdsDataForWs.put(KEY_TABLE_ID, tableIdOpt.get().toString());
                                }
                                java.util.Optional<String> tableCodeOpt = orderRepository.findTableCodeByOrderId(order.getId());
                                if (tableCodeOpt.isPresent()) {
                                    kdsDataForWs.put(KEY_TABLE_CODE, tableCodeOpt.get());
                                }
                            } catch (Exception e) {
                                log.debug(LOG_COULD_NOT_GET_TABLE_ID, e.getMessage());
                            }
                        }
                        if (order.getWaiter() != null) {
                            kdsDataForWs.put(KEY_WAITER_ID, order.getWaiter().getId().toString());
                            kdsDataForWs.put(KEY_WAITER_NAME, order.getWaiter().getFirstName() + " " + order.getWaiter().getLastName());
                        }
                        if (order.getTotalAmount() != null) {
                            kdsDataForWs.put(KEY_TOTAL_AMOUNT, order.getTotalAmount().toString());
                        }
                        if (order.getSubTotal() != null) {
                            kdsDataForWs.put(KEY_SUB_TOTAL, order.getSubTotal().toString());
                        }
                        if (comments != null && !comments.trim().isEmpty()) {
                            kdsDataForWs.put(KEY_COMMENTS, comments);
                        }
                        kdsDataForWs.put(KEY_TIMESTAMP, OffsetDateTime.now(ZoneOffset.UTC).format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
                    }

                    com.gulfnet.shared_library.model.request.StatusEventMessage firstKdsEventForRabbit = null;
                    for (KdsItemRecipient rec : kdsRecipients) {
                        com.gulfnet.shared_library.entity.User kdsUser = rec.user();
                        if (kdsUser == null || kdsUser.getId() == null) {
                            continue;
                        }
                        try {
                            Locale kdsLoc = localeForKdsRecipient(kdsUser, userLocale);
                            Map<String, String> orderDataKds;
                            NotificationMessage kdsMsg;
                            if (isApproved) {
                                orderDataKds = buildOrderData(order, NOTIF_CANCELLATION_APPROVED, comments, kdsLoc);
                                orderDataKds.put(KEY_IS_APPROVED, "true");
                                kdsMsg = notificationBuilder.buildMessage(
                                        NotificationTemplate.Templates.CANCELLATION_APPROVED,
                                        kdsLoc,
                                        new Object[]{orderNumber, tableCode, notificationComments},
                                        orderDataKds);
                            } else {
                                orderDataKds = buildOrderData(order, NOTIF_CANCELLATION_REJECTED, comments, kdsLoc);
                                orderDataKds.put(KEY_IS_APPROVED, VALUE_FALSE);
                                kdsMsg = notificationBuilder.buildMessage(
                                        NotificationTemplate.Templates.CANCELLATION_REJECTED,
                                        kdsLoc,
                                        new Object[]{orderNumber, tableCode, notificationComments},
                                        orderDataKds);
                            }
                            attachTargetKdsIdsToNotificationMessageData(kdsMsg, rec.targetKdsStationIds());
                            saveNotificationToDatabase(kdsUser, kdsMsg, kdsNotificationType, null);

                            if (isApproved && restaurantId != null && kdsDataForWs != null && kdsTopic != null) {
                                java.util.Map<String, Object> wsData = new java.util.HashMap<>(kdsDataForWs);
                                putTargetKdsIdsOnKdsDataMap(wsData, rec.targetKdsStationIds());
                                com.gulfnet.shared_library.model.request.StatusEventMessage eventMessage =
                                        com.gulfnet.shared_library.model.request.StatusEventMessage.builder()
                                                .title(kdsMsg.getTitle())
                                                .message(kdsMsg.getBody())
                                                .notificationType(NOTIF_ORDER_CANCELLATION_APPROVED)
                                                .orderId(order.getId().toString())
                                                .status(com.gulfnet.shared_library.enums.OrderStatus.CANCELED.toString())
                                                .data(wsData)
                                                .build();
                                messagingTemplate.convertAndSendToUser(kdsUser.getId().toString(), kdsTopic, eventMessage);
                                log.info("[KDS WebSocket] delivered ORDER_CANCELLATION_APPROVED user-scoped kdsUserId={}, orderId={}, stompSubscribeHint=/user{}",
                                        kdsUser.getId(), order.getId(), kdsTopic);
                                if (firstKdsEventForRabbit == null) {
                                    firstKdsEventForRabbit = eventMessage;
                                }
                            }
                        } catch (Exception e) {
                            log.error("Failed to send/save order cancellation KDS notification for user {}: {}",
                                    kdsUser.getId(), e.getMessage());
                        }
                    }
                    if (!isApproved && !kdsRecipients.isEmpty()) {
                        log.info("[KDS WebSocket] notifyOrderCancellationDecision: DECLINED — KDS gets DB notification only; no WebSocket to kds/order-cancellation (orderId={}, kdsUserCount={})",
                                order.getId(), kdsRecipients.size());
                    }

                    if (rabbitTemplate != null && firstKdsEventForRabbit != null && kdsTopic != null) {
                        try {
                            Map<String, Object> wsMessage = new HashMap<>();
                            wsMessage.put(KEY_TOPIC, kdsTopic);
                            if (firstKdsEventForRabbit.getTitle() != null && !firstKdsEventForRabbit.getTitle().isEmpty()) {
                                wsMessage.put(KEY_TITLE, firstKdsEventForRabbit.getTitle());
                            }
                            wsMessage.put(KEY_MESSAGE, firstKdsEventForRabbit.getMessage());
                            wsMessage.put(KEY_NOTIFICATION_TYPE, firstKdsEventForRabbit.getNotificationType());
                            wsMessage.put(KEY_ORDER_ID, firstKdsEventForRabbit.getOrderId());
                            wsMessage.put(KEY_STATUS, firstKdsEventForRabbit.getStatus());
                            wsMessage.put(KEY_DATA, firstKdsEventForRabbit.getData());
                            wsMessage.put(KEY_TIMESTAMP, OffsetDateTime.now(ZoneOffset.UTC).format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
                            wsMessage.put(KEY_TYPE, TYPE_WEBSOCKET_NOTIFICATION);
                            rabbitTemplate.convertAndSend(WEBSOCKET_TOPIC_EXCHANGE, WEBSOCKET_ROUTING_KEY, wsMessage);
                            log.info("[Notification][FCM] rabbitPublish exchange={} routingKey={} payloadWsTopic={} notificationType={}",
                                    WEBSOCKET_TOPIC_EXCHANGE, WEBSOCKET_ROUTING_KEY, kdsTopic, firstKdsEventForRabbit.getNotificationType());
                        } catch (Exception e) {
                            log.warn("Failed to publish KDS order cancellation WebSocket message to RabbitMQ: {}", e.getMessage());
                        }
                    }
                    if (!kdsRecipients.isEmpty()) {
                        log.info("Saved order cancellation decision notification to {} category-specific KDS user(s) for order {} (restaurant: {})",
                                kdsRecipients.size(), order.getId(), restaurantId != null ? restaurantId.toString() : LOG_PLACEHOLDER_UNKNOWN);
                    }
                } catch (Exception e) {
                    log.warn("Failed to save order cancellation decision notification for KDS users: {}", e.getMessage());
                }
            }
            
        } catch (Exception e) {
            log.error("Failed to send order cancellation decision notification{}: {}", 
                    requester != null ? " to requester " + requester.getId() : "", e.getMessage(), e);
        }
    }

    // ==================== TABLE NOTIFICATIONS ====================
    
    /**
     * Sends a notification to the waiter when a table is assigned to them.
     * Reloads user to ensure latest device token is used for FCM notifications.
     *
     * @param table The table that was assigned
     * @param waiter The waiter to whom the table is assigned (must not be null)
     * @param userLocale Locale for message localization
     */
    @Override
    public void notifyTableAssigned(RestaurantTable table, User waiter, Locale userLocale) {
        if (waiter == null) {
            log.warn("Cannot send table assigned notification: waiter is null");
            return;
        }
        
        try {
            // Reload user to ensure we have the latest device token
            String deviceToken = waiter.getDeviceToken();
            if ((deviceToken == null || deviceToken.trim().isEmpty()) && userRepository != null) {
                try {
                    log.info("Reloading user {} to fetch device token for table assignment notification", waiter.getId());
                    User reloadedUser = userRepository.findById(waiter.getId()).orElse(null);
                    if (reloadedUser != null) {
                        deviceToken = reloadedUser.getDeviceToken();
                        log.info(LOG_RELOADED_USER_DEVICE_TOKEN, waiter.getId(), 
                                deviceToken != null && !deviceToken.trim().isEmpty() ? STATUS_FOUND : STATUS_NOT_FOUND);
                    }
                } catch (Exception e) {
                    log.warn(LOG_FAILED_TO_RELOAD_USER, waiter.getId(), e.getMessage());
                }
            }
            
            // Normalize device token (handle empty strings as null)
            if (deviceToken != null && deviceToken.trim().isEmpty()) {
                deviceToken = null;
            }
            
            if (deviceToken == null || deviceToken.trim().isEmpty()) {
                log.warn("Cannot send table assigned notification: waiter {} has no device token", waiter.getId());
                // Still save to database even if FCM cannot be sent
            }
            
            Locale loc = localeForWaiterRecipient(waiter, userLocale);
            Map<String, String> tableData = buildTableData(table, NOTIF_TABLE_ASSIGNED, loc);
            
            boolean isTakeawayTable = Boolean.TRUE.equals(table.getIsVirtual());
            NotificationTemplate template = isTakeawayTable
                    ? NotificationTemplate.Templates.TABLE_ASSIGNED_TAKEAWAY
                    : NotificationTemplate.Templates.TABLE_ASSIGNED;
            Object[] messageArgs = isTakeawayTable
                    ? new Object[0]
                    : new Object[]{
                            table.getTableOrder().toString(),
                            getSectionName(table.getRestaurantRow().getRestaurantSection(), loc)
                    };

            NotificationMessage message = notificationBuilder.buildMessage(
                    template,
                    loc,
                    messageArgs,
                    tableData
            );

            // Only send real-time notification if waiter has active (non-expired) session
            if (hasActiveSession(waiter.getId())) {
                sendWebSocketNotification(waiter.getId(), TABLE_UPDATE_TOPIC, message, deviceToken);
            } else {
                log.info("Skipping table assigned notification for waiter {} - session expired or logged out", waiter.getId());
            }
            
            // Save notification to database (waiter can see it when they log in again)
            saveNotificationToDatabase(waiter, message, NOTIF_TABLE_ASSIGNED, null);
            
            log.info("Table assignment notification sent to waiter {} for table {}", waiter.getId(), table.getTableOrder());
            
        } catch (Exception e) {
            log.error("Failed to send table assigned notification to waiter {}: {}", waiter.getId(), e.getMessage(), e);
        }
    }
    
    /**
     * Sends a notification to the waiter when a table assignment is removed.
     * Reloads user to ensure latest device token is used for FCM notifications.
     *
     * @param table The table that was removed from the waiter
     * @param waiter The waiter from whom the table was removed (must not be null)
     * @param userLocale Locale for message localization
     */
    @Override
    public void notifyTableRemoved(RestaurantTable table, User waiter, Locale userLocale) {
        if (waiter == null) {
            log.warn("Cannot send table removed notification: waiter is null");
            return;
        }
        
        try {
            // Reload user to ensure we have the latest device token
            String deviceToken = waiter.getDeviceToken();
            if ((deviceToken == null || deviceToken.trim().isEmpty()) && userRepository != null) {
                try {
                    log.info("Reloading user {} to fetch device token for table removal notification", waiter.getId());
                    User reloadedUser = userRepository.findById(waiter.getId()).orElse(null);
                    if (reloadedUser != null) {
                        deviceToken = reloadedUser.getDeviceToken();
                        log.info(LOG_RELOADED_USER_DEVICE_TOKEN, waiter.getId(), 
                                deviceToken != null && !deviceToken.trim().isEmpty() ? STATUS_FOUND : STATUS_NOT_FOUND);
                    }
                } catch (Exception e) {
                    log.warn(LOG_FAILED_TO_RELOAD_USER, waiter.getId(), e.getMessage());
                }
            }
            
            // Normalize device token (handle empty strings as null)
            if (deviceToken != null && deviceToken.trim().isEmpty()) {
                deviceToken = null;
            }
            
            if (deviceToken == null || deviceToken.trim().isEmpty()) {
                log.warn("Cannot send table removed notification: waiter {} has no device token", waiter.getId());
                // Still save to database even if FCM cannot be sent
            }
            
            Locale loc = localeForWaiterRecipient(waiter, userLocale);
            Map<String, String> tableData = buildTableData(table, NOTIF_TABLE_REMOVED, loc);
            
            NotificationMessage message = notificationBuilder.buildMessage(
                    NotificationTemplate.Templates.TABLE_REMOVED,
                    loc,
                    new Object[]{
                            table.getTableOrder().toString(),
                            getSectionName(table.getRestaurantRow().getRestaurantSection(), loc)
                    },
                    tableData
            );

            // Only send real-time notification if waiter has active (non-expired) session
            if (hasActiveSession(waiter.getId())) {
                sendWebSocketNotification(waiter.getId(), TABLE_UPDATE_TOPIC, message, deviceToken);
            } else {
                log.info("Skipping table removed notification for waiter {} - session expired or logged out", waiter.getId());
            }
            
            // Save notification to database (waiter can see it when they log in again)
            saveNotificationToDatabase(waiter, message, NOTIF_TABLE_REMOVED, null);
            
            log.info("Table removal notification sent to waiter {} for table {}", waiter.getId(), table.getTableOrder());
            
        } catch (Exception e) {
            log.error("Failed to send table removed notification to waiter {}: {}", waiter.getId(), e.getMessage(), e);
        }
    }
    
    /**
     * Sends notifications to waiters when guests are transferred between tables.
     * Notifies both source and target waiters (if different) with appropriate messages.
     *
     * @param sourceTable The table from which guests are transferred
     * @param targetTable The table to which guests are transferred
     * @param sourceWaiter The waiter assigned to the source table (can be null)
     * @param targetWaiter The waiter assigned to the target table (can be null)
     * @param userLocale Locale for message localization
     */
    @Override
    public void notifyGuestTransfer(RestaurantTable sourceTable, RestaurantTable targetTable, 
                                   User sourceWaiter, User targetWaiter, Locale userLocale) {
        boolean isSameWaiter = sourceWaiter != null && targetWaiter != null && 
                              sourceWaiter.getId().equals(targetWaiter.getId());
        
        // Notify source waiter if exists
        if (sourceWaiter != null) {
            sendGuestTransferNotificationToWaiter(sourceTable, targetTable, sourceWaiter, true, 
                    isSameWaiter, userLocale);
        }
        
        // Notify target waiter if exists and different from source waiter
        if (targetWaiter != null && !isSameWaiter) {
            sendGuestTransferNotificationToWaiter(sourceTable, targetTable, targetWaiter, false, 
                    false, userLocale);
        }
    }
    
    /**
     * Helper method to send guest transfer notification to a specific waiter
     */
    private void sendGuestTransferNotificationToWaiter(RestaurantTable sourceTable, RestaurantTable targetTable,
                                                      User waiter, boolean isSourceWaiter, boolean isSameWaiter, 
                                                      Locale userLocale) {
        if (waiter == null) {
            return;
        }
        try {
            // Reload user to ensure we have the latest device token
            String deviceToken = waiter.getDeviceToken();
            if ((deviceToken == null || deviceToken.trim().isEmpty()) && userRepository != null) {
                try {
                    log.info("Reloading user {} to fetch device token for guest transfer notification", waiter.getId());
                    User reloadedUser = userRepository.findById(waiter.getId()).orElse(null);
                    if (reloadedUser != null) {
                        deviceToken = reloadedUser.getDeviceToken();
                        log.info(LOG_RELOADED_USER_DEVICE_TOKEN, waiter.getId(), 
                                deviceToken != null && !deviceToken.trim().isEmpty() ? STATUS_FOUND : STATUS_NOT_FOUND);
                    }
                } catch (Exception e) {
                    log.warn(LOG_FAILED_TO_RELOAD_USER, waiter.getId(), e.getMessage());
                }
            }
            
            // Normalize device token (handle empty strings as null)
            if (deviceToken != null && deviceToken.trim().isEmpty()) {
                deviceToken = null;
            }
            
            if (deviceToken == null || deviceToken.trim().isEmpty()) {
                log.warn("Cannot send guest transfer notification: waiter {} has no device token", waiter.getId());
                // Still save to database even if FCM cannot be sent
            }
            
            // Build notification message
            String title;
            String body;
            Map<String, String> data = new HashMap<>();
            data.put(KEY_NOTIFICATION_TYPE, "GUEST_TRANSFER");
            data.put("sourceTableId", sourceTable.getId().toString());
            data.put("targetTableId", targetTable.getId().toString());
            data.put("sourceTableCode", sourceTable.getTableCode());
            data.put("targetTableCode", targetTable.getTableCode());
            data.put("sourceTableOrder", sourceTable.getTableOrder().toString());
            data.put("targetTableOrder", targetTable.getTableOrder().toString());
            
            // Use tableCode for clarity (unique identifier) instead of tableOrder
            String sourceTableLabel = sourceTable.getTableCode() != null && !sourceTable.getTableCode().trim().isEmpty() 
                    ? sourceTable.getTableCode() 
                    : String.valueOf(sourceTable.getTableOrder());
            String targetTableLabel = targetTable.getTableCode() != null && !targetTable.getTableCode().trim().isEmpty() 
                    ? targetTable.getTableCode() 
                    : String.valueOf(targetTable.getTableOrder());
            
            Locale loc = localeForWaiterRecipient(waiter, userLocale);
            title = messageUtil.getMessage("notification.guest.transfer.title", loc);
            String bodyKey;
            Object[] bodyArgObjs;
            if (isSameWaiter) {
                bodyKey = "notification.guest.transfer.body.same";
                bodyArgObjs = new Object[]{sourceTableLabel, targetTableLabel};
            } else if (isSourceWaiter) {
                bodyKey = "notification.guest.transfer.body.source";
                bodyArgObjs = new Object[]{sourceTableLabel, targetTableLabel};
            } else {
                bodyKey = "notification.guest.transfer.body.target";
                bodyArgObjs = new Object[]{targetTableLabel, sourceTableLabel};
            }
            body = messageUtil.getMessage(bodyKey, loc, bodyArgObjs);
            String bodyArgsJson = serializeNotificationBodyArgs(bodyArgObjs);

            NotificationMessage message = NotificationMessage.builder()
                    .title(title)
                    .body(body)
                    .bodyKey(bodyKey)
                    .bodyArgs(bodyArgsJson)
                    .data(data)
                    .build();
            
            // Only send real-time notification if waiter has active (non-expired) session
            if (hasActiveSession(waiter.getId())) {
                sendWebSocketNotification(waiter.getId(), TABLE_UPDATE_TOPIC, message, deviceToken);
            } else {
                log.info("Skipping guest transfer notification for waiter {} - session expired or logged out", waiter.getId());
            }
            
            // Save notification to database (waiter can see it when they log in again)
            saveNotificationToDatabase(waiter, message, "GUEST_TRANSFER", null);
            
            log.info("Guest transfer FCM notification sent to waiter {} (isSource: {}, isSame: {})", 
                    waiter.getId(), isSourceWaiter, isSameWaiter);
            
        } catch (Exception e) {
            log.error("Failed to send guest transfer notification to waiter {}: {}", waiter.getId(), e.getMessage(), e);
        }
    }
    
    /**
     * Sends notifications to HQ Admins when a table section change request is opened.
     * Notifies all provided HQ Admins about the pending request.
     *
     * @param table The table for which section change is requested
     * @param hqAdmins List of HQ Admins to notify (must not be null or empty)
     * @param userLocale Locale for message localization
     */
    @Override
    public void notifyTableSectionRequestOpened(RestaurantTable table, List<User> hqAdmins, Locale userLocale) {
        if (hqAdmins == null || hqAdmins.isEmpty()) {
            log.warn("No HQ Admins provided for table/section request notification");
            return;
        }

        try {
            int savedCount = 0;
            int failedCount = 0;
            for (User hqAdmin : hqAdmins) {
                if (hqAdmin == null) {
                    continue;
                }
                try {
                    Locale loc = localeForRecipient(hqAdmin, userLocale);
                    Map<String, String> tableData = buildTableData(table, NOTIF_TABLE_SECTION_REQUEST_OPENED, loc);
                    tableData.put(KEY_REQUEST_ID, table.getId().toString());
                    tableData.put(KEY_REQUEST_TYPE, "TABLE");
                    NotificationMessage message = notificationBuilder.buildMessage(
                            NotificationTemplate.Templates.TABLE_SECTION_REQUEST_OPENED,
                            loc,
                            new Object[]{
                                    table.getTableOrder().toString(),
                                    table.getTableSectionRequestedBy() != null ?
                                            table.getTableSectionRequestedBy().getFirstName() + " " + table.getTableSectionRequestedBy().getLastName() : "Manager"
                            },
                            tableData
                    );
                    try {
                        sendToUser(hqAdmin, message, MANAGER_TOPIC);
                    } catch (Exception e) {
                        log.warn("Failed to send push notification for table section request to HQ Admin {}, but will still save: {}",
                                hqAdmin.getId(), e.getMessage());
                    }
                    saveNotificationToDatabase(hqAdmin, message, NOTIF_TABLE_SECTION_REQUEST_OPENED, null);
                    savedCount++;
                } catch (Exception e) {
                    failedCount++;
                    log.error("Failed to save table section request notification to database for HQ Admin {}: {}",
                            hqAdmin.getId(), e.getMessage(), e);
                }
            }
            
            if (savedCount > 0) {
                log.info("Successfully saved table section request notifications to database for {} HQ Admin(s)", savedCount);
            }
            if (failedCount > 0) {
                log.error("Failed to save table section request notifications to database for {} HQ Admin(s)", failedCount);
            }

        } catch (Exception e) {
            log.error("Failed to send table/section request notification to HQ Admins: {}", e.getMessage(), e);
        }
    }
    
    /**
     * Sends notifications to HQ Admins when a section change request is opened.
     * Notifies all provided HQ Admins about the pending request.
     *
     * @param section The section for which change is requested
     * @param hqAdmins List of HQ Admins to notify (must not be null or empty)
     * @param userLocale Locale for message localization
     */
    @Override
    public void notifyTableSectionRequestOpened(com.gulfnet.shared_library.entity.RestaurantSection section, List<User> hqAdmins, Locale userLocale) {
        if (hqAdmins == null || hqAdmins.isEmpty()) {
            log.warn("No HQ Admins provided for table/section request notification");
            return;
        }

        try {
            int savedCount = 0;
            int failedCount = 0;
            for (User hqAdmin : hqAdmins) {
                if (hqAdmin == null) {
                    continue;
                }
                try {
                    Locale loc = localeForRecipient(hqAdmin, userLocale);
                    Map<String, String> sectionData = buildSectionData(section, NOTIF_TABLE_SECTION_REQUEST_OPENED, loc);
                    sectionData.put(KEY_REQUEST_ID, section.getId().toString());
                    sectionData.put(KEY_REQUEST_TYPE, "SECTION");
                    NotificationMessage message = notificationBuilder.buildMessage(
                            NotificationTemplate.Templates.TABLE_SECTION_REQUEST_OPENED,
                            loc,
                            new Object[]{
                                    getSectionName(section, loc),
                                    section.getTableSectionRequestedBy() != null ?
                                            section.getTableSectionRequestedBy().getFirstName() + " " + section.getTableSectionRequestedBy().getLastName() : "Manager"
                            },
                            sectionData
                    );
                    try {
                        sendToUser(hqAdmin, message, MANAGER_TOPIC);
                    } catch (Exception e) {
                        log.warn("Failed to send push notification for section request to HQ Admin {}, but will still save: {}",
                                hqAdmin.getId(), e.getMessage());
                    }
                    saveNotificationToDatabase(hqAdmin, message, NOTIF_TABLE_SECTION_REQUEST_OPENED, null);
                    savedCount++;
                } catch (Exception e) {
                    failedCount++;
                    log.error("Failed to save section request notification to database for HQ Admin {}: {}",
                            hqAdmin.getId(), e.getMessage(), e);
                }
            }
            
            if (savedCount > 0) {
                log.info("Successfully saved section request notifications to database for {} HQ Admin(s)", savedCount);
            }
            if (failedCount > 0) {
                log.error("Failed to save section request notifications to database for {} HQ Admin(s)", failedCount);
            }

        } catch (Exception e) {
            log.error("Failed to send table/section request notification to HQ Admins: {}", e.getMessage(), e);
        }
    }
    
    /**
     * Sends a notification to the requester when a table section change request is created.
     * Requires the requester to have a device token for FCM notification.
     *
     * @param table The table for which the request was created
     * @param requester The user who created the request (must not be null and must have device token)
     * @param userLocale Locale for message localization
     */
    @Override
    public void notifyTableSectionRequestCreated(RestaurantTable table, User requester, Locale userLocale) {
        if (requester == null || requester.getDeviceToken() == null) {
            log.warn("Cannot send table/section request created notification: requester or FCM token is null");
            return;
        }
        
        try {
            Locale loc = localeForRecipient(requester, userLocale);
            Map<String, String> tableData = buildTableData(table, NOTIF_TABLE_SECTION_REQUEST_CREATED, loc);
            tableData.put(KEY_REQUEST_ID, table.getId().toString());
            tableData.put(KEY_REQUEST_TYPE, "TABLE");
            
            NotificationMessage message = notificationBuilder.buildMessage(
                    NotificationTemplate.Templates.TABLE_SECTION_REQUEST_CREATED,
                    loc,
                    new Object[]{
                            table.getTableOrder().toString()
                    },
                    tableData
            );

            sendToUser(requester, message, MANAGER_TOPIC);
            
            // Save notification to database
            saveNotificationToDatabase(requester, message, NOTIF_TABLE_SECTION_REQUEST_CREATED, null);
            
        } catch (Exception e) {
            log.error("Failed to send table/section request created notification to requester {}: {}", requester.getId(), e.getMessage(), e);
        }
    }
    
    /**
     * Sends a notification to the requester when a section change request is created.
     * Requires the requester to have a device token for FCM notification.
     *
     * @param section The section for which the request was created
     * @param requester The user who created the request (must not be null and must have device token)
     * @param userLocale Locale for message localization
     */
    @Override
    public void notifyTableSectionRequestCreated(com.gulfnet.shared_library.entity.RestaurantSection section, User requester, Locale userLocale) {
        if (requester == null || requester.getDeviceToken() == null) {
            log.warn("Cannot send table/section request created notification: requester or FCM token is null");
            return;
        }
        
        try {
            Locale loc = localeForRecipient(requester, userLocale);
            Map<String, String> sectionData = buildSectionData(section, NOTIF_TABLE_SECTION_REQUEST_CREATED, loc);
            sectionData.put(KEY_REQUEST_ID, section.getId().toString());
            sectionData.put(KEY_REQUEST_TYPE, "SECTION");
            
            NotificationMessage message = notificationBuilder.buildMessage(
                    NotificationTemplate.Templates.TABLE_SECTION_REQUEST_CREATED,
                    loc,
                    new Object[]{
                            getSectionName(section, loc)
                    },
                    sectionData
            );

            sendToUser(requester, message, MANAGER_TOPIC);
            
            // Save notification to database
            saveNotificationToDatabase(requester, message, NOTIF_TABLE_SECTION_REQUEST_CREATED, null);
            
        } catch (Exception e) {
            log.error("Failed to send table/section request created notification to requester {}: {}", requester.getId(), e.getMessage(), e);
        }
    }

    // ==================== USER NOTIFICATIONS ====================
    
    /**
     * Sends a notification to the user when their password is updated.
     * Requires the user to have a device token for FCM notification.
     *
     * @param user The user whose password was updated (must not be null and must have device token)
     * @param userLocale Locale for message localization
     */
    @Override
    public void notifyPasswordUpdated(User user, Locale userLocale) {
        if (user == null || user.getDeviceToken() == null) {
            log.warn("Cannot send password updated notification: user or FCM token is null");
            return;
        }
        
        try {
            Locale loc = localeForRecipient(user, userLocale);
            Map<String, String> userData = buildUserData(user, NOTIF_PASSWORD_UPDATED, null);
            
            NotificationMessage message = notificationBuilder.buildMessage(
                    NotificationTemplate.Templates.PASSWORD_UPDATED,
                    loc,
                    new Object[]{user.getFirstName() + " " + user.getLastName()},
                    userData
            );

            sendToUser(user, message, USER_UPDATE_TOPIC);
            
            // Save notification to database
            saveNotificationToDatabase(user, message, NOTIF_PASSWORD_UPDATED, null);
            
        } catch (Exception e) {
            log.error("Failed to send password updated notification to user {}: {}", user.getId(), e.getMessage(), e);
        }
    }
    
    /**
     * Sends notifications to approvers when a profile update request is opened.
     * Also sends KDS WebSocket notification if applicable.
     *
     * @param user The user whose profile update is requested
     * @param approvers List of approvers to notify (must not be null or empty)
     * @param userLocale Locale for message localization
     */
    @Override
    public void notifyProfileUpdateRequestOpened(User user, List<User> approvers, Locale userLocale) {
        if (approvers == null || approvers.isEmpty()) {
            log.warn("No approvers provided for profile update request notification");
            return;
        }

        try {
            for (User approver : approvers) {
                if (approver == null) {
                    continue;
                }
                Locale loc = localeForRecipient(approver, userLocale);
                Map<String, String> userData = buildUserData(user, NOTIF_PROFILE_UPDATE_REQUEST_OPENED, null);
                userData.put(KEY_REQUEST_ID, user.getId().toString());
                userData.put(KEY_REQUESTER_NAME, user.getUpdatedBy() != null ?
                        user.getUpdatedBy().getFirstName() + " " + user.getUpdatedBy().getLastName() : "User");
                NotificationMessage message = notificationBuilder.buildMessage(
                        NotificationTemplate.Templates.PROFILE_UPDATE_REQUEST_OPENED,
                        loc,
                        new Object[]{
                                user.getFirstName() + " " + user.getLastName(),
                                user.getUpdatedBy() != null ?
                                        user.getUpdatedBy().getFirstName() + " " + user.getUpdatedBy().getLastName() : "User"
                        },
                        userData
                );
                sendToUser(approver, message, MANAGER_TOPIC);
                saveNotificationToDatabase(approver, message, NOTIF_PROFILE_UPDATE_REQUEST_OPENED, null);
            }
            
            // Also send KDS WebSocket notification
            try {
                java.util.UUID restaurantId = null;
                if (user.getRestaurantId() != null) {
                    restaurantId = user.getRestaurantId();
                } else if (approvers != null && !approvers.isEmpty() && approvers.get(0).getRestaurantId() != null) {
                    restaurantId = approvers.get(0).getRestaurantId();
                }
                if (restaurantId != null) {
                    notifyKdsProfileUpdateRequestOpened(user, restaurantId, userLocale);
                }
            } catch (Exception e) {
                log.warn("Failed to send KDS profile update request notification: {}", e.getMessage());
            }

        } catch (Exception e) {
            log.error("Failed to send profile update request notification to approvers: {}", e.getMessage(), e);
        }
    }
    
    /**
     * Sends a notification to the requester when a profile update request is created.
     * Uses unified user update topic for both cashier and KDS users.
     *
     * @param user The user whose profile update is requested
     * @param requester The user who created the request (must not be null)
     * @param userLocale Locale for message localization
     */
    @Override
    public void notifyProfileUpdateRequestCreated(User user, User requester, Locale userLocale) {
        if (requester == null) {
            log.warn("Cannot send profile update request created notification: requester is null");
            return;
        }
        
        try {
            Locale loc = localeForRecipient(requester, userLocale);
            Map<String, String> userData = buildUserData(user, NOTIF_PROFILE_UPDATE_REQUEST_CREATED, null);
            userData.put(KEY_REQUEST_ID, user.getId().toString());
            
            NotificationMessage message = notificationBuilder.buildMessage(
                    NotificationTemplate.Templates.PROFILE_UPDATE_REQUEST_CREATED,
                    loc,
                    new Object[]{
                            user.getFirstName() + " " + user.getLastName()
                    },
                    userData
            );

            // Get restaurant ID for notification data
            java.util.UUID restaurantId = null;
            if (user.getRestaurantId() != null) {
                restaurantId = user.getRestaurantId();
            } else if (requester.getRestaurantId() != null) {
                restaurantId = requester.getRestaurantId();
            }
            
            // Use /topic/user/updates for both cashier and KDS
            sendProfileUpdateToUnifiedTopic(USER_UPDATE_TOPIC, user, requester, message, NOTIF_PROFILE_UPDATE_REQUEST_CREATED, null, restaurantId, userLocale);
            
            // Save notification to database for requester only (the KDS user who raised the request)
            saveNotificationToDatabase(requester, message, NOTIF_PROFILE_UPDATE_REQUEST_CREATED, null);
            
        } catch (Exception e) {
            log.error("Failed to send profile update request created notification to requester {}: {}", requester.getId(), e.getMessage(), e);
        }
    }
    
    /**
     * Sends a notification to the requester about the decision on a profile update request.
     * Delegates to the overloaded method with manager parameter set to null.
     *
     * @param user The user whose profile update was requested
     * @param requester The user who requested the profile update (must not be null)
     * @param isApproved Whether the request was approved or rejected
     * @param comments Manager's comments on the decision
     * @param userLocale Locale for message localization
     */
    @Override
    public void notifyProfileUpdateRequestDecision(User user, User requester, boolean isApproved, String comments, Locale userLocale) {
        notifyProfileUpdateRequestDecision(user, requester, isApproved, comments, userLocale, null);
    }
    
    /**
     * Sends a notification to the requester about the decision on a profile update request.
     * Uses different topics based on the requester's role (cashier, KDS, or waiter).
     *
     * @param user The user whose profile update was requested
     * @param requester The user who requested the profile update (must not be null)
     * @param isApproved Whether the request was approved or rejected
     * @param comments Manager's comments on the decision
     * @param userLocale Locale for message localization
     * @param manager The manager who made the decision (can be null)
     */
    @Override
    public void notifyProfileUpdateRequestDecision(User user, User requester, boolean isApproved, String comments, Locale userLocale, User manager) {
        if (requester == null) {
            log.warn("Cannot send profile update request decision notification: requester is null");
            return;
        }
        
        try {
            Locale loc = localeForRecipient(requester, userLocale);
            Map<String, String> userData;
            NotificationMessage message;
            String notificationType;
            
            if (isApproved) {
                userData = buildUserData(user, NOTIF_PROFILE_UPDATE_REQUEST_APPROVED, comments);
                userData.put(KEY_IS_APPROVED, "true");
                userData.put(KEY_REQUEST_ID, user.getId().toString());
                notificationType = NOTIF_PROFILE_UPDATE_REQUEST_APPROVED;
                
                message = notificationBuilder.buildMessage(
                        NotificationTemplate.Templates.PROFILE_UPDATE_REQUEST_APPROVED,
                        loc,
                        new Object[]{
                                user.getFirstName() + " " + user.getLastName(),
                                comments != null ? comments : ""
                        },
                        userData
                );
            } else {
                userData = buildUserData(user, NOTIF_PROFILE_UPDATE_REQUEST_DECLINED, comments);
                userData.put(KEY_IS_APPROVED, VALUE_FALSE);
                userData.put(KEY_REQUEST_ID, user.getId().toString());
                notificationType = NOTIF_PROFILE_UPDATE_REQUEST_DECLINED;
                
                message = notificationBuilder.buildMessage(
                        NotificationTemplate.Templates.PROFILE_UPDATE_REQUEST_DECLINED,
                        loc,
                        new Object[]{
                                user.getFirstName() + " " + user.getLastName(),
                                comments != null ? comments : ""
                        },
                        userData
                );
            }
            
            // Get device token for waiter role (for FCM notification)
            String deviceToken = null;
            if (!isCashier(requester) && !isKds(requester)) {
                // For waiter and other roles, get device token for FCM
                deviceToken = requester.getDeviceToken();
                if ((deviceToken == null || deviceToken.trim().isEmpty()) && userRepository != null) {
                    try {
                        log.info("Reloading requester {} to fetch device token for profile update request decision notification", requester.getId());
                        User reloadedRequester = userRepository.findById(requester.getId()).orElse(null);
                        if (reloadedRequester != null) {
                            deviceToken = reloadedRequester.getDeviceToken();
                            log.info("Reloaded requester {} - device token: {}", requester.getId(), 
                                    deviceToken != null && !deviceToken.trim().isEmpty() ? STATUS_FOUND : STATUS_NOT_FOUND);
                        }
                    } catch (Exception e) {
                        log.warn("Failed to reload requester {} to get device token: {}", requester.getId(), e.getMessage());
                    }
                }
                
                // Normalize device token (handle empty strings as null)
                if (deviceToken != null && deviceToken.trim().isEmpty()) {
                    deviceToken = null;
                }
            }
            
            // Send to cashier-specific topic if requester is cashier (for Cashier App)
            if (isCashier(requester)) {
                // Send to user-specific cashier topic that Cashier App subscribes to
                sendWebSocketNotification(requester.getId(), CASHIER_TOPIC, message, null, notificationType);
                log.info("Sent profile update decision notification to cashier {} via CASHIER_TOPIC", requester.getId());
            } else if (isKds(requester)) {
                // Send to /topic/user/updates for KDS user who raised the request (only the requester, not all KDS users)
                sendWebSocketNotification(requester.getId(), USER_UPDATE_TOPIC, message, null, notificationType);
                log.info("Sent profile update decision notification to KDS user {} via USER_UPDATE_TOPIC (/topic/user/updates)", requester.getId());
            } else {
                // For other roles (waiter, etc.), send to user update topic with device token for FCM
                sendWebSocketNotification(requester.getId(), USER_UPDATE_TOPIC, message, deviceToken, notificationType);
                log.info("Sent profile update decision notification to waiter/user {} via USER_UPDATE_TOPIC (/topic/user/updates) with device token: {}", 
                        requester.getId(), deviceToken != null && !deviceToken.trim().isEmpty() ? "PRESENT" : "NOT PRESENT");
            }
            
            // Save notification to database for the requester
            // This ensures the notification is saved in restaurant-management service's database
            // and appears in notification listings. The user-management service may save it separately,
            // but we need to ensure it's saved here for proper notification listing functionality.
            saveNotificationToDatabase(requester, message, notificationType, manager);
            
            // Send list refresh events for both notifications and requests
            sendListRefreshEvent(requester.getId(), LIST_NOTIFICATIONS);
            sendListRefreshEvent(requester.getId(), LIST_REQUESTS);

            // Many clients refresh header / "me" only on PROFILE_UPDATED_DIRECTLY. After approval the DB
            // already has the new name; emit that type over WebSocket only (no extra DB row or FCM).
            if (isApproved) {
                try {
                    sendProfileUpdatedDirectlyWebSocketOnly(user, manager, requester, userLocale);
                } catch (Exception e) {
                    log.warn("Failed to send profile sync WebSocket after profile update approval for {}: {}",
                            requester.getId(), e.getMessage());
                }
            }
            
        } catch (Exception e) {
            log.error("Failed to send profile update request decision notification to requester {}: {}", requester.getId(), e.getMessage(), e);
        }
    }

    /**
     * WebSocket-only {@code PROFILE_UPDATED_DIRECTLY} so apps reuse the same profile-refresh handler as direct edits.
     */
    private void sendProfileUpdatedDirectlyWebSocketOnly(User profileUser, User updater, User requester, Locale userLocale) {
        if (profileUser == null || requester == null) {
            return;
        }
        try {
            User employee = userRepository != null
                    ? userRepository.findById(profileUser.getId()).orElse(profileUser)
                    : profileUser;
            Locale loc = localeForRecipient(requester, userLocale);
            Map<String, String> userData = buildUserData(employee, NOTIF_PROFILE_UPDATED_DIRECTLY, null);
            if (updater != null) {
                userData.put("updaterName", (updater.getFirstName() != null ? updater.getFirstName() : "") +
                        " " + (updater.getLastName() != null ? updater.getLastName() : "").trim());
                userData.put("updaterId", updater.getId().toString());
            }
            String updaterName = updater != null
                    ? (updater.getFirstName() + " " + updater.getLastName()).trim()
                    : "Administrator";
            NotificationMessage syncMessage = notificationBuilder.buildMessage(
                    NotificationTemplate.Templates.PROFILE_UPDATED_DIRECTLY,
                    loc,
                    new Object[]{ updaterName },
                    userData
            );
            if (isCashier(requester)) {
                sendWebSocketNotification(requester.getId(), CASHIER_TOPIC, syncMessage, null, NOTIF_PROFILE_UPDATED_DIRECTLY);
            } else if (isKds(requester)) {
                sendWebSocketNotification(requester.getId(), USER_UPDATE_TOPIC, syncMessage, null, NOTIF_PROFILE_UPDATED_DIRECTLY);
            } else {
                sendWebSocketNotification(requester.getId(), USER_UPDATE_TOPIC, syncMessage, null, NOTIF_PROFILE_UPDATED_DIRECTLY);
            }
            log.info("Sent PROFILE_UPDATED_DIRECTLY (WebSocket-only) after approved profile request for user {}", requester.getId());
        } catch (Exception e) {
            log.error("sendProfileUpdatedDirectlyWebSocketOnly failed for requester {}: {}", requester.getId(), e.getMessage(), e);
        }
    }
    
    /**
     * Sends a notification to an employee when their profile is updated directly (without a request).
     * Uses different topics based on the employee's role (cashier, KDS, or waiter).
     *
     * @param employee The employee whose profile was updated
     * @param updater The user who updated the profile (can be null)
     * @param userLocale Locale for message localization
     */
    @Override
    public void notifyProfileUpdatedDirectly(User employee, User updater, Locale userLocale) {
        if (employee == null) {
            log.warn("Cannot send profile updated directly notification: employee is null");
            return;
        }
        
        try {
            Locale loc = localeForRecipient(employee, userLocale);
            Map<String, String> userData = buildUserData(employee, NOTIF_PROFILE_UPDATED_DIRECTLY, null);
            if (updater != null) {
                userData.put("updaterName", (updater.getFirstName() != null ? updater.getFirstName() : "") + 
                        " " + (updater.getLastName() != null ? updater.getLastName() : "").trim());
                userData.put("updaterId", updater.getId().toString());
            }
            
            String updaterName = updater != null 
                    ? (updater.getFirstName() + " " + updater.getLastName()).trim()
                    : "Administrator";
            
            NotificationMessage message = notificationBuilder.buildMessage(
                    NotificationTemplate.Templates.PROFILE_UPDATED_DIRECTLY,
                    loc,
                    new Object[]{
                            updaterName
                    },
                    userData
            );
            
            // Get device token for FCM (only for waiter role)
            String deviceToken = null;
            if (!isCashier(employee) && !isKds(employee)) {
                // For waiter and other roles, get device token for FCM
                deviceToken = employee.getDeviceToken();
                if ((deviceToken == null || deviceToken.trim().isEmpty()) && userRepository != null) {
                    try {
                        User reloadedEmployee = userRepository.findById(employee.getId()).orElse(null);
                        if (reloadedEmployee != null) {
                            deviceToken = reloadedEmployee.getDeviceToken();
                        }
                    } catch (Exception e) {
                        log.warn("Failed to reload employee to get device token: {}", e.getMessage());
                    }
                }
            }
            
            // Send notification based on role:
            // - Cashier and KDS: WebSocket only (no FCM)
            // - Waiter and other roles: Both WebSocket and FCM
            if (isCashier(employee)) {
                // Cashier: WebSocket only (no device token = no FCM)
                sendWebSocketNotification(employee.getId(), CASHIER_TOPIC, message, null, NOTIF_PROFILE_UPDATED_DIRECTLY);
                log.info("Sent profile updated directly notification to cashier {} via WebSocket only", employee.getId());
            } else if (isKds(employee)) {
                // KDS: WebSocket only (no device token = no FCM)
                sendWebSocketNotification(employee.getId(), USER_UPDATE_TOPIC, message, null, NOTIF_PROFILE_UPDATED_DIRECTLY);
                log.info("Sent profile updated directly notification to KDS user {} via WebSocket only", employee.getId());
            } else {
                // Waiter and other roles: Both WebSocket and FCM (with device token)
                sendWebSocketNotification(employee.getId(), USER_UPDATE_TOPIC, message, deviceToken, NOTIF_PROFILE_UPDATED_DIRECTLY);
                log.info("Sent profile updated directly notification to waiter/user {} via WebSocket and FCM", employee.getId());
            }
            
            // Save notification to database
            saveNotificationToDatabase(employee, message, NOTIF_PROFILE_UPDATED_DIRECTLY, updater);
            
            // Send list refresh event
            sendListRefreshEvent(employee.getId(), LIST_NOTIFICATIONS);
            
        } catch (Exception e) {
            log.error("Failed to send profile updated directly notification to employee {}: {}", employee.getId(), e.getMessage(), e);
        }
    }
    
    /**
     * Sends notification to managers when an employee is assigned to a restaurant.
     * Notifications are sent in each manager's preferred language (from their languageCode),
     * with fallback to the provided userLocale or English.
     *
     * @param employee   the employee who was assigned to the restaurant
     * @param restaurant the restaurant the employee was assigned to
     * @param managers   list of managers to notify about the assignment
     * @param userLocale fallback locale for notifications if manager's language code is not available
     */
    @Override
    public void notifyEmployeeAssignedToRestaurant(User employee, com.gulfnet.shared_library.entity.Restaurant restaurant, List<User> managers, Locale userLocale) {
        if (employee == null || restaurant == null || managers == null || managers.isEmpty()) {
            log.warn("Cannot send employee assigned notification: employee, restaurant, or managers are null/empty");
            return;
        }
        
        try {
            String employeeName = (employee.getFirstName() != null ? employee.getFirstName() : "") + 
                    " " + (employee.getLastName() != null ? employee.getLastName() : "");
            employeeName = employeeName.trim();
            
            // Send notification to each manager in their preferred language
            // Each manager should receive the notification in their own language preference
            for (User manager : managers) {
                try {
                    // Get manager's preferred locale from their languageCode
                    // Use userLocale as fallback if manager doesn't have a languageCode
                    Locale managerLocale = (userLocale != null) ? userLocale : Locale.forLanguageTag("en");
                    if (manager.getLanguageCode() != null && !manager.getLanguageCode().trim().isEmpty()) {
                        try {
                            managerLocale = Locale.forLanguageTag(manager.getLanguageCode());
                        } catch (Exception e) {
                            log.warn("Invalid language code for manager {}: {}, using fallback locale", 
                                    manager.getId(), manager.getLanguageCode());
                        }
                    }
                    
                    // Get restaurant name in manager's preferred language
                    String restaurantName = "";
                    try {
                        if (restaurant.getTranslations() != null && !restaurant.getTranslations().isEmpty()) {
                            String managerLanguage = managerLocale.getLanguage();
                            restaurantName = restaurant.getTranslations().stream()
                                    .filter(t -> t.getLanguageCode() != null && t.getLanguageCode().startsWith(managerLanguage))
                                    .findFirst()
                                    .map(com.gulfnet.shared_library.entity.RestaurantTranslation::getName)
                                    .orElse(restaurant.getTranslations().get(0).getName());
                        }
                    } catch (Exception e) {
                        log.debug("Could not access restaurant translations directly: {}", e.getMessage());
                    }
                    
                    // Fallback to restaurantCode if no translation found
                    if (restaurantName == null || restaurantName.trim().isEmpty()) {
                        restaurantName = restaurant.getRestaurantCode() != null ? restaurant.getRestaurantCode() : "";
                    }
                    
                    // Build employee data for this manager's notification
                    Map<String, String> employeeData = buildUserData(employee, NOTIF_EMPLOYEE_ASSIGNED_TO_RESTAURANT, null);
                    employeeData.put(KEY_RESTAURANT_ID, restaurant.getId().toString());
                    employeeData.put("restaurantName", restaurantName);
                    employeeData.put(KEY_RESTAURANT_CODE, restaurant.getRestaurantCode() != null ? restaurant.getRestaurantCode() : "");
                    
                    // Build notification message in manager's preferred language
                    NotificationMessage message = notificationBuilder.buildMessage(
                            NotificationTemplate.Templates.EMPLOYEE_ASSIGNED_TO_RESTAURANT,
                            managerLocale,
                            new Object[]{
                                    employeeName,
                                    restaurantName
                            },
                            employeeData
                    );
                    
                    // sendToUser sends both WebSocket and FCM (if device token available)
                    sendToUser(manager, message, MANAGER_TOPIC);
                    // Save notification to database
                    saveNotificationToDatabase(manager, message, NOTIF_EMPLOYEE_ASSIGNED_TO_RESTAURANT, null);
                    log.info("Sent employee assigned notification to manager {} (locale: {}) via WebSocket and FCM (if device token available), saved to DB", 
                            manager.getId(), managerLocale.getLanguage());
                } catch (Exception e) {
                    log.error("Failed to send employee assigned notification to manager {}: {}", manager.getId(), e.getMessage(), e);
                }
            }
            
        } catch (Exception e) {
            log.error("Failed to send employee assigned notification: {}", e.getMessage(), e);
        }
    }

    // ==================== MENU ASSIGNMENT NOTIFICATIONS ====================
    
    /**
     * Sends notifications to managers when a menu is assigned to a restaurant.
     * Each manager receives the notification in their preferred language.
     *
     * @param menu The menu that was assigned
     * @param restaurant The restaurant to which the menu was assigned
     * @param managers List of managers to notify (must not be null or empty)
     * @param userLocale Locale for message localization (used as fallback)
     */
    @Override
    public void notifyMenuAssignedToRestaurant(com.gulfnet.shared_library.entity.Menu menu, 
                                                com.gulfnet.shared_library.entity.Restaurant restaurant, 
                                                List<User> managers, Locale userLocale) {
        if (menu == null || restaurant == null || managers == null || managers.isEmpty()) {
            log.warn("Cannot send menu assigned notification: menu, restaurant, or managers are null/empty");
            return;
        }
        
        try {
            for (User manager : managers) {
                try {
                    // Get manager's preferred locale
                    Locale managerLocale = (userLocale != null) ? userLocale : Locale.forLanguageTag("en");
                    if (manager.getLanguageCode() != null && !manager.getLanguageCode().trim().isEmpty()) {
                        try {
                            managerLocale = Locale.forLanguageTag(manager.getLanguageCode());
                        } catch (Exception e) {
                            log.warn("Invalid language code for manager {}: {}, using fallback locale", 
                                    manager.getId(), manager.getLanguageCode());
                        }
                    }
                    
                    // Get menu name in manager's preferred language
                    String menuName = "";
                    try {
                        if (menu.getTranslations() != null && !menu.getTranslations().isEmpty()) {
                            String managerLanguage = managerLocale.getLanguage();
                            menuName = menu.getTranslations().stream()
                                    .filter(t -> t.getLanguageCode() != null && t.getLanguageCode().startsWith(managerLanguage))
                                    .findFirst()
                                    .map(com.gulfnet.shared_library.entity.MenuTranslation::getName)
                                    .orElse(menu.getTranslations().get(0).getName());
                        }
                    } catch (Exception e) {
                        log.debug("Could not access menu translations directly: {}", e.getMessage());
                    }
                    if (menuName == null || menuName.trim().isEmpty()) {
                        menuName = menu.getId().toString();
                    }
                    
                    // Get restaurant name in manager's preferred language
                    String restaurantName = "";
                    try {
                        if (restaurant.getTranslations() != null && !restaurant.getTranslations().isEmpty()) {
                            String managerLanguage = managerLocale.getLanguage();
                            restaurantName = restaurant.getTranslations().stream()
                                    .filter(t -> t.getLanguageCode() != null && t.getLanguageCode().startsWith(managerLanguage))
                                    .findFirst()
                                    .map(com.gulfnet.shared_library.entity.RestaurantTranslation::getName)
                                    .orElse(restaurant.getTranslations().get(0).getName());
                        }
                    } catch (Exception e) {
                        log.debug("Could not access restaurant translations directly: {}", e.getMessage());
                    }
                    if (restaurantName == null || restaurantName.trim().isEmpty()) {
                        restaurantName = restaurant.getRestaurantCode() != null ? restaurant.getRestaurantCode() : "";
                    }
                    
                    // Build notification data
                    Map<String, String> menuData = new HashMap<>();
                    menuData.put("menuId", menu.getId().toString());
                    menuData.put("menuName", menuName);
                    menuData.put(KEY_RESTAURANT_ID, restaurant.getId().toString());
                    menuData.put("restaurantName", restaurantName);
                    menuData.put(KEY_NOTIFICATION_TYPE, NOTIF_MENU_ASSIGNED_TO_RESTAURANT);
                    
                    // Build notification message in manager's preferred language
                    NotificationMessage message = notificationBuilder.buildMessage(
                            NotificationTemplate.Templates.MENU_ASSIGNED_TO_RESTAURANT,
                            managerLocale,
                            new Object[]{menuName, restaurantName},
                            menuData
                    );
                    
                    // sendToUser sends both WebSocket and FCM (if device token available)
                    sendToUser(manager, message, MANAGER_TOPIC);
                    // Save notification to database
                    saveNotificationToDatabase(manager, message, NOTIF_MENU_ASSIGNED_TO_RESTAURANT, null);
                    log.info("Sent menu assigned notification to manager {} (locale: {}) for menu {} at restaurant {}", 
                            manager.getId(), managerLocale.getLanguage(), menu.getId(), restaurant.getId());
                } catch (Exception e) {
                    log.error("Failed to send menu assigned notification to manager {}: {}", manager.getId(), e.getMessage(), e);
                }
            }
        } catch (Exception e) {
            log.error("Failed to send menu assigned notification: {}", e.getMessage(), e);
        }
    }

    @Override
    public void notifyMenuLiveAtRestaurant(com.gulfnet.shared_library.entity.Menu menu,
                                           com.gulfnet.shared_library.entity.Restaurant restaurant,
                                           List<User> managers,
                                           Locale userLocale) {
        if (menu == null || restaurant == null || managers == null || managers.isEmpty()) {
            log.warn("Cannot send menu live notification: menu, restaurant, or managers are null/empty");
            return;
        }

        log.info("[MENU_LIVE][FCM] notify start menuId={} restaurantId={} restaurantCode={} managerCount={} fallbackUserLocale={}",
                menu.getId(),
                restaurant.getId(),
                restaurant.getRestaurantCode() != null ? restaurant.getRestaurantCode() : "",
                managers.size(),
                userLocale);

        try {
            for (User manager : managers) {
                try {
                    Locale managerLocale = (userLocale != null) ? userLocale : Locale.forLanguageTag("en");
                    if (manager.getLanguageCode() != null && !manager.getLanguageCode().trim().isEmpty()) {
                        try {
                            managerLocale = Locale.forLanguageTag(manager.getLanguageCode());
                        } catch (Exception e) {
                            log.warn("Invalid language code for manager {}: {}, using fallback locale",
                                    manager.getId(), manager.getLanguageCode());
                        }
                    }

                    String menuName = "";
                    try {
                        if (menu.getTranslations() != null && !menu.getTranslations().isEmpty()) {
                            String managerLanguage = managerLocale.getLanguage();
                            menuName = menu.getTranslations().stream()
                                    .filter(t -> t.getLanguageCode() != null && t.getLanguageCode().startsWith(managerLanguage))
                                    .findFirst()
                                    .map(com.gulfnet.shared_library.entity.MenuTranslation::getName)
                                    .orElse(menu.getTranslations().get(0).getName());
                        }
                    } catch (Exception e) {
                        log.debug("Could not access menu translations directly: {}", e.getMessage());
                    }
                    if (menuName == null || menuName.trim().isEmpty()) {
                        menuName = menu.getId().toString();
                    }

                    String restaurantName = "";
                    try {
                        if (restaurant.getTranslations() != null && !restaurant.getTranslations().isEmpty()) {
                            String managerLanguage = managerLocale.getLanguage();
                            restaurantName = restaurant.getTranslations().stream()
                                    .filter(t -> t.getLanguageCode() != null && t.getLanguageCode().startsWith(managerLanguage))
                                    .findFirst()
                                    .map(com.gulfnet.shared_library.entity.RestaurantTranslation::getName)
                                    .orElse(restaurant.getTranslations().get(0).getName());
                        }
                    } catch (Exception e) {
                        log.debug("Could not access restaurant translations directly: {}", e.getMessage());
                    }
                    if (restaurantName == null || restaurantName.trim().isEmpty()) {
                        restaurantName = restaurant.getRestaurantCode() != null ? restaurant.getRestaurantCode() : "";
                    }

                    Map<String, String> data = new HashMap<>();
                    data.put("menuId", menu.getId().toString());
                    data.put("menuName", menuName);
                    data.put(KEY_RESTAURANT_ID, restaurant.getId().toString());
                    data.put("restaurantName", restaurantName);
                    data.put(KEY_NOTIFICATION_TYPE, NOTIF_MENU_LIVE_AT_RESTAURANT);

                    NotificationMessage message = notificationBuilder.buildMessage(
                            NotificationTemplate.Templates.MENU_LIVE_AT_RESTAURANT,
                            managerLocale,
                            new Object[]{menuName, restaurantName},
                            data);

                    boolean activeSession = hasActiveSession(manager.getId());
                    String deviceTokenForLog = manager.getDeviceToken();
                    if ((deviceTokenForLog == null || deviceTokenForLog.trim().isEmpty()) && userRepository != null) {
                        try {
                            User reloadedForLog = userRepository.findById(manager.getId()).orElse(null);
                            if (reloadedForLog != null) {
                                deviceTokenForLog = reloadedForLog.getDeviceToken();
                            }
                        } catch (Exception e) {
                            log.debug("[MENU_LIVE][FCM] token reload for log failed managerId={}: {}", manager.getId(), e.getMessage());
                        }
                    }
                    boolean tokenPresent = deviceTokenForLog != null && !deviceTokenForLog.trim().isEmpty();
                    log.info("[MENU_LIVE][FCM] payload managerId={} menuId={} restaurantId={} managerLocale={} activeSession={} deviceToken={} wsTopic={} notificationType={} title=\"{}\" body=\"{}\"",
                            manager.getId(),
                            menu.getId(),
                            restaurant.getId(),
                            managerLocale,
                            activeSession,
                            tokenPresent ? "present" : "absent",
                            MANAGER_TOPIC,
                            NOTIF_MENU_LIVE_AT_RESTAURANT,
                            message.getTitle(),
                            message.getBody());
                    if (!activeSession) {
                        log.warn("[MENU_LIVE][FCM] managerId={} has no active session — sendToUser returns immediately (no WebSocket / Rabbit FCM pipeline). DB row is still saved after.",
                                manager.getId());
                    }
                    if (!tokenPresent) {
                        log.warn("[MENU_LIVE][FCM] managerId={} has no device token — FCM payload will not include deviceToken (integration may skip push).",
                                manager.getId());
                    }

                    sendToUser(manager, message, MANAGER_TOPIC);
                    log.info("[MENU_LIVE][FCM] sendToUser completed managerId={} menuId={} restaurantId={}",
                            manager.getId(), menu.getId(), restaurant.getId());

                    saveNotificationToDatabase(manager, message, NOTIF_MENU_LIVE_AT_RESTAURANT, null);
                    log.info("[MENU_LIVE][FCM] saved in-app notification row managerId={} type={}",
                            manager.getId(), NOTIF_MENU_LIVE_AT_RESTAURANT);
                    log.info("Sent menu live notification to manager {} (locale: {}) for menu {} at restaurant {}",
                            manager.getId(), managerLocale.getLanguage(), menu.getId(), restaurant.getId());
                } catch (Exception e) {
                    log.error("Failed to send menu live notification to manager {}: {}", manager.getId(), e.getMessage(), e);
                }
            }
        } catch (Exception e) {
            log.error("Failed to send menu live notification: {}", e.getMessage(), e);
        }
    }

    // ==================== PAYMENT NOTIFICATIONS ====================
    
    /**
     * Sends a notification to the waiter/cashier when payment is completed.
     * Uses different topics based on the user's role (cashier vs waiter).
     *
     * @param order The order for which payment was completed
     * @param waiter The waiter/cashier to notify (must not be null)
     * @param paymentMethod The payment method used
     * @param amountPaid The amount that was paid
     * @param userLocale Locale for message localization
     */
    @Override
    public void notifyPaymentCompleted(com.gulfnet.shared_library.entity.Order order, User waiter, String paymentMethod, 
                                      BigDecimal amountPaid, Locale userLocale) {
        if (waiter == null) {
            log.warn("Cannot send payment completed notification: user is null");
            return;
        }
        
        try {
            Locale loc = localeForWaiterRecipient(waiter, userLocale);
            String formattedAmountPaid = formatAmountForNotification(amountPaid);
            Map<String, String> paymentData = buildPaymentData(order, paymentMethod, amountPaid, null);
            
            String tableCode = getTableCodeFromOrder(order);
            NotificationMessage message = notificationBuilder.buildMessage(
                    NotificationTemplate.Templates.PAYMENT_COMPLETED,
                    loc,
                    new Object[]{
                            tableCode,
                            paymentMethod,
                            formattedAmountPaid
                    },
                    paymentData
            );

            // Determine the correct WebSocket topic based on user role
            if (isCashier(waiter)) {
                // Send via WebSocket only (no FCM for Windows cashier app) with specific notification type
                sendWebSocketNotification(waiter.getId(), CASHIER_TOPIC, message, null, NOTIF_PAYMENT_COMPLETED);
                log.info("Sent payment completed notification to cashier {} via CASHIER_TOPIC", waiter.getId());
            } else {
                // Send to waiter via ORDER_UPDATE_TOPIC (sendToUser handles null device tokens gracefully)
                sendToUser(waiter, message, ORDER_UPDATE_TOPIC);
                log.info("Sent payment completed notification to waiter {} via ORDER_UPDATE_TOPIC", waiter.getId());
            }
            
            // Always save notification to database regardless of push delivery success
            saveNotificationToDatabase(waiter, message, NOTIF_PAYMENT_COMPLETED, null);
            
        } catch (Exception e) {
            log.error("Failed to send payment completed notification to user {}: {}", waiter.getId(), e.getMessage(), e);
        }
    }
    
    /**
     * Sends a notification to the cashier when a payment error occurs.
     * Sent via WebSocket only (no FCM for Windows cashier app).
     *
     * @param cashier The cashier to notify (must not be null)
     * @param transaction The transaction that encountered an error
     * @param errorType Type of error that occurred
     * @param errorMessage Detailed error message
     * @param userLocale Locale for message localization
     */
    @Override
    public void notifyPaymentError(User cashier, com.gulfnet.shared_library.entity.Transaction transaction, 
                                  String errorType, String errorMessage, Locale userLocale) {
        if (cashier == null) {
            log.warn("Cannot send payment error notification: cashier is null");
            return;
        }
        
        try {
            Locale loc = localeForRecipient(cashier, userLocale);
            // Use specific notification type for failed/expired so listing shows "Payment Failed" or "Payment Expired"
            String notificationType;
            NotificationTemplate template;
            Object[] messageArgs;
            String resolvedErrorMsg = errorMessage != null ? errorMessage : messageUtil.getMessage("notification.payment.error.generic", loc);
            String txRef = transaction.getTransactionNumber() != null ? transaction.getTransactionNumber() : transaction.getId().toString();

            if (NOTIF_PAYMENT_EXPIRED.equalsIgnoreCase(errorType)) {
                notificationType = NOTIF_PAYMENT_EXPIRED;
                template = NotificationTemplate.Templates.PAYMENT_EXPIRED;
                messageArgs = new Object[]{ txRef, resolvedErrorMsg };
            } else if (NOTIF_PAYMENT_FAILED.equalsIgnoreCase(errorType)) {
                notificationType = NOTIF_PAYMENT_FAILED;
                template = NotificationTemplate.Templates.PAYMENT_FAILED;
                messageArgs = new Object[]{ txRef, resolvedErrorMsg };
            } else {
                notificationType = NOTIF_PAYMENT_ERROR;
                template = NotificationTemplate.Templates.PAYMENT_ERROR;
                messageArgs = new Object[]{ txRef, errorType, resolvedErrorMsg };
            }

            Map<String, String> errorData = buildPaymentErrorData(transaction, errorType, errorMessage, loc, notificationType);
            
            NotificationMessage message = notificationBuilder.buildMessage(
                    template,
                    loc,
                    messageArgs,
                    errorData
            );

            // Send via WebSocket only (no FCM for Windows cashier app) with specific notification type
            sendWebSocketNotification(cashier.getId(), CASHIER_TOPIC, message, null, notificationType);
            
            // Save notification to database
            saveNotificationToDatabase(cashier, message, notificationType, null);
            
            // Explicit list refresh so client shows pop-up / updates notification list
            sendListRefreshEvent(cashier.getId(), LIST_NOTIFICATIONS);
            
        } catch (Exception e) {
            log.error("Failed to send payment error notification to cashier {}: {}", cashier.getId(), e.getMessage(), e);
        }
    }

    /**
     * Notifies a waiter about a payment error for a transaction.
     * <p>
     * Uses waiter-preferred locale resolution and sends via {@link #sendToUser(User, NotificationMessage, String)}
     * on {@code ORDER_UPDATE_TOPIC} (WebSocket + FCM where applicable). The notification is also persisted and a list
     * refresh event is emitted so the frontend updates its notification list.
     * <p>
     * The {@code errorType} is normalized into one of the saved notification types:
     * {@code PAYMENT_EXPIRED}, {@code PAYMENT_FAILED}, or {@code PAYMENT_ERROR} (fallback).
     *
     * @param waiter waiter recipient (required)
     * @param transaction transaction that encountered the error (required)
     * @param errorType error discriminator (e.g. {@code PAYMENT_EXPIRED}/{@code PAYMENT_FAILED}; other values map to generic)
     * @param errorMessage optional detailed error message; if {@code null} a localized generic message is used
     * @param userLocale triggering/request locale (may be {@code null})
     */
    @Override
    public void notifyPaymentErrorToWaiter(User waiter, com.gulfnet.shared_library.entity.Transaction transaction, 
                                          String errorType, String errorMessage, Locale userLocale) {
        if (waiter == null) {
            log.warn("Cannot send payment error notification to waiter: waiter is null");
            return;
        }
        
        try {
            Locale loc = localeForWaiterRecipient(waiter, userLocale);
            String notificationType;
            NotificationTemplate template;
            Object[] messageArgs;
            String resolvedErrorMsg = errorMessage != null ? errorMessage : messageUtil.getMessage("notification.payment.error.generic", loc);
            String txRef = transaction.getTransactionNumber() != null ? transaction.getTransactionNumber() : transaction.getId().toString();

            if (NOTIF_PAYMENT_EXPIRED.equalsIgnoreCase(errorType)) {
                notificationType = NOTIF_PAYMENT_EXPIRED;
                template = NotificationTemplate.Templates.PAYMENT_EXPIRED;
                messageArgs = new Object[]{ txRef, resolvedErrorMsg };
            } else if (NOTIF_PAYMENT_FAILED.equalsIgnoreCase(errorType)) {
                notificationType = NOTIF_PAYMENT_FAILED;
                template = NotificationTemplate.Templates.PAYMENT_FAILED;
                messageArgs = new Object[]{ txRef, resolvedErrorMsg };
            } else {
                notificationType = NOTIF_PAYMENT_ERROR;
                template = NotificationTemplate.Templates.PAYMENT_ERROR;
                messageArgs = new Object[]{ txRef, errorType, resolvedErrorMsg };
            }

            Map<String, String> errorData = buildPaymentErrorData(transaction, errorType, errorMessage, loc, notificationType);
            NotificationMessage message = notificationBuilder.buildMessage(template, loc, messageArgs, errorData);

            // Send to waiter via ORDER_UPDATE_TOPIC with device token for FCM pop-up
            sendToUser(waiter, message, ORDER_UPDATE_TOPIC);
            
            saveNotificationToDatabase(waiter, message, notificationType, null);
            sendListRefreshEvent(waiter.getId(), LIST_NOTIFICATIONS);
            
        } catch (Exception e) {
            log.error("Failed to send payment error notification to waiter {}: {}", waiter.getId(), e.getMessage(), e);
        }
    }

    // ==================== CASHIER NOTIFICATIONS ====================
    
    /**
     * Sends a notification to the cashier about the manager's decision on a discount request.
     * Sent via WebSocket only (no FCM for Windows cashier app).
     * Note: Database notification is handled separately in user-management service.
     *
     * @param order The order for which discount was requested
     * @param cashier The cashier who requested the discount (must not be null)
     * @param isApproved Whether the discount was approved or rejected
     * @param comments Manager's comments on the decision
     * @param userLocale Locale for message localization
     */
    @Override
    public void notifyDiscountRequestDecision(com.gulfnet.shared_library.entity.Order order, User cashier, 
                                             boolean isApproved, String comments, Locale userLocale) {
        if (cashier == null) {
            log.warn("Cannot send discount request decision notification: cashier is null");
            return;
        }
        
        try {
            Locale loc = localeForRecipient(cashier, null);
            Map<String, String> orderData = buildOrderData(order, isApproved ? "DISCOUNT_REQUEST_APPROVED" : "DISCOUNT_REQUEST_DECLINED", comments, loc);
            orderData.put(KEY_IS_APPROVED, String.valueOf(isApproved));
            orderData.put(KEY_APPROVED, String.valueOf(isApproved)); // Also add as boolean string for consistency
            if (comments != null && !comments.trim().isEmpty()) {
                orderData.put(KEY_MANAGER_COMMENTS, comments);
            }
            
            NotificationMessage message = notificationBuilder.buildMessage(
                    isApproved ? NotificationTemplate.Templates.DISCOUNT_REQUEST_APPROVED : NotificationTemplate.Templates.DISCOUNT_REQUEST_DECLINED,
                    loc,
                    new Object[]{
                            order.getOrderNumber() != null ? order.getOrderNumber() : 
                                (order.getId() != null ? order.getId().toString() : ""),
                            comments != null ? comments : ""
                    },
                    orderData
            );

            // Send via WebSocket only (no FCM for Windows cashier app) with specific notification type
            // Use ADDITIONAL_DISCOUNT_REQUEST_* type to match the notification saved in user-management service
            // This ensures consistency and prevents duplicate notifications
            String notificationType = isApproved ? "ADDITIONAL_DISCOUNT_REQUEST_APPROVED" : "ADDITIONAL_DISCOUNT_REQUEST_DECLINED";
            sendWebSocketNotification(cashier.getId(), CASHIER_TOPIC, message, null, notificationType);
            
            // Note: For additional discount requests, the notification is already saved in 
            // user-management service's saveAdditionalDiscountRequestNotification method.
            // We only send WebSocket notification here to avoid duplicate DB entries.
            // If this method needs to save for other use cases, add a parameter to control saving.
            
        } catch (Exception e) {
            log.error("Failed to send discount request decision notification to cashier {}: {}", cashier.getId(), e.getMessage(), e);
        }
    }
    
    /**
     * Sends a notification to the cashier about the manager's decision on a transaction cancellation request.
     * Sent via WebSocket only (no FCM for Windows cashier app).
     *
     * @param transaction The transaction for which cancellation was requested
     * @param cashier The cashier who requested the cancellation (must not be null)
     * @param isApproved Whether the cancellation was approved or rejected
     * @param comments Manager's comments on the decision
     * @param userLocale Locale for message localization
     */
    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void notifyCancellationRequestDecisionForCashier(com.gulfnet.shared_library.entity.Transaction transaction, 
                                                           User cashier, boolean isApproved, String comments, Locale userLocale) {
        if (cashier == null) {
            log.warn("Cannot send cancellation request decision notification: cashier is null");
            return;
        }
        
        try {
            // Do not use the manager/request API locale as fallback — it caused Cashier pop-ups in English
            // while the notification list was localized via Accept-Language. Recipient language only, else English.
            Locale cashierLoc = localeForRecipient(cashier, null);
            Map<String, String> transactionData = buildTransactionData(transaction, isApproved ? NOTIF_CANCELLATION_APPROVED : NOTIF_CANCELLATION_REJECTED, comments, cashierLoc);
            transactionData.put(KEY_IS_APPROVED, String.valueOf(isApproved));
            transactionData.put(KEY_APPROVED, String.valueOf(isApproved)); // Also add as boolean string for consistency
            if (comments != null && !comments.trim().isEmpty()) {
                transactionData.put(KEY_MANAGER_COMMENTS, comments);
            }
            
            // Build the identifier for the notification message
            // Prefer transaction number, fallback to order number, then transaction ID
            String identifier = "";
            if (transaction.getTransactionNumber() != null && !transaction.getTransactionNumber().trim().isEmpty()) {
                identifier = transaction.getTransactionNumber();
            } else if (transaction.getOrder() != null && transaction.getOrder().getOrderNumber() != null && !transaction.getOrder().getOrderNumber().trim().isEmpty()) {
                identifier = transaction.getOrder().getOrderNumber();
            } else if (transaction.getId() != null) {
                identifier = "Transaction " + transaction.getId().toString();
            }
            
            NotificationMessage message = notificationBuilder.buildMessage(
                    isApproved ? NotificationTemplate.Templates.CANCELLATION_APPROVED : NotificationTemplate.Templates.CANCELLATION_REJECTED,
                    cashierLoc,
                    new Object[]{
                            identifier,
                            transaction.getOrder() != null ? getTableCodeFromOrder(transaction.getOrder()) : "",
                            comments != null ? comments : ""
                    },
                    transactionData
            );

            // Override title and body for transaction cancellation notifications to cashiers
            String tableCode = transaction.getOrder() != null ? getTableCodeFromOrder(transaction.getOrder()) : "";
            if (isApproved) {
                String transactionCancellationTitle = messageUtil.getMessage("notification.transaction.cancellation.approved.title", cashierLoc);
                String transactionCancellationBody = messageUtil.getMessage("notification.transaction.cancellation.approved.body", cashierLoc,
                        identifier,
                        tableCode,
                        comments != null ? comments : "");
                message.setTitle(transactionCancellationTitle);
                message.setBody(transactionCancellationBody);
            } else {
                String transactionCancellationTitle = messageUtil.getMessage("notification.transaction.cancellation.rejected.title", cashierLoc);
                String transactionCancellationBody = messageUtil.getMessage("notification.transaction.cancellation.rejected.body", cashierLoc,
                        identifier,
                        tableCode,
                        comments != null ? comments : "");
                message.setTitle(transactionCancellationTitle);
                message.setBody(transactionCancellationBody);
            }

            // Send via WebSocket only (no FCM for Windows cashier app) with specific notification type
            String notificationType = isApproved ? NOTIF_CANCELLATION_APPROVED : NOTIF_CANCELLATION_REJECTED;
            sendWebSocketNotification(cashier.getId(), CASHIER_TOPIC, message, null, notificationType);
            
            // Save notification to database (also sends LIST_NOTIFICATIONS refresh — avoid duplicating it below)
            saveNotificationToDatabase(cashier, message, notificationType, null);
            
            sendListRefreshEvent(cashier.getId(), LIST_REQUESTS);
            
        } catch (Exception e) {
            log.error("Failed to send cancellation request decision notification to cashier {}: {}", cashier.getId(), e.getMessage(), e);
        }
    }
    
    // ==================== HQ ADMIN ALERT NOTIFICATIONS ====================
    
    /**
     * Resolve a human-readable restaurant name for notifications, using translations
     * when available and falling back to group name and then restaurant code.
     */
    private String getRestaurantNameForLocale(Restaurant restaurant, Locale locale) {
        if (restaurant == null) {
            return "";
        }
        
        String restaurantName = "";
        
        try {
            if (restaurant.getTranslations() != null && !restaurant.getTranslations().isEmpty()) {
                Locale effectiveLocale = (locale != null) ? locale : Locale.forLanguageTag("en");
                String language = effectiveLocale.getLanguage();
                
                restaurantName = restaurant.getTranslations().stream()
                        .filter(t -> t.getLanguageCode() != null && t.getLanguageCode().startsWith(language))
                        .findFirst()
                        .map(com.gulfnet.shared_library.entity.RestaurantTranslation::getName)
                        .orElse(restaurant.getTranslations().get(0).getName());
            }
        } catch (Exception e) {
            log.debug("Could not access restaurant translations for alert notification: {}", e.getMessage());
        }
        
        if (restaurantName == null || restaurantName.trim().isEmpty()) {
            if (restaurant.getRestaurantGroupName() != null && !restaurant.getRestaurantGroupName().trim().isEmpty()) {
                restaurantName = restaurant.getRestaurantGroupName();
            } else if (restaurant.getRestaurantCode() != null && !restaurant.getRestaurantCode().trim().isEmpty()) {
                restaurantName = restaurant.getRestaurantCode();
            } else {
                restaurantName = "";
            }
        }
        
        return restaurantName;
    }
    
    /**
     * Sends alert notifications to all HQ Admins when a restaurant's sales threshold is breached.
     * Resolves alert configuration for the restaurant and checks if alerts are enabled.
     * Saves notifications to database even if push notification fails.
     *
     * @param restaurant The restaurant whose sales threshold was breached
     * @param totalSales The total sales amount that breached the threshold
     * @param userLocale Locale for message localization
     */
    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void notifyHqAdminsSalesThresholdBreached(Restaurant restaurant, BigDecimal totalSales, Locale userLocale) {
        if (restaurant == null) {
            log.warn("Cannot send sales threshold alert: restaurant is null");
            return;
        }
        
        if (roleRepository == null || userRepository == null) {
            log.warn(LOG_ROLE_OR_USER_REPO_NOT_AVAILABLE);
            return;
        }
        
        // Variables that need to be accessible for database save even if message building fails
        java.util.List<User> hqAdmins = null;
        NotificationMessage message = null;
        String fallbackTitle = null;
        String fallbackBody = null;
        
        try {
            // Resolve effective alert configuration for this restaurant (Restaurant → Group → Account defaults)
            AlertConfigurationResolver.ResolvedAlertConfig resolvedConfig =
                    alertConfigurationResolver.resolveForRestaurant(restaurant);

            if (!resolvedConfig.isAlertsEnabled()) {
                log.debug("Alerts are disabled for restaurant {} - skipping sales threshold alert",
                        restaurant.getId());
                return;
            }

            // Find HQ Admin role
            java.util.Optional<com.gulfnet.shared_library.entity.Role> hqRoleOpt = 
                    roleRepository.findByName(ROLE_HQ_ADMIN);
            if (hqRoleOpt.isEmpty()) {
                log.warn("HQ_ADMIN role not found - cannot send sales threshold alerts");
                return;
            }
            
            java.util.UUID hqRoleId = hqRoleOpt.get().getId();
            org.springframework.data.domain.Pageable pageable = 
                    org.springframework.data.domain.PageRequest.of(0, 1000);
            org.springframework.data.domain.Page<com.gulfnet.shared_library.entity.User> hqPage =
                    userRepository.findAllByRoleIdAndIsDeletedFalse(hqRoleId, pageable);
            hqAdmins = hqPage.getContent();
            
            if (hqAdmins == null || hqAdmins.isEmpty()) {
                log.warn("No HQ Admin users found for sales threshold alert");
                return;
            }

            // Current-status check: skip if this alert was already sent today for this restaurant (prevents duplicate pop-ups)
            long ts = System.currentTimeMillis();
            log.info("[SALES_THRESHOLD_DEBUG] NOTIFY_ENTER ts={} restaurant={} restaurantId={} - duplicate check starting", ts, restaurant.getRestaurantCode(), restaurant.getId());
            LocalDate today = LocalDate.now(ZoneOffset.UTC);
            OffsetDateTime startOfDay = today.atStartOfDay().atOffset(ZoneOffset.UTC);
            OffsetDateTime endOfDay = today.plusDays(1).atStartOfDay().atOffset(ZoneOffset.UTC);
            List<Notification> existingSalesAlerts =
                    notificationRepository.findByTypeAndCreatedAtBetween("SALES_THRESHOLD_ALERT", startOfDay, endOfDay);
            String ridSentinel = String.format(ALERT_RID_SENTINEL_FORMAT, restaurant.getId());
            for (Notification n : existingSalesAlerts) {
                if (n.getMessage() != null && n.getMessage().contains(ridSentinel)) {
                    log.info("[SALES_THRESHOLD_DEBUG] NOTIFY_SKIPPED_DUPLICATE ts={} restaurant={} - existing alert found (id={}), skipping",
                            System.currentTimeMillis(), restaurant.getRestaurantCode(), n.getId());
                    log.info("Sales threshold alert already sent today for restaurant {} (currentStatus=alreadySent) - skipping duplicate",
                            restaurant.getRestaurantCode());
                    return;
                }
            }
            log.info("[SALES_THRESHOLD_DEBUG] NOTIFY_DUPCHECK_PASSED ts={} restaurant={} existingCount={} - proceeding to save and send FCM",
                    System.currentTimeMillis(), restaurant.getRestaurantCode(), existingSalesAlerts.size());

            // Build message - if this fails, we'll use fallback message for database save
            try {
                java.util.Map<String, String> data = new java.util.HashMap<>();
                data.put(KEY_RESTAURANT_ID, restaurant.getId().toString());
                data.put(KEY_RESTAURANT_CODE, restaurant.getRestaurantCode() != null ? restaurant.getRestaurantCode() : "");
                if (totalSales != null) {
                    data.put("totalSales", totalSales.toPlainString());
                }
                if (resolvedConfig.getSalesAlertThreshold() != null) {
                    data.put("salesAlertThreshold", resolvedConfig.getSalesAlertThreshold().toPlainString());
                }
                
                // Use human-readable restaurant name (with translation if available) for alerts
                String restaurantName = getRestaurantNameForLocale(restaurant, userLocale);
                
                String totalSalesStr = totalSales != null ? totalSales.toPlainString() : "0.00";
                String thresholdStr = resolvedConfig.getSalesAlertThreshold() != null 
                        ? resolvedConfig.getSalesAlertThreshold().toPlainString() 
                        : "-";
                
                message = notificationBuilder.buildMessage(
                        NotificationTemplate.Templates.SALES_THRESHOLD_ALERT,
                        userLocale,
                        new Object[]{restaurantName, totalSalesStr, thresholdStr},
                        data
                );
            } catch (Exception e) {
                log.warn("Failed to build notification message for sales threshold alert, will use fallback: {}", e.getMessage());
                // Create fallback message for database save using human-readable restaurant name
                String restaurantName = getRestaurantNameForLocale(restaurant, userLocale);
                String totalSalesStr = totalSales != null ? totalSales.toPlainString() : "0.00";
                String thresholdStr = resolvedConfig.getSalesAlertThreshold() != null 
                        ? resolvedConfig.getSalesAlertThreshold().toPlainString() 
                        : "-";
                fallbackTitle = "Sales Threshold Alert";
                fallbackBody = String.format("Sales threshold reached for restaurant %s. Total sales: %s, Threshold: %s", 
                        restaurantName, totalSalesStr, thresholdStr);
            }

            // Save notification to database FIRST so duplicate check (currentStatus) is visible before FCM;
            // this prevents HQ threshold notification from popping twice when multiple evaluations run close together.
            if (hqAdmins != null && !hqAdmins.isEmpty()) {
                String titleToSave = (message != null && message.getTitle() != null)
                        ? message.getTitle()
                        : (fallbackTitle != null ? fallbackTitle : "Sales Threshold Alert");
                String bodyToSave = (message != null && message.getBody() != null)
                        ? message.getBody()
                        : (fallbackBody != null ? fallbackBody : "Sales threshold has been reached");
                bodyToSave = bodyToSave + String.format(ALERT_DEDUP_RESTAURANT_ID_SUFFIX_FORMAT, restaurant.getId());

                int savedCount = 0;
                int failedCount = 0;
                for (User hqAdmin : hqAdmins) {
                    try {
                        saveNotificationToDatabase(hqAdmin, titleToSave, bodyToSave,
                                "SALES_THRESHOLD_ALERT", null, true);
                        savedCount++;
                    } catch (Exception e) {
                        failedCount++;
                        log.error("Failed to save sales threshold alert notification to database for HQ Admin {}: {}",
                                hqAdmin.getId(), e.getMessage(), e);
                    }
                }

                if (savedCount > 0) {
                    log.info("[SALES_THRESHOLD_DEBUG] NOTIFY_DB_SAVED ts={} restaurant={} savedCount={} hqAdminCount={}",
                            System.currentTimeMillis(), restaurant.getRestaurantCode(), savedCount, hqAdmins.size());
                    log.info("Successfully saved sales threshold alert notifications to database for {} HQ Admin(s)", savedCount);
                }
                if (failedCount > 0) {
                    log.error("Failed to save sales threshold alert notifications to database for {} HQ Admin(s)", failedCount);
                }
            }

            // Send FCM only after DB save so concurrent evaluators see currentStatus=alreadySent and skip (no duplicate pop-up)
            if (message != null && hqAdmins != null && !hqAdmins.isEmpty()) {
                try {
                    log.info("[SALES_THRESHOLD_DEBUG] NOTIFY_FCM_SENDING ts={} restaurant={} hqAdminCount={} - publishing to RabbitMQ",
                            System.currentTimeMillis(), restaurant.getRestaurantCode(), hqAdmins.size());
                    sendFcmOnlyToUsersWithRecipientLocales(hqAdmins, userLocale, loc -> {
                        java.util.Map<String, String> data = new java.util.HashMap<>();
                        data.put(KEY_RESTAURANT_ID, restaurant.getId().toString());
                        data.put(KEY_RESTAURANT_CODE, restaurant.getRestaurantCode() != null ? restaurant.getRestaurantCode() : "");
                        if (totalSales != null) {
                            data.put("totalSales", totalSales.toPlainString());
                        }
                        if (resolvedConfig.getSalesAlertThreshold() != null) {
                            data.put("salesAlertThreshold", resolvedConfig.getSalesAlertThreshold().toPlainString());
                        }
                        String restaurantName = getRestaurantNameForLocale(restaurant, loc);
                        String totalSalesStr = totalSales != null ? totalSales.toPlainString() : "0.00";
                        String thresholdStr = resolvedConfig.getSalesAlertThreshold() != null
                                ? resolvedConfig.getSalesAlertThreshold().toPlainString()
                                : "-";
                        return notificationBuilder.buildMessage(
                                NotificationTemplate.Templates.SALES_THRESHOLD_ALERT,
                                loc,
                                new Object[]{restaurantName, totalSalesStr, thresholdStr},
                                data);
                    }, MANAGER_TOPIC);
                    log.info("[SALES_THRESHOLD_DEBUG] NOTIFY_FCM_SENT ts={} restaurant={} - FCM messages published for {} HQ Admin(s)",
                            System.currentTimeMillis(), restaurant.getRestaurantCode(), hqAdmins.size());
                } catch (Exception e) {
                    log.warn("Failed to send push notification for sales threshold alert: {}", e.getMessage());
                }
            }
            
        } catch (Exception e) {
            log.error("Failed to prepare sales threshold alert for restaurant {}: {}", 
                    restaurant.getId(), e.getMessage(), e);
        }
    }
    
    /**
     * Sends alert notifications to all HQ Admins when a restaurant's refund percentage threshold is breached.
     * Resolves alert configuration for the restaurant and checks if alerts are enabled.
     * Saves notifications to database even if push notification fails.
     *
     * @param restaurant The restaurant whose refund percentage threshold was breached
     * @param refundPercentage The current refund percentage
     * @param thresholdPercentage The threshold percentage that was breached (can be null, uses config value)
     * @param userLocale Locale for message localization
     */
    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void notifyHqAdminsRefundPercentageBreached(Restaurant restaurant, BigDecimal refundPercentage, 
                                                       BigDecimal thresholdPercentage, Locale userLocale) {
        if (restaurant == null) {
            log.warn("Cannot send refund percentage alert: restaurant is null");
            return;
        }
        
        if (roleRepository == null || userRepository == null) {
            log.warn(LOG_ROLE_OR_USER_REPO_NOT_AVAILABLE);
            return;
        }
        
        // Variables that need to be accessible for database save even if message building fails
        java.util.List<User> hqAdmins = null;
        NotificationMessage message = null;
        String fallbackTitle = null;
        String fallbackBody = null;
        
        try {
            // Resolve effective alert configuration for this restaurant
            AlertConfigurationResolver.ResolvedAlertConfig resolvedConfig =
                    alertConfigurationResolver.resolveForRestaurant(restaurant);

            if (!resolvedConfig.isAlertsEnabled()) {
                log.debug("Alerts are disabled for restaurant {} - skipping refund percentage alert",
                        restaurant.getId());
                return;
            }

            java.util.Optional<com.gulfnet.shared_library.entity.Role> hqRoleOpt = 
                    roleRepository.findByName(ROLE_HQ_ADMIN);
            if (hqRoleOpt.isEmpty()) {
                log.warn("HQ_ADMIN role not found - cannot send refund percentage alerts");
                return;
            }
            
            java.util.UUID hqRoleId = hqRoleOpt.get().getId();
            org.springframework.data.domain.Pageable pageable = 
                    org.springframework.data.domain.PageRequest.of(0, 1000);
            org.springframework.data.domain.Page<com.gulfnet.shared_library.entity.User> hqPage =
                    userRepository.findAllByRoleIdAndIsDeletedFalse(hqRoleId, pageable);
            hqAdmins = hqPage.getContent();
            
            if (hqAdmins == null || hqAdmins.isEmpty()) {
                log.warn("No HQ Admin users found for refund percentage alert");
                return;
            }

            // Current-status check: skip if this alert was already sent today for this restaurant (prevents duplicate pop-ups)
            LocalDate todayRefund = LocalDate.now(ZoneOffset.UTC);
            OffsetDateTime startOfDayRefund = todayRefund.atStartOfDay().atOffset(ZoneOffset.UTC);
            OffsetDateTime endOfDayRefund = todayRefund.plusDays(1).atStartOfDay().atOffset(ZoneOffset.UTC);
            List<Notification> existingRefundAlerts =
                    notificationRepository.findByTypeAndCreatedAtBetween("REFUND_PERCENTAGE_ALERT", startOfDayRefund, endOfDayRefund);
            String ridSentinelRefund = String.format(ALERT_RID_SENTINEL_FORMAT, restaurant.getId());
            for (Notification n : existingRefundAlerts) {
                if (n.getMessage() != null && n.getMessage().contains(ridSentinelRefund)) {
                    log.info("Refund percentage alert already sent today for restaurant {} (currentStatus=alreadySent) - skipping duplicate",
                            restaurant.getRestaurantCode());
                    return;
                }
            }

            // Build message - if this fails, we'll use fallback message for database save
            try {
                java.util.Map<String, String> data = new java.util.HashMap<>();
                data.put(KEY_RESTAURANT_ID, restaurant.getId().toString());
                data.put(KEY_RESTAURANT_CODE, restaurant.getRestaurantCode() != null ? restaurant.getRestaurantCode() : "");
                if (refundPercentage != null) {
                    data.put("refundPercentage", refundPercentage.toPlainString());
                }
                BigDecimal effectiveThreshold = thresholdPercentage != null
                        ? thresholdPercentage
                        : resolvedConfig.getRefundAlertPercentage();
                if (effectiveThreshold != null) {
                    data.put("refundThreshold", effectiveThreshold.toPlainString());
                }
                
                // Use human-readable restaurant name (with translation if available) for alerts
                String restaurantName = getRestaurantNameForLocale(restaurant, userLocale);
                
                String refundPctStr = refundPercentage != null ? refundPercentage.toPlainString() : "0";
                String thresholdStr = effectiveThreshold != null ? effectiveThreshold.toPlainString() : "-";
                
                message = notificationBuilder.buildMessage(
                        NotificationTemplate.Templates.REFUND_PERCENTAGE_ALERT,
                        userLocale,
                        new Object[]{restaurantName, refundPctStr, thresholdStr},
                        data
                );
            } catch (Exception e) {
                log.warn("Failed to build notification message for refund percentage alert, will use fallback: {}", e.getMessage());
                // Create fallback message for database save using human-readable restaurant name
                String restaurantName = getRestaurantNameForLocale(restaurant, userLocale);
                String refundPctStr = refundPercentage != null ? refundPercentage.toPlainString() : "0";
                BigDecimal effectiveThreshold = thresholdPercentage != null
                        ? thresholdPercentage
                        : resolvedConfig.getRefundAlertPercentage();
                String thresholdStr = effectiveThreshold != null ? effectiveThreshold.toPlainString() : "-";
                fallbackTitle = "Refund Percentage Alert";
                fallbackBody = String.format("High refund percentage detected for restaurant %s. Refund percentage: %s%%, Threshold: %s%%", 
                        restaurantName, refundPctStr, thresholdStr);
            }

            // Save notification to database FIRST so duplicate check (currentStatus) is visible before FCM
            if (hqAdmins != null && !hqAdmins.isEmpty()) {
                String titleToSave = (message != null && message.getTitle() != null)
                        ? message.getTitle()
                        : (fallbackTitle != null ? fallbackTitle : "Refund Percentage Alert");
                String bodyToSave = (message != null && message.getBody() != null)
                        ? message.getBody()
                        : (fallbackBody != null ? fallbackBody : "Refund percentage threshold has been reached");
                bodyToSave = bodyToSave + String.format(ALERT_DEDUP_RESTAURANT_ID_SUFFIX_FORMAT, restaurant.getId());

                int savedCount = 0;
                int failedCount = 0;
                for (User hqAdmin : hqAdmins) {
                    try {
                        saveNotificationToDatabase(hqAdmin, titleToSave, bodyToSave,
                                "REFUND_PERCENTAGE_ALERT", null, true);
                        savedCount++;
                    } catch (Exception e) {
                        failedCount++;
                        log.error("Failed to save refund percentage alert notification to database for HQ Admin {}: {}",
                                hqAdmin.getId(), e.getMessage(), e);
                    }
                }

                if (savedCount > 0) {
                    log.info("Successfully saved refund percentage alert notifications to database for {} HQ Admin(s)", savedCount);
                }
                if (failedCount > 0) {
                    log.error("Failed to save refund percentage alert notifications to database for {} HQ Admin(s)", failedCount);
                }
            }

            // Send FCM only after DB save so concurrent evaluators see currentStatus=alreadySent and skip
            if (message != null && hqAdmins != null && !hqAdmins.isEmpty()) {
                try {
                    sendFcmOnlyToUsersWithRecipientLocales(hqAdmins, userLocale, loc -> {
                        java.util.Map<String, String> data = new java.util.HashMap<>();
                        data.put(KEY_RESTAURANT_ID, restaurant.getId().toString());
                        data.put(KEY_RESTAURANT_CODE, restaurant.getRestaurantCode() != null ? restaurant.getRestaurantCode() : "");
                        if (refundPercentage != null) {
                            data.put("refundPercentage", refundPercentage.toPlainString());
                        }
                        BigDecimal effectiveThreshold = thresholdPercentage != null
                                ? thresholdPercentage
                                : resolvedConfig.getRefundAlertPercentage();
                        if (effectiveThreshold != null) {
                            data.put("refundThreshold", effectiveThreshold.toPlainString());
                        }
                        String restaurantName = getRestaurantNameForLocale(restaurant, loc);
                        String refundPctStr = refundPercentage != null ? refundPercentage.toPlainString() : "0";
                        String thresholdStr = effectiveThreshold != null ? effectiveThreshold.toPlainString() : "-";
                        return notificationBuilder.buildMessage(
                                NotificationTemplate.Templates.REFUND_PERCENTAGE_ALERT,
                                loc,
                                new Object[]{restaurantName, refundPctStr, thresholdStr},
                                data);
                    }, MANAGER_TOPIC);
                } catch (Exception e) {
                    log.warn("Failed to send push notification for refund percentage alert: {}", e.getMessage());
                }
            }
            
        } catch (Exception e) {
            log.error("Failed to prepare refund percentage alert for restaurant {}: {}", 
                    restaurant.getId(), e.getMessage(), e);
        }
    }

    /**
     * Notifies HQ admins if the cancellation percentage threshold is breached.
     * <p>
     * Dedupe rule: at most one cancellation alert per restaurant per day (UTC). The method first persists notifications
     * (with list-refresh suppressed) so concurrent evaluators can observe the "already sent" status before any push is sent,
     * then sends FCM only. This avoids duplicate pop-ups on the frontend while still recording the alert in the DB.
     * <p>
     * The saved notification {@code type} varies based on which metric breached:
     * {@code CANCELLATION_PERCENTAGE_ALERT} (both), {@code ORDER_CANCELLATION_PERCENTAGE_ALERT} (order only),
     * or {@code TRANSACTION_CANCELLATION_PERCENTAGE_ALERT} (transaction only).
     *
     * @param restaurant restaurant whose metrics are being evaluated (required)
     * @param orderCancelPct computed order cancellation percentage (may be {@code null})
     * @param transactionCancelPct computed transaction cancellation percentage (may be {@code null})
     * @param thresholdPercentage optional override threshold; if {@code null} restaurant/global configuration is used
     * @param userLocale triggering/request locale used as a basis for recipient locale resolution (may be {@code null})
     * @param orderBreached whether the order cancellation percentage exceeded the threshold
     * @param transactionBreached whether the transaction cancellation percentage exceeded the threshold
     */
    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void notifyHqAdminsCancellationPercentageBreachedIfAny(Restaurant restaurant, BigDecimal orderCancelPct,
                                                                 BigDecimal transactionCancelPct, BigDecimal thresholdPercentage,
                                                                 Locale userLocale, boolean orderBreached, boolean transactionBreached) {
        if (restaurant == null) {
            log.warn("Cannot send cancellation percentage alert: restaurant is null");
            return;
        }
        if (roleRepository == null || userRepository == null) {
            log.warn(LOG_ROLE_OR_USER_REPO_NOT_AVAILABLE);
            return;
        }
        java.util.List<User> hqAdmins = null;
        try {
            AlertConfigurationResolver.ResolvedAlertConfig resolvedConfig =
                    alertConfigurationResolver.resolveForRestaurant(restaurant);
            if (!resolvedConfig.isAlertsEnabled()) {
                log.debug("Alerts are disabled for restaurant {} - skipping cancellation alert", restaurant.getId());
                return;
            }
            java.util.Optional<com.gulfnet.shared_library.entity.Role> hqRoleOpt = roleRepository.findByName(ROLE_HQ_ADMIN);
            if (hqRoleOpt.isEmpty()) {
                log.warn("HQ_ADMIN role not found - cannot send cancellation alerts");
                return;
            }
            java.util.UUID hqRoleId = hqRoleOpt.get().getId();
            org.springframework.data.domain.Pageable pageable = org.springframework.data.domain.PageRequest.of(0, 1000);
            org.springframework.data.domain.Page<com.gulfnet.shared_library.entity.User> hqPage =
                    userRepository.findAllByRoleIdAndIsDeletedFalse(hqRoleId, pageable);
            hqAdmins = hqPage.getContent();
            if (hqAdmins == null || hqAdmins.isEmpty()) {
                log.warn("No HQ Admin users found for cancellation alert");
                return;
            }

            // Current-status check: skip if any cancellation alert was already sent today for this restaurant (prevents duplicate pop-ups)
            LocalDate todayCancel = LocalDate.now(ZoneOffset.UTC);
            OffsetDateTime startOfDayCancel = todayCancel.atStartOfDay().atOffset(ZoneOffset.UTC);
            OffsetDateTime endOfDayCancel = todayCancel.plusDays(1).atStartOfDay().atOffset(ZoneOffset.UTC);
            String ridSentinelCancel = String.format(ALERT_RID_SENTINEL_FORMAT, restaurant.getId());
            for (String cancelType : new String[]{"CANCELLATION_PERCENTAGE_ALERT", "ORDER_CANCELLATION_PERCENTAGE_ALERT", "TRANSACTION_CANCELLATION_PERCENTAGE_ALERT"}) {
                List<Notification> existingCancelAlerts =
                        notificationRepository.findByTypeAndCreatedAtBetween(cancelType, startOfDayCancel, endOfDayCancel);
                for (Notification n : existingCancelAlerts) {
                    if (n.getMessage() != null && n.getMessage().contains(ridSentinelCancel)) {
                        return;
                    }
                }
            }

            // Save notification to database FIRST so duplicate check (currentStatus) is visible before FCM
            if (hqAdmins != null && !hqAdmins.isEmpty()) {
                String thresholdStrSave = thresholdPercentage != null ? thresholdPercentage.toPlainString() : "-";
                String orderStrSave = orderCancelPct != null ? orderCancelPct.toPlainString() : "0";
                String txnStrSave = transactionCancelPct != null ? transactionCancelPct.toPlainString() : "0";
                String savedType;
                if (orderBreached && transactionBreached) {
                    savedType = "CANCELLATION_PERCENTAGE_ALERT";
                } else if (orderBreached) {
                    savedType = "ORDER_CANCELLATION_PERCENTAGE_ALERT";
                } else {
                    savedType = "TRANSACTION_CANCELLATION_PERCENTAGE_ALERT";
                }
                int saved = 0;
                for (User hqAdmin : hqAdmins) {
                    try {
                        Locale loc = localeForRecipient(hqAdmin, userLocale);
                        String restaurantName = getRestaurantNameForLocale(restaurant, loc);
                        String title;
                        String body;
                        if (orderBreached && transactionBreached) {
                            title = messageUtil.getMessage("notification.alert.cancellation.percentage.combined.title", loc);
                            body = messageUtil.getMessage("notification.alert.cancellation.percentage.combined.body", loc, restaurantName, orderStrSave, txnStrSave, thresholdStrSave);
                        } else if (orderBreached) {
                            title = messageUtil.getMessage("notification.alert.order.cancellation.percentage.title", loc);
                            body = messageUtil.getMessage("notification.alert.order.cancellation.percentage.body", loc, restaurantName, orderStrSave, thresholdStrSave);
                        } else {
                            title = messageUtil.getMessage("notification.alert.transaction.cancellation.percentage.title", loc);
                            body = messageUtil.getMessage("notification.alert.transaction.cancellation.percentage.body", loc, restaurantName, txnStrSave, thresholdStrSave);
                        }
                        body = body + String.format(ALERT_DEDUP_RESTAURANT_ID_SUFFIX_FORMAT, restaurant.getId());
                        saveNotificationToDatabase(hqAdmin, title, body, savedType, null, true);
                        saved++;
                    } catch (Exception e) {
                        log.error("Failed to save {} for HQ Admin {}: {}", savedType, hqAdmin.getId(), e.getMessage(), e);
                    }
                }
                if (saved > 0) {
                    log.info("Successfully saved cancellation alert notifications to database for {} HQ Admin(s)", saved);
                }
            }

            // Send FCM only after DB save so concurrent evaluators see currentStatus=alreadySent and skip
            if (hqAdmins != null && !hqAdmins.isEmpty()) {
                try {
                    sendFcmOnlyToUsersWithRecipientLocales(hqAdmins, userLocale, loc -> {
                        try {
                            String rn = getRestaurantNameForLocale(restaurant, loc);
                            BigDecimal effTh = thresholdPercentage != null ? thresholdPercentage
                                    : resolvedConfig.getCancellationAlertPercentage();
                            String ts = effTh != null ? effTh.toPlainString() : "-";
                            java.util.Map<String, String> d = new java.util.HashMap<>();
                            d.put(KEY_RESTAURANT_ID, restaurant.getId().toString());
                            d.put(KEY_RESTAURANT_CODE, restaurant.getRestaurantCode() != null ? restaurant.getRestaurantCode() : "");
                            if (effTh != null) {
                                d.put("cancellationThreshold", effTh.toPlainString());
                            }
                            if (orderBreached && transactionBreached) {
                                String orderStr = orderCancelPct != null ? orderCancelPct.toPlainString() : "0";
                                String txnStr = transactionCancelPct != null ? transactionCancelPct.toPlainString() : "0";
                                d.put("orderCancellationPercentage", orderStr);
                                d.put("transactionCancellationPercentage", txnStr);
                                return notificationBuilder.buildMessage(
                                        NotificationTemplate.Templates.CANCELLATION_PERCENTAGE_COMBINED_ALERT,
                                        loc, new Object[]{rn, orderStr, txnStr, ts}, d);
                            } else if (orderBreached) {
                                String orderStr = orderCancelPct != null ? orderCancelPct.toPlainString() : "0";
                                d.put(KEY_CANCELLATION_PERCENTAGE, orderStr);
                                return notificationBuilder.buildMessage(
                                        NotificationTemplate.Templates.ORDER_CANCELLATION_PERCENTAGE_ALERT,
                                        loc, new Object[]{rn, orderStr, ts}, d);
                            } else {
                                String txnStr = transactionCancelPct != null ? transactionCancelPct.toPlainString() : "0";
                                d.put(KEY_CANCELLATION_PERCENTAGE, txnStr);
                                return notificationBuilder.buildMessage(
                                        NotificationTemplate.Templates.TRANSACTION_CANCELLATION_PERCENTAGE_ALERT,
                                        loc, new Object[]{rn, txnStr, ts}, d);
                            }
                        } catch (Exception e) {
                            log.warn("Cancellation alert template build failed, using message keys: {}", e.getMessage());
                            return buildCancellationPercentageAlertFallbackFromMessageKeys(
                                    loc, restaurant, resolvedConfig, thresholdPercentage,
                                    orderBreached, transactionBreached, orderCancelPct, transactionCancelPct);
                        }
                    }, MANAGER_TOPIC);
                } catch (Exception e) {
                    log.warn("Failed to send push for cancellation alert: {}", e.getMessage());
                }
            }
        } catch (Exception e) {
            log.error("Failed to prepare cancellation alert for restaurant {}: {}", restaurant.getId(), e.getMessage(), e);
        }
    }
    
    /**
     * Sends a notification to the cashier about the manager's decision on a refund request.
     * Sent via WebSocket only (no FCM for Windows cashier app).
     *
     * @param transaction The transaction for which refund was requested
     * @param cashier The cashier who requested the refund (must not be null)
     * @param isApproved Whether the refund was approved or rejected
     * @param comments Manager's comments on the decision
     * @param userLocale Locale for message localization
     */
    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void notifyRefundRequestDecision(com.gulfnet.shared_library.entity.Transaction transaction, User cashier, 
                                           boolean isApproved, String comments, Locale userLocale) {
        if (cashier == null) {
            log.warn("Cannot send refund request decision notification: cashier is null");
            return;
        }
        
        try {
            Locale loc = localeForRecipient(cashier, null);
            Map<String, String> transactionData = buildTransactionData(transaction, isApproved ? NOTIF_REFUND_REQUEST_APPROVED : NOTIF_REFUND_REQUEST_DECLINED, comments, loc);
            transactionData.put(KEY_IS_APPROVED, String.valueOf(isApproved));
            transactionData.put(KEY_APPROVED, String.valueOf(isApproved)); // Also add as boolean string for consistency
            if (comments != null && !comments.trim().isEmpty()) {
                transactionData.put(KEY_MANAGER_COMMENTS, comments);
            }
            
            NotificationMessage message = notificationBuilder.buildMessage(
                    isApproved ? NotificationTemplate.Templates.REFUND_REQUEST_APPROVED : NotificationTemplate.Templates.REFUND_REQUEST_DECLINED,
                    loc,
                    new Object[]{
                            transaction.getTransactionNumber() != null ? transaction.getTransactionNumber() : 
                                (transaction.getId() != null ? transaction.getId().toString() : ""),
                            comments != null ? comments : ""
                    },
                    transactionData
            );

            // Send via WebSocket only (no FCM for Windows cashier app) with specific notification type
            String notificationType = isApproved ? NOTIF_REFUND_REQUEST_APPROVED : NOTIF_REFUND_REQUEST_DECLINED;
            sendWebSocketNotification(cashier.getId(), CASHIER_TOPIC, message, null, notificationType);
            
            // Save notification to database (also sends LIST_NOTIFICATIONS refresh — avoid duplicating it below)
            saveNotificationToDatabase(cashier, message, notificationType, null);
            
            sendListRefreshEvent(cashier.getId(), LIST_REQUESTS);
            
        } catch (Exception e) {
            log.error("Failed to send refund request decision notification to cashier {}: {}", cashier.getId(), e.getMessage(), e);
        }
    }
    
    /**
     * Sends a notification to the cashier when a device integration error occurs.
     * Sent via WebSocket only (no FCM for Windows cashier app).
     *
     * @param cashier The cashier to notify (must not be null)
     * @param deviceType Type of device that encountered an error
     * @param errorMessage Detailed error message
     * @param additionalInfo Additional information about the error (can be null)
     * @param userLocale Locale for message localization
     */
    @Override
    public void notifyDeviceIntegrationError(User cashier, String deviceType, String errorMessage, 
                                            String additionalInfo, Locale userLocale) {
        if (cashier == null) {
            log.warn("Cannot send device integration error notification: cashier is null");
            return;
        }
        
        try {
            Locale loc = localeForRecipient(cashier, null);
            Map<String, String> errorData = buildDeviceErrorData(deviceType, errorMessage, additionalInfo);
            
            NotificationMessage message = notificationBuilder.buildMessage(
                    NotificationTemplate.Templates.DEVICE_INTEGRATION_ERROR,
                    loc,
                    new Object[]{
                            deviceType != null ? deviceType : messageUtil.getMessage("notification.device.error.unknown.device", loc),
                            errorMessage != null ? errorMessage : messageUtil.getMessage("notification.device.error.generic", loc)
                    },
                    errorData
            );

            // Send via WebSocket only (no FCM for Windows cashier app) with specific notification type
            sendWebSocketNotification(cashier.getId(), CASHIER_TOPIC, message, null, NOTIF_DEVICE_INTEGRATION_ERROR);
            
            // Save notification to database
            saveNotificationToDatabase(cashier, message, NOTIF_DEVICE_INTEGRATION_ERROR, null);
            
        } catch (Exception e) {
            log.error("Failed to send device integration error notification to cashier {}: {}", cashier.getId(), e.getMessage(), e);
        }
    }
    
    // ==================== CASH DRAWER SHIFT DISCREPANCY NOTIFICATIONS ====================
    
    /**
     * Sends notifications to managers when a cash drawer shift discrepancy request is opened.
     * Notifies all provided managers about the pending discrepancy request.
     *
     * @param cashierShift The cashier shift with the discrepancy
     * @param cashier The cashier who reported the discrepancy (must not be null)
     * @param managers List of managers to notify (must not be null or empty)
     * @param userLocale Locale for message localization
     */
    @Override
    public void notifyCashDrawerShiftDiscrepancyRequest(com.gulfnet.shared_library.entity.CashierShift cashierShift, 
                                                       User cashier, List<User> managers, Locale userLocale) {
        if (cashierShift == null || cashier == null) {
            log.warn("Cannot send cash drawer shift discrepancy request notification: cashierShift or cashier is null");
            return;
        }
        
        if (managers == null || managers.isEmpty()) {
            log.warn("No managers provided for cash drawer shift discrepancy request notification");
            return;
        }
        
        try {
            for (User manager : managers) {
                if (manager == null) {
                    continue;
                }
                Locale loc = localeForRecipient(manager, userLocale);
                Map<String, String> shiftData = buildCashierShiftData(cashierShift, NOTIF_CASH_DRAWER_SHIFT_DISCREPANCY_REQUEST, loc);
                shiftData.put(KEY_SHIFT_ID, cashierShift.getId().toString());
                shiftData.put(KEY_CASHIER_ID, cashier.getId().toString());
                shiftData.put(KEY_CASHIER_NAME, cashier.getFirstName() + " " + cashier.getLastName());
                if (cashierShift.getDiscrepancyReason() != null) {
                    shiftData.put("discrepancyReason", cashierShift.getDiscrepancyReason());
                }
                String cashierName = cashier.getFirstName() + " " + cashier.getLastName();
                String discrepancyAmount = cashierShift.getDiscrepancyAmount() != null ?
                        cashierShift.getDiscrepancyAmount().toString() : "0.00";
                NotificationMessage message = notificationBuilder.buildMessage(
                        NotificationTemplate.Templates.CASH_DRAWER_SHIFT_DISCREPANCY_REQUEST,
                        loc,
                        new Object[]{
                                cashierName,
                                discrepancyAmount
                        },
                        shiftData
                );
                sendToUser(manager, message, MANAGER_TOPIC);
                saveNotificationToDatabase(manager, message,
                        NOTIF_CASH_DRAWER_SHIFT_DISCREPANCY_REQUEST, cashier);
            }
            
        } catch (Exception e) {
            log.error("Failed to send cash drawer shift discrepancy request notification to managers: {}", e.getMessage(), e);
        }
    }
    
    /**
     * Sends a notification to the cashier about the manager's decision on a cash drawer shift discrepancy request.
     * Sent via WebSocket only (no FCM for Windows cashier app).
     *
     * @param cashierShift The cashier shift with the discrepancy
     * @param cashier The cashier who reported the discrepancy (must not be null)
     * @param isApproved Whether the discrepancy was approved or rejected
     * @param comments Manager's comments on the decision
     * @param manager The manager who made the decision (can be null)
     * @param userLocale Locale for message localization
     */
    @Override
    public void notifyCashDrawerShiftDiscrepancyDecision(com.gulfnet.shared_library.entity.CashierShift cashierShift, 
                                                        User cashier, boolean isApproved, String comments, 
                                                        User manager, Locale userLocale) {
        if (cashierShift == null || cashier == null) {
            log.warn("Cannot send cash drawer shift discrepancy decision notification: cashierShift or cashier is null");
            return;
        }
        
        try {
            Locale loc = localeForRecipient(cashier, userLocale);
            Map<String, String> shiftData = buildCashierShiftData(cashierShift,
                    isApproved ? NOTIF_CASH_DRAWER_SHIFT_DISCREPANCY_APPROVED : NOTIF_CASH_DRAWER_SHIFT_DISCREPANCY_DECLINED,
                    loc);
            shiftData.put(KEY_SHIFT_ID, cashierShift.getId().toString());
            shiftData.put(KEY_CASHIER_ID, cashier.getId().toString());
            shiftData.put(KEY_IS_APPROVED, String.valueOf(isApproved));
            shiftData.put(KEY_APPROVED, String.valueOf(isApproved));
            if (comments != null && !comments.trim().isEmpty()) {
                shiftData.put(KEY_MANAGER_COMMENTS, comments);
                shiftData.put(KEY_COMMENTS, comments);
            }
            if (manager != null) {
                shiftData.put("managerId", manager.getId().toString());
                shiftData.put("managerName", manager.getFirstName() + " " + manager.getLastName());
            }
            
            String discrepancyAmount = cashierShift.getDiscrepancyAmount() != null ? 
                    cashierShift.getDiscrepancyAmount().toString() : "0.00";
            
            NotificationMessage message = notificationBuilder.buildMessage(
                    isApproved ? NotificationTemplate.Templates.CASH_DRAWER_SHIFT_DISCREPANCY_APPROVED : 
                            NotificationTemplate.Templates.CASH_DRAWER_SHIFT_DISCREPANCY_DECLINED,
                    loc,
                    new Object[]{
                            discrepancyAmount,
                            comments != null ? comments : ""
                    },
                    shiftData
            );

            // Send via WebSocket only (no FCM for Windows cashier app) with specific notification type
            String notificationType = isApproved ? NOTIF_CASH_DRAWER_SHIFT_DISCREPANCY_APPROVED : 
                    NOTIF_CASH_DRAWER_SHIFT_DISCREPANCY_DECLINED;
            sendWebSocketNotification(cashier.getId(), CASHIER_TOPIC, message, null, notificationType);
            
            // Save notification to database
            saveNotificationToDatabase(cashier, message, notificationType, manager);
            
        } catch (Exception e) {
            log.error("Failed to send cash drawer shift discrepancy decision notification to cashier {}: {}", 
                    cashier.getId(), e.getMessage(), e);
        }
    }

    /**
     * Sends a notification to the cashier when their cash drawer shift is closed by a manager.
     * Sent via WebSocket only (no FCM for Windows cashier app).
     *
     * @param cashierShift The cashier shift that was closed
     * @param cashier The cashier whose shift was closed (must not be null)
     * @param manager The manager who closed the shift (must not be null)
     * @param userLocale Locale for message localization
     */
    @Override
    public void notifyCashDrawerShiftClosedByManager(com.gulfnet.shared_library.entity.CashierShift cashierShift,
                                                     User cashier,
                                                     User manager,
                                                     Locale userLocale) {
        if (cashierShift == null || cashier == null || manager == null) {
            log.warn("Cannot send cash drawer shift closed-by-manager notification: shift, cashier, or manager is null");
            return;
        }

        try {
            Map<String, String> shiftData = buildCashierShiftData(cashierShift,
                    "CASH_DRAWER_SHIFT_CLOSED_BY_MANAGER",
                    userLocale);
            shiftData.put(KEY_SHIFT_ID, cashierShift.getId().toString());
            shiftData.put(KEY_CASHIER_ID, cashier.getId().toString());
            shiftData.put("managerId", manager.getId().toString());
            shiftData.put("managerName", manager.getFirstName() + " " + manager.getLastName());

            String discrepancyAmount = cashierShift.getDiscrepancyAmount() != null
                    ? cashierShift.getDiscrepancyAmount().toString()
                    : "0.00";

            NotificationMessage message = notificationBuilder.buildMessage(
                    NotificationTemplate.Templates.CASH_DRAWER_SHIFT_CLOSED_BY_MANAGER,
                    userLocale,
                    new Object[]{
                            manager.getFirstName() + " " + manager.getLastName(),
                            discrepancyAmount
                    },
                    shiftData
            );

            // Send via WebSocket only (no FCM for Windows cashier app) with specific notification type
            String notificationType = "CASH_DRAWER_SHIFT_CLOSED_BY_MANAGER";
            sendWebSocketNotification(cashier.getId(), CASHIER_TOPIC, message, null, notificationType);

            // Save notification to database
            saveNotificationToDatabase(cashier, message, notificationType, manager);

        } catch (Exception e) {
            log.error("Failed to send cash drawer shift closed-by-manager notification to cashier {}: {}",
                    cashier.getId(), e.getMessage(), e);
        }
    }
    
    /**
     * Sends notifications to cashiers when an order is cancelled by a manager.
     * Sent via WebSocket only (no FCM for Windows cashier app).
     *
     * @param order The order that was cancelled
     * @param cashiers List of cashiers to notify (must not be null or empty)
     * @param cancellationReason Reason for the cancellation
     * @param userLocale Locale for message localization
     */
    @Override
    public void notifyCashiersOrderCancelledByManager(com.gulfnet.shared_library.entity.Order order, 
                                                     List<User> cashiers, String cancellationReason, 
                                                     Locale userLocale) {
        if (order == null) {
            log.warn("Cannot send order cancellation notification to cashiers: order is null");
            return;
        }
        
        if (cashiers == null || cashiers.isEmpty()) {
            log.debug("No cashiers to notify about order cancellation for order {}", order.getId());
            return;
        }
        
        try {
            // Safely get order number - fallback to order ID if order number is null
            String orderNumber = order.getOrderNumber() != null && !order.getOrderNumber().trim().isEmpty() 
                    && !"null".equalsIgnoreCase(order.getOrderNumber()) 
                    ? order.getOrderNumber() 
                    : (order.getId() != null ? order.getId().toString() : "");
            
            // Safely get table code
            String tableCode = getTableCodeFromOrder(order);

            String cancellationText = cancellationReason != null && !cancellationReason.trim().isEmpty() ? cancellationReason : "";

            log.info("=== CASHIER NOTIFICATION CHECK: Building ORDER_CANCELLED_BY_MANAGER notification for order {} (orderNumber={}, tableCode={}, cancellationReason='{}') for {} cashier(s) ===",
                    order.getId(),
                    orderNumber,
                    tableCode,
                    cancellationReason,
                    cashiers.size());

            String notificationType = "ORDER_CANCELLED_BY_MANAGER";

            // Send notification to all cashiers (per-recipient locale for list + WebSocket parity)
            for (User cashier : cashiers) {
                if (cashier != null && isCashier(cashier)) {
                    try {
                        Locale loc = localeForRecipient(cashier, userLocale);
                        Map<String, String> orderData = buildOrderData(order, "ORDER_CANCELLED_BY_MANAGER", cancellationReason, loc);
                        orderData.put(KEY_ORDER_ID, order.getId().toString());
                        orderData.put(KEY_ORDER_NUMBER, orderNumber);
                        orderData.put(KEY_TABLE_CODE, tableCode);
                        if (cancellationReason != null && !cancellationReason.trim().isEmpty()) {
                            orderData.put("cancellationReason", cancellationReason);
                        }

                        NotificationMessage message = notificationBuilder.buildMessage(
                                NotificationTemplate.Templates.CANCELLATION_APPROVED,
                                loc,
                                new Object[]{
                                        orderNumber,
                                        tableCode,
                                        cancellationText
                                },
                                orderData
                        );

                        String orderCancellationTitle = messageUtil.getMessage("notification.order.cancelled.by.manager.title", loc);
                        String orderCancellationBody = messageUtil.getMessage("notification.order.cancelled.by.manager.body", loc,
                                orderNumber,
                                tableCode,
                                cancellationText);
                        message.setTitle(orderCancellationTitle);
                        message.setBody(orderCancellationBody);

                        log.info("=== CASHIER NOTIFICATION CHECK: Notification prepared for order {} cashier {} with title='{}', type='{}', topic='{}' ===",
                                order.getId(),
                                cashier.getId(),
                                orderCancellationTitle,
                                notificationType,
                                CASHIER_TOPIC);

                        // Send via WebSocket to cashier (Windows app doesn't support FCM)
                        sendWebSocketNotification(cashier.getId(), CASHIER_TOPIC, message, null, notificationType);

                        // Save notification to database
                        saveNotificationToDatabase(cashier, message, notificationType, null);
                        log.info("=== CASHIER NOTIFICATION CHECK: Sent ORDER_CANCELLED_BY_MANAGER notification to cashier {} on topic {} and stored in DB (orderId={}) ===",
                                cashier.getId(),
                                CASHIER_TOPIC,
                                order.getId());
                    } catch (Exception e) {
                        log.error("Failed to send order cancellation notification to cashier {}: {}", 
                                cashier.getId(), e.getMessage(), e);
                    }
                }
            }
            
        } catch (Exception e) {
            log.error("Failed to send order cancellation notification to cashiers for order {}: {}", 
                    order.getId(), e.getMessage(), e);
        }
    }

    /**
     * Sends notifications to managers when a cash drawer shift is started.
     * Notifies all provided managers about the shift start.
     *
     * @param cashierShift The cashier shift that was started
     * @param cashier The cashier who started the shift (must not be null)
     * @param managers List of managers to notify (must not be null or empty)
     * @param userLocale Locale for message localization
     */
    @Override
    public void notifyCashDrawerShiftStarted(com.gulfnet.shared_library.entity.CashierShift cashierShift,
                                            User cashier,
                                            List<User> managers,
                                            Locale userLocale) {
        if (cashierShift == null || cashier == null) {
            log.warn("Cannot send cash drawer shift started notification: shift or cashier is null");
            return;
        }
        
        if (managers == null || managers.isEmpty()) {
            log.warn("No managers provided for cash drawer shift started notification");
            return;
        }
        
        try { 
            for (User manager : managers) {
                if (manager == null) {
                    continue;
                }
                Locale loc = localeForRecipient(manager, userLocale);
                Map<String, String> shiftData = buildCashierShiftData(cashierShift, NOTIF_CASH_DRAWER_SHIFT_STARTED, loc);
                shiftData.put(KEY_SHIFT_ID, cashierShift.getId().toString());
                shiftData.put(KEY_CASHIER_ID, cashier.getId().toString());
                shiftData.put(KEY_CASHIER_NAME, cashier.getFirstName() + " " + cashier.getLastName());
                String cashierName = cashier.getFirstName() + " " + cashier.getLastName();
                String cashDrawerName = resolveCashDrawerNameForNotification(cashierShift.getCashDrawer(), loc);
                String openingBalance = cashierShift.getOpeningBalance() != null ?
                        cashierShift.getOpeningBalance().toString() : "0.00";
                NotificationMessage message = notificationBuilder.buildMessage(
                        NotificationTemplate.Templates.CASH_DRAWER_SHIFT_STARTED,
                        loc,
                        new Object[]{
                                cashierName,
                                cashDrawerName,
                                openingBalance
                        },
                        shiftData
                );
                sendToUser(manager, message, MANAGER_TOPIC);
                saveNotificationToDatabase(manager, message,
                        NOTIF_CASH_DRAWER_SHIFT_STARTED, cashier);
            }
            
        } catch (Exception e) {
            log.error("Failed to send cash drawer shift started notification to managers: {}", e.getMessage(), e);
        }
    }

    /**
     * Sends notifications to managers when a cash drawer shift is closed.
     * Notifies all provided managers about the shift closure.
     *
     * @param cashierShift The cashier shift that was closed
     * @param cashier The cashier who closed the shift (must not be null)
     * @param managers List of managers to notify (must not be null or empty)
     * @param userLocale Locale for message localization
     */
    @Override
    public void notifyCashDrawerShiftClosed(com.gulfnet.shared_library.entity.CashierShift cashierShift,
                                           User cashier,
                                           List<User> managers,
                                           Locale userLocale) {
        if (cashierShift == null || cashier == null) {
            log.warn("Cannot send cash drawer shift closed notification: shift or cashier is null");
            return;
        }
        
        if (managers == null || managers.isEmpty()) {
            log.warn("No managers provided for cash drawer shift closed notification");
            return;
        }
        
        try {
            for (User manager : managers) {
                if (manager == null) {
                    continue;
                }
                Locale loc = localeForRecipient(manager, userLocale);
                Map<String, String> shiftData = buildCashierShiftData(cashierShift, NOTIF_CASH_DRAWER_SHIFT_CLOSED, loc);
                shiftData.put(KEY_SHIFT_ID, cashierShift.getId().toString());
                shiftData.put(KEY_CASHIER_ID, cashier.getId().toString());
                shiftData.put(KEY_CASHIER_NAME, cashier.getFirstName() + " " + cashier.getLastName());
                String cashierName = cashier.getFirstName() + " " + cashier.getLastName();
                String cashDrawerName = resolveCashDrawerNameForNotification(cashierShift.getCashDrawer(), loc);
                String closingBalance = cashierShift.getClosingBalance() != null ?
                        cashierShift.getClosingBalance().toString() : "0.00";
                String discrepancyAmount = cashierShift.getDiscrepancyAmount() != null ?
                        cashierShift.getDiscrepancyAmount().toString() : "0.00";
                NotificationMessage message = notificationBuilder.buildMessage(
                        NotificationTemplate.Templates.CASH_DRAWER_SHIFT_CLOSED,
                        loc,
                        new Object[]{
                                cashierName,
                                cashDrawerName,
                                closingBalance,
                                discrepancyAmount
                        },
                        shiftData
                );
                sendToUser(manager, message, MANAGER_TOPIC);
                saveNotificationToDatabase(manager, message,
                        NOTIF_CASH_DRAWER_SHIFT_CLOSED, cashier);
            }
            
        } catch (Exception e) {
            log.error("Failed to send cash drawer shift closed notification to managers: {}", e.getMessage(), e);
        }
    }

    // ==================== GENERIC NOTIFICATION METHODS ====================
    
    /**
     * Generic method to send a notification to a single user.
     * Resolves the notification template by type and sends via WebSocket and FCM (if device token available).
     *
     * @param user The user to notify (must not be null)
     * @param notificationType Type of notification to send
     * @param messageArgs Arguments for message template formatting
     * @param additionalData Additional data to include in the notification
     * @param userLocale Locale for message localization
     */
    @Override
    public void sendNotificationToUser(User user, String notificationType, Object[] messageArgs, 
                                      Map<String, String> additionalData, Locale userLocale) {
        try {
            NotificationTemplate template = getTemplateByType(notificationType);
            if (template == null) {
                log.warn("No template found for notification type: {}", notificationType);
                return;
            }
            
            Locale loc = localeForRecipient(user, userLocale);
            NotificationMessage message = notificationBuilder.buildMessage(template, loc, messageArgs, additionalData);
            sendToUser(user, message, getUserTopic(user));
            
            // Save notification to database
            saveNotificationToDatabase(user, message, notificationType, null);
            
        } catch (Exception e) {
            log.error("Failed to send notification to user {}: {}", user.getId(), e.getMessage(), e);
        }
    }
    
    /**
     * Generic method to send a notification to multiple users.
     * Delegates to the overloaded method with createdBy parameter set to null.
     *
     * @param users List of users to notify (must not be null or empty)
     * @param notificationType Type of notification to send
     * @param messageArgs Arguments for message template formatting
     * @param additionalData Additional data to include in the notification
     * @param userLocale Locale for message localization
     */
    @Override
    public void sendNotificationToUsers(List<User> users, String notificationType, Object[] messageArgs, 
                                       Map<String, String> additionalData, Locale userLocale) {
        sendNotificationToUsers(users, notificationType, messageArgs, additionalData, null, userLocale);
    }
    
    /**
     * Generic method to send a notification to multiple users.
     * Resolves the notification template by type and sends via WebSocket and FCM (if device tokens available).
     *
     * @param users List of users to notify (must not be null or empty)
     * @param notificationType Type of notification to send
     * @param messageArgs Arguments for message template formatting
     * @param additionalData Additional data to include in the notification
     * @param createdBy The user who triggered the notification (can be null)
     * @param userLocale Locale for message localization
     */
    @Override
    public void sendNotificationToUsers(List<User> users, String notificationType, Object[] messageArgs, 
                                       Map<String, String> additionalData, User createdBy, Locale userLocale) {
        try {
            NotificationTemplate template = getTemplateByType(notificationType);
            if (template == null) {
                log.warn("No template found for notification type: {}", notificationType);
                return;
            }
            
            for (User user : users) {
                if (user == null) {
                    continue;
                }
                Locale loc = localeForRecipient(user, userLocale);
                NotificationMessage message = notificationBuilder.buildMessage(template, loc, messageArgs, additionalData);
                sendToUser(user, message, MANAGER_TOPIC);
                saveNotificationToDatabase(user, message, notificationType, createdBy);
            }
            
        } catch (Exception e) {
            log.error("Failed to send notification to users: {}", e.getMessage(), e);
        }
    }
    
    @Override
    public void sendNotificationToRole(String role, String notificationType, Object[] messageArgs, 
                                     Map<String, String> additionalData, Locale userLocale) {
        // This would require user repository to find users by role
        // Implementation depends on your user management system
        log.warn("sendNotificationToRole not implemented - requires user repository integration");
    }
    
    /**
     * Sends notifications to managers (excluding the resolving manager) about a request resolution.
     * Filters out the manager who resolved the request to avoid duplicate notifications.
     *
     * @param managers List of managers to notify (must not be null or empty)
     * @param resolvingManager The manager who resolved the request (used to filter out from notification list)
     * @param requestType Type of request that was resolved
     * @param requestIdentifier Identifier of the request (e.g., request ID)
     * @param isApproved Whether the request was approved or rejected
     * @param comments Comments on the resolution
     * @param userLocale Locale for message localization
     */
    @Override
    public void notifyManagersAboutRequestResolution(List<User> managers, User resolvingManager, 
                                                    String requestType, String requestIdentifier, 
                                                    boolean isApproved, String comments, Locale userLocale) {
        if (managers == null || managers.isEmpty()) {
            log.debug("No managers to notify about request resolution");
            return;
        }
        
        // Filter out the manager who resolved the request (they already know)
        List<User> otherManagers = managers.stream()
                .filter(manager -> !manager.getId().equals(resolvingManager.getId()))
                .collect(java.util.stream.Collectors.toList());
        
        if (otherManagers.isEmpty()) {
            log.debug("No other managers to notify (only resolving manager is in the list)");
            return;
        }
        
        try {
            String action = isApproved ? VALUE_APPROVED : VALUE_DECLINED;
            String managerName = resolvingManager.getFirstName() + " " + resolvingManager.getLastName();
            
            // Build notification data
            Map<String, String> notificationData = new HashMap<>();
            notificationData.put(KEY_REQUEST_TYPE, requestType);
            notificationData.put("requestIdentifier", requestIdentifier);
            notificationData.put("action", action);
            notificationData.put(KEY_IS_APPROVED, String.valueOf(isApproved));
            notificationData.put("resolvingManagerId", resolvingManager.getId().toString());
            notificationData.put("resolvingManagerName", managerName);
            if (comments != null && !comments.trim().isEmpty()) {
                notificationData.put(KEY_COMMENTS, comments);
            }
            notificationData.put(KEY_NOTIFICATION_TYPE, "REQUEST_RESOLUTION");
            notificationData.put(KEY_TIMESTAMP, OffsetDateTime.now(ZoneOffset.UTC).format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
            
            // Send to all other managers (per-recipient locale for title/body)
            for (User manager : otherManagers) {
                if (manager == null) {
                    continue;
                }
                Locale loc = localeForRecipient(manager, userLocale);
                String title = messageUtil.getMessage("manager.notification.request.resolution.title", loc,
                        requestType, action);
                String body = messageUtil.getMessage("manager.notification.request.resolution.body", loc,
                        managerName, action, requestType, requestIdentifier);
                NotificationMessage message = NotificationMessage.builder()
                        .title(title)
                        .body(body)
                        .data(notificationData)
                        .build();
                sendToUser(manager, message, MANAGER_TOPIC);
                saveNotificationToDatabase(manager, title, body, "REQUEST_RESOLUTION", resolvingManager);
            }
            
            log.info("Notified {} manager(s) about {} request resolution by manager {}", 
                    otherManagers.size(), requestType, resolvingManager.getId());
            
        } catch (Exception e) {
            log.error("Failed to notify managers about request resolution: {}", e.getMessage(), e);
        }
    }

    // ==================== PRIVATE HELPER METHODS ====================
    
    /**
     * Check if user has an active session (logged in and session not expired).
     * Session is considered active only if a LoginAudit record exists and loginExpiryDate is in the future.
     * On logout the record is deleted; on session timeout the record remains but loginExpiryDate has passed.
     *
     * @param userId User ID to check
     * @return true if user has active (non-expired) session, false otherwise
     */
    private boolean hasActiveSession(java.util.UUID userId) {
        if (loginAuditRepository == null) {
            log.warn("LoginAuditRepository is not available, skipping session check for user {}", userId);
            // If repository is not available, assume session is active to avoid breaking notifications
            return true;
        }
        
        try {
            Optional<LoginAudit> auditOpt = loginAuditRepository.findByUser_Id(userId);
            if (auditOpt.isEmpty()) {
                log.info("User {} does not have an active session (no login audit), skipping notification", userId);
                return false;
            }
            OffsetDateTime expiry = auditOpt.get().getLoginExpiryDate();
            OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
            // Active = record exists and (no expiry set or expiry is in the future)
            boolean active = expiry == null || expiry.isAfter(now);
            if (!active) {
                log.info("User {} session has expired (expiry: {}), skipping notification", userId, expiry);
            } else {
                log.debug("User {} has active session - proceeding with notification", userId);
            }
            return active;
        } catch (Exception e) {
            log.error("Error checking session for user {}: {}", userId, e.getMessage(), e);
            // On error, assume session is active to avoid breaking notifications
            return true;
        }
    }
    
    /**
     * Send notification to a single user via WebSocket and RabbitMQ
     */
    private void sendToUser(User user, NotificationMessage message, String webSocketTopic) {
        if (user == null) {
            log.warn("Cannot send notification: user is null");
            return;
        }
        
        log.info("[Notification] sendToUser start userId={} webSocketTopic={}", user.getId(), webSocketTopic);
        
        // Check if user has active session before sending notification
        if (!hasActiveSession(user.getId())) {
            log.info("[Notification] sendToUser skipped userId={} webSocketTopic={} reason=noActiveSession", user.getId(), webSocketTopic);
            return;
        }
        
        // Reload user to ensure device token is loaded (in case of lazy loading)
        String deviceToken = user.getDeviceToken();
        if ((deviceToken == null || deviceToken.trim().isEmpty()) && userRepository != null) {
            try {
                User reloadedUser = userRepository.findById(user.getId()).orElse(null);
                if (reloadedUser != null) {
                    deviceToken = reloadedUser.getDeviceToken();
                }
            } catch (Exception e) {
                log.warn("[Notification] sendToUser failed to reload user for deviceToken userId={}: {}", user.getId(), e.getMessage());
            }
        }
        
        try {
            sendWebSocketNotification(user.getId(), webSocketTopic, message, deviceToken);
        } catch (Exception e) {
            log.error("[Notification] sendToUser failed userId={} webSocketTopic={}: {}", user.getId(), webSocketTopic, e.getMessage(), e);
        }
    }
    
    /**
     * Send notification to multiple users via WebSocket and RabbitMQ
     */
    private void sendToUsers(List<User> users, NotificationMessage message, String webSocketTopic) {
        if (users == null || users.isEmpty()) {
            return;
        }
        
        // Send WebSocket notifications and publish to RabbitMQ for integration service
        for (User user : users) {
            if (user == null) {
                continue;
            }
            
            // Check if user has active session before sending notification
            if (!hasActiveSession(user.getId())) {
                log.debug("Skipping notification for user {} - user is logged out", user.getId());
                continue;
            }
            
            try {
                // Ensure device token is loaded - reload user if needed to get device_token field
                String deviceToken = user.getDeviceToken();
                log.info("Initial device token for user {}: {}", user.getId(), 
                        deviceToken != null && !deviceToken.trim().isEmpty() ? STATUS_FOUND : STATUS_NOT_FOUND);
                
                if ((deviceToken == null || deviceToken.trim().isEmpty()) && userRepository != null) {
                    // Try to reload user to get device token
                    try {
                        log.info("Reloading user {} to fetch device token", user.getId());
                        User reloadedUser = userRepository.findById(user.getId()).orElse(null);
                        if (reloadedUser != null) {
                            deviceToken = reloadedUser.getDeviceToken();
                            log.info(LOG_RELOADED_USER_DEVICE_TOKEN, user.getId(), 
                                    deviceToken != null && !deviceToken.trim().isEmpty() ? STATUS_FOUND : STATUS_NOT_FOUND);
                        } else {
                            log.warn("User {} not found when reloading", user.getId());
                        }
                    } catch (Exception e) {
                        log.warn(LOG_FAILED_TO_RELOAD_USER, user.getId(), e.getMessage(), e);
                    }
                } else if (userRepository == null) {
                    log.warn("UserRepository is null, cannot reload user {} to get device token", user.getId());
                }
                
                // Normalize device token (handle empty strings as null)
                if (deviceToken != null && deviceToken.trim().isEmpty()) {
                    deviceToken = null;
                }
                
                sendWebSocketNotification(user.getId(), webSocketTopic, message, deviceToken);
            } catch (Exception e) {
                log.error("Failed to send WebSocket notification to user {}: {}", 
                        user.getId(), e.getMessage(), e);
            }
        }
    }
    
    /**
     * Send WebSocket notification and publish to RabbitMQ for integration service
     */
    private void sendWebSocketNotification(java.util.UUID userId, String topic, NotificationMessage message, String deviceToken) {
        sendWebSocketNotification(userId, topic, message, deviceToken, null);
    }
    
    /**
     * Send WebSocket notification with enhanced data for cashiers
     * @param notificationType Specific notification type (e.g., DISCOUNT_REQUEST_APPROVED, CANCELLATION_APPROVED, etc.)
     */
    private void sendWebSocketNotification(java.util.UUID userId, String topic, NotificationMessage message, String deviceToken, String notificationType) {
        sendWebSocketNotification(userId, topic, message, deviceToken, notificationType, false);
    }

    /**
     * Send notification: WebSocket and/or RabbitMQ (FCM). When fcmOnly is true, only publishes to RabbitMQ (no WebSocket) to avoid duplicate pop-ups.
     */
    private void sendWebSocketNotification(java.util.UUID userId, String topic, NotificationMessage message, String deviceToken, String notificationType, boolean fcmOnly) {
        String effectiveNotificationType = null;
        if (notificationType != null && !notificationType.trim().isEmpty()) {
            effectiveNotificationType = notificationType.trim();
        } else if (message.getData() != null && message.getData().containsKey(KEY_NOTIFICATION_TYPE)) {
            String fromData = message.getData().get(KEY_NOTIFICATION_TYPE);
            if (fromData != null && !fromData.trim().isEmpty()) {
                effectiveNotificationType = fromData.trim();
            }
        }
        String logNotificationType = effectiveNotificationType != null ? effectiveNotificationType : "generic";
        boolean hasDeviceToken = deviceToken != null && !deviceToken.trim().isEmpty();

        log.info("[Notification] trigger userId={} wsTopic={} stompDestination={} notificationType={} fcmOnly={} deviceToken={}",
                userId,
                topic,
                fcmOnly ? "(websocket skipped)" : "/user/" + userId + topic,
                logNotificationType,
                fcmOnly,
                hasDeviceToken ? LOG_DEVICE_TOKEN_PRESENT : LOG_DEVICE_TOKEN_ABSENT);

        Map<String, Object> wsMessage = new HashMap<>();
        wsMessage.put(KEY_TITLE, message.getTitle());
        wsMessage.put("body", message.getBody());
        wsMessage.put(KEY_DATA, message.getData());
        wsMessage.put(KEY_TIMESTAMP, OffsetDateTime.now(ZoneOffset.UTC).format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
        wsMessage.put(KEY_USER_ID, userId.toString());
        wsMessage.put(KEY_TOPIC, topic);
        wsMessage.put(KEY_TYPE, TYPE_WEBSOCKET_NOTIFICATION);
        
        if (effectiveNotificationType != null) {
            wsMessage.put(KEY_NOTIFICATION_TYPE, effectiveNotificationType);
        }
        
        // Add FCM-related fields for integration service
        if (message.getImageUrl() != null) {
            wsMessage.put("imageUrl", message.getImageUrl());
        }
        if (message.getClickAction() != null) {
            wsMessage.put("clickAction", message.getClickAction());
        }
        if (message.getSound() != null) {
            wsMessage.put("sound", message.getSound());
        }
        if (message.getPriority() != null) {
            wsMessage.put("priority", message.getPriority().toString());
        }
        if (message.getMessageType() != null) {
            wsMessage.put("messageType", message.getMessageType().toString());
        }
        if (hasDeviceToken) {
            wsMessage.put("deviceToken", deviceToken);
        } else if (fcmOnly) {
            log.warn("[Notification][FCM] fcmOnly send but no deviceToken userId={} wsTopic={} notificationType={}",
                    userId, topic, logNotificationType);
        } else {
            log.debug("[Notification][FCM] no deviceToken on payload userId={} wsTopic={} notificationType={} (WebSocket-only or token optional)",
                    userId, topic, logNotificationType);
        }

        // Critical: never perform WebSocket/Rabbit publish while a DB transaction is open.
        // If publishing blocks (broker/network), we'd hold a JDBC connection "idle in transaction" and exhaust Hikari.
        runAfterCommit("sendWebSocketNotification", () -> {
            if (!fcmOnly) {
                if (topic != null && topic.contains("cashier/notifications")) {
                    log.info("[CASHIER-NOTIFY] restaurant-management: convertAndSendToUser userId={} destination=/user/{}{} notificationType={}",
                            userId, userId, topic, logNotificationType);
                }
                messagingTemplate.convertAndSendToUser(userId.toString(), topic, wsMessage);
                log.info("[Notification][WebSocket] sent userScoped userId={} destination=/user/{}{} notificationType={}",
                        userId, userId, topic, logNotificationType);
            } else {
                log.info("[Notification][WebSocket] skipped userScoped (fcmOnly) userId={} wouldDestination=/user/{}{} notificationType={}",
                        userId, userId, topic, logNotificationType);
            }

            if (rabbitTemplate != null) {
                try {
                    rabbitTemplate.convertAndSend(WEBSOCKET_TOPIC_EXCHANGE, WEBSOCKET_ROUTING_KEY, wsMessage);
                    log.info("[Notification][FCM] rabbitPublish exchange={} routingKey={} userId={} payloadWsTopic={} notificationType={} deviceToken={}",
                            WEBSOCKET_TOPIC_EXCHANGE, WEBSOCKET_ROUTING_KEY, userId, topic, logNotificationType, hasDeviceToken ? LOG_DEVICE_TOKEN_PRESENT : LOG_DEVICE_TOKEN_ABSENT);
                } catch (Exception e) {
                    log.warn("[Notification][FCM] rabbitPublish failed userId={} payloadWsTopic={} notificationType={}: {} — FCM will not be sent",
                            userId, topic, logNotificationType, e.getMessage());
                }
            } else {
                log.warn("[Notification][FCM] RabbitTemplate unavailable userId={} payloadWsTopic={} notificationType={} — skipping FCM pipeline",
                        userId, topic, logNotificationType);
            }
        });
    }

    /**
     * Send FCM only to multiple users (no WebSocket). Used for HQ threshold alerts to avoid duplicate pop-ups.
     */
    private void sendFcmOnlyToUsers(List<User> users, NotificationMessage message, String topic) {
        if (users == null || users.isEmpty()) {
            return;
        }
        for (User user : users) {
            if (user == null) {
                continue;
            }
            String deviceToken = user.getDeviceToken();
            if ((deviceToken == null || deviceToken.trim().isEmpty()) && userRepository != null) {
                try {
                    User reloadedUser = userRepository.findById(user.getId()).orElse(null);
                    if (reloadedUser != null) {
                        deviceToken = reloadedUser.getDeviceToken();
                    }
                } catch (Exception e) {
                    log.warn(LOG_FAILED_TO_RELOAD_USER, user.getId(), e.getMessage(), e);
                }
            }
            if (deviceToken != null && deviceToken.trim().isEmpty()) {
                deviceToken = null;
            }
            try {
                sendWebSocketNotification(user.getId(), topic, message, deviceToken, null, true);
            } catch (Exception e) {
                log.error("Failed to send FCM notification to user {}: {}", user.getId(), e.getMessage(), e);
            }
        }
    }

    /**
     * When push template build fails, build FCM payload from the same i18n keys as notification listing.
     */
    private NotificationMessage buildCancellationPercentageAlertFallbackFromMessageKeys(
            Locale loc,
            Restaurant restaurant,
            AlertConfigurationResolver.ResolvedAlertConfig resolvedConfig,
            BigDecimal thresholdPercentage,
            boolean orderBreached,
            boolean transactionBreached,
            BigDecimal orderCancelPct,
            BigDecimal transactionCancelPct) {
        BigDecimal effTh = thresholdPercentage != null ? thresholdPercentage
                : resolvedConfig.getCancellationAlertPercentage();
        java.util.Map<String, String> d = new java.util.HashMap<>();
        d.put(KEY_RESTAURANT_ID, restaurant.getId().toString());
        d.put(KEY_RESTAURANT_CODE, restaurant.getRestaurantCode() != null ? restaurant.getRestaurantCode() : "");
        if (effTh != null) {
            d.put("cancellationThreshold", effTh.toPlainString());
        }
        String rn = getRestaurantNameForLocale(restaurant, loc);
        String thresholdStrSave = thresholdPercentage != null ? thresholdPercentage.toPlainString() : "-";
        String orderStrSave = orderCancelPct != null ? orderCancelPct.toPlainString() : "0";
        String txnStrSave = transactionCancelPct != null ? transactionCancelPct.toPlainString() : "0";
        String title;
        String body;
        if (orderBreached && transactionBreached) {
            d.put("orderCancellationPercentage", orderStrSave);
            d.put("transactionCancellationPercentage", txnStrSave);
            title = messageUtil.getMessage("notification.alert.cancellation.percentage.combined.title", loc);
            body = messageUtil.getMessage("notification.alert.cancellation.percentage.combined.body", loc,
                    rn, orderStrSave, txnStrSave, thresholdStrSave);
        } else if (orderBreached) {
            d.put(KEY_CANCELLATION_PERCENTAGE, orderStrSave);
            title = messageUtil.getMessage("notification.alert.order.cancellation.percentage.title", loc);
            body = messageUtil.getMessage("notification.alert.order.cancellation.percentage.body", loc,
                    rn, orderStrSave, thresholdStrSave);
        } else {
            d.put(KEY_CANCELLATION_PERCENTAGE, txnStrSave);
            title = messageUtil.getMessage("notification.alert.transaction.cancellation.percentage.title", loc);
            body = messageUtil.getMessage("notification.alert.transaction.cancellation.percentage.body", loc,
                    rn, txnStrSave, thresholdStrSave);
        }
        return NotificationMessage.builder().title(title).body(body).data(d).build();
    }

    /**
     * FCM-only multi-user send with a message built per recipient locale (matches notification listing behavior).
     */
    private void sendFcmOnlyToUsersWithRecipientLocales(List<User> users, Locale requestLocale,
            java.util.function.Function<Locale, NotificationMessage> messageFactory, String topic) {
        if (users == null || users.isEmpty()) {
            return;
        }
        for (User user : users) {
            if (user == null) {
                continue;
            }
            Locale loc = localeForRecipient(user, requestLocale);
            NotificationMessage message;
            try {
                message = messageFactory.apply(loc);
            } catch (Exception e) {
                log.warn("Failed to build localized FCM message for user {}: {}", user.getId(), e.getMessage());
                continue;
            }
            if (message == null) {
                continue;
            }
            String deviceToken = user.getDeviceToken();
            if ((deviceToken == null || deviceToken.trim().isEmpty()) && userRepository != null) {
                try {
                    User reloadedUser = userRepository.findById(user.getId()).orElse(null);
                    if (reloadedUser != null) {
                        deviceToken = reloadedUser.getDeviceToken();
                    }
                } catch (Exception e) {
                    log.warn(LOG_FAILED_TO_RELOAD_USER, user.getId(), e.getMessage(), e);
                }
            }
            if (deviceToken != null && deviceToken.trim().isEmpty()) {
                deviceToken = null;
            }
            try {
                sendWebSocketNotification(user.getId(), topic, message, deviceToken, null, true);
            } catch (Exception e) {
                log.error("Failed to send FCM notification to user {}: {}", user.getId(), e.getMessage(), e);
            }
        }
    }
    
    /**
     * Send WebSocket list refresh event to notify clients that a list needs to be refreshed.
     * This is used for notification lists and request lists.
     * 
     * @param userId The user ID to send the refresh event to
     * @param listType The type of list that needs refreshing (LIST_NOTIFICATIONS or LIST_REQUESTS)
     */
    private void sendListRefreshEvent(java.util.UUID userId, String listType) {
        if (userId == null) {
            log.debug("Cannot send list refresh event: userId is null");
            return;
        }

        runAfterCommit("sendListRefreshEvent", () -> {
            try {
                Map<String, Object> refreshEvent = new HashMap<>();
                refreshEvent.put(KEY_TYPE, "LIST_REFRESH");
                refreshEvent.put("listType", listType != null ? listType : LIST_NOTIFICATIONS);
                refreshEvent.put(KEY_USER_ID, userId.toString());
                refreshEvent.put(KEY_TIMESTAMP, OffsetDateTime.now(ZoneOffset.UTC).format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));

                // Send to user-specific topic for list updates
                // Use convertAndSendToUser for user-specific routing (more secure)
                String topic = "/topic/list-update";
                messagingTemplate.convertAndSendToUser(userId.toString(), topic, refreshEvent);

                log.info("[Notification][WebSocket] sent userScoped userId={} destination=/user/{}{} notificationType=LIST_REFRESH listType={}",
                        userId, userId, topic, listType);
            } catch (Exception e) {
                log.warn("Failed to send list refresh event to user {}: {}", userId, e.getMessage());
            }
        });
    }

    /**
     * Run an action after the current Spring transaction commits.
     * If there's no active transaction, runs immediately.
     */
    private void runAfterCommit(String tag, Runnable action) {
        if (action == null) {
            return;
        }
        try {
            if (!TransactionSynchronizationManager.isActualTransactionActive()) {
                action.run();
                return;
            }
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    try {
                        action.run();
                    } catch (Exception e) {
                        log.warn("afterCommit action failed ({}): {}", tag, e.getMessage(), e);
                    }
                }
            });
        } catch (Exception e) {
            // Safety: never fail the business operation due to notification publishing scheduling.
            log.warn("Failed to register afterCommit action ({}): {}", tag, e.getMessage(), e);
            try {
                action.run();
            } catch (Exception ex) {
                log.warn("Fallback immediate action failed ({}): {}", tag, ex.getMessage(), ex);
            }
        }
    }
    
    /**
     * Send list refresh events to multiple users.
     * 
     * @param userIds List of user IDs to send refresh events to
     * @param listType The type of list that needs refreshing (LIST_NOTIFICATIONS or LIST_REQUESTS)
     */
    private void sendListRefreshEventToUsers(List<User> users, String listType) {
        if (users == null || users.isEmpty()) {
            return;
        }
        
        for (User user : users) {
            if (user != null && user.getId() != null) {
                sendListRefreshEvent(user.getId(), listType);
            }
        }
    }
    
    /**
     * Safely get table order number from an Order entity.
     * Handles lazy loading by falling back to repository query if needed.
     */
    private String getTableOrderFromOrder(com.gulfnet.shared_library.entity.Order order) {
        if (order == null) {
            return "";
        }

        // Try to get from already loaded entity
        try {
            if (order.getRestaurantTable() != null && order.getRestaurantTable().getTableOrder() != null) {
                return order.getRestaurantTable().getTableOrder().toString();
            }
        } catch (Exception e) {
            log.debug("Could not get table order from order entity: {}", e.getMessage());
        }

        // Fallback to querying the repository if the entity is not loaded
        if (orderRepository != null) {
            try {
                java.util.Optional<Integer> tableOrderOpt = orderRepository.findTableOrderByOrderId(order.getId());
                if (tableOrderOpt.isPresent()) {
                    return tableOrderOpt.get().toString();
                }
            } catch (Exception e) {
                log.warn("Failed to get table order for order {} from repository: {}", order.getId(), e.getMessage());
            }
        }

        log.warn("Failed to get table order for order: {}", order.getId());
        return "";
    }
    
    /**
     * Safely get table code from an Order entity, falling back to table order if table code is not available.
     * Handles lazy loading by falling back to repository query if needed.
     * This method is used for displaying table information in notifications (preferring table code over table order).
     */
    private String getTableCodeFromOrder(com.gulfnet.shared_library.entity.Order order) {
        if (order == null) {
            return "";
        }

        // Try to get table code from already loaded entity
        try {
            if (order.getRestaurantTable() != null) {
                String tableCode = order.getRestaurantTable().getTableCode();
                if (tableCode != null && !tableCode.trim().isEmpty()) {
                    return tableCode;
                }
                // Fallback to table order if table code is not available
                if (order.getRestaurantTable().getTableOrder() != null) {
                    return order.getRestaurantTable().getTableOrder().toString();
                }
            }
        } catch (Exception e) {
            log.debug("Could not get table code from order entity: {}", e.getMessage());
        }

        // Fallback to querying the repository if the entity is not loaded
        if (orderRepository != null) {
            try {
                // Try to get table code first
                java.util.Optional<String> tableCodeOpt = orderRepository.findTableCodeByOrderId(order.getId());
                if (tableCodeOpt.isPresent() && !tableCodeOpt.get().trim().isEmpty()) {
                    return tableCodeOpt.get();
                }
                // Fallback to table order if table code is not available
                java.util.Optional<Integer> tableOrderOpt = orderRepository.findTableOrderByOrderId(order.getId());
                if (tableOrderOpt.isPresent()) {
                    return tableOrderOpt.get().toString();
                }
            } catch (Exception e) {
                log.warn("Failed to get table code for order {} from repository: {}", order.getId(), e.getMessage());
            }
        }

        log.warn("Failed to get table code for order: {}", order.getId());
        return "";
    }
    
    /**
     * Build item-related notification data
     */
    private Map<String, String> buildItemData(OrderedItem orderedItem, String type, String additionalInfo, Locale userLocale) {
        String tableCode = getTableCodeFromOrder(orderedItem.getOrder());
        Map<String, String> data = notificationBuilder.buildItemData(
                orderedItem.getId().toString(),
                orderedItem.getItem().getId().toString(),
                getItemName(orderedItem.getItem(), userLocale),
                orderedItem.getOrder().getId().toString(),
                tableCode,
                orderedItem.getItemStatus().toString(),
                orderedItem.getQuantity().toString(),
                additionalInfo
        );
        data.put(KEY_TYPE, type);
        return data;
    }
    
    /**
     * Human-readable identifier for transaction in notifications (waiter/cashier).
     * Prefer transaction number, then order number; avoid showing raw UUID when a better label exists.
     */
    private String getTransactionIdentifierForNotification(com.gulfnet.shared_library.entity.Transaction transaction) {
        if (transaction.getTransactionNumber() != null && !transaction.getTransactionNumber().trim().isEmpty()) {
            return transaction.getTransactionNumber();
        }
        if (transaction.getOrder() != null && transaction.getOrder().getOrderNumber() != null && !transaction.getOrder().getOrderNumber().trim().isEmpty()) {
            return transaction.getOrder().getOrderNumber();
        }
        if (transaction.getId() != null) {
            return "Transaction " + transaction.getId().toString();
        }
        return "";
    }

    /**
     * Build transaction-related notification data
     */
    private Map<String, String> buildTransactionData(com.gulfnet.shared_library.entity.Transaction transaction, String type, String additionalInfo, Locale userLocale) {
        Map<String, String> data = new HashMap<>();
        if (transaction.getId() != null) {
            data.put(KEY_REQUEST_ID, transaction.getId().toString());
        }
        data.put("transactionId", transaction.getId() != null ? transaction.getId().toString() : "");
        data.put("transactionNumber", transaction.getTransactionNumber() != null ? transaction.getTransactionNumber() : "");
        data.put(KEY_ORDER_ID, transaction.getOrder() != null && transaction.getOrder().getId() != null ? transaction.getOrder().getId().toString() : "");
        data.put(KEY_ORDER_NUMBER, transaction.getOrder() != null && transaction.getOrder().getOrderNumber() != null ? transaction.getOrder().getOrderNumber() : "");
        data.put("transactionStatus", transaction.getTransactionStatus() != null ? transaction.getTransactionStatus().toString() : "");
        data.put("paymentMethod", transaction.getPaymentMethod() != null ? transaction.getPaymentMethod() : "");
        data.put("transactionAmount", transaction.getTransactionAmount() != null ? transaction.getTransactionAmount().toString() : "");
        if (transaction.getOrder() != null && transaction.getOrder().getRestaurantTable() != null) {
            data.put(KEY_TABLE_ID, transaction.getOrder().getRestaurantTable().getId() != null ? transaction.getOrder().getRestaurantTable().getId().toString() : "");
            data.put("tableOrder", transaction.getOrder().getRestaurantTable().getTableOrder() != null ? transaction.getOrder().getRestaurantTable().getTableOrder().toString() : "");
            data.put(KEY_TABLE_CODE, transaction.getOrder().getRestaurantTable().getTableCode() != null ? transaction.getOrder().getRestaurantTable().getTableCode() : "");
        }
        // Prefer transaction.restaurant: it is JOIN FETCHed by findByIdWithOrderAndTable. Order.restaurant is lazy;
        // after REQUIRES_NEW (e.g. notifyRefundRequestDecision from Rabbit) the entity is detached — touching
        // order.getRestaurant() alone causes LazyInitializationException and skips WebSocket + DB notification.
        if (transaction.getRestaurant() != null && transaction.getRestaurant().getId() != null) {
            data.put(KEY_RESTAURANT_ID, transaction.getRestaurant().getId().toString());
        } else if (transaction.getOrder() != null && transaction.getOrder().getRestaurant() != null
                && transaction.getOrder().getRestaurant().getId() != null) {
            data.put(KEY_RESTAURANT_ID, transaction.getOrder().getRestaurant().getId().toString());
        }
        if (additionalInfo != null) {
            data.put(KEY_ADDITIONAL_INFO, additionalInfo);
            data.put(KEY_COMMENTS, additionalInfo); // Also add as comments for consistency
        }
        data.put(KEY_TYPE, type);
        data.put(KEY_NOTIFICATION_TYPE, type); // Add notificationType for easier client-side filtering
        return data;
    }
    
    /**
     * Build order-related notification data
     */
    private Map<String, String> buildOrderData(com.gulfnet.shared_library.entity.Order order, String type, String additionalInfo, Locale userLocale) {
        Map<String, String> data = new HashMap<>();
        data.put(KEY_ORDER_ID, order.getId().toString());
        data.put(KEY_ORDER_NUMBER, order.getOrderNumber() != null ? order.getOrderNumber() : "");
        data.put(KEY_ORDER_STATUS, order.getOrderStatus() != null ? order.getOrderStatus().toString() : "");
        data.put(KEY_TOTAL_AMOUNT, order.getTotalAmount() != null ? order.getTotalAmount().toString() : "");
        data.put(KEY_SUB_TOTAL, order.getSubTotal() != null ? order.getSubTotal().toString() : "");
        
        // Safely get table code using helper method to avoid LazyInitializationException
        // Falls back to table order if table code is not available
        String tableCode = getTableCodeFromOrder(order);
        if (!tableCode.isEmpty()) {
            data.put(KEY_TABLE_CODE, tableCode);
        }
        
        // Try to get table ID using repository if available
        if (orderRepository != null) {
            try {
                java.util.Optional<java.util.UUID> tableIdOpt = orderRepository.findTableIdByOrderId(order.getId());
                if (tableIdOpt.isPresent()) {
                    data.put(KEY_TABLE_ID, tableIdOpt.get().toString());
                }
            } catch (Exception e) {
                log.debug("Could not get table ID for order {}: {}", order.getId(), e.getMessage());
            }
        }
        
        // Try to get restaurant ID safely
        try {
            java.util.UUID restaurantId = getRestaurantIdFromOrder(order);
            data.put(KEY_RESTAURANT_ID, restaurantId.toString());
        } catch (Exception e) {
            log.debug("Could not get restaurant ID for order {}: {}", order.getId(), e.getMessage());
        }
        
        // Try to get waiter information safely
        try {
            if (order.getWaiter() != null) {
                data.put(KEY_WAITER_ID, order.getWaiter().getId().toString());
                String waiterName = (order.getWaiter().getFirstName() != null ? order.getWaiter().getFirstName() : "") + 
                                   " " + (order.getWaiter().getLastName() != null ? order.getWaiter().getLastName() : "");
                data.put(KEY_WAITER_NAME, waiterName.trim());
            }
        } catch (Exception e) {
            log.debug("Could not get waiter information for order {}: {}", order.getId(), e.getMessage());
        }
        
        if (additionalInfo != null) {
            data.put(KEY_ADDITIONAL_INFO, additionalInfo);
            data.put(KEY_COMMENTS, additionalInfo); // Also add as comments for consistency
        }
        data.put(KEY_TYPE, type);
        data.put(KEY_NOTIFICATION_TYPE, type); // Add notificationType for easier client-side filtering
        return data;
    }
    
    /**
     * Build table-related notification data
     */
    private Map<String, String> buildTableData(RestaurantTable table, String type, Locale userLocale) {
        Map<String, String> data = notificationBuilder.buildTableData(
                table.getId().toString(),
                table.getTableOrder().toString(),
                table.getTableStatus().toString(),
                table.getRestaurantRow() != null && table.getRestaurantRow().getRestaurantSection() != null 
                        ? table.getRestaurantRow().getRestaurantSection().getId().toString() : null,
                getSectionName(table.getRestaurantRow().getRestaurantSection(), userLocale)
        );
        data.put(KEY_TYPE, type);
        return data;
    }
    
    /**
     * Build section-related notification data
     */
    private Map<String, String> buildSectionData(com.gulfnet.shared_library.entity.RestaurantSection section, String type, Locale userLocale) {
        Map<String, String> data = new HashMap<>();
        data.put("sectionId", section.getId().toString());
        data.put("sectionName", getSectionName(section, userLocale));
        data.put(KEY_TYPE, type);
        // RestaurantSection doesn't have direct restaurant relationship, it's through RestaurantRow
        return data;
    }
    
    /**
     * Build user-related notification data
     */
    private Map<String, String> buildUserData(User user, String type, String additionalInfo) {
        Map<String, String> data = notificationBuilder.buildUserData(
                user.getId().toString(),
                user.getFirstName() + " " + user.getLastName(),
                user.getRoleId() != null ? user.getRoleId().toString() : "USER",
                additionalInfo
        );
        data.put(KEY_TYPE, type);
        return data;
    }
    
    /**
     * Build payment-related notification data
     */
    private Map<String, String> buildPaymentData(com.gulfnet.shared_library.entity.Order order, 
                                               String paymentMethod, BigDecimal amountPaid, String additionalInfo) {
        Map<String, String> data = notificationBuilder.buildPaymentData(
                order.getId().toString(),
                order.getRestaurantTable().getTableOrder().toString(),
                paymentMethod,
                formatAmountForNotification(amountPaid),
                additionalInfo
        );
        data.put(KEY_TYPE, NOTIF_PAYMENT_COMPLETED);
        return data;
    }

    /**
     * Formats payment amounts based on configured chain currency.
     * Example: Yen -> no decimals, Dollar/Baht -> 2 decimals.
     */
    private String formatAmountForNotification(BigDecimal amount) {
        String currency = restaurantChainConfigProperties.getChain() != null
                ? restaurantChainConfigProperties.getChain().getCurrency()
                : null;
        return CurrencyFormatter.formatAmount(amount, currency).toPlainString();
    }
    
    /**
     * Build payment error notification data
     * @param notificationType The notification type (PAYMENT_FAILED, PAYMENT_EXPIRED, or PAYMENT_ERROR)
     */
    private Map<String, String> buildPaymentErrorData(com.gulfnet.shared_library.entity.Transaction transaction, 
                                                     String errorType, String errorMessage, Locale userLocale, String notificationType) {
        Map<String, String> data = new HashMap<>();
        data.put("transactionId", transaction.getId().toString());
        data.put("transactionNumber", transaction.getTransactionNumber() != null ? transaction.getTransactionNumber() : "");
        data.put(KEY_ORDER_ID, transaction.getOrder() != null ? transaction.getOrder().getId().toString() : "");
        data.put(KEY_ORDER_NUMBER, transaction.getOrder() != null ? transaction.getOrder().getOrderNumber() : "");
        data.put("errorType", errorType != null ? errorType : VALUE_UNKNOWN);
        data.put("errorMessage", errorMessage != null ? errorMessage : "");
        data.put("paymentMethod", transaction.getPaymentMethod() != null ? transaction.getPaymentMethod() : "");
        data.put("transactionAmount", transaction.getTransactionAmount() != null ? transaction.getTransactionAmount().toString() : "");
        if (transaction.getOrder() != null && transaction.getOrder().getRestaurantTable() != null) {
            data.put(KEY_TABLE_ID, transaction.getOrder().getRestaurantTable().getId().toString());
            data.put("tableOrder", transaction.getOrder().getRestaurantTable().getTableOrder().toString());
            data.put(KEY_TABLE_CODE, transaction.getOrder().getRestaurantTable().getTableCode() != null ? transaction.getOrder().getRestaurantTable().getTableCode() : "");
        }
        if (transaction.getOrder() != null && transaction.getOrder().getRestaurant() != null) {
            data.put(KEY_RESTAURANT_ID, transaction.getOrder().getRestaurant().getId().toString());
        }
        data.put(KEY_TYPE, notificationType);
        data.put(KEY_NOTIFICATION_TYPE, notificationType); // Add notificationType for easier client-side filtering
        return data;
    }
    
    /**
     * Build device/integration error notification data
     */
    private Map<String, String> buildDeviceErrorData(String deviceType, String errorMessage, String additionalInfo) {
        Map<String, String> data = new HashMap<>();
        data.put("deviceType", deviceType != null ? deviceType : VALUE_UNKNOWN);
        data.put("errorMessage", errorMessage != null ? errorMessage : "");
        if (additionalInfo != null) {
            data.put(KEY_ADDITIONAL_INFO, additionalInfo);
        }
        data.put(KEY_TYPE, NOTIF_DEVICE_INTEGRATION_ERROR);
        data.put(KEY_NOTIFICATION_TYPE, NOTIF_DEVICE_INTEGRATION_ERROR); // Add notificationType for easier client-side filtering
        return data;
    }
    
    private String resolveCashDrawerNameForNotification(com.gulfnet.shared_library.entity.CashDrawer drawer, Locale userLocale) {
        if (drawer == null) {
            return "";
        }
        List<CashDrawerTranslation> list =
                cashDrawerTranslationRepository.findAllByCashDrawer_IdOrderByLanguageCodeAsc(drawer.getId());
        return CashDrawerTranslationUtil.resolveName(list, userLocale != null ? userLocale : Locale.ENGLISH);
    }

    /**
     * Build cashier shift-related notification data
     */
    private Map<String, String> buildCashierShiftData(com.gulfnet.shared_library.entity.CashierShift cashierShift, String type, Locale userLocale) {
        Map<String, String> data = new HashMap<>();
        data.put(KEY_SHIFT_ID, cashierShift.getId().toString());
        data.put(KEY_STATUS, cashierShift.getStatus() != null ? cashierShift.getStatus().toString() : "");
        data.put("openingBalance", cashierShift.getOpeningBalance() != null ? cashierShift.getOpeningBalance().toString() : "0.00");
        data.put("closingBalance", cashierShift.getClosingBalance() != null ? cashierShift.getClosingBalance().toString() : "");
        data.put("expectedClosingBalance", cashierShift.getExpectedClosingBalance() != null ? cashierShift.getExpectedClosingBalance().toString() : "");
        data.put("discrepancyAmount", cashierShift.getDiscrepancyAmount() != null ? cashierShift.getDiscrepancyAmount().toString() : "0.00");
        if (cashierShift.getDiscrepancyReason() != null) {
            data.put("discrepancyReason", cashierShift.getDiscrepancyReason());
        }
        if (cashierShift.getCashDrawer() != null) {
            data.put("cashDrawerId", cashierShift.getCashDrawer().getId().toString());
            data.put("cashDrawerName", resolveCashDrawerNameForNotification(cashierShift.getCashDrawer(), userLocale));
        }
        if (cashierShift.getRestaurant() != null) {
            data.put(KEY_RESTAURANT_ID, cashierShift.getRestaurant().getId().toString());
        }
        if (cashierShift.getCashier() != null) {
            data.put(KEY_CASHIER_ID, cashierShift.getCashier().getId().toString());
            data.put(KEY_CASHIER_NAME, cashierShift.getCashier().getFirstName() + " " + cashierShift.getCashier().getLastName());
        }
        if (cashierShift.getStartedAt() != null) {
            data.put("startedAt", cashierShift.getStartedAt().toString());
        }
        if (cashierShift.getClosedAt() != null) {
            data.put("closedAt", cashierShift.getClosedAt().toString());
        }
        data.put(KEY_TYPE, type);
        data.put(KEY_NOTIFICATION_TYPE, type); // Add notificationType for easier client-side filtering
        return data;
    }
    
    /**
     * Build combo-related notification data
     */
    private Map<String, String> buildComboData(com.gulfnet.shared_library.entity.OrderedCombo orderedCombo, String type, String additionalInfo, Locale userLocale) {
        Map<String, String> data = notificationBuilder.buildItemData(
                orderedCombo.getId().toString(),
                orderedCombo.getCombo().getComboId().toString(),
                getComboName(orderedCombo.getCombo(), userLocale),
                orderedCombo.getOrder().getId().toString(),
                orderedCombo.getOrder().getRestaurantTable().getTableOrder().toString(),
                orderedCombo.getItemStatus().toString(),
                orderedCombo.getQuantity().toString(),
                additionalInfo
        );
        data.put(KEY_TYPE, type);
        data.put("isCombo", "true");
        return data;
    }
    
    /**
     * Get item name from translations
     * Uses repository to avoid LazyInitializationException when Hibernate session is closed
     */
    private String getItemName(com.gulfnet.shared_library.entity.Item item, Locale userLocale) {
        if (item == null || item.getId() == null) {
            return "Item";
        }
        
        // Use repository to fetch translations to avoid LazyInitializationException
        if (itemTranslationRepository != null) {
            try {
                List<com.gulfnet.shared_library.entity.ItemTranslation> translations = 
                        itemTranslationRepository.findAllByItemId(item.getId());
                
                if (translations != null && !translations.isEmpty()) {
                    // Try to find exact language match
                    java.util.Optional<com.gulfnet.shared_library.entity.ItemTranslation> exactMatch = 
                            translations.stream()
                                    .filter(t -> t.getLanguageCode() != null && 
                                            t.getLanguageCode().equals(userLocale.getLanguage()))
                                    .findFirst();
                    
                    if (exactMatch.isPresent() && exactMatch.get().getName() != null) {
                        return exactMatch.get().getName();
                    }
                    
                    // Fallback to first available translation
                    if (translations.get(0).getName() != null) {
                        return translations.get(0).getName();
                    }
                }
            } catch (Exception e) {
                log.warn("Failed to fetch item translations for item {}: {}", item.getId(), e.getMessage());
            }
        }
        
        // Fallback: try to access lazy-loaded translations if repository is not available
        try {
            if (item.getTranslations() != null && !item.getTranslations().isEmpty()) {
        return item.getTranslations().stream()
                        .filter(t -> t.getLanguageCode() != null && 
                                t.getLanguageCode().equals(userLocale.getLanguage()))
                .map(com.gulfnet.shared_library.entity.ItemTranslation::getName)
                .findFirst()
                        .orElse(item.getTranslations().get(0).getName());
            }
        } catch (Exception e) {
            log.debug("Could not access item translations directly for item {}: {}", item.getId(), e.getMessage());
        }
        
        return "Item";
    }
    
    /**
     * Localized combo name with the same fallback order as order/combo APIs (configured languages, e.g. en after ja).
     */
    private String getComboName(com.gulfnet.shared_library.entity.Combo combo, Locale userLocale) {
        return orderValidationService.getComboName(combo, userLocale);
    }
    
    /**
     * Get section name from translations
     */
    private String getSectionName(com.gulfnet.shared_library.entity.RestaurantSection section, Locale userLocale) {
        if (section == null) return "";
        
        return section.getTranslations().stream()
                .filter(t -> t.getLanguageCode().equals(userLocale.getLanguage()))
                .map(com.gulfnet.shared_library.entity.RestaurantSectionTranslation::getName)
                .findFirst()
                .orElse(section.getTranslations().isEmpty() ? "" : section.getTranslations().get(0).getName());
    }
    
    /**
     * Get notification template by type
     */
    private NotificationTemplate getTemplateByType(String notificationType) {
        return switch (notificationType.toUpperCase()) {
            case NOTIF_ORDER_PLACED -> NotificationTemplate.Templates.ORDER_PLACED;
            case NOTIF_ITEM_PUSHED -> NotificationTemplate.Templates.ITEM_PUSHED_TO_KITCHEN;
            case NOTIF_ORDER_UPDATED -> NotificationTemplate.Templates.ORDER_UPDATED;
            case NOTIF_ITEM_READY -> NotificationTemplate.Templates.ITEM_READY;
            case NOTIF_ITEM_SERVED -> NotificationTemplate.Templates.ITEM_SERVED;
            case NOTIF_ITEM_DELAYED -> NotificationTemplate.Templates.ITEM_DELAYED;
            case NOTIF_TABLE_ASSIGNED -> NotificationTemplate.Templates.TABLE_ASSIGNED;
            case NOTIF_TABLE_REMOVED -> NotificationTemplate.Templates.TABLE_REMOVED;
            case NOTIF_PAYMENT_COMPLETED -> NotificationTemplate.Templates.PAYMENT_COMPLETED;
            case NOTIF_PAYMENT_ERROR -> NotificationTemplate.Templates.PAYMENT_ERROR;
            case NOTIF_PAYMENT_FAILED -> NotificationTemplate.Templates.PAYMENT_FAILED;
            case NOTIF_PAYMENT_EXPIRED -> NotificationTemplate.Templates.PAYMENT_EXPIRED;
            case NOTIF_CANCELLATION_APPROVED -> NotificationTemplate.Templates.CANCELLATION_APPROVED;
            case NOTIF_CANCELLATION_REJECTED -> NotificationTemplate.Templates.CANCELLATION_REJECTED;
            case "MANAGER_CANCEL_REQUEST_NOTIFICATION" -> NotificationTemplate.Templates.MANAGER_CANCEL_REQUEST_NOTIFICATION;
            case "MANAGER_ITEM_CANCELED_NOTIFICATION" -> NotificationTemplate.Templates.MANAGER_ITEM_CANCELED_NOTIFICATION;
            case NOTIF_PASSWORD_UPDATED -> NotificationTemplate.Templates.PASSWORD_UPDATED;
            case NOTIF_TABLE_SECTION_REQUEST_OPENED -> NotificationTemplate.Templates.TABLE_SECTION_REQUEST_OPENED;
            case NOTIF_TABLE_SECTION_REQUEST_CREATED -> NotificationTemplate.Templates.TABLE_SECTION_REQUEST_CREATED;
            case NOTIF_PROFILE_UPDATE_REQUEST_OPENED -> NotificationTemplate.Templates.PROFILE_UPDATE_REQUEST_OPENED;
            case NOTIF_PROFILE_UPDATE_REQUEST_CREATED -> NotificationTemplate.Templates.PROFILE_UPDATE_REQUEST_CREATED;
            case NOTIF_PROFILE_UPDATED_DIRECTLY -> NotificationTemplate.Templates.PROFILE_UPDATED_DIRECTLY;
            case NOTIF_EMPLOYEE_ASSIGNED_TO_RESTAURANT -> NotificationTemplate.Templates.EMPLOYEE_ASSIGNED_TO_RESTAURANT;
            case "ADDITIONAL_DISCOUNT_REQUEST_OPENED" -> NotificationTemplate.Templates.ADDITIONAL_DISCOUNT_REQUEST_OPENED;
            case "DISCOUNT_REQUEST_APPROVED" -> NotificationTemplate.Templates.DISCOUNT_REQUEST_APPROVED;
            case "DISCOUNT_REQUEST_DECLINED" -> NotificationTemplate.Templates.DISCOUNT_REQUEST_DECLINED;
            case NOTIF_REFUND_REQUEST_APPROVED -> NotificationTemplate.Templates.REFUND_REQUEST_APPROVED;
            case NOTIF_REFUND_REQUEST_DECLINED -> NotificationTemplate.Templates.REFUND_REQUEST_DECLINED;
            case NOTIF_DEVICE_INTEGRATION_ERROR -> NotificationTemplate.Templates.DEVICE_INTEGRATION_ERROR;
            case NOTIF_CASH_DRAWER_SHIFT_DISCREPANCY_REQUEST -> NotificationTemplate.Templates.CASH_DRAWER_SHIFT_DISCREPANCY_REQUEST;
            case NOTIF_CASH_DRAWER_SHIFT_DISCREPANCY_APPROVED -> NotificationTemplate.Templates.CASH_DRAWER_SHIFT_DISCREPANCY_APPROVED;
            case NOTIF_CASH_DRAWER_SHIFT_DISCREPANCY_DECLINED -> NotificationTemplate.Templates.CASH_DRAWER_SHIFT_DISCREPANCY_DECLINED;
            case NOTIF_CASH_DRAWER_SHIFT_STARTED -> NotificationTemplate.Templates.CASH_DRAWER_SHIFT_STARTED;
            case NOTIF_CASH_DRAWER_SHIFT_CLOSED -> NotificationTemplate.Templates.CASH_DRAWER_SHIFT_CLOSED;
            case NOTIF_MENU_ASSIGNED_TO_RESTAURANT -> NotificationTemplate.Templates.MENU_ASSIGNED_TO_RESTAURANT;
            case NOTIF_MENU_LIVE_AT_RESTAURANT -> NotificationTemplate.Templates.MENU_LIVE_AT_RESTAURANT;
            case NOTIF_KDS_COOKING -> NotificationTemplate.Templates.KDS_ITEM_COOKING;
            case NOTIF_KDS_READY -> NotificationTemplate.Templates.KDS_ITEM_READY;
            case NOTIF_KDS_DELAYED -> NotificationTemplate.Templates.KDS_ITEM_DELAYED;
            default -> null;
        };
    }

    private NotificationTemplate templateForKdsLineWorkflowStatus(com.gulfnet.shared_library.enums.ItemStatus status) {
        if (status == null) {
            return null;
        }
        return switch (status) {
            case COOKING -> NotificationTemplate.Templates.KDS_ITEM_COOKING;
            case READY -> NotificationTemplate.Templates.KDS_ITEM_READY;
            case DELAYED -> NotificationTemplate.Templates.KDS_ITEM_DELAYED;
            default -> null;
        };
    }

    private String kdsLineWorkflowSaveType(com.gulfnet.shared_library.enums.ItemStatus status) {
        if (status == null) {
            return null;
        }
        return switch (status) {
            case COOKING -> NOTIF_KDS_COOKING;
            case READY -> NOTIF_KDS_READY;
            case DELAYED -> NOTIF_KDS_DELAYED;
            default -> null;
        };
    }
    
    /**
     * Get appropriate WebSocket topic based on user role
     */
    private String getUserTopic(User user) {
        if (user.getRoleId() == null) return USER_UPDATE_TOPIC;
        
        // Since we only have roleId, we'll need to determine role type differently
        // For now, default to USER_UPDATE_TOPIC
        return USER_UPDATE_TOPIC;
    }
    
    /**
     * Save notification to database with i18n body key and args for locale-aware resolution.
     * Persists additional data (e.g. requestId) for navigation from notification list to request details.
     */
    private void saveNotificationToDatabase(User user, NotificationMessage msg, String type, User createdBy) {
        String additionalDataJson = null;
        if (msg.getData() != null && !msg.getData().isEmpty()) {
            try {
                additionalDataJson = OBJECT_MAPPER.writeValueAsString(msg.getData());
            } catch (JsonProcessingException e) {
                log.warn("Failed to serialize notification data for persistence: {}", e.getMessage());
            }
        }
        saveNotificationToDatabase(user, msg.getTitle(), msg.getBody(), type, createdBy,
                msg.getBodyKey(), msg.getBodyArgs(), additionalDataJson, false);
    }

    /**
     * Save notification to database (legacy — no i18n body key stored).
     */
    private void saveNotificationToDatabase(User user, String title, String message, String type, User createdBy) {
        saveNotificationToDatabase(user, title, message, type, createdBy, null, null, null, false);
    }

    /**
     * Save notification to database, optionally suppressing the WebSocket list-refresh event.
     * Used by HQ threshold alerts where FCM push is the sole pop-up channel; the list-refresh
     * would otherwise cause a duplicate pop-up on the frontend.
     */
    private void saveNotificationToDatabase(User user, String title, String message, String type,
                                            User createdBy, boolean suppressListRefresh) {
        saveNotificationToDatabase(user, title, message, type, createdBy, null, null, null, suppressListRefresh);
    }

    private void saveNotificationToDatabase(User user, String title, String message, String type,
                                            User createdBy, String bodyKey, String bodyArgs) {
        saveNotificationToDatabase(user, title, message, type, createdBy, bodyKey, bodyArgs, null, false);
    }

    /**
     * Persists a notification row and optionally triggers a WebSocket list-refresh for the recipient.
     * <p>
     * This is the central persistence method used by the various overloads:
     * - Reloads {@code user} (and {@code createdBy} when present) from {@code userRepository} so the entities are managed
     *   in the current persistence context (important when users originate from another service/module).
     * - Stores both resolved title/body text and optional i18n fields ({@code bodyKey}/{@code bodyArgs}) so the UI can
     *   later re-resolve the body per viewer locale if needed.
     * - Stores optional {@code additionalDataJson} for navigation/metadata (e.g., request ids).
     * <p>
     * If {@code suppressListRefresh} is {@code true}, the list-refresh event is not emitted. This is used for HQ threshold
     * alerts where push notification is the sole pop-up channel; emitting a list refresh would cause a duplicate pop-up.
     *
     * @param user recipient user (required)
     * @param title notification title to persist (nullable; persisted as empty string when {@code null})
     * @param message notification body to persist (nullable; persisted as empty string when {@code null})
     * @param type notification type identifier (nullable; persisted as {@code UNKNOWN} when {@code null})
     * @param createdBy actor user, if any (may be {@code null})
     * @param bodyKey optional message key for locale-aware resolution (may be {@code null})
     * @param bodyArgs optional serialized args for {@code bodyKey} (may be {@code null})
     * @param additionalDataJson optional JSON string for extra metadata (may be {@code null})
     * @param suppressListRefresh whether to skip emitting the WebSocket notification list refresh event
     */
    private void saveNotificationToDatabase(User user, String title, String message, String type,
                                            User createdBy, String bodyKey, String bodyArgs,
                                            String additionalDataJson, boolean suppressListRefresh) {
        try {
            if (user == null) {
                log.warn("Cannot save notification: user is null");
                return;
            }
            
            if (user.getId() == null) {
                log.error("Cannot save notification: user ID is null");
                return;
            }
            
            // Reload user from repository to ensure it's managed in this persistence context
            // This is important when User entity is passed from user-management service
            User managedUser = user;
            if (userRepository != null) {
                try {
                    managedUser = userRepository.findById(user.getId()).orElse(null);
                    if (managedUser == null) {
                        log.error("Cannot save notification: user {} not found in repository", user.getId());
                        return;
                    }
                    log.debug("Reloaded user {} from repository for notification", user.getId());
                } catch (Exception e) {
                    log.error("Failed to reload user {} from repository: {}", user.getId(), e.getMessage(), e);
                    return;
                }
            } else {
                log.warn("UserRepository is null, using provided user entity. Ensure user {} is managed in current persistence context.", user.getId());
            }
            
            // Reload createdBy user if provided to ensure it's managed in this persistence context
            User managedCreatedBy = createdBy;
            if (createdBy != null && userRepository != null) {
                try {
                    if (createdBy.getId() != null) {
                        managedCreatedBy = userRepository.findById(createdBy.getId()).orElse(null);
                        if (managedCreatedBy == null) {
                            log.warn("CreatedBy user {} not found in repository, saving with null", createdBy.getId());
                            managedCreatedBy = null;
                        }
                    } else {
                        log.warn("CreatedBy user ID is null, saving with null");
                        managedCreatedBy = null;
                    }
                } catch (Exception e) {
                    log.warn("Failed to reload createdBy user {} from repository: {}", 
                            createdBy.getId(), e.getMessage(), e);
                    managedCreatedBy = null;
                }
            }
            
            Notification notification = Notification.builder()
                    .user(managedUser)
                    .title(title != null ? title : "")
                    .message(message != null ? message : "")
                    .type(type != null ? type : VALUE_UNKNOWN)
                    .read(false)
                    .createdAt(OffsetDateTime.now(ZoneOffset.UTC))
                    .createdBy(managedCreatedBy)
                    .bodyKey(bodyKey)
                    .bodyArgs(bodyArgs)
                    .additionalData(additionalDataJson)
                    .build();
            
            Notification savedNotification = notificationRepository.save(notification);
            
            // Flush to ensure notification is immediately available for queries
            notificationRepository.flush();
            
            // Verify the saved notification has the correct user relationship
            UUID savedUserId = savedNotification.getUser() != null ? savedNotification.getUser().getId() : null;
            // Send list refresh event to notify frontend that notification list needs updating.
            // Suppressed for HQ threshold alerts where FCM push is the sole pop-up channel;
            // sending a list-refresh here would cause the frontend to show a second pop-up.
            if (!suppressListRefresh && savedUserId != null && savedUserId.equals(managedUser.getId())) {
                try {
                    sendListRefreshEvent(managedUser.getId(), LIST_NOTIFICATIONS);
                } catch (Exception e) {
                    log.warn("Failed to send notification list refresh event: {}", e.getMessage());
                }
            }
        } catch (Exception e) {
            log.error("Failed to save notification to database for user {}: {}", 
                    user != null ? user.getId() : "null", e.getMessage(), e);
            // Re-throw to allow caller to handle if needed, but log first
        }
    }
    
    /**
     * Helper method to safely get restaurant ID from Order.
     * Handles lazy loading by querying the database if the entity is not loaded.
     */
    private java.util.UUID getRestaurantIdFromOrder(com.gulfnet.shared_library.entity.Order order) {
        if (order == null) {
            log.error("Order is null when trying to get restaurant ID");
            throw new IllegalStateException("Order cannot be null");
        }

        // Try to get from already loaded entity
        try {
            if (order.getRestaurant() != null) {
                return order.getRestaurant().getId();
            }
        } catch (org.hibernate.LazyInitializationException e) {
            log.debug("Could not get restaurant ID from order entity (lazy loading): {}", e.getMessage());
        } catch (Exception e) {
            log.debug("Could not get restaurant ID from order entity: {}", e.getMessage());
        }

        // Fallback to querying the repository if the entity is not loaded
        if (orderRepository != null) {
            try {
                java.util.Optional<java.util.UUID> restaurantIdOpt = orderRepository.findRestaurantIdByOrderId(order.getId());
                if (restaurantIdOpt.isPresent()) {
                    return restaurantIdOpt.get();
                }
            } catch (Exception e) {
                log.warn("Failed to get restaurant ID for order {} from repository: {}", order.getId(), e.getMessage());
            }
        }

        log.error("Failed to get restaurant ID for order: {}", order.getId());
        throw new IllegalStateException("Failed to get restaurant ID for order: " + order.getId());
    }
    
    /**
     * Helper method to convert various types to UUID safely
     * Handles UUID, String, and other types that can be converted to UUID
     * @param obj The object to convert
     * @param fieldName The field name for logging purposes
     * @return UUID if conversion successful, null otherwise
     */
    private java.util.UUID convertToUUID(Object obj, String fieldName) {
        if (obj == null) {
            return null;
        }
        
        try {
            if (obj instanceof java.util.UUID) {
                return (java.util.UUID) obj;
            } else if (obj instanceof String) {
                return java.util.UUID.fromString((String) obj);
            } else if (obj.getClass().isArray()) {
                // Handle PostgreSQL array types (UUID[]) - extract first element
                Object[] array = (Object[]) obj;
                if (array.length > 0 && array[0] != null) {
                    // Recursively convert the first element
                    return convertToUUID(array[0], fieldName + "[0]");
                } else {
                    log.warn("Empty array returned for {}: {}", fieldName, obj.getClass().getName());
                    return null;
                }
            } else {
                // Try to convert via string representation
                String uuidStr = obj.toString();
                return java.util.UUID.fromString(uuidStr);
            }
        } catch (IllegalArgumentException e) {
            log.warn("Invalid UUID format for {}: {}", fieldName, obj);
            return null;
        } catch (Exception e) {
            log.warn("Failed to convert {} to UUID: {}", fieldName, e.getMessage());
            return null;
        }
    }
    
    /**
     * Adds every ACTIVE user assigned to default KDS device(s) for the restaurant (de-duplicated by user id).
     * Non-default stations still receive category-targeted notifications; default KDS always receives order events.
     */
    private void mergeUsersAssignedToDefaultKds(java.util.UUID restaurantId,
            List<com.gulfnet.shared_library.entity.User> kdsUsers) {
        if (restaurantId == null || kdsRepository == null || kdsConfigurationRepository == null || userRepository == null) {
            return;
        }
        try {
            List<com.gulfnet.shared_library.entity.Kds> defaultStations = kdsRepository.findAll().stream()
                    .filter(kds -> restaurantId.equals(kds.getRestaurantId())
                            && Boolean.FALSE.equals(kds.getIsDeleted())
                            && Boolean.TRUE.equals(kds.getIsDefault()))
                    .collect(java.util.stream.Collectors.toList());
            if (defaultStations.isEmpty()) {
                return;
            }
            java.util.List<java.util.UUID> defaultKdsIds = defaultStations.stream()
                    .map(com.gulfnet.shared_library.entity.Kds::getId)
                    .collect(java.util.stream.Collectors.toList());
            java.util.List<java.util.UUID> extraUserIds = kdsConfigurationRepository.findUserIdsByKdsIdIn(defaultKdsIds);
            if (extraUserIds == null || extraUserIds.isEmpty()) {
                return;
            }
            java.util.Set<java.util.UUID> existingIds = kdsUsers.stream()
                    .filter(u -> u != null && u.getId() != null)
                    .map(com.gulfnet.shared_library.entity.User::getId)
                    .collect(java.util.stream.Collectors.toSet());
            java.util.List<java.util.UUID> toLoad = extraUserIds.stream()
                    .filter(java.util.Objects::nonNull)
                    .filter(id -> !existingIds.contains(id))
                    .distinct()
                    .collect(java.util.stream.Collectors.toList());
            if (toLoad.isEmpty()) {
                return;
            }
            for (com.gulfnet.shared_library.entity.User u : userRepository.findAllById(toLoad)) {
                if (u != null && u.getStatus() == com.gulfnet.shared_library.enums.EntityStatus.ACTIVE) {
                    kdsUsers.add(u);
                }
            }
        } catch (Exception e) {
            log.warn("Failed to merge default KDS users for restaurant {}: {}", restaurantId, e.getMessage());
        }
    }

    /**
     * De-duplicates KDS users in-place by {@link com.gulfnet.shared_library.entity.User#getId()} while preserving order.
     * <p>
     * This prevents duplicate notifications when a user is returned from multiple KDS routing sources (e.g. multiple station
     * mappings or default-station merges).
     *
     * @param kdsUsers mutable list of KDS users to de-dupe (may be {@code null})
     */
    private void dedupeKdsUsersByUserId(List<com.gulfnet.shared_library.entity.User> kdsUsers) {
        if (kdsUsers == null || kdsUsers.isEmpty()) {
            return;
        }
        java.util.Map<java.util.UUID, com.gulfnet.shared_library.entity.User> byId = new java.util.LinkedHashMap<>();
        for (com.gulfnet.shared_library.entity.User u : kdsUsers) {
            if (u != null && u.getId() != null) {
                byId.putIfAbsent(u.getId(), u);
            }
        }
        kdsUsers.clear();
        kdsUsers.addAll(byId.values());
    }

    /**
     * KDS routing: recipient plus station id(s) this event applies to (client shows only if current station matches).
     */
    private record KdsItemRecipient(com.gulfnet.shared_library.entity.User user,
                                    java.util.Set<java.util.UUID> targetKdsStationIds) {
    }

    private java.util.Set<java.util.UUID> loadUserAssignedKdsIds(java.util.UUID userId) {
        java.util.Set<java.util.UUID> out = new java.util.HashSet<>();
        if (userId == null || kdsConfigurationRepository == null) {
            return out;
        }
        try {
            java.util.List<com.gulfnet.shared_library.entity.KdsConfiguration> configs =
                    kdsConfigurationRepository.findByUserId(userId);
            if (configs != null) {
                for (com.gulfnet.shared_library.entity.KdsConfiguration c : configs) {
                    try {
                        if (c.getKds() != null && c.getKds().getId() != null) {
                            out.add(c.getKds().getId());
                        }
                    } catch (Exception e) {
                        // Raised from debug to warn so a lazy-init failure cannot silently shrink a user's
                        // station set and accidentally widen routing via the "targets empty -> all matched"
                        // fallback in findKdsRecipientsForItem.
                        log.warn("Could not read KDS id from KdsConfiguration id={} (userId={}): {}",
                                c != null ? c.getId() : null, userId, e.getMessage());
                    }
                }
            }
        } catch (Exception e) {
            log.warn("loadUserAssignedKdsIds failed for userId={}: {}", userId, e.getMessage());
        }
        return out;
    }

    private java.util.List<KdsItemRecipient> mergeKdsRecipientsByUser(java.util.List<KdsItemRecipient> recipients) {
        if (recipients == null || recipients.isEmpty()) {
            return java.util.List.of();
        }
        java.util.Map<java.util.UUID, KdsItemRecipient> byUser = new java.util.LinkedHashMap<>();
        for (KdsItemRecipient r : recipients) {
            if (r == null || r.user() == null || r.user().getId() == null) {
                continue;
            }
            byUser.merge(r.user().getId(), r, (a, b) -> {
                java.util.Set<java.util.UUID> u = new java.util.HashSet<>(a.targetKdsStationIds());
                u.addAll(b.targetKdsStationIds());
                return new KdsItemRecipient(a.user(), u);
            });
        }
        return new java.util.ArrayList<>(byUser.values());
    }

    private void putTargetKdsIdsOnKdsDataMap(java.util.Map<String, Object> kdsData,
                                             java.util.Set<java.util.UUID> targetKdsStationIds) {
        if (kdsData == null || targetKdsStationIds == null || targetKdsStationIds.isEmpty()) {
            return;
        }
        kdsData.put(KEY_TARGET_KDS_IDS, targetKdsStationIds.stream()
                .map(java.util.UUID::toString)
                .sorted()
                .collect(java.util.stream.Collectors.joining(",")));
    }

    private void attachTargetKdsIdsToNotificationMessageData(NotificationMessage message,
                                                             java.util.Set<java.util.UUID> targetKdsStationIds) {
        if (message == null || message.getData() == null || targetKdsStationIds == null || targetKdsStationIds.isEmpty()) {
            return;
        }
        message.getData().put(KEY_TARGET_KDS_IDS, targetKdsStationIds.stream()
                .map(java.util.UUID::toString)
                .sorted()
                .collect(java.util.stream.Collectors.joining(",")));
    }

    private String serializeTargetKdsIdsForAdditionalData(java.util.Set<java.util.UUID> targetKdsStationIds) {
        if (targetKdsStationIds == null || targetKdsStationIds.isEmpty()) {
            return null;
        }
        try {
            java.util.Map<String, Object> meta = new java.util.HashMap<>();
            meta.put(KEY_TARGET_KDS_IDS, targetKdsStationIds.stream()
                    .map(java.util.UUID::toString)
                    .sorted()
                    .collect(java.util.stream.Collectors.joining(",")));
            return OBJECT_MAPPER.writeValueAsString(meta);
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            log.warn("Failed to serialize target KDS ids: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Resolves KDS recipients for an order by unioning per-menu-item routing (each line may map to different stations).
     */
    private java.util.List<KdsItemRecipient> findKdsRecipientsForOrder(com.gulfnet.shared_library.entity.Order order) {
        java.util.List<KdsItemRecipient> acc = new java.util.ArrayList<>();
        if (order == null || order.getId() == null) {
            return acc;
        }
        java.util.UUID restaurantId;
        try {
            restaurantId = getRestaurantIdFromOrder(order);
        } catch (Exception e) {
            log.debug("findKdsRecipientsForOrder: no restaurantId for order {}: {}", order.getId(), e.getMessage());
            return acc;
        }
        if (restaurantId == null) {
            return acc;
        }
        java.util.Set<java.util.UUID> seenMenuItemIds = new java.util.HashSet<>();
        try {
            if (orderedItemRepository != null) {
                java.util.List<OrderedItem> orderedItems = orderedItemRepository.findByOrderId(order.getId());
                for (OrderedItem oi : orderedItems) {
                    if (oi == null || oi.getOrderedCombo() != null || oi.getItem() == null || oi.getItem().getId() == null) {
                        continue;
                    }
                    if (!seenMenuItemIds.add(oi.getItem().getId())) {
                        continue;
                    }
                    acc.addAll(findKdsRecipientsForItem(oi.getItem(), restaurantId));
                }
                if (orderedComboRepository != null) {
                    java.util.List<com.gulfnet.shared_library.entity.OrderedCombo> combos =
                            orderedComboRepository.findByOrderId(order.getId());
                    if (combos != null) {
                        for (com.gulfnet.shared_library.entity.OrderedCombo combo : combos) {
                            if (combo == null) {
                                continue;
                            }
                            java.util.List<OrderedItem> comboLines =
                                    orderedItemRepository.findByOrderedComboId(combo.getId());
                            for (OrderedItem comboItem : comboLines) {
                                if (comboItem == null || comboItem.getItem() == null || comboItem.getItem().getId() == null) {
                                    continue;
                                }
                                if (!seenMenuItemIds.add(comboItem.getItem().getId())) {
                                    continue;
                                }
                                acc.addAll(findKdsRecipientsForItem(comboItem.getItem(), restaurantId));
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.warn("findKdsRecipientsForOrder failed for order {}: {}", order.getId(), e.getMessage());
        }
        return mergeKdsRecipientsByUser(acc);
    }

    /**
     * Resolves KDS recipients for item-level events with per-recipient target station ids.
     * Users on a non-default station only get that station in {@code targetKdsStationIds} for category-matched lines.
     * Users included only via default KDS merge get default station id(s) only, so other stations' apps can ignore the pop-up.
     */
    private java.util.List<KdsItemRecipient> findKdsRecipientsForItem(com.gulfnet.shared_library.entity.Item item,
                                                                      java.util.UUID restaurantId) {
        java.util.List<KdsItemRecipient> out = new java.util.ArrayList<>();
        if (item == null || item.getId() == null || restaurantId == null
                || categoryKdsRepository == null || categoryItemMappingRepository == null
                || kdsRepository == null || kdsConfigurationRepository == null
                || menuCategoryMappingRepository == null || categoryRepository == null) {
            return out;
        }

        try {
            java.util.List<com.gulfnet.shared_library.entity.Kds> allForRestaurant = kdsRepository.findAll().stream()
                    .filter(kds -> restaurantId.equals(kds.getRestaurantId())
                            && Boolean.FALSE.equals(kds.getIsDeleted()))
                    .collect(java.util.stream.Collectors.toList());

            java.util.List<com.gulfnet.shared_library.entity.Kds> nonDefaultKdss = allForRestaurant.stream()
                    .filter(kds -> Boolean.FALSE.equals(kds.getIsDefault()))
                    .collect(java.util.stream.Collectors.toList());

            java.util.List<com.gulfnet.shared_library.entity.Kds> defaultKdss = allForRestaurant.stream()
                    .filter(kds -> Boolean.TRUE.equals(kds.getIsDefault()))
                    .collect(java.util.stream.Collectors.toList());

            java.util.List<com.gulfnet.shared_library.entity.Kds> restaurantKdss;
            if (nonDefaultKdss.isEmpty()) {
                restaurantKdss = defaultKdss;
                if (!restaurantKdss.isEmpty()) {
                    log.debug("No non-default KDS for restaurant {}; using default KDS station(s) for item notification routing",
                            restaurantId);
                }
            } else {
                restaurantKdss = nonDefaultKdss;
            }

            if (restaurantKdss.isEmpty()) {
                log.debug("No KDS stations found for restaurant {}", restaurantId);
                return out;
            }

            java.util.List<java.util.UUID> itemMenuCategoryMappingIds = null;
            try {
                itemMenuCategoryMappingIds = categoryItemMappingRepository.findMenuCategoryMappingIdsByItemId(item.getId());
            } catch (Exception e) {
                log.warn("Could not get menu category mapping IDs for item {}: {}", item.getId(), e.getMessage());
                return out;
            }

            if (itemMenuCategoryMappingIds == null || itemMenuCategoryMappingIds.isEmpty()) {
                log.debug("No menu category mapping IDs found for item {}", item.getId());
                return out;
            }

            java.util.Set<java.util.UUID> assignedKdsIds = new java.util.HashSet<>();

            java.util.List<java.util.UUID> kdsIds = restaurantKdss.stream()
                    .map(com.gulfnet.shared_library.entity.Kds::getId)
                    .collect(java.util.stream.Collectors.toList());

            java.util.List<com.gulfnet.shared_library.entity.CategoryKds> allCategoryKdsMappings =
                    categoryKdsRepository.findAllByKdsIdIn(kdsIds);

            java.util.Map<java.util.UUID, java.util.Set<java.util.UUID>> kdsToMenuCategoryMappingMap =
                    new java.util.HashMap<>();

            for (com.gulfnet.shared_library.entity.CategoryKds categoryKds : allCategoryKdsMappings) {
                try {
                    java.util.UUID kdsId = categoryKds.getKds().getId();
                    if (categoryKds.getMenuCategoryMapping() != null
                            && categoryKds.getMenuCategoryMapping().getId() != null) {
                        kdsToMenuCategoryMappingMap
                                .computeIfAbsent(kdsId, k -> new java.util.HashSet<>())
                                .add(categoryKds.getMenuCategoryMapping().getId());
                    }
                } catch (Exception e) {
                    // Raised from debug to warn: silent loss here was causing non-default KDS stations to appear
                    // empty in the category map and be excluded from KDS routing.
                    log.warn("Could not access KDS or menu category mapping from CategoryKds id={}: {}",
                            categoryKds != null ? categoryKds.getId() : null, e.getMessage());
                }
            }

            // Iterate KDS-first and accept every station whose configured categories intersect with the item's
            // categories. The previous version iterated per item category and broke after the first matching KDS,
            // which silently dropped additional non-default stations that handled the same category.
            java.util.Set<java.util.UUID> itemMenuCategoryMappingIdSet = new java.util.HashSet<>(itemMenuCategoryMappingIds);
            itemMenuCategoryMappingIdSet.remove(null);

            for (com.gulfnet.shared_library.entity.Kds kds : restaurantKdss) {
                java.util.Set<java.util.UUID> kdsMenuCategoryMappingIdsSet =
                        kdsToMenuCategoryMappingMap.get(kds.getId());

                if (kdsMenuCategoryMappingIdsSet == null || kdsMenuCategoryMappingIdsSet.isEmpty()) {
                    continue;
                }
                if (!java.util.Collections.disjoint(kdsMenuCategoryMappingIdsSet, itemMenuCategoryMappingIdSet)) {
                    assignedKdsIds.add(kds.getId());
                }
            }

            java.util.Set<java.util.UUID> defaultKdsIdSet = defaultKdss.stream()
                    .map(com.gulfnet.shared_library.entity.Kds::getId)
                    .collect(java.util.stream.Collectors.toSet());

            java.util.List<com.gulfnet.shared_library.entity.User> categoryStationUsers = new java.util.ArrayList<>();
            if (!assignedKdsIds.isEmpty()) {
                java.util.List<java.util.UUID> userIds = new java.util.ArrayList<>();
                try {
                    userIds = kdsConfigurationRepository.findUserIdsByKdsIdIn(new java.util.ArrayList<>(assignedKdsIds));
                } catch (Exception e) {
                    log.error("Failed to get user IDs from kds_configuration: {}", e.getMessage(), e);
                }
                if (!userIds.isEmpty() && userRepository != null) {
                    for (com.gulfnet.shared_library.entity.User u : userRepository.findAllById(userIds)) {
                        if (u != null && u.getStatus() == com.gulfnet.shared_library.enums.EntityStatus.ACTIVE) {
                            categoryStationUsers.add(u);
                        }
                    }
                } else if (userIds.isEmpty()) {
                    log.warn("No user IDs found for KDS devices: {}", assignedKdsIds);
                }
            }

            java.util.Map<java.util.UUID, java.util.Set<java.util.UUID>> userIdToTargets = new java.util.LinkedHashMap<>();
            java.util.List<com.gulfnet.shared_library.entity.User> orderedUsers = new java.util.ArrayList<>();
            java.util.Set<java.util.UUID> categoryUserIds = new java.util.HashSet<>();

            for (com.gulfnet.shared_library.entity.User u : categoryStationUsers) {
                if (u == null || u.getId() == null) {
                    continue;
                }
                categoryUserIds.add(u.getId());
                java.util.Set<java.util.UUID> uKds = loadUserAssignedKdsIds(u.getId());
                java.util.Set<java.util.UUID> targets = new java.util.HashSet<>();
                for (java.util.UUID kid : assignedKdsIds) {
                    if (uKds.contains(kid)) {
                        targets.add(kid);
                    }
                }
                if (targets.isEmpty() && !assignedKdsIds.isEmpty()) {
                    targets.addAll(assignedKdsIds);
                }
                boolean userOnDefault = uKds.stream().anyMatch(defaultKdsIdSet::contains);
                if (userOnDefault && !defaultKdsIdSet.isEmpty()) {
                    targets.addAll(defaultKdsIdSet);
                }
                if (!targets.isEmpty()) {
                    userIdToTargets.put(u.getId(), targets);
                    orderedUsers.add(u);
                }
            }

            if (!defaultKdss.isEmpty() && !defaultKdsIdSet.isEmpty() && kdsConfigurationRepository != null
                    && userRepository != null) {
                java.util.List<java.util.UUID> extraUserIds =
                        kdsConfigurationRepository.findUserIdsByKdsIdIn(new java.util.ArrayList<>(defaultKdsIdSet));
                if (extraUserIds != null) {
                    java.util.Set<java.util.UUID> existing = new java.util.HashSet<>(categoryUserIds);
                    for (java.util.UUID uid : extraUserIds) {
                        if (uid == null || existing.contains(uid)) {
                            continue;
                        }
                        com.gulfnet.shared_library.entity.User u = userRepository.findById(uid).orElse(null);
                        if (u != null && u.getStatus() == com.gulfnet.shared_library.enums.EntityStatus.ACTIVE) {
                            userIdToTargets.put(u.getId(), new java.util.HashSet<>(defaultKdsIdSet));
                            orderedUsers.add(u);
                        }
                    }
                }
            }

            for (com.gulfnet.shared_library.entity.User u : orderedUsers) {
                java.util.Set<java.util.UUID> t = userIdToTargets.get(u.getId());
                if (t == null || t.isEmpty()) {
                    log.debug("Skipping KDS recipient {} with empty target station set for item {}", u.getId(), item.getId());
                    continue;
                }
                out.add(new KdsItemRecipient(u, java.util.Collections.unmodifiableSet(new java.util.HashSet<>(t))));
            }
        } catch (Exception e) {
            log.error("Failed to find KDS recipients for item {}: {}", item.getId(), e.getMessage(), e);
        }

        return out;
    }

    // ==================== KDS NOTIFICATIONS ====================
    
    /**
     * Sends notifications to KDS users when an order is cancelled.
     * Finds all KDS users assigned to items in the order and notifies them via restaurant-specific KDS topic.
     *
     * @param order The order that was cancelled
     * @param cancellationReason Reason for the cancellation
     * @param userLocale Locale for message localization
     */
    @Override
    public void notifyKdsOrderCanceled(com.gulfnet.shared_library.entity.Order order, String cancellationReason, Locale userLocale) {
        if (order == null) {
            log.warn("Cannot send KDS order cancellation notification: order is null");
            return;
        }
        
        try {
            // Get restaurant ID from order
            java.util.UUID restaurantId = getRestaurantIdFromOrder(order);
            
            if (restaurantId == null) {
                log.warn("Cannot send KDS order cancellation notification: restaurant ID not found for order {}", order.getId());
                return;
            }
            
            java.util.List<java.util.UUID> canceledItemIds = new java.util.ArrayList<>();
            if (orderedItemRepository != null) {
                try {
                    java.util.List<OrderedItem> orderedItems = orderedItemRepository.findByOrderId(order.getId());
                    for (OrderedItem orderedItem : orderedItems) {
                        if (orderedItem.getOrderedCombo() != null) {
                            continue;
                        }
                        if (orderedItem.getItem() != null && orderedItem.getItem().getId() != null) {
                            canceledItemIds.add(orderedItem.getId());
                        }
                    }
                } catch (Exception e) {
                    log.warn("Failed to fetch ordered items for order {}: {}", order.getId(), e.getMessage());
                }
            }

            java.util.List<KdsItemRecipient> orderCancelRecipients = findKdsRecipientsForOrder(order);
            
            // Build order cancellation data
            Map<String, Object> kdsData = new HashMap<>();
            kdsData.put(KEY_ORDER_ID, order.getId().toString());
            kdsData.put(KEY_ORDER_NUMBER, order.getOrderNumber() != null ? order.getOrderNumber() : "");
            kdsData.put(KEY_ORDER_STATUS, order.getOrderStatus() != null ? order.getOrderStatus().toString() : "");
            kdsData.put(KEY_TOTAL_AMOUNT, order.getTotalAmount() != null ? order.getTotalAmount().toString() : "");
            kdsData.put(KEY_SUB_TOTAL, order.getSubTotal() != null ? order.getSubTotal().toString() : "");
            kdsData.put(KEY_RESTAURANT_ID, restaurantId.toString());
            kdsData.put("canceledItemIds", canceledItemIds);
            kdsData.put("canceledItemCount", canceledItemIds.size());
            
            if (order.getRestaurantTable() != null) {
                kdsData.put(KEY_TABLE_ID, order.getRestaurantTable().getId().toString());
                // Use table code for tableNumber (falls back to table order if code not available)
                String tableCodeForDisplay = order.getRestaurantTable().getTableCode() != null && !order.getRestaurantTable().getTableCode().trim().isEmpty()
                        ? order.getRestaurantTable().getTableCode()
                        : (order.getRestaurantTable().getTableOrder() != null ? order.getRestaurantTable().getTableOrder().toString() : "");
                kdsData.put(KEY_TABLE_NUMBER, tableCodeForDisplay);
                kdsData.put(KEY_TABLE_CODE, order.getRestaurantTable().getTableCode() != null ? order.getRestaurantTable().getTableCode() : "");
            }
            
            if (order.getWaiter() != null) {
                kdsData.put(KEY_WAITER_ID, order.getWaiter().getId().toString());
                kdsData.put(KEY_WAITER_NAME, order.getWaiter().getFirstName() + " " + order.getWaiter().getLastName());
            }
            
            if (cancellationReason != null && !cancellationReason.trim().isEmpty()) {
                kdsData.put("cancellationReason", cancellationReason);
                kdsData.put("reason", cancellationReason);
            }
            
            kdsData.put(KEY_NOTIFICATION_TYPE, NOTIF_ORDER_CANCELED);
            kdsData.put(KEY_TIMESTAMP, OffsetDateTime.now(ZoneOffset.UTC).format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
            
            log.info("[KDS WebSocket] notifyKdsOrderCanceled orderId={}, restaurantId={}, kdsRecipientCount={}, canceledLineItemCount={}",
                    order.getId(), restaurantId, orderCancelRecipients.size(), canceledItemIds.size());
            
            if (!orderCancelRecipients.isEmpty()) {
                java.util.Map<String, Object> kdsDataBase = new java.util.HashMap<>(kdsData);
                for (KdsItemRecipient rec : orderCancelRecipients) {
                    com.gulfnet.shared_library.entity.User kdsUser = rec.user();
                    if (kdsUser == null || kdsUser.getId() == null) {
                        continue;
                    }
                    try {
                        Locale kdsLoc = localeForKdsRecipient(kdsUser, userLocale);
                        String notificationMessage = messageUtil.getMessage("order.cancelled", kdsLoc);
                        String notificationTitle = messageUtil.getMessage("notification.order.cancelled.title", kdsLoc);

                        String kdsTopic = RESTAURANT_TOPIC_PREFIX + restaurantId + "/kds/order-canceled";

                        java.util.Map<String, Object> payload = new java.util.HashMap<>(kdsDataBase);
                        putTargetKdsIdsOnKdsDataMap(payload, rec.targetKdsStationIds());

                        com.gulfnet.shared_library.model.request.StatusEventMessage eventMessage =
                                com.gulfnet.shared_library.model.request.StatusEventMessage.builder()
                                        .title(notificationTitle)
                                        .message(notificationMessage)
                                        .notificationType(NOTIF_ORDER_CANCELED)
                                        .orderId(order.getId().toString())
                                        .status(com.gulfnet.shared_library.enums.OrderStatus.CANCELED.toString())
                                        .data(payload)
                                        .build();

                        messagingTemplate.convertAndSendToUser(kdsUser.getId().toString(), kdsTopic, eventMessage);
                        log.info("[KDS WebSocket] delivered ORDER_CANCELED user-scoped kdsUserId={}, orderId={}, stompSubscribeHint=/user{}",
                                kdsUser.getId(), order.getId(), kdsTopic);

                        String extra = serializeTargetKdsIdsForAdditionalData(rec.targetKdsStationIds());
                        saveNotificationToDatabase(kdsUser,
                                notificationTitle,
                                notificationMessage,
                                NOTIF_ORDER_CANCELED, null, null, null, extra, false);
                    } catch (Exception e) {
                        log.warn("Failed to send order cancellation notification to KDS user {}: {}",
                                kdsUser.getId(), e.getMessage());
                    }
                }
                
                log.info("Sent KDS WebSocket notification for order cancellation: {} to {} KDS user(s) for restaurant {}", 
                        order.getId(), orderCancelRecipients.size(), restaurantId);
            } else {
                log.warn("[KDS WebSocket] notifyKdsOrderCanceled: no KDS users to notify (orderId={}, restaurantId={}) — check item/KDS station mapping",
                        order.getId(), restaurantId);
            }
            
            // Note: we no longer broadcast to the restaurant-wide KDS topic here.
            // KDS WebSocket notifications for order cancellation are now user-scoped
            // via convertAndSendToUser above, based on assigned KDS users.
        } catch (Exception e) {
            log.error("Failed to send KDS order cancellation notification: {}", e.getMessage(), e);
        }
    }
    
    /**
     * Send KDS WebSocket notification for profile update request opened
     */
    private void notifyKdsProfileUpdateRequestOpened(User user, java.util.UUID restaurantId, Locale userLocale) {
        if (user == null || restaurantId == null) {
            log.warn("Cannot send KDS profile update request notification: user or restaurantId is null");
            return;
        }
        
        try {
            String kdsTopic = RESTAURANT_TOPIC_PREFIX + restaurantId + "/kds/profile-update";
            Map<String, Object> kdsData = new HashMap<>();
            kdsData.put(KEY_USER_ID, user.getId().toString());
            kdsData.put(KEY_REQUEST_ID, user.getId().toString());
            kdsData.put(KEY_USER_NAME, user.getFirstName() + " " + user.getLastName());
            kdsData.put(KEY_FIRST_NAME, user.getFirstName() != null ? user.getFirstName() : "");
            kdsData.put(KEY_LAST_NAME, user.getLastName() != null ? user.getLastName() : "");
            kdsData.put(KEY_EMAIL, user.getEmail() != null ? user.getEmail() : "");
            
            if (user.getRoleId() != null) {
                kdsData.put(KEY_ROLE_ID, user.getRoleId().toString());
            }
            
            if (user.getUpdatedBy() != null) {
                kdsData.put("requesterId", user.getUpdatedBy().getId().toString());
                kdsData.put(KEY_REQUESTER_NAME, user.getUpdatedBy().getFirstName() + " " + user.getUpdatedBy().getLastName());
            } else {
                kdsData.put(KEY_REQUESTER_NAME, "User");
            }
            
            kdsData.put(KEY_RESTAURANT_ID, restaurantId.toString());
            kdsData.put(KEY_NOTIFICATION_TYPE, "PROFILE_UPDATE_REQUEST");
            kdsData.put(KEY_TIMESTAMP, OffsetDateTime.now(ZoneOffset.UTC).format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
            
            // Broadcast: one payload for all subscribers — localize from the subject user's profile (same priority as waiter/KDS helpers)
            Locale loc = localeForRecipient(user, userLocale);
            com.gulfnet.shared_library.model.request.StatusEventMessage eventMessage = 
                    com.gulfnet.shared_library.model.request.StatusEventMessage.builder()
                    .title(messageUtil.getMessage("notification.user.update.title", loc))
                    .message(messageUtil.getMessage("notification.profile.update.request.opened.body", loc, 
                            user.getFirstName() + " " + user.getLastName(),
                            user.getUpdatedBy() != null ? 
                                user.getUpdatedBy().getFirstName() + " " + user.getUpdatedBy().getLastName() : "User"))
                    .notificationType("PROFILE_UPDATE_REQUEST")
                    .userId(user.getId().toString())
                    .data(kdsData)
                    .build();
            
            messagingTemplate.convertAndSend(kdsTopic, eventMessage);
            log.info("[KDS WebSocket] profile update request opened: BROADCAST topic={} (subscribe exactly this path; no /user prefix). userIdInPayload={}, restaurantId={}",
                    kdsTopic, user.getId(), restaurantId);
            
            // Also publish to RabbitMQ for integration service to log
            if (rabbitTemplate != null) {
                try {
                    Map<String, Object> wsMessage = new HashMap<>();
                    wsMessage.put(KEY_TOPIC, kdsTopic);
                    if (eventMessage.getTitle() != null && !eventMessage.getTitle().isEmpty()) {
                        wsMessage.put(KEY_TITLE, eventMessage.getTitle());
                    }
                    wsMessage.put(KEY_MESSAGE, eventMessage.getMessage());
                    wsMessage.put(KEY_NOTIFICATION_TYPE, eventMessage.getNotificationType());
                    wsMessage.put(KEY_USER_ID, eventMessage.getUserId());
                    wsMessage.put(KEY_DATA, eventMessage.getData());
                    wsMessage.put(KEY_TIMESTAMP, OffsetDateTime.now(ZoneOffset.UTC).format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
                    wsMessage.put(KEY_TYPE, TYPE_WEBSOCKET_NOTIFICATION);
                    
                    rabbitTemplate.convertAndSend(WEBSOCKET_TOPIC_EXCHANGE, WEBSOCKET_ROUTING_KEY, wsMessage);
                    log.info("[Notification][FCM] rabbitPublish exchange={} routingKey={} payloadWsTopic={} notificationType={}",
                            WEBSOCKET_TOPIC_EXCHANGE, WEBSOCKET_ROUTING_KEY, kdsTopic, eventMessage.getNotificationType());
                } catch (Exception e) {
                    log.warn("Failed to publish KDS profile update request WebSocket message to RabbitMQ: {}", e.getMessage());
                }
            }
        } catch (Exception e) {
            log.error("Failed to send KDS profile update request notification: {}", e.getMessage(), e);
        }
    }
    
    
    /**
     * Send profile update notification to unified topic for both cashier and KDS
     * @param topic The unified topic (e.g., /topic/restaurant/{restaurantId}/profile-update)
     * @param user The user whose profile is being updated
     * @param requester The requester (cashier or other user)
     * @param message The notification message
     * @param notificationType The notification type
     * @param comments Optional comments
     * @param restaurantId The restaurant ID
     * @param userLocale The user locale
     */
    private void sendProfileUpdateToUnifiedTopic(String topic, User user, User requester, NotificationMessage message, 
                                                String notificationType, String comments, java.util.UUID restaurantId, Locale userLocale) {
        try {
            // Build unified message data that works for both cashier and KDS
            Map<String, Object> unifiedData = new HashMap<>();
            
            // Add user information
            unifiedData.put(KEY_USER_ID, user.getId().toString());
            unifiedData.put(KEY_REQUEST_ID, user.getId().toString());
            unifiedData.put(KEY_USER_NAME, user.getFirstName() + " " + user.getLastName());
            unifiedData.put(KEY_FIRST_NAME, user.getFirstName() != null ? user.getFirstName() : "");
            unifiedData.put(KEY_LAST_NAME, user.getLastName() != null ? user.getLastName() : "");
            unifiedData.put(KEY_EMAIL, user.getEmail() != null ? user.getEmail() : "");
            
            if (user.getRoleId() != null) {
                unifiedData.put(KEY_ROLE_ID, user.getRoleId().toString());
            }
            
            // Add requester information
            if (requester != null) {
                unifiedData.put("requesterId", requester.getId().toString());
                unifiedData.put(KEY_REQUESTER_NAME, requester.getFirstName() + " " + requester.getLastName());
            }
            
            // Add notification-specific data
            if (restaurantId != null) {
                unifiedData.put(KEY_RESTAURANT_ID, restaurantId.toString());
            }
            unifiedData.put(KEY_NOTIFICATION_TYPE, notificationType);
            unifiedData.put(KEY_TIMESTAMP, OffsetDateTime.now(ZoneOffset.UTC).format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
            
            // Add approval/decline status if applicable
            if (notificationType != null) {
                if (notificationType.contains(VALUE_APPROVED)) {
                    unifiedData.put(KEY_IS_APPROVED, "true");
                    unifiedData.put(KEY_APPROVED, "true");
                    unifiedData.put(KEY_STATUS, VALUE_APPROVED);
                } else if (notificationType.contains(VALUE_DECLINED) || notificationType.contains("REJECTED")) {
                    unifiedData.put(KEY_IS_APPROVED, VALUE_FALSE);
                    unifiedData.put(KEY_APPROVED, VALUE_FALSE);
                    unifiedData.put(KEY_STATUS, VALUE_DECLINED);
                }
            }
            
            if (comments != null && !comments.trim().isEmpty()) {
                unifiedData.put(KEY_COMMENTS, comments);
                unifiedData.put(KEY_MANAGER_COMMENTS, comments);
            }
            
            // Add all data from the notification message
            if (message.getData() != null) {
                unifiedData.putAll(message.getData());
            }
            
            // Build unified WebSocket message
            Map<String, Object> wsMessage = new HashMap<>();
            wsMessage.put(KEY_TITLE, message.getTitle());
            wsMessage.put("body", message.getBody());
            wsMessage.put(KEY_DATA, unifiedData);
            wsMessage.put(KEY_TIMESTAMP, OffsetDateTime.now(ZoneOffset.UTC).format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
            wsMessage.put(KEY_USER_ID, user.getId().toString());
            wsMessage.put(KEY_TOPIC, topic);
            wsMessage.put(KEY_TYPE, TYPE_WEBSOCKET_NOTIFICATION);
            wsMessage.put(KEY_NOTIFICATION_TYPE, notificationType);
            
            // Add FCM-related fields if available
            if (message.getImageUrl() != null) {
                wsMessage.put("imageUrl", message.getImageUrl());
            }
            if (message.getClickAction() != null) {
                wsMessage.put("clickAction", message.getClickAction());
            }
            if (message.getSound() != null) {
                wsMessage.put("sound", message.getSound());
            }
            if (message.getPriority() != null) {
                wsMessage.put("priority", message.getPriority().toString());
            }
            if (message.getMessageType() != null) {
                wsMessage.put("messageType", message.getMessageType().toString());
            }
            
            // Add device token for cashier if available (for FCM)
            if (requester != null && requester.getDeviceToken() != null && !requester.getDeviceToken().trim().isEmpty()) {
                wsMessage.put("deviceToken", requester.getDeviceToken());
            }
            
            // Send to unified topic (both cashier and KDS subscribe to this)
            messagingTemplate.convertAndSend(topic, wsMessage);
            log.info("[Notification][WebSocket] broadcast topic={} userId={} notificationType={}", topic, user.getId(), notificationType);
            
            // Also publish to RabbitMQ for integration service
            if (rabbitTemplate != null) {
                try {
                    rabbitTemplate.convertAndSend(WEBSOCKET_TOPIC_EXCHANGE, WEBSOCKET_ROUTING_KEY, wsMessage);
                    boolean dt = requester != null && requester.getDeviceToken() != null && !requester.getDeviceToken().trim().isEmpty();
                    log.info("[Notification][FCM] rabbitPublish exchange={} routingKey={} payloadWsTopic={} userId={} notificationType={} deviceToken={}",
                            WEBSOCKET_TOPIC_EXCHANGE, WEBSOCKET_ROUTING_KEY, topic, user.getId(), notificationType, dt ? LOG_DEVICE_TOKEN_PRESENT : LOG_DEVICE_TOKEN_ABSENT);
                } catch (Exception e) {
                    log.warn("[Notification][FCM] rabbitPublish failed unified profile topic={} userId={}: {}", topic, user.getId(), e.getMessage());
                }
            }
        } catch (Exception e) {
            log.error("Failed to send unified profile update notification to topic {}: {}", topic, e.getMessage(), e);
        }
    }
    
    /**
     * Check if a user is a cashier
     * @param user The user to check
     * @return true if the user is a cashier, false otherwise
     */
    private boolean isCashier(User user) {
        if (user == null || user.getRoleId() == null || roleRepository == null) {
            return false;
        }
        try {
            com.gulfnet.shared_library.entity.Role role = roleRepository.findById(user.getRoleId()).orElse(null);
            return role != null && "CASHIER".equals(role.getName());
        } catch (Exception e) {
            log.debug("Failed to check if user is cashier: {}", e.getMessage());
            return false;
        }
    }
    
    /**
     * Check if a user is a KDS user
     * @param user The user to check
     * @return true if the user is a KDS user, false otherwise
     */
    private boolean isKds(User user) {
        if (user == null || user.getRoleId() == null || roleRepository == null) {
            return false;
        }
        try {
            com.gulfnet.shared_library.entity.Role role = roleRepository.findById(user.getRoleId()).orElse(null);
            return role != null && "KDS".equals(role.getName());
        } catch (Exception e) {
            log.debug("Failed to check if user is KDS: {}", e.getMessage());
            return false;
        }
    }
    
    /**
     * Check if all items in an order are on HOLD
     * @param order The order to check
     * @return true if all items are on HOLD, false otherwise
     */
    private boolean checkIfAllItemsOnHold(com.gulfnet.shared_library.entity.Order order) {
        if (order == null) {
            return false;
        }
        
        try {
            // Check regular items
            if (order.getOrderedItems() != null && !order.getOrderedItems().isEmpty()) {
                for (OrderedItem item : order.getOrderedItems()) {
                    // Only check items that are not part of a combo (combo items are handled separately)
                    if (item.getOrderedCombo() == null && item.getItemStatus() != ItemStatus.ON_HOLD) {
                        return false;
                    }
                }
            }
            
            // Check combos
            if (order.getOrderedCombos() != null && !order.getOrderedCombos().isEmpty()) {
                for (com.gulfnet.shared_library.entity.OrderedCombo combo : order.getOrderedCombos()) {
                    if (combo.getItemStatus() != ItemStatus.ON_HOLD) {
                        return false;
                    }
                }
            }
            
            // If we have items/combos and all are on HOLD, return true
            return (order.getOrderedItems() != null && !order.getOrderedItems().isEmpty())
                    || (order.getOrderedCombos() != null && !order.getOrderedCombos().isEmpty());
        } catch (Exception e) {
            log.warn("Failed to check if all items are on HOLD for order {}: {}", order.getId(), e.getMessage());
            return false;
        }
    }
    
    /**
     * Sends a notification to KDS users when an item is pushed (status changed to PUSHED).
     * Finds KDS users assigned to the item's KDS station and sends notifications to them.
     * Each recipient's {@link User#getLanguageCode()} (column {@code language_code}) drives template and item-name
     * localization; {@code userLocale} is only a fallback when profile language is blank.
     *
     * @param orderedItem The ordered item that was pushed
     * @param userLocale Locale from the triggering HTTP request (waiter/cashier); fallback only for KDS copy
     */
    @Override
    public void notifyItemPushed(OrderedItem orderedItem, Locale userLocale) {
        if (orderedItem == null) {
            log.warn("Cannot send item pushed notification: orderedItem is null");
            return;
        }

        log.info("[KDS WebSocket] notifyItemPushed enter orderedItemId={}", orderedItem.getId());

        // Reload with order + item fetched in this thread. Callers often invoke this from
        // CompletableFuture.runAsync after the HTTP transaction ends; the original entity can be
        // detached and lazy-loaded relationships unavailable, which skips KDS WebSocket entirely
        // while DB paths may still partially run — users see listing updates without pop-up.
        if (orderedItem.getId() != null && orderedItemRepository != null) {
            try {
                orderedItem = orderedItemRepository.findByIdWithWaiterInfo(orderedItem.getId()).orElse(orderedItem);
                log.debug("[KDS WebSocket] notifyItemPushed reloaded orderedItemId={}, orderPresent={}, itemPresent={}",
                        orderedItem.getId(), orderedItem.getOrder() != null, orderedItem.getItem() != null);
            } catch (Exception e) {
                log.warn("Could not reload ordered item {} for KDS ITEM_PUSHED notification: {}", orderedItem.getId(), e.getMessage());
            }
        }
        
        try {
            if (orderedItem.getOrder() != null && orderedItem.getItem() != null) {
                try {
                    java.util.UUID restaurantId = getRestaurantIdFromOrder(orderedItem.getOrder());
                    java.util.List<KdsItemRecipient> kdsRecipients =
                            findKdsRecipientsForItem(orderedItem.getItem(), restaurantId);
                    if (restaurantId == null) {
                        log.warn("[KDS WebSocket] notifyItemPushed: restaurantId null (orderedItemId={}, orderId={}) — skipping item-status WebSocket",
                                orderedItem.getId(), orderedItem.getOrder().getId());
                    } else if (kdsRecipients.isEmpty()) {
                        log.warn("[KDS WebSocket] notifyItemPushed: findKdsRecipientsForItem returned 0 users (orderedItemId={}, menuItemId={}, restaurantId={})",
                                orderedItem.getId(), orderedItem.getItem().getId(), restaurantId);
                    }
                    for (KdsItemRecipient rec : kdsRecipients) {
                        com.gulfnet.shared_library.entity.User kdsUser = rec.user();
                        if (kdsUser == null) {
                            continue;
                        }
                        try {
                            com.gulfnet.shared_library.entity.User kdsRecipient = kdsUser;
                            if (userRepository != null && kdsUser.getId() != null) {
                                kdsRecipient = userRepository.findById(kdsUser.getId()).orElse(kdsUser);
                            }
                            Locale loc = localeForKdsRecipient(kdsRecipient, userLocale);
                            Map<String, String> itemData = buildItemData(orderedItem, NOTIF_ITEM_PUSHED, null, loc);
                            String itemName = getItemName(orderedItem.getItem(), loc);
                            NotificationMessage message = notificationBuilder.buildMessage(
                                    NotificationTemplate.Templates.ITEM_PUSHED_TO_KITCHEN,
                                    loc,
                                    new Object[]{itemName},
                                    itemData
                            );
                            attachTargetKdsIdsToNotificationMessageData(message, rec.targetKdsStationIds());
                            log.info("[KDS] ITEM_PUSHED waiter→KDS localized kdsUserId={} kdsPreferredLanguageCode={} resolvedLocale={} title={} --kds message={}",
                                    kdsRecipient.getId(),
                                    kdsRecipient.getLanguageCode() != null ? kdsRecipient.getLanguageCode().trim() : "",
                                    loc.toLanguageTag(),
                                    message.getTitle() != null ? message.getTitle() : "",
                                    message.getBody() != null ? message.getBody() : "");
                            saveNotificationToDatabase(kdsRecipient, message, NOTIF_ITEM_PUSHED, null);
                            if (restaurantId != null) {
                                sendKdsItemStatusWebSocket(orderedItem, restaurantId,
                                        java.util.List.of(new KdsItemRecipient(kdsRecipient, rec.targetKdsStationIds())),
                                        com.gulfnet.shared_library.enums.ItemStatus.PUSHED, NOTIF_ITEM_PUSHED, message);
                            }
                        } catch (Exception e) {
                            log.warn("Failed to save item pushed notification for KDS user {}: {}", kdsUser.getId(), e.getMessage());
                        }
                    }
                    if (!kdsRecipients.isEmpty()) {
                        log.info("Saved item pushed notification to {} category-specific KDS user(s) assigned to item {} (order: {}, restaurant: {})",
                                kdsRecipients.size(), orderedItem.getItem().getId(), orderedItem.getOrder().getId(), restaurantId);
                    }
                } catch (Exception e) {
                    log.warn("Failed to send item pushed notification to KDS users: {}", e.getMessage());
                }
            } else {
                log.warn("[KDS WebSocket] notifyItemPushed skip: order or item still null after reload (orderedItemId={}, orderNull={}, itemNull={})",
                        orderedItem.getId(), orderedItem.getOrder() == null, orderedItem.getItem() == null);
            }
            
        } catch (Exception e) {
            log.error("Failed to send item pushed notification: {}", e.getMessage(), e);
        }
    }

    /**
     * Sends user-scoped KDS WebSocket notifications to
     * {@code /topic/restaurant/{restaurantId}/kds/{kdsStationId}/item-status} (one subscription per active station),
     * with legacy {@code .../kds/item-status} only when no {@code targetKdsIds} are present.
     * Caller is responsible for DB notifications; this method only sends WebSocket and optionally RabbitMQ.
     */
    private void sendKdsItemStatusWebSocket(OrderedItem orderedItem, java.util.UUID restaurantId,
                                           java.util.List<KdsItemRecipient> recipients,
                                           com.gulfnet.shared_library.enums.ItemStatus status,
                                           String notificationType, NotificationMessage notificationMessage) {
        if (restaurantId == null) {
            log.warn("[KDS WebSocket] sendKdsItemStatusWebSocket skipped: restaurantId null (orderedItemId={}, notificationType={})",
                    orderedItem != null ? orderedItem.getId() : null, notificationType);
            return;
        }
        if (recipients == null || recipients.isEmpty()) {
            log.warn("[KDS WebSocket] sendKdsItemStatusWebSocket skipped: no recipients (orderedItemId={}, restaurantId={}, notificationType={})",
                    orderedItem != null ? orderedItem.getId() : null, restaurantId, notificationType);
            return;
        }
        try {
            java.util.UUID orderId = orderedItem.getOrder() != null ? orderedItem.getOrder().getId() : null;
            log.info("[KDS WebSocket] sendKdsItemStatusWebSocket start orderedItemId={}, orderId={}, menuItemId={}, restaurantId={}, status={}, notificationType={}, recipientCount={}, stompSubscribeHint=/user/topic/restaurant/{{restaurantId}}/kds/{{kdsStationId}}/item-status",
                    orderedItem.getId(),
                    orderId,
                    orderedItem.getItem() != null ? orderedItem.getItem().getId() : null,
                    restaurantId,
                    status,
                    notificationType,
                    recipients.size());
            Map<String, Object> kdsDataBase = new HashMap<>();
            kdsDataBase.put(KEY_ORDERED_ITEM_ID, orderedItem.getId().toString());
            kdsDataBase.put(KEY_ITEM_ID, orderedItem.getItem() != null ? orderedItem.getItem().getId().toString() : "");
            kdsDataBase.put("itemType", orderedItem.getOrderedCombo() != null ? "combo_item" : "item");
            kdsDataBase.put(KEY_ORDER_ID, orderId != null ? orderId.toString() : "");
            kdsDataBase.put(KEY_STATUS, status.toString());
            kdsDataBase.put(KEY_RESTAURANT_ID, restaurantId.toString());
            kdsDataBase.put(KEY_NOTIFICATION_TYPE, notificationType);
            kdsDataBase.put(KEY_TIMESTAMP, OffsetDateTime.now(ZoneOffset.UTC).format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));

            String orderNumber = "";
            if (orderId != null && orderRepository != null) {
                try {
                    orderNumber = orderRepository.findOrderNumberByOrderId(orderId).orElse("");
                } catch (Exception e) {
                    log.debug(LOG_COULD_NOT_GET_ORDER_NUMBER_KDS, e.getMessage());
                }
            }
            kdsDataBase.put(KEY_ORDER_NUMBER, orderNumber);

            if (orderId != null && orderRepository != null) {
                try {
                    java.util.Optional<java.util.UUID> tableIdOpt = orderRepository.findTableIdByOrderId(orderId);
                    if (tableIdOpt.isPresent()) {
                        kdsDataBase.put(KEY_TABLE_ID, tableIdOpt.get().toString());
                    }
                    java.util.Optional<String> tableCodeOpt = orderRepository.findTableCodeByOrderId(orderId);
                    if (tableCodeOpt.isPresent()) {
                        kdsDataBase.put(KEY_TABLE_CODE, tableCodeOpt.get());
                    }
                } catch (Exception e) {
                    log.debug(LOG_COULD_NOT_GET_TABLE_ID, e.getMessage());
                }
            }
            String tableCodeFallback = orderedItem.getOrder() != null ? getTableCodeFromOrder(orderedItem.getOrder()) : "";
            if (!tableCodeFallback.isEmpty()) {
                kdsDataBase.put(KEY_TABLE_NUMBER, tableCodeFallback);
            }

            String body = notificationMessage != null && notificationMessage.getBody() != null
                    ? notificationMessage.getBody() : "";
            String title = notificationMessage != null && notificationMessage.getTitle() != null
                    ? notificationMessage.getTitle() : "";

            com.gulfnet.shared_library.model.request.StatusEventMessage rabbitPayload = null;
            String rabbitTopicForIntegration = null;

            for (KdsItemRecipient recipient : recipients) {
                com.gulfnet.shared_library.entity.User kdsUser = recipient.user();
                if (kdsUser == null || kdsUser.getId() == null) {
                    continue;
                }
                java.util.List<String> destinations =
                        resolveKdsItemStatusTopicDestinations(restaurantId, recipient.targetKdsStationIds());
                for (String kdsTopic : destinations) {
                    try {
                        java.util.Map<String, Object> kdsData = new java.util.HashMap<>(kdsDataBase);
                        putTargetKdsIdsOnKdsDataMap(kdsData, recipient.targetKdsStationIds());
                        com.gulfnet.shared_library.model.request.StatusEventMessage eventMessage =
                                com.gulfnet.shared_library.model.request.StatusEventMessage.builder()
                                        .title(title)
                                        .message(body)
                                        .notificationType(notificationType)
                                        .orderId(orderId != null ? orderId.toString() : "")
                                        .itemId(orderedItem.getId().toString())
                                        .status(status.toString())
                                        .data(kdsData)
                                        .userId(kdsUser.getId().toString())
                                        .build();
                        messagingTemplate.convertAndSendToUser(kdsUser.getId().toString(), kdsTopic, eventMessage);
                        if (rabbitPayload == null) {
                            rabbitPayload = eventMessage;
                            rabbitTopicForIntegration = kdsTopic;
                        }
                        log.info("[KDS WebSocket] delivered (user-scoped) notificationType={}, kdsUserId={}, orderedItemId={}, stompSubscribeHint=/user{}",
                                notificationType, kdsUser.getId(), orderedItem.getId(), kdsTopic);
                        if (NOTIF_ITEM_PUSHED.equals(notificationType)) {
                            String kdsLang = kdsUser.getLanguageCode() != null ? kdsUser.getLanguageCode().trim() : "";
                            log.info("[KDS] ITEM_PUSHED kds-side delivery (STOMP) kdsUserId={} kdsPreferredLanguageCode={} title={} --kds message={}",
                                    kdsUser.getId(), kdsLang, title, body);
                        }
                    } catch (Exception e) {
                        log.warn("Failed to send KDS WebSocket notification to user {} for {} ({}): {}",
                                kdsUser.getId(), notificationType, orderedItem.getId(), e.getMessage());
                    }
                }
            }
            log.debug("Sent KDS WebSocket {} to {} user(s) for item {} (order: {})",
                    notificationType, recipients.size(), orderedItem.getItem() != null ? orderedItem.getItem().getId() : "?", orderId);

            if (rabbitTemplate != null && rabbitPayload != null) {
                try {
                    Map<String, Object> wsMessage = new HashMap<>();
                    wsMessage.put(KEY_TOPIC, rabbitTopicForIntegration != null
                            ? rabbitTopicForIntegration
                            : legacyKdsItemStatusTopic(restaurantId));
                    if (rabbitPayload.getTitle() != null && !rabbitPayload.getTitle().isEmpty()) {
                        wsMessage.put(KEY_TITLE, rabbitPayload.getTitle());
                    }
                    wsMessage.put(KEY_MESSAGE, rabbitPayload.getMessage());
                    wsMessage.put(KEY_NOTIFICATION_TYPE, rabbitPayload.getNotificationType());
                    wsMessage.put(KEY_ORDER_ID, rabbitPayload.getOrderId());
                    wsMessage.put(KEY_STATUS, rabbitPayload.getStatus());
                    wsMessage.put(KEY_DATA, rabbitPayload.getData());
                    wsMessage.put(KEY_TIMESTAMP, OffsetDateTime.now(ZoneOffset.UTC).format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
                    wsMessage.put(KEY_TYPE, TYPE_WEBSOCKET_NOTIFICATION);
                    rabbitTemplate.convertAndSend(WEBSOCKET_TOPIC_EXCHANGE, WEBSOCKET_ROUTING_KEY, wsMessage);
                    log.info("[Notification][FCM] rabbitPublish exchange={} routingKey={} payloadWsTopic={} notificationType={} (KDS item event, deduped)",
                            WEBSOCKET_TOPIC_EXCHANGE, WEBSOCKET_ROUTING_KEY,
                            rabbitTopicForIntegration != null ? rabbitTopicForIntegration : legacyKdsItemStatusTopic(restaurantId),
                            rabbitPayload.getNotificationType());
                    if (NOTIF_ITEM_PUSHED.equals(notificationType)) {
                        log.info("[KDS] ITEM_PUSHED rabbit→integration kdsUserId={} title={} --kds message={}",
                                rabbitPayload.getUserId() != null ? rabbitPayload.getUserId() : "",
                                rabbitPayload.getTitle() != null ? rabbitPayload.getTitle() : "",
                                rabbitPayload.getMessage() != null ? rabbitPayload.getMessage() : "");
                    }
                } catch (Exception e) {
                    log.warn("Failed to publish KDS {} WebSocket message to RabbitMQ: {}", notificationType, e.getMessage());
                }
            }
        } catch (Exception e) {
            log.error("Failed to send KDS WebSocket for {} (item {}): {}", notificationType, orderedItem.getId(), e.getMessage(), e);
        }
    }
    
    /**
     * Sends WebSocket + in-app notifications to assigned KDS users when a line moves to
     * {@link com.gulfnet.shared_library.enums.ItemStatus#COOKING}, {@code READY}, or {@code DELAYED}.
     * Pop-up copy matches the PUSHED pattern: localized item name + short status line
     * ({@code notification.kds.*} keys). WebSocket {@code notificationType} stays {@code ITEM_STATUS_UPDATE};
     * persisted type is {@code KDS_COOKING}, {@code KDS_READY}, or {@code KDS_DELAYED}.
     *
     * @param orderedItem the ordered item whose status changed
     * @param newStatus   the new status of the ordered item
     * @param userLocale locale from the triggering HTTP request when the recipient has no profile language
     */
    @Override
    public void notifyKdsItemStatusChange(OrderedItem orderedItem, com.gulfnet.shared_library.enums.ItemStatus newStatus,
                                          Locale userLocale) {
        if (orderedItem == null || newStatus == null) {
            log.warn("Cannot send KDS item status change notification: orderedItem or newStatus is null");
            return;
        }

        log.info("[KDS WebSocket] notifyKdsItemStatusChange enter orderedItemId={}, newStatus={}",
                orderedItem.getId(), newStatus);
        
        try {
            if (orderedItem.getOrder() != null && orderedItem.getItem() != null) {
                java.util.UUID restaurantId = getRestaurantIdFromOrder(orderedItem.getOrder());
                java.util.List<KdsItemRecipient> kdsRecipients =
                        findKdsRecipientsForItem(orderedItem.getItem(), restaurantId);
                if (restaurantId == null) {
                    log.warn("[KDS WebSocket] notifyKdsItemStatusChange: restaurantId null (orderedItemId={}, orderId={}) — skip WebSocket",
                            orderedItem.getId(), orderedItem.getOrder().getId());
                } else if (kdsRecipients.isEmpty()) {
                    log.warn("[KDS WebSocket] notifyKdsItemStatusChange: no KDS users (orderedItemId={}, menuItemId={}, restaurantId={}, newStatus={})",
                            orderedItem.getId(), orderedItem.getItem().getId(), restaurantId, newStatus);
                }
                
                if (!kdsRecipients.isEmpty() && restaurantId != null) {
                    NotificationTemplate lineTemplate = templateForKdsLineWorkflowStatus(newStatus);
                    String saveType = kdsLineWorkflowSaveType(newStatus);
                    if (lineTemplate == null || saveType == null) {
                        log.warn("notifyKdsItemStatusChange: unsupported KDS workflow status {} (orderedItemId={})",
                                newStatus, orderedItem.getId());
                        return;
                    }

                    java.util.UUID orderId = orderedItem.getOrder() != null ? orderedItem.getOrder().getId() : null;
                    Map<String, Object> kdsDataTemplate = new HashMap<>();
                    kdsDataTemplate.put(KEY_ORDERED_ITEM_ID, orderedItem.getId().toString());
                    kdsDataTemplate.put(KEY_ITEM_ID, orderedItem.getItem().getId().toString());
                    kdsDataTemplate.put("itemType", orderedItem.getOrderedCombo() != null ? "combo_item" : "item");
                    kdsDataTemplate.put(KEY_ORDER_ID, orderId != null ? orderId.toString() : "");
                    kdsDataTemplate.put(KEY_STATUS, newStatus.toString());
                    kdsDataTemplate.put(KEY_RESTAURANT_ID, restaurantId.toString());
                    kdsDataTemplate.put(KEY_NOTIFICATION_TYPE, NOTIF_ITEM_STATUS_UPDATE);
                    kdsDataTemplate.put("kdsPersistedType", saveType);
                    kdsDataTemplate.put(KEY_TIMESTAMP, OffsetDateTime.now(ZoneOffset.UTC).format(java.time.format.DateTimeFormatter.ISO_LOCAL_DATE_TIME));
                    
                    boolean publishedRabbit = false;
                    String kdsTopicForRabbit = null;
                    // Send user-scoped WebSocket notification to each assigned KDS user (per-recipient locale)
                    for (KdsItemRecipient rec : kdsRecipients) {
                        com.gulfnet.shared_library.entity.User kdsUser = rec.user();
                        if (kdsUser == null || kdsUser.getId() == null) {
                            continue;
                        }
                        try {
                            Locale kdsLoc = localeForKdsRecipient(kdsUser, userLocale);
                            String itemName = getItemName(orderedItem.getItem(), kdsLoc);
                            Map<String, String> itemData = buildItemData(orderedItem, saveType, null, kdsLoc);
                            NotificationMessage dbMessage = notificationBuilder.buildMessage(
                                    lineTemplate,
                                    kdsLoc,
                                    new Object[]{itemName},
                                    itemData
                            );
                            attachTargetKdsIdsToNotificationMessageData(dbMessage, rec.targetKdsStationIds());
                            java.util.Map<String, Object> kdsData = new java.util.HashMap<>(kdsDataTemplate);
                            putTargetKdsIdsOnKdsDataMap(kdsData, rec.targetKdsStationIds());
                            com.gulfnet.shared_library.model.request.StatusEventMessage eventMessage =
                                    com.gulfnet.shared_library.model.request.StatusEventMessage.builder()
                                            .title(dbMessage.getTitle())
                                            .message(dbMessage.getBody())
                                            .notificationType(NOTIF_ITEM_STATUS_UPDATE)
                                            .orderId(orderId != null ? orderId.toString() : null)
                                            .itemId(orderedItem.getId().toString())
                                            .userId(kdsUser.getId().toString())
                                            .status(newStatus.toString())
                                            .data(kdsData)
                                            .build();
                            java.util.List<String> statusDestinations =
                                    resolveKdsItemStatusTopicDestinations(restaurantId, rec.targetKdsStationIds());
                            for (String kdsTopic : statusDestinations) {
                                messagingTemplate.convertAndSendToUser(kdsUser.getId().toString(), kdsTopic, eventMessage);
                                if (kdsTopicForRabbit == null) {
                                    kdsTopicForRabbit = kdsTopic;
                                }
                                log.info("[KDS WebSocket] delivered ITEM_STATUS_UPDATE user-scoped kdsUserId={}, orderedItemId={}, newStatus={}, saveType={}, stompSubscribeHint=/user{}",
                                        kdsUser.getId(), orderedItem.getId(), newStatus, saveType, kdsTopic);
                            }
                            saveNotificationToDatabase(kdsUser, dbMessage, saveType, null);
                            if (rabbitTemplate != null && !publishedRabbit) {
                                try {
                                    Map<String, Object> wsMessage = new HashMap<>();
                                    wsMessage.put(KEY_TOPIC, kdsTopicForRabbit != null
                                            ? kdsTopicForRabbit
                                            : legacyKdsItemStatusTopic(restaurantId));
                                    if (eventMessage.getTitle() != null && !eventMessage.getTitle().isEmpty()) {
                                        wsMessage.put(KEY_TITLE, eventMessage.getTitle());
                                    }
                                    wsMessage.put(KEY_MESSAGE, eventMessage.getMessage());
                                    wsMessage.put(KEY_NOTIFICATION_TYPE, eventMessage.getNotificationType());
                                    wsMessage.put(KEY_ITEM_ID, eventMessage.getItemId());
                                    wsMessage.put(KEY_STATUS, eventMessage.getStatus());
                                    wsMessage.put(KEY_DATA, eventMessage.getData());
                                    wsMessage.put(KEY_TIMESTAMP, OffsetDateTime.now(ZoneOffset.UTC).format(java.time.format.DateTimeFormatter.ISO_LOCAL_DATE_TIME));
                                    wsMessage.put(KEY_TYPE, TYPE_WEBSOCKET_NOTIFICATION);
                                    rabbitTemplate.convertAndSend(WEBSOCKET_TOPIC_EXCHANGE, WEBSOCKET_ROUTING_KEY, wsMessage);
                                    publishedRabbit = true;
                                    log.info("[Notification][FCM] rabbitPublish exchange={} routingKey={} payloadWsTopic={} notificationType={} (KDS item-status workflow, deduped)",
                                            WEBSOCKET_TOPIC_EXCHANGE, WEBSOCKET_ROUTING_KEY,
                                            kdsTopicForRabbit != null ? kdsTopicForRabbit : legacyKdsItemStatusTopic(restaurantId),
                                            eventMessage.getNotificationType());
                                } catch (Exception e) {
                                    log.warn("Failed to publish KDS item status change to RabbitMQ: {}", e.getMessage());
                                }
                            }
                        } catch (Exception e) {
                            log.warn("Failed to send KDS item status notification to user {} for item {} ({}): {}",
                                    kdsUser.getId(), orderedItem.getId(), newStatus, e.getMessage());
                        }
                    }
                    
                    log.info("Sent KDS item status change ({}) notification (WebSocket + DB) to {} category-specific user(s) for item {} (order: {})",
                            newStatus, kdsRecipients.size(), orderedItem.getItem().getId(), orderId);
                }
            } else {
                log.warn("[KDS WebSocket] notifyKdsItemStatusChange skip: order or item null (orderedItemId={}, orderNull={}, itemNull={})",
                        orderedItem.getId(), orderedItem.getOrder() == null, orderedItem.getItem() == null);
            }
        } catch (Exception e) {
            log.error("Failed to send KDS item status change notification for item {} ({}): {}", 
                    orderedItem.getId(), newStatus, e.getMessage(), e);
        }
    }
}