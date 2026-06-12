package com.gulfnet.restaurantmanagement.service.impl;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.data.domain.Sort.Direction;
import com.gulfnet.shared_library.exception.BadRequestException;
import lombok.extern.slf4j.Slf4j;
import java.text.Collator;
import org.springframework.dao.DataIntegrityViolationException;

import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.*;
import java.util.Objects;
import java.util.stream.Collectors;

import com.gulfnet.shared_library.entity.*;
import com.gulfnet.shared_library.repository.*;
import com.gulfnet.shared_library.model.response.dto.*;
import com.gulfnet.shared_library.model.response.dto.RestaurantItemsAndMenusResponse;
import com.gulfnet.shared_library.model.response.dto.ItemModifierItemListResponse;
import com.gulfnet.shared_library.model.request.AssignModifierGroupsRequest;
import com.gulfnet.shared_library.config.AWSService;
import com.gulfnet.shared_library.model.request.ItemRequest;
import com.gulfnet.shared_library.enums.EntityStatus;
import com.gulfnet.shared_library.enums.MenuStatus;
import com.gulfnet.shared_library.enums.RestaurantMenuMappingStatus;
import com.gulfnet.shared_library.enums.ItemOrderType;
import com.gulfnet.shared_library.enums.AlcoholType;
import com.gulfnet.restaurantmanagement.service.ItemService;
import com.gulfnet.restaurantmanagement.service.AuditTrailService;
import com.gulfnet.restaurantmanagement.service.OrderNotificationService;
import com.gulfnet.restaurantmanagement.util.MessageUtil;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import com.gulfnet.shared_library.model.request.StatusEventMessage;
import java.time.format.DateTimeFormatter;
import com.gulfnet.shared_library.enums.ActionType;
import com.gulfnet.shared_library.util.TranslationUtils;
import com.gulfnet.shared_library.util.LocaleSortUtil;
import com.gulfnet.restaurantmanagement.config.LocalizationProperties;
import com.gulfnet.restaurantmanagement.config.RestaurantChainConfigProperties;
import com.gulfnet.shared_library.util.ImageThumbnailUtil;
import com.gulfnet.shared_library.util.CurrencyFormatter;

import org.springframework.context.i18n.LocaleContextHolder;
import com.gulfnet.shared_library.model.response.dto.ItemModifierItemListResponseEnhanced;
import com.gulfnet.shared_library.model.response.dto.ItemResponseWithModifierEnhanced;
import com.gulfnet.shared_library.entity.RestaurantItemAvailability;
import com.gulfnet.shared_library.repository.RestaurantItemAvailabilityRepository;
import com.gulfnet.shared_library.repository.CategoryItemMappingRepository;
import com.gulfnet.shared_library.enums.AppliedTo;
import java.math.BigDecimal;
import java.util.Collections;
import java.util.Optional;
import com.gulfnet.shared_library.entity.MenuCategoryMapping;
import com.gulfnet.shared_library.entity.CategoryItemMapping;
import com.gulfnet.shared_library.entity.CategoryDiscountMapping;
import com.gulfnet.shared_library.entity.ItemDiscountMapping;
import com.gulfnet.shared_library.entity.DiscountBxgyItem;
import com.gulfnet.shared_library.entity.Discount;
import com.gulfnet.shared_library.enums.DiscountType;
import com.gulfnet.shared_library.repository.MenuCategoryMappingRepository;
import com.gulfnet.shared_library.repository.CategoryDiscountMappingRepository;
import com.gulfnet.shared_library.repository.ItemDiscountMappingRepository;
import com.gulfnet.shared_library.repository.DiscountBxgyItemRepository;
import java.util.stream.Collectors;
import com.gulfnet.shared_library.entity.MenuDiscountMapping;
import com.gulfnet.shared_library.entity.MenuDiscountId;
import com.gulfnet.shared_library.entity.RestaurantDiscountMapping;
import com.gulfnet.shared_library.entity.RestaurantDiscountId;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.OffsetTime;
import java.time.ZoneOffset;
import java.time.DayOfWeek;
import com.gulfnet.shared_library.entity.ModifierGroup;
import com.gulfnet.shared_library.entity.ModifierItem;
import com.gulfnet.shared_library.entity.ModifierItemTranslation;
import com.gulfnet.shared_library.enums.ModifierType;
import com.gulfnet.shared_library.model.response.dto.ModifierGroupResponse;
import com.gulfnet.shared_library.model.response.dto.ModifierGroupTranslationDto;
import com.gulfnet.shared_library.model.response.dto.ModifierGroupWithItemsResponse;
import com.gulfnet.shared_library.model.response.dto.ModifierItemListResponseDto;
import java.util.Comparator;
import java.math.RoundingMode;
import com.gulfnet.restaurantmanagement.util.PriceOverrideHelper;

@Slf4j 
@Service
public class ItemServiceImpl implements ItemService {

    // Field names
    private static final String FIELD_BASE_PRICE = "basePrice";
    private static final String FIELD_CREATED_AT = "createdAt";
    
    // Other constants
    private static final String NO_TRANSLATIONS = "No translations";
    
    private static final String msgItemNotFound = "item.not.found";
    private static final String msgUserNotFound = "user.not.found";
    private static final String msgItemErrorInvalidLanguage = "error.invalid.language";
    private static final String msgItemErrorDeleted = "item.error.deleted";
    private static final String msgItemUpdateErrorNotFound = "item.update.error.not_found";
    private static final String msgItemGetSuccess = "item.get.success";

    @Autowired
    private ItemRepository itemRepository;

    @Autowired
    private ItemTranslationRepository itemTranslationRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private AWSService awsService;

    @Autowired
    private MessageUtil messageUtil;

    @Autowired
    private LocalizationProperties localizationProperties;

    @Autowired
    private RestaurantChainConfigProperties restaurantChainConfigProperties;

    @Autowired
    private ItemModifierGroupRepository itemModifierGroupRepository;

    @Autowired
    private ModifierGroupRepository modifierGroupRepository;

    @Autowired
    private AuditTrailService auditTrailService;

    @Autowired
    private RestaurantRepository restaurantRepository;

    @Autowired
    private ModifierItemRepository modifierItemRepository;

    @Autowired
    private CategoryItemMappingRepository categoryItemMappingRepository;

    @Autowired
    private MenuCategoryMappingRepository menuCategoryMappingRepository;

    @Autowired
    private MenuRepository menuRepository;

    @Autowired
    private RestaurantItemAvailabilityRepository restaurantItemAvailabilityRepository;

    @Autowired
    private MenuDiscountMappingRepository menuDiscountMappingRepository;

    @Autowired
    private com.gulfnet.shared_library.repository.RestaurantDiscountMappingRepository restaurantDiscountMappingRepository;

    @Autowired
    private CategoryDiscountMappingRepository categoryDiscountMappingRepository;

    @Autowired
    private ItemDiscountMappingRepository itemDiscountMappingRepository;

    @Autowired
    private DiscountBxgyItemRepository discountBxgyItemRepository;

    @Autowired
    private ImageThumbnailUtil imageThumbnailUtil;

    @Autowired
    private PromotionRepository promotionRepository;

    @Autowired
    private MenuPromotionMappingRepository menuPromotionMappingRepository;

    @Autowired
    private RestaurantPromotionMappingRepository restaurantPromotionMappingRepository;

    @Autowired
    private PriceOverrideHelper priceOverrideHelper;
    
    @Autowired
    private RestaurantMenuMappingRepository restaurantMenuMappingRepository;
    
    @Autowired
    private MenuTranslationRepository menuTranslationRepository;

    @Autowired
    private OrderNotificationService orderNotificationService;

    @Autowired(required = false)
    private SimpMessagingTemplate messagingTemplate;
   
    



    /**
     * Creates a new item with translations, base price, and image.
     * Validates translation uniqueness, item name uniqueness per language, and creates item entity.
     * Uploads item image and thumbnail to S3 if provided.
     *
     * @param userId  the ID of the user creating the item
     * @param request the item creation request with translations, price, and image
     * @param locale  locale code for localized error messages
     * @return ResponseDto containing the created item response
     * @throws ResponseStatusException if validation fails, user not found, or item name exists
     */
    @Override
@Transactional
public ResponseDto<ItemDto<ItemResponse>> createItem(String userId, ItemRequest request, String locale) {
    Locale userLocale = Locale.forLanguageTag(locale);
    User user = userRepository.findById(UUID.fromString(userId))
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                    messageUtil.getMessage(msgUserNotFound, userLocale, userId)));

    List<ItemTranslationDto> translations = request.getTranslations();
    if (translations != null && !translations.isEmpty()) {
        // Validate that at least one translation has a non-empty name
        boolean hasValidName = translations.stream()
            .anyMatch(t -> t.getName() != null && !t.getName().trim().isEmpty());
        
        if (!hasValidName) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    messageUtil.getMessage("item.create.error.no.valid.name", userLocale));
        }
        
        // Check for duplicate language codes and validate language codes
        Set<String> languageCodes = new HashSet<>();
        for (ItemTranslationDto entry : translations) {
            String name = entry.getName();
            String lang = entry.getLanguageCode();
            
            // Only validate non-empty names
            if (name != null && !name.trim().isEmpty() && lang != null) {
                if (!localizationProperties.getLanguages().contains(lang)) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        messageUtil.getMessage(msgItemErrorInvalidLanguage, userLocale));
                }
                if (!languageCodes.add(lang)) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        messageUtil.getMessage("item.error.duplicate.language", userLocale, lang));
                }
                
                // Check for existing names in the same language
                boolean exists = itemTranslationRepository.existsByNameAndLanguageCode(name.trim(), lang);
                if (exists) {
                    throw new ResponseStatusException(HttpStatus.CONFLICT,
                            messageUtil.getMessage("item.create.error.name.exists", userLocale));
                }
            }
        }
    } else {
        // No translations provided at all
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                messageUtil.getMessage("item.create.error.no.translations", userLocale));
    }

    String itemCode = request.getItemCode().trim();
    if (itemRepository.existsActiveItemByItemCode(itemCode)) {
        throw new ResponseStatusException(HttpStatus.CONFLICT,
                messageUtil.getMessage("item.itemCode.exists", userLocale, itemCode));
    }

    Item item = new Item();
    item.setItemCode(itemCode);
    item.setBasePrice(request.getBasePrice());
    item.setImageUrl(awsService.stripToKey(request.getImageUrl()));
    item.setHasModifierAssigned(request.getHasModifierAssigned()); 
    item.setOutOfStock(request.getOutOfStock());
    item.setStatus(request.getStatus());
    item.setDietaryPreference(request.getDietaryPreference()); 
    item.setItemOrderType(request.getItemOrderType());
    item.setAlcoholType(request.getAlcoholType());
    item.setIsDeleted(Boolean.TRUE.equals(request.getIsDeleted()));
    item.setCreatedAt(OffsetDateTime.now(ZoneOffset.UTC));
    item.setCreatedBy(user);
    item = itemRepository.save(item);

    // Create thumbnail same as bulk upload pattern
    if (item.getImageUrl() != null && !item.getImageUrl().isEmpty()) {
        try {
            String s3Key = extractS3Key(item.getImageUrl());
            byte[] original = awsService.downloadFileFromS3(s3Key);
            String ext = getExtensionFromKey(s3Key);
            byte[] thumb = imageThumbnailUtil.createThumbnail(original, ext);
            String thumbKey = buildThumbKeyFromKey(s3Key);
            String thumbUrl = awsService.uploadFile(new java.io.ByteArrayInputStream(thumb), thumbKey, thumb.length);
            item.setThumbnailUrl(thumbUrl);
            itemRepository.save(item);
        } catch (Exception e) {
            log.warn("Failed to generate thumbnail for item: {}", item.getId(), e);
        }
    }

    if (translations != null && !translations.isEmpty()) {
        for (ItemTranslationDto entry : translations) {
            String name = entry.getName();
            if (name != null && !name.trim().isEmpty() && entry.getLanguageCode() != null) {
                ItemTranslation translation = new ItemTranslation();
                translation.setName(name.trim());
                translation.setItem(item);
                translation.setDescription(entry.getDescription());
                translation.setLanguageCode(entry.getLanguageCode());
                itemTranslationRepository.save(translation);
            }
        }
    }

    List<ItemTranslation> savedTranslations = itemTranslationRepository.findAllByItemId(item.getId());
    List<ItemTranslationDto> translationDTOs = savedTranslations.stream()
            .map(t -> ItemTranslationDto.builder()
                    .languageCode(t.getLanguageCode())
                    .name(t.getName())
                    .description(t.getDescription())
                    .build())
            .collect(Collectors.toList());

    String currency = restaurantChainConfigProperties.getChain() != null ? restaurantChainConfigProperties.getChain().getCurrency() : null;

    ItemResponse response = ItemResponse.builder()
            .id(item.getId())
            .itemCode(item.getItemCode())
            .basePrice(item.getBasePrice() != null ? CurrencyFormatter.formatAmount(BigDecimal.valueOf(item.getBasePrice()), currency).doubleValue() : null)
            .imageUrl(item.getImageUrl() != null && !item.getImageUrl().isEmpty() ? 
                     awsService.getPreSignedUrl(item.getImageUrl()) : null)
            .hasModifierAssigned(item.getHasModifierAssigned()) 
            .outOfStock(item.getOutOfStock())
            .status(item.getStatus())
            .dietaryPreference(item.getDietaryPreference()) 
            .itemOrderType(item.getItemOrderType())
            .alcoholType(item.getAlcoholType())
            .isDeleted(item.getIsDeleted())
            .createdAt(item.getCreatedAt() != null ? item.getCreatedAt().toLocalDateTime() : null)
            .createdBy(item.getCreatedBy().getFirstName())
            .translations(translationDTOs)
            .build();

    ItemDto<ItemResponse> itemDto = ItemDto.<ItemResponse>builder()
            .item(response)
            .build();

    // Create audit trail for item creation
    try {
        Restaurant restaurant = null;
        if (user.getRestaurantId() != null) {
            restaurant = restaurantRepository.findById(user.getRestaurantId()).orElse(null);
        }
        String itemName = translationDTOs.isEmpty() ? NO_TRANSLATIONS : translationDTOs.get(0).getName();
        auditTrailService.createAuditTrail(
                user,
                ActionType.MENU_ITEM_CREATE,
                restaurant,
                null, // status - will default to NA for non-request actions
                null, // ipAddress - not available in this context
                null, // userAgent - not available in this context
                item.getId(),
                "ITEM",
                "Menu Item created: " + itemName
        );
    } catch (Exception e) {
        log.error("Failed to create audit trail for item creation: {}", e.getMessage());
        // Don't break item creation flow if audit trail fails
    }

    // Publish WebSocket notification for item creation
    publishItemWebSocketNotification(item, "CREATE", "ITEM_CREATE", "item.create.success", userLocale, translationDTOs);

    return ResponseDto.<ItemDto<ItemResponse>>builder()
            .message(messageUtil.getMessage("item.create.success", userLocale))
            .data(itemDto)
            .build();
}

private String extractS3Key(String url) {
    return awsService.stripToKey(url);
}

private String getExtensionFromKey(String key) {
    if (key == null) {
        return "png";
    }
    int dot = key.lastIndexOf('.');
    return (dot > -1 && dot < key.length() - 1) ? key.substring(dot + 1) : "png";
}

    /**
     * Builds a thumbnail S3 key from a full image S3 key.
     * Extracts directory path and prepends "thumb_" to the filename.
     *
     * @param key the S3 key of the full image
     * @return S3 key for the thumbnail (or "thumb_" if key is null)
     */
private String buildThumbKeyFromKey(String key) {
    if (key == null) {
        return "thumb_";
    }
    int slash = key.lastIndexOf('/');
    String dir = (slash >= 0) ? key.substring(0, slash + 1) : "";
    String file = (slash >= 0) ? key.substring(slash + 1) : key;
    if (file == null) {
        file = "";
    }
    int dot = file.lastIndexOf('.');
    String base = (dot > -1) ? file.substring(0, dot) : file;
    String ext = (dot > -1) ? file.substring(dot) : "";
    return dir + "thumb_" + base + ext;
}

    /**
     * Updates an existing item with new translations, price, and image.
     * Validates translation uniqueness and item name uniqueness per language.
     * Updates item entity, translations, and image/thumbnail if provided.
     *
     * @param itemId  the UUID of the item to update
     * @param request the item update request with new details
     * @param userId  the ID of the user performing the update
     * @param locale  locale code for localized error messages
     * @return ResponseDto containing the updated item response
     * @throws ResponseStatusException if item not found, validation fails, or item name exists
     */
@Override
@Transactional
public ResponseDto<ItemDto<ItemResponse>> updateItem(UUID itemId, ItemRequest request, String userId, String locale) {
    Locale userLocale = Locale.forLanguageTag(locale);

    User user = userRepository.findById(UUID.fromString(userId))
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                    messageUtil.getMessage(msgUserNotFound, userLocale, userId)));

    Item item = itemRepository.findById(itemId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                    messageUtil.getMessage(msgItemUpdateErrorNotFound, userLocale, itemId)));

    if (item.getIsDeleted()) {
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                messageUtil.getMessage("item.update.error.deleted", userLocale));
    }

    List<ItemTranslationDto> translations = request.getTranslations();
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
        for (ItemTranslationDto entry : translations) {
            String name = entry.getName();
            String lang = entry.getLanguageCode();
            
            // Only validate non-empty names
            if (name != null && !name.trim().isEmpty() && lang != null) {
                if (!localizationProperties.getLanguages().contains(lang)) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        messageUtil.getMessage(msgItemErrorInvalidLanguage, userLocale));
                }
                if (!languageCodes.add(lang)) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        messageUtil.getMessage("item.error.duplicate.language", userLocale, lang));
                }
            }
        }
    } else {
        // No translations provided at all
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                messageUtil.getMessage("combo.translations.required", userLocale));
    }

    String itemCode = request.getItemCode().trim();
    if (itemRepository.existsActiveItemByItemCodeExcludingId(itemCode, itemId)) {
        throw new ResponseStatusException(HttpStatus.CONFLICT,
                messageUtil.getMessage("item.itemCode.exists", userLocale, itemCode));
    }

    // Update item basic details
    item.setItemCode(itemCode);
    item.setBasePrice(request.getBasePrice());
    item.setImageUrl(awsService.stripToKey(request.getImageUrl()));
    item.setAlcoholType(request.getAlcoholType());
    item.setHasModifierAssigned(request.getHasModifierAssigned()); 
    item.setOutOfStock(request.getOutOfStock());
    item.setStatus(request.getStatus());
    item.setDietaryPreference(request.getDietaryPreference()); 
    item.setItemOrderType(request.getItemOrderType());
    item.setUpdatedAt(OffsetDateTime.now(ZoneOffset.UTC));
    item.setUpdatedBy(user);
    item = itemRepository.save(item);

    // Create thumbnail same as bulk upload pattern on update when imageUrl present
    if (item.getImageUrl() != null && !item.getImageUrl().isEmpty()) {
        try {
            String s3Key = extractS3Key(item.getImageUrl());
            byte[] original = awsService.downloadFileFromS3(s3Key);
            String ext = getExtensionFromKey(s3Key);
            byte[] thumb = imageThumbnailUtil.createThumbnail(original, ext);
            String thumbKey = buildThumbKeyFromKey(s3Key);
            String thumbUrl = awsService.uploadFile(new java.io.ByteArrayInputStream(thumb), thumbKey, thumb.length);
            item.setThumbnailUrl(thumbUrl);
            itemRepository.save(item);
        } catch (Exception e) {
            log.warn("Failed to generate thumbnail for item: {}", item.getId(), e);
        }
    }

    if (translations != null && !translations.isEmpty()) {
        List<ItemTranslation> existingTranslations = itemTranslationRepository.findAllByItemIdWithLanguage(item.getId());
        Map<String, ItemTranslation> existingTranslationMap = new HashMap<>();
        for (ItemTranslation translation : existingTranslations) {
            existingTranslationMap.put(translation.getLanguageCode(), translation);
        }

        // Get language codes from request with non-empty names
        Set<String> validRequestLanguageCodes = translations.stream()
                .filter(t -> t.getLanguageCode() != null && 
                           t.getName() != null && 
                           !t.getName().trim().isEmpty())
                .map(ItemTranslationDto::getLanguageCode)
                .collect(Collectors.toSet());

        // Remove translations that are not in the request or have empty names
        List<ItemTranslation> translationsToRemove = new ArrayList<>();
        for (ItemTranslation existingTranslation : existingTranslations) {
            if (!validRequestLanguageCodes.contains(existingTranslation.getLanguageCode())) {
                translationsToRemove.add(existingTranslation);
            }
        }

        // Delete translations that are not in the request or have empty names
        for (ItemTranslation translationToRemove : translationsToRemove) {
            itemTranslationRepository.delete(translationToRemove);
        }

        for (ItemTranslationDto entry : translations) {
            String name = entry.getName();
            if (name != null && !name.trim().isEmpty() && entry.getLanguageCode() != null) {
                ItemTranslation translation = existingTranslationMap.get(entry.getLanguageCode());
                if (translation != null) {
                    translation.setName(name.trim());
                    translation.setDescription(entry.getDescription());
                    itemTranslationRepository.save(translation);
                } else {
                    translation = new ItemTranslation();
                    translation.setName(name.trim());
                    translation.setItem(item);
                    translation.setDescription(entry.getDescription());
                    translation.setLanguageCode(entry.getLanguageCode());
                    itemTranslationRepository.save(translation);
                }
            }
        }
    }

    List<ItemTranslation> savedTranslations = itemTranslationRepository.findAllByItemIdWithLanguage(item.getId());
    List<ItemTranslationDto> translationDTOs = savedTranslations.stream()
            .map(t -> ItemTranslationDto.builder()
                    .languageCode(t.getLanguageCode())
                    .name(t.getName())
                    .description(t.getDescription())
                    .build())
            .collect(Collectors.toList());

    User createdByUser = userRepository.findById(item.getCreatedBy().getId()).orElse(null);

    String currency = restaurantChainConfigProperties.getChain() != null ? restaurantChainConfigProperties.getChain().getCurrency() : null;

    ItemResponse response = ItemResponse.builder()
            .id(item.getId())
            .itemCode(item.getItemCode())
            .basePrice(item.getBasePrice() != null ? CurrencyFormatter.formatAmount(BigDecimal.valueOf(item.getBasePrice()), currency).doubleValue() : null)
            .imageUrl(item.getImageUrl() != null && !item.getImageUrl().isEmpty() ? 
                     awsService.getPreSignedUrl(item.getImageUrl()) : null)
            .outOfStock(item.getOutOfStock())
            .status(item.getStatus())
            .dietaryPreference(item.getDietaryPreference()) 
            .itemOrderType(item.getItemOrderType())
            .alcoholType(item.getAlcoholType())
            .hasModifierAssigned(item.getHasModifierAssigned()) 
            .isDeleted(item.getIsDeleted())
            .createdAt(item.getCreatedAt() != null ? item.getCreatedAt().toLocalDateTime() : null)
            .createdBy(createdByUser != null ? createdByUser.getFirstName() : null)
            .updatedAt(item.getUpdatedAt() != null ? item.getUpdatedAt().toLocalDateTime() : null)
            .updatedBy(user.getFirstName())
            .translations(translationDTOs)
            .build();

    ItemDto<ItemResponse> itemDto = ItemDto.<ItemResponse>builder()
            .item(response)
            .build();

    // Create audit trail for item update
    try {
        Restaurant restaurant = null;
        if (user.getRestaurantId() != null) {
            restaurant = restaurantRepository.findById(user.getRestaurantId()).orElse(null);
        }
        String itemName = translationDTOs.isEmpty() ? NO_TRANSLATIONS : translationDTOs.get(0).getName();
        auditTrailService.createAuditTrail(
                user,
                ActionType.MENU_ITEM_UPDATE,
                restaurant,
                null, // status - will default to NA for non-request actions
                null, // ipAddress - not available in this context
                null, // userAgent - not available in this context
                item.getId(),
                "ITEM",
                "Menu Item updated: " + itemName
        );
    } catch (Exception e) {
        log.error("Failed to create audit trail for item update: {}", e.getMessage());
        // Don't break item update flow if audit trail fails
    }

    // Publish WebSocket notification for item update
    publishItemWebSocketNotification(item, "UPDATE", "ITEM_UPDATE", "item.update.success", userLocale, translationDTOs);

    return ResponseDto.<ItemDto<ItemResponse>>builder()
            .message(messageUtil.getMessage("item.update.success", userLocale))
            .data(itemDto)
            .build();
}

/**
 * Publishes WebSocket notification for item create/update operations
 * @param item The item entity
 * @param action The action performed ("CREATE" or "UPDATE")
 * @param notificationType The notification type ("ITEM_CREATE" or "ITEM_UPDATE")
 * @param messageKey The message key for the success message
 * @param userLocale The user locale
 * @param translationDTOs The item translations
 */
private void publishItemWebSocketNotification(Item item, String action, String notificationType, 
                                               String messageKey, Locale userLocale, 
                                               List<ItemTranslationDto> translationDTOs) {
    try {
        String topic = "/topic/item-price";
        Map<String, Object> itemData = new HashMap<>();
        itemData.put("itemId", item.getId().toString());
        itemData.put(FIELD_BASE_PRICE, item.getBasePrice());
        itemData.put("status", item.getStatus() != null ? item.getStatus().toString() : null);
        itemData.put("outOfStock", item.getOutOfStock());
        itemData.put("action", action);
        itemData.put("notificationType", notificationType);
        itemData.put("timestamp", LocalDateTime.now(ZoneOffset.UTC).format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
        
        if (translationDTOs != null && !translationDTOs.isEmpty()) {
            itemData.put("itemName", translationDTOs.get(0).getName());
        }
        
        StatusEventMessage eventMessage = StatusEventMessage.builder()
                .message(messageUtil.getMessage(messageKey, userLocale))
                .notificationType(notificationType)
                .itemId(item.getId().toString())
                .data(itemData)
                .build();
        
        // Send directly to WebSocket clients
        if (messagingTemplate != null) {
            messagingTemplate.convertAndSend(topic, eventMessage);
            log.info("[Notification][WebSocket] broadcast topic={} notificationType={} action={} itemId={}",
                    topic, notificationType, action.toLowerCase(), item.getId());
        }
        
        // Also publish to RabbitMQ for integration service to log
        orderNotificationService.publishToRabbitMQ(topic, eventMessage);
    } catch (Exception e) {
        log.error("Failed to publish WebSocket notification for item {}: {}", action.toLowerCase(), e.getMessage(), e);
    }
}

    /**
     * Soft deletes an item by setting isDeleted flag to true.
     * Validates that item is not published in any menu and has no active modifier group assignments.
     * Sends WebSocket notification about the deletion.
     *
     * @param itemId the UUID of the item to delete
     * @param userId the ID of the user performing the deletion
     * @param locale locale code for localized error messages
     * @return ResponseDto with success message
     * @throws ResponseStatusException if item not found, is published, or has active modifier assignments
     */
    @Override
    @Transactional
    public ResponseDto<String> deleteItem(UUID itemId, String userId, String locale) {
        Locale userLocale = Locale.forLanguageTag(locale);

        User user = userRepository.findById(UUID.fromString(userId))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        messageUtil.getMessage(msgUserNotFound, userLocale)));

        Item item = itemRepository.findById(itemId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        messageUtil.getMessage(msgItemUpdateErrorNotFound, userLocale, itemId)));

        if (Boolean.TRUE.equals(item.getIsDeleted())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    messageUtil.getMessage("item.delete.error.already_deleted", userLocale));
        }

        // Check if item is assigned to any category of any menu and that menu is assigned to any restaurant with LIVE status
        List<CategoryItemMapping> itemMappings = categoryItemMappingRepository.findByItemIdWithCategoryHierarchy(itemId);
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
                    List<RestaurantMenuMapping> restaurantMenuMappings = restaurantMenuMappingRepository.findById_MenuId(menuId);
                    for (RestaurantMenuMapping mapping : restaurantMenuMappings) {
                        if (RestaurantMenuMappingStatus.LIVE.equals(mapping.getStatus())) {
                            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                                    messageUtil.getMessage("item.delete.error.assigned.to.live.menu", userLocale));
                        }
                    }
                }
            }
        }

        item.setIsDeleted(true);
        item.setUpdatedAt(OffsetDateTime.now(ZoneOffset.UTC));
        item.setUpdatedBy(user);
        itemRepository.save(item);

        // Create audit trail for item deletion
        try {
            Restaurant restaurant = null;
            if (user.getRestaurantId() != null) {
                restaurant = restaurantRepository.findById(user.getRestaurantId()).orElse(null);
            }
            List<ItemTranslation> translations = itemTranslationRepository.findAllByItemId(item.getId());
            String itemName = translations.isEmpty() ? NO_TRANSLATIONS : translations.get(0).getName();
            auditTrailService.createAuditTrail(
                    user,
                    ActionType.MENU_ITEM_DELETE,
                    restaurant,
                    null, // status - will default to NA for non-request actions
                    null, // ipAddress - not available in this context
                    null, // userAgent - not available in this context
                    item.getId(),
                    "ITEM",
                    "Menu Item deleted: " + itemName
            );
        } catch (Exception e) {
            log.error("Failed to create audit trail for item deletion: {}", e.getMessage());
            // Don't break item deletion flow if audit trail fails
        }

        return ResponseDto.<String>builder()
                .message(messageUtil.getMessage("item.delete.success", userLocale))
                .data("Item with ID " + itemId + " has been deleted")
                .build();
    }

        // Helper to check if a menu is published and not deleted
     private boolean isPublishedAndNotDeleted(Menu menu) {
            return MenuStatus.PUBLISHED.equals(menu.getStatus()) && !Boolean.TRUE.equals(menu.getIsDeleted());
    }
    
    // Helper method to check if item has any active modifier group assignments
    private boolean hasActiveModifierGroupAssignments(UUID itemId) {
        return !itemModifierGroupRepository.findByItemIdAndIsDeletedFalse(itemId).isEmpty();
    }
    
    /**
     * Retrieves a paginated and filterable list of items.
     * Supports filtering by status, modifier assignment, deletion status, search by name, item order type, and alcohol type.
     * Results are sorted and paginated with locale-aware name sorting.
     *
     * @param page              page number for pagination
     * @param size              page size for pagination
     * @param status            optional filter by entity status
     * @param hasModifierAssigned optional filter by modifier assignment status
     * @param search            optional search term for item name
     * @param sortBy            field to sort by
     * @param direction         sort direction
     * @param locale            locale code for localized responses and sorting
     * @param thumb             whether to include thumbnail URLs
     * @param isDeleted         optional filter by deletion status (true shows deleted, false shows non-deleted)
     * @param itemOrderType     optional filter by item order type (DINE_IN, TAKEAWAY, BOTH)
     * @param alcoholType       optional filter by alcohol type (ALCOHOLIC, NON_ALCOHOLIC)
     * @return ResponseDto containing paginated list of items
     */
    @Override
public ResponseDto<ItemListResponse> getItems(Integer page, Integer size, String status, Boolean hasModifierAssigned,
        String search, String sortBy, Sort.Direction direction, String locale, Boolean thumb, Boolean isDeleted, String itemOrderType, String alcoholType) {
    
    Locale userLocale = Locale.forLanguageTag(locale);

    // Validate and set pagination
    int pageNumber = (page != null ? page : 1) - 1;
    if (pageNumber < 0) pageNumber = 0;
    int pageSize = size != null ? size : Integer.MAX_VALUE;
    if (pageSize < 1) pageSize = Integer.MAX_VALUE;

    // Validate locale
    if (!localizationProperties.getLanguages().contains(locale)) {
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                messageUtil.getMessage(msgItemErrorInvalidLanguage, userLocale));
    }

    // Process status filter
    final String statusValue;
    if (status != null && !status.isEmpty()) {
        try {
            EntityStatus statusEnum = EntityStatus.valueOf(status.toUpperCase());
            statusValue = statusEnum.name();
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    messageUtil.getMessage("error.invalid.status", userLocale));
        }
    } else {
        statusValue = null;
    }

    // Process itemOrderType filter
    final ItemOrderType itemOrderTypeEnum;
    if (itemOrderType != null && !itemOrderType.isEmpty()) {
        try {
            itemOrderTypeEnum = ItemOrderType.valueOf(itemOrderType.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    messageUtil.getMessage("item.error.invalid.itemOrderType", userLocale));
        }
    } else {
        itemOrderTypeEnum = null;
    }

    // Process alcoholType filter
    final AlcoholType alcoholTypeEnum;
    if (alcoholType != null && !alcoholType.isEmpty()) {
        try {
            alcoholTypeEnum = AlcoholType.valueOf(alcoholType.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    messageUtil.getMessage("item.error.invalid.alcoholType", userLocale));
        }
    } else {
        alcoholTypeEnum = null;
    }

    // Get currency for formatting prices
    String currency = restaurantChainConfigProperties.getChain() != null ? restaurantChainConfigProperties.getChain().getCurrency() : null;

    // Determine sort field mapping (DB fields)
    String normalizedSortBy = (sortBy == null || sortBy.isBlank()) ? FIELD_CREATED_AT : sortBy;
    String dbSortField = switch (normalizedSortBy) {
        case "price" -> FIELD_BASE_PRICE;
        case FIELD_BASE_PRICE -> FIELD_BASE_PRICE;
        case FIELD_CREATED_AT -> FIELD_CREATED_AT;
        case "updatedAt" -> "updatedAt";
        default -> null; // e.g., name -> needs in-memory due to locale translations
    };

    // Create pagination (optionally with DB sort if supported)
    Pageable pageable = (dbSortField != null)
            ? PageRequest.of(pageNumber, pageSize, Sort.by(direction, dbSortField))
            : PageRequest.of(pageNumber, pageSize);
    
    // Create specification for filtering
    Specification<Item> spec = (root, query, cb) -> {
        List<Predicate> predicates = new ArrayList<>();
        
        // Add status filter
        if (statusValue != null) {
            predicates.add(cb.equal(root.get("status"), EntityStatus.valueOf(statusValue)));
        }
        
        // Add hasModifierAssigned filter
        if (hasModifierAssigned != null) {
            predicates.add(cb.equal(root.get("hasModifierAssigned"), hasModifierAssigned));
        }
        
        // Handle isDeleted filter: if isDeleted=true, show deleted; otherwise show non-deleted (default)
        if (isDeleted != null && isDeleted) {
            predicates.add(cb.equal(root.get("isDeleted"), true));
        } else {
            predicates.add(cb.equal(root.get("isDeleted"), false));
        }
        
        // Add search filter with translation join
        if (search != null && !search.trim().isEmpty()) {
            Join<Item, ItemTranslation> translationJoin = root.join("translations", JoinType.LEFT);
            String searchPattern = "%" + search.trim().toLowerCase() + "%";
            // Search only in name field, handling null values
            predicates.add(cb.and(
                cb.isNotNull(translationJoin.get("name")),
                cb.like(cb.lower(translationJoin.get("name")), searchPattern)
            ));
            // Add distinct to avoid duplicate items when an item has multiple translations
            query.distinct(true);
        }
        
        // Add itemOrderType filter
        if (itemOrderTypeEnum != null) {
            predicates.add(cb.equal(root.get("itemOrderType"), itemOrderTypeEnum));
        }
        
        // Add alcoholType filter
        if (alcoholTypeEnum != null) {
            predicates.add(cb.equal(root.get("alcoholType"), alcoholTypeEnum));
        }
        
        return cb.and(predicates.toArray(new Predicate[0]));
    };
    
    // If sorting by translatable name, fetch all then sort globally before slicing
    if ("name".equalsIgnoreCase(normalizedSortBy)) {
        List<Item> allItems = itemRepository.findAll(spec);

        // Batch load translations for all items
        List<UUID> allItemIds = allItems.stream().map(Item::getId).collect(Collectors.toList());
        Map<UUID, List<ItemTranslation>> translationsMapAll = allItemIds.isEmpty()
                ? Collections.emptyMap()
                : itemTranslationRepository
                        .findAllByItemIdIn(allItemIds)
                        .stream()
                        .collect(Collectors.groupingBy(t -> t.getItem().getId()));

        // Batch load menu counts for all items to avoid N+1 queries
        Map<UUID, Long> menuCountMap;
        if (allItemIds.isEmpty()) {
            menuCountMap = Collections.emptyMap();
        } else {
            List<Object[]> menuCountsRaw = categoryItemMappingRepository.countMenusByItemIdsBatch(allItemIds);
            menuCountMap = menuCountsRaw.stream()
                    .collect(Collectors.toMap(
                            row -> (UUID) row[0],
                            row -> ((Number) row[1]).longValue()
                    ));
        }

        // Map to responses
        List<ItemResponse> allResponses = allItems.stream()
                .map(item -> {
                    List<ItemTranslation> itemTranslations = translationsMapAll.getOrDefault(item.getId(), Collections.emptyList());
                    ItemTranslation translation = itemTranslations.stream()
                            .filter(t -> t.getLanguageCode() != null && t.getLanguageCode().equals(locale))
                            .findFirst()
                            .orElse(null);

                    // Apply fallback if exact match not found
                    if (translation == null && !itemTranslations.isEmpty()) {
                        // Get ordered languages from application.properties (excluding requested locale)
                        List<String> fallbackLanguages = localizationProperties.getLanguages().stream()
                                .filter(lang -> lang != null && !lang.equalsIgnoreCase(locale))
                                .collect(Collectors.toList());
                        
                        // Iterate through fallback languages in order from properties file
                        for (String fallbackLang : fallbackLanguages) {
                            translation = itemTranslations.stream()
                                    .filter(t -> t.getLanguageCode() != null && t.getLanguageCode().equalsIgnoreCase(fallbackLang))
                                    .findFirst()
                                    .orElse(null);
                            if (translation != null) {
                                break; // Found a translation, stop searching
                            }
                        }
                    }

                    List<ItemTranslationDto> translationDTOs = new ArrayList<>();
                    if (translation != null) {
                        translationDTOs.add(ItemTranslationDto.builder()
                                .languageCode(translation.getLanguageCode())
                                .name(translation.getName())
                                .description(translation.getDescription())
                                .build());
                    }

                    String createdByName = null;
                    String updatedByName = null;
                    if (item.getCreatedBy() != null) {
                        User createdByUser = item.getCreatedBy();
                        createdByName = createdByUser.getFirstName() + " " + createdByUser.getLastName();
                    }
                    if (item.getUpdatedBy() != null) {
                        User updatedByUser = item.getUpdatedBy();
                        updatedByName = updatedByUser.getFirstName() + " " + updatedByUser.getLastName();
                    }

                    String rawUrl;
                    if (Boolean.TRUE.equals(thumb) && item.getThumbnailUrl() != null && !item.getThumbnailUrl().isEmpty()) {
                        rawUrl = item.getThumbnailUrl();
                    } else {
                        rawUrl = item.getImageUrl();
                    }
                    String presigned = (rawUrl != null && !rawUrl.isEmpty()) ? awsService.getPreSignedUrl(rawUrl) : null;

                    return ItemResponse.builder()
                            .id(item.getId())
                            .itemCode(item.getItemCode())
                            .basePrice(item.getBasePrice() != null ? CurrencyFormatter.formatAmount(BigDecimal.valueOf(item.getBasePrice()), currency).doubleValue() : null)
                            .imageUrl(presigned)
                            .outOfStock(item.getOutOfStock())
                            .status(item.getStatus())
                            .dietaryPreference(item.getDietaryPreference())
                            .itemOrderType(item.getItemOrderType())
                            .alcoholType(item.getAlcoholType())
                            .hasModifierAssigned(Boolean.TRUE.equals(item.getHasModifierAssigned()))
                            .isDeleted(item.getIsDeleted())
                            .translations(translationDTOs)
                            .createdAt(item.getCreatedAt() != null ? item.getCreatedAt().toLocalDateTime() : null)
                            .updatedAt(item.getUpdatedAt() != null ? item.getUpdatedAt().toLocalDateTime() : null)
                            .createdBy(createdByName)
                            .updatedBy(updatedByName)
                            .menuCount(menuCountMap.getOrDefault(item.getId(), 0L))
                            .build();
                })
                .collect(Collectors.toList());

        // Global sort by locale-aware name
        sortItems(allResponses, normalizedSortBy, direction, locale);

        // Manual pagination slice
        int fromIndex = Math.min(pageNumber * pageSize, allResponses.size());
        int toIndex = Math.min(fromIndex + pageSize, allResponses.size());
        List<ItemResponse> pagedResponses = allResponses.subList(fromIndex, toIndex);

        PaginationMetaData paginationMetaData = PaginationMetaData.builder()
                .page(pageNumber + 1)
                .size(pageSize)
                .totalPages((int) Math.ceil((double) allResponses.size() / pageSize))
                .totalRecords((long) allResponses.size())
                .build();

        ItemListResponse listResponse = ItemListResponse.builder()
                .items(pagedResponses)
                .count((long) pagedResponses.size())
                .total((long) allResponses.size())
                .metaData(paginationMetaData)
                .build();

        return ResponseDto.<ItemListResponse>builder()
                .message(messageUtil.getMessage("item.discount.list.success", userLocale))
                .data(listResponse)
                .build();
    }

    // Otherwise, use DB-level pagination + sorting
    Page<Item> itemPage = itemRepository.findAll(spec, pageable);
    List<Item> items = itemPage.getContent();

    // Batch load all translations to avoid N+1 queries
    List<UUID> itemIds = items.stream()
            .map(Item::getId)
            .collect(Collectors.toList());
    
    Map<UUID, List<ItemTranslation>> translationsMap = itemTranslationRepository
            .findAllByItemIdIn(itemIds)
            .stream()
            .collect(Collectors.groupingBy(t -> t.getItem().getId()));

    // Batch load menu counts for all items to avoid N+1 queries
    Map<UUID, Long> menuCountMap;
    if (itemIds.isEmpty()) {
        menuCountMap = Collections.emptyMap();
    } else {
        List<Object[]> menuCountsRaw = categoryItemMappingRepository.countMenusByItemIdsBatch(itemIds);
        menuCountMap = menuCountsRaw.stream()
                .collect(Collectors.toMap(
                        row -> (UUID) row[0],
                        row -> ((Number) row[1]).longValue()
                ));
    }

    // Convert to response DTOs with inline mapping
    List<ItemResponse> itemResponses = items.stream()
            .map(item -> {
                // Get translations from batch-loaded map
                List<ItemTranslation> itemTranslations = translationsMap.getOrDefault(item.getId(), Collections.emptyList());
                
                // Find translation for the requested language
                ItemTranslation translation = itemTranslations.stream()
                        .filter(t -> t.getLanguageCode() != null && t.getLanguageCode().equals(locale))
                        .findFirst()
                        .orElse(null);

                // Apply fallback if exact match not found
                if (translation == null && !itemTranslations.isEmpty()) {
                    // Get ordered languages from application.properties (excluding requested locale)
                    List<String> fallbackLanguages = localizationProperties.getLanguages().stream()
                            .filter(lang -> lang != null && !lang.equalsIgnoreCase(locale))
                            .collect(Collectors.toList());
                    
                    // Iterate through fallback languages in order from properties file
                    for (String fallbackLang : fallbackLanguages) {
                        translation = itemTranslations.stream()
                                .filter(t -> t.getLanguageCode() != null && t.getLanguageCode().equalsIgnoreCase(fallbackLang))
                                .findFirst()
                                .orElse(null);
                        if (translation != null) {
                            break; // Found a translation, stop searching
                        }
                    }
                }

                List<ItemTranslationDto> translationDTOs = new ArrayList<>();
                if (translation != null) {
                    translationDTOs.add(ItemTranslationDto.builder()
                            .languageCode(translation.getLanguageCode())
                            .name(translation.getName())
                            .description(translation.getDescription())
                            .build());
                }

                String createdByName = null;
                String updatedByName = null;

                if (item.getCreatedBy() != null) {
                    User createdByUser = item.getCreatedBy();
                    createdByName = createdByUser.getFirstName() + " " + createdByUser.getLastName();
                }

                if (item.getUpdatedBy() != null) {
                    User updatedByUser = item.getUpdatedBy();
                    updatedByName = updatedByUser.getFirstName() + " " + updatedByUser.getLastName();
                }

                String rawUrl;
                if (Boolean.TRUE.equals(thumb) && item.getThumbnailUrl() != null && !item.getThumbnailUrl().isEmpty()) {
                    rawUrl = item.getThumbnailUrl();
                    log.info("Thumbnail URL: {}", rawUrl);
                } else {
                    rawUrl = item.getImageUrl();
                    log.info("Image URL: {}", rawUrl);
                }
                String presigned = (rawUrl != null && !rawUrl.isEmpty()) ? awsService.getPreSignedUrl(rawUrl) : null;

                return ItemResponse.builder()
                        .id(item.getId())
                        .itemCode(item.getItemCode())
                        .basePrice(item.getBasePrice() != null ? CurrencyFormatter.formatAmount(BigDecimal.valueOf(item.getBasePrice()), currency).doubleValue() : null)
                        .imageUrl(presigned)
                        .outOfStock(item.getOutOfStock())
                        .status(item.getStatus())
                        .dietaryPreference(item.getDietaryPreference())
                        .itemOrderType(item.getItemOrderType())
                        .alcoholType(item.getAlcoholType())
                        .hasModifierAssigned(Boolean.TRUE.equals(item.getHasModifierAssigned()))
                        .isDeleted(item.getIsDeleted())
                        .translations(translationDTOs)
                        .createdAt(item.getCreatedAt() != null ? item.getCreatedAt().toLocalDateTime() : null)
                        .updatedAt(item.getUpdatedAt() != null ? item.getUpdatedAt().toLocalDateTime() : null)
                        .createdBy(createdByName)
                        .updatedBy(updatedByName)
                        .menuCount(menuCountMap.getOrDefault(item.getId(), 0L))
                        .build();
            })
            .collect(Collectors.toList());

    // No in-memory resort when DB sorting is applied

    // Build pagination metadata from actual page data
    PaginationMetaData paginationMetaData = PaginationMetaData.builder()
            .page(itemPage.getNumber() + 1)
            .size(itemPage.getSize())
            .totalPages(itemPage.getTotalPages())
            .totalRecords(itemPage.getTotalElements())
            .build();

    // Build final response
    ItemListResponse listResponse = ItemListResponse.builder()
            .items(itemResponses)
            .count((long) itemResponses.size())
            .total(itemPage.getTotalElements())
            .metaData(paginationMetaData)
            .build();

    return ResponseDto.<ItemListResponse>builder()
            .message(messageUtil.getMessage("item.discount.list.success", userLocale))
            .data(listResponse)
            .build();
}
    

    /**
     * Sorts a list of items in-memory using locale-aware comparison.
     * Supports sorting by name (locale-aware), createdAt, basePrice, and status.
     *
     * @param items    the list of items to sort
     * @param sortBy   field to sort by (defaults to "createdAt")
     * @param direction sort direction
     * @param locale   locale code for locale-aware name sorting
     */
private void sortItems(List<ItemResponse> items, String sortBy, Sort.Direction direction, String locale) {
    String normalizedSortBy = (sortBy == null || sortBy.isBlank()) ? FIELD_CREATED_AT : sortBy;
    Comparator<ItemResponse> comparator = switch (normalizedSortBy) {
        case "name" -> Comparator.comparing(
                item -> getItemNameForLocale(item, locale),
                (s1, s2) -> {
                    if (s1 == null) return (s2 == null) ? 0 : 1;
                    if (s2 == null) return -1;
                    
                    // Create a collator for the specific locale
                    Collator collator = switch(locale) {
                        case "th" -> Collator.getInstance(new Locale("th", "TH"));
                        case "ja" -> Collator.getInstance(new Locale("ja", "JP"));
                        default -> Collator.getInstance(new Locale("en", "US"));
                    };
                    
                    // Set collator strength
                    collator.setStrength(Collator.PRIMARY);
                    
                    return collator.compare(s1, s2);
                }
        );
        case "price", FIELD_BASE_PRICE -> Comparator.comparing(
            ItemResponse::getBasePrice,
            (p1, p2) -> {
                if (p1 == null && p2 == null) return 0;
                if (p1 == null) return 1;  // null values last
                if (p2 == null) return -1;
                return p1.compareTo(p2);
            }
    );
        default -> Comparator.comparing(
                ItemResponse::getCreatedAt,
                Comparator.nullsLast(LocalDateTime::compareTo)
        );
    };

    if (direction == Sort.Direction.DESC) {
        comparator = comparator.reversed();
    }

    items.sort(comparator);
}

    /**
     * Gets the item name for a specific locale with fallback logic.
     * Tries exact locale match first, then falls back to default language or first available translation.
     *
     * @param item   the item response to get name from
     * @param locale locale code for selecting translation
     * @return item name in the requested locale or fallback
     */
private String getItemNameForLocale(ItemResponse item, String locale) {
    if (item.getTranslations() == null || item.getTranslations().isEmpty()) {
        return "";
    }
    // Try exact match first
    for (var t : item.getTranslations()) {
        if (t.getLanguageCode() != null && t.getLanguageCode().equalsIgnoreCase(locale)) {
            return t.getName();
        }
    }
    // Fallback using configured language order
    for (String lang : localizationProperties.getLanguages()) {
        for (var t : item.getTranslations()) {
            if (t.getLanguageCode() != null && t.getLanguageCode().equalsIgnoreCase(lang)) {
                return t.getName();
            }
        }
    }
    // Last resort: first non-null name
    for (var t : item.getTranslations()) {
        if (t.getName() != null) return t.getName();
    }
    return "";
}

   
    /**
     * Retrieves a single item by ID with all translations and details.
     * Optionally includes thumbnail URL if thumb is true.
     *
     * @param itemId the UUID of the item to retrieve
     * @param locale locale code for localized responses
     * @param thumb  whether to include thumbnail URL
     * @return ResponseDto containing the item details
     * @throws ResponseStatusException if item not found or locale is invalid
     */
@Override
@Transactional(readOnly = true)
public ResponseDto<ItemDto<ItemResponse>> getItemById(UUID itemId, String locale, Boolean thumb) {
    Locale userLocale = Locale.forLanguageTag(locale);

    if (!localizationProperties.getLanguages().contains(locale)) {
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                messageUtil.getMessage(msgItemErrorInvalidLanguage, userLocale));
    }

    Item item = itemRepository.findById(itemId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                    messageUtil.getMessage(msgItemNotFound, userLocale, itemId)));

    if (Boolean.TRUE.equals(item.getIsDeleted())) {
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                messageUtil.getMessage(msgItemErrorDeleted, userLocale));
    }

    // Get all translations for the item
    List<ItemTranslation> translations = itemTranslationRepository.findAllByItemId(item.getId());
    
    // Map all translations to DTOs
    List<ItemTranslationDto> translationDtos = translations.stream()
            .map(translation -> ItemTranslationDto.builder()
                    .languageCode(translation.getLanguageCode())
                    .name(translation.getName())
                    .description(translation.getDescription())
                    .build())
            .collect(Collectors.toList());

    // Get creator and updater information
    User createdByUser = null;
    User updatedByUser = null;

    if (item.getCreatedBy() != null) {
        createdByUser = userRepository.findById(item.getCreatedBy().getId()).orElse(null);
    }
    if (item.getUpdatedBy() != null) {
        updatedByUser = userRepository.findById(item.getUpdatedBy().getId()).orElse(null);
    }

    String createdByName = createdByUser != null ?
            createdByUser.getFirstName() + " " + createdByUser.getLastName() : null;
    String updatedByName = updatedByUser != null ?
            updatedByUser.getFirstName() + " " + updatedByUser.getLastName() : null;

    String rawUrl;
    if (Boolean.TRUE.equals(thumb) && item.getThumbnailUrl() != null && !item.getThumbnailUrl().isEmpty()) {
        rawUrl = item.getThumbnailUrl();
    } else {
        rawUrl = item.getImageUrl();
    }
    String presignedUrl = (rawUrl != null && !rawUrl.isEmpty()) ? awsService.getPreSignedUrl(rawUrl) : null;

    // Get currency for formatting prices
    String currency = restaurantChainConfigProperties.getChain() != null ? restaurantChainConfigProperties.getChain().getCurrency() : null;

    // Count menus for this item
    long menuCount = categoryItemMappingRepository.countDistinctMenusByItemId(item.getId());

    // Build response with all translations
    ItemResponse response = ItemResponse.builder()
            .id(item.getId())
            .itemCode(item.getItemCode())
            .basePrice(item.getBasePrice() != null ? CurrencyFormatter.formatAmount(BigDecimal.valueOf(item.getBasePrice()), currency).doubleValue() : null)
            .imageUrl(presignedUrl)
            .outOfStock(item.getOutOfStock())
            .status(item.getStatus())
            .dietaryPreference(item.getDietaryPreference())
            .itemOrderType(item.getItemOrderType())
            .alcoholType(item.getAlcoholType())
            .hasModifierAssigned(item.getHasModifierAssigned()) 
            .isDeleted(item.getIsDeleted())
            .createdAt(item.getCreatedAt() != null ? item.getCreatedAt().toLocalDateTime() : null)
            .createdBy(createdByName)
            .updatedAt(item.getUpdatedAt() != null ? item.getUpdatedAt().toLocalDateTime() : null)
            .updatedBy(updatedByName)
            .translations(translationDtos)  // Now includes all translations
            .menuCount(menuCount)
            .build();

    ItemDto<ItemResponse> itemDto = ItemDto.<ItemResponse>builder()
            .item(response)
            .build();

    return ResponseDto.<ItemDto<ItemResponse>>builder()
            .message(messageUtil.getMessage(msgItemGetSuccess, userLocale))
            .data(itemDto)
            .build();
}

    // Modifier group assignment methods
    /**
     * Assigns modifier groups to an item.
     * Creates ItemModifierGroupMapping records for each modifier group in the request.
     *
     * @param request the assignment request with item ID and modifier group IDs
     * @return ResponseDto containing list of assigned modifier groups
     * @throws ResponseStatusException if item not found or modifier group not found
     */
    @Override
    @Transactional
    public ResponseDto<ModifierGroupAssignmentListResponse> assignModifierGroupsToItem(AssignModifierGroupsRequest request) {
        log.info("Assigning modifier groups to item: {}", request.getItemId());

        // Validate item exists
        Item item = itemRepository.findById(request.getItemId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                    messageUtil.getMessage(msgItemUpdateErrorNotFound, 
                        LocaleContextHolder.getLocale(), request.getItemId())));

        // Check if item is deleted
        if (item.getIsDeleted()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                messageUtil.getMessage("item.modifier.assignment.error.item.deleted", 
                    LocaleContextHolder.getLocale()));
        }

        List<ModifierGroupAssignmentListResponse.AssignedModifierGroup> assignedModifierGroups = new ArrayList<>();

        for (AssignModifierGroupsRequest.ModifierGroupAssignment assignment : request.getModifierGroups()) {
            try {
                // Validate modifier group exists
                ModifierGroup modifierGroup = modifierGroupRepository.findByIdAndIsDeletedFalse(assignment.getModifierGroupId())
                        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                            messageUtil.getMessage("modifier.group.not.found",
                                LocaleContextHolder.getLocale())));

                // Check if assignment already exists
                if (itemModifierGroupRepository.existsByItemIdAndModifierGroupIdAndIsDeletedFalse(request.getItemId(), assignment.getModifierGroupId())) {
                    throw new ResponseStatusException(HttpStatus.CONFLICT,
                        messageUtil.getMessage("item.modifier.assignment.error.already.assigned", 
                            LocaleContextHolder.getLocale(), assignment.getModifierGroupId()));
                }

                // Validate min and max modifier items in the modifier group
                List<ModifierItem> modifierItems = modifierItemRepository.findByModifierGroup_IdAndIsDeletedFalse(modifierGroup.getId());
                int activeModifierItemsCount = (int) modifierItems.stream()
                        .filter(modifierItem -> EntityStatus.ACTIVE.equals(modifierItem.getStatus()))
                        .count();

                // Check minimum limit
                if (modifierGroup.getMinLimit() != null && activeModifierItemsCount < modifierGroup.getMinLimit()) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        messageUtil.getMessage("item.modifier.assignment.error.min.items.not.met", 
                            LocaleContextHolder.getLocale(), modifierGroup.getMinLimit(), activeModifierItemsCount));
                }

                // Validate that for SUBSTITUTE type modifiers, at least one modifier item must be marked as default
                if (modifierGroup.getModifierType() == ModifierType.SUBSTITUTE) {
                    boolean hasDefaultItem = modifierItems.stream()
                            .filter(modifierItem -> EntityStatus.ACTIVE.equals(modifierItem.getStatus()))
                            .anyMatch(modifierItem -> Boolean.TRUE.equals(modifierItem.getIsDefault()));
                    
                    if (!hasDefaultItem) {
                        throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                            messageUtil.getMessage("item.modifier.assignment.error.substitute.requires.default", 
                                LocaleContextHolder.getLocale()));
                    }
                }

                // Create new assignment
                ItemModifierGroup itemModifierGroup = ItemModifierGroup.builder()
                        .item(item)
                        .modifierGroup(modifierGroup)
                        .sortOrder(assignment.getSortOrder())
                        .isDeleted(false)
                        .build();

                ItemModifierGroup savedAssignment = itemModifierGroupRepository.save(itemModifierGroup);
                item.setHasModifierAssigned(true);
                itemRepository.save(item);

                // Get modifier group name and description (you might want to fetch from translation)
                String modifierGroupName = "Modifier Group"; // You can fetch from translation
                String modifierGroupDescription = "Description"; // You can fetch from translation

                // Add to successful assignments
                assignedModifierGroups.add(new ModifierGroupAssignmentListResponse.AssignedModifierGroup(
                        savedAssignment.getModifierGroup().getId(),
                        modifierGroupName,
                        modifierGroupDescription,
                        savedAssignment.getSortOrder(),
                        200
                ));

                log.info("Successfully assigned modifier group {} to item {}", modifierGroup.getId(), item.getId());

            } catch (ResponseStatusException e) {
                // Re-throw ResponseStatusException as is
                throw e;
            } catch (DataIntegrityViolationException e) {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                    messageUtil.getMessage("item.modifier.assignment.error.already.assigned",
                        LocaleContextHolder.getLocale(), assignment.getModifierGroupId()), e);
            } catch (Exception e) {
                log.error("Unexpected error while assigning modifier group {} to item {}: {}", 
                    assignment.getModifierGroupId(), request.getItemId(), e.getMessage(), e);
            
                throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    messageUtil.getMessage("item.modifier.assignment.error.unexpected",
                        LocaleContextHolder.getLocale()), e);
            }
        }

        ModifierGroupAssignmentListResponse response = ModifierGroupAssignmentListResponse.builder()
                .itemId(item.getId())
                .assignedModifierGroups(assignedModifierGroups)
                .count((long) assignedModifierGroups.size())
                .total((long) assignedModifierGroups.size())
                .build();

        return ResponseDto.<ModifierGroupAssignmentListResponse>builder()
                .data(response)
                .message(messageUtil.getMessage("item.modifier.assignment.success", LocaleContextHolder.getLocale()))
                .build();
    }

    /**
     * Unassigns a modifier group from an item.
     * Deletes the ItemModifierGroupMapping record.
     *
     * @param itemId       the UUID of the item
     * @param modifierGroupId the UUID of the modifier group to unassign
     * @param updaterId    the ID of the user performing the unassignment
     * @param updaterRole  the role of the user performing the unassignment
     * @return ResponseDto containing list of remaining modifier group assignments
     * @throws ResponseStatusException if item not found or modifier group not assigned
     */
    @Override
    @Transactional
    public ResponseDto<ModifierGroupAssignmentListResponse> unassignModifierGroupFromItem(UUID itemId, UUID modifierGroupId, String updaterId, String updaterRole) {
        log.info("Unassigning modifier group from item: {}", itemId);

        // Validate item exists
        Item item = itemRepository.findById(itemId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                    messageUtil.getMessage("item.name.not.found",
                        LocaleContextHolder.getLocale())));

        // Check if item is deleted
        if (item.getIsDeleted()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                messageUtil.getMessage("item.modifier.unassignment.error.item.deleted", 
                    LocaleContextHolder.getLocale()));
        }

        try {
            // Validate modifier group exists
            ModifierGroup modifierGroup = modifierGroupRepository.findByIdAndIsDeletedFalse(modifierGroupId)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        messageUtil.getMessage("modifier.group.not.found",
                            LocaleContextHolder.getLocale())));

            // Find existing assignment
            ItemModifierGroup existingAssignment = itemModifierGroupRepository
                    .findByItemIdAndModifierGroupIdAndIsDeletedFalse(itemId, modifierGroupId)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        messageUtil.getMessage("modifier.group.not.assigned.to.item", 
                            LocaleContextHolder.getLocale(), modifierGroupId)));

            // Soft delete the assignment
            existingAssignment.setIsDeleted(true);
            itemModifierGroupRepository.save(existingAssignment);
            
            // Check if there are any remaining active assignments and update hasModifierAssigned
            boolean hasRemainingAssignments = hasActiveModifierGroupAssignments(itemId);
            item.setHasModifierAssigned(hasRemainingAssignments);
            itemRepository.save(item);

            // Get modifier group name and description
            String modifierGroupName = "Modifier Group"; // You can fetch from translation
            String modifierGroupDescription = "Description"; // You can fetch from translation

            // Create response with single unassigned modifier group
            ModifierGroupAssignmentListResponse.AssignedModifierGroup unassignedModifierGroup = 
                new ModifierGroupAssignmentListResponse.AssignedModifierGroup(
                    modifierGroup.getId(),
                    modifierGroupName,
                    modifierGroupDescription,
                    existingAssignment.getSortOrder(),
                    200
                );

            List<ModifierGroupAssignmentListResponse.AssignedModifierGroup> unassignedModifierGroups = 
                Arrays.asList(unassignedModifierGroup);

            ModifierGroupAssignmentListResponse response = ModifierGroupAssignmentListResponse.builder()
                    .itemId(item.getId())
                    .assignedModifierGroups(unassignedModifierGroups)
                    .build();

            log.info("Successfully unassigned modifier group {} from item {}", modifierGroup.getId(), item.getId());

            return ResponseDto.<ModifierGroupAssignmentListResponse>builder()
                    .data(response)
                    .message(messageUtil.getMessage("item.modifier.unassignment.success", LocaleContextHolder.getLocale()))
                    .build();

        } catch (ResponseStatusException e) {
            // Re-throw ResponseStatusException as is
            throw e;
        } catch (Exception e) {
            log.error("Failed to unassign modifier group {} from item {}: {}", modifierGroupId, itemId, e.getMessage(), e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                messageUtil.getMessage("item.modifier.unassignment.error.failed", 
                    LocaleContextHolder.getLocale(), modifierGroupId) + ": " + e.getMessage(), e);
        }
    }


    /**
     * Retrieves an item with its associated modifier groups and modifier items.
     * Returns paginated list of modifier groups with their modifier items.
     *
     * @param page   page number for pagination
     * @param size   page size for pagination
     * @param itemId the UUID of the item
     * @param locale locale code for localized responses
     * @return ResponseDto containing item details with paginated modifier groups and items
     * @throws ResponseStatusException if item not found
     */
@Override
@Transactional
public ResponseDto<ItemModifierItemListResponse> getItemWithModifiersItems(Integer page, Integer size, UUID itemId, String locale) {
    Locale userLocale = Locale.forLanguageTag(locale);

    // 1️⃣ Validate item
    Item item = itemRepository.findById(itemId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                    messageUtil.getMessage(msgItemNotFound, userLocale, itemId)));

    if (Boolean.TRUE.equals(item.getIsDeleted())) {
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                messageUtil.getMessage(msgItemErrorDeleted, userLocale));
    }

    // 2️⃣ Validate and set pagination
    int pageNumber = (page != null ? page : 1) - 1;
    if (pageNumber < 0) pageNumber = 0;
    int pageSize = size != null ? size : 10; // default page size
    if (pageSize < 1) pageSize = 10;

    // 3️⃣ Item translations with fallback
    List<ItemTranslation> itemTranslations = itemTranslationRepository.findAllByItemId(item.getId());
    List<ItemTranslationDto> translationDtos = new ArrayList<>();
    
    if (!itemTranslations.isEmpty()) {
        // Try exact match first
        ItemTranslation exactMatch = itemTranslations.stream()
                .filter(t -> t.getLanguageCode() != null && t.getLanguageCode().equals(locale))
                .findFirst()
                .orElse(null);
        
        if (exactMatch != null) {
            // Use exact match
            translationDtos.add(ItemTranslationDto.builder()
                    .languageCode(exactMatch.getLanguageCode())
                    .name(exactMatch.getName())
                    .description(exactMatch.getDescription())
                    .build());
        } else {
            // First try default language from restaurant chain config
            String defaultLanguageCode = (restaurantChainConfigProperties.getChain() != null 
                    && restaurantChainConfigProperties.getChain().getDefaultLanguageCode() != null)
                    ? restaurantChainConfigProperties.getChain().getDefaultLanguageCode()
                    : null;
            
            ItemTranslation fallbackTranslation = null;
            if (defaultLanguageCode != null && !defaultLanguageCode.equals(locale)) {
                fallbackTranslation = itemTranslations.stream()
                        .filter(t -> t.getLanguageCode() != null && t.getLanguageCode().equalsIgnoreCase(defaultLanguageCode))
                        .findFirst()
                        .orElse(null);
            }
            
            // If still not found, use TranslationUtils with ordered languages
            if (fallbackTranslation == null) {
                java.util.Optional<ItemTranslation> fallback =
                        TranslationUtils.pickPreferredOrFromList(
                                itemTranslations,
                                locale,
                                localizationProperties.getLanguages(),
                                ItemTranslation::getLanguageCode
                        );
                fallbackTranslation = fallback.orElse(null);
            }
            
            if (fallbackTranslation != null) {
                translationDtos.add(ItemTranslationDto.builder()
                        .languageCode(fallbackTranslation.getLanguageCode())
                        .name(fallbackTranslation.getName())
                        .description(fallbackTranslation.getDescription())
                        .build());
            }
        }
    }

    // 3️⃣.a Get currency for formatting prices
    String currency = restaurantChainConfigProperties.getChain() != null ? restaurantChainConfigProperties.getChain().getCurrency() : null;

    // 4️⃣ Modifier groups with items
    List<ModifierGroupWithItemsResponse> modifierDetails = itemModifierGroupRepository
            .findByItemIdAndIsDeletedFalse(itemId)
            .stream()
            .map(img -> {
                ModifierGroup group = img.getModifierGroup();
                
                // Skip INACTIVE modifier groups
                if (group.getStatus() == EntityStatus.INACTIVE) {
                    return null;
                }

                // Build ModifierGroupResponse with translations based on locale
                ModifierGroupResponse groupResponse = ModifierGroupResponse.builder()
                        .id(group.getId())
                        .modifierType(group.getModifierType())
                        .allowMultiSelect(group.getAllowMultiSelect())
                        .minLimit(group.getMinLimit())
                        .maxLimit(group.getMaxLimit())
                        .status(group.getStatus())
                        .isDeleted(group.getIsDeleted())
                        .translations(
                                group.getTranslations().stream()
                                        .filter(t -> t.getLanguageCode().equalsIgnoreCase(locale))
                                        .map(t -> ModifierGroupTranslationDto.builder()
                                                .languageCode(t.getLanguageCode())
                                                .name(t.getName())
                                                .description(t.getDescription())
                                                .build())
                                        .toList()
                        )
                        .build();

                // Items inside group - sorted by sortOrder
                List<ModifierItemListResponseDto> modifierItems = modifierItemRepository
                        .findByModifierGroup_IdAndIsDeletedFalse(group.getId())
                        .stream()
                        .filter(modifierItem -> modifierItem.getStatus() == EntityStatus.ACTIVE) // Filter out INACTIVE items
                        .sorted((a, b) -> {
                            // Handle null sortOrder values
                            Integer sortA = a.getSortOrder() != null ? a.getSortOrder() : Integer.MAX_VALUE;
                            Integer sortB = b.getSortOrder() != null ? b.getSortOrder() : Integer.MAX_VALUE;
                            return sortA.compareTo(sortB);
                        })
                        .map(modifierItem -> {
                            // Get localized name and description
                            String localizedName = null;
                            String localizedDescription = null;
                            ModifierItemTranslation translation = modifierItem.getTranslations().stream()
                                    .filter(t -> t.getLanguageCode().equalsIgnoreCase(locale))
                                    .findFirst()
                                    .orElse(null);

                            if (translation != null) {
                                localizedName = translation.getName();
                                localizedDescription = translation.getDescription();
                            } else {
                                // Fallback using ordered config languages
                                java.util.Optional<ModifierItemTranslation> fallback =
                                        TranslationUtils.pickPreferredOrFromList(
                                                modifierItem.getTranslations(),
                                                locale,
                                                localizationProperties.getLanguages(),
                                                ModifierItemTranslation::getLanguageCode
                                        );
                                if (fallback.isPresent()) {
                                    translation = fallback.get();
                                    localizedName = translation.getName();
                                    localizedDescription = translation.getDescription();
                                }
                            }

                            return ModifierItemListResponseDto.builder()
                                    .id(modifierItem.getId())
                                    .modifierGroupId(group.getId())
                                    .modifierCode(modifierItem.getModifierCode())
                                    .imageUrl(modifierItem.getImageUrl() != null && !modifierItem.getImageUrl().isEmpty() ? awsService.getPreSignedUrl(modifierItem.getImageUrl()) : null)
                                    .price(modifierItem.getPrice() != null ? CurrencyFormatter.formatAmount(modifierItem.getPrice(), currency) : null)
                                    .sortOrder(modifierItem.getSortOrder())
                                    .isDefault(modifierItem.getIsDefault())
                                    .status(modifierItem.getStatus())
                                    .isDeleted(modifierItem.getIsDeleted())
                                    .createdAt(modifierItem.getCreatedAt() != null ? modifierItem.getCreatedAt().toLocalDateTime() : null)
                                    .updatedAt(modifierItem.getUpdatedAt() != null ? modifierItem.getUpdatedAt().toLocalDateTime() : null)
                                    .createdBy(modifierItem.getCreatedBy() != null ? modifierItem.getCreatedBy().getFirstName() : null)
                                    .updatedBy(modifierItem.getUpdatedBy() != null ? modifierItem.getUpdatedBy().getFirstName() : null)
                                    .name(localizedName)
                                    .description(localizedDescription)
                                    .build();
                        })
                        .toList();

                return ModifierGroupWithItemsResponse.builder()
                        .modifierGroup(groupResponse)
                        .modifierItems(modifierItems)
                        .build();
            })
            .filter(Objects::nonNull) // Remove null responses (INACTIVE groups)
            .toList();

    // 5️⃣ Apply safe pagination
    int fromIndex = pageNumber * pageSize;
    List<ModifierGroupWithItemsResponse> paginatedModifierDetails;
    if (fromIndex >= modifierDetails.size()) {
        paginatedModifierDetails = Collections.emptyList();
    } else {
        int toIndex = Math.min(fromIndex + pageSize, modifierDetails.size());
        paginatedModifierDetails = modifierDetails.subList(fromIndex, toIndex);
    }

    // 6️⃣ Build ItemResponseWithModifier
    String currencyForModifier = restaurantChainConfigProperties.getChain() != null ? restaurantChainConfigProperties.getChain().getCurrency() : null;
    ItemResponseWithModifier itemResponse = ItemResponseWithModifier.builder()
            .id(item.getId())
            .itemCode(item.getItemCode())
            .basePrice(item.getBasePrice() != null ? CurrencyFormatter.formatAmount(BigDecimal.valueOf(item.getBasePrice()), currencyForModifier).doubleValue() : null)
            .imageUrl(item.getImageUrl() != null && !item.getImageUrl().isEmpty() ? awsService.getPreSignedUrl(item.getImageUrl()) : null)
            .outOfStock(item.getOutOfStock())
            .status(item.getStatus())
            .dietaryPreference(item.getDietaryPreference())
            .hasModifierAssigned(Boolean.TRUE.equals(item.getHasModifierAssigned()))
            .isDeleted(item.getIsDeleted())
            .createdAt(item.getCreatedAt() != null ? item.getCreatedAt().toLocalDateTime() : null)
            .updatedAt(item.getUpdatedAt() != null ? item.getUpdatedAt().toLocalDateTime() : null)
            .createdBy(item.getCreatedBy() != null ? item.getCreatedBy().getFirstName() : null)
            .updatedBy(item.getUpdatedBy() != null ? item.getUpdatedBy().getFirstName() : null)
            .translations(translationDtos)
            .modifierDetails(paginatedModifierDetails)
            .build();

    // 7️⃣ Build pagination metadata
    PaginationMetaData paginationMetaData = PaginationMetaData.builder()
            .page(pageNumber + 1)
            .size(pageSize)
            .totalPages((int) Math.ceil((double) modifierDetails.size() / pageSize))
            .totalRecords((long) modifierDetails.size())
            .build();

    // 8️⃣ Build Response DTO
    ItemModifierItemListResponse response = ItemModifierItemListResponse.builder()
            .item(itemResponse)
            .count((long) paginatedModifierDetails.size())
            .total((long) modifierDetails.size())
            .metaData(paginationMetaData)
            .build();

    return ResponseDto.<ItemModifierItemListResponse>builder()
            .message(messageUtil.getMessage(msgItemGetSuccess, userLocale))
            .data(response)
            .build();
}

    /**
     * Retrieves an item with enhanced modifier details considering restaurant, menu, and promotion context.
     * Includes item availability, discount information, and promotion-specific pricing.
     * Returns modifier groups and items with context-aware information.
     *
     * @param itemId       the UUID of the item
     * @param restaurantId the UUID of the restaurant (for availability and discount checks)
     * @param menuId       the UUID of the menu (for discount checks)
     * @param locale       locale code for localized responses
     * @param promotionId  optional UUID of promotion (for promotion-specific pricing)
     * @return ResponseDto containing enhanced item details with modifiers and discount information
     * @throws ResponseStatusException if item not found
     */
@Override
@Transactional
public ResponseDto<ItemModifierItemListResponseEnhanced> getItemWithModifiersItemsEnhanced(
        UUID itemId, UUID restaurantId, UUID menuId, String locale, UUID promotionId) {
    
    Locale userLocale = Locale.forLanguageTag(locale);

    // 1️⃣ Validate item
    Item item = itemRepository.findById(itemId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                    messageUtil.getMessage(msgItemNotFound, userLocale, itemId)));

    if (Boolean.TRUE.equals(item.getIsDeleted())) {
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                messageUtil.getMessage(msgItemErrorDeleted, userLocale));
    }

    // 2️⃣ Item translations with fallback
    List<ItemTranslation> itemTranslations = itemTranslationRepository.findAllByItemId(item.getId());
    List<ItemTranslationDto> translationDtos = new ArrayList<>();
    
    if (!itemTranslations.isEmpty()) {
        // Try exact match first
        ItemTranslation exactMatch = itemTranslations.stream()
                .filter(t -> t.getLanguageCode() != null && t.getLanguageCode().equals(locale))
                .findFirst()
                .orElse(null);
        
        if (exactMatch != null) {
            // Use exact match
            translationDtos.add(ItemTranslationDto.builder()
                    .languageCode(exactMatch.getLanguageCode())
                    .name(exactMatch.getName())
                    .description(exactMatch.getDescription())
                    .build());
        } else {
            // Get ordered languages from application.properties (excluding requested locale)
            List<String> fallbackLanguages = localizationProperties.getLanguages().stream()
                    .filter(lang -> lang != null && !lang.equalsIgnoreCase(locale))
                    .collect(Collectors.toList());
            
            // Iterate through fallback languages in order from properties file
            ItemTranslation fallbackTranslation = null;
            for (String fallbackLang : fallbackLanguages) {
                fallbackTranslation = itemTranslations.stream()
                        .filter(t -> t.getLanguageCode() != null && t.getLanguageCode().equalsIgnoreCase(fallbackLang))
                        .findFirst()
                        .orElse(null);
                if (fallbackTranslation != null) {
                    break; // Found a translation, stop searching
                }
            }
            
            if (fallbackTranslation != null) {
                translationDtos.add(ItemTranslationDto.builder()
                        .languageCode(fallbackTranslation.getLanguageCode())
                        .name(fallbackTranslation.getName())
                        .description(fallbackTranslation.getDescription())
                        .build());
            }
        }
    }

    // 3️⃣ Calculate discount and availability (with price override support)
    DiscountCalculationResult discountResult = calculateItemDiscount(
            menuId, itemId, 1, restaurantId, promotionId, userLocale);
    Boolean isAvailable = checkItemAvailability(restaurantId, itemId, menuId);

    String currency = restaurantChainConfigProperties.getChain() != null ? restaurantChainConfigProperties.getChain().getCurrency() : null;

    // Determine BXGY discount details for this item in the given menu
    // Use CategoryItemMapping IDs to match the logic used in the list endpoint
    boolean isBxgyItem = false;
    boolean isBxgyBuyItem = false;
    Integer buyQuantity = null;
    Integer getQuantity = null;
    UUID discountId = null;
    Discount bxgyDiscount = null;

    log.info("Checking BXGY discounts for item {} in menu {}", itemId, menuId);
    
    // Get ALL CategoryItemMappings for this item that belong to the menu
    // This ensures we find BXGY discounts only where the CategoryItemMapping is actually in discount_bxgy_item table
    List<MenuCategoryMapping> allMenuCategoryMappings = menuCategoryMappingRepository.findByMenuId(menuId);
    List<CategoryItemMapping> itemMappings = categoryItemMappingRepository.findByMenuCategoryMappingIn(allMenuCategoryMappings)
        .stream()
        .filter(mapping -> mapping.getItem().getId().equals(itemId))
        .collect(Collectors.toList());
    
    log.info("Found {} item mappings for item {} in menu {}", itemMappings.size(), itemId, menuId);
    
    if (!itemMappings.isEmpty()) {
        // Extract CategoryItemMapping IDs for this item in the menu
        List<UUID> categoryItemMappingIds = itemMappings.stream()
                .map(CategoryItemMapping::getId)
                .collect(Collectors.toList());
        
        // Query buy items by CategoryItemMapping IDs - only finds discounts where the mapping ID is in discount_bxgy_item
        List<DiscountBxgyItem> buyItems = discountBxgyItemRepository.findByBuyItemMappingIdsAndMenuId(
                categoryItemMappingIds, menuId, DiscountType.BXGY, EntityStatus.ACTIVE);
        log.info("Found {} buy items for BXGY discount for item {} using CategoryItemMapping IDs", buyItems.size(), itemId);
        
        for (DiscountBxgyItem bxgy : buyItems) {
            Discount discount = bxgy.getDiscount();
            if (discount != null) {
                log.info("Checking if discount {} is active for menu {} and restaurant {}", discount.getId(), menuId, restaurantId);
                boolean isActive = isDiscountActive(discount, menuId, restaurantId);
                log.info("Discount {} isActive: {}", discount.getId(), isActive);
                if (isActive) {
                    isBxgyItem = true;
                    isBxgyBuyItem = true;
                    bxgyDiscount = discount;
                    buyQuantity = discount.getBuyQuantity();
                    getQuantity = discount.getGetQuantity();
                    discountId = discount.getId();
                    log.info("Item {} is a BXGY buy item with discount {} using CategoryItemMapping {}", itemId, discountId, bxgy.getBuyItemMapping().getId());
                    break;
                }
            }
        }
        
        // Query get items by CategoryItemMapping IDs - only finds discounts where the mapping ID is in discount_bxgy_item
        if (!isBxgyBuyItem || bxgyDiscount == null) {
            List<DiscountBxgyItem> getItems = discountBxgyItemRepository.findByGetItemMappingIdsAndMenuId(
                    categoryItemMappingIds, menuId, DiscountType.BXGY, EntityStatus.ACTIVE);
            log.info("Found {} get items for BXGY discount for item {} using CategoryItemMapping IDs", getItems.size(), itemId);
            
            for (DiscountBxgyItem bxgy : getItems) {
                Discount discount = bxgy.getDiscount();
                if (discount != null) {
                    log.info("Checking if discount {} is active for menu {} and restaurant {}", discount.getId(), menuId, restaurantId);
                    boolean isActive = isDiscountActive(discount, menuId, restaurantId);
                    log.info("Discount {} isActive: {}", discount.getId(), isActive);
                    if (isActive) {
                        isBxgyItem = true;
                        isBxgyBuyItem = false; // Item is a GET item, not a BUY item
                        // Only set if not already set from buy item check
                        if (bxgyDiscount == null) {
                            bxgyDiscount = discount;
                            buyQuantity = discount.getBuyQuantity();
                            getQuantity = discount.getGetQuantity();
                            discountId = discount.getId();
                        }
                        log.info("Item {} is a BXGY get item with discount {} using CategoryItemMapping {}", itemId, discountId, bxgy.getGetItemMapping().getId());
                        break;
                    }
                }
            }
        }
    }

    // 4️⃣ Modifier groups with items (same logic as original but without pagination)
    List<ModifierGroupWithItemsResponse> modifierDetails = itemModifierGroupRepository
        .findByItemIdAndIsDeletedFalse(itemId)
        .stream()
        .map(img -> {
            ModifierGroup group = img.getModifierGroup();
            
            // Skip DELETED or INACTIVE modifier groups
            if (Boolean.TRUE.equals(group.getIsDeleted()) || group.getStatus() == EntityStatus.INACTIVE) {
                return null;
            }

            // Build ModifierGroupResponse with translations based on locale
            ModifierGroupResponse groupResponse = ModifierGroupResponse.builder()
                    .id(group.getId())
                    .modifierType(group.getModifierType())
                    .allowMultiSelect(group.getAllowMultiSelect())
                    .minLimit(group.getMinLimit())
                    .maxLimit(group.getMaxLimit())
                    .status(group.getStatus())
                    .isDeleted(group.getIsDeleted())
                    .translations(
                            group.getTranslations().stream()
                                    .filter(t -> t.getLanguageCode().equalsIgnoreCase(locale))
                                    .map(t -> ModifierGroupTranslationDto.builder()
                                            .languageCode(t.getLanguageCode())
                                            .name(t.getName())
                                            .description(t.getDescription())
                                            .build())
                                    .toList()
                    )
                    .build();

            // Items inside group - sorted by sortOrder
            List<ModifierItemListResponseDto> modifierItems = group.getModifierItems()
                    .stream()
                    .filter(mi -> !Boolean.TRUE.equals(mi.getIsDeleted()) && mi.getStatus() == EntityStatus.ACTIVE)
                    .sorted(Comparator.comparing(ModifierItem::getSortOrder, Comparator.nullsLast(Comparator.naturalOrder())))
                    .map(modifierItem -> {
                        // Find localized name and description from translations
                        String localizedName = null;
                        String localizedDescription = null;

                        // First try to find the specific locale
                        for (ModifierItemTranslation translation : modifierItem.getTranslations()) {
                            if (translation.getLanguageCode().equalsIgnoreCase(locale)) {
                                localizedName = translation.getName();
                                localizedDescription = translation.getDescription();
                                break;
                            }
                        }

                        // If no translation found for the requested locale, use only config's first language
                        if (localizedName == null && !modifierItem.getTranslations().isEmpty()) {
                            String configuredDefaultLang = (localizationProperties.getLanguages() != null && !localizationProperties.getLanguages().isEmpty())
                                    ? localizationProperties.getLanguages().get(0)
                                    : null;
                            if (configuredDefaultLang != null) {
                                java.util.Optional<ModifierItemTranslation> cfg = modifierItem.getTranslations().stream()
                                        .filter(t -> configuredDefaultLang.equalsIgnoreCase(t.getLanguageCode()))
                                        .findFirst();
                                if (cfg.isPresent()) {
                                    ModifierItemTranslation firstTranslation = cfg.get();
                                    localizedName = firstTranslation.getName();
                                    localizedDescription = firstTranslation.getDescription();
                                }
                            }
                        }

                        // Fallback to empty strings if no translations exist
                        if (localizedName == null) {
                            localizedName = "";
                        }
                        if (localizedDescription == null) {
                            localizedDescription = "";
                        }

                        return ModifierItemListResponseDto.builder()
                                .id(modifierItem.getId())
                                .modifierGroupId(group.getId())
                                .modifierCode(modifierItem.getModifierCode())
                                .imageUrl(modifierItem.getImageUrl() != null && !modifierItem.getImageUrl().isEmpty() ? awsService.getPreSignedUrl(modifierItem.getImageUrl()) : null)
                                .price(modifierItem.getPrice() != null ? CurrencyFormatter.formatAmount(modifierItem.getPrice(), currency) : null)
                                .sortOrder(modifierItem.getSortOrder())
                                .isDefault(modifierItem.getIsDefault())
                                .status(modifierItem.getStatus())
                                .isDeleted(modifierItem.getIsDeleted())
                                .createdAt(modifierItem.getCreatedAt() != null ? modifierItem.getCreatedAt().toLocalDateTime() : null)
                                .updatedAt(modifierItem.getUpdatedAt() != null ? modifierItem.getUpdatedAt().toLocalDateTime() : null)
                                .createdBy(modifierItem.getCreatedBy() != null ? modifierItem.getCreatedBy().getFirstName() : null)
                                .updatedBy(modifierItem.getUpdatedBy() != null ? modifierItem.getUpdatedBy().getFirstName() : null)
                                .name(localizedName)
                                .description(localizedDescription)
                                .build();
                    })
                    .toList();

            return ModifierGroupWithItemsResponse.builder()
                    .modifierGroup(groupResponse)
                    .modifierItems(modifierItems)
                    .build();
        })
        .filter(Objects::nonNull) // Remove null responses (DELETED or INACTIVE groups)
        .toList();

    // 5️⃣ Build enhanced ItemResponseWithModifier
    // Use the overridden base price (effectiveBasePrice) from discount result
    ItemResponseWithModifierEnhanced itemResponse = ItemResponseWithModifierEnhanced.builder()
            .id(item.getId())
            .itemCode(item.getItemCode())
            .basePrice(CurrencyFormatter.formatAmount(discountResult.getOriginalPrice(), currency).doubleValue()) // This is the effective base price after override, formatted based on currency
            .discountedPrice(discountResult.getFinalPrice() != null ? CurrencyFormatter.formatAmount(discountResult.getFinalPrice(), currency) : null)
            .discountAmount(discountResult.getDiscountAmount() != null ? CurrencyFormatter.formatAmount(discountResult.getDiscountAmount(), currency) : null)
            .discountDetail(generateDiscountDetail(discountResult, userLocale)) // Prefer precomputed label
            .isAvailable(isAvailable)
            .imageUrl(item.getImageUrl() != null && !item.getImageUrl().isEmpty() ? 
                awsService.getPreSignedUrl(item.getImageUrl()) : null)
            .outOfStock(item.getOutOfStock())
            .status(item.getStatus())
            .dietaryPreference(item.getDietaryPreference())
            .alcoholType(item.getAlcoholType())
            .hasModifierAssigned(Boolean.TRUE.equals(item.getHasModifierAssigned()))
            .isDeleted(item.getIsDeleted())
            .createdAt(item.getCreatedAt() != null ? item.getCreatedAt().toLocalDateTime() : null)
            .updatedAt(item.getUpdatedAt() != null ? item.getUpdatedAt().toLocalDateTime() : null)
            .createdBy(item.getCreatedBy() != null ? item.getCreatedBy().getFirstName() : null)
            .updatedBy(item.getUpdatedBy() != null ? item.getUpdatedBy().getFirstName() : null)
            .translations(translationDtos)
            .modifierDetails(modifierDetails)
            .build();

    // 5️⃣.a Attach BXGY details (if any) via setters
    itemResponse.setIsBxgyBuyItem(isBxgyBuyItem);
    itemResponse.setBuyQuantity(buyQuantity);
    itemResponse.setGetQuantity(getQuantity);
    itemResponse.setDiscountId(discountId);
    
    // 5️⃣.b Fetch allowCookingRequest from chain configuration and set it
    Boolean allowCookingRequest = restaurantChainConfigProperties.getChain() != null 
            && restaurantChainConfigProperties.getChain().isAllowCookingRequest();
    itemResponse.setAllowCookingRequest(allowCookingRequest);

    // 6️⃣ Build Enhanced Response DTO
    ItemModifierItemListResponseEnhanced response = ItemModifierItemListResponseEnhanced.builder()
            .item(itemResponse)
            .build();

    return ResponseDto.<ItemModifierItemListResponseEnhanced>builder()
            .data(response)
            .message(messageUtil.getMessage(msgItemGetSuccess, userLocale))
            .build();
}

// Replace the existing calculateItemDiscount method with this:
    /**
     * Calculates discount information for an item in a menu and restaurant context.
     * Checks item-level and category-level discounts, finds the best applicable discount,
     * and considers promotion discounts if provided.
     *
     * @param menuId       the UUID of the menu
     * @param itemId       the UUID of the item
     * @param quantity     the quantity of items (for BXGY calculations)
     * @param restaurantId the UUID of the restaurant
     * @param promotionId  optional UUID of promotion (for promotion-specific discounts)
     * @return DiscountCalculationResult with discount details or null if no discount applies
     */
private DiscountCalculationResult calculateItemDiscount(UUID menuId, UUID itemId, Integer quantity,
        UUID restaurantId, UUID promotionId, Locale userLocale) {
    
    Item item = itemRepository.findById(itemId).orElse(null);
    if (item == null) {
        return new DiscountCalculationResult(BigDecimal.ZERO, null, null, null, null);
    }
    
    Double basePrice = item.getBasePrice();
    
    if (basePrice == null || basePrice <= 0) {
        return new DiscountCalculationResult(
            BigDecimal.valueOf(basePrice != null ? basePrice : 0.0), 
            null, 
            null, 
            null,
            null
        );
    }
    
    // Get menu category mappings for this menu
    List<MenuCategoryMapping> allMenuCategoryMappings = menuCategoryMappingRepository.findByMenuId(menuId);
    
    if (allMenuCategoryMappings.isEmpty()) {
        return new DiscountCalculationResult(
            BigDecimal.valueOf(basePrice), 
            null, 
            null, 
            null,
            null
        );
    }
    
    // Get only the MenuCategoryMappings that contain this specific item
    // This is needed for correct price override resolution
    List<CategoryItemMapping> itemMappings = categoryItemMappingRepository.findByMenuCategoryMappingIn(allMenuCategoryMappings)
        .stream()
        .filter(mapping -> mapping.getItem().getId().equals(item.getId()))
        .collect(Collectors.toList());
    
    List<MenuCategoryMapping> itemSpecificMcms = itemMappings.stream()
        .map(CategoryItemMapping::getMenuCategoryMapping)
        .distinct()
        .collect(Collectors.toList());
    
    log.info("Item {} - Base price before override: {}", itemId, basePrice);
    log.info("Item {} - Found {} item-specific menu category mappings", itemId, itemSpecificMcms.size());
    
    // Apply price overrides FIRST before any other calculations
    // Use item-specific MenuCategoryMappings to correctly identify item's categories
    Double effectiveBasePrice = basePrice;
    if (restaurantId != null && !itemSpecificMcms.isEmpty()) {
        PriceOverrideHelper.ActiveOverrideIndex activeOverrideIndex = priceOverrideHelper.buildActiveOverrideIndex(restaurantId);
        effectiveBasePrice = priceOverrideHelper.resolveEffectiveBasePrice(basePrice, menuId, itemSpecificMcms, activeOverrideIndex);
        
        if (!effectiveBasePrice.equals(basePrice)) {
            log.info("Item {} - Price override APPLIED! Original: {}, After override: {}", itemId, basePrice, effectiveBasePrice);
        } else {
            log.info("Item {} - No price override found or applied", itemId);
        }
    } else {
        if (restaurantId == null) {
            log.info("Item {} - Skipping price override (restaurantId is null)", itemId);
        } else if (itemSpecificMcms.isEmpty()) {
            log.info("Item {} - Skipping price override (item not found in any category)", itemId);
        }
    }
    
    // Get and validate promotion discount if promotionId is provided
    Discount promotionDiscount = null;
    if (promotionId != null) {
        Optional<Promotion> promotionOpt = promotionRepository.findById(promotionId);
        if (promotionOpt.isPresent()) {
            Promotion promotion = promotionOpt.get();
            
            // Validate promotion status
            if (Boolean.TRUE.equals(promotion.getIsDeleted()) || promotion.getStatus() != EntityStatus.ACTIVE) {
                log.warn("Item {} - Promotion {} is not active or deleted, skipping promotion discount", itemId, promotionId);
            } else if (promotion.getDiscount() == null) {
                log.warn("Item {} - Promotion {} has no discount assigned", itemId, promotionId);
            } else {
                // Validate promotion assignment to menu
                MenuPromotionId menuPromotionId = new MenuPromotionId(menuId, promotionId);
                Optional<MenuPromotionMapping> menuPromotionMapping = menuPromotionMappingRepository.findById(menuPromotionId);
                
                if (menuPromotionMapping.isEmpty()) {
                    log.warn("Item {} - Promotion {} is not assigned to menu {}, skipping promotion discount", 
                        itemId, promotionId, menuId);
                } else {
                    // Validate promotion validity dates
                    MenuPromotionMapping mapping = menuPromotionMapping.get();
                    OffsetDateTime nowUtc = OffsetDateTime.now(ZoneOffset.UTC);
                    OffsetDateTime validFrom = mapping.getValidFrom();
                    OffsetDateTime validTo = mapping.getValidTo();
                    
                    if (validFrom != null && validTo != null) {
                        boolean isCurrentlyValid = !nowUtc.isBefore(validFrom) && !nowUtc.isAfter(validTo);
                        if (!isCurrentlyValid) {
                            log.warn("Item {} - Promotion {} is outside validity period ({} to {}), skipping promotion discount", 
                                itemId, promotionId, validFrom, validTo);
                        } else {
                            // Validate restaurant assignment if restaurantId is provided
                            if (restaurantId != null) {
                                RestaurantPromotionId restaurantPromotionId = new RestaurantPromotionId();
                                restaurantPromotionId.setRestaurantId(restaurantId);
                                restaurantPromotionId.setPromotionId(promotionId);
                                Optional<RestaurantPromotionMapping> restaurantPromotionMapping = 
                                    restaurantPromotionMappingRepository.findById(restaurantPromotionId);
                                
                                if (restaurantPromotionMapping.isEmpty() || 
                                    restaurantPromotionMapping.get().getStatus() != EntityStatus.ACTIVE) {
                                    log.warn("Item {} - Promotion {} is not active for restaurant {}, skipping promotion discount", 
                                        itemId, promotionId, restaurantId);
                                } else {
                                    // All validations passed, use promotion discount
                                    promotionDiscount = promotion.getDiscount();
                                    log.info("Item {} - Promotion {} discount validated: type={}, value={}", 
                                        itemId, promotionId, promotionDiscount.getDiscountType(), promotionDiscount.getValue());
                                }
                            } else {
                                // No restaurantId provided, use menu-level promotion
                                promotionDiscount = promotion.getDiscount();
                                log.info("Item {} - Promotion {} discount validated (menu-level): type={}, value={}", 
                                    itemId, promotionId, promotionDiscount.getDiscountType(), promotionDiscount.getValue());
                            }
                        }
                    } else {
                        // No validity dates, use promotion discount
                        promotionDiscount = promotion.getDiscount();
                        log.info("Item {} - Promotion {} discount validated (no validity dates): type={}, value={}", 
                            itemId, promotionId, promotionDiscount.getDiscountType(), promotionDiscount.getValue());
                    }
                }
            }
        } else {
            log.warn("Item {} - Promotion {} not found", itemId, promotionId);
        }
    }
    
    // Use the same logic as MenuServiceImpl with effective base price
    log.info("Item {} - Calculating discounts on effectiveBasePrice: {}", itemId, effectiveBasePrice);
    DiscountInfo discountInfo = calculateDiscountInfo(
            effectiveBasePrice, item, allMenuCategoryMappings, restaurantId, promotionDiscount, userLocale);
    
    log.info("Item {} - DiscountInfo returned: basePrice={}, discountedPrice={}, detail={}", 
        itemId, discountInfo.getBasePrice(), discountInfo.getDiscountedPrice(), discountInfo.getDiscountDetail());
    
    BigDecimal originalPrice = BigDecimal.valueOf(effectiveBasePrice);
    BigDecimal finalPrice = discountInfo.getDiscountedPrice() != null ? 
        BigDecimal.valueOf(discountInfo.getDiscountedPrice()) : null;
    
    log.info("Item {} - Final calculation: originalPrice={}, finalPrice={}, discountAmount={}", 
        itemId, originalPrice, finalPrice, finalPrice != null ? originalPrice.subtract(finalPrice) : 0);
    
    // Find the best discount to determine the level
    AppliedTo discountLevel = findDiscountLevel(menuId, itemId, allMenuCategoryMappings, effectiveBasePrice, restaurantId);
    
    return new DiscountCalculationResult(originalPrice, finalPrice, null, discountLevel, discountInfo.getDiscountDetail());
}

    /**
     * Calculates discount information for an item considering category and item-level discounts.
     * Checks promotion discounts if provided. Finds the best applicable discount.
     *
     * @param basePrice        the base price of the item
     * @param item             the item entity
     * @param mcms             list of menu category mappings for the item
     * @param restaurantId     the UUID of the restaurant
     * @param promotionDiscount optional promotion discount to consider
     * @return DiscountInfo with discount details or null if no discount applies
     */
private DiscountInfo calculateDiscountInfo(Double basePrice, Item item, List<MenuCategoryMapping> mcms,
        UUID restaurantId, Discount promotionDiscount, Locale userLocale) {
    
    if (basePrice == null || basePrice <= 0) {
        return new DiscountInfo(basePrice, null, false, false, null);
    }
    
    // Get only the CategoryItemMappings that belong to the current menu
    List<CategoryItemMapping> itemMappings = categoryItemMappingRepository.findByMenuCategoryMappingIn(mcms)
        .stream()
        .filter(mapping -> mapping.getItem().getId().equals(item.getId()))
        .collect(Collectors.toList());
    
    
    boolean isBxgyBuyItem = false;
    boolean isBxgyGetItem = false;
    String discountDetail = null;
    
    // Check BXGY discounts only for items in this menu
    for (CategoryItemMapping itemMapping : itemMappings) {
        // Check if this item is a buy item in BXGY
        List<DiscountBxgyItem> buyItems = discountBxgyItemRepository.findByBuyItemMapping(itemMapping);
        for (DiscountBxgyItem bxgy : buyItems) {
            if (isDiscountActive(bxgy.getDiscount(), itemMapping.getMenuCategoryMapping().getMenu().getId(), restaurantId)) {
                isBxgyBuyItem = true;
                discountDetail = generateBxgyDetail(bxgy.getDiscount(), userLocale);
                break;
            }
        }
        
        // Check if this item is a get item in BXGY
        List<DiscountBxgyItem> getItems = discountBxgyItemRepository.findByGetItemMapping(itemMapping);
        for (DiscountBxgyItem bxgy : getItems) {
            if (isDiscountActive(bxgy.getDiscount(), itemMapping.getMenuCategoryMapping().getMenu().getId(), restaurantId)) {
                isBxgyGetItem = true;
                discountDetail = generateBxgyDetail(bxgy.getDiscount(), userLocale);
                break;
            }
        }
    }
    
    // If item is part of BXGY, return base price only (no discountedPrice)
    if (isBxgyBuyItem || isBxgyGetItem) {
        return new DiscountInfo(basePrice, null, isBxgyBuyItem, isBxgyGetItem, discountDetail);
    }
    
    // Collect all applicable discounts (including promotion discount if provided and assigned)
    List<DiscountInfo> applicableDiscounts = new ArrayList<>();
    
    // Check if promotion discount is assigned to this item
    if (promotionDiscount != null) {
        DiscountInfo promotionDiscountInfo = findPromotionDiscountInfo(promotionDiscount, itemMappings, basePrice, restaurantId, userLocale);
        if (promotionDiscountInfo != null) {
            applicableDiscounts.add(promotionDiscountInfo);
            log.info("Promotion discount found for item {}: discountId={}, discountAmount={}", 
                item.getId(), promotionDiscount.getId(), 
                promotionDiscountInfo.getDiscountedPrice() != null ? 
                    basePrice - promotionDiscountInfo.getDiscountedPrice() : 0);
        } else {
            log.info("Promotion discount {} is not assigned to item {}, will compare with other discounts", 
                promotionDiscount.getId(), item.getId());
        }
    }
    
    // Collect all other applicable discounts
    // Get menuId from the first MenuCategoryMapping (all should have the same menuId)
    UUID menuId = !mcms.isEmpty() ? mcms.get(0).getMenu().getId() : null;
    collectApplicableDiscounts(itemMappings, basePrice, menuId, restaurantId, promotionDiscount, applicableDiscounts, userLocale);
    
    // Find the best discount (highest discount percentage/amount) from all collected discounts
    DiscountInfo bestDiscountInfo = findBestDiscount(applicableDiscounts, basePrice);
    
    if (bestDiscountInfo != null) {
        return bestDiscountInfo;
    }
    
    log.debug("No discount applied to item, returning base price: {}", basePrice);
    return new DiscountInfo(basePrice, null, false, false, null);
}
    /**
     * Finds the discount level (ITEM or CATEGORY) for an item.
     * Checks both item-level and category-level discounts and returns the level with the highest discount percentage.
     *
     * @param menuId            the UUID of the menu
     * @param itemId            the UUID of the item
     * @param mcms              list of menu category mappings for the item
     * @param effectiveBasePrice the effective base price to calculate discount percentage
     * @param restaurantId       the UUID of the restaurant
     * @return AppliedTo enum indicating discount level (ITEM or CATEGORY), or null if no discount
     */
private AppliedTo findDiscountLevel(UUID menuId, UUID itemId, List<MenuCategoryMapping> mcms, Double effectiveBasePrice, UUID restaurantId) {
    Item item = itemRepository.findById(itemId).orElse(null);
    if (item == null) return null;
    
    if (effectiveBasePrice == null || effectiveBasePrice <= 0) return null;
    
    List<CategoryItemMapping> itemMappings = categoryItemMappingRepository.findByMenuCategoryMappingIn(mcms)
        .stream()
        .filter(mapping -> mapping.getItem().getId().equals(item.getId()))
        .collect(Collectors.toList());
    
    double maxDiscountPercentage = 0.0;
    AppliedTo bestLevel = null;
    
    // Check category-level discounts
    for (CategoryItemMapping itemMapping : itemMappings) {
        MenuCategoryMapping mcm = itemMapping.getMenuCategoryMapping();
        List<CategoryDiscountMapping> categoryDiscounts = categoryDiscountMappingRepository.findByMenuCategoryMapping(mcm);
        
        for (CategoryDiscountMapping cdm : categoryDiscounts) {
            Discount discount = cdm.getDiscount();
            if (isDiscountActive(discount, itemMapping.getMenuCategoryMapping().getMenu().getId(), restaurantId) && discount.getDiscountType() != DiscountType.BXGY) {
                double discountPercentage = calculateDiscountPercentage(discount, effectiveBasePrice);
                if (discountPercentage > maxDiscountPercentage) {
                    maxDiscountPercentage = discountPercentage;
                    bestLevel = AppliedTo.CATEGORY;
                }
            }
        }
    }
    
    // Check item-level discounts
    for (CategoryItemMapping itemMapping : itemMappings) {
        List<ItemDiscountMapping> itemDiscounts = itemDiscountMappingRepository.findByCategoryItemMapping(itemMapping);
        
        for (ItemDiscountMapping idm : itemDiscounts) {
            Discount discount = idm.getDiscount();
            if (isDiscountActive(discount, itemMapping.getMenuCategoryMapping().getMenu().getId(), restaurantId) && discount.getDiscountType() != DiscountType.BXGY) {
                double discountPercentage = calculateDiscountPercentage(discount, effectiveBasePrice);
                if (discountPercentage > maxDiscountPercentage) {
                    maxDiscountPercentage = discountPercentage;
                    bestLevel = AppliedTo.ITEM;
                }
            }
        }
    }
    
    return bestLevel;
}

/**
 * Helper method to find promotion discount info if it's assigned to the item
 */
private DiscountInfo findPromotionDiscountInfo(Discount promotionDiscount, List<CategoryItemMapping> itemMappings,
        Double basePrice, UUID restaurantId, Locale userLocale) {
    UUID promotionDiscountId = promotionDiscount.getId();
    
    // Check item-level discounts
    for (CategoryItemMapping itemMapping : itemMappings) {
        List<ItemDiscountMapping> itemDiscounts = itemDiscountMappingRepository.findByCategoryItemMapping(itemMapping);
        for (ItemDiscountMapping idm : itemDiscounts) {
            Discount discount = idm.getDiscount();
            if (discount != null && discount.getId().equals(promotionDiscountId)) {
                boolean isActive = isDiscountActive(discount, itemMapping.getMenuCategoryMapping().getMenu().getId(), restaurantId);
                if (isActive && discount.getDiscountType() != DiscountType.BXGY) {
                    double discountAmount = calculateDiscountAmount(discount, basePrice);
                    if (discountAmount > 0) {
                        double discountedPrice = basePrice - discountAmount;
                        String discountDetail = generateDiscountDetailFromDiscount(discount, basePrice, userLocale);
                        return new DiscountInfo(basePrice, Math.max(0.0, Math.round(discountedPrice * 100.0) / 100.0), 
                            false, false, discountDetail);
                    }
                }
            }
        }
    }
    
    // Check category-level discounts
    for (CategoryItemMapping itemMapping : itemMappings) {
        MenuCategoryMapping mcm = itemMapping.getMenuCategoryMapping();
        List<CategoryDiscountMapping> categoryDiscounts = categoryDiscountMappingRepository.findByMenuCategoryMapping(mcm);
        
        for (CategoryDiscountMapping cdm : categoryDiscounts) {
            Discount discount = cdm.getDiscount();
            if (discount != null && discount.getId().equals(promotionDiscountId)) {
                boolean isActive = isDiscountActive(discount, itemMapping.getMenuCategoryMapping().getMenu().getId(), restaurantId);
                if (isActive && discount.getDiscountType() != DiscountType.BXGY) {
                    double discountAmount = calculateDiscountAmount(discount, basePrice);
                    if (discountAmount > 0) {
                        double discountedPrice = basePrice - discountAmount;
                        String discountDetail = generateDiscountDetailFromDiscount(discount, basePrice, userLocale);
                        return new DiscountInfo(basePrice, Math.max(0.0, Math.round(discountedPrice * 100.0) / 100.0), 
                            false, false, discountDetail);
                    }
                }
            }
        }
    }
    
    return null;
}

/**
 * Helper method to collect all applicable discounts (excluding promotion discount if it was already added)
 */
private void collectApplicableDiscounts(List<CategoryItemMapping> itemMappings, Double basePrice, UUID menuId, UUID restaurantId,
        Discount promotionDiscount, List<DiscountInfo> applicableDiscounts, Locale userLocale) {
    
    // Check category-level discounts
    for (CategoryItemMapping itemMapping : itemMappings) {
        MenuCategoryMapping mcm = itemMapping.getMenuCategoryMapping();
        List<CategoryDiscountMapping> categoryDiscounts = categoryDiscountMappingRepository.findByMenuCategoryMapping(mcm);
        
        log.info("Found {} category-level discounts for item {} in category {}", 
                categoryDiscounts.size(), itemMapping.getItem().getId(), 
                mcm.getCategory() != null ? mcm.getCategory().getId() : "null");
        
        for (CategoryDiscountMapping cdm : categoryDiscounts) {
            Discount discount = cdm.getDiscount();
            
            // Skip promotion discount if it was already added to the list
            if (promotionDiscount != null && discount != null && discount.getId().equals(promotionDiscount.getId())) {
                continue;
            }
            
            boolean isActive = isDiscountActive(discount, menuId, restaurantId);
            log.info("Category discount {} for item {} - isActive: {}, type: {}", 
                    discount != null ? discount.getId() : "null", itemMapping.getItem().getId(), isActive, 
                    discount != null ? discount.getDiscountType() : "null");
            
            if (isActive && discount != null && discount.getDiscountType() != DiscountType.BXGY) {
                double discountAmount = calculateDiscountAmount(discount, basePrice);
                if (discountAmount > 0) {
                    double discountedPrice = basePrice - discountAmount;
                    String discountDetail = generateDiscountDetailFromDiscount(discount, basePrice, userLocale);
                    log.info("Adding category discount {} to applicable discounts: basePrice={}, discountedPrice={}, discountDetail={}", 
                            discount.getId(), basePrice, discountedPrice, discountDetail);
                    applicableDiscounts.add(new DiscountInfo(basePrice, 
                        Math.max(0.0, Math.round(discountedPrice * 100.0) / 100.0), false, false, discountDetail));
                }
            }
        }
        
        // If this category has a parent category, also check for discounts on the parent category
        if (mcm.getCategory() != null && mcm.getCategory().getParentCategory() != null && menuId != null) {
            UUID parentCategoryId = mcm.getCategory().getParentCategory().getId();
            Optional<MenuCategoryMapping> parentMcm = menuCategoryMappingRepository.findByMenuIdAndCategoryId(menuId, parentCategoryId);
            
            if (parentMcm.isPresent()) {
                List<CategoryDiscountMapping> parentCategoryDiscounts = categoryDiscountMappingRepository.findByMenuCategoryMapping(parentMcm.get());
                log.info("Found {} parent category-level discounts for item {} in parent category {}", 
                        parentCategoryDiscounts.size(), itemMapping.getItem().getId(), parentCategoryId);
                
                for (CategoryDiscountMapping cdm : parentCategoryDiscounts) {
                    Discount discount = cdm.getDiscount();
                    
                    // Skip promotion discount if it was already added to the list
                    if (promotionDiscount != null && discount != null && discount.getId().equals(promotionDiscount.getId())) {
                        continue;
                    }
                    
                    boolean isActive = isDiscountActive(discount, menuId, restaurantId);
                    log.info("Parent category discount {} for item {} - isActive: {}, type: {}", 
                            discount != null ? discount.getId() : "null", itemMapping.getItem().getId(), isActive, 
                            discount != null ? discount.getDiscountType() : "null");
                    
                    if (isActive && discount != null && discount.getDiscountType() != DiscountType.BXGY) {
                        double discountAmount = calculateDiscountAmount(discount, basePrice);
                        if (discountAmount > 0) {
                            double discountedPrice = basePrice - discountAmount;
                            String discountDetail = generateDiscountDetailFromDiscount(discount, basePrice, userLocale);
                            log.info("Adding parent category discount {} to applicable discounts: basePrice={}, discountedPrice={}, discountDetail={}", 
                                    discount.getId(), basePrice, discountedPrice, discountDetail);
                            applicableDiscounts.add(new DiscountInfo(basePrice, 
                                Math.max(0.0, Math.round(discountedPrice * 100.0) / 100.0), false, false, discountDetail));
                        }
                    }
                }
            }
        }
    }
    
    // Check item-level discounts
    for (CategoryItemMapping itemMapping : itemMappings) {
        List<ItemDiscountMapping> itemDiscounts = itemDiscountMappingRepository.findByCategoryItemMapping(itemMapping);
        
        for (ItemDiscountMapping idm : itemDiscounts) {
            Discount discount = idm.getDiscount();
            
            // Skip promotion discount if it was already added to the list
            if (promotionDiscount != null && discount != null && discount.getId().equals(promotionDiscount.getId())) {
                continue;
            }
            
            boolean isActive = isDiscountActive(discount, itemMapping.getMenuCategoryMapping().getMenu().getId(), restaurantId);
            
            if (isActive && discount.getDiscountType() != DiscountType.BXGY) {
                double discountAmount = calculateDiscountAmount(discount, basePrice);
                if (discountAmount > 0) {
                    double discountedPrice = basePrice - discountAmount;
                    String discountDetail = generateDiscountDetailFromDiscount(discount, basePrice, userLocale);
                    applicableDiscounts.add(new DiscountInfo(basePrice, 
                        Math.max(0.0, Math.round(discountedPrice * 100.0) / 100.0), false, false, discountDetail));
                }
            }
        }
    }
}

/**
 * Helper method to find the best discount from a list of applicable discounts
 */
private DiscountInfo findBestDiscount(List<DiscountInfo> applicableDiscounts, Double basePrice) {
    if (applicableDiscounts.isEmpty()) {
        return null;
    }
    
    DiscountInfo bestDiscount = null;
    double maxDiscountPercentage = 0.0;
    
    for (DiscountInfo discountInfo : applicableDiscounts) {
        if (discountInfo.getDiscountedPrice() != null) {
            double discountAmount = basePrice - discountInfo.getDiscountedPrice();
            double discountPercentage = (discountAmount / basePrice) * 100.0;
            
            if (discountPercentage > maxDiscountPercentage) {
                maxDiscountPercentage = discountPercentage;
                bestDiscount = discountInfo;
            }
        }
    }
    
    return bestDiscount;
}

// Add these helper methods (copy from MenuServiceImpl):
private double calculateDiscountPercentage(Discount discount, Double basePrice) {
    if (discount.getDiscountType() == DiscountType.PERCENT) {
        return discount.getValue() != null ? discount.getValue().doubleValue() : 0.0;
    } else if (discount.getDiscountType() == DiscountType.FLAT) {
        double flatAmount = discount.getValue() != null ? discount.getValue().doubleValue() : 0.0;
        return basePrice > 0 ? (flatAmount / basePrice) * 100.0 : 0.0;
    }
    return 0.0;
}

    /**
     * Calculates the discount amount for a discount based on base price.
     * For PERCENT discounts: calculates percentage of base price, applies max discount limit if specified.
     * For FLAT discounts: returns the flat amount (capped at base price).
     *
     * @param discount  the discount entity
     * @param basePrice the base price to calculate discount from
     * @return the calculated discount amount
     */
private double calculateDiscountAmount(Discount discount, Double basePrice) {
    if (discount.getDiscountType() == DiscountType.PERCENT) {
        double percentage = discount.getValue() != null ? discount.getValue().doubleValue() : 0.0;
        double discountAmount = (basePrice * percentage) / 100.0;
        
        // Apply max discount limit if specified
        if (discount.getMaxDiscountValue() != null) {
            discountAmount = Math.min(discountAmount, discount.getMaxDiscountValue().doubleValue());
        }
        
        return discountAmount;
    } else if (discount.getDiscountType() == DiscountType.FLAT) {
        double flatAmount = discount.getValue() != null ? discount.getValue().doubleValue() : 0.0;
        return Math.min(flatAmount, basePrice); // Can't discount more than the base price
    }
    return 0.0;
}

private String generateBxgyDetail(Discount discount, Locale userLocale) {
    if (discount == null) {
        return null;
    }
    int buyQuantity = discount.getBuyQuantity() != null ? discount.getBuyQuantity() : 1;
    int getQuantity = discount.getGetQuantity() != null ? discount.getGetQuantity() : 1;
    return messageUtil.getMessage("discount.bxgy.detail", userLocale, buyQuantity, getQuantity);
}

    /**
     * Generates a human-readable discount detail string from a DiscountCalculationResult.
     * Formats discount as percentage or flat amount based on discount type.
     *
     * @param discountResult the discount calculation result
     * @return formatted discount detail string (e.g., "20% off" or "Flat $5 off")
     */
private String generateDiscountDetail(DiscountCalculationResult discountResult, Locale userLocale) {
    // Prefer the precomputed label if available (e.g., "Flat ¥50 off", BXGY text)
    if (discountResult.getDiscountDetail() != null && !discountResult.getDiscountDetail().isEmpty()) {
        return discountResult.getDiscountDetail();
    }

    if (discountResult.getDiscountAmount() == null || 
        discountResult.getDiscountAmount().compareTo(BigDecimal.ZERO) <= 0) {
        return null; // No discount applied
    }
    
    // Calculate discount percentage
    BigDecimal originalPrice = discountResult.getOriginalPrice();
    BigDecimal discountAmount = discountResult.getDiscountAmount();
    
    if (originalPrice.compareTo(BigDecimal.ZERO) > 0) {
        BigDecimal percentage = discountAmount.divide(originalPrice, 4, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100));
        
        // Round to nearest whole number
        int roundedPercentage = percentage.setScale(0, RoundingMode.HALF_UP).intValue();
        
        if (roundedPercentage > 0) {
            return messageUtil.getMessage("discount.percent.detail", userLocale, roundedPercentage);
        }
    }
    
    // If percentage is 0 or calculation failed, show flat amount
    String currencySymbol = restaurantChainConfigProperties.getChain() != null && 
                           restaurantChainConfigProperties.getChain().getCurrency() != null ? 
                           restaurantChainConfigProperties.getChain().getCurrency() : "";
    return messageUtil.getMessage(
            "discount.flat.detail",
            userLocale,
            currencySymbol,
            discountAmount.setScale(0, RoundingMode.HALF_UP).toPlainString()
    );
}

    /**
     * Generates a human-readable discount detail string from a Discount entity.
     * Formats discount as percentage or flat amount based on discount type.
     *
     * @param discount  the discount entity
     * @param basePrice the base price (used for formatting)
     * @return formatted discount detail string (e.g., "20% off" or "Flat $5 off")
     */
private String generateDiscountDetailFromDiscount(Discount discount, Double basePrice, Locale userLocale) {
    if (discount == null) return null;
    
    if (discount.getDiscountType() == DiscountType.PERCENT) {
        double percentage = discount.getValue() != null ? discount.getValue().doubleValue() : 0.0;
        return messageUtil.getMessage("discount.percent.detail", userLocale, (int) Math.round(percentage));
    } else if (discount.getDiscountType() == DiscountType.FLAT) {
        double flatAmount = discount.getValue() != null ? discount.getValue().doubleValue() : 0.0;
        String currencySymbol = restaurantChainConfigProperties.getChain() != null && 
                               restaurantChainConfigProperties.getChain().getCurrency() != null ? 
                               restaurantChainConfigProperties.getChain().getCurrency() : "";
        return messageUtil.getMessage("discount.flat.detail", userLocale, currencySymbol, (int) Math.round(flatAmount));
    }
    
    return null;
}

// Add the DiscountInfo inner class
private static class DiscountInfo {
    private final Double basePrice;
    private final Double discountedPrice;  
    private final boolean isBxgyBuyItem;
    private final boolean isBxgyGetItem;
    private final String discountDetail;
    
    public DiscountInfo(Double basePrice, Double discountedPrice, boolean isBxgyBuyItem, boolean isBxgyGetItem, String discountDetail) {
        this.basePrice = basePrice;
        this.discountedPrice = discountedPrice;
        this.isBxgyBuyItem = isBxgyBuyItem;
        this.isBxgyGetItem = isBxgyGetItem;
        this.discountDetail = discountDetail;
    }
    
    public Double getBasePrice() { return basePrice; }
    public Double getDiscountedPrice() { return discountedPrice; }
    public boolean isBxgyBuyItem() { return isBxgyBuyItem; }
    public boolean isBxgyGetItem() { return isBxgyGetItem; }
    public String getDiscountDetail() { return discountDetail; }
}

// Helper method to check availability
    /**
     * Checks if an item is available for a specific restaurant and menu.
     * Looks up RestaurantItemAvailability record and returns availability status.
     * Returns false if any parameter is null or if no availability record exists.
     *
     * @param restaurantId the UUID of the restaurant
     * @param itemId       the UUID of the item
     * @param menuId       the UUID of the menu
     * @return true if item is available, false otherwise
     */
private Boolean checkItemAvailability(UUID restaurantId, UUID itemId, UUID menuId) {
    try {
        // Validate input parameters
        if (restaurantId == null || itemId == null || menuId == null) {
            return false;
        }

        List<CategoryItemMapping> categoryItemMappings =
                categoryItemMappingRepository.findAllByMenuCategoryMappingMenuIdAndItemId(menuId, itemId);
        if (categoryItemMappings.isEmpty()) {
            return false;
        }

        // Same rule as items-and-menus list: unavailable if any placement is explicitly unavailable
        for (CategoryItemMapping categoryItemMapping : categoryItemMappings) {
            if (categoryItemMapping == null || categoryItemMapping.getId() == null) {
                return false;
            }
            Optional<RestaurantItemAvailability> availabilityOpt = findRestaurantItemAvailability(
                    restaurantId, categoryItemMapping.getId());
            if (availabilityOpt.isPresent()) {
                RestaurantItemAvailability availability = availabilityOpt.get();
                if (availability != null && Boolean.FALSE.equals(availability.getIsAvailable())) {
                    return false;
                }
            }
        }

        Item item = findItemById(itemId);
        if (item == null) {
            return false;
        }

        return !Boolean.TRUE.equals(item.getOutOfStock());

    } catch (Exception e) {
        return false; // Default to unavailable on any unexpected error
    }
}

/**
 * Helper method to find restaurant item availability with error handling.
 */
private Optional<RestaurantItemAvailability> findRestaurantItemAvailability(UUID restaurantId, UUID categoryItemMappingId) {
    try {
        return restaurantItemAvailabilityRepository.findByRestaurantIdAndCategoryItemMappingId(
                restaurantId, categoryItemMappingId);
    } catch (Exception e) {
        log.warn("Error finding restaurant item availability for restaurant {} and mapping {}", 
                restaurantId, categoryItemMappingId, e);
        return Optional.empty();
    }
}

/**
 * Helper method to find item by ID with error handling.
 */
private Item findItemById(UUID itemId) {
    try {
        return itemRepository.findById(itemId).orElse(null);
    } catch (Exception e) {
        log.warn("Error finding item by ID: {}", itemId, e);
        return null;
    }
}

    /**
     * Checks if a discount is currently active for a menu and restaurant.
     * Validates discount status, deletion state, usage limits, and restaurant/menu-specific
     * validity periods, time restrictions, and day-of-week restrictions.
     *
     * @param discount    the discount to check
     * @param menuId      the UUID of the menu
     * @param restaurantId the UUID of the restaurant (optional)
     * @return true if discount is active, false otherwise
     */
private boolean isDiscountActive(Discount discount, UUID menuId, UUID restaurantId) {
    // First check: discount is active and not deleted
    if (discount == null || discount.getIsDeleted() || discount.getStatus() != EntityStatus.ACTIVE) {
        log.debug("Discount {} is null, deleted, or not active", discount != null ? discount.getId() : "null");
        return false;
    }
    
    // Usage limit check: consider expired when maxUses reached
    // maxUses = 0 means unlimited, so only check if maxUses > 0
    if (discount.getMaxUses() != null && discount.getMaxUses() > 0 && discount.getCurrentUsage() >= discount.getMaxUses()) {
        log.debug("Discount {} has reached max usage limit", discount.getId());
        return false;
    }
    
    OffsetDateTime nowUtc = OffsetDateTime.now(ZoneOffset.UTC).truncatedTo(java.time.temporal.ChronoUnit.SECONDS);
    
    // Check restaurant discount mapping first if restaurantId is provided
    if (restaurantId != null) {
        RestaurantDiscountId restaurantDiscountId = new RestaurantDiscountId();
        restaurantDiscountId.setRestaurantId(restaurantId);
        restaurantDiscountId.setDiscountId(discount.getId());
        Optional<RestaurantDiscountMapping> restaurantDiscountMappingOpt = restaurantDiscountMappingRepository.findById(restaurantDiscountId);
        
        if (restaurantDiscountMappingOpt.isPresent()) {
            RestaurantDiscountMapping restaurantDiscountMapping = restaurantDiscountMappingOpt.get();
            
            // Check status - if INACTIVE, discount is not valid for this restaurant
            if (restaurantDiscountMapping.getStatus() != null && restaurantDiscountMapping.getStatus() != EntityStatus.ACTIVE) {
                log.debug("Restaurant discount mapping for discount {} is not active", discount.getId());
                return false;
            }
            
            // Check restaurant-specific validity period (using UTC)
            if (restaurantDiscountMapping.getValidFrom() != null && nowUtc.isBefore(restaurantDiscountMapping.getValidFrom())) {
                log.debug("Discount {} validFrom {} is after current time {}", discount.getId(), restaurantDiscountMapping.getValidFrom(), nowUtc);
                return false;
            }
            
            if (restaurantDiscountMapping.getValidTo() != null && nowUtc.isAfter(restaurantDiscountMapping.getValidTo())) {
                log.debug("Discount {} validTo {} is before current time {}", discount.getId(), restaurantDiscountMapping.getValidTo(), nowUtc);
                return false;
            }
            
            // Check restaurant-specific time restrictions (using UTC)
            if (restaurantDiscountMapping.getStartTime() != null && restaurantDiscountMapping.getEndTime() != null) {
                OffsetTime currentTime = nowUtc.toOffsetTime();
                OffsetTime startTime = restaurantDiscountMapping.getStartTime();
                OffsetTime endTime = restaurantDiscountMapping.getEndTime();
                
                boolean isTimeValid = false;
                if (startTime.isBefore(endTime) || startTime.equals(endTime)) {
                    // Normal case: start <= end (e.g., 12:00 to 18:00 or 12:00 to 12:00 for 24-hour)
                    isTimeValid = !currentTime.isBefore(startTime) && !currentTime.isAfter(endTime);
                } else {
                    // Overnight case: start > end (e.g., 23:00 to 02:00)
                    // Active if currentTime >= startTime OR currentTime <= endTime
                    isTimeValid = !currentTime.isBefore(startTime) || !currentTime.isAfter(endTime);
                }
                
                if (!isTimeValid) {
                    log.debug("Discount {} time restriction not met. Current: {}, Start: {}, End: {}", 
                        discount.getId(), currentTime, startTime, endTime);
                    return false;
                }
            }
            
            // Check restaurant-specific day-of-week restrictions
            if (restaurantDiscountMapping.getDaysOfWeek() != null && !restaurantDiscountMapping.getDaysOfWeek().isEmpty()) {
                com.gulfnet.shared_library.enums.DayOfWeek currentDay = convertToDayOfWeek(nowUtc.getDayOfWeek());
                if (!restaurantDiscountMapping.getDaysOfWeek().contains(currentDay)) {
                    log.debug("Discount {} not valid on current day {}", discount.getId(), currentDay);
                    return false;
                }
            }
            
            // Restaurant mapping exists and is valid
            log.debug("Discount {} is active for restaurant {}", discount.getId(), restaurantId);
            return true;
        }
        // If restaurantId is provided but no RestaurantDiscountMapping exists, fall through to check MenuDiscountMapping
    }
    
    // Check menu discount mapping (either restaurantId is null or RestaurantDiscountMapping doesn't exist)
    if (menuId != null) {
        MenuDiscountId menuDiscountId = new MenuDiscountId();
        menuDiscountId.setMenuId(menuId);
        menuDiscountId.setDiscountId(discount.getId());
        Optional<MenuDiscountMapping> menuDiscountMappingOpt = menuDiscountMappingRepository.findById(menuDiscountId);
        
        if (menuDiscountMappingOpt.isPresent()) {
            MenuDiscountMapping menuDiscountMapping = menuDiscountMappingOpt.get();
            
            // Check menu-specific validity period (using UTC)
            if (menuDiscountMapping.getValidFrom() != null && nowUtc.isBefore(menuDiscountMapping.getValidFrom())) {
                log.debug("Menu discount {} validFrom {} is after current time {}", discount.getId(), menuDiscountMapping.getValidFrom(), nowUtc);
                return false;
            }
            
            if (menuDiscountMapping.getValidTo() != null && nowUtc.isAfter(menuDiscountMapping.getValidTo())) {
                log.debug("Menu discount {} validTo {} is before current time {}", discount.getId(), menuDiscountMapping.getValidTo(), nowUtc);
                return false;
            }
            
            // Check menu-specific time restrictions (using UTC)
            if (menuDiscountMapping.getStartTime() != null && menuDiscountMapping.getEndTime() != null) {
                OffsetTime currentTime = nowUtc.toOffsetTime();
                OffsetTime startTime = menuDiscountMapping.getStartTime();
                OffsetTime endTime = menuDiscountMapping.getEndTime();
                
                boolean isTimeValid = false;
                if (startTime.isBefore(endTime) || startTime.equals(endTime)) {
                    // Normal case: start <= end (e.g., 12:00 to 18:00 or 12:00 to 12:00 for 24-hour)
                    isTimeValid = !currentTime.isBefore(startTime) && !currentTime.isAfter(endTime);
                } else {
                    // Overnight case: start > end (e.g., 23:00 to 02:00)
                    // Active if currentTime >= startTime OR currentTime <= endTime
                    isTimeValid = !currentTime.isBefore(startTime) || !currentTime.isAfter(endTime);
                }
                
                if (!isTimeValid) {
                    log.debug("Menu discount {} time restriction not met. Current: {}, Start: {}, End: {}", 
                        discount.getId(), currentTime, startTime, endTime);
                    return false;
                }
            }
            
            // Check menu-specific day-of-week restrictions
            if (menuDiscountMapping.getDaysOfWeek() != null && !menuDiscountMapping.getDaysOfWeek().isEmpty()) {
                com.gulfnet.shared_library.enums.DayOfWeek currentDay = convertToDayOfWeek(nowUtc.getDayOfWeek());
                if (!menuDiscountMapping.getDaysOfWeek().contains(currentDay)) {
                    log.debug("Menu discount {} not valid on current day {}", discount.getId(), currentDay);
                    return false;
                }
            }
            
            // Menu mapping exists and is valid
            log.debug("Discount {} is active for menu {}", discount.getId(), menuId);
            return true;
        }
    }
    
    // If no mapping found, consider discount active (backward compatibility)
    log.debug("No discount mapping found for discount {}, menu {}, restaurant {}. Considering active.", 
        discount.getId(), menuId, restaurantId);
    return true;
}

private boolean isTimeInRange(OffsetTime currentTime, OffsetTime startTime, OffsetTime endTime) {
    if (startTime.isBefore(endTime)) {
        return !currentTime.isBefore(startTime) && !currentTime.isAfter(endTime);
    } else {
        return !currentTime.isBefore(startTime) || !currentTime.isAfter(endTime);
    }
}

    /**
     * Converts Java DayOfWeek enum to custom DayOfWeek enum.
     *
     * @param javaDayOfWeek the Java DayOfWeek value
     * @return corresponding custom DayOfWeek enum value (defaults to SUNDAY if unknown)
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
        default: return com.gulfnet.shared_library.enums.DayOfWeek.SUNDAY;
    }
}

    /**
     * Retrieves a paginated list of items and menus for a specific restaurant.
     * Includes item availability information and supports filtering by availability status and search.
     * Results are sorted and paginated.
     *
     * @param restaurantId the UUID of the restaurant
     * @param page         page number for pagination
     * @param size         page size for pagination
     * @param isAvailable  optional filter by item availability status
     * @param search       optional search term for item name
     * @param sortBy       field to sort by
     * @param direction    sort direction
     * @param locale       locale code for localized responses
     * @return ResponseDto containing paginated list of items with menu and availability information
     * @throws ResponseStatusException if locale is invalid
     */
@Override
@Transactional(readOnly = true)
public ResponseDto<RestaurantItemsAndMenusResponse> getRestaurantItemsAndMenus(UUID restaurantId, Integer page, Integer size, Boolean isAvailable, String search, String sortBy, Sort.Direction direction, String locale) {
        Locale userLocale = Locale.forLanguageTag(locale);

        if (!localizationProperties.getLanguages().contains(locale)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    messageUtil.getMessage(msgItemErrorInvalidLanguage, userLocale));
        }

        // Set pagination parameters with defaults
        int pageNumber = (page != null && page > 0) ? page - 1 : 0;
        int pageSize = (size != null && size > 0) ? size : 10;

        // Resolve the menu shown for this restaurant (LIVE first), same as menu payload below.
        // Item list and availability must use all category_item_mapping rows for this menu so
        // items placed in multiple categories do not pick the wrong mapping for isAvailable.
        List<RestaurantMenuMapping> restaurantMenuMappingsForItems =
                restaurantMenuMappingRepository.findById_RestaurantId(restaurantId);
        RestaurantMenuMapping selectedRestaurantMenuMappingForItems = null;
        if (restaurantMenuMappingsForItems != null && !restaurantMenuMappingsForItems.isEmpty()) {
            selectedRestaurantMenuMappingForItems = restaurantMenuMappingsForItems.stream()
                    .filter(mapping -> mapping.getMenu() != null
                            && !Boolean.TRUE.equals(mapping.getMenu().getIsDeleted()))
                    .filter(mapping -> RestaurantMenuMappingStatus.LIVE.equals(mapping.getStatus()))
                    .findFirst()
                    .orElse(restaurantMenuMappingsForItems.stream()
                            .filter(mapping -> mapping.getMenu() != null
                                    && !Boolean.TRUE.equals(mapping.getMenu().getIsDeleted()))
                            .findFirst()
                            .orElse(null));
        }

        // 1. Fetch all RestaurantItemAvailability records for the restaurant
        List<RestaurantItemAvailability> restaurantItemAvailabilities = 
                restaurantItemAvailabilityRepository.findByRestaurantId(restaurantId);

        // 1.5. Create a map from CategoryItemMapping ID to isAvailable status
        Map<UUID, Boolean> categoryItemMappingToAvailabilityMap = restaurantItemAvailabilities.stream()
                .collect(Collectors.toMap(
                        ria -> ria.getCategoryItemMapping().getId(),
                        ria -> Optional.ofNullable(ria.getIsAvailable()).orElse(true),
                        (existing, replacement) -> existing // In case of duplicates, keep the first one
                ));

        // 2. Load CategoryItemMapping rows: prefer all placements on the restaurant's display menu
        List<CategoryItemMapping> categoryItemMappings = new ArrayList<>();
        if (selectedRestaurantMenuMappingForItems != null && selectedRestaurantMenuMappingForItems.getMenu() != null) {
            categoryItemMappings = categoryItemMappingRepository.findByMenuIdAndRestaurant(
                    selectedRestaurantMenuMappingForItems.getMenu().getId(), restaurantId);
        } else {
            Set<UUID> categoryItemMappingIds = restaurantItemAvailabilities.stream()
                    .map(ria -> ria.getCategoryItemMapping().getId())
                    .collect(Collectors.toSet());
            if (!categoryItemMappingIds.isEmpty()) {
                categoryItemMappings = categoryItemMappingIds.stream()
                        .map(id -> categoryItemMappingRepository.findById(id))
                        .filter(Optional::isPresent)
                        .map(Optional::get)
                        .collect(Collectors.toList());
            }
        }

        // 3. Extract unique item IDs and fetch Item records
        Set<UUID> itemIds = categoryItemMappings.stream()
                .map(cim -> cim.getItem().getId())
                .collect(Collectors.toSet());

        List<Item> items = new ArrayList<>();
        if (!itemIds.isEmpty()) {
            items = itemRepository.findAllById(itemIds);
        }

        // 4. Get currency for formatting prices
        String currency = restaurantChainConfigProperties.getChain() != null ? restaurantChainConfigProperties.getChain().getCurrency() : null;

        // 4.5. Build ItemResponse list with translations
        List<ItemResponse> itemResponses = new ArrayList<>();
        for (Item item : items) {
            if (Boolean.TRUE.equals(item.getIsDeleted())) {
                continue;
            }

            // Get translations
            List<ItemTranslation> itemTranslations = itemTranslationRepository.findAllByItemId(item.getId());
            List<ItemTranslationDto> translationDtos = new ArrayList<>();

            if (!itemTranslations.isEmpty()) {
                ItemTranslation exactMatch = itemTranslations.stream()
                        .filter(t -> t.getLanguageCode() != null && t.getLanguageCode().equals(locale))
                        .findFirst()
                        .orElse(null);

                if (exactMatch != null) {
                    translationDtos.add(ItemTranslationDto.builder()
                            .languageCode(exactMatch.getLanguageCode())
                            .name(exactMatch.getName())
                            .description(exactMatch.getDescription())
                            .build());
                } else {
                    Optional<ItemTranslation> fallback = TranslationUtils.pickPreferredOrFromList(
                            itemTranslations,
                            locale,
                            localizationProperties.getLanguages(),
                            ItemTranslation::getLanguageCode
                    );
                    fallback.ifPresent(trans -> translationDtos.add(ItemTranslationDto.builder()
                            .languageCode(trans.getLanguageCode())
                            .name(trans.getName())
                            .description(trans.getDescription())
                            .build()));
                }
            }

            // Get creator and updater names
            String createdByName = null;
            String updatedByName = null;

            if (item.getCreatedBy() != null) {
                User createdByUser = userRepository.findById(item.getCreatedBy().getId()).orElse(null);
                if (createdByUser != null) {
                    createdByName = createdByUser.getFirstName() + " " + createdByUser.getLastName();
                }
            }

            if (item.getUpdatedBy() != null) {
                User updatedByUser = userRepository.findById(item.getUpdatedBy().getId()).orElse(null);
                if (updatedByUser != null) {
                    updatedByName = updatedByUser.getFirstName() + " " + updatedByUser.getLastName();
                }
            }

            // Resolve isAvailable from every category placement for this item (same menu may list
            // the item under multiple categories). Unavailable if any placement is explicitly false.
            boolean itemIsAvailable = true;
            for (CategoryItemMapping cim : categoryItemMappings) {
                if (!cim.getItem().getId().equals(item.getId())) {
                    continue;
                }
                Boolean avail = categoryItemMappingToAvailabilityMap.get(cim.getId());
                if (Boolean.FALSE.equals(avail)) {
                    itemIsAvailable = false;
                    break;
                }
            }

            ItemResponse itemResponse = ItemResponse.builder()
                    .id(item.getId())
                    .itemCode(item.getItemCode())
                    .basePrice(item.getBasePrice() != null ? CurrencyFormatter.formatAmount(BigDecimal.valueOf(item.getBasePrice()), currency).doubleValue() : null)
                    .imageUrl(awsService.getFullUrl(item.getImageUrl()))
                    .alcoholType(item.getAlcoholType())
                    .outOfStock(item.getOutOfStock())
                    .status(item.getStatus())
                    .dietaryPreference(item.getDietaryPreference())
                    .itemOrderType(item.getItemOrderType())
                    .alcoholType(item.getAlcoholType())
                    .isDeleted(item.getIsDeleted())
                    .createdBy(createdByName)
                    .updatedBy(updatedByName)
                    .hasModifierAssigned(item.getHasModifierAssigned())
                    .isAvailable(itemIsAvailable)
                    .createdAt(item.getCreatedAt() != null ? item.getCreatedAt().toLocalDateTime() : null)
                    .updatedAt(item.getUpdatedAt() != null ? item.getUpdatedAt().toLocalDateTime() : null)
                    .translations(translationDtos)
                    .build();

            itemResponses.add(itemResponse);
        }

        // 4.5. Apply search filter on name if provided
        if (search != null && !search.trim().isEmpty()) {
            String searchLower = search.trim().toLowerCase();
            itemResponses = itemResponses.stream()
                    .filter(item -> {
                        if (item.getTranslations() == null || item.getTranslations().isEmpty()) {
                            return false;
                        }
                        // Check if any translation name contains the search term
                        return item.getTranslations().stream()
                                .anyMatch(translation -> translation.getName() != null && 
                                        translation.getName().toLowerCase().contains(searchLower));
                    })
                    .collect(Collectors.toList());
        }

        // 4.6. Apply isAvailable filter if provided
        if (isAvailable != null) {
            itemResponses = itemResponses.stream()
                    .filter(item -> isAvailable.equals(item.getIsAvailable()))
                    .collect(Collectors.toList());
        }

        // 4.7. Apply sorting using LocaleSortUtil
        String normalizedSortBy = (sortBy == null || sortBy.isBlank()) ? FIELD_CREATED_AT : sortBy;
        Sort.Direction sortDirection = (direction != null) ? direction : Sort.Direction.DESC;
        
        // Set locale in context for LocaleSortUtil
        LocaleContextHolder.setLocale(userLocale);
        LocaleSortUtil.sortName(itemResponses, normalizedSortBy, sortDirection);

        // 4.8. Apply pagination to items
        int totalItems = itemResponses.size();
        int fromIndex = Math.min(pageNumber * pageSize, totalItems);
        int toIndex = Math.min(fromIndex + pageSize, totalItems);
        List<ItemResponse> pagedItemResponses = itemResponses.subList(fromIndex, toIndex);

        // Build pagination metadata
        PaginationMetaData paginationMetaData = PaginationMetaData.builder()
                .page(pageNumber + 1)
                .size(pageSize)
                .totalPages((int) Math.ceil((double) totalItems / pageSize))
                .totalRecords((long) totalItems)
                .build();

        // 5–6. Build menu payload using the same mapping selection as for items (no second DB round-trip)
        MenuResponse menuResponse = null;

        if (selectedRestaurantMenuMappingForItems != null) {
            RestaurantMenuMapping selectedMapping = selectedRestaurantMenuMappingForItems;
            if (selectedMapping.getMenu() != null) {
                Menu menu = selectedMapping.getMenu();

                // Get menu translations
                List<MenuTranslation> menuTranslations = menuTranslationRepository.findByMenuId(menu.getId());
                List<MenuTranslationDto> menuTranslationDtos = new ArrayList<>();

                if (!menuTranslations.isEmpty()) {
                    MenuTranslation exactMatch = menuTranslations.stream()
                            .filter(t -> t.getLanguageCode() != null && t.getLanguageCode().equals(locale))
                            .findFirst()
                            .orElse(null);

                    if (exactMatch != null) {
                        menuTranslationDtos.add(MenuTranslationDto.builder()
                                .languageCode(exactMatch.getLanguageCode())
                                .name(exactMatch.getName())
                                .description(exactMatch.getDescription())
                                .build());
                    } else {
                        Optional<MenuTranslation> fallback = TranslationUtils.pickPreferredOrFromList(
                                menuTranslations,
                                locale,
                                localizationProperties.getLanguages(),
                                MenuTranslation::getLanguageCode
                        );
                        fallback.ifPresent(trans -> menuTranslationDtos.add(MenuTranslationDto.builder()
                                .languageCode(trans.getLanguageCode())
                                .name(trans.getName())
                                .description(trans.getDescription())
                                .build()));
                    }
                }

                // Get creator and updater names
                String createdByName = null;
                String updatedByName = null;

                if (menu.getCreatedBy() != null) {
                    User createdByUser = userRepository.findById(menu.getCreatedBy().getId()).orElse(null);
                    if (createdByUser != null) {
                        createdByName = createdByUser.getFirstName() + " " + createdByUser.getLastName();
                    }
                }

                if (menu.getUpdatedBy() != null) {
                    User updatedByUser = userRepository.findById(menu.getUpdatedBy().getId()).orElse(null);
                    if (updatedByUser != null) {
                        updatedByName = updatedByUser.getFirstName() + " " + updatedByUser.getLastName();
                    }
                }

                menuResponse = MenuResponse.builder()
                        .id(menu.getId())
                        .menuMasterId(menu.getMenuMasterId())
                        .version(menu.getVersion())
                        .status(menu.getStatus() != null ? menu.getStatus().toString() : null)
                        .translations(menuTranslationDtos)
                        .menuStructureId(menu.getMenuStructure() != null ? menu.getMenuStructure().getId() : null)
                        .createdAt(menu.getCreatedAt() != null ? menu.getCreatedAt().toLocalDateTime() : null)
                        .updatedAt(menu.getUpdatedAt() != null ? menu.getUpdatedAt().toLocalDateTime() : null)
                        .createdBy(createdByName)
                        .updatedBy(updatedByName)
                        .build();
            }
        }

        // 7. Build and return response
        RestaurantItemsAndMenusResponse response = RestaurantItemsAndMenusResponse.builder()
                .items(pagedItemResponses)
                .menu(menuResponse)
                .count((long) pagedItemResponses.size())
                .total((long) totalItems)
                .metaData(paginationMetaData)
                .build();

        return ResponseDto.<RestaurantItemsAndMenusResponse>builder()
                .data(response)
                .message(messageUtil.getMessage("restaurant.items.menus.fetched.success", userLocale))
                .build();
    }

    public static class DiscountCalculationResult {
    private final BigDecimal originalPrice;
    private final BigDecimal finalPrice;
    private final Discount appliedDiscount;
    private final AppliedTo discountLevel;
    private final String discountDetail;
    
    public DiscountCalculationResult(BigDecimal originalPrice, BigDecimal finalPrice, 
                                   Discount appliedDiscount, AppliedTo discountLevel,
                                   String discountDetail) {
        this.originalPrice = originalPrice;
        this.finalPrice = finalPrice;
        this.appliedDiscount = appliedDiscount;
        this.discountLevel = discountLevel;
        this.discountDetail = discountDetail;
    }
    
    public BigDecimal getOriginalPrice() { return originalPrice; }
    public BigDecimal getFinalPrice() { return finalPrice; }
    public Discount getAppliedDiscount() { return appliedDiscount; }
    public AppliedTo getDiscountLevel() { return discountLevel; }
    public String getDiscountDetail() { return discountDetail; }
    
    public BigDecimal getDiscountAmount() {
        if (finalPrice == null) {
            return BigDecimal.ZERO;
        }
        return originalPrice.subtract(finalPrice);
    }
}

    /**
     * Restores one or more soft-deleted items by setting isDeleted flag to false.
     * Only restores items that are currently deleted. Updates updatedBy and updatedAt fields.
     *
     * @param ids    list of item UUIDs to restore
     * @param userId the ID of the user performing the restore
     * @param locale locale code for localized error messages
     * @return ResponseDto with success message
     * @throws ResponseStatusException if user not found, items not found, or no deleted items to restore
     */
    @Override
    @Transactional
    public ResponseDto<Void> restoreItems(List<UUID> ids, String userId, String locale) {
        Locale userLocale = Locale.forLanguageTag(locale);
        
        // Find user for updatedBy
        User user = userRepository.findById(UUID.fromString(userId))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        messageUtil.getMessage(msgUserNotFound, userLocale)));
        
        // Find all items by IDs
        List<Item> items = itemRepository.findAllById(ids);
        
        if (items.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                    messageUtil.getMessage(msgItemNotFound, userLocale));
        }
        
        // Filter only deleted items and restore them
        List<Item> deletedItems = items.stream()
                .filter(i -> Boolean.TRUE.equals(i.getIsDeleted()))
                .collect(Collectors.toList());
        
        if (deletedItems.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    messageUtil.getMessage("item.restore.error.not.deleted", userLocale));
        }
        
        // Restore all deleted items
        for (Item item : deletedItems) {
            item.setIsDeleted(false);
            item.setUpdatedAt(OffsetDateTime.now(ZoneOffset.UTC));
            item.setUpdatedBy(user);
        }
        
        itemRepository.saveAll(deletedItems);
        
        return ResponseDto.<Void>builder()
            .message(messageUtil.getMessage("item.restore.success", userLocale))
            .build();
    }

}