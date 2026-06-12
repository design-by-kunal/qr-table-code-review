package com.gulfnet.shared_library.model.response.dto;

import com.gulfnet.shared_library.enums.OverrideLevel;
import com.gulfnet.shared_library.enums.OverrideType;
import com.gulfnet.shared_library.enums.PriceOverrideStatus;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PriceOverrideResponse {
    
    private UUID id;
    private OverrideLevel overrideLevel;
    private OverrideType overrideType;
    private BigDecimal overrideValue;
    private PriceOverrideStatus status;
    private LocalDateTime createdAt;
    private String createdBy;
    private LocalDateTime updatedAt;
    private String updatedBy;
    private List<TranslationResponse> translations;
    private List<MappingResponse> mappings;
    
    // Schedule fields
    private OffsetDateTime validFrom;
    private OffsetDateTime validTo;
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TranslationResponse {
        private String languageCode;
        private String name;
        private String reason;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MappingResponse {
        private RestaurantInfo restaurant;
        private MenuInfo menu;
        private List<CategoryInfo> categories;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RestaurantInfo {
        private UUID id;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MenuInfo {
        private UUID id;
        private String name;
        private List<TranslationResponse> translations;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CategoryInfo {
        private UUID id;
        private UUID parentCategoryId;
        private String name;
        private List<TranslationResponse> translations;
        @Builder.Default
        private List<CategoryInfo> subCategories = new ArrayList<>();
    }
}

