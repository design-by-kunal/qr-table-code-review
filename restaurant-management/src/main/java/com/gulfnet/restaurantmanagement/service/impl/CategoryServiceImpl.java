package com.gulfnet.restaurantmanagement.service.impl;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.stream.Collectors;
import com.gulfnet.shared_library.repository.UserRepository;
import com.gulfnet.shared_library.entity.User;

import com.gulfnet.shared_library.entity.Category;
import com.gulfnet.shared_library.entity.MenuCategoryMapping;
import com.gulfnet.shared_library.model.response.dto.*;
import com.gulfnet.shared_library.repository.CategoryRepository;
import com.gulfnet.shared_library.repository.CategoryTranslationRepository;
import com.gulfnet.shared_library.model.response.dto.SubcategoryDataResponse;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import com.gulfnet.shared_library.model.response.dto.SubcategoryResponse;

import com.gulfnet.restaurantmanagement.service.CategoryService;
import com.gulfnet.restaurantmanagement.service.AuditTrailService;
import com.gulfnet.restaurantmanagement.util.MessageUtil;
import com.gulfnet.shared_library.enums.ActionType;
import com.gulfnet.shared_library.entity.Restaurant;
import com.gulfnet.shared_library.repository.RestaurantRepository;
import com.gulfnet.shared_library.util.TranslationUtils;
import com.gulfnet.restaurantmanagement.config.LocalizationProperties;
import com.gulfnet.shared_library.entity.CategoryTranslation;
import com.gulfnet.shared_library.entity.MenuStructure;
import com.gulfnet.shared_library.model.request.CategoryRequest;
import com.gulfnet.shared_library.model.request.CategoryTranslationRequest;
import com.gulfnet.shared_library.repository.MenuStructureRepository;
import com.gulfnet.shared_library.repository.MenuRepository;
import com.gulfnet.shared_library.repository.MenuCategoryMappingRepository;
import com.gulfnet.shared_library.repository.RestaurantMenuMappingRepository;

import org.springframework.transaction.annotation.Transactional;
import lombok.RequiredArgsConstructor;
import com.gulfnet.shared_library.model.response.dto.CategoryResponse;
import com.gulfnet.shared_library.model.response.dto.CategoryWrapperResponse;
import com.gulfnet.shared_library.model.response.dto.CategoryListData;
import com.gulfnet.shared_library.model.response.dto.PaginationMetaData;
import com.gulfnet.shared_library.enums.EntityStatus;
import com.gulfnet.shared_library.enums.MenuStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

@Service
@RequiredArgsConstructor
public class CategoryServiceImpl implements CategoryService {

    private final MenuStructureRepository menuStructureRepository;
    private final UserRepository userRepository;
    private final CategoryRepository categoryRepository;
    private final CategoryTranslationRepository translationRepository;
    private final MenuRepository menuRepository;
    private final MenuCategoryMappingRepository menuCategoryMappingRepository;
    private final RestaurantMenuMappingRepository restaurantMenuMappingRepository;
    private final MessageUtil messageUtil;
    private final LocalizationProperties localizationProperties;
    private final AuditTrailService auditTrailService;
    private final RestaurantRepository restaurantRepository;

    // Constants
    private static final String DEFAULT_NO_TRANSLATIONS = "No translations";
    private static final String ENTITY_TYPE_CATEGORY = "CATEGORY";
    private static final String LABEL_CATEGORY = "Category";
    private static final String LABEL_SUBCATEGORY = "Subcategory";
    private static final String MSG_CATEGORY_NOT_FOUND = "category.not.found";

    /**
     * Creates a new category or subcategory.
     * Validates menu structure, parent category (if provided), display order uniqueness, and translation uniqueness.
     * Prevents creation if the menu structure has active published menus.
     *
     * @param request Category creation request containing menu structure ID, parent category ID (optional), translations, status, and display order
     * @param creatorId UUID of the user creating the category
     * @param locale Locale for message localization
     * @return ResponseDto containing the created category or subcategory data
     */
    @Transactional
    public ResponseDto<?> createCategory(CategoryRequest request, String creatorId, String locale) {
        Locale userLocale = Locale.forLanguageTag(locale);

        // displayOrder optional for creation; will default to 0 if not provided

        MenuStructure menuStructure = menuStructureRepository.findByIdAndIsDeletedFalse(request.getMenuStructureId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        messageUtil.getMessage("category.create.error.menuStructure.notfound", userLocale)));

        // Validate that menu structure doesn't have active published menus
        if (menuRepository.existsByMenuStructureIdAndStatusAndIsDeletedFalse(
                menuStructure.getId(), MenuStatus.PUBLISHED)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    messageUtil.getMessage("category.create.error.assigned.to.active.published.menu", userLocale));
        }

        User creator = userRepository.findById(UUID.fromString(creatorId))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        messageUtil.getMessage("user.not.found", userLocale)));

        Category parentCategory = null;
        if (request.getParentCategoryId() != null) {
            parentCategory = categoryRepository.findByIdAndIsDeletedFalse(request.getParentCategoryId())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                            messageUtil.getMessage(MSG_CATEGORY_NOT_FOUND, userLocale)));
        }

        // Enforce unique displayOrder within level only when provided in the request
        Integer requestedOrder = request.getDisplayOrder() != null ? request.getDisplayOrder() : 0;
        
        // Validate display order is not negative
        if (request.getDisplayOrder() != null && request.getDisplayOrder() < 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    messageUtil.getMessage(parentCategory == null ?
                            "category.create.error.displayOrder.negative" :
                            "subcategory.create.error.displayOrder.negative", userLocale));
        }
        
        if (request.getDisplayOrder() != null) {
            boolean orderConflict;
            if (parentCategory == null) {
                orderConflict = categoryRepository.existsByMenuStructure_IdAndParentCategoryIsNullAndDisplayOrderAndIsDeletedFalse(
                        menuStructure.getId(), requestedOrder);
            } else {
                orderConflict = categoryRepository.existsByMenuStructure_IdAndParentCategory_IdAndDisplayOrderAndIsDeletedFalse(
                        menuStructure.getId(), parentCategory.getId(), requestedOrder);
            }
            if (orderConflict) {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        messageUtil.getMessage(parentCategory == null ?
                                "category.create.error.displayOrder.duplicate" :
                                "subcategory.create.error.displayOrder.duplicate", userLocale));
            }
        }

        Category category = Category.builder()
                .menuStructure(menuStructure)
                .parentCategory(parentCategory)
                .status(request.getStatus())
                .displayOrder(requestedOrder)
                .isCombo(request.getIsCombo() != null && request.getIsCombo())
                .isDeleted(false)
                .createdAt(OffsetDateTime.now(ZoneOffset.UTC))
                .createdBy(creator)
                .build();

        categoryRepository.save(category);

        boolean isTopLevelCategory = (parentCategory == null);
        UUID parentId = (parentCategory != null) ? parentCategory.getId() : null;

        // Filter out empty/blank translation entries (e.g., language present but name blank)
        List<CategoryTranslationRequest> validTranslations = request.getTranslations().stream()
                .filter(t -> t.getLanguageCode() != null && t.getName() != null && !t.getName().trim().isEmpty())
                .toList();

        // First, validate all translations before saving any
        validTranslations.forEach(t -> {
            boolean exists;
            String duplicateKey;
        
            if (isTopLevelCategory) {
                exists = translationRepository.existsByNameAndLanguageCodeAndCategory_MenuStructure_IdAndCategory_ParentCategoryIsNull(
                        t.getName(), t.getLanguageCode(), menuStructure.getId());
                duplicateKey = "category.create.error.duplicate.translation";
            } else {
                exists = translationRepository.existsByNameAndLanguageCodeAndCategory_ParentCategory_Id(
                        t.getName(), t.getLanguageCode(), parentId);
                duplicateKey = "subcategory.create.error.duplicate.translation";
            }
        
            if (exists) {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        messageUtil.getMessage(duplicateKey, userLocale));
            }
        });
        
        // Then save all translations
        List<CategoryTranslation> translations = validTranslations.stream()
                .map(t -> translationRepository.save(
                        CategoryTranslation.builder()
                                .category(category)
                                .languageCode(t.getLanguageCode())
                                .name(t.getName())
                                .build()
                ))
                .toList();
        

        category.setTranslations(translations);

        List<CategoryTranslationResponse> translationResponses = translations.stream().map(t ->
                CategoryTranslationResponse.builder()
                        .languageCode(t.getLanguageCode())
                        .name(t.getName())
                        .build()
        ).toList();

        CategoryCreateResponse createdCategory = buildCategoryCreateResponse(category, translationResponses);

        PaginationMetaData metaData = buildSingleItemMetaData();

// Create audit trail for category creation
try {
    Restaurant restaurant = null;
    if (creator.getRestaurantId() != null) {
        restaurant = restaurantRepository.findById(creator.getRestaurantId()).orElse(null);
    }
    List<CategoryTranslation> categoryTranslations = translationRepository.findByCategoryId(category.getId());
    String categoryName = categoryTranslations.isEmpty() ? DEFAULT_NO_TRANSLATIONS : categoryTranslations.get(0).getName();
    auditTrailService.createAuditTrail(
            creator,
            ActionType.CATEGORY_CREATE,
            restaurant,
            null, // status - will default to NA for non-request actions
            null, // ipAddress - not available in this context
            null, // userAgent - not available in this context
            category.getId(),
            ENTITY_TYPE_CATEGORY,
            (isTopLevelCategory ? LABEL_CATEGORY : LABEL_SUBCATEGORY) + " created: " + categoryName
    );
} catch (Exception e) {
    // Don't break category creation flow if audit trail fails
}

if (isTopLevelCategory) {
    CategoryDataResponse responseData = CategoryDataResponse.builder()
            .category(createdCategory)
            .count(1L)
            .total(1L)
            .metaData(metaData)
            .build();

    return ResponseDto.<CategoryDataResponse>builder()
            .message(messageUtil.getMessage("category.create.success", userLocale))
            .data(responseData)
            .build();
} else {
    SubcategoryDataResponse responseData = SubcategoryDataResponse.builder()
            .subcategory(createdCategory)
            .count(1L)
            .total(1L)
            .metaData(metaData)
            .build();

        return ResponseDto.<SubcategoryDataResponse>builder()
                .message(messageUtil.getMessage("subcategory.create.success", userLocale))
                .data(responseData)
                .build();
    }

    }

    /**
     * Build a simple single-item CategoryCreateResponse with its translations.
     */
    private CategoryCreateResponse buildCategoryCreateResponse(
            Category category,
            List<CategoryTranslationResponse> translationResponses) {
        return CategoryCreateResponse.builder()
                .id(category.getId())
                .status(category.getStatus())
                .displayOrder(category.getDisplayOrder())
                .translations(translationResponses)
                .build();
    }

    /**
     * Build default pagination metadata for single-item create/update responses.
     */
    private PaginationMetaData buildSingleItemMetaData() {
        return PaginationMetaData.builder()
                .page(0)
                .size(10)
                .totalPages(1)
                .totalRecords(1L)
                .build();
    }

    /**
     * Updates an existing category or subcategory.
     * Validates menu structure, parent category (if provided), display order uniqueness, and translation uniqueness.
     * Prevents update if the menu structure has active published menus.
     * Replaces all existing translations with the new ones provided in the request.
     *
     * @param categoryId UUID of the category to update
     * @param request Category update request containing menu structure ID, parent category ID (optional), translations, status, and display order
     * @param updaterId UUID of the user updating the category
     * @param locale Locale for message localization
     * @return ResponseDto containing the updated category or subcategory data
     */
    @Transactional
    public ResponseDto<?>  updateCategory(UUID categoryId, CategoryRequest request, String updaterId, String locale) {
        Locale userLocale = Locale.forLanguageTag(locale);
    
        User updater = userRepository.findById(UUID.fromString(updaterId))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        messageUtil.getMessage("category.update.error.updater.notfound", userLocale)));
    
        Category category = categoryRepository.findByIdAndIsDeletedFalse(categoryId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        messageUtil.getMessage(MSG_CATEGORY_NOT_FOUND, userLocale)));
    
        MenuStructure menuStructure = menuStructureRepository.findByIdAndIsDeletedFalse(request.getMenuStructureId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        messageUtil.getMessage(MSG_CATEGORY_NOT_FOUND, userLocale)));
    
        // Validate that menu structure doesn't have active published menus
        if (menuRepository.existsByMenuStructureIdAndStatusAndIsDeletedFalse(
                menuStructure.getId(), MenuStatus.PUBLISHED)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    messageUtil.getMessage("category.update.error.assigned.to.active.published.menu", userLocale));
        }
    
        category.setMenuStructure(menuStructure);
    
        Category parentCategory = null;
        if (request.getParentCategoryId() != null) {
            parentCategory = categoryRepository.findByIdAndIsDeletedFalse(request.getParentCategoryId())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                            messageUtil.getMessage(MSG_CATEGORY_NOT_FOUND, userLocale)));
        }
        category.setParentCategory(parentCategory);
    
        // Consider only non-empty translations for duplicate language validation
        List<CategoryTranslationRequest> validTranslationsForUpdate = request.getTranslations().stream()
                .filter(t -> t.getLanguageCode() != null && t.getName() != null && !t.getName().trim().isEmpty())
                .toList();

        long uniqueLangCount = validTranslationsForUpdate.stream()
                .map(t -> t.getLanguageCode())
                .distinct()
                .count();
    
        if (uniqueLangCount != validTranslationsForUpdate.size()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    messageUtil.getMessage("category.update.error.duplicate.language", userLocale));
        }
    
        boolean isTopLevelCategory = (parentCategory == null);
        UUID parentId = (parentCategory != null) ? parentCategory.getId() : null;
    
        for (var t : validTranslationsForUpdate) {
            boolean exists;
            String duplicateKey;
    
            if (isTopLevelCategory) {
                exists = translationRepository.existsRootCategoryTranslationForUpdate(
                        t.getName(), t.getLanguageCode(), menuStructure.getId(), categoryId);
                duplicateKey = "category.update.error.duplicate.translation";
            } else {
                exists = translationRepository.existsSubCategoryTranslationForUpdate(
                        t.getName(), t.getLanguageCode(), parentId, categoryId);
                duplicateKey = "subcategory.update.error.duplicate.translation";
            }
    
            if (exists) {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        messageUtil.getMessage(duplicateKey, userLocale));
            }
        }
    
        translationRepository.deleteAllByCategory_Id(categoryId);
        translationRepository.flush();
        
        List<CategoryTranslation> newTranslations = validTranslationsForUpdate.stream()
                .map(t -> translationRepository.save(CategoryTranslation.builder()
                        .category(category)
                        .languageCode(t.getLanguageCode())
                        .name(t.getName())
                        .build()))
                .collect(Collectors.toList());
    
        category.getTranslations().clear();
        category.getTranslations().addAll(newTranslations);
        category.setStatus(request.getStatus());
        
        // Update isCombo if provided
        if (request.getIsCombo() != null) {
            category.setIsCombo(request.getIsCombo());
        }

        // Enforce unique displayOrder on update if changed
        if (request.getDisplayOrder() != null && !request.getDisplayOrder().equals(category.getDisplayOrder())) {
            // Validate display order is not negative
            if (request.getDisplayOrder() < 0) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        messageUtil.getMessage(isTopLevelCategory ?
                                "category.update.error.displayOrder.negative" :
                                "subcategory.update.error.displayOrder.negative", userLocale));
            }
            
            boolean conflict;
            if (isTopLevelCategory) {
                conflict = categoryRepository.existsRootDisplayOrderConflictExcludingId(
                        menuStructure.getId(), request.getDisplayOrder(), categoryId);
            } else {
                conflict = categoryRepository.existsSubDisplayOrderConflictExcludingId(
                        menuStructure.getId(), parentId, request.getDisplayOrder(), categoryId);
            }
            if (conflict) {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        messageUtil.getMessage(isTopLevelCategory ?
                                "category.update.error.displayOrder.duplicate" :
                                "subcategory.update.error.displayOrder.duplicate", userLocale));
            }
            category.setDisplayOrder(request.getDisplayOrder());
        }
        category.setUpdatedAt(OffsetDateTime.now(ZoneOffset.UTC));
        category.setUpdatedBy(updater);
    
        categoryRepository.save(category);
        List<CategoryTranslationResponse> translationResponses = newTranslations.stream().map(t ->
        CategoryTranslationResponse.builder()
                .languageCode(t.getLanguageCode())
                .name(t.getName())
                .build()
).toList();

CategoryCreateResponse updatedCategory = buildCategoryCreateResponse(category, translationResponses);

PaginationMetaData metaData = buildSingleItemMetaData();

// Create audit trail for category update
try {
    Restaurant restaurant = null;
    if (updater.getRestaurantId() != null) {
        restaurant = restaurantRepository.findById(updater.getRestaurantId()).orElse(null);
    }
    List<CategoryTranslation> categoryTranslations = translationRepository.findByCategoryId(category.getId());
    String categoryName = categoryTranslations.isEmpty() ? DEFAULT_NO_TRANSLATIONS : categoryTranslations.get(0).getName();
    auditTrailService.createAuditTrail(
            updater,
            ActionType.CATEGORY_UPDATE,
            restaurant,
            null, // status - will default to NA for non-request actions
            null, // ipAddress - not available in this context
            null, // userAgent - not available in this context
            category.getId(),
            ENTITY_TYPE_CATEGORY,
            (isTopLevelCategory ? LABEL_CATEGORY : LABEL_SUBCATEGORY) + " updated: " + categoryName
    );
} catch (Exception e) {
    // Don't break category update flow if audit trail fails
}

if (isTopLevelCategory) {
    CategoryDataResponse responseData = CategoryDataResponse.builder()
            .category(updatedCategory)
            .count(1L)
            .total(1L)
            .metaData(metaData)
            .build();

    return ResponseDto.<CategoryDataResponse>builder()
            .message(messageUtil.getMessage("category.update.success", userLocale))
            .data(responseData)
            .build();
} else {
    SubcategoryDataResponse responseData = SubcategoryDataResponse.builder()
            .subcategory(updatedCategory)
            .count(1L)
            .total(1L)
            .metaData(metaData)
            .build();

    return ResponseDto.<SubcategoryDataResponse>builder()
            .message(messageUtil.getMessage("subcategory.update.success", userLocale))
            .data(responseData)
            .build();
}
}
   
    /**
     * Retrieves a single category or subcategory by its ID.
     * Includes translations, parent category information, and user details (createdBy, updatedBy).
     * Resolves category name based on locale with fallback logic.
     *
     * @param categoryId UUID of the category to retrieve
     * @param locale Locale for message localization and name resolution
     * @return ResponseDto containing the category or subcategory data
     */
    @Transactional
    public ResponseDto<?>  getCategoryById(UUID categoryId, String locale) {
    Locale userLocale = Locale.forLanguageTag(locale);

    Category category = categoryRepository.findByIdAndIsDeletedFalse(categoryId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                    messageUtil.getMessage(MSG_CATEGORY_NOT_FOUND, userLocale)));

    List<CategoryTranslationResponse> translationResponses = category.getTranslations().stream()
            .map(t -> CategoryTranslationResponse.builder()
                    .languageCode(t.getLanguageCode())
                    .name(t.getName())
                    .build())
            .collect(Collectors.toList());

    // Extract name based on the locale header with fallback
    List<CategoryTranslation> translations = category.getTranslations();
    String name = resolveCategoryName(translations, userLocale);

    

    // Get parent category name if it exists with fallback
    String parentCategoryName = null;
    if (category.getParentCategory() != null) {
        List<CategoryTranslation> parentTranslations = category.getParentCategory().getTranslations();
        if (!parentTranslations.isEmpty()) {
            // Try exact match first
            CategoryTranslation exactMatch = parentTranslations.stream()
                    .filter(t -> t.getLanguageCode() != null && t.getLanguageCode().equals(userLocale.getLanguage()))
                    .findFirst()
                    .orElse(null);
            
            if (exactMatch != null) {
                parentCategoryName = exactMatch.getName();
            } else {
                // Fallback using TranslationUtils
                java.util.Optional<CategoryTranslation> fallback =
                        TranslationUtils.pickPreferredOrFromList(
                                parentTranslations,
                                userLocale.getLanguage(),
                                localizationProperties.getLanguages(),
                                CategoryTranslation::getLanguageCode
                        );
                parentCategoryName = fallback.map(CategoryTranslation::getName)
                        .orElse("");
            }
        }
    }

    // Get user names for createdBy and updatedBy
    String createdBy = null;
    if (category.getCreatedBy() != null) {
        createdBy = category.getCreatedBy().getFirstName() + " " + category.getCreatedBy().getLastName();
    }
    
    String updatedBy = null;
    if (category.getUpdatedBy() != null) {
        updatedBy = category.getUpdatedBy().getFirstName() + " " + category.getUpdatedBy().getLastName();
    }

    CategoryResponse.CategoryData categoryData = CategoryResponse.CategoryData.builder()
            .id(category.getId())
            .menuStructureId(category.getMenuStructure().getId())
            .parentCategoryId(category.getParentCategory() != null ? category.getParentCategory().getId() : null)
            .parentCategoryName(parentCategoryName)
            .status(category.getStatus())
            .name(name)
            .createdBy(createdBy)
            .createdAt(category.getCreatedAt() != null ? category.getCreatedAt().toLocalDateTime() : null)
            .updatedBy(updatedBy)
            .updatedAt(category.getUpdatedAt() != null ? category.getUpdatedAt().toLocalDateTime() : null)
            .displayOrder(category.getDisplayOrder())
            .isCombo(category.getIsCombo())
            .translations(translationResponses)
            .build();

    // Determine if this is a subcategory
    boolean isSubcategory = category.getParentCategory() != null;

    if (isSubcategory) {
        SubcategoryResponse response = SubcategoryResponse.builder()
                .subcategory(categoryData)
                .build();

        return ResponseDto.<SubcategoryResponse>builder()
                .message(messageUtil.getMessage("subcategory.get.success", userLocale))
                .data(response)
                .build();
    } else {
        CategoryResponse response = CategoryResponse.builder()
                .category(categoryData)
                .build();

        return ResponseDto.<CategoryResponse>builder()
                .message(messageUtil.getMessage("category.get.success", userLocale))
                .data(response)
                .build();
    }
}



    /**
     * Retrieves a paginated and filterable list of categories or subcategories.
     * If categoryId is provided, returns subcategories of that category.
     * Otherwise, returns top-level categories for the menu structure.
     * Supports filtering by status, menu structure, parent category, and search term.
     *
     * @param page Page number (1-based)
     * @param size Page size
     * @param status Entity status filter (can be null)
     * @param menuStructureId UUID of the menu structure (required)
     * @param categoryId UUID of the parent category (optional, if provided returns subcategories)
     * @param search Search term to filter by category name in translations (optional)
     * @param sortBy Field to sort by
     * @param direction Sort direction (ASC or DESC)
     * @return ResponseDto containing a paginated list of categories or subcategories
     */
    @Override
    @Transactional
    public ResponseDto<CategoryWrapperResponse> getCategories(int page, int size, EntityStatus status, UUID menuStructureId, UUID categoryId, String search, String sortBy, Sort.Direction direction) {
        Locale userLocale = LocaleContextHolder.getLocale();

        // Convert 1-based page to 0-based for Spring Data JPA
        int zeroBasedPage = page > 0 ? page - 1 : 0;
        
        // Create pageable with sorting
        Pageable pageable = PageRequest.of(zeroBasedPage, size, Sort.by(direction, sortBy));
        
        // Get filtered categories based on menu structure and parent category with search
        Page<Category> categoryPage;
        if (search != null && !search.trim().isEmpty()) {
            categoryPage = categoryRepository.findByStatusAndMenuStructureAndParentCategoryWithSearch(status, menuStructureId, categoryId, search.trim(), pageable);
        } else {
            categoryPage = categoryRepository.findByStatusAndMenuStructureAndParentCategory(status, menuStructureId, categoryId, pageable);
        }
        
        // Convert to response DTOs
        List<CategoryListData> categoryResponses = categoryPage.getContent().stream().map(category -> {
            // Get all translations for this category and resolve name with locale + fallback
            List<CategoryTranslation> translations = category.getTranslations();
            String name = resolveCategoryName(translations, userLocale);

            // Get user names for createdBy and updatedBy
            String createdBy = null;
            if (category.getCreatedBy() != null) {
                createdBy = category.getCreatedBy().getFirstName() + " " + category.getCreatedBy().getLastName();
            }
            
            String updatedBy = null;
            if (category.getUpdatedBy() != null) {
                updatedBy = category.getUpdatedBy().getFirstName() + " " + category.getUpdatedBy().getLastName();
            }

            long subcategoryCount = categoryRepository.countSubcategoriesByCategoryId(category.getId());

            return CategoryListData.builder()
                    .id(category.getId())
                    .status(category.getStatus())
                    .name(name)
                    .createdBy(createdBy)
                    .createdAt(category.getCreatedAt() != null ? category.getCreatedAt().toLocalDateTime() : null)
                    .updatedBy(updatedBy)
                    .updatedAt(category.getUpdatedAt() != null ? category.getUpdatedAt().toLocalDateTime() : null)
                    .subcategoryCount(subcategoryCount) 
                    .displayOrder(category.getDisplayOrder())
                    .isCombo(category.getIsCombo())
                    .translations(translations.stream()
                            .map(t -> CategoryTranslationResponse.builder()
                                    .languageCode(t.getLanguageCode())
                                    .name(t.getName())
                                    .build())
                            .collect(Collectors.toList()))
                    .build();
        }).collect(Collectors.toList());

        // Build pagination metadata
        PaginationMetaData metaData = PaginationMetaData.builder()
                .page(page)
                .size(size)
                .totalPages(categoryPage.getTotalPages())
                .totalRecords(categoryPage.getTotalElements())
                .build();



        // Build wrapper response with appropriate fields based on parameters
        CategoryWrapperResponse wrapperResponse;
        String messageKey;
        
        if (menuStructureId != null && categoryId != null) {
            // Return subcategories in the wrapper
            wrapperResponse = CategoryWrapperResponse.builder()
                    .subcategories(categoryResponses)
                    .categories(null)
                    .count((long) categoryResponses.size())
                    .total(categoryPage.getTotalElements())
                    .metaData(metaData)
                    .build();
            messageKey = "subcategory.list.success";
        } else {
            // Return categories in the wrapper
            wrapperResponse = buildCategoryWrapperResponse(categoryResponses, categoryPage.getTotalElements(), metaData);
            messageKey = "category.list.success";
        }
        
        return ResponseDto.<CategoryWrapperResponse>builder()
                .message(messageUtil.getMessage(messageKey, userLocale))
                .data(wrapperResponse)
                .build();
    }

    /**
     * Soft-deletes a category or subcategory.
     * Validates that the category is not used as a parent category and is not assigned to active published menus.
     * Creates an audit trail for the deletion.
     *
     * @param categoryId UUID of the category to delete
     * @param deleterId UUID of the user performing the deletion
     * @return ResponseDto with success message
     */
    @Override
    @Transactional
    public ResponseDto<Void> deleteCategory(UUID categoryId, String deleterId) {
        // Get user locale
        Locale userLocale = LocaleContextHolder.getLocale();
    
        // 1. First validate user exists
        User deleter = userRepository.findById(UUID.fromString(deleterId))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        messageUtil.getMessage("user.not.found", userLocale)));
    
        // 2. Check if category exists (without isDeleted check)
        Category category = categoryRepository.findById(categoryId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        messageUtil.getMessage(MSG_CATEGORY_NOT_FOUND, userLocale)));
    
        // 3. Check if category is used as parent
        if (categoryRepository.existsByParentCategoryIdAndIsDeletedFalse(categoryId)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        messageUtil.getMessage("category.delete.error.has.subcategories", userLocale));
            }
    
        // 4. Check if category is assigned to a menu structure with active published menus
        // Active means status = PUBLISHED AND isDeleted = false
        if (menuRepository.existsByMenuStructureIdAndStatusAndIsDeletedFalse(
                category.getMenuStructure().getId(), MenuStatus.PUBLISHED)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    messageUtil.getMessage("category.delete.error.assigned.to.active.published.menu", userLocale));
        }
    
        // 6. Check if already deleted
        if (category.getIsDeleted()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    messageUtil.getMessage("category.delete.error.already.deleted", userLocale));
        }
    
        // 7. Perform soft delete and update
        category.setIsDeleted(true);
        category.setUpdatedAt(OffsetDateTime.now(ZoneOffset.UTC));
        category.setUpdatedBy(deleter);
        
        // Save the updated category
        categoryRepository.save(category);
    
        // Create audit trail for category deletion
        try {
            Restaurant restaurant = null;
            if (deleter.getRestaurantId() != null) {
                restaurant = restaurantRepository.findById(deleter.getRestaurantId()).orElse(null);
            }
            List<CategoryTranslation> categoryTranslations = translationRepository.findByCategoryId(category.getId());
            String categoryName = categoryTranslations.isEmpty() ? DEFAULT_NO_TRANSLATIONS : categoryTranslations.get(0).getName();
            boolean isTopLevel = category.getParentCategory() == null;
            auditTrailService.createAuditTrail(
                    deleter,
                    ActionType.CATEGORY_DELETE,
                    restaurant,
                    null, // status - will default to NA for non-request actions
                    null, // ipAddress - not available in this context
                    null, // userAgent - not available in this context
                    category.getId(),
                    ENTITY_TYPE_CATEGORY,
                    (isTopLevel ? LABEL_CATEGORY : LABEL_SUBCATEGORY) + " deleted: " + categoryName
            );
        } catch (Exception e) {
            // Don't break category deletion flow if audit trail fails
        }
    
        return ResponseDto.<Void>builder()
                .message(messageUtil.getMessage("category.delete.success", userLocale))
                .build();
    }

    /**
     * Retrieves all active combo categories assigned to a specific menu.
     * Returns categories that are marked as combo categories (isCombo = true) and are active in the menu.
     * Includes translations and resolves category names based on locale with fallback logic.
     *
     * @param menuId UUID of the menu
     * @param locale Locale for message localization and name resolution
     * @return ResponseDto containing a list of active combo categories for the menu
     */
    @Override
    @Transactional(readOnly = true)
    public ResponseDto<CategoryWrapperResponse> getActiveComboCategoriesByMenuId(UUID menuId, String locale) {
        Locale userLocale = Locale.forLanguageTag(locale);

        // Validate menu exists
        menuRepository.findById(menuId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        messageUtil.getMessage("menu.not.found", userLocale)));

        // Get active combo categories from menu_category_mapping
        List<com.gulfnet.shared_library.entity.MenuCategoryMapping> mappings = 
                menuCategoryMappingRepository.findByMenuIdAndStatusAndCategoryIsComboTrue(menuId, EntityStatus.ACTIVE);

        // Convert to CategoryListData
        List<CategoryListData> categoryResponses = mappings.stream().map(mapping -> {
            Category category = mapping.getCategory();
            List<CategoryTranslation> translations = category.getTranslations();

            // Apply fallback language logic for the name
            String name = resolveCategoryName(translations, userLocale);

            // Get user names for createdBy and updatedBy
            String createdBy = null;
            if (category.getCreatedBy() != null) {
                createdBy = category.getCreatedBy().getFirstName() + " " + category.getCreatedBy().getLastName();
            }

            String updatedBy = null;
            if (category.getUpdatedBy() != null) {
                updatedBy = category.getUpdatedBy().getFirstName() + " " + category.getUpdatedBy().getLastName();
            }

            long subcategoryCount = categoryRepository.countSubcategoriesByCategoryId(category.getId());

            return CategoryListData.builder()
                    .id(category.getId())
                    .status(category.getStatus())
                    .name(name)
                    .createdBy(createdBy)
                    .createdAt(category.getCreatedAt() != null ? category.getCreatedAt().toLocalDateTime() : null)
                    .updatedBy(updatedBy)
                    .updatedAt(category.getUpdatedAt() != null ? category.getUpdatedAt().toLocalDateTime() : null)
                    .subcategoryCount(subcategoryCount)
                    .displayOrder(category.getDisplayOrder())
                    .isCombo(category.getIsCombo())
                    .menuCategoryMappingId(mapping.getId())
                    .translations(translations.stream()
                            .map(t -> CategoryTranslationResponse.builder()
                                    .languageCode(t.getLanguageCode())
                                    .name(t.getName())
                                    .build())
                            .collect(Collectors.toList()))
                    .build();
        }).collect(Collectors.toList());

        // Build wrapper response
        CategoryWrapperResponse wrapperResponse = buildCategoryWrapperResponse(categoryResponses, null, null);

        return ResponseDto.<CategoryWrapperResponse>builder()
                .message(messageUtil.getMessage("category.list.success", userLocale))
                .data(wrapperResponse)
                .build();
    }

    /**
     * Helper method to build CategoryWrapperResponse with categories
     * @param categoryResponses The list of category responses
     * @param total The total count (can be null to use categoryResponses.size())
     * @param metaData The pagination metadata (can be null)
     * @return CategoryWrapperResponse
     */
    private CategoryWrapperResponse buildCategoryWrapperResponse(
            List<CategoryListData> categoryResponses, Long total, PaginationMetaData metaData) {
        long totalCount = total != null ? total : categoryResponses.size();
        return CategoryWrapperResponse.builder()
                .categories(categoryResponses)
                .subcategories(null)
                .count((long) categoryResponses.size())
                .total(totalCount)
                .metaData(metaData)
                .build();
    }

    /**
     * Resolve a category name given translations and the user's locale, with deterministic fallback.
     */
    private String resolveCategoryName(List<CategoryTranslation> translations, Locale userLocale) {
        if (translations == null || translations.isEmpty()) {
            return "";
        }

        // Try exact language match first
        CategoryTranslation exactMatch = translations.stream()
                .filter(t -> t.getLanguageCode() != null && t.getLanguageCode().equals(userLocale.getLanguage()))
                .findFirst()
                .orElse(null);

        if (exactMatch != null && exactMatch.getName() != null) {
            return exactMatch.getName();
        }

        // Fallback using TranslationUtils and configured languages
        return TranslationUtils.pickPreferredOrFromList(
                        translations,
                        userLocale.getLanguage(),
                        localizationProperties.getLanguages(),
                        CategoryTranslation::getLanguageCode
                )
                .map(CategoryTranslation::getName)
                .orElse("");
    }

}
