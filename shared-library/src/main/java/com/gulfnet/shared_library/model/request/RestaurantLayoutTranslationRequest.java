package com.gulfnet.shared_library.model.request;

import jakarta.validation.constraints.NotNull;
import lombok.*;

@AllArgsConstructor
@Getter
@Setter
@NoArgsConstructor
@Builder
public class RestaurantLayoutTranslationRequest {

    @NotNull(message = "{restaurant.layout.translation.language.required}")
    private String languageCode;

    private String name;
}
