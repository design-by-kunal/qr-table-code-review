package com.gulfnet.shared_library.model.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.*;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@ToString
public class PromotionTranslationRequest {

    @NotBlank(message = "{promotion.translation.language.blank}")
    @Size(max = 5, message = "{promotion.translation.language.length}")
    private String languageCode;

    @Size(max = 255, message = "{promotion.translation.name.length}")
    private String name;

    @Size(max = 500, message = "{promotion.translation.heading.length}")
    private String heading;

    @Size(max = 1000, message = "{promotion.translation.description.length}")
    private String description;
} 