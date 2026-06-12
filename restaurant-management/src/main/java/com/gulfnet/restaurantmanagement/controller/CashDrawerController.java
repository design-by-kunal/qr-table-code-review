package com.gulfnet.restaurantmanagement.controller;

import com.gulfnet.restaurantmanagement.service.CashDrawerService;
import com.gulfnet.shared_library.enums.DrawerEventType;
import com.gulfnet.shared_library.model.request.*;
import com.gulfnet.shared_library.model.request.CashierDiscrepancyReasonRequest;
import com.gulfnet.shared_library.model.response.dto.*;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.Locale;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/v1/cash-drawer")
@RequiredArgsConstructor
public class CashDrawerController {

    private final CashDrawerService cashDrawerService;

    /**
     * Create a new cash drawer for a restaurant
     * POST /api/v1/cash-drawer/drawers
     */
    @PostMapping("/drawers")
    public ResponseEntity<ResponseDto<CashDrawerResponse>> createCashDrawer(
            @Valid @RequestBody CreateCashDrawerRequest request,
            @RequestHeader("User-ID") String userId,
            @RequestHeader(value = "locale", defaultValue = "en") String locale) {
        
        log.info("Request received to create cash drawer for restaurant: {} serial: {} by user: {}",
                request.getRestaurantId(), request.getSerialNumber(), userId);

        ResponseDto<CashDrawerResponse> response = cashDrawerService.createCashDrawer(userId, request, locale);

        log.info("Successfully created cash drawer for restaurant: {} serial: {}",
                request.getRestaurantId(), request.getSerialNumber());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * Get all cash drawers for a restaurant
     * GET /api/v1/cash-drawer/drawers?restaurantId={id}&status={status}&search={search}&page={page}&size={size}&sortBy={sortBy}&sortDirection={ASC|DESC}
     * 
     */
    @GetMapping("/drawers")
    public ResponseEntity<ResponseDto<CashDrawerListResponse>> getCashDrawers(
            @RequestParam("restaurantId") UUID restaurantId,
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "search", required = false) String search,
            @RequestParam(value = "page", required = false) Integer page,
            @RequestParam(value = "size", required = false) Integer size,
            @RequestParam(value = "sortBy", required = false, defaultValue = "name") String sortBy,
            @RequestParam(value = "sortDirection", required = false, defaultValue = "ASC") String sortDirection,
            @RequestHeader(value = "locale", defaultValue = "en") String locale) {
        
        log.info("Request received to get cash drawers for restaurant: {} with status: {}, search: {}, page: {}, size: {}, sortBy: {}, sortDirection: {}", 
                restaurantId, status, search, page, size, sortBy, sortDirection);
        
        ResponseDto<CashDrawerListResponse> response = cashDrawerService.getCashDrawers(
                restaurantId, status, search, page, size, sortBy, sortDirection, locale);
        
        log.info("Successfully retrieved {} cash drawers for restaurant: {} (total: {})", 
                response.getData().getCount(), restaurantId, response.getData().getTotal());
        return ResponseEntity.ok(response);
    }

    /**
     * Get simple drawer list (only active drawers for selection)
     * GET /api/v1/cash-drawer/drawers/list?restaurantId={id}
     */
    @GetMapping("/drawers/list")
    public ResponseEntity<ResponseDto<CashDrawerListResponse>> getDrawerList(
            @RequestParam("restaurantId") UUID restaurantId,
            @RequestHeader(value = "locale", defaultValue = "en") String locale) {
        
        log.info("Request received to get drawer list for restaurant: {}", restaurantId);
        
        ResponseDto<CashDrawerListResponse> response = cashDrawerService.getDrawerList(restaurantId, locale);
        
        log.info("Successfully retrieved {} active drawers for restaurant: {}", 
                response.getData().getCount(), restaurantId);
        return ResponseEntity.ok(response);
    }

    /**
     * Get a single cash drawer by id.
     * GET /api/v1/cash-drawer/drawers/{drawerId}
     */
    @GetMapping("/drawers/{drawerId}")
    public ResponseEntity<ResponseDto<CashDrawerResponse>> getCashDrawerById(
            @PathVariable UUID drawerId,
            @RequestHeader(value = "locale", defaultValue = "en") String locale) {

        log.info("Request received to get cash drawer: {}", drawerId);

        LocaleContextHolder.setLocale(Locale.forLanguageTag(locale));

        ResponseDto<CashDrawerResponse> response = cashDrawerService.getCashDrawerById(drawerId);

        log.info("Successfully retrieved cash drawer: {}", drawerId);
        return ResponseEntity.ok(response);
    }

    /**
     * Update cash drawer status
     * PUT /api/v1/cash-drawer/drawers/{drawerId}/status?status={ACTIVE|INACTIVE}
     * Note: Cannot update status if drawer has an active shift
     */
    @PutMapping("/drawers/{drawerId}/status")
    public ResponseEntity<ResponseDto<CashDrawerResponse>> updateCashDrawerStatus(
            @PathVariable UUID drawerId,
            @RequestParam("status") String status,
            @RequestHeader("User-ID") String userId,
            @RequestHeader(value = "locale", defaultValue = "en") String locale) {
        
        log.info("Request received to update cash drawer status: {} to {} by user: {}", drawerId, status, userId);
        
        ResponseDto<CashDrawerResponse> response = cashDrawerService.updateCashDrawerStatus(drawerId, status, userId, locale);
        
        log.info("Successfully updated cash drawer status: {} to {}", drawerId, status);
        return ResponseEntity.ok(response);
    }

    /**
     * Update cash drawer translations and serial number
     * PUT /api/v1/cash-drawer/drawers/{drawerId}
     * Note: Cannot update while the drawer has an active shift; per-language names must be unique within the restaurant; serial is globally unique.
     */
    @PutMapping("/drawers/{drawerId}")
    public ResponseEntity<ResponseDto<CashDrawerResponse>> updateCashDrawer(
            @PathVariable UUID drawerId,
            @Valid @RequestBody UpdateCashDrawerRequest request,
            @RequestHeader("User-ID") String userId,
            @RequestHeader(value = "locale", defaultValue = "en") String locale) {

        log.info("Request received to update cash drawer: {} by user: {}", drawerId, userId);

        ResponseDto<CashDrawerResponse> response =
                cashDrawerService.updateCashDrawer(drawerId, request, userId, locale);

        log.info("Successfully updated cash drawer: {}", drawerId);
        return ResponseEntity.ok(response);
    }

    /**
     * Delete a cash drawer (only if it has never been used for a shift)
     * DELETE /api/v1/cash-drawer/drawers/{drawerId}
     */
    @DeleteMapping("/drawers/{drawerId}")
    public ResponseEntity<ResponseDto<Void>> deleteCashDrawer(
            @PathVariable UUID drawerId,
            @RequestHeader("User-ID") String userId,
            @RequestHeader(value = "locale", defaultValue = "en") String locale) {

        log.info("Request received to delete cash drawer: {} by user: {}", drawerId, userId);

        ResponseDto<Void> response = cashDrawerService.deleteCashDrawer(drawerId, userId, locale);

        log.info("Successfully deleted cash drawer: {}", drawerId);
        return ResponseEntity.ok(response);
    }

    /**
     * Start a new shift for a cashier
     * POST /api/v1/cash-drawer/shifts/start
     */
    @PostMapping("/shifts/start")
    public ResponseEntity<ResponseDto<CashierShiftResponse>> startShift(
            @Valid @RequestBody StartShiftRequest request,
            @RequestHeader("User-ID") String userId,
            @RequestHeader(value = "locale", defaultValue = "en") String locale) {
        
        log.info("Request received to start shift for cashier: {} on drawer: {} with opening balance: {}", 
                userId, request.getCashDrawerId(), request.getOpeningBalance());
        
        ResponseDto<CashierShiftResponse> response = cashDrawerService.startShift(userId, request, locale);
        
        log.info("Successfully started shift: {} for cashier: {}", response.getData().getId(), userId);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * Get current cashier's shifts (history with status and discrepancy details)
     * GET /api/v1/cash-drawer/shifts
     */
    @GetMapping("/shifts")
    public ResponseEntity<ResponseDto<CashierShiftListResponse>> getMyShifts(
            @RequestHeader("User-ID") String userId,
            @RequestParam(value = "page", required = false) Integer page,
            @RequestParam(value = "size", required = false) Integer size,
            @RequestHeader(value = "locale", defaultValue = "en") String locale) {

        log.info("Request received to get shift history for cashier: {} (page: {}, size: {})", userId, page, size);

        ResponseDto<CashierShiftListResponse> response = cashDrawerService.getMyShifts(userId, page, size, locale);

        log.info("Successfully retrieved shift history for cashier: {}", userId);
        return ResponseEntity.ok(response);
    }

    /**
     * Get open shift details by cashier ID
     * Returns the cashier shift ID and cash drawer ID for the currently open shift
     * Managers can only view shifts for cashiers in their restaurant
     * Cashiers can only view their own shifts
     * GET /api/v1/cash-drawer/shifts/open?cashierId={id}
     */
    @GetMapping("/shifts/open")
    public ResponseEntity<ResponseDto<CashierOpenShiftResponse>> getOpenShiftByCashierId(
            @RequestParam("cashierId") UUID cashierId,
            @RequestHeader("User-ID") String userId,
            @RequestHeader("User-Role") String userRole,
            @RequestHeader(value = "locale", defaultValue = "en") String locale) {

        log.info("Request received to get open shift for cashier: {} by user: {} with role: {}", cashierId, userId, userRole);

        ResponseDto<CashierOpenShiftResponse> response = cashDrawerService.getOpenShiftByCashierId(userId, userRole, cashierId, locale);

        log.info("Successfully retrieved open shift for cashier: {} (shiftId: {}, drawerId: {})",
                cashierId, response.getData().getCashierShiftId(), response.getData().getCashDrawerId());
        return ResponseEntity.ok(response);
    }

    /**
     * Get manager-side cashier shift listing for a restaurant (Manager only).
     *
     * This endpoint is designed for the "Cashier Shifts" screen used by managers.
     * It only considers two logical statuses for the UI:
     * - OPEN   -> active shifts (ShiftStatus.OPEN)
     * - CLOSED -> shifts with internal statuses CLOSED or APPROVED
     *
     * Shifts with PENDING_APPROVAL or REJECTED status remain part of the
     * discrepancy request listing and are intentionally excluded from here.
     *
     * Managers can only see shifts from their own restaurant. The restaurant ID
     * is automatically determined from the manager's user record.
     *
     * GET /api/v1/cash-drawer/shifts/manager-listing?status={OPEN|CLOSED|ALL}&cashDrawerId={id}&cashierId={id}&search={term}&page={page}&size={size}&startDate={date}&endDate={date}
     * 
     * Pagination: If page or size are not provided, defaults to page=1, size=10. Maximum page size is 100.
     */
    @GetMapping("/shifts/manager-listing")
    public ResponseEntity<ResponseDto<CashierShiftListResponse>> getRestaurantShiftListing(
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "cashDrawerId", required = false) UUID cashDrawerId,
            @RequestParam(value = "cashierId", required = false) UUID cashierId,
            @RequestParam(value = "search", required = false) String search,
            @RequestParam(value = "page", required = false) Integer page,
            @RequestParam(value = "size", required = false) Integer size,
            @RequestParam(value = "startDate", required = false)
                @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startDate,
            @RequestParam(value = "endDate", required = false)
                @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endDate,
            @RequestHeader("User-ID") String userId,
            @RequestHeader("User-Role") String userRole,
            @RequestHeader(value = "locale", defaultValue = "en") String locale) {

        log.info("Request received to get manager shift listing by user: {} with role: {} (status: {}, cashDrawerId: {}, cashierId: {}, search: {}, page: {}, size: {}, startDate: {}, endDate: {})",
                userId, userRole, status, cashDrawerId, cashierId, search, page, size, startDate, endDate);

        ResponseDto<CashierShiftListResponse> response = cashDrawerService.getRestaurantShiftListing(
                userId, userRole, status, cashDrawerId, cashierId, search, page, size, startDate, endDate, locale);

        log.info("Successfully retrieved manager shift listing for user: {}", userId);
        return ResponseEntity.ok(response);
    }

    /**
     * Close an active shift
     * Can be called by cashiers (their own shifts) or managers (any shift in their restaurant)
     * POST /api/v1/cash-drawer/shifts/{shiftId}/close
     */
    @PostMapping("/shifts/{shiftId}/close")
    public ResponseEntity<ResponseDto<CashierShiftResponse>> closeShift(
            @PathVariable UUID shiftId,
            @Valid @RequestBody CloseShiftRequest request,
            @RequestHeader("User-ID") String userId,
            @RequestHeader(value = "locale", defaultValue = "en") String locale) {
        
        log.info("Request received to close shift: {} by user: {} with closing balance: {}", 
                shiftId, userId, request.getClosingBalance());
        
        ResponseDto<CashierShiftResponse> response = cashDrawerService.closeShift(userId, shiftId, request, locale);
        
        log.info("Successfully closed shift: {} with status: {}", shiftId, response.getData().getStatus());
        return ResponseEntity.ok(response);
    }

    /**
     * Update discrepancy reason for a closed shift with discrepancy (cashier)
     * PATCH /api/v1/cash-drawer/shifts/{shiftId}/discrepancy-reason
     */
    @PatchMapping("/shifts/{shiftId}/discrepancy-reason")
    public ResponseEntity<ResponseDto<CashierShiftResponse>> updateDiscrepancyReason(
            @PathVariable UUID shiftId,
            @Valid @RequestBody CashierDiscrepancyReasonRequest request,
            @RequestHeader("User-ID") String userId,
            @RequestHeader("User-Role") String userRole,
            @RequestHeader(value = "locale", defaultValue = "en") String locale) {

        log.info("Request received to update discrepancy reason for shift: {} by user: {} with role: {}", shiftId, userId, userRole);

        ResponseDto<CashierShiftResponse> response =
                cashDrawerService.updateDiscrepancyReason(userId, userRole, shiftId, request, locale);

        log.info("Successfully updated discrepancy reason for shift: {}", shiftId);
        return ResponseEntity.ok(response);
    }

    /**
     * Approve a shift closure (Manager only)
     * POST /api/v1/cash-drawer/shifts/{shiftId}/approve
     */
    @PostMapping("/shifts/{shiftId}/approve")
    public ResponseEntity<ResponseDto<CashierShiftResponse>> approveShift(
            @PathVariable UUID shiftId,
            @Valid @RequestBody ApproveShiftRequest request,
            @RequestHeader("User-ID") String managerId,
            @RequestHeader(value = "locale", defaultValue = "en") String locale) {
        
        log.info("Request received to approve shift: {} by manager: {}", shiftId, managerId);
        
        ResponseDto<CashierShiftResponse> response = cashDrawerService.approveShift(managerId, shiftId, request, locale);
        
        log.info("Successfully approved shift: {} by manager: {}", shiftId, managerId);
        return ResponseEntity.ok(response);
    }

    /**
     * Log drawer event (deposit or withdrawal)
     * POST /api/v1/cash-drawer/events
     */
    @PostMapping("/events")
    public ResponseEntity<ResponseDto<CashDrawerLogResponse>> logDrawerEvent(
            @Valid @RequestBody ManualDrawerEventRequest request,
            @RequestHeader("User-ID") String userId,
            @RequestHeader(value = "locale", defaultValue = "en") String locale) {
        
        log.info("Request received to log drawer event: {} with amount: {} by user: {} (transactionId: {})", 
                request.getEventType(), request.getAmount(), userId, request.getTransactionId());
        
        ResponseDto<CashDrawerLogResponse> response = cashDrawerService.logManualEvent(userId, request, locale);
        
        log.info("Successfully logged drawer event: {} for user: {}", response.getData().getId(), userId);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * Get drawer event logs with filters
     * GET /api/v1/cash-drawer/events/logs?shiftId={id}&drawerId={id}&eventType={type}&page={page}&size={size}&startDate={date}&endDate={date}
     */
    @GetMapping("/events/logs")
    public ResponseEntity<ResponseDto<CashDrawerLogListResponse>> getDrawerEventLogs(
            @RequestParam(value = "shiftId", required = false) UUID shiftId,
            @RequestParam(value = "drawerId", required = false) UUID drawerId,
            @RequestParam(value = "eventType", required = false) String eventType,
            @RequestParam(value = "page", required = false) Integer page,
            @RequestParam(value = "size", required = false) Integer size,
            @RequestParam(value = "startDate", required = false) 
                @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startDate,
            @RequestParam(value = "endDate", required = false) 
                @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endDate,
            @RequestHeader("User-ID") String userId,
            @RequestHeader(value = "locale", defaultValue = "en") String locale) {
        
        log.info("Request received to get drawer event logs (shiftId: {}, drawerId: {}, eventType: {}, page: {}, size: {}, startDate: {}, endDate: {})", 
                shiftId, drawerId, eventType, page, size, startDate, endDate);
        
        DrawerEventType drawerEventType = null;
        if (eventType != null && !eventType.isEmpty()) {
            try {
                drawerEventType = DrawerEventType.valueOf(eventType.toUpperCase());
            } catch (IllegalArgumentException e) {
                log.warn("Invalid drawer event type: {}", eventType);
            }
        }
        
        ResponseDto<CashDrawerLogListResponse> response = cashDrawerService.getDrawerEventLogs(
                userId, shiftId, drawerId, drawerEventType, page, size, startDate, endDate, locale);
        
        log.info("Successfully retrieved drawer event logs (count: {})", 
                response.getData().getPagination().getTotalRecords());
        return ResponseEntity.ok(response);
    }

    /**
     * Get cash drawer event logs for the current cashier (user)
     * GET /api/v1/cash-drawer/events/my-logs?drawerId={id}&eventType={type}&page={page}&size={size}&startDate={date}&endDate={date}
     */
    @GetMapping("/events/my-logs")
    public ResponseEntity<ResponseDto<CashDrawerLogListResponse>> getMyDrawerEventLogs(
            @RequestParam(value = "drawerId", required = false) UUID drawerId,
            @RequestParam(value = "eventType", required = false) String eventType,
            @RequestParam(value = "page", required = false) Integer page,
            @RequestParam(value = "size", required = false) Integer size,
            @RequestParam(value = "startDate", required = false) 
                @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startDate,
            @RequestParam(value = "endDate", required = false) 
                @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endDate,
            @RequestHeader("User-ID") String userId,
            @RequestHeader(value = "locale", defaultValue = "en") String locale) {
        
        log.info("Request received to get cashier drawer event logs (userId: {}, drawerId: {}, eventType: {}, page: {}, size: {}, startDate: {}, endDate: {})", 
                userId, drawerId, eventType, page, size, startDate, endDate);
        
        DrawerEventType drawerEventType = null;
        if (eventType != null && !eventType.isEmpty()) {
            try {
                drawerEventType = DrawerEventType.valueOf(eventType.toUpperCase());
            } catch (IllegalArgumentException e) {
                log.warn("Invalid drawer event type: {}", eventType);
            }
        }
        
        ResponseDto<CashDrawerLogListResponse> response = cashDrawerService.getMyDrawerEventLogs(
                userId, drawerId, drawerEventType, page, size, startDate, endDate, locale);
        
        log.info("Successfully retrieved cashier drawer event logs (count: {})", 
                response.getData().getLogs().size());
        return ResponseEntity.ok(response);
    }
}

