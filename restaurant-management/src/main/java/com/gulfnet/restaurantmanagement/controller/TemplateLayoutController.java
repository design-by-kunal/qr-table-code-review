package com.gulfnet.restaurantmanagement.controller;

import com.gulfnet.restaurantmanagement.service.TemplateLayoutService;
import com.gulfnet.shared_library.enums.EntityStatus;
import com.gulfnet.shared_library.model.request.TemplateLayoutRequest;
import com.gulfnet.shared_library.model.request.TemplateLayoutRequestDto;
import com.gulfnet.shared_library.model.request.RestoreEntitiesRequest;
import com.gulfnet.shared_library.model.response.dto.TemplateLayoutResponseDto;
import com.gulfnet.shared_library.model.response.dto.ResponseDto;
import com.gulfnet.shared_library.model.response.dto.TemplateLayoutDto;
import com.gulfnet.shared_library.model.response.dto.TemplateLayoutListResponse;
import com.gulfnet.shared_library.model.response.dto.TemplateLayoutListDto;
import com.gulfnet.shared_library.model.response.dto.TemplateLayoutStructureDto;
import com.gulfnet.shared_library.model.response.dto.TemplateLayoutResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/v1/template-layouts")
@RequiredArgsConstructor
public class TemplateLayoutController {

    private final TemplateLayoutService templateLayoutService;

    @PostMapping
    public ResponseEntity<ResponseDto<TemplateLayoutDto<TemplateLayoutResponse>>> createTemplateLayout(
            @Valid @RequestBody TemplateLayoutRequest request,
            @RequestHeader("User-ID") String creatorId) {
        log.info("Request received to create template layout by user: {}", creatorId);
        ResponseDto<TemplateLayoutDto<TemplateLayoutResponse>> response = templateLayoutService.createTemplateLayout(request, creatorId);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{id}")
    public ResponseEntity<ResponseDto<TemplateLayoutDto<TemplateLayoutResponse>>> getTemplateLayout(
            @PathVariable UUID id) {
        log.info("Request received to get template layout with id: {}", id);
        ResponseDto<TemplateLayoutDto<TemplateLayoutResponse>> response = templateLayoutService.getTemplateLayout(id);
        return ResponseEntity.ok(response);
    }

    /**
     * Returns a filterable, paginated list of template layouts. Optional query parameters control text
     * search, {@link EntityStatus}, translation language, page/size, sort field and direction, and
     * inclusion of soft-deleted rows.
     *
     * @param search        optional substring filter
     * @param status        optional status filter
     * @param languageCode  optional locale/language filter for translations
     * @param page          optional page index (service-defined default when omitted)
     * @param size          optional page size
     * @param sortBy        field to sort by (default {@code createdAt})
     * @param direction     sort direction (default {@code DESC})
     * @param isDeleted     optional soft-delete filter
     * @return {@link ResponseEntity} wrapping {@link TemplateLayoutListDto} via {@link ResponseDto}
     */
    @GetMapping
    public ResponseEntity<ResponseDto<TemplateLayoutListDto>> getAllTemplateLayouts(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) EntityStatus status,
            @RequestParam(required = false) String languageCode,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            @RequestParam(required = false, defaultValue = "createdAt") String sortBy,
            @RequestParam(required = false, defaultValue = "DESC") String direction,
            @RequestParam(required = false) Boolean isDeleted) {

        ResponseDto<TemplateLayoutListDto> response = templateLayoutService.getAllTemplateLayouts(
                search, status, languageCode, page, size, sortBy, direction, isDeleted);
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ResponseDto<Void>> deleteTemplateLayout(
            @PathVariable UUID id,
            @RequestHeader("User-ID") String updaterId) {
        log.info("Request received to delete template layout with id: {} by user: {}", id, updaterId);
        ResponseDto<Void> response = templateLayoutService.softDeleteTemplateLayout(id, updaterId);
        return ResponseEntity.ok(response);
    }

    @PutMapping("/{id}")
    public ResponseEntity<ResponseDto<TemplateLayoutDto<TemplateLayoutResponse>>> updateTemplateLayout(
            @PathVariable UUID id,
            @Valid @RequestBody TemplateLayoutRequest request,
            @RequestHeader("User-ID") String updaterId,
            @RequestHeader("User-Role") String userRole) {
        ResponseDto<TemplateLayoutDto<TemplateLayoutResponse>> response =
                templateLayoutService.updateTemplateLayout(id, request, updaterId, userRole);
        return ResponseEntity.ok(response);
    }

    /**
     * Creates sections, rows, and tables for an existing template layout. Requires caller identity and role
     * headers for authorization in the service layer.
     *
     * @param templateLayoutId parent template layout identifier
     * @param requestDto       structure payload (sections, rows, tables, etc.)
     * @param creatorId        {@code User-ID} of the actor
     * @param userRole         {@code User-Role} for permission checks
     * @return {@link ResponseEntity} with the persisted structure wrapped in {@link ResponseDto}
     */
    @PostMapping("/structure/{templateLayoutId}")
    public ResponseEntity<ResponseDto<TemplateLayoutStructureDto<TemplateLayoutResponseDto>>> createStructure(
            @PathVariable UUID templateLayoutId,
            @Valid @RequestBody TemplateLayoutRequestDto requestDto,
            @RequestHeader("User-ID") String creatorId,
            @RequestHeader("User-Role") String userRole) {
        log.info("Request to create structure for templateLayoutId: {} by user: {}", templateLayoutId, creatorId);

        ResponseDto<TemplateLayoutStructureDto<TemplateLayoutResponseDto>> response =
        templateLayoutService.createTemplateStructure(templateLayoutId, requestDto, creatorId, userRole);

        return ResponseEntity.ok(response);
    }

    /**
     * Updates the floor-plan structure (sections, rows, tables) for an existing template layout.
     * Same path variable and body shape as {@link #createStructure}, but uses HTTP PUT semantics.
     *
     * @param templateLayoutId template layout to replace structure for
     * @param requestDto       updated structure payload
     * @param updaterId        {@code User-ID} of the actor
     * @param userRole         {@code User-Role} for permission checks
     * @return {@link ResponseEntity} with the updated structure wrapped in {@link ResponseDto}
     */
    @PutMapping("/structure/{templateLayoutId}")
    public ResponseEntity<ResponseDto<TemplateLayoutStructureDto<TemplateLayoutResponseDto>>> updateStructure(
            @PathVariable UUID templateLayoutId,
            @Valid @RequestBody TemplateLayoutRequestDto requestDto,
            @RequestHeader("User-ID") String updaterId,
            @RequestHeader("User-Role") String userRole) {
        log.info("Request to update structure for templateLayoutId: {} by user: {}", templateLayoutId, updaterId);

        ResponseDto<TemplateLayoutStructureDto<TemplateLayoutResponseDto>> response =
                templateLayoutService.updateTemplateStructure(templateLayoutId, requestDto, updaterId, userRole);

        return ResponseEntity.ok(response);
    }

    @GetMapping("/structure/{templateLayoutId}")
    public ResponseDto<TemplateLayoutStructureDto<TemplateLayoutResponseDto>> getTemplateStructure(
            @PathVariable UUID templateLayoutId) {
        return templateLayoutService.getTemplateStructure(templateLayoutId);
    }

    @PutMapping("/restore")
    public ResponseEntity<ResponseDto<Void>> restoreTemplateLayouts(
            @Valid @RequestBody RestoreEntitiesRequest request,
            @RequestHeader("User-ID") String updaterId) {
        log.info("Request received to restore template layouts by user: {}", updaterId);
        ResponseDto<Void> response = templateLayoutService.restoreTemplateLayouts(request.getIds(), updaterId);
        return ResponseEntity.ok(response);
    }




}
