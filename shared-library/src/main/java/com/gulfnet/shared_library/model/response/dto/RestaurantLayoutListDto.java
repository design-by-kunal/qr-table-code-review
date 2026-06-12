package com.gulfnet.shared_library.model.response.dto;

import lombok.*;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RestaurantLayoutListDto {

    private List<RestaurantLayoutListResponse> restaurantLayouts;

    private Long count;

    private Long total;

    private PaginationMetaData metaData;

    private List<ErrorDto> errors;
}
