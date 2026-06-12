package com.gulfnet.shared_library.model.request;

import com.gulfnet.shared_library.enums.DayOfWeek;
import com.gulfnet.shared_library.model.response.dto.ComboTranslationDto;
import jakarta.validation.constraints.*;
import lombok.*;

import java.time.OffsetTime;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import com.gulfnet.shared_library.model.response.dto.ComboGroupTranslationDto;
import com.gulfnet.shared_library.enums.ItemOrderType;
import jakarta.validation.Valid;
import java.math.BigDecimal;

@AllArgsConstructor
@Getter
@Setter
@ToString
@Builder
@NoArgsConstructor
public class ComboRequest {
    
    
    @NotNull(message = "{combo.menuId.required}")
    private UUID menuId;
    
    private UUID categoryId; // Required: category ID to map combo to
    
    private String type;
    
    @NotNull(message = "{combo.basePrice.required}")
    private BigDecimal basePrice;
    
    private String comboImageUrl;
    
    private String status;
    
    private ItemOrderType itemOrderType;
    
    private OffsetDateTime validFrom;
    private OffsetDateTime validTo;
    private OffsetTime startTime; // UTC from frontend
    private OffsetTime endTime;   // UTC from frontend
    
    @NotEmpty(message = "{combo.daysOfWeek.required}")
    private List<DayOfWeek> daysOfWeek;
    
    @Valid
    @NotEmpty(message = "{combo.groups.required}")
    private List<ComboGroupRequest> comboGroups;
    
    @NotNull(message = "{combo.translations.required}")
    @Size(min = 1, message = "{combo.translations.min.one}")
    private List<ComboTranslationDto> translations;
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ComboGroupRequest {
        
        
        private String groupType;
        
        @Builder.Default
        private Integer minSelect = 1;
        
        @Builder.Default
        private Integer maxSelect = 1;
        
        @Valid
        @NotEmpty(message = "{combo.group.items.required}")
        private List<ComboItemRequest> items;
        @Valid
        @NotEmpty(message = "{combo.group.translations.required}")
        private List<ComboGroupTranslationDto> translations;
        
        @Data
        @Builder
        @NoArgsConstructor
        @AllArgsConstructor
        public static class ComboItemRequest {
            
            @NotNull(message = "{combo.group.item.itemId.required}")
            private UUID itemId;
            private List<UUID> modifierItemId; // Can be null, empty, single item, or multiple items
            
            @Builder.Default
            private Boolean defaultItem = false;
            
            // Manual getter/setter for modifierItemId to ensure it's available
            public List<UUID> getModifierItemId() {
                return modifierItemId;
            }
            
            public void setModifierItemId(List<UUID> modifierItemId) {
                this.modifierItemId = modifierItemId;
            }
        }
    }
}
