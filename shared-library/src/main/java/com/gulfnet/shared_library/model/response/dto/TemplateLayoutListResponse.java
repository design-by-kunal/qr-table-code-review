package com.gulfnet.shared_library.model.response.dto;

import com.gulfnet.shared_library.enums.EntityStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TemplateLayoutListResponse {
    private UUID id;
    private EntityStatus status;
    private String name;
    private String languageCode;
    private Integer totalSeatingCapacity;
    private Integer sectionCount;
    private LocalDateTime createdAt;
    private String createdByName;
}
