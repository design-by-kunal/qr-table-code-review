package com.gulfnet.shared_library.model.response.dto;

import com.gulfnet.shared_library.enums.ChargeType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RestaurantChainConfigDto {
    private int itemQuantityLimit;
    private int maxItemsInCombo;
    private boolean allowComboPriceOverride;
    private boolean includePackingChargesForTakeaway;
    private PackingChargesForTakeaway packingChargesForTakeaway;
    private TaxSetup taxSetup;
    private ServiceChargesSetup serviceChargesSetup;
    private boolean allowRefunds;
    private boolean transactionsDataTransfer;
    private boolean kdsDataTransfer;
    private String kdsLiveDashboardResetTime;
    private String cashierLiveDashboardResetTime;
    private boolean allowOrderCancellation;
    private int upperLimitSections;
    private int upperLimitTables;
    private int upperLimitOrdersPerDay;
    private int upperLimitUsers;
    private int upperLimitRestaurantGroups;
    private int upperLimitRestaurants;
    private String liveDashboardsResetTime;
    private int upperLimitMenuCategoryLevels;
    private ImageDimensions itemImageDimensions;
    private ImageDimensions promotionImageDimensions;
    private boolean autoSaveDraft;
    private int autoSaveDraftTimeInSeconds;
    private boolean allowCombo;
    private boolean showOOSItemsByDefault;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PackingChargesForTakeaway {
        private double value;
        private ChargeType type;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TaxSetup {
        private DineInTax dineIn;
        private TakeAwayTax takeAway;
        
        @Data
        @Builder
        @NoArgsConstructor
        @AllArgsConstructor
        public static class DineInTax {
            private TaxCharge alcoholic;
            private TaxCharge nonAlcoholic;
        }
        
        @Data
        @Builder
        @NoArgsConstructor
        @AllArgsConstructor
        public static class TakeAwayTax {
            private TaxCharge alcoholic;
            private TaxCharge nonAlcoholic;
        }
        
        @Data
        @Builder
        @NoArgsConstructor
        @AllArgsConstructor
        public static class TaxCharge {
            private double value;
            private ChargeType type;
        }
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ServiceChargesSetup {
        private ServiceCharge dineIn;
        private ServiceCharge takeAway;
        
        @Data
        @Builder
        @NoArgsConstructor
        @AllArgsConstructor
        public static class ServiceCharge {
            private double value;
            private ChargeType type;
        }
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ImageDimensions {
        private int width;
        private int height;
    }
} 