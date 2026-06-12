package com.gulfnet.restaurantmanagement.controller;

import com.gulfnet.shared_library.model.request.KdsRequest;
import com.gulfnet.shared_library.model.request.AssignUserToKdsRequest;
import com.gulfnet.shared_library.model.request.UnassignUserFromKdsRequest;
import com.gulfnet.shared_library.model.request.UpdateKdsConfigRequest;
import com.gulfnet.shared_library.model.request.AssignDeviceToKdsRequest;
import com.gulfnet.shared_library.model.response.dto.KdsConfigurationListResponse;
import com.gulfnet.shared_library.model.response.dto.KdsDto;
import com.gulfnet.shared_library.model.response.dto.KdsListResponse;
import com.gulfnet.shared_library.model.response.dto.KdsResponse;
import com.gulfnet.shared_library.model.response.dto.KdsAssignedUserListResponse;
import com.gulfnet.shared_library.model.response.dto.ResponseDto;
import com.gulfnet.restaurantmanagement.service.KdsService;

import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import lombok.RequiredArgsConstructor;

import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/v1/kds")
@RequiredArgsConstructor
public class KdsController {

    private final KdsService kdsService;

    @PostMapping
    public ResponseEntity<ResponseDto<KdsDto<KdsResponse>>> createKds(
            @Valid @RequestBody KdsRequest request,
            @RequestHeader("User-ID") String userId,
            @RequestHeader(value = "locale", defaultValue = "en") String locale) {
        log.info("Received create KDS request with locale: {}", locale);
        ResponseDto<KdsDto<KdsResponse>> saved = 
            kdsService.createKds(userId, request, locale);
        return ResponseEntity.ok(saved);
    }

    @PutMapping("/{id}")
    public ResponseEntity<ResponseDto<KdsDto<KdsResponse>>> updateKds(
            @PathVariable UUID id,
            @Valid @RequestBody KdsRequest request,
            @RequestHeader("User-ID") String userId,
            @RequestHeader(value = "locale", defaultValue = "en") String locale) {
        log.info("Received update KDS request with locale: {}", locale);
        ResponseDto<KdsDto<KdsResponse>> updated = 
            kdsService.updateKds(id, request, userId, locale);
        return ResponseEntity.ok(updated);
    }

    @GetMapping("/{id}")
    public ResponseEntity<ResponseDto<KdsDto<KdsResponse>>> getKdsById(
            @PathVariable UUID id,
            @RequestHeader(value = "locale", defaultValue = "en") String locale) {
        log.info("Received get KDS by id request with locale: {}", locale);
        ResponseDto<KdsDto<KdsResponse>> response = kdsService.getKdsById(id, locale);
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ResponseDto<String>> deleteKds(
            @PathVariable UUID id,
            @RequestHeader("User-ID") String userId,
            @RequestHeader(value = "locale", defaultValue = "en") String locale) {
        log.info("Received delete KDS request with locale: {}", locale);
        ResponseDto<String> response = kdsService.deleteKds(id, userId, locale);
        return ResponseEntity.ok(response);
    }

    /**
     * Retrieves a paginated and filterable list of Kitchen Display Systems (KDS).
     * Supports filtering by status and text search. Results are filtered based on the user's access.
     *
     * @param page      optional page number for pagination
     * @param size      optional page size for pagination
     * @param status    optional filter by KDS status
     * @param search    optional search term for text search
     * @param sortBy    field to sort by (default: "createdAt")
     * @param direction sort direction (default: DESC)
     * @param userId    the user ID from the request header (required)
     * @param locale    locale code for localized responses (default: "en")
     * @return response containing paginated list of KDS configurations with filters applied
     */
    @GetMapping
    public ResponseEntity<ResponseDto<KdsListResponse>> getKdsList(
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "DESC") Sort.Direction direction,
            @RequestHeader("User-ID") String userId,
            @RequestHeader(value = "locale", defaultValue = "en") String locale) {
        log.info("Received get KDS list request with locale: {}", locale);
        ResponseDto<KdsListResponse> response = kdsService.getKdsList(page, size, status, search, sortBy, direction, userId, locale);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/unassigned-categories")
    public ResponseEntity<ResponseDto<com.gulfnet.shared_library.model.response.dto.CategoryWrapperResponse>> getUnassignedCategories(
            @RequestHeader("User-ID") String userId,
            @RequestParam(value = "menuId", required = false) java.util.UUID menuId,
            @RequestHeader(value = "locale", defaultValue = "en") String locale) {
        ResponseDto<com.gulfnet.shared_library.model.response.dto.CategoryWrapperResponse> response =
                kdsService.getUnassignedCategories(userId, menuId, locale);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/assign-user")
    public ResponseEntity<ResponseDto<KdsConfigurationListResponse>> assignUserToKds(
            @Valid @RequestBody AssignUserToKdsRequest request,
            @RequestHeader("User-ID") String userId,
            @RequestHeader(value = "locale", defaultValue = "en") String locale) {
        log.info("Received assign user to KDS request with locale: {}", locale);
        ResponseDto<KdsConfigurationListResponse> response = kdsService.assignUserToKds(request, userId, locale);
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/unassign-user")
    public ResponseEntity<ResponseDto<KdsConfigurationListResponse>> unassignUserFromKds(
            @Valid @RequestBody UnassignUserFromKdsRequest request,
            @RequestHeader("User-ID") String userId,
            @RequestHeader(value = "locale", defaultValue = "en") String locale) {
        log.info("Received unassign user from KDS request with locale: {}", locale);
        ResponseDto<KdsConfigurationListResponse> response = kdsService.unassignUserFromKds(request, userId, locale);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/config/{deviceCode}")
    public ResponseEntity<ResponseDto<KdsDto<KdsResponse>>> getKdsConfigByDeviceId(
            @PathVariable String deviceCode,
            @RequestHeader(value = "locale", defaultValue = "en") String locale) {
        log.info("Received get KDS config by device code request for device code: {} with locale: {}", deviceCode, locale);
        ResponseDto<KdsDto<KdsResponse>> response = kdsService.getKdsConfigByDeviceId(deviceCode, locale);
        return ResponseEntity.ok(response);
    }

    @PutMapping("/{id}/config")
    public ResponseEntity<ResponseDto<KdsDto<KdsResponse>>> updateKdsConfig(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateKdsConfigRequest request,
            @RequestHeader(value = "locale", defaultValue = "en") String locale) {
        log.info("Received update KDS config request for KDS ID: {} with device code: {} and locale: {}", id, request.getDeviceCode(), locale);
        ResponseDto<KdsDto<KdsResponse>> response = kdsService.updateKdsConfig(id, request, locale);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/assign-device")
    public ResponseEntity<ResponseDto<KdsDto<KdsResponse>>> assignDeviceToKds(
            @Valid @RequestBody AssignDeviceToKdsRequest request,
            @RequestHeader("User-ID") String userId,
            @RequestHeader(value = "locale", defaultValue = "en") String locale) {
        log.info("Received assign device to KDS request with device code: {}, KDS ID: {} and locale: {}", 
                request.getDeviceCode(), request.getKdsId(), locale);
        ResponseDto<KdsDto<KdsResponse>> response = kdsService.assignDeviceToKds(request, userId, locale);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{id}/assigned-users")
    public ResponseEntity<ResponseDto<KdsAssignedUserListResponse>> getAssignedUsersByKdsId(
            @PathVariable UUID id,
            @RequestParam("restaurantId") UUID restaurantId,
            @RequestHeader(value = "locale", defaultValue = "en") String locale) {
        log.info("Received get assigned users for KDS ID: {}, restaurantId: {} with locale: {}", id, restaurantId, locale);
        ResponseDto<KdsAssignedUserListResponse> response = kdsService.getAssignedUsersByKdsId(id, restaurantId, locale);
        return ResponseEntity.ok(response);
    }
}

