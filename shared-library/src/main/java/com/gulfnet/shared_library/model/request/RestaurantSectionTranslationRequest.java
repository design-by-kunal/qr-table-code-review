package com.gulfnet.shared_library.model.request;

import jakarta.validation.constraints.NotNull;
import lombok.*;

@AllArgsConstructor
@Getter
@Setter
@NoArgsConstructor
@Builder
public class RestaurantSectionTranslationRequest {

    @NotNull
    private String languageCode;

    @NotNull
    private String name;
}
