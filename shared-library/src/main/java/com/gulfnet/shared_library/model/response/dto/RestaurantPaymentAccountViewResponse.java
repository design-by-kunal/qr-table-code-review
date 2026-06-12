package com.gulfnet.shared_library.model.response.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RestaurantPaymentAccountViewResponse {

    private UUID id;
    private UUID restaurantId;
    private String paymentType;
    private String publicKey;
    private String secretKey;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
