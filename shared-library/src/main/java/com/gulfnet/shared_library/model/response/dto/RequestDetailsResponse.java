package com.gulfnet.shared_library.model.response.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RequestDetailsResponse {
    private String requestType; // "Profile Update", "Additional Discount", "Table/Section", "Refund", "Item Cancellation", "Transaction Cancellation", "Order Cancellation", or "Shift Discrepancy"
    private String restaurantName; // Restaurant name for the request
    private ProfileUpdateRequestWithComparisonResponse profileUpdateDetails;
    private AdditionalDiscountRequestResponse additionalDiscountDetails;
    private TableSectionRequestResponse tableSectionDetails;
    private RefundRequestResponse refundDetails;
    private ItemCancellationRequestResponse itemCancellationDetails;
    private ComboCancellationRequestResponse comboCancellationDetails;
    private TransactionCancellationRequestResponse transactionCancellationDetails;
    private OrderCancellationRequestResponse orderCancellationDetails;
    private CashierShiftResponse shiftDiscrepancyDetails;
}

