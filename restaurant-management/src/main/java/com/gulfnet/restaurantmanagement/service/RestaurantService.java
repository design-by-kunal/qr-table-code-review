package com.gulfnet.restaurantmanagement.service;

import com.gulfnet.shared_library.model.request.AssignEmployeesRequest;
import com.gulfnet.shared_library.model.request.RestaurantRequest;
import com.gulfnet.shared_library.model.request.UpdateRestaurantAccountSettingsRequest;
import com.gulfnet.shared_library.model.response.dto.RestaurantResponse;
import com.gulfnet.shared_library.model.response.dto.EmployeeAssignmentListResponse;
import com.gulfnet.shared_library.model.response.dto.ResponseDto;
import com.gulfnet.shared_library.model.response.dto.RestaurantDto;
import com.gulfnet.shared_library.model.response.dto.RestaurantListResponse;
import com.gulfnet.shared_library.model.response.dto.CategoryListResponse;
import com.gulfnet.shared_library.model.response.dto.MenuCategorySummaryResponse;
import com.gulfnet.shared_library.model.response.dto.CodeUniquenessResponse;
import com.gulfnet.shared_library.model.response.dto.RestaurantAccountSettingsResponseDto;
import java.util.UUID;
import java.util.List;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;


public interface RestaurantService {

   ResponseDto<RestaurantDto<RestaurantResponse>> saveRestaurant(String userId, RestaurantRequest request);
   ResponseDto<RestaurantDto<RestaurantResponse>> getRestaurantById(UUID restaurantId, String userId, Boolean includeDeleted);
   /**
    * Retrieves a paginated and filterable list of restaurants.
    * Supports filtering by restaurant group, status, menu assignment, and text search.
    *
    * @param page            page number (1-based)
    * @param size            page size
    * @param restaurantGroupId optional filter by restaurant group ID
    * @param restaurantId    optional filter by specific restaurant ID
    * @param status          optional filter by status
    * @param search          optional search term for text search
    * @param hasMenuAssigned optional filter by menu assignment status
    * @param sortBy          field to sort by
    * @param direction       sort direction (ASC or DESC)
    * @param locale          locale code for localized responses
    * @param userId          ID of the user requesting the list
    * @param userRole        role of the user requesting the list
    * @param isDeleted       optional filter by deleted status
    * @return {@link ResponseDto} containing paginated list of restaurants
    */
   ResponseDto<RestaurantListResponse> getRestaurants(
    Integer page, 
    Integer size, 
    UUID restaurantGroupId, 
    UUID restaurantId,
    String status, 
    String search, 
    Boolean hasMenuAssigned,
    String sortBy, 
    Sort.Direction direction,
    String locale,
    String userId,
    String userRole,
    Boolean isDeleted
);
   ResponseDto<String> deleteRestaurant(UUID id, String userId);

   ResponseDto<RestaurantDto<RestaurantResponse>> updateRestaurant(UUID id, String userId, RestaurantRequest request);
   
       // Employee assignment methods
   ResponseDto<EmployeeAssignmentListResponse> assignEmployeesToRestaurant(AssignEmployeesRequest request);

   // New: fetch active categories for a restaurant's assigned menu
   ResponseDto<MenuCategorySummaryResponse> getActiveCategoriesForRestaurant(UUID restaurantId);

   // Remove assigned menu from restaurant
   ResponseDto<Void> removeMenuFromRestaurant(UUID restaurantId, UUID menuId, String locale);

   // Check code uniqueness (user_code or restaurant_code)
   ResponseDto<CodeUniquenessResponse> checkCodeUniqueness(String type, String value, UUID excludeId, String locale);

   // Restaurant-specific account settings
   ResponseDto<RestaurantAccountSettingsResponseDto> getRestaurantAccountSettings(UUID restaurantId);
   ResponseDto<RestaurantAccountSettingsResponseDto> updateRestaurantAccountSettings(UUID restaurantId, UpdateRestaurantAccountSettingsRequest request, String userId);

   ResponseDto<Void> restoreRestaurants(List<UUID> ids, String userId);

}

