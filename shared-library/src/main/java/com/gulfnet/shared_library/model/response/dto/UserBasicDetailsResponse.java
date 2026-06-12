package com.gulfnet.shared_library.model.response.dto;

import com.gulfnet.shared_library.enums.EmploymentType;
import com.gulfnet.shared_library.enums.EntityStatus;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
public class UserBasicDetailsResponse {
    private UUID id;
    private String userCode;
    private String firstName;
    private String lastName;
    private String contactNumber;
    private String email;
    private String photoUrl;
    private EmploymentType employmentType;
    private RoleResponse role;
    private String languageCode;
    private EntityStatus status;
    private LocalDateTime createdAt;
    private UUID restaurantId;
    private String restaurantName;
    private UUID restaurantGroupId;
    private String restaurantGroupName;
}
