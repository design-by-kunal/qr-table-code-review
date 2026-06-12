package com.gulfnet.restaurantmanagement.service;

import com.gulfnet.shared_library.model.request.MenuRequest;
import com.gulfnet.shared_library.model.response.dto.MenuDto;
import com.gulfnet.shared_library.model.response.dto.MenuResponse;
import com.gulfnet.shared_library.model.response.dto.MenuListResponse;
import com.gulfnet.shared_library.model.response.dto.MenuVersionsResponse;
import java.util.UUID;
import com.gulfnet.shared_library.model.response.dto.ResponseDto;
import org.springframework.data.domain.Sort;
import com.gulfnet.shared_library.enums.MenuStatus;
import com.gulfnet.shared_library.model.request.AssignMenuStructureCategoriesRequest;
import com.gulfnet.shared_library.model.request.AssignMenuToRestaurantGroupRequest;
import com.gulfnet.shared_library.enums.EntityStatus; // Existing import
import com.gulfnet.shared_library.model.response.dto.MenuRestaurantGroupListResponse; // Existing import
import com.gulfnet.shared_library.model.request.DuplicateMenuRequest;
import java.util.List;
import com.gulfnet.shared_library.model.response.dto.RestaurantMenuDtoListResponse;
import com.gulfnet.shared_library.model.request.ScheduleMenuRequest;
import com.gulfnet.shared_library.enums.RestaurantMenuMappingStatus;
import com.gulfnet.shared_library.model.response.dto.MenuItemListResponse;
import com.gulfnet.shared_library.model.request.ItemAvailabilityChangeRequest;
import com.gulfnet.shared_library.model.response.dto.BxgyDiscountDetailsResponse;


public interface MenuService {

    // Create draft menu
    ResponseDto<MenuDto<MenuResponse>> createMenu(String userId, MenuRequest request, String locale);
    ResponseDto<MenuListResponse> getMenus(Integer page, Integer size, MenuStatus status, Boolean isPublished, String search, 
    String sortBy, Sort.Direction direction, String locale, Boolean isDeleted);
    ResponseDto<MenuDto<MenuResponse>> updateMenu(UUID id, String userId, MenuRequest request, String locale);
    ResponseDto<String> deleteMenu(UUID id, String userId, String locale);
    ResponseDto<MenuDto<MenuResponse>> getMenuById(UUID id, String locale);
    ResponseDto<Void> assignMenuStructureAndCategories(AssignMenuStructureCategoriesRequest request, String userId, String userLocale);
    ResponseDto<MenuDto<MenuResponse>> publishMenu(UUID menuId, String userId, String locale);
    ResponseDto<MenuDto<MenuResponse>> getMenuDetails(UUID menuId, UUID menuStructureId, String locale); 
    ResponseDto<Void> assignMenuToRestaurantGroup(
            AssignMenuToRestaurantGroupRequest request, 
            String userId,
            String locale);
            
        ResponseDto<MenuRestaurantGroupListResponse> getRestaurantGroupDetailsByMenuId(
                UUID menuId,
                String locale,
                EntityStatus status,
                String search,
                Integer page,
                Integer size
        );
        
        ResponseDto<Void> removeRestaurantGroupFromMenu(UUID menuId, UUID restaurantGroupId, String locale);


    ResponseDto<MenuVersionsResponse> getMenuVersions(UUID menuMasterId, MenuStatus status, String locale);
    ResponseDto<MenuDto<MenuResponse>> duplicateMenu(UUID menuId, String userId, DuplicateMenuRequest request, String locale);

    ResponseDto<MenuItemListResponse> getItemsByMenuAndCategory(UUID menuId, String categoryIdOrWildcard, UUID restaurantId, String locale, String search, String orderType, String alcoholType, Integer page, Integer size);
    ResponseDto<Void> removeRestaurantFromMenu(UUID menuId, UUID restaurantId, String locale);

    // Get restaurants assigned to a menu with filters
    ResponseDto<RestaurantMenuDtoListResponse> getRestaurantsByMenuId(
        UUID menuId,
        String locale,
        RestaurantMenuMappingStatus menuStatus,  // Changed to RestaurantMenuMappingStatus
        UUID restaurantGroupId,
        String search,
        Integer page,
        Integer size
    );


    // Schedule menu status change for restaurants
    ResponseDto<String> scheduleMenuForRestaurants(
            ScheduleMenuRequest request,
            String userId,
            String locale
    );

   
    ResponseDto<String> changeItemAvailability(ItemAvailabilityChangeRequest request, String userId, String locale);
    
    // Get BXGY discount details for an item
    ResponseDto<BxgyDiscountDetailsResponse> getBxgyDiscountDetails(UUID itemId, UUID menuId, UUID restaurantId, String locale);

    ResponseDto<Void> restoreMenus(List<UUID> ids, String userId, String locale);
}
