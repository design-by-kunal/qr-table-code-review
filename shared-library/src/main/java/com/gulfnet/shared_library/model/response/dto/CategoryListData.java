package com.gulfnet.shared_library.model.response.dto;

import com.gulfnet.shared_library.enums.EntityStatus;
import lombok.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CategoryListData {
    private UUID id;
    
    private EntityStatus status;
    
    // Name in the requested language (based on Accept-Language header)
    private String name;
    
    // Additional fields with user names
    private String createdBy;
    private LocalDateTime createdAt;
    private String updatedBy;
    private LocalDateTime updatedAt;
    private Long subcategoryCount; 
    
    private Integer displayOrder;
    
    private List<CategoryTranslationResponse> translations;
    
    // MenuCategoryMapping ID - needed for KDS category selection
    private UUID menuCategoryMappingId;
    
    // Combo ID - if this is a combo, this field will contain the combo ID
    // When comboId is present, this CategoryListData represents a combo that can be selected as a category
    private UUID comboId;
    
    // Flag indicating if this category is a combo category
    private Boolean isCombo;
} 