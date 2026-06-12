package com.gulfnet.shared_library.model.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AssignRestaurantsToGroupRequest {
    
    private UUID restaurantGroupId;

    @NotNull(message = "{restaurant.ids.required}")
    @Size(min = 1, message = "{restaurant.ids.not.empty}")
    private List<String> restaurantIds; // Changed from List<UUID> to List<String> to support "*" wildcard
}