package com.gulfnet.shared_library.model.request;

import jakarta.validation.constraints.NotEmpty;
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
public class UnassignUserFromKdsRequest {

    @NotNull(message = "{kds.configuration.kds.id.required}")
    private UUID kdsId;

    @NotEmpty(message = "{user.id.required}")
    private List<UUID> userIds;
}

