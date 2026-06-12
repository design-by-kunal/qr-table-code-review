package com.gulfnet.shared_library.model.request;

import com.gulfnet.shared_library.enums.PromotionType;
import com.gulfnet.shared_library.enums.EntityStatus;
import com.gulfnet.shared_library.model.request.PromotionTranslationRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PromotionRequest {

    @NotNull(message = "{promotion.error.type.required}")
    private String type; // Accept any string, validate manually in service

    private String imageUrl;

    @NotEmpty(message = "{promotion.error.translation.required}")
    @Valid
    private List<PromotionTranslationRequest> translations;

    private UUID discountId;

    @NotNull(message = "{promotion.error.status.required}")
    private String status;
}