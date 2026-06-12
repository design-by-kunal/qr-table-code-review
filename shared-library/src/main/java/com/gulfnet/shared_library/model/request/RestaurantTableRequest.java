package com.gulfnet.shared_library.model.request;

import com.gulfnet.shared_library.enums.TableShape;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.util.UUID;

@AllArgsConstructor
@Getter
@Setter
@NoArgsConstructor
@Builder
public class RestaurantTableRequest {

    private UUID id;

    @NotNull
    private Integer tableOrder;

    @NotNull
    private TableShape shape;

    @NotNull
    private Integer capacity;

    @NotBlank(message = "Table code is required and cannot be empty")
    private String tableCode;
}
