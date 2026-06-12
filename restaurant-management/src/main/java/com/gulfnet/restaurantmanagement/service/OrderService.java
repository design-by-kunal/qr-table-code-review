package com.gulfnet.restaurantmanagement.service;

import com.gulfnet.shared_library.model.request.OrderRequest;
import com.gulfnet.shared_library.model.request.UpdateOrderedItemRequest;
import com.gulfnet.shared_library.model.request.UpdateOrderedComboRequest;
import com.gulfnet.shared_library.model.request.ItemStatusPayload;
import com.gulfnet.shared_library.model.request.PaymentRequest;
import com.gulfnet.shared_library.model.request.ItemCancellationRequestDto;
import com.gulfnet.shared_library.model.request.ItemCancellationApprovalRequest;
import com.gulfnet.shared_library.model.response.dto.OrderDto;
import com.gulfnet.shared_library.model.response.dto.OrderResponse;
import com.gulfnet.shared_library.model.response.dto.ResponseDto;
import com.gulfnet.shared_library.model.response.dto.ItemStatusResponseWrapper;
import com.gulfnet.shared_library.model.response.dto.SessionDto;
import com.gulfnet.shared_library.model.response.dto.SessionOrderWrapper;
import com.gulfnet.shared_library.model.response.dto.TableOrderResponseDto;
import com.gulfnet.shared_library.model.response.dto.TableDto;
import com.gulfnet.shared_library.model.response.dto.LiveOrderResponse;
import com.gulfnet.shared_library.model.response.dto.LiveOrderListDto;
import com.gulfnet.shared_library.model.response.dto.InitiateCheckoutResponse;
import com.gulfnet.shared_library.model.response.PaymentResponse;
import com.gulfnet.shared_library.model.response.dto.ReceiptUrlResponse;
import com.gulfnet.shared_library.model.response.dto.ItemCancellationRequestResponse;
import com.gulfnet.shared_library.model.response.dto.ItemCancellationRequestListResponse;
import com.gulfnet.shared_library.model.response.dto.ComboCancellationRequestResponse;
import com.gulfnet.shared_library.model.response.dto.CancellationRequestListResponse;
import com.gulfnet.shared_library.model.response.dto.TicketDashboardListDto;
import com.gulfnet.shared_library.model.response.dto.TicketDetailsResponse;
import com.gulfnet.shared_library.model.request.AdditionalDiscountRequest;
import com.gulfnet.shared_library.model.request.OrderCancellationRequestDto;
import com.gulfnet.shared_library.model.response.dto.OrderCancellationRequestResponse;
import com.gulfnet.shared_library.model.request.RatingRequest;
import com.gulfnet.shared_library.model.response.dto.RatingDto;
import com.gulfnet.shared_library.model.response.dto.RatingResponse;
import com.gulfnet.shared_library.model.response.dto.OrderHistoryListDto;

import com.gulfnet.shared_library.enums.OrderType;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

public interface OrderService {
    
    ResponseDto<OrderDto<OrderResponse>> createOrder(String userId, String sessionIdHeader, OrderRequest request, OrderType orderType);
    
    ResponseDto<OrderDto<OrderResponse>> updateOrder(String userId, String sessionIdHeader, UUID orderId, OrderRequest request, OrderType orderType);
    
    ResponseDto<OrderDto<OrderResponse>> calculateOrder(String userId, String sessionIdHeader, OrderRequest request, OrderType orderType);
    
    ResponseDto<OrderDto<List<OrderResponse>>> getOrdersBySessionId(String userId, String sessionIdHeader, UUID sessionId);

    ResponseDto<OrderDto<OrderResponse>> updateOrderedItem(String userId, UUID orderedItemId, UpdateOrderedItemRequest request);

    ResponseDto<OrderDto<OrderResponse>> updateOrderedCombo(String userId, UUID orderedComboId, UpdateOrderedComboRequest request);

    ResponseDto<ItemStatusResponseWrapper> updateItemStatus(String userId, ItemStatusPayload payload);

    ResponseDto<TableDto<TableOrderResponseDto>> getOrdersByTableId(String userId, UUID tableId);

    ResponseDto<LiveOrderListDto> getLiveOrders(UUID restaurantId, Integer page, Integer size, String sortBy, String direction, 
                                               String orderStatus, String orderType, String transactionStatus, 
                                               String paymentMethod, String sectionId, String waiterId, String tableId, String search);
    
    ResponseDto<InitiateCheckoutResponse> initiateCheckout(String userId, String sessionIdHeader, UUID orderId);
    
    ResponseDto<PaymentResponse> processPayment(String userId, String sessionIdHeader, PaymentRequest request, Locale paymentLocale);

    ResponseDto<ReceiptUrlResponse> getReceiptPresignedUrl(UUID orderId);

    // Order cancellation request methods
    ResponseDto<OrderCancellationRequestResponse> requestOrderCancellation(String userId, UUID orderId, OrderCancellationRequestDto request);

    ResponseDto<OrderDto<OrderResponse>> applyAdditionalDiscount(String userId, UUID orderId, AdditionalDiscountRequest request);
    
    ResponseDto<OrderDto<OrderResponse>> removeAdditionalDiscount(String userId, UUID orderId);

    ResponseDto<RatingDto<RatingResponse>> submitRating(String userId, String sessionIdHeader, UUID orderId, RatingRequest request);

    /**
     * Retrieves paginated order history for a restaurant with multiple filters.
     * Supports filtering by order status, order type, transaction status, payment method, section, date range, and text search.
     *
     * @param restaurantId     the restaurant ID to get order history for
     * @param page             page number (1-based)
     * @param size             page size
     * @param orderStatus      optional filter by order status
     * @param orderType        optional filter by order type
     * @param transactionStatus optional filter by transaction status
     * @param paymentMethod    optional filter by payment method
     * @param sortBy           field to sort by
     * @param direction        sort direction (ASC or DESC)
     * @param search           optional search term for text search
     * @param sectionId        optional filter by section ID
     * @param date             optional filter by specific date
     * @param startDate        optional start date and time for date range filter
     * @param endDate          optional end date and time for date range filter
     * @param hasFeedback      optional; when {@code true}, only orders with a matching row in the rating table
     * @param locale           locale for messages
     * @return {@link ResponseDto} containing paginated list of order history
     */
    ResponseDto<OrderHistoryListDto> getOrderHistory(
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
            String sectionId,
            LocalDate date,
            LocalDateTime startDate,
            LocalDateTime endDate,
            Boolean hasFeedback,
            String locale);
}
