package com.gulfnet.restaurantmanagement.service.impl;

import com.gulfnet.restaurantmanagement.service.GmoLinkPlusAlterTranService;
import com.gulfnet.restaurantmanagement.service.GmoService;
import com.gulfnet.restaurantmanagement.service.OmiseService;
import com.gulfnet.restaurantmanagement.service.RefundService;
import com.gulfnet.restaurantmanagement.service.RestaurantAlertEvaluationService;
import com.gulfnet.restaurantmanagement.util.MessageUtil;
import com.gulfnet.shared_library.config.AWSService;
import com.gulfnet.shared_library.entity.Order;
import com.gulfnet.shared_library.entity.OrderedCombo;
import com.gulfnet.shared_library.entity.OrderedItem;
import com.gulfnet.shared_library.entity.Refund;
import com.gulfnet.shared_library.entity.RefundItem;
import com.gulfnet.shared_library.entity.Restaurant;
import com.gulfnet.shared_library.entity.Role;
import com.gulfnet.shared_library.entity.Transaction;
import com.gulfnet.shared_library.entity.User;
import com.gulfnet.shared_library.enums.RequestStatus;
import com.gulfnet.shared_library.enums.RefundType;
import com.gulfnet.shared_library.enums.TransactionStatus;
import com.gulfnet.shared_library.enums.DrawerEventType;
import com.gulfnet.shared_library.enums.ActionType;
import com.gulfnet.restaurantmanagement.service.AuditTrailService;
import com.gulfnet.shared_library.entity.CashDrawerLog;
import com.gulfnet.shared_library.entity.CashierShift;
import com.gulfnet.shared_library.model.request.CompleteRefundRequest;
import com.gulfnet.shared_library.model.request.RefundCalculateRequest;
import com.gulfnet.shared_library.model.response.dto.RefundCalculateResponse;
import com.gulfnet.shared_library.model.response.dto.RefundCompletionResponse;
import com.gulfnet.shared_library.model.response.dto.RefundDetailsResponse;
import com.gulfnet.shared_library.model.response.dto.RefundReceiptUrlResponse;
import com.gulfnet.shared_library.model.response.dto.RefundRequestResponse;
import com.gulfnet.shared_library.model.response.dto.ResponseDto;
import com.gulfnet.shared_library.repository.OrderedComboRepository;
import com.gulfnet.shared_library.repository.OrderedItemRepository;
import com.gulfnet.shared_library.repository.RefundItemRepository;
import com.gulfnet.shared_library.repository.RefundRepository;
import com.gulfnet.shared_library.repository.RoleRepository;
import com.gulfnet.shared_library.repository.TransactionRepository;
import com.gulfnet.shared_library.repository.UserRepository;
import com.gulfnet.shared_library.repository.CashDrawerLogRepository;
import com.gulfnet.shared_library.repository.CashierShiftRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import com.gulfnet.shared_library.util.CurrencyFormatter;
import com.gulfnet.restaurantmanagement.config.RestaurantChainConfigProperties;
import com.gulfnet.shared_library.enums.ChargeType;
import com.gulfnet.shared_library.enums.OrderType;
import com.gulfnet.shared_library.enums.AlcoholType;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class RefundServiceImpl implements RefundService {

    private static final String ROLE_CASHIER = "CASHIER";
    private static final String ROLE_MANAGER = "MANAGER";
    private static final String MSG_REFUND_NOT_FOUND = "refund.not.found";
    private static final java.util.Random GMO_REFUND_ID_RANDOM = new java.util.Random();

    private final RefundRepository refundRepository;
    private final RefundItemRepository refundItemRepository;
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final MessageUtil messageUtil;
    private final AWSService awsService;
    private final RestaurantChainConfigProperties restaurantChainConfigProperties;
    private final CashDrawerLogRepository cashDrawerLogRepository;
    private final CashierShiftRepository cashierShiftRepository;
    private final TransactionRepository transactionRepository;
    private final OrderedItemRepository orderedItemRepository;
    private final OrderedComboRepository orderedComboRepository;
    private final OmiseService omiseService;
    private final GmoService gmoService;
    private final GmoLinkPlusAlterTranService gmoLinkPlusAlterTranService;
    private final AuditTrailService auditTrailService;
    private final ReceiptGenerationAsyncService receiptGenerationAsyncService;

    // Lazy injection to avoid circular dependency and ensure alert evaluation runs after commit
    @Autowired
    @Lazy
    private RestaurantAlertEvaluationService restaurantAlertEvaluationService;

    /**
     * Completes a refund transaction. Only CASHIER and MANAGER roles can complete refunds.
     * Updates refund status, processes payment gateway refund if applicable, and updates transaction status.
     *
     * @param refundId the UUID of the refund to complete
     * @param request  the completion request containing refund details
     * @param userId   the ID of the user completing the refund
     * @param userRole the role of the user completing the refund (must be CASHIER or MANAGER)
     * @return {@link ResponseDto} containing refund completion details
     * @throws ResponseStatusException if user is unauthorized, refund not found, or completion fails
     */
    @Override
    @Transactional
    public ResponseDto<RefundCompletionResponse> completeRefund(
            UUID refundId,
            CompleteRefundRequest request,
            String userId,
            String userRole) {
        Locale userLocale = LocaleContextHolder.getLocale();
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);

        log.info("Request received to complete refund: {} by user: {} with role: {}", refundId, userId, userRole);

        User cashier = validateCashierOrManagerAccess(userId, userRole, userLocale);

        // Fetch refund
        Refund refund = refundRepository.findById(refundId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        messageUtil.getMessage(MSG_REFUND_NOT_FOUND, userLocale)));

        // Get transaction for later use
        Transaction transaction = refund.getTransaction();
        validateUserRestaurantAccess(cashier, transaction, userLocale);

        // Validate refund not already completed
        if (refund.getCompletedAt() != null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    messageUtil.getMessage("refund.already.completed", userLocale));
        }

        // Validate refund offered >= total refund amount
        if (request.getRefundOffered().compareTo(refund.getTotalRefundAmount()) < 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    messageUtil.getMessage("refund.offered.insufficient", userLocale));
        }

        String refundMethod = refund.getRefundMethod();
        if (!isSupportedRefundMethod(refundMethod)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    messageUtil.getMessage("refund.method.not.supported", userLocale));
        }

        RefundChangeCalculation change = calculateRefundChange(request, refund);

        if ("UPI".equalsIgnoreCase(refundMethod)) {
            processGatewayUpiRefund(transaction, refund, userLocale);
        } else if (isCardRefundMethod(refundMethod)) {
            gmoLinkPlusAlterTranService.submitCardReturn(transaction, refund.getTotalRefundAmount(), userLocale);
            persistCardRefundAwaitingNotify(refund, cashier, request, change, now);
            return ResponseDto.<RefundCompletionResponse>builder()
                    .message(messageUtil.getMessage("refund.card.gateway.pending", userLocale))
                    .data(buildPendingCardRefundCompletionResponse(refund, request, cashier, change))
                    .build();
        }

        RefundCompletionResponse completionResponse = finalizeRefundCompletion(
                refund, transaction, cashier, request, change, now, userLocale);
        log.info("Refund completed successfully: {}", refundId);
        return ResponseDto.<RefundCompletionResponse>builder()
                .message(messageUtil.getMessage("refund.completed.successfully", userLocale))
                .data(completionResponse)
                .build();
    }

    @Override
    @Transactional
    public void completeCardRefundFromGmoNotify(UUID transactionId) {
        Locale userLocale = Locale.ENGLISH;
        Refund refund = refundRepository.findByTransactionId(transactionId).orElse(null);
        if (refund == null) {
            log.info("[GMO LinkPlus refund notify] {} (transactionId={})",
                    messageUtil.getMessage("refund.card.notify.no.pending", userLocale), transactionId);
            return;
        }
        if (refund.getCompletedAt() != null) {
            log.info("[GMO LinkPlus refund notify] Refund {} already completed; idempotent skip", refund.getId());
            return;
        }
        if (!isCardRefundMethod(refund.getRefundMethod())) {
            log.info("[GMO LinkPlus refund notify] Ignoring RETURN for non-card refund method={}",
                    refund.getRefundMethod());
            return;
        }
        User cashier = refund.getCompletedBy();
        if (cashier == null) {
            log.warn("[GMO LinkPlus refund notify] {} (refundId={})",
                    messageUtil.getMessage("refund.card.notify.missing.initiator", userLocale), refund.getId());
            return;
        }

        Transaction transaction = refund.getTransaction();
        CompleteRefundRequest request = new CompleteRefundRequest();
        request.setRefundOffered(refund.getRefundOffered() != null
                ? refund.getRefundOffered() : refund.getTotalRefundAmount());
        request.setChangeCollected(refund.getChangeCollected());

        RefundChangeCalculation change = calculateRefundChange(request, refund);
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        finalizeRefundCompletion(refund, transaction, cashier, request, change, now, userLocale);
        log.info("[GMO LinkPlus refund notify] Refund {} finalized after RETURN for transaction {}",
                refund.getId(), transactionId);
    }

    /**
     * Generates a pre-signed URL for downloading a refund receipt PDF.
     * The receipt is generated on-demand and uploaded to S3 if it doesn't exist.
     *
     * @param refundId the UUID of the refund to get receipt URL for
     * @param userId   the ID of the user requesting the receipt (required)
     * @param userRole the role of the user requesting the receipt (required; CASHIER or MANAGER)
     * @return {@link ResponseDto} containing the pre-signed URL for the refund receipt
     * @throws ResponseStatusException if user is unauthorized, refund is not found, or receipt generation fails
     */
    @Override
    @Transactional(readOnly = true)
    public ResponseDto<RefundReceiptUrlResponse> getRefundReceiptPresignedUrl(UUID refundId, String userId, String userRole) {
        Locale userLocale = LocaleContextHolder.getLocale();

        log.info("Request received to get refund receipt presigned URL for refund: {} by user: {} with role: {}", refundId, userId, userRole);
        User user = validateCashierOrManagerAccess(userId, userRole, userLocale);

        // Find the refund
        Refund refund = refundRepository.findById(refundId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        messageUtil.getMessage(MSG_REFUND_NOT_FOUND, userLocale)));

        validateUserRestaurantAccess(user, refund.getTransaction(), userLocale);

        // Check if refund has a receipt URL
        String receiptUrl = refund.getReceiptUrl();
        if (receiptUrl == null || receiptUrl.trim().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                    messageUtil.getMessage("refund.receipt.not.found", userLocale));
        }

        try {
            // Generate presigned URL for the receipt with PDF-specific headers for inline preview
            String presignedUrl = awsService.getPreSignedUrlForPdf(receiptUrl);
            String presignedUrlAttachment = awsService.getPreSignedUrlForPdfAttachment(receiptUrl);

            RefundReceiptUrlResponse response = RefundReceiptUrlResponse.builder()
                    .refundId(refund.getId().toString())
                    .refundNumber(refund.getRefundNumber())
                    .receiptUrl(presignedUrl)
                    .downloadReceiptUrl(presignedUrlAttachment)
                    .build();

            log.info("Successfully generated presigned URL for refund: {}", refundId);
            return ResponseDto.<RefundReceiptUrlResponse>builder()
                    .data(response)
                    .message(messageUtil.getMessage("refund.receipt.url.generated.successfully", userLocale))
                    .build();

        } catch (Exception e) {
            log.error("Error generating presigned URL for refund: {}", refundId, e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    messageUtil.getMessage("refund.receipt.url.generation.failed", userLocale));
        }
    }

    /**
     * Retrieves detailed information about a refund including items, combos, amounts, and status.
     *
     * @param refundId the UUID of the refund to retrieve details for
     * @param userId   the ID of the user requesting the details (required)
     * @param userRole the role of the user requesting the details (required; CASHIER or MANAGER)
     * @return {@link ResponseDto} containing detailed refund information
     * @throws ResponseStatusException if user is unauthorized or refund is not found
     */
    @Override
    @Transactional(readOnly = true)
    public ResponseDto<RefundDetailsResponse> getRefundDetails(UUID refundId, String userId, String userRole) {
        Locale userLocale = LocaleContextHolder.getLocale();

        log.info("Request received to get refund details for refund: {} by user: {} with role: {}", refundId, userId, userRole);
        User user = validateCashierOrManagerAccess(userId, userRole, userLocale);

        // Fetch refund
        Refund refund = refundRepository.findById(refundId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        messageUtil.getMessage(MSG_REFUND_NOT_FOUND, userLocale)));

        // Get transaction and order
        Transaction transaction = refund.getTransaction();
        validateUserRestaurantAccess(user, transaction, userLocale);

        Order order = transaction.getOrder();
        if (order == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                    messageUtil.getMessage("order.not.found", userLocale));
        }

        // Get requester details
        String requestedByName = null;
        String requestedByRole = null;
        UUID requestedById = null;
        if (transaction.getRequestedBy() != null) {
            try {
                User requester = transaction.getRequestedBy();
                requestedById = requester.getId();
                requestedByName = requester.getFirstName() + " " + requester.getLastName();
                if (requester.getRoleId() != null) {
                    Role role = roleRepository.findById(requester.getRoleId()).orElse(null);
                    if (role != null) {
                        requestedByRole = role.getName();
                    }
                }
            } catch (Exception e) {
                log.warn("Error fetching requester details: {}", e.getMessage());
            }
        }

        // Get reviewer details
        String reviewedByName = null;
        UUID reviewedById = null;
        if (transaction.getReviewedBy() != null) {
            try {
                User reviewer = transaction.getReviewedBy();
                reviewedById = reviewer.getId();
                reviewedByName = reviewer.getFirstName() + " " + reviewer.getLastName();
            } catch (Exception e) {
                log.warn("Error fetching reviewer details: {}", e.getMessage());
            }
        }

        // Build response with required fields for cashier completion screen
        String currency = restaurantChainConfigProperties.getChain() != null ? restaurantChainConfigProperties.getChain().getCurrency() : null;
        RefundDetailsResponse response = RefundDetailsResponse.builder()
                .refundId(refund.getId())
                .refundNumber(refund.getRefundNumber())
                .totalRefundAmount(refund.getTotalRefundAmount() != null ? CurrencyFormatter.formatAmount(refund.getTotalRefundAmount(), currency) : null)
                .alcoholicTaxableRefundAmount(refund.getAlcoholicTaxableRefundAmount() != null
                        ? CurrencyFormatter.formatAmount(refund.getAlcoholicTaxableRefundAmount(), currency)
                        : null)
                .nonAlcoholicTaxableRefundAmount(refund.getNonAlcoholicTaxableRefundAmount() != null
                        ? CurrencyFormatter.formatAmount(refund.getNonAlcoholicTaxableRefundAmount(), currency)
                        : null)
                .refundMethod(refund.getRefundMethod())
                .requestStatus(transaction.getRequestStatus())
                .transactionId(transaction.getId())
                .orderId(order.getId())
                .transactionNumber(transaction.getTransactionNumber())
                .transactionStatus(transaction.getTransactionStatus())
                .orderNumber(order.getOrderNumber())
                .requestedAt(transaction.getRequestedAt() != null ? transaction.getRequestedAt().toLocalDateTime() : null)
                .requestedBy(requestedById)
                .requestedByName(requestedByName)
                .requestedByRole(requestedByRole)
                .reviewedAt(transaction.getReviewedAt() != null ? transaction.getReviewedAt().toLocalDateTime() : null)
                .reviewedBy(reviewedById)
                .reviewedByName(reviewedByName)
                .requestComments(transaction.getRequestComments())
                .build();

        log.info("Successfully retrieved refund details for refund: {}", refundId);
        return ResponseDto.<RefundDetailsResponse>builder()
                .data(response)
                .message(messageUtil.getMessage("refund.details.retrieved.successfully", userLocale))
                .build();
    }

    /**
     * Calculates refund amounts for items and combos in a transaction.
     * Computes proportional refunds for items, combos, discounts, taxes, and service charges.
     *
     * @param request  the refund calculation request containing transaction ID and items/combos to refund
     * @param userId   the ID of the user requesting the calculation (required)
     * @param userRole the role of the user requesting the calculation (required; CASHIER or MANAGER)
     * @return {@link ResponseDto} containing calculated refund amounts breakdown
     * @throws ResponseStatusException if user is unauthorized, transaction is not found, or calculation fails
     */
    @Override
    @Transactional(readOnly = true)
    public ResponseDto<RefundCalculateResponse> calculateRefundAmounts(RefundCalculateRequest request, String userId, String userRole) {
        Locale userLocale = LocaleContextHolder.getLocale();

        log.info("Request received to calculate refund amounts for transaction: {} by user: {} with role: {}", request.getTransactionId(), userId, userRole);
        User user = validateCashierOrManagerAccess(userId, userRole, userLocale);

        // Validate transaction exists
        Transaction transaction = transactionRepository.findById(request.getTransactionId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        messageUtil.getMessage("transaction.not.found", userLocale)));

        validateUserRestaurantAccess(user, transaction, userLocale);

        // Get order from transaction
        Order order = transaction.getOrder();
        if (order == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                    messageUtil.getMessage("order.not.found", userLocale));
        }

        // Validate that at least one item or combo is provided
        if ((request.getOrderedItems() == null || request.getOrderedItems().isEmpty()) &&
            (request.getOrderedCombos() == null || request.getOrderedCombos().isEmpty())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    messageUtil.getMessage("refund.calculate.items.required", userLocale));
        }

        // Validate no duplicate IDs
        validateNoDuplicateOrderedItemIds(request.getOrderedItems(), userLocale);
        validateNoDuplicateOrderedComboIds(request.getOrderedCombos(), userLocale);

        // Calculate subtotal for specified items and combos
        RefundSubtotalBreakdown subtotalBreakdown = calculateRefundSubtotalBreakdown(
                request.getOrderedItems(), request.getOrderedCombos(), order, userLocale);

        String currency = restaurantChainConfigProperties.getChain() != null 
                ? restaurantChainConfigProperties.getChain().getCurrency() 
                : null;
        
        RefundCalculateResponse response = calculateRefundResponseUsingPricingRules(
                subtotalBreakdown, order, currency, transaction.getId());

        log.info("Successfully calculated refund amounts for transaction: {}", request.getTransactionId());
        return ResponseDto.<RefundCalculateResponse>builder()
                .data(response)
                .message(messageUtil.getMessage("refund.calculate.success", userLocale))
                .build();
    }

    /**
     * Calculate refund amount for an item based on quantity.
     */
    private BigDecimal calculateItemRefundAmount(OrderedItem orderedItem, Integer refundQuantity) {
        BigDecimal itemRefundAmount;
        if (orderedItem.getTotalDiscountedItemAmount() != null) {
            itemRefundAmount = orderedItem.getTotalDiscountedItemAmount();
        } else if (orderedItem.getTotalItemAmount() != null) {
            itemRefundAmount = orderedItem.getTotalItemAmount();
        } else if (orderedItem.getPrice() != null) {
            itemRefundAmount = orderedItem.getPrice();
        } else {
            itemRefundAmount = BigDecimal.ZERO;
        }

        if (refundQuantity < orderedItem.getQuantity()) {
            BigDecimal unitPrice = itemRefundAmount.divide(
                    BigDecimal.valueOf(orderedItem.getQuantity()), 2, RoundingMode.HALF_UP);
            itemRefundAmount = unitPrice.multiply(BigDecimal.valueOf(refundQuantity));
        }
        return itemRefundAmount;
    }

    /**
     * Calculate refund amount for a combo based on quantity.
     */
    private BigDecimal calculateComboRefundAmount(OrderedCombo orderedCombo, Integer refundQuantity) {
        BigDecimal comboRefundAmount;
        if (orderedCombo.getTotalComboAmount() != null) {
            comboRefundAmount = orderedCombo.getTotalComboAmount();
        } else {
            comboRefundAmount = orderedCombo.getPrice() != null ? orderedCombo.getPrice() : BigDecimal.ZERO;
        }

        if (refundQuantity < orderedCombo.getQuantity()) {
            BigDecimal unitPrice = comboRefundAmount.divide(
                    BigDecimal.valueOf(orderedCombo.getQuantity()), 2, RoundingMode.HALF_UP);
            comboRefundAmount = unitPrice.multiply(BigDecimal.valueOf(refundQuantity));
        }
        return comboRefundAmount;
    }

    /**
     * Subtotal breakdown (and alcoholic/non-alcoholic split) for refund calculation.
     */
    private static class RefundSubtotalBreakdown {
        private final BigDecimal subtotalRefundAmount;
        private final BigDecimal alcoholicSubtotalRefundAmount;
        private final BigDecimal nonAlcoholicSubtotalRefundAmount;

        private RefundSubtotalBreakdown(BigDecimal subtotalRefundAmount,
                                       BigDecimal alcoholicSubtotalRefundAmount,
                                       BigDecimal nonAlcoholicSubtotalRefundAmount) {
            this.subtotalRefundAmount = subtotalRefundAmount;
            this.alcoholicSubtotalRefundAmount = alcoholicSubtotalRefundAmount;
            this.nonAlcoholicSubtotalRefundAmount = nonAlcoholicSubtotalRefundAmount;
        }
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
        BigDecimal totalComboAmount;
        if (orderedCombo.getTotalComboAmount() != null) {
            totalComboAmount = orderedCombo.getTotalComboAmount();
        } else if (orderedCombo.getPrice() != null) {
            totalComboAmount = orderedCombo.getPrice().multiply(BigDecimal.valueOf(orderedCombo.getQuantity()));
        } else {
            totalComboAmount = BigDecimal.ZERO;
        }

        // Calculate raw items price by summing OrderedItem amounts
        BigDecimal rawItemsPrice = BigDecimal.ZERO;
        List<ComboItemTaxBreakdown> rawItems = new ArrayList<>();
        
        for (OrderedItem orderedItem : orderedCombo.getOrderedItems()) {
            if (orderedItem == null) continue;
            
            // Get item amount (prefer totalDiscountedItemAmount, fallback to totalItemAmount)
            BigDecimal itemAmount;
            if (orderedItem.getTotalDiscountedItemAmount() != null) {
                itemAmount = orderedItem.getTotalDiscountedItemAmount();
            } else if (orderedItem.getTotalItemAmount() != null) {
                itemAmount = orderedItem.getTotalItemAmount();
            } else {
                itemAmount = BigDecimal.ZERO;
            }
            
            rawItemsPrice = rawItemsPrice.add(itemAmount);
            
            // Get alcohol type (prefer stored, fallback to item)
            AlcoholType alcoholType = orderedItem.getAlcoholType();
            if (alcoholType == null && orderedItem.getItem() != null) {
                alcoholType = orderedItem.getItem().getAlcoholType();
            }
            if (alcoholType == null) {
                alcoholType = AlcoholType.NON_ALCOHOLIC;
            }
            
            rawItems.add(new ComboItemTaxBreakdown(itemAmount, alcoholType));
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

    /**
     * Calculates the refund subtotal and its alcoholic/non-alcoholic breakdown for a refund request.
     * <p>
     * For each requested ordered item/combo, this method:
     * </p>
     * <ul>
     *   <li>Loads the persisted {@link OrderedItem}/{@link OrderedCombo} by id and validates it belongs to the given {@link Order}.</li>
     *   <li>Validates refund quantity (defaults to original quantity when null; must be \(1..originalQuantity\)).</li>
     *   <li>Computes the refund amount using existing pricing rules (including proportional handling for combos).</li>
     *   <li>Accumulates totals into overall subtotal and alcoholic/non-alcoholic subtotals (used later for tax split).</li>
     * </ul>
     *
     * @param orderedItems  item refund calculation inputs (may be null)
     * @param orderedCombos combo refund calculation inputs (may be null)
     * @param order         owning order to validate membership against
     * @param userLocale    locale used for localized exception messages
     * @return subtotal breakdown for the refund request
     * @throws ResponseStatusException when referenced entities are missing, do not belong to the order, or quantities are invalid
     */
    private RefundSubtotalBreakdown calculateRefundSubtotalBreakdown(
            List<RefundCalculateRequest.ItemRefundCalculate> orderedItems,
            List<RefundCalculateRequest.ComboRefundCalculate> orderedCombos,
            Order order,
            Locale userLocale) {
        BigDecimal subtotal = BigDecimal.ZERO;
        BigDecimal alcoholicSubtotal = BigDecimal.ZERO;
        BigDecimal nonAlcoholicSubtotal = BigDecimal.ZERO;

        // Items
        if (orderedItems != null) {
            for (RefundCalculateRequest.ItemRefundCalculate itemRequest : orderedItems) {
                OrderedItem orderedItem = orderedItemRepository.findById(itemRequest.getOrderedItemId())
                        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                                messageUtil.getMessage("ordered.item.not.found", userLocale)));

                if (!orderedItem.getOrder().getId().equals(order.getId())) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                            messageUtil.getMessage("refund.item.not.belongs.to.order", userLocale));
                }

                Integer refundQuantity = itemRequest.getQuantity() != null ? itemRequest.getQuantity() : orderedItem.getQuantity();
                if (refundQuantity <= 0 || refundQuantity > orderedItem.getQuantity()) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                            messageUtil.getMessage("refund.invalid.quantity", userLocale));
                }

                BigDecimal amount = calculateItemRefundAmount(orderedItem, refundQuantity);
                subtotal = subtotal.add(amount);

                AlcoholType alcoholType = orderedItem.getAlcoholType();
                if (alcoholType == null && orderedItem.getItem() != null) {
                    alcoholType = orderedItem.getItem().getAlcoholType();
                }
                if (alcoholType == AlcoholType.ALCOHOLIC) {
                    alcoholicSubtotal = alcoholicSubtotal.add(amount);
                } else {
                    nonAlcoholicSubtotal = nonAlcoholicSubtotal.add(amount);
                }
            }
        }

        // Combos - include in subtotal and alcoholic/non-alcoholic breakdown
        String currency = restaurantChainConfigProperties.getChain() != null 
                ? restaurantChainConfigProperties.getChain().getCurrency() 
                : "USD";
        
        if (orderedCombos != null) {
            for (RefundCalculateRequest.ComboRefundCalculate comboRequest : orderedCombos) {
                OrderedCombo orderedCombo = orderedComboRepository.findById(comboRequest.getOrderedComboId())
                        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                                messageUtil.getMessage("ordered.combo.not.found", userLocale)));

                if (!orderedCombo.getOrder().getId().equals(order.getId())) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                            messageUtil.getMessage("refund.combo.not.belongs.to.order", userLocale));
                }

                Integer refundQuantity = comboRequest.getQuantity() != null ? comboRequest.getQuantity() : orderedCombo.getQuantity();
                if (refundQuantity <= 0 || refundQuantity > orderedCombo.getQuantity()) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                            messageUtil.getMessage("refund.invalid.quantity", userLocale));
                }

                BigDecimal comboAmount = calculateComboRefundAmount(orderedCombo, refundQuantity);
                subtotal = subtotal.add(comboAmount);

                // Extract combo item prices for alcoholic/non-alcoholic breakdown
                List<ComboItemTaxBreakdown> comboItemBreakdowns = extractComboItemTaxBreakdown(
                        orderedCombo, refundQuantity, currency);
                
                RefundAlcoholSplit split = accumulateAlcoholSplitFromComboBreakdowns(comboItemBreakdowns);
                alcoholicSubtotal = alcoholicSubtotal.add(split.alcoholic());
                nonAlcoholicSubtotal = nonAlcoholicSubtotal.add(split.nonAlcoholic());
            }
        }

        return new RefundSubtotalBreakdown(subtotal, alcoholicSubtotal, nonAlcoholicSubtotal);
    }

    private record RefundAlcoholSplit(BigDecimal alcoholic, BigDecimal nonAlcoholic) {}

    private RefundAlcoholSplit accumulateAlcoholSplitFromComboBreakdowns(List<ComboItemTaxBreakdown> comboItemBreakdowns) {
        BigDecimal alcoholic = BigDecimal.ZERO;
        BigDecimal nonAlcoholic = BigDecimal.ZERO;
        if (comboItemBreakdowns == null) {
            return new RefundAlcoholSplit(alcoholic, nonAlcoholic);
        }
        for (ComboItemTaxBreakdown itemBreakdown : comboItemBreakdowns) {
            boolean skip = itemBreakdown == null || itemBreakdown.amount == null;
            if (skip) {
                continue;
            }
            ComboItemTaxBreakdown nonNull = java.util.Objects.requireNonNull(itemBreakdown);
            if (nonNull.alcoholType == AlcoholType.ALCOHOLIC) {
                alcoholic = alcoholic.add(nonNull.amount);
            } else {
                nonAlcoholic = nonAlcoholic.add(nonNull.amount);
            }
        }
        return new RefundAlcoholSplit(alcoholic, nonAlcoholic);
    }

    /**
     * Calculate refund amounts using the same pricing rules as order calculation:
     * - Apply proportional order-level discount first (based on ratio of refunded subtotal / order subtotal)
     * - Apply service charge (dine-in) or packing charge (takeaway) on the discounted subtotal
     * - Calculate tax separately for alcoholic/non-alcoholic on (discounted subtotal + proportional charge)
     * - Apply proportional additional discount at the end
     */
    private RefundCalculateResponse calculateRefundResponseUsingPricingRules(
            RefundSubtotalBreakdown breakdown, Order order, String currency, UUID transactionId) {
        
        BigDecimal subtotalRefundAmount = breakdown.subtotalRefundAmount != null ? breakdown.subtotalRefundAmount : BigDecimal.ZERO;
        BigDecimal orderSubtotal = order.getSubTotal() != null ? order.getSubTotal() : BigDecimal.ZERO;

        BigDecimal refundRatio = BigDecimal.ZERO;
        if (orderSubtotal.compareTo(BigDecimal.ZERO) > 0 && subtotalRefundAmount.compareTo(BigDecimal.ZERO) > 0) {
            refundRatio = subtotalRefundAmount.divide(orderSubtotal, 4, RoundingMode.HALF_UP);
        }

        int scale = CurrencyFormatter.getDecimalPlaces(currency);

        // Proportional discounts (keep same behavior as existing refund logic)
        BigDecimal discountRefundAmount = order.getDiscountAmount() != null
                ? order.getDiscountAmount().multiply(refundRatio).setScale(scale, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;
        BigDecimal additionalDiscountRefundAmount = order.getAdditionalDiscountAmount() != null
                ? order.getAdditionalDiscountAmount().multiply(refundRatio).setScale(scale, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;

        BigDecimal discountedSubtotalRefundAmount = subtotalRefundAmount.subtract(discountRefundAmount).max(BigDecimal.ZERO);

        // Service/Packing charges based on order type using current chain config
        OrderType orderType = order.getOrderType();
        RestaurantChainConfigProperties.RestaurantChainData chain = restaurantChainConfigProperties.getChain();
        RefundCharges charges = calculateRefundCharges(chain, orderType, discountedSubtotalRefundAmount, currency);
        RefundAlcoholSplitScaled split = scaleAlcoholSplit(breakdown, discountedSubtotalRefundAmount);
        BigDecimal chargeAmount = charges.serviceChargeRefundAmount().add(charges.packingChargeRefundAmount());

        RefundTaxes taxes = calculateRefundTaxes(chain, orderType, split, chargeAmount, currency);

        BigDecimal totalBeforeAdditionalDiscount =
                discountedSubtotalRefundAmount
                        .add(charges.serviceChargeRefundAmount())
                        .add(charges.packingChargeRefundAmount())
                        .add(taxes.taxRefundAmount());
        BigDecimal totalRefundAmount = totalBeforeAdditionalDiscount.subtract(additionalDiscountRefundAmount);
        if (totalRefundAmount.compareTo(BigDecimal.ZERO) < 0) totalRefundAmount = BigDecimal.ZERO;

        totalRefundAmount = totalRefundAmount.setScale(scale, RoundingMode.HALF_UP);

        return RefundCalculateResponse.builder()
                .transactionId(transactionId)
                .orderId(order.getId())
                .subtotalRefundAmount(CurrencyFormatter.formatAmount(subtotalRefundAmount, currency))
                .taxRefundAmount(CurrencyFormatter.formatAmount(taxes.taxRefundAmount(), currency))
                .alcoholicTaxRefundAmount(CurrencyFormatter.formatAmount(taxes.alcoholicTaxRefundAmount(), currency))
                .alcoholicTaxableRefundAmount(taxes.alcoholicTaxableRefundAmount())
                .nonAlcoholicTaxRefundAmount(CurrencyFormatter.formatAmount(taxes.nonAlcoholicTaxRefundAmount(), currency))
                .nonAlcoholicTaxableRefundAmount(taxes.nonAlcoholicTaxableRefundAmount())
                .serviceChargeRefundAmount(CurrencyFormatter.formatAmount(charges.serviceChargeRefundAmount(), currency))
                .packingChargeRefundAmount(CurrencyFormatter.formatAmount(charges.packingChargeRefundAmount(), currency))
                .discountRefundAmount(CurrencyFormatter.formatAmount(discountRefundAmount, currency))
                .additionalDiscountRefundAmount(CurrencyFormatter.formatAmount(additionalDiscountRefundAmount, currency))
                .totalRefundAmount(CurrencyFormatter.formatAmount(totalRefundAmount, currency))
                .originalOrderSubtotal(CurrencyFormatter.formatAmount(orderSubtotal, currency))
                .originalOrderTaxAmount(order.getTaxAmount() != null ? CurrencyFormatter.formatAmount(order.getTaxAmount(), currency) : null)
                .originalOrderServiceChargeAmount(order.getServiceChargeAmount() != null ? CurrencyFormatter.formatAmount(order.getServiceChargeAmount(), currency) : null)
                .originalOrderPackingChargeAmount(order.getPackingChargeAmount() != null ? CurrencyFormatter.formatAmount(order.getPackingChargeAmount(), currency) : null)
                .refundRatio(refundRatio)
                .build();
    }

    private record RefundCharges(BigDecimal serviceChargeRefundAmount, BigDecimal packingChargeRefundAmount) {}

    private RefundCharges calculateRefundCharges(RestaurantChainConfigProperties.RestaurantChainData chain,
                                                 OrderType orderType,
                                                 BigDecimal discountedSubtotalRefundAmount,
                                                 String currency) {
        BigDecimal serviceChargeRefundAmount = BigDecimal.ZERO;
        BigDecimal packingChargeRefundAmount = BigDecimal.ZERO;
        if (chain == null || orderType == null) {
            return new RefundCharges(serviceChargeRefundAmount, packingChargeRefundAmount);
        }
        if (orderType == OrderType.DINE_IN) {
            RestaurantChainConfigProperties.ServiceChargesForDineIn service = chain.getServiceChargesForDineIn();
            if (service != null) {
                serviceChargeRefundAmount = calculateChargeAmount(
                        discountedSubtotalRefundAmount,
                        BigDecimal.valueOf(service.getValue()),
                        service.getType(),
                        currency);
            }
        } else if (orderType == OrderType.TAKEAWAY) {
            boolean includePacking = Boolean.TRUE.equals(chain.isIncludePackingChargesForTakeaway());
            if (includePacking && chain.getPackingChargesForTakeaway() != null) {
                RestaurantChainConfigProperties.PackingChargesForTakeaway packing = chain.getPackingChargesForTakeaway();
                packingChargeRefundAmount = calculateChargeAmount(
                        discountedSubtotalRefundAmount,
                        BigDecimal.valueOf(packing.getValue()),
                        packing.getType(),
                        currency);
            }
        }
        return new RefundCharges(serviceChargeRefundAmount, packingChargeRefundAmount);
    }

    private record RefundAlcoholSplitScaled(BigDecimal alcoholicSubtotal, BigDecimal nonAlcoholicSubtotal) {}

    private RefundAlcoholSplitScaled scaleAlcoholSplit(RefundSubtotalBreakdown breakdown, BigDecimal discountedSubtotalRefundAmount) {
        BigDecimal alcoholicSubtotal = breakdown.alcoholicSubtotalRefundAmount != null ? breakdown.alcoholicSubtotalRefundAmount : BigDecimal.ZERO;
        BigDecimal nonAlcoholicSubtotal = breakdown.nonAlcoholicSubtotalRefundAmount != null ? breakdown.nonAlcoholicSubtotalRefundAmount : BigDecimal.ZERO;
        BigDecimal splitTotal = alcoholicSubtotal.add(nonAlcoholicSubtotal);

        if (splitTotal.compareTo(BigDecimal.ZERO) > 0 && discountedSubtotalRefundAmount.compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal scaleFactor = discountedSubtotalRefundAmount.divide(splitTotal, 10, RoundingMode.HALF_UP);
            alcoholicSubtotal = alcoholicSubtotal.multiply(scaleFactor);
            nonAlcoholicSubtotal = nonAlcoholicSubtotal.multiply(scaleFactor);
        } else {
            alcoholicSubtotal = BigDecimal.ZERO;
            nonAlcoholicSubtotal = discountedSubtotalRefundAmount;
        }
        return new RefundAlcoholSplitScaled(alcoholicSubtotal, nonAlcoholicSubtotal);
    }

    private record RefundTaxes(BigDecimal taxRefundAmount,
                              BigDecimal alcoholicTaxRefundAmount,
                              BigDecimal nonAlcoholicTaxRefundAmount,
                              BigDecimal alcoholicTaxableRefundAmount,
                              BigDecimal nonAlcoholicTaxableRefundAmount) {}

    private RefundTaxes calculateRefundTaxes(RestaurantChainConfigProperties.RestaurantChainData chain,
                                             OrderType orderType,
                                             RefundAlcoholSplitScaled split,
                                             BigDecimal chargeAmount,
                                             String currency) {
        BigDecimal chargeToAlcoholic = BigDecimal.ZERO;
        BigDecimal chargeToNonAlcoholic = BigDecimal.ZERO;
        BigDecimal denomForChargeSplit = split.alcoholicSubtotal().add(split.nonAlcoholicSubtotal());
        if (chargeAmount.compareTo(BigDecimal.ZERO) > 0 && denomForChargeSplit.compareTo(BigDecimal.ZERO) > 0) {
            chargeToAlcoholic = chargeAmount.multiply(split.alcoholicSubtotal())
                    .divide(denomForChargeSplit, 20, RoundingMode.HALF_UP);
            chargeToNonAlcoholic = chargeAmount.subtract(chargeToAlcoholic);
        }
        BigDecimal alcoholicTaxBase = split.alcoholicSubtotal().add(chargeToAlcoholic);
        BigDecimal nonAlcoholicTaxBase = split.nonAlcoholicSubtotal().add(chargeToNonAlcoholic);

        BigDecimal alcoholicTaxableRefundAmount = CurrencyFormatter.formatAmount(alcoholicTaxBase, currency);
        BigDecimal nonAlcoholicTaxableRefundAmount = CurrencyFormatter.formatAmount(nonAlcoholicTaxBase, currency);

        BigDecimal taxRefundAmount = BigDecimal.ZERO;
        BigDecimal alcoholicTaxRefundAmount = BigDecimal.ZERO;
        BigDecimal nonAlcoholicTaxRefundAmount = BigDecimal.ZERO;
        if (chain != null && chain.getTaxSetup() != null && orderType != null) {
            RestaurantChainConfigProperties.TaxSetup taxSetup = chain.getTaxSetup();
            RestaurantChainConfigProperties.TaxSetup.TaxCharge alcoholicTaxCharge =
                    (orderType == OrderType.DINE_IN ? taxSetup.getDineIn().getAlcoholic() : taxSetup.getTakeAway().getAlcoholic());
            RestaurantChainConfigProperties.TaxSetup.TaxCharge nonAlcoholicTaxCharge =
                    (orderType == OrderType.DINE_IN ? taxSetup.getDineIn().getNonAlcoholic() : taxSetup.getTakeAway().getNonAlcoholic());

            alcoholicTaxRefundAmount = calculateChargeAmount(
                    alcoholicTaxBase,
                    BigDecimal.valueOf(alcoholicTaxCharge.getValue()),
                    alcoholicTaxCharge.getType(),
                    currency);
            nonAlcoholicTaxRefundAmount = calculateChargeAmount(
                    nonAlcoholicTaxBase,
                    BigDecimal.valueOf(nonAlcoholicTaxCharge.getValue()),
                    nonAlcoholicTaxCharge.getType(),
                    currency);
            taxRefundAmount = alcoholicTaxRefundAmount.add(nonAlcoholicTaxRefundAmount);
        }

        return new RefundTaxes(taxRefundAmount, alcoholicTaxRefundAmount, nonAlcoholicTaxRefundAmount,
                alcoholicTaxableRefundAmount, nonAlcoholicTaxableRefundAmount);
    }

    private BigDecimal calculateChargeAmount(BigDecimal baseAmount, BigDecimal value, ChargeType type, String currency) {
        if (type == null) type = ChargeType.PERCENT;
        BigDecimal amount = (type == ChargeType.PERCENT)
                ? baseAmount.multiply(value).divide(BigDecimal.valueOf(100), 10, RoundingMode.HALF_UP)
                : value;
        return CurrencyFormatter.formatAmount(amount, currency);
    }

    private User validateCashierOrManagerAccess(String userId, String userRole, Locale userLocale) {
        if (!ROLE_CASHIER.equals(userRole) && !ROLE_MANAGER.equals(userRole)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    messageUtil.getMessage("refund.access.unauthorized", userLocale));
        }
        return userRepository.findById(UUID.fromString(userId))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        messageUtil.getMessage("user.not.found", userLocale)));
    }

    private UUID requireUserRestaurantId(User user, Locale userLocale) {
        if (user.getRestaurantId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    messageUtil.getMessage("user.restaurant.not.assigned", userLocale));
        }
        return user.getRestaurantId();
    }

    private void validateUserRestaurantAccess(User user, Transaction transaction, Locale userLocale) {
        UUID userRestaurantId = requireUserRestaurantId(user, userLocale);
        if (transaction == null || transaction.getRestaurant() == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                    messageUtil.getMessage("transaction.not.found", userLocale));
        }
        if (!userRestaurantId.equals(transaction.getRestaurant().getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    messageUtil.getMessage("refund.restaurant.access.unauthorized", userLocale));
        }
    }

    /**
     * Retrieves all manager-initiated refund requests for a specific restaurant.
     * Returns refunds that were initiated by managers and are pending completion.
     *
     * @param restaurantId the restaurant ID to get refunds for
     * @param userId        the ID of the user requesting the list (required)
     * @param userRole      the role of the user requesting the list (required; CASHIER or MANAGER)
     * @return {@link ResponseDto} containing list of manager-initiated refund requests
     * @throws ResponseStatusException if user is unauthorized or not found
     */
    @Override
    @Transactional(readOnly = true)
    public ResponseDto<List<RefundRequestResponse>> getManagerInitiatedRefunds(String restaurantId, String userId, String userRole) {
        Locale userLocale = LocaleContextHolder.getLocale();
        
        log.info("Request received to get manager-initiated refunds for restaurant: {} by user: {} with role: {}", restaurantId, userId, userRole);

        User user = validateCashierOrManagerAccess(userId, userRole, userLocale);
        UUID userRestaurantId = requireUserRestaurantId(user, userLocale);

        // Parse restaurantId if provided
        if (restaurantId != null && !restaurantId.trim().isEmpty() && !"null".equalsIgnoreCase(restaurantId)) {
            try {
                UUID restaurantIdFilter = UUID.fromString(restaurantId.trim());
                if (!userRestaurantId.equals(restaurantIdFilter)) {
                    throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                            messageUtil.getMessage("refund.restaurant.access.unauthorized", userLocale));
                }
            } catch (IllegalArgumentException e) {
                String errorMessage = messageUtil.getMessage("error.invalid.restaurantId", userLocale, restaurantId);
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, errorMessage);
            }
        }

        final UUID finalRestaurantId = userRestaurantId;

        // Get all refunds
        List<Refund> allRefunds = refundRepository.findAll();

        // Filter refunds where transaction has request_status = NONE (manager-initiated refunds)
        List<Refund> managerInitiatedRefunds = allRefunds.stream()
                .filter(refund -> {
                    Transaction transaction = refund.getTransaction();
                    return transaction != null && transaction.getRequestStatus() == RequestStatus.NONE;
                })
                .collect(Collectors.toList());

        // Build response list
        List<RefundRequestResponse> refundResponses = managerInitiatedRefunds.stream()
                .filter(refund -> {
                    Transaction transaction = refund.getTransaction();
                    return transaction != null
                            && transaction.getRestaurant() != null
                            && transaction.getRestaurant().getId().equals(finalRestaurantId);
                })
                .map(refund -> buildRefundRequestResponseFromRefund(refund, userLocale))
                .collect(Collectors.toList());
        
        log.info("Successfully retrieved {} manager-initiated refunds", refundResponses.size());
        return ResponseDto.<List<RefundRequestResponse>>builder()
                .data(refundResponses)
                .message(messageUtil.getMessage("refund.requests.retrieved", userLocale))
                .build();
    }
    
    /**
     * Build RefundRequestResponse from Refund entity (for manager-initiated refunds where request_status=NONE)
     */
    private RefundRequestResponse buildRefundRequestResponseFromRefund(Refund refund, Locale userLocale) {
        Transaction transaction = refund.getTransaction();
        Order order = transaction != null ? transaction.getOrder() : null;

        String restaurantName = resolveRestaurantName(transaction, userLocale);
        List<RefundRequestResponse.RefundItemResponse> refundItemResponses = buildRefundItemResponses(refund.getId(), userLocale);
        String requestedByRole = resolveRequestedByRole(transaction);

        return RefundRequestResponse.builder()
                .refundId(refund.getId())
                .transactionId(transaction != null ? transaction.getId() : null)
                .orderId(order != null ? order.getId() : null)
                .orderNumber(order != null ? order.getOrderNumber() : null)
                .transactionNumber(transaction != null ? transaction.getTransactionNumber() : null)
                .paymentMethod(transaction != null ? transaction.getPaymentMethod() : null)
                .paymentApp(transaction != null ? transaction.getPaymentApp() : null)
                .transactionAmount(transaction != null ? transaction.getTransactionAmount() : null)
                .requestDate(refund.getCreatedAt() != null ? refund.getCreatedAt().toLocalDate() : null)
                .raisedBy(transaction != null && transaction.getReviewedBy() != null ? transaction.getReviewedBy().getId() : null)
                .refundType(refund.getRefundType())
                .refundMethod(refund.getRefundMethod())
                .totalRefundAmount(refund.getTotalRefundAmount())
                .subtotalRefundAmount(refund.getSubtotalRefundAmount())
                .taxRefundAmount(refund.getTaxRefundAmount())
                .alcoholicTaxRefundAmount(refund.getAlcoholicTaxRefundAmount())
                .nonAlcoholicTaxRefundAmount(refund.getNonAlcoholicTaxRefundAmount())
                .serviceChargeRefundAmount(refund.getServiceChargeRefundAmount())
                .packingChargeRefundAmount(refund.getPackingChargeRefundAmount())
                .discountRefundAmount(refund.getDiscountRefundAmount())
                .additionalDiscountRefundAmount(refund.getAdditionalDiscountRefundAmount())
                .refundItems(refundItemResponses)
                .refundReason(refund.getRefundReason())
                .requestStatus(transaction != null ? transaction.getRequestStatus() : RequestStatus.NONE)
                .requestedAt(transaction != null && transaction.getRequestedAt() != null ? transaction.getRequestedAt().toLocalDateTime() : null)
                .requestedBy(transaction != null && transaction.getRequestedBy() != null ? transaction.getRequestedBy().getId() : null)
                .requestedByName(transaction != null && transaction.getRequestedBy() != null
                        ? transaction.getRequestedBy().getFirstName() + " " + transaction.getRequestedBy().getLastName()
                        : null)
                .requestedByRole(requestedByRole)
                .reviewedAt(transaction != null && transaction.getReviewedAt() != null
                        ? transaction.getReviewedAt().toLocalDateTime()
                        : null)
                .reviewedBy(transaction != null && transaction.getReviewedBy() != null ? transaction.getReviewedBy().getId() : null)
                .reviewedByName(transaction != null && transaction.getReviewedBy() != null
                        ? transaction.getReviewedBy().getFirstName() + " " + transaction.getReviewedBy().getLastName()
                        : null)
                .comments(transaction != null ? transaction.getRequestComments() : null)
                .restaurantId(transaction != null && transaction.getRestaurant() != null ? transaction.getRestaurant().getId() : null)
                .restaurantName(restaurantName)
                .transactionStatus(transaction != null ? transaction.getTransactionStatus() : null)
                .build();
    }

    // ==================== Extracted helper methods ====================

    private record RefundChangeCalculation(
            BigDecimal expectedChange,
            BigDecimal changeCollected,
            BigDecimal discrepancyAmount,
            String discrepancyReason) {}

    private static boolean isSupportedRefundMethod(String refundMethod) {
        return "CASH".equalsIgnoreCase(refundMethod)
                || "UPI".equalsIgnoreCase(refundMethod)
                || isCardRefundMethod(refundMethod);
    }

    private static boolean isCardRefundMethod(String refundMethod) {
        if (refundMethod == null) {
            return false;
        }
        String m = refundMethod.trim();
        return "CARD".equalsIgnoreCase(m)
                || "CREDIT_CARD".equalsIgnoreCase(m)
                || "DEBIT_CARD".equalsIgnoreCase(m);
    }

    private RefundChangeCalculation calculateRefundChange(CompleteRefundRequest request, Refund refund) {
        BigDecimal expectedChange = request.getRefundOffered().subtract(refund.getTotalRefundAmount());
        BigDecimal changeCollected = request.getChangeCollected() != null ? request.getChangeCollected() : BigDecimal.ZERO;
        BigDecimal discrepancyAmount = BigDecimal.ZERO;
        String discrepancyReason = null;

        if (expectedChange.compareTo(BigDecimal.ZERO) > 0) {
            if (request.getChangeCollected() == null) {
                changeCollected = expectedChange;
            } else {
                discrepancyAmount = changeCollected.subtract(expectedChange);
                if (discrepancyAmount.compareTo(BigDecimal.ZERO) != 0) {
                    discrepancyReason = String.format("Change collected (%.2f) differs from expected (%.2f)",
                            changeCollected, expectedChange);
                }
            }
        }
        return new RefundChangeCalculation(expectedChange, changeCollected, discrepancyAmount, discrepancyReason);
    }

    private void persistCardRefundAwaitingNotify(
            Refund refund,
            User cashier,
            CompleteRefundRequest request,
            RefundChangeCalculation change,
            OffsetDateTime now) {
        refund.setRefundOffered(request.getRefundOffered());
        refund.setChangeCollected(change.changeCollected());
        refund.setCompletedBy(cashier);
        refund.setUpdatedAt(now);
        refundRepository.save(refund);
        log.info("Card refund {} submitted to GMO; awaiting RETURN notification (txId={})",
                refund.getId(), refund.getTransaction().getId());
    }

    private RefundCompletionResponse buildPendingCardRefundCompletionResponse(
            Refund refund,
            CompleteRefundRequest request,
            User cashier,
            RefundChangeCalculation change) {
        String currency = restaurantChainConfigProperties.getChain() != null
                ? restaurantChainConfigProperties.getChain().getCurrency() : null;
        return RefundCompletionResponse.builder()
                .refundId(refund.getId())
                .refundNumber(refund.getRefundNumber())
                .refundAmount(refund.getTotalRefundAmount() != null
                        ? CurrencyFormatter.formatAmount(refund.getTotalRefundAmount(), currency) : null)
                .refundOffered(request.getRefundOffered() != null
                        ? CurrencyFormatter.formatAmount(request.getRefundOffered(), currency) : null)
                .changeExpected(change.expectedChange() != null
                        ? CurrencyFormatter.formatAmount(change.expectedChange(), currency) : null)
                .changeCollected(change.changeCollected() != null
                        ? CurrencyFormatter.formatAmount(change.changeCollected(), currency) : null)
                .discrepancyAmount(change.discrepancyAmount() != null
                        ? CurrencyFormatter.formatAmount(change.discrepancyAmount(), currency) : null)
                .discrepancyReason(change.discrepancyReason())
                .completedAt(null)
                .completedBy(cashier.getId())
                .completedByName(cashier.getFirstName() + " " + cashier.getLastName())
                .receipt(null)
                .cashDrawerLogId(null)
                .build();
    }

    private RefundCompletionResponse finalizeRefundCompletion(
            Refund refund,
            Transaction transaction,
            User cashier,
            CompleteRefundRequest request,
            RefundChangeCalculation change,
            OffsetDateTime now,
            Locale userLocale) {
        UUID refundId = refund.getId();
        String refundMethod = refund.getRefundMethod();

        refund.setRefundOffered(request.getRefundOffered());
        refund.setChangeCollected(change.changeCollected());
        refund.setCompletedAt(now);
        refund.setCompletedBy(cashier);
        refund.setUpdatedAt(now);

        RefundType refundType = refund.getRefundType();
        if (refundType == RefundType.FULL) {
            transaction.setTransactionStatus(TransactionStatus.REFUNDED);
        } else if (refundType == RefundType.PARTIAL) {
            transaction.setTransactionStatus(TransactionStatus.PARTIALLY_REFUNDED);
        }
        transaction.setUpdatedAt(now);

        transactionRepository.save(transaction);
        transactionRepository.flush();

        refund = refundRepository.save(refund);
        refundRepository.flush();

        UUID cashDrawerLogId = "CASH".equalsIgnoreCase(refundMethod)
                ? createCashDrawerRefundLog(refund, cashier, request.getRefundOffered(), change.changeCollected())
                : null;

        Restaurant restaurant = transaction.getRestaurant();
        RefundCompletionResponse.RefundReceiptResponse receiptResponse =
                generateReceiptAndBuildResponse(refund, refundId);

        createRefundCompletionAuditTrail(new RefundCompletionAuditTrailContext(
                cashier,
                restaurant,
                transaction,
                refund,
                request.getRefundOffered(),
                change.changeCollected(),
                change.discrepancyAmount(),
                change.discrepancyReason()
        ));

        RefundCompletionResponse completionResponse = buildRefundCompletionResponse(new RefundCompletionResponseContext(
                refund,
                request,
                cashier,
                now,
                change.expectedChange(),
                change.changeCollected(),
                change.discrepancyAmount(),
                change.discrepancyReason(),
                receiptResponse,
                cashDrawerLogId
        ));

        evaluateAlertsAfterRefundCommit(restaurant, userLocale);
        scheduleRefundReceiptGenerationAfterCommit(refundId);
        return completionResponse;
    }

    /**
     * Decide which gateway to use for UPI refund (GMO vs Omise) based on transaction data.
     */
    private void processGatewayUpiRefund(Transaction transaction, Refund refund, Locale userLocale) {
        // If transaction has GMO identifiers, use GMO refund flow
        if (transaction.getGmoOrderId() != null || transaction.getGmoOrderReservationId() != null) {
            String gmoRefundId = generateGmoRefundId();
            refund.setGmoRefundId(gmoRefundId);
            gmoService.processGmoRefund(transaction, refund, gmoRefundId, userLocale);
            return;
        }

        // Fallback to Omise when no GMO identifiers are present
        processUpiRefund(transaction, refund, userLocale);
    }

    /**
     * Process UPI refund via Omise gateway.
     * Handles both PayPay (JPY) and PromptPay (THB) refunds.
     */
    private void processUpiRefund(Transaction transaction, Refund refund, Locale userLocale) {
        String chargeId = transaction.getOmiseChargeId();
        if (chargeId == null || chargeId.isBlank()) {
            log.error("Cannot process UPI refund: omiseChargeId is null for transaction {}", transaction.getId());
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    messageUtil.getMessage("refund.upi.missing.charge.id", userLocale));
        }

        BigDecimal refundAmount = refund.getTotalRefundAmount();
        String orderIdForMetadata = transaction.getOrder() != null ? transaction.getOrder().getId().toString() : null;
        
        // OmiseServiceImpl will determine the currency from the charge and convert the amount appropriately:
        // - For PayPay (JPY): amount is used as-is (already in smallest unit)
        // - For PromptPay (THB): amount is converted to satang (multiply by 100)
        log.info("Initiating Omise refund for charge {} with amount {} (OmiseServiceImpl will handle currency conversion)", 
                chargeId, refundAmount);

        try {
            // Use restaurant-specific or chain-level Omise credentials based on restaurant/payment configuration
            java.util.UUID restaurantId = transaction.getRestaurant().getId();
            var refundResponse = omiseService.createRefund(restaurantId, chargeId, refundAmount, orderIdForMetadata);
            String omiseStatus = refundResponse.has("status") ? refundResponse.get("status").asText() : "UNKNOWN";
            log.info("Omise refund response for charge {}: status={}, id={}",
                    chargeId, omiseStatus,
                    refundResponse.has("id") ? refundResponse.get("id").asText() : "UNKNOWN");
            if (!"closed".equalsIgnoreCase(omiseStatus) && !"successful".equalsIgnoreCase(omiseStatus)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        messageUtil.getMessage("refund.upi.omise.failed", userLocale) + ": " + omiseStatus);
            }
        } catch (ResponseStatusException e) {
            throw e;
        } catch (Exception e) {
            log.error("Error while calling Omise refund API for transaction {}", transaction.getId(), e);
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    messageUtil.getMessage("refund.upi.omise.error", userLocale) + ": " + e.getMessage());
        }
    }

    /**
     * Generate a 20-digit numeric refund ID for GMO (refund_id).
     */
    private String generateGmoRefundId() {
        // Use a single Random instance and the original value (avoid Math.abs bias/overflow).
        String base = String.valueOf(System.currentTimeMillis()) + String.valueOf(GMO_REFUND_ID_RANDOM.nextInt());
        String numeric = base.replaceAll("\\D", "");
        if (numeric.length() < 20) {
            numeric = String.format("%-20s", numeric).replace(' ', '0');
        }
        return numeric.substring(0, 20);
    }

    /**
     * Create a SALE_REFUND cash drawer log entry for CASH refunds.
     * @return the ID of the created log entry, or null if not applicable or failed.
     */
    private UUID createCashDrawerRefundLog(Refund refund, User cashier,
                                            BigDecimal refundOffered, BigDecimal changeCollected) {
        if (!"CASH".equals(refund.getRefundMethod())) {
            return null;
        }
        try {
            Optional<CashierShift> activeShiftOpt = cashierShiftRepository.findActiveShiftByCashierId(cashier.getId());
            if (activeShiftOpt.isEmpty()) {
                log.warn("No active shift found for cashier: {}. SALE_REFUND log not created for refund: {}",
                        cashier.getId(), refund.getRefundNumber());
                return null;
            }
            CashierShift activeShift = activeShiftOpt.get();
            BigDecimal netRefundAmount = refund.getTotalRefundAmount().negate();
            BigDecimal refundTotal = refund.getTotalRefundAmount() != null ? refund.getTotalRefundAmount() : BigDecimal.ZERO;
            BigDecimal safeRefundOffered = refundOffered != null ? refundOffered : refundTotal;
            BigDecimal safeChangeCollected = changeCollected != null ? changeCollected : BigDecimal.ZERO;

            String notes = String.format("Cash refund: %s (Refund amount: %s, Offered: %s, Change collected: %s)",
                    refund.getRefundNumber(), refund.getTotalRefundAmount(), refundOffered, changeCollected);

            CashDrawerLog refundLog = CashDrawerLog.builder()
                    .shift(activeShift)
                    .drawer(activeShift.getCashDrawer())
                    .user(cashier)
                    .eventType(DrawerEventType.SALE_REFUND)
                    .amount(netRefundAmount)
                    .expectedAmount(netRefundAmount)
                    // Physical cash movement:
                    // grossOut = cash paid to customer, grossIn = change collected back.
                    .grossOut(safeRefundOffered)
                    .grossIn(safeChangeCollected)
                    .refund(refund)
                    .notes(notes)
                    .createdBy(cashier)
                    .build();
            refundLog = cashDrawerLogRepository.save(refundLog);
            log.info("Created SALE_REFUND cash drawer log for refund: {} with net amount: {} (Refund: {}, Offered: {}, Change: {})",
                    refund.getRefundNumber(), netRefundAmount,
                    refund.getTotalRefundAmount(), refundOffered, changeCollected);
            return refundLog.getId();
        } catch (Exception e) {
            log.error("Failed to create SALE_REFUND cash drawer log for refund: {}. Error: {}",
                    refund.getRefundNumber(), e.getMessage(), e);
            return null;
        }
    }

    /**
     * Generate refund receipt PDF, save it, and build the receipt response DTO.
     */
    private RefundCompletionResponse.RefundReceiptResponse generateReceiptAndBuildResponse(
            Refund refund, UUID refundId) {
        if (refund.getReceiptUrl() != null && !refund.getReceiptUrl().trim().isEmpty()) {
            String finalReceiptUrl = generatePresignedUrl(refund.getReceiptUrl());
            log.info("Receipt response built with URL for refund: {}", refundId);
            return RefundCompletionResponse.RefundReceiptResponse.builder()
                    .receiptUrl(finalReceiptUrl)
                    .receiptNumber("REF-RECEIPT-" + refund.getRefundNumber())
                    .build();
        }
        log.info("Refund receipt is not available yet for refund: {}. Async generation has been scheduled.", refundId);
        return null;
    }

    private void scheduleRefundReceiptGenerationAfterCommit(UUID refundId) {
        if (refundId == null) {
            return;
        }
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    receiptGenerationAsyncService.generateRefundReceiptAfterCompletion(refundId);
                }
            });
            return;
        }
        receiptGenerationAsyncService.generateRefundReceiptAfterCompletion(refundId);
    }

    /**
     * Generate a presigned URL for a given S3 receipt URL, with fallback.
     */
    private String generatePresignedUrl(String receiptUrl) {
        try {
            String presignedUrl = awsService.getPreSignedUrlForPdf(receiptUrl);
            log.info("Generated presigned URL for refund receipt in completion response");
            return presignedUrl;
        } catch (Exception e) {
            log.warn("Failed to generate presigned URL for receipt, using stored URL. Error: {}", e.getMessage(), e);
            return receiptUrl;
        }
    }

    /**
     * Create audit trail entry for a completed refund.
     */
    private record RefundCompletionAuditTrailContext(
            User cashier,
            Restaurant restaurant,
            Transaction transaction,
            Refund refund,
            BigDecimal refundOffered,
            BigDecimal changeCollected,
            BigDecimal discrepancyAmount,
            String discrepancyReason
    ) {}

    private void createRefundCompletionAuditTrail(RefundCompletionAuditTrailContext ctx) {
        try {
            auditTrailService.createAuditTrail(
                    ctx.cashier(), ActionType.REFUND, ctx.restaurant(), RequestStatus.APPROVED,
                    null, null,
                    ctx.transaction().getId(), "TRANSACTION",
                    String.format("Refund completed: Refund Number %s, Amount %s, Method %s, Offered %s, Change Collected %s",
                            ctx.refund().getRefundNumber(), ctx.refund().getTotalRefundAmount(),
                            ctx.refund().getRefundMethod(), ctx.refundOffered(), ctx.changeCollected()),
                    null, null, null,
                    ctx.discrepancyAmount(), ctx.discrepancyReason(), ctx.cashier());
        } catch (Exception e) {
            log.error("Failed to create audit trail for refund completion: {}", e.getMessage(), e);
        }
    }

    /**
     * Build the RefundCompletionResponse DTO.
     */
    private record RefundCompletionResponseContext(
            Refund refund,
            CompleteRefundRequest request,
            User cashier,
            OffsetDateTime now,
            BigDecimal expectedChange,
            BigDecimal changeCollected,
            BigDecimal discrepancyAmount,
            String discrepancyReason,
            RefundCompletionResponse.RefundReceiptResponse receiptResponse,
            UUID cashDrawerLogId
    ) {}

    private RefundCompletionResponse buildRefundCompletionResponse(RefundCompletionResponseContext ctx) {

        String currency = restaurantChainConfigProperties.getChain() != null
                ? restaurantChainConfigProperties.getChain().getCurrency() : null;

        return RefundCompletionResponse.builder()
                .refundId(ctx.refund().getId())
                .refundNumber(ctx.refund().getRefundNumber())
                .refundAmount(ctx.refund().getTotalRefundAmount() != null ? CurrencyFormatter.formatAmount(ctx.refund().getTotalRefundAmount(), currency) : null)
                .refundOffered(ctx.request().getRefundOffered() != null ? CurrencyFormatter.formatAmount(ctx.request().getRefundOffered(), currency) : null)
                .changeExpected(ctx.expectedChange() != null ? CurrencyFormatter.formatAmount(ctx.expectedChange(), currency) : null)
                .changeCollected(ctx.changeCollected() != null ? CurrencyFormatter.formatAmount(ctx.changeCollected(), currency) : null)
                .discrepancyAmount(ctx.discrepancyAmount() != null ? CurrencyFormatter.formatAmount(ctx.discrepancyAmount(), currency) : null)
                .discrepancyReason(ctx.discrepancyReason())
                .completedAt(ctx.now().toLocalDateTime())
                .completedBy(ctx.cashier().getId())
                .completedByName(ctx.cashier().getFirstName() + " " + ctx.cashier().getLastName())
                .receipt(ctx.receiptResponse())
                .cashDrawerLogId(ctx.cashDrawerLogId())
                .build();
    }

    /**
     * Validate that there are no duplicate orderedItemIds in the request.
     */
    private void validateNoDuplicateOrderedItemIds(List<RefundCalculateRequest.ItemRefundCalculate> orderedItems,
                                                     Locale userLocale) {
        if (orderedItems == null || orderedItems.size() <= 1) {
            return;
        }
        java.util.Set<UUID> seenItemIds = new java.util.HashSet<>();
        for (RefundCalculateRequest.ItemRefundCalculate itemRequest : orderedItems) {
            if (itemRequest.getOrderedItemId() != null && !seenItemIds.add(itemRequest.getOrderedItemId())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        messageUtil.getMessage("refund.calculate.duplicate.ordered.item.id",
                                userLocale, itemRequest.getOrderedItemId()));
            }
        }
    }

    /**
     * Validate that there are no duplicate orderedComboIds in the request.
     */
    private void validateNoDuplicateOrderedComboIds(List<RefundCalculateRequest.ComboRefundCalculate> orderedCombos,
                                                      Locale userLocale) {
        if (orderedCombos == null || orderedCombos.size() <= 1) {
            return;
        }
        java.util.Set<UUID> seenComboIds = new java.util.HashSet<>();
        for (RefundCalculateRequest.ComboRefundCalculate comboRequest : orderedCombos) {
            if (comboRequest.getOrderedComboId() != null && !seenComboIds.add(comboRequest.getOrderedComboId())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        messageUtil.getMessage("refund.calculate.duplicate.ordered.combo.id",
                                userLocale, comboRequest.getOrderedComboId()));
            }
        }
    }

    // NOTE: calculateItemsSubtotal / calculateCombosSubtotal replaced by calculateRefundSubtotalBreakdown

    /**
     * Resolve restaurant name from translations based on user locale.
     */
    private String resolveRestaurantName(Transaction transaction, Locale userLocale) {
        if (transaction == null || transaction.getRestaurant() == null) {
            return null;
        }
        Restaurant restaurant = transaction.getRestaurant();
        if (restaurant.getTranslations() == null || restaurant.getTranslations().isEmpty()) {
            return messageUtil.getMessage("refund.restaurant.default", userLocale);
        }
        String userLanguage = userLocale.getLanguage();
        return restaurant.getTranslations().stream()
                .filter(t -> t.getLanguageCode() != null && t.getLanguageCode().startsWith(userLanguage))
                .findFirst()
                .map(com.gulfnet.shared_library.entity.RestaurantTranslation::getName)
                .orElse(restaurant.getTranslations().get(0).getName());
    }

    /**
     * Build list of RefundItemResponse DTOs from persisted RefundItems.
     */
    private List<RefundRequestResponse.RefundItemResponse> buildRefundItemResponses(UUID refundId, Locale userLocale) {
        List<RefundItem> refundItems = refundItemRepository.findByRefund_Id(refundId);
        List<RefundRequestResponse.RefundItemResponse> responses = new ArrayList<>();
        String userLanguage = userLocale.getLanguage();

        for (RefundItem refundItem : refundItems) {
            String itemName = "Item";
            String itemType = "ITEM";

            if (refundItem.getOrderedItem() != null) {
                OrderedItem orderedItem = refundItem.getOrderedItem();
                if (orderedItem.getItem() != null && orderedItem.getItem().getTranslations() != null) {
                    itemName = orderedItem.getItem().getTranslations().stream()
                            .filter(t -> t.getLanguageCode() != null && t.getLanguageCode().equals(userLanguage))
                            .map(com.gulfnet.shared_library.entity.ItemTranslation::getName)
                            .findFirst()
                            .orElse(orderedItem.getItem().getTranslations().get(0).getName());
                }
            } else if (refundItem.getOrderedCombo() != null) {
                OrderedCombo orderedCombo = refundItem.getOrderedCombo();
                if (orderedCombo.getCombo() != null && orderedCombo.getCombo().getTranslations() != null) {
                    itemName = orderedCombo.getCombo().getTranslations().stream()
                            .filter(t -> t.getLanguageCode() != null && t.getLanguageCode().equals(userLanguage))
                            .map(com.gulfnet.shared_library.entity.ComboTranslation::getName)
                            .findFirst()
                            .orElse(orderedCombo.getCombo().getTranslations().get(0).getName());
                }
                itemType = "COMBO";
            }

            UUID itemId;
            if (refundItem.getOrderedItem() != null) {
                itemId = refundItem.getOrderedItem().getId();
            } else {
                itemId = refundItem.getOrderedCombo() != null ? refundItem.getOrderedCombo().getId() : null;
            }

            responses.add(RefundRequestResponse.RefundItemResponse.builder()
                    .itemId(itemId)
                    .itemType(itemType)
                    .itemName(itemName)
                    .quantity(refundItem.getQuantity())
                    .refundAmount(refundItem.getRefundAmount())
                    .build());
        }
        return responses;
    }

    /**
     * Resolve the role name for the transaction's requestedBy user.
     */
    private String resolveRequestedByRole(Transaction transaction) {
        if (transaction == null || transaction.getRequestedBy() == null
                || transaction.getRequestedBy().getRoleId() == null) {
            return null;
        }
        return roleRepository.findById(transaction.getRequestedBy().getRoleId())
                .map(Role::getName)
                .orElse(null);
    }

    /**
     * Evaluates real-time HQ alerts after a refund completion commits.
     * Handles both active transaction (deferred via TransactionSynchronization) and
     * no-active-transaction (immediate) cases.
     *
     * @param restaurant the restaurant to evaluate alerts for (may be null)
     * @param userLocale the user's locale
     */
    private void evaluateAlertsAfterRefundCommit(Restaurant restaurant, Locale userLocale) {
        if (restaurant == null) {
            log.warn("Restaurant is null, skipping alert evaluation after refund completion.");
            return;
        }

        // Check if alert evaluation service is available (lazy injection)
        if (restaurantAlertEvaluationService == null) {
            log.warn("⚠️ RestaurantAlertEvaluationService is null (lazy injection not initialized), skipping alert evaluation after refund completion for restaurant: {}",
                    restaurant.getRestaurantCode());
            return;
        }

        final Restaurant finalRestaurant = restaurant;
        final Locale finalUserLocale = userLocale;

        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                /**
                 * Executes after the surrounding refund transaction successfully commits.
                 * <p>
                 * Runs real-time HQ/restaurant alert evaluation using the committed state to avoid alerting on a
                 * transaction that later rolls back. Errors are caught and logged to avoid impacting the caller flow.
                 * </p>
                 */
                @Override
                public void afterCommit() {
                    try {
                        log.info("🔔 Triggering alert evaluation for restaurant: {} after refund completion commit",
                                finalRestaurant.getRestaurantCode());
                        if (restaurantAlertEvaluationService == null) {
                            log.error("❌ RestaurantAlertEvaluationService is null in afterCommit callback - lazy injection failed");
                            return;
                        }
                        restaurantAlertEvaluationService.evaluateRestaurantAlertsRealtime(finalRestaurant, finalUserLocale);
                        log.info("✅ Alert evaluation completed for restaurant: {} after refund completion commit",
                                finalRestaurant.getRestaurantCode());
                    } catch (Exception e) {
                        log.error("❌ Failed to evaluate real-time alerts after refund completion commit: {}", e.getMessage(), e);
                    }
                }
            });
            log.info("📋 Registered alert evaluation to run after refund completion commit for restaurant: {}",
                    restaurant.getRestaurantCode());
        } else {
            try {
                log.info("🔔 Triggering alert evaluation for restaurant: {} after refund completion (no active transaction)",
                        restaurant.getRestaurantCode());
                if (restaurantAlertEvaluationService == null) {
                    log.error("❌ RestaurantAlertEvaluationService is null (no active transaction) - lazy injection failed");
                    return;
                }
                restaurantAlertEvaluationService.evaluateRestaurantAlertsRealtime(restaurant, userLocale);
                log.info("✅ Alert evaluation completed for restaurant: {} after refund completion (no active transaction)",
                        restaurant.getRestaurantCode());
            } catch (Exception e) {
                log.error("❌ Failed to evaluate real-time alerts after refund completion (no active transaction): {}",
                        e.getMessage(), e);
            }
        }
    }
}

