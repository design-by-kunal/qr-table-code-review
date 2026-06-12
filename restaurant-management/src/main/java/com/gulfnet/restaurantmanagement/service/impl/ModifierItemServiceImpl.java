package com.gulfnet.restaurantmanagement.service.impl;

import com.gulfnet.restaurantmanagement.service.ModifierItemService;
import com.gulfnet.restaurantmanagement.service.AuditTrailService;
import com.gulfnet.restaurantmanagement.util.MessageUtil;
import com.gulfnet.shared_library.enums.ActionType;
import com.gulfnet.shared_library.entity.Restaurant;
import com.gulfnet.shared_library.repository.RestaurantRepository;
import com.gulfnet.shared_library.entity.ModifierGroup;
import com.gulfnet.shared_library.entity.ModifierItem;
import com.gulfnet.shared_library.entity.ModifierItemTranslation;
import com.gulfnet.shared_library.entity.User;
import com.gulfnet.shared_library.enums.EntityStatus;
import com.gulfnet.shared_library.enums.ModifierType;
import org.springframework.transaction.annotation.Transactional;  // Add this import
import com.gulfnet.restaurantmanagement.config.LocalizationProperties;
import com.gulfnet.shared_library.util.TranslationUtils;  
import com.gulfnet.shared_library.model.request.ModifierItemRequestDto;
import com.gulfnet.shared_library.model.response.dto.*;

import com.gulfnet.shared_library.model.response.dto.ModifierItemListResponse;
import com.gulfnet.shared_library.model.response.dto.ModifierItemListResponseDto;
import com.gulfnet.shared_library.model.response.dto.PaginationMetaData;
import java.util.ArrayList;
import com.gulfnet.shared_library.repository.ModifierGroupRepository;
import com.gulfnet.shared_library.repository.ModifierItemRepository;
import com.gulfnet.shared_library.repository.ModifierItemTranslationRepository;
import com.gulfnet.shared_library.repository.UserRepository;
import com.gulfnet.shared_library.repository.ItemModifierGroupRepository;
import com.gulfnet.shared_library.repository.CategoryItemMappingRepository;
import com.gulfnet.shared_library.repository.RestaurantMenuMappingRepository;
import com.gulfnet.shared_library.enums.RestaurantMenuMappingStatus;
import com.gulfnet.shared_library.entity.CategoryItemMapping;
import com.gulfnet.shared_library.entity.MenuCategoryMapping;
import com.gulfnet.shared_library.entity.RestaurantMenuMapping;
import com.gulfnet.shared_library.entity.ItemModifierGroup;
import java.math.BigDecimal;
import lombok.RequiredArgsConstructor;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;      
import java.util.HashSet;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.data.domain.Sort;
import java.util.Map;
import java.util.function.Function;
import java.util.Objects;
import java.util.Optional;
import com.gulfnet.shared_library.config.AWSService;
import com.gulfnet.shared_library.util.LocaleSortUtil;
import com.gulfnet.shared_library.util.CurrencyFormatter;
import com.gulfnet.restaurantmanagement.config.RestaurantChainConfigProperties;
import java.util.Comparator;

@Service
@RequiredArgsConstructor
public class ModifierItemServiceImpl implements ModifierItemService {
    private static final String MSG_USER_NOT_FOUND = "user.not.found";
    private static final String MSG_MODIFIER_GROUP_NOT_FOUND = "modifier.group.not.found";

    private final ModifierItemRepository modifierItemRepository;
    private final ModifierItemTranslationRepository translationRepository;
    private final ModifierGroupRepository modifierGroupRepository;
    private final UserRepository userRepository;
    private final MessageUtil messageUtil;
    private final LocalizationProperties localizationProperties;  
    private final AWSService awsService;
    private final RestaurantChainConfigProperties restaurantChainConfigProperties;
    private final AuditTrailService auditTrailService;
    private final RestaurantRepository restaurantRepository;
    private final ItemModifierGroupRepository itemModifierGroupRepository;
    private final CategoryItemMappingRepository categoryItemMappingRepository;
    private final RestaurantMenuMappingRepository restaurantMenuMappingRepository;

    /**
     * Creates a new modifier item with translations, image, price, and sort order.
     * Validates translations, modifier group, price requirements (based on modifier type),
     * sort order uniqueness, and default item constraints for SUBSTITUTE type modifiers.
     * Creates an audit trail for the creation.
     *
     * @param userId the ID of the user creating the modifier item
     * @param request the modifier item request containing translations, modifier group ID, image, price, sort order, and status
     * @param locale the locale for localized error messages
     * @return a response containing the created modifier item with translations
     * @throws ResponseStatusException if validation fails, user/modifier group not found, duplicate sort order, or creation fails
     */
    @Override
    @Transactional
    public ResponseDto<ModifierItemDto<ModifierItemResponseDto>> createModifierItem(String userId, ModifierItemRequestDto request, String locale) {
        Locale userLocale = LocaleContextHolder.getLocale();
    
        List<ModifierItemTranslationDto> translations = request.getTranslations();
        if (translations != null && !translations.isEmpty()) {
            // Validate that at least one translation has at least one non-empty field (name OR description)
            boolean hasValidTranslation = translations.stream()
                .anyMatch(t -> hasValidNameOrDescription(t.getName(), t.getDescription()));
            
            if (!hasValidTranslation) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        messageUtil.getMessage("item.create.error.no.valid.name", userLocale));
            }
            
            // Check for duplicate language codes and validate language codes
            validateTranslationsLanguageCodes(translations, userLocale, "create");
        } else {
            // No translations provided at all
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    messageUtil.getMessage("item.create.error.no.translations", userLocale));
        }
    
        // Find user
        User creator = userRepository.findById(UUID.fromString(userId))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        messageUtil.getMessage("modifier.item.create.error.creator.notfound", userLocale)));
    
        // Find modifier group
        ModifierGroup modifierGroup = modifierGroupRepository.findByIdAndIsDeletedFalse(request.getModifierGroupId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        messageUtil.getMessage(MSG_MODIFIER_GROUP_NOT_FOUND, userLocale)));
    
        // Validate price based on modifier group type
        // SUBSTITUTE modifier groups can have null price, ADD_ON must have a price
        if (modifierGroup.getModifierType() != ModifierType.SUBSTITUTE && request.getPrice() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    messageUtil.getMessage("modifier.item.price.required", userLocale));
        }
    
        // Check for duplicate sort order within the same modifier group
        if (modifierItemRepository.existsBySortOrderAndModifierGroupIdAndIsDeletedFalse(request.getSortOrder(), modifierGroup.getId())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    messageUtil.getMessage("modifier.item.create.error.duplicate.sort.order", userLocale, request.getSortOrder()));
        }

        String modifierCode = request.getModifierCode().trim();
        if (modifierItemRepository.existsActiveModifierItemByModifierCode(modifierCode)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    messageUtil.getMessage("modifier.item.modifierCode.exists", userLocale, modifierCode));
        }

        // Validate that for SUBSTITUTE type modifiers, only one item can be marked as default
        if (modifierGroup.getModifierType() == ModifierType.SUBSTITUTE 
                && request.getIsDefault() != null && request.getIsDefault()) {
            List<ModifierItem> existingItems = modifierItemRepository.findByModifierGroup_IdAndIsDeletedFalse(modifierGroup.getId());
            boolean hasExistingDefault = existingItems.stream()
                    .anyMatch(item -> Boolean.TRUE.equals(item.getIsDefault()));
            
            if (hasExistingDefault) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        messageUtil.getMessage("modifier.item.create.error.substitute.multiple.default", userLocale));
            }
        }
    
        // Create modifier item
        ModifierItem modifierItem = new ModifierItem();
        modifierItem.setModifierGroup(modifierGroup);
        modifierItem.setModifierCode(modifierCode);
        modifierItem.setImageUrl(awsService.stripToKey(request.getImageUrl()));
        // For SUBSTITUTE type, price can be null; for ADD_ON, price is required (validated above)
        modifierItem.setPrice(request.getPrice());
        modifierItem.setSortOrder(request.getSortOrder());
        modifierItem.setIsDefault(request.getIsDefault() != null && request.getIsDefault());
        modifierItem.setStatus(request.getStatus());
        modifierItem.setIsDeleted(false);
        modifierItem.setCreatedBy(creator);
        modifierItem.setCreatedAt(OffsetDateTime.now(ZoneOffset.UTC));
    
        modifierItemRepository.save(modifierItem);
    
        // Create translations (for translations with at least one non-empty field)
        List<ModifierItemTranslation> modifierItemTranslations = request.getTranslations().stream()
                .filter(t -> {
                    String name = t.getName();
                    String description = t.getDescription();
                    return (name != null && !name.trim().isEmpty()) 
                        || (description != null && !description.trim().isEmpty());
                })
                .map(t -> {
                    String name = t.getName();
                    String description = t.getDescription();
                    String trimmedName = name != null && !name.trim().isEmpty() ? name.trim() : null;
                    String trimmedDescription = description != null && !description.trim().isEmpty() 
                        ? description.trim() 
                        : null;
                    
                    // Check for duplicate translations (only if name is provided)
                    if (trimmedName != null && !trimmedName.isEmpty()) {
                        boolean translationExists = translationRepository
                                .existsByNameAndLanguageCodeAndModifierItem_ModifierGroup_Id(
                                        trimmedName, t.getLanguageCode(), modifierGroup.getId());
        
                        if (translationExists) {
                            throw new ResponseStatusException(HttpStatus.CONFLICT,
                                    messageUtil.getMessage("modifier.item.create.error.duplicate.translation", userLocale));
                        }
                    }
    
                    return ModifierItemTranslation.builder()
                            .modifierItem(modifierItem)
                            .name(trimmedName)
                            .description(trimmedDescription)
                            .languageCode(t.getLanguageCode())
                            .build();
                })
                .collect(Collectors.toList());
    
        translationRepository.saveAll(modifierItemTranslations);
        modifierItem.setTranslations(modifierItemTranslations);
    
        // Create response
        List<ModifierItemTranslationDto> translationResponses =
                buildTranslationResponses(modifierItemTranslations);
    
        ModifierItemResponseDto itemResponse =
                buildModifierItemResponse(modifierItem, translationResponses, false);
    
        // Create audit trail for modifier item creation
        createModifierAuditTrail(ActionType.MODIFIER_CREATE, "created", modifierItem.getId(), userId, userLocale, translationResponses);

        return ResponseDto.<ModifierItemDto<ModifierItemResponseDto>>builder()
                .message(messageUtil.getMessage("modifier.item.create.success", userLocale))
                .data(new ModifierItemDto<>(itemResponse))
                .build();
    }

    /**
     * Updates an existing modifier item with new translations, image, price, and sort order.
     * Validates translations, modifier group, price requirements, sort order uniqueness,
     * default item constraints, and prevents inactivation if the modifier item is used in live menus.
     * Updates or removes translations based on the request, and creates an audit trail.
     *
     * @param id the ID of the modifier item to update
     * @param request the modifier item request containing updated translations, modifier group ID, image, price, sort order, and status
     * @param userId the ID of the user updating the modifier item
     * @param locale the locale for localized error messages
     * @return a response containing the updated modifier item with translations
     * @throws ResponseStatusException if validation fails, modifier item not found, is deleted, used in live menus, or update fails
     */
    @Override
    @Transactional
    public ResponseDto<ModifierItemDto<ModifierItemResponseDto>> updateModifierItem(UUID id, ModifierItemRequestDto request, String userId, String locale) {
        Locale userLocale = LocaleContextHolder.getLocale();
        validateUpdateTranslations(request.getTranslations(), userLocale);
        User updater = findUserById(userId, userLocale);
        ModifierItem modifierItem = findExistingModifierItem(id, userLocale);
        ModifierGroup modifierGroup = findModifierGroup(request.getModifierGroupId(), userLocale);
        BigDecimal finalPrice = resolveUpdatedPrice(request, modifierItem, modifierGroup, userLocale);
        String modifierCode = request.getModifierCode().trim();

        validateModifierItemUpdate(request, id, modifierItem, modifierGroup, modifierCode, userLocale);
        applyModifierItemUpdates(modifierItem, request, modifierGroup, modifierCode, finalPrice, updater);
        syncModifierItemTranslations(modifierItem, request.getTranslations(), modifierGroup, id, userLocale);
    
        // Save the modifier item which will cascade to translations
        modifierItemRepository.save(modifierItem);
    
        // Create response
        List<ModifierItemTranslationDto> translationResponses =
                buildTranslationResponses(modifierItem.getTranslations());
    
        ModifierItemResponseDto itemResponse =
                buildModifierItemResponse(modifierItem, translationResponses, true);
    
        // Create audit trail for modifier item update
        createModifierAuditTrail(ActionType.MODIFIER_UPDATE, "updated", modifierItem.getId(), userId, userLocale, translationResponses);

        return ResponseDto.<ModifierItemDto<ModifierItemResponseDto>>builder()
                .message(messageUtil.getMessage("modifier.item.update.success", userLocale))
                .data(new ModifierItemDto<>(itemResponse))
                .build();
    }

    /**
     * Common helper to create audit trail entries for modifier item create/update.
     */
    private void createModifierAuditTrail(
            ActionType actionType,
            String actionVerb,
            UUID modifierItemId,
            String userId,
            Locale userLocale,
            List<ModifierItemTranslationDto> translationResponses) {
        try {
            User user = userRepository.findById(UUID.fromString(userId))
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                            messageUtil.getMessage(MSG_USER_NOT_FOUND, userLocale)));
            Restaurant restaurant = null;
            if (user.getRestaurantId() != null) {
                restaurant = restaurantRepository.findById(user.getRestaurantId()).orElse(null);
            }
            String modifierName = translationResponses.isEmpty() ? "No translations" : translationResponses.get(0).getName();
            auditTrailService.createAuditTrail(
                    user,
                    actionType,
                    restaurant,
                    null, // status - will default to NA for non-request actions
                    null, // ipAddress - not available in this context
                    null, // userAgent - not available in this context
                    modifierItemId,
                    "MODIFIER_ITEM",
                    "Modifier Item " + actionVerb + ": " + modifierName
            );
        } catch (Exception e) {
            // Don't break modifier item flow if audit trail fails
        }
    }

    /**
     * Helper to map entity translations to DTO translations.
     */
    private List<ModifierItemTranslationDto> buildTranslationResponses(
            List<ModifierItemTranslation> translations) {
        if (translations == null) {
            return new ArrayList<>();
        }

        return translations.stream()
                .map(t -> ModifierItemTranslationDto.builder()
                        .languageCode(t.getLanguageCode())
                        .name(t.getName())
                        .description(t.getDescription())
                        .build())
                .collect(Collectors.toList());
    }

    /**
     * Helper to build a ModifierItemResponseDto, optionally including updated fields.
     */
    private ModifierItemResponseDto buildModifierItemResponse(
            ModifierItem modifierItem,
            List<ModifierItemTranslationDto> translationResponses,
            boolean includeUpdatedFields) {

        String currency = restaurantChainConfigProperties.getChain() != null
                ? restaurantChainConfigProperties.getChain().getCurrency()
                : null;

        ModifierItemResponseDto.ModifierItemResponseDtoBuilder builder =
                ModifierItemResponseDto.builder()
                        .id(modifierItem.getId())
                        .modifierGroupId(modifierItem.getModifierGroup().getId())
                        .modifierCode(modifierItem.getModifierCode())
                        .imageUrl(modifierItem.getImageUrl() != null && !modifierItem.getImageUrl().isEmpty()
                                ? awsService.getPreSignedUrl(modifierItem.getImageUrl())
                                : null)
                        .price(modifierItem.getPrice() != null
                                ? CurrencyFormatter.formatAmount(modifierItem.getPrice(), currency)
                                : null)
                        .sortOrder(modifierItem.getSortOrder())
                        .isDefault(modifierItem.getIsDefault())
                        .status(modifierItem.getStatus())
                        .isDeleted(modifierItem.getIsDeleted())
                        .createdAt(modifierItem.getCreatedAt() != null ? modifierItem.getCreatedAt().toLocalDateTime() : null)
                        .createdBy(modifierItem.getCreatedBy() != null
                                ? modifierItem.getCreatedBy().getFirstName()
                                : null)
                        .translations(translationResponses);

        if (includeUpdatedFields) {
            builder.updatedAt(modifierItem.getUpdatedAt() != null ? modifierItem.getUpdatedAt().toLocalDateTime() : null);
            builder.updatedBy(modifierItem.getUpdatedBy() != null
                    ? modifierItem.getUpdatedBy().getFirstName()
                    : null);
        }

        return builder.build();
    }

    /**
     * Retrieves detailed information about a single modifier item by its ID, including all translations.
     *
     * @param id the ID of the modifier item to retrieve
     * @param locale the locale for localized error messages
     * @return a response containing the modifier item with all translations
     * @throws ResponseStatusException if the modifier item is not found or is deleted
     */
    @Override
    @Transactional(readOnly = true)
    public ResponseDto<ModifierItemDto<ModifierItemResponseDto>> getModifierItemDetails(UUID id, String locale) {
        Locale userLocale = LocaleContextHolder.getLocale();
    
        // Find modifier item and check if it exists
        ModifierItem modifierItem = modifierItemRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        messageUtil.getMessage("modifier.item.get.error.translation.notfound", userLocale)));
    
        // Check if modifier item is deleted
        if (modifierItem.getIsDeleted()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    messageUtil.getMessage("modifier.item.get.error.deleted", userLocale));
        }
    
        // Get all translations without filtering by locale (detail endpoint should return all translations)
        List<ModifierItemTranslationDto> translationResponses = modifierItem.getTranslations() != null ? 
                modifierItem.getTranslations().stream()
                .map(t -> ModifierItemTranslationDto.builder()
                        .languageCode(t.getLanguageCode())
                        .name(t.getName())
                        .description(t.getDescription())
                        .build())
                .collect(Collectors.toList()) : new ArrayList<>();
    
        String currency = restaurantChainConfigProperties.getChain() != null ? restaurantChainConfigProperties.getChain().getCurrency() : null;
        ModifierItemResponseDto itemResponse = ModifierItemResponseDto.builder()
                .id(modifierItem.getId())
                .modifierGroupId(modifierItem.getModifierGroup() != null ? modifierItem.getModifierGroup().getId() : null)
                .modifierCode(modifierItem.getModifierCode())
                .imageUrl(modifierItem.getImageUrl() != null && !modifierItem.getImageUrl().isEmpty() ? 
                         awsService.getPreSignedUrl(modifierItem.getImageUrl()) : null)
                .price(modifierItem.getPrice() != null ? CurrencyFormatter.formatAmount(modifierItem.getPrice(), currency) : null)
                .sortOrder(modifierItem.getSortOrder())
                .isDefault(modifierItem.getIsDefault())
                .status(modifierItem.getStatus())
                .isDeleted(modifierItem.getIsDeleted())
                .createdAt(modifierItem.getCreatedAt() != null ? modifierItem.getCreatedAt().toLocalDateTime() : null)
                .createdBy(modifierItem.getCreatedBy() != null ? modifierItem.getCreatedBy().getFirstName() : null)
                .updatedAt(modifierItem.getUpdatedAt() != null ? modifierItem.getUpdatedAt().toLocalDateTime() : null)
                .updatedBy(modifierItem.getUpdatedBy() != null ? modifierItem.getUpdatedBy().getFirstName() : null)
                .translations(translationResponses)
                .build();
    
        return ResponseDto.<ModifierItemDto<ModifierItemResponseDto>>builder()
                .message(messageUtil.getMessage("modifier.item.get.success", userLocale))
                .data(new ModifierItemDto<>(itemResponse))
                .build();
    }

    /**
     * Sorts {@code itemDtos} in place by {@code keyExtractor}, honoring {@code sortDirection} ({@code ASC} vs {@code DESC}).
     *
     * @param keyExtractor comparable field read from each DTO (nulls last)
     */
    private static <T extends Comparable<? super T>> void sortModifierItemListInPlace(
            List<ModifierItemListResponseDto> itemDtos,
            Function<ModifierItemListResponseDto, T> keyExtractor,
            String sortDirection) {
        Comparator<ModifierItemListResponseDto> comp = Comparator.comparing(
                keyExtractor,
                Comparator.nullsLast(Comparator.naturalOrder()));
        if (sortDirection != null && sortDirection.equalsIgnoreCase("DESC")) {
            comp = comp.reversed();
        }
        itemDtos.sort(comp);
    }

    /**
     * Retrieves a paginated and filterable list of modifier items for a specific modifier group.
     * Supports filtering by status, searching by name, sorting, and pagination.
     * Returns locale-specific translations for each modifier item.
     *
     * @param modifierGroupId the ID of the modifier group
     * @param status optional filter by entity status
     * @param search optional search term to filter by modifier item name
     * @param page the page number (1-based)
     * @param size the page size
     * @param sortBy the field to sort by
     * @param sortDirection the sort direction (ASC or DESC)
     * @return a response containing a paginated list of modifier items with locale-specific translations
     * @throws ResponseStatusException if the modifier group is not found
     */
    @Override
    @Transactional(readOnly = true)
    public ResponseDto<ModifierItemListResponse> getModifierItemsByGroupId(
            UUID modifierGroupId,
            EntityStatus status,
            String search,
            Integer page,
            Integer size,
            String sortBy,
            String sortDirection) {
        
        Locale userLocale = LocaleContextHolder.getLocale();
        String languageCode = userLocale.getLanguage();

        // Validate modifier group exists
        ModifierGroup modifierGroup = modifierGroupRepository.findByIdAndIsDeletedFalse(modifierGroupId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        messageUtil.getMessage(MSG_MODIFIER_GROUP_NOT_FOUND, userLocale)));

        // Get modifier items for the group with search and status filtering
        final List<ModifierItem> modifierItems;
        if (search != null && !search.trim().isEmpty()) {
            modifierItems = modifierItemRepository.findByModifierGroupIdAndStatusAndSearch(
                    modifierGroupId, status, search.trim());
        } else {
            List<ModifierItem> allItems = modifierItemRepository.findByModifierGroup_IdAndIsDeletedFalse(modifierGroupId);
            
            // Filter by status if provided
            if (status != null) {
                modifierItems = allItems.stream()
                        .filter(item -> item.getStatus() == status)
                        .collect(Collectors.toList());
            } else {
                modifierItems = allItems;
            }
        }


        // Sort the items using LocaleSortUtil for locale-aware sorting
        Sort.Direction direction = Sort.Direction.fromString(sortDirection);
        LocaleSortUtil.sortName(modifierItems, sortBy, direction);

        // Convert to DTOs first (like restaurant service)
        List<ModifierItemListResponseDto> itemDtos = modifierItems.stream()
                .map(modifierItem -> {
                    // Get translation for user's locale with deterministic fallback
                    ModifierItemTranslation translation = modifierItem.getTranslations().stream()
                            .filter(t -> t.getLanguageCode().equalsIgnoreCase(languageCode))
                            .findFirst()
                            .orElseGet(() -> TranslationUtils
                                    .pickPreferredOrFromList(
                                            modifierItem.getTranslations(),
                                            languageCode,
                                            localizationProperties.getLanguages(),
                                            ModifierItemTranslation::getLanguageCode
                                    ).orElse(null));

                    String currency = restaurantChainConfigProperties.getChain() != null ? restaurantChainConfigProperties.getChain().getCurrency() : null;
                    return ModifierItemListResponseDto.builder()
                            .id(modifierItem.getId())
                            .modifierGroupId(modifierItem.getModifierGroup().getId())
                            .modifierCode(modifierItem.getModifierCode())
                            .imageUrl(modifierItem.getImageUrl() != null && !modifierItem.getImageUrl().isEmpty() ? 
                                     awsService.getPreSignedUrl(modifierItem.getImageUrl()) : null)
                            .price(modifierItem.getPrice() != null ? CurrencyFormatter.formatAmount(modifierItem.getPrice(), currency) : null)
                            .sortOrder(modifierItem.getSortOrder())
                            .isDefault(modifierItem.getIsDefault())
                            .status(modifierItem.getStatus())
                            .isDeleted(modifierItem.getIsDeleted())
                            .createdAt(modifierItem.getCreatedAt() != null ? modifierItem.getCreatedAt().toLocalDateTime() : null)
                            .updatedAt(modifierItem.getUpdatedAt() != null ? modifierItem.getUpdatedAt().toLocalDateTime() : null)
                            .name(translation != null ? translation.getName() : null)
                            .description(translation != null ? translation.getDescription() : null)
                            .build();
                })
                .collect(Collectors.toList());


        // Get creator IDs for name mapping
        Set<UUID> creatorIds = modifierItems.stream()
                .map(ModifierItem::getCreatedBy)
                .filter(Objects::nonNull)
                .map(User::getId)
                .collect(Collectors.toSet());

        Map<UUID, String> creatorIdToNameMap = userRepository.findAllById(creatorIds).stream()
                .collect(Collectors.toMap(
                        User::getId,
                        user -> {
                            String firstName = Optional.ofNullable(user.getFirstName()).orElse("");
                            String lastName = Optional.ofNullable(user.getLastName()).orElse("");
                            return (firstName + " " + lastName).trim();
                        }
                ));

        // Set creator names in DTOs
        itemDtos.forEach(dto -> {
            // Find the corresponding modifier item to get creator info
            ModifierItem correspondingItem = modifierItems.stream()
                    .filter(item -> item.getId().equals(dto.getId()))
                    .findFirst()
                    .orElse(null);
            
            if (correspondingItem != null) {
                dto.setCreatedBy(
                    Optional.ofNullable(correspondingItem.getCreatedBy())
                        .map(User::getId)
                        .map(creatorIdToNameMap::get)
                        .orElse(null)
                );
                dto.setUpdatedBy(
                    Optional.ofNullable(correspondingItem.getUpdatedBy())
                        .map(User::getId)
                        .map(creatorIdToNameMap::get)
                        .orElse(null)
                );
            }
        });

        // Apply sorting (in-memory for complex fields like restaurant service)
        if ("name".equalsIgnoreCase(sortBy)) {
            // Set the locale in context for LocaleSortUtil (like restaurant service)
            LocaleContextHolder.setLocale(userLocale);
            LocaleSortUtil.sortName(itemDtos, sortBy, Sort.Direction.fromString(sortDirection));
        } else if ("price".equalsIgnoreCase(sortBy)) {
            sortModifierItemListInPlace(itemDtos, ModifierItemListResponseDto::getPrice, sortDirection);
        } else if ("sortOrder".equalsIgnoreCase(sortBy)) {
            sortModifierItemListInPlace(itemDtos, ModifierItemListResponseDto::getSortOrder, sortDirection);
        } else if ("createdAt".equalsIgnoreCase(sortBy)) {
            sortModifierItemListInPlace(itemDtos, ModifierItemListResponseDto::getCreatedAt, sortDirection);
        }

        // Apply pagination to sorted results (like restaurant service)
        int totalItems = itemDtos.size();
        int adjustedPage = Math.max(0, page - 1);
        int startIndex = adjustedPage * size;
        
        // Check if startIndex is beyond the available items
        if (startIndex >= totalItems) {
            // Return empty result for pages beyond available data
            PaginationMetaData metaData = PaginationMetaData.builder()
                    .page(page)
                    .size(size)
                    .totalPages((int) Math.ceil((double) totalItems / size))
                    .totalRecords((long) totalItems)
                    .build();

            ModifierItemListResponse data = ModifierItemListResponse.builder()
                    .modifierItems(List.of())
                    .count(0L)
                    .total((long) totalItems)
                    .metaData(metaData)
                    .build();

            return ResponseDto.<ModifierItemListResponse>builder()
                    .message(messageUtil.getMessage("modifier.item.list.fetch.success", userLocale))
                    .data(data)
                    .build();
        }
        
        int endIndex = Math.min(startIndex + size, totalItems);
        List<ModifierItemListResponseDto> paginatedDtos = itemDtos.subList(startIndex, endIndex);

        // Calculate pagination metadata
        int totalPages = (int) Math.ceil((double) totalItems / size);
        PaginationMetaData metaData = PaginationMetaData.builder()
                .page(page)
                .size(size)
                .totalPages(totalPages)
                .totalRecords((long) totalItems)
                .build();

        ModifierItemListResponse data = ModifierItemListResponse.builder()
                .modifierItems(paginatedDtos)
                .count((long) paginatedDtos.size())
                .total((long) totalItems)
                .metaData(metaData)
                .build();

        return ResponseDto.<ModifierItemListResponse>builder()
                .message(messageUtil.getMessage("modifier.item.list.fetch.success", userLocale))
                .data(data)
                .build();
    }

    /**
     * Soft-deletes a modifier item by setting its isDeleted flag to true.
     * Only HQ_ADMIN users are allowed to delete modifier items.
     * Prevents deletion if the modifier item is used in live menus.
     * Creates an audit trail for the deletion.
     *
     * @param id the ID of the modifier item to delete
     * @param userId the ID of the user deleting the modifier item
     * @param userRole the role of the user (must be HQ_ADMIN)
     * @return a response containing a success message
     * @throws ResponseStatusException if user is not authorized, modifier item not found, is already deleted, or is used in live menus
     */
    @Override
    @Transactional
    public ResponseDto<Void> deleteModifierItem(UUID id, String userId, String userRole) {
        Locale userLocale = LocaleContextHolder.getLocale();
        
        // Check if user has permission to delete (only HQ Admin)
        if (!"HQ_ADMIN".equals(userRole)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    messageUtil.getMessage("modifier.item.delete.unauthorized", userLocale));
        }
        
        // Find user
        User updater = userRepository.findById(UUID.fromString(userId))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        messageUtil.getMessage(MSG_USER_NOT_FOUND, userLocale)));
        
        // Find modifier item
        ModifierItem modifierItem = modifierItemRepository.findByIdAndIsDeletedFalse(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        messageUtil.getMessage("modifier.item.update.error.notfound", userLocale)));
        
        // Check if already deleted
        if (modifierItem.getIsDeleted()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    messageUtil.getMessage("modifier.item.delete.error.alreadydeleted", userLocale));
        }
        
        // Check if modifier item is used in live menus
        if (isModifierItemUsedInLiveMenus(id)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    messageUtil.getMessage("modifier.item.delete.error.assigned.to.live.menu", userLocale));
        }
        
        // Soft delete
        modifierItem.setIsDeleted(true);
        modifierItem.setUpdatedBy(updater);
        modifierItem.setUpdatedAt(OffsetDateTime.now(ZoneOffset.UTC));
        modifierItemRepository.save(modifierItem);
        
        // Create audit trail for modifier item deletion
        try {
            Restaurant restaurant = null;
            if (updater.getRestaurantId() != null) {
                restaurant = restaurantRepository.findById(updater.getRestaurantId()).orElse(null);
            }
            List<ModifierItemTranslation> modifierTranslations = translationRepository.findAllByModifierItem_Id(modifierItem.getId());
            String modifierName = modifierTranslations.isEmpty() ? "No translations" : modifierTranslations.get(0).getName();
            auditTrailService.createAuditTrail(
                    updater,
                    ActionType.MODIFIER_DELETE,
                    restaurant,
                    null, // status - will default to NA for non-request actions
                    null, // ipAddress - not available in this context
                    null, // userAgent - not available in this context
                    modifierItem.getId(),
                    "MODIFIER_ITEM",
                    "Modifier Item deleted: " + modifierName
            );
        } catch (Exception e) {
            // Don't break modifier item deletion flow if audit trail fails
        }
        
        return ResponseDto.<Void>builder()
                .message(messageUtil.getMessage("modifier.item.delete.success", userLocale))
                .build();
    }

    /**
     * Helper method to check if a modifier item is assigned to a modifier group that is assigned to items in live menus
     * @param modifierItemId The modifier item ID to check
     * @return true if the modifier item is used in any live menu, false otherwise
     */
    private boolean isModifierItemUsedInLiveMenus(UUID modifierItemId) {
        // Get the modifier item
        ModifierItem modifierItem = modifierItemRepository.findById(modifierItemId).orElse(null);
        if (modifierItem == null || modifierItem.getModifierGroup() == null) {
            return false;
        }
        
        // Get the modifier group
        UUID modifierGroupId = modifierItem.getModifierGroup().getId();
        
        // Get all items assigned to this modifier group
        List<ItemModifierGroup> itemModifierGroups = 
                itemModifierGroupRepository.findByModifierGroupIdAndIsDeletedFalse(modifierGroupId);
        
        if (itemModifierGroups.isEmpty()) {
            return false;
        }
        
        // Collect all item IDs
        Set<UUID> itemIds = itemModifierGroups.stream()
                .map(img -> img.getItem().getId())
                .collect(Collectors.toSet());
        
        // For each item, check if it's in any category that's in a menu with LIVE status
        for (UUID itemId : itemIds) {
            // Get all category-item mappings for this item
            List<CategoryItemMapping> itemMappings = 
                    categoryItemMappingRepository.findByItemIdWithCategoryHierarchy(itemId);
            
            if (!itemMappings.isEmpty()) {
                // Collect all menu IDs from the item's category mappings
                Set<UUID> menuIds = new HashSet<>();
                for (CategoryItemMapping mapping : itemMappings) {
                    MenuCategoryMapping menuCategoryMapping = mapping.getMenuCategoryMapping();
                    if (menuCategoryMapping != null && menuCategoryMapping.getMenu() != null) {
                        menuIds.add(menuCategoryMapping.getMenu().getId());
                    }
                }
                
                // Check if any of these menus are assigned to restaurants with LIVE status
                if (!menuIds.isEmpty()) {
                    for (UUID menuId : menuIds) {
                        List<RestaurantMenuMapping> restaurantMenuMappings = 
                                restaurantMenuMappingRepository.findById_MenuId(menuId);
                        for (RestaurantMenuMapping mapping : restaurantMenuMappings) {
                            if (RestaurantMenuMappingStatus.LIVE.equals(mapping.getStatus())) {
                                return true;
                            }
                        }
                    }
                }
            }
        }
        
        return false;
    }

    /**
     * Checks if at least one of name or description is non-empty.
     * This validation is used to ensure translations have at least one valid field.
     * 
     * @param name The name field to check
     * @param description The description field to check
     * @return true if at least one field (name or description) is non-empty, false otherwise
     */
    private boolean hasValidNameOrDescription(String name, String description) {
        return (name != null && !name.trim().isEmpty()) 
                || (description != null && !description.trim().isEmpty());
    }

    /**
     * Validates update translation payloads: non-empty list, at least one meaningful name/description, supported codes.
     *
     * @throws ResponseStatusException on validation failure
     */
    private void validateUpdateTranslations(List<ModifierItemTranslationDto> translations, Locale userLocale) {
        if (translations == null || translations.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    messageUtil.getMessage("combo.translations.required", userLocale));
        }

        boolean hasValidTranslation = translations.stream()
                .anyMatch(t -> hasValidNameOrDescription(t.getName(), t.getDescription()));
        if (!hasValidTranslation) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    messageUtil.getMessage("item.update.error.no.valid.name", userLocale));
        }

        validateTranslationsLanguageCodes(translations, userLocale, "update");
    }

    private User findUserById(String userId, Locale userLocale) {
        return userRepository.findById(UUID.fromString(userId))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        messageUtil.getMessage(MSG_USER_NOT_FOUND, userLocale)));
    }

    private ModifierItem findExistingModifierItem(UUID id, Locale userLocale) {
        ModifierItem modifierItem = modifierItemRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        messageUtil.getMessage("modifier.item.update.error.notfound", userLocale)));
        if (modifierItem.getIsDeleted()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    messageUtil.getMessage("modifier.item.update.error.deleted", userLocale));
        }
        return modifierItem;
    }

    private ModifierGroup findModifierGroup(UUID modifierGroupId, Locale userLocale) {
        return modifierGroupRepository.findByIdAndIsDeletedFalse(modifierGroupId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        messageUtil.getMessage(MSG_MODIFIER_GROUP_NOT_FOUND, userLocale)));
    }

    /**
     * Returns the request price when supplied; otherwise keeps the existing price, requiring a price for non-substitute groups.
     */
    private BigDecimal resolveUpdatedPrice(ModifierItemRequestDto request, ModifierItem modifierItem,
            ModifierGroup modifierGroup, Locale userLocale) {
        if (request.getPrice() != null) {
            return request.getPrice();
        }

        if (modifierGroup.getModifierType() != ModifierType.SUBSTITUTE && modifierItem.getPrice() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    messageUtil.getMessage("modifier.item.price.required", userLocale));
        }

        return modifierItem.getPrice();
    }

    private void validateModifierItemUpdate(ModifierItemRequestDto request, UUID id, ModifierItem modifierItem,
            ModifierGroup modifierGroup, String modifierCode, Locale userLocale) {
        validateSortOrderForUpdate(request, id, modifierGroup, userLocale);
        validateModifierCodeForUpdate(modifierCode, id, userLocale);
        validateDefaultModifierSelection(request, id, modifierGroup, userLocale);
        validateStatusChangeForUpdate(request.getStatus(), modifierItem.getStatus(), id, userLocale);
    }

    private void validateSortOrderForUpdate(ModifierItemRequestDto request, UUID id,
            ModifierGroup modifierGroup, Locale userLocale) {
        if (modifierItemRepository.existsBySortOrderAndModifierGroupIdAndIsDeletedFalseAndIdNot(
                request.getSortOrder(), modifierGroup.getId(), id)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    messageUtil.getMessage("modifier.item.create.error.duplicate.sort.order",
                            userLocale, request.getSortOrder()));
        }
    }

    private void validateModifierCodeForUpdate(String modifierCode, UUID id, Locale userLocale) {
        if (modifierItemRepository.existsActiveModifierItemByModifierCodeExcludingId(modifierCode, id)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    messageUtil.getMessage("modifier.item.modifierCode.exists", userLocale, modifierCode));
        }
    }

    /**
     * For SUBSTITUTE groups, ensures at most one default modifier remains when this item is marked default.
     *
     * @param id updating item id (excluded when scanning existing defaults)
     */
    private void validateDefaultModifierSelection(ModifierItemRequestDto request, UUID id,
            ModifierGroup modifierGroup, Locale userLocale) {
        boolean requestIsDefault = request.getIsDefault() != null && request.getIsDefault();
        if (modifierGroup.getModifierType() != ModifierType.SUBSTITUTE || !requestIsDefault) {
            return;
        }

        boolean hasExistingDefault = modifierItemRepository.findByModifierGroup_IdAndIsDeletedFalse(modifierGroup.getId())
                .stream()
                .filter(item -> !item.getId().equals(id))
                .anyMatch(item -> Boolean.TRUE.equals(item.getIsDefault()));
        if (hasExistingDefault) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    messageUtil.getMessage("modifier.item.create.error.substitute.multiple.default", userLocale));
        }
    }

    private void validateStatusChangeForUpdate(EntityStatus newStatus, EntityStatus currentStatus, UUID id,
            Locale userLocale) {
        if (newStatus == EntityStatus.INACTIVE
                && currentStatus == EntityStatus.ACTIVE
                && isModifierItemUsedInLiveMenus(id)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    messageUtil.getMessage("modifier.item.update.error.cannot.inactivate.in.live.menu", userLocale));
        }
    }

    /**
     * Applies scalar field updates from the request onto {@code modifierItem} (code, image key, price, flags, audit).
     */
    private void applyModifierItemUpdates(ModifierItem modifierItem, ModifierItemRequestDto request,
            ModifierGroup modifierGroup, String modifierCode, BigDecimal finalPrice, User updater) {
        modifierItem.setModifierGroup(modifierGroup);
        modifierItem.setModifierCode(modifierCode);
        modifierItem.setImageUrl(awsService.stripToKey(request.getImageUrl()));
        modifierItem.setPrice(finalPrice);
        modifierItem.setSortOrder(request.getSortOrder());
        modifierItem.setIsDefault(request.getIsDefault() != null && request.getIsDefault());
        modifierItem.setStatus(request.getStatus());
        modifierItem.setUpdatedBy(updater);
        modifierItem.setUpdatedAt(OffsetDateTime.now(ZoneOffset.UTC));
    }

    private void syncModifierItemTranslations(ModifierItem modifierItem,
            List<ModifierItemTranslationDto> requestTranslations, ModifierGroup modifierGroup, UUID modifierItemId,
            Locale userLocale) {
        Set<String> requestLanguageCodes = requestTranslations.stream()
                .filter(t -> hasValidNameOrDescription(t.getName(), t.getDescription()))
                .map(ModifierItemTranslationDto::getLanguageCode)
                .collect(Collectors.toSet());

        removeObsoleteTranslations(modifierItem, requestLanguageCodes);
        upsertModifierItemTranslations(modifierItem, requestTranslations, modifierGroup, modifierItemId, userLocale);
    }

    private void removeObsoleteTranslations(ModifierItem modifierItem, Set<String> requestLanguageCodes) {
        List<ModifierItemTranslation> translationsToRemove = modifierItem.getTranslations().stream()
                .filter(t -> !requestLanguageCodes.contains(t.getLanguageCode()))
                .collect(Collectors.toList());

        for (ModifierItemTranslation translationToRemove : translationsToRemove) {
            modifierItem.getTranslations().remove(translationToRemove);
            translationRepository.delete(translationToRemove);
        }
    }

    /**
     * Creates or updates {@link ModifierItemTranslation} rows from the request, skipping blank entries and enforcing uniqueness.
     */
    private void upsertModifierItemTranslations(ModifierItem modifierItem,
            List<ModifierItemTranslationDto> requestTranslations, ModifierGroup modifierGroup, UUID modifierItemId,
            Locale userLocale) {
        for (ModifierItemTranslationDto translationDto : requestTranslations) {
            if (!hasValidNameOrDescription(translationDto.getName(), translationDto.getDescription())) {
                continue;
            }

            String trimmedName = trimToNull(translationDto.getName());
            String trimmedDescription = trimToNull(translationDto.getDescription());
            validateDuplicateModifierItemTranslation(trimmedName, translationDto.getLanguageCode(),
                    modifierGroup.getId(), modifierItemId, userLocale);

            ModifierItemTranslation translation = modifierItem.getTranslations().stream()
                    .filter(t -> t.getLanguageCode().equals(translationDto.getLanguageCode()))
                    .findFirst()
                    .orElseGet(() -> createNewModifierItemTranslation(modifierItem));

            translation.setName(trimmedName);
            translation.setDescription(trimmedDescription);
            translation.setLanguageCode(translationDto.getLanguageCode());
        }
    }

    private String trimToNull(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        return value.trim();
    }

    /**
     * Ensures another modifier item in the same group does not already use {@code trimmedName} for {@code languageCode}.
     *
     * @param modifierItemId updating item id (excluded from the duplicate check)
     */
    private void validateDuplicateModifierItemTranslation(String trimmedName, String languageCode,
            UUID modifierGroupId, UUID modifierItemId, Locale userLocale) {
        if (trimmedName == null || trimmedName.isEmpty()) {
            return;
        }

        boolean translationExists = translationRepository
                .existsByNameAndLanguageCodeAndModifierItem_ModifierGroup_IdAndModifierItem_IdNot(
                        trimmedName, languageCode, modifierGroupId, modifierItemId);
        if (translationExists) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    messageUtil.getMessage("modifier.item.update.error.duplicate.translation", userLocale));
        }
    }

    private ModifierItemTranslation createNewModifierItemTranslation(ModifierItem modifierItem) {
        ModifierItemTranslation newTranslation = new ModifierItemTranslation();
        newTranslation.setModifierItem(modifierItem);
        modifierItem.getTranslations().add(newTranslation);
        return newTranslation;
    }

    /**
     * Validates language codes in translations for duplicates and supported languages.
     * Only validates translations with at least one non-empty field (name OR description).
     * 
     * @param translations The list of translations to validate
     * @param userLocale The user's locale for error messages
     * @param operation The operation type ("create" or "update") for error message keys
     * @throws ResponseStatusException if duplicate language codes are found or invalid language codes are detected
     */
    private void validateTranslationsLanguageCodes(
            List<ModifierItemTranslationDto> translations, 
            Locale userLocale, 
            String operation) {
        Set<String> languageCodes = new HashSet<>();
        for (ModifierItemTranslationDto translation : translations) {
            String name = translation.getName();
            String description = translation.getDescription();
            String languageCode = translation.getLanguageCode();
            
            // Only validate translations with at least one non-empty field (name OR description)
            if (hasValidNameOrDescription(name, description) && languageCode != null) {
                // Check if language code is supported
                if (!localizationProperties.getLanguages().contains(languageCode)) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                            messageUtil.getMessage("modifier.item." + operation + ".error.invalid.language", 
                                userLocale,
                                new Object[]{languageCode, String.join(",", localizationProperties.getLanguages())}));
                }
        
                // Check for duplicates
                if (!languageCodes.add(languageCode)) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                            messageUtil.getMessage("modifier.item." + operation + ".error.duplicate.language", userLocale));
                }
            }
        }
    }
}