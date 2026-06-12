package com.gulfnet.restaurantmanagement.service.impl;

import com.gulfnet.restaurantmanagement.service.ModifierGroupService;
import com.gulfnet.restaurantmanagement.service.AuditTrailService;
import com.gulfnet.restaurantmanagement.util.MessageUtil;
import com.gulfnet.shared_library.enums.ActionType;
import com.gulfnet.shared_library.entity.Restaurant;
import com.gulfnet.shared_library.repository.RestaurantRepository;
import com.gulfnet.shared_library.entity.ModifierGroup;
import com.gulfnet.shared_library.entity.ModifierGroupTranslation;
import com.gulfnet.shared_library.entity.User;
import com.gulfnet.shared_library.enums.EntityStatus;
import com.gulfnet.shared_library.enums.ModifierType;
import com.gulfnet.shared_library.model.request.ModifierGroupRequestDto;
import com.gulfnet.shared_library.model.request.ModifierGroupTranslationRequestDto;
import com.gulfnet.shared_library.model.response.dto.ModifierGroupDto;
import com.gulfnet.shared_library.model.response.dto.ModifierGroupResponse;
import com.gulfnet.shared_library.model.response.dto.ModifierGroupTranslationDto;
import com.gulfnet.shared_library.model.response.dto.ResponseDto;
import com.gulfnet.shared_library.model.response.dto.ModifierGroupListResponse;
import com.gulfnet.shared_library.model.response.dto.ModifierGroupBasicDetailsResponse;
import com.gulfnet.shared_library.model.response.dto.ModifierGroupDetailsResponse;
import com.gulfnet.shared_library.model.response.dto.PaginationMetaData;
import com.gulfnet.shared_library.repository.ModifierGroupRepository;
import com.gulfnet.shared_library.repository.ModifierGroupTranslationRepository;
import com.gulfnet.shared_library.repository.ModifierItemRepository;
import com.gulfnet.shared_library.repository.ModifierItemTranslationRepository;
import com.gulfnet.shared_library.repository.UserRepository;
import com.gulfnet.shared_library.repository.ItemModifierGroupRepository;
import com.gulfnet.shared_library.repository.CategoryItemMappingRepository;
import com.gulfnet.shared_library.entity.ItemModifierGroup;
import com.gulfnet.shared_library.entity.ModifierItem;
import com.gulfnet.shared_library.entity.ModifierItemTranslation;
import com.gulfnet.shared_library.repository.RestaurantMenuMappingRepository;
import com.gulfnet.shared_library.enums.RestaurantMenuMappingStatus;
import com.gulfnet.shared_library.entity.CategoryItemMapping;
import com.gulfnet.shared_library.entity.MenuCategoryMapping;
import com.gulfnet.shared_library.entity.RestaurantMenuMapping;
import com.gulfnet.shared_library.util.LocaleSortUtil;
import com.gulfnet.shared_library.util.TranslationUtils;
import com.gulfnet.restaurantmanagement.config.LocalizationProperties;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import jakarta.persistence.criteria.Predicate;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.*;
import java.util.stream.Collectors;
@Service
@RequiredArgsConstructor
public class ModifierGroupServiceImpl implements ModifierGroupService {
    
    // Constants for message keys
    private static final String MSG_MODIFIER_GROUP_USER_NOT_FOUND = "user.not.found";
    private static final String MSG_MODIFIER_GROUP_NOT_FOUND = "modifier.group.not.found";
    
    // Constants for action types
    private static final String ACTION_TYPE_MODIFIER_GROUP = "MODIFIER_GROUP";
    
    // Constants for field names
    private static final String FIELD_IS_DELETED = "isDeleted";
    
    // Constants for default values
    private static final String DEFAULT_NO_TRANSLATIONS = "No translations";
    
    private final UserRepository userRepository;
    private final MessageUtil messageUtil;
    private final ModifierGroupRepository modifierGroupRepository;
    private final ModifierGroupTranslationRepository translationRepository;
    private final ModifierItemRepository modifierItemRepository;
    private final ModifierItemTranslationRepository modifierItemTranslationRepository;
    private final ItemModifierGroupRepository itemModifierGroupRepository;
    private final CategoryItemMappingRepository categoryItemMappingRepository;
    private final LocalizationProperties localizationProperties;
    private final AuditTrailService auditTrailService;
    private final RestaurantRepository restaurantRepository;
    private final RestaurantMenuMappingRepository restaurantMenuMappingRepository;

    /**
     * Creates a new modifier group with translations and modifier items.
     * Validates translations, creates modifier group entity, and associates modifier items.
     *
     * @param request    the modifier group request containing group details and translations
     * @param creatorId  the ID of the user creating the modifier group
     * @param creatorRole the role of the user creating the modifier group
     * @return {@link ResponseDto} containing the created modifier group details
     * @throws ResponseStatusException if creator user not found or validation fails
     */
    @Transactional
    public ResponseDto<ModifierGroupDto<ModifierGroupResponse>> createModifierGroup(
            ModifierGroupRequestDto request,
            String creatorId,
            String creatorRole) {

        Locale userLocale = LocaleContextHolder.getLocale();

        User creator = userRepository.findById(UUID.fromString(creatorId))
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        messageUtil.getMessage(MSG_MODIFIER_GROUP_USER_NOT_FOUND, userLocale)
                ));

        List<ModifierGroupTranslationRequestDto> translations = request.getTranslations();
        if (translations != null && !translations.isEmpty()) {
            // Validate that at least one translation has a non-empty name
            boolean hasValidName = translations.stream()
                .anyMatch(t -> t.getName() != null && !t.getName().trim().isEmpty());
            
            if (!hasValidName) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        messageUtil.getMessage("item.update.error.no.valid.name", userLocale));
            }
            
            // Check for duplicate language codes and validate language codes
            Set<String> languageCodes = new HashSet<>();
            for (ModifierGroupTranslationRequestDto translation : translations) {
                String name = translation.getName();
                String languageCode = translation.getLanguageCode();
                
                // Only validate non-empty names
                if (name != null && !name.trim().isEmpty() && languageCode != null) {
                    if (!localizationProperties.getLanguages().contains(languageCode)) {
                        throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                                messageUtil.getMessage("error.invalid.language", userLocale, languageCode));
                    }
                    if (!languageCodes.add(languageCode)) {
                        throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                                messageUtil.getMessage("modifier.group.create.error.duplicate.language", userLocale, languageCode));
                    }
                    
                    // Check for duplicate names in the same language
                    boolean duplicateExists = translationRepository.existsByNameIgnoreCaseAndLanguageCodeAndModifierGroupIsDeletedFalse(
                            name.trim(), languageCode);
                    if (duplicateExists) {
                        throw new ResponseStatusException(HttpStatus.CONFLICT,
                                messageUtil.getMessage("modifier.group.duplicate.name", userLocale));
                    }
                }
            }
        } else {
            // No translations provided at all
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    messageUtil.getMessage("combo.translations.required", userLocale));
        }


        ModifierGroup modifierGroup = ModifierGroup.builder()
                .modifierType(ModifierType.valueOf(String.valueOf(request.getModifierType())))
                .allowMultiSelect(request.getAllowMultiSelect())
                .minLimit(request.getMinLimit())
                .maxLimit(request.getMaxLimit())
                .status(EntityStatus.valueOf(String.valueOf(request.getStatus())))
                .isDeleted(false)
                .createdAt(OffsetDateTime.now(ZoneOffset.UTC))
                .updatedAt(OffsetDateTime.now(ZoneOffset.UTC))
                .createdBy(creator)
                .updatedBy(creator)
                .build();


        ModifierGroup savedGroup = modifierGroupRepository.save(modifierGroup);

        List<ModifierGroupTranslation> modifierGroupTranslations = request.getTranslations().stream()
                .filter(t -> t.getName() != null && !t.getName().trim().isEmpty())
                .map(t -> ModifierGroupTranslation.builder()
                        .languageCode(t.getLanguageCode())
                        .name(t.getName().trim())
                        .description(t.getDescription())
                        .modifierGroup(savedGroup)
                        .build())
                .collect(Collectors.toList());

        translationRepository.saveAll(modifierGroupTranslations);
        savedGroup.setTranslations(modifierGroupTranslations);

        List<ModifierGroupTranslationDto> translationDtos = modifierGroupTranslations.stream()
                .map(t -> ModifierGroupTranslationDto.builder()
                        .languageCode(t.getLanguageCode())
                        .name(t.getName())
                        .description(t.getDescription())
                        .build())
                .collect(Collectors.toList());

        ModifierGroupResponse response = ModifierGroupResponse.builder()
                .id(savedGroup.getId())
                .modifierType(savedGroup.getModifierType())
                .allowMultiSelect(savedGroup.getAllowMultiSelect())
                .minLimit(savedGroup.getMinLimit())
                .maxLimit(savedGroup.getMaxLimit())
                .status(savedGroup.getStatus())
                .isDeleted(savedGroup.getIsDeleted())
                .translations(translationDtos)
                .build();

        ModifierGroupDto<ModifierGroupResponse> dto = ModifierGroupDto.<ModifierGroupResponse>builder()
                .modifierGroup(response)
                .build();

        // Create audit trail for modifier group creation
        try {
            Restaurant restaurant = null;
            if (creator.getRestaurantId() != null) {
                restaurant = restaurantRepository.findById(creator.getRestaurantId()).orElse(null);
            }
            String modifierName = translationDtos.isEmpty() ? DEFAULT_NO_TRANSLATIONS : translationDtos.get(0).getName();
            auditTrailService.createAuditTrail(
                    creator,
                    ActionType.MODIFIER_CREATE,
                    restaurant,
                    null, // status - will default to NA for non-request actions
                    null, // ipAddress - not available in this context
                    null, // userAgent - not available in this context
                    savedGroup.getId(),
                    ACTION_TYPE_MODIFIER_GROUP,
                    "Modifier Group created: " + modifierName
            );
        } catch (Exception e) {
            // Don't break modifier group creation flow if audit trail fails
        }

        return ResponseDto.<ModifierGroupDto<ModifierGroupResponse>>builder()
                .message(messageUtil.getMessage("modifier.group.create.success", userLocale))
                .data(dto)
                .build();
    }

    /**
     * Updates an existing modifier group with new details, translations, and modifier items.
     * Validates translations, updates modifier group entity, and manages modifier item associations.
     *
     * @param modifierGroupId the UUID of the modifier group to update
     * @param request         the modifier group request containing updated details
     * @param updaterId       the ID of the user updating the modifier group
     * @param updaterRole     the role of the user updating the modifier group
     * @return {@link ResponseDto} containing the updated modifier group details
     * @throws ResponseStatusException if modifier group not found, updater user not found, or validation fails
     */
    @Transactional
    public ResponseDto<ModifierGroupDto<ModifierGroupResponse>> updateModifierGroup(
            UUID modifierGroupId,
            ModifierGroupRequestDto request,
            String updaterId,
            String updaterRole) {
    
        Locale userLocale = LocaleContextHolder.getLocale();
    
        User updater = requireUser(updaterId, userLocale);
        ModifierGroup modifierGroup = requireModifierGroup(modifierGroupId, userLocale);

        List<ModifierGroupTranslationRequestDto> translations = request.getTranslations();
        validateModifierGroupTranslationsForUpdate(translations, modifierGroupId, userLocale);

        List<ModifierGroupTranslation> newTranslations = replaceModifierGroupTranslations(modifierGroup, modifierGroupId, translations);
    
        // Validate status change - Don't allow changing from ACTIVE to INACTIVE if modifier group is used in live menus
        EntityStatus newStatus = request.getStatus();
        EntityStatus currentStatus = modifierGroup.getStatus();
        
        if (newStatus == EntityStatus.INACTIVE && currentStatus == EntityStatus.ACTIVE
                && isModifierGroupUsedInLiveMenus(modifierGroupId)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    messageUtil.getMessage("modifier.group.update.error.cannot.inactivate.in.live.menu", userLocale)
            );
        }
        
        modifierGroup.setModifierType(ModifierType.valueOf(request.getModifierType().name()));
        modifierGroup.setAllowMultiSelect(request.getAllowMultiSelect());
        modifierGroup.setMinLimit(request.getMinLimit());
        modifierGroup.setMaxLimit(request.getMaxLimit());
        modifierGroup.setStatus(newStatus);
        modifierGroup.setUpdatedAt(OffsetDateTime.now(ZoneOffset.UTC));
        modifierGroup.setUpdatedBy(updater);
    
        ModifierGroup updatedGroup = modifierGroupRepository.save(modifierGroup);
    
        List<ModifierGroupTranslationDto> translationDtos = newTranslations.stream()
                .map(t -> ModifierGroupTranslationDto.builder()
                        .languageCode(t.getLanguageCode())
                        .name(t.getName())
                        .description(t.getDescription())
                        .build())
                .collect(Collectors.toList());
    
        ModifierGroupResponse response = ModifierGroupResponse.builder()
                .id(updatedGroup.getId())
                .modifierType(updatedGroup.getModifierType())
                .allowMultiSelect(updatedGroup.getAllowMultiSelect())
                .minLimit(updatedGroup.getMinLimit())
                .maxLimit(updatedGroup.getMaxLimit())
                .status(updatedGroup.getStatus())
                .isDeleted(updatedGroup.getIsDeleted())
                .translations(translationDtos)
                .build();
    
        ModifierGroupDto<ModifierGroupResponse> dto = ModifierGroupDto.<ModifierGroupResponse>builder()
                .modifierGroup(response)
                .build();

        // Create audit trail for modifier group update
        try {
            Restaurant restaurant = null;
            if (updater.getRestaurantId() != null) {
                restaurant = restaurantRepository.findById(updater.getRestaurantId()).orElse(null);
            }
            String modifierName = translationDtos.isEmpty() ? DEFAULT_NO_TRANSLATIONS : translationDtos.get(0).getName();
            auditTrailService.createAuditTrail(
                    updater,
                    ActionType.MODIFIER_UPDATE,
                    restaurant,
                    null, // status - will default to NA for non-request actions
                    null, // ipAddress - not available in this context
                    null, // userAgent - not available in this context
                    updatedGroup.getId(),
                    ACTION_TYPE_MODIFIER_GROUP,
                    "Modifier Group updated: " + modifierName
            );
        } catch (Exception e) {
            // Don't break modifier group update flow if audit trail fails
        }

        return ResponseDto.<ModifierGroupDto<ModifierGroupResponse>>builder()
                .message(messageUtil.getMessage("modifier.group.update.success", userLocale))
                .data(dto)
                .build();
    }

    private User requireUser(String userId, Locale userLocale) {
        return userRepository.findById(UUID.fromString(userId))
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        messageUtil.getMessage(MSG_MODIFIER_GROUP_USER_NOT_FOUND, userLocale)
                ));
    }

    private ModifierGroup requireModifierGroup(UUID modifierGroupId, Locale userLocale) {
        return modifierGroupRepository.findById(modifierGroupId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        messageUtil.getMessage(MSG_MODIFIER_GROUP_NOT_FOUND, userLocale)
                ));
    }

    private void validateModifierGroupTranslationsForUpdate(List<ModifierGroupTranslationRequestDto> translations,
                                                           UUID modifierGroupId,
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

        Set<String> languageCodes = new HashSet<>();
        for (ModifierGroupTranslationRequestDto translation : translations) {
            String name = translation.getName();
            String languageCode = translation.getLanguageCode();
            String trimmedName = name != null ? name.trim() : null;
            boolean skip = trimmedName == null || trimmedName.isEmpty() || languageCode == null;
            if (skip) {
                continue;
            }
            if (!localizationProperties.getLanguages().contains(languageCode)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        messageUtil.getMessage("error.invalid.language", userLocale, languageCode));
            }
            if (!languageCodes.add(languageCode)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        messageUtil.getMessage("modifier.group.update.error.duplicate.language", userLocale, languageCode));
            }
            boolean exists = translationRepository
                    .existsByNameIgnoreCaseAndLanguageCodeAndModifierGroupIsDeletedFalseAndModifierGroupIdNot(
                            trimmedName, languageCode, modifierGroupId);
            if (exists) {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        messageUtil.getMessage("modifier.group.duplicate.name", userLocale));
            }
        }
    }

    private List<ModifierGroupTranslation> replaceModifierGroupTranslations(ModifierGroup modifierGroup,
                                                                           UUID modifierGroupId,
                                                                           List<ModifierGroupTranslationRequestDto> translations) {
        translationRepository.deleteAllByModifierGroup_Id(modifierGroupId);
        translationRepository.flush();

        List<ModifierGroupTranslation> newTranslations = translations.stream()
                .filter(t -> t.getName() != null && !t.getName().trim().isEmpty())
                .map(t -> translationRepository.save(
                        ModifierGroupTranslation.builder()
                                .modifierGroup(modifierGroup)
                                .languageCode(t.getLanguageCode())
                                .name(t.getName().trim())
                                .description(t.getDescription())
                                .build()))
                .collect(Collectors.toList());

        modifierGroup.getTranslations().clear();
        modifierGroup.getTranslations().addAll(newTranslations);
        return newTranslations;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional(readOnly = true)
    public ResponseDto<ModifierGroupListResponse> getModifierGroups(
            EntityStatus status,
            ModifierType modifierType,
            Boolean allowMultiSelect,
            UUID itemId,
            String search,
            Integer page,
            Integer size,
            String sortBy,
            String sortDirection,
            Boolean isDeleted) {

        Locale userLocale = LocaleContextHolder.getLocale();
        String languageCode = userLocale.getLanguage();
        ModifierGroupQuery query = new ModifierGroupQuery(status, modifierType, allowMultiSelect, itemId, search, page, size, sortBy, sortDirection, isDeleted);

        Page<ModifierGroup> groupPage = loadModifierGroupsPage(query, languageCode);
        ModifierGroupListResponse data = buildModifierGroupListResponse(groupPage, query, languageCode);

        return ResponseDto.<ModifierGroupListResponse>builder()
                .message(messageUtil.getMessage("modifier.group.fetch.success", userLocale))
                .data(data)
                .build();
    }

    private record ModifierGroupQuery(
            EntityStatus status,
            ModifierType modifierType,
            Boolean allowMultiSelect,
            UUID itemId,
            String search,
            Integer page,
            Integer size,
            String sortBy,
            String sortDirection,
            Boolean isDeleted
    ) {
        boolean usePagination() {
            return page != null && size != null;
        }

        boolean hasSearch() {
            return search != null && !search.trim().isEmpty();
        }

        int adjustedPage() {
            return Math.max(0, (page != null ? page : 1) - 1);
        }

        boolean requiresInMemorySorting() {
            return hasSearch() || itemId != null || (sortBy != null && "name".equalsIgnoreCase(sortBy));
        }
    }

    private Page<ModifierGroup> loadModifierGroupsPage(ModifierGroupQuery query, String languageCode) {
        Sort sort = resolveCreatedAtSort(query.sortBy(), query.sortDirection());
        Pageable pageable = buildPageable(query, sort);

        if (!query.requiresInMemorySorting()) {
            Specification<ModifierGroup> spec = buildModifierGroupSpecification(query.status(), query.modifierType(), query.allowMultiSelect(), query.isDeleted());
            if (query.usePagination()) {
                return modifierGroupRepository.findAll(spec, pageable);
            }
            List<ModifierGroup> groups = modifierGroupRepository.findAll(spec);
            sortModifierGroups(groups, query.sortBy(), query.sortDirection(), languageCode);
            return new PageImpl<>(groups, PageRequest.of(0, groups.size()), groups.size());
        }

        List<ModifierGroup> groups = loadGroupsForInMemoryFlow(query);
        if (groups.isEmpty()) {
            return new PageImpl<>(new ArrayList<>(), PageRequest.of(0, 1), 0);
        }

        maybeLoadTranslationsForNameSort(query.sortBy(), groups);
        groups = applySearchIfNeeded(query, groups);
        sortModifierGroups(groups, query.sortBy(), query.sortDirection(), languageCode);
        return paginateIfNeeded(query, groups);
    }

    private Sort resolveCreatedAtSort(String sortBy, String sortDirection) {
        if (sortBy != null && "createdAt".equalsIgnoreCase(sortBy)) {
            return Sort.by(Sort.Direction.fromString(sortDirection), sortBy);
        }
        return null;
    }

    private Pageable buildPageable(ModifierGroupQuery query, Sort sort) {
        if (!query.usePagination()) {
            return null;
        }
        int adjustedPage = query.adjustedPage();
        int size = query.size();
        if (sort != null) {
            return PageRequest.of(adjustedPage, size, sort);
        }
        return PageRequest.of(adjustedPage, size);
    }

    private List<ModifierGroup> loadGroupsForInMemoryFlow(ModifierGroupQuery query) {
        if (query.itemId() != null) {
            List<ItemModifierGroup> itemModifierGroups =
                    itemModifierGroupRepository.findByItemIdAndIsDeletedFalse(query.itemId());
            if (itemModifierGroups == null || itemModifierGroups.isEmpty()) {
                return new ArrayList<>();
            }
            Set<UUID> assignedModifierGroupIds = itemModifierGroups.stream()
                    .map(assignment -> assignment.getModifierGroup().getId())
                    .collect(Collectors.toSet());
            Specification<ModifierGroup> itemIdSpec = (root, q, cb) -> {
                List<Predicate> predicates = new ArrayList<>();
                predicates.add(root.get("id").in(assignedModifierGroupIds));
                if (query.isDeleted() != null && query.isDeleted()) {
                    predicates.add(cb.equal(root.get(FIELD_IS_DELETED), true));
                } else {
                    predicates.add(cb.equal(root.get(FIELD_IS_DELETED), false));
                }
                return cb.and(predicates.toArray(new Predicate[0]));
            };
            List<ModifierGroup> groups = modifierGroupRepository.findAll(itemIdSpec);
            return applyFiltersForItemIdFlow(query, groups);
        }

        Specification<ModifierGroup> spec = buildModifierGroupSpecification(query.status(), query.modifierType(), query.allowMultiSelect(), query.isDeleted());
        return modifierGroupRepository.findAll(spec);
    }

    private List<ModifierGroup> applyFiltersForItemIdFlow(ModifierGroupQuery query, List<ModifierGroup> groups) {
        if (groups == null) {
            return new ArrayList<>();
        }
        return groups.stream()
                .filter(group -> {
                    if (query.status() != null && group.getStatus() != query.status()) {
                        return false;
                    }
                    if (query.modifierType() != null && group.getModifierType() != query.modifierType()) {
                        return false;
                    }
                    return query.allowMultiSelect() == null || group.getAllowMultiSelect().equals(query.allowMultiSelect());
                })
                .collect(Collectors.toList());
    }

    private void maybeLoadTranslationsForNameSort(String sortBy, List<ModifierGroup> groups) {
        if (sortBy == null || !"name".equalsIgnoreCase(sortBy) || groups == null || groups.isEmpty()) {
            return;
        }
        List<UUID> groupIds = groups.stream().map(ModifierGroup::getId).collect(Collectors.toList());
        List<ModifierGroupTranslation> allTranslations = translationRepository.findAllByModifierGroupIdIn(groupIds);
        Map<UUID, List<ModifierGroupTranslation>> translationsByGroupId = allTranslations.stream()
                .collect(Collectors.groupingBy(t -> t.getModifierGroup().getId()));
        for (ModifierGroup group : groups) {
            List<ModifierGroupTranslation> translations = translationsByGroupId.getOrDefault(group.getId(), new ArrayList<>());
            if (group.getTranslations() == null) {
                group.setTranslations(new ArrayList<>());
            }
            group.getTranslations().clear();
            group.getTranslations().addAll(translations);
        }
    }

    private List<ModifierGroup> applySearchIfNeeded(ModifierGroupQuery query, List<ModifierGroup> groups) {
        if (!query.hasSearch() || groups == null) {
            return groups;
        }
        String searchTerm = query.search().trim().toLowerCase();
        eagerLoadModifierItemsForSearch(groups);
        return groups.stream()
                .filter(group -> matchesSearch(group, searchTerm))
                .collect(Collectors.toList());
    }

    private void eagerLoadModifierItemsForSearch(List<ModifierGroup> groups) {
        List<UUID> groupIds = groups.stream().map(ModifierGroup::getId).collect(Collectors.toList());
        if (groupIds.isEmpty()) {
            return;
        }
        List<ModifierItem> allItems = modifierItemRepository.findByModifierGroup_IdInAndIsDeletedFalse(groupIds);
        Map<UUID, List<ModifierItem>> itemsByGroupId = allItems.stream()
                .collect(Collectors.groupingBy(item -> item.getModifierGroup().getId()));

        List<UUID> itemIds = allItems.stream().map(ModifierItem::getId).collect(Collectors.toList());
        Map<UUID, List<ModifierItemTranslation>> translationsByItemId = new HashMap<>();
        if (!itemIds.isEmpty()) {
            List<ModifierItemTranslation> allTranslations = modifierItemTranslationRepository.findAllByModifierItem_IdIn(itemIds);
            translationsByItemId = allTranslations.stream()
                    .collect(Collectors.groupingBy(t -> t.getModifierItem().getId()));
        }

        for (ModifierGroup group : groups) {
            List<ModifierItem> items = itemsByGroupId.getOrDefault(group.getId(), new ArrayList<>());
            if (group.getModifierItems() == null) {
                group.setModifierItems(new ArrayList<>());
            }
            group.getModifierItems().clear();
            group.getModifierItems().addAll(items);

            for (ModifierItem item : items) {
                List<ModifierItemTranslation> translations = translationsByItemId.get(item.getId());
                if (translations != null && !translations.isEmpty()) {
                    if (item.getTranslations() == null) {
                        item.setTranslations(new ArrayList<>());
                    }
                    item.getTranslations().clear();
                    item.getTranslations().addAll(translations);
                }
            }
        }
    }

    private boolean matchesSearch(ModifierGroup group, String searchTerm) {
        boolean groupNameMatches = group.getTranslations() != null && group.getTranslations().stream()
                .anyMatch(translation ->
                        translation.getName() != null &&
                                translation.getName().toLowerCase().contains(searchTerm)
                );

        boolean itemNameMatches = group.getModifierItems() != null && group.getModifierItems().stream()
                .filter(item -> !Boolean.TRUE.equals(item.getIsDeleted()))
                .flatMap(item -> {
                    if (item.getTranslations() == null || item.getTranslations().isEmpty()) {
                        return java.util.stream.Stream.empty();
                    }
                    return item.getTranslations().stream();
                })
                .anyMatch(t -> t.getName() != null && t.getName().toLowerCase().contains(searchTerm));

        return groupNameMatches || itemNameMatches;
    }

    private Page<ModifierGroup> paginateIfNeeded(ModifierGroupQuery query, List<ModifierGroup> groups) {
        if (!query.usePagination() || query.size() == null) {
            return new PageImpl<>(groups, PageRequest.of(0, groups.size()), groups.size());
        }
        int totalResults = groups.size();
        int start = query.adjustedPage() * query.size();
        int end = Math.min(start + query.size(), totalResults);
        List<ModifierGroup> paginatedGroups = start < totalResults ? groups.subList(start, end) : new ArrayList<>();
        return new PageImpl<>(paginatedGroups, PageRequest.of(query.adjustedPage(), query.size()), totalResults);
    }

    private ModifierGroupListResponse buildModifierGroupListResponse(Page<ModifierGroup> groupPage, ModifierGroupQuery query, String languageCode) {
        Set<UUID> creatorIds = groupPage.getContent().stream()
                .map(ModifierGroup::getCreatedBy)
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

        Map<UUID, Long> menuCountMap = loadMenuCountMap(groupPage);
        List<ModifierGroupBasicDetailsResponse> groupDtos = mapToBasicDetails(groupPage.getContent(), creatorIdToNameMap, menuCountMap, languageCode);

        int totalFilteredRecords = (int) groupPage.getTotalElements();
        int totalPages = query.usePagination() ? groupPage.getTotalPages() : 1;
        int currentPage = query.usePagination() ? query.page() : 1;
        int currentPageSize = groupDtos.size();

        PaginationMetaData metaData = PaginationMetaData.builder()
                .page(currentPage)
                .size(query.usePagination() ? query.size() : currentPageSize)
                .totalPages(totalPages)
                .totalRecords(totalFilteredRecords)
                .build();

        return ModifierGroupListResponse.builder()
                .modifierGroups(groupDtos)
                .count((long) groupDtos.size())
                .total((long) totalFilteredRecords)
                .metaData(metaData)
                .build();
    }

    private Map<UUID, Long> loadMenuCountMap(Page<ModifierGroup> groupPage) {
        List<UUID> modifierGroupIds = groupPage.getContent().stream()
                .map(ModifierGroup::getId)
                .collect(Collectors.toList());
        if (modifierGroupIds.isEmpty()) {
            return Collections.emptyMap();
        }
        List<Object[]> menuCountsRaw = categoryItemMappingRepository.countMenusByModifierGroupIdsBatch(modifierGroupIds);
        return menuCountsRaw.stream()
                .collect(Collectors.toMap(
                        row -> (UUID) row[0],
                        row -> ((Number) row[1]).longValue()
                ));
    }

    private List<ModifierGroupBasicDetailsResponse> mapToBasicDetails(List<ModifierGroup> groups,
                                                                     Map<UUID, String> creatorIdToNameMap,
                                                                     Map<UUID, Long> menuCountMap,
                                                                     String languageCode) {
        return groups.stream()
                .map(modifierGroup -> {
                    String resolvedName = resolveModifierGroupName(modifierGroup, languageCode);
                    return ModifierGroupBasicDetailsResponse.builder()
                            .id(modifierGroup.getId())
                            .name(resolvedName)
                            .modifierType(modifierGroup.getModifierType())
                            .allowMultiSelect(modifierGroup.getAllowMultiSelect())
                            .status(modifierGroup.getStatus())
                            .createdAt(modifierGroup.getCreatedAt() != null ? modifierGroup.getCreatedAt().toLocalDateTime() : null)
                            .createdBy(
                                    Optional.ofNullable(modifierGroup.getCreatedBy())
                                            .map(User::getId)
                                            .map(creatorIdToNameMap::get)
                                            .orElse(null)
                            )
                            .menuCount(menuCountMap.getOrDefault(modifierGroup.getId(), 0L))
                            .build();
                })
                .collect(Collectors.toList());
    }

    private String resolveModifierGroupName(ModifierGroup modifierGroup, String languageCode) {
        if (modifierGroup.getTranslations() == null || modifierGroup.getTranslations().isEmpty()) {
            return null;
        }
        ModifierGroupTranslation translation = modifierGroup.getTranslations().stream()
                .filter(t -> t.getLanguageCode().equalsIgnoreCase(languageCode))
                .findFirst()
                .orElse(null);
        if (translation != null) {
            return translation.getName();
        }
        Optional<ModifierGroupTranslation> cfg =
                TranslationUtils.pickPreferredOrFromList(
                        modifierGroup.getTranslations(),
                        languageCode,
                        localizationProperties.getLanguages(),
                        ModifierGroupTranslation::getLanguageCode
                );
        return cfg.map(ModifierGroupTranslation::getName).orElse(null);
    }
    
    /**
     * Fetches full details for a modifier group, including translations and audit metadata.
     * <p>
     * Loads the modifier group (excluding soft-deleted records), resolves creator/updater display names in batch, and
     * maps translations into response DTOs.
     * </p>
     *
     * @param modifierGroupId modifier group identifier
     * @return response containing modifier group details
     * @throws ResponseStatusException when the modifier group does not exist (or is deleted)
     */
    @Transactional(readOnly = true)
    public ResponseDto<ModifierGroupDto<ModifierGroupDetailsResponse>> getModifierGroupDetails(UUID modifierGroupId) {
        Locale userLocale = LocaleContextHolder.getLocale();
    
        ModifierGroup modifierGroup = modifierGroupRepository.findByIdAndIsDeletedFalse(modifierGroupId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        messageUtil.getMessage(MSG_MODIFIER_GROUP_NOT_FOUND, userLocale)
                ));

        Set<UUID> userIds = new HashSet<>();
        if (modifierGroup.getCreatedBy() != null) {
            userIds.add(modifierGroup.getCreatedBy().getId());
        }
        if (modifierGroup.getUpdatedBy() != null) {
            userIds.add(modifierGroup.getUpdatedBy().getId());
        }

        Map<UUID, String> userIdToNameMap = userRepository.findAllById(userIds).stream()
                .collect(Collectors.toMap(
                    User::getId,
                    user -> {
                        String firstName = Optional.ofNullable(user.getFirstName()).orElse("");
                        String lastName = Optional.ofNullable(user.getLastName()).orElse("");
                        return (firstName + " " + lastName).trim();
                    }
                ));

        String createdByName = Optional.ofNullable(modifierGroup.getCreatedBy())
                .map(User::getId)
                .map(userIdToNameMap::get)
                .orElse(null);

        String updatedByName = Optional.ofNullable(modifierGroup.getUpdatedBy())
                .map(User::getId)
                .map(userIdToNameMap::get)
                .orElse(null);

        List<ModifierGroupTranslationDto> translationDtos = modifierGroup.getTranslations().stream()
                .map(t -> ModifierGroupTranslationDto.builder()
                        .languageCode(t.getLanguageCode())
                        .name(t.getName())
                        .description(t.getDescription())
                        .build())
                .collect(Collectors.toList());

        ModifierGroupDetailsResponse detailsResponse = ModifierGroupDetailsResponse.builder()
                .id(modifierGroup.getId())
                .modifierType(modifierGroup.getModifierType())
                .allowMultiSelect(modifierGroup.getAllowMultiSelect())
                .minLimit(modifierGroup.getMinLimit())
                .maxLimit(modifierGroup.getMaxLimit())
                .status(modifierGroup.getStatus())
                .createdAt(modifierGroup.getCreatedAt() != null ? modifierGroup.getCreatedAt().toLocalDateTime() : null)
                .createdBy(createdByName)
                .updatedAt(modifierGroup.getUpdatedAt() != null ? modifierGroup.getUpdatedAt().toLocalDateTime() : null)
                .updatedBy(updatedByName)
                .translations(translationDtos)
                .build();

        ModifierGroupDto<ModifierGroupDetailsResponse> dto = ModifierGroupDto.<ModifierGroupDetailsResponse>builder()
                .modifierGroup(detailsResponse)
                .build();

        return ResponseDto.<ModifierGroupDto<ModifierGroupDetailsResponse>>builder()
                .message(messageUtil.getMessage("modifier.group.details.fetch.success", userLocale))
                .data(dto)
                .build();
    }

    /**
     * Builds a specification for filtering ModifierGroups based on status, modifierType, allowMultiSelect, and isDeleted.
     */
    private Specification<ModifierGroup> buildModifierGroupSpecification(EntityStatus status, ModifierType modifierType, Boolean allowMultiSelect, Boolean isDeleted) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            
            // Handle isDeleted filter: if isDeleted=true, show deleted; otherwise show non-deleted (default)
            if (isDeleted != null && isDeleted) {
                predicates.add(cb.equal(root.get(FIELD_IS_DELETED), true));
            } else {
                predicates.add(cb.equal(root.get(FIELD_IS_DELETED), false));
            }
            
            if (status != null) {
                predicates.add(cb.equal(root.get("status"), status));
            }
            
            if (modifierType != null) {
                predicates.add(cb.equal(root.get("modifierType"), modifierType));
            }
            
            if (allowMultiSelect != null) {
                predicates.add(cb.equal(root.get("allowMultiSelect"), allowMultiSelect));
            }
            
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    /**
     * Gets the locale-aware name for a ModifierGroup.
     * First tries to find a translation matching the language code,
     * then falls back to the first available translation.
     */
    private String getLocaleAwareName(ModifierGroup group, String languageCode) {
        if (group == null || group.getTranslations() == null || group.getTranslations().isEmpty()) {
            return "";
        }
        
        // Try to find translation matching the language code
        Optional<ModifierGroupTranslation> matchingTranslation = group.getTranslations().stream()
                .filter(t -> t.getLanguageCode() != null && t.getLanguageCode().equalsIgnoreCase(languageCode))
                .findFirst();
        
        if (matchingTranslation.isPresent() && matchingTranslation.get().getName() != null) {
            return matchingTranslation.get().getName();
        }
        
        // Fallback to first available translation
        Optional<ModifierGroupTranslation> firstTranslation = group.getTranslations().stream()
                .filter(t -> t.getName() != null && !t.getName().trim().isEmpty())
                .findFirst();
        
        return firstTranslation.map(ModifierGroupTranslation::getName).orElse("");
    }

    /**
     * Sorts a list of ModifierGroups according to the given sort field and direction,
     * using locale-aware name sorting when needed.
     */
    private void sortModifierGroups(List<ModifierGroup> groups, String sortBy, String sortDirection, String languageCode) {
        if (groups == null || groups.isEmpty() || sortBy == null) {
            return;
        }

        Sort.Direction direction = Sort.Direction.fromString(sortDirection);

        if ("name".equalsIgnoreCase(sortBy)) {
            // Custom locale-aware sorting for ModifierGroup names
            groups.sort((g1, g2) -> {
                String name1 = getLocaleAwareName(g1, languageCode);
                String name2 = getLocaleAwareName(g2, languageCode);

                // Use locale-aware collator for proper sorting
                java.text.Collator collator = switch (languageCode) {
                    case "th" -> java.text.Collator.getInstance(new Locale("th", "TH"));
                    case "ja" -> java.text.Collator.getInstance(new Locale("ja", "JP"));
                    default -> java.text.Collator.getInstance(Locale.US);
                };
                collator.setStrength(java.text.Collator.PRIMARY);

                int comparison = collator.compare(
                        name1 != null ? name1 : "",
                        name2 != null ? name2 : ""
                );
                return direction == Sort.Direction.DESC ? -comparison : comparison;
            });
        } else if ("createdAt".equalsIgnoreCase(sortBy)) {
            // Sort by createdAt
            groups.sort((g1, g2) -> {
                java.time.LocalDateTime date1 = g1.getCreatedAt() != null ? g1.getCreatedAt().toLocalDateTime() : null;
                java.time.LocalDateTime date2 = g2.getCreatedAt() != null ? g2.getCreatedAt().toLocalDateTime() : null;

                if (date1 == null && date2 == null) return 0;
                if (date1 == null) return 1;
                if (date2 == null) return -1;

                int comparison = date1.compareTo(date2);
                return direction == Sort.Direction.DESC ? -comparison : comparison;
            });
        } else {
            // Fallback for other fields - use LocaleSortUtil
            LocaleSortUtil.sortName(groups, sortBy, direction);
        }
    }

    /**
     * Soft-deletes a modifier group. Only HQ_ADMIN can perform this action.
     *
     * @param modifierGroupId the UUID of the modifier group to delete
     * @param updaterId       the ID of the user performing the deletion
     * @param userRole        the role of the user performing the deletion (must be HQ_ADMIN)
     * @return {@link ResponseDto} indicating success of the deletion
     * @throws ResponseStatusException if user is not HQ_ADMIN, modifier group not found, or deletion fails
     */
    @Transactional
    public ResponseDto<Void> deleteModifierGroup(UUID modifierGroupId, String updaterId, String userRole) {
        Locale userLocale = LocaleContextHolder.getLocale();

        if (!"HQ_ADMIN".equals(userRole)) {
                throw new ResponseStatusException(
                        HttpStatus.FORBIDDEN,
                        messageUtil.getMessage("modifier.group.delete.unauthorized", userLocale)
                );
        }

        ModifierGroup modifierGroup = modifierGroupRepository.findByIdAndIsDeletedFalse(modifierGroupId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        messageUtil.getMessage(MSG_MODIFIER_GROUP_NOT_FOUND, userLocale)
                ));

        // Check if modifier group is assigned to any item
        List<ItemModifierGroup> assignedItems = 
                itemModifierGroupRepository.findByModifierGroupIdAndIsDeletedFalse(modifierGroupId);
        
        if (!assignedItems.isEmpty() && isModifierGroupUsedInLiveMenus(modifierGroupId)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    messageUtil.getMessage("modifier.group.delete.error.assigned.to.live.menu", userLocale)
            );
        }

        User updater = userRepository.findById(UUID.fromString(updaterId))
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        messageUtil.getMessage(MSG_MODIFIER_GROUP_USER_NOT_FOUND, userLocale)
                ));

        modifierGroup.setIsDeleted(true);
        modifierGroup.setUpdatedAt(OffsetDateTime.now(ZoneOffset.UTC));
        modifierGroup.setUpdatedBy(updater);

        modifierGroupRepository.save(modifierGroup);

        // Create audit trail for modifier group deletion
        try {
            Restaurant restaurant = null;
            if (updater.getRestaurantId() != null) {
                restaurant = restaurantRepository.findById(updater.getRestaurantId()).orElse(null);
            }
            List<ModifierGroupTranslation> modifierTranslations = modifierGroup.getTranslations();
            String modifierName = modifierTranslations.isEmpty() ? DEFAULT_NO_TRANSLATIONS : modifierTranslations.get(0).getName();
            auditTrailService.createAuditTrail(
                    updater,
                    ActionType.MODIFIER_DELETE,
                    restaurant,
                    null, // status - will default to NA for non-request actions
                    null, // ipAddress - not available in this context
                    null, // userAgent - not available in this context
                    modifierGroup.getId(),
                    ACTION_TYPE_MODIFIER_GROUP,
                    "Modifier Group deleted: " + modifierName
            );
        } catch (Exception e) {
            // Don't break modifier group deletion flow if audit trail fails
        }

        return ResponseDto.<Void>builder()
            .message(messageUtil.getMessage("modifier.group.delete.success", userLocale))
            .build();
    }

    /**
     * Restores multiple soft-deleted modifier groups by setting isDeleted to false.
     * Only HQ_ADMIN can perform this action.
     *
     * @param ids      list of modifier group UUIDs to restore
     * @param updaterId the ID of the user performing the restoration
     * @param userRole  the role of the user performing the restoration (must be HQ_ADMIN)
     * @return {@link ResponseDto} indicating success of the restoration
     * @throws ResponseStatusException if user is not HQ_ADMIN or restoration fails
     */
    @Override
    @Transactional
    public ResponseDto<Void> restoreModifierGroups(List<UUID> ids, String updaterId, String userRole) {
        Locale userLocale = LocaleContextHolder.getLocale();
        
        // Find user for updatedBy
        User updater = userRepository.findById(UUID.fromString(updaterId))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        messageUtil.getMessage(MSG_MODIFIER_GROUP_USER_NOT_FOUND, userLocale)));
        
        // Find all modifier groups by IDs
        List<ModifierGroup> modifierGroups = modifierGroupRepository.findAllById(ids);
        
        if (modifierGroups.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                    messageUtil.getMessage(MSG_MODIFIER_GROUP_NOT_FOUND, userLocale));
        }
        
        // Filter only deleted modifier groups and restore them
        List<ModifierGroup> deletedModifierGroups = modifierGroups.stream()
                .filter(mg -> Boolean.TRUE.equals(mg.getIsDeleted()))
                .collect(Collectors.toList());
        
        if (deletedModifierGroups.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    messageUtil.getMessage("modifier.group.restore.error.not.deleted", userLocale));
        }
        
        // Restore all deleted modifier groups
        for (ModifierGroup modifierGroup : deletedModifierGroups) {
            modifierGroup.setIsDeleted(false);
            modifierGroup.setUpdatedAt(OffsetDateTime.now(ZoneOffset.UTC));
            modifierGroup.setUpdatedBy(updater);
        }
        
        modifierGroupRepository.saveAll(deletedModifierGroups);
        
        return ResponseDto.<Void>builder()
            .message(messageUtil.getMessage("modifier.group.restore.success", userLocale))
            .build();
    }

    /**
     * Helper method to check if a modifier group is assigned to items that are in live menus
     * @param modifierGroupId The modifier group ID to check
     * @return true if the modifier group is used in any live menu, false otherwise
     */
    private boolean isModifierGroupUsedInLiveMenus(UUID modifierGroupId) {
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
    
}

