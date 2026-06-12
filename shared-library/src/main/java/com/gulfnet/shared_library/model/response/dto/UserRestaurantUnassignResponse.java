package com.gulfnet.shared_library.model.response.dto;

import lombok.Builder;
import lombok.Data;

import java.util.UUID;

@Data
@Builder
public class UserRestaurantUnassignResponse {
    private UUID userId;
    private UUID restaurantId; 
}