package com.gulfnet.shared_library.model.response.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WaiterInfo {
    private UUID id;
    private String userCode;
    private String waiterName;
    private String firstName;
    private String lastName;
    private UUID tableAssignmentId;
}

