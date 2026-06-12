package com.gulfnet.shared_library.model.response.dto;

import com.gulfnet.shared_library.enums.AppType;
import com.gulfnet.shared_library.enums.DeviceType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DeviceTokenResponse {
    private String deviceToken;
    private DeviceType deviceType;
    private AppType appType;
    private Boolean isUpdated;
}
