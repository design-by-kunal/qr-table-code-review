package com.gulfnet.restaurantmanagement.controller;

import com.gulfnet.restaurantmanagement.service.SystemPaymentSettingService;
import com.gulfnet.shared_library.model.request.SystemPaymentSettingRequest;
import com.gulfnet.shared_library.model.response.dto.ResponseDto;
import com.gulfnet.shared_library.model.response.dto.SystemPaymentSettingResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/api/v1/system-payment-settings")
@RequiredArgsConstructor
public class SystemPaymentSettingController {

    private final SystemPaymentSettingService systemPaymentSettingService;

    /**
     * Updates the system payment setting for a specific payment gateway.
     * Enables or disables a payment gateway for the entire system.
     *
     * @param request the payment setting request containing gateway code and enabled status
     * @param userId  the user ID from the request header (required)
     * @param locale  locale code for localized responses (default: "en")
     * @return response containing the updated payment setting details
     */
    @PutMapping
    public ResponseEntity<ResponseDto<SystemPaymentSettingResponse>> updateSystemPaymentSetting(
            @Valid @RequestBody SystemPaymentSettingRequest request,
            @RequestHeader("User-ID") String userId,
            @RequestHeader(value = "locale", defaultValue = "en") String locale) {
        
        log.info("Received update system payment setting request for gateway: {} from user: {} with locale: {}", 
                request.getGatewayCode(), userId, locale);
        ResponseDto<SystemPaymentSettingResponse> response = 
                systemPaymentSettingService.updateSystemPaymentSetting(request, userId, locale);
        return ResponseEntity.ok(response);
    }
}
