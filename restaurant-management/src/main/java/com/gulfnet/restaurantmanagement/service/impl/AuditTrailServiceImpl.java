package com.gulfnet.restaurantmanagement.service.impl;

import com.gulfnet.restaurantmanagement.config.OnlineCardPaymentProperties;
import com.gulfnet.restaurantmanagement.service.AuditTrailService;
import com.gulfnet.restaurantmanagement.util.MessageUtil;
import com.gulfnet.shared_library.entity.AuditTrail;
import com.gulfnet.shared_library.entity.Restaurant;
import com.gulfnet.shared_library.entity.User;
import com.gulfnet.shared_library.entity.RestaurantGroupTranslation;
import com.gulfnet.shared_library.enums.RequestStatus;
import com.gulfnet.shared_library.enums.ActionType;
import com.gulfnet.shared_library.model.response.dto.AuditTrailListResponse;
import com.gulfnet.shared_library.model.response.dto.AuditTrailResponse;
import com.gulfnet.shared_library.model.response.dto.PaginationMetaData;
import com.gulfnet.shared_library.model.response.dto.ResponseDto;
import com.gulfnet.shared_library.entity.RestaurantTranslation;
import com.gulfnet.shared_library.repository.AuditTrailRepository;
import com.gulfnet.shared_library.repository.RestaurantRepository;
import com.gulfnet.shared_library.repository.RestaurantGroupRepository;
import com.gulfnet.shared_library.repository.RestaurantTranslationRepository;
import com.gulfnet.shared_library.repository.RestaurantGroupTranslationRepository;
import com.gulfnet.shared_library.repository.RoleRepository;
import com.gulfnet.shared_library.repository.OrderRepository;
import com.gulfnet.shared_library.repository.UserRepository;
import com.gulfnet.shared_library.entity.Order;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.NoSuchMessageException;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;

import java.io.IOException;
import java.io.OutputStreamWriter;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.Set;
import java.util.HashSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuditTrailServiceImpl implements AuditTrailService {

    private final AuditTrailRepository auditTrailRepository;
    private final RestaurantRepository restaurantRepository;
    private final RestaurantGroupRepository restaurantGroupRepository;
    private final RestaurantTranslationRepository restaurantTranslationRepository;
    private final RestaurantGroupTranslationRepository restaurantGroupTranslationRepository;
    private final RoleRepository roleRepository;
    private final OrderRepository orderRepository;
    private final UserRepository userRepository;
    private final OnlineCardPaymentProperties onlineCardPaymentProperties;
    private final MessageUtil messageUtil;

    @PersistenceContext
    private EntityManager entityManager;

    // Constants
    private static final String ROLE_MANAGER = "MANAGER";
    private static final String ENTITY_TYPE_AUDIT_TRAIL = "AUDIT_TRAIL";
    private static final String ENTITY_TYPE_TRANSACTION = "TRANSACTION";
    private static final String ENTITY_TYPE_ORDER = "ORDER";
    private static final String ENTITY_TYPE_CASH_DRAWER = "CASH_DRAWER";
    private static final String ENTITY_TYPE_RESTAURANT = "RESTAURANT";
    private static final String PARAM_AUDIT_TRAIL_ID = "auditTrailId";
    private static final String DATE_TIME_FORMAT_PATTERN = "yyyy-MM-dd HH:mm:ss";
    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern(DATE_TIME_FORMAT_PATTERN);

    private static final Pattern AUDIT_NOTE_EXPORTED_CSV =
            Pattern.compile("^Exported (\\d+) audit trail records to CSV$");
    private static final Pattern AUDIT_NOTE_USER_CRUD =
            Pattern.compile("^User (created|updated|deleted): (.+) \\(([\\s\\S]*)\\)$");
    private static final Pattern AUDIT_NOTE_PROFILE_UPDATE =
            Pattern.compile("^Profile update request (.+?)\\. Comments: ([\\s\\S]*)$");
    private static final Pattern AUDIT_NOTE_REFUND =
            Pattern.compile("^Refund request (.+?)\\. Comments: ([\\s\\S]*)$");
    private static final Pattern AUDIT_NOTE_TX_CANCEL =
            Pattern.compile("^Transaction cancellation request (.+?)\\. Comments: ([\\s\\S]*)$");
    private static final Pattern AUDIT_NOTE_ADD_DISCOUNT =
            Pattern.compile("^Additional discount request (.+?)\\. Comments: ([\\s\\S]*)$");
    private static final Pattern AUDIT_NOTE_ADD_DISCOUNT_MANAGER =
            Pattern.compile("^Additional discount request (.+?) by manager\\. Comments: ([\\s\\S]*)$");

    /**
     * Get the set of action types that should be excluded for MANAGER role
     * These actions are not performed by managers and should be hidden from their audit trail view
     */
    private Set<ActionType> getManagerExcludedActionTypes() {
        Set<ActionType> excludedTypes = new HashSet<>();
        
        // Refund (cashier/requester entry); manager sees only REQUEST_REFUND_APPROVE/DECLINE (their decision)
        excludedTypes.add(ActionType.REFUND);
        
        // Cancellation
        excludedTypes.add(ActionType.CANCELLATION);
        
        // Category management
        excludedTypes.add(ActionType.CATEGORY_CREATE);
        excludedTypes.add(ActionType.CATEGORY_UPDATE);
        excludedTypes.add(ActionType.CATEGORY_DELETE);
        
        // Menu management (all menu related actions)
        excludedTypes.add(ActionType.MENU_CREATE);
        excludedTypes.add(ActionType.MENU_UPDATE);
        excludedTypes.add(ActionType.MENU_DELETE);
        excludedTypes.add(ActionType.MENU_PUBLISH);
        excludedTypes.add(ActionType.MENU_UNPUBLISH);
        excludedTypes.add(ActionType.MENU_ITEM_CREATE);
        excludedTypes.add(ActionType.MENU_ITEM_UPDATE);
        excludedTypes.add(ActionType.MENU_ITEM_DELETE);
        
        // Employee bulk upload
        excludedTypes.add(ActionType.EMPLOYEE_BULK_UPLOAD);
        
        // Item unavailability
        excludedTypes.add(ActionType.ITEM_AVAILABILITY_UPDATE);
        
        // Modifier management
        excludedTypes.add(ActionType.MODIFIER_CREATE);
        excludedTypes.add(ActionType.MODIFIER_UPDATE);
        excludedTypes.add(ActionType.MODIFIER_DELETE);
        
        // Restaurant CRUD (HQ-only; manager operates within an assigned restaurant)
        excludedTypes.add(ActionType.RESTAURANT_CREATE);
        excludedTypes.add(ActionType.RESTAURANT_UPDATE);
        excludedTypes.add(ActionType.RESTAURANT_DELETE);
        
        // Restaurant group CRUD (HQ-only)
        excludedTypes.add(ActionType.RESTAURANT_GROUP_CREATE);
        excludedTypes.add(ActionType.RESTAURANT_GROUP_UPDATE);
        excludedTypes.add(ActionType.RESTAURANT_GROUP_DELETE);
        
        // Table layout template CRUD (HQ-only; managers use table transfer/block/etc.)
        excludedTypes.add(ActionType.TABLE_LAYOUT_TEMPLATE_CREATE);
        excludedTypes.add(ActionType.TABLE_LAYOUT_TEMPLATE_UPDATE);
        excludedTypes.add(ActionType.TABLE_LAYOUT_TEMPLATE_DELETE);
        
        return excludedTypes;
    }

    /**
     * Validates user authorization and applies manager/cashier restaurant restrictions.
     * 
     * @param userRole The role of the user accessing audit trails
     * @param userIdHeader The user ID from header (for manager/cashier filtering)
     * @param restaurantId The original restaurant ID (may be overridden for managers/cashiers)
     * @param restaurantGroupId The original restaurant group ID (may be cleared for managers/cashiers)
     * @param userLocale The user locale for error messages
     * @param context Context string for logging (e.g., "CSV export")
     * @return An array where [0] is the filtered restaurantId and [1] is the filtered restaurantGroupId
     * @throws ResponseStatusException if user is not authorized or validation fails
     */
    private UUID[] validateAuthorizationAndApplyRestrictions(String userRole, String userIdHeader, 
            UUID restaurantId, UUID restaurantGroupId, Locale userLocale, String context) {
        // Only HQ_ADMIN, MANAGER, and CASHIER can access audit trails
        if (userRole == null ||
                (!"HQ_ADMIN".equalsIgnoreCase(userRole)
                        && !ROLE_MANAGER.equalsIgnoreCase(userRole)
                        && !"CASHIER".equalsIgnoreCase(userRole))) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    messageUtil.getMessage("audit.trail.access.unauthorized", userLocale));
        }

        UUID filteredRestaurantId = restaurantId;
        UUID filteredRestaurantGroupId = restaurantGroupId;

        // If user is MANAGER or CASHIER, automatically filter by their restaurant
        if ((ROLE_MANAGER.equalsIgnoreCase(userRole) || "CASHIER".equalsIgnoreCase(userRole))
                && userIdHeader != null && !userIdHeader.isBlank()) {
            try {
                UUID managerUserId = UUID.fromString(userIdHeader);
                User manager = userRepository.findById(managerUserId)
                        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                                messageUtil.getMessage("user.not.found", userLocale)));
                
                UUID managerRestaurantId = manager.getRestaurantId();
                if (managerRestaurantId == null) {
                    throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                            messageUtil.getMessage("audit.trail.manager.no.restaurant", userLocale));
                }
                
                // Override restaurantId with manager's restaurant (ignore any provided restaurantId)
                filteredRestaurantId = managerRestaurantId;
                // Clear restaurantGroupId as manager can only see their own restaurant
                filteredRestaurantGroupId = null;
                String logMessage = context != null && !context.isEmpty() 
                        ? String.format("Manager %s restricted to their restaurant for %s: %s", managerUserId, context, filteredRestaurantId)
                        : String.format("Manager %s restricted to their restaurant: %s", managerUserId, filteredRestaurantId);
                log.info(logMessage);
            } catch (IllegalArgumentException e) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        messageUtil.getMessage("error.invalid.user.id", userLocale));
            }
        }

        return new UUID[]{filteredRestaurantId, filteredRestaurantGroupId};
    }

    /**
     * Filters out action types that managers should not see from the audit trail logs.
     * 
     * @param logs The list of audit trail response DTOs to filter
     * @param userRole The role of the user viewing the audit trails
     * @return Filtered list of audit trail responses (or original list if not a manager)
     */
    private List<AuditTrailResponse> filterManagerExcludedActionTypes(List<AuditTrailResponse> logs, String userRole) {
        if (ROLE_MANAGER.equalsIgnoreCase(userRole)) {
            Set<ActionType> excludedTypes = getManagerExcludedActionTypes();
            return logs.stream()
                    .filter(log -> {
                        if (log.getActionType() == null) {
                            return true; // Keep logs with null action type
                        }
                        try {
                            ActionType logActionType = ActionType.valueOf(log.getActionType());
                            return !excludedTypes.contains(logActionType);
                        } catch (IllegalArgumentException e) {
                            return true; // Keep logs with invalid action type
                        }
                    })
                    .collect(Collectors.toList());
        }
        return logs;
    }

    /**
     * Maps module display names to entity types for database filtering.
     * Frontend sends module names like "Restaurant Management", "Menu Management", etc.
     * but we need to filter by entity_type in the database.
     * 
     * @param module The module name from the frontend
     * @return The corresponding entity type string, or null if module is null/blank
     */
    private String mapModuleToEntityType(String module) {
        if (module == null || module.isBlank()) {
            return null;
        }
        
        String moduleNormalized = module.trim();
        switch (moduleNormalized.toLowerCase()) {
            case "restaurant management":
            case "restaurant":
                return ENTITY_TYPE_RESTAURANT;
            case "restaurant group":
            case "restaurant_group":
                return "RESTAURANT_GROUP";
            case "menu management":
            case "menu":
                return "MENU";
            case "item":
                return "ITEM";
            case "category":
                return "CATEGORY";
            case "modifier":
                return "MODIFIER";
            case "table":
                return "TABLE";
            case "section":
                return "SECTION";
            case "table layout template":
            case "table_layout_template":
                return "TABLE_LAYOUT_TEMPLATE";
            case "order management":
            case "order":
                return ENTITY_TYPE_ORDER;
            case "transaction management":
            case "transaction":
                return ENTITY_TYPE_TRANSACTION;
            case "user management":
            case "user":
                return "USER";
            case "role":
                return "ROLE";
            case "cash drawer":
                return ENTITY_TYPE_CASH_DRAWER;
            case "discount":
                return "DISCOUNT";
            case "promotion":
                return "PROMOTION";
            case "kds":
            case "kds management":
                return "KDS";
            case "price override":
            case "price_override":
                return "PRICE_OVERRIDE";
            case "settings":
                return "SETTINGS";
            case "audit trail":
            case "audit_trail":
                return ENTITY_TYPE_AUDIT_TRAIL;
            default:
                // If it's already an entity type, use as-is
                return moduleNormalized.toUpperCase();
        }
    }

    private Locale auditTrailResponseLocale() {
        Locale locale = LocaleContextHolder.getLocale();
        return locale != null ? locale : Locale.ENGLISH;
    }

    private String localizedAuditMessage(String code, Locale locale, Object... args) {
        try {
            return messageUtil.getMessage(code, locale, args);
        } catch (NoSuchMessageException e) {
            return null;
        }
    }

    private String localizedActionLabel(ActionType actionType, Locale locale) {
        if (actionType == null) {
            return null;
        }
        String fromBundle = localizedAuditMessage("audit.trail.action." + actionType.name(), locale);
        if (fromBundle != null) {
            return fromBundle;
        }
        return actionType.name().replace('_', ' ');
    }

    private String localizedEntityTypeLabel(String entityType, Locale locale) {
        if (entityType == null || entityType.isBlank()) {
            return null;
        }
        String fromBundle = localizedAuditMessage("audit.trail.entity." + entityType.toUpperCase(Locale.ROOT), locale);
        if (fromBundle != null) {
            return fromBundle;
        }
        return entityType;
    }

    private String localizedModuleForEntityType(String entityTypeUpper, String entityTypeRaw, Locale locale) {
        if (entityTypeUpper == null || entityTypeUpper.isBlank()) {
            return null;
        }
        String messageKey = switch (entityTypeUpper) {
            case ENTITY_TYPE_ORDER -> "audit.trail.module.order_management";
            case ENTITY_TYPE_TRANSACTION -> "audit.trail.module.transaction_management";
            case ENTITY_TYPE_CASH_DRAWER -> "audit.trail.module.cash_drawer";
            case "USER", "ROLE" -> "audit.trail.module.user_management";
            case ENTITY_TYPE_RESTAURANT, "RESTAURANT_GROUP" -> "audit.trail.module.restaurant_management";
            case "MENU", "ITEM", "CATEGORY", "MODIFIER" -> "audit.trail.module.menu_management";
            case "TABLE", "SECTION", "TABLE_LAYOUT_TEMPLATE" -> "audit.trail.module.table_management";
            case "DISCOUNT" -> "audit.trail.module.discount_management";
            case "PROMOTION" -> "audit.trail.module.promotion_management";
            case "KDS" -> "audit.trail.module.kds_management";
            case "PRICE_OVERRIDE" -> "audit.trail.module.price_override";
            case "SETTINGS" -> "audit.trail.module.settings";
            case ENTITY_TYPE_AUDIT_TRAIL -> "audit.trail.module.audit_trail";
            default -> null;
        };
        if (messageKey != null) {
            String resolved = localizedAuditMessage(messageKey, locale);
            if (resolved != null) {
                return resolved;
            }
        }
        return entityTypeRaw;
    }

    /**
     * Stored {@code notes} are written in English at persist time; map known English templates to the request locale.
     */
    private String localizeStoredAuditNotes(String notes, Locale locale) {
        if (notes == null || notes.isBlank()) {
            return notes;
        }
        Locale en = Locale.ENGLISH;
        try {
            if (notes.equals(messageUtil.getMessage("audit.trail.note.user_login_success", en))) {
                return messageUtil.getMessage("audit.trail.note.user_login_success", locale);
            }
            if (notes.equals(messageUtil.getMessage("audit.trail.note.user_logout", en))) {
                return messageUtil.getMessage("audit.trail.note.user_logout", locale);
            }
            Matcher exported = AUDIT_NOTE_EXPORTED_CSV.matcher(notes);
            if (exported.matches()) {
                int n = Integer.parseInt(exported.group(1));
                return messageUtil.getMessage("audit.trail.note.csv_exported", locale, n);
            }
            Matcher userCrud = AUDIT_NOTE_USER_CRUD.matcher(notes);
            if (userCrud.matches()) {
                String kind = userCrud.group(1);
                String p0 = userCrud.group(2);
                String p1 = userCrud.group(3);
                String key = switch (kind) {
                    case "created" -> "audit.trail.note.user_created";
                    case "updated" -> "audit.trail.note.user_updated";
                    case "deleted" -> "audit.trail.note.user_deleted";
                    default -> null;
                };
                if (key != null) {
                    return messageUtil.getMessage(key, locale, p0, p1);
                }
            }
            Matcher profile = AUDIT_NOTE_PROFILE_UPDATE.matcher(notes);
            if (profile.matches()) {
                return messageUtil.getMessage("audit.trail.note.profile_update", locale, profile.group(1), profile.group(2));
            }
            Matcher refund = AUDIT_NOTE_REFUND.matcher(notes);
            if (refund.matches()) {
                return messageUtil.getMessage("audit.trail.note.refund_request", locale, refund.group(1), refund.group(2));
            }
            Matcher txCancel = AUDIT_NOTE_TX_CANCEL.matcher(notes);
            if (txCancel.matches()) {
                return messageUtil.getMessage("audit.trail.note.tx_cancel_request", locale, txCancel.group(1), txCancel.group(2));
            }
            Matcher addDisc = AUDIT_NOTE_ADD_DISCOUNT.matcher(notes);
            if (addDisc.matches()) {
                return messageUtil.getMessage("audit.trail.note.additional_discount_request", locale, addDisc.group(1), addDisc.group(2));
            }
            Matcher addDiscMgr = AUDIT_NOTE_ADD_DISCOUNT_MANAGER.matcher(notes);
            if (addDiscMgr.matches()) {
                return messageUtil.getMessage(
                        "audit.trail.note.additional_discount_request_by_manager", locale, addDiscMgr.group(1), addDiscMgr.group(2));
            }
        } catch (NoSuchMessageException e) {
            log.debug("Audit note localization skipped: {}", e.getMessage());
        }
        return notes;
    }

    /**
     * Calculates start and end date times based on a period string.
     * 
     * @param period The period string (DAILY, TODAY, 30_DAYS, 3_MONTHS, 6_MONTHS)
     * @param userLocale The user locale for error messages
     * @return An array of LocalDateTime where [0] is startDateTime and [1] is endDateTime
     * @throws ResponseStatusException if the period is invalid
     */
    private LocalDateTime[] calculateDateRangeFromPeriod(String period, Locale userLocale) {
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        LocalDateTime startDateTime;
        LocalDateTime endDateTime;
        
        switch (period.toUpperCase()) {
            case "DAILY":
            case "TODAY":
                startDateTime = now.withHour(0).withMinute(0).withSecond(0).withNano(0);
                endDateTime = now.withHour(23).withMinute(59).withSecond(59).withNano(999_999_000);
                break;
            case "30_DAYS":
                startDateTime = now.minusDays(30).withHour(0).withMinute(0).withSecond(0).withNano(0);
                endDateTime = now.withHour(23).withMinute(59).withSecond(59).withNano(999_999_000);
                break;
            case "3_MONTHS":
                startDateTime = now.minusMonths(3).withHour(0).withMinute(0).withSecond(0).withNano(0);
                endDateTime = now.withHour(23).withMinute(59).withSecond(59).withNano(999_999_000);
                break;
            case "6_MONTHS":
                startDateTime = now.minusMonths(6).withHour(0).withMinute(0).withSecond(0).withNano(0);
                endDateTime = now.withHour(23).withMinute(59).withSecond(59).withNano(999_999_000);
                break;
            default:
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        messageUtil.getMessage("reports.error.invalid.period", userLocale));
        }
        
        return new LocalDateTime[]{startDateTime, endDateTime};
    }

    private void ensureRestaurantExistsForAudit(UUID restaurantId, Locale userLocale) {
        if (restaurantId != null) {
            restaurantRepository.findById(restaurantId)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                            messageUtil.getMessage("restaurant.not.found", userLocale)));
        }
    }

    private void ensureRestaurantGroupExistsForAudit(UUID restaurantGroupId, Locale userLocale) {
        if (restaurantGroupId != null) {
            restaurantGroupRepository.findById(restaurantGroupId)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                            messageUtil.getMessage("restaurant.group.not.found", userLocale)));
        }
    }

    private ActionType parseActionTypeFilter(String actionType, Locale userLocale) {
        if (actionType == null || actionType.isBlank()) {
            return null;
        }
        try {
            return ActionType.valueOf(actionType.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    messageUtil.getMessage("error.invalid.actionType", userLocale, actionType));
        }
    }

    private RequestStatus parseStatusFilter(String status, Locale userLocale) {
        if (status == null || status.isBlank() || "ALL".equalsIgnoreCase(status)) {
            return null;
        }
        try {
            return RequestStatus.valueOf(status.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    messageUtil.getMessage("error.invalid.status", userLocale, status));
        }
    }

    /**
     * Resolves inclusive {@code [start, end]} bounds for audit filtering: single day, explicit range, named period,
     * or a wide default when no filter is provided.
     *
     * @param date       optional calendar day (whole day in local semantics of the caller)
     * @param startDate  optional range start (time normalized to start of day when paired with {@code endDate})
     * @param endDate    optional range end (time normalized to end of day when paired with {@code startDate})
     * @param period     optional preset period key understood by {@link #calculateDateRangeFromPeriod}
     * @param userLocale locale for period parsing errors/messages
     * @return two-element array {@code [startInclusive, endInclusive]}
     */
    private LocalDateTime[] resolveAuditTrailFilterDateRange(
            LocalDate date,
            LocalDateTime startDate,
            LocalDateTime endDate,
            String period,
            Locale userLocale) {
        if (date != null) {
            return new LocalDateTime[]{
                    date.atStartOfDay(),
                    date.atTime(23, 59, 59, 999_999_000)
            };
        }
        if (startDate != null && endDate != null) {
            return new LocalDateTime[]{
                    startDate.withHour(0).withMinute(0).withSecond(0).withNano(0),
                    endDate.withHour(23).withMinute(59).withSecond(59).withNano(999_999_000)
            };
        }
        if (period != null && !period.isBlank()) {
            return calculateDateRangeFromPeriod(period, userLocale);
        }
        return new LocalDateTime[]{
                LocalDateTime.of(1900, 1, 1, 0, 0, 0),
                LocalDateTime.of(2100, 12, 31, 23, 59, 59, 999_999_000)
        };
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional(readOnly = true)
    public ResponseDto<AuditTrailListResponse> getAuditTrails(
            Integer page,
            Integer size,
            UUID userId,
            UUID restaurantId,
            UUID restaurantGroupId,
            String actionType,
            String status,
            String module,
            String role,
            String search,
            String period,
            LocalDate date,
            LocalDateTime startDate,
            LocalDateTime endDate,
            String sortBy,
            String direction,
            String userRole,
            String userIdHeader,
            String locale) {

        log.info("Getting audit trails (page: {}, size: {}, userId: {}, restaurantId: {}, restaurantGroupId: {}, actionType: {}, status: {}, module: {}, role: {}, search: {}, period: {}, date: {}, startDate: {}, endDate: {})",
                page, size, userId, restaurantId, restaurantGroupId, actionType, status, module, role, search, period, date, startDate, endDate);

        Locale userLocale = LocaleContextHolder.getLocale();

        // Validate authorization and apply manager/cashier restrictions
        UUID[] filteredIds = validateAuthorizationAndApplyRestrictions(userRole, userIdHeader, 
                restaurantId, restaurantGroupId, userLocale, null);
        restaurantId = filteredIds[0];
        restaurantGroupId = filteredIds[1];

        ensureRestaurantExistsForAudit(restaurantId, userLocale);

        ActionType actionTypeEnum = parseActionTypeFilter(actionType, userLocale);
        RequestStatus statusEnum = parseStatusFilter(status, userLocale);

        // Map module name to entity type for filtering
        // Frontend sends module names like "Restaurant Management", "Menu Management", etc.
        // but we need to filter by entity_type in the database
        String entityTypeForFilter = mapModuleToEntityType(module);

        LocalDateTime[] auditDateRange = resolveAuditTrailFilterDateRange(date, startDate, endDate, period, userLocale);
        LocalDateTime startDateTime = auditDateRange[0];
        LocalDateTime endDateTime = auditDateRange[1];

        // Pagination
        boolean noPaging = (page == null || size == null || page <= 0 || size <= 0);
        Pageable pageable;

        // Convert Java property name to database column name for native query
        String dbSortField = (sortBy != null && !sortBy.isBlank()) ? sortBy : "createdAt";
        if ("requestDate".equalsIgnoreCase(dbSortField)) {
            dbSortField = "createdAt";
        }
        
        // Check if sorting requires in-memory processing (user/userName sorting)
        // Similar to UserServiceImpl pattern for employeeName sorting
        boolean requiresInMemorySorting = "user".equalsIgnoreCase(dbSortField) || 
                                          "username".equalsIgnoreCase(dbSortField);
        
        // Map Java property names to database column names for native SQL
        // For native queries, Spring Data JPA needs the actual database column names
        String dbColumnName = dbSortField;
        if (!requiresInMemorySorting) {
            switch (dbSortField.toLowerCase()) {
                case "createdat":
                    dbColumnName = "created_at";
                    break;
                case "updatedat":
                    dbColumnName = "updated_at";
                    break;
                case "actiontype":
                    dbColumnName = "action_type";
                    break;
                case "lognumber":
                    dbColumnName = "log_number";
                    break;
                case "entityid":
                    dbColumnName = "entity_id";
                    break;
                case "entitytype":
                    dbColumnName = "entity_type";
                    break;
                case "userid":
                    dbColumnName = "user_id";
                    break;
                case "restaurantid":
                    dbColumnName = "restaurant_id";
                    break;
                case "requestedat":
                    dbColumnName = "requested_at";
                    break;
                case "reviewedat":
                    dbColumnName = "reviewed_at";
                    break;
                default:
                    // If it's already in snake_case or unknown, use as-is
                    dbColumnName = dbSortField;
            }
        } else {
            // For user sorting, use created_at as fallback for initial query
            // We'll sort by user name in-memory after converting to DTOs
            dbColumnName = "created_at";
        }

        if (!noPaging && !requiresInMemorySorting) {
            if (direction == null || direction.isBlank()) {
                direction = "DESC";
            }
            Sort.Direction sortDirection = direction.equalsIgnoreCase("ASC") ?
                    Sort.Direction.ASC : Sort.Direction.DESC;
            // Use database column name for native query sorting
            pageable = PageRequest.of(page - 1, size, Sort.by(sortDirection, dbColumnName));
        } else if (requiresInMemorySorting) {
            // Fetch all records for in-memory sorting (no pagination at DB level)
            pageable = Pageable.unpaged();
        } else {
            pageable = Pageable.unpaged();
        }

        // Search term
        String searchTerm = (search != null && !search.isBlank()) ? search.trim() : null;

        // Query with filters - convert enums to strings for native query
        String actionTypeStr = (actionTypeEnum != null) ? actionTypeEnum.name() : null;
        String statusStr = (statusEnum != null) ? statusEnum.name() : null;
        // Use entityTypeForFilter instead of module for database query
        String moduleStr = entityTypeForFilter; // This will be used as entity_type in the query
        String roleStr = (role != null && !role.isBlank()) ? role.trim() : null;
        // For MANAGER, exclude restricted action types at DB so pagination returns requested page size
        List<String> excludedActionTypes = null;
        if (ROLE_MANAGER.equalsIgnoreCase(userRole)) {
            excludedActionTypes = getManagerExcludedActionTypes().stream()
                    .map(ActionType::name)
                    .collect(Collectors.toList());
        }
        Page<AuditTrail> trailsPage = (excludedActionTypes != null && !excludedActionTypes.isEmpty())
                ? auditTrailRepository.findWithFiltersExcludingActionTypes(
                        userId, restaurantId, restaurantGroupId, actionTypeStr, statusStr, moduleStr, roleStr, startDateTime, endDateTime, searchTerm, excludedActionTypes, pageable)
                : auditTrailRepository.findWithFilters(
                        userId, restaurantId, restaurantGroupId, actionTypeStr, statusStr, moduleStr, roleStr, startDateTime, endDateTime, searchTerm, pageable);

        // Convert to response DTOs (manager exclusion already applied at DB when MANAGER)
        List<AuditTrailResponse> logs = trailsPage.getContent().stream()
                .map(this::convertToResponse)
                .collect(Collectors.toList());

        // Apply in-memory sorting for user/userName (following UserServiceImpl pattern)
        if (requiresInMemorySorting) {
            Sort.Direction sortDirection = (direction == null || direction.isBlank() || "DESC".equalsIgnoreCase(direction)) ?
                    Sort.Direction.DESC : Sort.Direction.ASC;
            
            Comparator<AuditTrailResponse> comp = Comparator.comparing(
                dto -> {
                    String userName = dto.getUserName() != null ? dto.getUserName() : "";
                    return userName.trim().toLowerCase();
                },
                Comparator.nullsLast(Comparator.naturalOrder())
            );
            if (sortDirection == Sort.Direction.DESC) {
                comp = comp.reversed();
            }
            logs.sort(comp);
        }

        // Get summary counts
        Long pendingCount = auditTrailRepository.countByStatus(RequestStatus.OPEN);
        Long approvedCount = auditTrailRepository.countByStatus(RequestStatus.APPROVED);
        Long rejectedCount = auditTrailRepository.countByStatus(RequestStatus.DECLINED);
        Long closedCount = 0L; // If you have a CLOSED status, add it to RequestStatus enum

        // If restaurantId is provided, get counts for that restaurant
        if (restaurantId != null) {
            pendingCount = auditTrailRepository.countByRestaurantIdAndStatus(restaurantId, RequestStatus.OPEN);
            approvedCount = auditTrailRepository.countByRestaurantIdAndStatus(restaurantId, RequestStatus.APPROVED);
            rejectedCount = auditTrailRepository.countByRestaurantIdAndStatus(restaurantId, RequestStatus.DECLINED);
        } else if (restaurantGroupId != null) {
            // If restaurantGroupId is provided, get counts for that restaurant group
            pendingCount = auditTrailRepository.countByRestaurantGroupIdAndStatus(restaurantGroupId, RequestStatus.OPEN);
            approvedCount = auditTrailRepository.countByRestaurantGroupIdAndStatus(restaurantGroupId, RequestStatus.APPROVED);
            rejectedCount = auditTrailRepository.countByRestaurantGroupIdAndStatus(restaurantGroupId, RequestStatus.DECLINED);
        }

        // Apply pagination manually for in-memory sorting (following UserServiceImpl pattern)
        long totalRecords;
        int totalPages;
        List<AuditTrailResponse> paginatedLogs;
        
        if (requiresInMemorySorting && !noPaging) {
            // Manual pagination for in-memory sorted results
            totalRecords = logs.size();
            totalPages = (int) Math.ceil((double) totalRecords / size);
            int fromIndex = Math.min((page - 1) * size, logs.size());
            int toIndex = Math.min(fromIndex + size, logs.size());
            paginatedLogs = logs.subList(fromIndex, toIndex);
        } else {
            // Already paginated by database or no pagination needed
            totalRecords = trailsPage.getTotalElements();
            totalPages = trailsPage.getTotalPages();
            paginatedLogs = logs;
        }

        AuditTrailListResponse listResponse = AuditTrailListResponse.builder()
                .logs(paginatedLogs)
                .count((long) paginatedLogs.size())
                .total(totalRecords)
                .metaData(noPaging ? null : PaginationMetaData.builder()
                        .page(page)
                        .size(size)
                        .totalPages(totalPages)
                        .totalRecords(totalRecords)
                        .build())
                .pendingCount(pendingCount)
                .approvedCount(approvedCount)
                .rejectedCount(rejectedCount)
                .closedCount(closedCount)
                .build();

        return ResponseDto.<AuditTrailListResponse>builder()
                .message(messageUtil.getMessage("audit.trail.list.success", userLocale))
                .data(listResponse)
                .build();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional(readOnly = true)
    public void exportAuditTrailsToCsv(
            UUID userId,
            UUID restaurantId,
            UUID restaurantGroupId,
            String actionType,
            String status,
            String module,
            String role,
            String search,
            String period,
            LocalDate date,
            LocalDateTime startDate,
            LocalDateTime endDate,
            String userRole,
            String userIdHeader,
            String locale,
            HttpServletResponse response) throws IOException {

        Locale userLocale = Locale.forLanguageTag(locale != null ? locale : "en");
        LocaleContextHolder.setLocale(userLocale);

        // Validate authorization and apply manager/cashier restrictions
        UUID[] filteredIds = validateAuthorizationAndApplyRestrictions(userRole, userIdHeader, 
                restaurantId, restaurantGroupId, userLocale, "CSV export");
        restaurantId = filteredIds[0];
        restaurantGroupId = filteredIds[1];

        log.info("Exporting audit trails to CSV (userId: {}, restaurantId: {}, restaurantGroupId: {}, actionType: {}, status: {}, module: {}, role: {}, search: {}, period: {}, date: {}, startDate: {}, endDate: {})",
                userId, restaurantId, restaurantGroupId, actionType, status, module, role, search, period, date, startDate, endDate);

        ensureRestaurantExistsForAudit(restaurantId, userLocale);
        ensureRestaurantGroupExistsForAudit(restaurantGroupId, userLocale);

        ActionType actionTypeEnum = parseActionTypeFilter(actionType, userLocale);
        RequestStatus statusEnum = parseStatusFilter(status, userLocale);

        LocalDateTime[] auditDateRange = resolveAuditTrailFilterDateRange(date, startDate, endDate, period, userLocale);
        LocalDateTime startDateTime = auditDateRange[0];
        LocalDateTime endDateTime = auditDateRange[1];

        // Search term
        String searchTerm = (search != null && !search.isBlank()) ? search.trim() : null;

        // Map module name to entity type for CSV export filtering
        String entityTypeForCsvFilter = mapModuleToEntityType(module);

        // Query with filters - no pagination for CSV export
        String actionTypeStr = (actionTypeEnum != null) ? actionTypeEnum.name() : null;
        String statusStr = (statusEnum != null) ? statusEnum.name() : null;
        String moduleStr = entityTypeForCsvFilter; // Use mapped entity type for filtering
        String roleStr = (role != null && !role.isBlank()) ? role.trim() : null;
        Pageable pageable = Pageable.unpaged();
        List<String> excludedActionTypes = null;
        if (ROLE_MANAGER.equalsIgnoreCase(userRole)) {
            excludedActionTypes = getManagerExcludedActionTypes().stream()
                    .map(ActionType::name)
                    .collect(Collectors.toList());
        }
        Page<AuditTrail> trailsPage = (excludedActionTypes != null && !excludedActionTypes.isEmpty())
                ? auditTrailRepository.findWithFiltersExcludingActionTypes(
                        userId, restaurantId, restaurantGroupId, actionTypeStr, statusStr, moduleStr, roleStr, startDateTime, endDateTime, searchTerm, excludedActionTypes, pageable)
                : auditTrailRepository.findWithFilters(
                        userId, restaurantId, restaurantGroupId, actionTypeStr, statusStr, moduleStr, roleStr, startDateTime, endDateTime, searchTerm, pageable);

        // Convert to response DTOs (manager exclusion already applied at DB when MANAGER)
        List<AuditTrailResponse> logs = trailsPage.getContent().stream()
                .map(this::convertToResponse)
                .collect(Collectors.toList());

        // Generate filename with timestamp
        String timestamp = LocalDateTime.now(ZoneOffset.UTC).format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
        String filename = "audit_trails_" + timestamp + ".csv";

        // Set response headers
        response.setContentType("text/csv;charset=UTF-8");
        response.setCharacterEncoding("UTF-8");
        response.setHeader("Content-Disposition", "attachment; filename=\"" + filename + "\"");

        // Get output stream and write BOM for Excel compatibility
        java.io.OutputStream outputStream = response.getOutputStream();
        // Write UTF-8 BOM (0xEF 0xBB 0xBF)
        outputStream.write(0xEF);
        outputStream.write(0xBB);
        outputStream.write(0xBF);

        // Create CSV printer
        try (CSVPrinter csvPrinter = new CSVPrinter(
                new OutputStreamWriter(outputStream, StandardCharsets.UTF_8),
                CSVFormat.DEFAULT)) {

            // Write Header Section
            csvPrinter.printRecord(messageUtil.getMessage("csv.audit.export.title", userLocale));
            csvPrinter.printRecord(
                    messageUtil.getMessage("csv.export.date", userLocale),
                    LocalDateTime.now(ZoneOffset.UTC).format(DATE_TIME_FORMATTER));
            if (date != null) {
                csvPrinter.printRecord(messageUtil.getMessage("csv.audit.report.date", userLocale), date.toString());
            } else {
                csvPrinter.printRecord(messageUtil.getMessage("csv.audit.start.date", userLocale), startDateTime.format(DATE_TIME_FORMATTER));
                csvPrinter.printRecord(messageUtil.getMessage("csv.audit.end.date", userLocale), endDateTime.format(DATE_TIME_FORMATTER));
            }
            csvPrinter.printRecord(messageUtil.getMessage("csv.audit.total.records", userLocale), logs.size());
            csvPrinter.printRecord();

            // Write Data Section with headers
            csvPrinter.printRecord(
                    messageUtil.getMessage("csv.audit.col.timestamp", userLocale),
                    messageUtil.getMessage("csv.audit.col.log.number", userLocale),
                    messageUtil.getMessage("csv.audit.col.user", userLocale),
                    messageUtil.getMessage("csv.audit.col.user.code", userLocale),
                    messageUtil.getMessage("csv.audit.col.role", userLocale),
                    messageUtil.getMessage("csv.audit.col.action.type", userLocale),
                    messageUtil.getMessage("csv.audit.col.module", userLocale),
                    messageUtil.getMessage("csv.audit.col.reference.id", userLocale),
                    messageUtil.getMessage("csv.audit.col.description", userLocale),
                    messageUtil.getMessage("csv.audit.col.status", userLocale),
                    messageUtil.getMessage("csv.audit.col.restaurant", userLocale),
                    messageUtil.getMessage("csv.audit.col.ip.address", userLocale),
                    messageUtil.getMessage("csv.audit.col.reviewed.by", userLocale),
                    messageUtil.getMessage("csv.audit.col.reviewed.at", userLocale),
                    messageUtil.getMessage("csv.audit.col.created.at", userLocale)
            );

            // Write data rows
            for (AuditTrailResponse log : logs) {
                csvPrinter.printRecord(
                        log.getTimestamp() != null ? log.getTimestamp().format(DATE_TIME_FORMATTER) : "",
                        log.getLogNumber() != null ? log.getLogNumber() : "",
                        log.getUserName() != null ? log.getUserName() : "",
                        log.getUserCode() != null ? log.getUserCode() : "",
                        log.getRole() != null ? log.getRole() : "",
                        log.getActionType() != null ? log.getActionType() : "",
                        log.getModule() != null ? log.getModule() : "",
                        log.getEntityId() != null ? log.getEntityId().toString() : "",
                        log.getDescription() != null ? log.getDescription() : "",
                        log.getStatus() != null ? log.getStatus().name() : "",
                        log.getRestaurantName() != null ? log.getRestaurantName() : "",
                        log.getIpAddress() != null ? log.getIpAddress() : "",
                        log.getReviewedByName() != null ? log.getReviewedByName() : "",
                        log.getReviewedAt() != null ? log.getReviewedAt().format(DATE_TIME_FORMATTER) : "",
                        log.getCreatedAt() != null ? log.getCreatedAt().format(DATE_TIME_FORMATTER) : ""
                );
            }

            csvPrinter.flush();
        }

        log.info("Successfully exported {} audit trails to CSV", logs.size());
        
        // Create audit trail for report export
        try {
            if (userId != null) {
                User user = userRepository.findById(userId).orElse(null);
                Restaurant restaurant = restaurantId != null ? restaurantRepository.findById(restaurantId).orElse(null) : null;
                if (user != null) {
                    // Create audit trail directly using repository to avoid circular dependency
                    AuditTrail auditTrail = AuditTrail.builder()
                            .user(user)
                            .actionType(ActionType.REPORT_EXPORT)
                            .restaurant(restaurant)
                            .status(RequestStatus.NA)
                            .ipAddress(null)
                            .userAgent(null)
                            .entityId(null) // report export doesn't have a specific entity
                            .entityType(ENTITY_TYPE_AUDIT_TRAIL)
                            .notes(String.format("Exported %d audit trail records to CSV", logs.size()))
                            .requestedBy(null)
                            .requestedAt(null)
                            .reviewedBy(null)
                            .reviewedAt(null)
                            .createdAt(OffsetDateTime.now(ZoneOffset.UTC))
                            .build();
                    auditTrailRepository.save(auditTrail);
                }
            }
        } catch (Exception e) {
            log.error("Failed to create audit trail for report export: {}", e.getMessage());
        }
    }

    /**
     * Reads {@code user_id} or {@code created_by} without initializing orphan {@link User} proxies
     * (customer card payments use a configured UUID with no {@code users} row).
     */
    private UUID fetchAuditTrailActorId(UUID auditTrailId, String column) {
        if (!"user_id".equals(column) && !"created_by".equals(column)) {
            return null;
        }
        try {
            Object result = entityManager.createNativeQuery(
                            "SELECT " + column + " FROM audit_trail WHERE id = :auditTrailId")
                    .setParameter(PARAM_AUDIT_TRAIL_ID, auditTrailId)
                    .getSingleResult();
            return result != null ? (UUID) result : null;
        } catch (Exception e) {
            log.debug("Could not fetch {} for audit trail {}: {}", column, auditTrailId, e.getMessage());
            return null;
        }
    }

    private boolean isOnlineCardActorId(UUID id) {
        if (id == null || !onlineCardPaymentProperties.isConfigured()) {
            return false;
        }
        try {
            return UUID.fromString(onlineCardPaymentProperties.getUserId().trim()).equals(id);
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    private String onlineCardActorDisplayName() {
        String name = onlineCardPaymentProperties.getUserName();
        return name != null && !name.isBlank() ? name.trim() : "Online Card Payment";
    }

    private String formatUserDisplayName(User user) {
        String firstName = user.getFirstName() != null ? user.getFirstName() : "";
        String lastName = user.getLastName() != null ? user.getLastName() : "";
        String name = (firstName + " " + lastName).trim();
        if (name.isEmpty()) {
            name = user.getUserCode() != null ? user.getUserCode() : "";
        }
        return name.isEmpty() ? null : name;
    }

    private String resolveActorDisplayName(UUID actorId) {
        if (actorId == null) {
            return null;
        }
        Optional<User> userOpt = userRepository.findById(actorId);
        if (userOpt.isPresent()) {
            return formatUserDisplayName(userOpt.get());
        }
        if (isOnlineCardActorId(actorId)) {
            return onlineCardActorDisplayName();
        }
        return null;
    }

    /**
     * Converts an AuditTrail entity to an AuditTrailResponse DTO.
     * Handles lazy loading of user, restaurant, restaurant group, reviewedBy, and createdBy relationships,
     * fetching them from the database if not already loaded.
     *
     * @param trail the AuditTrail entity to convert
     * @return an AuditTrailResponse DTO with all populated fields
     */
    private AuditTrailResponse convertToResponse(AuditTrail trail) {
        Locale locale = auditTrailResponseLocale();
        String userName = null;
        String userCode = null;
        UUID userId = fetchAuditTrailActorId(trail.getId(), "user_id");
        if (userId != null) {
            Optional<User> userOpt = userRepository.findById(userId);
            if (userOpt.isPresent()) {
                User user = userOpt.get();
                userCode = user.getUserCode();
                userName = formatUserDisplayName(user);
            } else if (isOnlineCardActorId(userId)) {
                userName = onlineCardActorDisplayName();
            }
        }

        String restaurantName = null;
        UUID restaurantId = null;
        String restaurantGroupName = null;
        UUID restaurantGroupId = null;
        Restaurant restaurant = trail.getRestaurant();
        
        if (restaurant == null) {
            if (trail.getActionType() == ActionType.RESTAURANT_CREATE && trail.getEntityType() != null 
                    && ENTITY_TYPE_RESTAURANT.equals(trail.getEntityType()) && trail.getEntityId() != null) {
                restaurantId = trail.getEntityId();
                restaurant = restaurantRepository.findById(restaurantId).orElse(null);
            } else {
                try {
                    Object result = entityManager.createNativeQuery(
                            "SELECT restaurant_id FROM audit_trail WHERE id = :auditTrailId")
                            .setParameter(PARAM_AUDIT_TRAIL_ID, trail.getId())
                            .getSingleResult();
                    if (result != null) {
                        restaurantId = (UUID) result;
                        restaurant = restaurantRepository.findById(restaurantId).orElse(null);
                    }
                } catch (Exception e) {
                    log.debug("Could not fetch restaurant_id for audit trail {}: {}", trail.getId(), e.getMessage());
                }
            }
        }
        
        if (restaurant != null) {
            restaurantId = restaurant.getId();
            List<RestaurantTranslation> translations = restaurantTranslationRepository
                    .findAllByRestaurantIdWithLanguage(restaurantId);
            if (!translations.isEmpty()) {
                restaurantName = translations.get(0).getName();
            } else {
                restaurantName = restaurant.getRestaurantCode();
            }
            
            // Get restaurant group information
            if (restaurant.getRestaurantGroup() != null) {
                restaurantGroupId = restaurant.getRestaurantGroup().getId();
                // Fetch restaurant group name from translations
                try {
                    List<RestaurantGroupTranslation> groupTranslations = 
                        restaurantGroupTranslationRepository.findAllByRestaurantGroupIdWithLanguage(restaurantGroupId);
                    if (!groupTranslations.isEmpty()) {
                        restaurantGroupName = groupTranslations.get(0).getName();
                    } else {
                        restaurantGroupName = restaurant.getRestaurantGroup().getRestaurantGroupCode();
                    }
                } catch (Exception e) {
                    log.debug("Could not fetch restaurant group name for group {}: {}", restaurantGroupId, e.getMessage());
                    restaurantGroupName = restaurant.getRestaurantGroup().getRestaurantGroupCode();
                }
            }
        }

        String reviewedByName = null;
        UUID reviewedById = null;
        if (trail.getReviewedBy() != null) {
            reviewedById = trail.getReviewedBy().getId();
            String firstName = trail.getReviewedBy().getFirstName() != null ? trail.getReviewedBy().getFirstName() : "";
            String lastName = trail.getReviewedBy().getLastName() != null ? trail.getReviewedBy().getLastName() : "";
            reviewedByName = (firstName + " " + lastName).trim();
            if (reviewedByName.isEmpty()) {
                reviewedByName = trail.getReviewedBy().getUserCode() != null ? trail.getReviewedBy().getUserCode() : "";
            }
        }

        UUID createdById = fetchAuditTrailActorId(trail.getId(), "created_by");
        String createdByName = resolveActorDisplayName(createdById);

        // Get role name
        String role = null;
        if (userId != null) {
            Optional<User> userForRole = userRepository.findById(userId);
            if (userForRole.isPresent() && userForRole.get().getRoleId() != null) {
                role = roleRepository.findById(userForRole.get().getRoleId())
                        .map(r -> r.getName())
                        .orElse(null);
            }
        }

        // Get transaction ID (when entityType is TRANSACTION)
        UUID transactionId = null;
        if (ENTITY_TYPE_TRANSACTION.equalsIgnoreCase(trail.getEntityType()) && trail.getEntityId() != null) {
            transactionId = trail.getEntityId();
        }

        // Get table and section names (when entityType is ORDER)
        String tableName = null;
        String sectionName = null;
        if (ENTITY_TYPE_ORDER.equalsIgnoreCase(trail.getEntityType()) && trail.getEntityId() != null) {
            try {
                // Use a query that fetches the order with table, row, and section relationships
                Order order = orderRepository.findByIdWithTableAndSection(trail.getEntityId()).orElse(null);
                if (order != null && order.getRestaurantTable() != null) {
                    // Get table order number
                    Integer tableOrder = order.getRestaurantTable().getTableOrder();
                    if (tableOrder != null) {
                        String label = localizedAuditMessage("audit.trail.table_label", locale, tableOrder);
                        tableName = label != null ? label : ("Table " + tableOrder);
                    }
                    
                    // Get section name - navigate through relationships
                    if (order.getRestaurantTable().getRestaurantRow() != null) {
                        var row = order.getRestaurantTable().getRestaurantRow();
                        if (row.getRestaurantSection() != null) {
                            var section = row.getRestaurantSection();
                            
                            // Get section name from translations with locale preference (same locale as audit row copy)
                            final String sectionLanguage = (locale.getLanguage() != null && !locale.getLanguage().isBlank())
                                    ? locale.getLanguage()
                                    : "en";
                            
                            if (section.getTranslations() != null && !section.getTranslations().isEmpty()) {
                                // Try to find translation matching user's language, fallback to first available
                                sectionName = section.getTranslations().stream()
                                        .filter(t -> t.getLanguageCode() != null && 
                                                   t.getLanguageCode().toLowerCase().startsWith(sectionLanguage.toLowerCase()))
                                        .map(com.gulfnet.shared_library.entity.RestaurantSectionTranslation::getName)
                                        .findFirst()
                                        .orElse(section.getTranslations().get(0).getName());
                            }
                        }
                    }
                }
            } catch (Exception e) {
                log.debug("Failed to get table/section info for order {}: {}", trail.getEntityId(), e.getMessage());
            }
        }

        String module = null;
        if (trail.getEntityType() != null && !trail.getEntityType().isBlank()) {
            String entityTypeUpper = trail.getEntityType().toUpperCase(Locale.ROOT);
            module = localizedModuleForEntityType(entityTypeUpper, trail.getEntityType(), locale);
        }

        String localizedNotes = localizeStoredAuditNotes(trail.getNotes(), locale);

        StringBuilder descriptionBuilder = new StringBuilder();
        if (trail.getActionType() != null) {
            String actionLabel = localizedActionLabel(trail.getActionType(), locale);
            if (actionLabel != null && !actionLabel.isBlank()) {
                descriptionBuilder.append(actionLabel);
            }
        }
        if (trail.getEntityType() != null && !trail.getEntityType().isBlank()) {
            if (descriptionBuilder.length() > 0) {
                descriptionBuilder.append(" - ");
            }
            String entityLabel = localizedEntityTypeLabel(trail.getEntityType(), locale);
            descriptionBuilder.append(entityLabel != null ? entityLabel : trail.getEntityType());
        }
        if (trail.getEntityId() != null) {
            if (descriptionBuilder.length() > 0) {
                String idSuffix = localizedAuditMessage("audit.trail.description.id_suffix", locale, trail.getEntityId());
                if (idSuffix != null) {
                    descriptionBuilder.append(" ").append(idSuffix);
                } else {
                    descriptionBuilder.append(" (ID: ").append(trail.getEntityId()).append(")");
                }
            } else {
                String entityOnly = localizedAuditMessage("audit.trail.description.entity_id_only", locale, trail.getEntityId());
                descriptionBuilder.append(entityOnly != null ? entityOnly : ("Entity ID: " + trail.getEntityId()));
            }
        }
        if (localizedNotes != null && !localizedNotes.isBlank()) {
            if (descriptionBuilder.length() > 0) {
                descriptionBuilder.append(" - ");
            }
            descriptionBuilder.append(localizedNotes);
        }
        String description = descriptionBuilder.length() > 0 ? descriptionBuilder.toString() : null;

        return AuditTrailResponse.builder()
                .id(trail.getId())
                .logNumber(trail.getLogNumber())
                .actionType(trail.getActionType() != null ? trail.getActionType().name() : null)
                .userName(userName)
                .userCode(userCode)
                .userId(userId)
                .restaurantName(restaurantName)
                .restaurantId(restaurantId)
                .restaurantGroupName(restaurantGroupName)
                .restaurantGroupId(restaurantGroupId)
                .requestDate(trail.getCreatedAt() != null ? trail.getCreatedAt().toLocalDateTime() : null)
                .status(trail.getStatus())
                .notes(localizedNotes)
                .entityId(trail.getEntityId())
                .entityType(trail.getEntityType())
                .ipAddress(trail.getIpAddress())
                .userAgent(trail.getUserAgent())
                .reviewedByName(reviewedByName)
                .reviewedById(reviewedById)
                .reviewedAt(trail.getReviewedAt() != null ? trail.getReviewedAt().toLocalDateTime() : null)
                .createdByName(createdByName)
                .createdById(createdById)
                .createdAt(trail.getCreatedAt() != null ? trail.getCreatedAt().toLocalDateTime() : null)
                .updatedAt(trail.getUpdatedAt() != null ? trail.getUpdatedAt().toLocalDateTime() : null)
                .role(role)
                .transactionId(transactionId)
                .tableName(tableName)
                .sectionName(sectionName)
                .timestamp(trail.getCreatedAt() != null ? trail.getCreatedAt().toLocalDateTime() : null) // Timestamp for list/table view
                .module(module) // Module name
                .description(description) // Enhanced description
                .build();
    }

    // ==================== AUDIT TRAIL CREATION METHODS ====================

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional(propagation = Propagation.REQUIRED)
    public AuditTrail createAuditTrail(
            User user,
            ActionType actionType,
            Restaurant restaurant,
            RequestStatus status,
            String ipAddress,
            String userAgent,
            UUID entityId,
            String entityType,
            String notes,
            BigDecimal openingBalance,
            BigDecimal closingBalance,
            BigDecimal expectedBalance,
            BigDecimal discrepancyAmount,
            String discrepancyReason,
            User createdBy) {

        try {
            String logNumber = generateLogNumber();
            
            // Determine if this is a request-type action that needs approval workflow
            // Request-type actions: REFUND, CANCELLATION, DISCOUNT (when applied to order)
            // Non-request actions: All CREATE/UPDATE/DELETE operations, LOGIN, LOGOUT, PAYMENT, ORDER_MODIFICATION, SYSTEM_ACTION
            boolean isRequestTypeAction = actionType == ActionType.REFUND ||
                                         actionType == ActionType.CANCELLATION ||
                                         actionType == ActionType.DISCOUNT; // Only when discount is applied to order
            
            // Set status: When caller explicitly passes APPROVED or DECLINED (e.g. manager decision), use it so audit trail shows correct status.
            // Otherwise: request-type actions use provided status or OPEN; non-request actions use NA.
            RequestStatus finalStatus;
            if (status == RequestStatus.APPROVED || status == RequestStatus.DECLINED) {
                finalStatus = status;
            } else if (isRequestTypeAction) {
                finalStatus = status != null ? status : RequestStatus.OPEN;
            } else {
                finalStatus = RequestStatus.NA;
            }
            
            Restaurant attachedRestaurant = null;
            boolean isRestaurantCreate = (actionType == ActionType.RESTAURANT_CREATE);
            
            if (isRestaurantCreate) {
                attachedRestaurant = null;
            } else if (restaurant != null && restaurant.getId() != null) {
                attachedRestaurant = attachRestaurantEntity(restaurant.getId());
            }
            
            AuditTrail auditTrail = AuditTrail.builder()
                    .logNumber(logNumber)
                    .user(user)
                    .actionType(actionType)
                    .restaurant(attachedRestaurant)
                    .status(finalStatus)
                    .ipAddress(ipAddress)
                    .userAgent(userAgent)
                    .entityId(entityId)
                    .entityType(entityType)
                    .notes(notes)
                    .createdBy(createdBy != null ? createdBy : user)
                    .createdAt(OffsetDateTime.now(ZoneOffset.UTC))
                    .updatedAt(OffsetDateTime.now(ZoneOffset.UTC))
                    .build();

            AuditTrail saved = auditTrailRepository.save(auditTrail);
            auditTrailRepository.flush();
            
            UUID savedRestaurantId = saved.getRestaurant() != null ? saved.getRestaurant().getId() : null;
            
            if (restaurant != null && restaurant.getId() != null && savedRestaurantId == null && !isRestaurantCreate) {
                savedRestaurantId = updateAuditTrailRestaurantIdInternal(saved.getId(), restaurant.getId(), logNumber);
            }
            
            return saved;
        } catch (Exception e) {
            log.error("Failed to create audit trail for action {} by user {}: {}", 
                    actionType, user != null ? user.getUserCode() : "unknown", e.getMessage(), e);
            // Don't throw exception to avoid breaking the main operation
            return null;
        }
    }

    /**
     * Creates a new audit trail record with minimal details (no entity ID, type, or notes).
     * Delegates to the main createAuditTrail method with default values.
     *
     * @param user the user who performed the action
     * @param actionType the type of action performed
     * @param restaurant the restaurant associated with the action (may be null)
     * @param ipAddress the IP address of the user
     * @param userAgent the user agent string
     * @return the created AuditTrail entity, or null if creation fails
     */
    @Override
    @Transactional(propagation = Propagation.REQUIRED)
    public AuditTrail createAuditTrail(
            User user,
            ActionType actionType,
            Restaurant restaurant,
            String ipAddress,
            String userAgent) {
        return createAuditTrail(user, actionType, restaurant, RequestStatus.OPEN, 
                ipAddress, userAgent, null, null, null, null, null, null, null, null, user);
    }

    /**
     * Creates a new audit trail record with entity details and notes.
     * Delegates to the main createAuditTrail method with default values for cash drawer fields.
     *
     * @param user the user who performed the action
     * @param actionType the type of action performed
     * @param restaurant the restaurant associated with the action (may be null)
     * @param status the request status (for request-type actions) or null
     * @param ipAddress the IP address of the user
     * @param userAgent the user agent string
     * @param entityId the ID of the entity the action was performed on
     * @param entityType the type of entity (e.g., "RESTAURANT", "MENU")
     * @param notes additional notes about the action
     * @return the created AuditTrail entity, or null if creation fails
     */
    @Override
    @Transactional(propagation = Propagation.REQUIRED)
    public AuditTrail createAuditTrail(
            User user,
            ActionType actionType,
            Restaurant restaurant,
            RequestStatus status,
            String ipAddress,
            String userAgent,
            UUID entityId,
            String entityType,
            String notes) {
        return createAuditTrail(user, actionType, restaurant, status, ipAddress, userAgent, 
                entityId, entityType, notes, null, null, null, null, null, user);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional(propagation = Propagation.REQUIRED)
    public AuditTrail createCashDrawerAuditTrail(
            User user,
            ActionType actionType,
            Restaurant restaurant,
            RequestStatus status,
            String ipAddress,
            String userAgent,
            BigDecimal openingBalance,
            BigDecimal closingBalance,
            BigDecimal expectedBalance,
            BigDecimal discrepancyAmount,
            String discrepancyReason,
            String notes) {
        return createAuditTrail(user, actionType, restaurant, status, ipAddress, userAgent, 
                null, ENTITY_TYPE_CASH_DRAWER, notes, openingBalance, closingBalance, expectedBalance, 
                discrepancyAmount, discrepancyReason, user);
    }

    /**
     * Updates the restaurant_id field of an audit trail record for RESTAURANT_CREATE actions.
     * This is used when a restaurant is created and the restaurant_id needs to be set after the restaurant entity is saved.
     * Only updates the most recent audit trail record for the given entityId and actionType that has a null restaurant_id.
     *
     * @param entityId the ID of the restaurant entity
     * @param restaurantId the restaurant ID to set
     * @param actionType must be RESTAURANT_CREATE, otherwise the method returns without doing anything
     */
    @Override
    @Transactional(propagation = Propagation.REQUIRED)
    public void updateAuditTrailRestaurantId(UUID entityId, UUID restaurantId, ActionType actionType) {
        try {
            if (entityId == null || restaurantId == null || actionType != ActionType.RESTAURANT_CREATE) {
                return;
            }
            
            @SuppressWarnings("unchecked")
            List<Object> auditTrailIdResults = entityManager.createNativeQuery(
                    "SELECT id FROM audit_trail " +
                    "WHERE entity_id = :entityId " +
                    "  AND action_type = CAST(:actionType AS VARCHAR) " +
                    "  AND restaurant_id IS NULL " +
                    "ORDER BY created_at DESC LIMIT 1")
                    .setParameter("entityId", entityId)
                    .setParameter("actionType", actionType.name())
                    .getResultList();
            
            if (auditTrailIdResults.isEmpty()) {
                return;
            }
            
            UUID auditTrailId = (UUID) auditTrailIdResults.get(0);
            
            entityManager.createNativeQuery(
                    "UPDATE audit_trail SET restaurant_id = :restaurantId WHERE id = :auditTrailId")
                    .setParameter("restaurantId", restaurantId)
                    .setParameter(PARAM_AUDIT_TRAIL_ID, auditTrailId)
                    .executeUpdate();
            entityManager.flush();
        } catch (Exception e) {
            log.error("Failed to update restaurant_id for audit trail {}: {}", entityId, e.getMessage());
        }
    }

    /**
     * Updates the review information for an audit trail record (typically for request-type actions).
     * Sets the reviewedBy user, reviewedAt timestamp, status, and optional notes.
     * Uses REQUIRED propagation to join the caller's transaction.
     *
     * @param auditTrailId the ID of the audit trail record to update
     * @param reviewedBy the user who reviewed the audit trail
     * @param status the new request status (e.g., APPROVED, DECLINED)
     * @param notes optional notes about the review
     */
    @Override
    @Transactional(propagation = Propagation.REQUIRED)
    public void updateAuditTrailReview(UUID auditTrailId, User reviewedBy, RequestStatus status, String notes) {
        try {
            AuditTrail auditTrail = auditTrailRepository.findById(auditTrailId)
                    .orElse(null);
            
            if (auditTrail != null) {
                auditTrail.setReviewedBy(reviewedBy);
                auditTrail.setReviewedAt(OffsetDateTime.now(ZoneOffset.UTC));
                auditTrail.setStatus(status);
                if (notes != null) {
                    auditTrail.setNotes(notes);
                }
                auditTrail.setUpdatedAt(OffsetDateTime.now(ZoneOffset.UTC));
                auditTrailRepository.save(auditTrail);
                log.info("Audit trail {} reviewed by {} with status {}", 
                        auditTrail.getLogNumber(), reviewedBy.getUserCode(), status);
            }
        } catch (Exception e) {
            log.error("Failed to update audit trail review for {}: {}", auditTrailId, e.getMessage(), e);
        }
    }

    /**
     * Generates a unique log number in format: REQ + sequence number
     * Format: REQ + 5-digit sequence (e.g., REQ15097)
     * Uses database sequence audit_trail_seq for thread-safe unique number generation
     */
    private String generateLogNumber() {
        try {
            // Get next value from database sequence (thread-safe)
            Long sequenceNumber = auditTrailRepository.getNextSequenceValue();
            
            // Format as REQ + 5-digit number (e.g., REQ00001, REQ15097)
            return String.format("REQ%05d", sequenceNumber);
        } catch (Exception e) {
            log.error("Error generating log number from sequence, using timestamp-based fallback: {}", e.getMessage());
            // Fallback to timestamp-based number if sequence fails
            return "REQ" + System.currentTimeMillis() % 100000;
        }
    }

    /**
     * Retrieves all distinct action types available in audit trails, filtered by user role.
     * Managers are excluded from seeing certain action types (e.g., HQ_ADMIN-specific actions).
     * Returns a sorted list of action type names as strings.
     *
     * @param locale the locale for localized error messages
     * @param userRole the role of the requesting user (for filtering action types)
     * @return a response containing a sorted list of available action type names
     * @throws ResponseStatusException if fetching action types fails
     */
    @Override
    @Transactional(readOnly = true)
    public ResponseDto<List<String>> getAvailableActionTypes(String locale, String userRole) {
        Locale userLocale = LocaleContextHolder.getLocale();
        if (locale != null && !locale.isBlank()) {
            try {
                userLocale = Locale.forLanguageTag(locale);
            } catch (Exception e) {
                log.warn("Invalid locale provided: {}, using default", locale);
            }
        }

        log.info("Fetching available action types for audit trail filter (userRole: {})", userRole);

        try {
            // Get distinct action types from database (action types that actually exist)
            List<ActionType> distinctActionTypes = auditTrailRepository.findDistinctActionTypes();
            
            // Filter out action types that managers should not see
            if (ROLE_MANAGER.equalsIgnoreCase(userRole)) {
                Set<ActionType> excludedTypes = getManagerExcludedActionTypes();
                distinctActionTypes = distinctActionTypes.stream()
                        .filter(actionType -> !excludedTypes.contains(actionType))
                        .collect(Collectors.toList());
            }
            
            // Convert to list of strings
            List<String> actionTypeNames = distinctActionTypes.stream()
                    .map(ActionType::name)
                    .sorted()
                    .collect(Collectors.toList());

            log.info("Found {} distinct action types in audit trail for role {}", actionTypeNames.size(), userRole);

            return ResponseDto.<List<String>>builder()
                    .message(messageUtil.getMessage("audit.trail.action.types.fetch.success", userLocale))
                    .data(actionTypeNames)
                    .build();
        } catch (Exception e) {
            log.error("Failed to fetch available action types: {}", e.getMessage(), e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    messageUtil.getMessage("audit.trail.action.types.fetch.error", userLocale));
        }
    }

    /**
     * Helper method to attach restaurant entity with multiple fallback strategies.
     * Tries to find restaurant using different methods with error handling.
     */
    private Restaurant attachRestaurantEntity(UUID restaurantId) {
        try {
            Restaurant restaurant = restaurantRepository.findById(restaurantId).orElse(null);
            if (restaurant != null) {
                return restaurant;
            }
        } catch (Exception e) {
            log.warn("Failed to fetch restaurant {}: {}", restaurantId, e.getMessage());
        }
        
        // Try getReferenceById as fallback
        try {
            return restaurantRepository.getReferenceById(restaurantId);
        } catch (jakarta.persistence.EntityNotFoundException refEx) {
            // Try entityManager.getReference as last resort
            try {
                return entityManager.getReference(Restaurant.class, restaurantId);
            } catch (Exception refEx2) {
                log.warn("Failed to attach restaurant {}: {}", restaurantId, refEx2.getMessage());
                return null;
            }
        }
    }

    /**
     * Helper method to update audit trail restaurant ID with error handling.
     * Returns the restaurant ID if update was successful, null otherwise.
     */
    private UUID updateAuditTrailRestaurantIdInternal(UUID auditTrailId, UUID restaurantId, String logNumber) {
        try {
            int updated = entityManager.createNativeQuery(
                    "UPDATE audit_trail SET restaurant_id = :restaurantId WHERE id = :auditTrailId")
                    .setParameter("restaurantId", restaurantId)
                    .setParameter(PARAM_AUDIT_TRAIL_ID, auditTrailId)
                    .executeUpdate();
            entityManager.flush();
            if (updated > 0) {
                return restaurantId;
            }
        } catch (Exception e) {
            log.error("Failed to update restaurant_id for audit trail {}: {}", logNumber, e.getMessage());
        }
        return null;
    }
}

