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
public class StaffPerformanceResponse {
    private WaiterPerformanceReport waiterPerformanceReport;
    private CashierPerformanceReport cashierPerformanceReport;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class WaiterPerformanceReport {
        private List<WaiterPerformanceItem> waiters;
        private PaginationMetaData pagination;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class WaiterPerformanceItem {
        private String waiterName;
        private String waiterCode;
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
    public static class CashierPerformanceReport {
        private List<CashierPerformanceItem> cashiers;
        private PaginationMetaData pagination;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CashierPerformanceItem {
        private String cashierName;
        private String cashierCode;
        private Long totalTransactions;
        @JsonSerialize(using = BigDecimalSerializer.class)
        private BigDecimal totalAmount;
        @JsonSerialize(using = BigDecimalSerializer.class)
        private BigDecimal averageTransactionValue;
    }
}

