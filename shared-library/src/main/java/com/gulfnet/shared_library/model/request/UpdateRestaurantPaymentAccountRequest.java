package com.gulfnet.shared_library.model.request;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateRestaurantPaymentAccountRequest {

    private String publicKey;

    private String secretKey;
}
