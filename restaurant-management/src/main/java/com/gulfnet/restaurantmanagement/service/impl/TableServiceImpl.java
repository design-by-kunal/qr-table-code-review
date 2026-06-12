package com.gulfnet.restaurantmanagement.service.impl;

import com.gulfnet.restaurantmanagement.config.RestaurantChainConfigProperties;
import com.gulfnet.restaurantmanagement.service.TableService;
import com.gulfnet.restaurantmanagement.service.NotificationService;
import com.gulfnet.restaurantmanagement.service.PrintQrCodeService;
import com.gulfnet.restaurantmanagement.util.MessageUtil;
import com.gulfnet.shared_library.util.TranslationUtils;
import com.gulfnet.restaurantmanagement.config.LocalizationProperties;
import com.gulfnet.shared_library.config.AWSService;
import com.gulfnet.shared_library.entity.RestaurantRow;
import com.gulfnet.shared_library.entity.RestaurantSection;
import com.gulfnet.shared_library.entity.RestaurantSectionTranslation;
import com.gulfnet.shared_library.entity.RestaurantTable;
import com.gulfnet.shared_library.entity.TableAssignment;
import com.gulfnet.shared_library.entity.User;
import com.gulfnet.shared_library.entity.Order;
import com.gulfnet.shared_library.entity.RestaurantLayout;
import com.gulfnet.shared_library.entity.OrderedItem;
import com.gulfnet.shared_library.entity.OrderedCombo;
import com.gulfnet.shared_library.entity.Transaction;
import com.gulfnet.shared_library.model.request.TableAssignmentRequest;
import com.gulfnet.shared_library.model.request.TableStatusPayload;
import com.gulfnet.shared_library.model.request.StatusEventMessage;
import com.gulfnet.shared_library.model.request.GuestTransferRequest;
import com.gulfnet.shared_library.model.request.TableMoveRequest;
import com.gulfnet.shared_library.model.request.TableSectionRequest;
import com.gulfnet.shared_library.model.request.WaiterTableAssignmentRequest;
import com.gulfnet.shared_library.enums.RequestStatus;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import static com.gulfnet.restaurantmanagement.config.RabbitMQConfig.*;
import com.gulfnet.shared_library.model.response.dto.PaginationMetaData;
import com.gulfnet.shared_library.model.response.dto.ResponseDto;
import com.gulfnet.shared_library.model.response.dto.TableAssignmentResponse;
import com.gulfnet.shared_library.model.response.dto.TableAssignmentWrapper;
import com.gulfnet.shared_library.model.response.dto.TableListResponse;
import com.gulfnet.shared_library.model.response.dto.TableListResponseDto;
import com.gulfnet.shared_library.model.response.dto.TableListResponseDtoV2;
import com.gulfnet.shared_library.model.response.dto.TableResponseV2;
import com.gulfnet.shared_library.model.response.dto.RowResponseV2;
import com.gulfnet.shared_library.model.response.dto.SectionResponseV2;
import com.gulfnet.shared_library.model.response.dto.TableSessionInfo;
import com.gulfnet.shared_library.model.response.dto.WaiterInfo;
import com.gulfnet.shared_library.model.response.dto.TableStatusResponseWrapper;
import com.gulfnet.shared_library.model.response.dto.SessionResponseDto;
import com.gulfnet.shared_library.model.response.dto.SessionWrapperDto;
import com.gulfnet.shared_library.model.response.dto.RestaurantDetailsDto;
import com.gulfnet.shared_library.model.response.dto.GuestTransferResponse;
import com.gulfnet.shared_library.model.response.dto.TableMoveResponse;
import com.gulfnet.shared_library.entity.Session;
import com.gulfnet.shared_library.repository.SessionRepository;
import com.gulfnet.restaurantmanagement.util.JwtUtil;
import com.gulfnet.shared_library.enums.ItemStatus;
import com.gulfnet.shared_library.enums.OrderStatus;
import com.gulfnet.shared_library.enums.QrCodeType;
import com.gulfnet.shared_library.enums.TableStatus;
import com.gulfnet.shared_library.enums.PaymentSystemType;
import com.gulfnet.shared_library.enums.TransactionStatus;
import com.gulfnet.shared_library.enums.ActionType;
import com.gulfnet.restaurantmanagement.service.AuditTrailService;
import com.gulfnet.shared_library.entity.Restaurant;
import com.gulfnet.shared_library.repository.RestaurantRepository;

import java.util.concurrent.CompletableFuture;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import com.gulfnet.shared_library.repository.RestaurantTableRepository;
import com.gulfnet.shared_library.repository.RestaurantRowRepository;
import com.gulfnet.shared_library.repository.RestaurantSectionRepository;
import com.gulfnet.shared_library.repository.TableAssignmentRepository;
import com.gulfnet.shared_library.repository.UserRepository;
import com.gulfnet.shared_library.repository.OrderRepository;
import com.gulfnet.shared_library.repository.RestaurantLayoutRepository;
import com.gulfnet.shared_library.repository.TransactionRepository;
import com.gulfnet.shared_library.repository.OrderedItemRepository;
import com.gulfnet.shared_library.repository.OrderedComboRepository;
import com.gulfnet.shared_library.repository.RoleRepository;
import com.gulfnet.shared_library.entity.Role;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;
import java.math.BigDecimal;

// QR generation and config
import com.gulfnet.restaurantmanagement.config.AppProperties;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.WriterException;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;

@Slf4j
@Service
@RequiredArgsConstructor
public class TableServiceImpl implements TableService {

    // Constants
    private static final String ROLE_MANAGER = "MANAGER";
    private static final String ROLE_WAITER = "WAITER";
    private static final String KEY_TIMESTAMP = "timestamp";
    private static final String PARAM_SECTION_IDS = "sectionIds";
    private static final String GUEST_TRANSFER_TO_TABLE = " to table ";
    private static final String LOG_TRANSACTION_NO_ORDER = "Transaction {} has no order, skipping";
    private static final String LOG_FOUND_ORDERS = "TableServiceImpl: Found {} orders for tableId={} with sessionIds={}";
    private static final String MSG_SESSION_START_SUCCESS_CREATED = "session.start.success.created";
    private static final String MSG_TABLE_LIST_FETCH_SUCCESS = "table.list.fetch.success";
    private static final String MSG_TABLE_NOT_FOUND = "table.not.found";
    private static final String MSG_RESTAURANT_ID_RESOLVE_FAILED = "restaurant.id.resolve.failed";
    private static final String MSG_TABLE_STATUS_UPDATED = "table.status.updated";
    private static final String MSG_WAITER_NOT_FOUND = "waiter.not.found";
    private static final String MSG_SESSION_TOKEN_EXPIRED = "session.token.expired";

    @Value("${jwt.expiry-hours:24}")
    private int jwtExpiryHours;

    private final TableAssignmentRepository tableAssignmentRepository;
    private final RestaurantTableRepository restaurantTableRepository;
    private final RestaurantRowRepository restaurantRowRepository;
    private final RestaurantSectionRepository restaurantSectionRepository;
    private final UserRepository userRepository;
    private final OrderRepository orderRepository;
    private final RestaurantLayoutRepository restaurantLayoutRepository;
    private final MessageUtil messageUtil;
    private final SimpMessagingTemplate messagingTemplate;
    
    @Autowired(required = false)
    private RabbitTemplate rabbitTemplate;
    
    private final AWSService awsService;
    private final SessionRepository sessionRepository;
    private final JwtUtil jwtUtil;
    private final RestaurantChainConfigProperties restaurantChainConfigProperties;
    private final LocalizationProperties localizationProperties;
    private final NotificationService notificationService;
    private final TransactionRepository transactionRepository;
    private final AppProperties appProperties;
    private final OrderedItemRepository orderedItemRepository;
    private final OrderedComboRepository orderedComboRepository;
    private final RoleRepository roleRepository;
    private final AuditTrailService auditTrailService;
    private final RestaurantRepository restaurantRepository;
    private final PrintQrCodeService printQrCodeService;
    
    @PersistenceContext
    private EntityManager entityManager;

    // ==================== HELPER METHODS FOR WEBSOCKET NOTIFICATIONS ====================

    /**
     * Sends WebSocket notification for table status update (optimistic update).
     */
    private void sendTableStatusWebSocketNotification(Locale locale, UUID restaurantId, UUID tableId, TableStatus newStatus) {
        try {
            String topic = "/topic/restaurant/" + restaurantId + "/table-status";
            StatusEventMessage eventMessage = StatusEventMessage.builder()
                    .message(messageUtil.getMessage(MSG_TABLE_STATUS_UPDATED, locale))
                    .build();
            messagingTemplate.convertAndSend(topic, eventMessage);
            log.info("[Notification][WebSocket] broadcast topic={} tableId={} status={} restaurantId={}",
                    topic, tableId, newStatus, restaurantId);
            
            // Also publish to RabbitMQ for integration service to log
            publishToRabbitMQ(topic, eventMessage);
        } catch (Exception e) {
            log.error("Failed to send WebSocket notification for table status update: {}", e.getMessage());
        }
    }

    /**
     * Helper method to get restaurant ID from RestaurantTable.
     * Handles lazy loading by fetching from repository if needed.
     */
    private Restaurant getRestaurantFromTable(RestaurantTable table) {
        if (table == null) return null;
        UUID restaurantId = getRestaurantIdFromTable(table);
        if (restaurantId == null) return null;
        return restaurantRepository.findById(restaurantId).orElse(null);
    }

    /**
     * Resolves the restaurant id associated with a {@link RestaurantTable}.
     * <p>
     * Attempts to traverse already-loaded relationships (row → section → layout → restaurant). If any part of the
     * graph is not initialized, it falls back to loading the table using a JOIN FETCH query to avoid lazy-loading
     * failures and returns the restaurant id from the fully-loaded graph.
     * </p>
     *
     * @param table table entity (may be partially initialized)
     * @return restaurant id for the table
     * @throws ResponseStatusException if the table cannot be loaded or relationships cannot be resolved
     */
    private UUID getRestaurantIdFromTable(RestaurantTable table) {
        try {
            // Try to get from already loaded entity
            if (table.getRestaurantRow() != null && 
                table.getRestaurantRow().getRestaurantSection() != null &&
                table.getRestaurantRow().getRestaurantSection().getRestaurantLayout() != null &&
                table.getRestaurantRow().getRestaurantSection().getRestaurantLayout().getRestaurant() != null) {
                return table.getRestaurantRow()
                    .getRestaurantSection()
                    .getRestaurantLayout()
                    .getRestaurant()
                    .getId();
            }
            
            // If not loaded, fetch from repository with JOIN FETCH to load all relationships
            RestaurantTable loadedTable = restaurantTableRepository.findByIdWithRelationships(table.getId())
                .orElseThrow(() -> new RuntimeException("Table not found: " + table.getId()));
            
            // Validate that all relationships are loaded
            if (loadedTable.getRestaurantRow() == null ||
                loadedTable.getRestaurantRow().getRestaurantSection() == null ||
                loadedTable.getRestaurantRow().getRestaurantSection().getRestaurantLayout() == null ||
                loadedTable.getRestaurantRow().getRestaurantSection().getRestaurantLayout().getRestaurant() == null) {
                throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                "Table relationships not properly loaded for table: " + table.getId());
            }
            
            return loadedTable.getRestaurantRow()
                .getRestaurantSection()
                .getRestaurantLayout()
                .getRestaurant()
                .getId();
        } catch (Exception e) {
            log.error("Failed to get restaurant ID from table {}: {}", table.getId(), e.getMessage(), e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                "Failed to get restaurant ID from table: " + e.getMessage(), e);
        }
    }

    /**
     * Updates table status asynchronously in the database (status, block reason, updatedBy, updatedAt).
     */
    private void updateTableStatusAsync(UUID tableId, TableStatus newStatus, TableStatus oldStatus,
                                       User updatedBy, LocalDateTime updatedAt, String blockReason) {
        CompletableFuture.runAsync(() -> {
            try {
                RestaurantTable tableToUpdate = restaurantTableRepository.findById(tableId)
                        .orElseThrow(() -> new RuntimeException("RestaurantTable not found: " + tableId));
                tableToUpdate.setTableStatus(newStatus);
                tableToUpdate.setBlockReason(blockReason);
                tableToUpdate.setUpdatedAt(updatedAt != null ? updatedAt.atOffset(ZoneOffset.UTC) : null);
                if (updatedBy != null) {
                    tableToUpdate.setUpdatedBy(updatedBy);
                }
                restaurantTableRepository.save(tableToUpdate);
                
                // Handle session expiration in async context if needed
                List<Session> activeSessions = sessionRepository.findByTableIdAndExpiredAtIsNull(tableId);
                if (!activeSessions.isEmpty() && (newStatus == TableStatus.BLOCKED || 
                    (oldStatus == TableStatus.OCCUPIED && newStatus != TableStatus.OCCUPIED))) {
                    OffsetDateTime expiredAt = OffsetDateTime.now(ZoneOffset.UTC);
                    for (Session session : activeSessions) {
                        session.setExpiredAt(expiredAt);
                    }
                    sessionRepository.saveAll(activeSessions);
                }
                
                log.debug("Successfully updated table status in database asynchronously: {} to {}", tableId, newStatus);
            } catch (Exception e) {
                log.error("Failed to update table status in database asynchronously for table {}: {}", tableId, e.getMessage(), e);
            }
        });
    }

    /**
     * Updates table status with WebSocket notification and async database update.
     */
    private void updateTableStatusWithNotification(RestaurantTable table, TableStatus newStatus, 
                                                   TableStatus oldStatus, User updatedBy, Locale locale) {
        UUID tableId = table.getId();
        UUID restaurantId = getRestaurantIdFromTable(table);
        
        // Send WebSocket notification (optimistic update)
        sendTableStatusWebSocketNotification(locale, restaurantId, tableId, newStatus);
        
        // Update database asynchronously (blockReason is only persisted here, not in a synchronous save)
        String blockReasonToSave = table.getBlockReason();
        updateTableStatusAsync(tableId, newStatus, oldStatus, updatedBy, LocalDateTime.now(ZoneOffset.UTC), blockReasonToSave);
        
        // Update local object for response
        table.setTableStatus(newStatus);
    }

    /**
     * Helper method to get restaurant ID from Order.
     * Handles lazy loading by getting from restaurant table if needed.
     */
    private UUID getRestaurantIdFromOrder(Order order) {
        if (order == null) {
            log.error("Order is null when trying to get restaurant ID");
            throw new IllegalStateException("Order cannot be null");
        }
        
        // Try to get from order's restaurant directly
        try {
            if (order.getRestaurant() != null && order.getRestaurant().getId() != null) {
                return order.getRestaurant().getId();
            }
        } catch (Exception e) {
            log.debug("Restaurant not loaded from order {}, will try restaurant table: {}", order.getId(), e.getMessage());
        }
        
        // Fallback: get from restaurant table
        try {
            if (order.getRestaurantTable() != null) {
                return getRestaurantIdFromTable(order.getRestaurantTable());
            }
        } catch (Exception e) {
            log.error("Failed to get restaurant ID from order {} via restaurant table: {}", order.getId(), e.getMessage());
        }
        
        throw new IllegalStateException("Failed to get restaurant ID for order: " + order.getId());
    }

    /**
     * Sends WebSocket notification for item/combo status update (optimistic update).
     */
    private void sendItemStatusWebSocketNotification(Locale userLocale, UUID restaurantId, UUID itemId, ItemStatus newStatus, String itemType) {
        try {
            String topic = "/topic/restaurant/" + restaurantId + "/item-status";
            Map<String, Object> itemData = new HashMap<>();
            itemData.put("itemId", itemId.toString());
            itemData.put("itemType", itemType); // "item" or "combo"
            itemData.put("status", newStatus.toString());
            itemData.put("restaurantId", restaurantId.toString());
            itemData.put("notificationType", "ITEM_STATUS_UPDATE");
            itemData.put(KEY_TIMESTAMP, LocalDateTime.now(ZoneOffset.UTC).format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
            
            StatusEventMessage eventMessage = StatusEventMessage.builder()
                    .title(messageUtil.getMessage("notification.item.status.update.title", userLocale))
                    .message(messageUtil.getMessage("item.status.updated", userLocale))
                    .notificationType("ITEM_STATUS_UPDATE")
                    .itemId(itemId.toString())
                    .status(newStatus.toString())
                    .data(itemData)
                    .build();
            messagingTemplate.convertAndSend(topic, eventMessage);
            log.info("[Notification][WebSocket] broadcast topic={} notificationType=ITEM_STATUS_UPDATE itemType={} itemId={} status={} restaurantId={}",
                    topic, itemType, itemId, newStatus, restaurantId);
            
            // Also publish to RabbitMQ for integration service to log
            publishToRabbitMQ(topic, eventMessage);
        } catch (Exception e) {
            log.error("Failed to send WebSocket notification for {} status update: {}", itemType, e.getMessage());
        }
    }

    /**
     * Cancels all active items (PUSHED, COOKING, DELAYED, READY) in PENDING and OPEN transactions 
     * when a table is marked for cleanup. This ensures KDS doesn't have items in active states.
     * 
     * Note: When cashiers add items, transactions become OPEN (not PENDING), so we need to cancel
     * items in both OPEN and PENDING transactions to match the validation logic.
     */
    private void cancelActiveItemsForCleanup(UUID tableId, Locale locale) {
        try {
            List<Session> activeSessions = sessionRepository.findByTableIdAndExpiredAtIsNull(tableId);
            if (activeSessions.isEmpty()) {
                return;
            }

            OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
            int totalCancelledItems = 0;
            int totalCancelledCombos = 0;

            for (Session activeSession : activeSessions) {
                // Find all PENDING and OPEN transactions for this session
                // Cashiers add items which create OPEN transactions, so we need to cancel items in both
                List<Transaction> pendingTransactions = transactionRepository
                        .findBySessionIdAndTransactionStatusOrderByCreatedAtAsc(activeSession.getId(), TransactionStatus.PENDING);
                List<Transaction> openTransactions = transactionRepository
                        .findBySessionIdAndTransactionStatusOrderByCreatedAtAsc(activeSession.getId(), TransactionStatus.OPEN);
                
                // Combine all transactions that need item cancellation
                List<Transaction> transactionsToCancel = new ArrayList<>();
                transactionsToCancel.addAll(pendingTransactions);
                transactionsToCancel.addAll(openTransactions);

                for (Transaction transaction : transactionsToCancel) {
                    Order order = transaction.getOrder();
                    if (order == null) {
                        continue;
                    }

                    UUID restaurantId = getRestaurantIdFromOrder(order);

                    // Cancel all active items (excluding combo items, as they're handled separately)
                    List<OrderedItem> activeItems = orderedItemRepository.findByOrderId(order.getId())
                            .stream()
                            .filter(item -> item.getOrderedCombo() == null) // Only regular items, not combo items
                            .filter(item -> {
                                ItemStatus status = item.getItemStatus();
                                // Cancel items that are in active states (not CANCELLED, ON_HOLD, or SERVED)
                                return status != ItemStatus.CANCELED 
                                        && status != ItemStatus.ON_HOLD 
                                        && status != ItemStatus.SERVED;
                            })
                            .collect(Collectors.toList());

                    for (OrderedItem item : activeItems) {
                        // Capture status before cancellation for wastage reporting
                        if (item.getItemStatus() != null && item.getItemStatus() != ItemStatus.CANCELED) {
                            item.setWastageSourceStatus(item.getItemStatus());
                        }
                        item.setItemStatus(ItemStatus.CANCELED);
                        item.setUpdatedAt(now);
                        item.setReason("Cancelled automatically when table marked for cleanup");
                        orderedItemRepository.save(item);
                        totalCancelledItems++;

                        // Skip broadcast WebSocket notification for CANCELED — notifyItemCanceled sends
                        // user-scoped KDS notifications to only the assigned KDS users for this item's category.
                        // Broadcasting CANCELED to /topic/restaurant/{id}/item-status causes cross-KDS notifications.

                        notifyItemCanceledBestEffort(item, locale);

                        log.info("Cancelled active item {} for order {} when table {} marked for cleanup", 
                                item.getId(), order.getId(), tableId);
                    }

                    // Cancel all active combos
                    List<OrderedCombo> activeCombos = orderedComboRepository.findByOrderId(order.getId())
                            .stream()
                            .filter(combo -> {
                                ItemStatus status = combo.getItemStatus();
                                // Cancel combos that are in active states (not CANCELLED, ON_HOLD, or SERVED)
                                return status != ItemStatus.CANCELED 
                                        && status != ItemStatus.ON_HOLD 
                                        && status != ItemStatus.SERVED;
                            })
                            .collect(Collectors.toList());

                    for (OrderedCombo combo : activeCombos) {
                        // Capture status before cancellation for wastage reporting
                        if (combo.getItemStatus() != null && combo.getItemStatus() != ItemStatus.CANCELED) {
                            combo.setWastageSourceStatus(combo.getItemStatus());
                        }
                        combo.setItemStatus(ItemStatus.CANCELED);
                        combo.setUpdatedAt(now);
                        combo.setReason("Cancelled automatically when table marked for cleanup");
                        orderedComboRepository.save(combo);
                        totalCancelledCombos++;

                        // Skip broadcast WebSocket notification for CANCELED combo —
                        // KDS notifications for order/combo cancellation are handled by user-scoped methods.

                        log.info("Cancelled active combo {} for order {} when table {} marked for cleanup", 
                                combo.getId(), order.getId(), tableId);
                    }
                }
            }

            // Flush to ensure cancellations are persisted before validation runs
            if (totalCancelledItems > 0 || totalCancelledCombos > 0) {
                orderedItemRepository.flush();
                orderedComboRepository.flush();
                log.info("Cancelled {} items and {} combos when table {} marked for cleanup", 
                        totalCancelledItems, totalCancelledCombos, tableId);
            }
        } catch (Exception e) {
            log.error("Failed to cancel active items for cleanup on table {}: {}", tableId, e.getMessage(), e);
            // Don't throw exception - allow cleanup to proceed even if cancellation fails
        }
    }

    /**
     * Sends WebSocket notification to a specific waiter.
     */
    private void sendWaiterWebSocketNotification(UUID waiterId, String message) {
        try {
            String topic = "/topic/waiter/" + waiterId;
            messagingTemplate.convertAndSend(topic, message);
            log.info("[Notification][WebSocket] broadcast topic={} waiterId={} payloadType=plain-string", topic, waiterId);
            
            // Also publish to RabbitMQ for integration service to log
            publishWaiterWsStringToRabbitBestEffort(topic, message, waiterId);
        } catch (Exception e) {
            log.error("Failed to send WebSocket notification to waiter {}: {}", waiterId, e.getMessage());
        }
    }

    private void notifyItemCanceledBestEffort(OrderedItem item, Locale locale) {
        try {
            notificationService.notifyItemCanceled(item, java.util.Collections.emptyList(), locale);
        } catch (Exception e) {
            UUID itemId = item != null ? item.getId() : null;
            log.warn("Failed to send item canceled notification for item {}: {}", itemId, e.getMessage());
        }
    }

    private void publishWaiterWsStringToRabbitBestEffort(String topic, String message, UUID waiterId) {
        if (rabbitTemplate == null) {
            return;
        }
        try {
            Map<String, Object> wsMessage = new HashMap<>();
            wsMessage.put("topic", topic);
            wsMessage.put("message", message);
            wsMessage.put("waiterId", waiterId != null ? waiterId.toString() : null);
            wsMessage.put(KEY_TIMESTAMP, LocalDateTime.now(ZoneOffset.UTC).format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
            wsMessage.put("type", "websocket_notification");

            rabbitTemplate.convertAndSend(WEBSOCKET_TOPIC_EXCHANGE, WEBSOCKET_ROUTING_KEY, wsMessage);
            log.info("[Notification][FCM] rabbitPublish exchange={} routingKey={} payloadWsTopic={} notificationType=guest-transfer-string waiterId={}",
                    WEBSOCKET_TOPIC_EXCHANGE, WEBSOCKET_ROUTING_KEY, topic, waiterId);
        } catch (Exception e) {
            log.warn("[Notification][FCM] rabbitPublish failed payloadWsTopic={}: {}", topic, e.getMessage());
        }
    }

    private void notifyTableAssignedBestEffort(RestaurantTable table, User waiter, Locale userLocale) {
        try {
            notificationService.notifyTableAssigned(table, waiter, userLocale);
        } catch (Exception e) {
            UUID waiterId = waiter != null ? waiter.getId() : null;
            log.error("Failed to send table assignment notification to waiter {}: {}", waiterId, e.getMessage(), e);
        }
    }

    private void notifyTableRemovedBestEffort(RestaurantTable table, User waiter, Locale userLocale) {
        try {
            notificationService.notifyTableRemoved(table, waiter, userLocale);
        } catch (Exception e) {
            UUID waiterId = waiter != null ? waiter.getId() : null;
            log.error("Failed to send table removal notification to waiter {}: {}", waiterId, e.getMessage(), e);
        }
    }
    
    /**
     * Helper method to publish WebSocket messages to RabbitMQ for integration service logging
     */
    private void publishToRabbitMQ(String topic, StatusEventMessage eventMessage) {
        if (rabbitTemplate != null) {
            try {
                Map<String, Object> wsMessage = new HashMap<>();
                wsMessage.put("topic", topic);
                if (eventMessage.getTitle() != null && !eventMessage.getTitle().isEmpty()) {
                    wsMessage.put("title", eventMessage.getTitle());
                }
                wsMessage.put("message", eventMessage.getMessage());
                wsMessage.put(KEY_TIMESTAMP, LocalDateTime.now(ZoneOffset.UTC).format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
                wsMessage.put("type", "websocket_notification");
                wsMessage.put(WEBSOCKET_MSG_SUPPRESS_LOCAL_FORWARD, Boolean.TRUE);

                rabbitTemplate.convertAndSend(WEBSOCKET_TOPIC_EXCHANGE, WEBSOCKET_ROUTING_KEY, wsMessage);
                log.info("[Notification][FCM] rabbitPublish exchange={} routingKey={} payloadWsTopic={} notificationType={}",
                        WEBSOCKET_TOPIC_EXCHANGE, WEBSOCKET_ROUTING_KEY, topic, eventMessage.getNotificationType());
            } catch (Exception e) {
                log.warn("[Notification][FCM] rabbitPublish failed payloadWsTopic={}: {}", topic, e.getMessage());
            }
        } else {
            log.debug("RabbitTemplate not available, skipping RabbitMQ publish payloadWsTopic={}", topic);
        }
    }

    /**
     * Sends WebSocket notifications to waiters for guest transfer.
     * 
     * Notification rules:
     * - If both tables have different waiters: both waiters receive notifications
     * - If both tables have the same waiter: the waiter receives one notification
     * - If only source table has a waiter: source waiter receives notification
     * - If only target table has a waiter: target waiter receives notification
     */
    private void sendGuestTransferNotifications(TableAssignment sourceAssignment, TableAssignment targetAssignment,
                                               RestaurantTable sourceTable, RestaurantTable targetTable) {
        // Safely extract waiter IDs with null checks
        UUID sourceWaiterId = null;
        if (sourceAssignment != null && sourceAssignment.getWaiter() != null) {
            sourceWaiterId = sourceAssignment.getWaiter().getId();
        }
        
        UUID targetWaiterId = null;
        if (targetAssignment != null && targetAssignment.getWaiter() != null) {
            targetWaiterId = targetAssignment.getWaiter().getId();
        }
        
        log.debug("Sending guest transfer notifications - Source waiter: {}, Target waiter: {}, Source table: {}, Target table: {}", 
                sourceWaiterId, targetWaiterId, sourceTable.getTableOrder(), targetTable.getTableOrder());
        
        // Case 1: Both tables have waiters
        if (sourceWaiterId != null && targetWaiterId != null) {
            if (!sourceWaiterId.equals(targetWaiterId)) {
                // Different waiters - notify both
                log.info("Notifying both waiters about guest transfer - Source waiter: {}, Target waiter: {}", sourceWaiterId, targetWaiterId);
                sendWaiterWebSocketNotification(sourceWaiterId, 
                        "Guest transferred from your table " + sourceTable.getTableOrder() + GUEST_TRANSFER_TO_TABLE + targetTable.getTableOrder());
                sendWaiterWebSocketNotification(targetWaiterId, 
                        "Guest transferred to your table " + targetTable.getTableOrder() + " from table " + sourceTable.getTableOrder());
            } else {
                // Same waiter assigned to both tables - notify once
                log.info("Notifying same waiter about guest transfer between their tables - Waiter: {}", sourceWaiterId);
                sendWaiterWebSocketNotification(sourceWaiterId, 
                        "Guest transferred from table " + sourceTable.getTableOrder() + GUEST_TRANSFER_TO_TABLE + targetTable.getTableOrder());
            }
        } 
        // Case 2: Only source table has a waiter
        else if (sourceWaiterId != null) {
            log.info("Notifying source waiter about guest transfer - Waiter: {}", sourceWaiterId);
            sendWaiterWebSocketNotification(sourceWaiterId, 
                    "Guest transferred from your table " + sourceTable.getTableOrder() + GUEST_TRANSFER_TO_TABLE + targetTable.getTableOrder());
        } 
        // Case 3: Only target table has a waiter
        else if (targetWaiterId != null) {
            log.info("Notifying target waiter about guest transfer - Waiter: {}", targetWaiterId);
            sendWaiterWebSocketNotification(targetWaiterId, 
                    "Guest transferred to your table " + targetTable.getTableOrder() + " from table " + sourceTable.getTableOrder());
        }
        // Case 4: Neither table has a waiter - no notification needed
        else {
            log.warn("No waiters assigned to source table {} or target table {} - no notifications sent", 
                    sourceTable.getTableOrder(), targetTable.getTableOrder());
        }
    }

    // ==================== SERVICE METHODS ====================

    /**
     * Assigns one waiter to one or more tables (manager-only).
     * <p>
     * For each table id, validates restaurant consistency, creates a {@link TableAssignment} when one does not already
     * exist, updates a BLOCKED table to AVAILABLE upon first successful assignment, sends waiter notifications, and
     * writes an audit trail entry summarizing the operation.
     * </p>
     *
     * @param request  assignment payload containing waiter id and list of table ids
     * @param userId   acting user id (string UUID)
     * @param userRole acting user role (must be MANAGER)
     * @return response wrapper containing successful assignment responses
     * @throws ResponseStatusException when authorization fails or required entities cannot be found/validated
     */
    @Override
    @Transactional
    public ResponseDto<TableAssignmentWrapper<List<TableAssignmentResponse>>> assignTableToWaiter(
            TableAssignmentRequest request,
            String userId,
            String userRole) {

        Locale userLocale = LocaleContextHolder.getLocale();

        validateManagerRole(userRole, userLocale);

        User creator = userRepository.findById(UUID.fromString(userId))
                .orElse(null);

        User waiter = userRepository.findById(request.getWaiterId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        messageUtil.getMessage(MSG_WAITER_NOT_FOUND, userLocale)));

        List<TableAssignmentResponse> successfulAssignments = new ArrayList<>();

        for (UUID tableId : request.getRestaurantTableId()) {
            try {
                RestaurantTable table = restaurantTableRepository.findById(tableId)
                        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                                messageUtil.getMessage(MSG_TABLE_NOT_FOUND, userLocale)));

                validateSameRestaurant(table, waiter, userLocale);

                boolean existsActiveAssignment = tableAssignmentRepository.existsByWaiterIdAndRestaurantTableIdAndUnassignedAtIsNull(
                        waiter.getId(), table.getId());

                if (existsActiveAssignment) {
                    continue;
                }

                TableAssignment assignment = new TableAssignment();
                assignment.setRestaurantTable(table);
                assignment.setWaiter(waiter);
                assignment.setAssignedAt(OffsetDateTime.now(ZoneOffset.UTC));
                assignment.setCreatedAt(OffsetDateTime.now(ZoneOffset.UTC));
                assignment.setCreatedBy(creator);

                TableAssignment saved = tableAssignmentRepository.save(assignment);

                // Update table status from BLOCKED to AVAILABLE when waiter is assigned
                if (table.getTableStatus() == TableStatus.BLOCKED) {
                    table.setTableStatus(TableStatus.AVAILABLE);
                    restaurantTableRepository.save(table);
                    
                    // Send WebSocket notification for table status update
                    UUID restaurantId = getRestaurantIdFromTable(table);
                    sendTableStatusWebSocketNotification(userLocale, restaurantId, table.getId(), TableStatus.AVAILABLE);
                }

                notifyTableAssignedBestEffort(table, waiter, userLocale);

                TableAssignmentResponse response = TableAssignmentResponse.builder()
                        .id(saved.getId())
                        .restaurantTableId(saved.getRestaurantTable().getId())
                        .waiterId(saved.getWaiter().getId())
                        .assignedAt(saved.getAssignedAt() != null ? saved.getAssignedAt().toLocalDateTime() : null)
                        .unassignedAt(saved.getUnassignedAt() != null ? saved.getUnassignedAt().toLocalDateTime() : null)
                        .build();
                successfulAssignments.add(response);

            } catch (Exception e) {
                log.error("Failed to assign table with ID {} to waiter {}: {}", tableId, waiter.getId(), e.getMessage(), e);
            }
        }

        TableAssignmentWrapper<List<TableAssignmentResponse>> wrapper = TableAssignmentWrapper.<List<TableAssignmentResponse>>builder()
                .tableAssignment(successfulAssignments)
                .build();

        // Create audit trail for table waiter assign
        if (!successfulAssignments.isEmpty()) {
            try {
                Restaurant restaurant = getRestaurantFromTable(restaurantTableRepository.findById(request.getRestaurantTableId().get(0)).orElse(null));
                String notes = String.format("Assigned waiter %s to %d table(s)", 
                        waiter.getFirstName() + " " + waiter.getLastName(), successfulAssignments.size());
                
                auditTrailService.createAuditTrail(
                        creator,
                        ActionType.TABLE_WAITER_ASSIGN,
                        restaurant,
                        RequestStatus.NA,
                        null, null,
                        waiter.getId(),
                        "USER",
                        notes
                );
            } catch (Exception e) {
                log.error("Failed to create audit trail for table waiter assign: {}", e.getMessage());
            }
        }

        String message;
        if (successfulAssignments.isEmpty()) {
            message = messageUtil.getMessage("table.assignment.failed", userLocale);
        } else {
            message = messageUtil.getMessage("table.assignment.success", userLocale);
        }

        return ResponseDto.<TableAssignmentWrapper<List<TableAssignmentResponse>>>builder()
                .message(message)
                .data(wrapper)
                .build();
    }

    /**
     * Assigns multiple waiters to a single table (manager-only).
     * <p>
     * Creates {@link TableAssignment} records for waiters not already assigned to the table, updates a BLOCKED table
     * to AVAILABLE after the first successful assignment, notifies each waiter, and records an audit trail entry.
     * </p>
     *
     * @param request  payload containing the table id and the waiter ids to assign
     * @param userId   acting user id (string UUID)
     * @param userRole acting user role (must be MANAGER)
     * @return response wrapper containing successful assignment responses
     * @throws ResponseStatusException when authorization fails or required entities cannot be found/validated
     */
    @Override
    @Transactional
    public ResponseDto<TableAssignmentWrapper<List<TableAssignmentResponse>>> assignWaitersToTable(
            WaiterTableAssignmentRequest request,
            String userId,
            String userRole) {

        Locale userLocale = LocaleContextHolder.getLocale();

        validateManagerRole(userRole, userLocale);

        User creator = userRepository.findById(UUID.fromString(userId))
                .orElse(null);

        RestaurantTable table = restaurantTableRepository.findById(request.getRestaurantTableId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        messageUtil.getMessage(MSG_TABLE_NOT_FOUND, userLocale)));

        List<TableAssignmentResponse> successfulAssignments = new ArrayList<>();
        boolean tableStatusUpdated = false;

        for (UUID waiterId : request.getWaiterIds()) {
            try {
                User waiter = userRepository.findById(waiterId)
                        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                                messageUtil.getMessage(MSG_WAITER_NOT_FOUND, userLocale)));

                validateSameRestaurant(table, waiter, userLocale);

                boolean existsActiveAssignment = tableAssignmentRepository.existsByWaiterIdAndRestaurantTableIdAndUnassignedAtIsNull(
                        waiter.getId(), table.getId());

                if (existsActiveAssignment) {
                    continue;
                }

                TableAssignment assignment = new TableAssignment();
                assignment.setRestaurantTable(table);
                assignment.setWaiter(waiter);
                assignment.setAssignedAt(OffsetDateTime.now(ZoneOffset.UTC));
                assignment.setCreatedAt(OffsetDateTime.now(ZoneOffset.UTC));
                assignment.setCreatedBy(creator);

                TableAssignment saved = tableAssignmentRepository.save(assignment);

                // Update table status from BLOCKED to AVAILABLE when at least one waiter is assigned
                if (!tableStatusUpdated && table.getTableStatus() == TableStatus.BLOCKED) {
                    table.setTableStatus(TableStatus.AVAILABLE);
                    restaurantTableRepository.save(table);
                    tableStatusUpdated = true;

                    UUID restaurantId = getRestaurantIdFromTable(table);
                    sendTableStatusWebSocketNotification(userLocale, restaurantId, table.getId(), TableStatus.AVAILABLE);
                }

                notifyTableAssignedBestEffort(table, waiter, userLocale);

                TableAssignmentResponse response = TableAssignmentResponse.builder()
                        .id(saved.getId())
                        .restaurantTableId(saved.getRestaurantTable().getId())
                        .waiterId(saved.getWaiter().getId())
                        .assignedAt(saved.getAssignedAt() != null ? saved.getAssignedAt().toLocalDateTime() : null)
                        .unassignedAt(saved.getUnassignedAt() != null ? saved.getUnassignedAt().toLocalDateTime() : null)
                        .build();
                successfulAssignments.add(response);
            } catch (Exception e) {
                log.error("Failed to assign table {} to waiter {}: {}", request.getRestaurantTableId(), waiterId, e.getMessage(), e);
            }
        }

        TableAssignmentWrapper<List<TableAssignmentResponse>> wrapper = TableAssignmentWrapper.<List<TableAssignmentResponse>>builder()
                .tableAssignment(successfulAssignments)
                .build();

        // Create audit trail for table waiter assign
        if (!successfulAssignments.isEmpty()) {
            try {
                Restaurant restaurant = getRestaurantFromTable(table);
                String notes = String.format("Assigned %d waiter(s) to table %s",
                        successfulAssignments.size(), table.getTableOrder());

                auditTrailService.createAuditTrail(
                        creator,
                        ActionType.TABLE_WAITER_ASSIGN,
                        restaurant,
                        RequestStatus.NA,
                        null, null,
                        null,
                        "USER",
                        notes
                );
            } catch (Exception e) {
                log.error("Failed to create audit trail for waiters-to-table assign: {}", e.getMessage());
            }
        }

        String message;
        if (successfulAssignments.isEmpty()) {
            message = messageUtil.getMessage("table.assignment.failed", userLocale);
        } else {
            message = messageUtil.getMessage("table.assignment.success", userLocale);
        }

        return ResponseDto.<TableAssignmentWrapper<List<TableAssignmentResponse>>>builder()
                .message(message)
                .data(wrapper)
                .build();
    }

    /**
     * Unassigns a waiter from a table by marking an assignment as unassigned (manager-only).
     * <p>
     * Validates the assignment exists and is not already unassigned, sets {@code unassignedAt/updatedAt/updatedBy},
     * optionally changes the table status from AVAILABLE to BLOCKED when no other active assignments exist,
     * sends waiter notifications, and writes an audit trail entry.
     * </p>
     *
     * @param assignmentId assignment id to unassign
     * @param userId       acting user id (string UUID)
     * @param userRole     acting user role (must be MANAGER)
     * @return response wrapper containing the updated assignment response
     * @throws ResponseStatusException when authorization fails, entities are missing, or assignment is already unassigned
     */
    @Override
    @Transactional
    public ResponseDto<TableAssignmentWrapper<TableAssignmentResponse>> unassignTableFromWaiter(
            UUID assignmentId,
            String userId,
            String userRole) {

        Locale userLocale = LocaleContextHolder.getLocale();

        validateManagerRole(userRole, userLocale);

        TableAssignment assignment = tableAssignmentRepository.findById(assignmentId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        messageUtil.getMessage("assignment.not.found", userLocale)));

        if (assignment.getUnassignedAt() != null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    messageUtil.getMessage("assignment.already.unassigned", userLocale));
        }

        User updater = userRepository.findById(UUID.fromString(userId))
                .orElse(null);     

assignment.setUnassignedAt(OffsetDateTime.now(ZoneOffset.UTC));
                assignment.setUpdatedAt(OffsetDateTime.now(ZoneOffset.UTC));
        assignment.setUpdatedBy(updater);

        TableAssignment saved = tableAssignmentRepository.save(assignment);

        // Validate that assignment has required entities
        RestaurantTable table = saved.getRestaurantTable();
        if (table == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    messageUtil.getMessage(MSG_TABLE_NOT_FOUND, userLocale));
        }

        User waiter = saved.getWaiter();
        if (waiter == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    messageUtil.getMessage(MSG_WAITER_NOT_FOUND, userLocale));
        }

        // Load table with all relationships to avoid LazyInitializationException
        RestaurantTable loadedTable = restaurantTableRepository.findByIdWithRelationships(table.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        messageUtil.getMessage(MSG_TABLE_NOT_FOUND, userLocale)));

        // Update table status to BLOCKED when waiter is unassigned (if no other active assignments exist)
        List<TableAssignment> otherActiveAssignments = tableAssignmentRepository
                .findByRestaurantTableIdAndUnassignedAtIsNull(loadedTable.getId());
        
        // If no other active assignment exists and table is AVAILABLE, set it to BLOCKED
        if (otherActiveAssignments.isEmpty() && loadedTable.getTableStatus() == TableStatus.AVAILABLE) {
            loadedTable.setTableStatus(TableStatus.BLOCKED);
            restaurantTableRepository.save(loadedTable);
            
            // Send WebSocket notification for table status update
            UUID restaurantId = getRestaurantIdFromTable(loadedTable);
            sendTableStatusWebSocketNotification(userLocale, restaurantId, loadedTable.getId(), TableStatus.BLOCKED);
        }

        notifyTableRemovedBestEffort(loadedTable, waiter, userLocale);

        TableAssignmentResponse response = TableAssignmentResponse.builder()
                .id(saved.getId())
                .restaurantTableId(loadedTable.getId())
                .waiterId(waiter.getId())
                .assignedAt(saved.getAssignedAt() != null ? saved.getAssignedAt().toLocalDateTime() : null)
                .unassignedAt(saved.getUnassignedAt() != null ? saved.getUnassignedAt().toLocalDateTime() : null)
                .build();

        TableAssignmentWrapper<TableAssignmentResponse> wrapper = TableAssignmentWrapper.<TableAssignmentResponse>builder()
                .tableAssignment(response)
                .build();

        // Create audit trail for table waiter unassign
        try {
            Restaurant restaurant = getRestaurantFromTable(loadedTable);
            String notes = String.format("Unassigned waiter %s from table %s", 
                    waiter.getFirstName() + " " + waiter.getLastName(), 
                    loadedTable.getTableOrder());
            
            auditTrailService.createAuditTrail(
                    updater,
                    ActionType.TABLE_WAITER_UNASSIGN,
                    restaurant,
                    RequestStatus.NA,
                    null, null,
                    waiter.getId(),
                    "USER",
                    notes
            );
        } catch (Exception e) {
            log.error("Failed to create audit trail for table waiter unassign: {}", e.getMessage());
        }

        return ResponseDto.<TableAssignmentWrapper<TableAssignmentResponse>>builder()
                .message(messageUtil.getMessage("table.unassignment.success", userLocale))
                .data(wrapper)
                .build();
    }

    private void validateManagerRole(String userRole, Locale locale) {
        if (userRole == null || !userRole.equalsIgnoreCase(ROLE_MANAGER)) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    messageUtil.getMessage("user.role.manager.required", locale)
            );
        }
    }

    /**
     * Ensures the waiter and table belong to the same restaurant.
     *
     * @param table  table entity whose restaurant is resolved via layout relationships
     * @param waiter waiter user whose {@code restaurantId} must match the table restaurant id
     * @param locale locale used for localized error messages
     * @throws ResponseStatusException when the restaurant cannot be resolved or ids do not match
     */
    private void validateSameRestaurant(RestaurantTable table, User waiter, Locale locale) {
                UUID waiterRestaurantId = waiter.getRestaurantId();

        if (table.getRestaurantRow() == null ||
            table.getRestaurantRow().getRestaurantSection() == null ||
            table.getRestaurantRow().getRestaurantSection().getRestaurantLayout() == null ||
            table.getRestaurantRow().getRestaurantSection().getRestaurantLayout().getRestaurant() == null) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    messageUtil.getMessage("table.assignment.error.cannot.resolve.restaurant", locale));
        }

        UUID tableRestaurantId = table.getRestaurantRow()
                                    .getRestaurantSection()
                                    .getRestaurantLayout()
                                    .getRestaurant()
                                    .getId();

        if (tableRestaurantId == null || waiterRestaurantId == null || !tableRestaurantId.equals(waiterRestaurantId)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    messageUtil.getMessage("table.assignment.error.mismatched.restaurant", locale)
            );
        }
    }

        
        /**
         * Retrieves tables using waiter, status, section, restaurant, and search filters.
         * <p>
         * Supports comma-separated table statuses and order statuses, optional paging, and clears the persistence
         * context up-front to avoid returning stale section relationships after table moves.
         * When {@code waiterId} is provided, results are scoped to active assignments; otherwise returns all tables.
         * </p>
         *
         * @param waiterId     optional waiter id filter
         * @param search       optional search term (mapped to a LIKE pattern)
         * @param status       optional status filter (comma-separated; may include table and/or order statuses)
         * @param sectionId    optional section id filter
         * @param restaurantId optional restaurant id filter
         * @param page         1-based page number (optional)
         * @param size         page size (optional)
         * @return response wrapper containing the filtered table list
         * @throws ResponseStatusException when any filter value is invalid
         */
        @Override
        @Transactional(readOnly = true)
        public ResponseDto<TableListResponseDto> getTablesByFilters(
                String waiterId,
                String search,
                String status,      
                String sectionId,
                String restaurantId,
                Integer page,
                Integer size) {

        log.info("TableServiceImpl.getTablesByFilters called with waiterId={}, page={}, size={}", waiterId, page, size);
        Locale userLocale = LocaleContextHolder.getLocale();
        
        // Clear persistence context cache to ensure fresh data from database
        // This is critical when filtering by sectionId, as tables may have been moved
        // to new sections and cached entities might have stale section relationships
        entityManager.clear();

        // Support both single and multiple statuses (comma-separated)
        // Parse status to separate TableStatus and OrderStatus
        Set<TableStatus> tableStatuses = null;
        Set<OrderStatus> orderStatuses = null;
        if (status != null && !status.isBlank()) {
                Set<String> allStatusValues = Arrays.stream(status.split(","))
                        .map(String::trim)
                        .filter(s -> !s.isBlank())
                        .map(String::toUpperCase)
                        .collect(Collectors.toSet());
                
                Set<TableStatus> parsedTableStatuses = new java.util.HashSet<>();
                Set<OrderStatus> parsedOrderStatuses = new java.util.HashSet<>();
                
                for (String statusValue : allStatusValues) {
                        try {
                                TableStatus tableStatus = TableStatus.valueOf(statusValue);
                                parsedTableStatuses.add(tableStatus);
                        } catch (IllegalArgumentException e1) {
                                try {
                                        OrderStatus orderStatus = OrderStatus.valueOf(statusValue);
                                        parsedOrderStatuses.add(orderStatus);
                                } catch (IllegalArgumentException e2) {
                                        String errorMessage = messageUtil.getMessage("error.invalid.tableStatus", userLocale, status);
                                        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, errorMessage);
                                }
                        }
                }
                
                tableStatuses = parsedTableStatuses.isEmpty() ? null : parsedTableStatuses;
                orderStatuses = parsedOrderStatuses.isEmpty() ? null : parsedOrderStatuses;
        }

        UUID sectionUUID = null;
        if (sectionId != null && !sectionId.isBlank()) {
                try {
                sectionUUID = UUID.fromString(sectionId);
                } catch (IllegalArgumentException e) {
                String errorMessage = messageUtil.getMessage("error.invalid.sectionId", userLocale, sectionId);
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, errorMessage);
                }
        }

        UUID restaurantUUID = null;
        if (restaurantId != null && !restaurantId.isBlank()) {
                try {
                restaurantUUID = UUID.fromString(restaurantId);
                } catch (IllegalArgumentException e) {
                String errorMessage = messageUtil.getMessage("error.invalid.restaurantId", userLocale, restaurantId);
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, errorMessage);
                }
        }

        waiterId = (waiterId != null && !waiterId.isBlank()) ? waiterId : null;
        search = (search != null && !search.isBlank()) ? search.trim() : null;
        String searchPattern = (search != null) ? "%" + search + "%" : null;
        sectionId = (sectionId != null && !sectionId.isBlank()) ? sectionId : null;

        // When waiterId is null, query RestaurantTable directly to get ALL tables for the restaurant
        // When waiterId is provided, query TableAssignment to get only assigned tables
        if (waiterId != null) {
            // Query by waiter assignment — same layout order as table/v2: section → row → table (nulls last)
            Sort sort = Sort.by(
                    Sort.Order.asc("restaurantTable.restaurantRow.restaurantSection.sectionOrder")
                            .with(Sort.NullHandling.NULLS_LAST),
                    Sort.Order.asc("restaurantTable.restaurantRow.rowOrder")
                            .with(Sort.NullHandling.NULLS_LAST),
                    Sort.Order.asc("restaurantTable.tableOrder")
                            .with(Sort.NullHandling.NULLS_LAST));
            boolean shouldPage = (page != null && size != null && page > 0 && size > 0);
            Pageable pageable = shouldPage
                    ? PageRequest.of(page - 1, size, sort)
                    : Pageable.unpaged();

            List<TableAssignment> assignments;
            Page<TableAssignment> tableAssignments;

            if (shouldPage) {
                // Use different methods based on whether search is provided to avoid null handling issues
                if (searchPattern != null) {
                    tableAssignments = tableAssignmentRepository.findByWaiterIdAndFilters(
                            UUID.fromString(waiterId),
                            tableStatuses,
                            sectionUUID,
                            restaurantUUID,
                            searchPattern,
                            pageable);
                } else {
                    tableAssignments = tableAssignmentRepository.findByWaiterIdAndFiltersNoSearch(
                            UUID.fromString(waiterId),
                            tableStatuses,
                            sectionUUID,
                            restaurantUUID,
                            pageable);
                }
            } else {
                // Use different methods based on whether search is provided to avoid null handling issues
                if (searchPattern != null) {
                    assignments = tableAssignmentRepository.findByWaiterIdAndFilters(
                            UUID.fromString(waiterId),
                            tableStatuses,
                            sectionUUID,
                            restaurantUUID,
                            searchPattern,
                            sort);
                } else {
                    assignments = tableAssignmentRepository.findByWaiterIdAndFiltersNoSearch(
                            UUID.fromString(waiterId),
                            tableStatuses,
                            sectionUUID,
                            restaurantUUID,
                            sort);
                }
                tableAssignments = new PageImpl<>(assignments);
            }
            
            // Filter by order status if specified
            if (orderStatuses != null && !orderStatuses.isEmpty()) {
                final Set<OrderStatus> finalOrderStatuses = orderStatuses;
                // Batch fetch order statuses for all tables to avoid N+1 queries
                List<UUID> tableIdsForOrderStatus = tableAssignments.getContent().stream()
                        .map(ta -> ta.getRestaurantTable().getId())
                        .distinct()
                        .collect(Collectors.toList());
                
                Map<UUID, OrderStatus> orderStatusByTableId = getLatestOrderStatusesForTables(tableIdsForOrderStatus);
                
                List<TableAssignment> filteredAssignments = tableAssignments.getContent().stream()
                        .filter(ta -> {
                            UUID tableId = ta.getRestaurantTable().getId();
                            OrderStatus latestOrderStatus = orderStatusByTableId.get(tableId);
                            return latestOrderStatus != null && finalOrderStatuses.contains(latestOrderStatus);
                        })
                        .collect(Collectors.toList());
                
                // Recreate Page with filtered content
                tableAssignments = new PageImpl<>(filteredAssignments, 
                        tableAssignments.getPageable(), 
                        filteredAssignments.size());
            }

            List<TableAssignment> waiterAssignmentList = new ArrayList<>(tableAssignments.getContent());
            waiterAssignmentList.sort(TableServiceImpl::compareWaiterAssignmentsByLayoutOrder);

            log.info("TableServiceImpl: Processing {} table assignments for waiter", waiterAssignmentList.size());
            // Batch load all waiters for all tables to avoid N+1 queries
            List<UUID> tableIds = waiterAssignmentList.stream()
                    .map(ta -> ta.getRestaurantTable().getId())
                    .distinct()
                    .collect(Collectors.toList());
            
            Map<UUID, List<WaiterInfo>> waitersByTableId = new HashMap<>();
            if (!tableIds.isEmpty()) {
                List<TableAssignment> allActiveAssignments = tableAssignmentRepository
                        .findByRestaurantTableIdInAndUnassignedAtIsNullWithWaiter(tableIds);
                
                waitersByTableId = allActiveAssignments.stream()
                        .collect(Collectors.groupingBy(
                                ta -> ta.getRestaurantTable().getId(),
                                Collectors.mapping(
                                        ta -> {
                                            User waiter = ta.getWaiter();
                                            return waiter != null ? WaiterInfo.builder()
                                                    .id(waiter.getId())
                                                    .userCode(waiter.getUserCode())
                                                    .firstName(waiter.getFirstName())
                                                    .lastName(waiter.getLastName())
                                                    .tableAssignmentId(ta.getId())
                                                    .build() : null;
                                        },
                                        Collectors.filtering(java.util.Objects::nonNull, Collectors.toList())
                                )
                        ));
            }

            final Map<UUID, List<WaiterInfo>> finalWaitersByTableId = waitersByTableId;
            List<TableListResponse> responses = waiterAssignmentList.stream()
                    .map(ta -> mapToTableListResponse(ta, userLocale, finalWaitersByTableId))
                    .collect(Collectors.toList());
            log.info("TableServiceImpl: Mapped {} table responses", responses.size());

            TableListResponseDto dto = TableListResponseDto.builder()
                    .tables(responses)
                    .count((long) responses.size())
                    .total(tableAssignments.getTotalElements())
                    .metaData(shouldPage ? PaginationMetaData.builder()
                            .page(page)
                            .size(size)
                            .totalPages(tableAssignments.getTotalPages())
                            .totalRecords(tableAssignments.getTotalElements())
                            .build() : null)
                    .build();

            return ResponseDto.<TableListResponseDto>builder()
                    .message(messageUtil.getMessage(MSG_TABLE_LIST_FETCH_SUCCESS, userLocale))
                    .data(dto)
                    .build();
        } else {
            // Query RestaurantTable directly to get ALL tables for the restaurant
            Sort sort = Sort.by(Sort.Direction.ASC, "tableOrder");
            boolean shouldPage = (page != null && size != null && page > 0 && size > 0);
            
            // When adding blank rows, we need ALL tables (not paginated) to determine which rows are blank
            // So fetch all tables first, add blank rows, then apply pagination
            boolean needsBlankRows = restaurantUUID != null;
            
            List<RestaurantTable> allTables;
            if (search != null) {
                allTables = restaurantTableRepository.findByFiltersWithSearch(
                        tableStatuses,
                        sectionUUID,
                        restaurantUUID,
                        searchPattern,
                        sort);
            } else {
                allTables = restaurantTableRepository.findByFilters(
                        tableStatuses,
                        sectionUUID,
                        restaurantUUID,
                        sort);
            }
            
            // Filter by order status if specified
            if (orderStatuses != null && !orderStatuses.isEmpty()) {
                final Set<OrderStatus> finalOrderStatuses = orderStatuses;
                // Batch fetch order statuses for all tables to avoid N+1 queries
                List<UUID> tableIdsForOrderStatus = allTables.stream()
                        .map(RestaurantTable::getId)
                        .distinct()
                        .collect(Collectors.toList());
                
                Map<UUID, OrderStatus> orderStatusByTableId = getLatestOrderStatusesForTables(tableIdsForOrderStatus);
                
                allTables = allTables.stream()
                        .filter(table -> {
                            OrderStatus latestOrderStatus = orderStatusByTableId.get(table.getId());
                            return latestOrderStatus != null && finalOrderStatuses.contains(latestOrderStatus);
                        })
                        .collect(Collectors.toList());
            }

            log.info("TableServiceImpl: Processing {} tables (all tables for restaurant)", allTables.size());
            
            // Batch load all waiters for all tables to avoid N+1 queries
            List<UUID> tableIds = allTables.stream()
                    .map(RestaurantTable::getId)
                    .distinct()
                    .collect(Collectors.toList());
            
            Map<UUID, List<WaiterInfo>> waitersByTableId = new HashMap<>();
            if (!tableIds.isEmpty()) {
                List<TableAssignment> allActiveAssignments = tableAssignmentRepository
                        .findByRestaurantTableIdInAndUnassignedAtIsNullWithWaiter(tableIds);
                
                waitersByTableId = allActiveAssignments.stream()
                        .collect(Collectors.groupingBy(
                                ta -> ta.getRestaurantTable().getId(),
                                Collectors.mapping(
                                        ta -> {
                                            User waiter = ta.getWaiter();
                                            return waiter != null ? WaiterInfo.builder()
                                                    .id(waiter.getId())
                                                    .userCode(waiter.getUserCode())
                                                    .firstName(waiter.getFirstName())
                                                    .lastName(waiter.getLastName())
                                                    .tableAssignmentId(ta.getId())
                                                    .build() : null;
                                        },
                                        Collectors.filtering(java.util.Objects::nonNull, Collectors.toList())
                                )
                        ));
            }

            final Map<UUID, List<WaiterInfo>> finalWaitersByTableId = waitersByTableId;
            List<TableListResponse> allResponses = allTables.stream()
                    .map(table -> mapToTableListResponse(table, userLocale, finalWaitersByTableId))
                    .collect(Collectors.toList());
            log.info("TableServiceImpl: Mapped {} table responses", allResponses.size());

            // Add blank rows for rows that have no matching tables (BEFORE pagination)
            if (needsBlankRows) {
                List<TableListResponse> blankRows = getBlankRowsForRestaurant(
                        restaurantUUID, sectionUUID, allResponses, userLocale);
                allResponses.addAll(blankRows);
                
                // Sort responses by section, rowOrder, and tableOrder
                allResponses.sort((r1, r2) -> {
                    // First sort by sectionId
                    int sectionCompare = r1.getSectionId().compareTo(r2.getSectionId());
                    if (sectionCompare != 0) return sectionCompare;
                    
                    // Then by rowOrder
                    int rowOrderCompare = Integer.compare(
                            r1.getRowOrder() != null ? r1.getRowOrder() : 0,
                            r2.getRowOrder() != null ? r2.getRowOrder() : 0);
                    if (rowOrderCompare != 0) return rowOrderCompare;
                    
                    // Finally by tableOrder (nulls last for blank rows)
                    if (r1.getTableOrder() == null && r2.getTableOrder() == null) return 0;
                    if (r1.getTableOrder() == null) return 1;
                    if (r2.getTableOrder() == null) return -1;
                    return Integer.compare(r1.getTableOrder(), r2.getTableOrder());
                });
            }

            // Calculate total including blank rows
            long totalWithBlankRows = allResponses.size();
            
            // Apply pagination AFTER adding blank rows
            List<TableListResponse> responses = shouldPage
                    ? safeSubList(allResponses, page, size)
                    : allResponses;

            // Calculate pagination metadata
            int totalPages = shouldPage ? (int) Math.ceil((double) totalWithBlankRows / size) : 1;

            TableListResponseDto dto = TableListResponseDto.builder()
                    .tables(responses)
                    .count((long) responses.size())
                    .total(totalWithBlankRows)
                    .metaData(shouldPage ? PaginationMetaData.builder()
                            .page(page)
                            .size(size)
                            .totalPages(totalPages)
                            .totalRecords(totalWithBlankRows)
                            .build() : null)
                    .build();

            return ResponseDto.<TableListResponseDto>builder()
                    .message(messageUtil.getMessage(MSG_TABLE_LIST_FETCH_SUCCESS, userLocale))
                    .data(dto)
                    .build();
        }
        }

        private static <T> List<T> safeSubList(List<T> list, Integer page, Integer size) {
            if (list == null || list.isEmpty() || page == null || size == null || page < 1 || size < 1) {
                return list != null ? list : new ArrayList<>();
            }
            int fromIndex = (page - 1) * size;
            int toIndex = Math.min(fromIndex + size, list.size());
            if (fromIndex >= list.size()) {
                return new ArrayList<>();
            }
            return list.subList(fromIndex, toIndex);
        }

        /** Same ordering as {@code GET /table/v2}: section order, then row order, then table order; nulls last. */
        private static int compareWaiterAssignmentsByLayoutOrder(TableAssignment a, TableAssignment b) {
            int c = compareNullableIntAscNullsLast(sectionOrderOfAssignment(a), sectionOrderOfAssignment(b));
            if (c != 0) {
                return c;
            }
            c = compareNullableIntAscNullsLast(rowOrderOfAssignment(a), rowOrderOfAssignment(b));
            if (c != 0) {
                return c;
            }
            return compareNullableIntAscNullsLast(tableOrderOfAssignment(a), tableOrderOfAssignment(b));
        }

        private static Integer sectionOrderOfAssignment(TableAssignment ta) {
            if (ta == null || ta.getRestaurantTable() == null) {
                return null;
            }
            RestaurantRow row = ta.getRestaurantTable().getRestaurantRow();
            if (row == null || row.getRestaurantSection() == null) {
                return null;
            }
            return row.getRestaurantSection().getSectionOrder();
        }

        private static Integer rowOrderOfAssignment(TableAssignment ta) {
            if (ta == null || ta.getRestaurantTable() == null) {
                return null;
            }
            RestaurantRow row = ta.getRestaurantTable().getRestaurantRow();
            return row != null ? row.getRowOrder() : null;
        }

        private static Integer tableOrderOfAssignment(TableAssignment ta) {
            if (ta == null || ta.getRestaurantTable() == null) {
                return null;
            }
            return ta.getRestaurantTable().getTableOrder();
        }

        /**
         * Compares two nullable integers in ascending order, treating {@code null} as greater (NULLS LAST).
         */
        private static int compareNullableIntAscNullsLast(Integer a, Integer b) {
            if (a == null && b == null) {
                return 0;
            }
            if (a == null) {
                return 1;
            }
            if (b == null) {
                return -1;
            }
            return Integer.compare(a, b);
        }

        /**
         * Maps a {@link TableAssignment} (table + waiter link) into the API list response structure.
         * <p>
         * Resolves section name with translation fallback, derives live order/session info for the table,
         * and attaches waiter information (potentially pre-fetched and grouped by table id).
         * </p>
         *
         * @param assignment       assignment containing the table and one waiter
         * @param locale           locale used for translation selection
         * @param waitersByTableId optional pre-grouped waiter info by table id to avoid repeated queries
         * @return mapped table list response
         */
        private TableListResponse mapToTableListResponse(TableAssignment assignment, Locale locale, Map<UUID, List<WaiterInfo>> waitersByTableId) {
        RestaurantTable table = assignment.getRestaurantTable();
        log.info("TableServiceImpl.mapToTableListResponse called for tableId={}, tableOrder={}", 
                table.getId(), table.getTableOrder());
        RestaurantRow row = table.getRestaurantRow();
        RestaurantSection section = row.getRestaurantSection();

        String sectionName = getSectionName(section, locale);

        // Determine live order status from latest order across active sessions
        List<Session> activeSessions = sessionRepository.findByTableIdAndExpiredAtIsNull(table.getId());
        String orderStatus = null; // null means no order exists (correct business logic)
        LocalDateTime occupiedAt = null;
        List<TableSessionInfo> sessions = new ArrayList<>();
        log.info("TableServiceImpl: Getting counts for tableId={}, activeSessions count={}", table.getId(), activeSessions.size());
        
        if (!activeSessions.isEmpty()) {
            occupiedAt = resolveOccupiedAt(activeSessions);
            
            List<UUID> sessionIds = toSessionIds(activeSessions);
            log.info("TableServiceImpl: Active session IDs for tableId={}: {}", table.getId(), sessionIds);
            
            // First, let's check what orders and ordered items actually exist for debugging
            List<Order> debugOrders = orderRepository.findBySessionIdsWithOrderedItems(sessionIds);
            log.info(LOG_FOUND_ORDERS, 
                    debugOrders.size(), table.getId(), sessionIds);
            
            // Log detailed information about all ordered items
            for (Order order : debugOrders) {
                log.info("TableServiceImpl: Order {} (orderNumber={}) has {} ordered items", 
                        order.getId(), order.getOrderNumber(), 
                        order.getOrderedItems() != null ? order.getOrderedItems().size() : 0);
                if (order.getOrderedItems() != null && !order.getOrderedItems().isEmpty()) {
                    for (com.gulfnet.shared_library.entity.OrderedItem item : order.getOrderedItems()) {
                        log.info("TableServiceImpl:   - OrderedItem id={}, quantity={}, item_status={} (raw value: {})", 
                                item.getId(), 
                                item.getQuantity(),
                                item.getItemStatus() != null ? item.getItemStatus().name() : "NULL",
                                item.getItemStatus() != null ? item.getItemStatus().toString() : "NULL");
                    }
                } else {
                    log.warn("TableServiceImpl: Order {} has no ordered items!", order.getId());
                }
            }
            
            // Batch fetch all orders with ordered items for all active sessions in a single query
            List<Order> allOrders = orderRepository.findBySessionIdsWithOrderedItems(sessionIds);
            log.debug(LOG_FOUND_ORDERS,
                    allOrders.size(), table.getId(), sessionIds);
            
            // Log ordered items details for debugging
            for (Order order : allOrders) {
                if (order.getOrderedItems() != null && !order.getOrderedItems().isEmpty()) {
                    log.debug("TableServiceImpl: Order {} has {} ordered items. Item statuses: {}", 
                            order.getId(), 
                            order.getOrderedItems().size(),
                            order.getOrderedItems().stream()
                                    .map(oi -> oi.getItemStatus() != null ? oi.getItemStatus().name() : "NULL")
                                    .collect(Collectors.toList()));
                }
            }
            
            // Group orders by session ID - need to fetch session to get ID
            // Create a map of sessionId to orders
            java.util.Map<UUID, List<Order>> ordersBySession = new java.util.HashMap<>();
            for (Order order : allOrders) {
                UUID sessionId = order.getSession().getId();
                ordersBySession.computeIfAbsent(sessionId, k -> new ArrayList<>()).add(order);
            }
            
            // Build session info list - get latest order for each session
            for (Session session : activeSessions) {
                List<Order> sessionOrders = ordersBySession.get(session.getId());
                OffsetDateTime sessionEndTime = resolveSessionEndTime(sessionOrders);
                if (sessionOrders != null && !sessionOrders.isEmpty()) {
                    // Sort by createdAt DESC to get the latest order
                    sessionOrders.sort((o1, o2) -> {
                        if (o1.getCreatedAt() == null && o2.getCreatedAt() == null) return 0;
                        if (o1.getCreatedAt() == null) return 1;
                        if (o2.getCreatedAt() == null) return -1;
                        return o2.getCreatedAt().compareTo(o1.getCreatedAt());
                    });
                    Order latestOrder = sessionOrders.get(0);
                    
                    // Calculate subtotal (use subTotal from order)
                    BigDecimal orderSubtotal = latestOrder.getSubTotal();
                    
                    // Calculate readyItems and pendingItems for this order using repository queries
                    Long readyItemsCount = orderedItemRepository.countReadyItemsByOrderId(latestOrder.getId());
                    Long pendingItemsCount = orderedItemRepository.countPendingItemsByOrderId(latestOrder.getId());
                    Long readyCombosCount = orderedComboRepository.countReadyCombosByOrderId(latestOrder.getId());
                    Long pendingCombosCount = orderedComboRepository.countPendingCombosByOrderId(latestOrder.getId());
                    
                    int sessionReadyItems = (readyItemsCount != null ? readyItemsCount.intValue() : 0) + 
                                           (readyCombosCount != null ? readyCombosCount.intValue() : 0);
                    int sessionPendingItems = (pendingItemsCount != null ? pendingItemsCount.intValue() : 0) + 
                                             (pendingCombosCount != null ? pendingCombosCount.intValue() : 0);
                    
                    TableSessionInfo sessionInfo = TableSessionInfo.builder()
                            .sessionId(session.getId())
                            .sequenceNo(session.getSequenceNo())
                            .orderId(latestOrder.getId())
                            .orderNumber(latestOrder.getOrderNumber())
                            .orderStatus(latestOrder.getOrderStatus() != null ? latestOrder.getOrderStatus().name() : null)
                            .readyItems(sessionReadyItems)
                            .pendingItems(sessionPendingItems)
                            .orderSubtotal(orderSubtotal)
                            .startTime(session.getIssuedAt())
                            .endTime(sessionEndTime)
                            .build();
                    sessions.add(sessionInfo);
                } else {
                    // Session exists but no orders yet
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
            
            // Find the latest order across all sessions for orderStatus
            Order latestOrder = null;
            for (List<Order> sessionOrders : ordersBySession.values()) {
                if (sessionOrders != null && !sessionOrders.isEmpty()) {
                    // Sort by createdAt DESC to get the latest order
                    sessionOrders.sort((o1, o2) -> {
                        if (o1.getCreatedAt() == null && o2.getCreatedAt() == null) return 0;
                        if (o1.getCreatedAt() == null) return 1;
                        if (o2.getCreatedAt() == null) return -1;
                        return o2.getCreatedAt().compareTo(o1.getCreatedAt());
                    });
                    Order candidate = sessionOrders.get(0);
                    if (latestOrder == null || (candidate.getCreatedAt() != null && latestOrder.getCreatedAt() != null && candidate.getCreatedAt().isAfter(latestOrder.getCreatedAt()))) {
                        latestOrder = candidate;
                    }
                }
            }
            
            if (latestOrder != null && latestOrder.getOrderStatus() != null) {
                orderStatus = latestOrder.getOrderStatus().name();
            }
        }

        // Get waiters from pre-loaded map (batched to avoid N+1 queries)
        List<WaiterInfo> waiters = waitersByTableId.getOrDefault(table.getId(), new ArrayList<>());

        return TableListResponse.builder()
                .id(table.getId().toString())
                .tableCode(table.getTableCode())
                .tableOrder(table.getTableOrder())
                .rowOrder(row.getRowOrder())
                .rowId(row.getId().toString())
                .sectionId(section.getId().toString())
                .sectionName(sectionName)
                .capacity(table.getCapacity())
                .tableStatus(table.getTableStatus().name())
                .blockReason(table.getBlockReason())
                .orderStatus(orderStatus)
                .occupiedAt(occupiedAt)
                .sessions(sessions)
                .waiters(waiters)
                .build();
        }

        /**
         * Overloaded method to map RestaurantTable directly to TableListResponse.
         * Used when querying all tables for a restaurant (not filtered by waiter assignment).
         */
        private TableListResponse mapToTableListResponse(RestaurantTable table, Locale locale, Map<UUID, List<WaiterInfo>> waitersByTableId) {
            log.info("TableServiceImpl.mapToTableListResponse called for tableId={}, tableOrder={}", 
                    table.getId(), table.getTableOrder());
            RestaurantRow row = table.getRestaurantRow();
            RestaurantSection section = row.getRestaurantSection();

            String sectionName = getSectionName(section, locale);

            // Determine live order status from latest order across active sessions
            List<Session> activeSessions = sessionRepository.findByTableIdAndExpiredAtIsNull(table.getId());
            String orderStatus = null; // null means no order exists (correct business logic)
            LocalDateTime occupiedAt = null;
            List<TableSessionInfo> sessions = new ArrayList<>();
            
            log.info("TableServiceImpl: Getting counts for tableId={}, activeSessions count={}", table.getId(), activeSessions.size());
            
            if (!activeSessions.isEmpty()) {
                occupiedAt = resolveOccupiedAt(activeSessions);
                
                List<UUID> sessionIds = toSessionIds(activeSessions);
                log.info("TableServiceImpl: Active session IDs for tableId={}: {}", table.getId(), sessionIds);
                
                // Batch fetch all orders with ordered items for all active sessions in a single query
                List<Order> allOrders = orderRepository.findBySessionIdsWithOrderedItems(sessionIds);
                log.debug(LOG_FOUND_ORDERS,
                        allOrders.size(), table.getId(), sessionIds);
                
                // Group orders by session ID
                java.util.Map<UUID, List<Order>> ordersBySession = new java.util.HashMap<>();
                for (Order order : allOrders) {
                    UUID sessionId = order.getSession().getId();
                    ordersBySession.computeIfAbsent(sessionId, k -> new ArrayList<>()).add(order);
                }
                
                // Build session info list - get latest order for each session
                for (Session session : activeSessions) {
                    List<Order> sessionOrders = ordersBySession.get(session.getId());
                    OffsetDateTime sessionEndTime = resolveSessionEndTime(sessionOrders);
                    if (sessionOrders != null && !sessionOrders.isEmpty()) {
                        // Sort by createdAt DESC to get the latest order
                        sessionOrders.sort((o1, o2) -> {
                            if (o1.getCreatedAt() == null && o2.getCreatedAt() == null) return 0;
                            if (o1.getCreatedAt() == null) return 1;
                            if (o2.getCreatedAt() == null) return -1;
                            return o2.getCreatedAt().compareTo(o1.getCreatedAt());
                        });
                        Order latestOrder = sessionOrders.get(0);
                        
                        // Calculate subtotal (use subTotal from order)
                        BigDecimal orderSubtotal = latestOrder.getSubTotal();
                        
                        // Calculate readyItems and pendingItems for this order using repository queries
                        Long readyItemsCount = orderedItemRepository.countReadyItemsByOrderId(latestOrder.getId());
                        Long pendingItemsCount = orderedItemRepository.countPendingItemsByOrderId(latestOrder.getId());
                        Long readyCombosCount = orderedComboRepository.countReadyCombosByOrderId(latestOrder.getId());
                        Long pendingCombosCount = orderedComboRepository.countPendingCombosByOrderId(latestOrder.getId());
                        
                        int sessionReadyItems = (readyItemsCount != null ? readyItemsCount.intValue() : 0) + 
                                               (readyCombosCount != null ? readyCombosCount.intValue() : 0);
                        int sessionPendingItems = (pendingItemsCount != null ? pendingItemsCount.intValue() : 0) + 
                                                 (pendingCombosCount != null ? pendingCombosCount.intValue() : 0);
                        
                        TableSessionInfo sessionInfo = TableSessionInfo.builder()
                                .sessionId(session.getId())
                                .sequenceNo(session.getSequenceNo())
                                .orderId(latestOrder.getId())
                                .orderNumber(latestOrder.getOrderNumber())
                                .orderStatus(latestOrder.getOrderStatus() != null ? latestOrder.getOrderStatus().name() : null)
                                .readyItems(sessionReadyItems)
                                .pendingItems(sessionPendingItems)
                                .orderSubtotal(orderSubtotal)
                                .startTime(session.getIssuedAt())
                                .endTime(sessionEndTime)
                                .build();
                        sessions.add(sessionInfo);
                    } else {
                        // Session exists but no orders yet
                        TableSessionInfo sessionInfo = TableSessionInfo.builder()
                                .sessionId(session.getId())
                                .sequenceNo(session.getSequenceNo())
                                .orderId(null)
                                .orderNumber(null)
                                .orderStatus(null)
                                .readyItems(0)
                                .pendingItems(0)
                                .orderSubtotal(null)
                                .startTime(session.getIssuedAt())
                                .endTime(sessionEndTime)
                                .build();
                        sessions.add(sessionInfo);
                    }
                }
                
                // Find the latest order across all sessions for orderStatus
                Order latestOrder = null;
                for (List<Order> sessionOrders : ordersBySession.values()) {
                    if (sessionOrders != null && !sessionOrders.isEmpty()) {
                        // Sort by createdAt DESC to get the latest order
                        sessionOrders.sort((o1, o2) -> {
                            if (o1.getCreatedAt() == null && o2.getCreatedAt() == null) return 0;
                            if (o1.getCreatedAt() == null) return 1;
                            if (o2.getCreatedAt() == null) return -1;
                            return o2.getCreatedAt().compareTo(o1.getCreatedAt());
                        });
                        Order candidate = sessionOrders.get(0);
                        if (latestOrder == null || (candidate.getCreatedAt() != null && latestOrder.getCreatedAt() != null && candidate.getCreatedAt().isAfter(latestOrder.getCreatedAt()))) {
                            latestOrder = candidate;
                        }
                    }
                }
                
                if (latestOrder != null && latestOrder.getOrderStatus() != null) {
                    orderStatus = latestOrder.getOrderStatus().name();
                }
            }

            // Get waiters from pre-loaded map (batched to avoid N+1 queries)
            List<WaiterInfo> waiters = waitersByTableId.getOrDefault(table.getId(), new ArrayList<>());

            return TableListResponse.builder()
                    .id(table.getId().toString())
                    .tableCode(table.getTableCode())
                    .tableOrder(table.getTableOrder())
                    .rowOrder(row.getRowOrder())
                    .rowId(row.getId().toString())
                    .sectionId(section.getId().toString())
                    .sectionName(sectionName)
                    .capacity(table.getCapacity())
                    .tableStatus(table.getTableStatus().name())
                    .blockReason(table.getBlockReason())
                    .orderStatus(orderStatus)
                    .occupiedAt(occupiedAt)
                    .sessions(sessions)
                    .waiters(waiters)
                    .build();
        }

        /**
         * Get blank rows (rows with no matching tables) for the restaurant/section.
         * This ensures that all rows are shown in the Live Table Dashboard, even if they're empty.
         */
        private List<TableListResponse> getBlankRowsForRestaurant(
                UUID restaurantId, UUID sectionId, List<TableListResponse> existingResponses, Locale locale) {
            
            List<TableListResponse> blankRows = new ArrayList<>();
            
            try {
                // First, get the layout ID for the restaurant
                Optional<RestaurantLayout> layoutOpt = restaurantLayoutRepository
                        .findByRestaurantIdAndIsDeletedFalse(restaurantId);
                
                if (layoutOpt.isEmpty()) {
                    log.warn("No layout found for restaurant: {}", restaurantId);
                    return blankRows;
                }
                
                UUID layoutId = layoutOpt.get().getId();
                
                // Query sections first (without fetching collections to avoid MultipleBagFetchException)
                String sectionQueryStr = "SELECT s FROM RestaurantSection s " +
                        "WHERE s.restaurantLayout.id = :layoutId " +
                        "AND s.isDeleted = false " +
                        (sectionId != null ? "AND s.id = :sectionId " : "");
                
                jakarta.persistence.TypedQuery<RestaurantSection> sectionQuery = entityManager
                        .createQuery(sectionQueryStr, RestaurantSection.class)
                        .setParameter("layoutId", layoutId);
                if (sectionId != null) {
                    sectionQuery.setParameter("sectionId", sectionId);
                }
                List<RestaurantSection> sections = sectionQuery.getResultList();
                
                // Now fetch rows and translations separately for each section to avoid MultipleBagFetchException
                if (!sections.isEmpty()) {
                    List<UUID> sectionIds = sections.stream()
                            .map(RestaurantSection::getId)
                            .collect(Collectors.toList());
                    
                    // Fetch rows for all sections
                    String rowsQueryStr = "SELECT r FROM RestaurantRow r " +
                            "WHERE r.restaurantSection.id IN :sectionIds " +
                            "AND r.isDeleted = false " +
                            "ORDER BY r.restaurantSection.id, r.rowOrder";
                    List<RestaurantRow> allRows = entityManager
                            .createQuery(rowsQueryStr, RestaurantRow.class)
                            .setParameter(PARAM_SECTION_IDS, sectionIds)
                            .getResultList();
                    
                    // Group rows by section
                    Map<UUID, List<RestaurantRow>> rowsBySection = allRows.stream()
                            .collect(Collectors.groupingBy(r -> r.getRestaurantSection().getId()));
                    
                    // Fetch translations for all sections
                    String translationsQueryStr = "SELECT t FROM RestaurantSectionTranslation t " +
                            "WHERE t.restaurantSection.id IN :sectionIds";
                    List<RestaurantSectionTranslation> allTranslations = entityManager
                            .createQuery(translationsQueryStr, RestaurantSectionTranslation.class)
                            .setParameter(PARAM_SECTION_IDS, sectionIds)
                            .getResultList();
                    
                    // Group translations by section
                    Map<UUID, List<RestaurantSectionTranslation>> translationsBySection = allTranslations.stream()
                            .collect(Collectors.groupingBy(t -> t.getRestaurantSection().getId()));
                    
                    // Manually set the collections on sections to avoid lazy loading issues
                    for (RestaurantSection section : sections) {
                        UUID sectionIdValue = section.getId();
                        List<RestaurantRow> rows = rowsBySection.getOrDefault(sectionIdValue, new ArrayList<>());
                        section.setRows(rows);
                        section.setTranslations(translationsBySection.getOrDefault(sectionIdValue, new ArrayList<>()));
                    }
                }
                // Get all rowIds that have tables in the existing responses
                Set<UUID> rowsWithTables = existingResponses.stream()
                        .filter(r -> r.getRowId() != null)
                        .map(r -> UUID.fromString(r.getRowId()))
                        .collect(Collectors.toSet());
                
                // For each section, get all rows and create blank entries for rows without tables
                for (RestaurantSection section : sections) {
                    // Get section name with translation
                    String sectionName = "NA";
                    List<RestaurantSectionTranslation> sectionTranslations = section.getTranslations();
                    if (!sectionTranslations.isEmpty()) {
                        RestaurantSectionTranslation exactMatch = sectionTranslations.stream()
                                .filter(t -> t.getLanguageCode() != null && t.getLanguageCode().equals(locale.getLanguage()))
                                .findFirst()
                                .orElse(null);
                        
                        if (exactMatch != null && exactMatch.getName() != null && !exactMatch.getName().trim().isEmpty()) {
                            sectionName = exactMatch.getName();
                        } else {
                            Optional<RestaurantSectionTranslation> fallback =
                                    TranslationUtils.pickPreferredOrFromList(
                                            sectionTranslations,
                                            locale.getLanguage(),
                                            localizationProperties.getLanguages(),
                                            RestaurantSectionTranslation::getLanguageCode
                                    );
                            sectionName = fallback.map(RestaurantSectionTranslation::getName)
                                    .filter(name -> name != null && !name.trim().isEmpty())
                                    .orElse("NA");
                        }
                    }
                    
                    // Get all rows for this section
                    List<RestaurantRow> rows = section.getRows().stream()
                            .filter(r -> !Boolean.TRUE.equals(r.getIsDeleted()))
                            .sorted(Comparator.comparing(RestaurantRow::getRowOrder))
                            .collect(Collectors.toList());
                    
                    // Create blank entries for rows that don't have any tables in the response
                    // This includes rows with no tables at all, or rows whose tables are filtered out
                    for (RestaurantRow row : rows) {
                        // If this row doesn't appear in the existing responses, create a blank row
                        if (!rowsWithTables.contains(row.getId())) {
                            TableListResponse blankRow = TableListResponse.builder()
                                    .id(null) // No table ID for blank rows
                                    .tableCode(null) // No table code for blank rows
                                    .tableOrder(null) // No table order for blank rows
                                    .rowOrder(row.getRowOrder())
                                    .rowId(row.getId().toString())
                                    .sectionId(section.getId().toString())
                                    .sectionName(sectionName)
                                    .capacity(null)
                                    .tableStatus(null)
                                    .orderStatus(null)
                                    .readyItems(0)
                                    .pendingItems(0)
                                    .occupiedAt(null)
                                    .sessions(new ArrayList<>())
                                    .waiters(new ArrayList<>())
                                    .build();
                            blankRows.add(blankRow);
                        }
                    }
                }
                
            } catch (Exception e) {
                // Log the error but don't throw - return empty list to avoid transaction rollback
                log.error("Error getting blank rows for restaurant {}: {}", restaurantId, e.getMessage(), e);
                // Return empty list to prevent affecting the main transaction
                return new ArrayList<>();
            }
            
            return blankRows;
        }

        /**
         * Updates a table's status (supports both single-table and bulk updates).
         * <p>
         * Accepts either {@code payload.tableId} (backward compatible) or {@code payload.tableIds} (bulk).
         * Delegates single-table logic to {@link #updateSingleTableStatus(UUID, TableStatus, TableStatusPayload, String, String, Locale)},
         * collects per-table results for bulk requests, and returns a consolidated response.
         * </p>
         *
         * @param payload requested table status update payload (single or bulk)
         * @param userId  acting user id (string UUID)
         * @param userRole acting user role
         * @return response wrapper containing status update result(s)
         * @throws ResponseStatusException when inputs are invalid or all bulk updates fail
         */
        @Transactional
        public ResponseDto<TableStatusResponseWrapper> updateTableStatus(TableStatusPayload payload, String userId, String userRole) {
                Locale locale = LocaleContextHolder.getLocale();

                // Support both single tableId (backward compatible) and multiple tableIds (new functionality)
                List<UUID> tableIdsToProcess = new ArrayList<>();
                if (payload.getTableIds() != null && !payload.getTableIds().isEmpty()) {
                    // New functionality: process multiple tables
                    // Filter out null values to make it optional/lenient
                    tableIdsToProcess.addAll(payload.getTableIds().stream()
                            .filter(Objects::nonNull)
                            .collect(Collectors.toList()));
                    log.info("Processing bulk table status update for {} tables (after filtering nulls)", tableIdsToProcess.size());
                } else if (payload.getTableId() != null) {
                    // Backward compatible: process single table
                    tableIdsToProcess.add(payload.getTableId());
                } else {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                            "Table ID or Table IDs are required");
                }

                // Validate we have at least one valid table ID
                if (tableIdsToProcess.isEmpty()) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                            "At least one valid Table ID is required");
                }

                TableStatus newStatus = payload.getTableStatus();
                
                // If multiple tables, process each one and collect results
                if (tableIdsToProcess.size() > 1) {
                    List<TableStatusResponseWrapper> results = new ArrayList<>();
                    List<String> errors = new ArrayList<>();
                    for (UUID tableId : tableIdsToProcess) {
                        if (tableId == null) {
                            log.warn("Skipping null table ID in bulk update");
                            continue;
                        }
                        try {
                            TableStatusResponseWrapper result = updateSingleTableStatus(
                                    tableId, newStatus, payload, userId, userRole, locale);
                            results.add(result);
                        } catch (Exception e) {
                            log.error("Failed to update status for table {}: {}", tableId, e.getMessage(), e);
                            errors.add(String.format("Table %s: %s", tableId, e.getMessage()));
                            // Continue with other tables even if one fails
                        }
                    }
                    
                    // If all tables failed, throw an error
                    if (results.isEmpty() && !errors.isEmpty()) {
                        throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                                "Failed to update any tables: " + String.join("; ", errors));
                    }
                    
                    // Return response with list of results
                    TableStatusResponseWrapper wrapper = TableStatusResponseWrapper.builder()
                            .tableStatus(payload)
                            .tableStatuses(results)
                            .updatedAt(LocalDateTime.now(ZoneOffset.UTC))
                            .build();
                    
                    return ResponseDto.<TableStatusResponseWrapper>builder()
                            .message(messageUtil.getMessage(MSG_TABLE_STATUS_UPDATED, locale))
                            .data(wrapper)
                            .count((long) results.size())
                            .build();
                }
                
                // Single table processing (backward compatible - uses helper method)
                UUID tableId = tableIdsToProcess.get(0);
                TableStatusResponseWrapper wrapper = updateSingleTableStatus(
                        tableId, newStatus, payload, userId, userRole, locale);

                return ResponseDto.<TableStatusResponseWrapper>builder()
                        .message(messageUtil.getMessage(MSG_TABLE_STATUS_UPDATED, locale))
                        .data(wrapper)
                        .build();
        }

        /**
         * Helper method to update status for a single table.
         * Used by both single and bulk update operations.
         */
        private TableStatusResponseWrapper updateSingleTableStatus(
                UUID tableId, TableStatus newStatus, TableStatusPayload payload,
                String userId, String userRole, Locale locale) {
            
            // Validate tableId is not null
            if (tableId == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Table ID cannot be null");
            }
            
            RestaurantTable table = restaurantTableRepository.findById(tableId)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                            messageUtil.getMessage(MSG_TABLE_NOT_FOUND, locale)));

            // Do not allow starting a session on a soft-deleted table
            if (Boolean.TRUE.equals(table.getIsDeleted())) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                            messageUtil.getMessage("table.deleted", locale));
            }

            // Validate blocking requirements
            if (newStatus == TableStatus.BLOCKED) {
                if (payload.getReason() == null || payload.getReason().trim().isEmpty()) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                            messageUtil.getMessage("block.reason.required", locale));
                }
                
                // Check if table is already blocked
                if (table.getTableStatus() == TableStatus.BLOCKED) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                            messageUtil.getMessage("table.already.blocked", locale));
                }
                
                // Validate permissions for blocking
                // Managers can block any table, Waiters can only block tables they're assigned to
                if (userRole == null || (!userRole.equals(ROLE_MANAGER) && !userRole.equals(ROLE_WAITER))) {
                    throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                            messageUtil.getMessage("insufficient.permissions", locale));
                }
                
                // If user is WAITER, validate they are assigned to the table
                if (ROLE_WAITER.equals(userRole)) {
                    if (userId == null || userId.trim().isEmpty()) {
                        throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                                messageUtil.getMessage("user.id.required", locale));
                    }
                    try {
                        UUID waiterId = UUID.fromString(userId);
                        boolean isAssigned = tableAssignmentRepository
                                .existsByWaiterIdAndRestaurantTableIdAndUnassignedAtIsNull(
                                        waiterId, tableId);
                        
                        if (!isAssigned) {
                            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                                    messageUtil.getMessage("waiter.not.assigned.to.table", locale));
                        }
                    } catch (IllegalArgumentException e) {
                        log.error("Invalid userId format for waiter validation: {}", userId, e);
                        throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                                messageUtil.getMessage("invalid.user.id", locale));
                    }
                }
                
                // Prevent blocking table if it has active orders with status PUSHED, IN_PROGRESS, or SERVED
                // Check when changing from OCCUPIED to BLOCKED (as per requirement)
                if (table.getTableStatus() == TableStatus.OCCUPIED) {
                    log.info("Checking for active orders before blocking table {} (current status: OCCUPIED)", tableId);
                    
                    // Query orders directly by table ID with specific statuses (most reliable approach)
                    // This checks orders regardless of session expiration status
                    List<OrderStatus> activeStatuses = Arrays.asList(
                            OrderStatus.PUSHED, 
                            OrderStatus.IN_PROGRESS, 
                            OrderStatus.SERVED
                    );
                    
                    List<Order> activeOrders = orderRepository.findByTableIdAndOrderStatusIn(tableId, activeStatuses);
                    
                    String orderDetails = buildOrderDetailsString(activeOrders);
                    
                    log.info("Found {} active orders (PUSHED/IN_PROGRESS/SERVED) for table {} when checking for blocking. Order details: {}", 
                            activeOrders.size(), tableId, orderDetails);
                    
                    if (!activeOrders.isEmpty()) {
                        log.error("Cannot block table {} - has {} active order(s) with status PUSHED, IN_PROGRESS, or SERVED", 
                                tableId, activeOrders.size());
                        throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                                messageUtil.getMessage("table.cannot.block.active.orders", locale));
                    } else {
                        log.info("No active orders found for table {} - allowing block", tableId);
                    }
                } else {
                    log.debug("Table {} current status is {} (not OCCUPIED), skipping active order check", 
                            tableId, table.getTableStatus());
                }
            }

            // Prevent marking table for cleanup with scenario-specific validation
            // Validation order: OPEN -> PENDING -> COMPLETED
            if (newStatus == TableStatus.CLEANUP) {
                List<Session> activeSessions = sessionRepository.findByTableIdAndExpiredAtIsNull(tableId);
                
                log.info("Checking cleanup validation for table {} - found {} active session(s)", tableId, activeSessions.size());
                
                for (Session session : activeSessions) {
                    // Find all transactions by status
                    List<Transaction> openTransactions = transactionRepository
                            .findBySessionIdAndTransactionStatusOrderByCreatedAtAsc(session.getId(), TransactionStatus.OPEN);
                    List<Transaction> pendingTransactions = transactionRepository
                            .findBySessionIdAndTransactionStatusOrderByCreatedAtAsc(session.getId(), TransactionStatus.PENDING);
                    List<Transaction> completedTransactions = transactionRepository
                            .findBySessionIdAndTransactionStatusOrderByCreatedAtAsc(session.getId(), TransactionStatus.COMPLETED);
                    
                    log.info("Session {} - found {} OPEN, {} PENDING, and {} COMPLETED transactions", 
                            session.getId(), openTransactions.size(), pendingTransactions.size(), completedTransactions.size());
                    
                    // Scenario 1: Check OPEN transactions first
                    for (Transaction transaction : openTransactions) {
                        Order order = transaction.getOrder();
                        if (order == null) {
                            log.warn(LOG_TRANSACTION_NO_ORDER, transaction.getId());
                            continue;
                        }
                        
                        log.info("Checking OPEN transaction {} for order {}", transaction.getId(), order.getId());
                        
                        // Get all items and combos for this transaction
                        List<OrderedItem> allItems = orderedItemRepository.findByOrderId(order.getId())
                                .stream()
                                .filter(item -> item.getOrderedCombo() == null) // Only regular items, not combo items
                                .collect(Collectors.toList());
                        List<OrderedCombo> allCombos = orderedComboRepository.findByOrderId(order.getId());
                        
                        // Check for active items (PUSHED, ON_HOLD, COOKING, DELAYED, READY)
                        boolean hasActiveItems = allItems.stream()
                                .anyMatch(item -> {
                                    ItemStatus status = item.getItemStatus();
                                    return status == ItemStatus.PUSHED || status == ItemStatus.ON_HOLD 
                                            || status == ItemStatus.COOKING || status == ItemStatus.DELAYED 
                                            || status == ItemStatus.READY;
                                });
                        boolean hasActiveCombos = allCombos.stream()
                                .anyMatch(combo -> {
                                    ItemStatus status = combo.getItemStatus();
                                    return status == ItemStatus.PUSHED || status == ItemStatus.ON_HOLD 
                                            || status == ItemStatus.COOKING || status == ItemStatus.DELAYED 
                                            || status == ItemStatus.READY;
                                });
                        
                        if (hasActiveItems || hasActiveCombos) {
                            // Scenario 1a: OPEN transaction + active items
                            log.warn("BLOCKING cleanup for table {} - OPEN transaction {} has active items (PUSHED, ON_HOLD, COOKING, DELAYED, or READY)", 
                                    tableId, transaction.getId());
                            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                                    messageUtil.getMessage("table.cleanup.blocked.items.active", locale));
                        }
                        
                        // Check if all items are SERVED or CANCELED (but not all CANCELED)
                        boolean allServedOrCanceled = allItems.stream()
                                .allMatch(item -> item.getItemStatus() == ItemStatus.SERVED 
                                        || item.getItemStatus() == ItemStatus.CANCELED)
                                && allCombos.stream()
                                .allMatch(combo -> combo.getItemStatus() == ItemStatus.SERVED 
                                        || combo.getItemStatus() == ItemStatus.CANCELED);
                        
                        boolean allCanceled = allItems.stream()
                                .allMatch(item -> item.getItemStatus() == ItemStatus.CANCELED)
                                && allCombos.stream()
                                .allMatch(combo -> combo.getItemStatus() == ItemStatus.CANCELED);
                        
                        if (allServedOrCanceled && !allCanceled) {
                            // Scenario 1b: OPEN transaction + all items SERVED or CANCELED (but not all CANCELED)
                            log.warn("BLOCKING cleanup for table {} - OPEN transaction {} has all items SERVED or CANCELED, checkout required", 
                                    tableId, transaction.getId());
                            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                                    messageUtil.getMessage("table.cleanup.blocked.checkout.required", locale));
                        }
                        
                        // If all items are CANCELED, allow cleanup for this transaction (continue to next transaction)
                        log.info("OPEN transaction {} passed validation - all items are CANCELED", transaction.getId());
                    }
                    
                    // Scenario 2: Check PENDING transactions
                    for (Transaction transaction : pendingTransactions) {
                        Order order = transaction.getOrder();
                        if (order == null) {
                            log.warn(LOG_TRANSACTION_NO_ORDER, transaction.getId());
                            continue;
                        }
                        
                        log.info("Checking PENDING transaction {} for order {}", transaction.getId(), order.getId());
                        
                        // Get all items and combos for this transaction
                        List<OrderedItem> allItems = orderedItemRepository.findByOrderId(order.getId())
                                .stream()
                                .filter(item -> item.getOrderedCombo() == null) // Only regular items, not combo items
                                .collect(Collectors.toList());
                        List<OrderedCombo> allCombos = orderedComboRepository.findByOrderId(order.getId());
                        
                        // Check for active items (PUSHED, ON_HOLD, COOKING, DELAYED, READY)
                        boolean hasActiveItems = allItems.stream()
                                .anyMatch(item -> {
                                    ItemStatus status = item.getItemStatus();
                                    return status == ItemStatus.PUSHED || status == ItemStatus.ON_HOLD 
                                            || status == ItemStatus.COOKING || status == ItemStatus.DELAYED 
                                            || status == ItemStatus.READY;
                                });
                        boolean hasActiveCombos = allCombos.stream()
                                .anyMatch(combo -> {
                                    ItemStatus status = combo.getItemStatus();
                                    return status == ItemStatus.PUSHED || status == ItemStatus.ON_HOLD 
                                            || status == ItemStatus.COOKING || status == ItemStatus.DELAYED 
                                            || status == ItemStatus.READY;
                                });
                        
                        if (hasActiveItems || hasActiveCombos) {
                            // PENDING transaction with active items (shouldn't happen after checkout, but handle it)
                            log.warn("BLOCKING cleanup for table {} - PENDING transaction {} has active items (PUSHED, ON_HOLD, COOKING, DELAYED, or READY)", 
                                    tableId, transaction.getId());
                            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                                    messageUtil.getMessage("table.cleanup.blocked.items.active", locale));
                        }
                        
                        // Check if all items are SERVED or CANCELED (but not all CANCELED)
                        boolean allServedOrCanceled = allItems.stream()
                                .allMatch(item -> item.getItemStatus() == ItemStatus.SERVED 
                                        || item.getItemStatus() == ItemStatus.CANCELED)
                                && allCombos.stream()
                                .allMatch(combo -> combo.getItemStatus() == ItemStatus.SERVED 
                                        || combo.getItemStatus() == ItemStatus.CANCELED);
                        
                        boolean allCanceled = allItems.stream()
                                .allMatch(item -> item.getItemStatus() == ItemStatus.CANCELED)
                                && allCombos.stream()
                                .allMatch(combo -> combo.getItemStatus() == ItemStatus.CANCELED);
                        
                        if (allServedOrCanceled && !allCanceled) {
                            // Scenario 2: PENDING transaction + all items SERVED or CANCELED (but not all CANCELED)
                            log.warn("BLOCKING cleanup for table {} - PENDING transaction {} has all items SERVED or CANCELED, payment pending", 
                                    tableId, transaction.getId());
                            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                                    messageUtil.getMessage("table.cleanup.blocked.payment.pending", locale));
                        }
                        
                        // If all items are CANCELED, allow cleanup for this transaction (continue to next transaction)
                        log.info("PENDING transaction {} passed validation - all items are CANCELED", transaction.getId());
                    }
                    
                    // Scenario 3: Check COMPLETED transactions
                    for (Transaction transaction : completedTransactions) {
                        Order order = transaction.getOrder();
                        if (order == null) {
                            log.warn(LOG_TRANSACTION_NO_ORDER, transaction.getId());
                            continue;
                        }
                        
                        log.info("Checking COMPLETED transaction {} for order {}", transaction.getId(), order.getId());
                        
                        // Get all items and combos for this transaction
                        List<OrderedItem> allItems = orderedItemRepository.findByOrderId(order.getId())
                                .stream()
                                .filter(item -> item.getOrderedCombo() == null) // Only regular items, not combo items
                                .collect(Collectors.toList());
                        List<OrderedCombo> allCombos = orderedComboRepository.findByOrderId(order.getId());
                        
                        // Check for active items (PUSHED, ON_HOLD, COOKING, DELAYED, READY)
                        boolean hasActiveItems = allItems.stream()
                                .anyMatch(item -> {
                                    ItemStatus status = item.getItemStatus();
                                    return status == ItemStatus.PUSHED || status == ItemStatus.ON_HOLD 
                                            || status == ItemStatus.COOKING || status == ItemStatus.DELAYED 
                                            || status == ItemStatus.READY;
                                });
                        boolean hasActiveCombos = allCombos.stream()
                                .anyMatch(combo -> {
                                    ItemStatus status = combo.getItemStatus();
                                    return status == ItemStatus.PUSHED || status == ItemStatus.ON_HOLD 
                                            || status == ItemStatus.COOKING || status == ItemStatus.DELAYED 
                                            || status == ItemStatus.READY;
                                });
                        
                        if (hasActiveItems || hasActiveCombos) {
                            // Scenario 3: COMPLETED transaction + active items
                            log.warn("BLOCKING cleanup for table {} - COMPLETED transaction {} has active items (PUSHED, ON_HOLD, COOKING, DELAYED, or READY)", 
                                    tableId, transaction.getId());
                            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                                    messageUtil.getMessage("table.cleanup.blocked.items.incomplete", locale));
                        }
                        
                        // Check if all items are SERVED or CANCELED
                        boolean allServedOrCanceled = allItems.stream()
                                .allMatch(item -> item.getItemStatus() == ItemStatus.SERVED 
                                        || item.getItemStatus() == ItemStatus.CANCELED)
                                && allCombos.stream()
                                .allMatch(combo -> combo.getItemStatus() == ItemStatus.SERVED 
                                        || combo.getItemStatus() == ItemStatus.CANCELED);
                        
                        if (!allServedOrCanceled) {
                            // This shouldn't happen if we already checked for active items, but handle edge cases
                            log.warn("BLOCKING cleanup for table {} - COMPLETED transaction {} has items that are not SERVED or CANCELED", 
                                    tableId, transaction.getId());
                            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                                    messageUtil.getMessage("table.cleanup.blocked.items.incomplete", locale));
                        }
                        
                        // If all items are SERVED or CANCELED, allow cleanup for this transaction
                        log.info("COMPLETED transaction {} passed validation - all items are SERVED or CANCELED", transaction.getId());
                    }
                }
                
                log.info("Cleanup validation passed for table {} - all transactions meet cleanup requirements", tableId);
            }

            TableStatus oldStatus = table.getTableStatus();
            table.setTableStatus(newStatus);
            table.setUpdatedAt(OffsetDateTime.now(ZoneOffset.UTC));
            
            // Set or clear blockReason based on new status
            if (newStatus == TableStatus.BLOCKED) {
                table.setBlockReason(payload.getReason());
            } else if (oldStatus == TableStatus.BLOCKED) {
                table.setBlockReason(null);
            }
            
            // Set updatedBy if user information is provided
            if (userId != null) {
                try {
                    User user = userRepository.findById(UUID.fromString(userId))
                            .orElse(null);
                    if (user != null) {
                        table.setUpdatedBy(user);
                    }
                } catch (Exception e) {
                    log.warn("Could not set updatedBy user for table {}: {}", tableId, e.getMessage());
                }
            }
            
            // Expire all active sessions ONLY when setting to cleanup
            // Expire sessions for CLEANUP to ensure proper cleanup of table state
            // Do NOT expire sessions when blocking or transitioning to AVAILABLE to preserve KDS dashboard items
            // KDS dashboard should only reset based on configured reset time, not table status changes
            List<Session> activeSessions = sessionRepository.findByTableIdAndExpiredAtIsNull(tableId);
            if (!activeSessions.isEmpty() && newStatus == TableStatus.CLEANUP) {
                OffsetDateTime expiredAt = OffsetDateTime.now(ZoneOffset.UTC);
                for (Session session : activeSessions) {
                    session.setExpiredAt(expiredAt);
                }
                sessionRepository.saveAll(activeSessions);
                log.info("Expired {} active session(s) for table {} with status {}", activeSessions.size(), tableId, newStatus);
            }
            
            // Update table status with WebSocket notification and async database update
            updateTableStatusWithNotification(table, newStatus, oldStatus, table.getUpdatedBy(), locale);

            // Create audit trail for table block/unblock when manager or waiter performs the action.
            // Manager: entry for manager. Waiter: entry for manager's audit (restaurant) showing waiter did the action.
            if ((ROLE_MANAGER.equals(userRole) || ROLE_WAITER.equals(userRole)) && (newStatus == TableStatus.BLOCKED || oldStatus == TableStatus.BLOCKED)) {
                try {
                    User actor = userId != null ? userRepository.findById(UUID.fromString(userId)).orElse(null) : null;
                    if (actor != null) {
                        Restaurant restaurant = getRestaurantFromTable(table);
                        ActionType actionType = newStatus == TableStatus.BLOCKED ? ActionType.TABLE_BLOCK : ActionType.TABLE_UNBLOCK;
                        String notes = String.format("Table status changed from %s to %s", 
                                oldStatus != null ? oldStatus.name() : "NULL",
                                newStatus != null ? newStatus.name() : "NULL");
                        if (newStatus == TableStatus.BLOCKED && payload.getReason() != null) {
                            notes += ". Reason: " + payload.getReason();
                        }
                        if (ROLE_WAITER.equals(userRole)) {
                            notes += " (by waiter)";
                        }
                        
                        auditTrailService.createAuditTrail(
                                actor,
                                actionType,
                                restaurant,
                                RequestStatus.NA,
                                null, // ipAddress
                                null, // userAgent
                                tableId,
                                "TABLE",
                                notes
                        );
                    }
                } catch (Exception e) {
                    log.error("Failed to create audit trail for table block/unblock: {}", e.getMessage());
                }
            }

            // Get user name for response
            String updatedByName = null;
            if (userId != null) {
                try {
                    User user = userRepository.findById(UUID.fromString(userId)).orElse(null);
                    if (user != null) {
                        updatedByName = user.getFirstName() + " " + user.getLastName();
                    }
                } catch (Exception e) {
                    log.warn("Could not get user name for response: {}", e.getMessage());
                }
            }

            // Create payload for this specific table
            TableStatusPayload tablePayload = TableStatusPayload.builder()
                    .tableId(tableId)
                    .tableStatus(newStatus)
                    .reason(payload.getReason())
                    .notes(payload.getNotes())
                    .build();

            return TableStatusResponseWrapper.builder()
                    .tableStatus(tablePayload)
                    .previousStatus(oldStatus != null ? oldStatus.name() : null)
                    .updatedBy(updatedByName)
                    .updatedAt(LocalDateTime.now(ZoneOffset.UTC))
                    .build();
        }

        /**
         * Returns a pre-signed S3 URL for downloading/printing a table's QR code image.
         * When the table has no stored PNG key or no print PDF URL, generates the PNG (and PDF when needed)
         * using the same pipeline as layout QR generation, persists keys, then returns the presigned PNG URL.
         *
         * @param tableId table identifier
         * @return pre-signed URL for the stored QR code asset
         * @throws ResponseStatusException when the table is not found or generation fails
         */
        @Override
        @Transactional
        public String getTableQrCodePresignedUrl(UUID tableId) {
                Locale locale = LocaleContextHolder.getLocale();

                RestaurantTable table = restaurantTableRepository.findById(tableId)
                        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                                messageUtil.getMessage(MSG_TABLE_NOT_FOUND, locale)));

                ensureTableQrPngAndPdfIfMissing(table, tableId, locale);

                RestaurantTable refreshed = restaurantTableRepository.findById(tableId)
                        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                                messageUtil.getMessage(MSG_TABLE_NOT_FOUND, locale)));

                String s3Key = refreshed.getQrCodeUrl();
                if (s3Key == null || s3Key.isEmpty()) {
                        throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                                messageUtil.getMessage("table.qr.regenerate.failed", locale));
                }

                // Note: TABLE_QR_PRINT audit trail is not implemented here because:
                // 1. This endpoint doesn't require User-ID/User-Role headers (it's a public GET endpoint)
                // 2. QR code printing/downloading is typically done client-side after getting the presigned URL
                // 3. If audit trail is needed, consider adding it in a separate endpoint that requires authentication
                //    or modify this endpoint to accept optional User-ID header for audit purposes

                return awsService.getPreSignedUrl(s3Key);
        }

    /**
     * Regenerates and re-uploads a table QR code for a manager, then returns a pre-signed URL to the new asset.
     * <p>
     * Generates the QR content using baseUrl + restaurantId + tableId, writes the QR PNG to S3, updates the table's
     * stored key, generates/uploads a PDF version, and writes an audit-trail entry.
     * </p>
     *
     * @param tableId  table identifier
     * @param userId   acting user id (string UUID)
     * @param userRole acting role (must be MANAGER)
     * @return pre-signed URL to the regenerated QR code asset
     * @throws ResponseStatusException when authorization fails, entities are missing, or generation/upload fails
     */
    @Override
    @Transactional
    public String regenerateTableQrCode(UUID tableId, String userId, String userRole) {
        Locale locale = LocaleContextHolder.getLocale();

        if (userRole == null || !userRole.equalsIgnoreCase(ROLE_MANAGER)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    messageUtil.getMessage("user.role.manager.required", locale));
        }

        RestaurantTable table = restaurantTableRepository.findById(tableId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        messageUtil.getMessage(MSG_TABLE_NOT_FOUND, locale)));

        UUID restaurantId = resolveRestaurantIdForTable(table, tableId, locale);

        try {
            generateUploadPngAndSaveTable(table, restaurantId, tableId);

            // Generate and upload QR code PDF
            generateAndUploadQrCodePdf(restaurantId, tableId, table);

            // Create audit trail for QR code generation
            createQrGenerationAuditTrail(restaurantId, tableId, table, userId);

            return awsService.getPreSignedUrl(table.getQrCodeUrl());
        } catch (WriterException | java.io.IOException e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    messageUtil.getMessage("table.qr.regenerate.failed", locale));
        }
    }


        /**
         * Starts or resumes a dining session for a table, updating table status and issuing a session token.
         * <p>
         * Validates table ownership (restaurant id), blocks session start when table is BLOCKED/CLEANUP/deleted,
         * transitions the table to OCCUPIED (with WebSocket notification), then creates/reuses sessions based on
         * QR code type and chain payment type rules (STATIC vs DYNAMIC, PREPAID vs POSTPAID).
         * </p>
         *
         * @param restaurantId restaurant identifier from QR context
         * @param tableId      table identifier from QR context
         * @param qrCodeType   QR code type (STATIC/DYNAMIC)
         * @param sessionId    optional session id (used for validating a requested DYNAMIC session)
         * @return response wrapper containing session token and restaurant/table details
         * @throws ResponseStatusException when validation fails or the session cannot be started
         */
        @Override
        public ResponseDto<SessionResponseDto> startSession(UUID restaurantId, UUID tableId, QrCodeType qrCodeType, UUID sessionId) 
        {
                Locale locale = LocaleContextHolder.getLocale();

                RestaurantTable table = restaurantTableRepository.findById(tableId)
                        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                                messageUtil.getMessage(MSG_TABLE_NOT_FOUND, locale)));

                // Do not allow starting a session on a soft-deleted table
                if (Boolean.TRUE.equals(table.getIsDeleted())) {
                        throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                                messageUtil.getMessage("table.deleted", locale));
                }

                if (!table.getRestaurantRow().getRestaurantSection().getRestaurantLayout().getRestaurant().getId().equals(restaurantId)) {
                        throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                                messageUtil.getMessage("table.restaurant.mismatch", locale));
                }

                // Check if table is blocked - cannot start session on blocked table
                if (table.getTableStatus() == TableStatus.BLOCKED) {
                        throw new ResponseStatusException(HttpStatus.LOCKED,
                                messageUtil.getMessage("table.blocked.cannot.order", locale));
                }

                // Check if table is in cleanup - cannot start session on cleanup table
                if (table.getTableStatus() == TableStatus.CLEANUP) {
                        throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                                messageUtil.getMessage("table.cleanup.cannot.order", locale));
                }

                // Do not allow customers to start a session (and place orders) unless at least one waiter is assigned.
                // This avoids orders being placed with no responsible waiter for the table.
                List<TableAssignment> activeAssignments = tableAssignmentRepository
                        .findByRestaurantTableIdAndUnassignedAtIsNull(tableId);
                if (activeAssignments == null || activeAssignments.isEmpty()) {
                        throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                                messageUtil.getMessage("table.no.waiter.assigned.cannot.order", locale));
                }

                table.setTableStatus(TableStatus.OCCUPIED);
                restaurantTableRepository.save(table);
                sendTableStatusWebSocketNotification(locale, restaurantId, tableId, TableStatus.OCCUPIED);

                Session session = null;
                String messageKey;
                
        // Get payment system type from configuration
        PaymentSystemType paymentSystemType = restaurantChainConfigProperties.getChain().getPaymentType();

                if (qrCodeType == QrCodeType.STATIC) {
                        if (paymentSystemType == PaymentSystemType.PREPAID) {
                                // PREPAID: Always generate new sessionId (no sequence number for STATIC)
                                session = Session.builder()
                                                .restaurantId(restaurantId)
                                                .tableId(tableId)
                                                .issuedAt(OffsetDateTime.now(ZoneOffset.UTC))
                                                .expiredAt(null)
                                                .qrCodeType(QrCodeType.STATIC)
                                                .sequenceNo(null)
                                                .build();
                                session = sessionRepository.save(session);
                                messageKey = MSG_SESSION_START_SUCCESS_CREATED;
                } else {
                        // POSTPAID or default behavior: Check if STATIC session exists and is not expired
                        List<Session> staticSessions = sessionRepository.findByTableIdAndQrCodeTypeAndExpiredAtIsNull(tableId, QrCodeType.STATIC);
                        
                        if (staticSessions.isEmpty()) {
                                // No session exists - create new one (no sequence number for STATIC)
                                session = Session.builder()
                                                .restaurantId(restaurantId)
                                                .tableId(tableId)
                                                .issuedAt(OffsetDateTime.now(ZoneOffset.UTC))
                                                .expiredAt(null)
                                                .qrCodeType(QrCodeType.STATIC)
                                                .sequenceNo(null)
                                                .build();
                                session = sessionRepository.save(session);
                                messageKey = MSG_SESSION_START_SUCCESS_CREATED;
                        } else if (staticSessions.size() == 1) {
                                // Single session exists - reuse it
                                session = staticSessions.get(0);
                                messageKey = "session.start.success.reused";
                        } else {
                                // Multiple sessions exist - expire all and create new one
                                log.warn("Multiple active STATIC sessions found for table {} ({} sessions). Expiring all and creating new session.", 
                                        tableId, staticSessions.size());
                                
                                OffsetDateTime expiredAt = OffsetDateTime.now(ZoneOffset.UTC);
                                for (Session existingSession : staticSessions) {
                                        existingSession.setExpiredAt(expiredAt);
                                }
                                sessionRepository.saveAll(staticSessions);
                                
                                // Create new session
                                session = Session.builder()
                                                .restaurantId(restaurantId)
                                                .tableId(tableId)
                                                .issuedAt(OffsetDateTime.now(ZoneOffset.UTC))
                                                .expiredAt(null)
                                                .qrCodeType(QrCodeType.STATIC)
                                                .sequenceNo(null)
                                                .build();
                                session = sessionRepository.save(session);
                                messageKey = MSG_SESSION_START_SUCCESS_CREATED;
                        }
                }
                } else if (qrCodeType == QrCodeType.DYNAMIC) {
                        if (sessionId != null) {
                                // Requested sessionId - validate and return it
                                session = sessionRepository.findById(sessionId).orElse(null);

                                if (session == null) {
                                        throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                                                messageUtil.getMessage("session.not.found", locale));
                                }

                                if (session.getExpiredAt() != null) {
                                        throw new ResponseStatusException(HttpStatus.CONFLICT,
                                                messageUtil.getMessage("session.expired", locale));
                                }

                                messageKey = "session.start.success.reused";
                        } else {
                                // Dynamic QR without sessionId - always generate new sessionId
                                Integer maxSequenceNo = sessionRepository.findMaxSequenceNoByTableIdAndExpiredAtIsNull(tableId);
                                if (maxSequenceNo == null) {
                                        maxSequenceNo = 0;
                                }
                                int newSequenceNo = maxSequenceNo + 1;
                                session = Session.builder()
                                                .restaurantId(restaurantId)
                                                .tableId(tableId)
                                                .issuedAt(OffsetDateTime.now(ZoneOffset.UTC))
                                                .expiredAt(null)
                                                .qrCodeType(QrCodeType.DYNAMIC)
                                                .sequenceNo(newSequenceNo)
                                                .build();
                                session = sessionRepository.save(session);
                                messageKey = MSG_SESSION_START_SUCCESS_CREATED;
                        }
                } else {
                        throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                                        messageUtil.getMessage("error.invalid.qrCodeType", locale));
                }

                session.setTokenExpiryAt(OffsetDateTime.now(ZoneOffset.UTC).plusHours(jwtExpiryHours));
                session = sessionRepository.save(session);

                String token = jwtUtil.generateCustomerSessionToken(
                        session.getId(), restaurantId, tableId, session.getTokenExpiryAt());

                RestaurantDetailsDto restaurantDetails = RestaurantDetailsDto.builder()
                        .sessionId(session.getId())
                        .restaurantId(restaurantId)
                        .tableId(tableId)
                        .countryName(restaurantChainConfigProperties.getChain() != null
                                ? restaurantChainConfigProperties.getChain().getCountryName()
                                : null)
                        .tableOrder(table.getTableOrder())
                        .qrCodeType(session.getQrCodeType())
                        .sequenceNo(session.getSequenceNo())
                        .tableCode(table.getTableCode())
                        .isVirtual(Boolean.TRUE.equals(table.getIsVirtual())) // Set isVirtual flag based on table
                        .build();

                SessionWrapperDto sessionWrapper = SessionWrapperDto.builder()
                        .token(token)
                        .restaurantDetails(restaurantDetails)
                        .build();

                SessionResponseDto sessionResponse = SessionResponseDto.builder()
                        .session(sessionWrapper)
                        .build();

                return ResponseDto.<SessionResponseDto>builder()
                        .message(messageUtil.getMessage(messageKey, locale))
                        .data(sessionResponse)
                        .build();
        }

        @Override
        @Transactional
        public void validateSession(UUID sessionId, String token) {
                Locale locale = LocaleContextHolder.getLocale();

                Session session = sessionRepository.findById(sessionId)
                        .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED,
                                messageUtil.getMessage("session.not.found", locale)));

                if (session.getExpiredAt() != null
                                && session.getExpiredAt().isBefore(OffsetDateTime.now(ZoneOffset.UTC))) {
                        throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,
                                messageUtil.getMessage("session.expired", locale));
                }

                OffsetDateTime tokenExpiryAt = session.getTokenExpiryAt();
                if (tokenExpiryAt == null) {
                        throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,
                                messageUtil.getMessage(MSG_SESSION_TOKEN_EXPIRED, locale));
                }

                Date jwtExpiry = jwtUtil.getExpirationFromToken(token);
                Instant jwtInstant = jwtExpiry.toInstant().truncatedTo(ChronoUnit.SECONDS);
                Instant dbInstant = tokenExpiryAt.toInstant().truncatedTo(ChronoUnit.SECONDS);

                if (!jwtInstant.equals(dbInstant)) {
                        throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,
                                messageUtil.getMessage(MSG_SESSION_TOKEN_EXPIRED, locale));
                }

                if (tokenExpiryAt.isBefore(OffsetDateTime.now(ZoneOffset.UTC))) {
                        throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,
                                messageUtil.getMessage(MSG_SESSION_TOKEN_EXPIRED, locale));
                }
        }

    // ==================== MANAGER-SPECIFIC APIs ====================

    /**
     * Transfers guests (an active session) from one table to another (manager-only).
     * <p>
     * Validates source/target tables and that the session is active and belongs to the source table, moves the session
     * and associated orders to the target table, updates table statuses, sends relevant WebSocket notifications,
     * notifies affected waiters, and records an audit trail entry.
     * </p>
     *
     * @param request  guest transfer payload (source/target table ids, session id, optional reason)
     * @param userId   acting user id (string UUID)
     * @param userRole acting role (must be MANAGER)
     * @return response wrapper containing transfer result details
     * @throws ResponseStatusException when validation fails or required entities are not found
     */
    @Override
    @Transactional
    public ResponseDto<GuestTransferResponse> transferGuests(GuestTransferRequest request, String userId, String userRole) {
        Locale userLocale = LocaleContextHolder.getLocale();
        
        // Validate manager role
        validateManagerRole(userRole, userLocale);
        
        // Validate source and target tables
        RestaurantTable sourceTable = restaurantTableRepository.findById(request.getSourceTableId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        messageUtil.getMessage(MSG_TABLE_NOT_FOUND, userLocale)));
        
        RestaurantTable targetTable = restaurantTableRepository.findById(request.getTargetTableId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        messageUtil.getMessage(MSG_TABLE_NOT_FOUND, userLocale)));
        
        // Validate session exists and is active
        Session session = sessionRepository.findById(request.getSessionId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        messageUtil.getMessage("session.not.found", userLocale)));
        
        if (session.getExpiredAt() != null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    messageUtil.getMessage("session.expired", userLocale));
        }
        
        // Validate session belongs to source table
        if (!session.getTableId().equals(request.getSourceTableId())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    messageUtil.getMessage("session.table.mismatch", userLocale));
        }
        
        // Validate target table is available
        if (targetTable.getTableStatus() != TableStatus.AVAILABLE) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    messageUtil.getMessage("table.not.available", userLocale));
        }
        
        // Get row information from tables
        RestaurantRow sourceRow = sourceTable.getRestaurantRow();
        RestaurantRow targetRow = targetTable.getRestaurantRow();
        
        // Validate row IDs if provided in request (optional validation)
        if (request.getSourceRowId() != null && !request.getSourceRowId().isBlank() 
                && (sourceRow == null || !sourceRow.getId().toString().equals(request.getSourceRowId()))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    messageUtil.getMessage("guest.transfer.sourceRowId.mismatch", userLocale));
        }
        
        if (request.getTargetRowId() != null && !request.getTargetRowId().isBlank() 
                && (targetRow == null || !targetRow.getId().toString().equals(request.getTargetRowId()))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    messageUtil.getMessage("guest.transfer.targetRowId.mismatch", userLocale));
        }
        
        // Get waiter assignments (get first active assignment if any)
        // Use method that eagerly fetches waiter to avoid LazyInitializationException
        List<TableAssignment> sourceAssignments = tableAssignmentRepository
                .findByRestaurantTableIdAndUnassignedAtIsNullWithWaiter(request.getSourceTableId());
        TableAssignment sourceAssignment = sourceAssignments.isEmpty() ? null : sourceAssignments.get(0);
        
        List<TableAssignment> targetAssignments = tableAssignmentRepository
                .findByRestaurantTableIdAndUnassignedAtIsNullWithWaiter(request.getTargetTableId());
        TableAssignment targetAssignment = targetAssignments.isEmpty() ? null : targetAssignments.get(0);
        
        // Check if source table has other active sessions before transferring
        List<Session> allSourceSessions = sessionRepository.findByTableIdAndExpiredAtIsNull(request.getSourceTableId());
        boolean hasOtherActiveSessions = allSourceSessions.stream()
                .anyMatch(s -> !s.getId().equals(session.getId()));
        
        // Transfer session to target table
        session.setTableId(request.getTargetTableId());
        sessionRepository.save(session);
        
        // Update all orders associated with this session to point to target table
        List<Order> ordersToUpdate = orderRepository.findBySessionIdOrderByCreatedAtDesc(session.getId());
        if (!ordersToUpdate.isEmpty()) {
            log.info("Updating {} order(s) from source table {} to target table {} for session {}", 
                    ordersToUpdate.size(), request.getSourceTableId(), request.getTargetTableId(), session.getId());
            for (Order order : ordersToUpdate) {
                order.setRestaurantTable(targetTable);
            }
            orderRepository.saveAll(ordersToUpdate);
            log.info("Successfully updated {} order(s) to target table", ordersToUpdate.size());
            
            // Send item-status WebSocket notifications for all items and combos in transferred orders
            // Skip PUSHED/SERVED/CANCELED statuses — their dedicated methods send user-specific
            // notifications targeting only assigned KDS users. Broadcasting would leak to all KDS.
            UUID targetRestaurantId = getRestaurantIdFromTable(targetTable);
            for (Order order : ordersToUpdate) {
                // Send notifications only for ON_HOLD items (non-KDS).
                // All KDS-specific statuses are excluded to prevent cross-category KDS notification leaks.
                List<OrderedItem> orderedItems = orderedItemRepository.findByOrderId(order.getId());
                for (OrderedItem orderedItem : orderedItems) {
                    if (orderedItem.getItemStatus() != null
                            && orderedItem.getItemStatus() == ItemStatus.ON_HOLD) {
                        sendItemStatusWebSocketNotification(userLocale, targetRestaurantId, orderedItem.getId(), orderedItem.getItemStatus(), "item");
                    }
                }
                
                // Send notifications only for ON_HOLD combos (non-KDS).
                List<OrderedCombo> orderedCombos = orderedComboRepository.findByOrderId(order.getId());
                for (OrderedCombo orderedCombo : orderedCombos) {
                    if (orderedCombo.getItemStatus() != null
                            && orderedCombo.getItemStatus() == ItemStatus.ON_HOLD) {
                        sendItemStatusWebSocketNotification(userLocale, targetRestaurantId, orderedCombo.getId(), orderedCombo.getItemStatus(), "combo");
                    }
                }
            }
            log.info("Sent item-status WebSocket notifications for all items and combos in transferred orders");
        }
        
        // Update table statuses
        UUID sourceRestaurantId = getRestaurantIdFromTable(sourceTable);
        UUID targetRestaurantId = getRestaurantIdFromTable(targetTable);
        
        if (!hasOtherActiveSessions) {
            // No more active sessions on source table, set to AVAILABLE
            sourceTable.setTableStatus(TableStatus.AVAILABLE);
            
            // Send WebSocket notification for source table status update
            sendTableStatusWebSocketNotification(userLocale, sourceRestaurantId, sourceTable.getId(), TableStatus.AVAILABLE);
        }
        // Target table becomes OCCUPIED
        targetTable.setTableStatus(TableStatus.OCCUPIED);
        
        // Send WebSocket notification for target table status update
        sendTableStatusWebSocketNotification(userLocale, targetRestaurantId, targetTable.getId(), TableStatus.OCCUPIED);
        
        restaurantTableRepository.saveAll(List.of(sourceTable, targetTable));
        
        // Build response
        GuestTransferResponse response = GuestTransferResponse.builder()
                .sessionId(session.getId())
                .sourceTableId(request.getSourceTableId())
                .targetTableId(request.getTargetTableId())
                .sourceTableOrder(sourceTable.getTableOrder().toString())
                .targetTableOrder(targetTable.getTableOrder().toString())
                .sourceRowId(sourceRow != null ? sourceRow.getId().toString() : null)
                .targetRowId(targetRow != null ? targetRow.getId().toString() : null)
                .sourceRowOrder(sourceRow != null ? sourceRow.getRowOrder() : null)
                .targetRowOrder(targetRow != null ? targetRow.getRowOrder() : null)
                .transferredAt(LocalDateTime.now(ZoneOffset.UTC))
                .reason(request.getReason())
                .previousWaiterId(sourceAssignment != null ? sourceAssignment.getWaiter().getId().toString() : null)
                .newWaiterId(targetAssignment != null ? targetAssignment.getWaiter().getId().toString() : null)
                .previousWaiterName(sourceAssignment != null ? sourceAssignment.getWaiter().getFirstName() + " " + sourceAssignment.getWaiter().getLastName() : null)
                .newWaiterName(targetAssignment != null ? targetAssignment.getWaiter().getFirstName() + " " + targetAssignment.getWaiter().getLastName() : null)
                .build();
        
        // Send FCM notifications to waiters via notification service
        User sourceWaiter = sourceAssignment != null && sourceAssignment.getWaiter() != null 
                ? sourceAssignment.getWaiter() : null;
        User targetWaiter = targetAssignment != null && targetAssignment.getWaiter() != null 
                ? targetAssignment.getWaiter() : null;
        
        try {
            notificationService.notifyGuestTransfer(sourceTable, targetTable, sourceWaiter, targetWaiter, userLocale);
        } catch (Exception e) {
            log.error("Failed to send guest transfer notifications: {}", e.getMessage(), e);
            // Don't fail the transfer if notification fails
        }
        
        // Create audit trail for table transfer
        try {
            User manager = userRepository.findById(UUID.fromString(userId))
                    .orElse(null);
            if (manager != null) {
                Restaurant restaurant = getRestaurantFromTable(sourceTable);
                String notes = String.format("Guests transferred from Table %s to Table %s. Reason: %s", 
                        sourceTable.getTableOrder(), 
                        targetTable.getTableOrder(),
                        request.getReason() != null ? request.getReason() : "N/A");
                
                auditTrailService.createAuditTrail(
                        manager,
                        ActionType.TABLE_TRANSFER,
                        restaurant,
                        RequestStatus.NA,
                        null, // ipAddress
                        null, // userAgent
                        session.getId(),
                        "SESSION",
                        notes
                );
            }
        } catch (Exception e) {
            log.error("Failed to create audit trail for table transfer: {}", e.getMessage());
        }
        
        return ResponseDto.<GuestTransferResponse>builder()
                .message(messageUtil.getMessage("guest.transfer.success", userLocale))
                .data(response)
                .build();
    }

    /**
     * Moves one or more tables to a target section (and optionally a target row) within the same layout (manager-only).
     * <p>
     * Validates tables exist and belong to the same layout, resolves the target section/row, reassigns row references,
     * assigns sequential table orders at the end of the target row, flushes and clears persistence context to avoid
     * stale section relationships, and records an audit trail entry.
     * </p>
     *
     * @param request  move request including table ids, target section id, optional target row id, and reason
     * @param userId   acting user id (string UUID)
     * @param userRole acting role (must be MANAGER)
     * @return response wrapper containing move result details
     * @throws ResponseStatusException when validation fails or entities are not found
     */
    @Override
    @Transactional
    public ResponseDto<TableMoveResponse> moveTables(TableMoveRequest request, String userId, String userRole) {
        Locale userLocale = LocaleContextHolder.getLocale();
        
        // Validate manager role
        validateManagerRole(userRole, userLocale);
        
        // Validate tables exist and get their current sections
        List<RestaurantTable> tables = restaurantTableRepository.findAllById(request.getTableIds());
        if (tables.size() != request.getTableIds().size()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                    messageUtil.getMessage(MSG_TABLE_NOT_FOUND, userLocale));
        }
        
        // All tables must belong to the same layout; sections may differ.
        RestaurantSection targetSection = null;
        UUID sourceSectionId = null;
        String sourceSectionName = "";
        String targetSectionName = "";

        UUID layoutId = tables.get(0).getRestaurantRow().getRestaurantSection().getRestaurantLayout().getId();
        LinkedHashSet<UUID> distinctSourceSectionIds = new LinkedHashSet<>();
        List<String> distinctSourceSectionNames = new ArrayList<>();
        for (RestaurantTable table : tables) {
            RestaurantSection currentSection = table.getRestaurantRow().getRestaurantSection();
            UUID tableLayoutId = currentSection.getRestaurantLayout().getId();
            if (!layoutId.equals(tableLayoutId)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        messageUtil.getMessage("table.cannot.move.different.layout", userLocale));
            }
            if (distinctSourceSectionIds.add(currentSection.getId())) {
                distinctSourceSectionNames.add(getSectionName(currentSection, userLocale));
            }
        }
        List<UUID> sourceSectionIds = new ArrayList<>(distinctSourceSectionIds);
        if (distinctSourceSectionIds.size() == 1) {
            sourceSectionId = sourceSectionIds.get(0);
            sourceSectionName = distinctSourceSectionNames.get(0);
        } else {
            sourceSectionName = String.join(", ", distinctSourceSectionNames);
        }
        
        // Find target section
        List<RestaurantSection> allSections = tables.get(0).getRestaurantRow()
                .getRestaurantSection().getRestaurantLayout().getSections();
        
        targetSection = allSections.stream()
                .filter(section -> section.getId().equals(request.getTargetSectionId()))
                .findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        messageUtil.getMessage("section.not.found", userLocale)));
        
        targetSectionName = getSectionName(targetSection, userLocale);
        
        // Get manager info
        User manager = userRepository.findById(UUID.fromString(userId))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        messageUtil.getMessage("user.not.found", userLocale)));
        
        // Select target row in the target section.
        // If targetRowId is provided, use it (and validate it belongs to the target section).
        // Otherwise, default to the first (lowest order) row in the target section.
        RestaurantRow targetRow;
        if (request.getTargetRowId() != null) {
            targetRow = targetSection.getRows().stream()
                    .filter(row -> row.getId().equals(request.getTargetRowId()))
                    .findFirst()
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                            messageUtil.getMessage("row.not.found", userLocale)));
        } else {
            targetRow = targetSection.getRows().stream()
                    .min((r1, r2) -> Integer.compare(r1.getRowOrder(), r2.getRowOrder()))
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST,
                            messageUtil.getMessage("section.no.rows", userLocale)));
        }
        
        // Get the maximum tableOrder in the target row to place moved tables at the end
        int maxTableOrder = targetRow.getTables().stream()
                .filter(t -> !Boolean.TRUE.equals(t.getIsDeleted()))
                .map(RestaurantTable::getTableOrder)
                .filter(Objects::nonNull)
                .mapToInt(Integer::intValue)
                .max()
                .orElse(0); // If row is empty, start from 0 (will become 1)
        
        // Move tables and assign sequential table orders starting from maxTableOrder + 1
        int nextTableOrder = maxTableOrder + 1;
        for (RestaurantTable table : tables) {
            table.setRestaurantRow(targetRow);
            table.setTableOrder(nextTableOrder);
            table.setUpdatedAt(OffsetDateTime.now(ZoneOffset.UTC));
            table.setUpdatedBy(manager);
            nextTableOrder++;
        }
        restaurantTableRepository.saveAll(tables);
        // Flush to ensure changes are persisted immediately
        restaurantTableRepository.flush();
        
        // Clear the persistence context cache to ensure subsequent queries fetch fresh data
        // This is critical when tables are moved to new sections, as TableAssignment entities
        // may have cached references to the old RestaurantTable/RestaurantRow/RestaurantSection
        entityManager.clear();
        
        // Create audit trail for table move section
        try {
            Restaurant restaurant = getRestaurantFromTable(tables.get(0));
            String notes = distinctSourceSectionIds.size() == 1
                    ? String.format("Moved %d table(s) from section '%s' to section '%s'",
                            tables.size(), sourceSectionName, targetSectionName)
                    : String.format("Moved %d table(s) from sections [%s] to section '%s'",
                            tables.size(), sourceSectionName, targetSectionName);
            
            auditTrailService.createAuditTrail(
                    manager,
                    ActionType.TABLE_MOVE_SECTION,
                    restaurant,
                    RequestStatus.NA,
                    null, // ipAddress
                    null, // userAgent
                    request.getTargetSectionId(),
                    "SECTION",
                    notes
            );
        } catch (Exception e) {
            log.error("Failed to create audit trail for table move section: {}", e.getMessage());
        }
        
        // Build response (avoid relying on Lombok builder method generation)
        TableMoveResponse response = new TableMoveResponse();
        response.setMovedTableIds(request.getTableIds());
        response.setSourceSectionId(sourceSectionId);
        response.setTargetSectionId(request.getTargetSectionId());
        response.setSourceSectionName(sourceSectionName);
        response.setTargetSectionName(targetSectionName);
        response.setReason(request.getReason());
        response.setMovedBy(manager.getFirstName() + " " + manager.getLastName());
        response.setMovedAt(LocalDateTime.now(ZoneOffset.UTC));
        
        return ResponseDto.<TableMoveResponse>builder()
                .message(messageUtil.getMessage("tables.moved.success", userLocale))
                .data(response)
                .build();
    }

    /**
     * Raises a table/section change request for HQ review (manager-only).
     * <p>
     * Creates an OPEN request on either a {@link RestaurantTable} or {@link RestaurantSection} by setting request
     * metadata (data/comments/requestedBy/requestedAt) and clearing previous review fields. Notifies HQ admins about
     * the opened request.
     * </p>
     *
     * @param request  request payload identifying entity type (Table/Section), entity id, and request metadata
     * @param userId   acting user id (string UUID)
     * @param userRole acting role (must be MANAGER)
     * @return response wrapper with a localized success message
     * @throws ResponseStatusException when authorization fails or validation fails
     */
    @Override
    @Transactional
    public ResponseDto<Object> raiseTableSectionRequest(TableSectionRequest request, String userId, String userRole) {
        Locale userLocale = LocaleContextHolder.getLocale();
        
        // Only MANAGER can raise table/section requests
        if (!ROLE_MANAGER.equals(userRole)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    messageUtil.getMessage("table.section.request.unauthorized", userLocale));
        }
        
        User manager = userRepository.findById(UUID.fromString(userId))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        messageUtil.getMessage("user.not.found", userLocale)));
        
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        
        // Validate entity type
        if (!"Table".equalsIgnoreCase(request.getEntityType()) && !"Section".equalsIgnoreCase(request.getEntityType())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    messageUtil.getMessage("table.section.request.invalid.entity.type", userLocale));
        }
        
        if ("Table".equalsIgnoreCase(request.getEntityType())) {
            // Handle table request
            RestaurantTable table = restaurantTableRepository.findById(request.getEntityId())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                            messageUtil.getMessage(MSG_TABLE_NOT_FOUND, userLocale)));
            
            // Check if there's already a pending request
            if (table.getTableSectionRequestStatus() == RequestStatus.OPEN) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        messageUtil.getMessage("table.section.request.already.pending", userLocale));
            }
            
            // Create new request - clear review fields from previous APPROVED/DECLINED request
            table.setTableSectionRequestStatus(RequestStatus.OPEN);
            table.setTableSectionRequestData(request.getRequestData());
            table.setTableSectionRequestedAt(now);
            table.setTableSectionRequestedBy(manager);
            table.setTableSectionRequestComments(request.getComments());
            // Clear review fields from previous APPROVED/DECLINED request to ensure this is treated as a new request
            table.setTableSectionReviewedAt(null);
            table.setTableSectionReviewedBy(null);
            table.setUpdatedAt(now);
            table.setUpdatedBy(manager);
            
            restaurantTableRepository.save(table);
            
            // Notify HQ Admins about newly opened table/section request
            try {
                Optional<Role> hqAdminRoleOpt = roleRepository.findByName("HQ_ADMIN");
                if (hqAdminRoleOpt.isPresent()) {
                    UUID hqAdminRoleId = hqAdminRoleOpt.get().getId();
                    Pageable pageable = PageRequest.of(0, 1000); // Get up to 1000 HQ Admins
                    Page<User> hqAdminsPage = userRepository.findAllByRoleIdAndIsDeletedFalse(hqAdminRoleId, pageable);
                    List<User> hqAdmins = hqAdminsPage.getContent();
                    if (!hqAdmins.isEmpty()) {
                        notificationService.notifyTableSectionRequestOpened(table, hqAdmins, userLocale);
                    }
                }
            } catch (Exception e) {
                log.error("Failed to send HQ Admin notification for table/section request: {}", e.getMessage(), e);
            }
        } else if ("Section".equalsIgnoreCase(request.getEntityType())) {
            // Handle section request
            RestaurantSection section = restaurantSectionRepository.findById(request.getEntityId())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                            messageUtil.getMessage("section.not.found", userLocale)));
            
            // Check if there's already a pending request
            if (section.getTableSectionRequestStatus() == RequestStatus.OPEN) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        messageUtil.getMessage("table.section.request.already.pending", userLocale));
            }
            
            // Create new request - clear review fields from previous APPROVED/DECLINED request
            section.setTableSectionRequestStatus(RequestStatus.OPEN);
            section.setTableSectionRequestData(request.getRequestData());
            section.setTableSectionRequestedAt(now);
            section.setTableSectionRequestedBy(manager);
            section.setTableSectionRequestComments(request.getComments());
            // Clear review fields from previous APPROVED/DECLINED request to ensure this is treated as a new request
            section.setTableSectionReviewedAt(null);
            section.setTableSectionReviewedBy(null);
            section.setUpdatedAt(now);
            section.setUpdatedBy(manager);
            
            restaurantSectionRepository.save(section);
            
            // Notify HQ Admins about newly opened table/section request
            try {
                Optional<Role> hqAdminRoleOpt = roleRepository.findByName("HQ_ADMIN");
                if (hqAdminRoleOpt.isPresent()) {
                    UUID hqAdminRoleId = hqAdminRoleOpt.get().getId();
                    Pageable pageable = PageRequest.of(0, 1000); // Get up to 1000 HQ Admins
                    Page<User> hqAdminsPage = userRepository.findAllByRoleIdAndIsDeletedFalse(hqAdminRoleId, pageable);
                    List<User> hqAdmins = hqAdminsPage.getContent();
                    if (!hqAdmins.isEmpty()) {
                        notificationService.notifyTableSectionRequestOpened(section, hqAdmins, userLocale);
                    }
                }
            } catch (Exception e) {
                log.error("Failed to send HQ Admin notification for table/section request: {}", e.getMessage(), e);
            }
        }
        
        return ResponseDto.<Object>builder()
                .message(messageUtil.getMessage("table.section.request.created", userLocale))
                .data(null)
                .build();
    }

    /**
     * Returns a display name for a section using translation fallback rules.
     *
     * @param section section entity
     * @param locale  locale used to pick the preferred translation
     * @return section name, or {@code "NA"} when no suitable translation exists
     */
    private String getSectionName(RestaurantSection section, Locale locale) {
        List<RestaurantSectionTranslation> translations = section.getTranslations();
        if (!translations.isEmpty()) {
            RestaurantSectionTranslation exactMatch = translations.stream()
                    .filter(t -> t.getLanguageCode() != null && t.getLanguageCode().equals(locale.getLanguage()))
                    .findFirst()
                    .orElse(null);
            
            if (exactMatch != null && exactMatch.getName() != null && !exactMatch.getName().trim().isEmpty()) {
                return exactMatch.getName();
            } else {
                // Try fallback for display purposes
                java.util.Optional<RestaurantSectionTranslation> fallback =
                        TranslationUtils.pickPreferredOrFromList(
                                translations,
                                locale.getLanguage(),
                                localizationProperties.getLanguages(),
                                RestaurantSectionTranslation::getLanguageCode
                        );
                // If fallback exists, use it; otherwise return "NA" for display
                return fallback.map(RestaurantSectionTranslation::getName)
                        .filter(name -> name != null && !name.trim().isEmpty())
                        .orElse("NA");
            }
        }
        // No translations at all - return "NA" for display
        return "NA";
    }

    /**
     * Retrieves active waiter-to-table assignments, optionally scoped to the caller's restaurant.
     *
     * @param page   1-based page number (optional)
     * @param size   page size (optional)
     * @param userId optional user id used to resolve restaurant id for scoping (string UUID)
     * @return response wrapper containing assignment responses
     */
    @Override
    @Transactional(readOnly = true)
    public ResponseDto<TableAssignmentWrapper<List<TableAssignmentResponse>>> getActiveWaiterAssignments(Integer page, Integer size, String userId) {
        Locale userLocale = LocaleContextHolder.getLocale();

        // Resolve restaurant ID from user if provided
        UUID restaurantId = null;
        if (userId != null && !userId.trim().isEmpty() && !"null".equalsIgnoreCase(userId)) {
            try {
                User user = userRepository.findById(UUID.fromString(userId)).orElse(null);
                if (user != null && user.getRestaurantId() != null) {
                    restaurantId = user.getRestaurantId();
                    log.debug("Filtering waiter assignments by restaurant ID: {} for user: {}", restaurantId, userId);
                }
            } catch (IllegalArgumentException e) {
                log.warn("Invalid userId format in getActiveWaiterAssignments: {}", userId);
            }
        }

        Sort sort = Sort.by(Sort.Direction.DESC, "assignedAt");
        boolean shouldPage = (page != null && size != null && page > 0 && size > 0);

        List<TableAssignment> assignments;
        if (shouldPage) {
            Page<TableAssignment> paged = tableAssignmentRepository.findByFilter(null, null, restaurantId, PageRequest.of(page - 1, size, sort));
            assignments = paged.getContent();
        } else {
            assignments = tableAssignmentRepository.findByFilter(null, null, restaurantId, sort);
        }

        List<TableAssignmentResponse> responses = assignments.stream()
                .map(a -> TableAssignmentResponse.builder()
                        .id(a.getId())
                        .restaurantTableId(a.getRestaurantTable() != null ? a.getRestaurantTable().getId() : null)
                        .waiterId(a.getWaiter() != null ? a.getWaiter().getId() : null)
                        .assignedAt(a.getAssignedAt() != null ? a.getAssignedAt().toLocalDateTime() : null)
                        .unassignedAt(a.getUnassignedAt() != null ? a.getUnassignedAt().toLocalDateTime() : null)
                        .build())
                .collect(Collectors.toList());

        TableAssignmentWrapper<List<TableAssignmentResponse>> wrapper = TableAssignmentWrapper.<List<TableAssignmentResponse>>builder()
                .tableAssignment(responses)
                .build();

        return ResponseDto.<TableAssignmentWrapper<List<TableAssignmentResponse>>>builder()
                .message(messageUtil.getMessage("table.assignment.list.fetch.success", userLocale))
                .data(wrapper)
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public ResponseDto<TableListResponseDtoV2> getTablesByFiltersV2(
            String waiterId,
            String search,
            String status,
            String sectionId,
            String restaurantId,
            Integer page,
            Integer size) {
        
        log.info("TableServiceImpl.getTablesByFiltersV2 called with restaurantId={}, status={}, sectionId={}, page={}, size={}", 
                restaurantId, status, sectionId, page, size);
        Locale userLocale = LocaleContextHolder.getLocale();
        
        // Clear persistence context cache to ensure fresh data from database
        entityManager.clear();
        
        // Parse restaurantId - make it optional like V1
        UUID restaurantUUID = null;
        if (restaurantId != null && !restaurantId.isBlank()) {
            try {
                restaurantUUID = UUID.fromString(restaurantId);
            } catch (IllegalArgumentException e) {
                String errorMessage = messageUtil.getMessage("error.invalid.restaurantId", userLocale, restaurantId);
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, errorMessage);
            }
        }
        
        // Parse status filter - support both single and multiple statuses (comma-separated) like V1
        // Parse status to separate TableStatus and OrderStatus
        Set<TableStatus> tableStatuses = null;
        Set<OrderStatus> orderStatuses = null;
        if (status != null && !status.isBlank()) {
            Set<String> allStatusValues = Arrays.stream(status.split(","))
                    .map(String::trim)
                    .filter(s -> !s.isBlank())
                    .map(String::toUpperCase)
                    .collect(Collectors.toSet());
            
            Set<TableStatus> parsedTableStatuses = new java.util.HashSet<>();
            Set<OrderStatus> parsedOrderStatuses = new java.util.HashSet<>();
            
            for (String statusValue : allStatusValues) {
                try {
                    TableStatus tableStatus = TableStatus.valueOf(statusValue);
                    parsedTableStatuses.add(tableStatus);
                } catch (IllegalArgumentException e1) {
                    try {
                        OrderStatus orderStatus = OrderStatus.valueOf(statusValue);
                        parsedOrderStatuses.add(orderStatus);
                    } catch (IllegalArgumentException e2) {
                        String errorMessage = messageUtil.getMessage("error.invalid.tableStatus", userLocale, status);
                        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, errorMessage);
                    }
                }
            }
            
            tableStatuses = parsedTableStatuses.isEmpty() ? null : parsedTableStatuses;
            orderStatuses = parsedOrderStatuses.isEmpty() ? null : parsedOrderStatuses;
        }
        
        // Parse sectionId filter
        UUID sectionUUID = null;
        if (sectionId != null && !sectionId.isBlank()) {
            try {
                sectionUUID = UUID.fromString(sectionId);
            } catch (IllegalArgumentException e) {
                String errorMessage = messageUtil.getMessage("error.invalid.sectionId", userLocale, sectionId);
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, errorMessage);
            }
        }
        
        waiterId = (waiterId != null && !waiterId.isBlank()) ? waiterId : null;
        search = (search != null && !search.isBlank()) ? search.trim() : null;
        String searchPattern = (search != null) ? "%" + search + "%" : null;
        
        // Fetch all tables matching filters (v2 doesn't support waiter filtering for now)
        Sort sort = Sort.by(Sort.Direction.ASC, "tableOrder");
        List<RestaurantTable> allTables;
        
        if (search != null) {
            allTables = restaurantTableRepository.findByFiltersWithSearch(
                    tableStatuses,
                    sectionUUID,
                    restaurantUUID,
                    searchPattern,
                    sort);
        } else {
            allTables = restaurantTableRepository.findByFilters(
                    tableStatuses,
                    sectionUUID,
                    restaurantUUID,
                    sort);
        }
        
        // Filter by order status if specified
        if (orderStatuses != null && !orderStatuses.isEmpty()) {
            final Set<OrderStatus> finalOrderStatuses = orderStatuses;
            // Batch fetch order statuses for all tables to avoid N+1 queries
            List<UUID> tableIdsForOrderStatus = allTables.stream()
                    .map(RestaurantTable::getId)
                    .distinct()
                    .collect(Collectors.toList());
            
            Map<UUID, OrderStatus> orderStatusByTableId = getLatestOrderStatusesForTables(tableIdsForOrderStatus);
            
            allTables = allTables.stream()
                    .filter(table -> {
                        OrderStatus latestOrderStatus = orderStatusByTableId.get(table.getId());
                        return latestOrderStatus != null && finalOrderStatuses.contains(latestOrderStatus);
                    })
                    .collect(Collectors.toList());
        }
        
        // Filter out virtual tables from regular tables
        List<RestaurantTable> regularTables = allTables.stream()
                .filter(table -> !Boolean.TRUE.equals(table.getIsVirtual()))
                .collect(Collectors.toList());
        
        log.info("TableServiceImpl: Processing {} regular tables ({} total, {} virtual filtered) for v2 API (restaurantId={}, tableStatuses={}, orderStatuses={}, sectionId={})", 
                regularTables.size(), allTables.size(), allTables.size() - regularTables.size(), restaurantUUID, tableStatuses, orderStatuses, sectionUUID);
        
        // Fetch virtual table separately if restaurantId is provided
        TableResponseV2 virtualTableResponse = null;
        if (restaurantUUID != null) {
            Optional<RestaurantTable> virtualTableOpt = restaurantTableRepository.findVirtualTableByRestaurantId(restaurantUUID);
            if (virtualTableOpt.isPresent()) {
                RestaurantTable virtualTable = virtualTableOpt.get();
                log.info("Found virtual table {} for restaurant {}", virtualTable.getId(), restaurantUUID);
                // Get waiters for virtual table
                List<TableAssignment> virtualTableAssignments = tableAssignmentRepository
                        .findByRestaurantTableIdInAndUnassignedAtIsNullWithWaiter(Arrays.asList(virtualTable.getId()));
                
                Map<UUID, List<WaiterInfo>> virtualWaitersMap = new HashMap<>();
                if (!virtualTableAssignments.isEmpty()) {
                    virtualWaitersMap = virtualTableAssignments.stream()
                            .collect(Collectors.groupingBy(
                                    ta -> ta.getRestaurantTable().getId(),
                                    Collectors.mapping(
                                            ta -> {
                                                User waiter = ta.getWaiter();
                                                return waiter != null ? WaiterInfo.builder()
                                                        .id(waiter.getId())
                                                        .userCode(waiter.getUserCode())
                                                        .firstName(waiter.getFirstName())
                                                        .lastName(waiter.getLastName())
                                                        .tableAssignmentId(ta.getId())
                                                        .build() : null;
                                            },
                                            Collectors.filtering(Objects::nonNull, Collectors.toList())
                                    )
                            ));
                }
                virtualTableResponse = mapToTableResponseV2(virtualTable, userLocale, virtualWaitersMap);
                log.info("Mapped virtual table to response: {}", virtualTableResponse != null ? virtualTableResponse.getId() : "null");
            } else {
                log.warn("Virtual table not found for restaurant {}", restaurantUUID);
            }
        } else {
            log.debug("restaurantUUID is null, skipping virtual table fetch");
        }
        
        // Batch load all waiters for all regular tables to avoid N+1 queries
        List<UUID> tableIds = regularTables.stream()
                .map(RestaurantTable::getId)
                .distinct()
                .collect(Collectors.toList());
        
        Map<UUID, List<WaiterInfo>> waitersByTableId = new HashMap<>();
        if (!tableIds.isEmpty()) {
            List<TableAssignment> allActiveAssignments = tableAssignmentRepository
                    .findByRestaurantTableIdInAndUnassignedAtIsNullWithWaiter(tableIds);
            
            waitersByTableId = allActiveAssignments.stream()
                    .collect(Collectors.groupingBy(
                            ta -> ta.getRestaurantTable().getId(),
                            Collectors.mapping(
                                    ta -> {
                                        User waiter = ta.getWaiter();
                                        return waiter != null ? WaiterInfo.builder()
                                                .id(waiter.getId())
                                                .userCode(waiter.getUserCode())
                                                .firstName(waiter.getFirstName())
                                                .lastName(waiter.getLastName())
                                                .tableAssignmentId(ta.getId())
                                                .build() : null;
                                    },
                                    Collectors.filtering(java.util.Objects::nonNull, Collectors.toList())
                            )
                    ));
        }
        
        final Map<UUID, List<WaiterInfo>> finalWaitersByTableId = waitersByTableId;
        
        // Map tables to TableResponseV2 and create a map for quick lookup (only regular tables, exclude virtual)
        Map<UUID, TableResponseV2> tableResponseMap = new HashMap<>();
        for (RestaurantTable table : regularTables) {
            TableResponseV2 tableResponse = mapToTableResponseV2(table, userLocale, finalWaitersByTableId);
            tableResponseMap.put(table.getId(), tableResponse);
        }
        
        // Group tables by section -> row (only regular tables, exclude virtual)
        Map<UUID, Map<UUID, List<TableResponseV2>>> sectionRowMap = new HashMap<>();
        Map<UUID, RestaurantSection> sectionMap = new HashMap<>();
        Map<UUID, RestaurantRow> rowMap = new HashMap<>();
        
        for (RestaurantTable table : regularTables) {
            RestaurantRow row = table.getRestaurantRow();
            RestaurantSection section = null;
            boolean skip = false;

            if (row == null) {
                log.warn("Table {} has no restaurant row, skipping from grouping", table.getId());
                skip = true;
            } else {
                section = row.getRestaurantSection();
                if (section == null) {
                    log.warn("Table {} row {} has no restaurant section, skipping from grouping", table.getId(), row.getId());
                    skip = true;
                }
            }

            if (skip) {
                continue;
            }

            RestaurantSection resolvedSection = Objects.requireNonNull(section);
            RestaurantRow resolvedRow = Objects.requireNonNull(row);

            sectionMap.put(resolvedSection.getId(), resolvedSection);
            rowMap.put(resolvedRow.getId(), resolvedRow);

            TableResponseV2 tableResponse = tableResponseMap.get(table.getId());
            if (tableResponse != null) {
                sectionRowMap.computeIfAbsent(resolvedSection.getId(), k -> new HashMap<>())
                        .computeIfAbsent(resolvedRow.getId(), k -> new ArrayList<>())
                        .add(tableResponse);
            }
        }
        
        // Build SectionResponseV2 list
        List<SectionResponseV2> sections = new ArrayList<>();
        for (Map.Entry<UUID, Map<UUID, List<TableResponseV2>>> sectionEntry : sectionRowMap.entrySet()) {
            UUID currentSectionId = sectionEntry.getKey();
            RestaurantSection section = sectionMap.get(currentSectionId);
            
            if (section == null) {
                log.warn("Section {} not found in sectionMap, skipping", currentSectionId);
                continue;
            }
            
            // Get section name with fallback
            String sectionName = "NA";
            List<RestaurantSectionTranslation> sectionTranslations = section.getTranslations();
            if (sectionTranslations != null && !sectionTranslations.isEmpty()) {
                RestaurantSectionTranslation exactMatch = sectionTranslations.stream()
                        .filter(t -> t.getLanguageCode() != null && t.getLanguageCode().equals(userLocale.getLanguage()))
                        .findFirst()
                        .orElse(null);
                
                if (exactMatch != null && exactMatch.getName() != null && !exactMatch.getName().trim().isEmpty()) {
                    sectionName = exactMatch.getName();
                } else {
                    Optional<RestaurantSectionTranslation> fallback =
                            TranslationUtils.pickPreferredOrFromList(
                                    sectionTranslations,
                                    userLocale.getLanguage(),
                                    localizationProperties.getLanguages(),
                                    RestaurantSectionTranslation::getLanguageCode
                            );
                    sectionName = fallback.map(RestaurantSectionTranslation::getName)
                            .filter(name -> name != null && !name.trim().isEmpty())
                            .orElse("NA");
                }
            }
            
            // Build rows for this section
            List<RowResponseV2> rows = new ArrayList<>();
            for (Map.Entry<UUID, List<TableResponseV2>> rowEntry : sectionEntry.getValue().entrySet()) {
                UUID rowId = rowEntry.getKey();
                RestaurantRow row = rowMap.get(rowId);
                
                if (row == null) {
                    log.warn("Row {} not found in rowMap, skipping", rowId);
                    continue;
                }
                
                List<TableResponseV2> tables = rowEntry.getValue();
                
                // Sort tables by tableOrder
                tables.sort(Comparator.comparing(TableResponseV2::getTableOrder, 
                        Comparator.nullsLast(Comparator.naturalOrder())));
                
                rows.add(RowResponseV2.builder()
                        .rowId(rowId.toString())
                        .rowOrder(row.getRowOrder())
                        .tables(tables)
                        .build());
            }
            
            // Include rows that have no tables yet (same section may have multiple rows; only rows with
            // tables appeared above). Without this, empty rows are missing from the response and UIs
            // cannot offer them as move targets.
            List<RestaurantRow> allRowsInSection =
                    restaurantRowRepository.findActiveByRestaurantSectionIdOrderByRowOrder(currentSectionId);
            Set<UUID> rowIdsWithTables = rows.stream()
                    .map(r -> UUID.fromString(r.getRowId()))
                    .collect(Collectors.toSet());
            for (RestaurantRow dbRow : allRowsInSection) {
                if (!rowIdsWithTables.contains(dbRow.getId())) {
                    rows.add(RowResponseV2.builder()
                            .rowId(dbRow.getId().toString())
                            .rowOrder(dbRow.getRowOrder())
                            .tables(new ArrayList<>())
                            .build());
                }
            }
            
            // Sort rows by rowOrder
            rows.sort(Comparator.comparing(RowResponseV2::getRowOrder, 
                    Comparator.nullsLast(Comparator.naturalOrder())));
            
            sections.add(SectionResponseV2.builder()
                    .sectionId(currentSectionId.toString())
                    .sectionNumber(section.getSectionOrder())
                    .sectionName(sectionName)
                    .rows(rows)
                    .build());
        }
        
        // Sort sections by sectionNumber
        sections.sort(Comparator.comparing(SectionResponseV2::getSectionNumber, 
                Comparator.nullsLast(Comparator.naturalOrder())));
        
        // Add sections that don't have any tables (blank rows) for live table dashboard
        // This ensures all sections are shown, even if they're empty
        if (restaurantUUID != null) {
            try {
                // Get the layout ID for the restaurant
                Optional<RestaurantLayout> layoutOpt = restaurantLayoutRepository
                        .findByRestaurantIdAndIsDeletedFalse(restaurantUUID);
                
                if (layoutOpt.isPresent()) {
                    UUID layoutId = layoutOpt.get().getId();
                    
                    // Get virtual table's section ID to exclude it from sections
                    UUID virtualSectionId = null;
                    if (virtualTableResponse != null && restaurantUUID != null) {
                        Optional<RestaurantTable> virtualTableOpt = restaurantTableRepository.findVirtualTableByRestaurantId(restaurantUUID);
                        if (virtualTableOpt.isPresent()) {
                            RestaurantTable virtualTable = virtualTableOpt.get();
                            RestaurantRow virtualRow = virtualTable.getRestaurantRow();
                            if (virtualRow != null && virtualRow.getRestaurantSection() != null) {
                                virtualSectionId = virtualRow.getRestaurantSection().getId();
                                log.debug("Found virtual section ID: {} to exclude from sections", virtualSectionId);
                            }
                        }
                    }
                    
                    // Query all sections for this layout (respecting sectionId filter if provided)
                    // Exclude virtual sections (sections containing virtual tables) using NOT EXISTS
                    String sectionQueryStr = "SELECT s FROM RestaurantSection s " +
                            "WHERE s.restaurantLayout.id = :layoutId " +
                            "AND s.isDeleted = false " +
                            "AND NOT EXISTS (" +
                            "    SELECT 1 FROM RestaurantTable t " +
                            "    JOIN t.restaurantRow r " +
                            "    WHERE r.restaurantSection.id = s.id " +
                            "    AND t.isVirtual = true " +
                            "    AND t.isDeleted = false" +
                            ") " +
                            (sectionUUID != null ? "AND s.id = :sectionId " : "") +
                            "ORDER BY s.sectionOrder";
                    
                    jakarta.persistence.TypedQuery<RestaurantSection> sectionQuery = entityManager
                            .createQuery(sectionQueryStr, RestaurantSection.class)
                            .setParameter("layoutId", layoutId);
                    if (sectionUUID != null) {
                        sectionQuery.setParameter("sectionId", sectionUUID);
                    }
                    List<RestaurantSection> allSections = sectionQuery.getResultList();
                    
                    // Also filter out virtual section by ID if we found it (additional safety check)
                    final UUID finalVirtualSectionId = virtualSectionId;
                    if (finalVirtualSectionId != null) {
                        allSections = allSections.stream()
                                .filter(s -> !s.getId().equals(finalVirtualSectionId))
                                .collect(Collectors.toList());
                        log.debug("Filtered out virtual section {} from allSections", finalVirtualSectionId);
                    }
                    
                    // Create a set of section IDs that already have tables
                    Set<UUID> sectionsWithTables = sectionRowMap.keySet();
                    
                    // Fetch rows for all sections that don't have tables
                    List<RestaurantSection> sectionsWithoutTables = allSections.stream()
                            .filter(s -> !sectionsWithTables.contains(s.getId()))
                            .collect(Collectors.toList());
                    
                    if (!sectionsWithoutTables.isEmpty()) {
                        List<UUID> sectionIdsWithoutTables = sectionsWithoutTables.stream()
                                .map(RestaurantSection::getId)
                                .collect(Collectors.toList());
                        
                        // Fetch rows for sections without tables
                        String rowsQueryStr = "SELECT r FROM RestaurantRow r " +
                                "WHERE r.restaurantSection.id IN :" + PARAM_SECTION_IDS + " " +
                                "AND r.isDeleted = false " +
                                "ORDER BY r.restaurantSection.id, r.rowOrder";
                        List<RestaurantRow> allRowsForBlankSections = entityManager
                                .createQuery(rowsQueryStr, RestaurantRow.class)
                                .setParameter(PARAM_SECTION_IDS, sectionIdsWithoutTables)
                                .getResultList();
                        
                        // Group rows by section
                        Map<UUID, List<RestaurantRow>> rowsBySection = allRowsForBlankSections.stream()
                                .collect(Collectors.groupingBy(r -> r.getRestaurantSection().getId()));
                        
                        // Fetch translations for sections without tables
                        String translationsQueryStr = "SELECT t FROM RestaurantSectionTranslation t " +
                                "WHERE t.restaurantSection.id IN :sectionIds";
                        List<RestaurantSectionTranslation> allTranslations = entityManager
                                .createQuery(translationsQueryStr, RestaurantSectionTranslation.class)
                                .setParameter(PARAM_SECTION_IDS, sectionIdsWithoutTables)
                                .getResultList();
                        
                        // Group translations by section
                        Map<UUID, List<RestaurantSectionTranslation>> translationsBySection = allTranslations.stream()
                                .collect(Collectors.groupingBy(t -> t.getRestaurantSection().getId()));
                        
                        // Add sections without tables with their rows (but empty tables list)
                        for (RestaurantSection section : sectionsWithoutTables) {
                            // Get section name with fallback
                            String sectionName = "NA";
                            List<RestaurantSectionTranslation> sectionTranslations = translationsBySection.get(section.getId());
                            if (sectionTranslations != null && !sectionTranslations.isEmpty()) {
                                RestaurantSectionTranslation exactMatch = sectionTranslations.stream()
                                        .filter(t -> t.getLanguageCode() != null && t.getLanguageCode().equals(userLocale.getLanguage()))
                                        .findFirst()
                                        .orElse(null);
                                
                                if (exactMatch != null && exactMatch.getName() != null && !exactMatch.getName().trim().isEmpty()) {
                                    sectionName = exactMatch.getName();
                                } else {
                                    Optional<RestaurantSectionTranslation> fallback =
                                            TranslationUtils.pickPreferredOrFromList(
                                                    sectionTranslations,
                                                    userLocale.getLanguage(),
                                                    localizationProperties.getLanguages(),
                                                    RestaurantSectionTranslation::getLanguageCode
                                            );
                                    sectionName = fallback.map(RestaurantSectionTranslation::getName)
                                            .filter(name -> name != null && !name.trim().isEmpty())
                                            .orElse("NA");
                                }
                            }
                            
                            // Build rows for this section (with empty tables list)
                            List<RowResponseV2> rows = new ArrayList<>();
                            List<RestaurantRow> sectionRows = rowsBySection.getOrDefault(section.getId(), new ArrayList<>());
                            
                            for (RestaurantRow row : sectionRows) {
                                rows.add(RowResponseV2.builder()
                                        .rowId(row.getId().toString())
                                        .rowOrder(row.getRowOrder())
                                        .tables(new ArrayList<>()) // Empty tables list for blank rows
                                        .build());
                            }
                            
                            // Sort rows by rowOrder
                            rows.sort(Comparator.comparing(RowResponseV2::getRowOrder, 
                                    Comparator.nullsLast(Comparator.naturalOrder())));
                            
                            // Add section with blank rows
                            sections.add(SectionResponseV2.builder()
                                    .sectionId(section.getId().toString())
                                    .sectionNumber(section.getSectionOrder())
                                    .sectionName(sectionName)
                                    .rows(rows)
                                    .build());
                        }
                        
                        // Re-sort sections by sectionNumber after adding blank sections
                        sections.sort(Comparator.comparing(SectionResponseV2::getSectionNumber, 
                                Comparator.nullsLast(Comparator.naturalOrder())));
                    }
                }
            } catch (Exception e) {
                log.warn("Failed to fetch blank sections for restaurant {}: {}", restaurantUUID, e.getMessage(), e);
                // Continue without blank sections if there's an error
            }
        }
        
        // Calculate total count (before pagination)
        long totalCount = tableResponseMap.size();
        
        // Apply pagination after grouping
        // Note: Pagination is applied after grouping, which may result in partial sections/rows
        boolean shouldPage = (page != null && size != null && page > 0 && size > 0);
        
        if (shouldPage) {
            // Flatten all tables from the grouped structure into a sorted list
            List<TableResponseV2> allTablesFlat = new ArrayList<>();
            for (SectionResponseV2 section : sections) {
                for (RowResponseV2 row : section.getRows()) {
                    allTablesFlat.addAll(row.getTables());
                }
            }
            
            // Apply pagination to the flat list
            int fromIndex = (page - 1) * size;
            int toIndex = Math.min(fromIndex + size, allTablesFlat.size());
            List<TableResponseV2> paginatedTables;
            if (fromIndex < allTablesFlat.size()) {
                paginatedTables = allTablesFlat.subList(fromIndex, toIndex);
            } else {
                paginatedTables = new ArrayList<>();
            }
            
            // Create a set of paginated table IDs for quick lookup
            Set<UUID> paginatedTableIds = paginatedTables.stream()
                    .map(t -> UUID.fromString(t.getId()))
                    .collect(Collectors.toSet());
            
            // Rebuild sections/rows structure with only paginated tables
            // Keep sections/rows with empty tables (blank rows) for live table dashboard
            List<SectionResponseV2> paginatedSections = new ArrayList<>();
            for (SectionResponseV2 section : sections) {
                List<RowResponseV2> paginatedRows = new ArrayList<>();
                for (RowResponseV2 row : section.getRows()) {
                    List<TableResponseV2> rowTables = row.getTables().stream()
                            .filter(t -> paginatedTableIds.contains(UUID.fromString(t.getId())))
                            .collect(Collectors.toList());
                    
                    // Include rows that have paginated tables OR rows with empty tables (blank rows)
                    boolean hasPaginatedTables = !rowTables.isEmpty();
                    boolean isEmptyRow = row.getTables() == null || row.getTables().isEmpty();
                    
                    if (hasPaginatedTables || isEmptyRow) {
                        paginatedRows.add(RowResponseV2.builder()
                                .rowId(row.getRowId())
                                .rowOrder(row.getRowOrder())
                                .tables(hasPaginatedTables ? rowTables : new ArrayList<>())
                                .build());
                    }
                }
                
                // Include sections that have paginated tables OR sections with empty rows (blank sections)
                // Check original section for empty rows, as they should always be included
                boolean hasPaginatedRows = !paginatedRows.isEmpty();
                boolean hasEmptyRows = section.getRows().stream()
                        .anyMatch(r -> r.getTables() == null || r.getTables().isEmpty());
                
                if (hasPaginatedRows || hasEmptyRows) {
                    paginatedSections.add(SectionResponseV2.builder()
                            .sectionId(section.getSectionId())
                            .sectionNumber(section.getSectionNumber())
                            .sectionName(section.getSectionName())
                            .rows(paginatedRows)
                            .build());
                }
            }
            
            sections = paginatedSections;
        }
        
        // Build pagination metadata
        PaginationMetaData metaData = null;
        if (shouldPage) {
            int totalPages = (int) Math.ceil((double) totalCount / size);
            metaData = PaginationMetaData.builder()
                    .page(page)
                    .size(size)
                    .totalPages(totalPages)
                    .totalRecords(totalCount)
                    .build();
        }
        
        // Calculate count of tables in the response (may be less than total if paginated)
        long responseCount = sections.stream()
                .mapToLong(s -> s.getRows().stream()
                        .mapToLong(r -> r.getTables().size())
                        .sum())
                .sum();
        
        TableListResponseDtoV2 dto = TableListResponseDtoV2.builder()
                .virtualTable(virtualTableResponse) // Virtual table at the top
                .sections(sections)
                .count(responseCount)
                .total(totalCount)
                .metaData(metaData)
                .build();
        
        return ResponseDto.<TableListResponseDtoV2>builder()
                .message(messageUtil.getMessage(MSG_TABLE_LIST_FETCH_SUCCESS, userLocale))
                .data(dto)
                .build();
    }
    
    /**
     * Map RestaurantTable to TableResponseV2
     */
    private TableResponseV2 mapToTableResponseV2(RestaurantTable table, Locale locale, Map<UUID, List<WaiterInfo>> waitersByTableId) {
        // Determine live order status from latest order across active sessions
        List<Session> activeSessions = sessionRepository.findByTableIdAndExpiredAtIsNull(table.getId());
        int readyItems = 0;
        int pendingItems = 0;
        List<TableSessionInfo> sessions = new ArrayList<>();
        
        if (!activeSessions.isEmpty()) {
            List<UUID> sessionIds = activeSessions.stream()
                    .map(Session::getId)
                    .collect(Collectors.toList());
            
            // Fetch item counts
            Long readyItemsCount = orderedItemRepository.countReadyItemsByTableId(table.getId());
            Long pendingItemsCount = orderedItemRepository.countPendingItemsByTableId(table.getId());
            
            readyItems = readyItemsCount != null ? readyItemsCount.intValue() : 0;
            pendingItems = pendingItemsCount != null ? pendingItemsCount.intValue() : 0;
            
            // Batch fetch all orders with ordered items for all active sessions
            List<Order> allOrders = orderRepository.findBySessionIdsWithOrderedItems(sessionIds);
            
            // Group orders by session ID
            Map<UUID, List<Order>> ordersBySession = new HashMap<>();
            for (Order order : allOrders) {
                if (order.getSession() == null) {
                    log.warn("Order {} has no session, skipping", order.getId());
                    continue;
                }
                UUID sessionId = order.getSession().getId();
                ordersBySession.computeIfAbsent(sessionId, k -> new ArrayList<>()).add(order);
            }
            
            // Build session info list - get latest order for each session
            for (Session session : activeSessions) {
                List<Order> sessionOrders = ordersBySession.get(session.getId());
                OffsetDateTime sessionEndTime = resolveSessionEndTime(sessionOrders);
                if (sessionOrders != null && !sessionOrders.isEmpty()) {
                    // Sort by createdAt DESC to get the latest order
                    sessionOrders.sort((o1, o2) -> {
                        if (o1.getCreatedAt() == null && o2.getCreatedAt() == null) return 0;
                        if (o1.getCreatedAt() == null) return 1;
                        if (o2.getCreatedAt() == null) return -1;
                        return o2.getCreatedAt().compareTo(o1.getCreatedAt());
                    });
                    Order latestOrder = sessionOrders.get(0);
                    
                    BigDecimal orderSubtotal = latestOrder.getSubTotal();
                    
                    TableSessionInfo sessionInfo = TableSessionInfo.builder()
                            .sessionId(session.getId())
                            .sequenceNo(session.getSequenceNo())
                            .orderId(latestOrder.getId())
                            .orderNumber(latestOrder.getOrderNumber())
                            .orderStatus(latestOrder.getOrderStatus() != null ? latestOrder.getOrderStatus().name() : null)
                            .orderSubtotal(orderSubtotal)
                            .startTime(session.getIssuedAt())
                            .endTime(sessionEndTime)
                            .build();
                    sessions.add(sessionInfo);
                } else {
                    // Session exists but no orders yet
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
        }
        
        // Get waiters from pre-loaded map
        List<WaiterInfo> waiters = waitersByTableId.getOrDefault(table.getId(), new ArrayList<>());
        
        return TableResponseV2.builder()
                .id(table.getId().toString())
                .tableCode(table.getTableCode())
                .tableOrder(table.getTableOrder())
                .capacity(table.getCapacity())
                .tableStatus(table.getTableStatus() != null ? table.getTableStatus().name() : null)
                .blockReason(table.getBlockReason())
                .readyItems(readyItems)
                .pendingItems(pendingItems)
                .sessions(sessions)
                .waiters(waiters)
                .build();
    }

    /**
     * Batch fetch the latest order status for multiple tables.
     * Returns a map of tableId -> latest order status.
     * This is optimized to avoid N+1 queries by fetching all order statuses in a single query.
     */
    private Map<UUID, OrderStatus> getLatestOrderStatusesForTables(List<UUID> tableIds) {
        if (tableIds == null || tableIds.isEmpty()) {
            return new HashMap<>();
        }
        
        // Batch fetch all orders for all tables in one query
        // The query orders by tableId, then createdAt DESC, so we get latest order first for each table
        List<Order> allOrders = orderRepository.findByTableIdsWithActiveSessions(tableIds);
        
        if (allOrders == null || allOrders.isEmpty()) {
            return new HashMap<>();
        }
        
        // Group by table ID and get the latest order for each table
        // Since orders are sorted by tableId, then createdAt DESC, 
        // the first order we encounter for each table is the latest
        Map<UUID, OrderStatus> orderStatusByTableId = new HashMap<>();
        
        for (Order order : allOrders) {
            if (order == null || order.getRestaurantTable() == null) {
                continue;
            }
            UUID tableId = order.getRestaurantTable().getId();
            // Only add if we haven't seen this table yet (first = latest due to sorting)
            if (!orderStatusByTableId.containsKey(tableId) && order.getOrderStatus() != null) {
                orderStatusByTableId.put(tableId, order.getOrderStatus());
            }
        }
        
        return orderStatusByTableId;
    }

    /**
     * Determine when a session effectively ended.
     * Returns the latest timestamp (updatedAt fallback to createdAt) of any order in the session
     * that is fully completed (status SERVED or CANCELED) and where all its items are in a final state.
     */
    private OffsetDateTime resolveSessionEndTime(List<Order> sessionOrders) {
        if (sessionOrders == null || sessionOrders.isEmpty()) {
            return null;
        }

        OffsetDateTime endTime = null;
        for (Order order : sessionOrders) {
            boolean skip = order == null
                    || order.getOrderStatus() == null
                    || (order.getOrderStatus() != OrderStatus.SERVED && order.getOrderStatus() != OrderStatus.CANCELED);
            if (skip) {
                continue;
            }

            boolean allItemsCompleted = true;
            if (order.getOrderedItems() != null && !order.getOrderedItems().isEmpty()) {
                allItemsCompleted = order.getOrderedItems().stream()
                        .filter(java.util.Objects::nonNull)
                        .allMatch(oi -> oi.getItemStatus() == ItemStatus.SERVED || oi.getItemStatus() == ItemStatus.CANCELED);
            }

            if (allItemsCompleted) {
                OffsetDateTime candidate = order.getUpdatedAt() != null ? order.getUpdatedAt() : order.getCreatedAt();
                if (candidate != null && (endTime == null || candidate.isAfter(endTime))) {
                    endTime = candidate;
                }
            }
        }

        return endTime;
    }

    private static LocalDateTime resolveOccupiedAt(List<Session> activeSessions) {
        if (activeSessions == null || activeSessions.isEmpty()) {
            return null;
        }
        return activeSessions.stream()
                .map(Session::getIssuedAt)
                .filter(Objects::nonNull)
                .min(OffsetDateTime::compareTo)
                .map(OffsetDateTime::toLocalDateTime)
                .orElse(null);
    }

    private static List<UUID> toSessionIds(List<Session> activeSessions) {
        if (activeSessions == null || activeSessions.isEmpty()) {
            return new ArrayList<>();
        }
        return activeSessions.stream().map(Session::getId).collect(Collectors.toList());
    }


    /**
     * Build order details string from list of orders.
     */
    private String buildOrderDetailsString(List<Order> activeOrders) {
        if (activeOrders.isEmpty()) {
            return "none";
        }
        return activeOrders.stream()
                .filter(Objects::nonNull)
                .map(o -> {
                    String orderNumber = o.getOrderNumber() != null ? o.getOrderNumber() : "null";
                    return String.format("Order[id=%s, status=%s, orderNumber=%s]",
                            o.getId(), o.getOrderStatus(), orderNumber);
                })
                .collect(Collectors.joining(", "));
    }

    private UUID resolveRestaurantIdForTable(RestaurantTable table, UUID tableId, Locale locale) {
        try {
            RestaurantRow row = table.getRestaurantRow();
            if (row == null) {
                throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                        messageUtil.getMessage(MSG_RESTAURANT_ID_RESOLVE_FAILED, locale));
            }
            RestaurantSection section = row.getRestaurantSection();
            if (section == null) {
                throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                        messageUtil.getMessage(MSG_RESTAURANT_ID_RESOLVE_FAILED, locale));
            }
            RestaurantLayout layout = section.getRestaurantLayout();
            if (layout == null || layout.getRestaurant() == null || layout.getRestaurant().getId() == null) {
                throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                        messageUtil.getMessage(MSG_RESTAURANT_ID_RESOLVE_FAILED, locale));
            }
            return layout.getRestaurant().getId();
        } catch (ResponseStatusException e) {
            throw e;
        } catch (NullPointerException e) {
            log.error("Null pointer encountered while resolving restaurant ID for table {}: {}", tableId, e.getMessage(), e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    messageUtil.getMessage(MSG_RESTAURANT_ID_RESOLVE_FAILED, locale));
        } catch (Exception e) {
            log.error("Unexpected error while resolving restaurant ID for table {}: {}", tableId, e.getMessage(), e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    messageUtil.getMessage(MSG_RESTAURANT_ID_RESOLVE_FAILED, locale));
        }
    }

    private String buildStaticQrScanTarget(UUID restaurantId, UUID tableId, RestaurantTable table) {
        if (Boolean.TRUE.equals(table.getIsVirtual())) {
            return String.format("%s/customer/r/%s/%s?isVirtual=true", appProperties.getBaseUrl(), restaurantId, tableId);
        }
        return String.format("%s/customer/r/%s/%s", appProperties.getBaseUrl(), restaurantId, tableId);
    }

    private void generateUploadPngAndSaveTable(RestaurantTable table, UUID restaurantId, UUID tableId)
            throws WriterException, java.io.IOException {
        String qrContent = buildStaticQrScanTarget(restaurantId, tableId, table);
        QRCodeWriter qrCodeWriter = new QRCodeWriter();
        var bitMatrix = qrCodeWriter.encode(qrContent, BarcodeFormat.QR_CODE, 250, 250);
        BufferedImage qrImage = MatrixToImageWriter.toBufferedImage(bitMatrix);

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(qrImage, "png", baos);
        byte[] bytes = baos.toByteArray();
        InputStream inputStream = new ByteArrayInputStream(bytes);

        String fileName = "table-id_" + tableId + ".png";
        String s3Key = "qr-codes/" + restaurantId + "/" + fileName;

        awsService.uploadFile(inputStream, s3Key, bytes.length);

        table.setQrCodeUrl(s3Key);
        restaurantTableRepository.save(table);
    }

    /**
     * Ensures static QR PNG and print PDF exist in storage and DB when either reference is missing.
     */
    private void ensureTableQrPngAndPdfIfMissing(RestaurantTable table, UUID tableId, Locale locale) {
        boolean needsPng = table.getQrCodeUrl() == null || table.getQrCodeUrl().isBlank();
        boolean needsPdf = table.getPrintQrCodeUrl() == null || table.getPrintQrCodeUrl().isBlank();
        if (!needsPng && !needsPdf) {
            return;
        }
        UUID restaurantId = resolveRestaurantIdForTable(table, tableId, locale);
        try {
            if (needsPng) {
                generateUploadPngAndSaveTable(table, restaurantId, tableId);
            }
            if (needsPng || needsPdf) {
                generateAndUploadQrCodePdf(restaurantId, tableId, table);
            }
        } catch (WriterException | java.io.IOException e) {
            log.error("On-demand QR PNG generation failed for table {}: {}", tableId, e.getMessage(), e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    messageUtil.getMessage("table.qr.regenerate.failed", locale));
        }
    }

    /**
     * Generate and upload QR code PDF for a table.
     * Failures are logged but do not propagate to avoid failing the main QR code regeneration flow.
     */
    private void generateAndUploadQrCodePdf(UUID restaurantId, UUID tableId, RestaurantTable table) {
        try {
            Restaurant restaurant = restaurantRepository.findById(restaurantId).orElse(null);
            if (restaurant != null) {
                String pdfUrl = printQrCodeService.generateQrCodePdf(restaurant, table);
                table.setPrintQrCodeUrl(pdfUrl);
                restaurantTableRepository.save(table);
                log.info("QR code PDF generated and uploaded for table {}: {}", tableId, pdfUrl);
            } else {
                log.warn("Restaurant not found for ID {} when generating QR code PDF for table {}", restaurantId, tableId);
            }
        } catch (Exception e) {
            log.error("Failed to generate QR code PDF for table {}: {}", tableId, e.getMessage(), e);
            // Don't fail the QR code regeneration if PDF generation fails
        }
    }

    /**
     * Create audit trail entry for QR code generation.
     * Failures are logged but do not propagate.
     */
    private void createQrGenerationAuditTrail(UUID restaurantId, UUID tableId, RestaurantTable table, String userId) {
        try {
            User manager = userRepository.findById(UUID.fromString(userId)).orElse(null);
            if (manager != null) {
                Restaurant restaurant = restaurantRepository.findById(restaurantId).orElse(null);
                auditTrailService.createAuditTrail(
                        manager,
                        ActionType.TABLE_QR_GENERATE,
                        restaurant,
                        RequestStatus.NA,
                        null, null,
                        tableId,
                        "TABLE",
                        "QR code regenerated for table " + table.getTableOrder()
                );
            }
        } catch (Exception e) {
            log.error("Failed to create audit trail for QR generation: {}", e.getMessage());
        }
    }
}
