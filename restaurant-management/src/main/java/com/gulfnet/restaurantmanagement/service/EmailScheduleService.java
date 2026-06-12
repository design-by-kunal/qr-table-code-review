package com.gulfnet.restaurantmanagement.service;

import com.gulfnet.shared_library.enums.ReportType;
import com.gulfnet.shared_library.enums.ScheduleFrequency;
import com.gulfnet.shared_library.model.request.CreateEmailScheduleRequest;
import com.gulfnet.shared_library.model.response.dto.EmailScheduleListResponse;
import com.gulfnet.shared_library.model.response.dto.EmailScheduleResponse;
import com.gulfnet.shared_library.model.response.dto.ResponseDto;

import java.util.UUID;

public interface EmailScheduleService {

    ResponseDto<EmailScheduleResponse> createSchedule(
            CreateEmailScheduleRequest request,
            String creatorId,
            String creatorRole,
            String locale);

    ResponseDto<Void> deleteSchedule(
            UUID scheduleId,
            String deleterId,
            String deleterRole,
            String locale);

    /**
     * Retrieves a paginated and filterable list of email schedules.
     * Supports filtering by restaurant, restaurant group, report type, and frequency.
     *
     * @param requesterId      ID of the user requesting the schedules
     * @param requesterRole    role of the user requesting the schedules
     * @param restaurantId     optional filter by restaurant ID
     * @param restaurantGroupId optional filter by restaurant group ID
     * @param reportType       optional filter by report type
     * @param frequency        optional filter by schedule frequency
     * @param sortBy           field to sort by
     * @param sortDirection    sort direction (ASC or DESC)
     * @param page             page number (1-based)
     * @param size             page size
     * @param locale           locale code for localized responses
     * @return {@link ResponseDto} containing paginated list of email schedules
     */
    ResponseDto<EmailScheduleListResponse> getAllSchedules(
            String requesterId,
            String requesterRole,
            UUID restaurantId,
            UUID restaurantGroupId,
            ReportType reportType,
            ScheduleFrequency frequency,
            String sortBy,
            String sortDirection,
            Integer page,
            Integer size,
            String locale);

    void syncAllSchedules(); // Sync database schedules with Quartz (for startup/recovery)

    /**
     * Deletes all email report schedules (and Quartz jobs) that the user created for the given restaurant.
     * Used when the user is no longer assigned to that restaurant.
     */
    void deleteSchedulesForUserAndRestaurant(UUID userId, UUID restaurantId);

    /**
     * Deletes all email report schedules (and Quartz jobs) created by the user.
     * Used when the user account is soft-deleted.
     */
    void deleteSchedulesForUser(UUID userId);
}

