package com.gulfnet.shared_library.model.response.dto;

import com.gulfnet.shared_library.enums.RequestStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProfileUpdateRequestWithComparisonResponse {
    private UUID userId;
    private String firstName;
    private String lastName;
    private String email;
    private String userCode;
    private RequestStatus status;
    private String requestData; // JSON string of requested changes
    private LocalDateTime requestedAt;
    private UUID requestedBy;
    private String requestedByName;
    private String requestedByRole;
    private LocalDateTime reviewedAt;
    private UUID reviewedBy;
    private String reviewedByName;
    private String comments;
    private String reason; // Reason for the profile update request
    private String restaurantName; // Restaurant name for the user
    
    // Old data (current user data before request)
    private String oldFirstName;
    private String oldLastName;
    private String oldEmail;
    private String oldContactNumber;
    private String oldPhotoUrl;
    private String oldLanguageCode;
    
    // New data (requested changes)
    private String newFirstName;
    private String newLastName;
    private String newEmail;
    private String newContactNumber;
    private String newPhotoUrl;
    private String newLanguageCode;
    
    // Fields that have changed (for easy frontend highlighting)
    private boolean firstNameChanged;
    private boolean lastNameChanged;
    private boolean emailChanged;
    private boolean contactNumberChanged;
    private boolean photoUrlChanged;
    private boolean languageCodeChanged;
}
