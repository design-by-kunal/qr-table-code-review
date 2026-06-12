package com.gulfnet.usermanagement.service;

import com.gulfnet.shared_library.entity.AuditLogging;
import com.gulfnet.shared_library.entity.User;
import com.gulfnet.shared_library.model.response.dto.ResponseDto;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;
import java.util.UUID;

public interface AuditLoggingService {

    /**
     * Log a manager action
     */
    void logManagerAction(User manager, User employee, AuditLogging.AuditAction action, 
                         UUID restaurantId, User createdBy);

    /**
     * Log a manager action with restaurant context
     */
    void logManagerAction(User manager, User employee, AuditLogging.AuditAction action, 
                         UUID restaurantId);

    /**
     * Get audit records with filters
     * All parameters are optional - pass null to ignore that filter
     */
    Page<AuditLogging> getAuditRecordsWithFilters(UUID managerId, UUID employeeId, 
                                                 UUID restaurantId, 
                                                 AuditLogging.AuditAction action,
                                                 LocalDateTime startDate, LocalDateTime endDate, 
                                                 Pageable pageable);

    /**
     * Get audit logs with validation and business logic
     */
    ResponseDto<Page<AuditLogging>> getAuditLogs(int page, int size, UUID managerId, UUID employeeId, 
                                                UUID restaurantId, String action, String startDate, 
                                                String endDate, String userRole);
}
