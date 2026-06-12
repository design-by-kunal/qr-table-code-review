package com.gulfnet.shared_library.model.request;


import com.gulfnet.shared_library.enums.EntityStatus;
import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.util.List;
import java.util.UUID;

@AllArgsConstructor
@Getter
@Setter
@ToString
@Builder
@NoArgsConstructor
public class RestaurantLayoutRequest {

    @NotNull
    private EntityStatus status;

    private UUID templateLayoutId;

    @NotNull
    private List<RestaurantLayoutTranslationRequest> translations;

}
