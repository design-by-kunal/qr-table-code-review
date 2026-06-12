package com.gulfnet.restaurantmanagement.service;

import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Page;
import com.gulfnet.shared_library.enums.EntityStatus;
import com.gulfnet.shared_library.model.request.TemplateLayoutRequest;
import com.gulfnet.shared_library.model.request.TemplateLayoutRequestDto;
import com.gulfnet.shared_library.model.response.dto.TemplateLayoutResponseDto;
import com.gulfnet.shared_library.model.response.dto.ResponseDto;
import com.gulfnet.shared_library.model.response.dto.TemplateLayoutDto;
import com.gulfnet.shared_library.model.response.dto.TemplateLayoutStructureDto;
import com.gulfnet.shared_library.model.response.dto.TemplateLayoutListDto;
import com.gulfnet.shared_library.model.response.dto.TemplateLayoutListResponse;
import com.gulfnet.shared_library.model.response.dto.TemplateLayoutResponse;

public interface TemplateLayoutService {
    ResponseDto<TemplateLayoutDto<TemplateLayoutResponse>> createTemplateLayout(TemplateLayoutRequest request, String creatorId);
    ResponseDto<TemplateLayoutDto<TemplateLayoutResponse>> getTemplateLayout(UUID id);
    
    ResponseDto<TemplateLayoutListDto> getAllTemplateLayouts(
            String search,
            EntityStatus status,
            String languageCode,
            Integer page,
            Integer size,
            String sortBy,
            String direction,
            Boolean isDeleted);

    ResponseDto<Void> softDeleteTemplateLayout(UUID id, String updaterId);

    ResponseDto<TemplateLayoutDto<TemplateLayoutResponse>> updateTemplateLayout(
        UUID id,
        TemplateLayoutRequest request,
        String updaterId,
        String userRole);

    ResponseDto<TemplateLayoutStructureDto<TemplateLayoutResponseDto>> createTemplateStructure(
                UUID templateLayoutId,
                TemplateLayoutRequestDto requestDto,
                String creatorId,
                String userRole);

    ResponseDto<TemplateLayoutStructureDto<TemplateLayoutResponseDto>> updateTemplateStructure(
                UUID templateLayoutId,
                TemplateLayoutRequestDto requestDto,
                String updaterId,
                String userRole);

    ResponseDto<TemplateLayoutStructureDto<TemplateLayoutResponseDto>> getTemplateStructure(UUID templateLayoutId);

    ResponseDto<Void> restoreTemplateLayouts(List<UUID> ids, String updaterId);

}
