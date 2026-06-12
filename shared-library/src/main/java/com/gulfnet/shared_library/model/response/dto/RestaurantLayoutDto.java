package com.gulfnet.shared_library.model.response.dto;

import lombok.*;

@AllArgsConstructor
@NoArgsConstructor
@Builder
@Data
@ToString
public class RestaurantLayoutDto<T> {
    private T restaurantLayout;
}
