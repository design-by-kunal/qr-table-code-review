package com.gulfnet.shared_library.model.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.*;

import java.util.List;
import java.util.UUID;

@AllArgsConstructor
@Getter
@Setter
@NoArgsConstructor
@Builder
public class RestaurantSectionRequest {

    private UUID id;

    @NotNull
    private Integer sectionOrder;

    @NotNull
    private List<RestaurantSectionTranslationRequest> translations;

    @NotNull
    private List<RestaurantRowRequest> rows;
}
