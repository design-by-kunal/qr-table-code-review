package com.gulfnet.restaurantmanagement.service;
import com.gulfnet.shared_library.model.request.KdsRequest;
import com.gulfnet.shared_library.model.request.AssignUserToKdsRequest;
import com.gulfnet.shared_library.model.request.UnassignUserFromKdsRequest;
import com.gulfnet.shared_library.model.request.UpdateKdsConfigRequest;
import com.gulfnet.shared_library.model.request.AssignDeviceToKdsRequest;
import com.gulfnet.shared_library.model.response.dto.KdsConfigurationListResponse;
import com.gulfnet.shared_library.model.response.dto.KdsDto;
import com.gulfnet.shared_library.model.response.dto.KdsListResponse;
import com.gulfnet.shared_library.model.response.dto.KdsResponse;
import com.gulfnet.shared_library.model.response.dto.KdsAssignedUserListResponse;
import com.gulfnet.shared_library.model.response.dto.ResponseDto;
import com.gulfnet.shared_library.model.response.dto.CategoryWrapperResponse;
import com.gulfnet.shared_library.model.response.dto.TicketDetailsResponse;
import com.gulfnet.shared_library.model.response.dto.TicketDashboardListDto;
import org.springframework.data.domain.Sort;

import java.util.UUID;

public interface KdsService {
    ResponseDto<KdsDto<KdsResponse>> createKds(String userId, KdsRequest request, String locale);
    
    ResponseDto<KdsDto<KdsResponse>> updateKds(UUID kdsId, KdsRequest request, String userId, String locale);
    
    ResponseDto<KdsDto<KdsResponse>> getKdsById(UUID kdsId, String locale);
    
    ResponseDto<String> deleteKds(UUID kdsId, String userId, String locale);
    
    ResponseDto<KdsListResponse> getKdsList(Integer page, Integer size, String status, String search, String sortBy, Sort.Direction direction, String userId, String locale);

    ResponseDto<CategoryWrapperResponse> getUnassignedCategories(String userId, UUID menuId, String locale);
    
    ResponseDto<KdsConfigurationListResponse> assignUserToKds(AssignUserToKdsRequest request, String userId, String locale);
    
    ResponseDto<KdsConfigurationListResponse> unassignUserFromKds(UnassignUserFromKdsRequest request, String userId, String locale);
    
    ResponseDto<KdsDto<KdsResponse>> getKdsConfigByDeviceId(String deviceCode, String locale);
    
    ResponseDto<KdsDto<KdsResponse>> updateKdsConfig(UUID kdsId, UpdateKdsConfigRequest request, String locale);
    
    ResponseDto<KdsDto<KdsResponse>> assignDeviceToKds(AssignDeviceToKdsRequest request, String userId, String locale);
    
    ResponseDto<KdsAssignedUserListResponse> getAssignedUsersByKdsId(UUID kdsId, UUID restaurantId, String locale);
    
    // Ticket Dashboard methods
    ResponseDto<TicketDetailsResponse> getTicketDetails(UUID orderedItemId);
    
    ResponseDto<TicketDashboardListDto> getTicketDashboardForKds(UUID kdsId, String userId, Integer page, Integer size,
            String itemStatuses, String orderTypes, String tableIds, String tableCodes, String categoryIds, String sectionIds, String sortBy, String direction);
}

