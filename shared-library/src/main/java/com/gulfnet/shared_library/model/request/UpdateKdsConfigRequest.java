package com.gulfnet.shared_library.model.request;

import jakarta.validation.constraints.NotBlank;
import lombok.*;

@AllArgsConstructor
@Getter
@Setter
@ToString
@Builder
@NoArgsConstructor
public class UpdateKdsConfigRequest {

    @NotBlank(message = "{kds.device.code.required}")
    private String deviceCode;
}

