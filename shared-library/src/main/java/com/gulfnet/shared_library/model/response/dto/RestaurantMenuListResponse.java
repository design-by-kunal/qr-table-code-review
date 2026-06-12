package com.gulfnet.shared_library.model.response.dto;

import lombok.*;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RestaurantMenuListResponse {
    private List<RestaurantMenuResponse> restaurants;
    private Long count;
    private Long total;
    private PaginationMetaData metaData;
} 