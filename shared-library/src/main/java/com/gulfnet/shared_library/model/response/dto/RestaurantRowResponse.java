package com.gulfnet.shared_library.model.response.dto;

import lombok.*;

import java.util.List;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RestaurantRowResponse {

    private UUID id;

    private Integer rowOrder;

    private List<RestaurantTableResponse> tables;
}
