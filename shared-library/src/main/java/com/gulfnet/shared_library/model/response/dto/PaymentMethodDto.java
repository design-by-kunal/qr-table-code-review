package com.gulfnet.shared_library.model.response.dto;

import lombok.*;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentMethodDto {
    private String type;
    private String logoUrl;
    private List<PaymentMethodTranslationDto> translations;
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PaymentMethodTranslationDto {
        private String languageCode;
        private String name;
    }
}
