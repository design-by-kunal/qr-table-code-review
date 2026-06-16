package com.gulfnet.usermanagement.service;

import com.gulfnet.shared_library.entity.BulkUpload;
import com.gulfnet.shared_library.model.request.RegisterUserRequest;
import com.gulfnet.shared_library.model.request.UpdateUserRequest;
import com.gulfnet.shared_library.model.request.UpdatePreferredLanguageRequest;
import com.gulfnet.shared_library.model.request.UpdateDeviceTokenRequest;
import com.gulfnet.shared_library.model.request.EmailAvailabilityRequest;
import com.gulfnet.shared_library.model.response.dto.*;
import com.gulfnet.shared_library.model.request.LoginRequest;
import com.gulfnet.shared_library.model.request.UserProfileUpdateRequest;
import com.gulfnet.shared_library.model.request.ProfileUpdateApprovalRequest;
import com.gulfnet.shared_library.model.response.dto.ProfileUpdateRequestResponse;
import com.gulfnet.shared_library.model.response.dto.ProfileUpdateRequestWithComparisonResponse;
import com.gulfnet.shared_library.model.response.dto.ProfileUpdateRequestWithComparisonListResponse;
import com.gulfnet.shared_library.enums.RequestStatus;

import jakarta.validation.Valid;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

public interface UserService {
    ResponseDto<UserAccountDataResponse> registerUser(RegisterUserRequest request, String creatorId, String creatorRole);

    ResponseEntity<ResponseDto<LoginResponseDto<LoginResponse>>> login(LoginRequest request, String ipAddress, String userAgent, String appVersion);

    ResponseDto<UserListResponse> getEmployees(int page, int size, UUID roleId, String status, String employmentType, String search, String sortBy, Sort.Direction direction, String restaurantStatus, UUID restaurantId, UUID restaurantGroupId, String localeHeader, Boolean isDeleted);

    ResponseDto<UserAccountDataResponse> updateUser(UUID id, @Valid UpdateUserRequest request, String updaterId, String updaterRole);

    ResponseDto<Void> deleteUser(UUID userId, String deleterId, String deleterRole, String locale);
        
    ResponseDto<String> logout(String token);

    ResponseDto<UserAccountDataResponse> getUserAccountDetails(UUID userId);

    ResponseDto<BulkUpload> processBulkUpload(MultipartFile file, MultipartFile imageZipFile, String action, String utfType, String language, String userId, String userRole, String localeHeader) throws IOException;

    void validateSession(String token, String locale, String appType, String appVersion);

    ResponseDto<UserRestaurantUnassignResponse> unassignRestaurantFromUser(UUID userId, String updaterId, String updaterRole);

    ResponseDto<Void> deleteMultipleUsers(List<UUID> userIds, String deletedReason, String updaterId, String userRole);

    ResponseDto<UserDataResponse> updatePreferredLanguage(UUID userId, UpdatePreferredLanguageRequest request, String updaterId);

    ResponseDto<Void> cancelProfileUpdateRequest(String userId);

    ResponseDto<ProfileUpdateRequestResponse> approveOrDeclineProfileUpdateRequest(UUID userId, ProfileUpdateApprovalRequest request, String managerId, String managerRole);

    ResponseDto<ProfileUpdateRequestWithComparisonListResponse> getPendingProfileUpdateRequestsWithComparison(int page, int size, RequestStatus status, String userRole);

    ResponseDto<UnifiedRequestListResponse> getAllPendingRequests(int page, int size, RequestStatus status, String requestType, String sortBy, String sortDirection, String userRole, String userId);

    ResponseDto<RequestDetailsResponse> getRequestDetails(UUID requestId, String userRole, String userId);

    ResponseDto<RequestApprovalResponse> approveOrDeclineRequest(UUID requestId, ProfileUpdateApprovalRequest request, String managerId, String managerRole);

    ResponseDto<DeviceTokenResponse> updateDeviceToken(UUID userId, UpdateDeviceTokenRequest request, String updaterId);

    ResponseDto<RequestTypeListResponse> getAllRequestTypes();
    
    ResponseDto<EmailAvailabilityResponse> checkEmailAvailability(EmailAvailabilityRequest request, String locale);
    
    ResponseDto<Void> restoreUsers(List<UUID> ids, String userId);
    
    }
