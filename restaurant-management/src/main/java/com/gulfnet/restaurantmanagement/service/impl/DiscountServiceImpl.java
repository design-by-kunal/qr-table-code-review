package com.gulfnet.restaurantmanagement.service.impl;

import com.gulfnet.restaurantmanagement.config.LocalizationProperties;
import com.gulfnet.shared_library.util.TranslationUtils;
import com.gulfnet.shared_library.util.LocaleSortUtil;
import com.gulfnet.restaurantmanagement.service.DiscountService;
import com.gulfnet.restaurantmanagement.service.AuditTrailService;
import com.gulfnet.restaurantmanagement.util.MessageUtil;
import com.gulfnet.shared_library.enums.ActionType;
import com.gulfnet.shared_library.entity.Discount;
import com.gulfnet.shared_library.entity.DiscountTranslation;
import com.gulfnet.shared_library.entity.User;
import com.gulfnet.shared_library.enums.AppliedTo;
import com.gulfnet.shared_library.enums.DayOfWeek;
import com.gulfnet.shared_library.enums.DiscountType;
import com.gulfnet.shared_library.enums.EntityStatus;
import com.gulfnet.shared_library.enums.RequestStatus;
import java.util.Optional;
import com.gulfnet.shared_library.entity.Item;
import com.gulfnet.shared_library.entity.ItemDiscountMapping;
import com.gulfnet.shared_library.model.request.DiscountRequest;
import com.gulfnet.shared_library.model.request.DiscountTranslationRequest;
import com.gulfnet.shared_library.model.response.dto.DiscountDto;
import com.gulfnet.shared_library.model.response.dto.DiscountResponse;
import com.gulfnet.shared_library.model.response.dto.DiscountTranslationResponse;
import com.gulfnet.shared_library.model.response.dto.ResponseDto;
import com.gulfnet.shared_library.repository.DiscountRepository;
import com.gulfnet.shared_library.repository.MenuDiscountMappingRepository;
import com.gulfnet.shared_library.model.response.dto.*;
import com.gulfnet.shared_library.model.request.*;
import com.gulfnet.shared_library.repository.DiscountRepository;
import com.gulfnet.shared_library.repository.DiscountTranslationRepository;
import com.gulfnet.shared_library.repository.ItemRepository;
import com.gulfnet.shared_library.repository.UserRepository;
import com.gulfnet.shared_library.entity.MenuCategoryMapping;
import com.gulfnet.shared_library.entity.CategoryItemMapping;
import com.gulfnet.shared_library.repository.CategoryItemMappingRepository;
import com.gulfnet.shared_library.repository.*;
import com.gulfnet.shared_library.entity.*;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import com.gulfnet.shared_library.util.CurrencyFormatter;
import com.gulfnet.restaurantmanagement.config.RestaurantChainConfigProperties;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.OffsetTime;
import java.time.ZoneOffset;
import java.util.*;
import java.util.Comparator;
import java.util.Locale;
import java.util.stream.Collectors;
import com.gulfnet.shared_library.repository.DiscountBxgyItemRepository;
import com.gulfnet.shared_library.repository.PromotionRepository;
import com.gulfnet.shared_library.enums.MenuStatus;

@Slf4j
@Service
public class DiscountServiceImpl implements DiscountService {

    // Message keys
    private static final String MSG_DISCOUNT_NOT_FOUND = "discount.not.found";
    private static final String MSG_MENU_NOT_FOUND = "menu.not.found";
    private static final String MSG_USER_NOT_FOUND = "user.not.found";
    private static final String MSG_RESTAURANT_NOT_FOUND = "restaurant.not.found";
    private static final String MSG_DISCOUNT_DELETED = "discount.deleted";
    private static final String MSG_DISCOUNT_INACTIVE = "discount.inactive";
    private static final String MSG_DISCOUNT_ASSIGNMENT_SUCCESS = "discount.assignment.success";
    private static final String MSG_DISCOUNTS_LIST_SUCCESS = "discounts.list.success";
    private static final String MSG_DISCOUNT_CREATE_SUCCESS = "discount.create.success";
    private static final String MSG_DISCOUNT_UPDATE_SUCCESS = "discount.update.success";
    private static final String MSG_DISCOUNT_DELETE_SUCCESS = "discount.delete.success";
    private static final String MSG_DISCOUNT_VIEW_SUCCESS = "discount.view.success";
    private static final String MSG_DISCOUNT_ALREADY_DELETED = "discount.already.deleted";
    private static final String MSG_DISCOUNT_ALREADY_ASSIGNED_TO_MENU = "discount.already.assigned.to.menu";
    private static final String MSG_DISCOUNT_NOT_ASSIGNED_TO_MENU = "discount.not.assigned.to.menu";
    private static final String MSG_DISCOUNT_NOT_ASSIGNED_TO_RESTAURANT = "discount.not.assigned.to.restaurant";
    private static final String MSG_DISCOUNT_VALIDITY_UPDATED_SUCCESS = "discount.validity.updated.success";
    private static final String MSG_DISCOUNT_ASSIGNMENT_EDIT_SUCCESS = "discount.assignment.edit.success";
    private static final String MSG_DISCOUNT_DETAILS_SUCCESS = "discount.details.success";
    private static final String MSG_DISCOUNT_DETAILS_RETRIEVED_SUCCESS = "discount.details.retrieved.success";
    private static final String MSG_DISCOUNT_BXGY_ASSIGNMENT_SUCCESS = "discount.bxgy.assignment.success";
    private static final String MSG_DISCOUNT_UNASSIGNMENT_SUCCESS = "discount.unassignment.success";
    private static final String MSG_DISCOUNT_RESTORE_SUCCESS = "discount.restore.success";
    private static final String MSG_DISCOUNT_RESTORE_ERROR_NOT_DELETED = "discount.restore.error.not.deleted";
    private static final String MSG_DISCOUNT_VALIDATION_SUCCESS = "discount.validation.success";
    private static final String MSG_DISCOUNT_ERROR_INVALID_LANGUAGE = "error.invalid.language";
    private static final String MSG_DISCOUNT_ERROR_CODE_EXISTS = "discount.error.code.exists";
    private static final String MSG_DISCOUNT_ERROR_MAX_USES_REQUIRED = "discount.error.max.uses.required";
    private static final String MSG_DISCOUNT_ERROR_PUBLISHED_MENU = "discount.error.published.menu";
    private static final String MSG_DISCOUNT_ERROR_ASSIGNED_TO_MENUS = "discount.error.assigned.to.menus";
    private static final String MSG_DISCOUNT_ERROR_ASSIGNED_TO_PROMOTION = "discount.error.assigned.to.promotion";
    private static final String MSG_DISCOUNT_ERROR_INVALID_DATE_RANGE = "discount.error.invalid.date.range";
    private static final String MSG_DISCOUNT_ERROR_SCHEDULE_INVALID_TIME_RANGE = "discount.error.schedule.invalid.time.range";
    private static final String MSG_DISCOUNT_ASSIGNMENT_PAST_VALID_FROM = "discount.assignment.past.validFrom";
    private static final String MSG_DISCOUNT_ASSIGNMENT_PAST_VALID_TO = "discount.assignment.past.validTo";
    private static final String MSG_DISCOUNT_BXGY_ITEM_ALREADY_ASSIGNED_TO_OTHER_DISCOUNT = "discount.bxgy.item.already.assigned.to.other.discount";
    private static final String MSG_DISCOUNT_BXGY_ALREADY_ASSIGNED = "discount.bxgy.already.assigned";
    private static final String MSG_DISCOUNT_ASSIGNMENT_ALREADY_EXISTS = "discount.assignment.already.exists";
    private static final String MSG_DISCOUNT_TYPE_MISMATCH = "discount.type.mismatch";
    private static final String MSG_DISCOUNT_TYPE_MISMATCH_CATEGORY = "discount.type.mismatch.category";
    private static final String MSG_DISCOUNT_TYPE_MISMATCH_ORDER = "discount.type.mismatch.order";
    private static final String MSG_ITEM_NOT_FOUND = "item.not.found";
    private static final String MSG_ITEM_DELETED = "item.deleted";
    private static final String MSG_ITEM_NOT_IN_MENU = "item.not.in.menu";
    private static final String MSG_ITEM_IDS_NULL = "item.ids.null";
    private static final String MSG_ITEM_ID_INVALID = "item.id.invalid";
    private static final String MSG_BUY_ITEM_IDS_REQUIRED = "buy.item.ids.required";
    private static final String MSG_GET_ITEM_IDS_REQUIRED = "get.item.ids.required";
    private static final String MSG_CATEGORY_LIST_EMPTY = "category.list.empty";
    private static final String MSG_CATEGORY_ID_INVALID = "category.id.invalid";
    private static final String MSG_CATEGORY_NOT_FOUND = "category.not.found";
    private static final String MSG_CATEGORY_MENU_MISMATCH = "category.menu.mismatch";
    private static final String MSG_CATEGORY_MENU_MAPPING_INVALID = "category.menu.mapping.invalid";
    private static final String MSG_CATEGORY_MENU_MAPPING_NOT_FOUND = "category.menu.mapping.not.found";
    private static final String MSG_CATEGORY_ALREADY_ASSIGNED_SAME_DISCOUNT = "category.already.assigned.same.discount";
    private static final String MSG_CATEGORY_ALREADY_ASSIGNED_DISCOUNT = "category.already.assigned.discount";
    private static final String MSG_DISCOUNT_CANNOT_APPLY_TO_COMBO_CATEGORY = "discount.cannot.apply.to.combo.category";
    private static final String MSG_DISCOUNT_ID_REQUIRED = "discount.id.required";
    private static final String MSG_MENU_ID_REQUIRED = "menu.id.required";
    private static final String MSG_MENU_DELETED = "menu.deleted";
    private static final String MSG_RESTAURANT_MENU_NOT_FOUND = "restaurant.menu.not.found";
    private static final String MSG_ERROR_INVALID_DISCOUNT_TYPE = "error.invalid.discountType";
    private static final String MSG_ERROR_INVALID_APPLIED_TO = "error.invalid.appliedTo";
    private static final String MSG_DISCOUNT_BULK_UNASSIGNMENT_CATEGORIES_SUCCESS = "discount.bulk.unassignment.categories.success";
    private static final String MSG_DISCOUNT_BULK_UNASSIGNMENT_ITEMS_SUCCESS = "discount.bulk.unassignment.items.success";
    private static final String MSG_MENU_DISCOUNT_LIST_SUCCESS = "menu.discount.list.success";

    // Field names
    private static final String FIELD_MAX_USES = "maxUses";
    private static final String FIELD_CURRENT_USAGE = "currentUsage";
    private static final String FIELD_DISCOUNT_CODE = "discountCode";
    private static final String FIELD_IS_DELETED = "isDeleted";
    private static final String FIELD_STATUS = "status";
    private static final String FIELD_CREATED_AT = "createdAt";
    
    // Other constants
    private static final String ENTITY_TYPE_DISCOUNT = "DISCOUNT";
    private static final String SUFFIX_CODE = " (";
    
    // Message keys (non-static for new ones)
    private final String MSG_DISCOUNT_ERROR_VALID_FROM_NOT_UTC = "discount.error.valid.from.not.utc";
    private final String MSG_DISCOUNT_ERROR_VALID_TO_NOT_UTC = "discount.error.valid.to.not.utc";

    // Audit trail message templates
    private static final String AUDIT_MSG_VALID_FROM = "ValidFrom: %s";
    private static final String AUDIT_MSG_VALID_TO = "ValidTo: %s";
    private static final String AUDIT_MSG_START_TIME = "StartTime: %s";
    private static final String AUDIT_MSG_END_TIME = "EndTime: %s";
    private static final String AUDIT_MSG_DAYS_OF_WEEK = "DaysOfWeek: %s";

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private MenuDiscountMappingRepository menuDiscountMappingRepository;

    @Autowired
    private DiscountRepository discountRepository;

    @Autowired
    private MessageUtil messageUtil;


    @Autowired
    private LocalizationProperties localizationProperties;

    @Autowired
    private RestaurantChainConfigProperties restaurantChainConfigProperties;

    @Autowired
    private DiscountTranslationRepository discountTranslationRepository;

    @Autowired
    private ItemRepository itemRepository;

    @Autowired
    private ItemDiscountMappingRepository itemDiscountMappingRepository;

    @Autowired
    private CategoryItemMappingRepository categoryItemMappingRepository;

    @Autowired
    private MenuCategoryMappingRepository menuCategoryMappingRepository;

    @Autowired
    private MenuRepository menuRepository;

    @Autowired
    private com.gulfnet.shared_library.repository.CategoryRepository categoryRepository;

    @Autowired
    private com.gulfnet.shared_library.repository.CategoryDiscountMappingRepository categoryDiscountMappingRepository;

    @Autowired
    private DiscountBxgyItemRepository discountBxgyItemRepository;

    @Autowired
    private PromotionRepository promotionRepository;

    @Autowired
    private com.gulfnet.shared_library.repository.RestaurantDiscountMappingRepository restaurantDiscountMappingRepository;

    @Autowired
    private com.gulfnet.shared_library.repository.RestaurantMenuMappingRepository restaurantMenuMappingRepository;

    @Autowired
    private com.gulfnet.shared_library.repository.RestaurantRepository restaurantRepository;

    @Autowired
    private com.gulfnet.shared_library.repository.CategoryTranslationRepository categoryTranslationRepository;

    @Autowired
    private com.gulfnet.shared_library.repository.ItemTranslationRepository itemTranslationRepository;

    @Autowired
    private com.gulfnet.shared_library.config.AWSService awsService;

    @Autowired
    private AuditTrailService auditTrailService;

    /**
     * Creates a new discount with translations and business rule validations.
     * Validates discount code uniqueness, business rules (maxDiscountValue, threshold, maxUses),
     * and creates audit trail for discount creation.
     *
     * @param userId  the ID of the user creating the discount
     * @param request the discount creation request with all discount details and translations
     * @param locale  the locale code for localized error messages
     * @return ResponseDto containing the created discount response
     * @throws ResponseStatusException if validation fails, discount code exists, or user not found
     */
    @Override
    @Transactional
    public ResponseDto<DiscountDto<DiscountResponse>> createDiscount(String userId, DiscountRequest request, String locale) {
        log.info("Creating new discount");
        Locale userLocale = Locale.forLanguageTag(locale);

        // Validate user
        User user = userRepository.findById(UUID.fromString(userId))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        messageUtil.getMessage(MSG_USER_NOT_FOUND, userLocale)));

        // Business rule: maxDiscountValue >= value
        if (request.getMaxDiscountValue() != null && request.getValue() != null &&
        request.getMaxDiscountValue().compareTo(request.getValue()) < 0) {
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
        messageUtil.getMessage("discount.error.max.discount.less", userLocale));
        }
        // Business rule: threshold cannot be negative
        if (request.getOrderValueThreshold() != null && request.getOrderValueThreshold().compareTo(BigDecimal.ZERO) < 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                messageUtil.getMessage("discount.error.threshold.negative", userLocale));
        }
        // Business rule: proper enum/type validation
        // Removed enum validation for DiscountType, AppliedTo, EntityStatus

        // Business rule: maxUses is required for ORDER discounts, optional for ITEM and CATEGORY
        if (request.getAppliedTo() == AppliedTo.ORDER && request.getMaxUses() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                messageUtil.getMessage(MSG_DISCOUNT_ERROR_MAX_USES_REQUIRED, userLocale));
        }

        // Check if discount code already exists
        if (discountRepository.existsByDiscountCodeAndIsDeletedFalse(request.getDiscountCode())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    messageUtil.getMessage(MSG_DISCOUNT_ERROR_CODE_EXISTS, userLocale));
        }

        // Defensive discount code validation
        String discountCode = request.getDiscountCode();
        if (discountCode == null || discountCode.trim().isEmpty()) {
            throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                messageUtil.getMessage("discount.error.code.blank", userLocale));
        }
        // Allow any non-blank code up to 10 characters, but disallow values equal to "true"/"false" (case-insensitive)
        if (!discountCode.matches("^(?i)(?!true$)(?!false$).{1,10}$")) {
            throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                messageUtil.getMessage("discount.error.code.pattern", userLocale));
        }

        // Create discount entity
        // Set maxUses to null for ITEM and CATEGORY discounts (even if provided)
        Integer maxUsesValue = (request.getAppliedTo() == AppliedTo.ITEM || request.getAppliedTo() == AppliedTo.CATEGORY) 
                ? null : request.getMaxUses();
        
        final Discount discount = Discount.builder()
                .discountCode(request.getDiscountCode())
                .discountType(request.getDiscountType())
                .appliedTo(request.getAppliedTo())
                .value(request.getValue())
                .orderValueThreshold(request.getOrderValueThreshold())
                .maxDiscountValue(request.getMaxDiscountValue())
                .buyQuantity(request.getBuyQuantity())
                .getQuantity(request.getGetQuantity())
                .maxUses(maxUsesValue)
                .currentUsage(0)
                .status(request.getStatus())
                .isDeleted(false)
                .createdBy(user)
                .build();

        // Add translations
        request.getTranslations().forEach(translationRequest -> {
            DiscountTranslation translation = DiscountTranslation.builder()
                    .languageCode(translationRequest.getLanguageCode())
                    .name(translationRequest.getName())
                    .description(translationRequest.getDescription())
                    .discount(discount)
                    .build();
            discount.getTranslations().add(translation);
        });


        // Save discount
        final Discount savedDiscount = discountRepository.save(discount);

        // Create response
        String currency = restaurantChainConfigProperties.getChain() != null ? restaurantChainConfigProperties.getChain().getCurrency() : null;
        DiscountResponse discountResponse = DiscountResponse.builder()
            .id(savedDiscount.getId())
            .discountCode(savedDiscount.getDiscountCode())
            .discountType(savedDiscount.getDiscountType())
            .appliedTo(savedDiscount.getAppliedTo())
            .value(savedDiscount.getValue() != null ? CurrencyFormatter.formatAmount(savedDiscount.getValue(), currency) : null)
            .orderValueThreshold(savedDiscount.getOrderValueThreshold() != null ? CurrencyFormatter.formatAmount(savedDiscount.getOrderValueThreshold(), currency) : null)
            .maxDiscountValue(savedDiscount.getMaxDiscountValue() != null ? CurrencyFormatter.formatAmount(savedDiscount.getMaxDiscountValue(), currency) : null)
            .buyQuantity(savedDiscount.getBuyQuantity())
            .getQuantity(savedDiscount.getGetQuantity())
            .maxUses(savedDiscount.getMaxUses())
            .currentUsage(savedDiscount.getCurrentUsage())
            .purchasedItemId(request.getPurchasedItemId())
            .freeItemId(request.getFreeItemId())
            .status(savedDiscount.getStatus())
            .isDeleted(savedDiscount.getIsDeleted())
            .createdAt(savedDiscount.getCreatedAt() != null ? savedDiscount.getCreatedAt().toLocalDateTime() : null)
            .createdBy(formatUserName(savedDiscount.getCreatedBy()))
            .translations(savedDiscount.getTranslations().stream()
                .map(translation -> DiscountTranslationResponse.builder()
                    .languageCode(translation.getLanguageCode())
                    .name(translation.getName())
                    .description(translation.getDescription())
                    .build())
                .collect(Collectors.toList()))
            .build();

        // Create audit trail for discount creation
        try {
            Restaurant restaurant = null;
            if (user.getRestaurantId() != null) {
                restaurant = restaurantRepository.findById(user.getRestaurantId()).orElse(null);
            }
            String discountName = savedDiscount.getTranslations().isEmpty() ? 
                savedDiscount.getDiscountCode() : savedDiscount.getTranslations().get(0).getName();
            auditTrailService.createAuditTrail(
                    user,
                    ActionType.DISCOUNT_CREATE,
                    restaurant,
                    null, // status - will default to NA for non-request actions
                    null, // ipAddress - not available in this context
                    null, // userAgent - not available in this context
                    savedDiscount.getId(),
                    ENTITY_TYPE_DISCOUNT,
                    "Discount created: " + discountName + SUFFIX_CODE + savedDiscount.getDiscountCode() + ")"
            );
        } catch (Exception e) {
            log.error("Failed to create audit trail for discount creation: {}", e.getMessage());
            // Don't break discount creation flow if audit trail fails
        }

        return ResponseDto.<DiscountDto<DiscountResponse>>builder()
                .data(DiscountDto.<DiscountResponse>builder()
                        .discount(discountResponse)
                        .build())
                .message(messageUtil.getMessage(MSG_DISCOUNT_CREATE_SUCCESS, userLocale))
                .build();
    }


    /**
     * Retrieves a paginated and filterable list of discounts.
     * Supports filtering by status, discount type, applied to type, search term, and deletion status.
     * Supports comma-separated values for discountType and appliedTo filters.
     * Results are sorted and paginated with locale-aware name sorting.
     *
     * @param page        page number for pagination
     * @param size        page size for pagination
     * @param status      optional filter by entity status
     * @param search      optional search term for discount code and name
     * @param sortBy      field to sort by
     * @param discountType optional comma-separated discount types to filter by
     * @param appliedTo   optional comma-separated applied to types to filter by
     * @param direction   sort direction
     * @param locale      locale code for localized responses and sorting
     * @param isDeleted   optional filter by deletion status
     * @return ResponseDto containing paginated list of discounts
     * @throws ResponseStatusException if locale is invalid or discount type/applied to is invalid
     */
    @Override
    @Transactional
    public ResponseDto<DiscountListResponse> getDiscounts(
            Integer page,
            Integer size,
            EntityStatus status,
            String search,
            String sortBy,
            String discountType,
            String appliedTo,
            Sort.Direction direction,
            String locale,
            Boolean isDeleted
    ) {

        log.info("Fetching discounts with language: {}, search: {}", locale, search);
        Locale userLocale = Locale.forLanguageTag(locale);

        // Validate locale
        if (!localizationProperties.getLanguages().contains(locale)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    messageUtil.getMessage(MSG_DISCOUNT_ERROR_INVALID_LANGUAGE, userLocale));
        }

        // Convert string parameters to enums with comma-separated support
        final Set<DiscountType> discountTypes;
        if (discountType != null && !discountType.trim().isEmpty()) {
            try {
                Set<DiscountType> tempDiscountTypes = Arrays.stream(discountType.split(","))
                        .map(String::trim)
                        .filter(s -> !s.isBlank())
                        .map(s -> DiscountType.valueOf(s.toUpperCase()))
                        .collect(Collectors.toSet());
                discountTypes = tempDiscountTypes.isEmpty() ? null : tempDiscountTypes;
            } catch (IllegalArgumentException e) {
                String errorMessage = messageUtil.getMessage(MSG_ERROR_INVALID_DISCOUNT_TYPE, userLocale, discountType);
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, errorMessage);
            }
        } else {
            discountTypes = null;
        }
        
        final Set<AppliedTo> appliedToTypes;
        if (appliedTo != null && !appliedTo.trim().isEmpty()) {
            try {
                Set<AppliedTo> tempAppliedToTypes = Arrays.stream(appliedTo.split(","))
                        .map(String::trim)
                        .filter(s -> !s.isBlank())
                        .map(s -> AppliedTo.valueOf(s.toUpperCase()))
                        .collect(Collectors.toSet());
                appliedToTypes = tempAppliedToTypes.isEmpty() ? null : tempAppliedToTypes;
            } catch (IllegalArgumentException e) {
                String errorMessage = messageUtil.getMessage(MSG_ERROR_INVALID_APPLIED_TO, userLocale, appliedTo);
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, errorMessage);
            }
        } else {
            appliedToTypes = null;
        }

        // Validate and set pagination
        int pageNumber = (page != null ? page : 1) - 1;
        if (pageNumber < 0) pageNumber = 0;
        int pageSize = size != null ? size : Integer.MAX_VALUE;
        if (pageSize < 1) pageSize = Integer.MAX_VALUE;

        // Determine sort field mapping (DB fields)
        String normalizedSortBy = (sortBy == null || sortBy.isBlank()) ? FIELD_CREATED_AT : sortBy;
        String dbSortField = switch (normalizedSortBy) {
            case FIELD_CREATED_AT -> FIELD_CREATED_AT;
            case "updatedAt" -> "updatedAt";
            default -> null; // e.g., name -> needs in-memory due to locale translations
        };

        // Create specification for filtering
        Specification<Discount> spec = (root, query, criteriaBuilder) -> {
            List<Predicate> predicates = new ArrayList<>();
            
            // Add status filter
            if (status != null) {
                predicates.add(criteriaBuilder.equal(root.get(FIELD_STATUS), status));
            }
            
            // Add discount type filter
            if (discountTypes != null && !discountTypes.isEmpty()) {
                predicates.add(root.get("discountType").in(discountTypes));
            }
            
            // Add appliedTo filter
            if (appliedToTypes != null && !appliedToTypes.isEmpty()) {
                predicates.add(root.get("appliedTo").in(appliedToTypes));
            }
            
            // Handle isDeleted filter: if isDeleted=true, show deleted; otherwise show non-deleted (default)
            if (isDeleted != null && isDeleted) {
                predicates.add(criteriaBuilder.equal(root.get(FIELD_IS_DELETED), true));
            } else {
                predicates.add(criteriaBuilder.equal(root.get(FIELD_IS_DELETED), false));
            }
            
            // Add search filter with translation join
            if (search != null && !search.trim().isEmpty()) {
                Join<Discount, DiscountTranslation> translationJoin = root.join("translations", JoinType.LEFT);
                String searchPattern = "%" + search.trim().toLowerCase() + "%";
                // Search in name and discountCode
                predicates.add(criteriaBuilder.or(
                    criteriaBuilder.like(criteriaBuilder.lower(translationJoin.get("name")), searchPattern),
                    criteriaBuilder.like(criteriaBuilder.lower(root.get(FIELD_DISCOUNT_CODE)), searchPattern)
                ));
                // Add distinct to avoid duplicate discounts when a discount has multiple translations
                query.distinct(true);
            }
            
            return criteriaBuilder.and(predicates.toArray(new Predicate[0]));
        };

        // Create pagination (optionally with DB sort if supported)
        Pageable pageable = (dbSortField != null)
                ? PageRequest.of(pageNumber, pageSize, Sort.by(direction, dbSortField))
                : PageRequest.of(pageNumber, pageSize);

        // If sorting by translatable name, fetch all then sort globally before slicing
        if ("name".equalsIgnoreCase(normalizedSortBy)) {
            List<Discount> allDiscounts = discountRepository.findAll(spec);

            // Batch load translations for all discounts
            List<UUID> allDiscountIds = allDiscounts.stream().map(Discount::getId).collect(Collectors.toList());
            Map<UUID, List<DiscountTranslation>> translationsMapAll = allDiscountIds.isEmpty()
                    ? Collections.emptyMap()
                    : discountTranslationRepository
                            .findByDiscountIds(allDiscountIds)
                            .stream()
                            .collect(Collectors.groupingBy(t -> t.getDiscount().getId()));

            // Batch fetch menu assignment counts
            Map<UUID, Integer> menuCountMap = new HashMap<>();
            if (!allDiscountIds.isEmpty()) {
                List<Object[]> menuCounts = menuDiscountMappingRepository.countMenuAssignmentsByDiscountIds(allDiscountIds);
                for (Object[] result : menuCounts) {
                    UUID discountId = (UUID) result[0];
                    Long count = (Long) result[1];
                    menuCountMap.put(discountId, count.intValue());
                }
            }
            
            // Batch fetch promotion existence
            final Set<UUID> discountIdsWithPromotions = allDiscountIds.isEmpty()
                    ? new HashSet<>()
                    : new HashSet<>(promotionRepository.findDiscountIdsWithPromotions(allDiscountIds));
            
            String currency = restaurantChainConfigProperties.getChain() != null ? 
                restaurantChainConfigProperties.getChain().getCurrency() : null;

            // Map to responses
            List<DiscountListData> allResponses = allDiscounts.stream()
                    .map(discount -> {
                        List<DiscountTranslation> discountTranslations = translationsMapAll.getOrDefault(discount.getId(), Collections.emptyList());
                        List<DiscountTranslationResponse> translationDTOs = List.of(
                                buildResolvedDiscountTranslation(discountTranslations, locale, discount.getDiscountCode()));

                        // Get creator and updater names with null checks
                        String createdByName = null;
                        String updatedByName = null;
                        if (discount.getCreatedBy() != null) {
                            User createdByUser = discount.getCreatedBy();
                            createdByName = (createdByUser.getFirstName() != null ? createdByUser.getFirstName() : "") + 
                                " " + (createdByUser.getLastName() != null ? createdByUser.getLastName() : "").trim();
                        }
                        if (discount.getUpdatedBy() != null) {
                            User updatedByUser = discount.getUpdatedBy();
                            updatedByName = (updatedByUser.getFirstName() != null ? updatedByUser.getFirstName() : "") + 
                                " " + (updatedByUser.getLastName() != null ? updatedByUser.getLastName() : "").trim();
                        }

                        // Get BXGY item information from the first free item if exists
                        UUID purchasedItemId = null;
                        UUID freeItemId = null;
                        if (!discount.getBxgyItems().isEmpty()) {
                            DiscountBxgyItem bxgyItem = discount.getBxgyItems().get(0);
                            if (bxgyItem.getBuyItemMapping() != null) {
                                purchasedItemId = bxgyItem.getBuyItemMapping().getItem().getId();
                            }
                            if (bxgyItem.getGetItemMapping() != null) {
                                freeItemId = bxgyItem.getGetItemMapping().getItem().getId();
                            }
                        }

                        // Get menu assigned count from batch-fetched map
                        int menuAssignedCount = menuCountMap.getOrDefault(discount.getId(), 0);
                        // Get promotion existence from batch-fetched set
                        boolean assignedToPromotion = discountIdsWithPromotions.contains(discount.getId());

                        return DiscountListData.builder()
                                .id(discount.getId())
                                .discountCode(discount.getDiscountCode())
                                .discountType(discount.getDiscountType())
                                .appliedTo(discount.getAppliedTo())
                                .value(discount.getValue() != null ? CurrencyFormatter.formatAmount(discount.getValue(), currency) : null)
                                .orderValueThreshold(discount.getOrderValueThreshold() != null ? CurrencyFormatter.formatAmount(discount.getOrderValueThreshold(), currency) : null)
                                .maxDiscountValue(discount.getMaxDiscountValue() != null ? CurrencyFormatter.formatAmount(discount.getMaxDiscountValue(), currency) : null)
                                .buyQuantity(discount.getBuyQuantity())
                                .getQuantity(discount.getGetQuantity())
                                .maxUses(discount.getMaxUses())
                                .currentUsage(discount.getCurrentUsage())
                                .purchasedItemId(purchasedItemId)
                                .freeItemId(freeItemId)
                                .status(discount.getStatus())
                                .isDeleted(discount.getIsDeleted())
                                .createdAt(discount.getCreatedAt() != null ? discount.getCreatedAt().toLocalDateTime() : null)
                                .createdBy(createdByName)
                                .updatedAt(discount.getUpdatedAt() != null ? discount.getUpdatedAt().toLocalDateTime() : null)
                                .updatedBy(updatedByName)
                                .translations(translationDTOs)
                                .menuAssignedCount(menuAssignedCount)
                                .assignedToPromotion(assignedToPromotion)
                                .build();
                    })
                    .collect(Collectors.toList());

            // Global sort by locale-aware name
            LocaleContextHolder.setLocale(userLocale);
            LocaleSortUtil.sortName(allResponses, normalizedSortBy, direction);

            // Manual pagination slice
            int fromIndex = Math.min(pageNumber * pageSize, allResponses.size());
            int toIndex = Math.min(fromIndex + pageSize, allResponses.size());
            List<DiscountListData> pagedResponses = allResponses.subList(fromIndex, toIndex);

            PaginationMetaData metaData = PaginationMetaData.builder()
                    .page(pageNumber + 1)
                    .size(pageSize)
                    .totalPages((int) Math.ceil((double) allResponses.size() / pageSize))
                    .totalRecords((long) allResponses.size())
                    .build();

            DiscountListResponse listResponse = DiscountListResponse.builder()
                    .discounts(pagedResponses)
                    .count((long) pagedResponses.size())
                    .total((long) allResponses.size())
                    .metaData(metaData)
                    .build();

            return ResponseDto.<DiscountListResponse>builder()
                    .data(listResponse)
                    .message(messageUtil.getMessage(MSG_DISCOUNTS_LIST_SUCCESS, userLocale))
                    .build();
        }

        // Otherwise, use DB-level pagination + sorting
        Page<Discount> discountPage = discountRepository.findAll(spec, pageable);
        List<Discount> discounts = discountPage.getContent();

        // Batch load all translations to avoid N+1 queries
        List<UUID> discountIds = discounts.stream()
                .map(Discount::getId)
                .collect(Collectors.toList());
        
        Map<UUID, List<DiscountTranslation>> translationsMap = discountTranslationRepository
                .findByDiscountIds(discountIds)
                .stream()
                .collect(Collectors.groupingBy(t -> t.getDiscount().getId()));

        // Batch fetch menu assignment counts
        Map<UUID, Integer> menuCountMap = new HashMap<>();
        if (!discountIds.isEmpty()) {
            List<Object[]> menuCounts = menuDiscountMappingRepository.countMenuAssignmentsByDiscountIds(discountIds);
            for (Object[] result : menuCounts) {
                UUID discountId = (UUID) result[0];
                Long count = (Long) result[1];
                menuCountMap.put(discountId, count.intValue());
            }
        }
        
        // Batch fetch promotion existence
        final Set<UUID> discountIdsWithPromotions = discountIds.isEmpty()
                ? new HashSet<>()
                : new HashSet<>(promotionRepository.findDiscountIdsWithPromotions(discountIds));
        
        String currency = restaurantChainConfigProperties.getChain() != null ? 
            restaurantChainConfigProperties.getChain().getCurrency() : null;

        // Convert to response DTOs with inline mapping
        List<DiscountListData> discountResponses = discounts.stream()
                .map(discount -> {
                    // Get translations from batch-loaded map
                    List<DiscountTranslation> discountTranslations = translationsMap.getOrDefault(discount.getId(), Collections.emptyList());
                    List<DiscountTranslationResponse> translationDTOs = List.of(
                            buildResolvedDiscountTranslation(discountTranslations, locale, discount.getDiscountCode()));

                    // Get creator and updater names with null checks
                    String createdByName = null;
                    String updatedByName = null;

                    if (discount.getCreatedBy() != null) {
                        User createdByUser = discount.getCreatedBy();
                        createdByName = (createdByUser.getFirstName() != null ? createdByUser.getFirstName() : "") + 
                            " " + (createdByUser.getLastName() != null ? createdByUser.getLastName() : "").trim();
                    }

                    if (discount.getUpdatedBy() != null) {
                        User updatedByUser = discount.getUpdatedBy();
                        updatedByName = (updatedByUser.getFirstName() != null ? updatedByUser.getFirstName() : "") + 
                            " " + (updatedByUser.getLastName() != null ? updatedByUser.getLastName() : "").trim();
                    }

                    // Get BXGY item information from the first free item if exists
                    UUID purchasedItemId = null;
                    UUID freeItemId = null;
                    if (!discount.getBxgyItems().isEmpty()) {
                        DiscountBxgyItem bxgyItem = discount.getBxgyItems().get(0);
                        if (bxgyItem.getBuyItemMapping() != null) {
                            purchasedItemId = bxgyItem.getBuyItemMapping().getItem().getId();
                        }
                        if (bxgyItem.getGetItemMapping() != null) {
                            freeItemId = bxgyItem.getGetItemMapping().getItem().getId();
                        }
                    }

                    // Get menu assigned count from batch-fetched map
                    int menuAssignedCount = menuCountMap.getOrDefault(discount.getId(), 0);
                    // Get promotion existence from batch-fetched set
                    boolean assignedToPromotion = discountIdsWithPromotions.contains(discount.getId());

                    return DiscountListData.builder()
                            .id(discount.getId())
                            .discountCode(discount.getDiscountCode())
                            .discountType(discount.getDiscountType())
                            .appliedTo(discount.getAppliedTo())
                            .value(discount.getValue() != null ? CurrencyFormatter.formatAmount(discount.getValue(), currency) : null)
                            .orderValueThreshold(discount.getOrderValueThreshold() != null ? CurrencyFormatter.formatAmount(discount.getOrderValueThreshold(), currency) : null)
                            .maxDiscountValue(discount.getMaxDiscountValue() != null ? CurrencyFormatter.formatAmount(discount.getMaxDiscountValue(), currency) : null)
                            .buyQuantity(discount.getBuyQuantity())
                            .getQuantity(discount.getGetQuantity())
                            .maxUses(discount.getMaxUses())
                            .currentUsage(discount.getCurrentUsage())
                            .purchasedItemId(purchasedItemId)
                            .freeItemId(freeItemId)
                            .status(discount.getStatus())
                            .isDeleted(discount.getIsDeleted())
                            .createdAt(discount.getCreatedAt() != null ? discount.getCreatedAt().toLocalDateTime() : null)
                            .createdBy(createdByName)
                            .updatedAt(discount.getUpdatedAt() != null ? discount.getUpdatedAt().toLocalDateTime() : null)
                            .updatedBy(updatedByName)
                            .translations(translationDTOs)
                            .menuAssignedCount(menuAssignedCount)
                            .assignedToPromotion(assignedToPromotion)
                            .build();
                })
                .collect(Collectors.toList());

        // No in-memory resort when DB sorting is applied

        // Build pagination metadata from actual page data
        PaginationMetaData metaData = PaginationMetaData.builder()
                .page(discountPage.getNumber() + 1)
                .size(discountPage.getSize())
                .totalPages(discountPage.getTotalPages())
                .totalRecords(discountPage.getTotalElements())
                .build();

        // Build final response
        DiscountListResponse listResponse = DiscountListResponse.builder()
                .discounts(discountResponses)
                .count((long) discountResponses.size())
                .total(discountPage.getTotalElements())
                .metaData(metaData)
                .build();

        return ResponseDto.<DiscountListResponse>builder()
                .data(listResponse)
                .message(messageUtil.getMessage(MSG_DISCOUNTS_LIST_SUCCESS, userLocale))
                .build();
    }

    /**
     * Retrieves a paginated and filterable list of discounts assigned to a specific restaurant.
     * Filters discounts through restaurant discount mappings and supports filtering by status,
     * discount type, applied to type, and search term. Results are sorted and paginated.
     *
     * @param restaurantId the UUID of the restaurant to get discounts for
     * @param page         page number for pagination
     * @param size         page size for pagination
     * @param status       optional status filter (string format)
     * @param search       optional search term for discount code and name
     * @param sortBy       field to sort by
     * @param discountType optional comma-separated discount types to filter by
     * @param appliedTo    optional comma-separated applied to types to filter by
     * @param direction    sort direction
     * @param locale       locale code for localized responses and sorting
     * @return ResponseDto containing paginated list of restaurant discounts
     * @throws ResponseStatusException if restaurant not found, locale invalid, or validation fails
     */
    @Override
    @Transactional
    public ResponseDto<DiscountListResponse> getRestaurantDiscounts(
            UUID restaurantId,
            Integer page,
            Integer size,
            String status,
            String search,
            String sortBy,
            String discountType,
            String appliedTo,
            Sort.Direction direction,
            String locale
    ) {
        log.info("Fetching discounts for restaurant {} with language: {}, search: {}", restaurantId, locale, search);
        Locale userLocale = Locale.forLanguageTag(locale);

        // Validate locale
        if (!localizationProperties.getLanguages().contains(locale)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    messageUtil.getMessage(MSG_DISCOUNT_ERROR_INVALID_LANGUAGE, userLocale));
        }

        // Validate and process status filter
        EntityStatus statusFilter = null;
        if (status != null && !status.trim().isEmpty()) {
            try {
                statusFilter = EntityStatus.valueOf(status.toUpperCase());
            } catch (IllegalArgumentException e) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        messageUtil.getMessage("discount.error.invalid.status", userLocale));
            }
        }

        // Validate restaurant exists
        Restaurant restaurant = restaurantRepository.findById(restaurantId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        messageUtil.getMessage(MSG_RESTAURANT_NOT_FOUND, userLocale)));

        // Get all restaurant discount mappings for this restaurant
        List<RestaurantDiscountMapping> restaurantDiscountMappings = 
                restaurantDiscountMappingRepository.findById_RestaurantId(restaurantId);
        
        // Filter by status if provided
        final EntityStatus finalStatusFilter = statusFilter;
        if (finalStatusFilter != null) {
            restaurantDiscountMappings = restaurantDiscountMappings.stream()
                    .filter(mapping -> finalStatusFilter.equals(mapping.getStatus()))
                    .collect(Collectors.toList());
        }
        
        if (restaurantDiscountMappings == null || restaurantDiscountMappings.isEmpty()) {
            // Return empty response
            PaginationMetaData metaData = PaginationMetaData.builder()
                    .page(1)
                    .size(size != null ? size : 10)
                    .totalPages(0)
                    .totalRecords(0L)
                    .build();

            DiscountListResponse emptyResponse = DiscountListResponse.builder()
                    .discounts(new ArrayList<>())
                    .count(0L)
                    .total(0L)
                    .metaData(metaData)
                    .build();

            return ResponseDto.<DiscountListResponse>builder()
                    .data(emptyResponse)
                    .message(messageUtil.getMessage(MSG_DISCOUNTS_LIST_SUCCESS, userLocale))
                    .build();
        }

        // Extract discount IDs
        List<UUID> discountIds = restaurantDiscountMappings.stream()
                .map(mapping -> mapping.getId().getDiscountId())
                .collect(Collectors.toList());

        // Fetch all discounts with translations and user relationships eagerly loaded (optimized to avoid N+1 queries)
        List<Discount> discounts = discountIds.isEmpty() ? new ArrayList<>() : 
                discountRepository.findAllByIdWithRelations(discountIds);

        // Batch fetch BXGY items separately (cannot fetch multiple bags in one query due to Hibernate limitation)
        // This is still efficient - only 2 queries total instead of N+1
        if (!discountIds.isEmpty()) {
            List<DiscountBxgyItem> bxgyItems = discountBxgyItemRepository.findByDiscountIdsWithRelations(discountIds);
            // Group BXGY items by discount ID and set them on the discount entities
            Map<UUID, List<DiscountBxgyItem>> bxgyItemsByDiscountId = bxgyItems.stream()
                    .collect(Collectors.groupingBy(item -> item.getDiscount().getId()));
            
            // Set BXGY items on each discount to avoid lazy loading
            for (Discount discount : discounts) {
                List<DiscountBxgyItem> discountBxgyItems = bxgyItemsByDiscountId.getOrDefault(discount.getId(), new ArrayList<>());
                discount.getBxgyItems().clear();
                discount.getBxgyItems().addAll(discountBxgyItems);
            }
        }

        // Convert string parameters to enums with comma-separated support
        final Set<DiscountType> discountTypes;
        if (discountType != null && !discountType.trim().isEmpty()) {
            try {
                Set<DiscountType> tempDiscountTypes = Arrays.stream(discountType.split(","))
                        .map(String::trim)
                        .filter(s -> !s.isBlank())
                        .map(s -> DiscountType.valueOf(s.toUpperCase()))
                        .collect(Collectors.toSet());
                discountTypes = tempDiscountTypes.isEmpty() ? null : tempDiscountTypes;
            } catch (IllegalArgumentException e) {
                String errorMessage = messageUtil.getMessage(MSG_ERROR_INVALID_DISCOUNT_TYPE, userLocale, discountType);
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, errorMessage);
            }
        } else {
            discountTypes = null;
        }
        
        final Set<AppliedTo> appliedToTypes;
        if (appliedTo != null && !appliedTo.trim().isEmpty()) {
            try {
                Set<AppliedTo> tempAppliedToTypes = Arrays.stream(appliedTo.split(","))
                        .map(String::trim)
                        .filter(s -> !s.isBlank())
                        .map(s -> AppliedTo.valueOf(s.toUpperCase()))
                        .collect(Collectors.toSet());
                appliedToTypes = tempAppliedToTypes.isEmpty() ? null : tempAppliedToTypes;
            } catch (IllegalArgumentException e) {
                String errorMessage = messageUtil.getMessage(MSG_ERROR_INVALID_APPLIED_TO, userLocale, appliedTo);
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, errorMessage);
            }
        } else {
            appliedToTypes = null;
        }

        // Create a map of discountId -> RestaurantDiscountMapping for quick lookup
        Map<UUID, RestaurantDiscountMapping> mappingMap = restaurantDiscountMappings.stream()
                .collect(Collectors.toMap(
                        mapping -> mapping.getId().getDiscountId(),
                        mapping -> mapping
                ));

        // Convert to response DTOs and combine data from both tables
        List<DiscountListData> discountResponses = discounts.stream()
                .map(discount -> {
                    // Get restaurant-specific mapping
                    RestaurantDiscountMapping restaurantMapping = mappingMap.get(discount.getId());

                    // Apply filters
                    // Discount type filter
                    if (discountTypes != null && !discountTypes.contains(discount.getDiscountType())) {
                        return null;
                    }

                    // AppliedTo filter
                    if (appliedToTypes != null && !appliedToTypes.contains(discount.getAppliedTo())) {
                        return null;
                    }

                    // Search filter (discount name in translations OR discountCode)
                    if (search != null && !search.trim().isEmpty()) {
                        String searchTerm = search.trim().toLowerCase();
                        boolean matches = false;
                        
                        // Check discount code
                        if (discount.getDiscountCode() != null &&
                            discount.getDiscountCode().toLowerCase().contains(searchTerm)) {
                            matches = true;
                        }

                        // Check discount name in translations
                        if (!matches && discount.getTranslations() != null) {
                            for (DiscountTranslation translation : discount.getTranslations()) {
                                if (translation.getName() != null && 
                                    translation.getName().toLowerCase().contains(searchTerm)) {
                                    matches = true;
                                    break;
                                }
                            }
                        }
                        
                        if (!matches) {
                            return null;
                        }
                    }

                    List<DiscountTranslationResponse> translationDTOs = List.of(
                            buildResolvedDiscountTranslation(discount.getTranslations(), locale, discount.getDiscountCode()));

                    // Get BXGY item information from the first free item if exists
                    UUID purchasedItemId = null;
                    UUID freeItemId = null;
                    if (!discount.getBxgyItems().isEmpty()) {
                        DiscountBxgyItem bxgyItem = discount.getBxgyItems().get(0);
                        if (bxgyItem.getBuyItemMapping() != null) {
                            purchasedItemId = bxgyItem.getBuyItemMapping().getItem().getId();
                        }
                        if (bxgyItem.getGetItemMapping() != null) {
                            freeItemId = bxgyItem.getGetItemMapping().getItem().getId();
                        }
                    }

                    // Get creator and updater names with null checks
                    String createdByName = formatUserName(discount.getCreatedBy());
                    String updatedByName = formatUserName(discount.getUpdatedBy());
                    String currency = restaurantChainConfigProperties.getChain() != null ? restaurantChainConfigProperties.getChain().getCurrency() : null;

                    // Build response with data from RestaurantDiscountMapping for validity fields
                    return DiscountListData.builder()
                            .id(discount.getId())
                            .discountCode(discount.getDiscountCode())
                            .discountType(discount.getDiscountType())
                            .appliedTo(discount.getAppliedTo())
                            .value(discount.getValue() != null ? CurrencyFormatter.formatAmount(discount.getValue(), currency) : null)
                            .orderValueThreshold(discount.getOrderValueThreshold() != null ? CurrencyFormatter.formatAmount(discount.getOrderValueThreshold(), currency) : null)
                            .maxDiscountValue(discount.getMaxDiscountValue() != null ? CurrencyFormatter.formatAmount(discount.getMaxDiscountValue(), currency) : null)
                            .buyQuantity(discount.getBuyQuantity())
                            .getQuantity(discount.getGetQuantity())
                            .maxUses(discount.getMaxUses())
                            .currentUsage(discount.getCurrentUsage())
                            // Get validity fields from RestaurantDiscountMapping
                            .validFrom(restaurantMapping != null ? restaurantMapping.getValidFrom() : null)
                            .validTo(restaurantMapping != null ? restaurantMapping.getValidTo() : null)
                            .daysOfWeek(restaurantMapping != null ? restaurantMapping.getDaysOfWeek() : null)
                            .startTime(restaurantMapping != null ? restaurantMapping.getStartTime() : null)
                            .endTime(restaurantMapping != null ? restaurantMapping.getEndTime() : null)
                            .purchasedItemId(purchasedItemId)
                            .freeItemId(freeItemId)
                            .status(restaurantMapping != null ? restaurantMapping.getStatus() : null)
                            .isHide(restaurantMapping != null ? restaurantMapping.getIsHide() : null)
                            .isDeleted(discount.getIsDeleted())
                            .createdAt(discount.getCreatedAt() != null ? discount.getCreatedAt().toLocalDateTime() : null)
                            .createdBy(createdByName)
                            .updatedAt(discount.getUpdatedAt() != null ? discount.getUpdatedAt().toLocalDateTime() : null)
                            .updatedBy(updatedByName)
                            .translations(translationDTOs)
                            .build();
                })
                .filter(Objects::nonNull) // Remove null entries from filters
                .collect(Collectors.toList());

        // Apply sorting using shared library method
        if ("name".equalsIgnoreCase(sortBy)) {
            // Set the locale in context for LocaleSortUtil
            LocaleContextHolder.setLocale(userLocale);
            LocaleSortUtil.sortName(discountResponses, sortBy, direction);
        } else {
            // createdAt or any other sort field: sort by createdAt (same comparator as previous createdAt/default branches)
            Comparator<DiscountListData> comp = Comparator.comparing(
                    DiscountListData::getCreatedAt,
                    Comparator.nullsLast(Comparator.naturalOrder()));
            if (direction == Sort.Direction.DESC) comp = comp.reversed();
            discountResponses.sort(comp);
        }

        // Validate and set pagination
        int pageNumber = (page != null ? page : 1) - 1;
        if (pageNumber < 0) pageNumber = 0;
        int pageSize = size != null ? size : 10;
        if (pageSize < 1) pageSize = 10;

        // Apply pagination
        int fromIndex = pageNumber * pageSize;
        int toIndex = Math.min(fromIndex + pageSize, discountResponses.size());
        List<DiscountListData> paginatedResponses = discountResponses.subList(fromIndex, toIndex);

        // Build pagination metadata
        PaginationMetaData metaData = PaginationMetaData.builder()
                .page(pageNumber + 1)
                .size(pageSize)
                .totalPages((int) Math.ceil((double) discountResponses.size() / pageSize))
                .totalRecords((long) discountResponses.size())
                .build();

        // Build final response
        DiscountListResponse listResponse = DiscountListResponse.builder()
                .discounts(paginatedResponses)
                .count((long) paginatedResponses.size())
                .total((long) discountResponses.size())
                .metaData(metaData)
                .build();

        return ResponseDto.<DiscountListResponse>builder()
                .data(listResponse)
                .message(messageUtil.getMessage(MSG_DISCOUNTS_LIST_SUCCESS, userLocale))
                .build();
    }

    /**
     * Retrieves detailed information about a discount assigned to a specific restaurant.
     * Includes restaurant-specific validity dates, times, schedule, and BXGY item details if applicable.
     *
     * @param restaurantId the UUID of the restaurant
     * @param discountId   the UUID of the discount
     * @param locale        locale code for localized responses
     * @return ResponseDto containing detailed restaurant discount information
     * @throws ResponseStatusException if restaurant not found, discount not found, or discount not assigned to restaurant
     */
    @Override
    @Transactional(readOnly = true)
    public ResponseDto<RestaurantDiscountDetailsResponse> getRestaurantDiscountDetails(
            UUID restaurantId,
            UUID discountId,
            String locale,
            String orderType) {
        
        log.info("Fetching discount details for restaurant {} and discount {} with language: {}, orderType: {}", 
                restaurantId, discountId, locale, orderType);
        Locale userLocale = Locale.forLanguageTag(locale);

        // Parse orderType if provided
        com.gulfnet.shared_library.enums.ItemOrderType orderTypeFilter = null;
        if (orderType != null && !orderType.trim().isEmpty()) {
            try {
                orderTypeFilter = com.gulfnet.shared_library.enums.ItemOrderType.valueOf(orderType.toUpperCase());
            } catch (IllegalArgumentException e) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        messageUtil.getMessage("item.error.invalid.orderType", userLocale));
            }
        }

        // Validate locale
        if (!localizationProperties.getLanguages().contains(locale)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    messageUtil.getMessage(MSG_DISCOUNT_ERROR_INVALID_LANGUAGE, userLocale));
        }

        // Validate restaurant exists
        Restaurant restaurant = restaurantRepository.findById(restaurantId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        messageUtil.getMessage(MSG_RESTAURANT_NOT_FOUND, userLocale)));

        // Validate discount exists and fetch with relationships (optimized)
        List<Discount> discounts = discountRepository.findAllByIdWithRelations(List.of(discountId));
        if (discounts.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                    messageUtil.getMessage(MSG_DISCOUNT_NOT_FOUND, userLocale));
        }
        Discount discount = discounts.get(0);
        
        // Batch fetch BXGY items if needed (will be used later for BXGY discount details)
        List<DiscountBxgyItem> bxgyItemsList = new ArrayList<>();
        if (discount.getDiscountType() == DiscountType.BXGY) {
            bxgyItemsList = discountBxgyItemRepository.findByDiscountIdsWithRelations(List.of(discountId));
        }

        // Check if discount is assigned to this restaurant
        RestaurantDiscountId restaurantDiscountId = new RestaurantDiscountId();
        restaurantDiscountId.setRestaurantId(restaurantId);
        restaurantDiscountId.setDiscountId(discountId);
        
        RestaurantDiscountMapping restaurantMapping = restaurantDiscountMappingRepository.findById(restaurantDiscountId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        messageUtil.getMessage(MSG_DISCOUNT_NOT_ASSIGNED_TO_RESTAURANT, userLocale)));

        // Get restaurant-specific validity from RestaurantDiscountMapping
        OffsetDateTime validFrom = restaurantMapping.getValidFrom();
        OffsetDateTime validTo = restaurantMapping.getValidTo();
        OffsetTime startTime = restaurantMapping.getStartTime();
        OffsetTime endTime = restaurantMapping.getEndTime();
        List<DayOfWeek> daysOfWeek = restaurantMapping.getDaysOfWeek();
        Boolean isHide = restaurantMapping.getIsHide();

        // Get menu for this restaurant (to filter categories/items)
        List<RestaurantMenuMapping> restaurantMenuMappings = restaurantMenuMappingRepository.findById_RestaurantId(restaurantId);
        if (restaurantMenuMappings == null || restaurantMenuMappings.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                    messageUtil.getMessage(MSG_RESTAURANT_MENU_NOT_FOUND, userLocale));
        }
        
        // Get all menu IDs for this restaurant
        List<UUID> menuIds = restaurantMenuMappings.stream()
                .map(mapping -> mapping.getId().getMenuId())
                .collect(Collectors.toList());

        List<DiscountTranslationResponse> translationDTOs = List.of(
                buildResolvedDiscountTranslation(discount.getTranslations(), locale, discount.getDiscountCode()));

        // Get creator and updater names
        String createdByName = formatUserName(discount.getCreatedBy());
        String updatedByName = formatUserName(discount.getUpdatedBy());

        // Initialize response lists
        List<RestaurantDiscountDetailsResponse.CategoryInfo> categories = new ArrayList<>();
        List<RestaurantDiscountDetailsResponse.ItemInfo> items = new ArrayList<>();
        List<RestaurantDiscountDetailsResponse.BxgyItemInfo> buyItems = new ArrayList<>();
        List<RestaurantDiscountDetailsResponse.BxgyItemInfo> getItems = new ArrayList<>();

        // Get category/item/BXGY details based on appliedTo and discountType
        if (discount.getAppliedTo() == AppliedTo.CATEGORY) {
            // Get all category discount mappings for this discount
            List<CategoryDiscountMapping> categoryMappings = categoryDiscountMappingRepository.findByDiscount(discount);
            
            // Filter by menus assigned to this restaurant
            Set<UUID> categoryIds = new HashSet<>();
            for (CategoryDiscountMapping mapping : categoryMappings) {
                MenuCategoryMapping mcm = mapping.getMenuCategoryMapping();
                if (mcm != null && menuIds.contains(mcm.getMenu().getId())) {
                    categoryIds.add(mcm.getCategory().getId());
                }
            }

            // Batch fetch category translations
            if (!categoryIds.isEmpty()) {
                List<CategoryTranslation> categoryTranslations = categoryTranslationRepository.findAllByCategoryIdIn(new ArrayList<>(categoryIds));
                Map<UUID, List<CategoryTranslation>> translationsByCategoryId = categoryTranslations.stream()
                        .collect(Collectors.groupingBy(t -> t.getCategory().getId()));

                for (UUID categoryId : categoryIds) {
                    List<CategoryTranslation> catTranslations = translationsByCategoryId.getOrDefault(categoryId, new ArrayList<>());
                    String categoryName = "";
                    
                    if (!catTranslations.isEmpty()) {
                        CategoryTranslation exactMatch = catTranslations.stream()
                            .filter(t -> t.getLanguageCode() != null && t.getLanguageCode().equals(locale))
                            .findFirst()
                            .orElse(null);
                        
                        if (exactMatch != null) {
                            categoryName = exactMatch.getName();
                        } else {
                            Optional<CategoryTranslation> fallback = TranslationUtils.pickPreferredOrFromList(
                                    catTranslations,
                                    locale,
                                    localizationProperties.getLanguages(),
                                    CategoryTranslation::getLanguageCode
                            );
                            categoryName = fallback.map(CategoryTranslation::getName).orElse("");
                        }
                    }

                    categories.add(RestaurantDiscountDetailsResponse.CategoryInfo.builder()
                            .id(categoryId)
                            .name(categoryName)
                            .build());
                }
            }
        } else if (discount.getAppliedTo() == AppliedTo.ITEM) {
            if (discount.getDiscountType() == DiscountType.BXGY) {
                // BXGY discount - get buy and get items (use batch-fetched list)
                List<DiscountBxgyItem> bxgyItems = bxgyItemsList;
                
                Set<UUID> buyItemIds = new HashSet<>();
                Set<UUID> getItemIds = new HashSet<>();
                Map<UUID, CategoryItemMapping> buyItemIdToCategoryItemMapping = new HashMap<>();
                Map<UUID, CategoryItemMapping> getItemIdToCategoryItemMapping = new HashMap<>();
                
                for (DiscountBxgyItem bxgyItem : bxgyItems) {
                    if (bxgyItem.getBuyItemMapping() != null && bxgyItem.getBuyItemMapping().getItem() != null) {
                        // Check if this item is in a menu assigned to this restaurant
                        CategoryItemMapping cim = bxgyItem.getBuyItemMapping();
                        if (cim.getMenuCategoryMapping() != null && 
                            menuIds.contains(cim.getMenuCategoryMapping().getMenu().getId())) {
                            UUID buyItemId = cim.getItem().getId();
                            buyItemIds.add(buyItemId);
                            buyItemIdToCategoryItemMapping.putIfAbsent(buyItemId, cim);
                        }
                    }
                    if (bxgyItem.getGetItemMapping() != null && bxgyItem.getGetItemMapping().getItem() != null) {
                        CategoryItemMapping cim = bxgyItem.getGetItemMapping();
                        if (cim.getMenuCategoryMapping() != null && 
                            menuIds.contains(cim.getMenuCategoryMapping().getMenu().getId())) {
                            UUID getItemId = cim.getItem().getId();
                            getItemIds.add(getItemId);
                            getItemIdToCategoryItemMapping.putIfAbsent(getItemId, cim);
                        }
                    }
                }

                // Batch fetch item translations and item details for buy items
                if (!buyItemIds.isEmpty()) {
                    // Batch fetch items with basePrice and imageUrl
                    List<Item> fetchedItems = itemRepository.findAllById(new ArrayList<>(buyItemIds));
                    Map<UUID, Item> itemMap = fetchedItems.stream()
                            .collect(Collectors.toMap(Item::getId, item -> item));
                    
                    List<ItemTranslation> itemTranslations = itemTranslationRepository.findAllByItemIdIn(new ArrayList<>(buyItemIds));
                    Map<UUID, List<ItemTranslation>> translationsByItemId = itemTranslations.stream()
                            .collect(Collectors.groupingBy(t -> t.getItem().getId()));

                    for (UUID itemId : buyItemIds) {
                        List<ItemTranslation> itemTrans = translationsByItemId.getOrDefault(itemId, new ArrayList<>());
                        String itemName = "";
                        
                        if (!itemTrans.isEmpty()) {
                            ItemTranslation exactMatch = itemTrans.stream()
                                .filter(t -> t.getLanguageCode() != null && t.getLanguageCode().equals(locale))
                                .findFirst()
                                .orElse(null);
                            
                            if (exactMatch != null) {
                                itemName = exactMatch.getName();
                            } else {
                                Optional<ItemTranslation> fallback = TranslationUtils.pickPreferredOrFromList(
                                        itemTrans,
                                        locale,
                                        localizationProperties.getLanguages(),
                                        ItemTranslation::getLanguageCode
                                );
                                itemName = fallback.map(ItemTranslation::getName).orElse("");
                            }
                        }

                        // Get item details
                        Item item = itemMap.get(itemId);
                        BigDecimal basePrice = null;
                        String imageUrl = null;
                        com.gulfnet.shared_library.enums.AlcoholType alcoholType = null;
                        if (item != null) {
                            // Filter by orderType if provided
                            if (orderTypeFilter != null) {
                                CategoryItemMapping cim = buyItemIdToCategoryItemMapping.get(itemId);
                                com.gulfnet.shared_library.enums.ItemOrderType itemOrderType = cim != null && cim.getItemOrderType() != null
                                        ? cim.getItemOrderType()
                                        : com.gulfnet.shared_library.enums.ItemOrderType.BOTH;
                                // Include item if:
                                // - orderType == DINE_IN: itemOrderType == DINE_IN OR BOTH
                                // - orderType == TAKEAWAY: itemOrderType == TAKEAWAY OR BOTH
                                if (orderTypeFilter == com.gulfnet.shared_library.enums.ItemOrderType.DINE_IN) {
                                    if (itemOrderType != com.gulfnet.shared_library.enums.ItemOrderType.DINE_IN && 
                                        itemOrderType != com.gulfnet.shared_library.enums.ItemOrderType.BOTH) {
                                        continue; // Skip this item
                                    }
                                } else if (orderTypeFilter == com.gulfnet.shared_library.enums.ItemOrderType.TAKEAWAY) {
                                    if (itemOrderType != com.gulfnet.shared_library.enums.ItemOrderType.TAKEAWAY && 
                                        itemOrderType != com.gulfnet.shared_library.enums.ItemOrderType.BOTH) {
                                        continue; // Skip this item
                                    }
                                }
                            }
                            
                            if (item.getBasePrice() != null) {
                                basePrice = BigDecimal.valueOf(item.getBasePrice());
                            }
                            // Generate presigned URL for image
                            if (item.getImageUrl() != null && !item.getImageUrl().isEmpty()) {
                                imageUrl = awsService.getPreSignedUrl(item.getImageUrl());
                            }
                            alcoholType = item.getAlcoholType();
                        }

                        buyItems.add(RestaurantDiscountDetailsResponse.BxgyItemInfo.builder()
                                .id(itemId)
                                .name(itemName)
                                .basePrice(basePrice)
                                .imageUrl(imageUrl)
                                .alcoholType(alcoholType)
                                .build());
                    }
                }

                // Batch fetch item translations and item details for get items
                if (!getItemIds.isEmpty()) {
                    // Batch fetch items with basePrice and imageUrl
                    List<Item> fetchedItems = itemRepository.findAllById(new ArrayList<>(getItemIds));
                    Map<UUID, Item> itemMap = fetchedItems.stream()
                            .collect(Collectors.toMap(Item::getId, item -> item));
                    
                    List<ItemTranslation> itemTranslations = itemTranslationRepository.findAllByItemIdIn(new ArrayList<>(getItemIds));
                    Map<UUID, List<ItemTranslation>> translationsByItemId = itemTranslations.stream()
                            .collect(Collectors.groupingBy(t -> t.getItem().getId()));

                    for (UUID itemId : getItemIds) {
                        List<ItemTranslation> itemTrans = translationsByItemId.getOrDefault(itemId, new ArrayList<>());
                        String itemName = "";
                        
                        if (!itemTrans.isEmpty()) {
                            ItemTranslation exactMatch = itemTrans.stream()
                                .filter(t -> t.getLanguageCode() != null && t.getLanguageCode().equals(locale))
                                .findFirst()
                                .orElse(null);
                            
                            if (exactMatch != null) {
                                itemName = exactMatch.getName();
                            } else {
                                Optional<ItemTranslation> fallback = TranslationUtils.pickPreferredOrFromList(
                                        itemTrans,
                                        locale,
                                        localizationProperties.getLanguages(),
                                        ItemTranslation::getLanguageCode
                                );
                                itemName = fallback.map(ItemTranslation::getName).orElse("");
                            }
                        }

                        // Get item details
                        Item item = itemMap.get(itemId);
                        BigDecimal basePrice = null;
                        String imageUrl = null;
                        com.gulfnet.shared_library.enums.AlcoholType alcoholType = null;
                        if (item != null) {
                            // Filter by orderType if provided
                            if (orderTypeFilter != null) {
                                CategoryItemMapping cim = getItemIdToCategoryItemMapping.get(itemId);
                                com.gulfnet.shared_library.enums.ItemOrderType itemOrderType = cim != null && cim.getItemOrderType() != null
                                        ? cim.getItemOrderType()
                                        : com.gulfnet.shared_library.enums.ItemOrderType.BOTH;
                                // Include item if:
                                // - orderType == DINE_IN: itemOrderType == DINE_IN OR BOTH
                                // - orderType == TAKEAWAY: itemOrderType == TAKEAWAY OR BOTH
                                if (orderTypeFilter == com.gulfnet.shared_library.enums.ItemOrderType.DINE_IN) {
                                    if (itemOrderType != com.gulfnet.shared_library.enums.ItemOrderType.DINE_IN && 
                                        itemOrderType != com.gulfnet.shared_library.enums.ItemOrderType.BOTH) {
                                        continue; // Skip this item
                                    }
                                } else if (orderTypeFilter == com.gulfnet.shared_library.enums.ItemOrderType.TAKEAWAY) {
                                    if (itemOrderType != com.gulfnet.shared_library.enums.ItemOrderType.TAKEAWAY && 
                                        itemOrderType != com.gulfnet.shared_library.enums.ItemOrderType.BOTH) {
                                        continue; // Skip this item
                                    }
                                }
                            }
                            
                            if (item.getBasePrice() != null) {
                                basePrice = BigDecimal.valueOf(item.getBasePrice());
                            }
                            // Generate presigned URL for image
                            if (item.getImageUrl() != null && !item.getImageUrl().isEmpty()) {
                                imageUrl = awsService.getPreSignedUrl(item.getImageUrl());
                            }
                            alcoholType = item.getAlcoholType();
                        }

                        getItems.add(RestaurantDiscountDetailsResponse.BxgyItemInfo.builder()
                                .id(itemId)
                                .name(itemName)
                                .basePrice(basePrice)
                                .imageUrl(imageUrl)
                                .alcoholType(alcoholType)
                                .build());
                    }
                }
            } else {
                // Regular item-level discount
                List<ItemDiscountMapping> itemMappings = itemDiscountMappingRepository.findByDiscount(discount);
                
                // Map to store itemId -> CategoryItemMapping for category lookup
                Map<UUID, CategoryItemMapping> itemIdToCategoryItemMapping = new HashMap<>();
                Set<UUID> itemIds = new HashSet<>();
                
                for (ItemDiscountMapping mapping : itemMappings) {
                    CategoryItemMapping cim = mapping.getCategoryItemMapping();
                    if (cim != null && cim.getMenuCategoryMapping() != null && 
                        menuIds.contains(cim.getMenuCategoryMapping().getMenu().getId())) {
                        UUID itemId = cim.getItem().getId();
                        itemIds.add(itemId);
                        itemIdToCategoryItemMapping.put(itemId, cim);
                    }
                }

                if (!itemIds.isEmpty()) {
                    // Batch fetch items with basePrice and imageUrl
                    List<Item> fetchedItems = itemRepository.findAllById(new ArrayList<>(itemIds));
                    Map<UUID, Item> itemMap = fetchedItems.stream()
                            .collect(Collectors.toMap(Item::getId, item -> item));
                    
                    // Batch fetch item translations
                    List<ItemTranslation> itemTranslations = itemTranslationRepository.findAllByItemIdIn(new ArrayList<>(itemIds));
                    Map<UUID, List<ItemTranslation>> translationsByItemId = itemTranslations.stream()
                            .collect(Collectors.groupingBy(t -> t.getItem().getId()));

                    // Collect category IDs for batch fetching category translations
                    Set<UUID> categoryIds = new HashSet<>();
                    for (CategoryItemMapping cim : itemIdToCategoryItemMapping.values()) {
                        if (cim.getMenuCategoryMapping() != null && 
                            cim.getMenuCategoryMapping().getCategory() != null) {
                            categoryIds.add(cim.getMenuCategoryMapping().getCategory().getId());
                        }
                    }
                    
                    // Batch fetch category translations
                    Map<UUID, List<CategoryTranslation>> categoryTranslationsMap = new HashMap<>();
                    if (!categoryIds.isEmpty()) {
                        List<CategoryTranslation> categoryTranslations = 
                                categoryTranslationRepository.findAllByCategoryIdIn(new ArrayList<>(categoryIds));
                        categoryTranslationsMap = categoryTranslations.stream()
                                .collect(Collectors.groupingBy(t -> t.getCategory().getId()));
                    }

                    for (UUID itemId : itemIds) {
                        List<ItemTranslation> itemTrans = translationsByItemId.getOrDefault(itemId, new ArrayList<>());
                        String itemName = "";
                        
                        if (!itemTrans.isEmpty()) {
                            ItemTranslation exactMatch = itemTrans.stream()
                                .filter(t -> t.getLanguageCode() != null && t.getLanguageCode().equals(locale))
                                .findFirst()
                                .orElse(null);
                            
                            if (exactMatch != null) {
                                itemName = exactMatch.getName();
                            } else {
                                Optional<ItemTranslation> fallback = TranslationUtils.pickPreferredOrFromList(
                                        itemTrans,
                                        locale,
                                        localizationProperties.getLanguages(),
                                        ItemTranslation::getLanguageCode
                                );
                                itemName = fallback.map(ItemTranslation::getName).orElse("");
                            }
                        }

                        // Get item details
                        Item item = itemMap.get(itemId);
                        BigDecimal basePrice = null;
                        String imageUrl = null;
                        com.gulfnet.shared_library.enums.AlcoholType alcoholType = null;
                        if (item != null) {
                            // Filter by orderType if provided
                            if (orderTypeFilter != null) {
                                CategoryItemMapping cim = itemIdToCategoryItemMapping.get(itemId);
                                com.gulfnet.shared_library.enums.ItemOrderType itemOrderType = cim != null && cim.getItemOrderType() != null
                                        ? cim.getItemOrderType()
                                        : com.gulfnet.shared_library.enums.ItemOrderType.BOTH;
                                // Include item if:
                                // - orderType == DINE_IN: itemOrderType == DINE_IN OR BOTH
                                // - orderType == TAKEAWAY: itemOrderType == TAKEAWAY OR BOTH
                                if (orderTypeFilter == com.gulfnet.shared_library.enums.ItemOrderType.DINE_IN) {
                                    if (itemOrderType != com.gulfnet.shared_library.enums.ItemOrderType.DINE_IN && 
                                        itemOrderType != com.gulfnet.shared_library.enums.ItemOrderType.BOTH) {
                                        continue; // Skip this item
                                    }
                                } else if (orderTypeFilter == com.gulfnet.shared_library.enums.ItemOrderType.TAKEAWAY) {
                                    if (itemOrderType != com.gulfnet.shared_library.enums.ItemOrderType.TAKEAWAY && 
                                        itemOrderType != com.gulfnet.shared_library.enums.ItemOrderType.BOTH) {
                                        continue; // Skip this item
                                    }
                                }
                            }
                            
                            if (item.getBasePrice() != null) {
                                basePrice = BigDecimal.valueOf(item.getBasePrice());
                            }
                            // Generate presigned URL for image
                            if (item.getImageUrl() != null && !item.getImageUrl().isEmpty()) {
                                imageUrl = awsService.getPreSignedUrl(item.getImageUrl());
                            }
                            alcoholType = item.getAlcoholType();
                        }

                        // Get category name
                        String categoryName = "";
                        CategoryItemMapping cim = itemIdToCategoryItemMapping.get(itemId);
                        if (cim != null && cim.getMenuCategoryMapping() != null && 
                            cim.getMenuCategoryMapping().getCategory() != null) {
                            UUID categoryId = cim.getMenuCategoryMapping().getCategory().getId();
                            List<CategoryTranslation> catTranslations = 
                                    categoryTranslationsMap.getOrDefault(categoryId, new ArrayList<>());
                            
                            if (!catTranslations.isEmpty()) {
                                CategoryTranslation exactMatch = catTranslations.stream()
                                    .filter(t -> t.getLanguageCode() != null && t.getLanguageCode().equals(locale))
                                    .findFirst()
                                    .orElse(null);
                                
                                if (exactMatch != null) {
                                    categoryName = exactMatch.getName();
                                } else {
                                    Optional<CategoryTranslation> fallback = TranslationUtils.pickPreferredOrFromList(
                                            catTranslations,
                                            locale,
                                            localizationProperties.getLanguages(),
                                            CategoryTranslation::getLanguageCode
                                    );
                                    categoryName = fallback.map(CategoryTranslation::getName).orElse("");
                                }
                            }
                        }

                        items.add(RestaurantDiscountDetailsResponse.ItemInfo.builder()
                                .id(itemId)
                                .name(itemName)
                                .categoryName(categoryName)
                                .basePrice(basePrice)
                                .imageUrl(imageUrl)
                                .alcoholType(alcoholType)
                                .build());
                    }
                }
            }
        }

        // Build response
        String currency = restaurantChainConfigProperties.getChain() != null ? restaurantChainConfigProperties.getChain().getCurrency() : null;
        RestaurantDiscountDetailsResponse response = RestaurantDiscountDetailsResponse.builder()
                .discountId(discount.getId())
                .discountCode(discount.getDiscountCode())
                .discountType(discount.getDiscountType())
                .appliedTo(discount.getAppliedTo())
                .value(discount.getValue() != null ? CurrencyFormatter.formatAmount(discount.getValue(), currency) : null)
                .orderValueThreshold(discount.getOrderValueThreshold() != null ? CurrencyFormatter.formatAmount(discount.getOrderValueThreshold(), currency) : null)
                .maxDiscountValue(discount.getMaxDiscountValue() != null ? CurrencyFormatter.formatAmount(discount.getMaxDiscountValue(), currency) : null)
                .buyQuantity(discount.getBuyQuantity())
                .getQuantity(discount.getGetQuantity())
                .maxUses(discount.getMaxUses())
                .currentUsage(discount.getCurrentUsage())
                .isDeleted(discount.getIsDeleted())
                .createdAt(discount.getCreatedAt() != null ? discount.getCreatedAt().toLocalDateTime() : null)
                .createdBy(createdByName)
                .updatedAt(discount.getUpdatedAt() != null ? discount.getUpdatedAt().toLocalDateTime() : null)
                .updatedBy(updatedByName)
                .translations(translationDTOs)
                .validFrom(validFrom)
                .validTo(validTo)
                .startTime(startTime)
                .endTime(endTime)
                .daysOfWeek(daysOfWeek)
                .isHide(isHide)
                .status(restaurantMapping.getStatus())
                .categories(categories.isEmpty() ? null : categories)
                .items(items.isEmpty() ? null : items)
                .buyItems(buyItems.isEmpty() ? null : buyItems)
                .getItems(getItems.isEmpty() ? null : getItems)
                .build();

        return ResponseDto.<RestaurantDiscountDetailsResponse>builder()
                .data(response)
                .message(messageUtil.getMessage(MSG_DISCOUNT_DETAILS_RETRIEVED_SUCCESS, userLocale))
                .build();
    }

    /**
     * Updates the validity period and schedule for a discount assigned to a restaurant.
     * Converts date/time values to UTC and updates the restaurant discount mapping.
     * Creates an audit trail for the update operation.
     *
     * @param restaurantId the UUID of the restaurant
     * @param discountId   the UUID of the discount
     * @param request       the update request with new validity dates, times, and schedule
     * @param userId        the ID of the user performing the update
     * @param locale        locale code for localized error messages
     * @return ResponseDto containing updated restaurant discount details
     * @throws ResponseStatusException if restaurant not found, discount not found, not assigned, or validation fails
     */
    @Override
    @Transactional
    public ResponseDto<RestaurantDiscountDetailsResponse> updateRestaurantDiscountValidity(
            UUID restaurantId,
            UUID discountId,
            UpdateRestaurantDiscountValidityRequest request,
            String userId,
            String locale) {
        
        log.info("Updating discount validity for restaurant {} and discount {} with language: {}", 
                restaurantId, discountId, locale);
        Locale userLocale = Locale.forLanguageTag(locale);
        
        // Fetch user
        User user = userRepository.findById(UUID.fromString(userId))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        messageUtil.getMessage(MSG_USER_NOT_FOUND, userLocale, userId)));

        // Validate locale
        if (!localizationProperties.getLanguages().contains(locale)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    messageUtil.getMessage(MSG_DISCOUNT_ERROR_INVALID_LANGUAGE, userLocale));
        }

        // Validate restaurant exists
        Restaurant restaurant = restaurantRepository.findById(restaurantId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        messageUtil.getMessage(MSG_RESTAURANT_NOT_FOUND, userLocale)));

        // Validate discount exists
        Discount discount = discountRepository.findById(discountId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        messageUtil.getMessage(MSG_DISCOUNT_NOT_FOUND, userLocale)));

        // Check if discount is assigned to this restaurant
        RestaurantDiscountId restaurantDiscountId = new RestaurantDiscountId();
        restaurantDiscountId.setRestaurantId(restaurantId);
        restaurantDiscountId.setDiscountId(discountId);
        
        RestaurantDiscountMapping restaurantMapping = restaurantDiscountMappingRepository.findById(restaurantDiscountId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        messageUtil.getMessage(MSG_DISCOUNT_NOT_ASSIGNED_TO_RESTAURANT, userLocale)));

        // Save original values to compare if dates are being changed
        OffsetDateTime originalValidFrom = restaurantMapping.getValidFrom();
        OffsetDateTime originalValidTo = restaurantMapping.getValidTo();

        // Update validity fields (convert to UTC)
        if (request.getValidFrom() != null) {
            restaurantMapping.setValidFrom(convertToUtc(request.getValidFrom()));
        }
        if (request.getValidTo() != null) {
            restaurantMapping.setValidTo(convertToUtc(request.getValidTo()));
        }
        if (request.getStartTime() != null) {
            restaurantMapping.setStartTime(convertToUtc(request.getStartTime()));
        }
        if (request.getEndTime() != null) {
            restaurantMapping.setEndTime(convertToUtc(request.getEndTime()));
        }
        if (request.getDaysOfWeek() != null) {
            restaurantMapping.setDaysOfWeek(request.getDaysOfWeek());
        }
        // Track status change for audit trail
        EntityStatus oldStatus = restaurantMapping.getStatus();
        EntityStatus newStatus = request.getStatus();
        boolean statusChanged = newStatus != null && !newStatus.equals(oldStatus);
        
        if (request.getStatus() != null) {
            restaurantMapping.setStatus(request.getStatus());
        }
        if (request.getIsHide() != null) {
            restaurantMapping.setIsHide(Boolean.TRUE.equals(request.getIsHide()));
        }

        // Validate date range and past dates
        OffsetDateTime nowUtc = OffsetDateTime.now(ZoneOffset.UTC);
        
        // Validate that validFrom is not in the past (only if it was provided AND changed in the request)
        if (request.getValidFrom() != null && restaurantMapping.getValidFrom() != null) {
            // Only validate if the date is actually being changed (not just keeping the same value)
            boolean isDateChanged = originalValidFrom == null || 
                    !restaurantMapping.getValidFrom().equals(originalValidFrom);
            if (isDateChanged && restaurantMapping.getValidFrom().isBefore(nowUtc)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, 
                        messageUtil.getMessage(MSG_DISCOUNT_ASSIGNMENT_PAST_VALID_FROM, userLocale));
            }
        }
        
        // Validate that validTo is not in the past (only if it was provided AND changed in the request)
        if (request.getValidTo() != null && restaurantMapping.getValidTo() != null) {
            // Only validate if the date is actually being changed (not just keeping the same value)
            boolean isDateChanged = originalValidTo == null || 
                    !restaurantMapping.getValidTo().equals(originalValidTo);
            if (isDateChanged && restaurantMapping.getValidTo().isBefore(nowUtc)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, 
                        messageUtil.getMessage(MSG_DISCOUNT_ASSIGNMENT_PAST_VALID_TO, userLocale));
            }
        }
        
        // Validate that validFrom is not after validTo
        if (restaurantMapping.getValidFrom() != null && restaurantMapping.getValidTo() != null &&
            restaurantMapping.getValidFrom().isAfter(restaurantMapping.getValidTo())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, 
                    messageUtil.getMessage(MSG_DISCOUNT_ERROR_INVALID_DATE_RANGE, userLocale));
        }

        // Validate time range - allow overnight schedules (e.g., 23:00 to 02:00)
        // Only reject if start and end times are the same (invalid)
        if (restaurantMapping.getStartTime() != null && restaurantMapping.getEndTime() != null 
                && restaurantMapping.getStartTime().equals(restaurantMapping.getEndTime())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    messageUtil.getMessage(MSG_DISCOUNT_ERROR_SCHEDULE_INVALID_TIME_RANGE, userLocale));
        }
        // Note: We allow overnight schedules where endTime < startTime

        /**
         * BXGY-specific validation:
         * For this restaurant, do not allow overlapping validity ranges for BXGY discounts
         * that share any of the same items.
         *
         * Example:
         *  - BXGY1 (items A,B) valid 20–23 Dec
         *  - BXGY2 (items A,B) valid 23–27 Dec
         * If BXGY1 is updated to 23–26 Dec, overlap occurs on items A,B → reject.
         */
        if (discount.getDiscountType() == DiscountType.BXGY) {
            OffsetDateTime newValidFrom = restaurantMapping.getValidFrom();
            OffsetDateTime newValidTo = restaurantMapping.getValidTo();

            // Load all restaurant-discount mappings for this restaurant (excluding current)
            List<RestaurantDiscountMapping> allRestaurantMappings =
                    restaurantDiscountMappingRepository.findById_RestaurantId(restaurantId);

            Map<UUID, RestaurantDiscountMapping> otherBxgyRestaurantMappings = allRestaurantMappings.stream()
                    .filter(m -> !m.getDiscount().getId().equals(discountId))
                    .filter(m -> m.getDiscount() != null)
                    .filter(m -> m.getDiscount().getDiscountType() == DiscountType.BXGY)
                    .filter(m -> m.getDiscount().getStatus() == EntityStatus.ACTIVE)
                    .filter(m -> !Boolean.TRUE.equals(m.getDiscount().getIsDeleted()))
                    .collect(Collectors.toMap(
                            m -> m.getDiscount().getId(),
                            m -> m,
                            (existing, replacement) -> existing
                    ));

            if (!otherBxgyRestaurantMappings.isEmpty()) {
                // Collect all BXGY discount IDs for this restaurant (current + others)
                Set<UUID> allBxgyDiscountIds = new HashSet<>(otherBxgyRestaurantMappings.keySet());
                allBxgyDiscountIds.add(discountId);

                // Load BXGY item mappings with related Item entities
                List<DiscountBxgyItem> bxgyItems =
                        discountBxgyItemRepository.findByDiscountIdsWithRelations(new ArrayList<>(allBxgyDiscountIds));

                // Map discount -> set of item IDs (buy + get), regardless of menu
                Map<UUID, Set<UUID>> discountToItemIds = new HashMap<>();
                for (DiscountBxgyItem bxgyItem : bxgyItems) {
                    if (bxgyItem.getDiscount() == null || bxgyItem.getDiscount().getId() == null) {
                        continue;
                    }
                    UUID dId = bxgyItem.getDiscount().getId();
                    Set<UUID> itemIds = discountToItemIds.computeIfAbsent(dId, k -> new HashSet<>());

                    if (bxgyItem.getBuyItemMapping() != null &&
                        bxgyItem.getBuyItemMapping().getItem() != null &&
                        bxgyItem.getBuyItemMapping().getItem().getId() != null) {
                        itemIds.add(bxgyItem.getBuyItemMapping().getItem().getId());
                    }
                    if (bxgyItem.getGetItemMapping() != null &&
                        bxgyItem.getGetItemMapping().getItem() != null &&
                        bxgyItem.getGetItemMapping().getItem().getId() != null) {
                        itemIds.add(bxgyItem.getGetItemMapping().getItem().getId());
                    }
                }

                Set<UUID> currentItemIds = discountToItemIds.getOrDefault(discountId, Collections.emptySet());

                if (!currentItemIds.isEmpty()) {
                    // Collect all conflicting item IDs where both discount items and date ranges overlap
                    Set<UUID> conflictingItemIds = new HashSet<>();

                    for (Map.Entry<UUID, RestaurantDiscountMapping> entry : otherBxgyRestaurantMappings.entrySet()) {
                        UUID otherDiscountId = entry.getKey();
                        RestaurantDiscountMapping otherMapping = entry.getValue();

                        Set<UUID> otherItemIds = discountToItemIds.getOrDefault(otherDiscountId, Collections.emptySet());
                        if (otherItemIds.isEmpty()) {
                            continue;
                        }

                        // Check item intersection first
                        Set<UUID> intersection = new HashSet<>(currentItemIds);
                        intersection.retainAll(otherItemIds);
                        if (intersection.isEmpty()) {
                            continue;
                        }

                        // Now check date range overlap between current and other restaurant mappings
                        OffsetDateTime otherValidFrom = otherMapping.getValidFrom();
                        OffsetDateTime otherValidTo = otherMapping.getValidTo();

                        boolean overlaps;
                        if (newValidFrom == null || otherValidFrom == null) {
                            // If either has no start date defined, treat as overlapping to be safe
                            overlaps = true;
                        } else if (newValidTo == null && otherValidTo == null) {
                            // Both are indefinite - overlap
                            overlaps = true;
                        } else if (newValidTo == null) {
                            // Current discount is indefinite
                            overlaps = otherValidTo == null || !newValidFrom.isAfter(otherValidTo);
                        } else if (otherValidTo == null) {
                            // Other discount is indefinite
                            overlaps = !otherValidFrom.isAfter(newValidTo);
                        } else {
                            // Standard overlap check: [A,B] and [C,D] overlap if A < D && B > C
                            overlaps = newValidFrom.isBefore(otherValidTo) && newValidTo.isAfter(otherValidFrom);
                        }

                        if (overlaps) {
                            conflictingItemIds.addAll(intersection);
                        }
                    }

                    if (!conflictingItemIds.isEmpty()) {
                        // Fetch item translations to get item names for message
                        List<ItemTranslation> itemTranslations = itemTranslationRepository
                                .findAllByItemIdIn(new ArrayList<>(conflictingItemIds));
                        Map<UUID, List<ItemTranslation>> translationsByItemId = itemTranslations.stream()
                                .collect(Collectors.groupingBy(t -> t.getItem().getId()));

                        List<String> conflictingItemNames = new ArrayList<>();
                        for (UUID itemId : conflictingItemIds) {
                            List<ItemTranslation> itemTrans = translationsByItemId
                                    .getOrDefault(itemId, Collections.emptyList());
                            String itemName = "";

                            if (!itemTrans.isEmpty()) {
                                ItemTranslation exactMatch = itemTrans.stream()
                                        .filter(t -> t.getLanguageCode() != null && t.getLanguageCode().equals(locale))
                                        .findFirst()
                                        .orElse(null);

                                if (exactMatch != null) {
                                    itemName = exactMatch.getName() != null ? exactMatch.getName() : "";
                                } else {
                                    Optional<ItemTranslation> fallback = TranslationUtils.pickPreferredOrFromList(
                                            itemTrans,
                                            locale,
                                            localizationProperties.getLanguages(),
                                            ItemTranslation::getLanguageCode
                                    );
                                    itemName = fallback.map(t -> t.getName() != null ? t.getName() : "").orElse("");
                                }
                            }

                            if (!itemName.isEmpty()) {
                                conflictingItemNames.add(itemName);
                            } else {
                                conflictingItemNames.add(itemId.toString());
                            }
                        }

                        String itemNamesList = String.join(", ", conflictingItemNames);
                        log.error("Overlapping BXGY discount validity detected for restaurant {} and discount {}. Conflicting items: {}",
                                restaurantId, discountId, itemNamesList);
                        throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                                messageUtil.getMessage(MSG_DISCOUNT_BXGY_ITEM_ALREADY_ASSIGNED_TO_OTHER_DISCOUNT, userLocale, itemNamesList));
                    }
                }
            }
        }

        // Save updated mapping
        restaurantDiscountMappingRepository.save(restaurantMapping);
        log.info("Updated discount validity for restaurant {} and discount {}", restaurantId, discountId);
        
        /**
         * Create audit trail for discount modify/activate/deactivate
         * 
         * DISCOUNT_MODIFY: Captures changes to discount validity settings (dates, times, days of week)
         * DISCOUNT_ACTIVATE: Captures when discount status is changed to ACTIVE
         * DISCOUNT_DEACTIVATE: Captures when discount status is changed to INACTIVE
         * 
         * Note: userId is not available in this method signature, so we use the discount's updatedBy as fallback.
         * If updatedBy is null, we still create the audit trail but log a warning.
         * This ensures all discount modifications are tracked in the audit trail.
         */
        try {
            // Determine if any modifications were made (check if any field in request was provided)
            boolean hasModifications = request.getValidFrom() != null || 
                                      request.getValidTo() != null || 
                                      request.getStartTime() != null || 
                                      request.getEndTime() != null ||
                                      request.getDaysOfWeek() != null ||
                                      request.getIsHide() != null ||
                                      statusChanged;
            
            if (hasModifications && restaurant != null) {
                /**
                 * Create audit trail for discount validity modification
                 * 
                 * DISCOUNT_MODIFY: Captures changes to discount validity settings (dates, times, days of week)
                 * DISCOUNT_ACTIVATE: Captures when discount status is changed to ACTIVE
                 * DISCOUNT_DEACTIVATE: Captures when discount status is changed to INACTIVE
                 * 
                 * Note: userId is now passed as a parameter to ensure we have the correct user performing the action.
                 */
                ActionType actionType = null;
                String notes = null;
                
                if (statusChanged) {
                    // Status change - activate or deactivate takes priority
                    // Get restaurant name from translations or use restaurantCode/ID as fallback
                    String restaurantName = "Restaurant";
                    if (restaurant.getTranslations() != null && !restaurant.getTranslations().isEmpty()) {
                        restaurantName = restaurant.getTranslations().get(0).getName();
                    } else if (restaurant.getRestaurantCode() != null) {
                        restaurantName = restaurant.getRestaurantCode();
                    } else {
                        restaurantName = restaurant.getId().toString();
                    }
                    
                    if (newStatus == EntityStatus.ACTIVE) {
                        actionType = ActionType.DISCOUNT_ACTIVATE;
                        notes = String.format("Discount activated for restaurant %s", restaurantName);
                    } else {
                        actionType = ActionType.DISCOUNT_DEACTIVATE;
                        notes = String.format("Discount deactivated for restaurant %s", restaurantName);
                    }
                } else {
                    // Date/time/days modification - DISCOUNT_MODIFY action type
                    actionType = ActionType.DISCOUNT_MODIFY;
                    StringBuilder notesBuilder = new StringBuilder("Discount validity modified: ");
                    List<String> changes = new ArrayList<>();
                    
                    if (request.getValidFrom() != null) {
                        changes.add(String.format(AUDIT_MSG_VALID_FROM, restaurantMapping.getValidFrom()));
                    }
                    if (request.getValidTo() != null) {
                        changes.add(String.format(AUDIT_MSG_VALID_TO, restaurantMapping.getValidTo()));
                    }
                    if (request.getStartTime() != null) {
                        changes.add(String.format(AUDIT_MSG_START_TIME, restaurantMapping.getStartTime()));
                    }
                    if (request.getEndTime() != null) {
                        changes.add(String.format(AUDIT_MSG_END_TIME, restaurantMapping.getEndTime()));
                    }
                    if (request.getDaysOfWeek() != null) {
                        changes.add(String.format(AUDIT_MSG_DAYS_OF_WEEK, restaurantMapping.getDaysOfWeek()));
                    }
                    
                    notes = notesBuilder.append(String.join(", ", changes)).toString();
                }
                
                // Create audit trail if we have an action type
                if (actionType != null) {
                    auditTrailService.createAuditTrail(
                            user,
                            actionType,
                            restaurant,
                            RequestStatus.NA,
                            null, // ipAddress
                            null, // userAgent
                            discountId,
                            ENTITY_TYPE_DISCOUNT,
                            notes
                    );
                    log.debug("Created audit trail for discount {} action: {} by user: {}", 
                            discountId, actionType, user.getUserCode());
                }
            }
        } catch (Exception e) {
            log.error("Failed to create audit trail for discount modify/activate/deactivate: {}", e.getMessage(), e);
            // Don't fail the discount update if audit trail creation fails
        }

        // Return updated discount details
        ResponseDto<RestaurantDiscountDetailsResponse> response = getRestaurantDiscountDetails(restaurantId, discountId, locale, null);
        response.setMessage(messageUtil.getMessage(MSG_DISCOUNT_VALIDITY_UPDATED_SUCCESS, userLocale));
        return response;
    }



    /**
     * Updates an existing discount with new values and translations.
     * Validates that discount is not used in published menus, discount code uniqueness,
     * and business rules. Updates translations and creates audit trail.
     *
     * @param discountId the ID of the discount to update
     * @param userId     the ID of the user performing the update
     * @param request    the discount update request with new values and translations
     * @param locale     locale code for localized error messages
     * @return ResponseDto containing updated discount response
     * @throws ResponseStatusException if discount not found, used in published menu, code exists, or validation fails
     */
    @Override
    @Transactional
    public ResponseDto<DiscountDto<DiscountResponse>> updateDiscount(
            String discountId,
            String userId,
            DiscountRequest request,
            String locale
    ) {
        log.info("Updating discount with id: {}", discountId);
        Locale userLocale = Locale.forLanguageTag(locale);

        // Validate user
        User user = userRepository.findById(UUID.fromString(userId))
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        messageUtil.getMessage(MSG_USER_NOT_FOUND, userLocale)
                ));

        // Find existing discount
        Discount existingDiscount = discountRepository.findById(UUID.fromString(discountId))
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        messageUtil.getMessage(MSG_DISCOUNT_NOT_FOUND, userLocale)
                ));

        // Check if discount is used in any published menu
        if (menuDiscountMappingRepository.isDiscountUsedInPublishedMenu(UUID.fromString(discountId))) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    messageUtil.getMessage(MSG_DISCOUNT_ERROR_PUBLISHED_MENU, userLocale)
            );
        }


        // Check if discount code already exists (excluding current discount)
        if (!existingDiscount.getDiscountCode().equalsIgnoreCase(request.getDiscountCode()) &&
            discountRepository.existsByDiscountCodeAndIsDeletedFalse(request.getDiscountCode())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    messageUtil.getMessage(MSG_DISCOUNT_ERROR_CODE_EXISTS, userLocale));
        }

        // Business rule: maxUses is required for ORDER discounts, optional for ITEM and CATEGORY
        if (request.getAppliedTo() == AppliedTo.ORDER && request.getMaxUses() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                messageUtil.getMessage(MSG_DISCOUNT_ERROR_MAX_USES_REQUIRED, userLocale));
        }

        // Update core Discount fields
        // Set maxUses to null for ITEM and CATEGORY discounts (even if provided)
        Integer maxUsesValue = (request.getAppliedTo() == AppliedTo.ITEM || request.getAppliedTo() == AppliedTo.CATEGORY) 
                ? null : request.getMaxUses();
        
        existingDiscount.setDiscountCode(request.getDiscountCode());
        existingDiscount.setDiscountType(request.getDiscountType());
        existingDiscount.setAppliedTo(request.getAppliedTo());
        existingDiscount.setValue(request.getValue());
        existingDiscount.setOrderValueThreshold(request.getOrderValueThreshold());
        existingDiscount.setMaxDiscountValue(request.getMaxDiscountValue());
        existingDiscount.setBuyQuantity(request.getBuyQuantity());
        existingDiscount.setGetQuantity(request.getGetQuantity());
        existingDiscount.setMaxUses(maxUsesValue);
        existingDiscount.setStatus(request.getStatus());
        existingDiscount.setUpdatedAt(OffsetDateTime.now(ZoneOffset.UTC));
        existingDiscount.setUpdatedBy(user);

        // Update translations
        existingDiscount.getTranslations().clear();
        if (request.getTranslations() != null) {
            for (DiscountTranslationRequest translationRequest : request.getTranslations()) {
                DiscountTranslation translation = DiscountTranslation.builder()
                        .discount(existingDiscount)
                        .languageCode(translationRequest.getLanguageCode())
                        .name(translationRequest.getName())
                        .description(translationRequest.getDescription())
                        .build();
                existingDiscount.getTranslations().add(translation);
            }
        }


        // Update DiscountBxgyItem (first free item only)
        DiscountBxgyItem bxgyItem = null;
        if (existingDiscount.getBxgyItems() != null && !existingDiscount.getBxgyItems().isEmpty()) {
            bxgyItem = existingDiscount.getBxgyItems().get(0);
        } else {
            bxgyItem = new DiscountBxgyItem();
            bxgyItem.setDiscount(existingDiscount);
            existingDiscount.getBxgyItems().add(bxgyItem);
        }
        if (request.getPurchasedItemId() != null) {
            Item purchasedItem = itemRepository.findById(request.getPurchasedItemId())
                    .orElse(null);
            if (purchasedItem != null) {
                // Find existing CategoryItemMapping for this item
                List<CategoryItemMapping> buyItemMappings = categoryItemMappingRepository.findByItem_Id(purchasedItem.getId());
                if (!buyItemMappings.isEmpty()) {
                    bxgyItem.setBuyItemMapping(buyItemMappings.get(0));
                }
            }
        }
        if (request.getFreeItemId() != null) {
            Item freeItemEntity = itemRepository.findById(request.getFreeItemId())
                    .orElse(null);
            if (freeItemEntity != null) {
                // Find existing CategoryItemMapping for this item
                List<CategoryItemMapping> getItemMappings = categoryItemMappingRepository.findByItem_Id(freeItemEntity.getId());
                if (!getItemMappings.isEmpty()) {
                    bxgyItem.setGetItemMapping(getItemMappings.get(0));
                }
            }
        }

        // Save updated discount
        existingDiscount = discountRepository.save(existingDiscount);

        // Prepare translation response
        List<DiscountTranslationResponse> translationResponses = existingDiscount.getTranslations().stream()
                .map(translation -> DiscountTranslationResponse.builder()
                        .languageCode(translation.getLanguageCode())
                        .name(translation.getName())
                        .description(translation.getDescription())
                        .build())
                .collect(Collectors.toList());

        // Prepare schedule and free item response from first entry
        DiscountBxgyItem firstBxgyItem = existingDiscount.getBxgyItems().isEmpty() ? null : existingDiscount.getBxgyItems().get(0);

        String currency = restaurantChainConfigProperties.getChain() != null ? restaurantChainConfigProperties.getChain().getCurrency() : null;
        DiscountResponse discountResponse = DiscountResponse.builder()
                .id(existingDiscount.getId())
                .discountCode(existingDiscount.getDiscountCode())
                .discountType(existingDiscount.getDiscountType())
                .appliedTo(existingDiscount.getAppliedTo())
                .value(existingDiscount.getValue() != null ? CurrencyFormatter.formatAmount(existingDiscount.getValue(), currency) : null)
                .orderValueThreshold(existingDiscount.getOrderValueThreshold() != null ? CurrencyFormatter.formatAmount(existingDiscount.getOrderValueThreshold(), currency) : null)
                .maxDiscountValue(existingDiscount.getMaxDiscountValue() != null ? CurrencyFormatter.formatAmount(existingDiscount.getMaxDiscountValue(), currency) : null)
                .buyQuantity(existingDiscount.getBuyQuantity())
                .getQuantity(existingDiscount.getGetQuantity())
                .maxUses(existingDiscount.getMaxUses())
                .currentUsage(existingDiscount.getCurrentUsage())
                .status(existingDiscount.getStatus())
                .isDeleted(Boolean.TRUE.equals(existingDiscount.getIsDeleted()))
                .createdAt(existingDiscount.getCreatedAt() != null ? existingDiscount.getCreatedAt().toLocalDateTime() : null)
                .createdBy(formatUserName(existingDiscount.getCreatedBy()))
                .updatedAt(existingDiscount.getUpdatedAt() != null ? existingDiscount.getUpdatedAt().toLocalDateTime() : null)
                .updatedBy(formatUserName(existingDiscount.getUpdatedBy()))
                .translations(translationResponses)
                .purchasedItemId(extractBuyItemId(firstBxgyItem))
                .freeItemId(extractGetItemId(firstBxgyItem))
                .build();

        DiscountDto<DiscountResponse> discountDto = DiscountDto.<DiscountResponse>builder()
                .discount(discountResponse)
                .build();

        // Create audit trail for discount update
        try {
            Restaurant restaurant = null;
            if (user.getRestaurantId() != null) {
                restaurant = restaurantRepository.findById(user.getRestaurantId()).orElse(null);
            }
            String discountName = translationResponses.isEmpty() ? 
                existingDiscount.getDiscountCode() : translationResponses.get(0).getName();
            auditTrailService.createAuditTrail(
                    user,
                    ActionType.DISCOUNT_UPDATE,
                    restaurant,
                    null, // status - will default to NA for non-request actions
                    null, // ipAddress - not available in this context
                    null, // userAgent - not available in this context
                    existingDiscount.getId(),
                    ENTITY_TYPE_DISCOUNT,
                    "Discount updated: " + discountName + SUFFIX_CODE + existingDiscount.getDiscountCode() + ")"
            );
        } catch (Exception e) {
            log.error("Failed to create audit trail for discount update: {}", e.getMessage());
            // Don't break discount update flow if audit trail fails
        }

        return ResponseDto.<DiscountDto<DiscountResponse>>builder()
                .message(messageUtil.getMessage(MSG_DISCOUNT_UPDATE_SUCCESS, userLocale))
                .data(discountDto)
                .build();
            }

    /**
     * Soft deletes a discount by setting isDeleted flag to true.
     * Validates that discount is not already deleted, not assigned to menus, and not assigned to promotions.
     * Creates an audit trail for the deletion.
     *
     * @param id     the UUID of the discount to delete
     * @param userId the ID of the user performing the deletion
     * @param locale locale code for localized error messages
     * @return ResponseDto with success message
     * @throws ResponseStatusException if discount not found, already deleted, or assigned to menus/promotions
     */
        @Override
        @Transactional
        public ResponseDto<String> deleteDiscount(UUID id, String userId, String locale) {
            log.info("Deleting discount with id: {}", id);
            Locale userLocale = Locale.forLanguageTag(locale);

        // Validate user
        User user = userRepository.findById(UUID.fromString(userId))
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                            messageUtil.getMessage(MSG_USER_NOT_FOUND, userLocale)));
        // Find discount
        Discount discount = discountRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        messageUtil.getMessage(MSG_DISCOUNT_NOT_FOUND, userLocale)));

        // Check if discount is already deleted
        if (Boolean.TRUE.equals(discount.getIsDeleted())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    messageUtil.getMessage(MSG_DISCOUNT_ALREADY_DELETED, userLocale));
        }

        // Check if discount is associated with menus (menuAssignedCount > 0)
        int menuAssignedCount = menuDiscountMappingRepository.findByDiscountId(discount.getId()).size();
        if (menuAssignedCount > 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    messageUtil.getMessage(MSG_DISCOUNT_ERROR_ASSIGNED_TO_MENUS, userLocale));
        }

        // Check if discount is associated with promotions (assignedToPromotion = true)
        boolean assignedToPromotion = promotionRepository.existsByDiscountIdAndIsDeletedFalse(discount.getId());
        if (assignedToPromotion) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    messageUtil.getMessage(MSG_DISCOUNT_ERROR_ASSIGNED_TO_PROMOTION, userLocale));
        }

        // Soft delete the discount
        discount.setIsDeleted(true);
        discount.setUpdatedAt(OffsetDateTime.now(ZoneOffset.UTC));
        discount.setUpdatedBy(user);
        discount.setStatus(EntityStatus.INACTIVE);
        discountRepository.save(discount);

        // Create audit trail for discount deletion
        try {
            Restaurant restaurant = null;
            if (user.getRestaurantId() != null) {
                restaurant = restaurantRepository.findById(user.getRestaurantId()).orElse(null);
            }
            String discountName = discount.getTranslations().isEmpty() ? 
                discount.getDiscountCode() : discount.getTranslations().get(0).getName();
            auditTrailService.createAuditTrail(
                    user,
                    ActionType.DISCOUNT_DELETE,
                    restaurant,
                    null, // status - will default to NA for non-request actions
                    null, // ipAddress - not available in this context
                    null, // userAgent - not available in this context
                    discount.getId(),
                    ENTITY_TYPE_DISCOUNT,
                    "Discount deleted: " + discountName + SUFFIX_CODE + discount.getDiscountCode() + ")"
            );
        } catch (Exception e) {
            log.error("Failed to create audit trail for discount deletion: {}", e.getMessage());
            // Don't break discount deletion flow if audit trail fails
        }

        return ResponseDto.<String>builder()
                .message(messageUtil.getMessage(MSG_DISCOUNT_DELETE_SUCCESS, userLocale))
                .data(id.toString())
                .build();
    }

    /**
     * Retrieves a single discount by ID with all translations and details.
     * Includes BXGY item information if the discount type is BXGY.
     *
     * @param id     the UUID of the discount to retrieve
     * @param locale locale code for localized responses
     * @return ResponseDto containing the discount details
     * @throws ResponseStatusException if discount not found
     */
    @Override
    @Transactional(readOnly = true)
    public ResponseDto<DiscountDto<DiscountResponse>> getDiscount(UUID id, String locale) {
        Locale userLocale = Locale.forLanguageTag(locale);

        Discount existingDiscount = discountRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        messageUtil.getMessage(MSG_DISCOUNT_NOT_FOUND, userLocale)
                ));

        List<DiscountTranslationResponse> translationResponses = existingDiscount.getTranslations().stream()
                .map(translation -> DiscountTranslationResponse.builder()
                        .languageCode(translation.getLanguageCode())
                        .name(translation.getName())
                        .description(translation.getDescription())
                        .build())
                .collect(Collectors.toList());

        DiscountBxgyItem firstBxgyItem = existingDiscount.getBxgyItems().isEmpty() ? null : existingDiscount.getBxgyItems().get(0);

        String currency = restaurantChainConfigProperties.getChain() != null ? restaurantChainConfigProperties.getChain().getCurrency() : null;
        DiscountResponse discountResponse = DiscountResponse.builder()
                .id(existingDiscount.getId())
                .discountCode(existingDiscount.getDiscountCode())
                .discountType(existingDiscount.getDiscountType())
                .appliedTo(existingDiscount.getAppliedTo())
                .value(existingDiscount.getValue() != null ? CurrencyFormatter.formatAmount(existingDiscount.getValue(), currency) : null)
                .orderValueThreshold(existingDiscount.getOrderValueThreshold() != null ? CurrencyFormatter.formatAmount(existingDiscount.getOrderValueThreshold(), currency) : null)
                .maxDiscountValue(existingDiscount.getMaxDiscountValue() != null ? CurrencyFormatter.formatAmount(existingDiscount.getMaxDiscountValue(), currency) : null)
                .buyQuantity(existingDiscount.getBuyQuantity())
                .getQuantity(existingDiscount.getGetQuantity())
                .maxUses(existingDiscount.getMaxUses())
                .currentUsage(existingDiscount.getCurrentUsage())
                .status(existingDiscount.getStatus())
                .isDeleted(Boolean.TRUE.equals(existingDiscount.getIsDeleted()))
                .createdAt(existingDiscount.getCreatedAt() != null ? existingDiscount.getCreatedAt().toLocalDateTime() : null)
                .createdBy(formatUserName(existingDiscount.getCreatedBy()))
                .updatedAt(existingDiscount.getUpdatedAt() != null ? existingDiscount.getUpdatedAt().toLocalDateTime() : null)
                .updatedBy(formatUserName(existingDiscount.getUpdatedBy()))
                .translations(translationResponses)
                .purchasedItemId(extractBuyItemId(firstBxgyItem))
                .freeItemId(extractGetItemId(firstBxgyItem))
                .build();
        DiscountDto<DiscountResponse> discountDto = DiscountDto.<DiscountResponse>builder()
                .discount(discountResponse)
                .build();
        return ResponseDto.<DiscountDto<DiscountResponse>>builder()
                .message(messageUtil.getMessage(MSG_DISCOUNT_VIEW_SUCCESS, userLocale))
                .data(discountDto)
                .build();
        }

    /**
     * Check if a discount assignment is still valid based on menu-level validity settings
     */
    private boolean isDiscountAssignmentStillValid(Discount discount, UUID menuId) {
        if (discount == null || discount.getIsDeleted() || discount.getStatus() != EntityStatus.ACTIVE) {
            return false;
        }
        
        // Check usage limits
        // maxUses = 0 means unlimited, so only check if maxUses > 0
        if (discount.getMaxUses() != null && discount.getMaxUses() > 0 && discount.getCurrentUsage() >= discount.getMaxUses()) {
            return false;
        }
        
        // Check menu-specific validity from menu_discount_mapping table
        MenuDiscountId menuDiscountId = new MenuDiscountId(menuId, discount.getId());
        Optional<MenuDiscountMapping> menuDiscountMappingOpt = menuDiscountMappingRepository.findById(menuDiscountId);
        
        if (menuDiscountMappingOpt.isEmpty()) {
            return false; // No menu mapping means not valid for this menu
        }
        
        MenuDiscountMapping menuDiscountMapping = menuDiscountMappingOpt.get();
        OffsetDateTime nowUtc = OffsetDateTime.now(ZoneOffset.UTC);
        
        // Check valid_from and valid_to dates
        if (menuDiscountMapping.getValidFrom() != null && nowUtc.isBefore(menuDiscountMapping.getValidFrom())) {
            return false; // Not yet valid
        }
        if (menuDiscountMapping.getValidTo() != null && nowUtc.isAfter(menuDiscountMapping.getValidTo())) {
            return false; // Expired
        }
        
        // Check start_time and end_time restrictions
        if (menuDiscountMapping.getStartTime() != null && menuDiscountMapping.getEndTime() != null) {
            // Convert all times to UTC to ensure consistent comparison regardless of timezone offsets
            OffsetTime currentTime = nowUtc.toOffsetTime();
            OffsetTime startTime = convertToUtc(menuDiscountMapping.getStartTime());
            OffsetTime endTime = convertToUtc(menuDiscountMapping.getEndTime());
            
            // Not 24-hour availability
            if (!startTime.equals(endTime) && startTime.isBefore(endTime)) {
                // Normal case: start < end (e.g., 12:00 to 18:00)
                if (currentTime.isBefore(startTime) || currentTime.isAfter(endTime)) {
                    return false;
                }
            } else if (!startTime.equals(endTime)) {
                // Overnight case: start > end (e.g., 23:00 to 02:00)
                if (currentTime.isBefore(startTime) && currentTime.isAfter(endTime)) {
                    return false;
                }
            }
        }
        
        // Check days of week restrictions
        if (menuDiscountMapping.getDaysOfWeek() != null && !menuDiscountMapping.getDaysOfWeek().isEmpty()) {
            DayOfWeek currentDay = convertToDayOfWeek(nowUtc.getDayOfWeek());
            if (!menuDiscountMapping.getDaysOfWeek().contains(currentDay)) {
                return false;
            }
        }
        
        return true;
    }

    /**
     * Assigns a discount to one or more items in a menu.
     * Supports both regular item assignments and BXGY (Buy X Get Y) assignments.
     * For BXGY, validates both buyItemIds and getItemIds lists.
     * Creates item discount mappings, menu discount mappings, and restaurant discount mappings.
     * Validates UTC datetime fields and discount status.
     *
     * @param request the assignment request with discount ID, menu ID, item IDs, and validity settings
     * @param locale  locale code for localized error messages
     * @return ResponseDto containing assignment response with assigned item IDs
     * @throws ResponseStatusException if validation fails, discount not found, inactive, or items invalid
     */
    @Override
    @Transactional
    @CacheEvict(value = "restaurants", allEntries = true)
    public ResponseDto<ItemDiscountAssignmentResponse> assignDiscountToItems(AssignDiscountToItemsRequest request, String locale) {
        Locale userLocale = Locale.forLanguageTag(locale);
        
        // Check if this is a BXGY assignment (both buyItemIds and getItemIds are provided)
        boolean isBxgyAssignment = request.getBuyItemIds() != null && !request.getBuyItemIds().isEmpty() 
                                 && request.getGetItemIds() != null && !request.getGetItemIds().isEmpty();
        
        // For BXGY, validate both buy and get item lists
        if (isBxgyAssignment) {
            if (request.getBuyItemIds() == null || request.getBuyItemIds().isEmpty()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, 
                    messageUtil.getMessage(MSG_BUY_ITEM_IDS_REQUIRED, userLocale));
            }
            
            if (request.getGetItemIds() == null || request.getGetItemIds().isEmpty()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, 
                    messageUtil.getMessage(MSG_GET_ITEM_IDS_REQUIRED, userLocale));
            }
        } else {
            // For regular assignment, validate itemIds list is not null
            if (request.getItemIds() == null || request.getItemIds().isEmpty()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, 
                    messageUtil.getMessage(MSG_ITEM_IDS_NULL, userLocale));
            }
        }

        // Validate that all item IDs are valid (not null)
        List<UUID> allItemIds = new ArrayList<>();
        if (isBxgyAssignment) {
            allItemIds.addAll(request.getBuyItemIds());
            allItemIds.addAll(request.getGetItemIds());
        } else {
            allItemIds.addAll(request.getItemIds());
        }
        
        for (UUID itemId : allItemIds) {
            if (itemId == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, 
                    messageUtil.getMessage(MSG_ITEM_ID_INVALID, userLocale));
            }
        }
        
        // Validate UTC datetime fields
        validateUtcDateTimeFields(request, userLocale);
        
        Discount discount = discountRepository.findById(request.getDiscountId())
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, messageUtil.getMessage(MSG_DISCOUNT_NOT_FOUND, userLocale)));
        
        // Check if discount is deleted
        if (Boolean.TRUE.equals(discount.getIsDeleted())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, messageUtil.getMessage(MSG_DISCOUNT_DELETED, userLocale));
        }
        
        // Check if discount is inactive
        if (discount.getStatus() != EntityStatus.ACTIVE) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, messageUtil.getMessage(MSG_DISCOUNT_INACTIVE, userLocale));
        }
        
        // Check if discount type is applicable to items
        if (discount.getAppliedTo() != AppliedTo.ITEM) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, messageUtil.getMessage(MSG_DISCOUNT_TYPE_MISMATCH, userLocale));
        }
        
        
        UUID menuId = request.getMenuId();
        List<MenuCategoryMapping> menuCategoryMappings = menuCategoryMappingRepository.findByMenuId(menuId);
        List<UUID> menuCategoryIds = menuCategoryMappings.stream()
            .map(mcm -> mcm.getCategory().getId())
            .collect(Collectors.toList());
        
        // Validate all items exist and are in the menu
        List<Item> allItems = new ArrayList<>();
        for (UUID itemId : allItemIds) {
            Item item = itemRepository.findById(itemId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, 
                    messageUtil.getMessage(MSG_ITEM_NOT_FOUND, userLocale)));
            
            if (Boolean.TRUE.equals(item.getIsDeleted())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, 
                    messageUtil.getMessage(MSG_ITEM_DELETED, userLocale));
            }
            
            // Check if item is in the menu
            List<CategoryItemMapping> itemMappings = categoryItemMappingRepository.findByItem_Id(itemId);
            boolean itemInMenu = itemMappings.stream()
                .anyMatch(cim -> menuCategoryIds.contains(cim.getMenuCategoryMapping().getCategory().getId()));
            
            if (!itemInMenu) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, 
                    messageUtil.getMessage(MSG_ITEM_NOT_IN_MENU, userLocale));
            }
            
            allItems.add(item);
        }
        
        
        // Check for duplicate assignments before proceeding
        if (isBxgyAssignment) {
            // Check for existing BXGY assignments
            for (UUID buyItemId : request.getBuyItemIds()) {
                // Find the CategoryItemMapping for this item in the specific menu
                CategoryItemMapping buyItemMapping = categoryItemMappingRepository
                    .findByItemIdAndMenuCategoryMappingMenuId(buyItemId, menuId)
                    .orElse(null);
                
                if (buyItemMapping != null) {
                    for (UUID getItemId : request.getGetItemIds()) {
                        // Find the CategoryItemMapping for this item in the specific menu
                        CategoryItemMapping getItemMapping = categoryItemMappingRepository
                            .findByItemIdAndMenuCategoryMappingMenuId(getItemId, menuId)
                            .orElse(null);
                        
                        if (getItemMapping != null) {
                            // Check if this BXGY combination already exists for this discount AND is still valid
                            List<DiscountBxgyItem> existingBxgyMappings = discountBxgyItemRepository.findByDiscountId(discount.getId());
                            boolean activeBxgyAssignmentFound = existingBxgyMappings.stream()
                                .anyMatch(bxgy -> {
                                    if (bxgy.getBuyItemMapping() == null || bxgy.getGetItemMapping() == null) {
                                        return false; // Invalid BXGY mapping
                                    }
                                    
                                    if (!bxgy.getBuyItemMapping().getId().equals(buyItemMapping.getId()) ||
                                        !bxgy.getGetItemMapping().getId().equals(getItemMapping.getId())) {
                                        return false; // Different BXGY combination
                                    }
                                    
                                    // Check if this BXGY assignment is still valid
                                    return isDiscountAssignmentStillValid(bxgy.getDiscount(), menuId);
                                });
                            
                            if (activeBxgyAssignmentFound) {
                                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, 
                                    messageUtil.getMessage(MSG_DISCOUNT_BXGY_ALREADY_ASSIGNED, userLocale));
                            }
                        }
                    }
                }
            }
        } else {
            // Check for existing regular item assignments
            for (Item item : allItems) {
                List<CategoryItemMapping> itemMappings = categoryItemMappingRepository.findByItem_Id(item.getId());
                for (CategoryItemMapping cim : itemMappings) {
                    if (menuCategoryIds.contains(cim.getMenuCategoryMapping().getCategory().getId())) {
                        // Check if this item is already assigned to this discount AND the assignment is still valid
                        List<ItemDiscountMapping> existingMappings = itemDiscountMappingRepository.findByCategoryItemMapping(cim);
                        boolean activeAssignmentFound = existingMappings.stream()
                            .anyMatch(mapping -> {
                                if (!mapping.getDiscount().getId().equals(discount.getId())) {
                                    return false; // Different discount
                                }
                                
                                // Check if this assignment is still valid
                                return isDiscountAssignmentStillValid(mapping.getDiscount(), menuId);
                            });
                        
                        if (activeAssignmentFound) {
                            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, 
                                messageUtil.getMessage(MSG_DISCOUNT_ASSIGNMENT_ALREADY_EXISTS, userLocale));
                        }
                    }
                }
            }
        }
        
        // Check if discount is already assigned to this menu AND the assignment is still valid
        List<MenuDiscountMapping> existingMenuMappings = menuDiscountMappingRepository.findByMenuId(menuId);
        boolean activeMenuAssignmentFound = existingMenuMappings.stream()
            .anyMatch(mdm -> {
                if (!mdm.getDiscount().getId().equals(discount.getId())) {
                    return false; // Different discount
                }
                
                // Check if this menu assignment is still valid
                return isDiscountAssignmentStillValid(mdm.getDiscount(), menuId);
            });
        
        if (activeMenuAssignmentFound) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, 
                messageUtil.getMessage(MSG_DISCOUNT_ALREADY_ASSIGNED_TO_MENU, userLocale));
        }
        
        if (isBxgyAssignment) {
            // Validate that items are not already assigned to other BXGY discounts in this menu
            log.info("Validating BXGY items for discount {} in menu {}", discount.getId(), menuId);
            
            // Collect all Item IDs (both buy and get items) that are being assigned
            Set<UUID> allItemIdsSet = new HashSet<>();
            allItemIdsSet.addAll(request.getBuyItemIds());
            allItemIdsSet.addAll(request.getGetItemIds());
            
            if (!allItemIdsSet.isEmpty()) {
                // Directly query for conflicts using Item IDs
                // This checks if any of these items are already used in other BXGY discounts in this menu
                List<DiscountBxgyItem> conflictingBxgyItems = discountBxgyItemRepository.findConflictingBxgyItems(
                    menuId, 
                    new ArrayList<>(allItemIdsSet), 
                    DiscountType.BXGY,
                    EntityStatus.ACTIVE,
                    discount.getId()
                );
                
                if (!conflictingBxgyItems.isEmpty()) {
                    // Get the new discount's validity dates from request
                    OffsetDateTime newValidFrom = request.getValidFrom() != null ? 
                        convertToUtc(request.getValidFrom()) : null;
                    OffsetDateTime newValidTo = request.getValidTo() != null ? 
                        convertToUtc(request.getValidTo()) : null;
                    
                    // Collect discount IDs from conflicting BXGY items
                    Set<UUID> conflictingDiscountIds = conflictingBxgyItems.stream()
                        .map(bxgyItem -> bxgyItem.getDiscount().getId())
                        .collect(java.util.stream.Collectors.toSet());
                    
                    // Fetch MenuDiscountMappings for this menu and filter by conflicting discount IDs
                    List<MenuDiscountMapping> allMenuMappings = menuDiscountMappingRepository.findByMenuId(menuId);
                    List<MenuDiscountMapping> conflictingMenuMappings = allMenuMappings.stream()
                        .filter(mdm -> conflictingDiscountIds.contains(mdm.getDiscount().getId()))
                        .collect(java.util.stream.Collectors.toList());
                    
                    // Map discount ID to MenuDiscountMapping for quick lookup
                    Map<UUID, MenuDiscountMapping> discountToMappingMap = conflictingMenuMappings.stream()
                        .collect(java.util.stream.Collectors.toMap(
                            mdm -> mdm.getDiscount().getId(),
                            mdm -> mdm,
                            (existing, replacement) -> existing // Keep first if duplicates
                        ));
                    
                    // Check for date overlaps - only throw exception if dates overlap
                    List<DiscountBxgyItem> overlappingBxgyItems = new ArrayList<>();
                    for (DiscountBxgyItem bxgyItem : conflictingBxgyItems) {
                        UUID discountId = bxgyItem.getDiscount().getId();
                        MenuDiscountMapping existingMapping = discountToMappingMap.get(discountId);
                        
                        if (existingMapping == null) {
                            // If no mapping found, skip this item (shouldn't happen, but be safe)
                            continue;
                        }
                        
                        OffsetDateTime existingValidFrom = existingMapping.getValidFrom();
                        OffsetDateTime existingValidTo = existingMapping.getValidTo();
                        
                        // Check for date overlap
                        // Two date ranges [A, B] and [C, D] overlap if: A < D && B > C
                        boolean overlaps = false;
                        
                        if (newValidFrom == null || existingValidFrom == null) {
                            // If either has no start date, consider it an overlap (shouldn't happen in practice)
                            overlaps = true;
                        } else if (newValidTo == null && existingValidTo == null) {
                            // Both are indefinite - they will overlap since both extend to infinity
                            overlaps = true;
                        } else if (newValidTo == null) {
                            // New discount is indefinite (extends to infinity)
                            // Overlaps if it starts before or when existing ends (since new has no end)
                            overlaps = !newValidFrom.isAfter(existingValidTo);
                        } else if (existingValidTo == null) {
                            // Existing discount is indefinite (extends to infinity)
                            // Overlaps if existing starts before or when new ends (since existing has no end)
                            overlaps = !existingValidFrom.isAfter(newValidTo);
                        } else {
                            // Both have end dates - standard overlap check
                            overlaps = newValidFrom.isBefore(existingValidTo) && newValidTo.isAfter(existingValidFrom);
                        }
                        
                        if (overlaps) {
                            overlappingBxgyItems.add(bxgyItem);
                        }
                    }
                    
                    // Only throw exception if there are overlapping date ranges
                    if (!overlappingBxgyItems.isEmpty()) {
                        // Collect the conflicting item IDs from overlapping items only
                        Set<UUID> conflictingItemIds = new HashSet<>();
                        for (DiscountBxgyItem bxgyItem : overlappingBxgyItems) {
                            if (bxgyItem.getBuyItemMapping() != null && bxgyItem.getBuyItemMapping().getItem() != null) {
                                conflictingItemIds.add(bxgyItem.getBuyItemMapping().getItem().getId());
                            }
                            if (bxgyItem.getGetItemMapping() != null && bxgyItem.getGetItemMapping().getItem() != null) {
                                conflictingItemIds.add(bxgyItem.getGetItemMapping().getItem().getId());
                            }
                        }
                        
                        // Fetch item translations to get item names
                        List<ItemTranslation> itemTranslations = itemTranslationRepository.findAllByItemIdIn(new ArrayList<>(conflictingItemIds));
                        Map<UUID, List<ItemTranslation>> translationsByItemId = itemTranslations.stream()
                                .collect(java.util.stream.Collectors.groupingBy(t -> t.getItem().getId()));
                        
                        // Collect item names in user's locale
                        List<String> conflictingItemNames = new ArrayList<>();
                        for (UUID itemId : conflictingItemIds) {
                            List<ItemTranslation> itemTrans = translationsByItemId.getOrDefault(itemId, new ArrayList<>());
                            String itemName = "";
                            
                            if (!itemTrans.isEmpty()) {
                                ItemTranslation exactMatch = itemTrans.stream()
                                    .filter(t -> t.getLanguageCode() != null && t.getLanguageCode().equals(locale))
                                    .findFirst()
                                    .orElse(null);
                                
                                if (exactMatch != null) {
                                    itemName = exactMatch.getName() != null ? exactMatch.getName() : "";
                                } else {
                                    Optional<ItemTranslation> fallback = TranslationUtils.pickPreferredOrFromList(
                                            itemTrans,
                                            locale,
                                            localizationProperties.getLanguages(),
                                            ItemTranslation::getLanguageCode
                                    );
                                    itemName = fallback.map(t -> t.getName() != null ? t.getName() : "").orElse("");
                                }
                            }
                            
                            if (!itemName.isEmpty()) {
                                conflictingItemNames.add(itemName);
                            } else {
                                // Fallback to item ID if no translation found
                                conflictingItemNames.add(itemId.toString());
                            }
                        }
                        
                        String itemNamesList = String.join(", ", conflictingItemNames);
                        log.error("Found {} overlapping BXGY items for discount {} in menu {}. Conflicting item IDs: {}, Item names: {}, New dates: {} to {}, Existing dates overlap", 
                            overlappingBxgyItems.size(), discount.getId(), menuId, conflictingItemIds, itemNamesList, newValidFrom, newValidTo);
                        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, 
                            messageUtil.getMessage(MSG_DISCOUNT_BXGY_ITEM_ALREADY_ASSIGNED_TO_OTHER_DISCOUNT, userLocale, itemNamesList));
                    }
                    
                    // If no date overlaps, allow the assignment (different validity periods)
                    log.info("Found conflicting BXGY items but no date overlap, allowing assignment for discount {} in menu {}", discount.getId(), menuId);
                }
                
                log.info("BXGY validation passed for discount {} in menu {}", discount.getId(), menuId);
            }
            // Handle BXGY assignment using DiscountBxgyItem
            List<DiscountBxgyItem> bxgyMappings = new ArrayList<>();
            
            // Process buy items and get items combinations
            for (UUID buyItemId : request.getBuyItemIds()) {
                // Find the CategoryItemMapping for this buy item in the specific menu
                CategoryItemMapping buyItemMapping = categoryItemMappingRepository
                    .findByItemIdAndMenuCategoryMappingMenuId(buyItemId, menuId)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, 
                        messageUtil.getMessage(MSG_ITEM_NOT_IN_MENU, userLocale)));
                
                for (UUID getItemId : request.getGetItemIds()) {
                    // Find the CategoryItemMapping for this get item in the specific menu
                    CategoryItemMapping getItemMapping = categoryItemMappingRepository
                        .findByItemIdAndMenuCategoryMappingMenuId(getItemId, menuId)
                        .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, 
                            messageUtil.getMessage(MSG_ITEM_NOT_IN_MENU, userLocale)));
                    
                    DiscountBxgyItem bxgyItem = DiscountBxgyItem.builder()
                        .discount(discount)
                        .buyItemMapping(buyItemMapping)
                        .getItemMapping(getItemMapping)
                        .build();
                    
                    bxgyMappings.add(bxgyItem);
                }
            }
            
            // Save BXGY mappings
            discountBxgyItemRepository.saveAll(bxgyMappings);
            
        } else {
            // Handle regular item assignment using ItemDiscountMapping
            List<ItemDiscountMapping> allMappings = new ArrayList<>();
            for (Item item : allItems) {
                List<CategoryItemMapping> itemMappings = categoryItemMappingRepository.findByItem_Id(item.getId());
                for (CategoryItemMapping cim : itemMappings) {
                    if (menuCategoryIds.contains(cim.getMenuCategoryMapping().getCategory().getId())) {
                        ItemDiscountMapping mapping = ItemDiscountMapping.builder()
                            .categoryItemMapping(cim)
                            .discount(discount)
                            .build();
                        allMappings.add(mapping);
                    }
                }
            }
            
            // Save mappings
            itemDiscountMappingRepository.saveAll(allMappings);
        }
        
        // Handle MenuDiscountMapping for validity fields (moved outside if-else block)
        MenuDiscountMapping existingMenuDiscountMapping = menuDiscountMappingRepository.findByMenuId(menuId)
            .stream()
            .filter(mdm -> mdm.getDiscount().getId().equals(discount.getId()))
            .findFirst()
            .orElse(null);
            
        if (existingMenuDiscountMapping != null) {
            // Update existing mapping with override fields (convert to UTC like combo system)
            existingMenuDiscountMapping.setValidFrom(convertToUtc(request.getValidFrom()));
            existingMenuDiscountMapping.setValidTo(convertToUtc(request.getValidTo()));
            existingMenuDiscountMapping.setStartTime(convertToUtc(request.getStartTime()));
            existingMenuDiscountMapping.setEndTime(convertToUtc(request.getEndTime()));
            existingMenuDiscountMapping.setDaysOfWeek(request.getDaysOfWeek());
            menuDiscountMappingRepository.save(existingMenuDiscountMapping);
        } else {
            // Create new mapping with override fields (convert to UTC like combo system)
            Menu menu = menuRepository.findById(menuId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, messageUtil.getMessage(MSG_MENU_NOT_FOUND, userLocale)));
            MenuDiscountId id = new MenuDiscountId();
            id.setMenuId(menuId);
            id.setDiscountId(discount.getId());
            MenuDiscountMapping menuDiscountMapping = MenuDiscountMapping.builder()
                .id(id)
                .menu(menu)
                .discount(discount)
                .validFrom(convertToUtc(request.getValidFrom()))
                .validTo(convertToUtc(request.getValidTo()))
                .startTime(convertToUtc(request.getStartTime()))
                .endTime(convertToUtc(request.getEndTime()))
                .daysOfWeek(request.getDaysOfWeek())
                .build();
            menuDiscountMappingRepository.save(menuDiscountMapping);
        }
        
        // Create restaurant discount mappings
        createRestaurantDiscountMappings(
            menuId,
            discount,
            request.getValidFrom(),
            request.getValidTo(),
            request.getStartTime(),
            request.getEndTime(),
            request.getDaysOfWeek(),
            null,
            userLocale
        );
        
        // Create response with all assigned items (UTC dates as received from frontend)
        ItemDiscountAssignmentResponse response = new ItemDiscountAssignmentResponse();
        response.setDiscountId(request.getDiscountId());
        response.setAssignedItemIds(allItemIds);
        response.setMenuId(request.getMenuId());
        response.setValidFrom(request.getValidFrom());
        response.setValidTo(request.getValidTo());
        response.setStartTime(request.getStartTime());
        response.setEndTime(request.getEndTime());
        response.setDaysOfWeek(request.getDaysOfWeek());
        
        String successMessage = isBxgyAssignment ? 
            messageUtil.getMessage(MSG_DISCOUNT_BXGY_ASSIGNMENT_SUCCESS, userLocale) :
            messageUtil.getMessage(MSG_DISCOUNT_ASSIGNMENT_SUCCESS, userLocale);
        
        return ResponseDto.<ItemDiscountAssignmentResponse>builder()
            .data(response)
            .message(successMessage)
            .build();
    }

    /**
     * Assigns a discount to one or more categories in a menu.
     * Validates that categories are not combo type categories.
     * Creates category discount mappings, menu discount mappings, and restaurant discount mappings.
     * Validates UTC datetime fields and discount status.
     *
     * @param request the assignment request with discount ID, menu ID, category IDs, and validity settings
     * @param locale  locale code for localized error messages
     * @return ResponseDto containing assignment response with assigned category IDs
     * @throws ResponseStatusException if validation fails, discount not found, inactive, or categories invalid/combo type
     */
    @Override
    @Transactional
    @CacheEvict(value = "restaurants", allEntries = true)
    public ResponseDto<CategoryDiscountAssignmentResponse> assignDiscountToCategories(AssignDiscountToCategoriesRequest request, String locale) {
        Locale userLocale = Locale.forLanguageTag(locale);
        
        // Validate category list is not empty
        if (request.getCategoryIds() == null || request.getCategoryIds().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, 
                messageUtil.getMessage(MSG_CATEGORY_LIST_EMPTY, userLocale));
        }
        
        // Validate that all category IDs are valid (not null)
        for (UUID categoryId : request.getCategoryIds()) {
            if (categoryId == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, 
                    messageUtil.getMessage(MSG_CATEGORY_ID_INVALID, userLocale));
            }
        }
        
        // Validate that none of the categories are combo type
        for (UUID categoryId : request.getCategoryIds()) {
            Category category = categoryRepository.findByIdAndIsDeletedFalse(categoryId).orElse(null);
            if (category != null && Boolean.TRUE.equals(category.getIsCombo())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, 
                    messageUtil.getMessage(MSG_DISCOUNT_CANNOT_APPLY_TO_COMBO_CATEGORY, userLocale));
            }
        }
        
        // Validate discount ID is provided
        if (request.getDiscountId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                messageUtil.getMessage(MSG_DISCOUNT_ID_REQUIRED, userLocale));
        }

        // Validate UTC datetime fields
        validateUtcDateTimeFields(request, userLocale);
        
        Discount discount = discountRepository.findById(request.getDiscountId())
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, messageUtil.getMessage(MSG_DISCOUNT_NOT_FOUND, userLocale)));
        
        // Check if discount is deleted
        if (Boolean.TRUE.equals(discount.getIsDeleted())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, messageUtil.getMessage(MSG_DISCOUNT_DELETED, userLocale));
        }
        
        // Check if discount is inactive
        if (discount.getStatus() != EntityStatus.ACTIVE) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, messageUtil.getMessage(MSG_DISCOUNT_INACTIVE, userLocale));
        }
        
        // Check if discount type is applicable to categories
        if (discount.getAppliedTo() != AppliedTo.CATEGORY) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, messageUtil.getMessage(MSG_DISCOUNT_TYPE_MISMATCH_CATEGORY, userLocale));
        }
        
        
        UUID menuId = request.getMenuId();
        List<MenuCategoryMapping> menuCategoryMappings = menuCategoryMappingRepository.findByMenuId(menuId);
        List<UUID> menuCategoryIds = menuCategoryMappings.stream()
            .map(mcm -> mcm.getCategory().getId())
            .collect(Collectors.toList());
        List<UUID> assignedCategoryIds = new ArrayList<>();
        
        // Check if any categories are already assigned to discounts
        for (UUID categoryId : request.getCategoryIds()) {
            // Check if category belongs to the specified menu
            if (!menuCategoryIds.contains(categoryId)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, messageUtil.getMessage(MSG_CATEGORY_MENU_MISMATCH, userLocale));
            }
            
            // Resolve MenuCategoryMapping for (menuId, categoryId)
            MenuCategoryMapping mcm = menuCategoryMappingRepository.findByMenuIdAndCategoryId(menuId, categoryId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, messageUtil.getMessage(MSG_CATEGORY_MENU_MISMATCH, userLocale)));
            // Check if category is already assigned to any discount via MCM AND the assignment is still valid
            List<CategoryDiscountMapping> existingMappings = categoryDiscountMappingRepository.findByMenuCategoryMapping(mcm);
            for (CategoryDiscountMapping existingMapping : existingMappings) {
                if (existingMapping.getDiscount().getId().equals(discount.getId())) {
                    // Category is already assigned to the same discount - check if still valid
                    if (isDiscountAssignmentStillValid(existingMapping.getDiscount(), menuId)) {
                        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, 
                            messageUtil.getMessage(MSG_CATEGORY_ALREADY_ASSIGNED_SAME_DISCOUNT, userLocale));
                    }
                    // If assignment is expired, allow reassignment
                } else {
                    // Category is assigned to a different discount - check if still valid
                    if (isDiscountAssignmentStillValid(existingMapping.getDiscount(), menuId)) {
                        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, 
                            messageUtil.getMessage(MSG_CATEGORY_ALREADY_ASSIGNED_DISCOUNT, userLocale));
                    }
                    // If assignment is expired, allow reassignment
                }
            }
        }
        
        // If validation passes, proceed with assignment
        for (UUID categoryId : request.getCategoryIds()) {
            MenuCategoryMapping mcm = menuCategoryMappingRepository.findByMenuIdAndCategoryId(menuId, categoryId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, messageUtil.getMessage(MSG_CATEGORY_MENU_MISMATCH, userLocale)));
            
            // Validate that the MenuCategoryMapping actually exists in the database
            if (mcm.getId() == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, 
                    messageUtil.getMessage(MSG_CATEGORY_MENU_MAPPING_INVALID, userLocale));
            }
            
            // Verify the MCM exists by querying it again to ensure it's not stale
            Optional<MenuCategoryMapping> verifiedMcm = menuCategoryMappingRepository.findById(mcm.getId());
            if (verifiedMcm.isEmpty()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, 
                    messageUtil.getMessage(MSG_CATEGORY_MENU_MAPPING_NOT_FOUND, userLocale));
            }
            
            // Additional safety check: verify the entity exists in the database right before save
            boolean mcmExists = menuCategoryMappingRepository.existsById(mcm.getId());
            if (!mcmExists) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, 
                    messageUtil.getMessage(MSG_CATEGORY_MENU_MAPPING_NOT_FOUND, userLocale));
            }
    
            try {
                CategoryDiscountMapping mapping = new CategoryDiscountMapping();
                mapping.setMenuCategoryMapping(verifiedMcm.get()); // Use the verified entity
                mapping.setDiscount(discount);
                categoryDiscountMappingRepository.save(mapping);
                assignedCategoryIds.add(categoryId);
            } catch (org.springframework.dao.DataIntegrityViolationException e) {
                // Handle any remaining constraint violations gracefully
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, 
                    messageUtil.getMessage(MSG_CATEGORY_MENU_MAPPING_NOT_FOUND, userLocale));
            }
        }
        
        // Handle MenuDiscountMapping with override fields
        MenuDiscountMapping existingMenuDiscountMapping = menuDiscountMappingRepository.findByMenuId(menuId)
            .stream()
            .filter(mdm -> mdm.getDiscount().getId().equals(discount.getId()))
            .findFirst()
            .orElse(null);
            
        if (existingMenuDiscountMapping != null) {
            // Update existing mapping with override fields
            existingMenuDiscountMapping.setValidFrom(convertToUtc(request.getValidFrom()));
            existingMenuDiscountMapping.setValidTo(convertToUtc(request.getValidTo()));
            existingMenuDiscountMapping.setStartTime(convertToUtc(request.getStartTime()));
            existingMenuDiscountMapping.setEndTime(convertToUtc(request.getEndTime()));
            existingMenuDiscountMapping.setDaysOfWeek(request.getDaysOfWeek());
            menuDiscountMappingRepository.save(existingMenuDiscountMapping);
        } else {
            // Create new mapping with override fields
            Menu menu = menuRepository.findById(menuId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, messageUtil.getMessage(MSG_MENU_NOT_FOUND, userLocale)));
            MenuDiscountId id = new MenuDiscountId();
            id.setMenuId(menuId);
            id.setDiscountId(discount.getId());
            MenuDiscountMapping menuDiscountMapping = MenuDiscountMapping.builder()
                .id(id)
                .menu(menu)
                .discount(discount)
                .validFrom(request.getValidFrom())
                .validTo(request.getValidTo())
                .startTime(request.getStartTime())
                .endTime(request.getEndTime())
                .daysOfWeek(request.getDaysOfWeek())
                .build();
            menuDiscountMappingRepository.save(menuDiscountMapping);
        }
        
        // Create restaurant discount mappings
        createRestaurantDiscountMappings(
            menuId,
            discount,
            request.getValidFrom(),
            request.getValidTo(),
            request.getStartTime(),
            request.getEndTime(),
            request.getDaysOfWeek(),
            null,
            userLocale
        );
        
        CategoryDiscountAssignmentResponse response = new CategoryDiscountAssignmentResponse();
        response.setDiscountId(request.getDiscountId());
        response.setAssignedCategoryIds(assignedCategoryIds);
        response.setMenuId(menuId);
        response.setValidFrom(request.getValidFrom());
        response.setValidTo(request.getValidTo());
        response.setStartTime(request.getStartTime());
        response.setEndTime(request.getEndTime());
        response.setDaysOfWeek(request.getDaysOfWeek());
        
        return ResponseDto.<CategoryDiscountAssignmentResponse>builder()
            .data(response)
            .message(messageUtil.getMessage(MSG_DISCOUNT_ASSIGNMENT_SUCCESS, userLocale))
            .build();
    }

    @Override
    @Transactional(readOnly = true)
    public ResponseDto<ItemDiscountListResponse> getMenuWithDiscounts(
            Integer page, Integer size, UUID menuId, String search, String sortBy,
            Sort.Direction direction, EntityStatus status, String discountType, String appliedTo, Boolean applyDayFilter, UUID restaurantId, String locale) {

        Locale userLocale = Locale.forLanguageTag(locale);
        
        // Validate locale
        if (!localizationProperties.getLanguages().contains(locale)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    messageUtil.getMessage(MSG_DISCOUNT_ERROR_INVALID_LANGUAGE, userLocale));
        }

        // Validate restaurant if provided
        if (restaurantId != null) {
            restaurantRepository.findById(restaurantId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        messageUtil.getMessage(MSG_RESTAURANT_NOT_FOUND, userLocale)));
        }

        // Validate and set pagination - Updated to match your example
        int pageNumber = (page != null ? page : 1) - 1;
        if (pageNumber < 0) pageNumber = 0;
        int pageSize = size != null ? size : Integer.MAX_VALUE; // Changed back to Integer.MAX_VALUE
        if (pageSize < 1) pageSize = Integer.MAX_VALUE;

        // Support both single and multiple discount types (comma-separated)
        final Set<DiscountType> discountTypes;
        if (discountType != null && !discountType.isBlank()) {
            try {
                Set<DiscountType> tempDiscountTypes = Arrays.stream(discountType.split(","))
                        .map(String::trim)
                        .filter(s -> !s.isBlank())
                        .map(s -> DiscountType.valueOf(s.toUpperCase()))
                        .collect(Collectors.toSet());
                discountTypes = tempDiscountTypes.isEmpty() ? null : tempDiscountTypes;
            } catch (IllegalArgumentException e) {
                String errorMessage = messageUtil.getMessage(MSG_ERROR_INVALID_DISCOUNT_TYPE, userLocale, discountType);
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, errorMessage);
            }
        } else {
            discountTypes = null;
        }

        // Support both single and multiple appliedTo types (comma-separated)
        final Set<AppliedTo> appliedToTypes;
        if (appliedTo != null && !appliedTo.isBlank()) {
            try {
                Set<AppliedTo> tempAppliedToTypes = Arrays.stream(appliedTo.split(","))
                        .map(String::trim)
                        .filter(s -> !s.isBlank())
                        .map(s -> AppliedTo.valueOf(s.toUpperCase()))
                        .collect(Collectors.toSet());
                appliedToTypes = tempAppliedToTypes.isEmpty() ? null : tempAppliedToTypes;
            } catch (IllegalArgumentException e) {
                String errorMessage = messageUtil.getMessage(MSG_ERROR_INVALID_APPLIED_TO, userLocale, appliedTo);
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, errorMessage);
            }
        } else {
            appliedToTypes = null;
        }

        Set<UUID> menuDiscountIds = new HashSet<>();
        Map<UUID, RestaurantDiscountMapping> discountIdToRestaurantMapping = new HashMap<>();
        Map<UUID, MenuDiscountMapping> discountIdToMenuMapping = new HashMap<>();
        List<MenuDiscountMapping> menuDiscountMappingsForMenu = null;
        
        // If restaurantId is provided and appliedTo is ORDER, get ORDER type discounts from RestaurantDiscountMapping
        if (restaurantId != null && appliedToTypes != null && appliedToTypes.contains(AppliedTo.ORDER)) {
            // Get all RestaurantDiscountMappings for this restaurant
            List<RestaurantDiscountMapping> restaurantDiscountMappings = 
                restaurantDiscountMappingRepository.findById_RestaurantId(restaurantId);
            
            // Filter for ORDER type discounts and build map
            for (RestaurantDiscountMapping rdm : restaurantDiscountMappings) {
                Discount discount = rdm.getDiscount();
                if (discount != null && discount.getAppliedTo() == AppliedTo.ORDER) {
                    menuDiscountIds.add(discount.getId());
                    discountIdToRestaurantMapping.put(discount.getId(), rdm);
                }
            }
        } else {
            // Original logic: Get all discount IDs associated with this menu
            // Add discounts directly assigned to the menu
            menuDiscountMappingsForMenu = menuDiscountMappingRepository.findByMenuId(menuId);
            for (MenuDiscountMapping mapping : menuDiscountMappingsForMenu) {
                menuDiscountIds.add(mapping.getDiscount().getId());
                // Keep a map for later lookups (avoid repeated queries)
                if (mapping.getDiscount() != null && mapping.getDiscount().getId() != null) {
                    discountIdToMenuMapping.putIfAbsent(mapping.getDiscount().getId(), mapping);
                }
            }
            
            // Add discounts assigned to categories in the menu via menu_category_mapping_id
            List<MenuCategoryMapping> menuCategoryMappings = menuCategoryMappingRepository.findByMenuId(menuId);
            for (MenuCategoryMapping mcm : menuCategoryMappings) {
                List<CategoryDiscountMapping> categoryDiscountMappings = categoryDiscountMappingRepository.findByMenuCategoryMapping(mcm);
                for (CategoryDiscountMapping cdm : categoryDiscountMappings) {
                    menuDiscountIds.add(cdm.getDiscount().getId());
                }
            }
            
            // Add discounts assigned to items in the menu via item_discount_mapping and discount_bxgy_item
            for (MenuCategoryMapping mcm : menuCategoryMappings) {
                List<CategoryItemMapping> categoryItemMappings = categoryItemMappingRepository.findByMenuCategoryMapping(mcm);
                for (CategoryItemMapping cim : categoryItemMappings) {
                    // Add regular item discount mappings
                    List<ItemDiscountMapping> itemDiscountMappings = itemDiscountMappingRepository.findByCategoryItemMapping(cim);
                    for (ItemDiscountMapping idm : itemDiscountMappings) {
                        menuDiscountIds.add(idm.getDiscount().getId());
                    }
                    
                    // Add BXGY item mappings
                    List<DiscountBxgyItem> bxgyItems = discountBxgyItemRepository.findByBuyItemMappingId(cim.getId());
                    for (DiscountBxgyItem bxgyItem : bxgyItems) {
                        menuDiscountIds.add(bxgyItem.getDiscount().getId());
                    }
                    
                    List<DiscountBxgyItem> bxgyGetItems = discountBxgyItemRepository.findByGetItemMappingId(cim.getId());
                    for (DiscountBxgyItem bxgyItem : bxgyGetItems) {
                        menuDiscountIds.add(bxgyItem.getDiscount().getId());
                    }
                }
            }
        }

        // Create specification for filtering
        Specification<Discount> spec = (root, query, criteriaBuilder) -> {
            List<Predicate> predicates = new ArrayList<>();

            // Filter by menu discount IDs
            predicates.add(root.get("id").in(menuDiscountIds));

            // Add status filter
            if (status != null) {
                predicates.add(criteriaBuilder.equal(root.get(FIELD_STATUS), status));
            }

            // Add discount type filter
            if (discountTypes != null && !discountTypes.isEmpty()) {
                predicates.add(root.get("discountType").in(discountTypes));
            }

            // Add appliedTo filter
            if (appliedToTypes != null && !appliedToTypes.isEmpty()) {
                predicates.add(root.get("appliedTo").in(appliedToTypes));
            }

            // Filter out deleted discounts (this method is for menu discounts, always show non-deleted)
            predicates.add(criteriaBuilder.equal(root.get(FIELD_IS_DELETED), false));

            // Exclude discounts where max usage has been reached (treat as expired)
            // Note: maxUses = 0 is treated as unlimited (no limit), similar to null
            predicates.add(
                criteriaBuilder.or(
                    // If maxUses is null, always allow (no limit)
                    criteriaBuilder.isNull(root.get(FIELD_MAX_USES)),
                    // If maxUses is 0, always allow (unlimited uses)
                    criteriaBuilder.equal(root.get(FIELD_MAX_USES), 0),
                    // If maxUses > 0, check that currentUsage < maxUses
                    criteriaBuilder.and(
                        criteriaBuilder.isNotNull(root.get(FIELD_MAX_USES)),
                        criteriaBuilder.greaterThan(root.get(FIELD_MAX_USES), 0),
                        criteriaBuilder.or(
                            // If currentUsage is null, treat as 0 (less than maxUses)
                            criteriaBuilder.isNull(root.get(FIELD_CURRENT_USAGE)),
                            // If currentUsage is not null, check currentUsage < maxUses
                            criteriaBuilder.lessThan(root.get(FIELD_CURRENT_USAGE), root.get(FIELD_MAX_USES))
                        )
                    )
                )
            );

            // Add search filter (name/description/discountCode - search in all translations)
            if (search != null && !search.trim().isEmpty()) {
                String searchTerm = "%" + search.trim().toLowerCase() + "%";
                Join<Discount, DiscountTranslation> translationJoin = root.join("translations", JoinType.LEFT);

                Predicate searchMatch = criteriaBuilder.or(
                    criteriaBuilder.like(criteriaBuilder.lower(translationJoin.get("name")), searchTerm),
                    criteriaBuilder.like(criteriaBuilder.lower(root.get(FIELD_DISCOUNT_CODE)), searchTerm)
                );

                predicates.add(searchMatch);
            }

            // Add distinct to avoid duplicates due to joins
            query.distinct(true);

            return criteriaBuilder.and(predicates.toArray(new Predicate[0]));
        };

        // Create Pageable with sorting
        Sort sort = Sort.by(direction, FIELD_CREATED_AT);
        if ("name".equals(sortBy)) {
            // For name sorting, we need to sort by translation name
            // This is complex with JPA, so we'll handle it after fetching
            sort = Sort.by(direction, FIELD_CREATED_AT);
        }
        
        // Fetch ALL discounts matching the specification (without pagination) 
        // because we need to apply in-memory filters (time-based, search) before pagination
        List<Discount> allDiscounts = discountRepository.findAll(spec, sort);

        // Convert to response DTOs with restaurant-specific or menu-specific overrides
        List<DiscountResponse> discountResponses = allDiscounts.stream().map((Discount discount) -> {
            // Get restaurant-specific or menu-specific override values
            RestaurantDiscountMapping restaurantMapping = null;
            MenuDiscountMapping menuMapping = null;
            
            if (restaurantId != null && appliedToTypes != null && appliedToTypes.contains(AppliedTo.ORDER)) {
                // Use RestaurantDiscountMapping when restaurantId is provided
                restaurantMapping = discountIdToRestaurantMapping.get(discount.getId());
            } else {
                // Use MenuDiscountMapping for original logic
                menuMapping = discountIdToMenuMapping.get(discount.getId());
            }
            
            // Extract BXGY info from first DiscountBxgyItem if present
            UUID purchasedItemId = null;
            UUID freeItemId = null;
            if (discount.getBxgyItems() != null && !discount.getBxgyItems().isEmpty()) {
                DiscountBxgyItem bxgyItem = discount.getBxgyItems().get(0);
                if (bxgyItem.getBuyItemMapping() != null) {
                    purchasedItemId = bxgyItem.getBuyItemMapping().getItem().getId();
                }
                if (bxgyItem.getGetItemMapping() != null) {
                    freeItemId = bxgyItem.getGetItemMapping().getItem().getId();
                }
            }
            
            List<DiscountTranslationResponse> translationResponses = List.of(
                    buildResolvedDiscountTranslation(discount.getTranslations(), locale, discount.getDiscountCode()));
            
            // Calculate promotion assignment for each discount
            boolean assignedToPromotion = promotionRepository.existsByDiscountIdAndIsDeletedFalse(discount.getId());
            
            String currency = restaurantChainConfigProperties.getChain() != null ? restaurantChainConfigProperties.getChain().getCurrency() : null;

            String createdByForResponse = null;
            if (discount.getCreatedBy() != null) {
                String createdFirst = discount.getCreatedBy().getFirstName() != null ? discount.getCreatedBy().getFirstName() : "";
                String createdLastRaw = discount.getCreatedBy().getLastName() != null ? discount.getCreatedBy().getLastName() : "";
                createdByForResponse = createdFirst + " " + createdLastRaw.trim();
            }
            String updatedByForResponse = null;
            if (discount.getUpdatedBy() != null) {
                String updatedFirst = discount.getUpdatedBy().getFirstName() != null ? discount.getUpdatedBy().getFirstName() : "";
                String updatedLastRaw = discount.getUpdatedBy().getLastName() != null ? discount.getUpdatedBy().getLastName() : "";
                updatedByForResponse = updatedFirst + " " + updatedLastRaw.trim();
            }
            OffsetDateTime validFromForResponse = restaurantMapping != null ? restaurantMapping.getValidFrom()
                    : (menuMapping != null ? menuMapping.getValidFrom() : null);
            OffsetDateTime validToForResponse = restaurantMapping != null ? restaurantMapping.getValidTo()
                    : (menuMapping != null ? menuMapping.getValidTo() : null);

            return DiscountResponse.builder()
                .id(discount.getId())
                .discountCode(discount.getDiscountCode())
                .discountType(discount.getDiscountType())
                .appliedTo(discount.getAppliedTo())
                .value(discount.getValue() != null ? CurrencyFormatter.formatAmount(discount.getValue(), currency) : null)
                .orderValueThreshold(discount.getOrderValueThreshold() != null ? CurrencyFormatter.formatAmount(discount.getOrderValueThreshold(), currency) : null)
                .maxDiscountValue(discount.getMaxDiscountValue() != null ? CurrencyFormatter.formatAmount(discount.getMaxDiscountValue(), currency) : null)
                .buyQuantity(discount.getBuyQuantity())
                .getQuantity(discount.getGetQuantity())
                .maxUses(discount.getMaxUses())
                .currentUsage(discount.getCurrentUsage())
                .purchasedItemId(purchasedItemId)
                .freeItemId(freeItemId)
                .status(discount.getStatus())
                .isDeleted(discount.getIsDeleted())
                .createdAt(discount.getCreatedAt() != null ? discount.getCreatedAt().toLocalDateTime() : null)
                .createdBy(createdByForResponse)
                .updatedAt(discount.getUpdatedAt() != null ? discount.getUpdatedAt().toLocalDateTime() : null)
                .updatedBy(updatedByForResponse)
                .translations(translationResponses)
                .validFrom(validFromForResponse)
                .validTo(validToForResponse)
                .assignedToPromotion(assignedToPromotion)
                .build();
        }).collect(Collectors.toList());

        // Apply hidden-discount filtering only when restaurant context is provided.
        // If restaurantId is absent, return all discounts assigned to this menu.
        if (restaurantId != null) {
            discountResponses = discountResponses.stream()
                    .filter(discount -> {
                        if (appliedToTypes != null && appliedToTypes.contains(AppliedTo.ORDER)) {
                            RestaurantDiscountMapping rdm = discountIdToRestaurantMapping.get(discount.getId());
                            return rdm != null && !Boolean.TRUE.equals(rdm.getIsHide());
                        }
                        MenuDiscountMapping mdm = discountIdToMenuMapping.get(discount.getId());
                        return mdm != null && !Boolean.TRUE.equals(mdm.getIsHide());
                    })
                    .collect(Collectors.toList());
        }

        // Apply time-based validity filtering only if applyDayFilter is true
        if (applyDayFilter) {
            OffsetDateTime nowUtc = OffsetDateTime.now(ZoneOffset.UTC);
            com.gulfnet.shared_library.enums.DayOfWeek currentDay = convertToDayOfWeek(nowUtc.getDayOfWeek());
            
            discountResponses = discountResponses.stream()
                .filter(discount -> {
                    // Exclude discounts where max usage has been reached (treat as expired)
                    // Note: maxUses = 0 is treated as unlimited (no limit), similar to null
                    if (discount.getMaxUses() != null && discount.getMaxUses() > 0 
                        && discount.getCurrentUsage() != null 
                        && discount.getCurrentUsage() >= discount.getMaxUses()) {
                        return false;
                    }
                    
                    // Get the appropriate mapping based on whether restaurantId is provided
                    RestaurantDiscountMapping restaurantMapping = null;
                    MenuDiscountMapping menuMapping = null;
                    
                    if (restaurantId != null && appliedToTypes != null && appliedToTypes.contains(AppliedTo.ORDER)) {
                        // Use RestaurantDiscountMapping when restaurantId is provided
                        restaurantMapping = discountIdToRestaurantMapping.get(discount.getId());
                        
                        // If no restaurant mapping exists, exclude the discount
                        if (restaurantMapping == null) {
                            return false;
                        }
                        
                        // Check status - if INACTIVE, exclude the discount
                        if (restaurantMapping.getStatus() != null && restaurantMapping.getStatus() != EntityStatus.ACTIVE) {
                            return false;
                        }

                        // Check hidden flag - if hidden, exclude the discount
                        if (Boolean.TRUE.equals(restaurantMapping.getIsHide())) {
                            return false;
                        }
                        
                        // Check valid_from and valid_to dates from RestaurantDiscountMapping
                        if (restaurantMapping.getValidFrom() != null && nowUtc.isBefore(restaurantMapping.getValidFrom())) {
                            return false; // Not yet valid
                        }
                        if (restaurantMapping.getValidTo() != null && nowUtc.isAfter(restaurantMapping.getValidTo())) {
                            return false; // Expired
                        }
                        
                        // Check start_time and end_time restrictions from RestaurantDiscountMapping
                        if (restaurantMapping.getStartTime() != null && restaurantMapping.getEndTime() != null) {
                            // Convert all times to UTC to ensure consistent comparison regardless of timezone offsets
                            OffsetTime currentTime = nowUtc.toOffsetTime();
                            OffsetTime startTime = convertToUtc(restaurantMapping.getStartTime());
                            OffsetTime endTime = convertToUtc(restaurantMapping.getEndTime());
                            
                            if (!startTime.equals(endTime) && startTime.isBefore(endTime)) {
                                // Normal case: start < end (e.g., 12:00 to 18:00)
                                if (currentTime.isBefore(startTime) || currentTime.isAfter(endTime)) {
                                    return false;
                                }
                            } else if (!startTime.equals(endTime)) {
                                // Overnight case: start > end (e.g., 23:00 to 02:00)
                                if (currentTime.isBefore(startTime) && currentTime.isAfter(endTime)) {
                                    return false;
                                }
                            }
                            // 24-hour availability (startTime.equals(endTime)) - always allow, no need to check time restrictions
                        }
                        
                        // Check days of week restrictions from RestaurantDiscountMapping
                        if (restaurantMapping.getDaysOfWeek() != null && !restaurantMapping.getDaysOfWeek().isEmpty()) {
                            if (!restaurantMapping.getDaysOfWeek().contains(currentDay)) {
                                return false;
                            }
                        }
                    } else {
                        // Use MenuDiscountMapping for original logic
                        menuMapping = discountIdToMenuMapping.get(discount.getId());
                        
                        // If no menu mapping exists, exclude the discount (it's not valid for this menu)
                        if (menuMapping == null) {
                            return false;
                        }

                        // Check valid_from and valid_to dates
                        if (menuMapping.getValidFrom() != null && nowUtc.isBefore(menuMapping.getValidFrom())) {
                            return false; // Not yet valid
                        }
                        if (menuMapping.getValidTo() != null && nowUtc.isAfter(menuMapping.getValidTo())) {
                            return false; // Expired
                        }
                        
                        // Check start_time and end_time restrictions
                        if (menuMapping.getStartTime() != null && menuMapping.getEndTime() != null) {
                            // Convert all times to UTC to ensure consistent comparison regardless of timezone offsets
                            OffsetTime currentTime = nowUtc.toOffsetTime();
                            OffsetTime startTime = convertToUtc(menuMapping.getStartTime());
                            OffsetTime endTime = convertToUtc(menuMapping.getEndTime());
                            
                            if (!startTime.equals(endTime) && startTime.isBefore(endTime)) {
                                // Normal case: start < end (e.g., 12:00 to 18:00)
                                if (currentTime.isBefore(startTime) || currentTime.isAfter(endTime)) {
                                    return false;
                                }
                            } else if (!startTime.equals(endTime)) {
                                // Overnight case: start > end (e.g., 23:00 to 02:00)
                                if (currentTime.isBefore(startTime) && currentTime.isAfter(endTime)) {
                                    return false;
                                }
                            }
                            // 24-hour availability (startTime.equals(endTime)) - always allow, no need to check time restrictions
                        }
                        
                        // Check days of week restrictions
                        if (menuMapping.getDaysOfWeek() != null && !menuMapping.getDaysOfWeek().isEmpty()) {
                            if (!menuMapping.getDaysOfWeek().contains(currentDay)) {
                                return false;
                            }
                        }
                    }
                    
                    return true;
                })
                .collect(Collectors.toList());
        }

        // Apply post-processing search filter (since we now fetch all discounts)
        if (search != null && !search.trim().isEmpty()) {
            String searchTerm = search.trim().toLowerCase();
            discountResponses = discountResponses.stream()
                .filter(discount -> {
                    // Check discount code
                    if (discount.getDiscountCode() != null && 
                        discount.getDiscountCode().toLowerCase().contains(searchTerm)) {
                        return true;
                    }
                    
                    // Check discount name in the selected translation
                    if (discount.getTranslations() != null && !discount.getTranslations().isEmpty()) {
                        String discountName = discount.getTranslations().get(0).getName();
                        if (discountName != null && discountName.toLowerCase().contains(searchTerm)) {
                            return true;
                        }
                    }
                    
                    return false;
                })
                .collect(Collectors.toList());
        }

        // Handle name sorting after fetching (since JPA sorting by joined fields is complex)
        if ("name".equals(sortBy)) {
            discountResponses.sort((a, b) -> {
                String nameA = a.getTranslations() != null ? 
                    a.getTranslations().stream().findFirst().map(t -> t.getName() != null ? t.getName() : "").orElse("") : "";
                String nameB = b.getTranslations() != null ? 
                    b.getTranslations().stream().findFirst().map(t -> t.getName() != null ? t.getName() : "").orElse("") : "";
                return direction == Sort.Direction.ASC ? nameA.compareToIgnoreCase(nameB) : nameB.compareToIgnoreCase(nameA);
            });
        }

        // Get total count after all filters (before pagination)
        long totalFilteredCount = discountResponses.size();
        
        // Apply pagination to the filtered list
        int start = pageNumber * pageSize;
        int end = Math.min(start + pageSize, discountResponses.size());
        List<DiscountResponse> paginatedDiscounts = (start < discountResponses.size()) 
            ? discountResponses.subList(start, end) 
            : new ArrayList<>();
        
        // Current page count
        long currentPageCount = paginatedDiscounts.size();

        // Build response with proper pagination metadata
        ItemDiscountListResponse response = ItemDiscountListResponse.builder()
            .menuId(menuId)
            .discounts(paginatedDiscounts)
            .count(currentPageCount)
            .total(totalFilteredCount) // Total count after all filters applied
            .metaData(PaginationMetaData.builder()
                .page(pageNumber + 1) // Convert back to 1-based
                .size(pageSize)
                .totalRecords(totalFilteredCount) // Total count after all filters applied
                .totalPages((int) Math.ceil((double) totalFilteredCount / pageSize)) // Calculate pages based on total filtered count
                .build())
            .build();
    
        return ResponseDto.<ItemDiscountListResponse>builder()
            .data(response)
            .message(messageUtil.getMessage(MSG_MENU_DISCOUNT_LIST_SUCCESS, userLocale))
            .build();
    }

    /**
     * Unassigns a discount from all categories in a menu.
     * Deletes all category discount mappings, menu discount mappings, and restaurant discount mappings
     * for restaurants assigned to the menu.
     *
     * @param menuId    the UUID of the menu
     * @param discountId the UUID of the discount to unassign
     * @param updaterId  the ID of the user performing the unassignment
     * @param locale     locale code for localized error messages
     * @return ResponseDto containing list of remaining assigned categories
     * @throws ResponseStatusException if menu not found or discount not found
     */
    @Override
    @Transactional
    @CacheEvict(value = "restaurants", allEntries = true)
    public ResponseDto<DiscountAssignmentCategoryListResponse> unassignDiscountFromAllCategories(UUID menuId, UUID discountId, String updaterId, String locale) {
        Locale userLocale = Locale.forLanguageTag(locale);
        
        // Validate menu exists
        Menu menu = menuRepository.findById(menuId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, messageUtil.getMessage(MSG_MENU_NOT_FOUND, userLocale)));
        
        // Validate discount exists
        Discount discount = discountRepository.findById(discountId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, messageUtil.getMessage(MSG_DISCOUNT_NOT_FOUND, userLocale)));
        
        // Get all categories in this menu
        List<MenuCategoryMapping> menuCategoryMappings = menuCategoryMappingRepository.findByMenuId(menuId);
        List<UUID> categoryIds = menuCategoryMappings.stream()
            .map(mcm -> mcm.getCategory().getId())
            .collect(Collectors.toList());
        
        // Find all category-discount mappings for this discount via menu_category_mapping_id
        List<CategoryDiscountMapping> mappingsToDelete = new ArrayList<>();
        for (UUID categoryId : categoryIds) {
            Optional<MenuCategoryMapping> maybeMcm = menuCategoryMappingRepository.findByMenuIdAndCategoryId(menuId, categoryId);
            if (maybeMcm.isPresent()) {
                List<CategoryDiscountMapping> categoryMappings = categoryDiscountMappingRepository.findByMenuCategoryMapping(maybeMcm.get());
                mappingsToDelete.addAll(categoryMappings.stream()
                    .filter(mapping -> mapping.getDiscount().getId().equals(discountId))
                    .collect(Collectors.toList()));
            }
        }
        
        // Delete all category-discount mappings
        categoryDiscountMappingRepository.deleteAll(mappingsToDelete);
        
        // Delete from menu_discount_mapping since all categories are unassigned
        List<MenuDiscountMapping> menuDiscountMappings = menuDiscountMappingRepository.findByMenuId(menuId);
        menuDiscountMappings.stream()
            .filter(mdm -> mdm.getDiscount().getId().equals(discountId))
            .forEach(menuDiscountMappingRepository::delete);
        
        // Delete restaurant-discount mappings for all restaurants assigned to this menu
        deleteRestaurantDiscountMappingsForMenu(menuId, discountId);
        
        // Get remaining assigned categories for response via MCM
        List<CategoryDiscountMapping> remainingMappings = categoryDiscountMappingRepository.findByDiscount(discount);
        List<UUID> assignedCategoryIds = remainingMappings.stream()
            .map(m -> m.getMenuCategoryMapping().getCategory().getId())
            .collect(Collectors.toList());
        
        DiscountAssignmentCategoryListResponse response = new DiscountAssignmentCategoryListResponse();
        response.setDiscountId(discount.getId());
        response.setAssignedCategoryIds(assignedCategoryIds);
        response.setAssignedMenuIds(new ArrayList<>(Arrays.asList(menuId)));
        
        return ResponseDto.<DiscountAssignmentCategoryListResponse>builder()
            .data(response)
            .message(messageUtil.getMessage(MSG_DISCOUNT_BULK_UNASSIGNMENT_CATEGORIES_SUCCESS, userLocale))
            .build();
    }

    /**
     * Unassigns a discount from all items in a menu.
     * Finds all items in the menu's categories and deletes their item discount mappings.
     * Also deletes menu discount mappings and restaurant discount mappings for restaurants assigned to the menu.
     *
     * @param menuId    the UUID of the menu
     * @param discountId the UUID of the discount to unassign
     * @param updaterId  the ID of the user performing the unassignment
     * @param locale     locale code for localized error messages
     * @return ResponseDto containing list of remaining assigned items
     * @throws ResponseStatusException if menu not found or discount not found
     */
    @Override
    @Transactional
    @CacheEvict(value = "restaurants", allEntries = true)
    public ResponseDto<DiscountAssignmentListResponse> unassignDiscountFromAllItems(UUID menuId, UUID discountId, String updaterId, String locale) {
        Locale userLocale = Locale.forLanguageTag(locale);
        
        // Validate menu exists
        Menu menu = menuRepository.findById(menuId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, messageUtil.getMessage(MSG_MENU_NOT_FOUND, userLocale)));
        
        // Validate discount exists
        Discount discount = discountRepository.findById(discountId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, messageUtil.getMessage(MSG_DISCOUNT_NOT_FOUND, userLocale)));
        
        // Get all categories in this menu
        List<MenuCategoryMapping> menuCategoryMappings = menuCategoryMappingRepository.findByMenuId(menuId);
        List<UUID> categoryIds = menuCategoryMappings.stream()
            .map(mcm -> mcm.getCategory().getId())
            .collect(Collectors.toList());
        
        // Find all items in these categories
        Set<UUID> itemIds = new HashSet<>();
        for (UUID categoryId : categoryIds) {
            Category category = categoryRepository.findByIdAndIsDeletedFalse(categoryId).orElse(null);
            if (category != null) {
                Optional<MenuCategoryMapping> maybeMcm = menuCategoryMappingRepository.findByMenuIdAndCategoryId(menuId, categoryId);
                List<CategoryItemMapping> categoryItemMappings = maybeMcm
                    .map(categoryItemMappingRepository::findByMenuCategoryMapping)
                    .orElse(Collections.emptyList());
                itemIds.addAll(categoryItemMappings.stream()
                    .map(cim -> cim.getItem().getId())
                    .collect(Collectors.toSet()));
            }
        }
        
        // Find all item-discount mappings for this discount that belong to this menu
        // First, get all CategoryItemMappings for this menu
        List<CategoryItemMapping> menuCategoryItemMappings = categoryItemMappingRepository.findByMenuCategoryMappingMenuId(menuId);
        Set<UUID> menuCategoryItemMappingIds = menuCategoryItemMappings.stream()
            .map(CategoryItemMapping::getId)
            .collect(Collectors.toSet());
        
        // Find all item-discount mappings for these CategoryItemMappings and this discount
        List<ItemDiscountMapping> mappingsToDelete = new ArrayList<>();
        for (CategoryItemMapping cim : menuCategoryItemMappings) {
            List<ItemDiscountMapping> discountMappings = itemDiscountMappingRepository.findByCategoryItemMapping(cim);
            mappingsToDelete.addAll(discountMappings.stream()
                .filter(mapping -> mapping.getDiscount().getId().equals(discountId))
                .collect(Collectors.toList()));
        }
        
        log.info("Found {} item-discount mappings to delete for discount {} in menu {}", 
            mappingsToDelete.size(), discountId, menuId);
        
        // Delete all item-discount mappings
        if (!mappingsToDelete.isEmpty()) {
            itemDiscountMappingRepository.deleteAll(mappingsToDelete);
            log.info("Successfully deleted {} item-discount mappings for discount {} in menu {}", 
                mappingsToDelete.size(), discountId, menuId);
        } else {
            log.warn("No item-discount mappings found to delete for discount {} in menu {}", 
                discountId, menuId);
        }
        
        // Delete BXGY item mappings for this discount that belong to this menu
        // Reuse menuCategoryItemMappings and menuCategoryItemMappingIds from above
        // Get all BXGY mappings for this discount
        List<DiscountBxgyItem> allBxgyMappings = discountBxgyItemRepository.findByDiscountId(discountId);
        
        // Filter to only BXGY mappings where buyItemMapping or getItemMapping belongs to this menu
        List<DiscountBxgyItem> bxgyMappingsToDelete = allBxgyMappings.stream()
            .filter(bxgy -> {
                // Check if buyItemMapping belongs to this menu
                boolean buyItemInMenu = bxgy.getBuyItemMapping() != null 
                    && menuCategoryItemMappingIds.contains(bxgy.getBuyItemMapping().getId());
                
                // Check if getItemMapping belongs to this menu
                boolean getItemInMenu = bxgy.getGetItemMapping() != null 
                    && menuCategoryItemMappingIds.contains(bxgy.getGetItemMapping().getId());
                
                // Delete if either buy or get item mapping belongs to this menu
                return buyItemInMenu || getItemInMenu;
            })
            .collect(Collectors.toList());
        
        log.info("Found {} BXGY mappings to delete for discount {} in menu {}", 
            bxgyMappingsToDelete.size(), discountId, menuId);
        
        // Delete all BXGY item mappings
        if (!bxgyMappingsToDelete.isEmpty()) {
            discountBxgyItemRepository.deleteAll(bxgyMappingsToDelete);
            log.info("Successfully deleted {} BXGY mappings for discount {} in menu {}", 
                bxgyMappingsToDelete.size(), discountId, menuId);
        } else {
            log.warn("No BXGY mappings found to delete for discount {} in menu {}", 
                discountId, menuId);
        }
        
        // Delete from menu_discount_mapping since all items are unassigned
        List<MenuDiscountMapping> menuDiscountMappings = menuDiscountMappingRepository.findByMenuId(menuId);
        List<MenuDiscountMapping> mappingsToDeleteFromMenu = menuDiscountMappings.stream()
            .filter(mdm -> mdm.getDiscount().getId().equals(discountId))
            .collect(Collectors.toList());
        
        log.info("Found {} MenuDiscountMapping records to delete for discount {} in menu {}", 
            mappingsToDeleteFromMenu.size(), discountId, menuId);
        
        if (!mappingsToDeleteFromMenu.isEmpty()) {
            menuDiscountMappingRepository.deleteAll(mappingsToDeleteFromMenu);
            log.info("Successfully deleted {} MenuDiscountMapping records for discount {} in menu {}", 
                mappingsToDeleteFromMenu.size(), discountId, menuId);
        } else {
            log.warn("No MenuDiscountMapping records found to delete for discount {} in menu {}", 
                discountId, menuId);
        }
        
        // Delete restaurant-discount mappings for all restaurants assigned to this menu
        deleteRestaurantDiscountMappingsForMenu(menuId, discountId);
        
        // Verify deletion was successful
        List<MenuDiscountMapping> remainingMenuMappings = menuDiscountMappingRepository.findByMenuId(menuId);
        boolean discountStillExists = remainingMenuMappings.stream()
            .anyMatch(mdm -> mdm.getDiscount().getId().equals(discountId));
        
        if (discountStillExists) {
            log.error("MenuDiscountMapping still exists after deletion attempt for discount {} in menu {}", 
                discountId, menuId);
        } else {
            log.info("MenuDiscountMapping successfully removed for discount {} in menu {}", 
                discountId, menuId);
        }
        
        // Get remaining assigned items for response (including BXGY items)
        List<ItemDiscountMapping> remainingMappings = itemDiscountMappingRepository.findByDiscount(discount);
        List<UUID> assignedItemIds = remainingMappings.stream()
            .map(m -> m.getCategoryItemMapping().getItem().getId())
            .collect(Collectors.toList());
        
        // Also include BXGY assigned items in the response with null safety
        List<DiscountBxgyItem> remainingBxgyMappings = discountBxgyItemRepository.findByDiscountId(discountId);
        Set<UUID> bxgyItemIds = new HashSet<>();
        for (DiscountBxgyItem bxgy : remainingBxgyMappings) {
            // Add null safety checks for BXGY mappings
            if (bxgy.getBuyItemMapping() != null && bxgy.getBuyItemMapping().getItem() != null) {
                bxgyItemIds.add(bxgy.getBuyItemMapping().getItem().getId());
            }
            if (bxgy.getGetItemMapping() != null && bxgy.getGetItemMapping().getItem() != null) {
                bxgyItemIds.add(bxgy.getGetItemMapping().getItem().getId());
            }
        }
        assignedItemIds.addAll(bxgyItemIds);
        
        // Clean up orphaned BXGY items with null mappings
        List<DiscountBxgyItem> orphanedBxgyItems = remainingBxgyMappings.stream()
            .filter(bxgy -> (bxgy.getBuyItemMapping() == null || bxgy.getGetItemMapping() == null))
            .collect(Collectors.toList());
        
        if (!orphanedBxgyItems.isEmpty()) {
            discountBxgyItemRepository.deleteAll(orphanedBxgyItems);
        }
        
        DiscountAssignmentListResponse response = new DiscountAssignmentListResponse();
        response.setDiscountId(discount.getId());
        response.setAssignedItemIds(assignedItemIds);
        
        return ResponseDto.<DiscountAssignmentListResponse>builder()
            .data(response)
            .message(messageUtil.getMessage(MSG_DISCOUNT_BULK_UNASSIGNMENT_ITEMS_SUCCESS, userLocale))
            .build();
    }

    /**
     * Retrieves detailed discount information for a discount assigned to a menu.
     * Includes menu-specific validity dates, schedule, and assigned category/item IDs based on discount type.
     *
     * @param menuId    the UUID of the menu
     * @param discountId the UUID of the discount
     * @param locale     locale code for localized responses
     * @return ResponseDto containing detailed discount information with menu-specific settings
     * @throws ResponseStatusException if menu not found, discount not found, or discount not assigned to menu
     */
    @Override
    @Transactional(readOnly = true)
    public ResponseDto<DiscountDetailsResponse> getDiscountDetailsWithMenu(UUID menuId, UUID discountId, String locale) {
        Locale userLocale = Locale.forLanguageTag(locale);
        
        // Validate menu exists
        Menu menu = menuRepository.findById(menuId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, messageUtil.getMessage(MSG_MENU_NOT_FOUND, userLocale)));
        
        // Validate discount exists
        Discount discount = discountRepository.findById(discountId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, messageUtil.getMessage(MSG_DISCOUNT_NOT_FOUND, userLocale)));
        
        // Check if discount is assigned to this menu
        List<MenuDiscountMapping> menuDiscountMappings = menuDiscountMappingRepository.findByMenuId(menuId);
        MenuDiscountMapping menuMapping = menuDiscountMappings.stream()
            .filter(mdm -> mdm.getDiscount().getId().equals(discountId))
            .findFirst()
            .orElse(null);
        
        if (menuMapping == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, 
                messageUtil.getMessage(MSG_DISCOUNT_NOT_ASSIGNED_TO_MENU, userLocale));
        }
        
        // Get menu-specific override values (dates are now required during assignment)
        OffsetDateTime validFrom = menuMapping.getValidFrom();
        OffsetDateTime validTo = menuMapping.getValidTo();
        Boolean isHide = menuMapping.getIsHide();
        
        // Get schedule values
        List<DayOfWeek> daysOfWeek = null;
        OffsetTime startTime = null;
        OffsetTime endTime = null;
        
        if (menuMapping.getDaysOfWeek() != null && !menuMapping.getDaysOfWeek().isEmpty()) {
            daysOfWeek = menuMapping.getDaysOfWeek();
            startTime = menuMapping.getStartTime();
            endTime = menuMapping.getEndTime();
        }
        
        List<UUID> categoryIds = null;
        List<UUID> itemIds = null;
        
        // Get assigned IDs based on what the discount is applied to
        if (discount.getAppliedTo() == AppliedTo.CATEGORY) {
            // Get assigned category IDs for this menu
            List<MenuCategoryMapping> menuCategoryMappings = menuCategoryMappingRepository.findByMenuId(menuId);
            List<UUID> allCategoryIds = menuCategoryMappings.stream()
                .map(mcm -> mcm.getCategory().getId())
                .collect(Collectors.toList());
            
            // Batch fetch all categories at once
            Map<UUID, Category> categoryMap = allCategoryIds.isEmpty() 
                ? Collections.emptyMap()
                : categoryRepository.findAllById(allCategoryIds).stream()
                    .filter(c -> !Boolean.TRUE.equals(c.getIsDeleted()))
                    .collect(Collectors.toMap(Category::getId, c -> c));
            
            // Build map: categoryId -> MenuCategoryMapping
            Map<UUID, MenuCategoryMapping> categoryToMcmMap = menuCategoryMappings.stream()
                .collect(Collectors.toMap(mcm -> mcm.getCategory().getId(), mcm -> mcm));
            
            // Batch fetch all CategoryDiscountMappings for this menu and discount
            List<UUID> menuCategoryMappingIds = menuCategoryMappings.stream()
                .map(MenuCategoryMapping::getId)
                .collect(Collectors.toList());
            List<CategoryDiscountMapping> allCategoryDiscountMappings = menuCategoryMappingIds.isEmpty()
                ? Collections.emptyList()
                : categoryDiscountMappingRepository.findByMenuCategoryMappingIdsAndDiscountId(menuCategoryMappingIds, discountId);
            
            // Build set of assigned category IDs
            Set<UUID> assignedCategoryIds = allCategoryDiscountMappings.stream()
                .map(cdm -> cdm.getMenuCategoryMapping().getCategory().getId())
                .collect(Collectors.toSet());
            
            categoryIds = allCategoryIds.stream()
                .filter(categoryId -> categoryMap.containsKey(categoryId) && assignedCategoryIds.contains(categoryId))
                .collect(Collectors.toList());
        } else if (discount.getAppliedTo() == AppliedTo.ITEM) {
            // Get assigned item IDs for this menu (both regular and BXGY items)
            List<MenuCategoryMapping> menuCategoryMappings = menuCategoryMappingRepository.findByMenuId(menuId);
            List<UUID> allCategoryIds = menuCategoryMappings.stream()
                .map(mcm -> mcm.getCategory().getId())
                .collect(Collectors.toList());
            
            // Batch fetch all CategoryItemMappings for this menu at once
            List<CategoryItemMapping> allCategoryItemMappings = categoryItemMappingRepository.findByMenuCategoryMappingIn(menuCategoryMappings);
            Set<UUID> allItemIds = allCategoryItemMappings.stream()
                .map(cim -> cim.getItem().getId())
                .collect(Collectors.toSet());
            
            // Check if this is a BXGY discount
            if (discount.getDiscountType() == DiscountType.BXGY) {
                // Handle BXGY discount - populate buyItemIds and getItemIds separately
                Set<UUID> buyItemIdsSet = new HashSet<>();
                Set<UUID> getItemIdsSet = new HashSet<>();
                
                // Step 1: Get all CategoryItemMapping IDs that belong to this menu's categories (already fetched above)
                Set<UUID> menuCategoryItemMappingIds = allCategoryItemMappings.stream()
                    .map(CategoryItemMapping::getId)
                    .collect(Collectors.toSet());
                
                // Step 2: Get all BXGY mappings for this discount
                List<DiscountBxgyItem> bxgyMappings = discountBxgyItemRepository.findByDiscountId(discountId);
                
                // Step 3: Match CategoryItemMapping IDs from discount_bxgy_item with menu's CategoryItemMapping IDs
                for (DiscountBxgyItem bxgyMapping : bxgyMappings) {
                    // Check if buy item's CategoryItemMapping belongs to this menu
                    if (bxgyMapping.getBuyItemMapping() != null) {
                        UUID buyItemMappingId = bxgyMapping.getBuyItemMapping().getId();
                        if (menuCategoryItemMappingIds.contains(buyItemMappingId)) {
                            UUID buyItemId = bxgyMapping.getBuyItemMapping().getItem().getId();
                            buyItemIdsSet.add(buyItemId);
                        }
                    }
                    
                    // Check if get item's CategoryItemMapping belongs to this menu
                    if (bxgyMapping.getGetItemMapping() != null) {
                        UUID getItemMappingId = bxgyMapping.getGetItemMapping().getId();
                        if (menuCategoryItemMappingIds.contains(getItemMappingId)) {
                            UUID getItemId = bxgyMapping.getGetItemMapping().getItem().getId();
                            getItemIdsSet.add(getItemId);
                        }
                    }
                }
                
                // Convert sets to lists for response
                List<UUID> buyItemIds = new ArrayList<>(buyItemIdsSet);
                List<UUID> getItemIds = new ArrayList<>(getItemIdsSet);
                
                // Build response for BXGY discount
                DiscountDetailsResponse response = DiscountDetailsResponse.builder()
                    .discountId(discountId)
                    .categoryIds(categoryIds)
                    .itemIds(Collections.emptyList())  // Not used for BXGY discounts, return empty list to preserve contract
                    .buyItemIds(buyItemIds)
                    .getItemIds(getItemIds)
                    .menuId(menuId)
                    .validFrom(validFrom)
                    .validTo(validTo)
                    .startTime(startTime)
                    .endTime(endTime)
                    .daysOfWeek(daysOfWeek)
                    .isHide(isHide)
                    .build();
                
                return ResponseDto.<DiscountDetailsResponse>builder()
                    .data(response)
                    .message(messageUtil.getMessage(MSG_DISCOUNT_DETAILS_SUCCESS, userLocale))
                    .build();
            } else {
                // Handle regular discount - use batch queries
                // Build map: CategoryItemMapping ID -> CategoryItemMapping for quick lookup
                Map<UUID, CategoryItemMapping> cimMap = allCategoryItemMappings.stream()
                    .collect(Collectors.toMap(CategoryItemMapping::getId, cim -> cim));
                
                // Build set of category IDs for filtering
                Set<UUID> categoryIdSet = new HashSet<>(allCategoryIds);
                
                // Batch fetch all ItemDiscountMappings for these CategoryItemMappings
                List<ItemDiscountMapping> allItemDiscountMappings = allCategoryItemMappings.isEmpty()
                    ? Collections.emptyList()
                    : itemDiscountMappingRepository.findByCategoryItemMappingIn(allCategoryItemMappings);
                
                // Filter ItemDiscountMappings for this discount and build set of assigned item IDs
                Set<UUID> assignedItemIds = allItemDiscountMappings.stream()
                    .filter(idm -> idm.getDiscount().getId().equals(discountId))
                    .filter(idm -> {
                        CategoryItemMapping cim = idm.getCategoryItemMapping();
                        return cim != null && cim.getMenuCategoryMapping() != null 
                            && categoryIdSet.contains(cim.getMenuCategoryMapping().getCategory().getId());
                    })
                    .map(idm -> idm.getCategoryItemMapping().getItem().getId())
                    .collect(Collectors.toSet());
                
                // Batch fetch BXGY mappings for backward compatibility
                List<DiscountBxgyItem> allBxgyMappings = discountBxgyItemRepository.findByDiscountId(discountId);
                Set<UUID> bxgyItemIds = new HashSet<>();
                for (DiscountBxgyItem bxgy : allBxgyMappings) {
                    if (bxgy.getBuyItemMapping() != null && cimMap.containsKey(bxgy.getBuyItemMapping().getId())) {
                        CategoryItemMapping cim = cimMap.get(bxgy.getBuyItemMapping().getId());
                        if (categoryIdSet.contains(cim.getMenuCategoryMapping().getCategory().getId())) {
                            bxgyItemIds.add(bxgy.getBuyItemMapping().getItem().getId());
                        }
                    }
                    if (bxgy.getGetItemMapping() != null && cimMap.containsKey(bxgy.getGetItemMapping().getId())) {
                        CategoryItemMapping cim = cimMap.get(bxgy.getGetItemMapping().getId());
                        if (categoryIdSet.contains(cim.getMenuCategoryMapping().getCategory().getId())) {
                            bxgyItemIds.add(bxgy.getGetItemMapping().getItem().getId());
                        }
                    }
                }
                
                // Combine regular and BXGY item IDs
                assignedItemIds.addAll(bxgyItemIds);
                itemIds = new ArrayList<>(assignedItemIds);
            }
        }
        
        // Build response for regular discounts
        DiscountDetailsResponse response = DiscountDetailsResponse.builder()
            .discountId(discountId)
            .categoryIds(categoryIds)
            .itemIds(itemIds)
            .buyItemIds(Collections.emptyList())  // Not used for regular discounts
            .getItemIds(Collections.emptyList())  // Not used for regular discounts
            .menuId(menuId)
            .validFrom(validFrom)
            .validTo(validTo)
            .startTime(startTime)
            .endTime(endTime)
            .daysOfWeek(daysOfWeek)
            .isHide(isHide)
            .build();
        
        return ResponseDto.<DiscountDetailsResponse>builder()
            .data(response)
            .message(messageUtil.getMessage(MSG_DISCOUNT_DETAILS_SUCCESS, userLocale))
            .build();
    }

    /**
     * Edits the assignment of a discount to categories in a menu.
     * Updates validity dates, times, and schedule in the menu discount mapping.
     * Converts date/time values to UTC. Updates restaurant discount mappings if dates changed.
     *
     * @param discountId the UUID of the discount (from path, authoritative)
     * @param request     the edit request with menu ID, category IDs, and new validity settings
     * @param updaterId   the ID of the user performing the edit
     * @param locale      locale code for localized error messages
     * @return ResponseDto containing updated discount details
     * @throws ResponseStatusException if menu not found, discount not found, not assigned, or validation fails
     */
    @Override
    @Transactional
    @CacheEvict(value = "restaurants", allEntries = true)
    public ResponseDto<DiscountDetailsResponse> editDiscountAssignment(UUID discountId, AssignDiscountToCategoriesRequest request, String updaterId, String locale) {
        Locale userLocale = Locale.forLanguageTag(locale);
        
        // Fetch user
        User user = userRepository.findById(UUID.fromString(updaterId))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        messageUtil.getMessage(MSG_USER_NOT_FOUND, userLocale, updaterId)));
        
        // Extract menuId from request
        UUID menuId = request.getMenuId();
        
        // Validate menu exists
        Menu menu = menuRepository.findById(menuId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, messageUtil.getMessage(MSG_MENU_NOT_FOUND, userLocale)));
        
        // Validate discount exists
        Discount discount = discountRepository.findById(discountId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, messageUtil.getMessage(MSG_DISCOUNT_NOT_FOUND, userLocale)));
        
        // Check if discount is assigned to this menu
        List<MenuDiscountMapping> menuDiscountMappings = menuDiscountMappingRepository.findByMenuId(menuId);
        MenuDiscountMapping menuMapping = menuDiscountMappings.stream()
            .filter(mdm -> mdm.getDiscount().getId().equals(discountId))
            .findFirst()
            .orElse(null);
        
        if (menuMapping == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, 
                messageUtil.getMessage(MSG_DISCOUNT_NOT_ASSIGNED_TO_MENU, userLocale));
        }
        
        // Update menu discount mapping with new values (convert to UTC like combo system)
        if (request.getValidFrom() != null) {
            menuMapping.setValidFrom(convertToUtc(request.getValidFrom()));
        }
        if (request.getValidTo() != null) {
            menuMapping.setValidTo(convertToUtc(request.getValidTo()));
        }
        if (request.getStartTime() != null) {
            menuMapping.setStartTime(convertToUtc(request.getStartTime()));
        }
        if (request.getEndTime() != null) {
            menuMapping.setEndTime(convertToUtc(request.getEndTime()));
        }
        if (request.getDaysOfWeek() != null) {
            menuMapping.setDaysOfWeek(request.getDaysOfWeek());
        }
        
        // Validate time range - allow overnight schedules (e.g., 23:00 to 02:00)
        if (menuMapping.getStartTime() != null && menuMapping.getEndTime() != null) {
            // Only reject if start and end times are the same (invalid)
            if (menuMapping.getStartTime().equals(menuMapping.getEndTime())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    messageUtil.getMessage(MSG_DISCOUNT_ERROR_SCHEDULE_INVALID_TIME_RANGE, userLocale));
            }
            // Note: We allow overnight schedules where endTime < startTime
        }
        
        // Validate that validFrom is not after validTo
        if (menuMapping.getValidFrom() != null && menuMapping.getValidTo() != null &&
            menuMapping.getValidFrom().isAfter(menuMapping.getValidTo())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, 
                messageUtil.getMessage(MSG_DISCOUNT_ERROR_INVALID_DATE_RANGE, userLocale));
        }
        
        // Validate that validTo is not completely in the past (allow extending expired discounts)
        OffsetDateTime nowUtc = OffsetDateTime.now(ZoneOffset.UTC);
        if (menuMapping.getValidTo() != null && menuMapping.getValidTo().isBefore(nowUtc)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, 
                messageUtil.getMessage(MSG_DISCOUNT_ASSIGNMENT_PAST_VALID_TO, userLocale));
        }
        
        // Handle assignments based on discount type
        if (discount.getAppliedTo() == AppliedTo.CATEGORY) {
            // First, remove existing category assignments for this discount in this menu
            List<MenuCategoryMapping> menuCategoryMappings = menuCategoryMappingRepository.findByMenuId(menuId);
            List<UUID> menuCategoryIds = menuCategoryMappings.stream()
                .map(mcm -> mcm.getCategory().getId())
                .collect(Collectors.toList());
            
            for (UUID categoryId : menuCategoryIds) {
                Category category = categoryRepository.findByIdAndIsDeletedFalse(categoryId).orElse(null);
                if (category != null) {
                    Optional<MenuCategoryMapping> maybeMcm = menuCategoryMappingRepository.findByMenuIdAndCategoryId(menuId, categoryId);
                    if (maybeMcm.isPresent()) {
                        List<CategoryDiscountMapping> existingMappings = categoryDiscountMappingRepository.findByMenuCategoryMapping(maybeMcm.get());
                        existingMappings.stream()
                            .filter(mapping -> mapping.getDiscount().getId().equals(discountId))
                            .forEach(categoryDiscountMappingRepository::delete);
                    }
                }
            }
            
            // Now assign new categories
            if (request.getCategoryIds() != null && !request.getCategoryIds().isEmpty()) {
                // Validate that none of the categories are combo type
                for (UUID categoryId : request.getCategoryIds()) {
                    Category category = categoryRepository.findByIdAndIsDeletedFalse(categoryId).orElse(null);
                    if (category != null && Boolean.TRUE.equals(category.getIsCombo())) {
                        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, 
                            messageUtil.getMessage(MSG_DISCOUNT_CANNOT_APPLY_TO_COMBO_CATEGORY, userLocale));
                    }
                }
                
                for (UUID categoryId : request.getCategoryIds()) {
                    if (menuCategoryIds.contains(categoryId)) {
                        Category category = categoryRepository.findByIdAndIsDeletedFalse(categoryId)
                            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, 
                                messageUtil.getMessage(MSG_CATEGORY_NOT_FOUND, userLocale)));
                        
                        MenuCategoryMapping mcm = menuCategoryMappingRepository.findByMenuIdAndCategoryId(menuId, categoryId)
                            .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, 
                                messageUtil.getMessage(MSG_CATEGORY_MENU_MISMATCH, userLocale)));
                        
                        // Validate that the MenuCategoryMapping actually exists in the database
                        if (mcm.getId() == null) {
                            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, 
                                messageUtil.getMessage(MSG_CATEGORY_MENU_MAPPING_INVALID, userLocale));
                        }
                        
                        // Verify the MCM exists by querying it again to ensure it's not stale
                        Optional<MenuCategoryMapping> verifiedMcm = menuCategoryMappingRepository.findById(mcm.getId());
                        if (verifiedMcm.isEmpty()) {
                            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, 
                                messageUtil.getMessage(MSG_CATEGORY_MENU_MAPPING_NOT_FOUND, userLocale));
                        }
                        
                        // Additional safety check: verify the entity exists in the database right before save
                        boolean mcmExists = menuCategoryMappingRepository.existsById(mcm.getId());
                        if (!mcmExists) {
                            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, 
                                messageUtil.getMessage(MSG_CATEGORY_MENU_MAPPING_NOT_FOUND, userLocale));
                        }
                        
                        try {
                            CategoryDiscountMapping mapping = new CategoryDiscountMapping();
                            mapping.setMenuCategoryMapping(verifiedMcm.get()); // Use the verified entity
                            mapping.setDiscount(discount);
                            categoryDiscountMappingRepository.save(mapping);
                        } catch (org.springframework.dao.DataIntegrityViolationException e) {
                            // Handle any remaining constraint violations gracefully
                            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, 
                                messageUtil.getMessage(MSG_CATEGORY_MENU_MAPPING_NOT_FOUND, userLocale));
                        }
                    }
                }
            }
        } else if (discount.getAppliedTo() == AppliedTo.ITEM) {
            // Similar logic for items
            List<MenuCategoryMapping> menuCategoryMappings = menuCategoryMappingRepository.findByMenuId(menuId);
            List<UUID> menuCategoryIds = menuCategoryMappings.stream()
                .map(mcm -> mcm.getCategory().getId())
                .collect(Collectors.toList());
            
            Set<UUID> allItemIds = new HashSet<>();
            for (UUID categoryId : menuCategoryIds) {
                Category category = categoryRepository.findByIdAndIsDeletedFalse(categoryId).orElse(null);
                if (category != null) {
                    // Find MenuCategoryMapping for this category in the menu
                    MenuCategoryMapping mcm = menuCategoryMappingRepository.findByMenuIdAndCategoryId(menuId, categoryId)
                        .orElse(null);
                    if (mcm != null) {
                        List<CategoryItemMapping> categoryItemMappings = categoryItemMappingRepository.findByMenuCategoryMapping(mcm);
                        allItemIds.addAll(categoryItemMappings.stream()
                            .map(cim -> cim.getItem().getId())
                            .collect(Collectors.toSet()));
                    }
                }
            }
            
            // Remove existing item assignments
            for (UUID itemId : allItemIds) {
                Item item = itemRepository.findById(itemId).orElse(null);
                if (item != null) {
                    // Find all CategoryItemMappings for this item
                    List<CategoryItemMapping> itemMappings = categoryItemMappingRepository.findByItem_Id(itemId);
                    for (CategoryItemMapping cim : itemMappings) {
                        // Check if this item mapping belongs to one of the categories being processed
                        if (menuCategoryIds.contains(cim.getMenuCategoryMapping().getCategory().getId())) {
                            List<ItemDiscountMapping> existingMappings = itemDiscountMappingRepository.findByCategoryItemMapping(cim);
                            existingMappings.stream()
                                .filter(mapping -> mapping.getDiscount().getId().equals(discountId))
                                .forEach(itemDiscountMappingRepository::delete);
                        }
                    }
                }
            }
            
            // Assign new items
            if (request.getCategoryIds() != null && !request.getCategoryIds().isEmpty()) {
                for (UUID itemId : request.getCategoryIds()) { // Using categoryIds as itemIds for this case
                    if (allItemIds.contains(itemId)) {
                        Item item = itemRepository.findById(itemId)
                            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, 
                                messageUtil.getMessage(MSG_ITEM_NOT_FOUND, userLocale)));
                        
                        // Find the CategoryItemMapping for this item in the specified menu
                        List<CategoryItemMapping> itemMappings = categoryItemMappingRepository.findByItem_Id(itemId);
                        CategoryItemMapping targetMapping = itemMappings.stream()
                            .filter(mapping -> menuCategoryIds.contains(mapping.getMenuCategoryMapping().getCategory().getId()))
                            .findFirst()
                            .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, 
                                messageUtil.getMessage("item.menu.mismatch", userLocale)));
                        
                        // Validate that the CategoryItemMapping actually exists in the database
                        if (targetMapping.getId() == null) {
                            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, 
                                messageUtil.getMessage("item.category.mapping.invalid", userLocale));
                        }
                        
                        // Verify the CIM exists by querying it again to ensure it's not stale
                        Optional<CategoryItemMapping> verifiedCim = categoryItemMappingRepository.findById(targetMapping.getId());
                        if (verifiedCim.isEmpty()) {
                            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, 
                                messageUtil.getMessage("item.category.mapping.not.found", userLocale));
                        }
                        
                        ItemDiscountMapping mapping = ItemDiscountMapping.builder()
                            .categoryItemMapping(verifiedCim.get())
                            .discount(discount)
                            .build();
                        itemDiscountMappingRepository.save(mapping);
                    }
                }
            }
        }
        
        // Save updated menu mapping
        menuDiscountMappingRepository.save(menuMapping);
        
        /**
         * Create audit trail for discount validity modification (category assignment)
         * 
         * DISCOUNT_MODIFY: Captures changes to discount validity settings (dates, times, days of week)
         * when editing category assignments for a discount in a menu.
         */
        try {
            Restaurant restaurant = null;
            if (user.getRestaurantId() != null) {
                restaurant = restaurantRepository.findById(user.getRestaurantId()).orElse(null);
            }
            
            // Determine if any validity modifications were made
            boolean hasValidityModifications = request.getValidFrom() != null || 
                                              request.getValidTo() != null || 
                                              request.getStartTime() != null || 
                                              request.getEndTime() != null ||
                                              request.getDaysOfWeek() != null;
            
            if (hasValidityModifications) {
                ActionType actionType = ActionType.DISCOUNT_MODIFY;
                StringBuilder notesBuilder = new StringBuilder("Discount validity modified (category assignment): ");
                List<String> changes = new ArrayList<>();
                
                if (request.getValidFrom() != null) {
                    changes.add(String.format(AUDIT_MSG_VALID_FROM, menuMapping.getValidFrom()));
                }
                if (request.getValidTo() != null) {
                    changes.add(String.format(AUDIT_MSG_VALID_TO, menuMapping.getValidTo()));
                }
                if (request.getStartTime() != null) {
                    changes.add(String.format(AUDIT_MSG_START_TIME, menuMapping.getStartTime()));
                }
                if (request.getEndTime() != null) {
                    changes.add(String.format(AUDIT_MSG_END_TIME, menuMapping.getEndTime()));
                }
                if (request.getDaysOfWeek() != null) {
                    changes.add(String.format(AUDIT_MSG_DAYS_OF_WEEK, menuMapping.getDaysOfWeek()));
                }
                
                String notes = notesBuilder.append(String.join(", ", changes)).toString();
                
                auditTrailService.createAuditTrail(
                        user,
                        actionType,
                        restaurant,
                        RequestStatus.NA,
                        null, // ipAddress
                        null, // userAgent
                        discountId,
                        ENTITY_TYPE_DISCOUNT,
                        notes
                );
                log.debug("Created audit trail for discount {} validity modification (category assignment) by user: {}", 
                        discountId, user.getUserCode());
            }
        } catch (Exception e) {
            log.error("Failed to create audit trail for discount validity modification (category assignment): {}", e.getMessage(), e);
            // Don't fail the discount update if audit trail creation fails
        }
        
        // Build response with edit success message
        DiscountDetailsResponse response = getDiscountDetailsWithMenu(menuId, discountId, locale).getData();

        return ResponseDto.<DiscountDetailsResponse>builder()
            .data(response)
            .message(messageUtil.getMessage(MSG_DISCOUNT_ASSIGNMENT_EDIT_SUCCESS, userLocale))
            .build();
    }

    /**
     * Edits the assignment of a discount to items in a menu.
     * Updates validity dates, times, and schedule in the menu discount mapping.
     * Supports both regular and BXGY item assignments. Converts date/time values to UTC.
     * Updates restaurant discount mappings if dates changed.
     *
     * @param discountId the UUID of the discount (from path, authoritative)
     * @param request     the edit request with menu ID, item IDs, and new validity settings
     * @param updaterId   the ID of the user performing the edit
     * @param locale      locale code for localized error messages
     * @return ResponseDto containing updated discount details
     * @throws ResponseStatusException if menu not found, discount not found, not assigned, or validation fails
     */
    @Override
    @Transactional
    @CacheEvict(value = "restaurants", allEntries = true)
    public ResponseDto<DiscountDetailsResponse> editDiscountItemAssignment(UUID discountId, AssignDiscountToItemsRequest request, String updaterId, String locale) {
        Locale userLocale = Locale.forLanguageTag(locale);
        
        // Fetch user
        User user = userRepository.findById(UUID.fromString(updaterId))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        messageUtil.getMessage(MSG_USER_NOT_FOUND, userLocale, updaterId)));
        
        // Extract menuId from request
        UUID menuId = request.getMenuId();
        
        // Validate menu exists
        Menu menu = menuRepository.findById(menuId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, messageUtil.getMessage(MSG_MENU_NOT_FOUND, userLocale)));
        
        // Validate discount exists - use path discountId (not request body discountId if different)
        // The path discountId is the authoritative one for this update operation
        Discount discount = discountRepository.findById(discountId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, messageUtil.getMessage(MSG_DISCOUNT_NOT_FOUND, userLocale)));
        
        log.info("Editing discount item assignment: pathDiscountId={}, requestDiscountId={}, menuId={}", 
            discountId, request.getDiscountId(), menuId);
        
        // Check if discount is assigned to this menu
        List<MenuDiscountMapping> menuDiscountMappings = menuDiscountMappingRepository.findByMenuId(menuId);
        MenuDiscountMapping menuMapping = menuDiscountMappings.stream()
            .filter(mdm -> mdm.getDiscount().getId().equals(discountId))
            .findFirst()
            .orElse(null);
        
        if (menuMapping == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, 
                messageUtil.getMessage(MSG_DISCOUNT_NOT_ASSIGNED_TO_MENU, userLocale));
        }
        
        // Update menu discount mapping with new values (convert to UTC like combo system)
        if (request.getValidFrom() != null) {
            menuMapping.setValidFrom(convertToUtc(request.getValidFrom()));
        }
        if (request.getValidTo() != null) {
            menuMapping.setValidTo(convertToUtc(request.getValidTo()));
        }
        if (request.getStartTime() != null) {
            menuMapping.setStartTime(convertToUtc(request.getStartTime()));
        }
        if (request.getEndTime() != null) {
            menuMapping.setEndTime(convertToUtc(request.getEndTime()));
        }
        if (request.getDaysOfWeek() != null) {
            menuMapping.setDaysOfWeek(request.getDaysOfWeek());
        }
        
        // Validate time range - allow overnight schedules (e.g., 23:00 to 02:00)
        // Only reject if start and end times are the same (invalid)
        if (menuMapping.getStartTime() != null && menuMapping.getEndTime() != null 
                && menuMapping.getStartTime().equals(menuMapping.getEndTime())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                messageUtil.getMessage(MSG_DISCOUNT_ERROR_SCHEDULE_INVALID_TIME_RANGE, userLocale));
        }
        // Note: We allow overnight schedules where endTime < startTime

        // Validate that validFrom is not after validTo
        if (menuMapping.getValidFrom() != null && menuMapping.getValidTo() != null &&
            menuMapping.getValidFrom().isAfter(menuMapping.getValidTo())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, 
                messageUtil.getMessage(MSG_DISCOUNT_ERROR_INVALID_DATE_RANGE, userLocale));
        }
        
        // Validate that validTo is not completely in the past (allow extending expired discounts)
        OffsetDateTime nowUtc = OffsetDateTime.now(ZoneOffset.UTC);
        if (menuMapping.getValidTo() != null && menuMapping.getValidTo().isBefore(nowUtc)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, 
                messageUtil.getMessage(MSG_DISCOUNT_ASSIGNMENT_PAST_VALID_TO, userLocale));
        }
        
    
        // 1. Remove existing item assignments for this discount in this menu
        // 2. Then assign the new items from the request
        // Get all CategoryItemMappings for this menu
        List<CategoryItemMapping> menuCategoryItemMappings = categoryItemMappingRepository.findByMenuCategoryMappingMenuId(menuId);
        List<ItemDiscountMapping> mappingsToDelete = new ArrayList<>();
        for (CategoryItemMapping cim : menuCategoryItemMappings) {
            List<ItemDiscountMapping> existingMappings = itemDiscountMappingRepository.findByCategoryItemMapping(cim);
            mappingsToDelete.addAll(existingMappings.stream()
                .filter(mapping -> mapping.getDiscount().getId().equals(discountId))
                .collect(Collectors.toList()));
        }
        if (!mappingsToDelete.isEmpty()) {
            itemDiscountMappingRepository.deleteAll(mappingsToDelete);
            log.info("Deleted {} existing item-discount mappings for discount {} in menu {}", 
                mappingsToDelete.size(), discountId, menuId);
        }
        
        // Assign new items
        if (request.getItemIds() != null && !request.getItemIds().isEmpty()) {
            for (UUID itemId : request.getItemIds()) {
                // Find the CategoryItemMapping for this item in the specified menu
                CategoryItemMapping targetMapping = categoryItemMappingRepository
                    .findByItemIdAndMenuCategoryMappingMenuId(itemId, menuId)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, 
                        messageUtil.getMessage(MSG_ITEM_NOT_IN_MENU, userLocale)));
                
                ItemDiscountMapping mapping = ItemDiscountMapping.builder()
                    .categoryItemMapping(targetMapping)
                    .discount(discount)
                    .build();
                itemDiscountMappingRepository.save(mapping);
            }
        }
        
        // Check if this is a BXGY assignment (both buyItemIds and getItemIds are provided)
        boolean isBxgyAssignment = request.getBuyItemIds() != null && !request.getBuyItemIds().isEmpty() 
                                 && request.getGetItemIds() != null && !request.getGetItemIds().isEmpty();
        
        if (isBxgyAssignment) {
            // First, remove existing BXGY mappings for this discount in this menu
            // This ensures we don't check conflicts with our own discount's items
            // Reuse menuCategoryItemMappings from above (already fetched for regular item deletion)
            Set<UUID> menuCategoryItemMappingIds = menuCategoryItemMappings.stream()
                .map(CategoryItemMapping::getId)
                .collect(Collectors.toSet());
            
            List<DiscountBxgyItem> existingBxgyMappings = discountBxgyItemRepository.findByDiscountId(discountId);
            List<DiscountBxgyItem> bxgyMappingsToDelete = existingBxgyMappings.stream()
                .filter(bxgy -> {
                    // Check if buyItemMapping belongs to this menu
                    boolean buyItemInMenu = bxgy.getBuyItemMapping() != null 
                        && menuCategoryItemMappingIds.contains(bxgy.getBuyItemMapping().getId());
                    
                    // Check if getItemMapping belongs to this menu
                    boolean getItemInMenu = bxgy.getGetItemMapping() != null 
                        && menuCategoryItemMappingIds.contains(bxgy.getGetItemMapping().getId());
                    
                    // Delete if either buy or get item mapping belongs to this menu
                    return buyItemInMenu || getItemInMenu;
                })
                .collect(Collectors.toList());
            
            if (!bxgyMappingsToDelete.isEmpty()) {
                discountBxgyItemRepository.deleteAll(bxgyMappingsToDelete);
                // Flush to ensure deletions are visible to subsequent queries
                discountBxgyItemRepository.flush();
                log.info("Deleted {} existing BXGY mappings for discount {} in menu {}", 
                    bxgyMappingsToDelete.size(), discountId, menuId);
            } else {
                log.info("No existing BXGY mappings to delete for discount {} in menu {}", discountId, menuId);
            }
            
            // Now check for conflicts with OTHER BXGY discounts (not the current one)
            // Rule: Items in the current BXGY discount should NOT be present in another BXGY discount
            //       in the same menu with overlapping date ranges.
            //       However, two different BXGY discounts CAN have the same items in the same menu
            //       if their date ranges do NOT overlap.
            // Get CategoryItemMapping IDs for the items in this menu
            Set<UUID> buyItemMappingIds = new HashSet<>();
            Set<UUID> getItemMappingIds = new HashSet<>();
            
            for (UUID buyItemId : request.getBuyItemIds()) {
                CategoryItemMapping buyItemMapping = categoryItemMappingRepository
                    .findByItemIdAndMenuCategoryMappingMenuId(buyItemId, menuId)
                    .orElse(null);
                if (buyItemMapping != null) {
                    buyItemMappingIds.add(buyItemMapping.getId());
                }
            }
            
            for (UUID getItemId : request.getGetItemIds()) {
                CategoryItemMapping getItemMapping = categoryItemMappingRepository
                    .findByItemIdAndMenuCategoryMappingMenuId(getItemId, menuId)
                    .orElse(null);
                if (getItemMapping != null) {
                    getItemMappingIds.add(getItemMapping.getId());
                }
            }
            
            // Find conflicts by CategoryItemMapping IDs (ensures we only check items in this menu)
            // This finds OTHER BXGY discounts that have the same items (buy or get) in the same menu
            List<DiscountBxgyItem> conflictingBxgyItems = new ArrayList<>();
            
            if (!buyItemMappingIds.isEmpty()) {
                List<DiscountBxgyItem> buyConflicts = discountBxgyItemRepository.findByBuyItemMappingIdsAndMenuId(
                    new ArrayList<>(buyItemMappingIds),
                    menuId,
                    DiscountType.BXGY,
                    EntityStatus.ACTIVE
                );
                conflictingBxgyItems.addAll(buyConflicts);
            }
            
            if (!getItemMappingIds.isEmpty()) {
                List<DiscountBxgyItem> getConflicts = discountBxgyItemRepository.findByGetItemMappingIdsAndMenuId(
                    new ArrayList<>(getItemMappingIds),
                    menuId,
                    DiscountType.BXGY,
                    EntityStatus.ACTIVE
                );
                conflictingBxgyItems.addAll(getConflicts);
            }
            
            // Remove duplicates and exclude current discount
            // Also verify that the CategoryItemMapping IDs in conflicting items belong to this menu
            // Reuse menuCategoryItemMappingIds from above (already fetched for deletion logic at line 4035)
            Set<UUID> seenBxgyIds = new HashSet<>();
            Set<UUID> foundConflictingDiscountIds = new HashSet<>();
            conflictingBxgyItems = conflictingBxgyItems.stream()
                .filter(bxgy -> {
                    UUID bxgyDiscountId = bxgy.getDiscount() != null ? bxgy.getDiscount().getId() : null;
                    
                    // Exclude current discount
                    if (bxgyDiscountId != null && bxgyDiscountId.equals(discount.getId())) {
                        log.debug("Excluding BXGY item from current discount {}", discountId);
                        return false;
                    }
                    
                    // CRITICAL: Verify that the CategoryItemMapping IDs in this BXGY item belong to this menu
                    // This ensures we only flag conflicts for items that are actually in the same menu
                    // A discount might be assigned to multiple menus, but we only care about conflicts in THIS menu
                    // The repository query checks if discount is assigned to menu, but we need to verify
                    // that the actual CategoryItemMapping IDs belong to this menu (not another menu)
                    
                    // Check if buyItemMapping is loaded and belongs to this menu
                    UUID buyItemMappingId = null;
                    if (bxgy.getBuyItemMapping() != null) {
                        buyItemMappingId = bxgy.getBuyItemMapping().getId();
                    }
                    boolean buyItemInMenu = buyItemMappingId != null 
                        && menuCategoryItemMappingIds.contains(buyItemMappingId);
                    
                    // Check if getItemMapping is loaded and belongs to this menu
                    UUID getItemMappingId = null;
                    if (bxgy.getGetItemMapping() != null) {
                        getItemMappingId = bxgy.getGetItemMapping().getId();
                    }
                    boolean getItemInMenu = getItemMappingId != null 
                        && menuCategoryItemMappingIds.contains(getItemMappingId);
                    
                    if (!buyItemInMenu && !getItemInMenu) {
                        log.debug("Skipping BXGY item {} - CategoryItemMapping IDs do not belong to menu {} (buyItemMappingId={}, getItemMappingId={}, buyItemInMenu={}, getItemInMenu={})", 
                            bxgy.getId(), menuId, buyItemMappingId, getItemMappingId, buyItemInMenu, getItemInMenu);
                        return false;
                    }
                    
                    // Remove duplicates by BXGY item ID
                    if (seenBxgyIds.contains(bxgy.getId())) {
                        return false;
                    }
                    seenBxgyIds.add(bxgy.getId());
                    
                    if (bxgyDiscountId != null) {
                        foundConflictingDiscountIds.add(bxgyDiscountId);
                    }
                    
                    return true;
                })
                .collect(Collectors.toList());
            
            log.info("Found {} potential conflicting BXGY items for discount {} in menu {} (before date overlap check). Conflicting discount IDs: {}", 
                conflictingBxgyItems.size(), discountId, menuId, foundConflictingDiscountIds);

            // Only check for conflicts if we found potential conflicts
            if (!conflictingBxgyItems.isEmpty() && !foundConflictingDiscountIds.isEmpty()) {
                // New validity window (already converted above when updating menuMapping)
                OffsetDateTime newValidFrom = menuMapping.getValidFrom();
                OffsetDateTime newValidTo = menuMapping.getValidTo();

                // Fetch menu mappings for conflicting discounts to compare date ranges
                List<MenuDiscountMapping> conflictingMenuMappings = menuDiscountMappingRepository.findByMenuId(menuId)
                    .stream()
                    .filter(mdm -> foundConflictingDiscountIds.contains(mdm.getDiscount().getId()))
                    .filter(mdm -> !mdm.getDiscount().getId().equals(discount.getId())) // Exclude current discount
                    .collect(Collectors.toList());

                log.info("Found {} menu mappings for conflicting discounts (excluding current discount {})", 
                    conflictingMenuMappings.size(), discountId);

                Map<UUID, MenuDiscountMapping> discountToMappingMap = conflictingMenuMappings.stream()
                    .collect(Collectors.toMap(
                        mdm -> mdm.getDiscount().getId(),
                        mdm -> mdm,
                        (existing, replacement) -> existing
                    ));

                // Check for date overlaps - only flag conflicts if dates actually overlap
                // This ensures that two different BXGY discounts CAN have the same items
                // in the same menu as long as their date ranges do NOT overlap
                List<DiscountBxgyItem> overlappingBxgyItems = new ArrayList<>();
                for (DiscountBxgyItem bxgyItem : conflictingBxgyItems) {
                    UUID conflictingDiscountId = bxgyItem.getDiscount() != null ? bxgyItem.getDiscount().getId() : null;
                    
                    // Skip if this belongs to current discount (shouldn't happen after filtering, but extra safety)
                    if (conflictingDiscountId == null || conflictingDiscountId.equals(discount.getId())) {
                        log.debug("Skipping BXGY item from current discount {}", discountId);
                        continue;
                    }
                    
                    MenuDiscountMapping existingMapping = discountToMappingMap.get(conflictingDiscountId);
                    if (existingMapping == null) {
                        log.debug("No menu mapping found for conflicting discount {}", conflictingDiscountId);
                        continue;
                    }

                    OffsetDateTime existingValidFrom = existingMapping.getValidFrom();
                    OffsetDateTime existingValidTo = existingMapping.getValidTo();

                    // Check if date ranges overlap
                    boolean overlaps = false;
                    if (newValidFrom == null || existingValidFrom == null) {
                        // If either has no start date, consider it an overlap (shouldn't happen in practice)
                        overlaps = true;
                        log.debug("Date overlap detected (null start dates): newValidFrom={}, existingValidFrom={}", 
                            newValidFrom, existingValidFrom);
                    } else if (newValidTo == null && existingValidTo == null) {
                        // Both are indefinite - they overlap since both extend to infinity
                        overlaps = true;
                        log.debug("Date overlap detected (both indefinite): newValidFrom={}, existingValidFrom={}", 
                            newValidFrom, existingValidFrom);
                    } else if (newValidTo == null) {
                        // New discount is indefinite (extends to infinity)
                        // Overlaps if it starts before or when existing ends (since new has no end)
                        overlaps = !newValidFrom.isAfter(existingValidTo);
                        log.debug("Date overlap check (new indefinite): newValidFrom={}, existingValidTo={}, overlaps={}", 
                            newValidFrom, existingValidTo, overlaps);
                    } else if (existingValidTo == null) {
                        // Existing discount is indefinite (extends to infinity)
                        // Overlaps if existing starts before or when new ends (since existing has no end)
                        overlaps = !existingValidFrom.isAfter(newValidTo);
                        log.debug("Date overlap check (existing indefinite): existingValidFrom={}, newValidTo={}, overlaps={}", 
                            existingValidFrom, newValidTo, overlaps);
                    } else {
                        // Both have end dates - standard overlap check
                        // Two date ranges [A, B] and [C, D] overlap if: A < D && B > C
                        overlaps = newValidFrom.isBefore(existingValidTo) && newValidTo.isAfter(existingValidFrom);
                        log.debug("Date overlap check: newRange=[{} to {}], existingRange=[{} to {}], overlaps={}", 
                            newValidFrom, newValidTo, existingValidFrom, existingValidTo, overlaps);
                    }

                    if (overlaps) {
                        log.info("Found overlapping date range for discount {} (conflicting with OTHER BXGY discount {}): newRange=[{} to {}], existingRange=[{} to {}]", 
                            discountId, conflictingDiscountId, newValidFrom, newValidTo, existingValidFrom, existingValidTo);
                        overlappingBxgyItems.add(bxgyItem);
                    } else {
                        log.info("No date overlap for discount {} (conflicting with OTHER BXGY discount {}): newRange=[{} to {}], existingRange=[{} to {}] - allowing assignment", 
                            discountId, conflictingDiscountId, newValidFrom, newValidTo, existingValidFrom, existingValidTo);
                    }
                }

                // Only throw error if we have overlapping BXGY discounts with same items
                if (!overlappingBxgyItems.isEmpty()) {
                    log.warn("Found {} overlapping BXGY items with OTHER discounts (not current discount {})", 
                        overlappingBxgyItems.size(), discountId);
                    
                    // Collect item IDs from overlapping discounts (only OTHER BXGY discounts)
                    Set<UUID> conflictingItemIds = new HashSet<>();
                    for (DiscountBxgyItem bxgyItem : overlappingBxgyItems) {
                        // Extra safety: skip if belongs to current discount (shouldn't happen)
                        UUID bxgyDiscountId = bxgyItem.getDiscount() != null ? bxgyItem.getDiscount().getId() : null;
                        if (bxgyDiscountId == null || bxgyDiscountId.equals(discount.getId())) {
                            log.warn("Skipping BXGY item from current discount {} - this should not happen", discountId);
                            continue;
                        }
                        
                        if (bxgyItem.getBuyItemMapping() != null && bxgyItem.getBuyItemMapping().getItem() != null) {
                            conflictingItemIds.add(bxgyItem.getBuyItemMapping().getItem().getId());
                        }
                        if (bxgyItem.getGetItemMapping() != null && bxgyItem.getGetItemMapping().getItem() != null) {
                            conflictingItemIds.add(bxgyItem.getGetItemMapping().getItem().getId());
                        }
                    }

                    if (!conflictingItemIds.isEmpty()) {
                        // Resolve item names for message
                        List<ItemTranslation> itemTranslations = itemTranslationRepository.findAllByItemIdIn(new ArrayList<>(conflictingItemIds));
                        Map<UUID, List<ItemTranslation>> translationsByItemId = itemTranslations.stream()
                                .collect(Collectors.groupingBy(t -> t.getItem().getId()));

                        List<String> conflictingItemNames = new ArrayList<>();
                        for (UUID itemId : conflictingItemIds) {
                            List<ItemTranslation> itemTrans = translationsByItemId.getOrDefault(itemId, new ArrayList<>());
                            String itemName = "";

                            if (!itemTrans.isEmpty()) {
                                ItemTranslation exactMatch = itemTrans.stream()
                                    .filter(t -> t.getLanguageCode() != null && t.getLanguageCode().equals(locale))
                                    .findFirst()
                                    .orElse(null);

                                if (exactMatch != null) {
                                    itemName = exactMatch.getName() != null ? exactMatch.getName() : "";
                                } else {
                                    Optional<ItemTranslation> fallback = TranslationUtils.pickPreferredOrFromList(
                                            itemTrans,
                                            locale,
                                            localizationProperties.getLanguages(),
                                            ItemTranslation::getLanguageCode
                                    );
                                    itemName = fallback.map(t -> t.getName() != null ? t.getName() : "").orElse("");
                                }
                            }

                            if (!itemName.isEmpty()) {
                                conflictingItemNames.add(itemName);
                            } else {
                                conflictingItemNames.add(itemId.toString());
                            }
                        }

                        String itemNamesList = String.join(", ", conflictingItemNames);
                        log.error("Overlapping BXGY conflict while editing discount {} in menu {} with OTHER BXGY discounts (not current discount). Items: {}", 
                            discountId, menuId, itemNamesList);
                        throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                            messageUtil.getMessage(MSG_DISCOUNT_BXGY_ITEM_ALREADY_ASSIGNED_TO_OTHER_DISCOUNT, userLocale, itemNamesList));
                    }
                }
            }
            
            // Create new BXGY mappings
            List<DiscountBxgyItem> newBxgyMappings = new ArrayList<>();
            
            for (UUID buyItemId : request.getBuyItemIds()) {
                // Find the CategoryItemMapping for this buy item in the specific menu
                CategoryItemMapping buyItemMapping = categoryItemMappingRepository
                    .findByItemIdAndMenuCategoryMappingMenuId(buyItemId, menuId)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, 
                        messageUtil.getMessage(MSG_ITEM_NOT_IN_MENU, userLocale)));
                
                for (UUID getItemId : request.getGetItemIds()) {
                    // Find the CategoryItemMapping for this get item in the specific menu
                    CategoryItemMapping getItemMapping = categoryItemMappingRepository
                        .findByItemIdAndMenuCategoryMappingMenuId(getItemId, menuId)
                        .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, 
                            messageUtil.getMessage(MSG_ITEM_NOT_IN_MENU, userLocale)));
                    
                    DiscountBxgyItem bxgyItem = DiscountBxgyItem.builder()
                        .discount(discount)
                        .buyItemMapping(buyItemMapping)
                        .getItemMapping(getItemMapping)
                        .build();
                    
                    newBxgyMappings.add(bxgyItem);
                }
            }
            
            // Save new BXGY mappings
            if (!newBxgyMappings.isEmpty()) {
                discountBxgyItemRepository.saveAll(newBxgyMappings);
                log.info("Created {} new BXGY mappings for discount {} in menu {}", 
                    newBxgyMappings.size(), discountId, menuId);
            }
        }
        
        // Save updated menu mapping
        menuDiscountMappingRepository.save(menuMapping);
        
        // Return updated discount details
       DiscountDetailsResponse response = getDiscountDetailsWithMenu(menuId, discountId, locale).getData();

       return ResponseDto.<DiscountDetailsResponse>builder()
           .data(response)
           .message(messageUtil.getMessage(MSG_DISCOUNT_ASSIGNMENT_EDIT_SUCCESS, userLocale))
           .build();
    }

    /**
     * Assigns an ORDER-type discount to a menu.
     * Validates that discount is active, not deleted, and of type ORDER.
     * Creates menu discount mapping and restaurant discount mappings for all restaurants assigned to the menu.
     * Validates UTC datetime fields.
     *
     * @param request the assignment request with discount ID, menu ID, and validity settings
     * @param userId  the ID of the user performing the assignment
     * @param locale  locale code for localized error messages
     * @return ResponseDto containing assignment response
     * @throws ResponseStatusException if validation fails, discount not found, inactive, wrong type, or menu invalid
     */
    @Override
    @Transactional
    @CacheEvict(value = "restaurants", allEntries = true)
    public ResponseDto<MenuDiscountAssignmentResponse> assignDiscountToOrder(AssignDiscountToMenuRequest request, String userId, String locale) {
        Locale userLocale = Locale.forLanguageTag(locale);
        
        // Validate request
        if (request.getDiscountId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, 
                messageUtil.getMessage(MSG_DISCOUNT_ID_REQUIRED, userLocale));
        }
        
        if (request.getMenuId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, 
                messageUtil.getMessage(MSG_MENU_ID_REQUIRED, userLocale));
        }
        
        // Validate UTC datetime fields
        validateUtcDateTimeFields(request, userLocale);
        
        // Find discount
        Discount discount = discountRepository.findById(request.getDiscountId())
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, 
                messageUtil.getMessage(MSG_DISCOUNT_NOT_FOUND, userLocale)));
        
        // Check if discount is deleted
        if (Boolean.TRUE.equals(discount.getIsDeleted())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, 
                messageUtil.getMessage(MSG_DISCOUNT_DELETED, userLocale));
        }
        
        // Check if discount is inactive
        if (discount.getStatus() != EntityStatus.ACTIVE) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, 
                messageUtil.getMessage(MSG_DISCOUNT_INACTIVE, userLocale));
        }
        
        // Check if discount type is applicable to order (order type)
        if (discount.getAppliedTo() != AppliedTo.ORDER) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, 
                messageUtil.getMessage(MSG_DISCOUNT_TYPE_MISMATCH_ORDER, userLocale));
        }
        
        
        // Find menu
        Menu menu = menuRepository.findById(request.getMenuId())
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, 
                messageUtil.getMessage(MSG_MENU_NOT_FOUND, userLocale)));
        
        // Check if menu is deleted
        if (Boolean.TRUE.equals(menu.getIsDeleted())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, 
                messageUtil.getMessage(MSG_MENU_DELETED, userLocale));
        }
        
        // Check if discount is already assigned to this menu AND the assignment is still valid
        List<MenuDiscountMapping> existingMenuMappings = menuDiscountMappingRepository.findByMenuId(request.getMenuId());
        boolean activeOrderAssignmentFound = existingMenuMappings.stream()
            .anyMatch(mdm -> {
                if (!mdm.getDiscount().getId().equals(request.getDiscountId())) {
                    return false; // Different discount
                }
                
                // Check if this order assignment is still valid
                return isDiscountAssignmentStillValid(mdm.getDiscount(), request.getMenuId());
            });
        
        if (activeOrderAssignmentFound) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, 
                messageUtil.getMessage(MSG_DISCOUNT_ALREADY_ASSIGNED_TO_MENU, userLocale));
        }
        
        // Create MenuDiscountMapping (convert to UTC like combo system)
        MenuDiscountMapping menuDiscountMapping = MenuDiscountMapping.builder()
            .id(new MenuDiscountId(request.getMenuId(), request.getDiscountId()))
            .menu(menu)
            .discount(discount)
            .validFrom(convertToUtc(request.getValidFrom()))
            .validTo(convertToUtc(request.getValidTo()))
            .startTime(convertToUtc(request.getStartTime()))
            .endTime(convertToUtc(request.getEndTime()))
            .daysOfWeek(request.getDaysOfWeek())
            .isHide(Boolean.TRUE.equals(request.getIsHide()))
            .build();
        
        // Save the mapping
        menuDiscountMappingRepository.save(menuDiscountMapping);
        
        // Create restaurant discount mappings
        createRestaurantDiscountMappings(
            request.getMenuId(),
            discount,
            request.getValidFrom(),
            request.getValidTo(),
            request.getStartTime(),
            request.getEndTime(),
            request.getDaysOfWeek(),
            request.getIsHide(),
            userLocale
        );
        
        // Create audit trail for menu update (discount assignment)
        try {
            User user = findUserByIdStringForAuditOrNull(userId);
            
            Restaurant restaurant = null;
            if (user != null && user.getRestaurantId() != null) {
                restaurant = restaurantRepository.findById(user.getRestaurantId()).orElse(null);
            }
            
            DiscountTranslationResponse resolvedForAudit = buildResolvedDiscountTranslation(
                    discount.getTranslations(),
                    userLocale.getLanguage(),
                    discount.getDiscountCode());
            String discountName = resolvedForAudit.getName() != null && !resolvedForAudit.getName().isBlank()
                    ? resolvedForAudit.getName()
                    : (discount.getDiscountCode() != null ? discount.getDiscountCode() : "N/A");
            
            auditTrailService.createAuditTrail(
                    user,
                    ActionType.MENU_UPDATE,
                    restaurant,
                    null, // status - will default to NA for non-request actions
                    null, // ipAddress - not available in this context
                    null, // userAgent - not available in this context
                    menu.getId(),
                    "MENU",
                    "Discount assigned to menu: " + discountName + " (ID: " + discount.getId() + ")"
            );
            log.debug("Created audit trail for discount assignment to menu: Menu ID: {}, Discount ID: {}", 
                    menu.getId(), discount.getId());
        } catch (Exception e) {
            log.error("Failed to create audit trail for discount assignment to menu: {}", e.getMessage(), e);
            // Don't break discount assignment flow if audit trail fails
        }
        
        // Create response with UTC dates (as received from frontend)
        MenuDiscountAssignmentResponse response = MenuDiscountAssignmentResponse.builder()
            .discountId(request.getDiscountId())
            .menuId(request.getMenuId())
            .validFrom(request.getValidFrom())
            .validTo(request.getValidTo())
            .startTime(request.getStartTime())
            .endTime(request.getEndTime())
            .daysOfWeek(request.getDaysOfWeek())
            .isHide(request.getIsHide())
            .build();
        
        return ResponseDto.<MenuDiscountAssignmentResponse>builder()
            .data(response)
            .message(messageUtil.getMessage(MSG_DISCOUNT_ASSIGNMENT_SUCCESS, userLocale))
            .build();
    }

    /**
     * Edits the assignment of an ORDER-type discount to a menu.
     * Updates validity dates, times, and schedule in the menu discount mapping.
     * Converts date/time values to UTC. Updates restaurant discount mappings if dates changed.
     * Validates that discount is active, not deleted, and of type ORDER.
     *
     * @param discountId the UUID of the discount (from path, authoritative)
     * @param request     the edit request with menu ID and new validity settings
     * @param updaterId   the ID of the user performing the edit
     * @param locale      locale code for localized error messages
     * @return ResponseDto containing updated discount details
     * @throws ResponseStatusException if menu not found, discount not found, not assigned, inactive, wrong type, or validation fails
     */
    @Override
    @Transactional
    @CacheEvict(value = "restaurants", allEntries = true)
    public ResponseDto<DiscountDetailsResponse> editDiscountOrderAssignment(UUID discountId, AssignDiscountToMenuRequest request, String updaterId, String locale) {
        Locale userLocale = Locale.forLanguageTag(locale);
        
        // Fetch user
        User user = userRepository.findById(UUID.fromString(updaterId))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        messageUtil.getMessage(MSG_USER_NOT_FOUND, userLocale, updaterId)));
        
        // Extract menuId from request
        UUID menuId = request.getMenuId();
        
        // Validate menu exists
        Menu menu = menuRepository.findById(menuId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, messageUtil.getMessage(MSG_MENU_NOT_FOUND, userLocale)));
        
        // Check if menu is deleted
        if (Boolean.TRUE.equals(menu.getIsDeleted())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, messageUtil.getMessage(MSG_MENU_DELETED, userLocale));
        }
        
        // Validate discount exists
        Discount discount = discountRepository.findById(discountId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, messageUtil.getMessage(MSG_DISCOUNT_NOT_FOUND, userLocale)));
        
        // Check if discount is deleted
        if (Boolean.TRUE.equals(discount.getIsDeleted())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, messageUtil.getMessage(MSG_DISCOUNT_DELETED, userLocale));
        }
        
        // Check if discount is inactive
        if (discount.getStatus() != EntityStatus.ACTIVE) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, messageUtil.getMessage(MSG_DISCOUNT_INACTIVE, userLocale));
        }
        
        // Check if discount type is applicable to order (order type)
        if (discount.getAppliedTo() != AppliedTo.ORDER) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, messageUtil.getMessage(MSG_DISCOUNT_TYPE_MISMATCH_ORDER, userLocale));
        }
        
        
        // Check if discount is assigned to this menu
        List<MenuDiscountMapping> menuDiscountMappings = menuDiscountMappingRepository.findByMenuId(menuId);
        MenuDiscountMapping menuMapping = menuDiscountMappings.stream()
            .filter(mdm -> mdm.getDiscount().getId().equals(discountId))
            .findFirst()
            .orElse(null);
        
        if (menuMapping == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, 
                messageUtil.getMessage(MSG_DISCOUNT_NOT_ASSIGNED_TO_MENU, userLocale));
        }
        
        // Update menu discount mapping with new values (convert to UTC like combo system)
        if (request.getValidFrom() != null) {
            menuMapping.setValidFrom(convertToUtc(request.getValidFrom()));
        }
        if (request.getValidTo() != null) {
            menuMapping.setValidTo(convertToUtc(request.getValidTo()));
        }
        if (request.getStartTime() != null) {
            menuMapping.setStartTime(convertToUtc(request.getStartTime()));
        }
        if (request.getEndTime() != null) {
            menuMapping.setEndTime(convertToUtc(request.getEndTime()));
        }
        if (request.getDaysOfWeek() != null) {
            menuMapping.setDaysOfWeek(request.getDaysOfWeek());
        }
        if (request.getIsHide() != null) {
            menuMapping.setIsHide(Boolean.TRUE.equals(request.getIsHide()));
        }
        
        // Validate that validFrom is not after validTo
        if (menuMapping.getValidFrom() != null && menuMapping.getValidTo() != null &&
            menuMapping.getValidFrom().isAfter(menuMapping.getValidTo())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, 
                messageUtil.getMessage(MSG_DISCOUNT_ERROR_INVALID_DATE_RANGE, userLocale));
        }
        
        // Validate time range - allow overnight schedules (e.g., 23:00 to 02:00)
        // Only reject if start and end times are the same (invalid)
        if (menuMapping.getStartTime() != null && menuMapping.getEndTime() != null 
                && menuMapping.getStartTime().equals(menuMapping.getEndTime())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                messageUtil.getMessage(MSG_DISCOUNT_ERROR_SCHEDULE_INVALID_TIME_RANGE, userLocale));
        }
        // Note: We allow overnight schedules where endTime < startTime
        
        // Validate that validFrom is not after validTo
        if (menuMapping.getValidFrom() != null && menuMapping.getValidTo() != null &&
            menuMapping.getValidFrom().isAfter(menuMapping.getValidTo())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, 
                messageUtil.getMessage(MSG_DISCOUNT_ERROR_INVALID_DATE_RANGE, userLocale));
        }
        
        // Validate that validTo is not completely in the past (allow extending expired discounts)
        OffsetDateTime nowUtc = OffsetDateTime.now(ZoneOffset.UTC);
        if (menuMapping.getValidTo() != null && menuMapping.getValidTo().isBefore(nowUtc)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, 
                messageUtil.getMessage(MSG_DISCOUNT_ASSIGNMENT_PAST_VALID_TO, userLocale));
        }
        
        // Save updated menu mapping
        menuDiscountMappingRepository.save(menuMapping);
        
        /**
         * Create audit trail for discount validity modification (order assignment)
         * 
         * DISCOUNT_MODIFY: Captures changes to discount validity settings (dates, times, days of week)
         * when editing order assignments for a discount in a menu.
         */
        try {
            Restaurant restaurant = null;
            if (user.getRestaurantId() != null) {
                restaurant = restaurantRepository.findById(user.getRestaurantId()).orElse(null);
            }
            
            // Determine if any validity modifications were made
            boolean hasValidityModifications = request.getValidFrom() != null || 
                                              request.getValidTo() != null || 
                                              request.getStartTime() != null || 
                                              request.getEndTime() != null ||
                                              request.getDaysOfWeek() != null;
            
            if (hasValidityModifications) {
                ActionType actionType = ActionType.DISCOUNT_MODIFY;
                StringBuilder notesBuilder = new StringBuilder("Discount validity modified (order assignment): ");
                List<String> changes = new ArrayList<>();
                
                if (request.getValidFrom() != null) {
                    changes.add(String.format(AUDIT_MSG_VALID_FROM, menuMapping.getValidFrom()));
                }
                if (request.getValidTo() != null) {
                    changes.add(String.format(AUDIT_MSG_VALID_TO, menuMapping.getValidTo()));
                }
                if (request.getStartTime() != null) {
                    changes.add(String.format(AUDIT_MSG_START_TIME, menuMapping.getStartTime()));
                }
                if (request.getEndTime() != null) {
                    changes.add(String.format(AUDIT_MSG_END_TIME, menuMapping.getEndTime()));
                }
                if (request.getDaysOfWeek() != null) {
                    changes.add(String.format(AUDIT_MSG_DAYS_OF_WEEK, menuMapping.getDaysOfWeek()));
                }
                
                String notes = notesBuilder.append(String.join(", ", changes)).toString();
                
                auditTrailService.createAuditTrail(
                        user,
                        actionType,
                        restaurant,
                        RequestStatus.NA,
                        null, // ipAddress
                        null, // userAgent
                        discountId,
                        ENTITY_TYPE_DISCOUNT,
                        notes
                );
                log.debug("Created audit trail for discount {} validity modification (order assignment) by user: {}", 
                        discountId, user.getUserCode());
            }
        } catch (Exception e) {
            log.error("Failed to create audit trail for discount validity modification (order assignment): {}", e.getMessage(), e);
            // Don't fail the discount update if audit trail creation fails
        }
        
        // Return updated discount details
        DiscountDetailsResponse response = getDiscountDetailsWithMenu(menuId, discountId, locale).getData();

        return ResponseDto.<DiscountDetailsResponse>builder()
            .data(response)
            .message(messageUtil.getMessage(MSG_DISCOUNT_ASSIGNMENT_EDIT_SUCCESS, userLocale))
            .build();
    }

    /**
     * Unassigns an ORDER-type discount from a menu.
     * Deletes the menu discount mapping and restaurant discount mappings for all restaurants assigned to the menu.
     * Validates that discount is of type ORDER.
     *
     * @param menuId    the UUID of the menu
     * @param discountId the UUID of the discount to unassign
     * @param updaterId  the ID of the user performing the unassignment
     * @param locale     locale code for localized error messages
     * @return ResponseDto containing unassignment response
     * @throws ResponseStatusException if menu not found, discount not found, not assigned, or wrong discount type
     */
    @Override
    @Transactional
    @CacheEvict(value = "restaurants", allEntries = true)
    public ResponseDto<DiscountAssignmentListResponse> unassignDiscountFromOrder(UUID menuId, UUID discountId, String updaterId, String locale) {
        Locale userLocale = Locale.forLanguageTag(locale);
        
        // Validate menu exists
        Menu menu = menuRepository.findById(menuId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, messageUtil.getMessage(MSG_MENU_NOT_FOUND, userLocale)));
        
        // Validate discount exists
        Discount discount = discountRepository.findById(discountId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, messageUtil.getMessage(MSG_DISCOUNT_NOT_FOUND, userLocale)));
        
        // Check if discount type is applicable to order (order type)
        if (discount.getAppliedTo() != AppliedTo.ORDER) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, 
                messageUtil.getMessage(MSG_DISCOUNT_TYPE_MISMATCH_ORDER, userLocale));
        }
        
        // Find menu-discount mapping
        List<MenuDiscountMapping> menuDiscountMappings = menuDiscountMappingRepository.findByMenuId(menuId);
        MenuDiscountMapping menuMapping = menuDiscountMappings.stream()
            .filter(mdm -> mdm.getDiscount().getId().equals(discountId))
            .findFirst()
            .orElse(null);
        
        if (menuMapping == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, 
                messageUtil.getMessage(MSG_DISCOUNT_NOT_ASSIGNED_TO_MENU, userLocale));
        }
        
        // Delete the menu-discount mapping
        menuDiscountMappingRepository.delete(menuMapping);
        
        // Delete restaurant-discount mappings for all restaurants assigned to this menu
        deleteRestaurantDiscountMappingsForMenu(menuId, discountId);
        
        // Create response
        DiscountAssignmentListResponse response = new DiscountAssignmentListResponse();
        response.setDiscountId(discount.getId());
        response.setAssignedItemIds(Collections.emptyList()); // No items for order-type discounts
        response.setAssignedMenuIds(Collections.emptyList()); // No other menus assigned
        
        return ResponseDto.<DiscountAssignmentListResponse>builder()
            .data(response)
            .message(messageUtil.getMessage(MSG_DISCOUNT_UNASSIGNMENT_SUCCESS, userLocale))
            .build();
    }
    
    /**
     * Validates a discount for use in an order.
     * Validates discount existence (by ID or code), active status, usage limits,
     * restaurant assignment, menu assignment, date/time validity, and schedule.
     * Returns validation result with discount details if valid.
     *
     * @param request the validation request with discount ID or code, restaurant ID, menu ID, and order details
     * @param locale  locale code for localized error messages
     * @return ResponseDto containing validation result and discount details if valid
     * @throws ResponseStatusException if discount not found, inactive, usage limit exceeded, or validation fails
     */
    @Override
    public ResponseDto<DiscountValidationResponse> validateDiscount(DiscountValidationRequest request, String locale) {
        try {
            Locale userLocale = Locale.forLanguageTag(locale);
            
            // Step 1: Find discount by either ID or code
            Optional<Discount> discountOpt;
            if (request.getDiscountId() != null) {
                // Find by discount ID
                discountOpt = discountRepository.findById(request.getDiscountId());
            } else if (request.getDiscountCode() != null && !request.getDiscountCode().trim().isEmpty()) {
                // Find by discount code
                discountOpt = discountRepository.findByDiscountCodeAndIsDeletedFalse(request.getDiscountCode());
            } else {
                // Neither ID nor code provided (should be caught by validation)
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    messageUtil.getMessage("discount.validation.error.either.code.or.id.required", userLocale));
            }
            
            if (discountOpt.isEmpty()) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                    messageUtil.getMessage(MSG_DISCOUNT_NOT_FOUND, userLocale));
            }
            
            Discount discount = discountOpt.get();
            
            // Step 2: Validate discount is active and not deleted
            if (discount.getStatus() != EntityStatus.ACTIVE || discount.getIsDeleted()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    messageUtil.getMessage("discount.validation.error.inactive", userLocale));
            }

            // Step 2.1: Validate discount can be applied to ORDER only
            if (discount.getAppliedTo() == null || discount.getAppliedTo() != AppliedTo.ORDER) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    messageUtil.getMessage(MSG_DISCOUNT_TYPE_MISMATCH_ORDER, userLocale));
            }
            
            // Step 3: Validate usage limits
            // maxUses = 0 means unlimited, so only check if maxUses > 0
            if (discount.getMaxUses() != null && discount.getMaxUses() > 0 && discount.getCurrentUsage() >= discount.getMaxUses()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    messageUtil.getMessage("discount.usage.limit.exceeded", userLocale));
            }
            
            // Step 4: Validate discount assignment and date/time validity
            RestaurantDiscountMapping restaurantMapping = null;
            MenuDiscountMapping menuMapping = null;
            OffsetDateTime nowUtc = OffsetDateTime.now(ZoneOffset.UTC);
            com.gulfnet.shared_library.enums.DayOfWeek currentDay = convertToDayOfWeek(nowUtc.getDayOfWeek());
            
            if (request.getRestaurantId() != null) {
                // Validate restaurant exists
                Restaurant restaurant = restaurantRepository.findById(request.getRestaurantId())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        messageUtil.getMessage(MSG_RESTAURANT_NOT_FOUND, userLocale)));
                
                // Check if discount is assigned to restaurant via RestaurantDiscountMapping
                RestaurantDiscountId restaurantDiscountId = new RestaurantDiscountId();
                restaurantDiscountId.setRestaurantId(request.getRestaurantId());
                restaurantDiscountId.setDiscountId(discount.getId());
                
                Optional<RestaurantDiscountMapping> restaurantMappingOpt = 
                    restaurantDiscountMappingRepository.findById(restaurantDiscountId);
                
                if (restaurantMappingOpt.isEmpty()) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        messageUtil.getMessage(MSG_DISCOUNT_NOT_ASSIGNED_TO_RESTAURANT, userLocale));
                }
                
                restaurantMapping = restaurantMappingOpt.get();
                
                // Check status - if INACTIVE, discount is not valid for this restaurant
                if (restaurantMapping.getStatus() != null && restaurantMapping.getStatus() != EntityStatus.ACTIVE) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        messageUtil.getMessage("discount.validation.error.inactive", userLocale));
                }
                
                // Check valid_from and valid_to dates from RestaurantDiscountMapping
                if (restaurantMapping.getValidFrom() != null && nowUtc.isBefore(restaurantMapping.getValidFrom())) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        messageUtil.getMessage("discount.validation.error.not.started", userLocale));
                }
                if (restaurantMapping.getValidTo() != null && nowUtc.isAfter(restaurantMapping.getValidTo())) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        messageUtil.getMessage("discount.validation.error.expired", userLocale));
                }
                
                // Check start_time and end_time restrictions from RestaurantDiscountMapping
                if (restaurantMapping.getStartTime() != null && restaurantMapping.getEndTime() != null) {
                    OffsetTime currentTime = nowUtc.toOffsetTime();
                    OffsetTime startTime = convertToUtc(restaurantMapping.getStartTime());
                    OffsetTime endTime = convertToUtc(restaurantMapping.getEndTime());
                    
                    if (!startTime.equals(endTime)) { // Not 24-hour availability
                        if (startTime.isBefore(endTime)) {
                            // Normal case: start < end (e.g., 12:00 to 18:00)
                            if (currentTime.isBefore(startTime) || currentTime.isAfter(endTime)) {
                                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                                    messageUtil.getMessage("discount.validation.error.outside.time.window", userLocale));
                            }
                        } else {
                            // Overnight case: start > end (e.g., 23:00 to 02:00)
                            if (currentTime.isBefore(startTime) && currentTime.isAfter(endTime)) {
                                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                                    messageUtil.getMessage("discount.validation.error.outside.time.window", userLocale));
                            }
                        }
                    }
                }
                
                // Check days of week restrictions from RestaurantDiscountMapping
                if (restaurantMapping.getDaysOfWeek() != null && !restaurantMapping.getDaysOfWeek().isEmpty()) {
                    if (!restaurantMapping.getDaysOfWeek().contains(currentDay)) {
                        throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                            messageUtil.getMessage("discount.validation.error.invalid.day", userLocale));
                    }
                }
            } else {
                // Original logic: Validate discount mapping with menu
                boolean isDiscountAssignedToMenu = menuDiscountMappingRepository.isDiscountAssignedToMenu(
                    discount.getId(), request.getMenuId());
                if (!isDiscountAssignedToMenu) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        messageUtil.getMessage("promotion.error.discount.not.assigned.to.menu", userLocale));
                }
            }
            
            // Step 5: Validate menu exists and is published
            Optional<Menu> menuOpt = menuRepository.findById(request.getMenuId());
            if (menuOpt.isEmpty()) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                    messageUtil.getMessage(MSG_MENU_NOT_FOUND, userLocale));
            }
            
            Menu menu = menuOpt.get();
            // Fix: Use MenuStatus.PUBLISHED instead of isPublished field
            if (!MenuStatus.PUBLISHED.equals(menu.getStatus()) || menu.getIsDeleted()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    messageUtil.getMessage("discount.validation.error.menu.not.published", userLocale));
            }
            
            // Step 6: Validate order threshold
            if (discount.getOrderValueThreshold() != null && 
                request.getSubTotal().compareTo(discount.getOrderValueThreshold()) < 0) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    messageUtil.getMessage("discount.validation.error.threshold.not.met", userLocale));
            }
            
            // Step 8: Calculate discount amount
            BigDecimal discountAmount = BigDecimal.ZERO;
            if (discount.getDiscountType() == DiscountType.FLAT) {
                discountAmount = discount.getValue();
            } else if (discount.getDiscountType() == DiscountType.PERCENT) {
                discountAmount = request.getSubTotal().multiply(discount.getValue()).divide(new BigDecimal("100"));
                
                // Apply maximum discount limit if set
                if (discount.getMaxDiscountValue() != null && 
                    discountAmount.compareTo(discount.getMaxDiscountValue()) > 0) {
                    discountAmount = discount.getMaxDiscountValue();
                }
            }
            
            // Step 9: Calculate final amount
            BigDecimal finalAmount = request.getSubTotal().subtract(discountAmount);
            if (finalAmount.compareTo(BigDecimal.ZERO) < 0) {
                finalAmount = BigDecimal.ZERO;
            }
            
            // Step 10: Create successful response
            String currency = restaurantChainConfigProperties.getChain() != null ? restaurantChainConfigProperties.getChain().getCurrency() : null;
            DiscountValidationResponse response = DiscountValidationResponse.builder()
                .discountId(discount.getId())
                .discountCode(discount.getDiscountCode())
                .discountType(discount.getDiscountType())
                .originalSubTotal(request.getSubTotal() != null ? CurrencyFormatter.formatAmount(request.getSubTotal(), currency) : null)
                .discountAmount(discountAmount != null ? CurrencyFormatter.formatAmount(discountAmount, currency) : null)
                .finalAmount(finalAmount != null ? CurrencyFormatter.formatAmount(finalAmount, currency) : null)
                .orderValueThreshold(discount.getOrderValueThreshold() != null ? CurrencyFormatter.formatAmount(discount.getOrderValueThreshold(), currency) : null)
                .build();
            
            return ResponseDto.<DiscountValidationResponse>builder()
                .data(response)
                .message(messageUtil.getMessage(MSG_DISCOUNT_VALIDATION_SUCCESS, userLocale))
                .build();
                
        } catch (ResponseStatusException e) {
            // Re-throw ResponseStatusException to preserve HTTP status codes
            throw e;
        } catch (Exception e) {
            log.error("Error validating discount: {}", e.getMessage(), e);
            Locale userLocale = Locale.forLanguageTag(locale);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                messageUtil.getMessage("discount.validation.error.general", userLocale));
        }
    }
    
    /**
     * Validate UTC datetime fields for discount assignment requests
     */
    private void validateUtcDateTimeFields(AssignDiscountToMenuRequest request, Locale userLocale) {
        // Validate validFrom field
        if (request.getValidFrom() != null) {
            // Ensure the datetime is in UTC
            if (!request.getValidFrom().getOffset().equals(ZoneOffset.UTC)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    messageUtil.getMessage(MSG_DISCOUNT_ERROR_VALID_FROM_NOT_UTC, userLocale));
            }
            
            // Check if validFrom is not in the past
            OffsetDateTime nowUtc = OffsetDateTime.now(ZoneOffset.UTC);
            if (request.getValidFrom().isBefore(nowUtc)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    messageUtil.getMessage(MSG_DISCOUNT_ASSIGNMENT_PAST_VALID_FROM, userLocale));
            }
        }
        
        // Validate validTo field
        if (request.getValidTo() != null) {
            // Ensure the datetime is in UTC
            if (!request.getValidTo().getOffset().equals(ZoneOffset.UTC)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    messageUtil.getMessage(MSG_DISCOUNT_ERROR_VALID_TO_NOT_UTC, userLocale));
            }
        }
        
        // Validate date range
        if (request.getValidFrom() != null && request.getValidTo() != null) {
            if (request.getValidFrom().isAfter(request.getValidTo())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    messageUtil.getMessage(MSG_DISCOUNT_ERROR_INVALID_DATE_RANGE, userLocale));
            }
        }
        
        // Validate time fields - allow overnight schedules (e.g., 23:00 to 02:00)
        if (request.getStartTime() != null && request.getEndTime() != null) {
            // Only reject if start and end times are the same (invalid)
            if (request.getStartTime().equals(request.getEndTime())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    messageUtil.getMessage(MSG_DISCOUNT_ERROR_SCHEDULE_INVALID_TIME_RANGE, userLocale));
            }
            // Note: We allow overnight schedules where endTime < startTime
        }
    }
    
    /**
     * Validate UTC datetime fields for category assignment requests
     */
    private void validateUtcDateTimeFields(AssignDiscountToCategoriesRequest request, Locale userLocale) {
        // Validate validFrom field
        if (request.getValidFrom() != null) {
            // Ensure the datetime is in UTC
            if (!request.getValidFrom().getOffset().equals(ZoneOffset.UTC)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    messageUtil.getMessage(MSG_DISCOUNT_ERROR_VALID_FROM_NOT_UTC, userLocale));
            }
            
            // Check if validFrom is not in the past
            OffsetDateTime nowUtc = OffsetDateTime.now(ZoneOffset.UTC);
            if (request.getValidFrom().isBefore(nowUtc)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    messageUtil.getMessage(MSG_DISCOUNT_ASSIGNMENT_PAST_VALID_FROM, userLocale));
            }
        }
        
        // Validate validTo field
        if (request.getValidTo() != null) {
            // Ensure the datetime is in UTC
            if (!request.getValidTo().getOffset().equals(ZoneOffset.UTC)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    messageUtil.getMessage(MSG_DISCOUNT_ERROR_VALID_TO_NOT_UTC, userLocale));
            }
        }
        
        // Validate date range
        if (request.getValidFrom() != null && request.getValidTo() != null) {
            if (request.getValidFrom().isAfter(request.getValidTo())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    messageUtil.getMessage(MSG_DISCOUNT_ERROR_INVALID_DATE_RANGE, userLocale));
            }
        }
        
        // Validate time fields - allow overnight schedules (e.g., 23:00 to 02:00)
        if (request.getStartTime() != null && request.getEndTime() != null) {
            // Only reject if start and end times are the same (invalid)
            if (request.getStartTime().equals(request.getEndTime())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    messageUtil.getMessage(MSG_DISCOUNT_ERROR_SCHEDULE_INVALID_TIME_RANGE, userLocale));
            }
            // Note: We allow overnight schedules where endTime < startTime
        }
    }
    
    /**
     * Validate UTC datetime fields for item assignment requests
     */
    private void validateUtcDateTimeFields(AssignDiscountToItemsRequest request, Locale userLocale) {
        // Validate validFrom field
        if (request.getValidFrom() != null) {
            // Ensure the datetime is in UTC
            if (!request.getValidFrom().getOffset().equals(ZoneOffset.UTC)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    messageUtil.getMessage(MSG_DISCOUNT_ERROR_VALID_FROM_NOT_UTC, userLocale));
            }
            
            // Check if validFrom is not in the past
            OffsetDateTime nowUtc = OffsetDateTime.now(ZoneOffset.UTC);
            if (request.getValidFrom().isBefore(nowUtc)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    messageUtil.getMessage(MSG_DISCOUNT_ASSIGNMENT_PAST_VALID_FROM, userLocale));
            }
        }
        
        // Validate validTo field
        if (request.getValidTo() != null) {
            // Ensure the datetime is in UTC
            if (!request.getValidTo().getOffset().equals(ZoneOffset.UTC)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    messageUtil.getMessage(MSG_DISCOUNT_ERROR_VALID_TO_NOT_UTC, userLocale));
            }
        }
        
        // Validate date range
        if (request.getValidFrom() != null && request.getValidTo() != null) {
            if (request.getValidFrom().isAfter(request.getValidTo())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    messageUtil.getMessage(MSG_DISCOUNT_ERROR_INVALID_DATE_RANGE, userLocale));
            }
        }
        
        // Validate time fields - allow overnight schedules (e.g., 23:00 to 02:00)
        if (request.getStartTime() != null && request.getEndTime() != null) {
            // Only reject if start and end times are the same (invalid)
            if (request.getStartTime().equals(request.getEndTime())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    messageUtil.getMessage(MSG_DISCOUNT_ERROR_SCHEDULE_INVALID_TIME_RANGE, userLocale));
            }
            // Note: We allow overnight schedules where endTime < startTime
        }
    }
    
    /**
     * Convert OffsetDateTime to UTC
     */
    private OffsetDateTime convertToUtc(OffsetDateTime offsetDateTime) {
        if (offsetDateTime == null) {
            return null;
        }
        return offsetDateTime.withOffsetSameInstant(ZoneOffset.UTC);
    }
    
    /**
     * Convert OffsetTime to UTC
     */
    private OffsetTime convertToUtc(OffsetTime offsetTime) {
        if (offsetTime == null) {
            return null;
        }
        return offsetTime.withOffsetSameInstant(ZoneOffset.UTC);
    }

    /**
     * Builds the single translation payload returned on discount list/detail APIs, aligned with combo/menu behavior:
     * prefers locale and configured language order, skips rows with blank names, then any non-blank name by language,
     * and finally {@code discountCode} when nothing else is available.
     */
    private DiscountTranslationResponse buildResolvedDiscountTranslation(
            List<DiscountTranslation> translations,
            String locale,
            String discountCode) {
        List<DiscountTranslation> safe = translations == null ? Collections.emptyList() : translations;
        if (safe.isEmpty()) {
            String name = (discountCode != null && !discountCode.isBlank()) ? discountCode : null;
            return DiscountTranslationResponse.builder()
                    .languageCode(locale)
                    .name(name)
                    .description(null)
                    .build();
        }
        return TranslationUtils.pickPreferredOrFromListNonBlank(
                        safe,
                        locale,
                        localizationProperties.getLanguages(),
                        DiscountTranslation::getLanguageCode,
                        DiscountTranslation::getName)
                .map(t -> DiscountTranslationResponse.builder()
                        .languageCode(t.getLanguageCode() != null ? t.getLanguageCode() : locale)
                        .name(t.getName())
                        .description(t.getDescription())
                        .build())
                .orElseGet(() -> {
                    String fallbackName = (discountCode != null && !discountCode.isBlank()) ? discountCode : null;
                    return DiscountTranslationResponse.builder()
                            .languageCode(locale)
                            .name(fallbackName)
                            .description(null)
                            .build();
                });
    }
    
    /**
     * Resolves {@code userId} to a User for audit logging; blank or invalid UUID yields null (invalid UUID is logged).
     */
    private User findUserByIdStringForAuditOrNull(String userId) {
        if (userId == null || userId.trim().isEmpty()) {
            return null;
        }
        try {
            return userRepository.findById(UUID.fromString(userId)).orElse(null);
        } catch (IllegalArgumentException e) {
            log.warn("Invalid userId provided for audit log: {}", userId);
            return null;
        }
    }

    /**
     * Helper method to format user's full name from User entity
     * Returns null if user is null, otherwise returns formatted name (firstName + lastName)
     * Handles null firstName and lastName gracefully
     */
    private String formatUserName(User user) {
        if (user == null) {
            return null;
        }
        String firstName = user.getFirstName() != null ? user.getFirstName() : "";
        String lastName = user.getLastName() != null ? user.getLastName() : "";
        String fullName = (firstName + " " + lastName).trim();
        return fullName.isEmpty() ? null : fullName;
    }

    /**
     * Helper method to extract item ID from DiscountBxgyItem with null safety
     * Returns the item ID if the BXGY item and its mapping exist, null otherwise
     */
    private UUID extractBuyItemId(DiscountBxgyItem bxgyItem) {
        if (bxgyItem == null || bxgyItem.getBuyItemMapping() == null) {
            return null;
        }
        CategoryItemMapping buyItemMapping = bxgyItem.getBuyItemMapping();
        if (buyItemMapping.getItem() == null) {
            return null;
        }
        return buyItemMapping.getItem().getId();
    }

    /**
     * Helper method to extract item ID from DiscountBxgyItem with null safety
     * Returns the item ID if the BXGY item and its mapping exist, null otherwise
     */
    private UUID extractGetItemId(DiscountBxgyItem bxgyItem) {
        if (bxgyItem == null || bxgyItem.getGetItemMapping() == null) {
            return null;
        }
        CategoryItemMapping getItemMapping = bxgyItem.getGetItemMapping();
        if (getItemMapping.getItem() == null) {
            return null;
        }
        return getItemMapping.getItem().getId();
    }

    /**
     * Helper method to convert Java DayOfWeek to custom DayOfWeek enum
     * 
     * This conversion is necessary because the application uses a custom DayOfWeek enum
     * (com.gulfnet.shared_library.enums.DayOfWeek) for database storage and business logic,
     * while Java's built-in java.time.DayOfWeek is used for date/time operations.
     * The conversion ensures compatibility between the two enum types.
     */
    private com.gulfnet.shared_library.enums.DayOfWeek convertToDayOfWeek(java.time.DayOfWeek javaDayOfWeek) {
        switch (javaDayOfWeek) {
            case SUNDAY: return com.gulfnet.shared_library.enums.DayOfWeek.SUNDAY;
            case MONDAY: return com.gulfnet.shared_library.enums.DayOfWeek.MONDAY;
            case TUESDAY: return com.gulfnet.shared_library.enums.DayOfWeek.TUESDAY;
            case WEDNESDAY: return com.gulfnet.shared_library.enums.DayOfWeek.WEDNESDAY;
            case THURSDAY: return com.gulfnet.shared_library.enums.DayOfWeek.THURSDAY;
            case FRIDAY: return com.gulfnet.shared_library.enums.DayOfWeek.FRIDAY;
            case SATURDAY: return com.gulfnet.shared_library.enums.DayOfWeek.SATURDAY;
            default: throw new IllegalArgumentException("Unexpected DayOfWeek value: " + javaDayOfWeek);
        }
    }

    /**
     * Helper method to create restaurant discount mappings
     * Gets restaurants from menu mapping - if menu has no restaurants assigned, skip restaurant mapping
     */
    private void createRestaurantDiscountMappings(
            UUID menuId,
            Discount discount,
            OffsetDateTime validFrom,
            OffsetDateTime validTo,
            OffsetTime startTime,
            OffsetTime endTime,
            List<DayOfWeek> daysOfWeek,
            Boolean isHide,
            Locale userLocale) {
        
        // Get restaurants from menu mapping
        List<RestaurantMenuMapping> restaurantMenuMappings = restaurantMenuMappingRepository.findById_MenuId(menuId);
        if (restaurantMenuMappings == null || restaurantMenuMappings.isEmpty()) {
            // Menu has no restaurants assigned, skip restaurant mapping
            log.info("Menu {} has no restaurants assigned, skipping restaurant discount mapping", menuId);
            return;
        }
        
        // Extract restaurant IDs from menu mappings
        List<UUID> restaurantIds = restaurantMenuMappings.stream()
            .map(mapping -> mapping.getId().getRestaurantId())
            .collect(Collectors.toList());
        
        // Get restaurant entities
        List<Restaurant> restaurants = restaurantRepository.findAllById(restaurantIds);
        if (restaurants.size() != restaurantIds.size()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                messageUtil.getMessage(MSG_RESTAURANT_NOT_FOUND, userLocale));
        }
        
        // Create restaurant discount mappings
        List<RestaurantDiscountMapping> restaurantDiscountMappings = new ArrayList<>();
        for (Restaurant restaurant : restaurants) {
            RestaurantDiscountId id = new RestaurantDiscountId();
            id.setRestaurantId(restaurant.getId());
            id.setDiscountId(discount.getId());
            
            RestaurantDiscountMapping mapping = RestaurantDiscountMapping.builder()
                .id(id)
                .restaurant(restaurant)
                .discount(discount)
                .validFrom(convertToUtc(validFrom))
                .validTo(convertToUtc(validTo))
                .startTime(convertToUtc(startTime))
                .endTime(convertToUtc(endTime))
                .daysOfWeek(daysOfWeek)
                .status(EntityStatus.ACTIVE)
                .isHide(Boolean.TRUE.equals(isHide))
                .build();
            
            restaurantDiscountMappings.add(mapping);
        }
        
        // Save all mappings
        restaurantDiscountMappingRepository.saveAll(restaurantDiscountMappings);
        log.info("Created {} restaurant discount mappings for discount {} and menu {}", 
            restaurantDiscountMappings.size(), discount.getId(), menuId);
    }

    /**
     * Deletes restaurant-discount mappings for all restaurants assigned to a menu
     * when a discount is unassigned from the menu
     */
    private void deleteRestaurantDiscountMappingsForMenu(UUID menuId, UUID discountId) {
        // Get all restaurants assigned to this menu
        List<RestaurantMenuMapping> restaurantMenuMappings = restaurantMenuMappingRepository.findById_MenuId(menuId);
        
        if (restaurantMenuMappings == null || restaurantMenuMappings.isEmpty()) {
            log.info("Menu {} has no restaurants assigned, skipping restaurant discount mapping deletion", menuId);
            return;
        }
        
        // Collect all restaurant-discount mappings to delete
        List<RestaurantDiscountMapping> restaurantDiscountMappingsToDelete = new ArrayList<>();
        
        for (RestaurantMenuMapping restaurantMenuMapping : restaurantMenuMappings) {
            UUID restaurantId = restaurantMenuMapping.getRestaurant().getId();
            RestaurantDiscountId restaurantDiscountId = new RestaurantDiscountId();
            restaurantDiscountId.setRestaurantId(restaurantId);
            restaurantDiscountId.setDiscountId(discountId);
            
            Optional<RestaurantDiscountMapping> restaurantDiscountMapping = 
                restaurantDiscountMappingRepository.findById(restaurantDiscountId);
            
            if (restaurantDiscountMapping.isPresent()) {
                restaurantDiscountMappingsToDelete.add(restaurantDiscountMapping.get());
            }
        }
        
        // Delete all collected mappings
        if (!restaurantDiscountMappingsToDelete.isEmpty()) {
            restaurantDiscountMappingRepository.deleteAll(restaurantDiscountMappingsToDelete);
            log.info("Deleted {} restaurant-discount mappings for discount {} and menu {}", 
                restaurantDiscountMappingsToDelete.size(), discountId, menuId);
        } else {
            log.info("No restaurant-discount mappings found to delete for discount {} and menu {}", 
                discountId, menuId);
        }
    }

    /**
     * Restores one or more soft-deleted discounts by setting isDeleted flag to false.
     * Only restores discounts that are currently deleted. Updates updatedBy and updatedAt fields.
     *
     * @param ids    list of discount UUIDs to restore
     * @param userId the ID of the user performing the restore
     * @param locale locale code for localized error messages
     * @return ResponseDto with success message
     * @throws ResponseStatusException if user not found, discounts not found, or no deleted discounts to restore
     */
    @Override
    @Transactional
    public ResponseDto<Void> restoreDiscounts(List<UUID> ids, String userId, String locale) {
        Locale userLocale = Locale.forLanguageTag(locale);
        
        // Find user for updatedBy
        User user = userRepository.findById(UUID.fromString(userId))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        messageUtil.getMessage(MSG_USER_NOT_FOUND, userLocale)));
        
        // Find all discounts by IDs
        List<Discount> discounts = discountRepository.findAllById(ids);
        
        if (discounts.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                    messageUtil.getMessage(MSG_DISCOUNT_NOT_FOUND, userLocale));
        }
        
        // Filter only deleted discounts and restore them
        List<Discount> deletedDiscounts = discounts.stream()
                .filter(d -> Boolean.TRUE.equals(d.getIsDeleted()))
                .collect(Collectors.toList());
        
        if (deletedDiscounts.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    messageUtil.getMessage(MSG_DISCOUNT_RESTORE_ERROR_NOT_DELETED, userLocale));
        }
        
        // Restore all deleted discounts
        for (Discount discount : deletedDiscounts) {
            discount.setIsDeleted(false);
            discount.setUpdatedAt(OffsetDateTime.now(ZoneOffset.UTC));
            discount.setUpdatedBy(user);
        }
        
        discountRepository.saveAll(deletedDiscounts);
        
        return ResponseDto.<Void>builder()
            .message(messageUtil.getMessage(MSG_DISCOUNT_RESTORE_SUCCESS, userLocale))
            .build();
    }
}