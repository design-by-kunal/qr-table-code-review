package com.gulfnet.shared_library.model.request;

import jakarta.validation.constraints.NotNull;
import lombok.*;


@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DuplicateMenuRequest {
    
    @NotNull(message = "{menu.duplicate.flag.required}")
    private Boolean isDuplicate; // true = duplicate, false = version
}
