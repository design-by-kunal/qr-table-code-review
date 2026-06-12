package com.gulfnet.restaurantmanagement.controller;

import com.gulfnet.restaurantmanagement.service.OmiseScannableQrStorageService;
import com.gulfnet.restaurantmanagement.service.TransactionService;
import com.gulfnet.shared_library.enums.ReportType;
import com.gulfnet.shared_library.enums.TransactionStatus;
import com.gulfnet.shared_library.model.request.RaiseRefundRequest;
import com.gulfnet.shared_library.model.request.TransactionStatusPayload;
import com.gulfnet.shared_library.model.response.dto.RaiseRefundRequestResponse;
import com.gulfnet.shared_library.model.response.dto.ResponseDto;
import com.gulfnet.shared_library.model.response.dto.TransactionCancellationRequestResponse;
import com.gulfnet.shared_library.model.response.dto.TransactionListDto;
import com.gulfnet.shared_library.model.response.dto.DiscountOfferReportListDto;
import com.gulfnet.shared_library.service.export.ReportExportService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/v1/transactions")
@RequiredArgsConstructor
public class TransactionController {

    private static final String FILTER_KEY_TRANSACTION_STATUSES = "transactionStatuses";
    
    private final TransactionService transactionService;
    private final ReportExportService reportExportService;
    private final OmiseScannableQrStorageService omiseScannableQrStorageService;

    /**
     * Retrieves a paginated and filterable list of transactions for a specific restaurant.
     * Supports filtering by order status, order type, transaction status, payment method,
     * date range, and text search. Results are localized based on the locale header.
     *
     * @param restaurantId      the UUID of the restaurant to get transactions for
     * @param page             optional page number for pagination
     * @param size             optional page size for pagination
     * @param orderStatus      optional filter by order status
     * @param orderType        optional filter by order type
     * @param transactionStatus optional filter by transaction status
     * @param paymentMethod    optional filter by payment method
     * @param sortBy           optional field to sort by
     * @param direction        optional sort direction (ASC, DESC)
     * @param search           optional search term for text search
     * @param date             optional filter by specific date (ISO date format)
     * @param startDate        optional filter by start date and time (ISO date-time format)
     * @param endDate          optional filter by end date and time (ISO date-time format)
     * @param locale           locale code for localized responses (default: "en")
     * @return response containing paginated list of transactions with filters applied
     */
    @GetMapping("/restaurant/{restaurantId}")
    public ResponseEntity<ResponseDto<TransactionListDto>> getTransactionsByRestaurant(
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
            @RequestParam(value = "date", required = false) 
                @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(value = "startDate", required = false) 
                @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startDate,
            @RequestParam(value = "endDate", required = false) 
                @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endDate,
            @RequestHeader(value = "locale", defaultValue = "en") String locale) {
        
        log.info("Request received to get transactions for restaurant: {} (page: {}, size: {}, orderStatus: {}, orderType: {}, " +
                "transactionStatus: {}, paymentMethod: {}, sortBy: {}, direction: {}, search: {}, date: {}, startDate: {}, endDate: {}) with locale: {}", 
                restaurantId, page, size, orderStatus, orderType, transactionStatus, paymentMethod, sortBy, direction, search, date, startDate, endDate, locale);
        
        ResponseDto<TransactionListDto> response = transactionService.getTransactionsByRestaurant(
                restaurantId, page, size, orderStatus, orderType, transactionStatus, paymentMethod, sortBy, direction, search, date, startDate, endDate, locale);
        
        log.info("Successfully retrieved {} transactions for restaurant: {} (total: {})", 
                response.getData().getCount(), restaurantId, response.getData().getTotal());
        return ResponseEntity.ok(response);
    }

    /**
     * Cancels a transaction by updating its status.
     * May create a cancellation request that requires manager approval depending on business rules.
     *
     * @param transactionId the UUID of the transaction to cancel
     * @param payload      the transaction status payload containing cancellation details
     * @param userId       the user ID from the request header (required)
     * @return response containing cancellation request status and transaction details
     */
    @PutMapping("/{transactionId}/status")
    public ResponseEntity<ResponseDto<TransactionCancellationRequestResponse>> cancelTransaction(
            @PathVariable UUID transactionId,
            @Valid @RequestBody TransactionStatusPayload payload,
            @RequestHeader("User-ID") String userId) {
        
        log.info("Request received to update transaction status: {} by user: {}", transactionId, userId);
        
        ResponseDto<TransactionCancellationRequestResponse> response = transactionService.cancelTransaction(
                userId, transactionId, payload);
        
        log.info("Successfully processed transaction status update request for transaction: {}", transactionId);
        return ResponseEntity.ok(response);
    }

    /**
     * Initiates a refund request for a transaction.
     * Creates a refund request that can be processed by cashiers or managers.
     *
     * @param transactionId the UUID of the transaction to initiate refund for
     * @param request      the refund request containing items/combos to refund and refund reason
     * @param userId       the user ID from the request header (required)
     * @param userRole     the user role from the request header (required)
     * @return response containing the created refund request details
     */
    /**
     * Serves cached PromptPay SVG QR (short {@code qrCode} URL for PromptPay only).
     */
    @GetMapping("/{transactionId}/omise-qr")
    public ResponseEntity<byte[]> getOmiseQrImage(@PathVariable UUID transactionId) {
        return omiseScannableQrStorageService.readCachedQr(transactionId)
                .map(cached -> ResponseEntity.ok()
                        .contentType(cached.mediaType())
                        .header(HttpHeaders.CACHE_CONTROL, "no-store")
                        .body(cached.bytes()))
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/{transactionId}/refund")
    public ResponseEntity<ResponseDto<RaiseRefundRequestResponse>> initiateRefund(
            @PathVariable UUID transactionId,
            @Valid @RequestBody RaiseRefundRequest request,
            @RequestHeader("User-ID") String userId,
            @RequestHeader("User-Role") String userRole) {
        
        log.info("Request received to initiate refund for transaction: {} by user: {} with role: {}", transactionId, userId, userRole);
        
        ResponseDto<RaiseRefundRequestResponse> response = transactionService.initiateRefund(
                userId, userRole, transactionId, request);
        
        log.info("Successfully initiated refund request for transaction: {}", transactionId);
        return ResponseEntity.ok(response);
    }

    /**
     * Retrieves or exports a report of discounts and offers applied to transactions for a restaurant.
     * Supports filtering by transaction status and date range.
     * Can return data in JSON format (default) or export to CSV format.
     *
     * @param restaurantId      the UUID of the restaurant to get the report for
     * @param page              optional page number for pagination (JSON format only)
     * @param size              optional page size for pagination (JSON format only)
     * @param transactionStatus optional filter by transaction status
     * @param date              optional filter by specific date (ISO date format)
     * @param startDate         optional filter by start date and time (ISO date-time format)
     * @param endDate           optional filter by end date and time (ISO date-time format)
     * @param format            output format: "json" (default) or "csv"
     * @param locale            locale code for localized responses (default: "en")
     * @param httpResponse      HTTP servlet response for CSV export (required for CSV format)
     * @return response containing paginated report data (JSON format) or null (CSV format - response written to HttpServletResponse)
     * @throws IOException if an I/O error occurs during CSV export
     */
    @GetMapping("/restaurant/{restaurantId}/discounts-offers-report")
    public ResponseEntity<?> getDiscountsAndOffersAppliedReport(
            @PathVariable UUID restaurantId,
            @RequestParam(value = "page", required = false) Integer page,
            @RequestParam(value = "size", required = false) Integer size,
            @RequestParam(value = "transactionStatus", required = false) String transactionStatus,
            @RequestParam(value = "date", required = false) 
                @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(value = "startDate", required = false) 
                @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startDate,
            @RequestParam(value = "endDate", required = false) 
                @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endDate,
            @RequestParam(value = "format", required = false, defaultValue = "json") String format,
            @RequestHeader(value = "locale", defaultValue = "en") String locale,
            HttpServletResponse httpResponse) throws IOException {
        
        log.info("Request received to get discounts and offers applied report for restaurant: {} (page: {}, size: {}, " +
                "transactionStatus: {}, date: {}, startDate: {}, endDate: {}, format: {}) with locale: {}", 
                restaurantId, page, size, transactionStatus, date, startDate, endDate, format, locale);
        
        // If format is CSV, export to CSV using shared library
        if ("csv".equalsIgnoreCase(format)) {
            // Prepare filters for ReportExportService
            java.util.Map<String, Object> filters = new java.util.HashMap<>();
            filters.put("restaurantId", restaurantId);
            
            // Convert transactionStatus string to Collection<TransactionStatus>
            if (transactionStatus != null && !transactionStatus.trim().isEmpty()) {
                try {
                    TransactionStatus status = TransactionStatus.valueOf(transactionStatus.toUpperCase());
                    filters.put(FILTER_KEY_TRANSACTION_STATUSES, java.util.Collections.singletonList(status));
                } catch (IllegalArgumentException e) {
                    log.warn("Invalid transaction status: {}, using all statuses", transactionStatus);
                    filters.put(FILTER_KEY_TRANSACTION_STATUSES, java.util.Arrays.asList(TransactionStatus.values()));
                }
            } else {
                filters.put(FILTER_KEY_TRANSACTION_STATUSES, java.util.Arrays.asList(TransactionStatus.values()));
            }
            
            // Handle date filters
            if (date != null) {
                LocalDateTime startOfDay = date.atStartOfDay();
                LocalDateTime endOfDay = date.atTime(23, 59, 59, 999999999);
                filters.put("startDateTime", startOfDay);
                filters.put("endDateTime", endOfDay);
            } else {
                if (startDate != null) {
                    filters.put("startDateTime", startDate);
                }
                if (endDate != null) {
                    filters.put("endDateTime", endDate);
                }
            }
            
            // Use shared library ReportExportService
            reportExportService.exportReport(ReportType.DISCOUNTS_OFFERS, filters, locale, httpResponse);
            log.info("Successfully exported discounts and offers applied report to CSV for restaurant: {}", restaurantId);
            // Return null - response is already written to HttpServletResponse
            return null;
        }
        
        // Default: return JSON response
        ResponseDto<DiscountOfferReportListDto> response = transactionService.getDiscountsAndOffersAppliedReport(
                restaurantId, page, size, transactionStatus, date, startDate, endDate, locale);
        
        log.info("Successfully retrieved discounts and offers applied report for restaurant: {} (total: {})", 
                restaurantId, response.getData().getTotal());
        return ResponseEntity.ok(response);
    }
}

