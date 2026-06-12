package com.gulfnet.restaurantmanagement.service.impl;

import com.gulfnet.restaurantmanagement.service.RestaurantService;
import com.gulfnet.restaurantmanagement.service.AuditTrailService;
import com.gulfnet.restaurantmanagement.service.UnusedSessionExpiryScheduleService;
import org.quartz.SchedulerException;
import com.gulfnet.restaurantmanagement.config.RestaurantChainConfigProperties;
import com.gulfnet.shared_library.config.AWSService;
import com.gulfnet.shared_library.entity.*;
import com.gulfnet.shared_library.entity.AuditTrail;
import com.gulfnet.shared_library.enums.EntityStatus;
import com.gulfnet.shared_library.enums.ActionType;
import com.gulfnet.shared_library.enums.RequestStatus;
import com.gulfnet.shared_library.enums.RestaurantMenuMappingStatus;
import com.gulfnet.shared_library.enums.TransactionStatus;
import com.gulfnet.shared_library.enums.ItemStatus;
import com.gulfnet.shared_library.mapper.RestaurantOperatingHoursMapper;
import com.gulfnet.shared_library.model.request.AssignEmployeesRequest;
import com.gulfnet.shared_library.model.request.RestaurantOperatingHoursRequest;
import com.gulfnet.shared_library.model.request.RestaurantRequest;
import com.gulfnet.shared_library.model.request.UpdateRestaurantAccountSettingsRequest;
import com.gulfnet.shared_library.model.response.dto.*;
import com.gulfnet.shared_library.repository.*;
import com.gulfnet.shared_library.repository.OperatingHourSlotRepository;
import com.gulfnet.shared_library.util.LocaleSortUtil;
import com.gulfnet.shared_library.util.TranslationUtils;
import com.gulfnet.restaurantmanagement.config.LocalizationProperties;
import com.gulfnet.restaurantmanagement.config.AppProperties;
import com.gulfnet.restaurantmanagement.util.MessageUtil;
import com.gulfnet.shared_library.enums.TableShape;
import com.gulfnet.shared_library.enums.TableStatus;
import com.gulfnet.shared_library.enums.QrCodeType;
import com.gulfnet.shared_library.util.EmailSender;
import com.gulfnet.shared_library.util.PasswordGeneratorUtil;
import com.gulfnet.shared_library.repository.LoginAuditRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import jakarta.persistence.EntityNotFoundException;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.OffsetTime;
import java.time.ZoneOffset;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.Objects;


@Service
public class RestaurantServiceImpl implements RestaurantService {

    private static final Logger logger = LoggerFactory.getLogger(RestaurantServiceImpl.class);

    // Role names
    private static final String ROLE_MANAGER = "MANAGER";
    private static final String ROLE_WAITER = "WAITER";

    // Entity types
    private static final String ENTITY_TYPE_RESTAURANT = "RESTAURANT";

    // Error messages
    private static final String ERROR_USER_NOT_FOUND_WITH_ID = "User not found with id: ";
    private static final String ERROR_RESTAURANT_NOT_FOUND_WITH_ID = "Restaurant not found with id: ";
    private static final String ERROR_RESTAURANT_GROUP_NOT_FOUND = "RestaurantGroup not found";

    // Message keys
    private static final String MSG_RESTAURANT_NOT_FOUND = "restaurant.not.found";
    private static final String MSG_USER_NOT_FOUND = "user.not.found";
    private static final String MSG_MENU_NOT_FOUND = "menu.not.found";
    private static final String MSG_RESTAURANT_CREATE_SUCCESS = "restaurant.create.success";
    private static final String MSG_RESTAURANT_GET_SUCCESS = "restaurant.get.success";
    private static final String MSG_RESTAURANT_LIST_SUCCESS = "restaurant.list.success";
    private static final String MSG_RESTAURANT_UPDATE_SUCCESS = "restaurant.update.success";
    private static final String MSG_RESTAURANT_DELETE_SUCCESS = "restaurant.delete.success";
    private static final String MSG_RESTAURANT_CREATE_ERROR_CODE_EXISTS = "restaurant.create.error.code.exists";
    private static final String MSG_RESTAURANT_CREATE_ERROR_GST_NUMBER_EXISTS = "restaurant.create.error.gst.number.exists";
    private static final String MSG_RESTAURANT_CREATE_ERROR_GROUP_DELETED = "restaurant.create.error.group.deleted";
    private static final String MSG_RESTAURANT_UPDATE_ERROR_NOT_FOUND = "restaurant.get.error.notfound";
    private static final String MSG_RESTAURANT_UPDATE_ERROR_DELETED = "restaurant.update.error.deleted";
    private static final String MSG_RESTAURANT_UPDATE_ERROR_CODE_EXISTS = "restaurant.update.error.code.exists";
    private static final String MSG_RESTAURANT_UPDATE_ERROR_GST_NUMBER_EXISTS = "restaurant.update.error.gst.number.exists";
    private static final String MSG_RESTAURANT_UPDATE_ERROR_GROUP_DELETED = "restaurant.update.error.group.deleted";
    private static final String MSG_RESTAURANT_UPDATE_ERROR_NAME_EXISTS = "restaurant.update.error.name.exists";
    private static final String MSG_RESTAURANT_DELETE_ERROR_NOT_FOUND = "restaurant.delete.error.notfound";
    private static final String MSG_RESTAURANT_DELETE_ERROR_ALREADY_DELETED = "restaurant.delete.error.alreadydeleted";
    private static final String MSG_CATEGORY_ACTIVE_LIST_SUCCESS = "category.active.list.success";
    private static final String MSG_VIRTUAL_SECTION_NAME = "virtual.section.name";
    private static final String MSG_VIRTUAL_TABLE_CODE_TAKEAWAY = "virtual.table.code.takeaway";
    private static final String MSG_EMAIL_ASSIGNMENT_MANAGER_SUBJECT = "email.assignment.manager.subject";
    private static final String MSG_EMAIL_ASSIGNMENT_MANAGER_TITLE = "email.assignment.manager.title";
    private static final String MSG_EMAIL_ASSIGNMENT_MANAGER_INTRO = "email.assignment.manager.intro";
    private static final String MSG_WAITER_RESTAURANT_ASSIGNMENT_EMAIL_SUBJECT = "waiter.restaurant.assignment.email.subject";
    private static final String MSG_EMAIL_RECEIPT_REGARDS = "email.receipt.regards";
    private static final String MSG_USER_REGISTRATION_EMAIL_TEAM = "user.registration.email.team";

    private static final class AssignmentEmailHtml {
        static final String TD_CLOSE = "</td>";
        static final String TR_CLOSE = "</tr>";
        static final String TABLE_CLOSE = "</table>";
        static final String DIV_CLOSE = "</div>";
        static final String TABLE_PRESENTATION_BORDER_COLLAPSE =
                "<table role=\"presentation\" width=\"100%\" cellpadding=\"0\" cellspacing=\"0\" style=\"border-collapse:collapse;\">";
        static final String TD_TR_TABLE_CLOSE = "</td></tr></table>";
        static final String TD_VALUE_ALIGN_RIGHT_13 =
                "<td align=\"right\" style=\"padding:4px 0;color:#111827;font-size:13px;font-weight:700;\">";

        private AssignmentEmailHtml() {
        }
    }

    // Audit trail messages
    private static final String AUDIT_MSG_RESTAURANT_CREATED = "Restaurant created: ";
    private static final String AUDIT_MSG_RESTAURANT_UPDATED = "Restaurant updated: ";
    private static final String AUDIT_MSG_RESTAURANT_DELETED = "Restaurant deleted: ";

    @Autowired
    private RestaurantRepository restaurantRepository;

    @Autowired
    private RestaurantTranslationRepository restaurantTranslationRepository;

    @Autowired
    private LanguageRepository languageRepository;

    @Autowired
    private RestaurantGroupRepository restaurantGroupRepository;

    @Autowired
    private RestaurantOperatingHoursMapper restaurantOperatingHoursMapper;

    @Autowired
    private RestaurantOperatingHoursRepository restaurantOperatingHoursRepository;

    @Autowired
    private OperatingHourSlotRepository operatingHourSlotRepository;

    @Autowired
    private UnusedSessionExpiryScheduleService unusedSessionExpiryScheduleService;

    @Autowired
    private RestaurantGroupTranslationRepository restaurantGroupTranslationRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private MessageSource messageSource;

    @Autowired
    private AWSService awsService;

    @Autowired
    private RestaurantMenuMappingRepository restaurantMenuMappingRepository;

    @Autowired
    private MenuCategoryMappingRepository menuCategoryMappingRepository;

    @Autowired
    private CategoryTranslationRepository categoryTranslationRepository;

    @Autowired
    private MenuTranslationRepository menuTranslationRepository;

    @Autowired
    private MenuDiscountMappingRepository menuDiscountMappingRepository;

    @Autowired
    private MenuPromotionMappingRepository menuPromotionMappingRepository;

    @Autowired
    private RestaurantDiscountMappingRepository restaurantDiscountMappingRepository;

    @Autowired
    private RestaurantPromotionMappingRepository restaurantPromotionMappingRepository;

    @Autowired
    private MenuRepository menuRepository;

    @Autowired
    private RestaurantGroupMenuMappingRepository restaurantGroupMenuMappingRepository;

    @Autowired
    private LocalizationProperties localizationProperties;

    @Autowired
    private RestaurantTableRepository restaurantTableRepository;

    @Autowired
    private TransactionRepository transactionRepository;

    @Autowired
    private OrderedItemRepository orderedItemRepository;

    @Autowired
    private OrderedComboRepository orderedComboRepository;

    @Autowired
    private CategoryItemMappingRepository categoryItemMappingRepository;

    @Autowired
    private RestaurantChainConfigProperties restaurantChainConfigProperties;

    @Autowired
    private AuditTrailService auditTrailService;

    @Autowired
    private RestaurantLayoutRepository restaurantLayoutRepository;

    @Autowired
    private RestaurantSectionTranslationRepository restaurantSectionTranslationRepository;

    @Autowired
    private AppProperties appProperties;

    @Autowired
    private RestaurantLayoutServiceImpl restaurantLayoutServiceImpl;

    @Autowired(required = false)
    private com.gulfnet.restaurantmanagement.service.NotificationService notificationService;

    @Autowired(required = false)
    private com.gulfnet.shared_library.repository.RoleRepository roleRepository;

    @Autowired(required = false)
    private EmailSender emailSender;

    @Autowired(required = false)
    private PasswordEncoder passwordEncoder;

    @Autowired(required = false)
    private LoginAuditRepository loginAuditRepository;

    @Autowired(required = false)
    private MessageUtil messageUtil;

    @Autowired
    @Qualifier("employeeAssignmentTaskExecutor")
    private Executor employeeAssignmentTaskExecutor;


    /**
     * Creates a new restaurant with translations, operating hours, and group assignment.
     * Validates restaurant code and GST number uniqueness. Creates virtual section for the restaurant.
     * Sets reset times from configuration and creates audit trail.
     *
     * @param userId the ID of the user creating the restaurant
     * @param dto    the restaurant creation request with all restaurant details
     * @return ResponseDto containing the created restaurant response
     * @throws ResponseStatusException if restaurant code exists, GST number exists, or validation fails
     */
    @Override
    @Transactional
    @CacheEvict(value = {"restaurants", "restaurantGroupsLite"}, allEntries = true)
    public ResponseDto<RestaurantDto<RestaurantResponse>> saveRestaurant(String userId, RestaurantRequest dto) {
        Locale userLocale = LocaleContextHolder.getLocale();
        
        // Check if restaurant code already exists (including deleted ones)
        if (restaurantRepository.existsByRestaurantCode(dto.getRestaurantCode())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, 
                    messageSource.getMessage(MSG_RESTAURANT_CREATE_ERROR_CODE_EXISTS, new Object[]{dto.getRestaurantCode()}, userLocale));
        }
        
        // Check if GST number already exists (only if provided)
        if (dto.getGstNumber() != null && !dto.getGstNumber().trim().isEmpty() 
            && restaurantRepository.existsByGstNumberAndIsDeletedFalse(dto.getGstNumber().trim(), null)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, 
                messageSource.getMessage(MSG_RESTAURANT_CREATE_ERROR_GST_NUMBER_EXISTS, null, userLocale));
        }
        
        // 1. Save base restaurant entity
        Restaurant restaurant = new Restaurant();
        logger.debug("Setting restaurantCode: {}", dto.getRestaurantCode());
        restaurant.setRestaurantCode(dto.getRestaurantCode());
        restaurant.setCity(dto.getCity());
        restaurant.setArea(dto.getArea());
        restaurant.setState(dto.getState());
        restaurant.setAddress1(dto.getAddress1());
        restaurant.setAddress2(dto.getAddress2());
        restaurant.setLatitude(dto.getLatitude());
        restaurant.setLongitude(dto.getLongitude());
        restaurant.setLocationPin(dto.getLocationPin());
        restaurant.setTableQrCodeType(dto.getTableQrCodeType());
        restaurant.setPaymentQrUrl(awsService.stripToKey(dto.getPaymentQrUrl()));
        restaurant.setStatus(dto.getStatus());
        restaurant.setIsDeleted(Optional.ofNullable(dto.getIsDeleted()).orElse(false));
        restaurant.setLogoUrl(awsService.stripToKey(dto.getLogoUrl()));
        restaurant.setCreatedAt(OffsetDateTime.now(ZoneOffset.UTC));
        restaurant.setRestaurantGroupName(dto.getRestaurantGroupName());
        // Trim GST number if provided
        restaurant.setGstNumber(dto.getGstNumber() != null ? dto.getGstNumber().trim() : null);
        restaurant.setPhoneNumber(normalizeOptionalString(dto.getPhoneNumber()));
        
        // Set alert configuration fields
        restaurant.setSalesAlertThreshold(dto.getSalesAlertThreshold());
        restaurant.setRefundAlertPercentage(dto.getRefundAlertPercentage());
        restaurant.setCancellationAlertPercentage(dto.getCancellationAlertPercentage());
        // Only set alertsEnabled if explicitly provided; leave null to inherit from group/account level
        restaurant.setAlertsEnabled(dto.getAlertsEnabled());

        // Set reset times from application.properties (chain-level defaults)
        logger.info("About to set reset times from config for restaurant: {}", restaurant.getRestaurantCode());
        setResetTimesFromConfig(restaurant);
        logger.info("Completed setting reset times from config for restaurant: {}. KDS: {}, Cashier: {}", 
                restaurant.getRestaurantCode(), 
                restaurant.getKdsLiveDashboardResetTime(), 
                restaurant.getCashierLiveDashboardResetTime());

        // Find user first
        User user = userRepository.findById(UUID.fromString(userId))
                .orElseThrow(() -> new EntityNotFoundException(ERROR_USER_NOT_FOUND_WITH_ID + userId));
        restaurant.setCreatedBy(user);
        // Don't set updatedBy during creation - it should be null initially

        // Link with restaurant group
        if (dto.getRestaurantGroupId() != null) {
            UUID groupId = dto.getRestaurantGroupId();
            RestaurantGroup group = restaurantGroupRepository.findById(groupId)
                .orElseThrow(() -> new EntityNotFoundException(ERROR_RESTAURANT_GROUP_NOT_FOUND));
            
            // Check if the restaurant group is deleted
            if (group.getIsDeleted() != null && group.getIsDeleted()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, 
                    messageSource.getMessage(MSG_RESTAURANT_CREATE_ERROR_GROUP_DELETED, null, userLocale));
            }
            
            restaurant.setRestaurantGroup(group);
        }

        // Log the reset times right before saving to database
        logger.info("Before saving to database - Restaurant: {}, KDS Reset Time: {}, Cashier Reset Time: {}", 
                restaurant.getRestaurantCode(), 
                restaurant.getKdsLiveDashboardResetTime(), 
                restaurant.getCashierLiveDashboardResetTime());

        restaurant = restaurantRepository.save(restaurant);
        
        // Log the reset times right after saving to database
        logger.info("After saving to database - Restaurant: {}, KDS Reset Time: {}, Cashier Reset Time: {}", 
                restaurant.getRestaurantCode(), 
                restaurant.getKdsLiveDashboardResetTime(), 
                restaurant.getCashierLiveDashboardResetTime());
        logger.debug("Saved restaurant with restaurantCode: {}", restaurant.getRestaurantCode());
        
        // Refresh the entity from database to ensure we have the latest data
        restaurantRepository.flush();
        restaurant = restaurantRepository.findById(restaurant.getId()).orElse(restaurant);
        logger.debug("After refresh - restaurantCode: {}", restaurant.getRestaurantCode());

        // 2. Save operating hours if present
        if (dto.getOperatingHours() != null && !dto.getOperatingHours().isEmpty()) {
            for (RestaurantOperatingHoursRequest hoursRequest : dto.getOperatingHours()) {
                hoursRequest.setRestaurantId(restaurant.getId());
                List<RestaurantOperatingHours> entities = restaurantOperatingHoursMapper.toEntities(hoursRequest);
                // Set the createdByUser for each entity
                entities.forEach(entity -> entity.setCreatedByUser(user));
                restaurantOperatingHoursRepository.saveAll(entities);
            }
            rescheduleUnusedSessionExpiryJobs(restaurant.getId());
        }

        // 3. Save translations (skip empty/null names)
        List<RestaurantTranslationDto> translations = dto.getTranslations();
        if (translations != null && !translations.isEmpty()) {
            for (RestaurantTranslationDto entry : translations) {
                // Skip translations with empty or null names
                if (entry.getName() != null && !entry.getName().trim().isEmpty()) {
                    RestaurantTranslation translation = new RestaurantTranslation();
                    translation.setName(entry.getName());
                    translation.setLanguageCode(entry.getLanguageCode());
                    translation.setRestaurant(restaurant);
                    restaurantTranslationRepository.save(translation);
                }
            }
        }

        // 4. Prepare response
        List<RestaurantTranslation> savedTranslations = restaurantTranslationRepository.findAllByRestaurantIdWithLanguage(restaurant.getId());
        List<RestaurantTranslationDto> translationDTOs = new ArrayList<>();
        for (RestaurantTranslation t : savedTranslations) {
            if (t.getLanguageCode() != null) {
                RestaurantTranslationDto dtoEntry = new RestaurantTranslationDto();
                dtoEntry.setLanguageCode(t.getLanguageCode());
                dtoEntry.setName(t.getName());
                translationDTOs.add(dtoEntry);
            }
        }

        // 5. Fetch operating hours for this restaurant (with slots)
        List<OperatingHourDto> operatingHours = restaurantOperatingHoursRepository
            .findByRestaurant_Id(restaurant.getId())
            .stream()
            .map(restaurantOperatingHoursMapper::toOperatingHoursDto)
            .collect(Collectors.toList());

        // Fetch group translations if restaurant has a group
        List<RestaurantGroupTranslationDTO> groupTranslations = null;
        if (restaurant.getRestaurantGroup() != null) {
            List<com.gulfnet.shared_library.entity.RestaurantGroupTranslation> groupTranslationEntities =
                restaurantGroupTranslationRepository.findAllByRestaurantGroupIdWithLanguage(restaurant.getRestaurantGroup().getId());
            groupTranslations = groupTranslationEntities.stream()
                .map(gt -> com.gulfnet.shared_library.model.response.dto.RestaurantGroupTranslationDTO.builder()
                    .languageCode(gt.getLanguageCode())
                    .name(gt.getName())
                    .build())
                .collect(java.util.stream.Collectors.toList());
        }

        RestaurantResponse response = buildRestaurantResponse(restaurant, translationDTOs, operatingHours, groupTranslations);

        RestaurantDto<RestaurantResponse> restaurantDTO = RestaurantDto.<RestaurantResponse>builder()
            .restaurant(response)
            .build();

        // Create audit trail for restaurant creation
        // IMPORTANT: For RESTAURANT_CREATE, the audit trail is created with restaurant_id=null initially
        // to avoid FK constraint violation (restaurant isn't committed yet in parent transaction).
        // We'll update the restaurant_id after the parent transaction commits.
        try {
            if (user != null && restaurant != null && restaurant.getId() != null) {
                AuditTrail auditTrail = auditTrailService.createAuditTrail(
                        user,
                        ActionType.RESTAURANT_CREATE,
                        restaurant,
                        RequestStatus.NA, // Explicitly set to NA for non-request actions
                        null, // ipAddress - not available in this context
                        null, // userAgent - not available in this context
                        restaurant.getId(),
                        ENTITY_TYPE_RESTAURANT,
                        AUDIT_MSG_RESTAURANT_CREATED + restaurant.getRestaurantCode()
                );
                if (auditTrail != null) {
                    updateAuditTrailRestaurantIdAfterCommit(restaurant.getId(), restaurant.getId());
                }
            } else {
                logger.warn("Cannot create audit trail for restaurant creation: user={}, restaurant={}, restaurantId={}", 
                        user != null ? user.getId() : "null", 
                        restaurant != null ? "not null" : "null",
                        restaurant != null ? restaurant.getId() : "null");
            }
        } catch (Exception e) {
            logger.error("Failed to create audit trail for restaurant creation: {}", e.getMessage(), e);
            // Don't break restaurant creation flow if audit trail fails
        }

        // 6. Create virtual section with rows and tables
        try {
            createVirtualSectionForRestaurant(restaurant, user);
        } catch (Exception e) {
            logger.error("Failed to create virtual section for restaurant {}: {}", restaurant.getId(), e.getMessage(), e);
            // Don't break restaurant creation flow if virtual section creation fails
        }

        return ResponseDto.<RestaurantDto<RestaurantResponse>>builder()
            .message(messageSource.getMessage(MSG_RESTAURANT_CREATE_SUCCESS, null, LocaleContextHolder.getLocale()))
            .data(restaurantDTO)
            .build();
    }

    /**
     * Retrieves a single restaurant by ID with all details including translations and operating hours.
     * Optionally includes deleted restaurants if includeDeleted is true.
     *
     * @param restaurantId the UUID of the restaurant to retrieve
     * @param userId       the ID of the user requesting (for access control)
     * @param includeDeleted whether to include deleted restaurants
     * @return ResponseDto containing the restaurant details
     * @throws EntityNotFoundException if restaurant not found
     */
    @Override
    @Transactional(readOnly = true)
    public ResponseDto<RestaurantDto<RestaurantResponse>> getRestaurantById(UUID restaurantId, String userId, Boolean includeDeleted) {
        // 1. Fetch base restaurant entity
        Restaurant restaurant;
        if (Boolean.TRUE.equals(includeDeleted)) {
            // Include deleted restaurants
            restaurant = restaurantRepository.findById(restaurantId)
                .orElseThrow(() -> new EntityNotFoundException(ERROR_RESTAURANT_NOT_FOUND_WITH_ID + restaurantId));
        } else {
            // Exclude deleted restaurants (default behavior)
            restaurant = restaurantRepository.findByIdAndIsDeletedFalse(restaurantId)
                .orElseThrow(() -> new EntityNotFoundException(ERROR_RESTAURANT_NOT_FOUND_WITH_ID + restaurantId));
        }

        // Fetch operating hours for this restaurant
        List<OperatingHourDto> operatingHours = restaurantOperatingHoursRepository
            .findByRestaurant_Id(restaurant.getId())
            .stream()
            .map(restaurantOperatingHoursMapper::toOperatingHoursDto)
            .collect(Collectors.toList());

        // 2. Fetch translations with associated language
        List<RestaurantTranslation> translations = restaurantTranslationRepository.findAllByRestaurantIdWithLanguage(restaurantId);
        List<RestaurantTranslationDto> translationDTOs = translations.stream()
            .filter(t -> t.getLanguageCode() != null)
            .map(t -> RestaurantTranslationDto.builder()
                .languageCode(t.getLanguageCode())
                .name(t.getName())
                .build())
            .collect(Collectors.toList());

        // Fetch group translations if restaurant has a group
        List<RestaurantGroupTranslationDTO> groupTranslations = null;
        if (restaurant.getRestaurantGroup() != null) {
            List<com.gulfnet.shared_library.entity.RestaurantGroupTranslation> groupTranslationEntities =
                restaurantGroupTranslationRepository.findAllByRestaurantGroupIdWithLanguage(restaurant.getRestaurantGroup().getId());
            groupTranslations = groupTranslationEntities.stream()
                .map(gt -> com.gulfnet.shared_library.model.response.dto.RestaurantGroupTranslationDTO.builder()
                    .languageCode(gt.getLanguageCode())
                    .name(gt.getName())
                    .build())
                .collect(java.util.stream.Collectors.toList());
        }

        String signedLogoUrl = null;
        if (restaurant.getLogoUrl() != null && !restaurant.getLogoUrl().isEmpty()) {
            signedLogoUrl = awsService.getPreSignedUrl(restaurant.getLogoUrl());
        }

        String signedPaymentQrUrl = null;
        if (restaurant.getPaymentQrUrl() != null && !restaurant.getPaymentQrUrl().isEmpty()) {
            signedPaymentQrUrl = awsService.getPreSignedUrl(restaurant.getPaymentQrUrl());
        }

        // Extract nested ternary operations for createdBy and updatedBy
        String createdByFullName = null;
        if (restaurant.getCreatedBy() != null) {
            String firstName = restaurant.getCreatedBy().getFirstName() != null ? restaurant.getCreatedBy().getFirstName() : "";
            String lastName = restaurant.getCreatedBy().getLastName() != null ? restaurant.getCreatedBy().getLastName() : "";
            createdByFullName = firstName + " " + lastName;
        }

        String updatedByFullName = null;
        if (restaurant.getUpdatedBy() != null) {
            String firstName = restaurant.getUpdatedBy().getFirstName() != null ? restaurant.getUpdatedBy().getFirstName() : "";
            String lastName = restaurant.getUpdatedBy().getLastName() != null ? restaurant.getUpdatedBy().getLastName() : "";
            updatedByFullName = firstName + " " + lastName;
        }

        // 3. Build RestaurantResponse
        RestaurantResponse response = RestaurantResponse.builder()
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
            .countryName(getCountryNameFromChainConfig())
            .paymentQrUrl(signedPaymentQrUrl)
            .tableQrCodeType(restaurant.getTableQrCodeType())
            .status(restaurant.getStatus())
            .restaurantGroupId(
                restaurant.getRestaurantGroup() != null ? restaurant.getRestaurantGroup().getId().toString() : null
            )
            .logoUrl(signedLogoUrl)
            .translations(translationDTOs)
            .createdAt(restaurant.getCreatedAt() != null ? restaurant.getCreatedAt().toLocalDateTime() : null)
            .createdBy(createdByFullName)
            .updatedAt(restaurant.getUpdatedAt() != null ? restaurant.getUpdatedAt().toLocalDateTime() : null)
            .updatedBy(updatedByFullName)
            .isDeleted(restaurant.getIsDeleted())
            // Add new fields to response
            .restaurantGroupName(restaurant.getRestaurantGroupName())
            .employeeCount(userRepository.countByRestaurantIdAndIsDeletedFalse(restaurant.getId()))
            .seatingCapacity(restaurantTableRepository.getTotalSeatingCapacityByRestaurantId(restaurant.getId()))
            .operatingHours(operatingHours)
            .restaurantGroupNames(groupTranslations)
            .gstNumber(restaurant.getGstNumber())
            .salesAlertThreshold(restaurant.getSalesAlertThreshold())
            .refundAlertPercentage(restaurant.getRefundAlertPercentage())
            .cancellationAlertPercentage(restaurant.getCancellationAlertPercentage())
            .alertsEnabled(restaurant.getAlertsEnabled())
            .phoneNumber(restaurant.getPhoneNumber())
            .build();

        // 4. Wrap in RestaurantDto and ResponseDto
        RestaurantDto<RestaurantResponse> restaurantDTO = RestaurantDto.<RestaurantResponse>builder()
            .restaurant(response)
            .build();

        return ResponseDto.<RestaurantDto<RestaurantResponse>>builder()
            .message(messageSource.getMessage(MSG_RESTAURANT_GET_SUCCESS, null, LocaleContextHolder.getLocale()))
            .data(restaurantDTO)
            .build();
    }



    /**
     * Retrieves a paginated and filterable list of restaurants.
     * Supports filtering by restaurant group, restaurant ID, status, menu assignment, deletion status, and search.
     * Managers can only see their assigned restaurant. Results are sorted and paginated with locale-aware name sorting.
     * Results are cached when search is empty.
     *
     * @param page            page number for pagination
     * @param size            page size for pagination
     * @param restaurantGroupId optional filter by restaurant group ID
     * @param restaurantId    optional filter by specific restaurant ID
     * @param status          optional filter by entity status
     * @param search          optional search term for restaurant name
     * @param hasMenuAssigned optional filter by menu assignment status
     * @param sortBy          field to sort by
     * @param direction       sort direction
     * @param locale          locale code for localized responses and sorting
     * @param userId          user ID for access control (managers see only their restaurant)
     * @param userRole        user role for access control
     * @param isDeleted       optional filter by deletion status (true shows deleted, false shows non-deleted)
     * @return ResponseDto containing paginated list of restaurants
     */
        @Override
        @Transactional(readOnly = true)
        @Cacheable(value = "restaurants", key = "'v2_' + #page + '_' + #size + '_' + (#restaurantGroupId != null ? #restaurantGroupId : 'null') + '_' + (#restaurantId != null ? #restaurantId : 'null') + '_' + (#status != null ? #status : 'null') + '_' + (#search != null ? #search.toLowerCase() : 'null') + '_' + (#hasMenuAssigned != null ? #hasMenuAssigned : 'null') + '_' + (#sortBy != null ? #sortBy : 'null') + '_' + (#direction != null ? #direction : 'null') + '_' + #locale + '_' + (#userId != null ? #userId : 'null') + '_' + (#userRole != null ? #userRole : 'null') + '_' + (#isDeleted != null ? #isDeleted : 'null')", condition = "#search == null || #search.trim().isEmpty()")
        public ResponseDto<RestaurantListResponse> getRestaurants(
                Integer page,
                Integer size,
                UUID restaurantGroupId,
                UUID restaurantId,
                String status,
                String search,
                Boolean hasMenuAssigned,
                String sortBy,
                Sort.Direction direction,
                String locale,
                String userId,
                String userRole,
                Boolean isDeleted) {
    
            long startTime = System.currentTimeMillis();
            Locale userLocale = Locale.forLanguageTag(locale);
            int pageNumber = Math.max((page != null ? page : 1) - 1, 0);
            int pageSize = (size != null && size > 0) ? size : Integer.MAX_VALUE;
            
            Specification<Restaurant> spec = buildRestaurantSpecification(restaurantGroupId, restaurantId, status, search, hasMenuAssigned, userId, userRole, isDeleted);
            List<Restaurant> allRestaurants = restaurantRepository.findAll(spec);
            int actualTotal = allRestaurants.size();
            
            if (allRestaurants.isEmpty()) {
                return buildEmptyResponse(userLocale, pageNumber, pageSize);
            }
            
            RestaurantBatchData batchData = loadRestaurantBatchData(allRestaurants);
            List<RestaurantResponse> allRestaurantResponses = mapRestaurantsToResponses(allRestaurants, batchData, locale);
            applySorting(allRestaurantResponses, sortBy, direction, userLocale);
            RestaurantListResponse listResponse = buildPaginatedResponse(allRestaurantResponses, pageNumber, pageSize, actualTotal);
            
            logger.info("Total getRestaurants method execution time: {}ms", System.currentTimeMillis() - startTime);
            
            return ResponseDto.<RestaurantListResponse>builder()
                    .message(messageSource.getMessage(MSG_RESTAURANT_LIST_SUCCESS, null, userLocale))
                    .data(listResponse)
                    .build();
        }

    /**
     * Updates an existing restaurant with new details, translations, and operating hours.
     * Validates restaurant code and GST number uniqueness (excluding current restaurant).
     * Updates restaurant entity, translations, operating hours, and group assignment.
     *
     * @param id     the UUID of the restaurant to update
     * @param userId the ID of the user performing the update
     * @param dto    the restaurant update request with new details
     * @return ResponseDto containing the updated restaurant response
     * @throws ResponseStatusException if restaurant not found, code/GST exists, or validation fails
     */
    @Override
    @Transactional
    @CacheEvict(value = {"restaurants", "restaurantGroupsLite"}, allEntries = true)
    public ResponseDto<RestaurantDto<RestaurantResponse>> updateRestaurant(UUID id, String userId, RestaurantRequest dto) {
    Locale userLocale = LocaleContextHolder.getLocale();

    // 1️⃣ Fetch existing restaurant
    Restaurant restaurant = restaurantRepository.findById(id)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                    messageSource.getMessage(MSG_RESTAURANT_UPDATE_ERROR_NOT_FOUND, new Object[]{id}, userLocale)));

    // Check if restaurant is deleted
    if (Boolean.TRUE.equals(restaurant.getIsDeleted())) {
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                messageSource.getMessage(MSG_RESTAURANT_UPDATE_ERROR_DELETED, null, userLocale));
    }

    // Check restaurant code uniqueness (including deleted ones)
    Optional<Restaurant> existingRestaurant = restaurantRepository.findByRestaurantCode(dto.getRestaurantCode());
    if (existingRestaurant.isPresent() && !existingRestaurant.get().getId().equals(id)) {
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                messageSource.getMessage(MSG_RESTAURANT_UPDATE_ERROR_CODE_EXISTS, new Object[]{dto.getRestaurantCode()}, userLocale));
    }

    // Check if GST number already exists (only if provided, excluding current restaurant)
    if (dto.getGstNumber() != null && !dto.getGstNumber().trim().isEmpty()) {
        String trimmedGstNumber = dto.getGstNumber().trim();
        if (restaurantRepository.existsByGstNumberAndIsDeletedFalse(trimmedGstNumber, id)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    messageSource.getMessage(MSG_RESTAURANT_UPDATE_ERROR_GST_NUMBER_EXISTS, null, userLocale));
        }
    }

    // 2️⃣ Update base fields
    restaurant.setRestaurantCode(dto.getRestaurantCode());
    restaurant.setCity(dto.getCity());
    restaurant.setArea(dto.getArea());
    restaurant.setState(dto.getState());
    restaurant.setAddress1(dto.getAddress1());
    restaurant.setAddress2(dto.getAddress2());
    restaurant.setLatitude(dto.getLatitude());
    restaurant.setLongitude(dto.getLongitude());
    restaurant.setLocationPin(dto.getLocationPin());
    restaurant.setTableQrCodeType(dto.getTableQrCodeType());
    restaurant.setPaymentQrUrl(awsService.stripToKey(dto.getPaymentQrUrl()));
    restaurant.setStatus(dto.getStatus());
    restaurant.setIsDeleted(Optional.ofNullable(dto.getIsDeleted()).orElse(false));
        restaurant.setLogoUrl(awsService.stripToKey(dto.getLogoUrl()));
        restaurant.setUpdatedAt(OffsetDateTime.now(ZoneOffset.UTC));
        restaurant.setRestaurantGroupName(dto.getRestaurantGroupName());
        // Trim GST number if provided
        restaurant.setGstNumber(dto.getGstNumber() != null ? dto.getGstNumber().trim() : null);
        restaurant.setPhoneNumber(normalizeOptionalString(dto.getPhoneNumber()));
        
        // Update alert configuration fields (only if provided to preserve existing values)
        if (dto.getSalesAlertThreshold() != null) {
            restaurant.setSalesAlertThreshold(dto.getSalesAlertThreshold());
        }
        if (dto.getRefundAlertPercentage() != null) {
            restaurant.setRefundAlertPercentage(dto.getRefundAlertPercentage());
        }
        if (dto.getCancellationAlertPercentage() != null) {
            restaurant.setCancellationAlertPercentage(dto.getCancellationAlertPercentage());
        }
        if (dto.getAlertsEnabled() != null) {
            restaurant.setAlertsEnabled(dto.getAlertsEnabled());
        }

    // Set updatedBy
    User user = userRepository.findById(UUID.fromString(userId))
            .orElseThrow(() -> new EntityNotFoundException(ERROR_USER_NOT_FOUND_WITH_ID + userId));
    restaurant.setUpdatedBy(user);

    // Link restaurant group
    if (dto.getRestaurantGroupId() != null) {
        RestaurantGroup group = restaurantGroupRepository.findById(dto.getRestaurantGroupId())
                .orElseThrow(() -> new EntityNotFoundException(ERROR_RESTAURANT_GROUP_NOT_FOUND));

        if (Boolean.TRUE.equals(group.getIsDeleted())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    messageSource.getMessage(MSG_RESTAURANT_UPDATE_ERROR_GROUP_DELETED, null, userLocale));
        }

        restaurant.setRestaurantGroup(group);
    } else {
        restaurant.setRestaurantGroup(null);
    }

    restaurant = restaurantRepository.save(restaurant);
    // Flush to ensure changes are persisted immediately
    restaurantRepository.flush();

        // 3️⃣ Handle translations
List<RestaurantTranslation> existingTranslations = restaurantTranslationRepository
.findAllByRestaurantIdWithLanguage(restaurant.getId());

// Get language codes from request with non-empty names
Set<String> validRequestLanguageCodes = dto.getTranslations().stream()
        .filter(t -> t.getLanguageCode() != null && 
                   t.getName() != null && 
                   !t.getName().trim().isEmpty())
        .map(RestaurantTranslationDto::getLanguageCode)
        .collect(Collectors.toSet());

// Remove translations that are not in the request or have empty names
List<RestaurantTranslation> translationsToRemove = new ArrayList<>();
for (RestaurantTranslation existingTranslation : existingTranslations) {
    if (!validRequestLanguageCodes.contains(existingTranslation.getLanguageCode())) {
        translationsToRemove.add(existingTranslation);
    }
}

// Delete translations that are not in the request or have empty names
for (RestaurantTranslation translationToRemove : translationsToRemove) {
    restaurantTranslationRepository.delete(translationToRemove);
}

Set<String> seenNamesInRequest = new HashSet<>(); // Track names in current request to avoid duplicates in same request

for (RestaurantTranslationDto translationDto : dto.getTranslations()) {
// Skip translations with empty or null names
if (translationDto.getName() == null || translationDto.getName().trim().isEmpty()) {
    continue;
}

String nameLower = translationDto.getName().toLowerCase();

// 1️⃣ Check duplicates in the incoming request itself
if (seenNamesInRequest.contains(nameLower)) {
// duplicates within same request for different languages
// allow only if all duplicates belong to same restaurant
// so we do nothing here
}
seenNamesInRequest.add(nameLower);

// 2️⃣ Find existing translation for this language
RestaurantTranslation existingTranslation = existingTranslations.stream()
    .filter(t -> t.getLanguageCode().equalsIgnoreCase(translationDto.getLanguageCode()))
    .findFirst()
    .orElse(null);

// 3️⃣ Check duplicates in database (only if the name actually changes)
boolean isSameAsExisting = existingTranslation != null &&
        existingTranslation.getName() != null &&
        existingTranslation.getName().trim().equalsIgnoreCase(translationDto.getName().trim());

if (!isSameAsExisting) {
    boolean nameExistsElsewhere;

    if (restaurant.getRestaurantGroup() != null) {
        // Check if any other restaurant in the same group has the same name
        nameExistsElsewhere = restaurantTranslationRepository.existsByNameInSameGroupForOtherRestaurants(
                translationDto.getName(), translationDto.getLanguageCode(), restaurant.getRestaurantGroup().getId(), restaurant.getId());
        logger.info("Checking name '{}' in language '{}' for restaurant group '{}', restaurant '{}' - exists: {}",
                translationDto.getName(), translationDto.getLanguageCode(), restaurant.getRestaurantGroup().getId(), restaurant.getId(), nameExistsElsewhere);
    } else {
        // If no group, check all restaurants except this one
        nameExistsElsewhere = restaurantTranslationRepository.existsByNameInOtherRestaurants(
                translationDto.getName(), translationDto.getLanguageCode(), restaurant.getId());
        logger.info("Checking name '{}' in language '{}' for restaurant '{}' (no group) - exists: {}",
                translationDto.getName(), translationDto.getLanguageCode(), restaurant.getId(), nameExistsElsewhere);
    }

    if (nameExistsElsewhere) {
        logger.error("Name conflict detected for '{}' in language '{}' for restaurant '{}'",
                translationDto.getName(), translationDto.getLanguageCode(), restaurant.getId());
        throw new ResponseStatusException(HttpStatus.CONFLICT,
                messageSource.getMessage(MSG_RESTAURANT_UPDATE_ERROR_NAME_EXISTS,
                        new Object[]{translationDto.getName()}, userLocale));
    }
}

// 4️⃣ Save or update translation
if (existingTranslation != null) {
existingTranslation.setName(translationDto.getName());
restaurantTranslationRepository.save(existingTranslation);
} else {
RestaurantTranslation newTranslation = new RestaurantTranslation();
newTranslation.setName(translationDto.getName());
newTranslation.setLanguageCode(translationDto.getLanguageCode());
newTranslation.setRestaurant(restaurant);
restaurantTranslationRepository.save(newTranslation);
}
}





    // 4️⃣ Handle operating hours updates
    if (dto.getOperatingHours() != null && !dto.getOperatingHours().isEmpty()) {
        // Delete existing operating hour slots first (to avoid foreign key constraint violation)
        operatingHourSlotRepository.deleteByRestaurantId(restaurant.getId());
        
        // Delete existing operating hours for this restaurant
        restaurantOperatingHoursRepository.deleteByRestaurantId(restaurant.getId());
        
        // Save new operating hours
        for (RestaurantOperatingHoursRequest hoursRequest : dto.getOperatingHours()) {
            hoursRequest.setRestaurantId(restaurant.getId());
            List<RestaurantOperatingHours> entities = restaurantOperatingHoursMapper.toEntities(hoursRequest);
            // Set the createdByUser for each entity
            entities.forEach(entity -> entity.setCreatedByUser(user));
            restaurantOperatingHoursRepository.saveAll(entities);
        }
        rescheduleUnusedSessionExpiryJobs(restaurant.getId());
    }

    // 5️⃣ Prepare response translations
    List<RestaurantTranslation> savedTranslations = restaurantTranslationRepository.findAllByRestaurantIdWithLanguage(restaurant.getId());
    List<RestaurantTranslationDto> translationDTOs = savedTranslations.stream()
            .map(t -> RestaurantTranslationDto.builder()
                    .languageCode(t.getLanguageCode())
                    .name(t.getName())
                    .build())
            .collect(Collectors.toList());

    // 6️⃣ Fetch operating hours
    List<OperatingHourDto> operatingHours = restaurantOperatingHoursRepository
            .findByRestaurant_Id(restaurant.getId())
            .stream()
            .map(restaurantOperatingHoursMapper::toOperatingHoursDto)
            .collect(Collectors.toList());

    // 6️⃣ Fetch group translations if applicable
    List<RestaurantGroupTranslationDTO> groupTranslations = null;
    if (restaurant.getRestaurantGroup() != null) {
        groupTranslations = restaurantGroupTranslationRepository
                .findAllByRestaurantGroupIdWithLanguage(restaurant.getRestaurantGroup().getId())
                .stream()
                .map(gt -> RestaurantGroupTranslationDTO.builder()
                        .languageCode(gt.getLanguageCode())
                        .name(gt.getName())
                        .build())
                .collect(Collectors.toList());
    }

    // 7️⃣ Build response
    RestaurantResponse response = buildRestaurantResponse(restaurant, translationDTOs, operatingHours, groupTranslations);
    RestaurantDto<RestaurantResponse> restaurantDTO = RestaurantDto.<RestaurantResponse>builder()
            .restaurant(response)
            .build();

    // Create audit trail for restaurant update
    try {
        auditTrailService.createAuditTrail(
                user,
                ActionType.RESTAURANT_UPDATE,
                restaurant,
                null, // status - will default to NA for non-request actions
                null, // ipAddress - not available in this context
                null, // userAgent - not available in this context
                restaurant.getId(),
                ENTITY_TYPE_RESTAURANT,
                AUDIT_MSG_RESTAURANT_UPDATED + restaurant.getRestaurantCode()
        );
    } catch (Exception e) {
        logger.error("Failed to create audit trail for restaurant update: {}", e.getMessage());
        // Don't break restaurant update flow if audit trail fails
    }

    return ResponseDto.<RestaurantDto<RestaurantResponse>>builder()
            .message(messageSource.getMessage(MSG_RESTAURANT_UPDATE_SUCCESS, null, userLocale))
            .data(restaurantDTO)
            .build();
}

    /**
     * Soft deletes a restaurant by setting isDeleted flag to true.
     * Validates that restaurant has no active employees assigned.
     * Clears cache entries.
     *
     * @param id     the UUID of the restaurant to delete
     * @param userId the ID of the user performing the deletion
     * @return ResponseDto with success message
     * @throws ResponseStatusException if restaurant not found or has active employees
     */
    @Override
    @Transactional
    @CacheEvict(value = {"restaurants", "restaurantGroupsLite"}, allEntries = true)
    public ResponseDto<String> deleteRestaurant(UUID id, String userId) {
        Locale userLocale = LocaleContextHolder.getLocale();
        
        Restaurant restaurant = restaurantRepository.findById(id)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, 
                messageSource.getMessage(MSG_RESTAURANT_DELETE_ERROR_NOT_FOUND, new Object[]{id}, userLocale)));
        
        // Check if restaurant is already deleted
        if (restaurant.getIsDeleted() != null && restaurant.getIsDeleted()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, 
                messageSource.getMessage(MSG_RESTAURANT_DELETE_ERROR_ALREADY_DELETED, null, userLocale));
        }
        
        // Find user for updatedBy
        User user = userRepository.findById(UUID.fromString(userId))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, 
                    messageSource.getMessage(MSG_USER_NOT_FOUND, new Object[]{userId}, userLocale)));

                    List<User> associatedUsers = userRepository.findAllByRestaurantId(id);
                    for (User associatedUser : associatedUsers) {
                        associatedUser.setRestaurantId(null);
                        associatedUser.setUpdatedAt(OffsetDateTime.now(ZoneOffset.UTC));
                    }
                    if (!associatedUsers.isEmpty()) {
                        userRepository.saveAll(associatedUsers);
                    }
        
        // Soft delete - set isDeleted flag to true
        restaurant.setIsDeleted(true);
        restaurant.setUpdatedAt(OffsetDateTime.now(ZoneOffset.UTC));
        restaurant.setUpdatedBy(user);
        
        restaurantRepository.save(restaurant);
        
        // Create audit trail for restaurant deletion
        try {
            auditTrailService.createAuditTrail(
                    user,
                    ActionType.RESTAURANT_DELETE,
                    restaurant,
                    null, // status - will default to NA for non-request actions
                    null, // ipAddress - not available in this context
                    null, // userAgent - not available in this context
                    restaurant.getId(),
                    ENTITY_TYPE_RESTAURANT,
                    AUDIT_MSG_RESTAURANT_DELETED + restaurant.getRestaurantCode()
            );
        } catch (Exception e) {
            logger.error("Failed to create audit trail for restaurant deletion: {}", e.getMessage());
            // Don't break restaurant deletion flow if audit trail fails
        }
        
        return ResponseDto.<String>builder()
            .message(messageSource.getMessage(MSG_RESTAURANT_DELETE_SUCCESS, null, LocaleContextHolder.getLocale()))
            .build();
    }

    /**
     * Assigns employees (users) to a restaurant.
     * Updates restaurant ID for each employee and handles password reset for waiters.
     * Creates audit trail for assignments.
     *
     * @param request the assignment request with restaurant ID and employee IDs
     * @return ResponseDto containing list of assigned employees
     * @throws EntityNotFoundException if restaurant not found or employee not found
     */
    // Employee assignment methods
    @Override
    @Transactional
    @CacheEvict(value = "restaurants", allEntries = true)
    public ResponseDto<EmployeeAssignmentListResponse> assignEmployeesToRestaurant(AssignEmployeesRequest request) {
        logger.info("Assigning employees to restaurant: {}", request.getRestaurantId());

        // Validate restaurant exists
        Restaurant restaurant = restaurantRepository.findById(request.getRestaurantId())
                .orElseThrow(() -> new EntityNotFoundException(
                    messageSource.getMessage("restaurant.employee.assignment.error.employee.notfound", 
                        new Object[]{request.getRestaurantId()}, LocaleContextHolder.getLocale())
                ));

        Locale requestLocale = LocaleContextHolder.getLocale();

        // Batch load employees (avoid N+1 queries)
        List<UUID> employeeIds = request.getEmployees() == null
                ? Collections.emptyList()
                : request.getEmployees().stream()
                    .map(AssignEmployeesRequest.EmployeeAssignment::getEmployeeId)
                    .filter(Objects::nonNull)
                    .distinct()
                    .collect(Collectors.toList());

        Map<UUID, User> employeeById = new HashMap<>();
        if (!employeeIds.isEmpty()) {
            for (User u : userRepository.findAllById(employeeIds)) {
                employeeById.put(u.getId(), u);
            }
        }

        // Validate all requested employees exist
        for (UUID employeeId : employeeIds) {
            if (!employeeById.containsKey(employeeId)) {
                throw new EntityNotFoundException(
                        messageSource.getMessage(
                                "restaurant.employee.assignment.error.employee.notfound",
                                new Object[]{employeeId},
                                requestLocale
                        )
                );
            }
        }

        // Apply assignment with bulk updates to reduce JPA entity flush overhead
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        if (!employeeIds.isEmpty()) {
            userRepository.assignRestaurantForUsers(restaurant.getId(), now, employeeIds);
            userRepository.activateUnlockedUsers(EntityStatus.ACTIVE, now, employeeIds);
        }

        // Keep in-memory objects aligned for response + after-commit side effects
        List<User> savedEmployees = new ArrayList<>();
        for (UUID employeeId : employeeIds) {
            User employee = employeeById.get(employeeId);
            if (employee == null) {
                continue;
            }
            boolean isLocked = Boolean.TRUE.equals(employee.getIsStatusLocked());
            employee.setRestaurantId(restaurant.getId());
            employee.setUpdatedAt(now);
            if (!isLocked) {
                employee.setStatus(EntityStatus.ACTIVE);
            }
            savedEmployees.add(employee);
        }

        // Build response quickly (side-effects happen after commit, async)
        List<EmployeeAssignmentListResponse.AssignedEmployee> assignedEmployees = new ArrayList<>();
        for (User savedEmployee : savedEmployees) {
            UUID existingRoleId = savedEmployee.getRoleId();
            assignedEmployees.add(new EmployeeAssignmentListResponse.AssignedEmployee(
                    savedEmployee.getId(),
                    safeConcatName(savedEmployee),
                    savedEmployee.getEmail(),
                    existingRoleId,
                    "Role Name",
                    200
            ));
        }

        scheduleEmployeeAssignmentSideEffectsAfterCommit(savedEmployees, restaurant, requestLocale);

        EmployeeAssignmentListResponse response = EmployeeAssignmentListResponse.builder()
                .restaurantId(restaurant.getId())
                .assignedEmployees(assignedEmployees)
                .count((long) assignedEmployees.size())
                .total((long) assignedEmployees.size())
                .build();

        return ResponseDto.<EmployeeAssignmentListResponse>builder()
                .data(response)
                .message(messageSource.getMessage("restaurant.employee.assignment.success", null, LocaleContextHolder.getLocale()))
                .build();
    }

    /**
     * Runs {@link #runEmployeeAssignmentSideEffectsForSingleEmployee} for each saved employee after the
     * current DB transaction commits (async on {@code employeeAssignmentTaskExecutor}), preserving
     * {@code requestLocale} in {@link org.springframework.context.i18n.LocaleContextHolder} for the task.
     * If no executor is wired, runs the same work synchronously in the caller thread.
     */
    private void scheduleEmployeeAssignmentSideEffectsAfterCommit(
            List<User> savedEmployees,
            Restaurant restaurant,
            Locale requestLocale
    ) {
        if (savedEmployees == null || savedEmployees.isEmpty()) {
            return;
        }
        if (employeeAssignmentTaskExecutor == null) {
            // Safety: if executor is not available, keep existing behavior (execute inline)
            for (User savedEmployee : savedEmployees) {
                runEmployeeAssignmentSideEffectsForSingleEmployee(savedEmployee, restaurant, requestLocale);
            }
            return;
        }

        Runnable task = () -> {
            Locale previous = LocaleContextHolder.getLocale();
            try {
                if (requestLocale != null) {
                    LocaleContextHolder.setLocale(requestLocale);
                }
                for (User savedEmployee : savedEmployees) {
                    runEmployeeAssignmentSideEffectsForSingleEmployee(savedEmployee, restaurant, requestLocale);
                }
            } finally {
                LocaleContextHolder.setLocale(previous);
            }
        };

        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    CompletableFuture.runAsync(task, employeeAssignmentTaskExecutor)
                            .exceptionally(ex -> {
                                logger.error("Employee assignment side-effects async task failed: {}", ex.getMessage(), ex);
                                return null;
                            });
                }
            });
        } else {
            CompletableFuture.runAsync(task, employeeAssignmentTaskExecutor)
                    .exceptionally(ex -> {
                        logger.error("Employee assignment side-effects async task failed: {}", ex.getMessage(), ex);
                        return null;
                    });
        }
    }

    /**
     * Best-effort post-assignment work for one user: waiter password reset path when applicable,
     * manager notifications, and {@link #sendAssignmentEmailsOnEmployeeAssigned}. Swallows exceptions so
     * the main assignment transaction is not rolled back by side effects.
     *
     * @param savedEmployee   newly persisted user row
     * @param restaurant      restaurant context for the assignment
     * @param requestLocale   locale for downstream messaging; async callers typically set
     *                        {@link org.springframework.context.i18n.LocaleContextHolder} before invoking
     */
    private void runEmployeeAssignmentSideEffectsForSingleEmployee(User savedEmployee, Restaurant restaurant, Locale requestLocale) {
        try {
            if (savedEmployee == null || restaurant == null) {
                return;
            }
            UUID existingRoleId = savedEmployee.getRoleId();

            // Check if employee is a WAITER and reset password + send email to managers
            if (existingRoleId != null && roleRepository != null) {
                handleWaiterPasswordResetOnAssignment(savedEmployee, existingRoleId, restaurant.getId());
            }

            // Notify managers when new employee is assigned to restaurant
            if (notificationService != null && roleRepository != null) {
                notifyManagersOfEmployeeAssignment(savedEmployee, restaurant);
            }

            // Email flow (generic)
            sendAssignmentEmailsOnEmployeeAssigned(savedEmployee, restaurant);
        } catch (Exception e) {
            logger.error("Employee assignment side-effects failed for employee {} and restaurant {}: {}",
                    savedEmployee != null ? savedEmployee.getId() : null,
                    restaurant != null ? restaurant.getId() : null,
                    e.getMessage(),
                    e
            );
        }
    }

    private String safeConcatName(User user) {
        String first = user != null ? user.getFirstName() : null;
        String last = user != null ? user.getLastName() : null;
        String full = (first == null ? "" : first.trim()) + " " + (last == null ? "" : last.trim());
        String trimmed = full.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    /**
     * Retrieves active categories for a restaurant across all assigned menus.
     * Returns categories with their menu assignments and localized names.
     *
     * @param restaurantId the UUID of the restaurant
     * @return ResponseDto containing list of active categories with menu information
     * @throws ResponseStatusException if restaurant not found, deleted, or inactive
     */
    @Override
    public ResponseDto<MenuCategorySummaryResponse> getActiveCategoriesForRestaurant(UUID restaurantId) {
        Locale userLocale = LocaleContextHolder.getLocale();
        String languageCode = userLocale.getLanguage();

        // 1) Validate restaurant exists, not deleted, and active
        Restaurant restaurant = restaurantRepository.findByIdAndIsDeletedFalse(restaurantId)
            .orElseThrow(() -> new ResponseStatusException(
                HttpStatus.NOT_FOUND,
                messageSource.getMessage(MSG_RESTAURANT_NOT_FOUND, null, userLocale)
            ));
        if (restaurant.getIsDeleted() != null && restaurant.getIsDeleted()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                messageSource.getMessage("restaurant.deleted", null, userLocale));
        }
        if (!EntityStatus.ACTIVE.equals(restaurant.getStatus())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                messageSource.getMessage("restaurant.not.active", new Object[]{restaurant.getId()}, userLocale));
        }

        // 2) Find assigned menu - must be exactly one
        List<RestaurantMenuMapping> restaurantMenuMappings = restaurantMenuMappingRepository.findById_RestaurantId(restaurantId);
        if (restaurantMenuMappings == null || restaurantMenuMappings.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                messageSource.getMessage("restaurant.menu.not.assigned", null, userLocale));
        }
        if (restaurantMenuMappings.size() > 1) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                messageSource.getMessage("restaurant.menu.multiple.assigned", null, userLocale));
        }

        RestaurantMenuMapping menuMapping = restaurantMenuMappings.get(0);
        
        // 3) ✅ NEW: Check that menu status is LIVE
        if (!RestaurantMenuMappingStatus.LIVE.equals(menuMapping.getStatus())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                messageSource.getMessage("restaurant.menu.not.live", 
                    new Object[]{menuMapping.getStatus()}, userLocale));
        }

        UUID menuId = menuMapping.getId().getMenuId();

        // 4) Localized menu name with fallback
        String menuName = null;
        var menuTranslations = menuTranslationRepository.findByMenuId(menuId);
        if (!menuTranslations.isEmpty()) {
            var mt = menuTranslations.stream()
                    .filter(t -> t.getLanguageCode() != null && t.getLanguageCode().equals(languageCode))
                    .findFirst()
                    .orElse(null);
            
            if (mt != null) {
                menuName = mt.getName();
            } else {
                // Fallback to ordered languages from config
                java.util.Optional<com.gulfnet.shared_library.entity.MenuTranslation> fallback =
                        TranslationUtils.pickPreferredOrFromList(
                                menuTranslations,
                                languageCode,
                                localizationProperties.getLanguages(),
                                com.gulfnet.shared_library.entity.MenuTranslation::getLanguageCode
                        );
                menuName = fallback.map(com.gulfnet.shared_library.entity.MenuTranslation::getName).orElse(null);
            }
        }

        // 5) Fetch categories via ACTIVE menu-category mappings only
        List<MenuCategoryMapping> mappings = menuCategoryMappingRepository.findByMenuId(menuId);

        List<MenuCategorySummaryResponse.CategorySummary> categorySummaries = mappings.stream()
            .filter(m -> EntityStatus.ACTIVE.equals(m.getStatus()))
            .filter(m -> m.getParentCategory() == null) // Only include top-level categories (no parent category)
            .map(MenuCategoryMapping::getCategory)
            .filter(Objects::nonNull)
            .map(cat -> {
                String name = null;
                var categoryTranslations = categoryTranslationRepository.findByCategoryId(cat.getId());
                if (!categoryTranslations.isEmpty()) {
                    var ct = categoryTranslations.stream()
                            .filter(t -> t.getLanguageCode() != null && t.getLanguageCode().equals(languageCode))
                            .findFirst()
                            .orElse(null);
                    
                    if (ct != null) {
                        name = ct.getName();
                    } else {
                        // Fallback to ordered languages from config
                        java.util.Optional<com.gulfnet.shared_library.entity.CategoryTranslation> fallback =
                                TranslationUtils.pickPreferredOrFromList(
                                        categoryTranslations,
                                        languageCode,
                                        localizationProperties.getLanguages(),
                                        com.gulfnet.shared_library.entity.CategoryTranslation::getLanguageCode
                                );
                        name = fallback.map(com.gulfnet.shared_library.entity.CategoryTranslation::getName).orElse(null);
                    }
                }
                
                return MenuCategorySummaryResponse.CategorySummary.builder()
                    .id(cat.getId())
                    .name(name)
                    .build();
            })
            .toList();
            
        MenuCategorySummaryResponse summary = MenuCategorySummaryResponse.builder()
            .menuId(menuId)
            .menuName(menuName)
            .categories(categorySummaries)
            .build();

        return ResponseDto.<MenuCategorySummaryResponse>builder()
            .data(summary)
            .message(messageSource.getMessage(MSG_CATEGORY_ACTIVE_LIST_SUCCESS, null, userLocale))
            .build();
    }

    /**
     * Removes a menu assignment from a restaurant.
     * Deletes restaurant menu mapping and related discount/promotion mappings.
     *
     * @param restaurantId the UUID of the restaurant
     * @param menuId       the UUID of the menu to remove
     * @param locale       locale code for localized error messages
     * @return ResponseDto with success message
     * @throws ResponseStatusException if restaurant not found, menu not found, or not assigned
     */
    @Override
    @Transactional
    public ResponseDto<Void> removeMenuFromRestaurant(UUID restaurantId, UUID menuId, String locale) {
        Locale userLocale = Locale.forLanguageTag(locale);

        // 1. Validate restaurant exists and is not deleted
        Restaurant restaurant = restaurantRepository.findById(restaurantId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        messageSource.getMessage(MSG_RESTAURANT_NOT_FOUND, null, userLocale)));

        if (Boolean.TRUE.equals(restaurant.getIsDeleted())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    messageSource.getMessage("restaurant.deleted", null, userLocale));
        }

        // 2. Validate restaurant is active
        if (restaurant.getStatus() != EntityStatus.ACTIVE) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    messageSource.getMessage("restaurant.menu.removal.error.not.active", null, userLocale));
        }

        // 3. Validate menu exists and is not deleted
        Menu menu = menuRepository.findById(menuId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        messageSource.getMessage(MSG_MENU_NOT_FOUND, null, userLocale)));

        if (Boolean.TRUE.equals(menu.getIsDeleted())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    messageSource.getMessage("menu.deleted", null, userLocale));
        }

        // 4. Check if restaurant-menu mapping exists
        RestaurantMenuId mappingId = new RestaurantMenuId(restaurantId, menuId);
        Optional<RestaurantMenuMapping> existingMapping = restaurantMenuMappingRepository.findById(mappingId);
        
        if (existingMapping.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                    messageSource.getMessage("restaurant.menu.mapping.not.found", null, userLocale));
        }

        // 5. Check if there are any transactions with blocked statuses (OPEN, PENDING)
        // or orders with items/combos in blocked statuses (PUSHED, ON_HOLD, COOKING, READY, DELAYED) for this restaurant
        // that contain items from this specific menu
        // Note: COMPLETED, CANCELLED, REFUNDED, and PARTIALLY_REFUNDED are final states and should allow menu unassignment
        List<TransactionStatus> blockedTransactionStatuses = Arrays.asList(
                TransactionStatus.OPEN, 
                TransactionStatus.PENDING
        );
        
        List<ItemStatus> blockedItemStatuses = Arrays.asList(
                ItemStatus.PUSHED,
                ItemStatus.ON_HOLD,
                ItemStatus.COOKING,
                ItemStatus.READY,
                ItemStatus.DELAYED
        );
        
        // Get all item IDs that belong to this menu
        List<CategoryItemMapping> menuCategoryItemMappings =
                categoryItemMappingRepository.findByMenuCategoryMappingMenuId(menuId);
        List<UUID> menuItemIds = menuCategoryItemMappings.stream()
                .map(mapping -> mapping.getItem().getId())
                .distinct()
                .collect(Collectors.toList());
        
        if (!menuItemIds.isEmpty()) {
            // Find all transactions for this restaurant (we need to check all, not just blocked ones)
            // because even COMPLETED/CANCELED transactions might have items in blocked statuses
            List<Transaction> allTransactions = transactionRepository.findByRestaurantId(restaurantId);
            
            // Check each transaction
            for (Transaction transaction : allTransactions) {
                Order order = transaction.getOrder();
                TransactionStatus transactionStatus = transaction.getTransactionStatus();
                
                // Skip transactions with null order or final transaction states (COMPLETED, CANCELLED, REFUNDED, PARTIALLY_REFUNDED) - these allow unassignment
                if (order == null || 
                    transactionStatus == TransactionStatus.COMPLETED || 
                    transactionStatus == TransactionStatus.CANCELED || 
                    transactionStatus == TransactionStatus.REFUNDED ||
                    transactionStatus == TransactionStatus.PARTIALLY_REFUNDED) {
                    continue; // Skip null orders or final states - no need to check item statuses
                }
                
                // Check transaction status first - if it's in blocked statuses, block unassignment
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
                        logger.warn("Blocking menu removal for restaurant {} and menu {} due to transaction status: {}", 
                                restaurantId, menuId, transactionStatus);
                        throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                                messageSource.getMessage("restaurant.menu.removal.error.ongoing.orders", null, userLocale));
                    }
                } else {
                    // Transaction is not in final states and not blocked - check if items/combos are in blocked statuses
                    List<OrderedItem> orderedItems = orderedItemRepository.findByOrderId(order.getId());
                    List<OrderedCombo> orderedCombos = orderedComboRepository.findByOrderId(order.getId());
                    
                    // Check standalone items from menu with blocked statuses
                    boolean hasBlockedMenuItems = orderedItems.stream()
                            .anyMatch(oi -> oi.getItem() != null && 
                                    oi.getOrderedCombo() == null && // Only standalone items
                                    menuItemIds.contains(oi.getItem().getId()) &&
                                    oi.getItemStatus() != null &&
                                    blockedItemStatuses.contains(oi.getItemStatus()));
                    
                    // Check combos from menu with blocked statuses
                    boolean hasBlockedMenuCombos = orderedCombos.stream()
                            .anyMatch(oc -> oc.getCombo() != null && 
                                    oc.getCombo().getMenu() != null &&
                                    oc.getCombo().getMenu().getId().equals(menuId) &&
                                    oc.getItemStatus() != null &&
                                    blockedItemStatuses.contains(oc.getItemStatus()));
                    
                    // Check items within combos from menu with blocked statuses
                    boolean hasBlockedMenuItemsInCombos = orderedItems.stream()
                            .anyMatch(oi -> oi.getItem() != null && 
                                    oi.getOrderedCombo() != null && // Items within combos
                                    menuItemIds.contains(oi.getItem().getId()) &&
                                    oi.getItemStatus() != null &&
                                    blockedItemStatuses.contains(oi.getItemStatus()));
                    
                    if (hasBlockedMenuItems || hasBlockedMenuCombos || hasBlockedMenuItemsInCombos) {
                        throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                                messageSource.getMessage("restaurant.menu.removal.error.ongoing.orders", null, userLocale));
                    }
                }
            }
        }

        // 6. Delete the restaurant-menu mapping
        restaurantMenuMappingRepository.delete(existingMapping.get());

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
                    Optional<RestaurantGroupMenuMapping> groupMenuMapping = restaurantGroupMenuMappingRepository
                            .findByMenuIdAndRestaurantGroupId(menuId, restaurantGroup.getId());
                    if (groupMenuMapping.isPresent()) {
                        restaurantGroupMenuMappingRepository.delete(groupMenuMapping.get());
                        logger.info("Removed group-menu mapping as no restaurants in group {} are assigned to menu {}", 
                                restaurantGroup.getId(), menuId);
                    }
                }
            } else {
                // No other restaurants in the group, remove group-menu mapping
                Optional<RestaurantGroupMenuMapping> groupMenuMapping = restaurantGroupMenuMappingRepository
                        .findByMenuIdAndRestaurantGroupId(menuId, restaurantGroup.getId());
                if (groupMenuMapping.isPresent()) {
                    restaurantGroupMenuMappingRepository.delete(groupMenuMapping.get());
                    logger.info("Removed group-menu mapping as no restaurants remain in group {} for menu {}", 
                            restaurantGroup.getId(), menuId);
                }
            }
        }

        logger.info("Successfully removed menu {} from restaurant {}", menuId, restaurantId);
        
        return ResponseDto.<Void>builder()
                .message(messageSource.getMessage("restaurant.menu.removed.success", null, userLocale))
                .build();
    }

    // Helper method to calculate employee count for a restaurant
    private Integer calculateEmployeeCount(UUID restaurantId) {
        return userRepository.countByRestaurantIdAndIsDeletedFalse(restaurantId);
    }

    // Helper method to calculate total seating capacity for a restaurant
    private Integer calculateSeatingCapacity(UUID restaurantId) {
        return restaurantTableRepository.getTotalSeatingCapacityByRestaurantId(restaurantId);
    }

    /**
     * Creates a Pageable object with database-level sorting where possible.
     * For complex fields like "name" and "employeeCount", returns unsorted Pageable for in-memory sorting.
     *
     * @param pageNumber page number (0-based)
     * @param pageSize   page size
     * @param sortBy     field to sort by
     * @param direction  sort direction
     * @return Pageable configured with sorting if applicable
     */
    // Helper method to create pageable with database-level sorting where possible
    private Pageable createPageableWithSorting(int pageNumber, int pageSize, String sortBy, Sort.Direction direction) {
        if (sortBy == null || sortBy.trim().isEmpty()) {
            return PageRequest.of(pageNumber, pageSize);
        }
        
        Sort.Direction sortDirection = (direction != null) ? direction : Sort.Direction.ASC;
        
        // Handle database-level sorting for simple fields
        switch (sortBy.toLowerCase()) {
            case "createdat":
                return PageRequest.of(pageNumber, pageSize, Sort.by(sortDirection, "createdAt"));
            case "name":
            case "employeecount":
                // For complex fields like "name" and "employeeCount" that require translation lookup or calculation,
                // we'll do in-memory sorting after loading the data
                return PageRequest.of(pageNumber, pageSize);
            default:
                // For other fields, try database-level sorting if the field exists
                return PageRequest.of(pageNumber, pageSize, Sort.by(sortDirection, sortBy));
        }
    }

    /**
     * Builds a JPA Specification for filtering restaurants.
     * Includes filters for deletion status, manager access, restaurant group, restaurant ID,
     * status, search term, and menu assignment. Uses EXISTS subqueries for efficient filtering.
     *
     * @param restaurantGroupId optional filter by restaurant group ID
     * @param restaurantId      optional filter by restaurant ID
     * @param status            optional filter by entity status
     * @param search            optional search term for restaurant name
     * @param hasMenuAssigned   optional filter by menu assignment status
     * @param userId            user ID for manager access filtering
     * @param userRole          user role for manager access filtering
     * @param isDeleted         optional filter by deletion status
     * @return JPA Specification for restaurant filtering
     */
    // Helper method to build restaurant specification with menu assignment filter and manager access filter
    private Specification<Restaurant> buildRestaurantSpecification(UUID restaurantGroupId, UUID restaurantId, String status, String search, Boolean hasMenuAssigned, String userId, String userRole, Boolean isDeleted) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            // Handle isDeleted filter: if isDeleted=true, show deleted; otherwise show non-deleted (default)
            if (isDeleted != null && isDeleted) {
                predicates.add(cb.equal(root.get("isDeleted"), true));
            } else {
                predicates.add(cb.equal(root.get("isDeleted"), false));
            }
            
            // Manager access filtering - if user is a manager, only show their assigned restaurant
            if (userId != null && userRole != null && ROLE_MANAGER.equals(userRole)) {
                // Find the restaurant assigned to this manager
                Subquery<UUID> managerRestaurantSubquery = query.subquery(UUID.class);
                Root<User> userRoot = managerRestaurantSubquery.from(User.class);
                managerRestaurantSubquery.select(userRoot.get("restaurantId"));
                managerRestaurantSubquery.where(cb.equal(userRoot.get("id"), UUID.fromString(userId)));
                
                predicates.add(cb.equal(root.get("id"), managerRestaurantSubquery));
            }
            
            // Filter by specific restaurant ID if provided
            if (restaurantId != null) {
                predicates.add(cb.equal(root.get("id"), restaurantId));
            }
            
            if (restaurantGroupId != null) {
                predicates.add(cb.equal(root.get("restaurantGroup").get("id"), restaurantGroupId));
            }
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
                // Use EXISTS subquery instead of join to avoid duplicate rows and incorrect count
                // EXISTS subquery ensures correct count query generation by Spring Data JPA
                Subquery<Long> translationSubquery = query.subquery(Long.class);
                Root<RestaurantTranslation> translationRoot = translationSubquery.from(RestaurantTranslation.class);
                translationSubquery.select(cb.literal(1L));
                translationSubquery.where(
                    cb.and(
                        cb.equal(translationRoot.get("restaurant"), root),
                        cb.like(cb.lower(translationRoot.get("name")), searchTerm)
                    )
                );
                predicates.add(cb.exists(translationSubquery));
                // Ensure distinct for both SELECT and COUNT queries to prevent duplicate counting
                // This is important when there are multiple EXISTS subqueries or complex predicates
                query.distinct(true);
            }
            
            // Optimize menu assignment filter - use EXISTS for better performance
            if (hasMenuAssigned != null) {
                Subquery<Long> subquery = query.subquery(Long.class);
                Root<RestaurantMenuMapping> mappingRoot = subquery.from(RestaurantMenuMapping.class);
                subquery.select(cb.literal(1L));
                subquery.where(cb.equal(mappingRoot.get("id").get("restaurantId"), root.get("id")));
                
                if (hasMenuAssigned) {
                    predicates.add(cb.exists(subquery));
                } else {
                    predicates.add(cb.not(cb.exists(subquery)));
                }
            }
            
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    /**
     * Builds an empty restaurant list response with pagination metadata.
     * Used when no restaurants match the filter criteria.
     *
     * @param userLocale locale for localized success message
     * @param pageNumber page number (0-based)
     * @param pageSize   page size
     * @return ResponseDto containing empty restaurant list with pagination metadata
     */
    // Helper method to build empty response
    private ResponseDto<RestaurantListResponse> buildEmptyResponse(Locale userLocale, int pageNumber, int pageSize) {
        PaginationMetaData metaData = PaginationMetaData.builder()
                .page(pageNumber + 1)
                .size(pageSize)
                .totalPages(0)
                .totalRecords(0L)
                .build();
        
        RestaurantListResponse listResponse = RestaurantListResponse.builder()
                .restaurants(Collections.emptyList())
                .count(0L)
                .total(0L)
                .metaData(metaData)
                .build();
        
        return ResponseDto.<RestaurantListResponse>builder()
                .message(messageSource.getMessage(MSG_RESTAURANT_LIST_SUCCESS, null, userLocale))
                .data(listResponse)
                .build();
    }

    /**
     * Builds a RestaurantResponse DTO using batch-loaded data for performance optimization.
     * Uses pre-loaded maps for translations, employee counts, seating capacity, and other aggregated data.
     * Applies locale-based translation fallback logic.
     *
     * @param restaurant                  the restaurant entity
     * @param translationsMap            batch-loaded map of restaurant translations by restaurant ID
     * @param groupTranslationsMap        batch-loaded map of restaurant group translations by group ID
     * @param employeeCountMap           batch-loaded map of employee counts by restaurant ID
     * @param seatingCapacityMap          batch-loaded map of seating capacities by restaurant ID
     * @param restaurantsWithMenuAssignments set of restaurant IDs with menu assignments
     * @param discountCountMap            batch-loaded map of discount counts by restaurant ID
     * @param promotionCountMap           batch-loaded map of promotion counts by restaurant ID
     * @param preSignedUrlCache           cache of pre-signed URLs (not used in optimized version)
     * @param locale                      locale code for selecting translations
     * @return RestaurantResponse with all restaurant details
     */
    // Optimized method to build restaurant response using batch-loaded data
    private RestaurantResponse buildRestaurantResponseOptimized(
            Restaurant restaurant,
            Map<UUID, List<RestaurantTranslation>> translationsMap,
            Map<UUID, List<RestaurantGroupTranslation>> groupTranslationsMap,
            Map<UUID, Integer> employeeCountMap,
            Map<UUID, Integer> seatingCapacityMap,
            Set<UUID> restaurantsWithMenuAssignments,
            Map<UUID, Long> discountCountMap,
            Map<UUID, Long> promotionCountMap,
            Map<String, String> preSignedUrlCache,
            String locale) {
        
        // Get translations from batch-loaded map
        List<RestaurantTranslation> translations = translationsMap.getOrDefault(restaurant.getId(), Collections.emptyList());
        RestaurantTranslation translation = translations.stream()
                .filter(t -> t.getLanguageCode() != null && t.getLanguageCode().equals(locale))
                .findFirst().orElse(null);

        List<RestaurantTranslationDto> translationDTOs = new ArrayList<>();
        if (translation != null) {
            translationDTOs.add(RestaurantTranslationDto.builder()
                    .languageCode(translation.getLanguageCode())
                    .name(translation.getName())
                    .build());
        } else if (!translations.isEmpty()) {
            java.util.Optional<RestaurantTranslation> fallback = TranslationUtils.pickPreferredOrFromList(
                    translations, locale, localizationProperties.getLanguages(), RestaurantTranslation::getLanguageCode);
            fallback.ifPresent(trans -> translationDTOs.add(RestaurantTranslationDto.builder()
                    .languageCode(trans.getLanguageCode())
                    .name(trans.getName())
                    .build()));
        }

        // Get group translations from batch-loaded map
        List<RestaurantGroupTranslationDTO> groupTranslations = null;
        if (restaurant.getRestaurantGroup() != null) {
            List<RestaurantGroupTranslation> groupTranslationEntities = groupTranslationsMap
                .getOrDefault(restaurant.getRestaurantGroup().getId(), Collections.emptyList());
            
            RestaurantGroupTranslation groupTranslation = groupTranslationEntities.stream()
                    .filter(gt -> gt.getLanguageCode() != null && gt.getLanguageCode().equals(locale))
                    .findFirst().orElse(null);

            final List<RestaurantGroupTranslationDTO> finalGroupTranslations = new ArrayList<>();
            if (groupTranslation != null) {
                finalGroupTranslations.add(RestaurantGroupTranslationDTO.builder()
                        .languageCode(groupTranslation.getLanguageCode())
                        .name(groupTranslation.getName())
                        .build());
            } else if (!groupTranslationEntities.isEmpty()) {
                java.util.Optional<RestaurantGroupTranslation> fallback = TranslationUtils.pickPreferredOrFromList(
                        groupTranslationEntities, locale, localizationProperties.getLanguages(), RestaurantGroupTranslation::getLanguageCode);
                fallback.ifPresent(gt -> finalGroupTranslations.add(RestaurantGroupTranslationDTO.builder()
                        .languageCode(gt.getLanguageCode())
                        .name(gt.getName())
                        .build()));
            }
            groupTranslations = finalGroupTranslations;
        }

        // Skip pre-signed URLs for restaurant listing to improve performance
        String signedLogoUrl = null;
        String signedPaymentQrUrl = null;

        String createdByName = (restaurant.getCreatedBy() != null)
                ? restaurant.getCreatedBy().getFirstName() + " " + restaurant.getCreatedBy().getLastName()
                : null;

        String updatedByName = (restaurant.getUpdatedBy() != null)
                ? restaurant.getUpdatedBy().getFirstName() + " " + restaurant.getUpdatedBy().getLastName()
                : null;

        // Get counts from batch-loaded maps
        Integer employeeCount = employeeCountMap.getOrDefault(restaurant.getId(), 0);
        Integer seatingCapacity = seatingCapacityMap.getOrDefault(restaurant.getId(), 0);
        Boolean menuPublished = restaurantsWithMenuAssignments.contains(restaurant.getId());
        Long activeDiscountCount = discountCountMap.getOrDefault(restaurant.getId(), 0L);
        Long activePromotionCount = promotionCountMap.getOrDefault(restaurant.getId(), 0L);

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
            .countryName(getCountryNameFromChainConfig())
            .tableQrCodeType(restaurant.getTableQrCodeType())
            .paymentQrUrl(signedPaymentQrUrl)
            .status(restaurant.getStatus())
            .createdAt(restaurant.getCreatedAt() != null ? restaurant.getCreatedAt().toLocalDateTime() : null)
            .createdBy(createdByName)
            .updatedAt(restaurant.getUpdatedAt() != null ? restaurant.getUpdatedAt().toLocalDateTime() : null)
            .updatedBy(updatedByName)
            .restaurantGroupId(restaurant.getRestaurantGroup() != null ? 
                restaurant.getRestaurantGroup().getId().toString() : null)
            .logoUrl(signedLogoUrl)
            .translations(translationDTOs)
            .isDeleted(restaurant.getIsDeleted())
            .restaurantGroupName(restaurant.getRestaurantGroupName())
            .employeeCount(employeeCount)
            .seatingCapacity(seatingCapacity)
            .menuPublished(menuPublished)
            .restaurantGroupNames(groupTranslations)
            .activeDiscountCount(activeDiscountCount)
            .activePromotionCount(activePromotionCount)
            .gstNumber(restaurant.getGstNumber())
            .salesAlertThreshold(restaurant.getSalesAlertThreshold())
            .refundAlertPercentage(restaurant.getRefundAlertPercentage())
            .cancellationAlertPercentage(restaurant.getCancellationAlertPercentage())
            .alertsEnabled(restaurant.getAlertsEnabled())
            .phoneNumber(restaurant.getPhoneNumber())
            .build();
    }

    /**
     * Builds a RestaurantResponse DTO from restaurant entity and related data.
     * Includes translations, operating hours, group translations, and pre-signed image URLs.
     *
     * @param restaurant       the restaurant entity
     * @param translationDTOs  list of restaurant translations
     * @param operatingHours   list of operating hours
     * @param groupTranslations list of restaurant group translations
     * @return RestaurantResponse with all restaurant details
     */
    // Helper method to build RestaurantResponse with all fields
    private RestaurantResponse buildRestaurantResponse(Restaurant restaurant, List<RestaurantTranslationDto> translationDTOs, 
                                                      List<OperatingHourDto> operatingHours, List<RestaurantGroupTranslationDTO> groupTranslations) {
        logger.debug("Building response - restaurantCode from entity: {}", restaurant.getRestaurantCode());
        
        String signedLogoUrl = null;
        if (restaurant.getLogoUrl() != null && !restaurant.getLogoUrl().isEmpty()) {
            signedLogoUrl = awsService.getPreSignedUrl(restaurant.getLogoUrl());
        }

        String signedPaymentQrUrl = null;
        if (restaurant.getPaymentQrUrl() != null && !restaurant.getPaymentQrUrl().isEmpty()) {
            signedPaymentQrUrl = awsService.getPreSignedUrl(restaurant.getPaymentQrUrl());
        }

        // Fetch user names for createdBy and updatedBy
        String createdByName = null;
        if (restaurant.getCreatedBy() != null) {
            User createdByUser = userRepository.findById(restaurant.getCreatedBy().getId()).orElse(null);
            if (createdByUser != null) {
                String firstName = createdByUser.getFirstName() != null ? createdByUser.getFirstName() : "";
                String lastName = createdByUser.getLastName() != null ? createdByUser.getLastName() : "";
                createdByName = firstName + " " + lastName;
                createdByName = createdByName.trim();
            }
        }

        String updatedByName = null;
        if (restaurant.getUpdatedBy() != null) {
            User updatedByUser = userRepository.findById(restaurant.getUpdatedBy().getId()).orElse(null);
            if (updatedByUser != null) {
                String firstName = updatedByUser.getFirstName() != null ? updatedByUser.getFirstName() : "";
                String lastName = updatedByUser.getLastName() != null ? updatedByUser.getLastName() : "";
                updatedByName = firstName + " " + lastName;
                updatedByName = updatedByName.trim();
            }
        }

        // Determine if any menu is assigned to this restaurant (for "Menu Published" flag)
        boolean menuPublished = restaurantMenuMappingRepository.existsById_RestaurantIdOptimized(restaurant.getId()) != null;

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
            .countryName(getCountryNameFromChainConfig())
            .tableQrCodeType(restaurant.getTableQrCodeType())
            .paymentQrUrl(signedPaymentQrUrl)
            .status(restaurant.getStatus())
            .createdAt(restaurant.getCreatedAt() != null ? restaurant.getCreatedAt().toLocalDateTime() : null)
            .updatedAt(restaurant.getUpdatedAt() != null ? restaurant.getUpdatedAt().toLocalDateTime() : null)
            .createdBy(createdByName)
            .updatedBy(updatedByName)
            .restaurantGroupId(restaurant.getRestaurantGroup() != null ? restaurant.getRestaurantGroup().getId().toString() : null)
            .logoUrl(signedLogoUrl)
            .translations(translationDTOs)
            .isDeleted(restaurant.getIsDeleted())
            .restaurantGroupName(restaurant.getRestaurantGroupName())
            .employeeCount(userRepository.countByRestaurantIdAndIsDeletedFalse(restaurant.getId()))
            .seatingCapacity(restaurantTableRepository.getTotalSeatingCapacityByRestaurantId(restaurant.getId()))
            .menuPublished(menuPublished)
            .operatingHours(operatingHours)
            .restaurantGroupNames(groupTranslations)
            .gstNumber(restaurant.getGstNumber())
            .salesAlertThreshold(restaurant.getSalesAlertThreshold())
            .refundAlertPercentage(restaurant.getRefundAlertPercentage())
            .cancellationAlertPercentage(restaurant.getCancellationAlertPercentage())
            .alertsEnabled(restaurant.getAlertsEnabled())
            .phoneNumber(restaurant.getPhoneNumber())
            .build();
    }

    private static String normalizeOptionalString(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private String getCountryNameFromChainConfig() {
        if (restaurantChainConfigProperties == null || restaurantChainConfigProperties.getChain() == null) {
            return null;
        }
        return restaurantChainConfigProperties.getChain().getCountryName();
    }

    /**
     * Checks if a restaurant code or GST number is unique.
     * Excludes the specified ID from the uniqueness check (for update operations).
     *
     * @param type      the type of code to check ("restaurantCode" or "gstNumber")
     * @param value     the code value to check
     * @param excludeId optional UUID to exclude from uniqueness check
     * @param locale    locale code for localized error messages
     * @return ResponseDto containing uniqueness check result
     */
    @Override
    public ResponseDto<CodeUniquenessResponse> checkCodeUniqueness(String type, String value, UUID excludeId, String locale) {
        Locale userLocale = LocaleContextHolder.getLocale();
        if (locale != null && !locale.isEmpty()) {
            try {
                userLocale = new Locale(locale);
            } catch (Exception e) {
                logger.warn("Invalid locale: {}, using default", locale);
            }
        }

        // Validate type parameter
        if (type == null || type.trim().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    messageSource.getMessage("validation.error.type.required", null, userLocale));
        }

        String normalizedType = type.trim().toLowerCase();
        if (!"user_code".equals(normalizedType) && !"restaurant_code".equals(normalizedType)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    messageSource.getMessage("validation.error.invalid.type", null, userLocale));
        }

        // Validate value parameter
        if (value == null || value.trim().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    messageSource.getMessage("validation.error.value.required", null, userLocale));
        }

        String normalizedValue = value.trim().toLowerCase();
        boolean isAvailable = false;

        if ("user_code".equals(normalizedType)) {
            // Check user code uniqueness (case-insensitive)
            boolean codeExists = userRepository.existsByUserCodeIgnoreCase(normalizedValue);
            
            if (excludeId != null) {
                // For update scenario: check if code exists excluding the current user
                if (codeExists) {
                    // Code exists, check if it belongs to the user being updated
                    Optional<User> currentUser = userRepository.findById(excludeId);
                    if (currentUser.isPresent()) {
                        String currentUserCode = currentUser.get().getUserCode();
                        // If the current user already has this code (case-insensitive), it's available
                        isAvailable = currentUserCode != null && 
                                      currentUserCode.trim().toLowerCase().equals(normalizedValue);
                    } else {
                        // User not found, code is taken by someone else
                        isAvailable = false;
                    }
                } else {
                    // Code doesn't exist, it's available
                    isAvailable = true;
                }
            } else {
                // For create scenario: code is available if it doesn't exist
                isAvailable = !codeExists;
            }
        } else if ("restaurant_code".equals(normalizedType)) {
            // Check restaurant code uniqueness (case-insensitive)
            if (excludeId != null) {
                // For update scenario: check if code exists excluding the current restaurant
                Optional<Restaurant> existingRestaurant = restaurantRepository.findByRestaurantCode(normalizedValue);
                if (existingRestaurant.isPresent() && !existingRestaurant.get().getId().equals(excludeId)) {
                    isAvailable = false;
                } else {
                    isAvailable = true;
                }
            } else {
                // For create scenario: check if code exists (including deleted ones, as per existing logic)
                isAvailable = !restaurantRepository.existsByRestaurantCode(normalizedValue);
            }
        }

        CodeUniquenessResponse response = CodeUniquenessResponse.builder()
                .isAvailable(isAvailable)
                .type(normalizedType)
                .value(value.trim())
                .build();

        String message = isAvailable 
                ? messageSource.getMessage("validation.code.available", null, userLocale)
                : messageSource.getMessage("validation.code.exists", null, userLocale);

        return ResponseDto.<CodeUniquenessResponse>builder()
                .message(message)
                .data(response)
                .build();
    }

    /**
     * Sets the reset times from application.properties (chain-level defaults) to the restaurant.
     * This ensures all new restaurants inherit the chain-level reset time configuration.
     */
    private void setResetTimesFromConfig(Restaurant restaurant) {
        try {
            if (restaurantChainConfigProperties == null) {
                logger.error("RestaurantChainConfigProperties is null! Cannot set reset times from config.");
                return;
            }
            
            RestaurantChainConfigProperties.RestaurantChainData config = restaurantChainConfigProperties.getChain();
            
            if (config == null) {
                logger.error("RestaurantChainData is null! Cannot set reset times from config.");
                return;
            }
            
            logger.info("Setting reset times from config. KDS: {}, Cashier: {}", 
                    config.getKdsLiveDashboardResetTime(), 
                    config.getCashierLiveDashboardResetTime());
            
            // Parse and set KDS reset time
            setKdsResetTimeFromConfig(restaurant, config);
            
            // Parse and set Cashier reset time
            setCashierResetTimeFromConfig(restaurant, config);
        } catch (Exception e) {
            logger.error("Error setting reset times from config for restaurant: {}", 
                    restaurant.getRestaurantCode(), e);
            // Don't throw - allow restaurant creation to proceed even if reset time setting fails
        }
    }
    
    /**
     * Parses a reset time string (supports both "HH:mm" and "HH:mm:ss+00:00" formats) 
     * and converts it to UTC OffsetTime.
     */
    private OffsetTime parseResetTime(String resetTimeStr) {
        try {
            return parseOffsetTime(resetTimeStr);
        } catch (Exception e) {
            // Fallback: Try parsing as LocalTime (old format: "03:00") and convert to UTC
            return parseLocalTimeAsOffsetTime(resetTimeStr);
        }
    }
    
    private OffsetTime parseOffsetTime(String resetTimeStr) {
        OffsetTime offsetTime = OffsetTime.parse(resetTimeStr);
        // Ensure UTC timezone
        return offsetTime.withOffsetSameInstant(ZoneOffset.UTC);
    }
    
    private OffsetTime parseLocalTimeAsOffsetTime(String resetTimeStr) {
        try {
            java.time.LocalTime localTime = java.time.LocalTime.parse(resetTimeStr);
            return localTime.atOffset(ZoneOffset.UTC);
        } catch (Exception e) {
            logger.error("Failed to parse reset time: {}", resetTimeStr, e);
            throw new IllegalArgumentException("Invalid reset time format: " + resetTimeStr, e);
        }
    }
    
    /**
     * Sets the KDS (Kitchen Display System) live dashboard reset time for a restaurant
     * from the restaurant chain configuration. Parses the reset time string and sets it
     * on the restaurant entity if valid, logging errors if parsing fails.
     *
     * @param restaurant the restaurant entity to update
     * @param config     the restaurant chain configuration data containing the reset time
     */
    private void setKdsResetTimeFromConfig(Restaurant restaurant, RestaurantChainConfigProperties.RestaurantChainData config) {
        if (config.getKdsLiveDashboardResetTime() != null && !config.getKdsLiveDashboardResetTime().isBlank()) {
            try {
                OffsetTime kdsResetTime = parseResetTime(config.getKdsLiveDashboardResetTime());
                restaurant.setKdsLiveDashboardResetTime(kdsResetTime);
                logger.info("Successfully set KDS reset time from config: {} for restaurant: {}", 
                        kdsResetTime, restaurant.getRestaurantCode());
            } catch (Exception e) {
                logger.error("Failed to parse KDS reset time from config: {}. Error: {}", 
                        config.getKdsLiveDashboardResetTime(), e.getMessage(), e);
            }
        } else {
            logger.warn("KDS reset time is null or blank in config. Skipping.");
        }
    }
    
    /**
     * Sets the Cashier live dashboard reset time for a restaurant from the restaurant
     * chain configuration. Parses the reset time string and sets it on the restaurant
     * entity if valid, logging errors if parsing fails.
     *
     * @param restaurant the restaurant entity to update
     * @param config     the restaurant chain configuration data containing the reset time
     */
    private void setCashierResetTimeFromConfig(Restaurant restaurant, RestaurantChainConfigProperties.RestaurantChainData config) {
        if (config.getCashierLiveDashboardResetTime() != null && !config.getCashierLiveDashboardResetTime().isBlank()) {
            try {
                OffsetTime cashierResetTime = parseResetTime(config.getCashierLiveDashboardResetTime());
                restaurant.setCashierLiveDashboardResetTime(cashierResetTime);
                logger.info("Successfully set Cashier reset time from config: {} for restaurant: {}", 
                        cashierResetTime, restaurant.getRestaurantCode());
            } catch (Exception e) {
                logger.error("Failed to parse Cashier reset time from config: {}. Error: {}", 
                        config.getCashierLiveDashboardResetTime(), e.getMessage(), e);
            }
        } else {
            logger.warn("Cashier reset time is null or blank in config. Skipping.");
        }
    }

    /**
     * Retrieves account settings for a restaurant.
     * Includes item limits, packing charges, tax/service charge configurations, and dashboard reset times.
     *
     * @param restaurantId the UUID of the restaurant
     * @return ResponseDto containing restaurant account settings
     * @throws ResponseStatusException if restaurant not found or deleted
     */
    @Override
    @Transactional
    public ResponseDto<RestaurantAccountSettingsResponseDto> getRestaurantAccountSettings(UUID restaurantId) {
        Locale userLocale = LocaleContextHolder.getLocale();

        Restaurant restaurant = restaurantRepository.findByIdAndIsDeletedFalse(restaurantId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        messageSource.getMessage(MSG_RESTAURANT_NOT_FOUND, new Object[]{restaurantId}, userLocale)));

        RestaurantAccountSettingsDto settingsDto = RestaurantAccountSettingsDto.builder()
                .kdsLiveDashboardResetTime(restaurant.getKdsLiveDashboardResetTime())
                .cashierLiveDashboardResetTime(restaurant.getCashierLiveDashboardResetTime())
                .build();

        RestaurantAccountSettingsResponseDto responseDto = RestaurantAccountSettingsResponseDto.builder()
                .accountSetting(settingsDto)
                .build();

        return ResponseDto.<RestaurantAccountSettingsResponseDto>builder()
                .message(messageSource.getMessage("restaurant.account.settings.get.success", null, userLocale))
                .data(responseDto)
                .build();
    }

    /**
     * Updates account settings for a restaurant.
     * Updates item limits, packing charges, tax/service charge configurations, and dashboard reset times.
     * Clears cache entries.
     *
     * @param restaurantId the UUID of the restaurant
     * @param request      the update request with new account settings
     * @param userId       the ID of the user performing the update
     * @return ResponseDto containing updated restaurant account settings
     * @throws ResponseStatusException if restaurant not found or deleted
     */
    @Override
    @Transactional
    @CacheEvict(value = "restaurants", allEntries = true)
    public ResponseDto<RestaurantAccountSettingsResponseDto> updateRestaurantAccountSettings(
            UUID restaurantId, 
            UpdateRestaurantAccountSettingsRequest request, 
            String userId) {
        Locale userLocale = LocaleContextHolder.getLocale();

        Restaurant restaurant = restaurantRepository.findByIdAndIsDeletedFalse(restaurantId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        messageSource.getMessage(MSG_RESTAURANT_NOT_FOUND, new Object[]{restaurantId}, userLocale)));

        // Update KDS reset time if provided
        // Save UTC values as-is from frontend (no conversion)
        OffsetTime oldKdsResetTime = restaurant.getKdsLiveDashboardResetTime();
        if (request.getKdsLiveDashboardResetTime() != null) {
            OffsetTime kdsResetTime = request.getKdsLiveDashboardResetTime();
            restaurant.setKdsLiveDashboardResetTime(kdsResetTime);
            logger.info("Updated KDS reset time for restaurant {}: {}", restaurant.getRestaurantCode(), kdsResetTime);
            
            // Create audit trail for KDS reset time extend
            try {
                User user = userRepository.findById(UUID.fromString(userId))
                        .orElseThrow(() -> new EntityNotFoundException(ERROR_USER_NOT_FOUND_WITH_ID + userId));
                String oldKdsResetTimeStr = oldKdsResetTime != null ? oldKdsResetTime.toString() : "N/A";
                String auditMessage = String.format("KDS reset time changed from %s to %s", 
                        oldKdsResetTimeStr, kdsResetTime.toString());
                auditTrailService.createAuditTrail(
                        user,
                        ActionType.KDS_RESET_TIME_EXTEND,
                        restaurant,
                        RequestStatus.NA,
                        null, // ipAddress
                        null, // userAgent
                        restaurant.getId(),
                        ENTITY_TYPE_RESTAURANT,
                        auditMessage
                );
            } catch (Exception e) {
                logger.error("Failed to create audit trail for KDS reset time extend: {}", e.getMessage());
            }
        }

        // Update Cashier reset time if provided
        // Save UTC values as-is from frontend (no conversion)
        if (request.getCashierLiveDashboardResetTime() != null) {
            OffsetTime cashierResetTime = request.getCashierLiveDashboardResetTime();
            restaurant.setCashierLiveDashboardResetTime(cashierResetTime);
            logger.info("Updated Cashier reset time for restaurant {}: {}", restaurant.getRestaurantCode(), cashierResetTime);
        }

        // Set updatedBy
        User user = userRepository.findById(UUID.fromString(userId))
                .orElseThrow(() -> new EntityNotFoundException(ERROR_USER_NOT_FOUND_WITH_ID + userId));
        restaurant.setUpdatedBy(user);
        restaurant.setUpdatedAt(OffsetDateTime.now(ZoneOffset.UTC));

        restaurant = restaurantRepository.save(restaurant);

        RestaurantAccountSettingsDto settingsDto = RestaurantAccountSettingsDto.builder()
                .kdsLiveDashboardResetTime(restaurant.getKdsLiveDashboardResetTime())
                .cashierLiveDashboardResetTime(restaurant.getCashierLiveDashboardResetTime())
                .build();

        RestaurantAccountSettingsResponseDto responseDto = RestaurantAccountSettingsResponseDto.builder()
                .accountSetting(settingsDto)
                .build();

        return ResponseDto.<RestaurantAccountSettingsResponseDto>builder()
                .message(messageSource.getMessage("restaurant.account.settings.update.success", null, userLocale))
                .data(responseDto)
                .build();
    }

    /**
     * Restores one or more soft-deleted restaurants by setting isDeleted flag to false.
     * Only restores restaurants that are currently deleted. Updates updatedBy and updatedAt fields.
     * Clears cache entries.
     *
     * @param ids    list of restaurant UUIDs to restore
     * @param userId the ID of the user performing the restore
     * @return ResponseDto with success message
     * @throws ResponseStatusException if user not found, restaurants not found, or no deleted restaurants to restore
     */
    @Override
    @Transactional
    @CacheEvict(value = {"restaurants", "restaurantGroupsLite"}, allEntries = true)
    public ResponseDto<Void> restoreRestaurants(List<UUID> ids, String userId) {
        Locale userLocale = LocaleContextHolder.getLocale();
        
        // Find user for updatedBy
        User user = userRepository.findById(UUID.fromString(userId))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, 
                    messageSource.getMessage(MSG_USER_NOT_FOUND, new Object[]{userId}, userLocale)));
        
        // Find all restaurants by IDs
        List<Restaurant> restaurants = restaurantRepository.findAllById(ids);
        
        if (restaurants.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, 
                messageSource.getMessage(MSG_RESTAURANT_NOT_FOUND, null, userLocale));
        }
        
        // Filter only deleted restaurants and restore them
        List<Restaurant> deletedRestaurants = restaurants.stream()
                .filter(r -> Boolean.TRUE.equals(r.getIsDeleted()))
                .collect(Collectors.toList());
        
        if (deletedRestaurants.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, 
                messageSource.getMessage("restaurant.group.restore.error.not.deleted", null, userLocale));
        }
        
        // Restore all deleted restaurants
        for (Restaurant restaurant : deletedRestaurants) {
            restaurant.setIsDeleted(false);
            restaurant.setUpdatedAt(OffsetDateTime.now(ZoneOffset.UTC));
            restaurant.setUpdatedBy(user);
        }
        
        restaurantRepository.saveAll(deletedRestaurants);
        
        return ResponseDto.<Void>builder()
            .message(messageSource.getMessage("restaurant.restore.success", null, userLocale))
            .build();
    }

    /**
     * Creates a virtual section with rows and tables for a newly created restaurant.
     * This virtual section is used for virtual/delivery orders that don't require a physical table.
     * 
     * @param restaurant The restaurant for which to create the virtual section
     * @param creator The user creating the virtual section
     */
    private void createVirtualSectionForRestaurant(Restaurant restaurant, User creator) {
        logger.info("Creating virtual section for restaurant: {}", restaurant.getRestaurantCode());

        // Create or get restaurant layout
        RestaurantLayout restaurantLayout = restaurantLayoutRepository.findByRestaurantIdAndIsDeletedFalse(restaurant.getId())
                .orElseGet(() -> {
                    RestaurantLayout newLayout = new RestaurantLayout();
                    newLayout.setRestaurant(restaurant);
                    newLayout.setCreatedAt(OffsetDateTime.now(ZoneOffset.UTC));
                    newLayout.setCreatedBy(creator);
                    newLayout.setIsDeleted(false);
                    newLayout.setStatus(EntityStatus.ACTIVE);
                    return restaurantLayoutRepository.save(newLayout);
                });

        // Check if virtual section already exists
        boolean virtualSectionExists = restaurantLayout.getSections().stream()
                .anyMatch(section -> {
                    if (Boolean.TRUE.equals(section.getIsDeleted())) {
                        return false;
                    }
                    // Check if any table in this section is virtual
                    return section.getRows().stream()
                            .filter(row -> !Boolean.TRUE.equals(row.getIsDeleted()))
                            .flatMap(row -> row.getTables().stream())
                            .anyMatch(table -> Boolean.TRUE.equals(table.getIsVirtual()) && !Boolean.TRUE.equals(table.getIsDeleted()));
                });

        if (virtualSectionExists) {
            logger.info("Virtual section already exists for restaurant: {}", restaurant.getRestaurantCode());
            return;
        }

        // Create virtual section
        RestaurantSection virtualSection = new RestaurantSection();
        virtualSection.setRestaurantLayout(restaurantLayout);
        virtualSection.setSectionOrder(9999); // High order to place it last
        virtualSection.setCreatedAt(OffsetDateTime.now(ZoneOffset.UTC));
        virtualSection.setCreatedBy(creator);
        virtualSection.setIsDeleted(false);

        // Create translations for virtual section in all supported languages
        List<String> supportedLanguages = localizationProperties.getLanguages();
        List<RestaurantSectionTranslation> translations = new ArrayList<>();
        for (String langCode : supportedLanguages) {
            RestaurantSectionTranslation translation = new RestaurantSectionTranslation();
            translation.setRestaurantSection(virtualSection);
            translation.setLanguageCode(langCode);
            // Get section name from message source using locale
            Locale locale = new Locale(langCode);
            String sectionName = messageSource.getMessage(MSG_VIRTUAL_SECTION_NAME, null, locale);
            translation.setName(sectionName);
            translations.add(translation);
        }
        virtualSection.setTranslations(translations);

        // Create virtual row
        RestaurantRow virtualRow = new RestaurantRow();
        virtualRow.setRestaurantSection(virtualSection);
        virtualRow.setRowOrder(0);
        virtualRow.setCreatedAt(OffsetDateTime.now(ZoneOffset.UTC));
        virtualRow.setCreatedBy(creator);
        virtualRow.setIsDeleted(false);

        // Create virtual table
        RestaurantTable virtualTable = new RestaurantTable();
        virtualTable.setRestaurantRow(virtualRow);
        virtualTable.setTableOrder(0);
        virtualTable.setShape(TableShape.SQUARE); // Default shape
        virtualTable.setCapacity(0); // Virtual table has no capacity
        virtualTable.setTableCode(messageSource.getMessage(MSG_VIRTUAL_TABLE_CODE_TAKEAWAY, null, LocaleContextHolder.getLocale())); // Default table code
        virtualTable.setTableStatus(TableStatus.BLOCKED);
        virtualTable.setIsVirtual(true);
        virtualTable.setCreatedAt(OffsetDateTime.now(ZoneOffset.UTC));
        virtualTable.setCreatedBy(creator);
        virtualTable.setIsDeleted(false);

        // Set row's tables
        virtualRow.setTables(new ArrayList<>(Arrays.asList(virtualTable)));

        // Set section's rows
        virtualSection.setRows(new ArrayList<>(Arrays.asList(virtualRow)));

        // Add section to layout
        if (restaurantLayout.getSections() == null) {
            restaurantLayout.setSections(new ArrayList<>());
        }
        restaurantLayout.getSections().add(virtualSection);

        // Save layout (cascade will save section, row, and table)
        restaurantLayout = restaurantLayoutRepository.save(restaurantLayout);
        restaurantLayoutRepository.flush();

        // Generate QR code for virtual table if using STATIC QR code type
        if (restaurantChainConfigProperties.getChain().getQrCodeType() == QrCodeType.STATIC) {
            generateQrCodeForVirtualTable(restaurant.getId());
        }

        logger.info("Successfully created virtual section for restaurant: {}", restaurant.getRestaurantCode());
    }

    /**
     * Reset password for waiter when assigned to restaurant and send credentials to manager
     */
    private void resetWaiterPasswordOnRestaurantAssignment(User waiter, UUID restaurantId, Locale userLocale) {
        try {
            // Check if required dependencies are available
            if (passwordEncoder == null || emailSender == null || loginAuditRepository == null || 
                roleRepository == null || messageUtil == null) {
                logger.warn("Required dependencies not available for waiter password reset. Skipping password reset for waiter {}", waiter.getUserCode());
                return;
            }

            // Check if manager exists for the restaurant
            Optional<com.gulfnet.shared_library.entity.Role> managerRoleOpt = roleRepository.findByName(ROLE_MANAGER);
            if (managerRoleOpt.isEmpty()) {
                logger.warn("MANAGER role not found in database. Cannot reset waiter password.");
                return;
            }

            UUID managerRoleId = managerRoleOpt.get().getId();
            List<User> managers = userRepository.findAllByRestaurantIdAndRoleIdAndIsDeletedFalse(
                    restaurantId, managerRoleId);

            // Generate new password
            String newPassword = PasswordGeneratorUtil.generatePassword(12);

            // Update waiter's password
            waiter.setPassword(passwordEncoder.encode(newPassword));
            waiter.setUpdatedAt(OffsetDateTime.now(ZoneOffset.UTC));
            userRepository.save(waiter);

            // Invalidate all sessions for the waiter
            loginAuditRepository.deleteByUser_Id(waiter.getId());

            // Always email the waiter their credentials (if they have an email)
            try {
                String waiterEmail = waiter.getEmail();
                if (waiterEmail != null && !waiterEmail.trim().isEmpty()) {
                    Locale waiterLocale = resolvePreferredLocale(waiter, userLocale);
                    String subject = messageUtil.getMessage(MSG_WAITER_RESTAURANT_ASSIGNMENT_EMAIL_SUBJECT, waiterLocale);
                    String htmlBody = buildWaiterAssignmentEmailBodyForEmployee(waiter, newPassword, waiterLocale);
                    if (htmlBody != null && !htmlBody.trim().isEmpty()) {
                        emailSender.sendEmail(waiterEmail, subject, htmlBody);
                        logger.info("Sent waiter assignment credentials to waiter: {}", waiterEmail);
                    } else {
                        logger.warn("Waiter assignment email body could not be built. Skipping email to waiter {}", waiter.getId());
                    }
                } else {
                    logger.warn("Waiter {} has no email address. Cannot send credentials to waiter.", waiter.getId());
                }
            } catch (Exception e) {
                logger.error("Failed to send waiter assignment email to waiter {}: {}", waiter.getId(), e.getMessage(), e);
            }

            // Collect unique manager emails (deduplicate)
            Set<String> uniqueManagerEmails = new LinkedHashSet<>();
            int duplicateCount = 0;
            for (User manager : managers) {
                if (manager.getEmail() != null && !manager.getEmail().trim().isEmpty()) {
                    boolean wasNew = uniqueManagerEmails.add(manager.getEmail());
                    if (!wasNew) {
                        duplicateCount++;
                    }
                }
            }

            if (duplicateCount > 0) {
                logger.info("Found {} manager(s) for restaurant {}. Filtered {} duplicate email(s).",
                        managers.size(), restaurantId, duplicateCount);
            }

            // Send email per manager using each manager's preferred language (if set).
            // This matches user-management behavior and ensures multi-language manager lists are handled correctly.
            boolean emailSent = false;
            Set<String> emailed = new LinkedHashSet<>();
            for (User manager : managers) {
                String managerEmail = manager.getEmail();
                if (managerEmail == null || managerEmail.trim().isEmpty()) {
                    continue;
                }
                if (!emailed.add(managerEmail)) {
                    continue; // avoid duplicate sends
                }
                try {
                    Locale managerLocale = resolvePreferredLocale(manager, userLocale);
                    String subject = messageUtil.getMessage(MSG_WAITER_RESTAURANT_ASSIGNMENT_EMAIL_SUBJECT, managerLocale);
                    String htmlBody = buildWaiterAssignmentEmailBody(waiter, newPassword, managerLocale, safeDisplayName(manager, managerLocale));
                    emailSender.sendEmail(managerEmail, subject, htmlBody);
                    logger.info("Sent waiter assignment credentials to manager: {}", managerEmail);
                    emailSent = true;
                } catch (Exception e) {
                    logger.error("Failed to send waiter assignment email to manager {}: {}",
                            managerEmail, e.getMessage(), e);
                }
            }

            // Fallback to default email if no manager emails were sent
            if (!emailSent) {
                Locale fallbackLocale = userLocale != null ? userLocale : Locale.ENGLISH;
                String subject = messageUtil.getMessage(MSG_WAITER_RESTAURANT_ASSIGNMENT_EMAIL_SUBJECT, fallbackLocale);
                String htmlBody = buildWaiterAssignmentEmailBody(waiter, newPassword, fallbackLocale, messageUtil.getMessage(MSG_USER_REGISTRATION_EMAIL_TEAM, fallbackLocale));
                sendWaiterAssignmentEmailToHqAdmins(subject, htmlBody);
            }

        } catch (Exception e) {
            logger.error("Failed to reset waiter password on restaurant assignment: {}", e.getMessage(), e);
            // Don't fail the user update if password reset fails
        }
    }

    private Locale resolvePreferredLocale(User user, Locale fallbackLocale) {
        try {
            if (user != null && user.getLanguageCode() != null && !user.getLanguageCode().trim().isEmpty()) {
                return Locale.forLanguageTag(user.getLanguageCode().trim());
            }
        } catch (Exception ignored) {
            // Keep fallback locale.
        }
        return fallbackLocale != null ? fallbackLocale : Locale.ENGLISH;
    }

    /**
     * Build HTML email body for waiter restaurant assignment
     */
    private String buildWaiterAssignmentEmailBody(User waiter, String newPassword, Locale userLocale, String recipientDisplayName) {
        if (messageUtil == null) {
            // Fallback if messageUtil is not available
            return "<html><body>"
                    + "<p>Dear Manager,</p>"
                    + "<p>A waiter has been assigned to your restaurant:</p>"
                    + "<p><b>Name:</b> " + waiter.getFirstName()
                    + (waiter.getLastName() != null ? " " + waiter.getLastName() : "") + "</p>"
                    + "<p><b>User Code:</b> " + waiter.getUserCode() + "</p>"
                    + "<p><b>New Password:</b> " + newPassword + "</p>"
                    + "<p>Please share these credentials with the waiter.</p>"
                    + "<p>Regards,<br>Team</p>"
                    + "</body></html>";
        }

        // Use an email-client friendly "card" layout (table-based) with inline styles.
        // Localization keys remain unchanged.
        String firstName = waiter.getFirstName() != null ? waiter.getFirstName() : "";
        String lastName = waiter.getLastName() != null ? waiter.getLastName() : "";
        boolean isJapanese = userLocale != null && "ja".equalsIgnoreCase(userLocale.getLanguage());
        String safeWaiterName = isJapanese
                ? ((lastName + " " + firstName).trim())
                : ((firstName + " " + lastName).trim());

        String safeUserCode = waiter.getUserCode() != null ? waiter.getUserCode() : "";
        String safeNewPassword = newPassword != null ? newPassword : "";

        return ""
                + "<!DOCTYPE html>"
                + "<html>"
                + "<body style=\"margin:0;padding:16px 0;background:#f3f4f6;font-family:Arial,Helvetica,sans-serif;\">"
                + AssignmentEmailHtml.TABLE_PRESENTATION_BORDER_COLLAPSE
                + "<tr>"
                + "<td align=\"center\">"
                + "<table role=\"presentation\" width=\"600\" cellpadding=\"0\" cellspacing=\"0\" "
                + "style=\"max-width:600px;width:100%;background:#ffffff;border-radius:14px;"
                + "border:1px solid #e5e7eb;overflow:hidden;\">"
                + "<tr>"
                + "<td style=\"background:#2563eb;height:10px;\">&nbsp;</td>"
                + AssignmentEmailHtml.TR_CLOSE
                + "<tr>"
                + "<td style=\"padding:20px 24px 8px 24px;\">"
                + "<div style=\"font-size:18px;color:#111827;font-weight:700;line-height:24px;\">"
                + messageUtil.getMessage("user.registration.email.manager.greeting", userLocale, (recipientDisplayName != null ? recipientDisplayName : ""))
                + AssignmentEmailHtml.DIV_CLOSE
                + AssignmentEmailHtml.TD_CLOSE
                + AssignmentEmailHtml.TR_CLOSE
                + "<tr>"
                + "<td style=\"padding:0 24px 24px 24px;\">"
                + "<table role=\"presentation\" width=\"100%\" cellpadding=\"0\" cellspacing=\"0\" "
                + "style=\"background:#f9fafb;border:1px solid #e5e7eb;border-radius:12px;\">"
                + "<tr>"
                + "<td style=\"padding:14px 16px;font-size:14px;color:#111827;line-height:22px;"
                + "word-break:break-word;overflow-wrap:anywhere;\">"
                + "<p style=\"margin:0 0 12px;\">A waiter has been assigned to your restaurant:</p>"
                + AssignmentEmailHtml.TABLE_PRESENTATION_BORDER_COLLAPSE
                + "<tr>"
                + "<td style=\"padding:4px 0;color:#6b7280;font-size:13px;font-weight:700;\">Name:</td>"
                + AssignmentEmailHtml.TD_VALUE_ALIGN_RIGHT_13
                + (safeWaiterName.isEmpty() ? safeUserCode : safeWaiterName)
                + AssignmentEmailHtml.TD_CLOSE
                + AssignmentEmailHtml.TR_CLOSE
                + "<tr>"
                + "<td style=\"padding:4px 0;color:#6b7280;font-size:13px;font-weight:700;\">User Code:</td>"
                + AssignmentEmailHtml.TD_VALUE_ALIGN_RIGHT_13
                + safeUserCode
                + AssignmentEmailHtml.TD_CLOSE
                + AssignmentEmailHtml.TR_CLOSE
                + "<tr>"
                + "<td style=\"padding:4px 0;color:#6b7280;font-size:13px;font-weight:700;\">New Password:</td>"
                + "<td align=\"right\" style=\"padding:4px 0;color:#111827;font-size:13px;font-weight:700;font-family:Courier New,Courier,monospace;\">"
                + safeNewPassword
                + AssignmentEmailHtml.TD_CLOSE
                + AssignmentEmailHtml.TR_CLOSE
                + AssignmentEmailHtml.TABLE_CLOSE
                + "<p style=\"margin:14px 0 0;\">Please share these credentials with the waiter.</p>"
                + "<p style=\"margin:14px 0 0;\">"
                + messageUtil.getMessage(MSG_EMAIL_RECEIPT_REGARDS, userLocale) + "<br>"
                + messageUtil.getMessage(MSG_USER_REGISTRATION_EMAIL_TEAM, userLocale) + "</p>"
                + AssignmentEmailHtml.TD_CLOSE
                + AssignmentEmailHtml.TR_CLOSE
                + AssignmentEmailHtml.TABLE_CLOSE
                + AssignmentEmailHtml.TD_CLOSE
                + AssignmentEmailHtml.TR_CLOSE
                + AssignmentEmailHtml.TABLE_CLOSE
                + AssignmentEmailHtml.TD_CLOSE
                + AssignmentEmailHtml.TR_CLOSE
                + AssignmentEmailHtml.TABLE_CLOSE
                + "</body>"
                + "</html>";
    }

    /**
     * Updates the audit trail restaurant ID after transaction commit.
     * Handles both transactional and non-transactional scenarios.
     */
    private void updateAuditTrailRestaurantIdAfterCommit(UUID entityId, UUID restaurantId) {
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    updateAuditTrailRestaurantIdSafely(entityId, restaurantId, "after commit");
                }
            });
        } else {
            updateAuditTrailRestaurantIdSafely(entityId, restaurantId, "");
        }
    }

    /**
     * Safely updates audit trail restaurant ID with error handling.
     */
    private void updateAuditTrailRestaurantIdSafely(UUID entityId, UUID restaurantId, String context) {
        try {
            auditTrailService.updateAuditTrailRestaurantId(
                    entityId, 
                    restaurantId, 
                    ActionType.RESTAURANT_CREATE
            );
        } catch (Exception updateEx) {
            String errorMessage = context.isEmpty() 
                    ? "Failed to update restaurant_id for audit trail: {}"
                    : "Failed to update restaurant_id for audit trail " + context + ": {}";
            logger.error(errorMessage, updateEx.getMessage());
        }
    }

    /**
     * Handles waiter password reset when assigned to a restaurant.
     */
    private void handleWaiterPasswordResetOnAssignment(User savedEmployee, UUID existingRoleId, UUID restaurantId) {
        try {
            com.gulfnet.shared_library.entity.Role employeeRole = roleRepository.findById(existingRoleId).orElse(null);
            if (employeeRole != null && ROLE_WAITER.equals(employeeRole.getName())) {
                resetWaiterPasswordOnRestaurantAssignment(savedEmployee, restaurantId, LocaleContextHolder.getLocale());
            }
        } catch (Exception e) {
            logger.error("Failed to reset waiter password on restaurant assignment: {}", e.getMessage(), e);
            // Don't fail the assignment if password reset fails
        }
    }

    /**
     * Notifies managers when a new employee is assigned to a restaurant.
     */
    private void notifyManagersOfEmployeeAssignment(User savedEmployee, Restaurant restaurant) {
        try {
            // Find MANAGER role
            java.util.Optional<com.gulfnet.shared_library.entity.Role> managerRoleOpt = roleRepository.findByName(ROLE_MANAGER);
            if (managerRoleOpt.isPresent()) {
                UUID managerRoleId = managerRoleOpt.get().getId();
                List<User> managers = userRepository.findAllByRestaurantIdAndRoleIdAndIsDeletedFalse(
                        restaurant.getId(), managerRoleId)
                        .stream()
                        .filter(u -> u.getStatus() == EntityStatus.ACTIVE)
                        .collect(java.util.stream.Collectors.toList());
                
                if (!managers.isEmpty()) {
                    // Pass the current locale as fallback - the notification service will use each manager's preferred language
                    // This ensures each manager receives the notification in their own language preference
                    Locale fallbackLocale = LocaleContextHolder.getLocale();
                    notificationService.notifyEmployeeAssignedToRestaurant(
                            savedEmployee, restaurant, managers, fallbackLocale);
                    logger.info("Sent employee assigned notification to {} managers for restaurant {}", 
                            managers.size(), restaurant.getId());
                }
            }
        } catch (Exception e) {
            logger.error("Failed to send employee assigned notification: {}", e.getMessage(), e);
            // Don't fail the assignment if notification fails
        }
    }

    /**
     * Generates QR code for virtual table if using STATIC QR code type.
     */
    private void generateQrCodeForVirtualTable(UUID restaurantId) {
        try {
            // Reload the table from database to ensure it's managed and has an ID
            // After cascade save, the entity might be detached, so we need to find it from the saved layout
            restaurantLayoutRepository.flush(); // Ensure the table is persisted and has an ID
            
            // Find the virtual table from the saved layout (it should have an ID after flush)
            Optional<RestaurantTable> tableOptional = restaurantTableRepository.findVirtualTableByRestaurantId(restaurantId);
            if (tableOptional.isPresent()) {
                RestaurantTable managedVirtualTable = tableOptional.get();
                restaurantLayoutServiceImpl.generateAndUploadQrForVirtualTable(restaurantId, managedVirtualTable);
                // Save the table to persist the QR code URL
                restaurantTableRepository.save(managedVirtualTable);
                logger.info("Generated QR code for virtual table: {}", managedVirtualTable.getId());
            } else {
                logger.error("Virtual table not found after save for restaurant: {}", restaurantId);
            }
        } catch (Exception e) {
            logger.error("Failed to generate QR code for virtual table for restaurant {}: {}", restaurantId, e.getMessage(), e);
            // Don't fail the entire process if QR generation fails
        }
    }

    /**
     * Sends waiter assignment emails to managers and falls back to default email if needed.
     */
    private void sendWaiterAssignmentEmails(Set<String> uniqueManagerEmails, String subject, String htmlBody) {
        boolean emailSent = false;
        for (String managerEmail : uniqueManagerEmails) {
            try {
                emailSender.sendEmail(managerEmail, subject, htmlBody);
                logger.info("Sent waiter assignment credentials to manager: {}", managerEmail);
                emailSent = true;
            } catch (Exception e) {
                logger.error("Failed to send waiter assignment email to manager {}: {}",
                        managerEmail, e.getMessage(), e);
            }
        }

        // Fallback to HQ Admin emails if no manager emails were sent
        if (!emailSent) {
            sendWaiterAssignmentEmailToHqAdmins(subject, htmlBody);
        }
    }

    /**
     * Sends waiter assignment email to all HQ Admin emails.
     */
    private void sendWaiterAssignmentEmailToHqAdmins(String subject, String htmlBody) {
        try {
            if (roleRepository == null || userRepository == null || emailSender == null) {
                logger.warn("RoleRepository/UserRepository/EmailSender not available - cannot send waiter assignment fallback email to HQ Admins");
                return;
            }

            Optional<com.gulfnet.shared_library.entity.Role> hqAdminRoleOpt = roleRepository.findByName("HQ_ADMIN");
            if (hqAdminRoleOpt.isEmpty()) {
                logger.warn("HQ_ADMIN role not found - cannot send waiter assignment fallback email to HQ Admins");
                return;
            }

            UUID hqRoleId = hqAdminRoleOpt.get().getId();
            List<User> hqAdmins = userRepository.findAllByRoleIdAndStatusAndIsDeletedFalse(hqRoleId, EntityStatus.ACTIVE);
            if (hqAdmins == null || hqAdmins.isEmpty()) {
                logger.warn("No HQ Admin users found - cannot send waiter assignment fallback email");
                return;
            }

            Set<String> emails = new LinkedHashSet<>();
            for (User hqAdmin : hqAdmins) {
                if (hqAdmin.getEmail() != null && !hqAdmin.getEmail().trim().isEmpty()) {
                    emails.add(hqAdmin.getEmail().trim());
                }
            }

            if (emails.isEmpty()) {
                logger.warn("HQ Admin users found but none have an email - cannot send waiter assignment fallback email");
                return;
            }

            for (String email : emails) {
                try {
                    emailSender.sendEmail(email, subject, htmlBody);
                    logger.info("Sent waiter assignment credentials fallback email to HQ Admin: {}", email);
                } catch (Exception e) {
                    logger.error("Failed to send waiter assignment fallback email to HQ Admin {}: {}", email, e.getMessage(), e);
                }
            }
        } catch (Exception e) {
            logger.error("Failed to send waiter assignment fallback email to HQ Admins: {}", e.getMessage(), e);
        }
    }

    /**
     * Sends HTML assignment notifications when {@code emailSender} and {@code messageUtil} are available:
     * first {@link #sendEmployeeAssignmentToEmployeeOrFallback}, then {@link #sendManagerAssignmentEmails}
     * for all active managers. Uses {@link LocaleContextHolder#getLocale()} as the default locale for copy.
     */
    private void sendAssignmentEmailsOnEmployeeAssigned(User employee, Restaurant restaurant) {
        try {
            if (employee == null || restaurant == null) {
                return;
            }
            if (emailSender == null) {
                return;
            }
            if (messageUtil == null) {
                logger.warn("MessageUtil not available - cannot send assignment emails for employee {}", employee.getId());
                return;
            }
            Locale fallbackLocale = LocaleContextHolder.getLocale();

            // Resolve role name for display (if possible)
            String roleName = resolveRoleName(employee);

            // Managers for this restaurant (active)
            List<User> managers = getActiveManagersForRestaurant(restaurant.getId());

            // 1) Send employee assignment email to employee OR to managers/HQ fallback if employee email missing
            boolean employeeEmailSent = sendEmployeeAssignmentToEmployeeOrFallback(employee, restaurant, roleName, managers, fallbackLocale);

            // 2) Managers always get email when any employee is assigned
            sendManagerAssignmentEmails(employee, restaurant, roleName, managers, fallbackLocale);

            if (!employeeEmailSent) {
                logger.info("Employee assignment primary email not sent for employee {} (likely missing email). Fallback attempted.", employee.getId());
            }
        } catch (Exception e) {
            logger.error("Failed to send assignment emails for employee {}: {}", employee != null ? employee.getId() : null, e.getMessage(), e);
        }
    }

    /**
     * Delivers the assignment email to the employee when an email address exists; otherwise emails each
     * active manager with manager-oriented copy, or {@link #sendEmployeeAssignmentFallbackToHqAdmins} when
     * no manager receives a message. Failed sends to the employee's own address do not trigger manager
     * fallback (returns {@code false}).
     *
     * @return {@code true} if mail was sent to the employee or at least one manager; {@code false} if only
     *         the HQ-admin fallback path ran (or nothing could be sent)
     */
    private boolean sendEmployeeAssignmentToEmployeeOrFallback(
            User employee,
            Restaurant restaurant,
            String roleName,
            List<User> managers,
            Locale fallbackLocale
    ) {
        String employeeEmail = employee.getEmail();
        if (employeeEmail != null && !employeeEmail.trim().isEmpty()) {
            Locale employeeLocale = resolvePreferredLocale(employee, fallbackLocale);
            String subject = messageUtil.getMessage("employee.restaurant.assignment.email.subject", employeeLocale);
            String html = buildAssignmentCardHtml(
                    employeeLocale,
                    messageUtil.getMessage("employee.restaurant.assignment.email.subject", employeeLocale),
                    messageUtil.getMessage("email.assignment.employee.intro", employeeLocale, safeRestaurantCode(restaurant)),
                    employee, // recipient
                    employee,
                    restaurant,
                    roleName
            );
            try {
                emailSender.sendEmail(employeeEmail.trim(), subject, html);
                return true;
            } catch (Exception e) {
                logger.error("Failed to send assignment email to employee {}: {}", employee.getId(), e.getMessage(), e);
                // If employee email exists but send fails, do not redirect credentials/info to others automatically.
                return false;
            }
        }

        // No employee email -> send to managers; if none, fallback to HQ admins
        boolean sentToAnyManager = false;
        if (managers != null && !managers.isEmpty()) {
            for (User manager : managers) {
                String to = manager.getEmail();
                if (to == null || to.trim().isEmpty()) {
                    continue;
                }
                Locale managerLocale = resolvePreferredLocale(manager, fallbackLocale);
                String subject = messageUtil.getMessage(MSG_EMAIL_ASSIGNMENT_MANAGER_SUBJECT, managerLocale);
                String html = buildAssignmentCardHtml(
                        managerLocale,
                        messageUtil.getMessage(MSG_EMAIL_ASSIGNMENT_MANAGER_TITLE, managerLocale),
                        messageUtil.getMessage(
                                MSG_EMAIL_ASSIGNMENT_MANAGER_INTRO,
                                managerLocale,
                                safeDisplayName(employee, managerLocale),
                                safeRestaurantCode(restaurant)
                        ),
                        manager, // recipient
                        employee,
                        restaurant,
                        roleName
                );
                try {
                    emailSender.sendEmail(to.trim(), subject, html);
                    sentToAnyManager = true;
                } catch (Exception e) {
                    logger.error("Failed to send employee-assignment fallback email to manager {}: {}", manager.getId(), e.getMessage(), e);
                }
            }
        }

        if (sentToAnyManager) {
            return true;
        }

        // Fallback to HQ admins
        sendEmployeeAssignmentFallbackToHqAdmins(employee, restaurant, roleName, fallbackLocale);
        return false;
    }

    /**
     * Emails each active {@code HQ_ADMIN} when the assignee has no email and no manager received the
     * credential-style assignment message. Uses the same HTML card layout as the manager fallback.
     */
    private void sendEmployeeAssignmentFallbackToHqAdmins(User employee, Restaurant restaurant, String roleName, Locale fallbackLocale) {
        try {
            List<User> hqAdmins = getActiveHqAdmins();
            if (hqAdmins == null || hqAdmins.isEmpty()) {
                logger.warn("No HQ Admin users found for employee assignment fallback email");
                return;
            }
            for (User hqAdmin : hqAdmins) {
                String to = hqAdmin.getEmail();
                if (to == null || to.trim().isEmpty()) {
                    continue;
                }
                Locale locale = resolvePreferredLocale(hqAdmin, fallbackLocale);
                String subject = messageUtil.getMessage(MSG_EMAIL_ASSIGNMENT_MANAGER_SUBJECT, locale);
                String html = buildAssignmentCardHtml(
                        locale,
                        messageUtil.getMessage(MSG_EMAIL_ASSIGNMENT_MANAGER_TITLE, locale),
                        messageUtil.getMessage(
                                MSG_EMAIL_ASSIGNMENT_MANAGER_INTRO,
                                locale,
                                safeDisplayName(employee, locale),
                                safeRestaurantCode(restaurant)
                        ),
                        hqAdmin, // recipient
                        employee,
                        restaurant,
                        roleName
                );
                try {
                    emailSender.sendEmail(to.trim(), subject, html);
                } catch (Exception e) {
                    logger.error("Failed to send employee-assignment fallback email to HQ Admin {}: {}", hqAdmin.getId(), e.getMessage(), e);
                }
            }
        } catch (Exception e) {
            logger.error("Failed to send employee-assignment fallback email to HQ Admins: {}", e.getMessage(), e);
        }
    }

    /**
     * Notifies every active restaurant manager (with a non-blank email) that {@code employee} was assigned,
     * using localized manager subject/title/intro and {@link #buildAssignmentCardHtml}.
     */
    private void sendManagerAssignmentEmails(User employee, Restaurant restaurant, String roleName, List<User> managers, Locale fallbackLocale) {
        if (managers == null || managers.isEmpty()) {
            return;
        }
        for (User manager : managers) {
            String to = manager.getEmail();
            if (to == null || to.trim().isEmpty()) {
                continue;
            }
            Locale managerLocale = resolvePreferredLocale(manager, fallbackLocale);
            String subject = messageUtil.getMessage(MSG_EMAIL_ASSIGNMENT_MANAGER_SUBJECT, managerLocale);
            String html = buildAssignmentCardHtml(
                    managerLocale,
                    messageUtil.getMessage(MSG_EMAIL_ASSIGNMENT_MANAGER_TITLE, managerLocale),
                    messageUtil.getMessage(
                            MSG_EMAIL_ASSIGNMENT_MANAGER_INTRO,
                            managerLocale,
                            safeDisplayName(employee, managerLocale),
                            safeRestaurantCode(restaurant)
                    ),
                    manager, // recipient
                    employee,
                    restaurant,
                    roleName
            );
            try {
                emailSender.sendEmail(to.trim(), subject, html);
            } catch (Exception e) {
                logger.error("Failed to send manager assignment email to manager {}: {}", manager.getId(), e.getMessage(), e);
            }
        }
    }

    /**
     * Loads non-deleted users for the restaurant whose role is {@link #ROLE_MANAGER} (by name lookup)
     * and {@link EntityStatus#ACTIVE}. Returns an empty list if repositories are unavailable or resolution fails.
     */
    private List<User> getActiveManagersForRestaurant(UUID restaurantId) {
        try {
            if (roleRepository == null || userRepository == null) {
                return java.util.Collections.emptyList();
            }
            Optional<com.gulfnet.shared_library.entity.Role> managerRoleOpt = roleRepository.findByName(ROLE_MANAGER);
            if (managerRoleOpt.isEmpty()) {
                return java.util.Collections.emptyList();
            }
            UUID managerRoleId = managerRoleOpt.get().getId();
            return userRepository.findAllByRestaurantIdAndRoleIdAndIsDeletedFalse(restaurantId, managerRoleId)
                    .stream()
                    .filter(u -> u.getStatus() == EntityStatus.ACTIVE)
                    .toList();
        } catch (Exception e) {
            logger.error("Failed to resolve restaurant managers for {}: {}", restaurantId, e.getMessage(), e);
            return java.util.Collections.emptyList();
        }
    }

    /**
     * Returns active, non-deleted users with the {@code HQ_ADMIN} role. Used for assignment email fallback;
     * yields an empty list on missing repositories or errors.
     */
    private List<User> getActiveHqAdmins() {
        try {
            if (roleRepository == null || userRepository == null) {
                return java.util.Collections.emptyList();
            }
            Optional<com.gulfnet.shared_library.entity.Role> hqAdminRoleOpt = roleRepository.findByName("HQ_ADMIN");
            if (hqAdminRoleOpt.isEmpty()) {
                return java.util.Collections.emptyList();
            }
            UUID hqRoleId = hqAdminRoleOpt.get().getId();
            return userRepository.findAllByRoleIdAndStatusAndIsDeletedFalse(hqRoleId, EntityStatus.ACTIVE);
        } catch (Exception e) {
            logger.error("Failed to resolve HQ admins: {}", e.getMessage(), e);
            return java.util.Collections.emptyList();
        }
    }

    private String resolveRoleName(User employee) {
        try {
            if (employee == null || employee.getRoleId() == null || roleRepository == null) {
                return "";
            }
            com.gulfnet.shared_library.entity.Role r = roleRepository.findById(employee.getRoleId()).orElse(null);
            return r != null && r.getName() != null ? r.getName() : "";
        } catch (Exception e) {
            return "";
        }
    }

    private String safeRestaurantCode(Restaurant restaurant) {
        return restaurant != null && restaurant.getRestaurantCode() != null ? restaurant.getRestaurantCode() : "";
    }

    private String safeDisplayName(User user, Locale locale) {
        if (user == null) {
            return "";
        }
        String firstName = user.getFirstName() != null ? user.getFirstName() : "";
        String lastName = user.getLastName() != null ? user.getLastName() : "";
        boolean isJapanese = locale != null && "ja".equalsIgnoreCase(locale.getLanguage());
        String full = isJapanese ? ((lastName + " " + firstName).trim()) : ((firstName + " " + lastName).trim());
        return full.isEmpty() ? (user.getUserCode() != null ? user.getUserCode() : "") : full;
    }

    /**
     * Builds a self-contained HTML email (table/card layout) for restaurant assignment notifications.
     * Greets {@code recipient}, shows {@code title} and {@code introLine}, then a detail grid for
     * {@code employee} (name, role, email, user code, restaurant code) with values escaped for HTML.
     */
    private String buildAssignmentCardHtml(
            Locale locale,
            String title,
            String introLine,
            User recipient,
            User employee,
            Restaurant restaurant,
            String roleName
    ) {
        String recipientName = safeDisplayName(recipient, locale);
        // Use the existing unified "Dear {name}" greeting key (localized).
        String greeting = messageUtil.getMessage("user.registration.email.manager.greeting", locale, recipientName);

        String fieldEmployeeName = messageUtil.getMessage("email.assignment.field.employee_name", locale);
        String fieldRole = messageUtil.getMessage("email.assignment.field.role", locale);
        String fieldEmail = messageUtil.getMessage("email.assignment.field.email", locale);
        String fieldUserCode = messageUtil.getMessage("email.assignment.field.user_code", locale);
        String fieldRestaurant = messageUtil.getMessage("email.assignment.field.restaurant", locale);

        String employeeEmail = employee.getEmail() != null ? employee.getEmail() : "";
        String employeeUserCode = employee.getUserCode() != null ? employee.getUserCode() : "";
        String restaurantCode = safeRestaurantCode(restaurant);

        String regards = messageUtil.getMessage(MSG_EMAIL_RECEIPT_REGARDS, locale);
        String team = messageUtil.getMessage(MSG_USER_REGISTRATION_EMAIL_TEAM, locale);

        // Same table/card design pattern as registration email, but generic strings.
        return ""
                + "<!DOCTYPE html>"
                + "<html>"
                + "<body style=\"margin:0;padding:16px 0;background:#f3f4f6;font-family:Arial,Helvetica,sans-serif;\">"
                + AssignmentEmailHtml.TABLE_PRESENTATION_BORDER_COLLAPSE
                + "<tr><td align=\"center\">"
                + "<table role=\"presentation\" width=\"600\" cellpadding=\"0\" cellspacing=\"0\" style=\"max-width:600px;width:100%;background:#ffffff;border-radius:14px;border:1px solid #e5e7eb;overflow:hidden;\">"
                + "<tr><td style=\"background:#2563eb;height:10px;\">&nbsp;</td></tr>"
                + "<tr><td style=\"padding:20px 24px 8px 24px;\">"
                + "<div style=\"font-size:18px;color:#111827;font-weight:700;line-height:24px;\">"
                + escapeHtml(greeting)
                + AssignmentEmailHtml.DIV_CLOSE
                + "</td></tr>"
                + "<tr><td style=\"padding:0 24px 24px 24px;\">"
                + "<div style=\"font-size:14px;color:#111827;line-height:22px;margin:0 0 12px;\">"
                + "<div style=\"font-weight:700;margin:0 0 6px;\">" + escapeHtml(title) + AssignmentEmailHtml.DIV_CLOSE
                + "<div style=\"margin:0;\">" + escapeHtml(introLine) + AssignmentEmailHtml.DIV_CLOSE
                + AssignmentEmailHtml.DIV_CLOSE
                + "<table role=\"presentation\" width=\"100%\" cellpadding=\"0\" cellspacing=\"0\" style=\"background:#f9fafb;border:1px solid #e5e7eb;border-radius:12px;\">"
                + "<tr><td style=\"padding:14px 16px;font-size:14px;color:#111827;line-height:22px;word-break:break-word;overflow-wrap:anywhere;\">"
                + AssignmentEmailHtml.TABLE_PRESENTATION_BORDER_COLLAPSE
                + rowHtml(fieldEmployeeName, safeDisplayName(employee, locale))
                + rowHtml(fieldRole, roleName != null ? roleName : "")
                + rowHtml(fieldEmail, employeeEmail)
                + rowHtml(fieldUserCode, employeeUserCode)
                + rowHtml(fieldRestaurant, restaurantCode)
                + AssignmentEmailHtml.TABLE_CLOSE
                + "<p style=\"margin:14px 0 0;\">"
                + escapeHtml(regards) + "<br>" + escapeHtml(team)
                + "</p>"
                + AssignmentEmailHtml.TD_TR_TABLE_CLOSE
                + AssignmentEmailHtml.TD_TR_TABLE_CLOSE
                + AssignmentEmailHtml.TD_TR_TABLE_CLOSE
                + "</body></html>";
    }

    private String rowHtml(String label, String value) {
        return ""
                + "<tr>"
                + "<td style=\"padding:4px 0;color:#6b7280;font-size:13px;font-weight:700;\">" + escapeHtml(label) + ":</td>"
                + AssignmentEmailHtml.TD_VALUE_ALIGN_RIGHT_13
                + escapeHtml(value != null ? value : "")
                + AssignmentEmailHtml.TD_CLOSE
                + AssignmentEmailHtml.TR_CLOSE;
    }

    private String escapeHtml(String input) {
        if (input == null) {
            return "";
        }
        return input
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;");
    }

    /**
     * Localized plain-text body for waiter credential email, formatted via {@link #toHtmlFromPlainText}
     * for HTML mailers. Returns {@code null} when {@code messageUtil} is not available.
     */
    private String buildWaiterAssignmentEmailBodyForEmployee(User waiter, String newPassword, Locale userLocale) {
        if (messageUtil == null) {
            return null;
        }

        String firstName = waiter.getFirstName() != null ? waiter.getFirstName() : "";
        String lastName = waiter.getLastName() != null ? waiter.getLastName() : "";
        boolean isJapanese = userLocale != null && "ja".equalsIgnoreCase(userLocale.getLanguage());
        String waiterName = isJapanese
                ? ((lastName + " " + firstName).trim())
                : ((firstName + " " + lastName).trim());
        String userCode = waiter.getUserCode() != null ? waiter.getUserCode() : "";

        String bodyPlain = messageUtil.getMessage(
                "waiter.restaurant.assignment.employee.email.body",
                userLocale != null ? userLocale : Locale.ENGLISH,
                waiterName,
                userCode,
                (newPassword != null ? newPassword : ""),
                messageUtil.getMessage(MSG_EMAIL_RECEIPT_REGARDS, userLocale != null ? userLocale : Locale.ENGLISH),
                messageUtil.getMessage(MSG_USER_REGISTRATION_EMAIL_TEAM, userLocale != null ? userLocale : Locale.ENGLISH)
        );
        return toHtmlFromPlainText(bodyPlain);
    }

    /**
     * Escapes HTML-sensitive characters and converts line breaks to {@code <br>} so translation-driven
     * plain text is safe to embed in HTML email bodies.
     */
    private String toHtmlFromPlainText(String plainText) {
        if (plainText == null) {
            return "";
        }
        // EmailSender sends HTML emails; keep translations plain-text and convert here.
        String escaped = plainText
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;");
        // Normalize newlines then convert to <br>
        escaped = escaped.replace("\r\n", "\n").replace("\r", "\n");
        return escaped.replace("\n", "<br>");
    }

    /**
     * Data class to hold batch-loaded restaurant data.
     */
    private static class RestaurantBatchData {
        final Map<UUID, List<RestaurantTranslation>> translationsMap;
        final Map<UUID, List<RestaurantGroupTranslation>> groupTranslationsMap;
        final Map<UUID, Integer> employeeCountMap;
        final Map<UUID, Integer> seatingCapacityMap;
        final Set<UUID> restaurantsWithMenuAssignments;
        final Map<UUID, Long> discountCountMap;
        final Map<UUID, Long> promotionCountMap;
        final Map<String, String> preSignedUrlCache;

        /**
         * Constructs a RestaurantBatchData container for batch-loaded restaurant-related data.
         * Used for performance optimization when building multiple restaurant responses.
         *
         * @param translationsMap            map of restaurant translations by restaurant ID
         * @param groupTranslationsMap       map of restaurant group translations by group ID
         * @param employeeCountMap           map of employee counts by restaurant ID
         * @param seatingCapacityMap         map of seating capacities by restaurant ID
         * @param restaurantsWithMenuAssignments set of restaurant IDs with menu assignments
         * @param discountCountMap           map of discount counts by restaurant ID
         * @param promotionCountMap          map of promotion counts by restaurant ID
         * @param preSignedUrlCache          cache of pre-signed URLs
         */
        RestaurantBatchData(Map<UUID, List<RestaurantTranslation>> translationsMap,
                          Map<UUID, List<RestaurantGroupTranslation>> groupTranslationsMap,
                          Map<UUID, Integer> employeeCountMap,
                          Map<UUID, Integer> seatingCapacityMap,
                          Set<UUID> restaurantsWithMenuAssignments,
                          Map<UUID, Long> discountCountMap,
                          Map<UUID, Long> promotionCountMap,
                          Map<String, String> preSignedUrlCache) {
            this.translationsMap = translationsMap;
            this.groupTranslationsMap = groupTranslationsMap;
            this.employeeCountMap = employeeCountMap;
            this.seatingCapacityMap = seatingCapacityMap;
            this.restaurantsWithMenuAssignments = restaurantsWithMenuAssignments;
            this.discountCountMap = discountCountMap;
            this.promotionCountMap = promotionCountMap;
            this.preSignedUrlCache = preSignedUrlCache;
        }
    }

    /**
     * Loads all batch data for restaurants to avoid N+1 queries.
     */
    private RestaurantBatchData loadRestaurantBatchData(List<Restaurant> restaurants) {
        long queryStart = System.currentTimeMillis();
        List<UUID> restaurantIds = restaurants.stream().map(Restaurant::getId).collect(Collectors.toList());
        
        Map<UUID, List<RestaurantTranslation>> translationsMap = restaurantTranslationRepository
                .findAllByRestaurantIdIn(restaurantIds)
                .stream()
                .collect(Collectors.groupingBy(t -> t.getRestaurant().getId()));
        
        List<UUID> groupIds = restaurants.stream()
                .map(r -> r.getRestaurantGroup() != null ? r.getRestaurantGroup().getId() : null)
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.toList());
        
        Map<UUID, List<RestaurantGroupTranslation>> groupTranslationsMap = groupIds.isEmpty() 
                ? new HashMap<>()
                : restaurantGroupTranslationRepository.findAllByRestaurantGroupIdIn(groupIds)
                        .stream()
                        .collect(Collectors.groupingBy(t -> t.getRestaurantGroup().getId()));
        
        Map<UUID, Integer> employeeCountMap = loadEmployeeCounts(restaurantIds);
        Map<UUID, Integer> seatingCapacityMap = restaurantTableRepository
                .getTotalSeatingCapacityByRestaurantIds(restaurantIds)
                .stream()
                .collect(Collectors.toMap(result -> (UUID) result[0], result -> ((Number) result[1]).intValue()));
        
        Set<UUID> restaurantsWithMenuAssignments = new HashSet<>(
                restaurantMenuMappingRepository.findRestaurantIdsWithMenuAssignments(restaurantIds));
        
        Map<UUID, Long> discountCountMap = new HashMap<>();
        Map<UUID, Long> promotionCountMap = new HashMap<>();
        if (!restaurantIds.isEmpty() && restaurantIds.size() <= 100) {
            loadDiscountAndPromotionCounts(restaurantIds, discountCountMap, promotionCountMap);
        }
        
        logger.info("Restaurant query and batch loading took: {}ms", System.currentTimeMillis() - queryStart);
        
        return new RestaurantBatchData(translationsMap, groupTranslationsMap, employeeCountMap, 
                seatingCapacityMap, restaurantsWithMenuAssignments, discountCountMap, 
                promotionCountMap, new HashMap<>());
    }

    /**
     * Loads employee counts for restaurants.
     */
    private Map<UUID, Integer> loadEmployeeCounts(List<UUID> restaurantIds) {
        Map<UUID, Integer> employeeCountMap = new HashMap<>();
        if (restaurantIds.isEmpty()) {
            return employeeCountMap;
        }
        
        try {
            List<Object[]> results = userRepository.countEmployeesByRestaurantIds(restaurantIds);
            if (results != null && !results.isEmpty()) {
                Map<UUID, Integer> tempMap = results.stream()
                        .filter(result -> result != null && result.length >= 2 && result[0] != null)
                        .collect(Collectors.toMap(
                                result -> (UUID) result[0],
                                result -> ((Number) result[1]).intValue(),
                                (existing, replacement) -> existing));
                employeeCountMap.putAll(tempMap);
            }
            logger.debug("Loaded employee counts for {} restaurants, {} have employees", 
                    restaurantIds.size(), employeeCountMap.size());
        } catch (Exception e) {
            logger.error("Failed to load employee counts for restaurants: {}", e.getMessage(), e);
        }
        return employeeCountMap;
    }

    /**
     * Loads discount and promotion counts for restaurants.
     */
    private void loadDiscountAndPromotionCounts(List<UUID> restaurantIds, 
                                                Map<UUID, Long> discountCountMap, 
                                                Map<UUID, Long> promotionCountMap) {
        try {
            Map<UUID, Long> tmpDiscountMap = restaurantDiscountMappingRepository
                    .countActiveDiscountsByRestaurantIds(restaurantIds)
                    .stream()
                    .collect(Collectors.toMap(result -> (UUID) result[0], 
                            result -> ((Number) result[1]).longValue()));
            discountCountMap.putAll(tmpDiscountMap);
        } catch (Exception e) {
            logger.warn("Failed to load discount counts", e);
        }
        
        try {
            Map<UUID, Long> tmpPromotionMap = restaurantPromotionMappingRepository
                    .countActivePromotionsByRestaurantIds(restaurantIds)
                    .stream()
                    .collect(Collectors.toMap(result -> (UUID) result[0], 
                            result -> ((Number) result[1]).longValue()));
            promotionCountMap.putAll(tmpPromotionMap);
        } catch (Exception e) {
            logger.warn("Failed to load promotion counts", e);
        }
    }

    /**
     * Maps restaurants to response DTOs using batch-loaded data.
     */
    private List<RestaurantResponse> mapRestaurantsToResponses(List<Restaurant> restaurants, 
                                                               RestaurantBatchData batchData, 
                                                               String locale) {
        return restaurants.stream()
                .map(restaurant -> buildRestaurantResponseOptimized(
                        restaurant,
                        batchData.translationsMap,
                        batchData.groupTranslationsMap,
                        batchData.employeeCountMap,
                        batchData.seatingCapacityMap,
                        batchData.restaurantsWithMenuAssignments,
                        batchData.discountCountMap,
                        batchData.promotionCountMap,
                        batchData.preSignedUrlCache,
                        locale))
                .collect(Collectors.toList());
    }

    /**
     * Applies sorting to restaurant responses.
     */
    private void applySorting(List<RestaurantResponse> responses, String sortBy, 
                             Sort.Direction direction, Locale userLocale) {
        if (sortBy == null || sortBy.trim().isEmpty()) {
            return;
        }
        
        if ("employeeCount".equalsIgnoreCase(sortBy) || "employeecount".equalsIgnoreCase(sortBy)) {
            Comparator<RestaurantResponse> comparator = Comparator.comparing(
                    r -> r.getEmployeeCount() != null ? r.getEmployeeCount() : 0,
                    Comparator.nullsLast(Comparator.naturalOrder()));
            if (direction == Sort.Direction.DESC) {
                comparator = comparator.reversed();
            }
            responses.sort(comparator);
            logger.debug("Sorted {} restaurants by employeeCount in {} order", responses.size(), direction);
        } else {
            LocaleContextHolder.setLocale(userLocale);
            LocaleSortUtil.sortName(responses, sortBy, direction);
            logger.debug("Sorted {} restaurants by {} in {} order using LocaleSortUtil", 
                    responses.size(), sortBy, direction);
        }
    }

    /**
     * Builds paginated response with metadata.
     */
    private RestaurantListResponse buildPaginatedResponse(List<RestaurantResponse> allResponses, 
                                                          int pageNumber, int pageSize, int total) {
        int fromIndex = Math.min(pageNumber * pageSize, allResponses.size());
        int toIndex = Math.min(fromIndex + pageSize, allResponses.size());
        List<RestaurantResponse> paginatedResponses = new ArrayList<>(
                allResponses.subList(fromIndex, toIndex));
        
        int totalPages = (int) Math.ceil((double) total / pageSize);
        PaginationMetaData metaData = PaginationMetaData.builder()
                .page(pageNumber + 1)
                .size(pageSize)
                .totalPages(totalPages)
                .totalRecords((long) total)
                .build();
        
        return RestaurantListResponse.builder()
                .restaurants(paginatedResponses)
                .count((long) paginatedResponses.size())
                .total((long) total)
                .metaData(metaData)
                .build();
    }

    private void rescheduleUnusedSessionExpiryJobs(UUID restaurantId) {
        try {
            unusedSessionExpiryScheduleService.scheduleForRestaurant(restaurantId);
        } catch (SchedulerException e) {
            logger.warn(
                    "Failed to reschedule unused session expiry Quartz jobs for restaurant {}: {}",
                    restaurantId,
                    e.getMessage());
        }
    }

} 