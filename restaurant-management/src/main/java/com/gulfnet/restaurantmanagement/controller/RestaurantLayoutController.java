package com.gulfnet.restaurantmanagement.controller;

import com.gulfnet.restaurantmanagement.service.RestaurantLayoutService;
import com.gulfnet.shared_library.model.request.RestaurantLayoutRequestDto;
import com.gulfnet.shared_library.model.response.dto.RestaurantLayoutResponseDto;
import com.gulfnet.shared_library.model.response.dto.ResponseDto;
import com.gulfnet.shared_library.model.response.dto.RestaurantLayoutStructureDto;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/v1/restaurants/{restaurantId}/structure")
@RequiredArgsConstructor
public class RestaurantLayoutController {

    private final RestaurantLayoutService restaurantLayoutService;

    /**
     * Creates the restaurant floor-plan structure (sections, rows, tables) under
     * {@code /api/v1/restaurants/{restaurantId}/structure}. Optionally seeds from a template when
     * {@code templateId} is provided; delegates to {@link RestaurantLayoutService#createRestaurantStructure}.
     *
     * @param restaurantId restaurant owning the layout
     * @param templateId   optional template layout to copy or align from
     * @param requestDto   structure payload
     * @param creatorId    {@code User-ID} header identifying the actor
     * @return {@link ResponseEntity} with the created structure in a {@link ResponseDto}
     */
    @PostMapping
    public ResponseEntity<ResponseDto<RestaurantLayoutStructureDto<RestaurantLayoutResponseDto>>> createStructure(
            @PathVariable UUID restaurantId,
            @RequestParam(required = false) UUID templateId,
            @Valid @RequestBody RestaurantLayoutRequestDto requestDto,
            @RequestHeader("User-ID") String creatorId) {

        log.info("Request to create structure for restaurantId: {}, templateId: {} by user: {}", restaurantId, templateId, creatorId);

        ResponseDto<RestaurantLayoutStructureDto<RestaurantLayoutResponseDto>> response =
                restaurantLayoutService.createRestaurantStructure(restaurantId, templateId, requestDto, creatorId);

        return ResponseEntity.ok(response);
    }

    @GetMapping
    public ResponseDto<RestaurantLayoutStructureDto<RestaurantLayoutResponseDto>> getRestaurantStructure(
            @PathVariable UUID restaurantId) {
        return restaurantLayoutService.getRestaurantStructure(restaurantId);
    }

    /**
     * Replaces or updates the restaurant floor-plan structure for {@code restaurantId}. Optional
     * {@code templateId} can drive how the update is applied; delegates to
     * {@link RestaurantLayoutService#updateRestaurantStructure}.
     *
     * @param restaurantId restaurant owning the layout
     * @param templateId   optional template reference
     * @param requestDto   updated structure payload
     * @param updaterId    {@code User-ID} header identifying the actor
     * @return {@link ResponseEntity} with the updated structure in a {@link ResponseDto}
     */
    @PutMapping
    public ResponseEntity<ResponseDto<RestaurantLayoutStructureDto<RestaurantLayoutResponseDto>>> updateStructure(
            @PathVariable UUID restaurantId,
            @RequestParam(required = false) UUID templateId,
            @Valid @RequestBody RestaurantLayoutRequestDto requestDto,
            @RequestHeader("User-ID") String updaterId) {

        log.info("Request to update structure for restaurantId: {} by user: {}", restaurantId, updaterId);

        ResponseDto<RestaurantLayoutStructureDto<RestaurantLayoutResponseDto>> response =
                restaurantLayoutService.updateRestaurantStructure(restaurantId, templateId, requestDto, updaterId);

        return ResponseEntity.ok(response);
    }

}
