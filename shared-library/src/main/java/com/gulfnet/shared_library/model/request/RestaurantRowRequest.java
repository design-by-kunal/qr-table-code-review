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
public class RestaurantRowRequest {

    private UUID id;

    @NotNull
    private Integer rowOrder;

    @NotNull
    private List<RestaurantTableRequest> tables;
}
