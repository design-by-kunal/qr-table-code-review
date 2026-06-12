package com.gulfnet.shared_library.model.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Min;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TableCapacityRequest {
    
    @NotNull(message = "{table.capacity.newCapacity.required}")
    @Min(value = 1, message = "{table.capacity.newCapacity.min}")
    private Integer newCapacity;
    
    private String reason; // Optional reason for change
}
