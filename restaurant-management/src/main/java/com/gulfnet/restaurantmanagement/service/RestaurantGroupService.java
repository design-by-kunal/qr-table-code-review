package com.gulfnet.restaurantmanagement.service;

import com.gulfnet.shared_library.model.response.dto.RestaurantGroupDTO;
import com.gulfnet.shared_library.model.response.dto.ResponseDto;
import com.gulfnet.shared_library.model.response.dto.RestaurantGroupResponse;
import com.gulfnet.shared_library.model.response.dto.RestaurantGroupListResponse;
import com.gulfnet.shared_library.model.response.dto.RestaurantListResponse;
import com.gulfnet.shared_library.model.response.dto.RestaurantMenuListResponse;
import org.springframework.data.domain.Sort;
import java.util.UUID;
import com.gulfnet.shared_library.model.request.AssignMenuToRestaurantGroupRequest;
import com.gulfnet.shared_library.model.request.AssignRestaurantsToGroupRequest;
import java.util.List;

public interface RestaurantGroupService {
    ResponseDto<RestaurantGroupDTO<RestaurantGroupResponse>> saveGroup(String userId, RestaurantGroupResponse dto);
    ResponseDto<RestaurantGroupDTO<RestaurantGroupResponse>> updateGroup(UUID id, String userId, RestaurantGroupResponse dto);
    ResponseDto<RestaurantGroupDTO<RestaurantGroupResponse>> getGroup(UUID id, String userId, Boolean includeDeleted);
    ResponseDto<RestaurantGroupDTO<RestaurantGroupResponse>> deleteGroup(UUID id, String userId);
    ResponseDto<RestaurantGroupListResponse> getRestaurantGroups(
        Integer page, 
        Integer size, 
        String status, 
        String search, 
        String sortBy, 
        Sort.Direction direction,
        String locale
    );
    ResponseDto<RestaurantMenuListResponse> getRestaurantGroupsByGroupIdAndMenuId(UUID groupId, UUID menuId, Integer page, Integer size, String status, String search, String sortBy, Sort.Direction direction);
    ResponseDto<Void> updateRestaurantMenuAssignments(AssignMenuToRestaurantGroupRequest request, String userId);
    
    // New methods for restaurant assignment
    ResponseDto<Void> assignRestaurantsToGroup(AssignRestaurantsToGroupRequest request, String userId);
    ResponseDto<Void> unassignRestaurantsFromGroup(AssignRestaurantsToGroupRequest request, String userId);
    
    // Method to get unassigned restaurants
    ResponseDto<RestaurantListResponse> getUnassignedRestaurants(Integer page, Integer size, String status, String search, String sortBy, Sort.Direction direction);

    // Lightweight listing: group data only with restaurant count
    ResponseDto<RestaurantGroupListResponse> getRestaurantGroupsLite(
        Integer page,
        Integer size,
        String status,
        String search,
        String sortBy,
        Sort.Direction direction,
        String locale,
        Boolean isDeleted
    );

    ResponseDto<Void> restoreRestaurantGroups(List<UUID> ids, String userId);
} 