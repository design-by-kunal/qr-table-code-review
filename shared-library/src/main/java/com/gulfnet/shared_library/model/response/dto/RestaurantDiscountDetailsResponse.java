package com.gulfnet.shared_library.model.response.dto;

import com.gulfnet.shared_library.enums.AppliedTo;
import com.gulfnet.shared_library.enums.DayOfWeek;
import com.gulfnet.shared_library.enums.DiscountType;
import com.gulfnet.shared_library.enums.EntityStatus;
import com.gulfnet.shared_library.enums.AlcoholType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.OffsetTime;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RestaurantDiscountDetailsResponse {
    // Discount basic information
    private UUID discountId;
    private String discountCode;
    private DiscountType discountType;
    private AppliedTo appliedTo;
    private BigDecimal value;
    private BigDecimal orderValueThreshold;
    private BigDecimal maxDiscountValue;
    private Integer buyQuantity;
    private Integer getQuantity;
    private Integer maxUses;
    private Integer currentUsage;
    private EntityStatus status;
    private Boolean isDeleted;
    private LocalDateTime createdAt;
    private String createdBy;
    private LocalDateTime updatedAt;
    private String updatedBy;
    private List<DiscountTranslationResponse> translations;
    
    // Restaurant-specific validity (from RestaurantDiscountMapping)
    private OffsetDateTime validFrom;
    private OffsetDateTime validTo;
    private OffsetTime startTime;
    private OffsetTime endTime;
    private List<DayOfWeek> daysOfWeek;
    private Boolean isHide;

    // Category-level discount details (only if appliedTo = CATEGORY)
    private List<CategoryInfo> categories;
    
    // Item-level discount details (only if appliedTo = ITEM and not BXGY)
    private List<ItemInfo> items;
    
    // BXGY discount details (only if discountType = BXGY)
    private List<BxgyItemInfo> buyItems;  // Buy items
    private List<BxgyItemInfo> getItems;  // Get items
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CategoryInfo {
        private UUID id;
        private String name;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ItemInfo {
        private UUID id;
        private String name;
        private String categoryName;
        private BigDecimal basePrice;
        private String imageUrl;
        private AlcoholType alcoholType;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class BxgyItemInfo {
        private UUID id;
        private String name;
        private BigDecimal basePrice;
        private String imageUrl;
        private AlcoholType alcoholType;
    }
}

