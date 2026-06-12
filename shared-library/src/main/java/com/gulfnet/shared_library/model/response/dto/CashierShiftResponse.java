package com.gulfnet.shared_library.model.response.dto;

import com.gulfnet.shared_library.enums.ShiftStatus;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CashierShiftResponse {
    private UUID id;
    private UUID cashDrawerId;
    private String cashDrawerName;
    private UUID cashierId;
    private String cashierName;
    private UUID restaurantId;
    private UUID shiftId;
    private String shiftName;
    private ShiftStatus status;
    private BigDecimal openingBalance;
    private BigDecimal closingBalance;
    private BigDecimal expectedClosingBalance;
    private BigDecimal discrepancyAmount;
    private String discrepancyReason;
    private LocalDateTime startedAt;
    private LocalDateTime closedAt;
    private UUID approvedBy;
    private String approvedByName;
    private LocalDateTime approvedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}

