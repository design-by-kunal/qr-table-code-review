package com.gulfnet.shared_library.model.request;

import lombok.Data;

@Data
public class BulkUserUploadRequest {
    private String userCode;
    private String firstName;
    private String lastName;
    private String email;
    private String mobileNumber;
    private String role;
    private String employmentType;
    private String restaurantCode;
    private String restaurantGroupCode;
    private String languageCode;
    private String status;
    private String shift;
    private String imageName;
} 