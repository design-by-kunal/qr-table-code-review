package com.gulfnet.shared_library.model.request;

import com.gulfnet.shared_library.model.response.dto.ItemTranslationDto;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.DecimalMin;
import com.gulfnet.shared_library.enums.AlcoholType;
import com.gulfnet.shared_library.enums.EntityStatus;
import com.gulfnet.shared_library.enums.DietaryPreference;
import com.gulfnet.shared_library.enums.ItemOrderType;
import lombok.*;

import java.util.List;

@AllArgsConstructor
@Getter
@Setter
@ToString
@Builder
@NoArgsConstructor
public class ItemRequest {

    @NotBlank(message = "{item.itemCode.required}")
    @Size(min = 1, max = 64, message = "{item.itemCode.size}")
    @Pattern(regexp = "^[A-Za-z0-9][A-Za-z0-9_-]{0,63}$", message = "{item.itemCode.pattern}")
    private String itemCode;

    @NotNull(message = "{item.basePrice.null}")
    @DecimalMin(value = "0.0", message = "{item.basePrice.min}")
    private Double basePrice;

    private String imageUrl;

    @Builder.Default
    private Boolean outOfStock = false;

    @NotNull(message = "{item.status.required}")
    private EntityStatus status;

    private DietaryPreference dietaryPreference;

    private ItemOrderType itemOrderType;

    private AlcoholType alcoholType;

    @Builder.Default
    private Boolean isDeleted = false;
    
    @Builder.Default
     private Boolean hasModifierAssigned =  false;


    @NotNull(message = "{item.translations.required}")
    @Size(min = 1, message = "{item.translations.min.one}")
    private List<ItemTranslationDto> translations;

    public List<ItemTranslationDto> getTranslations() {
        return translations;
    }
}