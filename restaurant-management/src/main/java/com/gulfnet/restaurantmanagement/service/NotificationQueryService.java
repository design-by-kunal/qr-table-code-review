package com.gulfnet.restaurantmanagement.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gulfnet.restaurantmanagement.util.MessageUtil;
import com.gulfnet.shared_library.entity.Notification;
import com.gulfnet.shared_library.entity.User;
import com.gulfnet.shared_library.model.response.dto.NotificationFilterTypesResponseDto;
import com.gulfnet.shared_library.model.response.dto.NotificationResponseDto;
import com.gulfnet.shared_library.model.response.dto.PaginationMetaData;
import com.gulfnet.shared_library.model.response.dto.ResponseDto;
import com.gulfnet.shared_library.repository.NotificationRepository;
import com.gulfnet.shared_library.repository.UserRepository;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationQueryService {

    private static final String DEFAULT_SORT_BY = "date";
    private static final String DEFAULT_SORT_DIRECTION = "DESC";
    private static final String ADDITIONAL_TARGET_KDS_IDS = "targetKdsIds";
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    /** Pattern to strip HQ threshold alert dedup suffix from message when returning to UI (stored as " [rid:uuid]"). */
    private static final Pattern ALERT_DEDUP_SUFFIX = Pattern.compile(" \\[rid:[^]]+\\]$");

    // Notification type constants (used across multiple category/filter maps)
    private static final String TYPE_CANCELLATION_APPROVED = "CANCELLATION_APPROVED";
    private static final String TYPE_CANCELLATION_REJECTED = "CANCELLATION_REJECTED";
    private static final String TYPE_ITEM_CANCELED = "ITEM_CANCELED";
    private static final String TYPE_ITEM_CANCELLATION_REQUEST = "ITEM_CANCELLATION_REQUEST";
    private static final String TYPE_ITEM_CANCELLATION_REQUEST_APPROVED = "ITEM_CANCELLATION_REQUEST_APPROVED";
    private static final String TYPE_ITEM_CANCELLATION_REQUEST_DECLINED = "ITEM_CANCELLATION_REQUEST_DECLINED";
    private static final String TYPE_ORDER_CANCELLED_BY_MANAGER = "ORDER_CANCELLED_BY_MANAGER";
    private static final String TYPE_REFUND_REQUEST_APPROVED = "REFUND_REQUEST_APPROVED";
    private static final String TYPE_REFUND_REQUEST_DECLINED = "REFUND_REQUEST_DECLINED";
    private static final String TYPE_CASH_DRAWER_SHIFT_DISCREPANCY_REQUEST = "CASH_DRAWER_SHIFT_DISCREPANCY_REQUEST";
    private static final String TYPE_CASH_DRAWER_SHIFT_DISCREPANCY_APPROVED = "CASH_DRAWER_SHIFT_DISCREPANCY_APPROVED";
    private static final String TYPE_CASH_DRAWER_SHIFT_DISCREPANCY_DECLINED = "CASH_DRAWER_SHIFT_DISCREPANCY_DECLINED";

    // Status filter constant
    private static final String STATUS_UNREAD = "unread";

    private static final String FILTER_ID_SALES_THRESHOLD_ALERT = "sales-threshold-alert";
    private static final String FILTER_ID_REFUND_PERCENTAGE_ALERT = "refund-percentage-alert";
    private static final String FILTER_ID_CANCELLED_TRANSACTIONS_PERCENTAGE_ALERT = "cancelled-transactions-percentage-alert";

    private static final String TYPE_ITEM_CANCELLATION_APPROVED = "ITEM_CANCELLATION_APPROVED";
    private static final String TYPE_ITEM_CANCELLATION_DECLINED = "ITEM_CANCELLATION_DECLINED";
    private static final String TYPE_ORDER_CANCELLATION_APPROVED = "ORDER_CANCELLATION_APPROVED";
    private static final String TYPE_ORDER_CANCELLATION_DECLINED = "ORDER_CANCELLATION_DECLINED";

    /** Filter IDs for HQ threshold alerts; managers do not receive these, so exclude from manager's type filter. */
    private static final Set<String> THRESHOLD_ALERT_FILTER_IDS = Set.of(
            FILTER_ID_SALES_THRESHOLD_ALERT,
            FILTER_ID_REFUND_PERCENTAGE_ALERT,
            FILTER_ID_CANCELLED_TRANSACTIONS_PERCENTAGE_ALERT);
    
    // Cancellation-related notification types that should have "Your" replaced for KDS users
    private static final Set<String> CANCELLATION_NOTIFICATION_TYPES = Set.of(
            TYPE_ITEM_CANCELLATION_APPROVED,
            TYPE_ITEM_CANCELLATION_DECLINED,
            TYPE_ITEM_CANCELLATION_REQUEST_APPROVED,
            TYPE_ITEM_CANCELLATION_REQUEST_DECLINED,
            "COMBO_CANCELLATION_REQUEST_APPROVED",
            "COMBO_CANCELLATION_REQUEST_DECLINED",
            TYPE_ORDER_CANCELLATION_APPROVED,
            TYPE_ORDER_CANCELLATION_DECLINED,
            TYPE_CANCELLATION_APPROVED,
            TYPE_CANCELLATION_REJECTED
    );

    /**
     * Maps each notification type to its i18n title message key so that titles are
     * always resolved in the viewer's locale at read time, not the creator's locale.
     */
    private static final Map<String, String> NOTIFICATION_TYPE_TITLE_KEY_MAP = buildNotificationTypeTitleKeyMap();

    @SuppressWarnings("java:S1192")
    /**
     * Builds the static notification-type to i18n title-key map.
     * <p>
     * The map stores message keys (not resolved titles) so titles can be resolved in the viewer's locale at read time.
     * </p>
     *
     * @return map of notification type → message key for title resolution
     */
    private static Map<String, String> buildNotificationTypeTitleKeyMap() {
        Map<String, String> m = new HashMap<>();
        // Order notifications
        m.put("ORDER_PLACED", "notification.order.placed.title");
        m.put("ORDER_UPDATED", "notification.order.updated.title");
        m.put("ORDER_STATUS_UPDATE", "notification.order.updated.title");
        m.put("ORDER_UPDATE", "notification.order.update.title");
        m.put("ORDER_CANCELLED", "notification.order.cancelled.title");
        m.put("ORDER_CANCELED", "notification.order.cancelled.title");
        // Item notifications
        m.put("ITEM_READY", "notification.item.ready.title");
        m.put("ITEM_SERVED", "notification.kds.served.title");
        m.put("ITEM_DELAYED", "notification.item.delayed.title");
        m.put("ITEM_CANCELED", "notification.item.cancelled.title");
        m.put("ITEM_PUSHED", "notification.item.pushed.title");
        m.put("ITEM_STATUS_UPDATE", "notification.item.status.update.title");
        m.put("KDS_COOKING", "notification.kds.cooking.title");
        m.put("KDS_READY", "notification.kds.ready.title");
        m.put("KDS_DELAYED", "notification.kds.delayed.title");
        // Table notifications
        m.put("TABLE_ASSIGNED", "notification.table.assigned.title");
        m.put("TABLE_REMOVED", "notification.table.removed.title");
        m.put("GUEST_TRANSFER", "notification.guest.transfer.title");
        // Payment notifications
        m.put("PAYMENT_COMPLETED", "notification.payment.completed.title");
        m.put("PAYMENT_ERROR", "notification.payment.error.title");
        m.put("PAYMENT_FAILED", "notification.payment.failed.title");
        m.put("PAYMENT_EXPIRED", "notification.payment.expired.title");
        // Cancellation approval/rejection
        m.put("CANCELLATION_APPROVED", "notification.cancellation.approved.title");
        m.put("CANCELLATION_REJECTED", "notification.cancellation.rejected.title");
        m.put("ITEM_CANCELLATION_APPROVED", "notification.item.cancellation.approved.title");
        m.put("ITEM_CANCELLATION_DECLINED", "notification.item.cancellation.declined.title");
        m.put("ITEM_CANCELLATION_REQUEST_APPROVED", "notification.item.cancellation.approved.title");
        m.put("ITEM_CANCELLATION_REQUEST_DECLINED", "notification.item.cancellation.declined.title");
        m.put("ORDER_CANCELLATION_APPROVED", "notification.order.cancellation.approved.title");
        m.put("ORDER_CANCELLATION_DECLINED", "notification.order.cancellation.declined.title");
        m.put("TRANSACTION_CANCELLATION_APPROVED", "notification.transaction.cancellation.approved.title");
        m.put("TRANSACTION_CANCELLATION_REJECTED", "notification.transaction.cancellation.rejected.title");
        // Manager cancel request notifications
        m.put("CANCEL_REQUEST_OPENED", "manager.notification.cancel.request.title");
        m.put("ITEM_CANCELLATION_REQUEST", "manager.notification.cancel.request.title");
        m.put("COMBO_CANCELLATION_REQUEST", "manager.notification.combo.cancel.request.title");
        m.put("TRANSACTION_CANCELLATION_REQUEST", "manager.notification.transaction.cancel.request.title");
        m.put("ORDER_CANCELLATION_REQUEST", "manager.notification.order.cancel.request.title");
        m.put("MANAGER_ITEM_CANCELED_NOTIFICATION", "manager.notification.item.canceled.title");
        // Transaction / order cancelled
        m.put("TRANSACTION_CANCELLED_FOR_WAITER", "notification.transaction.cancelled.for.waiter.title");
        m.put("ORDER_CANCELLED_BY_MANAGER", "notification.order.cancelled.by.manager.title");
        // Password
        m.put("PASSWORD_UPDATED", "notification.password.updated.title");
        // Profile update requests
        m.put("PROFILE_UPDATE_REQUEST_OPENED", "notification.profile.update.request.opened.title");
        m.put("PROFILE_UPDATE_REQUEST", "notification.user.update.title");
        m.put("PROFILE_UPDATE_REQUEST_CREATED", "notification.profile.update.request.created.title");
        m.put("PROFILE_UPDATE_REQUEST_APPROVED", "notification.profile.update.request.approved.title");
        m.put("PROFILE_UPDATE_REQUEST_DECLINED", "notification.profile.update.request.declined.title");
        m.put("PROFILE_UPDATED_DIRECTLY", "notification.profile.updated.directly.title");
        // Employee
        m.put("EMPLOYEE_ASSIGNED_TO_RESTAURANT", "notification.employee.assigned.to.restaurant.title");
        // Table/Section requests
        m.put("TABLE_SECTION_REQUEST_OPENED", "notification.table.section.request.opened.title");
        m.put("TABLE_SECTION_REQUEST_CREATED", "notification.table.section.request.created.title");
        m.put("TABLE_SECTION_REQUEST_APPROVED", "notification.table.section.request.approved.title");
        m.put("TABLE_SECTION_REQUEST_DECLINED", "notification.table.section.request.declined.title");
        // Additional discount
        m.put("ADDITIONAL_DISCOUNT_REQUEST_OPENED", "notification.additional.discount.request.opened.title");
        m.put("ADDITIONAL_DISCOUNT_REQUEST_APPROVED", "notification.additional.discount.request.approved.title");
        m.put("ADDITIONAL_DISCOUNT_REQUEST_DECLINED", "notification.additional.discount.request.declined.title");
        m.put("DISCOUNT_REQUEST_APPROVED", "notification.discount.request.approved.title");
        m.put("DISCOUNT_REQUEST_DECLINED", "notification.discount.request.declined.title");
        // Refund
        m.put("REFUND_REQUEST", "manager.notification.refund.request.title");
        m.put("REFUND_REQUEST_APPROVED", "notification.refund.request.approved.title");
        m.put("REFUND_REQUEST_DECLINED", "notification.refund.request.declined.title");
        // Device
        m.put("DEVICE_INTEGRATION_ERROR", "notification.device.integration.error.title");
        // Cash drawer
        m.put("CASH_DRAWER_SHIFT_DISCREPANCY_REQUEST", "notification.cash.drawer.shift.discrepancy.request.title");
        m.put("CASH_DRAWER_SHIFT_DISCREPANCY_APPROVED", "notification.cash.drawer.shift.discrepancy.approved.title");
        m.put("CASH_DRAWER_SHIFT_DISCREPANCY_DECLINED", "notification.cash.drawer.shift.discrepancy.declined.title");
        m.put("CASH_DRAWER_SHIFT_STARTED", "notification.cash.drawer.shift.started.title");
        m.put("CASH_DRAWER_SHIFT_CLOSED", "notification.cash.drawer.shift.closed.title");
        m.put("CASH_DRAWER_SHIFT_CLOSED_BY_MANAGER", "notification.cash.drawer.shift.closed.by.manager.title");
        // HQ alert notifications
        m.put("SALES_THRESHOLD_ALERT", "notification.alert.sales.threshold.title");
        m.put("REFUND_PERCENTAGE_ALERT", "notification.alert.refund.percentage.title");
        m.put("ORDER_CANCELLATION_PERCENTAGE_ALERT", "notification.alert.order.cancellation.percentage.title");
        m.put("TRANSACTION_CANCELLATION_PERCENTAGE_ALERT", "notification.alert.transaction.cancellation.percentage.title");
        m.put("CANCELLATION_PERCENTAGE_ALERT", "notification.alert.cancellation.percentage.combined.title");
        // Menu assignment
        m.put("MENU_ASSIGNED_TO_RESTAURANT", "notification.menu.assigned.to.restaurant.title");
        m.put("MENU_LIVE_AT_RESTAURANT", "notification.menu.live.at.restaurant.title");
        return Map.copyOf(m);
    }

    private static final Map<String, Set<String>> CATEGORY_TYPE_MAP =
            Map.of(
                    "ORDERS",
                    Set.of(
                            "ITEM_READY",
                            "ITEM_DELAYED",
                            TYPE_CANCELLATION_APPROVED,
                            TYPE_CANCELLATION_REJECTED,
                            "PAYMENT_COMPLETED",
                            "PAYMENT_ERROR",
                            "PAYMENT_FAILED",
                            "PAYMENT_EXPIRED",
                            TYPE_ITEM_CANCELLATION_REQUEST,
                            TYPE_ITEM_CANCELLATION_REQUEST_APPROVED,
                            TYPE_ITEM_CANCELLATION_REQUEST_DECLINED,
                            "COMBO_CANCELLATION_REQUEST",
                            "TRANSACTION_CANCELLATION_REQUEST",
                            "TRANSACTION_CANCELLED_FOR_WAITER",
                            "ORDER_CANCELLATION_REQUEST",
                            "MANAGER_ITEM_CANCELED_NOTIFICATION",
                            "ORDER_UPDATE",
                            "ORDER_CANCELLED",
                            TYPE_ORDER_CANCELLED_BY_MANAGER,
                            "SALES_THRESHOLD_ALERT",
                            "REFUND_PERCENTAGE_ALERT",
                            "ORDER_CANCELLATION_PERCENTAGE_ALERT",
                            "TRANSACTION_CANCELLATION_PERCENTAGE_ALERT",
                            "CANCELLATION_PERCENTAGE_ALERT"),
                    "TABLE",
                    Set.of("TABLE_ASSIGNED", "TABLE_REMOVED", "GUEST_TRANSFER"),
                    "KDS",
                    Set.of(
                            "ORDER_PLACED",
                            "ORDER_CANCELED",
                            "ORDER_UPDATE",
                            "ITEM_READY",
                            "ITEM_DELAYED",
                            TYPE_ITEM_CANCELED,
                            "ITEM_SERVED",
                            "ITEM_PUSHED",
                            "KDS_COOKING",
                            "KDS_READY",
                            "KDS_DELAYED",
                            "ITEM_CANCELLATION_APPROVED",
                            "ITEM_CANCELLATION_DECLINED",
                            "ORDER_CANCELLATION_APPROVED",
                            "ORDER_CANCELLATION_DECLINED"),
                    "REQUESTS",
                    Set.of(
                            "PROFILE_UPDATE_REQUEST_OPENED",
                            "PROFILE_UPDATE_REQUEST_CREATED",
                            "PROFILE_UPDATE_REQUEST_APPROVED",
                            "PROFILE_UPDATE_REQUEST_DECLINED",
                            "ADDITIONAL_DISCOUNT_REQUEST_OPENED",
                            "ADDITIONAL_DISCOUNT_REQUEST_APPROVED",
                            "ADDITIONAL_DISCOUNT_REQUEST_DECLINED",
                            "DISCOUNT_REQUEST_APPROVED",
                            "DISCOUNT_REQUEST_DECLINED",
                            "TABLE_SECTION_REQUEST_OPENED",
                            "TABLE_SECTION_REQUEST_CREATED",
                            "TABLE_SECTION_REQUEST_APPROVED",
                            "TABLE_SECTION_REQUEST_DECLINED",
                            "REFUND_REQUEST",
                            TYPE_REFUND_REQUEST_APPROVED,
                            TYPE_REFUND_REQUEST_DECLINED,
                            "CANCEL_REQUEST_OPENED",
                            TYPE_ITEM_CANCELLATION_REQUEST,
                            TYPE_ITEM_CANCELLATION_REQUEST_APPROVED,
                            TYPE_ITEM_CANCELLATION_REQUEST_DECLINED,
                            "COMBO_CANCELLATION_REQUEST",
                            "COMBO_CANCELLATION_REQUEST_APPROVED",
                            "COMBO_CANCELLATION_REQUEST_DECLINED",
                            TYPE_ITEM_CANCELED,
                            TYPE_CASH_DRAWER_SHIFT_DISCREPANCY_REQUEST,
                            TYPE_CASH_DRAWER_SHIFT_DISCREPANCY_APPROVED,
                            TYPE_CASH_DRAWER_SHIFT_DISCREPANCY_DECLINED),
                    "CASHIER",
                    Set.of(
                            "PAYMENT_COMPLETED",
                            "PAYMENT_ERROR",
                            "PAYMENT_FAILED",
                            "PAYMENT_EXPIRED",
                            "DISCOUNT_REQUEST_APPROVED",
                            "DISCOUNT_REQUEST_DECLINED",
                            TYPE_CANCELLATION_APPROVED,
                            TYPE_CANCELLATION_REJECTED,
                            TYPE_REFUND_REQUEST_APPROVED,
                            TYPE_REFUND_REQUEST_DECLINED,
                            "DEVICE_INTEGRATION_ERROR",
                            TYPE_CASH_DRAWER_SHIFT_DISCREPANCY_REQUEST,
                            TYPE_CASH_DRAWER_SHIFT_DISCREPANCY_APPROVED,
                            TYPE_CASH_DRAWER_SHIFT_DISCREPANCY_DECLINED,
                            TYPE_ORDER_CANCELLED_BY_MANAGER));

    // Mapping for notification type filter IDs
    private static final Map<String, Set<String>> NOTIFICATION_TYPE_FILTER_MAP =
            Map.of(
                    "profile-update",
                    Set.of(
                            "PROFILE_UPDATE_REQUEST_OPENED",
                            "PROFILE_UPDATE_REQUEST_CREATED",
                            "PROFILE_UPDATE_REQUEST_APPROVED",
                            "PROFILE_UPDATE_REQUEST_DECLINED"),
                    "additional-discount",
                    Set.of(
                            "ADDITIONAL_DISCOUNT_REQUEST_OPENED",
                            "ADDITIONAL_DISCOUNT_REQUEST_APPROVED",
                            "ADDITIONAL_DISCOUNT_REQUEST_DECLINED"),
                    "refund",
                    Set.of(
                            "REFUND_REQUEST",
                            TYPE_REFUND_REQUEST_APPROVED,
                            TYPE_REFUND_REQUEST_DECLINED),
                    "item-cancellation",
                    Set.of(
                            TYPE_ITEM_CANCELLATION_REQUEST,
                            TYPE_ITEM_CANCELLATION_REQUEST_APPROVED,
                            TYPE_ITEM_CANCELLATION_REQUEST_DECLINED,
                            TYPE_ITEM_CANCELED,
                            "MANAGER_ITEM_CANCELED_NOTIFICATION"),
                    "transaction-cancellation",
                    Set.of(
                            "TRANSACTION_CANCELLATION_REQUEST",
                            "TRANSACTION_CANCELLED_FOR_WAITER"),
                    "order-cancellation",
                    Set.of(
                            "ORDER_CANCELLATION_REQUEST",
                            "ORDER_CANCELLED",
                            TYPE_ORDER_CANCELLED_BY_MANAGER,
                            TYPE_CANCELLATION_APPROVED,
                            TYPE_CANCELLATION_REJECTED),
                    "cash-drawer-shift-discrepancy",
                    Set.of(
                            TYPE_CASH_DRAWER_SHIFT_DISCREPANCY_REQUEST,
                            TYPE_CASH_DRAWER_SHIFT_DISCREPANCY_APPROVED,
                            TYPE_CASH_DRAWER_SHIFT_DISCREPANCY_DECLINED),
                    // Extra HQ alert filters
                    FILTER_ID_SALES_THRESHOLD_ALERT,
                    Set.of(
                            "SALES_THRESHOLD_ALERT"),
                    FILTER_ID_REFUND_PERCENTAGE_ALERT,
                    Set.of(
                            "REFUND_PERCENTAGE_ALERT"),
                    FILTER_ID_CANCELLED_TRANSACTIONS_PERCENTAGE_ALERT,
                    Set.of(
                            "ORDER_CANCELLATION_PERCENTAGE_ALERT",
                            "TRANSACTION_CANCELLATION_PERCENTAGE_ALERT",
                            "CANCELLATION_PERCENTAGE_ALERT"));

    private final NotificationRepository notificationRepository;
    private final MessageUtil messageUtil;
    private final UserRepository userRepository;
    private final com.gulfnet.shared_library.repository.KdsConfigurationRepository kdsConfigurationRepository;
    private final com.gulfnet.shared_library.repository.CategoryKdsRepository categoryKdsRepository;
    private final com.gulfnet.shared_library.repository.OrderRepository orderRepository;
    private final com.gulfnet.shared_library.repository.OrderedItemRepository orderedItemRepository;
    private final com.gulfnet.shared_library.repository.CategoryItemMappingRepository categoryItemMappingRepository;

    /**
     * Retrieves paginated notifications for a user with filtering and sorting.
     * Supports filtering by category, status, and KDS ID. For KDS users with {@code kdsId}, filters by
     * {@code additional_data.targetKdsIds} when present, otherwise by station category mappings (legacy).
     *
     * @param userId        the user ID to get notifications for
     * @param page          page index (0-based; first page is 0). Omit or null defaults to 0.
     * @param size          page size
     * @param category      optional filter by notification category/type
     * @param status        optional filter by read status (all, read, unread)
     * @param sortBy        field to sort by (default: "date" maps to "createdAt")
     * @param sortDirection sort direction (ASC or DESC, default: DESC)
     * @param kdsId         optional KDS ID for KDS users to filter notifications
     * @param locale        locale for localized messages
     * @return {@link ResponseDto} containing paginated list of notifications
     * @throws ResponseStatusException if user ID is invalid, status is invalid, or KDS assignment validation fails
     */
    public ResponseDto<List<NotificationResponseDto>> getNotificationsForUser(
            String userId, Integer page, Integer size, String category, String status, 
            String sortBy, String sortDirection, String kdsId, Locale locale) {

        UUID userIdUuid = parseRequiredUuid(userId, "notifications.fetch.user.id.header.invalid", locale);
        UUID kdsIdUuid = parseOptionalUuid(kdsId, "notifications.fetch.kds.id.invalid", locale);

        Pageable pageable = buildPageable(page, size, sortBy, sortDirection);
        String normalizedStatus = validateAndNormalizeStatus(status, locale);
        Set<String> typeFilter = resolveCategoryTypes(category, locale);

        Page<Notification> notificationPage = fetchNotificationPage(userIdUuid, normalizedStatus, typeFilter, pageable);

        KdsContext kdsContext = loadKdsContext(userIdUuid, kdsIdUuid, locale);

        List<NotificationResponseDto> notifications = filterAndMapNotifications(
                notificationPage, kdsContext, kdsIdUuid, userIdUuid);

        return buildPaginatedResponse(notifications, notificationPage, kdsContext, kdsIdUuid, userIdUuid, locale);
    }

    // ==================== HELPER METHODS FOR getNotificationsForUser ====================

    private UUID parseRequiredUuid(String value, String errorMessageKey, Locale locale) {
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException ex) {
            log.warn("Invalid UUID format: {}", value);
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, messageUtil.getMessage(errorMessageKey, locale));
        }
    }

    private UUID parseOptionalUuid(String value, String errorMessageKey, Locale locale) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        try {
            return UUID.fromString(value.trim());
        } catch (IllegalArgumentException ex) {
            log.warn("Invalid UUID format: {}", value);
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, messageUtil.getMessage(errorMessageKey, locale));
        }
    }

    /**
     * Builds a Pageable object from pagination and sorting parameters.
     * Maps page/size into a {@link Pageable}; applies sort field and direction defaults.
     *
     * @param page          page number (1-based; null defaults to 1)
     * @param size          page size (null defaults to 20; passed through as-is when non-null)
     * @param sortBy        field to sort by (defaults to "date" which maps to "createdAt")
     * @param sortDirection sort direction (ASC or DESC, defaults to DESC)
     * @return {@link Pageable} object for pagination and sorting
     */
    private Pageable buildPageable(Integer page, Integer size, String sortBy, String sortDirection) {
        int pageNumber = Math.max((page != null && page > 0 ? page : 1) - 1, 0);
        int pageSize = size != null ? size : 20;

        String sortField = (sortBy != null && !sortBy.trim().isEmpty()) ? sortBy.trim().toLowerCase() : DEFAULT_SORT_BY;
        String sortDir = (sortDirection != null && !sortDirection.trim().isEmpty()) ? sortDirection.trim().toUpperCase() : DEFAULT_SORT_DIRECTION;

        if (!"ASC".equals(sortDir) && !"DESC".equals(sortDir)) {
            sortDir = DEFAULT_SORT_DIRECTION;
        }

        String sortFieldName = "date".equals(sortField) ? "createdAt" : sortField;
        Sort.Direction direction = "ASC".equals(sortDir) ? Sort.Direction.ASC : Sort.Direction.DESC;
        Sort sort = Sort.by(direction, sortFieldName).and(Sort.by(direction, "id"));
        return PageRequest.of(pageNumber, pageSize, sort);
    }

    /**
     * Validates and normalizes the notification status filter.
     * Accepts "all", "read", or "unread" (case-insensitive).
     *
     * @param status the status filter string to validate
     * @param locale locale for localized error messages
     * @return normalized status string ("all", "read", or "unread")
     * @throws ResponseStatusException if status is invalid
     */
    private String validateAndNormalizeStatus(String status, Locale locale) {
        String normalizedStatus = (status != null && !status.trim().isEmpty())
                ? status.trim().toLowerCase()
                : "all";

        if (!normalizedStatus.equals("all") && !normalizedStatus.equals("read") && !normalizedStatus.equals(STATUS_UNREAD)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    messageUtil.getMessage("notifications.fetch.invalid.status", locale, status));
        }
        return normalizedStatus;
    }

    /**
     * Fetches a page of notifications from the repository based on user ID, status, and type filter.
     * Uses different repository methods depending on status and type filter combinations.
     *
     * @param userIdUuid      the user ID to fetch notifications for
     * @param normalizedStatus normalized status filter ("all", "read", or "unread")
     * @param typeFilter      optional set of notification types to filter by
     * @param pageable        pagination and sorting parameters
     * @return {@link Page} of notifications matching the criteria
     */
    private Page<Notification> fetchNotificationPage(UUID userIdUuid, String normalizedStatus, Set<String> typeFilter, Pageable pageable) {
        Page<UUID> idPage;
        if (typeFilter != null) {
            if (normalizedStatus.equals("read")) {
                idPage = notificationRepository.findIdsByUser_IdAndReadIsTrueAndTypeIn(userIdUuid, typeFilter, pageable);
            } else if (normalizedStatus.equals(STATUS_UNREAD)) {
                idPage = notificationRepository.findIdsByUser_IdAndReadIsFalseAndTypeIn(userIdUuid, typeFilter, pageable);
            } else {
                idPage = notificationRepository.findIdsByUser_IdAndTypeIn(userIdUuid, typeFilter, pageable);
            }
        } else {
            if (normalizedStatus.equals("read")) {
                idPage = notificationRepository.findIdsByUser_IdAndReadIsTrue(userIdUuid, pageable);
            } else if (normalizedStatus.equals(STATUS_UNREAD)) {
                idPage = notificationRepository.findIdsByUser_IdAndReadIsFalse(userIdUuid, pageable);
            } else {
                idPage = notificationRepository.findIdsByUser_Id(userIdUuid, pageable);
            }
        }
        List<UUID> ids = idPage.getContent();
        if (ids.isEmpty()) {
            return new PageImpl<>(List.of(), pageable, idPage.getTotalElements());
        }
        List<Notification> loaded = notificationRepository.findByIdInWithCreatedBy(ids);
        List<Notification> ordered = orderNotificationsByIdOrder(ids, loaded);
        return new PageImpl<>(ordered, pageable, idPage.getTotalElements());
    }

    /**
     * Restores the sort order from the paginated ID query; {@code IN} results are not ordered.
     */
    private static List<Notification> orderNotificationsByIdOrder(List<UUID> idOrder, List<Notification> loaded) {
        if (idOrder.isEmpty()) {
            return List.of();
        }
        Map<UUID, Notification> byId = loaded.stream()
                .collect(Collectors.toMap(Notification::getId, n -> n, (a, b) -> a));
        return idOrder.stream().map(byId::get).filter(Objects::nonNull).toList();
    }

    /**
     * Holds the KDS user context resolved from the user's role and KDS assignments.
     */
    private record KdsContext(boolean isKdsUser, java.util.Set<UUID> userKdsIds, java.util.Set<UUID> kdsCategoryMappingIds) {
        static KdsContext notKds() {
            return new KdsContext(false, Set.of(), Set.of());
        }
    }

    /**
     * Loads KDS context for a user, including KDS assignments and category mappings.
     * Returns a non-KDS context if the user is not a KDS user or if KDS ID is not provided.
     *
     * @param userIdUuid the user ID to load KDS context for
     * @param kdsIdUuid  optional KDS ID to validate assignment and load category mappings
     * @param locale     locale for localized error messages
     * @return {@link KdsContext} containing KDS user status, assigned KDS IDs, and category mapping IDs
     * @throws ResponseStatusException if KDS assignment validation fails
     */
    private KdsContext loadKdsContext(UUID userIdUuid, UUID kdsIdUuid, Locale locale) {
        java.util.Set<UUID> userKdsIds = new java.util.HashSet<>();
        java.util.Set<UUID> kdsCategoryMappingIds = new java.util.HashSet<>();

        try {
            Optional<String> roleName = userRepository.findRoleNameByUserId(userIdUuid);
            if (roleName.isEmpty() || !"KDS".equals(roleName.get())) {
                return KdsContext.notKds();
            }

            if (kdsIdUuid != null && kdsConfigurationRepository != null) {
                validateKdsAssignment(userIdUuid, kdsIdUuid, locale);
                userKdsIds = loadUserKdsIds(userIdUuid);
                kdsCategoryMappingIds = loadKdsCategoryMappingIds(kdsIdUuid);
            }

            return new KdsContext(true, userKdsIds, kdsCategoryMappingIds);
        } catch (ResponseStatusException e) {
            throw e;
        } catch (Exception e) {
            log.debug("Could not determine user role for notification message customization: {}", e.getMessage());
            return KdsContext.notKds();
        }
    }

    private void validateKdsAssignment(UUID userIdUuid, UUID kdsIdUuid, Locale locale) {
        boolean isAssigned = kdsConfigurationRepository.existsByUserIdAndKdsId(userIdUuid, kdsIdUuid);
        if (!isAssigned) {
            log.warn("User {} attempted to fetch notifications for KDS {} which is not assigned to them",
                    userIdUuid, kdsIdUuid);
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    messageUtil.getMessage("notifications.fetch.kds.not.assigned", locale));
        }
    }

    /**
     * Loads all KDS IDs assigned to a user from KDS configuration.
     *
     * @param userIdUuid the user ID to load KDS IDs for
     * @return set of KDS IDs assigned to the user
     */
    private java.util.Set<UUID> loadUserKdsIds(UUID userIdUuid) {
        java.util.Set<UUID> userKdsIds = new java.util.HashSet<>();
        List<com.gulfnet.shared_library.entity.KdsConfiguration> configs =
                kdsConfigurationRepository.findByUserId(userIdUuid);
        if (configs != null) {
            for (com.gulfnet.shared_library.entity.KdsConfiguration config : configs) {
                try {
                    if (config.getKds() != null && config.getKds().getId() != null) {
                        userKdsIds.add(config.getKds().getId());
                    }
                } catch (Exception e) {
                    log.debug("Could not access KDS from configuration: {}", e.getMessage());
                }
            }
        }
        return userKdsIds;
    }

    /**
     * Loads all menu category mapping IDs associated with a KDS station.
     *
     * @param kdsIdUuid the KDS ID to load category mappings for
     * @return set of menu category mapping IDs associated with the KDS
     */
    private java.util.Set<UUID> loadKdsCategoryMappingIds(UUID kdsIdUuid) {
        java.util.Set<UUID> kdsCategoryMappingIds = new java.util.HashSet<>();
        if (categoryKdsRepository != null) {
            try {
                List<UUID> categoryMappingIds = categoryKdsRepository.findMenuCategoryMappingIdsByKdsId(kdsIdUuid);
                if (categoryMappingIds != null) {
                    kdsCategoryMappingIds.addAll(categoryMappingIds);
                }
                log.debug("Found {} category mappings for KDS {}", kdsCategoryMappingIds.size(), kdsIdUuid);
            } catch (Exception e) {
                log.warn("Failed to get category mappings for KDS {}: {}", kdsIdUuid, e.getMessage());
            }
        }
        return kdsCategoryMappingIds;
    }

    /**
     * Filters and maps notifications to DTOs, applying KDS-specific filtering if applicable.
     * For KDS users with {@code kdsId}, filters by stored {@code targetKdsIds} first, then legacy category matching.
     *
     * @param notificationPage the page of notifications to filter and map
     * @param kdsContext       KDS context containing user KDS information
     * @param kdsIdUuid        optional KDS ID for filtering
     * @param userIdUuid       the user ID viewing the notifications
     * @return list of filtered and mapped notification DTOs
     */
    private List<NotificationResponseDto> filterAndMapNotifications(
            Page<Notification> notificationPage, KdsContext kdsContext, UUID kdsIdUuid, UUID userIdUuid) {

        // Apply station-scoped listing whenever kdsId is present (not only when CategoryKds rows exist).
        // Rows persisted with targetKdsIds match here even for default / uncategorized stations.
        if (kdsContext.isKdsUser() && kdsIdUuid != null) {
            log.info("Filtering notifications for KDS user {} with KDS ID {} ({} category mappings for legacy fallback)",
                    userIdUuid, kdsIdUuid, kdsContext.kdsCategoryMappingIds().size());

            List<NotificationResponseDto> notifications = notificationPage.stream()
                    .filter(notification -> doesNotificationBelongToKds(notification, kdsIdUuid, kdsContext.kdsCategoryMappingIds()))
                    .map(notification -> toDto(notification, userIdUuid, true))
                    .collect(Collectors.toList());

            log.info("Filtered {} notifications for KDS {} from {} total notifications",
                    notifications.size(), kdsIdUuid, notificationPage.getNumberOfElements());
            return notifications;
        }

        return notificationPage.stream()
                .map(notification -> toDto(notification, userIdUuid, kdsContext.isKdsUser()))
                .collect(Collectors.toList());
    }

    /**
     * Builds a paginated response DTO from filtered notifications and original notification page.
     * Adjusts total count for KDS-filtered results.
     *
     * @param notifications    filtered list of notification DTOs
     * @param notificationPage original page of notifications from repository
     * @param kdsContext       KDS context for determining if filtering was applied
     * @param kdsIdUuid        optional KDS ID used for filtering
     * @param userIdUuid       the user ID viewing the notifications
     * @param locale           locale for localized messages
     * @return {@link ResponseDto} containing paginated notification list with metadata
     */
    private ResponseDto<List<NotificationResponseDto>> buildPaginatedResponse(
            List<NotificationResponseDto> notifications, Page<Notification> notificationPage,
            KdsContext kdsContext, UUID kdsIdUuid, UUID userIdUuid, Locale locale) {

        long filteredCount = notifications.size();
        long originalTotal = notificationPage.getTotalElements();

        log.info("Notification query results - User ID: {}, Original Total: {}, Filtered Count: {}, Page: {}, Size: {}",
                userIdUuid, originalTotal, filteredCount, notificationPage.getNumber(), notificationPage.getSize());

        if (originalTotal == 0) {
            log.warn("No notifications found for user {}", userIdUuid);
        }

        long totalRecords = originalTotal;
        if (kdsContext.isKdsUser() && kdsIdUuid != null) {
            totalRecords = filteredCount;
        }

        int totalPages = (int) Math.ceil((double) totalRecords / notificationPage.getSize());
        PaginationMetaData metaData =
                PaginationMetaData.builder()
                        .page(notificationPage.getNumber() + 1)
                        .size(notificationPage.getSize())
                        .totalPages(totalPages > 0 ? totalPages : 1)
                        .totalRecords(totalRecords)
                        .build();

        return ResponseDto.<List<NotificationResponseDto>>builder()
                .message(messageUtil.getMessage("notifications.fetch.success", locale))
                .data(notifications)
                .count((long) notifications.size())
                .total(notificationPage.getTotalElements())
                .metaData(metaData)
                .build();
    }

    // ==================== CATEGORY RESOLUTION ====================

    /**
     * Resolves category string to a set of notification types.
     * Supports both new notification type filter IDs and legacy category formats.
     *
     * @param category the category string to resolve
     * @param locale   locale for localized error messages
     * @return set of notification types, or null if category is empty
     * @throws ResponseStatusException if category is invalid
     */
    private Set<String> resolveCategoryTypes(String category, Locale locale) {
        if (category == null || category.trim().isEmpty()) {
            return null;
        }

        String normalized = category.trim();
        
        // First check if it's a new notification type filter ID (e.g., "profile-update", "order-cancellation")
        Set<String> types = NOTIFICATION_TYPE_FILTER_MAP.get(normalized.toLowerCase());
        if (types != null) {
            return types;
        }
        
        // Fall back to old category format (e.g., "ORDERS", "REQUESTS")
        String normalizedUpper = normalized.toUpperCase(Locale.ROOT);
        types = CATEGORY_TYPE_MAP.get(normalizedUpper);
        if (types == null) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    messageUtil.getMessage("notifications.fetch.invalid.category", locale, category));
        }
        return types;
    }

    // ==================== DTO CONVERSION ====================

    /**
     * Converts a notification entity to a DTO.
     * Convenience method that delegates to the overloaded method with null viewing user ID and non-KDS user.
     *
     * @param notification the notification entity to convert
     * @return {@link NotificationResponseDto} with notification details
     */
    public NotificationResponseDto toDto(Notification notification) {
        return toDto(notification, null, false);
    }
    
    /**
     * Converts a notification entity to a DTO with user context for message customization.
     * Customizes message text for KDS users viewing cancellation notifications.
     *
     * @param notification  the notification entity to convert
     * @param viewingUserId optional ID of the user viewing the notification
     * @param isKdsUser     whether the viewing user is a KDS user
     * @return {@link NotificationResponseDto} with notification details and customized message
     */
    public NotificationResponseDto toDto(Notification notification, UUID viewingUserId, boolean isKdsUser) {
        String createdByName = resolveCreatedByName(notification);
        String title = resolveNotificationTitle(notification);
        String message = resolveNotificationMessage(notification, viewingUserId, isKdsUser);
        UUID requestId = resolveRequestIdFromNotification(notification);
        
        NotificationResponseDto dto = NotificationResponseDto.builder()
                .id(notification.getId())
                .title(title)
                .type(notification.getType())
                .message(message)
                .createdBy(createdByName)
                .read(notification.getRead())
                .createdAt(notification.getCreatedAt())
                .requestId(requestId)
                .build();
        dto.setTargetKdsIds(parseTargetKdsIdsFromAdditionalData(notification.getAdditionalData()));
        return dto;
    }

    /**
     * Resolves requestId from notification for navigation to GET /api/v1/users/requests/{requestId}/details.
     * Parses additional_data JSON and extracts requestId, orderId, userId, transactionId, etc.
     */
    private static UUID resolveRequestIdFromNotification(Notification notification) {
        String additionalDataJson = notification.getAdditionalData();
        if (additionalDataJson == null || additionalDataJson.trim().isEmpty()) {
            return null;
        }
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> data = OBJECT_MAPPER.readValue(additionalDataJson, Map.class);
            if (data == null || data.isEmpty()) {
                return null;
            }
            for (String key : new String[]{"requestId", "orderId", "userId", "transactionId", "shiftId", "tableId", "sectionId", "orderedItemId", "orderedComboId"}) {
                Object val = data.get(key);
                if (val != null && !val.toString().trim().isEmpty()) {
                    try {
                        return UUID.fromString(val.toString().trim());
                    } catch (IllegalArgumentException ignored) {
                        // try next key
                    }
                }
            }
        } catch (JsonProcessingException e) {
            log.warn("Failed to parse notification additional_data: {}", e.getMessage());
        }
        return null;
    }

    private String resolveCreatedByName(Notification notification) {
        if (notification.getCreatedBy() == null) {
            return null;
        }
        String firstName = notification.getCreatedBy().getFirstName() != null
                ? notification.getCreatedBy().getFirstName() : "";
        String lastName = notification.getCreatedBy().getLastName() != null
                ? notification.getCreatedBy().getLastName() : "";
        String createdByName = (firstName + " " + lastName).trim();
        return createdByName.isEmpty() ? null : createdByName;
    }

    /**
     * Resolves the notification title in the viewer's locale.
     * Looks up the notification type in {@link #NOTIFICATION_TYPE_TITLE_KEY_MAP} and resolves the
     * corresponding i18n message key. Falls back to the stored title for unknown types.
     *
     * @param notification the notification entity to resolve title for
     * @return localized notification title
     */
    private String resolveNotificationTitle(Notification notification) {
        String notificationType = notification.getType();
        if (notificationType != null) {
            String titleKey = NOTIFICATION_TYPE_TITLE_KEY_MAP.get(notificationType);
            if (titleKey != null) {
                try {
                    return messageUtil.getMessage(titleKey, LocaleContextHolder.getLocale());
                } catch (Exception e) {
                    log.warn("Failed to resolve title key '{}' for notification type '{}', falling back to stored title",
                            titleKey, notificationType);
                }
            }
        }
        return notification.getTitle();
    }

    /**
     * Resolves the notification message in the viewer's locale.
     * If the notification has a stored {@code bodyKey}, re-resolves the body from
     * message properties using the viewer's locale. Falls back to the stored message
     * for legacy notifications without a bodyKey.
     * Also customizes for KDS users viewing cancellation notifications they didn't create.
     *
     * @param notification  the notification entity to resolve message for
     * @param viewingUserId optional ID of the user viewing the notification
     * @param isKdsUser     whether the viewing user is a KDS user
     * @return notification message, localized and customized if applicable
     */
    private String resolveNotificationMessage(Notification notification, UUID viewingUserId, boolean isKdsUser) {
        String message = resolveBodyFromKey(notification);
        String notificationType = notification.getType();

        // For KDS users viewing cancellation notifications they didn't create, replace "Your" with "A"
        if (isKdsUser && viewingUserId != null && notification.getCreatedBy() != null
                && notificationType != null && CANCELLATION_NOTIFICATION_TYPES.contains(notificationType)
                && !viewingUserId.equals(notification.getCreatedBy().getId())
                && message != null && message.trim().startsWith("Your")) {
            message = message.replaceFirst("^Your", "A");
            log.debug("Replaced 'Your' with 'A' in cancellation notification message for KDS user {}: {}",
                    viewingUserId, notification.getId());
        }
        return stripAlertDedupSuffix(message);
    }

    /**
     * Re-resolves the notification body from its stored message key and args in the viewer's locale.
     * Falls back to the stored message text for legacy notifications that have no bodyKey.
     */
    private String resolveBodyFromKey(Notification notification) {
        String bodyKey = notification.getBodyKey();
        if (bodyKey == null || bodyKey.isEmpty()) {
            return notification.getMessage();
        }
        try {
            Object[] args = deserializeBodyArgs(notification.getBodyArgs());
            return messageUtil.getMessage(bodyKey, LocaleContextHolder.getLocale(), args);
        } catch (Exception e) {
            log.warn("Failed to resolve body key '{}' for notification {}, falling back to stored message",
                    bodyKey, notification.getId());
            return notification.getMessage();
        }
    }

    private static Object[] deserializeBodyArgs(String bodyArgsJson) {
        if (bodyArgsJson == null || bodyArgsJson.isEmpty()) {
            return new Object[0];
        }
        try {
            return OBJECT_MAPPER.readValue(bodyArgsJson, String[].class);
        } catch (JsonProcessingException e) {
            return new Object[0];
        }
    }

    /**
     * Strips the HQ threshold alert dedup suffix " [rid:uuid]" from the message so the UI does not display it.
     * The suffix is stored in the database for duplicate detection only.
     */
    private static String stripAlertDedupSuffix(String message) {
        if (message == null) {
            return null;
        }
        return ALERT_DEDUP_SUFFIX.matcher(message).replaceFirst("");
    }

    // ==================== MARK AS READ ====================

    /**
     * Marks a single notification as read for a user.
     *
     * @param userId        the user ID marking the notification as read
     * @param notificationId the UUID of the notification to mark as read
     * @param locale        locale for localized messages
     * @return {@link ResponseDto} containing the updated notification DTO
     * @throws ResponseStatusException if notification not found or user is not authorized
     */
    @Transactional
    public ResponseDto<NotificationResponseDto> markNotificationAsRead(
            String userId, UUID notificationId, Locale locale) {

        UUID userIdUuid;
        try {
            userIdUuid = UUID.fromString(userId);
        } catch (IllegalArgumentException ex) {
            log.warn("Invalid User-ID format: {}", userId);
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    messageUtil.getMessage("notifications.mark.read.user.id.invalid", locale));
        }

        Notification notification = notificationRepository.findById(notificationId)
                .orElseThrow(() -> {
                    log.warn("Notification not found: {}", notificationId);
                    return new ResponseStatusException(
                            HttpStatus.NOT_FOUND,
                            messageUtil.getMessage("notifications.mark.read.not.found", locale, notificationId));
                });
        
        // Eagerly load createdBy if not already loaded (within transaction)
        if (notification.getCreatedBy() != null) {
            try {
                notification.getCreatedBy().getFirstName();
            } catch (Exception e) {
                log.debug("Could not load createdBy for notification {}: {}", notificationId, e.getMessage());
            }
        }

        // Verify that the notification belongs to the user
        if (notification.getUser() == null || !notification.getUser().getId().equals(userIdUuid)) {
            log.warn("User {} attempted to mark notification {} as read, but notification belongs to different user",
                    userId, notificationId);
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    messageUtil.getMessage("notifications.mark.read.forbidden", locale));
        }

        // Mark as read if not already read
        if (notification.getRead() == null || !notification.getRead()) {
            notification.setRead(true);
            notification = notificationRepository.save(notification);
            log.info("Marked notification {} as read for user {}", notificationId, userId);
        } else {
            log.debug("Notification {} is already marked as read", notificationId);
        }

        boolean isKdsUser = resolveIsKdsUser(userIdUuid);
        NotificationResponseDto responseDto = toDto(notification, userIdUuid, isKdsUser);

        return ResponseDto.<NotificationResponseDto>builder()
                .message(messageUtil.getMessage("notifications.mark.read.success", locale))
                .data(responseDto)
                .build();
    }

    /**
     * Marks multiple notifications as read for a user in a single operation.
     * <p>
     * Only notifications that belong to the provided user and are currently unread are updated. Notifications not
     * owned by the user are ignored. If no unread notifications match, an empty list response is returned.
     * </p>
     *
     * @param userId          the user ID marking the notifications as read (string UUID)
     * @param notificationIds list of notification UUIDs to mark as read
     * @param locale          locale for localized messages
     * @return {@link ResponseDto} containing list of updated notification DTOs
     * @throws ResponseStatusException if ids are empty, user id is invalid, or none of the ids exist
     */
    @Transactional
    public ResponseDto<List<NotificationResponseDto>> markNotificationsAsRead(
            String userId, List<UUID> notificationIds, Locale locale) {

        if (notificationIds == null || notificationIds.isEmpty()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    messageUtil.getMessage("notifications.mark.read.ids.empty", locale));
        }

        UUID userIdUuid;
        try {
            userIdUuid = UUID.fromString(userId);
        } catch (IllegalArgumentException ex) {
            log.warn("Invalid User-ID format: {}", userId);
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    messageUtil.getMessage("notifications.mark.read.user.id.invalid", locale));
        }

        // Fetch all notifications
        List<Notification> notifications = notificationRepository.findAllById(notificationIds);

        if (notifications.isEmpty()) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    messageUtil.getMessage("notifications.mark.read.none.found", locale));
        }

        // Filter and update notifications that belong to the user
        List<Notification> userNotifications = notifications.stream()
                .filter(n -> n.getUser() != null && n.getUser().getId().equals(userIdUuid))
                .filter(n -> n.getRead() == null || !n.getRead())
                .peek(n -> n.setRead(true))
                .collect(Collectors.toList());

        if (userNotifications.isEmpty()) {
            log.warn("No unread notifications found for user {} in the provided list", userId);
            return ResponseDto.<List<NotificationResponseDto>>builder()
                    .message(messageUtil.getMessage("notifications.mark.read.no.unread", locale))
                    .data(List.of())
                    .build();
        }

        // Save all updated notifications
        List<Notification> savedNotifications = notificationRepository.saveAll(userNotifications);
        log.info("Marked {} notifications as read for user {}", savedNotifications.size(), userId);

        boolean isKdsUser = resolveIsKdsUser(userIdUuid);

        List<NotificationResponseDto> responseDtos = savedNotifications.stream()
                .map(notification -> toDto(notification, userIdUuid, isKdsUser))
                .collect(Collectors.toList());

        return ResponseDto.<List<NotificationResponseDto>>builder()
                .message(messageUtil.getMessage("notifications.mark.read.multiple.success", locale, savedNotifications.size()))
                .data(responseDtos)
                .count((long) responseDtos.size())
                .build();
    }

    /**
     * Checks if the given user has a KDS role.
     */
    private boolean resolveIsKdsUser(UUID userIdUuid) {
        try {
            return userRepository.findRoleNameByUserId(userIdUuid)
                    .map("KDS"::equals)
                    .orElse(false);
        } catch (Exception e) {
            log.debug("Could not determine user role for notification message customization: {}", e.getMessage());
        }
        return false;
    }

    // ==================== KDS NOTIFICATION FILTERING ====================

    /**
     * Parses {@code targetKdsIds} from notification {@code additional_data} (comma-separated string or JSON array).
     */
    private List<UUID> parseTargetKdsIdsFromAdditionalData(String additionalDataJson) {
        if (additionalDataJson == null || additionalDataJson.trim().isEmpty()) {
            return null;
        }
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> data = OBJECT_MAPPER.readValue(additionalDataJson, Map.class);
            if (data == null || data.isEmpty()) {
                return null;
            }
            Object raw = data.get(ADDITIONAL_TARGET_KDS_IDS);
            if (raw == null) {
                return null;
            }
            if (raw instanceof List<?> list) {
                List<UUID> out = new ArrayList<>();
                for (Object o : list) {
                    if (o == null) {
                        continue;
                    }
                    try {
                        out.add(UUID.fromString(o.toString().trim()));
                    } catch (IllegalArgumentException ignored) {
                        // skip invalid entry
                    }
                }
                return out.isEmpty() ? null : out;
            }
            String csv = raw.toString().trim();
            if (csv.isEmpty()) {
                return null;
            }
            List<UUID> out = new ArrayList<>();
            for (String part : csv.split(",")) {
                String p = part.trim();
                if (p.isEmpty()) {
                    continue;
                }
                try {
                    out.add(UUID.fromString(p));
                } catch (IllegalArgumentException ignored) {
                    // skip
                }
            }
            return out.isEmpty() ? null : out;
        } catch (JsonProcessingException e) {
            log.debug("Could not parse notification additional_data for targetKdsIds: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Check if a notification belongs to a specific KDS device by checking if the order
     * in the notification message has items that match the KDS's assigned categories.
     */
    private boolean doesNotificationBelongToKds(Notification notification, UUID kdsId, Set<UUID> kdsCategoryMappingIds) {
        if (notification == null || notification.getMessage() == null) {
            return false;
        }

        List<UUID> routed = parseTargetKdsIdsFromAdditionalData(notification.getAdditionalData());
        if (routed != null && !routed.isEmpty()) {
            return routed.contains(kdsId);
        }

        if (kdsCategoryMappingIds.isEmpty()) {
            return false;
        }
        
        String notificationType = notification.getType();
        // If notification type is NOT KDS-relevant (e.g., PAYMENT_COMPLETED, TABLE_ASSIGNED, etc.),
        // filter it out - don't show it to KDS users
        if (!isKdsRelevantNotificationType(notificationType)) {
            log.debug("Notification {} type {} is not KDS-relevant, filtering out for KDS {}", 
                    notification.getId(), notificationType, kdsId);
            return false;
        }
        
        try {
            String message = notification.getMessage();
            
            // For ITEM_CANCELED notifications, try to match via OrderedItem ID
            if (TYPE_ITEM_CANCELED.equals(notificationType)) {
                Boolean result = checkItemCanceledForKds(message, notification.getId(), kdsId, kdsCategoryMappingIds);
                if (result != null) {
                    return result;
                }
                // Fall through to order number extraction as fallback
            }
            
            return checkOrderForKds(message, notification.getId(), kdsId, kdsCategoryMappingIds);
        } catch (Exception e) {
            log.warn("Error checking if notification {} belongs to KDS {}: {}",
                    notification.getId(), kdsId, e.getMessage());
            // Fail-closed: if we can't determine if notification belongs to KDS, don't show it
            return false;
        }
    }

    /**
     * Determines if a notification type is relevant to KDS and should be filtered.
     */
    private boolean isKdsRelevantNotificationType(String notificationType) {
        Set<String> kdsNotificationTypes = CATEGORY_TYPE_MAP.get("KDS");
        if (kdsNotificationTypes != null && kdsNotificationTypes.contains(notificationType)) {
            return true;
        }
        // Also check cancellation/item related types
        return notificationType != null && (
                notificationType.contains("CANCELLATION") ||
                notificationType.contains("ITEM") ||
                notificationType.equals("ITEM_PUSHED"));
    }

    /**
     * Checks if an ITEM_CANCELED notification belongs to a KDS by matching the ordered item's categories.
     * Returns true/false if a definitive match is found, null to fall through to order-based matching.
     */
    private Boolean checkItemCanceledForKds(String message, UUID notificationId, UUID kdsId, Set<UUID> kdsCategoryMappingIds) {
        UUID orderedItemId = extractOrderedItemIdFromMessage(message);

        if (orderedItemId == null || orderedItemRepository == null || categoryItemMappingRepository == null) {
            log.debug("Could not extract OrderedItem ID from notification message: {}", message);
            return null; // Fall through
        }

        Optional<com.gulfnet.shared_library.entity.OrderedItem> orderedItemOpt =
                orderedItemRepository.findById(orderedItemId);

        if (orderedItemOpt.isEmpty()) {
            log.debug("OrderedItem {} not found for notification {}", orderedItemId, notificationId);
            return null; // Fall through
        }

        com.gulfnet.shared_library.entity.OrderedItem orderedItem = orderedItemOpt.get();
        if (orderedItem.getItem() == null || orderedItem.getItem().getId() == null || orderedItem.getOrderedCombo() != null) {
            return null; // Fall through
        }

        UUID itemId = orderedItem.getItem().getId();
        if (doesItemMatchKdsCategories(itemId, kdsCategoryMappingIds)) {
            log.debug("Notification {} matches KDS {} through OrderedItem {} item {}",
                    notificationId, kdsId, orderedItemId, itemId);
            return true;
        }

        log.debug("Notification {} (OrderedItem {}) does not match KDS {} categories",
                notificationId, orderedItemId, kdsId);
        return false;
    }

    /**
     * Checks if a notification belongs to a KDS by extracting the order number from the message
     * and matching order items against KDS categories.
     */
    private boolean checkOrderForKds(String message, UUID notificationId, UUID kdsId, Set<UUID> kdsCategoryMappingIds) {
        String orderNumber = extractOrderNumberFromMessage(message);

        if (orderNumber == null || orderNumber.isEmpty()) {
            log.debug("Could not extract order number from notification message: {}. Filtering out for KDS {}", message, kdsId);
            return false; // Fail-closed: if we can't determine, don't show the notification
        }

        if (orderRepository == null) {
            log.warn("OrderRepository is null, cannot filter notifications by KDS. Filtering out for KDS {}", kdsId);
            return false; // Fail-closed: if we can't filter, don't show the notification
        }

        Optional<com.gulfnet.shared_library.entity.Order> orderOpt = orderRepository.findByOrderNumber(orderNumber);
        if (orderOpt.isEmpty()) {
            log.debug("Order not found for order number: {}. Filtering out for KDS {}", orderNumber, kdsId);
            return false; // Fail-closed: if order not found, don't show the notification
        }

        if (orderedItemRepository == null || categoryItemMappingRepository == null) {
            log.warn("Repositories are null, cannot filter notifications by KDS. Filtering out for KDS {}", kdsId);
            return false; // Fail-closed: if we can't filter, don't show the notification
        }

        List<com.gulfnet.shared_library.entity.OrderedItem> orderedItems =
                orderedItemRepository.findByOrderId(orderOpt.get().getId());

        if (orderedItems.isEmpty()) {
            log.debug("No ordered items found for order: {}. Filtering out for KDS {}", orderNumber, kdsId);
            return false; // Fail-closed: if no items found, don't show the notification
        }

        for (com.gulfnet.shared_library.entity.OrderedItem orderedItem : orderedItems) {
            if (orderedItem.getItem() == null || orderedItem.getItem().getId() == null
                    || orderedItem.getOrderedCombo() != null) {
                continue;
            }

            if (doesItemMatchKdsCategories(orderedItem.getItem().getId(), kdsCategoryMappingIds)) {
                log.debug("Notification {} matches KDS {} through order {} item {}",
                        notificationId, kdsId, orderNumber, orderedItem.getItem().getId());
                return true;
            }
        }

        log.debug("Notification {} does not match KDS {} for order {} - no items match KDS categories",
                notificationId, kdsId, orderNumber);
        return false;
    }

    /**
     * Checks if an item's category mappings match any of the KDS's assigned categories.
     */
    private boolean doesItemMatchKdsCategories(UUID itemId, Set<UUID> kdsCategoryMappingIds) {
        List<com.gulfnet.shared_library.entity.CategoryItemMapping> categoryMappings =
                categoryItemMappingRepository.findByItem_Id(itemId);

        if (categoryMappings == null) {
            return false;
        }

        for (com.gulfnet.shared_library.entity.CategoryItemMapping categoryMapping : categoryMappings) {
            if (categoryMapping.getMenuCategoryMapping() != null
                    && categoryMapping.getMenuCategoryMapping().getId() != null
                    && kdsCategoryMappingIds.contains(categoryMapping.getMenuCategoryMapping().getId())) {
                return true;
            }
        }
        return false;
    }

    // ==================== MESSAGE PARSING ====================

    /**
     * Extract order number from notification message.
     */
    private String extractOrderNumberFromMessage(String message) {
        if (message == null || message.trim().isEmpty()) {
            return null;
        }
        
        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile(
                "([A-Za-z0-9]+-ORD-[0-9-]+)"
        );
        java.util.regex.Matcher matcher = pattern.matcher(message);
        
        if (matcher.find()) {
            return matcher.group(1);
        }
        
        return null;
    }
    
    /**
     * Extract OrderedItem ID from notification message.
     */
    private UUID extractOrderedItemIdFromMessage(String message) {
        if (message == null || message.trim().isEmpty()) {
            return null;
        }
        
        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile(
                "(?i)OrderedItem\\s+ID:\\s*([0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12})",
                java.util.regex.Pattern.CASE_INSENSITIVE
        );
        java.util.regex.Matcher matcher = pattern.matcher(message);
        
        if (matcher.find()) {
            try {
                return UUID.fromString(matcher.group(1));
            } catch (IllegalArgumentException e) {
                log.debug("Invalid UUID format in OrderedItem ID extraction: {}", matcher.group(1));
                return null;
            }
        }
        
        return null;
    }

    // ==================== FILTER TYPES ====================

    /**
     * Retrieves available notification filter types and categories.
     * Returns both new notification type filter IDs and legacy category formats.
     * For MANAGER role, excludes threshold alert types (sales-threshold-alert, refund-percentage-alert,
     * cancelled-transactions-percentage-alert) since managers do not receive those notifications.
     *
     * @param userId optional user ID to filter types by role (when provided and user is MANAGER, threshold alerts are excluded)
     * @param locale locale for localized filter type names
     * @return {@link ResponseDto} containing available notification filter types
     */
    public ResponseDto<NotificationFilterTypesResponseDto> getNotificationFilterTypes(String userId, Locale locale) {
        log.info("Fetching notification filter types for user: {}", userId);

        List<NotificationFilterTypesResponseDto.NotificationTypeDto> allTypes = List.of(
            NotificationFilterTypesResponseDto.NotificationTypeDto.builder()
                .id("profile-update")
                .name(messageUtil.getMessage("notification.filter.type.profile-update", locale))
                .build(),
            NotificationFilterTypesResponseDto.NotificationTypeDto.builder()
                .id("additional-discount")
                .name(messageUtil.getMessage("receipt.additional.discount", locale))
                .build(),
            NotificationFilterTypesResponseDto.NotificationTypeDto.builder()
                .id("refund")
                .name(messageUtil.getMessage("notification.filter.type.refund", locale))
                .build(),
            NotificationFilterTypesResponseDto.NotificationTypeDto.builder()
                .id("item-cancellation")
                .name(messageUtil.getMessage("notification.filter.type.item-cancellation", locale))
                .build(),
            NotificationFilterTypesResponseDto.NotificationTypeDto.builder()
                .id("transaction-cancellation")
                .name(messageUtil.getMessage("notification.filter.type.transaction-cancellation", locale))
                .build(),
            NotificationFilterTypesResponseDto.NotificationTypeDto.builder()
                .id("order-cancellation")
                .name(messageUtil.getMessage("notification.filter.type.order-cancellation", locale))
                .build(),
            NotificationFilterTypesResponseDto.NotificationTypeDto.builder()
                .id("cash-drawer-shift-discrepancy")
                .name(messageUtil.getMessage("notification.filter.type.cash-drawer-shift-discrepancy", locale))
                .build(),
            NotificationFilterTypesResponseDto.NotificationTypeDto.builder()
                .id(FILTER_ID_SALES_THRESHOLD_ALERT)
                .name(messageUtil.getMessage("notification.alert.sales.threshold.title", locale))
                .build(),
            NotificationFilterTypesResponseDto.NotificationTypeDto.builder()
                .id(FILTER_ID_REFUND_PERCENTAGE_ALERT)
                .name(messageUtil.getMessage("notification.filter.type.refund-percentage-alert", locale))
                .build(),
            NotificationFilterTypesResponseDto.NotificationTypeDto.builder()
                .id(FILTER_ID_CANCELLED_TRANSACTIONS_PERCENTAGE_ALERT)
                .name(messageUtil.getMessage("notification.filter.type.cancelled-transactions-percentage-alert", locale))
                .build()
        );

        List<NotificationFilterTypesResponseDto.NotificationTypeDto> notificationTypes = allTypes;
        if (userId != null && !userId.isBlank()) {
            boolean isManager = isUserManager(userId);
            if (isManager) {
                notificationTypes = allTypes.stream()
                        .filter(t -> !THRESHOLD_ALERT_FILTER_IDS.contains(t.getId()))
                        .collect(Collectors.toList());
                log.debug("Excluded threshold alert types from filter for MANAGER user: {}", userId);
            }
        }

        long count = notificationTypes.size();

        NotificationFilterTypesResponseDto filterTypes = NotificationFilterTypesResponseDto.builder()
                .notificationTypes(notificationTypes)
                .count(count)
                .total(count)
                .build();

        return ResponseDto.<NotificationFilterTypesResponseDto>builder()
                .message(messageUtil.getMessage("notifications.filter.types.fetch.success", locale))
                .data(filterTypes)
                .build();
    }

    /**
     * Checks whether the given user has the MANAGER role.
     * <p>
     * Used to customize notification filter types returned to the client. Invalid user id formats are treated as
     * "not manager" and logged.
     * </p>
     *
     * @param userId user identifier (string UUID)
     * @return {@code true} when the user's role name equals {@code "MANAGER"}
     */
    private boolean isUserManager(String userId) {
        try {
            UUID userIdUuid = UUID.fromString(userId);
            return userRepository.findRoleNameByUserId(userIdUuid)
                    .map("MANAGER"::equals)
                    .orElse(false);
        } catch (IllegalArgumentException e) {
            log.warn("Invalid user ID format for filter types: {}", userId);
            return false;
        }
    }
}
