package com.gulfnet.shared_library.entity;

import jakarta.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.io.Serializable;
import java.util.UUID;

@Embeddable
@Data
@NoArgsConstructor
@AllArgsConstructor
public class RestaurantDiscountId implements Serializable {
    private UUID restaurantId;
    private UUID discountId;
}

