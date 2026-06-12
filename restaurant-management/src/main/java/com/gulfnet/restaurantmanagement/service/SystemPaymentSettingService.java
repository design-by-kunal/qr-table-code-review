package com.gulfnet.restaurantmanagement.service;

import com.gulfnet.shared_library.model.request.SystemPaymentSettingRequest;
import com.gulfnet.shared_library.model.response.dto.ResponseDto;
import com.gulfnet.shared_library.model.response.dto.SystemPaymentSettingResponse;

public interface SystemPaymentSettingService {
    
    ResponseDto<SystemPaymentSettingResponse> updateSystemPaymentSetting(SystemPaymentSettingRequest request, String userId, String locale);
}
