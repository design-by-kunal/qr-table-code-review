package com.gulfnet.shared_library.model.response.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RequestApprovalResponse {
    private String requestType; // "Profile Update", "Additional Discount", "Table/Section", "Refund", "Item Cancellation", "Combo Cancellation", "Transaction Cancellation", or "Order Cancellation"
    private ProfileUpdateRequestResponse profileUpdateResponse;
    private AdditionalDiscountRequestResponse additionalDiscountResponse;
    private TableSectionRequestResponse tableSectionResponse;
    private RefundRequestResponse refundResponse;
    private ItemCancellationRequestResponse itemCancellationResponse;
    private ComboCancellationRequestResponse comboCancellationResponse;
    private TransactionCancellationRequestResponse transactionCancellationResponse;
    private OrderCancellationRequestResponse orderCancellationResponse;
}

