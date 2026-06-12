package com.gulfnet.shared_library.model.response.dto;

import com.gulfnet.shared_library.enums.EntityStatus;
import lombok.*;

import java.util.List;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString
public class RestaurantLayoutResponse {

    private UUID id;

    private UUID restaurantId;

    private UUID templateLayoutId;

    private EntityStatus status;

    private List<RestaurantLayoutTranslationResponse> translations;
}
