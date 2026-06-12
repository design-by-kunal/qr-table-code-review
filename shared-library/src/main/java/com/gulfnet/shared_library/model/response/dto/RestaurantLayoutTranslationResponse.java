package com.gulfnet.shared_library.model.response.dto;

import lombok.*;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@ToString
public class RestaurantLayoutTranslationResponse {

    private String languageCode;

    private String name;
}
