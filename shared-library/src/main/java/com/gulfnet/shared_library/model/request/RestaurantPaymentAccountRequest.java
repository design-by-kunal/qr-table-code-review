package com.gulfnet.shared_library.model.request;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RestaurantPaymentAccountRequest {

    private UUID restaurantId;

    /**
     * Logical payment type, e.g. "paypay", "promptpay", "paynow".
     */
    private String paymentType;

    private String publicKey;

    private String secretKey;
}

