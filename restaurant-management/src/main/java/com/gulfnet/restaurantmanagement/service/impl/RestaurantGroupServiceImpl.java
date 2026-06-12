package com.gulfnet.restaurantmanagement.service.impl;

import com.gulfnet.restaurantmanagement.service.RestaurantGroupService;
import com.gulfnet.restaurantmanagement.service.AuditTrailService;
import com.gulfnet.shared_library.config.AWSService;
import com.gulfnet.shared_library.enums.ActionType;
import com.gulfnet.shared_library.entity.RestaurantGroup;
import com.gulfnet.shared_library.entity.RestaurantGroupTranslation;
import com.gulfnet.shared_library.entity.Restaurant;
import com.gulfnet.shared_library.entity.RestaurantTranslation;
import com.gulfnet.shared_library.entity.OperatingHourSlot;
import com.gulfnet.shared_library.entity.RestaurantOperatingHours;
import com.gulfnet.shared_library.entity.Language;
import com.gulfnet.shared_library.entity.User;
import com.gulfnet.shared_library.enums.EntityStatus;
import com.gulfnet.shared_library.model.response.dto.RestaurantGroupDTO;
import com.gulfnet.shared_library.model.response.dto.RestaurantGroupTranslationDTO;
import com.gulfnet.shared_library.model.response.dto.RestaurantTranslationDto;
import com.gulfnet.shared_library.model.response.dto.OperatingHourDto;
import com.gulfnet.shared_library.model.response.dto.RestaurantResponse;
import com.gulfnet.shared_library.model.response.dto.ResponseDto;
import com.gulfnet.shared_library.model.response.dto.RestaurantGroupResponse;
import com.gulfnet.shared_library.model.response.dto.PaginationMetaData;
import com.gulfnet.shared_library.model.response.dto.RestaurantGroupListResponse;
import com.gulfnet.shared_library.model.response.dto.RestaurantListResponse;
import com.gulfnet.shared_library.model.response.dto.RestaurantMenuResponse;
import com.gulfnet.shared_library.model.response.dto.RestaurantMenuListResponse;
import com.gulfnet.shared_library.repository.RestaurantRepository;
import com.gulfnet.shared_library.repository.RestaurantTranslationRepository;
import com.gulfnet.shared_library.repository.RestaurantOperatingHoursRepository;
import com.gulfnet.shared_library.repository.RestaurantGroupRepository;
import com.gulfnet.shared_library.repository.RestaurantGroupTranslationRepository;
import com.gulfnet.shared_library.repository.LanguageRepository;
import com.gulfnet.shared_library.repository.UserRepository;
import com.gulfnet.shared_library.mapper.RestaurantOperatingHoursMapper;
import com.gulfnet.shared_library.model.request.AssignRestaurantsToGroupRequest;
import com.gulfnet.shared_library.model.request.AssignMenuToRestaurantGroupRequest;
import com.gulfnet.shared_library.entity.RestaurantMenuMapping;
import com.gulfnet.shared_library.entity.RestaurantGroupMenuMapping;
import com.gulfnet.shared_library.entity.Menu;
import com.gulfnet.shared_library.entity.RestaurantMenuId;
import com.gulfnet.shared_library.entity.RestaurantGroupMenuId;
import com.gulfnet.shared_library.enums.RestaurantMenuMappingStatus;
import com.gulfnet.shared_library.repository.RestaurantMenuMappingRepository;
import com.gulfnet.shared_library.repository.RestaurantGroupMenuMappingRepository;
import com.gulfnet.shared_library.repository.MenuRepository;
import com.gulfnet.shared_library.enums.MenuStatus;
import com.gulfnet.restaurantmanagement.util.MessageUtil;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import org.springframework.data.jpa.domain.Specification;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Subquery;
import jakarta.persistence.criteria.Root;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import com.gulfnet.shared_library.entity.RestaurantMenuMapping;
import com.gulfnet.shared_library.enums.MenuStatus;
import com.gulfnet.shared_library.repository.MenuRepository;
import com.gulfnet.shared_library.repository.RestaurantGroupMenuMappingRepository;
import com.gulfnet.shared_library.repository.RestaurantMenuMappingRepository;
import com.gulfnet.shared_library.model.request.AssignMenuToRestaurantGroupRequest;
import com.gulfnet.shared_library.entity.RestaurantGroupMenuId;
import com.gulfnet.shared_library.entity.RestaurantMenuId;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import java.util.Locale;
import java.util.Comparator;
import java.util.Map;
import java.util.HashMap;
import com.gulfnet.shared_library.model.request.AssignRestaurantsToGroupRequest;
import com.gulfnet.shared_library.enums.RestaurantMenuMappingStatus;
import com.gulfnet.shared_library.repository.MenuDiscountMappingRepository;
import com.gulfnet.shared_library.repository.MenuPromotionMappingRepository;
import org.springframework.data.jpa.domain.Specification;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.Predicate;
import java.util.ArrayList;
import com.gulfnet.shared_library.util.LocaleSortUtil;
import com.gulfnet.shared_library.util.TranslationUtils;
import org.springframework.context.i18n.LocaleContextHolder;
import com.gulfnet.restaurantmanagement.config.LocalizationProperties;
import com.gulfnet.restaurantmanagement.util.MessageUtil;


@Service
public class RestaurantGroupServiceImpl implements RestaurantGroupService {

    // Constants
    private static final String ENTITY_TYPE_RESTAURANT_GROUP = "RESTAURANT_GROUP";
    private static final String FIELD_IS_DELETED = "isDeleted";
    
    // Message key constants
    private static final String MSG_USER_NOT_FOUND = "user.not.found";
    private static final String MSG_SOME_RESTAURANTS_NOT_FOUND = "some.restaurants.not.found";
    private static final String MSG_INVALID_RESTAURANT_ID_FORMAT = "invalid.restaurant.id.format";
    private static final String MSG_RESTAURANT_GROUP_NOT_FOUND = "restaurant.group.not.found";

    @Autowired
    private RestaurantGroupRepository groupRepository;

    @Autowired
    private RestaurantGroupTranslationRepository translationRepository;

    @Autowired
    private LanguageRepository languageRepository;

    @Autowired
    private RestaurantRepository restaurantRepository;

    @Autowired
    private RestaurantTranslationRepository restaurantTranslationRepository;

    @Autowired
    private RestaurantOperatingHoursRepository restaurantOperatingHoursRepository;
    @Autowired
    private RestaurantOperatingHoursMapper restaurantOperatingHoursMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private MessageSource messageSource;

    @Autowired
    private AWSService awsService;

    @Autowired
    private MenuRepository menuRepository;

    @Autowired
    private RestaurantGroupMenuMappingRepository restaurantGroupMenuMappingRepository;

    @Autowired
    private RestaurantMenuMappingRepository restaurantMenuMappingRepository;

    @Autowired
    private MenuDiscountMappingRepository menuDiscountMappingRepository;

    @Autowired
    private MenuPromotionMappingRepository menuPromotionMappingRepository;

    @Autowired
    private com.gulfnet.shared_library.repository.RestaurantDiscountMappingRepository restaurantDiscountMappingRepository;

    @Autowired
    private com.gulfnet.shared_library.repository.RestaurantPromotionMappingRepository restaurantPromotionMappingRepository;

    @Autowired
    private LocalizationProperties localizationProperties;

    @Autowired
    private com.gulfnet.shared_library.repository.CategoryItemMappingRepository categoryItemMappingRepository;

    @Autowired
    private com.gulfnet.shared_library.repository.RestaurantItemAvailabilityRepository restaurantItemAvailabilityRepository;

    @Autowired
    private MessageUtil messageUtil;

    @Autowired
    private AuditTrailService auditTrailService;



    /**
     * Creates a new restaurant group with translations and alert-configuration fields.
     * <p>
     * Validations include:
     * - at least one translation has a non-empty name
     * - no duplicate language codes among provided translations (for non-empty names)
     * - translation name uniqueness per language across groups (when a name is provided)
     * - restaurant group code uniqueness (including deleted records)
     * <p>
     * Side effects:
     * - persists {@link RestaurantGroup} and its {@link RestaurantGroupTranslation} entries
     * - writes an audit trail entry (best-effort)
     * - evicts caches that expose restaurant groups/restaurants
     *
     * @param userId actor user id (UUID string) (required)
     * @param dto request payload used to create the group (required)
     * @return wrapper containing the created restaurant group response
     * @throws ResponseStatusException on validation failure or missing user
     */
    @Override
    @Transactional
    @CacheEvict(value = {"restaurantGroupsLite", "restaurants"}, allEntries = true)
        public ResponseDto<RestaurantGroupDTO<RestaurantGroupResponse>> saveGroup(String userId, RestaurantGroupResponse dto) {
        Locale userLocale = LocaleContextHolder.getLocale();
        List<RestaurantGroupTranslationDTO> translations = validateCreateGroupTranslations(dto.getTranslations(), userLocale);
        validateRestaurantGroupCodeForCreate(dto.getRestaurantGroupCode(), userLocale);

        User user = loadUserOrThrow(userId, userLocale);
        RestaurantGroup group = buildNewRestaurantGroup(dto, user);
        group = groupRepository.save(group);

        saveGroupTranslations(group, translations);
        group = refreshRestaurantGroup(group);

        RestaurantGroupDTO<RestaurantGroupResponse> groupDTO = buildRestaurantGroupDto(group);
        createRestaurantGroupAuditTrail(user, group, ActionType.RESTAURANT_GROUP_CREATE, "created");

        return ResponseDto.<RestaurantGroupDTO<RestaurantGroupResponse>>builder()
                .message(messageSource.getMessage("restaurantgroup.create.success", null, userLocale))
                .data(groupDTO)
                .build();

        }

/**
 * Updates an existing restaurant group, including translations and alert-configuration fields.
 * <p>
 * Validations include:
 * - group must exist and not be deleted
 * - group code uniqueness across other groups (including deleted records)
 * - translation name conflicts across other groups for the same language
 * - disallow setting group status to INACTIVE if the group contains ACTIVE restaurants
 * <p>
 * Side effects:
 * - updates group fields and translations
 * - writes an audit trail entry (best-effort)
 * - evicts the lite groups cache
 *
 * @param id restaurant group id (required)
 * @param userId actor user id (UUID string) (required)
 * @param dto updated group payload (required)
 * @return wrapper containing the updated restaurant group response
 * @throws ResponseStatusException on validation failure or missing user/group
 */
@Override
@Transactional
@CacheEvict(value = "restaurantGroupsLite", allEntries = true)
public ResponseDto<RestaurantGroupDTO<RestaurantGroupResponse>> updateGroup(UUID id, String userId, RestaurantGroupResponse dto) {
    Locale userLocale = LocaleContextHolder.getLocale();

    RestaurantGroup group = loadExistingGroupOrThrow(id, userLocale);
    validateRestaurantGroupCodeForUpdate(dto.getRestaurantGroupCode(), group, id, userLocale);

    List<RestaurantGroupTranslationDTO> translations = validateUpdateGroupTranslations(dto.getTranslations(), id, userLocale);
    EntityStatus newStatus = dto.getStatus() != null ? EntityStatus.valueOf(dto.getStatus()) : null;
    validateRestaurantGroupStatusTransition(id, group.getStatus(), newStatus, userLocale);

    User user = loadUserOrThrow(userId, userLocale);
    applyRestaurantGroupUpdates(group, dto, newStatus, user);
    group = groupRepository.save(group);

    mergeGroupTranslations(group, translations);
    group = refreshRestaurantGroup(group);

    RestaurantGroupDTO<RestaurantGroupResponse> groupDTO = buildRestaurantGroupDto(group);
    createRestaurantGroupAuditTrail(user, group, ActionType.RESTAURANT_GROUP_UPDATE, "updated");

    return ResponseDto.<RestaurantGroupDTO<RestaurantGroupResponse>>builder()
            .message(messageSource.getMessage("restaurantgroup.update.success", null, userLocale))
            .data(groupDTO)
            .build();
}

    /**
     * Fetches a restaurant group with translations and associated restaurants.
     * <p>
     * When {@code includeDeleted} is true, deleted groups are eligible for retrieval; otherwise only non-deleted groups
     * are returned.
     *
     * @param id restaurant group id (required)
     * @param userId requesting user id (currently not used for filtering; kept for API shape)
     * @param includeDeleted whether to include soft-deleted groups
     * @return wrapper containing the restaurant group and related restaurant info
     */
    @Override
    public ResponseDto<RestaurantGroupDTO<RestaurantGroupResponse>> getGroup(UUID id, String userId, Boolean includeDeleted) {
        RestaurantGroup group = loadGroupForGet(id, includeDeleted);
        List<RestaurantResponse> restaurantResponses = buildGroupRestaurantResponses(id);
        RestaurantGroupDTO<RestaurantGroupResponse> dto = buildDetailedRestaurantGroupDto(group, restaurantResponses);

        return ResponseDto.<RestaurantGroupDTO<RestaurantGroupResponse>>builder()
            .message(messageSource.getMessage("restaurantgroup.get.success", null, LocaleContextHolder.getLocale()))
            .data(dto)
            .build();
    }

    /**
     * Lists restaurant groups with filtering, search, sorting, and pagination.
     * <p>
     * Search matches group translation names (case-insensitive). Results include a locale-aware translation with fallback
     * to configured languages, plus restaurant counts and related restaurant details.
     *
     * @param page 1-based page number (optional)
     * @param size page size (optional)
     * @param status optional status filter (ACTIVE/INACTIVE)
     * @param search optional search term applied to translation name
     * @param sortBy sort field (name/createdAt; optional)
     * @param direction sort direction (optional)
     * @param locale locale tag used for translation selection and messages (required)
     * @return paged list response of restaurant groups
     */
    @Override
    @Transactional(readOnly = true)
    public ResponseDto<RestaurantGroupListResponse> getRestaurantGroups(
            Integer page,
            Integer size,
            String status,
            String search,
            String sortBy,
            Sort.Direction direction,
            String locale) {
        
        Locale userLocale = Locale.forLanguageTag(locale);
        
        // Validate & normalize pagination
        int pageNumber = (page != null ? page : 1) - 1;
        if (pageNumber < 0) pageNumber = 0;
        int pageSize = (size != null && size > 0) ? size : Integer.MAX_VALUE;
        
        // Build specification
        Specification<RestaurantGroup> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get(FIELD_IS_DELETED), false));
            
            if (status != null && !status.trim().isEmpty()) {
                try {
                    EntityStatus entityStatus = EntityStatus.valueOf(status.toUpperCase());
                    predicates.add(cb.equal(root.get("status"), entityStatus));
                } catch (IllegalArgumentException e) {
                    // Invalid status, ignore filter
                }
            }
            if (search != null && !search.trim().isEmpty()) {
                String searchTerm = "%" + search.toLowerCase() + "%";
                Join<RestaurantGroup, RestaurantGroupTranslation> translationJoin = root.join("translations");
                predicates.add(cb.like(cb.lower(translationJoin.get("name")), searchTerm));
            }
            
            return cb.and(predicates.toArray(new Predicate[0]));
        };
        
        // Fetch restaurant groups (ignore DB sort for name)
        List<RestaurantGroup> restaurantGroups = groupRepository.findAll(spec);
        
        // Batch load all translations to avoid N+1 queries
        List<UUID> groupIds = restaurantGroups.stream()
                .map(RestaurantGroup::getId)
                .collect(Collectors.toList());
        
        Map<UUID, List<RestaurantGroupTranslation>> translationsMap = translationRepository
                .findAllByRestaurantGroupIdIn(groupIds)
                .stream()
                .collect(Collectors.groupingBy(t -> t.getRestaurantGroup().getId()));
        
        // Map to DTO
        List<RestaurantGroupResponse> groupResponses = restaurantGroups.stream()
                .map(group -> {
                    // Get translations from batch-loaded map
                    List<RestaurantGroupTranslation> translations = translationsMap.getOrDefault(group.getId(), Collections.emptyList());

                    RestaurantGroupTranslation translation = translations.stream()
                            .filter(t -> t.getLanguageCode() != null && t.getLanguageCode().equalsIgnoreCase(locale))
                            .findFirst().orElse(null);

                    List<RestaurantGroupTranslationDTO> translationDTOs = new ArrayList<>();
                    if (translation != null) {
                        translationDTOs.add(RestaurantGroupTranslationDTO.builder()
                                .languageCode(translation.getLanguageCode())
                                .name(translation.getName())
                                .build());
                    } else if (!translations.isEmpty()) {
                        // Fallback to ordered languages from config
                        java.util.Optional<RestaurantGroupTranslation> cfg =
                                TranslationUtils.pickPreferredOrFromList(
                                        translations,
                                        locale,
                                        localizationProperties.getLanguages(),
                                        RestaurantGroupTranslation::getLanguageCode
                                );
                        cfg.ifPresent(first -> translationDTOs.add(RestaurantGroupTranslationDTO.builder()
                                .languageCode(first.getLanguageCode())
                                .name(first.getName())
                                .build()));
                    }

                    String createdByName = (group.getCreatedBy() != null)
                            ? formatUserName(group.getCreatedBy())
                            : null;

                    String updatedByName = (group.getUpdatedBy() != null)
                            ? formatUserName(group.getUpdatedBy())
                            : null;

                    // Fetch restaurants for this group
                    List<Restaurant> restaurants = restaurantRepository.findByRestaurantGroupIdAndIsDeletedFalse(group.getId());
                    List<RestaurantResponse> restaurantResponses = new ArrayList<>();
                    
                    for (Restaurant restaurant : restaurants) {
                        // Fetch translations for each restaurant
                        List<RestaurantTranslation> restaurantTranslations = restaurantTranslationRepository.findAllByRestaurantIdWithLanguage(restaurant.getId());
                        List<RestaurantTranslationDto> restaurantTranslationDTOs = new ArrayList<>();
                        
                        for (RestaurantTranslation rt : restaurantTranslations) {
                            if (rt.getLanguageCode() != null) {
                                RestaurantTranslationDto translationDTO = RestaurantTranslationDto.builder()
                                    .languageCode(rt.getLanguageCode())
                                    .name(rt.getName())
                                    .build();
                                restaurantTranslationDTOs.add(translationDTO);
                            }
                        }

                        // Fetch operating hours for this restaurant
                        List<com.gulfnet.shared_library.model.response.dto.OperatingHourDto> operatingHours =
                            restaurantOperatingHoursRepository.findByRestaurant_Id(restaurant.getId())
                                .stream()
                                .map(restaurantOperatingHoursMapper::toOperatingHoursDto)
                                .collect(java.util.stream.Collectors.toList());

                        // Count active discounts and promotions for this restaurant from direct restaurant mappings
                        Long activeDiscountCount = restaurantDiscountMappingRepository.countActiveDiscountsByRestaurantId(restaurant.getId());
                        Long activePromotionCount = restaurantPromotionMappingRepository.countActivePromotionsByRestaurantId(restaurant.getId());

                        RestaurantResponse restaurantResponse = RestaurantResponse.builder()
                            .uuid(restaurant.getId().toString())
                            .restaurantCode(restaurant.getRestaurantCode())
                            .city(restaurant.getCity())
                            .area(restaurant.getArea())
                            .state(restaurant.getState())
                            .address1(restaurant.getAddress1())
                            .address2(restaurant.getAddress2())
                            .latitude(restaurant.getLatitude())
                            .longitude(restaurant.getLongitude())
                            .locationPin(restaurant.getLocationPin())
                            .tableQrCodeType(restaurant.getTableQrCodeType())
                            .paymentQrUrl(awsService.getFullUrl(restaurant.getPaymentQrUrl()))
                            .status(restaurant.getStatus())
                            .createdAt(restaurant.getCreatedAt() != null ? restaurant.getCreatedAt().toLocalDateTime() : null)
                            .updatedAt(restaurant.getUpdatedAt() != null ? restaurant.getUpdatedAt().toLocalDateTime() : null)
                            .createdBy(restaurant.getCreatedBy() != null ? 
                                formatUserName(restaurant.getCreatedBy()) : null)
                            .updatedBy(restaurant.getUpdatedBy() != null ? 
                                formatUserName(restaurant.getUpdatedBy()) : null)
                            .restaurantGroupId(restaurant.getRestaurantGroup() != null ? restaurant.getRestaurantGroup().getId().toString() : null)
                            .logoUrl(awsService.getFullUrl(restaurant.getLogoUrl()))
                            .translations(restaurantTranslationDTOs)
                            .isDeleted(restaurant.getIsDeleted())
                            .restaurantGroupName(restaurant.getRestaurantGroupName())
                            .employeeCount(userRepository.countByRestaurantId(restaurant.getId()))
                            .operatingHours(operatingHours)
                            .activeDiscountCount(activeDiscountCount != null ? activeDiscountCount : 0L)
                            .activePromotionCount(activePromotionCount != null ? activePromotionCount : 0L)
                            .salesAlertThreshold(restaurant.getSalesAlertThreshold())
                            .refundAlertPercentage(restaurant.getRefundAlertPercentage())
                            .cancellationAlertPercentage(restaurant.getCancellationAlertPercentage())
                            .alertsEnabled(restaurant.getAlertsEnabled())
                            .phoneNumber(restaurant.getPhoneNumber())
                            .build();
                        
                        restaurantResponses.add(restaurantResponse);
                    }

                    // Calculate total active discount and promotion counts for the restaurant group
                    Long totalActiveDiscountCount = restaurantResponses.stream()
                        .mapToLong(r -> r.getActiveDiscountCount() != null ? r.getActiveDiscountCount() : 0L)
                        .sum();
                    Long totalActivePromotionCount = restaurantResponses.stream()
                        .mapToLong(r -> r.getActivePromotionCount() != null ? r.getActivePromotionCount() : 0L)
                        .sum();

                    return RestaurantGroupResponse.builder()
                        .uuid(group.getId().toString())
                        .restaurantGroupCode(group.getRestaurantGroupCode())
                        .status(group.getStatus() != null ? group.getStatus().toString() : null)
                        .createdAt(group.getCreatedAt() != null ? group.getCreatedAt().toLocalDateTime() : null)
                        .createdBy(createdByName)
                        .updatedAt(group.getUpdatedAt() != null ? group.getUpdatedAt().toLocalDateTime() : null)
                        .updatedBy(updatedByName)
                        .translations(translationDTOs)
                        .isPublished(!restaurantGroupMenuMappingRepository.findById_RestaurantGroupId(group.getId()).isEmpty())
                        .restaurantCount((long) restaurantResponses.size())
                        .restaurants(restaurantResponses)
                        .activeDiscountCount(totalActiveDiscountCount)
                        .activePromotionCount(totalActivePromotionCount)
                        .build();
                })
                .collect(Collectors.toList()); 
        
        // ✅ Sorting (in-memory for complex fields)
        if ("name".equalsIgnoreCase(sortBy)) {
            // Set the locale in context for LocaleSortUtil
            LocaleContextHolder.setLocale(Locale.forLanguageTag(locale));
            LocaleSortUtil.sortName(groupResponses, sortBy, direction);
        } else if ("createdAt".equalsIgnoreCase(sortBy)) {
            Comparator<RestaurantGroupResponse> comp = Comparator.comparing(RestaurantGroupResponse::getCreatedAt,
                    Comparator.nullsLast(Comparator.naturalOrder()));
            if (direction == Sort.Direction.DESC) comp = comp.reversed();
            groupResponses.sort(comp);
        } else if ("updatedAt".equalsIgnoreCase(sortBy)) {
            Comparator<RestaurantGroupResponse> comp = Comparator.comparing(RestaurantGroupResponse::getUpdatedAt,
                    Comparator.nullsLast(Comparator.naturalOrder()));
            if (direction == Sort.Direction.DESC) comp = comp.reversed();
            groupResponses.sort(comp);
        } else if ("restaurantGroupCode".equalsIgnoreCase(sortBy)) {
            Comparator<RestaurantGroupResponse> comp = Comparator.comparing(RestaurantGroupResponse::getRestaurantGroupCode,
                    Comparator.nullsLast(Comparator.naturalOrder()));
            if (direction == Sort.Direction.DESC) comp = comp.reversed();
            groupResponses.sort(comp);
        }
        // Add more sorting fields as needed
        
        // ✅ Pagination
        int fromIndex = Math.min(pageNumber * pageSize, groupResponses.size());
        int toIndex = Math.min(fromIndex + pageSize, groupResponses.size());
        List<RestaurantGroupResponse> paginatedResponses = groupResponses.subList(fromIndex, toIndex);
        
        // ✅ Metadata
        PaginationMetaData metaData = PaginationMetaData.builder()
                .page(pageNumber + 1)
                .size(pageSize)
                .totalPages((int) Math.ceil((double) groupResponses.size() / pageSize))
                .totalRecords((long) groupResponses.size())
                .build();
        
        // ✅ Final response
        RestaurantGroupListResponse listResponse = RestaurantGroupListResponse.builder()
                .restaurantGroups(paginatedResponses)
                .count((long) paginatedResponses.size())
                .total((long) groupResponses.size())
                .metaData(metaData)
                .build();
        
        return ResponseDto.<RestaurantGroupListResponse>builder()
                .message(messageSource.getMessage("restaurantgroup.list.success", null, userLocale))
                .data(listResponse)
                .build();
    }

    /**
     * Lists restaurant groups in a lightweight form, with optional inclusion of deleted groups.
     * <p>
     * Uses caching keyed by the full filter/paging/sort inputs. Translation selection is locale-aware with fallback to
     * configured languages.
     *
     * @param page 1-based page number (optional)
     * @param size page size (optional)
     * @param status optional status filter
     * @param search optional search term applied to group code and/or translations
     * @param sortBy sort field (optional; locale-aware sorting is applied in service)
     * @param direction sort direction (optional)
     * @param locale locale tag used for translation selection and messages (required)
     * @param isDeleted if true, returns deleted groups; otherwise returns non-deleted groups
     * @return paged list response of restaurant groups
     */
    @Override
    @Transactional(readOnly = true)
    @Cacheable(value = "restaurantGroupsLite", key = "#page + '_' + #size + '_' + (#status != null ? #status : 'null') + '_' + (#search != null ? #search.toLowerCase() : 'null') + '_' + (#sortBy != null ? #sortBy : 'null') + '_' + (#direction != null ? #direction : 'null') + '_' + #locale + '_' + (#isDeleted != null ? #isDeleted : 'null')")
    public ResponseDto<RestaurantGroupListResponse> getRestaurantGroupsLite(
            Integer page,
            Integer size,
            String status,
            String search,
            String sortBy,
            Sort.Direction direction,
            String locale,
            Boolean isDeleted) {

        Locale userLocale = Locale.forLanguageTag(locale);

        int pageNumber = (page != null ? page : 1) - 1;
        if (pageNumber < 0) pageNumber = 0;
        int pageSize = (size != null && size > 0) ? size : Integer.MAX_VALUE;

        final boolean hasSearch = search != null && !search.trim().isEmpty();
        final boolean hasStatus = status != null && !status.trim().isEmpty();
        EntityStatus entityStatusTemp = null;
        if (hasStatus) {
            try {
                entityStatusTemp = EntityStatus.valueOf(status.toUpperCase());
            } catch (IllegalArgumentException ignored) {
                // If status is invalid, keep as null so it won't be used in the filter
            }
        }
        final EntityStatus entityStatus = entityStatusTemp;

        Pageable pageable = PageRequest.of(0, Integer.MAX_VALUE); // filtering only; sort in service via LocaleSortUtil

        // Build specification with isDeleted filter
        final EntityStatus finalEntityStatus = entityStatus;
        Specification<RestaurantGroup> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            
            // Handle isDeleted filter: if isDeleted=true, show deleted; otherwise show non-deleted (default)
            if (isDeleted != null && isDeleted) {
                predicates.add(cb.equal(root.get(FIELD_IS_DELETED), true));
            } else {
                predicates.add(cb.equal(root.get(FIELD_IS_DELETED), false));
            }
            
            if (hasStatus && finalEntityStatus != null) {
                predicates.add(cb.equal(root.get("status"), finalEntityStatus));
            }
            
            if (hasSearch) {
                String searchPattern = "%" + search.trim().toLowerCase() + "%";
                Predicate codePredicate = cb.like(cb.lower(root.get("restaurantGroupCode")), searchPattern);
                
                Subquery<Long> translationSubquery = query.subquery(Long.class);
                Root<RestaurantGroupTranslation> translationRoot = translationSubquery.from(RestaurantGroupTranslation.class);
                translationSubquery.select(cb.literal(1L));
                translationSubquery.where(
                    cb.and(
                        cb.equal(translationRoot.get("restaurantGroup"), root),
                        cb.like(cb.lower(translationRoot.get("name")), searchPattern)
                    )
                );
                Predicate translationPredicate = cb.exists(translationSubquery);
                
                predicates.add(cb.or(codePredicate, translationPredicate));
            }
            
            return cb.and(predicates.toArray(new Predicate[0]));
        };

        List<RestaurantGroup> groups = groupRepository.findAll(spec);

        List<RestaurantGroupResponse> responses = groups.stream()
            .map(group -> {
                List<RestaurantGroupTranslation> translations = translationRepository
                    .findAllByRestaurantGroupIdWithLanguage(group.getId());

                RestaurantGroupTranslation translation = translations.stream()
                    .filter(t -> t.getLanguageCode() != null && t.getLanguageCode().equals(locale))
                    .findFirst().orElse(null);

                List<RestaurantGroupTranslationDTO> translationDTOs = new ArrayList<>();
                if (translation != null) {
                    translationDTOs.add(RestaurantGroupTranslationDTO.builder()
                        .languageCode(translation.getLanguageCode())
                        .name(translation.getName())
                        .build());
                } else if (!translations.isEmpty()) {
                    // Fallback to ordered languages from config
                    java.util.Optional<RestaurantGroupTranslation> fallback =
                            TranslationUtils.pickPreferredOrFromList(
                                    translations,
                                    locale,
                                    localizationProperties.getLanguages(),
                                    RestaurantGroupTranslation::getLanguageCode
                            );
                    fallback.ifPresent(gt -> translationDTOs.add(RestaurantGroupTranslationDTO.builder()
                            .languageCode(gt.getLanguageCode())
                            .name(gt.getName())
                            .build()));
                }

                long restaurantCount = restaurantRepository.countByRestaurantGroupIdAndIsDeletedFalse(group.getId());

                String createdByName = (group.getCreatedBy() != null) ? formatUserName(group.getCreatedBy()) : null;
                String updatedByName = (group.getUpdatedBy() != null) ? formatUserName(group.getUpdatedBy()) : null;

                return RestaurantGroupResponse.builder()
                    .uuid(group.getId().toString())
                    .restaurantGroupCode(group.getRestaurantGroupCode())
                    .status(group.getStatus() != null ? group.getStatus().toString() : null)
                    .createdAt(group.getCreatedAt() != null ? group.getCreatedAt().toLocalDateTime() : null)
                    .createdBy(createdByName)
                    .updatedAt(group.getUpdatedAt() != null ? group.getUpdatedAt().toLocalDateTime() : null)
                    .updatedBy(updatedByName)
                    .translations(translationDTOs)
                    .isPublished(!restaurantGroupMenuMappingRepository.findById_RestaurantGroupId(group.getId()).isEmpty())
                    .restaurantCount(restaurantCount)
                    .build();
            })
            .collect(Collectors.toList());

        // Locale-aware sorting on the final response list (name and createdAt supported by util)
        LocaleContextHolder.setLocale(Locale.forLanguageTag(locale));
        LocaleSortUtil.sortName(responses, sortBy, direction);

        int fromIndex = Math.min(pageNumber * pageSize, responses.size());
        int toIndex = Math.min(fromIndex + pageSize, responses.size());
        // Convert SubList to ArrayList for Redis serialization compatibility
        List<RestaurantGroupResponse> pageContent = new ArrayList<>(responses.subList(fromIndex, toIndex));

        PaginationMetaData metaData = PaginationMetaData.builder()
                .page(pageNumber + 1)
                .size(pageSize)
                .totalPages((int) Math.ceil((double) responses.size() / pageSize))
                .totalRecords((long) responses.size())
                .build();

        RestaurantGroupListResponse listResponse = RestaurantGroupListResponse.builder()
                .restaurantGroups(pageContent)
                .count((long) pageContent.size())
                .total((long) responses.size())
                .metaData(metaData)
                .build();

        return ResponseDto.<RestaurantGroupListResponse>builder()
                .message(messageSource.getMessage("restaurantgroup.list.success", null, userLocale))
                .data(listResponse)
                .build();
    }

    /**
     * Soft-deletes a restaurant group.
     * <p>
     * Constraints:
     * - group must exist and not already be deleted
     * - group cannot be deleted while it still has linked (non-deleted) restaurants
     * <p>
     * Side effects:
     * - sets {@code isDeleted=true} and updates audit fields
     * - writes an audit trail entry (best-effort)
     * - evicts caches that expose restaurant groups/restaurants
     *
     * @param id restaurant group id (required)
     * @param userId actor user id (UUID string) (required)
     * @return response indicating success
     * @throws ResponseStatusException on validation failure or missing user/group
     */
    @Override
    @Transactional
    @CacheEvict(value = {"restaurantGroupsLite", "restaurants"}, allEntries = true)
    public ResponseDto<RestaurantGroupDTO<RestaurantGroupResponse>> deleteGroup(UUID id, String userId) {
        Locale userLocale = LocaleContextHolder.getLocale();
        
        RestaurantGroup group = groupRepository.findById(id)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, 
                messageSource.getMessage(MSG_RESTAURANT_GROUP_NOT_FOUND, null, userLocale)));
        
        // Check if group is already deleted
        if (group.getIsDeleted() != null && group.getIsDeleted()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, 
                messageSource.getMessage("restaurantgroup.delete.error.alreadydeleted", null, userLocale));
        }
        
        // Check if there are any active restaurants linked to this group
        List<Restaurant> linkedRestaurants = restaurantRepository.findByRestaurantGroupIdAndIsDeletedFalse(id);
        if (!linkedRestaurants.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, 
                messageSource.getMessage("restaurantgroup.delete.error.has.restaurants", new Object[]{linkedRestaurants.size()}, userLocale));
        }
        
        // Find user for updatedBy
        User user = userRepository.findById(UUID.fromString(userId))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, 
                    messageSource.getMessage(MSG_USER_NOT_FOUND, new Object[]{userId}, userLocale)));
        
        // Soft delete - set isDeleted flag to true
        group.setIsDeleted(true);
        group.setUpdatedAt(OffsetDateTime.now(ZoneOffset.UTC));
        group.setUpdatedBy(user); // Store user object
        
        groupRepository.save(group);
        
        // Create audit trail for restaurant group deletion
        try {
            auditTrailService.createAuditTrail(
                    user,
                    ActionType.RESTAURANT_GROUP_DELETE,
                    null, // Restaurant groups are not restaurant-specific
                    null, // status - will default to NA for non-request actions
                    null, // ipAddress - not available in this context
                    null, // userAgent - not available in this context
                    group.getId(),
                    ENTITY_TYPE_RESTAURANT_GROUP,
                    "Restaurant Group deleted: " + group.getRestaurantGroupCode()
            );
        } catch (Exception e) {
            // Don't break restaurant group deletion flow if audit trail fails
        }
        
        // Build response
        List<RestaurantGroupTranslation> translations = translationRepository.findAllByRestaurantGroupIdWithLanguage(id);
        List<RestaurantGroupTranslationDTO> translationDTOs = new ArrayList<>();
        for (RestaurantGroupTranslation t : translations) {
            if (t.getLanguageCode() != null) {
                translationDTOs.add(RestaurantGroupTranslationDTO.builder()
                    .languageCode(t.getLanguageCode())
                    .name(t.getName())
                    .build());
            }
        }
    
        long restaurantCount = 0; // No active restaurants since group is being deleted
    
        // Build RestaurantGroupResponse inline
        String createdByName = null;
        if (group.getCreatedBy() != null) {
            User createdByUser = group.getCreatedBy();
            if (createdByUser != null) {
                createdByName = (createdByUser.getFirstName() != null ? createdByUser.getFirstName() : "") + 
                               " " + (createdByUser.getLastName() != null ? createdByUser.getLastName() : "");
                createdByName = createdByName.trim();
            }
        }
        
        String updatedByName = null;
        if (group.getUpdatedBy() != null) {
            User updatedByUser = group.getUpdatedBy();
            if (updatedByUser != null) {
                updatedByName = (updatedByUser.getFirstName() != null ? updatedByUser.getFirstName() : "") + 
                               " " + (updatedByUser.getLastName() != null ? updatedByUser.getLastName() : "");
                updatedByName = updatedByName.trim();
            }
        }
        
        RestaurantGroupResponse response = RestaurantGroupResponse.builder()
            .uuid(group.getId().toString())
            .restaurantGroupCode(group.getRestaurantGroupCode())
            .status(group.getStatus() != null ? group.getStatus().name() : null)
            .createdAt(group.getCreatedAt() != null ? group.getCreatedAt().toLocalDateTime() : null)
            .createdBy(createdByName)
            .updatedAt(group.getUpdatedAt() != null ? group.getUpdatedAt().toLocalDateTime() : null)
            .updatedBy(updatedByName)
            .translations(translationDTOs)
            .isPublished(!restaurantGroupMenuMappingRepository.findById_RestaurantGroupId(group.getId()).isEmpty())
            .restaurantCount(restaurantCount)
            .build();
    
        return ResponseDto.<RestaurantGroupDTO<RestaurantGroupResponse>>builder()
            .message(messageSource.getMessage("restaurantgroup.delete.success", null, LocaleContextHolder.getLocale()))
            .build();
    }

    /**
     * Lists restaurants in a group that are mapped to a given menu (group-menu assignment view).
     * <p>
     * Validates that the menu and group exist and that the group is associated with the menu, then returns the
     * eligible restaurants with search/sort/pagination applied.
     *
     * @param groupId restaurant group id (required)
     * @param menuId menu id (required)
     * @param page page number (0-based in this method's implementation)
     * @param size page size
     * @param status optional status filter (currently applied as ACTIVE-only downstream)
     * @param search optional search term applied to restaurant translation names
     * @param sortBy sort field ("name" or "createdAt")
     * @param direction sort direction
     * @return list response of restaurants mapped to the menu within the group
     * @throws ResponseStatusException on missing menu/group/mapping or invalid request
     */
    @Override
    @Transactional(readOnly = true)
    public ResponseDto<RestaurantMenuListResponse> getRestaurantGroupsByGroupIdAndMenuId(
            UUID groupId, UUID menuId, Integer page, Integer size, String status, 
            String search, String sortBy, Sort.Direction direction) {
        
        Locale userLocale = LocaleContextHolder.getLocale();
        
        // Validate menuId exists
        Menu menu = menuRepository.findById(menuId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, 
                messageSource.getMessage("menu.not.found", new Object[]{menuId}, userLocale)));
        
        // Validate groupId exists
        RestaurantGroup group = groupRepository.findById(groupId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, 
                messageSource.getMessage(MSG_RESTAURANT_GROUP_NOT_FOUND, null, userLocale)));
        
        // Check if restaurant group is associated with the menu
        RestaurantGroupMenuMapping groupMenuMapping = restaurantGroupMenuMappingRepository
            .findByMenuIdAndRestaurantGroupId(menuId, groupId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, 
                messageSource.getMessage("restaurantgroup.menu.mapping.not.found", new Object[]{groupId, menuId}, userLocale)));
        
        // Get restaurants that are mapped to this menu and belong to this group
        List<RestaurantMenuMapping> restaurantMenuMappings = restaurantMenuMappingRepository
            .findByMenuIdAndRestaurantRestaurantGroupId(menuId, groupId);
        
        // Extract restaurant IDs and get restaurant entities
        List<UUID> restaurantIds = restaurantMenuMappings.stream()
            .map(mapping -> mapping.getRestaurant().getId())
            .collect(Collectors.toList());
        
        List<Restaurant> restaurants = restaurantRepository.findAllById(restaurantIds);
        
        // Filter to only include active restaurants that are not deleted
        restaurants = restaurants.stream()
            .filter(restaurant -> (restaurant.getIsDeleted() == null || !restaurant.getIsDeleted()) 
                && restaurant.getStatus() == EntityStatus.ACTIVE)
            .collect(Collectors.toList());
        
        // Apply search filter (only search by restaurant name through translations)
        if (search != null && !search.trim().isEmpty()) {
            String searchTerm = search.trim().toLowerCase();
            restaurants = restaurants.stream()
                .filter(restaurant -> {
                    // Get translations for this restaurant
                    List<RestaurantTranslation> translations = restaurantTranslationRepository
                        .findAllByRestaurantIdWithLanguage(restaurant.getId());
                    
                    // Check if any translation name contains the search term
                    return translations.stream()
                        .anyMatch(translation -> translation.getName() != null && 
                            translation.getName().toLowerCase().contains(searchTerm));
                })
                .collect(Collectors.toList());
        }
        
        // Apply sorting (only by name or creation date)
        if (sortBy != null && !sortBy.trim().isEmpty()) {
            String sortField = sortBy.trim().toLowerCase();
            Comparator<Restaurant> comparator = switch (sortField) {
                case "name" -> Comparator.comparing(r -> {
                    // Get the first translation name for sorting
                    List<RestaurantTranslation> translations = restaurantTranslationRepository
                        .findAllByRestaurantIdWithLanguage(r.getId());
                    return translations.stream()
                        .findFirst()
                        .map(RestaurantTranslation::getName)
                        .orElse("");
                });
                case "createdat" -> Comparator.comparing(Restaurant::getCreatedAt);
                default -> Comparator.comparing(Restaurant::getCreatedAt);
            };
            
            if (direction == Sort.Direction.DESC) {
                comparator = comparator.reversed();
            }
            
            restaurants.sort(comparator);
        }
        
        // Apply pagination
        int totalSize = restaurants.size();
        int pageSize = size != null ? size : 10;
        int currentPage = page != null ? page : 0;
        int startIndex = currentPage * pageSize;
        int endIndex = Math.min(startIndex + pageSize, totalSize);
        
        List<Restaurant> paginatedRestaurants = restaurants.subList(startIndex, endIndex);
        
        // Get current locale for localization
        String currentLocale = LocaleContextHolder.getLocale().getLanguage();
        
        // Build restaurant responses
        List<RestaurantMenuResponse> restaurantResponses = paginatedRestaurants.stream()
            .map(restaurant -> {
                // Get translations
                List<RestaurantTranslation> translations = restaurantTranslationRepository
                    .findAllByRestaurantIdWithLanguage(restaurant.getId());
                
                // Find the translation for the current locale with proper fallback
                String localizedName = "";
                if (!translations.isEmpty()) {
                    RestaurantTranslation exactMatch = translations.stream()
                            .filter(t -> t.getLanguageCode() != null && t.getLanguageCode().equalsIgnoreCase(currentLocale))
                            .findFirst()
                            .orElse(null);
                    
                    if (exactMatch != null) {
                        localizedName = exactMatch.getName();
                    } else {
                        // Fallback using TranslationUtils
                        java.util.Optional<RestaurantTranslation> fallback =
                                TranslationUtils.pickPreferredOrFromList(
                                        translations,
                                        currentLocale,
                                        localizationProperties.getLanguages(),
                                        RestaurantTranslation::getLanguageCode
                                );
                        localizedName = fallback.map(RestaurantTranslation::getName)
                                .orElseGet(() -> translations.stream()
                                        .filter(t -> t.getName() != null && !t.getName().isEmpty())
                                        .findFirst()
                                        .map(RestaurantTranslation::getName)
                                        .orElse(""));
                    }
                }
                
                // Generate signed URL for logo
                String signedLogoUrl = null;
                if (restaurant.getLogoUrl() != null && !restaurant.getLogoUrl().isEmpty()) {
                    try {
                        signedLogoUrl = awsService.getPreSignedUrl(restaurant.getLogoUrl());
                    } catch (Exception e) {
                        // Log error but continue without signed URL
                    }
                }
                
                return RestaurantMenuResponse.builder()
                    .uuid(restaurant.getId().toString())
                    .logoUrl(signedLogoUrl)

                    .status(restaurant.getStatus())
                    .name(localizedName)
                    .build();
            })
            .collect(Collectors.toList());
        
        // Build pagination metadata
        PaginationMetaData paginationMetaData = PaginationMetaData.builder()
            .page(currentPage)
            .size(pageSize)
            .totalRecords((long) totalSize)
            .totalPages((int) Math.ceil((double) totalSize / pageSize))
            .build();
        
        // Build response
        RestaurantMenuListResponse response = RestaurantMenuListResponse.builder()
            .restaurants(restaurantResponses)
            .count((long) restaurantResponses.size())
            .total((long) totalSize)
            .metaData(paginationMetaData)
            .build();
        
        return ResponseDto.<RestaurantMenuListResponse>builder()
            .data(response)
            .message(messageSource.getMessage("restaurantgroup.menu.restaurants.retrieved.success", null, userLocale))
            .build();
    }

    /**
     * Updates restaurant-menu assignments for a restaurant group and a published menu.
     * <p>
     * Supports wildcard restaurant selection ({@code "*"}) to target all restaurants in the group.
     * Preserves existing SCHEDULED/LIVE mappings where possible and replaces others, then ensures item-availability
     * records exist for all menu items for all affected restaurants.
     *
     * @param request assignment request payload (required)
     * @param userId actor user id (UUID string) (required)
     * @return void response indicating success
     * @throws ResponseStatusException on validation failure (missing user/group/menu, invalid restaurants, menu not published)
     */
    @Override
    @Transactional
    @CacheEvict(value = {"restaurants", "restaurantGroupsLite"}, allEntries = true)
    public ResponseDto<Void> updateRestaurantMenuAssignments(AssignMenuToRestaurantGroupRequest request, String userId) {
        Locale userLocale = LocaleContextHolder.getLocale();

        // Validate user exists
        User user = userRepository.findById(UUID.fromString(userId))
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, 
                messageSource.getMessage(MSG_USER_NOT_FOUND, new Object[]{userId}, userLocale)));
        
        // Validate groupId exists
        RestaurantGroup group = groupRepository.findById(request.getRestaurantGroupId())
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, 
                messageSource.getMessage(MSG_RESTAURANT_GROUP_NOT_FOUND, null, userLocale)));
        
        // Validate menuId exists and is published
        Menu menu = menuRepository.findById(request.getMenuId())
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, 
                messageSource.getMessage("menu.not.found", new Object[]{request.getMenuId()}, userLocale)));
        
        if (menu.getStatus() != MenuStatus.PUBLISHED) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, 
                messageSource.getMessage("menu.not.published", new Object[]{request.getMenuId()}, userLocale));
        }
        
        // Validate restaurantIds
        if (request.getRestaurantIds() == null || request.getRestaurantIds().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, 
                messageSource.getMessage("restaurant.ids.required", null, userLocale));
        }
        
        List<UUID> restaurantIds = new ArrayList<>();
        
        // Handle wildcard case
        if (request.getRestaurantIds().contains("*")) {
            // Get all restaurants in the group
            List<Restaurant> groupRestaurants = restaurantRepository.findByRestaurantGroupIdAndIsDeletedFalse(request.getRestaurantGroupId());
            restaurantIds = groupRestaurants.stream()
                .map(Restaurant::getId)
                .collect(Collectors.toList());
        } else {
            // Parse UUIDs from strings
            try {
                restaurantIds = request.getRestaurantIds().stream()
                    .map(UUID::fromString)
                    .collect(Collectors.toList());
            } catch (IllegalArgumentException e) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, 
                    messageSource.getMessage(MSG_INVALID_RESTAURANT_ID_FORMAT, null, userLocale));
            }
        }
        
        // Validate all restaurants belong to the specified group and are active
        List<Restaurant> restaurants = restaurantRepository.findAllById(restaurantIds);
        if (restaurants.size() != restaurantIds.size()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, 
                messageSource.getMessage(MSG_SOME_RESTAURANTS_NOT_FOUND, null, userLocale));
        }
        
        for (Restaurant restaurant : restaurants) {
            if (!restaurant.getRestaurantGroup().getId().equals(request.getRestaurantGroupId())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, 
                    messageSource.getMessage("restaurant.not.in.group", new Object[]{restaurant.getId(), request.getRestaurantGroupId()}, userLocale));
            }
            
            if (restaurant.getStatus() != EntityStatus.ACTIVE) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, 
                    messageSource.getMessage("restaurant.not.active", new Object[]{restaurant.getId()}, userLocale));
            }
        }
        
        // Ensure restaurant group menu mapping exists
        RestaurantGroupMenuMapping groupMenuMapping = restaurantGroupMenuMappingRepository
            .findByMenuIdAndRestaurantGroupId(request.getMenuId(), request.getRestaurantGroupId())
            .orElseGet(() -> {
                RestaurantGroupMenuMapping newMapping = RestaurantGroupMenuMapping.builder()
                    .id(new RestaurantGroupMenuId(request.getRestaurantGroupId(), request.getMenuId()))
                    .restaurantGroup(group)
                    .menu(menu)
                    .build();
                return restaurantGroupMenuMappingRepository.save(newMapping);
            });
        
        // Get existing restaurant menu mappings for this group and menu
        List<RestaurantMenuMapping> existingMappings = restaurantMenuMappingRepository
            .findByMenuIdAndRestaurantRestaurantGroupId(request.getMenuId(), request.getRestaurantGroupId());
        
        // Create a map of existing mappings by restaurant ID for quick lookup
        Map<UUID, RestaurantMenuMapping> existingMappingsByRestaurantId = existingMappings.stream()
            .collect(Collectors.toMap(
                mapping -> mapping.getRestaurant().getId(),
                mapping -> mapping
            ));
        
        // Separate restaurants that need new assignments from those that should keep existing ones
        List<UUID> restaurantsNeedingNewAssignments = new ArrayList<>();
        List<RestaurantMenuMapping> mappingsToKeep = new ArrayList<>();
        
        for (UUID restaurantId : restaurantIds) {
            RestaurantMenuMapping existingMapping = existingMappingsByRestaurantId.get(restaurantId);
            
            if (existingMapping != null && 
                (existingMapping.getStatus() == RestaurantMenuMappingStatus.SCHEDULED || 
                 existingMapping.getStatus() == RestaurantMenuMappingStatus.LIVE)) {
                // Keep existing SCHEDULED or LIVE assignments
                mappingsToKeep.add(existingMapping);
            } else {
                // Restaurant needs new assignment (either no existing mapping or has UNSCHEDULED status)
                restaurantsNeedingNewAssignments.add(restaurantId);
            }
        }
        
        // Remove only the mappings that need to be replaced (UNSCHEDULED or for restaurants not in the new list)
        List<RestaurantMenuMapping> mappingsToDelete = existingMappings.stream()
            .filter(mapping -> !mappingsToKeep.contains(mapping))
            .collect(Collectors.toList());
        
        if (!mappingsToDelete.isEmpty()) {
            restaurantMenuMappingRepository.deleteAll(mappingsToDelete);
        }
        
        // Create new restaurant menu mappings only for restaurants that need them
        if (!restaurantsNeedingNewAssignments.isEmpty()) {
            List<RestaurantMenuMapping> newMappings = restaurantsNeedingNewAssignments.stream()
                .map(restaurantId -> {
                    Restaurant restaurant = restaurants.stream()
                        .filter(r -> r.getId().equals(restaurantId))
                        .findFirst()
                        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, 
                            messageSource.getMessage("restaurant.not.found", new Object[]{restaurantId}, userLocale)));
                    
                    return RestaurantMenuMapping.builder()
                        .id(new RestaurantMenuId(restaurantId, request.getMenuId()))
                        .restaurant(restaurant)
                        .menu(menu)
                        .status(RestaurantMenuMappingStatus.UNSCHEDULED)
                        .build();
                })
                .collect(Collectors.toList());
            
            restaurantMenuMappingRepository.saveAll(newMappings);
        }
        
        // Create/update availability records for all restaurants (both new assignments and existing ones)
        // This ensures that:
        // 1. New restaurant-menu assignments get availability records for all menu items
        // 2. Existing restaurants get availability records for any new items added to the menu
        // 3. When a menu is reassigned after unassignment, availability records are recreated
        UUID userIdUuid = UUID.fromString(userId);
        // Preload category item mappings for this menu once
        List<com.gulfnet.shared_library.entity.CategoryItemMapping> menuCategoryItemMappings =
                categoryItemMappingRepository.findByMenuCategoryMappingMenuId(request.getMenuId());
        
        // Create availability records for restaurants with new assignments
        for (UUID restaurantId : restaurantsNeedingNewAssignments) {
            createAvailabilityForRestaurantMenuMapping(restaurantId, menuCategoryItemMappings, userIdUuid);
        }
        
        // Create availability records for restaurants that kept existing mappings (handles new items added to menu)
        for (RestaurantMenuMapping mapping : mappingsToKeep) {
            createAvailabilityForRestaurantMenuMapping(
                    mapping.getRestaurant().getId(), menuCategoryItemMappings, userIdUuid);
        }
        
        return ResponseDto.<Void>builder()
            .message(messageSource.getMessage("restaurant.menu.assignments.updated.success", null, userLocale))
            .build();
    }
    
    /**
     * Assigns one or more restaurants to a restaurant group.
     * <p>
     * Supports wildcard assignment ({@code "*"}) to assign all currently unassigned restaurants.
     *
     * @param request request containing group id and restaurant ids (required)
     * @param userId actor user id (UUID string) (required)
     * @return void response indicating success
     * @throws ResponseStatusException on validation failure (missing user/group/restaurants, deleted entities, invalid ids)
     */
    @Override
    @Transactional
    @CacheEvict(value = {"restaurantGroupsLite", "restaurants"}, allEntries = true)
    public ResponseDto<Void> assignRestaurantsToGroup(AssignRestaurantsToGroupRequest request, String userId) {
        Locale userLocale = LocaleContextHolder.getLocale();
        
        // Validate user exists
        User user = userRepository.findById(UUID.fromString(userId))
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, 
                messageSource.getMessage(MSG_USER_NOT_FOUND, new Object[]{userId}, userLocale)));
        
        // Validate restaurant group exists and is not deleted
        RestaurantGroup group = groupRepository.findById(request.getRestaurantGroupId())
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, 
                messageSource.getMessage(MSG_RESTAURANT_GROUP_NOT_FOUND, null, userLocale)));
        
        if (Boolean.TRUE.equals(group.getIsDeleted())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, 
                messageSource.getMessage("restaurantgroup.get.error.deleted", null, userLocale));
        }
        
        List<Restaurant> restaurants;
        
        // Check if wildcard assignment is requested
        if (request.getRestaurantIds().size() == 1 && "*".equals(request.getRestaurantIds().get(0))) {
            // Get all unassigned restaurants
            restaurants = restaurantRepository.findByRestaurantGroupIsNullAndIsDeletedFalse();
            
            if (restaurants.isEmpty()) {
                return ResponseDto.<Void>builder()
                    .message(messageSource.getMessage("no.unassigned.restaurants.found", null, userLocale))
                    .build();
            }
        } else {
            // Convert string IDs to UUIDs and validate
            List<UUID> restaurantUuids = new ArrayList<>();
            for (String restaurantIdStr : request.getRestaurantIds()) {
                try {
                    restaurantUuids.add(UUID.fromString(restaurantIdStr));
                } catch (IllegalArgumentException e) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, 
                        messageSource.getMessage(MSG_INVALID_RESTAURANT_ID_FORMAT, new Object[]{restaurantIdStr}, userLocale));
                }
            }
            
            // Validate all restaurants exist and are not deleted
            restaurants = restaurantRepository.findAllById(restaurantUuids);
            if (restaurants.size() != restaurantUuids.size()) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, 
                    messageSource.getMessage(MSG_SOME_RESTAURANTS_NOT_FOUND, null, userLocale));
            }
            
            for (Restaurant restaurant : restaurants) {
                if (Boolean.TRUE.equals(restaurant.getIsDeleted())) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, 
                        messageSource.getMessage("restaurant.deleted", new Object[]{restaurant.getId()}, userLocale));
                }
            }
        }
        
        // Assign restaurants to the group
        for (Restaurant restaurant : restaurants) {
            restaurant.setRestaurantGroup(group);
            restaurant.setUpdatedAt(OffsetDateTime.now(ZoneOffset.UTC));
            restaurant.setUpdatedBy(user);
        }
        
        restaurantRepository.saveAll(restaurants);
        
        return ResponseDto.<Void>builder()
            .message(messageSource.getMessage("restaurants.assigned.to.group.success", 
                new Object[]{restaurants.size(), group.getRestaurantGroupCode()}, userLocale))
            .build();
    }
    
    /**
     * Unassigns one or more restaurants from a restaurant group (sets {@code restaurant.restaurantGroup = null}).
     *
     * @param request request containing group id and restaurant ids to unassign (required)
     * @param userId actor user id (UUID string) (required)
     * @return void response indicating success
     * @throws ResponseStatusException on validation failure (missing user/group/restaurants, invalid ids, not assigned to group)
     */
    @Override
    @Transactional
    @CacheEvict(value = {"restaurantGroupsLite", "restaurants"}, allEntries = true)
    public ResponseDto<Void> unassignRestaurantsFromGroup(AssignRestaurantsToGroupRequest request, String userId) {
        Locale userLocale = LocaleContextHolder.getLocale();
        
        // Validate user exists
        User user = userRepository.findById(UUID.fromString(userId))
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, 
                messageSource.getMessage(MSG_USER_NOT_FOUND, new Object[]{userId}, userLocale)));
        
        // Validate restaurant group exists
        RestaurantGroup group = groupRepository.findById(request.getRestaurantGroupId())
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, 
                messageSource.getMessage(MSG_RESTAURANT_GROUP_NOT_FOUND, null, userLocale)));
        
        // Convert string IDs to UUIDs and validate
        List<UUID> restaurantUuids = new ArrayList<>();
        for (String restaurantIdStr : request.getRestaurantIds()) {
            try {
                restaurantUuids.add(UUID.fromString(restaurantIdStr));
            } catch (IllegalArgumentException e) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, 
                    messageSource.getMessage(MSG_INVALID_RESTAURANT_ID_FORMAT, new Object[]{restaurantIdStr}, userLocale));
            }
        }
        
        // Validate all restaurants exist and are currently assigned to this group
        List<Restaurant> restaurants = restaurantRepository.findAllById(restaurantUuids);
        if (restaurants.size() != restaurantUuids.size()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, 
                messageSource.getMessage(MSG_SOME_RESTAURANTS_NOT_FOUND, null, userLocale));
        }
        
        for (Restaurant restaurant : restaurants) {
            if (Boolean.TRUE.equals(restaurant.getIsDeleted())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, 
                    messageSource.getMessage("restaurant.deleted", new Object[]{restaurant.getId()}, userLocale));
            }
            
            if (restaurant.getRestaurantGroup() == null || 
                !restaurant.getRestaurantGroup().getId().equals(request.getRestaurantGroupId())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, 
                    messageSource.getMessage("restaurant.not.assigned.to.group", 
                        new Object[]{restaurant.getId(), request.getRestaurantGroupId()}, userLocale));
            }
        }
        
        // Unassign restaurants from the group
        for (Restaurant restaurant : restaurants) {
            restaurant.setRestaurantGroup(null);
            restaurant.setUpdatedAt(OffsetDateTime.now(ZoneOffset.UTC));
            restaurant.setUpdatedBy(user);
        }
        
        restaurantRepository.saveAll(restaurants);
        
        return ResponseDto.<Void>builder()
            .message(messageSource.getMessage("restaurants.unassigned.from.group.success", 
                new Object[]{restaurants.size(), group.getRestaurantGroupCode()}, userLocale))
            .build();
    }
    
    /**
     * Lists restaurants that are not assigned to any restaurant group.
     * <p>
     * Applies filtering/search/sorting/pagination and returns locale-aware translations with fallback.
     *
     * @param page 1-based page number (optional)
     * @param size page size (optional)
     * @param status optional status filter
     * @param search optional search term
     * @param sortBy sort field (optional)
     * @param direction sort direction (optional)
     * @return paged list response of unassigned restaurants
     */
    @Override
    @Transactional(readOnly = true)
    public ResponseDto<RestaurantListResponse> getUnassignedRestaurants(
            Integer page, Integer size, String status, String search, String sortBy, Sort.Direction direction) {
        
        Locale userLocale = LocaleContextHolder.getLocale();
        
        // Set default pagination values
        if (page == null && size == null) {
            page = 1;
            size = Integer.MAX_VALUE;
        } else {
            page = (page == null) ? 1 : page;
            size = (size == null) ? 10 : size;
        }
        
        int zeroBasedPage = page - 1;
        Pageable pageable = PageRequest.of(zeroBasedPage, size);
        
        // Get filtered and searched unassigned restaurants
        Page<Restaurant> restaurantsPage = getFilteredAndSearchedUnassignedRestaurants(status, search, pageable);
        List<Restaurant> restaurantList = restaurantsPage.getContent();
        
        // Get current locale for translation filtering
        String currentLocale = LocaleContextHolder.getLocale().getLanguage();
        
        // Build restaurant responses
        List<RestaurantResponse> restaurantResponses = restaurantList.stream()
            .map(restaurant -> {
                // Get translations for this restaurant
                List<RestaurantTranslation> translations = restaurantTranslationRepository
                    .findAllByRestaurantIdWithLanguage(restaurant.getId());
                
                // Filter translations with proper fallback using TranslationUtils
                List<RestaurantTranslationDto> filteredTranslations = new ArrayList<>();
                if (!translations.isEmpty()) {
                    RestaurantTranslation exactMatch = translations.stream()
                            .filter(t -> t.getLanguageCode() != null && t.getLanguageCode().equalsIgnoreCase(currentLocale))
                            .findFirst()
                            .orElse(null);
                    
                    if (exactMatch != null) {
                        // Use exact match
                        filteredTranslations.add(RestaurantTranslationDto.builder()
                                .languageCode(exactMatch.getLanguageCode())
                                .name(exactMatch.getName())
                                .build());
                    } else {
                        // Fallback using TranslationUtils
                        java.util.Optional<RestaurantTranslation> fallback =
                                TranslationUtils.pickPreferredOrFromList(
                                        translations,
                                        currentLocale,
                                        localizationProperties.getLanguages(),
                                        RestaurantTranslation::getLanguageCode
                                );
                        fallback.ifPresent(trans -> filteredTranslations.add(RestaurantTranslationDto.builder()
                                .languageCode(trans.getLanguageCode())
                                .name(trans.getName())
                                .build()));
                    }
                }
                
                // Generate signed URL for logo
                String signedLogoUrl = null;
                if (restaurant.getLogoUrl() != null && !restaurant.getLogoUrl().isEmpty()) {
                    try {
                        signedLogoUrl = awsService.getPreSignedUrl(restaurant.getLogoUrl());
                    } catch (Exception e) {
                        signedLogoUrl = restaurant.getLogoUrl(); // fallback
                    }
                }
                
                // Generate signed URL for payment QR
                String signedPaymentQrUrl = null;
                if (restaurant.getPaymentQrUrl() != null && !restaurant.getPaymentQrUrl().isEmpty()) {
                    try {
                        signedPaymentQrUrl = awsService.getPreSignedUrl(restaurant.getPaymentQrUrl());
                    } catch (Exception e) {
                        signedPaymentQrUrl = restaurant.getPaymentQrUrl(); // fallback
                    }
                }
                
                // Get operating hours
                List<OperatingHourDto> operatingHours = restaurantOperatingHoursRepository
                    .findByRestaurant_Id(restaurant.getId())
                    .stream()
                    .map(restaurantOperatingHoursMapper::toOperatingHoursDto)
                    .collect(Collectors.toList());
                
                return RestaurantResponse.builder()
                    .uuid(restaurant.getId().toString())
                    .restaurantCode(restaurant.getRestaurantCode())
                    .city(restaurant.getCity())
                    .area(restaurant.getArea())
                    .state(restaurant.getState())
                    .address1(restaurant.getAddress1())
                    .address2(restaurant.getAddress2())
                    .latitude(restaurant.getLatitude())
                    .longitude(restaurant.getLongitude())
                    .locationPin(restaurant.getLocationPin())
                    .logoUrl(signedLogoUrl)
                    .paymentQrUrl(signedPaymentQrUrl)
                    .tableQrCodeType(restaurant.getTableQrCodeType())
                    .status(restaurant.getStatus())
                    .translations(filteredTranslations)
                    .createdAt(restaurant.getCreatedAt() != null ? restaurant.getCreatedAt().toLocalDateTime() : null)
                    .createdBy(formatUserName(restaurant.getCreatedBy()))
                    .updatedAt(restaurant.getUpdatedAt() != null ? restaurant.getUpdatedAt().toLocalDateTime() : null)
                    .updatedBy(formatUserName(restaurant.getUpdatedBy()))
                    .operatingHours(operatingHours)
                    .isDeleted(restaurant.getIsDeleted())
                    .employeeCount(userRepository.countByRestaurantId(restaurant.getId()))
                    .salesAlertThreshold(restaurant.getSalesAlertThreshold())
                    .refundAlertPercentage(restaurant.getRefundAlertPercentage())
                    .cancellationAlertPercentage(restaurant.getCancellationAlertPercentage())
                    .alertsEnabled(restaurant.getAlertsEnabled())
                    .phoneNumber(restaurant.getPhoneNumber())
                    .build();
            })
            .collect(Collectors.toList());
        
        // Apply sorting if specified
        if (sortBy != null && !sortBy.trim().isEmpty()) {
            String sortField = sortBy.trim().toLowerCase();
            Comparator<RestaurantResponse> comparator = switch (sortField) {
                case "name" -> Comparator.comparing(r -> r.getTranslations().stream()
                        .findFirst()
                        .map(RestaurantTranslationDto::getName)
                        .orElse(""));
                case "createdat" -> Comparator.comparing(RestaurantResponse::getCreatedAt);
                case "restaurantcode" -> Comparator.comparing(RestaurantResponse::getRestaurantCode);
                default -> Comparator.comparing(RestaurantResponse::getCreatedAt);
            };
            
            if (direction == Sort.Direction.DESC) {
                comparator = comparator.reversed();
            }
            
            restaurantResponses.sort(comparator);
        }
        
        // Build pagination metadata
        PaginationMetaData paginationMetaData = PaginationMetaData.builder()
            .page(page)
            .size(size)
            .totalRecords(restaurantsPage.getTotalElements())
            .totalPages(restaurantsPage.getTotalPages())
            .build();
        
        // Build response
        RestaurantListResponse response = RestaurantListResponse.builder()
            .restaurants(restaurantResponses)
            .count((long) restaurantResponses.size())
            .total(restaurantsPage.getTotalElements())
            .metaData(paginationMetaData)
            .build();
        
        return ResponseDto.<RestaurantListResponse>builder()
            .message(messageSource.getMessage("unassigned.restaurants.retrieved.success", null, userLocale))
            .data(response)
            .build();
    }
    
    /**
     * Get filtered and searched unassigned restaurants
     */
    private Page<Restaurant> getFilteredAndSearchedUnassignedRestaurants(String status, String search, Pageable pageable) {
        boolean hasSearch = search != null && !search.trim().isEmpty();
        boolean hasStatus = status != null && !status.trim().isEmpty();
        
        if (hasSearch && hasStatus) {
            try {
                EntityStatus entityStatus = EntityStatus.valueOf(status.toUpperCase());
                return restaurantRepository.searchUnassignedRestaurantsByKeywordAndStatus(search.trim(), entityStatus, pageable);
            } catch (IllegalArgumentException e) {
                // If status is invalid, fall back to search only
                return restaurantRepository.searchUnassignedRestaurantsByKeyword(search.trim(), pageable);
            }
        } else if (hasSearch) {
            return restaurantRepository.searchUnassignedRestaurantsByKeyword(search.trim(), pageable);
        } else if (hasStatus) {
            try {
                EntityStatus entityStatus = EntityStatus.valueOf(status.toUpperCase());
                return restaurantRepository.findByRestaurantGroupIsNullAndIsDeletedFalseAndStatus(entityStatus, pageable);
            } catch (IllegalArgumentException e) {
                // If status is invalid, return all unassigned restaurants
                return restaurantRepository.findByRestaurantGroupIsNullAndIsDeletedFalse(pageable);
            }
        } else {
            return restaurantRepository.findByRestaurantGroupIsNullAndIsDeletedFalse(pageable);
        }
    }
    
    private String formatUserName(User user) {
        if (user == null) {
            return null;
        }
        String firstName = user.getFirstName() != null ? user.getFirstName() : "";
        String lastName = user.getLastName() != null ? user.getLastName() : "";
        return (firstName + " " + lastName).trim();
    }

    private User loadUserOrThrow(String userId, Locale userLocale) {
        return userRepository.findById(UUID.fromString(userId))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        messageSource.getMessage(MSG_USER_NOT_FOUND, new Object[]{userId}, userLocale)));
    }

    private RestaurantGroup loadExistingGroupOrThrow(UUID groupId, Locale userLocale) {
        RestaurantGroup group = groupRepository.findById(groupId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        messageSource.getMessage(MSG_RESTAURANT_GROUP_NOT_FOUND, null, userLocale)));
        if (Boolean.TRUE.equals(group.getIsDeleted())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    messageSource.getMessage("restaurantgroup.update.error.deleted", null, userLocale));
        }
        return group;
    }

    private List<RestaurantGroupTranslationDTO> validateCreateGroupTranslations(
            List<RestaurantGroupTranslationDTO> translations, Locale userLocale) {
        if (translations == null || translations.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    messageSource.getMessage("combo.translations.required", null, userLocale));
        }

        boolean hasValidName = translations.stream()
                .anyMatch(t -> t.getName() != null && !t.getName().trim().isEmpty());
        if (!hasValidName) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    messageSource.getMessage("item.update.error.no.valid.name", null, userLocale));
        }

        Set<String> languageSet = new HashSet<>();
        for (RestaurantGroupTranslationDTO t : translations) {
            String name = t.getName();
            if (name != null && !name.trim().isEmpty()) {
                boolean nameExists = translationRepository.existsByNameAndLanguageCode(name.trim(), t.getLanguageCode());
                if (nameExists) {
                    throw new ResponseStatusException(HttpStatus.CONFLICT,
                            messageSource.getMessage("restaurantgroup.create.error.name.exists",
                                    new Object[]{name.trim()}, userLocale));
                }
                if (!languageSet.add(t.getLanguageCode())) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                            messageSource.getMessage("restaurantgroup.create.error.duplicate.language",
                                    new Object[]{t.getLanguageCode()}, userLocale));
                }
            }
        }
        return translations;
    }

    private void validateRestaurantGroupCodeForCreate(String restaurantGroupCode, Locale userLocale) {
        if (groupRepository.existsByRestaurantGroupCode(restaurantGroupCode)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    messageSource.getMessage("restaurantgroup.create.error.code.exists",
                            new Object[]{restaurantGroupCode}, userLocale));
        }
    }

    private RestaurantGroup buildNewRestaurantGroup(RestaurantGroupResponse dto, User user) {
        RestaurantGroup group = new RestaurantGroup();
        group.setRestaurantGroupCode(dto.getRestaurantGroupCode());
        group.setStatus(dto.getStatus() != null ? EntityStatus.valueOf(dto.getStatus()) : null);
        group.setCreatedAt(OffsetDateTime.now(ZoneOffset.UTC));
        group.setIsDeleted(false);
        group.setSalesAlertThreshold(dto.getSalesAlertThreshold());
        group.setRefundAlertPercentage(dto.getRefundAlertPercentage());
        group.setCancellationAlertPercentage(dto.getCancellationAlertPercentage());
        group.setAlertsEnabled(dto.getAlertsEnabled() == null || dto.getAlertsEnabled());
        group.setCreatedBy(user);
        return group;
    }

    private void saveGroupTranslations(RestaurantGroup group, List<RestaurantGroupTranslationDTO> translations) {
        for (RestaurantGroupTranslationDTO entry : translations) {
            String name = entry.getName();
            if (name != null && !name.trim().isEmpty()) {
                RestaurantGroupTranslation translation = new RestaurantGroupTranslation();
                translation.setName(name.trim());
                translation.setLanguageCode(entry.getLanguageCode());
                translation.setRestaurantGroup(group);
                translationRepository.save(translation);
            }
        }
    }

    private RestaurantGroup refreshRestaurantGroup(RestaurantGroup group) {
        groupRepository.flush();
        return groupRepository.findById(group.getId()).orElse(group);
    }

    private List<RestaurantGroupTranslationDTO> buildGroupTranslationDtos(UUID groupId) {
        List<RestaurantGroupTranslation> savedTranslations = translationRepository.findAllByRestaurantGroupIdWithLanguage(groupId);
        List<RestaurantGroupTranslationDTO> translationDTOs = new ArrayList<>();
        for (RestaurantGroupTranslation t : savedTranslations) {
            if (t.getLanguageCode() != null) {
                RestaurantGroupTranslationDTO translationDTO = new RestaurantGroupTranslationDTO();
                translationDTO.setLanguageCode(t.getLanguageCode());
                translationDTO.setName(t.getName());
                translationDTOs.add(translationDTO);
            }
        }
        return translationDTOs;
    }

    private RestaurantGroupDTO<RestaurantGroupResponse> buildRestaurantGroupDto(RestaurantGroup group) {
        List<RestaurantGroupTranslationDTO> translationDTOs = buildGroupTranslationDtos(group.getId());
        long restaurantCount = restaurantRepository.countByRestaurantGroupIdAndIsDeletedFalse(group.getId());

        RestaurantGroupResponse response = RestaurantGroupResponse.builder()
                .uuid(group.getId().toString())
                .restaurantGroupCode(group.getRestaurantGroupCode())
                .status(group.getStatus() != null ? group.getStatus().name() : null)
                .createdAt(group.getCreatedAt() != null ? group.getCreatedAt().toLocalDateTime() : null)
                .createdBy(formatUserName(group.getCreatedBy()))
                .updatedAt(group.getUpdatedAt() != null ? group.getUpdatedAt().toLocalDateTime() : null)
                .updatedBy(formatUserName(group.getUpdatedBy()))
                .translations(translationDTOs)
                .isPublished(!restaurantGroupMenuMappingRepository.findById_RestaurantGroupId(group.getId()).isEmpty())
                .restaurantCount(restaurantCount)
                .salesAlertThreshold(group.getSalesAlertThreshold())
                .refundAlertPercentage(group.getRefundAlertPercentage())
                .cancellationAlertPercentage(group.getCancellationAlertPercentage())
                .alertsEnabled(group.getAlertsEnabled())
                .build();

        return RestaurantGroupDTO.<RestaurantGroupResponse>builder()
                .RestaurantGroup(response)
                .build();
    }

    private void createRestaurantGroupAuditTrail(User user, RestaurantGroup group, ActionType actionType,
            String actionVerb) {
        try {
            auditTrailService.createAuditTrail(
                    user,
                    actionType,
                    null,
                    null,
                    null,
                    null,
                    group.getId(),
                    ENTITY_TYPE_RESTAURANT_GROUP,
                    "Restaurant Group " + actionVerb + ": " + group.getRestaurantGroupCode());
        } catch (Exception e) {
            // Don't break restaurant group flow if audit trail fails
        }
    }

    private void validateRestaurantGroupCodeForUpdate(String restaurantGroupCode, RestaurantGroup group, UUID id,
            Locale userLocale) {
        if (!restaurantGroupCode.equals(group.getRestaurantGroupCode())
                && groupRepository.existsByRestaurantGroupCodeExcludingIdIncludingDeleted(restaurantGroupCode, id)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    messageSource.getMessage("restaurantgroup.create.error.code.exists",
                            new Object[]{restaurantGroupCode}, userLocale));
        }
    }

    private List<RestaurantGroupTranslationDTO> validateUpdateGroupTranslations(
            List<RestaurantGroupTranslationDTO> translations, UUID groupId, Locale userLocale) {
        if (translations == null || translations.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    messageSource.getMessage("combo.translations.required", null, userLocale));
        }

        boolean hasValidName = translations.stream()
                .anyMatch(t -> t.getName() != null && !t.getName().trim().isEmpty());
        if (!hasValidName) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    messageSource.getMessage("item.update.error.no.valid.name", null, userLocale));
        }

        Set<String> languageSet = new HashSet<>();
        for (RestaurantGroupTranslationDTO t : translations) {
            String name = t.getName();
            if (name != null && !name.trim().isEmpty()) {
                boolean nameExists = translationRepository
                        .existsByNameAndLanguageCodeAndRestaurantGroupIdNot(name.trim(), t.getLanguageCode(), groupId);
                if (nameExists) {
                    throw new ResponseStatusException(HttpStatus.CONFLICT,
                            messageSource.getMessage("restaurantgroup.create.error.name.exists",
                                    new Object[]{name.trim()}, userLocale));
                }
            }
            if (!languageSet.add(t.getLanguageCode())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        messageSource.getMessage("restaurantgroup.create.error.duplicate.language",
                                new Object[]{t.getLanguageCode()}, userLocale));
            }
        }

        return translations;
    }

    private void validateRestaurantGroupStatusTransition(UUID groupId, EntityStatus currentStatus, EntityStatus newStatus,
            Locale userLocale) {
        if (newStatus == EntityStatus.INACTIVE && currentStatus != EntityStatus.INACTIVE) {
            List<Restaurant> activeRestaurants = restaurantRepository
                    .findByRestaurantGroupIdAndIsDeletedFalseAndStatus(groupId, EntityStatus.ACTIVE);
            if (!activeRestaurants.isEmpty()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        messageSource.getMessage("restaurantgroup.update.error.cannot.inactivate.with.active.restaurants",
                                null, userLocale));
            }
        }
    }

    private void applyRestaurantGroupUpdates(RestaurantGroup group, RestaurantGroupResponse dto, EntityStatus newStatus,
            User user) {
        group.setRestaurantGroupCode(dto.getRestaurantGroupCode());
        group.setStatus(newStatus);
        group.setUpdatedAt(OffsetDateTime.now(ZoneOffset.UTC));
        if (dto.getSalesAlertThreshold() != null) {
            group.setSalesAlertThreshold(dto.getSalesAlertThreshold());
        }
        if (dto.getRefundAlertPercentage() != null) {
            group.setRefundAlertPercentage(dto.getRefundAlertPercentage());
        }
        if (dto.getCancellationAlertPercentage() != null) {
            group.setCancellationAlertPercentage(dto.getCancellationAlertPercentage());
        }
        if (dto.getAlertsEnabled() != null) {
            group.setAlertsEnabled(dto.getAlertsEnabled());
        }
        group.setUpdatedBy(user);
    }

    private void mergeGroupTranslations(RestaurantGroup group, List<RestaurantGroupTranslationDTO> translations) {
        List<RestaurantGroupTranslation> existingTranslations = translationRepository
                .findAllByRestaurantGroupIdWithLanguage(group.getId());
        Map<String, RestaurantGroupTranslation> existingTranslationsMap = existingTranslations.stream()
                .collect(Collectors.toMap(
                        RestaurantGroupTranslation::getLanguageCode,
                        translation -> translation,
                        (existing, replacement) -> existing));

        for (RestaurantGroupTranslationDTO entry : translations) {
            String languageCode = entry.getLanguageCode();
            String name = entry.getName();

            if (name != null && !name.trim().isEmpty()) {
                RestaurantGroupTranslation translation = existingTranslationsMap.get(languageCode);
                if (translation != null) {
                    translation.setName(name.trim());
                } else {
                    translation = new RestaurantGroupTranslation();
                    translation.setName(name.trim());
                    translation.setLanguageCode(languageCode);
                    translation.setRestaurantGroup(group);
                }
                translationRepository.save(translation);
            } else if (languageCode != null && existingTranslationsMap.containsKey(languageCode)) {
                translationRepository.delete(existingTranslationsMap.get(languageCode));
                existingTranslationsMap.remove(languageCode);
            }
        }

        Set<String> requestedLanguageCodes = translations.stream()
                .filter(t -> t.getName() != null && !t.getName().trim().isEmpty())
                .map(RestaurantGroupTranslationDTO::getLanguageCode)
                .collect(Collectors.toSet());

        existingTranslations.stream()
                .filter(t -> !requestedLanguageCodes.contains(t.getLanguageCode()))
                .forEach(translationRepository::delete);
    }

    private RestaurantGroup loadGroupForGet(UUID id, Boolean includeDeleted) {
        if (Boolean.TRUE.equals(includeDeleted)) {
            return groupRepository.findById(id)
                    .orElseThrow(() -> new RuntimeException("RestaurantGroup not found"));
        }
        return groupRepository.findByIdAndIsDeletedFalse(id)
                .orElseThrow(() -> new RuntimeException("RestaurantGroup not found"));
    }

    private List<RestaurantResponse> buildGroupRestaurantResponses(UUID groupId) {
        List<Restaurant> restaurants = restaurantRepository.findByRestaurantGroupIdAndIsDeletedFalse(groupId);
        List<RestaurantResponse> restaurantResponses = new ArrayList<>();

        for (Restaurant restaurant : restaurants) {
            List<RestaurantTranslationDto> restaurantTranslationDTOs = restaurantTranslationRepository
                    .findAllByRestaurantIdWithLanguage(restaurant.getId())
                    .stream()
                    .filter(rt -> rt.getLanguageCode() != null)
                    .map(rt -> RestaurantTranslationDto.builder()
                            .languageCode(rt.getLanguageCode())
                            .name(rt.getName())
                            .build())
                    .collect(Collectors.toList());

            List<OperatingHourDto> operatingHours = restaurantOperatingHoursRepository.findByRestaurant_Id(restaurant.getId())
                    .stream()
                    .map(restaurantOperatingHoursMapper::toOperatingHoursDto)
                    .collect(Collectors.toList());

            Long activeDiscountCount = restaurantDiscountMappingRepository.countActiveDiscountsByRestaurantId(restaurant.getId());
            Long activePromotionCount = restaurantPromotionMappingRepository.countActivePromotionsByRestaurantId(restaurant.getId());

            RestaurantResponse restaurantResponse = RestaurantResponse.builder()
                    .uuid(restaurant.getId().toString())
                    .restaurantCode(restaurant.getRestaurantCode())
                    .city(restaurant.getCity())
                    .area(restaurant.getArea())
                    .state(restaurant.getState())
                    .address1(restaurant.getAddress1())
                    .address2(restaurant.getAddress2())
                    .latitude(restaurant.getLatitude())
                    .longitude(restaurant.getLongitude())
                    .locationPin(restaurant.getLocationPin())
                    .tableQrCodeType(restaurant.getTableQrCodeType())
                    .paymentQrUrl(awsService.getFullUrl(restaurant.getPaymentQrUrl()))
                    .status(restaurant.getStatus())
                    .createdAt(restaurant.getCreatedAt() != null ? restaurant.getCreatedAt().toLocalDateTime() : null)
                    .updatedAt(restaurant.getUpdatedAt() != null ? restaurant.getUpdatedAt().toLocalDateTime() : null)
                    .createdBy(formatUserName(restaurant.getCreatedBy()))
                    .updatedBy(formatUserName(restaurant.getUpdatedBy()))
                    .restaurantGroupId(restaurant.getRestaurantGroup() != null ? restaurant.getRestaurantGroup().getId().toString() : null)
                    .logoUrl(awsService.getFullUrl(restaurant.getLogoUrl()))
                    .translations(restaurantTranslationDTOs)
                    .isDeleted(restaurant.getIsDeleted())
                    .restaurantGroupName(restaurant.getRestaurantGroupName())
                    .employeeCount(userRepository.countByRestaurantId(restaurant.getId()))
                    .operatingHours(operatingHours)
                    .activeDiscountCount(activeDiscountCount != null ? activeDiscountCount : 0L)
                    .activePromotionCount(activePromotionCount != null ? activePromotionCount : 0L)
                    .salesAlertThreshold(restaurant.getSalesAlertThreshold())
                    .refundAlertPercentage(restaurant.getRefundAlertPercentage())
                    .cancellationAlertPercentage(restaurant.getCancellationAlertPercentage())
                    .alertsEnabled(restaurant.getAlertsEnabled())
                    .phoneNumber(restaurant.getPhoneNumber())
                    .build();

            restaurantResponses.add(restaurantResponse);
        }

        return restaurantResponses;
    }

    private RestaurantGroupDTO<RestaurantGroupResponse> buildDetailedRestaurantGroupDto(RestaurantGroup group,
            List<RestaurantResponse> restaurantResponses) {
        List<RestaurantGroupTranslationDTO> translationDTOs = buildGroupTranslationDtos(group.getId());
        long restaurantCount = restaurantRepository.countByRestaurantGroupIdAndIsDeletedFalse(group.getId());
        Long totalActiveDiscountCount = restaurantResponses.stream()
                .mapToLong(r -> r.getActiveDiscountCount() != null ? r.getActiveDiscountCount() : 0L)
                .sum();
        Long totalActivePromotionCount = restaurantResponses.stream()
                .mapToLong(r -> r.getActivePromotionCount() != null ? r.getActivePromotionCount() : 0L)
                .sum();

        RestaurantGroupResponse response = RestaurantGroupResponse.builder()
                .uuid(group.getId().toString())
                .restaurantGroupCode(group.getRestaurantGroupCode())
                .status(group.getStatus() != null ? group.getStatus().name() : null)
                .createdAt(group.getCreatedAt() != null ? group.getCreatedAt().toLocalDateTime() : null)
                .createdBy(formatUserName(group.getCreatedBy()))
                .updatedAt(group.getUpdatedAt() != null ? group.getUpdatedAt().toLocalDateTime() : null)
                .updatedBy(formatUserName(group.getUpdatedBy()))
                .translations(translationDTOs)
                .isPublished(!restaurantGroupMenuMappingRepository.findById_RestaurantGroupId(group.getId()).isEmpty())
                .restaurantCount(restaurantCount)
                .activeDiscountCount(totalActiveDiscountCount)
                .activePromotionCount(totalActivePromotionCount)
                .salesAlertThreshold(group.getSalesAlertThreshold())
                .refundAlertPercentage(group.getRefundAlertPercentage())
                .cancellationAlertPercentage(group.getCancellationAlertPercentage())
                .alertsEnabled(group.getAlertsEnabled())
                .build();
        response.setRestaurants(restaurantResponses);

        return RestaurantGroupDTO.<RestaurantGroupResponse>builder()
                .RestaurantGroup(response)
                .build();
    }

    /**
     * Create availability records for restaurant-menu mapping
     * Works on a pre-fetched list of CategoryItemMappings for a given menu
     */
    private void createAvailabilityForRestaurantMenuMapping(
            UUID restaurantId,
            List<com.gulfnet.shared_library.entity.CategoryItemMapping> categoryItemMappings,
            UUID userId) {

        if (categoryItemMappings == null || categoryItemMappings.isEmpty()) {
            return;
        }

        // Preload existing availability records for this restaurant and menu items
        List<UUID> mappingIds = categoryItemMappings.stream()
                .map(com.gulfnet.shared_library.entity.CategoryItemMapping::getId)
                .collect(Collectors.toList());

        List<com.gulfnet.shared_library.entity.RestaurantItemAvailability> existingAvailabilities =
                restaurantItemAvailabilityRepository.findByRestaurantIdAndCategoryItemMappingIdIn(
                        restaurantId, mappingIds);

        // Build a set of existing mapping IDs to skip duplicates
        Set<UUID> existingMappingIds = existingAvailabilities.stream()
                .map(a -> a.getCategoryItemMapping().getId())
                .collect(Collectors.toSet());

        User user = new User();
        user.setId(userId);

        Restaurant restaurant = new Restaurant();
        restaurant.setId(restaurantId);

        for (com.gulfnet.shared_library.entity.CategoryItemMapping mapping : categoryItemMappings) {
            if (existingMappingIds.contains(mapping.getId())) {
                continue;
            }

            com.gulfnet.shared_library.entity.RestaurantItemAvailability availability = 
                    com.gulfnet.shared_library.entity.RestaurantItemAvailability.builder()
                    .restaurant(restaurant)
                    .categoryItemMapping(mapping)
                    .isAvailable(true) // Default to available
                    .createdBy(user)
                    .build();

            restaurantItemAvailabilityRepository.save(availability);
        }
    }

    /**
     * Restores (un-deletes) a list of soft-deleted restaurant groups.
     * <p>
     * Only groups that currently have {@code isDeleted=true} are restored; if none of the provided ids are deleted,
     * the call is rejected.
     *
     * @param ids restaurant group ids to restore (required)
     * @param userId actor user id (UUID string) (required)
     * @return void response indicating success
     * @throws ResponseStatusException if no groups are found, or none are deleted, or user is missing
     */
    @Override
    @Transactional
    @CacheEvict(value = {"restaurantGroupsLite", "restaurants"}, allEntries = true)
    public ResponseDto<Void> restoreRestaurantGroups(List<UUID> ids, String userId) {
        Locale userLocale = LocaleContextHolder.getLocale();
        
        // Find user for updatedBy
        User user = userRepository.findById(UUID.fromString(userId))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, 
                    messageSource.getMessage(MSG_USER_NOT_FOUND, new Object[]{userId}, userLocale)));
        
        // Find all restaurant groups by IDs that are deleted
        List<RestaurantGroup> groups = groupRepository.findAllById(ids);
        
        if (groups.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, 
                messageSource.getMessage(MSG_RESTAURANT_GROUP_NOT_FOUND, null, userLocale));
        }
        
        // Filter only deleted groups and restore them
        List<RestaurantGroup> deletedGroups = groups.stream()
                .filter(g -> Boolean.TRUE.equals(g.getIsDeleted()))
                .collect(Collectors.toList());
        
        if (deletedGroups.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, 
                messageSource.getMessage("restaurant.group.restore.error.not.deleted", null, userLocale));
        }
        
        // Restore all deleted groups
        for (RestaurantGroup group : deletedGroups) {
            group.setIsDeleted(false);
            group.setUpdatedAt(OffsetDateTime.now(ZoneOffset.UTC));
            group.setUpdatedBy(user);
        }
        
        groupRepository.saveAll(deletedGroups);
        
        return ResponseDto.<Void>builder()
            .message(messageSource.getMessage("restaurant.group.restore.success", null, userLocale))
            .build();
    }
}
