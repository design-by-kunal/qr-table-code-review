package com.gulfnet.shared_library.model.response.dto;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.List;
import com.gulfnet.shared_library.enums.EntityStatus;
import com.gulfnet.shared_library.enums.DietaryPreference;

import java.time.LocalDateTime;
import java.util.UUID;
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ItemResponseWithModifier {
    private UUID id;

    private String itemCode;
    
    private Double basePrice;
    
    private String imageUrl;
    
    private Boolean outOfStock;
    
    private EntityStatus status;

    private DietaryPreference dietaryPreference;
    
    private Boolean isDeleted;
    
    private String createdBy;
    
    private String updatedBy;

    private Boolean hasModifierAssigned ;

    
    private LocalDateTime createdAt;
    
    private LocalDateTime updatedAt;
    
    private List<ItemTranslationDto> translations;
    private List<ModifierGroupWithItemsResponse> modifierDetails;
}
