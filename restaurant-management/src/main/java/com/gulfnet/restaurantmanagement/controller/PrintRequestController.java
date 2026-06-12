package com.gulfnet.restaurantmanagement.controller;

import com.gulfnet.restaurantmanagement.service.PrintRequestService;
import com.gulfnet.shared_library.model.request.PrintRequestCreateRequest;
import com.gulfnet.shared_library.model.request.PrintRequestDecisionRequest;
import com.gulfnet.shared_library.model.response.dto.PrintRequestListResponse;
import com.gulfnet.shared_library.model.response.dto.PrintRequestResponse;
import com.gulfnet.shared_library.model.response.dto.ResponseDto;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/print-request")
@Slf4j
public class PrintRequestController {

    @Autowired
    private PrintRequestService printRequestService;

    /**
     * Create a new print request from Manager/Waiter using an existing file URL (S3).
     */
    @PostMapping
    public ResponseEntity<ResponseDto<PrintRequestResponse>> createPrintRequest(
            @Valid @RequestBody PrintRequestCreateRequest request,
            @RequestHeader("User-ID") String userId,
            @RequestHeader("User-Role") String userRole,
            @RequestHeader(value = "locale", defaultValue = "en") String locale) {

        log.info("Received request to create print request by user: {}, role: {}", userId, userRole);
        ResponseDto<PrintRequestResponse> response = printRequestService.createPrintRequest(request, userId, userRole, locale);
        return ResponseEntity.status(HttpStatus.OK).body(response);
    }

    /**
     * Cashier: get pending print requests for their restaurant.
     */
    @GetMapping("/cashier/pending")
    public ResponseEntity<ResponseDto<PrintRequestListResponse>> getPendingPrintRequestsForCashier(
            @RequestParam(value = "page", required = false) Integer page,
            @RequestParam(value = "size", required = false) Integer size,
            @RequestHeader("User-ID") String cashierId,
            @RequestHeader("User-Role") String cashierRole,
            @RequestHeader(value = "locale", defaultValue = "en") String locale) {

        log.info("Received request to get pending print requests for cashier: {}", cashierId);
        ResponseDto<PrintRequestListResponse> response =
                printRequestService.getPendingRequestsForCashier(page, size, cashierId, cashierRole, locale);
        return ResponseEntity.ok(response);
    }

    /**
     * Requester: get their own print requests.
     */
    @GetMapping("/requester")
    public ResponseEntity<ResponseDto<PrintRequestListResponse>> getRequesterPrintRequests(
            @RequestParam(value = "page", required = false) Integer page,
            @RequestParam(value = "size", required = false) Integer size,
            @RequestHeader("User-ID") String userId,
            @RequestHeader(value = "locale", defaultValue = "en") String locale) {

        log.info("Received request to get print requests for requester: {}", userId);
        ResponseDto<PrintRequestListResponse> response =
                printRequestService.getRequestsForRequester(page, size, userId, locale);
        return ResponseEntity.ok(response);
    }

    /**
     * Cashier approves and (logically) prints the request.
     * Frontend uses fileUrl from response to actually open/print from S3.
     */
    @PutMapping("/{requestId}/approve")
    public ResponseEntity<ResponseDto<PrintRequestResponse>> approvePrintRequest(
            @PathVariable UUID requestId,
            @Valid @RequestBody(required = false) PrintRequestDecisionRequest decision,
            @RequestHeader("User-ID") String cashierId,
            @RequestHeader("User-Role") String cashierRole,
            @RequestHeader(value = "locale", defaultValue = "en") String locale) {

        log.info("Received request to approve print request {} by cashier {}", requestId, cashierId);
        ResponseDto<PrintRequestResponse> response =
                printRequestService.approvePrintRequest(requestId, decision, cashierId, cashierRole, locale);
        return ResponseEntity.ok(response);
    }

    /**
     * Cashier declines the print request.
     * Optional comments can be provided explaining the reason for decline.
     */
    @PutMapping("/{requestId}/decline")
    public ResponseEntity<ResponseDto<PrintRequestResponse>> declinePrintRequest(
            @PathVariable UUID requestId,
            @Valid @RequestBody(required = false) PrintRequestDecisionRequest decision,
            @RequestHeader("User-ID") String cashierId,
            @RequestHeader("User-Role") String cashierRole,
            @RequestHeader(value = "locale", defaultValue = "en") String locale) {

        log.info("Received request to decline print request {} by cashier {}", requestId, cashierId);
        ResponseDto<PrintRequestResponse> response =
                printRequestService.declinePrintRequest(requestId, decision, cashierId, cashierRole, locale);
        return ResponseEntity.ok(response);
    }
}


