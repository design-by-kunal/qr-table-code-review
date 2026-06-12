package com.gulfnet.shared_library.model.response.dto;

import com.gulfnet.shared_library.enums.ComboType;
import com.gulfnet.shared_library.enums.DayOfWeek;
import com.gulfnet.shared_library.enums.EntityStatus;
import com.gulfnet.shared_library.enums.DietaryPreference;
import com.gulfnet.shared_library.enums.ItemOrderType;
import com.gulfnet.shared_library.enums.AlcoholType;
import lombok.*;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.OffsetTime;
import java.util.List;
import java.util.UUID;
import java.math.BigDecimal;

@AllArgsConstructor
@Getter
@Setter
@ToString
@Builder
@NoArgsConstructor
public class ComboDetailsResponse {
    private UUID comboId;
    private UUID menuId;
    private UUID categoryId;
    private ComboType type;
    private BigDecimal basePrice;
    private String comboImageUrl;
    private EntityStatus status;
    private ItemOrderType itemOrderType;
    private AlcoholType alcoholType;
    private Boolean isAvailable; // availability flag for the combo
    private OffsetDateTime validFrom;
    private OffsetDateTime validTo;
    private OffsetTime startTime;
    private OffsetTime endTime;
    private List<DayOfWeek> daysOfWeek;
    private List<ComboGroupDetailsResponse> comboGroups;
    private LocalDateTime createdAt;
    private String createdBy;
    private LocalDateTime updatedAt;
    private String updatedBy;
    private List<ComboTranslationDto> translations;
    private Boolean allowCookingRequest;
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ComboGroupDetailsResponse {
        private UUID comboGroupId;
        private com.gulfnet.shared_library.enums.ComboGroupType groupType;
        private Integer minSelect;
        private Integer maxSelect;
        private List<ComboItemDetailsResponse> items;
        private List<ComboGroupTranslationDto> translations;
        
        @Data
        @Builder
        @NoArgsConstructor
        @AllArgsConstructor
        public static class ComboItemDetailsResponse {
            // Item details
            private UUID itemId;
            private Double itemBasePrice;
            private String itemImageUrl;
            private EntityStatus itemStatus;
            private DietaryPreference itemDietaryPreference;
            private AlcoholType itemAlcoholType;
            private Boolean itemHasModifierAssigned;
            private Boolean defaultItem; // whether this item is default in its group
            private Boolean isAvailable; // availability flag for the item
            private List<ItemTranslationDto> itemTranslations;
            
            // Modifier details
            private List<UUID> modifierItemId; // Can be null, empty, single item, or multiple items
            private List<ModifierItemDetailsInfo> modifierItems; // Detailed modifier information
            
            // Manual getter/setter methods for new fields
            public List<UUID> getModifierItemId() {
                return modifierItemId;
            }
            
            public void setModifierItemId(List<UUID> modifierItemId) {
                this.modifierItemId = modifierItemId;
            }
            
            public List<ModifierItemDetailsInfo> getModifierItems() {
                return modifierItems;
            }
            
            public void setModifierItems(List<ModifierItemDetailsInfo> modifierItems) {
                this.modifierItems = modifierItems;
            }
            
            @Data
            @Builder
            @NoArgsConstructor
            @AllArgsConstructor
            public static class ModifierItemDetailsInfo {
                private UUID modifierItemId;
                private String modifierItemName;
                private String modifierItemDescription;
                private BigDecimal modifierItemPrice;
                private String modifierItemImageUrl;
                private EntityStatus modifierItemStatus;
                private List<ModifierItemTranslationDto> modifierItemTranslations;
            }
        }
    }
} 