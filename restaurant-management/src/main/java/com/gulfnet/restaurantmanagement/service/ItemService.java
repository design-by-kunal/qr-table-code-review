package com.gulfnet.restaurantmanagement.service;

import com.gulfnet.shared_library.enums.EntityStatus;
import com.gulfnet.shared_library.model.request.AssignModifierGroupsRequest;
import com.gulfnet.shared_library.model.request.ItemRequest;
import com.gulfnet.shared_library.model.response.dto.ItemDto;
import com.gulfnet.shared_library.model.response.dto.ItemResponse;
import com.gulfnet.shared_library.model.response.dto.ItemModifierItemListResponse;
import com.gulfnet.shared_library.model.response.dto.ModifierGroupAssignmentListResponse;
import com.gulfnet.shared_library.model.response.dto.ItemListResponse;
import com.gulfnet.shared_library.model.response.dto.ResponseDto;
import com.gulfnet.shared_library.model.response.dto.RestaurantItemsAndMenusResponse;
import org.springframework.data.domain.Sort;

import java.util.List;
import java.util.UUID;
import com.gulfnet.shared_library.model.response.dto.ItemModifierItemListResponseEnhanced;

public interface ItemService {
    ResponseDto<ItemDto<ItemResponse>> createItem(String userId, ItemRequest request, String locale);

    ResponseDto<ItemDto<ItemResponse>> updateItem(UUID itemId, ItemRequest request, String userId, String locale);

    ResponseDto<String> deleteItem(UUID itemId, String userId, String locale);

    ResponseDto<ItemDto<ItemResponse>> getItemById(UUID itemId, String locale, Boolean thumb);

    ResponseDto<ItemListResponse> getItems(Integer page, Integer size, String status, Boolean hasModifierAssigned, String search, String sortBy, Sort.Direction direction, String locale, Boolean thumb, Boolean isDeleted, String itemOrderType, String alcoholType);

    ResponseDto<ItemModifierItemListResponse> getItemWithModifiersItems(Integer page, Integer size, UUID itemId, String locale);
    
    // Modifier group assignment methods
    ResponseDto<ModifierGroupAssignmentListResponse> assignModifierGroupsToItem(AssignModifierGroupsRequest request);
    
    ResponseDto<ModifierGroupAssignmentListResponse> unassignModifierGroupFromItem(UUID itemId, UUID modifierGroupId, String updaterId, String updaterRole);


    ResponseDto<ItemModifierItemListResponseEnhanced> getItemWithModifiersItemsEnhanced(
        UUID itemId, UUID restaurantId, UUID menuId, String locale, UUID promotionId);

    ResponseDto<RestaurantItemsAndMenusResponse> getRestaurantItemsAndMenus(UUID restaurantId, Integer page, Integer size, Boolean isAvailable, String search, String sortBy, Sort.Direction direction, String locale);

    ResponseDto<Void> restoreItems(List<UUID> ids, String userId, String locale);

}   
