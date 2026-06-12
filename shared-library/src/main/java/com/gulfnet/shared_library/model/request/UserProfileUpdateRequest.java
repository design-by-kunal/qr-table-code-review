package com.gulfnet.shared_library.model.request;

import jakarta.validation.constraints.Email;
import lombok.Data;

@Data
public class UserProfileUpdateRequest {
    
    private String firstName;
    
    private String lastName;
    
    @Email(message = "{user.profile.email.invalid}")
    private String email;
    
    private String contactNumber;
    
    private String photoUrl;
    
    private String languageCode;
    
    private String userCode;
}
