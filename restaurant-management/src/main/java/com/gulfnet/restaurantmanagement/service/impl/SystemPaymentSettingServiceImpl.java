package com.gulfnet.restaurantmanagement.service.impl;

import com.gulfnet.restaurantmanagement.service.SystemPaymentSettingService;
import com.gulfnet.restaurantmanagement.util.MessageUtil;
import com.gulfnet.shared_library.entity.SystemPaymentSetting;
import com.gulfnet.shared_library.model.request.SystemPaymentSettingRequest;
import com.gulfnet.shared_library.model.response.dto.ResponseDto;
import com.gulfnet.shared_library.model.response.dto.SystemPaymentSettingResponse;
import com.gulfnet.shared_library.repository.SystemPaymentSettingRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class SystemPaymentSettingServiceImpl implements SystemPaymentSettingService {

    private final SystemPaymentSettingRepository systemPaymentSettingRepository;
    private final MessageUtil messageUtil;

    /**
     * Updates system payment setting for a payment gateway.
     * Creates a new setting if it doesn't exist. If enabling a gateway, disables all other gateways
     * to ensure only one payment gateway is active at a time.
     *
     * @param request the payment setting request with gateway code and enabled status
     * @param userId  the ID of the user performing the update
     * @param locale  locale code for localized success message
     * @return ResponseDto containing the updated payment setting response
     */
    @Override
    @Transactional
    public ResponseDto<SystemPaymentSettingResponse> updateSystemPaymentSetting(
            SystemPaymentSettingRequest request, String userId, String locale) {
        Locale userLocale = Locale.forLanguageTag(locale);
        UUID userIdUuid = UUID.fromString(userId);

        SystemPaymentSetting setting = systemPaymentSettingRepository.findByGatewayCode(request.getGatewayCode())
                .orElseGet(() -> {
                    // Create new setting if it doesn't exist
                    SystemPaymentSetting newSetting = new SystemPaymentSetting();
                    newSetting.setGatewayCode(request.getGatewayCode());
                    newSetting.setIsEnabled(false); // Default to disabled
                    newSetting.setCreatedBy(userIdUuid);
                    newSetting.setUpdatedBy(userIdUuid);
                    log.info("Creating new system payment setting for gateway code: {}", request.getGatewayCode());
                    return newSetting;
                });

        // If enabling this gateway, disable all other gateways
        if (request.getIsEnabled() != null && request.getIsEnabled()) {
            java.util.List<SystemPaymentSetting> allSettings = systemPaymentSettingRepository.findAll();
            for (SystemPaymentSetting otherSetting : allSettings) {
                if (!otherSetting.getGatewayCode().equals(request.getGatewayCode()) && 
                    Boolean.TRUE.equals(otherSetting.getIsEnabled())) {
                    otherSetting.setIsEnabled(false);
                    otherSetting.setUpdatedBy(userIdUuid);
                    systemPaymentSettingRepository.save(otherSetting);
                    log.info("Disabled payment gateway: {}", otherSetting.getGatewayCode());
                }
            }
        }

        if (request.getIsEnabled() != null) {
            setting.setIsEnabled(request.getIsEnabled());
        }
        setting.setUpdatedBy(userIdUuid);

        setting = systemPaymentSettingRepository.save(setting);
        log.info("Saved system payment setting with gateway code: {}", setting.getGatewayCode());

        SystemPaymentSettingResponse response = mapToResponse(setting);
        return ResponseDto.<SystemPaymentSettingResponse>builder()
                .data(response)
                .message(messageUtil.getMessage("system.payment.setting.updated.successfully", userLocale))
                .build();
    }

    private SystemPaymentSettingResponse mapToResponse(SystemPaymentSetting setting) {
        return SystemPaymentSettingResponse.builder()
                .id(setting.getId())
                .gatewayCode(setting.getGatewayCode())
                .isEnabled(setting.getIsEnabled())
                .createdAt(setting.getCreatedAt() != null ? setting.getCreatedAt().toLocalDateTime() : null)
                .updatedAt(setting.getUpdatedAt() != null ? setting.getUpdatedAt().toLocalDateTime() : null)
                .createdBy(setting.getCreatedBy())
                .updatedBy(setting.getUpdatedBy())
                .build();
    }
}
