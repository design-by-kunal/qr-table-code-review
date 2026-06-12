package com.gulfnet.shared_library.model.response.dto;

import lombok.Data;

@Data
public class LoginResponse {
    private String token;
    private UserBasicDetailsResponse userBasicDetails;
} 