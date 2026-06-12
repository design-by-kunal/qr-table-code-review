package com.gulfnet.shared_library.model.response.dto;

import lombok.*;

import java.math.BigDecimal;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DiscountOfferReportListDto {
    private List<DiscountOfferReportResponse> discountOffers;
    private Long count;
    private Long total;
    private PaginationMetaData metaData;
    private List<ErrorDto> errors;
    
    // Summary statistics
    private SummaryStatistics summary;
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SummaryStatistics {
        private Long totalOrdersWithDiscounts;
        private BigDecimal totalDiscountAmount;
        private BigDecimal totalAdditionalDiscountAmount;
        private BigDecimal totalCombinedDiscountAmount;
        private Long ordersWithOrderLevelDiscount;
        private Long ordersWithAdditionalDiscount;
        private Long ordersWithBothDiscounts;
    }
}

