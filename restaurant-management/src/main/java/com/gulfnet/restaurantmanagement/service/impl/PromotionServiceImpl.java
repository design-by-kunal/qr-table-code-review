package com.gulfnet.restaurantmanagement.service.impl;

import com.gulfnet.restaurantmanagement.service.PromotionService;
import com.gulfnet.restaurantmanagement.service.AuditTrailService;
import com.gulfnet.shared_library.enums.ActionType;
import com.gulfnet.shared_library.entity.Promotion;
import com.gulfnet.shared_library.entity.PromotionTranslation;
import com.gulfnet.shared_library.entity.Discount;
import com.gulfnet.shared_library.entity.User;
import com.gulfnet.shared_library.enums.EntityStatus;
import com.gulfnet.shared_library.enums.RequestStatus;
import com.gulfnet.shared_library.enums.PromotionType;
import com.gulfnet.shared_library.model.request.PromotionRequest;
import com.gulfnet.shared_library.model.request.PromotionTranslationRequest;
import com.gulfnet.shared_library.model.response.dto.PromotionDto;
import com.gulfnet.shared_library.model.response.dto.PromotionResponse;
import com.gulfnet.shared_library.model.response.dto.PromotionTranslationResponse;
import com.gulfnet.restaurantmanagement.config.LocalizationProperties;
import com.gulfnet.shared_library.util.TranslationUtils;
import com.gulfnet.shared_library.model.response.dto.ResponseDto;
import com.gulfnet.shared_library.repository.PromotionRepository;
import com.gulfnet.shared_library.repository.PromotionTranslationRepository;
import com.gulfnet.shared_library.repository.DiscountRepository;
import com.gulfnet.shared_library.repository.UserRepository;
import com.gulfnet.shared_library.config.AWSService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;

import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import java.util.Set;

import java.time.OffsetDateTime;
import java.time.OffsetTime;
import java.time.ZoneOffset;
import com.gulfnet.shared_library.enums.DayOfWeek;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import com.gulfnet.shared_library.model.response.dto.PromotionListResponse;
import com.gulfnet.shared_library.model.response.dto.PaginationMetaData;
import com.gulfnet.shared_library.util.LocaleSortUtil;
import java.util.stream.Collectors;
import org.springframework.data.domain.Sort;
import com.gulfnet.restaurantmanagement.util.MessageUtil;
import com.gulfnet.shared_library.entity.Menu;
import com.gulfnet.shared_library.entity.MenuPromotionMapping;
import com.gulfnet.shared_library.entity.MenuPromotionId;
import com.gulfnet.shared_library.repository.MenuRepository;
import com.gulfnet.shared_library.repository.MenuPromotionMappingRepository;
import com.gulfnet.shared_library.model.request.MenuPromotionMappingRequest;
import com.gulfnet.shared_library.model.response.dto.MenuPromotionResponseDto;
import com.gulfnet.shared_library.model.response.dto.MenuPromotionListResponse;
import com.gulfnet.shared_library.repository.MenuDiscountMappingRepository;
import com.gulfnet.shared_library.entity.RestaurantPromotionMapping;
import com.gulfnet.shared_library.entity.RestaurantPromotionId;
import com.gulfnet.shared_library.entity.Restaurant;
import com.gulfnet.shared_library.entity.RestaurantMenuMapping;
import com.gulfnet.shared_library.repository.RestaurantPromotionMappingRepository;
import com.gulfnet.shared_library.repository.RestaurantMenuMappingRepository;
import com.gulfnet.shared_library.repository.RestaurantRepository;
import com.gulfnet.shared_library.model.request.UpdateRestaurantPromotionValidityRequest;
import com.gulfnet.shared_library.entity.PromotionMenuComboMapping;
import com.gulfnet.shared_library.entity.MenuCategoryComboMapping;
import com.gulfnet.shared_library.entity.Combo;
import com.gulfnet.shared_library.entity.ComboTranslation;
import com.gulfnet.shared_library.model.response.dto.ComboTranslationDto;
import com.gulfnet.shared_library.repository.PromotionMenuComboMappingRepository;
import com.gulfnet.shared_library.repository.MenuCategoryComboMappingRepository;
import com.gulfnet.shared_library.repository.ComboTranslationRepository;
import com.gulfnet.restaurantmanagement.service.ComboService;
import com.gulfnet.shared_library.model.response.dto.ComboDetailsResponse;
import com.gulfnet.shared_library.model.response.dto.ComboDto;

@Service
@Slf4j
public class PromotionServiceImpl implements PromotionService {

    private static final String msgMenuNotFound = "menu.not.found";
    private static final String msgPromotionErrorInvalidType = "promotion.error.invalid.type";
    private static final String msgRestaurantNotFound = "restaurant.not.found";
    private static final String msgUserNotFound = "user.not.found";
    private static final String msgPromotionErrorInvalidLanguage = "promotion.error.invalid.language";
    private static final String msgPromotionAlreadyDeleted = "promotion.already.deleted";
    private static final String msgPromotionNotFound = "promotion.not.found";
    private static final String msgPromotionListSuccess = "promotion.list.success";
    private static final String msgPromotionErrorInvalidStatus = "promotion.error.invalid.status";

    @Autowired
    private PromotionRepository promotionRepository;

    @Autowired
    private PromotionTranslationRepository promotionTranslationRepository;

    @Autowired
    private DiscountRepository discountRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private AWSService awsService;

    @Autowired
    private MessageUtil messageUtil;

    @Autowired
    private LocalizationProperties localizationProperties;

    @Autowired
    private MenuRepository menuRepository;

    @Autowired
    private MenuPromotionMappingRepository menuPromotionMappingRepository;

    @Autowired
    private MenuDiscountMappingRepository menuDiscountMappingRepository;

    @Autowired
    private RestaurantPromotionMappingRepository restaurantPromotionMappingRepository;

    @Autowired
    private RestaurantMenuMappingRepository restaurantMenuMappingRepository;

    @Autowired
    private AuditTrailService auditTrailService;

    @Autowired
    private RestaurantRepository restaurantRepository;

    @Autowired
    private PromotionMenuComboMappingRepository promotionMenuComboMappingRepository;

    @Autowired
    private MenuCategoryComboMappingRepository menuCategoryComboMappingRepository;

    @Autowired
    private ComboTranslationRepository comboTranslationRepository;

    @Autowired
    private ComboService comboService;

    // Constants
    private static final String DEFAULT_NO_TRANSLATIONS = "No translations";
    private static final String ENTITY_TYPE_PROMOTION = "PROMOTION";

    /**
     * Creates a new promotion with translations, discount assignment (if applicable), and image handling.
     * Validates promotion type, status, translations, and discount requirements based on promotion type.
     * Creates audit trail for promotion creation.
     *
     * @param userId ID of the user creating the promotion
     * @param request Promotion request containing type, status, translations, discount ID, and image URL
     * @param locale Locale for error messages and localization
     * @return ResponseDto containing the created promotion response
     */
    @Override
    @Transactional
    public ResponseDto<PromotionDto<PromotionResponse>> createPromotion(String userId, PromotionRequest request, String locale) {
        Locale userLocale = Locale.forLanguageTag(locale);
        
        // Validate and convert promotion type
        PromotionType promotionType;
        try {
            promotionType = PromotionType.valueOf(request.getType().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                messageUtil.getMessage(msgPromotionErrorInvalidType, userLocale));
        }

        // Validate status
        if (request.getStatus() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                messageUtil.getMessage("promotion.error.status.required", userLocale));
        }
        
        // Validate user
        User user = userRepository.findById(UUID.fromString(userId))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        messageUtil.getMessage(msgUserNotFound, userLocale)));

        // Validate translations: check for duplicate and invalid language codes
        List<PromotionTranslationRequest> translations = request.getTranslations();
        if (translations != null && !translations.isEmpty()) {
            // Validate that at least one translation has a non-empty name
            boolean hasValidName = translations.stream()
                .anyMatch(t -> t.getName() != null && !t.getName().trim().isEmpty());
            
            if (!hasValidName) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        messageUtil.getMessage("item.update.error.no.valid.name", userLocale));
            }
            
            Set<String> languageCodes = new java.util.HashSet<>();
            for (PromotionTranslationRequest tr : translations) {
                String name = tr.getName();
                String lang = tr.getLanguageCode();
                
                // Only validate non-empty names
                if (name != null && !name.trim().isEmpty() && lang != null) {
                    if (!localizationProperties.getLanguages().contains(lang)) {
                        throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                            messageUtil.getMessage("error.invalid.language", userLocale, lang));
                    }
                    if (!languageCodes.add(lang)) {
                        throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                            messageUtil.getMessage("promotion.translation.error.duplicate.language", userLocale));
                    }
                }
            }
        } else {
            // No translations provided at all
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    messageUtil.getMessage("combo.translations.required", userLocale));
        }

        // Validate promotion type and discount requirements
        if (PromotionType.DISCOUNT.equals(promotionType)) {
            if (request.getDiscountId() == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        messageUtil.getMessage("promotion.error.discount.required", userLocale));
            }
        } else if ((PromotionType.GENERIC.equals(promotionType) || PromotionType.COMBO.equals(promotionType)) 
                && request.getDiscountId() != null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    messageUtil.getMessage("promotion.error.discount.not.allowed", userLocale));
        }

        // Validate discount if provided
        Discount discount = null;
        if (request.getDiscountId() != null) {
            discount = discountRepository.findById(request.getDiscountId())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                            messageUtil.getMessage("discount.not.found", userLocale)));
            // Additional validation for discount status
            if (Boolean.TRUE.equals(discount.getIsDeleted()) || discount.getStatus() != EntityStatus.ACTIVE) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        messageUtil.getMessage("promotion.error.discount.inactive.or.deleted", userLocale));
            }
        }

        // Convert status from String to EntityStatus with error handling
        EntityStatus entityStatus;
        try {
            entityStatus = EntityStatus.valueOf(request.getStatus().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                messageUtil.getMessage(msgPromotionErrorInvalidStatus, userLocale));
        }

        // Create promotion entity
        Promotion promotion = Promotion.builder()
                .type(promotionType)
                .imageUrl(awsService.stripToKey(request.getImageUrl()))
                .discount(discount)
                .status(entityStatus)
                .isDeleted(false)
                .createdBy(user)
                .build();

        promotion = promotionRepository.save(promotion);

        // Save translations
        for (PromotionTranslationRequest translationRequest : request.getTranslations()) {
            String name = translationRequest.getName();
            if (name != null && !name.trim().isEmpty()) {
                PromotionTranslation translation = PromotionTranslation.builder()
                        .promotion(promotion)
                        .languageCode(translationRequest.getLanguageCode())
                        .name(name.trim())
                        .heading(translationRequest.getHeading() != null ? translationRequest.getHeading().trim() : null)
                        .description(translationRequest.getDescription())
                        .build();
                promotionTranslationRepository.save(translation);
            }
        }

        // Create response
        PromotionResponse response = buildPromotionResponse(promotion);
        PromotionDto<PromotionResponse> promotionDto = PromotionDto.<PromotionResponse>builder()
                .promotion(response)
                .build();

        // Create audit trail for promotion creation
        try {
            Restaurant restaurant = null;
            if (user.getRestaurantId() != null) {
                restaurant = restaurantRepository.findById(user.getRestaurantId()).orElse(null);
            }
            List<PromotionTranslation> promotionTranslations = promotionTranslationRepository.findAllByPromotionId(promotion.getId());
            String promotionName = promotionTranslations.isEmpty() ? 
                DEFAULT_NO_TRANSLATIONS : promotionTranslations.get(0).getName();
            auditTrailService.createAuditTrail(
                    user,
                    ActionType.PROMOTION_CREATE,
                    restaurant,
                    null, // status - will default to NA for non-request actions
                    null, // ipAddress - not available in this context
                    null, // userAgent - not available in this context
                    promotion.getId(),
                    ENTITY_TYPE_PROMOTION,
                    "Promotion created: " + promotionName
            );
        } catch (Exception e) {
            log.error("Failed to create audit trail for promotion creation: {}", e.getMessage());
            // Don't break promotion creation flow if audit trail fails
        }

        return ResponseDto.<PromotionDto<PromotionResponse>>builder()
                .message(messageUtil.getMessage("promotion.create.success", userLocale))
                .data(promotionDto)
                .build();
    }

    /**
     * Retrieves a paginated and filterable list of promotions.
     * Supports filtering by status, type, search term, and deleted status.
     * Applies locale-specific translations and sorting.
     *
     * @param page Page number (1-based)
     * @param size Number of records per page
     * @param status Filter by promotion status (ACTIVE, INACTIVE, etc.)
     * @param type Filter by promotion type (DISCOUNT, COMBO, GENERIC)
     * @param search Search term to filter by promotion name or heading
     * @param sortBy Field to sort by
     * @param direction Sort direction (ASC or DESC)
     * @param locale Locale for translations
     * @param isDeleted Whether to include deleted promotions (true) or exclude them (false/null)
     * @return ResponseDto containing a paginated list of promotions
     */
    @Override
    @Transactional(readOnly = true)
    public ResponseDto<PromotionListResponse> getPromotions(
            Integer page, 
            Integer size, 
            String status, 
            String type, 
            String search, 
            String sortBy, 
            Sort.Direction direction, 
            String locale,
            Boolean isDeleted) {
        
        Locale userLocale = LocaleContextHolder.getLocale();

        // Validate and set pagination
        int pageNumber = (page != null ? page : 1) - 1;
        if (pageNumber < 0) pageNumber = 0;
        int pageSize = size != null ? size : Integer.MAX_VALUE;
        if (pageSize < 1) pageSize = Integer.MAX_VALUE;

        // Process status filter
        final EntityStatus statusEnum;
        if (status != null && !status.isEmpty()) {
            try {
                statusEnum = EntityStatus.valueOf(status.toUpperCase());
            } catch (IllegalArgumentException e) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        messageUtil.getMessage(msgPromotionErrorInvalidStatus, userLocale));
            }
        } else {
            statusEnum = null;
        }

        // Process type filter
        final PromotionType typeEnum;
        if (type != null && !type.isEmpty()) {
            try {
                typeEnum = PromotionType.valueOf(type.toUpperCase());
            } catch (IllegalArgumentException e) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        messageUtil.getMessage(msgPromotionErrorInvalidType, userLocale));
            }
        } else {
            typeEnum = null;
        }

        // Build specification for filtering
        final EntityStatus finalStatusEnum = statusEnum;
        final PromotionType finalTypeEnum = typeEnum;
        Specification<Promotion> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            
            // Handle isDeleted filter: if isDeleted=true, show deleted; otherwise show non-deleted (default)
            if (isDeleted != null && isDeleted) {
                predicates.add(cb.equal(root.get("isDeleted"), true));
            } else {
                predicates.add(cb.equal(root.get("isDeleted"), false));
            }
            
            if (finalStatusEnum != null) {
                predicates.add(cb.equal(root.get("status"), finalStatusEnum));
            }
            
            if (finalTypeEnum != null) {
                predicates.add(cb.equal(root.get("type"), finalTypeEnum));
            }
            
            if (search != null && !search.trim().isEmpty()) {
                String searchPattern = "%" + search.trim().toLowerCase() + "%";
                Join<Promotion, PromotionTranslation> translationJoin = root.join("translations", JoinType.LEFT);
                Predicate namePredicate = cb.like(cb.lower(translationJoin.get("name")), searchPattern);
                Predicate headingPredicate = cb.like(cb.lower(translationJoin.get("heading")), searchPattern);
                predicates.add(cb.or(namePredicate, headingPredicate));
                query.distinct(true);
            }
            
            return cb.and(predicates.toArray(new Predicate[0]));
        };

        // Get filtered promotions from database
        List<Promotion> promotions = promotionRepository.findAll(spec);

        // Batch fetch discounts to avoid N+1 queries
        List<UUID> promotionIds = promotions.stream()
                .map(Promotion::getId)
                .collect(Collectors.toList());
        
        Map<UUID, UUID> promotionToDiscountMap = new HashMap<>();
        Map<UUID, Discount> discountMap = new HashMap<>();
        
        if (!promotionIds.isEmpty()) {
            // Get promotion to discount ID mappings
            List<Object[]> mappings = promotionRepository.findPromotionDiscountMappings(promotionIds);
            for (Object[] mapping : mappings) {
                UUID promoId = (UUID) mapping[0];
                UUID discountId = (UUID) mapping[1];
                if (discountId != null) {
                    promotionToDiscountMap.put(promoId, discountId);
                }
            }
            
            // Batch fetch all discounts
            List<UUID> discountIds = new ArrayList<>(promotionToDiscountMap.values());
            if (!discountIds.isEmpty()) {
                List<Discount> discounts = discountRepository.findAllById(discountIds);
                discountMap = discounts.stream()
                        .collect(Collectors.toMap(Discount::getId, discount -> discount));
            }
        }
        
        // Set discounts on promotions to avoid lazy loading
        final Map<UUID, Discount> finalDiscountMap = discountMap;
        final Map<UUID, UUID> finalPromotionToDiscountMap = promotionToDiscountMap;
        promotions.forEach(promotion -> {
            UUID discountId = finalPromotionToDiscountMap.get(promotion.getId());
            if (discountId != null) {
                Discount discount = finalDiscountMap.get(discountId);
                if (discount != null) {
                    promotion.setDiscount(discount);
                }
            }
        });

        // Convert to response DTOs with locale-specific translations
        List<PromotionResponse> promotionResponses = promotions.stream()
                .map(promotion -> {
                    // Apply fallback language logic for promotion translations
                    String promotionName = "";
                    String promotionHeading = "";
                    String promotionDescription = "";
                    String selectedLanguageCode = locale;
                    List<PromotionTranslation> translations = promotionTranslationRepository.findAllByPromotionId(promotion.getId());

                    ResolvedPromotionTexts texts = resolvePromotionTexts(translations, locale);
                    promotionName = texts.name;
                    promotionHeading = texts.heading;
                    promotionDescription = texts.description;
                    selectedLanguageCode = locale;

                    // Build translation DTOs for display (only the selected translation)
                    List<PromotionTranslationResponse> translationResponses = List.of(
                        PromotionTranslationResponse.builder()
                            .languageCode(selectedLanguageCode)
                            .name(promotionName)
                            .heading(promotionHeading)
                            .description(promotionDescription)
                            .build()
                    );

                    // Generate presigned URL for image
                    String signedImageUrl = null;
                    if (promotion.getImageUrl() != null && !promotion.getImageUrl().isEmpty()) {
                        signedImageUrl = awsService.getPreSignedUrl(promotion.getImageUrl());
                    }

                    // Calculate assigned menu count for this specific promotion
                    Long assignedMenuCount = menuPromotionMappingRepository.countByPromotionId(promotion.getId());

                    return PromotionResponse.builder()
                            .id(promotion.getId())
                            .type(promotion.getType())
                            .imageUrl(signedImageUrl)
                            .translations(translationResponses)
                            .discountId(promotion.getDiscount() != null ? promotion.getDiscount().getId() : null)
                            .status(promotion.getStatus())
                            .isDeleted(promotion.getIsDeleted())
                            .assignedMenuCount(assignedMenuCount)
                            .createdAt(promotion.getCreatedAt() != null ? promotion.getCreatedAt().toLocalDateTime() : null)
                            .createdBy(formatUserName(promotion.getCreatedBy()))
                            .updatedAt(promotion.getUpdatedAt() != null ? promotion.getUpdatedAt().toLocalDateTime() : null)
                            .updatedBy(formatUserName(promotion.getUpdatedBy()))
                            .build();
                })
                .collect(Collectors.toList());

        // Apply sorting using shared library sort method
        LocaleSortUtil.sortName(promotionResponses, sortBy, direction);

        // Apply pagination
        int fromIndex = pageNumber * pageSize;
        int toIndex = Math.min(fromIndex + pageSize, promotionResponses.size());
        List<PromotionResponse> paginatedResponses = promotionResponses.subList(fromIndex, toIndex);

        // Build pagination metadata
        PaginationMetaData paginationMetaData = PaginationMetaData.builder()
                .page(pageNumber + 1)
                .size(pageSize)
                .totalPages((int) Math.ceil((double) promotionResponses.size() / pageSize))
                .totalRecords((long) promotionResponses.size())
                .build();

        // Build final response
        PromotionListResponse listResponse = PromotionListResponse.builder()
                .promotions(paginatedResponses)
                .count((long) paginatedResponses.size())
                .total((long) promotionResponses.size())
                .metaData(paginationMetaData)
                .build();

        return ResponseDto.<PromotionListResponse>builder()
                .message(messageUtil.getMessage(msgPromotionListSuccess, userLocale))
                .data(listResponse)
                .build();
    }

    /**
     * Soft-deletes a promotion by setting its isDeleted flag to true.
     * Validates that the promotion is not assigned to any published menus before deletion.
     * Creates audit trail for promotion deletion.
     *
     * @param id UUID of the promotion to delete
     * @param userId ID of the user performing the deletion
     * @return ResponseDto with success message
     */
    @Override
    @Transactional
    public ResponseDto<String> deletePromotion(UUID id, String userId) {
        Locale userLocale = LocaleContextHolder.getLocale();

        // Validate user
        User user = userRepository.findById(UUID.fromString(userId))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        messageUtil.getMessage(msgUserNotFound, userLocale)));

        // Find promotion
        Promotion promotion = promotionRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        messageUtil.getMessage(msgPromotionNotFound, userLocale)));

        // Check if already deleted
        if (Boolean.TRUE.equals(promotion.getIsDeleted())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    messageUtil.getMessage(msgPromotionAlreadyDeleted, userLocale));
        }

        // Check if promotion is assigned to any published menus
        if (menuPromotionMappingRepository.existsByPromotionIdAndMenuIsPublished(id)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    messageUtil.getMessage("promotion.cannot.delete.assigned.to.published.menu", userLocale));
        }

        // Soft delete
        promotion.setIsDeleted(true);
        promotion.setUpdatedAt(OffsetDateTime.now(ZoneOffset.UTC));
        promotion.setUpdatedBy(user);
        promotionRepository.save(promotion);

        // Create audit trail for promotion deletion
        try {
            Restaurant restaurant = null;
            if (user.getRestaurantId() != null) {
                restaurant = restaurantRepository.findById(user.getRestaurantId()).orElse(null);
            }
            List<PromotionTranslation> promotionTranslations = promotionTranslationRepository.findAllByPromotionId(promotion.getId());
            String promotionName = promotionTranslations.isEmpty() ? 
                DEFAULT_NO_TRANSLATIONS : promotionTranslations.get(0).getName();
            auditTrailService.createAuditTrail(
                    user,
                    ActionType.PROMOTION_DELETE,
                    restaurant,
                    null, // status - will default to NA for non-request actions
                    null, // ipAddress - not available in this context
                    null, // userAgent - not available in this context
                    promotion.getId(),
                    ENTITY_TYPE_PROMOTION,
                    "Promotion deleted: " + promotionName
            );
        } catch (Exception e) {
            log.error("Failed to create audit trail for promotion deletion: {}", e.getMessage());
            // Don't break promotion deletion flow if audit trail fails
        }

        return ResponseDto.<String>builder()
                .message(messageUtil.getMessage("promotion.delete.success", userLocale))
                .data(null)
                .build();
    }

    /**
     * Retrieves detailed information about a specific promotion.
     * For DISCOUNT type promotions, validates discount assignment to menu if menuId is provided.
     * For COMBO type promotions, includes combo details and translations.
     * Includes all translations, discount information, and assigned menu count.
     *
     * @param id UUID of the promotion to retrieve
     * @param menuId Optional UUID of the menu for validation (required for DISCOUNT type promotions)
     * @param locale Locale for translations
     * @return ResponseDto containing the promotion details
     */
    @Override
    @Transactional(readOnly = true)
    public ResponseDto<PromotionDto<PromotionResponse>> getPromotionDetails(UUID id, UUID menuId, String locale) {
        Locale userLocale = LocaleContextHolder.getLocale();

        // Find promotion
        Promotion promotion = promotionRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        messageUtil.getMessage(msgPromotionNotFound, userLocale)));

        // Check if deleted
        if (Boolean.TRUE.equals(promotion.getIsDeleted())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    messageUtil.getMessage(msgPromotionAlreadyDeleted, userLocale));
        }

        // Validate menuId if provided and promotion has discount
        if (menuId != null && promotion.getType() == PromotionType.DISCOUNT && promotion.getDiscount() != null) {
            boolean isDiscountAssignedToMenu = menuDiscountMappingRepository.isDiscountAssignedToMenu(
                    promotion.getDiscount().getId(), menuId);
            
            if (!isDiscountAssignedToMenu) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        messageUtil.getMessage("promotion.error.discount.not.assigned.to.menu", userLocale));
            }
        }

        // Build response
        PromotionResponse response = buildPromotionResponse(promotion);

        // Set assigned menu count
        response.setAssignedMenuCount(menuPromotionMappingRepository.countByPromotionId(promotion.getId()));

        // Set discount info if type is DISCOUNT
        if (promotion.getType() == PromotionType.DISCOUNT) {
            response.setDiscountApplied(true);
            if (promotion.getDiscount() != null) {
                if (promotion.getDiscount().getDiscountType() != null) {
                    response.setDiscountType(promotion.getDiscount().getDiscountType().name());
                } else {
                    response.setDiscountType(null);
                }
                if (promotion.getDiscount().getAppliedTo() != null) {
                    response.setDiscountAppliedTo(promotion.getDiscount().getAppliedTo().name());
                } else {
                    response.setDiscountAppliedTo(null);
                }
            } else {
                response.setDiscountType(null);
                response.setDiscountAppliedTo(null);
            }
            // Clear combo info for DISCOUNT type
            response.setComboId(null);
            response.setComboTranslations(null);
        } else if (promotion.getType() == PromotionType.COMBO) {
            // Set combo info for COMBO type - optimized single query
            response.setDiscountApplied(false);
            response.setDiscountType(null);
            response.setDiscountAppliedTo(null);
            
            // Optimized query: fetch combo mapping with combo and menu in one go
            List<PromotionMenuComboMapping> comboMappings = 
                promotionMenuComboMappingRepository.findByPromotionIdWithComboAndMenu(promotion.getId(), menuId);
            
            if (!comboMappings.isEmpty()) {
                // Use the first mapping (already filtered by menuId if provided)
                PromotionMenuComboMapping comboMapping = comboMappings.get(0);
                
                if (comboMapping != null 
                    && comboMapping.getMenuCategoryComboMapping() != null
                    && comboMapping.getMenuCategoryComboMapping().getCombo() != null) {
                    
                    Combo combo = comboMapping.getMenuCategoryComboMapping().getCombo();
                    response.setComboId(combo.getComboId());
                    
                    // Batch fetch all combo translations in one query
                    List<ComboTranslation> comboTranslations = 
                        comboTranslationRepository.findByComboComboId(combo.getComboId());
                    
                    // Convert all translations to DTOs (return all translations like promotion translations)
                    List<ComboTranslationDto> comboTranslationDtos = comboTranslations.stream()
                        .map(translation -> ComboTranslationDto.builder()
                            .languageCode(translation.getLanguageCode())
                            .name(translation.getName())
                            .description(translation.getDescription())
                            .build())
                        .collect(Collectors.toList());
                    
                    response.setComboTranslations(comboTranslationDtos);
                }
            } else {
                response.setComboId(null);
                response.setComboTranslations(null);
            }
        } else {
            // GENERIC type
            response.setDiscountApplied(false);
            response.setDiscountType(null);
            response.setDiscountAppliedTo(null);
            response.setComboId(null);
            response.setComboTranslations(null);
        }
        PromotionDto<PromotionResponse> promotionDto = PromotionDto.<PromotionResponse>builder()
                .promotion(response)
                .build();

        return ResponseDto.<PromotionDto<PromotionResponse>>builder()
                .message(messageUtil.getMessage("promotion.detail.success", userLocale))
                .data(promotionDto)
                .build();
    }

    /**
     * Updates an existing promotion, including type, status, translations, discount assignment, and image URL.
     * Validates promotion type and discount requirements, replaces all translations, and creates audit trail.
     *
     * @param id UUID of the promotion to update
     * @param request Promotion request containing updated fields
     * @param userId ID of the user performing the update
     * @param locale Locale for error messages and localization
     * @return ResponseDto containing the updated promotion response
     */
    @Override
    @Transactional
    public ResponseDto<PromotionDto<PromotionResponse>> updatePromotion(UUID id, PromotionRequest request, String userId, String locale) {
        Locale userLocale = Locale.forLanguageTag(locale);

        // Validate and convert promotion type
        PromotionType promotionType;
        try {
            promotionType = PromotionType.valueOf(request.getType().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                messageUtil.getMessage(msgPromotionErrorInvalidType, userLocale));
        }

        // Validate status
        if (request.getStatus() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                messageUtil.getMessage("promotion.error.status.required", userLocale));
        }

        // 1. Check if promotion exists
        Promotion promotion = promotionRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        messageUtil.getMessage(msgPromotionNotFound, userLocale)));

        // 2. Check if deleted
        if (Boolean.TRUE.equals(promotion.getIsDeleted())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    messageUtil.getMessage(msgPromotionAlreadyDeleted, userLocale));
        }

        // 3. Validate promotion type and discount requirements
        if (PromotionType.GENERIC.equals(promotionType) || PromotionType.COMBO.equals(promotionType)) {
            if (request.getDiscountId() != null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        messageUtil.getMessage("promotion.error.discount.not.allowed", userLocale));
            }
        } else if (PromotionType.DISCOUNT.equals(promotionType) && request.getDiscountId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    messageUtil.getMessage("promotion.error.discount.required", userLocale));
        }

        // Validate translations: check for duplicate and invalid language codes
        List<PromotionTranslationRequest> translations = request.getTranslations();
        if (translations != null && !translations.isEmpty()) {
            // Validate that at least one translation has a non-empty name
            boolean hasValidName = translations.stream()
                .anyMatch(t -> t.getName() != null && !t.getName().trim().isEmpty());
            
            if (!hasValidName) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        messageUtil.getMessage("item.update.error.no.valid.name", userLocale));
            }
            
            Set<String> languageCodes = new java.util.HashSet<>();
            for (PromotionTranslationRequest tr : translations) {
                String name = tr.getName();
                String lang = tr.getLanguageCode();
                
                // Only validate non-empty names
                if (name != null && !name.trim().isEmpty() && lang != null) {
                    if (!localizationProperties.getLanguages().contains(lang)) {
                        throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                            messageUtil.getMessage("error.invalid.language", userLocale, lang));
                    }
                    if (!languageCodes.add(lang)) {
                        throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                            messageUtil.getMessage("promotion.translation.error.duplicate.language", userLocale));
                    }
                }
            }
        } else {
            // No translations provided at all
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    messageUtil.getMessage("combo.translations.required", userLocale));
        }

        // 5. Update fields
        promotion.setType(promotionType);
        promotion.setImageUrl(awsService.stripToKey(request.getImageUrl()));
        // Convert status from String to EntityStatus with error handling
        EntityStatus updateEntityStatus;
        try {
            updateEntityStatus = EntityStatus.valueOf(request.getStatus().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                messageUtil.getMessage(msgPromotionErrorInvalidStatus, userLocale));
        }
        promotion.setStatus(updateEntityStatus);
        // Discount validation for update
        Discount discount = null;
        if (request.getDiscountId() != null) {
            discount = discountRepository.findById(request.getDiscountId())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                            messageUtil.getMessage("discount.not.found", userLocale)));
            if (Boolean.TRUE.equals(discount.getIsDeleted()) || discount.getStatus() != EntityStatus.ACTIVE) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        messageUtil.getMessage("promotion.error.discount.inactive.or.deleted", userLocale));
            }
            promotion.setDiscount(discount);
        } else {
            promotion.setDiscount(null);
        }
        promotion.setUpdatedAt(OffsetDateTime.now(ZoneOffset.UTC));
        promotion.setUpdatedBy(userRepository.findById(UUID.fromString(userId)).orElse(null));
        promotionRepository.save(promotion);

        // Remove old translations and add new ones
        promotionTranslationRepository.deleteAll(promotionTranslationRepository.findAllByPromotionId(promotion.getId()));
        request.getTranslations().forEach(tr -> {
            String name = tr.getName();
            if (name != null && !name.trim().isEmpty()) {
                PromotionTranslation translation = PromotionTranslation.builder()
                        .promotion(promotion)
                        .languageCode(tr.getLanguageCode())
                        .name(name.trim())
                        .heading(tr.getHeading() != null ? tr.getHeading().trim() : null)
                        .description(tr.getDescription())
                        .build();
                promotionTranslationRepository.save(translation);
            }
        });

        // Build response
        PromotionResponse response = buildPromotionResponse(promotion);
        PromotionDto<PromotionResponse> promotionDto = PromotionDto.<PromotionResponse>builder()
                .promotion(response)
                .build();

        // Create audit trail for promotion update
        try {
            User user = userRepository.findById(UUID.fromString(userId))
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                            messageUtil.getMessage(msgUserNotFound, userLocale)));
            Restaurant restaurant = null;
            if (user.getRestaurantId() != null) {
                restaurant = restaurantRepository.findById(user.getRestaurantId()).orElse(null);
            }
            List<PromotionTranslation> promotionTranslations = promotionTranslationRepository.findAllByPromotionId(promotion.getId());
            String promotionName = promotionTranslations.isEmpty() ? 
                DEFAULT_NO_TRANSLATIONS : promotionTranslations.get(0).getName();
            auditTrailService.createAuditTrail(
                    user,
                    ActionType.PROMOTION_UPDATE,
                    restaurant,
                    null, // status - will default to NA for non-request actions
                    null, // ipAddress - not available in this context
                    null, // userAgent - not available in this context
                    promotion.getId(),
                    ENTITY_TYPE_PROMOTION,
                    "Promotion updated: " + promotionName
            );
        } catch (Exception e) {
            log.error("Failed to create audit trail for promotion update: {}", e.getMessage());
            // Don't break promotion update flow if audit trail fails
        }

        return ResponseDto.<PromotionDto<PromotionResponse>>builder()
                .message(messageUtil.getMessage("promotion.update.success", userLocale))
                .data(promotionDto)
                .build();
    }

    /**
     * Assigns a promotion to a menu with validity dates.
     * For COMBO type promotions, validates and creates promotion-menu-combo mapping.
     * Creates restaurant promotion mappings for all restaurants assigned to the menu.
     * Validates UTC datetime fields and creates audit trail.
     *
     * @param request Menu promotion mapping request containing menu ID, promotion ID, validity dates, and optional combo ID
     * @param userId ID of the user performing the assignment
     * @param locale Locale for error messages and localization
     * @return ResponseDto containing the menu promotion response
     */
    @Override
    @Transactional
    @CacheEvict(value = "restaurants", allEntries = true)
    public ResponseDto<MenuPromotionResponseDto> assignPromotionToMenu(MenuPromotionMappingRequest request, String userId, String locale) {
        Locale userLocale = Locale.forLanguageTag(locale);
        // Null/blank checks for required fields
        if (request.getMenuId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, messageUtil.getMessage("menu.id.required", userLocale));
        }
        if (request.getPromotionId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, messageUtil.getMessage("menu.promotion.mapping.promotionid.required", userLocale));
        }
        if (request.getValidFrom() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, messageUtil.getMessage("menu.promotion.mapping.validfrom.required", userLocale));
        }
        if (request.getValidTo() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, messageUtil.getMessage("menu.promotion.mapping.validto.required", userLocale));
        }
        // Validate menu
        Menu menu = menuRepository.findById(request.getMenuId())
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, messageUtil.getMessage(msgMenuNotFound, userLocale)));
        // Removed publish status validation for menu
        if (Boolean.TRUE.equals(menu.getIsDeleted())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, messageUtil.getMessage("menu.inactive.or.deleted", userLocale));
        }
        // Validate promotion
        Promotion promotion = promotionRepository.findById(request.getPromotionId())
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, messageUtil.getMessage(msgPromotionNotFound, userLocale)));
        if (Boolean.TRUE.equals(promotion.getIsDeleted()) || promotion.getStatus() != EntityStatus.ACTIVE) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, messageUtil.getMessage("promotion.inactive.or.deleted", userLocale));
        }
        
        // Validate COMBO type promotion requirements
        if (PromotionType.COMBO.equals(promotion.getType())) {
            if (request.getComboId() == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, 
                    messageUtil.getMessage("promotion.combo.id.required", userLocale));
            }
            
            // Find MenuCategoryComboMapping for the given combo and menu
            // This validates that the combo is assigned to a category in the specified menu
            List<MenuCategoryComboMapping> menuCategoryComboMappings = 
                menuCategoryComboMappingRepository.findByCombo_ComboIdAndMenuCategoryMapping_Menu_Id(
                    request.getComboId(), request.getMenuId());
            
            if (menuCategoryComboMappings.isEmpty()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, 
                    messageUtil.getMessage("promotion.combo.mapping.not.found", userLocale));
            }
            
            // If multiple mappings exist (combo in multiple categories in same menu), use the first one
            // Log a warning if multiple exist
            if (menuCategoryComboMappings.size() > 1) {
                log.warn("Multiple menu category combo mappings found for combo {} in menu {}. Using the first one.", 
                    request.getComboId(), request.getMenuId());
            }
            
            MenuCategoryComboMapping menuCategoryComboMapping = menuCategoryComboMappings.get(0);
            
            // Create promotion-menu-combo mapping
            PromotionMenuComboMapping promotionMenuComboMapping = PromotionMenuComboMapping.builder()
                .promotion(promotion)
                .menuCategoryComboMapping(menuCategoryComboMapping)
                .build();
            promotionMenuComboMappingRepository.save(promotionMenuComboMapping);
            log.info("Created promotion-menu-combo mapping for promotion {} and combo {} in menu {}", 
                promotion.getId(), request.getComboId(), request.getMenuId());
        } else {
            // If comboId is provided but promotion type is not COMBO, throw error
            if (request.getComboId() != null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, 
                    messageUtil.getMessage("promotion.combo.id.not.allowed", userLocale));
            }
        }
        
        // Validate no duplicate mapping
        MenuPromotionId mappingId = new MenuPromotionId(request.getMenuId(), request.getPromotionId());
        if (menuPromotionMappingRepository.findById(mappingId).isPresent()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, messageUtil.getMessage("menu.promotion.mapping.duplicate", userLocale));
        }
        // Validate UTC datetime fields
        validateUtcDateTimeFields(request, userLocale, null, false);
        
        // Get validated values (already in UTC)
        OffsetDateTime validFrom = request.getValidFrom();
        OffsetDateTime validTo = request.getValidTo();
        // Save mapping
        MenuPromotionMapping mapping = MenuPromotionMapping.builder()
            .id(mappingId)
            .menu(menu)
            .promotion(promotion)
            .validFrom(validFrom)
            .validTo(validTo)
            .build();
        menuPromotionMappingRepository.save(mapping);

        // Create restaurant promotion mappings
        createRestaurantPromotionMappings(
            request.getMenuId(),
            promotion,
            validFrom,
            validTo,
            userLocale
        );
        
        // Create audit trail for menu update (promotion assignment)
        try {
            User user = findUserForAuditTrail(userId);
            
            Restaurant restaurant = null;
            if (user != null && user.getRestaurantId() != null) {
                restaurant = restaurantRepository.findById(user.getRestaurantId()).orElse(null);
            }
            
            String promotionName = "N/A";
            if (promotion.getTranslations() != null && !promotion.getTranslations().isEmpty()) {
                promotionName = promotion.getTranslations().get(0).getName();
            }
            
            auditTrailService.createAuditTrail(
                    user,
                    ActionType.MENU_UPDATE,
                    restaurant,
                    null, // status - will default to NA for non-request actions
                    null, // ipAddress - not available in this context
                    null, // userAgent - not available in this context
                    menu.getId(),
                    "MENU",
                    "Promotion assigned to menu: " + promotionName + " (ID: " + promotion.getId() + ")"
            );
            log.debug("Created audit trail for promotion assignment to menu: Menu ID: {}, Promotion ID: {}", 
                    menu.getId(), promotion.getId());
        } catch (Exception e) {
            log.error("Failed to create audit trail for promotion assignment to menu: {}", e.getMessage(), e);
            // Don't break promotion assignment flow if audit trail fails
        }

        // Build MenuPromotionResponseDto (similar to getMenuAssignedPromotions)
        List<PromotionTranslation> translations = promotionTranslationRepository.findAllByPromotionId(promotion.getId());
        List<PromotionTranslationResponse> filteredTranslations = new ArrayList<>();
        ResolvedPromotionTexts texts = resolvePromotionTexts(translations, locale);
        filteredTranslations.add(PromotionTranslationResponse.builder()
                .languageCode(locale)
                .name(texts.name)
                .heading(texts.heading)
                .description(texts.description)
                .build());
        boolean discountApplied = promotion.getType() == PromotionType.DISCOUNT;
        String discountType = null;
        String discountAppliedTo = null;
        if (discountApplied && promotion.getDiscount() != null) {
            Discount discount = promotion.getDiscount();
            discountType = discount.getDiscountType() != null ? discount.getDiscountType().name() : null;
            discountAppliedTo = discount.getAppliedTo() != null ? discount.getAppliedTo().name() : null;
        }
        String promotionImage = promotion.getImageUrl() != null ? awsService.getPreSignedUrl(promotion.getImageUrl()) : null;
        MenuPromotionResponseDto responseDto = MenuPromotionResponseDto.builder()
            .translations(filteredTranslations)
            .validFrom(validFrom)
            .validTo(validTo)
            .imageUrl(promotionImage)
            .discountApplied(discountApplied)
            .discountType(discountType)
            .discountAppliedTo(discountAppliedTo)
            .promotionId(promotion.getId())
            .type(promotion.getType())
            .build();
        // set discount fields explicitly to avoid builder mismatch across modules
        if (promotion.getDiscount() != null) {
            responseDto.setDiscountId(promotion.getDiscount().getId());
            responseDto.setDiscountValue(promotion.getDiscount().getValue());
        }

        return ResponseDto.<MenuPromotionResponseDto>builder()
            .message(messageUtil.getMessage("menu.promotion.mapping.success", userLocale))
            .data(responseDto)
            .build();
    }

    /**
     * Deletes a promotion assignment from a menu.
     * For COMBO type promotions, also deletes the promotion-menu-combo mapping.
     * Deletes restaurant promotion mappings for all restaurants assigned to the menu.
     *
     * @param menuId UUID of the menu from which to remove the promotion
     * @param promotionId UUID of the promotion to unassign
     * @param locale Locale for error messages
     * @return ResponseDto with success message
     */
    @Override
    @Transactional
    @CacheEvict(value = "restaurants", allEntries = true)
    public ResponseDto<String> deleteMenuPromotionAssignment(UUID menuId, UUID promotionId, String locale) {
        Locale userLocale = Locale.forLanguageTag(locale);
        // Validate menu
        Menu menu = menuRepository.findById(menuId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, messageUtil.getMessage(msgMenuNotFound, userLocale)));
        if (Boolean.TRUE.equals(menu.getIsDeleted())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, messageUtil.getMessage("menu.deleted", userLocale));
        }
        // Validate promotion
        Promotion promotion = promotionRepository.findById(promotionId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, messageUtil.getMessage(msgPromotionNotFound, userLocale)));
        if (Boolean.TRUE.equals(promotion.getIsDeleted())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, messageUtil.getMessage(msgPromotionAlreadyDeleted, userLocale));
        }
        // Validate mapping exists
        MenuPromotionId mappingId = new MenuPromotionId(menuId, promotionId);
        MenuPromotionMapping mapping = menuPromotionMappingRepository.findById(mappingId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, messageUtil.getMessage("menu.promotion.mapping.not.found", userLocale)));
        
        // If promotion type is COMBO, delete the promotion-menu-combo mapping
        // Optimized: Use direct delete query instead of fetch-filter-delete pattern
        if (PromotionType.COMBO.equals(promotion.getType())) {
            promotionMenuComboMappingRepository.deleteByPromotionIdAndMenuId(promotionId, menuId);
            log.info("Deleted promotion-menu-combo mapping for promotion {} and menu {}", 
                promotionId, menuId);
        }
        
        // Delete mapping
        menuPromotionMappingRepository.delete(mapping);
        
        // Delete restaurant-promotion mappings for all restaurants assigned to this menu
        deleteRestaurantPromotionMappingsForMenu(menuId, promotionId);
        
        return ResponseDto.<String>builder()
            .message(messageUtil.getMessage("menu.promotion.mapping.delete.success", userLocale))
            .data(null)
            .build();
    }



    /**
     * Retrieves a paginated and filterable list of promotions assigned to a specific menu.
     * Optionally filters by restaurant and availability status.
     * For COMBO type promotions, checks combo-based availability including date ranges, time ranges, and days of week.
     * Applies locale-specific translations and sorting.
     *
     * @param menuId UUID of the menu
     * @param restaurantId Optional UUID of the restaurant for filtering and availability checks
     * @param page Page number (1-based)
     * @param size Number of records per page
     * @param search Search term to filter by promotion name
     * @param isAvailable Filter to show only currently available promotions (requires restaurantId)
     * @param sortBy Field to sort by
     * @param direction Sort direction (ASC or DESC)
     * @param locale Locale for translations
     * @return ResponseDto containing a paginated list of menu promotions
     */
    @Override
@Transactional(readOnly = true)
public ResponseDto<MenuPromotionListResponse> getMenuAssignedPromotions(
        UUID menuId,
        UUID restaurantId,          // OPTIONAL
        Integer page,
        Integer size,
        String search,
        Boolean isAvailable,
        String sortBy,
        Sort.Direction direction,
        String locale
) {

    Locale userLocale = Locale.forLanguageTag(locale);

    // 1️⃣ Fetch menu → promotion mappings
    List<MenuPromotionMapping> mappings =
            menuPromotionMappingRepository.findByMenu_Id(menuId);

    List<MenuPromotionResponseDto> result = new ArrayList<>();

    // 2️⃣ Current UTC time (Java-based time check)
    OffsetDateTime nowUtc = OffsetDateTime.now(ZoneOffset.UTC);

    Map<UUID, Boolean> promotionAvailabilityMap = Collections.emptyMap();
    Map<UUID, RestaurantPromotionMapping> promotionStatusMap = Collections.emptyMap();
    
    if (restaurantId != null) {
        List<RestaurantPromotionMapping> restaurantMappings =
                restaurantPromotionMappingRepository
                        .findById_RestaurantId(restaurantId);
    
        promotionStatusMap = new HashMap<>();
        promotionAvailabilityMap = new HashMap<>();
    
        for (RestaurantPromotionMapping rpm : restaurantMappings) {
            UUID promotionId = rpm.getId().getPromotionId();
            promotionStatusMap.put(promotionId, rpm);
            
            if (Boolean.TRUE.equals(isAvailable)) {
                boolean availableNow = isPromotionCurrentlyAvailable(
                        rpm.getValidFrom(),
                        rpm.getValidTo(),
                        nowUtc
                );
                promotionAvailabilityMap.put(promotionId, availableNow);
            }
        }
    }
    

    // 4️⃣ Main loop over menu promotions
    for (MenuPromotionMapping mapping : mappings) {

        Promotion promotion = mapping.getPromotion();

        // Basic promotion validations and restaurant mapping status check
        boolean shouldSkip = promotion == null
                || Boolean.TRUE.equals(promotion.getIsDeleted())
                || promotion.getStatus() != EntityStatus.ACTIVE;
        
        // Check RestaurantPromotionMapping status (when restaurantId is provided)
        if (!shouldSkip && restaurantId != null) {
            RestaurantPromotionMapping restaurantMapping = promotionStatusMap.get(promotion.getId());
            // If restaurant mapping exists, check status - only include if ACTIVE
            if (restaurantMapping != null 
                    && (restaurantMapping.getStatus() == null || restaurantMapping.getStatus() != EntityStatus.ACTIVE)) {
                shouldSkip = true; // Skip if status is INACTIVE or null
            }
        }
        
        // 6️⃣ Apply restaurant availability filter (ONLY when requested)
        boolean isCurrentlyAvailable = true;
        if (Boolean.TRUE.equals(isAvailable) && restaurantId != null) {
            RestaurantPromotionMapping restaurantMapping = promotionStatusMap.get(promotion.getId());
            
            isCurrentlyAvailable = false;
            
            // For COMBO type promotions, check combo-based availability
            if (PromotionType.COMBO.equals(promotion.getType())) {
                // Fetch combo mapping for this promotion and menu
                Optional<PromotionMenuComboMapping> comboMapping = 
                    promotionMenuComboMappingRepository.findByPromotionIdAndMenuId(promotion.getId(), menuId);
                
                if (comboMapping.isPresent() 
                    && comboMapping.get().getMenuCategoryComboMapping() != null
                    && comboMapping.get().getMenuCategoryComboMapping().getCombo() != null) {
                    
                    Combo combo = comboMapping.get().getMenuCategoryComboMapping().getCombo();
                    
                    // Use combo-based availability check
                    OffsetDateTime promotionValidFrom = restaurantMapping != null 
                        ? restaurantMapping.getValidFrom() 
                        : mapping.getValidFrom();
                    OffsetDateTime promotionValidTo = restaurantMapping != null 
                        ? restaurantMapping.getValidTo() 
                        : mapping.getValidTo();
                    
                    isCurrentlyAvailable = isComboPromotionCurrentlyAvailable(
                        promotionValidFrom,
                        promotionValidTo,
                        combo,
                        nowUtc
                    );
                }
            } else {
                // For non-COMBO promotions, use standard availability check
                Boolean availabilityFromMap = promotionAvailabilityMap.get(promotion.getId());
                isCurrentlyAvailable = availabilityFromMap != null && availabilityFromMap;
            }
        }

        // 7️⃣ Translation fallback logic
        String promotionName = "";
        String promotionHeading = "";
        String promotionDescription = "";
        String selectedLanguageCode = locale;

        List<PromotionTranslation> translations =
                promotionTranslationRepository.findAllByPromotionId(promotion.getId());

        ResolvedPromotionTexts texts = resolvePromotionTexts(translations, locale);
        promotionName = texts.name;
        promotionHeading = texts.heading;
        promotionDescription = texts.description;
        selectedLanguageCode = locale;

        // 8️⃣ Search filter - combine all skip conditions into one continue
        if (shouldSkip || !isCurrentlyAvailable ||
            (search != null && !search.isEmpty() && !promotionMatchesSearch(search, promotionName, promotionHeading, promotionDescription))) {
            continue;
        }

        // 9️⃣ Translation response DTO (selected language only)
        List<PromotionTranslationResponse> filteredTranslations = List.of(
                PromotionTranslationResponse.builder()
                        .languageCode(selectedLanguageCode)
                        .name(promotionName)
                        .heading(promotionHeading)
                        .description(promotionDescription)
                        .build()
        );

        // 🔟 Discount info
        boolean discountApplied = promotion.getType() == PromotionType.DISCOUNT;
        String discountType = null;
        String discountAppliedTo = null;

        if (discountApplied && promotion.getDiscount() != null) {
            Discount discount = promotion.getDiscount();
            discountType = discount.getDiscountType() != null
                    ? discount.getDiscountType().name()
                    : null;
            discountAppliedTo = discount.getAppliedTo() != null
                    ? discount.getAppliedTo().name()
                    : null;
        }

        // 🔟 Promotion image (AWS presigned URL)
        String promotionImage = promotion.getImageUrl() != null
                ? awsService.getPreSignedUrl(promotion.getImageUrl())
                : null;

        // 1️⃣1️⃣ Build response DTO
        MenuPromotionResponseDto listItem = MenuPromotionResponseDto.builder()
                .translations(filteredTranslations)
                .validFrom(mapping.getValidFrom())   // menu-level validity (unchanged)
                .validTo(mapping.getValidTo())
                .imageUrl(promotionImage)
                .discountApplied(discountApplied)
                .discountType(discountType)
                .discountAppliedTo(discountAppliedTo)
                .promotionId(promotion.getId())
                .type(promotion.getType())
                .build();

        if (promotion.getDiscount() != null) {
            listItem.setDiscountId(promotion.getDiscount().getId());
            listItem.setDiscountValue(promotion.getDiscount().getValue());
        }
        
        // Set combo ID for COMBO type promotions
        if (PromotionType.COMBO.equals(promotion.getType())) {
            // Optimized query: fetch combo mapping for this promotion and menu
            Optional<PromotionMenuComboMapping> comboMapping = 
                promotionMenuComboMappingRepository.findByPromotionIdAndMenuId(promotion.getId(), menuId);
            
            if (comboMapping.isPresent() 
                && comboMapping.get().getMenuCategoryComboMapping() != null
                && comboMapping.get().getMenuCategoryComboMapping().getCombo() != null) {
                listItem.setComboId(comboMapping.get().getMenuCategoryComboMapping().getCombo().getComboId());
            }
        }

        result.add(listItem);
    }

    // 1️⃣2️⃣ Sorting
    LocaleSortUtil.sortName(result, sortBy, direction);

    // 1️⃣3️⃣ Pagination
    int pageNumber = (page != null ? page : 1) - 1;
    int pageSize = size != null ? size : result.size();

    int fromIndex = Math.max(0, pageNumber * pageSize);
    int toIndex = Math.min(result.size(), fromIndex + pageSize);

    List<MenuPromotionResponseDto> paged =
            fromIndex < toIndex ? result.subList(fromIndex, toIndex) : new ArrayList<>();

    PaginationMetaData metaData = PaginationMetaData.builder()
            .page(pageNumber + 1)
            .size(pageSize)
            .totalPages((int) Math.ceil((double) result.size() / pageSize))
            .totalRecords((long) result.size())
            .build();

    // 1️⃣4️⃣ Final response
    MenuPromotionListResponse listResponse = MenuPromotionListResponse.builder()
            .promotions(paged)
            .count((long) paged.size())
            .total((long) result.size())
            .metaData(metaData)
            .errors(null)
            .build();

    return ResponseDto.<MenuPromotionListResponse>builder()
            .message(messageUtil.getMessage("menu.promotion.list.success", userLocale))
            .data(listResponse)
            .build();
}

    /**
     * Restores soft-deleted promotions by setting their isDeleted flag to false.
     * Only restores promotions that are currently deleted.
     *
     * @param ids List of promotion UUIDs to restore
     * @param userId ID of the user performing the restoration
     * @return ResponseDto with success message
     */
    @Override
    @Transactional
    public ResponseDto<Void> restorePromotions(List<UUID> ids, String userId) {
        Locale userLocale = LocaleContextHolder.getLocale();
        
        // Find user for updatedBy
        User user = userRepository.findById(UUID.fromString(userId))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        messageUtil.getMessage(msgUserNotFound, userLocale)));
        
        // Find all promotions by IDs
        List<Promotion> promotions = promotionRepository.findAllById(ids);
        
        if (promotions.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                    messageUtil.getMessage(msgPromotionNotFound, userLocale));
        }
        
        // Filter only deleted promotions and restore them
        List<Promotion> deletedPromotions = promotions.stream()
                .filter(p -> Boolean.TRUE.equals(p.getIsDeleted()))
                .collect(Collectors.toList());
        
        if (deletedPromotions.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    messageUtil.getMessage("promotion.restore.error.not.deleted", userLocale));
        }
        
        // Restore all deleted promotions
        for (Promotion promotion : deletedPromotions) {
            promotion.setIsDeleted(false);
            promotion.setUpdatedAt(OffsetDateTime.now(ZoneOffset.UTC));
            promotion.setUpdatedBy(user);
        }
        
        promotionRepository.saveAll(deletedPromotions);
        
        return ResponseDto.<Void>builder()
            .message(messageUtil.getMessage("promotion.restore.success", userLocale))
            .build();
    }


    /**
     * Updates the validity dates of a promotion assignment to a menu.
     * For COMBO type promotions, handles combo mapping updates if the combo ID has changed.
     * Validates UTC datetime fields and updates restaurant promotion mappings accordingly.
     *
     * @param request Menu promotion mapping request containing menu ID, promotion ID, and updated validity dates
     * @param locale Locale for error messages and localization
     * @return ResponseDto containing the updated menu promotion response
     */
    @Override
    @Transactional
    @CacheEvict(value = "restaurants", allEntries = true)
    public ResponseDto<MenuPromotionResponseDto> updateMenuPromotionAssignment(MenuPromotionMappingRequest request, String locale) {
        Locale userLocale = Locale.forLanguageTag(locale);
        // Null/blank checks for required fields
        if (request.getMenuId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, messageUtil.getMessage("menu.id.required", userLocale));
        }
        if (request.getPromotionId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, messageUtil.getMessage("menu.promotion.mapping.promotionid.required", userLocale));
        }
        if (request.getValidFrom() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, messageUtil.getMessage("menu.promotion.mapping.validfrom.required", userLocale));
        }
        if (request.getValidTo() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, messageUtil.getMessage("menu.promotion.mapping.validto.required", userLocale));
        }
        // Validate menu
        UUID menuId = request.getMenuId();
        Menu menu = menuRepository.findById(menuId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, messageUtil.getMessage(msgMenuNotFound, userLocale)));
        // Removed publish status validation for menu
        if (Boolean.TRUE.equals(menu.getIsDeleted())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, messageUtil.getMessage("menu.inactive.or.deleted", userLocale));
        }
        // Validate promotion
        UUID promotionId = request.getPromotionId();
        Promotion promotion = promotionRepository.findById(promotionId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, messageUtil.getMessage(msgPromotionNotFound, userLocale)));
        if (Boolean.TRUE.equals(promotion.getIsDeleted()) || promotion.getStatus() != EntityStatus.ACTIVE) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, messageUtil.getMessage("promotion.inactive.or.deleted", userLocale));
        }
        // Validate mapping exists
        MenuPromotionId mappingId = new MenuPromotionId(menuId, promotionId);
        MenuPromotionMapping mapping = menuPromotionMappingRepository.findById(mappingId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, messageUtil.getMessage("menu.promotion.mapping.not.found", userLocale)));
        
        // Handle COMBO type promotion requirements
        if (PromotionType.COMBO.equals(promotion.getType())) {
            if (request.getComboId() == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, 
                    messageUtil.getMessage("promotion.combo.id.required", userLocale));
            }
            
            // Find MenuCategoryComboMapping for the given combo and menu
            // This validates that the combo is assigned to a category in the specified menu
            List<MenuCategoryComboMapping> menuCategoryComboMappings = 
                menuCategoryComboMappingRepository.findByCombo_ComboIdAndMenuCategoryMapping_Menu_Id(
                    request.getComboId(), menuId);
            
            if (menuCategoryComboMappings.isEmpty()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, 
                    messageUtil.getMessage("promotion.combo.mapping.not.found", userLocale));
            }
            
            // If multiple mappings exist (combo in multiple categories in same menu), use the first one
            // Log a warning if multiple exist
            if (menuCategoryComboMappings.size() > 1) {
                log.warn("Multiple menu category combo mappings found for combo {} in menu {}. Using the first one.", 
                    request.getComboId(), menuId);
            }
            
            MenuCategoryComboMapping menuCategoryComboMapping = menuCategoryComboMappings.get(0);
            
            // Check if there's an existing promotion-menu-combo mapping for this promotion
            List<PromotionMenuComboMapping> existingComboMappings = 
                promotionMenuComboMappingRepository.findByPromotion_Id(promotionId);
            
            // Find the existing mapping for this specific menu (if any)
            PromotionMenuComboMapping existingComboMapping = null;
            if (!existingComboMappings.isEmpty()) {
                existingComboMapping = existingComboMappings.stream()
                    .filter(m -> m.getMenuCategoryComboMapping() != null 
                        && m.getMenuCategoryComboMapping().getMenuCategoryMapping() != null
                        && m.getMenuCategoryComboMapping().getMenuCategoryMapping().getMenu() != null
                        && m.getMenuCategoryComboMapping().getMenuCategoryMapping().getMenu().getId().equals(menuId))
                    .findFirst()
                    .orElse(null);
            }
            
            // Check if the combo has changed
            boolean comboChanged = false;
            if (existingComboMapping != null) {
                UUID existingComboId = existingComboMapping.getMenuCategoryComboMapping().getCombo().getComboId();
                if (!existingComboId.equals(request.getComboId())) {
                    comboChanged = true;
                    log.info("Combo changed for promotion {} in menu {}: {} -> {}", 
                        promotionId, menuId, existingComboId, request.getComboId());
                }
            } else {
                // No existing mapping, need to create one
                comboChanged = true;
            }
            
            // If combo changed, delete old mapping and create new one
            if (comboChanged) {
                if (existingComboMapping != null) {
                    promotionMenuComboMappingRepository.delete(existingComboMapping);
                    log.info("Deleted old promotion-menu-combo mapping for promotion {} in menu {}", 
                        promotionId, menuId);
                }
                
                // Create new promotion-menu-combo mapping
                PromotionMenuComboMapping promotionMenuComboMapping = PromotionMenuComboMapping.builder()
                    .promotion(promotion)
                    .menuCategoryComboMapping(menuCategoryComboMapping)
                    .build();
                promotionMenuComboMappingRepository.save(promotionMenuComboMapping);
                log.info("Created/updated promotion-menu-combo mapping for promotion {} and combo {} in menu {}", 
                    promotionId, request.getComboId(), menuId);
            }
        } else {
            // If comboId is provided but promotion type is not COMBO, throw error
            if (request.getComboId() != null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, 
                    messageUtil.getMessage("promotion.combo.id.not.allowed", userLocale));
            }
            
            // If promotion type is not COMBO, delete any existing combo mappings for this promotion and menu
            List<PromotionMenuComboMapping> existingComboMappings = 
                promotionMenuComboMappingRepository.findByPromotion_Id(promotionId);
            
            if (!existingComboMappings.isEmpty()) {
                PromotionMenuComboMapping existingComboMapping = existingComboMappings.stream()
                    .filter(m -> m.getMenuCategoryComboMapping() != null 
                        && m.getMenuCategoryComboMapping().getMenuCategoryMapping() != null
                        && m.getMenuCategoryComboMapping().getMenuCategoryMapping().getMenu() != null
                        && m.getMenuCategoryComboMapping().getMenuCategoryMapping().getMenu().getId().equals(menuId))
                    .findFirst()
                    .orElse(null);
                
                if (existingComboMapping != null) {
                    promotionMenuComboMappingRepository.delete(existingComboMapping);
                    log.info("Deleted promotion-menu-combo mapping for promotion {} in menu {} as promotion type is not COMBO", 
                        promotionId, menuId);
                }
            }
        }
        
        // Validate UTC datetime fields
        validateUtcDateTimeFields(request, userLocale, mapping.getValidFrom(), true);
        
        // Get validated values (already in UTC)
        OffsetDateTime validFrom = request.getValidFrom();
        OffsetDateTime validTo = request.getValidTo();
        // Update mapping
        mapping.setValidFrom(validFrom);
        mapping.setValidTo(validTo);
        menuPromotionMappingRepository.save(mapping);

        // Build response DTO (reuse logic from assign)
        List<PromotionTranslation> translations = promotionTranslationRepository.findAllByPromotionId(promotion.getId());
        List<PromotionTranslationResponse> filteredTranslations = new ArrayList<>();
        ResolvedPromotionTexts texts = resolvePromotionTexts(translations, locale);
        filteredTranslations.add(PromotionTranslationResponse.builder()
                .languageCode(locale)
                .name(texts.name)
                .heading(texts.heading)
                .description(texts.description)
                .build());
        boolean discountApplied = promotion.getType() == PromotionType.DISCOUNT;
        String discountType = null;
        String discountAppliedTo = null;
        if (discountApplied && promotion.getDiscount() != null) {
            Discount discount = promotion.getDiscount();
            discountType = discount.getDiscountType() != null ? discount.getDiscountType().name() : null;
            discountAppliedTo = discount.getAppliedTo() != null ? discount.getAppliedTo().name() : null;
        }
        String promotionImage = promotion.getImageUrl() != null ? awsService.getPreSignedUrl(promotion.getImageUrl()) : null;
        MenuPromotionResponseDto responseDto = MenuPromotionResponseDto.builder()
            .translations(filteredTranslations)
            .validFrom(validFrom)
            .validTo(validTo)
            .imageUrl(promotionImage)
            .discountApplied(discountApplied)
            .discountType(discountType)
            .discountAppliedTo(discountAppliedTo)
            .promotionId(promotion.getId())
            .type(promotion.getType())
            .build();
        if (promotion.getDiscount() != null) {
            responseDto.setDiscountId(promotion.getDiscount().getId());
            responseDto.setDiscountValue(promotion.getDiscount().getValue());
        }
        return ResponseDto.<MenuPromotionResponseDto>builder()
            .message(messageUtil.getMessage("menu.promotion.update.mapping.success", userLocale))
            .data(responseDto)
            .build();
    }

    /**
     * Helper method to build a PromotionResponse DTO from a Promotion entity.
     * Fetches translations, generates pre-signed image URL, and calculates assigned menu count.
     *
     * @param promotion Promotion entity to convert
     * @return PromotionResponse DTO with all promotion details
     */
    // Helper method to build PromotionResponse (unchanged for create operation)
    private PromotionResponse buildPromotionResponse(Promotion promotion) {
        // Fetch translations from repository
        List<PromotionTranslation> translations = promotionTranslationRepository.findAllByPromotionId(promotion.getId());
        
        // Build translation responses
        List<PromotionTranslationResponse> translationResponses = new ArrayList<>();
        for (PromotionTranslation translation : translations) {
            translationResponses.add(PromotionTranslationResponse.builder()
                    .languageCode(translation.getLanguageCode())
                    .name(translation.getName())
                    .heading(translation.getHeading())
                    .description(translation.getDescription())
                    .build());
        }

        // Generate presigned URL for image
        String signedImageUrl = null;
        if (promotion.getImageUrl() != null && !promotion.getImageUrl().isEmpty()) {
            signedImageUrl = awsService.getPreSignedUrl(promotion.getImageUrl());
        }

        // Calculate assigned menu count for this specific promotion
        Long assignedMenuCount = menuPromotionMappingRepository.countByPromotionId(promotion.getId());

        return PromotionResponse.builder()
                .id(promotion.getId())
                .type(promotion.getType())
                .imageUrl(signedImageUrl)
                .translations(translationResponses)
                .discountId(promotion.getDiscount() != null ? promotion.getDiscount().getId() : null)
                .status(promotion.getStatus())
                .isDeleted(promotion.getIsDeleted())
                .assignedMenuCount(assignedMenuCount)
                .createdAt(promotion.getCreatedAt() != null ? promotion.getCreatedAt().toLocalDateTime() : null)
                .createdBy(formatUserName(promotion.getCreatedBy()))
                .updatedAt(promotion.getUpdatedAt() != null ? promotion.getUpdatedAt().toLocalDateTime() : null)
                .updatedBy(formatUserName(promotion.getUpdatedBy()))
                .build();
    }

    private static final class ResolvedPromotionTexts {
        final String name;
        final String heading;
        final String description;

        ResolvedPromotionTexts(String name, String heading, String description) {
            this.name = name != null ? name : "";
            this.heading = heading != null ? heading : "";
            this.description = description != null ? description : "";
        }
    }

    private static boolean isBlankDisplayText(String value) {
        if (value == null) {
            return true;
        }
        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            return true;
        }
        return "N/A".equalsIgnoreCase(trimmed) || "NA".equalsIgnoreCase(trimmed);
    }

    private static boolean promotionMatchesSearch(String search, String name, String heading, String description) {
        String term = search.trim().toLowerCase();
        return (name != null && name.toLowerCase().contains(term))
                || (heading != null && heading.toLowerCase().contains(term))
                || (description != null && description.toLowerCase().contains(term));
    }

    /**
     * Resolves name, heading, and description independently for list/banner use-cases.
     * Each field prefers the requested locale, then configured language order, skipping blank / N/A placeholder text.
     */
    private ResolvedPromotionTexts resolvePromotionTexts(List<PromotionTranslation> translations, String locale) {
        List<PromotionTranslation> safe = translations == null ? Collections.emptyList() : translations;
        if (safe.isEmpty()) {
            return new ResolvedPromotionTexts("", "", "");
        }
        String name = resolvePromotionField(safe, locale, PromotionTranslation::getName);
        String heading = resolvePromotionField(safe, locale, PromotionTranslation::getHeading);
        String description = resolvePromotionField(safe, locale, PromotionTranslation::getDescription);
        return new ResolvedPromotionTexts(name, heading, description);
    }

    private Optional<PromotionTranslation> pickTranslationForPromotionField(
            List<PromotionTranslation> safe,
            String locale,
            Function<PromotionTranslation, String> fieldExtractor) {
        return TranslationUtils.pickPreferredOrFromListNonBlank(
                safe,
                locale,
                localizationProperties.getLanguages(),
                PromotionTranslation::getLanguageCode,
                t -> {
                    String v = fieldExtractor.apply(t);
                    return isBlankDisplayText(v) ? null : v.trim();
                });
    }

    private String resolvePromotionField(
            List<PromotionTranslation> safe,
            String locale,
            Function<PromotionTranslation, String> fieldExtractor) {
        Optional<PromotionTranslation> picked = pickTranslationForPromotionField(safe, locale, fieldExtractor);
        if (picked.isPresent()) {
            String v = fieldExtractor.apply(picked.get());
            if (!isBlankDisplayText(v)) {
                return v.trim();
            }
        }
        return safe.stream()
                .filter(t -> !isBlankDisplayText(fieldExtractor.apply(t)))
                .min(Comparator.comparing(
                        t -> Optional.ofNullable(t.getLanguageCode()).orElse(""),
                        String.CASE_INSENSITIVE_ORDER))
                .map(fieldExtractor)
                .map(String::trim)
                .orElse("");
    }

    /**
     * Validates that datetime fields in the menu promotion mapping request are in UTC timezone.
     * Ensures validFrom is not in the past and validTo is after validFrom.
     *
     * @param request Menu promotion mapping request to validate
     * @param userLocale Locale for error messages
     */
    private void validateUtcDateTimeFields(MenuPromotionMappingRequest request, Locale userLocale, OffsetDateTime existingValidFromUtc, boolean isUpdate) {
        // Validate validFrom field
        if (request.getValidFrom() != null) {
            // Ensure the datetime is in UTC
            if (!request.getValidFrom().getOffset().equals(ZoneOffset.UTC)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    messageUtil.getMessage("promotion.error.valid.from.not.utc", userLocale));
            }
            
            boolean validFromChanged = true;
            if (isUpdate && existingValidFromUtc != null) {
                validFromChanged = !request.getValidFrom().toInstant().equals(existingValidFromUtc.toInstant());
            }

            // On create: validFrom must not be in the past.
            // On update: only enforce "not in the past" if validFrom is being changed in the request.
            if (!isUpdate || validFromChanged) {
                OffsetDateTime nowUtc = OffsetDateTime.now(ZoneOffset.UTC);
                if (request.getValidFrom().isBefore(nowUtc)) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        messageUtil.getMessage("combo.error.valid.from.past", userLocale));
                }
            }
        }
        
        // Validate validTo field
        if (request.getValidTo() != null 
                && !request.getValidTo().getOffset().equals(ZoneOffset.UTC)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                messageUtil.getMessage("promotion.error.valid.to.not.utc", userLocale));
        }
        
        // Validate date range
        if (request.getValidFrom() != null && request.getValidTo() != null 
                && request.getValidFrom().isAfter(request.getValidTo())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                messageUtil.getMessage("promotion.error.invalid.date.range", userLocale));
        }
    }
    
   
    private boolean isPromotionCurrentlyAvailable(OffsetDateTime validFrom, OffsetDateTime validTo, OffsetDateTime currentTime) {
        if (validFrom == null || validTo == null) {
            return false; // If dates are null, consider not available
        }
        
        return !currentTime.isBefore(validFrom) && !currentTime.isAfter(validTo);
    }

    /**
     * Check if a COMBO type promotion is currently available based on combo's validity period.
     * For COMBO promotions, availability is determined by the intersection of:
     * 1. Promotion validity period (from RestaurantPromotionMapping)
     * 2. Combo validity period (from Combo entity)
     * 3. Combo's daily time range (startTime to endTime)
     * 4. Combo's days of week (daysOfWeek list)
     * 
     * @param promotionValidFrom Promotion's validFrom date
     * @param promotionValidTo Promotion's validTo date
     * @param combo The combo entity associated with the promotion
     * @param currentTime Current UTC time
     * @return true if promotion is available based on combo's rules, false otherwise
     */
    private boolean isComboPromotionCurrentlyAvailable(
            OffsetDateTime promotionValidFrom,
            OffsetDateTime promotionValidTo,
            Combo combo,
            OffsetDateTime currentTime) {
        
        if (combo == null) {
            return false; // No combo means not available
        }
        
        // 1. Check promotion validity period
        if (promotionValidFrom == null || promotionValidTo == null) {
            return false; // Promotion dates must be set
        }
        if (currentTime.isBefore(promotionValidFrom) || currentTime.isAfter(promotionValidTo)) {
            return false; // Outside promotion period
        }
        
        // 2. Check combo validity period (date intersection)
        // Current time must be within both promotion and combo date ranges
        OffsetDateTime comboValidFrom = combo.getValidFrom();
        OffsetDateTime comboValidTo = combo.getValidTo();
        
        if (comboValidFrom != null && currentTime.isBefore(comboValidFrom)) {
            return false; // Before combo start date
        }
        if (comboValidTo != null && currentTime.isAfter(comboValidTo)) {
            return false; // After combo end date
        }
        
        // If we reach here, currentTime is within both promotion and combo date ranges
        // (i.e., within the intersection)
        
        // 3. Check combo's daily time range (startTime to endTime)
        OffsetTime comboStartTime = combo.getStartTime();
        OffsetTime comboEndTime = combo.getEndTime();
        
        if (comboStartTime != null && comboEndTime != null) {
            OffsetTime currentTimeOnly = currentTime.toOffsetTime();
            
            // Handle normal time range (e.g., 12:00 to 21:00)
            if (comboStartTime.isBefore(comboEndTime) || comboStartTime.equals(comboEndTime)) {
                if (currentTimeOnly.isBefore(comboStartTime) || currentTimeOnly.isAfter(comboEndTime)) {
                    return false; // Outside daily time range
                }
            } else {
                // Handle overnight time range (e.g., 23:00 to 02:00)
                if (currentTimeOnly.isBefore(comboStartTime) && currentTimeOnly.isAfter(comboEndTime)) {
                    return false; // Outside overnight time range
                }
            }
        }
        
        // 4. Check combo's days of week
        List<DayOfWeek> comboDaysOfWeek = combo.getDaysOfWeek();
        if (comboDaysOfWeek != null && !comboDaysOfWeek.isEmpty()) {
            java.time.DayOfWeek javaDayOfWeek = currentTime.getDayOfWeek();
            DayOfWeek currentDay = convertToDayOfWeek(javaDayOfWeek);
            
            if (!comboDaysOfWeek.contains(currentDay)) {
                return false; // Not available on current day
            }
        }
        
        return true; // All checks passed
    }
    
    /**
     * Helper method to convert Java DayOfWeek to custom DayOfWeek enum
     */
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
     * Creates restaurant promotion mappings for all restaurants assigned to a menu.
     * Called when a promotion is assigned to a menu to propagate the assignment to all associated restaurants.
     *
     * @param menuId UUID of the menu
     * @param promotion Promotion entity to assign to restaurants
     * @param validFrom Start date/time for the promotion validity (in UTC)
     * @param validTo End date/time for the promotion validity (in UTC)
     * @param userLocale Locale for error messages
     */
    private void createRestaurantPromotionMappings(
            UUID menuId,
            Promotion promotion,
            OffsetDateTime validFrom,
            OffsetDateTime validTo,
            Locale userLocale) {
        
        // Get restaurants from menu mapping
        List<RestaurantMenuMapping> restaurantMenuMappings = restaurantMenuMappingRepository.findById_MenuId(menuId);
        if (restaurantMenuMappings == null || restaurantMenuMappings.isEmpty()) {
            // Menu has no restaurants assigned, skip restaurant mapping
            log.info("Menu {} has no restaurants assigned, skipping restaurant promotion mapping", menuId);
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
                messageUtil.getMessage(msgRestaurantNotFound, userLocale));
        }
        
        // Create restaurant promotion mappings
        List<RestaurantPromotionMapping> restaurantPromotionMappings = new ArrayList<>();
        for (Restaurant restaurant : restaurants) {
            RestaurantPromotionId id = new RestaurantPromotionId();
            id.setRestaurantId(restaurant.getId());
            id.setPromotionId(promotion.getId());
            
            RestaurantPromotionMapping mapping = RestaurantPromotionMapping.builder()
                .id(id)
                .restaurant(restaurant)
                .promotion(promotion)
                .validFrom(validFrom)
                .validTo(validTo)
                .status(EntityStatus.ACTIVE)
                .build();
            
            restaurantPromotionMappings.add(mapping);
        }
        
        // Save all mappings
        restaurantPromotionMappingRepository.saveAll(restaurantPromotionMappings);
        log.info("Created {} restaurant promotion mappings for promotion {} and menu {}", 
            restaurantPromotionMappings.size(), promotion.getId(), menuId);
    }

    /**
     * Deletes restaurant-promotion mappings for all restaurants assigned to a menu
     * when a promotion is unassigned from the menu
     */
    private void deleteRestaurantPromotionMappingsForMenu(UUID menuId, UUID promotionId) {
        // Get all restaurants assigned to this menu
        List<RestaurantMenuMapping> restaurantMenuMappings = restaurantMenuMappingRepository.findById_MenuId(menuId);
        
        if (restaurantMenuMappings == null || restaurantMenuMappings.isEmpty()) {
            log.info("Menu {} has no restaurants assigned, skipping restaurant promotion mapping deletion", menuId);
            return;
        }
        
        // Collect all restaurant-promotion mappings to delete
        List<RestaurantPromotionMapping> restaurantPromotionMappingsToDelete = new ArrayList<>();
        
        for (RestaurantMenuMapping restaurantMenuMapping : restaurantMenuMappings) {
            UUID restaurantId = restaurantMenuMapping.getRestaurant().getId();
            RestaurantPromotionId restaurantPromotionId = new RestaurantPromotionId();
            restaurantPromotionId.setRestaurantId(restaurantId);
            restaurantPromotionId.setPromotionId(promotionId);
            
            Optional<RestaurantPromotionMapping> restaurantPromotionMapping = 
                restaurantPromotionMappingRepository.findById(restaurantPromotionId);
            
            if (restaurantPromotionMapping.isPresent()) {
                restaurantPromotionMappingsToDelete.add(restaurantPromotionMapping.get());
            }
        }
        
        // Delete all collected mappings
        if (!restaurantPromotionMappingsToDelete.isEmpty()) {
            restaurantPromotionMappingRepository.deleteAll(restaurantPromotionMappingsToDelete);
            log.info("Deleted {} restaurant-promotion mappings for promotion {} and menu {}", 
                restaurantPromotionMappingsToDelete.size(), promotionId, menuId);
        } else {
            log.info("No restaurant-promotion mappings found to delete for promotion {} and menu {}", 
                promotionId, menuId);
        }
    }

    /**
     * Retrieves a paginated and filterable list of promotions assigned to a specific restaurant.
     * Supports filtering by status and search term.
     * Applies locale-specific translations and sorting.
     *
     * @param restaurantId UUID of the restaurant
     * @param page Page number (1-based)
     * @param size Number of records per page
     * @param search Search term to filter by promotion name
     * @param status Filter by promotion status (ACTIVE, INACTIVE, etc.)
     * @param sortBy Field to sort by
     * @param direction Sort direction (ASC or DESC)
     * @param locale Locale for translations
     * @return ResponseDto containing a paginated list of restaurant promotions
     */
    @Override
    @Transactional(readOnly = true)
    public ResponseDto<MenuPromotionListResponse> getRestaurantAssignedPromotions(
            UUID restaurantId,
            Integer page,
            Integer size,
            String search,
            String status,
            String sortBy,
            Sort.Direction direction,
            String locale) {

        Locale userLocale = Locale.forLanguageTag(locale);

        // Validate locale
        if (!localizationProperties.getLanguages().contains(locale)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    messageUtil.getMessage(msgPromotionErrorInvalidLanguage, userLocale));
        }

        // Validate and process status filter
        EntityStatus statusFilter = null;
        if (status != null && !status.trim().isEmpty()) {
            try {
                statusFilter = EntityStatus.valueOf(status.toUpperCase());
            } catch (IllegalArgumentException e) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        messageUtil.getMessage(msgPromotionErrorInvalidStatus, userLocale));
            }
        }

        // Validate restaurant exists
        restaurantRepository.findById(restaurantId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        messageUtil.getMessage(msgRestaurantNotFound, userLocale)));

        List<RestaurantPromotionMapping> restaurantPromotionMappings =
                restaurantPromotionMappingRepository.findById_RestaurantId(restaurantId);
        
        // Filter by status if provided
        final EntityStatus finalStatusFilter = statusFilter;
        if (finalStatusFilter != null) {
            restaurantPromotionMappings = restaurantPromotionMappings.stream()
                    .filter(mapping -> finalStatusFilter.equals(mapping.getStatus()))
                    .collect(Collectors.toList());
        }

        if (restaurantPromotionMappings == null || restaurantPromotionMappings.isEmpty()) {
            PaginationMetaData metaData = PaginationMetaData.builder()
                    .page(1)
                    .size(size != null ? size : 10)
                    .totalPages(0)
                    .totalRecords(0L)
                    .build();

            MenuPromotionListResponse emptyResponse = MenuPromotionListResponse.builder()
                    .promotions(new ArrayList<>())
                    .count(0L)
                    .total(0L)
                    .metaData(metaData)
                    .errors(null)
                    .build();

            return ResponseDto.<MenuPromotionListResponse>builder()
                    .message(messageUtil.getMessage(msgPromotionListSuccess, userLocale))
                    .data(emptyResponse)
                    .build();
        }

        // Extract promotion IDs and build mapping map
        List<UUID> promotionIds = restaurantPromotionMappings.stream()
                .map(mapping -> mapping.getId().getPromotionId())
                .collect(Collectors.toList());

        Map<UUID, RestaurantPromotionMapping> mappingMap = restaurantPromotionMappings.stream()
                .collect(Collectors.toMap(
                        mapping -> mapping.getId().getPromotionId(),
                        mapping -> mapping
                ));

        List<Promotion> promotions = promotionIds.isEmpty()
                ? new ArrayList<>()
                : promotionRepository.findAllById(promotionIds);

        List<MenuPromotionResponseDto> result = new ArrayList<>();

        for (Promotion promotion : promotions) {
            // Check if promotion should be skipped
            boolean shouldSkip = promotion == null || Boolean.TRUE.equals(promotion.getIsDeleted());
            
            RestaurantPromotionMapping restaurantPromotionMapping = null;
            String promotionName = "";
            String promotionHeading = "";
            String promotionDescription = "";
            String selectedLanguageCode = locale;

            if (!shouldSkip) {
                restaurantPromotionMapping = mappingMap.get(promotion.getId());

                // Apply search on localized promotion name
                List<PromotionTranslation> translations =
                        promotionTranslationRepository.findAllByPromotionId(promotion.getId());

                if (!translations.isEmpty()) {
                    ResolvedPromotionTexts texts = resolvePromotionTexts(translations, locale);
                    promotionName = texts.name;
                    promotionHeading = texts.heading;
                    promotionDescription = texts.description;
                    selectedLanguageCode = locale;
                }

                // Check search filter
                if (search != null && !search.trim().isEmpty()) {
                    if (!promotionMatchesSearch(search, promotionName, promotionHeading, promotionDescription)) {
                        shouldSkip = true;
                    }
                }
            }
            
            if (shouldSkip) {
                continue;
            }

            List<PromotionTranslationResponse> filteredTranslations = List.of(
                    PromotionTranslationResponse.builder()
                            .languageCode(selectedLanguageCode)
                            .name(promotionName)
                            .heading(promotionHeading)
                            .description(promotionDescription)
                            .build()
            );

            boolean discountApplied = promotion.getType() == PromotionType.DISCOUNT;
            String discountType = null;
            String discountAppliedTo = null;
            if (discountApplied && promotion.getDiscount() != null) {
                Discount discount = promotion.getDiscount();
                discountType = discount.getDiscountType() != null ? discount.getDiscountType().name() : null;
                discountAppliedTo = discount.getAppliedTo() != null ? discount.getAppliedTo().name() : null;
            }

            String promotionImage = promotion.getImageUrl() != null
                    ? awsService.getPreSignedUrl(promotion.getImageUrl())
                    : null;

            MenuPromotionResponseDto listItem = MenuPromotionResponseDto.builder()
                    .translations(filteredTranslations)
                    .validFrom(restaurantPromotionMapping != null ? restaurantPromotionMapping.getValidFrom() : null)
                    .validTo(restaurantPromotionMapping != null ? restaurantPromotionMapping.getValidTo() : null)
                    .imageUrl(promotionImage)
                    .discountApplied(discountApplied)
                    .discountType(discountType)
                    .discountAppliedTo(discountAppliedTo)
                    .promotionId(promotion.getId())
                    .status(restaurantPromotionMapping != null ? restaurantPromotionMapping.getStatus() : null)
                    .type(promotion.getType())
                    .build();

            // For COMBO type promotions, also include the comboId assigned to this promotion
            if (promotion.getType() == PromotionType.COMBO) {
                List<PromotionMenuComboMapping> comboMappings =
                        promotionMenuComboMappingRepository.findByPromotion_Id(promotion.getId());
                if (comboMappings != null && !comboMappings.isEmpty()) {
                    PromotionMenuComboMapping comboMapping = comboMappings.get(0);
                    if (comboMapping.getMenuCategoryComboMapping() != null
                            && comboMapping.getMenuCategoryComboMapping().getCombo() != null) {
                        listItem.setComboId(comboMapping.getMenuCategoryComboMapping().getCombo().getComboId());
                    }
                }
            }

            if (promotion.getDiscount() != null) {
                listItem.setDiscountId(promotion.getDiscount().getId());
                listItem.setDiscountValue(promotion.getDiscount().getValue());
            }

            result.add(listItem);
        }

        // Sort by localized name
        LocaleSortUtil.sortName(result, sortBy, direction);

        // Pagination
        int pageNumber = (page != null ? page : 1) - 1;
        int pageSize = size != null && size > 0 ? size : result.size();

        int fromIndex = Math.max(0, pageNumber * pageSize);
        int toIndex = Math.min(result.size(), fromIndex + pageSize);

        List<MenuPromotionResponseDto> paged =
                fromIndex < toIndex ? result.subList(fromIndex, toIndex) : new ArrayList<>();

        PaginationMetaData metaData = PaginationMetaData.builder()
                .page(pageNumber + 1)
                .size(pageSize)
                .totalPages(pageSize == 0 ? 0 : (int) Math.ceil((double) result.size() / pageSize))
                .totalRecords((long) result.size())
                .build();

        MenuPromotionListResponse listResponse = MenuPromotionListResponse.builder()
                .promotions(paged)
                .count((long) paged.size())
                .total((long) result.size())
                .metaData(metaData)
                .errors(null)
                .build();

        return ResponseDto.<MenuPromotionListResponse>builder()
                .message(messageUtil.getMessage(msgPromotionListSuccess, userLocale))
                .data(listResponse)
                .build();
    }

    /**
     * Retrieves detailed information about a specific promotion assigned to a restaurant.
     * Includes all translations, discount information, validity dates, and status from the restaurant mapping.
     *
     * @param restaurantId UUID of the restaurant
     * @param promotionId UUID of the promotion
     * @param locale Locale for error messages (translations are returned for all languages)
     * @return ResponseDto containing the restaurant promotion details
     */
    @Override
    @Transactional(readOnly = true)
    public ResponseDto<MenuPromotionResponseDto> getRestaurantPromotionDetails(
            UUID restaurantId,
            UUID promotionId,
            UUID comboId,
            String locale) {

        log.info("Fetching promotion details for restaurant {} and promotion {} with language: {}",
                restaurantId, promotionId, locale);

        Locale userLocale = Locale.forLanguageTag(locale);

        // Validate locale
        if (!localizationProperties.getLanguages().contains(locale)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    messageUtil.getMessage(msgPromotionErrorInvalidLanguage, userLocale));
        }

        // Validate restaurant exists
        restaurantRepository.findById(restaurantId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        messageUtil.getMessage(msgRestaurantNotFound, userLocale)));

        // Validate promotion exists
        Promotion promotion = promotionRepository.findById(promotionId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        messageUtil.getMessage(msgPromotionNotFound, userLocale)));

        if (Boolean.TRUE.equals(promotion.getIsDeleted())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    messageUtil.getMessage(msgPromotionAlreadyDeleted, userLocale));
        }

        // Validate restaurant-promotion mapping and get validity
        RestaurantPromotionId restaurantPromotionId = new RestaurantPromotionId();
        restaurantPromotionId.setRestaurantId(restaurantId);
        restaurantPromotionId.setPromotionId(promotionId);

        RestaurantPromotionMapping restaurantPromotionMapping = restaurantPromotionMappingRepository.findById(restaurantPromotionId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        messageUtil.getMessage("promotion.not.assigned.to.restaurant", userLocale)));

        // Fetch and return all translations (no filtering by locale)
        List<PromotionTranslation> translations =
                promotionTranslationRepository.findAllByPromotionId(promotion.getId());

        List<PromotionTranslationResponse> translationResponses = translations.stream()
                .map(t -> PromotionTranslationResponse.builder()
                        .languageCode(t.getLanguageCode())
                        .name(t.getName())
                        .heading(t.getHeading())
                        .description(t.getDescription())
                        .build())
                .collect(Collectors.toList());

        boolean discountApplied = promotion.getType() == PromotionType.DISCOUNT;
        String discountType = null;
        String discountAppliedTo = null;
        if (discountApplied && promotion.getDiscount() != null) {
            Discount discount = promotion.getDiscount();
            discountType = discount.getDiscountType() != null ? discount.getDiscountType().name() : null;
            discountAppliedTo = discount.getAppliedTo() != null ? discount.getAppliedTo().name() : null;
        }

        String promotionImage = promotion.getImageUrl() != null
                ? awsService.getPreSignedUrl(promotion.getImageUrl())
                : null;

        MenuPromotionResponseDto responseDto = MenuPromotionResponseDto.builder()
                .translations(translationResponses)
                .validFrom(restaurantPromotionMapping.getValidFrom())
                .validTo(restaurantPromotionMapping.getValidTo())
                .imageUrl(promotionImage)
                .discountApplied(discountApplied)
                .discountType(discountType)
                .discountAppliedTo(discountAppliedTo)
                .promotionId(promotion.getId())
                .status(restaurantPromotionMapping.getStatus())
                .type(promotion.getType())
                .build();

        // If this is a COMBO promotion and comboId is provided, include full combo details
        if (promotion.getType() == PromotionType.COMBO && comboId != null) {
            ResponseDto<ComboDto<ComboDetailsResponse>> comboResponse =
                    comboService.getComboDetailsById(comboId, locale, restaurantId, null);
            responseDto.setComboId(comboId);
            responseDto.setComboDetails(comboResponse.getData());
        }

        if (promotion.getDiscount() != null) {
            responseDto.setDiscountId(promotion.getDiscount().getId());
            responseDto.setDiscountValue(promotion.getDiscount().getValue());
        }

        return ResponseDto.<MenuPromotionResponseDto>builder()
                .message(messageUtil.getMessage("promotion.detail.success", userLocale))
                .data(responseDto)
                .build();
    }

    /**
     * Updates the validity dates and/or status of a promotion assignment for a specific restaurant.
     * Validates date ranges and creates audit trail for status changes (activate/deactivate) or date modifications.
     *
     * @param restaurantId UUID of the restaurant
     * @param promotionId UUID of the promotion
     * @param request Request containing updated validity dates and/or status
     * @param locale Locale for error messages and localization
     * @return ResponseDto containing the updated restaurant promotion response
     */
    @Override
    @Transactional
    public ResponseDto<MenuPromotionResponseDto> updateRestaurantPromotionValidity(
            UUID restaurantId,
            UUID promotionId,
            UpdateRestaurantPromotionValidityRequest request,
            String locale) {

        Locale userLocale = Locale.forLanguageTag(locale);

        // Validate locale
        if (!localizationProperties.getLanguages().contains(locale)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    messageUtil.getMessage(msgPromotionErrorInvalidLanguage, userLocale));
        }

        // Validate restaurant exists
        restaurantRepository.findById(restaurantId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        messageUtil.getMessage(msgRestaurantNotFound, userLocale)));

        // Validate promotion exists
        Promotion promotion = promotionRepository.findById(promotionId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        messageUtil.getMessage(msgPromotionNotFound, userLocale)));

        if (Boolean.TRUE.equals(promotion.getIsDeleted())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    messageUtil.getMessage(msgPromotionAlreadyDeleted, userLocale));
        }

        // Validate restaurant-promotion mapping
        RestaurantPromotionId restaurantPromotionId = new RestaurantPromotionId();
        restaurantPromotionId.setRestaurantId(restaurantId);
        restaurantPromotionId.setPromotionId(promotionId);

        RestaurantPromotionMapping restaurantPromotionMapping = restaurantPromotionMappingRepository.findById(restaurantPromotionId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        messageUtil.getMessage("promotion.not.assigned.to.restaurant", userLocale)));

        // Basic validation of dates (no timezone enforcement here, just logical range)
        if (request.getValidFrom() != null && request.getValidTo() != null &&
                request.getValidFrom().isAfter(request.getValidTo())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    messageUtil.getMessage("promotion.error.invalid.date.range", userLocale));
        }

        // Update validity
        if (request.getValidFrom() != null) {
            restaurantPromotionMapping.setValidFrom(request.getValidFrom());
        }
        if (request.getValidTo() != null) {
            restaurantPromotionMapping.setValidTo(request.getValidTo());
        }
        
        // Track status change for audit trail
        EntityStatus oldStatus = restaurantPromotionMapping.getStatus();
        EntityStatus newStatus = request.getStatus();
        boolean statusChanged = newStatus != null && !newStatus.equals(oldStatus);
        
        // Update status if provided
        if (request.getStatus() != null) {
            restaurantPromotionMapping.setStatus(request.getStatus());
        }

        restaurantPromotionMappingRepository.save(restaurantPromotionMapping);
        
        // Create audit trail for promotion modify/activate/deactivate
        // Note: userId is not available in this method signature, so we'll use the promotion's updatedBy
        try {
            User user = promotion.getUpdatedBy(); // Use promotion's updatedBy as fallback
            Restaurant restaurant = restaurantRepository.findById(restaurantId).orElse(null);
            if (user != null && restaurant != null) {
                ActionType actionType = null;
                String notes = null;
                
                if (statusChanged) {
                    // Status change - activate or deactivate
                    if (newStatus == EntityStatus.ACTIVE) {
                        actionType = ActionType.PROMOTION_ACTIVATE;
                        notes = "Promotion activated";
                    } else {
                        actionType = ActionType.PROMOTION_DEACTIVATE;
                        notes = "Promotion deactivated";
                    }
                } else if (request.getValidFrom() != null || request.getValidTo() != null) {
                    // Date modification
                    actionType = ActionType.PROMOTION_MODIFY;
                    notes = String.format("Promotion dates modified. ValidFrom: %s, ValidTo: %s", 
                            request.getValidFrom() != null ? request.getValidFrom().toString() : "unchanged",
                            request.getValidTo() != null ? request.getValidTo().toString() : "unchanged");
                }
                
                // Only create audit trail if both actionType and notes are set
                if (actionType != null && notes != null) {
                    auditTrailService.createAuditTrail(
                            user,
                            actionType,
                            restaurant,
                            RequestStatus.NA,
                            null, // ipAddress
                            null, // userAgent
                            promotionId,
                            ENTITY_TYPE_PROMOTION,
                            notes
                    );
                }
            }
        } catch (Exception e) {
            log.error("Failed to create audit trail for promotion modify/activate/deactivate: {}", e.getMessage());
        }

        // Build response inline (do not reuse other methods)
        // Fetch all translations
        List<PromotionTranslation> translations =
                promotionTranslationRepository.findAllByPromotionId(promotion.getId());

        List<PromotionTranslationResponse> translationResponses = translations.stream()
                .map(t -> PromotionTranslationResponse.builder()
                        .languageCode(t.getLanguageCode())
                        .name(t.getName())
                        .heading(t.getHeading())
                        .description(t.getDescription())
                        .build())
                .collect(Collectors.toList());

        boolean discountApplied = promotion.getType() == PromotionType.DISCOUNT;
        String discountType = null;
        String discountAppliedTo = null;
        if (discountApplied && promotion.getDiscount() != null) {
            Discount discount = promotion.getDiscount();
            discountType = discount.getDiscountType() != null ? discount.getDiscountType().name() : null;
            discountAppliedTo = discount.getAppliedTo() != null ? discount.getAppliedTo().name() : null;
        }

        String promotionImage = promotion.getImageUrl() != null
                ? awsService.getPreSignedUrl(promotion.getImageUrl())
                : null;

        MenuPromotionResponseDto responseDto = MenuPromotionResponseDto.builder()
                .translations(translationResponses)
                .validFrom(restaurantPromotionMapping.getValidFrom())
                .validTo(restaurantPromotionMapping.getValidTo())
                .imageUrl(promotionImage)
                .discountApplied(discountApplied)
                .discountType(discountType)
                .discountAppliedTo(discountAppliedTo)
                .promotionId(promotion.getId())
                .type(promotion.getType())
                .build();

        if (promotion.getDiscount() != null) {
            responseDto.setDiscountId(promotion.getDiscount().getId());
            responseDto.setDiscountValue(promotion.getDiscount().getValue());
        }

        return ResponseDto.<MenuPromotionResponseDto>builder()
                .message(messageUtil.getMessage("promotion.validity.updated.success", userLocale))
                .data(responseDto)
                .build();
    }

    /**
     * Helper method to find user for audit trail with error handling.
     * Returns null if userId is invalid or user not found.
     */
    private User findUserForAuditTrail(String userId) {
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
     * Helper method to format user name from User entity.
     * Returns null if user is null, otherwise returns formatted name.
     */
    private String formatUserName(User user) {
        if (user == null) {
            return null;
        }
        String firstName = user.getFirstName() != null ? user.getFirstName() : "";
        String lastName = user.getLastName() != null ? user.getLastName() : "";
        return (firstName + " " + lastName).trim();
    }
}