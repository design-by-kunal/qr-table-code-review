package com.gulfnet.shared_library.model.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class EmployeeEditProfileRequestDto {
    
    @NotBlank(message = "{employee.profile.firstName.required}")
    private String firstName;
    
    @NotBlank(message = "{employee.profile.lastName.required}")
    private String lastName;
    
    @Email(message = "{employee.profile.email.invalid}")
    @NotBlank(message = "{employee.profile.email.required}")
    private String email;
    
    private String contactNumber;
    private String photoUrl;
    
    // Add any other fields that employees should be able to edit
}
