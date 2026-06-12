package com.gulfnet.shared_library.model.response.dto;

import lombok.*;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@ToString
public class OnShiftStaff {
    private UUID staffId;
    private String name;
    private String roleName;
    private String photoUrl;
    private String restaurantName;
}

