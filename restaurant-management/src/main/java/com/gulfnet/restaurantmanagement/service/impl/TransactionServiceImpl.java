package com.gulfnet.restaurantmanagement.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gulfnet.restaurantmanagement.service.NotificationService;
import com.gulfnet.restaurantmanagement.service.OrderRecalculationService;
import com.gulfnet.restaurantmanagement.service.RefundReceiptService;
import com.gulfnet.restaurantmanagement.service.TransactionService;
import com.gulfnet.restaurantmanagement.service.RestaurantAlertEvaluationService;
import com.gulfnet.restaurantmanagement.util.MessageUtil;
import com.gulfnet.shared_library.entity.AuditTrail;
import com.gulfnet.shared_library.entity.Order;
import com.gulfnet.shared_library.entity.OrderedCombo;
import com.gulfnet.shared_library.entity.OrderedItem;
import com.gulfnet.shared_library.entity.RestaurantTable;
import com.gulfnet.shared_library.entity.TableAssignment;
import com.gulfnet.shared_library.entity.Refund;
import com.gulfnet.shared_library.entity.Role;
import com.gulfnet.shared_library.entity.Transaction;
import com.gulfnet.shared_library.entity.Restaurant;
import com.gulfnet.shared_library.entity.RestaurantSection;
import com.gulfnet.shared_library.entity.RestaurantSectionTranslation;
import com.gulfnet.shared_library.entity.User;
import com.gulfnet.shared_library.enums.ItemStatus;
import com.gulfnet.shared_library.enums.OrderStatus;
import com.gulfnet.shared_library.enums.OrderType;
import com.gulfnet.shared_library.enums.PaymentSystemType;
import com.gulfnet.shared_library.enums.RequestStatus;
import com.gulfnet.shared_library.enums.TransactionStatus;
import com.gulfnet.shared_library.enums.EntityStatus;
import com.gulfnet.shared_library.enums.ActionType;
import com.gulfnet.shared_library.enums.AlcoholType;
import com.gulfnet.shared_library.enums.ChargeType;
import com.gulfnet.shared_library.model.request.CompleteRefundRequest;
import com.gulfnet.shared_library.model.request.RaiseRefundRequest;
import com.gulfnet.shared_library.model.response.dto.RaiseRefundRequestResponse;
import com.gulfnet.shared_library.model.response.dto.RefundCompletionResponse;
import com.gulfnet.shared_library.model.response.dto.RefundReceiptUrlResponse;
import com.gulfnet.shared_library.enums.RefundType;
import com.gulfnet.shared_library.entity.Item;
import com.gulfnet.shared_library.entity.Combo;
import com.gulfnet.shared_library.entity.ItemTranslation;
import com.gulfnet.shared_library.entity.ComboTranslation;
import com.gulfnet.shared_library.model.request.TransactionCancellationRequestDto;
import com.gulfnet.shared_library.model.request.TransactionStatusPayload;
import com.gulfnet.shared_library.model.response.dto.RefundRequestResponse;
import com.gulfnet.shared_library.model.response.dto.PaginationMetaData;
import com.gulfnet.shared_library.model.response.dto.ResponseDto;
import com.gulfnet.shared_library.model.response.dto.TransactionCancellationRequestListResponse;
import com.gulfnet.shared_library.model.response.dto.TransactionCancellationRequestResponse;
import com.gulfnet.shared_library.model.response.dto.TransactionListDto;
import com.gulfnet.shared_library.model.response.dto.TransactionResponse;
import com.gulfnet.shared_library.model.response.dto.DiscountOfferReportListDto;
import com.gulfnet.shared_library.model.response.dto.DiscountOfferReportResponse;
import com.gulfnet.shared_library.repository.OrderRepository;
import com.gulfnet.shared_library.repository.OrderedComboRepository;
import com.gulfnet.shared_library.repository.OrderedItemRepository;
import com.gulfnet.shared_library.repository.TableAssignmentRepository;
import com.gulfnet.shared_library.repository.RefundRepository;
import com.gulfnet.shared_library.repository.RefundItemRepository;
import com.gulfnet.shared_library.entity.RefundItem;
import com.gulfnet.shared_library.config.AWSService;
import com.gulfnet.shared_library.repository.RoleRepository;
import com.gulfnet.shared_library.repository.TransactionRepository;
import com.gulfnet.shared_library.repository.RestaurantRepository;
import com.gulfnet.shared_library.repository.UserRepository;
import com.gulfnet.restaurantmanagement.service.AuditTrailService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.server.ResponseStatusException;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.math.BigDecimal;
import java.math.RoundingMode;
import com.gulfnet.shared_library.util.CancellationAmountPolicy;
import com.gulfnet.shared_library.util.CurrencyFormatter;
import com.gulfnet.shared_library.util.TranslationUtils;
import com.gulfnet.restaurantmanagement.config.LocalizationProperties;
import com.gulfnet.restaurantmanagement.config.RestaurantChainConfigProperties;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;
import java.io.OutputStreamWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import jakarta.servlet.http.HttpServletResponse;

@Slf4j
@Service
@RequiredArgsConstructor
public class TransactionServiceImpl implements TransactionService {

    // Constants for message keys
    private static final String MSG_USER_NOT_FOUND = "user.not.found";
    private static final String MSG_RESTAURANT_NOT_FOUND = "restaurant.not.found";
    private static final String MSG_TRANSACTION_NOT_FOUND = "transaction.not.found";
    private static final String MSG_ERROR_INVALID_TRANSACTION_STATUS = "error.invalid.transactionStatus";
    
    // Constants for role names
    private static final String ROLE_MANAGER = "MANAGER";
    private static final String ROLE_HQ_ADMIN = "HQ_ADMIN";
    
    // Constants for action types
    private static final String ACTION_TYPE_TRANSACTION = "TRANSACTION";
    
    // Constants for request types
    private static final String REQUEST_TYPE_REFUND = "REFUND";
    
    // Constants for field names
    private static final String FIELD_CREATED_AT = "createdAt";
    private static final String FIELD_REQUEST_TYPE = "requestType";
    private static final String FIELD_REFUND_AMOUNT = "refundAmount";
    
    // Constants for date format
    private static final String DATE_TIME_FORMAT = "yyyy-MM-dd HH:mm:ss";

    private final TransactionRepository transactionRepository;
    private final RefundRepository refundRepository;
    private final RestaurantRepository restaurantRepository;
    private final MessageUtil messageUtil;
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final NotificationService notificationService;
    private final OrderRepository orderRepository;
    private final OrderedItemRepository orderedItemRepository;
    private final OrderedComboRepository orderedComboRepository;
    private final RefundItemRepository refundItemRepository;
    private final AuditTrailService auditTrailService;
    private final RefundReceiptService refundReceiptService;
    private final AWSService awsService;
    private final TableAssignmentRepository tableAssignmentRepository;
    private final RestaurantChainConfigProperties restaurantChainConfigProperties;
    private final LocalizationProperties localizationProperties;

    @Autowired
    @Lazy
    private RestaurantAlertEvaluationService restaurantAlertEvaluationService;

    @Autowired
    @Lazy
    private OrderRecalculationService orderRecalculationService;
    
    @PersistenceContext
    private EntityManager entityManager;

    /**
     * Retrieves a filtered, sorted, and paginated list of transactions for a restaurant.
     * <p>
     * Supports comma-separated filters for order status/type, transaction status, and payment method,
     * an optional free-text search (transaction number / order number), and date or date-range filtering.
     * </p>
     *
     * @param restaurantId       the restaurant identifier to scope the query
     * @param page               1-based page number; when {@code null} or invalid, paging may be disabled by callers
     * @param size               page size; when {@code null} or invalid, paging may be disabled by callers
     * @param orderStatus        optional order-status filter (supports comma-separated values)
     * @param orderType          optional order-type filter (supports comma-separated values)
     * @param transactionStatus  optional transaction-status filter (supports comma-separated values)
     * @param paymentMethod      optional payment method filter (supports comma-separated values)
     * @param sortBy             optional sort field
     * @param direction          optional sort direction (ASC/DESC)
     * @param search             optional case-insensitive search term
     * @param date               optional single-day filter (takes precedence over {@code startDate}/{@code endDate})
     * @param startDate          optional start date-time (used when {@code date} is not provided)
     * @param endDate            optional end date-time (used when {@code date} is not provided)
     * @param locale             locale string from request (currently resolved via {@link LocaleContextHolder})
     * @return response wrapper containing {@link TransactionListDto} and paging metadata (when applicable)
     * @throws ResponseStatusException if the restaurant is not found or if any filter value is invalid
     */
    @Override
    @Transactional(readOnly = true)
    public ResponseDto<TransactionListDto> getTransactionsByRestaurant(
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
            String locale) {
        
        log.info("Getting transactions for restaurant: {} (page: {}, size: {}, sortBy: {}, direction: {}, " +
                "orderStatus: {}, orderType: {}, transactionStatus: {}, paymentMethod: {}, search: {}, date: {}, startDate: {}, endDate: {})", 
                restaurantId, page, size, sortBy, direction, orderStatus, orderType, transactionStatus, paymentMethod, search, date, startDate, endDate);

        Locale userLocale = LocaleContextHolder.getLocale();

        // Validate restaurant
        Restaurant restaurant = restaurantRepository.findById(restaurantId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        messageUtil.getMessage(MSG_RESTAURANT_NOT_FOUND, userLocale)));

        // Parse filters - support comma-separated values
        Collection<OrderStatus> orderStatuses = null;
        if (orderStatus != null && !orderStatus.isBlank()) {
            try {
                orderStatuses = Arrays.stream(orderStatus.split(","))
                        .map(String::trim)
                        .filter(s -> !s.isBlank())
                        .map(s -> OrderStatus.valueOf(s.toUpperCase()))
                        .collect(Collectors.toSet());
                if (orderStatuses.isEmpty()) orderStatuses = null;
            } catch (IllegalArgumentException e) {
                String errorMessage = messageUtil.getMessage("error.invalid.orderStatus", userLocale, orderStatus);
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, errorMessage);
            }
        }
        
        Collection<OrderType> orderTypes = null;
        if (orderType != null && !orderType.isBlank()) {
            try {
                orderTypes = Arrays.stream(orderType.split(","))
                        .map(String::trim)
                        .filter(s -> !s.isBlank())
                        .map(s -> OrderType.valueOf(s.toUpperCase()))
                        .collect(Collectors.toSet());
                if (orderTypes.isEmpty()) orderTypes = null;
            } catch (IllegalArgumentException e) {
                String errorMessage = messageUtil.getMessage("error.invalid.orderType", userLocale, orderType);
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, errorMessage);
            }
        }
        
        Collection<TransactionStatus> transactionStatuses = null;
        if (transactionStatus != null && !transactionStatus.isBlank()) {
            try {
                transactionStatuses = Arrays.stream(transactionStatus.split(","))
                        .map(String::trim)
                        .filter(s -> !s.isBlank())
                        .map(s -> TransactionStatus.valueOf(s.toUpperCase()))
                        .collect(Collectors.toSet());
                if (transactionStatuses.isEmpty()) transactionStatuses = null;
            } catch (IllegalArgumentException e) {
                String errorMessage = messageUtil.getMessage(MSG_ERROR_INVALID_TRANSACTION_STATUS, userLocale, transactionStatus);
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, errorMessage);
            }
        }
        
        Collection<String> paymentMethods = null;
        if (paymentMethod != null && !paymentMethod.isBlank()) {
            paymentMethods = Arrays.stream(paymentMethod.split(","))
                    .map(String::trim)
                    .filter(s -> !s.isBlank())
                    .collect(Collectors.toSet());
            if (paymentMethods.isEmpty()) paymentMethods = null;
        }

        // Search by transactionNumber or orderNumber (case-insensitive)
        String searchTerm = (search != null && !search.isBlank()) ? search.trim() : null;
        String likePatternLower = (searchTerm == null ? null : "%" + searchTerm.toLowerCase() + "%");

        // Handle date filtering - prioritize 'date' parameter if provided
        // Use reasonable min/max dates for null dates to avoid PostgreSQL timestamp range issues
        LocalDateTime startDateTime;
        LocalDateTime endDateTime;
        
        if (date != null) {
            // If 'date' is provided, filter for that entire day
            startDateTime = date.atStartOfDay(); // 00:00:00
            endDateTime = date.atTime(23, 59, 59, 999999000); // 23:59:59.999999
        } else {
            // Otherwise, use startDate and endDate if provided
            // If null, use reasonable min/max dates (PostgreSQL timestamp range: 4713 BC to 294276 AD)
            // Using 1900-01-01 and 2100-12-31 as safe bounds
            startDateTime = (startDate != null) ? startDate : LocalDateTime.of(1900, 1, 1, 0, 0, 0);
            if (endDate != null) {
                // If endDate is provided, set it to end of day for inclusive filtering
                endDateTime = endDate.toLocalDate().atTime(23, 59, 59, 999999000);
            } else {
                // Use a far future date within PostgreSQL's valid range
                endDateTime = LocalDateTime.of(2100, 12, 31, 23, 59, 59, 999999000);
            }
        }

        // Pagination - support both paged and unpaged requests
        boolean noPaging = (page == null || size == null || page <= 0 || size <= 0);
        Pageable pageable;
        
        // Handle sorting
        // If sortBy is "transactionDateTime" (or empty), we sort by effective time:
        // COALESCE(updatedAt, createdAt) so null updatedAt falls back to createdAt.
        String dbSortField = sortBy;
        if (sortBy != null && !sortBy.isBlank()) {
            if ("transactionDateTime".equalsIgnoreCase(sortBy)) {
                dbSortField = FIELD_CREATED_AT;
            }
        } else {
            dbSortField = FIELD_CREATED_AT;
        }
        
        if (!noPaging) {
            if (direction == null || direction.isBlank()) {
                direction = "DESC";
            }
            org.springframework.data.domain.Sort.Direction sortDirection = 
                direction.equalsIgnoreCase("ASC") ? 
                org.springframework.data.domain.Sort.Direction.ASC : 
                org.springframework.data.domain.Sort.Direction.DESC;
            // For effective-time sorting we use repository ORDER BY COALESCE(...) to get a true fallback.
            // For other fields we keep pageable sort.
            if (FIELD_CREATED_AT.equals(dbSortField)) {
                pageable = PageRequest.of(page - 1, size);
            } else {
                pageable = PageRequest.of(page - 1, size, org.springframework.data.domain.Sort.by(sortDirection, dbSortField));
            }
        } else {
            pageable = Pageable.unpaged();
        }

        Page<Transaction> transactionsPage;
        if (FIELD_CREATED_AT.equals(dbSortField)) {
            org.springframework.data.domain.Sort.Direction effectiveDirection =
                    (direction != null && direction.equalsIgnoreCase("ASC"))
                            ? org.springframework.data.domain.Sort.Direction.ASC
                            : org.springframework.data.domain.Sort.Direction.DESC;

            transactionsPage = (effectiveDirection == org.springframework.data.domain.Sort.Direction.ASC)
                    ? transactionRepository.findByRestaurantIdWithFiltersOrderByEffectiveTimeAsc(
                    restaurantId, orderStatuses, orderTypes, transactionStatuses, paymentMethods, likePatternLower,
                    startDateTime.atOffset(java.time.ZoneOffset.UTC), endDateTime.atOffset(java.time.ZoneOffset.UTC), pageable)
                    : transactionRepository.findByRestaurantIdWithFiltersOrderByEffectiveTimeDesc(
                    restaurantId, orderStatuses, orderTypes, transactionStatuses, paymentMethods, likePatternLower,
                    startDateTime.atOffset(java.time.ZoneOffset.UTC), endDateTime.atOffset(java.time.ZoneOffset.UTC), pageable);
        } else {
            transactionsPage = transactionRepository.findByRestaurantIdWithFilters(
                    restaurantId, orderStatuses, orderTypes, transactionStatuses, paymentMethods, likePatternLower,
                    startDateTime.atOffset(java.time.ZoneOffset.UTC), endDateTime.atOffset(java.time.ZoneOffset.UTC), pageable);
        }

        // Get currency for formatting prices
        String currency = restaurantChainConfigProperties.getChain() != null ? restaurantChainConfigProperties.getChain().getCurrency() : null;

        List<TransactionResponse> transactions = transactionsPage.getContent().stream()
                .map(transaction -> convertToTransactionResponse(transaction, currency))
                .collect(Collectors.toList());

        TransactionListDto dto = TransactionListDto.builder()
                .transactions(transactions)
                .count((long) transactions.size())
                .total(transactionsPage.getTotalElements())
                .metaData(noPaging ? null : PaginationMetaData.builder()
                        .page(page)
                        .size(size)
                        .totalPages(transactionsPage.getTotalPages())
                        .totalRecords(transactionsPage.getTotalElements())
                        .build())
                .build();

        return ResponseDto.<TransactionListDto>builder()
                .message(messageUtil.getMessage("transaction.list.success", userLocale))
                .data(dto)
                .build();
    }

    /**
     * Converts Transaction entity to TransactionResponse DTO
     */
    private TransactionResponse convertToTransactionResponse(Transaction transaction, String currency) {
        // Get order information
        com.gulfnet.shared_library.entity.Order order = transaction.getOrder();
        
        // Determine orderBy: if createdBy (waiter) exists -> use name; else -> Customer
        String orderBy = "Customer";
        if (order != null && order.getCreatedBy() != null) {
            String firstName = order.getCreatedBy().getFirstName() != null ? order.getCreatedBy().getFirstName() : "";
            String lastName = order.getCreatedBy().getLastName() != null ? order.getCreatedBy().getLastName() : "";
            String fullName = (firstName + " " + lastName).trim();
            if (!fullName.isEmpty()) {
                orderBy = fullName;
            }
        }
        
        // Get table information
        Integer tableOrder = null;
        String tableCode = null;
        Integer rowOrder = null;
        UUID sectionId = null;
        String sectionName = null;
        
        if (order != null && order.getRestaurantTable() != null) {
            tableOrder = order.getRestaurantTable().getTableOrder();
            tableCode = order.getRestaurantTable().getTableCode();
            if (order.getRestaurantTable().getRestaurantRow() != null) {
                rowOrder = order.getRestaurantTable().getRestaurantRow().getRowOrder();
                if (order.getRestaurantTable().getRestaurantRow().getRestaurantSection() != null) {
                    // Get section information
                    RestaurantSection section = order.getRestaurantTable().getRestaurantRow().getRestaurantSection();
                    sectionId = section.getId();
                    
                    // Get section name from translations
                    Locale userLocale = LocaleContextHolder.getLocale();
                    
                    sectionName = section.getTranslations().stream()
                            .filter(t -> userLocale.getLanguage().equalsIgnoreCase(t.getLanguageCode()))
                            .map(RestaurantSectionTranslation::getName)
                            .findFirst()
                            .orElse(section.getTranslations().isEmpty() ? "" : section.getTranslations().get(0).getName());
                }
            }
        }
        
        // Get refund ID only if transaction status is REFUNDED or PARTIALLY_REFUNDED
        UUID refundId = null;
        TransactionStatus transactionStatus = transaction.getTransactionStatus();
        if (transactionStatus == TransactionStatus.REFUNDED || transactionStatus == TransactionStatus.PARTIALLY_REFUNDED) {
            try {
                refundId = refundRepository.findByTransactionId(transaction.getId())
                        .map(Refund::getId)
                        .orElse(null);
            } catch (Exception e) {
                log.debug("Error fetching refund for transaction {}: {}", transaction.getId(), e.getMessage());
            }
        }
        
        return TransactionResponse.builder()
                .orderId(order != null ? order.getId() : null)
                .orderNumber(order != null ? order.getOrderNumber() : null)
                .orderStatus(order != null ? order.getOrderStatus() : null)
                .orderType(order != null ? order.getOrderType() : null)
                .orderBy(orderBy)
                .totalAmount(order != null && order.getTotalAmount() != null ? CurrencyFormatter.formatAmount(order.getTotalAmount(), currency) : null)
                .tableId(order != null && order.getRestaurantTable() != null ? order.getRestaurantTable().getId() : null)
                .tableCode(tableCode)
                .tableOrder(tableOrder)
                .rowOrder(rowOrder)
                .sectionId(sectionId)
                .sectionName(sectionName)
                .transactionId(transaction.getId())
                .transactionStatus(transaction.getTransactionStatus())
                .transactionNumber(transaction.getTransactionNumber())
                .paymentMethod(transaction.getPaymentMethod())
                .paymentApp(transaction.getPaymentApp())
                .sessionId(order != null && order.getSession() != null ? order.getSession().getId() : null)
                .transactionDateTime(transaction.getCreatedAt() != null ? transaction.getCreatedAt().toLocalDateTime() : null)
                .refundId(refundId)
                .build();
    }

    /**
     * Cancels a transaction either directly or by raising an approval request.
     * <p>
     * Managers can cancel immediately. Other roles may be required to create a cancellation request depending
     * on the current transaction status. When cancellation happens, this method reloads the transaction with
     * its order/table relationships to support downstream notifications and audit entries.
     * </p>
     *
     * @param userId   authenticated user id (string form; may be {@code null}/blank/"null")
     * @param transactionId the transaction identifier to cancel
     * @param payload  requested status and optional reason (status must be {@link TransactionStatus#CANCELED})
     * @return response wrapper containing the cancellation request/transaction status details
     * @throws ResponseStatusException if the transaction is not found, status is invalid, or cancellation is not allowed
     */
    @Override
    @Transactional
    public ResponseDto<TransactionCancellationRequestResponse> cancelTransaction(
            String userId,
            UUID transactionId,
            TransactionStatusPayload payload) {
        Locale userLocale = LocaleContextHolder.getLocale();

        // Validate transaction
        Transaction transaction = transactionRepository.findById(transactionId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        messageUtil.getMessage(MSG_TRANSACTION_NOT_FOUND, userLocale)));

        // Validate that status is CANCELED
        if (payload.getTransactionStatus() != TransactionStatus.CANCELED) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    messageUtil.getMessage("transaction.cancellation.invalid.status", userLocale));
        }

        // Validate transaction can be canceled
        if (transaction.getTransactionStatus() == TransactionStatus.CANCELED ||
            transaction.getTransactionStatus() == TransactionStatus.REFUNDED) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    messageUtil.getMessage("transaction.already.canceled.or.refunded", userLocale));
        }
        
        // Never downgrade a COMPLETED transaction to CANCELED.
        // Completed payments should go through refund workflow instead.
        if (transaction.getTransactionStatus() == TransactionStatus.COMPLETED) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    messageUtil.getMessage("transaction.cancellation.not.allowed.completed", userLocale));
        }

        // Get authenticated user
        User authenticatedUser = null;
        if (userId != null && !userId.trim().isEmpty() && !"null".equalsIgnoreCase(userId)) {
            try {
                authenticatedUser = userRepository.findById(UUID.fromString(userId))
                        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                                messageUtil.getMessage(MSG_USER_NOT_FOUND, userLocale)));
            } catch (IllegalArgumentException e) {
                log.warn("Invalid userId format: {}", userId);
            }
        }

        // Check if user is MANAGER - managers can cancel transactions directly without creating request
        if (authenticatedUser != null && isManager(authenticatedUser)) {
            // Manager can cancel directly - no request needed
            // Reload transaction with order and table relationships to ensure waiter notification works
            transaction = transactionRepository.findByIdWithOrderAndTable(transactionId)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                            messageUtil.getMessage(MSG_TRANSACTION_NOT_FOUND, userLocale)));
            
            // Validate transaction can be canceled
            if (transaction.getTransactionStatus() == TransactionStatus.CANCELED ||
                transaction.getTransactionStatus() == TransactionStatus.REFUNDED) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        messageUtil.getMessage("transaction.already.canceled.or.refunded", userLocale));
            }
            
            handleTransactionCancellation(transaction, authenticatedUser, userLocale);
            log.info("Manager directly cancelled transaction {}", transactionId);
            
            return ResponseDto.<TransactionCancellationRequestResponse>builder()
                    .message(messageUtil.getMessage("transaction.cancelled.directly", userLocale))
                    .data(buildTransactionCancellationRequestResponse(transaction))
                    .build();
        }

        // Check if cancellation requires approval
        if (requiresTransactionCancellationApproval(transaction.getTransactionStatus())) {
            // Create cancellation request
            return handleTransactionCancellationRequest(transaction, payload, authenticatedUser, userLocale);
        } else {
            // Direct cancellation - no approval needed (for non-manager users when approval not required)
            // Reload transaction with order and table relationships to ensure waiter notification works
            transaction = transactionRepository.findByIdWithOrderAndTable(transactionId)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                            messageUtil.getMessage(MSG_TRANSACTION_NOT_FOUND, userLocale)));
            handleTransactionCancellation(transaction, authenticatedUser, userLocale);
            return ResponseDto.<TransactionCancellationRequestResponse>builder()
                    .message(messageUtil.getMessage("transaction.cancelled.directly", userLocale))
                    .data(buildTransactionCancellationRequestResponse(transaction))
                    .build();
        }
    }

    /**
     * Retrieves pending (OPEN) transaction cancellation requests for review.
     *
     * @param page     1-based page number
     * @param size     page size
     * @param userRole role name of the requester; only MANAGER/HQ_ADMIN are allowed
     * @return response wrapper containing a paginated list of pending cancellation requests
     * @throws ResponseStatusException if the role is not authorized to view requests
     */
    @Override
    @Transactional(readOnly = true)
    public ResponseDto<TransactionCancellationRequestListResponse> getPendingTransactionCancellationRequests(
            int page,
            int size,
            String userRole) {
        Locale userLocale = LocaleContextHolder.getLocale();

        // Only MANAGER and HQ_ADMIN can view cancellation requests
        if (!ROLE_MANAGER.equals(userRole) && !ROLE_HQ_ADMIN.equals(userRole)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    messageUtil.getMessage("transaction.cancellation.request.unauthorized", userLocale));
        }

        Pageable pageable = PageRequest.of(page - 1, size, Sort.by(Sort.Direction.DESC, "requestedAt"));
        Page<Transaction> transactionsPage = transactionRepository.findByRequestStatus(RequestStatus.OPEN, pageable);

        List<TransactionCancellationRequestResponse> transactionRequests = transactionsPage.getContent().stream()
                .map(this::buildTransactionCancellationRequestResponse)
                .collect(Collectors.toList());

        TransactionCancellationRequestListResponse.MetaData metaData = TransactionCancellationRequestListResponse.MetaData.builder()
                .page(page)
                .size(size)
                .totalPages(transactionsPage.getTotalPages())
                .totalRecords(transactionsPage.getTotalElements())
                .build();

        TransactionCancellationRequestListResponse response = TransactionCancellationRequestListResponse.builder()
                .transactionCancellationRequests(transactionRequests)
                .count(transactionRequests.size())
                .total(transactionsPage.getTotalElements())
                .metaData(metaData)
                .build();

        return ResponseDto.<TransactionCancellationRequestListResponse>builder()
                .message(messageUtil.getMessage("transaction.cancellation.requests.retrieved", userLocale))
                .data(response)
                .build();
    }

    /**
     * Check if transaction cancellation requires approval
     * All transactions (OPEN, PENDING, COMPLETED) require approval for cancellation
     */
    private boolean requiresTransactionCancellationApproval(TransactionStatus currentStatus) {
        return currentStatus == TransactionStatus.OPEN || 
               currentStatus == TransactionStatus.PENDING;
    }

    /**
     * Handle transaction cancellation request creation
     */
    private ResponseDto<TransactionCancellationRequestResponse> handleTransactionCancellationRequest(
            Transaction transaction,
            TransactionStatusPayload payload,
            User authenticatedUser,
            Locale userLocale) {
        // Check if there's already a pending request (either cancellation or refund)
        if (transaction.getRequestStatus() == RequestStatus.OPEN) {
            // Check if it's a refund request
            if (transaction.getRequestData() != null) {
                try {
                    ObjectMapper objectMapper = new ObjectMapper();
                    @SuppressWarnings("unchecked")
                    Map<String, Object> existingRequestData = objectMapper.readValue(transaction.getRequestData(), Map.class);
                    if (REQUEST_TYPE_REFUND.equals(existingRequestData.get(FIELD_REQUEST_TYPE))) {
                        throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                                messageUtil.getMessage("refund.request.already.pending", userLocale));
                    }
                } catch (JsonProcessingException e) {
                    // Invalid JSON, treat as cancellation request
                }
            }
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    messageUtil.getMessage("transaction.cancellation.request.already.pending", userLocale));
        }

        // Create cancellation request data
        try {
            ObjectMapper objectMapper = new ObjectMapper();
            TransactionCancellationRequestDto requestDto = TransactionCancellationRequestDto.builder()
                    .cancellationReason(payload.getReason())
                    .requestedStatus(payload.getTransactionStatus()) // Store the requested status (CANCELED)
                    .build();
            String requestData = objectMapper.writeValueAsString(requestDto);

            transaction.setRequestStatus(RequestStatus.OPEN);
            transaction.setRequestData(requestData);
            // Use UTC timezone to match the rest of the application
            OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
            transaction.setRequestedAt(now);
            transaction.setRequestedBy(authenticatedUser);
            // Clear review fields from previous APPROVED/DECLINED request to ensure this is treated as a new request
            transaction.setReviewedAt(null);
            transaction.setReviewedBy(null);
            transaction.setUpdatedAt(now);

            transactionRepository.save(transaction);

            // Create audit trail entry for cashier when creating pending cancellation request
            createCancellationRequestAuditTrail(authenticatedUser, transaction, payload.getReason());

            // Notify managers about newly opened cancellation request
            notifyManagersAboutRequest(transaction, userLocale);

            return ResponseDto.<TransactionCancellationRequestResponse>builder()
                    .message(messageUtil.getMessage("transaction.cancellation.request.created", userLocale))
                    .data(buildTransactionCancellationRequestResponse(transaction))
                    .build();

        } catch (JsonProcessingException e) {
            log.error("Error creating cancellation request data: {}", e.getMessage());
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    messageUtil.getMessage("transaction.cancellation.request.error", userLocale));
        }
    }

    /**
     * Handle direct transaction cancellation (no approval needed)
     * Also used by approval path to send waiter notification after cancellation is already processed.
     * If transaction is already canceled, this method will skip the cancellation logic and only send notifications.
     */
    private void handleTransactionCancellation(Transaction transaction, User authenticatedUser, Locale userLocale) {
        // Capture previous status before any mutation so we can make notification decisions based on it
        TransactionStatus previousStatus = transaction.getTransactionStatus();
        boolean alreadyCanceled = previousStatus == TransactionStatus.CANCELED;
        
        if (previousStatus == TransactionStatus.COMPLETED) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    messageUtil.getMessage("transaction.cancellation.not.allowed.completed", userLocale));
        }
        
        if (!alreadyCanceled) {
            log.info("Transaction {} cancelled directly for order {}", transaction.getId(), transaction.getOrder().getId());

            transaction.setTransactionStatus(TransactionStatus.CANCELED);
            // Use UTC timezone to match the rest of the application
            transaction.setUpdatedAt(OffsetDateTime.now(ZoneOffset.UTC));
            transactionRepository.save(transaction);

            // Cancel all orderedItems and orderedCombos in the order
            cancelAllOrderItemsAndCombos(transaction.getOrder(), authenticatedUser, userLocale);

            // Align order monetary totals with item-level cancellation policy (completed + takeaway/prepaid => skip)
            Order linkedOrder = transaction.getOrder();
            if (linkedOrder != null) {
                UUID orderId = linkedOrder.getId();
                Order freshOrder = orderRepository.findById(orderId).orElse(linkedOrder);
                RestaurantChainConfigProperties.RestaurantChainData chainCfg = restaurantChainConfigProperties.getChain();
                PaymentSystemType chainPt = chainCfg != null ? chainCfg.getPaymentType() : null;
                boolean skipAmounts = CancellationAmountPolicy.shouldSkipOrderAmountAdjustmentOnCancellation(
                        freshOrder.getOrderType(), chainPt, previousStatus);

                if (!skipAmounts) {
                    CancellationAmountPolicy.resetOrderMonetaryTotalsForFullCancellation(freshOrder);
                    log.info("Order {} monetary totals reset after transaction {} cancellation (policy: adjust amounts)",
                            orderId, transaction.getId());
                } else {
                    log.info("Order {} monetary totals unchanged after transaction {} cancellation (same skip policy as item no-deduction)",
                            orderId, transaction.getId());
                }
                freshOrder.setOrderStatus(OrderStatus.CANCELED);
                freshOrder.setUpdatedAt(OffsetDateTime.now(ZoneOffset.UTC));
                if (authenticatedUser != null) {
                    freshOrder.setUpdatedBy(authenticatedUser);
                }
                orderRepository.save(freshOrder);
                orderRepository.flush(); // Ensure order change is committed before alert evaluation
                log.info("Order {} persisted after transaction {} cancellation", orderId, transaction.getId());
                orderRecalculationService.maybeExpireTakeawaySessionWhenOrderServedAndTransactionCompleted(orderId);
            }

            // Create audit trail entry for user who cancelled the transaction
            try {
                auditTrailService.createAuditTrail(
                        authenticatedUser,
                        ActionType.CANCELLATION,
                        transaction.getRestaurant(),
                        RequestStatus.NA, // Direct cancellation doesn't require approval
                        null, // IP address not available
                        null, // User agent not available
                        transaction.getId(),
                        ACTION_TYPE_TRANSACTION,
                        "Transaction cancelled directly"
                );
            } catch (Exception e) {
                log.error("Failed to create audit trail for direct transaction cancellation: {}", e.getMessage(), e);
            }

            log.info("Transaction {} cancelled successfully", transaction.getId());

            // If this was a manager-driven direct cancellation of a PENDING transaction,
            // notify the associated cashier that the order was cancelled by manager.
            try {
                if (previousStatus == TransactionStatus.PENDING && transaction.getOrder() != null) {
                    // Collect all cashiers linked to this transaction/order
                    Set<User> cashiersToNotify = new HashSet<>();
                    
                    // Check if transaction has a cashier assigned
                    if (transaction.getCashier() != null && isCashier(transaction.getCashier())) {
                        cashiersToNotify.add(transaction.getCashier());
                    }
                    
                    // Check if transaction was requested by a cashier
                    if (transaction.getRequestedBy() != null && isCashier(transaction.getRequestedBy())) {
                        cashiersToNotify.add(transaction.getRequestedBy());
                    }
                    
                    // Notify all linked cashiers
                    if (!cashiersToNotify.isEmpty()) {
                        String managerComment = "Cancelled directly by manager";
                        notificationService.notifyCashiersOrderCancelledByManager(
                                transaction.getOrder(),
                                new ArrayList<>(cashiersToNotify),
                                managerComment,
                                userLocale);
                        log.info("Notified {} cashier(s) about direct cancellation of PENDING order {} by manager",
                                cashiersToNotify.size(), transaction.getOrder().getId());
                    } else {
                        log.debug("No cashiers linked to transaction {} (order {}) to notify about cancellation",
                                transaction.getId(), transaction.getOrder().getId());
                    }
                }
            } catch (Exception e) {
                log.error("Failed to notify cashier about direct order cancellation for transaction {}: {}",
                        transaction.getId(), e.getMessage(), e);
            }
        } else {
            log.debug("Transaction {} already canceled, skipping cancellation logic and only sending waiter notification", transaction.getId());
        }

        // Notify waiter about transaction cancellation, if a waiter is assigned
        // This is called for both direct cancellations and approved cancellations
        notifyWaiterAboutTransactionCancellation(transaction, userLocale);

        // ==================== REAL-TIME HQ ALERT EVALUATION ====================
        // Check if cancellation/refund/sales thresholds are breached after this transaction cancellation.
        // Must run AFTER transaction commits so the REQUIRES_NEW alert transaction can see the data.
        if (!alreadyCanceled) {
            evaluateAlertsAfterTransactionCommit(transaction.getRestaurant(), userLocale, "transaction cancellation");
        }
    }

    /**
     * Notify assigned waiter (if any) that a transaction for their table has been cancelled
     */
    private void notifyWaiterAboutTransactionCancellation(Transaction transaction, Locale userLocale) {
        try {
            log.info("Attempting to notify waiter about transaction cancellation for transaction: {}", transaction.getId());
            Order order = transaction.getOrder();
            if (order == null || order.getRestaurantTable() == null) {
                log.warn("No order or table associated with transaction {} for waiter notification. Order: {}, Table: {}", 
                        transaction.getId(), order != null ? order.getId() : "null", 
                        order != null && order.getRestaurantTable() != null ? order.getRestaurantTable().getId() : "null");
                return;
            }

            // Reuse the waiter assigned to the table if available
            RestaurantTable table = order.getRestaurantTable();
            log.info("Looking for waiter assignment for table {} (table order: {}) for transaction {}", 
                    table.getId(), table.getTableOrder(), transaction.getId());
            
            List<TableAssignment> tableAssignments = tableAssignmentRepository
                    .findByRestaurantTableIdAndUnassignedAtIsNullWithWaiter(table.getId());

            if (tableAssignments == null || tableAssignments.isEmpty()) {
                log.warn("No active waiter assignment found for table {} (table order: {}) for transaction {}", 
                        table.getId(), table.getTableOrder(), transaction.getId());
                return;
            }

            log.info("Found {} active waiter assignment(s) for table {}", tableAssignments.size(), table.getId());

            // Collect unique waiters (in case same waiter is assigned multiple times)
            Set<User> uniqueWaiters = new HashSet<>();
            for (TableAssignment assignment : tableAssignments) {
                User waiter = assignment.getWaiter();
                if (waiter != null) {
                    uniqueWaiters.add(waiter);
                } else {
                    log.warn("Table assignment {} has null waiter for table {} and transaction {}", 
                            assignment.getId(), table.getId(), transaction.getId());
                }
            }

            if (uniqueWaiters.isEmpty()) {
                log.warn("No valid waiters found in table assignments for table {} and transaction {}", 
                        table.getId(), transaction.getId());
                return;
            }

            log.info("Notifying {} unique waiter(s) about transaction cancellation for transaction {}", 
                    uniqueWaiters.size(), transaction.getId());

            int notifiedCount = notifyAssignedWaiters(uniqueWaiters, transaction, userLocale);

            log.info("Successfully notified {}/{} waiter(s) about transaction cancellation for transaction {}", 
                    notifiedCount, uniqueWaiters.size(), transaction.getId());
        } catch (Exception e) {
            log.error("Failed to notify waiter about transaction cancellation for transaction {}: {}", transaction.getId(), e.getMessage(), e);
        }
    }

    /**
     * Cancel all orderedItems and orderedCombos in an order when transaction is canceled
     */
    private void cancelAllOrderItemsAndCombos(Order order, User user, Locale userLocale) {
        if (order == null) {
            log.warn("Cannot cancel items/combos: order is null");
            return;
        }

        UUID orderId = order.getId();
        // Use UTC timezone to match the rest of the application
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);

        // Cancel all orderedItems (only regular items, not combo items)
        List<OrderedItem> orderedItems = orderedItemRepository.findByOrderId(orderId).stream()
                .filter(item -> item.getOrderedCombo() == null) // Only regular items
                .filter(item -> item.getItemStatus() != ItemStatus.CANCELED) // Skip already canceled
                .collect(Collectors.toList());

        for (OrderedItem item : orderedItems) {
            // Capture status before cancellation for wastage reporting
            // Only set wastage_source_status if current status is COOKING, READY, or SERVED
            if (item.getItemStatus() != null 
                    && item.getItemStatus() != ItemStatus.CANCELED
                    && (item.getItemStatus() == ItemStatus.COOKING 
                        || item.getItemStatus() == ItemStatus.READY 
                        || item.getItemStatus() == ItemStatus.SERVED)
                    && item.getWastageSourceStatus() == null) {
                item.setWastageSourceStatus(item.getItemStatus());
            }
            item.setItemStatus(ItemStatus.CANCELED);
            item.setUpdatedAt(now);
            item.setUpdatedBy(user);
            orderedItemRepository.save(item);
            log.info("Cancelled orderedItem {} for order {}", item.getId(), orderId);
            
            // Create audit trail for item cancellation
            try {
                Restaurant restaurant = order.getRestaurant();
                auditTrailService.createAuditTrail(
                        user,
                        ActionType.CANCELLATION,
                        restaurant,
                        RequestStatus.NA, // Direct cancellation doesn't require approval
                        null, // ipAddress
                        null, // userAgent
                        item.getId(),
                        "ITEM",
                        "Item cancelled due to transaction cancellation"
                );
            } catch (Exception e) {
                log.error("Failed to create audit trail for item cancellation (item {}): {}", item.getId(), e.getMessage(), e);
                // Don't break the flow if audit trail fails
            }
        }

        // Cancel all orderedCombos
        List<OrderedCombo> orderedCombos = orderedComboRepository.findByOrderId(orderId).stream()
                .filter(combo -> combo.getItemStatus() != ItemStatus.CANCELED) // Skip already canceled
                .collect(Collectors.toList());

        for (OrderedCombo combo : orderedCombos) {
            // Capture status before cancellation for wastage reporting
            // Only set wastage_source_status if current status is COOKING, READY, or SERVED
            if (combo.getItemStatus() != null 
                    && combo.getItemStatus() != ItemStatus.CANCELED
                    && (combo.getItemStatus() == ItemStatus.COOKING 
                        || combo.getItemStatus() == ItemStatus.READY 
                        || combo.getItemStatus() == ItemStatus.SERVED)
                    && combo.getWastageSourceStatus() == null) {
                combo.setWastageSourceStatus(combo.getItemStatus());
            }
            combo.setItemStatus(ItemStatus.CANCELED);
            combo.setUpdatedAt(now);
            combo.setUpdatedBy(user);
            orderedComboRepository.save(combo);
            log.info("Cancelled orderedCombo {} for order {}", combo.getId(), orderId);
        }

        // Set order status to CANCELED when transaction is canceled
        order.setOrderStatus(OrderStatus.CANCELED);
        order.setUpdatedAt(now);
        order.setUpdatedBy(user);
        orderRepository.save(order);
        orderRepository.flush(); // Ensure order status change is committed before alert evaluation
        log.info("Order {} status set to CANCELED after transaction cancellation", orderId);
    }

    private int notifyAssignedWaiters(Set<User> uniqueWaiters, Transaction transaction, Locale userLocale) {
        int notifiedCount = 0;
        for (User waiter : uniqueWaiters) {
            if (notifyWaiterCancellation(waiter, transaction, userLocale)) {
                notifiedCount++;
            }
        }
        return notifiedCount;
    }

    /**
     * Sends the waiter-scoped transaction cancellation notification; returns {@code false} when delivery throws.
     */
    private boolean notifyWaiterCancellation(User waiter, Transaction transaction, Locale userLocale) {
        try {
            log.info("Sending transaction cancellation notification to waiter {} ({} {}) for transaction {}",
                    waiter.getId(), waiter.getFirstName(), waiter.getLastName(), transaction.getId());
            notificationService.notifyTransactionCancelledForWaiter(transaction, waiter, userLocale);
            log.info("Successfully sent transaction cancellation notification to waiter {} for transaction {}",
                    waiter.getId(), transaction.getId());
            return true;
        } catch (Exception e) {
            log.error("Failed to send transaction cancellation notification to waiter {} for transaction {}: {}",
                    waiter.getId(), transaction.getId(), e.getMessage(), e);
            return false;
        }
    }

    /**
     * Build TransactionCancellationRequestResponse from Transaction entity
     */
    private TransactionCancellationRequestResponse buildTransactionCancellationRequestResponse(Transaction transaction) {
        Locale userLocale = LocaleContextHolder.getLocale();

        String cancellationReason = null;
        TransactionStatus requestedStatus = null;

        // Parse cancellation request data if available
        if (transaction.getRequestData() != null) {
            try {
                ObjectMapper objectMapper = new ObjectMapper();
                TransactionCancellationRequestDto requestDto = objectMapper.readValue(
                        transaction.getRequestData(), TransactionCancellationRequestDto.class);
                cancellationReason = requestDto.getCancellationReason();
                requestedStatus = requestDto.getRequestedStatus();
            } catch (JsonProcessingException e) {
                log.error("Error parsing cancellation request data for transaction {}: {}", transaction.getId(), e.getMessage());
            }
        }

        // Get role name for requestedBy user
        String requestedByRole = null;
        if (transaction.getRequestedBy() != null && transaction.getRequestedBy().getRoleId() != null) {
            var role = roleRepository.findById(transaction.getRequestedBy().getRoleId()).orElse(null);
            if (role != null) {
                requestedByRole = role.getName();
            }
        }
        
        String currency = restaurantChainConfigProperties.getChain() != null ? restaurantChainConfigProperties.getChain().getCurrency() : null;
        return TransactionCancellationRequestResponse.builder()
                .transactionId(transaction.getId())
                .orderId(transaction.getOrder() != null ? transaction.getOrder().getId() : null)
                .orderNumber(transaction.getOrder() != null ? transaction.getOrder().getOrderNumber() : null)
                .transactionNumber(transaction.getTransactionNumber())
                .paymentMethod(transaction.getPaymentMethod())
                .paymentApp(transaction.getPaymentApp())
                .transactionAmount(transaction.getTransactionAmount() != null ? CurrencyFormatter.formatAmount(transaction.getTransactionAmount(), currency) : null)
                .currentTransactionStatus(transaction.getTransactionStatus())
                .cancellationReason(cancellationReason)
                .requestStatus(transaction.getRequestStatus())
                .requestedAt(transaction.getRequestedAt() != null ? transaction.getRequestedAt().toLocalDateTime() : null)
                .requestedBy(transaction.getRequestedBy() != null ? transaction.getRequestedBy().getId() : null)
                .requestedByName(transaction.getRequestedBy() != null ?
                        transaction.getRequestedBy().getFirstName() + " " + transaction.getRequestedBy().getLastName() : null)
                .requestedByRole(requestedByRole)
                .reviewedAt(transaction.getReviewedAt() != null ? transaction.getReviewedAt().toLocalDateTime() : null)
                .reviewedBy(transaction.getReviewedBy() != null ? transaction.getReviewedBy().getId() : null)
                .reviewedByName(transaction.getReviewedBy() != null ?
                        transaction.getReviewedBy().getFirstName() + " " + transaction.getReviewedBy().getLastName() : null)
                .comments(transaction.getRequestComments())
                .build();
    }
    /**
     * Initiates a refund for a transaction.
     * <p>
     * Validates the transaction and order, processes the requested refund scope (FULL/PARTIAL),
     * computes refund amounts using the same pricing rules used during order pricing (discounts, charges, and tax),
     * and then either processes immediately (MANAGER/HQ_ADMIN) or creates an approval request for managers.
     * </p>
     *
     * @param userId        authenticated user id
     * @param userRole      role of the user initiating the refund
     * @param transactionId the transaction identifier to refund
     * @param request       refund request payload (type, items/combos for partial refunds, and reason)
     * @return response wrapper containing the raised refund request result (approved or pending)
     * @throws ResponseStatusException if validation fails (missing entities, invalid quantities, invalid status)
     */
    @Override
    @Transactional
    public ResponseDto<RaiseRefundRequestResponse> initiateRefund(
            String userId,
            String userRole,
            UUID transactionId,
            RaiseRefundRequest request) {
        Locale userLocale = LocaleContextHolder.getLocale();

        // Validate transaction and user
        Transaction transaction = validateAndGetTransaction(transactionId, userLocale);
        User user = userRepository.findById(UUID.fromString(userId))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        messageUtil.getMessage(MSG_USER_NOT_FOUND, userLocale)));
        Order order = validateOrder(transaction, userLocale);

        // Get refund type (mandatory in request)
        RefundType refundType = request.getRefundType();

        // Process refund items and calculate amounts
        RefundCalculationResult calculationResult = processRefundItems(
                request, order, refundType, userLocale);

        // Calculate refund amounts using same pricing rules as order calculation
        String currency = restaurantChainConfigProperties.getChain() != null ? restaurantChainConfigProperties.getChain().getCurrency() : null;
        RefundAmounts refundAmounts = calculateRefundAmountsUsingPricingRules(
                calculationResult, order, currency);

        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        boolean hasPermission = ROLE_MANAGER.equals(userRole) || ROLE_HQ_ADMIN.equals(userRole);

        if (hasPermission) {
            return processRefundDirectly(transaction, order, user, userRole, refundType, request,
                    calculationResult, refundAmounts, now, userLocale);
        } else {
            return createRefundRequest(transaction, order, user, userRole, refundType, request,
                    calculationResult, refundAmounts, now, userLocale);
        }
    }

    // Helper methods for validation and processing
    /**
     * Loads and validates a transaction for refund processing.
     * <p>
     * Enforces refund eligibility based on transaction status and blocks creating a new refund when an OPEN
     * request exists whose {@code requestType} is {@code REFUND}.
     * Cancellation requests (or non-REFUND request data) do not block refunds.
     * </p>
     *
     * @param transactionId the transaction identifier
     * @param userLocale    locale used for localized error messages
     * @return the validated {@link Transaction}
     * @throws ResponseStatusException if the transaction is not found, not eligible for refund, or a refund is already pending
     */
    private Transaction validateAndGetTransaction(UUID transactionId, Locale userLocale) {
        Transaction transaction = transactionRepository.findById(transactionId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        messageUtil.getMessage(MSG_TRANSACTION_NOT_FOUND, userLocale)));

        Order order = transaction.getOrder();

        // Refunds are only allowed for completed (or partially refunded) transactions.
        log.info("Refund validation - transactionId={}, status={}, orderType={}",
                transaction.getId(),
                transaction.getTransactionStatus(),
                order != null ? order.getOrderType() : null);

        if (transaction.getTransactionStatus() != TransactionStatus.COMPLETED
                && transaction.getTransactionStatus() != TransactionStatus.PARTIALLY_REFUNDED) {
            log.warn("Refund blocked - transactionId={} has status {} (not COMPLETED/PARTIALLY_REFUNDED)",
                    transaction.getId(), transaction.getTransactionStatus());
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    messageUtil.getMessage("refund.transaction.must.be.completed", userLocale));
        }

        // Check if there's already a pending refund request
        // Note: Cancellation requests (or declined requests) should not prevent refund requests
        // Only block if there's an OPEN request with requestType="REFUND"
        if (transaction.getRequestStatus() == RequestStatus.OPEN && transaction.getRequestData() != null) {
            try {
                ObjectMapper objectMapper = new ObjectMapper();
                Map<String, Object> existingRequestData = objectMapper.readValue(transaction.getRequestData(), Map.class);
                String requestType = (String) existingRequestData.get(FIELD_REQUEST_TYPE);
                // Only block if it's a pending REFUND request
                // Cancellation requests (or requests without requestType) should not block refunds
                if (REQUEST_TYPE_REFUND.equals(requestType)) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                            messageUtil.getMessage("refund.request.already.pending", userLocale));
                }
                // If requestType is not "REFUND" (e.g., cancellation request), allow refund to proceed
            } catch (JsonProcessingException e) {
                // If requestData is not valid JSON, allow refund to proceed
                // This handles old format requests or corrupted data
            }
        }
        // If requestStatus is not OPEN (e.g., DECLINED, APPROVED, NONE), allow refund to proceed

        return transaction;
    }

    private Order validateOrder(Transaction transaction, Locale userLocale) {
        Order order = transaction.getOrder();
        if (order == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    messageUtil.getMessage("refund.order.not.found", userLocale));
        }
        return order;
    }


    // Inner class to hold calculation results
    private static class RefundCalculationResult {
        private final BigDecimal subtotalRefundAmount;
        private final List<RaiseRefundRequestResponse.OrderedItemRefundResponse> orderedItemsResponse;
        private final List<RaiseRefundRequestResponse.OrderedComboRefundResponse> orderedCombosResponse;
        private final List<Map<String, Object>> orderedItemsJson;
        private final List<Map<String, Object>> orderedCombosJson;

        public RefundCalculationResult(BigDecimal subtotalRefundAmount,
                                     List<RaiseRefundRequestResponse.OrderedItemRefundResponse> orderedItemsResponse,
                                     List<RaiseRefundRequestResponse.OrderedComboRefundResponse> orderedCombosResponse,
                                     List<Map<String, Object>> orderedItemsJson,
                                     List<Map<String, Object>> orderedCombosJson) {
            this.subtotalRefundAmount = subtotalRefundAmount;
            this.orderedItemsResponse = orderedItemsResponse;
            this.orderedCombosResponse = orderedCombosResponse;
            this.orderedItemsJson = orderedItemsJson;
            this.orderedCombosJson = orderedCombosJson;
        }

        public BigDecimal getSubtotalRefundAmount() { return subtotalRefundAmount; }
        public List<RaiseRefundRequestResponse.OrderedItemRefundResponse> getOrderedItemsResponse() { return orderedItemsResponse; }
        public List<RaiseRefundRequestResponse.OrderedComboRefundResponse> getOrderedCombosResponse() { return orderedCombosResponse; }
        public List<Map<String, Object>> getOrderedItemsJson() { return orderedItemsJson; }
        public List<Map<String, Object>> getOrderedCombosJson() { return orderedCombosJson; }
    }

    /**
     * Value object holding the calculated monetary components for a refund.
     * <p>
     * Values are derived from selected refund items/combos and then adjusted using the same pricing rules
     * as the order (discount allocation, charges, and tax split).
     * </p>
     */
    private static class RefundAmounts {
        private final BigDecimal totalRefundAmount;
        private final BigDecimal subtotalRefundAmount;
        private final BigDecimal taxRefundAmount;
        private final BigDecimal alcoholicTaxRefundAmount;
        private final BigDecimal nonAlcoholicTaxRefundAmount;
        private final BigDecimal alcoholicTaxableRefundAmount;
        private final BigDecimal nonAlcoholicTaxableRefundAmount;
        private final BigDecimal serviceChargeRefundAmount;
        private final BigDecimal packingChargeRefundAmount;
        private final BigDecimal discountRefundAmount;
        private final BigDecimal additionalDiscountRefundAmount;

        /**
         * Creates a {@link RefundAmounts} container.
         *
         * @param totalRefundAmount              grand total to refund (after charges/tax and additional discount)
         * @param subtotalRefundAmount            subtotal component selected for refund (before charges/tax)
         * @param taxRefundAmount                 total consumption tax to refund
         * @param alcoholicTaxRefundAmount        alcoholic tax portion of the refund
         * @param nonAlcoholicTaxRefundAmount     non-alcoholic tax portion of the refund
         * @param alcoholicTaxableRefundAmount    taxable base amount for alcoholic items/charges
         * @param nonAlcoholicTaxableRefundAmount taxable base amount for non-alcoholic items/charges
         * @param serviceChargeRefundAmount       service charge component to refund (dine-in)
         * @param packingChargeRefundAmount       packing charge component to refund (takeaway, when enabled)
         * @param discountRefundAmount            allocated order-level discount portion to refund
         * @param additionalDiscountRefundAmount  allocated additional discount portion to refund
         */
        public RefundAmounts(BigDecimal totalRefundAmount, BigDecimal subtotalRefundAmount,
                           BigDecimal taxRefundAmount, BigDecimal alcoholicTaxRefundAmount,
                           BigDecimal nonAlcoholicTaxRefundAmount,
                           BigDecimal alcoholicTaxableRefundAmount, BigDecimal nonAlcoholicTaxableRefundAmount,
                           BigDecimal serviceChargeRefundAmount, BigDecimal packingChargeRefundAmount,
                           BigDecimal discountRefundAmount, BigDecimal additionalDiscountRefundAmount) {
            this.totalRefundAmount = totalRefundAmount;
            this.subtotalRefundAmount = subtotalRefundAmount;
            this.taxRefundAmount = taxRefundAmount;
            this.alcoholicTaxRefundAmount = alcoholicTaxRefundAmount;
            this.nonAlcoholicTaxRefundAmount = nonAlcoholicTaxRefundAmount;
            this.alcoholicTaxableRefundAmount = alcoholicTaxableRefundAmount;
            this.nonAlcoholicTaxableRefundAmount = nonAlcoholicTaxableRefundAmount;
            this.serviceChargeRefundAmount = serviceChargeRefundAmount;
            this.packingChargeRefundAmount = packingChargeRefundAmount;
            this.discountRefundAmount = discountRefundAmount;
            this.additionalDiscountRefundAmount = additionalDiscountRefundAmount;
        }

        public BigDecimal getTotalRefundAmount() { return totalRefundAmount; }
        public BigDecimal getSubtotalRefundAmount() { return subtotalRefundAmount; }
        public BigDecimal getTaxRefundAmount() { return taxRefundAmount; }
        public BigDecimal getAlcoholicTaxRefundAmount() { return alcoholicTaxRefundAmount; }
        public BigDecimal getNonAlcoholicTaxRefundAmount() { return nonAlcoholicTaxRefundAmount; }
        public BigDecimal getAlcoholicTaxableRefundAmount() { return alcoholicTaxableRefundAmount; }
        public BigDecimal getNonAlcoholicTaxableRefundAmount() { return nonAlcoholicTaxableRefundAmount; }
        public BigDecimal getServiceChargeRefundAmount() { return serviceChargeRefundAmount; }
        public BigDecimal getPackingChargeRefundAmount() { return packingChargeRefundAmount; }
        public BigDecimal getDiscountRefundAmount() { return discountRefundAmount; }
        public BigDecimal getAdditionalDiscountRefundAmount() { return additionalDiscountRefundAmount; }
    }

    /**
     * Processes refund request items/combos and computes the raw subtotal refund amount.
     * <p>
     * For FULL refunds it includes all eligible ordered items (excluding combo items) and combos.
     * For PARTIAL refunds it validates requested item/combo ids, ensures they belong to the transaction's order,
     * validates quantities, and rejects refunding combo items separately.
     * </p>
     *
     * @param request    the incoming refund request
     * @param order      the order associated with the transaction
     * @param refundType refund scope (FULL or PARTIAL)
     * @param userLocale locale used for localized validation errors
     * @return calculation result containing subtotal and DTO/JSON representations of selected items/combos
     * @throws ResponseStatusException when request is invalid (missing items for partial, invalid ids, invalid quantities)
     */
    private RefundCalculationResult processRefundItems(RaiseRefundRequest request, Order order,
                                                      RefundType refundType, Locale userLocale) {
        BigDecimal subtotalRefundAmount = BigDecimal.ZERO;
        List<RaiseRefundRequestResponse.OrderedItemRefundResponse> orderedItemsResponse = new ArrayList<>();
        List<RaiseRefundRequestResponse.OrderedComboRefundResponse> orderedCombosResponse = new ArrayList<>();
        List<Map<String, Object>> orderedItemsJson = new ArrayList<>();
        List<Map<String, Object>> orderedCombosJson = new ArrayList<>();

        if (refundType == RefundType.FULL) {
            // Process all items and combos
            List<OrderedItem> allOrderedItems = orderedItemRepository.findByOrderId(order.getId());
            List<OrderedCombo> allOrderedCombos = orderedComboRepository.findByOrderId(order.getId());

            for (OrderedItem orderedItem : allOrderedItems) {
                if (isEligibleFullRefundItem(orderedItem)) {
                    BigDecimal itemAmount = processItem(orderedItem, orderedItem.getQuantity(), null, userLocale,
                            orderedItemsResponse, orderedItemsJson);
                    subtotalRefundAmount = subtotalRefundAmount.add(itemAmount);
                }
            }

            for (OrderedCombo orderedCombo : allOrderedCombos) {
                if (isEligibleFullRefundCombo(orderedCombo)) {
                    BigDecimal comboAmount = processCombo(orderedCombo, orderedCombo.getQuantity(), null, userLocale,
                            orderedCombosResponse, orderedCombosJson);
                    subtotalRefundAmount = subtotalRefundAmount.add(comboAmount);
                }
            }
        } else {
            // PARTIAL refund: validate and process requested items
            if ((request.getOrderedItems() == null || request.getOrderedItems().isEmpty()) &&
                (request.getOrderedCombos() == null || request.getOrderedCombos().isEmpty())) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        messageUtil.getMessage("refund.request.items.required", userLocale));
            }

            if (request.getOrderedItems() != null) {
                for (RaiseRefundRequest.OrderedItemRefund itemRequest : request.getOrderedItems()) {
                    OrderedItem orderedItem = orderedItemRepository.findById(itemRequest.getOrderedItemId())
                            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                                    messageUtil.getMessage("ordered.item.not.found", userLocale)));
                    validateItemBelongsToOrder(orderedItem, order, userLocale);
                    // Validate that the item is not a combo item - combo items should only be refunded as part of the combo
                    if (orderedItem.getOrderedCombo() != null) {
                        throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                                messageUtil.getMessage("refund.combo.item.cannot.be.refunded.separately", userLocale));
                    }
                    // Payment-aware rule for cancelled items:
                    // If an item is cancelled, it can be refunded ONLY if it was included in a completed payment.
                    if (orderedItem.getItemStatus() == ItemStatus.CANCELED
                            && !Boolean.TRUE.equals(orderedItem.getIncludedInPayment())) {
                        throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                                messageUtil.getMessage("refund.canceled.item.not.included.in.payment", userLocale));
                    }
                    validateQuantity(itemRequest.getQuantity(), orderedItem.getQuantity(), userLocale);
                    BigDecimal itemAmount = processItem(orderedItem, itemRequest.getQuantity(), itemRequest.getItemReason(), userLocale,
                            orderedItemsResponse, orderedItemsJson);
                    subtotalRefundAmount = subtotalRefundAmount.add(itemAmount);
                }
            }

            if (request.getOrderedCombos() != null) {
                for (RaiseRefundRequest.OrderedComboRefund comboRequest : request.getOrderedCombos()) {
                    OrderedCombo orderedCombo = orderedComboRepository.findById(comboRequest.getOrderedComboId())
                            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                                    messageUtil.getMessage("ordered.combo.not.found", userLocale)));
                    validateComboBelongsToOrder(orderedCombo, order, userLocale);
                    // Payment-aware rule for cancelled combos:
                    // If a combo is cancelled, it can be refunded ONLY if it was included in a completed payment.
                    if (orderedCombo.getItemStatus() == ItemStatus.CANCELED
                            && !Boolean.TRUE.equals(orderedCombo.getIncludedInPayment())) {
                        throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                                messageUtil.getMessage("refund.canceled.item.not.included.in.payment", userLocale));
                    }
                    validateQuantity(comboRequest.getQuantity(), orderedCombo.getQuantity(), userLocale);
                    BigDecimal comboAmount = processCombo(orderedCombo, comboRequest.getQuantity(), comboRequest.getItemReason(), userLocale,
                            orderedCombosResponse, orderedCombosJson);
                    subtotalRefundAmount = subtotalRefundAmount.add(comboAmount);
                }
            }
        }

        return new RefundCalculationResult(subtotalRefundAmount, orderedItemsResponse, orderedCombosResponse,
                orderedItemsJson, orderedCombosJson);
    }

    /**
     * Builds both the API response representation and request-data JSON representation for an ordered item refund entry.
     *
     * @param orderedItem          the ordered item to refund
     * @param refundQuantity       quantity being refunded
     * @param itemReason           optional per-item reason
     * @param userLocale           locale used to resolve the translated item name
     * @param orderedItemsResponse mutable list to append the structured response entry to
     * @param orderedItemsJson     mutable list to append the JSON-map entry to (stored in {@code Transaction.requestData})
     * @return calculated refund amount for this item (possibly prorated by quantity)
     */
    private BigDecimal processItem(OrderedItem orderedItem, Integer refundQuantity, String itemReason,
                           Locale userLocale,
                           List<RaiseRefundRequestResponse.OrderedItemRefundResponse> orderedItemsResponse,
                           List<Map<String, Object>> orderedItemsJson) {
        BigDecimal itemRefundAmount = calculateItemRefundAmount(orderedItem, refundQuantity);

        String itemName = getItemName(orderedItem.getItem(), userLocale);
        String imageUrl = orderedItem.getItem() != null ? awsService.getFullUrl(orderedItem.getItem().getImageUrl()) : null;

        orderedItemsResponse.add(RaiseRefundRequestResponse.OrderedItemRefundResponse.builder()
                .orderedItemId(orderedItem.getId())
                .quantity(refundQuantity)
                        .refundAmount(itemRefundAmount)
                .imageUrl(imageUrl)
                        .build());

        Map<String, Object> itemJson = buildItemJson(orderedItem, refundQuantity, itemRefundAmount, itemName, imageUrl, itemReason);
        orderedItemsJson.add(itemJson);
        
        return itemRefundAmount;
    }

    /**
     * Builds both the API response representation and request-data JSON representation for an ordered combo refund entry.
     *
     * @param orderedCombo         the ordered combo to refund
     * @param refundQuantity       quantity being refunded
     * @param itemReason           optional per-combo reason
     * @param userLocale           locale used to resolve the translated combo name
     * @param orderedCombosResponse mutable list to append the structured response entry to
     * @param orderedCombosJson    mutable list to append the JSON-map entry to (stored in {@code Transaction.requestData})
     * @return calculated refund amount for this combo (possibly prorated by quantity)
     */
    private BigDecimal processCombo(OrderedCombo orderedCombo, Integer refundQuantity, String itemReason,
                             Locale userLocale,
                             List<RaiseRefundRequestResponse.OrderedComboRefundResponse> orderedCombosResponse,
                             List<Map<String, Object>> orderedCombosJson) {
        BigDecimal comboRefundAmount = calculateComboRefundAmount(orderedCombo, refundQuantity);

        String comboName = getComboName(orderedCombo.getCombo(), userLocale);
        String imageUrl = orderedCombo.getCombo() != null ? awsService.getFullUrl(orderedCombo.getCombo().getComboImageUrl()) : null;

        orderedCombosResponse.add(RaiseRefundRequestResponse.OrderedComboRefundResponse.builder()
                .orderedComboId(orderedCombo.getId())
                .quantity(refundQuantity)
                .refundAmount(comboRefundAmount)
                .imageUrl(imageUrl)
                .build());

        Map<String, Object> comboJson = buildComboJson(orderedCombo, refundQuantity, comboRefundAmount, comboName, imageUrl, itemReason);
        orderedCombosJson.add(comboJson);
        
        return comboRefundAmount;
    }

    /**
     * Calculates the refund amount for an ordered item, prorating by quantity when needed.
     * <p>
     * Prefers total discounted amount when available, otherwise falls back to total amount or unit price.
     * </p>
     *
     * @param orderedItem    the ordered item
     * @param refundQuantity quantity to refund (must be {@code > 0})
     * @return refund amount for the requested quantity
     */
    private BigDecimal calculateItemRefundAmount(OrderedItem orderedItem, Integer refundQuantity) {
        BigDecimal itemRefundAmount = BigDecimal.ZERO;
        if (orderedItem.getTotalDiscountedItemAmount() != null) {
            itemRefundAmount = orderedItem.getTotalDiscountedItemAmount();
        } else if (orderedItem.getTotalItemAmount() != null) {
            itemRefundAmount = orderedItem.getTotalItemAmount();
        } else if (orderedItem.getPrice() != null) {
            itemRefundAmount = orderedItem.getPrice();
        }

        if (refundQuantity < orderedItem.getQuantity()) {
            BigDecimal unitPrice = itemRefundAmount.divide(BigDecimal.valueOf(orderedItem.getQuantity()), 2, RoundingMode.HALF_UP);
            itemRefundAmount = unitPrice.multiply(BigDecimal.valueOf(refundQuantity));
        }
        return itemRefundAmount;
    }

    /**
     * Calculates the refund amount for an ordered combo, prorating by quantity when needed.
     *
     * @param orderedCombo   the ordered combo
     * @param refundQuantity quantity to refund (must be {@code > 0})
     * @return refund amount for the requested quantity
     */
    private BigDecimal calculateComboRefundAmount(OrderedCombo orderedCombo, Integer refundQuantity) {
        BigDecimal fallbackPrice = BigDecimal.ZERO;
        if (orderedCombo.getPrice() != null) {
            fallbackPrice = orderedCombo.getPrice();
        }
        BigDecimal comboRefundAmount = orderedCombo.getTotalComboAmount() != null
                ? orderedCombo.getTotalComboAmount()
                : fallbackPrice;

        if (refundQuantity < orderedCombo.getQuantity()) {
            BigDecimal unitPrice = comboRefundAmount.divide(BigDecimal.valueOf(orderedCombo.getQuantity()), 2, RoundingMode.HALF_UP);
            comboRefundAmount = unitPrice.multiply(BigDecimal.valueOf(refundQuantity));
        }
        return comboRefundAmount;
    }

    /**
     * Builds a JSON-serializable map describing a refunded ordered item for storing in request metadata.
     *
     * @param orderedItem      ordered item being refunded
     * @param refundQuantity   quantity refunded
     * @param itemRefundAmount computed refund amount
     * @param itemName         resolved item display name
     * @param imageUrl         resolved absolute image URL (may be {@code null})
     * @param itemReason       optional reason for this item
     * @return map suitable for JSON serialization
     */
    private Map<String, Object> buildItemJson(OrderedItem orderedItem, Integer refundQuantity,
                                            BigDecimal itemRefundAmount, String itemName, String imageUrl, String itemReason) {
        Map<String, Object> itemJson = new HashMap<>();
        itemJson.put("orderedItemId", orderedItem.getId().toString());
        itemJson.put("quantity", refundQuantity);
        itemJson.put("itemName", itemName);
        itemJson.put("originalQuantity", orderedItem.getQuantity());
        itemJson.put("refundQuantity", refundQuantity);
        itemJson.put("unitPrice", orderedItem.getPrice() != null ? orderedItem.getPrice() : BigDecimal.ZERO);
        itemJson.put("totalItemAmount", itemRefundAmount);
        itemJson.put(FIELD_REFUND_AMOUNT, itemRefundAmount);
        itemJson.put("imageUrl", imageUrl);
        if (itemReason != null) {
            itemJson.put("itemReason", itemReason);
        }
        return itemJson;
    }

    /**
     * Builds a JSON-serializable map describing a refunded ordered combo for storing in request metadata.
     *
     * @param orderedCombo      ordered combo being refunded
     * @param refundQuantity    quantity refunded
     * @param comboRefundAmount computed refund amount
     * @param comboName         resolved combo display name
     * @param imageUrl          resolved absolute image URL (may be {@code null})
     * @param itemReason        optional reason for this combo
     * @return map suitable for JSON serialization
     */
    private Map<String, Object> buildComboJson(OrderedCombo orderedCombo, Integer refundQuantity,
                                              BigDecimal comboRefundAmount, String comboName, String imageUrl, String itemReason) {
        Map<String, Object> comboJson = new HashMap<>();
        comboJson.put("orderedComboId", orderedCombo.getId().toString());
        comboJson.put("quantity", refundQuantity);
        comboJson.put("comboName", comboName);
        comboJson.put("originalQuantity", orderedCombo.getQuantity());
        comboJson.put("refundQuantity", refundQuantity);
        comboJson.put("unitPrice", orderedCombo.getPrice() != null ? orderedCombo.getPrice() : BigDecimal.ZERO);
        comboJson.put("totalComboAmount", comboRefundAmount);
        comboJson.put(FIELD_REFUND_AMOUNT, comboRefundAmount);
        comboJson.put("imageUrl", imageUrl);
        if (itemReason != null) {
            comboJson.put("itemReason", itemReason);
        }
        return comboJson;
    }

    private void validateItemBelongsToOrder(OrderedItem orderedItem, Order order, Locale userLocale) {
        if (!orderedItem.getOrder().getId().equals(order.getId())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    messageUtil.getMessage("refund.item.not.belongs.to.order", userLocale));
        }
    }

    private void validateComboBelongsToOrder(OrderedCombo orderedCombo, Order order, Locale userLocale) {
        if (!orderedCombo.getOrder().getId().equals(order.getId())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    messageUtil.getMessage("refund.combo.not.belongs.to.order", userLocale));
        }
    }

    private void validateQuantity(Integer refundQuantity, Integer originalQuantity, Locale userLocale) {
        if (refundQuantity <= 0 || refundQuantity > originalQuantity) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    messageUtil.getMessage("refund.invalid.quantity", userLocale));
        }
    }

    /**
     * Calculates detailed refund amounts using the same pricing rules applied during order pricing.
     * <p>
     * Allocates discounts proportionally, recalculates charges (service/packing) based on the discounted subtotal,
     * splits alcoholic vs non-alcoholic taxable bases from the selected refund items/combos, and calculates tax using
     * chain configuration for the order type. Values are normalized using the currency's decimal places.
     * </p>
     *
     * @param calculationResult raw refund selection results (selected items/combos and subtotal)
     * @param order             the original order (source of discounts and configuration context)
     * @param currency          currency code used for rounding/formatting rules
     * @return aggregated refund monetary components
     */
    private RefundAmounts calculateRefundAmountsUsingPricingRules(RefundCalculationResult calculationResult, Order order, String currency) {
        BigDecimal subtotalRefundAmount = calculationResult.getSubtotalRefundAmount() != null ? calculationResult.getSubtotalRefundAmount() : BigDecimal.ZERO;
        BigDecimal orderSubtotal = order.getSubTotal() != null ? order.getSubTotal() : BigDecimal.ZERO;

        BigDecimal refundRatio = BigDecimal.ZERO;
        if (orderSubtotal.compareTo(BigDecimal.ZERO) > 0 && subtotalRefundAmount.compareTo(BigDecimal.ZERO) > 0) {
            refundRatio = subtotalRefundAmount.divide(orderSubtotal, 4, RoundingMode.HALF_UP);
        }

        // Proportional discounts (keep existing behavior)
        BigDecimal discountRefundAmount = order.getDiscountAmount() != null
                ? CurrencyFormatter.formatAmount(order.getDiscountAmount().multiply(refundRatio), currency)
                : BigDecimal.ZERO;
        BigDecimal additionalDiscountRefundAmount = order.getAdditionalDiscountAmount() != null
                ? CurrencyFormatter.formatAmount(order.getAdditionalDiscountAmount().multiply(refundRatio), currency)
                : BigDecimal.ZERO;

        BigDecimal discountedSubtotalRefundAmount = subtotalRefundAmount.subtract(discountRefundAmount);
        if (discountedSubtotalRefundAmount.compareTo(BigDecimal.ZERO) < 0) discountedSubtotalRefundAmount = BigDecimal.ZERO;

        // Alcoholic/non-alcoholic split based on selected refund items/combos
        BigDecimal alcoholicSubtotal = BigDecimal.ZERO;
        BigDecimal nonAlcoholicSubtotal = BigDecimal.ZERO;

        if (calculationResult.getOrderedItemsResponse() != null) {
            for (RaiseRefundRequestResponse.OrderedItemRefundResponse itemResp : calculationResult.getOrderedItemsResponse()) {
                OrderedItem orderedItem = findRefundOrderedItem(itemResp);
                if (orderedItem != null) {
                    com.gulfnet.shared_library.enums.AlcoholType alcoholType = orderedItem.getAlcoholType();
                    if (alcoholType == null && orderedItem.getItem() != null) {
                        alcoholType = orderedItem.getItem().getAlcoholType();
                    }
                    if (alcoholType == com.gulfnet.shared_library.enums.AlcoholType.ALCOHOLIC) {
                        alcoholicSubtotal = alcoholicSubtotal.add(itemResp.getRefundAmount());
                    } else {
                        nonAlcoholicSubtotal = nonAlcoholicSubtotal.add(itemResp.getRefundAmount());
                    }
                }
            }
        }

        // Combos - include in alcoholic/non-alcoholic breakdown
        if (calculationResult.getOrderedCombosResponse() != null) {
            for (RaiseRefundRequestResponse.OrderedComboRefundResponse comboResp : calculationResult.getOrderedCombosResponse()) {
                OrderedCombo orderedCombo = findRefundOrderedCombo(comboResp);
                if (orderedCombo != null) {
                    Integer refundQuantity = comboResp.getQuantity() != null ? comboResp.getQuantity() : orderedCombo.getQuantity();

                    List<ComboItemTaxBreakdown> comboItemBreakdowns = extractComboItemTaxBreakdown(
                            orderedCombo, refundQuantity, currency);

                    for (ComboItemTaxBreakdown itemBreakdown : comboItemBreakdowns) {
                        if (hasBreakdownAmount(itemBreakdown)) {
                            if (itemBreakdown.alcoholType == AlcoholType.ALCOHOLIC) {
                                alcoholicSubtotal = alcoholicSubtotal.add(itemBreakdown.amount);
                            } else {
                                nonAlcoholicSubtotal = nonAlcoholicSubtotal.add(itemBreakdown.amount);
                            }
                        }
                    }
                }
            }
        }

        BigDecimal splitTotal = alcoholicSubtotal.add(nonAlcoholicSubtotal);

        // Scale subtotals to match discounted refund subtotal (items + combos)
        // The splitTotal now includes both items and combo effective item amounts
        if (splitTotal.compareTo(BigDecimal.ZERO) > 0 && discountedSubtotalRefundAmount.compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal scaleFactor = discountedSubtotalRefundAmount.divide(splitTotal, 10, RoundingMode.HALF_UP);
            alcoholicSubtotal = alcoholicSubtotal.multiply(scaleFactor);
            nonAlcoholicSubtotal = nonAlcoholicSubtotal.multiply(scaleFactor);
        } else {
            alcoholicSubtotal = BigDecimal.ZERO;
            nonAlcoholicSubtotal = discountedSubtotalRefundAmount;
        }

        // Charges based on order type
        BigDecimal serviceChargeRefundAmount = BigDecimal.ZERO;
        BigDecimal packingChargeRefundAmount = BigDecimal.ZERO;
        if (order.getOrderType() == OrderType.DINE_IN) {
            RestaurantChainConfigProperties.ServiceChargesForDineIn service = restaurantChainConfigProperties.getChain() != null
                    ? restaurantChainConfigProperties.getChain().getServiceChargesForDineIn()
                    : null;
            if (service != null) {
                serviceChargeRefundAmount = calculateChargeAmount(discountedSubtotalRefundAmount, BigDecimal.valueOf(service.getValue()), service.getType(), currency);
            }
        } else if (order.getOrderType() == OrderType.TAKEAWAY
                && restaurantChainConfigProperties.getChain() != null
                && Boolean.TRUE.equals(restaurantChainConfigProperties.getChain().isIncludePackingChargesForTakeaway())
                && restaurantChainConfigProperties.getChain().getPackingChargesForTakeaway() != null) {
            RestaurantChainConfigProperties.PackingChargesForTakeaway packing = restaurantChainConfigProperties.getChain().getPackingChargesForTakeaway();
            packingChargeRefundAmount = calculateChargeAmount(discountedSubtotalRefundAmount, BigDecimal.valueOf(packing.getValue()), packing.getType(), currency);
        }

        BigDecimal chargeAmount = serviceChargeRefundAmount.add(packingChargeRefundAmount);
        BigDecimal chargeToAlcoholic = BigDecimal.ZERO;
        BigDecimal chargeToNonAlcoholic = BigDecimal.ZERO;
        BigDecimal denomForChargeSplit = alcoholicSubtotal.add(nonAlcoholicSubtotal);
        if (chargeAmount.compareTo(BigDecimal.ZERO) > 0 && denomForChargeSplit.compareTo(BigDecimal.ZERO) > 0) {
            chargeToAlcoholic = chargeAmount.multiply(alcoholicSubtotal)
                    .divide(denomForChargeSplit, 20, RoundingMode.HALF_UP);
            chargeToNonAlcoholic = chargeAmount.subtract(chargeToAlcoholic);
        }
        BigDecimal alcoholicTaxBase = alcoholicSubtotal.add(chargeToAlcoholic);
        BigDecimal nonAlcoholicTaxBase = nonAlcoholicSubtotal.add(chargeToNonAlcoholic);

        // Taxable bases are what consumption tax is calculated from.
        // IMPORTANT: format + reconcile to avoid +/-1 roundoff drift when both alcoholic and non-alcoholic exist.
        BigDecimal alcoholicTaxableRefundAmount = CurrencyFormatter.formatAmount(alcoholicTaxBase, currency);
        BigDecimal totalTaxableBaseFormatted = CurrencyFormatter.formatAmount(alcoholicTaxBase.add(nonAlcoholicTaxBase), currency);
        BigDecimal nonAlcoholicTaxableRefundAmount = totalTaxableBaseFormatted.subtract(alcoholicTaxableRefundAmount);

        // Tax calculation using chain config (same as order pricing)
        BigDecimal taxRefundAmount = BigDecimal.ZERO;
        BigDecimal alcoholicTaxRefundAmount = BigDecimal.ZERO;
        BigDecimal nonAlcoholicTaxRefundAmount = BigDecimal.ZERO;
        if (restaurantChainConfigProperties.getChain() != null && restaurantChainConfigProperties.getChain().getTaxSetup() != null) {
            RestaurantChainConfigProperties.TaxSetup taxSetup = restaurantChainConfigProperties.getChain().getTaxSetup();
            RestaurantChainConfigProperties.TaxSetup.TaxCharge alcoholicTaxCharge =
                    order.getOrderType() == OrderType.DINE_IN ? taxSetup.getDineIn().getAlcoholic() : taxSetup.getTakeAway().getAlcoholic();
            RestaurantChainConfigProperties.TaxSetup.TaxCharge nonAlcoholicTaxCharge =
                    order.getOrderType() == OrderType.DINE_IN ? taxSetup.getDineIn().getNonAlcoholic() : taxSetup.getTakeAway().getNonAlcoholic();

            BigDecimal alcoholicTaxUnformatted = calculateChargeAmountUnformatted(
                    alcoholicTaxBase,
                    BigDecimal.valueOf(alcoholicTaxCharge.getValue()),
                    alcoholicTaxCharge.getType());
            BigDecimal nonAlcoholicTaxUnformatted = calculateChargeAmountUnformatted(
                    nonAlcoholicTaxBase,
                    BigDecimal.valueOf(nonAlcoholicTaxCharge.getValue()),
                    nonAlcoholicTaxCharge.getType());

            alcoholicTaxRefundAmount = CurrencyFormatter.formatAmount(alcoholicTaxUnformatted, currency);
            nonAlcoholicTaxRefundAmount = CurrencyFormatter.formatAmount(nonAlcoholicTaxUnformatted, currency);
            taxRefundAmount = alcoholicTaxRefundAmount.add(nonAlcoholicTaxRefundAmount);
        }

        BigDecimal totalBeforeAdditionalDiscount =
                discountedSubtotalRefundAmount.add(serviceChargeRefundAmount).add(packingChargeRefundAmount).add(taxRefundAmount);
        BigDecimal totalRefundAmount = totalBeforeAdditionalDiscount.subtract(additionalDiscountRefundAmount);
        if (totalRefundAmount.compareTo(BigDecimal.ZERO) < 0) totalRefundAmount = BigDecimal.ZERO;
        totalRefundAmount = CurrencyFormatter.formatAmount(totalRefundAmount, currency);

        return new RefundAmounts(
                totalRefundAmount,
                subtotalRefundAmount,
                taxRefundAmount,
                alcoholicTaxRefundAmount,
                nonAlcoholicTaxRefundAmount,
                alcoholicTaxableRefundAmount,
                nonAlcoholicTaxableRefundAmount,
                serviceChargeRefundAmount,
                packingChargeRefundAmount,
                discountRefundAmount,
                additionalDiscountRefundAmount
        );
    }

    /**
     * Helper class for combo item tax breakdown in refunds
     */
    private static class ComboItemTaxBreakdown {
        private final BigDecimal amount;
        private final AlcoholType alcoholType;

        private ComboItemTaxBreakdown(BigDecimal amount, AlcoholType alcoholType) {
            this.amount = amount;
            this.alcoholType = alcoholType;
        }
    }

    /**
     * Extract combo item prices for tax breakdown from OrderedCombo.
     * Calculates effective prices based on original combo price and scale factor.
     */
    private List<ComboItemTaxBreakdown> extractComboItemTaxBreakdown(
            OrderedCombo orderedCombo, Integer refundQuantity, String currency) {
        
        if (orderedCombo == null || orderedCombo.getOrderedItems() == null || orderedCombo.getOrderedItems().isEmpty()) {
            return new ArrayList<>();
        }

        // Get total combo amount (original price for all quantities)
        BigDecimal totalComboAmount = resolveTotalComboAmount(orderedCombo);

        // Calculate raw items price by summing OrderedItem amounts
        BigDecimal rawItemsPrice = BigDecimal.ZERO;
        List<ComboItemTaxBreakdown> rawItems = new ArrayList<>();
        
        for (OrderedItem orderedItem : orderedCombo.getOrderedItems()) {
            if (orderedItem != null) {
                BigDecimal itemAmount = resolveOrderedItemAmount(orderedItem);
                rawItemsPrice = rawItemsPrice.add(itemAmount);

                AlcoholType alcoholType = orderedItem.getAlcoholType();
                if (alcoholType == null && orderedItem.getItem() != null) {
                    alcoholType = orderedItem.getItem().getAlcoholType();
                }
                if (alcoholType == null) {
                    alcoholType = AlcoholType.NON_ALCOHOLIC;
                }

                rawItems.add(new ComboItemTaxBreakdown(itemAmount, alcoholType));
            }
        }

        // Calculate scale factor (combo price / raw items price)
        BigDecimal scaleFactor = BigDecimal.ONE;
        if (rawItemsPrice.compareTo(BigDecimal.ZERO) > 0 && totalComboAmount.compareTo(BigDecimal.ZERO) > 0) {
            scaleFactor = totalComboAmount.divide(rawItemsPrice, 10, RoundingMode.HALF_UP);
        }

        // Calculate refund ratio (refund quantity / original quantity)
        BigDecimal refundRatio = BigDecimal.ONE;
        if (orderedCombo.getQuantity() != null && orderedCombo.getQuantity() > 0) {
            refundRatio = BigDecimal.valueOf(refundQuantity)
                    .divide(BigDecimal.valueOf(orderedCombo.getQuantity()), 10, RoundingMode.HALF_UP);
        }

        // Calculate effective prices for refund quantity
        List<ComboItemTaxBreakdown> result = new ArrayList<>();
        for (ComboItemTaxBreakdown raw : rawItems) {
            // Effective price per unit = raw amount * scale factor
            BigDecimal effectivePricePerUnit = raw.amount.multiply(scaleFactor);
            // Total effective price for refund quantity = effective price per unit * refund ratio
            BigDecimal effectivePriceForRefund = effectivePricePerUnit.multiply(refundRatio);
            BigDecimal formatted = CurrencyFormatter.formatAmount(effectivePriceForRefund, currency);
            result.add(new ComboItemTaxBreakdown(formatted, raw.alcoholType));
        }

        return result;
    }

    private BigDecimal calculateChargeAmount(BigDecimal baseAmount, BigDecimal value, ChargeType type, String currency) {
        if (type == null) type = ChargeType.PERCENT;
        BigDecimal amount = calculateChargeAmountUnformatted(baseAmount, value, type);
        return CurrencyFormatter.formatAmount(amount, currency);
    }

    /**
     * Calculate charge amount WITHOUT formatting.
     * Used for "format + reconcile" scenarios where we must avoid +/-1 rounding drift.
     */
    private BigDecimal calculateChargeAmountUnformatted(BigDecimal baseAmount, BigDecimal value, ChargeType type) {
        if (type == null) type = ChargeType.PERCENT;
        return (type == ChargeType.PERCENT)
                ? baseAmount.multiply(value).divide(BigDecimal.valueOf(100), 10, RoundingMode.HALF_UP)
                : value;
    }

    private boolean isEligibleFullRefundItem(OrderedItem orderedItem) {
        // FULL refund flow includes all regular (non-combo) items.
        // Cancelled items are included only if they were part of a completed payment.
        return orderedItem.getOrderedCombo() == null
                && orderedItem.getQuantity() != null
                && orderedItem.getQuantity() > 0
                && (orderedItem.getItemStatus() != ItemStatus.CANCELED
                    || Boolean.TRUE.equals(orderedItem.getIncludedInPayment()));
    }

    private boolean isEligibleFullRefundCombo(OrderedCombo orderedCombo) {
        // FULL refund flow includes all combos.
        // Cancelled combos are included only if they were part of a completed payment.
        return orderedCombo.getQuantity() != null
                && orderedCombo.getQuantity() > 0
                && (orderedCombo.getItemStatus() != ItemStatus.CANCELED
                    || Boolean.TRUE.equals(orderedCombo.getIncludedInPayment()));
    }

    private boolean hasValidItemRefundResponse(RaiseRefundRequestResponse.OrderedItemRefundResponse itemResp) {
        return itemResp != null
                && itemResp.getOrderedItemId() != null
                && itemResp.getRefundAmount() != null;
    }

    private boolean hasValidComboRefundResponse(RaiseRefundRequestResponse.OrderedComboRefundResponse comboResp) {
        return comboResp != null && comboResp.getOrderedComboId() != null;
    }

    private boolean hasBreakdownAmount(ComboItemTaxBreakdown itemBreakdown) {
        return itemBreakdown != null && itemBreakdown.amount != null;
    }

    private OrderedItem findRefundOrderedItem(RaiseRefundRequestResponse.OrderedItemRefundResponse itemResp) {
        if (!hasValidItemRefundResponse(itemResp)) {
            return null;
        }
        return orderedItemRepository.findById(itemResp.getOrderedItemId()).orElse(null);
    }

    private OrderedCombo findRefundOrderedCombo(RaiseRefundRequestResponse.OrderedComboRefundResponse comboResp) {
        if (!hasValidComboRefundResponse(comboResp)) {
            return null;
        }
        return orderedComboRepository.findById(comboResp.getOrderedComboId()).orElse(null);
    }

    private BigDecimal resolveTotalComboAmount(OrderedCombo orderedCombo) {
        if (orderedCombo.getTotalComboAmount() != null) {
            return orderedCombo.getTotalComboAmount();
        }
        if (orderedCombo.getPrice() != null) {
            return orderedCombo.getPrice().multiply(BigDecimal.valueOf(orderedCombo.getQuantity()));
        }
        return BigDecimal.ZERO;
    }

    private BigDecimal resolveOrderedItemAmount(OrderedItem orderedItem) {
        if (orderedItem.getTotalDiscountedItemAmount() != null) {
            return orderedItem.getTotalDiscountedItemAmount();
        }
        if (orderedItem.getTotalItemAmount() != null) {
            return orderedItem.getTotalItemAmount();
        }
        return BigDecimal.ZERO;
    }

    /**
     * Processes a refund immediately (no approval workflow).
     * <p>
     * Persists {@link Refund} and {@link RefundItem} entities, updates transaction review metadata, flushes changes
     * so that downstream real-time HQ alert evaluation can run after commit, and writes an audit trail entry.
     * </p>
     *
     * @param transaction       transaction being refunded
     * @param order             associated order
     * @param user              actor initiating the refund
     * @param userRole          role of the actor (used in the response)
     * @param refundType        refund type (FULL/PARTIAL)
     * @param request           refund request payload
     * @param calculationResult selected refund items/combos and subtotal
     * @param refundAmounts     computed refund components
     * @param now               timestamp (UTC) to use for created/updated fields
     * @param userLocale        locale used for messages
     * @return approved refund response
     */
    private ResponseDto<RaiseRefundRequestResponse> processRefundDirectly(
            Transaction transaction, Order order, User user, String userRole, RefundType refundType,
            RaiseRefundRequest request, RefundCalculationResult calculationResult, RefundAmounts refundAmounts,
            OffsetDateTime now, Locale userLocale) {
        // Create Refund entity
        Refund refund = Refund.builder()
                        .transaction(transaction)
                .refundNumber(generateRefundNumber(transaction.getRestaurant()))
                .refundType(refundType)
                .refundReason(request.getRefundReason())
                .refundMethod(transaction.getPaymentMethod())
                .totalRefundAmount(refundAmounts.getTotalRefundAmount())
                .subtotalRefundAmount(refundAmounts.getSubtotalRefundAmount())
                .taxRefundAmount(refundAmounts.getTaxRefundAmount())
                .alcoholicTaxRefundAmount(refundAmounts.getAlcoholicTaxRefundAmount())
                .nonAlcoholicTaxRefundAmount(refundAmounts.getNonAlcoholicTaxRefundAmount())
                .alcoholicTaxableRefundAmount(refundAmounts.getAlcoholicTaxableRefundAmount())
                .nonAlcoholicTaxableRefundAmount(refundAmounts.getNonAlcoholicTaxableRefundAmount())
                .serviceChargeRefundAmount(refundAmounts.getServiceChargeRefundAmount())
                .packingChargeRefundAmount(refundAmounts.getPackingChargeRefundAmount())
                .discountRefundAmount(refundAmounts.getDiscountRefundAmount())
                .additionalDiscountRefundAmount(refundAmounts.getAdditionalDiscountRefundAmount())
                        .createdAt(now)
                .updatedAt(now)
                        .build();

        refund = refundRepository.save(refund);

        // Create RefundItem entities
        createRefundItems(refund, order, refundType, request);
        
        // Update transaction updatedAt timestamp and set reviewed fields
        OffsetDateTime nowOffset = OffsetDateTime.now(ZoneOffset.UTC);
        transaction.setUpdatedAt(nowOffset);
        transaction.setReviewedAt(nowOffset);
        transaction.setReviewedBy(user);
        transactionRepository.save(transaction);
        transactionRepository.flush(); // Ensure transaction is committed before alert evaluation
        refundRepository.flush(); // Ensure refund is committed before alert evaluation
        log.debug("Transaction {} saved and flushed for refund (type: {}). Triggering alert evaluation.", 
                transaction.getId(), refundType);

        // ==================== REAL-TIME HQ ALERT EVALUATION ====================
        // Check if refund percentage threshold is breached after this refund.
        // IMPORTANT: Run alert evaluation AFTER the surrounding transaction commits
        // so the new Refund/RefundItem rows are visible to the REQUIRES_NEW alert transaction.
        evaluateAlertsAfterTransactionCommit(transaction.getRestaurant(), userLocale, "refund");

        // Create audit trail
        createRefundProcessedAuditTrail(user, transaction, refundAmounts.getTotalRefundAmount(), request.getRefundReason());

        return buildResponse(transaction, order, user, userRole, refundType, request,
                calculationResult, refundAmounts, RequestStatus.APPROVED, now != null ? now.toLocalDateTime() : null, userLocale,
                messageUtil.getMessage("refund.processed.successfully", userLocale));
    }

    /**
     * Creates a refund request for manager approval by storing structured request data in the transaction.
     * <p>
     * Ensures that any existing OPEN cancellation request is cleared (refund and cancellation share the same
     * request fields on the {@link Transaction}), writes JSON request metadata for later review, and notifies managers.
     * </p>
     *
     * @param transaction       transaction being refunded
     * @param order             associated order
     * @param user              actor initiating the refund request
     * @param userRole          role of the actor (used in the response)
     * @param refundType        refund type (FULL/PARTIAL)
     * @param request           refund request payload
     * @param calculationResult selected refund items/combos and subtotal
     * @param refundAmounts     computed refund components
     * @param now               timestamp (UTC) to record request creation time
     * @param userLocale        locale used for localized errors/messages
     * @return pending refund response (OPEN request)
     * @throws ResponseStatusException when request data cannot be serialized
     */
    private ResponseDto<RaiseRefundRequestResponse> createRefundRequest(
            Transaction transaction, Order order, User user, String userRole, RefundType refundType,
            RaiseRefundRequest request, RefundCalculationResult calculationResult, RefundAmounts refundAmounts,
            OffsetDateTime now, Locale userLocale) {
        try {
            // Check if there's already a pending transaction cancellation request and clear it
            // Refund requests and transaction cancellation requests use the same Transaction entity
            // Only one type of request should exist at a time
            clearExistingCancellationRequestIfPresent(transaction);
            
            // Build request data JSON
            Map<String, Object> refundData = buildRefundRequestData(transaction, order, refundType, request,
                    calculationResult, refundAmounts);
            String requestDataJson = new ObjectMapper().writeValueAsString(refundData);

            // Store refund request in Transaction entity
            transaction.setRequestStatus(RequestStatus.OPEN);
            transaction.setRequestData(requestDataJson);
            transaction.setRequestedAt(now);
            transaction.setRequestedBy(user);
            transaction.setReviewedAt(null);
            transaction.setReviewedBy(null);
            transaction.setRequestComments(null);
            transaction.setUpdatedAt(OffsetDateTime.now(ZoneOffset.UTC));
            transactionRepository.save(transaction);

            // Create audit trail entry for cashier when creating pending refund request
            createRefundRequestAuditTrail(user, transaction, request.getRefundReason());

            // Notify managers about newly opened refund request (similar to cancellation requests)
            notifyManagersAboutRefundRequest(transaction, userLocale);

            return buildResponse(transaction, order, user, userRole, refundType, request,
                    calculationResult, refundAmounts, RequestStatus.OPEN, now != null ? now.toLocalDateTime() : null, userLocale,
                    messageUtil.getMessage("refund.request.sent.for.manager.approval", userLocale));

        } catch (JsonProcessingException e) {
            log.error("Error creating refund request data: {}", e.getMessage());
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    messageUtil.getMessage("refund.request.error", userLocale));
        }
    }

    /**
     * Builds structured data for a refund request to be stored as JSON in {@code Transaction.requestData}.
     *
     * @param transaction       transaction being refunded
     * @param order             associated order
     * @param refundType        refund type (FULL/PARTIAL)
     * @param request           refund request payload
     * @param calculationResult selected refund items/combos and subtotal
     * @param refundAmounts     computed refund monetary components
     * @return a map containing request metadata including amounts and selected item/combo details
     */
    private Map<String, Object> buildRefundRequestData(Transaction transaction, Order order, RefundType refundType,
                                                       RaiseRefundRequest request, RefundCalculationResult calculationResult,
                                                       RefundAmounts refundAmounts) {
                Map<String, Object> refundData = new HashMap<>();
                refundData.put(FIELD_REQUEST_TYPE, REQUEST_TYPE_REFUND);
        refundData.put("refundType", refundType != null ? refundType.toString() : null);
                refundData.put("refundReason", request.getRefundReason());
        refundData.put(FIELD_REFUND_AMOUNT, refundAmounts.getSubtotalRefundAmount());
        refundData.put("totalRefundAmount", refundAmounts.getTotalRefundAmount());
        refundData.put("subtotalRefundAmount", refundAmounts.getSubtotalRefundAmount());
        refundData.put("taxRefundAmount", refundAmounts.getTaxRefundAmount());
        refundData.put("alcoholicTaxRefundAmount", refundAmounts.getAlcoholicTaxRefundAmount());
        refundData.put("nonAlcoholicTaxRefundAmount", refundAmounts.getNonAlcoholicTaxRefundAmount());
        refundData.put("serviceChargeRefundAmount", refundAmounts.getServiceChargeRefundAmount());
        refundData.put("packingChargeRefundAmount", refundAmounts.getPackingChargeRefundAmount());
        refundData.put("discountRefundAmount", refundAmounts.getDiscountRefundAmount());
        refundData.put("additionalDiscountRefundAmount", refundAmounts.getAdditionalDiscountRefundAmount());
        refundData.put("paymentMethod", transaction.getPaymentMethod());
        refundData.put("transactionId", transaction.getId().toString());
        refundData.put("orderId", order.getId().toString());
        refundData.put("orderNumber", order.getOrderNumber());
        refundData.put("transactionNumber", transaction.getTransactionNumber());
        refundData.put("orderedItems", calculationResult.getOrderedItemsJson());
        refundData.put("orderedCombos", calculationResult.getOrderedCombosJson());
        return refundData;
    }

    /**
     * Persists {@link RefundItem} rows for a created refund based on refund scope.
     * <p>
     * FULL refunds include all eligible ordered items (excluding combo items) and combos.
     * PARTIAL refunds include only the requested items/combos and apply the same cancellation eligibility rules
     * as during validation.
     * </p>
     *
     * @param refund     persisted refund parent entity
     * @param order      source order for ordered items/combos
     * @param refundType refund scope
     * @param request    original request (used for partial item/combo lists)
     * @throws ResponseStatusException if requested entities are missing or not eligible
     */
    private void createRefundItems(Refund refund, Order order, RefundType refundType, RaiseRefundRequest request) {
        List<RefundItem> refundItemsToSave = new ArrayList<>();

        if (refundType == RefundType.FULL) {
            for (OrderedItem orderedItem : orderedItemRepository.findByOrderId(order.getId())) {
                if (isEligibleFullRefundItem(orderedItem)) {
                    BigDecimal itemRefundAmount = calculateItemRefundAmount(orderedItem, orderedItem.getQuantity());
                    refundItemsToSave.add(RefundItem.builder()
                            .refund(refund)
                            .orderedItem(orderedItem)
                            .orderedCombo(null)
                            .quantity(orderedItem.getQuantity())
                            .refundAmount(itemRefundAmount)
                            .build());
                }
            }

            for (OrderedCombo orderedCombo : orderedComboRepository.findByOrderId(order.getId())) {
                if (isEligibleFullRefundCombo(orderedCombo)) {
                    BigDecimal comboRefundAmount = calculateComboRefundAmount(orderedCombo, orderedCombo.getQuantity());
                    refundItemsToSave.add(RefundItem.builder()
                            .refund(refund)
                            .orderedItem(null)
                            .orderedCombo(orderedCombo)
                            .quantity(orderedCombo.getQuantity())
                            .refundAmount(comboRefundAmount)
                            .build());
                }
            }
        } else {
            if (request.getOrderedItems() != null) {
                for (RaiseRefundRequest.OrderedItemRefund itemRequest : request.getOrderedItems()) {
                    OrderedItem orderedItem = orderedItemRepository.findById(itemRequest.getOrderedItemId())
                            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                                    messageUtil.getMessage("ordered.item.not.found", LocaleContextHolder.getLocale())));
                    // Validate that the item is not a combo item - combo items should only be refunded as part of the combo
                    if (orderedItem.getOrderedCombo() != null) {
                        throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                                messageUtil.getMessage("refund.combo.item.cannot.be.refunded.separately", LocaleContextHolder.getLocale()));
                    }
                    // Payment-aware rule for cancelled items:
                    // If an item is cancelled, it can be refunded ONLY if it was included in a completed payment.
                    if (orderedItem.getItemStatus() == ItemStatus.CANCELED
                            && !Boolean.TRUE.equals(orderedItem.getIncludedInPayment())) {
                        throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                                messageUtil.getMessage("refund.canceled.item.not.included.in.payment", LocaleContextHolder.getLocale()));
                    }
                    BigDecimal itemRefundAmount = calculateItemRefundAmount(orderedItem, itemRequest.getQuantity());
                    refundItemsToSave.add(RefundItem.builder()
                            .refund(refund)
                            .orderedItem(orderedItem)
                            .orderedCombo(null)
                            .quantity(itemRequest.getQuantity())
                            .refundAmount(itemRefundAmount)
                            .build());
                }
            }

            if (request.getOrderedCombos() != null) {
                for (RaiseRefundRequest.OrderedComboRefund comboRequest : request.getOrderedCombos()) {
                    OrderedCombo orderedCombo = orderedComboRepository.findById(comboRequest.getOrderedComboId())
                            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                                    messageUtil.getMessage("ordered.combo.not.found", LocaleContextHolder.getLocale())));
                    // Payment-aware rule for cancelled combos:
                    // If a combo is cancelled, it can be refunded ONLY if it was included in a completed payment.
                    if (orderedCombo.getItemStatus() == ItemStatus.CANCELED
                            && !Boolean.TRUE.equals(orderedCombo.getIncludedInPayment())) {
                        throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                                messageUtil.getMessage("refund.canceled.item.not.included.in.payment", LocaleContextHolder.getLocale()));
                    }
                    BigDecimal comboRefundAmount = calculateComboRefundAmount(orderedCombo, comboRequest.getQuantity());
                    refundItemsToSave.add(RefundItem.builder()
                            .refund(refund)
                            .orderedItem(null)
                            .orderedCombo(orderedCombo)
                            .quantity(comboRequest.getQuantity())
                            .refundAmount(comboRefundAmount)
                            .build());
                }
            }
        }

        if (!refundItemsToSave.isEmpty()) {
            refundItemRepository.saveAll(refundItemsToSave);
        }
    }


    /**
     * Builds a standardized refund API response using computed refund amounts and selected item/combo details.
     *
     * @param transaction       transaction being refunded
     * @param order             associated order
     * @param user              actor who requested/processed the refund
     * @param userRole          role of the actor (for display)
     * @param refundType        refund type (FULL/PARTIAL)
     * @param request           request payload (reason, etc.)
     * @param calculationResult selected refund items/combos and subtotal
     * @param refundAmounts     computed refund monetary components
     * @param requestStatus     resulting request status (OPEN/APPROVED/etc.)
     * @param now               timestamp to expose as requestedAt in response
     * @param userLocale        locale used to resolve translated restaurant name and messages
     * @param message           localized message to return to caller
     * @return response wrapper containing {@link RaiseRefundRequestResponse}
     */
    private ResponseDto<RaiseRefundRequestResponse> buildResponse(
            Transaction transaction, Order order, User user, String userRole, RefundType refundType,
            RaiseRefundRequest request, RefundCalculationResult calculationResult, RefundAmounts refundAmounts,
            RequestStatus requestStatus, LocalDateTime now, Locale userLocale, String message) {
        String restaurantName = getRestaurantName(transaction.getRestaurant(), userLocale);
        String requestedByName = user.getFirstName() + " " + user.getLastName();

        String currency = restaurantChainConfigProperties.getChain() != null ? restaurantChainConfigProperties.getChain().getCurrency() : null;
        RaiseRefundRequestResponse response = RaiseRefundRequestResponse.builder()
                        .transactionId(transaction.getId())
                        .orderId(order.getId())
                        .orderNumber(order.getOrderNumber())
                        .transactionNumber(transaction.getTransactionNumber())
                        .paymentMethod(transaction.getPaymentMethod())
                        .paymentApp(transaction.getPaymentApp())
                        .transactionAmount(transaction.getTransactionAmount() != null ? CurrencyFormatter.formatAmount(transaction.getTransactionAmount(), currency) : null)
                .refundType(refundType)
                        .refundReason(request.getRefundReason())
                .refundMethod(transaction.getPaymentMethod())
                .totalRefundAmount(refundAmounts.getTotalRefundAmount() != null ? CurrencyFormatter.formatAmount(refundAmounts.getTotalRefundAmount(), currency) : null)
                .subtotalRefundAmount(refundAmounts.getSubtotalRefundAmount() != null ? CurrencyFormatter.formatAmount(refundAmounts.getSubtotalRefundAmount(), currency) : null)
                .taxRefundAmount(refundAmounts.getTaxRefundAmount() != null ? CurrencyFormatter.formatAmount(refundAmounts.getTaxRefundAmount(), currency) : null)
                .alcoholicTaxRefundAmount(refundAmounts.getAlcoholicTaxRefundAmount() != null ? CurrencyFormatter.formatAmount(refundAmounts.getAlcoholicTaxRefundAmount(), currency) : null)
                .nonAlcoholicTaxRefundAmount(refundAmounts.getNonAlcoholicTaxRefundAmount() != null ? CurrencyFormatter.formatAmount(refundAmounts.getNonAlcoholicTaxRefundAmount(), currency) : null)
                .serviceChargeRefundAmount(refundAmounts.getServiceChargeRefundAmount() != null ? CurrencyFormatter.formatAmount(refundAmounts.getServiceChargeRefundAmount(), currency) : null)
                .packingChargeRefundAmount(refundAmounts.getPackingChargeRefundAmount() != null ? CurrencyFormatter.formatAmount(refundAmounts.getPackingChargeRefundAmount(), currency) : null)
                .discountRefundAmount(refundAmounts.getDiscountRefundAmount() != null ? CurrencyFormatter.formatAmount(refundAmounts.getDiscountRefundAmount(), currency) : null)
                .additionalDiscountRefundAmount(refundAmounts.getAdditionalDiscountRefundAmount() != null ? CurrencyFormatter.formatAmount(refundAmounts.getAdditionalDiscountRefundAmount(), currency) : null)
                .orderedItems(calculationResult.getOrderedItemsResponse())
                .orderedCombos(calculationResult.getOrderedCombosResponse())
                .requestStatus(requestStatus)
                        .requestedAt(now)
                .requestedBy(user.getId())
                .requestedByName(requestedByName)
                .requestedByRole(userRole)
                        .restaurantId(transaction.getRestaurant() != null ? transaction.getRestaurant().getId() : null)
                        .restaurantName(restaurantName)
                        .build();

        return ResponseDto.<RaiseRefundRequestResponse>builder()
                .message(message)
                .data(response)
                        .build();
    }

    /**
     * Generates a unique refund number similar to transaction number format
     * Format: {restaurantCode}-REF-{yyyyMMdd-HHmmss}-{4-digit-random}
     */
    private String generateRefundNumber(Restaurant restaurant) {
        String restaurantCode = restaurant.getRestaurantCode();
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        String timestamp = now.format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
        String random = String.format("%04d", ThreadLocalRandom.current().nextInt(10000));
        
        return String.format("%s-REF-%s-%s", restaurantCode, timestamp, random);
    }

    /**
     * Display text for translation picking: null when blank or placeholder so another locale can be used.
     */
    private static String translationDisplayTextForPick(String name) {
        if (name == null || name.isBlank()) {
            return null;
        }
        if ("NA".equalsIgnoreCase(name.trim())) {
            return null;
        }
        return name;
    }

    /**
     * Item display name for refund metadata: preferred locale, then configured languages, then any non-blank name
     * (same strategy as discount assignment / assigned discount list).
     */
    private String getItemName(Item item, Locale userLocale) {
        if (item == null || item.getTranslations() == null || item.getTranslations().isEmpty()) {
            return "Item";
        }
        String preferred = userLocale != null ? userLocale.getLanguage() : null;
        return TranslationUtils.pickPreferredOrFromListNonBlank(
                        item.getTranslations(),
                        preferred,
                        localizationProperties.getLanguages(),
                        ItemTranslation::getLanguageCode,
                        t -> translationDisplayTextForPick(t.getName()))
                .map(ItemTranslation::getName)
                .orElse("Item");
    }

    /**
     * Combo display name for refund metadata: preferred locale, then configured languages, then any non-blank name.
     */
    private String getComboName(Combo combo, Locale userLocale) {
        if (combo == null || combo.getTranslations() == null || combo.getTranslations().isEmpty()) {
            return "Combo";
        }
        String preferred = userLocale != null ? userLocale.getLanguage() : null;
        return TranslationUtils.pickPreferredOrFromListNonBlank(
                        combo.getTranslations(),
                        preferred,
                        localizationProperties.getLanguages(),
                        ComboTranslation::getLanguageCode,
                        t -> translationDisplayTextForPick(t.getName()))
                .map(ComboTranslation::getName)
                .orElse("Combo");
    }

    /**
     * Helper method to get restaurant name
     */
    private String getRestaurantName(Restaurant restaurant, Locale userLocale) {
        if (restaurant == null) {
            return null;
        }
        if (restaurant.getTranslations() != null && !restaurant.getTranslations().isEmpty()) {
            String userLanguage = userLocale.getLanguage();
            return restaurant.getTranslations().stream()
                    .filter(t -> t.getLanguageCode() != null && t.getLanguageCode().startsWith(userLanguage))
                    .findFirst()
                    .map(com.gulfnet.shared_library.entity.RestaurantTranslation::getName)
                    .orElse(restaurant.getTranslations().get(0).getName());
        }
        return "Restaurant";
    }

    /**
     * Check if a user is a manager
     * @param user The user to check
     * @return true if the user is a manager, false otherwise
     */
    private boolean isManager(User user) {
        if (user == null || user.getRoleId() == null) {
            return false;
        }
        try {
            Role role = roleRepository.findById(user.getRoleId()).orElse(null);
            return role != null && ROLE_MANAGER.equals(role.getName());
        } catch (Exception e) {
            log.debug("Failed to check if user is manager: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Check if a user is a cashier
     * @param user The user to check
     * @return true if the user is a cashier, false otherwise
     */
    private boolean isCashier(User user) {
        if (user == null || user.getRoleId() == null) {
            return false;
        }
        try {
            Role role = roleRepository.findById(user.getRoleId()).orElse(null);
            return role != null && "CASHIER".equals(role.getName());
        } catch (Exception e) {
            log.debug("Failed to check if user is cashier: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Retrieves a report of transactions where discounts and/or additional offers were applied.
     * <p>
     * Supports comma-separated transaction-status filters and date or date-range filtering, then aggregates
     * summary statistics (counts and total discount amounts) alongside the paged result set.
     * </p>
     *
     * @param restaurantId      restaurant identifier
     * @param page              1-based page number; when {@code null} or invalid, paging may be disabled
     * @param size              page size; when {@code null} or invalid, paging may be disabled
     * @param transactionStatus optional transaction-status filter (supports comma-separated values)
     * @param date              optional single-day filter (takes precedence over {@code startDate}/{@code endDate})
     * @param startDate         optional start date-time (used when {@code date} is not provided)
     * @param endDate           optional end date-time (used when {@code date} is not provided)
     * @param locale            locale string from request (currently resolved via {@link LocaleContextHolder})
     * @return response wrapper containing {@link DiscountOfferReportListDto} and optional pagination metadata
     * @throws ResponseStatusException if restaurant is not found or filters are invalid
     */
    @Override
    @Transactional(readOnly = true)
    public ResponseDto<DiscountOfferReportListDto> getDiscountsAndOffersAppliedReport(
            UUID restaurantId,
            Integer page,
            Integer size,
            String transactionStatus,
            LocalDate date,
            LocalDateTime startDate,
            LocalDateTime endDate,
            String locale) {
        
        log.info("Getting discounts and offers applied report for restaurant: {} (page: {}, size: {}, " +
                "transactionStatus: {}, date: {}, startDate: {}, endDate: {})", 
                restaurantId, page, size, transactionStatus, date, startDate, endDate);

        Locale userLocale = LocaleContextHolder.getLocale();

        // Validate restaurant
        Restaurant restaurant = restaurantRepository.findById(restaurantId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        messageUtil.getMessage(MSG_RESTAURANT_NOT_FOUND, userLocale)));

        // Parse transaction status filter - support comma-separated values
        Collection<TransactionStatus> transactionStatuses = null;
        if (transactionStatus != null && !transactionStatus.isBlank()) {
            try {
                transactionStatuses = Arrays.stream(transactionStatus.split(","))
                        .map(String::trim)
                        .filter(s -> !s.isBlank())
                        .map(s -> TransactionStatus.valueOf(s.toUpperCase()))
                        .collect(Collectors.toSet());
                if (transactionStatuses.isEmpty()) transactionStatuses = null;
            } catch (IllegalArgumentException e) {
                String errorMessage = messageUtil.getMessage(MSG_ERROR_INVALID_TRANSACTION_STATUS, userLocale, transactionStatus);
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, errorMessage);
            }
        }

        // Handle date filtering - prioritize 'date' parameter if provided
        LocalDateTime startDateTime;
        LocalDateTime endDateTime;
        
        if (date != null) {
            // If 'date' is provided, filter for that entire day
            startDateTime = date.atStartOfDay(); // 00:00:00
            endDateTime = date.atTime(23, 59, 59, 999999000); // 23:59:59.999999
        } else {
            // Otherwise, use startDate and endDate if provided
            startDateTime = (startDate != null) ? startDate : LocalDateTime.of(1900, 1, 1, 0, 0, 0);
            if (endDate != null) {
                endDateTime = endDate.toLocalDate().atTime(23, 59, 59, 999999000);
            } else {
                endDateTime = LocalDateTime.of(2100, 12, 31, 23, 59, 59, 999999000);
            }
        }

        // Pagination - support both paged and unpaged requests
        boolean noPaging = (page == null || size == null || page <= 0 || size <= 0);
        Pageable pageable;
        
        if (!noPaging) {
            pageable = PageRequest.of(page - 1, size, Sort.by(Sort.Direction.DESC, FIELD_CREATED_AT));
        } else {
            pageable = Pageable.unpaged();
        }

        // Query transactions with discounts
        log.debug("Querying transactions with discounts - restaurantId: {}, transactionStatuses: {}, startDate: {}, endDate: {}", 
                restaurantId, transactionStatuses, startDateTime, endDateTime);
        
        Page<Transaction> transactionsPage = transactionRepository.findTransactionsWithDiscounts(
                restaurantId, transactionStatuses,
                startDateTime.atOffset(java.time.ZoneOffset.UTC), endDateTime.atOffset(java.time.ZoneOffset.UTC), pageable);

        log.info("Found {} transactions with discounts (total: {})", 
                transactionsPage.getContent().size(), transactionsPage.getTotalElements());

        // Convert to response DTOs
        List<DiscountOfferReportResponse> discountOffers = transactionsPage.getContent().stream()
                .map(this::convertToDiscountOfferReportResponse)
                .collect(Collectors.toList());

        // Calculate summary statistics
        DiscountOfferReportListDto.SummaryStatistics summary = calculateSummaryStatistics(
                transactionsPage.getContent());

        DiscountOfferReportListDto dto = DiscountOfferReportListDto.builder()
                .discountOffers(discountOffers)
                .count((long) discountOffers.size())
                .total(transactionsPage.getTotalElements())
                .metaData(noPaging ? null : PaginationMetaData.builder()
                        .page(page)
                        .size(size)
                        .totalPages(transactionsPage.getTotalPages())
                        .totalRecords(transactionsPage.getTotalElements())
                        .build())
                .summary(summary)
                .build();

        return ResponseDto.<DiscountOfferReportListDto>builder()
                .message(messageUtil.getMessage("discount.offer.report.success", userLocale))
                .data(dto)
                .build();
    }

    /**
     * Converts Transaction entity to DiscountOfferReportResponse DTO
     */
    public DiscountOfferReportResponse convertToDiscountOfferReportResponse(Transaction transaction) {
        Order order = transaction.getOrder();
        Locale userLocale = LocaleContextHolder.getLocale();

        // Calculate total discount amount
        BigDecimal totalDiscountAmount = BigDecimal.ZERO;
        if (order.getDiscountAmount() != null) {
            totalDiscountAmount = totalDiscountAmount.add(order.getDiscountAmount());
        }
        if (order.getAdditionalDiscountAmount() != null) {
            totalDiscountAmount = totalDiscountAmount.add(order.getAdditionalDiscountAmount());
        }

        String currency = restaurantChainConfigProperties.getChain() != null ? restaurantChainConfigProperties.getChain().getCurrency() : null;
        return DiscountOfferReportResponse.builder()
                .orderId(order != null ? order.getId() : null)
                .orderNumber(order != null ? order.getOrderNumber() : null)
                .transactionId(transaction.getId())
                .transactionNumber(transaction.getTransactionNumber())
                .transactionDateTime(transaction.getCreatedAt() != null ? transaction.getCreatedAt().toLocalDateTime() : null)
                .restaurantId(transaction.getRestaurant() != null ? transaction.getRestaurant().getId() : null)
                .restaurantName(getRestaurantName(transaction.getRestaurant(), userLocale))
                .discountId(order != null && order.getDiscount() != null ? order.getDiscount().getId() : null)
                .discountCode(order != null ? order.getDiscountCode() : null)
                .discountType(order != null ? order.getDiscountType() : null)
                .discountValue(order != null && order.getDiscountValue() != null ? CurrencyFormatter.formatAmount(order.getDiscountValue(), currency) : null)
                .discountAmount(order != null && order.getDiscountAmount() != null ? CurrencyFormatter.formatAmount(order.getDiscountAmount(), currency) : null)
                .additionalDiscountValue(order != null && order.getAdditionalDiscountValue() != null ? CurrencyFormatter.formatAmount(order.getAdditionalDiscountValue(), currency) : null)
                .additionalDiscountType(order != null ? order.getAdditionalDiscountType() : null)
                .additionalDiscountAmount(order != null && order.getAdditionalDiscountAmount() != null ? CurrencyFormatter.formatAmount(order.getAdditionalDiscountAmount(), currency) : null)
                .additionalDiscountReason(order != null ? order.getAdditionalDiscountReason() : null)
                .subTotal(order != null && order.getSubTotal() != null ? CurrencyFormatter.formatAmount(order.getSubTotal(), currency) : null)
                .totalAmount(order != null && order.getTotalAmount() != null ? CurrencyFormatter.formatAmount(order.getTotalAmount(), currency) : null)
                .totalDiscountAmount(totalDiscountAmount != null ? CurrencyFormatter.formatAmount(totalDiscountAmount, currency) : null)
                .build();
    }

    /**
     * Calculate summary statistics for discounts and offers report
     */
    public DiscountOfferReportListDto.SummaryStatistics calculateSummaryStatistics(List<Transaction> transactions) {
        long totalOrdersWithDiscounts = transactions.size();
        BigDecimal totalDiscountAmount = BigDecimal.ZERO;
        BigDecimal totalAdditionalDiscountAmount = BigDecimal.ZERO;
        long ordersWithOrderLevelDiscount = 0;
        long ordersWithAdditionalDiscount = 0;
        long ordersWithBothDiscounts = 0;

        for (Transaction transaction : transactions) {
            Order order = transaction.getOrder();
            if (order != null) {
                boolean hasOrderDiscount = order.getDiscountAmount() != null &&
                        order.getDiscountAmount().compareTo(BigDecimal.ZERO) > 0;
                boolean hasAdditionalDiscount = order.getAdditionalDiscountAmount() != null &&
                        order.getAdditionalDiscountAmount().compareTo(BigDecimal.ZERO) > 0;

                if (hasOrderDiscount) {
                    totalDiscountAmount = totalDiscountAmount.add(order.getDiscountAmount());
                    ordersWithOrderLevelDiscount++;
                }

                if (hasAdditionalDiscount) {
                    totalAdditionalDiscountAmount = totalAdditionalDiscountAmount.add(order.getAdditionalDiscountAmount());
                    ordersWithAdditionalDiscount++;
                }

                if (hasOrderDiscount && hasAdditionalDiscount) {
                    ordersWithBothDiscounts++;
                }
            }
        }

        BigDecimal totalCombinedDiscountAmount = totalDiscountAmount.add(totalAdditionalDiscountAmount);

        String currency = restaurantChainConfigProperties.getChain() != null ? restaurantChainConfigProperties.getChain().getCurrency() : null;
        return DiscountOfferReportListDto.SummaryStatistics.builder()
                .totalOrdersWithDiscounts(totalOrdersWithDiscounts)
                .totalDiscountAmount(totalDiscountAmount != null ? CurrencyFormatter.formatAmount(totalDiscountAmount, currency) : null)
                .totalAdditionalDiscountAmount(totalAdditionalDiscountAmount != null ? CurrencyFormatter.formatAmount(totalAdditionalDiscountAmount, currency) : null)
                .totalCombinedDiscountAmount(totalCombinedDiscountAmount != null ? CurrencyFormatter.formatAmount(totalCombinedDiscountAmount, currency) : null)
                .ordersWithOrderLevelDiscount(ordersWithOrderLevelDiscount)
                .ordersWithAdditionalDiscount(ordersWithAdditionalDiscount)
                .ordersWithBothDiscounts(ordersWithBothDiscounts)
                .build();
    }

    /**
     * Exports the discounts/offers applied report to CSV and streams it to the HTTP response.
     * <p>
     * Uses the same filters as {@link #getDiscountsAndOffersAppliedReport(UUID, Integer, Integer, String, LocalDate, LocalDateTime, LocalDateTime, String)}
     * but fetches the full unpaged dataset for export. Sets response headers (UTF-8) and writes the CSV content.
     * </p>
     *
     * @param restaurantId      restaurant identifier
     * @param transactionStatus optional transaction-status filter (supports comma-separated values)
     * @param date              optional single-day filter (takes precedence over {@code startDate}/{@code endDate})
     * @param startDate         optional start date-time (used when {@code date} is not provided)
     * @param endDate           optional end date-time (used when {@code date} is not provided)
     * @param locale            locale string from request (currently resolved via {@link LocaleContextHolder})
     * @param response          servlet response to write the CSV to
     * @throws IOException if writing to the servlet response fails
     * @throws ResponseStatusException if restaurant is not found or filters are invalid
     */
    @Override
    @Transactional(readOnly = true)
    public void exportDiscountsAndOffersAppliedReportToCsv(
            UUID restaurantId,
            String transactionStatus,
            LocalDate date,
            LocalDateTime startDate,
            LocalDateTime endDate,
            String locale,
            HttpServletResponse response) throws IOException {
        
        log.info("Exporting discounts and offers applied report to CSV for restaurant: {} (transactionStatus: {}, " +
                "date: {}, startDate: {}, endDate: {})", 
                restaurantId, transactionStatus, date, startDate, endDate);

        Locale userLocale = LocaleContextHolder.getLocale();

        // Validate restaurant
        Restaurant restaurant = restaurantRepository.findById(restaurantId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        messageUtil.getMessage(MSG_RESTAURANT_NOT_FOUND, userLocale)));

        // Parse transaction status filter
        Collection<TransactionStatus> transactionStatuses = null;
        if (transactionStatus != null && !transactionStatus.isBlank()) {
            try {
                transactionStatuses = Arrays.stream(transactionStatus.split(","))
                        .map(String::trim)
                        .filter(s -> !s.isBlank())
                        .map(s -> TransactionStatus.valueOf(s.toUpperCase()))
                        .collect(Collectors.toSet());
                if (transactionStatuses.isEmpty()) transactionStatuses = null;
            } catch (IllegalArgumentException e) {
                String errorMessage = messageUtil.getMessage(MSG_ERROR_INVALID_TRANSACTION_STATUS, userLocale, transactionStatus);
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, errorMessage);
            }
        }

        // Handle date filtering
        LocalDateTime startDateTime;
        LocalDateTime endDateTime;
        
        if (date != null) {
            startDateTime = date.atStartOfDay();
            endDateTime = date.atTime(23, 59, 59, 999999000);
        } else {
            startDateTime = (startDate != null) ? startDate : LocalDateTime.of(1900, 1, 1, 0, 0, 0);
            if (endDate != null) {
                endDateTime = endDate.toLocalDate().atTime(23, 59, 59, 999999000);
            } else {
                endDateTime = LocalDateTime.of(2100, 12, 31, 23, 59, 59, 999999000);
            }
        }

        // Build filters map for generic export engine
        Map<String, Object> filters = new HashMap<>();
        filters.put("restaurantId", restaurantId);
        filters.put("transactionStatuses", transactionStatuses);
        filters.put("date", date);
        filters.put("startDateTime", startDateTime);
        filters.put("endDateTime", endDateTime);

        // Legacy CSV export path retained until the generic report export service is wired here.
        
        // Fetch all transactions with discounts (no pagination for CSV export)
        log.debug("Querying transactions with discounts for CSV - restaurantId: {}, transactionStatuses: {}, startDate: {}, endDate: {}", 
                restaurantId, transactionStatuses, startDateTime, endDateTime);
        
        Pageable pageable = Pageable.unpaged();
        Page<Transaction> transactionsPage = transactionRepository.findTransactionsWithDiscounts(
                restaurantId, transactionStatuses,
                startDateTime.atOffset(java.time.ZoneOffset.UTC), endDateTime.atOffset(java.time.ZoneOffset.UTC), pageable);

        log.info("Found {} transactions with discounts for CSV export (total: {})", 
                transactionsPage.getContent().size(), transactionsPage.getTotalElements());

        List<DiscountOfferReportResponse> discountOffers = transactionsPage.getContent().stream()
                .map(this::convertToDiscountOfferReportResponse)
                .collect(Collectors.toList());

        // Calculate summary statistics
        DiscountOfferReportListDto.SummaryStatistics summary = calculateSummaryStatistics(
                transactionsPage.getContent());

        // Generate filename with timestamp
        String timestamp = LocalDateTime.now(ZoneOffset.UTC).format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
        String filename = "discounts_offers_report_" + timestamp + ".csv";

        // Set response headers
        response.setContentType("text/csv;charset=UTF-8");
        response.setCharacterEncoding("UTF-8");
        response.setHeader("Content-Disposition", "attachment; filename=\"" + filename + "\"");

        // Get output stream and write BOM for Excel compatibility
        java.io.OutputStream outputStream = response.getOutputStream();
        // Write UTF-8 BOM (0xEF 0xBB 0xBF)
        outputStream.write(0xEF);
        outputStream.write(0xBB);
        outputStream.write(0xBF);

        // Create CSV printer
        try (CSVPrinter csvPrinter = new CSVPrinter(
                new OutputStreamWriter(outputStream, StandardCharsets.UTF_8),
                CSVFormat.DEFAULT)) {

            // Write Header Section
            csvPrinter.printRecord("DISCOUNTS AND OFFERS APPLIED REPORT");
            csvPrinter.printRecord("Restaurant", getRestaurantName(restaurant, userLocale));
            csvPrinter.printRecord("Export Date", LocalDateTime.now(ZoneOffset.UTC).format(DateTimeFormatter.ofPattern(DATE_TIME_FORMAT)));
            if (date != null) {
                csvPrinter.printRecord("Report Date", date.toString());
            } else {
                csvPrinter.printRecord("Start Date", startDateTime.format(DateTimeFormatter.ofPattern(DATE_TIME_FORMAT)));
                csvPrinter.printRecord("End Date", endDateTime.format(DateTimeFormatter.ofPattern(DATE_TIME_FORMAT)));
            }
            csvPrinter.printRecord();

            // Write Summary Statistics
            csvPrinter.printRecord("SUMMARY STATISTICS");
            csvPrinter.printRecord("Metric", "Value");
            csvPrinter.printRecord("Total Orders with Discounts", summary.getTotalOrdersWithDiscounts());
            csvPrinter.printRecord("Total Discount Amount", summary.getTotalDiscountAmount() != null ? 
                    summary.getTotalDiscountAmount().toString() : "0.00");
            csvPrinter.printRecord("Total Additional Discount Amount", summary.getTotalAdditionalDiscountAmount() != null ? 
                    summary.getTotalAdditionalDiscountAmount().toString() : "0.00");
            csvPrinter.printRecord("Total Combined Discount Amount", summary.getTotalCombinedDiscountAmount() != null ? 
                    summary.getTotalCombinedDiscountAmount().toString() : "0.00");
            csvPrinter.printRecord("Orders with Order-Level Discount", summary.getOrdersWithOrderLevelDiscount());
            csvPrinter.printRecord("Orders with Additional Discount", summary.getOrdersWithAdditionalDiscount());
            csvPrinter.printRecord("Orders with Both Discounts", summary.getOrdersWithBothDiscounts());
            csvPrinter.printRecord();

            // Write Data Section
            csvPrinter.printRecord("DISCOUNT DETAILS");
            csvPrinter.printRecord(
                    "Order Number",
                    "Transaction Number",
                    "Transaction Date",
                    "Discount Code",
                    "Discount Type",
                    "Discount Value",
                    "Discount Amount",
                    "Additional Discount Value",
                    "Additional Discount Type",
                    "Additional Discount Amount",
                    "Additional Discount Reason",
                    "Subtotal",
                    "Total Amount",
                    "Total Discount Amount"
            );

            // Write data rows
            for (DiscountOfferReportResponse report : discountOffers) {
                csvPrinter.printRecord(
                        report.getOrderNumber() != null ? report.getOrderNumber() : "",
                        report.getTransactionNumber() != null ? report.getTransactionNumber() : "",
                        report.getTransactionDateTime() != null ? 
                                report.getTransactionDateTime().format(DateTimeFormatter.ofPattern(DATE_TIME_FORMAT)) : "",
                        report.getDiscountCode() != null ? report.getDiscountCode() : "",
                        report.getDiscountType() != null ? report.getDiscountType().toString() : "",
                        report.getDiscountValue() != null ? report.getDiscountValue().toString() : "",
                        report.getDiscountAmount() != null ? report.getDiscountAmount().toString() : "",
                        report.getAdditionalDiscountValue() != null ? report.getAdditionalDiscountValue().toString() : "",
                        report.getAdditionalDiscountType() != null ? report.getAdditionalDiscountType().toString() : "",
                        report.getAdditionalDiscountAmount() != null ? report.getAdditionalDiscountAmount().toString() : "",
                        report.getAdditionalDiscountReason() != null ? report.getAdditionalDiscountReason() : "",
                        report.getSubTotal() != null ? report.getSubTotal().toString() : "",
                        report.getTotalAmount() != null ? report.getTotalAmount().toString() : "",
                        report.getTotalDiscountAmount() != null ? report.getTotalDiscountAmount().toString() : ""
                );
            }

            csvPrinter.flush();
        }
        
        // Ensure the output stream is flushed
        outputStream.flush();
        // Ensure response is committed
        response.flushBuffer();

        log.info("Successfully exported discounts and offers applied report to CSV for restaurant: {} (total records: {})", 
                restaurantId, discountOffers.size());
    }

    // ==================== EXTRACTED HELPER METHODS ====================

    /**
     * Updates an audit trail record with request and review information for QA tracking.
     * Extracted from nested try block to improve readability.
     */
    private void updateAuditTrailWithRequestReviewInfo(AuditTrail auditTrail, Transaction transaction,
                                                        User reviewer, OffsetDateTime reviewedAt) {
        try {
            entityManager.createNativeQuery(
                    "UPDATE audit_trail SET " +
                    "requested_by = :requestedById, " +
                    "requested_at = :requestedAt, " +
                    "reviewed_by = :reviewedById, " +
                    "reviewed_at = :reviewedAt " +
                    "WHERE id = :auditTrailId")
                    .setParameter("requestedById", transaction.getRequestedBy() != null ? transaction.getRequestedBy().getId() : null)
                    .setParameter("requestedAt", transaction.getRequestedAt() != null ? transaction.getRequestedAt() : null)
                    .setParameter("reviewedById", reviewer.getId())
                    .setParameter("reviewedAt", reviewedAt)
                    .setParameter("auditTrailId", auditTrail.getId())
                    .executeUpdate();
            entityManager.flush();
            log.debug("Updated audit trail {} with request and review information for QA tracking", auditTrail.getLogNumber());
        } catch (Exception updateEx) {
            log.error("Failed to update audit trail with request/review info: {}", updateEx.getMessage(), updateEx);
        }
    }

    /**
     * Creates an audit trail entry for a cashier's cancellation request.
     * Extracted from nested try block in handleTransactionCancellationRequest.
     */
    private void createCancellationRequestAuditTrail(User authenticatedUser, Transaction transaction, String reason) {
        try {
            String cancellationReason = reason != null ? reason : "N/A";
            auditTrailService.createAuditTrail(
                    authenticatedUser,
                    ActionType.CANCELLATION,
                    transaction.getRestaurant(),
                    RequestStatus.OPEN,
                    null, // IP address not available
                    null, // User agent not available
                    transaction.getId(),
                    ACTION_TYPE_TRANSACTION,
                    String.format("Transaction cancellation request created. Reason: %s", cancellationReason)
            );
        } catch (Exception e) {
            log.error("Failed to create audit trail for cashier cancellation request: {}", e.getMessage(), e);
        }
    }

    /**
     * Notifies managers about a newly opened cancellation request.
     * Extracted from nested try block in handleTransactionCancellationRequest.
     */
    private void notifyManagersAboutRequest(Transaction transaction, Locale userLocale) {
        try {
            UUID restaurantId = transaction.getRestaurant().getId();
            Optional<Role> managerRoleOpt = roleRepository.findByName(ROLE_MANAGER);
            if (managerRoleOpt.isPresent()) {
                UUID managerRoleId = managerRoleOpt.get().getId();
                List<User> managers = userRepository.findAllByRestaurantIdAndRoleIdAndIsDeletedFalse(restaurantId, managerRoleId);
                if (!managers.isEmpty()) {
                    notificationService.notifyTransactionCancellationRequestOpened(transaction, managers, transaction.getRequestedBy(), userLocale);
                }
            }
        } catch (Exception e) {
            log.error("Failed to send manager notification for cancellation request: {}", e.getMessage(), e);
        }
    }

    /**
     * Checks for an existing transaction cancellation request and logs if clearing it.
     * Extracted from nested try block in createRefundRequest.
     */
    private void clearExistingCancellationRequestIfPresent(Transaction transaction) {
        if (transaction.getRequestStatus() == RequestStatus.OPEN && transaction.getRequestData() != null) {
            try {
                ObjectMapper objectMapper = new ObjectMapper();
                @SuppressWarnings("unchecked")
                Map<String, Object> existingRequestData = objectMapper.readValue(transaction.getRequestData(), Map.class);
                // If it's not a refund request, it's a transaction cancellation request - clear it
                if (!REQUEST_TYPE_REFUND.equals(existingRequestData.get(FIELD_REQUEST_TYPE))) {
                    log.info("Clearing existing transaction cancellation request for transaction {} before creating refund request", transaction.getId());
                }
            } catch (JsonProcessingException e) {
                // Invalid JSON, proceed to overwrite
            }
        }
    }

    /**
     * Creates an audit trail entry for a cashier's refund request.
     * Extracted from nested try block in createRefundRequest.
     */
    private void createRefundRequestAuditTrail(User user, Transaction transaction, String refundReason) {
        try {
            String reason = refundReason != null ? refundReason : "N/A";
            auditTrailService.createAuditTrail(
                    user,
                    ActionType.REFUND,
                    transaction.getRestaurant(),
                    RequestStatus.OPEN,
                    null, // IP address not available
                    null, // User agent not available
                    transaction.getId(),
                    ACTION_TYPE_TRANSACTION,
                    String.format("Refund request created. Reason: %s", reason)
            );
        } catch (Exception e) {
            log.error("Failed to create audit trail for cashier refund request: {}", e.getMessage(), e);
        }
    }

    /**
     * Notifies managers about a newly opened refund request.
     * Extracted from nested try block in createRefundRequest.
     */
    private void notifyManagersAboutRefundRequest(Transaction transaction, Locale userLocale) {
        try {
            if (transaction.getRestaurant() != null) {
                UUID restaurantId = transaction.getRestaurant().getId();
                Optional<Role> managerRoleOpt = roleRepository.findByName(ROLE_MANAGER);
                if (managerRoleOpt.isPresent()) {
                    UUID managerRoleId = managerRoleOpt.get().getId();
                    List<User> managers = userRepository.findAllByRestaurantIdAndRoleIdAndIsDeletedFalse(restaurantId, managerRoleId);
                    if (!managers.isEmpty()) {
                        notificationService.notifyRefundRequestOpened(transaction, managers, transaction.getRequestedBy(), userLocale);
                    }
                }
            } else {
                log.warn("Transaction {} has no restaurant associated - skipping manager refund request notification", transaction.getId());
            }
        } catch (Exception e) {
            log.error("Failed to send manager notification for refund request: {}", e.getMessage(), e);
        }
    }

    /**
     * Creates an audit trail entry for a processed refund (approved by manager).
     * Extracted from nested try block in processDirectRefund.
     */
    private void createRefundProcessedAuditTrail(User user, Transaction transaction,
                                                  BigDecimal totalRefundAmount, String refundReason) {
        try {
            auditTrailService.createAuditTrail(
                    user,
                    ActionType.REFUND,
                    transaction.getRestaurant(),
                    RequestStatus.APPROVED,
                    null, // IP address not available
                    null, // User agent not available
                    transaction.getId(),
                    ACTION_TYPE_TRANSACTION,
                    String.format("Refund processed: Amount %s, Reason: %s", totalRefundAmount, refundReason),
                    null, // openingBalance
                    null, // closingBalance
                    null, // expectedBalance
                    null, // discrepancyAmount
                    null, // discrepancyReason
                    user // createdBy
            );
        } catch (Exception e) {
            log.error("Failed to create audit trail for refund: {}", e.getMessage());
        }
    }

    /**
     * Evaluates real-time HQ alerts after a transaction commits.
     * Handles both active transaction (deferred via TransactionSynchronization) and
     * no-active-transaction (immediate) cases.
     * Extracted to eliminate duplicated nested try blocks across multiple methods.
     *
     * @param restaurant the restaurant to evaluate alerts for (may be null)
     * @param userLocale the user's locale
     * @param context    a descriptive context string for log messages (e.g. "refund", "transaction cancellation")
     */
    private void evaluateAlertsAfterTransactionCommit(Restaurant restaurant, Locale userLocale, String context) {
        if (restaurant == null) {
            log.warn("Restaurant is null, skipping alert evaluation after {}.", context);
            return;
        }

        // Check if alert evaluation service is available (lazy injection)
        if (restaurantAlertEvaluationService == null) {
            log.warn("⚠️ RestaurantAlertEvaluationService is null (lazy injection not initialized), skipping alert evaluation after {} for restaurant: {}", 
                    context, restaurant.getRestaurantCode());
            return;
        }

        final Restaurant finalRestaurant = restaurant;
        final Locale finalUserLocale = userLocale;

        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                /**
                 * Runs after the surrounding transaction commits to ensure newly persisted rows
                 * (e.g., refunds/refund-items or cancellation updates) are visible to the alert evaluation transaction.
                 */
                @Override
                public void afterCommit() {
                    try {
                        log.info("🔔 Triggering alert evaluation for restaurant: {} after {} commit",
                                finalRestaurant.getRestaurantCode(), context);
                        if (restaurantAlertEvaluationService == null) {
                            log.error("❌ RestaurantAlertEvaluationService is null in afterCommit callback - lazy injection failed");
                            return;
                        }
                        restaurantAlertEvaluationService.evaluateRestaurantAlertsRealtime(finalRestaurant, finalUserLocale);
                        log.info("✅ Alert evaluation completed for restaurant: {} after {} commit",
                                finalRestaurant.getRestaurantCode(), context);
                    } catch (Exception e) {
                        log.error("❌ Failed to evaluate real-time alerts after {} commit: {}", context, e.getMessage(), e);
                    }
                }
            });
            log.info("📋 Registered alert evaluation to run after {} commit for restaurant: {}",
                    context, restaurant.getRestaurantCode());
        } else {
            try {
                log.info("🔔 Triggering alert evaluation for restaurant: {} after {} (no active transaction)",
                        restaurant.getRestaurantCode(), context);
                if (restaurantAlertEvaluationService == null) {
                    log.error("❌ RestaurantAlertEvaluationService is null (no active transaction) - lazy injection failed");
                    return;
                }
                restaurantAlertEvaluationService.evaluateRestaurantAlertsRealtime(restaurant, userLocale);
                log.info("✅ Alert evaluation completed for restaurant: {} after {} (no active transaction)",
                        restaurant.getRestaurantCode(), context);
            } catch (Exception e) {
                log.error("❌ Failed to evaluate real-time alerts after {} (no active transaction): {}",
                        context, e.getMessage(), e);
            }
        }
    }

}
