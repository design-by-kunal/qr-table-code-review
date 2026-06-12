package com.gulfnet.shared_library.model.request;

import com.gulfnet.shared_library.enums.EntityStatus;
import com.gulfnet.shared_library.model.response.dto.KdsTranslationDto;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.*;

import java.util.List;
import java.util.UUID;

@AllArgsConstructor
@Getter
@Setter
@ToString
@Builder
@NoArgsConstructor
public class KdsRequest {

    @NotNull(message = "{kds.status.required}")
    private EntityStatus status;

    @Builder.Default
    private Boolean isDeleted = false;

    @Builder.Default
    private Boolean isDefault = false;

    @NotNull(message = "{kds.translations.required}")
    @Size(min = 1, message = "{kds.translations.min.one}")
    private List<KdsTranslationDto> translations;

    // Optional: Menu Category Mapping IDs to assign during create or update
    // These are restaurant-menu-specific category mappings, not master category IDs
    private List<UUID> menuCategoryMappingIds;
    
    // Optional: Master Category IDs to assign during create or update
    // These will be converted to menuCategoryMappingIds based on restaurant's menus
    private List<UUID> categoryIds;
    
    // Optional: Combo IDs to assign during create or update
    // Combos are treated as categories for KDS assignment
    private List<UUID> comboIds;

    public List<KdsTranslationDto> getTranslations() {
        return translations;
    }
}

