package com.gulfnet.restaurantmanagement.service;

import com.gulfnet.shared_library.model.request.RestaurantLayoutRequestDto;
import com.gulfnet.shared_library.model.response.dto.RestaurantLayoutStructureDto;
import com.gulfnet.shared_library.model.response.dto.RestaurantLayoutResponseDto;
import com.gulfnet.shared_library.model.response.dto.ResponseDto;

import java.util.UUID;

public interface RestaurantLayoutService {

    ResponseDto<RestaurantLayoutStructureDto<RestaurantLayoutResponseDto>> createRestaurantStructure(
                UUID restaurantId,
                UUID templateId,                                     // optional, can be null
                RestaurantLayoutRequestDto requestDto,
                String creatorId);

    ResponseDto<RestaurantLayoutStructureDto<RestaurantLayoutResponseDto>> getRestaurantStructure(UUID restaurantId);
    ResponseDto<RestaurantLayoutStructureDto<RestaurantLayoutResponseDto>> updateRestaurantStructure(
        UUID restaurantId,
        UUID templateId,   
        RestaurantLayoutRequestDto requestDto,
        String updaterId);

}
