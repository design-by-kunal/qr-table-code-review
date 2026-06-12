package com.gulfnet.restaurantmanagement.service;

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

import java.util.UUID;

import java.util.List;

public interface PriceOverrideService {
    
    ResponseDto<PriceOverrideResponse> createPriceOverride(PriceOverrideRequest request, String userId, String locale);
    
    ResponseDto<PriceOverrideResponse> updatePriceOverride(UUID id, PriceOverrideRequest request, String userId, String locale);
    
    ResponseDto<PriceOverrideResponse> getPriceOverrideById(UUID id, String locale);
    
    ResponseDto<PriceOverrideListResponse> getPriceOverridesByRestaurant(
            UUID restaurantId, 
            Integer page, 
            Integer size, 
            String search, 
            OverrideLevel overrideLevel,
            PriceOverrideStatus status,
            String sortBy, 
            Sort.Direction direction, 
            String locale);
    
    ResponseDto<PriceOverrideResponse> updatePriceOverrideSchedule(UUID id, UpdatePriceOverrideScheduleRequest request, String userId, String locale);
    
    ResponseDto<PriceOverrideResponse> schedulePriceOverrideDeactivation(UUID id, SchedulePriceOverrideDeactivationRequest request, String userId, String locale);
    
    ResponseDto<String> deletePriceOverride(UUID id, String userId, String locale);
    
    ResponseDto<PriceOverrideImpactedItemListResponse> getImpactedItemsByPriceOverride(
            UUID priceOverrideId, UUID restaurantId, Integer page, Integer size, 
            String search, String sortBy, Sort.Direction direction, String locale);
}

