package com.gulfnet.usermanagement.controller;

import com.gulfnet.shared_library.model.request.RestoreEntitiesRequest;
import com.gulfnet.shared_library.model.request.ShiftRequest;
import com.gulfnet.shared_library.model.response.dto.ResponseDto;
import com.gulfnet.shared_library.model.response.dto.ShiftDataResponse;
import com.gulfnet.shared_library.model.response.dto.ShiftListResponse;
import com.gulfnet.usermanagement.service.ShiftService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import io.swagger.v3.oas.annotations.Operation;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/shifts")
@RequiredArgsConstructor
@Slf4j
public class ShiftController {

    private final ShiftService shiftService;

    @PostMapping
    public ResponseEntity<ResponseDto<ShiftDataResponse>> createShift(@RequestBody ShiftRequest request) {
        return ResponseEntity.ok(shiftService.createShift(request));
    }

    /**
     * Retrieves a paginated list of shifts with optional filters for status, search keyword,
     * soft-delete flag, and sort options. Also supports locale-specific responses.
     *
     * @param page      optional page number for pagination
     * @param size      optional page size for pagination
     * @param status    optional status filter (ACTIVE, INACTIVE)
     * @param search    optional search keyword to filter shifts by name
     * @param sortBy    optional field name to sort by (defaults to createdAt). Supported values: {@code name}, {@code createdAt}
     * @param direction optional sort direction (ASC or DESC, defaults to DESC)
     * @param isDeleted optional flag to include soft-deleted shifts
     * @param locale    optional locale header for localized responses
     * @return {@link ResponseEntity} wrapping {@link ResponseDto} with {@link ShiftListResponse} data
     */
    @GetMapping
    public ResponseEntity<ResponseDto<ShiftListResponse>> getAllShifts(
            @RequestParam(value = "page", required = false) Integer page,
            @RequestParam(value = "size", required = false) Integer size,
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "search", required = false) String search,
            @RequestParam(value = "sortBy", required = false) String sortBy,
            @RequestParam(value = "direction", required = false) String direction,
            @RequestParam(value = "isDeleted", required = false) Boolean isDeleted,
            @RequestHeader(value = "locale", required = false) String locale) {
        log.info("Received get shifts list request (page: {}, size: {}, status: {}, search: {}, sortBy: {}, direction: {}, isDeleted: {}) with locale: {}", 
                page, size, status, search, sortBy, direction, isDeleted, locale);
        ResponseDto<ShiftListResponse> response = shiftService.getAllShifts(
                page, size, status, search, sortBy, direction, isDeleted, locale);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{shiftId}")
    public ResponseEntity<ResponseDto<ShiftDataResponse>> getShiftById(
            @PathVariable UUID shiftId,
            @RequestHeader(value = "locale", required = false) String locale) {
        log.info("Received get shift by id request with locale: {}", locale);
        ResponseDto<ShiftDataResponse> response = shiftService.getShiftById(shiftId, locale);
        return ResponseEntity.ok(response);
    }

    @PutMapping("/{shiftId}")
    public ResponseEntity<ResponseDto<ShiftDataResponse>> updateShift(
            @PathVariable UUID shiftId,
            @RequestBody ShiftRequest request) {
        return ResponseEntity.ok(shiftService.updateShift(shiftId, request));
    }

    @DeleteMapping("/{shiftId}")
    public ResponseEntity<ResponseDto<Void>> deleteShift(
            @PathVariable UUID shiftId,
            @RequestHeader("User-ID") String userId) {
        log.info("Received request to delete shift: {} by user: {}", shiftId, userId);
        return ResponseEntity.ok(shiftService.deleteShift(shiftId, userId));
    }

    @PutMapping("/restore")
    @Operation(summary = "Restore shifts", description = "Restore multiple shifts by changing isDeleted from true to false")
    public ResponseEntity<ResponseDto<Void>> restoreShifts(
            @Valid @RequestBody RestoreEntitiesRequest request,
            @RequestHeader("User-ID") String userId) {
        log.info("Received request to restore shifts: {}", request.getIds());
        ResponseDto<Void> response = shiftService.restoreShifts(request.getIds(), userId);
        return ResponseEntity.ok(response);
    }
}
