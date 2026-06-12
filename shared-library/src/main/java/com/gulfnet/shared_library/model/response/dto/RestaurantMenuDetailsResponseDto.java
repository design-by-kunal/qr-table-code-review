package com.gulfnet.shared_library.model.response.dto;

import com.gulfnet.shared_library.enums.EntityStatus;
import com.gulfnet.shared_library.enums.RestaurantMenuMappingStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RestaurantMenuDetailsResponseDto {
    private UUID restaurantId;
    private String restaurantName;
    private String restaurantCode;
    private UUID restaurantGroupId;
    private String restaurantGroupName;
    private RestaurantMenuMappingStatus menuStatus;
    private EntityStatus restaurantStatus;
    private OffsetDateTime assignedAt;
    private String assignedBy;
    private OffsetDateTime schedulePublishTime;
}