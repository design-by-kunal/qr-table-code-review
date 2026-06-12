package com.gulfnet.restaurantmanagement.controller;

import com.gulfnet.restaurantmanagement.service.OrderService;
import com.gulfnet.restaurantmanagement.service.KdsService;
import com.gulfnet.shared_library.model.request.OrderRequest;
import com.gulfnet.shared_library.model.request.PaymentRequest;
import com.gulfnet.shared_library.model.request.ItemStatusPayload;
import com.gulfnet.shared_library.model.response.dto.ItemStatusResponseWrapper;
import com.gulfnet.shared_library.model.response.dto.OrderDto;
import com.gulfnet.shared_library.model.response.dto.OrderResponse;
import com.gulfnet.shared_library.model.response.dto.ResponseDto;
import com.gulfnet.shared_library.model.response.PaymentResponse;
import com.gulfnet.shared_library.model.response.dto.TicketDashboardListDto;
import com.gulfnet.shared_library.model.request.OrderCancellationRequestDto;
import com.gulfnet.shared_library.model.response.dto.OrderCancellationRequestResponse;
import com.gulfnet.shared_library.model.response.dto.TicketDetailsResponse;
import com.gulfnet.shared_library.enums.OrderType;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.Locale;
import java.util.UUID;
import com.gulfnet.shared_library.model.request.UpdateOrderedItemRequest;
import com.gulfnet.shared_library.model.request.UpdateOrderedComboRequest;
import com.gulfnet.shared_library.model.response.dto.TableOrderResponseDto;
import com.gulfnet.shared_library.model.response.dto.TableDto;
import com.gulfnet.shared_library.model.response.dto.LiveOrderListDto;
import com.gulfnet.shared_library.model.response.dto.InitiateCheckoutResponse;
import com.gulfnet.shared_library.model.response.dto.ReceiptUrlResponse;
import com.gulfnet.shared_library.model.request.AdditionalDiscountRequest;
import com.gulfnet.shared_library.model.request.RatingRequest;
import com.gulfnet.shared_library.model.response.dto.RatingDto;
import com.gulfnet.shared_library.model.response.dto.RatingResponse;
import com.gulfnet.shared_library.model.response.dto.OrderHistoryListDto;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Slf4j
@RestController
@RequestMapping("/api/v1/order")
@RequiredArgsConstructor
public class OrderController {

    private static final String ANONYMOUS_USER = "anonymous";
    
    private final OrderService orderService;
    private final KdsService kdsService;

    /**
     * Creates a new order for a session with the specified order type.
     *
     * @param request   the order request containing items, combos, and session information
     * @param orderType the type of order (DINE_IN, TAKEAWAY, DELIVERY, etc.)
     * @param userId    optional user ID from the request header
     * @return response containing the created order details
     */
    @PostMapping
    public ResponseEntity<ResponseDto<OrderDto<OrderResponse>>> createOrder(
            @Valid @RequestBody OrderRequest request,
            @RequestParam OrderType orderType,
            @RequestHeader(value = "User-ID", required = false) String userId,
            @RequestHeader(value = "Session-ID", required = false) String sessionIdHeader) {
        
        log.info("Request received to create order for session: {} with order type: {} by user: {}", 
                request.getSessionId(), orderType, userId != null ? userId : ANONYMOUS_USER);
        
        ResponseDto<OrderDto<OrderResponse>> response = orderService.createOrder(userId, sessionIdHeader, request, orderType);
        
        log.info("Successfully created order: {}", response.getData().getOrder().getId());
        return ResponseEntity.ok(response);
    }

    /**
     * Applies an additional discount to an existing order.
     *
     * @param orderId the UUID of the order to apply the discount to
     * @param request the additional discount request containing discount details
     * @param userId  the user ID from the request header (required)
     * @return response containing the updated order with applied discount
     */
    @PostMapping("/{orderId}/apply-additional-discount")
    public ResponseEntity<ResponseDto<OrderDto<OrderResponse>>> applyAdditionalDiscount(
            @PathVariable UUID orderId,
            @Valid @RequestBody AdditionalDiscountRequest request,
            @RequestHeader("User-ID") String userId) {

        log.info("Request received to apply additional discount for order: {} by user: {}", orderId, userId);

        ResponseDto<OrderDto<OrderResponse>> response = orderService.applyAdditionalDiscount(userId, orderId, request);

        log.info("Successfully applied additional discount for order: {}", orderId);
        return ResponseEntity.ok(response);
    }

    /**
     * Removes the additional discount from an existing order.
     *
     * @param orderId the UUID of the order to remove the discount from
     * @param userId  the user ID from the request header (required)
     * @return response containing the updated order with discount removed
     */
    @DeleteMapping("/{orderId}/remove-additional-discount")
    public ResponseEntity<ResponseDto<OrderDto<OrderResponse>>> removeAdditionalDiscount(
            @PathVariable UUID orderId,
            @RequestHeader("User-ID") String userId) {

        log.info("Request received to remove additional discount for order: {} by user: {}", orderId, userId);

        ResponseDto<OrderDto<OrderResponse>> response = orderService.removeAdditionalDiscount(userId, orderId);

        log.info("Successfully removed additional discount for order: {}", orderId);
        return ResponseEntity.ok(response);
    }

    /**
     * Updates an existing order with new items, combos, or modifications.
     *
     * @param orderId   the UUID of the order to update
     * @param request   the order request containing updated items and session information
     * @param orderType the type of order (DINE_IN, TAKEAWAY, DELIVERY, etc.)
     * @param userId    optional user ID from the request header
     * @return response containing the updated order details
     */
    @PutMapping("/{orderId}")
    public ResponseEntity<ResponseDto<OrderDto<OrderResponse>>> updateOrder(
            @PathVariable UUID orderId,
            @Valid @RequestBody OrderRequest request,
            @RequestParam OrderType orderType,
            @RequestHeader(value = "User-ID", required = false) String userId,
            @RequestHeader(value = "Session-ID", required = false) String sessionIdHeader) {
        
        log.info("Request received to update order: {} for session: {} with order type: {} by user: {}", 
                orderId, request.getSessionId(), orderType, userId != null ? userId : ANONYMOUS_USER);
        
        ResponseDto<OrderDto<OrderResponse>> response = orderService.updateOrder(userId, sessionIdHeader, orderId, request, orderType);
        
        log.info("Successfully updated order: {}", orderId);
        return ResponseEntity.ok(response);
    }

    /**
     * Calculates the order totals including discounts, taxes, and charges without creating the order.
     * Useful for previewing order costs before final submission.
     *
     * @param request   the order request containing items, combos, and session information
     * @param orderType the type of order (DINE_IN, TAKEAWAY, DELIVERY, etc.)
     * @param userId    optional user ID from the request header
     * @return response containing the calculated order totals and breakdown
     */
    @PostMapping("/calculate")
    public ResponseEntity<ResponseDto<OrderDto<OrderResponse>>> calculateOrder(
            @Valid @RequestBody OrderRequest request,
            @RequestParam OrderType orderType,
            @RequestHeader(value = "User-ID", required = false) String userId,
            @RequestHeader(value = "Session-ID", required = false) String sessionIdHeader) {
        
        log.info("Request received to calculate order for session: {} with order type: {} by user: {}", 
                request.getSessionId(), orderType, userId != null ? userId : ANONYMOUS_USER);
        
        ResponseDto<OrderDto<OrderResponse>> response = orderService.calculateOrder(userId, sessionIdHeader, request, orderType);
        
        log.info("Successfully calculated order for session: {}", request.getSessionId());
        return ResponseEntity.ok(response);
    }

    /**
     * Retrieves all orders associated with a specific session.
     *
     * @param sessionId the UUID of the session to get orders for
     * @param userId   optional user ID from the request header
     * @return response containing a list of orders for the session
     */
    @GetMapping("/session/{sessionId}")
    public ResponseEntity<ResponseDto<OrderDto<List<OrderResponse>>>> getOrdersBySessionId(
            @PathVariable UUID sessionId,
            @RequestHeader(value = "User-ID", required = false) String userId,
            @RequestHeader(value = "Session-ID", required = false) String sessionIdHeader) {
        
        log.info("Request received to get orders for session: {} by user: {}", sessionId, userId);
        
        ResponseDto<OrderDto<List<OrderResponse>>> response = orderService.getOrdersBySessionId(userId, sessionIdHeader, sessionId);
        
        log.info("Successfully retrieved {} orders for session: {}", 
                response.getData().getOrder().size(), sessionId);
        return ResponseEntity.ok(response);
    }

    /**
     * Updates a specific ordered item in an order (e.g., quantity, modifiers).
     *
     * @param orderedItemId the UUID of the ordered item to update
     * @param request       the update request containing new item details
     * @param userId        the user ID from the request header (required)
     * @return response containing the updated order with modified item
     */
    @PutMapping("/ordered-items/{orderedItemId}")
    public ResponseEntity<ResponseDto<OrderDto<OrderResponse>>> updateOrderedItem(
            @PathVariable UUID orderedItemId,
            @Valid @RequestBody UpdateOrderedItemRequest request,
            @RequestHeader("User-ID") String userId) {
        
        log.info("Request received to update ordered item: {} by user: {}", orderedItemId, userId);
        
        ResponseDto<OrderDto<OrderResponse>> response = orderService.updateOrderedItem(userId, orderedItemId, request);
        
        log.info("Successfully updated ordered item: {}", orderedItemId);
        return ResponseEntity.ok(response);
    }

    /**
     * Updates a specific ordered combo in an order (e.g., quantity, selected items).
     *
     * @param orderedComboId the UUID of the ordered combo to update
     * @param request        the update request containing new combo details
     * @param userId         the user ID from the request header (required)
     * @return response containing the updated order with modified combo
     */
    @PutMapping("/ordered-combos/{orderedComboId}")
    public ResponseEntity<ResponseDto<OrderDto<OrderResponse>>> updateOrderedCombo(
            @PathVariable UUID orderedComboId,
            @Valid @RequestBody UpdateOrderedComboRequest request,
            @RequestHeader("User-ID") String userId) {
        
        log.info("Request received to update ordered combo: {} by user: {}", orderedComboId, userId);
        
        ResponseDto<OrderDto<OrderResponse>> response = orderService.updateOrderedCombo(userId, orderedComboId, request);
        
        log.info("Successfully updated ordered combo: {}", orderedComboId);
        return ResponseEntity.ok(response);
    }

    /**
     * Updates the status of ordered items and/or combos.
     * Supports single item, single combo, bulk items, bulk combos, or combined updates.
     *
     * @param payload the status update payload containing item/combo IDs and new status
     * @param userId  the user ID from the request header (required)
     * @return response containing the updated item/combo status information
     */
    @PutMapping("/ordered-items/status")
    public ResponseEntity<ResponseDto<ItemStatusResponseWrapper>> updateItemStatus(
            @Valid @RequestBody ItemStatusPayload payload,
            @RequestHeader("User-ID") String userId) {
        
        // Supports single item, single combo, bulk items, bulk combos, or combined (items + combos)
        log.info("Request received to update item/combo status to {} by user: {}", 
                payload.getItemStatus(), userId);
        
        ResponseDto<ItemStatusResponseWrapper> response = orderService.updateItemStatus(userId, payload);
        
        log.info("Successfully updated item/combo status to {}", payload.getItemStatus());
        
        return ResponseEntity.ok(response);
    }

    /**
     * Retrieves all orders associated with a specific table, grouped by session.
     *
     * @param tableId the UUID of the table to get orders for
     * @param userId  the user ID from the request header (required)
     * @return response containing table details with associated session orders
     */
    @GetMapping("/table/{tableId}")
    public ResponseEntity<ResponseDto<TableDto<TableOrderResponseDto>>> getOrdersByTableId(
            @PathVariable UUID tableId,
            @RequestHeader("User-ID") String userId) {
        
        log.info("Request received to get orders for table: {} by user: {}", tableId, userId);
        
        ResponseDto<TableDto<TableOrderResponseDto>> response = orderService.getOrdersByTableId(userId, tableId);
        
        log.info("Successfully retrieved {} session groups for table: {}", 
                response.getData().getTable().getSession().size(), tableId);
        return ResponseEntity.ok(response);
    }

    /**
     * Retrieves live orders for a restaurant with pagination, filtering, and search capabilities.
     * Supports filtering by order status, order type, transaction status, payment method,
     * section, waiter, table, and text search.
     *
     * @param restaurantId     the UUID of the restaurant to get live orders for
     * @param page            optional page number for pagination
     * @param size            optional page size for pagination
     * @param sortBy          optional field to sort by
     * @param direction       optional sort direction (ASC, DESC)
     * @param orderStatus     optional filter by order status
     * @param orderType       optional filter by order type
     * @param transactionStatus optional filter by transaction status
     * @param paymentMethod   optional filter by payment method
     * @param sectionId       optional filter by section ID
     * @param waiterId        optional filter by waiter ID
     * @param tableId         optional filter by table ID
     * @param search          optional search term for text search
     * @return response containing paginated list of live orders with filters applied
     */
    @GetMapping("/restaurant/{restaurantId}")
    public ResponseEntity<ResponseDto<LiveOrderListDto>> getLiveOrders(
            @PathVariable UUID restaurantId,
            @RequestParam(value = "page", required = false) Integer page,
            @RequestParam(value = "size", required = false) Integer size,
            @RequestParam(value = "sortBy", required = false) String sortBy,
            @RequestParam(value = "direction", required = false) String direction,
            @RequestParam(value = "orderStatus", required = false) String orderStatus,
            @RequestParam(value = "orderType", required = false) String orderType,
            @RequestParam(value = "transactionStatus", required = false) String transactionStatus,
            @RequestParam(value = "paymentMethod", required = false) String paymentMethod,
            @RequestParam(value = "sectionId", required = false) String sectionId,
            @RequestParam(value = "waiterId", required = false) String waiterId,
            @RequestParam(value = "tableId", required = false) String tableId,
            @RequestParam(value = "search", required = false) String search) {

        log.info("Request received to get live orders for restaurant: {} (page: {}, size: {}, sortBy: {}, direction: {}, " +
                "orderStatus: {}, orderType: {}, transactionStatus: {}, paymentMethod: {}, sectionId: {}, waiterId: {}, tableId: {}, search: {})", 
                restaurantId, page, size, sortBy, direction, orderStatus, orderType, transactionStatus, paymentMethod, sectionId, waiterId, tableId, search);

        ResponseDto<LiveOrderListDto> response = orderService.getLiveOrders(restaurantId, page, size, sortBy, direction, 
                orderStatus, orderType, transactionStatus, paymentMethod, sectionId, waiterId, tableId, search);

        log.info("Successfully retrieved {} live orders for restaurant: {} with pagination, filters, and search",
                response.getData().getCount(), restaurantId);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{orderId}/receipt")
    public ResponseEntity<ResponseDto<ReceiptUrlResponse>> getReceiptPresignedUrl(
            @PathVariable UUID orderId) {
        
        log.info("Request received to get receipt presigned URL for order: {}", orderId);
        
        ResponseDto<ReceiptUrlResponse> response = orderService.getReceiptPresignedUrl(orderId);
        
        log.info("Successfully generated presigned URL for order: {}", orderId);
        return ResponseEntity.ok(response);
    }

    /**
     * Initiates the checkout process for an order, preparing it for payment.
     * 
     * <p>Checkout is NOT allowed in the following cases:</p>
     * <ul>
     *   <li>Transaction status is not OPEN (PENDING, COMPLETED, REFUNDED, or CANCELED)</li>
     *   <li>ALL items and combos are ON_HOLD (at least one must be SERVED or CANCELED)</li>
     *   <li>Any item or combo has a status other than SERVED or CANCELED (after ON_HOLD cancellation)</li>
     *   <li>Order has a pending cancellation request (RequestStatus.OPEN)</li>
     *   <li>Any item or combo has a pending cancellation request (RequestStatus.OPEN)</li>
     * </ul>
     * 
     * <p>Note: If SOME (but not all) items/combos are ON_HOLD, they are automatically cancelled during checkout initiation.</p>
     *
     * @param orderId the UUID of the order to initiate checkout for
     * @param userId  the user ID from the request header (optional; when absent, checkout proceeds without an authenticated user context)
     * @return response containing checkout initiation details
     * @throws ResponseStatusException with BAD_REQUEST if checkout is not allowed due to validation failures
     * @throws ResponseStatusException with NOT_FOUND if order or transaction is not found
     */
    @PostMapping("/{orderId}/initiate-checkout")
    public ResponseEntity<ResponseDto<InitiateCheckoutResponse>> initiateCheckout(
            @PathVariable UUID orderId,
            @RequestHeader(value = "User-ID", required = false) String userId,
            @RequestHeader(value = "Session-ID", required = false) String sessionIdHeader) {
        
        log.info("Request received to initiate checkout for order: {} by user: {}", orderId, userId);
        
        ResponseDto<InitiateCheckoutResponse> response = orderService.initiateCheckout(userId, sessionIdHeader, orderId);
        
        log.info("Successfully initiated checkout for order: {}", orderId);
        return ResponseEntity.ok(response);
    }

    /**
     * Processes payment for an order using the specified payment method.
     *
     * @param request the payment request containing order ID, payment method, amount, and customer email
     * @param userId           staff user ID ({@code User-ID}); required for cashier-initiated payment
     * @param sessionIdHeader  customer session ID ({@code Session-ID}); required for customer self-pay when {@code User-ID} is absent
     * @param locale request language ({@code locale} header, e.g. {@code en}, {@code ja}, {@code th}); passed through to payment
     *                 (including GMO hosted card {@code displaysetting.Lang} mapping)
     * @return response containing payment processing result and transaction details
     */
    @PostMapping(value = "/payment", consumes = {"application/json"})
    public ResponseEntity<ResponseDto<PaymentResponse>> processPayment(
            @Valid @RequestBody PaymentRequest request,
            @RequestHeader(value = "User-ID", required = false) String userId,
            @RequestHeader(value = "Session-ID", required = false) String sessionIdHeader,
            @RequestHeader(value = "locale", defaultValue = "en") String locale) {

        Locale paymentLocale = Locale.forLanguageTag(locale);

        log.info("Request received to process payment for order: {} with method: {} and amount: {} and email: '{}' by user: {} session: {} locale: {}",
                request.getOrderId(), request.getPaymentMethod(), request.getAmountPaid(),
                request.getEmail() != null ? request.getEmail() : "null",
                userId != null ? userId : ANONYMOUS_USER,
                sessionIdHeader,
                locale);

        ResponseDto<PaymentResponse> response = orderService.processPayment(userId, sessionIdHeader, request, paymentLocale);

        log.info("Successfully processed payment for order: {}", request.getOrderId());
        return ResponseEntity.ok(response);
    }

    /**
     * Requests cancellation of an order with a specified reason.
     * May require manager approval depending on order status and business rules.
     *
     * @param orderId the UUID of the order to cancel
     * @param request the cancellation request containing cancellation reason
     * @param userId  the user ID from the request header (required)
     * @return response containing cancellation request status and current order status
     */
    @PostMapping("/{orderId}/cancel/request")
    public ResponseEntity<ResponseDto<OrderCancellationRequestResponse>> requestOrderCancellation(
            @PathVariable UUID orderId,
            @Valid @RequestBody OrderCancellationRequestDto request,
            @RequestHeader(value = "User-ID", required = true) String userId) {
        // High-signal trace log for manager-direct cancellation vs. regular request
        log.info("=== CASHIER NOTIFICATION CHECK: API request received to cancel order {} by user {} with cancellationReason='{}' ===",
                orderId,
                userId,
                request.getCancellationReason());

        ResponseDto<OrderCancellationRequestResponse> response =
                orderService.requestOrderCancellation(userId, orderId, request);

        OrderCancellationRequestResponse body = response != null ? response.getData() : null;
        if (body != null) {
            log.info("=== CASHIER NOTIFICATION CHECK: API cancel order {} completed. currentOrderStatus={}, requestStatus={}, cancellationReason='{}' ===",
                    orderId,
                    body.getCurrentOrderStatus(),
                    body.getRequestStatus(),
                    body.getCancellationReason());
        } else {
            log.warn("=== CASHIER NOTIFICATION CHECK: API cancel order {} completed but response body is null ===", orderId);
        }

        return ResponseEntity.ok(response);
    }

    // ==================== TICKET DASHBOARD ENDPOINTS ====================

    @GetMapping("/tickets/{orderedItemId}")
    public ResponseEntity<ResponseDto<TicketDetailsResponse>> getTicketDetails(
            @PathVariable UUID orderedItemId) {

        log.info("Request received to get ticket details for ordered item: {}", orderedItemId);

        ResponseDto<TicketDetailsResponse> response = kdsService.getTicketDetails(orderedItemId);

        log.info("Successfully retrieved ticket details for ordered item: {}", orderedItemId);
        return ResponseEntity.ok(response);
    }

    /**
     * Retrieves the ticket dashboard for a Kitchen Display System (KDS) with pagination and filtering.
     * Supports filtering by item status, order type, table IDs, table codes, category IDs, section IDs,
     * and sorting options.
     *
     * @param kdsId      the UUID of the KDS to get tickets for
     * @param userId     the user ID from the request header (required)
     * @param page       optional page number for pagination
     * @param size       optional page size for pagination
     * @param statuses optional comma-separated list of item statuses to filter by
     * @param orderTypes optional comma-separated list of order types to filter by
     * @param tableIds   optional comma-separated list of table IDs to filter by
     * @param tableCodes optional comma-separated list of table codes to filter by
     * @param categoryIds optional comma-separated list of category IDs to filter by
     * @param sectionIds optional comma-separated list of section IDs to filter by
     * @param sortBy     optional field to sort by
     * @param direction  optional sort direction (ASC, DESC)
     * @return response containing paginated list of tickets for the KDS with filters applied
     */
    @GetMapping("/kds/{kdsId}/tickets")
    public ResponseEntity<ResponseDto<TicketDashboardListDto>> getTicketDashboardForKds(
            @PathVariable UUID kdsId,
            @RequestHeader("User-ID") String userId,
            @RequestParam(value = "page", required = false) Integer page,
            @RequestParam(value = "size", required = false) Integer size,
            @RequestParam(value = "status", required = false) String statuses,
            @RequestParam(value = "orderType", required = false) String orderTypes,
            @RequestParam(value = "tableId", required = false) String tableIds,
            @RequestParam(value = "tableCode", required = false) String tableCodes,
            @RequestParam(value = "categoryId", required = false) String categoryIds,
            @RequestParam(value = "sectionId", required = false) String sectionIds,
            @RequestParam(value = "sortBy", required = false) String sortBy,
            @RequestParam(value = "direction", required = false) String direction) {

        log.info("Request received to get ticket dashboard for KDS: {} by user: {} (page: {}, size: {}, itemStatuses: {}, orderTypes: {}, tableIds: {}, tableCodes: {}, categoryIds: {}, sectionIds: {}, sortBy: {}, direction: {})",
                kdsId, userId, page, size, statuses, orderTypes, tableIds, tableCodes, categoryIds, sectionIds, sortBy, direction);

        ResponseDto<TicketDashboardListDto> response = kdsService.getTicketDashboardForKds(
                kdsId, userId, page, size, statuses, orderTypes, tableIds, tableCodes, categoryIds, sectionIds, sortBy, direction);

        log.info("Successfully retrieved {} tickets for KDS: {}", response.getData().getCount(), kdsId);
        return ResponseEntity.ok(response);
    }

    /**
     * Submits a rating and feedback for a completed order.
     *
     * @param orderId the UUID of the order to rate
     * @param request the rating request containing rating value and optional feedback
     * @param userId  optional user ID from the request header
     * @return response containing the submitted rating details
     */
    @PostMapping("/{orderId}/ratings")
    public ResponseEntity<ResponseDto<RatingDto<RatingResponse>>> submitRating(
            @PathVariable UUID orderId,
            @Valid @RequestBody RatingRequest request,
            @RequestHeader(value = "User-ID", required = false) String userId,
            @RequestHeader(value = "Session-ID", required = false) String sessionIdHeader) {
        
        log.info("Request received to submit rating for order: {} by user: {}", orderId, userId);
        
        ResponseDto<RatingDto<RatingResponse>> response = orderService.submitRating(userId, sessionIdHeader, orderId, request);
        
        log.info("Successfully submitted rating for order: {}", orderId);
        return ResponseEntity.ok(response);
    }

    /**
     * Retrieves order history for a restaurant with pagination, filtering, and search capabilities.
     * Supports filtering by order status, order type, transaction status, payment method, section,
     * date range, and text search. Results are localized based on the locale header.
     *
     * @param restaurantId     the UUID of the restaurant to get order history for
     * @param page            optional page number for pagination
     * @param size            optional page size for pagination
     * @param orderStatus     optional filter by order status
     * @param orderType       optional filter by order type
     * @param transactionStatus optional filter by transaction status
     * @param paymentMethod   optional filter by payment method
     * @param sortBy          optional field to sort by
     * @param direction       optional sort direction (ASC, DESC)
     * @param search          optional search term for text search
     * @param sectionId       optional filter by section ID
     * @param date            optional filter by specific date (ISO date format)
     * @param startDate       optional filter by start date and time (ISO date-time format)
     * @param endDate         optional filter by end date and time (ISO date-time format)
     * @param hasFeedback     when true, only orders that have a row in the rating table ({@code feedback} may be null)
     * @param locale          locale code for localized responses (default: "en")
     * @return response containing paginated list of historical orders with filters applied
     */
    @GetMapping("/restaurant/{restaurantId}/history")
    public ResponseEntity<ResponseDto<OrderHistoryListDto>> getOrderHistory(
            @PathVariable UUID restaurantId,
            @RequestParam(value = "page", required = false) Integer page,
            @RequestParam(value = "size", required = false) Integer size,
            @RequestParam(value = "orderStatus", required = false) String orderStatus,
            @RequestParam(value = "orderType", required = false) String orderType,
            @RequestParam(value = "transactionStatus", required = false) String transactionStatus,
            @RequestParam(value = "paymentMethod", required = false) String paymentMethod,
            @RequestParam(value = "sortBy", required = false) String sortBy,
            @RequestParam(value = "direction", required = false) String direction,
            @RequestParam(value = "search", required = false) String search,
            @RequestParam(value = "sectionId", required = false) String sectionId,
            @RequestParam(value = "date", required = false) 
                @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(value = "startDate", required = false) 
                @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startDate,
            @RequestParam(value = "endDate", required = false) 
                @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endDate,
            @RequestParam(value = "hasFeedback", required = false) Boolean hasFeedback,
            @RequestHeader(value = "locale", defaultValue = "en") String locale) {
        
        log.info("Request received to get order history for restaurant: {} (page: {}, size: {}, orderStatus: {}, orderType: {}, " +
                "transactionStatus: {}, paymentMethod: {}, sortBy: {}, direction: {}, search: {}, sectionId: {}, date: {}, startDate: {}, endDate: {}, hasFeedback: {}) with locale: {}", 
                restaurantId, page, size, orderStatus, orderType, transactionStatus, paymentMethod, sortBy, direction, search, sectionId, date, startDate, endDate, hasFeedback, locale);
        
        ResponseDto<OrderHistoryListDto> response = orderService.getOrderHistory(
                restaurantId, page, size, orderStatus, orderType, transactionStatus, paymentMethod, 
                sortBy, direction, search, sectionId, date, startDate, endDate, hasFeedback, locale);
        
        log.info("Successfully retrieved {} orders for restaurant: {} (total: {})", 
                response.getData().getCount(), restaurantId, response.getData().getTotal());
        return ResponseEntity.ok(response);
    }

}
