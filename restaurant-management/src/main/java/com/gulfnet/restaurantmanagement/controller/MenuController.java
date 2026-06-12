package com.gulfnet.restaurantmanagement.controller;

import com.gulfnet.shared_library.model.request.MenuRequest;
import com.gulfnet.shared_library.model.request.RestoreEntitiesRequest;
import com.gulfnet.shared_library.model.response.dto.MenuDto;
import com.gulfnet.shared_library.model.response.dto.ResponseDto;
import com.gulfnet.shared_library.model.response.dto.MenuResponse;
import com.gulfnet.shared_library.model.response.dto.MenuVersionsResponse;
import com.gulfnet.restaurantmanagement.service.MenuService;
import com.gulfnet.shared_library.enums.MenuStatus;
import com.gulfnet.shared_library.model.request.AssignMenuToRestaurantGroupRequest;
import com.gulfnet.shared_library.model.response.dto.ResponseDto;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import com.gulfnet.shared_library.model.request.AssignMenuStructureCategoriesRequest;
import com.gulfnet.shared_library.model.request.DuplicateMenuRequest;
import com.gulfnet.shared_library.enums.RestaurantMenuMappingStatus;
import lombok.extern.slf4j.Slf4j;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import com.gulfnet.shared_library.model.response.dto.MenuListResponse;
import com.gulfnet.shared_library.model.response.dto.ResponseDto;
import com.gulfnet.shared_library.model.request.AssignCategoriesItemsRequest;
import com.gulfnet.shared_library.enums.EntityStatus; 
import com.gulfnet.shared_library.model.response.dto.MenuRestaurantGroupListResponse;
import com.gulfnet.shared_library.model.response.dto.ItemResponse;
import com.gulfnet.shared_library.model.response.dto.MenuItemResponse;
import com.gulfnet.shared_library.model.response.dto.RestaurantMenuDtoListResponse;
import java.util.List;
import com.gulfnet.shared_library.model.request.ScheduleMenuRequest;
import com.gulfnet.shared_library.model.response.dto.MenuItemListResponse;
import com.gulfnet.shared_library.model.response.dto.PaginationMetaData;
import com.gulfnet.shared_library.model.request.ItemAvailabilityChangeRequest;
import com.gulfnet.shared_library.model.response.dto.BxgyDiscountDetailsResponse;
import com.gulfnet.shared_library.entity.Item;
import com.gulfnet.shared_library.repository.ItemRepository;
import java.time.LocalDateTime;
import java.util.Optional;


@RestController
@RequestMapping("api/v1/menus")
@RequiredArgsConstructor
@Slf4j
public class MenuController {

    private final MenuService menuService;

    /**
     * Creates a new menu with the specified configuration.
     *
     * @param request the menu request containing menu details and configuration
     * @param userId  the user ID from the request header (required)
     * @param locale  locale code for localized responses (default: "en")
     * @return response containing the created menu details
     */
    @PostMapping
    public ResponseEntity<ResponseDto<MenuDto<MenuResponse>>> createMenu(
            @Valid @RequestBody MenuRequest request,
            @RequestHeader("User-ID") String userId,
            @RequestHeader(value = "locale", defaultValue = "en") String locale) {

        log.info("Received create menu request with locale: {}", locale);
        ResponseDto<MenuDto<MenuResponse>> saved =
                menuService.createMenu(userId, request, locale);

        return ResponseEntity.ok(saved);
    }

    /**
     * Retrieves a paginated and filterable list of menus.
     * Supports filtering by status, publication status, menu structure, deletion status, and text search.
     *
     * @param page           optional page number for pagination
     * @param size           optional page size for pagination
     * @param status         optional filter by menu status
     * @param menuStructureId optional filter by menu structure ID
     * @param isPublished    optional filter by publication status
     * @param search         optional search term for text search
     * @param sortBy         field to sort by (default: "createdAt")
     * @param direction      sort direction (default: DESC)
     * @param locale         locale code for localized responses (default: "en")
     * @param isDeleted      optional filter by deletion status
     * @return response containing paginated list of menus with filters applied
     */
    @GetMapping
    public ResponseEntity<ResponseDto<MenuListResponse>> getMenus(

            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            @RequestParam(required = false) MenuStatus status,
            @RequestParam(required = false) UUID menuStructureId,
            @RequestParam(required = false) Boolean isPublished,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "DESC") Sort.Direction direction,
            @RequestHeader(value = "locale", defaultValue = "en") String locale,
            @RequestParam(required = false) Boolean isDeleted) {
        
            log.info("Request received to fetch menus with language: {}", locale);
            ResponseDto<MenuListResponse> response = menuService.getMenus(
                page, size, status, isPublished, search, sortBy, direction, locale, isDeleted);
        return ResponseEntity.ok(response);
    }

    /**
     * Updates an existing menu with new configuration and details.
     *
     * @param id      the UUID of the menu to update
     * @param request the menu request containing updated menu details
     * @param userId  the user ID from the request header (required)
     * @param locale  locale code for localized responses (default: "en")
     * @return response containing the updated menu details
     */
    @PutMapping("/{id}")
    public ResponseEntity<ResponseDto<MenuDto<MenuResponse>>> updateMenu(
            @PathVariable UUID id,
            @Valid @RequestBody MenuRequest request,
            @RequestHeader("User-ID") String userId,
            @RequestHeader(value = "locale", defaultValue = "en") String locale) {

        log.info("Received update menu request for id: {} with locale: {}", id, locale);
        ResponseDto<MenuDto<MenuResponse>> updated = 
                menuService.updateMenu(id, userId, request, locale);

        return ResponseEntity.ok(updated);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ResponseDto<String>> deleteMenu(
            @PathVariable UUID id,
            @RequestHeader("User-ID") String userId,
            @RequestHeader(value = "locale", defaultValue = "en") String locale) {
        log.info("Received delete menu request with locale: {}", locale);
        ResponseDto<String> response = menuService.deleteMenu(id, userId, locale);
        return ResponseEntity.ok(response);
    }


    @GetMapping("/{id}")
    public ResponseEntity<ResponseDto<MenuDto<MenuResponse>>> getMenuById(
            @PathVariable UUID id,
            @RequestHeader(value = "locale", defaultValue = "en") String locale) {
        
        log.info("Request received to fetch menu with id: {} and locale: {}", id, locale);
        ResponseDto<MenuDto<MenuResponse>> response = menuService.getMenuById(id, locale);
        return ResponseEntity.ok(response);
    }


  
    @PostMapping("/assign-structure-categories")
    public ResponseDto<Void> assignMenuStructureAndCategories(
        @Valid @RequestBody AssignMenuStructureCategoriesRequest request,
        @RequestHeader("User-ID") String userId,
        @RequestHeader(value = "locale", defaultValue = "en") String locale) {
    
    log.info("Assigning menu structure and categories with locale: {}", locale);
    return menuService.assignMenuStructureAndCategories(request, userId, locale);
    }



@PutMapping("/publish/{menuId}")
public ResponseEntity<ResponseDto<MenuDto<MenuResponse>>> publishMenu(
        @PathVariable UUID menuId,
        @RequestHeader("User-ID") String userId,
        @RequestHeader(value = "locale", defaultValue = "en") String locale) {
    
    ResponseDto<MenuDto<MenuResponse>> response = menuService.publishMenu(menuId, userId, locale);
    return ResponseEntity.ok(response);
        }

@GetMapping("/{menuId}/structure/{menuStructureId}")
public ResponseEntity<ResponseDto<MenuDto<MenuResponse>>> getMenuDetails(
        @PathVariable UUID menuId,
        @PathVariable UUID menuStructureId,
        @RequestHeader(value = "locale", defaultValue = "en") String locale) {
    
    log.info("Request received to fetch menu details for menuId: {} and menuStructureId: {}", menuId, menuStructureId);
    ResponseDto<MenuDto<MenuResponse>> response = menuService.getMenuDetails(menuId, menuStructureId, locale);
    return ResponseEntity.ok(response);
        }

/**
 * Assigns a menu to one or more restaurant groups.
 *
 * @param request the assignment request containing menu ID and restaurant group IDs
 * @param userId  the user ID from the request header (required)
 * @param locale  locale code for localized responses (default: "en")
 * @return response indicating success of the assignment operation
 */
@PostMapping("/assign-menu")
public ResponseEntity<ResponseDto<Void>> assignMenuToRestaurantGroup(
        @Valid @RequestBody AssignMenuToRestaurantGroupRequest request,
        @RequestHeader("User-ID") String userId,
        @RequestHeader(value = "locale", defaultValue = "en") String locale) {

    log.info("Received request to assign menu to restaurant group. GroupId: {}, MenuId: {}", 
        request.getRestaurantGroupId(), request.getMenuId());
        
    ResponseDto<Void> response = menuService.assignMenuToRestaurantGroup(request, userId, locale);
    return ResponseEntity.ok(response);
        }

/**
 * Retrieves restaurant groups assigned to a specific menu.
 * Returns a paginated and filterable list of restaurant groups with assignment details.
 *
 * @param menuId  the UUID of the menu to get restaurant groups for
 * @param locale  locale code for localized responses (default: "en")
 * @param status  optional filter by entity status (ACTIVE, INACTIVE, etc.)
 * @param search  optional search term for text search
 * @param page    optional page number for pagination
 * @param size    optional page size for pagination
 * @return response containing paginated list of restaurant groups assigned to the menu
 */
@GetMapping("/assign-restaurant-group/{menuId}")
public ResponseEntity<ResponseDto<MenuRestaurantGroupListResponse>> getRestaurantGroupsByMenuId(
        @PathVariable UUID menuId,
        @RequestHeader(value = "locale", defaultValue = "en") String locale,
        @RequestParam(required = false) EntityStatus status,
        @RequestParam(required = false) String search,
        @RequestParam(required = false) Integer page,
        @RequestParam(required = false) Integer size
) {
    log.info("Request received to fetch restaurant groups for menuId: {} with locale: {}, status: {}, search: {}, page: {}, size: {}",
            menuId, locale, status, search, page, size);
    ResponseDto<MenuRestaurantGroupListResponse> response = menuService.getRestaurantGroupDetailsByMenuId(
            menuId, locale, status, search, page, size);
    return ResponseEntity.ok(response);
}


@DeleteMapping("{menuId}/restaurant-groups/{restaurantGroupId}")
public ResponseEntity<ResponseDto<Void>> removeRestaurantGroupFromMenu(
        @PathVariable UUID menuId,
        @PathVariable UUID restaurantGroupId,
        @RequestHeader(name = "locale", defaultValue = "en") String locale
) {
    ResponseDto<Void> response = menuService.removeRestaurantGroupFromMenu(menuId, restaurantGroupId, locale);
    return ResponseEntity.ok(response);
}

@GetMapping("/{menuMasterId}/versions")
public ResponseEntity<ResponseDto<MenuVersionsResponse>> getMenuVersions(
        @PathVariable UUID menuMasterId,
        @RequestParam(required = false) MenuStatus status,
        @RequestHeader(value = "locale", defaultValue = "en") String locale) {

    log.info("Request received to fetch menu versions for menuMasterId: {} with status filter: {}", menuMasterId, status);
    ResponseDto<MenuVersionsResponse> response = menuService.getMenuVersions(menuMasterId, status, locale);
    return ResponseEntity.ok(response);
        }

/**
 * Duplicates an existing menu to create a new menu version.
 * Can optionally duplicate associated items and categories based on the request flag.
 *
 * @param menuId  the UUID of the menu to duplicate
 * @param request the duplicate request containing duplication options
 * @param userId  the user ID from the request header (required)
 * @param locale  locale code for localized responses (default: "en")
 * @return response containing the newly created duplicated menu details
 */
@PostMapping("/{menuId}/duplicate")
public ResponseEntity<ResponseDto<MenuDto<MenuResponse>>> duplicateMenu(
        @PathVariable UUID menuId,
        @Valid @RequestBody DuplicateMenuRequest request,
        @RequestHeader("User-ID") String userId,
        @RequestHeader(value = "locale", defaultValue = "en") String locale) {

    log.info("Received duplicate menu request for id: {} with flag: {} and locale: {}", 
        menuId, request.getIsDuplicate(), locale);
    ResponseDto<MenuDto<MenuResponse>> response = menuService.duplicateMenu(menuId, userId, request, locale);
    return ResponseEntity.ok(response);
        }

/**
 * Retrieves items for a specific menu and category combination.
 * Supports filtering by restaurant, text search, alcohol type, and pagination.
 * The category parameter can be a specific category ID or a wildcard.
 *
 * @param menuId                the UUID of the menu
 * @param categoryIdOrWildcard  the category ID or wildcard pattern (required)
 * @param restaurantId          optional filter by restaurant ID
 * @param search                optional search term for text search
 * @param orderType             optional filter by order type (DINE_IN, TAKEAWAY)
 * @param alcoholType           optional filter by alcohol type (ALCOHOLIC, NON_ALCOHOLIC)
 * @param page                  optional page number for pagination
 * @param size                  optional page size for pagination
 * @param locale                locale code for localized responses (default: "en")
 * @return response containing paginated list of items for the menu and category
 */
@GetMapping("/{menuId}/items")
public ResponseEntity<ResponseDto<MenuItemListResponse>> getItemsByMenuAndCategory(
        @PathVariable UUID menuId,
        @RequestParam(name = "category", required = true) String categoryIdOrWildcard,
        @RequestParam(name = "restaurantId", required = false) UUID restaurantId,
        @RequestParam(name = "search", required = false) String search,
        @RequestParam(name = "orderType", required = false) String orderType,
        @RequestParam(name = "alcoholType", required = false) String alcoholType,
        @RequestParam(required = false) Integer page,
        @RequestParam(required = false) Integer size,
        @RequestHeader(value = "locale", defaultValue = "en") String locale) {
    ResponseDto<MenuItemListResponse> response = menuService.getItemsByMenuAndCategory(menuId, categoryIdOrWildcard, restaurantId, locale, search, orderType, alcoholType, page, size);
    return ResponseEntity.ok(response);
        }


@DeleteMapping("/{menuId}/restaurants/{restaurantId}")
public ResponseEntity<ResponseDto<Void>> removeRestaurantFromMenu(
        @PathVariable UUID menuId,
        @PathVariable UUID restaurantId,
        @RequestHeader(value = "locale", defaultValue = "en") String locale) {
    
    log.info("Received request to remove restaurant {} from menu {}", restaurantId, menuId);
    ResponseDto<Void> response = menuService.removeRestaurantFromMenu(menuId, restaurantId, locale);
    return ResponseEntity.ok(response);
        }

/**
 * Retrieves restaurants assigned to a specific menu.
 * Returns a paginated and filterable list of restaurants with menu assignment details.
 *
 * @param menuId           the UUID of the menu to get restaurants for
 * @param menuStatus       optional filter by restaurant menu mapping status
 * @param restaurantGroupId optional filter by restaurant group ID
 * @param search           optional search term for text search
 * @param page             optional page number for pagination
 * @param size             optional page size for pagination
 * @param locale           locale code for localized responses (default: "en")
 * @return response containing paginated list of restaurants assigned to the menu
 */
@GetMapping("/{menuId}/restaurants")
public ResponseEntity<ResponseDto<RestaurantMenuDtoListResponse>> getRestaurantsByMenuId(
        @PathVariable UUID menuId,
        @RequestParam(required = false) RestaurantMenuMappingStatus menuStatus,  
        @RequestParam(required = false) UUID restaurantGroupId,
        @RequestParam(required = false) String search,
        @RequestParam(required = false) Integer page,
        @RequestParam(required = false) Integer size,
        @RequestHeader(value = "locale", defaultValue = "en") String locale) {
    
    log.info("Request received to fetch restaurants for menu {} with filters: menuStatus={}, restaurantGroupId={}, search={}, page={}, size={}, locale={}", 
        menuId, menuStatus, restaurantGroupId, search, page, size, locale);
    
    ResponseDto<RestaurantMenuDtoListResponse> response = menuService.getRestaurantsByMenuId(
        menuId, locale, menuStatus, restaurantGroupId, search, page, size);
    
    return ResponseEntity.ok(response);
        }




/**
 * Schedules a menu to be published to one or more restaurants at a specific UTC time.
 * The menu will be automatically published to the specified restaurants at the scheduled time.
 *
 * @param request the schedule request containing menu ID, restaurant IDs, and scheduled publish time (UTC)
 * @param userId  the user ID from the request header (required)
 * @param locale  locale code for localized responses (default: "en")
 * @return response containing success message
 */
@PostMapping("/schedule")
public ResponseEntity<ResponseDto<String>> scheduleMenuForRestaurants(
        @Valid @RequestBody ScheduleMenuRequest request,
        @RequestHeader("User-ID") String userId,
        @RequestHeader(value = "locale", defaultValue = "en") String locale) {
    
    log.info("Received schedule menu request for menuId: {} with {} restaurants at UTC time: {}", 
        request.getMenuId(), request.getRestaurantIds().size(), request.getSchedulePublishTime());
    
    ResponseDto<String> response = menuService.scheduleMenuForRestaurants(request, userId, locale);
    return ResponseEntity.ok(response);
        }

/**
 * Changes the availability status of an item for a specific restaurant and menu combination.
 *
 * @param request the availability change request containing restaurant ID, menu ID, item ID, and availability status
 * @param userId  the user ID from the request header (required)
 * @param locale  locale code for localized responses (default: "en")
 * @return response containing success message
 */
@PutMapping("/item-availability")
public ResponseEntity<ResponseDto<String>> changeItemAvailability(
        @Valid @RequestBody ItemAvailabilityChangeRequest request,
        @RequestHeader("User-ID") String userId,
        @RequestHeader(value = "locale", defaultValue = "en") String locale) {

    log.info("Received request to change item availability. RestaurantId: {}, MenuId: {}, ItemId: {}, IsAvailable: {}", 
        request.getRestaurantId(), request.getMenuId(), request.getItemId(), request.getIsAvailable());
        
    ResponseDto<String> response = menuService.changeItemAvailability(request, userId, locale);
    return ResponseEntity.ok(response);
}

/**
 * Retrieves Buy-X-Get-Y (BXGY) discount details for a specific item in a menu and restaurant context.
 *
 * @param itemId       the UUID of the item to get BXGY discount details for
 * @param menuId       the UUID of the menu
 * @param restaurantId the UUID of the restaurant
 * @param locale       locale code for localized responses (default: "en")
 * @return response containing BXGY discount details including discount rules and eligibility
 */
@GetMapping("/bxgy-discount-details")
public ResponseEntity<ResponseDto<BxgyDiscountDetailsResponse>> getBxgyDiscountDetails(
        @RequestParam UUID itemId,
        @RequestParam UUID menuId,
        @RequestParam UUID restaurantId,
        @RequestHeader(value = "locale", defaultValue = "en") String locale) {
    
    log.info("Request received to fetch BXGY discount details for itemId: {}, menuId: {}, and restaurantId: {}", itemId, menuId, restaurantId);
    
    ResponseDto<BxgyDiscountDetailsResponse> response = menuService.getBxgyDiscountDetails(itemId, menuId, restaurantId, locale);
    return ResponseEntity.ok(response);
}

@PutMapping("/restore")
public ResponseEntity<ResponseDto<Void>> restoreMenus(
        @Valid @RequestBody RestoreEntitiesRequest request,
        @RequestHeader("User-ID") String userId,
        @RequestHeader(value = "locale", defaultValue = "en") String locale) {
    log.info("Received restore request with locale: {}", locale);
    ResponseDto<Void> response = menuService.restoreMenus(request.getIds(), userId, locale);
    return ResponseEntity.ok(response);
}
    }




