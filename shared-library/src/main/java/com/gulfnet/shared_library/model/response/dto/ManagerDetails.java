package com.gulfnet.shared_library.model.response.dto;

import lombok.*;

import java.time.OffsetTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@ToString
public class ManagerDetails {
    private UUID managerId;
    private String name;
    private String contactNumber;
    private String email;
    private String shiftTime; // e.g., "09:00Z - 17:00Z"
    private OffsetTime shiftStartTime;
    private OffsetTime shiftEndTime;
    private String photoUrl;
    private String restaurantName;
}

