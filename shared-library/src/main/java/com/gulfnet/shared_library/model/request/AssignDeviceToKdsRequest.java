package com.gulfnet.shared_library.model.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.util.UUID;

@AllArgsConstructor
@Getter
@Setter
@ToString
@Builder
@NoArgsConstructor
public class AssignDeviceToKdsRequest {

    @NotBlank(message = "{kds.device.code.required}")
    private String deviceCode;

    @NotNull(message = "{kds.configuration.kds.id.required}")
    private UUID kdsId;
}

