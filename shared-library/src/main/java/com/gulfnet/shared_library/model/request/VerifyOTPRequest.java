package com.gulfnet.shared_library.model.request;

import lombok.Data;

@Data
public class VerifyOTPRequest {
    private String email;
    
    private String otp;
    
    private String newPassword;
    
    private String confirmPassword;
    
    // Optional encrypted payload field for RSA encryption
    private String payload;
} 