package com.gulfnet.restaurantmanagement.controller;

import com.gulfnet.restaurantmanagement.service.MenuStructureService;
import com.gulfnet.shared_library.model.request.MenuStructureRequest;
import com.gulfnet.shared_library.model.request.RestoreEntitiesRequest;
import com.gulfnet.shared_library.model.response.dto.MenuCategoryStructureResponse;
import com.gulfnet.shared_library.model.response.dto.ResponseDto;
import com.gulfnet.shared_library.model.response.dto.MenuStructureDto;
import com.gulfnet.shared_library.model.response.dto.MenuStructureResponse;
import com.gulfnet.shared_library.model.response.dto.MenuStructureListResponse;
import com.gulfnet.shared_library.enums.EntityStatus;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@Slf4j 
@RestController
@RequestMapping("/api/v1/menu-structure")
@RequiredArgsConstructor
public class MenuStructureController {

    private final MenuStructureService menuStructureService;


    @PostMapping
    public ResponseEntity<ResponseDto<MenuStructureDto<MenuStructureResponse>>> createMenuStructure(
            @Valid @RequestBody MenuStructureRequest request,
            @RequestHeader("User-ID") String userId,
            @RequestHeader(value = "locale", defaultValue = "en") String locale) {
        log.info("Received create request with locale: {}", locale);
        ResponseDto<MenuStructureDto<MenuStructureResponse>> saved = 
            menuStructureService.createMenuStructure(request, userId, locale);
        return ResponseEntity.ok(saved);
    }

    @PutMapping("/{id}")
    public ResponseEntity<ResponseDto<MenuStructureDto<MenuStructureResponse>>> updateMenuStructure(
            @PathVariable UUID id,
            @Valid @RequestBody MenuStructureRequest request,
            @RequestHeader("User-ID") String userId,
            @RequestHeader(value = "locale", defaultValue = "en") String locale) {
        log.info("Received update request with locale: {}", locale);
        ResponseDto<MenuStructureDto<MenuStructureResponse>> updated = 
            menuStructureService.updateMenuStructure(id, request, userId, locale);
        return ResponseEntity.ok(updated);
    }

    /**
     * Retrieves a paginated and filterable list of menu structures.
     * Supports filtering by status, deletion status, and text search.
     *
     * @param page      page number for pagination (default: 0)
     * @param size      page size for pagination (default: 10)
     * @param status    optional filter by entity status (ACTIVE, INACTIVE, etc.)
     * @param search    optional search term for text search
     * @param sortBy    field to sort by (default: "createdAt")
     * @param direction sort direction (default: DESC)
     * @param locale    locale code for localized responses (default: "en")
     * @param isDeleted optional filter by deletion status
     * @return response containing paginated list of menu structures with filters applied
     */
    @GetMapping
    public ResponseEntity<ResponseDto<MenuStructureListResponse>> getMenuStructures(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) EntityStatus status,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "DESC") Sort.Direction direction,
            @RequestHeader(value = "locale", defaultValue = "en") String locale,
            @RequestParam(required = false) Boolean isDeleted) {

        log.info("Request received to fetch menu structures with language: {}", locale);
        log.info("Query parameters - page: {}, size: {}, status: {}, search: {}, sortBy: {}, direction: {}, isDeleted: {}",
                page, size, status, search, sortBy, direction, locale, isDeleted);

        ResponseDto<MenuStructureListResponse> response = menuStructureService.getMenuStructures(
                page, size, status, search, sortBy, direction, locale, isDeleted);
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ResponseDto<String>> deleteMenuStructure(
            @PathVariable UUID id,
            @RequestHeader("User-ID") String userId,
            @RequestHeader(value = "locale", defaultValue = "en") String locale) {
        log.info("Received delete request with locale:  {}", locale);
        ResponseDto<String> response = menuStructureService.deleteMenuStructure(id, userId, locale);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{id}")
    public ResponseEntity<ResponseDto<MenuStructureDto<MenuStructureResponse>>> getMenuStructureById(
            @PathVariable UUID id,
            @RequestHeader(value = "locale", defaultValue = "en") String locale) {
        log.info("Received request to fetch menu structure with id: {} and locale: {}", id, locale);
        ResponseDto<MenuStructureDto<MenuStructureResponse>> response = 
            menuStructureService.getMenuStructureById(id, locale);
        return ResponseEntity.ok(response);
    }

    /**
     * Returns the category tree for a {@code menu} and {@code menuStructure}, including items when the
     * structure is assigned to that menu. Delegates to {@link MenuStructureService#getMenuStructure}
     * which validates the structure (not deleted, active), loads the menu, and applies optional filters
     * such as {@link EntityStatus}, text search, and {@link com.gulfnet.shared_library.enums.ItemOrderType}
     * via {@code orderType} / {@code itemOrderType} ({@code itemOrderType} wins when both are set).
     *
     * @param menuId           menu whose items may be merged into the structure response
     * @param menuStructureId layout definition to expand into categories
     * @param status           optional filter on categories/items when building with menu context
     * @param search           optional free-text filter
     * @param hasCombo         optional flag forwarded to the service (API contract)
     * @param orderType        optional {@link com.gulfnet.shared_library.enums.ItemOrderType} name when {@code itemOrderType} is absent
     * @param itemOrderType    optional item order type filter (takes precedence over {@code orderType})
     * @return {@link ResponseEntity} with {@link ResponseDto} containing {@link MenuCategoryStructureResponse}
     */
    @GetMapping("/structure")
    public ResponseEntity<ResponseDto<MenuCategoryStructureResponse>> getMenuStructure(
            @RequestParam UUID menuId,
            @RequestParam UUID menuStructureId,
            @RequestParam(required = false) EntityStatus status,
            @RequestParam(required = false) String search,
            @RequestParam(required = false, defaultValue = "false") Boolean hasCombo,
            @RequestParam(required = false) String orderType,
            @RequestParam(required = false) String itemOrderType) {

        ResponseDto<MenuCategoryStructureResponse> response = menuStructureService.getMenuStructure(
                menuId, menuStructureId, status, search, hasCombo, orderType, itemOrderType);
        return ResponseEntity.ok(response);
    }

    @PutMapping("/restore")
    public ResponseEntity<ResponseDto<Void>> restoreMenuStructures(
            @Valid @RequestBody RestoreEntitiesRequest request,
            @RequestHeader("User-ID") String userId,
            @RequestHeader(value = "locale", defaultValue = "en") String locale) {
        log.info("Received restore request with locale: {}", locale);
        ResponseDto<Void> response = menuStructureService.restoreMenuStructures(request.getIds(), userId, locale);
        return ResponseEntity.ok(response);
    }
}
