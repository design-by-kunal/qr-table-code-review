package com.gulfnet.shared_library.model.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.UUID;

import com.gulfnet.shared_library.enums.EmploymentType;
import com.gulfnet.shared_library.enums.EntityStatus;

@Data
public class UpdateUserRequest {

    @NotBlank(message = "{user.update.userCode.blank}")
    private String userCode;
    
    @NotBlank(message = "{user.update.firstName.blank}")
    private String firstName;

    @NotBlank(message = "{user.update.lastName.blank}")
    private String lastName;

    @Email(message = "{user.update.email.invalid}")
    private String email;

    private String contactNumber;

    private String photoUrl;

    @NotNull(message = "{user.update.employmentType.required}")
    private EmploymentType employmentType;

    private UUID roleId;

    @NotBlank(message = "{user.update.languageCode.blank}")
    private String languageCode;

    private UUID restaurantId;

    private UUID shiftId;

    private UUID restaurantGroupId;

    private EntityStatus status;

    private Boolean isStatusLocked;


}
