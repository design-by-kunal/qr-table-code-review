package com.gulfnet.restaurantmanagement.service;

import com.gulfnet.shared_library.model.request.PromotionRequest;
import com.gulfnet.shared_library.model.response.dto.PromotionDto;
import com.gulfnet.shared_library.model.response.dto.PromotionListResponse;
import com.gulfnet.shared_library.model.response.dto.PromotionResponse;
import com.gulfnet.shared_library.model.response.dto.ResponseDto;
import com.gulfnet.shared_library.model.request.UpdateRestaurantPromotionValidityRequest;
import com.gulfnet.shared_library.model.request.MenuPromotionMappingRequest;
import com.gulfnet.shared_library.model.response.dto.MenuPromotionResponseDto;
import com.gulfnet.shared_library.model.response.dto.MenuPromotionListResponse;
import org.springframework.data.domain.Sort;

import java.util.List;
import java.util.UUID;


public interface PromotionService {
    ResponseDto<PromotionDto<PromotionResponse>> createPromotion(String userId, PromotionRequest request, String locale);
    
    ResponseDto<PromotionListResponse> getPromotions(
            Integer page, 
            Integer size, 
            String status, 
            String type, 
            String search, 
            String sortBy, 
            Sort.Direction direction, 
            String locale,
            Boolean isDeleted);

    ResponseDto<PromotionDto<PromotionResponse>> getPromotionDetails(UUID id, UUID menuId, String locale);

    ResponseDto<PromotionDto<PromotionResponse>> updatePromotion(UUID id, PromotionRequest request, String userId, String locale);

    ResponseDto<String> deletePromotion(UUID id, String userId);

    ResponseDto<MenuPromotionResponseDto> assignPromotionToMenu(MenuPromotionMappingRequest request, String userId, String locale);

    ResponseDto<MenuPromotionListResponse> getMenuAssignedPromotions(
        UUID menuId,
        UUID restaurantId,     // optional
        Integer page,
        Integer size,
        String search,
        Boolean isAvailable,
        String sortBy,
        Sort.Direction direction,
        String locale
    );
    ResponseDto<MenuPromotionListResponse> getRestaurantAssignedPromotions(
            UUID restaurantId,
            Integer page,
            Integer size,
            String search,
            String status,
            String sortBy,
            Sort.Direction direction,
            String locale);

    /**
     * View promotion details for a specific restaurant and promotion.
     * Validity (validFrom/validTo) is taken from RestaurantPromotionMapping,
     * similar to restaurant discount view API.
     */
    ResponseDto<MenuPromotionResponseDto> getRestaurantPromotionDetails(
            UUID restaurantId,
            UUID promotionId,
            UUID comboId,
            String locale);

    /**
     * Update promotion validity for a specific restaurant.
     * Updates validFrom and validTo in RestaurantPromotionMapping.
     */
    ResponseDto<MenuPromotionResponseDto> updateRestaurantPromotionValidity(
            UUID restaurantId,
            UUID promotionId,
            UpdateRestaurantPromotionValidityRequest request,
            String locale);

    ResponseDto<String> deleteMenuPromotionAssignment(UUID menuId, UUID promotionId, String locale);

    ResponseDto<MenuPromotionResponseDto> updateMenuPromotionAssignment(MenuPromotionMappingRequest request, String locale);

    ResponseDto<Void> restorePromotions(List<UUID> ids, String userId);
}