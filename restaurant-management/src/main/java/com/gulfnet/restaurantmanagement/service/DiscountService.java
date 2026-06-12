package com.gulfnet.restaurantmanagement.service;

import com.gulfnet.shared_library.enums.EntityStatus;
import com.gulfnet.shared_library.enums.DiscountType;
import com.gulfnet.shared_library.enums.AppliedTo;
import com.gulfnet.shared_library.model.request.DiscountRequest;
import com.gulfnet.shared_library.model.response.dto.DiscountDto;
import com.gulfnet.shared_library.model.response.dto.*;
import com.gulfnet.shared_library.model.request.*;
import com.gulfnet.shared_library.model.response.dto.DiscountListResponse;
import com.gulfnet.shared_library.model.response.dto.DiscountResponse;
import com.gulfnet.shared_library.model.response.dto.ResponseDto;
import org.springframework.data.domain.Sort;

import java.util.List;
import java.util.UUID;

public interface DiscountService {
    ResponseDto<DiscountDto<DiscountResponse>> createDiscount(String userId, DiscountRequest request, String locale);
    ResponseDto<DiscountDto<DiscountResponse>> updateDiscount(String discountId, String userId, DiscountRequest request, String locale);
    
    /**
     * Retrieves a paginated and filterable list of discounts.
     * Supports filtering by status, discount type, applied to, and text search.
     *
     * @param page        page number (1-based)
     * @param size        page size
     * @param status      optional filter by entity status
     * @param search      optional search term for text search
     * @param sortBy      field to sort by
     * @param discountType optional filter by discount type
     * @param appliedTo   optional filter by applied to (ITEM, CATEGORY, ORDER)
     * @param direction   sort direction (ASC or DESC)
     * @param locale      locale code for localized responses
     * @param isDeleted   optional filter by deleted status
     * @return {@link ResponseDto} containing paginated list of discounts
     */
    ResponseDto<DiscountListResponse> getDiscounts(
        Integer page,
        Integer size,
        EntityStatus status,
        String search,
        String sortBy,
        String discountType,
        String appliedTo,
        Sort.Direction direction,
        String locale,
        Boolean isDeleted
    );

    ResponseDto<String> deleteDiscount(UUID id, String userId, String locale);

    ResponseDto<DiscountDto<DiscountResponse>> getDiscount(UUID id, String locale);

    ResponseDto<ItemDiscountAssignmentResponse> assignDiscountToItems(AssignDiscountToItemsRequest request, String locale);
    
    ResponseDto<ItemDiscountListResponse> getMenuWithDiscounts(Integer page, Integer size, UUID menuId, String search, String sortBy,
Sort.Direction direction, EntityStatus status, String discountType, String appliedTo, Boolean applyDayFilter, UUID restaurantId, String locale);
    ResponseDto<CategoryDiscountAssignmentResponse> assignDiscountToCategories(AssignDiscountToCategoriesRequest request, String locale);
    ResponseDto<DiscountAssignmentCategoryListResponse> unassignDiscountFromAllCategories(UUID menuId, UUID discountId, String updaterId, String locale);
    ResponseDto<DiscountAssignmentListResponse> unassignDiscountFromAllItems(UUID menuId, UUID discountId, String updaterId, String locale);
    ResponseDto<DiscountDetailsResponse> getDiscountDetailsWithMenu(UUID menuId, UUID discountId, String locale);
    ResponseDto<DiscountDetailsResponse> editDiscountAssignment(UUID discountId, AssignDiscountToCategoriesRequest request, String updaterId, String locale);
    ResponseDto<DiscountDetailsResponse> editDiscountItemAssignment(UUID discountId, AssignDiscountToItemsRequest request, String updaterId, String locale);
    ResponseDto<MenuDiscountAssignmentResponse> assignDiscountToOrder(AssignDiscountToMenuRequest request, String userId, String locale);
    ResponseDto<DiscountDetailsResponse> editDiscountOrderAssignment(UUID discountId, AssignDiscountToMenuRequest request, String updaterId, String locale);
    ResponseDto<DiscountAssignmentListResponse> unassignDiscountFromOrder(UUID menuId, UUID discountId, String updaterId, String locale);
    
    ResponseDto<DiscountValidationResponse> validateDiscount(DiscountValidationRequest request, String locale);
    
    /**
     * Retrieves a paginated and filterable list of discounts for a specific restaurant.
     * Supports filtering by status, discount type, applied to, and text search.
     *
     * @param restaurantId the restaurant ID to get discounts for
     * @param page         page number (1-based)
     * @param size         page size
     * @param status       optional filter by status
     * @param search       optional search term for text search
     * @param sortBy       field to sort by
     * @param discountType optional filter by discount type
     * @param appliedTo    optional filter by applied to (ITEM, CATEGORY, ORDER)
     * @param direction    sort direction (ASC or DESC)
     * @param locale       locale code for localized responses
     * @return {@link ResponseDto} containing paginated list of restaurant discounts
     */
    ResponseDto<DiscountListResponse> getRestaurantDiscounts(
        UUID restaurantId,
        Integer page,
        Integer size,
        String status,
        String search,
        String sortBy,
        String discountType,
        String appliedTo,
        Sort.Direction direction,
        String locale
    );
    
    ResponseDto<RestaurantDiscountDetailsResponse> getRestaurantDiscountDetails(
        UUID restaurantId,
        UUID discountId,
        String locale,
        String orderType
    );
    
    ResponseDto<RestaurantDiscountDetailsResponse> updateRestaurantDiscountValidity(
        UUID restaurantId,
        UUID discountId,
        UpdateRestaurantDiscountValidityRequest request,
        String userId,
        String locale
    );

    ResponseDto<Void> restoreDiscounts(List<UUID> ids, String userId, String locale);
}