package com.gulfnet.shared_library.model.response.dto;

import com.gulfnet.shared_library.enums.EntityStatus;
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
public class KdsResponse {
    private UUID id;
    
    private EntityStatus status;
    
    private Boolean isDeleted;
    
    private Boolean isDefault;
    
    private String deviceCode;
    
    private String createdBy;
    
    private String updatedBy;
    
    private LocalDateTime createdAt;
    
    private LocalDateTime updatedAt;
    
    private List<KdsTranslationDto> translations;
    
    private List<CategoryListData> categories;
    
    private List<CategoryResponse.CategoryData> subCategories;
    
    private List<ComboResponse> combos;

    @Builder.Default
    private Long assignedCount = 0L;
    
    @Builder.Default
    private Boolean isDeviceLinked = false;
}

