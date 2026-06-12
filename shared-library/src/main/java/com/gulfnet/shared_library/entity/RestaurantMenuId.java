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
public class RestaurantMenuId implements Serializable {
    private UUID restaurantId;
    private UUID menuId;
}