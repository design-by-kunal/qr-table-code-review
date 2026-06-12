package com.gulfnet.restaurantmanagement.service.impl;

import com.gulfnet.restaurantmanagement.service.KdsService;
import com.gulfnet.restaurantmanagement.service.AuditTrailService;
import com.gulfnet.restaurantmanagement.util.MessageUtil;
import com.gulfnet.shared_library.enums.ActionType;
import com.gulfnet.shared_library.enums.RequestStatus;
import com.gulfnet.restaurantmanagement.config.LocalizationProperties;
import com.gulfnet.shared_library.entity.*;
import com.gulfnet.shared_library.enums.EntityStatus;
import com.gulfnet.shared_library.model.request.KdsRequest;
import com.gulfnet.shared_library.model.request.AssignUserToKdsRequest;
import com.gulfnet.shared_library.model.request.UnassignUserFromKdsRequest;
import com.gulfnet.shared_library.model.request.UpdateKdsConfigRequest;
import com.gulfnet.shared_library.model.request.AssignDeviceToKdsRequest;
import com.gulfnet.shared_library.model.response.dto.KdsConfigurationListResponse;
import com.gulfnet.shared_library.model.response.dto.KdsConfigurationResponse;
import com.gulfnet.shared_library.model.response.dto.KdsDto;
import com.gulfnet.shared_library.model.response.dto.KdsListResponse;
import com.gulfnet.shared_library.model.response.dto.KdsAssignedUserListResponse;
import com.gulfnet.shared_library.model.response.dto.KdsAssignedUserResponse;
import com.gulfnet.shared_library.model.response.dto.CategoryWrapperResponse;
import com.gulfnet.shared_library.model.response.dto.CategoryListData;
import com.gulfnet.shared_library.model.response.dto.KdsResponse;
import com.gulfnet.shared_library.model.response.dto.CategoryResponse;
import com.gulfnet.shared_library.model.response.dto.KdsTranslationDto;
import com.gulfnet.shared_library.model.response.dto.PaginationMetaData;
import com.gulfnet.shared_library.model.response.dto.ResponseDto;
import com.gulfnet.shared_library.model.response.dto.ComboResponse;
import com.gulfnet.shared_library.model.response.dto.ComboTranslationDto;
import com.gulfnet.shared_library.model.response.dto.TicketDashboardListDto;
import com.gulfnet.shared_library.model.response.dto.TicketDetailsResponse;
import com.gulfnet.shared_library.model.response.dto.TicketDashboardResponse;
import com.gulfnet.shared_library.model.response.dto.OrderedItemModifierResponse;
import com.gulfnet.shared_library.model.response.dto.OrderedItemResponse;
import com.gulfnet.shared_library.model.response.dto.ModifierItemResponse;
import com.gulfnet.shared_library.enums.ItemStatus;
import com.gulfnet.shared_library.enums.OrderType;
import com.gulfnet.shared_library.util.CurrencyFormatter;
import com.gulfnet.shared_library.repository.*;
import com.gulfnet.shared_library.util.LocaleSortUtil;

import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.transaction.annotation.Transactional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;

import java.time.LocalDateTime;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.OffsetTime;
import java.time.ZoneOffset;
import java.util.*;
import java.util.stream.Collectors;
import java.util.Optional;
import java.util.Comparator;
import java.util.Arrays;
import java.util.Collection;
import java.util.Objects;
import java.math.BigDecimal;

@Slf4j
@Service
public class KdsServiceImpl implements KdsService {

    // Constants
    private static final String DEFAULT_NO_TRANSLATIONS = "No translations";
    private static final String msgRestaurantNotFound = "restaurant.not.found";
    private static final String msgKdsRestaurantMismatch = "kds.restaurant.mismatch";
    
    // Message key constants
    private static final String MSG_RESTAURANT_NO_MENUS = "restaurant.no.menus";
    private static final String MSG_KDS_GET_ERROR_DELETED = "kds.get.error.deleted";
    private static final String MSG_CATEGORY_NOT_FOUND = "category.not.found";
    private static final String MSG_CATEGORY_KDS_UNASSIGNED_SUCCESS = "category.kds.unassigned.success";
    private static final String MSG_USER_NOT_FOUND = "user.not.found";
    private static final String MSG_TICKET_DASHBOARD_RETRIEVED_SUCCESS = "ticket.dashboard.retrieved.success";
    private static final String MSG_KDS_GET_ERROR_NOT_FOUND = "kds.get.error.not_found";
    private static final String MSG_USER_RESTAURANT_NOT_ASSIGNED = "user.restaurant.not.assigned";
    private static final String MSG_KDS_ERROR_INVALID_LANGUAGE = "kds.error.invalid.language";
    private static final String MSG_KDS_DELETE_ERROR_NOT_FOUND = "kds.delete.error.not_found";

    @Autowired
    private KdsRepository kdsRepository;

    @Autowired
    private KdsTranslationRepository kdsTranslationRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private CategoryKdsRepository categoryKdsRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private MenuCategoryMappingRepository menuCategoryMappingRepository;

    @Autowired
    private RestaurantMenuMappingRepository restaurantMenuMappingRepository;

    @Autowired
    private MessageUtil messageUtil;

    @Autowired
    private LocalizationProperties localizationProperties;

    @Autowired
    private CategoryTranslationRepository categoryTranslationRepository;

    @Autowired
    private KdsConfigurationRepository kdsConfigurationRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private ComboKdsRepository comboKdsRepository;

    @Autowired
    private ComboRepository comboRepository;

    @Autowired
    private ComboTranslationRepository comboTranslationRepository;
    
    // Dependencies for ticket dashboard methods
    @Autowired
    private OrderedItemRepository orderedItemRepository;
    
    @Autowired
    private OrderedItemModifierRepository orderedItemModifierRepository;
    
    @Autowired
    private RestaurantRepository restaurantRepository;

    @Autowired
    private AuditTrailService auditTrailService;
    
    @Autowired
    private CategoryItemMappingRepository categoryItemMappingRepository;
    
    @Autowired
    private ModifierGroupRepository modifierGroupRepository;
    
    @Autowired
    private ModifierItemRepository modifierItemRepository;
    
    @Autowired
    private com.gulfnet.restaurantmanagement.config.RestaurantChainConfigProperties restaurantChainConfigProperties;
    
    @Autowired
    private com.gulfnet.shared_library.config.AWSService awsService;
    
    @Autowired
    private com.gulfnet.restaurantmanagement.service.OrderValidationService orderValidationService;
    
    @Autowired
    private RestaurantTableRepository restaurantTableRepository;

    @Autowired
    private TableAssignmentRepository tableAssignmentRepository;
    
    @Autowired
    private com.gulfnet.restaurantmanagement.service.OrderNotificationService orderNotificationService;

    /**
     * Generates a unique device code for KDS
     * Format: KDS-{8 character alphanumeric}
     */
    private String generateUniqueDeviceCode() {
        String deviceCode;
        int maxAttempts = 10;
        int attempts = 0;
        
        do {
            // Generate a random 8-character alphanumeric code
            String randomPart = UUID.randomUUID().toString().replace("-", "").substring(0, 8).toUpperCase();
            deviceCode = "KDS-" + randomPart;
            attempts++;
            
            if (attempts >= maxAttempts) {
                throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                        "Failed to generate unique device code after " + maxAttempts + " attempts");
            }
        } while (kdsRepository.existsByDeviceCode(deviceCode) || 
                 kdsConfigurationRepository.existsByDeviceCode(deviceCode));
        
        return deviceCode;
    }

    /**
     * Creates a new KDS device record for the requesting user's restaurant.
     * <p>
     * Validates the user, enforces the “single default KDS per restaurant” rule, validates translations
     * (at least one non-empty name, valid and non-duplicate language codes, and name uniqueness per restaurant+language),
     * persists the KDS and its translations, and returns the created record.
     * </p>
     *
     * @param userId  id of the user creating the KDS (string UUID)
     * @param request request payload including status, default flag, and translations
     * @param locale  locale tag used for localized messages and translation validation
     * @return response wrapper containing the created KDS DTO
     * @throws ResponseStatusException if validation fails or referenced entities are not found
     */
    @Override
    @Transactional
    public ResponseDto<KdsDto<KdsResponse>> createKds(String userId, KdsRequest request, String locale) {
        Locale userLocale = Locale.forLanguageTag(locale);
        
        // Validate user exists
        User user = userRepository.findById(UUID.fromString(userId))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        messageUtil.getMessage(MSG_USER_NOT_FOUND, userLocale, userId)));

        // Check if user is trying to set KDS as default and if a default already exists
        if (Boolean.TRUE.equals(request.getIsDefault()) && user.getRestaurantId() != null) {
            List<Kds> existingDefaultKdsList = kdsRepository.findByRestaurantIdAndIsDefaultTrueAndIsDeletedFalse(user.getRestaurantId());
            if (!existingDefaultKdsList.isEmpty()) {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        messageUtil.getMessage("kds.create.error.default.already.exists", userLocale));
            }
        }

        List<KdsTranslationDto> translations = request.getTranslations();
        
        // Validate translations
        if (translations != null && !translations.isEmpty()) {
            // Validate that at least one translation has a non-empty name
            boolean hasValidName = translations.stream()
                    .anyMatch(t -> t.getName() != null && !t.getName().trim().isEmpty());

            if (!hasValidName) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        messageUtil.getMessage("kds.create.error.no.valid.name", userLocale));
            }

            // Check for duplicate language codes and validate language codes
            Set<String> languageCodes = new HashSet<>();
            for (KdsTranslationDto entry : translations) {
                String name = entry.getName();
                String lang = entry.getLanguageCode();

                // Only validate non-empty names
                if (name != null && !name.trim().isEmpty() && lang != null) {
                    if (!localizationProperties.getLanguages().contains(lang)) {
                        throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                                messageUtil.getMessage(MSG_KDS_ERROR_INVALID_LANGUAGE, userLocale));
                    }
                    if (!languageCodes.add(lang)) {
                        throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                                messageUtil.getMessage("kds.error.duplicate.language", userLocale, lang));
                    }

                    // Check for existing names in the same language at restaurant level
                    if (user.getRestaurantId() != null) {
                        boolean exists = kdsTranslationRepository.existsByNameAndLanguageCodeAndRestaurantId(
                                name.trim(), lang, user.getRestaurantId());
                        if (exists) {
                            throw new ResponseStatusException(HttpStatus.CONFLICT,
                                    messageUtil.getMessage("kds.create.error.name.exists", userLocale));
                        }
                    }
                }
            }
        } else {
            // No translations provided at all
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    messageUtil.getMessage("kds.create.error.no.translations", userLocale));
        }

        // Create KDS entity using builder (device code will be set during initialization from Flutter)
        Kds.KdsBuilder builder = Kds.builder()
                .status(request.getStatus())
                .isDeleted(Boolean.TRUE.equals(request.getIsDeleted()))
                .isDefault(Boolean.TRUE.equals(request.getIsDefault()))
                .createdAt(OffsetDateTime.now(ZoneOffset.UTC))
                .createdBy(user)
                .translations(new ArrayList<>());
        
        // Set restaurant ID using reflection to avoid builder issues
        Kds kds = builder.build();
        kds.setRestaurantId(user.getRestaurantId());
        kds = kdsRepository.save(kds);

        // Save translations
        if (translations != null && !translations.isEmpty()) {
            for (KdsTranslationDto entry : translations) {
                String name = entry.getName();
                if (name != null && !name.trim().isEmpty() && entry.getLanguageCode() != null) {
                    KdsTranslation translation = new KdsTranslation();
                    translation.setName(name.trim());
                    translation.setKds(kds);
                    translation.setLanguageCode(entry.getLanguageCode());
                    kdsTranslationRepository.save(translation);
                }
            }
        }

        // Fetch saved translations
        List<KdsTranslation> savedTranslations = kdsTranslationRepository.findAllByKdsId(kds.getId());
        List<KdsTranslationDto> translationDTOs = savedTranslations.stream()
                .map(t -> KdsTranslationDto.builder()
                        .languageCode(t.getLanguageCode())
                        .name(t.getName())
                        .build())
                .collect(Collectors.toList());

        // Assign categories if provided in request
        // Support both categoryIds (master category IDs) and menuCategoryMappingIds
        UUID restaurantId = user.getRestaurantId();
        
        if (restaurantId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    messageUtil.getMessage(MSG_USER_RESTAURANT_NOT_ASSIGNED, userLocale));
        }

        // Get all menus for the restaurant
        List<RestaurantMenuMapping> restaurantMenuMappings = restaurantMenuMappingRepository
                .findById_RestaurantId(restaurantId);
        if (restaurantMenuMappings == null || restaurantMenuMappings.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    messageUtil.getMessage(MSG_RESTAURANT_NO_MENUS, userLocale));
        }

        Set<UUID> restaurantMenuIds = restaurantMenuMappings.stream()
                .map(rmm -> rmm.getMenu().getId())
                .collect(Collectors.toSet());

        // Collect menu category mappings to assign
        List<MenuCategoryMapping> menuCategoryMappingsToAssign = new ArrayList<>();
        Set<UUID> processedMenuCategoryMappingIds = new HashSet<>();
        
        // Collect combo IDs that were mistakenly sent in categoryIds
        // Since CategoryListData uses id field for both categories and combos,
        // frontend may send combo IDs in categoryIds field
        Set<UUID> comboIdsFromCategoryIds = new HashSet<>();

        // If categoryIds are provided, convert them to menuCategoryMappingIds
        if (request.getCategoryIds() != null && !request.getCategoryIds().isEmpty()) {
            // Remove duplicates from categoryIds
            Set<UUID> uniqueCategoryIds = new HashSet<>(request.getCategoryIds());
            
            // Find menu category mappings for these category IDs across all restaurant menus
            for (UUID categoryId : uniqueCategoryIds) {
                // Check if this ID is actually a combo ID (CategoryListData confusion)
                // Since combos are treated as categories for KDS, we need to differentiate
                if (comboRepository.existsById(categoryId)) {
                    // This is a combo ID, not a category ID - collect it to process as combo
                    comboIdsFromCategoryIds.add(categoryId);
                    continue;
                }
                
                // Validate category exists and is not deleted
                Category category = categoryRepository.findByIdAndIsDeletedFalse(categoryId)
                        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                                messageUtil.getMessage(MSG_CATEGORY_NOT_FOUND, userLocale, categoryId)));

                // Find menu category mappings for this category in restaurant's menus
                List<MenuCategoryMapping> mappings = menuCategoryMappingRepository.findByCategory_IdIn(
                        Collections.singletonList(categoryId));
                
                // Filter to only include mappings from restaurant's menus with ACTIVE status
                List<MenuCategoryMapping> restaurantMappings = mappings.stream()
                        .filter(mcm -> mcm.getMenu() != null && 
                                      restaurantMenuIds.contains(mcm.getMenu().getId()) &&
                                      EntityStatus.ACTIVE.equals(mcm.getStatus()))
                        .collect(Collectors.toList());

                if (restaurantMappings.isEmpty()) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                            messageUtil.getMessage("category.not.assigned.to.menu", userLocale));
                }

                // Add all mappings for this category (in case category exists in multiple menus)
                for (MenuCategoryMapping mcm : restaurantMappings) {
                    if (!processedMenuCategoryMappingIds.contains(mcm.getId())) {
                        menuCategoryMappingsToAssign.add(mcm);
                        processedMenuCategoryMappingIds.add(mcm.getId());
                    }
                }
            }
        }

        // If menuCategoryMappingIds are also provided, add them (avoiding duplicates)
        if (request.getMenuCategoryMappingIds() != null && !request.getMenuCategoryMappingIds().isEmpty()) {
            Set<UUID> duplicateMappingIds = new HashSet<>();
            
            for (UUID menuCategoryMappingId : request.getMenuCategoryMappingIds()) {
                // Check for duplicates in request
                if (!duplicateMappingIds.add(menuCategoryMappingId)) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                            messageUtil.getMessage("category.duplicate.in.request", userLocale, menuCategoryMappingId));
                }

                // Skip if already processed from categoryIds
                if (processedMenuCategoryMappingIds.contains(menuCategoryMappingId)) {
                    continue;
                }

                // Fetch MenuCategoryMapping
                MenuCategoryMapping menuCategoryMapping = menuCategoryMappingRepository.findById(menuCategoryMappingId)
                        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                                messageUtil.getMessage("menu.category.mapping.not.found", userLocale, menuCategoryMappingId)));

                // Validate MenuCategoryMapping belongs to one of the restaurant's menus
                if (menuCategoryMapping.getMenu() == null || 
                    !restaurantMenuIds.contains(menuCategoryMapping.getMenu().getId())) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                            messageUtil.getMessage("menu.category.mapping.not.in.restaurant.menu", userLocale, menuCategoryMappingId));
                }

                // Validate category is not deleted
                Category category = menuCategoryMapping.getCategory();
                if (category == null) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                            messageUtil.getMessage(MSG_CATEGORY_NOT_FOUND, userLocale, menuCategoryMappingId));
                }

                if (Boolean.TRUE.equals(category.getIsDeleted())) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                            messageUtil.getMessage("category.deleted", userLocale, category.getId()));
                }

                menuCategoryMappingsToAssign.add(menuCategoryMapping);
                processedMenuCategoryMappingIds.add(menuCategoryMappingId);
            }
        }

        // Create CategoryKds mappings for all collected menu category mappings
        if (!menuCategoryMappingsToAssign.isEmpty()) {
            for (MenuCategoryMapping menuCategoryMapping : menuCategoryMappingsToAssign) {
                // Check if this MenuCategoryMapping is already assigned to this KDS
                if (categoryKdsRepository.existsByMenuCategoryMappingIdAndKdsId(menuCategoryMapping.getId(), kds.getId())) {
                    Category category = menuCategoryMapping.getCategory();
                    throw new ResponseStatusException(HttpStatus.CONFLICT,
                            messageUtil.getMessage("category.already.assigned.to.kds", userLocale, 
                                    category != null ? category.getId() : menuCategoryMapping.getId(), kds.getId()));
                }

                CategoryKds categoryKds = CategoryKds.builder()
                        .menuCategoryMapping(menuCategoryMapping)
                        .kds(kds)
                        .build();
                categoryKdsRepository.save(categoryKds);
            }
        }

        // Assign combos if provided in request
        // Also include combo IDs that were mistakenly sent in categoryIds
        Set<UUID> allComboIds = new HashSet<>();
        if (request.getComboIds() != null && !request.getComboIds().isEmpty()) {
            allComboIds.addAll(request.getComboIds());
        }
        // Add combo IDs that were found in categoryIds field
        allComboIds.addAll(comboIdsFromCategoryIds);
        
        if (!allComboIds.isEmpty()) {
            // Remove duplicates from comboIds
            Set<UUID> uniqueComboIds = new HashSet<>(allComboIds);
            
            for (UUID comboId : uniqueComboIds) {
                // Validate combo exists and is not deleted
                Combo combo = comboRepository.findById(comboId)
                        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                                messageUtil.getMessage("combo.not.found", userLocale, comboId)));

                if (Boolean.TRUE.equals(combo.getIsDeleted())) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                            messageUtil.getMessage("combo.deleted", userLocale, comboId));
                }

                // Validate combo belongs to one of the restaurant's menus
                if (combo.getMenu() == null || !restaurantMenuIds.contains(combo.getMenu().getId())) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                            messageUtil.getMessage("combo.not.assigned.to.restaurant.menu", userLocale, comboId));
                }

                // Check if this combo is already assigned to this KDS
                if (comboKdsRepository.existsByComboIdAndKdsId(comboId, kds.getId())) {
                    throw new ResponseStatusException(HttpStatus.CONFLICT,
                            messageUtil.getMessage("combo.already.assigned.to.kds", userLocale, comboId, kds.getId()));
                }

                // Create ComboKds mapping
                ComboKds comboKds = ComboKds.builder()
                        .combo(combo)
                        .kds(kds)
                        .build();
                comboKdsRepository.save(comboKds);
            }
        }

        // Populate assigned categories for response
        List<CategoryKds> createdCategoryKdsMappings = categoryKdsRepository.findByKdsId(kds.getId());
        List<CategoryListData> createdAssignedRootCategories = new ArrayList<>();
        List<CategoryResponse.CategoryData> createdAssignedSubCategories = new ArrayList<>();

        for (CategoryKds ck : createdCategoryKdsMappings) {
            MenuCategoryMapping menuCategoryMapping = ck.getMenuCategoryMapping();
            if (menuCategoryMapping == null || menuCategoryMapping.getCategory() == null) {
                continue;
            }

            Category category = menuCategoryMapping.getCategory();

            String categoryName = categoryTranslationRepository
                    .findByCategoryIdAndLanguageCode(category.getId(), locale)
                    .map(CategoryTranslation::getName)
                    .orElse(null);

            if (category.getParentCategory() == null) {
                CategoryListData data = CategoryListData.builder()
                        .id(category.getId())
                        .status(category.getStatus())
                        .name(categoryName)
                        .menuCategoryMappingId(menuCategoryMapping.getId())
                        .createdBy(category.getCreatedBy() != null ? category.getCreatedBy().getFirstName() : null)
                        .createdAt(category.getCreatedAt() != null ? category.getCreatedAt().toLocalDateTime() : null)
                        .updatedBy(category.getUpdatedBy() != null ? category.getUpdatedBy().getFirstName() : null)
                        .updatedAt(category.getUpdatedAt() != null ? category.getUpdatedAt().toLocalDateTime() : null)
                        .displayOrder(category.getDisplayOrder())
                        .build();
                createdAssignedRootCategories.add(data);
            } else {
                Category parent = category.getParentCategory();
                String parentName = parent != null ? categoryTranslationRepository
                        .findByCategoryIdAndLanguageCode(parent.getId(), locale)
                        .map(CategoryTranslation::getName)
                        .orElse(null) : null;

                CategoryResponse.CategoryData sub = CategoryResponse.CategoryData.builder()
                        .id(category.getId())
                        .parentCategoryId(parent != null ? parent.getId() : null)
                        .parentCategoryName(parentName)
                        .status(category.getStatus())
                        .name(categoryName)
                        .menuCategoryMappingId(menuCategoryMapping.getId())
                        .createdBy(category.getCreatedBy() != null ? category.getCreatedBy().getFirstName() : null)
                        .createdAt(category.getCreatedAt() != null ? category.getCreatedAt().toLocalDateTime() : null)
                        .updatedBy(category.getUpdatedBy() != null ? category.getUpdatedBy().getFirstName() : null)
                        .updatedAt(category.getUpdatedAt() != null ? category.getUpdatedAt().toLocalDateTime() : null)
                        .displayOrder(category.getDisplayOrder())
                        .build();
                createdAssignedSubCategories.add(sub);
            }
        }

        // Populate assigned combos for response
        List<ComboKds> createdComboKdsMappings = comboKdsRepository.findByKdsId(kds.getId());
        List<ComboResponse> assignedCombos = new ArrayList<>();
        List<CategoryListData> createdAssignedComboCategories = new ArrayList<>();
        
        for (ComboKds ck : createdComboKdsMappings) {
            Combo combo = ck.getCombo();
            if (combo == null) {
                continue;
            }

            // Get combo translations
            List<ComboTranslation> comboTranslations = comboTranslationRepository.findByComboComboId(combo.getComboId());
            List<ComboTranslationDto> comboTranslationDtos = comboTranslations.stream()
                    .map(t -> ComboTranslationDto.builder()
                            .languageCode(t.getLanguageCode())
                            .name(t.getName())
                            .description(t.getDescription())
                            .build())
                    .collect(Collectors.toList());

            // Get combo name in the requested locale
            String comboName = comboTranslations.stream()
                    .filter(t -> t.getLanguageCode() != null && t.getLanguageCode().equals(locale))
                    .map(ComboTranslation::getName)
                    .findFirst()
                    .orElse(null);

            // Add combo as CategoryListData so it appears in the categories list (for consistency with selection API)
            CategoryListData comboAsCategory = CategoryListData.builder()
                    .id(combo.getComboId()) // Use combo ID as the ID
                    .comboId(combo.getComboId()) // Mark this as a combo
                    .status(combo.getStatus())
                    .name(comboName)
                    .displayOrder(null) // Combos don't have displayOrder
                    .createdAt(combo.getCreatedAt() != null ? combo.getCreatedAt().toLocalDateTime() : null)
                    .createdBy(combo.getCreatedBy() != null ? combo.getCreatedBy().getFirstName() : null)
                    .updatedAt(combo.getUpdatedAt() != null ? combo.getUpdatedAt().toLocalDateTime() : null)
                    .updatedBy(combo.getUpdatedBy() != null ? combo.getUpdatedBy().getFirstName() : null)
                    .menuCategoryMappingId(null) // Combos don't have menu category mapping IDs
                    .build();
            createdAssignedComboCategories.add(comboAsCategory);

            // Also keep the ComboResponse for backward compatibility
            ComboResponse comboResponse = ComboResponse.builder()
                    .comboId(combo.getComboId())
                    .menuId(combo.getMenu() != null ? combo.getMenu().getId() : null)
                    .type(combo.getType())
                    .basePrice(combo.getBasePrice())
                    .comboImageUrl(awsService.getFullUrl(combo.getComboImageUrl()))
                    .status(combo.getStatus())
                    .validFrom(combo.getValidFrom())
                    .validTo(combo.getValidTo())
                    .startTime(combo.getStartTime())
                    .endTime(combo.getEndTime())
                    .daysOfWeek(combo.getDaysOfWeek())
                    .createdAt(combo.getCreatedAt() != null ? combo.getCreatedAt().toLocalDateTime() : null)
                    .createdBy(combo.getCreatedBy() != null ? combo.getCreatedBy().getFirstName() : null)
                    .updatedAt(combo.getUpdatedAt() != null ? combo.getUpdatedAt().toLocalDateTime() : null)
                    .updatedBy(combo.getUpdatedBy() != null ? combo.getUpdatedBy().getFirstName() : null)
                    .translations(comboTranslationDtos)
                    .build();
            assignedCombos.add(comboResponse);
        }

        // Combine categories and combos into a single list for consistency with selection API
        List<CategoryListData> allCreatedAssignedCategories = new ArrayList<>(createdAssignedRootCategories);
        allCreatedAssignedCategories.addAll(createdAssignedComboCategories);

        // Check if device is linked (deviceCode is not null and not empty)
        Boolean isDeviceLinked = kds.getDeviceCode() != null && !kds.getDeviceCode().trim().isEmpty();
        
        // Build response
        KdsResponse response = KdsResponse.builder()
                .id(kds.getId())
                .status(kds.getStatus())
                .isDeleted(kds.getIsDeleted())
                .isDefault(kds.getIsDefault())
                .deviceCode(kds.getDeviceCode())
                .isDeviceLinked(isDeviceLinked)
                .createdAt(kds.getCreatedAt() != null ? kds.getCreatedAt().toLocalDateTime() : null)
                .createdBy(kds.getCreatedBy().getFirstName())
                .translations(translationDTOs)
                .categories(allCreatedAssignedCategories) // Includes both categories and combos
                .subCategories(createdAssignedSubCategories)
                .combos(assignedCombos) // Keep for backward compatibility
                .build();

        KdsDto<KdsResponse> kdsDto = KdsDto.<KdsResponse>builder()
                .kds(response)
                .build();

        // Create audit trail for KDS creation
        try {
            Restaurant restaurant = null;
            if (user.getRestaurantId() != null) {
                restaurant = restaurantRepository.findById(user.getRestaurantId()).orElse(null);
            }
            String kdsName = translationDTOs.isEmpty() ? 
                DEFAULT_NO_TRANSLATIONS : translationDTOs.get(0).getName();
            auditTrailService.createAuditTrail(
                    user,
                    ActionType.KDS_CREATE,
                    restaurant,
                    null, // status - will default to NA for non-request actions
                    null, // ipAddress - not available in this context
                    null, // userAgent - not available in this context
                    kds.getId(),
                    "KDS",
                    "KDS created: " + kdsName
            );
        } catch (Exception e) {
            log.error("Failed to create audit trail for KDS creation: {}", e.getMessage());
            // Don't break KDS creation flow if audit trail fails
        }

        return ResponseDto.<KdsDto<KdsResponse>>builder()
                .message(messageUtil.getMessage("kds.create.success", userLocale))
                .data(kdsDto)
                .build();
    }

    @Override
    @Transactional
    public ResponseDto<KdsDto<KdsResponse>> updateKds(UUID kdsId, KdsRequest request, String userId, String locale) {
        Locale userLocale = Locale.forLanguageTag(locale);

        User user = userRepository.findById(UUID.fromString(userId))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        messageUtil.getMessage(MSG_USER_NOT_FOUND, userLocale, userId)));

        Kds kds = kdsRepository.findById(kdsId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        messageUtil.getMessage(MSG_KDS_DELETE_ERROR_NOT_FOUND, userLocale)));

        if (kds.getIsDeleted()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    messageUtil.getMessage("kds.update.error.deleted", userLocale));
        }

        // Check if user is trying to set KDS as default and if a default already exists
        if (Boolean.TRUE.equals(request.getIsDefault()) && kds.getRestaurantId() != null) {
            // If this KDS is being set as default, unset any other default KDS for this restaurant
            List<Kds> defaultKdss = kdsRepository.findByRestaurantIdAndIsDefaultTrueAndIsDeletedFalse(kds.getRestaurantId());
            for (Kds defaultKds : defaultKdss) {
                if (!defaultKds.getId().equals(kdsId)) {
                    defaultKds.setIsDefault(false);
                    kdsRepository.save(defaultKds);
                }
            }
        }

        List<KdsTranslationDto> translations = request.getTranslations();
        if (translations != null && !translations.isEmpty()) {
            // Validate that at least one translation has a non-empty name
            boolean hasValidName = translations.stream()
                    .anyMatch(t -> t.getName() != null && !t.getName().trim().isEmpty());

            if (!hasValidName) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        messageUtil.getMessage("kds.update.error.no.valid.name", userLocale));
            }

            // Check for duplicate language codes and validate language codes
            Set<String> languageCodes = new HashSet<>();
            for (KdsTranslationDto entry : translations) {
                String name = entry.getName();
                String lang = entry.getLanguageCode();

                // Only validate non-empty names
                if (name != null && !name.trim().isEmpty() && lang != null) {
                    if (!localizationProperties.getLanguages().contains(lang)) {
                        throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                                messageUtil.getMessage(MSG_KDS_ERROR_INVALID_LANGUAGE, userLocale));
                    }
                    if (!languageCodes.add(lang)) {
                        throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                                messageUtil.getMessage("kds.error.duplicate.language", userLocale, lang));
                    }

                    // Check for existing names in the same language at restaurant level (excluding current KDS)
                    if (kds.getRestaurantId() != null) {
                        Optional<KdsTranslation> existingTranslation = kdsTranslationRepository
                                .findByKdsIdAndLanguageCode(kdsId, lang);
                        
                        // Check if name is being changed or if it's a new translation
                        boolean nameChanged = existingTranslation
                                .map(t -> !t.getName().equalsIgnoreCase(name.trim()))
                                .orElse(true);
                        
                        if (nameChanged) {
                            // Check if new name conflicts with any other KDS in the same restaurant
                            boolean conflictExists = kdsTranslationRepository
                                    .existsByNameAndLanguageCodeAndRestaurantIdAndKdsIdNot(
                                            name.trim(), lang, kds.getRestaurantId(), kdsId);
                            if (conflictExists) {
                                throw new ResponseStatusException(HttpStatus.CONFLICT,
                                        messageUtil.getMessage("kds.update.error.name.exists", userLocale));
                            }
                        }
                    }
                }
            }
        } else {
            // No translations provided at all
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    messageUtil.getMessage("kds.update.error.no.translations", userLocale));
        }

        // Update KDS basic details
        kds.setStatus(request.getStatus());
        kds.setIsDeleted(Boolean.TRUE.equals(request.getIsDeleted()));
        kds.setIsDefault(Boolean.TRUE.equals(request.getIsDefault()));
        kds.setUpdatedAt(OffsetDateTime.now(ZoneOffset.UTC));
        kds.setUpdatedBy(user);
        kds = kdsRepository.save(kds);

        // Update translations
        if (translations != null && !translations.isEmpty()) {
            List<KdsTranslation> existingTranslations = kdsTranslationRepository.findAllByKdsId(kds.getId());
            Map<String, KdsTranslation> existingTranslationMap = new HashMap<>();
            for (KdsTranslation translation : existingTranslations) {
                existingTranslationMap.put(translation.getLanguageCode(), translation);
            }

            // Get language codes from request with non-empty names
            Set<String> validRequestLanguageCodes = translations.stream()
                    .filter(t -> t.getLanguageCode() != null && 
                               t.getName() != null && 
                               !t.getName().trim().isEmpty())
                    .map(KdsTranslationDto::getLanguageCode)
                    .collect(Collectors.toSet());

            // Remove translations that are not in the request or have empty names
            List<KdsTranslation> translationsToRemove = new ArrayList<>();
            for (KdsTranslation existingTranslation : existingTranslations) {
                if (!validRequestLanguageCodes.contains(existingTranslation.getLanguageCode())) {
                    translationsToRemove.add(existingTranslation);
                }
            }

            // Delete translations that are not in the request or have empty names
            for (KdsTranslation translationToRemove : translationsToRemove) {
                kdsTranslationRepository.delete(translationToRemove);
            }

            // Update existing and add new translations (only for non-empty names)
            for (KdsTranslationDto entry : translations) {
                String name = entry.getName();
                if (name != null && !name.trim().isEmpty() && entry.getLanguageCode() != null) {
                    KdsTranslation translation = existingTranslationMap.get(entry.getLanguageCode());
                    if (translation != null) {
                        // Update existing translation
                        translation.setName(name.trim());
                        kdsTranslationRepository.save(translation);
                    } else {
                        // Create new translation
                        translation = new KdsTranslation();
                        translation.setName(name.trim());
                        translation.setKds(kds);
                        translation.setLanguageCode(entry.getLanguageCode());
                        kdsTranslationRepository.save(translation);
                    }
                }
            }
        }

        // Fetch saved translations
        List<KdsTranslation> savedTranslations = kdsTranslationRepository.findAllByKdsId(kds.getId());
        List<KdsTranslationDto> translationDTOs = savedTranslations.stream()
                .map(t -> KdsTranslationDto.builder()
                        .languageCode(t.getLanguageCode())
                        .name(t.getName())
                        .build())
                .collect(Collectors.toList());

        // Collect combo IDs that were mistakenly sent in categoryIds
        // Since CategoryListData uses id field for both categories and combos,
        // frontend may send combo IDs in categoryIds field
        // Declared at method scope so it can be used in both category and combo sync sections
        Set<UUID> comboIdsFromCategoryIds = new HashSet<>();

        // Sync categories if provided in request (assign new ones and unassign removed ones)
        // Support both categoryIds (master category IDs) and menuCategoryMappingIds
        // If either field is provided (even if empty array), sync categories
        // Empty array means clear all categories, null means don't change categories
        if (request.getCategoryIds() != null || request.getMenuCategoryMappingIds() != null) {
            UUID restaurantId = user.getRestaurantId();
            
            if (restaurantId == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        messageUtil.getMessage(MSG_USER_RESTAURANT_NOT_ASSIGNED, userLocale));
            }

            if (!restaurantId.equals(kds.getRestaurantId())) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                        messageUtil.getMessage(msgKdsRestaurantMismatch, userLocale));
            }

            // Get all menus for the restaurant
            List<RestaurantMenuMapping> restaurantMenuMappings = restaurantMenuMappingRepository
                    .findById_RestaurantId(restaurantId);
            if (restaurantMenuMappings == null || restaurantMenuMappings.isEmpty()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        messageUtil.getMessage(MSG_RESTAURANT_NO_MENUS, userLocale));
            }

            Set<UUID> restaurantMenuIds = restaurantMenuMappings.stream()
                    .map(rmm -> rmm.getMenu().getId())
                    .collect(Collectors.toSet());

            // Build set of requested menu category mapping IDs
            Set<UUID> requestedMenuCategoryMappingIdsSet = new HashSet<>();

            // Convert categoryIds to menuCategoryMappingIds if provided
            if (request.getCategoryIds() != null && !request.getCategoryIds().isEmpty()) {
                Set<UUID> uniqueCategoryIds = new HashSet<>(request.getCategoryIds());
                
                for (UUID categoryId : uniqueCategoryIds) {
                    // Check if this ID is actually a combo ID (CategoryListData confusion)
                    // Since combos are treated as categories for KDS, we need to differentiate
                    if (comboRepository.existsById(categoryId)) {
                        // This is a combo ID, not a category ID - collect it to process as combo
                        comboIdsFromCategoryIds.add(categoryId);
                        continue;
                    }
                    
                    // Validate category exists and is not deleted
                    Category category = categoryRepository.findByIdAndIsDeletedFalse(categoryId)
                            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                                    messageUtil.getMessage(MSG_CATEGORY_NOT_FOUND, userLocale, categoryId)));

                    // Find menu category mappings for this category in restaurant's menus
                    List<MenuCategoryMapping> mappings = menuCategoryMappingRepository.findByCategory_IdIn(
                            Collections.singletonList(categoryId));
                    
                    // Filter to only include mappings from restaurant's menus with ACTIVE status
                    List<MenuCategoryMapping> restaurantMappings = mappings.stream()
                            .filter(mcm -> mcm.getMenu() != null && 
                                          restaurantMenuIds.contains(mcm.getMenu().getId()) &&
                                          EntityStatus.ACTIVE.equals(mcm.getStatus()))
                            .collect(Collectors.toList());

                    if (restaurantMappings.isEmpty()) {
                        throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                                messageUtil.getMessage("category.not.assigned.to.menu", userLocale, categoryId));
                    }

                    // Add all mappings for this category
                    for (MenuCategoryMapping mcm : restaurantMappings) {
                        requestedMenuCategoryMappingIdsSet.add(mcm.getId());
                    }
                }
            }

            // Add menuCategoryMappingIds if provided
            if (request.getMenuCategoryMappingIds() != null && !request.getMenuCategoryMappingIds().isEmpty()) {
                // Remove duplicates from request
                Set<UUID> duplicateMappingIds = new HashSet<>();
                for (UUID menuCategoryMappingId : request.getMenuCategoryMappingIds()) {
                    if (!duplicateMappingIds.add(menuCategoryMappingId)) {
                        throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                                messageUtil.getMessage("category.duplicate.in.request", userLocale, menuCategoryMappingId));
                    }
                    requestedMenuCategoryMappingIdsSet.add(menuCategoryMappingId);
                }
            }

            // Get currently assigned menu category mappings
            List<CategoryKds> currentAssignments = categoryKdsRepository.findByKdsId(kds.getId());
            Set<UUID> currentMenuCategoryMappingIds = currentAssignments.stream()
                    .map(ck -> ck.getMenuCategoryMapping() != null ? ck.getMenuCategoryMapping().getId() : null)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toSet());

            // Find menu category mappings to assign (in request but not currently assigned)
            Set<UUID> menuCategoryMappingsToAssign = new HashSet<>(requestedMenuCategoryMappingIdsSet);
            menuCategoryMappingsToAssign.removeAll(currentMenuCategoryMappingIds);

            // Find menu category mappings to unassign (currently assigned but not in request)
            Set<UUID> menuCategoryMappingsToUnassign = new HashSet<>(currentMenuCategoryMappingIds);
            menuCategoryMappingsToUnassign.removeAll(requestedMenuCategoryMappingIdsSet);

            // Assign new menu category mappings
            if (!menuCategoryMappingsToAssign.isEmpty()) {
                List<MenuCategoryMapping> menuCategoryMappingsToAssignList = new ArrayList<>();
                
                for (UUID menuCategoryMappingId : menuCategoryMappingsToAssign) {
                    // Fetch MenuCategoryMapping
                    MenuCategoryMapping menuCategoryMapping = menuCategoryMappingRepository.findById(menuCategoryMappingId)
                            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                                    messageUtil.getMessage("menu.category.mapping.not.found", userLocale, menuCategoryMappingId)));

                    // Validate MenuCategoryMapping belongs to one of the restaurant's menus
                    if (menuCategoryMapping.getMenu() == null || 
                        !restaurantMenuIds.contains(menuCategoryMapping.getMenu().getId())) {
                        throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                                messageUtil.getMessage("menu.category.mapping.not.in.restaurant.menu", userLocale, menuCategoryMappingId));
                    }

                    // Validate category is not deleted
                    Category category = menuCategoryMapping.getCategory();
                    if (category == null) {
                        throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                                messageUtil.getMessage(MSG_CATEGORY_NOT_FOUND, userLocale, menuCategoryMappingId));
                    }

                    if (Boolean.TRUE.equals(category.getIsDeleted())) {
                        throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                                messageUtil.getMessage("category.deleted", userLocale, category.getId()));
                    }

                    // Check if this MenuCategoryMapping is already assigned to this KDS
                    if (categoryKdsRepository.existsByMenuCategoryMappingIdAndKdsId(menuCategoryMappingId, kds.getId())) {
                        throw new ResponseStatusException(HttpStatus.CONFLICT,
                                messageUtil.getMessage("category.already.assigned.to.kds", userLocale, category.getId(), kds.getId()));
                    }

                    menuCategoryMappingsToAssignList.add(menuCategoryMapping);
                }

                // Create CategoryKds mappings for new assignments
                for (MenuCategoryMapping menuCategoryMapping : menuCategoryMappingsToAssignList) {
                    CategoryKds categoryKds = CategoryKds.builder()
                            .menuCategoryMapping(menuCategoryMapping)
                            .kds(kds)
                            .build();
                    categoryKdsRepository.save(categoryKds);
                }
            }

            // Unassign removed menu category mappings
            if (!menuCategoryMappingsToUnassign.isEmpty()) {
                List<CategoryKds> categoryKdsToDelete = currentAssignments.stream()
                        .filter(ck -> ck.getMenuCategoryMapping() != null && 
                                     menuCategoryMappingsToUnassign.contains(ck.getMenuCategoryMapping().getId()))
                        .collect(Collectors.toList());
                
                categoryKdsRepository.deleteAll(categoryKdsToDelete);
            }
        }

        // Sync combos if provided in request (assign new ones and unassign removed ones)
        // Also include combo IDs that were mistakenly sent in categoryIds
        Set<UUID> allRequestedComboIds = new HashSet<>();
        if (request.getComboIds() != null && !request.getComboIds().isEmpty()) {
            allRequestedComboIds.addAll(request.getComboIds());
        }
        // Add combo IDs that were found in categoryIds field
        allRequestedComboIds.addAll(comboIdsFromCategoryIds);
        
        if (request.getComboIds() != null || !comboIdsFromCategoryIds.isEmpty()) {
            // Get currently assigned combos
            List<ComboKds> currentComboAssignments = comboKdsRepository.findByKdsId(kds.getId());
            Set<UUID> currentComboIds = currentComboAssignments.stream()
                    .map(ck -> ck.getCombo() != null ? ck.getCombo().getComboId() : null)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toSet());

            // Remove duplicates from request
            Set<UUID> requestedComboIdsSet = new HashSet<>(allRequestedComboIds);
            if (allRequestedComboIds.size() != requestedComboIdsSet.size()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        messageUtil.getMessage("combo.duplicate.in.request", userLocale));
            }

            // Find combos to assign (in request but not currently assigned)
            Set<UUID> combosToAssign = new HashSet<>(requestedComboIdsSet);
            combosToAssign.removeAll(currentComboIds);

            // Find combos to unassign (currently assigned but not in request)
            Set<UUID> combosToUnassign = new HashSet<>(currentComboIds);
            combosToUnassign.removeAll(requestedComboIdsSet);

            // Get all menus for the restaurant (needed for validation)
            UUID restaurantId = user.getRestaurantId();
            if (restaurantId == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        messageUtil.getMessage(MSG_USER_RESTAURANT_NOT_ASSIGNED, userLocale));
            }
            
            List<RestaurantMenuMapping> restaurantMenuMappings = restaurantMenuMappingRepository
                    .findById_RestaurantId(restaurantId);
            if (restaurantMenuMappings == null || restaurantMenuMappings.isEmpty()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        messageUtil.getMessage(MSG_RESTAURANT_NO_MENUS, userLocale));
            }

            Set<UUID> restaurantMenuIds = restaurantMenuMappings.stream()
                    .map(rmm -> rmm.getMenu().getId())
                    .collect(Collectors.toSet());

            // Assign new combos
            if (!combosToAssign.isEmpty()) {
                for (UUID comboId : combosToAssign) {
                    // Validate combo exists and is not deleted
                    Combo combo = comboRepository.findById(comboId)
                            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                                    messageUtil.getMessage("combo.not.found", userLocale, comboId)));

                    if (Boolean.TRUE.equals(combo.getIsDeleted())) {
                        throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                                messageUtil.getMessage("combo.deleted", userLocale, comboId));
                    }

                    // Validate combo belongs to one of the restaurant's menus
                    if (combo.getMenu() == null || !restaurantMenuIds.contains(combo.getMenu().getId())) {
                        throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                                messageUtil.getMessage("combo.not.assigned.to.restaurant.menu", userLocale, comboId));
                    }

                    // Check if this combo is already assigned to this KDS
                    if (comboKdsRepository.existsByComboIdAndKdsId(comboId, kds.getId())) {
                        throw new ResponseStatusException(HttpStatus.CONFLICT,
                                messageUtil.getMessage("combo.already.assigned.to.kds", userLocale, comboId, kds.getId()));
                    }

                    // Create ComboKds mapping
                    ComboKds comboKds = ComboKds.builder()
                            .combo(combo)
                            .kds(kds)
                            .build();
                    comboKdsRepository.save(comboKds);
                }
            }

            // Unassign removed combos
            if (!combosToUnassign.isEmpty()) {
                List<ComboKds> comboKdsToDelete = currentComboAssignments.stream()
                        .filter(ck -> ck.getCombo() != null && 
                                     combosToUnassign.contains(ck.getCombo().getComboId()))
                        .collect(Collectors.toList());
                
                comboKdsRepository.deleteAll(comboKdsToDelete);
            }
        }

        // Populate assigned categories for response
        List<CategoryKds> updatedCategoryKdsMappings = categoryKdsRepository.findByKdsId(kds.getId());
        List<CategoryListData> updatedAssignedRootCategories = new ArrayList<>();
        List<CategoryResponse.CategoryData> updatedAssignedSubCategories = new ArrayList<>();

        for (CategoryKds ck : updatedCategoryKdsMappings) {
            MenuCategoryMapping menuCategoryMapping = ck.getMenuCategoryMapping();
            if (menuCategoryMapping == null || menuCategoryMapping.getCategory() == null) {
                continue;
            }

            Category category = menuCategoryMapping.getCategory();

            String categoryName = categoryTranslationRepository
                    .findByCategoryIdAndLanguageCode(category.getId(), locale)
                    .map(CategoryTranslation::getName)
                    .orElse(null);

            if (category.getParentCategory() == null) {
                CategoryListData data = CategoryListData.builder()
                        .id(category.getId())
                        .status(category.getStatus())
                        .name(categoryName)
                        .menuCategoryMappingId(menuCategoryMapping.getId())
                        .createdBy(category.getCreatedBy() != null ? category.getCreatedBy().getFirstName() : null)
                        .createdAt(category.getCreatedAt() != null ? category.getCreatedAt().toLocalDateTime() : null)
                        .updatedBy(category.getUpdatedBy() != null ? category.getUpdatedBy().getFirstName() : null)
                        .updatedAt(category.getUpdatedAt() != null ? category.getUpdatedAt().toLocalDateTime() : null)
                        .displayOrder(category.getDisplayOrder())
                        .build();
                updatedAssignedRootCategories.add(data);
            } else {
                Category parent = category.getParentCategory();
                String parentName = parent != null ? categoryTranslationRepository
                        .findByCategoryIdAndLanguageCode(parent.getId(), locale)
                        .map(CategoryTranslation::getName)
                        .orElse(null) : null;

                CategoryResponse.CategoryData sub = CategoryResponse.CategoryData.builder()
                        .id(category.getId())
                        .parentCategoryId(parent != null ? parent.getId() : null)
                        .parentCategoryName(parentName)
                        .status(category.getStatus())
                        .name(categoryName)
                        .menuCategoryMappingId(menuCategoryMapping.getId())
                        .createdBy(category.getCreatedBy() != null ? category.getCreatedBy().getFirstName() : null)
                        .createdAt(category.getCreatedAt() != null ? category.getCreatedAt().toLocalDateTime() : null)
                        .updatedBy(category.getUpdatedBy() != null ? category.getUpdatedBy().getFirstName() : null)
                        .updatedAt(category.getUpdatedAt() != null ? category.getUpdatedAt().toLocalDateTime() : null)
                        .displayOrder(category.getDisplayOrder())
                        .build();
                updatedAssignedSubCategories.add(sub);
            }
        }

        // Populate assigned combos for response
        List<ComboKds> updatedComboKdsMappings = comboKdsRepository.findByKdsId(kds.getId());
        List<ComboResponse> updatedAssignedCombos = new ArrayList<>();
        List<CategoryListData> updatedAssignedComboCategories = new ArrayList<>();
        
        for (ComboKds ck : updatedComboKdsMappings) {
            Combo combo = ck.getCombo();
            if (combo == null) {
                continue;
            }

            // Get combo translations
            List<ComboTranslation> comboTranslations = comboTranslationRepository.findByComboComboId(combo.getComboId());
            List<ComboTranslationDto> comboTranslationDtos = comboTranslations.stream()
                    .map(t -> ComboTranslationDto.builder()
                            .languageCode(t.getLanguageCode())
                            .name(t.getName())
                            .description(t.getDescription())
                            .build())
                    .collect(Collectors.toList());

            // Get combo name in the requested locale
            String comboName = comboTranslations.stream()
                    .filter(t -> t.getLanguageCode() != null && t.getLanguageCode().equals(locale))
                    .map(ComboTranslation::getName)
                    .findFirst()
                    .orElse(null);

            // Add combo as CategoryListData so it appears in the categories list (for consistency with selection API)
            CategoryListData comboAsCategory = CategoryListData.builder()
                    .id(combo.getComboId()) // Use combo ID as the ID
                    .comboId(combo.getComboId()) // Mark this as a combo
                    .status(combo.getStatus())
                    .name(comboName)
                    .displayOrder(null) // Combos don't have displayOrder
                    .createdAt(combo.getCreatedAt() != null ? combo.getCreatedAt().toLocalDateTime() : null)
                    .createdBy(combo.getCreatedBy() != null ? combo.getCreatedBy().getFirstName() : null)
                    .updatedAt(combo.getUpdatedAt() != null ? combo.getUpdatedAt().toLocalDateTime() : null)
                    .updatedBy(combo.getUpdatedBy() != null ? combo.getUpdatedBy().getFirstName() : null)
                    .menuCategoryMappingId(null) // Combos don't have menu category mapping IDs
                    .build();
            updatedAssignedComboCategories.add(comboAsCategory);

            // Also keep the ComboResponse for backward compatibility
            ComboResponse comboResponse = ComboResponse.builder()
                    .comboId(combo.getComboId())
                    .menuId(combo.getMenu() != null ? combo.getMenu().getId() : null)
                    .type(combo.getType())
                    .basePrice(combo.getBasePrice())
                    .comboImageUrl(awsService.getFullUrl(combo.getComboImageUrl()))
                    .status(combo.getStatus())
                    .validFrom(combo.getValidFrom())
                    .validTo(combo.getValidTo())
                    .startTime(combo.getStartTime())
                    .endTime(combo.getEndTime())
                    .daysOfWeek(combo.getDaysOfWeek())
                    .createdAt(combo.getCreatedAt() != null ? combo.getCreatedAt().toLocalDateTime() : null)
                    .createdBy(combo.getCreatedBy() != null ? combo.getCreatedBy().getFirstName() : null)
                    .updatedAt(combo.getUpdatedAt() != null ? combo.getUpdatedAt().toLocalDateTime() : null)
                    .updatedBy(combo.getUpdatedBy() != null ? combo.getUpdatedBy().getFirstName() : null)
                    .translations(comboTranslationDtos)
                    .build();
            updatedAssignedCombos.add(comboResponse);
        }

        // Combine categories and combos into a single list for consistency with selection API
        List<CategoryListData> allUpdatedAssignedCategories = new ArrayList<>(updatedAssignedRootCategories);
        allUpdatedAssignedCategories.addAll(updatedAssignedComboCategories);

        // Check if device is linked (deviceCode is not null and not empty)
        Boolean isDeviceLinked = kds.getDeviceCode() != null && !kds.getDeviceCode().trim().isEmpty();
        
        // Build response
        KdsResponse response = KdsResponse.builder()
                .id(kds.getId())
                .status(kds.getStatus())
                .isDeleted(kds.getIsDeleted())
                .isDefault(kds.getIsDefault())
                .deviceCode(kds.getDeviceCode())
                .isDeviceLinked(isDeviceLinked)
                .createdAt(kds.getCreatedAt() != null ? kds.getCreatedAt().toLocalDateTime() : null)
                .createdBy(kds.getCreatedBy().getFirstName())
                .updatedAt(kds.getUpdatedAt() != null ? kds.getUpdatedAt().toLocalDateTime() : null)
                .updatedBy(user.getFirstName())
                .translations(translationDTOs)
                .categories(allUpdatedAssignedCategories) // Includes both categories and combos
                .subCategories(updatedAssignedSubCategories)
                .combos(updatedAssignedCombos) // Keep for backward compatibility
                .build();

        KdsDto<KdsResponse> kdsDto = KdsDto.<KdsResponse>builder()
                .kds(response)
                .build();

        // Create audit trail for KDS update
        try {
            Restaurant restaurant = null;
            if (user.getRestaurantId() != null) {
                restaurant = restaurantRepository.findById(user.getRestaurantId()).orElse(null);
            }
            String kdsName = kds.getTranslations().isEmpty() ? 
                DEFAULT_NO_TRANSLATIONS : kds.getTranslations().get(0).getName();
            auditTrailService.createAuditTrail(
                    user,
                    ActionType.KDS_UPDATE,
                    restaurant,
                    null, // status - will default to NA for non-request actions
                    null, // ipAddress - not available in this context
                    null, // userAgent - not available in this context
                    kds.getId(),
                    "KDS",
                    "KDS updated: " + kdsName
            );
        } catch (Exception e) {
            log.error("Failed to create audit trail for KDS update: {}", e.getMessage());
            // Don't break KDS update flow if audit trail fails
        }

        return ResponseDto.<KdsDto<KdsResponse>>builder()
                .message(messageUtil.getMessage("kds.update.success", userLocale))
                .data(kdsDto)
                .build();
    }

    /**
     * Retrieves a KDS by id (including its translations and assigned categories/combos).
     *
     * @param kdsId  KDS identifier
     * @param locale locale tag used for validation and localization
     * @return response wrapper containing the KDS DTO
     * @throws ResponseStatusException if locale is invalid, the KDS is not found, or the KDS is deleted
     */
    @Override
    @Transactional(readOnly = true)
    public ResponseDto<KdsDto<KdsResponse>> getKdsById(UUID kdsId, String locale) {
        Locale userLocale = Locale.forLanguageTag(locale);

        // Validate locale
        if (!localizationProperties.getLanguages().contains(locale)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    messageUtil.getMessage(MSG_KDS_ERROR_INVALID_LANGUAGE, userLocale));
        }

        // Find KDS by ID
        Kds kds = kdsRepository.findById(kdsId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        messageUtil.getMessage(MSG_KDS_GET_ERROR_NOT_FOUND, userLocale)));

        if (Boolean.TRUE.equals(kds.getIsDeleted())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    messageUtil.getMessage(MSG_KDS_GET_ERROR_DELETED, userLocale));
        }

        // Get all translations for the KDS
        List<KdsTranslation> translations = kdsTranslationRepository.findAllByKdsId(kds.getId());

        // Map all translations to DTOs
        List<KdsTranslationDto> translationDTOs = translations.stream()
                .map(t -> KdsTranslationDto.builder()
                        .languageCode(t.getLanguageCode())
                        .name(t.getName())
                        .build())
                .collect(Collectors.toList());

        // Get creator and updater information
        User createdByUser = kds.getCreatedBy();
        User updatedByUser = kds.getUpdatedBy();

        String createdByName = createdByUser != null ? createdByUser.getFirstName() : null;
        String updatedByName = updatedByUser != null ? updatedByUser.getFirstName() : null;

        // Get assigned categories for this KDS
        List<CategoryKds> categoryKdsMappings = categoryKdsRepository.findByKdsId(kds.getId());
        List<CategoryListData> assignedRootCategories = new ArrayList<>();
        List<CategoryResponse.CategoryData> assignedSubCategories = new ArrayList<>();
        
        if (categoryKdsMappings != null && !categoryKdsMappings.isEmpty()) {
            for (CategoryKds ck : categoryKdsMappings) {
                MenuCategoryMapping menuCategoryMapping = ck.getMenuCategoryMapping();
                if (menuCategoryMapping == null || menuCategoryMapping.getCategory() == null) {
                    continue;
                }

                Category category = menuCategoryMapping.getCategory();

                String categoryName = categoryTranslationRepository
                        .findByCategoryIdAndLanguageCode(category.getId(), locale)
                        .map(CategoryTranslation::getName)
                        .orElse(null);

                if (category.getParentCategory() == null) {
                    CategoryListData data = CategoryListData.builder()
                            .id(category.getId())
                            .status(category.getStatus())
                            .name(categoryName)
                            .menuCategoryMappingId(menuCategoryMapping.getId())
                            .createdBy(category.getCreatedBy() != null ? category.getCreatedBy().getFirstName() : null)
                            .createdAt(category.getCreatedAt() != null ? category.getCreatedAt().toLocalDateTime() : null)
                            .updatedBy(category.getUpdatedBy() != null ? category.getUpdatedBy().getFirstName() : null)
                            .updatedAt(category.getUpdatedAt() != null ? category.getUpdatedAt().toLocalDateTime() : null)
                            .displayOrder(category.getDisplayOrder())
                            .build();
                    assignedRootCategories.add(data);
                } else {
                    Category parent = category.getParentCategory();
                    String parentName = parent != null ? categoryTranslationRepository
                            .findByCategoryIdAndLanguageCode(parent.getId(), locale)
                            .map(CategoryTranslation::getName)
                            .orElse(null) : null;

                    CategoryResponse.CategoryData sub = CategoryResponse.CategoryData.builder()
                            .id(category.getId())
                            .parentCategoryId(parent != null ? parent.getId() : null)
                            .parentCategoryName(parentName)
                            .status(category.getStatus())
                            .name(categoryName)
                            .menuCategoryMappingId(menuCategoryMapping.getId())
                            .createdBy(category.getCreatedBy() != null ? category.getCreatedBy().getFirstName() : null)
                            .createdAt(category.getCreatedAt() != null ? category.getCreatedAt().toLocalDateTime() : null)
                            .updatedBy(category.getUpdatedBy() != null ? category.getUpdatedBy().getFirstName() : null)
                            .updatedAt(category.getUpdatedAt() != null ? category.getUpdatedAt().toLocalDateTime() : null)
                            .displayOrder(category.getDisplayOrder())
                            .build();
                    assignedSubCategories.add(sub);
                }
            }
        }

        // Populate assigned combos for response
        List<ComboKds> comboKdsMappings = comboKdsRepository.findByKdsId(kds.getId());
        List<ComboResponse> assignedCombos = new ArrayList<>();
        List<CategoryListData> assignedComboCategories = new ArrayList<>();
        
        for (ComboKds ck : comboKdsMappings) {
            Combo combo = ck.getCombo();
            if (combo == null) {
                continue;
            }

            // Get combo translations
            List<ComboTranslation> comboTranslations = comboTranslationRepository.findByComboComboId(combo.getComboId());
            List<ComboTranslationDto> comboTranslationDtos = comboTranslations.stream()
                    .map(t -> ComboTranslationDto.builder()
                            .languageCode(t.getLanguageCode())
                            .name(t.getName())
                            .description(t.getDescription())
                            .build())
                    .collect(Collectors.toList());

            // Get combo name in the requested locale
            String comboName = comboTranslations.stream()
                    .filter(t -> t.getLanguageCode() != null && t.getLanguageCode().equals(locale))
                    .map(ComboTranslation::getName)
                    .findFirst()
                    .orElse(null);

            // Add combo as CategoryListData so it appears in the categories list (for consistency with selection API)
            CategoryListData comboAsCategory = CategoryListData.builder()
                    .id(combo.getComboId()) // Use combo ID as the ID
                    .comboId(combo.getComboId()) // Mark this as a combo
                    .status(combo.getStatus())
                    .name(comboName)
                    .displayOrder(null) // Combos don't have displayOrder
                    .createdAt(combo.getCreatedAt() != null ? combo.getCreatedAt().toLocalDateTime() : null)
                    .createdBy(combo.getCreatedBy() != null ? combo.getCreatedBy().getFirstName() : null)
                    .updatedAt(combo.getUpdatedAt() != null ? combo.getUpdatedAt().toLocalDateTime() : null)
                    .updatedBy(combo.getUpdatedBy() != null ? combo.getUpdatedBy().getFirstName() : null)
                    .menuCategoryMappingId(null) // Combos don't have menu category mapping IDs
                    .build();
            assignedComboCategories.add(comboAsCategory);

            // Also keep the ComboResponse for backward compatibility
            ComboResponse comboResponse = ComboResponse.builder()
                    .comboId(combo.getComboId())
                    .menuId(combo.getMenu() != null ? combo.getMenu().getId() : null)
                    .type(combo.getType())
                    .basePrice(combo.getBasePrice())
                    .comboImageUrl(awsService.getFullUrl(combo.getComboImageUrl()))
                    .status(combo.getStatus())
                    .validFrom(combo.getValidFrom())
                    .validTo(combo.getValidTo())
                    .startTime(combo.getStartTime())
                    .endTime(combo.getEndTime())
                    .daysOfWeek(combo.getDaysOfWeek())
                    .createdAt(combo.getCreatedAt() != null ? combo.getCreatedAt().toLocalDateTime() : null)
                    .createdBy(combo.getCreatedBy() != null ? combo.getCreatedBy().getFirstName() : null)
                    .updatedAt(combo.getUpdatedAt() != null ? combo.getUpdatedAt().toLocalDateTime() : null)
                    .updatedBy(combo.getUpdatedBy() != null ? combo.getUpdatedBy().getFirstName() : null)
                    .translations(comboTranslationDtos)
                    .build();
            assignedCombos.add(comboResponse);
        }

        // Combine categories and combos into a single list for consistency with selection API
        List<CategoryListData> allAssignedCategories = new ArrayList<>(assignedRootCategories);
        allAssignedCategories.addAll(assignedComboCategories);

        // Check if device is linked (deviceCode is not null and not empty)
        Boolean isDeviceLinked = kds.getDeviceCode() != null && !kds.getDeviceCode().trim().isEmpty();
        
        // Build response
        KdsResponse response = KdsResponse.builder()
                .id(kds.getId())
                .status(kds.getStatus())
                .isDeleted(kds.getIsDeleted())
                .isDefault(kds.getIsDefault())
                .deviceCode(kds.getDeviceCode())
                .isDeviceLinked(isDeviceLinked)
                .createdAt(kds.getCreatedAt() != null ? kds.getCreatedAt().toLocalDateTime() : null)
                .createdBy(createdByName)
                .updatedAt(kds.getUpdatedAt() != null ? kds.getUpdatedAt().toLocalDateTime() : null)
                .updatedBy(updatedByName)
                .translations(translationDTOs)
                .categories(allAssignedCategories) // Includes both categories and combos
                .subCategories(assignedSubCategories)
                .combos(assignedCombos) // Keep for backward compatibility
                .build();

        KdsDto<KdsResponse> kdsDto = KdsDto.<KdsResponse>builder()
                .kds(response)
                .build();

        return ResponseDto.<KdsDto<KdsResponse>>builder()
                .message(messageUtil.getMessage("kds.get.success", userLocale))
                .data(kdsDto)
                .build();
    }

    /**
     * Soft-deletes a KDS by marking it deleted and updating audit fields.
     * <p>
     * Validates locale, loads the acting user, ensures the KDS exists and is not already deleted,
     * updates {@code isDeleted/updatedAt/updatedBy}, persists the change, and records an audit trail entry.
     * </p>
     *
     * @param kdsId  KDS identifier
     * @param userId acting user id (string UUID)
     * @param locale locale tag used for validation and messages
     * @return response wrapper containing a confirmation message and simple data string
     * @throws ResponseStatusException if locale is invalid, entities are not found, or KDS is already deleted
     */
    @Override
    @Transactional
    public ResponseDto<String> deleteKds(UUID kdsId, String userId, String locale) {
        Locale userLocale = Locale.forLanguageTag(locale);

        // Validate locale
        if (!localizationProperties.getLanguages().contains(locale)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    messageUtil.getMessage(MSG_KDS_ERROR_INVALID_LANGUAGE, userLocale));
        }

        // Find user
        User user = userRepository.findById(UUID.fromString(userId))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        messageUtil.getMessage(MSG_USER_NOT_FOUND, userLocale, userId)));

        // Find KDS by ID
        Kds kds = kdsRepository.findById(kdsId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        messageUtil.getMessage(MSG_KDS_DELETE_ERROR_NOT_FOUND, userLocale)));

        // Check if KDS is already deleted
        if (Boolean.TRUE.equals(kds.getIsDeleted())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    messageUtil.getMessage("kds.delete.error.already_deleted", userLocale));
        }

        // Perform soft delete
        kds.setIsDeleted(true);
        kds.setUpdatedAt(OffsetDateTime.now(ZoneOffset.UTC));
        kds.setUpdatedBy(user);
        kdsRepository.save(kds);

        // Create audit trail for KDS deletion
        try {
            Restaurant restaurant = null;
            if (user.getRestaurantId() != null) {
                restaurant = restaurantRepository.findById(user.getRestaurantId()).orElse(null);
            }
            String kdsName = kds.getTranslations().isEmpty() ? 
                DEFAULT_NO_TRANSLATIONS : kds.getTranslations().get(0).getName();
            auditTrailService.createAuditTrail(
                    user,
                    ActionType.KDS_DELETE,
                    restaurant,
                    null, // status - will default to NA for non-request actions
                    null, // ipAddress - not available in this context
                    null, // userAgent - not available in this context
                    kds.getId(),
                    "KDS",
                    "KDS deleted: " + kdsName
            );
        } catch (Exception e) {
            log.error("Failed to create audit trail for KDS deletion: {}", e.getMessage());
            // Don't break KDS deletion flow if audit trail fails
        }

        return ResponseDto.<String>builder()
                .message(messageUtil.getMessage("kds.delete.success", userLocale))
                .data("KDS with ID " + kdsId + " has been deleted")
                .build();
    }

    /**
     * Returns a filtered, paginated list of KDS devices for the caller's restaurant.
     * <p>
     * Applies restaurant scoping, excludes deleted KDS devices, supports optional status/search filtering,
     * and enforces role-based visibility (KDS-role users only see devices they are assigned to).
     * </p>
     *
     * @param page      1-based page number (optional)
     * @param size      page size (optional)
     * @param status    optional status filter
     * @param search    optional search term applied to translation names
     * @param sortBy    optional sort field
     * @param direction sort direction
     * @param userId    caller user id (string UUID)
     * @param locale    locale tag used for validation/messages
     * @return response wrapper containing {@link KdsListResponse}
     * @throws ResponseStatusException if locale is invalid, user is not found, or user has no restaurant assigned
     */
    @Override
    @Transactional(readOnly = true)
    public ResponseDto<KdsListResponse> getKdsList(Integer page, Integer size, String status, String search, 
                                                   String sortBy, Sort.Direction direction, String userId, String locale) {
        Locale userLocale = Locale.forLanguageTag(locale);

        // Validate and set pagination
        int pageNumber = (page != null && page > 0) ? page - 1 : 0;
        if (pageNumber < 0) pageNumber = 0;
        int pageSize = (size != null && size > 0) ? size : Integer.MAX_VALUE;

        // Validate locale
        if (!localizationProperties.getLanguages().contains(locale)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    messageUtil.getMessage(MSG_KDS_ERROR_INVALID_LANGUAGE, userLocale));
        }

        // Get user and restaurant ID
        User user = userRepository.findById(UUID.fromString(userId))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        messageUtil.getMessage(MSG_USER_NOT_FOUND, userLocale, userId)));

        if (user.getRestaurantId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    messageUtil.getMessage(MSG_USER_NOT_FOUND, userLocale));
        }

        UUID restaurantId = user.getRestaurantId();

        // Get user's role for role-based filtering
        Role userRole = null;
        String roleName = null;
        if (user.getRoleId() != null) {
            userRole = roleRepository.findById(user.getRoleId())
                    .orElse(null);
            if (userRole != null) {
                roleName = userRole.getName();
            }
        }

        // Process status filter
        final String statusValue;
        if (status != null && !status.isEmpty()) {
            try {
                EntityStatus statusEnum = EntityStatus.valueOf(status.toUpperCase());
                statusValue = statusEnum.name();
            } catch (IllegalArgumentException e) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        messageUtil.getMessage("error.invalid.status", userLocale, status));
            }
        } else {
            statusValue = null;
        }

        // Create specification for filtering
        final String finalRoleName = roleName;
        final UUID finalUserId = user.getId();
        Specification<Kds> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            // Add restaurant filter
            predicates.add(cb.equal(root.get("restaurantId"), restaurantId));

            // Add status filter
            if (statusValue != null) {
                predicates.add(cb.equal(root.get("status"), EntityStatus.valueOf(statusValue)));
            }

            // Add isDeleted filter - exclude deleted KDS
            predicates.add(cb.equal(root.get("isDeleted"), false));

            // Role-based filtering:
            // - MANAGER and HQ_ADMIN: See all KDS devices in their restaurant
            // - KDS users: See only KDS devices they are assigned to
            if ("KDS".equals(finalRoleName)) {
                // For KDS users, filter by assignment via KdsConfiguration using subquery
                Subquery<UUID> assignedKdsSubquery = query.subquery(UUID.class);
                Root<KdsConfiguration> configRoot = assignedKdsSubquery.from(KdsConfiguration.class);
                assignedKdsSubquery.select(configRoot.get("kds").get("id"));
                assignedKdsSubquery.where(cb.equal(configRoot.get("user").get("id"), finalUserId));
                predicates.add(cb.in(root.get("id")).value(assignedKdsSubquery));
            }
            // For MANAGER and HQ_ADMIN, no additional filter needed - they see all KDS in restaurant

            // Add search filter with translation join
            if (search != null && !search.trim().isEmpty()) {
                Join<Kds, KdsTranslation> translationJoin = root.join("translations", JoinType.LEFT);
                String searchPattern = "%" + search.toLowerCase() + "%";
                predicates.add(cb.like(cb.lower(translationJoin.get("name")), searchPattern));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };

        // Fetch all KDS from database (without pagination, like menu listing)
        List<Kds> kdsList = kdsRepository.findAll(spec);

        // Batch load all translations to avoid N+1 queries
        List<UUID> kdsIds = kdsList.stream()
                .map(Kds::getId)
                .collect(Collectors.toList());

        Map<UUID, List<KdsTranslation>> translationsMap = kdsTranslationRepository
                .findAllByKdsIdIn(kdsIds)
                .stream()
                .collect(Collectors.groupingBy(t -> t.getKds().getId()));

        // Batch load all CategoryKds for all KDS to avoid N+1 queries
        List<CategoryKds> allCategoryKds = categoryKdsRepository.findAllByKdsIdIn(kdsIds);
        Map<UUID, List<CategoryKds>> categoryKdsMap = allCategoryKds.stream()
                .collect(Collectors.groupingBy(ck -> ck.getKds().getId()));

        // Batch load all KdsConfiguration records to count assigned users for each KDS
        List<KdsConfiguration> allKdsConfigurations = kdsIds.isEmpty() 
                ? Collections.emptyList() 
                : kdsConfigurationRepository.findAllByKdsIdIn(kdsIds);
        Map<UUID, Long> assignedCountMap = allKdsConfigurations.stream()
                .collect(Collectors.groupingBy(
                        config -> config.getKds().getId(),
                        Collectors.counting()
                ));

        // Extract all unique category IDs and batch load translations
        Set<UUID> categoryIds = allCategoryKds.stream()
                .map(ck -> {
                    MenuCategoryMapping mcm = ck.getMenuCategoryMapping();
                    return (mcm != null && mcm.getCategory() != null) ? mcm.getCategory().getId() : null;
                })
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        final Map<UUID, String> categoryNameMap = categoryIds.isEmpty() 
                ? Collections.emptyMap()
                : categoryTranslationRepository
                        .findAllByCategoryIdInAndLanguageCode(new ArrayList<>(categoryIds), locale)
                        .stream()
                        .collect(Collectors.toMap(
                                ct -> ct.getCategory().getId(),
                                CategoryTranslation::getName,
                                (existing, replacement) -> existing
                        ));

        // Extract parent category IDs for batch loading parent names
        Set<UUID> parentCategoryIds = allCategoryKds.stream()
                .map(ck -> {
                    MenuCategoryMapping mcm = ck.getMenuCategoryMapping();
                    Category cat = (mcm != null) ? mcm.getCategory() : null;
                    return (cat != null && cat.getParentCategory() != null) ? cat.getParentCategory().getId() : null;
                })
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        final Map<UUID, String> parentCategoryNameMap = parentCategoryIds.isEmpty()
                ? Collections.emptyMap()
                : categoryTranslationRepository
                        .findAllByCategoryIdInAndLanguageCode(new ArrayList<>(parentCategoryIds), locale)
                        .stream()
                        .collect(Collectors.toMap(
                                ct -> ct.getCategory().getId(),
                                CategoryTranslation::getName,
                                (existing, replacement) -> existing
                        ));

        // Convert to response DTOs
        List<KdsResponse> kdsResponses = kdsList.stream()
                .map(kds -> {
                    List<KdsTranslation> translations = translationsMap.getOrDefault(kds.getId(), Collections.emptyList());
                    
                    // Find translation for the requested language
                    KdsTranslation translation = translations.stream()
                            .filter(t -> t.getLanguageCode() != null && t.getLanguageCode().equals(locale))
                            .findFirst()
                            .orElse(null);

                    List<KdsTranslationDto> translationDTOs = new ArrayList<>();
                    if (translation != null) {
                        translationDTOs.add(KdsTranslationDto.builder()
                                .languageCode(translation.getLanguageCode())
                                .name(translation.getName())
                                .build());
                    }

                    String createdByName = kds.getCreatedBy() != null ? kds.getCreatedBy().getFirstName() : null;
                    String updatedByName = kds.getUpdatedBy() != null ? kds.getUpdatedBy().getFirstName() : null;

                    // Get categories assigned to this KDS from batch-loaded map
                    List<CategoryKds> categoryKdsList = categoryKdsMap.getOrDefault(kds.getId(), Collections.emptyList());

                    List<CategoryListData> assignedRootCategories = new ArrayList<>();
                    List<CategoryResponse.CategoryData> assignedSubCategories = new ArrayList<>();

                    for (CategoryKds ck : categoryKdsList) {
                        MenuCategoryMapping menuCategoryMapping = ck.getMenuCategoryMapping();
                        if (menuCategoryMapping == null || menuCategoryMapping.getCategory() == null) {
                            continue;
                        }

                        Category category = menuCategoryMapping.getCategory();

                        // Get translated name from batch-loaded map
                        String categoryName = categoryNameMap.get(category.getId());

                        if (category.getParentCategory() == null) {
                            // Root category
                            CategoryListData data = CategoryListData.builder()
                                    .id(category.getId())
                                    .status(category.getStatus())
                                    .name(categoryName)
                                    .createdBy(category.getCreatedBy() != null ? category.getCreatedBy().getFirstName() : null)
                                    .createdAt(category.getCreatedAt() != null ? category.getCreatedAt().toLocalDateTime() : null)
                                    .updatedBy(category.getUpdatedBy() != null ? category.getUpdatedBy().getFirstName() : null)
                                    .updatedAt(category.getUpdatedAt() != null ? category.getUpdatedAt().toLocalDateTime() : null)
                                    .displayOrder(category.getDisplayOrder())
                                    .build();
                            assignedRootCategories.add(data);
                        } else {
                            // Subcategory
                            Category parent = category.getParentCategory();
                            String parentName = parent != null ? parentCategoryNameMap.get(parent.getId()) : null;

                            CategoryResponse.CategoryData sub = CategoryResponse.CategoryData.builder()
                                    .id(category.getId())
                                    .parentCategoryId(parent != null ? parent.getId() : null)
                                    .parentCategoryName(parentName)
                                    .status(category.getStatus())
                                    .name(categoryName)
                                    .createdBy(category.getCreatedBy() != null ? category.getCreatedBy().getFirstName() : null)
                                    .createdAt(category.getCreatedAt() != null ? category.getCreatedAt().toLocalDateTime() : null)
                                    .updatedBy(category.getUpdatedBy() != null ? category.getUpdatedBy().getFirstName() : null)
                                    .updatedAt(category.getUpdatedAt() != null ? category.getUpdatedAt().toLocalDateTime() : null)
                                    .displayOrder(category.getDisplayOrder())
                                    .build();
                            assignedSubCategories.add(sub);
                        }
                    }

                    // Get assigned user count for this KDS
                    Long assignedCount = assignedCountMap.getOrDefault(kds.getId(), 0L);
                    
                    // Check if device is linked (deviceCode is not null and not empty)
                    Boolean isDeviceLinked = kds.getDeviceCode() != null && !kds.getDeviceCode().trim().isEmpty();

                    return KdsResponse.builder()
                            .id(kds.getId())
                            .status(kds.getStatus())
                            .isDeleted(kds.getIsDeleted())
                            .isDefault(kds.getIsDefault())
                            .deviceCode(kds.getDeviceCode())
                            .isDeviceLinked(isDeviceLinked)
                            .createdAt(kds.getCreatedAt() != null ? kds.getCreatedAt().toLocalDateTime() : null)
                            .createdBy(createdByName)
                            .updatedAt(kds.getUpdatedAt() != null ? kds.getUpdatedAt().toLocalDateTime() : null)
                            .updatedBy(updatedByName)
                            .translations(translationDTOs)
                            .categories(assignedRootCategories)
                            .subCategories(assignedSubCategories)
                            .assignedCount(assignedCount)
                            .build();
                })
                .collect(Collectors.toList());

        // Apply sorting (in-memory for all fields like menu listing)
        String sortField = (sortBy != null && !sortBy.trim().isEmpty()) ? sortBy.trim() : "createdAt";
        Sort.Direction sortDirection = direction != null ? direction : Sort.Direction.DESC;
        
        if ("name".equalsIgnoreCase(sortField)) {
            // Set the locale in context for LocaleSortUtil (like menu service)
            LocaleContextHolder.setLocale(userLocale);
            LocaleSortUtil.sortName(kdsResponses, sortField, sortDirection);
        } else if ("createdAt".equalsIgnoreCase(sortField)) {
            Comparator<KdsResponse> comp = Comparator.comparing(
                KdsResponse::getCreatedAt,
                Comparator.nullsLast(Comparator.naturalOrder())
            );
            if (sortDirection == Sort.Direction.DESC) comp = comp.reversed();
            kdsResponses.sort(comp);
        } else if ("updatedAt".equalsIgnoreCase(sortField)) {
            Comparator<KdsResponse> comp = Comparator.comparing(
                KdsResponse::getUpdatedAt,
                Comparator.nullsLast(Comparator.naturalOrder())
            );
            if (sortDirection == Sort.Direction.DESC) comp = comp.reversed();
            kdsResponses.sort(comp);
        } else if ("status".equalsIgnoreCase(sortField)) {
            Comparator<KdsResponse> comp = Comparator.comparing(
                KdsResponse::getStatus,
                Comparator.nullsLast(Comparator.naturalOrder())
            );
            if (sortDirection == Sort.Direction.DESC) comp = comp.reversed();
            kdsResponses.sort(comp);
        } else if ("id".equalsIgnoreCase(sortField)) {
            Comparator<KdsResponse> comp = Comparator.comparing(
                KdsResponse::getId,
                Comparator.nullsLast(Comparator.naturalOrder())
            );
            if (sortDirection == Sort.Direction.DESC) comp = comp.reversed();
            kdsResponses.sort(comp);
        }

        // Apply pagination to sorted results (like menu listing)
        int fromIndex = Math.min(pageNumber * pageSize, kdsResponses.size());
        int toIndex = Math.min(fromIndex + pageSize, kdsResponses.size());
        List<KdsResponse> paginatedResponses = kdsResponses.subList(fromIndex, toIndex);

        // Build pagination metadata
        PaginationMetaData paginationMetaData = PaginationMetaData.builder()
                .page(pageNumber + 1)
                .size(pageSize)
                .totalPages((int) Math.ceil((double) kdsResponses.size() / pageSize))
                .totalRecords((long) kdsResponses.size())
                .build();

        // Build final response
        KdsListResponse listResponse = KdsListResponse.builder()
                .kds(paginatedResponses)
                .count((long) paginatedResponses.size())
                .total((long) kdsResponses.size())
                .metaData(paginationMetaData)
                .build();

        return ResponseDto.<KdsListResponse>builder()
                .message(messageUtil.getMessage("kds.list.success", userLocale))
                .data(listResponse)
                .build();
    }

    /**
     * Retrieves categories (and combo “category-like” entries) that are not assigned to any non-default KDS device.
     * <p>
     * Resolves the effective menu for the user's restaurant when {@code menuId} is not provided, builds the set of
     * menu-category mappings and combos already assigned across the restaurant’s non-default KDS devices, and returns
     * the remaining entries available for assignment.
     * </p>
     *
     * @param userId caller user id (string UUID)
     * @param menuId optional menu id; when null, the restaurant’s first mapped menu is used
     * @param locale locale tag used for messages and translation selection
     * @return response wrapper containing a combined category/combo selection payload
     * @throws ResponseStatusException if the user is not found or has no restaurant assigned
     */
    @Override
    @Transactional(readOnly = true)
    public ResponseDto<CategoryWrapperResponse> getUnassignedCategories(String userId, UUID menuId, String locale) {
        Locale userLocale = Locale.forLanguageTag(locale);

        // Resolve user and restaurant
        User user = userRepository.findById(UUID.fromString(userId))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        messageUtil.getMessage(MSG_USER_NOT_FOUND, userLocale, userId)));
        if (user.getRestaurantId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    messageUtil.getMessage(MSG_USER_RESTAURANT_NOT_ASSIGNED, userLocale));
        }

        // Resolve menu for restaurant if not provided
        UUID effectiveMenuId = menuId;
        if (effectiveMenuId == null) {
            List<RestaurantMenuMapping> mappings = restaurantMenuMappingRepository.findById_RestaurantId(user.getRestaurantId());
            if (mappings == null || mappings.isEmpty()) {
                return ResponseDto.<CategoryWrapperResponse>builder()
                        .message(messageUtil.getMessage(MSG_CATEGORY_KDS_UNASSIGNED_SUCCESS, userLocale))
                        .data(CategoryWrapperResponse.builder()
                                .categories(java.util.Collections.emptyList())
                                .count(0L)
                                .total(0L)
                                .build())
                        .build();
            }
            effectiveMenuId = mappings.get(0).getMenu().getId();
        }

        // Get all categories linked to the menu
        List<MenuCategoryMapping> menuCategoryMappings = menuCategoryMappingRepository.findByMenuId(effectiveMenuId);
        if (menuCategoryMappings == null || menuCategoryMappings.isEmpty()) {
            return ResponseDto.<CategoryWrapperResponse>builder()
                    .message(messageUtil.getMessage(MSG_CATEGORY_KDS_UNASSIGNED_SUCCESS, userLocale))
                    .data(CategoryWrapperResponse.builder()
                            .categories(java.util.Collections.emptyList())
                            .count(0L)
                            .total(0L)
                            .build())
                    .build();
        }

        // Collect restaurant KDS and exclude default in memory
        Specification<Kds> kdsSpec = (root, query, cb) -> cb.and(
                cb.equal(root.get("restaurantId"), user.getRestaurantId()),
                cb.equal(root.get("isDeleted"), false)
        );
        List<Kds> restaurantKdss = kdsRepository.findAll(kdsSpec).stream()
                .filter(k -> Boolean.FALSE.equals(k.getIsDefault()))
                .collect(Collectors.toList());

        // Build set of MenuCategoryMapping IDs that are assigned to any non-default KDS
        java.util.Set<UUID> assignedMenuCategoryMappingIds = new java.util.HashSet<>();
        // Build set of Combo IDs that are assigned to any non-default KDS
        java.util.Set<UUID> assignedComboIds = new java.util.HashSet<>();
        
        if (restaurantKdss != null && !restaurantKdss.isEmpty()) {
            for (Kds kds : restaurantKdss) {
                List<CategoryKds> mappingsByKds = categoryKdsRepository.findByKdsId(kds.getId());
                if (mappingsByKds != null) {
                    for (CategoryKds ck : mappingsByKds) {
                        if (ck.getMenuCategoryMapping() != null && ck.getMenuCategoryMapping().getId() != null) {
                            assignedMenuCategoryMappingIds.add(ck.getMenuCategoryMapping().getId());
                        }
                    }
                }
                
                // Get assigned combos for this KDS
                List<ComboKds> comboMappingsByKds = comboKdsRepository.findByKdsId(kds.getId());
                if (comboMappingsByKds != null) {
                    for (ComboKds ck : comboMappingsByKds) {
                        if (ck.getCombo() != null && ck.getCombo().getComboId() != null) {
                            assignedComboIds.add(ck.getCombo().getComboId());
                        }
                    }
                }
            }
        }

        // Filter menu category mappings that are NOT in assignedMenuCategoryMappingIds
        List<CategoryListData> unassigned = menuCategoryMappings.stream()
                .filter(mcm -> mcm.getCategory() != null && mcm.getCategory().getId() != null)
                .filter(mcm -> !assignedMenuCategoryMappingIds.contains(mcm.getId()))
                .map(mcm -> {
                    Category c = mcm.getCategory();
                    String name = null;
                    if (c.getTranslations() != null) {
                        name = c.getTranslations().stream()
                                .filter(t -> t.getLanguageCode() != null && t.getLanguageCode().equals(locale))
                                .map(t -> t.getName())
                                .findFirst()
                                .orElse(null);
                    }
                    return CategoryListData.builder()
                            .id(c.getId())
                            .status(c.getStatus())
                            .name(name)
                            .displayOrder(c.getDisplayOrder())
                            .menuCategoryMappingId(mcm.getId())
                            .build();
                })
                .collect(Collectors.toList());

        // Get all combos for the menu and filter unassigned ones
        List<Combo> allCombos = comboRepository.findByMenuIdAndStatusAndIsDeletedFalse(effectiveMenuId, EntityStatus.ACTIVE);
        List<ComboResponse> unassignedCombos = new ArrayList<>();
        List<CategoryListData> unassignedComboCategories = new ArrayList<>();
        
        if (allCombos != null && !allCombos.isEmpty()) {
            for (Combo combo : allCombos) {
                // Skip if combo is already assigned to any non-default KDS
                if (assignedComboIds.contains(combo.getComboId())) {
                    continue;
                }

                // Get combo translations
                List<ComboTranslation> comboTranslations = comboTranslationRepository.findByComboComboId(combo.getComboId());
                List<ComboTranslationDto> comboTranslationDtos = comboTranslations.stream()
                        .map(t -> ComboTranslationDto.builder()
                                .languageCode(t.getLanguageCode())
                                .name(t.getName())
                                .description(t.getDescription())
                                .build())
                        .collect(Collectors.toList());

                // Get combo name in the requested locale
                String comboName = comboTranslations.stream()
                        .filter(t -> t.getLanguageCode() != null && t.getLanguageCode().equals(locale))
                        .map(ComboTranslation::getName)
                        .findFirst()
                        .orElse(null);

                // Add combo as CategoryListData so it appears in the categories selection list
                CategoryListData comboAsCategory = CategoryListData.builder()
                        .id(combo.getComboId()) // Use combo ID as the ID
                        .comboId(combo.getComboId()) // Mark this as a combo
                        .status(combo.getStatus())
                        .name(comboName)
                        .displayOrder(null) // Combos don't have displayOrder
                        .createdAt(combo.getCreatedAt() != null ? combo.getCreatedAt().toLocalDateTime() : null)
                        .createdBy(combo.getCreatedBy() != null ? combo.getCreatedBy().getFirstName() : null)
                        .updatedAt(combo.getUpdatedAt() != null ? combo.getUpdatedAt().toLocalDateTime() : null)
                        .updatedBy(combo.getUpdatedBy() != null ? combo.getUpdatedBy().getFirstName() : null)
                        .menuCategoryMappingId(null) // Combos don't have menu category mapping IDs
                        .build();
                unassignedComboCategories.add(comboAsCategory);

                // Also keep the ComboResponse for backward compatibility
                ComboResponse comboResponse = ComboResponse.builder()
                        .comboId(combo.getComboId())
                        .menuId(combo.getMenu() != null ? combo.getMenu().getId() : null)
                        .type(combo.getType())
                        .basePrice(combo.getBasePrice())
                        .comboImageUrl(awsService.getFullUrl(combo.getComboImageUrl()))
                        .status(combo.getStatus())
                        .validFrom(combo.getValidFrom())
                        .validTo(combo.getValidTo())
                        .startTime(combo.getStartTime())
                        .endTime(combo.getEndTime())
                        .daysOfWeek(combo.getDaysOfWeek())
                        .createdAt(combo.getCreatedAt() != null ? combo.getCreatedAt().toLocalDateTime() : null)
                        .createdBy(combo.getCreatedBy() != null ? combo.getCreatedBy().getFirstName() : null)
                        .updatedAt(combo.getUpdatedAt() != null ? combo.getUpdatedAt().toLocalDateTime() : null)
                        .updatedBy(combo.getUpdatedBy() != null ? combo.getUpdatedBy().getFirstName() : null)
                        .translations(comboTranslationDtos)
                        .build();
                unassignedCombos.add(comboResponse);
            }
        }

        // Combine categories and combos into a single list for selection
        // Combos will have comboId set, categories will have menuCategoryMappingId set
        List<CategoryListData> allUnassignedCategories = new ArrayList<>(unassigned);
        allUnassignedCategories.addAll(unassignedComboCategories);

        long totalCount = allUnassignedCategories.size();
        CategoryWrapperResponse wrapper = CategoryWrapperResponse.builder()
                .categories(allUnassignedCategories) // Includes both categories and combos
                .combos(unassignedCombos) // Keep for backward compatibility
                .count(totalCount)
                .total(totalCount)
                .build();

        return ResponseDto.<CategoryWrapperResponse>builder()
                .message(messageUtil.getMessage(MSG_CATEGORY_KDS_UNASSIGNED_SUCCESS, userLocale))
                .data(wrapper)
                .build();
    }

    /**
     * Assigns one or more KDS-role users to a KDS device by creating {@link KdsConfiguration} records.
     * <p>
     * Enforces that assigned users are active/non-deleted, have the KDS role, belong to the same restaurant as the KDS,
     * and are not already assigned. Device code is copied from the KDS (may be null if the device is not yet linked).
     * An audit-trail entry is created for the bulk assignment.
     * </p>
     *
     * @param request assignment request containing KDS id and user ids to assign
     * @param userId  acting user id (string UUID)
     * @param locale  locale tag used for validation/messages
     * @return response wrapper containing the created configuration entries
     * @throws ResponseStatusException if locale is invalid, entities are not found, or validation fails
     */
    @Override
    @Transactional
    public ResponseDto<KdsConfigurationListResponse> assignUserToKds(AssignUserToKdsRequest request, String userId, String locale) {
        Locale userLocale = Locale.forLanguageTag(locale);

        // Validate locale
        if (!localizationProperties.getLanguages().contains(locale)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    messageUtil.getMessage(MSG_KDS_ERROR_INVALID_LANGUAGE, userLocale));
        }

        // Get the current user (who is performing the assignment)
        User currentUser = userRepository.findById(UUID.fromString(userId))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        messageUtil.getMessage(MSG_USER_NOT_FOUND, userLocale, userId)));

        // Get the KDS role
        Role kdsRole = roleRepository.findByName("KDS")
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        messageUtil.getMessage("role.not.found", userLocale, "KDS")));

        // Get the KDS
        Kds kds = kdsRepository.findById(request.getKdsId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        messageUtil.getMessage(MSG_KDS_GET_ERROR_NOT_FOUND, userLocale)));

        // Validate KDS is not deleted
        if (Boolean.TRUE.equals(kds.getIsDeleted())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    messageUtil.getMessage(MSG_KDS_GET_ERROR_DELETED, userLocale));
        }

        UUID kdsRestaurantId = kds.getRestaurantId();

        // Get device code from KDS (can be null if not yet initialized)
        // One KDS = One device code (stored in Kds entity)
        // Multiple users can be assigned to the same KDS, all using the same device code
        // One user can be assigned to multiple KDSs (each with different device codes)
        String deviceCode = kds.getDeviceCode();

        List<KdsConfigurationResponse> configurations = new ArrayList<>();
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);

        // Get KDS name from translation (once for all users)
        String kdsName = null;
        Optional<KdsTranslation> kdsTranslation = kdsTranslationRepository.findByKdsIdAndLanguageCode(
                kds.getId(), locale);
        if (kdsTranslation.isPresent()) {
            kdsName = kdsTranslation.get().getName();
        } else {
            // Fallback to any available translation
            List<KdsTranslation> allTranslations = kdsTranslationRepository.findAllByKdsId(kds.getId());
            if (!allTranslations.isEmpty()) {
                kdsName = allTranslations.get(0).getName();
            }
        }

        // Process each user (multiple users can be assigned to the same KDS)
        for (UUID userIdToAssign : request.getUserIds()) {
            // Get the user to be assigned
            User userToAssign = userRepository.findById(userIdToAssign)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                            messageUtil.getMessage(MSG_USER_NOT_FOUND, userLocale, userIdToAssign.toString())));

            // Validate user is not deleted
            if (Boolean.TRUE.equals(userToAssign.getIsDeleted())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        messageUtil.getMessage("user.deleted", userLocale));
            }

            // Validate user has KDS role
            if (!kdsRole.getId().equals(userToAssign.getRoleId())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        messageUtil.getMessage("kds.configuration.user.role.mismatch", userLocale));
            }

            // Validate user has restaurant assigned
            if (userToAssign.getRestaurantId() == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        messageUtil.getMessage(MSG_USER_RESTAURANT_NOT_ASSIGNED, userLocale));
            }

            // Validate user's restaurant matches KDS restaurant
            if (!userToAssign.getRestaurantId().equals(kdsRestaurantId)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        messageUtil.getMessage("kds.configuration.restaurant.mismatch", userLocale));
            }

            // Check if assignment already exists (skip if user is already assigned to this KDS)
            // One user can be assigned to multiple KDSs, but not duplicate assignments to the same KDS
            if (kdsConfigurationRepository.existsByUserIdAndKdsId(userIdToAssign, request.getKdsId())) {
                // Skip duplicate assignments - user is already assigned to this KDS
                continue;
            }

            // Create the configuration with device code
            KdsConfiguration configuration = KdsConfiguration.builder()
                    .user(userToAssign)
                    .kds(kds)
                    .deviceCode(deviceCode)
                    .createdAt(now)
                    .createdBy(currentUser)
                    .updatedAt(now)
                    .updatedBy(currentUser)
                    .build();

            configuration = kdsConfigurationRepository.save(configuration);

            // Build response item
            KdsConfigurationResponse configResponse = KdsConfigurationResponse.builder()
                    .id(configuration.getId())
                    .userId(userToAssign.getId())
                    .userName(userToAssign.getFirstName() + " " + (userToAssign.getLastName() != null ? userToAssign.getLastName() : ""))
                    .kdsId(kds.getId())
                    .kdsName(kdsName)
                    .deviceCode(deviceCode)
                    .createdAt(configuration.getCreatedAt() != null ? configuration.getCreatedAt().toLocalDateTime() : null)
                    .createdBy(currentUser.getFirstName())
                    .updatedAt(configuration.getUpdatedAt() != null ? configuration.getUpdatedAt().toLocalDateTime() : null)
                    .updatedBy(currentUser.getFirstName())
                    .build();

            configurations.add(configResponse);
        }

        // Create audit trail for KDS assignee add
        try {
            Restaurant restaurant = kds.getRestaurantId() != null ? 
                    restaurantRepository.findById(kds.getRestaurantId()).orElse(null) : null;
            String notes = String.format("Assigned %d user(s) to KDS: %s", 
                    configurations.size(), kdsName != null ? kdsName : kds.getId().toString());
            auditTrailService.createAuditTrail(
                    currentUser,
                    ActionType.KDS_ASSIGNEE_ADD,
                    restaurant,
                    RequestStatus.NA,
                    null, // ipAddress
                    null, // userAgent
                    kds.getId(),
                    "KDS",
                    notes
            );
        } catch (Exception e) {
            log.error("Failed to create audit trail for KDS assignee add: {}", e.getMessage());
        }

        // Build list response
        KdsConfigurationListResponse listResponse = KdsConfigurationListResponse.builder()
                .configurations(configurations)
                .count((long) configurations.size())
                .total((long) configurations.size())
                .build();

        return ResponseDto.<KdsConfigurationListResponse>builder()
                .message(messageUtil.getMessage("kds.configuration.assign.success", userLocale))
                .data(listResponse)
                .build();
    }

    /**
     * Unassigns one or more users from a KDS device by deleting {@link KdsConfiguration} records.
     * <p>
     * Missing assignments are ignored (idempotent behavior). An audit-trail entry is created describing how many users
     * were removed from the KDS.
     * </p>
     *
     * @param request unassignment request containing KDS id and user ids to unassign
     * @param userId  acting user id (string UUID)
     * @param locale  locale tag used for validation/messages
     * @return response wrapper containing the removed configuration entries (as response DTOs)
     * @throws ResponseStatusException if locale is invalid or the acting user/KDS cannot be found
     */
    @Override
    @Transactional
    public ResponseDto<KdsConfigurationListResponse> unassignUserFromKds(UnassignUserFromKdsRequest request, String userId, String locale) {
        Locale userLocale = Locale.forLanguageTag(locale);

        // Validate locale
        if (!localizationProperties.getLanguages().contains(locale)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    messageUtil.getMessage(MSG_KDS_ERROR_INVALID_LANGUAGE, userLocale));
        }

        // Get the current user (who is performing the unassignment)
        User currentUser = userRepository.findById(UUID.fromString(userId))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        messageUtil.getMessage(MSG_USER_NOT_FOUND, userLocale, userId)));

        // Get the KDS (for validation and name retrieval)
        Kds kds = kdsRepository.findById(request.getKdsId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        messageUtil.getMessage(MSG_KDS_GET_ERROR_NOT_FOUND, userLocale)));

        // Get KDS name from translation (once for all users)
        String kdsName = null;
        Optional<KdsTranslation> kdsTranslation = kdsTranslationRepository.findByKdsIdAndLanguageCode(
                kds.getId(), locale);
        if (kdsTranslation.isPresent()) {
            kdsName = kdsTranslation.get().getName();
        } else {
            // Fallback to any available translation
            List<KdsTranslation> allTranslations = kdsTranslationRepository.findAllByKdsId(kds.getId());
            if (!allTranslations.isEmpty()) {
                kdsName = allTranslations.get(0).getName();
            }
        }

        List<KdsConfigurationResponse> unassignedConfigurations = new ArrayList<>();

        // Process each user
        for (UUID userIdToUnassign : request.getUserIds()) {
            // Find the configuration
            Optional<KdsConfiguration> configurationOpt = kdsConfigurationRepository
                    .findByUserIdAndKdsId(userIdToUnassign, request.getKdsId());

            if (configurationOpt.isEmpty()) {
                // Skip if assignment doesn't exist - don't throw error
                continue;
            }

            KdsConfiguration configuration = configurationOpt.get();
            User userToUnassign = configuration.getUser();

            // Build response item before deletion
            KdsConfigurationResponse configResponse = KdsConfigurationResponse.builder()
                    .id(configuration.getId())
                    .userId(userToUnassign.getId())
                    .userName(userToUnassign.getFirstName() + " " + (userToUnassign.getLastName() != null ? userToUnassign.getLastName() : ""))
                    .kdsId(kds.getId())
                    .kdsName(kdsName)
                    .deviceCode(configuration.getDeviceCode())
                    .createdAt(configuration.getCreatedAt() != null ? configuration.getCreatedAt().toLocalDateTime() : null)
                    .createdBy(configuration.getCreatedBy() != null ? configuration.getCreatedBy().getFirstName() : null)
                    .updatedAt(LocalDateTime.now(ZoneOffset.UTC))
                    .updatedBy(currentUser.getFirstName())
                    .build();

            // Delete the configuration
            kdsConfigurationRepository.delete(configuration);

            unassignedConfigurations.add(configResponse);
        }

        // Create audit trail for KDS assignee remove
        try {
            Restaurant restaurant = kds.getRestaurantId() != null ? 
                    restaurantRepository.findById(kds.getRestaurantId()).orElse(null) : null;
            String notes = String.format("Removed %d user(s) from KDS: %s", 
                    unassignedConfigurations.size(), kdsName != null ? kdsName : kds.getId().toString());
            auditTrailService.createAuditTrail(
                    currentUser,
                    ActionType.KDS_ASSIGNEE_REMOVE,
                    restaurant,
                    RequestStatus.NA,
                    null, // ipAddress
                    null, // userAgent
                    kds.getId(),
                    "KDS",
                    notes
            );
        } catch (Exception e) {
            log.error("Failed to create audit trail for KDS assignee remove: {}", e.getMessage());
        }

        // Build list response
        KdsConfigurationListResponse listResponse = KdsConfigurationListResponse.builder()
                .configurations(unassignedConfigurations)
                .count((long) unassignedConfigurations.size())
                .total((long) unassignedConfigurations.size())
                .build();

        return ResponseDto.<KdsConfigurationListResponse>builder()
                .message(messageUtil.getMessage("kds.configuration.unassign.success", userLocale))
                .data(listResponse)
                .build();
    }

    /**
     * Fetches KDS configuration by device code.
     * <p>
     * Device linking is one-to-one: a device code identifies a single {@link Kds} record. This method validates the KDS
     * is active and not deleted, then returns the KDS details along with assigned categories/combos.
     * </p>
     *
     * @param deviceCode device code reported by the KDS client
     * @param locale     locale tag used for validation/messages and translation selection
     * @return response wrapper containing the KDS DTO
     * @throws ResponseStatusException if locale is invalid, the device code is not found, or the KDS is inactive/deleted
     */
    @Override
    @Transactional(readOnly = true)
    public ResponseDto<KdsDto<KdsResponse>> getKdsConfigByDeviceId(String deviceCode, String locale) {
        Locale userLocale = Locale.forLanguageTag(locale);

        // Validate locale
        if (!localizationProperties.getLanguages().contains(locale)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    messageUtil.getMessage(MSG_KDS_ERROR_INVALID_LANGUAGE, userLocale));
        }

        // Find KDS by device code (device code is stored in Kds entity)
        Optional<Kds> kdsOpt = kdsRepository.findByDeviceCode(deviceCode);
        if (kdsOpt.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                    messageUtil.getMessage("kds.config.not.found", userLocale));
        }

        Kds kds = kdsOpt.get();

        // Validate KDS is not deleted
        if (Boolean.TRUE.equals(kds.getIsDeleted())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    messageUtil.getMessage(MSG_KDS_GET_ERROR_DELETED, userLocale));
        }

        // Validate KDS is active
        if (kds.getStatus() != EntityStatus.ACTIVE) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    messageUtil.getMessage("kds.inactive", userLocale));
        }

        // Get all translations for the KDS
        List<KdsTranslation> translations = kdsTranslationRepository.findAllByKdsId(kds.getId());

        // Map all translations to DTOs
        List<KdsTranslationDto> translationDTOs = translations.stream()
                .map(t -> KdsTranslationDto.builder()
                        .languageCode(t.getLanguageCode())
                        .name(t.getName())
                        .build())
                .collect(Collectors.toList());

        // Get assigned categories for this KDS
        List<CategoryKds> categoryKdsMappings = categoryKdsRepository.findByKdsId(kds.getId());
        List<CategoryListData> assignedRootCategories = new ArrayList<>();
        List<CategoryResponse.CategoryData> assignedSubCategories = new ArrayList<>();
        
        if (categoryKdsMappings != null && !categoryKdsMappings.isEmpty()) {
            for (CategoryKds ck : categoryKdsMappings) {
                MenuCategoryMapping menuCategoryMapping = ck.getMenuCategoryMapping();
                if (menuCategoryMapping == null || menuCategoryMapping.getCategory() == null) {
                    continue;
                }

                Category category = menuCategoryMapping.getCategory();

                String categoryName = categoryTranslationRepository
                        .findByCategoryIdAndLanguageCode(category.getId(), locale)
                        .map(CategoryTranslation::getName)
                        .orElse(null);

                if (category.getParentCategory() == null) {
                    CategoryListData data = CategoryListData.builder()
                            .id(category.getId())
                            .status(category.getStatus())
                            .name(categoryName)
                            .menuCategoryMappingId(menuCategoryMapping.getId())
                            .createdBy(category.getCreatedBy() != null ? category.getCreatedBy().getFirstName() : null)
                            .createdAt(category.getCreatedAt() != null ? category.getCreatedAt().toLocalDateTime() : null)
                            .updatedBy(category.getUpdatedBy() != null ? category.getUpdatedBy().getFirstName() : null)
                            .updatedAt(category.getUpdatedAt() != null ? category.getUpdatedAt().toLocalDateTime() : null)
                            .displayOrder(category.getDisplayOrder())
                            .build();
                    assignedRootCategories.add(data);
                } else {
                    Category parent = category.getParentCategory();
                    String parentName = parent != null ? categoryTranslationRepository
                            .findByCategoryIdAndLanguageCode(parent.getId(), locale)
                            .map(CategoryTranslation::getName)
                            .orElse(null) : null;

                    CategoryResponse.CategoryData sub = CategoryResponse.CategoryData.builder()
                            .id(category.getId())
                            .parentCategoryId(parent != null ? parent.getId() : null)
                            .parentCategoryName(parentName)
                            .status(category.getStatus())
                            .name(categoryName)
                            .menuCategoryMappingId(menuCategoryMapping.getId())
                            .createdBy(category.getCreatedBy() != null ? category.getCreatedBy().getFirstName() : null)
                            .createdAt(category.getCreatedAt() != null ? category.getCreatedAt().toLocalDateTime() : null)
                            .updatedBy(category.getUpdatedBy() != null ? category.getUpdatedBy().getFirstName() : null)
                            .updatedAt(category.getUpdatedAt() != null ? category.getUpdatedAt().toLocalDateTime() : null)
                            .displayOrder(category.getDisplayOrder())
                            .build();
                    assignedSubCategories.add(sub);
                }
            }
        }

        // Populate assigned combos for response
        List<ComboKds> comboKdsMappingsForDevice = comboKdsRepository.findByKdsId(kds.getId());
        List<ComboResponse> assignedCombosForDevice = new ArrayList<>();
        List<CategoryListData> assignedComboCategoriesForDevice = new ArrayList<>();
        
        for (ComboKds ck : comboKdsMappingsForDevice) {
            Combo combo = ck.getCombo();
            if (combo == null) {
                continue;
            }

            // Get combo translations
            List<ComboTranslation> comboTranslations = comboTranslationRepository.findByComboComboId(combo.getComboId());
            List<ComboTranslationDto> comboTranslationDtos = comboTranslations.stream()
                    .map(t -> ComboTranslationDto.builder()
                            .languageCode(t.getLanguageCode())
                            .name(t.getName())
                            .description(t.getDescription())
                            .build())
                    .collect(Collectors.toList());

            // Get combo name in the requested locale
            String comboName = comboTranslations.stream()
                    .filter(t -> t.getLanguageCode() != null && t.getLanguageCode().equals(locale))
                    .map(ComboTranslation::getName)
                    .findFirst()
                    .orElse(null);

            // Add combo as CategoryListData so it appears in the categories list (for consistency with selection API)
            CategoryListData comboAsCategory = CategoryListData.builder()
                    .id(combo.getComboId()) // Use combo ID as the ID
                    .comboId(combo.getComboId()) // Mark this as a combo
                    .status(combo.getStatus())
                    .name(comboName)
                    .displayOrder(null) // Combos don't have displayOrder
                    .createdAt(combo.getCreatedAt() != null ? combo.getCreatedAt().toLocalDateTime() : null)
                    .createdBy(combo.getCreatedBy() != null ? combo.getCreatedBy().getFirstName() : null)
                    .updatedAt(combo.getUpdatedAt() != null ? combo.getUpdatedAt().toLocalDateTime() : null)
                    .updatedBy(combo.getUpdatedBy() != null ? combo.getUpdatedBy().getFirstName() : null)
                    .menuCategoryMappingId(null) // Combos don't have menu category mapping IDs
                    .build();
            assignedComboCategoriesForDevice.add(comboAsCategory);

            // Also keep the ComboResponse for backward compatibility
            ComboResponse comboResponse = ComboResponse.builder()
                    .comboId(combo.getComboId())
                    .menuId(combo.getMenu() != null ? combo.getMenu().getId() : null)
                    .type(combo.getType())
                    .basePrice(combo.getBasePrice())
                    .comboImageUrl(awsService.getFullUrl(combo.getComboImageUrl()))
                    .status(combo.getStatus())
                    .validFrom(combo.getValidFrom())
                    .validTo(combo.getValidTo())
                    .startTime(combo.getStartTime())
                    .endTime(combo.getEndTime())
                    .daysOfWeek(combo.getDaysOfWeek())
                    .createdAt(combo.getCreatedAt() != null ? combo.getCreatedAt().toLocalDateTime() : null)
                    .createdBy(combo.getCreatedBy() != null ? combo.getCreatedBy().getFirstName() : null)
                    .updatedAt(combo.getUpdatedAt() != null ? combo.getUpdatedAt().toLocalDateTime() : null)
                    .updatedBy(combo.getUpdatedBy() != null ? combo.getUpdatedBy().getFirstName() : null)
                    .translations(comboTranslationDtos)
                    .build();
            assignedCombosForDevice.add(comboResponse);
        }

        // Combine categories and combos into a single list for consistency with selection API
        List<CategoryListData> allAssignedCategoriesForDevice = new ArrayList<>(assignedRootCategories);
        allAssignedCategoriesForDevice.addAll(assignedComboCategoriesForDevice);

        // Check if device is linked (deviceCode is not null and not empty)
        Boolean isDeviceLinked = kds.getDeviceCode() != null && !kds.getDeviceCode().trim().isEmpty();
        
        // Build response
        KdsResponse response = KdsResponse.builder()
                .id(kds.getId())
                .status(kds.getStatus())
                .isDeleted(kds.getIsDeleted())
                .isDefault(kds.getIsDefault())
                .deviceCode(kds.getDeviceCode())
                .isDeviceLinked(isDeviceLinked)
                .createdAt(kds.getCreatedAt() != null ? kds.getCreatedAt().toLocalDateTime() : null)
                .createdBy(kds.getCreatedBy() != null ? kds.getCreatedBy().getFirstName() : null)
                .updatedAt(kds.getUpdatedAt() != null ? kds.getUpdatedAt().toLocalDateTime() : null)
                .updatedBy(kds.getUpdatedBy() != null ? kds.getUpdatedBy().getFirstName() : null)
                .translations(translationDTOs)
                .categories(allAssignedCategoriesForDevice) // Includes both categories and combos
                .subCategories(assignedSubCategories)
                .combos(assignedCombosForDevice) // Keep for backward compatibility
                .build();

        KdsDto<KdsResponse> kdsDto = KdsDto.<KdsResponse>builder()
                .kds(response)
                .build();

        return ResponseDto.<KdsDto<KdsResponse>>builder()
                .message(messageUtil.getMessage("kds.config.fetch.success", userLocale))
                .data(kdsDto)
                .build();
    }

    /**
     * Links/updates the device code for a KDS and propagates it to all related {@link KdsConfiguration} rows.
     * <p>
     * Enforces the one-to-one constraint between device code and KDS (device code cannot be used by another KDS).
     * </p>
     *
     * @param kdsId   KDS identifier
     * @param request request containing the device code to link
     * @param locale  locale tag used for validation/messages
     * @return response wrapper containing the updated KDS DTO
     * @throws ResponseStatusException if locale is invalid, the KDS is not found/deleted, or device code conflicts
     */
    @Override
    @Transactional
    public ResponseDto<KdsDto<KdsResponse>> updateKdsConfig(UUID kdsId, UpdateKdsConfigRequest request, String locale) {
        Locale userLocale = Locale.forLanguageTag(locale);

        // Validate locale
        if (!localizationProperties.getLanguages().contains(locale)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    messageUtil.getMessage(MSG_KDS_ERROR_INVALID_LANGUAGE, userLocale));
        }

        // Validate device code is provided
        String deviceCode = request.getDeviceCode();
        if (deviceCode == null || deviceCode.trim().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    messageUtil.getMessage("kds.device.code.required", userLocale));
        }

        // Find KDS by ID
        Kds kds = kdsRepository.findById(kdsId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        messageUtil.getMessage(MSG_KDS_GET_ERROR_NOT_FOUND, userLocale)));

        // Validate KDS is not deleted
        if (Boolean.TRUE.equals(kds.getIsDeleted())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    messageUtil.getMessage(MSG_KDS_GET_ERROR_DELETED, userLocale));
        }

        // Check if device code is already assigned to a different KDS (one-to-one relationship: one device code = one KDS)
        Optional<Kds> existingKdsByDeviceCode = kdsRepository.findByDeviceCode(deviceCode.trim());
        if (existingKdsByDeviceCode.isPresent() && !existingKdsByDeviceCode.get().getId().equals(kdsId)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    messageUtil.getMessage("kds.device.code.exists", userLocale));
        }

        // Update device code
        kds.setDeviceCode(deviceCode.trim());
        kds.setUpdatedAt(OffsetDateTime.now(ZoneOffset.UTC));
        kds = kdsRepository.save(kds);

        // Update device code in all KdsConfiguration records for this KDS
        List<KdsConfiguration> configurations = kdsConfigurationRepository.findByKdsId(kdsId);
        if (!configurations.isEmpty()) {
            for (KdsConfiguration config : configurations) {
                config.setDeviceCode(deviceCode.trim());
                config.setUpdatedAt(OffsetDateTime.now(ZoneOffset.UTC));
                kdsConfigurationRepository.save(config);
            }
        }

        // Get all translations for the KDS
        List<KdsTranslation> translations = kdsTranslationRepository.findAllByKdsId(kds.getId());

        // Map all translations to DTOs
        List<KdsTranslationDto> translationDTOs = translations.stream()
                .map(t -> KdsTranslationDto.builder()
                        .languageCode(t.getLanguageCode())
                        .name(t.getName())
                        .build())
                .collect(Collectors.toList());

        // Get assigned categories for this KDS
        List<CategoryKds> categoryKdsMappings = categoryKdsRepository.findByKdsId(kds.getId());
        List<CategoryListData> assignedCategories = new ArrayList<>();
        
        if (categoryKdsMappings != null && !categoryKdsMappings.isEmpty()) {
            assignedCategories = categoryKdsMappings.stream()
                    .map(ck -> {
                        MenuCategoryMapping menuCategoryMapping = ck.getMenuCategoryMapping();
                        if (menuCategoryMapping == null || menuCategoryMapping.getCategory() == null) {
                            return null;
                        }
                        
                        Category category = menuCategoryMapping.getCategory();
                        
                        // Get category name in the requested locale
                        String categoryName = null;
                        if (category.getTranslations() != null) {
                            categoryName = category.getTranslations().stream()
                                    .filter(t -> t.getLanguageCode() != null && t.getLanguageCode().equals(locale))
                                    .map(t -> t.getName())
                                    .findFirst()
                                    .orElse(null);
                        }
                        
                        return CategoryListData.builder()
                                .id(category.getId())
                                .status(category.getStatus())
                                .name(categoryName)
                                .displayOrder(category.getDisplayOrder())
                                .createdAt(category.getCreatedAt() != null ? category.getCreatedAt().toLocalDateTime() : null)
                                .updatedAt(category.getUpdatedAt() != null ? category.getUpdatedAt().toLocalDateTime() : null)
                                .build();
                    })
                    .filter(c -> c != null)
                    .collect(Collectors.toList());
        }

        // Check if device is linked (deviceCode is not null and not empty)
        Boolean isDeviceLinked = deviceCode != null && !deviceCode.trim().isEmpty();
        
        // Build response
        KdsResponse response = KdsResponse.builder()
                .id(kds.getId())
                .status(kds.getStatus())
                .isDeleted(kds.getIsDeleted())
                .isDefault(kds.getIsDefault())
                .deviceCode(deviceCode)
                .isDeviceLinked(isDeviceLinked)
                .createdAt(kds.getCreatedAt() != null ? kds.getCreatedAt().toLocalDateTime() : null)
                .createdBy(kds.getCreatedBy() != null ? kds.getCreatedBy().getFirstName() : null)
                .updatedAt(kds.getUpdatedAt() != null ? kds.getUpdatedAt().toLocalDateTime() : null)
                .updatedBy(kds.getUpdatedBy() != null ? kds.getUpdatedBy().getFirstName() : null)
                .translations(translationDTOs)
                .categories(assignedCategories)
                .build();

        KdsDto<KdsResponse> kdsDto = KdsDto.<KdsResponse>builder()
                .kds(response)
                .build();

        return ResponseDto.<KdsDto<KdsResponse>>builder()
                .message(messageUtil.getMessage("kds.config.update.success", userLocale))
                .data(kdsDto)
                .build();
    }

    /**
     * Assigns (links) a device code to a KDS device.
     * <p>
     * Only MANAGER and HQ_ADMIN are allowed. Enforces restaurant scoping for MANAGER (must match KDS restaurant),
     * ensures the device code is not already linked to any KDS, persists it on the KDS and updates existing
     * {@link KdsConfiguration} rows with the new device code.
     * </p>
     *
     * @param request request containing the KDS id and device code to assign
     * @param userId  acting user id (string UUID)
     * @param locale  locale tag used for validation/messages
     * @return response wrapper containing the updated KDS DTO
     * @throws ResponseStatusException if locale is invalid, authorization fails, or device code conflicts
     */
    @Override
    @Transactional
    public ResponseDto<KdsDto<KdsResponse>> assignDeviceToKds(AssignDeviceToKdsRequest request, String userId, String locale) {
        Locale userLocale = Locale.forLanguageTag(locale);

        // Validate locale
        if (!localizationProperties.getLanguages().contains(locale)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    messageUtil.getMessage(MSG_KDS_ERROR_INVALID_LANGUAGE, userLocale));
        }

        // Get the current user (who is performing the assignment)
        User currentUser = userRepository.findById(UUID.fromString(userId))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        messageUtil.getMessage(MSG_USER_NOT_FOUND, userLocale, userId)));

        // Validate user is not deleted
        if (Boolean.TRUE.equals(currentUser.getIsDeleted())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    messageUtil.getMessage("user.deleted", userLocale));
        }

        // Get user's role
        Role userRole = null;
        if (currentUser.getRoleId() != null) {
            userRole = roleRepository.findById(currentUser.getRoleId())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                            messageUtil.getMessage("role.not.found", userLocale, currentUser.getRoleId().toString())));
        }

        // Validate only MANAGER or HQ_ADMIN can assign devices
        if (userRole == null || (!"MANAGER".equals(userRole.getName()) && !"HQ_ADMIN".equals(userRole.getName()))) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    messageUtil.getMessage("kds.device.assign.unauthorized", userLocale));
        }

        // Validate device code is provided
        String deviceCode = request.getDeviceCode();
        if (deviceCode == null || deviceCode.trim().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    messageUtil.getMessage("kds.device.code.required", userLocale));
        }
        deviceCode = deviceCode.trim();

        // Find KDS by ID
        Kds kds = kdsRepository.findById(request.getKdsId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        messageUtil.getMessage(MSG_KDS_GET_ERROR_NOT_FOUND, userLocale)));

        // Validate KDS is not deleted
        if (Boolean.TRUE.equals(kds.getIsDeleted())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    messageUtil.getMessage(MSG_KDS_GET_ERROR_DELETED, userLocale));
        }

        // If user is MANAGER, validate they can only link to KDS in their restaurant
        if ("MANAGER".equals(userRole.getName())) {
            if (currentUser.getRestaurantId() == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        messageUtil.getMessage(MSG_USER_RESTAURANT_NOT_ASSIGNED, userLocale));
            }

            if (!currentUser.getRestaurantId().equals(kds.getRestaurantId())) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                        messageUtil.getMessage(msgKdsRestaurantMismatch, userLocale));
            }
        }
        // HQ_ADMIN can link to any KDS (no restaurant restriction)

        // Check if device code is already assigned to any KDS (same or different)
        // If already assigned, return error message
        Optional<Kds> existingKdsByDeviceCode = kdsRepository.findByDeviceCode(deviceCode);
        if (existingKdsByDeviceCode.isPresent()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    messageUtil.getMessage("kds.device.already.assigned", userLocale));
        }

        // Set device code on KDS
        kds.setDeviceCode(deviceCode);
        kds.setUpdatedAt(OffsetDateTime.now(ZoneOffset.UTC));
        kds.setUpdatedBy(currentUser);
        kds = kdsRepository.save(kds);

        // Update device code in all existing KdsConfiguration records for this KDS
        List<KdsConfiguration> configurations = kdsConfigurationRepository.findByKdsId(kds.getId());
        if (!configurations.isEmpty()) {
            for (KdsConfiguration config : configurations) {
                config.setDeviceCode(deviceCode);
                config.setUpdatedAt(OffsetDateTime.now(ZoneOffset.UTC));
                config.setUpdatedBy(currentUser);
                kdsConfigurationRepository.save(config);
            }
        }

        // Get all translations for the KDS
        List<KdsTranslation> translations = kdsTranslationRepository.findAllByKdsId(kds.getId());

        // Map all translations to DTOs
        List<KdsTranslationDto> translationDTOs = translations.stream()
                .map(t -> KdsTranslationDto.builder()
                        .languageCode(t.getLanguageCode())
                        .name(t.getName())
                        .build())
                .collect(Collectors.toList());

        // Get assigned categories for this KDS
        List<CategoryKds> categoryKdsMappings = categoryKdsRepository.findByKdsId(kds.getId());
        List<CategoryListData> assignedCategories = new ArrayList<>();
        
        if (categoryKdsMappings != null && !categoryKdsMappings.isEmpty()) {
            assignedCategories = categoryKdsMappings.stream()
                    .map(ck -> {
                        MenuCategoryMapping menuCategoryMapping = ck.getMenuCategoryMapping();
                        if (menuCategoryMapping == null || menuCategoryMapping.getCategory() == null) {
                            return null;
                        }
                        
                        Category category = menuCategoryMapping.getCategory();
                        
                        // Get category name in the requested locale
                        String categoryName = null;
                        if (category.getTranslations() != null) {
                            categoryName = category.getTranslations().stream()
                                    .filter(t -> t.getLanguageCode() != null && t.getLanguageCode().equals(locale))
                                    .map(t -> t.getName())
                                    .findFirst()
                                    .orElse(null);
                        }
                        
                        return CategoryListData.builder()
                                .id(category.getId())
                                .status(category.getStatus())
                                .name(categoryName)
                                .displayOrder(category.getDisplayOrder())
                                .createdAt(category.getCreatedAt() != null ? category.getCreatedAt().toLocalDateTime() : null)
                                .updatedAt(category.getUpdatedAt() != null ? category.getUpdatedAt().toLocalDateTime() : null)
                                .build();
                    })
                    .filter(c -> c != null)
                    .collect(Collectors.toList());
        }

        // Check if device is linked (deviceCode is not null and not empty)
        Boolean isDeviceLinked = deviceCode != null && !deviceCode.trim().isEmpty();
        
        // Build response
        KdsResponse response = KdsResponse.builder()
                .id(kds.getId())
                .status(kds.getStatus())
                .isDeleted(kds.getIsDeleted())
                .isDefault(kds.getIsDefault())
                .deviceCode(deviceCode)
                .isDeviceLinked(isDeviceLinked)
                .createdAt(kds.getCreatedAt() != null ? kds.getCreatedAt().toLocalDateTime() : null)
                .createdBy(kds.getCreatedBy() != null ? kds.getCreatedBy().getFirstName() : null)
                .updatedAt(kds.getUpdatedAt() != null ? kds.getUpdatedAt().toLocalDateTime() : null)
                .updatedBy(currentUser.getFirstName())
                .translations(translationDTOs)
                .categories(assignedCategories)
                .build();

        KdsDto<KdsResponse> kdsDto = KdsDto.<KdsResponse>builder()
                .kds(response)
                .build();

        return ResponseDto.<KdsDto<KdsResponse>>builder()
                .message(messageUtil.getMessage("kds.device.assign.success", userLocale))
                .data(kdsDto)
                .build();
    }

    /**
     * Returns users assigned to a KDS device for a given restaurant.
     * <p>
     * Filters defensively to users who still have the KDS role and belong to the provided restaurant.
     * </p>
     *
     * @param kdsId        KDS identifier
     * @param restaurantId restaurant identifier (must match the KDS restaurant)
     * @param locale       locale tag used for validation/messages
     * @return response wrapper containing the assigned user list
     * @throws ResponseStatusException if locale is invalid, KDS is not found/deleted, or restaurant mismatch occurs
     */
    @Override
    @Transactional(readOnly = true)
    public ResponseDto<KdsAssignedUserListResponse> getAssignedUsersByKdsId(UUID kdsId, UUID restaurantId, String locale) {
        Locale userLocale = Locale.forLanguageTag(locale);

        // Validate locale
        if (!localizationProperties.getLanguages().contains(locale)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    messageUtil.getMessage(MSG_KDS_ERROR_INVALID_LANGUAGE, userLocale));
        }

        // Only return users whose role is KDS (defensive against stale kds_configuration rows).
        UUID kdsRoleId = roleRepository.findByName("KDS")
                .map(Role::getId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                        "KDS role not found"));

        // Find KDS by ID
        Kds kds = kdsRepository.findById(kdsId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        messageUtil.getMessage(MSG_KDS_GET_ERROR_NOT_FOUND, userLocale)));

        // Validate KDS is not deleted
        if (Boolean.TRUE.equals(kds.getIsDeleted())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    messageUtil.getMessage(MSG_KDS_GET_ERROR_DELETED, userLocale));
        }

        if (kds.getRestaurantId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    messageUtil.getMessage(msgRestaurantNotFound, userLocale));
        }

        if (!restaurantId.equals(kds.getRestaurantId())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    messageUtil.getMessage(msgKdsRestaurantMismatch, userLocale));
        }

        // Get all KdsConfiguration records for this KDS
        List<KdsConfiguration> configurations = kdsConfigurationRepository.findByKdsId(kdsId);

        // Map to response DTOs
        List<KdsAssignedUserResponse> assignedUsers = configurations.stream()
                .map(config -> {
                    User user = config.getUser();
                    if (user == null) {
                        return null;
                    }

                    if (user.getRoleId() == null || !kdsRoleId.equals(user.getRoleId())) {
                        return null;
                    }

                    if (user.getRestaurantId() == null || !restaurantId.equals(user.getRestaurantId())) {
                        return null;
                    }

                    return KdsAssignedUserResponse.builder()
                            .id(user.getId())
                            .firstName(user.getFirstName())
                            .lastName(user.getLastName())
                            .email(user.getEmail())
                            .contactNumber(user.getContactNumber())
                            .status(user.getStatus())
                            .build();
                })
                .filter(Objects::nonNull)
                .collect(Collectors.toList());

        // Build response
        KdsAssignedUserListResponse listResponse = KdsAssignedUserListResponse.builder()
                .users(assignedUsers)
                .count((long) assignedUsers.size())
                .total((long) assignedUsers.size())
                .build();

        return ResponseDto.<KdsAssignedUserListResponse>builder()
                .message(messageUtil.getMessage("kds.assigned.users.fetch.success", userLocale))
                .data(listResponse)
                .build();
    }

    // ==================== TICKET DASHBOARD METHODS ====================

    /**
     * Retrieves detailed ticket information for a single ordered item shown on KDS.
     * <p>
     * Uses an eager-fetch query for waiter information to avoid lazy-loading issues in the response.
     * </p>
     *
     * @param orderedItemId ordered item identifier
     * @return response wrapper containing ticket details
     * @throws ResponseStatusException if the ordered item is not found
     */
    @Override
    @Transactional(readOnly = true)
    public ResponseDto<TicketDetailsResponse> getTicketDetails(UUID orderedItemId) {
        log.info("Getting ticket details for ordered item: {}", orderedItemId);

        Locale userLocale = LocaleContextHolder.getLocale();

        // Use query that eagerly fetches waiter information to ensure it's available in the response
        OrderedItem orderedItem = orderedItemRepository.findByIdWithWaiterInfo(orderedItemId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        messageUtil.getMessage("ordered.item.not.found", userLocale)));

        TicketDetailsResponse details = convertToTicketDetailsResponse(orderedItem, userLocale);

        return ResponseDto.<TicketDetailsResponse>builder()
                .message(messageUtil.getMessage("ticket.details.retrieved.success", userLocale))
                .data(details)
                .build();
    }

    /**
     * Retrieves the live ticket dashboard for a KDS device.
     * <p>
     * Validates KDS existence, supports multi-select filtering through comma-separated query params
     * (item statuses, order types, tables by id or code, categories, and sections), and delegates the main
     * aggregation/paging to {@link #computeTicketDashboard(UUID, Integer, Integer, String, String, String, String, String, String, String, String)}.
     * </p>
     *
     * @param kdsId         KDS identifier
     * @param userId        caller user id (used for logging; access is validated during device login)
     * @param page          1-based page number (optional)
     * @param size          page size (optional)
     * @param itemStatuses  comma-separated item-status filter
     * @param orderTypes    comma-separated order-type filter
     * @param tableIds      comma-separated table-id filter
     * @param tableCodes    comma-separated table-code filter (resolved to ids and merged with {@code tableIds})
     * @param categoryIds   comma-separated category-id filter (validated against KDS assigned categories)
     * @param sectionIds    comma-separated section-id filter
     * @param sortBy        sort field
     * @param direction     sort direction
     * @return response wrapper containing the ticket dashboard list payload
     * @throws ResponseStatusException if the KDS is not found or has no restaurant assigned
     */
    @Override
    @Transactional(readOnly = true)
    public ResponseDto<TicketDashboardListDto> getTicketDashboardForKds(UUID kdsId, String userId, Integer page, Integer size,
            String itemStatuses, String orderTypes, String tableIds, String tableCodes, String categoryIds, String sectionIds, String sortBy, String direction) {
        Locale userLocale = LocaleContextHolder.getLocale();

        // Validate KDS
        Kds kds = kdsRepository.findById(kdsId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        messageUtil.getMessage(MSG_KDS_DELETE_ERROR_NOT_FOUND, userLocale)));

        if (kds.getRestaurantId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    messageUtil.getMessage(msgRestaurantNotFound, userLocale));
        }

        // Note: Restaurant and user access validation is performed at login time when deviceCode is provided
        // No need to validate again here for every API call
        log.info("User {} accessing KDS {} dashboard (validation done at login)", userId, kdsId);

        // Resolve table codes to table IDs if tableCodes is provided
        String mergedTableIds = tableIds;
        if (tableCodes != null && !tableCodes.isBlank()) {
            try {
                // Parse table codes from comma-separated string (supports multi-select)
                List<String> tableCodeList = Arrays.stream(tableCodes.split(","))
                        .map(String::trim)
                        .filter(s -> !s.isBlank())
                        .collect(Collectors.toList());
                
                if (!tableCodeList.isEmpty()) {
                    // Convert table codes to lowercase for case-insensitive matching
                    List<String> lowercaseTableCodes = tableCodeList.stream()
                            .map(String::toLowerCase)
                            .collect(Collectors.toList());
                    
                    // Find table IDs by table codes for this restaurant
                    List<UUID> resolvedTableIds = restaurantTableRepository
                            .findTableIdsByTableCodesAndRestaurantId(kds.getRestaurantId(), lowercaseTableCodes);
                    
                    log.info("Resolved {} table codes to {} table IDs for restaurant: {}", 
                            tableCodeList.size(), resolvedTableIds.size(), kds.getRestaurantId());
                    
                    if (!resolvedTableIds.isEmpty()) {
                        // Merge with existing tableIds if provided (supports multi-select via comma-separated string)
                        Set<UUID> allTableIds = new HashSet<>(resolvedTableIds);
                        
                        if (tableIds != null && !tableIds.isBlank()) {
                            // Parse existing table IDs and add to set (supports multi-select)
                            Arrays.stream(tableIds.split(","))
                                    .map(String::trim)
                                    .filter(s -> !s.isBlank())
                                    .forEach(s -> {
                                        try {
                                            allTableIds.add(UUID.fromString(s));
                                        } catch (IllegalArgumentException e) {
                                            log.warn("Invalid table ID format in filter: '{}'. Skipping this ID.", s);
                                        }
                                    });
                        }
                        
                        // Convert merged set back to comma-separated string
                        mergedTableIds = allTableIds.stream()
                                .map(UUID::toString)
                                .collect(Collectors.joining(","));
                        
                        log.info("Merged table filter: {} table IDs (from {} table codes + {} existing table IDs)", 
                                allTableIds.size(), resolvedTableIds.size(), 
                                (tableIds != null && !tableIds.isBlank()) ? "some" : "0");
                    } else {
                        log.warn("No tables found for table codes: {} in restaurant: {}. Table code filter will be ignored.", 
                                tableCodes, kds.getRestaurantId());
                    }
                }
            } catch (Exception e) {
                log.error("Error resolving table codes to IDs: '{}'. Error: {}", tableCodes, e.getMessage(), e);
                // On error, continue with original tableIds (don't break the request)
            }
        }

        // Handle category filter: Use provided categoryIds if available, otherwise use KDS-assigned categories
        String finalCategoryIds = categoryIds;
        if (categoryIds == null || categoryIds.isBlank()) {
            // Resolve category IDs assigned to this KDS (fallback to KDS configuration)
            List<CategoryKds> mappings = categoryKdsRepository.findByKdsId(kdsId);
            log.info("Found {} CategoryKds mappings for KDS: {}", mappings != null ? mappings.size() : 0, kdsId);
            
            if (mappings != null && !mappings.isEmpty()) {
                // Extract category IDs from mappings, handling null values in the chain
                // If category is a subcategory, use parent category ID; otherwise use category ID itself
                List<UUID> extractedCategoryIds = mappings.stream()
                        .map(CategoryKds::getMenuCategoryMapping)
                        .filter(java.util.Objects::nonNull)
                        .peek(mcm -> log.debug("Processing MenuCategoryMapping: {}", mcm.getId()))
                        .map(MenuCategoryMapping::getCategory)
                        .filter(java.util.Objects::nonNull)
                        .map(category -> {
                            // If category has a parent, use parent ID (main category)
                            // Otherwise, use the category ID itself (it's already a main category)
                            if (category.getParentCategory() != null) {
                                log.debug("Category {} is a subcategory, using parent category: {}", 
                                        category.getId(), category.getParentCategory().getId());
                                return category.getParentCategory().getId();
                            } else {
                                log.debug("Category {} is a main category", category.getId());
                                return category.getId();
                            }
                        })
                        .filter(java.util.Objects::nonNull)
                        .distinct() // Remove duplicates in case multiple subcategories share the same parent
                        .collect(Collectors.toList());
                
                log.info("Extracted {} valid category IDs from {} mappings for KDS: {}", 
                        extractedCategoryIds.size(), mappings.size(), kdsId);
                
                if (!extractedCategoryIds.isEmpty()) {
                    finalCategoryIds = extractedCategoryIds.stream()
                            .map(UUID::toString)
                            .collect(Collectors.joining(","));
                    log.info("Using KDS-assigned category IDs: {}", finalCategoryIds);
                } else {
                    log.warn("No valid category IDs found for KDS: {} (mappings exist but MenuCategoryMapping or Category is null)", kdsId);
                    // Return empty list if no valid categories found
                    return ResponseDto.<TicketDashboardListDto>builder()
                            .message(messageUtil.getMessage(MSG_TICKET_DASHBOARD_RETRIEVED_SUCCESS, userLocale))
                            .data(TicketDashboardListDto.builder()
                                    .tickets(java.util.Collections.emptyList())
                                    .count(0L)
                                    .total(0L)
                                    .statusCounts(TicketDashboardListDto.StatusCounts.builder()
                                            .pushed(0L).cooking(0L).delayed(0L).ready(0L).served(0L).canceled(0L)
                                            .build())
                                    .build())
                            .build();
                }
            } else {
                log.warn("No CategoryKds mappings found for KDS: {}. No tickets will be returned.", kdsId);
                // No categories assigned → return empty list quickly
                return ResponseDto.<TicketDashboardListDto>builder()
                        .message(messageUtil.getMessage(MSG_TICKET_DASHBOARD_RETRIEVED_SUCCESS, userLocale))
                        .data(TicketDashboardListDto.builder()
                                .tickets(java.util.Collections.emptyList())
                                .count(0L)
                                .total(0L)
                                .statusCounts(TicketDashboardListDto.StatusCounts.builder()
                                        .pushed(0L).cooking(0L).delayed(0L).ready(0L).served(0L).canceled(0L)
                                        .build())
                                .build())
                        .build();
            }
        } else {
            // Validate provided categoryIds format (supports multi-select via comma-separated string)
            log.info("Using provided category filter: {} (supports multi-select via comma-separated UUIDs)", categoryIds);
            try {
                // Parse and validate category IDs
                List<UUID> providedCategoryIds = Arrays.stream(categoryIds.split(","))
                        .map(String::trim)
                        .filter(s -> !s.isBlank())
                        .map(s -> {
                            try {
                                return UUID.fromString(s);
                            } catch (IllegalArgumentException e) {
                                log.warn("Invalid category ID format in filter: '{}'. Skipping this ID.", s);
                                return null;
                            }
                        })
                        .filter(java.util.Objects::nonNull)
                        .collect(Collectors.toList());
                
                if (providedCategoryIds.isEmpty()) {
                    log.warn("No valid category IDs found in filter string: '{}'. Skipping category filter.", categoryIds);
                    finalCategoryIds = null;
                } else {
                    // CRITICAL SECURITY FIX: Validate that provided categories are actually assigned to this KDS
                    // This prevents users from accessing items from other KDS by providing category IDs manually
                    List<CategoryKds> kdsCategoryMappings = categoryKdsRepository.findByKdsId(kdsId);
                    
                    // Extract category IDs assigned to this KDS (handle subcategories by using parent category)
                    Set<UUID> kdsAssignedCategoryIds = kdsCategoryMappings.stream()
                            .map(CategoryKds::getMenuCategoryMapping)
                            .filter(java.util.Objects::nonNull)
                            .map(MenuCategoryMapping::getCategory)
                            .filter(java.util.Objects::nonNull)
                            .map(category -> {
                                // If category has a parent, use parent ID (main category)
                                // Otherwise, use the category ID itself (it's already a main category)
                                if (category.getParentCategory() != null) {
                                    return category.getParentCategory().getId();
                                } else {
                                    return category.getId();
                                }
                            })
                            .filter(java.util.Objects::nonNull)
                            .collect(Collectors.toSet());
                    
                    // Filter provided category IDs to only those assigned to this KDS
                    List<UUID> validCategoryIds = providedCategoryIds.stream()
                            .filter(kdsAssignedCategoryIds::contains)
                            .collect(Collectors.toList());
                    
                    if (validCategoryIds.isEmpty()) {
                        log.warn("None of the provided category IDs ({}) are assigned to KDS {}. Returning empty results.", 
                                providedCategoryIds, kdsId);
                        // Return empty list if no valid categories
                        return ResponseDto.<TicketDashboardListDto>builder()
                                .message(messageUtil.getMessage(MSG_TICKET_DASHBOARD_RETRIEVED_SUCCESS, userLocale))
                                .data(TicketDashboardListDto.builder()
                                        .tickets(java.util.Collections.emptyList())
                                        .count(0L)
                                        .total(0L)
                                        .statusCounts(TicketDashboardListDto.StatusCounts.builder()
                                                .pushed(0L).cooking(0L).delayed(0L).ready(0L).served(0L).canceled(0L)
                                                .build())
                                        .build())
                                .build();
                    }
                    
                    // Log if some categories were filtered out
                    if (validCategoryIds.size() < providedCategoryIds.size()) {
                        List<UUID> filteredOut = providedCategoryIds.stream()
                                .filter(id -> !kdsAssignedCategoryIds.contains(id))
                                .collect(Collectors.toList());
                        log.warn("Filtered out {} category ID(s) that are not assigned to KDS {}: {}. Using only valid categories: {}", 
                                filteredOut.size(), kdsId, filteredOut, validCategoryIds);
                    }
                    
                    finalCategoryIds = validCategoryIds.stream()
                            .map(UUID::toString)
                            .collect(Collectors.joining(","));
                    
                    log.info("Validated category filter: {} valid category IDs assigned to KDS {} (from {} provided)", 
                            validCategoryIds.size(), kdsId, providedCategoryIds.size());
                }
            } catch (Exception e) {
                log.error("Error validating category IDs: '{}'. Error: {}", categoryIds, e.getMessage(), e);
                // On error, return empty results to prevent security issue
                return ResponseDto.<TicketDashboardListDto>builder()
                        .message(messageUtil.getMessage(MSG_TICKET_DASHBOARD_RETRIEVED_SUCCESS, userLocale))
                        .data(TicketDashboardListDto.builder()
                                .tickets(java.util.Collections.emptyList())
                                .count(0L)
                                .total(0L)
                                .statusCounts(TicketDashboardListDto.StatusCounts.builder()
                                        .pushed(0L).cooking(0L).delayed(0L).ready(0L).served(0L).canceled(0L)
                                        .build())
                                .build())
                        .build();
            }
        }

        // Delegate to existing restaurant-scoped method, supplying categoryIds filter and merged tableIds
        log.info("Calling computeTicketDashboard for restaurant: {} with categoryIds: {}, tableIds: {} (both support multi-select)", 
                kds.getRestaurantId(), finalCategoryIds, mergedTableIds);
        return computeTicketDashboard(kds.getRestaurantId(), page, size,
                itemStatuses, orderTypes, finalCategoryIds, null, mergedTableIds, sectionIds, sortBy, direction);
    }

        /**
         * Parameter object for {@link #computeTicketDashboard(TicketDashboardParams)}.
         * <p>
         * Used to reduce the argument count and keep filter/paging fields bundled together for logging and reuse.
         * </p>
         */
    private static class TicketDashboardParams {
        final UUID restaurantId;
        final Integer page;
        final Integer size;
        final String itemStatuses;
        final String orderTypes;
        final String categoryIds;
        final String subcategoryIds;
        final String tableIds;
        final String sectionIds;
        final String sortBy;
        final String direction;
        
        TicketDashboardParams(UUID restaurantId, Integer page, Integer size, 
                String itemStatuses, String orderTypes, String categoryIds, String subcategoryIds, 
                String tableIds, String sectionIds, String sortBy, String direction) {
            this.restaurantId = restaurantId;
            this.page = page;
            this.size = size;
            this.itemStatuses = itemStatuses;
            this.orderTypes = orderTypes;
            this.categoryIds = categoryIds;
            this.subcategoryIds = subcategoryIds;
            this.tableIds = tableIds;
            this.sectionIds = sectionIds;
            this.sortBy = sortBy;
            this.direction = direction;
        }
    }
    
    /**
     * Convenience overload that builds {@link TicketDashboardParams} and delegates to
     * {@link #computeTicketDashboard(TicketDashboardParams)}.
     */
    private ResponseDto<TicketDashboardListDto> computeTicketDashboard(UUID restaurantId, Integer page, Integer size, 
            String itemStatuses, String orderTypes, String categoryIds, String subcategoryIds, 
            String tableIds, String sectionIds, String sortBy, String direction) {
        TicketDashboardParams params = new TicketDashboardParams(restaurantId, page, size, itemStatuses, orderTypes, 
                categoryIds, subcategoryIds, tableIds, sectionIds, sortBy, direction);
        return computeTicketDashboard(params);
    }
    
    /**
     * Computes the ticket dashboard for a restaurant by applying filters, sorting, pagination, and aggregation.
     * <p>
     * Uses the restaurant-configured reset time to scope the base dataset, then applies service-layer filters
     * (status/order-type/table/section/category). Category filtering is restricted to menus assigned to the restaurant.
     * </p>
     *
     * @param params consolidated dashboard parameters
     * @return dashboard list payload with count/total and status counts
     * @throws ResponseStatusException if the restaurant is not found
     */
    private ResponseDto<TicketDashboardListDto> computeTicketDashboard(TicketDashboardParams params) {
        log.info("Computing ticket dashboard for restaurant: {} (page: {}, size: {}, itemStatuses: {}, orderTypes: {}, " +
                "categoryIds: {}, subcategoryIds: {}, tableIds: {}, sectionIds: {}, sortBy: {}, direction: {})", 
                params.restaurantId, params.page, params.size, params.itemStatuses, params.orderTypes, 
                params.categoryIds, params.subcategoryIds, params.tableIds, params.sectionIds, params.sortBy, params.direction);

        Locale userLocale = LocaleContextHolder.getLocale();

        // Validate restaurant
        Restaurant restaurant = restaurantRepository.findById(params.restaurantId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        messageUtil.getMessage(msgRestaurantNotFound, userLocale)));

        // Get restaurant's assigned menu IDs - needed for category filtering
        // Category filter should only consider items from menus assigned to this restaurant
        List<UUID> restaurantMenuIds = restaurantMenuMappingRepository.findById_RestaurantId(params.restaurantId)
                .stream()
                .map(rmm -> rmm.getId().getMenuId())
                .collect(Collectors.toList());
        log.debug("Restaurant {} has {} assigned menu(s): {}", params.restaurantId, restaurantMenuIds.size(), restaurantMenuIds);

        // Calculate daily reset time from restaurant table
        LocalDateTime resetTime = calculateDailyResetTime(restaurant);

        // Parse item-status filter once and apply it at the DB layer.
        // Supports comma-separated values (e.g. "PUSHED,SERVED,CANCELED").
        List<String> itemStatusNames = null;
        if (params.itemStatuses != null && !params.itemStatuses.isBlank()) {
            try {
                itemStatusNames = Arrays.stream(params.itemStatuses.split(","))
                        .map(String::trim)
                        .filter(s -> !s.isBlank())
                        .map(s -> ItemStatus.valueOf(s.toUpperCase()).name())
                        .distinct()
                        .collect(Collectors.toList());
                if (itemStatusNames.isEmpty()) {
                    itemStatusNames = null;
                }
            } catch (IllegalArgumentException e) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Invalid itemStatus filter value. Allowed values are: " + Arrays.toString(ItemStatus.values()));
            }
        }

        // Get base items (after reset time)
        // Note: Items are shown based on reset time only, not session expiration status
        // Sessions are not expired when tables are marked cleanup/available to preserve KDS dashboard items
        List<OrderedItem> baseItems = (itemStatusNames == null)
                ? orderedItemRepository.findTicketDashboardItemsBase(params.restaurantId, resetTime)
                : orderedItemRepository.findTicketDashboardItemsBaseWithItemStatuses(params.restaurantId, resetTime, itemStatusNames);
        log.info("Found {} base OrderedItems for restaurant: {} (after reset time: {})", 
                baseItems.size(), params.restaurantId, resetTime);

        // Note: Section relationships are loaded lazily. When section filter is applied,
        // getSectionIdFromItem() will access the section relationship, which may trigger lazy loading.
        // If lazy loading fails (e.g., entity manager cleared, session closed), the section filter
        // will correctly filter out the item (see section filter exception handling below).

        Set<UUID> restaurantMenuIdSet = new HashSet<>(restaurantMenuIds);
        Set<OrderType> orderTypeFilter = parseOrderTypes(params.orderTypes);
        Set<UUID> tableFilter = parseUuidFilter(params.tableIds, "table");
        Set<UUID> sectionFilter = parseUuidFilter(params.sectionIds, "section");
        Set<UUID> categoryFilter = parseUuidFilter(params.categoryIds, "category");
        Set<UUID> subcategoryFilter = parseUuidFilter(params.subcategoryIds, "subcategory");

        Map<UUID, List<CategoryItemMapping>> categoryMappingsByItemId =
                loadCategoryMappingsByItemId(baseItems, restaurantMenuIdSet);

        // Apply filters in service layer
        List<OrderedItem> filteredItems = baseItems.stream()
                .filter(item -> {
                    if (!orderTypeFilter.isEmpty() && !orderTypeFilter.contains(item.getOrder().getOrderType())) {
                        return false;
                    }

                    if (!tableFilter.isEmpty()) {
                        if (item.getOrder() == null || item.getOrder().getRestaurantTable() == null) {
                            return false;
                        }
                        UUID itemTableId = item.getOrder().getRestaurantTable().getId();
                        if (!tableFilter.contains(itemTableId)) {
                            return false;
                        }
                    }

                    if (!sectionFilter.isEmpty()) {
                        UUID sectionId = getSectionIdFromItem(item);
                        if (sectionId == null || !sectionFilter.contains(sectionId)) {
                            return false;
                        }
                    }

                    if (!categoryFilter.isEmpty() || !subcategoryFilter.isEmpty()) {
                        List<CategoryItemMapping> mappings = categoryMappingsByItemId
                                .getOrDefault(item.getItem().getId(), Collections.emptyList());

                        if (!categoryFilter.isEmpty() && !checkCategoryMatch(mappings, categoryFilter, item)) {
                            return false;
                        }

                        if (!subcategoryFilter.isEmpty()) {
                            boolean subcategoryMatches = mappings.stream()
                                    .map(CategoryItemMapping::getMenuCategoryMapping)
                                    .filter(Objects::nonNull)
                                    .map(MenuCategoryMapping::getCategory)
                                    .filter(Objects::nonNull)
                                    .filter(category -> category.getParentCategory() != null)
                                    .map(Category::getId)
                                    .anyMatch(subcategoryFilter::contains);
                            if (!subcategoryMatches) {
                                return false;
                            }
                        }
                    }

                    return true;
                })
                .collect(Collectors.toList());

        log.info("After filtering, {} OrderedItems match the criteria for restaurant: {} (categoryIds: {})", 
                filteredItems.size(), params.restaurantId, params.categoryIds);

        // Calculate status counts
        TicketDashboardListDto.StatusCounts statusCounts = TicketDashboardListDto.StatusCounts.builder()
                .pushed(filteredItems.stream().filter(i -> i.getItemStatus() == ItemStatus.PUSHED).count())
                .cooking(filteredItems.stream().filter(i -> i.getItemStatus() == ItemStatus.COOKING).count())
                .delayed(filteredItems.stream().filter(i -> i.getItemStatus() == ItemStatus.DELAYED).count())
                .ready(filteredItems.stream().filter(i -> i.getItemStatus() == ItemStatus.READY).count())
                .served(filteredItems.stream().filter(i -> i.getItemStatus() == ItemStatus.SERVED).count())
                .canceled(filteredItems.stream().filter(i -> i.getItemStatus() == ItemStatus.CANCELED).count())
                .build();

        // Sort items
        filteredItems = sortTicketItems(filteredItems, params.sortBy, params.direction);

        // Apply pagination
        long total = filteredItems.size();
        boolean noPaging = (params.page == null || params.size == null || params.page <= 0 || params.size <= 0);
        if (!noPaging) {
            int startIndex = (params.page - 1) * params.size;
            int endIndex = Math.min(startIndex + params.size, filteredItems.size());
            if (startIndex < filteredItems.size()) {
                filteredItems = filteredItems.subList(startIndex, endIndex);
            } else {
                filteredItems = new ArrayList<>();
            }
        }

        // Convert to response DTOs
        // Parse category filter for use in response building
        Set<UUID> categoryFilterSet = null;
        if (params.categoryIds != null && !params.categoryIds.isBlank()) {
            try {
                categoryFilterSet = Arrays.stream(params.categoryIds.split(","))
                        .map(String::trim)
                        .filter(s -> !s.isBlank())
                        .map(UUID::fromString)
                        .collect(Collectors.toSet());
            } catch (Exception e) {
                log.warn("Failed to parse categoryIds for response building: {}", e.getMessage());
            }
        }
        final Set<UUID> finalCategoryFilterSet = categoryFilterSet;
        Map<UUID, User> waiterByTableId = resolveWaitersByTableId(filteredItems);
        Map<UUID, List<OrderedItemModifier>> modifiersByOrderedItemId = loadModifiersByOrderedItemId(filteredItems);
        Map<String, String> imageUrlCache = new HashMap<>();
        
        List<TicketDashboardResponse> tickets = filteredItems.stream()
                .map(item -> convertToTicketDashboardResponse(
                        item,
                        userLocale,
                        finalCategoryFilterSet,
                        categoryMappingsByItemId,
                        waiterByTableId,
                        modifiersByOrderedItemId,
                        imageUrlCache))
                .collect(Collectors.toList());

        // Build response
        TicketDashboardListDto dto = TicketDashboardListDto.builder()
                .tickets(tickets)
                .count((long) tickets.size())
                .total(total)
                .statusCounts(statusCounts)
                .metaData(noPaging ? null : PaginationMetaData.builder()
                        .page(params.page)
                        .size(params.size)
                        .totalPages((int) Math.ceil((double) total / params.size))
                        .totalRecords(total)
                        .build())
                .build();

        return ResponseDto.<TicketDashboardListDto>builder()
                .message(messageUtil.getMessage(MSG_TICKET_DASHBOARD_RETRIEVED_SUCCESS, userLocale))
                .data(dto)
                .build();
    }

    private Set<OrderType> parseOrderTypes(String orderTypes) {
        if (orderTypes == null || orderTypes.isBlank()) {
            return Collections.emptySet();
        }
        try {
            return Arrays.stream(orderTypes.split(","))
                    .map(String::trim)
                    .filter(s -> !s.isBlank())
                    .map(s -> OrderType.valueOf(s.toUpperCase()))
                    .collect(Collectors.toSet());
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Invalid orderType filter value. Allowed values are: " + Arrays.toString(OrderType.values()));
        }
    }

    private Set<UUID> parseUuidFilter(String rawFilter, String filterName) {
        if (rawFilter == null || rawFilter.isBlank()) {
            return Collections.emptySet();
        }

        Set<UUID> parsed = Arrays.stream(rawFilter.split(","))
                .map(String::trim)
                .filter(s -> !s.isBlank())
                .map(s -> {
                    try {
                        return UUID.fromString(s);
                    } catch (IllegalArgumentException e) {
                        log.warn("Invalid {} ID format in filter: '{}'. Skipping this ID.", filterName, s);
                        return null;
                    }
                })
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        if (parsed.isEmpty()) {
            log.warn("No valid {} IDs found in filter string: '{}'. Skipping {} filter.", filterName, rawFilter, filterName);
        }

        return parsed;
    }

    private Map<UUID, List<CategoryItemMapping>> loadCategoryMappingsByItemId(
            List<OrderedItem> items,
            Set<UUID> restaurantMenuIdSet) {
        if (items == null || items.isEmpty()) {
            return Collections.emptyMap();
        }

        List<UUID> itemIds = items.stream()
                .map(OrderedItem::getItem)
                .filter(Objects::nonNull)
                .map(Item::getId)
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.toList());

        if (itemIds.isEmpty()) {
            return Collections.emptyMap();
        }

        List<CategoryItemMapping> allMappings = categoryItemMappingRepository.findByItem_IdIn(itemIds);

        return allMappings.stream()
                .filter(mapping -> isMappingFromAssignedMenu(mapping, restaurantMenuIdSet))
                .collect(Collectors.groupingBy(mapping -> mapping.getItem().getId()));
    }

    private boolean isMappingFromAssignedMenu(CategoryItemMapping mapping, Set<UUID> restaurantMenuIdSet) {
        if (mapping == null || mapping.getMenuCategoryMapping() == null || mapping.getMenuCategoryMapping().getMenu() == null) {
            return false;
        }
        return restaurantMenuIdSet.contains(mapping.getMenuCategoryMapping().getMenu().getId());
    }

    private Map<UUID, User> resolveWaitersByTableId(List<OrderedItem> items) {
        if (items == null || items.isEmpty()) {
            return Collections.emptyMap();
        }

        List<UUID> tableIds = items.stream()
                .map(OrderedItem::getOrder)
                .filter(Objects::nonNull)
                .map(Order::getRestaurantTable)
                .filter(Objects::nonNull)
                .map(RestaurantTable::getId)
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.toList());

        if (tableIds.isEmpty()) {
            return Collections.emptyMap();
        }

        List<TableAssignment> assignments = tableAssignmentRepository
                .findByRestaurantTableIdInAndUnassignedAtIsNullWithWaiter(tableIds);

        Map<UUID, TableAssignment> latestByTable = new HashMap<>();
        for (TableAssignment assignment : assignments) {
            if (assignment == null || assignment.getRestaurantTable() == null || assignment.getWaiter() == null) {
                continue;
            }
            UUID tableId = assignment.getRestaurantTable().getId();
            TableAssignment existing = latestByTable.get(tableId);
            if (existing == null) {
                latestByTable.put(tableId, assignment);
                continue;
            }
            OffsetDateTime existingAssigned = existing.getAssignedAt();
            OffsetDateTime candidateAssigned = assignment.getAssignedAt();
            if (existingAssigned == null || (candidateAssigned != null && candidateAssigned.isAfter(existingAssigned))) {
                latestByTable.put(tableId, assignment);
            }
        }

        Map<UUID, User> waiterByTableId = new HashMap<>();
        latestByTable.forEach((tableId, assignment) -> waiterByTableId.put(tableId, assignment.getWaiter()));
        return waiterByTableId;
    }

    private Map<UUID, List<OrderedItemModifier>> loadModifiersByOrderedItemId(List<OrderedItem> items) {
        if (items == null || items.isEmpty()) {
            return Collections.emptyMap();
        }

        List<UUID> orderedItemIds = items.stream()
                .map(OrderedItem::getId)
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.toList());

        if (orderedItemIds.isEmpty()) {
            return Collections.emptyMap();
        }

        List<OrderedItemModifier> modifiers = orderedItemModifierRepository.findByOrderedItemIdInWithRelations(orderedItemIds);
        return modifiers.stream()
                .filter(m -> m.getOrderedItem() != null && m.getOrderedItem().getId() != null)
                .collect(Collectors.groupingBy(m -> m.getOrderedItem().getId()));
    }

    /**
     * Calculate daily reset time from restaurant table.
     * Defaults to midnight UTC if restaurant doesn't have a value set.
     * 
     * @param restaurant The restaurant entity to get reset time from
     * @return LocalDateTime representing the last reset time
     */
    private LocalDateTime calculateDailyResetTime(Restaurant restaurant) {
        // Get reset time from restaurant table only
        if (restaurant == null || restaurant.getKdsLiveDashboardResetTime() == null) {
            log.warn("No KDS reset time configured for restaurant {}. Defaulting to midnight UTC.", 
                    restaurant != null ? restaurant.getId() : "unknown");
            return LocalDate.now(ZoneOffset.UTC).atStartOfDay();
        }

        try {
            OffsetTime resetOffsetTime = restaurant.getKdsLiveDashboardResetTime();
            
            // Ensure UTC timezone
            resetOffsetTime = resetOffsetTime.withOffsetSameInstant(ZoneOffset.UTC);
            log.debug("Using KDS reset time from restaurant table: {} for restaurant: {}", 
                    resetOffsetTime, restaurant.getId());
            
            // Get current UTC time
            LocalDateTime nowUtc = LocalDateTime.now(ZoneOffset.UTC);
            LocalDateTime resetDateTime = LocalDate.now(ZoneOffset.UTC).atTime(resetOffsetTime.toLocalTime());
            
            // If reset time hasn't passed today in UTC, use yesterday's reset time
            if (resetDateTime.isAfter(nowUtc)) {
                resetDateTime = resetDateTime.minusDays(1);
            }
            
            return resetDateTime;
        } catch (Exception e) {
            log.warn("Failed to parse reset time from restaurant {}: {}, using midnight UTC. Error: {}", 
                    restaurant.getId(), restaurant.getKdsLiveDashboardResetTime(), e.getMessage());
            return LocalDate.now(ZoneOffset.UTC).atStartOfDay();
        }
    }

    /**
     * Converts an {@link OrderedItem} into a KDS ticket dashboard response row.
     * <p>
     * Enriches with table/section metadata, waiter assignment, translated names, image URLs, and category/subcategory
     * information while avoiding lazy-loading pitfalls where possible.
     * </p>
     *
     * @param item              ordered item to convert
     * @param userLocale        locale used for translation selection
     * @param categoryFilterSet optional category filter context (may be null)
     * @return dashboard response row
     */
    private TicketDashboardResponse convertToTicketDashboardResponse(OrderedItem item, Locale userLocale, Set<UUID> categoryFilterSet) {
        return convertToTicketDashboardResponse(
                item,
                userLocale,
                categoryFilterSet,
                Collections.emptyMap(),
                Collections.emptyMap(),
                Collections.emptyMap(),
                new HashMap<>());
    }

    private TicketDashboardResponse convertToTicketDashboardResponse(
            OrderedItem item,
            Locale userLocale,
            Set<UUID> categoryFilterSet,
            Map<UUID, List<CategoryItemMapping>> categoryMappingsByItemId,
            Map<UUID, User> waiterByTableId,
            Map<UUID, List<OrderedItemModifier>> modifiersByOrderedItemId,
            Map<String, String> imageUrlCache) {
        Order order = item.getOrder();
        
        // Get table and section info
        UUID sectionId = null;
        String sectionName = null;
        Integer tableOrder = null;
        Integer rowOrder = null;
        String tableCode = null;
        if (order.getRestaurantTable() != null) {
            tableOrder = order.getRestaurantTable().getTableOrder();
            tableCode = order.getRestaurantTable().getTableCode();
            if (order.getRestaurantTable().getRestaurantRow() != null) {
                rowOrder = order.getRestaurantTable().getRestaurantRow().getRowOrder();
                if (order.getRestaurantTable().getRestaurantRow().getRestaurantSection() != null) {
                    RestaurantSection section = order.getRestaurantTable().getRestaurantRow().getRestaurantSection();
                    sectionId = section.getId();
                    sectionName = section.getTranslations().stream()
                            .filter(t -> userLocale.getLanguage().equalsIgnoreCase(t.getLanguageCode()))
                            .map(RestaurantSectionTranslation::getName)
                            .findFirst()
                            .orElse(section.getTranslations().isEmpty() ? "" : section.getTranslations().get(0).getName());
                }
            }
        }

        // Get waiter info from table assignment (not from order.waiter which may be null)
        // For KDS items, there should always be a waiter assigned to the table
        String waiterName = null;
        UUID waiterId = null;
        User waiter = null;
        if (order.getRestaurantTable() != null) {
            waiter = waiterByTableId.get(order.getRestaurantTable().getId());
        }
        // Fallback to order.getWaiter() if table assignment doesn't have a waiter
        if (waiter == null && order.getWaiter() != null) {
            waiter = order.getWaiter();
        }
        if (waiter != null) {
            waiterId = waiter.getId();
            String firstName = waiter.getFirstName() != null ? waiter.getFirstName() : "";
            String lastName = waiter.getLastName() != null ? waiter.getLastName() : "";
            waiterName = (firstName + " " + lastName).trim();
            if (waiterName.isEmpty()) {
                log.warn("Waiter found for order {} but name is empty. Waiter ID: {}", order.getId(), waiterId);
                waiterName = "N/A";
            }
        } else {
            // This should not happen for KDS items - log error but don't show "Customer"
            log.error("No waiter found for KDS item {} in order {} at table {}. This should not happen for items in KDS.", 
                    item.getId(), order.getId(), 
                    order.getRestaurantTable() != null ? order.getRestaurantTable().getId() : "null");
            waiterName = "N/A";
        }

        // Get last status changed by
        String lastStatusChangedBy = "System";
        if (item.getUpdatedBy() != null) {
            String firstName = item.getUpdatedBy().getFirstName() != null ? item.getUpdatedBy().getFirstName() : "";
            String lastName = item.getUpdatedBy().getLastName() != null ? item.getUpdatedBy().getLastName() : "";
            lastStatusChangedBy = (firstName + " " + lastName).trim();
            if (lastStatusChangedBy.isEmpty()) {
                lastStatusChangedBy = "System";
            }
        }

        // Get item name and image
        String itemName = item.getItem().getTranslations().stream()
                .filter(t -> t.getLanguageCode().equals(userLocale.getLanguage()))
                .findFirst()
                .map(ItemTranslation::getName)
                .orElse(item.getItem().getTranslations().isEmpty() ? 
                    "Item" : item.getItem().getTranslations().get(0).getName());
        String imageUrlKey = item.getItem().getImageUrl();
        String imageUrl = null;
        if (imageUrlKey != null && !imageUrlKey.isEmpty()) {
            imageUrl = imageUrlCache.computeIfAbsent(imageUrlKey, awsService::getPreSignedUrl);
        }

        // Get category info
        // Use eagerly fetched mappings to avoid lazy loading issues
        UUID categoryId = null;
        String categoryName = null;
        UUID subcategoryId = null;
        String subcategoryName = null;
        List<CategoryItemMapping> mappings = categoryMappingsByItemId.getOrDefault(
                item.getItem().getId(),
                Collections.emptyList());
        if (!mappings.isEmpty()) {
            // If a category filter was provided, prefer the mapping that matches the filter
            // Otherwise, use the first valid mapping
            CategoryItemMapping mapping = null;
            if (categoryFilterSet != null && !categoryFilterSet.isEmpty()) {
                // Find mapping that matches the filter
                for (CategoryItemMapping m : mappings) {
                    if (m.getMenuCategoryMapping() != null && 
                        m.getMenuCategoryMapping().getCategory() != null) {
                        Category category = m.getMenuCategoryMapping().getCategory();
                        UUID mainCategoryId = category.getParentCategory() != null ? 
                                category.getParentCategory().getId() : category.getId();
                        if (categoryFilterSet.contains(mainCategoryId)) {
                            mapping = m;
                            break;
                        }
                    }
                }
            }
            // If no matching mapping found (or no filter), use first valid mapping
            if (mapping == null) {
                for (CategoryItemMapping m : mappings) {
                    if (m.getMenuCategoryMapping() != null && 
                        m.getMenuCategoryMapping().getCategory() != null) {
                        mapping = m;
                        break;
                    }
                }
            }
            
            if (mapping != null && mapping.getMenuCategoryMapping().getCategory() != null) {
                Category category = mapping.getMenuCategoryMapping().getCategory();
                if (category.getParentCategory() != null) {
                    // This is a subcategory
                    subcategoryId = category.getId();
                    subcategoryName = category.getTranslations().stream()
                            .filter(t -> t.getLanguageCode().equals(userLocale.getLanguage()))
                            .map(CategoryTranslation::getName)
                            .findFirst()
                            .orElse(category.getTranslations().isEmpty() ? "" : category.getTranslations().get(0).getName());
                    categoryId = category.getParentCategory().getId();
                    categoryName = category.getParentCategory().getTranslations().stream()
                            .filter(t -> t.getLanguageCode().equals(userLocale.getLanguage()))
                            .map(CategoryTranslation::getName)
                            .findFirst()
                            .orElse(category.getParentCategory().getTranslations().isEmpty() ? "" : 
                                category.getParentCategory().getTranslations().get(0).getName());
                } else {
                    // This is a main category
                    categoryId = category.getId();
                    categoryName = category.getTranslations().stream()
                            .filter(t -> t.getLanguageCode().equals(userLocale.getLanguage()))
                            .map(CategoryTranslation::getName)
                            .findFirst()
                            .orElse(category.getTranslations().isEmpty() ? "" : category.getTranslations().get(0).getName());
                }
            }
        }

        // Get modifiers
        List<OrderedItemModifierResponse> modifiers = new ArrayList<>();
        if (item.getOrderedItemModifiers() != null && !item.getOrderedItemModifiers().isEmpty()) {
            UUID restaurantId = orderNotificationService.getRestaurantIdSafely(order);
            modifiers = buildOrderedItemResponse(
                    item,
                    restaurantId,
                    userLocale,
                    modifiersByOrderedItemId.getOrDefault(item.getId(), Collections.emptyList()))
                    .getOrderedItemModifiers();
        }

        return TicketDashboardResponse.builder()
                .orderedItemId(item.getId())
                .orderId(order.getId())
                .orderNumber(order.getOrderNumber())
                .itemId(item.getItem().getId())
                .itemName(itemName)
                .imageUrl(imageUrl)
                .quantity(item.getQuantity())
                .itemStatus(item.getItemStatus())
                .reason(item.getReason())
                .notes(item.getNotes())
                .orderPlacedAt(order.getCreatedAt())
                .itemCreatedAt(item.getCreatedAt())
                .itemUpdatedAt(item.getUpdatedAt())
                .orderedItemModifiers(modifiers)
                .tableId(order.getRestaurantTable() != null ? order.getRestaurantTable().getId() : null)
                .tableCode(tableCode)
                .tableOrder(tableOrder)
                .rowOrder(rowOrder)
                .sectionId(sectionId)
                .sectionName(sectionName)
                .waiterId(waiterId)
                .waiterName(waiterName)
                .serviceType(order.getOrderType() != null ? order.getOrderType().name() : null)
                .categoryId(categoryId)
                .categoryName(categoryName)
                .subcategoryId(subcategoryId)
                .subcategoryName(subcategoryName)
                .lastStatusChangedBy(lastStatusChangedBy)
                .build();
    }

    /**
     * Converts an {@link OrderedItem} into the detailed ticket view response.
     * <p>
     * Reuses the dashboard conversion for shared fields and adds price calculations derived from persisted order values.
     * </p>
     *
     * @param item       ordered item
     * @param userLocale locale used for translation selection
     * @return ticket details response
     */
    private TicketDetailsResponse convertToTicketDetailsResponse(OrderedItem item, Locale userLocale) {
        Order order = item.getOrder();
        
        // Reuse conversion logic from TicketDashboardResponse
        TicketDashboardResponse dashboardResponse = convertToTicketDashboardResponse(item, userLocale, null);
        
        // Get price calculations
        com.gulfnet.shared_library.model.response.dto.ItemPriceCalculationResult priceResult = calculateItemPriceForExistingOrder(item, item.getOrderedItemModifiers());

        return TicketDetailsResponse.builder()
                .orderedItemId(item.getId())
                .itemId(item.getItem().getId())
                .itemName(dashboardResponse.getItemName())
                .imageUrl(dashboardResponse.getImageUrl())
                .quantity(item.getQuantity())
                .price(priceResult.basePricePerUnit())
                .totalItemAmount(priceResult.totalAmountWithDiscount())
                .itemStatus(item.getItemStatus())
                .reason(dashboardResponse.getReason())
                .notes(item.getNotes())
                .itemCreatedAt(item.getCreatedAt())
                .itemUpdatedAt(item.getUpdatedAt())
                .orderedItemModifiers(dashboardResponse.getOrderedItemModifiers())
                .orderId(order.getId())
                .orderNumber(order.getOrderNumber())
                .orderStatus(order.getOrderStatus())
                .orderType(order.getOrderType())
                .orderPlacedAt(order.getCreatedAt())
                .orderTotalAmount(order.getTotalAmount())
                .tableId(dashboardResponse.getTableId())
                .tableCode(dashboardResponse.getTableCode())
                .tableOrder(dashboardResponse.getTableOrder())
                .rowOrder(dashboardResponse.getRowOrder())
                .sectionId(dashboardResponse.getSectionId())
                .sectionName(dashboardResponse.getSectionName())
                .waiterId(dashboardResponse.getWaiterId())
                .waiterName(dashboardResponse.getWaiterName())
                .lastStatusChangedBy(dashboardResponse.getLastStatusChangedBy())
                .statusChangedAt(item.getUpdatedAt())
                .statusChangedBy(dashboardResponse.getLastStatusChangedBy())
                .build();
    }

    private UUID getSectionIdFromItem(OrderedItem item) {
        if (item.getOrder().getRestaurantTable() != null &&
            item.getOrder().getRestaurantTable().getRestaurantRow() != null &&
            item.getOrder().getRestaurantTable().getRestaurantRow().getRestaurantSection() != null) {
            return item.getOrder().getRestaurantTable().getRestaurantRow().getRestaurantSection().getId();
        }
        return null;
    }

    /**
     * Sorts ticket items for the dashboard based on requested sort field and direction.
     *
     * @param items     items to sort
     * @param sortBy    sort field (defaults to {@code orderPlacedAt})
     * @param direction sort direction (defaults to {@code DESC})
     * @return sorted list
     */
    private List<OrderedItem> sortTicketItems(List<OrderedItem> items, String sortBy, String direction) {
        if (sortBy == null || sortBy.isBlank()) {
            sortBy = "orderPlacedAt";
        }
        if (direction == null || direction.isBlank()) {
            direction = "DESC";
        }

        Comparator<OrderedItem> comparator;
        switch (sortBy.toLowerCase()) {
            case "itemname":
                comparator = Comparator.comparing(i -> i.getItem().getTranslations().isEmpty() ? "" : 
                    i.getItem().getTranslations().get(0).getName());
                break;
            case "quantity":
                comparator = Comparator.comparing(OrderedItem::getQuantity);
                break;
            case "itemstatus":
                comparator = Comparator.comparing(OrderedItem::getItemStatus);
                break;
            case "orderplacedat":
            default:
                comparator = Comparator.comparing(i -> i.getOrder().getCreatedAt(), Comparator.nullsLast(Comparator.naturalOrder()));
                break;
        }

        if ("DESC".equalsIgnoreCase(direction)) {
            comparator = comparator.reversed();
        }

        return items.stream()
                .sorted(comparator)
                .collect(Collectors.toList());
    }

    /**
     * Builds an {@link OrderedItemResponse} including modifier-group aggregation and persisted price calculations.
     *
     * @param orderedItem  ordered item to map
     * @param restaurantId restaurant context (used for modifier/name resolution; may be required by downstream helpers)
     * @param userLocale   locale used for translation selection
     * @return ordered item response DTO
     */
    private OrderedItemResponse buildOrderedItemResponse(
            OrderedItem orderedItem,
            UUID restaurantId,
            Locale userLocale,
            List<OrderedItemModifier> preloadedModifiers) {
        // Use preloaded modifiers when available to avoid N+1 queries in dashboard mapping.
        List<OrderedItemModifier> modifiers = preloadedModifiers != null
                ? preloadedModifiers
                : orderedItemModifierRepository.findByOrderedItemId(orderedItem.getId());
        
        // Group modifiers by modifier group to avoid repeating the same group
        Map<UUID, List<OrderedItemModifier>> modifiersByGroup = modifiers.stream()
            .collect(Collectors.groupingBy(m -> m.getModifierGroup().getId()));

        List<OrderedItemModifierResponse> modifierResponses = modifiersByGroup.entrySet().stream()
            .map(entry -> {
                UUID groupId = entry.getKey();
                List<OrderedItemModifier> groupModifiers = entry.getValue();

                // All in the group share the same group
                ModifierGroup group = groupModifiers.get(0).getModifierGroup();
                String groupName = group.getTranslations().stream()
                        .filter(t -> t.getLanguageCode().equals(userLocale.getLanguage()))
                        .findFirst()
                        .map(ModifierGroupTranslation::getName)
                        .orElse(group.getTranslations().isEmpty() ? "Modifier Group" : group.getTranslations().get(0).getName());

                List<ModifierItemResponse> modifierItemResponses = groupModifiers.stream()
                    .map(mod -> ModifierItemResponse.builder()
                        .id(mod.getId())
                        .modifierItemId(mod.getModifierItem().getId())
                        .modifierItemName(mod.getModifierItem().getTranslations().stream()
                                .filter(t -> t.getLanguageCode().equals(userLocale.getLanguage()))
                                .findFirst()
                                .map(ModifierItemTranslation::getName)
                                .orElse(mod.getModifierItem().getTranslations().isEmpty() ? 
                                    "Modifier Item" : mod.getModifierItem().getTranslations().get(0).getName()))
                        .price(CurrencyFormatter.formatAmount(mod.getPrice(), restaurantChainConfigProperties.getChain().getCurrency()))
                        .build())
                    .collect(Collectors.toList());

                return OrderedItemModifierResponse.builder()
                    .modifierGroupId(groupId)
                    .modifierGroupName(groupName)
                    .modifierItems(modifierItemResponses)
                    .build();
            })
            .collect(Collectors.toList());

        // Use unified price calculation method for existing orders
        com.gulfnet.shared_library.model.response.dto.ItemPriceCalculationResult priceResult = calculateItemPriceForExistingOrder(orderedItem, modifiers);
        
        OrderedItemResponse.OrderedItemResponseBuilder responseBuilder = OrderedItemResponse.builder()
                .id(orderedItem.getId())
                .itemId(orderedItem.getItem().getId())
                .alcoholType(orderedItem.getAlcoholType() != null ? orderedItem.getAlcoholType() : orderedItem.getItem().getAlcoholType())
                .quantity(orderedItem.getQuantity())
                .price(priceResult.basePricePerUnit())
                .discountedPrice(priceResult.discountedPricePerUnit())
                .totalItemAmount(priceResult.totalAmountWithoutDiscount())
                .totalDiscountedItemAmount(priceResult.totalAmountWithDiscount())
                .orderedItemModifiers(modifierResponses);
        
        // Add BXGY fields if not null
        if (orderedItem.getBxgyRole() != null) {
            responseBuilder.bxgyRole(orderedItem.getBxgyRole());
        }
        if (orderedItem.getDiscountApplicationId() != null) {
            responseBuilder.discountApplicationId(orderedItem.getDiscountApplicationId());
        }
        if (orderedItem.getDiscountId() != null) {
            responseBuilder.discountId(orderedItem.getDiscountId());
        }
        if (orderedItem.getFreeQuantity() != null) {
            responseBuilder.freeQuantity(orderedItem.getFreeQuantity());
        }
        
        return responseBuilder.build();
    }

    /**
     * Calculates per-unit and total item prices for an existing order using persisted database values when available.
     * <p>
     * Prefers {@code OrderedItem.price/discountedPrice/totalItemAmount/totalDiscountedItemAmount}. When legacy rows
     * do not contain totals, it falls back to computing totals using modifiers and base price, applying currency
     * formatting/rounding rules.
     * </p>
     *
     * @param orderedItem ordered item with persisted pricing fields
     * @param modifiers   modifiers for the ordered item (used for fallback calculations)
     * @return price calculation result with base/discounted per-unit price and totals
     * @throws IllegalArgumentException when quantity is zero or negative
     */
    private com.gulfnet.shared_library.model.response.dto.ItemPriceCalculationResult calculateItemPriceForExistingOrder(OrderedItem orderedItem, 
            List<OrderedItemModifier> modifiers) {
        if (orderedItem.getQuantity() <= 0) {
            throw new IllegalArgumentException("Quantity must be greater than zero for price calculation.");
        }

        // Get currency for formatting
        String currency = restaurantChainConfigProperties.getChain().getCurrency();
        
        // Use database values directly from OrderedItem entity
        // Base price per unit - use stored price from database, fallback to Item.basePrice for legacy data
        BigDecimal basePricePerUnit;
        if (orderedItem.getPrice() != null) {
            basePricePerUnit = CurrencyFormatter.formatAmount(orderedItem.getPrice(), currency);
        } else {
            // Fallback for legacy data
            basePricePerUnit = CurrencyFormatter.formatAmount(
                BigDecimal.valueOf(orderedItem.getItem().getBasePrice()), 
                currency);
        }

        // Discounted price per unit - use stored discountedPrice from database
        // If null, it means no discount was applied (per requirements)
        BigDecimal discountedPricePerUnit = null;
        if (orderedItem.getDiscountedPrice() != null) {
            discountedPricePerUnit = CurrencyFormatter.formatAmount(orderedItem.getDiscountedPrice(), currency);
        }
        // If discountedPrice is null, it means no discount was applied, so keep it as null

        // Total amount without discount - use stored totalItemAmount from database
        BigDecimal totalAmountWithoutDiscount;
        if (orderedItem.getTotalItemAmount() != null) {
            totalAmountWithoutDiscount = CurrencyFormatter.formatAmount(orderedItem.getTotalItemAmount(), currency);
        } else {
            // Fallback: calculate from base price and modifiers
            BigDecimal quantity = BigDecimal.valueOf(orderedItem.getQuantity());
            BigDecimal perUnitModifierPrice = modifiers.stream()
                .map(OrderedItemModifier::getPrice)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
            perUnitModifierPrice = CurrencyFormatter.formatAmount(perUnitModifierPrice, currency);
            totalAmountWithoutDiscount = CurrencyFormatter.formatAmount(
                basePricePerUnit.add(perUnitModifierPrice).multiply(quantity), 
                currency);
        }

        // Total amount with discount - use stored totalDiscountedItemAmount from database
        // If null, it means no discount was applied (per requirements)
        BigDecimal totalAmountWithDiscount = null;
        if (orderedItem.getTotalDiscountedItemAmount() != null) {
            totalAmountWithDiscount = CurrencyFormatter.formatAmount(orderedItem.getTotalDiscountedItemAmount(), currency);
        }
        // If totalDiscountedItemAmount is null, it means no discount was applied, so keep it as null

        return new com.gulfnet.shared_library.model.response.dto.ItemPriceCalculationResult(
            basePricePerUnit,
            discountedPricePerUnit,
            totalAmountWithoutDiscount,
            totalAmountWithDiscount
        );
    }
    
    /**
     * Helper method to check if an item matches any category in the filter.
     * This reduces the number of break/continue statements in the calling loop.
     */
    private boolean checkCategoryMatch(List<CategoryItemMapping> mappings, Collection<UUID> categoryFilter, OrderedItem item) {
        for (CategoryItemMapping mapping : mappings) {
            MenuCategoryMapping menuCategoryMapping = mapping.getMenuCategoryMapping();
            Category category = null;
            boolean skip = false;
            if (menuCategoryMapping == null) {
                log.warn("CategoryItemMapping {} has null MenuCategoryMapping for item {}", mapping.getId(), item.getItem().getId());
                skip = true;
            } else {
                category = menuCategoryMapping.getCategory();
                if (category == null) {
                    log.warn("CategoryItemMapping {} has null Category for item {} (MenuCategoryMapping: {})",
                            mapping.getId(), item.getItem().getId(), menuCategoryMapping.getId());
                    skip = true;
                }
            }
            if (skip) {
                continue;
            }

            UUID itemCategoryId = category.getId();
            // Check if it's a main category (no parent) or if parent matches
            if (category.getParentCategory() == null) {
                // Main category
                if (categoryFilter.contains(itemCategoryId)) {
                    UUID menuId = menuCategoryMapping.getMenu() != null ? menuCategoryMapping.getMenu().getId() : null;
                    log.debug("Item {} matches category filter (main category: {} from menu: {})", 
                            item.getItem().getId(), itemCategoryId, menuId);
                    return true;
                }
            } else {
                // Subcategory - check parent category
                UUID parentCategoryId = category.getParentCategory().getId();
                if (categoryFilter.contains(parentCategoryId)) {
                    UUID menuId = menuCategoryMapping.getMenu() != null ? menuCategoryMapping.getMenu().getId() : null;
                    log.debug("Item {} matches category filter (parent category: {} from menu: {})", 
                            item.getItem().getId(), parentCategoryId, menuId);
                    return true;
                }
            }
        }
        return false;
    }
}

