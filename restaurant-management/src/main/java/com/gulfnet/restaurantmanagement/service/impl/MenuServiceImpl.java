package com.gulfnet.restaurantmanagement.service.impl;
import org.quartz.*;
import org.quartz.impl.matchers.GroupMatcher;
import org.springframework.context.ApplicationContext;
import com.gulfnet.shared_library.util.EmailSender;
import com.gulfnet.shared_library.entity.Role;
import com.gulfnet.shared_library.repository.RoleRepository;
import com.gulfnet.shared_library.repository.UserRepository;
import com.gulfnet.shared_library.enums.RestaurantMenuMappingStatus;
import java.time.OffsetDateTime;
import java.time.OffsetTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.HashSet;
import java.util.Set;
import com.gulfnet.shared_library.model.response.dto.MenuListTranslationDto;
import com.gulfnet.shared_library.model.response.dto.MenuTranslationDto;
import java.util.Optional;
import jakarta.persistence.criteria.Subquery;
import jakarta.persistence.criteria.Root;
import com.gulfnet.shared_library.entity.ComboTranslation;  
import com.gulfnet.shared_library.entity.MenuTranslation;  
import java.time.LocalDateTime;
import java.util.Locale;
import java.util.HashSet;
import java.util.UUID;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import org.springframework.data.domain.Sort;
import java.util.stream.Collectors;
import com.gulfnet.shared_library.enums.EntityStatus;
import com.gulfnet.shared_library.enums.ActionType;
import com.gulfnet.shared_library.enums.RequestStatus;
import com.gulfnet.shared_library.enums.ItemOrderType;
import java.util.Collections;
import com.gulfnet.shared_library.entity.ItemTranslation;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;
import com.gulfnet.shared_library.model.request.AssignCategoriesItemsRequest;
import com.gulfnet.shared_library.model.request.AssignMenuStructureCategoriesRequest;
import com.gulfnet.shared_library.model.request.AssignMenuStructureCategoriesRequest.CategoryAssignment;
import com.gulfnet.shared_library.model.request.AssignMenuStructureCategoriesRequest.SubCategoryAssignment;
import com.gulfnet.shared_library.entity.CategoryItemMapping;
import com.gulfnet.shared_library.entity.MenuCategoryMapping;
import com.gulfnet.shared_library.entity.CategoryItemId;
import com.gulfnet.shared_library.entity.MenuCategoryId;
import com.gulfnet.restaurantmanagement.job.RestaurantMenuScheduleJob;
import java.util.Map;
import com.gulfnet.shared_library.entity.MenuStructure;
import com.gulfnet.shared_library.entity.Item;
import com.gulfnet.shared_library.entity.PriceOverride;
import com.gulfnet.shared_library.entity.PriceOverrideMapping;
import com.gulfnet.shared_library.repository.MenuStructureRepository;
import com.gulfnet.shared_library.repository.MenuTranslationRepository;
import com.gulfnet.shared_library.model.response.dto.MenuRestaurantGroupListResponse; 
import com.gulfnet.shared_library.model.response.dto.PaginationMetaData;
import com.gulfnet.shared_library.model.response.dto.MenuRestaurantGroupDetailsResponseDto;
import com.gulfnet.shared_library.util.LocaleSortUtil;
import java.text.Collator;
import java.util.Comparator;
import java.util.Locale;
import java.time.LocalDateTime;
import com.gulfnet.shared_library.model.response.dto.CategoryResponse;
import com.gulfnet.shared_library.model.response.dto.ItemResponse;
import com.gulfnet.shared_library.model.response.dto.TranslationResponse;
import com.gulfnet.shared_library.model.response.dto.ItemTranslationDto;
import com.gulfnet.shared_library.model.response.dto.CategoryTranslationResponse;
import com.gulfnet.shared_library.entity.RestaurantGroup;  
import com.gulfnet.shared_library.enums.RestaurantMenuMappingStatus;
import java.util.concurrent.CompletableFuture;

import org.springframework.transaction.annotation.Transactional;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;
import com.gulfnet.shared_library.entity.Category;
import com.gulfnet.shared_library.entity.CategoryTranslation;
import com.gulfnet.shared_library.repository.MenuCategoryMappingRepository;
import com.gulfnet.shared_library.repository.CategoryItemMappingRepository;

import com.gulfnet.shared_library.model.request.MenuRequest;
import com.gulfnet.shared_library.model.response.dto.MenuResponse;
import com.gulfnet.shared_library.model.response.dto.MenuListResponse;
import com.gulfnet.shared_library.model.response.dto.MenuDto;
import com.gulfnet.shared_library.model.response.dto.ResponseDto;
import com.gulfnet.shared_library.model.response.dto.MenuVersionsResponse;
import com.gulfnet.shared_library.enums.MenuStatus;
import com.gulfnet.shared_library.model.request.AssignMenuToRestaurantGroupRequest;
import com.gulfnet.shared_library.model.request.DuplicateMenuRequest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.domain.Specification;
import com.gulfnet.shared_library.config.AWSService;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.util.stream.Collectors;

import java.util.ArrayList;
import java.util.HashMap;
import com.gulfnet.shared_library.model.response.dto.MenuListData;
import lombok.extern.slf4j.Slf4j;

import com.gulfnet.shared_library.entity.Menu;
import com.gulfnet.shared_library.entity.MenuTranslation;
import com.gulfnet.shared_library.entity.User;
import com.gulfnet.shared_library.entity.Kds;
import com.gulfnet.shared_library.entity.KdsTranslation;
import com.gulfnet.shared_library.entity.CategoryKds;
import com.gulfnet.shared_library.entity.KdsConfiguration;
import com.gulfnet.shared_library.entity.Role;
import com.gulfnet.shared_library.entity.Order;
import com.gulfnet.shared_library.entity.OrderedItem;
import com.gulfnet.shared_library.entity.OrderedCombo;
import com.gulfnet.shared_library.entity.Transaction;
import com.gulfnet.shared_library.enums.ItemStatus;
import com.gulfnet.shared_library.enums.OrderStatus;
import com.gulfnet.shared_library.enums.TransactionStatus;
import com.gulfnet.shared_library.repository.KdsRepository;
import com.gulfnet.shared_library.repository.OrderRepository;
import com.gulfnet.shared_library.repository.OrderedItemRepository;
import com.gulfnet.shared_library.repository.OrderedComboRepository;
import com.gulfnet.shared_library.repository.TransactionRepository;
import com.gulfnet.shared_library.repository.KdsTranslationRepository;
import com.gulfnet.shared_library.repository.CategoryKdsRepository;
import com.gulfnet.shared_library.repository.KdsConfigurationRepository;
import com.gulfnet.shared_library.repository.MenuRepository;
import com.gulfnet.shared_library.repository.MenuTranslationRepository;
import com.gulfnet.shared_library.repository.RestaurantGroupMenuMappingRepository;
import com.gulfnet.shared_library.repository.RestaurantMenuMappingRepository;
import com.gulfnet.shared_library.entity.Restaurant;
import com.gulfnet.shared_library.entity.RestaurantGroupMenuMapping;
import com.gulfnet.shared_library.entity.RestaurantMenuMapping;
import com.gulfnet.shared_library.entity.RestaurantGroupMenuId;
import com.gulfnet.shared_library.entity.RestaurantMenuId;
import com.gulfnet.shared_library.repository.RestaurantGroupRepository;
import com.gulfnet.shared_library.repository.RestaurantRepository;
import com.gulfnet.shared_library.repository.CategoryRepository;
import com.gulfnet.shared_library.repository.CategoryTranslationRepository;
import com.gulfnet.shared_library.repository.RestaurantGroupTranslationRepository; 
import com.gulfnet.shared_library.repository.ItemRepository;
import com.gulfnet.restaurantmanagement.util.MessageUtil;
import com.gulfnet.shared_library.util.TranslationUtils;
import com.gulfnet.shared_library.entity.RestaurantGroupTranslation;
import com.gulfnet.shared_library.entity.RestaurantTranslation;
import com.gulfnet.shared_library.repository.RestaurantTranslationRepository;
import com.gulfnet.restaurantmanagement.service.MenuService;
import com.gulfnet.restaurantmanagement.service.AuditTrailService;
import com.gulfnet.restaurantmanagement.service.NotificationService;
import com.gulfnet.restaurantmanagement.service.OrderNotificationService;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import com.gulfnet.restaurantmanagement.config.LocalizationProperties;
import com.gulfnet.restaurantmanagement.config.RestaurantChainConfigProperties;
import com.gulfnet.shared_library.entity.AuditTrail;
import com.gulfnet.shared_library.util.CurrencyFormatter;
import java.util.LinkedHashMap;
import com.gulfnet.shared_library.repository.ItemTranslationRepository;
import com.gulfnet.shared_library.model.response.dto.MenuItemResponse;
import com.gulfnet.shared_library.model.response.dto.RestaurantMenuDtoListResponse;
import com.gulfnet.shared_library.model.response.dto.RestaurantMenuDetailsResponseDto;
import com.gulfnet.restaurantmanagement.job.RestaurantMenuScheduleJob;
import com.gulfnet.shared_library.model.request.ScheduleMenuRequest;
import com.gulfnet.shared_library.model.response.dto.MenuItemListResponse;
import com.gulfnet.shared_library.model.response.dto.ComboTranslationDto;
import com.gulfnet.shared_library.entity.RestaurantItemAvailability;
import com.gulfnet.shared_library.repository.RestaurantItemAvailabilityRepository;
import com.gulfnet.shared_library.repository.CategoryItemMappingRepository;
import com.gulfnet.shared_library.model.request.ItemAvailabilityChangeRequest;
import com.gulfnet.shared_library.model.request.StatusEventMessage;
import java.time.format.DateTimeFormatter;
import com.gulfnet.shared_library.repository.CategoryDiscountMappingRepository;
import com.gulfnet.shared_library.repository.ItemModifierGroupRepository;
import com.gulfnet.shared_library.repository.ModifierItemRepository;
import com.gulfnet.shared_library.repository.ModifierItemTranslationRepository;
import com.gulfnet.shared_library.entity.ModifierItem;
import com.gulfnet.shared_library.entity.ModifierItemTranslation;
import com.gulfnet.shared_library.repository.ItemDiscountMappingRepository;
import com.gulfnet.shared_library.entity.ItemModifierGroup;
import com.gulfnet.shared_library.entity.ModifierGroup;
import com.gulfnet.shared_library.repository.DiscountRepository;
import com.gulfnet.shared_library.entity.CategoryDiscountMapping;
import com.gulfnet.shared_library.entity.ItemDiscountMapping;
import com.gulfnet.shared_library.entity.Discount;
import com.gulfnet.shared_library.enums.DiscountType;
import com.gulfnet.shared_library.enums.OverrideType;
import java.math.BigDecimal;
import java.math.RoundingMode;
import com.gulfnet.shared_library.repository.DiscountBxgyItemRepository;
import com.gulfnet.shared_library.entity.DiscountBxgyItem;
import com.gulfnet.shared_library.enums.DayOfWeek;
import java.time.LocalTime;
import com.gulfnet.shared_library.repository.MenuDiscountMappingRepository;
import com.gulfnet.shared_library.repository.RestaurantDiscountMappingRepository;
import com.gulfnet.shared_library.entity.MenuDiscountMapping;
import com.gulfnet.shared_library.entity.RestaurantDiscountMapping;
import com.gulfnet.shared_library.entity.RestaurantDiscountId;
import com.gulfnet.shared_library.entity.Discount;
import com.gulfnet.shared_library.entity.MenuDiscountId;
import com.gulfnet.shared_library.repository.MenuPromotionMappingRepository;
import com.gulfnet.shared_library.repository.RestaurantPromotionMappingRepository;
import com.gulfnet.shared_library.entity.MenuPromotionMapping;
import com.gulfnet.shared_library.entity.RestaurantPromotionMapping;
import com.gulfnet.shared_library.entity.RestaurantPromotionId;
import com.gulfnet.shared_library.entity.Promotion;
import com.gulfnet.shared_library.repository.ComboRepository;
import com.gulfnet.shared_library.entity.Combo;
import com.gulfnet.shared_library.model.response.dto.ComboResponse;
import com.gulfnet.shared_library.model.response.dto.BxgyDiscountDetailsResponse;
import com.gulfnet.shared_library.model.response.dto.BxgyItemDto;
import com.gulfnet.shared_library.repository.ComboTranslationRepository;
import com.gulfnet.shared_library.repository.PriceOverrideMappingRepository;
import com.gulfnet.shared_library.repository.MenuCategoryComboMappingRepository;
import com.gulfnet.shared_library.entity.MenuCategoryComboMapping;
import com.gulfnet.restaurantmanagement.util.PriceOverrideHelper;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;

@Slf4j  
@Service
@Transactional
public class MenuServiceImpl implements MenuService {

    // Entity types
    private static final String ENTITY_TYPE_MENU = "MENU";
    private static final String ENTITY_TYPE_ITEM = "ITEM";

    // Field names
    private static final String FIELD_IS_DELETED = "isDeleted";
    private static final String FIELD_STATUS = "status";
    private static final String FIELD_IS_PUBLISHED = "isPublished";

    // Audit trail messages
    private static final String MSG_NO_TRANSLATIONS = "No translations";
    private static final String MSG_MENU_CREATED = "Menu created: ";
    private static final String MSG_MENU_UPDATED = "Menu updated: ";
    private static final String MSG_MENU_DELETED = "Menu deleted: ";
    private static final String MSG_MENU_PUBLISHED = "Menu published: ";

    // Message keys
    private static final String MSG_MENUS_ERROR_ALREADY_DELETED = "menus.error.already.deleted";
    private static final String MSG_USER_NOT_FOUND = "user.not.found";
    private static final String MSG_MENU_NOT_FOUND = "menu.not.found";
    private static final String MSG_ITEM_NOT_FOUND = "item.not.found";
    private static final String MSG_RESTAURANT_NOT_FOUND = "restaurant.not.found";
    private static final String MSG_CATEGORY_NOT_FOUND = "category.not.found";
    private static final String MSG_MENU_ERROR_INVALID_LANGUAGE = "error.invalid.language";
    private static final String PREFIX_CATEGORY = "Category (";
    private static final String JOB_DATA_KEY_MENU_ID = "menuId";


    @Autowired
    private  MenuRepository menuRepository;


    @Autowired
    private MenuTranslationRepository menuTranslationRepository;

    @Autowired
    private EmailSender emailSender;


    @Autowired
    private MessageUtil messageUtil;


    @Autowired
    private LocalizationProperties localizationProperties;

    @Autowired
    private MenuPublishAsyncService menuPublishAsyncService;
    
    @Autowired
    private RestaurantChainConfigProperties restaurantChainConfigProperties;
    
    @Autowired
    private  CategoryItemMappingRepository categoryItemMappingRepository;

    @Autowired
    private  MenuCategoryMappingRepository menuCategoryMappingRepository;

    @Autowired
    private MenuStructureRepository menuStructureRepository;

    @Autowired
    private ComboRepository comboRepository;

    @Autowired
    private ComboTranslationRepository comboTranslationRepository;

    @Autowired
    private AWSService awsService;

    @Autowired
    private UserRepository userRepository;


    @Autowired
    private CategoryRepository categoryRepository;  

    @Autowired
    private ItemRepository itemRepository;

    @Autowired
    private RestaurantGroupTranslationRepository restaurantGroupTranslationRepository; 

    @Autowired
    private  RestaurantGroupMenuMappingRepository groupMenuMappingRepository;

    @Autowired
    private  RestaurantMenuMappingRepository restaurantMenuMappingRepository;

    @Autowired
    private RestaurantGroupRepository restaurantGroupRepository;


    @Autowired
    private RestaurantRepository restaurantRepository;

    @Autowired
    private RestaurantTranslationRepository restaurantTranslationRepository;

    @Autowired
    private Scheduler scheduler; 

    @Autowired
    private ApplicationContext applicationContext;

    @Autowired
    private AuditTrailService auditTrailService;

    @Autowired
    private ItemTranslationRepository itemTranslationRepository;

    @Autowired
    private RestaurantItemAvailabilityRepository restaurantItemAvailabilityRepository;

    @PersistenceContext
    private EntityManager entityManager;

    @Autowired
    private CategoryDiscountMappingRepository categoryDiscountMappingRepository;

    @Autowired
    private ItemDiscountMappingRepository itemDiscountMappingRepository;

    @Autowired
    private DiscountRepository discountRepository;

    @Autowired
    private DiscountBxgyItemRepository discountBxgyItemRepository;

    @Autowired
    private MenuDiscountMappingRepository menuDiscountMappingRepository;

    @Autowired
    private RestaurantDiscountMappingRepository restaurantDiscountMappingRepository;

    @Autowired
    private MenuPromotionMappingRepository menuPromotionMappingRepository;

    @Autowired
    private RestaurantPromotionMappingRepository restaurantPromotionMappingRepository;

    @Autowired
    private PriceOverrideMappingRepository priceOverrideMappingRepository;

    @Autowired
    private PriceOverrideHelper priceOverrideHelper;

    @Autowired
    private OrderNotificationService orderNotificationService;

    @Autowired
    private NotificationService notificationService;

    @Autowired(required = false)
    private SimpMessagingTemplate messagingTemplate;

    @Autowired
    private KdsRepository kdsRepository;

    @Autowired
    private KdsTranslationRepository kdsTranslationRepository;

    @Autowired
    private CategoryKdsRepository categoryKdsRepository;

    @Autowired
    private KdsConfigurationRepository kdsConfigurationRepository;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private OrderedItemRepository orderedItemRepository;

    @Autowired
    private OrderedComboRepository orderedComboRepository;

    @Autowired
    private TransactionRepository transactionRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private ItemModifierGroupRepository itemModifierGroupRepository;

    @Autowired
    private ModifierItemRepository modifierItemRepository;

    @Autowired
    private ModifierItemTranslationRepository modifierItemTranslationRepository;

    @Autowired
    private CategoryTranslationRepository categoryTranslationRepository;

    @Autowired
    private MenuCategoryComboMappingRepository menuCategoryComboMappingRepository;

    /**
     * Creates a new menu with translations and menu structure assignment.
     * Validates translation uniqueness, menu name uniqueness per language, and creates menu entity.
     * Sets initial status to DRAFT and version to 1.0.
     *
     * @param userId  the ID of the user creating the menu
     * @param request the menu creation request with translations and menu structure ID
     * @param locale  locale code for localized error messages
     * @return ResponseDto containing the created menu response
     * @throws ResponseStatusException if validation fails, user not found, or menu name exists
     */
    @Override
    @Transactional
    public ResponseDto<MenuDto<MenuResponse>> createMenu(String userId, MenuRequest request, String locale) {
        Locale userLocale = Locale.forLanguageTag(locale);
    
        // Fetch user
        User user = userRepository.findById(UUID.fromString(userId))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        messageUtil.getMessage(MSG_USER_NOT_FOUND, userLocale, userId)));
    
        // Validate translations
        List<MenuTranslationDto> translations = request.getTranslations();
        if (translations != null && !translations.isEmpty()) {
            // Validate that at least one translation has a non-empty name
            boolean hasValidName = translations.stream()
                .anyMatch(t -> t.getName() != null && !t.getName().trim().isEmpty());
            
            if (!hasValidName) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        messageUtil.getMessage("item.update.error.no.valid.name", userLocale));
            }
            
            Set<String> languageCodes = new HashSet<>();
            Set<String> menuNames = new HashSet<>();
            for (MenuTranslationDto entry : translations) {
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
        
                    boolean exists = menuTranslationRepository.existsByNameAndLanguageCode(name.trim(), languageCode);
                    if (exists) {
                        throw new ResponseStatusException(HttpStatus.CONFLICT,
                                messageUtil.getMessage("menus.error.name.exists", userLocale, name.trim(), languageCode));
                    }
                }
            }
        } else {
            // No translations provided at all
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    messageUtil.getMessage("combo.translations.required", userLocale));
        }
    

        Menu menu = new Menu();
        menu.setVersion(1.0); // Always set version 1.0 for new menu creation
        // ✅ Put the check here
        if (request.getStatus() != null) {
            MenuStatus status;
            try {
                status = MenuStatus.valueOf(request.getStatus());
            } catch (IllegalArgumentException e) {
                throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    messageUtil.getMessage("menus.error.invalid.status", userLocale, request.getStatus())
                );
            }

            // ❌ Prevent creating published menus
            if (status == MenuStatus.PUBLISHED) {
                throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    messageUtil.getMessage("menus.publish.error.no", userLocale)
                );
            }

            menu.setStatus(status);
        } else {
            menu.setStatus(MenuStatus.DRAFT); // default
        }
        
        menu.setCreatedAt(OffsetDateTime.now(ZoneOffset.UTC));
        menu.setCreatedBy(user);
        menu = menuRepository.save(menu);
        
        // Set menuMasterId to the menu's own ID initially
        // This will be updated when menu structure is assigned
        menu.setMenuMasterId(menu.getId());
        menu = menuRepository.save(menu);

        String createdByName = user.getFirstName() + " " + user.getLastName();
    
        // Save translations
        if (translations != null && !translations.isEmpty()) {
            for (MenuTranslationDto entry : translations) {
                String name = entry.getName();
                if (name != null && !name.trim().isEmpty()) {
                    MenuTranslation translation = new MenuTranslation();
                    translation.setMenu(menu);
                    translation.setName(name.trim());
                    translation.setLanguageCode(entry.getLanguageCode());
                    translation.setDescription(entry.getDescription()); 
                    menuTranslationRepository.save(translation);
                }
            }
        }
    
        // Fetch saved translations for response
        // Make sure you have this in MenuTranslationRepository:
        // @Query("SELECT mt FROM MenuTranslation mt WHERE mt.menu.id = :menuId")
        List<MenuTranslation> savedTranslations = menuTranslationRepository.findByMenuId(menu.getId());
    
        List<MenuTranslationDto> translationDTOs = savedTranslations.stream()
                .map(t -> MenuTranslationDto.builder()
                        .languageCode(t.getLanguageCode())
                        .name(t.getName())
                        .description(t.getDescription())
                        .build())
                .collect(Collectors.toList());
    
        // Build response
        MenuResponse menuResponse = MenuResponse.builder()
                .id(menu.getId())
                .menuMasterId(menu.getMenuMasterId())
                .version(menu.getVersion())
                .status(menu.getStatus().name())
                .translations(translationDTOs)
                .createdAt(menu.getCreatedAt() != null ? menu.getCreatedAt().toLocalDateTime() : null)
                .createdBy(createdByName)
                .menuStructureId(menu.getMenuStructure() != null ? menu.getMenuStructure().getId() : null)
                .build();
                
    
        MenuDto<MenuResponse> menuDto = MenuDto.<MenuResponse>builder()
                .menu(menuResponse)
                .build();
    
        // Create audit trail for menu creation
        try {
            Restaurant restaurant = null;
            if (user.getRestaurantId() != null) {
                restaurant = restaurantRepository.findById(user.getRestaurantId()).orElse(null);
            }
            auditTrailService.createAuditTrail(
                    user,
                    ActionType.MENU_CREATE,
                    restaurant,
                    null, // status - will default to NA for non-request actions
                    null, // ipAddress - not available in this context
                    null, // userAgent - not available in this context
                    menu.getId(),
                    ENTITY_TYPE_MENU,
                    MSG_MENU_CREATED + (menuResponse.getTranslations().isEmpty() ? MSG_NO_TRANSLATIONS : 
                        menuResponse.getTranslations().get(0).getName())
            );
        } catch (Exception e) {
            log.error("Failed to create audit trail for menu creation: {}", e.getMessage());
            // Don't break menu creation flow if audit trail fails
        }
    
        return ResponseDto.<MenuDto<MenuResponse>>builder()
                .message(messageUtil.getMessage("menus.create.success", userLocale))
                .data(menuDto)
                .build();
    }


    
    /**
     * Retrieves a paginated and filterable list of menus.
     * Supports filtering by status, published state, deletion status, and search by name.
     * Results are sorted and paginated with locale-aware name sorting.
     *
     * @param page        page number for pagination
     * @param size        page size for pagination
     * @param status      optional filter by menu status (excludes ARCHIVED)
     * @param isPublished optional filter by published state
     * @param search      optional search term for menu name
     * @param sortBy      field to sort by
     * @param direction   sort direction
     * @param locale      locale code for localized responses and sorting
     * @param isDeleted   optional filter by deletion status (true shows deleted, false shows non-deleted)
     * @return ResponseDto containing paginated list of menus
     * @throws ResponseStatusException if locale is invalid
     */
    @Override
    @Transactional(readOnly = true)
    public ResponseDto<MenuListResponse> getMenus(
                Integer page,
                Integer size,
                MenuStatus status,
                Boolean isPublished,
                String search,
                String sortBy,
                Sort.Direction direction,
                String locale,
                Boolean isDeleted) {
        
            Locale userLocale = Locale.forLanguageTag(locale);
        
            // Validate & normalize pagination
            int pageNumber = (page != null ? page : 1) - 1;
            if (pageNumber < 0) pageNumber = 0;
            int pageSize = (size != null && size > 0) ? size : Integer.MAX_VALUE;
        
            // Validate locale
            if (!localizationProperties.getLanguages().contains(locale)) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        messageUtil.getMessage(MSG_MENU_ERROR_INVALID_LANGUAGE, userLocale)
                );
            }
        
            // Build specification
            Specification<Menu> spec = (root, query, cb) -> {
                List<Predicate> predicates = new ArrayList<>();
                predicates.add(cb.notEqual(root.get(FIELD_STATUS), MenuStatus.ARCHIVED));
                // Handle isDeleted filter: if isDeleted=true, show deleted; otherwise show non-deleted (default)
                if (isDeleted != null && isDeleted) {
                    predicates.add(cb.equal(root.get(FIELD_IS_DELETED), true));
                } else {
                    predicates.add(cb.equal(root.get(FIELD_IS_DELETED), false));
                }
        
                if (status != null && status != MenuStatus.ARCHIVED) {
                    predicates.add(cb.equal(root.get(FIELD_STATUS), status));
                }
                if (isPublished != null) {
                    predicates.add(cb.equal(root.get(FIELD_IS_PUBLISHED), isPublished));
                }
                if (search != null && !search.trim().isEmpty()) {
                    String searchTerm = "%" + search.toLowerCase() + "%";
                    Join<Menu, MenuTranslation> translationJoin = root.join("translations");
                    predicates.add(cb.like(cb.lower(translationJoin.get("name")), searchTerm));
                }
        
                return cb.and(predicates.toArray(new Predicate[0]));
            };
        
            // Fetch menus (ignore DB sort for name)
            List<Menu> menus = menuRepository.findAll(spec);
        
            // Map -> DTO
            List<MenuListData> menuResponses = menus.stream()
                    .map(menu -> {
                        List<MenuTranslation> menuTranslations = menu.getTranslations();
                        List<MenuListTranslationDto> translationDTOs = new ArrayList<>();
                        
                        if (!menuTranslations.isEmpty()) {
                            // Try exact match first
                            MenuTranslation exactMatch = menuTranslations.stream()
                                    .filter(t -> t.getLanguageCode() != null && t.getLanguageCode().equals(locale))
                                    .findFirst()
                                    .orElse(null);
                            
                            if (exactMatch != null) {
                                // Use exact match
                                translationDTOs.add(MenuListTranslationDto.builder()
                                        .languageCode(exactMatch.getLanguageCode())
                                        .name(exactMatch.getName())
                                        .build());
                            } else {
                                // Fallback using TranslationUtils
                                java.util.Optional<MenuTranslation> fallback =
                                        TranslationUtils.pickPreferredOrFromList(
                                                menuTranslations,
                                                locale,
                                                localizationProperties.getLanguages(),
                                                MenuTranslation::getLanguageCode
                                        );
                                fallback.ifPresent(trans -> translationDTOs.add(MenuListTranslationDto.builder()
                                        .languageCode(trans.getLanguageCode())
                                        .name(trans.getName())
                                        .build()));
                            }
                        }
        
                        String createdByName = (menu.getCreatedBy() != null)
                                ? menu.getCreatedBy().getFirstName() + " " + menu.getCreatedBy().getLastName()
                                : null;
        
                        String updatedByName = (menu.getUpdatedBy() != null)
                                ? menu.getUpdatedBy().getFirstName() + " " + menu.getUpdatedBy().getLastName()
                                : null;
        
                        return MenuListData.builder()
                                .id(menu.getId())
                                .menuMasterId(menu.getMenuMasterId())
                                .status(menu.getStatus())
                                .menuStructureId(menu.getMenuStructure() != null ? menu.getMenuStructure().getId() : null)
                                .version(menu.getVersion())
                                .translations(translationDTOs)
                                .isDeleted(menu.getIsDeleted())
                                .createdBy(createdByName)
                                .createdAt(menu.getCreatedAt() != null ? menu.getCreatedAt().toLocalDateTime() : null)
                                .updatedBy(updatedByName)
                                .updatedAt(menu.getUpdatedAt() != null ? menu.getUpdatedAt().toLocalDateTime() : null)
                                .restaurantGroupCount(0L)
                                .restaurantCount(0L)
                                .build();
                    })
                    .collect(Collectors.toList());
        
            // ✅ Sorting
            if ("name".equalsIgnoreCase(sortBy)) {
                LocaleSortUtil.sortName(menuResponses, sortBy, direction);
            } else if ("createdAt".equalsIgnoreCase(sortBy)) {
                Comparator<MenuListData> comp = Comparator.comparing(MenuListData::getCreatedAt,
                        Comparator.nullsLast(Comparator.naturalOrder()));
                if (direction == Sort.Direction.DESC) comp = comp.reversed();
                menuResponses.sort(comp);
            } else if ("updatedAt".equalsIgnoreCase(sortBy)) {
                Comparator<MenuListData> comp = Comparator.comparing(MenuListData::getUpdatedAt,
                        Comparator.nullsLast(Comparator.naturalOrder()));
                if (direction == Sort.Direction.DESC) comp = comp.reversed();
                menuResponses.sort(comp);
            }
            // (Add more fields if needed)
        
            // ✅ Pagination
            int fromIndex = Math.min(pageNumber * pageSize, menuResponses.size());
            int toIndex   = Math.min(fromIndex + pageSize, menuResponses.size());
            List<MenuListData> paginatedResponses = menuResponses.subList(fromIndex, toIndex);
        
            // ✅ Metadata
            PaginationMetaData metaData = PaginationMetaData.builder()
                    .page(pageNumber + 1)
                    .size(pageSize)
                    .totalPages((int) Math.ceil((double) menuResponses.size() / pageSize))
                    .totalRecords((long) menuResponses.size())
                    .build();
        
            // ✅ Final response
            MenuListResponse listResponse = MenuListResponse.builder()
                    .menus(paginatedResponses)
                    .count((long) paginatedResponses.size())
                    .total((long) menuResponses.size())
                    .metaData(metaData)
                    .build();
        
            return ResponseDto.<MenuListResponse>builder()
                    .message(messageUtil.getMessage("menus.list.success", userLocale))
                    .data(listResponse)
                    .build();
        }
        
    
    /**
     * Updates an existing menu with new translations and menu structure.
     * Validates translation uniqueness and menu name uniqueness per language.
     * Updates menu entity and translations.
     *
     * @param id      the UUID of the menu to update
     * @param userId  the ID of the user performing the update
     * @param request the menu update request with new translations and menu structure ID
     * @param locale  locale code for localized error messages
     * @return ResponseDto containing the updated menu response
     * @throws ResponseStatusException if menu not found, validation fails, or menu name exists
     */
    @Override
    @Transactional
    public ResponseDto<MenuDto<MenuResponse>> updateMenu(UUID id, String userId, MenuRequest request, String locale) {
        Locale userLocale = Locale.forLanguageTag(locale);

        // Fetch user
        User user = userRepository.findById(UUID.fromString(userId))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        messageUtil.getMessage(MSG_USER_NOT_FOUND, userLocale, userId)));

        // Find the menu
        Menu menu = menuRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        messageUtil.getMessage(MSG_MENU_NOT_FOUND, userLocale)));

         // Check if menu is deleted
        if (Boolean.TRUE.equals(menu.getIsDeleted())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    messageUtil.getMessage("menus.update.error.deleted", userLocale));
        }                

        // Update translations if provided
        List<MenuTranslationDto> translations = request.getTranslations();
        if (translations != null && !translations.isEmpty()) {
            // Validate that at least one translation has a non-empty name
            boolean hasValidName = translations.stream()
                .anyMatch(t -> t.getName() != null && !t.getName().trim().isEmpty());
            
            if (!hasValidName) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        messageUtil.getMessage("item.update.error.no.valid.name", userLocale));
            }
            
            // Check for duplicate languages and validate language codes
            Set<String> languageCodes = new HashSet<>();
            for (MenuTranslationDto t : translations) {
                String name = t.getName();
                String languageCode = t.getLanguageCode();
                
                if (name != null && !name.trim().isEmpty() && languageCode != null) {
                    if (!languageCodes.add(languageCode)) {
                        throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                                messageUtil.getMessage("menu.error.duplicate.language", userLocale, languageCode));
                    }
                    
                    // Validate language code
                    if (!localizationProperties.getLanguages().contains(languageCode)) {
                        throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                                messageUtil.getMessage(MSG_MENU_ERROR_INVALID_LANGUAGE, userLocale));
                    }
                    
                    // Check if name exists on a different logical menu (exclude this menu and same menuMasterId)
                    boolean exists = menuTranslationRepository.existsByNameAndLanguageCodeAndMenuIdNotExcludingSameMaster(
                            name.trim(), languageCode, id, menu.getMenuMasterId());
                    if (exists) {
                        throw new ResponseStatusException(HttpStatus.CONFLICT,
                                messageUtil.getMessage("menus.error.name.exists", userLocale, name.trim(), languageCode));
                    }
                }
            }

            // Delete and flush existing translations
            menuTranslationRepository.deleteByMenuId(id);
            menuTranslationRepository.flush();

            // Create and save new translations (only non-empty names)
            List<MenuTranslation> newTranslations = translations.stream()
                    .filter(t -> t.getName() != null && !t.getName().trim().isEmpty())
                    .map(t -> menuTranslationRepository.save(MenuTranslation.builder()
                            .menu(menu)
                            .languageCode(t.getLanguageCode())
                            .name(t.getName().trim())
                            .description(t.getDescription())
                            .build()))
                    .collect(Collectors.toList());

            // Update menu translations
            menu.getTranslations().clear();
            menu.getTranslations().addAll(newTranslations);
        } else {
            // No translations provided at all
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    messageUtil.getMessage("combo.translations.required", userLocale));
        }

        // Update audit fields
        menu.setUpdatedAt(OffsetDateTime.now(ZoneOffset.UTC));
        menu.setUpdatedBy(user);

        // Save menu
        menuRepository.save(menu);

        // Create audit trail for menu edit
        try {
            Restaurant restaurant = null;
            // Menu edits are typically done at HQ level, so restaurant may be null
            // But if user has a restaurant, we can include it
            if (user.getRestaurantId() != null) {
                restaurant = restaurantRepository.findById(user.getRestaurantId()).orElse(null);
            }
            auditTrailService.createAuditTrail(
                    user,
                    ActionType.MENU_UPDATE,
                    restaurant,
                    null, // status - will default to NA for non-request actions
                    null, // ipAddress - not available in this context
                    null, // userAgent - not available in this context
                    menu.getId(),
                    ENTITY_TYPE_MENU,
                    MSG_MENU_UPDATED + (menu.getTranslations().isEmpty() ? MSG_NO_TRANSLATIONS : 
                        menu.getTranslations().get(0).getName())
            );
        } catch (Exception e) {
            log.error("Failed to create audit trail for menu update: {}", e.getMessage());
            // Don't break menu update flow if audit trail fails
        }

        // Build response
        List<MenuTranslationDto> translationDTOs = menu.getTranslations().stream()
                .map(t -> MenuTranslationDto.builder()
                        .languageCode(t.getLanguageCode())
                        .name(t.getName())
                        .description(t.getDescription())
                        .build())
                .collect(Collectors.toList());

         MenuResponse menuResponse = MenuResponse.builder()
                .id(menu.getId())
                .version(menu.getVersion())
                .status(menu.getStatus().name())
                .translations(translationDTOs)
                .createdAt(menu.getCreatedAt() != null ? menu.getCreatedAt().toLocalDateTime() : null)
                .createdBy(menu.getCreatedBy().getFirstName() + " " + 
                        menu.getCreatedBy().getLastName())
                .updatedAt(menu.getUpdatedAt() != null ? menu.getUpdatedAt().toLocalDateTime() : null)
                .updatedBy(user.getFirstName() + " " + user.getLastName())
                .menuStructureId(menu.getMenuStructure() != null ? menu.getMenuStructure().getId() : null)
                .build();

        return ResponseDto.<MenuDto<MenuResponse>>builder()
                .message(messageUtil.getMessage("menus.update.success", userLocale))
                .data(MenuDto.<MenuResponse>builder()
                        .menu(menuResponse)
                        .build())
                .build();
    }

    /**
     * Retrieves a single menu by ID with all translations and details.
     *
     * @param id     the UUID of the menu to retrieve
     * @param locale locale code for localized responses
     * @return ResponseDto containing the menu details
     * @throws ResponseStatusException if menu not found or locale is invalid
     */
    @Override            
    @Transactional(readOnly = true)
    public ResponseDto<MenuDto<MenuResponse>> getMenuById(UUID id, String locale) {
        // Convert string locale to Locale object
        Locale userLocale = Locale.forLanguageTag(locale);
        
        // Validate locale
        if (!localizationProperties.getLanguages().contains(locale)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        messageUtil.getMessage(MSG_MENU_ERROR_INVALID_LANGUAGE, userLocale));
        }
        
        Menu menu = menuRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    messageUtil.getMessage(MSG_MENU_NOT_FOUND, userLocale)
                ));

        if (Boolean.TRUE.equals(menu.getIsDeleted())) {
            throw new ResponseStatusException(
                HttpStatus.NOT_FOUND,
                messageUtil.getMessage(MSG_MENUS_ERROR_ALREADY_DELETED, userLocale)
            );
        }

        // Convert translations to DTOs (detail endpoint should return all translations)
        List<MenuTranslationDto> translationDtos = menu.getTranslations().stream()
                .map(translation -> MenuTranslationDto.builder()
                        .languageCode(translation.getLanguageCode())
                        .name(translation.getName())
                        .description(translation.getDescription())
                        .build())
                .collect(Collectors.toList());

        // Determine if menu is editable based on status and menu master ID
        Boolean isEditable = true;
        
        // Case 1: If menu is published, it's not editable
        if (MenuStatus.PUBLISHED.equals(menu.getStatus())) {
            isEditable = false;
        } 
        // Case 2: If menu is draft, check if there's a published version with same menu master ID
        else if (MenuStatus.DRAFT.equals(menu.getStatus()) && menu.getMenuMasterId() != null) {
            // Check if there's a published menu with the same master ID
            List<Menu> publishedMenusWithSameMasterId = menuRepository.findByMenuMasterIdAndStatusAndIsDeletedFalseOrderByVersionDesc(
                menu.getMenuMasterId(), MenuStatus.PUBLISHED);
            
            // If there's a published menu with the same master ID, this draft is not editable
            if (!publishedMenusWithSameMasterId.isEmpty()) {
                isEditable = false;
            }
        }

        // Build the response
        MenuResponse menuResponse = MenuResponse.builder()
                .id(menu.getId())
                .menuMasterId(menu.getMenuMasterId())
                .version(menu.getVersion())
                .status(menu.getStatus().toString())
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
                
                return ResponseDto.<MenuDto<MenuResponse>>builder()
                .data(MenuDto.<MenuResponse>builder()
                        .menu(menuResponse)
                        .build())
                .message(messageUtil.getMessage("menus.view.success", userLocale))
                .build();
    }


    /**
     * Soft deletes a menu by setting isDeleted flag to true.
     * Validates that menu is not assigned to restaurants/restaurant groups.
     *
     * @param id     the UUID of the menu to delete
     * @param userId the ID of the user performing the deletion
     * @param locale locale code for localized error messages
     * @return ResponseDto with success message
     * @throws ResponseStatusException if menu not found, already deleted, or assigned to restaurants/restaurant groups
     */
    @Override
    @Transactional
    public ResponseDto<String> deleteMenu(UUID id, String userId, String locale) {
        Locale userLocale = Locale.forLanguageTag(locale);

        // Fetch user
        User user = userRepository.findById(UUID.fromString(userId))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        messageUtil.getMessage(MSG_USER_NOT_FOUND, userLocale, userId)));

        // Find the menu
        Menu menu = menuRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        messageUtil.getMessage(MSG_MENU_NOT_FOUND, userLocale)));

        // Check if menu is already deleted
        if (Boolean.TRUE.equals(menu.getIsDeleted())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        messageUtil.getMessage(MSG_MENUS_ERROR_ALREADY_DELETED, userLocale));
        }

        // Check if menu is assigned to any restaurants
        if (restaurantMenuMappingRepository.existsById_MenuId(id)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    messageUtil.getMessage("menus.delete.error.assigned.to.restaurant", userLocale));
        }

        // Check if menu is assigned to any restaurant groups
        if (groupMenuMappingRepository.existsById_MenuId(id)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    messageUtil.getMessage("menus.delete.error.assigned.to.restaurant.group", userLocale));
        }

        // Perform soft delete
        menu.setIsDeleted(true);
        menu.setUpdatedAt(OffsetDateTime.now(ZoneOffset.UTC));
        menu.setUpdatedBy(user);
        menuRepository.save(menu);

        // Create audit trail for menu deletion
        try {
            Restaurant restaurant = null;
            if (user.getRestaurantId() != null) {
                restaurant = restaurantRepository.findById(user.getRestaurantId()).orElse(null);
            }
            auditTrailService.createAuditTrail(
                    user,
                    ActionType.MENU_DELETE,
                    restaurant,
                    null, // status - will default to NA for non-request actions
                    null, // ipAddress - not available in this context
                    null, // userAgent - not available in this context
                    menu.getId(),
                    ENTITY_TYPE_MENU,
                    MSG_MENU_DELETED + (menu.getTranslations().isEmpty() ? MSG_NO_TRANSLATIONS : 
                        menu.getTranslations().get(0).getName())
            );
        } catch (Exception e) {
            log.error("Failed to create audit trail for menu deletion: {}", e.getMessage());
            // Don't break menu deletion flow if audit trail fails
        }

        return ResponseDto.<String>builder()
                .message(messageUtil.getMessage("menus.delete.success", userLocale))
                .data("Menu with ID " + id + " has been deleted")
                .build();
    
    }
    

    public ResponseDto<Void> assignMenuStructureAndCategories(
            AssignMenuStructureCategoriesRequest request, 
            String userId,
            String localeStr) {
            
        Locale locale = new Locale(localeStr);
        
        // Fetch user
        User user = userRepository.findById(UUID.fromString(userId))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        messageUtil.getMessage(MSG_USER_NOT_FOUND, locale, userId)));
            
        // 1. Validate menu structure exists
        MenuStructure menuStructure = menuStructureRepository.findById(request.getMenuStructureId())
            .orElseThrow(() -> new ResponseStatusException(
                HttpStatus.NOT_FOUND,
                messageUtil.getMessage(MSG_MENU_NOT_FOUND, locale, request.getMenuStructureId())
            ));
        
        // Validate that menu structure is active
        if (!EntityStatus.ACTIVE.equals(menuStructure.getStatus())) {
            throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                messageUtil.getMessage("menu.structure.inactive", locale)
            );
        }
            
        // 2. Validate menu exists
            Menu menu = menuRepository.findById(request.getMenuId())
            .orElseThrow(() -> new ResponseStatusException(
                HttpStatus.NOT_FOUND,
                messageUtil.getMessage(MSG_MENU_NOT_FOUND, locale, request.getMenuId())
            ));
        
        // Track previous state for audit log
        MenuStructure previousMenuStructure = menu.getMenuStructure();
        Set<UUID> previousCategoryIds = new HashSet<>();
        List<MenuCategoryMapping> previousMenuCategoryMappings = menuCategoryMappingRepository.findByMenuId(menu.getId());
        for (MenuCategoryMapping mcm : previousMenuCategoryMappings) {
            previousCategoryIds.add(mcm.getCategory().getId());
        }
        Set<UUID> previousComboIds = new HashSet<>();
        for (MenuCategoryMapping mcm : previousMenuCategoryMappings) {
            List<MenuCategoryComboMapping> comboMappings = menuCategoryComboMappingRepository
                    .findByMenuCategoryMapping_Id(mcm.getId());
            for (MenuCategoryComboMapping comboMapping : comboMappings) {
                if (comboMapping.getCombo() != null) {
                    previousComboIds.add(comboMapping.getCombo().getComboId());
                }
            }
        }
        List<MenuDiscountMapping> previousDiscountMappings = menuDiscountMappingRepository.findByMenuId(menu.getId());
        Set<UUID> previousDiscountIds = previousDiscountMappings.stream()
                .map(mdm -> mdm.getDiscount() != null ? mdm.getDiscount().getId() : null)
                .filter(java.util.Objects::nonNull)
                .collect(Collectors.toSet());
        List<MenuPromotionMapping> previousPromotionMappings = menuPromotionMappingRepository.findByMenu_Id(menu.getId());
        Set<UUID> previousPromotionIds = previousPromotionMappings.stream()
                .map(mpm -> mpm.getPromotion() != null ? mpm.getPromotion().getId() : null)
                .filter(java.util.Objects::nonNull)
                .collect(Collectors.toSet());
            
        // 3. Validate no duplicate items across categories/subcategories
        validateNoDuplicateItems(request, locale);
            
        // 4. Assign menu structure to menu and update menuMasterId
        menu.setMenuStructure(menuStructure);
        menu.setUpdatedAt(OffsetDateTime.now(ZoneOffset.UTC));
        menu.setUpdatedBy(user);
        
        // Update menuMasterId to match other menus with the same menu structure
      
        
        menuRepository.save(menu);
        
        // --- FIXED: Collect all data BEFORE any deletions ---
        Set<UUID> currentCategoryIdsInRequest = new HashSet<>();
        Set<UUID> categoriesAndSubcategoriesInRequest = new HashSet<>(); // To collect all category and subcategory IDs for item reconciliation
        
        for (CategoryAssignment categoryAssignment : request.getCategories()) {
            currentCategoryIdsInRequest.add(categoryAssignment.getId());
            categoriesAndSubcategoriesInRequest.add(categoryAssignment.getId());

            if (categoryAssignment.getSubcategories() != null) {
                for (SubCategoryAssignment subCategoryAssignment : categoryAssignment.getSubcategories()) {
                    currentCategoryIdsInRequest.add(subCategoryAssignment.getId());
                    categoriesAndSubcategoriesInRequest.add(subCategoryAssignment.getId()); // Add subcategory IDs
                }
            }
        }

        // Collect all target (categoryId, itemId) pairs from the request
        Set<Map.Entry<UUID, UUID>> currentCategoryItemPairsInRequest = new HashSet<>();
        for (CategoryAssignment categoryAssignment : request.getCategories()) {
            // Direct items for the main category
            List<ResolvedItemAssignment> categoryItems = resolveItemAssignments(categoryAssignment.getItems());
            if (categoryItems != null) {
                for (ResolvedItemAssignment itemAssignment : categoryItems) {
                    currentCategoryItemPairsInRequest.add(Map.entry(categoryAssignment.getId(), itemAssignment.getItemId()));
                }
            }
            // Items within subcategories
            if (categoryAssignment.getSubcategories() != null) {
                for (SubCategoryAssignment subCategoryAssignment : categoryAssignment.getSubcategories()) {
                    List<ResolvedItemAssignment> subCategoryItems = resolveItemAssignments(subCategoryAssignment.getItems());
                    if (subCategoryItems != null) {
                        for (ResolvedItemAssignment itemAssignment : subCategoryItems) {
                            currentCategoryItemPairsInRequest.add(Map.entry(subCategoryAssignment.getId(), itemAssignment.getItemId()));
                        }
                    }
                }
            }
        }

        // Fetch all existing mappings BEFORE any deletions
        List<MenuCategoryMapping> existingMenuCategoryMappings = menuCategoryMappingRepository.findByMenuId(menu.getId());
        List<MenuCategoryMapping> scopeMcms = existingMenuCategoryMappings.stream()
            .filter(mcm -> categoriesAndSubcategoriesInRequest.contains(mcm.getCategory().getId()))
            .collect(Collectors.toList());
        List<CategoryItemMapping> existingCategoryItemMappings = categoryItemMappingRepository.findByMenuCategoryMappingIn(scopeMcms);

        // --- STEP 1: Delete RestaurantItemAvailability records FIRST (before CategoryItemMapping) ---
        // Get all restaurants that have this menu assigned (needed for deletion)
        List<RestaurantMenuMapping> restaurantMenuMappings = restaurantMenuMappingRepository.findById_MenuId(menu.getId());
        Set<UUID> restaurantIds = restaurantMenuMappings.stream()
                .map(rmm -> rmm.getRestaurant().getId())
                .collect(Collectors.toSet());
        
        // Collect CategoryItemMapping IDs that will be deleted
        List<CategoryItemMapping> categoryItemMappingsToDelete = new ArrayList<>();
        for (CategoryItemMapping existingItemMapping : existingCategoryItemMappings) {
            UUID existingCategoryId = existingItemMapping.getMenuCategoryMapping().getCategory().getId();
            UUID existingItemId = existingItemMapping.getItem().getId();
            if (!currentCategoryItemPairsInRequest.contains(Map.entry(existingCategoryId, existingItemId))) {
                categoryItemMappingsToDelete.add(existingItemMapping);
            }
        }
        
        // Delete ALL restaurant_item_availability records for mappings that will be deleted
        // (across ALL restaurants - foreign key constraint requires this)
        // Using native query to delete all records regardless of restaurant
        if (!categoryItemMappingsToDelete.isEmpty()) {
            List<UUID> categoryItemMappingIdsToDelete = categoryItemMappingsToDelete.stream()
                    .map(CategoryItemMapping::getId)
                    .collect(Collectors.toList());
            
            // Delete ALL dependent records for these category_item_mapping IDs before deleting the mappings
            // This handles foreign key constraints from multiple tables
            if (!categoryItemMappingIdsToDelete.isEmpty()) {
                // 1. Delete restaurant_item_availability records
                StringBuilder queryBuilder1 = new StringBuilder(
                        "DELETE FROM restaurant_item_availability WHERE category_item_mapping_id IN (");
                for (int i = 0; i < categoryItemMappingIdsToDelete.size(); i++) {
                    if (i > 0) queryBuilder1.append(",");
                    queryBuilder1.append("?");
                }
                queryBuilder1.append(")");
                
                jakarta.persistence.Query deleteQuery1 = entityManager.createNativeQuery(queryBuilder1.toString());
                for (int i = 0; i < categoryItemMappingIdsToDelete.size(); i++) {
                    deleteQuery1.setParameter(i + 1, categoryItemMappingIdsToDelete.get(i));
                }
                int deletedCount1 = deleteQuery1.executeUpdate();
                
                // 2. Delete item_discount_mapping records
                StringBuilder queryBuilder2 = new StringBuilder(
                        "DELETE FROM item_discount_mapping WHERE category_item_mapping_id IN (");
                for (int i = 0; i < categoryItemMappingIdsToDelete.size(); i++) {
                    if (i > 0) queryBuilder2.append(",");
                    queryBuilder2.append("?");
                }
                queryBuilder2.append(")");
                
                jakarta.persistence.Query deleteQuery2 = entityManager.createNativeQuery(queryBuilder2.toString());
                for (int i = 0; i < categoryItemMappingIdsToDelete.size(); i++) {
                    deleteQuery2.setParameter(i + 1, categoryItemMappingIdsToDelete.get(i));
                }
                int deletedCount2 = deleteQuery2.executeUpdate();
                
                // 3. Delete combo_item_mapping records
                StringBuilder queryBuilder3 = new StringBuilder(
                        "DELETE FROM combo_item_mapping WHERE category_item_mapping_id IN (");
                for (int i = 0; i < categoryItemMappingIdsToDelete.size(); i++) {
                    if (i > 0) queryBuilder3.append(",");
                    queryBuilder3.append("?");
                }
                queryBuilder3.append(")");
                
                jakarta.persistence.Query deleteQuery3 = entityManager.createNativeQuery(queryBuilder3.toString());
                for (int i = 0; i < categoryItemMappingIdsToDelete.size(); i++) {
                    deleteQuery3.setParameter(i + 1, categoryItemMappingIdsToDelete.get(i));
                }
                int deletedCount3 = deleteQuery3.executeUpdate();
                
                // 4. Delete discount_bxgy_item records (both buy_item_ids and get_item_ids)
                StringBuilder queryBuilder4 = new StringBuilder(
                        "DELETE FROM discount_bxgy_item WHERE buy_item_ids IN (");
                for (int i = 0; i < categoryItemMappingIdsToDelete.size(); i++) {
                    if (i > 0) queryBuilder4.append(",");
                    queryBuilder4.append("?");
                }
                queryBuilder4.append(") OR get_item_ids IN (");
                for (int i = 0; i < categoryItemMappingIdsToDelete.size(); i++) {
                    if (i > 0) queryBuilder4.append(",");
                    queryBuilder4.append("?");
                }
                queryBuilder4.append(")");
                
                jakarta.persistence.Query deleteQuery4 = entityManager.createNativeQuery(queryBuilder4.toString());
                int paramIndex = 1;
                // Set buy_item_ids parameters
                for (UUID id : categoryItemMappingIdsToDelete) {
                    deleteQuery4.setParameter(paramIndex++, id);
                }
                // Set get_item_ids parameters
                for (UUID id : categoryItemMappingIdsToDelete) {
                    deleteQuery4.setParameter(paramIndex++, id);
                }
                int deletedCount4 = deleteQuery4.executeUpdate();
                
                entityManager.flush(); // Ensure all deletions are committed before proceeding
                
                log.debug("Deleted dependent records for {} category item mapping(s): {} availability, {} item_discount, {} combo_item, {} bxgy", 
                        categoryItemMappingIdsToDelete.size(), deletedCount1, deletedCount2, deletedCount3, deletedCount4);
            }
        }
        
        // --- STEP 2: Delete CategoryItemMapping records ---
        // Batch delete all CategoryItemMapping records that need to be removed
        if (!categoryItemMappingsToDelete.isEmpty()) {
            categoryItemMappingRepository.deleteAll(categoryItemMappingsToDelete);
        }
        
        // --- ADDITIONAL STEP: Delete ALL CategoryItemMapping records for MenuCategoryMappings that will be removed ---
        List<MenuCategoryMapping> menuCategoryMappingsToDelete = new ArrayList<>();
        for (MenuCategoryMapping existingMapping : existingMenuCategoryMappings) {
            if (!currentCategoryIdsInRequest.contains(existingMapping.getCategory().getId())) {
                menuCategoryMappingsToDelete.add(existingMapping);
            }
        }
        
        // Delete all CategoryItemMapping records for the MenuCategoryMappings that will be removed
        if (!menuCategoryMappingsToDelete.isEmpty()) {
            List<CategoryItemMapping> allCategoryItemMappingsForRemoval = 
                categoryItemMappingRepository.findByMenuCategoryMappingIn(menuCategoryMappingsToDelete);
            
            // Delete ALL restaurant_item_availability records first (before CategoryItemMapping)
            // (across ALL restaurants - foreign key constraint requires this)
            // Using native query to delete all records regardless of restaurant
            if (!allCategoryItemMappingsForRemoval.isEmpty()) {
                List<UUID> categoryItemMappingIdsForRemoval = allCategoryItemMappingsForRemoval.stream()
                        .map(CategoryItemMapping::getId)
                        .collect(Collectors.toList());
                
                // Delete ALL dependent records for these category_item_mapping IDs before deleting the mappings
                // This handles foreign key constraints from multiple tables
                if (!categoryItemMappingIdsForRemoval.isEmpty()) {
                    // 1. Delete restaurant_item_availability records
                    StringBuilder queryBuilder1 = new StringBuilder(
                            "DELETE FROM restaurant_item_availability WHERE category_item_mapping_id IN (");
                    for (int i = 0; i < categoryItemMappingIdsForRemoval.size(); i++) {
                        if (i > 0) queryBuilder1.append(",");
                        queryBuilder1.append("?");
                    }
                    queryBuilder1.append(")");
                    
                    jakarta.persistence.Query deleteQuery1 = entityManager.createNativeQuery(queryBuilder1.toString());
                    for (int i = 0; i < categoryItemMappingIdsForRemoval.size(); i++) {
                        deleteQuery1.setParameter(i + 1, categoryItemMappingIdsForRemoval.get(i));
                    }
                    int deletedCount1 = deleteQuery1.executeUpdate();
                    
                    // 2. Delete item_discount_mapping records
                    StringBuilder queryBuilder2 = new StringBuilder(
                            "DELETE FROM item_discount_mapping WHERE category_item_mapping_id IN (");
                    for (int i = 0; i < categoryItemMappingIdsForRemoval.size(); i++) {
                        if (i > 0) queryBuilder2.append(",");
                        queryBuilder2.append("?");
                    }
                    queryBuilder2.append(")");
                    
                    jakarta.persistence.Query deleteQuery2 = entityManager.createNativeQuery(queryBuilder2.toString());
                    for (int i = 0; i < categoryItemMappingIdsForRemoval.size(); i++) {
                        deleteQuery2.setParameter(i + 1, categoryItemMappingIdsForRemoval.get(i));
                    }
                    int deletedCount2 = deleteQuery2.executeUpdate();
                    
                    // 3. Delete combo_item_mapping records
                    StringBuilder queryBuilder3 = new StringBuilder(
                            "DELETE FROM combo_item_mapping WHERE category_item_mapping_id IN (");
                    for (int i = 0; i < categoryItemMappingIdsForRemoval.size(); i++) {
                        if (i > 0) queryBuilder3.append(",");
                        queryBuilder3.append("?");
                    }
                    queryBuilder3.append(")");
                    
                    jakarta.persistence.Query deleteQuery3 = entityManager.createNativeQuery(queryBuilder3.toString());
                    for (int i = 0; i < categoryItemMappingIdsForRemoval.size(); i++) {
                        deleteQuery3.setParameter(i + 1, categoryItemMappingIdsForRemoval.get(i));
                    }
                    int deletedCount3 = deleteQuery3.executeUpdate();
                    
                    // 4. Delete discount_bxgy_item records (both buy_item_ids and get_item_ids)
                    StringBuilder queryBuilder4 = new StringBuilder(
                            "DELETE FROM discount_bxgy_item WHERE buy_item_ids IN (");
                    for (int i = 0; i < categoryItemMappingIdsForRemoval.size(); i++) {
                        if (i > 0) queryBuilder4.append(",");
                        queryBuilder4.append("?");
                    }
                    queryBuilder4.append(") OR get_item_ids IN (");
                    for (int i = 0; i < categoryItemMappingIdsForRemoval.size(); i++) {
                        if (i > 0) queryBuilder4.append(",");
                        queryBuilder4.append("?");
                    }
                    queryBuilder4.append(")");
                    
                    jakarta.persistence.Query deleteQuery4 = entityManager.createNativeQuery(queryBuilder4.toString());
                    int paramIndex = 1;
                    // Set buy_item_ids parameters
                    for (UUID id : categoryItemMappingIdsForRemoval) {
                        deleteQuery4.setParameter(paramIndex++, id);
                    }
                    // Set get_item_ids parameters
                    for (UUID id : categoryItemMappingIdsForRemoval) {
                        deleteQuery4.setParameter(paramIndex++, id);
                    }
                    int deletedCount4 = deleteQuery4.executeUpdate();
                    
                    entityManager.flush(); // Ensure all deletions are committed before proceeding
                    
                    log.debug("Deleted dependent records for {} category item mapping(s) (removed categories, menu: {}): {} availability, {} item_discount, {} combo_item, {} bxgy", 
                            categoryItemMappingIdsForRemoval.size(), menu.getId(), deletedCount1, deletedCount2, deletedCount3, deletedCount4);
                }
            }
            
            // Now delete the CategoryItemMapping records
            if (!allCategoryItemMappingsForRemoval.isEmpty()) {
                categoryItemMappingRepository.deleteAll(allCategoryItemMappingsForRemoval);
            }
        }
        // --- End Reconcile CategoryItemMapping ---

        // --- STEP 2: Delete MenuCategoryMapping SECOND (parent records) ---
        // Batch delete all MenuCategoryMapping records that need to be removed
        if (!menuCategoryMappingsToDelete.isEmpty()) {
            menuCategoryMappingRepository.deleteAll(menuCategoryMappingsToDelete);
        }
        // --- End Reconcile MenuCategoryMapping ---

        // 4. Process categories (existing logic for add/update)
        for (CategoryAssignment categoryAssignment : request.getCategories()) {
            Category category = categoryRepository.findById(categoryAssignment.getId())
                .orElseThrow(() -> new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    messageUtil.getMessage(MSG_CATEGORY_NOT_FOUND, locale, categoryAssignment.getId())
                ));
                
            // Validate category belongs to menu structure
            if (!category.getMenuStructure().getId().equals(menuStructure.getId())) {
                throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    messageUtil.getMessage("category.not.in.menu.structure", locale, 
                        category.getId(), menuStructure.getId())
                );
            }
            
            // Create or update menu-category mapping (status might have been updated during reconciliation)
            Optional<MenuCategoryMapping> existingMenuCategoryMapping = menuCategoryMappingRepository.findByMenuIdAndCategoryId(menu.getId(), category.getId());
            
            MenuCategoryMapping menuCategoryMapping;
            if (existingMenuCategoryMapping.isPresent()) {
                menuCategoryMapping = existingMenuCategoryMapping.get();
                menuCategoryMapping.setStatus(categoryAssignment.getStatus() != null ? 
                    categoryAssignment.getStatus() : EntityStatus.ACTIVE);
            } else {
                // This else branch should ideally not be hit if reconciliation logic is perfect, but kept as a safeguard
                menuCategoryMapping = MenuCategoryMapping.builder()
                    .menu(menu)
                    .category(category)
                    .status(categoryAssignment.getStatus() != null ? 
                        categoryAssignment.getStatus() : EntityStatus.ACTIVE)
                    .build();
            }
            menuCategoryMappingRepository.save(menuCategoryMapping);
            
            // Handle subcategories if present
            if (categoryAssignment.getSubcategories() != null && !categoryAssignment.getSubcategories().isEmpty()) {
                processSubCategories(menu, category, categoryAssignment.getSubcategories(), locale);
            }
            // Handle direct items if present
            else if (hasItems(categoryAssignment.getItems())) {
                processDirectItems(menu.getId(), category,
                    resolveItemAssignments(categoryAssignment.getItems()),
                    categoryAssignment.getStatus(), locale);
            }
            // If both subcategories and items are null/empty, only the menu-category mapping is created.
            // No action needed here for items if they are null/empty, as the reconciliation already handled deletions.
        }
        
        // Create audit trail for menu structure, combos, discounts, and promotions assignment
        try {
            Restaurant restaurant = null;
            if (user.getRestaurantId() != null) {
                restaurant = restaurantRepository.findById(user.getRestaurantId()).orElse(null);
            }
            
            // Collect information about what was newly assigned
            List<String> assignmentDetails = new ArrayList<>();
            
            // Track newly assigned menu structure
            boolean menuStructureChanged = (previousMenuStructure == null && menuStructure != null) ||
                    (previousMenuStructure != null && menuStructure != null && 
                     !previousMenuStructure.getId().equals(menuStructure.getId()));
            
            if (menuStructureChanged) {
                String menuStructureName = "N/A";
                if (menuStructure.getTranslations() != null && !menuStructure.getTranslations().isEmpty()) {
                    menuStructureName = menuStructure.getTranslations().get(0).getName();
                } else {
                    menuStructureName = menuStructure.getId().toString();
                }
                assignmentDetails.add(String.format("Menu Structure assigned: %s (ID: %s)", 
                        menuStructureName, 
                        menuStructure.getId()));
            }
            
            // Track newly assigned combos (check ALL combos in ALL categories, not just newly assigned categories)
            List<MenuCategoryMapping> currentMenuCategoryMappings = menuCategoryMappingRepository.findByMenuId(menu.getId());
            
            Set<UUID> newlyAssignedComboIds = new HashSet<>();
            for (MenuCategoryMapping mcm : currentMenuCategoryMappings) {
                // Check combos in ALL categories (both newly assigned and existing)
                List<MenuCategoryComboMapping> comboMappings = menuCategoryComboMappingRepository
                        .findByMenuCategoryMapping_Id(mcm.getId());
                for (MenuCategoryComboMapping comboMapping : comboMappings) {
                    if (comboMapping.getCombo() != null) {
                        UUID comboId = comboMapping.getCombo().getComboId();
                        // Track if this combo wasn't in the previous state
                        if (!previousComboIds.contains(comboId)) {
                            newlyAssignedComboIds.add(comboId);
                        }
                    }
                }
            }
            
            if (!newlyAssignedComboIds.isEmpty()) {
                assignmentDetails.add(String.format("Combos assigned: %d combo(s)", newlyAssignedComboIds.size()));
            }
            
            // Track newly assigned discounts (discounts that exist now but didn't before)
            List<MenuDiscountMapping> currentDiscountMappings = menuDiscountMappingRepository.findByMenuId(menu.getId());
            Set<UUID> newlyAssignedDiscountIds = new HashSet<>();
            for (MenuDiscountMapping mdm : currentDiscountMappings) {
                if (mdm.getDiscount() != null) {
                    UUID discountId = mdm.getDiscount().getId();
                    if (!previousDiscountIds.contains(discountId)) {
                        newlyAssignedDiscountIds.add(discountId);
                    }
                }
            }
            
            if (!newlyAssignedDiscountIds.isEmpty()) {
                assignmentDetails.add(String.format("Discounts assigned: %d discount(s)", newlyAssignedDiscountIds.size()));
            }
            
            // Track newly assigned promotions (promotions that exist now but didn't before)
            List<MenuPromotionMapping> currentPromotionMappings = menuPromotionMappingRepository.findByMenu_Id(menu.getId());
            Set<UUID> newlyAssignedPromotionIds = new HashSet<>();
            for (MenuPromotionMapping mpm : currentPromotionMappings) {
                if (mpm.getPromotion() != null) {
                    UUID promotionId = mpm.getPromotion().getId();
                    if (!previousPromotionIds.contains(promotionId)) {
                        newlyAssignedPromotionIds.add(promotionId);
                    }
                }
            }
            
            if (!newlyAssignedPromotionIds.isEmpty()) {
                assignmentDetails.add(String.format("Promotions assigned: %d promotion(s)", newlyAssignedPromotionIds.size()));
            }
            
            // Only create audit log if something was actually assigned
            if (!assignmentDetails.isEmpty()) {
                String notes = String.join("; ", assignmentDetails);
                
                auditTrailService.createAuditTrail(
                        user,
                        ActionType.MENU_UPDATE,
                        restaurant,
                        null, // status - will default to NA for non-request actions
                        null, // ipAddress - not available in this context
                        null, // userAgent - not available in this context
                        menu.getId(),
                        "MENU",
                        "Menu structure and assignments updated: " + notes
                );
                log.debug("Created audit trail for menu structure assignment: Menu ID: {}, User: {}", 
                        menu.getId(), user.getUserCode());
            } else {
                log.debug("No new assignments detected for menu structure assignment: Menu ID: {}", menu.getId());
            }
        } catch (Exception e) {
            log.error("Failed to create audit trail for menu structure assignment: {}", e.getMessage(), e);
            // Don't break menu structure assignment flow if audit trail fails
        }
        
        return ResponseDto.<Void>builder()
            .message(messageUtil.getMessage("menu.structure.assignment.success", locale))
            .build();
    }

    private boolean hasItems(List<?> items) {
        return items != null && !items.isEmpty();
    }

    /**
     * Normalizes {@code items} from menu-structure assignment payloads into {@link ResolvedItemAssignment}
     * rows: supports legacy entries that are plain {@link UUID}s (order type {@link ItemOrderType#BOTH}),
     * and objects that expose {@code getItemId()} / {@code getItemOrderType()} via reflection (e.g. bound
     * DTOs). Skips {@code null} elements and objects with no resolvable item id; missing order type defaults
     * to {@link ItemOrderType#BOTH}.
     *
     * @param items heterogeneous list from the request; may be {@code null}
     * @return mutable list, empty (not {@code null}) when input is {@code null} or empty
     */
    private List<ResolvedItemAssignment> resolveItemAssignments(List<?> items) {
        if (items == null || items.isEmpty()) {
            return Collections.emptyList();
        }
        List<ResolvedItemAssignment> resolved = new ArrayList<>();
        for (Object assignment : items) {
            resolveOneItemAssignment(assignment).ifPresent(resolved::add);
        }
        return resolved;
    }

    /**
     * Resolves a single assignment entry (UUID or object with item id / order type via reflection).
     */
    private Optional<ResolvedItemAssignment> resolveOneItemAssignment(Object assignment) {
        if (assignment == null) {
            return Optional.empty();
        }
        if (assignment instanceof UUID uuid) {
            return Optional.of(new ResolvedItemAssignment(uuid, ItemOrderType.BOTH));
        }
        UUID itemId = readItemIdViaReflection(assignment);
        if (itemId == null) {
            return Optional.empty();
        }
        ItemOrderType orderType = readItemOrderTypeViaReflection(assignment);
        return Optional.of(new ResolvedItemAssignment(itemId, orderType != null ? orderType : ItemOrderType.BOTH));
    }

    /**
     * Reads {@code getItemId()} from a loosely typed assignment object (CSV/DTO shapes), or {@code null} if unsupported.
     */
    private UUID readItemIdViaReflection(Object assignment) {
        try {
            java.lang.reflect.Method getter = assignment.getClass().getMethod("getItemId");
            Object value = getter.invoke(assignment);
            if (value instanceof UUID) {
                return (UUID) value;
            }
        } catch (Exception ignored) {
            // Optional DTO shapes may omit getItemId(); ignore and fall through to null.
        }
        return null;
    }

    /**
     * Reads {@code getItemOrderType()} from a loosely typed assignment object, or {@code null} when not present.
     */
    private ItemOrderType readItemOrderTypeViaReflection(Object assignment) {
        try {
            java.lang.reflect.Method getter = assignment.getClass().getMethod("getItemOrderType");
            Object value = getter.invoke(assignment);
            if (value instanceof ItemOrderType) {
                return (ItemOrderType) value;
            }
        } catch (Exception ignored) {
            // Optional DTO shapes may omit getItemOrderType(); ignore and fall through to null.
        }
        return null;
    }

    private static class ResolvedItemAssignment {
        private final UUID itemId;
        private final ItemOrderType itemOrderType;

        private ResolvedItemAssignment(UUID itemId, ItemOrderType itemOrderType) {
            this.itemId = itemId;
            this.itemOrderType = itemOrderType;
        }

        private UUID getItemId() {
            return itemId;
        }

        private ItemOrderType getItemOrderType() {
            return itemOrderType;
        }
    }
    
    /**
     * Validates that no item appears in multiple categories or subcategories
     * @throws ResponseStatusException if duplicate items are found, with item names in the message
     */
    private void validateNoDuplicateItems(AssignMenuStructureCategoriesRequest request, Locale locale) {
        // Map to track which categories/subcategories each item belongs to
        // Key: itemId, Value: List of category/subcategory names where the item appears
        Map<UUID, List<String>> itemToCategoriesMap = new HashMap<>();
        
        // Collect all unique item IDs to fetch translations in batch
        Set<UUID> allItemIds = new HashSet<>();
        
        // First pass: collect all items and track their locations
        for (CategoryAssignment categoryAssignment : request.getCategories()) {
            String categoryName = getCategoryName(categoryAssignment.getId(), locale);
            
            // Check direct items in category
            List<ResolvedItemAssignment> categoryItems = resolveItemAssignments(categoryAssignment.getItems());
            if (categoryItems != null && !categoryItems.isEmpty()) {
                for (ResolvedItemAssignment itemAssignment : categoryItems) {
                    UUID itemId = itemAssignment.getItemId();
                    allItemIds.add(itemId);
                    itemToCategoriesMap.computeIfAbsent(itemId, k -> new ArrayList<>())
                        .add(categoryName);
                }
            }
            
            // Check items in subcategories
            if (categoryAssignment.getSubcategories() != null && !categoryAssignment.getSubcategories().isEmpty()) {
                for (SubCategoryAssignment subCategoryAssignment : categoryAssignment.getSubcategories()) {
                    String subCategoryName = getCategoryName(subCategoryAssignment.getId(), locale);
                    List<ResolvedItemAssignment> subCategoryItems = resolveItemAssignments(subCategoryAssignment.getItems());
                    if (subCategoryItems != null && !subCategoryItems.isEmpty()) {
                        for (ResolvedItemAssignment itemAssignment : subCategoryItems) {
                            UUID itemId = itemAssignment.getItemId();
                            allItemIds.add(itemId);
                            itemToCategoriesMap.computeIfAbsent(itemId, k -> new ArrayList<>())
                                .add(subCategoryName);
                        }
                    }
                }
            }
        }
        
        // Find duplicate items (items that appear in multiple categories/subcategories)
        List<UUID> duplicateItemIds = itemToCategoriesMap.entrySet().stream()
            .filter(entry -> entry.getValue().size() > 1)
            .map(Map.Entry::getKey)
            .collect(Collectors.toList());
        
        if (!duplicateItemIds.isEmpty()) {
            // Fetch item translations in batch
            Map<UUID, List<ItemTranslation>> itemTranslationsMap = allItemIds.isEmpty() 
                ? Collections.emptyMap()
                : itemTranslationRepository.findAllByItemIdIn(new ArrayList<>(allItemIds))
                    .stream()
                    .collect(Collectors.groupingBy(t -> t.getItem().getId()));
            
            // Build error message with item names
            List<String> duplicateItemNames = new ArrayList<>();
            for (UUID itemId : duplicateItemIds) {
                String itemName = getItemName(itemId, itemTranslationsMap, locale.getLanguage());
                List<String> locations = itemToCategoriesMap.get(itemId);
                String locationsStr = String.join(", ", locations);
                duplicateItemNames.add(String.format("%s (in: %s)", itemName, locationsStr));
            }
            
            String errorMessage = String.join("; ", duplicateItemNames);
            throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                messageUtil.getMessage("menu.duplicate.items.in.categories", locale, errorMessage)
            );
        }
    }
    
    /**
     * Helper method to get item name from translations
     */
    private String getItemName(UUID itemId, Map<UUID, List<ItemTranslation>> translationsMap, String locale) {
        List<ItemTranslation> translations = translationsMap.getOrDefault(itemId, Collections.emptyList());
        if (translations.isEmpty()) {
            return "Unknown Item (" + itemId + ")";
        }

        ItemTranslation exactMatch = translations.stream()
                .filter(t -> t.getLanguageCode() != null && t.getLanguageCode().equalsIgnoreCase(locale))
                .findFirst()
                .orElse(null);

        if (exactMatch != null && exactMatch.getName() != null) {
            return exactMatch.getName();
        }

        Optional<ItemTranslation> fallback = TranslationUtils.pickPreferredOrFromList(
                translations, locale, localizationProperties.getLanguages(),
                ItemTranslation::getLanguageCode);

        return fallback.map(t -> t.getName() != null ? t.getName() : "Unknown Item")
                .orElse("Unknown Item");
    }
    
    /**
     * Helper method to get category name from translations
     */
    private String getCategoryName(UUID categoryId, Locale locale) {
        try {
            Category category = categoryRepository.findById(categoryId).orElse(null);
            if (category == null) {
                return PREFIX_CATEGORY + categoryId + ")";
            }
            
            List<CategoryTranslation> translations = categoryTranslationRepository.findByCategoryId(categoryId);
            if (translations.isEmpty()) {
                return PREFIX_CATEGORY + categoryId + ")";
            }
            
            CategoryTranslation exactMatch = translations.stream()
                .filter(t -> t.getLanguageCode() != null && t.getLanguageCode().equalsIgnoreCase(locale.getLanguage()))
                .findFirst()
                .orElse(null);
            
            if (exactMatch != null && exactMatch.getName() != null) {
                return exactMatch.getName();
            }
            
            Optional<CategoryTranslation> fallback = TranslationUtils.pickPreferredOrFromList(
                translations, locale.getLanguage(), localizationProperties.getLanguages(),
                CategoryTranslation::getLanguageCode);
            
            return fallback.map(t -> t.getName() != null ? t.getName() : "Category")
                .orElse("Category");
        } catch (Exception e) {
            return PREFIX_CATEGORY + categoryId + ")";
        }
    }
    
    /**
     * Processes direct item assignments to a category in a menu.
     * Validates that category has no subcategories (items cannot be assigned directly to categories with subcategories).
     * Creates or updates CategoryItemMapping records for each item.
     *
     * @param menuId   the UUID of the menu
     * @param category the category to assign items to
     * @param itemIds  list of item UUIDs to assign
     * @param status   entity status (not used, kept for interface compatibility)
     * @param locale   locale for localized error messages
     * @throws ResponseStatusException if category has subcategories, item not found, or category-menu mismatch
     */
    private void processDirectItems(
            UUID menuId,
            Category category, 
            List<ResolvedItemAssignment> itemAssignments,
            EntityStatus status,
            Locale locale) {
            
        // Check if category has subcategories - if so, items cannot be assigned directly
        if (categoryRepository.existsByParentCategoryIdAndIsDeletedFalse(category.getId())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    messageUtil.getMessage("category.has.subcategories.items.not.allowed", locale));
        }
            
        // This method is now only called if itemIds is NOT null and NOT empty.
        // Reconciliation already handled deletions, this part handles updates/additions.
        for (ResolvedItemAssignment itemAssignment : itemAssignments) {
            UUID itemId = itemAssignment.getItemId();
            Item item = itemRepository.findById(itemId)
                .orElseThrow(() -> new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    messageUtil.getMessage(MSG_ITEM_NOT_FOUND, locale, itemId)
                ));
                
            // Resolve MenuCategoryMapping for this category within its menu
            MenuCategoryMapping mcm = menuCategoryMappingRepository.findByMenuIdAndCategoryId(menuId, category.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    messageUtil.getMessage("category.menu.mismatch", locale)));
            
            // Check if mapping already exists
            List<CategoryItemMapping> existingMappings = categoryItemMappingRepository.findByMenuCategoryMapping(mcm);
            Optional<CategoryItemMapping> existingMapping = existingMappings.stream()
                .filter(e -> e.getItem().getId().equals(itemId))
                .findFirst();
            
            CategoryItemMapping mapping;
            if (existingMapping.isPresent()) {
                mapping = existingMapping.get();
            } else {
                // This else branch should ideally not be hit if reconciliation logic is perfect, but kept as a safeguard
                mapping = CategoryItemMapping.builder()
                    .menuCategoryMapping(mcm)
                    .item(item)
                    .build();
            }
            setCategoryItemMappingOrderType(mapping, itemAssignment.getItemOrderType() != null
                    ? itemAssignment.getItemOrderType()
                    : ItemOrderType.BOTH);
            categoryItemMappingRepository.save(mapping);
        }
    }

    private void setCategoryItemMappingOrderType(CategoryItemMapping mapping, ItemOrderType orderType) {
        if (mapping == null || orderType == null) {
            return;
        }
        try {
            java.lang.reflect.Method setter = mapping.getClass().getMethod("setItemOrderType", ItemOrderType.class);
            setter.invoke(mapping, orderType);
        } catch (Exception ignored) {
            // Shared library without itemOrderType support: keep backward-compatible behavior.
        }
    }
    
    /**
     * Processes subcategory assignments to a parent category in a menu.
     * Creates or updates MenuCategoryMapping records for subcategories with parent category reference.
     * Processes items for each subcategory if provided.
     *
     * @param menu          the menu entity
     * @param parentCategory the parent category
     * @param subCategories  list of subcategory assignments with items and status
     * @param locale        locale for localized error messages
     * @throws ResponseStatusException if subcategory not found, invalid subcategory, or validation fails
     */
    private void processSubCategories(
            Menu menu,
            Category parentCategory, 
            List<AssignMenuStructureCategoriesRequest.SubCategoryAssignment> subCategories,
            Locale locale) {
            
        for (AssignMenuStructureCategoriesRequest.SubCategoryAssignment subCategoryAssignment : subCategories) {
            Category subCategory = categoryRepository.findById(subCategoryAssignment.getId())
                .orElseThrow(() -> new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    messageUtil.getMessage("subcategory.not.found", locale, 
                        subCategoryAssignment.getId())
                ));
                
            // Validate it's actually a subcategory
            if (!subCategory.getParentCategory().getId().equals(parentCategory.getId())) {
                throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    messageUtil.getMessage("invalid.subcategory", locale,
                        subCategory.getId(), parentCategory.getId())
                );
            }
            
            // Create or update menu-subcategory mapping with parent category reference
            Optional<MenuCategoryMapping> existingSubCategoryMapping = menuCategoryMappingRepository.findByMenuIdAndCategoryId(
                menu.getId(), subCategory.getId());
            
            MenuCategoryMapping subCategoryMapping;
            if (existingSubCategoryMapping.isPresent()) {
                subCategoryMapping = existingSubCategoryMapping.get();
                subCategoryMapping.setStatus(subCategoryAssignment.getStatus() != null ? 
                    subCategoryAssignment.getStatus() : EntityStatus.ACTIVE);
                subCategoryMapping.setParentCategory(parentCategory);
            } else {
                subCategoryMapping = MenuCategoryMapping.builder()
                    .menu(menu)
                    .category(subCategory)
                    .parentCategory(parentCategory)
                    .status(subCategoryAssignment.getStatus() != null ? 
                        subCategoryAssignment.getStatus() : EntityStatus.ACTIVE)
                    .build();
            }
            menuCategoryMappingRepository.save(subCategoryMapping);
            
            // Only process items if they are present and non-empty.
            // Reconciliation already handled deletions for these.\
            if (hasItems(subCategoryAssignment.getItems())) {
                processDirectItems(
                    menu.getId(),
                    subCategory, // Pass subCategory here for mapping items to the subcategory
                    resolveItemAssignments(subCategoryAssignment.getItems()),
                    subCategoryAssignment.getStatus(),
                    locale
                );
            }
            // If items are null or empty, no CategoryItemMapping is created/updated for this subcategory's items by this call.
            // Deletions would have been handled by the broader CategoryItemMapping reconciliation phase.
        }
    }

    /**
     * Publishes a menu by setting isPublished flag to true and status to PUBLISHED.
     * Validates that menu has menu structure, categories, and items in all active categories/subcategories.
     * Transfers restaurant/group mappings from archived versions to this menu and syncs default KDS
     * category mappings for every restaurant assigned to this menu.
     *
     * @param menuId the UUID of the menu to publish
     * @param userId the ID of the user publishing the menu
     * @param locale locale code for localized error messages
     * @return ResponseDto containing the published menu response
     * @throws ResponseStatusException if menu not found, no menu structure, no categories, or validation fails
     */
    @Override
    @Transactional
    @CacheEvict(value = {"restaurants", "restaurantGroupsLite"}, allEntries = true)
    public ResponseDto<MenuDto<MenuResponse>> publishMenu(UUID menuId, String userId, String locale) {
        Locale userLocale = Locale.forLanguageTag(locale);
        
        // Find the menu
        Menu menu = menuRepository.findById(menuId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        messageUtil.getMessage(MSG_MENU_NOT_FOUND, userLocale)));

        // Check if menu is assigned to a menu structure
        MenuStructure menuStructure = menu.getMenuStructure();
        if (menuStructure == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    messageUtil.getMessage("menu.error.no.menu.structure", userLocale));
        }

        // Get all categories mapped to this menu using targeted query instead of full table scan
        List<MenuCategoryMapping> menuCategories = menuCategoryMappingRepository.findByMenuId(menuId);
                
        if (menuCategories.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    messageUtil.getMessage("menu.error.no.categories", userLocale));
        }

        // Get all categories from menu structure
        Set<UUID> menuStructureCategories = menuStructure.getCategories().stream()
                .map(Category::getId)
                .collect(Collectors.toSet());

        // Check each category for menu structure assignment and items
        for (MenuCategoryMapping menuCategory : menuCategories) {
            // Skip inactive mappings – they should not participate in publish validation
            if (!EntityStatus.ACTIVE.equals(menuCategory.getStatus())) {
                log.info(
                        "Menu publish: skipping category due to INACTIVE menu-category mapping. " +
                                "menuId={}, menuCategoryMappingId={}, categoryId={}, mappingStatus={}",
                        menuId,
                        menuCategory.getId(),
                        menuCategory.getCategory() != null ? menuCategory.getCategory().getId() : null,
                        menuCategory.getStatus()
                );
                continue;
            }

            Category category = menuCategory.getCategory();

            // Verify category belongs to menu structure
            if (!menuStructureCategories.contains(category.getId())) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        messageUtil.getMessage("menu.error.category.not.in.structure", userLocale)
                );
            }
            
            // Only check items for ACTIVE, non-combo categories
            if (EntityStatus.ACTIVE.equals(category.getStatus()) && Boolean.FALSE.equals(category.getIsCombo())) {
                // Efficiently load subcategories (excluding deleted ones) using repository method
                List<Category> subcategories =
                        categoryRepository.findByParentCategoryAndIsDeletedFalseOrderByDisplayOrderAsc(category);
                
                if (!subcategories.isEmpty()) {
                    // Category has subcategories - verify items in subcategories
                    for (Category subcategory : subcategories) {
                        log.info("Checking items for subcategory: {}", subcategory.getId());
                        
                        // Verify subcategory belongs to menu structure
                        if (!menuStructureCategories.contains(subcategory.getId())) {
                            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                                    messageUtil.getMessage("menu.error.subcategory.not.in.structure", userLocale));
                        }
                        
                        // Only check items for ACTIVE subcategories
                        if (EntityStatus.ACTIVE.equals(subcategory.getStatus())) {
                            // Efficiently find items for subcategory:
                            // 1. Get menu-category mappings for this menu & subcategory
                            List<MenuCategoryMapping> subcategoryMappings =
                                    menuCategoryMappingRepository.findByMenuIdAndCategory_IdIn(
                                            menuId, Collections.singletonList(subcategory.getId())
                                    );

                            // Skip inactive subcategory mappings from validation
                            subcategoryMappings = subcategoryMappings.stream()
                                    .filter(mapping -> EntityStatus.ACTIVE.equals(mapping.getStatus()))
                                    .collect(Collectors.toList());

                            if (subcategoryMappings.isEmpty()) {
                                log.info(
                                        "Menu publish: skipping subcategory due to no ACTIVE menu-category mappings. " +
                                                "menuId={}, subCategoryId={}",
                                        menuId,
                                        subcategory.getId()
                                );
                                continue;
                            }

                            List<CategoryItemMapping> subCategoryItems =
                                    categoryItemMappingRepository.findByMenuCategoryMappingIn(subcategoryMappings);
                            
                            if (subCategoryItems.isEmpty()) {
                                throw new ResponseStatusException(
                                        HttpStatus.BAD_REQUEST,
                                        messageUtil.getMessage("menu.error.subcategory.no.items", userLocale)
                                );
                            }
                        }
                    }
                } else {
                    // Category has no subcategories - verify direct items
                    List<MenuCategoryMapping> categoryMappings =
                            menuCategoryMappingRepository.findByMenuIdAndCategory_IdIn(
                                    menuId, Collections.singletonList(category.getId())
                            );
                    List<CategoryItemMapping> categoryItems = categoryMappings.isEmpty()
                            ? Collections.emptyList()
                            : categoryItemMappingRepository.findByMenuCategoryMappingIn(categoryMappings);
                    
                    if (categoryItems.isEmpty()) {
                        throw new ResponseStatusException(
                                HttpStatus.BAD_REQUEST,
                                messageUtil.getMessage("menu.error.category.no.items", userLocale)
                        );
                    }
                }
            }
        }

        // Update current menu status to published (synchronous, lightweight)
        User currentUser = userRepository.findById(UUID.fromString(userId))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        messageUtil.getMessage(MSG_USER_NOT_FOUND, userLocale)));

        menu.setStatus(MenuStatus.PUBLISHED);
        menu.setUpdatedAt(OffsetDateTime.now(ZoneOffset.UTC));
        menu.setUpdatedBy(currentUser);

        Menu savedMenu = menuRepository.save(menu);

        // Synchronously transfer restaurant and group mappings to ensure they're available immediately
        // This is critical for the getRestaurantsByMenuId API to work right after publish
        MenuPublishAsyncService.MappingTransferResult transferResult = null;
        try {
            transferResult = menuPublishAsyncService.transferRestaurantMappingsSynchronously(savedMenu.getId(), userId);
            log.info("Successfully transferred restaurant mappings synchronously for menu {}", savedMenu.getId());
        } catch (Exception ex) {
            log.error("Failed to transfer restaurant mappings synchronously for menu {}: {}", 
                    savedMenu.getId(), ex.getMessage(), ex);
            // Continue execution even if transfer fails - async tasks will handle cleanup
            transferResult = new MenuPublishAsyncService.MappingTransferResult(
                    new HashSet<>(), new HashSet<>(), new HashMap<>());
        }

        // Default KDS rows link to MenuCategoryMapping; after publish those mappings are for a new menu id.
        // Refresh CategoryKds for each restaurant on this menu so KDS matches the published structure.
        try {
            List<RestaurantMenuMapping> mappingsForPublishedMenu =
                    restaurantMenuMappingRepository.findById_MenuId(savedMenu.getId());
            Set<UUID> syncedRestaurantIds = new HashSet<>();
            for (RestaurantMenuMapping mapping : mappingsForPublishedMenu) {
                Restaurant restaurant = mapping.getRestaurant();
                if (restaurant == null || !syncedRestaurantIds.add(restaurant.getId())) {
                    continue;
                }
                updateDefaultKdsCategoriesForRestaurant(restaurant, currentUser, savedMenu, userLocale);
            }
        } catch (Exception e) {
            log.error("Failed to sync default KDS after menu publish for menu {}: {}", savedMenu.getId(), e.getMessage(), e);
        }

        // Trigger heavy post-publish tasks asynchronously (create/remove availability, 
        // schedule jobs, send notifications)
        // Note: Mappings are already transferred synchronously above
        try {
            menuPublishAsyncService.runPostPublishTasks(savedMenu.getId(), userId, locale, transferResult);
        } catch (Exception ex) {
            log.error("Failed to trigger async post-publish tasks for menu {}: {}", savedMenu.getId(), ex.getMessage(), ex);
        }

        // Create audit trail for menu publish
        try {
            Restaurant restaurant = null;
            if (currentUser.getRestaurantId() != null) {
                restaurant = restaurantRepository.findById(currentUser.getRestaurantId()).orElse(null);
            }
            auditTrailService.createAuditTrail(
                    currentUser,
                    ActionType.MENU_PUBLISH,
                    restaurant,
                    null, // status - will default to NA for non-request actions
                    null, // ipAddress - not available in this context
                    null, // userAgent - not available in this context
                    savedMenu.getId(),
                    ENTITY_TYPE_MENU,
                    MSG_MENU_PUBLISHED + (savedMenu.getTranslations().isEmpty() ? MSG_NO_TRANSLATIONS :
                        savedMenu.getTranslations().get(0).getName())
            );
        } catch (Exception e) {
            log.error("Failed to create audit trail for menu publish: {}", e.getMessage());
            // Don't break menu publish flow if audit trail fails
        }
        
        return ResponseDto.<MenuDto<MenuResponse>>builder()
                .data(MenuDto.<MenuResponse>builder()
                        .menu(MenuResponse.builder()
                                .id(savedMenu.getId())
                                .menuMasterId(savedMenu.getMenuMasterId())
                                .version(savedMenu.getVersion())
                                .status(savedMenu.getStatus().toString())
                                .translations(savedMenu.getTranslations().stream()
                                        .map(translation -> MenuTranslationDto.builder()
                                                .languageCode(translation.getLanguageCode())
                                                .name(translation.getName())
                                                .description(translation.getDescription())
                                                .build())
                                        .collect(Collectors.toList()))
                                .createdAt(savedMenu.getCreatedAt() != null ? savedMenu.getCreatedAt().toLocalDateTime() : null)
                                .createdBy(savedMenu.getCreatedBy() != null ? savedMenu.getCreatedBy().getId().toString() : null)
                                .updatedAt(savedMenu.getUpdatedAt() != null ? savedMenu.getUpdatedAt().toLocalDateTime() : null)
                                .updatedBy(savedMenu.getUpdatedBy() != null ? savedMenu.getUpdatedBy().getId().toString() : null)
                                .build())
                        .build())
                .message(messageUtil.getMessage("menu.published.success", userLocale))
                .build();
        }

        
    /**
     * Retrieves detailed menu information including categories, items, and modifiers.
     * Includes menu structure details and nested category/item hierarchy.
     *
     * @param menuId         the UUID of the menu
     * @param menuStructureId the UUID of the menu structure (for validation)
     * @param locale         locale code for localized responses
     * @return ResponseDto containing detailed menu information with categories and items
     * @throws ResponseStatusException if menu not found or menu structure mismatch
     */
        @Override
        @Transactional(readOnly = true)
        public ResponseDto<MenuDto<MenuResponse>> getMenuDetails(UUID menuId, UUID menuStructureId, String locale) {
            Locale userLocale = Locale.forLanguageTag(locale);
            
            // Find the menu
            Menu menu = menuRepository.findById(menuId)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                            messageUtil.getMessage(MSG_MENU_NOT_FOUND, userLocale)));
        
            // Verify menu structure
            if (menu.getMenuStructure() == null || !menu.getMenuStructure().getId().equals(menuStructureId)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        messageUtil.getMessage("menu.error.invalid.menu.structure", userLocale));
            }
        
            // Get categories mapped to this menu with ACTIVE status in MenuCategoryMapping
            List<MenuCategoryMapping> activeMenuCategories = menuCategoryMappingRepository.findAll()
                    .stream()
                    .filter(mapping -> mapping.getMenu().getId().equals(menuId) && EntityStatus.ACTIVE.equals(mapping.getStatus()))
                    .collect(Collectors.toList());
        
            // Build category response with items
            List<CategoryResponse> categoryResponses = new ArrayList<>();
            
            for (MenuCategoryMapping menuCategory : activeMenuCategories) {
                Category category = menuCategory.getCategory();
        
                // Only consider active categories
                if (EntityStatus.ACTIVE.equals(category.getStatus())) {
                    List<ItemResponse> allItemResponses = new ArrayList<>();
        
                    // Check if category has subcategories
                    List<Category> subcategories = categoryRepository.findByParentCategoryAndIsDeletedFalse(category)
                            .stream()
                            .filter(subCat -> EntityStatus.ACTIVE.equals(subCat.getStatus()))
                            .collect(Collectors.toList());
        
                    if (subcategories.isEmpty()) {
                        // No subcategories - get direct items of the category
                        MenuCategoryMapping mcm = menuCategoryMappingRepository
                            .findByMenuIdAndCategoryId(menu.getId(), category.getId()).orElse(null);
                        allItemResponses = (mcm == null ? Collections.<CategoryItemMapping>emptyList() :
                                categoryItemMappingRepository.findByMenuCategoryMapping(mcm))
                                .stream()
                                .map(mapping -> mapping.getItem())
                                .filter(item -> EntityStatus.ACTIVE.equals(item.getStatus()))
                                .map(item -> createItemResponse(item, locale))
                                .collect(Collectors.toList());
                    } else {
                        // Has subcategories - get items from all subcategories
                        for (Category subcategory : subcategories) {
                            MenuCategoryMapping subMcm = menuCategoryMappingRepository
                                .findByMenuIdAndCategoryId(menu.getId(), subcategory.getId()).orElse(null);
                            List<ItemResponse> subCategoryItems = (subMcm == null ? Collections.<CategoryItemMapping>emptyList() :
                                    categoryItemMappingRepository.findByMenuCategoryMapping(subMcm))
                                    .stream()
                                    .map(mapping -> mapping.getItem())
                                    .filter(item -> EntityStatus.ACTIVE.equals(item.getStatus()))
                                    .map(item -> createItemResponse(item, locale))
                                    .collect(Collectors.toList());
                            
                            allItemResponses.addAll(subCategoryItems);
                        }
                    }
        
                    // Only add category to response if it has items
                    if (!allItemResponses.isEmpty()) {
                        // Get category translation for requested locale
                        CategoryTranslation categoryTranslation = category.getTranslations().stream()
                                .filter(t -> t.getLanguageCode().equals(locale))
                                .findFirst()
                                .orElse(null);
        
                        categoryResponses.add(CategoryResponse.builder()
                                .category(CategoryResponse.CategoryData.builder()
                                        .id(category.getId())
                                        .menuStructureId(category.getMenuStructure().getId())
                                        .parentCategoryId(category.getParentCategory() != null ?
                                                category.getParentCategory().getId() : null)
                                        .parentCategoryName(category.getParentCategory() != null ?
                                                category.getParentCategory().getTranslations().stream()
                                                        .filter(t -> t.getLanguageCode().equals(locale))
                                                        .findFirst()
                                                        .map(CategoryTranslation::getName)
                                                        .orElse(null) : null)
                                        .status(category.getStatus())
                                        .name(categoryTranslation != null ? categoryTranslation.getName() : null)
                                        .createdBy(category.getCreatedBy() != null ?
                                                category.getCreatedBy().getFirstName() + " " + category.getCreatedBy().getLastName() : null)
                                        .createdAt(category.getCreatedAt() != null ? category.getCreatedAt().toLocalDateTime() : null)
                                        .updatedBy(category.getUpdatedBy() != null ?
                                                category.getUpdatedBy().getFirstName() + " " + category.getUpdatedBy().getLastName() : null)
                                        .updatedAt(category.getUpdatedAt() != null ? category.getUpdatedAt().toLocalDateTime() : null)
                                        .translations(Collections.singletonList(CategoryTranslationResponse.builder()
                                                .languageCode(locale)
                                                .name(categoryTranslation != null ? categoryTranslation.getName() : null)
                                                .build()))
                                        .items(allItemResponses)
                                        .build())
                                .build());
                    }
                }
            }
        
            // Get menu translation for requested locale
            MenuTranslation menuTranslation = menu.getTranslations().stream()
                    .filter(trans -> trans.getLanguageCode().equals(locale))
                    .findFirst()
                    .orElse(null);
        
            // Build final response
            MenuResponse menuResponse = MenuResponse.builder()
                    .id(menu.getId())
                    .menuMasterId(menu.getMenuMasterId())
                    .version(menu.getVersion())
                    .status(menu.getStatus().toString())
                    .translations(Collections.singletonList(MenuTranslationDto.builder()
                            .languageCode(locale)
                            .name(menuTranslation != null ? menuTranslation.getName() : null)
                            .description(menuTranslation != null ? menuTranslation.getDescription() : null)
                            .build()))
                    .categories(categoryResponses)
                    .createdAt(menu.getCreatedAt() != null ? menu.getCreatedAt().toLocalDateTime() : null)
                    .createdBy(menu.getCreatedBy() != null ? menu.getCreatedBy().getId().toString() : null)
                    .updatedAt(menu.getUpdatedAt() != null ? menu.getUpdatedAt().toLocalDateTime() : null)
                    .updatedBy(menu.getUpdatedBy() != null ? menu.getUpdatedBy().getId().toString() : null)
                    .build();
        
            return ResponseDto.<MenuDto<MenuResponse>>builder()
                    .data(MenuDto.<MenuResponse>builder()
                            .menu(menuResponse)
                            .build())
                    .message(messageUtil.getMessage("menu.details.fetched.success", userLocale))
                    .build();
        }
        
// Helper method to create ItemResponse with locale-specific translation including description
    /**
     * Creates an ItemResponse DTO from an Item entity.
     * Includes item translations, formatted prices, and pre-signed image URLs.
     *
     * @param item   the item entity to convert
     * @param locale locale code for selecting translations
     * @return ItemResponse with item details and localized information
     */
private ItemResponse createItemResponse(Item item, String locale) {
    // Get item translation for requested locale
    ItemTranslation itemTranslation = item.getTranslations().stream()
            .filter(trans -> trans.getLanguageCode().equals(locale))
            .findFirst()
            .orElse(null);

            String signedImageUrl = null;
            if (item.getImageUrl() != null && !item.getImageUrl().isEmpty()) {
                signedImageUrl = awsService.getPreSignedUrl(item.getImageUrl());
            }

    String currency = restaurantChainConfigProperties.getChain() != null ? restaurantChainConfigProperties.getChain().getCurrency() : null;
    return ItemResponse.builder()
            .id(item.getId())
            .itemCode(item.getItemCode())
            .status(item.getStatus())
            .basePrice(item.getBasePrice() != null ? CurrencyFormatter.formatAmount(BigDecimal.valueOf(item.getBasePrice()), currency).doubleValue() : null)
            .outOfStock(item.getOutOfStock())
            .imageUrl(signedImageUrl)
            .translations(Collections.singletonList(ItemTranslationDto.builder()
                    .languageCode(locale)
                    .name(itemTranslation != null ? itemTranslation.getName() : null)
                    .description(itemTranslation != null ? itemTranslation.getDescription() : null)  // Added description
                    .build()))
            .build();
}

    /**
     * Assigns a menu to one or more restaurant groups.
     * Creates restaurant menu mappings for all restaurants in the groups, assigns promotions and discounts,
     * and creates default KDS configurations for restaurants without existing default KDS.
     *
     * @param request the assignment request with menu ID and restaurant group IDs
     * @param userId  the ID of the user performing the assignment
     * @param locale  locale code for localized error messages
     * @return ResponseDto with success message
     * @throws ResponseStatusException if menu not found, restaurant group not found, or validation fails
     */
@Override
@Transactional
@CacheEvict(value = {"restaurants", "restaurantGroupsLite"}, allEntries = true)
public ResponseDto<Void> assignMenuToRestaurantGroup(
        AssignMenuToRestaurantGroupRequest request, 
        String userId,
        String locale) {
    Locale userLocale = Locale.forLanguageTag(locale);
    
    // Get user
    User user = userRepository.findById(UUID.fromString(userId))
        .orElseThrow(() -> new ResponseStatusException(
            HttpStatus.NOT_FOUND,
            messageUtil.getMessage(MSG_USER_NOT_FOUND, userLocale)
        ));

    // 1. Validate restaurant group
    RestaurantGroup restaurantGroup = restaurantGroupRepository.findById(request.getRestaurantGroupId())
        .orElseThrow(() -> new ResponseStatusException(
            HttpStatus.NOT_FOUND,
            messageUtil.getMessage("restaurant.group.not.found", userLocale)
        ));

    if (Boolean.TRUE.equals(restaurantGroup.getIsDeleted())) {
        throw new ResponseStatusException(
            HttpStatus.BAD_REQUEST,
            messageUtil.getMessage("restaurant.group.deleted", userLocale)
        );
    }

    // 2. Validate menu
    Menu menu = menuRepository.findById(request.getMenuId())
        .orElseThrow(() -> new ResponseStatusException(
            HttpStatus.NOT_FOUND,
            messageUtil.getMessage(MSG_MENU_NOT_FOUND, userLocale)
        ));

    if (Boolean.TRUE.equals(menu.getIsDeleted())) {
        throw new ResponseStatusException(
            HttpStatus.BAD_REQUEST,
            messageUtil.getMessage("menu.deleted", userLocale)
        );
    }

    if (!MenuStatus.PUBLISHED.equals(menu.getStatus())) {
        throw new ResponseStatusException(
            HttpStatus.BAD_REQUEST,
            messageUtil.getMessage("menu.not.published", userLocale)
        );
    }

    // 3. Get restaurants based on input
    if (request.getRestaurantIds() == null) {
        throw new ResponseStatusException(
            HttpStatus.BAD_REQUEST,
            messageUtil.getMessage("restaurant.ids.null", userLocale)
        );
    }
    
    List<Restaurant> restaurants;
    if (request.getRestaurantIds().size() == 1 && "*".equals(request.getRestaurantIds().get(0))) {
        // Get all active restaurants from the group
        restaurants = restaurantRepository.findByRestaurantGroup_IdAndIsDeletedFalseAndStatus(
            request.getRestaurantGroupId(), 
            EntityStatus.ACTIVE
        );
        if (restaurants.isEmpty()) {
            throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                messageUtil.getMessage("restaurant.group.no.active.restaurants", userLocale)
            );
        }
    } else {
        try {
            List<UUID> restaurantUuids = request.getRestaurantIds().stream()
                .map(UUID::fromString)
                .collect(Collectors.toList());
            restaurants = restaurantRepository.findAllById(restaurantUuids);
            if (restaurants.size() != restaurantUuids.size()) {
                throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    messageUtil.getMessage(MSG_RESTAURANT_NOT_FOUND, userLocale)
                );
            }
            for (Restaurant restaurant : restaurants) {
                if (!restaurant.getRestaurantGroup().getId().equals(request.getRestaurantGroupId())) {
                    throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        messageUtil.getMessage(
                            "restaurant.not.in.group", 
                            userLocale,
                            new Object[]{restaurant.getId()}   
                        )
                    );
                }
                if (Boolean.TRUE.equals(restaurant.getIsDeleted())) {
                    throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        messageUtil.getMessage(
                            "restaurant.deleted", 
                            userLocale,
                            new Object[]{restaurant.getId()}
                        )
                    );
                }
                if (restaurant.getStatus() != EntityStatus.ACTIVE) {
                    throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        messageUtil.getMessage(
                            "restaurant.not.active", 
                            userLocale,
                            new Object[]{restaurant.getId()}
                        )
                    );
                }
            }
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                messageUtil.getMessage("invalid.uuid.format", userLocale)
            );
        }
    }

    // 4. NEW VALIDATION: Check if any restaurant is already assigned to a different menu
    List<UUID> restaurantIds = restaurants.stream().map(Restaurant::getId).collect(Collectors.toList());
    List<RestaurantMenuMapping> existingMappings = restaurantMenuMappingRepository.findById_RestaurantIdIn(restaurantIds);
    
    // Filter out mappings for the current menu (we want to allow reassignment to the same menu)
    List<RestaurantMenuMapping> conflictingMappings = existingMappings.stream()
        .filter(mapping -> !mapping.getMenu().getId().equals(menu.getId()))
        .collect(Collectors.toList());
    
    if (!conflictingMappings.isEmpty()) {
        // Find the first conflicting restaurant to show in error message
        RestaurantMenuMapping firstConflict = conflictingMappings.get(0);
        String restaurantCode = firstConflict.getRestaurant().getRestaurantCode();
        String menuName = firstConflict.getMenu().getTranslations().stream()
            .filter(t -> t.getLanguageCode().equals(locale))
            .findFirst()
            .map(MenuTranslation::getName)
            .orElse("");
        
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,

            messageUtil.getMessage("restaurant.menu.assignment.conflict.details", userLocale, 
                restaurantCode, menuName)
        );
    }

    // 5. Create or update restaurant group to menu mapping
    Optional<RestaurantGroupMenuMapping> existingGroupMenuMapping = groupMenuMappingRepository
        .findByMenuIdAndRestaurantGroupId(menu.getId(), restaurantGroup.getId());
    if (existingGroupMenuMapping.isEmpty()) {
        RestaurantGroupMenuMapping groupMenuMapping = RestaurantGroupMenuMapping.builder()
            .id(new RestaurantGroupMenuId(restaurantGroup.getId(), menu.getId()))
            .restaurantGroup(restaurantGroup)
            .menu(menu)
            .build();
        groupMenuMappingRepository.save(groupMenuMapping);
    }

    // 6. Get existing restaurant-menu mappings for this specific menu
    List<RestaurantMenuMapping> existingRestaurantMappings = restaurantMenuMappingRepository
        .findById_RestaurantIdIn(restaurantIds)
        .stream()
        .filter(mapping -> mapping.getMenu().getId().equals(menu.getId()))
        .collect(Collectors.toList());
    
    // 7. Identify restaurants that are already assigned vs new ones
    Set<UUID> alreadyAssignedRestaurantIds = existingRestaurantMappings.stream()
        .map(mapping -> mapping.getId().getRestaurantId())
        .collect(Collectors.toSet());
    
    List<Restaurant> newRestaurants = restaurants.stream()
        .filter(restaurant -> !alreadyAssignedRestaurantIds.contains(restaurant.getId()))
        .collect(Collectors.toList());
    
    List<Restaurant> existingRestaurants = restaurants.stream()
        .filter(restaurant -> alreadyAssignedRestaurantIds.contains(restaurant.getId()))
        .collect(Collectors.toList());

    // 8. Create new restaurant to menu mappings only for unassigned restaurants
    if (!newRestaurants.isEmpty()) {
        List<RestaurantMenuMapping> newRestaurantMenuMappings = newRestaurants.stream()
            .map(restaurant -> RestaurantMenuMapping.builder()
                .id(new RestaurantMenuId(restaurant.getId(), menu.getId()))
                .restaurant(restaurant)
                .menu(menu)
                .status(RestaurantMenuMappingStatus.UNSCHEDULED)
                .build())
            .collect(Collectors.toList());
        restaurantMenuMappingRepository.saveAll(newRestaurantMenuMappings);
        
        // 9. Create KDS for new restaurants and assign menu categories
        for (Restaurant restaurant : newRestaurants) {
            createDefaultKdsForRestaurant(restaurant, user, menu, userLocale);
        }
    }
    
    // 8b. Create/update availability records for all restaurants (both new and existing)
    // This ensures that:
    // 1. New restaurants get availability records for all menu items
    // 2. Existing restaurants get availability records for any new items added to the menu
    // 3. When a menu is reassigned after unassignment, availability records are recreated
    UUID userIdUuid = UUID.fromString(userId);
    // Preload category item mappings for this menu once
    List<CategoryItemMapping> menuCategoryItemMappings =
            categoryItemMappingRepository.findByMenuCategoryMappingMenuId(menu.getId());
    
    // Create availability records for new restaurants
    for (Restaurant restaurant : newRestaurants) {
        createAvailabilityForRestaurantMenuMappingOptimized(
                restaurant.getId(), menuCategoryItemMappings, userIdUuid);
    }
    
    // Create availability records for existing restaurants (handles new items added to menu)
    // The method will skip existing records, so it's safe to call for existing restaurants
    for (Restaurant restaurant : existingRestaurants) {
        createAvailabilityForRestaurantMenuMappingOptimized(
                restaurant.getId(), menuCategoryItemMappings, userIdUuid);
    }
    
    // 9b. Update default KDS categories for existing restaurants to sync with menu
    // This ensures that when a menu is reassigned or menu categories change, 
    // the default KDS categories are updated accordingly
    for (Restaurant restaurant : existingRestaurants) {
        updateDefaultKdsCategoriesForRestaurant(restaurant, user, menu, userLocale);
    }

    // 10. Assign discounts to restaurants if menu has any discounts
    assignDiscountsToRestaurants(menu.getId(), restaurants, userLocale);

    // 11. Assign promotions to restaurants if menu has any promotions
    assignPromotionsToRestaurants(menu.getId(), restaurants, userLocale);

    // 12. Notify managers of newly assigned restaurants so they can update KDS device assignments
    if (!newRestaurants.isEmpty()) {
        try {
            Optional<Role> managerRoleOpt = roleRepository.findByName("MANAGER");
            if (managerRoleOpt.isPresent()) {
                UUID managerRoleId = managerRoleOpt.get().getId();
                for (Restaurant restaurant : newRestaurants) {
                    // Find active managers for the restaurant
                    List<User> managers = userRepository
                            .findAllByRestaurantIdAndRoleIdAndIsDeletedFalse(restaurant.getId(), managerRoleId)
                            .stream()
                            .filter(u -> u.getStatus() == EntityStatus.ACTIVE)
                            .collect(Collectors.toList());
                    
                    if (!managers.isEmpty()) {
                        notificationService.notifyMenuAssignedToRestaurant(menu, restaurant, managers, userLocale);
                    }
                }
            }
        } catch (Exception e) {
            log.error("Failed to send menu assignment notifications: {}", e.getMessage(), e);
        }
    }

    log.info("Successfully processed menu {} assignment to restaurant group {} - {} new restaurants assigned, {} already assigned", 
        menu.getId(), restaurantGroup.getId(), newRestaurants.size(), existingRestaurants.size());

    return ResponseDto.<Void>builder()
        .message(messageUtil.getMessage("menu.assignment.success", userLocale))
        .build();
}

    /**
     * Creates a default KDS (Kitchen Display System) configuration for a restaurant if one doesn't exist.
     * If a default KDS already exists, updates its categories to match the new menu.
     * Creates KDS category mappings for all categories in the menu.
     *
     * @param restaurant the restaurant entity
     * @param user       the user performing the operation
     * @param menu       the menu to create KDS for
     * @param locale     locale for localized error messages
     */
    private void createDefaultKdsForRestaurant(Restaurant restaurant, User user, Menu menu, Locale locale) {
    // Check if a default KDS already exists for this restaurant
    List<Kds> existingDefaultKds = kdsRepository.findByRestaurantIdAndIsDefaultTrueAndIsDeletedFalse(restaurant.getId());
    
    if (!existingDefaultKds.isEmpty()) {
        log.info("Default KDS already exists for restaurant {}, updating categories to match new menu", restaurant.getId());
        // Update default KDS categories to match the new menu
        updateDefaultKdsCategoriesForRestaurant(restaurant, user, menu, locale);
        return;
    }
    
    // Create default KDS
    Kds kds = Kds.builder()
        .status(EntityStatus.ACTIVE)
        .isDeleted(false)
        .isDefault(true)
        .createdAt(OffsetDateTime.now(ZoneOffset.UTC))
        .createdBy(user)
        .translations(new ArrayList<>())
        .build();
    
    kds.setRestaurantId(restaurant.getId());
    kds = kdsRepository.save(kds);
    
    // Add default KDS translations for all supported languages
    String defaultName = "Default KDS";
    for (String languageCode : localizationProperties.getLanguages()) {
        KdsTranslation translation = KdsTranslation.builder()
            .name(defaultName)
            .languageCode(languageCode)
            .kds(kds)
            .build();
        kdsTranslationRepository.save(translation);
    }
    
    // Get all categories for this menu
    List<MenuCategoryMapping> menuCategoryMappings = menuCategoryMappingRepository.findByMenuId(menu.getId());
    
    if (menuCategoryMappings.isEmpty()) {
        log.info("No categories found for menu {}", menu.getId());
        return;
    }
    
    // Assign all categories to the created KDS using MenuCategoryMapping IDs
    List<CategoryKds> categoryKdsList = new ArrayList<>();
    for (MenuCategoryMapping menuCategoryMapping : menuCategoryMappings) {
        // Check if mapping already exists
        boolean exists = categoryKdsRepository.existsByMenuCategoryMappingIdAndKdsId(menuCategoryMapping.getId(), kds.getId());
        if (!exists) {
            CategoryKds categoryKds = CategoryKds.builder()
                .menuCategoryMapping(menuCategoryMapping)
                .kds(kds)
                .build();
            categoryKdsList.add(categoryKds);
        }
    }
    
    if (!categoryKdsList.isEmpty()) {
        categoryKdsRepository.saveAll(categoryKdsList);
        log.info("Assigned {} categories to KDS {} for restaurant {}", categoryKdsList.size(), kds.getId(), restaurant.getId());
    }
    
    // Get the KDS role
    Optional<Role> kdsRoleOpt = roleRepository.findByName("KDS");
    if (kdsRoleOpt.isPresent()) {
        Role kdsRole = kdsRoleOpt.get();
        
        // Get all employees with KDS role for this restaurant
        List<User> kdsEmployees = userRepository.findAllByRestaurantIdAndRoleIdAndIsDeletedFalse(restaurant.getId(), kdsRole.getId());
        
        if (!kdsEmployees.isEmpty()) {
            // Assign all KDS employees to the created KDS
            List<KdsConfiguration> kdsConfigurationList = new ArrayList<>();
            OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
            
            for (User kdsEmployee : kdsEmployees) {
                // Check if assignment already exists
                boolean exists = kdsConfigurationRepository.existsByUserIdAndKdsId(kdsEmployee.getId(), kds.getId());
                if (!exists) {
                    KdsConfiguration configuration = KdsConfiguration.builder()
                            .user(kdsEmployee)
                            .kds(kds)
                            .createdAt(now)
                            .createdBy(user)
                            .updatedAt(now)
                            .updatedBy(user)
                            .build();
                    kdsConfigurationList.add(configuration);
                }
            }
            
            if (!kdsConfigurationList.isEmpty()) {
                kdsConfigurationRepository.saveAll(kdsConfigurationList);
                log.info(messageUtil.getMessage("kds.default.employees.assigned", locale, kdsConfigurationList.size()));
            }
        } else {
            log.info(messageUtil.getMessage("kds.default.no.employees.found", locale));
        }
    } else {
        log.warn(messageUtil.getMessage("kds.default.role.not.found", locale));
    }
}

/**
 * Updates default KDS category assignments for a restaurant when menu changes.
 * This method:
 * 1. Finds the default KDS for the restaurant
 * 2. Gets all categories from the new menu
 * 3. Removes old category assignments that are no longer in the new menu
 * 4. Adds new category assignments from the new menu
 * 5. Keeps existing assignments that are still valid
 */
private void updateDefaultKdsCategoriesForRestaurant(Restaurant restaurant, User user, Menu menu, Locale locale) {
    // Find default KDS for this restaurant
    List<Kds> defaultKdsList = kdsRepository.findByRestaurantIdAndIsDefaultTrueAndIsDeletedFalse(restaurant.getId());
    
    if (defaultKdsList.isEmpty()) {
        log.warn("No default KDS found for restaurant {}, cannot update categories", restaurant.getId());
        return;
    }
    
    Kds defaultKds = defaultKdsList.get(0);
    log.info("Updating default KDS {} categories for restaurant {} to match menu {}", 
            defaultKds.getId(), restaurant.getId(), menu.getId());
    
    // Get all categories for the new menu
    List<MenuCategoryMapping> newMenuCategoryMappings = menuCategoryMappingRepository.findByMenuId(menu.getId());
    Set<UUID> newMenuCategoryMappingIds = newMenuCategoryMappings.stream()
            .map(MenuCategoryMapping::getId)
            .collect(Collectors.toSet());
    
    // Get current category assignments for the default KDS
    List<CategoryKds> currentCategoryKdsList = categoryKdsRepository.findByKdsId(defaultKds.getId());
    Set<UUID> currentMenuCategoryMappingIds = currentCategoryKdsList.stream()
            .map(ck -> ck.getMenuCategoryMapping() != null ? ck.getMenuCategoryMapping().getId() : null)
            .filter(java.util.Objects::nonNull)
            .collect(Collectors.toSet());
    
    // Find categories to remove (in current assignments but not in new menu)
    List<CategoryKds> categoriesToRemove = currentCategoryKdsList.stream()
            .filter(ck -> {
                UUID mappingId = ck.getMenuCategoryMapping() != null ? ck.getMenuCategoryMapping().getId() : null;
                return mappingId != null && !newMenuCategoryMappingIds.contains(mappingId);
            })
            .collect(Collectors.toList());
    
    // Find categories to add (in new menu but not in current assignments)
    List<CategoryKds> categoriesToAdd = newMenuCategoryMappings.stream()
            .filter(mcm -> !currentMenuCategoryMappingIds.contains(mcm.getId()))
            .map(mcm -> CategoryKds.builder()
                    .menuCategoryMapping(mcm)
                    .kds(defaultKds)
                    .build())
            .collect(Collectors.toList());
    
    // Remove old category assignments
    if (!categoriesToRemove.isEmpty()) {
        categoryKdsRepository.deleteAll(categoriesToRemove);
        log.info("Removed {} old category assignments from default KDS {} for restaurant {}", 
                categoriesToRemove.size(), defaultKds.getId(), restaurant.getId());
    }
    
    // Add new category assignments
    if (!categoriesToAdd.isEmpty()) {
        categoryKdsRepository.saveAll(categoriesToAdd);
        log.info("Added {} new category assignments to default KDS {} for restaurant {}", 
                categoriesToAdd.size(), defaultKds.getId(), restaurant.getId());
    }
    
    if (categoriesToRemove.isEmpty() && categoriesToAdd.isEmpty()) {
        log.info("Default KDS {} categories are already in sync with menu {} for restaurant {}", 
                defaultKds.getId(), menu.getId(), restaurant.getId());
    } else {
        log.info("Successfully updated default KDS {} categories for restaurant {}: removed {}, added {}", 
                defaultKds.getId(), restaurant.getId(), categoriesToRemove.size(), categoriesToAdd.size());
    }
}

/**
 * Assign discounts to restaurants when menu is assigned
 * Gets all discounts associated with the menu and creates restaurant discount mappings
 * If no discounts are found, skips assignment (no error)
 */
private void assignDiscountsToRestaurants(UUID menuId, List<Restaurant> restaurants, Locale userLocale) {
    // Get all discounts associated with this menu
    List<MenuDiscountMapping> menuDiscountMappings = menuDiscountMappingRepository.findByMenuId(menuId);
    
    if (menuDiscountMappings == null || menuDiscountMappings.isEmpty()) {
        // Menu has no discounts, skip assignment
        log.info("Menu {} has no discounts assigned, skipping restaurant discount assignment", menuId);
        return;
    }
    
    // Create restaurant discount mappings for each discount and each restaurant
    List<RestaurantDiscountMapping> restaurantDiscountMappings = new ArrayList<>();
    
    for (MenuDiscountMapping menuDiscountMapping : menuDiscountMappings) {
        Discount discount = menuDiscountMapping.getDiscount();
        
        // Skip if discount is deleted or inactive
        if (Boolean.TRUE.equals(discount.getIsDeleted()) || discount.getStatus() != EntityStatus.ACTIVE) {
            log.debug("Skipping discount {} - deleted or inactive", discount.getId());
            continue;
        }
        
        for (Restaurant restaurant : restaurants) {
            // Check if mapping already exists
            RestaurantDiscountId id = new RestaurantDiscountId();
            id.setRestaurantId(restaurant.getId());
            id.setDiscountId(discount.getId());
            
            if (restaurantDiscountMappingRepository.existsById(id)) {
                log.debug("Restaurant discount mapping already exists for restaurant {} and discount {}", 
                    restaurant.getId(), discount.getId());
                continue;
            }
            
            // Create new restaurant discount mapping with validity fields from menu discount mapping
            RestaurantDiscountMapping mapping = RestaurantDiscountMapping.builder()
                .id(id)
                .restaurant(restaurant)
                .discount(discount)
                .validFrom(menuDiscountMapping.getValidFrom())
                .validTo(menuDiscountMapping.getValidTo())
                .startTime(menuDiscountMapping.getStartTime())
                .endTime(menuDiscountMapping.getEndTime())
                .daysOfWeek(menuDiscountMapping.getDaysOfWeek())
                .status(EntityStatus.ACTIVE)
                .build();
            
            restaurantDiscountMappings.add(mapping);
        }
    }
    
    // Save all mappings
    if (!restaurantDiscountMappings.isEmpty()) {
        restaurantDiscountMappingRepository.saveAll(restaurantDiscountMappings);
        log.info("Created {} restaurant discount mappings for menu {} and {} restaurants", 
            restaurantDiscountMappings.size(), menuId, restaurants.size());
    } else {
        log.info("No new restaurant discount mappings created for menu {} (all may already exist)", menuId);
    }
}

    /**
     * Assigns promotions associated with a menu to restaurants.
     * Creates restaurant promotion mappings for all promotions assigned to the menu.
     *
     * @param menuId      the UUID of the menu
     * @param restaurants list of restaurants to assign promotions to
     * @param userLocale  locale for localized error messages (not used but kept for consistency)
     */
private void assignPromotionsToRestaurants(UUID menuId, List<Restaurant> restaurants, Locale userLocale) {
    // Get all promotions associated with this menu
    List<MenuPromotionMapping> menuPromotionMappings = menuPromotionMappingRepository.findByMenu_Id(menuId);
    
    if (menuPromotionMappings == null || menuPromotionMappings.isEmpty()) {
        // Menu has no promotions, skip assignment
        log.info("Menu {} has no promotions assigned, skipping restaurant promotion assignment", menuId);
        return;
    }
    
    // Create restaurant promotion mappings for each promotion and each restaurant
    List<RestaurantPromotionMapping> restaurantPromotionMappings = new ArrayList<>();
    
    for (MenuPromotionMapping menuPromotionMapping : menuPromotionMappings) {
        Promotion promotion = menuPromotionMapping.getPromotion();
        
        // Skip if promotion is deleted or inactive
        if (Boolean.TRUE.equals(promotion.getIsDeleted()) || promotion.getStatus() != EntityStatus.ACTIVE) {
            log.debug("Skipping promotion {} - deleted or inactive", promotion.getId());
            continue;
        }
        
        for (Restaurant restaurant : restaurants) {
            // Check if mapping already exists
            RestaurantPromotionId id = new RestaurantPromotionId();
            id.setRestaurantId(restaurant.getId());
            id.setPromotionId(promotion.getId());
            
            if (restaurantPromotionMappingRepository.existsById(id)) {
                log.debug("Restaurant promotion mapping already exists for restaurant {} and promotion {}", 
                    restaurant.getId(), promotion.getId());
                continue;
            }
            
            // Create new restaurant promotion mapping with validity fields from menu promotion mapping
            RestaurantPromotionMapping mapping = RestaurantPromotionMapping.builder()
                .id(id)
                .restaurant(restaurant)
                .promotion(promotion)
                .validFrom(menuPromotionMapping.getValidFrom())
                .validTo(menuPromotionMapping.getValidTo())
                .status(EntityStatus.ACTIVE)
                .build();
            
            restaurantPromotionMappings.add(mapping);
        }
    }
    
    // Save all mappings
    if (!restaurantPromotionMappings.isEmpty()) {
        restaurantPromotionMappingRepository.saveAll(restaurantPromotionMappings);
        log.info("Created {} restaurant promotion mappings for menu {} and {} restaurants", 
            restaurantPromotionMappings.size(), menuId, restaurants.size());
    } else {
        log.info("No new restaurant promotion mappings created for menu {} (all may already exist)", menuId);
    }
}

    /**
     * Retrieves a paginated and filterable list of restaurant groups assigned to a menu.
     * Includes restaurant group details, assigned restaurant counts, and translations.
     * Supports filtering by status and search by code/name.
     *
     * @param menuId  the UUID of the menu
     * @param locale  locale code for localized responses
     * @param status  optional filter by entity status
     * @param search  optional search term for restaurant group code or name
     * @param page    page number for pagination
     * @param size    page size for pagination
     * @return ResponseDto containing paginated list of restaurant groups with assigned restaurant counts
     * @throws ResponseStatusException if menu not found
     */
@Override
@Transactional(readOnly = true)
public ResponseDto<MenuRestaurantGroupListResponse> getRestaurantGroupDetailsByMenuId(
        UUID menuId,
        String locale,
        EntityStatus status,
        String search,
        Integer page,
        Integer size
) {
    Locale userLocale = Locale.forLanguageTag(locale);

    // 1. Validate locale
    if (!localizationProperties.getLanguages().contains(locale)) {
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        messageUtil.getMessage(MSG_MENU_ERROR_INVALID_LANGUAGE, userLocale));
    }

    // 2. Validate menu exists
    if (!menuRepository.existsById(menuId)) {
        throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                messageUtil.getMessage(MSG_MENU_NOT_FOUND, userLocale, menuId));
    }

    // 3. Set pagination parameters
    int pageNumber = (page != null ? page : 1) - 1;
    if (pageNumber < 0) pageNumber = 0;
    int pageSize = size != null ? size : Integer.MAX_VALUE;
    if (pageSize < 1) pageSize = Integer.MAX_VALUE;

    // 4. Get restaurant group mappings for this menu
    List<RestaurantGroupMenuMapping> groupMappings = groupMenuMappingRepository.findById_MenuId(menuId);
    
    // 5. Filter by status if provided
    List<RestaurantGroup> restaurantGroups = groupMappings.stream()
        .map(RestaurantGroupMenuMapping::getRestaurantGroup)
        .filter(group -> status == null || group.getStatus() == status)
        .collect(Collectors.toList());
    
    // 6. Apply search filter if provided
    if (search != null && !search.trim().isEmpty()) {
        restaurantGroups = restaurantGroups.stream()
            .filter(group -> {
                // Get translations for this group
                List<RestaurantGroupTranslation> translations = restaurantGroupTranslationRepository
                    .findAllByRestaurantGroupIdWithLanguage(group.getId());
                
                // Check if any translation matches the search term
                return translations.stream()
                    .anyMatch(translation -> translation.getName() != null && 
                        translation.getName().toLowerCase().contains(search.toLowerCase()));
            })
            .collect(Collectors.toList());
    }
    
    // 7. Build response DTOs with fallback language logic
    List<MenuRestaurantGroupDetailsResponseDto> restaurantGroupDetails = restaurantGroups.stream()
        .map(group -> {
            // Get all translations for this group
            List<RestaurantGroupTranslation> translations = restaurantGroupTranslationRepository
                .findAllByRestaurantGroupIdWithLanguage(group.getId());
            
            // Apply fallback language logic
            String groupName = "";
            if (!translations.isEmpty()) {
                // Try exact match first
                RestaurantGroupTranslation exactMatch = translations.stream()
                    .filter(t -> t.getLanguageCode() != null && t.getLanguageCode().equals(locale))
                    .findFirst()
                    .orElse(null);
                
                if (exactMatch != null) {
                    groupName = exactMatch.getName();
                } else {
                    // Fallback using TranslationUtils
                    java.util.Optional<RestaurantGroupTranslation> fallback =
                            TranslationUtils.pickPreferredOrFromList(
                                    translations,
                                    locale,
                                    localizationProperties.getLanguages(),
                                    RestaurantGroupTranslation::getLanguageCode
                            );
                    groupName = fallback.map(RestaurantGroupTranslation::getName)
                            .orElse("");
                }
            }
            
            // Count assigned restaurants for this group
            long assignedRestaurantCount = restaurantMenuMappingRepository
                .countByMenuIdAndRestaurantRestaurantGroupId(menuId, group.getId());
            
            return MenuRestaurantGroupDetailsResponseDto.builder()
                .id(group.getId())
                .name(groupName)
                .status(group.getStatus())
                .assignedRestaurantCount(assignedRestaurantCount)
                .build();
        })
        .collect(Collectors.toList());

    // 8. Handle empty results
    if (restaurantGroupDetails.isEmpty()) {
        MenuRestaurantGroupListResponse emptyResponse = MenuRestaurantGroupListResponse.builder()
                .restaurantGroups(new ArrayList<>())
                .count(0L)
                .total(0L)
                .metaData(PaginationMetaData.builder()
                        .page(pageNumber + 1)
                        .size(pageSize)
                        .totalPages(0)
                        .totalRecords(0L)
                        .build())
                .build();

        return ResponseDto.<MenuRestaurantGroupListResponse>builder()
                .message(messageUtil.getMessage("menu.restaurant.group.details.success", userLocale))
                .data(emptyResponse)
                .build();
    }

    // 9. Apply pagination in memory (since we can't do it in the query easily with complex joins)
    int totalRecords = restaurantGroupDetails.size();
    int totalPages = (int) Math.ceil((double) totalRecords / pageSize);

    if (pageNumber >= totalPages && totalRecords > 0) {
        throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                messageUtil.getMessage("pagination.error.page.not.found", userLocale));
    }

    int fromIndex = pageNumber * pageSize;
    int toIndex = Math.min(fromIndex + pageSize, totalRecords);
    
    List<MenuRestaurantGroupDetailsResponseDto> pagedResults = new ArrayList<>();
    if (fromIndex < totalRecords) {
        pagedResults = restaurantGroupDetails.subList(fromIndex, toIndex);
    }

    // 10. Build response
    PaginationMetaData metaData = PaginationMetaData.builder()
            .page(pageNumber + 1)
            .size(pageSize)
            .totalPages(totalPages)
            .totalRecords((long) totalRecords)
            .build();

    MenuRestaurantGroupListResponse listResponse = MenuRestaurantGroupListResponse.builder()
            .restaurantGroups(pagedResults)
            .count((long) pagedResults.size())
            .total((long) totalRecords)
            .metaData(metaData)
            .build();

    return ResponseDto.<MenuRestaurantGroupListResponse>builder()
            .message(messageUtil.getMessage("menu.restaurant.group.details.success", userLocale))
            .data(listResponse)
            .build();
}

    /**
     * Removes a restaurant group from a menu assignment.
     * Removes restaurant menu mappings for all restaurants in the group and deletes
     * restaurant discount/promotion mappings. Clears cache entries.
     *
     * @param menuId          the UUID of the menu
     * @param restaurantGroupId the UUID of the restaurant group to remove
     * @param locale          locale code for localized error messages
     * @return ResponseDto with success message
     * @throws ResponseStatusException if menu not found or restaurant group not assigned to menu
     */
@Override
@Transactional
@CacheEvict(value = {"restaurants", "restaurantGroupsLite"}, allEntries = true)
public ResponseDto<Void> removeRestaurantGroupFromMenu(UUID menuId, UUID restaurantGroupId, String locale) {
    Locale userLocale = Locale.forLanguageTag(locale);

    // 1. Validate menu
    Menu menu = menuRepository.findById(menuId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                            messageUtil.getMessage(MSG_MENU_NOT_FOUND, userLocale)));

    // 2. Validate restaurant group
    RestaurantGroup restaurantGroup = restaurantGroupRepository.findById(restaurantGroupId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                    messageUtil.getMessage("restaurant.group.not.found", userLocale)));

    // 3. Validate mapping exists
    RestaurantGroupMenuMapping mapping = groupMenuMappingRepository
            .findByMenuIdAndRestaurantGroupId(menuId, restaurantGroupId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                    messageUtil.getMessage("menu.restaurant.group.mapping.not.found", userLocale)));

    // 4. Delete the mapping of restaurant group with menu
    groupMenuMappingRepository.delete(mapping);

    // 5. Find restaurants belonging to this group AND mapped to this menu
    List<RestaurantMenuMapping> restaurantMappings =
            restaurantMenuMappingRepository.findByMenuIdAndRestaurantRestaurantGroupId(menuId, restaurantGroupId);

        if (!restaurantMappings.isEmpty()) {
            // Preload category item mappings for this menu once
            List<CategoryItemMapping> menuCategoryItemMappings =
                    categoryItemMappingRepository.findByMenuCategoryMappingMenuId(menuId);

            // Remove availability records for all restaurants in this group for this menu
            for (RestaurantMenuMapping restaurantMapping : restaurantMappings) {
                removeAvailabilityForRestaurantMenuMappingOptimized(
                        restaurantMapping.getRestaurant().getId(), menuCategoryItemMappings);
            }

            // Delete restaurant-menu mappings
            restaurantMenuMappingRepository.deleteAll(restaurantMappings);
        }

    return ResponseDto.<Void>builder()
            .message(messageUtil.getMessage("menu.restaurant.group.mapping.deleted.success", userLocale))
            .build();
}


    /**
     * Retrieves all versions of a menu by menu master ID.
     * Returns menus sorted by version in descending order (latest first).
     * Supports filtering by menu status.
     *
     * @param menuMasterId the UUID of the menu master (shared ID for all versions)
     * @param status       optional filter by menu status
     * @param locale       locale code for localized responses
     * @return ResponseDto containing list of menu versions
     * @throws ResponseStatusException if locale is invalid
     */
    @Override
    @Transactional(readOnly = true)
    public ResponseDto<MenuVersionsResponse> getMenuVersions(UUID menuMasterId, MenuStatus status, String locale) {
        Locale userLocale = Locale.forLanguageTag(locale);
        
        // Validate locale
        if (!localizationProperties.getLanguages().contains(locale)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        messageUtil.getMessage(MSG_MENU_ERROR_INVALID_LANGUAGE, userLocale));
        }
        
        // Get all versions of the menu based on status filter
        List<Menu> menuVersions;
        if (status != null) {
            menuVersions = menuRepository.findByMenuMasterIdAndStatusAndIsDeletedFalseOrderByVersionDesc(
                menuMasterId, status);
        } else {
            menuVersions = menuRepository.findByMenuMasterIdAndIsDeletedFalseOrderByVersionDesc(menuMasterId);
        }

        // Convert to response DTOs
        List<MenuVersionsResponse.MenuVersionData> versionDataList = menuVersions.stream()
            .map(menuVersion -> {
                // Get translation with fallback
                List<MenuTranslation> menuTranslations = menuVersion.getTranslations();
                List<MenuListTranslationDto> translationDTOs = new ArrayList<>();
                
                if (!menuTranslations.isEmpty()) {
                    // Try exact match first
                    MenuTranslation exactMatch = menuTranslations.stream()
                            .filter(t -> t.getLanguageCode() != null && t.getLanguageCode().equals(locale))
                            .findFirst()
                            .orElse(null);
                    
                    if (exactMatch != null) {
                        // Use exact match
                        translationDTOs.add(MenuListTranslationDto.builder()
                                .languageCode(exactMatch.getLanguageCode())
                                .name(exactMatch.getName())
                                .build());
                    } else {
                        // Fallback using TranslationUtils
                        java.util.Optional<MenuTranslation> fallback =
                                TranslationUtils.pickPreferredOrFromList(
                                        menuTranslations,
                                        locale,
                                        localizationProperties.getLanguages(),
                                        MenuTranslation::getLanguageCode
                                );
                        fallback.ifPresent(trans -> translationDTOs.add(MenuListTranslationDto.builder()
                                .languageCode(trans.getLanguageCode())
                                .name(trans.getName())
                                .build()));
                    }
                }

                String createdByName = null;
                String updatedByName = null;

                if (menuVersion.getCreatedBy() != null) {
                    User createdByUser = menuVersion.getCreatedBy();
                    createdByName = createdByUser.getFirstName() + " " + createdByUser.getLastName();
                }

                if (menuVersion.getUpdatedBy() != null) {
                    User updatedByUser = menuVersion.getUpdatedBy();
                    updatedByName = updatedByUser.getFirstName() + " " + updatedByUser.getLastName();
                }

                return MenuVersionsResponse.MenuVersionData.builder()
                    .id(menuVersion.getId())
                    .menuMasterId(menuVersion.getMenuMasterId())
                    .menuStructureId(menuVersion.getMenuStructure() != null ? menuVersion.getMenuStructure().getId() : null)
                    .status(menuVersion.getStatus())
                    .version(menuVersion.getVersion())
                    .translations(translationDTOs)
                    .isDeleted(menuVersion.getIsDeleted())
                    .createdBy(createdByName)
                    .createdAt(menuVersion.getCreatedAt() != null ? menuVersion.getCreatedAt().toLocalDateTime() : null)
                    .updatedBy(updatedByName)
                    .updatedAt(menuVersion.getUpdatedAt() != null ? menuVersion.getUpdatedAt().toLocalDateTime() : null)
                    .restaurantGroupCount(0L)
                    .restaurantCount(0L)
                    .build();
            })
            .collect(Collectors.toList());

        // Build the response
        MenuVersionsResponse response = MenuVersionsResponse.builder()
            .menuMasterId(menuMasterId)
            .versions(versionDataList)
            .totalVersions((long) versionDataList.size())
            .build();

        return ResponseDto.<MenuVersionsResponse>builder()
            .data(response)
            .message(messageUtil.getMessage("menu.versions.fetched.success", userLocale))
            .build();
    }

    /**
     * Duplicates a menu either as a new version of the same menu master or as a completely new menu.
     * Copies menu translations, category mappings, item mappings, and all related data.
     * Routes to duplicateMenuVersion or duplicateMenuAsNew based on request.
     *
     * @param menuId  the UUID of the source menu to duplicate
     * @param userId  the ID of the user performing the duplication
     * @param request the duplication request specifying whether to create new version or new menu
     * @param locale  locale code for localized error messages
     * @return ResponseDto containing the duplicated menu response
     * @throws ResponseStatusException if menu not found, user not found, or validation fails
     */
    @Override
    @Transactional
    public ResponseDto<MenuDto<MenuResponse>> duplicateMenu(UUID menuId, String userId, DuplicateMenuRequest request, String locale) {
        Locale userLocale = Locale.forLanguageTag(locale);
        
        // Fetch user
        User user = userRepository.findById(UUID.fromString(userId))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        messageUtil.getMessage(MSG_USER_NOT_FOUND, userLocale, userId)));
        
        // Find the source menu
        Menu sourceMenu = menuRepository.findById(menuId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        messageUtil.getMessage(MSG_MENU_NOT_FOUND, userLocale)));
        
        // Check if source menu is deleted
        if (Boolean.TRUE.equals(sourceMenu.getIsDeleted())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        messageUtil.getMessage(MSG_MENUS_ERROR_ALREADY_DELETED, userLocale));
        }
        
        // Validate duplicate flag
        if (request.getIsDuplicate() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    messageUtil.getMessage("menu.error.duplicate.flag.required", userLocale));
        }
        
        // Handle duplicate mode (flag = true) - create new menu with version 1
        if (request.getIsDuplicate()) {
            return duplicateMenuAsNew(sourceMenu, user, userLocale);
        } else {
            // Handle version mode (flag = false) - create new version under same master ID
            UUID menuMasterId = sourceMenu.getMenuMasterId();
            if (menuMasterId == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        messageUtil.getMessage("menu.error.menu.master.not.found", userLocale));
            }
            
            return duplicateMenuVersion(sourceMenu, menuMasterId, user, userLocale);
        }
    }
    
    /**
     * Duplicates a menu as a new version of the same menu master.
     * Creates a new menu with incremented version number and same menuMasterId.
     * Copies all menu data including translations, category mappings, and item mappings.
     *
     * @param sourceMenu   the source menu to duplicate
     * @param menuMasterId the menu master ID to assign to the new version
     * @param user         the user performing the duplication
     * @param userLocale   locale for localized error messages
     * @return ResponseDto containing the duplicated menu response
     * @throws ResponseStatusException if menu master not found or draft version already exists
     */
    private ResponseDto<MenuDto<MenuResponse>> duplicateMenuVersion(Menu sourceMenu, UUID menuMasterId, User user, Locale userLocale) {
        // Find the latest version of the menu with the given menuMasterId
        List<Menu> existingVersions = menuRepository.findByMenuMasterIdAndIsDeletedFalseOrderByVersionDesc(menuMasterId);
        
        if (existingVersions.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                    messageUtil.getMessage("menu.error.menu.master.not.found", userLocale));
        }
        
        // Check if there's already a draft menu with the same master ID
        List<Menu> draftMenusWithSameMasterId = menuRepository.findByMenuMasterIdAndStatusAndIsDeletedFalseOrderByVersionDesc(
            menuMasterId, MenuStatus.DRAFT);
        
        if (!draftMenusWithSameMasterId.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    messageUtil.getMessage("menu.error.draft.already.exists", userLocale));
        }
        
        // Get the latest version to determine the new version number
        Menu latestVersion = existingVersions.get(0);
        Double newVersion = latestVersion.getVersion() != null ? latestVersion.getVersion() + 1.0 : 1.0;
        
        // Create new menu with incremented version
        Menu newMenu = new Menu();
        newMenu.setMenuStructure(sourceMenu.getMenuStructure());
        newMenu.setMenuMasterId(menuMasterId);
        newMenu.setVersion(newVersion);
        newMenu.setStatus(MenuStatus.DRAFT);
        newMenu.setIsDeleted(false);
        newMenu.setCreatedAt(OffsetDateTime.now(ZoneOffset.UTC));
        newMenu.setCreatedBy(user);
        
        newMenu = menuRepository.save(newMenu);
        
        // Copy translations
        copyMenuTranslations(sourceMenu, newMenu);
        
        // Copy menu-category mappings
        Map<UUID, MenuCategoryMapping> oldToNewMappingMap = copyMenuCategoryMappings(sourceMenu, newMenu);
        
        // Copy category-item mappings using the mapping of old to new MenuCategoryMapping
        copyCategoryItemMappings(sourceMenu, oldToNewMappingMap);
        
        // Build response
        return buildMenuResponse(newMenu, user, userLocale, "menu.version.duplicated.success");
    }
    
    /**
     * Duplicates a menu as a completely new menu with its own menu master ID.
     * Creates a new menu with version 1.0 and assigns its own ID as menuMasterId.
     * Copies all menu data including translations, category mappings, and item mappings.
     *
     * @param sourceMenu the source menu to duplicate
     * @param user       the user performing the duplication
     * @param userLocale locale for localized error messages
     * @return ResponseDto containing the duplicated menu response
     */
    private ResponseDto<MenuDto<MenuResponse>> duplicateMenuAsNew(Menu sourceMenu, User user, Locale userLocale) {
        // Create new menu with its own master ID
        Menu newMenu = new Menu();
        newMenu.setMenuStructure(sourceMenu.getMenuStructure());
        newMenu.setMenuMasterId(null); // Will be set to its own ID after creation
        newMenu.setVersion(1.0); // New menu starts with version 1
        newMenu.setStatus(MenuStatus.DRAFT);
        newMenu.setIsDeleted(false);
        newMenu.setCreatedAt(OffsetDateTime.now(ZoneOffset.UTC));
        newMenu.setCreatedBy(user);
        
        newMenu = menuRepository.save(newMenu);
        
        // Set menuMasterId to its own ID
        newMenu.setMenuMasterId(newMenu.getId());
        newMenu = menuRepository.save(newMenu);
        
        // Copy translations with "copy" suffix
        copyMenuTranslationsWithSuffix(sourceMenu, newMenu, "copy");
        
        // Copy menu-category mappings
        Map<UUID, MenuCategoryMapping> oldToNewMappingMap = copyMenuCategoryMappings(sourceMenu, newMenu);
        
        // Copy category-item mappings using the mapping of old to new MenuCategoryMapping
        copyCategoryItemMappings(sourceMenu, oldToNewMappingMap);
        
        // Build response
        return buildMenuResponse(newMenu, user, userLocale, "menu.duplicated.success");
    }
    
    private void copyMenuTranslations(Menu sourceMenu, Menu newMenu) {
        for (MenuTranslation sourceTranslation : sourceMenu.getTranslations()) {
            MenuTranslation newTranslation = new MenuTranslation();
            newTranslation.setMenu(newMenu);
            newTranslation.setLanguageCode(sourceTranslation.getLanguageCode());
            newTranslation.setName(sourceTranslation.getName());
            newTranslation.setDescription(sourceTranslation.getDescription());
            menuTranslationRepository.save(newTranslation);
        }
    }
    
    private void copyMenuTranslationsWithSuffix(Menu sourceMenu, Menu newMenu, String suffix) {
        for (MenuTranslation sourceTranslation : sourceMenu.getTranslations()) {
            MenuTranslation newTranslation = new MenuTranslation();
            newTranslation.setMenu(newMenu);
            newTranslation.setLanguageCode(sourceTranslation.getLanguageCode());
            newTranslation.setName(sourceTranslation.getName() + " " + suffix);
            newTranslation.setDescription(sourceTranslation.getDescription());
            menuTranslationRepository.save(newTranslation);
        }
    }
    
    /**
     * Copies menu category mappings from source menu to new menu.
     * Preserves category relationships, parent category references, and status.
     * Returns a map of old MenuCategoryMapping IDs to new MenuCategoryMapping entities.
     *
     * @param sourceMenu the source menu to copy mappings from
     * @param newMenu    the new menu to copy mappings to
     * @return map of old MenuCategoryMapping ID to new MenuCategoryMapping
     */
    private Map<UUID, MenuCategoryMapping> copyMenuCategoryMappings(Menu sourceMenu, Menu newMenu) {
        List<MenuCategoryMapping> sourceMappings = menuCategoryMappingRepository.findByMenuId(sourceMenu.getId());
        Map<UUID, MenuCategoryMapping> oldToNewMappingMap = new HashMap<>();
        
        for (MenuCategoryMapping sourceMapping : sourceMappings) {
            MenuCategoryMapping newMapping = new MenuCategoryMapping();
            newMapping.setMenu(newMenu);
            newMapping.setCategory(sourceMapping.getCategory());
            newMapping.setParentCategory(sourceMapping.getParentCategory());
            newMapping.setStatus(sourceMapping.getStatus());
            newMapping = menuCategoryMappingRepository.save(newMapping);
            
            // Store the mapping from old to new
            oldToNewMappingMap.put(sourceMapping.getId(), newMapping);
        }
        
        return oldToNewMappingMap;
    }
    
    /**
     * Builds a MenuDto response from a Menu entity.
     * Includes menu translations, formatted response, and success message.
     *
     * @param menu              the menu entity to convert
     * @param user              the user (for audit purposes)
     * @param userLocale        locale for localized messages
     * @param successMessageKey message key for success message
     * @return ResponseDto containing menu response with translations
     */
    private ResponseDto<MenuDto<MenuResponse>> buildMenuResponse(Menu menu, User user, Locale userLocale, String successMessageKey) {
        // Fetch saved translations for response
        List<MenuTranslation> savedTranslations = menuTranslationRepository.findByMenuId(menu.getId());
        
        List<MenuTranslationDto> translationDTOs = savedTranslations.stream()
                .map(t -> MenuTranslationDto.builder()
                        .languageCode(t.getLanguageCode())
                        .name(t.getName())
                        .description(t.getDescription())
                        .build())
                .collect(Collectors.toList());
        
        String createdByName = user.getFirstName() + " " + user.getLastName();
        
        MenuResponse menuResponse = MenuResponse.builder()
                .id(menu.getId())
                .menuMasterId(menu.getMenuMasterId())
                .version(menu.getVersion())
                .status(menu.getStatus().name())
                .translations(translationDTOs)
                .createdAt(menu.getCreatedAt() != null ? menu.getCreatedAt().toLocalDateTime() : null)
                .createdBy(createdByName)
                .menuStructureId(menu.getMenuStructure() != null ? menu.getMenuStructure().getId() : null)
                .build();
        
        MenuDto<MenuResponse> menuDto = MenuDto.<MenuResponse>builder()
                .menu(menuResponse)
                .build();
        
        return ResponseDto.<MenuDto<MenuResponse>>builder()
                .message(messageUtil.getMessage(successMessageKey, userLocale))
                .data(menuDto)
                .build();
    }

    private boolean hasActiveModifierGroupAssignments(UUID itemId) {
        List<ItemModifierGroup> assignments = itemModifierGroupRepository.findByItemIdAndIsDeletedFalse(itemId);
        return assignments.stream().anyMatch(assignment -> {
            ModifierGroup mg = assignment.getModifierGroup();
            return mg != null && !Boolean.TRUE.equals(mg.getIsDeleted()) && EntityStatus.ACTIVE.equals(mg.getStatus());
        });
    }

    /**
     * Retrieves a paginated list of items for a specific menu and category.
     * Supports wildcard category ID ("*") to get items from all categories.
     * Includes item availability information for the specified restaurant.
     *
     * @param menuId            the UUID of the menu
     * @param categoryIdOrWildcard category UUID or "*" for all categories
     * @param restaurantId      the UUID of the restaurant for availability checks
     * @param locale            locale code for localized responses
     * @param search            optional search term for item name
     * @param page              page number for pagination
     * @param size              page size for pagination
     * @return ResponseDto containing paginated list of items with availability
     * @throws ResponseStatusException if menu not found
     */
    @Override
    @Transactional(readOnly = true)
    public ResponseDto<MenuItemListResponse> getItemsByMenuAndCategory(UUID menuId, String categoryIdOrWildcard, UUID restaurantId, String locale, String search, String orderType, String alcoholType, Integer page, Integer size) {
        Locale userLocale = Locale.forLanguageTag(locale);

        // Validate menu exists
        Menu menu = menuRepository.findById(menuId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        messageUtil.getMessage(MSG_MENU_NOT_FOUND, userLocale, menuId)));

      

        // Collect ACTIVE menu-category mappings for this menu
        List<MenuCategoryMapping> menuCategoryMappings = menuCategoryMappingRepository.findByMenuId(menuId);
        Set<UUID> assignedCategoryIds = menuCategoryMappings.stream()
                .filter(mcm -> EntityStatus.ACTIVE.equals(mcm.getStatus()))
                .map(mcm -> mcm.getCategory().getId())
                .collect(Collectors.toSet());

        if (assignedCategoryIds.isEmpty()) {
            MenuItemListResponse emptyResponse = MenuItemListResponse.builder()
                    .items(Collections.emptyList())
                    .count(0L)
                    .total(0L)
                    .metaData(PaginationMetaData.builder()
                            .page(1)
                            .size(0)
                            .totalPages(0)
                            .totalRecords(0L)
                            .build())
                    .build();
            
            return ResponseDto.<MenuItemListResponse>builder()
                    .message(messageUtil.getMessage("menu.items.none", userLocale))
                    .data(emptyResponse)
                    .build();
        }

        Set<UUID> allCategoryIds = new HashSet<>();
        boolean isWildcard = "*".equals(categoryIdOrWildcard);
        Category requestedCategory = null;
        MenuCategoryMapping requestedCategoryMapping = null;
        boolean shouldFetchCombos = false;
        
        if (isWildcard) {
            // For all assigned categories, if it has subcategories assigned (ACTIVE), only send items of those subcategories
            for (UUID catId : assignedCategoryIds) {
                // Find subcategories assigned to this menu (ACTIVE)
                List<MenuCategoryMapping> subMappings = menuCategoryMappingRepository.findByMenuId(menuId).stream()
                        .filter(mcm -> mcm.getCategory().getParentCategory() != null &&
                                catId.equals(mcm.getCategory().getParentCategory().getId()) &&
                                EntityStatus.ACTIVE.equals(mcm.getStatus()))
                        .collect(Collectors.toList());
                if (!subMappings.isEmpty()) {
                    for (MenuCategoryMapping subMcm : subMappings) {
                        allCategoryIds.add(subMcm.getCategory().getId());
                    }
                } else {
                    allCategoryIds.add(catId);
                }
            }
            // For wildcard, fetch all combos (existing behavior)
            shouldFetchCombos = true;
        } else {
            UUID categoryId;
            try {
                categoryId = UUID.fromString(categoryIdOrWildcard);
            } catch (IllegalArgumentException ex) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        messageUtil.getMessage("category.invalid.id", userLocale));
            }
            // Check if categoryId is assigned to menu (ACTIVE)
            if (!assignedCategoryIds.contains(categoryId)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        messageUtil.getMessage("category.not.assigned.to.menu", userLocale));
            }
            
            // Get the category to check isCombo flag
            requestedCategory = categoryRepository.findById(categoryId)
                    .orElse(null);
            
            if (requestedCategory == null) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                        messageUtil.getMessage(MSG_CATEGORY_NOT_FOUND, userLocale));
            }
            
            // Get the menu category mapping for this category and menu
            requestedCategoryMapping = menuCategoryMappingRepository.findByMenuIdAndCategoryId(menuId, categoryId)
                    .orElse(null);
            
            if (requestedCategoryMapping == null) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                        messageUtil.getMessage("menu.category.mapping.error", userLocale));
            }
            
            // Check if this category is a parent of any other assigned category (i.e., has subcategories assigned)
            List<MenuCategoryMapping> subMappings = menuCategoryMappingRepository.findByMenuId(menuId).stream()
                    .filter(mcm -> mcm.getCategory().getParentCategory() != null &&
                            categoryId.equals(mcm.getCategory().getParentCategory().getId()) &&
                            EntityStatus.ACTIVE.equals(mcm.getStatus()))
                    .collect(Collectors.toList());
            if (!subMappings.isEmpty()) {
                for (MenuCategoryMapping subMcm : subMappings) {
                    allCategoryIds.add(subMcm.getCategory().getId());
                }
            } else {
                allCategoryIds.add(categoryId);
            }
            
            // Check isCombo flag - only fetch combos if category isCombo is true
            shouldFetchCombos = Boolean.TRUE.equals(requestedCategory.getIsCombo());
        }

        if (allCategoryIds.isEmpty()) {
            MenuItemListResponse emptyResponse = MenuItemListResponse.builder()
                    .items(Collections.emptyList())
                    .count(0L)
                    .total(0L)
                    .metaData(PaginationMetaData.builder()
                            .page(1)
                            .size(0)
                            .totalPages(0)
                            .totalRecords(0L)
                            .build())
                    .build();
            
            return ResponseDto.<MenuItemListResponse>builder()
                    .message(messageUtil.getMessage("menu.items.none", userLocale))
                    .data(emptyResponse)
                    .build();
        }

        // Fetch category-item mappings for all collected categories
        // FIXED: Use menuId filter to ensure items are only from the specific menu
        List<MenuCategoryMapping> mcms = menuCategoryMappingRepository.findByMenuIdAndCategory_IdIn(menuId, new ArrayList<>(allCategoryIds));
        List<CategoryItemMapping> mappings = categoryItemMappingRepository.findByMenuCategoryMappingIn(mcms);

        // Fetch availability data for all category item mappings
        List<UUID> categoryItemMappingIds = mappings.stream()
                .map(CategoryItemMapping::getId)
                .collect(Collectors.toList());
        
        List<RestaurantItemAvailability> availabilityRecords = restaurantItemAvailabilityRepository
                .findByRestaurantIdAndCategoryItemMappingIdIn(restaurantId, categoryItemMappingIds);
        
        // Create a map of category item mapping ID to availability status
        Map<UUID, Boolean> availabilityMap = availabilityRecords.stream()
                .collect(Collectors.toMap(
                    av -> av.getCategoryItemMapping().getId(),
                    RestaurantItemAvailability::getIsAvailable
                ));

        // Parse orderType if provided
        ItemOrderType orderTypeFilter = null;
        if (orderType != null && !orderType.trim().isEmpty()) {
            try {
                orderTypeFilter = ItemOrderType.valueOf(orderType.toUpperCase());
            } catch (IllegalArgumentException e) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        messageUtil.getMessage("item.error.invalid.orderType", userLocale));
            }
        }
        // Create final copy for use in lambda expressions
        final ItemOrderType finalOrderTypeFilter = orderTypeFilter;

        // Parse alcoholType if provided
        com.gulfnet.shared_library.enums.AlcoholType alcoholTypeFilter = null;
        if (alcoholType != null && !alcoholType.trim().isEmpty()) {
            try {
                alcoholTypeFilter = com.gulfnet.shared_library.enums.AlcoholType.valueOf(alcoholType.toUpperCase());
            } catch (IllegalArgumentException e) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        messageUtil.getMessage("item.error.invalid.alcoholType", userLocale));
            }
        }
        // Create final copy for use in lambda expressions
        final com.gulfnet.shared_library.enums.AlcoholType finalAlcoholTypeFilter = alcoholTypeFilter;

        Map<UUID, Item> uniqueItemsById = new LinkedHashMap<>();
        Map<UUID, CategoryItemMapping> itemToMappingMap = new HashMap<>();
        for (CategoryItemMapping mapping : mappings) {
            Item item = mapping.getItem();
            boolean activeItem = item != null
                    && (item.getIsDeleted() == null || !item.getIsDeleted())
                    && EntityStatus.ACTIVE.equals(item.getStatus());
            ItemOrderType itemOrderType = mapping.getItemOrderType() != null
                    ? mapping.getItemOrderType()
                    : ItemOrderType.BOTH;
            boolean passesOrderTypeFilter = true;
            if (orderTypeFilter != null) {
                if (orderTypeFilter == ItemOrderType.DINE_IN) {
                    passesOrderTypeFilter = itemOrderType == ItemOrderType.DINE_IN || itemOrderType == ItemOrderType.BOTH;
                } else if (orderTypeFilter == ItemOrderType.TAKEAWAY) {
                    passesOrderTypeFilter = itemOrderType == ItemOrderType.TAKEAWAY || itemOrderType == ItemOrderType.BOTH;
                }
            }
            com.gulfnet.shared_library.enums.AlcoholType itemAlcoholType = item != null ? item.getAlcoholType() : null;
            boolean passesAlcoholFilter = alcoholTypeFilter == null
                    || (itemAlcoholType != null && itemAlcoholType.equals(alcoholTypeFilter));

            if (!activeItem || !passesOrderTypeFilter || !passesAlcoholFilter) {
                continue;
            }
            uniqueItemsById.putIfAbsent(item.getId(), item);
            itemToMappingMap.put(item.getId(), mapping);
        }

        // Around line 2025-2030, after fetching the mappings, create a map for discount calculation
        Map<UUID, List<MenuCategoryMapping>> itemToMcmMap = new HashMap<>();
        for (CategoryItemMapping mapping : mappings) {
            UUID itemId = mapping.getItem().getId();
            itemToMcmMap.computeIfAbsent(itemId, k -> new ArrayList<>()).add(mapping.getMenuCategoryMapping());
        }

        PriceOverrideHelper.ActiveOverrideIndex activeOverrideIndex = (restaurantId != null) ? priceOverrideHelper.buildActiveOverrideIndex(restaurantId) : null;

        List<MenuItemResponse> responses = uniqueItemsById.values().stream()
                .map(item -> {
                    List<ItemTranslation> savedTranslations = itemTranslationRepository.findAllByItemId(item.getId());
                    String requestedLang = userLocale.getLanguage();
                    ItemTranslation selected = null;
                    if (savedTranslations != null && !savedTranslations.isEmpty()) {
                        // Try exact match first
                        selected = savedTranslations.stream()
                                .filter(t -> t.getLanguageCode() != null && requestedLang.equalsIgnoreCase(t.getLanguageCode()))
                                .findFirst()
                                .orElse(null);
                        
                        if (selected == null) {
                            // Fallback using TranslationUtils
                            java.util.Optional<ItemTranslation> fallback =
                                    TranslationUtils.pickPreferredOrFromList(
                                            savedTranslations,
                                            requestedLang,
                                            localizationProperties.getLanguages(),
                                            ItemTranslation::getLanguageCode
                                    );
                            selected = fallback.orElse(null);
                        }
                    }
                    ItemTranslationDto translation = null;
                    if (selected != null) {
                        translation = ItemTranslationDto.builder()
                                .languageCode(selected.getLanguageCode())
                                .name(selected.getName())
                                .description(selected.getDescription())
                                .build();
                    }
                    

                    // Get availability status for this item
                    CategoryItemMapping mapping = itemToMappingMap.get(item.getId());
                    Boolean isAvailable = mapping != null ? availabilityMap.get(mapping.getId()) : null;
                    
                    // Get isCombo from category
                    Boolean isCombo = false;
                    if (mapping != null && mapping.getMenuCategoryMapping() != null 
                            && mapping.getMenuCategoryMapping().getCategory() != null) {
                        isCombo = Boolean.TRUE.equals(mapping.getMenuCategoryMapping().getCategory().getIsCombo());
                    }
                    
                    // Calculate discount information with price overrides applied
                    List<MenuCategoryMapping> itemMcms = itemToMcmMap.getOrDefault(item.getId(), Collections.emptyList());

                    Double effectiveBasePrice = item.getBasePrice();
                    if (restaurantId != null) {
                        effectiveBasePrice = priceOverrideHelper.resolveEffectiveBasePrice(effectiveBasePrice, menuId, itemMcms, activeOverrideIndex);
                    }

                    DiscountInfo discountInfo = calculateDiscountInfo(
                            effectiveBasePrice, item, itemMcms, menuId, restaurantId, userLocale);
                    
                    String currency = restaurantChainConfigProperties.getChain() != null ? restaurantChainConfigProperties.getChain().getCurrency() : null;
                    MenuItemResponse.MenuItemResponseBuilder builder = MenuItemResponse.builder()
                            .id(item.getId())
                            .itemCode(item.getItemCode())
                            .basePrice(discountInfo.getBasePrice() != null ? CurrencyFormatter.formatAmount(BigDecimal.valueOf(discountInfo.getBasePrice()), currency).doubleValue() : null)
                            .isBxgyBuyItem(discountInfo.isBxgyBuyItem())
                            .discountDetail(discountInfo.getDiscountDetail())
                            .imageUrl(item.getImageUrl() != null && !item.getImageUrl().isEmpty()
                                    ? awsService.getPreSignedUrl(item.getImageUrl()) : null)
                            .hasModifierAssigned(hasActiveModifierGroupAssignments(item.getId()))
                            .outOfStock(item.getOutOfStock())
                            .status(item.getStatus())
                            .dietaryPreference(item.getDietaryPreference())
                            .alcoholType(item.getAlcoholType())
                            .isDeleted(item.getIsDeleted())
                            .createdAt(item.getCreatedAt() != null ? item.getCreatedAt().toLocalDateTime() : null)
                            .updatedAt(item.getUpdatedAt() != null ? item.getUpdatedAt().toLocalDateTime() : null)
                            .createdBy(item.getCreatedBy() != null ? item.getCreatedBy().getFirstName() : null)
                            .updatedBy(item.getUpdatedBy() != null ? item.getUpdatedBy().getFirstName() : null)
                            .isAvailable(isAvailable)
                            .isCombo(isCombo)
                            .translation(translation);

                    // Only set discountedPrice if it's not null (i.e., not a BXGY item)
                    if (discountInfo.getDiscountedPrice() != null) {
                        builder.discountedPrice(CurrencyFormatter.formatAmount(BigDecimal.valueOf(discountInfo.getDiscountedPrice()), currency).doubleValue());
                    }

                    // Always set BXGY fields (even if null) so they appear in the response
                    builder.buyQuantity(discountInfo.getBuyQuantity())
                           .getQuantity(discountInfo.getGetQuantity())
                           .discountId(discountInfo.getDiscountId());

                    return builder.build();
                })
                .filter(itemResponse -> {
                    if (search == null || search.trim().isEmpty()) return true;
                    String searchLower = search.trim().toLowerCase();
                    String name = itemResponse.getTranslation() != null && itemResponse.getTranslation().getName() != null ? itemResponse.getTranslation().getName().toLowerCase() : "";
                    return name.contains(searchLower);
                })
                .collect(Collectors.toList());

        boolean hasFallbackTranslations = responses.stream()
                .anyMatch(response -> response.getTranslation() != null &&
                        !userLocale.getLanguage().equalsIgnoreCase(response.getTranslation().getLanguageCode()));

        String messageKey = hasFallbackTranslations ? "menu.items.success.with.fallback" : "menu.items.success";

        // Validate & normalize pagination (will apply over items + combos combined)
        int pageNumber = (page != null ? page : 1) - 1;
        if (pageNumber < 0) pageNumber = 0;
        int pageSize = (size != null && size > 0) ? size : Integer.MAX_VALUE;

        // Fetch combos based on category isCombo flag
        List<Combo> combos = new ArrayList<>();
        if (shouldFetchCombos) {
            if (isWildcard) {
                // For wildcard, fetch all combos associated with this menu (existing behavior)
                combos = comboRepository.findCombosByMenuWithFilters(
                        menuId,
                        EntityStatus.ACTIVE.name(),
                        null,
                        search,
                        userLocale.getLanguage()
                );
            } else if (requestedCategoryMapping != null) {
                // For specific category with isCombo=true, fetch combos from menu_category_combo_mapping
                List<MenuCategoryComboMapping> comboMappings = menuCategoryComboMappingRepository
                        .findByMenuCategoryMapping_Id(requestedCategoryMapping.getId());
                
                // Extract combo IDs and fetch combos
                List<UUID> comboIds = comboMappings.stream()
                        .map(mapping -> mapping.getCombo().getComboId())
                        .filter(Objects::nonNull)
                        .distinct()
                        .collect(Collectors.toList());
                
                if (!comboIds.isEmpty()) {
                    combos = comboRepository.findAllById(comboIds).stream()
                            .filter(c -> c != null 
                                    && EntityStatus.ACTIVE.equals(c.getStatus()) 
                                    && (c.getIsDeleted() == null || !c.getIsDeleted()))
                            .collect(Collectors.toList());
                }
            }
        }
        
        // Filter combos by validity, day of week, and time
        OffsetDateTime nowUtc = OffsetDateTime.now(ZoneOffset.UTC).truncatedTo(java.time.temporal.ChronoUnit.SECONDS);
        java.time.OffsetTime currentUtcTime = nowUtc.toOffsetTime();
        com.gulfnet.shared_library.enums.DayOfWeek currentDay = com.gulfnet.shared_library.enums.DayOfWeek.valueOf(nowUtc.getDayOfWeek().name());
        String currency = restaurantChainConfigProperties.getChain() != null ? restaurantChainConfigProperties.getChain().getCurrency() : null;
        
        List<MenuItemResponse> comboAsItems = combos.stream()
                .filter(c -> (c.getValidFrom() == null || !nowUtc.isBefore(c.getValidFrom()))
                        && (c.getValidTo() == null || !nowUtc.isAfter(c.getValidTo())))
                .filter(c -> (c.getDaysOfWeek() == null || c.getDaysOfWeek().isEmpty() || c.getDaysOfWeek().contains(currentDay)))
                .filter(c -> {
                    if (c.getStartTime() == null || c.getEndTime() == null) return true;
                    java.time.OffsetTime start = c.getStartTime();
                    java.time.OffsetTime end = c.getEndTime();
                    if (start.equals(end)) return true;
                    if (start.isBefore(end)) {
                        return !currentUtcTime.isBefore(start) && !currentUtcTime.isAfter(end);
                    } else {
                        return !currentUtcTime.isAfter(end) || !currentUtcTime.isBefore(start);
                    }
                })
                .filter(c -> {
                    // Filter by orderType if provided (same logic as items)
                    if (finalOrderTypeFilter != null) {
                        ItemOrderType comboOrderType = c.getItemOrderType();
                        if (comboOrderType == null) {
                            return false; // Skip combos without orderType if filter is specified
                        }
                        // Include combo if:
                        // - orderType == DINE_IN: comboOrderType == DINE_IN OR BOTH
                        // - orderType == TAKEAWAY: comboOrderType == TAKEAWAY OR BOTH
                        if (finalOrderTypeFilter == ItemOrderType.DINE_IN) {
                            return comboOrderType == ItemOrderType.DINE_IN || comboOrderType == ItemOrderType.BOTH;
                        } else if (finalOrderTypeFilter == ItemOrderType.TAKEAWAY) {
                            return comboOrderType == ItemOrderType.TAKEAWAY || comboOrderType == ItemOrderType.BOTH;
                        }
                        return false;
                    }
                    return true; // No filter, include all combos
                })
                .filter(c -> {
                    // Filter by alcoholType if provided
                    if (finalAlcoholTypeFilter != null) {
                        // Calculate combo's alcoholType based on items in the combo
                        // If combo contains ANY alcoholic item → ALCOHOLIC
                        // Else → NON_ALCOHOLIC
                        List<com.gulfnet.shared_library.entity.ComboItemMapping> comboItemMappings = 
                                comboRepository.findComboItemMappingsWithItems(c.getComboId());
                        
                        if (comboItemMappings.isEmpty()) {
                            return false; // Skip combos without items if filter is specified
                        }
                        
                        // Check if any item is alcoholic
                        boolean hasAlcoholicItem = comboItemMappings.stream()
                                .anyMatch(itemMapping -> {
                                    Item item = itemMapping.getCategoryItemMapping().getItem();
                                    return item != null && 
                                           item.getAlcoholType() == com.gulfnet.shared_library.enums.AlcoholType.ALCOHOLIC;
                                });
                        
                        com.gulfnet.shared_library.enums.AlcoholType comboAlcoholType = hasAlcoholicItem 
                                ? com.gulfnet.shared_library.enums.AlcoholType.ALCOHOLIC 
                                : com.gulfnet.shared_library.enums.AlcoholType.NON_ALCOHOLIC;
                        
                        return comboAlcoholType == finalAlcoholTypeFilter;
                    }
                    return true; // No filter, include all combos
                })
                .map(c -> {
                    // Get combo translations
                    List<ComboTranslation> comboTranslations = comboTranslationRepository.findByComboComboId(c.getComboId());
                    ItemTranslationDto translation = null;
                    
                    if (!comboTranslations.isEmpty()) {
                        java.util.Optional<ComboTranslation> selected =
                                TranslationUtils.pickPreferredOrFromListNonBlank(
                                        comboTranslations,
                                        userLocale.getLanguage(),
                                        localizationProperties.getLanguages(),
                                        ComboTranslation::getLanguageCode,
                                        ComboTranslation::getName);
                        if (selected.isPresent()) {
                            ComboTranslation trans = selected.get();
                            translation = ItemTranslationDto.builder()
                                    .languageCode(trans.getLanguageCode())
                                    .name(trans.getName())
                                    .description(trans.getDescription())
                                    .build();
                        }
                    }
                    
                    // Calculate combo's alcoholType based on items in the combo
                    // If combo contains ANY alcoholic item → ALCOHOLIC
                    // Else → NON_ALCOHOLIC
                    List<com.gulfnet.shared_library.entity.ComboItemMapping> comboItemMappings = 
                            comboRepository.findComboItemMappingsWithItems(c.getComboId());
                    
                    com.gulfnet.shared_library.enums.AlcoholType comboAlcoholType = 
                            com.gulfnet.shared_library.enums.AlcoholType.NON_ALCOHOLIC; // Default to NON_ALCOHOLIC
                    
                    if (!comboItemMappings.isEmpty()) {
                        // Check if any item is alcoholic
                        boolean hasAlcoholicItem = comboItemMappings.stream()
                                .anyMatch(itemMapping -> {
                                    Item item = itemMapping.getCategoryItemMapping().getItem();
                                    return item != null && 
                                           item.getAlcoholType() == com.gulfnet.shared_library.enums.AlcoholType.ALCOHOLIC;
                                });
                        
                        comboAlcoholType = hasAlcoholicItem 
                                ? com.gulfnet.shared_library.enums.AlcoholType.ALCOHOLIC 
                                : com.gulfnet.shared_library.enums.AlcoholType.NON_ALCOHOLIC;
                    }
                    
                    // Convert combo to MenuItemResponse format
                    return MenuItemResponse.builder()
                            .id(c.getComboId()) // Use comboId as id
                            .basePrice(c.getBasePrice() != null ? CurrencyFormatter.formatAmount(c.getBasePrice(), currency).doubleValue() : null)
                            .isBxgyBuyItem(false)
                            .buyQuantity(null)
                            .getQuantity(null)
                            .discountId(null)
                            .imageUrl(c.getComboImageUrl() != null && !c.getComboImageUrl().isEmpty()
                                    ? awsService.getPreSignedUrl(c.getComboImageUrl()) : null)
                            .outOfStock(false)
                            .status(c.getStatus())
                            .alcoholType(comboAlcoholType)
                            .isDeleted(c.getIsDeleted())
                            .createdBy(c.getCreatedBy() != null ? (c.getCreatedBy().getFirstName() + " " + c.getCreatedBy().getLastName()) : null)
                            .updatedBy(c.getUpdatedBy() != null ? (c.getUpdatedBy().getFirstName() + " " + c.getUpdatedBy().getLastName()) : null)
                            .hasModifierAssigned(false)
                            .createdAt(c.getCreatedAt() != null ? c.getCreatedAt().toLocalDateTime() : null)
                            .updatedAt(c.getUpdatedAt() != null ? c.getUpdatedAt().toLocalDateTime() : null)
                            .isAvailable(true) // Combos are available if they pass the filters
                            .isCombo(true) // Combos always have isCombo=true
                            .translation(translation)
                            .build();
                })
                .filter(comboItem -> {
                    if (search == null || search.trim().isEmpty()) return true;
                    String searchLower = search.trim().toLowerCase();
                    String name = comboItem.getTranslation() != null && comboItem.getTranslation().getName() != null 
                            ? comboItem.getTranslation().getName().toLowerCase() : "";
                    return name.contains(searchLower);
                })
                .collect(Collectors.toList());


        // Combine items and combos into a single list
        List<MenuItemResponse> allItems = new ArrayList<>(responses);
        allItems.addAll(comboAsItems);

        // Apply pagination over the combined list (items + combos)
        int totalItems = allItems.size();
        int fromIndex = Math.min(pageNumber * pageSize, totalItems);
        int toIndex = Math.min(fromIndex + pageSize, totalItems);
        List<MenuItemResponse> pagedItems = allItems.subList(fromIndex, toIndex);

        // Create pagination metadata
        PaginationMetaData metaData = PaginationMetaData.builder()
                .page(pageNumber + 1)
                .size(pageSize)
                .totalPages((int) Math.ceil((double) totalItems / pageSize))
                .totalRecords((long) totalItems)
                .build();

        // Create the list response
        MenuItemListResponse listResponse = MenuItemListResponse.builder()
                .items(pagedItems)
                .count((long) pagedItems.size())
                .total((long) totalItems)
                .metaData(metaData)
                .build();

        return ResponseDto.<MenuItemListResponse>builder()
                .message(messageUtil.getMessage(messageKey, userLocale))
                .data(listResponse)
                .build();
    }


    /**
     * Removes a restaurant from a menu assignment.
     * Deletes restaurant menu mapping and related discount/promotion mappings.
     * Clears cache entries.
     *
     * @param menuId       the UUID of the menu
     * @param restaurantId the UUID of the restaurant to remove
     * @param locale       locale code for localized error messages
     * @return ResponseDto with success message
     * @throws ResponseStatusException if menu not found, restaurant not found, or not assigned to menu
     */
    @Override
    @Transactional
    @CacheEvict(value = {"restaurants", "restaurantGroupsLite"}, allEntries = true)
    public ResponseDto<Void> removeRestaurantFromMenu(UUID menuId, UUID restaurantId, String locale) {
        Locale userLocale = Locale.forLanguageTag(locale);

        // 1. Validate menu exists and is not deleted
        Menu menu = menuRepository.findById(menuId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        messageUtil.getMessage(MSG_MENU_NOT_FOUND, userLocale)));

        if (Boolean.TRUE.equals(menu.getIsDeleted())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    messageUtil.getMessage("menu.deleted", userLocale));
        }

        // 2. Validate restaurant exists and is not deleted
        Restaurant restaurant = restaurantRepository.findById(restaurantId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        messageUtil.getMessage(MSG_RESTAURANT_NOT_FOUND, userLocale)));

        if (Boolean.TRUE.equals(restaurant.getIsDeleted())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    messageUtil.getMessage("restaurant.deleted", userLocale, restaurantId));
        }

        // 3. Validate restaurant is active
        if (restaurant.getStatus() != EntityStatus.ACTIVE) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    messageUtil.getMessage("restaurant.menu.removal.error.not.active", userLocale));
        }

        // 4. Check if restaurant-menu mapping exists
        RestaurantMenuId mappingId = new RestaurantMenuId(restaurantId, menuId);
        Optional<RestaurantMenuMapping> existingMapping = restaurantMenuMappingRepository.findById(mappingId);
        
        if (existingMapping.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                    messageUtil.getMessage("restaurant.menu.mapping.not.found", userLocale));
        }

        // 5. Delete the restaurant-menu mapping
        restaurantMenuMappingRepository.delete(existingMapping.get());

        // 5a. Delete restaurant-discount mappings for discounts associated with this menu
        List<MenuDiscountMapping> menuDiscountMappings = menuDiscountMappingRepository.findByMenuId(menuId);
        if (menuDiscountMappings != null && !menuDiscountMappings.isEmpty()) {
            List<RestaurantDiscountMapping> restaurantDiscountMappingsToDelete = new ArrayList<>();
            for (MenuDiscountMapping menuDiscountMapping : menuDiscountMappings) {
                Discount discount = menuDiscountMapping.getDiscount();
                RestaurantDiscountId restaurantDiscountId = new RestaurantDiscountId();
                restaurantDiscountId.setRestaurantId(restaurantId);
                restaurantDiscountId.setDiscountId(discount.getId());
                
                Optional<RestaurantDiscountMapping> restaurantDiscountMapping = 
                    restaurantDiscountMappingRepository.findById(restaurantDiscountId);
                
                if (restaurantDiscountMapping.isPresent()) {
                    restaurantDiscountMappingsToDelete.add(restaurantDiscountMapping.get());
                }
            }
            
            if (!restaurantDiscountMappingsToDelete.isEmpty()) {
                restaurantDiscountMappingRepository.deleteAll(restaurantDiscountMappingsToDelete);
                log.info("Deleted {} restaurant-discount mappings for restaurant {} and menu {}", 
                    restaurantDiscountMappingsToDelete.size(), restaurantId, menuId);
            }
        }

        // 5b. Delete restaurant-promotion mappings for promotions associated with this menu
        List<MenuPromotionMapping> menuPromotionMappings = menuPromotionMappingRepository.findByMenu_Id(menuId);
        if (menuPromotionMappings != null && !menuPromotionMappings.isEmpty()) {
            List<RestaurantPromotionMapping> restaurantPromotionMappingsToDelete = new ArrayList<>();
            for (MenuPromotionMapping menuPromotionMapping : menuPromotionMappings) {
                Promotion promotion = menuPromotionMapping.getPromotion();
                RestaurantPromotionId restaurantPromotionId = new RestaurantPromotionId();
                restaurantPromotionId.setRestaurantId(restaurantId);
                restaurantPromotionId.setPromotionId(promotion.getId());
                
                Optional<RestaurantPromotionMapping> restaurantPromotionMapping = 
                    restaurantPromotionMappingRepository.findById(restaurantPromotionId);
                
                if (restaurantPromotionMapping.isPresent()) {
                    restaurantPromotionMappingsToDelete.add(restaurantPromotionMapping.get());
                }
            }
            
            if (!restaurantPromotionMappingsToDelete.isEmpty()) {
                restaurantPromotionMappingRepository.deleteAll(restaurantPromotionMappingsToDelete);
                log.info("Deleted {} restaurant-promotion mappings for restaurant {} and menu {}", 
                    restaurantPromotionMappingsToDelete.size(), restaurantId, menuId);
            }
        }

        // 6. Check if this was the last restaurant for this menu in the restaurant group
        // If so, we might want to remove the group-menu mapping as well
        RestaurantGroup restaurantGroup = restaurant.getRestaurantGroup();
        if (restaurantGroup != null) {
            // Check if there are any other restaurants in the same group still assigned to this menu
            List<Restaurant> otherGroupRestaurants = restaurantRepository
                    .findByRestaurantGroup_IdAndIsDeletedFalseAndStatus(restaurantGroup.getId(), EntityStatus.ACTIVE)
                    .stream()
                    .filter(r -> !r.getId().equals(restaurantId))
                    .collect(Collectors.toList());

            if (!otherGroupRestaurants.isEmpty()) {
                List<UUID> otherRestaurantIds = otherGroupRestaurants.stream()
                        .map(Restaurant::getId)
                        .collect(Collectors.toList());
                
                // Check if any other restaurant in the group is still assigned to this menu
                boolean hasOtherAssignments = restaurantMenuMappingRepository
                        .findById_RestaurantIdIn(otherRestaurantIds)
                        .stream()
                        .anyMatch(mapping -> mapping.getMenu().getId().equals(menuId));
                
                // If no other restaurants in the group are assigned to this menu, remove group-menu mapping
                if (!hasOtherAssignments) {
                    Optional<RestaurantGroupMenuMapping> groupMenuMapping = groupMenuMappingRepository
                            .findByMenuIdAndRestaurantGroupId(menuId, restaurantGroup.getId());
                    if (groupMenuMapping.isPresent()) {
                        groupMenuMappingRepository.delete(groupMenuMapping.get());
                        log.info("Removed group-menu mapping as no restaurants in group {} are assigned to menu {}", 
                                restaurantGroup.getId(), menuId);
                    }
                }
            } else {
                // No other restaurants in the group, remove group-menu mapping
                Optional<RestaurantGroupMenuMapping> groupMenuMapping = groupMenuMappingRepository
                        .findByMenuIdAndRestaurantGroupId(menuId, restaurantGroup.getId());
                if (groupMenuMapping.isPresent()) {
                    groupMenuMappingRepository.delete(groupMenuMapping.get());
                    log.info("Removed group-menu mapping as no restaurants remain in group {} for menu {}", 
                            restaurantGroup.getId(), menuId);
                }
            }
        }
                
        // 7. Remove all availability records for this restaurant-menu combination
        List<CategoryItemMapping> menuCategoryItemMappings =
                categoryItemMappingRepository.findByMenuCategoryMappingMenuId(menuId);
        removeAvailabilityForRestaurantMenuMappingOptimized(restaurantId, menuCategoryItemMappings);

        // 8. Check if there are any transactions with blocked statuses (OPEN, PENDING)
        // or orders with items/combos in blocked statuses (PUSHED, ON_HOLD, COOKING, READY, DELAYED) for this restaurant
        // that contain items from this specific menu
        // Note: COMPLETED, CANCELLED, REFUNDED, and PARTIALLY_REFUNDED are final states and should allow menu unassignment
        List<TransactionStatus> blockedTransactionStatuses = java.util.Arrays.asList(
                TransactionStatus.OPEN, 
                TransactionStatus.PENDING
        );
        
        List<ItemStatus> blockedItemStatuses = java.util.Arrays.asList(
                ItemStatus.PUSHED,
                ItemStatus.ON_HOLD,
                ItemStatus.COOKING,
                ItemStatus.READY,
                ItemStatus.DELAYED
        );
        
        // Get all item IDs that belong to this menu
        List<UUID> menuItemIds = menuCategoryItemMappings.stream()
                .map(mapping -> mapping.getItem().getId())
                .distinct()
                .collect(Collectors.toList());
        
        if (!menuItemIds.isEmpty()) {
            // Find all transactions for this restaurant
            List<Transaction> allTransactions = transactionRepository.findByRestaurantId(restaurantId);
            
            // Check each transaction
            for (Transaction transaction : allTransactions) {
                Order order = transaction.getOrder();
                TransactionStatus transactionStatus = transaction.getTransactionStatus();
                
                // Skip if order is null or transaction is in final states (COMPLETED, CANCELLED, REFUNDED, PARTIALLY_REFUNDED)
                // Final states allow unassignment, so no need to check item statuses
                if (order == null || 
                    transactionStatus == TransactionStatus.COMPLETED || 
                    transactionStatus == TransactionStatus.CANCELED || 
                    transactionStatus == TransactionStatus.REFUNDED ||
                    transactionStatus == TransactionStatus.PARTIALLY_REFUNDED) {
                    continue;
                }
                
                // Check transaction status - if it's in blocked statuses, block unassignment
                if (blockedTransactionStatuses.contains(transactionStatus)) {
                    // Check if this transaction has any items from the menu
                    List<OrderedItem> orderedItems = orderedItemRepository.findByOrderId(order.getId());
                    List<OrderedCombo> orderedCombos = orderedComboRepository.findByOrderId(order.getId());
                    
                    boolean hasMenuItems = orderedItems.stream()
                            .anyMatch(oi -> oi.getItem() != null && 
                                    oi.getOrderedCombo() == null && // Only standalone items
                                    menuItemIds.contains(oi.getItem().getId()));
                    
                    boolean hasMenuCombos = orderedCombos.stream()
                            .anyMatch(oc -> oc.getCombo() != null && 
                                    oc.getCombo().getMenu() != null &&
                                    oc.getCombo().getMenu().getId().equals(menuId));
                    
                    // Check if combo items belong to menu
                    boolean hasMenuItemsInCombos = orderedItems.stream()
                            .anyMatch(oi -> oi.getItem() != null && 
                                    oi.getOrderedCombo() != null && // Items within combos
                                    menuItemIds.contains(oi.getItem().getId()));
                    
                    if (hasMenuItems || hasMenuCombos || hasMenuItemsInCombos) {
                        throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                                messageUtil.getMessage("restaurant.menu.removal.error.ongoing.orders", userLocale));
                    }
                }
            }
        }

        // 9. Check if there are any KDS records assigned to this restaurant
        // If KDS exists but no ongoing orders, remove the KDS and proceed with menu unassignment
        Specification<Kds> kdsSpec = (root, query, cb) -> cb.and(
                cb.equal(root.get("restaurantId"), restaurantId),
                cb.equal(root.get(FIELD_IS_DELETED), false)
        );
        List<Kds> allKdsForRestaurant = kdsRepository.findAll(kdsSpec);
        
        if (!allKdsForRestaurant.isEmpty()) {
            // Soft delete all KDS devices for this restaurant
            // Note: updatedBy is set to null as we don't have user context in this method
            for (Kds kds : allKdsForRestaurant) {
                kds.setIsDeleted(true);
                kds.setUpdatedAt(OffsetDateTime.now(ZoneOffset.UTC));
                kds.setUpdatedBy(null);
                kdsRepository.save(kds);
                log.info("Soft deleted KDS {} for restaurant {} during menu unassignment", kds.getId(), restaurantId);
            }
        }

        log.info("Successfully removed restaurant {} from menu {}", restaurantId, menuId);
        
        return ResponseDto.<Void>builder()
                .message(messageUtil.getMessage("restaurant.menu.mapping.removed.success", userLocale))
                .build();
    }

    /**
     * Retrieves a paginated and filterable list of restaurants assigned to a menu.
     * Includes restaurant details, menu assignment status, and translations.
     * Supports filtering by menu status, restaurant group, and search term.
     *
     * @param menuId          the UUID of the menu
     * @param locale          locale code for localized responses
     * @param menuStatus      optional filter by restaurant menu mapping status
     * @param restaurantGroupId optional filter by restaurant group ID
     * @param search          optional search term for restaurant name
     * @param page            page number for pagination
     * @param size            page size for pagination
     * @return ResponseDto containing paginated list of restaurants with menu assignment details
     * @throws ResponseStatusException if menu not found
     */
    @Override
    @Transactional(readOnly = true)
    public ResponseDto<RestaurantMenuDtoListResponse> getRestaurantsByMenuId(
            UUID menuId,
            String locale,
            RestaurantMenuMappingStatus menuStatus,
            UUID restaurantGroupId,
            String search,
            Integer page,
            Integer size
    ) {
        Locale userLocale = Locale.forLanguageTag(locale);

        // 1. Validate locale
        if (!localizationProperties.getLanguages().contains(locale)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        messageUtil.getMessage(MSG_MENU_ERROR_INVALID_LANGUAGE, userLocale));
        }

        // 2. Validate menu exists
        if (!menuRepository.existsById(menuId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                    messageUtil.getMessage(MSG_MENU_NOT_FOUND, userLocale, menuId));
        }

        // 3. Set pagination parameters
        int pageNumber = (page != null ? page : 1) - 1;
        if (pageNumber < 0) pageNumber = 0;
        int pageSize = size != null ? size : Integer.MAX_VALUE;
        if (pageSize < 1) pageSize = Integer.MAX_VALUE;

        // 4. Get restaurant menu mappings for this menu
        List<RestaurantMenuMapping> restaurantMappings = restaurantMenuMappingRepository.findById_MenuId(menuId);
        
        // 5. Filter by menu status if provided
        List<RestaurantMenuMapping> filteredMappings = restaurantMappings.stream()
            .filter(mapping -> menuStatus == null || mapping.getStatus() == menuStatus)
            .collect(Collectors.toList());
        
        // 6. Filter by restaurant group if provided
        if (restaurantGroupId != null) {
            filteredMappings = filteredMappings.stream()
                .filter(mapping -> mapping.getRestaurant().getRestaurantGroup() != null && 
                    mapping.getRestaurant().getRestaurantGroup().getId().equals(restaurantGroupId))
                .collect(Collectors.toList());
        }
        
        // 7. Apply search filter if provided
        if (search != null && !search.trim().isEmpty()) {
            filteredMappings = filteredMappings.stream()
                .filter(mapping -> {
                    Restaurant restaurant = mapping.getRestaurant();
                    // Check restaurant code
                    if (restaurant.getRestaurantCode() != null && 
                        restaurant.getRestaurantCode().toLowerCase().contains(search.toLowerCase())) {
                        return true;
                    }
                    
                    // Check restaurant name translations
                    List<RestaurantTranslation> translations = restaurantTranslationRepository
                        .findAllByRestaurantIdWithLanguage(restaurant.getId());
                    return translations.stream()
                        .anyMatch(translation -> translation.getName() != null && 
                            translation.getName().toLowerCase().contains(search.toLowerCase()));
                })
                .collect(Collectors.toList());
        }
        
        // 8. Build response DTOs with fallback language logic
        List<RestaurantMenuDetailsResponseDto> restaurantDetails = filteredMappings.stream()
            .map(mapping -> {
                Restaurant restaurant = mapping.getRestaurant();
                
                // Get restaurant name with fallback
                String restaurantName = "";
                List<RestaurantTranslation> restaurantTranslations = restaurantTranslationRepository
                    .findAllByRestaurantIdWithLanguage(restaurant.getId());
                
                if (!restaurantTranslations.isEmpty()) {
                    // Try exact match first
                    RestaurantTranslation exactMatch = restaurantTranslations.stream()
                        .filter(t -> t.getLanguageCode() != null && t.getLanguageCode().equals(locale))
                        .findFirst()
                        .orElse(null);
                    
                    if (exactMatch != null) {
                        restaurantName = exactMatch.getName();
                    } else {
                        // Fallback using TranslationUtils
                        java.util.Optional<RestaurantTranslation> fallback =
                                TranslationUtils.pickPreferredOrFromList(
                                        restaurantTranslations,
                                        locale,
                                        localizationProperties.getLanguages(),
                                        RestaurantTranslation::getLanguageCode
                                );
                        // Final fallback: use first available translation if TranslationUtils returns empty
                        restaurantName = fallback.map(RestaurantTranslation::getName)
                                .orElseGet(() -> restaurantTranslations.stream()
                                        .filter(t -> t.getName() != null && !t.getName().isEmpty())
                                        .findFirst()
                                        .map(RestaurantTranslation::getName)
                                        .orElse(""));
                    }
                }
                
                // Get restaurant group name with fallback
                String restaurantGroupName = "";
                if (restaurant.getRestaurantGroup() != null) {
                    List<RestaurantGroupTranslation> groupTranslations = restaurantGroupTranslationRepository
                        .findAllByRestaurantGroupIdWithLanguage(restaurant.getRestaurantGroup().getId());
                    
                    if (!groupTranslations.isEmpty()) {
                        // Try exact match first
                        RestaurantGroupTranslation exactMatch = groupTranslations.stream()
                            .filter(t -> t.getLanguageCode() != null && t.getLanguageCode().equals(locale))
                            .findFirst()
                            .orElse(null);
                        
                        if (exactMatch != null) {
                            restaurantGroupName = exactMatch.getName();
                        } else {
                            // Fallback using TranslationUtils
                            java.util.Optional<RestaurantGroupTranslation> fallback =
                                    TranslationUtils.pickPreferredOrFromList(
                                            groupTranslations,
                                            locale,
                                            localizationProperties.getLanguages(),
                                            RestaurantGroupTranslation::getLanguageCode
                                    );
                            restaurantGroupName = fallback.map(RestaurantGroupTranslation::getName)
                                    .orElse("");
                        }
                    }
                }
                
                // Return UTC time directly - frontend will handle timezone conversion for display
                return RestaurantMenuDetailsResponseDto.builder()
                    .restaurantId(restaurant.getId())
                    .restaurantName(restaurantName)
                    .restaurantCode(restaurant.getRestaurantCode())
                    .restaurantGroupId(restaurant.getRestaurantGroup() != null ? restaurant.getRestaurantGroup().getId() : null)
                    .restaurantGroupName(restaurantGroupName)
                    .menuStatus(mapping.getStatus())
                    .restaurantStatus(restaurant.getStatus())
                    .assignedAt(restaurant.getCreatedAt())
                    .assignedBy(restaurant.getCreatedBy() != null ? restaurant.getCreatedBy().getFirstName() : null)
                    .schedulePublishTime(mapping.getScheduledPublishTime() != null ? 
                        mapping.getScheduledPublishTime().truncatedTo(ChronoUnit.SECONDS) : null) // Truncate microseconds
                    .build();
            })
            .collect(Collectors.toList());

        // 9. Handle empty results
        if (restaurantDetails.isEmpty()) {
            RestaurantMenuDtoListResponse emptyResponse = RestaurantMenuDtoListResponse.builder()
                    .restaurants(new ArrayList<>())
                    .count(0L)
                    .total(0L)
                    .metaData(PaginationMetaData.builder()
                            .page(pageNumber + 1)
                            .size(pageSize)
                            .totalPages(0)
                            .totalRecords(0L)
                            .build())
                    .build();

            return ResponseDto.<RestaurantMenuDtoListResponse>builder()
                    .message(messageUtil.getMessage("menu.restaurants.success", userLocale))
                    .data(emptyResponse)
                    .build();
        }

        // 10. Apply pagination in memory (since we can't do it in the query easily with complex joins)
        int totalRecords = restaurantDetails.size();
        int totalPages = (int) Math.ceil((double) totalRecords / pageSize);

        if (pageNumber >= totalPages && totalRecords > 0) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                    messageUtil.getMessage("pagination.error.page.not.found", userLocale));
        }

        int fromIndex = pageNumber * pageSize;
        int toIndex = Math.min(fromIndex + pageSize, totalRecords);
        
        List<RestaurantMenuDetailsResponseDto> pagedResults = new ArrayList<>();
        if (fromIndex < totalRecords) {
            pagedResults = restaurantDetails.subList(fromIndex, toIndex);
        }

        // 11. Build response
        PaginationMetaData metaData = PaginationMetaData.builder()
                .page(pageNumber + 1)
                .size(pageSize)
                .totalPages(totalPages)
                .totalRecords((long) totalRecords)
                .build();

        RestaurantMenuDtoListResponse listResponse = RestaurantMenuDtoListResponse.builder()
                .restaurants(pagedResults)
                .count((long) pagedResults.size())
                .total((long) totalRecords)
                .metaData(metaData)
                .build();

        return ResponseDto.<RestaurantMenuDtoListResponse>builder()
                .message(messageUtil.getMessage("menu.restaurants.success", userLocale))
                .data(listResponse)
                .build();
    }


    /**
     * Schedules a menu to be published to restaurants at a specific UTC time.
     * Creates Quartz jobs for each restaurant to publish the menu at the scheduled time.
     * Validates that scheduled time is in the future.
     *
     * @param request the schedule request with menu ID, restaurant IDs, and scheduled publish time (UTC)
     * @param userId  the ID of the user scheduling the menu
     * @param locale  locale code for localized error messages
     * @return ResponseDto with success message
     * @throws ResponseStatusException if menu not found, restaurant not found, scheduled time in past, or validation fails
     */
    @Override
    @Transactional
    public ResponseDto<String> scheduleMenuForRestaurants(
            ScheduleMenuRequest request,
            String userId,
            String locale) {
        
        Locale userLocale = Locale.forLanguageTag(locale);
        
        // Use UTC timezone for all operations - frontend sends UTC time
        ZoneOffset utcOffset = ZoneOffset.UTC;
        
               // Validate menu exists and eagerly fetch translations to avoid LazyInitializationException
               Menu menu = menuRepository.findById(request.getMenuId())
               .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, 
                   messageUtil.getMessage(MSG_MENU_NOT_FOUND, userLocale)));
       
       // Eagerly load translations to avoid LazyInitializationException in async method
       menu.getTranslations().size(); // This forces the lazy collection to load
       
        // Validate all restaurant IDs exist and are assigned to this menu
        List<RestaurantMenuMapping> existingMappings = restaurantMenuMappingRepository
                .findById_RestaurantIdIn(request.getRestaurantIds());
        
        // Check if all restaurants are assigned to this specific menu
        List<UUID> assignedRestaurantIds = existingMappings.stream()
                .filter(mapping -> mapping.getId().getMenuId().equals(request.getMenuId()))
                .map(mapping -> mapping.getId().getRestaurantId())
                .collect(Collectors.toList());
        
        List<UUID> requestedRestaurantIds = request.getRestaurantIds();
        List<UUID> unassignedRestaurantIds = requestedRestaurantIds.stream()
                .filter(restaurantId -> !assignedRestaurantIds.contains(restaurantId))
                .collect(Collectors.toList());
        
        if (!unassignedRestaurantIds.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, 
                messageUtil.getMessage("menu.restaurants.not.assigned", userLocale, 
                    unassignedRestaurantIds.toString()));
        }
        
        // Check if any restaurant already has LIVE status for this menu
        List<RestaurantMenuMapping> liveMappings = existingMappings.stream()
                .filter(mapping -> mapping.getId().getMenuId().equals(request.getMenuId()) 
                        && mapping.getStatus() == RestaurantMenuMappingStatus.LIVE)
                .toList();
        
        if (!liveMappings.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, 
                messageUtil.getMessage("menu.restaurants.already.live", userLocale));
        }
        
        // Check if time is in the past using UTC timezone
        OffsetDateTime nowInUtc = OffsetDateTime.now(utcOffset);
        if (request.getSchedulePublishTime() != null && request.getSchedulePublishTime().isBefore(nowInUtc)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, 
                messageUtil.getMessage("menu.schedule.time.past", userLocale));
        }
        
               // If schedule time is null, execute immediately
               if (request.getSchedulePublishTime() == null) {
                // Filter mappings for this specific menu and update them in batch
                List<RestaurantMenuMapping> mappingsToUpdate = existingMappings.stream()
                    .filter(mapping -> mapping.getId().getMenuId().equals(request.getMenuId()))
                    .peek(mapping -> {
                        mapping.setStatus(RestaurantMenuMappingStatus.LIVE);
                        // Store current time in UTC for consistency
                        mapping.setScheduledPublishTime(OffsetDateTime.now(utcOffset));
                    })
                    .collect(Collectors.toList());
                
                // Batch save all mappings at once
                if (!mappingsToUpdate.isEmpty()) {
                    restaurantMenuMappingRepository.saveAll(mappingsToUpdate);
                }
                
                // Send immediate notification asynchronously to avoid blocking
                CompletableFuture.runAsync(() -> {
                    try {
                        RestaurantMenuScheduleJob.sendRestaurantMenuLiveNotification(
                            menu,
                            new HashSet<>(request.getRestaurantIds()),
                            emailSender,
                            applicationContext,
                            userLocale);
                    } catch (Exception e) {
                        log.error("Error sending notification", e);
                    }
                });
                
                return ResponseDto.<String>builder()
                        .message(messageUtil.getMessage("menu.restaurants.live.immediate", userLocale))
                        .data("Menu status changed to LIVE immediately")
                        .build();
            } else {
                // Frontend sends UTC time, so use it directly
                OffsetDateTime utcTime = request.getSchedulePublishTime();
                
                try {
                    // Schedule the job for future execution using UTC time
                    scheduleRestaurantMenuJob(request, userId);
                } catch (SchedulerException e) {
                    log.error("Failed to schedule restaurant menu job", e);
                    throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, 
                        messageUtil.getMessage("menu.schedule.error", userLocale));
                }
                
                // Filter and update mappings for this specific menu in batch
                List<RestaurantMenuMapping> mappingsToUpdate = existingMappings.stream()
                    .filter(mapping -> mapping.getId().getMenuId().equals(request.getMenuId()))
                    .peek(mapping -> {
                        // Update status to SCHEDULED if it's UNSCHEDULED
                        if (mapping.getStatus() == RestaurantMenuMappingStatus.UNSCHEDULED) {
                            mapping.setStatus(RestaurantMenuMappingStatus.SCHEDULED);
                        }
                        // Store the UTC time in database
                        mapping.setScheduledPublishTime(utcTime);
                    })
                    .collect(Collectors.toList());
                
                // Batch save all mappings at once
                if (!mappingsToUpdate.isEmpty()) {
                    restaurantMenuMappingRepository.saveAll(mappingsToUpdate);
                }
                
                int scheduledRestaurantCount = request.getRestaurantIds().size();
                String scheduledMessage = scheduledRestaurantCount == 1
                        ? messageUtil.getMessage("menu.restaurants.scheduled.future.single", userLocale)
                        : messageUtil.getMessage("menu.restaurants.scheduled.future.multiple", userLocale);

                return ResponseDto.<String>builder()
                        .message(scheduledMessage)
                        .data(scheduledRestaurantCount == 1
                                ? "Menu will go live for 1 selected restaurant"
                                : "Menu will go live for " + scheduledRestaurantCount + " selected restaurants")
                        .build();
            }
    }
    
    /**
     * Schedules a Quartz job to publish a menu to a restaurant at a specific UTC time.
     * Validates that scheduled time is in the future. Creates a one-time trigger for the scheduled time.
     *
     * @param request the schedule request with menu ID, restaurant ID, and scheduled publish time (UTC)
     * @param userId  the ID of the user scheduling the menu
     * @throws SchedulerException if Quartz job creation fails
     * @throws IllegalArgumentException if scheduled time is in the past
     */
    private void scheduleRestaurantMenuJob(ScheduleMenuRequest request, String userId) throws SchedulerException {
        // Use UTC timezone for consistent scheduling
        ZoneOffset utcOffset = ZoneOffset.UTC;
        
        // Validate that the scheduled time is in the future
        OffsetDateTime now = OffsetDateTime.now(utcOffset);
        if (request.getSchedulePublishTime().isBefore(now)) {
            throw new IllegalArgumentException("Scheduled time must be in the future. Current time: " + now + ", Scheduled time: " + request.getSchedulePublishTime());
        }
        
        // Log the scheduling details for debugging
        log.info("Scheduling menu job for menuId: {}, scheduledTime: {}, currentTime: {}, utcOffset: {}", 
                request.getMenuId(), 
                request.getSchedulePublishTime(), 
                now, 
                utcOffset);
        
        // Create job detail
        JobDetail jobDetail = JobBuilder.newJob(RestaurantMenuScheduleJob.class)
                .withIdentity("restaurant-menu-schedule-" + request.getMenuId() + "-" + System.currentTimeMillis())
                .usingJobData(JOB_DATA_KEY_MENU_ID, request.getMenuId().toString())
                .usingJobData("restaurantIds", String.join(",", request.getRestaurantIds().stream()
                        .map(UUID::toString)
                        .toList()))
                .build();
        
        // Convert OffsetDateTime to Date using UTC timezone
        Date scheduledDate = Date.from(request.getSchedulePublishTime().toInstant());
        
        // Log the converted date for debugging
        log.info("Converted scheduled time to Date: {} (milliseconds: {})", scheduledDate, scheduledDate.getTime());
        
        // Create trigger
        Trigger trigger = TriggerBuilder.newTrigger()
                .withIdentity("restaurant-menu-trigger-" + request.getMenuId() + "-" + System.currentTimeMillis())
                .startAt(scheduledDate)
                .build();
        
        // Schedule the job
        scheduler.scheduleJob(jobDetail, trigger);
        
        log.info("Successfully scheduled job for menuId: {} at {}", request.getMenuId(), scheduledDate);
    }

    /**
     * Cancels all scheduled Quartz jobs for a menu.
     * Scans all job groups and removes jobs that have the menu ID in their job data.
     *
     * @param menuId the UUID of the menu to cancel scheduled jobs for
     * @throws SchedulerException if Quartz job deletion fails
     */
    private void cancelScheduledJobsForMenu(UUID menuId) throws SchedulerException {
        // Note: jobs and triggers are created with dynamic identities; we scan and remove by job data
        for (String groupName : scheduler.getJobGroupNames()) {
            for (JobKey jobKey : scheduler.getJobKeys(GroupMatcher.jobGroupEquals(groupName))) {
                JobDetail jobDetail = scheduler.getJobDetail(jobKey);
                if (jobDetail != null && jobDetail.getJobDataMap() != null) {
                    String jobMenuId = jobDetail.getJobDataMap().getString(JOB_DATA_KEY_MENU_ID);
                    if (jobMenuId != null && jobMenuId.equalsIgnoreCase(menuId.toString())) {
                        scheduler.deleteJob(jobKey);
                        log.info("Deleted Quartz job {} for archived menu {}", jobKey, menuId);
                    }
                }
            }
        }
    }

    /**
     * Copies category item mappings from source menu to new menu.
     * Uses the old-to-new MenuCategoryMapping map to link items to the correct category mappings in the new menu.
     *
     * @param sourceMenu        the source menu to copy mappings from
     * @param oldToNewMappingMap map of old MenuCategoryMapping ID to new MenuCategoryMapping
     */
    private void copyCategoryItemMappings(Menu sourceMenu, Map<UUID, MenuCategoryMapping> oldToNewMappingMap) {
        // Get all source MenuCategoryMapping IDs
        List<UUID> sourceMappingIds = new ArrayList<>(oldToNewMappingMap.keySet());
        
        // Find all CategoryItemMapping records for the source menu
        List<MenuCategoryMapping> sourceMappings = menuCategoryMappingRepository.findAllById(sourceMappingIds);
        
        // Validate that all source mappings belong to the source menu
        for (MenuCategoryMapping sourceMapping : sourceMappings) {
            if (!sourceMapping.getMenu().getId().equals(sourceMenu.getId())) {
                throw new IllegalStateException("Source mapping does not belong to source menu");
            }
        }
        
        List<CategoryItemMapping> sourceCategoryItemMappings = categoryItemMappingRepository.findByMenuCategoryMappingIn(sourceMappings);
        
        // Copy CategoryItemMapping records to the new menu
        for (CategoryItemMapping sourceCategoryItemMapping : sourceCategoryItemMappings) {
            MenuCategoryMapping newMenuCategoryMapping = oldToNewMappingMap.get(sourceCategoryItemMapping.getMenuCategoryMapping().getId());
            
            if (newMenuCategoryMapping != null) {
                CategoryItemMapping newCategoryItemMapping = new CategoryItemMapping();
                newCategoryItemMapping.setMenuCategoryMapping(newMenuCategoryMapping);
                newCategoryItemMapping.setItem(sourceCategoryItemMapping.getItem());
                categoryItemMappingRepository.save(newCategoryItemMapping);
            }
        }
    }

    /**
     * Optimized availability creation: works on a pre-fetched list of CategoryItemMappings
     * for a given menu, to avoid re-querying per restaurant.
     */
    private void createAvailabilityForRestaurantMenuMappingOptimized(
            UUID restaurantId,
            List<CategoryItemMapping> categoryItemMappings,
            UUID userId) {

        if (categoryItemMappings == null || categoryItemMappings.isEmpty()) {
            return;
        }

        // Preload existing availability records for this restaurant and menu items
        List<UUID> mappingIds = categoryItemMappings.stream()
                .map(CategoryItemMapping::getId)
                .collect(Collectors.toList());

        List<RestaurantItemAvailability> existingAvailabilities =
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

        for (CategoryItemMapping mapping : categoryItemMappings) {
            if (existingMappingIds.contains(mapping.getId())) {
                continue;
            }

            RestaurantItemAvailability availability = RestaurantItemAvailability.builder()
                    .restaurant(restaurant)
                    .categoryItemMapping(mapping)
                    .isAvailable(true) // Default to available
                    .createdBy(user)
                    .build();

            restaurantItemAvailabilityRepository.save(availability);
        }
    }

    /**
     * Optimized availability removal: works on a pre-fetched list of CategoryItemMappings
     * for the old menu, to avoid re-querying per restaurant.
     */
    private void removeAvailabilityForRestaurantMenuMappingOptimized(
            UUID restaurantId,
            List<CategoryItemMapping> categoryItemMappings) {

        if (categoryItemMappings == null || categoryItemMappings.isEmpty()) {
            return;
        }

        // Fetch existing availabilities for this restaurant and these mappings
        List<UUID> mappingIds = categoryItemMappings.stream()
                .map(CategoryItemMapping::getId)
                .collect(Collectors.toList());

        List<RestaurantItemAvailability> existingAvailabilities =
                restaurantItemAvailabilityRepository.findByRestaurantIdAndCategoryItemMappingIdIn(
                        restaurantId, mappingIds);

        // Delete all fetched availability records in batch (or one-by-one if JPA flushes that way)
        for (RestaurantItemAvailability availability : existingAvailabilities) {
            restaurantItemAvailabilityRepository.delete(availability);
        }
    }

    /**
     * Changes the availability status of an item for a specific restaurant and menu.
     * Resolves every category placement for that menu and item, then updates existing
     * {@link RestaurantItemAvailability} rows only (no create).
     *
     * @param request the availability change request with restaurant ID, menu ID, item ID, and status
     * @param userId  the ID of the user performing the change
     * @param locale  locale code for localized error messages
     * @return ResponseDto with success message
     * @throws ResponseStatusException if restaurant not found, menu not found, item not found, or validation fails
     */
    @Override
    @Transactional
    public ResponseDto<String> changeItemAvailability(ItemAvailabilityChangeRequest request, String userId, String locale) {
        Locale userLocale = Locale.forLanguageTag(locale);
        
        // 1. Validate restaurant exists
        Restaurant restaurant = restaurantRepository.findById(UUID.fromString(request.getRestaurantId()))
            .orElseThrow(() -> new ResponseStatusException(
                HttpStatus.NOT_FOUND,
                messageUtil.getMessage(MSG_RESTAURANT_NOT_FOUND, userLocale)
            ));
        
        // 2. Validate menu exists
        menuRepository.findById(UUID.fromString(request.getMenuId()))
            .orElseThrow(() -> new ResponseStatusException(
                HttpStatus.NOT_FOUND,
                messageUtil.getMessage(MSG_MENU_NOT_FOUND, userLocale)
            ));
        
        // 3. Validate item exists
        Item item = itemRepository.findById(UUID.fromString(request.getItemId()))
            .orElseThrow(() -> new ResponseStatusException(
                HttpStatus.NOT_FOUND,
                messageUtil.getMessage(MSG_ITEM_NOT_FOUND, userLocale)
            ));
        
        UUID menuId = UUID.fromString(request.getMenuId());
        UUID itemUuid = UUID.fromString(request.getItemId());

        // 4. Every category_item_mapping for this item on the requested menu (item may appear in multiple categories)
        List<CategoryItemMapping> categoryItemMappings = categoryItemMappingRepository
            .findAllByMenuCategoryMappingMenuIdAndItemId(menuId, itemUuid);
        if (categoryItemMappings.isEmpty()) {
            throw new ResponseStatusException(
                HttpStatus.NOT_FOUND,
                messageUtil.getMessage("item.not.found.in.menu", userLocale)
            );
        }

        // 5. Get user
        User user = userRepository.findById(UUID.fromString(userId))
            .orElseThrow(() -> new ResponseStatusException(
                HttpStatus.NOT_FOUND,
                messageUtil.getMessage(MSG_USER_NOT_FOUND, userLocale)
            ));

        Set<UUID> expectedCategoryItemMappingIds = categoryItemMappings.stream()
            .map(CategoryItemMapping::getId)
            .collect(Collectors.toSet());

        // 6. Load existing availability rows and create missing ones for extra placements.
        List<RestaurantItemAvailability> existingAvailabilities =
            restaurantItemAvailabilityRepository.findByRestaurantIdAndCategoryItemMappingIdIn(
                restaurant.getId(), List.copyOf(expectedCategoryItemMappingIds));

        Set<UUID> foundCategoryItemMappingIds = existingAvailabilities.stream()
            .map(ria -> ria.getCategoryItemMapping().getId())
            .collect(Collectors.toSet());

        List<RestaurantItemAvailability> availabilitiesToSave = new ArrayList<>(existingAvailabilities);
        for (CategoryItemMapping mapping : categoryItemMappings) {
            if (foundCategoryItemMappingIds.contains(mapping.getId())) {
                continue;
            }
            availabilitiesToSave.add(RestaurantItemAvailability.builder()
                .restaurant(restaurant)
                .categoryItemMapping(mapping)
                .isAvailable(request.getIsAvailable())
                .createdBy(user)
                .updatedBy(user)
                .updatedAt(OffsetDateTime.now(ZoneOffset.UTC))
                .build());
        }

        // 7. Update every placement for this menu + item
        OffsetDateTime updatedAt = OffsetDateTime.now(ZoneOffset.UTC);
        Boolean oldAvailability = existingAvailabilities.isEmpty() ? null : existingAvailabilities.get(0).getIsAvailable();
        for (RestaurantItemAvailability availability : availabilitiesToSave) {
            availability.setIsAvailable(request.getIsAvailable());
            availability.setUpdatedBy(user);
            availability.setUpdatedAt(updatedAt);
        }
        restaurantItemAvailabilityRepository.saveAll(availabilitiesToSave);

        String status = request.getIsAvailable() ? "available" : "unavailable";
        log.info("Successfully updated item {} availability to {} for restaurant {} ({} category mapping(s) on menu {})",
            request.getItemId(), status, request.getRestaurantId(), availabilitiesToSave.size(), menuId);

        // Create audit trail for item availability update
        try {
            auditTrailService.createAuditTrail(
                    user,
                    ActionType.ITEM_AVAILABILITY_UPDATE,
                    restaurant,
                    RequestStatus.NA,
                    null, // ipAddress
                    null, // userAgent
                    item.getId(),
                    ENTITY_TYPE_ITEM,
                    String.format("Item availability changed from %s to %s for %d category mapping(s) on menu %s",
                            oldAvailability != null && oldAvailability ? "available" : "unavailable",
                            status,
                            availabilitiesToSave.size(),
                            request.getMenuId())
            );
        } catch (Exception e) {
            log.error("Failed to create audit trail for item availability update: {}", e.getMessage());
        }
        
        // Publish WebSocket notification for item availability change
        try {
            String topic = "/topic/item-availability";
            Map<String, Object> itemData = new HashMap<>();
            itemData.put("itemId", request.getItemId());
            itemData.put(JOB_DATA_KEY_MENU_ID, request.getMenuId());
            itemData.put("restaurantId", request.getRestaurantId());
            itemData.put("isAvailable", request.getIsAvailable());
            itemData.put(FIELD_STATUS, status);
            itemData.put("notificationType", "ITEM_AVAILABILITY_UPDATE");
            itemData.put("timestamp", LocalDateTime.now(ZoneOffset.UTC).format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
            
            StatusEventMessage eventMessage = StatusEventMessage.builder()
                    .message(messageUtil.getMessage("item.availability.updated", userLocale))
                    .notificationType("ITEM_AVAILABILITY_UPDATE")
                    .itemId(request.getItemId())
                    .status(status)
                    .data(itemData)
                    .build();
            
            // Send directly to WebSocket clients
            if (messagingTemplate != null) {
                messagingTemplate.convertAndSend(topic, eventMessage);
                log.info("[Notification][WebSocket] broadcast topic={} notificationType=ITEM_AVAILABILITY_UPDATE itemId={}, restaurantId={}, status={}",
                        topic, request.getItemId(), request.getRestaurantId(), status);
            }
            
            // Also publish to RabbitMQ for integration service to log
            orderNotificationService.publishToRabbitMQ(topic, eventMessage);
        } catch (Exception e) {
            log.error("Failed to publish WebSocket notification for item availability update: {}", e.getMessage(), e);
        }
        
        return ResponseDto.<String>builder()
            .message(messageUtil.getMessage("item.availability.updated", userLocale))
            .data("Item availability updated to " + status)
            .build();
    }
    
  
    /**
     * Checks if a discount is currently active for a menu and restaurant.
     * Validates discount status, deletion state, usage limits, and restaurant/menu-specific
     * validity periods, time restrictions, and day-of-week restrictions.
     * Checks RestaurantDiscountMapping first if restaurantId is provided, then MenuDiscountMapping.
     *
     * @param discount    the discount to check
     * @param menuId      the UUID of the menu
     * @param restaurantId the UUID of the restaurant (optional)
     * @return true if discount is active, false otherwise
     */
    private boolean isDiscountActive(Discount discount, UUID menuId, UUID restaurantId) {
        // First check: discount is active and not deleted
        if (discount == null || discount.getIsDeleted() || discount.getStatus() != EntityStatus.ACTIVE) {
            log.info("Discount {} is null, deleted, or not active. isDeleted={}, status={}", 
                    discount != null ? discount.getId() : "null", 
                    discount != null ? discount.getIsDeleted() : "null",
                    discount != null ? discount.getStatus() : "null");
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
            
            log.info("Checking RestaurantDiscountMapping for discount {} and restaurant {}: {}", 
                    discount.getId(), restaurantId, restaurantDiscountMappingOpt.isPresent() ? "FOUND" : "NOT FOUND");
            
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
                    DayOfWeek currentDay = convertToDayOfWeek(nowUtc.getDayOfWeek());
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
            
            log.info("Checking MenuDiscountMapping for discount {} and menu {}: {}", 
                    discount.getId(), menuId, menuDiscountMappingOpt.isPresent() ? "FOUND" : "NOT FOUND");
            
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
                    DayOfWeek currentDay = convertToDayOfWeek(nowUtc.getDayOfWeek());
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
        log.info("No discount mapping found for discount {}, menu {}, restaurant {}. Considering active (backward compatibility).", 
            discount.getId(), menuId, restaurantId);
        return true;
    }

    // Helper method to convert Java DayOfWeek to custom DayOfWeek enum
    /**
     * Converts Java DayOfWeek enum to custom DayOfWeek enum.
     *
     * @param javaDayOfWeek the Java DayOfWeek value
     * @return corresponding custom DayOfWeek enum value (defaults to SUNDAY if unknown)
     */
    private DayOfWeek convertToDayOfWeek(java.time.DayOfWeek javaDayOfWeek) {
        switch (javaDayOfWeek) {
            case SUNDAY: return DayOfWeek.SUNDAY;
            case MONDAY: return DayOfWeek.MONDAY;
            case TUESDAY: return DayOfWeek.TUESDAY;
            case WEDNESDAY: return DayOfWeek.WEDNESDAY;
            case THURSDAY: return DayOfWeek.THURSDAY;
            case FRIDAY: return DayOfWeek.FRIDAY;
            case SATURDAY: return DayOfWeek.SATURDAY;
            default: return DayOfWeek.SUNDAY;
        }
    }

    // Helper method to check if current time is within the allowed range
    private boolean isTimeInRange(LocalTime currentTime, LocalTime startTime, LocalTime endTime) {
        // Handle cases where end time might be next day (e.g., 23:00 to 02:00)
        if (startTime.isBefore(endTime)) {
            // Normal case: start < end (e.g., 12:00 to 18:00)
            return !currentTime.isBefore(startTime) && !currentTime.isAfter(endTime);
        } else {
            // Overnight case: start > end (e.g., 23:00 to 02:00)
            return !currentTime.isBefore(startTime) || !currentTime.isAfter(endTime);
        }
    }

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

  
    /**
     * Calculates discount information for an item in a menu and restaurant context.
     * Checks item-level and category-level discounts, finds the best applicable discount,
     * and returns discount details including BXGY information.
     *
     * @param basePrice    the base price of the item
     * @param item         the item entity
     * @param mcms         list of menu category mappings for the item
     * @param menuId       the UUID of the menu
     * @param restaurantId the UUID of the restaurant
     * @param userLocale   locale for localized discount labels (e.g. BXGY detail)
     * @return DiscountInfo with discount details or null if no discount applies
     */
    private DiscountInfo calculateDiscountInfo(Double basePrice, Item item, List<MenuCategoryMapping> mcms,
            UUID menuId, UUID restaurantId, Locale userLocale) {
        log.info("Calculating discount for item: {} with basePrice: {}, menuId: {}, restaurantId: {}", 
                item.getId(), basePrice, menuId, restaurantId);
        log.info("MenuCategoryMappings count: {}", mcms.size());
        
        if (basePrice == null || basePrice <= 0) {
            log.info("Base price is null or <= 0, returning no discount");
            return new DiscountInfo(basePrice, null, false, false, null, null, null, null);
        }
        
        // Get ALL CategoryItemMappings for this item that belong to the menu (not just current categories)
        // This ensures we find BXGY discounts even if assigned using a different CategoryItemMapping
        List<MenuCategoryMapping> allMenuCategoryMappings = menuCategoryMappingRepository.findByMenuId(menuId);
        List<CategoryItemMapping> itemMappings = categoryItemMappingRepository.findByMenuCategoryMappingIn(allMenuCategoryMappings)
            .stream()
            .filter(mapping -> mapping.getItem().getId().equals(item.getId()))
            .collect(Collectors.toList());
        
        log.info("Found {} item mappings for item {} in menu {}", itemMappings.size(), item.getId(), menuId);
        
        boolean isBxgyBuyItem = false;
        boolean isBxgyGetItem = false;
        String discountDetail = null;
        Integer buyQuantity = null;
        Integer getQuantity = null;
        UUID discountId = null;
        Discount bxgyDiscount = null;
        
        // Check BXGY discounts for this item - query by CategoryItemMapping IDs
        // This ensures we only find discounts where the CategoryItemMapping is actually in discount_bxgy_item table
        log.info("Checking BXGY discounts for item {} in menu {} using {} CategoryItemMapping IDs", item.getId(), menuId, itemMappings.size());
        
        if (!itemMappings.isEmpty()) {
            // Extract CategoryItemMapping IDs for this item in the menu
            List<UUID> categoryItemMappingIds = itemMappings.stream()
                    .map(CategoryItemMapping::getId)
                    .collect(Collectors.toList());
            
            // Query buy items by CategoryItemMapping IDs - only finds discounts where the mapping ID is in discount_bxgy_item
            List<DiscountBxgyItem> buyItems = discountBxgyItemRepository.findByBuyItemMappingIdsAndMenuId(
                    categoryItemMappingIds, menuId, DiscountType.BXGY, EntityStatus.ACTIVE);
            log.info("Found {} buy items for BXGY discount for item {} using CategoryItemMapping IDs", buyItems.size(), item.getId());
            
            for (DiscountBxgyItem bxgy : buyItems) {
                // Additional check for validity period and usage limits
                Discount discount = bxgy.getDiscount();
                if (discount != null) {
                    log.info("Checking if discount {} is active for menu {} and restaurant {}", discount.getId(), menuId, restaurantId);
                    boolean isActive = isDiscountActive(discount, menuId, restaurantId);
                    log.info("Discount {} isActive: {}", discount.getId(), isActive);
                    if (isActive) {
                        isBxgyBuyItem = true;
                        bxgyDiscount = discount;
                        discountDetail = generateBxgyDetail(bxgyDiscount, userLocale);
                        buyQuantity = bxgyDiscount.getBuyQuantity();
                        getQuantity = bxgyDiscount.getGetQuantity();
                        discountId = bxgyDiscount.getId();
                        log.info("Item {} is a BXGY buy item with discount {} using CategoryItemMapping {}", item.getId(), discountId, bxgy.getBuyItemMapping().getId());
                        break;
                    }
                }
            }
            
            // Query get items by CategoryItemMapping IDs - only finds discounts where the mapping ID is in discount_bxgy_item
            if (!isBxgyBuyItem || bxgyDiscount == null) {
                List<DiscountBxgyItem> getItems = discountBxgyItemRepository.findByGetItemMappingIdsAndMenuId(
                        categoryItemMappingIds, menuId, DiscountType.BXGY, EntityStatus.ACTIVE);
                log.info("Found {} get items for BXGY discount for item {} using CategoryItemMapping IDs", getItems.size(), item.getId());
                
                for (DiscountBxgyItem bxgy : getItems) {
                    // Additional check for validity period and usage limits
                    Discount discount = bxgy.getDiscount();
                    if (discount != null) {
                        log.info("Checking if discount {} is active for menu {} and restaurant {}", discount.getId(), menuId, restaurantId);
                        boolean isActive = isDiscountActive(discount, menuId, restaurantId);
                        log.info("Discount {} isActive: {}", discount.getId(), isActive);
                        if (isActive) {
                            isBxgyGetItem = true;
                            // Only set if not already set from buy item check
                            if (bxgyDiscount == null) {
                                bxgyDiscount = discount;
                                discountDetail = generateBxgyDetail(bxgyDiscount, userLocale);
                                buyQuantity = bxgyDiscount.getBuyQuantity();
                                getQuantity = bxgyDiscount.getGetQuantity();
                                discountId = bxgyDiscount.getId();
                            }
                            log.info("Item {} is a BXGY get item with discount {} using CategoryItemMapping {}", item.getId(), discountId, bxgy.getGetItemMapping().getId());
                            break;
                        }
                    }
                }
            }
        }
        
        // If item is part of BXGY (buy or get item), return base price only (no discountedPrice) and don't apply regular discounts
        // BXGY discounts are exclusive - items in BXGY should not have category/item type discounts applied
        if (isBxgyBuyItem || isBxgyGetItem) {
            return new DiscountInfo(basePrice, null, isBxgyBuyItem, isBxgyGetItem, discountDetail, buyQuantity, getQuantity, discountId);
        }
        
        // For regular discounts, calculate discounted price
        double maxDiscountPercentage = 0.0;
        double maxDiscountAmount = 0.0;
        Discount bestDiscount = null;
        
        // Check category-level discounts - only for categories that contain this item
        for (CategoryItemMapping itemMapping : itemMappings) {
            MenuCategoryMapping mcm = itemMapping.getMenuCategoryMapping();
            
            // Check discounts on the direct category
            List<CategoryDiscountMapping> categoryDiscounts = categoryDiscountMappingRepository.findByMenuCategoryMapping(mcm);
            log.info("Found {} category-level discounts for item {} in category {}", 
                    categoryDiscounts.size(), item.getId(), mcm.getCategory() != null ? mcm.getCategory().getId() : "null");
            
            for (CategoryDiscountMapping cdm : categoryDiscounts) {
                Discount discount = cdm.getDiscount();
                if (discount != null) {
                    boolean isActive = isDiscountActive(discount, menuId, restaurantId);
                    log.info("Category discount {} for item {} - isActive: {}, type: {}", 
                            discount.getId(), item.getId(), isActive, discount.getDiscountType());
                    if (isActive && discount.getDiscountType() != DiscountType.BXGY) {
                        double discountPercentage = calculateDiscountPercentage(discount, basePrice);
                        if (discountPercentage > maxDiscountPercentage) {
                            maxDiscountPercentage = discountPercentage;
                            maxDiscountAmount = calculateDiscountAmount(discount, basePrice);
                            bestDiscount = discount;
                            log.info("New best category discount found: {} with {}% discount", discount.getId(), discountPercentage);
                        }
                    }
                }
            }
            
            // If this category has a parent category, also check for discounts on the parent category
            if (mcm.getCategory() != null && mcm.getCategory().getParentCategory() != null) {
                UUID parentCategoryId = mcm.getCategory().getParentCategory().getId();
                Optional<MenuCategoryMapping> parentMcm = menuCategoryMappingRepository.findByMenuIdAndCategoryId(menuId, parentCategoryId);
                
                if (parentMcm.isPresent()) {
                    List<CategoryDiscountMapping> parentCategoryDiscounts = categoryDiscountMappingRepository.findByMenuCategoryMapping(parentMcm.get());
                    
                    for (CategoryDiscountMapping cdm : parentCategoryDiscounts) {
                        Discount discount = cdm.getDiscount();
                        if (isDiscountActive(discount, menuId, restaurantId) && discount.getDiscountType() != DiscountType.BXGY) {
                            double discountPercentage = calculateDiscountPercentage(discount, basePrice);
                            if (discountPercentage > maxDiscountPercentage) {
                                maxDiscountPercentage = discountPercentage;
                                maxDiscountAmount = calculateDiscountAmount(discount, basePrice);
                                bestDiscount = discount;
                            }
                        }
                    }
                }
            }
        }
        
        // Check item-level discounts - only for items in this menu
        for (CategoryItemMapping itemMapping : itemMappings) {
            List<ItemDiscountMapping> itemDiscounts = itemDiscountMappingRepository.findByCategoryItemMapping(itemMapping);
            log.info("Found {} item-level discounts for item {} using CategoryItemMapping {}", 
                    itemDiscounts.size(), item.getId(), itemMapping.getId());
            
            for (ItemDiscountMapping idm : itemDiscounts) {
                Discount discount = idm.getDiscount();
                if (discount != null) {
                    boolean isActive = isDiscountActive(discount, itemMapping.getMenuCategoryMapping().getMenu().getId(), restaurantId);
                    log.info("Item discount {} for item {} - isActive: {}, type: {}", 
                            discount.getId(), item.getId(), isActive, discount.getDiscountType());
                    if (isActive && discount.getDiscountType() != DiscountType.BXGY) {
                        double discountPercentage = calculateDiscountPercentage(discount, basePrice);
                        // Item-level discounts take priority over category-level when percentages are equal
                        // Use >= to ensure item discount wins on tie (consistent with OrderPricingServiceImpl)
                        if (discountPercentage >= maxDiscountPercentage) {
                            maxDiscountPercentage = discountPercentage;
                            maxDiscountAmount = calculateDiscountAmount(discount, basePrice);
                            bestDiscount = discount;
                            log.info("New best item discount found: {} with {}% discount", discount.getId(), discountPercentage);
                        }
                    }
                }
            }
        }
        
        // Apply the highest discount
        if (maxDiscountAmount > 0 && bestDiscount != null) {
            double discountedPrice = basePrice - maxDiscountAmount;
            discountDetail = generateDiscountDetail(bestDiscount, basePrice, userLocale);
            log.info("Applying discount {} to item {}: basePrice={}, discountedPrice={}, discountDetail={}", 
                    bestDiscount.getId(), item.getId(), basePrice, discountedPrice, discountDetail);
            return new DiscountInfo(basePrice, Math.max(0.0, Math.round(discountedPrice * 100.0) / 100.0), isBxgyBuyItem, isBxgyGetItem, discountDetail, buyQuantity, getQuantity, discountId);
        }
        
        // No discount applied, but still return BXGY status if item is a get item
        log.info("No discount applied to item {}: maxDiscountAmount={}, bestDiscount={}", 
                item.getId(), maxDiscountAmount, bestDiscount != null ? bestDiscount.getId() : "null");
        return new DiscountInfo(basePrice, null, isBxgyBuyItem, isBxgyGetItem, discountDetail, buyQuantity, getQuantity, discountId);
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
     * Generates a human-readable discount detail string.
     * Formats discount as percentage or flat amount based on discount type.
     *
     * @param discount  the discount entity
     * @param basePrice the base price (used for formatting)
     * @param userLocale locale for localized detail strings
     * @return formatted discount detail string (e.g., "20% off" or "Flat $5 off")
     */
    private String generateDiscountDetail(Discount discount, Double basePrice, Locale userLocale) {
        if (discount == null) return null;
        
        if (discount.getDiscountType() == DiscountType.PERCENT) {
            double percentage = discount.getValue() != null ? discount.getValue().doubleValue() : 0.0;
            return messageUtil.getMessage("discount.percent.detail", userLocale, (int) Math.round(percentage));
        } else if (discount.getDiscountType() == DiscountType.FLAT) {
            double flatAmount = discount.getValue() != null ? discount.getValue().doubleValue() : 0.0;
            String currencySymbol = restaurantChainConfigProperties.getChain() != null
                    && restaurantChainConfigProperties.getChain().getCurrency() != null
                    ? restaurantChainConfigProperties.getChain().getCurrency()
                    : "";
            return messageUtil.getMessage("discount.flat.detail", userLocale, currencySymbol, (int) Math.round(flatAmount));
        }
        
        return null;
    }

    private static class DiscountInfo {
        private final Double basePrice;
        private final Double discountedPrice;  
        private final boolean isBxgyBuyItem;
        private final boolean isBxgyGetItem;
        private final String discountDetail;
        private final Integer buyQuantity;
        private final Integer getQuantity;
        private final UUID discountId;
        
        public DiscountInfo(Double basePrice, Double discountedPrice, boolean isBxgyBuyItem, boolean isBxgyGetItem, String discountDetail, Integer buyQuantity, Integer getQuantity, UUID discountId) {
            this.basePrice = basePrice;
            this.discountedPrice = discountedPrice;
            this.isBxgyBuyItem = isBxgyBuyItem;
            this.isBxgyGetItem = isBxgyGetItem;
            this.discountDetail = discountDetail;
            this.buyQuantity = buyQuantity;
            this.getQuantity = getQuantity;
            this.discountId = discountId;
        }
        
        public Double getBasePrice() { return basePrice; }
        public Double getDiscountedPrice() { return discountedPrice; }
        public boolean isBxgyBuyItem() { return isBxgyBuyItem; }
        public boolean isBxgyGetItem() { return isBxgyGetItem; }
        public String getDiscountDetail() { return discountDetail; }
        public Integer getBuyQuantity() { return buyQuantity; }
        public Integer getGetQuantity() { return getQuantity; }
        public UUID getDiscountId() { return discountId; }
    }

    /**
     * Retrieves Buy-X-Get-Y (BXGY) discount details for an item in a menu and restaurant context.
     * Finds applicable BXGY discounts and returns buy quantity, get quantity, and discount information.
     *
     * @param itemId       the UUID of the item
     * @param menuId       the UUID of the menu
     * @param restaurantId the UUID of the restaurant
     * @param locale       locale code for localized responses
     * @return ResponseDto containing BXGY discount details
     * @throws ResponseStatusException if item not found
     */
    @Override
    @Transactional(readOnly = true)
    public ResponseDto<BxgyDiscountDetailsResponse> getBxgyDiscountDetails(UUID itemId, UUID menuId, UUID restaurantId, String locale) {
        Locale userLocale = Locale.forLanguageTag(locale);
        
        // Step 1: Validate item exists
        Item item = itemRepository.findById(itemId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        messageUtil.getMessage(MSG_ITEM_NOT_FOUND, userLocale, itemId)));
        
        // Step 2: Get all MenuCategoryMapping IDs for the specified menu
        List<MenuCategoryMapping> menuCategoryMappings = menuCategoryMappingRepository.findByMenuId(menuId);
        if (menuCategoryMappings.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                    messageUtil.getMessage(MSG_MENU_NOT_FOUND, userLocale, menuId));
        }
        
        List<UUID> menuCategoryMappingIds = menuCategoryMappings.stream()
                .map(MenuCategoryMapping::getId)
                .collect(Collectors.toList());
        
        // Step 3: Find CategoryItemMapping using MenuCategoryMapping ID + Item ID
        List<CategoryItemMapping> itemMappings = new ArrayList<>();
        for (UUID menuCategoryMappingId : menuCategoryMappingIds) {
            CategoryItemMapping mapping = categoryItemMappingRepository
                    .findByMenuCategoryMapping_IdAndItem_Id(menuCategoryMappingId, itemId);
            if (mapping != null) {
                itemMappings.add(mapping);
            }
        }
        
        if (itemMappings.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                    messageUtil.getMessage("item.not.in.menu", userLocale));
        }
        
        // Step 4: Query DiscountBxgyItem with CategoryItemMapping IDs to find BXGY discounts
        Set<UUID> discountIds = new HashSet<>();
        Map<UUID, Discount> discountMap = new HashMap<>();
        
        for (CategoryItemMapping itemMapping : itemMappings) {
            // Check if item is a BUY item
            List<DiscountBxgyItem> buyItems = discountBxgyItemRepository.findByBuyItemMappingId(itemMapping.getId());
            for (DiscountBxgyItem bxgy : buyItems) {
                if (isDiscountActive(bxgy.getDiscount(), menuId, restaurantId)) {
                    discountIds.add(bxgy.getDiscount().getId());
                    discountMap.put(bxgy.getDiscount().getId(), bxgy.getDiscount());
                }
            }
            
            // Check if item is a GET item
            List<DiscountBxgyItem> getItems = discountBxgyItemRepository.findByGetItemMappingId(itemMapping.getId());
            for (DiscountBxgyItem bxgy : getItems) {
                if (isDiscountActive(bxgy.getDiscount(), menuId, restaurantId)) {
                    discountIds.add(bxgy.getDiscount().getId());
                    discountMap.put(bxgy.getDiscount().getId(), bxgy.getDiscount());
                }
            }
        }
        
        if (discountIds.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                    "No BXGY discount found for this item in the specified menu");
        }
        
        // Step 5: Get Discount details (discountId, buyQuantity, getQuantity)
        // For simplicity, get the first active discount (you can modify this logic as needed)
        UUID discountId = discountIds.iterator().next();
        Discount discount = discountMap.get(discountId);
        
        // Step 6: Find all BXGY items for this discount that belong to the same menu
        List<DiscountBxgyItem> allBxgyItems = discountBxgyItemRepository.findByDiscountId(discountId)
                .stream()
                .filter(bxgy -> {
                    // Only include items where the buy or get item mapping belongs to the specified menu
                    if (bxgy.getBuyItemMapping() != null) {
                        UUID buyItemMenuId = bxgy.getBuyItemMapping().getMenuCategoryMapping().getMenu().getId();
                        if (buyItemMenuId.equals(menuId)) {
                            return true;
                        }
                    }
                    if (bxgy.getGetItemMapping() != null) {
                        UUID getItemMenuId = bxgy.getGetItemMapping().getMenuCategoryMapping().getMenu().getId();
                        if (getItemMenuId.equals(menuId)) {
                            return true;
                        }
                    }
                    return false;
                })
                .collect(Collectors.toList());
        
        // Collect all item IDs for batch fetching default modifier items
        Set<UUID> allItemIds = new HashSet<>();
        for (DiscountBxgyItem bxgyItem : allBxgyItems) {
            if (bxgyItem.getBuyItemMapping() != null) {
                allItemIds.add(bxgyItem.getBuyItemMapping().getItem().getId());
            }
            if (bxgyItem.getGetItemMapping() != null) {
                allItemIds.add(bxgyItem.getGetItemMapping().getItem().getId());
            }
        }
        
        // Batch fetch item-modifier group mappings
        Map<UUID, List<ItemModifierGroup>> itemModifierGroupsMap = new HashMap<>();
        if (!allItemIds.isEmpty()) {
            // Since findByItemIdAndIsDeletedFalse takes single UUID, we need to fetch for each item
            for (UUID currentItemId : allItemIds) {
                List<ItemModifierGroup> groups = itemModifierGroupRepository.findByItemIdAndIsDeletedFalse(currentItemId);
                if (!groups.isEmpty()) {
                    itemModifierGroupsMap.put(currentItemId, groups);
                }
            }
        }
        
        // Collect modifier group IDs
        Set<UUID> modifierGroupIds = itemModifierGroupsMap.values().stream()
                .flatMap(List::stream)
                .map(img -> img.getModifierGroup().getId())
                .collect(Collectors.toSet());
        
        // Batch fetch default modifier items for all modifier groups
        Map<UUID, ModifierItem> defaultModifierItemMap = new HashMap<>();
        final Map<UUID, List<ModifierItemTranslation>> modifierItemTranslationsMap;
        if (!modifierGroupIds.isEmpty()) {
            // Get all modifier items for these groups
            List<ModifierItem> allModifierItems = modifierItemRepository.findByModifierGroup_IdInAndIsDeletedFalse(
                    new ArrayList<>(modifierGroupIds));
            
            // Filter for default items (isDefault = true, status = ACTIVE)
            List<ModifierItem> defaultModifierItems = allModifierItems.stream()
                    .filter(mi -> Boolean.TRUE.equals(mi.getIsDefault()) 
                            && mi.getStatus() == EntityStatus.ACTIVE
                            && !Boolean.TRUE.equals(mi.getIsDeleted()))
                    .collect(Collectors.toList());
            
            // Map by modifier group ID (one default per group)
            for (ModifierItem defaultItem : defaultModifierItems) {
                UUID groupId = defaultItem.getModifierGroup().getId();
                if (!defaultModifierItemMap.containsKey(groupId)) {
                    defaultModifierItemMap.put(groupId, defaultItem);
                }
            }
            
            // Batch fetch translations for default modifier items
            if (!defaultModifierItemMap.isEmpty()) {
                List<UUID> defaultModifierItemIds = defaultModifierItemMap.values().stream()
                        .map(ModifierItem::getId)
                        .collect(Collectors.toList());
                
                modifierItemTranslationsMap = modifierItemTranslationRepository
                        .findAllByModifierItem_IdIn(defaultModifierItemIds)
                        .stream()
                        .collect(Collectors.groupingBy(t -> t.getModifierItem().getId()));
            } else {
                modifierItemTranslationsMap = new HashMap<>();
            }
        } else {
            modifierItemTranslationsMap = new HashMap<>();
        }
        
        // Helper method to get default modifier item info for an item
        java.util.function.Function<UUID, java.util.AbstractMap.SimpleEntry<UUID, String>> getDefaultModifierInfo = targetItemId -> {
            List<ItemModifierGroup> itemGroups = itemModifierGroupsMap.get(targetItemId);
            if (itemGroups == null || itemGroups.isEmpty()) {
                return new java.util.AbstractMap.SimpleEntry<>(null, null);
            }
            
            // Find first default modifier item across all modifier groups for this item
            for (ItemModifierGroup itemGroup : itemGroups) {
                UUID groupId = itemGroup.getModifierGroup().getId();
                ModifierItem defaultItem = defaultModifierItemMap.get(groupId);
                if (defaultItem != null) {
                    // Get translation for locale
                    List<ModifierItemTranslation> translations = modifierItemTranslationsMap.get(defaultItem.getId());
                    String modifierName = null;
                    if (translations != null && !translations.isEmpty()) {
                        // Try exact locale match first
                        ModifierItemTranslation exactMatch = translations.stream()
                                .filter(t -> t.getLanguageCode() != null && 
                                        t.getLanguageCode().equalsIgnoreCase(locale))
                                .findFirst()
                                .orElse(null);
                        
                        if (exactMatch != null) {
                            modifierName = exactMatch.getName();
                        } else {
                            // Fallback to first available translation
                            modifierName = translations.get(0).getName();
                        }
                    }
                    return new java.util.AbstractMap.SimpleEntry<>(defaultItem.getId(), modifierName);
                }
            }
            return new java.util.AbstractMap.SimpleEntry<>(null, null);
        };
        
        // Collect buy items
        List<BxgyItemDto> buyItems = new ArrayList<>();
        Set<UUID> processedBuyItems = new HashSet<>();
        
        for (DiscountBxgyItem bxgyItem : allBxgyItems) {
            if (bxgyItem.getBuyItemMapping() != null) {
                CategoryItemMapping buyItemMapping = bxgyItem.getBuyItemMapping();
                Item buyItem = buyItemMapping.getItem();
                
                // Skip if mapping doesn't belong to correct menu, item is deleted, or item is not active
                if (!buyItemMapping.getMenuCategoryMapping().getMenu().getId().equals(menuId) ||
                    (buyItem.getIsDeleted() != null && buyItem.getIsDeleted()) ||
                    (buyItem.getStatus() != null && buyItem.getStatus() != EntityStatus.ACTIVE)) {
                    continue;
                }
                
                if (!processedBuyItems.contains(buyItem.getId())) {
                    processedBuyItems.add(buyItem.getId());
                    
                    // Get translations
                    List<ItemTranslation> translations = itemTranslationRepository.findAllByItemId(buyItem.getId());
                    List<ItemTranslationDto> translationDtos = translations.stream()
                            .map(t -> ItemTranslationDto.builder()
                                    .languageCode(t.getLanguageCode())
                                    .name(t.getName())
                                    .description(t.getDescription())
                                    .build())
                            .collect(Collectors.toList());
                    
                    // Get default modifier item info
                    java.util.AbstractMap.SimpleEntry<UUID, String> defaultModifierInfo = getDefaultModifierInfo.apply(buyItem.getId());
                    
                    // Generate presigned URL for item image
                    String presignedUrl = null;
                    if (buyItem.getImageUrl() != null && !buyItem.getImageUrl().isEmpty()) {
                        presignedUrl = awsService.getPreSignedUrl(buyItem.getImageUrl());
                    }
                    
                    buyItems.add(BxgyItemDto.builder()
                            .itemId(buyItem.getId())
                            .itemName(translations.isEmpty() ? "Item" : translations.get(0).getName())
                            .basePrice(buyItem.getBasePrice() != null ? BigDecimal.valueOf(buyItem.getBasePrice()) : BigDecimal.ZERO)
                            .price(BigDecimal.ZERO)
                            .translations(translationDtos)
                            .defaultModifierItemId(defaultModifierInfo.getKey())
                            .defaultModifierItemName(defaultModifierInfo.getValue())
                            .presignedUrl(presignedUrl)
                            .build());
                }
            }
        }
        
        // Collect get items
        List<BxgyItemDto> getItems = new ArrayList<>();
        Set<UUID> processedGetItems = new HashSet<>();
        
        // Collect categoryItemMappingIds for getItems to batch fetch availability
        Set<UUID> getItemCategoryItemMappingIds = new HashSet<>();
        for (DiscountBxgyItem bxgyItem : allBxgyItems) {
            if (bxgyItem.getGetItemMapping() != null) {
                CategoryItemMapping getItemMapping = bxgyItem.getGetItemMapping();
                // Check if the mapping belongs to the correct menu
                if (!getItemMapping.getMenuCategoryMapping().getMenu().getId().equals(menuId)) {
                    continue;
                }
                if (getItemMapping.getId() != null) {
                    getItemCategoryItemMappingIds.add(getItemMapping.getId());
                }
            }
        }
        
        // Batch fetch availability records for getItems
        Map<UUID, Boolean> categoryItemMappingToAvailabilityMap = new HashMap<>();
        if (restaurantId != null && !getItemCategoryItemMappingIds.isEmpty()) {
            List<RestaurantItemAvailability> availabilityRecords = restaurantItemAvailabilityRepository
                    .findByRestaurantIdAndCategoryItemMappingIdIn(restaurantId, new ArrayList<>(getItemCategoryItemMappingIds));
            
            for (RestaurantItemAvailability availability : availabilityRecords) {
                if (availability.getCategoryItemMapping() != null && availability.getCategoryItemMapping().getId() != null) {
                    categoryItemMappingToAvailabilityMap.put(
                            availability.getCategoryItemMapping().getId(),
                            Optional.ofNullable(availability.getIsAvailable()).orElse(true)
                    );
                }
            }
        }
        
        for (DiscountBxgyItem bxgyItem : allBxgyItems) {
            if (bxgyItem.getGetItemMapping() != null) {
                CategoryItemMapping getItemMapping = bxgyItem.getGetItemMapping();
                Item getItem = getItemMapping.getItem();
                
                // Skip if mapping doesn't belong to correct menu, item is deleted, or item is not active
                if (!getItemMapping.getMenuCategoryMapping().getMenu().getId().equals(menuId) ||
                    (getItem.getIsDeleted() != null && getItem.getIsDeleted()) ||
                    (getItem.getStatus() != null && getItem.getStatus() != EntityStatus.ACTIVE)) {
                    continue;
                }
                
                if (!processedGetItems.contains(getItem.getId())) {
                    processedGetItems.add(getItem.getId());
                    
                    // Get translations
                    List<ItemTranslation> translations = itemTranslationRepository.findAllByItemId(getItem.getId());
                    List<ItemTranslationDto> translationDtos = translations.stream()
                            .map(t -> ItemTranslationDto.builder()
                                    .languageCode(t.getLanguageCode())
                                    .name(t.getName())
                                    .description(t.getDescription())
                                    .build())
                            .collect(Collectors.toList());
                    
                    // Get default modifier item info
                    java.util.AbstractMap.SimpleEntry<UUID, String> defaultModifierInfo = getDefaultModifierInfo.apply(getItem.getId());
                    
                    // Generate presigned URL for item image
                    String presignedUrl = null;
                    if (getItem.getImageUrl() != null && !getItem.getImageUrl().isEmpty()) {
                        presignedUrl = awsService.getPreSignedUrl(getItem.getImageUrl());
                    }
                    
                    // Get availability status from restaurant item availability
                    Boolean isAvailable = null;
                    if (restaurantId != null && getItemMapping.getId() != null) {
                        isAvailable = categoryItemMappingToAvailabilityMap.getOrDefault(getItemMapping.getId(), true);
                    }
                    
                    getItems.add(BxgyItemDto.builder()
                            .itemId(getItem.getId())
                            .itemName(translations.isEmpty() ? "Item" : translations.get(0).getName())
                            .basePrice(getItem.getBasePrice() != null ? BigDecimal.valueOf(getItem.getBasePrice()) : BigDecimal.ZERO)
                            .price(BigDecimal.ZERO)
                            .translations(translationDtos)
                            .defaultModifierItemId(defaultModifierInfo.getKey())
                            .defaultModifierItemName(defaultModifierInfo.getValue())
                            .presignedUrl(presignedUrl)
                            .isAvailable(isAvailable)
                            .build());
                }
            }
        }
        
        // Build response
        BxgyDiscountDetailsResponse response = BxgyDiscountDetailsResponse.builder()
                .discountId(discount.getId())
                .discountName("BXGY Discount") // Default name since Discount entity doesn't have getName()
                .buyQuantity(discount.getBuyQuantity() != null ? discount.getBuyQuantity() : 1)
                .getQuantity(discount.getGetQuantity() != null ? discount.getGetQuantity() : 1)
                .buyItems(buyItems)
                .getItems(getItems)
                .build();
        
        return ResponseDto.<BxgyDiscountDetailsResponse>builder()
                .message("BXGY discount details fetched successfully")
                .data(response)
                .build();
    }

    /**
     * Restores one or more soft-deleted menus by setting isDeleted flag to false.
     * Only restores menus that are currently deleted. Updates updatedBy and updatedAt fields.
     *
     * @param ids    list of menu UUIDs to restore
     * @param userId the ID of the user performing the restore
     * @param locale locale code for localized error messages
     * @return ResponseDto with success message
     * @throws ResponseStatusException if user not found, menus not found, or no deleted menus to restore
     */
    @Override
    @Transactional
    public ResponseDto<Void> restoreMenus(List<UUID> ids, String userId, String locale) {
        Locale userLocale = Locale.forLanguageTag(locale);
        
        // Find user for updatedBy
        User user = userRepository.findById(UUID.fromString(userId))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        messageUtil.getMessage(MSG_USER_NOT_FOUND, userLocale)));
        
        // Find all menus by IDs
        List<Menu> menus = menuRepository.findAllById(ids);
        
        if (menus.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                    messageUtil.getMessage(MSG_MENU_NOT_FOUND, userLocale));
        }
        
        // Filter only deleted menus and restore them
        List<Menu> deletedMenus = menus.stream()
                .filter(m -> Boolean.TRUE.equals(m.getIsDeleted()))
                .collect(Collectors.toList());
        
        if (deletedMenus.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    messageUtil.getMessage("menu.restore.error.not.deleted", userLocale));
        }
        
        // Restore all deleted menus
        for (Menu menu : deletedMenus) {
            menu.setIsDeleted(false);
            menu.setUpdatedAt(OffsetDateTime.now(ZoneOffset.UTC));
            menu.setUpdatedBy(user);
        }
        
        menuRepository.saveAll(deletedMenus);
        
        return ResponseDto.<Void>builder()
            .message(messageUtil.getMessage("menu.restore.success", userLocale))
            .build();
    }

}




