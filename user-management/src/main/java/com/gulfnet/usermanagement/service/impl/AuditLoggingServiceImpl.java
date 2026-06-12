package com.gulfnet.usermanagement.service.impl;

import com.gulfnet.shared_library.entity.AuditLogging;
import com.gulfnet.shared_library.entity.User;
import com.gulfnet.shared_library.model.response.dto.PaginationMetaData;
import com.gulfnet.shared_library.model.response.dto.ResponseDto;
import com.gulfnet.shared_library.repository.AuditLoggingRepository;
import com.gulfnet.usermanagement.service.AuditLoggingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuditLoggingServiceImpl implements AuditLoggingService {

    private final AuditLoggingRepository auditLoggingRepository;

    /**
     * Creates and persists an audit log entry describing a manager's action on an employee
     * within a specific restaurant, recording who performed the action and who created the log.
     *
     * @param manager      the manager performing the action
     * @param employee     the employee on whom the action is performed
     * @param action       the type of audit action performed
     * @param restaurantId the restaurant identifier related to the action
     * @param createdBy    the user entity recorded as the creator of the audit log
     */
    @Override
    @Transactional
    public void logManagerAction(User manager, User employee, AuditLogging.AuditAction action, 
                                UUID restaurantId, User createdBy) {
        try {
            AuditLogging auditLog = AuditLogging.builder()
                    .manager(manager)
                    .employee(employee)
                    .action(action)
                    .restaurantId(restaurantId)
                    .createdBy(createdBy)
                    .createdAt(OffsetDateTime.now(ZoneOffset.UTC))
                    .build();

            auditLoggingRepository.save(auditLog);
            
            log.info("Audit log created: Manager {} performed {} on Employee {} in Restaurant {}", 
                    manager.getUserCode(), action, employee.getUserCode(), restaurantId);
        } catch (Exception e) {
            log.error("Failed to create audit log for manager {} action {} on employee {}", 
                    manager.getUserCode(), action, employee.getUserCode(), e);
            // Don't throw exception to avoid breaking the main operation
        }
    }

    /**
     * Convenience overload that logs a manager action using the manager as both
     * the actor and the creator of the audit record.
     *
     * @param manager      the manager performing the action
     * @param employee     the employee on whom the action is performed
     * @param action       the type of audit action performed
     * @param restaurantId the restaurant identifier related to the action
     */
    @Override
    @Transactional
    public void logManagerAction(User manager, User employee, AuditLogging.AuditAction action, 
                                UUID restaurantId) {
        logManagerAction(manager, employee, action, restaurantId, manager);
    }

    @Override
    public Page<AuditLogging> getAuditRecordsWithFilters(UUID managerId, UUID employeeId, 
                                                         UUID restaurantId, 
                                                         AuditLogging.AuditAction action,
                                                         LocalDateTime startDate, LocalDateTime endDate, 
                                                         Pageable pageable) {
        return auditLoggingRepository.findWithFilters(
                managerId, employeeId, restaurantId, action, startDate, endDate, pageable);
    }

    /**
     * Retrieves paginated audit logs with multiple optional filters (manager, employee,
     * restaurant, action type, and date range) and enforces that only MANAGER and HQ_ADMIN
     * roles can access this data. Validates pagination and filter inputs before querying.
     *
     * @param page        page number (1-based; first page is 1)
     * @param size        page size (1–100)
     * @param managerId   optional manager ID filter
     * @param employeeId  optional employee ID filter
     * @param restaurantId optional restaurant ID filter
     * @param action      optional audit action filter as string
     * @param startDate   optional ISO start date-time string
     * @param endDate     optional ISO end date-time string
     * @param userRole    role of the requesting user (must be MANAGER or HQ_ADMIN)
     * @return {@link ResponseDto} wrapping a {@link Page} of {@link AuditLogging} entries
     */
    @Override
    public ResponseDto<Page<AuditLogging>> getAuditLogs(int page, int size, UUID managerId, UUID employeeId, 
                                                       UUID restaurantId, String action, String startDate, 
                                                       String endDate, String userRole) {
        
        // Role validation
        if (!"MANAGER".equals(userRole) && !"HQ_ADMIN".equals(userRole)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Access denied. Only MANAGER and HQ_ADMIN roles can access audit logs.");
        }
        
        // Pagination validation (1-based page number)
        if (page < 1) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Page number must be at least 1");
        }
        if (size < 1 || size > 100) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Page size must be between 1 and 100");
        }
        
        // Action validation
        AuditLogging.AuditAction actionEnum = null;
        if (action != null && !action.trim().isEmpty()) {
            try {
                actionEnum = AuditLogging.AuditAction.valueOf(action.toUpperCase());
            } catch (IllegalArgumentException e) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, 
                    "Invalid action type: " + action + ". Valid actions: " + 
                    Arrays.toString(AuditLogging.AuditAction.values()));
            }
        }
        
        // Date validation
        LocalDateTime start = null;
        LocalDateTime end = null;
        if (startDate != null && !startDate.trim().isEmpty()) {
            try {
                start = LocalDateTime.parse(startDate);
            } catch (Exception e) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, 
                    "Invalid start date format. Use ISO format: yyyy-MM-ddTHH:mm:ss");
            }
        }
        if (endDate != null && !endDate.trim().isEmpty()) {
            try {
                end = LocalDateTime.parse(endDate);
            } catch (Exception e) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, 
                    "Invalid end date format. Use ISO format: yyyy-MM-ddTHH:mm:ss");
            }
        }
        
        // Date range validation
        if (start != null && end != null && start.isAfter(end)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, 
                "Start date cannot be after end date");
        }
        
        // Create pageable (convert 1-based page to 0-based index)
        Pageable pageable = PageRequest.of(page - 1, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        
        try {
            Page<AuditLogging> auditLogs = auditLoggingRepository.findWithFilters(
                    managerId, employeeId, restaurantId, actionEnum, start, end, pageable);

            PaginationMetaData metaData = PaginationMetaData.builder()
                    .page(page)
                    .size(size)
                    .totalPages(auditLogs.getTotalPages())
                    .totalRecords(auditLogs.getTotalElements())
                    .build();

            return ResponseDto.<Page<AuditLogging>>builder()
                    .message("Audit logs retrieved successfully")
                    .data(auditLogs)
                    .metaData(metaData)
                    .build();
        } catch (Exception e) {
            log.error("Error retrieving audit logs: {}", e.getMessage(), e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, 
                "Failed to retrieve audit logs");
        }
    }
}
