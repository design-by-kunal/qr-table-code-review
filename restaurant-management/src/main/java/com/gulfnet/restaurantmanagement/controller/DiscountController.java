package com.gulfnet.restaurantmanagement.controller;

import com.gulfnet.restaurantmanagement.service.DiscountService;
import com.gulfnet.shared_library.enums.EntityStatus;
import com.gulfnet.shared_library.model.request.DiscountRequest;
import com.gulfnet.shared_library.model.request.RestoreEntitiesRequest;
import com.gulfnet.shared_library.model.response.dto.*;
import com.gulfnet.shared_library.model.request.*;
import com.gulfnet.shared_library.model.request.UpdateRestaurantDiscountValidityRequest;
import com.gulfnet.shared_library.model.response.dto.ResponseDto;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("api/v1/discounts")
@RequiredArgsConstructor
@Slf4j
public class DiscountController {

    private final DiscountService discountService;

    @PostMapping
    public ResponseEntity<ResponseDto<DiscountDto<DiscountResponse>>> createDiscount(
            @RequestBody DiscountRequest request,
            @RequestHeader("User-ID") String userId,
            @RequestHeader(value = "locale", defaultValue = "en") String locale) {

        log.info("Received create discount request with locale: {}", locale);
        ResponseDto<DiscountDto<DiscountResponse>> response = discountService.createDiscount(userId, request, locale);
        return ResponseEntity.ok(response);
    }


    @PutMapping("/{id}")
    public ResponseEntity<ResponseDto<DiscountDto<DiscountResponse>>> updateDiscount(
            @PathVariable("id") String discountId,
            @RequestBody DiscountRequest request,
            @RequestHeader("User-ID") String userId,
            @RequestHeader(value = "locale", defaultValue = "en") String locale) {

        log.info("Received update discount request for id: {} with locale: {}", discountId, locale);
        ResponseDto<DiscountDto<DiscountResponse>> response = discountService.updateDiscount(discountId, userId, request, locale);
        return ResponseEntity.ok(response);
    }

    /**
     * Retrieves a paginated and filterable list of discounts.
     * Supports filtering by status, discount type, applied to (item/category/order), deletion status, and text search.
     *
     * @param page        optional page number for pagination
     * @param size        optional page size for pagination
     * @param status      optional filter by entity status (ACTIVE, INACTIVE, etc.)
     * @param search      optional search term for text search
     * @param discountType optional filter by discount type
     * @param appliedTo   optional filter by what the discount is applied to (item, category, order)
     * @param sortBy      field to sort by (default: "createdAt")
     * @param direction   sort direction (default: DESC)
     * @param locale      locale code for localized responses (default: "en")
     * @param isDeleted   optional filter by deletion status
     * @return response containing paginated list of discounts with filters applied
     */
    @GetMapping
    public ResponseEntity<ResponseDto<DiscountListResponse>> getDiscounts(
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            @RequestParam(required = false) EntityStatus status,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String discountType,
            @RequestParam(required = false) String appliedTo,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "DESC") Sort.Direction direction,
            @RequestHeader(value = "locale", defaultValue = "en") String locale,
            @RequestParam(required = false) Boolean isDeleted) {

        log.info("Request received to fetch discounts with language: {}", locale);
        ResponseDto<DiscountListResponse> response = discountService.getDiscounts(
                page, size, status, search, sortBy, discountType, appliedTo, direction, locale, isDeleted);
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ResponseDto<String>> deleteDiscount(
            @PathVariable UUID id,
            @RequestHeader("User-ID") String userId,
            @RequestHeader(value = "locale", defaultValue = "en") String locale) {

        log.info("Request received to delete discount with id: {}", id);
        ResponseDto<String> response = discountService.deleteDiscount(id, userId, locale);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{id}")
    public ResponseEntity<ResponseDto<DiscountDto<DiscountResponse>>> getDiscount(
            @PathVariable UUID id,
            @RequestHeader(value = "locale", defaultValue = "en") String locale) {
        log.info("Request received to get discount with id: {}", id);
        ResponseDto<DiscountDto<DiscountResponse>> response = discountService.getDiscount(id, locale);
        return ResponseEntity.ok(response);
    }

    /**
     * Assign a single discount to multiple items
     */
    @PostMapping("/assign-to-items")
    public ResponseEntity<ResponseDto<ItemDiscountAssignmentResponse>> assignDiscountToItems(
            @RequestBody AssignDiscountToItemsRequest request,
            @RequestHeader(value = "locale", defaultValue = "en") String locale) {
        log.info("Received assign discount {} to items {} with locale: {}", request.getDiscountId(), request.getItemIds(), locale);
        ResponseDto<ItemDiscountAssignmentResponse> response = discountService.assignDiscountToItems(request, locale);
        return ResponseEntity.ok(response);
    }

    /**
     * Get all discounts for a menu by menuId, with filtering, sorting, and searching.
     * This endpoint can be used for both admin and customer applications.
     * For customer apps: set applyDayFilter=true to apply time-based validity filtering
     * For admin apps: set applyDayFilter=false to see all discounts regardless of validity window
     * When restaurantId is provided, it will filter ORDER type discounts assigned to that restaurant
     * and check validity from RestaurantDiscountMapping table.
     */
    @GetMapping("/menu/{menuId}/discounts")
    public ResponseEntity<ResponseDto<ItemDiscountListResponse>> getMenuWithDiscounts(
            @PathVariable("menuId") UUID menuId,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "DESC") Sort.Direction direction,
            @RequestParam(required = false) EntityStatus status,
            @RequestParam(required = false) String discountType,
            @RequestParam(required = false) String appliedTo,
            @RequestParam(required = false, defaultValue = "false") Boolean applyDayFilter,
            @RequestParam(required = false) UUID restaurantId,
            @RequestHeader(value = "locale", defaultValue = "en") String locale) {
        
        log.info("Request received to fetch menu discounts for menuId: {} with restaurantId: {}, language: {}, search: {}, status: {}, discountType: {}, appliedTo: {}, applyDayFilter: {}", 
                menuId, restaurantId, locale, search, status, discountType, appliedTo, applyDayFilter);
        
        ResponseDto<ItemDiscountListResponse> response = discountService.getMenuWithDiscounts(
            page, size, menuId, search, sortBy, direction, status, discountType, appliedTo, applyDayFilter, restaurantId, locale);
        return ResponseEntity.ok(response);
    }

    /**
     * Assign a single discount to multiple categories
     */
    @PostMapping("/assign-to-categories")
    public ResponseEntity<ResponseDto<CategoryDiscountAssignmentResponse>> assignDiscountToCategories(
            @RequestBody AssignDiscountToCategoriesRequest request,
            @RequestHeader(value = "locale", defaultValue = "en") String locale) {
        log.info("Received assign discount {} to categories {} with locale: {}", request.getDiscountId(), request.getCategoryIds(), locale);
        ResponseDto<CategoryDiscountAssignmentResponse> response = discountService.assignDiscountToCategories(request, locale);
        return ResponseEntity.ok(response);
    }

    /**
     * Unassign a discount from ALL categories mapped to it
     */
    @DeleteMapping("/menu/{menuId}/discounts/{discountId}/categories")
    public ResponseEntity<ResponseDto<DiscountAssignmentCategoryListResponse>> unassignDiscountFromAllCategories(
            @PathVariable UUID menuId,
            @PathVariable UUID discountId,
            @RequestHeader("User-ID") String updaterId,
            @RequestHeader(value = "locale", defaultValue = "en") String locale) {
        log.info("Request received to unassign discount {} from ALL categories in menu {} by Updater ID: {}", discountId, menuId, updaterId);
        ResponseDto<DiscountAssignmentCategoryListResponse> response = discountService.unassignDiscountFromAllCategories(menuId, discountId, updaterId, locale);
        log.info("Successfully unassigned discount {} from ALL categories in menu {}", discountId, menuId);
        return ResponseEntity.ok(response);
    }

    /**
     * Unassign a discount from ALL items mapped to it
     */
    @DeleteMapping("/menu/{menuId}/discounts/{discountId}/items")
    public ResponseEntity<ResponseDto<DiscountAssignmentListResponse>> unassignDiscountFromAllItems(
            @PathVariable UUID menuId,
            @PathVariable UUID discountId,
            @RequestHeader("User-ID") String updaterId,
            @RequestHeader(value = "locale", defaultValue = "en") String locale) {
        log.info("Request received to unassign discount {} from ALL items in menu {} by Updater ID: {}", discountId, menuId, updaterId);
        ResponseDto<DiscountAssignmentListResponse> response = discountService.unassignDiscountFromAllItems(menuId, discountId, updaterId, locale);
        log.info("Successfully unassigned discount {} from ALL items in menu {}", discountId, menuId);
        return ResponseEntity.ok(response);
    }

    /**
     * Unassign a discount from order (order type discount)
     */
    @DeleteMapping("/menu/{menuId}/discounts/{discountId}/orders")
    public ResponseEntity<ResponseDto<DiscountAssignmentListResponse>> unassignDiscountFromOrder(
            @PathVariable UUID menuId,
            @PathVariable UUID discountId,
            @RequestHeader("User-ID") String updaterId,
            @RequestHeader(value = "locale", defaultValue = "en") String locale) {
        log.info("Request received to unassign discount {} from order in menu {} by Updater ID: {}", discountId, menuId, updaterId);
        ResponseDto<DiscountAssignmentListResponse> response = discountService.unassignDiscountFromOrder(menuId, discountId, updaterId, locale);
        log.info("Successfully unassigned discount {} from order in menu {}", discountId, menuId);
        return ResponseEntity.ok(response);
    }

    /**
     * Get discount details by menu ID and discount ID with menu-specific information
     */
    @GetMapping("/menu/{menuId}/discounts/{discountId}/details")
    public ResponseEntity<ResponseDto<DiscountDetailsResponse>> getDiscountDetailsWithMenu(
            @PathVariable UUID menuId,
            @PathVariable UUID discountId,
            @RequestHeader(value = "locale", defaultValue = "en") String locale) {
        log.info("Request received to get discount details for menu {} and discount ID: {}", menuId, discountId);
        ResponseDto<DiscountDetailsResponse> response = discountService.getDiscountDetailsWithMenu(menuId, discountId, locale);
        return ResponseEntity.ok(response);
    }

    /**
     * Edit discount assignment to categories with menu-specific overrides
     */
    @PutMapping("/assign-to-categories/{discountId}")
    public ResponseEntity<ResponseDto<DiscountDetailsResponse>> editDiscountAssignment(
            @PathVariable UUID discountId,
            @RequestBody AssignDiscountToCategoriesRequest request,
            @RequestHeader("User-ID") String updaterId,
            @RequestHeader(value = "locale", defaultValue = "en") String locale) {
        log.info("Request received to edit discount assignment for discount {} and menu {}", discountId, request.getMenuId());
        ResponseDto<DiscountDetailsResponse> response = discountService.editDiscountAssignment(discountId, request, updaterId, locale);
        return ResponseEntity.ok(response);
    }

    /**
     * Edit discount assignment to items with menu-specific overrides
     */
    @PutMapping("/assign-to-items/{discountId}")
    public ResponseEntity<ResponseDto<DiscountDetailsResponse>> editDiscountItemAssignment(
            @PathVariable UUID discountId,
            @RequestBody AssignDiscountToItemsRequest request,
            @RequestHeader("User-ID") String updaterId,
            @RequestHeader(value = "locale", defaultValue = "en") String locale) {
        log.info("Request received to edit discount item assignment for discount {} and menu {}", discountId, request.getMenuId());
        ResponseDto<DiscountDetailsResponse> response = discountService.editDiscountItemAssignment(discountId, request, updaterId, locale);
        return ResponseEntity.ok(response);
    }

    /**
     * Assign a discount to a menu (order type discount)
     */
    @PostMapping("/assign-to-orders")
    public ResponseEntity<ResponseDto<MenuDiscountAssignmentResponse>> assignDiscountToOrder(
            @RequestBody AssignDiscountToMenuRequest request,
            @RequestHeader("User-ID") String userId,
            @RequestHeader(value = "locale", defaultValue = "en") String locale) {
        log.info("Received assign discount {} to order {} with locale: {}", request.getDiscountId(), request.getMenuId(), locale);
        ResponseDto<MenuDiscountAssignmentResponse> response = discountService.assignDiscountToOrder(request, userId, locale);
        return ResponseEntity.ok(response);
    }

    /**
     * Edit discount assignment to order (order type discount) with menu-specific overrides
     */
    @PutMapping("/assign-to-orders/{discountId}")
    public ResponseEntity<ResponseDto<DiscountDetailsResponse>> editDiscountOrderAssignment(
            @PathVariable UUID discountId,
            @RequestBody AssignDiscountToMenuRequest request,
            @RequestHeader("User-ID") String updaterId,
            @RequestHeader(value = "locale", defaultValue = "en") String locale) {
        log.info("Request received to edit discount order assignment for discount {} and menu {}", discountId, request.getMenuId());
        ResponseDto<DiscountDetailsResponse> response = discountService.editDiscountOrderAssignment(discountId, request, updaterId, locale);
        return ResponseEntity.ok(response);
    }
    
    /**
     * Validate discount for a given menu and subtotal
     */
    @PostMapping("/validate")
    public ResponseEntity<ResponseDto<DiscountValidationResponse>> validateDiscount(
            @RequestBody DiscountValidationRequest request,
            @RequestHeader(value = "locale", defaultValue = "en") String locale) {
        log.info("Received discount validation request for discount code: {} and discount id: {} and menu: {}", 
                request.getDiscountCode(), request.getDiscountId(), request.getMenuId());
        ResponseDto<DiscountValidationResponse> response = discountService.validateDiscount(request, locale);
        return ResponseEntity.ok(response);
    }

    /**
     * Get all discounts assigned to a restaurant with filtering, sorting, and pagination
     */
    @GetMapping("/restaurants/{restaurantId}/discounts")
    public ResponseEntity<ResponseDto<DiscountListResponse>> getRestaurantDiscounts(
            @PathVariable UUID restaurantId,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String discountType,
            @RequestParam(required = false) String appliedTo,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "DESC") Sort.Direction direction,
            @RequestHeader(value = "locale", defaultValue = "en") String locale) {
        
        log.info("Request received to fetch discounts for restaurant {} with language: {}, search: {}, status: {}, discountType: {}, appliedTo: {}", 
                restaurantId, locale, search, status, discountType, appliedTo);
        
        ResponseDto<DiscountListResponse> response = discountService.getRestaurantDiscounts(
                restaurantId, page, size, status, search, sortBy, discountType, appliedTo, direction, locale);
        return ResponseEntity.ok(response);
    }

    /**
     * Get detailed discount information for a specific restaurant and discount
     * Includes discount details, validity, and mapped categories/items/BXGY details
     * Supports filtering items by order type and includes alcohol type information
     *
     * @param restaurantId the UUID of the restaurant
     * @param discountId   the UUID of the discount
     * @param locale       locale code for localized responses (default: "en")
     * @param orderType    optional filter by order type (DINE_IN, TAKEAWAY) to filter items
     * @return response containing detailed discount information with filtered items
     */
    @GetMapping("/restaurants/{restaurantId}/discounts/{discountId}")
    public ResponseEntity<ResponseDto<RestaurantDiscountDetailsResponse>> getRestaurantDiscountDetails(
            @PathVariable UUID restaurantId,
            @PathVariable UUID discountId,
            @RequestHeader(value = "locale", defaultValue = "en") String locale,
            @RequestParam(name = "orderType", required = false) String orderType) {
        
        log.info("Request received to fetch discount details for restaurant {} and discount {} with language: {}, orderType: {}", 
                restaurantId, discountId, locale, orderType);
        
        ResponseDto<RestaurantDiscountDetailsResponse> response = discountService.getRestaurantDiscountDetails(
                restaurantId, discountId, locale, orderType);
        return ResponseEntity.ok(response);
    }

    /**
     * Update discount validity for a specific restaurant
     * Updates validFrom, validTo, startTime, endTime, daysOfWeek, status, and isHide in RestaurantDiscountMapping
     */
    @PutMapping("/restaurants/{restaurantId}/discounts/{discountId}/validity")
    public ResponseEntity<ResponseDto<RestaurantDiscountDetailsResponse>> updateRestaurantDiscountValidity(
            @PathVariable UUID restaurantId,
            @PathVariable UUID discountId,
            @RequestBody UpdateRestaurantDiscountValidityRequest request,
            @RequestHeader("User-ID") String userId,
            @RequestHeader(value = "locale", defaultValue = "en") String locale) {
        
        log.info("Request received to update discount validity for restaurant {} and discount {} with language: {}", 
                restaurantId, discountId, locale);
        
        ResponseDto<RestaurantDiscountDetailsResponse> response = discountService.updateRestaurantDiscountValidity(
                restaurantId, discountId, request, userId, locale);
        return ResponseEntity.ok(response);
    }

    @PutMapping("/restore")
    public ResponseEntity<ResponseDto<Void>> restoreDiscounts(
            @Valid @RequestBody RestoreEntitiesRequest request,
            @RequestHeader("User-ID") String userId,
            @RequestHeader(value = "locale", defaultValue = "en") String locale) {
        log.info("Received restore discounts request");
        ResponseDto<Void> response = discountService.restoreDiscounts(request.getIds(), userId, locale);
        return ResponseEntity.ok(response);
    }
}