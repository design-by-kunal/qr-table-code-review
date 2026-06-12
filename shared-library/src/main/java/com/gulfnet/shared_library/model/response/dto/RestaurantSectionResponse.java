package com.gulfnet.shared_library.model.response.dto;

import lombok.*;

import java.util.List;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RestaurantSectionResponse {

    private UUID id;

    private Integer sectionOrder;

    private List<RestaurantSectionTranslationResponse> translations;

    private List<RestaurantRowResponse> rows;
}
