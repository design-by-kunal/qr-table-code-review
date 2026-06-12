package com.gulfnet.shared_library.model.response.dto;

import com.gulfnet.shared_library.enums.MenuStatus;
import lombok.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MenuVersionsResponse {
    private UUID menuMasterId;
    private List<MenuVersionData> versions;
    private Long totalVersions;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MenuVersionData {
        private UUID id;
        private UUID menuMasterId;
        private UUID menuStructureId;
        private MenuStatus status;
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
}

