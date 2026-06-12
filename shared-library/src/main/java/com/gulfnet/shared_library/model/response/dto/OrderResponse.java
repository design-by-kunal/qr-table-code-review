package com.gulfnet.shared_library.model.response.dto;

import com.gulfnet.shared_library.enums.DiscountType;
import com.gulfnet.shared_library.enums.OrderStatus;
import com.gulfnet.shared_library.enums.OrderType;
import com.gulfnet.shared_library.enums.RequestStatus;
import com.gulfnet.shared_library.enums.TransactionStatus;
import lombok.*;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderResponse {
    private UUID id;
    private UUID sessionId;
    private UUID restaurantId;
    private UUID restaurantTableId;
    private String tableCode;
    private UUID waiterId;
    private UUID discountId;
    private OrderStatus orderStatus;
    private OrderType orderType;
    private BigDecimal subTotal;
    private BigDecimal subtotalAfterDiscount;
    private String discountCode;
    private BigDecimal discountValue;
    private BigDecimal discountAmount;
    private DiscountType discountType;
    private BigDecimal taxAmount;
    private BigDecimal alcoholicTaxAmount;
    private BigDecimal nonAlcoholicTaxAmount;
    // Taxable amount breakdown (alcoholic vs non-alcoholic)
    private BigDecimal alcoholicTaxableAmount;
    private BigDecimal nonAlcoholicTaxableAmount;
    private BigDecimal serviceChargeAmount;
    private BigDecimal packingChargeAmount; 
    private BigDecimal additionalDiscountValue;
    private DiscountType additionalDiscountType;
    private BigDecimal additionalDiscountAmount;
    private String additionalDiscountReason;
    private BigDecimal totalAmount;
    private String orderNumber;
    /**
     * GMO LinkType Plus {@code OrderID} for hosted credit card checkout (max 27 chars).
     */
    private String gmoLinkOrderId;
    private UUID transactionId;
    private String transactionNumber;
    private TransactionStatus transactionStatus;
    private RequestStatus transactionRequestStatus;
    private UUID refundId;
    private List<OrderedItemResponse> orderedItems;
    private List<OrderedComboResponse> orderedCombos;
    private String gstNumber;
    private String email;
    private RequestStatus requestStatus;
    private RequestStatus additionalDiscountRequestStatus;
    private String cancellationReason;
    private OrderRating rating;
    private OffsetDateTime startTime;
    private OffsetDateTime endTime;
    private List<WaiterInfo> waiters;
    private Boolean isInitiated;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class OrderRating {
        private Integer experience;
        private Integer food;
        private Integer service;
        private String feedback;
    }
}
