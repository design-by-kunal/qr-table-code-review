package com.gulfnet.restaurantmanagement.service;

import com.gulfnet.shared_library.model.request.ComboRequest;
import com.gulfnet.shared_library.model.response.dto.ComboDto;
import com.gulfnet.shared_library.model.response.dto.ComboListResponse;
import com.gulfnet.shared_library.model.response.dto.ComboResponse;
import com.gulfnet.shared_library.model.response.dto.ComboDetailsResponse;
import com.gulfnet.shared_library.model.response.dto.ResponseDto;
import org.springframework.data.domain.Sort;

import java.util.UUID;

public interface ComboService {
    ResponseDto<ComboDto<ComboResponse>> createCombo(String userId, ComboRequest request, String locale);

    ResponseDto<ComboDto<ComboResponse>> updateCombo(UUID comboId, String userId, ComboRequest request, String locale);

    /**
     * Retrieves a paginated and filterable list of combos for a menu.
     * Supports filtering by status, type, availability, and text search.
     *
     * @param menuId      the menu ID to get combos for
     * @param page        page number (1-based)
     * @param size        page size
     * @param status      optional filter by status
     * @param type        optional filter by combo type
     * @param search      optional search term for text search
     * @param isAvailable optional filter by availability
     * @param sortBy      field to sort by
     * @param direction   sort direction (ASC or DESC)
     * @param locale      locale code for localized responses
     * @param restaurantId optional restaurant ID for availability checking
     * @return {@link ResponseDto} containing paginated list of combos
     */
    ResponseDto<ComboListResponse> getCombos(
        UUID menuId,
        Integer page, 
        Integer size, 
        String status, 
        String type, 
        String search, 
        Boolean isAvailable,
        String sortBy, 
        Sort.Direction direction, 
        String locale,
        UUID restaurantId);

    ResponseDto<ComboDto<ComboDetailsResponse>> getComboDetailsById(UUID comboId, String locale, UUID restaurantId, String orderType);

    ResponseDto<String> deleteCombo(UUID comboId, String userId, String locale);
}
