package com.gulfnet.shared_library.model.request;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.util.List;
import java.util.UUID;

@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
@Builder
public class TableAssignmentRequest {


    @NotNull
    private UUID waiterId;

    @NotEmpty
    private List<UUID> restaurantTableId;

}
