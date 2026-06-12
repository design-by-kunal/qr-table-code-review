package com.gulfnet.restaurantmanagement.controller;

import com.gulfnet.shared_library.model.request.ComboRequest;
import com.gulfnet.shared_library.model.response.dto.ComboDto;
import com.gulfnet.shared_library.model.response.dto.ComboListResponse;
import com.gulfnet.shared_library.model.response.dto.ComboResponse;
import com.gulfnet.shared_library.model.response.dto.ComboDetailsResponse;
import com.gulfnet.shared_library.model.response.dto.ResponseDto;
import com.gulfnet.restaurantmanagement.service.ComboService;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/v1/combos")
@RequiredArgsConstructor
public class ComboController {

    private final ComboService comboService;

    @PostMapping
    public ResponseEntity<ResponseDto<ComboDto<ComboResponse>>> createCombo(
            @Valid @RequestBody ComboRequest request,
            @RequestHeader("User-ID") String userId,
            @RequestHeader(value = "locale", defaultValue = "en") String locale) {
        log.info("Received create combo request with locale: {}", locale);
        ResponseDto<ComboDto<ComboResponse>> response = 
            comboService.createCombo(userId, request, locale);
        return ResponseEntity.ok(response);
    }

    @PutMapping("/{comboId}")
    public ResponseEntity<ResponseDto<ComboDto<ComboResponse>>> updateCombo(
            @PathVariable UUID comboId,
            @Valid @RequestBody ComboRequest request,
            @RequestHeader("User-ID") String userId,
            @RequestHeader(value = "locale", defaultValue = "en") String locale) {
        log.info("Received update combo request for comboId: {} with locale: {}", comboId, locale);
        ResponseDto<ComboDto<ComboResponse>> response = 
            comboService.updateCombo(comboId, userId, request, locale);
        return ResponseEntity.ok(response);
    }

    /**
     * Retrieves a paginated and filterable list of combos for a specific menu.
     * Supports filtering by status, type, availability, restaurant, and text search.
     *
     * @param menuId      the UUID of the menu to get combos for
     * @param page        optional page number for pagination
     * @param size        optional page size for pagination
     * @param status      optional filter by combo status
     * @param type        optional filter by combo type
     * @param search      optional search term for text search
     * @param isAvailable optional filter by availability status
     * @param sortBy      field to sort by (default: "createdAt")
     * @param direction   sort direction (default: DESC)
     * @param locale      locale code for localized responses (default: "en")
     * @param restaurantId optional filter by restaurant ID
     * @return response containing paginated list of combos with filters applied
     */
    @GetMapping("/menu/{menuId}")
    public ResponseEntity<ResponseDto<ComboListResponse>> getCombos(
        @PathVariable UUID menuId,
        @RequestParam(required = false) Integer page,
        @RequestParam(required = false) Integer size,
        @RequestParam(required = false) String status,
        @RequestParam(required = false) String type,
        @RequestParam(required = false) String search,
        @RequestParam(required = false) Boolean isAvailable,
        @RequestParam(defaultValue = "createdAt") String sortBy,
        @RequestParam(defaultValue = "DESC") Sort.Direction direction,
        @RequestHeader(value = "locale", defaultValue = "en") String locale,
        @RequestParam(required = false) UUID restaurantId) {
    
    log.info("Received get combos list request for menuId: {} with isAvailable: {} and locale: {}", menuId, isAvailable, locale);
    ResponseDto<ComboListResponse> response = comboService.getCombos(
            menuId, page, size, status, type, search, isAvailable, sortBy, direction, locale, restaurantId);
    return ResponseEntity.ok(response);
}

    /**
     * Retrieves detailed information about a specific combo including groups, items, and modifiers.
     * Supports filtering by order type and includes alcohol type information.
     *
     * @param comboId      the UUID of the combo to get details for
     * @param locale       locale code for localized responses (default: "en")
     * @param restaurantId optional filter by restaurant ID for availability checks
     * @param orderType    optional filter by order type (DINE_IN, TAKEAWAY) to filter combo items
     * @return response containing detailed combo information
     */
    @GetMapping("/{comboId}/details")
    public ResponseEntity<ResponseDto<ComboDto<ComboDetailsResponse>>> getComboDetailsById(
            @PathVariable UUID comboId,
            @RequestHeader(value = "locale", defaultValue = "en") String locale,
            @RequestParam(required = false) UUID restaurantId,
            @RequestParam(name = "orderType", required = false) String orderType) {
        log.info("Received get combo details request for comboId: {} with locale: {}, orderType: {}", comboId, locale, orderType);
        ResponseDto<ComboDto<ComboDetailsResponse>> response = comboService.getComboDetailsById(comboId, locale, restaurantId, orderType);
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{comboId}")
    public ResponseEntity<ResponseDto<String>> deleteCombo(
            @PathVariable UUID comboId,
            @RequestHeader("User-ID") String userId,
            @RequestHeader(value = "locale", defaultValue = "en") String locale) {
        log.info("Received delete combo request for comboId: {} with locale: {}", comboId, locale);
        ResponseDto<String> response = comboService.deleteCombo(comboId, userId, locale);
        return ResponseEntity.ok(response);
    }

}
