package com.gulfnet.shared_library.model.response.dto;

import com.gulfnet.shared_library.enums.AlcoholType;
import com.gulfnet.shared_library.enums.EntityStatus;
import com.gulfnet.shared_library.enums.DietaryPreference;
import com.gulfnet.shared_library.enums.ItemOrderType;

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
public class ItemResponse {
    private UUID id;

    private String itemCode;
    
    private Double basePrice;
    
    private String imageUrl;
    
    private Boolean outOfStock;
    
    private EntityStatus status;

    private DietaryPreference dietaryPreference;

    private ItemOrderType itemOrderType;

    private AlcoholType alcoholType;
    
    private Boolean isDeleted;
    
    private String createdBy;
    
    private String updatedBy;

    private Boolean hasModifierAssigned ;

    private Boolean isAvailable;
    
    private LocalDateTime createdAt;
    
    private LocalDateTime updatedAt;
    
    private List<ItemTranslationDto> translations;
    
    private Long menuCount;
}