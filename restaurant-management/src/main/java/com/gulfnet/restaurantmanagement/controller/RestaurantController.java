package com.gulfnet.restaurantmanagement.controller;

import com.gulfnet.restaurantmanagement.service.BulkItemUploadService;
import com.gulfnet.restaurantmanagement.service.BulkRestaurantUploadService;
import com.gulfnet.restaurantmanagement.service.MenuStructureService;
import com.gulfnet.restaurantmanagement.service.RestaurantService;
import com.gulfnet.restaurantmanagement.validator.RestaurantCsvFileValidator;
import com.gulfnet.shared_library.entity.BulkUpload;
import com.gulfnet.shared_library.enums.EntityStatus;
import com.gulfnet.shared_library.model.request.AssignEmployeesRequest;
import com.gulfnet.shared_library.model.request.RestaurantRequest;
import com.gulfnet.shared_library.model.request.UpdateRestaurantAccountSettingsRequest;
import com.gulfnet.shared_library.model.request.RestoreEntitiesRequest;
import com.gulfnet.shared_library.model.response.dto.RestaurantResponse;
import com.gulfnet.shared_library.model.response.dto.BulkUploadWithPresignedUrls;
import com.gulfnet.shared_library.model.response.dto.EmployeeAssignmentListResponse;
import com.gulfnet.shared_library.model.response.dto.ResponseDto;
import com.gulfnet.shared_library.model.response.dto.RestaurantDto;
import com.gulfnet.shared_library.model.response.dto.RestaurantListResponse;
import com.gulfnet.shared_library.model.response.dto.CategoryListResponse;
import com.gulfnet.shared_library.model.response.dto.MenuDetailStructureDto;
import com.gulfnet.shared_library.model.response.dto.MenuCategorySummaryResponse;
import com.gulfnet.shared_library.model.response.dto.CodeUniquenessResponse;
import com.gulfnet.shared_library.model.response.dto.RestaurantAccountSettingsResponseDto;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;

import java.util.UUID;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.data.domain.Page;

import java.io.IOException;
import java.util.List;
import java.util.Locale;

import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import lombok.extern.slf4j.Slf4j;


@Slf4j
@RestController
@RequestMapping("/api/v1/restaurant")
public class RestaurantController {

    @Autowired
    private RestaurantService restaurantService;

    @Autowired
    private MessageSource messageSource;

    @Autowired
    private BulkRestaurantUploadService bulkRestaurantUploadService;

    @Autowired
    private RestaurantCsvFileValidator restaurantCsvFileValidator;

    @Autowired
    private MenuStructureService menuStructureService;



    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ResponseEntity<ResponseDto<RestaurantDto<RestaurantResponse>>> saveRestaurant(
            @Valid @RequestBody RestaurantRequest dto,
            @RequestHeader("User-ID") String userId) {
        ResponseDto<RestaurantDto<RestaurantResponse>> saved = restaurantService.saveRestaurant(userId, dto);
        return ResponseEntity.status(HttpStatus.CREATED).body(saved);
    }

    @GetMapping("/{id}")
    public ResponseEntity<ResponseDto<RestaurantDto<RestaurantResponse>>> getRestaurant(
            @PathVariable UUID id,
            @RequestHeader("User-ID") String userId,
            @RequestParam(value = "includeDeleted", defaultValue = "false") Boolean includeDeleted) {
        ResponseDto<RestaurantDto<RestaurantResponse>> response = restaurantService.getRestaurantById(id, userId, includeDeleted);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{id}/active-categories")
    public ResponseEntity<ResponseDto<MenuCategorySummaryResponse>> getActiveCategoriesForRestaurant(
            @PathVariable UUID id) {
        ResponseDto<MenuCategorySummaryResponse> response = restaurantService.getActiveCategoriesForRestaurant(id);
        return ResponseEntity.ok(response);
    }

    /**
     * Retrieves a paginated and filterable list of restaurants.
     * Supports filtering by restaurant group, status, menu assignment, deletion status, and text search.
     * Results are filtered based on user role and access permissions.
     *
     * @param page             optional page number for pagination
     * @param size             optional page size for pagination
     * @param restaurantGroupId optional filter by restaurant group ID
     * @param restaurantId     optional filter by specific restaurant ID
     * @param status           optional filter by restaurant status
     * @param search           optional search term for text search
     * @param hasMenuAssigned  optional filter by whether restaurant has menu assigned
     * @param sortBy           field to sort by (default: "createdAt")
     * @param direction        sort direction (default: DESC)
     * @param locale           locale code for localized responses (default: "en")
     * @param userId           the user ID from the request header (required)
     * @param userRole         the user role from the request header (required)
     * @param isDeleted        optional filter by deletion status
     * @return response containing paginated list of restaurants with filters applied
     */
    @GetMapping
    public ResponseEntity<ResponseDto<RestaurantListResponse>> getRestaurants(
        @RequestParam(required = false) Integer page,
        @RequestParam(required = false) Integer size,
        @RequestParam(required = false) UUID restaurantGroupId,
        @RequestParam(required = false) UUID restaurantId,
        @RequestParam(required = false) String status,
        @RequestParam(required = false) String search,
        @RequestParam(required = false) Boolean hasMenuAssigned,
        @RequestParam(defaultValue = "createdAt") String sortBy,
        @RequestParam(defaultValue = "DESC") Sort.Direction direction,
        @RequestHeader(value = "locale", defaultValue = "en") String locale,
        @RequestHeader("User-ID") String userId,
        @RequestHeader("User-Role") String userRole,
        @RequestParam(required = false) Boolean isDeleted
        ) {
    ResponseDto<RestaurantListResponse> response = restaurantService.getRestaurants(
        page, size, restaurantGroupId, restaurantId, status, search, hasMenuAssigned, sortBy, direction, locale, userId, userRole, isDeleted);
    return ResponseEntity.ok(response);
}

    @PutMapping("/{id}")
    public ResponseEntity<ResponseDto<RestaurantDto<RestaurantResponse>>> updateRestaurant(
            @PathVariable UUID id, 
            @Valid @RequestBody RestaurantRequest dto,
            @RequestHeader("User-ID") String userId) {
        ResponseDto<RestaurantDto<RestaurantResponse>> updated = restaurantService.updateRestaurant(id, userId, dto);
        return ResponseEntity.ok(updated);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ResponseDto<String>> deleteRestaurant(
            @PathVariable UUID id,
            @RequestHeader("User-ID") String userId) {
        ResponseDto<String> response = restaurantService.deleteRestaurant(id, userId);
        return ResponseEntity.ok(response);
    }

    /**
     * Assign employees to a restaurant with specific roles
     * 
     * @param request The assignment request containing restaurant ID and employee assignments
     * @return Response with assignment results
     */
    @PostMapping("/assign-employees")
    public ResponseEntity<ResponseDto<EmployeeAssignmentListResponse>> assignEmployeesToRestaurant(
            @Valid @RequestBody AssignEmployeesRequest request) {
        
        ResponseDto<EmployeeAssignmentListResponse> response = restaurantService.assignEmployeesToRestaurant(request);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/bulkUpload/template")
    public ResponseEntity<Void> downloadBulkUploadTemplate(HttpServletResponse response) throws IOException {
        return bulkRestaurantUploadService.downloadRestaurantTemplate(response);
    }

    @PostMapping("/bulkUpload")
    public ResponseEntity<ResponseDto<BulkUploadWithPresignedUrls>> uploadRestaurants(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "imageZipFile", required = false) MultipartFile imageZipFile,
            @RequestHeader("User-ID") String userId) throws IOException {

        restaurantCsvFileValidator.validate(file);
        ResponseDto<BulkUploadWithPresignedUrls> response = bulkRestaurantUploadService.processRestaurantBulkUpload(file, imageZipFile, userId);
        return ResponseEntity.ok(response);
    }

    /**
     * Retrieves all menu structures assigned to a specific restaurant.
     * Supports filtering by status and text search.
     *
     * @param id     the UUID of the restaurant to get menus for
     * @param status optional filter by menu status (default: ACTIVE)
     * @param search optional search term for text search
     * @param locale locale code for localized responses (default: "en")
     * @return response containing list of menu detail structures for the restaurant
     */
    @GetMapping("/{id}/menu")
    public ResponseEntity<ResponseDto<List<MenuDetailStructureDto>>> getMenusByRestaurantId(
            @PathVariable("id") UUID id,
            @RequestParam(name = "status", required = false, defaultValue = "ACTIVE") EntityStatus status,
            @RequestParam(name = "search", required = false) String search,
            @RequestHeader(value = "locale", defaultValue = "en") String locale) {

        ResponseDto<List<MenuDetailStructureDto>> response = menuStructureService
                .getAllMenuStructuresByRestaurantId(id, status, search, locale);

        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{restaurantId}/menu/{menuId}")
    public ResponseEntity<ResponseDto<Void>> removeMenuFromRestaurant(
            @PathVariable("restaurantId") UUID restaurantId,
            @PathVariable("menuId") UUID menuId,
            @RequestHeader(value = "locale", defaultValue = "en") String locale) {

        ResponseDto<Void> response = restaurantService.removeMenuFromRestaurant(restaurantId, menuId, locale);
        return ResponseEntity.ok(response);
    }

    /**
     * Check code uniqueness for user_code or restaurant_code
     * 
     * @param type The type of code to check: "user_code" or "restaurant_code"
     * @param value The code value to check
     * @param excludeId Optional UUID to exclude from check (useful during updates)
     * @param locale Locale for messages
     * @return Response indicating if the code is available
     */
    @GetMapping("/validation/check-uniqueness")
    public ResponseEntity<ResponseDto<CodeUniquenessResponse>> checkCodeUniqueness(
            @RequestParam("type") String type,
            @RequestParam("value") String value,
            @RequestParam(value = "excludeId", required = false) UUID excludeId,
            @RequestHeader(value = "locale", defaultValue = "en") String locale) {

        ResponseDto<CodeUniquenessResponse> response = restaurantService.checkCodeUniqueness(type, value, excludeId, locale);
        return ResponseEntity.ok(response);
    }

    /**
     * Get restaurant-specific account settings (reset times)
     * 
     * @param id Restaurant ID
     * @return Restaurant account settings
     */
    @GetMapping("/{id}/account-settings")
    public ResponseEntity<ResponseDto<RestaurantAccountSettingsResponseDto>> getRestaurantAccountSettings(
            @PathVariable UUID id) {
        ResponseDto<RestaurantAccountSettingsResponseDto> response = restaurantService.getRestaurantAccountSettings(id);
        return ResponseEntity.ok(response);
    }

    /**
     * Update restaurant-specific account settings (reset times)
     * 
     * @param id Restaurant ID
     * @param request Update request containing reset times
     * @param userId User ID from header
     * @return Updated restaurant account settings
     */
    @PutMapping("/{id}/account-settings")
    public ResponseEntity<ResponseDto<RestaurantAccountSettingsResponseDto>> updateRestaurantAccountSettings(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateRestaurantAccountSettingsRequest request,
            @RequestHeader("User-ID") String userId) {
        ResponseDto<RestaurantAccountSettingsResponseDto> response = restaurantService.updateRestaurantAccountSettings(id, request, userId);
        return ResponseEntity.ok(response);
    }

    @PutMapping("/restore")
    public ResponseEntity<ResponseDto<Void>> restoreRestaurants(
            @Valid @RequestBody RestoreEntitiesRequest request,
            @RequestHeader("User-ID") String userId) {
        ResponseDto<Void> response = restaurantService.restoreRestaurants(request.getIds(), userId);
        return ResponseEntity.ok(response);
    }

} 