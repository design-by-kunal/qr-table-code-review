package com.gulfnet.restaurantmanagement.service;

import com.gulfnet.shared_library.entity.AuditTrail;
import com.gulfnet.shared_library.entity.Restaurant;
import com.gulfnet.shared_library.entity.User;
import com.gulfnet.shared_library.enums.RequestStatus;
import com.gulfnet.shared_library.enums.ActionType;
import com.gulfnet.shared_library.model.response.dto.AuditTrailListResponse;
import com.gulfnet.shared_library.model.response.dto.ResponseDto;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public interface AuditTrailService {
    
    /**
     * Retrieves audit trail logs with filtering, pagination, and search capabilities.
     * Supports filtering by user, restaurant, restaurant group, action type, status, module, role,
     * time period, and text search. Results are filtered based on user role and access permissions.
     *
     * @param page             optional page number for pagination
     * @param size             optional page size for pagination
     * @param userId           optional filter by user ID
     * @param restaurantId     optional filter by restaurant ID
     * @param restaurantGroupId optional filter by restaurant group ID
     * @param actionType       optional filter by action type
     * @param status           filter by status
     * @param module           optional filter by module
     * @param role             optional filter by user role
     * @param search           optional search term for text search
     * @param period           optional predefined time period (e.g., "today", "week", "month")
     * @param date             optional filter by specific date
     * @param startDate        optional filter by start date and time
     * @param endDate          optional filter by end date and time
     * @param sortBy           field to sort by
     * @param direction        sort direction
     * @param userRole         user role for access control
     * @param userIdHeader     user ID for access control
     * @param locale           locale code for localized responses
     * @return response containing paginated audit trail logs with filters applied
     */
    ResponseDto<AuditTrailListResponse> getAuditTrails(
            Integer page,
            Integer size,
            UUID userId,
            UUID restaurantId,
            UUID restaurantGroupId,
            String actionType,
            String status,
            String module,
            String role,
            String search,
            String period,
            LocalDate date,
            LocalDateTime startDate,
            LocalDateTime endDate,
            String sortBy,
            String direction,
            String userRole,
            String userIdHeader,
            String locale);

    /**
     * Exports audit trail logs to CSV format and streams it to the HTTP response.
     * Uses the same filtering parameters as getAuditTrails.
     *
     * @param userId           optional filter by user ID
     * @param restaurantId     optional filter by restaurant ID
     * @param restaurantGroupId optional filter by restaurant group ID
     * @param actionType       optional filter by action type
     * @param status           filter by status
     * @param module           optional filter by module
     * @param role             optional filter by user role
     * @param search           optional search term for text search
     * @param period           optional predefined time period (e.g., "today", "week", "month")
     * @param date             optional filter by specific date
     * @param startDate        optional filter by start date and time
     * @param endDate          optional filter by end date and time
     * @param userRole         user role for access control
     * @param userIdHeader     user ID for access control
     * @param locale           locale code for localized responses
     * @param response         HTTP servlet response to stream the CSV file to
     * @throws IOException if an I/O error occurs during CSV generation or streaming
     */
    void exportAuditTrailsToCsv(
            UUID userId,
            UUID restaurantId,
            UUID restaurantGroupId,
            String actionType,
            String status,
            String module,
            String role,
            String search,
            String period,
            LocalDate date,
            LocalDateTime startDate,
            LocalDateTime endDate,
            String userRole,
            String userIdHeader,
            String locale,
            HttpServletResponse response) throws IOException;

    /**
     * Creates an audit trail record for a user action with comprehensive details.
     * Supports optional cash drawer balance information for cash drawer operations.
     *
     * @param user                the user who performed the action
     * @param actionType          the type of action performed
     * @param restaurant          the restaurant where the action occurred (can be null)
     * @param status              the status of the request/action
     * @param ipAddress          the IP address of the user
     * @param userAgent          the user agent string
     * @param entityId           optional UUID of the entity involved in the action
     * @param entityType         optional type of entity involved
     * @param notes              optional notes or description
     * @param openingBalance     optional opening balance for cash drawer operations
     * @param closingBalance     optional closing balance for cash drawer operations
     * @param expectedBalance    optional expected balance for cash drawer operations
     * @param discrepancyAmount   optional discrepancy amount for cash drawer operations
     * @param discrepancyReason  optional reason for discrepancy
     * @param createdBy          optional user who created this audit trail
     * @return the created audit trail entity
     */
    AuditTrail createAuditTrail(
            User user,
            ActionType actionType,
            Restaurant restaurant,
            RequestStatus status,
            String ipAddress,
            String userAgent,
            UUID entityId,
            String entityType,
            String notes,
            BigDecimal openingBalance,
            BigDecimal closingBalance,
            BigDecimal expectedBalance,
            BigDecimal discrepancyAmount,
            String discrepancyReason,
            User createdBy);

    /** @see #createAuditTrail(User, ActionType, Restaurant, RequestStatus, String, String, UUID, String, String, BigDecimal, BigDecimal, BigDecimal, BigDecimal, String, User) */
    AuditTrail createAuditTrail(
            User user,
            ActionType actionType,
            Restaurant restaurant,
            String ipAddress,
            String userAgent);

    /** @see #createAuditTrail(User, ActionType, Restaurant, RequestStatus, String, String, UUID, String, String, BigDecimal, BigDecimal, BigDecimal, BigDecimal, String, User) */
    AuditTrail createAuditTrail(
            User user,
            ActionType actionType,
            Restaurant restaurant,
            RequestStatus status,
            String ipAddress,
            String userAgent,
            UUID entityId,
            String entityType,
            String notes);

    /**
     * Creates an audit trail record specifically for cash drawer operations.
     * Includes opening balance, closing balance, expected balance, discrepancy information,
     * and optional notes.
     *
     * @param user                the user who performed the cash drawer operation
     * @param actionType          the type of cash drawer action
     * @param restaurant          the restaurant where the operation occurred
     * @param status              the status of the cash drawer operation
     * @param ipAddress          the IP address of the user
     * @param userAgent          the user agent string
     * @param openingBalance     the opening balance of the cash drawer
     * @param closingBalance     the closing balance of the cash drawer
     * @param expectedBalance    the expected balance of the cash drawer
     * @param discrepancyAmount  the discrepancy amount (if any)
     * @param discrepancyReason   the reason for the discrepancy (if any)
     * @param notes              optional notes or description
     * @return the created cash drawer audit trail entity
     */
    AuditTrail createCashDrawerAuditTrail(
            User user,
            ActionType actionType,
            Restaurant restaurant,
            RequestStatus status,
            String ipAddress,
            String userAgent,
            BigDecimal openingBalance,
            BigDecimal closingBalance,
            BigDecimal expectedBalance,
            BigDecimal discrepancyAmount,
            String discrepancyReason,
            String notes);

    void updateAuditTrailReview(UUID auditTrailId, User reviewedBy, RequestStatus status, String notes);

    /**
     * Update the restaurant_id for an audit trail after the restaurant is committed.
     * This is used for RESTAURANT_CREATE actions where the restaurant wasn't committed when the audit trail was created.
     */
    void updateAuditTrailRestaurantId(UUID entityId, UUID restaurantId, ActionType actionType);

    /**
     * Get all available action types for filter dropdown
     * Returns distinct action types from the database
     * Filters out action types not accessible to managers if userRole is MANAGER
     */
    ResponseDto<List<String>> getAvailableActionTypes(String locale, String userRole);
}

