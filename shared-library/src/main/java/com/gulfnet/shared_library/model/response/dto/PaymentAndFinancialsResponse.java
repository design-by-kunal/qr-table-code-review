package com.gulfnet.shared_library.model.response.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.gulfnet.shared_library.serializer.BigDecimalSerializer;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentAndFinancialsResponse {
    private PaymentReconciliationReport paymentReconciliationReport;
    private CashDrawerReconciliationReport cashDrawerReconciliationReport;
    private CancellationReport cancellationReport;
    private ChargebackReport chargebackReport;
    private WastageReport wastageReport;
    private CashierShiftListResponse cashierShiftsReport;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PaymentReconciliationReport {
        private List<PaymentReconciliationItem> payments;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PaymentReconciliationItem {
        private String paymentMethod;
        private Long totalTransactions;
        @JsonSerialize(using = BigDecimalSerializer.class)
        private BigDecimal totalAmount;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CashDrawerReconciliationReport {
        @JsonSerialize(using = BigDecimalSerializer.class)
        private BigDecimal openingBalance;
        @JsonSerialize(using = BigDecimalSerializer.class)
        private BigDecimal cashWithdrawal;
        @JsonSerialize(using = BigDecimalSerializer.class)
        private BigDecimal actualCashBalance;
        @JsonSerialize(using = BigDecimalSerializer.class)
        private BigDecimal totalCashSales;
        @JsonSerialize(using = BigDecimalSerializer.class)
        private BigDecimal totalCashRefundsPaid;
        @JsonSerialize(using = BigDecimalSerializer.class)
        private BigDecimal cashSalesGrossIn;  // cash received from customers for cash sales
        @JsonSerialize(using = BigDecimalSerializer.class)
        private BigDecimal cashSalesGrossOut; // change returned to customers for cash sales
        @JsonSerialize(using = BigDecimalSerializer.class)
        private BigDecimal cashRefundsGrossOut; // cash paid out to customers for refunds
        @JsonSerialize(using = BigDecimalSerializer.class)
        private BigDecimal cashRefundsGrossIn;  // change collected back from customers for refunds
        @JsonSerialize(using = BigDecimalSerializer.class)
        private BigDecimal expectedCashBalance;
        @JsonSerialize(using = BigDecimalSerializer.class)
        private BigDecimal totalCashDeposits;
        @JsonSerialize(using = BigDecimalSerializer.class)
        private BigDecimal discrepancyAmount;
        @JsonSerialize(using = BigDecimalSerializer.class)
        private BigDecimal adjustmentPending; // Sum of ADJUSTMENT_PENDING from shifts with only pending (no approved/rejected)
        @JsonSerialize(using = BigDecimalSerializer.class)
        private BigDecimal adjustmentApproved; // Sum of all ADJUSTMENT_APPROVED amounts
        @JsonSerialize(using = BigDecimalSerializer.class)
        private BigDecimal adjustmentRejected; // Sum of all ADJUSTMENT_REJECTED amounts
        private String status; // "balanced" or "discrepancy"
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CancellationReport {
        private List<CancellationItem> cancellations;
        private Long count;
        private Long total;
        private PaginationMetaData metaData;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CancellationItem {
        private String id;
        private String type;
        private LocalDateTime dateTime;
        @JsonSerialize(using = BigDecimalSerializer.class)
        private BigDecimal amount;
        private String paymentMethod;
        private String reason;
        private String initiatedBy;
        private String cancelledBy;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ChargebackReport {
        private List<ChargebackItem> chargebacks;
        private Long count;
        private Long total;
        private PaginationMetaData metaData;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ChargebackItem {
        private String transactionId;
        private LocalDateTime dateTime;
        @JsonSerialize(using = BigDecimalSerializer.class)
        private BigDecimal amount;
        private String paymentMethod;
        private String reason;
        private String bankStatus; // "Completed", "Pending", etc.
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class WastageReport {
        private List<WastageItem> wastageItems;
        private Long count;
        private Long total;
        private PaginationMetaData metaData;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class WastageItem {
        private String itemName;
        private String category;
        private Integer quantityWasted;
        @JsonSerialize(using = BigDecimalSerializer.class)
        private BigDecimal totalWastageCost;
        @JsonSerialize(using = BigDecimalSerializer.class)
        private BigDecimal percentageOfTotalWaste;
        private LocalDateTime dateTime;
        private String reason;
    }
}

