package com.gulfnet.shared_library.model.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import java.util.UUID;
import com.gulfnet.shared_library.enums.EmploymentType;
import com.gulfnet.shared_library.enums.EntityStatus;
import com.gulfnet.shared_library.enums.RequestStatus;

@Data
public class RegisterUserRequest {
    @NotBlank
    private String firstName;
    @NotBlank
    private String lastName;
    @Email
    private String email;
    private String contactNumber;
    private String photoUrl;
    @NotNull
    private EmploymentType employmentType;
    
    @NotNull(message = "{user.register.userCode.required}")
    @NotBlank(message = "{user.register.userCode.blank}")
    private String userCode;
    
    private UUID roleId;
    @NotBlank(message = "{user.register.languageCode.blank}")
    private String languageCode;
    private UUID restaurantId;
    private UUID shiftId;
    private UUID restaurantGroupId;
    private Boolean isStatusLocked;
    private EntityStatus status;
    private Boolean isDeleted;
    private RequestStatus profileUpdateRequestStatus;
} 