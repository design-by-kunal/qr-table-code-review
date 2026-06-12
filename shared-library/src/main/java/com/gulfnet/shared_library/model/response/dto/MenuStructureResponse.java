package com.gulfnet.shared_library.model.response.dto;

import com.gulfnet.shared_library.enums.EntityStatus;
import lombok.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@ToString
public class MenuStructureResponse {

    private UUID id;

    private EntityStatus status;

    private Boolean isDeleted;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    private String createdBy;
    
    private String updatedBy;

    private List<MenuStructureTranslationDto> translations;
    
    private Long menuCount;
}