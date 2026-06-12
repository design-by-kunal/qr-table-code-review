package com.gulfnet.shared_library.model.response.dto;

import com.gulfnet.shared_library.enums.OverrideLevel;
import com.gulfnet.shared_library.enums.OverrideType;
import com.gulfnet.shared_library.enums.PriceOverrideStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PriceOverrideImpactedItemListResponse {
    // Price Override Basic Details (shared across all items)
    private UUID priceOverrideId;
    private OverrideLevel overrideLevel;
    private OverrideType overrideType;
    private BigDecimal overrideValue;
    private PriceOverrideStatus priceOverrideStatus;
    private OffsetDateTime validFrom;
    private OffsetDateTime validTo;
    private List<PriceOverrideResponse.TranslationResponse> priceOverrideTranslations;
    
    // Item List
    private List<PriceOverrideImpactedItemResponse> items;
    private Long count;
    private Long total;
    private PaginationMetaData metaData;
}

