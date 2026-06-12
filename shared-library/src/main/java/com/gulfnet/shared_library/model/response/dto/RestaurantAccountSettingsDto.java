package com.gulfnet.shared_library.model.response.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RestaurantAccountSettingsDto {
    private OffsetTime kdsLiveDashboardResetTime;
    private OffsetTime cashierLiveDashboardResetTime;
}

