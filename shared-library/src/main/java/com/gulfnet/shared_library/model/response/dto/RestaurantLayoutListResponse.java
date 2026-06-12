package com.gulfnet.shared_library.model.response.dto;

import com.gulfnet.shared_library.enums.EntityStatus;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RestaurantLayoutListResponse {

    private UUID id;

    private EntityStatus status;

    private String name;

    private String languageCode;

    private Integer totalSeatingCapacity;

    private Integer sectionCount;

    private LocalDateTime createdAt;

    private String createdByName;

    private UUID templateLayoutId;
}
