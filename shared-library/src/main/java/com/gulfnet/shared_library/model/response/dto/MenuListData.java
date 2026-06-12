package com.gulfnet.shared_library.model.response.dto;

import com.gulfnet.shared_library.enums.MenuStatus;
import lombok.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@AllArgsConstructor
@Getter
@Setter
@ToString
@Builder
@NoArgsConstructor
public class MenuListData {
    private UUID id;
    private UUID menuMasterId;       // Common ID for multiple versions of the same menu
    private MenuStatus status;
    private UUID menuStructureId;
    private Double version;
    private String createdBy;
    private LocalDateTime createdAt;
    private String updatedBy;
    private LocalDateTime updatedAt;
    private List<MenuListTranslationDto> translations;
    private Boolean isDeleted;
    private Long restaurantGroupCount;
    private Long restaurantCount;
}