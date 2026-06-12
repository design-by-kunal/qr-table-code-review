package com.gulfnet.shared_library.model.response.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.gulfnet.shared_library.serializer.BigDecimalSerializer;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PerformanceResponse {
    private SalesByServerReport salesByServerReport;
    private CustomerRatingDistribution customerRatingDistribution;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SalesByServerReport {
        private List<SalesByServerItem> servers;
        private Long count;
        private Long total;
        private PaginationMetaData metaData;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SalesByServerItem {
        private String serverName;
        private String serverCode;
        private Long totalOrders;
        @JsonSerialize(using = BigDecimalSerializer.class)
        private BigDecimal totalSales;
        @JsonSerialize(using = BigDecimalSerializer.class)
        private BigDecimal averageOrderValue;
        private Long totalTablesServed;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CustomerRatingDistribution {
        private List<RatingDistributionItem> distribution;
        private PaginationMetaData metaData;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RatingDistributionItem {
        private Integer rating; // 1-5
        private Long count;
        private Double percentage;
    }
}

