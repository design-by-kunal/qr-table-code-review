package com.gulfnet.shared_library.model.response.dto;

import com.gulfnet.shared_library.enums.EntityStatus;
import com.gulfnet.shared_library.enums.EmploymentType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import java.time.OffsetTime;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserAccountDetailsDto {
    private UUID id;
    private String firstName;
    private String lastName;
    private String email;
    private String contactNumber;
    private String photoUrl;
    private String userCode;
    private EmploymentType employmentType;
    private EntityStatus status;
    private String languageCode;
    private UUID roleId;
    private String roleName;
    private Boolean isStatusLocked;
    
    // Shift details
    private ShiftDetails shiftDetails;
    
    // Restaurant group details with IDs
    private RestaurantGroupDetails restaurantGroupDetails;
    
    // Restaurant details with IDs
    private RestaurantDetails restaurantDetails;
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ShiftDetails {
        private UUID shiftId;
        private String shiftName;
        private OffsetTime startTime;
        private OffsetTime endTime;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RestaurantGroupDetails {
        private UUID restaurantGroupId;
        private List<RestaurantGroupTranslationDTO> translations;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RestaurantDetails {
        private UUID restaurantId;
        private List<RestaurantTranslationDto> translations;
    }
} 