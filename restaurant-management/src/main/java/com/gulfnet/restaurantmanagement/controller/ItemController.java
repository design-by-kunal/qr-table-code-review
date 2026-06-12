package com.gulfnet.restaurantmanagement.controller;

import com.gulfnet.shared_library.entity.BulkUpload;
import com.gulfnet.shared_library.model.request.AssignModifierGroupsRequest;
import com.gulfnet.shared_library.model.request.ItemRequest;
import com.gulfnet.shared_library.model.request.RestoreEntitiesRequest;
import com.gulfnet.shared_library.model.response.dto.ItemDto;
import com.gulfnet.shared_library.model.response.dto.ItemResponse;
import com.gulfnet.shared_library.model.response.dto.ModifierGroupAssignmentListResponse;
import com.gulfnet.shared_library.model.response.dto.ItemListResponse;
import com.gulfnet.shared_library.model.response.dto.ItemModifierItemListResponse;
import com.gulfnet.shared_library.model.response.dto.ResponseDto;
import com.gulfnet.restaurantmanagement.service.ItemService;
import com.gulfnet.restaurantmanagement.service.BulkItemUploadService;
import com.gulfnet.shared_library.model.response.dto.ItemModifierItemListResponseEnhanced;
import com.gulfnet.shared_library.model.response.dto.RestaurantItemsAndMenusResponse;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import lombok.RequiredArgsConstructor;

import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/v1/items")
@RequiredArgsConstructor
public class ItemController {

    private final ItemService itemService;
    private final BulkItemUploadService bulkItemUploadService; 

    @PostMapping
    public ResponseEntity<ResponseDto<ItemDto<ItemResponse>>> createItem(
            @Valid @RequestBody ItemRequest request,
            @RequestHeader("User-ID") String userId,
            @RequestHeader(value = "locale", defaultValue = "en") String locale) {
        log.info("Received create request with locale: {}", locale);
        ResponseDto<ItemDto<ItemResponse>> saved = 
            itemService.createItem(userId, request, locale);
        return ResponseEntity.ok(saved);
    }

    @PutMapping("/{id}")
    public ResponseEntity<ResponseDto<ItemDto<ItemResponse>>> updateItem(
            @PathVariable UUID id,
            @Valid @RequestBody ItemRequest request,
            @RequestHeader("User-ID") String userId,
            @RequestHeader(value = "locale", defaultValue = "en") String locale) {
        log.info("Received update request with locale: {}", locale);
        ResponseDto<ItemDto<ItemResponse>> updated = 
            itemService.updateItem(id, request, userId, locale);
        return ResponseEntity.ok(updated);
    }

    /**
     * Retrieves a paginated and filterable list of items.
     * Supports filtering by status, modifier assignment, deletion status, text search, item order type, and alcohol type.
     * Can optionally return thumbnail images instead of full images.
     *
     * @param page              optional page number for pagination
     * @param size              optional page size for pagination
     * @param status            optional filter by item status
     * @param hasModifierAssigned optional filter by whether item has modifiers assigned
     * @param search            optional search term for text search
     * @param sortBy            field to sort by (default: "createdAt")
     * @param direction         sort direction (default: DESC)
     * @param locale            locale code for localized responses (default: "en")
     * @param thumb             whether to return thumbnail images instead of full images (default: false)
     * @param isDeleted         optional filter by deletion status
     * @param itemOrderType     optional filter by item order type (DINE_IN, TAKEAWAY, BOTH)
     * @param alcoholType       optional filter by alcohol type (ALCOHOLIC, NON_ALCOHOLIC)
     * @return response containing paginated list of items with filters applied
     */
    @GetMapping
    public ResponseEntity<ResponseDto<ItemListResponse>> getItems(
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Boolean hasModifierAssigned,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "DESC") Sort.Direction direction,
            @RequestHeader(value = "locale", defaultValue = "en") String locale,
            @RequestParam(name = "thumb", required = false, defaultValue = "false") Boolean thumb,
            @RequestParam(required = false) Boolean isDeleted,
            @RequestParam(required = false) String itemOrderType,
            @RequestParam(required = false) String alcoholType) {
        log.info("Received get items list request with locale: {}", locale);
        ResponseDto<ItemListResponse> response = itemService.getItems(page, size, status, hasModifierAssigned, search, sortBy, direction, locale, thumb, isDeleted, itemOrderType, alcoholType);
        return ResponseEntity.ok(response);
    }

 

    @DeleteMapping("/{id}")
    public ResponseEntity<ResponseDto<String>> deleteItem(
            @PathVariable UUID id,
            @RequestHeader("User-ID") String userId,
            @RequestHeader(value = "locale", defaultValue = "en") String locale) {
        log.info("Received delete request with locale: {}", locale);
        ResponseDto<String> response = itemService.deleteItem(id, userId, locale);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{id}")
    public ResponseEntity<ResponseDto<ItemDto<ItemResponse>>> getItemById(
            @PathVariable UUID id,
            @RequestHeader(value = "locale", defaultValue = "en") String locale,
            @RequestParam(name = "thumb", required = false, defaultValue = "false") Boolean thumb) {
            log.info("Received get item by id request with locale: {}", locale);
        ResponseDto<ItemDto<ItemResponse>> response = itemService.getItemById(id, locale, thumb);
        return ResponseEntity.ok(response);
    }


        @GetMapping("/bulkUpload/template")
        public ResponseEntity<Void> downloadBulkUploadTemplate(
                @RequestHeader(value = "locale", defaultValue = "en") String locale,
                HttpServletResponse response) throws IOException {
            log.info("Received request to download item bulk upload template with locale: {}", locale);
            return bulkItemUploadService.downloadTemplate(response, locale);
        }

        /**
         * Processes a bulk upload of items from a CSV file.
         * Optionally accepts an image ZIP file for item images.
         * The upload is processed asynchronously and returns immediately with an ACCEPTED status.
         *
         * @param file      the CSV file containing item data (required)
         * @param imageZipFile optional ZIP file containing item images
         * @param action    optional upload action parameter
         * @param utfType   UTF encoding type for CSV file (default: UTF_8)
         * @param userId    the user ID from the request header (required)
         * @param userRole  the user role from the request header (required)
         * @param language  language code for processing (default: "en")
         * @param request   HTTP servlet request to extract locale header
         * @return response containing bulk upload record with status (ACCEPTED status code)
         * @throws IOException if file processing fails
         */
        @PostMapping("/bulkUpload")
        public ResponseEntity<ResponseDto<BulkUpload>> bulkUpload(
                @RequestParam("file") MultipartFile file,
                @RequestParam(value = "imageZipFile", required = false) MultipartFile imageZipFile,
                @RequestParam(value = "upload", required = false) String action,
                @RequestParam(value = "utf_type", defaultValue = "UTF_8") String utfType,
                @RequestHeader("User-ID") String userId,
                @RequestHeader(value = "User-Role", required = true) String userRole,
                @RequestHeader(value = "locale", defaultValue = "en") String language,
                HttpServletRequest request) throws IOException {
            
            String localeHeader = request.getHeader("locale");
            ResponseDto<BulkUpload> response = bulkItemUploadService.processBulkUpload(
                file, imageZipFile, action, utfType, language, userId, userRole, localeHeader);
            return ResponseEntity.status(HttpStatus.ACCEPTED).body(response);
        }


    /**
     * Assign modifier groups to an item
     * 
     * @param request The assignment request containing item ID and modifier group assignments
     * @return Response with assignment results
     */
    @PostMapping("/assign-modifier-groups")
    public ResponseEntity<ResponseDto<ModifierGroupAssignmentListResponse>> assignModifierGroupsToItem(
            @Valid @RequestBody AssignModifierGroupsRequest request) {
        
        ResponseDto<ModifierGroupAssignmentListResponse> response = itemService.assignModifierGroupsToItem(request);
        return ResponseEntity.ok(response);
    }

    /**
     * Unassign a modifier group from an item
     * 
     * @param itemId The item ID to unassign the modifier group from
     * @param modifierGroupId The modifier group ID to unassign
     * @param updaterId The ID of the user performing the unassignment
     * @param updaterRole The role of the user performing the unassignment
     * @return Response with unassignment results
     */
    @DeleteMapping("/{itemId}/modifier-groups/{modifierGroupId}")
    public ResponseEntity<ResponseDto<ModifierGroupAssignmentListResponse>> unassignModifierGroupFromItem(
            @PathVariable UUID itemId,
            @PathVariable UUID modifierGroupId,
            @RequestHeader("User-ID") String updaterId,
            @RequestHeader("User-Role") String updaterRole) {

        log.info("Request received to unassign modifier group {} from Item ID: {} by Updater ID: {} with role: {}",
                modifierGroupId, itemId, updaterId, updaterRole);
        
        ResponseDto<ModifierGroupAssignmentListResponse> response = itemService.unassignModifierGroupFromItem(itemId, modifierGroupId, updaterId, updaterRole);
        
        log.info("Successfully unassigned modifier group {} from Item ID: {}", modifierGroupId, itemId);
        return ResponseEntity.ok(response);
    }

    /**
     * Retrieves an item with its associated modifier groups and modifier items.
     * Returns paginated modifier items for the specified item.
     *
     * @param page   optional page number for pagination
     * @param size   optional page size for pagination
     * @param id     the UUID of the item to get modifier details for
     * @param locale locale code for localized responses (default: "en")
     * @return response containing item details with paginated list of modifier items
     */
    @GetMapping("/with-modifier-items/{id}")
    public ResponseEntity<ResponseDto<ItemModifierItemListResponse>> getItemWithModifiersItems(
        @RequestParam(required = false) Integer page,
        @RequestParam(required = false) Integer size,
            @PathVariable UUID id,
            @RequestHeader(value = "locale", defaultValue = "en") String locale) {

        log.info("Received get item with modifiers request: {}, locale: {}", id, locale);

        ResponseDto<ItemModifierItemListResponse> response =
                itemService.getItemWithModifiersItems(page, size, id, locale);

        return ResponseEntity.ok(response);
}

    /**
     * Retrieves an item with enhanced modifier details including restaurant and menu context.
     * Includes pricing, availability, and promotion information specific to the restaurant and menu.
     *
     * @param id          the UUID of the item to get enhanced modifier details for
     * @param restaurantId the UUID of the restaurant (required)
     * @param menuId      the UUID of the menu (required)
     * @param locale      locale code for localized responses (default: "en")
     * @param promotionId optional promotion ID to include promotion-specific pricing
     * @return response containing enhanced item details with modifiers, pricing, and promotion information
     */
    @GetMapping("/with-modifier-items-enhanced/{id}")
    public ResponseEntity<ResponseDto<ItemModifierItemListResponseEnhanced>> getItemWithModifiersItemsEnhanced(
    @PathVariable UUID id,
    @RequestParam UUID restaurantId,
    @RequestParam UUID menuId,
    @RequestHeader(value = "locale", defaultValue = "en") String locale,
    @RequestParam(required = false) UUID promotionId) {

    log.info("Received get item with modifiers enhanced request: {}, restaurant: {}, menu: {}, promotion: {}, locale: {}", 
        id, restaurantId, menuId, promotionId, locale);

    ResponseDto<ItemModifierItemListResponseEnhanced> response =
            itemService.getItemWithModifiersItemsEnhanced(id, restaurantId, menuId, locale, promotionId);

    return ResponseEntity.ok(response);
}

    /**
     * Retrieves all items and menus for a specific restaurant.
     * Returns a paginated and filterable list of items with their associated menu information.
     *
     * @param restaurantId the UUID of the restaurant to get items and menus for
     * @param page         optional page number for pagination
     * @param size         optional page size for pagination
     * @param isAvailable  optional filter by item availability status
     * @param search       optional search term for text search
     * @param sortBy       field to sort by (default: "createdAt")
     * @param direction    sort direction (default: DESC)
     * @param locale       locale code for localized responses (default: "en")
     * @return response containing paginated list of items with menu associations for the restaurant
     */
    @GetMapping("/restaurant/{restaurantId}/items-and-menus")
    public ResponseEntity<ResponseDto<RestaurantItemsAndMenusResponse>> getRestaurantItemsAndMenus(
            @PathVariable UUID restaurantId,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            @RequestParam(required = false) Boolean isAvailable,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "DESC") Sort.Direction direction,
            @RequestHeader(value = "locale", defaultValue = "en") String locale) {
        
        log.info("Received get restaurant items and menus request for restaurant: {}, page: {}, size: {}, isAvailable: {}, search: {}, sortBy: {}, direction: {}, locale: {}", 
                restaurantId, page, size, isAvailable, search, sortBy, direction, locale);
        
        ResponseDto<RestaurantItemsAndMenusResponse> response = 
                itemService.getRestaurantItemsAndMenus(restaurantId, page, size, isAvailable, search, sortBy, direction, locale);
        
        return ResponseEntity.ok(response);
    }

    @PutMapping("/restore")
    public ResponseEntity<ResponseDto<Void>> restoreItems(
            @Valid @RequestBody RestoreEntitiesRequest request,
            @RequestHeader("User-ID") String userId,
            @RequestHeader(value = "locale", defaultValue = "en") String locale) {
        log.info("Received restore request with locale: {}", locale);
        ResponseDto<Void> response = itemService.restoreItems(request.getIds(), userId, locale);
        return ResponseEntity.ok(response);
    }


}