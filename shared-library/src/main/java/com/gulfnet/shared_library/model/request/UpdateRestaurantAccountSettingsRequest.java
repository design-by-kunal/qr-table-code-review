package com.gulfnet.shared_library.model.request;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateRestaurantAccountSettingsRequest {
    private OffsetTime kdsLiveDashboardResetTime; // UTC time (e.g., "03:00:00+00:00" or "03:00+00")
    private OffsetTime cashierLiveDashboardResetTime; // UTC time (e.g., "03:00:00+00:00" or "03:00+00")
}

