package com.gulfnet.shared_library.model.response.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.gulfnet.shared_library.enums.EntityStatus;
import lombok.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CategoryResponse {
    
    private CategoryData category;
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CategoryData {
        private UUID id;
        
        private UUID menuStructureId;
        
        private UUID parentCategoryId;

        private String parentCategoryName;  
        
        private EntityStatus status;
        
        // Name in the requested language (based on Accept-Language header)
        private String name;
        
        // Additional fields with user names
        private String createdBy;
        private LocalDateTime createdAt;
        @JsonInclude(JsonInclude.Include.ALWAYS)
        private String updatedBy;
        @JsonInclude(JsonInclude.Include.ALWAYS)
        private LocalDateTime updatedAt;
        
        private Integer displayOrder;
        
        private Boolean isCombo;
        
        private List<CategoryTranslationResponse> translations;
        private List<ItemResponse> items;

        // MenuCategoryMapping ID - needed for KDS category selection
        private UUID menuCategoryMappingId;

    }
} 