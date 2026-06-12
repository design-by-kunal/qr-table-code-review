package com.gulfnet.shared_library.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentCredentials {
    private String publicKey;
    private String secretKey;
    private boolean isRestaurantSpecific;
}
