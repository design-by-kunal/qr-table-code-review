package com.gulfnet.shared_library.model.response.dto;

import lombok.Builder;
import lombok.Data;

import java.util.UUID;

@Data
@Builder
public class UserResponseDto {
    private UUID id;
    private String firstName;
    private String lastName;
    private String email;
    private String contactNumber;
    private String photoUrl;
    private String employmentType;
    private String userCode;
    private UUID roleId;
    private String languageCode;
    private UUID restaurantId;
    private UUID shiftId;
    private String status;
    private Boolean isStatusLocked;
}
