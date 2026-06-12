package com.gulfnet.shared_library.model.request;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
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
public class UnassignCategoriesFromKdsRequest {
    
    @NotNull(message = "KDS ID is required")
    private UUID kdsId;
    
    @NotNull(message = "Category IDs list cannot be null")
    @NotEmpty(message = "At least one category ID is required")
    private List<UUID> categoryIds;
}

