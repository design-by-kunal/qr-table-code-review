package com.gulfnet.restaurantmanagement.controller;

import com.gulfnet.restaurantmanagement.service.AuditTrailService;
import com.gulfnet.shared_library.model.response.dto.AuditTrailListResponse;
import com.gulfnet.shared_library.model.response.dto.ResponseDto;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/v1/audit-trails")
@RequiredArgsConstructor
@Tag(name = "Audit Trail", description = "API for managing audit trail logs for all system actions")
public class AuditTrailController {

    private final AuditTrailService auditTrailService;

    /**
     * Retrieves audit trail logs with filtering, pagination, and search capabilities.
     * Supports all action types: Login, Logout, Payment, Refund, Cancellation, Discount,
     * Cash Drawer operations, Transaction Edit, Order Modification, and System Actions.
     * HQ_ADMIN can access all audit trails. MANAGER can only access audit trails for their assigned restaurant.
     * Can return data in JSON format (default) or export to CSV format.
     *
     * @param page             optional page number for pagination (JSON format only)
     * @param size             optional page size for pagination (JSON format only)
     * @param userId           optional filter by user ID
     * @param restaurantId     optional filter by restaurant ID
     * @param restaurantGroupId optional filter by restaurant group ID
     * @param actionType       optional filter by action type
     * @param status           filter by status (default: "ALL")
     * @param module           optional filter by module
     * @param role             optional filter by user role
     * @param search           optional search term for text search
     * @param period           optional predefined time period (e.g., "today", "week", "month")
     * @param date             optional filter by specific date (ISO date format)
     * @param startDate        optional filter by start date and time (ISO date-time format)
     * @param endDate          optional filter by end date and time (ISO date-time format)
     * @param sortBy           field to sort by (default: "createdAt")
     * @param direction        sort direction (default: "DESC")
     * @param format           output format: "json" (default) or "csv"
     * @param userRole         the user role from the request header (required)
     * @param userIdHeader     optional user ID from the request header
     * @param locale           locale code for localized responses (default: "en")
     * @param httpResponse     HTTP servlet response for CSV export (required for CSV format)
     * @return response containing paginated audit trail logs (JSON format) or null (CSV format - response written to HttpServletResponse)
     * @throws IOException if an I/O error occurs during CSV export
     */
    @GetMapping
    @Operation(summary = "Get audit trails", 
               description = "Retrieve audit trail logs with filtering, pagination, and search capabilities. Supports all action types: Login, Logout, Payment, Refund, Cancellation, Discount, Cash Drawer operations, Transaction Edit, Order Modification, and System Actions. HQ_ADMIN can access all audit trails. MANAGER can only access audit trails for their assigned restaurant.")
    public ResponseEntity<ResponseDto<AuditTrailListResponse>> getAuditTrails(
            @RequestParam(value = "page", required = false) Integer page,
            @RequestParam(value = "size", required = false) Integer size,
            @RequestParam(value = "userId", required = false) UUID userId,
            @RequestParam(value = "restaurantId", required = false) UUID restaurantId,
            @RequestParam(value = "restaurantGroupId", required = false) UUID restaurantGroupId,
            @RequestParam(value = "actionType", required = false) String actionType,
            @RequestParam(value = "status", required = false, defaultValue = "ALL") String status,
            @RequestParam(value = "module", required = false) String module,
            @RequestParam(value = "role", required = false) String role,
            @RequestParam(value = "search", required = false) String search,
            @RequestParam(value = "period", required = false) String period,
            @RequestParam(value = "date", required = false) 
                @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(value = "startDate", required = false) 
                @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startDate,
            @RequestParam(value = "endDate", required = false) 
                @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endDate,
            @RequestParam(value = "sortBy", required = false, defaultValue = "createdAt") String sortBy,
            @RequestParam(value = "direction", required = false, defaultValue = "DESC") String direction,
            @RequestParam(value = "format", required = false, defaultValue = "json") String format,
            @RequestHeader("User-Role") String userRole,
            @RequestHeader("User-ID") String userIdHeader,
            @RequestHeader(value = "locale", defaultValue = "en") String locale,
            HttpServletResponse httpResponse) throws IOException {

        log.info("Request received to get audit trails (page: {}, size: {}, userId: {}, restaurantId: {}, restaurantGroupId: {}, " +
                "actionType: {}, status: {}, module: {}, role: {}, search: {}, period: {}, date: {}, startDate: {}, endDate: {}, sortBy: {}, direction: {}, format: {})",
                page, size, userId, restaurantId, restaurantGroupId, actionType, status, module, role, search, period, date, startDate, endDate, sortBy, direction, format);

        // If format is CSV, export to CSV
        if ("csv".equalsIgnoreCase(format)) {
            auditTrailService.exportAuditTrailsToCsv(
                    userId, restaurantId, restaurantGroupId, actionType, status, module, role, search, period, date, startDate, endDate, userRole, userIdHeader, locale, httpResponse);
            log.info("Successfully exported audit trails to CSV");
            // Return null - response is already written to HttpServletResponse
            return null;
        }

        // Default: return JSON response
        ResponseDto<AuditTrailListResponse> response = auditTrailService.getAuditTrails(
                page, size, userId, restaurantId, restaurantGroupId, actionType, status, module, role, search, period, date, startDate, endDate, sortBy, direction, userRole, userIdHeader, locale);

        log.info("Successfully retrieved {} audit trails (total: {})",
                response.getData().getCount(), response.getData().getTotal());
        return ResponseEntity.ok(response);
    }

    /**
     * Retrieves all distinct action types available in audit trails for filter dropdown.
     * Filters based on user role - managers see only actions they can perform.
     *
     * @param locale   locale code for localized responses (default: "en")
     * @param userRole the user role from the request header (required)
     * @return response containing list of available action types for the user's role
     */
    @GetMapping("/action-types")
    @Operation(summary = "Get available action types", 
               description = "Retrieve all distinct action types available in audit trails for filter dropdown. Filters based on user role - managers see only actions they can perform.")
    public ResponseEntity<ResponseDto<List<String>>> getAvailableActionTypes(
            @RequestHeader(value = "locale", defaultValue = "en") String locale,
            @RequestHeader("User-Role") String userRole) {
        
        log.info("Request received to get available action types for audit trail filter (userRole: {})", userRole);
        
        ResponseDto<List<String>> response = auditTrailService.getAvailableActionTypes(locale, userRole);
        
        log.info("Successfully retrieved {} action types for role {}", 
                response.getData() != null ? response.getData().size() : 0, userRole);
        return ResponseEntity.ok(response);
    }
}

