package com.gulfnet.restaurantmanagement.controller;

import com.gulfnet.restaurantmanagement.service.TableService;
import com.gulfnet.shared_library.model.request.TableAssignmentRequest;
import com.gulfnet.shared_library.model.request.WaiterTableAssignmentRequest;
import com.gulfnet.shared_library.model.request.TableStatusPayload;
import com.gulfnet.shared_library.model.request.GuestTransferRequest;
import com.gulfnet.shared_library.model.request.TableMoveRequest;
import com.gulfnet.shared_library.model.request.TableSectionRequest;
import com.gulfnet.shared_library.model.response.dto.ResponseDto;
import com.gulfnet.shared_library.model.response.dto.TableAssignmentResponse;
import com.gulfnet.shared_library.model.response.dto.TableAssignmentWrapper;
import com.gulfnet.shared_library.model.response.dto.TableListResponseDto;
import com.gulfnet.shared_library.model.response.dto.TableListResponseDtoV2;
import com.gulfnet.shared_library.model.response.dto.TableStatusResponseWrapper;
import com.gulfnet.shared_library.model.response.dto.SessionResponseDto;
import com.gulfnet.shared_library.model.response.dto.GuestTransferResponse;
import com.gulfnet.shared_library.model.response.dto.TableMoveResponse;
import com.gulfnet.shared_library.enums.QrCodeType;

import jakarta.validation.Valid;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import io.swagger.v3.oas.annotations.Operation;

import java.util.List;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/v1/table")
@RequiredArgsConstructor
public class TableController {

    private final TableService tableService;

    @PostMapping("/assign")
    public ResponseEntity<ResponseDto<TableAssignmentWrapper<List<TableAssignmentResponse>>>> assignTable(
            @Valid @RequestBody TableAssignmentRequest request,
            @RequestHeader("User-ID") String userId,
            @RequestHeader("User-Role") String userRole) {
        return ResponseEntity.ok(tableService.assignTableToWaiter(request, userId, userRole));
    }

    @PostMapping("/assign-waiters")
    public ResponseEntity<ResponseDto<TableAssignmentWrapper<List<TableAssignmentResponse>>>> assignWaitersToTable(
            @Valid @RequestBody WaiterTableAssignmentRequest request,
            @RequestHeader("User-ID") String userId,
            @RequestHeader("User-Role") String userRole) {
        return ResponseEntity.ok(tableService.assignWaitersToTable(request, userId, userRole));
    }

    @PatchMapping("/unassign/{assignmentId}")
    public ResponseEntity<ResponseDto<TableAssignmentWrapper<TableAssignmentResponse>>> unassignTable(
            @PathVariable UUID assignmentId,
            @RequestHeader("User-ID") String userId,
            @RequestHeader("User-Role") String userRole) {
        return ResponseEntity.ok(tableService.unassignTableFromWaiter(assignmentId, userId, userRole));
    }

    /**
     * Retrieves a paginated and filterable list of tables.
     * Supports filtering by waiter, status, section, restaurant, and text search.
     *
     * @param waiterId     optional waiter ID to filter tables assigned to a specific waiter
     * @param search       optional search term for text search
     * @param status       optional filter by table status
     * @param sectionId    optional filter by section ID
     * @param restaurantId optional filter by restaurant ID
     * @param page         optional page number for pagination
     * @param size         optional page size for pagination
     * @return response containing paginated list of tables with filters applied
     */
    @GetMapping
    public ResponseDto<TableListResponseDto> getTablesByFilters(
            @RequestParam(required = false) String waiterId,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String sectionId,
            @RequestParam(required = false) String restaurantId,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size) {

        return tableService.getTablesByFilters(waiterId, search, status, sectionId, restaurantId, page, size);
    }

    /**
     * Retrieves a paginated and filterable list of tables (version 2 with enhanced response format).
     * Supports filtering by waiter, status, section, restaurant, and text search.
     *
     * @param waiterId     optional waiter ID to filter tables assigned to a specific waiter
     * @param search       optional search term for text search
     * @param status       optional filter by table status
     * @param sectionId    optional filter by section ID
     * @param restaurantId optional filter by restaurant ID
     * @param page         optional page number for pagination
     * @param size         optional page size for pagination
     * @return response containing paginated list of tables with enhanced format and filters applied
     */
    @GetMapping("/v2")
    public ResponseDto<TableListResponseDtoV2> getTablesByFiltersV2(
            @RequestParam(required = false) String waiterId,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String sectionId,
            @RequestParam(required = false) String restaurantId,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size) {

        return tableService.getTablesByFiltersV2(waiterId, search, status, sectionId, restaurantId, page, size);
    }

    /**
     * Updates the status of one or more tables.
     * Supports both single table update (backward compatible) and bulk table updates.
     *
     * @param payload the table status payload containing table ID(s) and new status
     * @param userId  optional user ID from the request header
     * @param userRole optional user role from the request header
     * @return response containing updated table status information
     */
    @PutMapping("/status")
    public ResponseEntity<ResponseDto<TableStatusResponseWrapper>> updateTableStatus(
            @Valid @RequestBody TableStatusPayload payload,
            @RequestHeader("User-ID") String userId,
            @RequestHeader("User-Role") String userRole) {

        // Support both single tableId (backward compatible) and multiple tableIds (new functionality)
        if (payload.getTableIds() != null && !payload.getTableIds().isEmpty()) {
            log.info("Table status update request for {} tables to status {} by user {}", 
                    payload.getTableIds().size(), payload.getTableStatus(), userId);
        } else {
            log.info("Table status update request for table {} to status {} by user {}", 
                    payload.getTableId(), payload.getTableStatus(), userId);
        }
        
        ResponseDto<TableStatusResponseWrapper> response = tableService.updateTableStatus(payload, userId, userRole);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{tableId}/qr-code")
    public ResponseEntity<String> getTableQrCodePresignedUrl(@PathVariable UUID tableId) {
        String presignedUrl = tableService.getTableQrCodePresignedUrl(tableId);
        return ResponseEntity.ok(presignedUrl);
    }

    @PostMapping("/{tableId}/qr-code/regenerate")
    public ResponseEntity<String> regenerateTableQrCode(
            @PathVariable UUID tableId,
            @RequestHeader("User-ID") String userId,
            @RequestHeader("User-Role") String userRole) {
        String presignedUrl = tableService.regenerateTableQrCode(tableId, userId, userRole);
        return ResponseEntity.ok(presignedUrl);
    }

    @PostMapping("/{tableId}/restaurant/{restaurantId}/session")
    public ResponseEntity<ResponseDto<SessionResponseDto>> startSession(
            @PathVariable UUID tableId,
            @PathVariable UUID restaurantId,
            @RequestParam QrCodeType qrCodeType,      
            @RequestParam(required = false) UUID sessionId) {

        ResponseDto<SessionResponseDto> response = tableService.startSession(restaurantId, tableId, qrCodeType, sessionId);
        return ResponseEntity.ok(response);
    }


    @GetMapping("/{sessionId}/validate")
    public ResponseEntity<Void> validateSession(
            @PathVariable UUID sessionId,
            HttpServletRequest request) {
        String authHeader = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Missing or invalid authorization header");
        }
        String token = authHeader.substring(7);
        tableService.validateSession(sessionId, token);
        return ResponseEntity.ok().build();
    }

    // ==================== MANAGER-SPECIFIC APIs ====================

    /**
     * Transfers guests from one table to another table.
     * Moves all active sessions and orders from the source table to the target table.
     *
     * @param request the guest transfer request containing source and target table IDs
     * @param userId  the user ID from the request header (required)
     * @param userRole the user role from the request header (required)
     * @return response containing transfer details and updated session information
     */
    @PostMapping("/transfer-guests")
    public ResponseEntity<ResponseDto<GuestTransferResponse>> transferGuests(
            @Valid @RequestBody GuestTransferRequest request,
            @RequestHeader("User-ID") String userId,
            @RequestHeader("User-Role") String userRole) {
        
        log.info("Guest transfer request from table {} to table {} by manager {}", 
                request.getSourceTableId(), request.getTargetTableId(), userId);
        
        ResponseDto<GuestTransferResponse> response = tableService.transferGuests(request, userId, userRole);
        return ResponseEntity.ok(response);
    }

    /**
     * Moves one or more tables from their current section to a target section.
     *
     * @param request the table move request containing table IDs and target section ID
     * @param userId  the user ID from the request header (required)
     * @param userRole the user role from the request header (required)
     * @return response containing move operation details and updated table information
     */
    @PostMapping("/move-tables")
    public ResponseEntity<ResponseDto<TableMoveResponse>> moveTables(
            @Valid @RequestBody TableMoveRequest request,
            @RequestHeader("User-ID") String userId,
            @RequestHeader("User-Role") String userRole) {
        
        log.info("Table move request for {} tables to section {} by manager {}", 
                request.getTableIds().size(), request.getTargetSectionId(), userId);
        
        ResponseDto<TableMoveResponse> response = tableService.moveTables(request, userId, userRole);
        return ResponseEntity.ok(response);
    }

    /**
     * Raises a request to HQ_ADMIN for table or section changes.
     * Managers can request modifications to table or section configurations that require admin approval.
     *
     * @param request the table/section request containing entity type and ID
     * @param userId  the user ID from the request header (required)
     * @param userRole the user role from the request header (required)
     * @return response containing the created request details
     */
    @PostMapping("/request")
    @Operation(summary = "Raise table/section request", description = "Manager raises a request to HQ_ADMIN for table/section changes")
    public ResponseEntity<ResponseDto<Object>> raiseTableSectionRequest(
            @Valid @RequestBody TableSectionRequest request,
            @RequestHeader("User-ID") String userId,
            @RequestHeader("User-Role") String userRole) {
        
        log.info("Table/Section request received - entityType: {}, entityId: {} by manager: {}", 
                request.getEntityType(), request.getEntityId(), userId);
        
        ResponseDto<Object> response = tableService.raiseTableSectionRequest(request, userId, userRole);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/waiters/assignments")
    public ResponseEntity<ResponseDto<TableAssignmentWrapper<List<TableAssignmentResponse>>>> getActiveWaiterAssignments(
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            @RequestHeader("User-ID") String userId) {
        ResponseDto<TableAssignmentWrapper<List<TableAssignmentResponse>>> response = tableService.getActiveWaiterAssignments(page, size, userId);
        return ResponseEntity.ok(response);
    }

}

