package com.gulfnet.restaurantmanagement.service.impl;

import com.gulfnet.restaurantmanagement.service.MenuStructureService;
import com.gulfnet.restaurantmanagement.service.ComboService;
import com.gulfnet.restaurantmanagement.util.MessageUtil;
import com.gulfnet.restaurantmanagement.config.LocalizationProperties;
import com.gulfnet.shared_library.util.TranslationUtils;
import com.gulfnet.shared_library.entity.MenuStructure;
import com.gulfnet.shared_library.entity.MenuStructureTranslation;
import com.gulfnet.shared_library.model.request.MenuStructureRequest;
import com.gulfnet.shared_library.config.AWSService;
import org.springframework.transaction.annotation.Transactional;
import com.gulfnet.shared_library.entity.User;
import com.gulfnet.shared_library.entity.*;
import com.gulfnet.shared_library.entity.Category; // Add this import
import com.gulfnet.shared_library.entity.CategoryTranslation; // Add this import
import com.gulfnet.shared_library.entity.Item; // Add this import
import com.gulfnet.shared_library.entity.CategoryItemMapping; // Add this import
import com.gulfnet.shared_library.model.response.dto.MenuCategoryStructureResponse;
import com.gulfnet.shared_library.model.response.dto.ComboDetailsResponse;
import com.gulfnet.shared_library.model.response.dto.ComboDto;
import com.gulfnet.shared_library.entity.MenuCategoryComboMapping;
import com.gulfnet.shared_library.repository.MenuCategoryComboMappingRepository;
import com.gulfnet.shared_library.model.response.dto.ResponseDto;


import com.gulfnet.shared_library.model.response.dto.*;
import com.gulfnet.shared_library.repository.*;
import com.gulfnet.shared_library.enums.EntityStatus;
import com.gulfnet.shared_library.enums.MenuStatus;
import com.gulfnet.shared_library.enums.ItemOrderType;
import jakarta.persistence.criteria.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import com.gulfnet.shared_library.util.LocaleSortUtil;
import lombok.extern.slf4j.Slf4j;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.*;
import java.util.stream.Collectors;
import java.util.UUID;

@Slf4j
@Service
public class MenuStructureServiceImpl implements MenuStructureService {
    
    // Message key constants
    private static final String MSG_USER_NOT_FOUND = "user.not.found";
    private static final String MSG_MENU_ERROR_NOT_FOUND = "menu.not.found";
    private static final String MSG_MENU_ERROR_ALREADY_DELETED = "menu.error.already.deleted";
    private static final String MSG_MENU_ERROR_INVALID_LANGUAGE = "error.invalid.language";

    @Autowired
    private MenuStructureRepository menuStructureRepository;

    @Autowired
    private MenuStructureTranslationRepository translationRepository;

    @Autowired
    private AWSService awsService;

    @Autowired
    private MessageUtil messageUtil;

    @Autowired
    private LocalizationProperties localizationProperties;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private CategoryRepository categoryRepository; // Autowired
    
    @Autowired
    private  MenuCategoryMappingRepository menuCategoryMappingRepository; // Added this

    @Autowired
    private MenuCategoryComboMappingRepository menuCategoryComboMappingRepository;


    @Autowired
    private CategoryItemMappingRepository categoryItemMappingRepository; // Autowired

    @Autowired
    private  MenuRepository menuRepository;

    @Autowired
    private RestaurantMenuMappingRepository restaurantMenuMappingRepository;

    @Autowired
    private ComboService comboService;

/**
 * Creates a new menu structure with translations.
 * <p>
 * Validations include:
 * - requesting user exists
 * - at least one translation has a non-empty name
 * - no duplicate language codes among non-empty translation names
 * - translation name uniqueness per language across menu structures (when a name is provided)
 *
 * @param request menu-structure create payload (required)
 * @param userId actor user id (UUID string) (required)
 * @param locale locale tag for messages/validation (required)
 * @return wrapper containing the created menu structure response
 * @throws ResponseStatusException on validation failure or missing user
 */
@Override
@Transactional
public ResponseDto<MenuStructureDto<MenuStructureResponse>> createMenuStructure(MenuStructureRequest request, String userId, String locale) {
    Locale userLocale = Locale.forLanguageTag(locale);

    User user = userRepository.findById(UUID.fromString(userId))
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                    messageUtil.getMessage(MSG_USER_NOT_FOUND, userLocale, userId)));

    List<MenuStructureTranslationDto> translations = request.getTranslations();
    if (translations != null && !translations.isEmpty()) {
        // Validate that at least one translation has a non-empty name
        boolean hasValidName = translations.stream()
            .anyMatch(t -> t.getName() != null && !t.getName().trim().isEmpty());
        
        if (!hasValidName) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    messageUtil.getMessage("item.update.error.no.valid.name", userLocale));
        }
        
        // Check for duplicate language codes in the request
        validateMenuStructureTranslationsLanguageCodes(translations, userLocale);
        
        for (MenuStructureTranslationDto entry : translations) {
            String name = entry.getName();
            String languageCode = entry.getLanguageCode();
            
            // Only validate non-empty names
            if (name != null && !name.trim().isEmpty() && languageCode != null) {
                boolean exists = translationRepository.existsByNameAndLanguageCode(name.trim(), languageCode);
                if (exists) {
                    throw new ResponseStatusException(HttpStatus.CONFLICT,
                            messageUtil.getMessage("menu.error.name.exists", userLocale, name.trim(), languageCode));
                }
            }
        }
    } else {
        // No translations provided at all
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                messageUtil.getMessage("combo.translations.required", userLocale));
    }

    MenuStructure menuStructure = new MenuStructure();
    menuStructure.setStatus(request.getStatus());
    menuStructure.setIsDeleted(Boolean.TRUE.equals(request.getIsDeleted()));
    menuStructure.setCreatedAt(OffsetDateTime.now(ZoneOffset.UTC));
    menuStructure.setCreatedBy(user);
    menuStructure = menuStructureRepository.save(menuStructure);

    if (translations != null && !translations.isEmpty()) {
        for (MenuStructureTranslationDto entry : translations) {
            String name = entry.getName();
            if (name != null && !name.trim().isEmpty()) {
                MenuStructureTranslation translation = new MenuStructureTranslation();
                translation.setName(name.trim());
                translation.setMenuStructure(menuStructure);
                translation.setLanguageCode(entry.getLanguageCode());
                translationRepository.save(translation);
            }
        }
    }

    List<MenuStructureTranslationDto> translationDTOs = buildMenuStructureTranslationDtos(menuStructure.getId());

    MenuStructureResponse response = MenuStructureResponse.builder()
            .id(menuStructure.getId())
            .status(menuStructure.getStatus())
            .isDeleted(menuStructure.getIsDeleted())
            .createdAt(menuStructure.getCreatedAt() != null ? menuStructure.getCreatedAt().toLocalDateTime() : null)
            .createdBy(menuStructure.getCreatedBy().getFirstName() + " " + menuStructure.getCreatedBy().getLastName())
            .translations(translationDTOs)
            .build();

    return buildMenuStructureResponseDto(response, "menu.create.success", userLocale);
    }

    /**
     * Restores (un-deletes) a list of soft-deleted menu structures.
     * <p>
     * Only structures currently marked as deleted are restored; if none of the provided ids are deleted, the call fails.
     *
     * @param ids menu structure ids to restore (required)
     * @param userId actor user id (UUID string) (required)
     * @param locale locale tag for messages (required)
     * @return void response indicating success
     * @throws ResponseStatusException if user is missing, no structures are found, or none are deleted
     */
    @Override
    @Transactional
    public ResponseDto<Void> restoreMenuStructures(List<UUID> ids, String userId, String locale) {
        Locale userLocale = Locale.forLanguageTag(locale);

        User user = loadUserOrThrow(userId, userLocale);
        List<MenuStructure> deletedMenuStructures = loadDeletedMenuStructuresOrThrow(ids, userLocale);

        restoreDeletedMenuStructures(deletedMenuStructures, user);
        menuStructureRepository.saveAll(deletedMenuStructures);

        return ResponseDto.<Void>builder()
                .message(messageUtil.getMessage("menu.structure.restore.success", userLocale))
                .build();
    }

    private User loadUserOrThrow(String userId, Locale userLocale) {
        return userRepository.findById(UUID.fromString(userId))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        messageUtil.getMessage(MSG_USER_NOT_FOUND, userLocale)));
    }

    /**
     * Loads menu structures by id and returns only those marked deleted; otherwise throws not-found or bad-request.
     */
    private List<MenuStructure> loadDeletedMenuStructuresOrThrow(List<UUID> ids, Locale userLocale) {
        List<MenuStructure> menuStructures = menuStructureRepository.findAllById(ids);
        if (menuStructures.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                    messageUtil.getMessage(MSG_MENU_ERROR_NOT_FOUND, userLocale));
        }

        List<MenuStructure> deletedMenuStructures = menuStructures.stream()
                .filter(ms -> Boolean.TRUE.equals(ms.getIsDeleted()))
                .collect(Collectors.toList());

        if (deletedMenuStructures.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    messageUtil.getMessage("menu.restore.error.not.deleted", userLocale));
        }

        return deletedMenuStructures;
    }

    private void restoreDeletedMenuStructures(List<MenuStructure> deletedMenuStructures, User user) {
        for (MenuStructure menuStructure : deletedMenuStructures) {
            menuStructure.setIsDeleted(false);
            menuStructure.setUpdatedAt(OffsetDateTime.now(ZoneOffset.UTC));
            menuStructure.setUpdatedBy(user);
        }
    }



/**
 * Updates an existing menu structure and its translations.
 * <p>
 * Constraints:
 * - cannot update a deleted structure
 * - when changing status from ACTIVE to INACTIVE, the structure must not be used by any PUBLISHED menu
 * <p>
 * Translation handling:
 * - validates language code duplicates/validity for non-empty names
 * - removes translations not present in the request (or with empty names)
 *
 * @param id menu structure id (required)
 * @param request update payload (required)
 * @param userId actor user id (UUID string) (required)
 * @param locale locale tag for messages/validation (required)
 * @return wrapper containing the updated menu structure response
 * @throws ResponseStatusException on validation failure or missing user/structure
 */
@Override
@Transactional
public ResponseDto<MenuStructureDto<MenuStructureResponse>> updateMenuStructure(UUID id, MenuStructureRequest request, String userId, String locale) {
    Locale userLocale = Locale.forLanguageTag(locale);

    User user = loadUserOrThrowForUpdate(userId, userLocale);
    MenuStructure menuStructure = loadMenuStructureOrThrow(id, userLocale);

    validateMenuStructureStatusTransition(menuStructure, request, userLocale);

    List<MenuStructureTranslationDto> translations = validateUpdateTranslationsOrThrow(request.getTranslations(), menuStructure, userLocale);

    menuStructure.setStatus(request.getStatus());
    menuStructure.setUpdatedAt(OffsetDateTime.now(ZoneOffset.UTC));
    menuStructure.setUpdatedBy(user);
    menuStructure = menuStructureRepository.save(menuStructure);

    syncMenuStructureTranslations(menuStructure, translations);
    List<MenuStructureTranslationDto> translationDTOs = buildMenuStructureTranslationDtos(menuStructure.getId());

    MenuStructureResponse response = buildMenuStructureResponseForUpdate(menuStructure, translationDTOs);
    return buildMenuStructureResponseDto(response, "menu.update.success", userLocale);
}



/**
 * Fetches a menu structure by id.
 * <p>
 * Detail endpoint returns all translations for the structure and includes a count of associated (non-deleted) menus.
 *
 * @param id menu structure id (required)
 * @param locale locale tag for messages/validation (required)
 * @return wrapper containing the menu structure response
 * @throws ResponseStatusException if locale is invalid, structure is not found, or structure is deleted
 */
@Override
@Transactional(readOnly = true)
public ResponseDto<MenuStructureDto<MenuStructureResponse>> getMenuStructureById(UUID id, String locale) {
    Locale userLocale = Locale.forLanguageTag(locale);
    validateMenuStructureReadLocale(locale, userLocale);

    MenuStructure menuStructure = loadMenuStructureOrThrow(id, userLocale);

    List<MenuStructureTranslationDto> translationDTOs = buildMenuStructureTranslationDtos(menuStructure.getId());
    long menuCount = menuRepository.countByMenuStructureIdAndIsDeletedFalse(menuStructure.getId());

    MenuStructureResponse response = buildMenuStructureResponse(menuStructure, translationDTOs, menuCount);
    return buildMenuStructureResponseDto(response, "menu.view.success", userLocale);
}

/**
 * Ensures {@code locale} is one of the configured supported language tags.
 */
private void validateMenuStructureReadLocale(String locale, Locale userLocale) {
    if (!localizationProperties.getLanguages().contains(locale)) {
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                messageUtil.getMessage(MSG_MENU_ERROR_INVALID_LANGUAGE, userLocale));
    }
}

/**
 * Loads a non-deleted {@link MenuStructure} by id or throws a localized {@link ResponseStatusException}.
 */
private MenuStructure loadMenuStructureOrThrow(UUID id, Locale userLocale) {
    MenuStructure menuStructure = menuStructureRepository.findById(id)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                    messageUtil.getMessage(MSG_MENU_ERROR_NOT_FOUND, userLocale, id)));

    if (menuStructure.getIsDeleted()) {
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                messageUtil.getMessage(MSG_MENU_ERROR_ALREADY_DELETED, userLocale));
    }

    return menuStructure;
}

private List<MenuStructureTranslationDto> buildMenuStructureTranslationDtos(UUID menuStructureId) {
    List<MenuStructureTranslation> translations = translationRepository.findAllByMenuStructureId(menuStructureId);
    return translations.stream()
            .map(t -> MenuStructureTranslationDto.builder()
                    .languageCode(t.getLanguageCode())
                    .name(t.getName())
                    .build())
            .collect(Collectors.toList());
}

/**
 * Maps a {@link MenuStructure} entity and its translations to the API response DTO, including associated menu count.
 */
private MenuStructureResponse buildMenuStructureResponse(MenuStructure menuStructure,
        List<MenuStructureTranslationDto> translationDTOs, long menuCount) {
    return MenuStructureResponse.builder()
            .id(menuStructure.getId())
            .status(menuStructure.getStatus())
            .isDeleted(menuStructure.getIsDeleted())
            .createdAt(menuStructure.getCreatedAt() != null ? menuStructure.getCreatedAt().toLocalDateTime() : null)
            .createdBy(menuStructure.getCreatedBy() != null ?
                    menuStructure.getCreatedBy().getFirstName() + " " + menuStructure.getCreatedBy().getLastName() : null)
            .updatedAt(menuStructure.getUpdatedAt() != null ? menuStructure.getUpdatedAt().toLocalDateTime() : null)
            .updatedBy(menuStructure.getUpdatedBy() != null ?
                    menuStructure.getUpdatedBy().getFirstName() + " " + menuStructure.getUpdatedBy().getLastName() : null)
            .translations(translationDTOs)
            .menuCount(menuCount)
            .build();
}




    /**
     * Soft-deletes a menu structure.
     * <p>
     * Constraint: a structure cannot be deleted if it has any associated menus that are not deleted.
     *
     * @param id menu structure id (required)
     * @param userId actor user id (UUID string) (required)
     * @param locale locale tag for messages (required)
     * @return response with a human-readable confirmation message
     * @throws ResponseStatusException if user/structure is missing, already deleted, or has associated menus
     */
    @Override
    @Transactional
    public ResponseDto<String> deleteMenuStructure(UUID id, String userId, String locale) {
        Locale userLocale = Locale.forLanguageTag(locale);

        User user = userRepository.findById(UUID.fromString(userId))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        messageUtil.getMessage(MSG_USER_NOT_FOUND, userLocale, userId)));

        MenuStructure menuStructure = menuStructureRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        messageUtil.getMessage(MSG_MENU_ERROR_NOT_FOUND, userLocale, id)));

        if (Boolean.TRUE.equals(menuStructure.getIsDeleted())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    messageUtil.getMessage(MSG_MENU_ERROR_ALREADY_DELETED, userLocale));
        }

        // Check if menu structure has any non-deleted menus (published or draft)
        List<Menu> associatedMenus = menuRepository.findByMenuStructureIdOrderByCreatedAtAsc(id);
        boolean hasNonDeletedMenus = associatedMenus.stream()
            .anyMatch(menu -> !Boolean.TRUE.equals(menu.getIsDeleted()));

        if (hasNonDeletedMenus) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    messageUtil.getMessage("menu.structure.delete.error.has.associated.menus", userLocale));
        }

        menuStructure.setIsDeleted(true);
        menuStructure.setUpdatedAt(OffsetDateTime.now(ZoneOffset.UTC));
        menuStructure.setUpdatedBy(user);
        menuStructureRepository.save(menuStructure);

        return ResponseDto.<String>builder()
                .message(messageUtil.getMessage("menu.delete.success", userLocale))
                .data("Menu structure with ID " + id + " has been deleted")
                .build();
    }

   
/**
 * Lists menu structures with filtering, search, sorting, and pagination.
 * <p>
 * Uses locale-aware translation fallback when returning the structure name.
 *
 * @param page 1-based page number (must be > 0; normalized internally)
 * @param size page size (must be > 0; normalized internally)
 * @param status optional status filter
 * @param search optional search term applied to translations
 * @param sortBy sort field (e.g. name/createdAt; handled in-memory)
 * @param direction sort direction
 * @param locale locale tag for messages/validation (required)
 * @param isDeleted if true, returns deleted structures; otherwise returns non-deleted structures
 * @return paged list response of menu structures
 * @throws ResponseStatusException if locale is invalid
 */
@Override
public ResponseDto<MenuStructureListResponse> getMenuStructures(int page, int size, EntityStatus status, String search, String sortBy, Sort.Direction direction, String locale, Boolean isDeleted) {
    Locale userLocale = Locale.forLanguageTag(locale);

    if (!localizationProperties.getLanguages().contains(locale)) {
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                messageUtil.getMessage(MSG_MENU_ERROR_INVALID_LANGUAGE, userLocale));
    }

    // Validate & normalize pagination (like discount service)
    int pageNumber = (page > 0 ? page : 1) - 1;
    if (pageNumber < 0) pageNumber = 0;
    int pageSize = (size > 0) ? size : Integer.MAX_VALUE;
    if (pageSize < 1) pageSize = Integer.MAX_VALUE;

    // Fetch all data without pagination (like discount service)
    Specification<MenuStructure> spec = buildMenuStructureListSpecification(status, search, isDeleted);

    List<MenuStructure> allMenuStructures = menuStructureRepository.findAll(spec);
    
    Map<UUID, Long> menuCountMap = loadMenuCountMap(allMenuStructures);
    
    List<MenuStructureResponse> menuStructures = buildMenuStructureResponses(allMenuStructures, menuCountMap, search, locale);
    applyMenuStructureSorting(menuStructures, sortBy, direction, userLocale);

    // Apply pagination to sorted results (like discount service)
    int fromIndex = pageNumber * pageSize;
    int toIndex = Math.min(fromIndex + pageSize, menuStructures.size());
    List<MenuStructureResponse> paginatedStructures;
    if (fromIndex >= menuStructures.size()) {
        paginatedStructures = Collections.emptyList();
    } else {
        paginatedStructures = menuStructures.subList(fromIndex, toIndex);
    }

    PaginationMetaData metaData = PaginationMetaData.builder()
            .page(pageNumber + 1) // Convert back to 1-based
            .size(pageSize)
            .totalPages((int) Math.ceil((double) menuStructures.size() / pageSize))
            .totalRecords((long) menuStructures.size())
            .build();

    MenuStructureListResponse listResponse = MenuStructureListResponse.builder()
            .menuStructures(paginatedStructures)
            .count((long) paginatedStructures.size())
            .total((long) menuStructures.size())
            .metaData(metaData)
            .errors(null)
            .build();

    return ResponseDto.<MenuStructureListResponse>builder()
            .data(listResponse)
            .message(messageUtil.getMessage("menu.list.success", userLocale))
            .build();
}

/**
 * JPA {@link Specification} for menu structure list queries: status, soft-delete flag, and optional name search.
 */
private Specification<MenuStructure> buildMenuStructureListSpecification(EntityStatus status, String search,
        Boolean isDeleted) {
    return (root, query, criteriaBuilder) -> {
        List<Predicate> predicates = new ArrayList<>();

        query.distinct(true);

        Join<MenuStructure, MenuStructureTranslation> translationJoin = root.join("translations", JoinType.LEFT);

        if (status != null) {
            predicates.add(criteriaBuilder.equal(root.get("status"), status));
        }

        if (isDeleted != null && isDeleted) {
            predicates.add(criteriaBuilder.equal(root.get("isDeleted"), true));
        } else {
            predicates.add(criteriaBuilder.equal(root.get("isDeleted"), false));
        }

        if (search != null && !search.trim().isEmpty()) {
            String searchPattern = "%" + search.toLowerCase() + "%";
            predicates.add(criteriaBuilder.like(criteriaBuilder.lower(translationJoin.get("name")), searchPattern));
        }

        return criteriaBuilder.and(predicates.toArray(new Predicate[0]));
    };
}

private Map<UUID, Long> loadMenuCountMap(List<MenuStructure> allMenuStructures) {
    List<UUID> menuStructureIds = allMenuStructures.stream()
            .map(MenuStructure::getId)
            .collect(Collectors.toList());

    return menuStructureIds.stream()
            .collect(Collectors.toMap(
                    id -> id,
                    id -> menuRepository.countByMenuStructureIdAndIsDeletedFalse(id)));
}

/**
 * Builds list rows with a single preferred translation per structure, optional search filtering, and menu counts.
 */
private List<MenuStructureResponse> buildMenuStructureResponses(List<MenuStructure> allMenuStructures,
        Map<UUID, Long> menuCountMap, String search, String locale) {
    return allMenuStructures.stream()
            .filter(menuStructure -> {
                if (search == null || search.trim().isEmpty()) {
                    return true;
                }

                List<MenuStructureTranslation> translations = translationRepository
                        .findAllByMenuStructureId(menuStructure.getId());
                return translations.stream()
                        .anyMatch(trans -> trans.getName() != null
                                && trans.getName().toLowerCase().contains(search.toLowerCase()));
            })
            .map(menuStructure -> {
                String structureName = "";
                String selectedLanguageCode = locale;
                List<MenuStructureTranslation> translations = translationRepository
                        .findAllByMenuStructureId(menuStructure.getId());

                if (!translations.isEmpty()) {
                    MenuStructureTranslation exactMatch = translations.stream()
                            .filter(t -> t.getLanguageCode() != null && t.getLanguageCode().equals(locale))
                            .findFirst()
                            .orElse(null);

                    if (exactMatch != null) {
                        structureName = exactMatch.getName();
                        selectedLanguageCode = exactMatch.getLanguageCode();
                    } else {
                        java.util.Optional<MenuStructureTranslation> fallback =
                                TranslationUtils.pickPreferredOrFromList(
                                        translations,
                                        locale,
                                        localizationProperties.getLanguages(),
                                        MenuStructureTranslation::getLanguageCode);

                        if (fallback.isPresent()) {
                            MenuStructureTranslation fallbackTranslation = fallback.get();
                            structureName = fallbackTranslation.getName();
                            selectedLanguageCode = fallbackTranslation.getLanguageCode();
                        }
                    }
                }

                List<MenuStructureTranslationDto> translationDtos = List.of(
                        MenuStructureTranslationDto.builder()
                                .languageCode(selectedLanguageCode)
                                .name(structureName)
                                .build());

                return MenuStructureResponse.builder()
                        .id(menuStructure.getId())
                        .status(menuStructure.getStatus())
                        .isDeleted(menuStructure.getIsDeleted())
                        .createdAt(menuStructure.getCreatedAt() != null ? menuStructure.getCreatedAt().toLocalDateTime()
                                : null)
                        .updatedAt(menuStructure.getUpdatedAt() != null ? menuStructure.getUpdatedAt().toLocalDateTime()
                                : null)
                        .createdBy(menuStructure.getCreatedBy() != null ?
                                menuStructure.getCreatedBy().getFirstName() + " " + menuStructure.getCreatedBy().getLastName()
                                : null)
                        .updatedBy(menuStructure.getUpdatedBy() != null ?
                                menuStructure.getUpdatedBy().getFirstName() + " " + menuStructure.getUpdatedBy().getLastName()
                                : null)
                        .translations(translationDtos)
                        .menuCount(menuCountMap.getOrDefault(menuStructure.getId(), 0L))
                        .build();
            })
            .collect(Collectors.toList());
}

/**
 * Sorts list DTOs by name (locale-aware) or by created/updated/status/id columns when requested.
 */
private void applyMenuStructureSorting(List<MenuStructureResponse> menuStructures, String sortBy,
        Sort.Direction direction, Locale userLocale) {
    if (sortBy == null || sortBy.trim().isEmpty()) {
        return;
    }

    String sortField = sortBy.trim().toLowerCase();

    if ("name".equalsIgnoreCase(sortField)) {
        LocaleContextHolder.setLocale(userLocale);
        LocaleSortUtil.sortName(menuStructures, sortBy, direction);
        return;
    }

    Comparator<MenuStructureResponse> comp;
    if ("createdat".equalsIgnoreCase(sortField)) {
        comp = Comparator.comparing(MenuStructureResponse::getCreatedAt,
                Comparator.nullsLast(Comparator.naturalOrder()));
    } else if ("updatedat".equalsIgnoreCase(sortField)) {
        comp = Comparator.comparing(MenuStructureResponse::getUpdatedAt,
                Comparator.nullsLast(Comparator.naturalOrder()));
    } else if ("status".equalsIgnoreCase(sortField)) {
        comp = Comparator.comparing(MenuStructureResponse::getStatus,
                Comparator.nullsLast(Comparator.naturalOrder()));
    } else if ("id".equalsIgnoreCase(sortField)) {
        comp = Comparator.comparing(MenuStructureResponse::getId,
                Comparator.nullsLast(Comparator.naturalOrder()));
    } else {
        return;
    }

    if (direction == Sort.Direction.DESC) {
        comp = comp.reversed();
    }
    menuStructures.sort(comp);
}


    /**
     * Builds a category/menu-item structure for a menu structure, optionally using a specific menu's mappings.
     * <p>
     * If the given {@code menuStructureId} is assigned to {@code menuId}, menu mappings (category status, item mappings,
     * combos, etc.) are used. Otherwise a structure based purely on categories/translations is returned.
     *
     * @param menuId menu id (required)
     * @param menuStructureId menu structure id (required)
     * @param status optional status filter (ACTIVE will filter inactive mappings)
     * @param search optional search term applied to item names
     * @param hasCombo optional combo filter (behavior depends on mapping logic)
     * @param orderType order type filter (DINE_IN/TAKEAWAY/BOTH semantics)
     * @param itemOrderType item order type filter (takes precedence over {@code orderType} when provided)
     * @return category structure response
     * @throws ResponseStatusException if menu/menuStructure is not found or invalid/inactive
     */
    @Override
    @Transactional(readOnly = true)
    public ResponseDto<MenuCategoryStructureResponse> getMenuStructure(UUID menuId, UUID menuStructureId, EntityStatus status, String search, Boolean hasCombo, String orderType, String itemOrderType) {
        Locale userLocale = LocaleContextHolder.getLocale();
        
        // First, validate and get the menu structure
        MenuStructure menuStructure = menuStructureRepository.findByIdAndIsDeletedFalse(menuStructureId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        messageUtil.getMessage("category.create.error.menuStructure.notfound", userLocale)));

        // Check if menu structure is inactive
        if (EntityStatus.INACTIVE.equals(menuStructure.getStatus())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    messageUtil.getMessage("menu.structure.inactive", userLocale));
        }

        // Get the menu
        Menu menu = menuRepository.findById(menuId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        messageUtil.getMessage(MSG_MENU_ERROR_NOT_FOUND, userLocale)));

        // Get root categories (categories without parent), sorted by displayOrder ASC
        List<Category> rootCategories = categoryRepository.findByMenuStructureIdAndIsDeletedFalseOrderByDisplayOrderAsc(menuStructureId)
                .stream()
                .filter(category -> category.getParentCategory() == null)
                .collect(Collectors.toList());

        List<MenuCategoryStructureResponse.CategoryStructureDto> categoryStructure;
        
        // Parse filters: itemOrderType and orderType both include BOTH
        // If both are provided, itemOrderType takes precedence
        ItemOrderType orderTypeFilter = null;
        boolean isExactMatchFilter = false; // Both filters now include BOTH
        
        if (itemOrderType != null && !itemOrderType.trim().isEmpty()) {
            // itemOrderType filter: includes BOTH (same as orderType)
            isExactMatchFilter = false;
            try {
                orderTypeFilter = ItemOrderType.valueOf(itemOrderType.toUpperCase());
            } catch (IllegalArgumentException e) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        messageUtil.getMessage("item.error.invalid.itemOrderType", userLocale));
            }
        } else if (orderType != null && !orderType.trim().isEmpty()) {
            // orderType filter: includes BOTH
            isExactMatchFilter = false;
            try {
                orderTypeFilter = ItemOrderType.valueOf(orderType.toUpperCase());
            } catch (IllegalArgumentException e) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        messageUtil.getMessage("item.error.invalid.orderType", userLocale));
            }
        }

        // Check if this menu structure is assigned to the menu
        if (menu.getMenuStructure() != null && menuStructureId.equals(menu.getMenuStructure().getId())) {
            categoryStructure = buildCategoryStructureWithMenu(
                    rootCategories,
                    menu,
                    new CategoryStructureBuildContext(userLocale, status, search, menuId, orderTypeFilter, isExactMatchFilter));
        } else {
            categoryStructure = buildCategoryStructureWithoutMenu(rootCategories, userLocale);
        }

        // Create response object using builder
        MenuCategoryStructureResponse response = MenuCategoryStructureResponse.builder()
                .categories(categoryStructure)
                .build();

        return ResponseDto.<MenuCategoryStructureResponse>builder()
        .data(response)
        .message(messageUtil.getMessage("menu.structure.categories.fetch.success", userLocale))
        .build();
    }

    /**
     * Returns all menu structures (via restaurant-menu mappings) for a restaurant, with translation fallback.
     *
     * @param restaurantId restaurant id (required)
     * @param status optional status filter
     * @param search optional search term
     * @param locale locale tag for messages/translation selection (required)
     * @return list of menu detail structures available for the restaurant (may be empty)
     * @throws ResponseStatusException if locale is invalid
     */
    @Override
    @Transactional(readOnly = true)
    public ResponseDto<List<MenuDetailStructureDto>> getAllMenuStructuresByRestaurantId(
            UUID restaurantId, EntityStatus status, String search, String locale) {

        Locale userLocale = Locale.forLanguageTag(locale);

        // Check locale validity
        if (!localizationProperties.getLanguages().contains(locale)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    messageUtil.getMessage(MSG_MENU_ERROR_INVALID_LANGUAGE, userLocale));
        }

        // Use optimized existence check with early exit
        Integer existsCheck = restaurantMenuMappingRepository.existsById_RestaurantIdOptimized(restaurantId);
        
        // Return empty array with message if no menu exists
        if (existsCheck == null) {
            return ResponseDto.<List<MenuDetailStructureDto>>builder()
                    .data(new ArrayList<>())
                    .message(messageUtil.getMessage(MSG_MENU_ERROR_NOT_FOUND, userLocale))
                    .build();
        }

        // Only fetch the full mappings if we know they exist
        List<RestaurantMenuMapping> mappings = restaurantMenuMappingRepository.findById_RestaurantId(restaurantId);

        List<MenuDetailStructureDto> result = new ArrayList<>();

        for (RestaurantMenuMapping mapping : mappings) {
            MenuDetailStructureDto dto = buildMenuDetailStructureDto(mapping, status, search, locale, userLocale);
            if (dto != null) {
                result.add(dto);
            }
        }

        return ResponseDto.<List<MenuDetailStructureDto>>builder()
                .data(result)
                .message(messageUtil.getMessage("restaurant.menu.structures.fetch.success", userLocale))
                .build();
    }

    /**
     * Builds the restaurant menu + category structure payload for one {@link RestaurantMenuMapping}, or {@code null} when skipped.
     */
    private MenuDetailStructureDto buildMenuDetailStructureDto(RestaurantMenuMapping mapping, EntityStatus status,
            String search, String locale, Locale userLocale) {
        Menu menu = mapping.getMenu();
        MenuStructure menuStructure = menu != null ? menu.getMenuStructure() : null;

        if (menu == null || Boolean.TRUE.equals(menu.getIsDeleted())
                || menuStructure == null || Boolean.TRUE.equals(menuStructure.getIsDeleted())) {
            return null;
        }

        List<MenuTranslationDto> translationDtos = buildMenuTranslationDtos(menu.getTranslations(), locale);
        boolean isEditable = isMenuEditable(menu);

        MenuResponse menuResponse = MenuResponse.builder()
                .id(menu.getId())
                .menuMasterId(menu.getMenuMasterId())
                .version(menu.getVersion())
                .status(mapping.getStatus() != null ? mapping.getStatus().toString() : "UNSCHEDULED")
                .translations(translationDtos)
                .createdAt(menu.getCreatedAt() != null ? menu.getCreatedAt().toLocalDateTime() : null)
                .updatedAt(menu.getUpdatedAt() != null ? menu.getUpdatedAt().toLocalDateTime() : null)
                .createdBy(menu.getCreatedBy() != null ?
                        menu.getCreatedBy().getFirstName() + " " + menu.getCreatedBy().getLastName() : null)
                .updatedBy(menu.getUpdatedBy() != null ?
                        menu.getUpdatedBy().getFirstName() + " " + menu.getUpdatedBy().getLastName() : null)
                .restaurantGroupCount(0L)
                .restaurantCount(0L)
                .isEditable(isEditable)
                .menuStructureId(menu.getMenuStructure() != null ? menu.getMenuStructure().getId() : null)
                .build();

        MenuDto<MenuResponse> menuDto = MenuDto.<MenuResponse>builder()
                .menu(menuResponse)
                .build();

        List<Category> rootCategories = categoryRepository
                .findByMenuStructureIdAndIsDeletedFalse(menuStructure.getId())
                .stream()
                .filter(c -> c.getParentCategory() == null)
                .collect(Collectors.toList());

        List<MenuCategoryStructureResponse.CategoryStructureDto> categoryStructure;
        if (menu.getMenuStructure() != null && menuStructure.getId().equals(menu.getMenuStructure().getId())) {
            categoryStructure = buildCategoryStructureWithMenu(
                    rootCategories,
                    menu,
                    new CategoryStructureBuildContext(userLocale, status, search, menu.getId(), null, false));
        } else {
            categoryStructure = buildCategoryStructureWithoutMenu(rootCategories, userLocale);
        }

        MenuCategoryStructureResponse structureResp = MenuCategoryStructureResponse.builder()
                .categories(categoryStructure)
                .build();

        return MenuDetailStructureDto.builder()
                .menu(menuDto)
                .structure(structureResp)
                .build();
    }

    /**
     * Picks the menu translation for {@code locale}, falling back via {@link TranslationUtils} when needed.
     */
    private List<MenuTranslationDto> buildMenuTranslationDtos(List<MenuTranslation> menuTranslations, String locale) {
        List<MenuTranslationDto> translationDtos = new ArrayList<>();
        if (menuTranslations == null || menuTranslations.isEmpty()) {
            return translationDtos;
        }

        MenuTranslation exactMatch = menuTranslations.stream()
                .filter(t -> t.getLanguageCode() != null && t.getLanguageCode().equals(locale))
                .findFirst()
                .orElse(null);

        if (exactMatch != null) {
            translationDtos.add(MenuTranslationDto.builder()
                    .languageCode(exactMatch.getLanguageCode())
                    .name(exactMatch.getName())
                    .description(exactMatch.getDescription())
                    .build());
            return translationDtos;
        }

        java.util.Optional<MenuTranslation> fallback = TranslationUtils.pickPreferredOrFromList(
                menuTranslations,
                locale,
                localizationProperties.getLanguages(),
                MenuTranslation::getLanguageCode);

        fallback.ifPresent(trans -> translationDtos.add(MenuTranslationDto.builder()
                .languageCode(trans.getLanguageCode())
                .name(trans.getName())
                .description(trans.getDescription())
                .build()));

        return translationDtos;
    }

    /**
     * Published menus are never editable; draft menus with a master id are editable only until a published sibling exists.
     */
    private boolean isMenuEditable(Menu menu) {
        if (menu == null) {
            return false;
        }

        if (MenuStatus.PUBLISHED.equals(menu.getStatus())) {
            return false;
        }

        if (MenuStatus.DRAFT.equals(menu.getStatus()) && menu.getMenuMasterId() != null) {
            List<Menu> publishedMenusWithSameMasterId = menuRepository
                    .findByMenuMasterIdAndStatusAndIsDeletedFalseOrderByVersionDesc(
                            menu.getMenuMasterId(), MenuStatus.PUBLISHED);
            return publishedMenusWithSameMasterId.isEmpty();
        }

        return true;
    }

    /**
     * Helper method to check if an item matches the orderType/itemOrderType filter
     * Both filters include BOTH: DINE_IN includes DINE_IN and BOTH, TAKEAWAY includes TAKEAWAY and BOTH
     * @param itemOrderType the item's order type
     * @param filterValue the filter value to match against
     * @param isExactMatch not used anymore (kept for backward compatibility), both filters now include BOTH
     * @return true if the item matches the filter
     */
    private boolean matchesOrderTypeFilter(ItemOrderType itemOrderType, ItemOrderType filterValue, boolean isExactMatch) {
        if (itemOrderType == null || filterValue == null) {
            return false;
        }
        
        // Both itemOrderType and orderType filters now include BOTH
        if (filterValue == ItemOrderType.DINE_IN) {
            return itemOrderType == ItemOrderType.DINE_IN || itemOrderType == ItemOrderType.BOTH;
        } else if (filterValue == ItemOrderType.TAKEAWAY) {
            return itemOrderType == ItemOrderType.TAKEAWAY || itemOrderType == ItemOrderType.BOTH;
        } else if (filterValue == ItemOrderType.BOTH) {
            // If filtering for BOTH, only exact match makes sense
            return itemOrderType == ItemOrderType.BOTH;
        }
        return false;
    }

    /**
     * Context object to keep {@link #buildCategoryStructureWithMenu(List, Menu, CategoryStructureBuildContext)}
     * method parameter count within limits.
     */
    private static class CategoryStructureBuildContext {
        private final Locale locale;
        private final EntityStatus filterStatus;
        private final String search;
        private final UUID menuId;
        private final ItemOrderType orderTypeFilter;
        private final boolean isExactMatchFilter;

        private CategoryStructureBuildContext(Locale locale, EntityStatus filterStatus, String search, UUID menuId,
                ItemOrderType orderTypeFilter, boolean isExactMatchFilter) {
            this.locale = locale;
            this.filterStatus = filterStatus;
            this.search = search;
            this.menuId = menuId;
            this.orderTypeFilter = orderTypeFilter;
            this.isExactMatchFilter = isExactMatchFilter;
        }
    }

    /**
     * Resolves a display name for {@code category} in {@code locale}, falling back to the first available translation.
     */
    private static String localizedCategoryDisplayName(Category category, Locale locale) {
        return category.getTranslations().stream()
                .filter(t -> t.getLanguageCode().equals(locale.getLanguage()))
                .findFirst()
                .map(CategoryTranslation::getName)
                .orElse(category.getTranslations().get(0).getName());
    }

    private static boolean skipCategoryForActiveMenuFilter(
            EntityStatus filterStatus, Optional<MenuCategoryMapping> menuCategoryMapping) {
        return filterStatus == EntityStatus.ACTIVE
                && (menuCategoryMapping.isEmpty() || !EntityStatus.ACTIVE.equals(menuCategoryMapping.get().getStatus()));
    }

    /**
     * Builds a nested category tree for a restaurant menu using live mappings, optional ACTIVE mapping filter,
     * order-type filters, and item name search (see {@link CategoryStructureBuildContext}).
     *
     * @param categories root categories under the menu structure
     * @param menu       menu providing ids for {@code MenuCategoryMapping} lookups
     * @param ctx        aggregated filter and locale context
     * @return DTO list representing categories, subcategories, mapped items, and combos
     */
    private List<MenuCategoryStructureResponse.CategoryStructureDto> buildCategoryStructureWithMenu(
        List<Category> categories, Menu menu, CategoryStructureBuildContext ctx) {
    Locale locale = ctx.locale;
    EntityStatus filterStatus = ctx.filterStatus;
    String search = ctx.search;
    UUID menuId = ctx.menuId;
    ItemOrderType orderTypeFilter = ctx.orderTypeFilter;
    boolean isExactMatchFilter = ctx.isExactMatchFilter;

    return categories.stream()
            .map(category -> {
                // Get menu-category mapping
                Optional<MenuCategoryMapping> menuCategoryMapping = menuCategoryMappingRepository
                        .findByMenuIdAndCategoryId(menu.getId(), category.getId());

                if (skipCategoryForActiveMenuFilter(filterStatus, menuCategoryMapping)) {
                    return null;
                }

                String name = localizedCategoryDisplayName(category, locale);

                // Fetch combos for this category if it's a combo category
                List<Object> categoryCombos = new ArrayList<>();
                if (Boolean.TRUE.equals(category.getIsCombo()) && menuCategoryMapping.isPresent()) {
                    try {
                        List<MenuCategoryComboMapping> comboMappings = menuCategoryComboMappingRepository
                                .findByMenuCategoryMapping_Id(menuCategoryMapping.get().getId());
                        
                        for (MenuCategoryComboMapping mapping : comboMappings) {
                            Combo combo = mapping.getCombo();
                            if (combo != null && !Boolean.TRUE.equals(combo.getIsDeleted()) 
                                    && EntityStatus.ACTIVE.equals(combo.getStatus())) {
                                // Filter by orderType/itemOrderType if provided
                                if (orderTypeFilter != null) {
                                    ItemOrderType comboOrderType = combo.getItemOrderType();
                                    if (!matchesOrderTypeFilter(comboOrderType, orderTypeFilter, isExactMatchFilter)) {
                                        continue; // Skip this combo
                                    }
                                }
                                // Fetch combo details using comboService
                                ResponseDto<ComboDto<ComboDetailsResponse>> comboDetails = comboService
                                        .getComboDetailsById(combo.getComboId(), locale.getLanguage(), null, null);
                                if (comboDetails != null && comboDetails.getData() != null 
                                        && comboDetails.getData().getCombo() != null) {
                                    categoryCombos.add(comboDetails.getData().getCombo());
                                }
                            }
                        }
                    } catch (Exception e) {
                        log.warn("Failed to fetch combos for category {}: {}", category.getId(), e.getMessage());
                    }
                }

                // Get subcategories sorted by displayOrder ASC
                List<Category> subcategories = categoryRepository
                        .findByParentCategoryAndIsDeletedFalseOrderByDisplayOrderAsc(category);
                boolean hasSubcategories = !subcategories.isEmpty();

                if (hasSubcategories) {
                    // Process subcategories
                    List<MenuCategoryStructureResponse.SubCategoryStructureDto> subCategoryDtos = subcategories.stream()
                            .map(subcategory -> {
                                // Get menu-subcategory mapping to check status
                                Optional<MenuCategoryMapping> subCategoryMapping = menuCategoryMappingRepository
                                        .findByMenuIdAndCategoryId(menu.getId(), subcategory.getId());

                                if (skipCategoryForActiveMenuFilter(filterStatus, subCategoryMapping)) {
                                    return null;
                                }

                                // Get all category-item mappings for this subcategory
                                Optional<MenuCategoryMapping> subMcmOpt = menuCategoryMappingRepository
                                .findByMenuIdAndCategoryId(menu.getId(), subcategory.getId());
                            List<CategoryItemMapping> categoryItemMappings = subMcmOpt
                                .map(categoryItemMappingRepository::findByMenuCategoryMapping)
                                .orElse(Collections.emptyList());

                                String subName = localizedCategoryDisplayName(subcategory, locale);

                                // Get items based on filter status
                                List<CategoryItemMapping> relevantMappings = categoryItemMappings;

                                // Convert mappings to ItemResponse objects and apply search filter
                                List<MenuCategoryStructureResponse.MenuItemDto> items = relevantMappings.stream()
                                        .filter(mapping -> {
                                            // Filter by orderType/itemOrderType if provided
                                            if (orderTypeFilter != null) {
                                                ItemOrderType mappedOrderType = mapping.getItemOrderType();
                                                if (mappedOrderType == null) {
                                                    mappedOrderType = ItemOrderType.BOTH;
                                                }
                                                return matchesOrderTypeFilter(mappedOrderType, orderTypeFilter, isExactMatchFilter);
                                            }
                                            return true; // No filter, include all items
                                        })
                                        .map(mapping -> buildMenuItemDto(mapping, locale))
                                        .filter(Objects::nonNull)
                                        .filter(item -> search == null || search.trim().isEmpty() || 
                                                item.getName() != null && 
                                                item.getName().toLowerCase().contains(search.toLowerCase()))
                                        .collect(Collectors.toList());

                                return MenuCategoryStructureResponse.SubCategoryStructureDto.builder()
                                        .id(subcategory.getId())
                                        .name(subName)
                                        .status(subCategoryMapping.map(MenuCategoryMapping::getStatus).orElse(null))
                                        .isCombo(subcategory.getIsCombo())
                                        .items(items)
                                        .build();
                            })
                            .filter(Objects::nonNull)
                            .collect(Collectors.toList());

                    return MenuCategoryStructureResponse.CategoryStructureDto.builder()
                            .id(category.getId())
                            .name(name)
                            .status(menuCategoryMapping.map(MenuCategoryMapping::getStatus).orElse(null))
                            .isCombo(category.getIsCombo())
                            .subcategories(subCategoryDtos)
                            .items(null)
                            .combos(categoryCombos.isEmpty() ? null : categoryCombos)
                            .build();
                }   else {
                    // Process direct items
                    Optional<MenuCategoryMapping> mcmOpt = menuCategoryMappingRepository
                        .findByMenuIdAndCategoryId(menu.getId(), category.getId());
                    List<CategoryItemMapping> categoryItemMappings = mcmOpt
                        .map(categoryItemMappingRepository::findByMenuCategoryMapping)
                        .orElse(Collections.emptyList());

                    List<MenuCategoryStructureResponse.MenuItemDto> items = categoryItemMappings.stream()
                            .filter(mapping -> {
                                // Filter by orderType/itemOrderType if provided
                                if (orderTypeFilter != null) {
                                    ItemOrderType mappedOrderType = mapping.getItemOrderType();
                                    if (mappedOrderType == null) {
                                        mappedOrderType = ItemOrderType.BOTH;
                                    }
                                    return matchesOrderTypeFilter(mappedOrderType, orderTypeFilter, isExactMatchFilter);
                                }
                                return true; // No filter, include all items
                            })
                            .map(mapping -> buildMenuItemDto(mapping, locale))
                            .filter(Objects::nonNull)
                            .filter(item -> search == null || search.trim().isEmpty() || 
                                    item.getName() != null && 
                                    item.getName().toLowerCase().contains(search.toLowerCase()))
                            .collect(Collectors.toList());

                    return MenuCategoryStructureResponse.CategoryStructureDto.builder()
                            .id(category.getId())
                            .name(name)
                            .status(menuCategoryMapping.map(MenuCategoryMapping::getStatus).orElse(null))
                            .isCombo(category.getIsCombo())
                            .subcategories(null)
                            .items(items)
                            .combos(categoryCombos.isEmpty() ? null : categoryCombos)
                            .build();
                }
            })
            .filter(Objects::nonNull)
            .collect(Collectors.toList());
}

    /**
     * Builds a nested category structure without menu mappings.
     * <p>
     * This renders categories/subcategories based on the menu structure definition only, with translated names.
     *
     * @param categories root categories to render
     * @param locale locale used for translations (required)
     * @return rendered category structure list
     */
    private List<MenuCategoryStructureResponse.CategoryStructureDto> buildCategoryStructureWithoutMenu(
            List<Category> categories, Locale locale) {
        return categories.stream()
                .map(category -> {
                    // Get subcategories sorted by displayOrder ASC
                    List<Category> subcategories = categoryRepository
                            .findByParentCategoryIdAndIsDeletedFalseOrderByDisplayOrderAsc(category.getId());

                    String name = localizedCategoryDisplayName(category, locale);

                    if (!subcategories.isEmpty()) {
                        // If category has subcategories
                        List<MenuCategoryStructureResponse.SubCategoryStructureDto> subCategoryDtos = subcategories.stream()
                                .map(subCategory -> {
                                    String subName = localizedCategoryDisplayName(subCategory, locale);

                                    return MenuCategoryStructureResponse.SubCategoryStructureDto.builder()
                                            .id(subCategory.getId())
                                            .name(subName)
                                            .status(subCategory.getStatus())
                                            .isCombo(subCategory.getIsCombo())
                                            .items(Collections.emptyList())
                                            .build();
                                })
                                .collect(Collectors.toList());

                        return MenuCategoryStructureResponse.CategoryStructureDto.builder()
                                .id(category.getId())
                                .name(name)
                                .status(category.getStatus())
                                .isCombo(category.getIsCombo())
                                .subcategories(subCategoryDtos)
                                .items(null)
                                .build();
                    } else {
                        return MenuCategoryStructureResponse.CategoryStructureDto.builder()
                                .id(category.getId())
                                .name(name)
                                .status(category.getStatus())
                                .isCombo(category.getIsCombo())
                                .subcategories(null)
                                .items(Collections.emptyList())
                                .build();
                    }
                })
                .collect(Collectors.toList());
    }

    /**
     * Maps a {@link CategoryItemMapping} to a {@link MenuCategoryStructureResponse.MenuItemDto} with locale-based translation
     * selection and signed image URL (when present).
     *
     * @param mapping category-item mapping providing item and order-type context (required)
     * @param locale locale used for item translation selection (required)
     * @return menu item DTO or {@code null} if the mapping has no item
     */
    private MenuCategoryStructureResponse.MenuItemDto buildMenuItemDto(CategoryItemMapping mapping, Locale locale) {
        Item item = mapping.getItem();
        if (item == null) {
            return null;
        }

        String itemName = item.getTranslations().stream()
        .filter(t -> t.getLanguageCode().equals(locale.getLanguage()))
        .findFirst()
        .map(ItemTranslation::getName)
        .orElse(item.getTranslations().get(0).getName());


            String itemDesc = item.getTranslations().stream()
            .filter(t -> t.getLanguageCode().equals(locale.getLanguage()))
            .findFirst()
            .map(ItemTranslation::getDescription)
            .orElse(null);

            String signedImageUrl = null;
            if (item.getImageUrl() != null && !item.getImageUrl().isEmpty()) {
                signedImageUrl = awsService.getPreSignedUrl(item.getImageUrl());
            }

        MenuCategoryStructureResponse.MenuItemDto dto = MenuCategoryStructureResponse.MenuItemDto.builder()
                .id(item.getId())
                .name(itemName)
                .description(itemDesc)
                .basePrice(item.getBasePrice())
                .alcoholType(item.getAlcoholType())
                .outOfStock(item.getOutOfStock())
                .status(item.getStatus())
                .imageUrl(signedImageUrl)
                .build();
        setMenuItemOrderType(dto, mapping.getItemOrderType() != null ? mapping.getItemOrderType() : ItemOrderType.BOTH);
        return dto;
    
    }

    private void setMenuItemOrderType(MenuCategoryStructureResponse.MenuItemDto dto, ItemOrderType orderType) {
        if (dto == null || orderType == null) {
            return;
        }
        try {
            java.lang.reflect.Method setter = dto.getClass().getMethod("setItemOrderType", ItemOrderType.class);
            setter.invoke(dto, orderType);
        } catch (Exception ignored) {
            // Older DTO shape without itemOrderType field.
        }
    }

    private User loadUserOrThrowForUpdate(String userId, Locale userLocale) {
        return userRepository.findById(UUID.fromString(userId))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        messageUtil.getMessage(MSG_USER_NOT_FOUND, userLocale, userId)));
    }

    /**
     * Blocks deactivating a structure that is still assigned to any published menu.
     */
    private void validateMenuStructureStatusTransition(MenuStructure menuStructure, MenuStructureRequest request,
            Locale userLocale) {
        if (EntityStatus.ACTIVE.equals(menuStructure.getStatus())
                && EntityStatus.INACTIVE.equals(request.getStatus())) {
            boolean hasPublishedMenus = menuRepository.existsByMenuStructureIdAndStatusAndIsDeletedFalse(
                    menuStructure.getId(), MenuStatus.PUBLISHED);

            if (hasPublishedMenus) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        messageUtil.getMessage("menu.structure.update.error.assigned.to.published.menu", userLocale));
            }
        }
    }

    /**
     * Validates update translation payloads (required names, language codes, uniqueness) and returns the same list.
     */
    private List<MenuStructureTranslationDto> validateUpdateTranslationsOrThrow(
            List<MenuStructureTranslationDto> translations,
            MenuStructure menuStructure,
            Locale userLocale) {
        if (translations == null || translations.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    messageUtil.getMessage("combo.translations.required", userLocale));
        }

        boolean hasValidName = translations.stream()
                .anyMatch(t -> t.getName() != null && !t.getName().trim().isEmpty());

        if (!hasValidName) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    messageUtil.getMessage("item.update.error.no.valid.name", userLocale));
        }

        validateMenuStructureTranslationsLanguageCodes(translations, userLocale);

        for (MenuStructureTranslationDto entry : translations) {
            String name = entry.getName();
            String languageCode = entry.getLanguageCode();

            if (name != null && !name.trim().isEmpty() && languageCode != null) {
                boolean exists = translationRepository
                        .existsByNameAndLanguageCodeAndMenuStructureIdNot(name.trim(), languageCode,
                                menuStructure.getId());
                if (exists) {
                    throw new ResponseStatusException(HttpStatus.CONFLICT,
                            messageUtil.getMessage("menu.error.name.exists", userLocale, name.trim(), languageCode));
                }
            }
        }

        return translations;
    }

    /**
     * Deletes translations removed by the request and upserts remaining rows for {@code menuStructure}.
     */
    private void syncMenuStructureTranslations(MenuStructure menuStructure,
            List<MenuStructureTranslationDto> translations) {
        List<MenuStructureTranslation> existingTranslations = translationRepository
                .findAllByMenuStructureId(menuStructure.getId());
        Map<String, MenuStructureTranslation> existingTranslationMap = existingTranslations.stream()
                .collect(Collectors.toMap(MenuStructureTranslation::getLanguageCode, t -> t));

        Set<String> validRequestLanguageCodes = translations.stream()
                .filter(t -> t.getLanguageCode() != null
                        && t.getName() != null
                        && !t.getName().trim().isEmpty())
                .map(MenuStructureTranslationDto::getLanguageCode)
                .collect(Collectors.toSet());

        List<MenuStructureTranslation> translationsToRemove = new ArrayList<>();
        for (MenuStructureTranslation existingTranslation : existingTranslations) {
            if (!validRequestLanguageCodes.contains(existingTranslation.getLanguageCode())) {
                translationsToRemove.add(existingTranslation);
            }
        }

        for (MenuStructureTranslation translationToRemove : translationsToRemove) {
            translationRepository.delete(translationToRemove);
        }

        for (MenuStructureTranslationDto entry : translations) {
            String name = entry.getName();
            String languageCode = entry.getLanguageCode();

            if (name != null && !name.trim().isEmpty() && languageCode != null) {
                MenuStructureTranslation translation = existingTranslationMap.get(languageCode);
                if (translation != null) {
                    translation.setName(name.trim());
                    translationRepository.save(translation);
                } else {
                    translation = new MenuStructureTranslation();
                    translation.setName(name.trim());
                    translation.setMenuStructure(menuStructure);
                    translation.setLanguageCode(languageCode);
                    translationRepository.save(translation);
                }
            }
        }
    }

    /**
     * Detail response for update flows (includes full translation list; omits menu count used by list/detail read APIs).
     */
    private MenuStructureResponse buildMenuStructureResponseForUpdate(MenuStructure menuStructure,
            List<MenuStructureTranslationDto> translationDTOs) {
        return MenuStructureResponse.builder()
                .id(menuStructure.getId())
                .status(menuStructure.getStatus())
                .isDeleted(menuStructure.getIsDeleted())
                .createdAt(menuStructure.getCreatedAt() != null ? menuStructure.getCreatedAt().toLocalDateTime() : null)
                .createdBy(menuStructure.getCreatedBy().getFirstName() + " " + menuStructure.getCreatedBy().getLastName())
                .updatedAt(menuStructure.getUpdatedAt() != null ? menuStructure.getUpdatedAt().toLocalDateTime() : null)
                .updatedBy(menuStructure.getUpdatedBy().getFirstName() + " " + menuStructure.getUpdatedBy().getLastName())
                .translations(translationDTOs)
                .build();
    }

    /**
     * Validates duplicate language codes and language code validity for menu structure translations.
     * This shared method is used by both create and update operations.
     * 
     * @param translations List of translation DTOs to validate
     * @param userLocale User's locale for error messages
     */
    private void validateMenuStructureTranslationsLanguageCodes(
            List<MenuStructureTranslationDto> translations, 
            Locale userLocale) {
        // Check for duplicate language codes in the request
        Set<String> languageCodes = new HashSet<>();
        for (MenuStructureTranslationDto entry : translations) {
            String name = entry.getName();
            String languageCode = entry.getLanguageCode();
            
            // Only validate non-empty names
            if (name != null && !name.trim().isEmpty() && languageCode != null) {
                if (!languageCodes.add(languageCode)) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                            messageUtil.getMessage("menu.error.duplicate.language", userLocale, languageCode));
                }
        
                if (!localizationProperties.getLanguages().contains(languageCode)) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                            messageUtil.getMessage(MSG_MENU_ERROR_INVALID_LANGUAGE, userLocale));
                }
            }
        }
    }

    /**
     * Builds a ResponseDto wrapping a MenuStructureDto with the provided response and message.
     * This shared method is used by both create and update operations.
     * 
     * @param response The MenuStructureResponse to wrap
     * @param messageKey The message key for the success message
     * @param userLocale User's locale for message translation
     * @return ResponseDto containing the wrapped MenuStructureDto
     */
    private ResponseDto<MenuStructureDto<MenuStructureResponse>> buildMenuStructureResponseDto(
            MenuStructureResponse response,
            String messageKey,
            Locale userLocale) {
        MenuStructureDto<MenuStructureResponse> menuStructureDto = MenuStructureDto.<MenuStructureResponse>builder()
                .menuStructure(response)
                .build();

        return ResponseDto.<MenuStructureDto<MenuStructureResponse>>builder()
                .message(messageUtil.getMessage(messageKey, userLocale))
                .data(menuStructureDto)
                .build();
    }
}


