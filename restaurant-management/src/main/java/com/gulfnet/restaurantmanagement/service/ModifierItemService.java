package com.gulfnet.restaurantmanagement.service;

import com.gulfnet.shared_library.enums.EntityStatus;
import com.gulfnet.shared_library.model.request.ModifierItemRequestDto;
import com.gulfnet.shared_library.model.response.dto.ModifierItemDto;
import com.gulfnet.shared_library.model.response.dto.ModifierItemResponseDto;
import com.gulfnet.shared_library.model.response.dto.ResponseDto;
import com.gulfnet.shared_library.model.response.dto.ModifierItemListResponse;
import org.springframework.data.domain.Sort;

import java.util.UUID;

public interface ModifierItemService {
    ResponseDto<ModifierItemDto<ModifierItemResponseDto>> createModifierItem(String userId, ModifierItemRequestDto request, String locale);
    ResponseDto<ModifierItemDto<ModifierItemResponseDto>> updateModifierItem(UUID id, ModifierItemRequestDto request, String userId, String locale);
    ResponseDto<ModifierItemDto<ModifierItemResponseDto>> getModifierItemDetails(UUID id, String locale);
    ResponseDto<ModifierItemListResponse> getModifierItemsByGroupId(
            UUID modifierGroupId,
            EntityStatus status,
            String search,
            Integer page,
            Integer size,
            String sortBy,
            String sortDirection
    );
    ResponseDto<Void> deleteModifierItem(UUID id, String userId, String userRole);
}

