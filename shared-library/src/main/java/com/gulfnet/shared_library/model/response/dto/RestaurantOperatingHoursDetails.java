package com.gulfnet.shared_library.model.response.dto;

import com.gulfnet.shared_library.enums.DayOfWeek;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;
import java.util.UUID;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class RestaurantOperatingHoursDetails {
    private UUID restaurantId;
    private Map<DayOfWeek, OperatingHourDto> operatingHours;
}