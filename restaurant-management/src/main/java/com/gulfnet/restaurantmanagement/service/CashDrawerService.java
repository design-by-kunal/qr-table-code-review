package com.gulfnet.restaurantmanagement.service;

import com.gulfnet.shared_library.model.request.*;
import com.gulfnet.shared_library.model.request.CashierDiscrepancyReasonRequest;
import com.gulfnet.shared_library.enums.DrawerEventType;
import com.gulfnet.shared_library.model.response.dto.*;

import java.time.LocalDateTime;
import java.util.UUID;

public interface CashDrawerService {

    // Cash Drawer Management
    ResponseDto<CashDrawerResponse> createCashDrawer(String userId, CreateCashDrawerRequest request, String locale);
    ResponseDto<CashDrawerListResponse> getCashDrawers(
            UUID restaurantId,
            String status,
            String search,
            Integer page,
            Integer size,
            String sortBy,
            String sortDirection,
            String locale);
    ResponseDto<CashDrawerListResponse> getDrawerList(UUID restaurantId, String locale);

    ResponseDto<CashDrawerResponse> getCashDrawerById(UUID drawerId);

    ResponseDto<CashDrawerResponse> updateCashDrawerStatus(UUID drawerId, String status, String userId, String locale);
    ResponseDto<CashDrawerResponse> updateCashDrawer(UUID drawerId, UpdateCashDrawerRequest request, String userId, String locale);
    ResponseDto<Void> deleteCashDrawer(UUID drawerId, String userId, String locale);

    // Shift Management
    ResponseDto<CashierShiftResponse> startShift(String userId, StartShiftRequest request, String locale);
    ResponseDto<CashierShiftResponse> closeShift(String userId, UUID shiftId, CloseShiftRequest request, String locale);
    ResponseDto<CashierShiftResponse> approveShift(String managerId, UUID shiftId, ApproveShiftRequest request, String locale);
    ResponseDto<CashierShiftListResponse> getMyShifts(String userId, Integer page, Integer size, String locale);
    ResponseDto<CashierShiftResponse> updateDiscrepancyReason(String userId, String userRole, UUID shiftId, CashierDiscrepancyReasonRequest request, String locale);
    ResponseDto<CashierOpenShiftResponse> getOpenShiftByCashierId(String userId, String userRole, UUID cashierId, String locale);

    /**
     * Manager-side cashier shift listing API.
     *
     * This endpoint is intended to power the "Cashier Shifts" screen for managers.
     * It only returns shifts that are:
     * - OPEN  -> shown as "Open" in the UI
     * - CLOSED or APPROVED -> shown under a unified "Closed" status in the UI
     *
     * PENDING_APPROVAL and REJECTED shifts continue to be served via the
     * dedicated discrepancy request listing API.
     */
    /**
     * Get manager listing of cashier shifts for a restaurant (Manager only)
     * Managers can only see shifts from their own restaurant
     */
    ResponseDto<CashierShiftListResponse> getRestaurantShiftListing(
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
            String locale);

    // Drawer Event Logging
    ResponseDto<CashDrawerLogResponse> logManualEvent(String userId, ManualDrawerEventRequest request, String locale);
    ResponseDto<CashDrawerLogListResponse> getDrawerEventLogs(
            String userId,
            UUID shiftId,
            UUID drawerId,
            DrawerEventType eventType,
            Integer page,
            Integer size,
            LocalDateTime startDate,
            LocalDateTime endDate,
            String locale);

    ResponseDto<CashDrawerLogListResponse> getMyDrawerEventLogs(
            String userId,
            UUID drawerId,
            DrawerEventType eventType,
            Integer page,
            Integer size,
            LocalDateTime startDate,
            LocalDateTime endDate,
            String locale);

}

