package com.gulfnet.restaurantmanagement.controller;

import com.gulfnet.restaurantmanagement.service.PromotionService;
import com.gulfnet.shared_library.model.request.PromotionRequest;
import com.gulfnet.shared_library.model.request.RestoreEntitiesRequest;
import com.gulfnet.shared_library.model.response.dto.PromotionDto;
import com.gulfnet.shared_library.model.response.dto.PromotionListResponse;
import com.gulfnet.shared_library.model.response.dto.PromotionResponse;
import com.gulfnet.shared_library.model.response.dto.ResponseDto;
import com.gulfnet.shared_library.model.request.MenuPromotionMappingRequest;
import com.gulfnet.shared_library.model.response.dto.MenuPromotionResponseDto;
import com.gulfnet.shared_library.model.response.dto.MenuPromotionListResponse;
import com.gulfnet.shared_library.model.request.UpdateRestaurantPromotionValidityRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("api/v1/promotions")
@RequiredArgsConstructor
@Slf4j
public class PromotionController {

    private final PromotionService promotionService;

    @PostMapping
    public ResponseEntity<ResponseDto<PromotionDto<PromotionResponse>>> createPromotion(
            @Valid @RequestBody PromotionRequest request,
            @RequestHeader("User-ID") String userId,
            @RequestHeader(value = "locale", defaultValue = "en") String locale) {

        log.info("Received create promotion request for user: {} with locale: {}", userId, locale);
        ResponseDto<PromotionDto<PromotionResponse>> response = promotionService.createPromotion(userId, request, locale);
        return ResponseEntity.ok(response);
    }

    /**
     * Retrieves a paginated and filterable list of promotions.
     * Supports filtering by status, type, deletion status, and text search.
     *
     * @param page      optional page number for pagination
     * @param size      optional page size for pagination
     * @param status    optional filter by promotion status
     * @param type      optional filter by promotion type
     * @param search    optional search term for text search
     * @param sortBy    field to sort by (default: "createdAt")
     * @param direction sort direction (default: DESC)
     * @param locale    locale code for localized responses (default: "en")
     * @param isDeleted optional filter by deletion status
     * @return response containing paginated list of promotions with filters applied
     */
    @GetMapping
    public ResponseEntity<ResponseDto<PromotionListResponse>> getPromotions(
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "DESC") Sort.Direction direction,
            @RequestHeader(value = "locale", defaultValue = "en") String locale,
            @RequestParam(required = false) Boolean isDeleted) {
        
        log.info("Received get promotions list request with locale: {}", locale);
        ResponseDto<PromotionListResponse> response = promotionService.getPromotions(
                page, size, status, type, search, sortBy, direction, locale, isDeleted);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{id}")
    public ResponseEntity<ResponseDto<PromotionDto<PromotionResponse>>> getPromotionDetails(
            @PathVariable UUID id,
            @RequestParam(required = false) UUID menuId,
            @RequestHeader(value = "locale", defaultValue = "en") String locale) {
        log.info("Received get promotion details request for id: {} with menuId: {} and locale: {}", id, menuId, locale);
        ResponseDto<PromotionDto<PromotionResponse>> response = promotionService.getPromotionDetails(id, menuId, locale);
        return ResponseEntity.ok(response);
    }

    @PutMapping("/{id}")
    public ResponseEntity<ResponseDto<PromotionDto<PromotionResponse>>> updatePromotion(
            @PathVariable UUID id,
            @Valid @RequestBody PromotionRequest request,
            @RequestHeader("User-ID") String userId,
            @RequestHeader(value = "locale", defaultValue = "en") String locale) {
        log.info("Received update promotion request for id: {} by user: {} with locale: {}", id, userId, locale);
        ResponseDto<PromotionDto<PromotionResponse>> response = promotionService.updatePromotion(id, request, userId, locale);
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ResponseDto<String>> deletePromotion(
            @PathVariable UUID id,
            @RequestHeader("User-ID") String userId) {
        log.info("Received delete promotion request for id: {} by user: {}", id, userId);
        ResponseDto<String> response = promotionService.deletePromotion(id, userId);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/assign-to-menu")
    public ResponseEntity<ResponseDto<MenuPromotionResponseDto>> assignPromotionToMenu(
            @Valid @RequestBody MenuPromotionMappingRequest request,
            @RequestHeader("User-ID") String userId,
            @RequestHeader(value = "locale", defaultValue = "en") String locale) {
        log.info("Received assign promotion to menu request");
        ResponseDto<MenuPromotionResponseDto> response = promotionService.assignPromotionToMenu(request, userId, locale);
        return ResponseEntity.ok(response);
        }

    /**
     * Retrieves promotions assigned to a specific menu.
     * Returns a paginated and filterable list of promotions with menu assignment details.
     * Optionally filters by restaurant to show restaurant-specific validity.
     *
     * @param menuId       the UUID of the menu to get assigned promotions for
     * @param restaurantId optional restaurant ID to filter by restaurant-specific validity
     * @param page         optional page number for pagination
     * @param size         optional page size for pagination
     * @param search       optional search term for text search
     * @param isAvailable  optional filter by availability status
     * @param sortBy       field to sort by (default: "createdAt")
     * @param direction    sort direction (default: DESC)
     * @param locale       locale code for localized responses (default: "en")
     * @return response containing paginated list of promotions assigned to the menu
     */
    @GetMapping("/menu/{menuId}/assigned")
public ResponseEntity<ResponseDto<MenuPromotionListResponse>> getMenuAssignedPromotions(
        @PathVariable UUID menuId,
        @RequestParam(required = false) UUID restaurantId,   // ✅ optional
        @RequestParam(required = false) Integer page,
        @RequestParam(required = false) Integer size,
        @RequestParam(required = false) String search,
        @RequestParam(required = false) Boolean isAvailable,
        @RequestParam(defaultValue = "createdAt") String sortBy,
        @RequestParam(defaultValue = "DESC") Sort.Direction direction,
        @RequestHeader(value = "locale", defaultValue = "en") String locale) {

    ResponseDto<MenuPromotionListResponse> response =
        promotionService.getMenuAssignedPromotions(
            menuId, restaurantId, page, size, search, isAvailable, sortBy, direction, locale
        );

    return ResponseEntity.ok(response);
}

    /**
     * Retrieves promotions assigned to a specific restaurant.
     * Returns a paginated and filterable list of promotions with restaurant-specific validity details.
     *
     * @param restaurantId the UUID of the restaurant to get assigned promotions for
     * @param page         optional page number for pagination
     * @param size         optional page size for pagination
     * @param search       optional search term for text search
     * @param status       optional filter by promotion status
     * @param sortBy       field to sort by (default: "name")
     * @param direction    sort direction (default: ASC)
     * @param locale       locale code for localized responses (default: "en")
     * @return response containing paginated list of promotions assigned to the restaurant
     */
    @GetMapping("/restaurants/{restaurantId}/promotions")
    public ResponseEntity<ResponseDto<MenuPromotionListResponse>> getRestaurantAssignedPromotions(
            @PathVariable UUID restaurantId,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "name") String sortBy,
            @RequestParam(defaultValue = "ASC") Sort.Direction direction,
            @RequestHeader(value = "locale", defaultValue = "en") String locale) {

        log.info("Received get assigned promotions for restaurant {} with locale {}, search {}, and status {}", restaurantId, locale, search, status);
        ResponseDto<MenuPromotionListResponse> response = promotionService.getRestaurantAssignedPromotions(
                restaurantId, page, size, search, status, sortBy, direction, locale);
        return ResponseEntity.ok(response);
    }

    /**
     * Get promotion details for a specific restaurant and promotion.
     * Validity is taken from RestaurantPromotionMapping (restaurant-specific).
     */
    @GetMapping("/restaurants/{restaurantId}/promotions/{promotionId}")
    public ResponseEntity<ResponseDto<MenuPromotionResponseDto>> getRestaurantPromotionDetails(
            @PathVariable UUID restaurantId,
            @PathVariable UUID promotionId,
            @RequestParam(required = false) UUID comboId,
            @RequestHeader(value = "locale", defaultValue = "en") String locale) {

        log.info("Received get promotion details for restaurant {} and promotion {} with locale {}",
                restaurantId, promotionId, locale);

        ResponseDto<MenuPromotionResponseDto> response = promotionService.getRestaurantPromotionDetails(
                restaurantId, promotionId, comboId, locale);
        return ResponseEntity.ok(response);
    }

    /**
     * Update promotion validity for a specific restaurant.
     * Updates validFrom and validTo in RestaurantPromotionMapping.
     */
    @PutMapping("/restaurants/{restaurantId}/promotions/{promotionId}/validity")
    public ResponseEntity<ResponseDto<MenuPromotionResponseDto>> updateRestaurantPromotionValidity(
            @PathVariable UUID restaurantId,
            @PathVariable UUID promotionId,
            @RequestBody UpdateRestaurantPromotionValidityRequest request,
            @RequestHeader(value = "locale", defaultValue = "en") String locale) {

        log.info("Request received to update promotion validity for restaurant {} and promotion {} with language: {}",
                restaurantId, promotionId, locale);

        ResponseDto<MenuPromotionResponseDto> response = promotionService.updateRestaurantPromotionValidity(
                restaurantId, promotionId, request, locale);
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/menu/{menuId}/promotion/{promotionId}")
    public ResponseEntity<ResponseDto<String>> deleteMenuPromotionAssignment(
            @PathVariable UUID menuId,
            @PathVariable UUID promotionId,
            @RequestHeader(value = "locale", defaultValue = "en") String locale) {
        log.info("Received request to delete menu-promotion assignment. MenuId: {}, PromotionId: {}", menuId, promotionId);
        ResponseDto<String> response = promotionService.deleteMenuPromotionAssignment(menuId, promotionId, locale);
        return ResponseEntity.ok(response);
    }

    @PutMapping("/assign-to-menu")
    public ResponseEntity<ResponseDto<MenuPromotionResponseDto>> updateMenuPromotionAssignment(
            @Valid @RequestBody MenuPromotionMappingRequest request,
            @RequestHeader(value = "locale", defaultValue = "en") String locale) {
        log.info("Received update menu-promotion assignment request");
        ResponseDto<MenuPromotionResponseDto> response = promotionService.updateMenuPromotionAssignment( request, locale);
        return ResponseEntity.ok(response);
    }

    @PutMapping("/restore")
    public ResponseEntity<ResponseDto<Void>> restorePromotions(
            @Valid @RequestBody RestoreEntitiesRequest request,
            @RequestHeader("User-ID") String userId) {
        log.info("Received restore promotions request");
        ResponseDto<Void> response = promotionService.restorePromotions(request.getIds(), userId);
        return ResponseEntity.ok(response);
    }
}