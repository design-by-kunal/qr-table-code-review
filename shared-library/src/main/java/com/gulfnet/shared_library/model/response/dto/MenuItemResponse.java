package com.gulfnet.shared_library.model.response.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.gulfnet.shared_library.enums.EntityStatus;
import com.gulfnet.shared_library.enums.DietaryPreference;
import com.gulfnet.shared_library.enums.AlcoholType;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

@AllArgsConstructor
@Getter
@Setter
@ToString
@Builder
@NoArgsConstructor
public class MenuItemResponse {
    private UUID id;

    private String itemCode;
    
    private Double basePrice;
    
    private Double discountedPrice;
    
    private Boolean isBxgyBuyItem;
    
    private String discountDetail;  // Add this field for discount description
    
    @JsonInclude(JsonInclude.Include.ALWAYS)
    private Integer buyQuantity;  // Buy quantity for BXGY discount
    
    @JsonInclude(JsonInclude.Include.ALWAYS)
    private Integer getQuantity;  // Get quantity for BXGY discount
    
    @JsonInclude(JsonInclude.Include.ALWAYS)
    private UUID discountId;  // Discount ID for BXGY discount
    
    private String imageUrl;
    
    private Boolean outOfStock;
    
    private EntityStatus status;

    private DietaryPreference dietaryPreference;

    @JsonInclude(JsonInclude.Include.ALWAYS)
    private AlcoholType alcoholType;
    
    private Boolean isDeleted;
    
    private String createdBy;
    
    private String updatedBy;

    private Boolean hasModifierAssigned;
    
    private LocalDateTime createdAt;
    
    private LocalDateTime updatedAt;
    
    private Boolean isAvailable;
    
    private Boolean isCombo;
    
    private ItemTranslationDto translation;
} 