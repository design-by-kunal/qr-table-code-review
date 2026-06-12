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
public class ReportsOverviewResponse {
    private DailySalesSummary dailySalesSummary;
    private List<PaymentTypeBreakdown> paymentTypesBreakdown;
    private ItemizedSalesReport itemizedSalesReport;
    private TableWiseSalesReport tableWiseSalesReport;
    private DiscountsPromotionsReport discountsPromotionsReport;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DailySalesSummary {
        @JsonSerialize(using = BigDecimalSerializer.class)
        private BigDecimal totalSales;
        private Long totalOrders;
        private Long totalTablesServed;
        @JsonSerialize(using = BigDecimalSerializer.class)
        private BigDecimal avgOrderValue;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PaymentTypeBreakdown {
        private String paymentMethod;
        @JsonSerialize(using = BigDecimalSerializer.class)
        private BigDecimal totalSales;
        private Double percentage;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ItemizedSalesReport {
        private List<ItemizedSalesItem> items;
        private Long count;
        private Long total;
        private PaginationMetaData metaData;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ItemizedSalesItem {
        private String itemCode;
        private String itemName;
        private String category;
        private Integer quantitySold;
        @JsonSerialize(using = BigDecimalSerializer.class)
        private BigDecimal unitPrice;
        @JsonSerialize(using = BigDecimalSerializer.class)
        private BigDecimal totalSales;
        private Double percentageOfTotalSales;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TableWiseSalesReport {
        private List<TableWiseSalesItem> tables;
        private Long count;
        private Long total;
        private PaginationMetaData metaData;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TableWiseSalesItem {
        private String tableNo;
        private Long totalOrders;
        @JsonSerialize(using = BigDecimalSerializer.class)
        private BigDecimal totalSales;
        @JsonSerialize(using = BigDecimalSerializer.class)
        private BigDecimal averageOrderValue;
        @JsonSerialize(using = BigDecimalSerializer.class)
        private BigDecimal totalTax;
        @JsonSerialize(using = BigDecimalSerializer.class)
        private BigDecimal totalServiceCharge;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DiscountsPromotionsReport {
        private List<DiscountPromotionItem> discounts;
        private Long count;
        private Long total;
        private PaginationMetaData metaData;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DiscountPromotionItem {
        private String discountType; // Category: "Order", "Additional Discount", "Item", or "Category"
        private String discountCode; // Specific discount code (e.g., "SUMMER20", "VIP_OFFER")
        private String discountName; // Discount name from translation (fallback to discount code if not available)
        private Long numberOfTransactions;
        @JsonSerialize(using = BigDecimalSerializer.class)
        private BigDecimal totalDiscountApplied;
        @JsonSerialize(using = BigDecimalSerializer.class)
        private BigDecimal totalRevenue;
        @JsonSerialize(using = BigDecimalSerializer.class)
        private BigDecimal totalRevenueBeforeDiscount;
        @JsonSerialize(using = BigDecimalSerializer.class)
        private BigDecimal discountEfficiency;
        private String appliedTo;
    }
}

