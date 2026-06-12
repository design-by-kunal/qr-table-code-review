package com.gulfnet.shared_library.model.response.dto;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor

public class RestaurantDto<T> {
    private T restaurant;   
}
