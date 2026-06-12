package com.gulfnet.shared_library.model.request;

import com.gulfnet.shared_library.enums.PaymentGatewayCode;
import jakarta.validation.constraints.NotNull;
import lombok.*;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SystemPaymentSettingRequest {

    @NotNull(message = "{system.payment.setting.gateway.code.required}")
    private PaymentGatewayCode gatewayCode;

    private Boolean isEnabled;
}
