package com.gulfnet.shared_library.model.response.dto;

import com.gulfnet.shared_library.enums.EntityStatus;
import lombok.*;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RestaurantMenuResponse {
    private String uuid;
    private String logoUrl;
    private EntityStatus status;
    private String name; // Localized name based on requested locale
} 