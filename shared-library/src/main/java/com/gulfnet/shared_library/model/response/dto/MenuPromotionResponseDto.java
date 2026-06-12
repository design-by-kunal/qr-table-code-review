package com.gulfnet.shared_library.model.response.dto;

import com.gulfnet.shared_library.enums.EntityStatus;
import com.gulfnet.shared_library.enums.PromotionType;
import com.gulfnet.shared_library.model.response.dto.ComboDetailsResponse;
import com.gulfnet.shared_library.model.response.dto.ComboDto;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MenuPromotionResponseDto {
    private List<PromotionTranslationResponse> translations;
    private OffsetDateTime validFrom;
    private OffsetDateTime validTo;
    private String imageUrl;
    private Boolean discountApplied;
    private String discountType;
    private String discountAppliedTo;
    private UUID discountId;
    private BigDecimal discountValue;
    private UUID promotionId;
    private EntityStatus status;
    private PromotionType type;

    // Combo information (for COMBO type promotions)
    private UUID comboId;
    // Full combo details (same structure as combo details API)
    private ComboDto<ComboDetailsResponse> comboDetails;

    // Explicit setter to satisfy tools that do not process Lombok
    public void setComboDetails(ComboDto<ComboDetailsResponse> comboDetails) {
        this.comboDetails = comboDetails;
    }
}
