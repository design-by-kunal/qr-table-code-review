package com.gulfnet.usermanagement.controller;

import com.gulfnet.shared_library.entity.BulkUpload;
import com.gulfnet.shared_library.model.request.LoginRequest;
import com.gulfnet.shared_library.model.request.BulkDeleteUsersRequest;
import com.gulfnet.shared_library.model.request.RestoreEntitiesRequest;
import com.gulfnet.shared_library.model.request.RegisterUserRequest;
import com.gulfnet.shared_library.model.request.UpdateUserRequest;
import com.gulfnet.shared_library.model.request.UpdatePreferredLanguageRequest;
import com.gulfnet.shared_library.model.request.UpdateDeviceTokenRequest;
import com.gulfnet.shared_library.model.request.EmailAvailabilityRequest;
import com.gulfnet.shared_library.model.request.UserProfileUpdateRequest;
import com.gulfnet.shared_library.model.request.ProfileUpdateApprovalRequest;
import com.gulfnet.shared_library.model.response.dto.*;
import com.gulfnet.shared_library.model.response.dto.ProfileUpdateRequestWithComparisonListResponse;
import com.gulfnet.shared_library.model.response.dto.ProfileUpdateRequestWithComparisonResponse;
import com.gulfnet.shared_library.model.response.dto.UnifiedRequestListResponse;
import com.gulfnet.shared_library.model.response.dto.AdditionalDiscountRequestResponse;
import com.gulfnet.shared_library.model.response.dto.RequestDetailsResponse;
import com.gulfnet.shared_library.model.response.dto.RequestApprovalResponse;
import com.gulfnet.shared_library.model.response.dto.RequestTypeListResponse;
import com.gulfnet.shared_library.enums.RequestStatus;
import com.gulfnet.usermanagement.service.UserService;
import com.gulfnet.usermanagement.service.BulkUserUploadService;
import com.gulfnet.usermanagement.service.AuditLoggingService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Sort;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import com.gulfnet.shared_library.entity.AuditLogging;
import java.time.LocalDateTime;
import com.gulfnet.shared_library.enums.BulkUploadStatus;
import com.gulfnet.shared_library.enums.UploadType;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import org.springframework.context.i18n.LocaleContextHolder;

import com.gulfnet.usermanagement.util.MessageUtil;
import com.gulfnet.usermanagement.validator.CsvFileUploadValidator;
import com.gulfnet.shared_library.security.EncryptedPayloadDecoder;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;

@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
@Slf4j
public class UserController {
    private static final String HEADER_WAITER_APP_VERSION = "WaiterAppVersion";
    private static final String HEADER_CASHIER_APP_VERSION = "CashierAppVersion";
    private static final String HEADER_KDS_APP_VERSION = "KDSAppVersion";
    private static final String HEADER_APP_TYPE = "App-Type";
    private static final String HEADER_APP_VERSION = "App-Version";

    private final UserService userService;
    private final BulkUserUploadService bulkUserUploadService;
    private final MessageUtil messageUtil;
    private final CsvFileUploadValidator csvFileUploadValidator;
    private final AuditLoggingService auditLoggingService;
    private final EncryptedPayloadDecoder encryptedPayloadDecoder;

    @PostMapping("/register")
    public ResponseEntity<ResponseDto<UserAccountDataResponse>> registerUser(
            @Valid @RequestBody RegisterUserRequest request,
            @RequestHeader("User-ID") String userId,
            @RequestHeader("User-Role") String userRole) {

        log.info("Received registerUser request: {}", request);
        ResponseDto<UserAccountDataResponse> response = userService.registerUser(request, userId, userRole);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * Authenticates a user and returns login tokens and profile data wrapped in a {@link ResponseDto}.
     * When {@link LoginRequest#getPayload()} is set, the body is treated as an RSA-encrypted JSON blob
     * that is decrypted and deserialized into {@link LoginRequest} before credentials are validated.
     * Client IP and {@code User-Agent} are taken from {@code httpRequest} for auditing or risk signals.
     *
     * @param request     plain or RSA-wrapped login fields (e.g. email or user code, password, optional flags)
     * @param httpRequest current servlet request for remote address and {@code User-Agent} header
     * @return {@link ResponseEntity} from {@link UserService#login(LoginRequest, String, String)}
     */
    @PostMapping("/login")
    public ResponseEntity<ResponseDto<LoginResponseDto<LoginResponse>>> login(
            @RequestBody LoginRequest request,
            HttpServletRequest httpRequest) {
        log.info("Received login request");

        request = encryptedPayloadDecoder.decodeIfPresent(request, LoginRequest::getPayload, LoginRequest.class);

        log.debug("Processing login request for user: {}", request.getEmail() != null ? request.getEmail() : request.getUserCode());
        String ipAddress = httpRequest.getRemoteAddr();
        String userAgent = httpRequest.getHeader("User-Agent");
        String appVersion = extractAppVersion(httpRequest);
        // forcedLogin is carried via request body only; service reads it from request
        return userService.login(request, ipAddress, userAgent, appVersion);
    }

    @PostMapping("/logout")
    public ResponseDto<String> logout(@RequestHeader("Authorization") String authHeader) {
        String token = authHeader.startsWith("Bearer ") ? authHeader.substring(7) : authHeader;
        return userService.logout(token);
    }
    
    /**
     * Retrieves a paginated, filterable list of employees/users with support for
     * role, status, employment type, restaurant filters, and soft-delete flag.
     *
     * @param page              page number (1-based; first page is 1)
     * @param size              page size
     * @param roleId            optional role identifier to filter users
     * @param status            optional user status filter
     * @param employmentType    optional employment type filter
     * @param search            optional search keyword
     * @param sortBy            field name to sort by, defaults to {@code createdAt}
     * @param direction         sort direction, defaults to {@code DESC}
     * @param restaurantStatus  optional restaurant status filter
     * @param restaurantId      optional restaurant identifier to filter users
     * @param restaurantGroupId optional restaurant group identifier to filter users
     * @param localeHeader      optional locale header for localized responses
     * @param isDeleted         optional flag to include soft-deleted users
     * @return {@link ResponseEntity} with {@link ResponseDto} containing {@link UserListResponse}
     */
    @GetMapping
    public ResponseEntity<ResponseDto<UserListResponse>> getEmployees(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) UUID roleId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String employmentType,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "DESC") Sort.Direction direction,
            @RequestParam(required = false) String restaurantStatus,
            @RequestParam(required = false) UUID restaurantId,
            @RequestParam(required = false) UUID restaurantGroupId,
            @RequestHeader(value = "locale", required = false) String localeHeader,
            @RequestParam(required = false) Boolean isDeleted
            ) {

        log.info("Query parameters - page: {}, size: {}, roleId: {}, status: {}, employmentType: {}, search: {}, sortBy: {}, direction: {}, restaurantStatus: {}, restaurantId: {}, restaurantGroupId: {}, locale: {}, isDeleted: {}",
                page, size, roleId, status, employmentType, search, sortBy, direction, restaurantStatus, restaurantId, restaurantGroupId, localeHeader, isDeleted);

        ResponseDto<UserListResponse> response = userService.getEmployees(page, size, roleId, status, employmentType, search, sortBy, direction, restaurantStatus, restaurantId, restaurantGroupId, localeHeader, isDeleted);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{userId}")
    public ResponseEntity<ResponseDto<UserAccountDataResponse>> getUserAccountDetails(@PathVariable UUID userId) {
        ResponseDto<UserAccountDataResponse> response = userService.getUserAccountDetails(userId);
        return ResponseEntity.ok(response);
    }

    @PutMapping("/{userId}")
    @Operation(summary = "Update user", description = "Updates user details. For CASHIER, WAITER, or KDS roles, creates an approval request. For other roles, updates directly.")
    public ResponseEntity<ResponseDto<UserAccountDataResponse>> updateUser(
            @PathVariable UUID userId,
            @RequestBody UpdateUserRequest request,
            @RequestHeader("User-ID") String updaterId,
            @RequestHeader("User-Role") String updaterRole) {

        ResponseDto<UserAccountDataResponse> response = userService.updateUser(userId, request, updaterId, updaterRole);
        return ResponseEntity.ok(response);
    }

    @PutMapping("/{userId}/preferred-language")
    public ResponseEntity<ResponseDto<UserDataResponse>> updatePreferredLanguage(
            @PathVariable UUID userId,
            @Valid @RequestBody UpdatePreferredLanguageRequest request,
            @RequestHeader("User-ID") String updaterId) {

        log.info("Received update preferred language request for user ID: {} with language: {}", userId, request.getLanguageCode());
        ResponseDto<UserDataResponse> response = userService.updatePreferredLanguage(userId, request, updaterId);
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{userId}")
    public ResponseEntity<ResponseDto<Void>> deleteUser(
            @PathVariable UUID userId,
            @RequestHeader("User-ID") String deleterId,
            @RequestHeader("User-Role") String deleterRole,
            @RequestHeader(value = "locale", defaultValue = "en") String locale) {

        log.info("Received delete user request for userId: {} with locale: {}", userId, locale);
        ResponseDto<Void> response = userService.deleteUser(userId, deleterId, deleterRole, locale);
        return ResponseEntity.ok(response);
    }

    /**
     * Deletes multiple users in a single request based on the list of user IDs provided,
     * restricted to HQ_ADMIN role.
     *
     * @param request    the bulk delete request containing user IDs and delete reason
     * @param deleterId  the ID of the user performing the deletion
     * @param deleterRole the role of the user performing the deletion
     * @return {@link ResponseEntity} with {@link ResponseDto} indicating the result of the operation
     */
    @DeleteMapping("/bulk")
    @Operation(summary = "Delete multiple users", description = "Delete multiple users by their IDs. Only HQ_ADMIN can perform this action.")
    public ResponseEntity<ResponseDto<Void>> deleteMultipleUsers(
            @Valid @RequestBody BulkDeleteUsersRequest request,
            @RequestHeader("User-ID") String deleterId,
            @RequestHeader("User-Role") String deleterRole) {

        log.info("Received bulk delete request for {} users by user: {} with role: {}", 
                request.getUserIds().size(), deleterId, deleterRole);
        ResponseDto<Void> response = userService.deleteMultipleUsers(request.getUserIds(), request.getDeletedReason(), deleterId, deleterRole);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/validate-session")
    public ResponseEntity<Void> validateSession(
        @RequestHeader("Authorization") String authHeader,
        @RequestHeader(value = HEADER_APP_TYPE, required = false) String appType,
        @RequestHeader(value = HEADER_APP_VERSION, required = false) String appVersion,
        @RequestHeader(value = "locale", defaultValue = "en") String locale) {
            log.info("API hit: GET /api/v1/users/validate-session");
            userService.validateSession(authHeader.substring(7), locale, appType, appVersion);
        return ResponseEntity.ok().build();

    }

    private String extractAppVersion(HttpServletRequest request) {
        String waiter = request.getHeader(HEADER_WAITER_APP_VERSION);
        if (waiter != null && !waiter.isBlank()) {
            return waiter.trim();
        }

        String cashier = request.getHeader(HEADER_CASHIER_APP_VERSION);
        if (cashier != null && !cashier.isBlank()) {
            return cashier.trim();
        }

        String kds = request.getHeader(HEADER_KDS_APP_VERSION);
        if (kds != null && !kds.isBlank()) {
            return kds.trim();
        }

        String generic = request.getHeader(HEADER_APP_VERSION);
        if (generic != null && !generic.isBlank()) {
            return generic.trim();
        }

        return null;
    }

    /**
     * Unassigns a restaurant from a user, effectively removing the relationship
     * between the user and their currently assigned restaurant.
     *
     * @param userId     the ID of the user whose restaurant assignment is being removed
     * @param updaterId  the ID of the user performing the operation
     * @param updaterRole the role of the user performing the operation
     * @return {@link ResponseEntity} with {@link ResponseDto} containing unassignment details
     */
    @DeleteMapping("/{userId}/restaurant")
    public ResponseEntity<ResponseDto<UserRestaurantUnassignResponse>> unassignRestaurant(
            @PathVariable UUID userId,
            @RequestHeader("User-Id") String updaterId,
            @RequestHeader("User-Role") String updaterRole) {

        log.info("Request received to unassign restaurant for User ID: {} by Updater ID: {} with role: {}",
                userId, updaterId, updaterRole);
        ResponseDto<UserRestaurantUnassignResponse> response = userService.unassignRestaurantFromUser(userId, updaterId, updaterRole);
        log.info("Successfully unassigned restaurant for User ID: {}", userId);
        return ResponseEntity.ok(response);
    }




    /**
     * Handles bulk user upload via CSV (and optional image ZIP), including validation
     * of the uploaded file and delegating processing to the bulk upload service.
     *
     * @param file         the primary CSV file containing user data
     * @param imageZipFile optional ZIP file containing related images
     * @param action       optional upload action type
     * @param utfType      character encoding type for the CSV, defaults to UTF_8
     * @param language     optional language code for processing messages
     * @param userId       ID of the user initiating the bulk upload
     * @param userRole     role of the user initiating the bulk upload
     * @param authHeader   authorization header containing the bearer token
     * @param request      the current HTTP servlet request (for locale and context)
     * @return {@link ResponseEntity} with {@link ResponseDto} containing {@link BulkUpload} metadata
     * @throws IOException if reading the uploaded files fails
     */
    @PostMapping("/bulkUpload")
    public ResponseEntity<ResponseDto<BulkUpload>> saveBulkUpload(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "imageZipFile", required = false) MultipartFile imageZipFile,
            @RequestParam(value = "upload", required = false) String action,
            @RequestParam(value = "utf_type", defaultValue = "UTF_8") String utfType,
            @RequestParam(value = "language", required = false) String language,
            @RequestHeader("User-ID") String userId,
            @RequestHeader("User-Role") String userRole,
            @RequestHeader("Authorization") String authHeader,
            HttpServletRequest request) throws IOException{

        log.info("Bulk upload request received. File: {}, Size: {} bytes", 
            file.getOriginalFilename(), file.getSize());
        
        try {
            // Validate file BEFORE processing - this must throw exception if validation fails
            csvFileUploadValidator.validate(file, language);
            log.info("File validation passed for: {}", file.getOriginalFilename());
        } catch (com.gulfnet.shared_library.exception.BadRequestException e) {
            log.error("File validation failed for: {}. Error: {}", file.getOriginalFilename(), e.getMessage());
            throw e; // Re-throw to ensure it's handled by exception handler
        } catch (org.springframework.web.server.ResponseStatusException e) {
            log.error("File validation failed with ResponseStatusException for: {}. Error: {}", 
                file.getOriginalFilename(), e.getReason());
            throw e; // Re-throw to ensure it's handled by exception handler
        } catch (Exception e) {
            log.error("Unexpected error during file validation for: {}. Error: {}", 
                file.getOriginalFilename(), e.getMessage(), e);
            throw new com.gulfnet.shared_library.exception.BadRequestException(
                "File validation failed: " + e.getMessage());
        }

        String localeHeader = request.getHeader("locale");
        ResponseDto<BulkUpload> response = userService.processBulkUpload(file, imageZipFile, action, utfType, language, userId, userRole, localeHeader);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(response);
    }

    
    @GetMapping("/bulkUpload/template")
    public ResponseEntity<Void> downloadBulkUploadTemplate(
            @RequestHeader("User-ID") String userId,
            @RequestHeader("User-Role") String userRole,
            @RequestHeader("Authorization") String authHeader,
            @RequestHeader(value = "locale", defaultValue = "en") String locale,
            HttpServletResponse response) throws IOException {
        // Set locale context for this request
        LocaleContextHolder.setLocale(new Locale(locale));
        return bulkUserUploadService.downloadTemplate(response);
    }


 
    /**
     * Retrieves a paginated list of bulk upload operations with optional filters
     * for status, search keyword, sort options, and upload type.
     *
     * @param status       optional bulk upload status filter
     * @param page         page number, defaults to 1
     * @param size         optional page size; if null, all records are returned
     * @param search       optional search keyword
     * @param sortBy       field name to sort by, defaults to {@code createdAt}
     * @param sortDirection sort direction, defaults to {@code DESC}
     * @param uploadType   optional upload type filter
     * @return {@link ResponseEntity} with {@link ResponseDto} containing {@link BulkUploadListResponse}
     */
    @GetMapping("/bulkuploaddetails")
    public ResponseEntity<ResponseDto<BulkUploadListResponse>> getBulkUploads(
            @RequestParam(required = false) BulkUploadStatus status,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(required = false) Integer size,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "DESC") String sortDirection,
            @RequestParam(required = false) UploadType uploadType) {
        
        int pageSize = (size != null) ? size : Integer.MAX_VALUE;
    
        ResponseDto<BulkUploadListResponse> response = 
            bulkUserUploadService.getBulkUploads(status, page, pageSize, search, sortBy, sortDirection, uploadType);
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/profile-update-request")
    @Operation(summary = "Cancel profile update request", description = "Allows users to cancel their pending profile update request")
    public ResponseEntity<ResponseDto<Void>> cancelProfileUpdateRequest(
            @RequestHeader("User-ID") String userId) {
        
        log.info("Received request to cancel profile update request from user: {}", userId);
        ResponseDto<Void> response = userService.cancelProfileUpdateRequest(userId);
        return ResponseEntity.ok(response);
    }

    /**
     * Approves or declines a unified request (e.g., profile update or additional discount)
     * based on the action provided in the request body.
     *
     * @param requestId   the ID of the request to approve or decline
     * @param request     the approval request payload including the action and comments
     * @param managerId   the ID of the manager performing the action
     * @param managerRole the role of the manager performing the action
     * @return {@link ResponseEntity} with {@link ResponseDto} containing {@link RequestApprovalResponse}
     */
    @PutMapping("/requests/{requestId}/approve")
    @Operation(summary = "Approve or decline any request", description = "Allows managers to approve or decline any request (profile update or additional discount). The system automatically detects the request type.")
    public ResponseEntity<ResponseDto<RequestApprovalResponse>> approveOrDeclineRequest(
            @PathVariable UUID requestId,
            @Valid @RequestBody ProfileUpdateApprovalRequest request,
            @RequestHeader("User-ID") String managerId,
            @RequestHeader("User-Role") String managerRole) {
        
        log.info("Received request to {} request for ID: {} by manager: {}", 
                request.getAction(), requestId, managerId);
        ResponseDto<RequestApprovalResponse> response = userService.approveOrDeclineRequest(requestId, request, managerId, managerRole);
        return ResponseEntity.ok(response);
    }

    /**
     * Retrieves a paginated list of unified requests for listing view with filters
     * for status, request type, and sorting options, scoped by user role and ID.
     *
     * @param page          page number, defaults to 1
     * @param size          page size, defaults to 10
     * @param status        optional request status filter
     * @param requestType   optional request type filter
     * @param sortBy        field name to sort by, defaults to {@code date}
     * @param sortDirection sort direction, defaults to {@code DESC}
     * @param userRole      role of the requesting user
     * @param userId        ID of the requesting user (required)
     * @return {@link ResponseEntity} with {@link ResponseDto} containing {@link UnifiedRequestListResponse}
     */
    @GetMapping("/requests")
    @Operation(summary = "Get all requests - listing view", description = "Get all requests with simplified fields for listing view. MANAGER and HQ_ADMIN can see all request types. CASHIER can only see refund requests (filtered by their restaurant).")
    public ResponseEntity<ResponseDto<UnifiedRequestListResponse>> getAllRequests(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) RequestStatus status,
            @RequestParam(required = false) String requestType,
            @RequestParam(defaultValue = "date") String sortBy,
            @RequestParam(defaultValue = "DESC") String sortDirection,
            @RequestHeader("User-Role") String userRole,
            @RequestHeader("User-ID") String userId) {
        
        log.info("Received request to get all requests - page: {}, size: {}, status: {}, requestType: {}, sortBy: {}, sortDirection: {}, role: {}, userId: {}", 
                page, size, status, requestType, sortBy, sortDirection, userRole, userId);
        ResponseDto<UnifiedRequestListResponse> response = userService.getAllPendingRequests(page, size, status, requestType, sortBy, sortDirection, userRole, userId);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/requests/{requestId}/details")
    @Operation(summary = "Get request details by ID", description = "Get full details of a request (profile update or additional discount) by ID. The system automatically detects the request type. (Manager/HQ_ADMIN only)")
    public ResponseEntity<ResponseDto<RequestDetailsResponse>> getRequestDetails(
            @PathVariable UUID requestId,
            @RequestHeader("User-Role") String userRole,
            @RequestHeader("User-ID") String userId) {
        
        log.info("Received request to get request details for ID: {} by role: {}, userId: {}", requestId, userRole, userId);
        ResponseDto<RequestDetailsResponse> response = userService.getRequestDetails(requestId, userRole, userId);
        return ResponseEntity.ok(response);
    }

    /**
     * Retrieves paginated audit logs with optional filters for manager, employee,
     * restaurant, action type, and date range.
     *
     * @param page        page number (1-based; first page is 1), defaults to 1
     * @param size        page size, defaults to 10
     * @param managerId   optional manager ID filter
     * @param employeeId  optional employee ID filter
     * @param restaurantId optional restaurant ID filter
     * @param action      optional action filter
     * @param startDate   optional start date filter (inclusive)
     * @param endDate     optional end date filter (inclusive)
     * @param userRole    role of the requesting user
     * @return {@link ResponseEntity} with {@link ResponseDto} containing paged {@link AuditLogging} records
     */
    @GetMapping("/audit-logs")
    @Operation(summary = "Get audit logs", description = "Retrieve audit logs with filtering options (Manager/HQ_ADMIN only)")
    public ResponseEntity<ResponseDto<Page<AuditLogging>>> getAuditLogs(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) UUID managerId,
            @RequestParam(required = false) UUID employeeId,
            @RequestParam(required = false) UUID restaurantId,
            @RequestParam(required = false) String action,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate,
            @RequestHeader("User-Role") String userRole) {
        
        log.info("Received request to get audit logs - page: {}, size: {}, managerId: {}, employeeId: {}, restaurantId: {}, action: {}", 
                page, size, managerId, employeeId, restaurantId, action);
        
        ResponseDto<Page<AuditLogging>> response = auditLoggingService.getAuditLogs(
                page, size, managerId, employeeId, restaurantId, action, startDate, endDate, userRole);
        return ResponseEntity.ok(response);
    }


    @PutMapping("/{userId}/device-token")
    @Operation(summary = "Update device token", description = "Update FCM device token for push notifications")
    public ResponseEntity<ResponseDto<DeviceTokenResponse>> updateDeviceToken(
            @PathVariable UUID userId,
            @Valid @RequestBody UpdateDeviceTokenRequest request,
            @RequestHeader("User-ID") String updaterId) {

        log.info("Received update device token request for user ID: {} with device type: {}", userId, request.getDeviceType());
        ResponseDto<DeviceTokenResponse> response = userService.updateDeviceToken(userId, request, updaterId);
        return ResponseEntity.ok(response);
    }


    @GetMapping("/request-types")
    @Operation(summary = "Get all request types", description = "Get all available request types for filtering requests")
    public ResponseEntity<ResponseDto<RequestTypeListResponse>> getAllRequestTypes() {
        log.info("Received request to fetch all request types");
        ResponseDto<RequestTypeListResponse> response = userService.getAllRequestTypes();
        return ResponseEntity.ok(response);
    }


    @PostMapping("/check-email-availability")
    @Operation(summary = "Check email availability", description = "Check if an email address is available for registration. Returns error if email already exists.")
    public ResponseEntity<ResponseDto<EmailAvailabilityResponse>> checkEmailAvailability(
            @RequestBody EmailAvailabilityRequest request,
            @RequestHeader(value = "locale", defaultValue = "en") String locale) {
        log.info("Received request to check email availability for: {}", request.getEmail());
        ResponseDto<EmailAvailabilityResponse> response = userService.checkEmailAvailability(request, locale);
        return ResponseEntity.ok(response);
    }

    @PutMapping("/restore")
    @Operation(summary = "Restore users", description = "Restore multiple users by changing isDeleted from true to false")
    public ResponseEntity<ResponseDto<Void>> restoreUsers(
            @Valid @RequestBody RestoreEntitiesRequest request,
            @RequestHeader("User-ID") String userId) {
        log.info("Received request to restore users: {}", request.getIds());
        ResponseDto<Void> response = userService.restoreUsers(request.getIds(), userId);
        return ResponseEntity.ok(response);
        
    }

} 

