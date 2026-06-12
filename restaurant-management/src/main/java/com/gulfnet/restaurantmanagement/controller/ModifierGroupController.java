package com.gulfnet.restaurantmanagement.controller;

import com.gulfnet.restaurantmanagement.service.ModifierGroupService;
import com.gulfnet.shared_library.enums.EntityStatus;
import com.gulfnet.shared_library.enums.ModifierType;
import com.gulfnet.shared_library.model.request.ModifierGroupRequestDto;
import com.gulfnet.shared_library.model.request.RestoreEntitiesRequest;
import com.gulfnet.shared_library.model.response.dto.ModifierGroupDto;
import com.gulfnet.shared_library.model.response.dto.ModifierGroupResponse;
import com.gulfnet.shared_library.model.response.dto.ResponseDto;
import com.gulfnet.shared_library.model.response.dto.ModifierGroupListResponse;
import com.gulfnet.shared_library.model.response.dto.ModifierGroupDetailsResponse;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Locale;
import java.util.UUID;
import org.springframework.context.i18n.LocaleContextHolder;

@RestController
@RequestMapping("/api/v1/modifier-groups")
@RequiredArgsConstructor
public class ModifierGroupController {

    private final ModifierGroupService modifierGroupService;

    @PostMapping
    public ResponseEntity<ResponseDto<ModifierGroupDto<ModifierGroupResponse>>> createModifierGroup(
            @RequestHeader("User-ID") String creatorId,
            @RequestHeader("User-Role") String creatorRole,
            @Valid @RequestBody ModifierGroupRequestDto request
    ) {
        ResponseDto<ModifierGroupDto<ModifierGroupResponse>> response = modifierGroupService.createModifierGroup(request, creatorId, creatorRole);
        return new ResponseEntity<>(response, HttpStatus.CREATED);
    }

    @PutMapping("/{modifierGroupId}")
    public ResponseEntity<ResponseDto<ModifierGroupDto<ModifierGroupResponse>>> updateModifierGroup(
            @PathVariable UUID modifierGroupId,
            @Valid @RequestBody ModifierGroupRequestDto request,
            @RequestHeader("User-ID") String updaterId,
            @RequestHeader("User-Role") String updaterRole
    ) {
        return ResponseEntity.ok(modifierGroupService.updateModifierGroup(modifierGroupId, request, updaterId, updaterRole));
    }

    /**
     * Retrieves a paginated and filterable list of modifier groups.
     * Supports filtering by status, modifier type, multi-select option, item association,
     * deletion status, and text search. Results are sorted and localized.
     *
     * @param status         optional filter by entity status (ACTIVE, INACTIVE, etc.)
     * @param modifierType   optional filter by modifier type
     * @param allowMultiSelect optional filter by multi-select capability
     * @param itemId         optional filter by associated item ID
     * @param search         optional search term for text search
     * @param page           optional page number for pagination
     * @param size           optional page size for pagination
     * @param sortBy         field to sort by (default: "createdAt")
     * @param sortDirection  sort direction (default: "DESC")
     * @param locale         locale code for localized responses (default: "en")
     * @param isDeleted      optional filter by deletion status
     * @return response containing paginated list of modifier groups with filters applied
     */
    @GetMapping
    public ResponseEntity<ResponseDto<ModifierGroupListResponse>> getModifierGroups(
            @RequestParam(required = false) EntityStatus status,
            @RequestParam(required = false) ModifierType modifierType,
            @RequestParam(required = false) Boolean allowMultiSelect,
            @RequestParam(required = false) UUID itemId,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "DESC") String sortDirection,
            @RequestHeader(value = "locale", defaultValue = "en") String locale,
            @RequestParam(required = false) Boolean isDeleted
    ) {
        // Set locale context for this request
        LocaleContextHolder.setLocale(Locale.forLanguageTag(locale));
        
        ResponseDto<ModifierGroupListResponse> response = modifierGroupService.getModifierGroups(
                status,
                modifierType,
                allowMultiSelect,
                itemId,
                search,
                page,
                size,
                sortBy,
                sortDirection,
                isDeleted
        );
        return ResponseEntity.ok(response);
    }


    @GetMapping("/{modifierGroupId}")
    public ResponseEntity<ResponseDto<ModifierGroupDto<ModifierGroupDetailsResponse>>> getModifierGroupDetails(
            @PathVariable UUID modifierGroupId
    ) {
        ResponseDto<ModifierGroupDto<ModifierGroupDetailsResponse>> response = modifierGroupService.getModifierGroupDetails(modifierGroupId);
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{modifierGroupId}")
    public ResponseEntity<ResponseDto<Void>> deleteModifierGroup(
            @PathVariable UUID modifierGroupId,
            @RequestHeader("User-ID") String updaterId,
            @RequestHeader("User-Role") String userRole
    ) {
        ResponseDto<Void> response = modifierGroupService.deleteModifierGroup(modifierGroupId, updaterId, userRole);
        return ResponseEntity.ok(response);
    }

    @PutMapping("/restore")
    public ResponseEntity<ResponseDto<Void>> restoreModifierGroups(
            @Valid @RequestBody RestoreEntitiesRequest request,
            @RequestHeader("User-ID") String updaterId,
            @RequestHeader("User-Role") String userRole) {
        ResponseDto<Void> response = modifierGroupService.restoreModifierGroups(request.getIds(), updaterId, userRole);
        return ResponseEntity.ok(response);
    }
}

