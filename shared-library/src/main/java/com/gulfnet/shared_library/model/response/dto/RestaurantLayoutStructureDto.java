package com.gulfnet.shared_library.model.response.dto;

import lombok.*;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@ToString
public class RestaurantLayoutStructureDto<T> {
    private T restaurantLayoutStructure;
}
