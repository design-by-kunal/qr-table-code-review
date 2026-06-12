package com.gulfnet.shared_library.model.request;

import com.gulfnet.shared_library.enums.AppType;
import com.gulfnet.shared_library.enums.DeviceType;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class UpdateDeviceTokenRequest {

    @NotBlank(message = "{validation.device.token.blank}")
    private String deviceToken;

    private DeviceType deviceType; // ANDROID, IOS, WEB

    private AppType appType; // HQADMIN, CASHIER, WAITER, MANAGER, KDS
}
