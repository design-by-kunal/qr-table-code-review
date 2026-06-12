package com.gulfnet.usermanagement.request.details;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gulfnet.shared_library.config.AWSService;
import com.gulfnet.shared_library.entity.*;
import com.gulfnet.shared_library.enums.*;
import com.gulfnet.shared_library.model.request.UserProfileUpdateRequest;
import com.gulfnet.shared_library.model.response.dto.*;
import com.gulfnet.shared_library.repository.*;
import com.gulfnet.usermanagement.util.MessageUtil;
import com.gulfnet.usermanagement.util.PaymentMethodDisplaySupport;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.http.HttpStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class RequestDetailsServiceImpl {

    private static final Logger log = LoggerFactory.getLogger(RequestDetailsServiceImpl.class);

    private final RequestDetailsCollaborator collaborator;
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final RestaurantRepository restaurantRepository;
    private final OrderRepository orderRepository;
    private final TransactionRepository transactionRepository;
    private final OrderedItemRepository orderedItemRepository;
    private final OrderedComboRepository orderedComboRepository;
    private final RestaurantTableRepository restaurantTableRepository;
    private final RestaurantSectionRepository restaurantSectionRepository;
    private final CashierShiftRepository cashierShiftRepository;
    private final MessageUtil messageUtil;
    private final PaymentMethodDisplaySupport paymentMethodDisplaySupport;
    private final AWSService awsService;

    public RequestDetailsServiceImpl(
            @Lazy RequestDetailsCollaborator collaborator,
            UserRepository userRepository,
            RoleRepository roleRepository,
            RestaurantRepository restaurantRepository,
            OrderRepository orderRepository,
            TransactionRepository transactionRepository,
            OrderedItemRepository orderedItemRepository,
            OrderedComboRepository orderedComboRepository,
            RestaurantTableRepository restaurantTableRepository,
            RestaurantSectionRepository restaurantSectionRepository,
            CashierShiftRepository cashierShiftRepository,
            MessageUtil messageUtil,
            PaymentMethodDisplaySupport paymentMethodDisplaySupport,
            AWSService awsService) {
        this.collaborator = collaborator;
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.restaurantRepository = restaurantRepository;
        this.orderRepository = orderRepository;
        this.transactionRepository = transactionRepository;
        this.orderedItemRepository = orderedItemRepository;
        this.orderedComboRepository = orderedComboRepository;
        this.restaurantTableRepository = restaurantTableRepository;
        this.restaurantSectionRepository = restaurantSectionRepository;
        this.cashierShiftRepository = cashierShiftRepository;
        this.messageUtil = messageUtil;
        this.paymentMethodDisplaySupport = paymentMethodDisplaySupport;
        this.awsService = awsService;
    }

    @Transactional(readOnly = true)
    public ResponseDto<RequestDetailsResponse> getDetails(UUID requestId, String userRole, String userId) {
        Locale userLocale = LocaleContextHolder.getLocale();
        
        // Only MANAGER and HQ_ADMIN can view request details
        if (!"MANAGER".equals(userRole) && !"HQ_ADMIN".equals(userRole)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    messageUtil.getMessage("user.profile.update.request.unauthorized", userLocale));
        }
        
        // Try to find as profile update request first (check if it's a user with a request)
        Optional<User> userOptional = userRepository.findById(requestId);
        if (userOptional.isPresent()) {
            User user = userOptional.get();
            if (user.getProfileUpdateRequestStatus() != RequestStatus.NONE) {
                // This is a profile update request
            
            try {
                ObjectMapper objectMapper = new ObjectMapper();
                UserProfileUpdateRequest updateRequest = null;
                
                if (user.getProfileUpdateRequestData() != null) {
                    updateRequest = objectMapper.readValue(user.getProfileUpdateRequestData(), UserProfileUpdateRequest.class);
                }
                
                // For old vs new data comparison:
                // - For OPEN/PENDING: Old = current user data, New = requested data from requestData
                // - For APPROVED: Old = requested data (what was requested, now applied), New = current user data (approved data)
                // - For DECLINED: Old = current user data (unchanged), New = requested data (what was declined)
                
                String oldFirstName, oldLastName, oldEmail, oldContactNumber, oldPhotoUrl, oldLanguageCode;
                String newFirstName, newLastName, newEmail, newContactNumber, newPhotoUrl, newLanguageCode;
                
                String normalizedUserPhotoUrl = (user.getPhotoUrl() != null && !user.getPhotoUrl().isEmpty()) ? awsService.stripToKey(user.getPhotoUrl()) : null;
                if (user.getProfileUpdateRequestStatus() == RequestStatus.APPROVED) {
                    // For approved requests: Old = what was requested (from requestData), New = current user data (approved)
                    oldFirstName = updateRequest != null ? updateRequest.getFirstName() : user.getFirstName();
                    oldLastName = updateRequest != null ? updateRequest.getLastName() : user.getLastName();
                    oldEmail = updateRequest != null ? updateRequest.getEmail() : user.getEmail();
                    oldContactNumber = updateRequest != null ? updateRequest.getContactNumber() : user.getContactNumber();
                    oldPhotoUrl = updateRequest != null ? updateRequest.getPhotoUrl() : normalizedUserPhotoUrl;
                    oldLanguageCode = updateRequest != null ? updateRequest.getLanguageCode() : user.getLanguageCode();
                    
                    newFirstName = user.getFirstName();
                    newLastName = user.getLastName();
                    newEmail = user.getEmail();
                    newContactNumber = user.getContactNumber();
                    newPhotoUrl = normalizedUserPhotoUrl;
                    newLanguageCode = user.getLanguageCode();
                } else {
                    // For OPEN/PENDING/DECLINED: Old = current user data, New = requested data
                    oldFirstName = user.getFirstName();
                    oldLastName = user.getLastName();
                    oldEmail = user.getEmail();
                    oldContactNumber = user.getContactNumber();
                    oldPhotoUrl = normalizedUserPhotoUrl;
                    oldLanguageCode = user.getLanguageCode();
                    
                    newFirstName = updateRequest != null ? updateRequest.getFirstName() : null;
                    newLastName = updateRequest != null ? updateRequest.getLastName() : null;
                    newEmail = updateRequest != null ? updateRequest.getEmail() : null;
                    newContactNumber = updateRequest != null ? updateRequest.getContactNumber() : null;
                    newPhotoUrl = updateRequest != null ? updateRequest.getPhotoUrl() : null;
                    newLanguageCode = updateRequest != null ? updateRequest.getLanguageCode() : null;
                }
                
                boolean firstNameChanged = updateRequest != null && !Objects.equals(oldFirstName, newFirstName);
                boolean lastNameChanged = updateRequest != null && !Objects.equals(oldLastName, newLastName);
                boolean emailChanged = updateRequest != null && !Objects.equals(oldEmail, newEmail);
                boolean contactNumberChanged = updateRequest != null && !Objects.equals(oldContactNumber, newContactNumber);
                boolean photoUrlChanged = updateRequest != null && !Objects.equals(oldPhotoUrl, newPhotoUrl);
                boolean languageCodeChanged = updateRequest != null && !Objects.equals(oldLanguageCode, newLanguageCode);
                
                // Get role name for requestedBy user (for profile updates, updatedBy is the requester)
                String requestedByRole = null;
                User requester = user.getUpdatedBy() != null ? user.getUpdatedBy() : user;
                if (requester != null && requester.getRoleId() != null) {
                    var role = roleRepository.findById(requester.getRoleId()).orElse(null);
                    if (role != null) {
                        requestedByRole = role.getName();
                    }
                }
                
                // Extract reason from requestData if available (for future use)
                String reason = null;
                try {
                    if (user.getProfileUpdateRequestData() != null && updateRequest != null) {
                        // Check if there's a reason field in the request data
                        // For now, reason is not stored in UserProfileUpdateRequest, so it will be null
                        // This field is added for future extensibility
                        reason = null;
                    }
                } catch (Exception e) {
                    log.warn("Error extracting reason from profile update request data: {}", e.getMessage());
                }
                
                // Get restaurant name for profile update request
                String restaurantName = null;
                if (user.getRestaurantId() != null) {
                    Optional<Restaurant> restaurantOpt = restaurantRepository.findById(user.getRestaurantId());
                    if (restaurantOpt.isPresent()) {
                        Restaurant restaurant = restaurantOpt.get();
                        if (restaurant.getTranslations() != null && !restaurant.getTranslations().isEmpty()) {
                            String userLanguage = userLocale.getLanguage();
                            restaurantName = restaurant.getTranslations().stream()
                                    .filter(t -> t.getLanguageCode() != null && t.getLanguageCode().startsWith(userLanguage))
                                    .findFirst()
                                    .map(com.gulfnet.shared_library.entity.RestaurantTranslation::getName)
                                    .orElse(restaurant.getTranslations().get(0).getName());
                        } else {
                            restaurantName = "Restaurant";
                        }
                    }
                }
                
                ProfileUpdateRequestWithComparisonResponse profileDetails = ProfileUpdateRequestWithComparisonResponse.builder()
                        .userId(user.getId())
                        .firstName(user.getFirstName())
                        .lastName(user.getLastName())
                        .email(user.getEmail())
                        .userCode(user.getUserCode())
                        .status(user.getProfileUpdateRequestStatus())
                        .requestData(user.getProfileUpdateRequestData())
                        .requestedAt(user.getProfileUpdateRequestedAt() != null ? user.getProfileUpdateRequestedAt().toLocalDateTime() : null)
                        .requestedBy(requester != null ? requester.getId() : null)
                        .requestedByName(requester != null ? 
                                requester.getFirstName() + " " + requester.getLastName() : null)
                        .requestedByRole(requestedByRole)
                        .reviewedAt(user.getProfileUpdateReviewedAt() != null ? user.getProfileUpdateReviewedAt().toLocalDateTime() : null)
                        .reviewedBy(user.getUpdatedBy() != null ? user.getUpdatedBy().getId() : null)
                        .reviewedByName(user.getUpdatedBy() != null ? 
                                user.getUpdatedBy().getFirstName() + " " + user.getUpdatedBy().getLastName() : null)
                        .comments(null) // Comments are only available when approving/declining, not stored in User entity
                        .reason(reason)
                        .restaurantName(restaurantName)
                        .oldFirstName(oldFirstName)
                        .oldLastName(oldLastName)
                        .oldEmail(oldEmail)
                        .oldContactNumber(oldContactNumber)
                        .oldPhotoUrl(oldPhotoUrl)
                        .oldLanguageCode(oldLanguageCode)
                        .newFirstName(newFirstName)
                        .newLastName(newLastName)
                        .newEmail(newEmail)
                        .newContactNumber(newContactNumber)
                        .newPhotoUrl(newPhotoUrl)
                        .newLanguageCode(newLanguageCode)
                        .firstNameChanged(firstNameChanged)
                        .lastNameChanged(lastNameChanged)
                        .emailChanged(emailChanged)
                        .contactNumberChanged(contactNumberChanged)
                        .photoUrlChanged(photoUrlChanged)
                        .languageCodeChanged(languageCodeChanged)
                        .build();
                
                RequestDetailsResponse response = RequestDetailsResponse.builder()
                        .requestType(messageUtil.getMessage("request.type.profile.update", userLocale))
                        .restaurantName(restaurantName)
                        .profileUpdateDetails(profileDetails)
                        .additionalDiscountDetails(null)
                        .build();
                
                return ResponseDto.<RequestDetailsResponse>builder()
                        .message(messageUtil.getMessage("user.profile.update.requests.retrieved", userLocale))
                        .data(response)
                        .build();
                        
            } catch (JsonProcessingException e) {
                log.error("Error parsing request data for user {}: {}", requestId, e.getMessage());
                throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                        messageUtil.getMessage("user.profile.update.request.error", userLocale));
            }
            }
        }
        
        // Try to find as additional discount or cancellation request (check if it's an order with a request)
        Optional<Order> orderOptional = orderRepository.findById(requestId);
        if (orderOptional.isPresent()) {
            Order order = orderOptional.get();
            
            // Check if both requests exist - if so, use requestType flag to determine which one
            boolean hasAdditionalDiscountRequest = order.getAdditionalDiscountRequestStatus() != RequestStatus.NONE;
            boolean hasCancellationRequest = order.getCancellationRequestStatus() != RequestStatus.NONE;
            
            // Determine which request to return based on requestType flag when both exist
            String additionalDiscountRequestType = collaborator.getRequestTypeFromData(order.getAdditionalDiscountRequestData());
            String cancellationRequestType = collaborator.getRequestTypeFromData(order.getCancellationRequestData());
            
            // If both requests exist, prioritize based on requestType flag
            // If requestType is not set (backward compatibility), default to additional discount first
            boolean shouldReturnAdditionalDiscount = hasAdditionalDiscountRequest && 
                (!hasCancellationRequest || 
                 "ADDITIONAL_DISCOUNT".equals(additionalDiscountRequestType) ||
                 (additionalDiscountRequestType == null && cancellationRequestType == null)); // backward compatibility
            
            if (shouldReturnAdditionalDiscount) {
                // This is an additional discount request
                // Only MANAGER can view additional discount request details (HQ_ADMIN cannot)
                if (!"MANAGER".equals(userRole)) {
                    throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                            messageUtil.getMessage("additional.discount.request.unauthorized.role", userLocale));
                }
                
                // MANAGER can only view requests from their own restaurant
                // Get manager's restaurant ID if user is a MANAGER
                UUID managerRestaurantId = null;
                if (userId != null && !userId.trim().isEmpty() && !"null".equalsIgnoreCase(userId)) {
                    try {
                        Optional<User> managerOpt = userRepository.findById(UUID.fromString(userId));
                        if (managerOpt.isPresent() && managerOpt.get().getRestaurantId() != null) {
                            managerRestaurantId = managerOpt.get().getRestaurantId();
                        }
                    } catch (IllegalArgumentException e) {
                        log.warn("Invalid userId format: {}", userId);
                    }
                }
                
                if (managerRestaurantId != null && order.getRestaurant() != null) {
                    if (!managerRestaurantId.equals(order.getRestaurant().getId())) {
                        throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                                messageUtil.getMessage("additional.discount.request.unauthorized.restaurant", userLocale));
                    }
                }
                
                // Get role name for requestedBy user
                String requestedByRole = null;
                if (order.getAdditionalDiscountRequestedBy() != null && order.getAdditionalDiscountRequestedBy().getRoleId() != null) {
                    var role = roleRepository.findById(order.getAdditionalDiscountRequestedBy().getRoleId()).orElse(null);
                    if (role != null) {
                        requestedByRole = role.getName();
                    }
                }
                
                // Get restaurant name
                String restaurantName = null;
                UUID restaurantId = null;
                if (order.getRestaurant() != null) {
                    restaurantId = order.getRestaurant().getId();
                    if (order.getRestaurant().getTranslations() != null && !order.getRestaurant().getTranslations().isEmpty()) {
                        String userLanguage = userLocale.getLanguage();
                        restaurantName = order.getRestaurant().getTranslations().stream()
                                .filter(t -> t.getLanguageCode() != null && t.getLanguageCode().startsWith(userLanguage))
                                .findFirst()
                                .map(com.gulfnet.shared_library.entity.RestaurantTranslation::getName)
                                .orElse(order.getRestaurant().getTranslations().get(0).getName());
                    } else {
                        restaurantName = "Restaurant";
                    }
                }
                
                // Calculate additionalDiscountAmount if request is OPEN (pending approval)
                BigDecimal calculatedAdditionalDiscountAmount = order.getAdditionalDiscountAmount();
                if (order.getAdditionalDiscountRequestStatus() == RequestStatus.OPEN) {
                    // Request is OPEN (pending approval) - calculate discount amount for preview
                    if (order.getAdditionalDiscountType() != null && order.getAdditionalDiscountValue() != null) {
                        // Calculate subtotal after discount: subTotal - discountAmount
                        BigDecimal subtotalAfterDiscount = order.getSubTotal() != null ? order.getSubTotal() : BigDecimal.ZERO;
                        if (order.getDiscountAmount() != null) {
                            subtotalAfterDiscount = subtotalAfterDiscount.subtract(order.getDiscountAmount());
                            if (subtotalAfterDiscount.compareTo(BigDecimal.ZERO) < 0) {
                                subtotalAfterDiscount = BigDecimal.ZERO;
                            }
                        }
                        // Calculate total before additional discount: subtotal + tax + service charge + packing charge
                        BigDecimal taxAmount = order.getTaxAmount() != null ? order.getTaxAmount() : BigDecimal.ZERO;
                        BigDecimal serviceChargeAmount = order.getServiceChargeAmount() != null ? order.getServiceChargeAmount() : BigDecimal.ZERO;
                        BigDecimal packingChargeAmount = order.getPackingChargeAmount() != null ? order.getPackingChargeAmount() : BigDecimal.ZERO;
                        BigDecimal totalBeforeAdditionalDiscount = subtotalAfterDiscount.add(taxAmount).add(serviceChargeAmount).add(packingChargeAmount);
                        // Calculate discount amount based on type
                        if (totalBeforeAdditionalDiscount.compareTo(BigDecimal.ZERO) > 0) {
                            if (order.getAdditionalDiscountType() == DiscountType.PERCENT) {
                                calculatedAdditionalDiscountAmount = totalBeforeAdditionalDiscount.multiply(order.getAdditionalDiscountValue())
                                        .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
                            } else if (order.getAdditionalDiscountType() == DiscountType.FLAT) {
                                calculatedAdditionalDiscountAmount = order.getAdditionalDiscountValue();
                            }
                        }
                    }
                }
                
                AdditionalDiscountRequestResponse discountDetails = AdditionalDiscountRequestResponse.builder()
                        .orderId(order.getId())
                        .orderNumber(order.getOrderNumber())
                        .orderTotalAmount(order.getTotalAmount() != null ? order.getTotalAmount() : BigDecimal.ZERO)
                        .additionalDiscountType(order.getAdditionalDiscountType())
                        .additionalDiscountValue(order.getAdditionalDiscountValue())
                        .additionalDiscountAmount(calculatedAdditionalDiscountAmount)
                        .additionalDiscountReason(order.getAdditionalDiscountReason())
                        .requestStatus(order.getAdditionalDiscountRequestStatus())
                        .requestedAt(order.getAdditionalDiscountRequestedAt() != null ? order.getAdditionalDiscountRequestedAt().toLocalDateTime() : null)
                        .requestedBy(order.getAdditionalDiscountRequestedBy() != null ? order.getAdditionalDiscountRequestedBy().getId() : null)
                        .requestedByName(order.getAdditionalDiscountRequestedBy() != null ? 
                            order.getAdditionalDiscountRequestedBy().getFirstName() + " " + order.getAdditionalDiscountRequestedBy().getLastName() : null)
                        .requestedByRole(requestedByRole)
                        .reviewedAt(order.getAdditionalDiscountReviewedAt() != null ? ((OffsetDateTime) order.getAdditionalDiscountReviewedAt()).toLocalDateTime() : null)
                        .reviewedBy(order.getAdditionalDiscountReviewedBy() != null ? order.getAdditionalDiscountReviewedBy().getId() : null)
                        .reviewedByName(order.getAdditionalDiscountReviewedBy() != null ? 
                            order.getAdditionalDiscountReviewedBy().getFirstName() + " " + order.getAdditionalDiscountReviewedBy().getLastName() : null)
                        .comments(order.getAdditionalDiscountRequestComments())
                        .restaurantId(restaurantId)
                        .restaurantName(restaurantName)
                        .build();
                
                RequestDetailsResponse response = RequestDetailsResponse.builder()
                        .requestType(messageUtil.getMessage("request.type.additional.discount", userLocale))
                        .restaurantName(restaurantName)
                        .profileUpdateDetails(null)
                        .additionalDiscountDetails(discountDetails)
                        .build();
                
                return ResponseDto.<RequestDetailsResponse>builder()
                        .message(messageUtil.getMessage("additional.discount.requests.retrieved", userLocale))
                        .data(response)
                        .build();
            }
            
            // Check if it's an order cancellation request
            if (hasCancellationRequest && (!hasAdditionalDiscountRequest || "ORDER_CANCELLATION".equals(cancellationRequestType))) {
                // This is an order cancellation request
                // Only MANAGER and HQ_ADMIN can view order cancellation request details
                if (!"MANAGER".equals(userRole) && !"HQ_ADMIN".equals(userRole)) {
                    throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                            messageUtil.getMessage("user.profile.update.request.unauthorized", userLocale));
                }
                
                // MANAGER can only view requests from their own restaurant
                UUID managerRestaurantId = null;
                if ("MANAGER".equals(userRole) && userId != null && !userId.trim().isEmpty() && !"null".equalsIgnoreCase(userId)) {
                    try {
                        Optional<User> managerOpt = userRepository.findById(UUID.fromString(userId));
                        if (managerOpt.isPresent() && managerOpt.get().getRestaurantId() != null) {
                            managerRestaurantId = managerOpt.get().getRestaurantId();
                        }
                    } catch (IllegalArgumentException e) {
                        log.warn("Invalid userId format: {}", userId);
                    }
                }
                
                if (managerRestaurantId != null && order.getRestaurant() != null) {
                    if (!managerRestaurantId.equals(order.getRestaurant().getId())) {
                        throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                                messageUtil.getMessage("order.cancellation.request.unauthorized.restaurant", userLocale));
                    }
                }
                
                OrderCancellationRequestResponse orderCancellationDetails = collaborator.buildOrderCancellationRequestResponse(order, userLocale);
                
                RequestDetailsResponse response = RequestDetailsResponse.builder()
                        .requestType(messageUtil.getMessage("request.type.order.cancellation", userLocale))
                        .restaurantName(orderCancellationDetails.getRestaurantName())
                        .profileUpdateDetails(null)
                        .additionalDiscountDetails(null)
                        .tableSectionDetails(null)
                        .refundDetails(null)
                        .itemCancellationDetails(null)
                        .comboCancellationDetails(null)
                        .transactionCancellationDetails(null)
                        .orderCancellationDetails(orderCancellationDetails)
                        .build();
                
                return ResponseDto.<RequestDetailsResponse>builder()
                        .message(messageUtil.getMessage("order.cancellation.requests.retrieved", userLocale))
                        .data(response)
                        .build();
            }
        }

        // Try to find as shift discrepancy request (cashier shift)
        Optional<CashierShift> shiftOptional = cashierShiftRepository.findById(requestId);
        if (shiftOptional.isPresent()) {
            // Only MANAGER can view shift discrepancy details
            if (!"MANAGER".equals(userRole)) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                        messageUtil.getMessage("additional.discount.request.unauthorized.role", userLocale));
            }

            CashierShift shift = shiftOptional.get();

            // MANAGER can only view requests from their own restaurant
            UUID managerRestaurantId = null;
            if (userId != null && !userId.trim().isEmpty() && !"null".equalsIgnoreCase(userId)) {
                try {
                    Optional<User> managerOpt = userRepository.findById(UUID.fromString(userId));
                    if (managerOpt.isPresent() && managerOpt.get().getRestaurantId() != null) {
                        managerRestaurantId = managerOpt.get().getRestaurantId();
                    }
                } catch (IllegalArgumentException e) {
                    log.warn("Invalid userId format: {}", userId);
                }
            }

            if (managerRestaurantId != null && shift.getRestaurant() != null) {
                if (!managerRestaurantId.equals(shift.getRestaurant().getId())) {
                    throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                            messageUtil.getMessage("additional.discount.request.unauthorized.restaurant", userLocale));
                }
            }

            // Restaurant name
            String restaurantName = null;
            if (shift.getRestaurant() != null &&
                    shift.getRestaurant().getTranslations() != null &&
                    !shift.getRestaurant().getTranslations().isEmpty()) {
                String userLanguage = userLocale.getLanguage();
                restaurantName = shift.getRestaurant().getTranslations().stream()
                        .filter(t -> t.getLanguageCode() != null && t.getLanguageCode().startsWith(userLanguage))
                        .findFirst()
                        .map(com.gulfnet.shared_library.entity.RestaurantTranslation::getName)
                        .orElse(shift.getRestaurant().getTranslations().get(0).getName());
            } else if (shift.getRestaurant() != null) {
                restaurantName = "Restaurant";
            }

            // Build CashierShiftResponse (similar to restaurant-management)
            CashierShiftResponse shiftDetails = CashierShiftResponse.builder()
                    .id(shift.getId())
                    .cashDrawerId(shift.getCashDrawer() != null ? shift.getCashDrawer().getId() : null)
                    .cashDrawerName(collaborator.resolveCashDrawerNameForUserService(shift.getCashDrawer(), userLocale))
                    .cashierId(shift.getCashier() != null ? shift.getCashier().getId() : null)
                    .cashierName(shift.getCashier() != null
                            ? (shift.getCashier().getFirstName() + " " + shift.getCashier().getLastName()).trim()
                            : null)
                    .restaurantId(shift.getRestaurant() != null ? shift.getRestaurant().getId() : null)
                    .shiftId(shift.getShift() != null ? shift.getShift().getId() : null)
                    .shiftName(shift.getShift() != null ? collaborator.getShiftNameFromShift(shift.getShift(), LocaleContextHolder.getLocale().getLanguage()) : null)
                    .status(shift.getStatus())
                    .openingBalance(shift.getOpeningBalance())
                    .closingBalance(shift.getClosingBalance())
                    .expectedClosingBalance(shift.getExpectedClosingBalance())
                    .discrepancyAmount(shift.getDiscrepancyAmount())
                    .discrepancyReason(shift.getDiscrepancyReason())
                    .startedAt(shift.getStartedAt() != null ? shift.getStartedAt().toLocalDateTime() : null)
                    .closedAt(shift.getClosedAt() != null ? shift.getClosedAt().toLocalDateTime() : null)
                    .approvedBy(shift.getApprovedBy() != null ? shift.getApprovedBy().getId() : null)
                    .approvedByName(shift.getApprovedBy() != null
                            ? (shift.getApprovedBy().getFirstName() + " " + shift.getApprovedBy().getLastName()).trim()
                            : null)
                    .approvedAt(shift.getApprovedAt() != null ? shift.getApprovedAt().toLocalDateTime() : null)
                    .createdAt(shift.getCreatedAt() != null ? shift.getCreatedAt().toLocalDateTime() : null)
                    .updatedAt(shift.getUpdatedAt() != null ? shift.getUpdatedAt().toLocalDateTime() : null)
                    .build();

            RequestDetailsResponse response = RequestDetailsResponse.builder()
                    .requestType(messageUtil.getMessage("request.type.shift.discrepancy", userLocale))
                    .restaurantName(restaurantName)
                    .shiftDiscrepancyDetails(shiftDetails)
                    .build();

            return ResponseDto.<RequestDetailsResponse>builder()
                    .message(messageUtil.getMessage("shift.discrepancy.requests.retrieved", userLocale))
                    .data(response)
                    .build();
        }
        
        // Try to find as table/section request (only HQ_ADMIN can view these)
        if ("HQ_ADMIN".equals(userRole)) {
            // Try table first
            Optional<RestaurantTable> tableOptional = restaurantTableRepository.findById(requestId);
            if (tableOptional.isPresent()) {
                RestaurantTable table = tableOptional.get();
                if (table.getTableSectionRequestStatus() != RequestStatus.NONE) {
                    String restaurantName = null;
                    UUID restaurantId = null;
                    if (table.getRestaurantRow() != null && 
                        table.getRestaurantRow().getRestaurantSection() != null &&
                        table.getRestaurantRow().getRestaurantSection().getRestaurantLayout() != null &&
                        table.getRestaurantRow().getRestaurantSection().getRestaurantLayout().getRestaurant() != null) {
                        Restaurant restaurant = table.getRestaurantRow().getRestaurantSection().getRestaurantLayout().getRestaurant();
                        restaurantId = restaurant.getId();
                        if (restaurant.getTranslations() != null && !restaurant.getTranslations().isEmpty()) {
                            String userLanguage = userLocale.getLanguage();
                            restaurantName = restaurant.getTranslations().stream()
                                    .filter(t -> t.getLanguageCode() != null && t.getLanguageCode().startsWith(userLanguage))
                                    .findFirst()
                                    .map(com.gulfnet.shared_library.entity.RestaurantTranslation::getName)
                                    .orElse(restaurant.getTranslations().get(0).getName());
                        } else {
                            restaurantName = "Restaurant";
                        }
                    }
                    
                    // Get role name for requestedBy user
                    String requestedByRole = null;
                    if (table.getTableSectionRequestedBy() != null && table.getTableSectionRequestedBy().getRoleId() != null) {
                        var role = roleRepository.findById(table.getTableSectionRequestedBy().getRoleId()).orElse(null);
                        if (role != null) {
                            requestedByRole = role.getName();
                        }
                    }
                    
                    TableSectionRequestResponse tableSectionDetails = TableSectionRequestResponse.builder()
                            .entityId(table.getId())
                            .entityType("Table")
                            .entityName("Table " + (table.getTableOrder() != null ? table.getTableOrder().toString() : ""))
                            .restaurantName(restaurantName)
                            .restaurantId(restaurantId)
                            .requestData(table.getTableSectionRequestData())
                            .requestStatus(table.getTableSectionRequestStatus())
                            .requestedAt(table.getTableSectionRequestedAt() != null ? table.getTableSectionRequestedAt().toLocalDateTime() : null)
                            .requestedBy(table.getTableSectionRequestedBy() != null ? table.getTableSectionRequestedBy().getId() : null)
                            .requestedByName(table.getTableSectionRequestedBy() != null ? 
                                table.getTableSectionRequestedBy().getFirstName() + " " + table.getTableSectionRequestedBy().getLastName() : null)
                            .requestedByRole(requestedByRole)
                            .reviewedAt(table.getTableSectionReviewedAt() != null ? table.getTableSectionReviewedAt().toLocalDateTime() : null)
                            .reviewedBy(table.getTableSectionReviewedBy() != null ? table.getTableSectionReviewedBy().getId() : null)
                            .reviewedByName(table.getTableSectionReviewedBy() != null ? 
                                table.getTableSectionReviewedBy().getFirstName() + " " + table.getTableSectionReviewedBy().getLastName() : null)
                            .comments(table.getTableSectionRequestComments())
                            .reason(table.getTableSectionRequestComments()) // Reason is stored in comments field
                            .build();
                    
                    RequestDetailsResponse response = RequestDetailsResponse.builder()
                            .requestType(messageUtil.getMessage("request.type.table.section", userLocale))
                            .restaurantName(restaurantName)
                            .profileUpdateDetails(null)
                            .additionalDiscountDetails(null)
                            .tableSectionDetails(tableSectionDetails)
                            .build();
                    
                    return ResponseDto.<RequestDetailsResponse>builder()
                            .message(messageUtil.getMessage("table.section.requests.retrieved", userLocale))
                            .data(response)
                            .build();
                }
            }
            
            // Try section
            Optional<RestaurantSection> sectionOptional = restaurantSectionRepository.findById(requestId);
            if (sectionOptional.isPresent()) {
                RestaurantSection section = sectionOptional.get();
                if (section.getTableSectionRequestStatus() != RequestStatus.NONE) {
                    String restaurantName = null;
                    UUID restaurantId = null;
                    String sectionName = null;
                    if (section.getRestaurantLayout() != null && section.getRestaurantLayout().getRestaurant() != null) {
                        Restaurant restaurant = section.getRestaurantLayout().getRestaurant();
                        restaurantId = restaurant.getId();
                        if (restaurant.getTranslations() != null && !restaurant.getTranslations().isEmpty()) {
                            String userLanguage = userLocale.getLanguage();
                            restaurantName = restaurant.getTranslations().stream()
                                    .filter(t -> t.getLanguageCode() != null && t.getLanguageCode().startsWith(userLanguage))
                                    .findFirst()
                                    .map(com.gulfnet.shared_library.entity.RestaurantTranslation::getName)
                                    .orElse(restaurant.getTranslations().get(0).getName());
                        } else {
                            restaurantName = "Restaurant";
                        }
                    }
                    
                    // Get section name from translations
                    if (section.getTranslations() != null && !section.getTranslations().isEmpty()) {
                        String userLanguage = userLocale.getLanguage();
                        sectionName = section.getTranslations().stream()
                                .filter(t -> t.getLanguageCode() != null && t.getLanguageCode().startsWith(userLanguage))
                                .findFirst()
                                .map(com.gulfnet.shared_library.entity.RestaurantSectionTranslation::getName)
                                .orElse(section.getTranslations().get(0).getName());
                    }
                    
                    // Get role name for requestedBy user
                    String requestedByRole = null;
                    if (section.getTableSectionRequestedBy() != null && section.getTableSectionRequestedBy().getRoleId() != null) {
                        var role = roleRepository.findById(section.getTableSectionRequestedBy().getRoleId()).orElse(null);
                        if (role != null) {
                            requestedByRole = role.getName();
                        }
                    }
                    
                    TableSectionRequestResponse tableSectionDetails = TableSectionRequestResponse.builder()
                            .entityId(section.getId())
                            .entityType("Section")
                            .entityName(sectionName != null ? sectionName : "Section")
                            .restaurantName(restaurantName)
                            .restaurantId(restaurantId)
                            .requestData(section.getTableSectionRequestData())
                            .requestStatus(section.getTableSectionRequestStatus())
                            .requestedAt(section.getTableSectionRequestedAt() != null ? section.getTableSectionRequestedAt().toLocalDateTime() : null)
                            .requestedBy(section.getTableSectionRequestedBy() != null ? section.getTableSectionRequestedBy().getId() : null)
                            .requestedByName(section.getTableSectionRequestedBy() != null ? 
                                section.getTableSectionRequestedBy().getFirstName() + " " + section.getTableSectionRequestedBy().getLastName() : null)
                            .requestedByRole(requestedByRole)
                            .reviewedAt(section.getTableSectionReviewedAt() != null ? section.getTableSectionReviewedAt().toLocalDateTime() : null)
                            .reviewedBy(section.getTableSectionReviewedBy() != null ? section.getTableSectionReviewedBy().getId() : null)
                            .reviewedByName(section.getTableSectionReviewedBy() != null ? 
                                section.getTableSectionReviewedBy().getFirstName() + " " + section.getTableSectionReviewedBy().getLastName() : null)
                            .comments(section.getTableSectionRequestComments())
                            .reason(section.getTableSectionRequestComments()) // Reason is stored in comments field
                            .build();
                    
                    RequestDetailsResponse response = RequestDetailsResponse.builder()
                            .requestType(messageUtil.getMessage("request.type.table.section", userLocale))
                            .restaurantName(restaurantName)
                            .profileUpdateDetails(null)
                            .additionalDiscountDetails(null)
                            .tableSectionDetails(tableSectionDetails)
                            .build();
                    
                    return ResponseDto.<RequestDetailsResponse>builder()
                            .message(messageUtil.getMessage("table.section.requests.retrieved", userLocale))
                            .data(response)
                            .build();
                }
            }
        }
        
        // Try to find as refund request (check if it's a transaction with a refund request)
        Optional<Transaction> transactionOptional =
                transactionRepository.findByIdWithRelationshipsForRefundResponse(requestId);
        if (transactionOptional.isEmpty()) {
            transactionOptional = transactionRepository.findById(requestId);
        }
        if (transactionOptional.isPresent()) {
            Transaction transaction = transactionOptional.get();
            if (transaction.getRequestStatus() == RequestStatus.OPEN || transaction.getRequestStatus() == RequestStatus.APPROVED || transaction.getRequestStatus() == RequestStatus.DECLINED) {
                // Check if it's a refund request (not cancellation)
                boolean isRefundRequestInData = transaction.getRequestData() != null
                        && transaction.getRequestData().contains("\"requestType\":\"REFUND\"");
                try {
                    if (transaction.getRequestData() != null) {
                        ObjectMapper objectMapper = new ObjectMapper();
                        Map<String, Object> requestData = objectMapper.readValue(transaction.getRequestData(), Map.class);
                        String requestType = (String) requestData.get("requestType");
                        if ("REFUND".equals(requestType)) {
                            return buildRefundRequestDetailsResponse(transaction, requestData, userLocale);
                        }
                    }
                } catch (JsonProcessingException e) {
                    log.warn("Error parsing request data for transaction {}: {}", requestId, e.getMessage());
                    if (isRefundRequestInData) {
                        throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                                messageUtil.getMessage("refund.requests.error", userLocale));
                    }
                } catch (ResponseStatusException e) {
                    throw e;
                } catch (RuntimeException e) {
                    if (isRefundRequestInData) {
                        log.error("Error building refund request details for transaction {}: {}", requestId, e.getMessage(), e);
                        throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                                messageUtil.getMessage("refund.requests.error", userLocale));
                    }
                    throw e;
                }
                
                // Check if it's a transaction cancellation request (not refund)
                if (!isRefundRequestInData && transaction.getRequestStatus() != RequestStatus.NONE) {
                    // This is a transaction cancellation request
                    // Only MANAGER can see transaction cancellation requests (HQ_ADMIN cannot)
                    if (!"MANAGER".equals(userRole)) {
                        throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                                messageUtil.getMessage("transaction.cancellation.request.unauthorized", userLocale));
                    }
                    
                    // MANAGER can only view requests from their own restaurant
                    // Get manager's restaurant ID if user is a MANAGER
                    UUID managerRestaurantId = null;
                    if (userId != null && !userId.trim().isEmpty() && !"null".equalsIgnoreCase(userId)) {
                        try {
                            Optional<User> managerOpt = userRepository.findById(UUID.fromString(userId));
                            if (managerOpt.isPresent() && managerOpt.get().getRestaurantId() != null) {
                                managerRestaurantId = managerOpt.get().getRestaurantId();
                            }
                        } catch (IllegalArgumentException e) {
                            log.warn("Invalid userId format: {}", userId);
                        }
                    }
                    
                    if (managerRestaurantId != null && transaction.getRestaurant() != null) {
                        if (!managerRestaurantId.equals(transaction.getRestaurant().getId())) {
                            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                                    messageUtil.getMessage("transaction.cancellation.request.unauthorized.restaurant", userLocale));
                        }
                    }
                    
                    // Build transaction cancellation request response
                        String cancellationReason = null;
                        try {
                            if (transaction.getRequestData() != null && !transaction.getRequestData().trim().isEmpty()) {
                                ObjectMapper objectMapper = new ObjectMapper();
                                com.gulfnet.shared_library.model.request.TransactionCancellationRequestDto requestDto = 
                                        objectMapper.readValue(transaction.getRequestData(), 
                                                com.gulfnet.shared_library.model.request.TransactionCancellationRequestDto.class);
                                if (requestDto != null) {
                                    cancellationReason = requestDto.getCancellationReason();
                                }
                            }
                        } catch (JsonProcessingException e) {
                            log.warn("Error parsing transaction cancellation request data for transaction {}: {}", transaction.getId(), e.getMessage());
                        } catch (Exception e) {
                            log.error("Unexpected error parsing transaction cancellation request data for transaction {}: {}", transaction.getId(), e.getMessage(), e);
                        }
                        
                        // Get role name for requestedBy user
                        String requestedByRole = null;
                        if (transaction.getRequestedBy() != null && transaction.getRequestedBy().getRoleId() != null) {
                            var role = roleRepository.findById(transaction.getRequestedBy().getRoleId()).orElse(null);
                            if (role != null) {
                                requestedByRole = role.getName();
                            }
                        }
                        
                        String restaurantName = null;
                        if (transaction.getRestaurant() != null && transaction.getRestaurant().getTranslations() != null && !transaction.getRestaurant().getTranslations().isEmpty()) {
                            String userLanguage = userLocale.getLanguage();
                            restaurantName = transaction.getRestaurant().getTranslations().stream()
                                    .filter(t -> t.getLanguageCode() != null && t.getLanguageCode().startsWith(userLanguage))
                                    .findFirst()
                                    .map(com.gulfnet.shared_library.entity.RestaurantTranslation::getName)
                                    .orElse(transaction.getRestaurant().getTranslations().get(0).getName());
                        } else if (transaction.getRestaurant() != null) {
                            restaurantName = "Restaurant";
                        }
                        
                        // Build transaction cancellation request response with proper null handling
                        String requestedByName = null;
                        if (transaction.getRequestedBy() != null) {
                            String firstName = transaction.getRequestedBy().getFirstName() != null ? transaction.getRequestedBy().getFirstName() : "";
                            String lastName = transaction.getRequestedBy().getLastName() != null ? transaction.getRequestedBy().getLastName() : "";
                            requestedByName = (firstName + " " + lastName).trim();
                            if (requestedByName.isEmpty()) {
                                requestedByName = null;
                            }
                        }
                        
                        String reviewedByName = null;
                        if (transaction.getReviewedBy() != null) {
                            String firstName = transaction.getReviewedBy().getFirstName() != null ? transaction.getReviewedBy().getFirstName() : "";
                            String lastName = transaction.getReviewedBy().getLastName() != null ? transaction.getReviewedBy().getLastName() : "";
                            reviewedByName = (firstName + " " + lastName).trim();
                            if (reviewedByName.isEmpty()) {
                                reviewedByName = null;
                            }
                        }
                        
                        TransactionCancellationRequestResponse transactionCancellationDetails = TransactionCancellationRequestResponse.builder()
                                .transactionId(transaction.getId())
                                .orderId(transaction.getOrder() != null ? transaction.getOrder().getId() : null)
                                .orderNumber(transaction.getOrder() != null ? transaction.getOrder().getOrderNumber() : null)
                                .transactionNumber(transaction.getTransactionNumber())
                                .paymentMethod(paymentMethodDisplaySupport.toDisplayName(transaction.getPaymentMethod(), userLocale))
                                .paymentApp(transaction.getPaymentApp())
                                .transactionAmount(transaction.getOrder() != null && transaction.getOrder().getTotalAmount() != null 
                                        ? transaction.getOrder().getTotalAmount() 
                                        : transaction.getTransactionAmount())
                                .currentTransactionStatus(transaction.getTransactionStatus())
                                .cancellationReason(cancellationReason)
                                .requestStatus(transaction.getRequestStatus())
                                .requestedAt(transaction.getRequestedAt() != null ? transaction.getRequestedAt().toLocalDateTime() : null)
                                .requestedBy(transaction.getRequestedBy() != null ? transaction.getRequestedBy().getId() : null)
                                .requestedByName(requestedByName)
                                .requestedByRole(requestedByRole)
                                .reviewedAt(transaction.getReviewedAt() != null ? ((OffsetDateTime) transaction.getReviewedAt()).toLocalDateTime() : null)
                                .reviewedBy(transaction.getReviewedBy() != null ? transaction.getReviewedBy().getId() : null)
                                .reviewedByName(reviewedByName)
                                .comments(transaction.getRequestComments())
                                .restaurantId(transaction.getRestaurant() != null ? transaction.getRestaurant().getId() : null)
                                .restaurantName(restaurantName)
                                .build();
                        
                        RequestDetailsResponse response = RequestDetailsResponse.builder()
                                .requestType(messageUtil.getMessage("request.type.transaction.cancellation", userLocale))
                                .restaurantName(restaurantName)
                                .profileUpdateDetails(null)
                                .additionalDiscountDetails(null)
                                .tableSectionDetails(null)
                                .refundDetails(null)
                                .itemCancellationDetails(null)
                                .comboCancellationDetails(null)
                                .transactionCancellationDetails(transactionCancellationDetails)
                                .build();
                        
                        return ResponseDto.<RequestDetailsResponse>builder()
                                .message(messageUtil.getMessage("transaction.cancellation.requests.retrieved", userLocale))
                                .data(response)
                                .build();
                }
            }
        }
        
        // Try to find as item cancellation request (check if it's an OrderedItem with a cancellation request)
        Optional<OrderedItem> orderedItemOptional = orderedItemRepository.findById(requestId);
        if (orderedItemOptional.isPresent()) {
            OrderedItem orderedItem = orderedItemOptional.get();
            if (orderedItem.getCancellationRequestStatus() != RequestStatus.NONE) {
                // This is an item cancellation request
                ItemCancellationRequestResponse itemCancellationDetails = collaborator.buildItemCancellationRequestResponse(orderedItem, userLocale);
                
                RequestDetailsResponse response = RequestDetailsResponse.builder()
                        .requestType(messageUtil.getMessage("request.type.item.cancellation", userLocale))
                        .restaurantName(itemCancellationDetails.getRestaurantName())
                        .profileUpdateDetails(null)
                        .additionalDiscountDetails(null)
                        .tableSectionDetails(null)
                        .refundDetails(null)
                        .itemCancellationDetails(itemCancellationDetails)
                        .comboCancellationDetails(null)
                        .build();
                
                return ResponseDto.<RequestDetailsResponse>builder()
                        .message(messageUtil.getMessage("item.cancellation.requests.retrieved", userLocale))
                        .data(response)
                        .build();
            }
        }
        
        // Try to find as combo cancellation request (check if it's an OrderedCombo with a cancellation request)
        Optional<OrderedCombo> orderedComboOptional = orderedComboRepository.findById(requestId);
        if (orderedComboOptional.isPresent()) {
            OrderedCombo orderedCombo = orderedComboOptional.get();
            if (orderedCombo.getCancellationRequestStatus() != RequestStatus.NONE) {
                // This is a combo cancellation request
                ComboCancellationRequestResponse comboCancellationDetails = collaborator.buildComboCancellationRequestResponse(orderedCombo, userLocale);
                
                RequestDetailsResponse response = RequestDetailsResponse.builder()
                        .requestType(messageUtil.getMessage("request.type.item.cancellation", userLocale))
                        .restaurantName(comboCancellationDetails.getRestaurantName())
                        .profileUpdateDetails(null)
                        .additionalDiscountDetails(null)
                        .tableSectionDetails(null)
                        .refundDetails(null)
                        .itemCancellationDetails(null)
                        .comboCancellationDetails(comboCancellationDetails)
                        .build();
                
                return ResponseDto.<RequestDetailsResponse>builder()
                        .message(messageUtil.getMessage("item.cancellation.requests.retrieved", userLocale))
                        .data(response)
                        .build();
            }
        }
        
        // If we reach here, the request ID doesn't match any request
        throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                messageUtil.getMessage("request.not.found", userLocale));
    }

    private ResponseDto<RequestDetailsResponse> buildRefundRequestDetailsResponse(
            Transaction transaction, Map<String, Object> requestData, Locale userLocale) {
        String restaurantName = resolveRestaurantName(transaction.getRestaurant(), userLocale);

        List<RefundRequestResponse.RefundItemResponse> refundItems = new ArrayList<>();
        if (requestData.containsKey("orderedItems")) {
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> orderedItemsList = (List<Map<String, Object>>) requestData.get("orderedItems");
            if (orderedItemsList != null) {
                for (Map<String, Object> item : orderedItemsList) {
                    RefundRequestResponse.RefundItemResponse line = parseRefundLineItem(item, false, userLocale);
                    if (line != null) {
                        refundItems.add(line);
                    }
                }
            }
        }
        if (requestData.containsKey("orderedCombos")) {
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> orderedCombosList = (List<Map<String, Object>>) requestData.get("orderedCombos");
            if (orderedCombosList != null) {
                for (Map<String, Object> combo : orderedCombosList) {
                    RefundRequestResponse.RefundItemResponse line = parseRefundLineItem(combo, true, userLocale);
                    if (line != null) {
                        refundItems.add(line);
                    }
                }
            }
        }

        BigDecimal totalRefundAmount = parseDecimalFromRequestData(requestData, "totalRefundAmount");
        if (totalRefundAmount.compareTo(BigDecimal.ZERO) == 0) {
            totalRefundAmount = parseDecimalFromRequestData(requestData, "refundAmount");
        }

        RefundType refundType = null;
        String refundTypeStr = (String) requestData.get("refundType");
        if (refundTypeStr != null) {
            try {
                refundType = RefundType.valueOf(refundTypeStr);
            } catch (IllegalArgumentException e) {
                log.warn("Invalid refundType in request_data: {}", refundTypeStr);
            }
        }
        String refundMethod = requestData.containsKey("paymentMethod")
                ? (String) requestData.get("paymentMethod")
                : transaction.getPaymentMethod();

        String requestedByRole = null;
        if (transaction.getRequestedBy() != null && transaction.getRequestedBy().getRoleId() != null) {
            var role = roleRepository.findById(transaction.getRequestedBy().getRoleId()).orElse(null);
            if (role != null) {
                requestedByRole = role.getName();
            }
        }

        RefundRequestResponse refundDetails = RefundRequestResponse.builder()
                .transactionId(transaction.getId())
                .orderId(transaction.getOrder() != null ? transaction.getOrder().getId() : null)
                .orderNumber(transaction.getOrder() != null ? transaction.getOrder().getOrderNumber() : null)
                .transactionNumber(transaction.getTransactionNumber())
                .paymentMethod(paymentMethodDisplaySupport.toDisplayName(transaction.getPaymentMethod(), userLocale))
                .paymentApp(transaction.getPaymentApp())
                .transactionAmount(transaction.getTransactionAmount())
                .refundType(refundType)
                .refundMethod(paymentMethodDisplaySupport.toDisplayName(refundMethod, userLocale))
                .totalRefundAmount(totalRefundAmount)
                .subtotalRefundAmount(parseDecimalFromRequestData(requestData, "subtotalRefundAmount"))
                .taxRefundAmount(parseDecimalFromRequestData(requestData, "taxRefundAmount"))
                .serviceChargeRefundAmount(parseDecimalFromRequestData(requestData, "serviceChargeRefundAmount"))
                .packingChargeRefundAmount(parseDecimalFromRequestData(requestData, "packingChargeRefundAmount"))
                .discountRefundAmount(parseDecimalFromRequestData(requestData, "discountRefundAmount"))
                .additionalDiscountRefundAmount(parseDecimalFromRequestData(requestData, "additionalDiscountRefundAmount"))
                .refundItems(refundItems)
                .refundReason((String) requestData.get("refundReason"))
                .requestStatus(transaction.getRequestStatus())
                .requestedAt(transaction.getRequestedAt() != null ? transaction.getRequestedAt().toLocalDateTime() : null)
                .requestedBy(transaction.getRequestedBy() != null ? transaction.getRequestedBy().getId() : null)
                .requestedByName(transaction.getRequestedBy() != null
                        ? transaction.getRequestedBy().getFirstName() + " " + transaction.getRequestedBy().getLastName()
                        : null)
                .requestedByRole(requestedByRole)
                .reviewedAt(transaction.getReviewedAt() != null
                        ? ((OffsetDateTime) transaction.getReviewedAt()).toLocalDateTime()
                        : null)
                .reviewedBy(transaction.getReviewedBy() != null ? transaction.getReviewedBy().getId() : null)
                .reviewedByName(transaction.getReviewedBy() != null
                        ? transaction.getReviewedBy().getFirstName() + " " + transaction.getReviewedBy().getLastName()
                        : null)
                .comments(transaction.getRequestComments())
                .restaurantId(transaction.getRestaurant() != null ? transaction.getRestaurant().getId() : null)
                .restaurantName(restaurantName)
                .build();

        RequestDetailsResponse response = RequestDetailsResponse.builder()
                .requestType(messageUtil.getMessage("request.type.refund", userLocale))
                .restaurantName(restaurantName)
                .profileUpdateDetails(null)
                .additionalDiscountDetails(null)
                .tableSectionDetails(null)
                .refundDetails(refundDetails)
                .build();

        return ResponseDto.<RequestDetailsResponse>builder()
                .message(messageUtil.getMessage("refund.requests.retrieved", userLocale))
                .data(response)
                .build();
    }

    private RefundRequestResponse.RefundItemResponse parseRefundLineItem(
            Map<String, Object> line, boolean comboLine, Locale userLocale) {
        String idKey = comboLine ? "orderedComboId" : "orderedItemId";
        Object idValue = line.get(idKey);
        if (idValue == null) {
            log.warn("Skipping refund line without {} in request_data", idKey);
            return null;
        }
        UUID lineId = UUID.fromString(idValue.toString());
        String nameKey = comboLine ? "comboName" : "itemName";
        String rawName = line.containsKey(nameKey) ? (String) line.get(nameKey) : null;
        String displayName = collaborator.resolveRefundLineDisplayName(rawName, lineId, comboLine, userLocale);
        int quantity = 1;
        if (line.get("refundQuantity") instanceof Number refundQty) {
            quantity = refundQty.intValue();
        } else if (line.get("quantity") instanceof Number qty) {
            quantity = qty.intValue();
        }
        return RefundRequestResponse.RefundItemResponse.builder()
                .itemId(lineId)
                .itemType(comboLine ? "COMBO" : "ITEM")
                .itemName(displayName)
                .quantity(quantity)
                .refundAmount(parseDecimalFromLine(line))
                .build();
    }

    private static BigDecimal parseDecimalFromRequestData(Map<String, Object> requestData, String key) {
        if (!requestData.containsKey(key)) {
            return BigDecimal.ZERO;
        }
        Object value = requestData.get(key);
        if (value instanceof Number number) {
            return new BigDecimal(number.toString());
        }
        return BigDecimal.ZERO;
    }

    private static BigDecimal parseDecimalFromLine(Map<String, Object> line) {
        Object value = line.get("refundAmount");
        if (value instanceof Number number) {
            return new BigDecimal(number.toString());
        }
        return BigDecimal.ZERO;
    }

    private static String resolveRestaurantName(Restaurant restaurant, Locale userLocale) {
        if (restaurant == null) {
            return null;
        }
        if (restaurant.getTranslations() == null || restaurant.getTranslations().isEmpty()) {
            return "Restaurant";
        }
        String userLanguage = userLocale != null ? userLocale.getLanguage() : "en";
        Optional<String> localized = restaurant.getTranslations().stream()
                .filter(t -> t.getLanguageCode() != null && t.getLanguageCode().startsWith(userLanguage))
                .map(com.gulfnet.shared_library.entity.RestaurantTranslation::getName)
                .filter(name -> name != null && !name.isBlank())
                .findFirst();
        if (localized.isPresent()) {
            return localized.get();
        }
        return restaurant.getTranslations().stream()
                .filter(t -> t.getLanguageCode() != null && t.getLanguageCode().startsWith("en"))
                .map(com.gulfnet.shared_library.entity.RestaurantTranslation::getName)
                .filter(name -> name != null && !name.isBlank())
                .findFirst()
                .orElse(restaurant.getTranslations().get(0).getName());
    }
}

