package com.gulfnet.shared_library.model.response.dto;

import lombok.*;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@ToString
public class PromotionTranslationResponse {

    private String languageCode;
    private String name;
    private String heading;
    private String description;
} 