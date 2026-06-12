package com.gulfnet.restaurantmanagement.service;

import com.gulfnet.shared_library.model.response.dto.DashboardResponse;
import com.gulfnet.shared_library.model.response.dto.MenuDashboardResponse;
import com.gulfnet.shared_library.model.response.dto.ResponseDto;
import com.gulfnet.shared_library.model.response.dto.RestaurantDashboardResponse;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.UUID;

public interface DashboardService {
    ResponseDto<DashboardResponse> getDashboardStatistics(String period, LocalDateTime startDate, LocalDateTime endDate, UUID restaurantGroupId, UUID restaurantId, String salesStatsPeriod, String locale);
    
    ResponseDto<RestaurantDashboardResponse> getRestaurantDashboard(
            String dateRange,
            LocalDateTime startDate,
            LocalDateTime endDate,
            UUID restaurantGroupId,
            UUID restaurantId,
            Integer onShiftStaffPage,
            Integer onShiftStaffSize,
            Integer managersPage,
            Integer managersSize,
            String locale);
    
    ResponseDto<MenuDashboardResponse> getMenuDashboard(String dateRange, LocalDateTime startDate, LocalDateTime endDate, UUID restaurantGroupId, UUID restaurantId, String locale);
    
    void exportDashboardStatisticsToCsv(String period, LocalDateTime startDate, LocalDateTime endDate, UUID restaurantGroupId, UUID restaurantId, String salesStatsPeriod, String locale, HttpServletResponse response) throws IOException;
}

