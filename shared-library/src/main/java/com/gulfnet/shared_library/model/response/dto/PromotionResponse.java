package com.gulfnet.shared_library.model.response.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.gulfnet.shared_library.enums.PromotionType;
import com.gulfnet.shared_library.enums.EntityStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import com.gulfnet.shared_library.model.response.dto.ComboTranslationDto;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PromotionResponse {
    private UUID id;
    private PromotionType type;
    private String imageUrl;
    private List<PromotionTranslationResponse> translations;
    private UUID discountId;
    private EntityStatus status;
    private Boolean isDeleted;
    private Long assignedMenuCount;
    private LocalDateTime createdAt;
    private String createdBy;
    private LocalDateTime updatedAt;
    private String updatedBy;
    private Boolean discountApplied;
    private String discountType;
    private String discountAppliedTo;
    
    // Combo information (for COMBO type promotions)
    private UUID comboId;
    private List<ComboTranslationDto> comboTranslations;

    /**
     * Get the name from the first translation (for sorting purposes)
     * @return the name from the first translation, or empty string if no translations
     */
    @JsonIgnore
    public String getName() {
        if (translations != null && !translations.isEmpty()) {
            return translations.get(0).getName() != null ? translations.get(0).getName() : "";
        }
        return "";
    }
}