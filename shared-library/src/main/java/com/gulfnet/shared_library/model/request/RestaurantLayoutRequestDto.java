package com.gulfnet.shared_library.model.request;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RestaurantLayoutRequestDto {
        private List<RestaurantSectionRequest> sections;

}
