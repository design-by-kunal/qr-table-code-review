package com.gulfnet.shared_library.model.response.dto;

import com.gulfnet.shared_library.enums.ComboType;
import com.gulfnet.shared_library.enums.DayOfWeek;
import com.gulfnet.shared_library.enums.EntityStatus;
import com.gulfnet.shared_library.enums.ItemOrderType;
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
public class ComboResponse {
    private UUID comboId;
    private UUID menuId;
    private ComboType type;
    private BigDecimal basePrice;
    private String comboImageUrl;
    private EntityStatus status;
    private ItemOrderType itemOrderType;
    private Boolean isAvailable; // availability flag for the combo
    private OffsetDateTime validFrom;
    private OffsetDateTime validTo;
    private OffsetTime startTime;
    private OffsetTime endTime;
    private List<DayOfWeek> daysOfWeek;
    private List<ComboGroupResponse> comboGroups;
    private LocalDateTime createdAt;
    private String createdBy;
    private LocalDateTime updatedAt;
    private String updatedBy;
    private List<ComboTranslationDto> translations;
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ComboGroupResponse {
        private UUID comboGroupId;
        private com.gulfnet.shared_library.enums.ComboGroupType groupType;
        private Integer minSelect;
        private Integer maxSelect;
        private List<ComboItemResponse> items;
        private List<ComboGroupTranslationDto> translations;
        
        @Data
        @Builder
        @NoArgsConstructor
        @AllArgsConstructor
        public static class ComboItemResponse {
            private UUID itemId;
            private List<UUID> modifierItemId; // Can be null, empty, single item, or multiple items
            private List<ModifierItemInfo> modifierItems; // Detailed modifier information
            
            // Manual getter/setter methods for new fields
            public List<UUID> getModifierItemId() {
                return modifierItemId;
            }
            
            public void setModifierItemId(List<UUID> modifierItemId) {
                this.modifierItemId = modifierItemId;
            }
            
            public List<ModifierItemInfo> getModifierItems() {
                return modifierItems;
            }
            
            public void setModifierItems(List<ModifierItemInfo> modifierItems) {
                this.modifierItems = modifierItems;
            }
            
            @Data
            @Builder
            @NoArgsConstructor
            @AllArgsConstructor
            public static class ModifierItemInfo {
                private UUID modifierItemId;
                private String modifierItemName;
                private String modifierItemDescription;
                private java.math.BigDecimal modifierItemPrice;
            }
        }
    }
}

