package com.gulfnet.restaurantmanagement.service;

import com.gulfnet.shared_library.model.request.MenuStructureRequest;
import com.gulfnet.shared_library.model.response.dto.MenuStructureDto;
import com.gulfnet.shared_library.model.response.dto.MenuStructureResponse;
import com.gulfnet.shared_library.model.response.dto.ResponseDto;
import com.gulfnet.shared_library.model.response.dto.MenuDetailStructureDto;
import com.gulfnet.shared_library.model.response.dto.MenuCategoryStructureResponse;
import com.gulfnet.shared_library.model.response.dto.MenuStructureListResponse;
import com.gulfnet.shared_library.enums.EntityStatus;
import org.springframework.data.domain.Sort;

import java.util.List;
import java.util.UUID;

public interface MenuStructureService {
    
    ResponseDto<MenuStructureDto<MenuStructureResponse>> createMenuStructure(MenuStructureRequest request,String userId,String locale);
    
    ResponseDto<MenuStructureDto<MenuStructureResponse>> updateMenuStructure(UUID id, MenuStructureRequest request,String userId,String locale);
    
    ResponseDto<String> deleteMenuStructure(UUID id,String userId,String locale);

    ResponseDto<MenuStructureDto<MenuStructureResponse>> getMenuStructureById(UUID id, String locale);
    ResponseDto<MenuCategoryStructureResponse> getMenuStructure(UUID menuId, UUID menuStructureId, EntityStatus status, String search, Boolean hasCombo, String orderType, String itemOrderType);


    
    ResponseDto<MenuStructureListResponse> getMenuStructures(
        int page, 
        int size, 
        EntityStatus status, 
        String search, 
        String sortBy, 
        Sort.Direction direction,
        String locale,
        Boolean isDeleted);

     ResponseDto<List<MenuDetailStructureDto>> getAllMenuStructuresByRestaurantId(
            UUID restaurantId, EntityStatus status, String search, String locale);

    ResponseDto<Void> restoreMenuStructures(List<UUID> ids, String userId, String locale);
}


