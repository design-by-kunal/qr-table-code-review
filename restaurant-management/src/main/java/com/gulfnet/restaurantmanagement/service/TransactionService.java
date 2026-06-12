package com.gulfnet.restaurantmanagement.service;

import com.gulfnet.shared_library.model.request.TransactionStatusPayload;
import com.gulfnet.shared_library.model.response.dto.ResponseDto;
import com.gulfnet.shared_library.model.response.dto.TransactionCancellationRequestListResponse;
import com.gulfnet.shared_library.model.response.dto.TransactionCancellationRequestResponse;
import com.gulfnet.shared_library.model.response.dto.TransactionListDto;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

public interface TransactionService {
        
    /**
     * Paginated transactions for a restaurant; filter parameters match {@link OrderService#getOrderHistory}
     * except this API includes {@code locale} on the transaction list response.
     */
    ResponseDto<TransactionListDto> getTransactionsByRestaurant(
            UUID restaurantId,
            Integer page,
            Integer size,
            String orderStatus,
            String orderType,
            String transactionStatus,
            String paymentMethod,
            String sortBy,
            String direction,
            String search,
            LocalDate date,
            LocalDateTime startDate,
            LocalDateTime endDate,
            String locale);

    ResponseDto<TransactionCancellationRequestResponse> cancelTransaction(
            String userId,
            UUID transactionId,
            TransactionStatusPayload payload);

    ResponseDto<TransactionCancellationRequestListResponse> getPendingTransactionCancellationRequests(
            int page,
            int size,
            String userRole);

    ResponseDto<com.gulfnet.shared_library.model.response.dto.RaiseRefundRequestResponse> initiateRefund(
            String userId,
            String userRole,
            UUID transactionId,
            com.gulfnet.shared_library.model.request.RaiseRefundRequest request);

    ResponseDto<com.gulfnet.shared_library.model.response.dto.DiscountOfferReportListDto> getDiscountsAndOffersAppliedReport(
            UUID restaurantId,
            Integer page,
            Integer size,
            String transactionStatus,
            LocalDate date,
            LocalDateTime startDate,
            LocalDateTime endDate,
            String locale);

    void exportDiscountsAndOffersAppliedReportToCsv(
            UUID restaurantId,
            String transactionStatus,
            LocalDate date,
            LocalDateTime startDate,
            LocalDateTime endDate,
            String locale,
            jakarta.servlet.http.HttpServletResponse response) throws java.io.IOException;
}

