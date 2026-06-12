package com.gulfnet.restaurantmanagement.controller;

import com.gulfnet.restaurantmanagement.service.PriceOverrideService;
import com.gulfnet.shared_library.enums.OverrideLevel;
import com.gulfnet.shared_library.enums.PriceOverrideStatus;
import com.gulfnet.shared_library.model.request.PriceOverrideRequest;
import com.gulfnet.shared_library.model.request.SchedulePriceOverrideDeactivationRequest;
import com.gulfnet.shared_library.model.request.UpdatePriceOverrideScheduleRequest;
import com.gulfnet.shared_library.model.response.dto.PriceOverrideImpactedItemListResponse;
import com.gulfnet.shared_library.model.response.dto.PriceOverrideResponse;
import com.gulfnet.shared_library.model.response.dto.PriceOverrideListResponse;
import com.gulfnet.shared_library.model.response.dto.ResponseDto;
import org.springframework.data.domain.Sort;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/price-overrides")
@RequiredArgsConstructor
@Slf4j
public class PriceOverrideController {

    private final PriceOverrideService priceOverrideService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ResponseEntity<ResponseDto<PriceOverrideResponse>> createPriceOverride(
            @Valid @RequestBody PriceOverrideRequest request,
            @RequestHeader("User-ID") String userId,
            @RequestHeader(value = "locale", defaultValue = "en") String locale) {
        
        log.info("Received create price override request for user: {} with locale: {}", userId, locale);
        ResponseDto<PriceOverrideResponse> response = priceOverrideService.createPriceOverride(request, userId, locale);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * Retrieves price overrides for a specific restaurant.
     * Returns a paginated and filterable list of price overrides with filtering by override level and status.
     *
     * @param restaurantId the UUID of the restaurant to get price overrides for (required)
     * @param page         optional page number for pagination
     * @param size         optional page size for pagination
     * @param search       optional search term for text search
     * @param overrideLevel optional filter by override level (MENU, CATEGORY, ITEM)
     * @param status       optional filter by price override status
     * @param sortBy       field to sort by (default: "createdAt")
     * @param direction    sort direction (default: DESC)
     * @param locale       locale code for localized responses (default: "en")
     * @return response containing paginated list of price overrides for the restaurant
     */
    @GetMapping
    public ResponseEntity<ResponseDto<PriceOverrideListResponse>> getPriceOverridesByRestaurant(
            @RequestParam(required = false) UUID restaurantId,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) OverrideLevel overrideLevel,
            @RequestParam(required = false) PriceOverrideStatus status,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "DESC") Sort.Direction direction,
            @RequestHeader(value = "locale", defaultValue = "en") String locale) {
        
        log.info("Received get price overrides by restaurant request for restaurant: {} with page: {}, size: {}, search: {}, overrideLevel: {}, status: {}, sortBy: {}, direction: {}, locale: {}", 
                restaurantId, page, size, search, overrideLevel, status, sortBy, direction, locale);
        ResponseDto<PriceOverrideListResponse> response = priceOverrideService.getPriceOverridesByRestaurant(
                restaurantId, page, size, search, overrideLevel, status, sortBy, direction, locale);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{id}")
    public ResponseEntity<ResponseDto<PriceOverrideResponse>> getPriceOverrideById(
            @PathVariable UUID id,
            @RequestHeader(value = "locale", defaultValue = "en") String locale) {
        
        log.info("Received get price override request for ID: {} with locale: {}", id, locale);
        ResponseDto<PriceOverrideResponse> response = priceOverrideService.getPriceOverrideById(id, locale);
        return ResponseEntity.ok(response);
    }

    @PutMapping("/{id}")
    public ResponseEntity<ResponseDto<PriceOverrideResponse>> updatePriceOverride(
            @PathVariable UUID id,
            @Valid @RequestBody PriceOverrideRequest request,
            @RequestHeader("User-ID") String userId,
            @RequestHeader(value = "locale", defaultValue = "en") String locale) {
        
        log.info("Received update price override request for ID: {}, user: {} with locale: {}", id, userId, locale);
        ResponseDto<PriceOverrideResponse> response = priceOverrideService.updatePriceOverride(id, request, userId, locale);
        return ResponseEntity.ok(response);
    }

    /**
     * Updates the schedule (activation and deactivation times) for a price override.
     *
     * @param id      the UUID of the price override to update schedule for
     * @param request the schedule update request containing activation and deactivation times
     * @param userId  the user ID from the request header (required)
     * @param locale  locale code for localized responses (default: "en")
     * @return response containing the updated price override with new schedule
     */
    @PutMapping("/{id}/schedule")
    public ResponseEntity<ResponseDto<PriceOverrideResponse>> updatePriceOverrideSchedule(
            @PathVariable UUID id,
            @Valid @RequestBody UpdatePriceOverrideScheduleRequest request,
            @RequestHeader("User-ID") String userId,
            @RequestHeader(value = "locale", defaultValue = "en") String locale) {
        
        log.info("Received update schedule request for price override ID: {}, user: {} with locale: {}", 
                 id, userId, locale);
        ResponseDto<PriceOverrideResponse> response = 
            priceOverrideService.updatePriceOverrideSchedule(id, request, userId, locale);
        return ResponseEntity.ok(response);
    }

    /**
     * Schedules the deactivation of a price override at a specific UTC time.
     * The price override will be automatically deactivated at the scheduled time.
     *
     * @param id      the UUID of the price override to schedule deactivation for
     * @param request the deactivation schedule request containing scheduled deactivation time
     * @param userId  the user ID from the request header (required)
     * @param locale  locale code for localized responses (default: "en")
     * @return response containing the updated price override with scheduled deactivation
     */
    @PutMapping("/{id}/schedule-deactivation")
    public ResponseEntity<ResponseDto<PriceOverrideResponse>> schedulePriceOverrideDeactivation(
            @PathVariable UUID id,
            @Valid @RequestBody SchedulePriceOverrideDeactivationRequest request,
            @RequestHeader("User-ID") String userId,
            @RequestHeader(value = "locale", defaultValue = "en") String locale) {
        
        log.info("Received schedule deactivation request for price override ID: {}, user: {} with locale: {}", 
                 id, userId, locale);
        ResponseDto<PriceOverrideResponse> response = 
            priceOverrideService.schedulePriceOverrideDeactivation(id, request, userId, locale);
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ResponseDto<String>> deletePriceOverride(
            @PathVariable UUID id,
            @RequestHeader("User-ID") String userId,
            @RequestHeader(value = "locale", defaultValue = "en") String locale) {
        
        log.info("Received delete price override request for ID: {}, user: {} with locale: {}", id, userId, locale);
        ResponseDto<String> response = priceOverrideService.deletePriceOverride(id, userId, locale);
        return ResponseEntity.ok(response);
    }

    /**
     * Retrieves items impacted by a price override for a specific restaurant.
     * Returns a paginated and filterable list of items that are affected by the price override.
     *
     * @param id          the UUID of the price override to get impacted items for
     * @param restaurantId the UUID of the restaurant (required)
     * @param page        optional page number for pagination
     * @param size        optional page size for pagination
     * @param search      optional search term for text search
     * @param sortBy      field to sort by (default: "createdAt")
     * @param direction   sort direction (default: DESC)
     * @param locale      locale code for localized responses (default: "en")
     * @return response containing paginated list of items impacted by the price override
     */
    @GetMapping("/{id}/impacted-items")
    public ResponseEntity<ResponseDto<PriceOverrideImpactedItemListResponse>> getImpactedItemsByPriceOverride(
            @PathVariable UUID id,
            @RequestParam UUID restaurantId,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "DESC") Sort.Direction direction,
            @RequestHeader(value = "locale", defaultValue = "en") String locale) {
        
        log.info("Received get impacted items request for price override ID: {}, restaurant: {} with locale: {}", 
                 id, restaurantId, locale);
        ResponseDto<PriceOverrideImpactedItemListResponse> response = 
            priceOverrideService.getImpactedItemsByPriceOverride(id, restaurantId, page, size, search, sortBy, direction, locale);
        return ResponseEntity.ok(response);
    }
}

