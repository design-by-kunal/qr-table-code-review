package com.gulfnet.restaurantmanagement.controller;

import com.gulfnet.restaurantmanagement.service.RefundService;
import com.gulfnet.shared_library.model.request.CompleteRefundRequest;
import com.gulfnet.shared_library.model.request.RefundCalculateRequest;
import com.gulfnet.shared_library.model.response.dto.RefundCalculateResponse;
import com.gulfnet.shared_library.model.response.dto.RefundCompletionResponse;
import com.gulfnet.shared_library.model.response.dto.RefundDetailsResponse;
import com.gulfnet.shared_library.model.response.dto.RefundReceiptUrlResponse;
import com.gulfnet.shared_library.model.response.dto.RefundRequestResponse;
import com.gulfnet.shared_library.model.response.dto.ResponseDto;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/v1/refunds")
@RequiredArgsConstructor
public class RefundController {

    private final RefundService refundService;

    /**
     * Completes a refund by processing cash payment.
     * Validates the refund offered amount and generates a receipt.
     *
     * @param refundId the UUID of the refund to complete
     * @param request  the complete refund request containing cash payment details
     * @param userId   the user ID from the request header (required)
     * @param userRole the user role from the request header (required)
     * @return response containing refund completion details and receipt information
     */
    @PostMapping("/{refundId}/complete")
    @Operation(summary = "Complete refund", description = "Complete a refund by processing cash payment. Validates refund offered amount and generates receipt.")
    public ResponseEntity<ResponseDto<RefundCompletionResponse>> completeRefund(
            @PathVariable UUID refundId,
            @Valid @RequestBody CompleteRefundRequest request,
            @RequestHeader("User-ID") String userId,
            @RequestHeader("User-Role") String userRole) {
        
        log.info("Request received to complete refund: {} by user: {} with role: {}", refundId, userId, userRole);
        
        ResponseDto<RefundCompletionResponse> response = refundService.completeRefund(
                refundId, request, userId, userRole);
        
        log.info("Successfully completed refund: {}", refundId);
        return ResponseEntity.ok(response);
    }

    /**
     * Retrieves detailed information about a refund for the cashier completion screen.
     *
     * @param refundId the UUID of the refund to get details for
     * @param userId   the user ID from the request header (required)
     * @param userRole the user role from the request header (required; CASHIER or MANAGER)
     * @return response containing refund details including amounts, items, and status
     */
    @GetMapping("/{refundId}")
    @Operation(summary = "Get refund details", description = "Get refund details for cashier completion screen")
    public ResponseEntity<ResponseDto<RefundDetailsResponse>> getRefundDetails(
            @PathVariable UUID refundId,
            @RequestHeader("User-ID") String userId,
            @RequestHeader("User-Role") String userRole) {
        
        log.info("Request received to get refund details for refund: {} by user: {} with role: {}", refundId, userId, userRole);
        
        ResponseDto<RefundDetailsResponse> response = refundService.getRefundDetails(refundId, userId, userRole);
        
        log.info("Successfully retrieved refund details for refund: {}", refundId);
        return ResponseEntity.ok(response);
    }

    /**
     * Generates a pre-signed URL for accessing the refund receipt PDF from S3.
     *
     * @param refundId the UUID of the refund to get the receipt URL for
     * @param userId   the user ID from the request header (required)
     * @param userRole the user role from the request header (required; CASHIER or MANAGER)
     * @return response containing the pre-signed URL for the refund receipt PDF
     */
    @GetMapping("/{refundId}/receipt")
    @Operation(summary = "Get refund receipt presigned URL", description = "Get presigned URL for refund receipt PDF")
    public ResponseEntity<ResponseDto<RefundReceiptUrlResponse>> getRefundReceiptPresignedUrl(
            @PathVariable UUID refundId,
            @RequestHeader("User-ID") String userId,
            @RequestHeader("User-Role") String userRole) {
        
        log.info("Request received to get refund receipt presigned URL for refund: {} by user: {} with role: {}", refundId, userId, userRole);
        
        ResponseDto<RefundReceiptUrlResponse> response = refundService.getRefundReceiptPresignedUrl(refundId, userId, userRole);
        
        log.info("Successfully generated presigned URL for refund: {}", refundId);
        return ResponseEntity.ok(response);
    }

    /**
     * Calculates proportional refund amounts including tax, service charge, and packaging charges
     * based on the items and combos being refunded from a transaction.
     *
     * @param request  the refund calculation request containing transaction ID and items/combos to refund
     * @param userId   the user ID from the request header (required)
     * @param userRole the user role from the request header (required; CASHIER or MANAGER)
     * @return response containing calculated refund amounts breakdown
     */
    @PostMapping("/calculate")
    @Operation(summary = "Calculate refund amounts", description = "Calculate proportional tax, service charge, and packaging charges based on items and combos")
    public ResponseEntity<ResponseDto<RefundCalculateResponse>> calculateRefundAmounts(
            @Valid @RequestBody RefundCalculateRequest request,
            @RequestHeader("User-ID") String userId,
            @RequestHeader("User-Role") String userRole) {
        
        log.info("Request received to calculate refund amounts for transaction: {} by user: {} with role: {}", request.getTransactionId(), userId, userRole);
        
        ResponseDto<RefundCalculateResponse> response = refundService.calculateRefundAmounts(request, userId, userRole);
        
        log.info("Successfully calculated refund amounts for transaction: {}", request.getTransactionId());
        return ResponseEntity.ok(response);
    }

    /**
     * Retrieves refunds that were initiated directly by managers.
     * These are transactions with request_status=NONE but having refund records.
     * Returns the same format as refund request retrieval.
     *
     * @param restaurantId optional restaurant ID to filter refunds by
     * @param userId       the user ID from the request header (required)
     * @param userRole     the user role from the request header (required; CASHIER or MANAGER)
     * @return response containing list of manager-initiated refund requests
     */
    @GetMapping("/manager-initiated")
    @Operation(summary = "Get manager-initiated refunds", description = "Get refunds that were initiated directly by managers (transactions with request_status=NONE but having refund records). Returns same format as refund request retrieval.")
    public ResponseEntity<ResponseDto<java.util.List<RefundRequestResponse>>> getManagerInitiatedRefunds(
            @RequestParam(value = "restaurantId", required = false) String restaurantId,
            @RequestHeader("User-ID") String userId,
            @RequestHeader("User-Role") String userRole) {
        
        log.info("Request received to get manager-initiated refunds for restaurant: {} by user: {} with role: {}", restaurantId, userId, userRole);
        
        ResponseDto<java.util.List<RefundRequestResponse>> response = refundService.getManagerInitiatedRefunds(restaurantId, userId, userRole);
        
        log.info("Successfully retrieved manager-initiated refunds");
        return ResponseEntity.ok(response);
    }
}

