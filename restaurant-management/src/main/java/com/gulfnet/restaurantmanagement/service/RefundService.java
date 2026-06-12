package com.gulfnet.restaurantmanagement.service;

import com.gulfnet.shared_library.model.request.CompleteRefundRequest;
import com.gulfnet.shared_library.model.request.RefundCalculateRequest;
import com.gulfnet.shared_library.model.response.dto.RefundCalculateResponse;
import com.gulfnet.shared_library.model.response.dto.RefundCompletionResponse;
import com.gulfnet.shared_library.model.response.dto.RefundDetailsResponse;
import com.gulfnet.shared_library.model.response.dto.RefundReceiptUrlResponse;
import com.gulfnet.shared_library.model.response.dto.RefundRequestResponse;
import com.gulfnet.shared_library.model.response.dto.ResponseDto;

import java.util.List;
import java.util.UUID;

public interface RefundService {
    
    ResponseDto<RefundCompletionResponse> completeRefund(
            UUID refundId,
            CompleteRefundRequest request,
            String userId,
            String userRole);

    ResponseDto<RefundReceiptUrlResponse> getRefundReceiptPresignedUrl(UUID refundId, String userId, String userRole);

    ResponseDto<RefundDetailsResponse> getRefundDetails(UUID refundId, String userId, String userRole);

    ResponseDto<RefundCalculateResponse> calculateRefundAmounts(RefundCalculateRequest request, String userId, String userRole);

    ResponseDto<List<RefundRequestResponse>> getManagerInitiatedRefunds(String restaurantId, String userId, String userRole);

    /**
     * Finalizes a card refund after GMO result notification ({@code Status=RETURN}).
     */
    void completeCardRefundFromGmoNotify(UUID transactionId);
}

