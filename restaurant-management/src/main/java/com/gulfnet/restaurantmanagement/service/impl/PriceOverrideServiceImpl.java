package com.gulfnet.restaurantmanagement.service.impl;

import com.gulfnet.restaurantmanagement.service.PriceOverrideService;
import com.gulfnet.restaurantmanagement.service.AuditTrailService;
import com.gulfnet.restaurantmanagement.util.MessageUtil;
import com.gulfnet.shared_library.enums.ActionType;
import com.gulfnet.shared_library.entity.PriceOverride;
import com.gulfnet.shared_library.entity.PriceOverrideMapping;
import com.gulfnet.shared_library.entity.Restaurant;
import com.gulfnet.shared_library.entity.Menu;
import com.gulfnet.shared_library.entity.Category;
import com.gulfnet.shared_library.entity.MenuCategoryMapping;
import com.gulfnet.shared_library.entity.RestaurantMenuMapping;
import com.gulfnet.shared_library.entity.PriceOverrideTranslation;
import com.gulfnet.shared_library.entity.User;
import com.gulfnet.shared_library.entity.Item;
import com.gulfnet.shared_library.entity.ItemTranslation;
import com.gulfnet.shared_library.enums.PriceOverrideStatus;
import com.gulfnet.shared_library.model.request.PriceOverrideRequest;
import com.gulfnet.shared_library.model.request.PriceOverrideTranslationRequest;
import com.gulfnet.shared_library.model.request.SchedulePriceOverrideDeactivationRequest;
import com.gulfnet.shared_library.model.request.UpdatePriceOverrideScheduleRequest;
import com.gulfnet.shared_library.enums.OverrideLevel;
import com.gulfnet.shared_library.model.response.dto.PriceOverrideResponse;
import com.gulfnet.shared_library.model.response.dto.PriceOverrideListResponse;
import com.gulfnet.shared_library.model.response.dto.PaginationMetaData;
import com.gulfnet.shared_library.model.response.dto.ResponseDto;
import com.gulfnet.shared_library.model.response.dto.PriceOverrideImpactedItemResponse;
import com.gulfnet.shared_library.model.response.dto.PriceOverrideImpactedItemListResponse;
import com.gulfnet.shared_library.model.response.dto.ItemTranslationDto;
import org.springframework.data.domain.Sort;
import com.gulfnet.shared_library.repository.PriceOverrideRepository;
import com.gulfnet.shared_library.repository.PriceOverrideMappingRepository;
import com.gulfnet.shared_library.repository.RestaurantRepository;
import com.gulfnet.shared_library.repository.MenuRepository;
import com.gulfnet.shared_library.repository.CategoryRepository;
import com.gulfnet.shared_library.repository.MenuCategoryMappingRepository;
import com.gulfnet.shared_library.repository.RestaurantMenuMappingRepository;
import com.gulfnet.shared_library.repository.PriceOverrideTranslationRepository;
import com.gulfnet.shared_library.repository.UserRepository;
import com.gulfnet.shared_library.repository.MenuTranslationRepository;
import com.gulfnet.shared_library.repository.CategoryTranslationRepository;
import com.gulfnet.shared_library.repository.ItemRepository;
import com.gulfnet.shared_library.repository.ItemTranslationRepository;
import com.gulfnet.shared_library.entity.MenuTranslation;
import com.gulfnet.shared_library.entity.CategoryTranslation;
import lombok.extern.slf4j.Slf4j;
import org.quartz.*;
import org.quartz.impl.matchers.GroupMatcher;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import com.gulfnet.restaurantmanagement.job.PriceOverrideActivationJob;
import com.gulfnet.restaurantmanagement.job.PriceOverrideDeactivationJob;
import com.gulfnet.shared_library.util.LocaleSortUtil;
import org.springframework.context.i18n.LocaleContextHolder;

import java.time.OffsetDateTime;
import java.time.OffsetTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.function.Function;
import java.math.BigDecimal;
import java.math.RoundingMode;
import com.gulfnet.shared_library.util.CurrencyFormatter;
import com.gulfnet.restaurantmanagement.config.RestaurantChainConfigProperties;
import com.gulfnet.shared_library.enums.OverrideType;
import com.gulfnet.shared_library.util.TranslationUtils;
import com.gulfnet.restaurantmanagement.config.LocalizationProperties;

@Slf4j
@Service
public class PriceOverrideServiceImpl implements PriceOverrideService {

    // Entity type constants
    private static final String ENTITY_TYPE_PRICE_OVERRIDE = "PRICE_OVERRIDE";
    
    // Message key constants
    private static final String MSG_USER_NOT_FOUND = "user.not.found";
    private static final String MSG_RESTAURANT_NOT_FOUND = "restaurant.not.found";
    private static final String MSG_PRICE_OVERRIDE_ERROR_NOT_FOUND = "price.override.error.not.found";
    private static final String MSG_PRICE_OVERRIDE_ERROR_DELETED = "price.override.error.deleted";
    private static final String MSG_PRICE_OVERRIDE_ERROR_CATEGORY_INVALID = "price.override.error.category.invalid";
    private static final String MSG_PRICE_OVERRIDE_LIST_SUCCESS = "price.override.list.success";
    private static final String MSG_PRICE_OVERRIDE_SCHEDULE_NOT_UTC = "price.override.schedule.not.utc";
    private static final String MSG_PRICE_OVERRIDE_SCHEDULE_INVALID_DATE_RANGE = "price.override.schedule.invalid.date.range";
    
    // Field name constants
    private static final String FIELD_PRICE_OVERRIDE_ID = "priceOverrideId";
    
    // Job group constants
    private static final String JOB_GROUP_PRICE_OVERRIDE_JOBS = "price-override-jobs";
    
    // Default value constants
    private static final String DEFAULT_NO_TRANSLATIONS = "No translations";

    @Autowired
    private PriceOverrideRepository priceOverrideRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PriceOverrideTranslationRepository priceOverrideTranslationRepository;

    @Autowired
    private MessageUtil messageUtil;

    @Autowired
    private RestaurantChainConfigProperties restaurantChainConfigProperties;

    @Autowired
    private PriceOverrideMappingRepository priceOverrideMappingRepository;

    @Autowired
    private RestaurantRepository restaurantRepository;

    @Autowired
    private MenuRepository menuRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private MenuTranslationRepository menuTranslationRepository;

    @Autowired
    private CategoryTranslationRepository categoryTranslationRepository;

    @Autowired
    private MenuCategoryMappingRepository menuCategoryMappingRepository;

    @Autowired
    private RestaurantMenuMappingRepository restaurantMenuMappingRepository;

    @Autowired
    private ItemRepository itemRepository;

    @Autowired
    private ItemTranslationRepository itemTranslationRepository;

    @Autowired
    private com.gulfnet.shared_library.repository.CategoryItemMappingRepository categoryItemMappingRepository;

    @Autowired
    private LocalizationProperties localizationProperties;

    @Autowired
    private AuditTrailService auditTrailService;

    @Autowired
    private Scheduler scheduler;

    /**
     * Creates a new price override with the specified configuration.
     * Validates the mapping scope and level, creates price override mappings for restaurant/menu/categories,
     * saves translations, and creates an audit trail.
     *
     * @param request the price override request containing override level, type, value, restaurant, menu, and category mappings
     * @param userId the ID of the user creating the price override
     * @param locale the locale for localized error messages
     * @return a response containing the created price override with all translations
     * @throws ResponseStatusException if validation fails, user not found, or creation fails
     */
    @Override
    @Transactional
    public ResponseDto<PriceOverrideResponse> createPriceOverride(PriceOverrideRequest request, String userId,
            String locale) {
        Locale userLocale = Locale.forLanguageTag(locale);

        try {
            User user = userRepository.findById(UUID.fromString(userId))
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                            messageUtil.getMessage(MSG_USER_NOT_FOUND, userLocale, userId)));

            PriceOverride priceOverride = PriceOverride.builder()
                    .overrideLevel(request.getOverrideLevel())
                    .overrideType(request.getOverrideType())
                    .overrideValue(request.getOverrideValue())
                    .status(PriceOverrideStatus.UNSCHEDULED)
                    .isDeleted(false)
                    .createdBy(user)
                    .build();

            priceOverride = priceOverrideRepository.save(priceOverride);
            log.info("Created price override with ID: {}", priceOverride.getId());

            // Validate mapping scope and level
            boolean hasMenu = request.getMenuId() != null;
            boolean hasCategoryMappings = hasCategorySelection(request);
            validateMappingScopeAndLevel(request, hasMenu, hasCategoryMappings, userLocale);

            // Load required refs and create mappings
            Restaurant restaurant = loadRestaurant(request.getRestaurantId(), userLocale);
            Menu menu = hasMenu ? loadMenu(request.getMenuId(), userLocale) : null;
            createPriceOverrideMappings(priceOverride, restaurant, menu, hasMenu, request.getCategoryIds(),
                    request.getSubcategoryIds(), userLocale);

            List<PriceOverrideTranslation> translations = createTranslations(priceOverride, request, userLocale);
            priceOverrideTranslationRepository.saveAll(translations);

            List<PriceOverrideTranslation> savedTranslations = priceOverrideTranslationRepository
                    .findByPriceOverrideId(priceOverride.getId());

            // Return ALL translations in create response
            PriceOverrideResponse response = buildPriceOverrideResponseWithAllTranslations(priceOverride, savedTranslations);

            // Create audit trail for price override creation
            createAuditTrailForPriceOverrideCreation(user, priceOverride, savedTranslations);

            return ResponseDto.<PriceOverrideResponse>builder()
                    .message(messageUtil.getMessage("price.override.create.success", userLocale))
                    .data(response)
                    .build();

        } catch (ResponseStatusException e) {
            throw e;
        } catch (Exception e) {
            log.error("Error creating price override: {}", e.getMessage(), e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "Failed to create price override: " + e.getMessage());
        }
    }

    /**
     * Updates an existing price override with new configuration.
     * Validates the mapping scope and level, deletes existing mappings and creates new ones,
     * updates translations, and creates an audit trail. Restricts updates if the price override is LIVE.
     *
     * @param id the ID of the price override to update
     * @param request the price override request containing updated configuration
     * @param userId the ID of the user updating the price override
     * @param locale the locale for localized error messages
     * @return a response containing the updated price override with all translations
     * @throws ResponseStatusException if validation fails, price override not found, is deleted, is LIVE, or update fails
     */
    @Override
    @Transactional
    public ResponseDto<PriceOverrideResponse> updatePriceOverride(UUID id, PriceOverrideRequest request, String userId,
            String locale) {
        Locale userLocale = Locale.forLanguageTag(locale);

        try {
            User user = userRepository.findById(UUID.fromString(userId))
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                            messageUtil.getMessage(MSG_USER_NOT_FOUND, userLocale, userId)));

            PriceOverride priceOverride = priceOverrideRepository.findById(id)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                            messageUtil.getMessage(MSG_PRICE_OVERRIDE_ERROR_NOT_FOUND, userLocale, id)));

            if (Boolean.TRUE.equals(priceOverride.getIsDeleted())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        messageUtil.getMessage(MSG_PRICE_OVERRIDE_ERROR_DELETED, userLocale));
            }

            // Restrict update if status is LIVE, unless explicitly changing status
            if (PriceOverrideStatus.LIVE.equals(priceOverride.getStatus()) && 
                (request.getStatus() == null || PriceOverrideStatus.LIVE.equals(request.getStatus()))) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        messageUtil.getMessage("price.override.error.active", userLocale));
            }

            // Validate mapping scope and level
            boolean hasMenu = request.getMenuId() != null;
            boolean hasCategoryMappings = hasCategorySelection(request);
            validateMappingScopeAndLevel(request, hasMenu, hasCategoryMappings, userLocale);

            // Update base fields
            priceOverride.setOverrideLevel(request.getOverrideLevel());
            priceOverride.setOverrideType(request.getOverrideType());
            priceOverride.setOverrideValue(request.getOverrideValue());
            
            // Update status if explicitly provided (allows deactivating ACTIVE price overrides)
            if (request.getStatus() != null) {
                priceOverride.setStatus(request.getStatus());
                log.info("Price override {} status updated to {}", priceOverride.getId(), request.getStatus());
            }
            
            priceOverride.setUpdatedBy(user);
            priceOverride = priceOverrideRepository.save(priceOverride);
            log.info("Updated price override with ID: {}", priceOverride.getId());

            // Load required refs and update mappings
            Restaurant restaurant = loadRestaurant(request.getRestaurantId(), userLocale);
            
            // Delete existing mappings
            List<PriceOverrideMapping> existingMappings = priceOverrideMappingRepository.findByPriceOverrideId(id);
            priceOverrideMappingRepository.deleteAll(existingMappings);

            Menu menu = hasMenu ? loadMenu(request.getMenuId(), userLocale) : null;
            createPriceOverrideMappings(priceOverride, restaurant, menu, hasMenu, request.getCategoryIds(),
                    request.getSubcategoryIds(), userLocale);

            // Update translations
            updatePriceOverrideTranslations(priceOverride, request);

            List<PriceOverrideTranslation> savedTranslations = priceOverrideTranslationRepository
                    .findByPriceOverrideId(priceOverride.getId());

            // Return ALL translations in update response
            PriceOverrideResponse response = buildPriceOverrideResponseWithAllTranslations(priceOverride, savedTranslations);

            // Create audit trail for price override update
            createAuditTrailForPriceOverrideUpdate(user, priceOverride, savedTranslations);

            return ResponseDto.<PriceOverrideResponse>builder()
                    .message(messageUtil.getMessage("price.override.update.success", userLocale))
                    .data(response)
                    .build();

        } catch (ResponseStatusException e) {
            throw e;
        } catch (Exception e) {
            log.error("Error updating price override: {}", e.getMessage(), e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "Failed to update price override: " + e.getMessage());
        }
    }

    /**
     * Retrieves a single price override by its ID, including all translations and mappings.
     *
     * @param id the ID of the price override to retrieve
     * @param locale the locale for localized error messages
     * @return a response containing the price override with all translations and mappings
     * @throws ResponseStatusException if the price override is not found or is deleted
     */
    @Override
    @Transactional(readOnly = true)
    public ResponseDto<PriceOverrideResponse> getPriceOverrideById(UUID id, String locale) {
        Locale userLocale = Locale.forLanguageTag(locale);

        try {
            PriceOverride priceOverride = priceOverrideRepository.findById(id)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                            messageUtil.getMessage(MSG_PRICE_OVERRIDE_ERROR_NOT_FOUND, userLocale, id)));

            if (Boolean.TRUE.equals(priceOverride.getIsDeleted())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        messageUtil.getMessage(MSG_PRICE_OVERRIDE_ERROR_DELETED, userLocale));
            }

            // Get all translations for the price override
            List<PriceOverrideTranslation> translations = priceOverrideTranslationRepository
                    .findByPriceOverrideId(priceOverride.getId());

            // Get all mappings with relations
            List<PriceOverrideMapping> mappings = priceOverrideMappingRepository
                    .findByPriceOverrideIdWithRelations(priceOverride.getId());

            // Build mapping responses with ALL translations
            List<PriceOverrideResponse.MappingResponse> mappingResponses = buildMappingResponses(mappings, userLocale, false);

            // For GET by ID, return ALL translations
            PriceOverrideResponse response = buildPriceOverrideResponseWithAllTranslations(priceOverride, translations);
            response.setMappings(mappingResponses);

            return ResponseDto.<PriceOverrideResponse>builder()
                    .message(messageUtil.getMessage("price.override.get.success", userLocale))
                    .data(response)
                    .build();

        } catch (ResponseStatusException e) {
            throw e;
        } catch (Exception e) {
            log.error("Error fetching price override: {}", e.getMessage(), e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "Failed to fetch price override: " + e.getMessage());
        }
    }

    /**
     * Retrieves a paginated and filterable list of price overrides for a specific restaurant.
     * Supports filtering by override level and status, searching by name, and sorting.
     *
     * @param restaurantId the ID of the restaurant
     * @param page the page number (1-based, will be converted to 0-based)
     * @param size the page size
     * @param search the search term to filter by price override name
     * @param overrideLevel optional filter by override level (MENU or CATEGORY)
     * @param status optional filter by price override status
     * @param sortBy the field to sort by (defaults to "createdAt")
     * @param direction the sort direction (defaults to DESC)
     * @param locale the locale for localized error messages and translations
     * @return a response containing a paginated list of price overrides with translations
     * @throws ResponseStatusException if the restaurant is not found
     */
    @Override
    @Transactional(readOnly = true)
    public ResponseDto<PriceOverrideListResponse> getPriceOverridesByRestaurant(
            UUID restaurantId, 
            Integer page, 
            Integer size, 
            String search, 
            OverrideLevel overrideLevel,
            PriceOverrideStatus status,
            String sortBy, 
            Sort.Direction direction, 
            String locale) {
        Locale userLocale = Locale.forLanguageTag(locale);

        // Handle missing restaurantId explicitly with a localized message
        if (restaurantId == null) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    messageUtil.getMessage(MSG_RESTAURANT_NOT_FOUND, userLocale));
        }

        try {
            // Validate restaurant exists
            restaurantRepository.findById(restaurantId)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                            messageUtil.getMessage(MSG_RESTAURANT_NOT_FOUND, userLocale, restaurantId)));

            int pageNumber = resolvePageNumber(page);
            int pageSize = resolvePageSize(size);
            String normalizedSortBy = normalizeSortBy(sortBy);
            Sort.Direction sortDirection = (direction != null) ? direction : Sort.Direction.DESC;

            List<PriceOverride> priceOverrides = priceOverrideMappingRepository
                    .findDistinctPriceOverridesByRestaurantId(restaurantId);

            if (priceOverrides.isEmpty()) {
                return buildEmptyPriceOverrideListResponse(userLocale, pageNumber, pageSize);
            }

            List<UUID> priceOverrideIds = priceOverrides.stream()
                    .map(PriceOverride::getId)
                    .collect(Collectors.toList());
            Map<UUID, List<PriceOverrideTranslation>> translationsMap = loadPriceOverrideTranslations(priceOverrideIds);
            Map<UUID, List<PriceOverrideMapping>> mappingsMap = loadPriceOverrideMappings(priceOverrideIds);
            List<PriceOverrideResponse> allResponses = buildPriceOverrideResponses(
                    priceOverrides, translationsMap, mappingsMap, userLocale);
            allResponses = filterPriceOverrideResponses(allResponses, overrideLevel, status, search);
            sortPriceOverrideResponses(allResponses, normalizedSortBy, sortDirection);
            return buildPriceOverrideListResponse(allResponses, userLocale, pageNumber, pageSize);

        } catch (ResponseStatusException e) {
            throw e;
        } catch (Exception e) {
            log.error("Error fetching price overrides by restaurant: {}", e.getMessage(), e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "Failed to fetch price overrides by restaurant: " + e.getMessage());
        }
    }

    private int resolvePageNumber(Integer page) {
        return Math.max((page != null ? page : 1) - 1, 0);
    }

    private int resolvePageSize(Integer size) {
        return size != null && size > 0 ? size : Integer.MAX_VALUE;
    }

    private String normalizeSortBy(String sortBy) {
        return (sortBy == null || sortBy.isBlank()) ? "createdAt" : sortBy;
    }

    /**
     * Builds a successful list response with an empty page, zero counts, and pagination metadata for the requested slice.
     */
    private ResponseDto<PriceOverrideListResponse> buildEmptyPriceOverrideListResponse(
            Locale userLocale, int pageNumber, int pageSize) {
        PaginationMetaData metaData = PaginationMetaData.builder()
                .page(pageNumber + 1)
                .size(pageSize)
                .totalPages(0)
                .totalRecords(0L)
                .build();

        PriceOverrideListResponse listResponse = PriceOverrideListResponse.builder()
                .priceOverrides(new ArrayList<>())
                .count(0L)
                .total(0L)
                .metaData(metaData)
                .build();

        return ResponseDto.<PriceOverrideListResponse>builder()
                .message(messageUtil.getMessage(MSG_PRICE_OVERRIDE_LIST_SUCCESS, userLocale))
                .data(listResponse)
                .build();
    }

    private Map<UUID, List<PriceOverrideTranslation>> loadPriceOverrideTranslations(List<UUID> priceOverrideIds) {
        if (priceOverrideIds.isEmpty()) {
            return new HashMap<>();
        }

        return priceOverrideTranslationRepository.findAllByPriceOverrideIdIn(priceOverrideIds).stream()
                .collect(Collectors.groupingBy(t -> t.getPriceOverride().getId()));
    }

    private Map<UUID, List<PriceOverrideMapping>> loadPriceOverrideMappings(List<UUID> priceOverrideIds) {
        Map<UUID, List<PriceOverrideMapping>> mappingsMap = new HashMap<>();
        for (UUID priceOverrideId : priceOverrideIds) {
            mappingsMap.put(priceOverrideId,
                    priceOverrideMappingRepository.findByPriceOverrideIdWithRelations(priceOverrideId));
        }
        return mappingsMap;
    }

    private List<PriceOverrideResponse> buildPriceOverrideResponses(List<PriceOverride> priceOverrides,
            Map<UUID, List<PriceOverrideTranslation>> translationsMap,
            Map<UUID, List<PriceOverrideMapping>> mappingsMap, Locale userLocale) {
        return priceOverrides.stream()
                .map(priceOverride -> buildPriceOverrideResponse(
                        priceOverride,
                        translationsMap.getOrDefault(priceOverride.getId(), new ArrayList<>()),
                        mappingsMap.getOrDefault(priceOverride.getId(), new ArrayList<>()),
                        userLocale))
                .collect(Collectors.toList());
    }

    private PriceOverrideResponse buildPriceOverrideResponse(PriceOverride priceOverride,
            List<PriceOverrideTranslation> translations, List<PriceOverrideMapping> mappings, Locale userLocale) {
        List<PriceOverrideResponse.TranslationResponse> translationResponses = buildTranslationResponses(
                translations, userLocale);
        List<PriceOverrideResponse.MappingResponse> mappingResponses = buildMappingResponses(mappings, userLocale, true);

        PriceOverrideResponse response = buildPriceOverrideResponse(priceOverride, translations, userLocale);
        response.setTranslations(translationResponses);
        response.setMappings(mappingResponses);
        return response;
    }

    /**
     * Filters in-memory list responses by override level, lifecycle status, and case-insensitive translation name search.
     */
    private List<PriceOverrideResponse> filterPriceOverrideResponses(List<PriceOverrideResponse> allResponses,
            OverrideLevel overrideLevel, PriceOverrideStatus status, String search) {
        List<PriceOverrideResponse> filteredResponses = allResponses;
        if (overrideLevel != null) {
            filteredResponses = filteredResponses.stream()
                    .filter(response -> overrideLevel.equals(response.getOverrideLevel()))
                    .collect(Collectors.toList());
        }
        if (status != null) {
            filteredResponses = filteredResponses.stream()
                    .filter(response -> status.equals(response.getStatus()))
                    .collect(Collectors.toList());
        }

        String searchTerm = (search != null && !search.trim().isEmpty()) ? search.trim().toLowerCase() : null;
        if (searchTerm == null) {
            return filteredResponses;
        }

        return filteredResponses.stream()
                .filter(response -> response.getTranslations() != null && response.getTranslations().stream()
                        .anyMatch(t -> t.getName() != null && t.getName().toLowerCase().contains(searchTerm)))
                .collect(Collectors.toList());
    }

    /**
     * Applies in-memory pagination to {@code allResponses} and wraps the slice with list metadata for the API contract.
     */
    private ResponseDto<PriceOverrideListResponse> buildPriceOverrideListResponse(
            List<PriceOverrideResponse> allResponses, Locale userLocale, int pageNumber, int pageSize) {
        int totalRecords = allResponses.size();
        int fromIndex = Math.min(pageNumber * pageSize, totalRecords);
        int toIndex = Math.min(fromIndex + pageSize, totalRecords);
        List<PriceOverrideResponse> pagedResponses = fromIndex < totalRecords
                ? allResponses.subList(fromIndex, toIndex)
                : new ArrayList<>();

        PaginationMetaData metaData = PaginationMetaData.builder()
                .page(pageNumber + 1)
                .size(pageSize)
                .totalPages((int) Math.ceil((double) totalRecords / pageSize))
                .totalRecords((long) totalRecords)
                .build();

        PriceOverrideListResponse listResponse = PriceOverrideListResponse.builder()
                .priceOverrides(pagedResponses)
                .count((long) pagedResponses.size())
                .total((long) totalRecords)
                .metaData(metaData)
                .build();

        return ResponseDto.<PriceOverrideListResponse>builder()
                .message(messageUtil.getMessage(MSG_PRICE_OVERRIDE_LIST_SUCCESS, userLocale))
                .data(listResponse)
                .build();
    }

    /**
     * Sorts price override responses based on sortBy field and direction
     */
    private void sortPriceOverrideResponses(List<PriceOverrideResponse> responses, String sortBy, Sort.Direction direction) {
        if (responses == null || responses.isEmpty()) {
            return;
        }

        boolean ascending = direction == Sort.Direction.ASC;

        switch (sortBy.toLowerCase()) {
            case "name":
                responses.sort((r1, r2) -> {
                    String name1 = getTranslationName(r1);
                    String name2 = getTranslationName(r2);
                    int comparison = compareStrings(name1, name2);
                    return ascending ? comparison : -comparison;
                });
                break;
            case "createdat":
            case "created_at":
                responses.sort((r1, r2) -> {
                    if (r1.getCreatedAt() == null && r2.getCreatedAt() == null) return 0;
                    if (r1.getCreatedAt() == null) return ascending ? 1 : -1;
                    if (r2.getCreatedAt() == null) return ascending ? -1 : 1;
                    int comparison = r1.getCreatedAt().compareTo(r2.getCreatedAt());
                    return ascending ? comparison : -comparison;
                });
                break;
            case "updatedat":
            case "updated_at":
                responses.sort((r1, r2) -> {
                    if (r1.getUpdatedAt() == null && r2.getUpdatedAt() == null) return 0;
                    if (r1.getUpdatedAt() == null) return ascending ? 1 : -1;
                    if (r2.getUpdatedAt() == null) return ascending ? -1 : 1;
                    int comparison = r1.getUpdatedAt().compareTo(r2.getUpdatedAt());
                    return ascending ? comparison : -comparison;
                });
                break;
            case "status":
                responses.sort((r1, r2) -> {
                    if (r1.getStatus() == null && r2.getStatus() == null) return 0;
                    if (r1.getStatus() == null) return ascending ? 1 : -1;
                    if (r2.getStatus() == null) return ascending ? -1 : 1;
                    int comparison = r1.getStatus().name().compareTo(r2.getStatus().name());
                    return ascending ? comparison : -comparison;
                });
                break;
            case "overridevalue":
            case "override_value":
                responses.sort((r1, r2) -> {
                    if (r1.getOverrideValue() == null && r2.getOverrideValue() == null) return 0;
                    if (r1.getOverrideValue() == null) return ascending ? 1 : -1;
                    if (r2.getOverrideValue() == null) return ascending ? -1 : 1;
                    int comparison = r1.getOverrideValue().compareTo(r2.getOverrideValue());
                    return ascending ? comparison : -comparison;
                });
                break;
            default:
                // Default to createdAt DESC
                responses.sort((r1, r2) -> {
                    if (r1.getCreatedAt() == null && r2.getCreatedAt() == null) return 0;
                    if (r1.getCreatedAt() == null) return 1;
                    if (r2.getCreatedAt() == null) return -1;
                    return r2.getCreatedAt().compareTo(r1.getCreatedAt());
                });
                break;
        }
    }

    /**
     * Helper method to get translation name from response
     */
    private String getTranslationName(PriceOverrideResponse response) {
        if (response.getTranslations() != null && !response.getTranslations().isEmpty()) {
            PriceOverrideResponse.TranslationResponse translation = response.getTranslations().get(0);
            return translation.getName() != null ? translation.getName() : "";
        }
        return "";
    }

    /**
     * Helper method to compare strings (null-safe)
     */
    private int compareStrings(String s1, String s2) {
        if (s1 == null && s2 == null) return 0;
        if (s1 == null) return 1;
        if (s2 == null) return -1;
        return s1.compareToIgnoreCase(s2);
    }

    /**
     * Builds translation response with locale fallback logic - returns only requested locale translation
     */
    private List<PriceOverrideResponse.TranslationResponse> buildTranslationResponses(
            List<PriceOverrideTranslation> translations, Locale userLocale) {
        if (translations == null || translations.isEmpty()) {
            return new ArrayList<>();
        }

        String localeCode = userLocale.getLanguage();
        List<PriceOverrideResponse.TranslationResponse> responses = new ArrayList<>();

        // Try exact match first
        PriceOverrideTranslation exactMatch = translations.stream()
                .filter(t -> t.getLanguageCode() != null && t.getLanguageCode().equals(localeCode))
                .findFirst()
                .orElse(null);

        if (exactMatch != null) {
            // Return only the requested locale translation
            responses.add(PriceOverrideResponse.TranslationResponse.builder()
                    .languageCode(exactMatch.getLanguageCode())
                    .name(exactMatch.getName())
                    .reason(exactMatch.getReason())
                    .build());
        } else {
            // Fallback: use first available translation if exact match not found
            if (!translations.isEmpty()) {
                PriceOverrideTranslation first = translations.get(0);
                responses.add(PriceOverrideResponse.TranslationResponse.builder()
                        .languageCode(first.getLanguageCode())
                        .name(first.getName())
                        .reason(first.getReason())
                        .build());
            }
        }

        return responses;
    }

    /**
     * Validates mapping scope (XOR) and override level requirements
     */
    private void validateMappingScopeAndLevel(PriceOverrideRequest request, boolean hasMenu, boolean hasCategories, Locale userLocale) {
        if ((hasMenu && hasCategories) || (!hasMenu && !hasCategories)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    messageUtil.getMessage("price.override.error.scope.xor", userLocale));
        }

        if (request.getOverrideLevel() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    messageUtil.getMessage("price.override.error.level.required", userLocale));
        }

        switch (request.getOverrideLevel()) {
            case MENU -> {
                if (!hasMenu) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                            messageUtil.getMessage("price.override.error.menu.required", userLocale));
                }
            }
            case CATEGORY -> {
                if (!hasCategories) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                            messageUtil.getMessage("price.override.error.category.required", userLocale));
                }
            }
            default -> {
                // No additional validation for other levels
            }
        }
    }

    private boolean hasCategorySelection(PriceOverrideRequest request) {
        return (request.getCategoryIds() != null && !request.getCategoryIds().isEmpty())
                || (request.getSubcategoryIds() != null && !request.getSubcategoryIds().isEmpty());
    }

    /**
     * Loads restaurant by ID
     */
    private Restaurant loadRestaurant(UUID restaurantId, Locale userLocale) {
        return restaurantRepository.findById(restaurantId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        messageUtil.getMessage(MSG_RESTAURANT_NOT_FOUND, userLocale, restaurantId)));
    }

    /**
     * Loads menu by ID
     */
    private Menu loadMenu(UUID menuId, Locale userLocale) {
        return menuRepository.findById(menuId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        messageUtil.getMessage("menu.not.found", userLocale, menuId)));
    }

    /**
     * Resolves the menu ID for a restaurant by finding the first menu assigned to the restaurant.
     * Validates that the restaurant has at least one menu assignment and that all assignments are consistent.
     *
     * @param restaurant the restaurant entity
     * @param userLocale the locale for localized error messages
     * @return the menu ID for the restaurant
     * @throws ResponseStatusException if the restaurant has no menu assignments or has inconsistent menu assignments
     */
    private UUID resolveRestaurantMenuId(Restaurant restaurant, Locale userLocale) {
        List<RestaurantMenuMapping> mappings = restaurantMenuMappingRepository.findById_RestaurantId(restaurant.getId());
        if (mappings == null || mappings.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    messageUtil.getMessage(MSG_PRICE_OVERRIDE_ERROR_CATEGORY_INVALID, userLocale));
        }

        UUID menuId = mappings.stream()
                .map(RestaurantMenuMapping::getMenu)
                .filter(Objects::nonNull)
                .map(Menu::getId)
                .filter(Objects::nonNull)
                .findFirst()
                .orElse(null);

        if (menuId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    messageUtil.getMessage(MSG_PRICE_OVERRIDE_ERROR_CATEGORY_INVALID, userLocale));
        }

        boolean inconsistentMenuAssignments = mappings.stream()
                .map(RestaurantMenuMapping::getMenu)
                .filter(Objects::nonNull)
                .map(Menu::getId)
                .filter(Objects::nonNull)
                .anyMatch(id -> !menuId.equals(id));

        if (inconsistentMenuAssignments) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    messageUtil.getMessage(MSG_PRICE_OVERRIDE_ERROR_CATEGORY_INVALID, userLocale));
        }

        return menuId;
    }

    /**
     * Creates price override mappings (menu or category) based on override level
     */
    private void createPriceOverrideMappings(PriceOverride priceOverride, Restaurant restaurant, Menu menu, 
            boolean hasMenu, List<UUID> categoryIds, List<UUID> subcategoryIds, Locale userLocale) {
        if (hasMenu && menu != null) {
            createSingleMenuMapping(priceOverride, restaurant, menu);
            return;
        }

        validateCategoryIds(categoryIds, subcategoryIds, userLocale);
        UUID restaurantMenuId = resolveRestaurantMenuId(restaurant, userLocale);
        Set<UUID> finalCategorySelection = buildFinalCategorySelection(
                restaurantMenuId, categoryIds, subcategoryIds, userLocale);
        createCategoryMappings(priceOverride, restaurant, restaurantMenuId, finalCategorySelection, userLocale);
    }

    private UUID extractMenuCategoryMappingId(PriceOverrideMapping mapping) {
        if (mapping == null) {
            return null;
        }
        // Use Lombok-generated getter directly
        return mapping.getMenuCategoryMappingId();
    }

    private void setMenuCategoryMappingId(PriceOverrideMapping mapping, UUID menuCategoryMappingId) {
        if (mapping == null) {
            return;
        }
        // Use Lombok-generated setter directly
        mapping.setMenuCategoryMappingId(menuCategoryMappingId);
    }

    /**
     * Updates price override translations
     */
    private void updatePriceOverrideTranslations(PriceOverride priceOverride, PriceOverrideRequest request) {
        List<PriceOverrideTranslation> existingTranslations = priceOverrideTranslationRepository
                .findByPriceOverrideId(priceOverride.getId());
        Map<String, PriceOverrideTranslation> existingTranslationMap = new HashMap<>();
        for (PriceOverrideTranslation translation : existingTranslations) {
            existingTranslationMap.put(translation.getLanguageCode(), translation);
        }

        // Get language codes from request
        Set<String> validRequestLanguageCodes = request.getTranslations().stream()
                .filter(t -> t.getLanguageCode() != null && t.getName() != null && !t.getName().trim().isEmpty())
                .map(PriceOverrideTranslationRequest::getLanguageCode)
                .collect(Collectors.toSet());

        // Remove translations that are not in the request
        List<PriceOverrideTranslation> translationsToRemove = new ArrayList<>();
        for (PriceOverrideTranslation existingTranslation : existingTranslations) {
            if (!validRequestLanguageCodes.contains(existingTranslation.getLanguageCode())) {
                translationsToRemove.add(existingTranslation);
            }
        }
        priceOverrideTranslationRepository.deleteAll(translationsToRemove);

        // Update or create translations
        for (PriceOverrideTranslationRequest translationRequest : request.getTranslations()) {
            String name = translationRequest.getName();
            if (name != null && !name.trim().isEmpty() && translationRequest.getLanguageCode() != null) {
                PriceOverrideTranslation translation = existingTranslationMap.get(translationRequest.getLanguageCode());
                if (translation != null) {
                    translation.setName(name.trim());
                    translation.setReason(translationRequest.getReason());
                    priceOverrideTranslationRepository.save(translation);
                } else {
                    translation = PriceOverrideTranslation.builder()
                            .priceOverride(priceOverride)
                            .languageCode(translationRequest.getLanguageCode())
                            .name(name.trim())
                            .reason(translationRequest.getReason())
                            .build();
                    priceOverrideTranslationRepository.save(translation);
                }
            }
        }
    }

    /**
     * Creates translations from request for new price override
     */
    private List<PriceOverrideTranslation> createTranslations(PriceOverride priceOverride, PriceOverrideRequest request,
            Locale userLocale) {
        List<PriceOverrideTranslation> translations = new ArrayList<>();

        // Validate that at least one translation has a non-empty name
        boolean hasValidName = request.getTranslations().stream()
                .anyMatch(t -> t.getName() != null && !t.getName().trim().isEmpty());
        
        if (!hasValidName) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    messageUtil.getMessage("combo.translations.required", userLocale));
        }

        // Only add translations with non-empty names
        for (PriceOverrideTranslationRequest translationRequest : request.getTranslations()) {
            String name = translationRequest.getName();
            if (name != null && !name.trim().isEmpty() && translationRequest.getLanguageCode() != null) {
                translations.add(PriceOverrideTranslation.builder()
                        .priceOverride(priceOverride)
                        .languageCode(translationRequest.getLanguageCode())
                        .name(name.trim())
                        .reason(translationRequest.getReason())
                        .build());
            }
        }

        return translations;
    }

    /**
     * Builds mapping responses from PriceOverrideMapping entities
     * @param filterTranslations if true, returns only requested locale translation; if false, returns all translations
     */
    private List<PriceOverrideResponse.MappingResponse> buildMappingResponses(
            List<PriceOverrideMapping> mappings, Locale userLocale, boolean filterTranslations) {
        
        // Group mappings by restaurant to combine menu/categories
        Map<UUID, PriceOverrideResponse.MappingResponse> mappingMap = new HashMap<>();
        Map<UUID, Map<UUID, PriceOverrideResponse.CategoryInfo>> categoryCacheByRestaurant = new HashMap<>();
        Map<UUID, List<CategoryTranslation>> categoryTranslationsCache = new HashMap<>();
        String localeCode = userLocale.getLanguage();

        List<UUID> menuCategoryMappingIds = mappings.stream()
                .map(this::extractMenuCategoryMappingId)
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.toList());

        Map<UUID, MenuCategoryMapping> menuCategoryMappingCache = menuCategoryMappingIds.isEmpty()
                ? Collections.emptyMap()
                : menuCategoryMappingRepository.findAllById(menuCategoryMappingIds).stream()
                        .collect(Collectors.toMap(MenuCategoryMapping::getId, Function.identity()));
        
        for (PriceOverrideMapping mapping : mappings) {
            UUID restaurantId = mapping.getRestaurant().getId();
            PriceOverrideResponse.MappingResponse mappingResponse = getOrCreateMappingResponse(
                    mappingMap, categoryCacheByRestaurant, mapping, restaurantId);
            attachMenuInfo(mappingResponse, mapping, localeCode, filterTranslations);
            attachCategoryInfo(mappingResponse, mapping, restaurantId, categoryCacheByRestaurant, menuCategoryMappingCache,
                    categoryTranslationsCache, localeCode, filterTranslations);
        }
        
        return new ArrayList<>(mappingMap.values());
    }

    /**
     * Returns the per-restaurant {@link PriceOverrideResponse.MappingResponse}, creating it (and per-restaurant category cache) on first use.
     */
    private PriceOverrideResponse.MappingResponse getOrCreateMappingResponse(
            Map<UUID, PriceOverrideResponse.MappingResponse> mappingMap,
            Map<UUID, Map<UUID, PriceOverrideResponse.CategoryInfo>> categoryCacheByRestaurant,
            PriceOverrideMapping mapping, UUID restaurantId) {
        PriceOverrideResponse.MappingResponse mappingResponse = mappingMap.get(restaurantId);
        if (mappingResponse != null) {
            return mappingResponse;
        }

        Restaurant restaurant = mapping.getRestaurant();
        PriceOverrideResponse.RestaurantInfo restaurantInfo = PriceOverrideResponse.RestaurantInfo.builder()
                .id(restaurant.getId())
                .build();

        mappingResponse = PriceOverrideResponse.MappingResponse.builder()
                .restaurant(restaurantInfo)
                .menu(null)
                .categories(new ArrayList<>())
                .build();

        mappingMap.put(restaurantId, mappingResponse);
        categoryCacheByRestaurant.put(restaurantId, new HashMap<>());
        return mappingResponse;
    }

    /**
     * Populates {@link PriceOverrideResponse.MappingResponse#getMenu()} once from {@code mapping} including localized menu translations.
     */
    private void attachMenuInfo(PriceOverrideResponse.MappingResponse mappingResponse, PriceOverrideMapping mapping,
            String localeCode, boolean filterTranslations) {
        if (mapping.getMenu() == null || mappingResponse.getMenu() != null) {
            return;
        }

        Menu menu = mapping.getMenu();
        List<MenuTranslation> menuTranslations = menuTranslationRepository.findByMenuId(menu.getId());
        MenuTranslation menuTranslationForLocale = findMenuTranslationForLocale(menuTranslations, localeCode);
        List<PriceOverrideResponse.TranslationResponse> menuTranslationList = filterTranslations
                ? buildFilteredMenuTranslations(menuTranslationForLocale)
                : buildAllMenuTranslations(menuTranslations);

        PriceOverrideResponse.MenuInfo menuInfo = PriceOverrideResponse.MenuInfo.builder()
                .id(menu.getId())
                .name(menuTranslationForLocale != null ? menuTranslationForLocale.getName() : "")
                .translations(menuTranslationList)
                .build();

        mappingResponse.setMenu(menuInfo);
    }

    private MenuTranslation findMenuTranslationForLocale(List<MenuTranslation> menuTranslations, String localeCode) {
        return menuTranslations.stream()
                .filter(t -> t.getLanguageCode() != null && t.getLanguageCode().equals(localeCode))
                .findFirst()
                .orElse(menuTranslations.isEmpty() ? null : menuTranslations.get(0));
    }

    private List<PriceOverrideResponse.TranslationResponse> buildFilteredMenuTranslations(
            MenuTranslation menuTranslationForLocale) {
        List<PriceOverrideResponse.TranslationResponse> menuTranslationList = new ArrayList<>();
        if (menuTranslationForLocale != null) {
            menuTranslationList.add(PriceOverrideResponse.TranslationResponse.builder()
                    .languageCode(menuTranslationForLocale.getLanguageCode())
                    .name(menuTranslationForLocale.getName())
                    .build());
        }
        return menuTranslationList;
    }

    private List<PriceOverrideResponse.TranslationResponse> buildAllMenuTranslations(
            List<MenuTranslation> menuTranslations) {
        return menuTranslations.stream()
                .map(t -> PriceOverrideResponse.TranslationResponse.builder()
                        .languageCode(t.getLanguageCode())
                        .name(t.getName())
                        .build())
                .collect(Collectors.toList());
    }

    /**
     * Adds category (and optional parent category) DTOs for a mapping into {@code mappingResponse}, de-duplicated.
     */
    private void attachCategoryInfo(PriceOverrideResponse.MappingResponse mappingResponse, PriceOverrideMapping mapping,
            UUID restaurantId,
            Map<UUID, Map<UUID, PriceOverrideResponse.CategoryInfo>> categoryCacheByRestaurant,
            Map<UUID, MenuCategoryMapping> menuCategoryMappingCache,
            Map<UUID, List<CategoryTranslation>> categoryTranslationsCache, String localeCode,
            boolean filterTranslations) {
        MenuCategoryMapping menuCategoryMapping = getMenuCategoryMapping(mapping, menuCategoryMappingCache);
        if (menuCategoryMapping == null || menuCategoryMapping.getCategory() == null) {
            return;
        }

        Map<UUID, PriceOverrideResponse.CategoryInfo> categoryCache = categoryCacheByRestaurant.computeIfAbsent(
                restaurantId, id -> new HashMap<>());
        Category category = menuCategoryMapping.getCategory();
        Category explicitParentCategory = menuCategoryMapping.getParentCategory();

        PriceOverrideResponse.CategoryInfo categoryInfo = getOrCreateCategoryInfo(categoryCache, category,
                explicitParentCategory, filterTranslations, localeCode, categoryTranslationsCache);

        UUID parentCategoryId = explicitParentCategory != null
                ? explicitParentCategory.getId()
                : categoryInfo.getParentCategoryId();
        if (parentCategoryId == null) {
            addUniqueCategory(mappingResponse.getCategories(), categoryInfo);
            return;
        }

        PriceOverrideResponse.CategoryInfo parentInfo = getOrCreateParentCategoryInfo(categoryCache,
                explicitParentCategory, parentCategoryId, filterTranslations, localeCode, categoryTranslationsCache);
        if (parentInfo != null) {
            addUniqueCategory(mappingResponse.getCategories(), parentInfo);
            addUniqueSubCategory(parentInfo, categoryInfo);
            return;
        }

        addUniqueCategory(mappingResponse.getCategories(), categoryInfo);
    }

    private MenuCategoryMapping getMenuCategoryMapping(PriceOverrideMapping mapping,
            Map<UUID, MenuCategoryMapping> menuCategoryMappingCache) {
        UUID menuCategoryMappingId = extractMenuCategoryMappingId(mapping);
        return menuCategoryMappingId == null ? null : menuCategoryMappingCache.get(menuCategoryMappingId);
    }

    /**
     * Returns a cached {@link PriceOverrideResponse.CategoryInfo} for {@code category}, building it on first access.
     */
    private PriceOverrideResponse.CategoryInfo getOrCreateCategoryInfo(
            Map<UUID, PriceOverrideResponse.CategoryInfo> categoryCache, Category category,
            Category explicitParentCategory, boolean filterTranslations, String localeCode,
            Map<UUID, List<CategoryTranslation>> categoryTranslationsCache) {
        PriceOverrideResponse.CategoryInfo categoryInfo = categoryCache.get(category.getId());
        if (categoryInfo == null) {
            categoryInfo = buildCategoryInfo(category, explicitParentCategory, filterTranslations, localeCode,
                    categoryTranslationsCache);
            categoryCache.put(category.getId(), categoryInfo);
        } else if (explicitParentCategory != null && categoryInfo.getParentCategoryId() == null) {
            categoryInfo.setParentCategoryId(explicitParentCategory.getId());
        }
        return categoryInfo;
    }

    /**
     * Loads or builds parent {@link PriceOverrideResponse.CategoryInfo} for nested category rendering.
     */
    private PriceOverrideResponse.CategoryInfo getOrCreateParentCategoryInfo(
            Map<UUID, PriceOverrideResponse.CategoryInfo> categoryCache, Category explicitParentCategory,
            UUID parentCategoryId, boolean filterTranslations, String localeCode,
            Map<UUID, List<CategoryTranslation>> categoryTranslationsCache) {
        PriceOverrideResponse.CategoryInfo parentInfo = categoryCache.get(parentCategoryId);
        if (parentInfo != null) {
            return parentInfo;
        }

        Category parentCategory = explicitParentCategory;
        if (parentCategory == null) {
            parentCategory = categoryRepository.findById(parentCategoryId).orElse(null);
        }
        if (parentCategory == null) {
            return null;
        }

        Category parentOfParent = parentCategory.getParentCategory();
        parentInfo = buildCategoryInfo(parentCategory, parentOfParent, filterTranslations, localeCode,
                categoryTranslationsCache);
        categoryCache.put(parentCategory.getId(), parentInfo);
        return parentInfo;
    }

    /**
     * Builds a CategoryInfo DTO from a Category entity, including translations.
     * Uses a cache to avoid repeated database queries for category translations.
     *
     * @param category the category entity to build the DTO from
     * @param parentCategoryOverride optional parent category override
     * @param filterTranslations if true, returns only the translation for the specified locale; if false, returns all translations
     * @param localeCode the locale code for filtering translations
     * @param categoryTranslationsCache a cache map of category ID to list of translations
     * @return a CategoryInfo DTO, or null if the category is null
     */
    private PriceOverrideResponse.CategoryInfo buildCategoryInfo(Category category, Category parentCategoryOverride,
            boolean filterTranslations, String localeCode,
            Map<UUID, List<CategoryTranslation>> categoryTranslationsCache) {
        if (category == null) {
            return null;
        }

        UUID categoryId = category.getId();
        List<CategoryTranslation> categoryTranslations = categoryTranslationsCache.computeIfAbsent(categoryId,
                id -> categoryTranslationRepository.findByCategoryId(id));

        CategoryTranslation categoryTranslationForLocale = findCategoryTranslationForLocale(categoryTranslations, localeCode);

        List<PriceOverrideResponse.TranslationResponse> categoryTranslationList;
        if (filterTranslations) {
            categoryTranslationList = new ArrayList<>();
            if (categoryTranslationForLocale != null) {
                categoryTranslationList.add(PriceOverrideResponse.TranslationResponse.builder()
                        .languageCode(categoryTranslationForLocale.getLanguageCode())
                        .name(categoryTranslationForLocale.getName())
                        .build());
            }
        } else {
            categoryTranslationList = categoryTranslations.stream()
                    .map(t -> PriceOverrideResponse.TranslationResponse.builder()
                            .languageCode(t.getLanguageCode())
                            .name(t.getName())
                            .build())
                    .collect(Collectors.toList());
        }

        String fallbackCategoryName = "";
        if (!categoryTranslations.isEmpty() && categoryTranslations.get(0).getName() != null) {
            fallbackCategoryName = categoryTranslations.get(0).getName();
        }
        String categoryName = categoryTranslationForLocale != null && categoryTranslationForLocale.getName() != null
                ? categoryTranslationForLocale.getName()
                : fallbackCategoryName;

        UUID parentCategoryId = parentCategoryOverride != null ? parentCategoryOverride.getId() : null;
        if (parentCategoryId == null && category.getParentCategory() != null) {
            parentCategoryId = category.getParentCategory().getId();
        }

        return PriceOverrideResponse.CategoryInfo.builder()
                .id(categoryId)
                .parentCategoryId(parentCategoryId)
                .name(categoryName)
                .translations(categoryTranslationList)
                .build();
    }

    /**
     * Adds a category to a list if it doesn't already exist (based on category ID).
     *
     * @param categories the list of categories to add to
     * @param categoryInfo the category to add (ignored if null or already exists)
     */
    private void addUniqueCategory(List<PriceOverrideResponse.CategoryInfo> categories,
            PriceOverrideResponse.CategoryInfo categoryInfo) {
        if (categoryInfo == null) {
            return;
        }

        boolean exists = categories.stream()
                .anyMatch(existing -> Objects.equals(existing.getId(), categoryInfo.getId()));
        if (!exists) {
            categories.add(categoryInfo);
        }
    }

    /**
     * Adds a subcategory to a parent category's subcategories list if it doesn't already exist (based on category ID).
     * Creates the subcategories list if it doesn't exist.
     *
     * @param parent the parent category to add the subcategory to
     * @param child the subcategory to add (ignored if null or parent is null or already exists)
     */
    private void addUniqueSubCategory(PriceOverrideResponse.CategoryInfo parent,
            PriceOverrideResponse.CategoryInfo child) {
        if (parent == null || child == null) {
            return;
        }

        List<PriceOverrideResponse.CategoryInfo> subCategories = parent.getSubCategories();
        if (subCategories == null) {
            subCategories = new ArrayList<>();
            parent.setSubCategories(subCategories);
        }

        boolean exists = subCategories.stream()
                .anyMatch(existing -> Objects.equals(existing.getId(), child.getId()));
        if (!exists) {
            subCategories.add(child);
        }
    }
    /**
     * Builds price override response with ALL translations
     */
    private PriceOverrideResponse buildPriceOverrideResponseWithAllTranslations(PriceOverride priceOverride,
            List<PriceOverrideTranslation> translations) {
        // Map all translations to DTOs
        List<PriceOverrideResponse.TranslationResponse> translationResponses = translations.stream()
                .map(t -> PriceOverrideResponse.TranslationResponse.builder()
                        .languageCode(t.getLanguageCode())
                        .name(t.getName())
                        .reason(t.getReason())
                        .build())
                .collect(Collectors.toList());

        String createdBy = formatUserName(priceOverride.getCreatedBy());
        String updatedBy = formatUserName(priceOverride.getUpdatedBy());
        
        return PriceOverrideResponse.builder()
                .id(priceOverride.getId())
                .overrideLevel(priceOverride.getOverrideLevel())
                .overrideType(priceOverride.getOverrideType())
                .overrideValue(priceOverride.getOverrideValue())
                .status(priceOverride.getStatus())
                .createdAt(priceOverride.getCreatedAt() != null ? priceOverride.getCreatedAt().toLocalDateTime() : null)
                .createdBy(createdBy)
                .updatedAt(priceOverride.getUpdatedAt() != null ? priceOverride.getUpdatedAt().toLocalDateTime() : null)
                .updatedBy(updatedBy)
                .translations(translationResponses)
                .validFrom(priceOverride.getValidFrom())
                .validTo(priceOverride.getValidTo())
                .build();
    }
    
    /**
     * Helper method to format user name from User entity
     */
    private String formatUserName(User user) {
        if (user == null) {
            return null;
        }
        String firstName = user.getFirstName() != null ? user.getFirstName() : "";
        String lastName = user.getLastName() != null ? user.getLastName() : "";
        return (firstName + " " + lastName).trim();
    }
    
    /**
     * Helper method to get currency from chain config
     */
    private String getCurrency() {
        return restaurantChainConfigProperties.getChain() != null 
                ? restaurantChainConfigProperties.getChain().getCurrency() 
                : null;
    }
    
    /**
     * Builds price override response with only requested locale translation for LIST endpoint
     */
    private PriceOverrideResponse buildPriceOverrideResponse(PriceOverride priceOverride,
            List<PriceOverrideTranslation> translations, Locale userLocale) {
        // Filter to get only requested locale translation
        String localeCode = userLocale != null ? userLocale.getLanguage() : "en";
        PriceOverrideTranslation selectedTranslation = findTranslationForLocale(translations, localeCode);
        
        List<PriceOverrideResponse.TranslationResponse> translationResponses = new ArrayList<>();
        if (selectedTranslation != null) {
            translationResponses.add(PriceOverrideResponse.TranslationResponse.builder()
                    .languageCode(selectedTranslation.getLanguageCode())
                    .name(selectedTranslation.getName())
                    .reason(selectedTranslation.getReason())
                    .build());
        }

        String createdBy = formatUserName(priceOverride.getCreatedBy());
        String updatedBy = formatUserName(priceOverride.getUpdatedBy());
        
        return PriceOverrideResponse.builder()
                .id(priceOverride.getId())
                .overrideLevel(priceOverride.getOverrideLevel())
                .overrideType(priceOverride.getOverrideType())
                .overrideValue(priceOverride.getOverrideValue())
                .status(priceOverride.getStatus())
                .createdAt(priceOverride.getCreatedAt() != null ? priceOverride.getCreatedAt().toLocalDateTime() : null)
                .createdBy(createdBy)
                .updatedAt(priceOverride.getUpdatedAt() != null ? priceOverride.getUpdatedAt().toLocalDateTime() : null)
                .updatedBy(updatedBy)
                .translations(translationResponses)
                .validFrom(priceOverride.getValidFrom())
                .validTo(priceOverride.getValidTo())
                .build();
    }
    
    /**
     * Helper method to find translation for a specific locale
     */
    private PriceOverrideTranslation findTranslationForLocale(List<PriceOverrideTranslation> translations, String localeCode) {
        return translations.stream()
                .filter(t -> t.getLanguageCode() != null && t.getLanguageCode().equals(localeCode))
                .findFirst()
                .orElse(translations.isEmpty() ? null : translations.get(0));
    }
    
    /**
     * Helper method to find category translation for a specific locale
     */
    private CategoryTranslation findCategoryTranslationForLocale(List<CategoryTranslation> translations, String localeCode) {
        return translations.stream()
                .filter(t -> t.getLanguageCode() != null && t.getLanguageCode().equals(localeCode))
                .findFirst()
                .orElse(translations.isEmpty() ? null : translations.get(0));
    }

    /**
     * Ensures a restaurant+menu {@link PriceOverrideMapping} exists for {@code priceOverride} (idempotent insert).
     */
    private void createSingleMenuMapping(PriceOverride priceOverride, Restaurant restaurant, Menu menu) {
        boolean exists = priceOverrideMappingRepository
                .existsByRestaurant_IdAndMenu_IdAndPriceOverride_Id(restaurant.getId(), menu.getId(),
                        priceOverride.getId());
        if (!exists) {
            PriceOverrideMapping mapping = PriceOverrideMapping.builder()
                    .priceOverride(priceOverride)
                    .restaurant(restaurant)
                    .menu(menu)
                    .build();
            priceOverrideMappingRepository.save(mapping);
        }
    }

    private void validateCategoryIds(List<UUID> categoryIds, List<UUID> subcategoryIds, Locale userLocale) {
        if ((categoryIds != null && categoryIds.contains(null))
                || (subcategoryIds != null && subcategoryIds.contains(null))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    messageUtil.getMessage(MSG_PRICE_OVERRIDE_ERROR_CATEGORY_INVALID, userLocale));
        }
    }

    private Set<UUID> buildFinalCategorySelection(UUID restaurantMenuId, List<UUID> categoryIds,
            List<UUID> subcategoryIds, Locale userLocale) {
        Set<UUID> parentCategoryIdsToExclude = new HashSet<>();
        Set<UUID> finalCategorySelection = new LinkedHashSet<>();

        addSubcategorySelections(restaurantMenuId, subcategoryIds, userLocale, parentCategoryIdsToExclude,
                finalCategorySelection);
        addCategorySelections(categoryIds, parentCategoryIdsToExclude, finalCategorySelection);

        return finalCategorySelection;
    }

    /**
     * Validates subcategories belong to {@code restaurantMenuId}, adds them to {@code finalCategorySelection}, and
     * records parent category ids that should not be added again as standalone parents.
     */
    private void addSubcategorySelections(UUID restaurantMenuId, List<UUID> subcategoryIds, Locale userLocale,
            Set<UUID> parentCategoryIdsToExclude, Set<UUID> finalCategorySelection) {
        if (subcategoryIds == null || subcategoryIds.isEmpty()) {
            return;
        }

        List<MenuCategoryMapping> subcategoryMappings = menuCategoryMappingRepository
                .findByMenuIdAndCategory_IdIn(restaurantMenuId, new ArrayList<>(subcategoryIds));
        Map<UUID, MenuCategoryMapping> subcategoryMappingMap = subcategoryMappings.stream()
                .collect(Collectors.toMap(m -> m.getCategory().getId(), Function.identity()));

        for (UUID subcategoryId : subcategoryIds) {
            MenuCategoryMapping subcategoryMapping = subcategoryMappingMap.get(subcategoryId);
            if (subcategoryMapping == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        messageUtil.getMessage(MSG_PRICE_OVERRIDE_ERROR_CATEGORY_INVALID, userLocale));
            }

            if (subcategoryMapping.getParentCategory() != null) {
                parentCategoryIdsToExclude.add(subcategoryMapping.getParentCategory().getId());
            }
            finalCategorySelection.add(subcategoryId);
        }
    }

    /**
     * Adds top-level category ids to {@code finalCategorySelection}, skipping parents already covered via subcategory picks.
     */
    private void addCategorySelections(List<UUID> categoryIds, Set<UUID> parentCategoryIdsToExclude,
            Set<UUID> finalCategorySelection) {
        if (categoryIds == null || categoryIds.isEmpty()) {
            return;
        }

        for (UUID categoryId : categoryIds) {
            if (!parentCategoryIdsToExclude.contains(categoryId)) {
                finalCategorySelection.add(categoryId);
                log.debug("Including category {} in price override - will impact all items under this category including subcategories",
                        categoryId);
            } else {
                log.debug("Excluding parent category {} from price override because its subcategory is already selected",
                        categoryId);
            }
        }
    }

    /**
     * Persists one {@link PriceOverrideMapping} per category in {@code finalCategorySelection} for the restaurant menu.
     */
    private void createCategoryMappings(PriceOverride priceOverride, Restaurant restaurant, UUID restaurantMenuId,
            Set<UUID> finalCategorySelection, Locale userLocale) {
        if (finalCategorySelection.isEmpty()) {
            return;
        }

        for (UUID categoryId : finalCategorySelection) {
            MenuCategoryMapping menuCategoryMapping = menuCategoryMappingRepository
                    .findByMenuIdAndCategoryId(restaurantMenuId, categoryId)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST,
                            messageUtil.getMessage(MSG_PRICE_OVERRIDE_ERROR_CATEGORY_INVALID, userLocale)));
            PriceOverrideMapping mapping = PriceOverrideMapping.builder()
                    .priceOverride(priceOverride)
                    .restaurant(restaurant)
                    .menu(null)
                    .build();
            setMenuCategoryMappingId(mapping, menuCategoryMapping.getId());
            priceOverrideMappingRepository.save(mapping);
        }
    }
    
    /**
     * Updates the schedule (validFrom and validTo) for an existing price override.
     * Validates schedule fields, converts to UTC, checks for overlapping price overrides,
     * cancels existing Quartz jobs, updates the schedule, determines status based on timing,
     * schedules new Quartz jobs, and creates an audit trail if activated.
     *
     * @param id the ID of the price override to update
     * @param request the schedule update request containing validFrom and validTo
     * @param userId the ID of the user updating the schedule
     * @param locale the locale for localized error messages
     * @return a response containing the updated price override with all translations and mappings
     * @throws ResponseStatusException if validation fails, price override not found, is deleted, or update fails
     */
    @Override
    @Transactional
    public ResponseDto<PriceOverrideResponse> updatePriceOverrideSchedule(UUID id, 
            UpdatePriceOverrideScheduleRequest request, 
            String userId, String locale) {
        Locale userLocale = Locale.forLanguageTag(locale);
        
        try {
            // Find user
            User user = userRepository.findById(UUID.fromString(userId))
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                            messageUtil.getMessage(MSG_USER_NOT_FOUND, userLocale, userId)));
            
            // Find price override
            PriceOverride priceOverride = priceOverrideRepository.findById(id)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                            messageUtil.getMessage(MSG_PRICE_OVERRIDE_ERROR_NOT_FOUND, userLocale, id)));
            
            if (Boolean.TRUE.equals(priceOverride.getIsDeleted())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        messageUtil.getMessage(MSG_PRICE_OVERRIDE_ERROR_DELETED, userLocale));
            }
            
            // Validate schedule fields
            validateScheduleFields(request, userLocale);
            
            // Convert to UTC
            OffsetDateTime validFromUtc = convertToUtc(request.getValidFrom());
            OffsetDateTime validToUtc = convertToUtc(request.getValidTo());
            
            // Validate no overlapping price overrides
            validateNoOverlappingPriceOverrides(priceOverride, validFromUtc, validToUtc, userLocale);
            
            // Cancel existing jobs for this price override before updating schedule
            cancelScheduledJobsForPriceOverride(id);
            
            // Update price override with schedule
            priceOverride.setValidFrom(validFromUtc);
            priceOverride.setValidTo(validToUtc);
            // Clear startTime and endTime as they are no longer used
            priceOverride.setStartTime(null);
            priceOverride.setEndTime(null);
            
            // Determine status based on schedule timing
            OffsetDateTime nowUtc = OffsetDateTime.now(ZoneOffset.UTC);
            
            boolean wasActivatedNow = false;
            // Check if schedule has expired (validTo is in the past)
            if (validToUtc != null && validToUtc.isBefore(nowUtc)) {
                priceOverride.setStatus(PriceOverrideStatus.UNSCHEDULED);
                log.info("Price override schedule has expired - setting status to UNSCHEDULED");
            } 
            // Check if validFrom is in the past/now - activate immediately
            else if (validFromUtc.isBefore(nowUtc) || validFromUtc.isEqual(nowUtc)) {
                // Immediate activation - validFrom is in the past or now
                priceOverride.setStatus(PriceOverrideStatus.LIVE);
                wasActivatedNow = true;
                log.info("Price override validFrom is in the past/now - setting status to LIVE");
            } 
            // validFrom is in the future - schedule for later
            else {
                priceOverride.setStatus(PriceOverrideStatus.SCHEDULED);
                log.info("Price override validFrom is in the future - setting status to SCHEDULED");
            }
            
            priceOverride.setUpdatedBy(user);
            priceOverride = priceOverrideRepository.save(priceOverride);
            
            // Create audit trail if price override was activated
            if (wasActivatedNow) {
                createAuditTrailForPriceOverrideActivation(user, priceOverride);
            }
            schedulePriceOverrideJobsSafely(priceOverride,
                    "Successfully scheduled Quartz jobs for price override {}",
                    "CRITICAL: Failed to schedule price override jobs for ID: {}. "
                            + "Price override will not activate/deactivate automatically. Manual intervention required. "
                            + "Error: {}");
            
            log.info("Updated schedule for price override with ID: {}", priceOverride.getId());
            
            // Build response
            List<PriceOverrideTranslation> savedTranslations = priceOverrideTranslationRepository
                    .findByPriceOverrideId(priceOverride.getId());
            
            // Get all mappings with relations
            List<PriceOverrideMapping> mappings = priceOverrideMappingRepository
                    .findByPriceOverrideIdWithRelations(priceOverride.getId());
            
            // Build mapping responses with ALL translations
            List<PriceOverrideResponse.MappingResponse> mappingResponses = buildMappingResponses(mappings, userLocale, false);
            
            PriceOverrideResponse response = buildPriceOverrideResponseWithAllTranslations(priceOverride, savedTranslations);
            response.setMappings(mappingResponses);
            
            return ResponseDto.<PriceOverrideResponse>builder()
                    .message(messageUtil.getMessage("price.override.schedule.update.success", userLocale))
                    .data(response)
                    .build();
                    
        } catch (ResponseStatusException e) {
            throw e;
        } catch (Exception e) {
            log.error("Error updating price override schedule: {}", e.getMessage(), e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "Failed to update price override schedule: " + e.getMessage());
        }
    }
    
    /**
     * Schedules the deactivation of a price override at a specific UTC time (validTo).
     * Validates that validTo is in UTC, is in the future, is after validFrom (if exists),
     * checks for overlapping price overrides, cancels existing deactivation job,
     * updates validTo, updates status if needed, schedules a new Quartz job for deactivation,
     * and creates an audit trail if activated.
     *
     * @param id the ID of the price override to schedule deactivation for
     * @param request the deactivation schedule request containing validTo
     * @param userId the ID of the user scheduling the deactivation
     * @param locale the locale for localized error messages
     * @return a response containing the updated price override with all translations and mappings
     * @throws ResponseStatusException if validation fails, price override not found, is deleted, or scheduling fails
     */
    @Override
    @Transactional
    public ResponseDto<PriceOverrideResponse> schedulePriceOverrideDeactivation(UUID id, 
            SchedulePriceOverrideDeactivationRequest request, 
            String userId, String locale) {
        Locale userLocale = Locale.forLanguageTag(locale);
        
        try {
            // Find user
            User user = userRepository.findById(UUID.fromString(userId))
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                            messageUtil.getMessage(MSG_USER_NOT_FOUND, userLocale, userId)));
            
            // Find price override
            PriceOverride priceOverride = priceOverrideRepository.findById(id)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                            messageUtil.getMessage(MSG_PRICE_OVERRIDE_ERROR_NOT_FOUND, userLocale, id)));
            
            if (Boolean.TRUE.equals(priceOverride.getIsDeleted())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        messageUtil.getMessage(MSG_PRICE_OVERRIDE_ERROR_DELETED, userLocale));
            }
            
            // Validate validTo
            if (!request.getValidTo().getOffset().equals(ZoneOffset.UTC)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        messageUtil.getMessage(MSG_PRICE_OVERRIDE_SCHEDULE_NOT_UTC, userLocale));
            }
            
            OffsetDateTime nowUtc = OffsetDateTime.now(ZoneOffset.UTC);
            OffsetDateTime validToUtc = convertToUtc(request.getValidTo());
            
            // Validate that validTo is in the future
            if (validToUtc.isBefore(nowUtc) || validToUtc.equals(nowUtc)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        messageUtil.getMessage(MSG_PRICE_OVERRIDE_SCHEDULE_INVALID_DATE_RANGE, userLocale) +
                        " validTo must be in the future.");
            }
            
            // Validate that validTo is after validFrom (if validFrom exists)
            if (priceOverride.getValidFrom() != null && validToUtc.isBefore(priceOverride.getValidFrom())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        messageUtil.getMessage(MSG_PRICE_OVERRIDE_SCHEDULE_INVALID_DATE_RANGE, userLocale) +
                        " validTo must be after validFrom.");
            }
            
            // Validate no overlapping price overrides (only if validFrom exists)
            if (priceOverride.getValidFrom() != null) {
                validateNoOverlappingPriceOverrides(priceOverride, priceOverride.getValidFrom(), validToUtc, userLocale);
            }
            
            // Cancel existing deactivation job if any
            cancelScheduledJobsForPriceOverride(id);
            
            // Update validTo
            priceOverride.setValidTo(validToUtc);
            
            // Update status if needed:
            // - If currently LIVE, keep it LIVE (will be set to UNSCHEDULED when validTo time arrives)
            // - If currently SCHEDULED, keep it SCHEDULED
            // - If currently UNSCHEDULED and validFrom is in the past, set to LIVE
            // - If currently UNSCHEDULED and validFrom is in the future, set to SCHEDULED
            boolean wasActivatedNow = false;
            if (priceOverride.getStatus() == PriceOverrideStatus.UNSCHEDULED) {
                if (priceOverride.getValidFrom() != null
                        && (priceOverride.getValidFrom().isBefore(nowUtc)
                        || priceOverride.getValidFrom().isEqual(nowUtc))) {
                    priceOverride.setStatus(PriceOverrideStatus.LIVE);
                    wasActivatedNow = true;
                    log.info("Price override was UNSCHEDULED, validFrom is in past - setting to LIVE");
                } else if (priceOverride.getValidFrom() != null) {
                    priceOverride.setStatus(PriceOverrideStatus.SCHEDULED);
                    log.info("Price override was UNSCHEDULED, validFrom is in future - setting to SCHEDULED");
                }
            }
            // If status is LIVE or SCHEDULED, keep it as is (will be updated by jobs)
            
            priceOverride.setUpdatedBy(user);
            priceOverride = priceOverrideRepository.save(priceOverride);
            
            // Create audit trail if price override was activated
            if (wasActivatedNow) {
                createAuditTrailForPriceOverrideActivation(user, priceOverride);
            }
            schedulePriceOverrideJobsSafely(priceOverride,
                    "Successfully scheduled deactivation job for price override {} at {}",
                    "CRITICAL: Failed to schedule deactivation job for ID: {}. "
                            + "Price override will not deactivate automatically. Manual intervention required. "
                            + "Error: {}",
                    validToUtc);
            
            log.info("Scheduled deactivation for price override with ID: {} at {}", 
                priceOverride.getId(), validToUtc);
            
            // Build response
            List<PriceOverrideTranslation> savedTranslations = priceOverrideTranslationRepository
                    .findByPriceOverrideId(priceOverride.getId());
            
            // Get all mappings with relations
            List<PriceOverrideMapping> mappings = priceOverrideMappingRepository
                    .findByPriceOverrideIdWithRelations(priceOverride.getId());
            
            // Build mapping responses with ALL translations
            List<PriceOverrideResponse.MappingResponse> mappingResponses = buildMappingResponses(mappings, userLocale, false);
            
            PriceOverrideResponse response = buildPriceOverrideResponseWithAllTranslations(priceOverride, savedTranslations);
            response.setMappings(mappingResponses);
            
            return ResponseDto.<PriceOverrideResponse>builder()
                    .message(messageUtil.getMessage("price.override.schedule.deactivation.success", userLocale))
                    .data(response)
                    .build();
                    
        } catch (ResponseStatusException e) {
            throw e;
        } catch (Exception e) {
            log.error("Error scheduling price override deactivation: {}", e.getMessage(), e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "Failed to schedule price override deactivation: " + e.getMessage());
        }
    }
    
    /**
     * Validates schedule fields at API level
     * Allows validFrom in the past for immediate activation
     * validTo is optional - can be null for indefinite activation
     */
    private void validateScheduleFields(UpdatePriceOverrideScheduleRequest request, 
            Locale userLocale) {
        // 1. Validate UTC timezone for validFrom
        if (!request.getValidFrom().getOffset().equals(ZoneOffset.UTC)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    messageUtil.getMessage(MSG_PRICE_OVERRIDE_SCHEDULE_NOT_UTC, userLocale));
        }
        
        // 2. Validate UTC timezone for validTo if provided
        if (request.getValidTo() != null && !request.getValidTo().getOffset().equals(ZoneOffset.UTC)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    messageUtil.getMessage(MSG_PRICE_OVERRIDE_SCHEDULE_NOT_UTC, userLocale));
        }
        
        OffsetDateTime validFromUtc = convertToUtc(request.getValidFrom());
        OffsetDateTime validToUtc = request.getValidTo() != null ? convertToUtc(request.getValidTo()) : null;
        
        // 3. validFrom can be in the past (for immediate activation) - no validation needed
        
        // 4. If validTo is provided, validate that it is after validFrom
        if (validToUtc != null && (validToUtc.isBefore(validFromUtc) || validToUtc.equals(validFromUtc))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    messageUtil.getMessage(MSG_PRICE_OVERRIDE_SCHEDULE_INVALID_DATE_RANGE, userLocale));
        }
        
        log.info("Schedule validation passed - Activation: {}, Deactivation: {}", 
            validFromUtc, validToUtc != null ? validToUtc : "Not set (indefinite)");
    }
    
    /**
     * Validate that there are no overlapping price overrides for the same restaurant
     * Rules:
     * 1. If a menu-level price override is LIVE, cannot schedule a category-level override
     * 2. If a category-level price override is LIVE, cannot schedule a menu-level override
     * 3. Cannot schedule menu-level overrides with overlapping date ranges
     * 4. Cannot schedule category-level overrides with overlapping date ranges ONLY if they target the same categories
     * 5. Multiple category-level overrides for different categories can coexist with overlapping dates
     */
    private void validateNoOverlappingPriceOverrides(PriceOverride priceOverride, 
            OffsetDateTime validFrom, OffsetDateTime validTo, Locale userLocale) {
        // Get restaurant ID from price override mappings
        List<PriceOverrideMapping> mappings = priceOverrideMappingRepository
                .findByPriceOverrideIdWithRelations(priceOverride.getId());
        
        if (mappings.isEmpty()) {
            log.warn("Price override {} has no mappings, skipping overlap validation", priceOverride.getId());
            return;
        }
        
        // Get restaurant ID (all mappings should be for the same restaurant)
        UUID restaurantId = mappings.get(0).getRestaurant().getId();
        
        // Get categories for current price override (if category-level)
        Set<UUID> currentCategoryMappingIds = new HashSet<>();
        if (priceOverride.getOverrideLevel() == OverrideLevel.CATEGORY) {
            currentCategoryMappingIds = mappings.stream()
                    .map(this::extractMenuCategoryMappingId)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toSet());
        }
        
        // Get all other price overrides for the same restaurant (excluding current one)
        List<PriceOverride> otherOverrides = priceOverrideMappingRepository
                .findDistinctPriceOverridesByRestaurantId(restaurantId)
                .stream()
                .filter(po -> !po.getId().equals(priceOverride.getId()))
                .filter(po -> !Boolean.TRUE.equals(po.getIsDeleted()))
                .collect(Collectors.toList());
        
        // Check for LIVE override conflicts:
        // 1. If scheduling a category-level override, cannot have a LIVE menu-level override
        // 2. If scheduling a menu-level override, cannot have a LIVE category-level override
        // NOTE: Skip this check if current override is already LIVE - it's already active and coexisting,
        // so we're just scheduling when it should end (deactivation), not creating a new conflict
        if (priceOverride.getStatus() != PriceOverrideStatus.LIVE) {
            if (priceOverride.getOverrideLevel() == OverrideLevel.CATEGORY) {
                boolean hasLiveMenuOverride = otherOverrides.stream()
                        .anyMatch(po -> po.getOverrideLevel() == OverrideLevel.MENU 
                                && po.getStatus() == PriceOverrideStatus.LIVE);
                
                if (hasLiveMenuOverride) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                            messageUtil.getMessage("price.override.schedule.menu.live.conflict", userLocale));
                }
            } else if (priceOverride.getOverrideLevel() == OverrideLevel.MENU) {
                boolean hasLiveCategoryOverride = otherOverrides.stream()
                        .anyMatch(po -> po.getOverrideLevel() == OverrideLevel.CATEGORY 
                                && po.getStatus() == PriceOverrideStatus.LIVE);
                
                if (hasLiveCategoryOverride) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                            messageUtil.getMessage("price.override.schedule.category.live.conflict", userLocale));
                }
            }
        }
        
        // Check for date overlaps with LIVE or SCHEDULED overrides
        for (PriceOverride otherOverride : otherOverrides) {
            if (shouldValidateDateOverlap(priceOverride, otherOverride, currentCategoryMappingIds)
                    && hasDateOverlap(validFrom, validTo, otherOverride.getValidFrom(), otherOverride.getValidTo())) {
                String overrideLevelStr = otherOverride.getOverrideLevel() == OverrideLevel.MENU 
                        ? "menu" : "category";
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        messageUtil.getMessage("price.override.schedule.date.overlap", userLocale, overrideLevelStr));
            }
        }
    }
    
    /**
     * Convert OffsetDateTime to UTC
     */
    private OffsetDateTime convertToUtc(OffsetDateTime dateTime) {
        return dateTime == null ? null : dateTime.withOffsetSameInstant(ZoneOffset.UTC);
    }
    
    /**
     * Check if schedule is currently valid
     * Uses validFrom and validTo directly
     */
    private boolean isScheduleCurrentlyValid(OffsetDateTime validFrom, OffsetDateTime validTo) {
        OffsetDateTime nowUtc = OffsetDateTime.now(ZoneOffset.UTC);

        return (validFrom == null || !nowUtc.isBefore(validFrom))
                && (validTo == null || !nowUtc.isAfter(validTo));
    }
    
    /**
     * Schedule Quartz jobs for price override activation and deactivation
     * Uses validFrom and validTo directly
     */
    private void schedulePriceOverrideJobs(PriceOverride priceOverride) throws SchedulerException {
        OffsetDateTime nowUtc = OffsetDateTime.now(ZoneOffset.UTC);
        UUID priceOverrideId = priceOverride.getId();
        
        // Cancel any existing jobs first
        cancelScheduledJobsForPriceOverride(priceOverrideId);
        
        // Schedule activation job using validFrom directly
        if (priceOverride.getValidFrom() != null) {
            OffsetDateTime activationTime = priceOverride.getValidFrom();
            
            log.info("Scheduling activation job for price override {} at {}", 
                priceOverrideId, activationTime);
            
            // Only schedule if activation time is in the future
            if (activationTime.isAfter(nowUtc)) {
                JobDetail activationJob = JobBuilder.newJob(PriceOverrideActivationJob.class)
                        .withIdentity("price-override-activation-" + priceOverrideId, JOB_GROUP_PRICE_OVERRIDE_JOBS)
                        .usingJobData(FIELD_PRICE_OVERRIDE_ID, priceOverrideId.toString())
                        .build();
                
                Date activationDate = Date.from(activationTime.toInstant());
                Trigger activationTrigger = TriggerBuilder.newTrigger()
                        .withIdentity("price-override-activation-trigger-" + priceOverrideId, JOB_GROUP_PRICE_OVERRIDE_JOBS)
                        .startAt(activationDate)
                        .build();
                
                scheduler.scheduleJob(activationJob, activationTrigger);
                log.info("Scheduled activation job for price override {} at {}", 
                    priceOverrideId, activationTime);
            } else {
                log.info("Activation time {} is in the past, not scheduling job", activationTime);
            }
        }
        
        // Schedule deactivation job using validTo directly
        if (priceOverride.getValidTo() != null) {
            OffsetDateTime deactivationTime = priceOverride.getValidTo();
            
            log.info("Scheduling deactivation job for price override {} at {}", 
                priceOverrideId, deactivationTime);
            
            // Only schedule if deactivation time is in the future
            if (deactivationTime.isAfter(nowUtc)) {
                JobDetail deactivationJob = JobBuilder.newJob(PriceOverrideDeactivationJob.class)
                        .withIdentity("price-override-deactivation-" + priceOverrideId, JOB_GROUP_PRICE_OVERRIDE_JOBS)
                        .usingJobData(FIELD_PRICE_OVERRIDE_ID, priceOverrideId.toString())
                        .build();
                
                Date deactivationDate = Date.from(deactivationTime.toInstant());
                Trigger deactivationTrigger = TriggerBuilder.newTrigger()
                        .withIdentity("price-override-deactivation-trigger-" + priceOverrideId, JOB_GROUP_PRICE_OVERRIDE_JOBS)
                        .startAt(deactivationDate)
                        .build();
                
                scheduler.scheduleJob(deactivationJob, deactivationTrigger);
                log.info("Scheduled deactivation job for price override {} at {}", 
                    priceOverrideId, deactivationTime);
            } else {
                log.info("Deactivation time {} is in the past, not scheduling job", deactivationTime);
            }
        }
    }
    
    /**
     * Cancel scheduled jobs for a price override
     * Similar to menu scheduling cleanup pattern
     */
    private void cancelScheduledJobsForPriceOverride(UUID priceOverrideId) throws SchedulerException {
        try {
            // Search for jobs in the price-override-jobs group
            // If group doesn't exist, getJobKeys will return empty collection (safe)
            for (JobKey jobKey : scheduler.getJobKeys(GroupMatcher.jobGroupEquals(JOB_GROUP_PRICE_OVERRIDE_JOBS))) {
                deleteJobIfMatchesPriceOverride(jobKey, priceOverrideId);
            }
        } catch (SchedulerException e) {
            log.error("Error accessing scheduler jobs for price override {}: {}", priceOverrideId, e.getMessage());
            throw e; // Re-throw to allow caller to handle
        }
    }
    
    /**
     * Helper method to delete a job if it matches the price override ID
     */
    private void deleteJobIfMatchesPriceOverride(JobKey jobKey, UUID priceOverrideId) {
        try {
            JobDetail jobDetail = scheduler.getJobDetail(jobKey);
            if (jobDetail != null && jobDetail.getJobDataMap() != null) {
                String jobPriceOverrideId = jobDetail.getJobDataMap().getString(FIELD_PRICE_OVERRIDE_ID);
                if (jobPriceOverrideId != null && jobPriceOverrideId.equalsIgnoreCase(priceOverrideId.toString())) {
                    scheduler.deleteJob(jobKey);
                    log.info("Deleted Quartz job {} for price override {}", jobKey, priceOverrideId);
                }
            }
        } catch (SchedulerException e) {
            log.warn("Failed to delete job {} for price override {}: {}", jobKey, priceOverrideId, e.getMessage());
            // Continue with other jobs
        }
    }
    
    /**
     * Soft-deletes a price override by setting its isDeleted flag to true and status to UNSCHEDULED.
     * Cancels any scheduled Quartz jobs for the price override and creates an audit trail.
     *
     * @param id the ID of the price override to delete
     * @param userId the ID of the user deleting the price override
     * @param locale the locale for localized error messages
     * @return a response containing a success message
     * @throws ResponseStatusException if validation fails, price override not found, is already deleted, or deletion fails
     */
    @Override
    @Transactional
    public ResponseDto<String> deletePriceOverride(UUID id, String userId, String locale) {
        Locale userLocale = Locale.forLanguageTag(locale);
        
        try {
            // Validate user
            User user = userRepository.findById(UUID.fromString(userId))
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                            messageUtil.getMessage(MSG_USER_NOT_FOUND, userLocale, userId)));
            
            // Find price override
            PriceOverride priceOverride = priceOverrideRepository.findById(id)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                            messageUtil.getMessage(MSG_PRICE_OVERRIDE_ERROR_NOT_FOUND, userLocale, id)));
            
            // Check if price override is already deleted
            if (Boolean.TRUE.equals(priceOverride.getIsDeleted())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        messageUtil.getMessage(MSG_PRICE_OVERRIDE_ERROR_DELETED, userLocale));
            }
            
            cancelScheduledJobsSafely(id);
            
            // Perform soft delete
            priceOverride.setIsDeleted(true);
            priceOverride.setStatus(PriceOverrideStatus.UNSCHEDULED);
            priceOverride.setUpdatedBy(user);
            priceOverride = priceOverrideRepository.save(priceOverride);
            
            log.info("Deleted price override with ID: {}", id);
            
            // Create audit trail for price override deletion
            createAuditTrailForPriceOverrideDeletion(user, priceOverride);

            return ResponseDto.<String>builder()
                    .message(messageUtil.getMessage("price.override.delete.success", userLocale))
                    .data("Price override with ID " + id + " has been deleted")
                    .build();
                    
        } catch (ResponseStatusException e) {
            throw e;
        } catch (Exception e) {
            log.error("Error deleting price override: {}", e.getMessage(), e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "Failed to delete price override: " + e.getMessage());
        }
    }

    /**
     * Retrieves a paginated and filterable list of items impacted by a specific price override for a given restaurant.
     * Determines impacted items based on the price override's level (MENU or CATEGORY) and mappings,
     * applies search filtering, sorting, and pagination.
     *
     * @param priceOverrideId the ID of the price override
     * @param restaurantId the ID of the restaurant
     * @param page the page number (1-based, will be converted to 0-based)
     * @param size the page size
     * @param search the search term to filter by item name
     * @param sortBy the field to sort by (defaults to "name")
     * @param direction the sort direction (defaults to ASC)
     * @param locale the locale for localized error messages and item translations
     * @return a response containing a paginated list of impacted items with translations
     * @throws ResponseStatusException if the price override or restaurant is not found, or if the price override is deleted
     */
    @Override
    @Transactional(readOnly = true)
    public ResponseDto<PriceOverrideImpactedItemListResponse> getImpactedItemsByPriceOverride(
            UUID priceOverrideId, UUID restaurantId, Integer page, Integer size,
            String search, String sortBy, Sort.Direction direction, String locale) {
        
        Locale userLocale = Locale.forLanguageTag(locale);
        
        try {
            // 1. Validate price override
            PriceOverride priceOverride = priceOverrideRepository.findById(priceOverrideId)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                            messageUtil.getMessage(MSG_PRICE_OVERRIDE_ERROR_NOT_FOUND, userLocale, priceOverrideId)));
            
            if (Boolean.TRUE.equals(priceOverride.getIsDeleted())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        messageUtil.getMessage(MSG_PRICE_OVERRIDE_ERROR_DELETED, userLocale));
            }
            
            // 2. Validate restaurant
            restaurantRepository.findById(restaurantId)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                            messageUtil.getMessage(MSG_RESTAURANT_NOT_FOUND, userLocale, restaurantId)));
            
            // 3. Get price override mappings for this restaurant
            List<PriceOverrideMapping> mappings = priceOverrideMappingRepository
                    .findByPriceOverrideIdWithRelations(priceOverrideId)
                    .stream()
                    .filter(m -> m.getRestaurant() != null && m.getRestaurant().getId().equals(restaurantId))
                    .collect(Collectors.toList());
            
            if (mappings.isEmpty()) {
                return buildEmptyImpactedItemsResponse(userLocale);
            }

            List<UUID> menuCategoryMappingIds = resolveImpactedMenuCategoryMappingIds(
                    priceOverride, mappings, userLocale);
            
            if (menuCategoryMappingIds.isEmpty()) {
                return buildEmptyImpactedItemsResponse(userLocale);
            }
            
            // 5. Query category_item_mapping table using menu_category_mapping_ids to get items
            List<com.gulfnet.shared_library.entity.CategoryItemMapping> categoryItemMappings = 
                    categoryItemMappingRepository.findByMenuCategoryMappingIdsAndRestaurant(
                            menuCategoryMappingIds, restaurantId);
            
            List<Item> items = categoryItemMappings.stream()
                    .map(com.gulfnet.shared_library.entity.CategoryItemMapping::getItem)
                    .filter(Objects::nonNull)
                    .distinct()
                    .collect(Collectors.toList());
            
            if (items.isEmpty()) {
                return buildEmptyImpactedItemsResponse(userLocale);
            }
            
            items = filterImpactedItemsBySearch(items, search);
            Map<UUID, List<ItemTranslation>> translationsMap = loadItemTranslations(items);
            List<PriceOverrideResponse.TranslationResponse> priceOverrideTranslationResponses =
                    buildPriceOverrideTranslationResponses(priceOverrideId, locale);
            List<PriceOverrideImpactedItemResponse> allItemResponses = buildImpactedItemResponses(
                    items, translationsMap, priceOverride, locale);
            sortImpactedItemResponses(allItemResponses, sortBy, direction, locale);
            long totalItems = allItemResponses.size();
            List<PriceOverrideImpactedItemResponse> itemResponses = paginateImpactedItemResponses(
                    allItemResponses, page, size);
            PaginationMetaData metaData = buildPaginationMetaData(page, size, totalItems);
            
            PriceOverrideImpactedItemListResponse response = PriceOverrideImpactedItemListResponse.builder()
                    .priceOverrideId(priceOverride.getId())
                    .overrideLevel(priceOverride.getOverrideLevel())
                    .overrideType(priceOverride.getOverrideType())
                    .overrideValue(priceOverride.getOverrideValue())
                    .priceOverrideStatus(priceOverride.getStatus())
                    .validFrom(priceOverride.getValidFrom())
                    .validTo(priceOverride.getValidTo())
                    .priceOverrideTranslations(priceOverrideTranslationResponses)
                    .items(itemResponses)
                    .count((long) itemResponses.size())
                    .total(totalItems)
                    .metaData(metaData)
                    .build();
            
            return ResponseDto.<PriceOverrideImpactedItemListResponse>builder()
                    .message(messageUtil.getMessage(MSG_PRICE_OVERRIDE_LIST_SUCCESS, userLocale))
                    .data(response)
                    .build();
            
        } catch (ResponseStatusException e) {
            throw e;
        } catch (Exception e) {
            log.error("Error fetching impacted items: {}", e.getMessage(), e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "Failed to fetch impacted items: " + e.getMessage());
        }
    }

    /**
     * Schedules Quartz jobs for {@code priceOverride}, logging {@code successMessage} or {@code failureMessage} without failing the request.
     */
    private void schedulePriceOverrideJobsSafely(PriceOverride priceOverride, String successMessage,
            String failureMessage, Object... successArgs) {
        try {
            schedulePriceOverrideJobs(priceOverride);
            Object[] resolvedSuccessArgs = successArgs.length == 0
                    ? new Object[]{priceOverride.getId()}
                    : prependArgument(priceOverride.getId(), successArgs);
            log.info(successMessage, resolvedSuccessArgs);
        } catch (SchedulerException e) {
            log.error(failureMessage, priceOverride.getId(), e.getMessage(), e);
        }
    }

    private void cancelScheduledJobsSafely(UUID priceOverrideId) {
        try {
            cancelScheduledJobsForPriceOverride(priceOverrideId);
            log.info("Cancelled scheduled jobs for price override {}", priceOverrideId);
        } catch (SchedulerException e) {
            log.error("Failed to cancel scheduled jobs for price override {}: {}", priceOverrideId, e.getMessage(), e);
        }
    }

    private Object[] prependArgument(Object firstArgument, Object... remainingArguments) {
        Object[] result = new Object[remainingArguments.length + 1];
        result[0] = firstArgument;
        System.arraycopy(remainingArguments, 0, result, 1, remainingArguments.length);
        return result;
    }

    /**
     * Whether {@code otherOverride} should participate in date-overlap validation against {@code currentOverride}
     * (status, level, and category intersection rules).
     */
    private boolean shouldValidateDateOverlap(PriceOverride currentOverride, PriceOverride otherOverride,
            Set<UUID> currentCategoryMappingIds) {
        if (otherOverride.getStatus() != PriceOverrideStatus.LIVE
                && otherOverride.getStatus() != PriceOverrideStatus.SCHEDULED) {
            return false;
        }

        if (currentOverride.getOverrideLevel() == OverrideLevel.MENU) {
            return otherOverride.getOverrideLevel() == OverrideLevel.MENU;
        }

        if (currentOverride.getOverrideLevel() != OverrideLevel.CATEGORY) {
            return false;
        }

        if (otherOverride.getOverrideLevel() == OverrideLevel.MENU) {
            return true;
        }

        if (otherOverride.getOverrideLevel() != OverrideLevel.CATEGORY) {
            return false;
        }

        Set<UUID> otherCategoryMappingIds = priceOverrideMappingRepository
                .findByPriceOverrideId(otherOverride.getId())
                .stream()
                .map(this::extractMenuCategoryMappingId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Set<UUID> intersection = new HashSet<>(currentCategoryMappingIds);
        intersection.retainAll(otherCategoryMappingIds);
        return !intersection.isEmpty();
    }

    /**
     * Inclusive range overlap check supporting open-ended ({@code null}) end bounds on either interval.
     */
    private boolean hasDateOverlap(OffsetDateTime validFrom, OffsetDateTime validTo,
            OffsetDateTime otherValidFrom, OffsetDateTime otherValidTo) {
        if (otherValidFrom == null) {
            return false;
        }
        if (validTo == null && otherValidTo == null) {
            return true;
        }
        if (validTo == null) {
            return !validFrom.isAfter(otherValidTo);
        }
        if (otherValidTo == null) {
            return !otherValidFrom.isAfter(validTo);
        }
        return validFrom.isBefore(otherValidTo) && validTo.isAfter(otherValidFrom);
    }

    /**
     * Collects {@link MenuCategoryMapping} ids impacted by a menu-level override (all categories under the menu) or
     * explicit category mappings for category-level overrides.
     */
    private List<UUID> resolveImpactedMenuCategoryMappingIds(PriceOverride priceOverride,
            List<PriceOverrideMapping> mappings, Locale userLocale) {
        if (priceOverride.getOverrideLevel() == OverrideLevel.MENU) {
            UUID menuId = mappings.stream()
                    .filter(m -> m.getMenu() != null)
                    .map(m -> m.getMenu().getId())
                    .findFirst()
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST,
                            messageUtil.getMessage("price.override.error.menu.required", userLocale)));

            return menuCategoryMappingRepository.findByMenuId(menuId).stream()
                    .map(MenuCategoryMapping::getId)
                    .collect(Collectors.toList());
        }

        if (priceOverride.getOverrideLevel() == OverrideLevel.CATEGORY) {
            return mappings.stream()
                    .map(this::extractMenuCategoryMappingId)
                    .filter(Objects::nonNull)
                    .distinct()
                    .collect(Collectors.toList());
        }

        return new ArrayList<>();
    }

    /**
     * Case-insensitive filter of items by any translation name containing {@code search}.
     */
    private List<Item> filterImpactedItemsBySearch(List<Item> items, String search) {
        if (search == null || search.trim().isEmpty()) {
            return items;
        }

        String searchLower = search.toLowerCase().trim();
        Map<UUID, List<ItemTranslation>> translationsMap = loadItemTranslations(items);
        return items.stream()
                .filter(item -> translationsMap.getOrDefault(item.getId(), Collections.emptyList()).stream()
                        .anyMatch(t -> t.getName() != null && t.getName().toLowerCase().contains(searchLower)))
                .collect(Collectors.toList());
    }

    private Map<UUID, List<ItemTranslation>> loadItemTranslations(List<Item> items) {
        List<UUID> itemIds = items.stream().map(Item::getId).collect(Collectors.toList());
        if (itemIds.isEmpty()) {
            return new HashMap<>();
        }

        return itemTranslationRepository.findAllByItemIdIn(itemIds).stream()
                .collect(Collectors.groupingBy(t -> t.getItem().getId()));
    }

    /**
     * Builds a minimal translation list for a price override, preferring {@code locale} with configured fallbacks.
     */
    private List<PriceOverrideResponse.TranslationResponse> buildPriceOverrideTranslationResponses(
            UUID priceOverrideId, String locale) {
        List<PriceOverrideTranslation> priceOverrideTranslations = priceOverrideTranslationRepository
                .findByPriceOverrideId(priceOverrideId);
        List<PriceOverrideResponse.TranslationResponse> translationResponses = new ArrayList<>();
        if (priceOverrideTranslations.isEmpty()) {
            return translationResponses;
        }

        PriceOverrideTranslation exactMatch = priceOverrideTranslations.stream()
                .filter(t -> t.getLanguageCode() != null && t.getLanguageCode().equals(locale))
                .findFirst()
                .orElse(null);
        if (exactMatch != null) {
            translationResponses.add(PriceOverrideResponse.TranslationResponse.builder()
                    .languageCode(exactMatch.getLanguageCode())
                    .name(exactMatch.getName())
                    .build());
            return translationResponses;
        }

        TranslationUtils.pickPreferredOrFromList(priceOverrideTranslations, locale, localizationProperties.getLanguages(),
                PriceOverrideTranslation::getLanguageCode)
                .ifPresent(trans -> translationResponses.add(PriceOverrideResponse.TranslationResponse.builder()
                        .languageCode(trans.getLanguageCode())
                        .name(trans.getName())
                        .build()));
        return translationResponses;
    }

    private List<PriceOverrideImpactedItemResponse> buildImpactedItemResponses(List<Item> items,
            Map<UUID, List<ItemTranslation>> translationsMap, PriceOverride priceOverride, String locale) {
        return items.stream()
                .map(item -> buildImpactedItemResponse(item, translationsMap, priceOverride, locale))
                .collect(Collectors.toList());
    }

    /**
     * One impacted-menu row with localized name, formatted base/overridden prices, and item metadata.
     */
    private PriceOverrideImpactedItemResponse buildImpactedItemResponse(Item item,
            Map<UUID, List<ItemTranslation>> translationsMap, PriceOverride priceOverride, String locale) {
        List<ItemTranslation> itemTranslations = translationsMap.getOrDefault(item.getId(), Collections.emptyList());
        ItemTranslation selectedTranslation = itemTranslations.stream()
                .filter(t -> t.getLanguageCode() != null && t.getLanguageCode().equals(locale))
                .findFirst()
                .orElse(itemTranslations.isEmpty() ? null : itemTranslations.get(0));

        List<ItemTranslationDto> translationDtos = new ArrayList<>();
        if (selectedTranslation != null) {
            translationDtos.add(ItemTranslationDto.builder()
                    .languageCode(selectedTranslation.getLanguageCode())
                    .name(selectedTranslation.getName())
                    .description(selectedTranslation.getDescription())
                    .build());
        }

        BigDecimal overriddenPrice = calculateOverriddenPrice(
                item.getBasePrice(), priceOverride.getOverrideType(), priceOverride.getOverrideValue());
        String currency = restaurantChainConfigProperties.getChain() != null
                ? restaurantChainConfigProperties.getChain().getCurrency()
                : null;

        return PriceOverrideImpactedItemResponse.builder()
                .itemId(item.getId())
                .basePrice(item.getBasePrice() != null
                        ? CurrencyFormatter.formatAmount(BigDecimal.valueOf(item.getBasePrice()), currency).doubleValue()
                        : null)
                .overriddenPrice(overriddenPrice != null
                        ? CurrencyFormatter.formatAmount(overriddenPrice, currency)
                        : null)
                .outOfStock(item.getOutOfStock())
                .itemStatus(item.getStatus())
                .dietaryPreference(item.getDietaryPreference())
                .createdAt(item.getCreatedAt() != null ? item.getCreatedAt().toLocalDateTime() : null)
                .updatedAt(item.getUpdatedAt() != null ? item.getUpdatedAt().toLocalDateTime() : null)
                .translations(translationDtos)
                .build();
    }

    /**
     * Sorts impacted items by overridden price or by localized name fields using {@link LocaleSortUtil}.
     */
    private void sortImpactedItemResponses(List<PriceOverrideImpactedItemResponse> allItemResponses, String sortBy,
            Sort.Direction direction, String locale) {
        String normalizedSortBy = (sortBy != null && !sortBy.trim().isEmpty()) ? sortBy.trim() : "createdAt";
        String sortField = normalizedSortBy.toLowerCase();

        if ("newprice".equals(sortField) || "overriddenprice".equals(sortField)
                || "overridden_price".equals(sortField)) {
            allItemResponses.sort((r1, r2) -> {
                BigDecimal price1 = r1.getOverriddenPrice() != null ? r1.getOverriddenPrice() : BigDecimal.ZERO;
                BigDecimal price2 = r2.getOverriddenPrice() != null ? r2.getOverriddenPrice() : BigDecimal.ZERO;
                int comparison = price1.compareTo(price2);
                return direction == Sort.Direction.ASC ? comparison : -comparison;
            });
            return;
        }

        LocaleContextHolder.setLocale(Locale.forLanguageTag(locale));
        LocaleSortUtil.sortName(allItemResponses, normalizedSortBy,
                direction != null ? direction : Sort.Direction.DESC);
    }

    private List<PriceOverrideImpactedItemResponse> paginateImpactedItemResponses(
            List<PriceOverrideImpactedItemResponse> allItemResponses, Integer page, Integer size) {
        int pageNumber = (page != null && page > 0) ? page - 1 : 0;
        int pageSize = (size != null && size > 0) ? size : Integer.MAX_VALUE;
        int start = pageNumber * pageSize;
        int end = Math.min(start + pageSize, allItemResponses.size());
        return start < allItemResponses.size()
                ? allItemResponses.subList(start, end)
                : new ArrayList<>();
    }

    /**
     * Builds list pagination metadata, or {@code null} when page/size are unset or non-positive.
     */
    private PaginationMetaData buildPaginationMetaData(Integer page, Integer size, long totalItems) {
        if (page == null || size == null || page <= 0 || size <= 0) {
            return null;
        }

        int totalPages = (int) Math.ceil((double) totalItems / size);
        return PaginationMetaData.builder()
                .page(page)
                .size(size)
                .totalPages(totalPages)
                .totalRecords(totalItems)
                .build();
    }
    
    /**
     * Helper method to build empty impacted items response
     */
    private ResponseDto<PriceOverrideImpactedItemListResponse> buildEmptyImpactedItemsResponse(Locale userLocale) {
        return ResponseDto.<PriceOverrideImpactedItemListResponse>builder()
                .message(messageUtil.getMessage(MSG_PRICE_OVERRIDE_LIST_SUCCESS, userLocale))
                .data(PriceOverrideImpactedItemListResponse.builder()
                        .items(new ArrayList<>())
                        .count(0L)
                        .total(0L)
                        .metaData(null)
                        .build())
                .build();
    }

    /**
     * Calculate the overridden price based on base price, override type, and override value
     */
    private BigDecimal calculateOverriddenPrice(Double basePrice, OverrideType overrideType, BigDecimal overrideValue) {
        if (basePrice == null) {
            return BigDecimal.ZERO;
        }
        
        BigDecimal basePriceDecimal = BigDecimal.valueOf(basePrice);
        
        switch (overrideType) {
            case PERCENTAGE_INCREMENT:
                return basePriceDecimal.multiply(BigDecimal.ONE.add(overrideValue.divide(BigDecimal.valueOf(100), 4, RoundingMode.HALF_UP)))
                        .setScale(2, RoundingMode.HALF_UP);
            case PERCENTAGE_DECREMENT:
                return basePriceDecimal.multiply(BigDecimal.ONE.subtract(overrideValue.divide(BigDecimal.valueOf(100), 4, RoundingMode.HALF_UP)))
                        .setScale(2, RoundingMode.HALF_UP);
            case AMOUNT_INCREMENT:
                return basePriceDecimal.add(overrideValue).setScale(2, RoundingMode.HALF_UP);
            case AMOUNT_DECREMENT:
                BigDecimal result = basePriceDecimal.subtract(overrideValue);
                return result.compareTo(BigDecimal.ZERO) < 0 ? BigDecimal.ZERO : result.setScale(2, RoundingMode.HALF_UP);
            default:
                return basePriceDecimal.setScale(2, RoundingMode.HALF_UP);
        }
    }
    
    /**
     * Helper method to create audit trail for price override creation
     */
    private void createAuditTrailForPriceOverrideCreation(User user, PriceOverride priceOverride, 
            List<PriceOverrideTranslation> savedTranslations) {
        try {
            Restaurant auditRestaurant = getAuditRestaurant(user);
            String overrideName = savedTranslations.isEmpty() ? DEFAULT_NO_TRANSLATIONS : savedTranslations.get(0).getName();
            auditTrailService.createAuditTrail(
                    user,
                    ActionType.PRICE_OVERRIDE_CREATE,
                    auditRestaurant,
                    null, // status - will default to NA for non-request actions
                    null, // ipAddress - not available in this context
                    null, // userAgent - not available in this context
                    priceOverride.getId(),
                    ENTITY_TYPE_PRICE_OVERRIDE,
                    "Price Override created: " + overrideName
            );
        } catch (Exception e) {
            log.error("Failed to create audit trail for price override creation: {}", e.getMessage());
            // Don't break price override creation flow if audit trail fails
        }
    }
    
    /**
     * Helper method to create audit trail for price override update
     */
    private void createAuditTrailForPriceOverrideUpdate(User user, PriceOverride priceOverride, 
            List<PriceOverrideTranslation> savedTranslations) {
        try {
            Restaurant auditRestaurant = getAuditRestaurant(user);
            String overrideName = savedTranslations.isEmpty() ? DEFAULT_NO_TRANSLATIONS : savedTranslations.get(0).getName();
            auditTrailService.createAuditTrail(
                    user,
                    ActionType.PRICE_OVERRIDE_UPDATE,
                    auditRestaurant,
                    null, // status - will default to NA for non-request actions
                    null, // ipAddress - not available in this context
                    null, // userAgent - not available in this context
                    priceOverride.getId(),
                    ENTITY_TYPE_PRICE_OVERRIDE,
                    "Price Override updated: " + overrideName
            );
        } catch (Exception e) {
            log.error("Failed to create audit trail for price override update: {}", e.getMessage());
            // Don't break price override update flow if audit trail fails
        }
    }
    
    /**
     * Helper method to create audit trail for price override activation
     */
    private void createAuditTrailForPriceOverrideActivation(User user, PriceOverride priceOverride) {
        try {
            Restaurant auditRestaurant = getAuditRestaurant(user);
            List<PriceOverrideTranslation> overrideTranslations = priceOverrideTranslationRepository
                    .findByPriceOverrideId(priceOverride.getId());
            String overrideName = overrideTranslations.isEmpty() ? DEFAULT_NO_TRANSLATIONS : overrideTranslations.get(0).getName();
            auditTrailService.createAuditTrail(
                    user,
                    ActionType.PRICE_OVERRIDE_ACTIVATE,
                    auditRestaurant,
                    null, // status - will default to NA for non-request actions
                    null, // ipAddress - not available in this context
                    null, // userAgent - not available in this context
                    priceOverride.getId(),
                    ENTITY_TYPE_PRICE_OVERRIDE,
                    "Price Override activated: " + overrideName
            );
        } catch (Exception e) {
            log.error("Failed to create audit trail for price override activation: {}", e.getMessage());
            // Don't break price override update flow if audit trail fails
        }
    }
    
    /**
     * Helper method to create audit trail for price override deletion
     */
    private void createAuditTrailForPriceOverrideDeletion(User user, PriceOverride priceOverride) {
        try {
            Restaurant auditRestaurant = getAuditRestaurant(user);
            List<PriceOverrideTranslation> overrideTranslations = priceOverrideTranslationRepository
                    .findByPriceOverrideId(priceOverride.getId());
            String overrideName = overrideTranslations.isEmpty() ? DEFAULT_NO_TRANSLATIONS : overrideTranslations.get(0).getName();
            auditTrailService.createAuditTrail(
                    user,
                    ActionType.PRICE_OVERRIDE_DELETE,
                    auditRestaurant,
                    null, // status - will default to NA for non-request actions
                    null, // ipAddress - not available in this context
                    null, // userAgent - not available in this context
                    priceOverride.getId(),
                    ENTITY_TYPE_PRICE_OVERRIDE,
                    "Price Override deleted: " + overrideName
            );
        } catch (Exception e) {
            log.error("Failed to create audit trail for price override deletion: {}", e.getMessage());
            // Don't break price override deletion flow if audit trail fails
        }
    }
    
    /**
     * Helper method to get audit restaurant for a user
     */
    private Restaurant getAuditRestaurant(User user) {
        if (user.getRestaurantId() != null) {
            return restaurantRepository.findById(user.getRestaurantId()).orElse(null);
        }
        return null;
    }
}
