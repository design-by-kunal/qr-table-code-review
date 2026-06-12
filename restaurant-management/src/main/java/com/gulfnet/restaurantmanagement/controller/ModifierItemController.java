package com.gulfnet.restaurantmanagement.controller;

import com.gulfnet.shared_library.enums.EntityStatus;
import com.gulfnet.shared_library.model.request.ModifierItemRequestDto;
import com.gulfnet.shared_library.model.response.dto.ModifierItemDto;
import com.gulfnet.shared_library.model.response.dto.ModifierItemResponseDto;
import com.gulfnet.shared_library.model.response.dto.ResponseDto;
import com.gulfnet.shared_library.model.response.dto.ModifierItemListResponse;
import com.gulfnet.restaurantmanagement.service.ModifierItemService;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.context.i18n.LocaleContextHolder;

import java.util.Locale;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/v1/modifier-items")
@RequiredArgsConstructor
public class ModifierItemController {

    private final ModifierItemService modifierItemService;

    @PostMapping
public ResponseEntity<ResponseDto<ModifierItemDto<ModifierItemResponseDto>>> createModifierItem(
        @Valid @RequestBody ModifierItemRequestDto request,
        @RequestHeader("User-ID") String userId,
        @RequestHeader(value = "locale", defaultValue = "en") String locale) {
    log.info("Received create request with locale: {}", locale);
    ResponseDto<ModifierItemDto<ModifierItemResponseDto>> saved = 
        modifierItemService.createModifierItem(userId, request, locale);
    return ResponseEntity.ok(saved);
}

@PutMapping("/{id}")
public ResponseEntity<ResponseDto<ModifierItemDto<ModifierItemResponseDto>>> updateModifierItem(
        @PathVariable UUID id,
        @Valid @RequestBody ModifierItemRequestDto request,
        @RequestHeader("User-ID") String userId,
        @RequestHeader(value = "locale", defaultValue = "en") String locale) {
    log.info("Received update request with locale: {}", locale);
    ResponseDto<ModifierItemDto<ModifierItemResponseDto>> updated = 
        modifierItemService.updateModifierItem(id, request, userId, locale);
    return ResponseEntity.ok(updated);
}

@GetMapping("/{id}")
public ResponseEntity<ResponseDto<ModifierItemDto<ModifierItemResponseDto>>> getModifierItemDetails(
        @PathVariable UUID id,
        @RequestHeader(value = "locale", defaultValue = "en") String locale){
    return ResponseEntity.ok(modifierItemService.getModifierItemDetails(id, locale));
}

/**
 * Retrieves a paginated and filterable list of modifier items for a specific modifier group.
 * Supports filtering by status and text search.
 *
 * @param modifierGroupId the UUID of the modifier group to get items for
 * @param status          optional filter by entity status (ACTIVE, INACTIVE, etc.)
 * @param search          optional search term for text search
 * @param page            page number for pagination (default: 1)
 * @param size            page size for pagination (default: 10)
 * @param sortBy          field to sort by (default: "createdAt")
 * @param sortDirection   sort direction (default: "DESC")
 * @param locale          locale code for localized responses (default: "en")
 * @return response containing paginated list of modifier items with filters applied
 */
@GetMapping
public ResponseEntity<ResponseDto<ModifierItemListResponse>> getModifierItemsByGroupId(
        @RequestParam UUID modifierGroupId,
        @RequestParam(required = false) EntityStatus status,
        @RequestParam(required = false) String search,
        @RequestParam(defaultValue = "1") Integer page,
        @RequestParam(defaultValue = "10") Integer size,
        @RequestParam(defaultValue = "createdAt") String sortBy,
        @RequestParam(defaultValue = "DESC") String sortDirection,
        @RequestHeader(value = "locale", defaultValue = "en") String locale
) {
    // Set locale context for this request
    LocaleContextHolder.setLocale(Locale.forLanguageTag(locale));
    
    ResponseDto<ModifierItemListResponse> response = modifierItemService.getModifierItemsByGroupId(
            modifierGroupId,
            status,
            search,
            page,
            size,
            sortBy,
            sortDirection
    );
    return ResponseEntity.ok(response);
}

@DeleteMapping("/{id}")
public ResponseEntity<ResponseDto<Void>> deleteModifierItem(
        @PathVariable UUID id,
        @RequestHeader("User-ID") String userId,
        @RequestHeader("User-Role") String userRole
) {
    ResponseDto<Void> response = modifierItemService.deleteModifierItem(id, userId, userRole);
    return ResponseEntity.ok(response);
}

}