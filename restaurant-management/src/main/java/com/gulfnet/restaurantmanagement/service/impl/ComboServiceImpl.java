package com.gulfnet.restaurantmanagement.service.impl;

import com.gulfnet.shared_library.entity.*;
import com.gulfnet.shared_library.enums.ComboType;
import com.gulfnet.shared_library.enums.ComboGroupType;
import com.gulfnet.shared_library.enums.DayOfWeek;
import com.gulfnet.shared_library.enums.EntityStatus;
import com.gulfnet.shared_library.enums.ItemOrderType;
import com.gulfnet.shared_library.enums.AlcoholType;
import com.gulfnet.shared_library.model.request.ComboRequest;
import com.gulfnet.shared_library.model.response.dto.*;
import com.gulfnet.shared_library.repository.*;
import com.gulfnet.restaurantmanagement.service.ComboService;
import com.gulfnet.restaurantmanagement.util.MessageUtil;
import com.gulfnet.restaurantmanagement.config.LocalizationProperties;
import com.gulfnet.shared_library.util.TranslationUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.context.i18n.LocaleContextHolder;
import jakarta.persistence.EntityManager;

import java.time.LocalDateTime;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.OffsetTime;
import java.time.ZoneOffset;
import java.util.*;
import java.util.stream.Collectors;
import java.util.HashSet;
import java.util.Set;
import com.gulfnet.shared_library.repository.MenuCategoryMappingRepository;
import com.gulfnet.shared_library.repository.MenuCategoryComboMappingRepository;
import java.util.Collections;
import com.gulfnet.shared_library.entity.ComboTranslation;
import com.gulfnet.shared_library.model.response.dto.ComboListResponse;
import com.gulfnet.shared_library.model.response.dto.PaginationMetaData;
import com.gulfnet.shared_library.util.LocaleSortUtil;
import com.gulfnet.shared_library.util.CurrencyFormatter;
import com.gulfnet.restaurantmanagement.config.RestaurantChainConfigProperties;
import com.gulfnet.shared_library.config.AWSService;
import java.math.BigDecimal;

@Slf4j
@Service
public class ComboServiceImpl implements ComboService {

    // Constants
    private static final String MSG_COMBO_NOT_FOUND = "combo.not.found";
    private static final String MSG_COMBO_ALREADY_DELETED = "combo.already.deleted";
    private static final String COMBO_TYPE_FIXED = "FIXED";
    private static final String COMBO_TYPE_CHOICE = "CHOICE";

    @Autowired
    private ComboRepository comboRepository;

    @Autowired
    private ComboGroupRepository comboGroupRepository;

    @Autowired
    private ComboItemMappingRepository comboItemMappingRepository;

    @Autowired
    private ComboTranslationRepository comboTranslationRepository;

    @Autowired
    private ComboGroupTranslationRepository comboGroupTranslationRepository;

    @Autowired
    private MenuRepository menuRepository;

    @Autowired
    private CategoryItemMappingRepository categoryItemMappingRepository;

    @Autowired
    private ItemRepository itemRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private MessageUtil messageUtil;

    @Autowired
    private LocalizationProperties localizationProperties;

    @Autowired
    private MenuCategoryMappingRepository menuCategoryMappingRepository;
    
    @Autowired
    private MenuCategoryComboMappingRepository menuCategoryComboMappingRepository;
    
    @Autowired
    private com.gulfnet.shared_library.repository.PromotionMenuComboMappingRepository promotionMenuComboMappingRepository;
    
    @Autowired
    private ComboItemModifierRepository comboItemModifierRepository;
    @Autowired
    private ModifierItemRepository modifierItemRepository;
    @Autowired
    private EntityManager entityManager;

    @Autowired
    private AWSService awsService;

    @Autowired
    private RestaurantChainConfigProperties restaurantChainConfigProperties;

    @Autowired
    private RestaurantItemAvailabilityRepository restaurantItemAvailabilityRepository;

    @Autowired
    private CategoryRepository categoryRepository;


    @Override
    @Transactional
    /**
     * Creates a new combo for a menu, including its groups, item mappings, modifiers, and translations.
     * <p>
     * Validates required fields (including category id), validates combo-type specific rules (group types, default item
     * rules, translations, UTC date/time constraints), then persists:
     * </p>
     * <ul>
     *   <li>The {@link Combo} entity (with validity window and scheduling fields normalized to UTC)</li>
     *   <li>{@link ComboGroup} entities and their {@link ComboItemMapping} entries</li>
     *   <li>{@link ComboItemModifier} entries for any provided modifier item ids</li>
     *   <li>Combo and group translations</li>
     *   <li>Menu/category assignment via category id</li>
     * </ul>
     *
     * @param userId acting user id (string UUID) used for created-by metadata
     * @param request create payload describing combo structure, availability window, and translations
     * @param locale locale/language tag used for localized messages and translation selection
     * @return response containing the created combo details
     * @throws ResponseStatusException when validation fails or referenced entities cannot be found
     */
    public ResponseDto<ComboDto<ComboResponse>> createCombo(String userId, ComboRequest request, String locale) {
        log.info("Creating combo with type: {}", request.getType());
        
        // Set locale context
        LocaleContextHolder.setLocale(new Locale(locale));
        
        // Validate categoryId is provided
        if (request.getCategoryId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, 
                    messageUtil.getMessage("combo.categoryId.required", null, locale));
        }
        
        // Validate menu exists
        Menu menu = menuRepository.findById(request.getMenuId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Menu not found"));
        
        // Validate combo type specific rules
        validateComboTypeRules(request, locale, null, false);
        
        // Create combo entity
        User user = userRepository.findById(UUID.fromString(userId))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
        
        Combo combo = Combo.builder()
                .menu(menu)
                .type(ComboType.valueOf(request.getType()))
                .basePrice(request.getBasePrice())
                .comboImageUrl(awsService.stripToKey(request.getComboImageUrl()))
                .status(EntityStatus.valueOf(request.getStatus()))
                .itemOrderType(request.getItemOrderType())
                .validFrom(convertToUtc(request.getValidFrom()))
                .validTo(convertToUtc(request.getValidTo()))
                .startTime(request.getStartTime() != null ? request.getStartTime().withOffsetSameInstant(ZoneOffset.UTC) : null)
                .endTime(request.getEndTime() != null ? request.getEndTime().withOffsetSameInstant(ZoneOffset.UTC) : null)
                .daysOfWeek(request.getDaysOfWeek())
                .isDeleted(false)
                .createdAt(OffsetDateTime.now(ZoneOffset.UTC))
                .createdBy(user)
                .build();
        
        Combo savedCombo = comboRepository.save(combo);
        
        createComboGroupsAndMappings(savedCombo, request, locale);
        createComboTranslations(savedCombo, request);
        createMenuCategoryComboMapping(savedCombo, request, locale);
        entityManager.flush();

        ComboResponse comboResponse = buildComboResponse(savedCombo.getComboId(), locale);
        
        return ResponseDto.<ComboDto<ComboResponse>>builder()
                .data(ComboDto.<ComboResponse>builder().combo(comboResponse).build())
                .message(messageUtil.getMessage("combo.created.success", null, locale))
                .build();
    }

    @Override
    @Transactional
    /**
     * Updates an existing combo and replaces its structure/translations.
     * <p>
     * Validates combo existence and that it is not deleted, ensures it belongs to the requested menu, validates the
     * updated structure via {@link #validateComboTypeRules(ComboRequest, String)}, then updates the combo and fully
     * replaces dependent data by deleting existing translations/groups/mappings/modifiers before recreating them from
     * the request.
     * </p>
     *
     * @param comboId combo identifier to update
     * @param userId  acting user id (string UUID) used for updated-by metadata
     * @param request updated combo payload (structure, availability fields, translations)
     * @param locale  locale/language tag used for localized messages and translation selection
     * @return response containing the updated combo details
     * @throws ResponseStatusException when validation fails or referenced entities cannot be found
     */
    public ResponseDto<ComboDto<ComboResponse>> updateCombo(UUID comboId, String userId, ComboRequest request, String locale) {
        log.info("Updating combo with ID: {} and type: {}", comboId, request.getType());
        
        // Set locale context
        LocaleContextHolder.setLocale(new Locale(locale));
        
        // Validate categoryId is provided
        if (request.getCategoryId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, 
                    messageUtil.getMessage("combo.categoryId.required", null, locale));
        }
        
        // Validate combo exists, is active, not deleted, and assigned to the specified menu
        Combo existingCombo = comboRepository.findById(comboId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, 
                    messageUtil.getMessage(MSG_COMBO_NOT_FOUND, null, locale)));
        
        // Check if combo is deleted
        if (Boolean.TRUE.equals(existingCombo.getIsDeleted())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, 
                messageUtil.getMessage(MSG_COMBO_ALREADY_DELETED, null, locale));
        }

        
        // Check if combo is assigned to the specified menu
        if (!existingCombo.getMenu().getId().equals(request.getMenuId())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, 
                messageUtil.getMessage("combo.not.assigned.to.menu", null, locale));
        }
        
        // Validate menu exists
        Menu menu = menuRepository.findById(request.getMenuId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, 
                    messageUtil.getMessage("menu.not.found", null, locale)));
        
        // Validate combo type specific rules.
        // On update, allow validFrom in the past; only enforce validFrom <= validTo.
        validateComboTypeRules(request, locale, existingCombo.getValidFrom(), true);
        
        // Get user for updatedBy
        User user = userRepository.findById(UUID.fromString(userId))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, 
                    messageUtil.getMessage("user.not.found", null, locale)));
        
        // Update combo entity
        existingCombo.setMenu(menu);
        existingCombo.setType(ComboType.valueOf(request.getType()));
        existingCombo.setBasePrice(request.getBasePrice());
        existingCombo.setComboImageUrl(awsService.stripToKey(request.getComboImageUrl()));
        existingCombo.setStatus(EntityStatus.valueOf(request.getStatus()));
        existingCombo.setItemOrderType(request.getItemOrderType());
        existingCombo.setValidFrom(convertToUtc(request.getValidFrom()));
        existingCombo.setValidTo(convertToUtc(request.getValidTo()));
        existingCombo.setStartTime(request.getStartTime() != null ? request.getStartTime().withOffsetSameInstant(ZoneOffset.UTC) : null);
        existingCombo.setEndTime(request.getEndTime() != null ? request.getEndTime().withOffsetSameInstant(ZoneOffset.UTC) : null);
        existingCombo.setDaysOfWeek(request.getDaysOfWeek());
        existingCombo.setUpdatedAt(OffsetDateTime.now(ZoneOffset.UTC));
        existingCombo.setUpdatedBy(user);
        
        // Delete existing combo translations first
        comboTranslationRepository.deleteByComboComboId(existingCombo.getComboId());
        
        // Flush to ensure translations are deleted before proceeding
        entityManager.flush();
        
        // Delete existing combo groups and related entities in proper order
        List<ComboGroup> existingGroups = comboRepository.findComboGroupsByComboId(existingCombo.getComboId());
        
        for (ComboGroup group : existingGroups) {
            // Get all combo item mappings for this group
            List<ComboItemMapping> existingMappings = comboRepository.findComboItemMappingsWithItems(existingCombo.getComboId());
            List<ComboItemMapping> groupMappings = existingMappings.stream()
                .filter(mapping -> mapping.getComboGroup().getComboGroupId().equals(group.getComboGroupId()))
                .collect(Collectors.toList());
            
            // Delete combo item modifiers first
            for (ComboItemMapping mapping : groupMappings) {
                List<ComboItemModifier> modifiers = comboItemModifierRepository.findByComboItemMappingId(mapping.getId());
                for (ComboItemModifier modifier : modifiers) {
                    comboItemModifierRepository.delete(modifier);
                }
            }
            
            // Then delete combo item mappings
            for (ComboItemMapping mapping : groupMappings) {
                comboItemMappingRepository.delete(mapping);
            }
            
            // Delete combo group translations
            List<ComboGroupTranslation> groupTranslations = comboRepository.findComboGroupTranslationsByComboId(existingCombo.getComboId());
            List<ComboGroupTranslation> groupTranslationsToDelete = groupTranslations.stream()
                .filter(translation -> translation.getComboGroup().getComboGroupId().equals(group.getComboGroupId()))
                .collect(Collectors.toList());
            
            for (ComboGroupTranslation translation : groupTranslationsToDelete) {
                comboGroupTranslationRepository.delete(translation);
            }
            
            // Finally delete the combo group
            comboGroupRepository.delete(group);
        }
        
        Combo savedCombo = comboRepository.save(existingCombo);
        
        // Create new combo groups and item mappings
        for (ComboRequest.ComboGroupRequest groupRequest : request.getComboGroups()) {
            ComboGroup comboGroup = ComboGroup.builder()
                    .combo(savedCombo)
                    .groupType(ComboGroupType.valueOf(groupRequest.getGroupType()))
                    .minSelect(groupRequest.getMinSelect())
                    .maxSelect(groupRequest.getMaxSelect())
                    .build();
            
            ComboGroup savedGroup = comboGroupRepository.save(comboGroup);
            
            // Create item mappings
            for (ComboRequest.ComboGroupRequest.ComboItemRequest itemRequest : groupRequest.getItems()) {
                // Find the CategoryItemMapping for this item in the specified menu
                CategoryItemMapping categoryItemMapping = categoryItemMappingRepository
                        .findByItemIdAndMenuCategoryMappingMenuId(itemRequest.getItemId(), request.getMenuId())
                        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, 
                            messageUtil.getMessage("item.not.found.in.menu", null, locale)));
                
                // Determine isDefault based on combo type and group type
                boolean isDefault = determineIsDefault(request.getType(), groupRequest.getGroupType(), itemRequest.getDefaultItem());
                
                ComboItemMapping comboItemMapping = ComboItemMapping.builder()
                        .comboGroup(savedGroup)
                        .categoryItemMapping(categoryItemMapping)
                        .isDefault(isDefault)
                        .build();
                
                ComboItemMapping savedComboItemMapping = comboItemMappingRepository.save(comboItemMapping);
                
                // Create modifier item mappings if provided
                List<UUID> modifierIds = new ArrayList<>();
                
                // Handle modifier items from the array
                if (itemRequest.getModifierItemId() != null && !itemRequest.getModifierItemId().isEmpty()) {
                    modifierIds.addAll(itemRequest.getModifierItemId());
                }
                
                // Remove duplicates
                modifierIds = modifierIds.stream().distinct().collect(Collectors.toList());
                
                // Create modifier mappings
                for (UUID modifierId : modifierIds) {
                    ModifierItem modifierItem = modifierItemRepository.findByIdAndIsDeletedFalse(modifierId)
                            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, 
                                messageUtil.getMessage("modifier.item.name.not.found", null, locale)));
                    
                    ComboItemModifier comboItemModifier = ComboItemModifier.builder()
                            .comboItemMapping(savedComboItemMapping)
                            .modifierItem(modifierItem)
                            .build();
                    
                    comboItemModifierRepository.save(comboItemModifier);
                }
            }
            
            // Create group translations
            for (ComboGroupTranslationDto translationDto : groupRequest.getTranslations()) {
                ComboGroupTranslation translation = ComboGroupTranslation.builder()
                        .comboGroup(savedGroup)
                        .languageCode(translationDto.getLanguageCode())
                        .groupName(translationDto.getGroupName())
                        .build();
                
                comboGroupTranslationRepository.save(translation);
            }
        }
        
        // Create combo translations
        for (ComboTranslationDto translationDto : request.getTranslations()) {
            ComboTranslation translation = ComboTranslation.builder()
                    .combo(savedCombo)
                    .languageCode(translationDto.getLanguageCode())
                    .name(translationDto.getName())
                    .description(translationDto.getDescription())
                    .build();
            
            comboTranslationRepository.save(translation);
        }
        
        // ==================== UPDATE MENU CATEGORY / COMBO MAPPING ====================
        // Validate category exists (categoryId is now required)
        Category category = categoryRepository.findByIdAndIsDeletedFalse(request.getCategoryId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, 
                        messageUtil.getMessage("category.not.found", null, locale)));
        
        // Validate that the category is a combo type category
        if (category.getIsCombo() == null || !category.getIsCombo()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, 
                    messageUtil.getMessage("combo.category.must.be.combo.type", null, locale));
        }
        
        // Find the menu_category_mapping for the given menuId and categoryId
        MenuCategoryMapping menuCategoryMapping = menuCategoryMappingRepository
                .findByMenuIdAndCategoryId(request.getMenuId(), request.getCategoryId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, 
                        messageUtil.getMessage("category.menu.mismatch", null, locale)));
        
        // Find existing mappings for this combo (there should typically be at most one)
        List<MenuCategoryComboMapping> existingMenuCategoryComboMappings =
                menuCategoryComboMappingRepository.findByCombo_ComboId(comboId);
        
        // Create the new mapping for the requested menu/category
        MenuCategoryComboMapping newMenuCategoryComboMapping = MenuCategoryComboMapping.builder()
                .combo(savedCombo)
                .menuCategoryMapping(menuCategoryMapping)
                .build();
        newMenuCategoryComboMapping = menuCategoryComboMappingRepository.save(newMenuCategoryComboMapping);
        log.info("Created new menu category combo mapping {} for combo {} with categoryId {}",
                newMenuCategoryComboMapping.getId(), comboId, request.getCategoryId());
        
        // If there are existing mappings, safely re-point any promotion mappings to the new mapping
        if (existingMenuCategoryComboMappings != null && !existingMenuCategoryComboMappings.isEmpty()) {
            for (MenuCategoryComboMapping oldMapping : existingMenuCategoryComboMappings) {
                List<com.gulfnet.shared_library.entity.PromotionMenuComboMapping> promoMappings =
                        promotionMenuComboMappingRepository.findByMenuCategoryComboMapping_Id(oldMapping.getId());
                
                for (com.gulfnet.shared_library.entity.PromotionMenuComboMapping promoMapping : promoMappings) {
                    promoMapping.setMenuCategoryComboMapping(newMenuCategoryComboMapping);
                }
                
                // After promotions are updated, delete the old mapping
                menuCategoryComboMappingRepository.delete(oldMapping);
                log.info("Deleted old menu category combo mapping {} for combo {}", oldMapping.getId(), comboId);
            }
        }
        
        entityManager.flush();
        ComboResponse comboResponse = buildComboResponse(savedCombo.getComboId(), locale);
        
        return ResponseDto.<ComboDto<ComboResponse>>builder()
                .data(ComboDto.<ComboResponse>builder().combo(comboResponse).build())
                .message(messageUtil.getMessage("combo.update.success", null, locale))
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    /**
     * Lists combos for a menu with optional filters and localized display.
     * <p>
     * Supports filtering by status, type, and search text. When {@code isAvailable=true}, the method further filters
     * combos to those currently available based on validity window (UTC), daily hours, days-of-week, and structural
     * availability (items active/in-stock and choice groups having at least one available option).
     * </p>
     *
     * @param menuId       menu identifier
     * @param page         1-based page number (optional; defaults to 1)
     * @param size         page size (optional; defaults to unpaged)
     * @param status       optional {@link EntityStatus} filter (string)
     * @param type         optional {@link ComboType} filter (string)
     * @param search       optional search term
     * @param isAvailable  when true, only returns combos that are currently available
     * @param sortBy       optional sort field (handled downstream)
     * @param direction    optional sort direction (handled downstream)
     * @param locale       locale/language tag used for translation selection
     * @param restaurantId restaurant context for item availability checks (optional)
     * @return paginated list response of combos
     * @throws ResponseStatusException when filter values are invalid or menu cannot be found
     */
    public ResponseDto<ComboListResponse> getCombos(
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
        UUID restaurantId) {
    
        Locale userLocale = LocaleContextHolder.getLocale();

        // Validate menu exists
        Menu menu = menuRepository.findById(menuId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        messageUtil.getMessage("menu.not.found", userLocale)));

        // Validate and set pagination
        int pageNumber = (page != null ? page : 1) - 1;
        if (pageNumber < 0) pageNumber = 0;
        int pageSize = size != null ? size : Integer.MAX_VALUE;
        if (pageSize < 1) pageSize = Integer.MAX_VALUE;

        // Process status filter
        // If isAvailable is true, default to ACTIVE. Otherwise, return all statuses (including INACTIVE)
        String statusValue = null;
        if (status != null && !status.isEmpty()) {
            try {
                EntityStatus statusEnum = EntityStatus.valueOf(status.toUpperCase());
                statusValue = statusEnum.name();
            } catch (IllegalArgumentException e) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        messageUtil.getMessage("combo.error.invalid.status", userLocale));
            }
        } else if (Boolean.TRUE.equals(isAvailable)) {
            // Default to ACTIVE only when isAvailable is true and status is not explicitly provided
            statusValue = EntityStatus.ACTIVE.name();
        }

        // Process type filter
        String typeValue = null;
        if (type != null && !type.isEmpty()) {
            try {
                ComboType typeEnum = ComboType.valueOf(type.toUpperCase());
                typeValue = typeEnum.name();
            } catch (IllegalArgumentException e) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        messageUtil.getMessage("combo.type.invalid", userLocale));
            }
        }

        // Get filtered combos from database for specific menu (only ACTIVE by default)
        List<Combo> combos = comboRepository.findCombosByMenuWithFilters(
                menuId,
                statusValue,
                typeValue,
                search,
                locale
        );

        // Filter by availability only if isAvailable parameter is true
        // If isAvailable is null or not provided, return all combos regardless of availability
        if (Boolean.TRUE.equals(isAvailable)) {
            // Note: User timezone should be retrieved from request headers or user profile in future enhancement
            ZoneOffset userTimezone = ZoneOffset.UTC; // Default to UTC for now
            combos = filterAvailableCombos(combos, userLocale, userTimezone);
        }

        // Convert to response DTOs with locale-specific translations
        List<ComboResponse> comboResponses = combos.stream()
                .map(combo -> {
                    // Get signed image URL
                    String signedImageUrl = null;
                    if (combo.getComboImageUrl() != null) {
                        signedImageUrl = awsService.getPreSignedUrl(combo.getComboImageUrl());
                    }

                    // Apply fallback language logic for combo translations
                    String comboName = "";
                    String comboDescription = "";
                    String selectedLanguageCode = locale;
                    List<ComboTranslation> translations = combo.getTranslations();
                    
                    if (!translations.isEmpty()) {
                        java.util.Optional<ComboTranslation> selected =
                                TranslationUtils.pickPreferredOrFromListNonBlank(
                                        translations,
                                        locale,
                                        localizationProperties.getLanguages(),
                                        ComboTranslation::getLanguageCode,
                                        ComboTranslation::getName);
                        if (selected.isPresent()) {
                            ComboTranslation t = selected.get();
                            comboName = t.getName();
                            comboDescription = t.getDescription();
                            selectedLanguageCode = t.getLanguageCode();
                        }
                    }
                    
                    // Build translation DTOs for display (only the selected translation)
                    List<ComboTranslationDto> translationResponses = List.of(
                        ComboTranslationDto.builder()
                            .languageCode(selectedLanguageCode)
                            .name(comboName)
                            .description(comboDescription)
                            .build()
                    );

                    // Fetch combo groups and items with modifiers for this combo
                    List<ComboGroup> comboGroups = comboRepository.findComboGroupsByComboId(combo.getComboId());
                    List<ComboItemMapping> comboItemMappings = comboRepository.findComboItemMappingsWithItems(combo.getComboId());
                    List<ComboItemModifier> comboItemModifiers = comboItemModifierRepository.findComboItemModifiersByComboId(combo.getComboId());
                    List<ComboGroupTranslation> comboGroupTranslations = comboRepository.findComboGroupTranslationsByComboId(combo.getComboId());
                    
                    // Create maps for quick lookup
                    Map<UUID, List<ComboItemMapping>> groupItemsMap = comboItemMappings.stream()
                        .collect(Collectors.groupingBy(cim -> cim.getComboGroup().getComboGroupId()));
                    
                    // Create map of combo item mapping ID to modifiers
                    Map<UUID, List<ComboItemModifier>> itemModifiersMap = comboItemModifiers.stream()
                        .collect(Collectors.groupingBy(cim -> cim.getComboItemMapping().getId()));
                    
                    Map<UUID, List<ComboGroupTranslation>> groupTranslationsMap = comboGroupTranslations.stream()
                        .collect(Collectors.groupingBy(cgt -> cgt.getComboGroup().getComboGroupId()));
                    
                    // Build combo groups response
                    List<ComboResponse.ComboGroupResponse> groupResponses = comboGroups.stream()
                        .map(group -> {
                            List<ComboItemMapping> groupItems = groupItemsMap.getOrDefault(group.getComboGroupId(), Collections.emptyList());
                            List<ComboGroupTranslation> groupTranslations = groupTranslationsMap.getOrDefault(group.getComboGroupId(), Collections.emptyList());
                            
                            return ComboResponse.ComboGroupResponse.builder()
                                .comboGroupId(group.getComboGroupId())
                                .groupType(group.getGroupType())
                                .minSelect(group.getMinSelect())
                                .maxSelect(group.getMaxSelect())
                                .items(groupItems.stream()
                                        .map(itemMapping -> {
                                            // Get modifiers for this item mapping
                                            List<ComboItemModifier> itemModifiers = itemModifiersMap.getOrDefault(itemMapping.getId(), Collections.emptyList());
                                            
                                            ComboResponse.ComboGroupResponse.ComboItemResponse.ComboItemResponseBuilder builder = 
                                                ComboResponse.ComboGroupResponse.ComboItemResponse.builder()
                                                    .itemId(itemMapping.getCategoryItemMapping().getItem().getId());
                                            
                                            // Add modifier item information if present
                                            if (!itemModifiers.isEmpty()) {
                                                // Set multiple modifier information
                                                List<UUID> modifierIds = itemModifiers.stream()
                                                        .map(modifier -> modifier.getModifierItem().getId())
                                                        .collect(Collectors.toList());
                                                builder.modifierItemId(modifierIds);
                                                
                                                // Build modifier items list
                                                List<ComboResponse.ComboGroupResponse.ComboItemResponse.ModifierItemInfo> modifierItemsList = 
                                                        itemModifiers.stream()
                                                                .map(modifier -> {
                                                                    ModifierItem modifierItem = modifier.getModifierItem();
                                                                    String modifierName = "";
                                                                    String modifierDescription = "";
                                                                    
                                                                    if (modifierItem.getTranslations() != null && !modifierItem.getTranslations().isEmpty()) {
                                                                        ModifierItemTranslation translation = modifierItem.getTranslations().get(0);
                                                                        modifierName = translation.getName();
                                                                        modifierDescription = translation.getDescription();
                                                                    }
                                                                    
                                                                    return ComboResponse.ComboGroupResponse.ComboItemResponse.ModifierItemInfo.builder()
                                                                            .modifierItemId(modifierItem.getId())
                                                                            .modifierItemName(modifierName)
                                                                            .modifierItemDescription(modifierDescription)
                                                                            .modifierItemPrice(modifierItem.getPrice())
                                                                            .build();
                                                                })
                                                                .collect(Collectors.toList());
                                                builder.modifierItems(modifierItemsList);
                                            }
                                            
                                            return builder.build();
                                        })
                                    .collect(Collectors.toList()))
                                .translations(groupTranslations.stream()
                                        .map(translation -> ComboGroupTranslationDto.builder()
                                            .languageCode(translation.getLanguageCode())
                                            .groupName(translation.getGroupName())
                                            .build())
                                    .collect(Collectors.toList()))
                                .build();
                        })
                        .collect(Collectors.toList());

                    // Calculate combo availability
                    boolean comboIsAvailable = restaurantId == null
                            || isComboAvailableForRestaurant(combo, restaurantId, comboGroups, comboItemMappings, groupItemsMap);

                    String currency = restaurantChainConfigProperties.getChain() != null ? restaurantChainConfigProperties.getChain().getCurrency() : null;
                    return ComboResponse.builder()
                            .comboId(combo.getComboId())
                            .menuId(combo.getMenu().getId())
                            .type(combo.getType())
                            .basePrice(combo.getBasePrice() != null ? CurrencyFormatter.formatAmount(combo.getBasePrice(), currency) : null)
                            .comboImageUrl(signedImageUrl)
                            .status(combo.getStatus())
                            .itemOrderType(combo.getItemOrderType())
                            .isAvailable(comboIsAvailable)
                .validFrom(combo.getValidFrom() != null ? combo.getValidFrom().withOffsetSameInstant(ZoneOffset.UTC) : null)
                .validTo(combo.getValidTo() != null ? combo.getValidTo().withOffsetSameInstant(ZoneOffset.UTC) : null)
                .startTime(combo.getStartTime() != null ? combo.getStartTime().withOffsetSameInstant(ZoneOffset.UTC) : null)
                .endTime(combo.getEndTime() != null ? combo.getEndTime().withOffsetSameInstant(ZoneOffset.UTC) : null)
                            .daysOfWeek(combo.getDaysOfWeek())
                            .comboGroups(groupResponses)
                            .createdAt(combo.getCreatedAt() != null ? combo.getCreatedAt().toLocalDateTime() : null)
                            .createdBy(formatUserFullName(combo.getCreatedBy()))
                            .updatedAt(combo.getUpdatedAt() != null ? combo.getUpdatedAt().toLocalDateTime() : null)
                            .updatedBy(formatUserFullName(combo.getUpdatedBy()))
                            .translations(translationResponses)
                            .build();
                })
                .collect(Collectors.toList());

        // Apply sorting using shared library sort method
        LocaleSortUtil.sortName(comboResponses, sortBy, direction);

        // Apply pagination
        int fromIndex = pageNumber * pageSize;
        int toIndex = Math.min(fromIndex + pageSize, comboResponses.size());
        List<ComboResponse> paginatedResponses = comboResponses.subList(fromIndex, toIndex);

        // Build pagination metadata
        PaginationMetaData paginationMetaData = PaginationMetaData.builder()
                .page(pageNumber + 1)
                .size(pageSize)
                .totalPages((int) Math.ceil((double) comboResponses.size() / pageSize))
                .totalRecords((long) comboResponses.size())
                .build();

        // Build final response
        ComboListResponse listResponse = ComboListResponse.builder()
                .combos(paginatedResponses)
                .count((long) paginatedResponses.size())
                .total((long) comboResponses.size())
                .metaData(paginationMetaData)
                .build();

        return ResponseDto.<ComboListResponse>builder()
                .message(messageUtil.getMessage("combo.list.success", userLocale))
                .data(listResponse)
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    /**
     * Retrieves full combo details including groups, items, modifiers, and translations.
     * <p>
     * Validates the combo exists and is not deleted, attaches pre-signed image URLs, and optionally filters returned
     * items by {@code orderType} (DINE_IN/TAKEAWAY) based on each item's configured order type. When {@code restaurantId}
     * is provided, item availability is derived from {@code restaurant_item_availability}.
     * </p>
     *
     * @param comboId      combo identifier
     * @param locale       locale/language tag used for translation selection and messages
     * @param restaurantId optional restaurant id for availability evaluation
     * @param orderType    optional order type string filter (DINE_IN/TAKEAWAY)
     * @return response containing combo details
     * @throws ResponseStatusException when combo/orderType is invalid or entities cannot be found
     */
    public ResponseDto<ComboDto<ComboDetailsResponse>> getComboDetailsById(UUID comboId, String locale, UUID restaurantId, String orderType) {
        log.info("Getting combo details for comboId: {} with locale: {}", comboId, locale);
        
        // Set locale context
        LocaleContextHolder.setLocale(new Locale(locale));
        
        // Find combo with translations
        Combo combo = comboRepository.findByIdWithTranslations(comboId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, 
                    messageUtil.getMessage(MSG_COMBO_NOT_FOUND, null, locale)));
        
        // Check if combo is deleted
        if (Boolean.TRUE.equals(combo.getIsDeleted())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                messageUtil.getMessage(MSG_COMBO_ALREADY_DELETED, null, locale));
        }
        
        // Get signed image URL
        String signedImageUrl = null;
        if (combo.getComboImageUrl() != null) {
            signedImageUrl = awsService.getPreSignedUrl(combo.getComboImageUrl());
        }
        
        // Fetch combo groups
        List<ComboGroup> comboGroups = comboRepository.findComboGroupsByComboId(comboId);
        
        // Fetch combo item mappings with items
        List<ComboItemMapping> comboItemMappings = comboRepository.findComboItemMappingsWithItems(comboId);
        
        // Parse orderType if provided
        ItemOrderType orderTypeFilter = null;
        if (orderType != null && !orderType.trim().isEmpty()) {
            try {
                orderTypeFilter = ItemOrderType.valueOf(orderType.toUpperCase());
            } catch (IllegalArgumentException e) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        messageUtil.getMessage("item.error.invalid.orderType", null, locale));
            }
        }
        comboItemMappings = filterComboItemMappingsByOrderType(comboItemMappings, orderTypeFilter);
        
        // Fetch combo item modifiers
        List<ComboItemModifier> comboItemModifiers = comboItemModifierRepository.findComboItemModifiersByComboId(comboId);
        
        // Fetch combo group translations
        List<ComboGroupTranslation> comboGroupTranslations = comboRepository.findComboGroupTranslationsByComboId(comboId);
        
        // Fetch combo translations
        List<ComboTranslation> comboTranslations = comboRepository.findComboTranslationsByComboId(comboId);
        
        // Create maps for quick lookup
        Map<UUID, List<ComboItemMapping>> groupItemsMap = comboItemMappings.stream()
            .collect(Collectors.groupingBy(cim -> cim.getComboGroup().getComboGroupId()));
        
        // Create map of combo item mapping ID to modifiers
        Map<UUID, List<ComboItemModifier>> itemModifiersMap = comboItemModifiers.stream()
            .collect(Collectors.groupingBy(cim -> cim.getComboItemMapping().getId()));
        
        Map<UUID, List<ComboGroupTranslation>> groupTranslationsMap = comboGroupTranslations.stream()
            .collect(Collectors.groupingBy(cgt -> cgt.getComboGroup().getComboGroupId()));
        
        // Build response
        List<ComboDetailsResponse.ComboGroupDetailsResponse> groupResponses = comboGroups.stream()
            .map(group -> {
                List<ComboItemMapping> groupItems = groupItemsMap.getOrDefault(group.getComboGroupId(), Collections.emptyList());
                List<ComboGroupTranslation> groupTranslations = groupTranslationsMap.getOrDefault(group.getComboGroupId(), Collections.emptyList());
                
                return ComboDetailsResponse.ComboGroupDetailsResponse.builder()
                    .comboGroupId(group.getComboGroupId())
                    .groupType(group.getGroupType())
                    .minSelect(group.getMinSelect())
                    .maxSelect(group.getMaxSelect())
                    .items(groupItems.stream()
                            .map(itemMapping -> {
                                Item item = itemMapping.getCategoryItemMapping().getItem();
                                
                                // Get modifiers for this item mapping
                                List<ComboItemModifier> itemModifiers = itemModifiersMap.getOrDefault(itemMapping.getId(), Collections.emptyList());
                                
                                // Get presigned URL for item image
                                String itemImageUrl = null;
                                if (item.getImageUrl() != null && !item.getImageUrl().isEmpty()) {
                                    itemImageUrl = awsService.getPreSignedUrl(item.getImageUrl());
                                }
                                
                                // Calculate item availability using restaurant_item_availability
                                Boolean itemIsAvailable = true;
                                if (restaurantId != null) {
                                    CategoryItemMapping categoryItemMapping = itemMapping.getCategoryItemMapping();
                                    if (categoryItemMapping != null && categoryItemMapping.getId() != null) {
                                        Optional<RestaurantItemAvailability> availabilityOpt = restaurantItemAvailabilityRepository
                                                .findByRestaurantIdAndCategoryItemMappingId(restaurantId, categoryItemMapping.getId());
                                        
                                        if (availabilityOpt.isPresent()) {
                                            RestaurantItemAvailability availability = availabilityOpt.get();
                                            itemIsAvailable = availability != null && Boolean.TRUE.equals(availability.getIsAvailable());
                                        } else {
                                            // If no availability record exists, consider item as available (fallback to default)
                                            itemIsAvailable = true;
                                        }
                                    } else {
                                        itemIsAvailable = false;
                                    }
                                }
                                
                                String currency = restaurantChainConfigProperties.getChain() != null ? restaurantChainConfigProperties.getChain().getCurrency() : null;
                                ComboDetailsResponse.ComboGroupDetailsResponse.ComboItemDetailsResponse.ComboItemDetailsResponseBuilder builder = 
                                    ComboDetailsResponse.ComboGroupDetailsResponse.ComboItemDetailsResponse.builder()
                                        .itemId(item.getId())
                                        .itemBasePrice(item.getBasePrice() != null ? CurrencyFormatter.formatAmount(BigDecimal.valueOf(item.getBasePrice()), currency).doubleValue() : null)
                                        .itemImageUrl(itemImageUrl)
                                        .itemStatus(item.getStatus())
                                        .itemDietaryPreference(item.getDietaryPreference())
                                        .itemAlcoholType(item.getAlcoholType())
                                        .itemHasModifierAssigned(item.getHasModifierAssigned())
                                        .defaultItem(Boolean.TRUE.equals(itemMapping.getIsDefault()))
                                        .isAvailable(itemIsAvailable)
                                        .itemTranslations(item.getTranslations() != null ? 
                                            item.getTranslations().stream()
                                                .map(translation -> ItemTranslationDto.builder()
                                                    .languageCode(translation.getLanguageCode())
                                                    .name(translation.getName())
                                                    .description(translation.getDescription())
                                                    .build())
                                                .collect(Collectors.toList()) : Collections.emptyList());
                                
                                // Add modifier item information if present
                                if (!itemModifiers.isEmpty()) {
                                    // Set multiple modifier information
                                    List<UUID> modifierIds = itemModifiers.stream()
                                            .map(modifier -> modifier.getModifierItem().getId())
                                            .collect(Collectors.toList());
                                    builder.modifierItemId(modifierIds);
                                    
                                    // Build modifier items list
                                    List<ComboDetailsResponse.ComboGroupDetailsResponse.ComboItemDetailsResponse.ModifierItemDetailsInfo> modifierItemsList = 
                                            itemModifiers.stream()
                                                    .map(modifier -> {
                                                        ModifierItem modifierItem = modifier.getModifierItem();
                                                        String modifierName = "";
                                                        String modifierDescription = "";
                                                        
                                                        if (modifierItem.getTranslations() != null && !modifierItem.getTranslations().isEmpty()) {
                                                            ModifierItemTranslation translation = modifierItem.getTranslations().get(0);
                                                            modifierName = translation.getName();
                                                            modifierDescription = translation.getDescription();
                                                        }
                                                        
                                                        // Get presigned URL for modifier item image
                                                        String modifierItemImageUrl = null;
                                                        if (modifierItem.getImageUrl() != null && !modifierItem.getImageUrl().isEmpty()) {
                                                            modifierItemImageUrl = awsService.getPreSignedUrl(modifierItem.getImageUrl());
                                                        }
                                                        
                                                        return ComboDetailsResponse.ComboGroupDetailsResponse.ComboItemDetailsResponse.ModifierItemDetailsInfo.builder()
                                                                .modifierItemId(modifierItem.getId())
                                                                .modifierItemName(modifierName)
                                                                .modifierItemDescription(modifierDescription)
                                                                .modifierItemPrice(modifierItem.getPrice())
                                                                .modifierItemImageUrl(modifierItemImageUrl)
                                                                .modifierItemStatus(modifierItem.getStatus())
                                                                .modifierItemTranslations(modifierItem.getTranslations() != null ? 
                                                                    modifierItem.getTranslations().stream()
                                                                        .map(translation -> ModifierItemTranslationDto.builder()
                                                                            .languageCode(translation.getLanguageCode())
                                                                            .name(translation.getName())
                                                                            .description(translation.getDescription())
                                                                            .build())
                                                                        .collect(Collectors.toList()) : Collections.emptyList())
                                                                .build();
                                                    })
                                                    .collect(Collectors.toList());
                                    builder.modifierItems(modifierItemsList);
                                }
                                
                                return builder.build();
                            })
                        .collect(Collectors.toList()))
                    .translations(groupTranslations.stream()
                            .map(translation -> ComboGroupTranslationDto.builder()
                                .languageCode(translation.getLanguageCode())
                                .groupName(translation.getGroupName())
                                .build())
                        .collect(Collectors.toList()))
                    .build();
            })
            .collect(Collectors.toList());
        
        // Include all combo translations
        List<ComboTranslationDto> translationDtos = comboTranslations.stream()
                .map(translation -> ComboTranslationDto.builder()
                    .languageCode(translation.getLanguageCode())
                    .name(translation.getName())
                    .description(translation.getDescription())
                    .build())
                .collect(Collectors.toList());
        
        // Calculate combo availability
        boolean comboIsAvailable = restaurantId == null
                || isComboAvailableForRestaurant(combo, restaurantId, comboGroups, comboItemMappings, groupItemsMap);
        
        // Fetch categoryId from menu category combo mapping
        UUID categoryId = null;
        List<MenuCategoryComboMapping> menuCategoryComboMappings = menuCategoryComboMappingRepository.findByCombo_ComboId(comboId);
        if (!menuCategoryComboMappings.isEmpty()) {
            MenuCategoryComboMapping mapping = menuCategoryComboMappings.get(0);
            if (mapping.getMenuCategoryMapping() != null && mapping.getMenuCategoryMapping().getCategory() != null) {
                categoryId = mapping.getMenuCategoryMapping().getCategory().getId();
            }
        }
        
        String currency = restaurantChainConfigProperties.getChain() != null ? restaurantChainConfigProperties.getChain().getCurrency() : null;
        
        // Fetch allowCookingRequest from restaurant chain config
        boolean allowCookingRequest = restaurantChainConfigProperties.getChain() != null 
                && restaurantChainConfigProperties.getChain().isAllowCookingRequest();
        
        // Calculate alcoholType based on items in the combo
        // If all items are alcoholic → ALCOHOLIC
        // If all items are non-alcoholic → NON_ALCOHOLIC
        // If mixed or no items → null
        AlcoholType comboAlcoholType = null;
        if (!comboItemMappings.isEmpty()) {
            List<AlcoholType> itemAlcoholTypes = comboItemMappings.stream()
                    .map(itemMapping -> {
                        Item item = itemMapping.getCategoryItemMapping().getItem();
                        return item != null ? item.getAlcoholType() : null;
                    })
                    .filter(Objects::nonNull)
                    .distinct()
                    .collect(Collectors.toList());
            
            if (itemAlcoholTypes.size() == 1) {
                // All items have the same alcohol type
                comboAlcoholType = itemAlcoholTypes.get(0);
            }
            // If size > 1, it means mixed alcohol types, so comboAlcoholType remains null
        }
        
        ComboDetailsResponse comboDetailsResponse = ComboDetailsResponse.builder()
                .comboId(combo.getComboId())
                .menuId(combo.getMenu().getId())
                .categoryId(categoryId)
                .type(combo.getType())
                .basePrice(combo.getBasePrice() != null ? CurrencyFormatter.formatAmount(combo.getBasePrice(), currency) : null)
                .comboImageUrl(signedImageUrl)
                .status(combo.getStatus())
                .itemOrderType(combo.getItemOrderType())
                .alcoholType(comboAlcoholType)
                .isAvailable(comboIsAvailable)
                .validFrom(combo.getValidFrom() != null ? combo.getValidFrom().withOffsetSameInstant(ZoneOffset.UTC) : null)
                .validTo(combo.getValidTo() != null ? combo.getValidTo().withOffsetSameInstant(ZoneOffset.UTC) : null)
                .startTime(combo.getStartTime() != null ? combo.getStartTime().withOffsetSameInstant(ZoneOffset.UTC) : null)
                .endTime(combo.getEndTime() != null ? combo.getEndTime().withOffsetSameInstant(ZoneOffset.UTC) : null)
                .daysOfWeek(combo.getDaysOfWeek())
                .comboGroups(groupResponses)
                .createdAt(combo.getCreatedAt() != null ? combo.getCreatedAt().toLocalDateTime() : null)
                .createdBy(formatUserFullName(combo.getCreatedBy()))
                .updatedAt(combo.getUpdatedAt() != null ? combo.getUpdatedAt().toLocalDateTime() : null)
                .updatedBy(formatUserFullName(combo.getUpdatedBy()))
                .translations(translationDtos)
                .allowCookingRequest(allowCookingRequest)
                .build();
        
        return ResponseDto.<ComboDto<ComboDetailsResponse>>builder()
                .data(ComboDto.<ComboDetailsResponse>builder().combo(comboDetailsResponse).build())
                .message(messageUtil.getMessage("combo.details.success", null, locale))
                .build();
    }

    /**
     * Determines if an item should be marked as default based on combo type and group type
     * 
     * Rules:
     * - For CHOICE combo type: Only one item per choice group can have isDefault=true
     * - For MIXED combo type: 
     *   - Fixed groups: No items have isDefault=true
     *   - Choice groups: Only one item per choice group can have isDefault=true
     * - For FIXED combo type: No items have isDefault=true
     */
    private boolean determineIsDefault(String comboType, String groupType, Boolean requestIsDefault) {
        ComboType comboTypeEnum = ComboType.valueOf(comboType);
        ComboGroupType groupTypeEnum = ComboGroupType.valueOf(groupType);
        
        // For FIXED combo type, no items should be default
        if (comboTypeEnum == ComboType.FIXED) {
            return false;
        }
        
        // For MIXED combo type, only choice groups can have default items
        if (comboTypeEnum == ComboType.MIXED) {
            if (groupTypeEnum == ComboGroupType.FIXED) {
                return false; // Fixed groups in mixed combos don't have default items
            } else if (groupTypeEnum == ComboGroupType.CHOICE) {
                return Boolean.TRUE.equals(requestIsDefault);
            }
        }
        
        // For CHOICE combo type, only choice groups can have default items
        if (comboTypeEnum == ComboType.CHOICE && groupTypeEnum == ComboGroupType.CHOICE) {
            return Boolean.TRUE.equals(requestIsDefault);
        }
        
        return false;
    }

    private boolean isComboAvailableForRestaurant(Combo combo,
                                                  UUID restaurantId,
                                                  List<ComboGroup> comboGroups,
                                                  List<ComboItemMapping> comboItemMappings,
                                                  Map<UUID, List<ComboItemMapping>> groupItemsMap) {
        if (restaurantId == null) {
            return true;
        }
        if (combo.getType() == ComboType.FIXED) {
            return comboItemMappings.stream()
                    .allMatch(m -> isCategoryItemMappingAvailableForRestaurant(restaurantId, m.getCategoryItemMapping()));
        }
        if (combo.getType() == ComboType.CHOICE) {
            return comboGroups.stream()
                    .filter(g -> g.getGroupType() == ComboGroupType.CHOICE)
                    .allMatch(g -> groupItemsMap.getOrDefault(g.getComboGroupId(), Collections.emptyList()).stream()
                            .anyMatch(m -> isCategoryItemMappingAvailableForRestaurant(restaurantId, m.getCategoryItemMapping())));
        }
        if (combo.getType() == ComboType.MIXED) {
            return comboGroups.stream().allMatch(g -> {
                List<ComboItemMapping> groupItems = groupItemsMap.getOrDefault(g.getComboGroupId(), Collections.emptyList());
                if (g.getGroupType() == ComboGroupType.FIXED) {
                    return groupItems.stream()
                            .allMatch(m -> isCategoryItemMappingAvailableForRestaurant(restaurantId, m.getCategoryItemMapping()));
                }
                if (g.getGroupType() == ComboGroupType.CHOICE) {
                    return groupItems.stream()
                            .anyMatch(m -> isCategoryItemMappingAvailableForRestaurant(restaurantId, m.getCategoryItemMapping()));
                }
                return true;
            });
        }
        return true;
    }

    private boolean isCategoryItemMappingAvailableForRestaurant(UUID restaurantId, CategoryItemMapping categoryItemMapping) {
        if (restaurantId == null || categoryItemMapping == null || categoryItemMapping.getId() == null) {
            return false;
        }
        Optional<RestaurantItemAvailability> availabilityOpt = restaurantItemAvailabilityRepository
                .findByRestaurantIdAndCategoryItemMappingId(restaurantId, categoryItemMapping.getId());
        if (availabilityOpt.isEmpty()) {
            return true;
        }
        RestaurantItemAvailability availability = availabilityOpt.get();
        return availability != null && Boolean.TRUE.equals(availability.getIsAvailable());
    }

    private void createComboGroupsAndMappings(Combo savedCombo, ComboRequest request, String locale) {
        for (ComboRequest.ComboGroupRequest groupRequest : request.getComboGroups()) {
            ComboGroup comboGroup = ComboGroup.builder()
                    .combo(savedCombo)
                    .groupType(ComboGroupType.valueOf(groupRequest.getGroupType()))
                    .minSelect(groupRequest.getMinSelect())
                    .maxSelect(groupRequest.getMaxSelect())
                    .build();

            ComboGroup savedGroup = comboGroupRepository.save(comboGroup);

            for (ComboRequest.ComboGroupRequest.ComboItemRequest itemRequest : groupRequest.getItems()) {
                CategoryItemMapping categoryItemMapping = categoryItemMappingRepository
                        .findByItemIdAndMenuCategoryMappingMenuId(itemRequest.getItemId(), request.getMenuId())
                        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                                "Item not found in the specified menu"));

                boolean isDefault = determineIsDefault(request.getType(), groupRequest.getGroupType(), itemRequest.getDefaultItem());
                ComboItemMapping comboItemMapping = ComboItemMapping.builder()
                        .comboGroup(savedGroup)
                        .categoryItemMapping(categoryItemMapping)
                        .isDefault(isDefault)
                        .build();

                ComboItemMapping savedComboItemMapping = comboItemMappingRepository.save(comboItemMapping);
                createComboItemModifiers(savedComboItemMapping, itemRequest.getModifierItemId());
            }

            for (ComboGroupTranslationDto translationDto : groupRequest.getTranslations()) {
                ComboGroupTranslation translation = ComboGroupTranslation.builder()
                        .comboGroup(savedGroup)
                        .languageCode(translationDto.getLanguageCode())
                        .groupName(translationDto.getGroupName())
                        .build();
                comboGroupTranslationRepository.save(translation);
            }
        }
    }

    private void createComboItemModifiers(ComboItemMapping savedComboItemMapping, List<UUID> modifierItemIds) {
        if (modifierItemIds == null || modifierItemIds.isEmpty()) {
            return;
        }
        List<UUID> distinctModifierIds = modifierItemIds.stream().distinct().collect(Collectors.toList());
        for (UUID modifierId : distinctModifierIds) {
            ModifierItem modifierItem = modifierItemRepository.findByIdAndIsDeletedFalse(modifierId)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                            "Modifier item not found: " + modifierId));

            ComboItemModifier comboItemModifier = ComboItemModifier.builder()
                    .comboItemMapping(savedComboItemMapping)
                    .modifierItem(modifierItem)
                    .build();
            comboItemModifierRepository.save(comboItemModifier);
        }
    }

    private void createComboTranslations(Combo savedCombo, ComboRequest request) {
        for (ComboTranslationDto translationDto : request.getTranslations()) {
            ComboTranslation translation = ComboTranslation.builder()
                    .combo(savedCombo)
                    .languageCode(translationDto.getLanguageCode())
                    .name(translationDto.getName())
                    .description(translationDto.getDescription())
                    .build();
            comboTranslationRepository.save(translation);
        }
    }

    private void createMenuCategoryComboMapping(Combo savedCombo, ComboRequest request, String locale) {
        Category category = categoryRepository.findByIdAndIsDeletedFalse(request.getCategoryId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        messageUtil.getMessage("category.not.found", null, locale)));

        if (category.getIsCombo() == null || !category.getIsCombo()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    messageUtil.getMessage("combo.category.must.be.combo.type", null, locale));
        }

        MenuCategoryMapping menuCategoryMapping = menuCategoryMappingRepository
                .findByMenuIdAndCategoryId(request.getMenuId(), request.getCategoryId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        messageUtil.getMessage("category.menu.mismatch", null, locale)));

        MenuCategoryComboMapping menuCategoryComboMapping = MenuCategoryComboMapping.builder()
                .combo(savedCombo)
                .menuCategoryMapping(menuCategoryMapping)
                .build();

        menuCategoryComboMappingRepository.save(menuCategoryComboMapping);
        log.info("Created new menu category combo mapping for combo {} with categoryId {}", savedCombo.getComboId(), request.getCategoryId());
    }

    private ComboResponse buildComboResponse(UUID comboId, String locale) {
        Combo comboWithTranslations = comboRepository.findByIdWithTranslations(comboId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Combo not found"));

        List<ComboGroup> comboGroups = comboRepository.findComboGroupsByComboId(comboId);
        List<ComboItemMapping> comboItemMappings = comboRepository.findComboItemMappingsWithItems(comboId);
        List<ComboItemModifier> comboItemModifiers = comboItemModifierRepository.findComboItemModifiersByComboId(comboId);
        List<ComboGroupTranslation> comboGroupTranslations = comboRepository.findComboGroupTranslationsByComboId(comboId);

        Map<UUID, List<ComboItemMapping>> groupItemsMap = comboItemMappings.stream()
                .collect(Collectors.groupingBy(cim -> cim.getComboGroup().getComboGroupId()));
        Map<UUID, List<ComboItemModifier>> itemModifiersMap = comboItemModifiers.stream()
                .collect(Collectors.groupingBy(cim -> cim.getComboItemMapping().getId()));
        Map<UUID, List<ComboGroupTranslation>> groupTranslationsMap = comboGroupTranslations.stream()
                .collect(Collectors.groupingBy(cgt -> cgt.getComboGroup().getComboGroupId()));

        List<ComboResponse.ComboGroupResponse> groupResponses = comboGroups.stream()
                .map(group -> buildComboGroupResponse(group, groupItemsMap, itemModifiersMap, groupTranslationsMap))
                .collect(Collectors.toList());

        List<ComboTranslation> comboTranslations = comboRepository.findComboTranslationsByComboId(comboId);
        List<ComboTranslationDto> translationDtos = comboTranslations.stream()
                .map(translation -> ComboTranslationDto.builder()
                        .languageCode(translation.getLanguageCode())
                        .name(translation.getName())
                        .description(translation.getDescription())
                        .build())
                .collect(Collectors.toList());

        String currency = restaurantChainConfigProperties.getChain() != null ? restaurantChainConfigProperties.getChain().getCurrency() : null;
        return ComboResponse.builder()
                .comboId(comboWithTranslations.getComboId())
                .menuId(comboWithTranslations.getMenu().getId())
                .type(comboWithTranslations.getType())
                .basePrice(comboWithTranslations.getBasePrice() != null ? CurrencyFormatter.formatAmount(comboWithTranslations.getBasePrice(), currency) : null)
                .comboImageUrl(awsService.getFullUrl(comboWithTranslations.getComboImageUrl()))
                .status(comboWithTranslations.getStatus())
                .itemOrderType(comboWithTranslations.getItemOrderType())
                .validFrom(comboWithTranslations.getValidFrom() != null ? comboWithTranslations.getValidFrom().withOffsetSameInstant(ZoneOffset.UTC) : null)
                .validTo(comboWithTranslations.getValidTo() != null ? comboWithTranslations.getValidTo().withOffsetSameInstant(ZoneOffset.UTC) : null)
                .startTime(comboWithTranslations.getStartTime() != null ? comboWithTranslations.getStartTime().withOffsetSameInstant(ZoneOffset.UTC) : null)
                .endTime(comboWithTranslations.getEndTime() != null ? comboWithTranslations.getEndTime().withOffsetSameInstant(ZoneOffset.UTC) : null)
                .daysOfWeek(comboWithTranslations.getDaysOfWeek())
                .comboGroups(groupResponses)
                .createdAt(comboWithTranslations.getCreatedAt() != null ? comboWithTranslations.getCreatedAt().toLocalDateTime() : null)
                .createdBy(formatUserFullName(comboWithTranslations.getCreatedBy()))
                .updatedAt(comboWithTranslations.getUpdatedAt() != null ? comboWithTranslations.getUpdatedAt().toLocalDateTime() : null)
                .updatedBy(formatUserFullName(comboWithTranslations.getUpdatedBy()))
                .translations(translationDtos)
                .build();
    }

    private ComboResponse.ComboGroupResponse buildComboGroupResponse(ComboGroup group,
                                                                    Map<UUID, List<ComboItemMapping>> groupItemsMap,
                                                                    Map<UUID, List<ComboItemModifier>> itemModifiersMap,
                                                                    Map<UUID, List<ComboGroupTranslation>> groupTranslationsMap) {
        List<ComboItemMapping> groupItems = groupItemsMap.getOrDefault(group.getComboGroupId(), Collections.emptyList());
        List<ComboGroupTranslation> groupTranslations = groupTranslationsMap.getOrDefault(group.getComboGroupId(), Collections.emptyList());

        return ComboResponse.ComboGroupResponse.builder()
                .comboGroupId(group.getComboGroupId())
                .groupType(group.getGroupType())
                .minSelect(group.getMinSelect())
                .maxSelect(group.getMaxSelect())
                .items(groupItems.stream().map(itemMapping -> buildComboItemResponse(itemMapping, itemModifiersMap))
                        .collect(Collectors.toList()))
                .translations(groupTranslations.stream()
                        .map(translation -> ComboGroupTranslationDto.builder()
                                .languageCode(translation.getLanguageCode())
                                .groupName(translation.getGroupName())
                                .build())
                        .collect(Collectors.toList()))
                .build();
    }

    private ComboResponse.ComboGroupResponse.ComboItemResponse buildComboItemResponse(ComboItemMapping itemMapping,
                                                                                     Map<UUID, List<ComboItemModifier>> itemModifiersMap) {
        List<ComboItemModifier> itemModifiers = itemModifiersMap.getOrDefault(itemMapping.getId(), Collections.emptyList());

        ComboResponse.ComboGroupResponse.ComboItemResponse.ComboItemResponseBuilder builder =
                ComboResponse.ComboGroupResponse.ComboItemResponse.builder()
                        .itemId(itemMapping.getCategoryItemMapping().getItem().getId());

        if (itemModifiers.isEmpty()) {
            return builder.build();
        }

        List<UUID> modifierIds = itemModifiers.stream()
                .map(modifier -> modifier.getModifierItem().getId())
                .collect(Collectors.toList());
        builder.modifierItemId(modifierIds);

        List<ComboResponse.ComboGroupResponse.ComboItemResponse.ModifierItemInfo> modifierItemsList =
                itemModifiers.stream()
                        .map(modifier -> {
                            ModifierItem modifierItem = modifier.getModifierItem();
                            String modifierName = "";
                            String modifierDescription = "";

                            if (modifierItem.getTranslations() != null && !modifierItem.getTranslations().isEmpty()) {
                                ModifierItemTranslation translation = modifierItem.getTranslations().get(0);
                                modifierName = translation.getName();
                                modifierDescription = translation.getDescription();
                            }

                            return ComboResponse.ComboGroupResponse.ComboItemResponse.ModifierItemInfo.builder()
                                    .modifierItemId(modifierItem.getId())
                                    .modifierItemName(modifierName)
                                    .modifierItemDescription(modifierDescription)
                                    .modifierItemPrice(modifierItem.getPrice())
                                    .build();
                        })
                        .collect(Collectors.toList());
        builder.modifierItems(modifierItemsList);
        return builder.build();
    }

    private List<ComboItemMapping> filterComboItemMappingsByOrderType(List<ComboItemMapping> comboItemMappings,
                                                                      ItemOrderType orderTypeFilter) {
        if (orderTypeFilter == null) {
            return comboItemMappings;
        }
        return comboItemMappings.stream()
                .filter(itemMapping -> isItemMappingAllowedForOrderType(itemMapping, orderTypeFilter))
                .collect(Collectors.toList());
    }

    private boolean isItemMappingAllowedForOrderType(ComboItemMapping itemMapping, ItemOrderType orderTypeFilter) {
        CategoryItemMapping categoryItemMapping = itemMapping.getCategoryItemMapping();
        if (categoryItemMapping == null) {
            return false;
        }
        ItemOrderType itemOrderType = categoryItemMapping.getItemOrderType() != null
                ? categoryItemMapping.getItemOrderType()
                : ItemOrderType.BOTH;
        if (orderTypeFilter == ItemOrderType.DINE_IN) {
            return itemOrderType == ItemOrderType.DINE_IN || itemOrderType == ItemOrderType.BOTH;
        }
        if (orderTypeFilter == ItemOrderType.TAKEAWAY) {
            return itemOrderType == ItemOrderType.TAKEAWAY || itemOrderType == ItemOrderType.BOTH;
        }
        return false;
    }

    /**
     * Validates combo request invariants that depend on combo type and structure.
     * <p>
     * Validates:
     * </p>
     * <ul>
     *   <li>Required enum fields (type/status/groupType) and day-of-week values</li>
     *   <li>UTC constraints and date range for validity window via {@link #validateUtcDateTimeFields(ComboRequest, Locale)}</li>
     *   <li>Daily time window rules (both start/end present; allow overnight; reject equal instants)</li>
     *   <li>Translation language codes (supported + no duplicates) for combos and groups</li>
     *   <li>Group composition rules (FIXED vs CHOICE groups) and default-item constraints per type</li>
     *   <li>Modifier item constraints (e.g., disallow for CHOICE combo type)</li>
     * </ul>
     *
     * @param request combo request to validate
     * @param locale  locale/language tag used for localized error messages
     * @throws ResponseStatusException when any rule is violated
     */
        private void validateComboTypeRules(ComboRequest request, String locale) {
        validateComboTypeRules(request, locale, null, false);
    }

    private void validateComboTypeRules(ComboRequest request, String locale, OffsetDateTime existingValidFromUtc, boolean isUpdate) {
        List<ComboRequest.ComboGroupRequest> groups = request.getComboGroups();
        Locale userLocale = Locale.forLanguageTag(locale);
        List<String> supportedLanguages = localizationProperties.getLanguages();
        
        // Validate type - not null and valid enum value
        if (request.getType() == null || request.getType().trim().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                messageUtil.getMessage("combo.error.type.required", userLocale));
        }
        
        try {
            ComboType.valueOf(request.getType());
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                messageUtil.getMessage("combo.type.invalid", userLocale));
        }
        
        // Validate status - not null and valid enum value
        if (request.getStatus() == null || request.getStatus().trim().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                messageUtil.getMessage("combo.error.status.required", userLocale));
        }
        
        try {
            EntityStatus.valueOf(request.getStatus());
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                messageUtil.getMessage("combo.error.invalid.status", userLocale));
        }
        
        // Validate DayOfWeek enums
        if (request.getDaysOfWeek() != null) {
            for (com.gulfnet.shared_library.enums.DayOfWeek day : request.getDaysOfWeek()) {
                try {
                    com.gulfnet.shared_library.enums.DayOfWeek.valueOf(day.name());
                } catch (IllegalArgumentException e) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        messageUtil.getMessage("combo.error.invalid.day.of.week", userLocale));
                }
            }
        }
        
        // Validate UTC datetime fields
        validateUtcDateTimeFields(request, userLocale, existingValidFromUtc, isUpdate);
        
        if (request.getStartTime() != null && request.getEndTime() != null) {
            OffsetTime startUtc = convertToUtc(request.getStartTime());
            OffsetTime endUtc = convertToUtc(request.getEndTime());
            if (startUtc.equals(endUtc)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    messageUtil.getMessage("combo.error.invalid.time.range", userLocale));
            }
        } else if ((request.getStartTime() != null && request.getEndTime() == null) ||
                   (request.getStartTime() == null && request.getEndTime() != null)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                messageUtil.getMessage("combo.error.time.both.required", userLocale));
        }
        
        // Validate combo translations
        if (request.getTranslations() != null && !request.getTranslations().isEmpty()) {
            Set<String> comboLanguageCodes = new HashSet<>();
            for (ComboTranslationDto translation : request.getTranslations()) {
                if (translation.getLanguageCode() != null) {
                    if (!comboLanguageCodes.add(translation.getLanguageCode())) {
                        throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                            messageUtil.getMessage("combo.error.duplicate.group.language", userLocale, translation.getLanguageCode()));
                    }
                    
                    if (!supportedLanguages.contains(translation.getLanguageCode())) {
                        throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                            messageUtil.getMessage("error.invalid.language", userLocale));
                    }
                }
            }
        }
        
        // Validate group translations and enum values
        for (ComboRequest.ComboGroupRequest group : groups) {
            // Validate ComboGroupType - not null and valid enum value
            if (group.getGroupType() == null || group.getGroupType().trim().isEmpty()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    messageUtil.getMessage("combo.error.group.type.required", userLocale));
            }
            
            try {
                ComboGroupType.valueOf(group.getGroupType());
            } catch (IllegalArgumentException e) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    messageUtil.getMessage("combo.error.invalid.group.type", userLocale));
            }
            
            // Validate group translations - require at least one translation
            if (group.getTranslations() == null || group.getTranslations().isEmpty()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    messageUtil.getMessage("combo.error.group.translation.required", userLocale));
            }
            
            // Validate group translations
            if (group.getTranslations() != null && !group.getTranslations().isEmpty()) {
                Set<String> groupLanguageCodes = new HashSet<>();
                for (ComboGroupTranslationDto translation : group.getTranslations()) {
                    if (translation.getLanguageCode() != null) {
                        if (!groupLanguageCodes.add(translation.getLanguageCode())) {
                            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                                messageUtil.getMessage("combo.error.duplicate.group.language", userLocale, translation.getLanguageCode()));
                        }
                        
                        if (!supportedLanguages.contains(translation.getLanguageCode())) {
                            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                                messageUtil.getMessage("error.invalid.language", userLocale));
                        }
                    }
                }
            }
        }
        
        // Original combo type specific validations
        switch (request.getType()) {
            case COMBO_TYPE_FIXED:
                // FIXED combo: exactly 1 group of type FIXED, min 2 items
                if (groups.size() != 1) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, 
                        messageUtil.getMessage("combo.error.fixed.combo.one.group", userLocale));
                }
                
                ComboRequest.ComboGroupRequest fixedGroup = groups.get(0);
                if (!COMBO_TYPE_FIXED.equals(fixedGroup.getGroupType())) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, 
                        messageUtil.getMessage("combo.error.fixed.combo.group.type", userLocale));
                }
                
                if (fixedGroup.getItems().size() < 2) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, 
                        messageUtil.getMessage("combo.error.fixed.combo.min.items", userLocale));
                }
                break;
                
            case COMBO_TYPE_CHOICE:
                // CHOICE combo: minimum 2 groups of type CHOICE, each with min 2 items
                if (groups.size() < 2) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, 
                        messageUtil.getMessage("combo.error.choice.combo.min.groups", userLocale));
                }
                
                for (ComboRequest.ComboGroupRequest group : groups) {
                    if (!COMBO_TYPE_CHOICE.equals(group.getGroupType())) {
                        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, 
                            messageUtil.getMessage("combo.error.choice.combo.group.type", userLocale));
                    }
                    
                    if (group.getItems().size() < 2) {
                        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, 
                            messageUtil.getMessage("combo.error.choice.combo.min.items", userLocale));
                    }
                    
                    if (group.getMinSelect() < 1 || group.getMaxSelect() < group.getMinSelect()) {
                        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, 
                            messageUtil.getMessage("combo.error.choice.combo.invalid.select", userLocale));
                    }
                }
                break;
                
            case "MIXED":
                // MIXED combo: at least 1 FIXED group and 1 CHOICE group.
                // FIXED group can have 1+ item; CHOICE group must have 2+ items.
                boolean hasFixedGroup = false;
                boolean hasChoiceGroup = false;
                
                for (ComboRequest.ComboGroupRequest group : groups) {
                    if (COMBO_TYPE_FIXED.equals(group.getGroupType())) {
                        hasFixedGroup = true;
                    } else if (COMBO_TYPE_CHOICE.equals(group.getGroupType())) {
                        hasChoiceGroup = true;
                    }
                    
                    boolean isChoiceGroup = COMBO_TYPE_CHOICE.equals(group.getGroupType());
                    int minItems = isChoiceGroup ? 2 : 1;
                    if (group.getItems().size() < minItems) {
                        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, 
                            messageUtil.getMessage("combo.error.mixed.combo.min.items", userLocale));
                    }
                }
                
                if (!hasFixedGroup) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, 
                        messageUtil.getMessage("combo.error.mixed.combo.need.fixed", userLocale));
                }
                
                if (!hasChoiceGroup) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, 
                        messageUtil.getMessage("combo.error.mixed.combo.need.choice", userLocale));
                }
                break;
        }
        
        // Validate isDefault flag rules
        validateIsDefaultRules(request, userLocale);
    }

    /**
     * Validates isDefault flag rules and modifier item rules based on combo type and group type
     * 
     * Rules:
     * - For CHOICE combo type: Only one item per choice group can have isDefault=true
     * - For MIXED combo type: 
     *   - Fixed groups: No items should have isDefault=true
     *   - Choice groups: Only one item per choice group can have isDefault=true
     * - For FIXED combo type: No items should have isDefault=true
     * 
     * Modifier Item Rules:
     * - FIXED combo type: Modifier items are allowed
     * - CHOICE combo type: Modifier items are NOT allowed
     * - MIXED combo type: Modifier items are allowed in all groups
     */
    private void validateIsDefaultRules(ComboRequest request, Locale userLocale) {
        ComboType comboType = ComboType.valueOf(request.getType());
        
        for (ComboRequest.ComboGroupRequest group : request.getComboGroups()) {
            ComboGroupType groupType = ComboGroupType.valueOf(group.getGroupType());
            
            // Count items with isDefault=true in this group
            long defaultItemCount = group.getItems().stream()
                    .mapToLong(item -> Boolean.TRUE.equals(item.getDefaultItem()) ? 1 : 0)
                    .sum();
            
            // For FIXED combo type, no items should be default
            if (comboType == ComboType.FIXED && defaultItemCount > 0) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    messageUtil.getMessage("combo.error.fixed.combo.no.default", userLocale));
            }
            
            // For MIXED combo type
            if (comboType == ComboType.MIXED) {
                if (groupType == ComboGroupType.FIXED) {
                    // Fixed groups in mixed combos shouldn't have default items
                    if (defaultItemCount > 0) {
                        throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                            messageUtil.getMessage("combo.error.mixed.fixed.group.no.default", userLocale));
                    }
                } else if (groupType == ComboGroupType.CHOICE && defaultItemCount > 1) {
                    // Choice groups in mixed combos can have at most one default item
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        messageUtil.getMessage("combo.error.choice.group.multiple.default", userLocale));
                }
            }
            
            // For CHOICE combo type, choice groups can have at most one default item
            if (comboType == ComboType.CHOICE && groupType == ComboGroupType.CHOICE && defaultItemCount > 1) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    messageUtil.getMessage("combo.error.choice.group.multiple.default", userLocale));
            }
            
            // Validate modifier item rules
            validateModifierItemRules(comboType, groupType, group, userLocale);
        }
    }
    
    /**
     * Validates modifier item rules based on combo type and group type
     */
    private void validateModifierItemRules(ComboType comboType, ComboGroupType groupType, 
                                         ComboRequest.ComboGroupRequest group, Locale userLocale) {
        
        // Check if any items in this group have modifier items
        boolean hasModifierItems = group.getItems().stream()
                .anyMatch(item -> item.getModifierItemId() != null && !item.getModifierItemId().isEmpty());
        
        // For CHOICE combo type, modifier items are not allowed
        if (hasModifierItems && comboType == ComboType.CHOICE) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                messageUtil.getMessage("combo.error.choice.combo.no.modifiers", userLocale));
        }
    }

    @Override
    @Transactional
    /**
     * Soft-deletes a combo by marking it deleted and removing its menu/category assignment.
     *
     * @param comboId combo identifier to delete
     * @param userId  acting user id (string UUID) used for updated-by metadata
     * @param locale  locale/language tag used for localized messages
     * @return response with a success message
     * @throws ResponseStatusException when combo/user is not found, combo is already deleted, or combo has no menu
     */
    public ResponseDto<String> deleteCombo(UUID comboId, String userId, String locale) {
        log.info("Deleting combo with ID: {}", comboId);
        
        // Set locale context
        LocaleContextHolder.setLocale(new Locale(locale));
        Locale userLocale = Locale.forLanguageTag(locale);
        
        // Validate combo exists
        Combo combo = comboRepository.findById(comboId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, 
                    messageUtil.getMessage(MSG_COMBO_NOT_FOUND, userLocale)));
        
        // Validate combo is not already deleted
        if (Boolean.TRUE.equals(combo.getIsDeleted())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, 
                messageUtil.getMessage(MSG_COMBO_ALREADY_DELETED, userLocale));
        }
        
        // Validate combo is assigned to a menu
        if (combo.getMenu() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, 
                messageUtil.getMessage("combo.not.assigned.to.menu", userLocale));
        }
        
        // Validate user exists
        User user = userRepository.findById(UUID.fromString(userId))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, 
                    messageUtil.getMessage("user.not.found", userLocale)));
        
        // Delete menu category combo mappings
        menuCategoryComboMappingRepository.deleteByCombo_ComboId(comboId);
        log.info("Deleted menu category combo mappings for combo {}", comboId);
        
        // Perform soft delete
        combo.setIsDeleted(true);
        combo.setUpdatedAt(OffsetDateTime.now(ZoneOffset.UTC));
        combo.setUpdatedBy(user);
        
        comboRepository.save(combo);
        
        log.info("Combo {} successfully deleted by user {}", comboId, userId);
        
        return ResponseDto.<String>builder()
                .data("Combo deleted successfully")
                .message(messageUtil.getMessage("combo.delete.success", userLocale))
                .build();
    }
    

    private void validateUtcDateTimeFields(ComboRequest request, Locale userLocale, OffsetDateTime existingValidFromUtc, boolean isUpdate) {

        // Validate validFrom field
        if (request.getValidFrom() != null) {
            // Ensure the datetime is in UTC
            if (!request.getValidFrom().getOffset().equals(ZoneOffset.UTC)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    messageUtil.getMessage("combo.error.valid.from.not.utc", userLocale));
            }
            
            // On create: validFrom must not be in the past.
            // On update: skip this check and only validate date range (validFrom <= validTo).
            if (!isUpdate) {
                OffsetDateTime nowUtc = OffsetDateTime.now(ZoneOffset.UTC);
                if (request.getValidFrom().isBefore(nowUtc)) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        messageUtil.getMessage("combo.error.valid.from.past", userLocale));
                }
            }
        }
        
        // Validate validTo field - ensure the datetime is in UTC
        if (request.getValidTo() != null && !request.getValidTo().getOffset().equals(ZoneOffset.UTC)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                messageUtil.getMessage("combo.error.valid.to.not.utc", userLocale));
        }
        
        // Validate date range
        if (request.getValidFrom() != null && request.getValidTo() != null
                && request.getValidFrom().isAfter(request.getValidTo())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                messageUtil.getMessage("combo.error.invalid.date.range", userLocale));
        }
    }
    
    private OffsetDateTime convertToUtc(OffsetDateTime offsetDateTime) {
        if (offsetDateTime == null) {
            return null;
        }
        return offsetDateTime.withOffsetSameInstant(ZoneOffset.UTC);
    }

    private OffsetTime convertToUtc(OffsetTime offsetTime) {
        if (offsetTime == null) {
            return null;
        }
        return offsetTime.withOffsetSameInstant(ZoneOffset.UTC);
    }
    
    
    private LocalDateTime convertToLocalDateTime(OffsetDateTime offsetDateTime) {
        if (offsetDateTime == null) {
            return null;
        }
        return offsetDateTime.toLocalDateTime();
    }
    
    private LocalTime convertToLocalTime(OffsetDateTime offsetDateTime) {
        if (offsetDateTime == null) {
            return null;
        }
        return offsetDateTime.toLocalTime();
    }
    
    /**
     * Filters a list of combos to those currently available for ordering.
     * <p>
     * Applies:
     * </p>
     * <ul>
     *   <li>UTC validity window checks ({@code validFrom/validTo})</li>
     *   <li>Daily hour checks ({@code startTime/endTime}) including overnight ranges</li>
     *   <li>Day-of-week checks</li>
     *   <li>Structural availability checks based on combo type and group composition, requiring items to be ACTIVE and
     *   not deleted/out-of-stock, and requiring CHOICE groups to have at least one available option</li>
     * </ul>
     * If structural evaluation fails for a combo, the combo is excluded (fail-closed).
     *
     * @param combos        combos to evaluate
     * @param userLocale    locale for localized logging/messages where applicable
     * @param userTimezone  user timezone used to derive current local time/day for scheduling checks
     * @return filtered list containing only currently available combos
     */
    private List<Combo> filterAvailableCombos(List<Combo> combos, Locale userLocale, ZoneOffset userTimezone) {
        OffsetDateTime nowUtc = OffsetDateTime.now(ZoneOffset.UTC);
        
        // Convert UTC time to user's local timezone for startTime/endTime comparison
        OffsetDateTime nowUserTimezone = nowUtc.withOffsetSameInstant(userTimezone);
        LocalTime currentTime = nowUserTimezone.toLocalTime();
        DayOfWeek currentDay = convertToDayOfWeek(nowUserTimezone.getDayOfWeek());
        
        return combos.stream()
                .filter(combo -> {
                    // Check validFrom/validTo (UTC datetime)
                    if (combo.getValidFrom() != null && nowUtc.isBefore(combo.getValidFrom())) {
                        return false; // Not yet valid
                    }
                    if (combo.getValidTo() != null && nowUtc.isAfter(combo.getValidTo())) {
                        return false; // Expired
                    }
                    
                    // Check startTime/endTime (daily hours)
                    if (combo.getStartTime() != null && combo.getEndTime() != null) {
                        LocalTime startTime = combo.getStartTime() != null ? combo.getStartTime().withOffsetSameInstant(ZoneOffset.UTC).toLocalTime() : null;
                        LocalTime endTime = combo.getEndTime() != null ? combo.getEndTime().withOffsetSameInstant(ZoneOffset.UTC).toLocalTime() : null;
                        
                        if (!isTimeInRange(currentTime, startTime, endTime)) {
                            return false; // Outside daily hours
                        }
                    }
                    
                    // Check daysOfWeek
                    if (combo.getDaysOfWeek() != null && !combo.getDaysOfWeek().isEmpty()
                            && !combo.getDaysOfWeek().contains(currentDay)) {
                        return false; // Not available on current day
                    }
                    
                    // Structural availability by combo type and group composition
                    try {
                        // Load groups and items for this combo
                        List<ComboGroup> groups = comboRepository.findComboGroupsByComboId(combo.getComboId());
                        List<ComboItemMapping> mappings = comboRepository.findComboItemMappingsWithItems(combo.getComboId());

                        // Build helpers to evaluate availability
                        java.util.Map<java.util.UUID, java.util.List<ComboItemMapping>> groupToMappings =
                                mappings.stream().collect(Collectors.groupingBy(m -> m.getComboGroup().getComboGroupId()));

                        java.util.function.Predicate<Item> isItemAvailable = item ->
                                item != null && item.getStatus() == EntityStatus.ACTIVE && !Boolean.TRUE.equals(item.getIsDeleted()) && !Boolean.TRUE.equals(item.getOutOfStock());

                        switch (combo.getType()) {
                            case FIXED -> {
                                // Any unavailable item invalidates the combo
                                for (ComboItemMapping mapping : mappings) {
                                    Item item = mapping.getCategoryItemMapping() != null ? mapping.getCategoryItemMapping().getItem() : null;
                                    if (!isItemAvailable.test(item)) {
                                        return false;
                                    }
                                }
                                return true;
                            }
                            case MIXED -> {
                                // All FIXED groups must have only available items
                                for (ComboGroup group : groups) {
                                    if (group.getGroupType() == ComboGroupType.FIXED) {
                                        List<ComboItemMapping> groupMappings = groupToMappings.getOrDefault(group.getComboGroupId(), java.util.Collections.emptyList());
                                        for (ComboItemMapping mapping : groupMappings) {
                                            Item item = mapping.getCategoryItemMapping() != null ? mapping.getCategoryItemMapping().getItem() : null;
                                            if (!isItemAvailable.test(item)) {
                                                return false; // fixed part unavailable
                                            }
                                        }
                                    }
                                }
                                // For CHOICE groups, ensure there is at least one available option per group
                                for (ComboGroup group : groups) {
                                    if (group.getGroupType() == ComboGroupType.CHOICE) {
                                        List<ComboItemMapping> groupMappings = groupToMappings.getOrDefault(group.getComboGroupId(), java.util.Collections.emptyList());
                                        boolean anyAvailable = groupMappings.stream().anyMatch(m -> {
                                            Item item = m.getCategoryItemMapping() != null ? m.getCategoryItemMapping().getItem() : null;
                                            return isItemAvailable.test(item);
                                        });
                                        if (!anyAvailable) {
                                            return false; // no available choice in this group
                                        }
                                    }
                                }
                                return true;
                            }
                            case CHOICE -> {
                                // Active if each choice group has at least one available item
                                for (ComboGroup group : groups) {
                                    List<ComboItemMapping> groupMappings = groupToMappings.getOrDefault(group.getComboGroupId(), java.util.Collections.emptyList());
                                    boolean anyAvailable = groupMappings.stream().anyMatch(m -> {
                                        Item item = m.getCategoryItemMapping() != null ? m.getCategoryItemMapping().getItem() : null;
                                        return isItemAvailable.test(item);
                                    });
                                    if (!anyAvailable) {
                                        return false;
                                    }
                                }
                                return true;
                            }
                            default -> {
                                return true;
                            }
                        }
                    } catch (Exception e) {
                        // If structural evaluation fails, be safe and hide the combo
                        log.warn("Failed structural availability check for combo {}: {}", combo.getComboId(), e.getMessage());
                        return false;
                    }
                })
                .collect(Collectors.toList());
    }
    
    private boolean isTimeInRange(LocalTime currentTime, LocalTime startTime, LocalTime endTime) {
        if (startTime.isBefore(endTime)) {
            // Normal case: start < end (e.g., 09:00 to 22:00)
            return !currentTime.isBefore(startTime) && !currentTime.isAfter(endTime);
        } else {
            // Overnight case: start > end (e.g., 23:00 to 02:00)
            return !currentTime.isBefore(startTime) || !currentTime.isAfter(endTime);
        }
    }
    
    private DayOfWeek convertToDayOfWeek(java.time.DayOfWeek javaDayOfWeek) {
        return switch (javaDayOfWeek) {
            case MONDAY -> DayOfWeek.MONDAY;
            case TUESDAY -> DayOfWeek.TUESDAY;
            case WEDNESDAY -> DayOfWeek.WEDNESDAY;
            case THURSDAY -> DayOfWeek.THURSDAY;
            case FRIDAY -> DayOfWeek.FRIDAY;
            case SATURDAY -> DayOfWeek.SATURDAY;
            case SUNDAY -> DayOfWeek.SUNDAY;
        };
    }

    /**
     * Formats a user's full name from first and last name, handling null values.
     * @param user the user whose name to format
     * @return trimmed full name, or null if the user is null
     */
    private String formatUserFullName(User user) {
        if (user == null) {
            return null;
        }
        String firstName = user.getFirstName() != null ? user.getFirstName() : "";
        String lastName = user.getLastName() != null ? user.getLastName() : "";
        return (firstName + " " + lastName).trim();
    }
}
