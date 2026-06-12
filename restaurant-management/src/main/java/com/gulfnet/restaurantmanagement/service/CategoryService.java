package com.gulfnet.restaurantmanagement.service;

import java.util.UUID;

import com.gulfnet.shared_library.model.request.CategoryRequest;
import com.gulfnet.shared_library.model.response.dto.CategoryWrapperResponse;
import com.gulfnet.shared_library.model.response.dto.ResponseDto;
import com.gulfnet.shared_library.enums.EntityStatus;
import org.springframework.data.domain.Sort;

public interface CategoryService {

    ResponseDto<?> createCategory(CategoryRequest request, String creatorId, String locale);
    ResponseDto<?> updateCategory(UUID categoryId, CategoryRequest request, String updaterId, String locale);
    ResponseDto<?> getCategoryById(UUID categoryId, String locale);  // Change return type to accept both responses
    ResponseDto<CategoryWrapperResponse> getCategories(int page, int size, EntityStatus status, UUID menuStructureId, UUID categoryId, String search, String sortBy, Sort.Direction direction);
    ResponseDto<Void> deleteCategory(UUID categoryId, String deleterId);
    ResponseDto<CategoryWrapperResponse> getActiveComboCategoriesByMenuId(UUID menuId, String locale);

}
