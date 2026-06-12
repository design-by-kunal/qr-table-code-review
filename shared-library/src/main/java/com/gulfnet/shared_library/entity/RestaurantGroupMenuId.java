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
public class RestaurantGroupMenuId implements Serializable {
    private UUID restaurantGroupId;
    private UUID menuId;
}