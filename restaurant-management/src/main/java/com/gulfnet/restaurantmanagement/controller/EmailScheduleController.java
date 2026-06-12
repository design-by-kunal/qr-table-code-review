package com.gulfnet.restaurantmanagement.controller;

import com.gulfnet.restaurantmanagement.service.EmailScheduleService;
import com.gulfnet.shared_library.enums.ReportType;
import com.gulfnet.shared_library.enums.ScheduleFrequency;
import com.gulfnet.shared_library.model.request.CreateEmailScheduleRequest;
import com.gulfnet.shared_library.model.response.dto.EmailScheduleListResponse;
import com.gulfnet.shared_library.model.response.dto.EmailScheduleResponse;
import com.gulfnet.shared_library.model.response.dto.ResponseDto;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/v1/email-schedules")
@RequiredArgsConstructor
public class EmailScheduleController {

    private final EmailScheduleService emailScheduleService;

    /**
     * Create a new email schedule
     * POST /api/v1/email-schedules
     */
    @PostMapping
    public ResponseEntity<ResponseDto<EmailScheduleResponse>> createSchedule(
            @Valid @RequestBody CreateEmailScheduleRequest request,
            @RequestHeader("User-ID") String userId,
            @RequestHeader("User-Role") String userRole,
            @RequestHeader(value = "locale", defaultValue = "en") String locale) {

        log.info("Request received to create email schedule: {} by user: {} with role: {}",
                request.getScheduleName(), userId, userRole);

        ResponseDto<EmailScheduleResponse> response = emailScheduleService.createSchedule(
                request, userId, userRole, locale);

        log.info("Successfully created email schedule: {}", response.getData().getId());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * Delete an email schedule
     * DELETE /api/v1/email-schedules/{id}
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<ResponseDto<Void>> deleteSchedule(
            @PathVariable UUID id,
            @RequestHeader("User-ID") String userId,
            @RequestHeader("User-Role") String userRole,
            @RequestHeader(value = "locale", defaultValue = "en") String locale) {

        log.info("Request received to delete email schedule: {} by user: {} with role: {}",
                id, userId, userRole);

        ResponseDto<Void> response = emailScheduleService.deleteSchedule(id, userId, userRole, locale);

        log.info("Successfully deleted email schedule: {}", id);
        return ResponseEntity.ok(response);
    }

    /**
     * Get all email schedules
     * GET /api/v1/email-schedules?restaurantId={id}&restaurantGroupId={id}&reportType={type}&frequency={frequency}&sortBy={field}&sortDirection={ASC|DESC}&page={page}&size={size}
     */
    @GetMapping
    public ResponseEntity<ResponseDto<EmailScheduleListResponse>> getAllSchedules(
            @RequestParam(value = "restaurantId", required = false) UUID restaurantId,
            @RequestParam(value = "restaurantGroupId", required = false) UUID restaurantGroupId,
            @RequestParam(value = "reportType", required = false) ReportType reportType,
            @RequestParam(value = "frequency", required = false) ScheduleFrequency frequency,
            @RequestParam(value = "sortBy", required = false) String sortBy,
            @RequestParam(value = "sortDirection", required = false) String sortDirection,
            @RequestParam(value = "page", required = false) Integer page,
            @RequestParam(value = "size", required = false) Integer size,
            @RequestHeader("User-ID") String userId,
            @RequestHeader("User-Role") String userRole,
            @RequestHeader(value = "locale", defaultValue = "en") String locale) {

        log.info("Request received to get email schedules by user: {} with role: {}, restaurantId: {}, restaurantGroupId: {}, reportType: {}, frequency: {}, sortBy: {}, sortDirection: {}",
                userId, userRole, restaurantId, restaurantGroupId, reportType, frequency, sortBy, sortDirection);

        ResponseDto<EmailScheduleListResponse> response = emailScheduleService.getAllSchedules(
                userId, userRole, restaurantId, restaurantGroupId, reportType, frequency, sortBy, sortDirection, page, size, locale);


        log.info("Successfully retrieved {} email schedules", response.getData().getSchedules().size());

        return ResponseEntity.ok(response);
    }
}

