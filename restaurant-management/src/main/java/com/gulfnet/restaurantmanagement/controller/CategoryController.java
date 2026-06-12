package com.gulfnet.restaurantmanagement.controller;

import com.gulfnet.restaurantmanagement.service.CategoryService;
import com.gulfnet.shared_library.model.request.CategoryRequest;
import com.gulfnet.shared_library.model.response.dto.CategoryWrapperResponse;
import com.gulfnet.shared_library.model.response.dto.ResponseDto;
import com.gulfnet.shared_library.enums.EntityStatus;
import org.springframework.data.domain.Sort;
import jakarta.validation.Valid;

import lombok.RequiredArgsConstructor;

import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/categories")
@RequiredArgsConstructor
public class CategoryController {

    private final CategoryService categoryService;

    @PostMapping
    public ResponseEntity<ResponseDto<?>> createCategory(
            @RequestHeader("User-ID") String creatorId,
            @Valid @RequestBody CategoryRequest request,
            @RequestHeader(value = "locale", defaultValue = "en") String locale
    ) {
        ResponseDto<?> response = categoryService.createCategory(request, creatorId, locale);
        return new ResponseEntity<>(response, HttpStatus.CREATED);
    }

    @GetMapping("/{categoryId}")
    public ResponseEntity<ResponseDto<?>> getCategoryById(
            @PathVariable UUID categoryId,
            @RequestHeader(value = "locale", defaultValue = "en") String locale) {
        ResponseDto<?> response = categoryService.getCategoryById(categoryId, locale);
        return ResponseEntity.ok(response);
    }

    /**
     * Retrieves a paginated and filterable list of categories.
     * Supports filtering by status, menu structure, parent category, and text search.
     *
     * @param page           page number for pagination (default: 1)
     * @param size           page size for pagination (default: 10)
     * @param status         optional filter by entity status (ACTIVE, INACTIVE, etc.)
     * @param menuStructureId optional filter by menu structure ID
     * @param categoryId     optional filter by parent category ID
     * @param search         optional search term for text search
     * @param sortBy         field to sort by (default: "createdAt")
     * @param direction      sort direction (default: DESC)
     * @param locale         locale code for localized responses (default: "en")
     * @return response containing paginated list of categories with filters applied
     */
    @GetMapping
    public ResponseEntity<ResponseDto<CategoryWrapperResponse>> getCategories(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) EntityStatus status,
            @RequestParam(required = false) UUID menuStructureId,
            @RequestParam(required = false) UUID categoryId,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "DESC") Sort.Direction direction,
            @RequestHeader(value = "locale", defaultValue = "en") String locale) {
        
        ResponseDto<CategoryWrapperResponse> response = categoryService.getCategories(page, size, status, menuStructureId, categoryId, search, sortBy, direction);
        return ResponseEntity.ok(response);
    }

    @PutMapping("/{categoryId}")
    public ResponseEntity<ResponseDto<?>> updateCategory(
            @PathVariable UUID categoryId,
            @RequestHeader("User-ID") String updaterId,
            @Valid @RequestBody CategoryRequest request,
            @RequestHeader(value = "locale", defaultValue = "en") String locale
    ) {
        ResponseDto<?> response = categoryService.updateCategory(categoryId, request, updaterId, locale);
        return new ResponseEntity<>(response, HttpStatus.OK);
    }
    
    @DeleteMapping("/{categoryId}")
    public ResponseEntity<ResponseDto<Void>> deleteCategory(
        @PathVariable UUID categoryId, 
        @RequestHeader("User-ID") String deleterId
    ) {
        ResponseDto<Void> response = categoryService.deleteCategory(categoryId, deleterId);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/menu/{menuId}/combo-categories")
    public ResponseEntity<ResponseDto<CategoryWrapperResponse>> getActiveComboCategoriesByMenuId(
            @PathVariable UUID menuId,
            @RequestHeader(value = "locale", defaultValue = "en") String locale) {
        ResponseDto<CategoryWrapperResponse> response = categoryService.getActiveComboCategoriesByMenuId(menuId, locale);
        return ResponseEntity.ok(response);
    }

}
