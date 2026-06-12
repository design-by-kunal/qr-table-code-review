package com.gulfnet.shared_library.model.response.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import com.gulfnet.shared_library.enums.EntityStatus;
import com.gulfnet.shared_library.enums.DietaryPreference;
import com.gulfnet.shared_library.enums.AlcoholType;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ItemResponseWithModifierEnhanced {
    private UUID id;

    private String itemCode;
    
    private Double basePrice;
    
    // Enhanced pricing information
    private BigDecimal discountedPrice;
    private BigDecimal discountAmount;
    private String discountDetail; // e.g., "20% off", "Flat ₹10 off"
    
    // BXGY discount information
    @JsonProperty("isBxgyBuyItem")
    private Boolean isBxgyBuyItem;
    private Integer buyQuantity;
    private Integer getQuantity;
    private UUID discountId;
    
    // Availability information
    private Boolean isAvailable;
    
    private String imageUrl;
    
    private Boolean outOfStock;
    
    private EntityStatus status;

    private DietaryPreference dietaryPreference;
    
    private AlcoholType alcoholType;
    
    private Boolean isDeleted;
    
    private String createdBy;
    
    private String updatedBy;

    private Boolean hasModifierAssigned;

    private LocalDateTime createdAt;
    
    private LocalDateTime updatedAt;
    
    private List<ItemTranslationDto> translations;
    private List<ModifierGroupWithItemsResponse> modifierDetails;
    
    private Boolean allowCookingRequest;
}