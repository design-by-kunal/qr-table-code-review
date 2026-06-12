package com.gulfnet.shared_library.model.response.dto;

import com.gulfnet.shared_library.enums.RequestStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProfileUpdateRequestResponse {
    private UUID userId;
    private String firstName;
    private String lastName;
    private String email;
    private String userCode;
    private RequestStatus status;
    private String requestData; // JSON string of requested changes
    private LocalDateTime requestedAt;
    private LocalDateTime reviewedAt;
    private UUID reviewedBy;
    private String reviewedByName;
    private String comments;
}
