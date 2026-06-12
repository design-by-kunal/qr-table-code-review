package com.gulfnet.restaurantmanagement.controller;

import com.gulfnet.restaurantmanagement.service.RestaurantGroupService;
import com.gulfnet.shared_library.model.response.dto.RestaurantGroupDTO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import com.gulfnet.shared_library.model.response.dto.ResponseDto;
import com.gulfnet.shared_library.model.response.dto.RestaurantGroupListResponse;
import com.gulfnet.shared_library.model.response.dto.RestaurantListResponse;
import com.gulfnet.shared_library.model.response.dto.RestaurantMenuListResponse;
import java.util.UUID;
import java.util.List;
import com.gulfnet.shared_library.model.response.dto.RestaurantGroupResponse;
import org.springframework.data.domain.Sort;
import com.gulfnet.shared_library.model.request.AssignMenuToRestaurantGroupRequest;
import com.gulfnet.shared_library.model.request.AssignRestaurantsToGroupRequest;
import com.gulfnet.shared_library.model.request.RestoreEntitiesRequest;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/restaurantgroup")
public class RestaurantGroupController {

    @Autowired
    private RestaurantGroupService restaurantGroupService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ResponseEntity<ResponseDto<RestaurantGroupDTO<RestaurantGroupResponse>>> saveGroup(
        @Valid @RequestBody RestaurantGroupResponse dto,
        @RequestHeader("User-ID") String userId) {

        ResponseDto<RestaurantGroupDTO<RestaurantGroupResponse>> saved = restaurantGroupService.saveGroup(userId, dto);
        return ResponseEntity.status(HttpStatus.CREATED).body(saved);
    }


    // PUT: Update a restaurant group and its translations
    @PutMapping("/{id}")
    public ResponseEntity<ResponseDto<RestaurantGroupDTO<RestaurantGroupResponse>>> updateGroup(
            @PathVariable UUID id, 
            @Valid @RequestBody RestaurantGroupResponse dto,
            @RequestHeader("User-ID") String userId) {
        ResponseDto<RestaurantGroupDTO<RestaurantGroupResponse>> updated = restaurantGroupService.updateGroup(id, userId, dto);
        return ResponseEntity.ok(updated);
    }

    // GET: Get a restaurant group with its translations
    @GetMapping("/{id}")
    public ResponseEntity<ResponseDto<RestaurantGroupDTO<RestaurantGroupResponse>>>getGroup(
            @PathVariable UUID id,
            @RequestHeader("User-ID") String userId,
            @RequestParam(value = "includeDeleted", defaultValue = "false") Boolean includeDeleted) {
        ResponseDto<RestaurantGroupDTO<RestaurantGroupResponse>> dto = restaurantGroupService.getGroup(id, userId, includeDeleted);
        return ResponseEntity.ok(dto);
    }

    /**
     * Retrieves a paginated and filterable list of restaurant groups.
     * Supports filtering by status and text search.
     *
     * @param page      optional page number for pagination
     * @param size      optional page size for pagination
     * @param status    optional filter by status
     * @param search    optional search term for text search
     * @param sortBy    field to sort by (default: "createdAt")
     * @param direction sort direction (default: DESC)
     * @param locale    locale code for localized responses (default: "en")
     * @return response containing paginated list of restaurant groups with filters applied
     */
    @GetMapping
    public ResponseEntity<ResponseDto<RestaurantGroupListResponse>> getRestaurantGroups(
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "DESC") Sort.Direction direction,
            @RequestHeader(value = "locale", defaultValue = "en") String locale
            ) {

        ResponseDto<RestaurantGroupListResponse> response = restaurantGroupService.getRestaurantGroups(
            page, size, status, search, sortBy, direction, locale);
        return ResponseEntity.ok(response);
    }

    /**
     * Retrieves a lightweight paginated and filterable list of restaurant groups.
     * Returns groups with restaurant count only (minimal data for performance).
     * Supports filtering by status, deletion status, and text search.
     *
     * @param page      optional page number for pagination
     * @param size      optional page size for pagination
     * @param status    optional filter by status
     * @param search    optional search term for text search
     * @param sortBy    field to sort by (default: "createdAt")
     * @param direction sort direction (default: DESC)
     * @param locale    locale code for localized responses (default: "en")
     * @param isDeleted optional filter by deletion status
     * @return response containing paginated list of restaurant groups with minimal data
     */
    @GetMapping("/lite")
    public ResponseEntity<ResponseDto<RestaurantGroupListResponse>> getRestaurantGroupsLite(
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "DESC") Sort.Direction direction,
            @RequestHeader(value = "locale", defaultValue = "en") String locale,
            @RequestParam(required = false) Boolean isDeleted
    ) {
        ResponseDto<RestaurantGroupListResponse> response = restaurantGroupService.getRestaurantGroupsLite(
            page, size, status, search, sortBy, direction, locale, isDeleted
        );
        return ResponseEntity.ok(response);
    }



    // DELETE: Delete a restaurant group and its translations
    @DeleteMapping("/{id}")
    public ResponseEntity<ResponseDto<RestaurantGroupDTO<RestaurantGroupResponse>>> deleteGroup(
            @PathVariable UUID id,
            @RequestHeader("User-ID") String userId) {
        ResponseDto<RestaurantGroupDTO<RestaurantGroupResponse>> response = restaurantGroupService.deleteGroup(id, userId);
        return ResponseEntity.ok(response);
    }

    /**
     * Retrieves restaurants assigned to a specific restaurant group and menu combination.
     * Returns a paginated and filterable list of restaurants with menu assignment details.
     *
     * @param groupId   the UUID of the restaurant group
     * @param menuId    the UUID of the menu
     * @param page      optional page number for pagination
     * @param size      optional page size for pagination
     * @param status    optional filter by restaurant status
     * @param search    optional search term for text search
     * @param sortBy    field to sort by (default: "createdAt")
     * @param direction sort direction (default: DESC)
     * @return response containing paginated list of restaurants with menu assignment details
     */
    @GetMapping("/{groupId}/menu/{menuId}/restaurants")
    public ResponseEntity<ResponseDto<RestaurantMenuListResponse>> getRestaurantGroupsByGroupIdAndMenuId(
            @PathVariable UUID groupId,
            @PathVariable UUID menuId,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "DESC") Sort.Direction direction) {

        ResponseDto<RestaurantMenuListResponse> response = restaurantGroupService.getRestaurantGroupsByGroupIdAndMenuId(
                groupId, menuId, page, size, status, search, sortBy, direction);
        return ResponseEntity.ok(response);
    }

    /**
     * Updates menu assignments for restaurants within a restaurant group.
     * Assigns or unassigns the specified menu to/from restaurants in the group.
     *
     * @param groupId the UUID of the restaurant group
     * @param menuId  the UUID of the menu to assign/unassign
     * @param request the request containing restaurant IDs and assignment action
     * @param userId  the user ID from the request header (required)
     * @return response indicating success of the update operation
     */
    @PutMapping("/{groupId}/menu/{menuId}/restaurants")
    public ResponseEntity<ResponseDto<Void>> updateRestaurantMenuAssignments(
            @PathVariable UUID groupId,
            @PathVariable UUID menuId,
            @RequestBody AssignMenuToRestaurantGroupRequest request,
            @RequestHeader("User-ID") String userId) {

        // Set the path variables to the request
        request.setRestaurantGroupId(groupId);
        request.setMenuId(menuId);

        ResponseDto<Void> response = restaurantGroupService.updateRestaurantMenuAssignments(request, userId);
        return ResponseEntity.ok(response);
    }

    /**
     * Assigns one or more restaurants to a restaurant group.
     *
     * @param groupId the UUID of the restaurant group to assign restaurants to
     * @param request the request containing restaurant IDs to assign
     * @param userId  the user ID from the request header (required)
     * @return response indicating success of the assignment operation
     */
    @PostMapping("/{groupId}/restaurants/assign")
    public ResponseEntity<ResponseDto<Void>> assignRestaurantsToGroup(
            @PathVariable UUID groupId,
            @Valid @RequestBody AssignRestaurantsToGroupRequest request,
            @RequestHeader("User-ID") String userId) {

        // Set the path variable to the request
        request.setRestaurantGroupId(groupId);

        ResponseDto<Void> response = restaurantGroupService.assignRestaurantsToGroup(request, userId);
        return ResponseEntity.ok(response);
    }

    /**
     * Unassigns one or more restaurants from a restaurant group.
     *
     * @param groupId the UUID of the restaurant group to unassign restaurants from
     * @param request the request containing restaurant IDs to unassign
     * @param userId  the user ID from the request header (required)
     * @return response indicating success of the unassignment operation
     */
    @DeleteMapping("/{groupId}/restaurants/unassign")
    public ResponseEntity<ResponseDto<Void>> unassignRestaurantsFromGroup(
            @PathVariable UUID groupId,
            @Valid @RequestBody AssignRestaurantsToGroupRequest request,
            @RequestHeader("User-ID") String userId) {

        // Set the path variable to the request
        request.setRestaurantGroupId(groupId);

        ResponseDto<Void> response = restaurantGroupService.unassignRestaurantsFromGroup(request, userId);
        return ResponseEntity.ok(response);
    }

    /**
     * Retrieves a paginated and filterable list of restaurants that are not assigned to any restaurant group.
     *
     * @param page      optional page number for pagination
     * @param size      optional page size for pagination
     * @param status    optional filter by restaurant status
     * @param search    optional search term for text search
     * @param sortBy    field to sort by (default: "createdAt")
     * @param direction sort direction (default: DESC)
     * @return response containing paginated list of unassigned restaurants with filters applied
     */
    @GetMapping("/restaurants/unassigned")
    public ResponseEntity<ResponseDto<RestaurantListResponse>> getUnassignedRestaurants(
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "DESC") Sort.Direction direction) {

        ResponseDto<RestaurantListResponse> response = restaurantGroupService.getUnassignedRestaurants(page, size, status, search, sortBy, direction);
        return ResponseEntity.ok(response);
    }

    @PutMapping("/restore")
    public ResponseEntity<ResponseDto<Void>> restoreRestaurantGroups(
            @Valid @RequestBody RestoreEntitiesRequest request,
            @RequestHeader("User-ID") String userId) {
        ResponseDto<Void> response = restaurantGroupService.restoreRestaurantGroups(request.getIds(), userId);
        return ResponseEntity.ok(response);
    }
}
