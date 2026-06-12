package com.gulfnet.shared_library.model.response.dto;

import lombok.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RestaurantSectionTranslationResponse {

    private String languageCode;

    private String name;
}
