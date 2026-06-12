package com.gulfnet.shared_library.model.response.dto;

import lombok.*;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CashierOpenShiftResponse {
    private UUID cashierShiftId;
    private UUID cashDrawerId;
    private UUID cashierId;
}
