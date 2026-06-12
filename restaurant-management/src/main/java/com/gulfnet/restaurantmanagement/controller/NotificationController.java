package com.gulfnet.restaurantmanagement.controller;

import com.gulfnet.restaurantmanagement.service.NotificationQueryService;
import com.gulfnet.shared_library.model.request.MarkAsReadRequest;
import com.gulfnet.shared_library.model.response.dto.NotificationFilterTypesResponseDto;
import com.gulfnet.shared_library.model.response.dto.NotificationResponseDto;
import com.gulfnet.shared_library.model.response.dto.ResponseDto;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping("/api/v1/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationQueryService notificationQueryService;

    /**
     * Retrieves a paginated and filterable list of notifications for a user.
     * Supports filtering by category/notification type, status, KDS, and sorting options.
     * Uses notificationTypeId if provided, otherwise falls back to category parameter.
     *
     * @param userId            the user ID from the request header (required)
     * @param page              optional page number for pagination (1-based; first page is 1; defaults to 1)
     * @param size              optional page size for pagination
     * @param category          optional filter by notification category (used if notificationTypeId not provided)
     * @param notificationTypeId optional filter by notification type ID (e.g., "orders", "table", "kds", "requests")
     * @param status            optional filter by notification status (read/unread)
     * @param sortBy            field to sort by (default: "date")
     * @param sortDirection     sort direction (default: "DESC")
     * @param kdsId             optional filter by KDS ID
     * @return response containing paginated list of notifications with filters applied
     */
    @GetMapping
    public ResponseEntity<ResponseDto<List<NotificationResponseDto>>> listNotifications(
            @RequestHeader("User-ID") String userId,
            @RequestParam(value = "page", required = false) Integer page,
            @RequestParam(value = "size", required = false) Integer size,
            @RequestParam(value = "category", required = false) String category,
            @RequestParam(value = "notificationTypeId", required = false) String notificationTypeId,
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "sortBy", defaultValue = "date") String sortBy,
            @RequestParam(value = "sortDirection", defaultValue = "DESC") String sortDirection,
            @RequestParam(value = "kdsId", required = false) String kdsId) {

        // Use notificationTypeId if provided (e.g., "orders", "table", "kds", "requests"), otherwise fall back to category
        String categoryToUse = (notificationTypeId != null && !notificationTypeId.trim().isEmpty()) 
                ? notificationTypeId 
                : category;

        log.info(
                "Fetching notifications for user: {} (page: {}, size: {}, category: {}, notificationTypeId: {}, status: {}, sortBy: {}, sortDirection: {}, kdsId: {})",
                userId,
                page,
                size,
                category,
                notificationTypeId,
                status,
                sortBy,
                sortDirection,
                kdsId);

        ResponseDto<List<NotificationResponseDto>> response =
                notificationQueryService.getNotificationsForUser(
                        userId, page, size, categoryToUse, status, sortBy, sortDirection, kdsId, LocaleContextHolder.getLocale());

        return ResponseEntity.ok(response);
    }

    /**
     * Marks a single notification as read for a user.
     *
     * @param userId         the user ID from the request header (required)
     * @param notificationId the UUID of the notification to mark as read
     * @return response containing the updated notification details
     */
    @PatchMapping("/{notificationId}/read")
    public ResponseEntity<ResponseDto<NotificationResponseDto>> markAsRead(
            @RequestHeader("User-ID") String userId,
            @PathVariable UUID notificationId) {

        log.info("Marking notification {} as read for user: {}", notificationId, userId);

        ResponseDto<NotificationResponseDto> response =
                notificationQueryService.markNotificationAsRead(
                        userId, notificationId, LocaleContextHolder.getLocale());

        return ResponseEntity.ok(response);
    }

    /**
     * Marks multiple notifications as read for a user in a single operation.
     *
     * @param userId the user ID from the request header (required)
     * @param request the request containing list of notification IDs to mark as read
     * @return response containing list of updated notification details
     */
    @PostMapping("/mark-read")
    public ResponseEntity<ResponseDto<List<NotificationResponseDto>>> markMultipleAsRead(
            @RequestHeader("User-ID") String userId,
            @RequestBody MarkAsReadRequest request) {

        log.info("Marking {} notifications as read for user: {}", 
                request.getNotificationIds() != null ? request.getNotificationIds().size() : 0, userId);

        ResponseDto<List<NotificationResponseDto>> response =
                notificationQueryService.markNotificationsAsRead(
                        userId, request.getNotificationIds(), LocaleContextHolder.getLocale());

        return ResponseEntity.ok(response);
    }

    /**
     * Retrieves available notification filter types for the type dropdown.
     * For MANAGER role, excludes threshold alert types (managers do not receive those notifications).
     * User ID is taken from User-ID header (added by gateway from JWT).
     */
    @GetMapping("/filter-types")
    public ResponseEntity<ResponseDto<NotificationFilterTypesResponseDto>> getFilterTypes(
            @RequestHeader("User-ID") String userId) {
        log.info("Fetching notification filter types for user: {}", userId);

        ResponseDto<NotificationFilterTypesResponseDto> response =
                notificationQueryService.getNotificationFilterTypes(userId, LocaleContextHolder.getLocale());

        return ResponseEntity.ok(response);
    }
}

