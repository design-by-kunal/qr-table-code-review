package com.gulfnet.restaurantmanagement.service.impl;

import com.gulfnet.restaurantmanagement.service.CashDrawerService;
import com.gulfnet.restaurantmanagement.service.NotificationService;
import com.gulfnet.restaurantmanagement.service.OrderValidationService;
import com.gulfnet.restaurantmanagement.util.MessageUtil;
import com.gulfnet.shared_library.entity.CashDrawer;
import com.gulfnet.shared_library.entity.CashDrawerLog;
import com.gulfnet.shared_library.entity.CashDrawerTranslation;
import com.gulfnet.shared_library.entity.CashierShift;
import com.gulfnet.shared_library.entity.Restaurant;
import com.gulfnet.shared_library.entity.Role;
import com.gulfnet.shared_library.entity.Shift;
import com.gulfnet.shared_library.entity.ShiftTranslation;
import com.gulfnet.shared_library.entity.Transaction;
import com.gulfnet.shared_library.entity.User;
import com.gulfnet.shared_library.enums.DrawerEventType;
import com.gulfnet.shared_library.enums.EntityStatus;
import com.gulfnet.shared_library.enums.ShiftStatus;
import com.gulfnet.shared_library.enums.ActionType;
import com.gulfnet.shared_library.enums.RequestStatus;
import com.gulfnet.shared_library.model.request.ApproveShiftRequest;
import com.gulfnet.shared_library.model.request.CashierDiscrepancyReasonRequest;
import com.gulfnet.shared_library.model.request.CloseShiftRequest;
import com.gulfnet.shared_library.model.request.CreateCashDrawerRequest;
import com.gulfnet.shared_library.model.request.ManualDrawerEventRequest;
import com.gulfnet.shared_library.model.request.StartShiftRequest;
import com.gulfnet.shared_library.model.request.CashDrawerTranslationRequest;
import com.gulfnet.shared_library.model.request.UpdateCashDrawerRequest;
import com.gulfnet.shared_library.model.response.dto.CashDrawerTranslationResponse;
import com.gulfnet.shared_library.model.response.dto.CashDrawerListResponse;
import com.gulfnet.shared_library.model.response.dto.CashDrawerLogListResponse;
import com.gulfnet.shared_library.model.response.dto.CashDrawerLogResponse;
import com.gulfnet.shared_library.model.response.dto.CashDrawerResponse;
import com.gulfnet.shared_library.model.response.dto.CashierOpenShiftResponse;
import com.gulfnet.shared_library.model.response.dto.CashierShiftListResponse;
import com.gulfnet.shared_library.model.response.dto.CashierShiftResponse;
import com.gulfnet.shared_library.model.response.dto.PaginationMetaData;
import com.gulfnet.shared_library.model.response.dto.ResponseDto;
import com.gulfnet.shared_library.repository.CashDrawerLogRepository;
import com.gulfnet.shared_library.repository.CashDrawerRepository;
import com.gulfnet.shared_library.repository.CashDrawerTranslationRepository;
import com.gulfnet.shared_library.repository.CashierShiftRepository;
import com.gulfnet.shared_library.repository.LoginAuditRepository;
import com.gulfnet.shared_library.repository.RestaurantRepository;
import com.gulfnet.shared_library.repository.RoleRepository;
import com.gulfnet.shared_library.repository.ShiftRepository;
import com.gulfnet.shared_library.repository.ShiftTranslationRepository;
import com.gulfnet.shared_library.repository.TransactionRepository;
import com.gulfnet.shared_library.repository.UserRepository;
import com.gulfnet.shared_library.util.CashDrawerTranslationUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class CashDrawerServiceImpl implements CashDrawerService {

    // Constants for message keys
    private static final String MSG_USER_NOT_FOUND = "user.not.found";
    private static final String MSG_CASH_DRAWER_NOT_FOUND = "cash.drawer.not.found";
    private static final String MSG_CASH_DRAWER_HAS_ACTIVE_SHIFT = "cash.drawer.has.active.shift";
    
    private static final String SORT_FIELD_CREATED_AT = "createdAt";

    private final CashDrawerRepository cashDrawerRepository;
    private final CashDrawerTranslationRepository cashDrawerTranslationRepository;
    private final CashierShiftRepository cashierShiftRepository;
    private final CashDrawerLogRepository cashDrawerLogRepository;
    private final RestaurantRepository restaurantRepository;
    private final UserRepository userRepository;
    private final ShiftRepository shiftRepository;
    private final ShiftTranslationRepository shiftTranslationRepository;
    private final TransactionRepository transactionRepository;
    private final RoleRepository roleRepository;
    private final LoginAuditRepository loginAuditRepository;
    private final NotificationService notificationService;
    private final OrderValidationService orderValidationService;
    private final MessageUtil messageUtil;
    private final com.gulfnet.restaurantmanagement.service.AuditTrailService auditTrailService;

    @PersistenceContext
    private EntityManager entityManager;

    /**
     * Creates a new cash drawer for a restaurant.
     * Validates translation names per language within the restaurant and serial number uniqueness globally.
     */
    @Override
    @Transactional
    public ResponseDto<CashDrawerResponse> createCashDrawer(String userId, CreateCashDrawerRequest request, String locale) {
        Locale userLocale = LocaleContextHolder.getLocale();

        log.info("Creating cash drawer for restaurant: {} serial: {}", request.getRestaurantId(), request.getSerialNumber());

        Restaurant restaurant = restaurantRepository.findById(request.getRestaurantId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        messageUtil.getMessage("restaurant.not.found", userLocale)));

        validateCashDrawerTranslationRequests(request.getTranslations(), userLocale);

        if (cashDrawerRepository.existsBySerialNumber(request.getSerialNumber().trim())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    messageUtil.getMessage("cash.drawer.serial.number.exists", userLocale, request.getSerialNumber()));
        }

        for (CashDrawerTranslationRequest tr : request.getTranslations()) {
            String lang = tr.getLanguageCode().trim();
            String nm = tr.getName().trim();
            if (cashDrawerTranslationRepository.countDuplicateNameInRestaurantForLanguage(
                    request.getRestaurantId(), nm, lang, null) > 0) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        messageUtil.getMessage("cash.drawer.name.exists", userLocale, nm));
            }
        }

        final CashDrawer cashDrawer = CashDrawer.builder()
                .restaurant(restaurant)
                .serialNumber(request.getSerialNumber().trim())
                .status(EntityStatus.ACTIVE)
                .build();

        List<CashDrawerTranslation> translationEntities = request.getTranslations().stream()
                .map(tr -> CashDrawerTranslation.builder()
                        .cashDrawer(cashDrawer)
                        .languageCode(tr.getLanguageCode().trim())
                        .name(tr.getName().trim())
                        .build())
                .collect(Collectors.toList());
        cashDrawer.setTranslations(translationEntities);

        CashDrawer savedCashDrawer = cashDrawerRepository.save(cashDrawer);

        CashDrawerResponse response = buildCashDrawerResponse(savedCashDrawer);

        return ResponseDto.<CashDrawerResponse>builder()
                .message(messageUtil.getMessage("cash.drawer.created.successfully", userLocale))
                .data(response)
                .build();
    }

    /**
     * Retrieves a paginated and filterable list of cash drawers for a restaurant.
     * Supports filtering by status, searching by name or serial number, sorting, and pagination.
     *
     * @param restaurantId the ID of the restaurant
     * @param status optional filter by entity status (ACTIVE, INACTIVE)
     * @param search optional search term to filter by drawer name or serial number
     * @param page the page number (1-based, will be converted to 0-based)
     * @param size the page size
     * @param sortBy the field to sort by (defaults to "name")
     * @param sortDirection the sort direction (ASC or DESC, defaults to ASC)
     * @param locale the locale for localized error messages
     * @return a response containing a paginated list of cash drawers
     * @throws ResponseStatusException if validation fails or invalid sort field
     */
    @Override
    @Transactional(readOnly = true)
    public ResponseDto<CashDrawerListResponse> getCashDrawers(
            UUID restaurantId,
            String status,
            String search,
            Integer page,
            Integer size,
            String sortBy,
            String sortDirection,
            String locale) {
        Locale userLocale = LocaleContextHolder.getLocale();

        // Parse status filter
        EntityStatus statusFilter = null;
        if (status != null && !status.isEmpty()) {
            try {
                statusFilter = EntityStatus.valueOf(status.toUpperCase());
            } catch (IllegalArgumentException e) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        messageUtil.getMessage("cash.drawer.status.invalid", userLocale));
            }
        }

        // Normalize search term
        String searchTerm = (search != null && !search.trim().isEmpty()) ? search.trim() : null;

        // Determine sort field and direction
        String dbSortField = (sortBy != null && !sortBy.isBlank()) ? sortBy : "name";
        // Validate sort field
        if (!isValidSortField(dbSortField)) {
            dbSortField = "name"; // Default to name if invalid
        }

        Sort.Direction direction = (sortDirection != null && sortDirection.equalsIgnoreCase("DESC"))
                ? Sort.Direction.DESC : Sort.Direction.ASC;

        // Handle pagination
        boolean noPaging = (page == null || size == null || page <= 0 || size <= 0);
        Pageable pageable = noPaging
                ? Pageable.unpaged()
                : PageRequest.of(page - 1, size, Sort.by(direction, dbSortField));

        Page<CashDrawer> drawerPage = cashDrawerRepository.findByRestaurantIdWithFilters(
                restaurantId, statusFilter, searchTerm, pageable, dbSortField, direction);

        List<CashDrawerResponse> drawerResponses = drawerPage.getContent().stream()
                .map(this::buildCashDrawerResponse)
                .collect(Collectors.toList());

        // Build pagination metadata
        PaginationMetaData pagination = noPaging ? null : PaginationMetaData.builder()
                .page(page)
                .size(size)
                .totalRecords(drawerPage.getTotalElements())
                .totalPages(drawerPage.getTotalPages())
                .build();

        CashDrawerListResponse response = CashDrawerListResponse.builder()
                .cashDrawers(drawerResponses)
                .count((long) drawerResponses.size())
                .total(drawerPage.getTotalElements())
                .pagination(pagination)
                .build();

        return ResponseDto.<CashDrawerListResponse>builder()
                .message(messageUtil.getMessage("cash.drawers.retrieved.successfully", userLocale))
                .data(response)
                .build();
    }

    /**
     * Validate sort field for CashDrawer
     */
    private boolean isValidSortField(String sortField) {
        return sortField != null && (
                sortField.equalsIgnoreCase("name") ||
                sortField.equalsIgnoreCase("serialNumber") ||
                sortField.equalsIgnoreCase("status") ||
                sortField.equalsIgnoreCase(SORT_FIELD_CREATED_AT) ||
                sortField.equalsIgnoreCase("updatedAt")
        );
    }

    /**
     * Retrieves a simple list of active cash drawers for a restaurant (for selection purposes).
     * Returns only ACTIVE drawers, sorted by name ascending, without pagination.
     *
     * @param restaurantId the ID of the restaurant
     * @param locale the locale for localized error messages
     * @return a response containing a list of active cash drawers
     */
    @Override
    @Transactional(readOnly = true)
    public ResponseDto<CashDrawerListResponse> getDrawerList(UUID restaurantId, String locale) {
        Locale userLocale = LocaleContextHolder.getLocale();

        List<CashDrawer> drawers = cashDrawerRepository.findActiveDrawersByRestaurantIdOrderByEnglishName(restaurantId);

        List<CashDrawerResponse> drawerResponses = drawers.stream()
                .map(this::buildCashDrawerResponse)
                .collect(Collectors.toList());

        CashDrawerListResponse response = CashDrawerListResponse.builder()
                .cashDrawers(drawerResponses)
                .count((long) drawerResponses.size())
                .total((long) drawerResponses.size())
                .pagination(null)
                .build();

        return ResponseDto.<CashDrawerListResponse>builder()
                .message(messageUtil.getMessage("cash.drawers.retrieved.successfully", userLocale))
                .data(response)
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    /**
     * Retrieves a cash drawer by id.
     *
     * @param drawerId cash drawer identifier
     * @return response containing the cash drawer details (including resolved localized name and translations)
     * @throws ResponseStatusException when the drawer does not exist
     */
    public ResponseDto<CashDrawerResponse> getCashDrawerById(UUID drawerId) {
        Locale userLocale = LocaleContextHolder.getLocale();

        CashDrawer cashDrawer = cashDrawerRepository.findById(drawerId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        messageUtil.getMessage(MSG_CASH_DRAWER_NOT_FOUND, userLocale)));

        CashDrawerResponse data = buildCashDrawerResponse(cashDrawer);
        return ResponseDto.<CashDrawerResponse>builder()
                .message(messageUtil.getMessage("cash.drawer.retrieved.successfully", userLocale))
                .data(data)
                .build();
    }

    /**
     * Updates the status of a cash drawer (ACTIVE or INACTIVE).
     * Prevents status update if the drawer has an active shift assigned.
     *
     * @param drawerId the ID of the cash drawer to update
     * @param status the new status (ACTIVE or INACTIVE)
     * @param userId the ID of the user updating the status
     * @param locale the locale for localized error messages
     * @return a response containing the updated cash drawer
     * @throws ResponseStatusException if drawer not found or has an active shift
     */
    @Override
    @Transactional
    public ResponseDto<CashDrawerResponse> updateCashDrawerStatus(UUID drawerId, String status, String userId, String locale) {
        Locale userLocale = LocaleContextHolder.getLocale();

        CashDrawer cashDrawer = cashDrawerRepository.findById(drawerId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        messageUtil.getMessage(MSG_CASH_DRAWER_NOT_FOUND, userLocale)));

        // Check if drawer has an active shift (is assigned)
        if (cashierShiftRepository.hasActiveShiftByDrawerId(drawerId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    messageUtil.getMessage(MSG_CASH_DRAWER_HAS_ACTIVE_SHIFT, userLocale));
        }

        EntityStatus newStatus = EntityStatus.valueOf(status.toUpperCase());
        cashDrawer.setStatus(newStatus);
        cashDrawer = cashDrawerRepository.save(cashDrawer);

        CashDrawerResponse response = buildCashDrawerResponse(cashDrawer);

        return ResponseDto.<CashDrawerResponse>builder()
                .message(messageUtil.getMessage("cash.drawer.updated.successfully", userLocale))
                .data(response)
                .build();
    }

    @Override
    @Transactional
    /**
     * Updates a cash drawer's serial number and translations.
     * <p>
     * Update is rejected when the drawer has an active shift. Translations are validated (non-empty, unique languages,
     * and English required), and duplicate names within the same restaurant/language are prevented. Existing
     * translations are bulk-deleted and the persistence context is flushed before inserting new translations to avoid
     * uniqueness races.
     * </p>
     *
     * @param drawerId cash drawer identifier
     * @param request  update payload containing new serial number and translations
     * @param userId   acting user id (string UUID)
     * @param locale   locale/language tag for localized messages
     * @return response containing the updated drawer
     * @throws ResponseStatusException when validation fails, drawer not found, or drawer has an active shift
     */
    public ResponseDto<CashDrawerResponse> updateCashDrawer(
            UUID drawerId, UpdateCashDrawerRequest request, String userId, String locale) {
        Locale userLocale = LocaleContextHolder.getLocale();

        CashDrawer cashDrawer = cashDrawerRepository.findById(drawerId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        messageUtil.getMessage(MSG_CASH_DRAWER_NOT_FOUND, userLocale)));

        if (cashierShiftRepository.hasActiveShiftByDrawerId(drawerId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    messageUtil.getMessage(MSG_CASH_DRAWER_HAS_ACTIVE_SHIFT, userLocale));
        }

        String newSerial = request.getSerialNumber().trim();
        UUID restaurantId = cashDrawer.getRestaurant().getId();

        validateCashDrawerTranslationRequests(request.getTranslations(), userLocale);

        for (CashDrawerTranslationRequest tr : request.getTranslations()) {
            String lang = tr.getLanguageCode().trim();
            String nm = tr.getName().trim();
            if (cashDrawerTranslationRepository.countDuplicateNameInRestaurantForLanguage(
                    restaurantId, nm, lang, drawerId) > 0) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        messageUtil.getMessage("cash.drawer.name.exists", userLocale, nm));
            }
        }

        if (!newSerial.equals(cashDrawer.getSerialNumber())
                && cashDrawerRepository.existsBySerialNumberAndIdNot(newSerial, drawerId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    messageUtil.getMessage("cash.drawer.serial.number.exists", userLocale, newSerial));
        }

        // Bulk delete + flush so INSERTs cannot race old rows (unique drawer+language).
        // With orphanRemoval, do not replace the translations collection with a new List — Hibernate
        // must keep the same collection instance; only clear() or first-time setTranslations(null -> new).
        cashDrawerTranslationRepository.deleteAllByCashDrawer_Id(drawerId);
        if (cashDrawer.getTranslations() == null) {
            cashDrawer.setTranslations(new ArrayList<>());
        } else {
            cashDrawer.getTranslations().clear();
        }
        entityManager.flush();

        for (CashDrawerTranslationRequest tr : request.getTranslations()) {
            cashDrawer.getTranslations().add(
                    CashDrawerTranslation.builder()
                            .cashDrawer(cashDrawer)
                            .languageCode(tr.getLanguageCode().trim())
                            .name(tr.getName().trim())
                            .build());
        }
        cashDrawer.setSerialNumber(newSerial);
        cashDrawer = cashDrawerRepository.save(cashDrawer);

        CashDrawerResponse response = buildCashDrawerResponse(cashDrawer);
        return ResponseDto.<CashDrawerResponse>builder()
                .message(messageUtil.getMessage("cash.drawer.updated.successfully", userLocale))
                .data(response)
                .build();
    }

    @Override
    @Transactional
    /**
     * Permanently deletes a cash drawer.
     * <p>
     * Deletion is rejected when the drawer has an active shift or any historical shifts associated with it.
     * </p>
     *
     * @param drawerId cash drawer identifier
     * @param userId   acting user id (string UUID)
     * @param locale   locale/language tag for localized messages
     * @return response with a localized success message
     * @throws ResponseStatusException when drawer not found, has an active shift, or has historical shifts
     */
    public ResponseDto<Void> deleteCashDrawer(UUID drawerId, String userId, String locale) {
        Locale userLocale = LocaleContextHolder.getLocale();

        CashDrawer cashDrawer = cashDrawerRepository.findById(drawerId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        messageUtil.getMessage(MSG_CASH_DRAWER_NOT_FOUND, userLocale)));

        if (cashierShiftRepository.hasActiveShiftByDrawerId(drawerId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    messageUtil.getMessage(MSG_CASH_DRAWER_HAS_ACTIVE_SHIFT, userLocale));
        }
        if (cashierShiftRepository.countByCashDrawerId(drawerId) > 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    messageUtil.getMessage("cash.drawer.cannot.delete.has.shifts", userLocale));
        }

        cashDrawerRepository.delete(cashDrawer);
        return ResponseDto.<Void>builder()
                .message(messageUtil.getMessage("cash.drawer.deleted.successfully", userLocale))
                .data(null)
                .build();
    }

    /**
     * Starts a new cashier shift on a cash drawer.
     * Validates that the cashier and drawer exist, the drawer is ACTIVE, neither has an active shift,
     * and the opening balance is non-negative. Creates a new CashierShift with OPEN status and sends notifications to managers.
     *
     * @param userId the ID of the cashier starting the shift
     * @param request the shift start request containing cash drawer ID, opening balance, and optional shift ID
     * @param locale the locale for localized error messages
     * @return a response containing the created cashier shift
     * @throws ResponseStatusException if validation fails, drawer/cashier not found, drawer not active, or shift already exists
     */
    @Override
    @Transactional
    public ResponseDto<CashierShiftResponse> startShift(String userId, StartShiftRequest request, String locale) {
        Locale userLocale = LocaleContextHolder.getLocale();
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);

        log.info("Starting shift for cashier: {} on drawer: {}", userId, request.getCashDrawerId());

        // Validate cashier
        User cashier = userRepository.findById(UUID.fromString(userId))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        messageUtil.getMessage(MSG_USER_NOT_FOUND, userLocale)));

        // Validate cash drawer
        CashDrawer cashDrawer = cashDrawerRepository.findById(request.getCashDrawerId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        messageUtil.getMessage(MSG_CASH_DRAWER_NOT_FOUND, userLocale)));

        // Only ACTIVE drawers can be assigned
        if (cashDrawer.getStatus() != EntityStatus.ACTIVE) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    messageUtil.getMessage("cash.drawer.not.active", userLocale));
        }

        // Check if drawer already has an active shift (is already assigned)
        if (cashierShiftRepository.hasActiveShiftByDrawerId(cashDrawer.getId())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    messageUtil.getMessage("cash.drawer.already.assigned", userLocale));
        }

        // Check if cashier already has an active shift
        if (cashierShiftRepository.hasActiveShift(cashier.getId())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    messageUtil.getMessage("cashier.has.active.shift", userLocale));
        }

        // Validate opening balance
        if (request.getOpeningBalance().compareTo(BigDecimal.ZERO) < 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    messageUtil.getMessage("opening.balance.invalid", userLocale));
        }

        // Get shift definition if provided
        Shift shift = null;
        if (request.getShiftId() != null) {
            shift = shiftRepository.findById(request.getShiftId())
                    .orElse(null); // Optional, don't fail if not found
        }

        // Create cashier shift
        CashierShift cashierShift = CashierShift.builder()
                .cashDrawer(cashDrawer)
                .cashier(cashier)
                .restaurant(cashDrawer.getRestaurant())
                .shift(shift)
                .status(ShiftStatus.OPEN)
                .openingBalance(request.getOpeningBalance())
                .startedAt(now)
                .build();

        cashierShift = cashierShiftRepository.save(cashierShift);

        // Log opening balance event
        CashDrawerLog openingLog = CashDrawerLog.builder()
                .shift(cashierShift)
                .drawer(cashDrawer)
                .user(cashier)
                .eventType(DrawerEventType.OPENING_BALANCE)
                .amount(request.getOpeningBalance())
                .notes(request.getNotes())
                .createdAt(now)
                .createdBy(cashier)
                .build();

        cashDrawerLogRepository.save(openingLog);

        // Create audit trail for cash drawer shift start
        try {
            auditTrailService.createCashDrawerAuditTrail(
                    cashier,
                    ActionType.SYSTEM_ACTION, // Using SYSTEM_ACTION as there's no specific CASH_DRAWER_OPEN action type
                    cashDrawer.getRestaurant(),
                    RequestStatus.NA,
                    null, // ipAddress
                    null, // userAgent
                    request.getOpeningBalance(),
                    null, // closingBalance
                    null, // expectedBalance
                    null, // discrepancyAmount
                    null, // discrepancyReason
                    String.format("Cash drawer shift started. Opening balance: %s", request.getOpeningBalance())
            );
        } catch (Exception e) {
            log.error("Failed to create audit trail for cash drawer shift start: {}", e.getMessage(), e);
            // Don't break shift start flow if audit trail fails
        }

        // Notify managers when cashier starts a shift
        try {
            List<User> managers = getManagersForRestaurant(cashDrawer.getRestaurant().getId());
            if (!managers.isEmpty()) {
                notificationService.notifyCashDrawerShiftStarted(cashierShift, cashier, managers, userLocale);
            }
        } catch (Exception e) {
            log.error("Failed to send cash drawer shift started notification: {}", e.getMessage(), e);
            // Do not fail startShift if notification sending fails
        }

        CashierShiftResponse response = buildCashierShiftResponse(cashierShift);

        return ResponseDto.<CashierShiftResponse>builder()
                .message(messageUtil.getMessage("shift.started.successfully", userLocale))
                .data(response)
                .build();
    }

    /**
     * Closes a cashier shift by setting the closing balance and calculating discrepancy.
     * Validates user permissions (cashier can only close their own shift, manager can close any shift in their restaurant),
     * shift is OPEN, and closing balance is non-negative. Calculates expected balance and discrepancy,
     * sets shift status to PENDING_APPROVAL if there's a discrepancy, or APPROVED if no discrepancy.
     * Creates audit trail and sends notifications to managers.
     *
     * @param userId the ID of the user closing the shift (cashier or manager)
     * @param shiftId the ID of the shift to close
     * @param request the shift close request containing closing balance
     * @param locale the locale for localized error messages
     * @return a response containing the updated cashier shift
     * @throws ResponseStatusException if validation fails, shift not found, not owned by cashier, or already closed
     */
    @Override
    @Transactional
    public ResponseDto<CashierShiftResponse> closeShift(String userId, UUID shiftId, CloseShiftRequest request, String locale) {
        Locale userLocale = LocaleContextHolder.getLocale();
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);

        log.info("Closing shift: {} by user: {}", shiftId, userId);

        ShiftValidationResult validationResult = validateUserAndShiftForShiftOperation(userId, shiftId, userLocale);
        User user = validationResult.user();
        CashierShift cashierShift = validationResult.cashierShift();
        boolean isManager = validationResult.isManager();
        boolean isCashier = validationResult.isCashier();

        // If cashier, validate shift belongs to them
        if (isCashier && !cashierShift.getCashier().getId().equals(user.getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    messageUtil.getMessage("shift.not.owned.by.cashier", userLocale));
        }

        // If manager, validate they belong to the same restaurant as the shift
        if (isManager
                && (user.getRestaurantId() == null || !user.getRestaurantId().equals(cashierShift.getRestaurant().getId()))) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    messageUtil.getMessage("shift.close.manager.restaurant.mismatch", userLocale));
        }

        // Validate shift is open
        if (cashierShift.getStatus() != ShiftStatus.OPEN) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    messageUtil.getMessage("shift.already.closed", userLocale));
        }

        // Validate closing balance
        if (request.getClosingBalance().compareTo(BigDecimal.ZERO) < 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    messageUtil.getMessage("closing.balance.invalid", userLocale));
        }

        // Calculate expected closing balance
        BigDecimal expectedBalance = calculateExpectedBalance(cashierShift.getId());

        // Calculate discrepancy
        BigDecimal discrepancy = request.getClosingBalance().subtract(expectedBalance);

        // Update shift
        cashierShift.setClosingBalance(request.getClosingBalance());
        cashierShift.setExpectedClosingBalance(expectedBalance);
        cashierShift.setDiscrepancyAmount(discrepancy);
        
        // Determine status based on discrepancy and who is closing
        if (discrepancy.compareTo(BigDecimal.ZERO) == 0) {
            // No discrepancy - auto-complete for both cashier and manager
            cashierShift.setStatus(ShiftStatus.CLOSED);
        } else {
            // Has discrepancy
            if (isManager) {
                // Manager closes with discrepancy - auto-approve (manager can handle it themselves)
                // Set discrepancyReason from manager's notes for consistency and audit completeness
                // Manager's notes are also saved in the CashDrawerLog for detailed audit trail
                cashierShift.setStatus(ShiftStatus.APPROVED);
                cashierShift.setApprovedBy(user);
                cashierShift.setApprovedAt(now);
                // Set discrepancyReason from manager notes if provided
                if (request.getNotes() != null && !request.getNotes().trim().isEmpty()) {
                    cashierShift.setDiscrepancyReason(request.getNotes());
                }
            } else {
                // Cashier closes with discrepancy - requires manager approval
                cashierShift.setStatus(ShiftStatus.PENDING_APPROVAL);
                // Cashier will add discrepancy reason later via updateDiscrepancyReason endpoint
            }
        }
        cashierShift.setClosedAt(now);
        cashierShift.setUpdatedAt(now);

        cashierShift = cashierShiftRepository.save(cashierShift);

        // When a manager closes the shift, force-logout the cashier by invalidating their active session.
        // user-management validate-session requires a matching LoginAudit record; deleting it forces re-login.
        if (isManager) {
            try {
                if (cashierShift.getCashier() != null && cashierShift.getCashier().getId() != null) {
                    loginAuditRepository.deleteByUser_Id(cashierShift.getCashier().getId());
                }
            } catch (Exception e) {
                // Do not fail shift close if logout fails; worst case cashier will be logged out by client-side policies later.
                log.warn("Failed to invalidate cashier session after shift close (shiftId={}, cashierId={}): {}",
                        shiftId,
                        cashierShift.getCashier() != null ? cashierShift.getCashier().getId() : null,
                        e.getMessage());
            }
        }

        // Log closing balance and adjustment events
        logClosingBalanceAndAdjustment(cashierShift, user, request, discrepancy, isManager, now);

        // Create audit trail for cash drawer shift close
        createCloseShiftAuditTrail(cashierShift, request.getClosingBalance(), expectedBalance, discrepancy);

        // Send notifications based on who closed the shift
        sendCloseShiftNotifications(cashierShift, user, isManager, isCashier, userLocale);

        CashierShiftResponse response = buildCashierShiftResponse(cashierShift);

        return ResponseDto.<CashierShiftResponse>builder()
                .message(messageUtil.getMessage("shift.closed.successfully", userLocale))
                .data(response)
                .build();
    }

    /**
     * Approves or rejects a cashier shift that is pending approval.
     * Only managers can approve shifts, and they must belong to the same restaurant as the shift.
     * Updates shift status to APPROVED or REJECTED, sets approvedBy and approvedAt, creates audit trail, and sends notifications.
     *
     * @param managerId the ID of the manager approving/rejecting the shift
     * @param shiftId the ID of the shift to approve/reject
     * @param request the approval request containing action (APPROVED or REJECTED) and optional notes
     * @param locale the locale for localized error messages
     * @return a response containing the updated cashier shift
     * @throws ResponseStatusException if validation fails, manager not found, not a manager, restaurant mismatch, or shift not pending approval
     */
    @Override
    @Transactional
    public ResponseDto<CashierShiftResponse> approveShift(String managerId, UUID shiftId, ApproveShiftRequest request, String locale) {
        Locale userLocale = LocaleContextHolder.getLocale();
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);

        log.info("Approving shift: {} by manager: {}", shiftId, managerId);

        // Validate manager
        User manager = userRepository.findById(UUID.fromString(managerId))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        messageUtil.getMessage(MSG_USER_NOT_FOUND, userLocale)));

        // Validate user is a manager
        if (!orderValidationService.isManager(manager)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    messageUtil.getMessage("shift.approval.manager.only", userLocale));
        }

        // Get shift
        CashierShift cashierShift = cashierShiftRepository.findById(shiftId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        messageUtil.getMessage("shift.not.found", userLocale)));

        // Validate manager belongs to the same restaurant as the shift
        if (manager.getRestaurantId() == null || !manager.getRestaurantId().equals(cashierShift.getRestaurant().getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    messageUtil.getMessage("shift.approval.manager.restaurant.mismatch", userLocale));
        }

        // Validate shift is pending approval
        if (cashierShift.getStatus() != ShiftStatus.PENDING_APPROVAL) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    messageUtil.getMessage("shift.not.pending.approval", userLocale));
        }

        // Update shift based on manager action
        if (request.getAction() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    messageUtil.getMessage("shift.approval.action.required", userLocale));
        }

        String responseMessage;
        switch (request.getAction()) {
            case APPROVED -> {
                cashierShift.setStatus(ShiftStatus.APPROVED);
                cashierShift.setApprovedBy(manager);
                cashierShift.setApprovedAt(now);
                responseMessage = messageUtil.getMessage("shift.approved.successfully", userLocale);
            }
            case DECLINED -> {
                // Set status to REJECTED to show declined status in request list
                cashierShift.setStatus(ShiftStatus.REJECTED);
                cashierShift.setApprovedBy(manager);
                cashierShift.setApprovedAt(now);
                responseMessage = messageUtil.getMessage("shift.rejected.successfully", userLocale);
            }
            default -> throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    messageUtil.getMessage("shift.approval.action.invalid", userLocale));
        }

        cashierShift.setUpdatedAt(now);

        cashierShift = cashierShiftRepository.save(cashierShift);

        // Create audit trail for shift discrepancy approval/rejection
        try {
            ActionType actionType = request.getAction() == com.gulfnet.shared_library.enums.RequestStatus.APPROVED
                    ? ActionType.REQUEST_SHIFT_DISCREPANCY_APPROVE
                    : ActionType.REQUEST_SHIFT_DISCREPANCY_DECLINE;
            String notes = String.format("Shift discrepancy request %s by manager.",
                    request.getAction() == com.gulfnet.shared_library.enums.RequestStatus.APPROVED ? "approved" : "rejected");
            if (request.getNotes() != null && !request.getNotes().trim().isEmpty()) {
                notes += " Comments: " + request.getNotes().trim();
            }
            auditTrailService.createAuditTrail(
                    manager,
                    actionType,
                    cashierShift.getRestaurant(),
                    request.getAction(),
                    null,
                    null,
                    cashierShift.getId(),
                    "CASHIER_SHIFT",
                    notes
            );
        } catch (Exception e) {
            log.error("Failed to create audit trail for shift discrepancy approval/rejection: {}", e.getMessage(), e);
        }

        // Create adjustment log entry based on approval/rejection decision
        BigDecimal discrepancyAmount = cashierShift.getDiscrepancyAmount();
        if (discrepancyAmount != null && discrepancyAmount.compareTo(BigDecimal.ZERO) != 0) {
            // Business rule:
            // - If discrepancy is -20 (drawer has 20 less than expected), adjustment amount is +20
            // - If discrepancy is 20 (drawer has 20 more than expected), adjustment amount is -20
            BigDecimal adjustmentAmount = discrepancyAmount.negate();

            // Determine event type based on manager action
            DrawerEventType adjustmentEventType = request.getAction() == com.gulfnet.shared_library.enums.RequestStatus.APPROVED
                    ? DrawerEventType.ADJUSTMENT_APPROVED
                    : DrawerEventType.ADJUSTMENT_REJECTED;

            CashDrawerLog adjustmentLog = CashDrawerLog.builder()
                    .shift(cashierShift)
                    .drawer(cashierShift.getCashDrawer())
                    .user(manager)
                    .eventType(adjustmentEventType)
                    .amount(adjustmentAmount)
                    .notes(request.getNotes())
                    .createdAt(now)
                    .createdBy(manager)
                    .build();

            cashDrawerLogRepository.save(adjustmentLog);
        }

        // Send notification to cashier when manager approves/declines
        try {
            User cashier = cashierShift.getCashier();
            boolean isApproved = request.getAction() == com.gulfnet.shared_library.enums.RequestStatus.APPROVED;
            String comments = request.getNotes();
            notificationService.notifyCashDrawerShiftDiscrepancyDecision(cashierShift, cashier, isApproved, comments, manager, userLocale);
        } catch (Exception e) {
            log.error("Failed to send cash drawer shift discrepancy decision notification: {}", e.getMessage(), e);
            // Don't fail the request if notification fails
        }

        CashierShiftResponse response = buildCashierShiftResponse(cashierShift);

        return ResponseDto.<CashierShiftResponse>builder()
                .message(responseMessage)
                .data(response)
                .build();
    }

    /**
     * Retrieves a paginated list of shifts for a specific cashier, ordered by start time descending.
     *
     * @param userId the ID of the cashier
     * @param page the page number (1-based, will be converted to 0-based)
     * @param size the page size
     * @param locale the locale for localized error messages
     * @return a response containing a paginated list of cashier shifts
     * @throws ResponseStatusException if cashier not found
     */
    @Override
    @Transactional(readOnly = true)
    public ResponseDto<CashierShiftListResponse> getMyShifts(String userId, Integer page, Integer size, String locale) {
        Locale userLocale = LocaleContextHolder.getLocale();

        User cashier = userRepository.findById(UUID.fromString(userId))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        messageUtil.getMessage(MSG_USER_NOT_FOUND, userLocale)));

        boolean noPaging = (page == null || size == null || page <= 0 || size <= 0);
        Pageable pageable = noPaging
                ? Pageable.unpaged()
                : PageRequest.of(page - 1, size, Sort.by(Sort.Direction.DESC, "startedAt"));

        Page<CashierShift> shiftPage = cashierShiftRepository.findByCashierIdOrderByStartedAtDesc(
                cashier.getId(), pageable);

        List<CashierShiftResponse> shiftResponses = shiftPage.getContent().stream()
                .map(this::buildCashierShiftResponse)
                .collect(Collectors.toList());

        PaginationMetaData pagination = noPaging ? null : PaginationMetaData.builder()
                .page(page)
                .size(size)
                .totalRecords(shiftPage.getTotalElements())
                .totalPages(shiftPage.getTotalPages())
                .build();

        CashierShiftListResponse response = CashierShiftListResponse.builder()
                .shifts(shiftResponses)
                .pagination(pagination)
                .build();

        return buildShiftHistoryResponse(response, userLocale);
    }

    /**
     * Retrieves the active (open) shift for a specific cashier.
     * Validates that the requesting user is a manager or cashier, and if manager, they belong to the same restaurant as the cashier.
     * If cashier, they can only view their own shift.
     *
     * @param userId the ID of the requesting user
     * @param userRole the role of the requesting user
     * @param cashierId the ID of the cashier whose shift to retrieve
     * @param locale the locale for localized error messages
     * @return a response containing the active shift details (cashier shift ID, drawer ID, cashier ID)
     * @throws ResponseStatusException if validation fails, user/cashier not found, unauthorized, or no active shift exists
     */
    @Override
    @Transactional(readOnly = true)
    public ResponseDto<CashierOpenShiftResponse> getOpenShiftByCashierId(String userId, String userRole, UUID cashierId, String locale) {
        Locale userLocale = LocaleContextHolder.getLocale();

        // Validate requesting user exists
        User requestingUser = userRepository.findById(UUID.fromString(userId))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        messageUtil.getMessage(MSG_USER_NOT_FOUND, userLocale)));

        // Validate cashier exists
        User cashier = userRepository.findById(cashierId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        messageUtil.getMessage(MSG_USER_NOT_FOUND, userLocale)));

        // Check if requesting user is manager or cashier
        boolean isManager = orderValidationService.isManager(requestingUser);
        boolean isCashier = orderValidationService.isCashier(requestingUser);

        // Only managers and cashiers can view shifts
        if (!isManager && !isCashier) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    messageUtil.getMessage("shift.view.unauthorized", userLocale));
        }

        // If manager, validate they belong to the same restaurant as the cashier
        if (isManager
                && (requestingUser.getRestaurantId() == null || cashier.getRestaurantId() == null
                        || !requestingUser.getRestaurantId().equals(cashier.getRestaurantId()))) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    messageUtil.getMessage("shift.view.manager.restaurant.mismatch", userLocale));
        }

        // If cashier, validate they are viewing their own shift
        if (isCashier && !requestingUser.getId().equals(cashierId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    messageUtil.getMessage("shift.view.unauthorized", userLocale));
        }

        // Find active (open) shift for the cashier
        CashierShift activeShift = cashierShiftRepository.findActiveShiftByCashierId(cashierId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        messageUtil.getMessage("no.active.shift", userLocale)));

        // Build response with cashier shift id, drawer id, and cashier id
        CashierOpenShiftResponse response = CashierOpenShiftResponse.builder()
                .cashierShiftId(activeShift.getId())
                .cashDrawerId(activeShift.getCashDrawer().getId())
                .cashierId(cashierId)
                .build();

        return ResponseDto.<CashierOpenShiftResponse>builder()
                .message(messageUtil.getMessage("open.shift.retrieved.successfully", userLocale))
                .data(response)
                .build();
    }

    /**
     * Retrieves a paginated and filterable list of cashier shifts for a restaurant (manager-only).
     * Supports filtering by status, cash drawer, cashier, search term, and date range.
     * Only managers can access this endpoint, and they can only view shifts for their own restaurant.
     *
     * @param userId the ID of the manager
     * @param userRole the role of the user (must be MANAGER)
     * @param status optional filter by shift status
     * @param cashDrawerId optional filter by cash drawer ID
     * @param cashierId optional filter by cashier ID
     * @param search optional search term to filter by cashier name
     * @param page the page number (1-based, will be converted to 0-based)
     * @param size the page size
     * @param startDate optional start date for date range filter
     * @param endDate optional end date for date range filter
     * @param locale the locale for localized error messages
     * @return a response containing a paginated list of cashier shifts
     * @throws ResponseStatusException if validation fails, user not found, not a manager, or restaurant not assigned
     */
    @Override
    @Transactional(readOnly = true)
    public ResponseDto<CashierShiftListResponse> getRestaurantShiftListing(
            String userId,
            String userRole,
            String status,
            UUID cashDrawerId,
            UUID cashierId,
            String search,
            Integer page,
            Integer size,
            LocalDateTime startDate,
            LocalDateTime endDate,
            String locale) {

        Locale userLocale = LocaleContextHolder.getLocale();

        // Validate user exists
        User user = userRepository.findById(UUID.fromString(userId))
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        messageUtil.getMessage(MSG_USER_NOT_FOUND, userLocale)));

        // Validate user is a manager
        if (!orderValidationService.isManager(user)) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    messageUtil.getMessage("shift.listing.manager.only", userLocale));
        }

        // Validate user role header matches
        if (userRole == null || !"MANAGER".equalsIgnoreCase(userRole)) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    messageUtil.getMessage("shift.listing.manager.only", userLocale));
        }

        // Get manager's restaurant ID
        UUID managerRestaurantId = user.getRestaurantId();
        if (managerRestaurantId == null) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    messageUtil.getMessage("manager.no.restaurant.assigned", userLocale));
        }

        // Validate restaurant exists
        restaurantRepository.findById(managerRestaurantId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        messageUtil.getMessage("restaurant.not.found", userLocale)));

        // Validate cash drawer belongs to manager's restaurant if provided
        if (cashDrawerId != null) {
            CashDrawer cashDrawer = cashDrawerRepository.findById(cashDrawerId)
                    .orElseThrow(() -> new ResponseStatusException(
                            HttpStatus.NOT_FOUND,
                            messageUtil.getMessage(MSG_CASH_DRAWER_NOT_FOUND, userLocale)));
            if (!cashDrawer.getRestaurant().getId().equals(managerRestaurantId)) {
                throw new ResponseStatusException(
                        HttpStatus.FORBIDDEN,
                        messageUtil.getMessage("cash.drawer.not.belongs.to.restaurant", userLocale));
            }
        }

        // Validate cashier belongs to manager's restaurant if provided
        if (cashierId != null) {
            User cashier = userRepository.findById(cashierId)
                    .orElseThrow(() -> new ResponseStatusException(
                            HttpStatus.NOT_FOUND,
                            messageUtil.getMessage(MSG_USER_NOT_FOUND, userLocale)));
            if (cashier.getRestaurantId() == null || !cashier.getRestaurantId().equals(managerRestaurantId)) {
                throw new ResponseStatusException(
                        HttpStatus.FORBIDDEN,
                        messageUtil.getMessage("cashier.not.belongs.to.restaurant", userLocale));
            }
        }

        log.info("Manager {} accessing shift listing for their restaurant: {} with filters (cashDrawerId: {}, cashierId: {}, search: {})",
                userId, managerRestaurantId, cashDrawerId, cashierId, search);

        // Determine which internal statuses to include
        List<ShiftStatus> statusFilters;
        if (status == null || status.isBlank() || status.equalsIgnoreCase("ALL")) {
            statusFilters = List.of(ShiftStatus.OPEN, ShiftStatus.CLOSED, ShiftStatus.APPROVED);
        } else if (status.equalsIgnoreCase("OPEN")) {
            statusFilters = List.of(ShiftStatus.OPEN);
        } else if (status.equalsIgnoreCase("CLOSED")) {
            // "Closed" in the UI includes internally CLOSED and APPROVED
            statusFilters = List.of(ShiftStatus.CLOSED, ShiftStatus.APPROVED);
        } else {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    messageUtil.getMessage("shift.status.invalid", userLocale));
        }

        // Normalize search term (trim and set to null if empty)
        String searchTerm = (search != null && !search.trim().isEmpty()) ? search.trim() : null;

        // Apply default pagination values if not provided
        // Default: page=1, size=10
        int pageNumber = (page != null && page > 0) ? page : 1;
        int pageSize = (size != null && size > 0) ? size : 10;
        
        // Ensure pageSize has a reasonable maximum limit to prevent performance issues
        if (pageSize > 100) {
            pageSize = 100;
        }

        // Use unsorted Pageable since native query already has ORDER BY clause
        Pageable pageable = PageRequest.of(pageNumber - 1, pageSize);

        // Convert enum collection to string names for native query compatibility
        List<String> statusFilterStrings = statusFilters.stream()
                .map(Enum::name)
                .collect(Collectors.toList());

        // Use the new comprehensive repository method that handles all filters at DB level
        Page<CashierShift> shiftPage = cashierShiftRepository.findByRestaurantIdWithFilters(
                managerRestaurantId,
                cashDrawerId,
                cashierId,
                statusFilterStrings,
                startDate,
                endDate,
                searchTerm,
                pageable
        );

        // Map APPROVED status to CLOSED for UI display in manager listing (both represent closed shifts)
        // This mapping is only applied here, not globally, to preserve APPROVED status in other endpoints
        List<CashierShiftResponse> shiftResponses = shiftPage.getContent().stream()
                .map(shift -> {
                    CashierShiftResponse response = buildCashierShiftResponse(shift);
                    // Map APPROVED to CLOSED for manager listing UI
                    if (response.getStatus() == ShiftStatus.APPROVED) {
                        response.setStatus(ShiftStatus.CLOSED);
                    }
                    return response;
                })
                .collect(Collectors.toList());

        // Always return pagination metadata
        PaginationMetaData pagination = PaginationMetaData.builder()
                .page(pageNumber)
                .size(pageSize)
                .totalRecords(shiftPage.getTotalElements())
                .totalPages(shiftPage.getTotalPages())
                .build();

        CashierShiftListResponse response = CashierShiftListResponse.builder()
                .shifts(shiftResponses)
                .pagination(pagination)
                .build();

        return buildShiftHistoryResponse(response, userLocale);
    }

    /**
     * Build a standard shift history response with localized success message.
     */
    private ResponseDto<CashierShiftListResponse> buildShiftHistoryResponse(
            CashierShiftListResponse response,
            Locale userLocale) {
        return ResponseDto.<CashierShiftListResponse>builder()
                .message(messageUtil.getMessage("shift.history.retrieved.successfully", userLocale))
                .data(response)
                .build();
    }

    /**
     * Updates the discrepancy reason for a cashier shift.
     * Managers can update the reason without creating a discrepancy request.
     * Cashiers can update the reason when shift is PENDING_APPROVAL or REJECTED, and if REJECTED, status changes back to PENDING_APPROVAL.
     * Creates a discrepancy request notification to managers if updated by cashier.
     *
     * @param userId the ID of the user updating the discrepancy reason
     * @param userRole the role of the user (MANAGER or CASHIER)
     * @param shiftId the ID of the shift
     * @param request the discrepancy reason request containing the reason
     * @param locale the locale for localized error messages
     * @return a response containing the updated cashier shift
     * @throws ResponseStatusException if validation fails, shift not found, unauthorized, or shift not in correct status
     */
    @Override
    @Transactional
    public ResponseDto<CashierShiftResponse> updateDiscrepancyReason(String userId, String userRole, UUID shiftId,
                                                                     CashierDiscrepancyReasonRequest request,
                                                                     String locale) {
        Locale userLocale = LocaleContextHolder.getLocale();
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);

        ShiftValidationResult validationResult = validateUserAndShiftForShiftOperation(userId, shiftId, userLocale);
        User user = validationResult.user();
        CashierShift cashierShift = validationResult.cashierShift();
        boolean isManager = validationResult.isManager();
        boolean isCashier = validationResult.isCashier();

        if (isManager) {
            // Manager: only save discrepancy reason, do NOT create or resend discrepancy request

            // Ensure manager belongs to the same restaurant as the shift
            if (user.getRestaurantId() == null
                    || !user.getRestaurantId().equals(cashierShift.getRestaurant().getId())) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                        messageUtil.getMessage("shift.approval.manager.restaurant.mismatch", userLocale));
            }

            cashierShift.setDiscrepancyReason(request.getDiscrepancyReason());
            cashierShift.setUpdatedAt(now);

            cashierShift = cashierShiftRepository.save(cashierShift);

        } else {
            // Cashier: keep existing behavior - save reason and create discrepancy request to managers

            if (!cashierShift.getCashier().getId().equals(user.getId())) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                        messageUtil.getMessage("shift.not.owned.by.cashier", userLocale));
            }

            // Allow discrepancy reason update when shift is PENDING_APPROVAL or REJECTED
            // If REJECTED, cashier can update reason and status will change back to PENDING_APPROVAL
            if (cashierShift.getStatus() != ShiftStatus.PENDING_APPROVAL
                    && cashierShift.getStatus() != ShiftStatus.REJECTED) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        messageUtil.getMessage("shift.not.pending.or.rejected", userLocale));
            }

            cashierShift.setDiscrepancyReason(request.getDiscrepancyReason());

            // If status is REJECTED, change it back to PENDING_APPROVAL so manager can review again
            if (cashierShift.getStatus() == ShiftStatus.REJECTED) {
                cashierShift.setStatus(ShiftStatus.PENDING_APPROVAL);
                // Clear approval fields since it's being resubmitted
                cashierShift.setApprovedBy(null);
                cashierShift.setApprovedAt(null);
            }

            cashierShift.setUpdatedAt(now);

            cashierShift = cashierShiftRepository.save(cashierShift);

            // Send notification to managers when discrepancy reason is submitted
            try {
                List<User> managers = getManagersForRestaurant(cashierShift.getRestaurant().getId());
                if (!managers.isEmpty()) {
                    notificationService.notifyCashDrawerShiftDiscrepancyRequest(cashierShift, user, managers, userLocale);
                }
            } catch (Exception e) {
                log.error("Failed to send cash drawer shift discrepancy request notification: {}", e.getMessage(), e);
                // Don't fail the request if notification fails
            }
        }

        CashierShiftResponse response = buildCashierShiftResponse(cashierShift);

        return ResponseDto.<CashierShiftResponse>builder()
                .message(messageUtil.getMessage("shift.discrepancy.reason.updated.successfully", userLocale))
                .data(response)
                .build();
    }

    /**
     * Logs a manual cash drawer event (deposit or withdrawal) for the cashier's active shift.
     * Validates that the event type is MANUAL_DEPOSIT or MANUAL_WITHDRAWAL, reason is provided,
     * and the cashier has an active shift. Creates a CashDrawerLog entry and an audit trail record.
     *
     * @param userId the ID of the cashier logging the event
     * @param request the manual event request containing event type, amount, and reason
     * @param locale the locale for localized error messages
     * @return a response containing the created cash drawer log entry
     * @throws ResponseStatusException if validation fails, user not found, invalid event type, reason missing, or no active shift
     */
    @Override
    @Transactional
    public ResponseDto<CashDrawerLogResponse> logManualEvent(String userId, ManualDrawerEventRequest request, String locale) {
        Locale userLocale = LocaleContextHolder.getLocale();
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);

        log.info("Logging manual drawer event: {} by user: {}", 
                request.getEventType(), userId);

        // Validate user
        User user = userRepository.findById(UUID.fromString(userId))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        messageUtil.getMessage(MSG_USER_NOT_FOUND, userLocale)));

        // Validate event type is deposit or withdrawal
        if (request.getEventType() != DrawerEventType.MANUAL_DEPOSIT &&
            request.getEventType() != DrawerEventType.MANUAL_WITHDRAWAL) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    messageUtil.getMessage("invalid.manual.event.type", userLocale));
        }

        // Manual events should not be linked to transactions
        // Transaction-linked cash flows are handled by SALE_INFLOW events

        // Reason is required for all manual events
        if (request.getReason() == null || request.getReason().trim().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    messageUtil.getMessage("reason.required.for.manual.expense", userLocale));
        }

        log.info("Creating manual drawer event: {} - {} (reason: {})", 
                request.getEventType(), request.getAmount(), request.getReason());

        // Get active shift
        CashierShift activeShift = cashierShiftRepository.findActiveShiftByCashierId(user.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        messageUtil.getMessage("no.active.shift", userLocale)));

        // Determine amount sign
        BigDecimal amount = request.getAmount();
        if (request.getEventType() == DrawerEventType.MANUAL_WITHDRAWAL) {
            amount = amount.negate(); // Negative for withdrawal
        }

        // Create log entry (manual events are not linked to transactions)
        CashDrawerLog logEntry = CashDrawerLog.builder()
                .shift(activeShift)
                .drawer(activeShift.getCashDrawer())
                .user(user)
                .eventType(request.getEventType())
                .amount(amount)
                .transaction(null) // Manual events are not linked to transactions
                .reason(request.getReason())
                .notes(request.getNotes())
                .createdAt(now)
                .createdBy(user)
                .build();

        logEntry = cashDrawerLogRepository.save(logEntry);

        try {
            String eventLabel = request.getEventType() == DrawerEventType.MANUAL_DEPOSIT
                    ? "Manual deposit"
                    : "Manual withdrawal";
            String notes = String.format("%s. Amount: %s. Reason: %s",
                    eventLabel, request.getAmount(), request.getReason().trim());
            if (request.getNotes() != null && !request.getNotes().trim().isEmpty()) {
                notes = notes + ". Notes: " + request.getNotes().trim();
            }
            auditTrailService.createAuditTrail(
                    user,
                    ActionType.SYSTEM_ACTION,
                    activeShift.getRestaurant(),
                    RequestStatus.NA,
                    null,
                    null,
                    logEntry.getId(),
                    "CASH_DRAWER",
                    notes
            );
        } catch (Exception e) {
            log.error("Failed to create audit trail for manual drawer event: {}", e.getMessage(), e);
        }

        CashDrawerLogResponse response = buildCashDrawerLogResponse(logEntry);

        return ResponseDto.<CashDrawerLogResponse>builder()
                .message(messageUtil.getMessage("drawer.event.logged.successfully", userLocale))
                .data(response)
                .build();
    }

    /**
     * Retrieves a paginated list of cash drawer event logs.
     * Supports filtering by shift ID, drawer ID, event type, or date range (at least one filter must be provided).
     * Results are ordered by creation time descending (latest first) for all query types.
     *
     * @param userId the ID of the requesting user (for authorization, not used for filtering)
     * @param shiftId optional filter by shift ID
     * @param drawerId optional filter by drawer ID
     * @param eventType optional filter by event type
     * @param page the page number (1-based; defaults to 1)
     * @param size the page size (defaults to 20)
     * @param startDate optional start date for date range filter (requires endDate)
     * @param endDate optional end date for date range filter (requires startDate)
     * @param locale the locale for localized error messages
     * @return a response containing a paginated list of cash drawer log entries
     * @throws ResponseStatusException if no filter is provided (shiftId, drawerId, or date range required)
     */
    @Override
    @Transactional(readOnly = true)
    public ResponseDto<CashDrawerLogListResponse> getDrawerEventLogs(
            String userId,
            UUID shiftId,
            UUID drawerId,
            DrawerEventType eventType,
            Integer page,
            Integer size,
            LocalDateTime startDate,
            LocalDateTime endDate,
            String locale) {
        Locale userLocale = LocaleContextHolder.getLocale();

        int pageNumber = (page != null && page > 0) ? page : 1;
        int pageSize = size != null ? size : 20;
        Pageable pageable = PageRequest.of(pageNumber - 1, pageSize,
                Sort.by(Sort.Direction.DESC, SORT_FIELD_CREATED_AT));

        Page<CashDrawerLog> logPage;

        if (shiftId != null) {
            if (eventType != null) {
                List<CashDrawerLog> logs = cashDrawerLogRepository.findByShiftIdAndEventTypeOrderByCreatedAtDesc(shiftId, eventType);
                // Convert list to page
                int start = (int) pageable.getOffset();
                int end = Math.min((start + pageable.getPageSize()), logs.size());
                List<CashDrawerLog> pagedLogs = start < logs.size() ? logs.subList(start, end) : List.of();
                logPage = new org.springframework.data.domain.PageImpl<>(pagedLogs, pageable, logs.size());
            } else {
                logPage = cashDrawerLogRepository.findByShiftIdOrderByCreatedAtDesc(shiftId, pageable);
            }
        } else if (drawerId != null) {
            logPage = cashDrawerLogRepository.findByDrawerIdOrderByCreatedAtDesc(drawerId, pageable);
        } else if (startDate != null && endDate != null) {
            logPage = cashDrawerLogRepository.findByDateRange(startDate, endDate, pageable);
        } else {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    messageUtil.getMessage("shift.or.drawer.or.date.range.required", userLocale));
        }

        List<CashDrawerLogResponse> logResponses = logPage.getContent().stream()
                .map(this::buildCashDrawerLogResponse)
                .collect(Collectors.toList());

        PaginationMetaData pagination = PaginationMetaData.builder()
                .page(pageNumber)
                .size(logPage.getSize())
                .totalRecords(logPage.getTotalElements())
                .totalPages(logPage.getTotalPages())
                .build();

        CashDrawerLogListResponse response = CashDrawerLogListResponse.builder()
                .logs(logResponses)
                .pagination(pagination)
                .build();

        return ResponseDto.<CashDrawerLogListResponse>builder()
                .message(messageUtil.getMessage("drawer.logs.retrieved.successfully", userLocale))
                .data(response)
                .build();
    }

    /**
     * Retrieves a paginated list of cash drawer event logs for the requesting user.
     * Supports optional filtering by drawer ID, event type, and date range.
     * Results are ordered by creation time descending.
     *
     * @param userId the ID of the user whose logs to retrieve
     * @param drawerId optional filter by drawer ID
     * @param eventType optional filter by event type
     * @param page the page number (1-based, will be converted to 0-based)
     * @param size the page size
     * @param startDate optional start date for date range filter
     * @param endDate optional end date for date range filter
     * @param locale the locale for localized error messages
     * @return a response containing a paginated list of cash drawer log entries for the user
     * @throws ResponseStatusException if user not found
     */
    @Override
    @Transactional(readOnly = true)
    public ResponseDto<CashDrawerLogListResponse> getMyDrawerEventLogs(
            String userId,
            UUID drawerId,
            DrawerEventType eventType,
            Integer page,
            Integer size,
            LocalDateTime startDate,
            LocalDateTime endDate,
            String locale) {
        Locale userLocale = LocaleContextHolder.getLocale();

        // Validate user
        User user = userRepository.findById(UUID.fromString(userId))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        messageUtil.getMessage(MSG_USER_NOT_FOUND, userLocale)));

        boolean noPaging = (page == null || size == null || page <= 0 || size <= 0);
        Pageable pageable = noPaging
                ? Pageable.unpaged()
                : PageRequest.of(page - 1, size, Sort.by(Sort.Direction.DESC, SORT_FIELD_CREATED_AT));

        Page<CashDrawerLog> logPage;

        // Get all logs for the user first
        List<CashDrawerLog> allUserLogs = cashDrawerLogRepository.findByUserIdOrderByCreatedAtDesc(user.getId());
        
        // Apply filters: drawerId, eventType, and date range
        List<CashDrawerLog> filteredLogs = allUserLogs.stream()
                .filter(log -> {
                    // Filter by drawerId if provided
                    if (drawerId != null && !log.getDrawer().getId().equals(drawerId)) {
                        return false;
                    }
                    
                    // Filter by eventType if provided
                    if (eventType != null && log.getEventType() != eventType) {
                        return false;
                    }
                    
                    // Filter by date range if provided
                    if (startDate != null && endDate != null) {
                        LocalDateTime logDate = log.getCreatedAt() != null ? log.getCreatedAt().toLocalDateTime() : null;
                        boolean inDateRange = (logDate.isEqual(startDate) || logDate.isAfter(startDate)) &&
                                             (logDate.isEqual(endDate) || logDate.isBefore(endDate));
                        if (!inDateRange) {
                            return false;
                        }
                    }
                    
                    return true;
                })
                .collect(Collectors.toList());

        // Apply pagination if needed
        if (noPaging) {
            logPage = new org.springframework.data.domain.PageImpl<>(filteredLogs, pageable, filteredLogs.size());
        } else {
            int start = (int) pageable.getOffset();
            int end = Math.min((start + pageable.getPageSize()), filteredLogs.size());
            List<CashDrawerLog> pagedLogs = start < filteredLogs.size() ? filteredLogs.subList(start, end) : List.of();
            logPage = new org.springframework.data.domain.PageImpl<>(pagedLogs, pageable, filteredLogs.size());
        }

        List<CashDrawerLogResponse> logResponses = logPage.getContent().stream()
                .map(this::buildCashDrawerLogResponse)
                .collect(Collectors.toList());

        PaginationMetaData pagination = noPaging ? null : PaginationMetaData.builder()
                .page(page)
                .size(size)
                .totalRecords(logPage.getTotalElements())
                .totalPages(logPage.getTotalPages())
                .build();

        CashDrawerLogListResponse response = CashDrawerLogListResponse.builder()
                .logs(logResponses)
                .pagination(pagination)
                .build();

        return ResponseDto.<CashDrawerLogListResponse>builder()
                .message(messageUtil.getMessage("cashier.drawer.logs.retrieved.successfully", userLocale))
                .data(response)
                .build();
    }

    /**
     * Log closing balance event and, if there is a discrepancy, log an adjustment event.
     */
    private void logClosingBalanceAndAdjustment(CashierShift cashierShift, User user,
                                                 CloseShiftRequest request, BigDecimal discrepancy,
                                                 boolean isManager, OffsetDateTime now) {
        CashDrawerLog closingLog = CashDrawerLog.builder()
                .shift(cashierShift)
                .drawer(cashierShift.getCashDrawer())
                .user(user)
                .eventType(DrawerEventType.CLOSING_BALANCE)
                .amount(request.getClosingBalance())
                .notes(request.getNotes())
                .createdAt(now)
                .createdBy(user)
                .build();

        cashDrawerLogRepository.save(closingLog);

        if (discrepancy.compareTo(BigDecimal.ZERO) != 0) {
            // Invert the discrepancy for the adjustment amount
            BigDecimal adjustmentAmount = discrepancy.negate();

            DrawerEventType adjustmentEventType = isManager
                    ? DrawerEventType.ADJUSTMENT_APPROVED
                    : DrawerEventType.ADJUSTMENT_PENDING;

            CashDrawerLog adjustmentLog = CashDrawerLog.builder()
                    .shift(cashierShift)
                    .drawer(cashierShift.getCashDrawer())
                    .user(user)
                    .eventType(adjustmentEventType)
                    .amount(adjustmentAmount)
                    .notes(request.getNotes())
                    .createdAt(now)
                    .createdBy(user)
                    .build();

            cashDrawerLogRepository.save(adjustmentLog);
        }
    }

    /**
     * Create audit trail entry for a cash drawer shift close operation.
     */
    private void createCloseShiftAuditTrail(CashierShift cashierShift, BigDecimal closingBalance,
                                             BigDecimal expectedBalance, BigDecimal discrepancy) {
        try {
            RequestStatus status = discrepancy.compareTo(BigDecimal.ZERO) == 0
                    ? RequestStatus.NA
                    : RequestStatus.OPEN;
            String discrepancyReason = cashierShift.getDiscrepancyReason();
            auditTrailService.createCashDrawerAuditTrail(
                    cashierShift.getCashier(),
                    ActionType.SYSTEM_ACTION,
                    cashierShift.getCashDrawer().getRestaurant(),
                    status,
                    null, null,
                    cashierShift.getOpeningBalance(),
                    closingBalance,
                    expectedBalance,
                    discrepancy,
                    discrepancyReason,
                    String.format("Cash drawer shift closed. Opening: %s, Closing: %s, Expected: %s, Discrepancy: %s",
                            cashierShift.getOpeningBalance(), closingBalance, expectedBalance, discrepancy)
            );
        } catch (Exception e) {
            log.error("Failed to create audit trail for cash drawer shift close: {}", e.getMessage(), e);
        }
    }

    /**
     * Send notifications after a shift is closed, depending on who performed the close.
     */
    private void sendCloseShiftNotifications(CashierShift cashierShift, User closingUser,
                                              boolean isManager, boolean isCashier, Locale userLocale) {
        if (isManager) {
            try {
                User cashierUser = cashierShift.getCashier();
                notificationService.notifyCashDrawerShiftClosedByManager(cashierShift, cashierUser, closingUser, userLocale);
            } catch (Exception e) {
                log.error("Failed to send cash drawer shift closed-by-manager notification: {}", e.getMessage(), e);
            }
        }

        if (isCashier) {
            try {
                List<User> managers = getManagersForRestaurant(cashierShift.getRestaurant().getId());
                if (!managers.isEmpty()) {
                    notificationService.notifyCashDrawerShiftClosed(cashierShift, closingUser, managers, userLocale);
                }
            } catch (Exception e) {
                log.error("Failed to send cash drawer shift closed notification: {}", e.getMessage(), e);
            }
        }
    }

    /**
     * Calculates the expected closing balance for a cashier shift.
     * Expected balance = Opening Balance + Total Inflows (SALE_INFLOW, MANUAL_DEPOSIT) - Total Outflows (SALE_REFUND, MANUAL_WITHDRAWAL).
     *
     * @param shiftId the ID of the shift
     * @return the expected closing balance
     * @throws RuntimeException if shift not found
     */
    private BigDecimal calculateExpectedBalance(UUID shiftId) {
        // Get opening balance
        CashierShift shift = cashierShiftRepository.findById(shiftId)
                .orElseThrow(() -> new RuntimeException("Shift not found"));

        BigDecimal openingBalance = shift.getOpeningBalance();

        // Get all logs for the shift
        List<CashDrawerLog> logs = cashDrawerLogRepository.findByShiftIdOrderByCreatedAtAsc(shiftId);

        // Calculate inflows (positive amounts from SALE_INFLOW and MANUAL_DEPOSIT)
        BigDecimal totalInflow = logs.stream()
                .filter(log -> log.getEventType() == DrawerEventType.SALE_INFLOW || 
                              log.getEventType() == DrawerEventType.MANUAL_DEPOSIT)
                .map(CashDrawerLog::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // Calculate outflows (absolute values from SALE_REFUND and MANUAL_WITHDRAWAL - they're stored as negative)
        BigDecimal totalOutflow = logs.stream()
                .filter(log -> log.getEventType() == DrawerEventType.SALE_REFUND || 
                              log.getEventType() == DrawerEventType.MANUAL_WITHDRAWAL)
                .map(log -> log.getAmount().abs()) // Get absolute value since they're stored as negative
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // Expected = Opening + Inflows - Outflows
        return openingBalance.add(totalInflow).subtract(totalOutflow);
    }

    /**
     * Builds the API response DTO for a cash drawer.
     * <p>
     * Loads translations from the repository, resolves the display name using locale fallback, and returns a response
     * including the resolved name plus the full translation list.
     * </p>
     *
     * @param cashDrawer cash drawer entity
     * @return response DTO for the cash drawer
     */
    private CashDrawerResponse buildCashDrawerResponse(CashDrawer cashDrawer) {
        Locale locale = LocaleContextHolder.getLocale();
        List<CashDrawerTranslation> translations =
                cashDrawerTranslationRepository.findAllByCashDrawer_IdOrderByLanguageCodeAsc(cashDrawer.getId());
        String resolvedName = CashDrawerTranslationUtil.resolveName(translations, locale);
        List<CashDrawerTranslationResponse> translationResponses = translations.stream()
                .map(t -> CashDrawerTranslationResponse.builder()
                        .languageCode(t.getLanguageCode())
                        .name(t.getName())
                        .build())
                .collect(Collectors.toList());
        return CashDrawerResponse.builder()
                .id(cashDrawer.getId())
                .restaurantId(cashDrawer.getRestaurant().getId())
                .name(resolvedName)
                .translations(translationResponses)
                .serialNumber(cashDrawer.getSerialNumber())
                .status(cashDrawer.getStatus())
                .createdAt(cashDrawer.getCreatedAt() != null ? cashDrawer.getCreatedAt().toLocalDateTime() : null)
                .updatedAt(cashDrawer.getUpdatedAt() != null ? cashDrawer.getUpdatedAt().toLocalDateTime() : null)
                .build();
    }

    /**
     * Validates cash drawer translation requests.
     * <p>
     * Requires at least one translation, non-blank languageCode/name fields, unique language codes (case-insensitive),
     * and a mandatory English ({@code en}) translation.
     * </p>
     *
     * @param translations translation requests to validate
     * @param userLocale   locale used for localized error messages
     * @throws ResponseStatusException when validation fails
     */
    private void validateCashDrawerTranslationRequests(List<CashDrawerTranslationRequest> translations, Locale userLocale) {
        if (translations == null || translations.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    messageUtil.getMessage("cash.drawer.translation.invalid", userLocale));
        }
        Set<String> seenLang = new HashSet<>();
        boolean hasEn = false;
        for (CashDrawerTranslationRequest t : translations) {
            if (t.getLanguageCode() == null || t.getLanguageCode().trim().isEmpty()
                    || t.getName() == null || t.getName().trim().isEmpty()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        messageUtil.getMessage("cash.drawer.translation.invalid", userLocale));
            }
            String lang = t.getLanguageCode().trim();
            if (!seenLang.add(lang.toLowerCase(Locale.ROOT))) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        messageUtil.getMessage("cash.drawer.translation.language.duplicate", userLocale));
            }
            if ("en".equalsIgnoreCase(lang)) {
                hasEn = true;
            }
        }
        if (!hasEn) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    messageUtil.getMessage("cash.drawer.translation.en.required", userLocale));
        }
    }

    private String resolveCashDrawerDisplayName(CashDrawer drawer) {
        if (drawer == null) {
            return null;
        }
        List<CashDrawerTranslation> list =
                cashDrawerTranslationRepository.findAllByCashDrawer_IdOrderByLanguageCodeAsc(drawer.getId());
        return CashDrawerTranslationUtil.resolveName(list, LocaleContextHolder.getLocale());
    }

    /**
     * Builds a CashierShiftResponse DTO from a CashierShift entity.
     * Includes all shift details, cashier and drawer information, and approval details.
     *
     * @param shift the CashierShift entity
     * @return a CashierShiftResponse DTO
     */
    private CashierShiftResponse buildCashierShiftResponse(CashierShift shift) {
        return CashierShiftResponse.builder()
                .id(shift.getId())
                .cashDrawerId(shift.getCashDrawer().getId())
                .cashDrawerName(resolveCashDrawerDisplayName(shift.getCashDrawer()))
                .cashierId(shift.getCashier().getId())
                .cashierName(shift.getCashier().getFirstName() + " " + shift.getCashier().getLastName())
                .restaurantId(shift.getRestaurant().getId())
                .shiftId(shift.getShift() != null ? shift.getShift().getId() : null)
                .shiftName(shift.getShift() != null ? getShiftNameFromShift(shift.getShift()) : null)
                .status(shift.getStatus())
                .openingBalance(shift.getOpeningBalance())
                .closingBalance(shift.getClosingBalance())
                .expectedClosingBalance(shift.getExpectedClosingBalance())
                .discrepancyAmount(shift.getDiscrepancyAmount())
                .discrepancyReason(shift.getDiscrepancyReason())
                .startedAt(shift.getStartedAt() != null ? shift.getStartedAt().toLocalDateTime() : null)
                .closedAt(shift.getClosedAt() != null ? shift.getClosedAt().toLocalDateTime() : null)
                .approvedBy(shift.getApprovedBy() != null ? shift.getApprovedBy().getId() : null)
                .approvedByName(shift.getApprovedBy() != null ? 
                        shift.getApprovedBy().getFirstName() + " " + shift.getApprovedBy().getLastName() : null)
                .approvedAt(shift.getApprovedAt() != null ? shift.getApprovedAt().toLocalDateTime() : null)
                .createdAt(shift.getCreatedAt() != null ? shift.getCreatedAt().toLocalDateTime() : null)
                .updatedAt(shift.getUpdatedAt() != null ? shift.getUpdatedAt().toLocalDateTime() : null)
                .build();
    }

    /**
     * Builds a CashDrawerLogResponse DTO from a CashDrawerLog entity.
     * Includes log details, drawer and user information, and associated transaction/refund information.
     *
     * @param log the CashDrawerLog entity
     * @return a CashDrawerLogResponse DTO
     */
    private CashDrawerLogResponse buildCashDrawerLogResponse(CashDrawerLog log) {
        return CashDrawerLogResponse.builder()
                .id(log.getId())
                .shiftId(log.getShift().getId())
                .drawerId(log.getDrawer().getId())
                .drawerName(resolveCashDrawerDisplayName(log.getDrawer()))
                .userId(log.getUser().getId())
                .userName(log.getUser().getFirstName() + " " + log.getUser().getLastName())
                .eventType(log.getEventType())
                .amount(log.getAmount())
                .expectedAmount(log.getExpectedAmount())
                .grossIn(log.getGrossIn())
                .grossOut(log.getGrossOut())
                .transactionId(log.getTransaction() != null ? log.getTransaction().getId() : null)
                .transactionNumber(log.getTransaction() != null ? log.getTransaction().getTransactionNumber() : null)
                .refundId(log.getRefund() != null ? log.getRefund().getId() : null)
                .refundNumber(log.getRefund() != null ? log.getRefund().getRefundNumber() : null)
                .reason(log.getReason())
                .notes(log.getNotes())
                .createdAt(log.getCreatedAt() != null ? log.getCreatedAt().toLocalDateTime() : null)
                .createdBy(log.getCreatedBy() != null ? log.getCreatedBy().getId() : null)
                .createdByName(log.getCreatedBy() != null ? 
                        log.getCreatedBy().getFirstName() + " " + log.getCreatedBy().getLastName() : null)
                .build();
    }
    
    /**
     * Get all managers for a restaurant
     */
    private List<User> getManagersForRestaurant(UUID restaurantId) {
        try {
            Optional<Role> managerRoleOpt = roleRepository.findByName("MANAGER");
            if (managerRoleOpt.isEmpty()) {
                log.warn("MANAGER role not found in database");
                return List.of();
            }
            
            UUID managerRoleId = managerRoleOpt.get().getId();
            List<User> managers = userRepository.findAllByRestaurantIdAndRoleIdAndIsDeletedFalse(restaurantId, managerRoleId)
                    .stream()
                    .filter(u -> u.getStatus() == EntityStatus.ACTIVE)
                    .collect(Collectors.toList());
            
            log.debug("Found {} managers for restaurant {}", managers.size(), restaurantId);
            return managers;
        } catch (Exception e) {
            log.error("Failed to get managers for restaurant {}: {}", restaurantId, e.getMessage(), e);
            return List.of();
        }
    }

    /**
     * Helper method to get shift name from translations
     */
    private String getShiftNameFromShift(com.gulfnet.shared_library.entity.Shift shift) {
        if (shift == null) {
            return null;
        }
        
        List<ShiftTranslation> translations = shiftTranslationRepository.findAllByShiftId(shift.getId());
        if (translations == null || translations.isEmpty()) {
            return "";
        }
        
        String preferredLocale = LocaleContextHolder.getLocale().getLanguage();
        // Default to 'en' if localization properties not available
        String defaultLanguage = "en";
        
        // Try to get preferred translation
        Optional<ShiftTranslation> translation = translations.stream()
                .filter(t -> preferredLocale != null && preferredLocale.equalsIgnoreCase(t.getLanguageCode()))
                .findFirst();
        
        if (translation.isPresent()) {
            return translation.get().getName();
        }
        
        // Fallback to default language
        Optional<ShiftTranslation> defaultTranslation = translations.stream()
                .filter(t -> defaultLanguage.equalsIgnoreCase(t.getLanguageCode()))
                .findFirst();
        if (defaultTranslation.isPresent()) {
            return defaultTranslation.get().getName();
        }
        
        // Last resort: return first available translation
        return translations.get(0).getName();
    }

    /**
     * Record to hold the result of user and shift validation for shift operations.
     */
    private record ShiftValidationResult(
            User user,
            CashierShift cashierShift,
            boolean isManager,
            boolean isCashier
    ) {}

    /**
     * Validates user and shift, and checks if user is authorized (manager or cashier).
     * This shared method is used by multiple shift operations.
     * 
     * @param userId User ID string
     * @param shiftId Shift ID
     * @param userLocale User's locale for error messages
     * @return ShiftValidationResult containing validated user, shift, and role flags
     */
    private ShiftValidationResult validateUserAndShiftForShiftOperation(
            String userId, UUID shiftId, Locale userLocale) {
        // Validate user
        User user = userRepository.findById(UUID.fromString(userId))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        messageUtil.getMessage(MSG_USER_NOT_FOUND, userLocale)));

        // Get shift
        CashierShift cashierShift = cashierShiftRepository.findById(shiftId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        messageUtil.getMessage("shift.not.found", userLocale)));

        // Check if user is manager or cashier
        boolean isManager = orderValidationService.isManager(user);
        boolean isCashier = orderValidationService.isCashier(user);

        if (!isManager && !isCashier) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    messageUtil.getMessage("shift.close.unauthorized", userLocale));
        }

        return new ShiftValidationResult(user, cashierShift, isManager, isCashier);
    }
}

