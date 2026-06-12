package com.gulfnet.restaurantmanagement.service.impl;

import com.gulfnet.restaurantmanagement.service.PrintRequestService;
import com.gulfnet.restaurantmanagement.util.MessageUtil;
import com.gulfnet.shared_library.config.AWSService;
import com.gulfnet.shared_library.entity.Order;
import com.gulfnet.shared_library.entity.PrintRequest;
import com.gulfnet.shared_library.entity.Refund;
import com.gulfnet.shared_library.entity.Restaurant;
import com.gulfnet.shared_library.entity.RestaurantTable;
import com.gulfnet.shared_library.entity.Role;
import com.gulfnet.shared_library.entity.Transaction;
import com.gulfnet.shared_library.entity.User;
import com.gulfnet.shared_library.enums.RequestStatus;
import com.gulfnet.shared_library.model.request.PrintRequestCreateRequest;
import com.gulfnet.shared_library.model.request.PrintRequestDecisionRequest;
import com.gulfnet.shared_library.model.request.StatusEventMessage;
import com.gulfnet.shared_library.model.response.dto.PaginationMetaData;
import com.gulfnet.shared_library.model.response.dto.PrintRequestListResponse;
import com.gulfnet.shared_library.model.response.dto.PrintRequestResponse;
import com.gulfnet.shared_library.model.response.dto.ResponseDto;
import com.gulfnet.shared_library.repository.OrderRepository;
import com.gulfnet.shared_library.repository.PrintRequestRepository;
import com.gulfnet.shared_library.repository.RefundRepository;
import com.gulfnet.shared_library.repository.RestaurantRepository;
import com.gulfnet.shared_library.repository.RestaurantTableRepository;
import com.gulfnet.shared_library.repository.RoleRepository;
import com.gulfnet.shared_library.repository.TransactionRepository;
import com.gulfnet.shared_library.repository.UserRepository;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import lombok.extern.slf4j.Slf4j;
import static com.gulfnet.restaurantmanagement.config.RabbitMQConfig.*;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@Slf4j
public class PrintRequestServiceImpl implements PrintRequestService {
    
    // Message key constants
    private static final String MSG_CASHIER_NOT_FOUND = "cashier.not.found";

    @Autowired
    private PrintRequestRepository printRequestRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RestaurantRepository restaurantRepository;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private RestaurantTableRepository restaurantTableRepository;

    @Autowired
    private TransactionRepository transactionRepository;

    @Autowired
    private RefundRepository refundRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private MessageUtil messageUtil;

    @Autowired
    private AWSService awsService;

    @Autowired
    private SimpMessagingTemplate messagingTemplate;

    @Autowired(required = false)
    private RabbitTemplate rabbitTemplate;

    // Constants
    private static final String ROLE_CASHIER = "CASHIER";
    private static final String PARAM_CASHIER_ID = "cashierId";

    /**
     * Creates a new print request.
     * Only MANAGER or WAITER roles can create print requests.
     * Derives restaurant from the requester and validates associated order, table, and refund (if provided).
     * Sends WebSocket notification for the new print request.
     *
     * @param request Print request creation request containing order ID, table ID, refund ID (all optional)
     * @param userId UUID of the user creating the request
     * @param userRole Role of the user (must be MANAGER or WAITER)
     * @param locale Locale for message localization
     * @return ResponseDto containing the created print request
     */
    @Override
    @Transactional
    public ResponseDto<PrintRequestResponse> createPrintRequest(PrintRequestCreateRequest request, String userId, String userRole, String locale) {
        Locale userLocale = Locale.forLanguageTag(locale != null ? locale : "en");
        
        UUID requesterUuid = parseUuid(userId, "userId", userLocale);

        // Only MANAGER / WAITER can create print requests (can be relaxed later)
        if (!"MANAGER".equalsIgnoreCase(userRole) && !"WAITER".equalsIgnoreCase(userRole)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    messageUtil.getMessage("print.request.create.error.role.not.allowed", userLocale));
        }

        User requester = userRepository.findById(requesterUuid)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        messageUtil.getMessage("user.not.found", userLocale, userId)));

        // Always derive restaurant from the requester (manager/waiter)
        UUID restaurantId = requester.getRestaurantId();
        if (restaurantId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    messageUtil.getMessage("print.request.create.error.no.restaurant", userLocale));
        }

        Restaurant restaurant = restaurantRepository.findByIdAndIsDeletedFalse(restaurantId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        messageUtil.getMessage("restaurant.not.found", userLocale)));

        // Fetch order if orderId is provided
        Order order = null;
        String orderNumber = null;
        if (request.getOrderId() != null) {
            order = orderRepository.findById(request.getOrderId())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                            messageUtil.getMessage("order.not.found", userLocale)));
            // Get orderNumber from the order entity
            orderNumber = order.getOrderNumber();
        }

        // Fetch restaurant table if restaurantTableId is provided
        RestaurantTable restaurantTable = null;
        if (request.getRestaurantTableId() != null) {
            restaurantTable = restaurantTableRepository.findById(request.getRestaurantTableId())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                            messageUtil.getMessage("table.not.found", userLocale)));
        }

        // Don't store fileUrl - it will be derived dynamically in the response
        PrintRequest entity = PrintRequest.builder()
                .restaurant(restaurant)
                .requestedBy(requester)
                .requestStatus(RequestStatus.OPEN)
                .fileUrl(null) // Not storing fileUrl in database
                .orderNumber(orderNumber)
                .order(order)
                .restaurantTable(restaurantTable)
                .refundId(request.getRefundId())
                .requestedAt(OffsetDateTime.now(ZoneOffset.UTC))
                .createdBy(requester)
                .build();

        PrintRequest saved = printRequestRepository.save(entity);

        // Send WebSocket notification for new print request
        sendPrintRequestWebSocketNotification(userLocale, restaurantId, saved.getId(), RequestStatus.OPEN);

        PrintRequestResponse dto = mapToResponse(saved);
        return ResponseDto.<PrintRequestResponse>builder()
                .data(dto)
                .message(messageUtil.getMessage("print.request.create.success", userLocale))
                .build();
    }

    /**
     * Retrieves pending (OPEN status) print requests for a cashier.
     * Only CASHIER role can access this endpoint.
     * Returns requests filtered by the cashier's restaurant and paginated in memory.
     *
     * @param page Page number (1-based, optional)
     * @param size Page size (optional, max 100)
     * @param cashierId UUID of the cashier
     * @param cashierRole Role of the cashier (must be CASHIER)
     * @param locale Locale for message localization
     * @return ResponseDto containing a paginated list of pending print requests
     */
    @Override
    public ResponseDto<PrintRequestListResponse> getPendingRequestsForCashier(Integer page, Integer size, String cashierId, String cashierRole, String locale) {
        Locale userLocale = Locale.forLanguageTag(locale != null ? locale : "en");
        
        UUID cashierUuid = parseUuid(cashierId, PARAM_CASHIER_ID, userLocale);

        if (!ROLE_CASHIER.equalsIgnoreCase(cashierRole)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    messageUtil.getMessage("print.request.view.error.role.not.allowed", userLocale));
        }

        User cashier = userRepository.findById(cashierUuid)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        messageUtil.getMessage(MSG_CASHIER_NOT_FOUND, userLocale)));

        if (cashier.getRestaurantId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    messageUtil.getMessage("print.request.view.error.no.restaurant", userLocale));
        }

        // Validate & normalize pagination
        int pageNumber = (page != null ? page : 1) - 1;
        if (pageNumber < 0) pageNumber = 0;
        int pageSize = (size != null && size > 0) ? size : Integer.MAX_VALUE;
        if (pageSize > 100) {
            pageSize = 100; // Maximum page size limit
        }

        // Fetch all (but sorted), paginate in memory
        Pageable pageable = PageRequest.of(0, Integer.MAX_VALUE, Sort.by(Sort.Direction.DESC, "requestedAt"));
        Page<PrintRequest> resultPage = printRequestRepository
                .findByRestaurant_IdAndRequestStatus(cashier.getRestaurantId(), RequestStatus.OPEN, pageable);

        List<PrintRequestResponse> allItems = resultPage.getContent().stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());

        // Pagination in memory
        int fromIndex = Math.min(pageNumber * pageSize, allItems.size());
        int toIndex = Math.min(fromIndex + pageSize, allItems.size());
        List<PrintRequestResponse> paginatedItems = allItems.subList(fromIndex, toIndex);

        // Build paginated response
        return buildPaginatedPrintRequestListResponse(paginatedItems, allItems, pageNumber, pageSize, userLocale, "print.request.view.success");
    }

    /**
     * Retrieves all print requests created by a specific requester.
     * Returns all requests (regardless of status) for the requester, paginated in memory.
     *
     * @param page Page number (1-based, optional)
     * @param size Page size (optional, max 100)
     * @param requesterId UUID of the requester
     * @param locale Locale for message localization
     * @return ResponseDto containing a paginated list of print requests for the requester
     */
    @Override
    public ResponseDto<PrintRequestListResponse> getRequestsForRequester(Integer page, Integer size, String requesterId, String locale) {
        Locale userLocale = Locale.forLanguageTag(locale != null ? locale : "en");
        
        UUID requesterUuid = parseUuid(requesterId, "requesterId", userLocale);

        // Validate & normalize pagination
        int pageNumber = (page != null ? page : 1) - 1;
        if (pageNumber < 0) pageNumber = 0;
        int pageSize = (size != null && size > 0) ? size : Integer.MAX_VALUE;
        if (pageSize > 100) {
            pageSize = 100; // Maximum page size limit
        }

        // Fetch all (but sorted), paginate in memory
        Pageable pageable = PageRequest.of(0, Integer.MAX_VALUE, Sort.by(Sort.Direction.DESC, "requestedAt"));
        Page<PrintRequest> resultPage = printRequestRepository
                .findByRequestedBy_Id(requesterUuid, pageable);

        List<PrintRequestResponse> allItems = resultPage.getContent().stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());

        // Pagination in memory
        int fromIndex = Math.min(pageNumber * pageSize, allItems.size());
        int toIndex = Math.min(fromIndex + pageSize, allItems.size());
        List<PrintRequestResponse> paginatedItems = allItems.subList(fromIndex, toIndex);

        // Build paginated response
        return buildPaginatedPrintRequestListResponse(paginatedItems, allItems, pageNumber, pageSize, userLocale, "print.request.list.success");
    }

    /**
     * Approves a print request.
     * Only CASHIER role can approve requests.
     * Validates that the request is OPEN and that the cashier belongs to the same restaurant as the request.
     * Updates request status to APPROVED and sets approval/completion timestamps.
     * Sends WebSocket notification for the approval.
     *
     * @param requestId UUID of the print request to approve
     * @param decision Decision request containing optional comments
     * @param cashierId UUID of the cashier approving the request
     * @param cashierRole Role of the cashier (must be CASHIER)
     * @param locale Locale for message localization
     * @return ResponseDto containing the approved print request
     */
    @Override
    @Transactional
    public ResponseDto<PrintRequestResponse> approvePrintRequest(UUID requestId, PrintRequestDecisionRequest decision, String cashierId, String cashierRole, String locale) {
        Locale userLocale = Locale.forLanguageTag(locale != null ? locale : "en");
        
        UUID cashierUuid = parseUuid(cashierId, PARAM_CASHIER_ID, userLocale);

        if (!ROLE_CASHIER.equalsIgnoreCase(cashierRole)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    messageUtil.getMessage("print.request.approve.error.role.not.allowed", userLocale));
        }

        User cashier = userRepository.findById(cashierUuid)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        messageUtil.getMessage(MSG_CASHIER_NOT_FOUND, userLocale)));

        PrintRequest entity = printRequestRepository.findById(requestId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        messageUtil.getMessage("print.request.not.found", userLocale)));

        if (entity.getRequestStatus() != RequestStatus.OPEN) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    messageUtil.getMessage("print.request.approve.error.not.open", userLocale));
        }

        // Ensure cashier belongs to same restaurant
        if (cashier.getRestaurantId() == null ||
                entity.getRestaurant() == null ||
                !cashier.getRestaurantId().equals(entity.getRestaurant().getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    messageUtil.getMessage("print.request.approve.error.restaurant.mismatch", userLocale));
        }

        entity.setRequestStatus(RequestStatus.APPROVED);
        entity.setApprovedBy(cashier);
        entity.setApprovedAt(OffsetDateTime.now(ZoneOffset.UTC));
        entity.setCompletedAt(OffsetDateTime.now(ZoneOffset.UTC));
        entity.setUpdatedBy(cashier);
        if (decision != null && decision.getComments() != null) {
            entity.setComments(decision.getComments());
        }

        PrintRequest saved = printRequestRepository.save(entity);
        
        // Send WebSocket notification for print request approval
        sendPrintRequestWebSocketNotification(userLocale, saved.getRestaurant().getId(), saved.getId(), RequestStatus.APPROVED);

        PrintRequestResponse dto = mapToResponse(saved);

        return ResponseDto.<PrintRequestResponse>builder()
                .data(dto)
                .message(messageUtil.getMessage("print.request.approve.success", userLocale))
                .build();
    }

    /**
     * Declines a print request.
     * Only CASHIER role can decline requests.
     * Validates that the request is OPEN and that the cashier belongs to the same restaurant as the request.
     * Updates request status to DECLINED and sets approval timestamp (but not completion timestamp).
     * Sends WebSocket notification for the decline.
     *
     * @param requestId UUID of the print request to decline
     * @param decision Decision request containing optional comments
     * @param cashierId UUID of the cashier declining the request
     * @param cashierRole Role of the cashier (must be CASHIER)
     * @param locale Locale for message localization
     * @return ResponseDto containing the declined print request
     */
    @Override
    @Transactional
    public ResponseDto<PrintRequestResponse> declinePrintRequest(UUID requestId, PrintRequestDecisionRequest decision, String cashierId, String cashierRole, String locale) {
        Locale userLocale = Locale.forLanguageTag(locale != null ? locale : "en");
        
        UUID cashierUuid = parseUuid(cashierId, PARAM_CASHIER_ID, userLocale);

        if (!ROLE_CASHIER.equalsIgnoreCase(cashierRole)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    messageUtil.getMessage("print.request.decline.error.role.not.allowed", userLocale));
        }

        User cashier = userRepository.findById(cashierUuid)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        messageUtil.getMessage(MSG_CASHIER_NOT_FOUND, userLocale)));

        PrintRequest entity = printRequestRepository.findById(requestId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        messageUtil.getMessage("print.request.not.found", userLocale)));

        if (entity.getRequestStatus() != RequestStatus.OPEN) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    messageUtil.getMessage("print.request.decline.error.not.open", userLocale));
        }

        // Ensure cashier belongs to same restaurant
        if (cashier.getRestaurantId() == null ||
                entity.getRestaurant() == null ||
                !cashier.getRestaurantId().equals(entity.getRestaurant().getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    messageUtil.getMessage("print.request.decline.error.restaurant.mismatch", userLocale));
        }

        entity.setRequestStatus(RequestStatus.DECLINED);
        entity.setApprovedBy(cashier);
        entity.setApprovedAt(OffsetDateTime.now(ZoneOffset.UTC));
        entity.setUpdatedBy(cashier);
        // Don't set completedAt for declined requests
        if (decision != null && decision.getComments() != null) {
            entity.setComments(decision.getComments());
        }

        PrintRequest saved = printRequestRepository.save(entity);
        
        // Send WebSocket notification for print request decline
        sendPrintRequestWebSocketNotification(userLocale, saved.getRestaurant().getId(), saved.getId(), RequestStatus.DECLINED);

        PrintRequestResponse dto = mapToResponse(saved);

        return ResponseDto.<PrintRequestResponse>builder()
                .data(dto)
                .message(messageUtil.getMessage("print.request.decline.success", userLocale))
                .build();
    }

    /**
     * Helper method to build a paginated PrintRequestListResponse.
     * 
     * @param paginatedItems The paginated list of items for the current page
     * @param allItems The complete list of all items (for total count)
     * @param pageNumber The current page number (0-based)
     * @param pageSize The page size
     * @param userLocale The locale for the response message
     * @param messageKey The message key for the response
     * @return ResponseDto containing PrintRequestListResponse
     */
    private ResponseDto<PrintRequestListResponse> buildPaginatedPrintRequestListResponse(
            List<PrintRequestResponse> paginatedItems,
            List<PrintRequestResponse> allItems,
            int pageNumber,
            int pageSize,
            Locale userLocale,
            String messageKey) {
        
        // Metadata
        PaginationMetaData metaData = PaginationMetaData.builder()
                .page(pageNumber + 1)
                .size(pageSize)
                .totalPages((int) Math.ceil((double) allItems.size() / pageSize))
                .totalRecords((long) allItems.size())
                .build();

        PrintRequestListResponse listResponse = PrintRequestListResponse.builder()
                .items(paginatedItems)
                .count((long) paginatedItems.size())
                .total((long) allItems.size())
                .metaData(metaData)
                .build();

        return ResponseDto.<PrintRequestListResponse>builder()
                .data(listResponse)
                .message(messageUtil.getMessage(messageKey, userLocale))
                .build();
    }

    private UUID parseUuid(String value, String fieldName, Locale locale) {
        try {
            return UUID.fromString(value);
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    messageUtil.getMessage("error.invalid.uuid", locale, fieldName));
        }
    }

    /**
     * Maps a PrintRequest entity to a PrintRequestResponse DTO.
     * Derives file URL dynamically based on request type (order, refund, or table) and converts it to a pre-signed URL.
     * Includes restaurant, requester, approver, order, table, and refund information.
     *
     * @param entity The PrintRequest entity to map
     * @return PrintRequestResponse DTO with all mapped fields and pre-signed file URL
     */
    private PrintRequestResponse mapToResponse(PrintRequest entity) {
        UUID restaurantId = entity.getRestaurant() != null ? entity.getRestaurant().getId() : null;
        String restaurantCode = entity.getRestaurant() != null ? entity.getRestaurant().getRestaurantCode() : null;

        UUID requestedById = entity.getRequestedBy() != null ? entity.getRequestedBy().getId() : null;
        String requestedByName = buildFullName(entity.getRequestedBy());
        String requestedByRole = getRoleName(entity.getRequestedBy());

        UUID approvedById = entity.getApprovedBy() != null ? entity.getApprovedBy().getId() : null;
        String approvedByName = buildFullName(entity.getApprovedBy());
        String approvedByRole = getRoleName(entity.getApprovedBy());

        // Derive fileUrl dynamically and convert to signed URL
        String fileUrl = deriveFileUrlForResponse(entity);
        String preSignedUrl = convertToPreSignedUrlForPrintRequest(fileUrl);

        UUID orderId = entity.getOrder() != null ? entity.getOrder().getId() : null;
        UUID restaurantTableId = null;
        String tableCode = null;
        
        // Get restaurantTableId and tableCode if restaurantTable is not null
        if (entity.getRestaurantTable() != null) {
            try {
                // Reload the table to ensure we have the latest data (lazy loading)
                RestaurantTable table = restaurantTableRepository.findById(entity.getRestaurantTable().getId()).orElse(null);
                if (table != null) {
                    restaurantTableId = table.getId();
                    tableCode = table.getTableCode();
                }
            } catch (Exception e) {
                log.warn("Failed to get restaurant table details for table {}: {}", 
                        entity.getRestaurantTable().getId(), e.getMessage());
            }
        }

        // Get refundNumber if refundId is present
        String refundNumber = null;
        if (entity.getRefundId() != null) {
            try {
                Refund refund = refundRepository.findById(entity.getRefundId()).orElse(null);
                if (refund != null) {
                    refundNumber = refund.getRefundNumber();
                }
            } catch (Exception e) {
                log.warn("Failed to get refund number for refund {}: {}", entity.getRefundId(), e.getMessage());
            }
        }

        return PrintRequestResponse.builder()
                .id(entity.getId())
                .restaurantId(restaurantId)
                .restaurantCode(restaurantCode)
                .requestedById(requestedById)
                .requestedByName(requestedByName)
                .requestedByRole(requestedByRole)
                .approvedById(approvedById)
                .approvedByName(approvedByName)
                .approvedByRole(approvedByRole)
                .requestStatus(entity.getRequestStatus())
                .fileUrl(preSignedUrl)
                .orderNumber(entity.getOrderNumber())
                .orderId(orderId)
                .restaurantTableId(restaurantTableId)
                .tableCode(tableCode)
                .refundId(entity.getRefundId())
                .refundNumber(refundNumber)
                .requestedAt(entity.getRequestedAt())
                .approvedAt(entity.getApprovedAt())
                .completedAt(entity.getCompletedAt())
                .comments(entity.getComments())
                .build();
    }

    /**
     * Helper method to build full name from User entity, handling null cases
     */
    private String buildFullName(User user) {
        if (user == null) {
            return null;
        }
        String firstName = user.getFirstName() != null ? user.getFirstName() : "";
        String lastName = user.getLastName() != null ? user.getLastName() : "";
        String fullName = (firstName + " " + lastName).trim();
        return fullName.isEmpty() ? null : fullName;
    }

    /**
     * Helper method to get role name from User entity
     */
    private String getRoleName(User user) {
        if (user == null || user.getRoleId() == null) {
            return null;
        }
        try {
            return roleRepository.findById(user.getRoleId())
                    .map(Role::getName)
                    .orElse(null);
        } catch (Exception e) {
            log.warn("Error fetching role for user {}: {}", user.getId(), e.getMessage());
            return null;
        }
    }

    /**
     * Derive fileUrl dynamically from the print request entity for response:
     * - For QR code requests: use restaurantTable.printQrCodeUrl
     * - For order receipts: use transaction.receiptUrl
     * - For refund receipts: use refund.receiptUrl
     */
    private String deriveFileUrlForResponse(PrintRequest entity) {
        // Priority 1: QR code print request (restaurantTable provided)
        if (entity.getRestaurantTable() != null) {
            try {
                // Reload the table to ensure we have the latest printQrCodeUrl (lazy loading)
                RestaurantTable table = restaurantTableRepository.findById(entity.getRestaurantTable().getId()).orElse(null);
                if (table != null && table.getPrintQrCodeUrl() != null && !table.getPrintQrCodeUrl().trim().isEmpty()) {
                    return table.getPrintQrCodeUrl();
                }
            } catch (Exception e) {
                log.warn("Failed to get restaurant table printQrCodeUrl for table {}: {}", 
                        entity.getRestaurantTable().getId(), e.getMessage());
            }
        }

        // Priority 2: Refund receipt (refundId provided)
        if (entity.getRefundId() != null) {
            try {
                Refund refund = refundRepository.findById(entity.getRefundId()).orElse(null);
                if (refund != null && refund.getReceiptUrl() != null && !refund.getReceiptUrl().trim().isEmpty()) {
                    return refund.getReceiptUrl();
                }
            } catch (Exception e) {
                log.warn("Failed to get refund receipt URL for refund {}: {}", entity.getRefundId(), e.getMessage());
            }
        }

        // Priority 3: Order receipt (order provided)
        if (entity.getOrder() != null) {
            try {
                // Get the transaction for this order
                Optional<Transaction> transactionOpt = transactionRepository.findByOrderId(entity.getOrder().getId());
                if (transactionOpt.isPresent()) {
                    Transaction transaction = transactionOpt.get();
                    if (transaction.getReceiptUrl() != null && !transaction.getReceiptUrl().trim().isEmpty()) {
                        return transaction.getReceiptUrl();
                    }
                }
            } catch (Exception e) {
                log.warn("Failed to get transaction receipt URL for order {}: {}", entity.getOrder().getId(), e.getMessage());
            }
        }

        // If no fileUrl can be derived, return null (will be handled in convertToPreSignedUrlForPrintRequest)
        log.warn("Cannot derive file URL for print request {}", entity.getId());
        return null;
    }

    /**
     * Convert file URL to pre-signed URL for cashier to use.
     * Uses getPreSignedUrlForPdf for PDF files (QR codes, receipts) to enable inline preview.
     */
    private String convertToPreSignedUrlForPrintRequest(String fileUrl) {
        if (fileUrl == null || fileUrl.trim().isEmpty()) {
            return null;
        }
        try {
            // Use getPreSignedUrlForPdf for PDF files (QR codes and receipts are PDFs)
            // This sets proper Content-Type and Content-Disposition headers for inline preview
            return awsService.getPreSignedUrlForPdf(fileUrl);
        } catch (Exception e) {
            log.warn("Failed to convert file URL to presigned URL: {}", fileUrl, e);
            // If conversion fails, return null
            return null;
        }
    }

    /**
     * Sends WebSocket notification for print request status update.
     */
    private void sendPrintRequestWebSocketNotification(Locale userLocale, UUID restaurantId, UUID printRequestId, RequestStatus status) {
        try {
            String topic = "/topic/restaurant/" + restaurantId + "/print-request";
            Map<String, Object> data = new HashMap<>();
            data.put("printRequestId", printRequestId.toString());
            data.put("restaurantId", restaurantId.toString());

            StatusEventMessage eventMessage = StatusEventMessage.builder()
                    .message(messageUtil.getMessage("print.request.status.updated", Locale.ENGLISH))
                    .notificationType("PRINT_REQUEST_" + status.name())
                    .status(status.name())
                    .data(data)
                    .build();
            messagingTemplate.convertAndSend(topic, eventMessage);
            log.info("[Notification][WebSocket] broadcast topic={} notificationType={} printRequestId={} status={} restaurantId={}",
                    topic, "PRINT_REQUEST_" + status.name(), printRequestId, status, restaurantId);
            
            // Also publish to RabbitMQ for integration service to log
            publishToRabbitMQ(topic, eventMessage);
        } catch (Exception e) {
            log.error("Failed to send WebSocket notification for print request status update: {}", e.getMessage());
        }
    }

    /**
     * Helper method to publish WebSocket messages to RabbitMQ for integration service logging
     */
    private void publishToRabbitMQ(String topic, StatusEventMessage eventMessage) {
        if (rabbitTemplate != null) {
            try {
                Map<String, Object> wsMessage = new HashMap<>();
                wsMessage.put("topic", topic);
                wsMessage.put("message", eventMessage.getMessage());
                wsMessage.put("timestamp", LocalDateTime.now(ZoneOffset.UTC).format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
                wsMessage.put("type", "websocket_notification");
                wsMessage.put(WEBSOCKET_MSG_SUPPRESS_LOCAL_FORWARD, Boolean.TRUE);

                rabbitTemplate.convertAndSend(WEBSOCKET_TOPIC_EXCHANGE, WEBSOCKET_ROUTING_KEY, wsMessage);
                log.info("[Notification][FCM] rabbitPublish exchange={} routingKey={} payloadWsTopic={} notificationType={}",
                        WEBSOCKET_TOPIC_EXCHANGE, WEBSOCKET_ROUTING_KEY, topic, eventMessage.getNotificationType());
            } catch (Exception e) {
                log.warn("[Notification][FCM] rabbitPublish failed payloadWsTopic={}: {}", topic, e.getMessage());
            }
        } else {
            log.debug("RabbitTemplate not available, skipping RabbitMQ publish payloadWsTopic={}", topic);
        }
    }
}


