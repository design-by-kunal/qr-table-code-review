package com.gulfnet.shared_library.model.response.dto;

import com.gulfnet.shared_library.enums.PaymentGatewayCode;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SystemPaymentSettingResponse {
    
    private UUID id;
    private PaymentGatewayCode gatewayCode;
    private Boolean isEnabled;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private UUID createdBy;
    private UUID updatedBy;
}
