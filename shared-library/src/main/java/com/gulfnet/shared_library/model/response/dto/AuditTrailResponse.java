package com.gulfnet.shared_library.model.response.dto;

import com.gulfnet.shared_library.enums.RequestStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuditTrailResponse {
    private UUID id;
    private String logNumber;
    private String actionType;
    private String userName;
    private String userCode;
    private UUID userId;
    private String restaurantName;
    private UUID restaurantId;
    private String restaurantGroupName;
    private UUID restaurantGroupId;
    private LocalDateTime requestDate;
    private RequestStatus status;
    private BigDecimal openingBalance;
    private BigDecimal closingBalance;
    private BigDecimal expectedBalance;
    private BigDecimal discrepancyAmount;
    private String discrepancyReason;
    private String notes;
    private UUID entityId;
    private String entityType;
    private String ipAddress;
    private String userAgent;
    private String reviewedByName;
    private UUID reviewedById;
    private LocalDateTime reviewedAt;
    private String createdByName;
    private UUID createdById;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private String role;
    private UUID transactionId;
    private String tableName;
    private String sectionName;
    // Additional fields for list/table view
    private LocalDateTime timestamp; // Alias for createdAt for clearer naming
    private String module; // Module name derived from entityType
    private String description; // Enhanced description combining action type, entity type, and notes
}

