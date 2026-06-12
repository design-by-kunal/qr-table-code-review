package com.gulfnet.restaurantmanagement.service;

import com.gulfnet.shared_library.enums.EntityStatus;
import com.gulfnet.shared_library.enums.ModifierType;
import com.gulfnet.shared_library.model.request.ModifierGroupRequestDto;
import com.gulfnet.shared_library.model.response.dto.ModifierGroupDto;
import com.gulfnet.shared_library.model.response.dto.ModifierGroupResponse;
import com.gulfnet.shared_library.model.response.dto.ResponseDto;
import com.gulfnet.shared_library.model.response.dto.ModifierGroupListResponse;
import com.gulfnet.shared_library.model.response.dto.ModifierGroupDetailsResponse;

import java.util.List;
import java.util.UUID;

public interface ModifierGroupService {

    ResponseDto<ModifierGroupDto<ModifierGroupResponse>> createModifierGroup(ModifierGroupRequestDto request, String creatorId, String creatorRole);

    ResponseDto<ModifierGroupDto<ModifierGroupResponse>> updateModifierGroup(UUID modifierGroupId, ModifierGroupRequestDto request, String updaterId, String updaterRole);

    /**
     * Retrieves a paginated and filterable list of modifier groups.
     * Supports filtering by status, modifier type, multi-select option, item, and text search.
     *
     * @param status         optional filter by entity status
     * @param modifierType   optional filter by modifier type (ADD_ON, SUBSTITUTE)
     * @param allowMultiSelect optional filter by multi-select capability
     * @param itemId         optional filter by item ID
     * @param search         optional search term for text search
     * @param page           page number (1-based)
     * @param size           page size
     * @param sortBy         field to sort by
     * @param sortDirection  sort direction (ASC or DESC)
     * @param isDeleted      optional filter by deleted status
     * @return {@link ResponseDto} containing paginated list of modifier groups
     */
    ResponseDto<ModifierGroupListResponse> getModifierGroups(
            EntityStatus status,
            ModifierType modifierType,
            Boolean allowMultiSelect,
            UUID itemId,
            String search,
            Integer page,
            Integer size,
            String sortBy,
            String sortDirection,
            Boolean isDeleted
    );

    ResponseDto<ModifierGroupDto<ModifierGroupDetailsResponse>> getModifierGroupDetails(UUID modifierGroupId);

    ResponseDto<Void> deleteModifierGroup(UUID modifierGroupId, String updaterId, String userRole);

    ResponseDto<Void> restoreModifierGroups(List<UUID> ids, String updaterId, String userRole);

}
