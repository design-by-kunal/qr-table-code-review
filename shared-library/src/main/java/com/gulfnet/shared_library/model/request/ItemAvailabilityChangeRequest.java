package com.gulfnet.shared_library.model.request;

import lombok.Data;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import jakarta.validation.constraints.NotNull;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ItemAvailabilityChangeRequest {
    
    @NotNull(message = "{item.availability.restaurantId.required}")
    private String restaurantId;
    
    @NotNull(message = "{item.availability.menuId.required}")
    private String menuId;
    
    @NotNull(message = "{item.availability.itemId.required}")
    private String itemId;
    
    @NotNull(message = "{item.availability.isAvailable.required}")
    private Boolean isAvailable;
    
}