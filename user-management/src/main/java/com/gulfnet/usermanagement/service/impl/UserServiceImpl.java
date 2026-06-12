package com.gulfnet.usermanagement.service.impl;

import com.gulfnet.shared_library.config.AWSService;
import com.gulfnet.shared_library.entity.*;
import com.gulfnet.shared_library.entity.AuditLogging;
import com.gulfnet.shared_library.entity.AuditTrail;
import com.gulfnet.shared_library.enums.BulkUploadStatus;
import com.gulfnet.shared_library.enums.UploadType;
import com.gulfnet.shared_library.enums.EmploymentType;
import com.gulfnet.shared_library.enums.EntityStatus;
import com.gulfnet.shared_library.exception.BadRequestException;
import com.gulfnet.shared_library.model.request.LoginRequest;
import com.gulfnet.shared_library.model.request.RegisterUserRequest;
import com.gulfnet.shared_library.model.request.UpdateUserRequest;
import com.gulfnet.shared_library.model.request.UpdatePreferredLanguageRequest;
import com.gulfnet.shared_library.model.request.UpdateDeviceTokenRequest;
import com.gulfnet.shared_library.model.request.EmailAvailabilityRequest;
import com.gulfnet.shared_library.model.response.dto.*;
import com.gulfnet.shared_library.model.response.dto.ProfileUpdateRequestResponse;
import com.gulfnet.shared_library.repository.*;
import com.gulfnet.shared_library.repository.NotificationRepository;
import com.gulfnet.shared_library.repository.RefundItemRepository;
import com.gulfnet.shared_library.entity.Order;
import com.gulfnet.shared_library.entity.RestaurantTable;
import com.gulfnet.shared_library.entity.RestaurantSection;
import com.gulfnet.shared_library.entity.Restaurant;
import com.gulfnet.shared_library.entity.CashDrawerTranslation;
import com.gulfnet.shared_library.entity.CashierShift;
import com.gulfnet.shared_library.enums.ShiftStatus;
import com.gulfnet.shared_library.model.response.dto.RefundRequestResponse;
import com.gulfnet.shared_library.enums.DiscountType;
import com.gulfnet.shared_library.enums.TransactionStatus;
import com.gulfnet.shared_library.enums.RefundType;
import com.gulfnet.shared_library.enums.AppType;
import com.gulfnet.shared_library.model.response.dto.AdditionalDiscountRequestResponse;
import com.gulfnet.shared_library.model.response.dto.UnifiedRequestListResponse;
import com.gulfnet.shared_library.model.response.dto.RequestListItemResponse;
import com.gulfnet.shared_library.model.response.dto.RequestDetailsResponse;
import com.gulfnet.shared_library.model.response.dto.RequestApprovalResponse;
import com.gulfnet.shared_library.model.request.AdditionalDiscountApprovalRequest;
import com.gulfnet.shared_library.model.response.dto.OrderCancellationRequestResponse;
import com.gulfnet.shared_library.model.response.dto.AccountSettingsDto;
import com.gulfnet.shared_library.model.response.dto.RestaurantChainDto;
import com.gulfnet.shared_library.model.response.dto.RestaurantChainResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import com.gulfnet.shared_library.enums.OrderStatus;
import com.gulfnet.shared_library.enums.OrderType;
import com.gulfnet.shared_library.enums.PaymentSystemType;
import com.gulfnet.shared_library.enums.ChargeType;
import com.gulfnet.shared_library.enums.BxgyRole;
import com.gulfnet.shared_library.util.*;
import com.gulfnet.shared_library.util.CancellationAmountPolicy;
import com.gulfnet.shared_library.util.CurrencyFormatter;
import com.gulfnet.shared_library.service.TakeawaySessionTableReleaseService;
import com.gulfnet.usermanagement.config.EmailProperties;
import com.gulfnet.usermanagement.config.FrontendUrlProperties;
import com.gulfnet.usermanagement.config.LocalizationProperties;
import com.gulfnet.usermanagement.config.RabbitMQConfig;
import com.gulfnet.usermanagement.service.BulkUserUploadService;
import com.gulfnet.usermanagement.service.UserService;
import com.gulfnet.usermanagement.service.AuditLoggingService;
import com.gulfnet.usermanagement.service.NotificationPublisherService;
import com.gulfnet.usermanagement.util.JwtUtil;
import com.gulfnet.shared_library.repository.AuditTrailRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.AmqpException;
import com.gulfnet.usermanagement.util.MessageUtil;
import com.gulfnet.usermanagement.util.RegistrationEmailHtmlFormatter;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.cache.annotation.CacheEvict;
import com.gulfnet.shared_library.model.request.StatusEventMessage;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.scheduling.annotation.Async;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.Date;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;
import com.gulfnet.shared_library.exception.ResourceNotFoundException;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileInputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Comparator;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Subquery;
import jakarta.persistence.criteria.Root;
import java.util.ArrayList;
import org.springframework.data.jpa.domain.Specification;
import com.gulfnet.shared_library.model.request.UserProfileUpdateRequest;
import com.gulfnet.shared_library.model.request.ProfileUpdateApprovalRequest;
 
import com.gulfnet.shared_library.enums.RequestStatus;
import com.gulfnet.shared_library.enums.ActionType;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import java.util.Map;
import java.util.HashMap;

import static com.gulfnet.usermanagement.config.RabbitMQConfig.NOTIFICATION_TOPIC_EXCHANGE;
import static com.gulfnet.usermanagement.config.RabbitMQConfig.NOTIFICATION_ROUTING_KEY;

@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private static final Logger log = LoggerFactory.getLogger(UserServiceImpl.class);

    @Value("${jwt.expiry-hours:24}")
    private int jwtExpiryHours;

    private final EmailProperties emailProperties;
    private final FrontendUrlProperties frontendUrlProperties;

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final EmailSender emailSender;
    private final ShiftRepository shiftRepository;
    private final ShiftTranslationRepository shiftTranslationRepository;
    private final UserShiftMappingRepository userShiftMappingRepository;
    private final RoleRepository roleRepository;
    private final RestaurantRepository restaurantRepository;
    private final RestaurantGroupTranslationRepository restaurantGroupTranslationRepository;
    private final RestaurantTranslationRepository restaurantTranslationRepository;
    private final JwtUtil jwtUtil;
    private final LocalizationProperties localizationProperties;
    private final MessageUtil messageUtil;
    private final LoginAuditRepository loginAuditRepository;
    private final BulkUploadRepository bulkUploadRepository;
    private final BulkUserUploadService bulkUserUploadService;
    private final AWSService awsService;
    private final TableAssignmentRepository tableAssignmentRepository;
    private final AuditLoggingService auditLoggingService;
    private final AuditTrailRepository auditTrailRepository;
    private final OrderRepository orderRepository;
    private final TakeawaySessionTableReleaseService takeawaySessionTableReleaseService;
    private final RestaurantTableRepository restaurantTableRepository;
    private final com.gulfnet.shared_library.repository.KdsRepository kdsRepository;
    private final com.gulfnet.shared_library.repository.KdsConfigurationRepository kdsConfigurationRepository;
    private final RestaurantSectionRepository restaurantSectionRepository;
    private final TransactionRepository transactionRepository;
    private final RefundRepository refundRepository;
    private final RefundItemRepository refundItemRepository;
    private final OrderedItemRepository orderedItemRepository;
    private final OrderedComboRepository orderedComboRepository;
    private final com.gulfnet.shared_library.repository.DiscountRepository discountRepository;
    private final NotificationRepository notificationRepository;
    private final NotificationPublisherService notificationPublisherService;
    private final org.springframework.context.ApplicationContext applicationContext;
    private final RegistrationEmailHtmlFormatter registrationEmailHtmlFormatter;

    private final RabbitTemplate rabbitTemplate;
    private final CashierShiftRepository cashierShiftRepository;

    private final CashDrawerTranslationRepository cashDrawerTranslationRepository;
    
    // WebSocket messaging template - NOT USED
    // User-management uses only RabbitMQ to send notifications
    // Restaurant-management consumes from RabbitMQ and forwards to WebSocket clients via /restaurant/ws
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private SimpMessagingTemplate messagingTemplate;
    
    // Optional injection of NotificationService from restaurant-management module
    // This allows WebSocket notifications for cashiers (Windows app doesn't support FCM)
    // If cross-module dependency is not available, this will be null and notifications will only use FCM
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    @org.springframework.beans.factory.annotation.Qualifier("notificationServiceImpl")
    private Object notificationService; // Using Object to avoid compilation errors if module not available
    
    // WebClient for calling restaurant-management API to fetch currency and tax/service charge rates
    // Load-balanced WebClient.Builder is defined in WebClientConfig
    @org.springframework.beans.factory.annotation.Autowired
    private org.springframework.web.reactive.function.client.WebClient.Builder webClientBuilder;
    
    // Base URL for restaurant-management service
    // Used to call /api/v1/restaurantchain/config API to get:
    // - Currency symbol (for CurrencyFormatter)
    // - Tax rates (dine-in/takeaway)
    // - Service charge rates (dine-in/takeaway)
    // - Packing charge configuration
    // This is required because user-management module doesn't have direct access to RestaurantChainConfigProperties
    // which is in the restaurant-management module
    @org.springframework.beans.factory.annotation.Value("${app.restaurant-management.base-url:http://localhost:8083}")
    private String restaurantManagementBaseUrl;
    
    @jakarta.persistence.PersistenceContext
    private jakarta.persistence.EntityManager entityManager;

    

    @Transactional
    public ResponseDto<UserAccountDataResponse> registerUser(
            RegisterUserRequest request, String creatorId, String creatorRole) {

        Locale userLocale = LocaleContextHolder.getLocale();

        if (!"HQ_ADMIN".equals(creatorRole) && !"MANAGER".equals(creatorRole)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    messageUtil.getMessage("user.registration.error.unauthorized", userLocale));
        }

        if ("MANAGER".equals(creatorRole)) {
            Role requestedRole = roleRepository.findById(request.getRoleId())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                            messageUtil.getMessage("user.registration.error.invalid.role", userLocale))
                    );
            if ("HQ_ADMIN".equals(requestedRole.getName())) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                        messageUtil.getMessage("user.registration.error.manager.hqadmin", userLocale));
            }
        }

        if (request.getEmail() != null && !request.getEmail().isBlank()
                && userRepository.existsByEmailIgnoreCase(request.getEmail())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    messageUtil.getMessage("user.registration.error.email.exists", userLocale, request.getEmail()));
        }

        // Normalize userCode to lowercase for case-insensitive uniqueness check only
        // Store the original userCode (trimmed) to preserve case
        String originalUserCode = request.getUserCode() != null ? request.getUserCode().trim() : null;
        String normalizedUserCode = originalUserCode != null ? originalUserCode.toLowerCase() : null;
        if (normalizedUserCode != null && userRepository.existsByUserCodeIgnoreCase(normalizedUserCode)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    messageUtil.getMessage("user.registration.error.usercode.exists", userLocale));
        }

        if (request.getLanguageCode() == null || !localizationProperties.getLanguages().contains(request.getLanguageCode())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    messageUtil.getMessage("user.registration.error.invalid.language", userLocale));
        }

        String generatedPassword = PasswordGeneratorUtil.generatePassword(12);

        User user = new User();
        user.setFirstName(request.getFirstName());
        user.setLastName(request.getLastName());
        user.setEmail(request.getEmail());
        user.setContactNumber(request.getContactNumber());
        user.setPhotoUrl(awsService.stripToKey(request.getPhotoUrl()));
        user.setEmploymentType(request.getEmploymentType());
        user.setUserCode(originalUserCode); // Store original case

        UUID roleId = request.getRoleId();
        roleRepository.findById(roleId).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                        messageUtil.getMessage("user.registration.error.invalid.roleid", userLocale)));
        user.setRoleId(roleId);

        user.setLanguageCode(request.getLanguageCode());

        if (request.getRestaurantId() != null) {
            restaurantRepository.findById(request.getRestaurantId())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                            messageUtil.getMessage("user.registration.error.invalid.restaurantid", userLocale)));
            user.setRestaurantId(request.getRestaurantId());
        }

        user.setPassword(passwordEncoder.encode(generatedPassword));
        user.setStatus(request.getStatus());
        user.setIsStatusLocked(Boolean.TRUE.equals(request.getIsStatusLocked()));
        user.setIsDeleted(false);
        // Default profile update status to NONE for fresh registrations
        user.setProfileUpdateRequestStatus(
                request.getProfileUpdateRequestStatus() != null
                        ? request.getProfileUpdateRequestStatus()
                        : RequestStatus.NONE
        );
        user.setProfileUpdateRequestData(null); // Initialize as null for new user registration

        User creator = userRepository.findById(UUID.fromString(creatorId))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        messageUtil.getMessage("user.not.found", userLocale)));

        user.setCreatedBy(creator);
        user.setUpdatedBy(null);
        user.setCreatedAt(OffsetDateTime.now(ZoneOffset.UTC));
        userRepository.save(user);

        // If HQ Admin directly creates a user and assigns them to a restaurant,
        // notify the restaurant managers about the new employee assignment.
        // This mirrors the behavior in restaurant-management when employees are
        // assigned via the assignment API so that managers are always notified.
        if ("HQ_ADMIN".equals(creatorRole) && user.getRestaurantId() != null) {
            try {
                sendEmployeeAssignedToRestaurantNotification(user, userLocale, creator);
            } catch (Exception e) {
                log.error("Failed to send employee assigned to restaurant notification for user {}: {}", 
                        user.getId(), e.getMessage(), e);
                // Do not fail registration if notification fails
            }
        }

        // Log manager action for creating employee
        try {
            auditLoggingService.logManagerAction(
                    creator,
                    user,
                    AuditLogging.AuditAction.CREATE_EMPLOYEE,
                    user.getRestaurantId()
            );
        } catch (Exception e) {
            log.error("Failed to log manager action for user creation: {}", e.getMessage());
        }

        // Create audit trail for user creation
        try {
            Restaurant restaurant = null;
            if (user.getRestaurantId() != null) {
                restaurant = restaurantRepository.findById(user.getRestaurantId()).orElse(null);
            }
            createAuditTrail(
                    creator,
                    ActionType.USER_CREATE,
                    restaurant,
                    RequestStatus.NA, // Non-request action - always NA
                    null, // ipAddress - not available in this context
                    null, // userAgent - not available in this context
                    user.getId(),
                    "USER",
                    "User created: " + user.getUserCode() + " (" + user.getFirstName() + " " + user.getLastName() + ")",
                    null, // requestedBy
                    null, // requestedAt
                    null, // reviewedBy
                    null  // reviewedAt
            );
        } catch (Exception e) {
            log.error("Failed to create audit trail for user creation: {}", e.getMessage());
            // Don't break user creation flow if audit trail fails
        }

        // Auto-assign user to default KDS if user has KDS role and restaurant has default KDS
        if (user.getRestaurantId() != null && user.getRoleId() != null) {
            try {
                Role userRole = roleRepository.findById(user.getRoleId()).orElse(null);
                if (userRole != null && "KDS".equals(userRole.getName())) {
                    assignUserToDefaultKds(user, creator, userLocale);
                }
            } catch (Exception e) {
                log.error("Failed to assign user {} to default KDS: {}", user.getId(), e.getMessage());
                // Don't fail user registration if KDS assignment fails
            }
        }

        if (request.getShiftId() != null) {
            Shift registerShift = shiftRepository.findById(request.getShiftId())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                            messageUtil.getMessage("user.registration.error.invalid.shiftid", userLocale)));

            UserShiftMapping userShiftMapping = new UserShiftMapping();
            UserShiftId userShiftId = new UserShiftId(user.getId(), registerShift.getId());
            userShiftMapping.setId(userShiftId);
            userShiftMapping.setUser(user);
            userShiftMapping.setShift(registerShift);
            userShiftMappingRepository.save(userShiftMapping);
        }

        Locale registrationEmailLocale = resolvePreferredLocale(user, userLocale);
        String subject = messageUtil.getMessage("user.registration.email.subject", registrationEmailLocale);

        // Get user's role to determine if login link should be included
        String userRoleName = null;
        boolean isWebAppUser = false;
        if (user.getRoleId() != null) {
            Role userRole = roleRepository.findById(user.getRoleId()).orElse(null);
            if (userRole != null) {
                userRoleName = userRole.getName();
                // Only HQ_ADMIN and MANAGER are web app users who need login links
                isWebAppUser = "HQ_ADMIN".equals(userRoleName) || "MANAGER".equals(userRoleName);
            }
        }

        // Get login URL only for web app users (HQ_ADMIN, MANAGER)
        String loginUrl = null;
        if (isWebAppUser) {
            String baseUrl = frontendUrlProperties.getUrlForRole(userRoleName);
            loginUrl = baseUrl != null && !baseUrl.isEmpty() ? baseUrl : null;
        }

        // Determine if this is a mobile app user (WAITER, CASHIER, KDS) who should send email to manager
        boolean isMobileAppUser = "WAITER".equals(userRoleName) || 
                                 "CASHIER".equals(userRoleName) || 
                                 "KDS".equals(userRoleName);

        // Determine recipient email(s) based on user role
        Set<String> recipientEmails = new LinkedHashSet<>();
        boolean isDefaultEmailUsed = false;
        boolean isSendingToUserEmail = false; // Track if we're sending to user's own email

        if (isMobileAppUser) {
            // For mobile app users (WAITER, CASHIER, KDS)
            // Priority: 1. User's email (if provided), 2. Manager(s), 3. Default email
            String userEmail = user.getEmail();
            if (userEmail != null && !userEmail.trim().isEmpty()) {
                // Send to user's email with web app user content
                recipientEmails.add(userEmail);
                isSendingToUserEmail = true;
                log.info("Sending registration email to user's email for mobile app user: {} (role: {})", 
                        user.getUserCode(), userRoleName);
            } else {
                // No user email provided, send to manager(s)
                if (user.getRestaurantId() != null) {
                    Optional<Role> managerRoleOpt = roleRepository.findByName("MANAGER");
                    if (managerRoleOpt.isPresent()) {
                        UUID managerRoleId = managerRoleOpt.get().getId();
                        List<User> managers = userRepository.findAllByRestaurantIdAndRoleIdAndIsDeletedFalse(
                                user.getRestaurantId(), managerRoleId);
                        
                        int duplicateCount = 0;
                        for (User manager : managers) {
                            if (manager.getEmail() != null && !manager.getEmail().trim().isEmpty()) {
                                boolean wasNew = recipientEmails.add(manager.getEmail());
                                if (!wasNew) {
                                    duplicateCount++;
                                    log.debug("Duplicate email detected for manager {}: {}", manager.getId(), manager.getEmail());
                                }
                            }
                        }
                        
                        if (duplicateCount > 0) {
                            log.info("Found {} manager(s) for restaurant {} (user: {}, role: {}). Filtered {} duplicate email(s).", 
                                    managers.size(), user.getRestaurantId(), user.getUserCode(), userRoleName, duplicateCount);
                        } else {
                            log.info("Found {} manager(s) for restaurant {} (user: {}, role: {})", 
                                    managers.size(), user.getRestaurantId(), user.getUserCode(), userRoleName);
                        }
                    }
                }
                
                // If no managers found, try sending to active HQ Admin users, each in their preferred language
                if (recipientEmails.isEmpty()) {
                    Optional<Role> hqAdminRoleOpt = roleRepository.findByName("HQ_ADMIN");
                    if (hqAdminRoleOpt.isPresent()) {
                        UUID hqAdminRoleId = hqAdminRoleOpt.get().getId();
                        List<User> hqAdmins = userRepository.findAllByRoleIdAndStatusAndIsDeletedFalse(
                                hqAdminRoleId, EntityStatus.ACTIVE);

                        List<User> hqAdminsWithEmail = new java.util.ArrayList<>();
                        for (User hqAdmin : hqAdmins) {
                            if (hqAdmin.getEmail() != null && !hqAdmin.getEmail().trim().isEmpty()) {
                                hqAdminsWithEmail.add(hqAdmin);
                            }
                        }

                        if (!hqAdminsWithEmail.isEmpty()) {
                            log.info("Found {} HQ_ADMIN user(s) with valid email for registration notification (registered user: {}, role: {}).",
                                    hqAdminsWithEmail.size(), user.getUserCode(), userRoleName);

                            // Send to all HQ Admins, each in their preferred language, asynchronously
                            UserServiceImpl self = applicationContext.getBean(UserServiceImpl.class);
                            self.sendRegistrationEmailToHqAdminsPerLocaleAsync(
                                    hqAdminsWithEmail,
                                    user,
                                    generatedPassword,
                                    userRoleName,
                                    userLocale
                            );
                        } else {
                            log.warn("No active HQ_ADMIN users with valid email found. Falling back to default registration email configuration.");
                        }
                    } else {
                        log.warn("HQ_ADMIN role not found in database when resolving registration email recipients. Falling back to default registration email configuration.");
                    }
                }

                // Final fallback to default email if no managers or HQ Admins found
                if (recipientEmails.isEmpty()) {
                    String defaultEmail = emailProperties.getEmail();
                    if (defaultEmail != null && !defaultEmail.trim().isEmpty()) {
                        recipientEmails.add(defaultEmail);
                        isDefaultEmailUsed = true;
                        log.warn("No managers or HQ_ADMIN users found. Using default email: {}", defaultEmail);
                    }
                }
            }
        } else {
            // For web app users (HQ_ADMIN, MANAGER), send to their own email
            String userEmail = user.getEmail();
            if (userEmail == null || userEmail.trim().isEmpty()) {
                String defaultEmail = emailProperties.getEmail();
                if (defaultEmail != null && !defaultEmail.trim().isEmpty()) {
                    recipientEmails.add(defaultEmail);
                    isDefaultEmailUsed = true;
                    log.warn("User {} has no email. Sending credentials to default email: {}", 
                            user.getId(), defaultEmail);
                }
            } else {
                recipientEmails.add(userEmail);
            }
        }

        // Build email body based on recipient type
        String htmlBody = registrationEmailHtmlFormatter.buildRegistrationEmailHtml(
                user,
                generatedPassword,
                registrationEmailLocale,
                userRoleName,
                isMobileAppUser,
                isSendingToUserEmail,
                isWebAppUser,
                loginUrl,
                isDefaultEmailUsed
        );

        // Send email to all recipients asynchronously
        if (!recipientEmails.isEmpty()) {
            // Get proxy to ensure @Async works (calling from same class requires proxy)
            UserServiceImpl self = applicationContext.getBean(UserServiceImpl.class);
            self.sendRegistrationEmailAsync(recipientEmails, subject, htmlBody, user.getUserCode(), userRoleName);
        }

        // Build full user data response (same as get-by-id)
        UserAccountDataResponse data = buildUserAccountDataResponse(user);

        String message;
        // Don't show email message to HQ_ADMIN since email is sent to the user/manager, not to HQ_ADMIN
        if ("HQ_ADMIN".equals(creatorRole)) {
            message = messageUtil.getMessage("user.registration.success.hqadmin", userLocale);
        } else {
            message = messageUtil.getMessage("user.registration.success", userLocale);
        }

        return ResponseDto.<UserAccountDataResponse>builder()
                .message(message)
                .data(data)
                .build();
    }

    /**
     * Notify restaurant managers that a new employee has been assigned to their restaurant
     * when the employee is created directly by HQ Admin on the registration page.
     *
     * This uses NotificationService (if available) for real-time WebSocket/FCM notifications
     * with a fallback to RabbitMQ so restaurant-management can process the notification
     * even when the cross-module bean is not available.
     * Also saves notifications directly to the database for each manager.
     * 
     * @param employee The employee that was created and assigned
     * @param userLocale The locale for message translation
     * @param creator The HQ Admin who created the user (will be saved in notification as createdBy)
     */
    private void sendEmployeeAssignedToRestaurantNotification(User employee, Locale userLocale, User creator) {
        if (employee == null || employee.getRestaurantId() == null) {
            log.debug("Skipping employee assigned notification: employee or restaurantId is null");
            return;
        }

        try {
            // Load restaurant
            Restaurant restaurant = restaurantRepository.findById(employee.getRestaurantId())
                    .orElse(null);
            if (restaurant == null) {
                log.warn("Restaurant not found for employee {} when sending employee assigned notification", 
                        employee.getId());
                return;
            }

            // Find MANAGER role
            Optional<Role> managerRoleOpt = roleRepository.findByName("MANAGER");
            if (managerRoleOpt.isEmpty()) {
                log.warn("MANAGER role not found when sending employee assigned notification for employee {}", 
                        employee.getId());
                return;
            }
            UUID managerRoleId = managerRoleOpt.get().getId();

            // Find active managers for the restaurant
            List<User> managers = userRepository
                    .findAllByRestaurantIdAndRoleIdAndIsDeletedFalse(restaurant.getId(), managerRoleId)
                    .stream()
                    .filter(u -> u.getStatus() == EntityStatus.ACTIVE)
                    .collect(java.util.stream.Collectors.toList());

            if (managers.isEmpty()) {
                log.info("No active managers found for restaurant {} when sending employee assigned notification", 
                        restaurant.getId());
                return;
            }

            // Save notifications to database for each manager
            saveEmployeeAssignedNotificationsToDatabase(employee, restaurant, managers, userLocale, creator);

            boolean notificationSent = false;

            // Try to use NotificationService directly if available (same JVM deployment)
            try {
                Object wsNotifier = resolveNotificationService();
                if (wsNotifier != null) {
                    log.info("Sending employee assigned notification via NotificationService - employee: {}, restaurant: {}", 
                            employee.getId(), restaurant.getId());

                    java.lang.reflect.Method method = wsNotifier.getClass().getMethod(
                            "notifyEmployeeAssignedToRestaurant",
                            User.class,
                            com.gulfnet.shared_library.entity.Restaurant.class,
                            java.util.List.class,
                            java.util.Locale.class
                    );
                    method.invoke(wsNotifier, employee, restaurant, managers, userLocale);

                    log.info("Successfully sent employee assigned notification via NotificationService - employee: {}, restaurant: {}", 
                            employee.getId(), restaurant.getId());
                    notificationSent = true;
                } else {
                    log.debug("NotificationService not available, will use RabbitMQ fallback for employee assigned notification");
                }
            } catch (NoSuchMethodException e) {
                log.warn("Method notifyEmployeeAssignedToRestaurant not found in NotificationService: {}", e.getMessage());
            } catch (Exception e) {
                log.warn("Failed to send employee assigned notification via NotificationService: {}", e.getMessage());
            }

            // Fallback: publish message to RabbitMQ so restaurant-management can handle it
            if (!notificationSent) {
                try {
                    publishEmployeeAssignedToRestaurantNotificationToRabbitMQ(employee, restaurant, userLocale);
                    log.info("Published employee assigned notification to RabbitMQ - employee: {}, restaurant: {}", 
                            employee.getId(), restaurant.getId());
                } catch (Exception e) {
                    log.error("Failed to publish employee assigned notification to RabbitMQ: {}", e.getMessage(), e);
                }
            }
        } catch (Exception e) {
            log.error("Unexpected error while sending employee assigned notification for user {}: {}", 
                    employee != null ? employee.getId() : "null", e.getMessage(), e);
        }
    }

    /**
     * Save notifications to database for managers when a new employee is assigned to their restaurant.
     * 
     * @param employee The employee that was assigned
     * @param restaurant The restaurant where the employee was assigned
     * @param managers List of managers to notify
     * @param userLocale The locale for message translation
     * @param creator The HQ Admin who created the user
     */
    private void saveEmployeeAssignedNotificationsToDatabase(User employee, Restaurant restaurant, 
                                                             List<User> managers, Locale userLocale, User creator) {
        try {
            // Ensure creator is a managed entity
            User managedCreator = null;
            if (creator != null && creator.getId() != null) {
                try {
                    managedCreator = userRepository.findById(creator.getId()).orElse(null);
                } catch (Exception e) {
                    log.warn("Failed to reload creator user {} from repository: {}", creator.getId(), e.getMessage());
                }
            }

            // Get employee name
            String employeeName = (employee.getFirstName() != null ? employee.getFirstName() : "") + 
                    " " + (employee.getLastName() != null ? employee.getLastName() : "");
            employeeName = employeeName.trim();
            if (employeeName.isEmpty()) {
                employeeName = employee.getUserCode() != null ? employee.getUserCode() : "Employee";
            }

            // Save notification for each manager (per-recipient locale for title/body and listing)
            for (User manager : managers) {
                try {
                    // Ensure manager is a managed entity
                    User managedManager = userRepository.findById(manager.getId())
                            .orElse(null);
                    if (managedManager == null) {
                        log.warn("Manager {} not found in repository, skipping notification", manager.getId());
                        continue;
                    }

                    Locale loc = localeForRecipient(managedManager, userLocale);
                    String restaurantName = resolveRestaurantNameForManagerNotification(restaurant, loc);
                    String title;
                    String message;
                    try {
                        title = messageUtil.getMessage("notification.employee.assigned.to.restaurant.title", loc);
                        message = messageUtil.getMessage("notification.employee.assigned.to.restaurant.body", loc,
                                employeeName, restaurantName);
                    } catch (Exception e) {
                        log.debug("Notification message keys not found, using fallback message: {}", e.getMessage());
                        title = "New Employee Assigned";
                        message = String.format("Employee %s has been assigned to restaurant %s", employeeName, restaurantName);
                    }

                    Notification notification = Notification.builder()
                            .user(managedManager)
                            .title(title)
                            .message(message)
                            .type("EMPLOYEE_ASSIGNED_TO_RESTAURANT")
                            .bodyKey("notification.employee.assigned.to.restaurant.body")
                            .bodyArgs(serializeBodyArgs(employeeName, restaurantName))
                            .read(false)
                            .createdAt(OffsetDateTime.now(ZoneOffset.UTC))
                            .createdBy(managedCreator)
                            .build();

                    Notification savedNotification = notificationRepository.saveAndFlush(notification);
                    log.info("Successfully saved employee assigned notification to database - ID: {}, manager: {}, employee: {}", 
                            savedNotification.getId(), managedManager.getId(), employee.getId());

                    // Publish notification to RabbitMQ for FCM processing
                    try {
                        notificationPublisherService.publishNotification(savedNotification, managedManager);
                    } catch (Exception e) {
                        log.error("Failed to publish notification to RabbitMQ for manager {}: {}", 
                                managedManager.getId(), e.getMessage(), e);
                    }
                } catch (Exception e) {
                    log.error("Failed to save employee assigned notification for manager {}: {}", 
                            manager.getId(), e.getMessage(), e);
                }
            }
        } catch (Exception e) {
            log.error("Failed to save employee assigned notifications to database: {}", e.getMessage(), e);
        }
    }

    /**
     * Publish employee assigned to restaurant notification to RabbitMQ for restaurant-management to consume.
     * This is used as a fallback when NotificationService is not available.
     */
    private void publishEmployeeAssignedToRestaurantNotificationToRabbitMQ(User employee,
                                                                           Restaurant restaurant,
                                                                           Locale locale) {
        if (employee == null || restaurant == null) {
            log.debug("Skipping RabbitMQ publish for employee assigned notification: employee or restaurant is null");
            return;
        }

        try {
            org.springframework.amqp.rabbit.core.RabbitTemplate rabbitTemplateBean =
                    applicationContext.getBean(org.springframework.amqp.rabbit.core.RabbitTemplate.class);

            if (rabbitTemplateBean == null) {
                log.warn("RabbitTemplate not available, cannot publish employee assigned notification");
                return;
            }

            Map<String, Object> message = new HashMap<>();
            message.put("type", "employee_assigned_to_restaurant");
            message.put("notificationType", "EMPLOYEE_ASSIGNED_TO_RESTAURANT");
            message.put("employeeId", employee.getId().toString());
            message.put("restaurantId", restaurant.getId().toString());
            message.put("locale", locale != null ? locale.toString() : "en");
            message.put("timestamp", java.time.LocalDateTime.now(java.time.ZoneOffset.UTC)
                    .format(java.time.format.DateTimeFormatter.ISO_LOCAL_DATE_TIME));

            // Publish to the same exchange that restaurant-management uses
            rabbitTemplateBean.convertAndSend(
                    "websocket.topic.exchange",
                    "employee.assigned.to.restaurant",  // Routing key for employee assigned notification
                    message
            );

            log.info("Published employee assigned notification to RabbitMQ - employee: {}, restaurant: {}", 
                    employee.getId(), restaurant.getId());
        } catch (Exception e) {
            log.error("Failed to publish employee assigned notification to RabbitMQ: {}", e.getMessage(), e);
        }
    }

    @Async
    public CompletableFuture<Void> sendRegistrationEmailAsync(Set<String> recipientEmails, String subject, String htmlBody, 
                                          String userCode, String userRoleName) {
        boolean emailSent = false;
        for (String recipientEmail : recipientEmails) {
            try {
                emailSender.sendEmail(recipientEmail, subject, htmlBody);
                log.info("Registration email sent successfully to: {} (for user: {}, role: {})", 
                        recipientEmail, userCode, userRoleName);
                emailSent = true;
            } catch (Exception e) {
                log.error("Failed to send registration email to {}: {}", recipientEmail, e.getMessage(), e);
            }
        }

        if (!emailSent && !recipientEmails.isEmpty()) {
            log.error("Failed to send registration email to any recipient for user: {}", userCode);
        }
        
        return CompletableFuture.completedFuture(null);
    }

    /**
     * Sends registration emails to all active HQ Admin users, each in their own preferred language.
     * This is used as a fallback when registering a mobile app user (e.g., WAITER) who has no email
     * and no restaurant managers to notify.
     */
    @Async
    public CompletableFuture<Void> sendRegistrationEmailToHqAdminsPerLocaleAsync(
            List<User> hqAdmins,
            User registeredUser,
            String generatedPassword,
            String registeredUserRoleName,
            Locale fallbackLocale
    ) {
        for (User hqAdmin : hqAdmins) {
            try {
                Locale hqLocale = resolvePreferredLocale(hqAdmin, fallbackLocale);
                String roleLabel = registeredUserRoleName != null ? registeredUserRoleName : "";
                String subject = messageUtil.getMessage(
                        "user.registration.email.subject.hq.mobile.fallback", hqLocale, roleLabel);

                // Credentials are for mobile staff; do not include HQ web portal link in this notification
                boolean isMobileAppUser = false;
                boolean isSendingToUserEmail = false;
                boolean isWebAppUser = false;
                String loginUrl = null;
                boolean isDefaultEmailUsed = false;
                String hqAdminFirstName = hqAdmin.getFirstName() != null ? hqAdmin.getFirstName().trim() : "";

                String htmlBody = registrationEmailHtmlFormatter.buildRegistrationEmailHtml(
                        registeredUser,
                        generatedPassword,
                        hqLocale,
                        registeredUserRoleName,
                        isMobileAppUser,
                        isSendingToUserEmail,
                        isWebAppUser,
                        loginUrl,
                        isDefaultEmailUsed,
                        true,
                        hqAdminFirstName.isEmpty() ? null : hqAdminFirstName
                );

                emailSender.sendEmail(hqAdmin.getEmail(), subject, htmlBody);
                log.info("Registration email sent to HQ_ADMIN {} ({}) in locale {} for registered user {} (role: {}).",
                        hqAdmin.getId(), hqAdmin.getEmail(), hqLocale, registeredUser.getUserCode(), registeredUserRoleName);
            } catch (Exception e) {
                log.error("Failed to send registration email to HQ_ADMIN {}: {}", hqAdmin.getId(), e.getMessage(), e);
            }
        }

        return CompletableFuture.completedFuture(null);
    }


    @Transactional
    public ResponseEntity<ResponseDto<LoginResponseDto<LoginResponse>>> login(
            LoginRequest request,
            String ipAddress,
            String userAgent,
            String appVersion) {

        Locale userLocale = LocaleContextHolder.getLocale();

        // Validate required fields
        if (request.getPassword() == null || request.getPassword().isBlank()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    messageUtil.getMessage("user.login.error.password.required", userLocale)
            );
        }

        if ((request.getEmail() == null || request.getEmail().isBlank()) &&
            (request.getUserCode() == null || request.getUserCode().isBlank())) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    messageUtil.getMessage("user.login.error.email.or.usercode.required", userLocale)
            );
        }

        User user = null;

        if (request.getUserCode() != null && !request.getUserCode().isEmpty()) {
            // Use case-insensitive lookup for active, non-deleted user by userCode
            String normalizedUserCode = request.getUserCode().trim().toLowerCase();
            Optional<User> activeUserOpt = userRepository.findByUserCodeIgnoreCaseAndIsDeletedFalseAndStatus(
                    normalizedUserCode,
                    EntityStatus.ACTIVE
            );

            if (activeUserOpt.isPresent()) {
                user = activeUserOpt.get();
            } else {
                // Check if a user with this code exists but is deleted/inactive
                Optional<User> anyUserOpt = userRepository.findByUserCodeIgnoreCase(normalizedUserCode);
                if (anyUserOpt.isPresent()) {
                    // User exists but is not active / is deleted
                    throw new ResponseStatusException(
                            HttpStatus.UNAUTHORIZED,
                            messageUtil.getMessage("user.login.error.user.not.exists", userLocale)
                    );
                }

                // No user found at all -> generic invalid credentials
                throw new ResponseStatusException(
                        HttpStatus.UNAUTHORIZED,
                        messageUtil.getMessage("user.login.error.invalid.credentials", userLocale)
                );
            }
        }  else {
            Optional<User> activeUserOpt = userRepository.findByEmailAndIsDeletedFalseAndStatus(
                    request.getEmail(),
                    EntityStatus.ACTIVE
            );

            if (activeUserOpt.isPresent()) {
                user = activeUserOpt.get();
            } else {
                // Check if a user with this email exists but is deleted/inactive
                Optional<User> anyUserOpt = userRepository.findByEmailIgnoreCase(request.getEmail());
                if (anyUserOpt.isPresent()) {
                    // User exists but is not active / is deleted
                    throw new ResponseStatusException(
                            HttpStatus.UNAUTHORIZED,
                            messageUtil.getMessage("user.login.error.user.not.exists", userLocale)
                    );
                }

                // No user found at all -> generic invalid credentials
                throw new ResponseStatusException(
                        HttpStatus.UNAUTHORIZED,
                        messageUtil.getMessage("user.login.error.invalid.credentials", userLocale)
                );
            }
        }

        // Check Restaurant Status
        if (user.getRestaurantId() != null) {
            Restaurant restaurant = restaurantRepository.findById(user.getRestaurantId())
                .orElseThrow(() -> new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    messageUtil.getMessage("user.login.error.restaurant.invalid", userLocale)
                ));
        
        // Check Restaurant Status
        if (restaurant.getStatus() != EntityStatus.ACTIVE) {
            throw new ResponseStatusException(
                HttpStatus.UNAUTHORIZED,
                messageUtil.getMessage("user.login.error.restaurant.inactive", userLocale)
            );
        }

        // Check Restaurant Group Status
        if (restaurant.getRestaurantGroup() != null) {
            RestaurantGroup restaurantGroup = restaurant.getRestaurantGroup();
            if (restaurantGroup.getStatus() != EntityStatus.ACTIVE) {
                throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    messageUtil.getMessage("user.login.error.restaurant.group.inactive", userLocale)
                );
            }
        }
    }

        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    messageUtil.getMessage("user.login.error.invalid.credentials", userLocale)
            );
        }
        boolean forcedLogin = Boolean.TRUE.equals(request.getForcedLogin());

        // ---- Single-session guard (per user) ----
        // If there is an existing active LoginAudit (not expired yet), block login unless forcedLogin=true.
        // When forced, we delete the existing LoginAudit so the other device is immediately logged out.
        OffsetDateTime nowUtc = OffsetDateTime.now(ZoneOffset.UTC);
        Optional<LoginAudit> existingLoginAuditOpt = loginAuditRepository.findByUser_Id(user.getId());
        if (existingLoginAuditOpt.isPresent()) {
            LoginAudit existing = existingLoginAuditOpt.get();
            OffsetDateTime existingExpiry = existing.getLoginExpiryDate();

            if (existingExpiry != null && existingExpiry.isAfter(nowUtc) && !forcedLogin) {
                throw new ResponseStatusException(
                        HttpStatus.CONFLICT,
                        messageUtil.getMessage("user.login.error.already.logged.in", userLocale)
                );
            }

            if (forcedLogin) {
                loginAuditRepository.delete(existing);
                            }
        }


        String roleName = null;
        Role userRole = null;
        if (user.getRoleId() != null) {
            userRole = roleRepository.findById(user.getRoleId()).orElse(null);
            if (userRole != null) {
                roleName = userRole.getName();
            }
        }

        // Validate that the requested appType matches the user's role.
        // This comparison is case-insensitive and ignores underscores so that,
        // for example, HQADMIN (AppType) and HQ_ADMIN (DB role) are treated as equal.
        if (request.getAppType() != null && roleName != null) {
            String normalizedRole = roleName.replace("_", "").toUpperCase();
            String normalizedAppType = request.getAppType().name().replace("_", "").toUpperCase();
            if (!normalizedRole.equals(normalizedAppType)) {
                throw new ResponseStatusException(
                        HttpStatus.UNAUTHORIZED,
                        messageUtil.getMessage("user.login.error.unauthorized.access", userLocale)
                );
            }
        }

        if (request.getUserCode() != null && (roleName == null || !roleName.equalsIgnoreCase("WAITER"))) {
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    messageUtil.getMessage("user.login.error.invalid.credentials", userLocale)
            );
        }

        // Validate that kdsId is mandatory for KDS users
        if ("KDS".equals(roleName) && request.getKdsId() == null) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    messageUtil.getMessage("user.login.error.kdsid.required.for.kds.users", userLocale)
            );
        }

        // Validate that kdsId can only be provided for KDS users
        if (request.getKdsId() != null && !"KDS".equals(roleName)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    messageUtil.getMessage("user.login.error.kdsid.only.for.kds.users", userLocale)
            );
        }

        // Validate KDS ID and restaurant match at login time
        if ("KDS".equals(roleName) && request.getKdsId() != null) {
            UUID kdsId = request.getKdsId();
            
            // Find KDS by ID
            Optional<com.gulfnet.shared_library.entity.Kds> kdsOpt = kdsRepository.findById(kdsId);
            if (kdsOpt.isEmpty()) {
                throw new ResponseStatusException(
                        HttpStatus.UNAUTHORIZED,
                        messageUtil.getMessage("user.login.error.invalid.credentials", userLocale)
                );
            }
            
            com.gulfnet.shared_library.entity.Kds kds = kdsOpt.get();
            
            // Validate user's restaurant matches KDS restaurant
            if (user.getRestaurantId() == null) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        messageUtil.getMessage("user.restaurant.not.assigned", userLocale)
                );
            }
            
            if (kds.getRestaurantId() == null || !user.getRestaurantId().equals(kds.getRestaurantId())) {
                throw new ResponseStatusException(
                        HttpStatus.FORBIDDEN,
                        messageUtil.getMessage("kds.restaurant.mismatch", userLocale)
                );
            }
            
            // Validate user is assigned to this specific KDS device
            boolean isAssigned = kdsConfigurationRepository.existsByUserIdAndKdsId(user.getId(), kdsId);
            if (!isAssigned) {
                throw new ResponseStatusException(
                        HttpStatus.FORBIDDEN,
                        messageUtil.getMessage("kds.access.unauthorized", userLocale)
                );
            }
            
            log.info("KDS user {} validated for KDS ID {}", user.getId(), kdsId);
        }

        if (user.getRestaurantId() != null) {
            Restaurant restaurant = restaurantRepository.findById(user.getRestaurantId())
                    .orElse(null);
            if (restaurant != null) {
                if (EntityStatus.INACTIVE.equals(restaurant.getStatus())) {
                    throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,
                            messageUtil.getMessage("user.login.error.restaurant.inactive", userLocale));
                }
                if (restaurant.getRestaurantGroup() != null &&
                        EntityStatus.INACTIVE.equals(restaurant.getRestaurantGroup().getStatus())) {
                    throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,
                            messageUtil.getMessage("user.login.error.restaurant.group.inactive", userLocale));
                }
            }
        }

        LocalDateTime expiryTime = LocalDateTime.now(ZoneOffset.UTC).plusHours(jwtExpiryHours);

        String token = jwtUtil.generateToken(user.getId(), user.getEmail(), roleName, expiryTime);

        final User loginUser = user;
        final LocalDateTime loginExpiryTime = expiryTime;

        // Persist session marker. If forcedLogin was used, any previous audit row was deleted above.
        saveOrUpdateLoginAudit(
                loginUser,
                loginExpiryTime,
                ipAddress,
                userAgent,
                request.getAppType(),
                appVersion
        );

        // Create audit trail for LOGIN action
        try {
            Restaurant restaurant = null;
            if (user.getRestaurantId() != null) {
                restaurant = restaurantRepository.findById(user.getRestaurantId()).orElse(null);
            }
            createAuditTrail(
                    user,
                    ActionType.LOGIN,
                    restaurant,
                    RequestStatus.NA, // Non-request action - always NA
                    ipAddress,
                    userAgent,
                    user.getId(),
                    "USER",
                    "User logged in successfully",
                    null, // requestedBy
                    null, // requestedAt
                    null, // reviewedBy
                    null  // reviewedAt
            );
        } catch (Exception e) {
            log.error("Failed to create audit trail for login: {}", e.getMessage());
            // Don't break login flow if audit trail fails
        }

        UserBasicDetailsResponse userBasicDetails = new UserBasicDetailsResponse();
        userBasicDetails.setId(user.getId());
        userBasicDetails.setUserCode(user.getUserCode());
        userBasicDetails.setFirstName(user.getFirstName());
        userBasicDetails.setLastName(user.getLastName());
        userBasicDetails.setEmail(user.getEmail());
        userBasicDetails.setContactNumber(user.getContactNumber());
        userBasicDetails.setEmploymentType(user.getEmploymentType());
        userBasicDetails.setStatus(user.getStatus());
        userBasicDetails.setLanguageCode(user.getLanguageCode());

        String signedPhotoUrl = null;
        if (user.getPhotoUrl() != null && !user.getPhotoUrl().isBlank()) {
            signedPhotoUrl = awsService.getPreSignedUrl(user.getPhotoUrl());
        }
        userBasicDetails.setPhotoUrl(signedPhotoUrl);

        // Set role in response using the role already fetched earlier
        if (userRole != null) {
            RoleResponse roleResponse = new RoleResponse();
            roleResponse.setId(userRole.getId());
            roleResponse.setName(userRole.getName());
            userBasicDetails.setRole(roleResponse);
        }

        LoginResponse loginResponse = new LoginResponse();
        loginResponse.setToken(token);
        loginResponse.setUserBasicDetails(userBasicDetails);

        LoginResponseDto<LoginResponse> loginResponseDto = new LoginResponseDto<>();
        loginResponseDto.setLoginAudit(loginResponse);

        ResponseDto<LoginResponseDto<LoginResponse>> responseDto = new ResponseDto<>();
        responseDto.setMessage(messageUtil.getMessage("user.login.success", userLocale));
        responseDto.setData(loginResponseDto);

        return ResponseEntity.ok(responseDto);
    }

    @Override
    @Transactional(readOnly = true)
    public ResponseDto<UserAccountDataResponse> getUserAccountDetails(UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + userId));

        UserAccountDataResponse data = buildUserAccountDataResponse(user);

        return ResponseDto.<UserAccountDataResponse>builder()
                .data(data)
                .message("User account details retrieved successfully")
                .build();
    }

    /**
     * Builds a full UserAccountDataResponse from a User entity.
     * Includes shift details, restaurant details with translations,
     * restaurant group details with translations, and role name.
     * Used by getUserAccountDetails, registerUser, and updateUser to return consistent full user data.
     */
    private UserAccountDataResponse buildUserAccountDataResponse(User user) {
        String signedPhotoUrl = null;
        if (user.getPhotoUrl() != null && !user.getPhotoUrl().isBlank()) {
            signedPhotoUrl = awsService.getPreSignedUrl(user.getPhotoUrl());
        }
        UserAccountDetailsDto.ShiftDetails shiftDetails = null;
        var userShiftMapping = userShiftMappingRepository.findFirstByUser_IdWithShift(user.getId()).orElse(null);
        if (userShiftMapping != null && userShiftMapping.getShift() != null) {
            Shift shift = userShiftMapping.getShift();
            String shiftName = getShiftNameFromShift(shift, LocaleContextHolder.getLocale().getLanguage());

            shiftDetails = UserAccountDetailsDto.ShiftDetails.builder()
                .shiftId(shift.getId())
                .shiftName(shiftName)
                .startTime(shift.getStartTime())
                .endTime(shift.getEndTime())
                .build();
        }

        List<RestaurantGroupTranslationDTO> restaurantGroupTranslations = null;
        List<RestaurantTranslationDto> restaurantTranslations = null;
        UserAccountDetailsDto.RestaurantGroupDetails restaurantGroupDetails = null;
        UserAccountDetailsDto.RestaurantDetails restaurantDetails = null;

        if (user.getRestaurantId() != null) {
            var restaurant = restaurantRepository.findByIdWithGroup(user.getRestaurantId()).orElse(null);
            if (restaurant != null) {
                var restaurantTranslationEntities = restaurantTranslationRepository.findAllByRestaurantIdWithLanguage(restaurant.getId());
                restaurantTranslations = restaurantTranslationEntities.stream()
                    .filter(rt -> rt.getLanguageCode() != null)
                    .map(rt -> RestaurantTranslationDto.builder()
                        .languageCode(rt.getLanguageCode())
                        .name(rt.getName())
                        .build())
                    .collect(Collectors.toList());

                restaurantDetails = UserAccountDetailsDto.RestaurantDetails.builder()
                    .restaurantId(restaurant.getId())
                    .translations(restaurantTranslations)
                    .build();

                if (restaurant.getRestaurantGroup() != null) {
                    var groupTranslationEntities = restaurantGroupTranslationRepository.findAllByRestaurantGroupIdWithLanguage(restaurant.getRestaurantGroup().getId());
                    restaurantGroupTranslations = groupTranslationEntities.stream()
                        .map(gt -> RestaurantGroupTranslationDTO.builder()
                            .languageCode(gt.getLanguageCode())
                            .name(gt.getName())
                            .build())
                        .collect(Collectors.toList());

                    restaurantGroupDetails = UserAccountDetailsDto.RestaurantGroupDetails.builder()
                        .restaurantGroupId(restaurant.getRestaurantGroup().getId())
                        .translations(restaurantGroupTranslations)
                        .build();
                }
            }
        }

        String roleName = null;
        if (user.getRoleId() != null) {
            var role = roleRepository.findById(user.getRoleId()).orElse(null);
            if (role != null) {
                roleName = role.getName();
            }
        }

        UserAccountDetailsDto userDetails = UserAccountDetailsDto.builder()
                .id(user.getId())
                .firstName(user.getFirstName())
                .lastName(user.getLastName())
                .email(user.getEmail())
                .contactNumber(user.getContactNumber())
                .photoUrl(signedPhotoUrl)
                .userCode(user.getUserCode())
                .employmentType(user.getEmploymentType())
                .status(user.getStatus())
                .languageCode(user.getLanguageCode())
                .roleId(user.getRoleId())
                .roleName(roleName)
                .isStatusLocked(user.getIsStatusLocked())
                .shiftDetails(shiftDetails)
                .restaurantGroupDetails(restaurantGroupDetails)
                .restaurantDetails(restaurantDetails)
                .build();

        return UserAccountDataResponse.builder()
                .user(userDetails)
                .count(1L)
                .total(1L)
                .metaData(null)
                .build();
    }

    @Transactional(readOnly = true)
    public ResponseDto<UserListResponse> getEmployees(
        int page,
        int size,
        UUID roleId,
        String status,
        String employmentType,
        String search,
        String sortBy,
        Sort.Direction direction,
        String restaurantStatus,
        UUID restaurantId,
        UUID restaurantGroupId,
        String localeHeader,
        Boolean isDeleted) {
    
    Locale userLocale = LocaleContextHolder.getLocale();
    
    // Get locale string from header or use LocaleContextHolder, with proper format
    String locale = localeHeader != null && !localeHeader.trim().isEmpty() 
        ? localeHeader.toLowerCase() 
        : (userLocale != null ? userLocale.getLanguage() : "en");

    // Validate & normalize pagination (like menu service)
    int pageNumber = (page > 0 ? page : 1) - 1;
    if (pageNumber < 0) pageNumber = 0;

    // Declare as final and validate inline
    final EntityStatus entityStatus;
    final EmploymentType empType;

    try {
        entityStatus = status != null ? EntityStatus.valueOf(status) : null;
    } catch (IllegalArgumentException ex) {
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                messageUtil.getMessage("shift.fetch.invalid.status", userLocale));
    }

    try {
        empType = employmentType != null ? EmploymentType.valueOf(employmentType) : null;
    } catch (IllegalArgumentException ex) {
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                messageUtil.getMessage("user.employees.fetch.invalid.employmenttype", userLocale));
    }

    // Build dynamic specification (replaces all if-else conditions)
    Specification<User> spec = (root, query, criteriaBuilder) -> {
        List<Predicate> predicates = new ArrayList<>();
        
        // Handle isDeleted filter: if isDeleted=true, show deleted; otherwise show non-deleted (default)
        if (isDeleted != null && isDeleted) {
            predicates.add(criteriaBuilder.equal(root.get("isDeleted"), true));
        } else {
            predicates.add(criteriaBuilder.equal(root.get("isDeleted"), false));
        }
        
        // Restaurant ID filter
        if (restaurantId != null) {
            predicates.add(criteriaBuilder.equal(root.get("restaurantId"), restaurantId));
        }
        
        // Restaurant Group ID filter - filter users by restaurant group through restaurant
        if (restaurantGroupId != null) {
            Subquery<UUID> restaurantSubquery = query.subquery(UUID.class);
            Root<Restaurant> restaurantRoot = restaurantSubquery.from(Restaurant.class);
            restaurantSubquery.select(restaurantRoot.get("id"));
            restaurantSubquery.where(
                criteriaBuilder.and(
                    criteriaBuilder.equal(restaurantRoot.get("restaurantGroup").get("id"), restaurantGroupId),
                    criteriaBuilder.equal(restaurantRoot.get("isDeleted"), false)
                )
            );
            predicates.add(root.get("restaurantId").in(restaurantSubquery));
        }
        
        // Role ID filter
        if (roleId != null) {
            predicates.add(criteriaBuilder.equal(root.get("roleId"), roleId));
        }
        
        // Status filter
        if (entityStatus != null) {
            predicates.add(criteriaBuilder.equal(root.get("status"), entityStatus));
        }
        
        // Employment type filter
        if (empType != null) {
            predicates.add(criteriaBuilder.equal(root.get("employmentType"), empType));
        }
        
        // Search filter (first name, last name, full name, email, user code)
        if (search != null && !search.trim().isEmpty()) {
            String normalizedSearch = search.trim().toLowerCase();
            String searchPattern = "%" + normalizedSearch + "%";

            // Lowercased individual name fields
            Expression<String> firstNameExpr = criteriaBuilder.lower(root.get("firstName"));
            Expression<String> lastNameExpr = criteriaBuilder.lower(root.get("lastName"));
            Expression<String> emailExpr = criteriaBuilder.lower(root.get("email"));
            Expression<String> userCodeExpr = criteriaBuilder.lower(root.get("userCode"));

            // Full name variants to support searches like "nl gupta"
            Expression<String> fullNameWithSpace = criteriaBuilder.lower(
                criteriaBuilder.concat(
                    criteriaBuilder.concat(root.get("firstName"), " "),
                    root.get("lastName")
                )
            );
            Expression<String> fullNameNoSpace = criteriaBuilder.lower(
                criteriaBuilder.concat(root.get("firstName"), root.get("lastName"))
            );

            Predicate firstNamePredicate = criteriaBuilder.like(firstNameExpr, searchPattern);
            Predicate lastNamePredicate = criteriaBuilder.like(lastNameExpr, searchPattern);
            Predicate fullNameWithSpacePredicate = criteriaBuilder.like(fullNameWithSpace, searchPattern);
            Predicate fullNameNoSpacePredicate = criteriaBuilder.like(fullNameNoSpace, searchPattern);
            Predicate emailPredicate = criteriaBuilder.like(emailExpr, searchPattern);
            Predicate userCodePredicate = criteriaBuilder.like(userCodeExpr, searchPattern);
            
            predicates.add(criteriaBuilder.or(
                firstNamePredicate,
                lastNamePredicate,
                fullNameWithSpacePredicate,
                fullNameNoSpacePredicate,
                emailPredicate,
                userCodePredicate
            ));
        }
        
        // Restaurant assignment filter
        if (restaurantStatus != null) {
            boolean isAssigned = restaurantStatus.equalsIgnoreCase("ASSIGN");
            if (isAssigned) {
                predicates.add(criteriaBuilder.isNotNull(root.get("restaurantId")));
            } else {
                predicates.add(criteriaBuilder.isNull(root.get("restaurantId")));
            }
        }
        
        return criteriaBuilder.and(predicates.toArray(new Predicate[0]));
    };

    // Validate and set pagination
    int pageSize = size > 0 ? size : 100; // Default to 100 if not specified
    
    // Keep in-memory sorting only for derived field "employeeName".
    // For real DB columns (e.g. createdAt/userCode), use DB sorting + pagination.
    boolean requiresInMemorySorting = "employeeName".equalsIgnoreCase(sortBy);
    
    List<User> users;
    Page<User> userPage = null;
    
    // Fetch data based on sorting requirements
    if (requiresInMemorySorting) {
        // Fetch ALL matching records for in-memory sorting (like ItemServiceImpl and MenuServiceImpl)
        users = ((JpaSpecificationExecutor<User>) userRepository).findAll(spec);
    } else {
        // Use database-level sorting and pagination for other fields
        Sort sort = Sort.by(direction, sortBy != null ? sortBy : "createdAt");
        Pageable pageable = PageRequest.of(pageNumber, pageSize, sort);
        userPage = ((JpaSpecificationExecutor<User>) userRepository).findAll(spec, pageable);
        users = userPage.getContent();
    }
    
    if (users.isEmpty()) {
        PaginationMetaData metaData = PaginationMetaData.builder()
            .page(pageNumber + 1)
            .size(pageSize)
            .totalPages(0)
            .totalRecords(0L)
            .build();
        
        UserListResponse data = UserListResponse.builder()
            .users(Collections.emptyList())
            .count(0L)
            .total(0L)
            .metaData(metaData)
            .build();
        
        return ResponseDto.<UserListResponse>builder()
            .message(messageUtil.getMessage("user.employees.fetch.success", userLocale))
            .data(data)
            .build();
    }
    
    // Batch load all related data to avoid N+1 queries
    // 1. Collect all unique IDs
    Set<UUID> roleIds = users.stream()
        .map(User::getRoleId)
        .filter(Objects::nonNull)
        .collect(Collectors.toSet());
    
    Set<UUID> restaurantIds = users.stream()
        .map(User::getRestaurantId)
        .filter(Objects::nonNull)
        .collect(Collectors.toSet());
    
    // 2. Batch load roles
    final Map<UUID, Role> rolesMap;
    if (!roleIds.isEmpty()) {
        rolesMap = roleRepository.findAllById(roleIds)
            .stream()
            .collect(Collectors.toMap(Role::getId, role -> role));
    } else {
        rolesMap = Collections.emptyMap();
    }
    
    // 3. Batch load restaurants
    final Map<UUID, Restaurant> restaurantsMap;
    final Set<UUID> restaurantGroupIds;
    if (!restaurantIds.isEmpty()) {
        restaurantsMap = restaurantRepository.findAllById(restaurantIds)
            .stream()
            .collect(Collectors.toMap(Restaurant::getId, restaurant -> restaurant));
        
        // Collect restaurant group IDs
        restaurantGroupIds = restaurantsMap.values().stream()
            .map(r -> r.getRestaurantGroup() != null ? r.getRestaurantGroup().getId() : null)
            .filter(Objects::nonNull)
            .collect(Collectors.toSet());
    } else {
        restaurantsMap = Collections.emptyMap();
        restaurantGroupIds = Collections.emptySet();
    }
    
    // 4. Batch load restaurant translations
    final Map<UUID, List<RestaurantTranslation>> restaurantTranslationsMap;
    if (!restaurantIds.isEmpty()) {
        restaurantTranslationsMap = restaurantTranslationRepository
            .findAllByRestaurantIdIn(new ArrayList<>(restaurantIds))
            .stream()
            .collect(Collectors.groupingBy(t -> t.getRestaurant().getId()));
    } else {
        restaurantTranslationsMap = Collections.emptyMap();
    }
    
    // 5. Batch load restaurant group translations
    final Map<UUID, List<RestaurantGroupTranslation>> groupTranslationsMap;
    if (!restaurantGroupIds.isEmpty()) {
        groupTranslationsMap = restaurantGroupTranslationRepository
            .findAllByRestaurantGroupIdIn(new ArrayList<>(restaurantGroupIds))
            .stream()
            .collect(Collectors.groupingBy(t -> t.getRestaurantGroup().getId()));
    } else {
        groupTranslationsMap = Collections.emptyMap();
    }
    
    // Convert to DTOs using batch-loaded data
    List<UserBasicDetailsResponse> employeeDtos = users.stream().map(user -> {
        UserBasicDetailsResponse dto = new UserBasicDetailsResponse();
        dto.setId(user.getId());
        dto.setUserCode(user.getUserCode());
        dto.setFirstName(user.getFirstName());
        dto.setLastName(user.getLastName());
        dto.setContactNumber(user.getContactNumber());
        dto.setEmploymentType(user.getEmploymentType());
        dto.setEmail(user.getEmail());
        dto.setStatus(user.getStatus());
        dto.setCreatedAt(user.getCreatedAt() != null ? user.getCreatedAt().toLocalDateTime() : null);
        
        // Set role from batch-loaded map
        if (user.getRoleId() != null) {
            Role role = rolesMap.get(user.getRoleId());
            if (role != null) {
                RoleResponse roleResponse = new RoleResponse();
                roleResponse.setId(role.getId());
                roleResponse.setName(role.getName());
                dto.setRole(roleResponse);
            }
        }
        
        // Set restaurant and restaurant group from batch-loaded maps
        if (user.getRestaurantId() != null) {
            Restaurant restaurant = restaurantsMap.get(user.getRestaurantId());
            if (restaurant != null) {
                dto.setRestaurantId(restaurant.getId());
                
                // Get restaurant name from batch-loaded translations
                List<RestaurantTranslation> restaurantTranslations = restaurantTranslationsMap
                    .getOrDefault(restaurant.getId(), Collections.emptyList());
                
                String restaurantName = "";
                if (!restaurantTranslations.isEmpty()) {
                    RestaurantTranslation exactMatch = restaurantTranslations.stream()
                        .filter(t -> t.getLanguageCode() != null && t.getLanguageCode().equals(locale))
                        .findFirst()
                        .orElse(null);
                    
                    if (exactMatch != null) {
                        restaurantName = exactMatch.getName();
                    } else {
                        Optional<RestaurantTranslation> fallback = TranslationUtils.pickPreferredOrFromList(
                            restaurantTranslations,
                            locale,
                            localizationProperties.getLanguages(),
                            RestaurantTranslation::getLanguageCode
                        );
                        restaurantName = fallback.map(RestaurantTranslation::getName)
                            .orElseGet(() -> restaurantTranslations.stream()
                                .filter(t -> t.getName() != null && !t.getName().isEmpty())
                                .findFirst()
                                .map(RestaurantTranslation::getName)
                                .orElse(""));
                    }
                }
                dto.setRestaurantName(restaurantName);
                
                // Get restaurant group name from batch-loaded translations
                if (restaurant.getRestaurantGroup() != null) {
                    dto.setRestaurantGroupId(restaurant.getRestaurantGroup().getId());
                    
                    List<RestaurantGroupTranslation> groupTranslations = groupTranslationsMap
                        .getOrDefault(restaurant.getRestaurantGroup().getId(), Collections.emptyList());
                    
                    String restaurantGroupName = "";
                    if (!groupTranslations.isEmpty()) {
                        RestaurantGroupTranslation exactMatch = groupTranslations.stream()
                            .filter(t -> t.getLanguageCode() != null && t.getLanguageCode().equals(locale))
                            .findFirst()
                            .orElse(null);
                        
                        if (exactMatch != null) {
                            restaurantGroupName = exactMatch.getName();
                        } else {
                            Optional<RestaurantGroupTranslation> fallback = TranslationUtils.pickPreferredOrFromList(
                                groupTranslations,
                                locale,
                                localizationProperties.getLanguages(),
                                RestaurantGroupTranslation::getLanguageCode
                            );
                            restaurantGroupName = fallback.map(RestaurantGroupTranslation::getName)
                                .orElse("");
                        }
                    }
                    dto.setRestaurantGroupName(restaurantGroupName);
                }
            }
        }
        
        return dto;
    }).collect(Collectors.toList());

    // Apply in-memory sorting for employeeName, userCode, and createdAt
    if (requiresInMemorySorting) {
        if ("employeeName".equalsIgnoreCase(sortBy)) {
            Comparator<UserBasicDetailsResponse> comp = Comparator.comparing(
                dto -> {
                    String firstName = dto.getFirstName() != null ? dto.getFirstName() : "";
                    String lastName = dto.getLastName() != null ? dto.getLastName() : "";
                    return (firstName + " " + lastName).trim().toLowerCase();
                },
                Comparator.nullsLast(Comparator.naturalOrder())
            );
            if (direction == Sort.Direction.DESC) comp = comp.reversed();
            employeeDtos.sort(comp);
        } else if ("userCode".equalsIgnoreCase(sortBy)) {
            Comparator<UserBasicDetailsResponse> comp = Comparator.comparing(
                dto -> dto.getUserCode() != null ? dto.getUserCode().toLowerCase() : "",
                Comparator.nullsLast(Comparator.naturalOrder())
            );
            if (direction == Sort.Direction.DESC) comp = comp.reversed();
            employeeDtos.sort(comp);
        } else if ("createdAt".equalsIgnoreCase(sortBy)) {
            Comparator<UserBasicDetailsResponse> comp = Comparator.comparing(
                UserBasicDetailsResponse::getCreatedAt,
                Comparator.nullsLast(Comparator.naturalOrder())
            );
            if (direction == Sort.Direction.DESC) comp = comp.reversed();
            employeeDtos.sort(comp);
        }
    }
    // Note: For other fields (updatedAt, etc.), sorting is done at database level

    // Apply pagination (manual for in-memory sorting, already done for DB sorting)
    long totalRecords;
    int totalPages;
    List<UserBasicDetailsResponse> paginatedDtos;
    
    if (requiresInMemorySorting) {
        // Manual pagination slice (like ItemServiceImpl and MenuServiceImpl)
        totalRecords = employeeDtos.size();
        totalPages = (int) Math.ceil((double) totalRecords / pageSize);
        int fromIndex = Math.min(pageNumber * pageSize, employeeDtos.size());
        int toIndex = Math.min(fromIndex + pageSize, employeeDtos.size());
        paginatedDtos = employeeDtos.subList(fromIndex, toIndex);
    } else {
        // Already paginated by database - userPage is guaranteed to be initialized here
        // because we're in the else branch where userPage was assigned
        totalRecords = userPage.getTotalElements();
        totalPages = userPage.getTotalPages();
        paginatedDtos = employeeDtos;
    }

    // Build pagination metadata
    PaginationMetaData metaData = PaginationMetaData.builder()
        .page(pageNumber + 1)
        .size(pageSize)
        .totalPages(totalPages)
        .totalRecords(totalRecords)
        .build();

    UserListResponse data = UserListResponse.builder()
        .users(paginatedDtos)
        .count((long) paginatedDtos.size())
        .total(totalRecords)
        .metaData(metaData)
        .build();

    return ResponseDto.<UserListResponse>builder()
        .message(messageUtil.getMessage("user.employees.fetch.success", userLocale))
        .data(data)
        .build();
}


    @Transactional
    @CacheEvict(value = "restaurants", allEntries = true)
    public ResponseDto<UserAccountDataResponse> updateUser(UUID userId, UpdateUserRequest request, String updaterId, String updaterRole) {

        Locale userLocale = LocaleContextHolder.getLocale();

        // ========================================
        // STEP 1: BASIC VALIDATIONS
        // ========================================
        
        // Validate user exists and is not deleted
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        messageUtil.getMessage("user.not.found", userLocale)));

        if (Boolean.TRUE.equals(user.getIsDeleted())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    messageUtil.getMessage("user.update.error.deleted", userLocale));
        }

        // Validate updater exists
        User updater = userRepository.findById(UUID.fromString(updaterId))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        messageUtil.getMessage("user.not.found", userLocale)));

        // ========================================
        // STEP 2: REQUEST DATA VALIDATIONS
        // ========================================
        
        // Validate language code only if provided (allow partial updates)
        if (request.getLanguageCode() != null && !localizationProperties.getLanguages().contains(request.getLanguageCode())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    messageUtil.getMessage("user.update.error.invalid.language", userLocale));
        }
        
        // For partial updates, fill in missing required fields from existing user data
        if (request.getUserCode() == null || request.getUserCode().trim().isEmpty()) {
            request.setUserCode(user.getUserCode());
        }
        if (request.getFirstName() == null || request.getFirstName().trim().isEmpty()) {
            request.setFirstName(user.getFirstName());
        }
        if (request.getLastName() == null || request.getLastName().trim().isEmpty()) {
            request.setLastName(user.getLastName());
        }
        // Email is optional - if not provided, it will be set to null (cleared)
        // Do not auto-fill email to allow clearing it
        if (request.getContactNumber() == null || request.getContactNumber().trim().isEmpty()) {
            request.setContactNumber(user.getContactNumber());
        }
        if (request.getLanguageCode() == null || request.getLanguageCode().trim().isEmpty()) {
            request.setLanguageCode(user.getLanguageCode());
        }
        if (request.getEmploymentType() == null) {
            request.setEmploymentType(user.getEmploymentType());
        }
        // Auto-fill shiftId from current user shift mapping if not provided
        if (request.getShiftId() == null) {
            var currentUserShiftMapping = userShiftMappingRepository.findFirstByUser_Id(userId).orElse(null);
            if (currentUserShiftMapping != null && currentUserShiftMapping.getShift() != null) {
                request.setShiftId(currentUserShiftMapping.getShift().getId());
            }
        }

        // Validate role ID if provided
        if (request.getRoleId() != null) {
            roleRepository.findById(request.getRoleId())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST,
                            messageUtil.getMessage("user.update.error.invalid.roleid", userLocale)));
        }

        // Validate restaurant ID if provided
        if (request.getRestaurantId() != null) {
            restaurantRepository.findById(request.getRestaurantId())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST,
                            messageUtil.getMessage("user.update.error.invalid.restaurantid", userLocale)));
        }

        // Validate shift ID if provided
        if (request.getShiftId() != null) {
            shiftRepository.findById(request.getShiftId())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST,
                            messageUtil.getMessage("user.update.error.invalid.shiftid", userLocale)));
        }

        // ========================================
        // STEP 3: BUSINESS LOGIC VALIDATIONS
        // ========================================
        
        // If user's status is locked and updater is not HQ_ADMIN, submit a profile update request instead of applying changes
        if (Boolean.TRUE.equals(user.getIsStatusLocked()) && !"HQ_ADMIN".equalsIgnoreCase(updaterRole)) {
            // Check if it's a self-update for locked users
            boolean isSelfUpdateLocked = userId.toString().equals(updaterId);
            boolean isManagerUpdate = "MANAGER".equalsIgnoreCase(updaterRole);
            
            // Track if photo was updated directly
            boolean photoUpdatedDirectly = false;
            
            // Check if ONLY photo is being changed (before updating anything)
            // Normalize userCode for comparison
            String normalizedRequestUserCodeForCheck1 = request.getUserCode() != null ? request.getUserCode().trim().toLowerCase() : null;
            String normalizedCurrentUserCodeForCheck1 = user.getUserCode() != null ? user.getUserCode().toLowerCase() : null;
            
            String currentPhotoUrlBeforeUpdate = (user.getPhotoUrl() != null && !user.getPhotoUrl().isEmpty()) ? awsService.stripToKey(user.getPhotoUrl()) : null;
            String requestedPhotoUrl = (request.getPhotoUrl() != null && !request.getPhotoUrl().isEmpty()) ? awsService.stripToKey(request.getPhotoUrl()) : null;
            boolean photoIsChanging = !Objects.equals(currentPhotoUrlBeforeUpdate, requestedPhotoUrl);
            
            // Get current shift ID from user shift mapping
            UUID currentShiftIdForCheck = null;
            var currentUserShiftMappingForCheck = userShiftMappingRepository.findFirstByUser_Id(userId).orElse(null);
            if (currentUserShiftMappingForCheck != null && currentUserShiftMappingForCheck.getShift() != null) {
                currentShiftIdForCheck = currentUserShiftMappingForCheck.getShift().getId();
            }
            
            boolean onlyPhotoIsChanging = photoIsChanging &&
                    Objects.equals(user.getFirstName(), request.getFirstName()) &&
                    Objects.equals(user.getLastName(), request.getLastName()) &&
                    Objects.equals(user.getEmail(), request.getEmail()) &&
                    Objects.equals(user.getContactNumber(), request.getContactNumber()) &&
                    Objects.equals(user.getLanguageCode(), request.getLanguageCode()) &&
                    Objects.equals(user.getEmploymentType(), request.getEmploymentType()) &&
                    Objects.equals(currentShiftIdForCheck, request.getShiftId()) &&
                    Objects.equals(normalizedCurrentUserCodeForCheck1, normalizedRequestUserCodeForCheck1);
            
            // If MANAGER is updating ONLY photo, or self-update with photo, apply photo directly
            if ((isManagerUpdate && onlyPhotoIsChanging) || (isSelfUpdateLocked && request.getPhotoUrl() != null)) {
                String currentPhotoUrl = (user.getPhotoUrl() != null && !user.getPhotoUrl().isEmpty()) ? awsService.stripToKey(user.getPhotoUrl()) : null;
                String requestedPhotoUrlToApply = (request.getPhotoUrl() != null && !request.getPhotoUrl().isEmpty()) ? awsService.stripToKey(request.getPhotoUrl()) : null;
                
                // Apply photo update immediately if it's different
                if (!Objects.equals(currentPhotoUrl, requestedPhotoUrlToApply)) {
                    user.setPhotoUrl(requestedPhotoUrlToApply);
                    user.setUpdatedAt(OffsetDateTime.now(ZoneOffset.UTC));
                    user.setUpdatedBy(updater);
                    userRepository.save(user);
                    photoUpdatedDirectly = true;
                    if (isManagerUpdate && onlyPhotoIsChanging) {
                        log.info("Photo updated directly for locked user {} by manager {} (photo-only update)", userId, updaterId);
                    } else {
                        log.info("Photo updated directly for locked user {} as they can update their own photo", userId);
                    }
                }
            }

            // Check if only photo was changed (and it was updated directly)
            boolean onlyPhotoChanged = photoUpdatedDirectly && onlyPhotoIsChanging;
            
            // If only photo was changed and it was updated directly, return success immediately
            // Allow photo updates even when there's a pending request (photo can be updated directly)
            if (onlyPhotoChanged) {
                return ResponseDto.<UserAccountDataResponse>builder()
                        .message(messageUtil.getMessage("user.update.success", userLocale))
                        .data(buildUserAccountDataResponse(user))
                        .build();
            }
            
            // Reject if an OPEN request already exists (only for non-photo updates)
            // Photo-only updates are allowed even with pending requests (handled above)
            if (user.getProfileUpdateRequestStatus() == RequestStatus.OPEN) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        messageUtil.getMessage("user.profile.update.request.already.pending", userLocale));
            }

            // Check email uniqueness if email is being changed
            if (!Objects.equals(user.getEmail(), request.getEmail()) && 
                request.getEmail() != null &&
                userRepository.existsByEmailIgnoreCase(request.getEmail())) {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        messageUtil.getMessage("user.update.error.email.exists", userLocale));
            }

            // Validate that there are actual changes before creating a request
            // Include photoUpdatedDirectly flag in hasChanges to treat photo change as valid modification
            String currentPhotoUrl = (user.getPhotoUrl() != null && !user.getPhotoUrl().isEmpty()) ? awsService.stripToKey(user.getPhotoUrl()) : null;
            // requestedPhotoUrl already declared above, just update its value if needed
            if (!photoUpdatedDirectly) {
                // Only check photo changes if photo wasn't already updated directly
                requestedPhotoUrl = (request.getPhotoUrl() != null && !request.getPhotoUrl().isEmpty()) ? awsService.stripToKey(request.getPhotoUrl()) : null;
            } else {
                // Photo was updated directly, so don't include it in the request
                requestedPhotoUrl = null;
            }
            
            // Check userCode changes (case-insensitive comparison)
            // Store original userCode to preserve case, use normalized version only for comparison
            String originalRequestUserCode = request.getUserCode() != null ? request.getUserCode().trim() : null;
            String normalizedRequestUserCode = originalRequestUserCode != null ? originalRequestUserCode.toLowerCase() : null;
            String normalizedCurrentUserCode = user.getUserCode() != null ? user.getUserCode().toLowerCase() : null;
            
            // Normalize strings for comparison (handle null and empty strings consistently)
            String currentFirstName = user.getFirstName() != null ? user.getFirstName().trim() : null;
            String requestFirstName = request.getFirstName() != null ? request.getFirstName().trim() : null;
            String currentLastName = user.getLastName() != null ? user.getLastName().trim() : null;
            String requestLastName = request.getLastName() != null ? request.getLastName().trim() : null;
            String currentEmail = user.getEmail() != null ? user.getEmail().trim() : null;
            String requestEmail = request.getEmail() != null ? request.getEmail().trim() : null;
            String currentContactNumber = user.getContactNumber() != null ? user.getContactNumber().trim() : null;
            String requestContactNumber = request.getContactNumber() != null ? request.getContactNumber().trim() : null;
            String currentLanguageCode = user.getLanguageCode() != null ? user.getLanguageCode().trim() : null;
            String requestLanguageCode = request.getLanguageCode() != null ? request.getLanguageCode().trim() : null;
            
            // Check for actual changes in profile fields (password is not part of profile update request)
            boolean firstNameChanged = !Objects.equals(currentFirstName, requestFirstName);
            boolean lastNameChanged = !Objects.equals(currentLastName, requestLastName);
            boolean emailChanged = !Objects.equals(currentEmail, requestEmail);
            boolean contactNumberChanged = !Objects.equals(currentContactNumber, requestContactNumber);
            boolean photoUrlChanged = !photoUpdatedDirectly && !Objects.equals(currentPhotoUrl, requestedPhotoUrl);
            boolean languageCodeChanged = !Objects.equals(currentLanguageCode, requestLanguageCode);
            boolean userCodeChanged = !Objects.equals(normalizedCurrentUserCode, normalizedRequestUserCode);
            
            // Check for changes in employment details and work schedule (these should be allowed for MANAGER)
            boolean employmentTypeChanged = !Objects.equals(user.getEmploymentType(), request.getEmploymentType());
            
            // Check shift changes - get current shift ID from user shift mapping
            UUID currentShiftId = null;
            var currentUserShiftMapping = userShiftMappingRepository.findFirstByUser_Id(userId).orElse(null);
            if (currentUserShiftMapping != null && currentUserShiftMapping.getShift() != null) {
                currentShiftId = currentUserShiftMapping.getShift().getId();
            }
            boolean shiftIdChanged = !Objects.equals(currentShiftId, request.getShiftId());
            
            boolean hasChanges = firstNameChanged || lastNameChanged || emailChanged || 
                    contactNumberChanged || photoUrlChanged || languageCodeChanged || 
                    userCodeChanged || photoUpdatedDirectly || employmentTypeChanged || shiftIdChanged; // Include employment and shift changes
            
            // Log change detection for debugging
            if (log.isDebugEnabled()) {
                log.debug("Profile update change detection for user {}: firstName={}, lastName={}, email={}, contactNumber={}, photoUrl={}, languageCode={}, userCode={}, employmentType={}, shiftId={}, photoUpdatedDirectly={}, hasChanges={}",
                        userId, firstNameChanged, lastNameChanged, emailChanged, contactNumberChanged, 
                        photoUrlChanged, languageCodeChanged, userCodeChanged, employmentTypeChanged, shiftIdChanged, photoUpdatedDirectly, hasChanges);
            }
            
            if (!hasChanges) {
                log.warn("Profile update request attempted for user {} with no actual changes. This may indicate a password-only update or duplicate request.", userId);
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        messageUtil.getMessage("user.profile.update.no.changes", userLocale));
            }

            try {
                // Build profile update request payload from incoming request
                // Use current values as fallback for null fields to ensure complete data in request
                // Exclude photoUrl if it's a self-update or manager photo-only update (already updated directly above)
                UserProfileUpdateRequest profileRequest = new UserProfileUpdateRequest();
                profileRequest.setFirstName(request.getFirstName() != null ? request.getFirstName() : user.getFirstName());
                profileRequest.setLastName(request.getLastName() != null ? request.getLastName() : user.getLastName());
                // Email is optional - if not provided, set to null to allow clearing it
                profileRequest.setEmail(request.getEmail());
                profileRequest.setContactNumber(request.getContactNumber() != null ? request.getContactNumber() : user.getContactNumber());
                profileRequest.setUserCode(originalRequestUserCode != null ? originalRequestUserCode : user.getUserCode()); // Store original case
                // Only include photoUrl in request if it wasn't updated directly
                if (!photoUpdatedDirectly) {
                    profileRequest.setPhotoUrl(requestedPhotoUrl);
                } else {
                    // Photo already updated directly, exclude from request
                    profileRequest.setPhotoUrl(null);
                }
                profileRequest.setLanguageCode(request.getLanguageCode() != null ? request.getLanguageCode() : user.getLanguageCode());

                // Double-check: Verify that the request data actually differs from current user data
                // This is a safety check to prevent creating requests with no changes
                boolean requestDataHasChanges = 
                        !Objects.equals(currentFirstName, profileRequest.getFirstName()) ||
                        !Objects.equals(currentLastName, profileRequest.getLastName()) ||
                        !Objects.equals(currentEmail, profileRequest.getEmail()) ||
                        !Objects.equals(currentContactNumber, profileRequest.getContactNumber()) ||
                        (!photoUpdatedDirectly && !Objects.equals(currentPhotoUrl, profileRequest.getPhotoUrl())) ||
                        !Objects.equals(currentLanguageCode, profileRequest.getLanguageCode()) ||
                        !Objects.equals(normalizedCurrentUserCode, 
                                profileRequest.getUserCode() != null ? profileRequest.getUserCode().toLowerCase() : null) ||
                        employmentTypeChanged || shiftIdChanged;
                
                if (!requestDataHasChanges) {
                    log.error("Profile update request data validation failed for user {}: Request data matches current user data exactly. This should not happen after change detection.", userId);
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                            messageUtil.getMessage("user.profile.update.no.changes", userLocale));
                }

                ObjectMapper objectMapper = new ObjectMapper();
                String requestDataJson = objectMapper.writeValueAsString(profileRequest);

                // Create new request - clear review fields from previous request
                user.setProfileUpdateRequestStatus(RequestStatus.OPEN);
                user.setProfileUpdateRequestData(requestDataJson);
                user.setProfileUpdateRequestedAt(OffsetDateTime.now(ZoneOffset.UTC));
                // Store the locked status at the time of request creation to ensure consistent authorization
                user.setProfileUpdateRequestLockedStatus(user.getIsStatusLocked());
                // Clear review fields from previous APPROVED/DECLINED request to ensure this is treated as a new request
                user.setProfileUpdateReviewedAt(null);
                user.setUpdatedAt(OffsetDateTime.now(ZoneOffset.UTC));
                user.setUpdatedBy(updater);

                userRepository.save(user);
                
                log.info("Profile update request created for user {} by updater {} with changes: firstName={}, lastName={}, email={}, contactNumber={}, photoUrl={}, languageCode={}, userCode={}",
                        userId, updaterId, firstNameChanged, lastNameChanged, emailChanged,
                        contactNumberChanged, photoUrlChanged, languageCodeChanged, userCodeChanged);

                // Notify approvers (HQ Admins) about newly opened profile update request for locked users
                // IMPORTANT:
                // - We create Notification rows in this service's database
                // - We also publish them to RabbitMQ so restaurant-management can mirror them for listing
                try {
                    boolean isLocked = Boolean.TRUE.equals(user.getIsStatusLocked());
                    List<User> approvers = new ArrayList<>();

                    // For locked users, HQ Admin should approve (regardless of requester role)
                    if (isLocked) {
                        Optional<Role> hqAdminRoleOpt = roleRepository.findByName("HQ_ADMIN");
                        if (hqAdminRoleOpt.isPresent()) {
                            UUID hqAdminRoleId = hqAdminRoleOpt.get().getId();
                            Pageable pageable = PageRequest.of(0, 1000);
                            Page<User> hqAdminsPage = userRepository.findAllByRoleIdAndIsDeletedFalse(hqAdminRoleId, pageable);
                            approvers = hqAdminsPage.getContent();
                        }
                    }

                    // Save notification to database for each approver (HQ Admin)
                    if (!approvers.isEmpty()) {
                        log.info("Found {} approvers for locked user profile update request for user {}", approvers.size(), user.getId());
                        try {
                            String requesterName = updater != null
                                    ? (updater.getFirstName() + " " + updater.getLastName()).trim()
                                    : (user.getFirstName() + " " + user.getLastName()).trim();
                            String userName = (user.getFirstName() + " " + user.getLastName()).trim();

                            for (User approver : approvers) {
                                try {
                                    // Ensure approver is a managed entity - reload to ensure device_token is loaded
                                    User managedApprover = userRepository.findById(approver.getId())
                                            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                                                    "Approver not found: " + approver.getId()));

                                    // Verify device token is loaded - log for debugging
                                    String approverDeviceToken = managedApprover.getDeviceToken();
                                    log.debug("Approver {} device token loaded (locked user request): {}",
                                            managedApprover.getId(),
                                            approverDeviceToken != null && !approverDeviceToken.trim().isEmpty() ? "YES" : "NO");

                                    Locale approverLoc = localeForRecipient(managedApprover, userLocale);
                                    String title = messageUtil.getMessage("notification.profile.update.request.opened.title", approverLoc);
                                    String message = messageUtil.getMessage("notification.profile.update.request.opened.body", approverLoc, userName, requesterName);

                                    // Use updater as createdBy when available, otherwise fallback to user
                                    User createdByUser = updater != null ? updater : user;
                                    if (createdByUser != null) {
                                        try {
                                            createdByUser = userRepository.findById(createdByUser.getId())
                                                    .orElse(createdByUser);
                                        } catch (Exception e) {
                                            log.warn("Could not reload createdBy user for locked profile update request, using existing entity: {}", e.getMessage());
                                        }
                                    }

                                    java.util.Map<String, String> requestData = new java.util.HashMap<>();
                                    requestData.put("requestId", user.getId().toString());
                                    requestData.put("userId", user.getId().toString());
                                    
                                    Notification notification = Notification.builder()
                                            .user(managedApprover)
                                            .title(title)
                                            .type("PROFILE_UPDATE_REQUEST_OPENED")
                                            .message(message)
                                            .bodyKey("notification.profile.update.request.opened.body")
                                            .bodyArgs(serializeBodyArgs(userName, requesterName))
                                            .additionalData(serializeRequestData(requestData))
                                            .createdBy(createdByUser)
                                            .read(false)
                                            .createdAt(OffsetDateTime.now(ZoneOffset.UTC))
                                            .build();

                                    Notification savedNotification = notificationRepository.saveAndFlush(notification);
                                    log.info("Successfully saved locked-user profile update request notification - ID: {}, approver: {}, user: {}",
                                            savedNotification.getId(), approver.getId(), user.getId());

                                    // Publish notification to RabbitMQ for FCM processing and restaurant-management persistence
                                    try {
                                        notificationPublisherService.publishNotification(savedNotification, managedApprover);
                                    } catch (Exception e) {
                                        log.error("Failed to publish locked-user profile update notification to RabbitMQ for approver {}: {}",
                                                approver.getId(), e.getMessage(), e);
                                    }
                                } catch (Exception e) {
                                    log.error("Failed to save notification for approver {} (locked user request): {}", approver.getId(), e.getMessage(), e);
                                }
                            }
                        } catch (Exception e) {
                            log.error("Failed to save approver notifications for locked user profile update request: {}", e.getMessage(), e);
                        }
                    } else {
                        log.warn("No HQ Admin approvers found for locked user profile update request - user: {}", user.getId());
                    }
                } catch (Exception e) {
                    log.error("Failed to send HQ Admin notification for locked user profile update request: {}", e.getMessage(), e);
                }
                
                // Note: Requester is NOT notified when request is created
                // Requester will be notified when request is approved/declined

                return ResponseDto.<UserAccountDataResponse>builder()
                        .message(messageUtil.getMessage("user.profile.update.request.submitted", userLocale))
                        .data(buildUserAccountDataResponse(user))
                        .build();
            } catch (JsonProcessingException e) {
                log.error("Error converting locked update request to JSON: {}", e.getMessage());
                throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                        messageUtil.getMessage("user.profile.update.request.error", userLocale));
            }
        }

        // Get user's current role information
        Role userRole = null;
        String userRoleName = null;
        boolean requiresApproval = false;
        boolean hasRestrictedRole = false;
        
        if (user.getRoleId() != null) {
            userRole = roleRepository.findById(user.getRoleId())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                            messageUtil.getMessage("user.update.error.role.notfound", userLocale)));
            
            userRoleName = userRole.getName();
            requiresApproval = "CASHIER".equals(userRoleName) || "WAITER".equals(userRoleName) || "KDS".equals(userRoleName);
            hasRestrictedRole = requiresApproval; // Same roles that require approval
        }

        // Check if it's a self-update (user updating their own profile)
        boolean isSelfUpdate = userId.toString().equals(updaterId);

        // Determine if updater can edit directly based on business rules
        boolean canEditDirectly = false;
        if ("HQ_ADMIN".equals(updaterRole)) {
            canEditDirectly = true; // HQ_ADMIN can edit any user
        } else if ("MANAGER".equals(updaterRole)) {
            // MANAGER cannot edit directly - all MANAGER updates must go through HQ_ADMIN approval
            // Exception: MANAGER can update their own photo directly (handled separately)
            canEditDirectly = false;
        }

        // Apply restriction: WAITER, CASHIER, KDS cannot update their own profiles in any case
        if (hasRestrictedRole && isSelfUpdate) {
            canEditDirectly = false;
        }
        
        // Determine if user can update photo directly
        // ANY role can update their own profile photo directly (no approval needed for photo)
        // Photo can be updated directly if: it's a self-update (regardless of role)
        boolean canUpdatePhotoDirectly = isSelfUpdate;

        // Note: MANAGER cannot edit any user directly - all updates must go through HQ_ADMIN approval
        // This is now handled by canEditDirectly = false for MANAGER role above

        // ========================================
        // STEP 4: UNIQUENESS VALIDATIONS
        // ========================================
        
        // Check if this is a manager updating an approved request
        boolean isApprovedRequestUpdate = false;
        if (user.getProfileUpdateRequestStatus() == RequestStatus.APPROVED && 
            user.getProfileUpdateRequestData() != null) {
            isApprovedRequestUpdate = true;
        }

        // Validate email uniqueness (skip for approved request updates)
        if (!isApprovedRequestUpdate) {
            if (!Objects.equals(user.getEmail(), request.getEmail()) && 
                request.getEmail() != null && 
                userRepository.existsByEmailIgnoreCase(request.getEmail())) {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        messageUtil.getMessage("user.update.error.email.exists", userLocale));
            }

            // Validate user code uniqueness (case-insensitive)
            if (request.getUserCode() != null) {
                String normalizedRequestUserCode = request.getUserCode().trim().toLowerCase();
                String normalizedCurrentUserCode = user.getUserCode() != null ? user.getUserCode().toLowerCase() : null;
                if (!normalizedRequestUserCode.equals(normalizedCurrentUserCode)) {
                    if (userRepository.existsByUserCodeIgnoreCase(normalizedRequestUserCode)) {
                        throw new ResponseStatusException(HttpStatus.CONFLICT,
                                messageUtil.getMessage("user.update.error.usercode.exists", userLocale));
                    }
                }
            }
        }

        // ========================================
        // STEP 5: HANDLE PHOTO UPDATE IF USER CAN UPDATE DIRECTLY
        // ========================================
        
        // Track if photo was updated directly
        boolean photoUpdatedDirectly = false;
        
        // If user can update photo directly, apply it immediately and exclude from request
        if (canUpdatePhotoDirectly && request.getPhotoUrl() != null) {
            String currentPhotoUrl = (user.getPhotoUrl() != null && !user.getPhotoUrl().isEmpty()) ? awsService.stripToKey(user.getPhotoUrl()) : null;
            String requestedPhotoUrl = (request.getPhotoUrl() != null && !request.getPhotoUrl().isEmpty()) ? awsService.stripToKey(request.getPhotoUrl()) : null;
            
            // Apply photo update immediately if it's different
            if (!Objects.equals(currentPhotoUrl, requestedPhotoUrl)) {
                user.setPhotoUrl(requestedPhotoUrl);
                user.setUpdatedAt(OffsetDateTime.now(ZoneOffset.UTC));
                user.setUpdatedBy(updater);
                userRepository.save(user);
                photoUpdatedDirectly = true;
                log.info("Photo updated directly for user {} as they can update it themselves", userId);
            }
        }
        
        // ========================================
        // STEP 6: APPROVAL WORKFLOW HANDLING
        // ========================================
        
        // Check if only employment details or shift are being changed (these can be updated directly by MANAGER)
        // Get current shift ID from user shift mapping
        UUID currentShiftId = null;
        var currentUserShiftMapping = userShiftMappingRepository.findFirstByUser_Id(userId).orElse(null);
        if (currentUserShiftMapping != null && currentUserShiftMapping.getShift() != null) {
            currentShiftId = currentUserShiftMapping.getShift().getId();
        }
        boolean employmentTypeChanged = !Objects.equals(user.getEmploymentType(), request.getEmploymentType());
        boolean shiftIdChanged = !Objects.equals(currentShiftId, request.getShiftId());
        
        // Check if only employment details or shift are changing (no profile fields)
        String normalizedRequestUserCodeForCheck3 = request.getUserCode() != null ? request.getUserCode().trim().toLowerCase() : null;
        String normalizedCurrentUserCodeForCheck3 = user.getUserCode() != null ? user.getUserCode().toLowerCase() : null;
        String normalizedCurrentPhotoForCheck3 = (user.getPhotoUrl() != null && !user.getPhotoUrl().isEmpty()) ? awsService.stripToKey(user.getPhotoUrl()) : null;
        String normalizedRequestPhotoForCheck3 = (request.getPhotoUrl() != null && !request.getPhotoUrl().isEmpty()) ? awsService.stripToKey(request.getPhotoUrl()) : null;
        boolean onlyEmploymentOrShiftChanged = (employmentTypeChanged || shiftIdChanged) &&
                Objects.equals(user.getFirstName(), request.getFirstName()) &&
                Objects.equals(user.getLastName(), request.getLastName()) &&
                Objects.equals(user.getEmail(), request.getEmail()) &&
                Objects.equals(user.getContactNumber(), request.getContactNumber()) &&
                Objects.equals(user.getLanguageCode(), request.getLanguageCode()) &&
                Objects.equals(normalizedCurrentUserCodeForCheck3, normalizedRequestUserCodeForCheck3) &&
                Objects.equals(normalizedCurrentPhotoForCheck3, normalizedRequestPhotoForCheck3) &&
                Objects.equals(user.getRoleId(), request.getRoleId()) &&
                Objects.equals(user.getRestaurantId(), request.getRestaurantId()) &&
                Objects.equals(user.getStatus(), request.getStatus());
        
        // If only employment details or shift are changing, allow MANAGER to update directly
        if (onlyEmploymentOrShiftChanged && "MANAGER".equals(updaterRole)) {
            // Update employment type and shift directly
            if (employmentTypeChanged) {
                user.setEmploymentType(request.getEmploymentType());
            }
            
            // Handle shift updates
            if (shiftIdChanged && request.getShiftId() != null) {
                Shift shift = shiftRepository.findById(request.getShiftId())
                        .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST,
                                messageUtil.getMessage("user.update.error.invalid.shiftid", userLocale)));
                
                // Remove existing shift mappings and create new one
                userShiftMappingRepository.deleteByUserId(user.getId());
                
                UserShiftMapping userShiftMapping = new UserShiftMapping();
                UserShiftId userShiftId = new UserShiftId(user.getId(), shift.getId());
                userShiftMapping.setId(userShiftId);
                userShiftMapping.setUser(user);
                userShiftMapping.setShift(shift);
                userShiftMappingRepository.save(userShiftMapping);
            } else if (shiftIdChanged && request.getShiftId() == null) {
                // Remove shift if shiftId is null
                userShiftMappingRepository.deleteByUserId(user.getId());
            }
            
            // Update audit fields
            user.setUpdatedAt(OffsetDateTime.now(ZoneOffset.UTC));
            user.setUpdatedBy(updater);
            userRepository.save(user);
            
            // Log manager action for updating employee
            try {
                auditLoggingService.logManagerAction(
                        updater,
                        user,
                        AuditLogging.AuditAction.UPDATE_EMPLOYEE,
                        user.getRestaurantId()
                );
            } catch (Exception e) {
                log.error("Failed to log manager action for user update: {}", e.getMessage());
            }
            
            // Build full response (same as get-by-id)
            return ResponseDto.<UserAccountDataResponse>builder()
                    .message(messageUtil.getMessage("user.update.success", userLocale))
                    .data(buildUserAccountDataResponse(user))
                    .build();
        }
        
        // If user role requires approval and this is not an approved request update, create approval request
        // Create request if:
        // 1. User requires approval (CASHIER/WAITER/KDS) AND updater cannot edit directly AND NOT (MANAGER updating unlocked user), OR
        // 2. Updater is MANAGER AND profile fields are changing AND user is locked (isStatusLocked = true)
        //    If user is NOT locked, MANAGER can update profile fields directly (no request needed)
        // Special case: MANAGER can update unlocked users directly (even if they require approval)
        boolean managerUpdatingUnlockedUser = "MANAGER".equals(updaterRole) && !Boolean.TRUE.equals(user.getIsStatusLocked());
        boolean shouldCreateRequest = (requiresApproval && !canEditDirectly && !managerUpdatingUnlockedUser) || 
                ("MANAGER".equals(updaterRole) && !onlyEmploymentOrShiftChanged && Boolean.TRUE.equals(user.getIsStatusLocked()));
        if (shouldCreateRequest && !canEditDirectly) {
            // Check if only photo was changed (and it was updated directly)
            // Normalize userCode for comparison
            String normalizedRequestUserCodeForCheck2 = request.getUserCode() != null ? request.getUserCode().trim().toLowerCase() : null;
            String normalizedCurrentUserCodeForCheck2 = user.getUserCode() != null ? user.getUserCode().toLowerCase() : null;
            
            boolean onlyPhotoChanged = photoUpdatedDirectly &&
                    Objects.equals(user.getFirstName(), request.getFirstName()) &&
                    Objects.equals(user.getLastName(), request.getLastName()) &&
                    Objects.equals(user.getEmail(), request.getEmail()) &&
                    Objects.equals(user.getContactNumber(), request.getContactNumber()) &&
                    Objects.equals(user.getLanguageCode(), request.getLanguageCode()) &&
                    Objects.equals(normalizedCurrentUserCodeForCheck2, normalizedRequestUserCodeForCheck2);
            
            // If only photo was changed and it was updated directly, return success immediately
            // Allow photo updates even when there's a pending request (photo can be updated directly)
            if (onlyPhotoChanged) {
                return ResponseDto.<UserAccountDataResponse>builder()
                        .message(messageUtil.getMessage("user.update.success", userLocale))
                        .data(buildUserAccountDataResponse(user))
                        .build();
            }
            
            // Check if user already has a pending request
            // Allow creating new requests when status is NONE, APPROVED, or DECLINED
            // Only prevent when status is OPEN (already has a pending request)
            // Photo-only updates are allowed even with pending requests (handled above)
            if (user.getProfileUpdateRequestStatus() == RequestStatus.OPEN) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        messageUtil.getMessage("user.profile.update.request.already.pending", userLocale));
            }

            // Validate that there are actual changes before creating a request
            // Include photoUpdatedDirectly flag in hasChanges to treat photo change as valid modification
            String currentPhotoUrl = (user.getPhotoUrl() != null && !user.getPhotoUrl().isEmpty()) ? awsService.stripToKey(user.getPhotoUrl()) : null;
            String requestedPhotoUrl = null;
            if (!canUpdatePhotoDirectly) {
                // Only check photo changes if user cannot update photo directly
                requestedPhotoUrl = (request.getPhotoUrl() != null && !request.getPhotoUrl().isEmpty()) ? awsService.stripToKey(request.getPhotoUrl()) : null;
            }
            
            // Check userCode changes (case-insensitive comparison)
            // Store original userCode to preserve case, use normalized version only for comparison
            String originalRequestUserCode = request.getUserCode() != null ? request.getUserCode().trim() : null;
            String normalizedRequestUserCode = originalRequestUserCode != null ? originalRequestUserCode.toLowerCase() : null;
            String normalizedCurrentUserCode = user.getUserCode() != null ? user.getUserCode().toLowerCase() : null;
            
            // Normalize strings for comparison (handle null and empty strings consistently)
            String currentFirstName = user.getFirstName() != null ? user.getFirstName().trim() : null;
            String requestFirstName = request.getFirstName() != null ? request.getFirstName().trim() : null;
            String currentLastName = user.getLastName() != null ? user.getLastName().trim() : null;
            String requestLastName = request.getLastName() != null ? request.getLastName().trim() : null;
            String currentEmail = user.getEmail() != null ? user.getEmail().trim() : null;
            String requestEmail = request.getEmail() != null ? request.getEmail().trim() : null;
            String currentContactNumber = user.getContactNumber() != null ? user.getContactNumber().trim() : null;
            String requestContactNumber = request.getContactNumber() != null ? request.getContactNumber().trim() : null;
            String currentLanguageCode = user.getLanguageCode() != null ? user.getLanguageCode().trim() : null;
            String requestLanguageCode = request.getLanguageCode() != null ? request.getLanguageCode().trim() : null;
            
            // Check for actual changes in profile fields (password is not part of profile update request)
            boolean firstNameChanged = !Objects.equals(currentFirstName, requestFirstName);
            boolean lastNameChanged = !Objects.equals(currentLastName, requestLastName);
            boolean emailChanged = !Objects.equals(currentEmail, requestEmail);
            boolean contactNumberChanged = !Objects.equals(currentContactNumber, requestContactNumber);
            boolean photoUrlChanged = !canUpdatePhotoDirectly && !Objects.equals(currentPhotoUrl, requestedPhotoUrl);
            boolean languageCodeChanged = !Objects.equals(currentLanguageCode, requestLanguageCode);
            boolean userCodeChanged = !Objects.equals(normalizedCurrentUserCode, normalizedRequestUserCode);
            
            // Reuse employment and shift change variables from outer scope (already calculated above)
            // employmentTypeChanged and shiftIdChanged are already available from the check above
            
            boolean hasChanges = firstNameChanged || lastNameChanged || emailChanged || 
                    contactNumberChanged || photoUrlChanged || languageCodeChanged || 
                    userCodeChanged || photoUpdatedDirectly || employmentTypeChanged || shiftIdChanged; // Include employment and shift changes
            
            // Log change detection for debugging
            if (log.isDebugEnabled()) {
                log.debug("Profile update change detection for user {} (unlocked): firstName={}, lastName={}, email={}, contactNumber={}, photoUrl={}, languageCode={}, userCode={}, employmentType={}, shiftId={}, photoUpdatedDirectly={}, hasChanges={}",
                        userId, firstNameChanged, lastNameChanged, emailChanged, contactNumberChanged, 
                        photoUrlChanged, languageCodeChanged, userCodeChanged, employmentTypeChanged, shiftIdChanged, photoUpdatedDirectly, hasChanges);
            }
            
            if (!hasChanges) {
                log.warn("Profile update request attempted for user {} (unlocked) with no actual changes. This may indicate a password-only update or duplicate request.", userId);
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        messageUtil.getMessage("user.profile.update.no.changes", userLocale));
            }

            try {
                // Convert UpdateUserRequest to UserProfileUpdateRequest for storage
                // Exclude photoUrl if user can update it directly
                UserProfileUpdateRequest profileRequest = new UserProfileUpdateRequest();
                profileRequest.setFirstName(request.getFirstName());
                profileRequest.setLastName(request.getLastName());
                profileRequest.setEmail(request.getEmail());
                profileRequest.setContactNumber(request.getContactNumber());
                profileRequest.setUserCode(originalRequestUserCode); // Store original case
                // Only include photoUrl in request if user cannot update it directly
                if (!canUpdatePhotoDirectly) {
                    profileRequest.setPhotoUrl(requestedPhotoUrl);
                } else {
                    // Keep current photoUrl (already updated directly above)
                    profileRequest.setPhotoUrl(null);
                }
                profileRequest.setLanguageCode(request.getLanguageCode());

                // Double-check: Verify that the request data actually differs from current user data
                // This is a safety check to prevent creating requests with no changes
                boolean requestDataHasChanges = 
                        !Objects.equals(currentFirstName, profileRequest.getFirstName()) ||
                        !Objects.equals(currentLastName, profileRequest.getLastName()) ||
                        !Objects.equals(currentEmail, profileRequest.getEmail()) ||
                        !Objects.equals(currentContactNumber, profileRequest.getContactNumber()) ||
                        (!canUpdatePhotoDirectly && !Objects.equals(currentPhotoUrl, profileRequest.getPhotoUrl())) ||
                        !Objects.equals(currentLanguageCode, profileRequest.getLanguageCode()) ||
                        !Objects.equals(normalizedCurrentUserCode, 
                                profileRequest.getUserCode() != null ? profileRequest.getUserCode().toLowerCase() : null) ||
                        photoUpdatedDirectly;
                
                if (!requestDataHasChanges) {
                    log.error("Profile update request data validation failed for user {} (unlocked): Request data matches current user data exactly. This should not happen after change detection.", userId);
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                            messageUtil.getMessage("user.profile.update.no.changes", userLocale));
                }

                // Convert request to JSON and store in profileUpdateRequestData
                ObjectMapper objectMapper = new ObjectMapper();
                String requestDataJson = objectMapper.writeValueAsString(profileRequest);
                
                // Create new request - clear review fields from previous request
                user.setProfileUpdateRequestStatus(RequestStatus.OPEN);
                user.setProfileUpdateRequestData(requestDataJson);
                user.setProfileUpdateRequestedAt(OffsetDateTime.now(ZoneOffset.UTC));
                // Store the locked status at the time of request creation to ensure consistent authorization
                user.setProfileUpdateRequestLockedStatus(user.getIsStatusLocked());
                // Clear review fields from previous APPROVED/DECLINED request to ensure this is treated as a new request
                user.setProfileUpdateReviewedAt(null);
                user.setUpdatedAt(OffsetDateTime.now(ZoneOffset.UTC));
                user.setUpdatedBy(updater);
                
                userRepository.save(user);
                
                log.info("Profile update request created for user {} (unlocked) by updater {} with changes: firstName={}, lastName={}, email={}, contactNumber={}, photoUrl={}, languageCode={}, userCode={}",
                        userId, updaterId, firstNameChanged, lastNameChanged, emailChanged, 
                        contactNumberChanged, photoUrlChanged, languageCodeChanged, userCodeChanged);
                
                // Notify approvers (Manager or HQ Admin) about newly opened profile update request
                // Determine who should approve:
                // 1. If requester is MANAGER -> always goes to HQ_ADMIN
                // 2. If user is locked (isStatusLocked = true) -> goes to HQ_ADMIN
                // 3. Otherwise -> goes to MANAGER
                // Since user-management is a separate microservice, notifications should be sent via:
                // 1. REST call to restaurant-management notification service, OR
                // 2. Shared notification service, OR
                // 3. Message queue/event bus
                try {
                    boolean isLocked = Boolean.TRUE.equals(user.getIsStatusLocked());
                    List<User> approvers = new ArrayList<>();
                    
                    // Check if requester (updater) is a MANAGER
                    boolean requesterIsManager = false;
                    if (updater != null && updater.getRoleId() != null) {
                        Role requesterRole = roleRepository.findById(updater.getRoleId()).orElse(null);
                        if (requesterRole != null && "MANAGER".equals(requesterRole.getName())) {
                            requesterIsManager = true;
                        }
                    }
                    
                    // If requester is MANAGER or user is locked, send to HQ_ADMIN
                    if (requesterIsManager || isLocked) {
                        // HQ Admin should approve
                        Optional<Role> hqAdminRoleOpt = roleRepository.findByName("HQ_ADMIN");
                        if (hqAdminRoleOpt.isPresent()) {
                            UUID hqAdminRoleId = hqAdminRoleOpt.get().getId();
                            Pageable pageable = PageRequest.of(0, 1000);
                            Page<User> hqAdminsPage = userRepository.findAllByRoleIdAndIsDeletedFalse(hqAdminRoleId, pageable);
                            approvers = hqAdminsPage.getContent();
                        }
                    } else {
                        // Manager should approve - find managers for the user's restaurant
                        if (user.getRestaurantId() != null) {
                            Optional<Role> managerRoleOpt = roleRepository.findByName("MANAGER");
                            if (managerRoleOpt.isPresent()) {
                                UUID managerRoleId = managerRoleOpt.get().getId();
                                approvers = userRepository.findAllByRestaurantIdAndRoleIdAndIsDeletedFalse(user.getRestaurantId(), managerRoleId);
                            }
                        }
                    }
                    
                    // Save notification to database for each approver
                    if (!approvers.isEmpty()) {
                        log.info("Found {} approvers for profile update request for user {}", approvers.size(), user.getId());
                        try {
                            String requesterName = user.getUpdatedBy() != null 
                                ? (user.getUpdatedBy().getFirstName() + " " + user.getUpdatedBy().getLastName()).trim()
                                : (user.getFirstName() + " " + user.getLastName()).trim();
                            String userName = (user.getFirstName() + " " + user.getLastName()).trim();

                            for (User approver : approvers) {
                                try {
                                    // Ensure approver is a managed entity - reload to ensure device_token is loaded
                                    User managedApprover = userRepository.findById(approver.getId())
                                            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, 
                                                    "Approver not found: " + approver.getId()));
                                    
                                    // Verify device token is loaded - log for debugging
                                    String approverDeviceToken = managedApprover.getDeviceToken();
                                    log.debug("Approver {} device token loaded: {}", 
                                            managedApprover.getId(), 
                                            approverDeviceToken != null && !approverDeviceToken.trim().isEmpty() ? "YES" : "NO");

                                    Locale approverLoc = localeForRecipient(managedApprover, userLocale);
                                    String title = messageUtil.getMessage("notification.profile.update.request.opened.title", approverLoc);
                                    String message = messageUtil.getMessage("notification.profile.update.request.opened.body", approverLoc, userName, requesterName);
                                    
                                    // Get createdBy user - ensure it's a managed entity
                                    // Use updater directly to avoid LazyInitializationException
                                    User createdByUser = updater != null ? updater : user;
                                    // Ensure it's a managed entity by reloading if needed
                                    if (createdByUser != null) {
                                        try {
                                            createdByUser = userRepository.findById(createdByUser.getId())
                                                    .orElse(createdByUser);
                                        } catch (Exception e) {
                                            log.warn("Could not reload createdBy user, using existing entity: {}", e.getMessage());
                                        }
                                    }
                                    
                                    java.util.Map<String, String> requestData = new java.util.HashMap<>();
                                    requestData.put("requestId", user.getId().toString());
                                    requestData.put("userId", user.getId().toString());
                                    
                                    Notification notification = Notification.builder()
                                            .user(managedApprover)
                                            .title(title)
                                            .type("PROFILE_UPDATE_REQUEST_OPENED")
                                            .message(message)
                                            .bodyKey("notification.profile.update.request.opened.body")
                                            .bodyArgs(serializeBodyArgs(userName, requesterName))
                                            .additionalData(serializeRequestData(requestData))
                                            .createdBy(createdByUser)
                                            .read(false)
                                            .createdAt(OffsetDateTime.now(ZoneOffset.UTC))
                                            .build();
                                    
                                    Notification savedNotification = notificationRepository.saveAndFlush(notification);
                                    log.info("Successfully saved profile update request notification - ID: {}, approver: {}, user: {}", 
                                            savedNotification.getId(), approver.getId(), user.getId());
                                    
                                    // Publish notification to RabbitMQ for FCM processing
                                    try {
                                        notificationPublisherService.publishNotification(savedNotification, managedApprover);
                                    } catch (Exception e) {
                                        log.error("Failed to publish notification to RabbitMQ for approver {}: {}", 
                                                approver.getId(), e.getMessage(), e);
                                    }
                                } catch (Exception e) {
                                    log.error("Failed to save notification for approver {}: {}", approver.getId(), e.getMessage(), e);
                                }
                            }
                        } catch (Exception e) {
                            log.error("Failed to save approver notifications for profile update request: {}", e.getMessage(), e);
                        }
                    } else {
                        log.warn("No approvers found for profile update request - user: {}, isLocked: {}", 
                                user.getId(), Boolean.TRUE.equals(user.getIsStatusLocked()));
                    }
                } catch (Exception e) {
                    log.error("Failed to send approver notification for profile update request: {}", e.getMessage(), e);
                }
                
                // Note: Requester is NOT notified when request is created
                // Requester will be notified when request is approved/declined
                
                return ResponseDto.<UserAccountDataResponse>builder()
                        .message(messageUtil.getMessage("user.profile.update.request.submitted", userLocale))
                        .data(buildUserAccountDataResponse(user))
                        .build();
                        
            } catch (JsonProcessingException e) {
                log.error("Error converting request to JSON: {}", e.getMessage());
                throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                        messageUtil.getMessage("user.profile.update.request.error", userLocale));
            }
        }

        // ========================================
        // STEP 7: DATA UPDATES AND BUSINESS LOGIC
        // ========================================
        
        // Check if restaurant is being changed and handle table assignments
        UUID oldRestaurantId = user.getRestaurantId();
        UUID newRestaurantId = request.getRestaurantId();
        boolean restaurantChanged = !Objects.equals(oldRestaurantId, newRestaurantId);
        
        // Check if role is being changed to KDS
        UUID oldRoleId = user.getRoleId();
        UUID newRoleId = request.getRoleId();
        boolean roleChangedToKds = false;
        if (newRoleId != null && !Objects.equals(oldRoleId, newRoleId)) {
            Role newRole = roleRepository.findById(newRoleId).orElse(null);
            roleChangedToKds = (newRole != null && "KDS".equals(newRole.getName()));
        }

        // Get old role before updating user fields (needed for password reset logic)
        Role oldRole = null;
        if (oldRoleId != null) {
            oldRole = roleRepository.findById(oldRoleId).orElse(null);
        }

        if (restaurantChanged && oldRestaurantId != null) {
            List<TableAssignment> activeAssignments = tableAssignmentRepository.findByWaiterIdAndUnassignedAtIsNull(userId);
            
            if (!activeAssignments.isEmpty()) {
                OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
                
                for (TableAssignment assignment : activeAssignments) {
                    assignment.setUnassignedAt(now);
                    assignment.setUpdatedAt(now);
                    assignment.setUpdatedBy(updater);
                }
                tableAssignmentRepository.saveAll(activeAssignments);
                
                log.info("Unassigned user {} from {} table assignments due to restaurant change from {} to {}", 
                    userId, activeAssignments.size(), oldRestaurantId, newRestaurantId);
            }
        }

        // Update user fields
        // Note: PhotoUrl may have already been updated in STEP 5 if user can update it directly
        String oldEmail = null;
        String newEmail = null;
        boolean emailChanged = false;

        if (!isApprovedRequestUpdate) {
            user.setFirstName(request.getFirstName());
            user.setLastName(request.getLastName());

            oldEmail = user.getEmail() != null ? user.getEmail().trim() : null;
            newEmail = request.getEmail() != null ? request.getEmail().trim() : null;
            emailChanged = !Objects.equals(oldEmail, newEmail);

            user.setEmail(request.getEmail());
            user.setContactNumber(request.getContactNumber());
            // Only update photoUrl here if it wasn't already updated directly
            if (!canUpdatePhotoDirectly) {
                user.setPhotoUrl(awsService.stripToKey(request.getPhotoUrl()));
            }
            user.setLanguageCode(request.getLanguageCode());
        }
        // When isApprovedRequestUpdate, profile fields already match approveOrDeclineProfileUpdateRequest
        // (JSON kept for HQ history). Do not copy firstName/lastName/email/etc. from the body — HQ often
        // sends a full UpdateUserRequest with stale profile data on the next save (e.g. unlock only).

        user.setEmploymentType(request.getEmploymentType());
        // Only update roleId if provided in request (preserve existing role if not specified)
        if (request.getRoleId() != null) {
            user.setRoleId(request.getRoleId());
        }
        user.setRestaurantId(request.getRestaurantId());

        // Reset password for waiter ONLY when being assigned to a restaurant for the first time
        // Do NOT reset password when restaurant is being changed for existing users
        // Only reset when waiter is being assigned for the first time (oldRestaurantId == null)
        if (restaurantChanged && newRestaurantId != null && oldRestaurantId == null) {
            // Check if user IS a WAITER (check current role after update, or old role if role not being changed)
            Role roleToCheck = oldRole; // Default to old role
            if (request.getRoleId() != null && !Objects.equals(oldRoleId, request.getRoleId())) {
                // Role is being changed, check new role
                Role newRole = roleRepository.findById(request.getRoleId()).orElse(null);
                roleToCheck = newRole;
            }
            // Only reset password if user IS a waiter (either old or new role is WAITER)
            if (roleToCheck != null && "WAITER".equals(roleToCheck.getName())) {
                resetWaiterPasswordOnRestaurantAssignment(user, newRestaurantId, userLocale, roleToCheck);
            }
        }
        // Note: Password is NOT reset when changing an existing user's restaurant (oldRestaurantId != null)
        // This prevents password changes when HQ admin changes a user's restaurant assignment

        // Update user code if provided and different (preserve original case)
        if (!isApprovedRequestUpdate && request.getUserCode() != null) {
            String originalRequestUserCode = request.getUserCode().trim();
            String normalizedRequestUserCode = originalRequestUserCode.toLowerCase();
            String normalizedCurrentUserCode = user.getUserCode() != null ? user.getUserCode().toLowerCase() : null;
            if (!normalizedRequestUserCode.equals(normalizedCurrentUserCode)) {
                user.setUserCode(originalRequestUserCode); // Store original case
            }
        }

        // Track status change for audit trail
        EntityStatus oldStatus = user.getStatus();
        EntityStatus newStatus = request.getStatus();
        boolean statusChanged = (oldStatus != null && newStatus != null && !oldStatus.equals(newStatus)) ||
                                (oldStatus == null && newStatus != null) ||
                                (oldStatus != null && newStatus == null);

        // Set status (allowed for MANAGER when not locked, HQ_ADMIN always allowed due to early guard)
        user.setStatus(request.getStatus());

        // Update lock flag if provided (when currently locked, only HQ_ADMIN reaches here due to early guard)
        if (request.getIsStatusLocked() != null) {
            user.setIsStatusLocked(request.getIsStatusLocked());
        }

        // Clear request data if this was an approved request update
        if (isApprovedRequestUpdate) {
            user.setProfileUpdateRequestData(null);
            user.setProfileUpdateRequestStatus(RequestStatus.NONE);
            user.setProfileUpdateRequestLockedStatus(null);
        }

        // IMPORTANT: Preserve the original requester (stored in updatedBy) if there's an OPEN profile update request
        // This ensures authorization checks can still identify the requester correctly even after direct profile updates
        // When HQ/Manager updates the profile directly while a request is OPEN, we must preserve updatedBy
        // so that the authorization check in approveOrDeclineProfileUpdateRequest can correctly determine
        // who the original requester was and apply the appropriate approval authority rules
        // 
        // This fixes the bug where:
        // 1. Profile locked → request created → profile unlocked → HQ tries to approve (gets unauthorized)
        // 2. Profile unlocked → request created → profile locked → Manager tries to approve (gets unauthorized)
        // In both cases, updatedBy was being overwritten, breaking the authorization check
        boolean hasOpenRequest = user.getProfileUpdateRequestStatus() == RequestStatus.OPEN;

        // Update audit fields
        user.setUpdatedAt(OffsetDateTime.now(ZoneOffset.UTC));
        // Only update updatedBy if there's no OPEN request, otherwise preserve the original requester
        // This prevents the authorization bug where profile status changes after request creation
        // cause incorrect authorization checks due to updatedBy being overwritten
        if (!hasOpenRequest) {
            user.setUpdatedBy(updater);
        }
        // If there's an OPEN request, keep updatedBy pointing to the original requester for authorization checks

        // Save user
        userRepository.save(user);

        if (restaurantChanged && oldRestaurantId != null) {
            publishUserLeftRestaurantEmailSchedulesAfterCommit(user.getId(), oldRestaurantId);
        }

        // Send email to new address on direct profile update (HQ Admin, or Manager updating unlocked user)
        if (emailChanged && newEmail != null && !newEmail.isEmpty()
                && ("HQ_ADMIN".equalsIgnoreCase(updaterRole) || managerUpdatingUnlockedUser)) {
            sendEmailChangeNotification(user, newEmail, userLocale, updaterRole);
        }

        // Auto-assign user to default KDS if:
        // 1. User has KDS role (after update) and restaurant is assigned/changed, OR
        // 2. Role was changed to KDS and user has restaurant assigned
        if (user.getRestaurantId() != null && user.getRoleId() != null) {
            try {
                Role currentUserRole = roleRepository.findById(user.getRoleId()).orElse(null);
                if (currentUserRole != null && "KDS".equals(currentUserRole.getName())) {
                    // Assign if:
                    // 1. Restaurant was changed or newly assigned, OR
                    // 2. Role was changed to KDS
                    if (restaurantChanged || (oldRestaurantId == null && newRestaurantId != null) || roleChangedToKds) {
                        assignUserToDefaultKds(user, updater, userLocale);
                    }
                }
            } catch (Exception e) {
                log.error("Failed to assign user {} to default KDS: {}", user.getId(), e.getMessage());
                // Don't fail user update if KDS assignment fails
            }
        }

        // Delete login audit records only if someone else is updating the user (not self-update)
        // For self-updates (like manager updating their own profile), preserve the session
        if (!isSelfUpdate) {
            loginAuditRepository.deleteByUser_Id(userId);
            log.info("Invalidated session for user {} as they were updated by another user {}", userId, updaterId);
        } else {
            log.info("Preserved session for user {} during self-update", userId);
        }

        // ========================================
        // STEP 7: SHIFT MANAGEMENT AND AUDIT LOGGING
        // ========================================
        
        // Log manager action for updating employee
        try {
            auditLoggingService.logManagerAction(
                    updater,
                    user,
                    AuditLogging.AuditAction.UPDATE_EMPLOYEE,
                    user.getRestaurantId()
            );
        } catch (Exception e) {
            log.error("Failed to log manager action for user update: {}", e.getMessage());
        }

        // Notify employee if profile was updated directly by manager or HQ (without request)
        // Only notify if:
        // 1. It's not a self-update (someone else updated the profile)
        // 2. It's a direct update (not through a request workflow)
        // 3. The updater is MANAGER or HQ_ADMIN (canEditDirectly is true for HQ_ADMIN, managerUpdatingUnlockedUser is true for MANAGER updating unlocked users)
        if (!isSelfUpdate && !isApprovedRequestUpdate && 
            (canEditDirectly || managerUpdatingUnlockedUser)) {
            boolean notificationSent = false;
            try {
                Object wsNotifier = resolveNotificationService();
                if (wsNotifier != null) {
                    try {
                        log.info("Sending profile updated directly notification to employee {} updated by {}", 
                                user.getId(), updaterId);
                        
                        java.lang.reflect.Method method = wsNotifier.getClass().getMethod(
                                "notifyProfileUpdatedDirectly",
                                User.class, User.class, Locale.class);
                        method.invoke(wsNotifier, user, updater, userLocale);
                        log.info("Successfully sent profile updated directly notification to employee {} updated by {}", 
                                user.getId(), updaterId);
                        notificationSent = true;
                    } catch (NoSuchMethodException e) {
                        log.warn("Method notifyProfileUpdatedDirectly not found in NotificationService: {}", e.getMessage());
                    } catch (Exception e) {
                        log.warn("Failed to send profile updated directly notification via NotificationService: {}", e.getMessage());
                    }
                }
                
                // Fallback: Publish to RabbitMQ if notification service is not available or failed
                if (!notificationSent) {
                    try {
                        publishProfileUpdatedDirectlyNotificationToRabbitMQ(user, updater, userLocale);
                        log.info("Published profile updated directly notification to RabbitMQ for employee {} updated by {}", 
                                user.getId(), updaterId);
                    } catch (Exception e) {
                        log.error("Failed to publish profile updated directly notification to RabbitMQ: {}", e.getMessage(), e);
                    }
                }
            } catch (Exception e) {
                log.error("Failed to send profile updated directly notification: {}", e.getMessage(), e);
                // Last resort: try RabbitMQ fallback even if service resolution failed
                try {
                    publishProfileUpdatedDirectlyNotificationToRabbitMQ(user, updater, userLocale);
                    log.info("Published profile updated directly notification to RabbitMQ (fallback) for employee {} updated by {}", 
                            user.getId(), updaterId);
                } catch (Exception fallbackException) {
                    log.error("Failed to publish profile updated directly notification to RabbitMQ (fallback): {}", 
                            fallbackException.getMessage(), fallbackException);
                }
            }
        }

        // Create audit trail for status update if status was changed
        if (statusChanged && "MANAGER".equals(updaterRole)) {
            try {
                Restaurant restaurant = null;
                if (user.getRestaurantId() != null) {
                    restaurant = restaurantRepository.findById(user.getRestaurantId()).orElse(null);
                }
                
                String notes = String.format("Employee status changed from %s to %s", 
                        oldStatus != null ? oldStatus.name() : "NULL",
                        newStatus != null ? newStatus.name() : "NULL");
                
                createAuditTrail(
                        updater,
                        ActionType.EMPLOYEE_STATUS_UPDATE,
                        restaurant,
                        RequestStatus.NA,
                        null, // ipAddress
                        null, // userAgent
                        user.getId(),
                        "USER",
                        notes,
                        null, // requestedBy
                        null, // requestedAt
                        null, // reviewedBy
                        null  // reviewedAt
                );
            } catch (Exception e) {
                log.error("Failed to create audit trail for employee status update: {}", e.getMessage());
            }
        }

        // Handle shift updates
        Shift shift = null;
        if (request.getShiftId() != null) {
            shift = shiftRepository.findById(request.getShiftId())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST,
                            messageUtil.getMessage("user.update.error.invalid.shiftid", userLocale)));

            // Remove existing shift mappings and create new one
            userShiftMappingRepository.deleteByUserId(user.getId());

            UserShiftMapping userShiftMapping = new UserShiftMapping();
            UserShiftId userShiftId = new UserShiftId(user.getId(), shift.getId());
            userShiftMapping.setId(userShiftId);
            userShiftMapping.setUser(user);
            userShiftMapping.setShift(shift);
            userShiftMappingRepository.save(userShiftMapping);
        }

        // ========================================
        // STEP 8: BUILD RESPONSE
        // ========================================

        // Create audit trail for user update
        try {
            Restaurant restaurant = null;
            if (user.getRestaurantId() != null) {
                restaurant = restaurantRepository.findById(user.getRestaurantId()).orElse(null);
            }
            createAuditTrail(
                    updater,
                    ActionType.USER_UPDATE,
                    restaurant,
                    RequestStatus.NA, // Non-request action - always NA
                    null, // ipAddress - not available in this context
                    null, // userAgent - not available in this context
                    user.getId(),
                    "USER",
                    "User updated: " + user.getUserCode() + " (" + user.getFirstName() + " " + user.getLastName() + ")",
                    null, // requestedBy
                    null, // requestedAt
                    null, // reviewedBy
                    null  // reviewedAt
            );
        } catch (Exception e) {
            log.error("Failed to create audit trail for user update: {}", e.getMessage());
            // Don't break user update flow if audit trail fails
        }

        return ResponseDto.<UserAccountDataResponse>builder()
                .message(messageUtil.getMessage("user.update.success", userLocale))
                .data(buildUserAccountDataResponse(user))
                .build();
    }
    
    @Transactional
    public ResponseDto<Void> deleteUser(UUID userId, String deleterId, String deleterRole, String locale) {
    
        Locale userLocale = Locale.forLanguageTag(locale);
    
        if (!"HQ_ADMIN".equals(deleterRole)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    messageUtil.getMessage("user.delete.error.forbidden", userLocale));
        }
    
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        messageUtil.getMessage("user.not.found", userLocale)));
    
        if (Boolean.TRUE.equals(user.getIsDeleted())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    messageUtil.getMessage("user.delete.error.alreadydeleted", userLocale));
        }
    
        User deleter = userRepository.findById(UUID.fromString(deleterId))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        messageUtil.getMessage("user.delete.error.deleter.notfound", userLocale)));
        
        // Restrict deletion if user has active table assignments
        boolean hasActiveAssignments = !tableAssignmentRepository.findByWaiterIdAndUnassignedAtIsNull(userId).isEmpty();
        if (hasActiveAssignments) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    messageUtil.getMessage("user.delete.error.active.table.assignments", userLocale));
        }

        // Restrict deletion if user has pending orders assigned (PUSHED or IN_PROGRESS)
        Long pendingOrdersCount = entityManager.createQuery(
                "SELECT COUNT(o) FROM Order o WHERE o.waiter.id = :waiterId AND o.orderStatus IN (:statuses)", Long.class)
            .setParameter("waiterId", userId)
            .setParameter("statuses", java.util.List.of(OrderStatus.PUSHED, OrderStatus.IN_PROGRESS))
            .getSingleResult();
        boolean hasPendingOrders = pendingOrdersCount != null && pendingOrdersCount > 0;
        if (hasPendingOrders) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    messageUtil.getMessage("user.delete.error.pending.orders", userLocale));
        }

        user.setIsDeleted(true);
        user.setStatus(EntityStatus.INACTIVE);
        user.setUpdatedBy(deleter);
        user.setUpdatedAt(OffsetDateTime.now(ZoneOffset.UTC));
    
        userRepository.save(user);

        publishUserDeletedEmailSchedulesAfterCommit(user.getId());

        // Delete login audit records to invalidate user tokens
        try {
            loginAuditRepository.deleteByUser_Id(user.getId());
        } catch (Exception e) {
            log.error("Failed to delete login audit records for user {}: {}", user.getId(), e.getMessage());
        }

        // Log manager action for deleting employee
        try {
            auditLoggingService.logManagerAction(
                    deleter,
                    user,
                    AuditLogging.AuditAction.DELETE_EMPLOYEE,
                    user.getRestaurantId()
            );
        } catch (Exception e) {
            log.error("Failed to log manager action for user deletion: {}", e.getMessage());
        }

        // Create audit trail for user deletion
        try {
            Restaurant restaurant = null;
            if (user.getRestaurantId() != null) {
                restaurant = restaurantRepository.findById(user.getRestaurantId()).orElse(null);
            }
            createAuditTrail(
                    deleter,
                    ActionType.USER_DELETE,
                    restaurant,
                    RequestStatus.NA, // Non-request action - always NA
                    null, // ipAddress - not available in this context
                    null, // userAgent - not available in this context
                    user.getId(),
                    "USER",
                    "User deleted: " + user.getUserCode() + " (" + user.getFirstName() + " " + user.getLastName() + ")",
                    null, // requestedBy
                    null, // requestedAt
                    null, // reviewedBy
                    null  // reviewedAt
            );
        } catch (Exception e) {
            log.error("Failed to create audit trail for user deletion: {}", e.getMessage());
            // Don't break user deletion flow if audit trail fails
        }
    
        UserResponseDto userResponse = UserResponseDto.builder()
                .id(user.getId())
                .firstName(user.getFirstName())
                .lastName(user.getLastName())
                .email(user.getEmail())
                .contactNumber(user.getContactNumber())
                .photoUrl(awsService.getFullUrl(user.getPhotoUrl()))
                .employmentType(user.getEmploymentType() != null ? user.getEmploymentType().name() : null)
                .userCode(user.getUserCode())
                .roleId(user.getRoleId())
                .languageCode(user.getLanguageCode())
                .restaurantId(user.getRestaurantId())
                .status(user.getStatus() != null ? user.getStatus().name() : null)
                .isStatusLocked(user.getIsStatusLocked())
                .build();
    
        // Build but do not assign to a local variable to avoid unused warning
        DeletedUserResponse.builder()
                .user(userResponse)
                .count(1L)
                .total(1L)
                .metaData(null)
                .build();
    
        String message = messageUtil.getMessage("user.delete.success", userLocale);

        return ResponseDto.<Void>builder()
                .message(message)
                .build();
    }

    @Transactional
    @CacheEvict(value = "restaurants", allEntries = true)
    public ResponseDto<UserRestaurantUnassignResponse> unassignRestaurantFromUser(UUID userId, String updaterId, String updaterRole) {

        Locale userLocale = LocaleContextHolder.getLocale();

        if (!"HQ_ADMIN".equals(updaterRole)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    messageUtil.getMessage("user.unassign.error.forbidden", userLocale));
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> {
                    return new ResponseStatusException(HttpStatus.NOT_FOUND,
                            messageUtil.getMessage("user.not.found", userLocale));
                });

        if (user.getRestaurantId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    messageUtil.getMessage("user.unassign.error.already.unassigned", userLocale));
        }

        UUID previousRestaurantId = user.getRestaurantId();

        User updater = userRepository.findById(UUID.fromString(updaterId))
                .orElseThrow(() -> {
                    return new ResponseStatusException(HttpStatus.NOT_FOUND,
                            messageUtil.getMessage("user.not.found", userLocale));
                });

        // Find all active table assignments for this user
        List<TableAssignment> activeAssignments = tableAssignmentRepository.findByWaiterIdAndUnassignedAtIsNull(userId);
        
        // Unassign user from all active table assignments
        if (!activeAssignments.isEmpty()) {
            OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
            for (TableAssignment assignment : activeAssignments) {
                assignment.setUnassignedAt(now);
                assignment.setUpdatedAt(now);
                assignment.setUpdatedBy(updater);
            }
            tableAssignmentRepository.saveAll(activeAssignments);
            
            log.info("Unassigned user {} from {} table assignments", userId, activeAssignments.size());
        }

        // Delete login audit records for the user if any present
        loginAuditRepository.deleteByUser_Id(userId);

        user.setRestaurantId(null);
        user.setUpdatedBy(updater);
        user.setUpdatedAt(OffsetDateTime.now(ZoneOffset.UTC));

        userRepository.save(user);

        publishUserLeftRestaurantEmailSchedulesAfterCommit(userId, previousRestaurantId);

        // Log manager action for unassigning restaurant
        try {
            auditLoggingService.logManagerAction(
                    updater,
                    user,
                    AuditLogging.AuditAction.UNASSIGN_RESTAURANT,
                    null // No restaurant after unassignment
            );
        } catch (Exception e) {
            log.error("Failed to log manager action for restaurant unassignment: {}", e.getMessage());
        }

        UserRestaurantUnassignResponse responseData = UserRestaurantUnassignResponse.builder()
            .userId(user.getId())
            .restaurantId(null)
            .build();

        return ResponseDto.<UserRestaurantUnassignResponse>builder()
            .message(messageUtil.getMessage("user.unassign.success", userLocale))
            .data(responseData)
            .build();
    }


    public ResponseDto<String> logout(String token) {

        Locale userLocale = LocaleContextHolder.getLocale();
    
        UUID userId;
        try {
            String userIdStr = jwtUtil.getUserIdFromToken(token);
            userId = UUID.fromString(userIdStr);
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,
                    messageUtil.getMessage("user.logout.error.invalid.token", userLocale));
        }
    
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        messageUtil.getMessage("user.not.found", userLocale)));
    
        // Clear device token on logout
        user.setDeviceToken(null);
        user.setUpdatedAt(OffsetDateTime.now(ZoneOffset.UTC));
        userRepository.save(user);
    
        loginAuditRepository.findByUser_Id(userId).ifPresent(loginAuditRepository::delete);
    
        // Create audit trail for LOGOUT action
        try {
            Restaurant restaurant = null;
            if (user.getRestaurantId() != null) {
                restaurant = restaurantRepository.findById(user.getRestaurantId()).orElse(null);
            }
            createAuditTrail(
                    user,
                    ActionType.LOGOUT,
                    restaurant,
                    RequestStatus.NA, // Non-request action - always NA
                    null, // IP address not available in logout
                    null, // User agent not available in logout
                    user.getId(),
                    "USER",
                    "User logged out",
                    null, // requestedBy
                    null, // requestedAt
                    null, // reviewedBy
                    null  // reviewedAt
            );
        } catch (Exception e) {
            log.error("Failed to create audit trail for logout: {}", e.getMessage());
            // Don't break logout flow if audit trail fails
        }
    
        return ResponseDto.<String>builder()
                .message(messageUtil.getMessage("user.logout.success", userLocale))
                .data(null)
                .build();
    }
    

    private LoginAudit saveOrUpdateLoginAudit(
            User user,
            LocalDateTime expiryTime,
            String ipAddress,
            String userAgent,
            AppType appType,
            String appVersion) {

        LoginAudit loginAudit = loginAuditRepository.findByUser_Id(user.getId()).orElseGet(LoginAudit::new);
        loginAudit.setUser(user);
        loginAudit.setLoginExpiryDate(expiryTime != null ? expiryTime.atOffset(ZoneOffset.UTC) : null);
        loginAudit.setIpAddress(ipAddress);
        loginAudit.setUserAgent(userAgent);
        loginAudit.setAppType(appType);
        loginAudit.setAppVersion(appVersion != null && !appVersion.isBlank() ? appVersion.trim() : null);
        loginAudit.setLastSeenAt(OffsetDateTime.now(ZoneOffset.UTC));
        loginAudit.setCreatedBy(user.getFirstName());
        loginAudit.setDateCreated(OffsetDateTime.now(ZoneOffset.UTC));
        return loginAuditRepository.save(loginAudit);
    }

    /**
     * Creates an audit trail record for login/logout actions
     * Uses AuditTrailRepository directly since user-management cannot access restaurant-management services
     */
    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.REQUIRES_NEW)
    private AuditTrail createAuditTrail(
            User user,
            ActionType actionType,
            Restaurant restaurant,
            RequestStatus status,
            String ipAddress,
            String userAgent,
            UUID entityId,
            String entityType,
            String notes,
            User requestedBy,
            OffsetDateTime requestedAt,
            User reviewedBy,
            OffsetDateTime reviewedAt) {
        try {
            // Generate log number using the same logic as AuditTrailServiceImpl
            // Note: This duplicates the log number generation logic, but user-management
            // cannot access restaurant-management services directly
            String logNumber = generateLogNumber();
            
            // Determine if this is a request-type action that needs approval workflow
            // Request-type actions: REFUND, CANCELLATION, DISCOUNT (and all REQUEST_*_APPROVE/DECLINE)
            // Non-request actions: LOGIN, LOGOUT, PAYMENT, ORDER_MODIFICATION, SYSTEM_ACTION
            boolean isRequestTypeAction = actionType == ActionType.REFUND ||
                                         actionType == ActionType.CANCELLATION ||
                                         actionType == ActionType.DISCOUNT ||
                                         actionType == ActionType.REQUEST_ADDITIONAL_DISCOUNT_APPROVE ||
                                         actionType == ActionType.REQUEST_ADDITIONAL_DISCOUNT_DECLINE;
            
            // Set status: When caller explicitly passes APPROVED or DECLINED (e.g. manager decision), use it so audit trail shows correct status.
            // Otherwise: request-type actions use provided status or OPEN; non-request actions use NA.
            RequestStatus finalStatus;
            if (status == RequestStatus.APPROVED || status == RequestStatus.DECLINED) {
                finalStatus = status;
            } else if (isRequestTypeAction) {
                // Request-type actions: use provided status or default to OPEN
                finalStatus = status != null ? status : RequestStatus.OPEN;
            } else {
                // Non-request actions: always use NA
                finalStatus = RequestStatus.NA;
            }
            
            AuditTrail auditTrail = AuditTrail.builder()
                    .logNumber(logNumber)
                    .user(user)
                    .actionType(actionType)
                    .restaurant(restaurant)
                    .status(finalStatus)
                    .ipAddress(ipAddress)
                    .userAgent(userAgent)
                    .entityId(entityId)
                    .entityType(entityType)
                    .notes(notes)
                    .requestedBy(requestedBy)
                    .requestedAt(requestedAt)
                    .reviewedBy(reviewedBy)
                    .reviewedAt(reviewedAt)
                    .createdBy(user)
                    .createdAt(OffsetDateTime.now(ZoneOffset.UTC))
                    .updatedAt(OffsetDateTime.now(ZoneOffset.UTC))
                    .build();

            AuditTrail saved = auditTrailRepository.save(auditTrail);
            log.info("Audit trail created: {} - {} by user {} (logNumber: {})", 
                    actionType, entityType != null ? entityType : "N/A", 
                    user.getUserCode(), logNumber);
            
            return saved;
        } catch (Exception e) {
            log.error("Failed to create audit trail for action {} by user {}: {}", 
                    actionType, user != null ? user.getUserCode() : "unknown", e.getMessage(), e);
            // Don't throw exception to avoid breaking the main operation
            return null;
        }
    }

    /**
     * Generates a unique log number in format: REQ + sequence number
     * Format: REQ + 5-digit sequence (e.g., REQ15097)
     * Uses database sequence audit_trail_seq for thread-safe unique number generation
     */
    private String generateLogNumber() {
        try {
            // Get next value from database sequence (thread-safe)
            Long sequenceNumber = auditTrailRepository.getNextSequenceValue();
            
            // Format as REQ + 5-digit number (e.g., REQ00001, REQ15097)
            return String.format("REQ%05d", sequenceNumber);
        } catch (Exception e) {
            log.error("Error generating log number from sequence, using timestamp-based fallback: {}", e.getMessage());
            // Fallback to timestamp-based number if sequence fails
            return "REQ" + (System.currentTimeMillis() % 100000);
        }
    }

    /**
     * Safe wrapper for createAuditTrail that prevents exceptions from affecting the main transaction.
     * This method catches ALL exceptions and logs them without rethrowing, ensuring that
     * audit trail failures don't cause the main transaction to be marked as rollback-only.
     * 
     * @param user The user performing the action
     * @param actionType The type of action
     * @param restaurant The restaurant (can be null)
     * @param status The request status
     * @param ipAddress IP address (can be null)
     * @param userAgent User agent (can be null)
     * @param entityId The entity ID
     * @param entityType The entity type
     * @param notes Notes for the audit trail
     * @param requestedBy User who requested (can be null)
     * @param requestedAt When it was requested (can be null)
     * @param reviewedBy User who reviewed (can be null)
     * @param reviewedAt When it was reviewed (can be null)
     */
    private void createAuditTrailSafely(
            User user,
            ActionType actionType,
            Restaurant restaurant,
            RequestStatus status,
            String ipAddress,
            String userAgent,
            UUID entityId,
            String entityType,
            String notes,
            User requestedBy,
            OffsetDateTime requestedAt,
            User reviewedBy,
            OffsetDateTime reviewedAt) {
        try {
            createAuditTrail(
                    user,
                    actionType,
                    restaurant,
                    status,
                    ipAddress,
                    userAgent,
                    entityId,
                    entityType,
                    notes,
                    requestedBy,
                    requestedAt,
                    reviewedBy,
                    reviewedAt
            );
        } catch (org.springframework.dao.DataIntegrityViolationException e) {
            log.error("Failed to create audit trail due to database constraint violation. " +
                    "This usually means the database migration hasn't been run yet. ActionType: {}, EntityType: {}, Error: {}", 
                    actionType, entityType, e.getMessage());
            // Don't rethrow - audit trail failure shouldn't prevent the main operation
        } catch (Exception e) {
            log.error("Failed to create audit trail for action {} on entity {} ({}): {}", 
                    actionType, entityId, entityType, e.getMessage(), e);
            // Don't rethrow - audit trail failure shouldn't prevent the main operation
        }
    }


    @Override
    public ResponseDto<BulkUpload> processBulkUpload(MultipartFile file, MultipartFile imageZipFile, String action, String utfType, String language, String userId, String userRole, String localeHeader) throws IOException {
        // Get locale from request header and use it as language if not provided
        if (language == null || language.trim().isEmpty()) {
            language = localeHeader;
        }

        log.info("Processing bulk upload. Action: {}, File: {}, UTF Type: {}, Language: {}, Has Images: {}", 
                action, file.getOriginalFilename(), utfType, language, imageZipFile != null && !imageZipFile.isEmpty());

        if (file == null || file.isEmpty()) {
            throw new BadRequestException("File is required");
        }

        UUID currentUserId = UUID.fromString(userId);
        
        String localDir = System.getProperty("user.home") + "/bulk-upload-files";
        File directory = new File(localDir);
        if (!directory.exists()) {
            directory.mkdirs();
        }
        
        String localFileName = currentUserId + "_" + System.currentTimeMillis() + "_" + file.getOriginalFilename();
        String localFilePath = localDir + "/" + localFileName;
        File localFile = new File(localFilePath);

        try (FileOutputStream fos = new FileOutputStream(localFile)) {
            byte[] fileBytes = file.getBytes();
            fos.write(fileBytes);
        }
        
        log.info("Bulk upload file saved locally at: {}", localFilePath);
        
        String s3Url = null;
        try (FileInputStream fileInputStream = new FileInputStream(localFile)) {
            String s3Key = "bulk-upload/employee/" + currentUserId + "/" + file.getOriginalFilename();
            s3Url = awsService.uploadFile(fileInputStream, s3Key, localFile.length());
            log.info("Successfully uploaded file to S3. Key: {}, URL: {}", s3Key, s3Url);
        } catch (Exception e) {
            log.error("Failed to upload file to S3", e);
            s3Url = localFilePath;
        }
        
        // Read file using getBytes() to avoid stream consumption issues
        // getBytes() reads the entire file into memory, which can be used multiple times
        List<String[]> records = bulkUserUploadService.readCsvFile(file, StandardCharsets.UTF_8);
        
        // Validate headers before creating BulkUpload record - this is a critical check
        if (records == null || records.isEmpty()) {
            throw new BadRequestException("File is empty. Please provide a valid CSV file with headers and data.");
        }
        
        String[] header = records.get(0);
        String[] expectedHeaders = {
            "user_code*", "first_name*", "last_name*", "email*", "mobile_number*", 
            "role*", "employment_type*", "restaurant_code", "restaurant_group_code", 
            "language_code*", "status*", "shift*", "image_name"
        };
        
        // Clean header by removing non-printable characters and BOM
        String[] cleanedHeader = Arrays.stream(header)
            .map(h -> h != null ? h.replace("\uFEFF", "").replaceAll("[^\\x20-\\x7E]", "").trim() : "")
            .toArray(String[]::new);
        
        // Validate column count
        if (cleanedHeader.length != expectedHeaders.length) {
            String errorMessage = String.format(
                "Invalid number of columns. Expected %d columns but found %d columns. " +
                "Expected columns: %s. " +
                "Found columns: %s",
                expectedHeaders.length, 
                cleanedHeader.length,
                String.join(", ", expectedHeaders),
                String.join(", ", cleanedHeader)
            );
            log.error("Header validation failed in processBulkUpload - Column count mismatch. Expected: {}, Found: {}", 
                expectedHeaders.length, cleanedHeader.length);
            log.error("Expected headers: {}", Arrays.toString(expectedHeaders));
            log.error("Found headers: {}", Arrays.toString(cleanedHeader));
            throw new BadRequestException(errorMessage);
        }
        
        // Validate each header column name
        for (int i = 0; i < expectedHeaders.length; i++) {
            String expected = expectedHeaders[i].trim();
            String found = cleanedHeader[i];
            
            if (!expected.equalsIgnoreCase(found)) {
                String errorMessage = String.format(
                    "Invalid column name at position %d. Expected '%s' but found '%s'. " +
                    "All expected columns: %s",
                    (i + 1), expected, found, String.join(", ", expectedHeaders)
                );
                log.error("Header validation failed in processBulkUpload - Column name mismatch at position {}. Expected: '{}', Found: '{}'", 
                    (i + 1), expected, found);
                throw new BadRequestException(errorMessage);
            }
        }
        
        // Filter out blank rows to get accurate count
        List<String[]> nonBlankRecords = records.stream()
            .filter(row -> !Arrays.stream(row).allMatch(s -> s == null || s.trim().isEmpty()))
            .collect(Collectors.toList());
        
        // Calculate total records excluding header and blank rows
        int totalRecords = Math.max(0, nonBlankRecords.size() - 1);
        
        BulkUpload bulkUpload = new BulkUpload();
        bulkUpload.setCreatedBy(currentUserId);
        bulkUpload.setStatus(BulkUploadStatus.PENDING);
        bulkUpload.setUploadType(UploadType.USERS);  
        bulkUpload.setFilePath(s3Url != null ? s3Url : localFilePath);
        bulkUpload.setErrorFilePath("");
        bulkUpload.setTotalRecordCount(totalRecords);
        bulkUpload.setSuccessRecordCount(0);
        bulkUpload.setFailureRecordCount(0);
        bulkUpload.setReason(messageUtil.getMessage("bulk.upload.initiated", LocaleContextHolder.getLocale()));
        bulkUpload.setCreatedAt(OffsetDateTime.now(ZoneOffset.UTC));
        bulkUpload.setUpdatedAt(OffsetDateTime.now(ZoneOffset.UTC));
        bulkUpload = bulkUploadRepository.save(bulkUpload);

        log.info("Created PENDING bulk upload record with ID: {}", bulkUpload.getId());

        log.info("Starting async processing for bulk upload ID: {}", bulkUpload.getId());
        
        // Convert MultipartFile to byte[] for async processing (MultipartFile is not serializable)
        byte[] imageZipFileBytes = null;
        String imageZipFileName = null;
        if (imageZipFile != null && !imageZipFile.isEmpty()) {
            try {
                imageZipFileBytes = imageZipFile.getBytes();
                imageZipFileName = imageZipFile.getOriginalFilename();
                log.info("Converted imageZipFile to bytes: {}, Size: {} bytes", imageZipFileName, imageZipFileBytes.length);
            } catch (IOException e) {
                log.error("Failed to convert imageZipFile to bytes", e);
                imageZipFileBytes = null;
                imageZipFileName = null;
            }
        }
        
        bulkUserUploadService.processAndSaveUsersFromLocalFile(localFilePath, imageZipFileBytes, imageZipFileName, currentUserId.toString(), language, bulkUpload.getId(), String.valueOf(totalRecords));
        log.info("Async processing initiated, returning immediate response for bulk upload ID: {}", bulkUpload.getId());

        ResponseDto<BulkUpload> responseDto = ResponseDto.<BulkUpload>builder()
                .data(bulkUpload)
                .message(messageUtil.getMessage("bulk.upload.initiated", LocaleContextHolder.getLocale()))
                .build();

        log.info("Returning immediate response with PENDING status for bulk upload ID: {}", bulkUpload.getId());
        return responseDto;
    }


    @Override
    @Transactional
    public void validateSession(String token, String locale, String appType, String appVersion) {
        java.util.Locale userLocale = new java.util.Locale(locale);
        log.info("Validate session flow started for locale={}", locale);
        
        String userId = jwtUtil.getUserIdFromToken(token);
        UUID userUUID = UUID.fromString(userId);
        log.debug("Validate session step: extracted userId={} from JWT", userUUID);
        
        // Check if user is deleted
        User user = userRepository.findById(userUUID)
            .orElseThrow(() -> new BadRequestException(
                messageUtil.getMessage("user.session.invalid", userLocale)));
        
        if (Boolean.TRUE.equals(user.getIsDeleted())) {
            throw new BadRequestException(
                messageUtil.getMessage("user.session.invalid", userLocale));
        }
        
        Date tokenExpiry = jwtUtil.getExpirationFromToken(token);
        log.debug("Validate session step: token expiry extracted for userId={}, tokenExpiry={}", userUUID, tokenExpiry);
        
        LoginAudit loginAudit = loginAuditRepository.findByUser_Id(userUUID)
            .orElseThrow(() -> new BadRequestException(
                messageUtil.getMessage("user.session.invalid", userLocale)));
        
        OffsetDateTime auditExpiry = loginAudit.getLoginExpiryDate();
        Date auditExpiryDate = Date.from(auditExpiry.toInstant());
        
        Instant tokenInstant = tokenExpiry.toInstant().truncatedTo(ChronoUnit.SECONDS);
        Instant auditInstant = auditExpiryDate.toInstant().truncatedTo(ChronoUnit.SECONDS);
        
        if (!tokenInstant.equals(auditInstant)) {
            throw new BadRequestException(
                messageUtil.getMessage("user.session.expired", userLocale));
        }
        log.info("Validate session flow completed successfully for userId={}", userUUID);

        // Update "last seen" metadata for observability/auditing.
        loginAudit.setLastSeenAt(OffsetDateTime.now(ZoneOffset.UTC));

        if (appVersion != null && !appVersion.isBlank()) {
            loginAudit.setAppVersion(appVersion.trim());
        }
        if (appType != null && !appType.isBlank()) {
            try {
                loginAudit.setAppType(AppType.fromString(appType.trim()));
            } catch (Exception ignored) {
                // Do not break auth flow on bad client headers
            }
        }
        loginAuditRepository.save(loginAudit);
    }


    @Transactional
    public ResponseDto<Void> deleteMultipleUsers(List<UUID> userIds, String deletedReason, String updaterId, String userRole) {
        Locale userLocale = LocaleContextHolder.getLocale();

        // Validate deleted reason is required
        if (deletedReason == null || deletedReason.trim().isEmpty()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    messageUtil.getMessage("user.delete.reason.required", userLocale)
            );
        }

        if (!"HQ_ADMIN".equals(userRole)) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    messageUtil.getMessage("user.delete.unauthorized", userLocale)
            );
        }

        User updater = userRepository.findById(UUID.fromString(updaterId))
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        messageUtil.getMessage("user.not.found", userLocale)
                ));

        long existingCount = userRepository.countByIdInAndIsDeletedFalse(userIds);
        if (existingCount != userIds.size()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    messageUtil.getMessage("user.delete.multiple.invalid", userLocale)
            );
        }

        List<User> usersToDelete = userRepository.findByIdInAndIsDeletedFalse(userIds);

        // Validate no user has active table assignments or pending orders
        for (User candidate : usersToDelete) {
            boolean hasActiveAssignments = !tableAssignmentRepository.findByWaiterIdAndUnassignedAtIsNull(candidate.getId()).isEmpty();
            if (hasActiveAssignments) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        messageUtil.getMessage("user.delete.error.active.table.assignments", userLocale)
                );
            }
            Long pendingOrdersCount = entityManager.createQuery(
                    "SELECT COUNT(o) FROM Order o WHERE o.waiter.id = :waiterId AND o.orderStatus IN (:statuses)", Long.class)
                .setParameter("waiterId", candidate.getId())
                .setParameter("statuses", java.util.List.of(OrderStatus.PUSHED, OrderStatus.IN_PROGRESS))
                .getSingleResult();
            boolean hasPendingOrders = pendingOrdersCount != null && pendingOrdersCount > 0;
            if (hasPendingOrders) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        messageUtil.getMessage("user.delete.error.pending.orders", userLocale)
                );
            }
        }

        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        usersToDelete.forEach(user -> {
            user.setIsDeleted(true);
            user.setStatus(EntityStatus.INACTIVE);
            user.setDeletedReason(deletedReason);
            user.setUpdatedAt(now);
            user.setUpdatedBy(updater);
        });

        userRepository.saveAll(usersToDelete);

        for (User user : usersToDelete) {
            publishUserDeletedEmailSchedulesAfterCommit(user.getId());
        }

        // Delete login audit records for all users
        for (User user : usersToDelete) {
            try {
                loginAuditRepository.deleteByUser_Id(user.getId());
            } catch (Exception e) {
                log.error("Failed to delete login audit records for user {}: {}", user.getId(), e.getMessage());
            }
        }

        // Log manager action for bulk deleting employees
        try {
            for (User user : usersToDelete) {
                auditLoggingService.logManagerAction(
                        updater,
                        user,
                        AuditLogging.AuditAction.BULK_DELETE_EMPLOYEES,
                        user.getRestaurantId()
                );
            }
        } catch (Exception e) {
            log.error("Failed to log manager action for bulk user deletion: {}", e.getMessage());
        }

        // Create audit trail for each user deletion
        for (User user : usersToDelete) {
            try {
                Restaurant restaurant = null;
                if (user.getRestaurantId() != null) {
                    restaurant = restaurantRepository.findById(user.getRestaurantId()).orElse(null);
                }
                createAuditTrail(
                        updater,
                        ActionType.USER_DELETE,
                        restaurant,
                        RequestStatus.NA, // Non-request action - always NA
                        null, // ipAddress - not available in this context
                        null, // userAgent - not available in this context
                        user.getId(),
                        "USER",
                        "User deleted: " + user.getUserCode() + " (" + user.getFirstName() + " " + user.getLastName() + ")",
                        null, // requestedBy
                        null, // requestedAt
                        null, // reviewedBy
                        null  // reviewedAt
                );
            } catch (Exception e) {
                log.error("Failed to create audit trail for user deletion (ID: {}): {}", user.getId(), e.getMessage());
                // Don't break user deletion flow if audit trail fails
            }
        }

        return ResponseDto.<Void>builder()
            .message(messageUtil.getMessage("user.delete.multiple.success", userLocale))
            .build();
    }
    
    @Transactional
    public ResponseDto<UserDataResponse> updatePreferredLanguage(UUID userId, UpdatePreferredLanguageRequest request, String updaterId) {
        Locale userLocale = LocaleContextHolder.getLocale();

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        messageUtil.getMessage("user.not.found", userLocale)));

        if (Boolean.TRUE.equals(user.getIsDeleted())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    messageUtil.getMessage("user.update.error.deleted", userLocale));
        }

        // Validate language code
        if (request.getLanguageCode() == null || !localizationProperties.getLanguages().contains(request.getLanguageCode())) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    messageUtil.getMessage("user.update.error.invalid.language", userLocale));
        }

        // Update language code
        user.setLanguageCode(request.getLanguageCode());
        user.setUpdatedAt(OffsetDateTime.now(ZoneOffset.UTC));

        User updater = userRepository.findById(UUID.fromString(updaterId))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        messageUtil.getMessage("user.not.found", userLocale)));
        user.setUpdatedBy(updater);

        userRepository.save(user);

        String signedPhotoUrl = null;
        if (user.getPhotoUrl() != null && !user.getPhotoUrl().isEmpty()) {
            signedPhotoUrl = awsService.getPreSignedUrl(user.getPhotoUrl());
        }

        UserResponseDto userResponse = UserResponseDto.builder()
                .id(user.getId())
                .firstName(user.getFirstName())
                .lastName(user.getLastName())
                .email(user.getEmail())
                .contactNumber(user.getContactNumber())
                .photoUrl(signedPhotoUrl)
                .employmentType(user.getEmploymentType() != null ? user.getEmploymentType().name() : null)
                .userCode(user.getUserCode())
                .roleId(user.getRoleId())
                .languageCode(user.getLanguageCode())
                .restaurantId(user.getRestaurantId())
                .shiftId(null) // Not updating shift in this method
                .status(user.getStatus() != null ? user.getStatus().name() : null)
                .isStatusLocked(user.getIsStatusLocked())
                .build();

        UserDataResponse data = UserDataResponse.builder()
                .user(userResponse)
                .count(1L)
                .total(1L)
                .metaData(null)
                .build();

        return ResponseDto.<UserDataResponse>builder()
                .message(messageUtil.getMessage("user.preferred.language.update.success", userLocale))
                .data(data)
                .build();
    }



    @Override
    @Transactional
    public ResponseDto<Void> cancelProfileUpdateRequest(String userId) {
        Locale userLocale = LocaleContextHolder.getLocale();
        
        User user = userRepository.findById(UUID.fromString(userId))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        messageUtil.getMessage("user.not.found", userLocale)));
        
        if (user.getProfileUpdateRequestStatus() != RequestStatus.OPEN) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    messageUtil.getMessage("user.profile.update.request.not.pending", userLocale));
        }
        
        // Clear the request data and reset status
        user.setProfileUpdateRequestStatus(RequestStatus.NONE);
        user.setProfileUpdateRequestData(null);
        user.setProfileUpdateRequestedAt(null);
        user.setProfileUpdateRequestLockedStatus(null);
        user.setUpdatedAt(OffsetDateTime.now(ZoneOffset.UTC));
        
        userRepository.save(user);
        
        return ResponseDto.<Void>builder()
                .message(messageUtil.getMessage("user.profile.update.request.cancelled", userLocale))
                .build();
    }

    @Override
    @Transactional
    public ResponseDto<ProfileUpdateRequestResponse> approveOrDeclineProfileUpdateRequest(UUID userId, ProfileUpdateApprovalRequest request, String managerId, String managerRole) {
        Locale userLocale = LocaleContextHolder.getLocale();
        
        // Only MANAGER and HQ_ADMIN can approve/decline requests
        if (!"MANAGER".equals(managerRole) && !"HQ_ADMIN".equals(managerRole)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    messageUtil.getMessage("user.profile.update.request.unauthorized", userLocale));
        }
        
        // Atomic request state update with pessimistic locking
        // Lock the user entity to prevent concurrent modifications
        User user = entityManager.find(User.class, userId, jakarta.persistence.LockModeType.PESSIMISTIC_WRITE);
        if (user == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                    messageUtil.getMessage("user.not.found", userLocale));
        }
        
        if (user.getProfileUpdateRequestStatus() != RequestStatus.OPEN) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    messageUtil.getMessage("user.profile.update.request.not.pending", userLocale));
        }

        // Determine approval authority:
        // 1. If requester is MANAGER -> HQ_ADMIN must approve
        // 2. If user is currently locked (isStatusLocked = true) -> HQ_ADMIN must approve
        // 3. Otherwise -> MANAGER can approve
        // IMPORTANT: Use the CURRENT lock status, not the status at request creation time.
        // This ensures consistency with the listing logic (which also uses current status).
        // If HQ unlocks a user after the request was created, the Manager should be able to approve it.
        boolean isLocked = Boolean.TRUE.equals(user.getIsStatusLocked());
        boolean requesterIsManager = false;
        
        // Check if requester (stored in updatedBy when request was created) is a MANAGER
        if (user.getUpdatedBy() != null && user.getUpdatedBy().getRoleId() != null) {
            Role requesterRole = roleRepository.findById(user.getUpdatedBy().getRoleId()).orElse(null);
            if (requesterRole != null && "MANAGER".equals(requesterRole.getName())) {
                requesterIsManager = true;
            }
        }
        
        String approvalAuthority;
        if (requesterIsManager || isLocked) {
            approvalAuthority = "HQ_ADMIN";
        } else {
            approvalAuthority = "MANAGER";
        }

        // Enforce that only appropriate role can approve
        if (!managerRole.equalsIgnoreCase(approvalAuthority)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    messageUtil.getMessage("user.profile.update.request.unauthorized", userLocale));
        }
        
        User manager = userRepository.findById(UUID.fromString(managerId))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        messageUtil.getMessage("user.not.found", userLocale)));
        
        // Use UTC timezone to match the rest of the application
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        
        // Declare email change tracking variables outside try block for use after approval
        boolean emailChanged = false;
        String newEmailValue = null;
        
         if (request.getAction() == RequestStatus.APPROVED) {
             // Apply the requested changes IMMEDIATELY
             try {
                 ObjectMapper objectMapper = new ObjectMapper();
                 UserProfileUpdateRequest updateRequest = objectMapper.readValue(user.getProfileUpdateRequestData(), UserProfileUpdateRequest.class);
                 
                 // Store old email before updating to check if it changed
                 String oldEmail = user.getEmail() != null ? user.getEmail().trim() : null;
                 newEmailValue = updateRequest.getEmail() != null ? updateRequest.getEmail().trim() : null;
                 emailChanged = !Objects.equals(oldEmail, newEmailValue);
                 
                 // Apply changes to user profile
                 user.setFirstName(updateRequest.getFirstName());
                 user.setLastName(updateRequest.getLastName());
                 user.setEmail(updateRequest.getEmail());
                 user.setContactNumber(updateRequest.getContactNumber());
                 // Apply userCode if present in the request
                 if (updateRequest.getUserCode() != null) {
                     user.setUserCode(updateRequest.getUserCode());
                 }
                 // Only update photo if it's present in the request (not null)
                 // Photo may be null if user can update it directly (already updated immediately)
                 if (updateRequest.getPhotoUrl() != null) {
                     user.setPhotoUrl(awsService.stripToKey(updateRequest.getPhotoUrl()));
                 }
                 user.setLanguageCode(updateRequest.getLanguageCode());
                 
                 // Keep status as APPROVED to maintain history for HQ visibility
                 // The request data is preserved so HQ can see what was approved
                 // When a new profile update request is created, status will be reset to OPEN
                 // This allows HQ to see all approved requests in the history
                 user.setProfileUpdateRequestStatus(RequestStatus.APPROVED);
                 
             } catch (JsonProcessingException e) {
                 log.error("Error parsing request data: {}", e.getMessage());
                 throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                         messageUtil.getMessage("user.profile.update.request.error", userLocale));
             }
         } else if (request.getAction() == RequestStatus.DECLINED) {
             // Keep request data for reference but mark as declined
             user.setProfileUpdateRequestStatus(RequestStatus.DECLINED);
         }
         
         // IMPORTANT: Get the requester BEFORE overwriting updatedBy field
         // The requester is the user who requested the update (stored in updatedBy when request was created)
         User requester = user.getUpdatedBy() != null ? user.getUpdatedBy() : user;
         
         // Set review timestamp (using UTC to match the rest of the application)
         user.setProfileUpdateReviewedAt(now);
         user.setUpdatedAt(now);
         user.setUpdatedBy(manager);
        
        userRepository.save(user);
        
        if (request.getAction() == RequestStatus.APPROVED && emailChanged && newEmailValue != null && !newEmailValue.isEmpty()) {
            sendEmailChangeNotification(user, newEmailValue, userLocale, managerRole);
        }
        
        // Move non-critical post-approval side effects off the request thread.
        // This reduces API latency while preserving behavior via best-effort async processing.
        UserServiceImpl self = applicationContext.getBean(UserServiceImpl.class);
        self.handleProfileUpdatePostActionsAsync(
                user.getId(),
                requester.getId(),
                manager.getId(),
                request.getAction(),
                request.getComments(),
                userLocale != null ? userLocale.toLanguageTag() : "en",
                now
        );
        
        // Build response
        ProfileUpdateRequestResponse response = ProfileUpdateRequestResponse.builder()
                .userId(user.getId())
                .firstName(user.getFirstName())
                .lastName(user.getLastName())
                .email(user.getEmail())
                .userCode(user.getUserCode())
                .status(user.getProfileUpdateRequestStatus())
                .requestData(user.getProfileUpdateRequestData())
                .requestedAt(user.getProfileUpdateRequestedAt() != null ? user.getProfileUpdateRequestedAt().toLocalDateTime() : null)
                .reviewedAt(user.getProfileUpdateReviewedAt() != null ? user.getProfileUpdateReviewedAt().toLocalDateTime() : null)
                .reviewedBy(manager.getId())
                .reviewedByName(manager.getFirstName() + " " + manager.getLastName())
                .comments(request.getComments())
                .build();
        
        String messageKey = request.getAction() == RequestStatus.APPROVED ? 
                "user.profile.update.request.approved" : "user.profile.update.request.declined";
        
        return ResponseDto.<ProfileUpdateRequestResponse>builder()
                .message(messageUtil.getMessage(messageKey, userLocale))
                .data(response)
                .build();
    }

    @Async
    public CompletableFuture<Void> handleProfileUpdatePostActionsAsync(
            UUID userId,
            UUID requesterId,
            UUID managerId,
            RequestStatus actionStatus,
            String comments,
            String localeTag,
            OffsetDateTime reviewedAt
    ) {
        Locale locale = Locale.forLanguageTag(localeTag != null ? localeTag : "en");
        try {
            User user = userRepository.findById(userId).orElse(null);
            User requester = userRepository.findById(requesterId).orElse(null);
            User manager = userRepository.findById(managerId).orElse(null);

            if (user == null || requester == null || manager == null) {
                log.warn("Skipping async post-actions for profile update due to missing entities. userId={}, requesterId={}, managerId={}",
                        userId, requesterId, managerId);
                return CompletableFuture.completedFuture(null);
            }

            boolean approved = actionStatus == RequestStatus.APPROVED;
            ActionType actionType = approved
                    ? ActionType.REQUEST_PROFILE_UPDATE_APPROVE
                    : ActionType.REQUEST_PROFILE_UPDATE_DECLINE;

            try {
                AuditLogging.AuditAction auditAction = approved
                        ? AuditLogging.AuditAction.APPROVE_PROFILE_UPDATE
                        : AuditLogging.AuditAction.DECLINE_PROFILE_UPDATE;
                auditLoggingService.logManagerAction(manager, user, auditAction, user.getRestaurantId());
            } catch (Exception e) {
                log.error("Failed to log manager action for profile update {}: {}", actionStatus, e.getMessage());
            }

            try {
                Restaurant restaurant = null;
                if (user.getRestaurantId() != null) {
                    restaurant = restaurantRepository.findById(user.getRestaurantId()).orElse(null);
                }
                createAuditTrail(
                        manager,
                        actionType,
                        restaurant,
                        actionStatus,
                        null,
                        null,
                        user.getId(),
                        "USER",
                        String.format("Profile update request %s. Comments: %s",
                                approved ? "approved" : "declined",
                                comments != null ? comments : "N/A"),
                        requester,
                        user.getProfileUpdateRequestedAt(),
                        manager,
                        reviewedAt
                );
            } catch (Exception e) {
                log.error("Failed to create audit trail for profile update: {}", e.getMessage());
            }

            try {
                saveProfileUpdateRequestNotification(requester, actionStatus, manager, locale, comments, user);
            } catch (Exception e) {
                log.error("Failed to save profile update request notification: {}", e.getMessage());
            }

            try {
                publishProfileUpdateNotificationToRabbitMQ(user, requester, manager, approved, comments, locale);
                log.info("Published profile update request decision notification to RabbitMQ for requester {} and user {}",
                        requester.getId(), user.getId());
            } catch (Exception e) {
                log.error("Failed to publish profile update notification to RabbitMQ: {}", e.getMessage(), e);
            }

            Object wsNotifier = resolveNotificationService();
            if (wsNotifier != null) {
                try {
                    java.lang.reflect.Method method = wsNotifier.getClass().getMethod(
                            "notifyProfileUpdateRequestDecision",
                            User.class, User.class, boolean.class, String.class, Locale.class, User.class);
                    method.invoke(wsNotifier, user, requester, approved, comments, locale, manager);
                } catch (NoSuchMethodException e) {
                    log.debug("Method notifyProfileUpdateRequestDecision not found in NotificationService: {}", e.getMessage());
                } catch (Exception e) {
                    log.debug("Failed to send profile update notification via direct call: {}", e.getMessage());
                }
            }

            try {
                UUID restaurantId = user.getRestaurantId();
                if (restaurantId != null) {
                    List<User> activeManagers = findActiveManagersForRestaurant(restaurantId);
                    notifyManagersAboutRequestResolution(
                            activeManagers,
                            manager,
                            user,
                            messageUtil.getMessage("request.type.profile.update", locale),
                            approved,
                            comments,
                            locale
                    );
                }
            } catch (Exception e) {
                log.error("Failed to notify managers about profile update request resolution: {}", e.getMessage(), e);
            }

            try {
                UUID restaurantId = user.getRestaurantId();
                if (restaurantId != null) {
                    sendUserUpdateWebSocketProfileUpdateDecisionNotification(restaurantId, actionStatus, user.getId(), locale);
                }
            } catch (Exception e) {
                log.warn("Failed to send user-update WebSocket notification for profile update decision: {}", e.getMessage());
            }
        } catch (Exception e) {
            log.error("Unexpected failure in async profile update post-actions: {}", e.getMessage(), e);
        }
        return CompletableFuture.completedFuture(null);
    }

    /**
     * Helper method to get all profile update requests without pagination, sorted by request date descending
     */
    private List<ProfileUpdateRequestWithComparisonResponse> getAllProfileUpdateRequestsWithoutPagination(RequestStatus status, String userRole) {
        Locale userLocale = LocaleContextHolder.getLocale();
        
        // Build specification for filtering
        Specification<User> spec = (root, query, criteriaBuilder) -> {
            List<Predicate> predicates = new ArrayList<>();
            
            // Base condition: not deleted
            predicates.add(criteriaBuilder.equal(root.get("isDeleted"), false));
            
            // Filter by request status
            // If status is null, show all requests (OPEN, APPROVED, DECLINED) - not just OPEN
            if (status != null) {
                predicates.add(criteriaBuilder.equal(root.get("profileUpdateRequestStatus"), status));
            } else {
                // Show all requests with status != NONE (i.e., OPEN, APPROVED, DECLINED)
                predicates.add(criteriaBuilder.notEqual(root.get("profileUpdateRequestStatus"), RequestStatus.NONE));
            }

            // Role-based visibility filter
            // MANAGER and CASHIER: see requests that managers can approve (unlocked + roles CASHIER/WAITER/KDS, but NOT requests from MANAGERs)
            // HQ_ADMIN: see requests that HQ can approve (locked users OR requests from MANAGERs)
            if ("MANAGER".equals(userRole) || "CASHIER".equals(userRole)) {
                // unlocked users AND userRole in {CASHIER, WAITER, KDS} AND requester is NOT MANAGER
                Predicate unlocked = criteriaBuilder.isFalse(root.get("isStatusLocked"));
                Subquery<Role> roleSub = query.subquery(Role.class);
                Root<Role> roleRoot = roleSub.from(Role.class);
                roleSub.select(roleRoot);
                roleSub.where(
                    criteriaBuilder.and(
                        criteriaBuilder.equal(roleRoot.get("id"), root.get("roleId")),
                        roleRoot.get("name").in("CASHIER", "WAITER", "KDS")
                    )
                );
                
                // Exclude requests where the user's own role is MANAGER
                // For profile update requests, the requester is the user themselves (not updatedBy)
                // When a request is approved, updatedBy becomes the manager who approved it,
                // so we must check the user's own role, not updatedBy
                Subquery<Role> userRoleSub = query.subquery(Role.class);
                Root<Role> userRoleRoot = userRoleSub.from(Role.class);
                userRoleSub.select(userRoleRoot);
                userRoleSub.where(
                    criteriaBuilder.and(
                        criteriaBuilder.equal(userRoleRoot.get("id"), root.get("roleId")),
                        criteriaBuilder.equal(userRoleRoot.get("name"), "MANAGER")
                    )
                );
                Predicate userNotManager = criteriaBuilder.not(criteriaBuilder.exists(userRoleSub));
                
                predicates.add(criteriaBuilder.and(unlocked, criteriaBuilder.exists(roleSub), userNotManager));
            } else if ("HQ_ADMIN".equals(userRole)) {
                // locked users OR requests where the user's own role is MANAGER
                Predicate locked = criteriaBuilder.isTrue(root.get("isStatusLocked"));
                
                // Check if the user's own role is MANAGER
                // For profile update requests, the requester is the user themselves (not updatedBy)
                // When a request is approved, updatedBy becomes the manager who approved it,
                // so we must check the user's own role, not updatedBy
                Subquery<Role> userRoleSub = query.subquery(Role.class);
                Root<Role> userRoleRoot = userRoleSub.from(Role.class);
                userRoleSub.select(userRoleRoot);
                userRoleSub.where(
                    criteriaBuilder.and(
                        criteriaBuilder.equal(userRoleRoot.get("id"), root.get("roleId")),
                        criteriaBuilder.equal(userRoleRoot.get("name"), "MANAGER")
                    )
                );
                Predicate userIsManager = criteriaBuilder.exists(userRoleSub);
                
                predicates.add(criteriaBuilder.or(locked, userIsManager));
            }
            
            return criteriaBuilder.and(predicates.toArray(new Predicate[0]));
        };
        
        // Fetch users with pending requests, sorted by request date descending
        Sort sort = Sort.by(Sort.Direction.DESC, "profileUpdateRequestedAt");
        List<User> usersWithRequests = ((JpaSpecificationExecutor<User>) userRepository).findAll(spec, sort);
        
        // Additional filtering in Java for MANAGER requests visibility
        // Filter out MANAGER requests from MANAGER view, and include them in HQ_ADMIN view
        List<User> filteredUsers = new ArrayList<>();
        for (User user : usersWithRequests) {
            if ("MANAGER".equals(userRole)) {
                // MANAGER should not see requests from other MANAGERs
                // For profile update requests, the requester is the user themselves (not updatedBy)
                // When a request is approved, updatedBy becomes the manager who approved it,
                // so we must check the user's own role, not updatedBy
                if (user.getRoleId() != null) {
                    Role userRoleObj = roleRepository.findById(user.getRoleId()).orElse(null);
                    if (userRoleObj != null && "MANAGER".equals(userRoleObj.getName())) {
                        continue; // Skip this request
                    }
                }
            } else if ("HQ_ADMIN".equals(userRole)) {
                // HQ_ADMIN should see locked users OR requests from MANAGERs
                // For profile update requests, the requester is the user themselves (not updatedBy)
                boolean isLocked = Boolean.TRUE.equals(user.getIsStatusLocked());
                boolean userIsManager = false;
                if (user.getRoleId() != null) {
                    Role userRoleObj = roleRepository.findById(user.getRoleId()).orElse(null);
                    if (userRoleObj != null && "MANAGER".equals(userRoleObj.getName())) {
                        userIsManager = true;
                    }
                }
                // If not locked and user is not MANAGER, skip (already filtered by spec, but double-check)
                if (!isLocked && !userIsManager) {
                    continue; // Skip this request
                }
            }
            filteredUsers.add(user);
        }
        
        // Convert to response DTOs with comparison data
        List<ProfileUpdateRequestWithComparisonResponse> requestResponses = new ArrayList<>();
        
        for (User user : filteredUsers) {
            try {
                ObjectMapper objectMapper = new ObjectMapper();
                UserProfileUpdateRequest updateRequest = null;
                
                // Parse the request data if it exists
                if (user.getProfileUpdateRequestData() != null) {
                    updateRequest = objectMapper.readValue(user.getProfileUpdateRequestData(), UserProfileUpdateRequest.class);
                }
                
                // For old vs new data comparison:
                // - For OPEN/PENDING: Old = current user data, New = requested data from requestData
                // - For APPROVED: Old = requested data (what was requested, now applied), New = current user data (approved data)
                // - For DECLINED: Old = current user data (unchanged), New = requested data (what was declined)
                
                String oldFirstName, oldLastName, oldEmail, oldContactNumber, oldPhotoUrl, oldLanguageCode;
                String newFirstName, newLastName, newEmail, newContactNumber, newPhotoUrl, newLanguageCode;
                
                String normalizedUserPhotoUrl = (user.getPhotoUrl() != null && !user.getPhotoUrl().isEmpty()) ? awsService.stripToKey(user.getPhotoUrl()) : null;
                if (user.getProfileUpdateRequestStatus() == RequestStatus.APPROVED) {
                    // For approved requests: Old = what was requested (from requestData), New = current user data (approved)
                    oldFirstName = updateRequest != null ? updateRequest.getFirstName() : user.getFirstName();
                    oldLastName = updateRequest != null ? updateRequest.getLastName() : user.getLastName();
                    oldEmail = updateRequest != null ? updateRequest.getEmail() : user.getEmail();
                    oldContactNumber = updateRequest != null ? updateRequest.getContactNumber() : user.getContactNumber();
                    oldPhotoUrl = updateRequest != null ? updateRequest.getPhotoUrl() : normalizedUserPhotoUrl;
                    oldLanguageCode = updateRequest != null ? updateRequest.getLanguageCode() : user.getLanguageCode();
                    
                    newFirstName = user.getFirstName();
                    newLastName = user.getLastName();
                    newEmail = user.getEmail();
                    newContactNumber = user.getContactNumber();
                    newPhotoUrl = normalizedUserPhotoUrl;
                    newLanguageCode = user.getLanguageCode();
                } else {
                    // For OPEN/PENDING/DECLINED: Old = current user data, New = requested data
                    oldFirstName = user.getFirstName();
                    oldLastName = user.getLastName();
                    oldEmail = user.getEmail();
                    oldContactNumber = user.getContactNumber();
                    oldPhotoUrl = normalizedUserPhotoUrl;
                    oldLanguageCode = user.getLanguageCode();
                    
                    newFirstName = updateRequest != null ? updateRequest.getFirstName() : null;
                    newLastName = updateRequest != null ? updateRequest.getLastName() : null;
                    newEmail = updateRequest != null ? updateRequest.getEmail() : null;
                    newContactNumber = updateRequest != null ? updateRequest.getContactNumber() : null;
                    newPhotoUrl = updateRequest != null ? updateRequest.getPhotoUrl() : null;
                    newLanguageCode = updateRequest != null ? updateRequest.getLanguageCode() : null;
                }
                
                boolean firstNameChanged = updateRequest != null && !Objects.equals(oldFirstName, newFirstName);
                boolean lastNameChanged = updateRequest != null && !Objects.equals(oldLastName, newLastName);
                boolean emailChanged = updateRequest != null && !Objects.equals(oldEmail, newEmail);
                boolean contactNumberChanged = updateRequest != null && !Objects.equals(oldContactNumber, newContactNumber);
                boolean photoUrlChanged = updateRequest != null && !Objects.equals(oldPhotoUrl, newPhotoUrl);
                boolean languageCodeChanged = updateRequest != null && !Objects.equals(oldLanguageCode, newLanguageCode);
                
                // Extract reason from requestData if available (for future use)
                String reason = null;
                try {
                    if (user.getProfileUpdateRequestData() != null && updateRequest != null) {
                        // Check if there's a reason field in the request data
                        // For now, reason is not stored in UserProfileUpdateRequest, so it will be null
                        // This field is added for future extensibility
                        reason = null;
                    }
                } catch (Exception e) {
                    log.warn("Error extracting reason from profile update request data: {}", e.getMessage());
                }
                
                // Get role name for requestedBy user (for profile updates, updatedBy is the requester)
                String requestedByRole = null;
                User requester = user.getUpdatedBy() != null ? user.getUpdatedBy() : user;
                if (requester != null && requester.getRoleId() != null) {
                    var role = roleRepository.findById(requester.getRoleId()).orElse(null);
                    if (role != null) {
                        requestedByRole = role.getName();
                    }
                }
                
                // Get restaurant name for profile update request
                String restaurantName = null;
                if (user.getRestaurantId() != null) {
                    Optional<Restaurant> restaurantOpt = restaurantRepository.findById(user.getRestaurantId());
                    if (restaurantOpt.isPresent()) {
                        Restaurant restaurant = restaurantOpt.get();
                        if (restaurant.getTranslations() != null && !restaurant.getTranslations().isEmpty()) {
                            String userLanguage = userLocale.getLanguage();
                            restaurantName = restaurant.getTranslations().stream()
                                    .filter(t -> t.getLanguageCode() != null && t.getLanguageCode().startsWith(userLanguage))
                                    .findFirst()
                                    .map(com.gulfnet.shared_library.entity.RestaurantTranslation::getName)
                                    .orElse(restaurant.getTranslations().get(0).getName());
                        } else {
                            restaurantName = "Restaurant";
                        }
                    }
                }
                
                ProfileUpdateRequestWithComparisonResponse response = ProfileUpdateRequestWithComparisonResponse.builder()
                        .userId(user.getId())
                        .firstName(user.getFirstName())
                        .lastName(user.getLastName())
                        .email(user.getEmail())
                        .userCode(user.getUserCode())
                        .status(user.getProfileUpdateRequestStatus())
                        .requestData(user.getProfileUpdateRequestData())
                        .requestedAt(user.getProfileUpdateRequestedAt() != null ? user.getProfileUpdateRequestedAt().toLocalDateTime() : null)
                        .requestedBy(requester != null ? requester.getId() : null)
                        .requestedByName(requester != null ? 
                                requester.getFirstName() + " " + requester.getLastName() : null)
                        .requestedByRole(requestedByRole)
                        .reviewedAt(user.getProfileUpdateReviewedAt() != null ? user.getProfileUpdateReviewedAt().toLocalDateTime() : null)
                        .reviewedBy(user.getUpdatedBy() != null ? user.getUpdatedBy().getId() : null)
                        .reviewedByName(user.getUpdatedBy() != null ? 
                                user.getUpdatedBy().getFirstName() + " " + user.getUpdatedBy().getLastName() : null)
                        .comments(null) // Comments are only available when approving/declining, not stored in User entity
                        .reason(reason)
                        .restaurantName(restaurantName)
                        .oldFirstName(oldFirstName)
                        .oldLastName(oldLastName)
                        .oldEmail(oldEmail)
                        .oldContactNumber(oldContactNumber)
                        .oldPhotoUrl(oldPhotoUrl)
                        .oldLanguageCode(oldLanguageCode)
                        .newFirstName(newFirstName)
                        .newLastName(newLastName)
                        .newEmail(newEmail)
                        .newContactNumber(newContactNumber)
                        .newPhotoUrl(newPhotoUrl)
                        .newLanguageCode(newLanguageCode)
                        .firstNameChanged(firstNameChanged)
                        .lastNameChanged(lastNameChanged)
                        .emailChanged(emailChanged)
                        .contactNumberChanged(contactNumberChanged)
                        .photoUrlChanged(photoUrlChanged)
                        .languageCodeChanged(languageCodeChanged)
                        .build();
                
                requestResponses.add(response);
                
            } catch (JsonProcessingException e) {
                log.error("Error parsing request data for user {}: {}", user.getId(), e.getMessage());
                // Continue with other users even if one fails
            }
        }
        
        return requestResponses;
    }

    @Override
    @Transactional(readOnly = true)
    public ResponseDto<ProfileUpdateRequestWithComparisonListResponse> getPendingProfileUpdateRequestsWithComparison(int page, int size, RequestStatus status, String userRole) {
        Locale userLocale = LocaleContextHolder.getLocale();
        
        // Only MANAGER and HQ_ADMIN can view profile update requests
        if (!"MANAGER".equals(userRole) && !"HQ_ADMIN".equals(userRole)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    messageUtil.getMessage("user.profile.update.request.unauthorized", userLocale));
        }
        
        // Build specification for filtering
        Specification<User> spec = (root, query, criteriaBuilder) -> {
            List<Predicate> predicates = new ArrayList<>();
            
            // Base condition: not deleted
            predicates.add(criteriaBuilder.equal(root.get("isDeleted"), false));
            
            // Filter by request status
            // If status is null, show all requests (OPEN, APPROVED, DECLINED) - not just OPEN
            if (status != null) {
                predicates.add(criteriaBuilder.equal(root.get("profileUpdateRequestStatus"), status));
            } else {
                // Show all requests with status != NONE (i.e., OPEN, APPROVED, DECLINED)
                predicates.add(criteriaBuilder.notEqual(root.get("profileUpdateRequestStatus"), RequestStatus.NONE));
            }

            // Role-based visibility filter
            // MANAGER: see requests that managers can approve (unlocked + roles CASHIER/WAITER/KDS, but NOT requests from MANAGERs)
            // HQ_ADMIN: see requests that HQ can approve (locked users OR requests from MANAGERs)
            if ("MANAGER".equals(userRole)) {
                // unlocked users AND userRole in {CASHIER, WAITER, KDS} AND requester is NOT MANAGER
                Predicate unlocked = criteriaBuilder.isFalse(root.get("isStatusLocked"));
                Subquery<Role> roleSub = query.subquery(Role.class);
                Root<Role> roleRoot = roleSub.from(Role.class);
                roleSub.select(roleRoot);
                roleSub.where(
                    criteriaBuilder.and(
                        criteriaBuilder.equal(roleRoot.get("id"), root.get("roleId")),
                        roleRoot.get("name").in("CASHIER", "WAITER", "KDS")
                    )
                );
                
                // Exclude requests where the user's own role is MANAGER
                // For profile update requests, the requester is the user themselves (not updatedBy)
                // When a request is approved, updatedBy becomes the manager who approved it,
                // so we must check the user's own role, not updatedBy
                Subquery<Role> userRoleSub = query.subquery(Role.class);
                Root<Role> userRoleRoot = userRoleSub.from(Role.class);
                userRoleSub.select(userRoleRoot);
                userRoleSub.where(
                    criteriaBuilder.and(
                        criteriaBuilder.equal(userRoleRoot.get("id"), root.get("roleId")),
                        criteriaBuilder.equal(userRoleRoot.get("name"), "MANAGER")
                    )
                );
                Predicate userNotManager = criteriaBuilder.not(criteriaBuilder.exists(userRoleSub));
                
                predicates.add(criteriaBuilder.and(unlocked, criteriaBuilder.exists(roleSub), userNotManager));
            } else if ("HQ_ADMIN".equals(userRole)) {
                // locked users OR requests where the user's own role is MANAGER
                Predicate locked = criteriaBuilder.isTrue(root.get("isStatusLocked"));
                
                // Check if the user's own role is MANAGER
                // For profile update requests, the requester is the user themselves (not updatedBy)
                // When a request is approved, updatedBy becomes the manager who approved it,
                // so we must check the user's own role, not updatedBy
                Subquery<Role> userRoleSub = query.subquery(Role.class);
                Root<Role> userRoleRoot = userRoleSub.from(Role.class);
                userRoleSub.select(userRoleRoot);
                userRoleSub.where(
                    criteriaBuilder.and(
                        criteriaBuilder.equal(userRoleRoot.get("id"), root.get("roleId")),
                        criteriaBuilder.equal(userRoleRoot.get("name"), "MANAGER")
                    )
                );
                Predicate userIsManager = criteriaBuilder.exists(userRoleSub);
                
                predicates.add(criteriaBuilder.or(locked, userIsManager));
            }
            
            return criteriaBuilder.and(predicates.toArray(new Predicate[0]));
        };
        
        // Get all requests without pagination, then apply pagination
        List<ProfileUpdateRequestWithComparisonResponse> requestResponses = getAllProfileUpdateRequestsWithoutPagination(status, userRole);
        
        // Apply pagination
        int pageNumber = (page > 0 ? page : 1) - 1;
        int pageSize = (size > 0) ? size : 10;
        int fromIndex = Math.min(pageNumber * pageSize, requestResponses.size());
        int toIndex = Math.min(fromIndex + pageSize, requestResponses.size());
        List<ProfileUpdateRequestWithComparisonResponse> paginatedRequests = fromIndex < requestResponses.size() ? 
                requestResponses.subList(fromIndex, toIndex) : new ArrayList<>();
        
        PaginationMetaData metaData = PaginationMetaData.builder()
                .page(pageNumber + 1)
                .size(pageSize)
                .totalPages((int) Math.ceil((double) requestResponses.size() / pageSize))
                .totalRecords((long) requestResponses.size())
                .build();
        
        ProfileUpdateRequestWithComparisonListResponse data = ProfileUpdateRequestWithComparisonListResponse.builder()
                .requests(paginatedRequests)
                .count((long) paginatedRequests.size())
                .total((long) requestResponses.size())
                .metaData(metaData)
                .build();
        
        return ResponseDto.<ProfileUpdateRequestWithComparisonListResponse>builder()
                .message(messageUtil.getMessage("user.profile.update.requests.retrieved", userLocale))
                .data(data)
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public ResponseDto<UnifiedRequestListResponse> getAllPendingRequests(int page, int size, RequestStatus status, String requestType, String sortBy, String sortDirection, String userRole, String userId) {
        Locale userLocale = LocaleContextHolder.getLocale();
        
        // Only MANAGER, HQ_ADMIN, and CASHIER can view requests
        if (!"MANAGER".equals(userRole) && !"HQ_ADMIN".equals(userRole) && !"CASHIER".equals(userRole)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    messageUtil.getMessage("user.profile.update.request.unauthorized", userLocale));
        }
        
        // For CASHIER role, allow any request type to be sent
        // Cashiers can see all request types (same as MANAGER) filtered by their restaurant
        
        // Get manager's or cashier's restaurant ID if user is a MANAGER or CASHIER
        UUID managerRestaurantId = null;
        if (("MANAGER".equals(userRole) || "CASHIER".equals(userRole)) && userId != null && !userId.trim().isEmpty() && !"null".equalsIgnoreCase(userId)) {
            try {
                Optional<User> userOpt = userRepository.findById(UUID.fromString(userId));
                if (userOpt.isPresent() && userOpt.get().getRestaurantId() != null) {
                    managerRestaurantId = userOpt.get().getRestaurantId();
                }
            } catch (IllegalArgumentException e) {
                log.warn("Invalid userId format: {}", userId);
            }
        }
        
        // Create final copy for use in lambda expressions
        final UUID finalManagerRestaurantId = managerRestaurantId;
        // For CASHIER role, get the cashier's user ID to filter requests they sent
        UUID cashierUserId = null;
        if ("CASHIER".equals(userRole) && userId != null && !userId.trim().isEmpty() && !"null".equalsIgnoreCase(userId)) {
            try {
                cashierUserId = UUID.fromString(userId);
            } catch (IllegalArgumentException e) {
                log.warn("Invalid cashier userId format: {}", userId);
            }
        }
        final UUID finalCashierUserId = cashierUserId;
        
        // Role-based request filtering:
        // MANAGER sees: Profile update requests (unlocked users from their restaurant) + Additional discount requests (from their restaurant) + Refund requests (from their restaurant) + Item cancellation requests (from their restaurant) + Transaction cancellation requests (from their restaurant) + Order cancellation requests (from their restaurant)
        // HQ_ADMIN sees: Profile update requests (locked users only, where isStatusLocked = true) + Table/Section requests (all)
        // CASHIER sees: Profile update requests (unlocked users from their restaurant) + Additional discount requests (from their restaurant) + Refund requests (from their restaurant) + Item cancellation requests (from their restaurant) + Transaction cancellation requests (from their restaurant) + Order cancellation requests (from their restaurant)
        
        // Validate pagination parameters
        int pageNumber = (page > 0 ? page : 1) - 1;
        int pageSize = (size > 0) ? size : 10;
        
        // Fetch ALL records for each type (without pagination), then combine, sort, and paginate
        // This ensures correct pagination across all request types
        
        // Get ALL profile update requests (without pagination)
        // MANAGER: sees unlocked users with CASHIER/WAITER/KDS roles
        // CASHIER: sees unlocked users with CASHIER/WAITER/KDS roles (same as MANAGER)
        // HQ_ADMIN: sees locked users
        List<ProfileUpdateRequestWithComparisonResponse> allProfileUpdateRequests = getAllProfileUpdateRequestsWithoutPagination(status, userRole);
        Set<UUID> profileRequestUserIds = allProfileUpdateRequests.stream()
                .map(ProfileUpdateRequestWithComparisonResponse::getUserId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<UUID, User> profileUsersById = profileRequestUserIds.isEmpty()
                ? Collections.emptyMap()
                : userRepository.findAllById(profileRequestUserIds).stream()
                        .collect(Collectors.toMap(User::getId, u -> u));
        
        // Convert profile update requests to simplified list items
        List<RequestListItemResponse> profileUpdateListItems = allProfileUpdateRequests.stream()
                .filter(req -> {
                    // For CASHIER role, only show requests they sent (where userId matches cashier's userId)
                    if ("CASHIER".equals(userRole) && finalCashierUserId != null) {
                        return req.getUserId().equals(finalCashierUserId);
                    }
                    // Filter by restaurant for MANAGER role
                    if (finalManagerRestaurantId != null) {
                        User user = profileUsersById.get(req.getUserId());
                        if (user != null && user.getRestaurantId() != null) {
                            return user.getRestaurantId().equals(finalManagerRestaurantId);
                        }
                        return false;
                    }
                    return true;
                })
                .map(req -> {
                    // Fetch restaurant name for the user
                    String restaurantName = null;
                    String requesterRole = null;
                    User user = profileUsersById.get(req.getUserId());
                    if (user != null) {
                        // Get role of the requester (for profile update, the requester is the user themselves)
                        if (user.getRoleId() != null) {
                            var role = roleRepository.findById(user.getRoleId()).orElse(null);
                            if (role != null) {
                                requesterRole = role.getName();
                            }
                        }
                        if (user.getRestaurantId() != null) {
                            Optional<Restaurant> restaurantOpt = restaurantRepository.findById(user.getRestaurantId());
                            if (restaurantOpt.isPresent()) {
                                Restaurant restaurant = restaurantOpt.get();
                                if (restaurant.getTranslations() != null && !restaurant.getTranslations().isEmpty()) {
                                    String userLanguage = userLocale.getLanguage();
                                    restaurantName = restaurant.getTranslations().stream()
                                            .filter(t -> t.getLanguageCode() != null && t.getLanguageCode().startsWith(userLanguage))
                                            .findFirst()
                                            .map(com.gulfnet.shared_library.entity.RestaurantTranslation::getName)
                                            .orElse(restaurant.getTranslations().get(0).getName());
                                } else {
                                    restaurantName = "Restaurant";
                                }
                            }
                        }
                    }
                    
                    return RequestListItemResponse.builder()
                            .requestId(req.getUserId())
                            .requestType(messageUtil.getMessage("request.type.profile.update", userLocale))
                            .raisedBy(req.getFirstName() + " " + req.getLastName())
                            .role(requesterRole)
                            .restaurant(restaurantName)
                            .requestDate(req.getRequestedAt())
                            .status(req.getStatus())
                            .entityId(req.getUserId())
                            .build();
                })
                .collect(Collectors.toList());

        // Get ALL shift discrepancy requests (cash drawer) - represented by CashierShift entities
        // Only MANAGER can view shift discrepancy requests from their restaurant
        List<RequestListItemResponse> shiftDiscrepancyListItems = new ArrayList<>();
        if ("MANAGER".equals(userRole) && finalManagerRestaurantId != null) {
            // Include PENDING_APPROVAL, APPROVED, and REJECTED statuses to show all shift discrepancy requests
            List<CashierShift> shifts = cashierShiftRepository.findShiftDiscrepancyRequestsByRestaurantId(finalManagerRestaurantId);

            shiftDiscrepancyListItems = shifts.stream()
                    .filter(shift -> {
                        // Map ShiftStatus to RequestStatus for filtering
                        RequestStatus mappedStatus;
                        switch (shift.getStatus()) {
                            case PENDING_APPROVAL:
                                mappedStatus = RequestStatus.OPEN;
                                break;
                            case APPROVED:
                                mappedStatus = RequestStatus.APPROVED;
                                break;
                            case REJECTED:
                                mappedStatus = RequestStatus.DECLINED;
                                break;
                            default:
                                mappedStatus = RequestStatus.NONE;
                                break;
                        }
                        // If status filter is provided, respect it
                        if (status != null && status != mappedStatus) {
                            return false;
                        }
                        return true;
                    })
                    .map(shift -> {
                        // Determine restaurant name from shift.restaurant
                        String restaurantName = null;
                        if (shift.getRestaurant() != null &&
                                shift.getRestaurant().getTranslations() != null &&
                                !shift.getRestaurant().getTranslations().isEmpty()) {
                            String userLanguage = userLocale.getLanguage();
                            restaurantName = shift.getRestaurant().getTranslations().stream()
                                    .filter(t -> t.getLanguageCode() != null && t.getLanguageCode().startsWith(userLanguage))
                                    .findFirst()
                                    .map(com.gulfnet.shared_library.entity.RestaurantTranslation::getName)
                                    .orElse(shift.getRestaurant().getTranslations().get(0).getName());
                        } else if (shift.getRestaurant() != null) {
                            restaurantName = "Restaurant";
                        }

                        // Raised by = cashier
                        String raisedByName = shift.getCashier() != null
                                ? (shift.getCashier().getFirstName() + " " + shift.getCashier().getLastName()).trim()
                                : "Unknown";

                        // Get role of the cashier
                        String requesterRole = null;
                        if (shift.getCashier() != null && shift.getCashier().getRoleId() != null) {
                            var role = roleRepository.findById(shift.getCashier().getRoleId()).orElse(null);
                            if (role != null) {
                                requesterRole = role.getName();
                            }
                        }

                        // Map ShiftStatus to RequestStatus for UI
                        RequestStatus mappedStatus;
                        switch (shift.getStatus()) {
                            case PENDING_APPROVAL:
                                mappedStatus = RequestStatus.OPEN;
                                break;
                            case APPROVED:
                                mappedStatus = RequestStatus.APPROVED;
                                break;
                            case REJECTED:
                                mappedStatus = RequestStatus.DECLINED;
                                break;
                            default:
                                mappedStatus = RequestStatus.NONE;
                                break;
                        }

                        return RequestListItemResponse.builder()
                                .requestId(shift.getId())
                                .requestType(messageUtil.getMessage("request.type.shift.discrepancy", userLocale))
                                .raisedBy(raisedByName)
                                .role(requesterRole)
                                .restaurant(restaurantName)
                                .requestDate(shift.getClosedAt() != null ? shift.getClosedAt().toLocalDateTime() : (shift.getStartedAt() != null ? shift.getStartedAt().toLocalDateTime() : null))
                                .status(mappedStatus)
                                .entityId(shift.getId())
                                .build();
                    })
                    .collect(Collectors.toList());
        }
        
        // Get ALL additional discount requests (without pagination)
        // MANAGER and CASHIER can view additional discount requests
        // MANAGER and CASHIER see only requests from their restaurant
        // HQ_ADMIN does NOT see additional discount requests
        List<RequestListItemResponse> additionalDiscountListItems = new ArrayList<>();
        if ("MANAGER".equals(userRole) || "CASHIER".equals(userRole)) {
            // If status is null, show all requests (OPEN, APPROVED, DECLINED) - not just OPEN
            Pageable allRecordsPageable = PageRequest.of(0, Integer.MAX_VALUE, Sort.by(Sort.Direction.DESC, "additionalDiscountRequestedAt"));
            Page<Order> allOrdersPage = orderRepository.findByAdditionalDiscountRequestStatusOptional(status, allRecordsPageable);
            additionalDiscountListItems = allOrdersPage.getContent().stream()
                .filter(order -> {
                    // For CASHIER role, only show requests they sent
                    if ("CASHIER".equals(userRole) && finalCashierUserId != null) {
                        return order.getAdditionalDiscountRequestedBy() != null && 
                               order.getAdditionalDiscountRequestedBy().getId().equals(finalCashierUserId);
                    }
                    // Filter by restaurant for MANAGER role
                    if (finalManagerRestaurantId != null) {
                        return order.getRestaurant() != null && order.getRestaurant().getId().equals(finalManagerRestaurantId);
                    }
                    return true;
                })
                .map(order -> {
                    String restaurantName = null;
                    if (order.getRestaurant() != null && order.getRestaurant().getTranslations() != null && !order.getRestaurant().getTranslations().isEmpty()) {
                        // Try to get translation matching user locale, fallback to first available
                        String userLanguage = userLocale.getLanguage();
                        restaurantName = order.getRestaurant().getTranslations().stream()
                                .filter(t -> t.getLanguageCode() != null && t.getLanguageCode().startsWith(userLanguage))
                                .findFirst()
                                .map(com.gulfnet.shared_library.entity.RestaurantTranslation::getName)
                                .orElse(order.getRestaurant().getTranslations().get(0).getName());
                    } else if (order.getRestaurant() != null) {
                        restaurantName = "Restaurant";
                    }
                    
                    String raisedByName = order.getAdditionalDiscountRequestedBy() != null
                            ? order.getAdditionalDiscountRequestedBy().getFirstName() + " " + order.getAdditionalDiscountRequestedBy().getLastName()
                            : "Unknown";
                    
                    // Get role of the requester
                    String requesterRole = null;
                    if (order.getAdditionalDiscountRequestedBy() != null && order.getAdditionalDiscountRequestedBy().getRoleId() != null) {
                        var role = roleRepository.findById(order.getAdditionalDiscountRequestedBy().getRoleId()).orElse(null);
                        if (role != null) {
                            requesterRole = role.getName();
                        }
                    }
                    
                    return RequestListItemResponse.builder()
                            .requestId(order.getId())
                            .requestType(messageUtil.getMessage("request.type.additional.discount", userLocale))
                            .raisedBy(raisedByName)
                            .role(requesterRole)
                            .restaurant(restaurantName)
                            .requestDate(order.getAdditionalDiscountRequestedAt() != null ? order.getAdditionalDiscountRequestedAt().toLocalDateTime() : null)
                            .status(order.getAdditionalDiscountRequestStatus())
                            .entityId(order.getId())
                            .build();
                })
                .collect(Collectors.toList());
        }
        
        // Get ALL table/section requests (without pagination)
        // Only HQ_ADMIN can approve table/section requests, so only HQ_ADMIN sees them
        List<RequestListItemResponse> tableSectionListItems = new ArrayList<>();
        if ("HQ_ADMIN".equals(userRole)) {
            Pageable tableSectionPageable = PageRequest.of(0, Integer.MAX_VALUE, Sort.by(Sort.Direction.DESC, "tableSectionRequestedAt"));
            
            // Get ALL table requests
            Page<RestaurantTable> tablesPage = restaurantTableRepository.findByTableSectionRequestStatusOptional(status, tableSectionPageable);
            List<RequestListItemResponse> tableListItems = tablesPage.getContent().stream()
                    .map(table -> {
                        String restaurantName = null;
                        if (table.getRestaurantRow() != null && 
                            table.getRestaurantRow().getRestaurantSection() != null &&
                            table.getRestaurantRow().getRestaurantSection().getRestaurantLayout() != null &&
                            table.getRestaurantRow().getRestaurantSection().getRestaurantLayout().getRestaurant() != null) {
                            Restaurant restaurant = table.getRestaurantRow().getRestaurantSection().getRestaurantLayout().getRestaurant();
                            if (restaurant.getTranslations() != null && !restaurant.getTranslations().isEmpty()) {
                                String userLanguage = userLocale.getLanguage();
                                restaurantName = restaurant.getTranslations().stream()
                                        .filter(t -> t.getLanguageCode() != null && t.getLanguageCode().startsWith(userLanguage))
                                        .findFirst()
                                        .map(com.gulfnet.shared_library.entity.RestaurantTranslation::getName)
                                        .orElse(restaurant.getTranslations().get(0).getName());
                            } else {
                                restaurantName = "Restaurant";
                            }
                        }
                        
                        String raisedByName = table.getTableSectionRequestedBy() != null
                                ? table.getTableSectionRequestedBy().getFirstName() + " " + table.getTableSectionRequestedBy().getLastName()
                                : "Unknown";
                        
                        // Get role of the requester
                        String requesterRole = null;
                        if (table.getTableSectionRequestedBy() != null && table.getTableSectionRequestedBy().getRoleId() != null) {
                            var role = roleRepository.findById(table.getTableSectionRequestedBy().getRoleId()).orElse(null);
                            if (role != null) {
                                requesterRole = role.getName();
                            }
                        }
                        
                        return RequestListItemResponse.builder()
                                .requestId(table.getId())
                                .requestType(messageUtil.getMessage("request.type.table.section", userLocale))
                                .raisedBy(raisedByName)
                                .role(requesterRole)
                                .restaurant(restaurantName)
                                .requestDate(table.getTableSectionRequestedAt() != null ? table.getTableSectionRequestedAt().toLocalDateTime() : null)
                                .status(table.getTableSectionRequestStatus())
                                .entityId(table.getId())
                                .build();
                    })
                    .collect(Collectors.toList());
            
            // Get ALL section requests
            Page<RestaurantSection> sectionsPage = restaurantSectionRepository.findByTableSectionRequestStatusOptional(status, tableSectionPageable);
            List<RequestListItemResponse> sectionListItems = sectionsPage.getContent().stream()
                    .map(section -> {
                        String restaurantName = null;
                        if (section.getRestaurantLayout() != null && section.getRestaurantLayout().getRestaurant() != null) {
                            Restaurant restaurant = section.getRestaurantLayout().getRestaurant();
                            if (restaurant.getTranslations() != null && !restaurant.getTranslations().isEmpty()) {
                                String userLanguage = userLocale.getLanguage();
                                restaurantName = restaurant.getTranslations().stream()
                                        .filter(t -> t.getLanguageCode() != null && t.getLanguageCode().startsWith(userLanguage))
                                        .findFirst()
                                        .map(com.gulfnet.shared_library.entity.RestaurantTranslation::getName)
                                        .orElse(restaurant.getTranslations().get(0).getName());
                            } else {
                                restaurantName = "Restaurant";
                            }
                        }
                        
                        String raisedByName = section.getTableSectionRequestedBy() != null
                                ? section.getTableSectionRequestedBy().getFirstName() + " " + section.getTableSectionRequestedBy().getLastName()
                                : "Unknown";
                        
                        // Get role of the requester
                        String requesterRole = null;
                        if (section.getTableSectionRequestedBy() != null && section.getTableSectionRequestedBy().getRoleId() != null) {
                            var role = roleRepository.findById(section.getTableSectionRequestedBy().getRoleId()).orElse(null);
                            if (role != null) {
                                requesterRole = role.getName();
                            }
                        }
                        
                        return RequestListItemResponse.builder()
                                .requestId(section.getId())
                                .requestType(messageUtil.getMessage("request.type.table.section", userLocale))
                                .raisedBy(raisedByName)
                                .role(requesterRole)
                                .restaurant(restaurantName)
                                .requestDate(section.getTableSectionRequestedAt() != null ? section.getTableSectionRequestedAt().toLocalDateTime() : null)
                                .status(section.getTableSectionRequestStatus())
                                .entityId(section.getId())
                                .build();
                    })
                    .collect(Collectors.toList());
            
            tableSectionListItems.addAll(tableListItems);
            tableSectionListItems.addAll(sectionListItems);
        }
        
        // Get ALL refund requests (without pagination)
        // MANAGER and CASHIER can view refund requests
        // MANAGER and CASHIER see only requests from their restaurant
        // HQ_ADMIN does NOT see refund requests
        List<RequestListItemResponse> refundListItems = new ArrayList<>();
        final ObjectMapper requestDataObjectMapper = new ObjectMapper();
        if ("MANAGER".equals(userRole) || "CASHIER".equals(userRole)) {
            Pageable refundPageable = PageRequest.of(0, Integer.MAX_VALUE, Sort.by(Sort.Direction.DESC, "requestedAt"));
            // Find transactions with refund requests (requestStatus = OPEN and requestData contains "requestType":"REFUND")
            Page<Transaction> allTransactionsPage = transactionRepository.findByRequestStatusOptional(
                    status, refundPageable);
            refundListItems = allTransactionsPage.getContent().stream()
                .filter(transaction -> {
                    // For CASHIER role, only show requests they sent
                    if ("CASHIER".equals(userRole) && finalCashierUserId != null) {
                        return transaction.getRequestedBy() != null && 
                               transaction.getRequestedBy().getId().equals(finalCashierUserId);
                    }
                    // Filter by restaurant for MANAGER role
                    if (finalManagerRestaurantId != null) {
                        return transaction.getRestaurant() != null && transaction.getRestaurant().getId().equals(finalManagerRestaurantId);
                    }
                    return true;
                })
                .filter(transaction -> {
                    // Only include transactions with refund requests
                    if (transaction.getRequestData() == null) {
                        return false;
                    }
                    try {
                        Map<String, Object> requestData = requestDataObjectMapper.readValue(transaction.getRequestData(), Map.class);
                        return "REFUND".equals(requestData.get("requestType"));
                    } catch (JsonProcessingException e) {
                        return false;
                    }
                })
                .map(transaction -> {
                    String restaurantName = null;
                    if (transaction.getRestaurant() != null && transaction.getRestaurant().getTranslations() != null && !transaction.getRestaurant().getTranslations().isEmpty()) {
                        String userLanguage = userLocale.getLanguage();
                        restaurantName = transaction.getRestaurant().getTranslations().stream()
                                .filter(t -> t.getLanguageCode() != null && t.getLanguageCode().startsWith(userLanguage))
                                .findFirst()
                                .map(com.gulfnet.shared_library.entity.RestaurantTranslation::getName)
                                .orElse(transaction.getRestaurant().getTranslations().get(0).getName());
                    } else if (transaction.getRestaurant() != null) {
                        restaurantName = messageUtil.getMessage("refund.restaurant.default", userLocale);
                    }
                    
                    String raisedByName = transaction.getRequestedBy() != null
                            ? transaction.getRequestedBy().getFirstName() + " " + transaction.getRequestedBy().getLastName()
                            : "Unknown";
                    
                    // Get role of the requester
                    String requesterRole = null;
                    if (transaction.getRequestedBy() != null && transaction.getRequestedBy().getRoleId() != null) {
                        var role = roleRepository.findById(transaction.getRequestedBy().getRoleId()).orElse(null);
                        if (role != null) {
                            requesterRole = role.getName();
                        }
                    }
                    
                    // Get refund ID if refund entity exists (created after manager approval)
                    UUID refundId = null;
                    Optional<Refund> refundOpt = refundRepository.findByTransactionId(transaction.getId());
                    if (refundOpt.isPresent()) {
                        refundId = refundOpt.get().getId();
                    }
                    
                    return RequestListItemResponse.builder()
                            .requestId(transaction.getId())
                            .requestType(messageUtil.getMessage("request.type.refund", userLocale))
                            .raisedBy(raisedByName)
                            .role(requesterRole)
                            .restaurant(restaurantName)
                            .requestDate(transaction.getRequestedAt() != null ? transaction.getRequestedAt().toLocalDateTime() : null)
                            .status(transaction.getRequestStatus())
                            .entityId(transaction.getId())
                            .refundId(refundId)
                            .transactionNumber(transaction.getTransactionNumber())
                            .transactionStatus(transaction.getTransactionStatus())
                            .build();
                })
                .collect(Collectors.toList());
        }
        
        // Get ALL item cancellation requests (without pagination)
        // MANAGER and CASHIER can view item cancellation requests
        // MANAGER and CASHIER see only requests from their restaurant
        // HQ_ADMIN does NOT see item cancellation requests
        List<RequestListItemResponse> itemCancellationListItems = new ArrayList<>();
        if ("MANAGER".equals(userRole) || "CASHIER".equals(userRole)) {
            Pageable itemCancellationPageable = PageRequest.of(0, Integer.MAX_VALUE, Sort.by(Sort.Direction.DESC, "cancellationRequestedAt"));
            Page<OrderedItem> allOrderedItemsPage = orderedItemRepository.findByCancellationRequestStatusOptional(status, itemCancellationPageable);
            itemCancellationListItems = allOrderedItemsPage.getContent().stream()
                .filter(orderedItem -> {
                    // For CASHIER role, only show requests they sent
                    if ("CASHIER".equals(userRole) && finalCashierUserId != null) {
                        return orderedItem.getCancellationRequestedBy() != null && 
                               orderedItem.getCancellationRequestedBy().getId().equals(finalCashierUserId);
                    }
                    // Filter by restaurant for MANAGER role
                    if (finalManagerRestaurantId != null) {
                        return orderedItem.getOrder() != null && orderedItem.getOrder().getRestaurant() != null && orderedItem.getOrder().getRestaurant().getId().equals(finalManagerRestaurantId);
                    }
                    return true;
                })
                .map(orderedItem -> {
                    String restaurantName = null;
                    if (orderedItem.getOrder() != null && orderedItem.getOrder().getRestaurant() != null && 
                        orderedItem.getOrder().getRestaurant().getTranslations() != null && 
                        !orderedItem.getOrder().getRestaurant().getTranslations().isEmpty()) {
                        String userLanguage = userLocale.getLanguage();
                        restaurantName = orderedItem.getOrder().getRestaurant().getTranslations().stream()
                                .filter(t -> t.getLanguageCode() != null && t.getLanguageCode().startsWith(userLanguage))
                                .findFirst()
                                .map(com.gulfnet.shared_library.entity.RestaurantTranslation::getName)
                                .orElse(orderedItem.getOrder().getRestaurant().getTranslations().get(0).getName());
                    } else if (orderedItem.getOrder() != null && orderedItem.getOrder().getRestaurant() != null) {
                        restaurantName = "Restaurant";
                    }
                    
                    String raisedByName = orderedItem.getCancellationRequestedBy() != null
                            ? orderedItem.getCancellationRequestedBy().getFirstName() + " " + orderedItem.getCancellationRequestedBy().getLastName()
                            : "Unknown";
                    
                    // Get role of the requester
                    String requesterRole = null;
                    if (orderedItem.getCancellationRequestedBy() != null && orderedItem.getCancellationRequestedBy().getRoleId() != null) {
                        var role = roleRepository.findById(orderedItem.getCancellationRequestedBy().getRoleId()).orElse(null);
                        if (role != null) {
                            requesterRole = role.getName();
                        }
                    }
                    
                    return RequestListItemResponse.builder()
                            .requestId(orderedItem.getId())
                            .requestType(messageUtil.getMessage("request.type.item.cancellation", userLocale))
                            .raisedBy(raisedByName)
                            .role(requesterRole)
                            .restaurant(restaurantName)
                            .requestDate(orderedItem.getCancellationRequestedAt() != null ? orderedItem.getCancellationRequestedAt().toLocalDateTime() : null)
                            .status(orderedItem.getCancellationRequestStatus())
                            .entityId(orderedItem.getId())
                            .build();
                })
                .collect(Collectors.toList());
        }
        
        // Get ALL combo cancellation requests (without pagination)
        // MANAGER and CASHIER can view combo cancellation requests
        // MANAGER and CASHIER see only requests from their restaurant
        // HQ_ADMIN does NOT see combo cancellation requests
        List<RequestListItemResponse> comboCancellationListItems = new ArrayList<>();
        if ("MANAGER".equals(userRole) || "CASHIER".equals(userRole)) {
            Pageable comboCancellationPageable = PageRequest.of(0, Integer.MAX_VALUE, Sort.by(Sort.Direction.DESC, "cancellationRequestedAt"));
            Page<OrderedCombo> allOrderedCombosPage = orderedComboRepository.findByCancellationRequestStatusOptional(status, comboCancellationPageable);
            comboCancellationListItems = allOrderedCombosPage.getContent().stream()
                .filter(orderedCombo -> {
                    // For CASHIER role, only show requests they sent
                    if ("CASHIER".equals(userRole) && finalCashierUserId != null) {
                        return orderedCombo.getCancellationRequestedBy() != null && 
                               orderedCombo.getCancellationRequestedBy().getId().equals(finalCashierUserId);
                    }
                    // Filter by restaurant for MANAGER role
                    if (finalManagerRestaurantId != null) {
                        return orderedCombo.getOrder() != null && orderedCombo.getOrder().getRestaurant() != null && orderedCombo.getOrder().getRestaurant().getId().equals(finalManagerRestaurantId);
                    }
                    return true;
                })
                .map(orderedCombo -> {
                    String restaurantName = null;
                    if (orderedCombo.getOrder() != null && orderedCombo.getOrder().getRestaurant() != null && 
                        orderedCombo.getOrder().getRestaurant().getTranslations() != null && 
                        !orderedCombo.getOrder().getRestaurant().getTranslations().isEmpty()) {
                        String userLanguage = userLocale.getLanguage();
                        restaurantName = orderedCombo.getOrder().getRestaurant().getTranslations().stream()
                                .filter(t -> t.getLanguageCode() != null && t.getLanguageCode().startsWith(userLanguage))
                                .findFirst()
                                .map(com.gulfnet.shared_library.entity.RestaurantTranslation::getName)
                                .orElse(orderedCombo.getOrder().getRestaurant().getTranslations().get(0).getName());
                    } else if (orderedCombo.getOrder() != null && orderedCombo.getOrder().getRestaurant() != null) {
                        restaurantName = "Restaurant";
                    }
                    
                    String raisedByName = orderedCombo.getCancellationRequestedBy() != null
                            ? orderedCombo.getCancellationRequestedBy().getFirstName() + " " + orderedCombo.getCancellationRequestedBy().getLastName()
                            : "Unknown";
                    
                    // Get role of the requester
                    String requesterRole = null;
                    if (orderedCombo.getCancellationRequestedBy() != null && orderedCombo.getCancellationRequestedBy().getRoleId() != null) {
                        var role = roleRepository.findById(orderedCombo.getCancellationRequestedBy().getRoleId()).orElse(null);
                        if (role != null) {
                            requesterRole = role.getName();
                        }
                    }
                    
                    return RequestListItemResponse.builder()
                            .requestId(orderedCombo.getId())
                            .requestType(messageUtil.getMessage("request.type.item.cancellation", userLocale))
                            .raisedBy(raisedByName)
                            .role(requesterRole)
                            .restaurant(restaurantName)
                            .requestDate(orderedCombo.getCancellationRequestedAt() != null ? orderedCombo.getCancellationRequestedAt().toLocalDateTime() : null)
                            .status(orderedCombo.getCancellationRequestStatus())
                            .entityId(orderedCombo.getId())
                            .build();
                })
                .collect(Collectors.toList());
        }
        
        // Get ALL transaction cancellation requests (without pagination)
        // MANAGER and CASHIER can view transaction cancellation requests (not HQ_ADMIN)
        // MANAGER and CASHIER see only requests from their restaurant
        List<RequestListItemResponse> transactionCancellationListItems = new ArrayList<>();
        if ("MANAGER".equals(userRole) || "CASHIER".equals(userRole)) {
            Pageable transactionCancellationPageable = PageRequest.of(0, Integer.MAX_VALUE, Sort.by(Sort.Direction.DESC, "requestedAt"));
            Page<Transaction> allTransactionsPage = transactionRepository.findByRequestStatusOptional(status, transactionCancellationPageable);
            transactionCancellationListItems = allTransactionsPage.getContent().stream()
                .filter(transaction -> {
                    // Filter out refund requests - only include cancellation requests
                    // Refund requests have "requestType":"REFUND" in requestData
                    if (transaction.getRequestData() != null) {
                        try {
                            @SuppressWarnings("unchecked")
                            Map<String, Object> requestData = requestDataObjectMapper.readValue(transaction.getRequestData(), Map.class);
                            if ("REFUND".equals(requestData.get("requestType"))) {
                                return false; // This is a refund request, exclude it
                            }
                        } catch (JsonProcessingException e) {
                            // Invalid JSON, might be old format cancellation request, continue checking
                        }
                    }
                    // Only include transactions with request status (cancellation requests)
                    if (transaction.getRequestStatus() == RequestStatus.NONE) {
                        return false;
                    }
                    // For CASHIER role, only show requests they sent
                    if ("CASHIER".equals(userRole) && finalCashierUserId != null) {
                        return transaction.getRequestedBy() != null && 
                               transaction.getRequestedBy().getId().equals(finalCashierUserId);
                    }
                    // Filter by restaurant for MANAGER role
                    if (finalManagerRestaurantId != null) {
                        return transaction.getRestaurant() != null && transaction.getRestaurant().getId().equals(finalManagerRestaurantId);
                    }
                    return true;
                })
                .map(transaction -> {
                    String restaurantName = null;
                    if (transaction.getRestaurant() != null && transaction.getRestaurant().getTranslations() != null && 
                        !transaction.getRestaurant().getTranslations().isEmpty()) {
                        String userLanguage = userLocale.getLanguage();
                        restaurantName = transaction.getRestaurant().getTranslations().stream()
                                .filter(t -> t.getLanguageCode() != null && t.getLanguageCode().startsWith(userLanguage))
                                .findFirst()
                                .map(com.gulfnet.shared_library.entity.RestaurantTranslation::getName)
                                .orElse(transaction.getRestaurant().getTranslations().get(0).getName());
                    } else if (transaction.getRestaurant() != null) {
                        restaurantName = "Restaurant";
                    }
                    
                    String raisedByName = transaction.getRequestedBy() != null
                            ? transaction.getRequestedBy().getFirstName() + " " + transaction.getRequestedBy().getLastName()
                            : "Unknown";
                    
                    // Get role of the requester
                    String requesterRole = null;
                    if (transaction.getRequestedBy() != null && transaction.getRequestedBy().getRoleId() != null) {
                        var role = roleRepository.findById(transaction.getRequestedBy().getRoleId()).orElse(null);
                        if (role != null) {
                            requesterRole = role.getName();
                        }
                    }
                    
                    return RequestListItemResponse.builder()
                            .requestId(transaction.getId())
                            .requestType(messageUtil.getMessage("request.type.transaction.cancellation", userLocale))
                            .raisedBy(raisedByName)
                            .role(requesterRole)
                            .restaurant(restaurantName)
                            .requestDate(transaction.getRequestedAt() != null ? transaction.getRequestedAt().toLocalDateTime() : null) // This should be set when cancellation request is created
                            .status(transaction.getRequestStatus())
                            .entityId(transaction.getId())
                            .build();
                })
                .collect(Collectors.toList());
        }
        
        // Get ALL order cancellation requests (without pagination)
        // MANAGER and CASHIER can view order cancellation requests
        // MANAGER and CASHIER see only requests from their restaurant
        // HQ_ADMIN does NOT see order cancellation requests
        List<RequestListItemResponse> orderCancellationListItems = new ArrayList<>();
        if ("MANAGER".equals(userRole) || "CASHIER".equals(userRole)) {
            Pageable orderCancellationPageable = PageRequest.of(0, Integer.MAX_VALUE, Sort.by(Sort.Direction.DESC, "cancellationRequestedAt"));
            Page<Order> allOrdersPage = orderRepository.findByCancellationRequestStatusOptional(status, orderCancellationPageable);
            orderCancellationListItems = allOrdersPage.getContent().stream()
                .filter(order -> {
                    // For CASHIER role, only show requests they sent
                    if ("CASHIER".equals(userRole) && finalCashierUserId != null) {
                        return order.getCancellationRequestedBy() != null && 
                               order.getCancellationRequestedBy().getId().equals(finalCashierUserId);
                    }
                    // Filter by restaurant for MANAGER role
                    if (finalManagerRestaurantId != null) {
                        return order.getRestaurant() != null && order.getRestaurant().getId().equals(finalManagerRestaurantId);
                    }
                    return true;
                })
                .map(order -> {
                    String restaurantName = null;
                    if (order.getRestaurant() != null && order.getRestaurant().getTranslations() != null && 
                        !order.getRestaurant().getTranslations().isEmpty()) {
                        String userLanguage = userLocale.getLanguage();
                        restaurantName = order.getRestaurant().getTranslations().stream()
                                .filter(t -> t.getLanguageCode() != null && t.getLanguageCode().startsWith(userLanguage))
                                .findFirst()
                                .map(com.gulfnet.shared_library.entity.RestaurantTranslation::getName)
                                .orElse(order.getRestaurant().getTranslations().get(0).getName());
                    } else if (order.getRestaurant() != null) {
                        restaurantName = "Restaurant";
                    }
                    
                    String raisedByName = order.getCancellationRequestedBy() != null
                            ? order.getCancellationRequestedBy().getFirstName() + " " + order.getCancellationRequestedBy().getLastName()
                            : "Unknown";
                    
                    // Get role of the requester
                    String requesterRole = null;
                    if (order.getCancellationRequestedBy() != null && order.getCancellationRequestedBy().getRoleId() != null) {
                        var role = roleRepository.findById(order.getCancellationRequestedBy().getRoleId()).orElse(null);
                        if (role != null) {
                            requesterRole = role.getName();
                        }
                    }
                    
                    return RequestListItemResponse.builder()
                            .requestId(order.getId())
                            .requestType(messageUtil.getMessage("request.type.order.cancellation", userLocale))
                            .raisedBy(raisedByName)
                            .role(requesterRole)
                            .restaurant(restaurantName)
                            .requestDate(order.getCancellationRequestedAt() != null ? order.getCancellationRequestedAt().toLocalDateTime() : null)
                            .status(order.getCancellationRequestStatus())
                            .entityId(order.getId())
                            .build();
                })
                .collect(Collectors.toList());
        }
        
        // Combine all requests
        // For CASHIER and MANAGER roles, include all request types (except table/section which is HQ_ADMIN only)
        // For HQ_ADMIN, include profile updates and table/section requests
        List<RequestListItemResponse> allRequests = new ArrayList<>();
        if ("CASHIER".equals(userRole) || "MANAGER".equals(userRole)) {
            // Cashiers and Managers see all request types from their restaurant
            allRequests.addAll(profileUpdateListItems);
            allRequests.addAll(additionalDiscountListItems);
            allRequests.addAll(refundListItems);
            allRequests.addAll(itemCancellationListItems);
            allRequests.addAll(comboCancellationListItems);
            allRequests.addAll(transactionCancellationListItems);
            allRequests.addAll(orderCancellationListItems);
            allRequests.addAll(shiftDiscrepancyListItems);
        } else if ("HQ_ADMIN".equals(userRole)) {
            // HQ_ADMIN sees profile updates and table/section requests
            allRequests.addAll(profileUpdateListItems);
            allRequests.addAll(tableSectionListItems);
        }
        
        // Filter by request type if provided
        // Support both slug IDs (e.g., "profile-update") and display names (e.g., "Profile Update")
        if (requestType != null && !requestType.trim().isEmpty()) {
            String requestTypeInput = requestType.trim();
            String displayName = convertRequestTypeSlugToDisplayName(requestTypeInput, userLocale);
            final String filterValue = displayName != null ? displayName : requestTypeInput;
            
            allRequests = allRequests.stream()
                    .filter(req -> req.getRequestType() != null && 
                            req.getRequestType().equalsIgnoreCase(filterValue))
                    .collect(Collectors.toList());
        }
        
        // Sort requests
        String sortField = (sortBy != null && !sortBy.trim().isEmpty()) ? sortBy.trim().toLowerCase() : "date";
        String sortDir = (sortDirection != null && !sortDirection.trim().isEmpty()) ? sortDirection.trim().toUpperCase() : "DESC";
        boolean ascending = "ASC".equals(sortDir);
        
        Comparator<RequestListItemResponse> comparator;
        
        if ("date".equals(sortField) || "requestdate".equals(sortField)) {
            // Sort by request date with nulls last
            comparator = Comparator.comparing(
                RequestListItemResponse::getRequestDate,
                Comparator.nullsLast(Comparator.naturalOrder())
            );
        } else if ("type".equals(sortField) || "requesttype".equals(sortField)) {
            // Sort by request type
            comparator = Comparator.comparing(
                req -> req.getRequestType() != null ? req.getRequestType() : "",
                Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER)
            );
        } else if ("status".equals(sortField)) {
            // Sort by status
            comparator = Comparator.comparing(
                req -> req.getStatus() != null ? req.getStatus().name() : "",
                Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER)
            );
        } else {
            // Default: sort by date with nulls last
            comparator = Comparator.comparing(
                RequestListItemResponse::getRequestDate,
                Comparator.nullsLast(Comparator.naturalOrder())
            );
        }
        
        // Apply sort direction: reverse if descending (naturalOrder is ascending, so reverse for DESC)
        if (ascending) {
            // Keep natural order (ascending) - no reversal needed
        } else {
            // Reverse for descending order
            comparator = comparator.reversed();
        }
        
        allRequests.sort(comparator);
        
        // Apply pagination to combined list
        long totalRequests = allRequests.size();
        int fromIndex = pageNumber * pageSize;
        int toIndex = (int) Math.min(fromIndex + pageSize, totalRequests);
        List<RequestListItemResponse> paginatedRequests = fromIndex < totalRequests ? 
                allRequests.subList(fromIndex, toIndex) : new ArrayList<>();
        
        // Build pagination metadata
        PaginationMetaData metaData = PaginationMetaData.builder()
                .page(pageNumber + 1)
                .size(pageSize)
                .totalPages((int) Math.ceil((double) totalRequests / pageSize))
                .totalRecords(totalRequests)
                .build();
        
        UnifiedRequestListResponse unifiedResponse = UnifiedRequestListResponse.builder()
                .requests(paginatedRequests)
                .count((long) paginatedRequests.size())
                .total(totalRequests)
                .metaData(metaData)
                .build();
        
        return ResponseDto.<UnifiedRequestListResponse>builder()
                .message(messageUtil.getMessage("request.list.fetch.success", userLocale))
                .data(unifiedResponse)
                .build();
    }

    @Override
    public ResponseDto<RequestDetailsResponse> getRequestDetails(UUID requestId, String userRole, String userId) {
        Locale userLocale = LocaleContextHolder.getLocale();
        
        // Only MANAGER and HQ_ADMIN can view request details
        if (!"MANAGER".equals(userRole) && !"HQ_ADMIN".equals(userRole)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    messageUtil.getMessage("user.profile.update.request.unauthorized", userLocale));
        }
        
        // Try to find as profile update request first (check if it's a user with a request)
        Optional<User> userOptional = userRepository.findById(requestId);
        if (userOptional.isPresent()) {
            User user = userOptional.get();
            if (user.getProfileUpdateRequestStatus() != RequestStatus.NONE) {
                // This is a profile update request
            
            try {
                ObjectMapper objectMapper = new ObjectMapper();
                UserProfileUpdateRequest updateRequest = null;
                
                if (user.getProfileUpdateRequestData() != null) {
                    updateRequest = objectMapper.readValue(user.getProfileUpdateRequestData(), UserProfileUpdateRequest.class);
                }
                
                // For old vs new data comparison:
                // - For OPEN/PENDING: Old = current user data, New = requested data from requestData
                // - For APPROVED: Old = requested data (what was requested, now applied), New = current user data (approved data)
                // - For DECLINED: Old = current user data (unchanged), New = requested data (what was declined)
                
                String oldFirstName, oldLastName, oldEmail, oldContactNumber, oldPhotoUrl, oldLanguageCode;
                String newFirstName, newLastName, newEmail, newContactNumber, newPhotoUrl, newLanguageCode;
                
                String normalizedUserPhotoUrl = (user.getPhotoUrl() != null && !user.getPhotoUrl().isEmpty()) ? awsService.stripToKey(user.getPhotoUrl()) : null;
                if (user.getProfileUpdateRequestStatus() == RequestStatus.APPROVED) {
                    // For approved requests: Old = what was requested (from requestData), New = current user data (approved)
                    oldFirstName = updateRequest != null ? updateRequest.getFirstName() : user.getFirstName();
                    oldLastName = updateRequest != null ? updateRequest.getLastName() : user.getLastName();
                    oldEmail = updateRequest != null ? updateRequest.getEmail() : user.getEmail();
                    oldContactNumber = updateRequest != null ? updateRequest.getContactNumber() : user.getContactNumber();
                    oldPhotoUrl = updateRequest != null ? updateRequest.getPhotoUrl() : normalizedUserPhotoUrl;
                    oldLanguageCode = updateRequest != null ? updateRequest.getLanguageCode() : user.getLanguageCode();
                    
                    newFirstName = user.getFirstName();
                    newLastName = user.getLastName();
                    newEmail = user.getEmail();
                    newContactNumber = user.getContactNumber();
                    newPhotoUrl = normalizedUserPhotoUrl;
                    newLanguageCode = user.getLanguageCode();
                } else {
                    // For OPEN/PENDING/DECLINED: Old = current user data, New = requested data
                    oldFirstName = user.getFirstName();
                    oldLastName = user.getLastName();
                    oldEmail = user.getEmail();
                    oldContactNumber = user.getContactNumber();
                    oldPhotoUrl = normalizedUserPhotoUrl;
                    oldLanguageCode = user.getLanguageCode();
                    
                    newFirstName = updateRequest != null ? updateRequest.getFirstName() : null;
                    newLastName = updateRequest != null ? updateRequest.getLastName() : null;
                    newEmail = updateRequest != null ? updateRequest.getEmail() : null;
                    newContactNumber = updateRequest != null ? updateRequest.getContactNumber() : null;
                    newPhotoUrl = updateRequest != null ? updateRequest.getPhotoUrl() : null;
                    newLanguageCode = updateRequest != null ? updateRequest.getLanguageCode() : null;
                }
                
                boolean firstNameChanged = updateRequest != null && !Objects.equals(oldFirstName, newFirstName);
                boolean lastNameChanged = updateRequest != null && !Objects.equals(oldLastName, newLastName);
                boolean emailChanged = updateRequest != null && !Objects.equals(oldEmail, newEmail);
                boolean contactNumberChanged = updateRequest != null && !Objects.equals(oldContactNumber, newContactNumber);
                boolean photoUrlChanged = updateRequest != null && !Objects.equals(oldPhotoUrl, newPhotoUrl);
                boolean languageCodeChanged = updateRequest != null && !Objects.equals(oldLanguageCode, newLanguageCode);
                
                // Get role name for requestedBy user (for profile updates, updatedBy is the requester)
                String requestedByRole = null;
                User requester = user.getUpdatedBy() != null ? user.getUpdatedBy() : user;
                if (requester != null && requester.getRoleId() != null) {
                    var role = roleRepository.findById(requester.getRoleId()).orElse(null);
                    if (role != null) {
                        requestedByRole = role.getName();
                    }
                }
                
                // Extract reason from requestData if available (for future use)
                String reason = null;
                try {
                    if (user.getProfileUpdateRequestData() != null && updateRequest != null) {
                        // Check if there's a reason field in the request data
                        // For now, reason is not stored in UserProfileUpdateRequest, so it will be null
                        // This field is added for future extensibility
                        reason = null;
                    }
                } catch (Exception e) {
                    log.warn("Error extracting reason from profile update request data: {}", e.getMessage());
                }
                
                // Get restaurant name for profile update request
                String restaurantName = null;
                if (user.getRestaurantId() != null) {
                    Optional<Restaurant> restaurantOpt = restaurantRepository.findById(user.getRestaurantId());
                    if (restaurantOpt.isPresent()) {
                        Restaurant restaurant = restaurantOpt.get();
                        if (restaurant.getTranslations() != null && !restaurant.getTranslations().isEmpty()) {
                            String userLanguage = userLocale.getLanguage();
                            restaurantName = restaurant.getTranslations().stream()
                                    .filter(t -> t.getLanguageCode() != null && t.getLanguageCode().startsWith(userLanguage))
                                    .findFirst()
                                    .map(com.gulfnet.shared_library.entity.RestaurantTranslation::getName)
                                    .orElse(restaurant.getTranslations().get(0).getName());
                        } else {
                            restaurantName = "Restaurant";
                        }
                    }
                }
                
                ProfileUpdateRequestWithComparisonResponse profileDetails = ProfileUpdateRequestWithComparisonResponse.builder()
                        .userId(user.getId())
                        .firstName(user.getFirstName())
                        .lastName(user.getLastName())
                        .email(user.getEmail())
                        .userCode(user.getUserCode())
                        .status(user.getProfileUpdateRequestStatus())
                        .requestData(user.getProfileUpdateRequestData())
                        .requestedAt(user.getProfileUpdateRequestedAt() != null ? user.getProfileUpdateRequestedAt().toLocalDateTime() : null)
                        .requestedBy(requester != null ? requester.getId() : null)
                        .requestedByName(requester != null ? 
                                requester.getFirstName() + " " + requester.getLastName() : null)
                        .requestedByRole(requestedByRole)
                        .reviewedAt(user.getProfileUpdateReviewedAt() != null ? user.getProfileUpdateReviewedAt().toLocalDateTime() : null)
                        .reviewedBy(user.getUpdatedBy() != null ? user.getUpdatedBy().getId() : null)
                        .reviewedByName(user.getUpdatedBy() != null ? 
                                user.getUpdatedBy().getFirstName() + " " + user.getUpdatedBy().getLastName() : null)
                        .comments(null) // Comments are only available when approving/declining, not stored in User entity
                        .reason(reason)
                        .restaurantName(restaurantName)
                        .oldFirstName(oldFirstName)
                        .oldLastName(oldLastName)
                        .oldEmail(oldEmail)
                        .oldContactNumber(oldContactNumber)
                        .oldPhotoUrl(oldPhotoUrl)
                        .oldLanguageCode(oldLanguageCode)
                        .newFirstName(newFirstName)
                        .newLastName(newLastName)
                        .newEmail(newEmail)
                        .newContactNumber(newContactNumber)
                        .newPhotoUrl(newPhotoUrl)
                        .newLanguageCode(newLanguageCode)
                        .firstNameChanged(firstNameChanged)
                        .lastNameChanged(lastNameChanged)
                        .emailChanged(emailChanged)
                        .contactNumberChanged(contactNumberChanged)
                        .photoUrlChanged(photoUrlChanged)
                        .languageCodeChanged(languageCodeChanged)
                        .build();
                
                RequestDetailsResponse response = RequestDetailsResponse.builder()
                        .requestType(messageUtil.getMessage("request.type.profile.update", userLocale))
                        .restaurantName(restaurantName)
                        .profileUpdateDetails(profileDetails)
                        .additionalDiscountDetails(null)
                        .build();
                
                return ResponseDto.<RequestDetailsResponse>builder()
                        .message(messageUtil.getMessage("user.profile.update.requests.retrieved", userLocale))
                        .data(response)
                        .build();
                        
            } catch (JsonProcessingException e) {
                log.error("Error parsing request data for user {}: {}", requestId, e.getMessage());
                throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                        messageUtil.getMessage("user.profile.update.request.error", userLocale));
            }
            }
        }
        
        // Try to find as additional discount or cancellation request (check if it's an order with a request)
        Optional<Order> orderOptional = orderRepository.findById(requestId);
        if (orderOptional.isPresent()) {
            Order order = orderOptional.get();
            
            // Check if both requests exist - if so, use requestType flag to determine which one
            boolean hasAdditionalDiscountRequest = order.getAdditionalDiscountRequestStatus() != RequestStatus.NONE;
            boolean hasCancellationRequest = order.getCancellationRequestStatus() != RequestStatus.NONE;
            
            // Determine which request to return based on requestType flag when both exist
            String additionalDiscountRequestType = getRequestTypeFromData(order.getAdditionalDiscountRequestData());
            String cancellationRequestType = getRequestTypeFromData(order.getCancellationRequestData());
            
            // If both requests exist, prioritize based on requestType flag
            // If requestType is not set (backward compatibility), default to additional discount first
            boolean shouldReturnAdditionalDiscount = hasAdditionalDiscountRequest && 
                (!hasCancellationRequest || 
                 "ADDITIONAL_DISCOUNT".equals(additionalDiscountRequestType) ||
                 (additionalDiscountRequestType == null && cancellationRequestType == null)); // backward compatibility
            
            if (shouldReturnAdditionalDiscount) {
                // This is an additional discount request
                // Only MANAGER can view additional discount request details (HQ_ADMIN cannot)
                if (!"MANAGER".equals(userRole)) {
                    throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                            messageUtil.getMessage("additional.discount.request.unauthorized.role", userLocale));
                }
                
                // MANAGER can only view requests from their own restaurant
                // Get manager's restaurant ID if user is a MANAGER
                UUID managerRestaurantId = null;
                if (userId != null && !userId.trim().isEmpty() && !"null".equalsIgnoreCase(userId)) {
                    try {
                        Optional<User> managerOpt = userRepository.findById(UUID.fromString(userId));
                        if (managerOpt.isPresent() && managerOpt.get().getRestaurantId() != null) {
                            managerRestaurantId = managerOpt.get().getRestaurantId();
                        }
                    } catch (IllegalArgumentException e) {
                        log.warn("Invalid userId format: {}", userId);
                    }
                }
                
                if (managerRestaurantId != null && order.getRestaurant() != null) {
                    if (!managerRestaurantId.equals(order.getRestaurant().getId())) {
                        throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                                messageUtil.getMessage("additional.discount.request.unauthorized.restaurant", userLocale));
                    }
                }
                
                // Get role name for requestedBy user
                String requestedByRole = null;
                if (order.getAdditionalDiscountRequestedBy() != null && order.getAdditionalDiscountRequestedBy().getRoleId() != null) {
                    var role = roleRepository.findById(order.getAdditionalDiscountRequestedBy().getRoleId()).orElse(null);
                    if (role != null) {
                        requestedByRole = role.getName();
                    }
                }
                
                // Get restaurant name
                String restaurantName = null;
                UUID restaurantId = null;
                if (order.getRestaurant() != null) {
                    restaurantId = order.getRestaurant().getId();
                    if (order.getRestaurant().getTranslations() != null && !order.getRestaurant().getTranslations().isEmpty()) {
                        String userLanguage = userLocale.getLanguage();
                        restaurantName = order.getRestaurant().getTranslations().stream()
                                .filter(t -> t.getLanguageCode() != null && t.getLanguageCode().startsWith(userLanguage))
                                .findFirst()
                                .map(com.gulfnet.shared_library.entity.RestaurantTranslation::getName)
                                .orElse(order.getRestaurant().getTranslations().get(0).getName());
                    } else {
                        restaurantName = "Restaurant";
                    }
                }
                
                // Calculate additionalDiscountAmount if request is OPEN (pending approval)
                BigDecimal calculatedAdditionalDiscountAmount = order.getAdditionalDiscountAmount();
                if (order.getAdditionalDiscountRequestStatus() == RequestStatus.OPEN) {
                    // Request is OPEN (pending approval) - calculate discount amount for preview
                    if (order.getAdditionalDiscountType() != null && order.getAdditionalDiscountValue() != null) {
                        // Calculate subtotal after discount: subTotal - discountAmount
                        BigDecimal subtotalAfterDiscount = order.getSubTotal() != null ? order.getSubTotal() : BigDecimal.ZERO;
                        if (order.getDiscountAmount() != null) {
                            subtotalAfterDiscount = subtotalAfterDiscount.subtract(order.getDiscountAmount());
                            if (subtotalAfterDiscount.compareTo(BigDecimal.ZERO) < 0) {
                                subtotalAfterDiscount = BigDecimal.ZERO;
                            }
                        }
                        // Calculate total before additional discount: subtotal + tax + service charge + packing charge
                        BigDecimal taxAmount = order.getTaxAmount() != null ? order.getTaxAmount() : BigDecimal.ZERO;
                        BigDecimal serviceChargeAmount = order.getServiceChargeAmount() != null ? order.getServiceChargeAmount() : BigDecimal.ZERO;
                        BigDecimal packingChargeAmount = order.getPackingChargeAmount() != null ? order.getPackingChargeAmount() : BigDecimal.ZERO;
                        BigDecimal totalBeforeAdditionalDiscount = subtotalAfterDiscount.add(taxAmount).add(serviceChargeAmount).add(packingChargeAmount);
                        // Calculate discount amount based on type
                        if (totalBeforeAdditionalDiscount.compareTo(BigDecimal.ZERO) > 0) {
                            if (order.getAdditionalDiscountType() == DiscountType.PERCENT) {
                                calculatedAdditionalDiscountAmount = totalBeforeAdditionalDiscount.multiply(order.getAdditionalDiscountValue())
                                        .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
                            } else if (order.getAdditionalDiscountType() == DiscountType.FLAT) {
                                calculatedAdditionalDiscountAmount = order.getAdditionalDiscountValue();
                            }
                        }
                    }
                }
                
                AdditionalDiscountRequestResponse discountDetails = AdditionalDiscountRequestResponse.builder()
                        .orderId(order.getId())
                        .orderNumber(order.getOrderNumber())
                        .orderTotalAmount(order.getTotalAmount() != null ? order.getTotalAmount() : BigDecimal.ZERO)
                        .additionalDiscountType(order.getAdditionalDiscountType())
                        .additionalDiscountValue(order.getAdditionalDiscountValue())
                        .additionalDiscountAmount(calculatedAdditionalDiscountAmount)
                        .additionalDiscountReason(order.getAdditionalDiscountReason())
                        .requestStatus(order.getAdditionalDiscountRequestStatus())
                        .requestedAt(order.getAdditionalDiscountRequestedAt() != null ? order.getAdditionalDiscountRequestedAt().toLocalDateTime() : null)
                        .requestedBy(order.getAdditionalDiscountRequestedBy() != null ? order.getAdditionalDiscountRequestedBy().getId() : null)
                        .requestedByName(order.getAdditionalDiscountRequestedBy() != null ? 
                            order.getAdditionalDiscountRequestedBy().getFirstName() + " " + order.getAdditionalDiscountRequestedBy().getLastName() : null)
                        .requestedByRole(requestedByRole)
                        .reviewedAt(order.getAdditionalDiscountReviewedAt() != null ? ((OffsetDateTime) order.getAdditionalDiscountReviewedAt()).toLocalDateTime() : null)
                        .reviewedBy(order.getAdditionalDiscountReviewedBy() != null ? order.getAdditionalDiscountReviewedBy().getId() : null)
                        .reviewedByName(order.getAdditionalDiscountReviewedBy() != null ? 
                            order.getAdditionalDiscountReviewedBy().getFirstName() + " " + order.getAdditionalDiscountReviewedBy().getLastName() : null)
                        .comments(order.getAdditionalDiscountRequestComments())
                        .restaurantId(restaurantId)
                        .restaurantName(restaurantName)
                        .build();
                
                RequestDetailsResponse response = RequestDetailsResponse.builder()
                        .requestType(messageUtil.getMessage("request.type.additional.discount", userLocale))
                        .restaurantName(restaurantName)
                        .profileUpdateDetails(null)
                        .additionalDiscountDetails(discountDetails)
                        .build();
                
                return ResponseDto.<RequestDetailsResponse>builder()
                        .message(messageUtil.getMessage("additional.discount.requests.retrieved", userLocale))
                        .data(response)
                        .build();
            }
            
            // Check if it's an order cancellation request
            if (hasCancellationRequest && (!hasAdditionalDiscountRequest || "ORDER_CANCELLATION".equals(cancellationRequestType))) {
                // This is an order cancellation request
                // Only MANAGER and HQ_ADMIN can view order cancellation request details
                if (!"MANAGER".equals(userRole) && !"HQ_ADMIN".equals(userRole)) {
                    throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                            messageUtil.getMessage("user.profile.update.request.unauthorized", userLocale));
                }
                
                // MANAGER can only view requests from their own restaurant
                UUID managerRestaurantId = null;
                if ("MANAGER".equals(userRole) && userId != null && !userId.trim().isEmpty() && !"null".equalsIgnoreCase(userId)) {
                    try {
                        Optional<User> managerOpt = userRepository.findById(UUID.fromString(userId));
                        if (managerOpt.isPresent() && managerOpt.get().getRestaurantId() != null) {
                            managerRestaurantId = managerOpt.get().getRestaurantId();
                        }
                    } catch (IllegalArgumentException e) {
                        log.warn("Invalid userId format: {}", userId);
                    }
                }
                
                if (managerRestaurantId != null && order.getRestaurant() != null) {
                    if (!managerRestaurantId.equals(order.getRestaurant().getId())) {
                        throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                                messageUtil.getMessage("order.cancellation.request.unauthorized.restaurant", userLocale));
                    }
                }
                
                OrderCancellationRequestResponse orderCancellationDetails = buildOrderCancellationRequestResponse(order, userLocale);
                
                RequestDetailsResponse response = RequestDetailsResponse.builder()
                        .requestType(messageUtil.getMessage("request.type.order.cancellation", userLocale))
                        .restaurantName(orderCancellationDetails.getRestaurantName())
                        .profileUpdateDetails(null)
                        .additionalDiscountDetails(null)
                        .tableSectionDetails(null)
                        .refundDetails(null)
                        .itemCancellationDetails(null)
                        .comboCancellationDetails(null)
                        .transactionCancellationDetails(null)
                        .orderCancellationDetails(orderCancellationDetails)
                        .build();
                
                return ResponseDto.<RequestDetailsResponse>builder()
                        .message(messageUtil.getMessage("order.cancellation.requests.retrieved", userLocale))
                        .data(response)
                        .build();
            }
        }

        // Try to find as shift discrepancy request (cashier shift)
        Optional<CashierShift> shiftOptional = cashierShiftRepository.findById(requestId);
        if (shiftOptional.isPresent()) {
            // Only MANAGER can view shift discrepancy details
            if (!"MANAGER".equals(userRole)) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                        messageUtil.getMessage("additional.discount.request.unauthorized.role", userLocale));
            }

            CashierShift shift = shiftOptional.get();

            // MANAGER can only view requests from their own restaurant
            UUID managerRestaurantId = null;
            if (userId != null && !userId.trim().isEmpty() && !"null".equalsIgnoreCase(userId)) {
                try {
                    Optional<User> managerOpt = userRepository.findById(UUID.fromString(userId));
                    if (managerOpt.isPresent() && managerOpt.get().getRestaurantId() != null) {
                        managerRestaurantId = managerOpt.get().getRestaurantId();
                    }
                } catch (IllegalArgumentException e) {
                    log.warn("Invalid userId format: {}", userId);
                }
            }

            if (managerRestaurantId != null && shift.getRestaurant() != null) {
                if (!managerRestaurantId.equals(shift.getRestaurant().getId())) {
                    throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                            messageUtil.getMessage("additional.discount.request.unauthorized.restaurant", userLocale));
                }
            }

            // Restaurant name
            String restaurantName = null;
            if (shift.getRestaurant() != null &&
                    shift.getRestaurant().getTranslations() != null &&
                    !shift.getRestaurant().getTranslations().isEmpty()) {
                String userLanguage = userLocale.getLanguage();
                restaurantName = shift.getRestaurant().getTranslations().stream()
                        .filter(t -> t.getLanguageCode() != null && t.getLanguageCode().startsWith(userLanguage))
                        .findFirst()
                        .map(com.gulfnet.shared_library.entity.RestaurantTranslation::getName)
                        .orElse(shift.getRestaurant().getTranslations().get(0).getName());
            } else if (shift.getRestaurant() != null) {
                restaurantName = "Restaurant";
            }

            // Build CashierShiftResponse (similar to restaurant-management)
            CashierShiftResponse shiftDetails = CashierShiftResponse.builder()
                    .id(shift.getId())
                    .cashDrawerId(shift.getCashDrawer() != null ? shift.getCashDrawer().getId() : null)
                    .cashDrawerName(resolveCashDrawerNameForUserService(shift.getCashDrawer(), userLocale))
                    .cashierId(shift.getCashier() != null ? shift.getCashier().getId() : null)
                    .cashierName(shift.getCashier() != null
                            ? (shift.getCashier().getFirstName() + " " + shift.getCashier().getLastName()).trim()
                            : null)
                    .restaurantId(shift.getRestaurant() != null ? shift.getRestaurant().getId() : null)
                    .shiftId(shift.getShift() != null ? shift.getShift().getId() : null)
                    .shiftName(shift.getShift() != null ? getShiftNameFromShift(shift.getShift(), LocaleContextHolder.getLocale().getLanguage()) : null)
                    .status(shift.getStatus())
                    .openingBalance(shift.getOpeningBalance())
                    .closingBalance(shift.getClosingBalance())
                    .expectedClosingBalance(shift.getExpectedClosingBalance())
                    .discrepancyAmount(shift.getDiscrepancyAmount())
                    .discrepancyReason(shift.getDiscrepancyReason())
                    .startedAt(shift.getStartedAt() != null ? shift.getStartedAt().toLocalDateTime() : null)
                    .closedAt(shift.getClosedAt() != null ? shift.getClosedAt().toLocalDateTime() : null)
                    .approvedBy(shift.getApprovedBy() != null ? shift.getApprovedBy().getId() : null)
                    .approvedByName(shift.getApprovedBy() != null
                            ? (shift.getApprovedBy().getFirstName() + " " + shift.getApprovedBy().getLastName()).trim()
                            : null)
                    .approvedAt(shift.getApprovedAt() != null ? shift.getApprovedAt().toLocalDateTime() : null)
                    .createdAt(shift.getCreatedAt() != null ? shift.getCreatedAt().toLocalDateTime() : null)
                    .updatedAt(shift.getUpdatedAt() != null ? shift.getUpdatedAt().toLocalDateTime() : null)
                    .build();

            RequestDetailsResponse response = RequestDetailsResponse.builder()
                    .requestType(messageUtil.getMessage("request.type.shift.discrepancy", userLocale))
                    .restaurantName(restaurantName)
                    .shiftDiscrepancyDetails(shiftDetails)
                    .build();

            return ResponseDto.<RequestDetailsResponse>builder()
                    .message(messageUtil.getMessage("shift.discrepancy.requests.retrieved", userLocale))
                    .data(response)
                    .build();
        }
        
        // Try to find as table/section request (only HQ_ADMIN can view these)
        if ("HQ_ADMIN".equals(userRole)) {
            // Try table first
            Optional<RestaurantTable> tableOptional = restaurantTableRepository.findById(requestId);
            if (tableOptional.isPresent()) {
                RestaurantTable table = tableOptional.get();
                if (table.getTableSectionRequestStatus() != RequestStatus.NONE) {
                    String restaurantName = null;
                    UUID restaurantId = null;
                    if (table.getRestaurantRow() != null && 
                        table.getRestaurantRow().getRestaurantSection() != null &&
                        table.getRestaurantRow().getRestaurantSection().getRestaurantLayout() != null &&
                        table.getRestaurantRow().getRestaurantSection().getRestaurantLayout().getRestaurant() != null) {
                        Restaurant restaurant = table.getRestaurantRow().getRestaurantSection().getRestaurantLayout().getRestaurant();
                        restaurantId = restaurant.getId();
                        if (restaurant.getTranslations() != null && !restaurant.getTranslations().isEmpty()) {
                            String userLanguage = userLocale.getLanguage();
                            restaurantName = restaurant.getTranslations().stream()
                                    .filter(t -> t.getLanguageCode() != null && t.getLanguageCode().startsWith(userLanguage))
                                    .findFirst()
                                    .map(com.gulfnet.shared_library.entity.RestaurantTranslation::getName)
                                    .orElse(restaurant.getTranslations().get(0).getName());
                        } else {
                            restaurantName = "Restaurant";
                        }
                    }
                    
                    // Get role name for requestedBy user
                    String requestedByRole = null;
                    if (table.getTableSectionRequestedBy() != null && table.getTableSectionRequestedBy().getRoleId() != null) {
                        var role = roleRepository.findById(table.getTableSectionRequestedBy().getRoleId()).orElse(null);
                        if (role != null) {
                            requestedByRole = role.getName();
                        }
                    }
                    
                    TableSectionRequestResponse tableSectionDetails = TableSectionRequestResponse.builder()
                            .entityId(table.getId())
                            .entityType("Table")
                            .entityName("Table " + (table.getTableOrder() != null ? table.getTableOrder().toString() : ""))
                            .restaurantName(restaurantName)
                            .restaurantId(restaurantId)
                            .requestData(table.getTableSectionRequestData())
                            .requestStatus(table.getTableSectionRequestStatus())
                            .requestedAt(table.getTableSectionRequestedAt() != null ? table.getTableSectionRequestedAt().toLocalDateTime() : null)
                            .requestedBy(table.getTableSectionRequestedBy() != null ? table.getTableSectionRequestedBy().getId() : null)
                            .requestedByName(table.getTableSectionRequestedBy() != null ? 
                                table.getTableSectionRequestedBy().getFirstName() + " " + table.getTableSectionRequestedBy().getLastName() : null)
                            .requestedByRole(requestedByRole)
                            .reviewedAt(table.getTableSectionReviewedAt() != null ? table.getTableSectionReviewedAt().toLocalDateTime() : null)
                            .reviewedBy(table.getTableSectionReviewedBy() != null ? table.getTableSectionReviewedBy().getId() : null)
                            .reviewedByName(table.getTableSectionReviewedBy() != null ? 
                                table.getTableSectionReviewedBy().getFirstName() + " " + table.getTableSectionReviewedBy().getLastName() : null)
                            .comments(table.getTableSectionRequestComments())
                            .reason(table.getTableSectionRequestComments()) // Reason is stored in comments field
                            .build();
                    
                    RequestDetailsResponse response = RequestDetailsResponse.builder()
                            .requestType(messageUtil.getMessage("request.type.table.section", userLocale))
                            .restaurantName(restaurantName)
                            .profileUpdateDetails(null)
                            .additionalDiscountDetails(null)
                            .tableSectionDetails(tableSectionDetails)
                            .build();
                    
                    return ResponseDto.<RequestDetailsResponse>builder()
                            .message(messageUtil.getMessage("table.section.requests.retrieved", userLocale))
                            .data(response)
                            .build();
                }
            }
            
            // Try section
            Optional<RestaurantSection> sectionOptional = restaurantSectionRepository.findById(requestId);
            if (sectionOptional.isPresent()) {
                RestaurantSection section = sectionOptional.get();
                if (section.getTableSectionRequestStatus() != RequestStatus.NONE) {
                    String restaurantName = null;
                    UUID restaurantId = null;
                    String sectionName = null;
                    if (section.getRestaurantLayout() != null && section.getRestaurantLayout().getRestaurant() != null) {
                        Restaurant restaurant = section.getRestaurantLayout().getRestaurant();
                        restaurantId = restaurant.getId();
                        if (restaurant.getTranslations() != null && !restaurant.getTranslations().isEmpty()) {
                            String userLanguage = userLocale.getLanguage();
                            restaurantName = restaurant.getTranslations().stream()
                                    .filter(t -> t.getLanguageCode() != null && t.getLanguageCode().startsWith(userLanguage))
                                    .findFirst()
                                    .map(com.gulfnet.shared_library.entity.RestaurantTranslation::getName)
                                    .orElse(restaurant.getTranslations().get(0).getName());
                        } else {
                            restaurantName = "Restaurant";
                        }
                    }
                    
                    // Get section name from translations
                    if (section.getTranslations() != null && !section.getTranslations().isEmpty()) {
                        String userLanguage = userLocale.getLanguage();
                        sectionName = section.getTranslations().stream()
                                .filter(t -> t.getLanguageCode() != null && t.getLanguageCode().startsWith(userLanguage))
                                .findFirst()
                                .map(com.gulfnet.shared_library.entity.RestaurantSectionTranslation::getName)
                                .orElse(section.getTranslations().get(0).getName());
                    }
                    
                    // Get role name for requestedBy user
                    String requestedByRole = null;
                    if (section.getTableSectionRequestedBy() != null && section.getTableSectionRequestedBy().getRoleId() != null) {
                        var role = roleRepository.findById(section.getTableSectionRequestedBy().getRoleId()).orElse(null);
                        if (role != null) {
                            requestedByRole = role.getName();
                        }
                    }
                    
                    TableSectionRequestResponse tableSectionDetails = TableSectionRequestResponse.builder()
                            .entityId(section.getId())
                            .entityType("Section")
                            .entityName(sectionName != null ? sectionName : "Section")
                            .restaurantName(restaurantName)
                            .restaurantId(restaurantId)
                            .requestData(section.getTableSectionRequestData())
                            .requestStatus(section.getTableSectionRequestStatus())
                            .requestedAt(section.getTableSectionRequestedAt() != null ? section.getTableSectionRequestedAt().toLocalDateTime() : null)
                            .requestedBy(section.getTableSectionRequestedBy() != null ? section.getTableSectionRequestedBy().getId() : null)
                            .requestedByName(section.getTableSectionRequestedBy() != null ? 
                                section.getTableSectionRequestedBy().getFirstName() + " " + section.getTableSectionRequestedBy().getLastName() : null)
                            .requestedByRole(requestedByRole)
                            .reviewedAt(section.getTableSectionReviewedAt() != null ? section.getTableSectionReviewedAt().toLocalDateTime() : null)
                            .reviewedBy(section.getTableSectionReviewedBy() != null ? section.getTableSectionReviewedBy().getId() : null)
                            .reviewedByName(section.getTableSectionReviewedBy() != null ? 
                                section.getTableSectionReviewedBy().getFirstName() + " " + section.getTableSectionReviewedBy().getLastName() : null)
                            .comments(section.getTableSectionRequestComments())
                            .reason(section.getTableSectionRequestComments()) // Reason is stored in comments field
                            .build();
                    
                    RequestDetailsResponse response = RequestDetailsResponse.builder()
                            .requestType(messageUtil.getMessage("request.type.table.section", userLocale))
                            .restaurantName(restaurantName)
                            .profileUpdateDetails(null)
                            .additionalDiscountDetails(null)
                            .tableSectionDetails(tableSectionDetails)
                            .build();
                    
                    return ResponseDto.<RequestDetailsResponse>builder()
                            .message(messageUtil.getMessage("table.section.requests.retrieved", userLocale))
                            .data(response)
                            .build();
                }
            }
        }
        
        // Try to find as refund request (check if it's a transaction with a refund request)
        Optional<Transaction> transactionOptional = transactionRepository.findById(requestId);
        if (transactionOptional.isPresent()) {
            Transaction transaction = transactionOptional.get();
            if (transaction.getRequestStatus() == RequestStatus.OPEN || transaction.getRequestStatus() == RequestStatus.APPROVED || transaction.getRequestStatus() == RequestStatus.DECLINED) {
                // Check if it's a refund request (not cancellation)
                try {
                    if (transaction.getRequestData() != null) {
                        ObjectMapper objectMapper = new ObjectMapper();
                        Map<String, Object> requestData = objectMapper.readValue(transaction.getRequestData(), Map.class);
                        String requestType = (String) requestData.get("requestType");
                        if ("REFUND".equals(requestType)) {
                            // This is a refund request
                            String restaurantName = null;
                            if (transaction.getRestaurant() != null && transaction.getRestaurant().getTranslations() != null && !transaction.getRestaurant().getTranslations().isEmpty()) {
                                String userLanguage = userLocale.getLanguage();
                                restaurantName = transaction.getRestaurant().getTranslations().stream()
                                        .filter(t -> t.getLanguageCode() != null && t.getLanguageCode().startsWith(userLanguage))
                                        .findFirst()
                                        .map(com.gulfnet.shared_library.entity.RestaurantTranslation::getName)
                                        .orElse(transaction.getRestaurant().getTranslations().get(0).getName());
                            } else if (transaction.getRestaurant() != null) {
                                restaurantName = "Restaurant";
                            }
                            
                            // Parse refund items from request data
                            List<com.gulfnet.shared_library.model.response.dto.RefundRequestResponse.RefundItemResponse> refundItems = new ArrayList<>();
                            
                            // Parse orderedItems
                            if (requestData.containsKey("orderedItems")) {
                                @SuppressWarnings("unchecked")
                                List<Map<String, Object>> orderedItemsList = (List<Map<String, Object>>) requestData.get("orderedItems");
                                if (orderedItemsList != null && !orderedItemsList.isEmpty()) {
                                    for (Map<String, Object> item : orderedItemsList) {
                                        UUID orderedItemId = UUID.fromString((String) item.get("orderedItemId"));
                                        String rawItemName = item.containsKey("itemName") ? (String) item.get("itemName") : null;
                                        String itemName = resolveRefundLineDisplayName(rawItemName, orderedItemId, false, userLocale);
                                        RefundRequestResponse.RefundItemResponse refundItem = RefundRequestResponse.RefundItemResponse.builder()
                                                .itemId(orderedItemId)
                                                .itemType("ITEM")
                                                .itemName(itemName)
                                                .quantity(item.containsKey("refundQuantity") ? 
                                                        ((Number) item.get("refundQuantity")).intValue() : 
                                                        (item.containsKey("quantity") ? 
                                                                ((Number) item.get("quantity")).intValue() : 1))
                                                .refundAmount(new BigDecimal(((Number) item.get("refundAmount")).toString()))
                                                .build();
                                        refundItems.add(refundItem);
                                    }
                                }
                            }
                            
                            // Parse orderedCombos
                            if (requestData.containsKey("orderedCombos")) {
                                @SuppressWarnings("unchecked")
                                List<Map<String, Object>> orderedCombosList = (List<Map<String, Object>>) requestData.get("orderedCombos");
                                if (orderedCombosList != null && !orderedCombosList.isEmpty()) {
                                    for (Map<String, Object> combo : orderedCombosList) {
                                        UUID orderedComboId = UUID.fromString((String) combo.get("orderedComboId"));
                                        String rawComboName = combo.containsKey("comboName") ? (String) combo.get("comboName") : null;
                                        String comboName = resolveRefundLineDisplayName(rawComboName, orderedComboId, true, userLocale);
                                        RefundRequestResponse.RefundItemResponse refundItem = RefundRequestResponse.RefundItemResponse.builder()
                                                .itemId(orderedComboId)
                                                .itemType("COMBO")
                                                .itemName(comboName)
                                                .quantity(combo.containsKey("refundQuantity") ? 
                                                        ((Number) combo.get("refundQuantity")).intValue() : 
                                                        (combo.containsKey("quantity") ? 
                                                                ((Number) combo.get("quantity")).intValue() : 1))
                                                .refundAmount(new BigDecimal(((Number) combo.get("refundAmount")).toString()))
                                                .build();
                                        refundItems.add(refundItem);
                                    }
                                }
                            }
                            
                            // Parse all refund amount breakdown fields from request_data
                            BigDecimal totalRefundAmount = requestData.containsKey("totalRefundAmount") 
                                    ? new BigDecimal(((Number) requestData.get("totalRefundAmount")).toString())
                                    : BigDecimal.ZERO;
                            BigDecimal subtotalRefundAmount = requestData.containsKey("subtotalRefundAmount") 
                                    ? new BigDecimal(((Number) requestData.get("subtotalRefundAmount")).toString())
                                    : BigDecimal.ZERO;
                            BigDecimal taxRefundAmount = requestData.containsKey("taxRefundAmount") 
                                    ? new BigDecimal(((Number) requestData.get("taxRefundAmount")).toString())
                                    : BigDecimal.ZERO;
                            BigDecimal serviceChargeRefundAmount = requestData.containsKey("serviceChargeRefundAmount") 
                                    ? new BigDecimal(((Number) requestData.get("serviceChargeRefundAmount")).toString())
                                    : BigDecimal.ZERO;
                            BigDecimal packingChargeRefundAmount = requestData.containsKey("packingChargeRefundAmount") 
                                    ? new BigDecimal(((Number) requestData.get("packingChargeRefundAmount")).toString())
                                    : BigDecimal.ZERO;
                            BigDecimal discountRefundAmount = requestData.containsKey("discountRefundAmount") 
                                    ? new BigDecimal(((Number) requestData.get("discountRefundAmount")).toString())
                                    : BigDecimal.ZERO;
                            BigDecimal additionalDiscountRefundAmount = requestData.containsKey("additionalDiscountRefundAmount") 
                                    ? new BigDecimal(((Number) requestData.get("additionalDiscountRefundAmount")).toString())
                                    : BigDecimal.ZERO;
                            
                            // Parse refundType and refundMethod
                            String refundTypeStr = (String) requestData.get("refundType");
                            RefundType refundType = null;
                            if (refundTypeStr != null) {
                                try {
                                    refundType = RefundType.valueOf(refundTypeStr);
                                } catch (IllegalArgumentException e) {
                                    log.warn("Invalid refundType in request_data: {}", refundTypeStr);
                                }
                            }
                            String refundMethod = requestData.containsKey("paymentMethod") ? 
                                    (String) requestData.get("paymentMethod") : transaction.getPaymentMethod();
                            
                            // Get role name for requestedBy user
                            String requestedByRole = null;
                            if (transaction.getRequestedBy() != null && transaction.getRequestedBy().getRoleId() != null) {
                                var role = roleRepository.findById(transaction.getRequestedBy().getRoleId()).orElse(null);
                                if (role != null) {
                                    requestedByRole = role.getName();
                                }
                            }
                            
                            RefundRequestResponse refundDetails = RefundRequestResponse.builder()
                                    .transactionId(transaction.getId())
                                    .orderId(transaction.getOrder() != null ? transaction.getOrder().getId() : null)
                                    .orderNumber(transaction.getOrder() != null ? transaction.getOrder().getOrderNumber() : null)
                                    .transactionNumber(transaction.getTransactionNumber())
                                    .paymentMethod(transaction.getPaymentMethod())
                                    .transactionAmount(transaction.getTransactionAmount())
                                    .refundType(refundType)
                                    .refundMethod(refundMethod)
                                    .totalRefundAmount(totalRefundAmount)
                                    .subtotalRefundAmount(subtotalRefundAmount)
                                    .taxRefundAmount(taxRefundAmount)
                                    .serviceChargeRefundAmount(serviceChargeRefundAmount)
                                    .packingChargeRefundAmount(packingChargeRefundAmount)
                                    .discountRefundAmount(discountRefundAmount)
                                    .additionalDiscountRefundAmount(additionalDiscountRefundAmount)
                                    .refundItems(refundItems)
                                    .refundReason((String) requestData.get("refundReason"))
                                    .requestStatus(transaction.getRequestStatus())
                                    .requestedAt(transaction.getRequestedAt() != null ? transaction.getRequestedAt().toLocalDateTime() : null)
                                    .requestedBy(transaction.getRequestedBy() != null ? transaction.getRequestedBy().getId() : null)
                                    .requestedByName(transaction.getRequestedBy() != null ? 
                                        transaction.getRequestedBy().getFirstName() + " " + transaction.getRequestedBy().getLastName() : null)
                                    .requestedByRole(requestedByRole)
                                    .reviewedAt(transaction.getReviewedAt() != null ? ((OffsetDateTime) transaction.getReviewedAt()).toLocalDateTime() : null)
                                    .reviewedBy(transaction.getReviewedBy() != null ? transaction.getReviewedBy().getId() : null)
                                    .reviewedByName(transaction.getReviewedBy() != null ? 
                                        transaction.getReviewedBy().getFirstName() + " " + transaction.getReviewedBy().getLastName() : null)
                                    .comments(transaction.getRequestComments())
                                    .restaurantId(transaction.getRestaurant() != null ? transaction.getRestaurant().getId() : null)
                                    .restaurantName(restaurantName)
                                    .build();
                            
                            RequestDetailsResponse response = RequestDetailsResponse.builder()
                                    .requestType(messageUtil.getMessage("request.type.refund", userLocale))
                                    .restaurantName(restaurantName)
                                    .profileUpdateDetails(null)
                                    .additionalDiscountDetails(null)
                                    .tableSectionDetails(null)
                                    .refundDetails(refundDetails)
                                    .build();
                            
                            return ResponseDto.<RequestDetailsResponse>builder()
                                    .message(messageUtil.getMessage("refund.requests.retrieved", userLocale))
                                    .data(response)
                                    .build();
                        }
                    }
                } catch (JsonProcessingException e) {
                    log.warn("Error parsing request data for transaction {}: {}", requestId, e.getMessage());
                }
                
                // Check if it's a transaction cancellation request (not refund)
                // Transaction cancellation requests have requestStatus but requestType is not "REFUND" in requestData
                // If requestData is null or doesn't contain "requestType":"REFUND", it's a transaction cancellation request
                boolean isRefundRequest = false;
                if (transaction.getRequestData() != null && transaction.getRequestData().contains("\"requestType\":\"REFUND\"")) {
                    isRefundRequest = true;
                }
                
                if (!isRefundRequest && transaction.getRequestStatus() != RequestStatus.NONE) {
                    // This is a transaction cancellation request
                    // Only MANAGER can see transaction cancellation requests (HQ_ADMIN cannot)
                    if (!"MANAGER".equals(userRole)) {
                        throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                                messageUtil.getMessage("transaction.cancellation.request.unauthorized", userLocale));
                    }
                    
                    // MANAGER can only view requests from their own restaurant
                    // Get manager's restaurant ID if user is a MANAGER
                    UUID managerRestaurantId = null;
                    if (userId != null && !userId.trim().isEmpty() && !"null".equalsIgnoreCase(userId)) {
                        try {
                            Optional<User> managerOpt = userRepository.findById(UUID.fromString(userId));
                            if (managerOpt.isPresent() && managerOpt.get().getRestaurantId() != null) {
                                managerRestaurantId = managerOpt.get().getRestaurantId();
                            }
                        } catch (IllegalArgumentException e) {
                            log.warn("Invalid userId format: {}", userId);
                        }
                    }
                    
                    if (managerRestaurantId != null && transaction.getRestaurant() != null) {
                        if (!managerRestaurantId.equals(transaction.getRestaurant().getId())) {
                            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                                    messageUtil.getMessage("transaction.cancellation.request.unauthorized.restaurant", userLocale));
                        }
                    }
                    
                    // Build transaction cancellation request response
                        String cancellationReason = null;
                        try {
                            if (transaction.getRequestData() != null && !transaction.getRequestData().trim().isEmpty()) {
                                ObjectMapper objectMapper = new ObjectMapper();
                                com.gulfnet.shared_library.model.request.TransactionCancellationRequestDto requestDto = 
                                        objectMapper.readValue(transaction.getRequestData(), 
                                                com.gulfnet.shared_library.model.request.TransactionCancellationRequestDto.class);
                                if (requestDto != null) {
                                    cancellationReason = requestDto.getCancellationReason();
                                }
                            }
                        } catch (JsonProcessingException e) {
                            log.warn("Error parsing transaction cancellation request data for transaction {}: {}", transaction.getId(), e.getMessage());
                        } catch (Exception e) {
                            log.error("Unexpected error parsing transaction cancellation request data for transaction {}: {}", transaction.getId(), e.getMessage(), e);
                        }
                        
                        // Get role name for requestedBy user
                        String requestedByRole = null;
                        if (transaction.getRequestedBy() != null && transaction.getRequestedBy().getRoleId() != null) {
                            var role = roleRepository.findById(transaction.getRequestedBy().getRoleId()).orElse(null);
                            if (role != null) {
                                requestedByRole = role.getName();
                            }
                        }
                        
                        String restaurantName = null;
                        if (transaction.getRestaurant() != null && transaction.getRestaurant().getTranslations() != null && !transaction.getRestaurant().getTranslations().isEmpty()) {
                            String userLanguage = userLocale.getLanguage();
                            restaurantName = transaction.getRestaurant().getTranslations().stream()
                                    .filter(t -> t.getLanguageCode() != null && t.getLanguageCode().startsWith(userLanguage))
                                    .findFirst()
                                    .map(com.gulfnet.shared_library.entity.RestaurantTranslation::getName)
                                    .orElse(transaction.getRestaurant().getTranslations().get(0).getName());
                        } else if (transaction.getRestaurant() != null) {
                            restaurantName = "Restaurant";
                        }
                        
                        // Build transaction cancellation request response with proper null handling
                        String requestedByName = null;
                        if (transaction.getRequestedBy() != null) {
                            String firstName = transaction.getRequestedBy().getFirstName() != null ? transaction.getRequestedBy().getFirstName() : "";
                            String lastName = transaction.getRequestedBy().getLastName() != null ? transaction.getRequestedBy().getLastName() : "";
                            requestedByName = (firstName + " " + lastName).trim();
                            if (requestedByName.isEmpty()) {
                                requestedByName = null;
                            }
                        }
                        
                        String reviewedByName = null;
                        if (transaction.getReviewedBy() != null) {
                            String firstName = transaction.getReviewedBy().getFirstName() != null ? transaction.getReviewedBy().getFirstName() : "";
                            String lastName = transaction.getReviewedBy().getLastName() != null ? transaction.getReviewedBy().getLastName() : "";
                            reviewedByName = (firstName + " " + lastName).trim();
                            if (reviewedByName.isEmpty()) {
                                reviewedByName = null;
                            }
                        }
                        
                        TransactionCancellationRequestResponse transactionCancellationDetails = TransactionCancellationRequestResponse.builder()
                                .transactionId(transaction.getId())
                                .orderId(transaction.getOrder() != null ? transaction.getOrder().getId() : null)
                                .orderNumber(transaction.getOrder() != null ? transaction.getOrder().getOrderNumber() : null)
                                .transactionNumber(transaction.getTransactionNumber())
                                .paymentMethod(transaction.getPaymentMethod())
                                .transactionAmount(transaction.getOrder() != null && transaction.getOrder().getTotalAmount() != null 
                                        ? transaction.getOrder().getTotalAmount() 
                                        : transaction.getTransactionAmount())
                                .currentTransactionStatus(transaction.getTransactionStatus())
                                .cancellationReason(cancellationReason)
                                .requestStatus(transaction.getRequestStatus())
                                .requestedAt(transaction.getRequestedAt() != null ? transaction.getRequestedAt().toLocalDateTime() : null)
                                .requestedBy(transaction.getRequestedBy() != null ? transaction.getRequestedBy().getId() : null)
                                .requestedByName(requestedByName)
                                .requestedByRole(requestedByRole)
                                .reviewedAt(transaction.getReviewedAt() != null ? ((OffsetDateTime) transaction.getReviewedAt()).toLocalDateTime() : null)
                                .reviewedBy(transaction.getReviewedBy() != null ? transaction.getReviewedBy().getId() : null)
                                .reviewedByName(reviewedByName)
                                .comments(transaction.getRequestComments())
                                .restaurantId(transaction.getRestaurant() != null ? transaction.getRestaurant().getId() : null)
                                .restaurantName(restaurantName)
                                .build();
                        
                        RequestDetailsResponse response = RequestDetailsResponse.builder()
                                .requestType(messageUtil.getMessage("request.type.transaction.cancellation", userLocale))
                                .restaurantName(restaurantName)
                                .profileUpdateDetails(null)
                                .additionalDiscountDetails(null)
                                .tableSectionDetails(null)
                                .refundDetails(null)
                                .itemCancellationDetails(null)
                                .comboCancellationDetails(null)
                                .transactionCancellationDetails(transactionCancellationDetails)
                                .build();
                        
                        return ResponseDto.<RequestDetailsResponse>builder()
                                .message(messageUtil.getMessage("transaction.cancellation.requests.retrieved", userLocale))
                                .data(response)
                                .build();
                }
            }
        }
        
        // Try to find as item cancellation request (check if it's an OrderedItem with a cancellation request)
        Optional<OrderedItem> orderedItemOptional = orderedItemRepository.findById(requestId);
        if (orderedItemOptional.isPresent()) {
            OrderedItem orderedItem = orderedItemOptional.get();
            if (orderedItem.getCancellationRequestStatus() != RequestStatus.NONE) {
                // This is an item cancellation request
                ItemCancellationRequestResponse itemCancellationDetails = buildItemCancellationRequestResponse(orderedItem, userLocale);
                
                RequestDetailsResponse response = RequestDetailsResponse.builder()
                        .requestType(messageUtil.getMessage("request.type.item.cancellation", userLocale))
                        .restaurantName(itemCancellationDetails.getRestaurantName())
                        .profileUpdateDetails(null)
                        .additionalDiscountDetails(null)
                        .tableSectionDetails(null)
                        .refundDetails(null)
                        .itemCancellationDetails(itemCancellationDetails)
                        .comboCancellationDetails(null)
                        .build();
                
                return ResponseDto.<RequestDetailsResponse>builder()
                        .message(messageUtil.getMessage("item.cancellation.requests.retrieved", userLocale))
                        .data(response)
                        .build();
            }
        }
        
        // Try to find as combo cancellation request (check if it's an OrderedCombo with a cancellation request)
        Optional<OrderedCombo> orderedComboOptional = orderedComboRepository.findById(requestId);
        if (orderedComboOptional.isPresent()) {
            OrderedCombo orderedCombo = orderedComboOptional.get();
            if (orderedCombo.getCancellationRequestStatus() != RequestStatus.NONE) {
                // This is a combo cancellation request
                ComboCancellationRequestResponse comboCancellationDetails = buildComboCancellationRequestResponse(orderedCombo, userLocale);
                
                RequestDetailsResponse response = RequestDetailsResponse.builder()
                        .requestType(messageUtil.getMessage("request.type.item.cancellation", userLocale))
                        .restaurantName(comboCancellationDetails.getRestaurantName())
                        .profileUpdateDetails(null)
                        .additionalDiscountDetails(null)
                        .tableSectionDetails(null)
                        .refundDetails(null)
                        .itemCancellationDetails(null)
                        .comboCancellationDetails(comboCancellationDetails)
                        .build();
                
                return ResponseDto.<RequestDetailsResponse>builder()
                        .message(messageUtil.getMessage("item.cancellation.requests.retrieved", userLocale))
                        .data(response)
                        .build();
            }
        }
        
        // If we reach here, the request ID doesn't match any request
        throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                messageUtil.getMessage("request.not.found", userLocale));
    }

    /**
     * Adjusts GET items in a BXGY discount application based on mathematical eligibility.
     * 
     * Formula: eligibleFreeQty = floor(activeBuyQty / buyQuantity) * getQuantity
     * If activeGetQty > eligibleFreeQty, cancels or reduces GET items to match eligible quantity.
     * 
     * @param discountApplicationId The BXGY discount application ID
     * @param discountId The discount ID (to fetch buyQuantity and getQuantity)
     * @param authenticatedUser User performing the action
     * @param hasUserId Whether user ID should be set
     * @param userLocale User locale for notifications
     * @param now Current timestamp for updates
     */
    private void adjustBxgyGetItemsAfterBuyCancellation(
            UUID discountApplicationId,
            UUID discountId,
            User authenticatedUser,
            boolean hasUserId,
            Locale userLocale,
            OffsetDateTime now) {
        
        if (discountApplicationId == null || discountId == null) {
            log.warn("Cannot adjust BXGY GET items: discountApplicationId or discountId is null");
            return;
        }
        
        // Step 1: Fetch all items in this discount application
        List<OrderedItem> items = orderedItemRepository.findByDiscountApplicationId(discountApplicationId);
        
        if (items.isEmpty()) {
            log.warn("No items found for discount_application_id: {}", discountApplicationId);
            return;
        }
        
        // Step 2: Fetch discount to get buyQuantity and getQuantity
        Discount discount = discountRepository.findById(discountId).orElse(null);
        if (discount == null) {
            log.warn("Discount {} not found for discount_application_id: {}", discountId, discountApplicationId);
            return;
        }
        
        int buyQuantityRequired = discount.getBuyQuantity() != null ? discount.getBuyQuantity() : 1;
        int getQuantityFree = discount.getGetQuantity() != null ? discount.getGetQuantity() : 1;
        
        // Step 3: Calculate active BUY and GET quantities (excluding cancelled items)
        int activeBuyQty = items.stream()
                .filter(i -> i.getBxgyRole() == BxgyRole.BUY)
                .filter(i -> i.getItemStatus() != com.gulfnet.shared_library.enums.ItemStatus.CANCELED)
                .mapToInt(OrderedItem::getQuantity)
                .sum();
        
        int activeGetQty = items.stream()
                .filter(i -> i.getBxgyRole() == BxgyRole.GET)
                .filter(i -> i.getItemStatus() != com.gulfnet.shared_library.enums.ItemStatus.CANCELED)
                .mapToInt(OrderedItem::getQuantity)
                .sum();
        
        // Step 4: Compute eligible free quantity
        int eligibleFreeQty = (activeBuyQty / buyQuantityRequired) * getQuantityFree;
        
        log.info("BXGY GET adjustment calculation - discountApplicationId: {}, activeBuyQty: {}, activeGetQty: {}, buyQuantityRequired: {}, getQuantityFree: {}, eligibleFreeQty: {}",
                discountApplicationId, activeBuyQty, activeGetQty, buyQuantityRequired, getQuantityFree, eligibleFreeQty);
        
        // Step 5: If excess GET exists, adjust quantity
        if (activeGetQty > eligibleFreeQty) {
            int excessQty = activeGetQty - eligibleFreeQty;
            
            log.info("Excess GET quantity detected: {}. Adjusting GET items for discount_application_id: {}", 
                    excessQty, discountApplicationId);
            
            // Get all active GET items
            List<OrderedItem> getItems = items.stream()
                    .filter(i -> i.getBxgyRole() == BxgyRole.GET)
                    .filter(i -> i.getItemStatus() != com.gulfnet.shared_library.enums.ItemStatus.CANCELED)
                    .collect(Collectors.toList());
            
            // Process GET items to cancel or reduce excess quantity
            for (OrderedItem getItem : getItems) {
                if (excessQty <= 0) {
                    break;
                }
                
                int itemQty = getItem.getQuantity();
                
                if (itemQty <= excessQty) {
                    // Cancel full row
                    log.info("Cancelling full GET item {} (quantity: {}) due to excess quantity", 
                            getItem.getId(), itemQty);
                    if (getItem.getItemStatus() != null && 
                        getItem.getItemStatus() != com.gulfnet.shared_library.enums.ItemStatus.CANCELED &&
                        (getItem.getItemStatus() == com.gulfnet.shared_library.enums.ItemStatus.COOKING 
                            || getItem.getItemStatus() == com.gulfnet.shared_library.enums.ItemStatus.READY 
                            || getItem.getItemStatus() == com.gulfnet.shared_library.enums.ItemStatus.SERVED)
                        && getItem.getWastageSourceStatus() == null) {
                        getItem.setWastageSourceStatus(getItem.getItemStatus());
                    }
                    getItem.setItemStatus(com.gulfnet.shared_library.enums.ItemStatus.CANCELED);
                    getItem.setUpdatedAt(now);
                    if (hasUserId && authenticatedUser != null) {
                        getItem.setUpdatedBy(authenticatedUser);
                    }
                    excessQty -= itemQty;
                    
                    // Send KDS notification for cancelled GET item (if notificationService is available)
                    try {
                        if (notificationService != null) {
                            // Use reflection to call notifyItemCanceled method
                            java.lang.reflect.Method notifyMethod = notificationService.getClass()
                                .getMethod("notifyItemCanceled", OrderedItem.class, List.class, Locale.class);
                            notifyMethod.invoke(notificationService, getItem, java.util.Collections.emptyList(), userLocale);
                            log.info("Sent KDS notification for cancelled GET item: {}", getItem.getId());
                        }
                    } catch (Exception e) {
                        log.warn("Failed to send KDS notification for cancelled GET item {}: {}", 
                                getItem.getId(), e.getMessage());
                    }
                } else {
                    // Partial reduction
                    int newQty = itemQty - excessQty;
                    log.info("Reducing GET item {} quantity from {} to {} due to excess quantity", 
                            getItem.getId(), itemQty, newQty);
                    getItem.setQuantity(newQty);
                    getItem.setUpdatedAt(now);
                    if (hasUserId && authenticatedUser != null) {
                        getItem.setUpdatedBy(authenticatedUser);
                    }
                    excessQty = 0;
                }
                
                orderedItemRepository.save(getItem);
            }
            
            log.info("Completed BXGY GET adjustment for discount_application_id: {}. Remaining excess: {}", 
                    discountApplicationId, excessQty);
        } else {
            log.info("No excess GET quantity. activeGetQty ({}) <= eligibleFreeQty ({})", 
                    activeGetQty, eligibleFreeQty);
        }
    }

    @Override
    @Transactional(noRollbackFor = {AmqpException.class, org.springframework.amqp.AmqpConnectException.class, 
            org.springframework.web.server.ResponseStatusException.class, org.hibernate.LazyInitializationException.class,
            org.hibernate.HibernateException.class, org.hibernate.QueryException.class})
    public ResponseDto<RequestApprovalResponse> approveOrDeclineRequest(UUID requestId, ProfileUpdateApprovalRequest request, String managerId, String managerRole) {
        Locale userLocale = LocaleContextHolder.getLocale();
        final ObjectMapper requestDataObjectMapper = new ObjectMapper();
        final UUID approverId = parseUserIdOrThrow(managerId, userLocale);
        User cachedApprover = null;
        
        // Only MANAGER and HQ_ADMIN can approve/decline requests
        if (!"MANAGER".equals(managerRole) && !"HQ_ADMIN".equals(managerRole)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    messageUtil.getMessage("user.profile.update.request.unauthorized", userLocale));
        }
        
        // Try to find as profile update request first
        Optional<User> userOptional = userRepository.findById(requestId);
        if (userOptional.isPresent()) {
            User user = userOptional.get();
            if (user.getProfileUpdateRequestStatus() == RequestStatus.OPEN) {
                // This is a profile update request - use existing method
                ResponseDto<ProfileUpdateRequestResponse> profileResponse = approveOrDeclineProfileUpdateRequest(requestId, request, managerId, managerRole);
                
                RequestApprovalResponse approvalResponse = RequestApprovalResponse.builder()
                        .requestType(messageUtil.getMessage("request.type.profile.update", userLocale))
                        .profileUpdateResponse(profileResponse.getData())
                        .additionalDiscountResponse(null)
                        .build();
                
                return ResponseDto.<RequestApprovalResponse>builder()
                        .message(profileResponse.getMessage())
                        .data(approvalResponse)
                        .build();
            }
        }
        
        // Try to find as additional discount request
        Optional<Order> orderOptional = orderRepository.findById(requestId);
        if (orderOptional.isPresent()) {
            Order order = orderOptional.get();
            if (order.getAdditionalDiscountRequestStatus() == RequestStatus.OPEN) {
                // This is an additional discount request
                // Only MANAGER can approve additional discount requests (HQ_ADMIN cannot)
                if (!"MANAGER".equals(managerRole)) {
                    throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                            messageUtil.getMessage("additional.discount.request.unauthorized.role", userLocale));
                }
                
                if (cachedApprover == null) {
                    cachedApprover = loadApproverOrThrow(approverId, userLocale);
                }
                User manager = cachedApprover;
                
                // MANAGER can only approve requests from their own restaurant
                if (manager.getRestaurantId() != null && order.getRestaurant() != null) {
                    if (!manager.getRestaurantId().equals(order.getRestaurant().getId())) {
                        throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                                messageUtil.getMessage("additional.discount.request.unauthorized.restaurant", userLocale));
                    }
                }
                
                // Atomic request state update with pessimistic locking
                // Lock the order entity to prevent concurrent modifications
                entityManager.lock(order, jakarta.persistence.LockModeType.PESSIMISTIC_WRITE);
                
                // Re-check status after acquiring lock (double-check locking pattern)
                if (order.getAdditionalDiscountRequestStatus() != RequestStatus.OPEN) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                            messageUtil.getMessage("additional.discount.request.not.pending", userLocale));
                }
                
                // Check if transaction has been closed (COMPLETED) by cashier
                // If so, manager cannot act on the additional discount request
                Optional<Transaction> transactionOptional = transactionRepository.findByOrderId(order.getId());
                if (transactionOptional.isPresent()) {
                    Transaction transaction = transactionOptional.get();
                    if (transaction.getTransactionStatus() == TransactionStatus.COMPLETED) {
                        throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                                messageUtil.getMessage("additional.discount.request.transaction.closed", userLocale));
                    }
                }
                
                // Use UTC timezone to match the rest of the application
                OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
                
                if (request.getAction() == RequestStatus.APPROVED) {
                    // Approve - apply the discount (rounding aligned with chain config + CurrencyFormatter)
                    RestaurantChainConfigCache chainCfg = getRestaurantChainConfig();
                    String cur = getCurrencyFromConfig(chainCfg);
                    com.gulfnet.shared_library.enums.RoundingMode roundingPolicy = chainCfg.getRoundingPolicy();
                    java.math.RoundingMode divideRm = resolveChainMoneyDivideRounding(roundingPolicy);

                    BigDecimal currentTotal = order.getTotalAmount() != null ? order.getTotalAmount() : BigDecimal.ZERO;
                    BigDecimal additionalDiscountSavings = BigDecimal.ZERO;

                    if (order.getAdditionalDiscountType() == DiscountType.PERCENT) {
                        additionalDiscountSavings = CurrencyFormatter.formatAmount(
                                currentTotal.multiply(order.getAdditionalDiscountValue())
                                        .divide(BigDecimal.valueOf(100), 10, divideRm),
                                cur,
                                roundingPolicy);
                    } else if (order.getAdditionalDiscountType() == DiscountType.FLAT) {
                        additionalDiscountSavings = CurrencyFormatter.formatAmount(
                                order.getAdditionalDiscountValue(), cur, roundingPolicy);
                    }

                    BigDecimal totalAmount = CurrencyFormatter.formatAmount(
                            currentTotal.subtract(additionalDiscountSavings), cur, roundingPolicy);
                    if (totalAmount.compareTo(BigDecimal.ZERO) < 0) {
                        totalAmount = BigDecimal.ZERO;
                    }

                    order.setAdditionalDiscountAmount(additionalDiscountSavings);
                    order.setTotalAmount(totalAmount);
                    order.setAdditionalDiscountRequestStatus(RequestStatus.APPROVED);
                } else if (request.getAction() == RequestStatus.DECLINED) {
                    // Decline - mark as declined but keep request data for audit
                    order.setAdditionalDiscountRequestStatus(RequestStatus.DECLINED);
                }
                
                // Set review information (using UTC to match the rest of the application)
                order.setAdditionalDiscountReviewedAt(now);
                order.setAdditionalDiscountReviewedBy(manager);
                order.setAdditionalDiscountRequestComments(request.getComments());
                order.setUpdatedAt(now);
                order.setUpdatedBy(manager);
                
                orderRepository.save(order);
                
                // CRITICAL: Re-fetch order with all relationships after save to ensure lazy-loaded relationships are available
                // This prevents LazyInitializationException when building response
                UUID orderId = order.getId();
                try {
                    order = orderRepository.findByIdWithRelationshipsForAdditionalDiscountResponse(orderId)
                            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                                    messageUtil.getMessage("order.not.found", userLocale)));
                    log.info("Re-fetched order {} with all relationships for additional discount response", orderId);
                } catch (Exception e) {
                    log.warn("Could not re-fetch order {} with relationships: {}", orderId, e.getMessage());
                    // Fallback to simple findById if the relationship fetch fails
                    try {
                        order = orderRepository.findById(orderId)
                                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                                        messageUtil.getMessage("order.not.found", userLocale)));
                    } catch (Exception ex) {
                        log.error("Failed to re-fetch order {} even with fallback: {}", orderId, ex.getMessage());
                        throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                                messageUtil.getMessage("order.not.found", userLocale));
                    }
                }
                
                ActionType additionalDiscountDecisionActionType = request.getAction() == RequestStatus.APPROVED
                        ? ActionType.REQUEST_ADDITIONAL_DISCOUNT_APPROVE
                        : ActionType.REQUEST_ADDITIONAL_DISCOUNT_DECLINE;

                // Create audit trail for manager when approving/declining additional discount request
                try {
                    createAuditTrail(
                            manager,
                            additionalDiscountDecisionActionType,
                            order.getRestaurant(),
                            request.getAction(),
                            null, // ipAddress
                            null, // userAgent
                            order.getId(),
                            "ORDER",
                            String.format("Additional discount request %s. Comments: %s", 
                                    request.getAction() == RequestStatus.APPROVED ? "approved" : "declined",
                                    request.getComments() != null ? request.getComments() : "N/A"),
                            order.getAdditionalDiscountRequestedBy(), // requestedBy
                            order.getAdditionalDiscountRequestedAt(), // requestedAt
                            manager, // reviewedBy
                            now // reviewedAt
                    );
                } catch (Exception e) {
                    log.error("Failed to create audit trail for manager when approving/declining additional discount: {}", e.getMessage(), e);
                }
                
                // Create audit trail entry for cashier when additional discount request is approved/declined
                try {
                    User requester = order.getAdditionalDiscountRequestedBy();
                    if (requester != null) {
                        createAuditTrail(
                                requester,
                                additionalDiscountDecisionActionType,
                                order.getRestaurant(),
                                request.getAction(), // APPROVED or DECLINED
                                null, // ipAddress
                                null, // userAgent
                                order.getId(),
                                "ORDER",
                                String.format("Additional discount request %s by manager. Comments: %s",
                                        request.getAction() == RequestStatus.APPROVED ? "approved" : "declined",
                                        request.getComments() != null ? request.getComments() : "N/A"),
                                requester, // requestedBy
                                order.getAdditionalDiscountRequestedAt(), // requestedAt
                                manager, // reviewedBy
                                now // reviewedAt
                        );
                    }
                } catch (Exception e) {
                    log.error("Failed to create audit trail for cashier when additional discount was approved/declined: {}", e.getMessage(), e);
                }
                
                // Save notification to database for the requester
                try {
                    User requester = order.getAdditionalDiscountRequestedBy();
                    if (requester != null) {
                        saveAdditionalDiscountRequestNotification(requester, request.getAction(), order.getOrderNumber(), order.getId(), manager, request.getComments(), userLocale);
                    }
                } catch (Exception e) {
                    log.error("Failed to save additional discount request notification: {}", e.getMessage());
                }
                
                // Get role name for requestedBy user
                String requestedByRole = null;
                if (order.getAdditionalDiscountRequestedBy() != null && order.getAdditionalDiscountRequestedBy().getRoleId() != null) {
                    var role = roleRepository.findById(order.getAdditionalDiscountRequestedBy().getRoleId()).orElse(null);
                    if (role != null) {
                        requestedByRole = role.getName();
                    }
                }
                
                // Get restaurant name
                String restaurantName = null;
                UUID restaurantId = null;
                if (order.getRestaurant() != null) {
                    restaurantId = order.getRestaurant().getId();
                    if (order.getRestaurant().getTranslations() != null && !order.getRestaurant().getTranslations().isEmpty()) {
                        String userLanguage = userLocale.getLanguage();
                        restaurantName = order.getRestaurant().getTranslations().stream()
                                .filter(t -> t.getLanguageCode() != null && t.getLanguageCode().startsWith(userLanguage))
                                .findFirst()
                                .map(com.gulfnet.shared_library.entity.RestaurantTranslation::getName)
                                .orElse(order.getRestaurant().getTranslations().get(0).getName());
                    } else {
                        restaurantName = "Restaurant";
                    }
                }
                
                // Build response
                AdditionalDiscountRequestResponse discountResponse = AdditionalDiscountRequestResponse.builder()
                        .orderId(order.getId())
                        .orderNumber(order.getOrderNumber())
                        .orderTotalAmount(order.getTotalAmount() != null ? order.getTotalAmount() : BigDecimal.ZERO)
                        .additionalDiscountType(order.getAdditionalDiscountType())
                        .additionalDiscountValue(order.getAdditionalDiscountValue())
                        .additionalDiscountAmount(order.getAdditionalDiscountAmount())
                        .additionalDiscountReason(order.getAdditionalDiscountReason())
                        .requestStatus(order.getAdditionalDiscountRequestStatus())
                        .requestedAt(order.getAdditionalDiscountRequestedAt() != null ? order.getAdditionalDiscountRequestedAt().toLocalDateTime() : null)
                        .requestedBy(order.getAdditionalDiscountRequestedBy() != null ? order.getAdditionalDiscountRequestedBy().getId() : null)
                        .requestedByName(order.getAdditionalDiscountRequestedBy() != null ? 
                            order.getAdditionalDiscountRequestedBy().getFirstName() + " " + order.getAdditionalDiscountRequestedBy().getLastName() : null)
                        .requestedByRole(requestedByRole)
                        .reviewedAt(order.getAdditionalDiscountReviewedAt() != null ? ((OffsetDateTime) order.getAdditionalDiscountReviewedAt()).toLocalDateTime() : null)
                        .reviewedBy(order.getAdditionalDiscountReviewedBy() != null ? order.getAdditionalDiscountReviewedBy().getId() : null)
                        .reviewedByName(order.getAdditionalDiscountReviewedBy() != null ? 
                            order.getAdditionalDiscountReviewedBy().getFirstName() + " " + order.getAdditionalDiscountReviewedBy().getLastName() : null)
                        .comments(order.getAdditionalDiscountRequestComments())
                        .restaurantId(restaurantId)
                        .restaurantName(restaurantName)
                        .build();
                
                RequestApprovalResponse approvalResponse = RequestApprovalResponse.builder()
                        .requestType(messageUtil.getMessage("request.type.additional.discount", userLocale))
                        .profileUpdateResponse(null)
                        .additionalDiscountResponse(discountResponse)
                        .build();
                
                String messageKey = request.getAction() == RequestStatus.APPROVED ? 
                        "additional.discount.request.approved" : "additional.discount.request.declined";
                
                // Notify all active managers about the request resolution
                try {
                    if (restaurantId != null) {
                        List<User> activeManagers = findActiveManagersForRestaurant(restaurantId);
                        notifyManagersAboutRequestResolution(activeManagers, manager, order, messageUtil.getMessage("request.type.additional.discount", userLocale), 
                                request.getAction() == RequestStatus.APPROVED, request.getComments(), userLocale);
                    }
                } catch (Exception e) {
                    log.error("Failed to notify managers about additional discount request resolution: {}", e.getMessage(), e);
                }
                
                return ResponseDto.<RequestApprovalResponse>builder()
                        .message(messageUtil.getMessage(messageKey, userLocale))
                        .data(approvalResponse)
                        .build();
            }
        }
        
        // Try to find as table/section request (only HQ_ADMIN can approve these)
        if ("HQ_ADMIN".equals(managerRole)) {
            // Try table first
            Optional<RestaurantTable> tableOptional = restaurantTableRepository.findById(requestId);
            if (tableOptional.isPresent()) {
                RestaurantTable table = tableOptional.get();
                if (table.getTableSectionRequestStatus() == RequestStatus.OPEN) {
                    if (cachedApprover == null) {
                        cachedApprover = loadApproverOrThrow(approverId, userLocale);
                    }
                    User hqAdmin = cachedApprover;
                    
                    // Use UTC timezone to match the rest of the application
                    OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
                    
                    if (request.getAction() == RequestStatus.APPROVED) {
                        // Approve - apply the requested changes (would need to parse requestData and apply)
                        table.setTableSectionRequestStatus(RequestStatus.APPROVED);
                    } else if (request.getAction() == RequestStatus.DECLINED) {
                        // Decline - mark as declined but keep request data for audit
                        table.setTableSectionRequestStatus(RequestStatus.DECLINED);
                    }
                    
                    // Set review information (using UTC to match the rest of the application)
                    table.setTableSectionReviewedAt(now);
                    table.setTableSectionReviewedBy(hqAdmin);
                    table.setTableSectionRequestComments(request.getComments());
                    table.setUpdatedAt(now);
                    table.setUpdatedBy(hqAdmin);
                    
                    restaurantTableRepository.save(table);
                    
                    // Save notification to database for the requester
                    try {
                        User requester = table.getTableSectionRequestedBy();
                        if (requester != null) {
                            saveTableSectionRequestNotification(requester, request.getAction(), "Table", table.getId(), hqAdmin, userLocale);
                        }
                    } catch (Exception e) {
                        log.error("Failed to save table section request notification: {}", e.getMessage());
                    }
                    
                    // Build response
                    String restaurantName = null;
                    UUID restaurantId = null;
                    if (table.getRestaurantRow() != null && 
                        table.getRestaurantRow().getRestaurantSection() != null &&
                        table.getRestaurantRow().getRestaurantSection().getRestaurantLayout() != null &&
                        table.getRestaurantRow().getRestaurantSection().getRestaurantLayout().getRestaurant() != null) {
                        Restaurant restaurant = table.getRestaurantRow().getRestaurantSection().getRestaurantLayout().getRestaurant();
                        restaurantId = restaurant.getId();
                        if (restaurant.getTranslations() != null && !restaurant.getTranslations().isEmpty()) {
                            String userLanguage = userLocale.getLanguage();
                            restaurantName = restaurant.getTranslations().stream()
                                    .filter(t -> t.getLanguageCode() != null && t.getLanguageCode().startsWith(userLanguage))
                                    .findFirst()
                                    .map(com.gulfnet.shared_library.entity.RestaurantTranslation::getName)
                                    .orElse(restaurant.getTranslations().get(0).getName());
                        } else {
                            restaurantName = "Restaurant";
                        }
                    }
                    
                    // Get role name for requestedBy user
                    String requestedByRole = null;
                    if (table.getTableSectionRequestedBy() != null && table.getTableSectionRequestedBy().getRoleId() != null) {
                        var role = roleRepository.findById(table.getTableSectionRequestedBy().getRoleId()).orElse(null);
                        if (role != null) {
                            requestedByRole = role.getName();
                        }
                    }
                    
                    TableSectionRequestResponse tableSectionResponse = TableSectionRequestResponse.builder()
                            .entityId(table.getId())
                            .entityType("Table")
                            .entityName("Table " + (table.getTableOrder() != null ? table.getTableOrder().toString() : ""))
                            .restaurantName(restaurantName)
                            .restaurantId(restaurantId)
                            .requestData(table.getTableSectionRequestData())
                            .requestStatus(table.getTableSectionRequestStatus())
                            .requestedAt(table.getTableSectionRequestedAt() != null ? table.getTableSectionRequestedAt().toLocalDateTime() : null)
                            .requestedBy(table.getTableSectionRequestedBy() != null ? table.getTableSectionRequestedBy().getId() : null)
                            .requestedByName(table.getTableSectionRequestedBy() != null ? 
                                table.getTableSectionRequestedBy().getFirstName() + " " + table.getTableSectionRequestedBy().getLastName() : null)
                            .requestedByRole(requestedByRole)
                            .reviewedAt(table.getTableSectionReviewedAt() != null ? table.getTableSectionReviewedAt().toLocalDateTime() : null)
                            .reviewedBy(table.getTableSectionReviewedBy() != null ? table.getTableSectionReviewedBy().getId() : null)
                            .reviewedByName(table.getTableSectionReviewedBy() != null ? 
                                table.getTableSectionReviewedBy().getFirstName() + " " + table.getTableSectionReviewedBy().getLastName() : null)
                            .comments(table.getTableSectionRequestComments())
                            .reason(table.getTableSectionRequestComments()) // Reason is stored in comments field
                            .build();
                    
                    RequestApprovalResponse approvalResponse = RequestApprovalResponse.builder()
                            .requestType(messageUtil.getMessage("request.type.table.section", userLocale))
                            .profileUpdateResponse(null)
                            .additionalDiscountResponse(null)
                            .tableSectionResponse(tableSectionResponse)
                            .build();
                    
                    String messageKey = request.getAction() == RequestStatus.APPROVED ? 
                            "table.section.request.approved" : "table.section.request.declined";
                    
                    return ResponseDto.<RequestApprovalResponse>builder()
                            .message(messageUtil.getMessage(messageKey, userLocale))
                            .data(approvalResponse)
                            .build();
                }
            }
            
            // Try section
            Optional<RestaurantSection> sectionOptional = restaurantSectionRepository.findById(requestId);
            if (sectionOptional.isPresent()) {
                RestaurantSection section = sectionOptional.get();
                if (section.getTableSectionRequestStatus() == RequestStatus.OPEN) {
                    if (cachedApprover == null) {
                        cachedApprover = loadApproverOrThrow(approverId, userLocale);
                    }
                    User hqAdmin = cachedApprover;
                    
                    // Use UTC timezone to match the rest of the application
                    OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
                    
                    if (request.getAction() == RequestStatus.APPROVED) {
                        // Approve - apply the requested changes (would need to parse requestData and apply)
                        section.setTableSectionRequestStatus(RequestStatus.APPROVED);
                    } else if (request.getAction() == RequestStatus.DECLINED) {
                        // Decline - mark as declined but keep request data for audit
                        section.setTableSectionRequestStatus(RequestStatus.DECLINED);
                    }
                    
                    // Set review information (using UTC to match the rest of the application)
                    section.setTableSectionReviewedAt(now);
                    section.setTableSectionReviewedBy(hqAdmin);
                    section.setTableSectionRequestComments(request.getComments());
                    section.setUpdatedAt(now);
                    section.setUpdatedBy(hqAdmin);
                    
                    restaurantSectionRepository.save(section);
                    
                    // Save notification to database for the requester
                    try {
                        User requester = section.getTableSectionRequestedBy();
                        if (requester != null) {
                            saveTableSectionRequestNotification(requester, request.getAction(), "Section", section.getId(), hqAdmin, userLocale);
                        }
                    } catch (Exception e) {
                        log.error("Failed to save section request notification: {}", e.getMessage());
                    }
                    
                    // Build response
                    String restaurantName = null;
                    UUID restaurantId = null;
                    String sectionName = null;
                    if (section.getRestaurantLayout() != null && section.getRestaurantLayout().getRestaurant() != null) {
                        Restaurant restaurant = section.getRestaurantLayout().getRestaurant();
                        restaurantId = restaurant.getId();
                        if (restaurant.getTranslations() != null && !restaurant.getTranslations().isEmpty()) {
                            String userLanguage = userLocale.getLanguage();
                            restaurantName = restaurant.getTranslations().stream()
                                    .filter(t -> t.getLanguageCode() != null && t.getLanguageCode().startsWith(userLanguage))
                                    .findFirst()
                                    .map(com.gulfnet.shared_library.entity.RestaurantTranslation::getName)
                                    .orElse(restaurant.getTranslations().get(0).getName());
                        } else {
                            restaurantName = "Restaurant";
                        }
                    }
                    
                    // Get section name from translations
                    if (section.getTranslations() != null && !section.getTranslations().isEmpty()) {
                        String userLanguage = userLocale.getLanguage();
                        sectionName = section.getTranslations().stream()
                                .filter(t -> t.getLanguageCode() != null && t.getLanguageCode().startsWith(userLanguage))
                                .findFirst()
                                .map(com.gulfnet.shared_library.entity.RestaurantSectionTranslation::getName)
                                .orElse(section.getTranslations().get(0).getName());
                    }
                    
                    // Get role name for requestedBy user
                    String requestedByRole = null;
                    if (section.getTableSectionRequestedBy() != null && section.getTableSectionRequestedBy().getRoleId() != null) {
                        var role = roleRepository.findById(section.getTableSectionRequestedBy().getRoleId()).orElse(null);
                        if (role != null) {
                            requestedByRole = role.getName();
                        }
                    }
                    
                    TableSectionRequestResponse tableSectionResponse = TableSectionRequestResponse.builder()
                            .entityId(section.getId())
                            .entityType("Section")
                            .entityName(sectionName != null ? sectionName : "Section")
                            .restaurantName(restaurantName)
                            .restaurantId(restaurantId)
                            .requestData(section.getTableSectionRequestData())
                            .requestStatus(section.getTableSectionRequestStatus())
                            .requestedAt(section.getTableSectionRequestedAt() != null ? section.getTableSectionRequestedAt().toLocalDateTime() : null)
                            .requestedBy(section.getTableSectionRequestedBy() != null ? section.getTableSectionRequestedBy().getId() : null)
                            .requestedByName(section.getTableSectionRequestedBy() != null ? 
                                section.getTableSectionRequestedBy().getFirstName() + " " + section.getTableSectionRequestedBy().getLastName() : null)
                            .requestedByRole(requestedByRole)
                            .reviewedAt(section.getTableSectionReviewedAt() != null ? section.getTableSectionReviewedAt().toLocalDateTime() : null)
                            .reviewedBy(section.getTableSectionReviewedBy() != null ? section.getTableSectionReviewedBy().getId() : null)
                            .reviewedByName(section.getTableSectionReviewedBy() != null ? 
                                section.getTableSectionReviewedBy().getFirstName() + " " + section.getTableSectionReviewedBy().getLastName() : null)
                            .comments(section.getTableSectionRequestComments())
                            .reason(section.getTableSectionRequestComments()) // Reason is stored in comments field
                            .build();
                    
                    RequestApprovalResponse approvalResponse = RequestApprovalResponse.builder()
                            .requestType(messageUtil.getMessage("request.type.table.section", userLocale))
                            .profileUpdateResponse(null)
                            .additionalDiscountResponse(null)
                            .tableSectionResponse(tableSectionResponse)
                            .build();
                    
                    String messageKey = request.getAction() == RequestStatus.APPROVED ? 
                            "table.section.request.approved" : "table.section.request.declined";
                    
                    return ResponseDto.<RequestApprovalResponse>builder()
                            .message(messageUtil.getMessage(messageKey, userLocale))
                            .data(approvalResponse)
                            .build();
                }
            }
        }
        
        // Try to find as refund request (check Transaction entity for refund requests)
        Optional<Transaction> refundTransactionOptional = transactionRepository.findById(requestId);
        Transaction loadedTransaction = refundTransactionOptional.orElse(null);
        boolean loadedTransactionIsRefundRequest = false;
        if (refundTransactionOptional.isPresent()) {
            Transaction transaction = refundTransactionOptional.get();
            if (transaction.getRequestStatus() == RequestStatus.OPEN && transaction.getRequestData() != null) {
                // Check if it's a refund request
                try {
                    Map<String, Object> requestData = requestDataObjectMapper.readValue(transaction.getRequestData(), Map.class);
                    loadedTransactionIsRefundRequest = "REFUND".equals(requestData.get("requestType"));
                } catch (JsonProcessingException e) {
                    // Not a valid JSON, skip
                }
                
                if (loadedTransactionIsRefundRequest) {
                // This is a refund request
                if (cachedApprover == null) {
                    cachedApprover = loadApproverOrThrow(approverId, userLocale);
                }
                User manager = cachedApprover;
                
                // Atomic request state update with pessimistic locking
                // Lock the transaction entity to prevent concurrent modifications
                entityManager.lock(transaction, jakarta.persistence.LockModeType.PESSIMISTIC_WRITE);
                
                // Re-check status after acquiring lock (double-check locking pattern)
                if (transaction.getRequestStatus() != RequestStatus.OPEN) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                            messageUtil.getMessage("refund.request.not.pending", userLocale));
                }
                
                // Use UTC timezone to match the rest of the application
                OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
                
                if (request.getAction() == RequestStatus.APPROVED) {
                        // Approve - create Refund and RefundItem entities from Transaction.requestData
                        processRefundApproval(transaction, manager, now, userLocale);
                } else if (request.getAction() == RequestStatus.DECLINED) {
                    // Decline - mark as declined but keep request data for audit
                        transaction.setRequestStatus(RequestStatus.DECLINED);
                }
                
                // Set review information (using UTC to match the rest of the application)
                    transaction.setReviewedAt(now);
                    transaction.setReviewedBy(manager);
                    transaction.setRequestComments(request.getComments());
                transaction.setUpdatedAt(now);
                
                transactionRepository.save(transaction);
                
                // CRITICAL: Re-fetch transaction with all relationships after save to ensure lazy-loaded relationships are available
                // This prevents LazyInitializationException when building response
                UUID transactionId = transaction.getId();
                try {
                    transaction = transactionRepository.findByIdWithRelationshipsForRefundResponse(transactionId)
                            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                                    messageUtil.getMessage("transaction.not.found", userLocale)));
                    log.info("Re-fetched transaction {} with all relationships for refund response", transactionId);
                } catch (Exception e) {
                    log.warn("Could not re-fetch transaction {} with relationships: {}", transactionId, e.getMessage());
                    // Fallback to simple findById if the relationship fetch fails
                    try {
                        transaction = transactionRepository.findById(transactionId)
                                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                                        messageUtil.getMessage("transaction.not.found", userLocale)));
                    } catch (Exception ex) {
                        log.error("Failed to re-fetch transaction {} even with fallback: {}", transactionId, ex.getMessage());
                        throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                                messageUtil.getMessage("transaction.not.found", userLocale));
                    }
                }
                
                // Create audit trail for refund request
                try {
                    ActionType actionType = request.getAction() == RequestStatus.APPROVED ? 
                            ActionType.REQUEST_REFUND_APPROVE : ActionType.REQUEST_REFUND_DECLINE;
                    createAuditTrail(
                            manager,
                            actionType,
                            transaction.getRestaurant(),
                            request.getAction(),
                            null, // ipAddress
                            null, // userAgent
                            transaction.getId(),
                            "TRANSACTION",
                            String.format("Refund request %s. Comments: %s", 
                                    request.getAction() == RequestStatus.APPROVED ? "approved" : "declined",
                                    request.getComments() != null ? request.getComments() : "N/A"),
                            transaction.getRequestedBy(), // requestedBy
                            transaction.getRequestedAt(), // requestedAt
                            manager, // reviewedBy
                            now // reviewedAt
                    );
                } catch (Exception e) {
                    log.error("Failed to create audit trail for refund: {}", e.getMessage());
                }
                
                // Send WebSocket notification for cashiers (Windows app doesn't support FCM)
                // The notification will be saved to database by the RabbitMQ listener in restaurant-management service
                try {
                    User requester = transaction.getRequestedBy();
                    if (requester != null) {
                        sendCashierWebSocketNotification("notifyRefundRequestDecision", transaction, requester, 
                                request.getAction() == RequestStatus.APPROVED, request.getComments(), userLocale);
                    }
                } catch (Exception e) {
                    log.error("Failed to send refund request notification: {}", e.getMessage());
                }
                
                // Build response
                String restaurantName = null;
                if (transaction.getRestaurant() != null && transaction.getRestaurant().getTranslations() != null && !transaction.getRestaurant().getTranslations().isEmpty()) {
                    String userLanguage = userLocale.getLanguage();
                    restaurantName = transaction.getRestaurant().getTranslations().stream()
                            .filter(t -> t.getLanguageCode() != null && t.getLanguageCode().startsWith(userLanguage))
                            .findFirst()
                            .map(com.gulfnet.shared_library.entity.RestaurantTranslation::getName)
                            .orElse(transaction.getRestaurant().getTranslations().get(0).getName());
                } else if (transaction.getRestaurant() != null) {
                    restaurantName = "Restaurant";
                }
                
                // Parse refund items from request data
                List<com.gulfnet.shared_library.model.response.dto.RefundRequestResponse.RefundItemResponse> refundItems = new ArrayList<>();
                BigDecimal totalRefundAmount = BigDecimal.ZERO;
                BigDecimal subtotalRefundAmount = BigDecimal.ZERO;
                BigDecimal taxRefundAmount = BigDecimal.ZERO;
                BigDecimal serviceChargeRefundAmount = BigDecimal.ZERO;
                BigDecimal packingChargeRefundAmount = BigDecimal.ZERO;
                BigDecimal discountRefundAmount = BigDecimal.ZERO;
                BigDecimal additionalDiscountRefundAmount = BigDecimal.ZERO;
                String refundReason = null;
                RefundType refundType = null;
                String refundMethod = null;
                
                try {
                    if (transaction.getRequestData() != null) {
                        Map<String, Object> requestData = requestDataObjectMapper.readValue(transaction.getRequestData(), Map.class);
                        
                        // Parse refund items
                        // Parse orderedItems
                        if (requestData.containsKey("orderedItems")) {
                            @SuppressWarnings("unchecked")
                            List<Map<String, Object>> orderedItemsList = (List<Map<String, Object>>) requestData.get("orderedItems");
                            if (orderedItemsList != null && !orderedItemsList.isEmpty()) {
                                for (Map<String, Object> item : orderedItemsList) {
                                    UUID orderedItemId = UUID.fromString((String) item.get("orderedItemId"));
                                    String rawItemName = item.containsKey("itemName") ? (String) item.get("itemName") : null;
                                    String itemName = resolveRefundLineDisplayName(rawItemName, orderedItemId, false, userLocale);
                                    RefundRequestResponse.RefundItemResponse refundItem = RefundRequestResponse.RefundItemResponse.builder()
                                            .itemId(orderedItemId)
                                            .itemType("ITEM")
                                            .itemName(itemName)
                                            .quantity(item.containsKey("refundQuantity") ? 
                                                    ((Number) item.get("refundQuantity")).intValue() : 
                                                    (item.containsKey("quantity") ? 
                                                            ((Number) item.get("quantity")).intValue() : 1))
                                                    .refundAmount(new BigDecimal(((Number) item.get("refundAmount")).toString()))
                                            .build();
                                    refundItems.add(refundItem);
                                }
                            }
                        }
                        
                        // Parse orderedCombos
                        if (requestData.containsKey("orderedCombos")) {
                            @SuppressWarnings("unchecked")
                            List<Map<String, Object>> orderedCombosList = (List<Map<String, Object>>) requestData.get("orderedCombos");
                            if (orderedCombosList != null && !orderedCombosList.isEmpty()) {
                                for (Map<String, Object> combo : orderedCombosList) {
                                    UUID orderedComboId = UUID.fromString((String) combo.get("orderedComboId"));
                                    String rawComboName = combo.containsKey("comboName") ? (String) combo.get("comboName") : null;
                                    String comboName = resolveRefundLineDisplayName(rawComboName, orderedComboId, true, userLocale);
                                    RefundRequestResponse.RefundItemResponse refundItem = RefundRequestResponse.RefundItemResponse.builder()
                                            .itemId(orderedComboId)
                                            .itemType("COMBO")
                                            .itemName(comboName)
                                            .quantity(combo.containsKey("refundQuantity") ? 
                                                    ((Number) combo.get("refundQuantity")).intValue() : 
                                                    (combo.containsKey("quantity") ? 
                                                            ((Number) combo.get("quantity")).intValue() : 1))
                                                            .refundAmount(new BigDecimal(((Number) combo.get("refundAmount")).toString()))
                                            .build();
                                    refundItems.add(refundItem);
                                }
                            }
                        }
                        
                        // Parse all refund amount breakdown fields from request_data
                        totalRefundAmount = requestData.containsKey("totalRefundAmount") 
                                ? new BigDecimal(((Number) requestData.get("totalRefundAmount")).toString())
                                : (requestData.containsKey("refundAmount") 
                                        ? new BigDecimal(((Number) requestData.get("refundAmount")).toString())
                                        : BigDecimal.ZERO);
                        subtotalRefundAmount = requestData.containsKey("subtotalRefundAmount") 
                                ? new BigDecimal(((Number) requestData.get("subtotalRefundAmount")).toString())
                                : BigDecimal.ZERO;
                        taxRefundAmount = requestData.containsKey("taxRefundAmount") 
                                ? new BigDecimal(((Number) requestData.get("taxRefundAmount")).toString())
                                : BigDecimal.ZERO;
                        serviceChargeRefundAmount = requestData.containsKey("serviceChargeRefundAmount") 
                                ? new BigDecimal(((Number) requestData.get("serviceChargeRefundAmount")).toString())
                                : BigDecimal.ZERO;
                        packingChargeRefundAmount = requestData.containsKey("packingChargeRefundAmount") 
                                ? new BigDecimal(((Number) requestData.get("packingChargeRefundAmount")).toString())
                                : BigDecimal.ZERO;
                        discountRefundAmount = requestData.containsKey("discountRefundAmount") 
                                ? new BigDecimal(((Number) requestData.get("discountRefundAmount")).toString())
                                : BigDecimal.ZERO;
                        additionalDiscountRefundAmount = requestData.containsKey("additionalDiscountRefundAmount") 
                                ? new BigDecimal(((Number) requestData.get("additionalDiscountRefundAmount")).toString())
                                : BigDecimal.ZERO;
                        
                        // Parse refundType and refundMethod
                        String refundTypeStr = (String) requestData.get("refundType");
                        if (refundTypeStr != null) {
                            try {
                                refundType = RefundType.valueOf(refundTypeStr);
                            } catch (IllegalArgumentException e) {
                                log.warn("Invalid refundType in request_data: {}", refundTypeStr);
                            }
                        }
                        refundMethod = requestData.containsKey("paymentMethod") ? 
                                (String) requestData.get("paymentMethod") : transaction.getPaymentMethod();
                        
                        refundReason = (String) requestData.get("refundReason");
                    }
                } catch (JsonProcessingException e) {
                    log.warn("Error parsing refund request data: {}", e.getMessage());
                }
                
                // Get role name for requestedBy user
                String requestedByRole = null;
                if (transaction.getRequestedBy() != null && transaction.getRequestedBy().getRoleId() != null) {
                    var role = roleRepository.findById(transaction.getRequestedBy().getRoleId()).orElse(null);
                    if (role != null) {
                        requestedByRole = role.getName();
                    }
                }
                
                RefundRequestResponse refundResponse = RefundRequestResponse.builder()
                        .transactionId(transaction.getId())
                        .orderId(transaction.getOrder() != null ? transaction.getOrder().getId() : null)
                        .orderNumber(transaction.getOrder() != null ? transaction.getOrder().getOrderNumber() : null)
                        .transactionNumber(transaction.getTransactionNumber())
                        .paymentMethod(transaction.getPaymentMethod())
                        .transactionAmount(transaction.getTransactionAmount())
                        .refundType(refundType)
                        .refundMethod(refundMethod)
                        .totalRefundAmount(totalRefundAmount)
                        .subtotalRefundAmount(subtotalRefundAmount)
                        .taxRefundAmount(taxRefundAmount)
                        .serviceChargeRefundAmount(serviceChargeRefundAmount)
                        .packingChargeRefundAmount(packingChargeRefundAmount)
                        .discountRefundAmount(discountRefundAmount)
                        .additionalDiscountRefundAmount(additionalDiscountRefundAmount)
                        .refundItems(refundItems)
                        .refundReason(refundReason)
                        .requestStatus(transaction.getRequestStatus())
                        .requestedAt(transaction.getRequestedAt() != null ? transaction.getRequestedAt().toLocalDateTime() : null)
                        .requestedBy(transaction.getRequestedBy() != null ? transaction.getRequestedBy().getId() : null)
                        .requestedByName(transaction.getRequestedBy() != null ? 
                            transaction.getRequestedBy().getFirstName() + " " + transaction.getRequestedBy().getLastName() : null)
                        .requestedByRole(requestedByRole)
                        .reviewedAt(transaction.getReviewedAt() != null ? ((OffsetDateTime) transaction.getReviewedAt()).toLocalDateTime() : null)
                        .reviewedBy(transaction.getReviewedBy() != null ? transaction.getReviewedBy().getId() : null)
                        .reviewedByName(transaction.getReviewedBy() != null ? 
                            transaction.getReviewedBy().getFirstName() + " " + transaction.getReviewedBy().getLastName() : null)
                        .comments(transaction.getRequestComments())
                        .restaurantId(transaction.getRestaurant() != null ? transaction.getRestaurant().getId() : null)
                        .restaurantName(restaurantName)
                        .build();
                
                RequestApprovalResponse approvalResponse = RequestApprovalResponse.builder()
                        .requestType(messageUtil.getMessage("request.type.refund", userLocale))
                        .profileUpdateResponse(null)
                        .additionalDiscountResponse(null)
                        .tableSectionResponse(null)
                        .refundResponse(refundResponse)
                        .build();
                
                String messageKey = request.getAction() == RequestStatus.APPROVED ? 
                        "refund.request.approved" : "refund.request.declined";
                
                // Notify all active managers about the request resolution
                try {
                    UUID restaurantId = transaction.getRestaurant() != null ? transaction.getRestaurant().getId() : null;
                    if (restaurantId != null) {
                        List<User> activeManagers = findActiveManagersForRestaurant(restaurantId);
                        notifyManagersAboutRequestResolution(activeManagers, manager, transaction, messageUtil.getMessage("request.type.refund", userLocale), 
                                request.getAction() == RequestStatus.APPROVED, request.getComments(), userLocale);
                    }
                } catch (Exception e) {
                    log.error("Failed to notify managers about refund request resolution: {}", e.getMessage(), e);
                }
                
                return ResponseDto.<RequestApprovalResponse>builder()
                        .message(messageUtil.getMessage(messageKey, userLocale))
                        .data(approvalResponse)
                        .build();
                }
            }
        }
        
        // Try to find as transaction cancellation request (check if it's a transaction with a cancellation request)
        // Note: This check should only run if the transaction was not already handled as refund above.
        if (loadedTransaction != null && !loadedTransactionIsRefundRequest) {
            Transaction transaction = loadedTransaction;
            if (transaction.getRequestStatus() == RequestStatus.OPEN) {
                // Check if it's a cancellation request (not a refund)
                // Refund requests have "requestType":"REFUND" in requestData, cancellation requests don't
                boolean isRefundRequest = false;
                if (transaction.getRequestData() != null) {
                    try {
                        @SuppressWarnings("unchecked")
                        Map<String, Object> requestData = requestDataObjectMapper.readValue(transaction.getRequestData(), Map.class);
                        isRefundRequest = "REFUND".equals(requestData.get("requestType"));
                    } catch (JsonProcessingException e) {
                        // Invalid JSON, treat as cancellation request
                    }
                }
                
                if (!isRefundRequest) {
                    // This is a transaction cancellation request
                    // Only MANAGER can approve/decline transaction cancellation requests (HQ_ADMIN cannot)
                    if (!"MANAGER".equals(managerRole)) {
                        throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                                messageUtil.getMessage("transaction.cancellation.request.unauthorized", userLocale));
                    }
                    
                    if (cachedApprover == null) {
                        cachedApprover = loadApproverOrThrow(approverId, userLocale);
                    }
                    User manager = cachedApprover;
                    
                    // MANAGER can only approve requests from their own restaurant
                    if (manager.getRestaurantId() != null && transaction.getRestaurant() != null) {
                        if (!manager.getRestaurantId().equals(transaction.getRestaurant().getId())) {
                            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                                    messageUtil.getMessage("transaction.cancellation.request.unauthorized.restaurant", userLocale));
                        }
                    }
                    
                    // Atomic request state update with pessimistic locking
                    // Lock the transaction entity to prevent concurrent modifications
                    entityManager.lock(transaction, jakarta.persistence.LockModeType.PESSIMISTIC_WRITE);
                    
                    // Re-check status after acquiring lock (double-check locking pattern)
                    if (transaction.getRequestStatus() != RequestStatus.OPEN) {
                        throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                                messageUtil.getMessage("transaction.cancellation.request.not.pending", userLocale));
                    }
                    
                    // Check if transaction has been closed (COMPLETED) by cashier
                    // If so, manager cannot act on the cancellation request
                    if (transaction.getTransactionStatus() == TransactionStatus.COMPLETED) {
                        throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                                messageUtil.getMessage("transaction.cancellation.request.transaction.closed", userLocale));
                    }
                    
                    // Use UTC timezone to match the rest of the application
                    OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
                    TransactionStatus transactionStatusBeforeTransactionCancelApproval = transaction.getTransactionStatus();
                    
                    if (request.getAction() == RequestStatus.APPROVED) {
                        // Approve cancellation - set transaction status to CANCELED
                        transaction.setTransactionStatus(TransactionStatus.CANCELED);
                        transaction.setRequestStatus(RequestStatus.APPROVED);
                        
                        // Cancel all orderedItems and orderedCombos in the order
                        Order order = transaction.getOrder();
                        if (order != null) {
                            UUID orderId = order.getId();
                            
                            // Cancel all orderedItems (only regular items, not combo items)
                            List<OrderedItem> orderedItems = orderedItemRepository.findByOrderId(orderId).stream()
                                    .filter(item -> item.getOrderedCombo() == null) // Only regular items
                                    .filter(item -> item.getItemStatus() != com.gulfnet.shared_library.enums.ItemStatus.CANCELED) // Skip already canceled
                                    .collect(Collectors.toList());
                            
                            for (OrderedItem item : orderedItems) {
                                // Capture status before cancellation for wastage reporting
                                // Only set wastage_source_status if current status is COOKING, READY, or SERVED
                                if (item.getItemStatus() != null 
                                        && item.getItemStatus() != com.gulfnet.shared_library.enums.ItemStatus.CANCELED
                                        && (item.getItemStatus() == com.gulfnet.shared_library.enums.ItemStatus.COOKING 
                                            || item.getItemStatus() == com.gulfnet.shared_library.enums.ItemStatus.READY 
                                            || item.getItemStatus() == com.gulfnet.shared_library.enums.ItemStatus.SERVED)
                                        && item.getWastageSourceStatus() == null) {
                                    item.setWastageSourceStatus(item.getItemStatus());
                                }
                                item.setItemStatus(com.gulfnet.shared_library.enums.ItemStatus.CANCELED);
                                item.setUpdatedAt(now);
                                item.setUpdatedBy(manager);
                                orderedItemRepository.save(item);
                            }
                            
                            // Cancel all orderedCombos
                            List<OrderedCombo> orderedCombos = orderedComboRepository.findByOrderId(orderId).stream()
                                    .filter(combo -> combo.getItemStatus() != com.gulfnet.shared_library.enums.ItemStatus.CANCELED) // Skip already canceled
                                    .collect(Collectors.toList());
                            
                            for (OrderedCombo combo : orderedCombos) {
                                // Capture status before cancellation for wastage reporting
                                // Only set wastage_source_status if current status is COOKING, READY, or SERVED
                                if (combo.getItemStatus() != null 
                                        && combo.getItemStatus() != com.gulfnet.shared_library.enums.ItemStatus.CANCELED
                                        && (combo.getItemStatus() == com.gulfnet.shared_library.enums.ItemStatus.COOKING 
                                            || combo.getItemStatus() == com.gulfnet.shared_library.enums.ItemStatus.READY 
                                            || combo.getItemStatus() == com.gulfnet.shared_library.enums.ItemStatus.SERVED)
                                        && combo.getWastageSourceStatus() == null) {
                                    combo.setWastageSourceStatus(combo.getItemStatus());
                                }
                                combo.setItemStatus(com.gulfnet.shared_library.enums.ItemStatus.CANCELED);
                                combo.setUpdatedAt(now);
                                combo.setUpdatedBy(manager);
                                orderedComboRepository.save(combo);
                                
                                // Also cancel all items within this combo
                                try {
                                    List<OrderedItem> comboItems = orderedItemRepository.findByOrderedComboId(combo.getId());
                                    if (comboItems != null && !comboItems.isEmpty()) {
                                        for (OrderedItem comboItem : comboItems) {
                                            if (comboItem == null) {
                                                continue;
                                            }
                                            if (comboItem.getItemStatus() == com.gulfnet.shared_library.enums.ItemStatus.CANCELED) {
                                                continue;
                                            }
                                            // Capture status before cancellation for wastage reporting
                                            if (comboItem.getItemStatus() != null
                                                    && (comboItem.getItemStatus() == com.gulfnet.shared_library.enums.ItemStatus.COOKING
                                                    || comboItem.getItemStatus() == com.gulfnet.shared_library.enums.ItemStatus.READY
                                                    || comboItem.getItemStatus() == com.gulfnet.shared_library.enums.ItemStatus.SERVED)
                                                    && comboItem.getWastageSourceStatus() == null) {
                                                comboItem.setWastageSourceStatus(comboItem.getItemStatus());
                                            }
                                            comboItem.setItemStatus(com.gulfnet.shared_library.enums.ItemStatus.CANCELED);
                                            comboItem.setUpdatedAt(now);
                                            comboItem.setUpdatedBy(manager);
                                        }
                                        orderedItemRepository.saveAll(comboItems);
                                    }
                                } catch (Exception e) {
                                    log.error("Failed to cancel combo items for combo {} during transaction cancellation approval: {}", combo.getId(), e.getMessage(), e);
                                }
                            }
                            
                            // Set order status to CANCELED when transaction is canceled
                            order.setOrderStatus(OrderStatus.CANCELED);
                            order.setUpdatedAt(now);
                            order.setUpdatedBy(manager);
                            orderRepository.save(order);
                            
                            if (!shouldSkipOrderAmountAdjustmentOnCancellation(order, transactionStatusBeforeTransactionCancelApproval)) {
                                try {
                                    recalculateOrderAfterOrderCancellation(order, userLocale, manager, now);
                                    log.info("Order {} monetary totals zeroed after transaction cancellation approval (policy: adjust amounts)", order.getId());
                                } catch (Exception e) {
                                    log.error("Failed to zero order totals after transaction cancellation approval: {}", e.getMessage(), e);
                                }
                            } else {
                                log.info("Order {} monetary totals unchanged after transaction cancellation approval (same skip policy as item no-deduction)",
                                        order.getId());
                            }
                        }
                    } else if (request.getAction() == RequestStatus.DECLINED) {
                        // Decline cancellation - keep current status
                        transaction.setRequestStatus(RequestStatus.DECLINED);
                    }
                    
                    // Set review information
                    transaction.setReviewedAt(now);
                    transaction.setReviewedBy(manager);
                    transaction.setRequestComments(request.getComments());
                    transaction.setUpdatedAt(now);
                    
                    transactionRepository.save(transaction);
                    
                    // ==================== REAL-TIME HQ ALERT EVALUATION ====================
                    // Check if cancellation thresholds are breached after this transaction cancellation approval.
                    // Must run AFTER transaction commits so the REQUIRES_NEW alert transaction can see the data.
                    if (request.getAction() == RequestStatus.APPROVED) {
                        try {
                            Restaurant alertRestaurant = transaction.getRestaurant();
                            if (alertRestaurant != null) {
                                triggerAlertEvaluationAfterCommit(alertRestaurant, userLocale, "transaction cancellation approval");
                            } else {
                                log.warn("Restaurant is null on transaction {} - cannot trigger alert evaluation", transaction.getId());
                            }
                        } catch (Exception e) {
                            log.error("Failed to trigger alert evaluation after transaction cancellation approval: {}", e.getMessage(), e);
                        }
                    }

                    if (request.getAction() == RequestStatus.APPROVED && transaction.getOrder() != null) {
                        takeawaySessionTableReleaseService.maybeExpireTakeawaySessionWhenOrderServedAndTransactionCompleted(
                                transaction.getOrder().getId(), null);
                    }
                    
                    // Create audit trail for transaction cancellation request (manager entry).
                    // Use REQUEST_CANCEL_TRANSACTION_APPROVE/DECLINE so the entry is visible in manager audit trail
                    // (ActionType.CANCELLATION is excluded from manager view).
                    try {
                        ActionType cancelActionType = request.getAction() == RequestStatus.APPROVED
                                ? ActionType.REQUEST_CANCEL_TRANSACTION_APPROVE
                                : ActionType.REQUEST_CANCEL_TRANSACTION_DECLINE;
                        createAuditTrail(
                                manager,
                                cancelActionType,
                                transaction.getRestaurant(),
                                request.getAction(),
                                null, // ipAddress
                                null, // userAgent
                                transaction.getId(),
                                "TRANSACTION",
                                String.format("Transaction cancellation request %s. Comments: %s",
                                        request.getAction() == RequestStatus.APPROVED ? "approved" : "declined",
                                        request.getComments() != null ? request.getComments() : "N/A"),
                                transaction.getRequestedBy(), // requestedBy
                                transaction.getRequestedAt(), // requestedAt
                                manager, // reviewedBy
                                now // reviewedAt
                        );
                    } catch (Exception e) {
                        log.error("Failed to create audit trail for transaction cancellation: {}", e.getMessage());
                    }
                    
                    // Send WebSocket notification for cashiers (Windows app doesn't support FCM)
                    // Note: Notification is saved to database by restaurant-management service after receiving RabbitMQ message
                    // to avoid duplicate notifications and ensure complete data (order number, table number) is available
                    try {
                        User requester = transaction.getRequestedBy();
                        if (requester != null) {
                            // Reload transaction with order and table relationships to ensure all data is available for notification
                            UUID transactionId = transaction.getId();
                            Transaction transactionWithRelations = transactionRepository.findByIdWithOrderAndTable(transactionId)
                                    .orElse(transaction); // Fallback to original if reload fails
                            
                            // Send WebSocket notification for cashiers (Windows app doesn't support FCM)
                            // Use the reloaded transaction to ensure order and table relationships are available
                            sendCashierWebSocketNotification("notifyCancellationRequestDecisionForCashier", transactionWithRelations, requester, 
                                    request.getAction() == RequestStatus.APPROVED, request.getComments(), userLocale);
                        }
                    } catch (Exception e) {
                        log.error("Failed to send transaction cancellation request notification: {}", e.getMessage());
                    }
                    
                    // Send WebSocket notifications to all three topics (item-status, order-status, transaction-status) for transaction cancellation (both approved and declined)
                    try {
                        if (transaction.getRestaurant() != null && transaction.getRestaurant().getId() != null) {
                            UUID restaurantId = transaction.getRestaurant().getId();
                            // Send to item-status topic
                            sendItemStatusWebSocketRequestDecisionNotification(
                                    restaurantId,
                                    request.getAction(),
                                    "TRANSACTION_CANCELLATION",
                                    transaction.getId(),
                                    userLocale
                            );
                            // Send to order-status topic
                            sendOrderStatusWebSocketRequestDecisionNotification(
                                    restaurantId,
                                    request.getAction(),
                                    "TRANSACTION_CANCELLATION",
                                    transaction.getId(),
                                    userLocale
                            );
                            // Send to transaction-status topic
                            sendTransactionStatusWebSocketRequestDecisionNotification(
                                    restaurantId,
                                    request.getAction(),
                                    "TRANSACTION_CANCELLATION",
                                    transaction.getId(),
                                    userLocale
                            );
                        }
                    } catch (Exception e) {
                        log.warn("Failed to send WebSocket notifications for transaction cancellation: {}", e.getMessage());
                    }
                    
                    // If cancellation was approved, notify waiters assigned to the table
                    if (request.getAction() == RequestStatus.APPROVED) {
                        // Notify waiters assigned to the table
                        try {
                            // Reload transaction to ensure order and table relationships are accessible
                            // The transaction was loaded earlier, but we need to ensure relationships are loaded
                            UUID transactionId = transaction.getId();
                            
                            // Try to access order - if it triggers lazy loading, it should work within the transaction
                            Order order = transaction.getOrder();
                            
                            // If order is null, try reloading the transaction
                            if (order == null) {
                                Optional<Transaction> reloadedOpt = transactionRepository.findById(transactionId);
                                if (reloadedOpt.isPresent()) {
                                    transaction = reloadedOpt.get();
                                    order = transaction.getOrder();
                                }
                            }
                            if (order != null && order.getRestaurantTable() != null) {
                                RestaurantTable table = order.getRestaurantTable();
                                log.info("Notifying waiters about approved transaction cancellation for transaction: {}, order: {}, table: {}", 
                                        transaction.getId(), order.getId(), table.getId());
                                
                                // Find waiters assigned to the table
                                List<TableAssignment> tableAssignments = tableAssignmentRepository
                                        .findByRestaurantTableIdAndUnassignedAtIsNullWithWaiter(table.getId());
                                
                                if (tableAssignments != null && !tableAssignments.isEmpty()) {
                                    log.info("Found {} active waiter assignment(s) for table {}", tableAssignments.size(), table.getId());
                                    
                                    // Collect unique waiters (in case same waiter is assigned multiple times)
                                    Set<User> uniqueWaiters = new HashSet<>();
                                    for (TableAssignment assignment : tableAssignments) {
                                        User waiter = assignment.getWaiter();
                                        if (waiter != null) {
                                            uniqueWaiters.add(waiter);
                                        }
                                    }
                                    
                                    if (!uniqueWaiters.isEmpty()) {
                                        log.info("Notifying {} unique waiter(s) about transaction cancellation for transaction {}", 
                                                uniqueWaiters.size(), transaction.getId());
                                        
                                        // Notify all waiters assigned to the table
                                        for (User waiter : uniqueWaiters) {
                                            try {
                                                log.info("Sending transaction cancellation notification to waiter {} ({} {}) for transaction {}", 
                                                        waiter.getId(), waiter.getFirstName(), waiter.getLastName(), transaction.getId());
                                                
                                                // Send WebSocket notification for waiters using reflection (same pattern as item/combo cancellation)
                                                sendWaiterTransactionCancellationNotification(transaction, waiter, userLocale);
                                                
                                                log.info("Successfully sent transaction cancellation notification to waiter {} for transaction {}", 
                                                        waiter.getId(), transaction.getId());
                                            } catch (Exception e) {
                                                log.error("Failed to send transaction cancellation notification to waiter {} for transaction {}: {}", 
                                                        waiter.getId(), transaction.getId(), e.getMessage(), e);
                                            }
                                        }
                                    } else {
                                        log.warn("No valid waiters found in table assignments for table {} and transaction {}", 
                                                table.getId(), transaction.getId());
                                    }
                                } else {
                                    log.warn("No active waiter assignment found for table {} (table order: {}) for transaction {}", 
                                            table.getId(), table.getTableOrder(), transaction.getId());
                                }
                            } else {
                                log.warn("Transaction {} has no order or table associated, cannot notify waiters. Order: {}, Table: {}", 
                                        transaction.getId(), order != null ? order.getId() : "null", 
                                        order != null && order.getRestaurantTable() != null ? order.getRestaurantTable().getId() : "null");
                            }
                        } catch (Exception e) {
                            log.error("Failed to notify waiters about transaction cancellation for transaction {}: {}", 
                                    transaction.getId(), e.getMessage(), e);
                        }
                    }
                    
                    // Build response
                    TransactionCancellationRequestResponse transactionCancellationResponse = buildTransactionCancellationRequestResponse(transaction, userLocale);
                    
                    RequestApprovalResponse approvalResponse = RequestApprovalResponse.builder()
                            .requestType(messageUtil.getMessage("request.type.transaction.cancellation", userLocale))
                            .profileUpdateResponse(null)
                            .additionalDiscountResponse(null)
                            .tableSectionResponse(null)
                            .refundResponse(null)
                            .itemCancellationResponse(null)
                            .comboCancellationResponse(null)
                            .transactionCancellationResponse(transactionCancellationResponse)
                            .build();
                    
                    String messageKey = request.getAction() == RequestStatus.APPROVED ?
                            "transaction.cancellation.request.approved" : "transaction.cancellation.request.declined";
                    
                    // Send WebSocket notifications to all three topics (item-status, order-status, transaction-status) for transaction cancellation (both approved and declined)
                    try {
                        UUID restaurantId = transaction.getRestaurant() != null ? transaction.getRestaurant().getId() : null;
                        if (restaurantId != null) {
                            log.info("Sending WebSocket notifications for transaction cancellation request {}: transactionId={}, restaurantId={}, action={}", 
                                    request.getAction(), transaction.getId(), restaurantId, request.getAction());
                            // Send to item-status topic
                            sendItemStatusWebSocketRequestDecisionNotification(
                                    restaurantId,
                                    request.getAction(),
                                    "TRANSACTION_CANCELLATION",
                                    transaction.getId(),
                                    userLocale
                            );
                            // Send to order-status topic
                            sendOrderStatusWebSocketRequestDecisionNotification(
                                    restaurantId,
                                    request.getAction(),
                                    "TRANSACTION_CANCELLATION",
                                    transaction.getId(),
                                    userLocale
                            );
                            // Send to transaction-status topic
                            sendTransactionStatusWebSocketRequestDecisionNotification(
                                    restaurantId,
                                    request.getAction(),
                                    "TRANSACTION_CANCELLATION",
                                    transaction.getId(),
                                    userLocale
                            );
                            log.info("Completed sending WebSocket notifications for transaction cancellation: transactionId={}, restaurantId={}, action={}", 
                                    transaction.getId(), restaurantId, request.getAction());
                        } else {
                            log.warn("Cannot send WebSocket notifications for transaction cancellation: restaurantId is null for transaction {}", transaction.getId());
                        }
                    } catch (Exception e) {
                        log.error("Failed to send WebSocket notifications for transaction cancellation: transactionId={}, error={}", 
                                transaction.getId(), e.getMessage(), e);
                    }
                    
                    // Notify all active managers about the request resolution
                    try {
                        UUID restaurantId = transaction.getRestaurant() != null ? transaction.getRestaurant().getId() : null;
                        if (restaurantId != null) {
                            List<User> activeManagers = findActiveManagersForRestaurant(restaurantId);
                            notifyManagersAboutRequestResolution(activeManagers, manager, transaction, messageUtil.getMessage("request.type.transaction.cancellation", userLocale), 
                                    request.getAction() == RequestStatus.APPROVED, request.getComments(), userLocale);
                        }
                    } catch (Exception e) {
                        log.error("Failed to notify managers about transaction cancellation request resolution: {}", e.getMessage(), e);
                    }
                    
                    return ResponseDto.<RequestApprovalResponse>builder()
                            .message(messageUtil.getMessage(messageKey, userLocale))
                            .data(approvalResponse)
                            .build();
                }
            }
        }
        
        // Try to find as item cancellation request
        Optional<OrderedItem> orderedItemOptional = orderedItemRepository.findById(requestId);
        if (orderedItemOptional.isPresent()) {
            OrderedItem orderedItem = orderedItemOptional.get();
            if (orderedItem.getCancellationRequestStatus() == RequestStatus.OPEN) {
                // This is an item cancellation request
                if (cachedApprover == null) {
                    cachedApprover = loadApproverOrThrow(approverId, userLocale);
                }
                User manager = cachedApprover;
                
                // Atomic request state update with pessimistic locking
                // Lock the orderedItem entity to prevent concurrent modifications
                entityManager.lock(orderedItem, jakarta.persistence.LockModeType.PESSIMISTIC_WRITE);
                
                // Re-check status after acquiring lock (double-check locking pattern)
                if (orderedItem.getCancellationRequestStatus() != RequestStatus.OPEN) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                            messageUtil.getMessage("item.cancellation.request.not.pending", userLocale));
                }
                
                // Use UTC timezone to match the rest of the application
                OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
                
                // CRITICAL: Get orderId before any entity operations that might clear the entity manager
                UUID orderId = orderedItem.getOrder() != null ? orderedItem.getOrder().getId() : null;
                
                if (request.getAction() == RequestStatus.APPROVED) {
                    // Approve - set item status to CANCELED
                    // Capture status before cancellation for wastage reporting
                    // Only set wastage_source_status if current status is COOKING, READY, or SERVED
                    if (orderedItem.getItemStatus() != null 
                            && orderedItem.getItemStatus() != com.gulfnet.shared_library.enums.ItemStatus.CANCELED
                            && (orderedItem.getItemStatus() == com.gulfnet.shared_library.enums.ItemStatus.COOKING 
                                || orderedItem.getItemStatus() == com.gulfnet.shared_library.enums.ItemStatus.READY 
                                || orderedItem.getItemStatus() == com.gulfnet.shared_library.enums.ItemStatus.SERVED)
                            && orderedItem.getWastageSourceStatus() == null) {
                        orderedItem.setWastageSourceStatus(orderedItem.getItemStatus());
                    }
                    orderedItem.setItemStatus(com.gulfnet.shared_library.enums.ItemStatus.CANCELED);
                    orderedItem.setCancellationRequestStatus(RequestStatus.APPROVED);
                } else if (request.getAction() == RequestStatus.DECLINED) {
                    // Decline - keep current status
                    orderedItem.setCancellationRequestStatus(RequestStatus.DECLINED);
                }
                
                // Set review information (using UTC to match the rest of the application)
                orderedItem.setCancellationReviewedAt(now);
                orderedItem.setCancellationReviewedBy(manager);
                orderedItem.setCancellationComments(request.getComments());
                orderedItem.setUpdatedAt(now);
                orderedItem.setUpdatedBy(manager);
                
                // CRITICAL: Ensure entity is managed before saving
                if (!entityManager.contains(orderedItem)) {
                    orderedItem = entityManager.merge(orderedItem);
                }
                
                // CRITICAL: Save and flush to ensure changes are persisted to database
                // This must happen BEFORE any recalculation that might clear the entity manager
                orderedItem = orderedItemRepository.save(orderedItem);
                orderedItemRepository.flush(); // Force immediate write to database
                
                log.info("Item cancellation request {} - Action: {}, Item status: {}, Request status: {} (after save and flush)", 
                        orderedItem.getId(), request.getAction(), orderedItem.getItemStatus(), orderedItem.getCancellationRequestStatus());
                
                // Double-check and force save if needed
                if (request.getAction() == RequestStatus.APPROVED) {
                    boolean needsResave = false;
                    if (orderedItem.getItemStatus() != com.gulfnet.shared_library.enums.ItemStatus.CANCELED) {
                        log.error("CRITICAL: Item status was not set to CANCELED! Current status: {}. Setting it now.", orderedItem.getItemStatus());
                        if (orderedItem.getWastageSourceStatus() == null && orderedItem.getItemStatus() != null 
                                && orderedItem.getItemStatus() != com.gulfnet.shared_library.enums.ItemStatus.CANCELED) {
                            orderedItem.setWastageSourceStatus(orderedItem.getItemStatus());
                        }
                        orderedItem.setItemStatus(com.gulfnet.shared_library.enums.ItemStatus.CANCELED);
                        needsResave = true;
                    }
                    if (orderedItem.getCancellationRequestStatus() != RequestStatus.APPROVED) {
                        log.error("CRITICAL: Cancellation request status was not set to APPROVED! Current status: {}. Setting it now.", orderedItem.getCancellationRequestStatus());
                        orderedItem.setCancellationRequestStatus(RequestStatus.APPROVED);
                        needsResave = true;
                    }
                    if (needsResave) {
                        orderedItem = orderedItemRepository.save(orderedItem);
                        orderedItemRepository.flush();
                        log.info("Item cancellation request {} - Re-saved with correct statuses", orderedItem.getId());
                    }
                    
                    // Auto-adjust GET items if this is a BUY item in a BXGY discount
                    UUID itemId = orderedItem.getId();
                    if (orderedItem.getDiscountApplicationId() != null && 
                        orderedItem.getBxgyRole() == BxgyRole.BUY &&
                        orderedItem.getDiscountId() != null) {
                        log.info("BUY item {} cancelled (via cancellation request approval) - adjusting related GET items with discount_application_id: {}", 
                            itemId, orderedItem.getDiscountApplicationId());
                        
                        adjustBxgyGetItemsAfterBuyCancellation(
                            orderedItem.getDiscountApplicationId(),
                            orderedItem.getDiscountId(),
                            manager,
                            true,
                            userLocale,
                            now
                        );
                        
                        // Flush to ensure GET items are persisted
                        orderedItemRepository.flush();
                    }
                    
                    // Recalculate order totals after item cancellation unless same skip policy as item no-deduction
                    if (orderId != null) {
                        try {
                            Optional<Order> orderOpt = orderRepository.findById(orderId);
                            TransactionStatus txStatus = transactionRepository.findByOrderId(orderId)
                                    .map(Transaction::getTransactionStatus)
                                    .orElse(null);
                            if (orderOpt.isPresent()
                                    && !shouldSkipOrderAmountAdjustmentOnCancellation(orderOpt.get(), txStatus)) {
                                recalculateOrderAfterItemCancellation(orderId, userLocale, manager);
                            } else if (orderOpt.isPresent()) {
                                log.info("Skipping order totals recalculation for item cancellation approval - order {} (same policy as item no-deduction)",
                                        orderId);
                            }
                        } catch (Exception e) {
                            log.error("Failed to recalculate order after item cancellation: {}", e.getMessage(), e);
                            // Don't throw - item cancellation is already saved, recalculation failure shouldn't rollback
                        }
                    }
                    if (orderId != null) {
                        takeawaySessionTableReleaseService.maybeExpireTakeawaySessionWhenOrderServedAndTransactionCompleted(orderId, null);
                    }
                }
                
                // CRITICAL: Initialize lazy-loaded relationships before recalculation (which clears entity manager)
                // This prevents LazyInitializationException when building response later
                try {
                    if (orderedItem.getItem() != null) {
                        org.hibernate.Hibernate.initialize(orderedItem.getItem());
                        if (orderedItem.getItem().getTranslations() != null) {
                            org.hibernate.Hibernate.initialize(orderedItem.getItem().getTranslations());
                        }
                    }
                    if (orderedItem.getOrder() != null) {
                        org.hibernate.Hibernate.initialize(orderedItem.getOrder());
                    }
                    if (orderedItem.getCancellationRequestedBy() != null) {
                        org.hibernate.Hibernate.initialize(orderedItem.getCancellationRequestedBy());
                    }
                } catch (Exception e) {
                    log.warn("Could not initialize lazy-loaded relationships for orderedItem {}: {}", orderedItem.getId(), e.getMessage());
                }
                
                // NOTE: Order recalculation removed - item cancellation works correctly without modifying the order table
                // The order totals will be recalculated by other services when needed
                
                // CRITICAL: Re-fetch orderedItem after recalculation with all relationships loaded
                // The recalculation may have cleared the entity manager, so we need fresh entity with all relationships
                UUID orderedItemId = orderedItem.getId();
                try {
                    // Use simple findById first to avoid query generation issues after entity manager clear
                    orderedItem = orderedItemRepository.findById(orderedItemId)
                            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                                    messageUtil.getMessage("ordered.item.not.found", userLocale)));
                    
                    // Manually initialize relationships to avoid lazy loading issues
                    try {
                        if (orderedItem.getItem() != null) {
                            org.hibernate.Hibernate.initialize(orderedItem.getItem());
                            if (orderedItem.getItem().getTranslations() != null) {
                                org.hibernate.Hibernate.initialize(orderedItem.getItem().getTranslations());
                            }
                        }
                        if (orderedItem.getOrder() != null) {
                            org.hibernate.Hibernate.initialize(orderedItem.getOrder());
                            if (orderedItem.getOrder().getRestaurant() != null) {
                                org.hibernate.Hibernate.initialize(orderedItem.getOrder().getRestaurant());
                                if (orderedItem.getOrder().getRestaurant().getTranslations() != null) {
                                    org.hibernate.Hibernate.initialize(orderedItem.getOrder().getRestaurant().getTranslations());
                                }
                            }
                        }
                        if (orderedItem.getCancellationRequestedBy() != null) {
                            org.hibernate.Hibernate.initialize(orderedItem.getCancellationRequestedBy());
                        }
                        if (orderedItem.getCancellationReviewedBy() != null) {
                            org.hibernate.Hibernate.initialize(orderedItem.getCancellationReviewedBy());
                        }
                    } catch (Exception initEx) {
                        log.warn("Could not initialize some relationships for orderedItem {}: {}", orderedItemId, initEx.getMessage());
                    }
                    
                    log.info("Re-fetched orderedItem {} with relationships initialized - Item status: {}, Request status: {}", 
                            orderedItemId, orderedItem.getItemStatus(), orderedItem.getCancellationRequestStatus());
                } catch (Exception e) {
                    // Don't throw exception - item status is already saved, re-fetch is only for building response
                    log.warn("Could not re-fetch orderedItem {} after recalculation: {}. Will use existing entity.", orderedItemId, e.getMessage());
                    // Try to refresh the entity we have
                    try {
                        entityManager.refresh(orderedItem);
                    } catch (Exception refreshEx) {
                        log.warn("Could not refresh orderedItem {}: {}", orderedItemId, refreshEx.getMessage());
                    }
                }
                
                // Create audit trail for item cancellation request
                // Use safe wrapper to prevent exceptions from affecting the main transaction
                try {
                    // Safely get restaurant - avoid LazyInitializationException by fetching by ID
                    Restaurant restaurant = null;
                    try {
                        // Get restaurant ID safely without triggering lazy loading
                        UUID restaurantId = null;
                        if (orderedItem.getOrder() != null && orderedItem.getOrder().getId() != null) {
                            // Fetch order with restaurant using repository
                            Optional<Order> orderOpt = orderRepository.findById(orderedItem.getOrder().getId());
                            if (orderOpt.isPresent()) {
                                Order order = orderOpt.get();
                                if (order.getRestaurant() != null) {
                                    restaurantId = order.getRestaurant().getId();
                                }
                            }
                        }
                        
                        // Fetch restaurant by ID if we got it
                        if (restaurantId != null) {
                            restaurant = restaurantRepository.findById(restaurantId).orElse(null);
                        }
                    } catch (Exception e) {
                        log.warn("Could not fetch restaurant for audit trail: {}", e.getMessage());
                    }
                    
                    ActionType actionType = request.getAction() == RequestStatus.APPROVED ? 
                            ActionType.REQUEST_CANCEL_ITEM_APPROVE : ActionType.REQUEST_CANCEL_ITEM_DECLINE;
                    createAuditTrailSafely(
                            manager,
                            actionType,
                            restaurant,
                            request.getAction(),
                            null, // ipAddress
                            null, // userAgent
                            orderedItem.getId(),
                            "ITEM", // Changed from "ORDERED_ITEM" to "ITEM" to match filter
                            String.format("Item cancellation request %s. Comments: %s", 
                                    request.getAction() == RequestStatus.APPROVED ? "approved" : "declined",
                                    request.getComments() != null ? request.getComments() : "N/A"),
                            orderedItem.getCancellationRequestedBy(), // requestedBy
                            orderedItem.getCancellationRequestedAt(), // requestedAt
                            manager, // reviewedBy
                            now // reviewedAt
                    );
                    
                    // Create audit trail entry for cashier/requester when item cancellation request is approved/declined
                    User requester = orderedItem.getCancellationRequestedBy();
                    if (requester != null) {
                        try {
                            createAuditTrailSafely(
                                    requester,
                                    ActionType.CANCELLATION,
                                    restaurant,
                                    request.getAction(), // APPROVED or DECLINED
                                    null, // ipAddress
                                    null, // userAgent
                                    orderedItem.getId(),
                                    "ITEM",
                                    String.format("Item cancellation request %s by manager. Comments: %s",
                                            request.getAction() == RequestStatus.APPROVED ? "approved" : "declined",
                                            request.getComments() != null ? request.getComments() : "N/A"),
                                    requester, // requestedBy
                                    orderedItem.getCancellationRequestedAt(), // requestedAt
                                    manager, // reviewedBy
                                    now // reviewedAt
                            );
                        } catch (Exception e) {
                            log.error("Failed to create audit trail for cashier when item cancellation was approved/declined: {}", e.getMessage(), e);
                        }
                    }
                } catch (Exception e) {
                    // Extra safety catch - should not happen as createAuditTrailSafely catches all exceptions
                    // but keeping this for defense in depth
                    log.error("Unexpected error in audit trail creation wrapper: {}", e.getMessage(), e);
                }
                
                // Save notification to database for the requester
                try {
                    User requester = orderedItem.getCancellationRequestedBy();
                    if (requester != null) {
                        saveItemCancellationRequestNotification(requester, request.getAction(), orderedItem, manager, userLocale);
                        
                        // Send WebSocket notification for cashiers/waiters
                        // Use the reloaded orderedItem to ensure relationships are available
                        UUID orderedItemIdForNotification = orderedItem.getId();
                        try {
                            OrderedItem orderedItemWithRelations = orderedItemRepository.findById(orderedItemIdForNotification)
                                    .orElse(orderedItem); // Fallback to original if reload fails
                            
                            sendItemCancellationNotification(orderedItemWithRelations, requester, 
                                    request.getAction() == RequestStatus.APPROVED, request.getComments(), userLocale);
                        } catch (Exception e) {
                            log.error("Failed to send item cancellation notification: {}", e.getMessage(), e);
                        }
                    }
                } catch (Exception e) {
                    log.error("Failed to save item cancellation request notification: {}", e.getMessage());
                }
                
                // Build response - wrap in try-catch to prevent LazyInitializationException from causing rollback
                ItemCancellationRequestResponse itemCancellationResponse = null;
                try {
                    itemCancellationResponse = buildItemCancellationRequestResponse(orderedItem, userLocale);
                } catch (org.hibernate.LazyInitializationException e) {
                    log.error("LazyInitializationException when building item cancellation response. Re-fetching with relationships: {}", e.getMessage());
                    // Re-fetch with relationships and try again
                    try {
                        orderedItem = orderedItemRepository.findByIdWithRelationshipsForCancellationResponse(orderedItem.getId())
                                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                                        messageUtil.getMessage("ordered.item.not.found", userLocale)));
                        itemCancellationResponse = buildItemCancellationRequestResponse(orderedItem, userLocale);
                    } catch (Exception ex) {
                        log.error("Failed to build item cancellation response even after re-fetch: {}", ex.getMessage(), ex);
                        throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                                messageUtil.getMessage("error.building.response", userLocale));
                    }
                } catch (Exception e) {
                    log.error("Error building item cancellation response: {}", e.getMessage(), e);
                    throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                            messageUtil.getMessage("error.building.response", userLocale));
                }
                
                RequestApprovalResponse approvalResponse = RequestApprovalResponse.builder()
                        .requestType(messageUtil.getMessage("request.type.item.cancellation", userLocale))
                        .profileUpdateResponse(null)
                        .additionalDiscountResponse(null)
                        .tableSectionResponse(null)
                        .refundResponse(null)
                        .itemCancellationResponse(itemCancellationResponse)
                        .comboCancellationResponse(null)
                        .build();
                
                String messageKey = request.getAction() == RequestStatus.APPROVED ? 
                        "item.cancellation.request.approved" : "item.cancellation.request.declined";
                
                // Notify all active managers about the request resolution
                UUID restaurantId = null;
                try {
                    // Safely get restaurant ID - use the re-fetched entity
                    if (orderedItem.getOrder() != null) {
                        org.hibernate.Hibernate.initialize(orderedItem.getOrder());
                        if (orderedItem.getOrder().getRestaurant() != null) {
                            org.hibernate.Hibernate.initialize(orderedItem.getOrder().getRestaurant());
                            restaurantId = orderedItem.getOrder().getRestaurant().getId();
                        }
                    }
                    if (restaurantId != null) {
                        List<User> activeManagers = findActiveManagersForRestaurant(restaurantId);
                        notifyManagersAboutRequestResolution(activeManagers, manager, orderedItem, messageUtil.getMessage("request.type.item.cancellation", userLocale), 
                                request.getAction() == RequestStatus.APPROVED, request.getComments(), userLocale);
                    }
                } catch (Exception e) {
                    log.error("Failed to notify managers about item cancellation request resolution: {}", e.getMessage(), e);
                }
                
                // Send WebSocket notification to item-status topic for item cancellation request decision.
                try {
                    if (restaurantId != null) {
                        sendItemStatusWebSocketRequestDecisionNotification(
                                restaurantId,
                                request.getAction(),
                                "ITEM_CANCELLATION",
                                orderedItem.getId(),
                                userLocale
                        );
                    }
                } catch (Exception e) {
                    log.warn("Failed to send item cancellation WebSocket notification to item-status topic: {}", e.getMessage());
                }
                
                return ResponseDto.<RequestApprovalResponse>builder()
                        .message(messageUtil.getMessage(messageKey, userLocale))
                        .data(approvalResponse)
                        .build();
            }
        }
        
        // Try to find as combo cancellation request
        Optional<OrderedCombo> orderedComboOptional = orderedComboRepository.findById(requestId);
        if (orderedComboOptional.isPresent()) {
            OrderedCombo orderedCombo = orderedComboOptional.get();
            if (orderedCombo.getCancellationRequestStatus() == RequestStatus.OPEN) {
                // This is a combo cancellation request
                if (cachedApprover == null) {
                    cachedApprover = loadApproverOrThrow(approverId, userLocale);
                }
                User manager = cachedApprover;
                
                // Atomic request state update with pessimistic locking
                // Lock the orderedCombo entity to prevent concurrent modifications
                entityManager.lock(orderedCombo, jakarta.persistence.LockModeType.PESSIMISTIC_WRITE);
                
                // Re-check status after acquiring lock (double-check locking pattern)
                if (orderedCombo.getCancellationRequestStatus() != RequestStatus.OPEN) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                            messageUtil.getMessage("combo.cancellation.request.not.pending", userLocale));
                }
                
                // Use UTC timezone to match the rest of the application
                OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
                
                // CRITICAL: Get orderId before any entity operations that might clear the entity manager
                UUID orderId = orderedCombo.getOrder() != null ? orderedCombo.getOrder().getId() : null;
                
                if (request.getAction() == RequestStatus.APPROVED) {
                    // Approve - set combo status to CANCELED
                    // Capture status before cancellation for wastage reporting
                    // Only set wastage_source_status if current status is COOKING, READY, or SERVED
                    if (orderedCombo.getItemStatus() != null 
                            && orderedCombo.getItemStatus() != com.gulfnet.shared_library.enums.ItemStatus.CANCELED
                            && (orderedCombo.getItemStatus() == com.gulfnet.shared_library.enums.ItemStatus.COOKING 
                                || orderedCombo.getItemStatus() == com.gulfnet.shared_library.enums.ItemStatus.READY 
                                || orderedCombo.getItemStatus() == com.gulfnet.shared_library.enums.ItemStatus.SERVED)
                            && orderedCombo.getWastageSourceStatus() == null) {
                        orderedCombo.setWastageSourceStatus(orderedCombo.getItemStatus());
                    }
                    orderedCombo.setItemStatus(com.gulfnet.shared_library.enums.ItemStatus.CANCELED);
                    orderedCombo.setCancellationRequestStatus(RequestStatus.APPROVED);
                    
                    // CRITICAL: Also cancel all items within this combo
                    // When a combo is cancelled, all its items must also be cancelled
                    UUID comboId = orderedCombo.getId();
                    List<OrderedItem> comboItems = orderedItemRepository.findByOrderedComboId(comboId);
                    if (comboItems != null && !comboItems.isEmpty()) {
                        log.info("Cancelling {} items within combo {} as part of combo cancellation approval", 
                                comboItems.size(), comboId);
                        for (OrderedItem comboItem : comboItems) {
                            if (comboItem.getItemStatus() != com.gulfnet.shared_library.enums.ItemStatus.CANCELED) {
                                // Capture status before cancellation for wastage reporting
                                // Only set wastage_source_status if current status is COOKING, READY, or SERVED
                                if (comboItem.getItemStatus() != null 
                                        && comboItem.getItemStatus() != com.gulfnet.shared_library.enums.ItemStatus.CANCELED
                                        && (comboItem.getItemStatus() == com.gulfnet.shared_library.enums.ItemStatus.COOKING 
                                            || comboItem.getItemStatus() == com.gulfnet.shared_library.enums.ItemStatus.READY 
                                            || comboItem.getItemStatus() == com.gulfnet.shared_library.enums.ItemStatus.SERVED)
                                        && comboItem.getWastageSourceStatus() == null) {
                                    comboItem.setWastageSourceStatus(comboItem.getItemStatus());
                                }
                                comboItem.setItemStatus(com.gulfnet.shared_library.enums.ItemStatus.CANCELED);
                                comboItem.setUpdatedAt(now);
                                comboItem.setUpdatedBy(manager);
                                orderedItemRepository.save(comboItem);
                                log.info("Cancelled combo item {} within combo {}", comboItem.getId(), comboId);
                            }
                        }
                        // Flush to ensure all combo items are persisted with CANCELED status
                        orderedItemRepository.flush();
                    }
                } else if (request.getAction() == RequestStatus.DECLINED) {
                    // Decline - keep current status
                    orderedCombo.setCancellationRequestStatus(RequestStatus.DECLINED);
                }
                
                // Set review information (using UTC to match the rest of the application)
                orderedCombo.setCancellationReviewedAt(now);
                orderedCombo.setCancellationReviewedBy(manager);
                orderedCombo.setCancellationComments(request.getComments());
                orderedCombo.setUpdatedAt(now);
                orderedCombo.setUpdatedBy(manager);
                
                // CRITICAL: Ensure entity is managed before saving
                if (!entityManager.contains(orderedCombo)) {
                    orderedCombo = entityManager.merge(orderedCombo);
                }
                
                // CRITICAL: Save and flush to ensure changes are persisted to database
                // This must happen BEFORE any recalculation that might clear the entity manager
                orderedCombo = orderedComboRepository.save(orderedCombo);
                orderedComboRepository.flush(); // Force immediate write to database
                
                log.info("Combo cancellation request {} - Action: {}, Combo status: {}, Request status: {} (after save and flush)", 
                        orderedCombo.getId(), request.getAction(), orderedCombo.getItemStatus(), orderedCombo.getCancellationRequestStatus());
                
                // Double-check and force save if needed
                if (request.getAction() == RequestStatus.APPROVED) {
                    boolean needsResave = false;
                    if (orderedCombo.getItemStatus() != com.gulfnet.shared_library.enums.ItemStatus.CANCELED) {
                        log.error("CRITICAL: Combo status was not set to CANCELED! Current status: {}. Setting it now.", orderedCombo.getItemStatus());
                        if (orderedCombo.getWastageSourceStatus() == null && orderedCombo.getItemStatus() != null 
                                && orderedCombo.getItemStatus() != com.gulfnet.shared_library.enums.ItemStatus.CANCELED) {
                            orderedCombo.setWastageSourceStatus(orderedCombo.getItemStatus());
                        }
                        orderedCombo.setItemStatus(com.gulfnet.shared_library.enums.ItemStatus.CANCELED);
                        needsResave = true;
                    }
                    if (orderedCombo.getCancellationRequestStatus() != RequestStatus.APPROVED) {
                        log.error("CRITICAL: Cancellation request status was not set to APPROVED! Current status: {}. Setting it now.", orderedCombo.getCancellationRequestStatus());
                        orderedCombo.setCancellationRequestStatus(RequestStatus.APPROVED);
                        needsResave = true;
                    }
                    if (needsResave) {
                        orderedCombo = orderedComboRepository.save(orderedCombo);
                        orderedComboRepository.flush();
                        log.info("Combo cancellation request {} - Re-saved with correct statuses", orderedCombo.getId());
                    }
                    
                    // Recalculate order totals after combo cancellation (only updates existing order, no new entries)
                    if (orderId != null) {
                        try {
                            Optional<Order> orderForPolicy = orderRepository.findById(orderId);
                            TransactionStatus txStatus = transactionRepository.findByOrderId(orderId)
                                    .map(Transaction::getTransactionStatus)
                                    .orElse(null);
                            if (orderForPolicy.isPresent()
                                    && !shouldSkipOrderAmountAdjustmentOnCancellation(orderForPolicy.get(), txStatus)) {
                                recalculateOrderAfterItemCancellation(orderId, userLocale, manager);
                            } else if (orderForPolicy.isPresent()) {
                                log.info("Skipping order totals recalculation for combo cancellation approval - order {} (same policy as item no-deduction)",
                                        orderId);
                            }
                        } catch (Exception e) {
                            log.error("Failed to recalculate order after combo cancellation: {}", e.getMessage(), e);
                            // Don't throw - combo cancellation is already saved, recalculation failure shouldn't rollback
                        }
                    }
                    if (orderId != null) {
                        takeawaySessionTableReleaseService.maybeExpireTakeawaySessionWhenOrderServedAndTransactionCompleted(orderId, null);
                    }
                }
                
                // CRITICAL: Initialize lazy-loaded relationships before recalculation (which clears entity manager)
                // This prevents LazyInitializationException when building response later
                try {
                    if (orderedCombo.getCombo() != null) {
                        org.hibernate.Hibernate.initialize(orderedCombo.getCombo());
                        if (orderedCombo.getCombo().getTranslations() != null) {
                            org.hibernate.Hibernate.initialize(orderedCombo.getCombo().getTranslations());
                        }
                    }
                    if (orderedCombo.getOrder() != null) {
                        org.hibernate.Hibernate.initialize(orderedCombo.getOrder());
                        // CRITICAL: Also initialize Restaurant and its translations
                        // This is what was causing the LazyInitializationException
                        if (orderedCombo.getOrder().getRestaurant() != null) {
                            org.hibernate.Hibernate.initialize(orderedCombo.getOrder().getRestaurant());
                            if (orderedCombo.getOrder().getRestaurant().getTranslations() != null) {
                                org.hibernate.Hibernate.initialize(orderedCombo.getOrder().getRestaurant().getTranslations());
                            }
                        }
                    }
                    if (orderedCombo.getCancellationRequestedBy() != null) {
                        org.hibernate.Hibernate.initialize(orderedCombo.getCancellationRequestedBy());
                    }
                    if (orderedCombo.getCancellationReviewedBy() != null) {
                        org.hibernate.Hibernate.initialize(orderedCombo.getCancellationReviewedBy());
                    }
                    log.info("Successfully initialized all lazy-loaded relationships for orderedCombo {}", orderedCombo.getId());
                } catch (Exception e) {
                    log.warn("Could not initialize lazy-loaded relationships for orderedCombo {}: {}", orderedCombo.getId(), e.getMessage());
                }
                
                // NOTE: Order recalculation removed - combo cancellation works correctly without modifying the order table
                // The order totals will be recalculated by other services when needed
                
                // NOTE: We use the in-memory orderedCombo that we already saved and flushed earlier
                // All necessary relationships were initialized before recalculation (lines 7112-7129)
                // Re-fetching after entity manager clear causes transaction rollback issues
                UUID orderedComboId = orderedCombo.getId();
                log.info("Using in-memory orderedCombo {} for response - Combo status: {}, Request status: {}", 
                        orderedComboId, orderedCombo.getItemStatus(), orderedCombo.getCancellationRequestStatus());
                
                // Create audit trail for combo cancellation request
                try {
                    // Safely get restaurant - avoid LazyInitializationException by fetching by ID
                    Restaurant restaurant = null;
                    try {
                        // Get restaurant ID safely without triggering lazy loading
                        UUID restaurantId = null;
                        if (orderedCombo.getOrder() != null && orderedCombo.getOrder().getId() != null) {
                            // Fetch order with restaurant using repository
                            Optional<Order> orderOpt = orderRepository.findById(orderedCombo.getOrder().getId());
                            if (orderOpt.isPresent()) {
                                Order order = orderOpt.get();
                                if (order.getRestaurant() != null) {
                                    restaurantId = order.getRestaurant().getId();
                                }
                            }
                        }
                        
                        // Fetch restaurant by ID if we got it
                        if (restaurantId != null) {
                            restaurant = restaurantRepository.findById(restaurantId).orElse(null);
                        }
                    } catch (Exception e) {
                        log.warn("Could not fetch restaurant for audit trail: {}", e.getMessage());
                    }
                    
                    createAuditTrailSafely(
                            manager,
                            ActionType.CANCELLATION,
                            restaurant,
                            request.getAction(),
                            null, // ipAddress
                            null, // userAgent
                            orderedCombo.getId(),
                            "ORDERED_COMBO",
                            String.format("Combo cancellation request %s. Comments: %s", 
                                    request.getAction() == RequestStatus.APPROVED ? "approved" : "declined",
                                    request.getComments() != null ? request.getComments() : "N/A"),
                            orderedCombo.getCancellationRequestedBy(), // requestedBy
                            orderedCombo.getCancellationRequestedAt(), // requestedAt
                            manager, // reviewedBy
                            now // reviewedAt
                    );
                } catch (Exception e) {
                    // Extra safety catch - should not happen as createAuditTrailSafely catches all exceptions
                    // but keeping this for defense in depth
                    log.error("Unexpected error in audit trail creation wrapper: {}", e.getMessage(), e);
                }
                
                // Save notification to database for the requester
                try {
                    User requester = orderedCombo.getCancellationRequestedBy();
                    if (requester != null) {
                        saveComboCancellationRequestNotification(requester, request.getAction(), orderedCombo, manager, userLocale);
                        // Send WebSocket notification for cashiers/waiters and KDS.
                        // Mirrors item cancellation flow to keep requester + KDS behavior consistent.
                        sendComboCancellationNotification(orderedCombo, requester,
                                request.getAction() == RequestStatus.APPROVED, request.getComments(), userLocale);
                    }
                } catch (Exception e) {
                    log.error("Failed to save combo cancellation request notification: {}", e.getMessage());
                }
                
                // Build response - wrap in try-catch to prevent LazyInitializationException from causing rollback
                ComboCancellationRequestResponse comboCancellationResponse = null;
                try {
                    comboCancellationResponse = buildComboCancellationRequestResponse(orderedCombo, userLocale);
                } catch (org.hibernate.LazyInitializationException e) {
                    log.error("LazyInitializationException when building combo cancellation response. Re-fetching with relationships: {}", e.getMessage());
                    // Re-fetch with relationships and try again
                    try {
                        orderedCombo = orderedComboRepository.findByIdWithRelationshipsForCancellationResponse(orderedCombo.getId())
                                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                                        messageUtil.getMessage("ordered.combo.not.found", userLocale)));
                        comboCancellationResponse = buildComboCancellationRequestResponse(orderedCombo, userLocale);
                    } catch (Exception ex) {
                        log.error("Failed to build combo cancellation response even after re-fetch: {}", ex.getMessage(), ex);
                        throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                                messageUtil.getMessage("error.building.response", userLocale));
                    }
                } catch (Exception e) {
                    log.error("Error building combo cancellation response: {}", e.getMessage(), e);
                    throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                            messageUtil.getMessage("error.building.response", userLocale));
                }
                
                RequestApprovalResponse approvalResponse = RequestApprovalResponse.builder()
                        .requestType(messageUtil.getMessage("request.type.item.cancellation", userLocale))
                        .profileUpdateResponse(null)
                        .additionalDiscountResponse(null)
                        .tableSectionResponse(null)
                        .refundResponse(null)
                        .itemCancellationResponse(null)
                        .comboCancellationResponse(comboCancellationResponse)
                        .build();
                
                String messageKey = request.getAction() == RequestStatus.APPROVED ? 
                        "item.cancellation.request.approved" : "item.cancellation.request.declined";
                
                // Notify all active managers about the request resolution
                UUID restaurantId = null;
                try {
                    restaurantId = orderedCombo.getOrder() != null && orderedCombo.getOrder().getRestaurant() != null ? 
                            orderedCombo.getOrder().getRestaurant().getId() : null;
                    if (restaurantId != null) {
                        List<User> activeManagers = findActiveManagersForRestaurant(restaurantId);
                        notifyManagersAboutRequestResolution(activeManagers, manager, orderedCombo, "Combo Cancellation", 
                                request.getAction() == RequestStatus.APPROVED, request.getComments(), userLocale);
                    }
                } catch (Exception e) {
                    log.error("Failed to notify managers about combo cancellation request resolution: {}", e.getMessage(), e);
                }
                
                // Send WebSocket notifications to all three topics (item-status, order-status, transaction-status) for combo cancellation approval/decline
                try {
                    if (restaurantId != null) {
                        // Send to item-status topic
                        sendItemStatusWebSocketRequestDecisionNotification(
                                restaurantId,
                                request.getAction(),
                                "COMBO_CANCELLATION",
                                orderedCombo.getId(),
                                userLocale
                        );
                        // Send to order-status topic
                        sendOrderStatusWebSocketRequestDecisionNotification(
                                restaurantId,
                                request.getAction(),
                                "COMBO_CANCELLATION",
                                orderedCombo.getId(),
                                userLocale
                        );
                        // Send to transaction-status topic
                        sendTransactionStatusWebSocketRequestDecisionNotification(
                                restaurantId,
                                request.getAction(),
                                "COMBO_CANCELLATION",
                                orderedCombo.getId(),
                                userLocale
                        );
                    }
                } catch (Exception e) {
                    log.warn("Failed to send combo cancellation WebSocket notification: {}", e.getMessage());
                }
                
                return ResponseDto.<RequestApprovalResponse>builder()
                        .message(messageUtil.getMessage(messageKey, userLocale))
                        .data(approvalResponse)
                        .build();
            }
        }
        
        // CRITICAL: Try to find as order cancellation request
        // This check is done specifically for order cancellation to ensure proper handling
        Optional<Order> orderCancellationOptional = orderRepository.findById(requestId);
        if (orderCancellationOptional.isPresent()) {
            Order order = orderCancellationOptional.get();
            if (order.getCancellationRequestStatus() == RequestStatus.OPEN) {
                // This is an order cancellation request - handle it specifically
                log.info("Processing order cancellation request for order ID: {} with action: {}", requestId, request.getAction());
                
                // Only MANAGER and HQ_ADMIN can approve/decline order cancellation requests
                if (!"MANAGER".equals(managerRole) && !"HQ_ADMIN".equals(managerRole)) {
                    throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                            messageUtil.getMessage("user.profile.update.request.unauthorized", userLocale));
                }
                
                if (cachedApprover == null) {
                    cachedApprover = loadApproverOrThrow(approverId, userLocale);
                }
                User manager = cachedApprover;
                
                // MANAGER can only approve/decline requests from their own restaurant
                if ("MANAGER".equals(managerRole) && manager.getRestaurantId() != null && order.getRestaurant() != null) {
                    if (!manager.getRestaurantId().equals(order.getRestaurant().getId())) {
                        throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                                messageUtil.getMessage("order.cancellation.request.unauthorized.restaurant", userLocale));
                    }
                }
                
                // Atomic request state update with pessimistic locking
                // Lock the order entity to prevent concurrent modifications
                try {
                    entityManager.lock(order, jakarta.persistence.LockModeType.PESSIMISTIC_WRITE);
                } catch (Exception e) {
                    log.error("Failed to acquire lock for order cancellation request {}: {}", requestId, e.getMessage(), e);
                    throw new ResponseStatusException(HttpStatus.CONFLICT,
                            messageUtil.getMessage("order.cancellation.request.not.pending", userLocale));
                }
                
                // Re-check status after acquiring lock (double-check locking pattern)
                if (order.getCancellationRequestStatus() != RequestStatus.OPEN) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                            messageUtil.getMessage("order.cancellation.request.not.pending", userLocale));
                }
                
                // Use UTC timezone to match the rest of the application
                OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
                TransactionStatus transactionStatusBeforeOrderCancel = transactionRepository
                        .findByOrderId(order.getId())
                        .map(Transaction::getTransactionStatus)
                        .orElse(null);
                
                try {
                    if (request.getAction() == RequestStatus.APPROVED) {
                        // Approve cancellation - cancel all items, combos, and transaction
                        order.setOrderStatus(OrderStatus.CANCELED);
                        order.setCancellationRequestStatus(RequestStatus.APPROVED);
                        
                        // Cancel all orderedItems and orderedCombos in the order
                        UUID orderId = order.getId();
                        
                        // Cancel all orderedItems (only regular items, not combo items)
                        try {
                            List<OrderedItem> orderedItems = orderedItemRepository.findByOrderId(orderId).stream()
                                    .filter(item -> item.getOrderedCombo() == null) // Only regular items
                                    .filter(item -> item.getItemStatus() != com.gulfnet.shared_library.enums.ItemStatus.CANCELED) // Skip already canceled
                                    .collect(Collectors.toList());
                            
                            for (OrderedItem item : orderedItems) {
                                try {
                                    // Capture status before cancellation for wastage reporting
                                    // Only set wastage_source_status if current status is COOKING, READY, or SERVED
                                    if (item.getItemStatus() != null 
                                            && item.getItemStatus() != com.gulfnet.shared_library.enums.ItemStatus.CANCELED
                                            && (item.getItemStatus() == com.gulfnet.shared_library.enums.ItemStatus.COOKING 
                                                || item.getItemStatus() == com.gulfnet.shared_library.enums.ItemStatus.READY 
                                                || item.getItemStatus() == com.gulfnet.shared_library.enums.ItemStatus.SERVED)
                                            && item.getWastageSourceStatus() == null) {
                                        item.setWastageSourceStatus(item.getItemStatus());
                                    }
                                    item.setItemStatus(com.gulfnet.shared_library.enums.ItemStatus.CANCELED);
                                    item.setUpdatedAt(now);
                                    item.setUpdatedBy(manager);
                                    orderedItemRepository.save(item);
                                } catch (Exception e) {
                                    log.error("Failed to cancel ordered item {}: {}", item.getId(), e.getMessage(), e);
                                    // Continue with other items even if one fails
                                }
                            }
                        } catch (Exception e) {
                            log.error("Failed to process ordered items for cancellation: {}", e.getMessage(), e);
                            // Continue with the cancellation process
                        }
                        
                        // Cancel all orderedCombos
                        try {
                            List<OrderedCombo> orderedCombos = orderedComboRepository.findByOrderId(orderId).stream()
                                    .filter(combo -> combo.getItemStatus() != com.gulfnet.shared_library.enums.ItemStatus.CANCELED) // Skip already canceled
                                    .collect(Collectors.toList());
                            
                            for (OrderedCombo combo : orderedCombos) {
                                try {
                                    // Capture status before cancellation for wastage reporting
                                    // Only set wastage_source_status if current status is COOKING, READY, or SERVED
                                    if (combo.getItemStatus() != null 
                                            && combo.getItemStatus() != com.gulfnet.shared_library.enums.ItemStatus.CANCELED
                                            && (combo.getItemStatus() == com.gulfnet.shared_library.enums.ItemStatus.COOKING 
                                                || combo.getItemStatus() == com.gulfnet.shared_library.enums.ItemStatus.READY 
                                                || combo.getItemStatus() == com.gulfnet.shared_library.enums.ItemStatus.SERVED)
                                            && combo.getWastageSourceStatus() == null) {
                                        combo.setWastageSourceStatus(combo.getItemStatus());
                                    }
                                    combo.setItemStatus(com.gulfnet.shared_library.enums.ItemStatus.CANCELED);
                                    combo.setUpdatedAt(now);
                                    combo.setUpdatedBy(manager);
                                    orderedComboRepository.save(combo);
                                } catch (Exception e) {
                                    log.error("Failed to cancel ordered combo {}: {}", combo.getId(), e.getMessage(), e);
                                    // Continue with other combos even if one fails
                                }
                            }
                        } catch (Exception e) {
                            log.error("Failed to process ordered combos for cancellation: {}", e.getMessage(), e);
                            // Continue with the cancellation process
                        }
                        
                        // Cancel transaction if exists
                        try {
                            Optional<Transaction> transactionOpt = transactionRepository.findByOrderId(orderId);
                            if (transactionOpt.isPresent()) {
                                Transaction transaction = transactionOpt.get();
                                TransactionStatus currentStatus = transaction.getTransactionStatus();
                                // Never downgrade a COMPLETED transaction to CANCELED.
                                // Also avoid canceling if already canceled/refunded/partially refunded.
                                if (currentStatus != TransactionStatus.CANCELED
                                        && currentStatus != TransactionStatus.REFUNDED
                                        && currentStatus != TransactionStatus.PARTIALLY_REFUNDED
                                        && currentStatus != TransactionStatus.COMPLETED) {
                                    transaction.setTransactionStatus(TransactionStatus.CANCELED);
                                    transaction.setUpdatedAt(now);
                                    transactionRepository.save(transaction);
                                }
                            }
                        } catch (Exception e) {
                            log.error("Failed to cancel transaction for order {}: {}", orderId, e.getMessage(), e);
                            // Continue with the cancellation process
                        }
                    } else if (request.getAction() == RequestStatus.DECLINED) {
                        // Decline cancellation - keep current status
                        order.setCancellationRequestStatus(RequestStatus.DECLINED);
                    }
                    
                    // Set review information (always set this regardless of approval/decline)
                    order.setCancellationReviewedAt(now);
                    order.setCancellationReviewedBy(manager);
                    order.setCancellationComments(request.getComments());
                    order.setUpdatedAt(now);
                    order.setUpdatedBy(manager);
                    
                    // CRITICAL: Save the order status immediately to ensure it's persisted
                    // This ensures the cancellation request status is saved even if subsequent operations fail
                    orderRepository.save(order);
                    orderRepository.flush();
                    log.info("Order cancellation request status saved: orderId={}, status={}", order.getId(), order.getCancellationRequestStatus());
                    
                    // Zero order monetary totals when policy requires adjustment (same rule as item no-deduction)
                    if (request.getAction() == RequestStatus.APPROVED) {
                        if (!shouldSkipOrderAmountAdjustmentOnCancellation(order, transactionStatusBeforeOrderCancel)) {
                            try {
                                recalculateOrderAfterOrderCancellation(order, userLocale, manager, now);
                                log.info("Order {} monetary totals zeroed after cancellation approval (policy: adjust amounts)", order.getId());
                            } catch (Exception e) {
                                log.error("Failed to zero order totals after order cancellation approval: {}", e.getMessage(), e);
                            }
                        } else {
                            log.info("Order {} cancellation approved - monetary totals unchanged (same skip policy as item no-deduction)", order.getId());
                        }
                        
                        // ==================== REAL-TIME HQ ALERT EVALUATION ====================
                        // Check if cancellation thresholds are breached after this order cancellation approval.
                        // Must run AFTER transaction commits so the REQUIRES_NEW alert transaction can see the data.
                        try {
                            Restaurant alertRestaurant = order.getRestaurant();
                            if (alertRestaurant != null) {
                                triggerAlertEvaluationAfterCommit(alertRestaurant, userLocale, "order cancellation approval");
                            }
                        } catch (Exception e) {
                            log.error("Failed to trigger alert evaluation after order cancellation approval: {}", e.getMessage(), e);
                        }

                        takeawaySessionTableReleaseService.maybeExpireTakeawaySessionWhenOrderServedAndTransactionCompleted(
                                order.getId(), null);
                    }
                } catch (Exception e) {
                    log.error("Unexpected error during order cancellation processing for order {}: {}", order.getId(), e.getMessage(), e);
                    // Try to save the status change if it was set
                    try {
                        if (order.getCancellationRequestStatus() == RequestStatus.APPROVED || 
                            order.getCancellationRequestStatus() == RequestStatus.DECLINED) {
                            order.setCancellationReviewedAt(now);
                            order.setCancellationReviewedBy(manager);
                            order.setCancellationComments(request.getComments());
                            order.setUpdatedAt(now);
                            order.setUpdatedBy(manager);
                            orderRepository.save(order);
                            orderRepository.flush();
                            log.info("Order cancellation status saved after error: orderId={}, status={}", order.getId(), order.getCancellationRequestStatus());
                        }
                    } catch (Exception saveException) {
                        log.error("Failed to save order cancellation status after error: {}", saveException.getMessage(), saveException);
                    }
                    // Re-throw the original exception
                    throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                            "Failed to process order cancellation request: " + e.getMessage());
                }
                
                // Send WebSocket notifications to item-status and order-status topics for order cancellation (both approved and declined)
                // NOTE: transaction-status topic is intentionally NOT notified here for ORDER_CANCELLATION
                // because the cashier already receives a detailed notification via /topic/cashier/notifications
                // (through notifyOrderCancellationDecision). Sending to transaction-status as well caused
                // duplicate pop-up notifications on the cashier app.
                try {
                    if (order.getRestaurant() != null && order.getRestaurant().getId() != null) {
                        UUID restaurantId = order.getRestaurant().getId();
                        log.info("Sending WebSocket notifications for order cancellation request {}: orderId={}, restaurantId={}, action={}", 
                                request.getAction(), order.getId(), restaurantId, request.getAction());
                        // Send to item-status topic
                        sendItemStatusWebSocketRequestDecisionNotification(
                                restaurantId,
                                request.getAction(),
                                "ORDER_CANCELLATION",
                                order.getId(),
                                userLocale
                        );
                        // Send to order-status topic
                        sendOrderStatusWebSocketRequestDecisionNotification(
                                restaurantId,
                                request.getAction(),
                                "ORDER_CANCELLATION",
                                order.getId(),
                                userLocale
                        );
                        log.info("Completed sending WebSocket notifications for order cancellation: orderId={}, restaurantId={}, action={}", 
                                order.getId(), restaurantId, request.getAction());
                    } else {
                        log.warn("Cannot send WebSocket notifications for order cancellation: restaurantId is null for order {}", order.getId());
                    }
                } catch (Exception e) {
                    log.error("Failed to send WebSocket notifications for order cancellation: orderId={}, error={}", 
                            order.getId(), e.getMessage(), e);
                }
                
                // Send KDS WebSocket notification if order cancellation is approved
                if (request.getAction() == RequestStatus.APPROVED) {
                    // KDS notification via NotificationService (existing behavior)
                    try {
                        if (notificationService != null) {
                            java.lang.reflect.Method method = notificationService.getClass()
                                    .getMethod("notifyKdsOrderCanceled", 
                                            com.gulfnet.shared_library.entity.Order.class, 
                                            String.class, 
                                            Locale.class);
                            method.invoke(notificationService, order, request.getComments(), userLocale);
                            log.debug("Sent KDS WebSocket notification for order cancellation via reflection");
                        }
                    } catch (Exception e) {
                        log.debug("NotificationService not available or failed to send KDS WebSocket notification for order cancellation: {}", e.getMessage());
                    }
                }
                
                // Create audit trail for CANCELLATION action
                // Use safe wrapper to prevent exceptions from affecting the main transaction
                try {
                    // Prefer manager comments for audit notes, but fall back to original cancellation
                    // reason supplied by waiter/cashier when comments are empty so HQ can still see it.
                    String auditReason = null;
                    String managerComments = request.getComments();
                    if (managerComments != null && !managerComments.trim().isEmpty()) {
                        auditReason = managerComments.trim();
                        log.debug("Using manager comments as audit reason for order cancellation: {}", auditReason);
                    } else {
                        // Try to extract cancellationReason from stored request data JSON
                        try {
                            String cancellationRequestData = order.getCancellationRequestData();
                            if (cancellationRequestData != null && !cancellationRequestData.trim().isEmpty()) {
                                log.debug("Attempting to parse cancellationRequestData for order {}: {}", order.getId(), cancellationRequestData);
                                ObjectMapper objectMapper = new ObjectMapper();
                                @SuppressWarnings("unchecked")
                                java.util.Map<String, Object> requestDataMap =
                                        objectMapper.readValue(cancellationRequestData, java.util.Map.class);
                                
                                // Try multiple possible keys for cancellation reason
                                Object reasonObj = requestDataMap.get("cancellationReason");
                                if (reasonObj == null) {
                                    reasonObj = requestDataMap.get("reason");
                                }
                                if (reasonObj == null) {
                                    reasonObj = requestDataMap.get("cancellation_reason");
                                }
                                
                                if (reasonObj != null) {
                                    if (reasonObj instanceof String cancellationReason) {
                                        if (!cancellationReason.trim().isEmpty()) {
                                            auditReason = cancellationReason.trim();
                                            log.debug("Extracted cancellation reason from requestData: {}", auditReason);
                                        } else {
                                            log.debug("Cancellation reason found in requestData but is empty");
                                        }
                                    } else {
                                        log.debug("Cancellation reason in requestData is not a String, type: {}", reasonObj.getClass().getName());
                                    }
                                } else {
                                    log.debug("No cancellation reason found in requestData map. Available keys: {}", requestDataMap.keySet());
                                }
                            } else {
                                log.debug("Order cancellationRequestData is null or empty for order {}", order.getId());
                            }
                        } catch (Exception ex) {
                            log.warn("Failed to parse order cancellationRequestData for audit trail: {}", ex.getMessage(), ex);
                        }
                    }
                    if (auditReason == null || auditReason.isEmpty()) {
                        auditReason = "N/A";
                        log.debug("No cancellation reason found, using default: N/A");
                    }

                    // Use ORDER_CANCEL so the manager's approval/decline is visible in manager audit trail
                    // (ActionType.CANCELLATION is excluded from manager view).
                    createAuditTrailSafely(
                            manager,
                            ActionType.ORDER_CANCEL,
                            order.getRestaurant(),
                            request.getAction(),
                            null, // ipAddress
                            null, // userAgent
                            order.getId(),
                            "ORDER",
                            String.format("Order cancellation request %s. Reason: %s",
                                    request.getAction() == RequestStatus.APPROVED ? "approved" : "declined",
                                    auditReason),
                            order.getCancellationRequestedBy(), // requestedBy
                            order.getCancellationRequestedAt(), // requestedAt
                            manager, // reviewedBy
                            now // reviewedAt
                    );
                } catch (Exception e) {
                    // Extra safety catch - should not happen as createAuditTrailSafely catches all exceptions
                    // but keeping this for defense in depth
                    log.error("Unexpected error in audit trail creation wrapper: {}", e.getMessage(), e);
                }
                
                // Save notification to database for the requester
                try {
                    User requester = order.getCancellationRequestedBy();
                    if (requester == null) {
                        // Fallback to the waiter assigned to the order if the original requester is not found
                        requester = order.getWaiter();
                    }
                    if (requester == null && order.getRestaurantTable() != null) {
                        // Fallback to the waiter assigned to the table if order waiter is not found
                        try {
                            List<TableAssignment> tableAssignments = tableAssignmentRepository.findByRestaurantTableIdAndUnassignedAtIsNullWithWaiter(order.getRestaurantTable().getId());
                            if (!tableAssignments.isEmpty()) {
                                // Sort by assigned_at descending to get the most recent assignment
                                tableAssignments.sort((ta1, ta2) -> {
                                    if (ta1.getAssignedAt() == null && ta2.getAssignedAt() == null) return 0;
                                    if (ta1.getAssignedAt() == null) return 1;
                                    if (ta2.getAssignedAt() == null) return -1;
                                    return ta2.getAssignedAt().compareTo(ta1.getAssignedAt());
                                });
                                requester = tableAssignments.get(0).getWaiter();
                            }
                        } catch (Exception e) {
                            log.error("Error getting waiter for table {}: {}", order.getRestaurantTable().getId(), e.getMessage(), e);
                        }
                    }
                    if (requester != null) {
                        saveOrderCancellationRequestNotification(requester, request.getAction(), order.getOrderNumber(), order.getId(), manager, userLocale);
                        
                        // Send notification via NotificationService (includes KDS notification)
                        // Try to use NotificationService directly (if available in same application context)
                        Object wsNotifier = resolveNotificationService();
                        boolean notificationSent = false;
                        if (wsNotifier != null) {
                            try {
                                boolean isApproved = request.getAction() == RequestStatus.APPROVED;
                                log.info("Sending order cancellation request decision notification to requester {} for order {} (approved: {})", 
                                        requester.getId(), order.getId(), isApproved);
                                
                                java.lang.reflect.Method method = wsNotifier.getClass()
                                        .getMethod("notifyOrderCancellationDecision", 
                                                com.gulfnet.shared_library.entity.Order.class, 
                                                User.class, 
                                                boolean.class, 
                                                String.class, 
                                                Locale.class);
                                method.invoke(wsNotifier, order, requester, isApproved, request.getComments(), userLocale);
                                log.info("Successfully sent order cancellation request decision notification to requester {} for order {} (includes KDS notification)", 
                                        requester.getId(), order.getId());
                                notificationSent = true; // Successfully sent via NotificationService, no need for RabbitMQ fallback
                            } catch (NoSuchMethodException e) {
                                log.error("Method notifyOrderCancellationDecision not found in NotificationService: {}", e.getMessage());
                                // Fall through to RabbitMQ fallback
                            } catch (Exception e) {
                                log.error("Failed to send order cancellation notification via reflection: {}", e.getMessage(), e);
                                // Fall through to RabbitMQ fallback
                            }
                        }
                        
                        // Fallback: Publish to RabbitMQ for restaurant-management to consume and send WebSocket notification (includes KDS notification)
                        if (!notificationSent) {
                            try {
                                boolean isApproved = request.getAction() == RequestStatus.APPROVED;
                                publishOrderCancellationNotificationToRabbitMQ(order, requester, isApproved, request.getComments(), userLocale);
                                log.info("Published order cancellation request decision notification to RabbitMQ for requester {} and order {} (includes KDS notification)", 
                                        requester.getId(), order.getId());
                            } catch (Exception e) {
                                log.error("Failed to publish order cancellation notification to RabbitMQ: {}", e.getMessage(), e);
                            }
                        }
                    } else {
                        log.warn("No requester, waiter, or table waiter found for order {} cancellation notification", order.getId());
                    }
                } catch (Exception e) {
                    log.error("Failed to save order cancellation request notification: {}", e.getMessage());
                }
                
                // Ensure order has all relationships loaded for response building
                // Since we're not clearing entity manager, the order should remain managed
                // But we re-fetch with relationships as a safety measure to ensure everything is loaded
                UUID orderId = order.getId();
                try {
                    order = orderRepository.findByIdWithRelationshipsForOrderCancellationResponse(orderId)
                            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                                    messageUtil.getMessage("order.not.found", userLocale)));
                } catch (Exception e) {
                    log.warn("Could not re-fetch order {} with relationships, using existing order: {}", orderId, e.getMessage());
                    // Continue with existing order - it should still be managed and accessible
                }
                
                // Build response - wrap in try-catch to prevent any exceptions from causing rollback
                OrderCancellationRequestResponse orderCancellationResponse = null;
                try {
                    orderCancellationResponse = buildOrderCancellationRequestResponse(order, userLocale);
                } catch (Exception e) {
                    log.error("Error building order cancellation response: {}", e.getMessage(), e);
                    throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                            messageUtil.getMessage("error.building.response", userLocale));
                }
                
                RequestApprovalResponse approvalResponse = RequestApprovalResponse.builder()
                        .requestType(messageUtil.getMessage("request.type.order.cancellation", userLocale))
                        .profileUpdateResponse(null)
                        .additionalDiscountResponse(null)
                        .tableSectionResponse(null)
                        .refundResponse(null)
                        .itemCancellationResponse(null)
                        .comboCancellationResponse(null)
                        .transactionCancellationResponse(null)
                        .orderCancellationResponse(orderCancellationResponse)
                        .build();
                
                String messageKey = request.getAction() == RequestStatus.APPROVED ?
                        "order.cancellation.request.approved" : "order.cancellation.request.declined";
                
                // Notify all active managers about the request resolution
                try {
                    UUID restaurantId = order.getRestaurant() != null ? order.getRestaurant().getId() : null;
                    if (restaurantId != null) {
                        List<User> activeManagers = findActiveManagersForRestaurant(restaurantId);
                        notifyManagersAboutRequestResolution(activeManagers, manager, order, messageUtil.getMessage("request.type.order.cancellation", userLocale), 
                                request.getAction() == RequestStatus.APPROVED, request.getComments(), userLocale);
                    }
                } catch (Exception e) {
                    log.error("Failed to notify managers about order cancellation request resolution: {}", e.getMessage(), e);
                }
                
                return ResponseDto.<RequestApprovalResponse>builder()
                        .message(messageUtil.getMessage(messageKey, userLocale))
                        .data(approvalResponse)
                        .build();
            }
        }
        
        // If we reach here, the request ID doesn't match any pending request
        throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                messageUtil.getMessage("request.not.found", userLocale));
    }

    /** Display text for {@link TranslationUtils#pickPreferredOrFromListNonBlank}: null when blank or {@code NA} placeholder. */
    private static String translationDisplayTextForPick(String name) {
        if (name == null || name.isBlank()) {
            return null;
        }
        if ("NA".equalsIgnoreCase(name.trim())) {
            return null;
        }
        return name;
    }

    private static boolean isMissingOrPlaceholderRefundName(String name) {
        return translationDisplayTextForPick(name) == null;
    }

    private String resolveLocalizedItemName(Item item, Locale userLocale) {
        if (item == null || item.getTranslations() == null || item.getTranslations().isEmpty()) {
            return "Item";
        }
        String preferred = userLocale != null ? userLocale.getLanguage() : null;
        return TranslationUtils.pickPreferredOrFromListNonBlank(
                item.getTranslations(),
                preferred,
                localizationProperties.getLanguages(),
                ItemTranslation::getLanguageCode,
                t -> translationDisplayTextForPick(t.getName()))
                .map(ItemTranslation::getName)
                .orElse("Item");
    }

    private String resolveLocalizedComboName(Combo combo, Locale userLocale) {
        if (combo == null || combo.getTranslations() == null || combo.getTranslations().isEmpty()) {
            return "Combo";
        }
        String preferred = userLocale != null ? userLocale.getLanguage() : null;
        return TranslationUtils.pickPreferredOrFromListNonBlank(
                combo.getTranslations(),
                preferred,
                localizationProperties.getLanguages(),
                ComboTranslation::getLanguageCode,
                t -> translationDisplayTextForPick(t.getName()))
                .map(ComboTranslation::getName)
                .orElse("Combo");
    }

    /**
     * When refund request_data has a missing or placeholder name, resolve from the ordered line and translations
     * (same fallback order as discount assignment).
     */
    private String resolveRefundLineDisplayName(String storedName, UUID lineId, boolean orderedComboLine, Locale userLocale) {
        if (!isMissingOrPlaceholderRefundName(storedName)) {
            return storedName;
        }
        try {
            if (orderedComboLine) {
                return orderedComboRepository.findByIdWithRelationshipsForCancellationResponse(lineId)
                        .map(oc -> oc.getCombo() != null ? resolveLocalizedComboName(oc.getCombo(), userLocale) : "Combo")
                        .orElse("Combo");
            }
            return orderedItemRepository.findByIdWithRelationshipsForCancellationResponse(lineId)
                    .map(oi -> oi.getItem() != null ? resolveLocalizedItemName(oi.getItem(), userLocale) : "Item")
                    .orElse("Item");
        } catch (Exception e) {
            log.debug("resolveRefundLineDisplayName failed for {} combo={}: {}", lineId, orderedComboLine, e.getMessage());
            return orderedComboLine ? "Combo" : "Item";
        }
    }
    
    /**
     * Build ItemCancellationRequestResponse from OrderedItem
     */
    private ItemCancellationRequestResponse buildItemCancellationRequestResponse(OrderedItem orderedItem, Locale userLocale) {
        // CRITICAL: Initialize lazy-loaded Item entity if not already initialized
        // This prevents LazyInitializationException when entity manager is cleared
        try {
            if (orderedItem.getItem() != null) {
                org.hibernate.Hibernate.initialize(orderedItem.getItem());
                if (orderedItem.getItem().getTranslations() != null) {
                    org.hibernate.Hibernate.initialize(orderedItem.getItem().getTranslations());
                }
            }
            if (orderedItem.getOrder() != null) {
                org.hibernate.Hibernate.initialize(orderedItem.getOrder());
                if (orderedItem.getOrder().getRestaurant() != null) {
                    org.hibernate.Hibernate.initialize(orderedItem.getOrder().getRestaurant());
                    if (orderedItem.getOrder().getRestaurant().getTranslations() != null) {
                        org.hibernate.Hibernate.initialize(orderedItem.getOrder().getRestaurant().getTranslations());
                    }
                }
            }
            if (orderedItem.getCancellationRequestedBy() != null) {
                org.hibernate.Hibernate.initialize(orderedItem.getCancellationRequestedBy());
            }
            if (orderedItem.getCancellationReviewedBy() != null) {
                org.hibernate.Hibernate.initialize(orderedItem.getCancellationReviewedBy());
            }
        } catch (Exception e) {
            log.warn("Could not initialize lazy-loaded relationships in buildItemCancellationRequestResponse: {}", e.getMessage());
        }
        
        String itemName = orderedItem.getItem() != null
                ? resolveLocalizedItemName(orderedItem.getItem(), userLocale)
                : "Item";
        
        // Get image URL with pre-signed URL if available
        String imageUrl = null;
        try {
            if (orderedItem.getItem() != null && orderedItem.getItem().getImageUrl() != null && !orderedItem.getItem().getImageUrl().isEmpty()) {
                imageUrl = awsService.getPreSignedUrl(orderedItem.getItem().getImageUrl());
            }
        } catch (Exception e) {
            log.warn("Failed to generate pre-signed URL for item image: {}", e.getMessage());
        }
        
        // Parse cancellation reason from request data
        String cancellationReason = null;
        if (orderedItem.getCancellationRequestData() != null) {
            try {
                ObjectMapper objectMapper = new ObjectMapper();
                com.gulfnet.shared_library.model.request.ItemCancellationRequestDto requestDto = 
                        objectMapper.readValue(orderedItem.getCancellationRequestData(), 
                                com.gulfnet.shared_library.model.request.ItemCancellationRequestDto.class);
                cancellationReason = requestDto.getCancellationReason();
            } catch (JsonProcessingException e) {
                log.warn("Failed to parse cancellation request data: {}", e.getMessage());
            }
        }
        
        // Get role name for requestedBy user
        String requestedByRole = null;
        if (orderedItem.getCancellationRequestedBy() != null && orderedItem.getCancellationRequestedBy().getRoleId() != null) {
            var role = roleRepository.findById(orderedItem.getCancellationRequestedBy().getRoleId()).orElse(null);
            if (role != null) {
                requestedByRole = role.getName();
            }
        }
        
        // Get restaurant name
        String restaurantName = null;
        UUID restaurantId = null;
        if (orderedItem.getOrder() != null && orderedItem.getOrder().getRestaurant() != null) {
            restaurantId = orderedItem.getOrder().getRestaurant().getId();
            if (orderedItem.getOrder().getRestaurant().getTranslations() != null && !orderedItem.getOrder().getRestaurant().getTranslations().isEmpty()) {
                String userLanguage = userLocale.getLanguage();
                restaurantName = orderedItem.getOrder().getRestaurant().getTranslations().stream()
                        .filter(t -> t.getLanguageCode() != null && t.getLanguageCode().startsWith(userLanguage))
                        .findFirst()
                        .map(com.gulfnet.shared_library.entity.RestaurantTranslation::getName)
                        .orElse(orderedItem.getOrder().getRestaurant().getTranslations().get(0).getName());
            } else {
                restaurantName = "Restaurant";
            }
        }
        
        return ItemCancellationRequestResponse.builder()
                .orderedItemId(orderedItem.getId())
                .orderId(orderedItem.getOrder() != null ? orderedItem.getOrder().getId() : null)
                .itemName(itemName)
                .imageUrl(imageUrl)
                .quantity(orderedItem.getQuantity())
                .price(orderedItem.getPrice())
                .currentItemStatus(orderedItem.getItemStatus()) // Show item status (Cooking, Delayed, Ready, etc.) in request details
                .cancellationReason(cancellationReason)
                .requestStatus(orderedItem.getCancellationRequestStatus())
                .requestedAt(orderedItem.getCancellationRequestedAt() != null ? orderedItem.getCancellationRequestedAt().toLocalDateTime() : null)
                .requestedBy(orderedItem.getCancellationRequestedBy() != null ? orderedItem.getCancellationRequestedBy().getId() : null)
                .requestedByName(orderedItem.getCancellationRequestedBy() != null ? 
                    orderedItem.getCancellationRequestedBy().getFirstName() + " " + orderedItem.getCancellationRequestedBy().getLastName() : null)
                .requestedByRole(requestedByRole)
                .reviewedAt(orderedItem.getCancellationReviewedAt() != null ? ((OffsetDateTime) orderedItem.getCancellationReviewedAt()).toLocalDateTime() : null)
                .reviewedBy(orderedItem.getCancellationReviewedBy() != null ? orderedItem.getCancellationReviewedBy().getId() : null)
                .reviewedByName(orderedItem.getCancellationReviewedBy() != null ? 
                    orderedItem.getCancellationReviewedBy().getFirstName() + " " + orderedItem.getCancellationReviewedBy().getLastName() : null)
                .comments(orderedItem.getCancellationComments())
                .orderedItemModifiers(new ArrayList<>()) // Modifiers not included for now
                .restaurantId(restaurantId)
                .restaurantName(restaurantName)
                .build();
    }
    
    /**
     * Build ComboCancellationRequestResponse from OrderedCombo
     */
    private ComboCancellationRequestResponse buildComboCancellationRequestResponse(OrderedCombo orderedCombo, Locale userLocale) {
        // CRITICAL: Initialize lazy-loaded Combo entity if not already initialized
        // This prevents LazyInitializationException when entity manager is cleared
        try {
            if (orderedCombo.getCombo() != null) {
                org.hibernate.Hibernate.initialize(orderedCombo.getCombo());
                if (orderedCombo.getCombo().getTranslations() != null) {
                    org.hibernate.Hibernate.initialize(orderedCombo.getCombo().getTranslations());
                }
            }
            if (orderedCombo.getOrder() != null) {
                org.hibernate.Hibernate.initialize(orderedCombo.getOrder());
                if (orderedCombo.getOrder().getRestaurant() != null) {
                    org.hibernate.Hibernate.initialize(orderedCombo.getOrder().getRestaurant());
                    if (orderedCombo.getOrder().getRestaurant().getTranslations() != null) {
                        org.hibernate.Hibernate.initialize(orderedCombo.getOrder().getRestaurant().getTranslations());
                    }
                }
            }
            if (orderedCombo.getCancellationRequestedBy() != null) {
                org.hibernate.Hibernate.initialize(orderedCombo.getCancellationRequestedBy());
            }
            if (orderedCombo.getCancellationReviewedBy() != null) {
                org.hibernate.Hibernate.initialize(orderedCombo.getCancellationReviewedBy());
            }
        } catch (Exception e) {
            log.warn("Could not initialize lazy-loaded relationships in buildComboCancellationRequestResponse: {}", e.getMessage());
        }
        
        String comboName = orderedCombo.getCombo() != null
                ? resolveLocalizedComboName(orderedCombo.getCombo(), userLocale)
                : "Combo";
        
        // Get image URL with pre-signed URL if available
        String imageUrl = null;
        try {
            if (orderedCombo.getCombo() != null && orderedCombo.getCombo().getComboImageUrl() != null && !orderedCombo.getCombo().getComboImageUrl().isEmpty()) {
                imageUrl = awsService.getPreSignedUrl(orderedCombo.getCombo().getComboImageUrl());
            }
        } catch (Exception e) {
            log.warn("Failed to generate pre-signed URL for combo image: {}", e.getMessage());
        }
        
        // Parse cancellation reason from request data
        String cancellationReason = null;
        if (orderedCombo.getCancellationRequestData() != null) {
            try {
                ObjectMapper objectMapper = new ObjectMapper();
                com.gulfnet.shared_library.model.request.ItemCancellationRequestDto requestDto = 
                        objectMapper.readValue(orderedCombo.getCancellationRequestData(), 
                                com.gulfnet.shared_library.model.request.ItemCancellationRequestDto.class);
                cancellationReason = requestDto.getCancellationReason();
            } catch (JsonProcessingException e) {
                log.warn("Failed to parse combo cancellation request data: {}", e.getMessage());
            }
        }
        
        // Get role name for requestedBy user
        String requestedByRole = null;
        if (orderedCombo.getCancellationRequestedBy() != null && orderedCombo.getCancellationRequestedBy().getRoleId() != null) {
            var role = roleRepository.findById(orderedCombo.getCancellationRequestedBy().getRoleId()).orElse(null);
            if (role != null) {
                requestedByRole = role.getName();
            }
        }
        
        // Get restaurant name
        String restaurantName = null;
        UUID restaurantId = null;
        if (orderedCombo.getOrder() != null && orderedCombo.getOrder().getRestaurant() != null) {
            restaurantId = orderedCombo.getOrder().getRestaurant().getId();
            if (orderedCombo.getOrder().getRestaurant().getTranslations() != null && !orderedCombo.getOrder().getRestaurant().getTranslations().isEmpty()) {
                String userLanguage = userLocale.getLanguage();
                restaurantName = orderedCombo.getOrder().getRestaurant().getTranslations().stream()
                        .filter(t -> t.getLanguageCode() != null && t.getLanguageCode().startsWith(userLanguage))
                        .findFirst()
                        .map(com.gulfnet.shared_library.entity.RestaurantTranslation::getName)
                        .orElse(orderedCombo.getOrder().getRestaurant().getTranslations().get(0).getName());
            } else {
                restaurantName = "Restaurant";
            }
        }
        
        return ComboCancellationRequestResponse.builder()
                .orderedComboId(orderedCombo.getId())
                .orderId(orderedCombo.getOrder() != null ? orderedCombo.getOrder().getId() : null)
                .comboName(comboName)
                .imageUrl(imageUrl)
                .quantity(orderedCombo.getQuantity())
                .price(orderedCombo.getPrice())
                .currentItemStatus(orderedCombo.getItemStatus()) // Show item status (Cooking, Delayed, Ready, etc.) in request details
                .cancellationReason(cancellationReason)
                .requestStatus(orderedCombo.getCancellationRequestStatus())
                .requestedAt(orderedCombo.getCancellationRequestedAt() != null ? orderedCombo.getCancellationRequestedAt().toLocalDateTime() : null)
                .requestedBy(orderedCombo.getCancellationRequestedBy() != null ? orderedCombo.getCancellationRequestedBy().getId() : null)
                .requestedByName(orderedCombo.getCancellationRequestedBy() != null ? 
                    orderedCombo.getCancellationRequestedBy().getFirstName() + " " + orderedCombo.getCancellationRequestedBy().getLastName() : null)
                .requestedByRole(requestedByRole)
                .reviewedAt(orderedCombo.getCancellationReviewedAt() != null ? ((OffsetDateTime) orderedCombo.getCancellationReviewedAt()).toLocalDateTime() : null)
                .reviewedBy(orderedCombo.getCancellationReviewedBy() != null ? orderedCombo.getCancellationReviewedBy().getId() : null)
                .reviewedByName(orderedCombo.getCancellationReviewedBy() != null ? 
                    orderedCombo.getCancellationReviewedBy().getFirstName() + " " + orderedCombo.getCancellationReviewedBy().getLastName() : null)
                .comments(orderedCombo.getCancellationComments())
                .restaurantId(restaurantId)
                .restaurantName(restaurantName)
                .build();
    }

    /**
     * Build TransactionCancellationRequestResponse from Transaction entity
     */
    private TransactionCancellationRequestResponse buildTransactionCancellationRequestResponse(Transaction transaction, Locale userLocale) {
        String cancellationReason = null;
        
        // Parse cancellation request data if available
        if (transaction.getRequestData() != null) {
            try {
                ObjectMapper objectMapper = new ObjectMapper();
                com.gulfnet.shared_library.model.request.TransactionCancellationRequestDto requestDto = 
                        objectMapper.readValue(transaction.getRequestData(), 
                                com.gulfnet.shared_library.model.request.TransactionCancellationRequestDto.class);
                if (requestDto != null) {
                    cancellationReason = requestDto.getCancellationReason();
                }
            } catch (JsonProcessingException e) {
                log.warn("Error parsing transaction cancellation request data for transaction {}: {}", transaction.getId(), e.getMessage());
            } catch (Exception e) {
                log.error("Unexpected error parsing transaction cancellation request data for transaction {}: {}", transaction.getId(), e.getMessage(), e);
            }
        }
        
        // Get role name for requestedBy user
        String requestedByRole = null;
        if (transaction.getRequestedBy() != null && transaction.getRequestedBy().getRoleId() != null) {
            var role = roleRepository.findById(transaction.getRequestedBy().getRoleId()).orElse(null);
            if (role != null) {
                requestedByRole = role.getName();
            }
        }
        
        // Build requestedByName with proper null handling
        String requestedByName = null;
        if (transaction.getRequestedBy() != null) {
            String firstName = transaction.getRequestedBy().getFirstName() != null ? transaction.getRequestedBy().getFirstName() : "";
            String lastName = transaction.getRequestedBy().getLastName() != null ? transaction.getRequestedBy().getLastName() : "";
            requestedByName = (firstName + " " + lastName).trim();
            if (requestedByName.isEmpty()) {
                requestedByName = null;
            }
        }
        
        // Build reviewedByName with proper null handling
        String reviewedByName = null;
        if (transaction.getReviewedBy() != null) {
            String firstName = transaction.getReviewedBy().getFirstName() != null ? transaction.getReviewedBy().getFirstName() : "";
            String lastName = transaction.getReviewedBy().getLastName() != null ? transaction.getReviewedBy().getLastName() : "";
            reviewedByName = (firstName + " " + lastName).trim();
            if (reviewedByName.isEmpty()) {
                reviewedByName = null;
            }
        }
        
        // Get restaurant name
        String restaurantName = null;
        UUID restaurantId = null;
        if (transaction.getRestaurant() != null) {
            restaurantId = transaction.getRestaurant().getId();
            if (transaction.getRestaurant().getTranslations() != null && !transaction.getRestaurant().getTranslations().isEmpty()) {
                String userLanguage = userLocale.getLanguage();
                restaurantName = transaction.getRestaurant().getTranslations().stream()
                        .filter(t -> t.getLanguageCode() != null && t.getLanguageCode().startsWith(userLanguage))
                        .findFirst()
                        .map(com.gulfnet.shared_library.entity.RestaurantTranslation::getName)
                        .orElse(transaction.getRestaurant().getTranslations().get(0).getName());
            } else {
                restaurantName = "Restaurant";
            }
        }
        
        return TransactionCancellationRequestResponse.builder()
                .transactionId(transaction.getId())
                .orderId(transaction.getOrder() != null ? transaction.getOrder().getId() : null)
                .orderNumber(transaction.getOrder() != null ? transaction.getOrder().getOrderNumber() : null)
                .transactionNumber(transaction.getTransactionNumber())
                .paymentMethod(transaction.getPaymentMethod())
                .paymentApp(transaction.getPaymentApp())
                .transactionAmount(transaction.getOrder() != null && transaction.getOrder().getTotalAmount() != null 
                        ? transaction.getOrder().getTotalAmount() 
                        : transaction.getTransactionAmount()) // Use order totalAmount, fallback to transaction amount
                .currentTransactionStatus(transaction.getTransactionStatus())
                .cancellationReason(cancellationReason)
                .requestStatus(transaction.getRequestStatus())
                .requestedAt(transaction.getRequestedAt() != null ? transaction.getRequestedAt().toLocalDateTime() : null) // Ensure this is set when request is created
                .requestedBy(transaction.getRequestedBy() != null ? transaction.getRequestedBy().getId() : null)
                .requestedByName(requestedByName)
                .requestedByRole(requestedByRole)
                .reviewedAt(transaction.getReviewedAt() != null ? ((OffsetDateTime) transaction.getReviewedAt()).toLocalDateTime() : null)
                .reviewedBy(transaction.getReviewedBy() != null ? transaction.getReviewedBy().getId() : null)
                .reviewedByName(reviewedByName)
                .comments(transaction.getRequestComments())
                .restaurantId(restaurantId)
                .restaurantName(restaurantName)
                .build();
    }

    /**
     * Same behavior as {@code OrderServiceImpl.cancelTransactionIfOrderCanceled}:
     * when the order is {@link OrderStatus#CANCELED}, cancel the linked transaction if not already terminal.
     */
    private void cancelTransactionIfOrderCanceled(UUID orderId) {
        if (orderId == null) {
            return;
        }
        Optional<Transaction> transactionOpt = transactionRepository.findByOrderId(orderId);
        if (transactionOpt.isEmpty()) {
            return;
        }
        Transaction transaction = transactionOpt.get();
        TransactionStatus currentStatus = transaction.getTransactionStatus();
        // Never downgrade a COMPLETED transaction to CANCELED.
        if (currentStatus != TransactionStatus.CANCELED
                && currentStatus != TransactionStatus.REFUNDED
                && currentStatus != TransactionStatus.PARTIALLY_REFUNDED
                && currentStatus != TransactionStatus.COMPLETED) {
            OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
            transaction.setTransactionStatus(TransactionStatus.CANCELED);
            transaction.setUpdatedAt(now);
            transactionRepository.save(transaction);
            log.info("Transaction {} canceled automatically (order {} is CANCELED after item/combo cancellation approval)",
                    transaction.getId(), orderId);
        } else if (currentStatus == TransactionStatus.COMPLETED) {
            log.info("Order {} is cancelled but transaction {} is COMPLETED; leaving transaction status unchanged.", orderId, transaction.getId());
        }
    }

    /**
     * Recalculate order totals after item or combo cancellation
     * Deducts the canceled item's amount from subtotal and recalculates tax, service charge, packaging charge, and total
     * Uses REQUIRES_NEW propagation to ensure recalculation errors don't affect the main transaction
     * 
     * @param orderId The ID of the order to recalculate
     * @param userLocale The locale for logging
     * @param updatedByUser The user who triggered this recalculation (manager who approved cancellation)
     */
    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.REQUIRES_NEW)
    private void recalculateOrderAfterItemCancellation(UUID orderId, Locale userLocale, User updatedByUser) {
        log.info("Recalculating order {} after item/combo cancellation", orderId);
        
        try {
            // Fetch order from database with relationships to ensure lazy-loaded data is available
            // No need to clear entity manager - this preserves all managed entities and their relationships
            Order freshOrder = orderRepository.findById(orderId)
                    .orElseThrow(() -> new RuntimeException("Order not found: " + orderId));
            
            // CRITICAL: Preserve original audit fields to prevent new record creation
            // These fields are updatable=false, but we need to ensure they're set correctly
            // to prevent JPA from treating this as a new entity
            OffsetDateTime originalCreatedAt = freshOrder.getCreatedAt();
            User originalCreatedBy = freshOrder.getCreatedBy();
            String originalOrderNumber = freshOrder.getOrderNumber();
            
            // Store original audit fields that must be preserved
            if (originalCreatedAt != null) {
                freshOrder.setCreatedAt(originalCreatedAt);
            }
            if (originalCreatedBy != null) {
                freshOrder.setCreatedBy(originalCreatedBy);
            }
            if (originalOrderNumber != null) {
                freshOrder.setOrderNumber(originalOrderNumber);
            }
            
            // CRITICAL: Get discount ID from the fresh order to avoid lazy loading issues
            UUID discountId = null;
            if (freshOrder.getDiscount() != null) {
                try {
                    discountId = freshOrder.getDiscount().getId();
                } catch (Exception e) {
                    // If we can't get the ID (proxy not initialized), try to initialize it
                    try {
                        org.hibernate.Hibernate.initialize(freshOrder.getDiscount());
                        if (freshOrder.getDiscount() != null) {
                            discountId = freshOrder.getDiscount().getId();
                        }
                    } catch (Exception ex) {
                        log.warn("Could not get discount ID from order {}: {}", orderId, ex.getMessage());
                    }
                }
            }
            
            // CRITICAL: Fetch Discount separately using the saved discount ID to avoid lazy loading issues
            Discount orderDiscount = null;
            if (discountId != null) {
                try {
                    orderDiscount = discountRepository.findById(discountId).orElse(null);
                    if (orderDiscount == null) {
                        log.debug("Discount {} not found for order {}", discountId, orderId);
                    }
                } catch (Exception e) {
                    log.warn("Could not load discount {} for order {}: {}", discountId, orderId, e.getMessage());
                }
            }
            
            // Get all active (non-canceled) ordered items (only regular items, not combo items)
            List<OrderedItem> activeOrderedItems = orderedItemRepository.findByOrderId(freshOrder.getId()).stream()
                    .filter(item -> item.getOrderedCombo() == null) // Only regular items
                    .filter(item -> item.getItemStatus() != com.gulfnet.shared_library.enums.ItemStatus.CANCELED)
                    .collect(Collectors.toList());
            
            // Calculate subtotal from active items
            // Use totalDiscountedItemAmount if available, otherwise totalItemAmount, otherwise price * quantity
            BigDecimal itemsSubTotal = activeOrderedItems.stream()
                    .map(oi -> {
                        if (oi.getTotalDiscountedItemAmount() != null) {
                            return oi.getTotalDiscountedItemAmount();
                        } else if (oi.getTotalItemAmount() != null) {
                            return oi.getTotalItemAmount();
                        } else {
                            BigDecimal price = oi.getPrice() != null ? oi.getPrice() : BigDecimal.ZERO;
                            Integer qty = oi.getQuantity() != null ? oi.getQuantity() : 0;
                            return price.multiply(BigDecimal.valueOf(qty));
                        }
                    })
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            
            // Get all active (non-canceled) ordered combos
            List<OrderedCombo> activeOrderedCombos = orderedComboRepository.findByOrderId(freshOrder.getId()).stream()
                    .filter(combo -> combo.getItemStatus() != com.gulfnet.shared_library.enums.ItemStatus.CANCELED)
                    .collect(Collectors.toList());
            
            // Calculate subtotal from active combos
            BigDecimal combosSubTotal = activeOrderedCombos.stream()
                    .map(oc -> {
                        if (oc.getTotalComboAmount() != null) {
                            return oc.getTotalComboAmount();
                        } else {
                            return oc.getPrice() != null ? oc.getPrice() : BigDecimal.ZERO;
                        }
                    })
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            
            BigDecimal newSubTotal = itemsSubTotal.add(combosSubTotal);
            
            log.info("Order {} new subtotal after item cancellation - Items: {}, Combos: {}, Total: {}", 
                    freshOrder.getId(), itemsSubTotal, combosSubTotal, newSubTotal);
            
            // Fetch restaurant chain config once for this recalculation
            RestaurantChainConfigCache config = getRestaurantChainConfig();
            // Get currency (try to get from config, fallback to default)
            String currency = getCurrencyFromConfig(config);
            
            // Validate and recalculate order-level discount if threshold is still met
            BigDecimal newDiscountAmount = BigDecimal.ZERO;
            BigDecimal newSubtotalAfterDiscount = newSubTotal;
            boolean discountRemoved = false;
            
            if (orderDiscount != null) {
                // Check if discount threshold is still met
                if (orderDiscount.getOrderValueThreshold() != null && 
                    newSubTotal.compareTo(orderDiscount.getOrderValueThreshold()) < 0) {
                    
                    log.warn("Order {} discount threshold no longer met. Removing discount. Threshold: {}, Current Subtotal: {}", 
                            freshOrder.getId(), orderDiscount.getOrderValueThreshold(), newSubTotal);
                    
                    // Remove discount
                    freshOrder.setDiscount(null);
                    freshOrder.setDiscountCode(null);
                    freshOrder.setDiscountValue(null);
                    freshOrder.setDiscountAmount(BigDecimal.ZERO);
                    freshOrder.setDiscountType(null);
                    discountRemoved = true;
                } else {
                    // Recalculate discount with new subtotal
                    newDiscountAmount = calculateOrderDiscountAmount(orderDiscount, newSubTotal, currency, config.getRoundingPolicy());
                    newSubtotalAfterDiscount = newSubTotal.subtract(newDiscountAmount);
                    if (newSubtotalAfterDiscount.compareTo(BigDecimal.ZERO) < 0) {
                        newSubtotalAfterDiscount = BigDecimal.ZERO;
                        newDiscountAmount = newSubTotal;
                    }
                    
                    // Update discount fields
                    freshOrder.setDiscountAmount(CurrencyFormatter.formatAmount(newDiscountAmount, currency, config.getRoundingPolicy()));
                    freshOrder.setDiscountCode(orderDiscount.getDiscountCode());
                    freshOrder.setDiscountValue(orderDiscount.getValue());
                    freshOrder.setDiscountType(orderDiscount.getDiscountType());
                }
            } else {
                // No discount, ensure all discount fields are zero/null
                freshOrder.setDiscountAmount(BigDecimal.ZERO);
            }
            
            // Recalculate tax, service charge, and packaging charge
            OrderCalculationResult calculationResult = recalculateOrderTotals(
                    config,
                    newSubtotalAfterDiscount,
                    freshOrder.getOrderType(),
                    freshOrder.getAdditionalDiscountValue(),
                    freshOrder.getAdditionalDiscountType(),
                    currency,
                    activeOrderedItems,
                    activeOrderedCombos);
            
            // Update order with recalculated amounts
            freshOrder.setSubTotal(CurrencyFormatter.formatAmount(newSubTotal, currency, config.getRoundingPolicy()));
            freshOrder.setTaxAmount(calculationResult.getTaxAmount());
            freshOrder.setAlcoholicTaxAmount(calculationResult.getAlcoholicTaxAmount());
            freshOrder.setNonAlcoholicTaxAmount(calculationResult.getNonAlcoholicTaxAmount());
            freshOrder.setAlcoholicTaxableAmount(calculationResult.getAlcoholicTaxableAmount());
            freshOrder.setNonAlcoholicTaxableAmount(calculationResult.getNonAlcoholicTaxableAmount());
            freshOrder.setServiceChargeAmount(calculationResult.getServiceChargeAmount());
            freshOrder.setPackingChargeAmount(calculationResult.getPackingChargeAmount());
            freshOrder.setAdditionalDiscountAmount(calculationResult.getAdditionalDiscountSavings());
            freshOrder.setTotalAmount(calculationResult.getTotalAmount());
            
            // Update order status based on item/combo statuses
            OrderStatus newOrderStatus = determineOrderStatusBasedOnItems(freshOrder.getId());
            OrderStatus oldOrderStatus = freshOrder.getOrderStatus();
            if (newOrderStatus != oldOrderStatus) {
                freshOrder.setOrderStatus(newOrderStatus);
                log.info("Order {} status updated from {} to {} after item cancellation", 
                        freshOrder.getId(), oldOrderStatus, newOrderStatus);
                
                // Send WebSocket notification for order status update
                try {
                    UUID restaurantId = null;
                    if (freshOrder.getRestaurant() != null) {
                        restaurantId = freshOrder.getRestaurant().getId();
                    }
                    
                    if (restaurantId != null && messagingTemplate != null) {
                        String topic = "/topic/restaurant/" + restaurantId + "/order-status";
                        Map<String, Object> orderData = new HashMap<>();
                        orderData.put("orderId", freshOrder.getId().toString());
                        orderData.put("status", newOrderStatus.toString());
                        orderData.put("restaurantId", restaurantId.toString());
                        orderData.put("notificationType", "ORDER_STATUS_UPDATE");
                        orderData.put("timestamp", LocalDateTime.now(ZoneOffset.UTC).format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
                        
                        StatusEventMessage eventMessage = StatusEventMessage.builder()
                                .message(messageUtil.getMessage("order.update.success", userLocale))
                                .notificationType("ORDER_STATUS_UPDATE")
                                .orderId(freshOrder.getId().toString())
                                .status(newOrderStatus.toString())
                                .data(orderData)
                                .build();
                        messagingTemplate.convertAndSend(topic, eventMessage);
                        log.debug("Sent WebSocket notification for order status update: {} to {} for restaurant {}", 
                                freshOrder.getId(), newOrderStatus, restaurantId);
                    }
                } catch (Exception e) {
                    log.warn("Failed to send WebSocket notification for order status update: {}", e.getMessage());
                }
            }
            
            // Save updated order
            orderRepository.save(freshOrder);
            orderRepository.flush();

            // Align with restaurant-management item status API: if order is CANCELED, cancel linked transaction
            orderRepository.findById(orderId).ifPresent(o -> {
                if (o.getOrderStatus() == OrderStatus.CANCELED) {
                    cancelTransactionIfOrderCanceled(orderId);
                }
            });
            
            log.info("Order {} recalculated successfully after item cancellation. New totals - SubTotal: {}, Discount: {}, Tax: {}, ServiceCharge: {}, PackingCharge: {}, AdditionalDiscount: {}, Total: {}", 
                    freshOrder.getId(), newSubTotal, newDiscountAmount, 
                    calculationResult.getTaxAmount(), calculationResult.getServiceChargeAmount(), 
                    calculationResult.getPackingChargeAmount(), calculationResult.getAdditionalDiscountSavings(), 
                    calculationResult.getTotalAmount());
            
        } catch (Exception e) {
            log.error("Error recalculating order {} after item cancellation: {}", orderId, e.getMessage(), e);
            throw new RuntimeException("Failed to recalculate order after item cancellation", e);
        }
    }
    
    /**
     * Determine order status based on item and combo statuses.
     * If some items are SERVED, some CANCELLED, and some ON_HOLD, order status is SERVED.
     */
    private OrderStatus determineOrderStatusBasedOnItems(UUID orderId) {
        List<OrderedItem> allOrderedItems = orderedItemRepository.findByOrderId(orderId);
        List<OrderedCombo> allOrderedCombos = orderedComboRepository.findByOrderId(orderId);
        
        // Combine items and combos for status determination
        long totalItems = allOrderedItems.size();
        long totalCombos = allOrderedCombos.size();
        long totalEntities = totalItems + totalCombos;
        
        if (totalEntities == 0) {
            return OrderStatus.PUSHED; // Default status for empty orders
        }
        
        // Count items by status
        long pushedItems = allOrderedItems.stream()
                .filter(item -> item.getItemStatus() == com.gulfnet.shared_library.enums.ItemStatus.PUSHED)
                .count();
        long servedItems = allOrderedItems.stream()
                .filter(item -> item.getItemStatus() == com.gulfnet.shared_library.enums.ItemStatus.SERVED)
                .count();
        long canceledItems = allOrderedItems.stream()
                .filter(item -> item.getItemStatus() == com.gulfnet.shared_library.enums.ItemStatus.CANCELED)
                .count();
        long cookingItems = allOrderedItems.stream()
                .filter(item -> item.getItemStatus() == com.gulfnet.shared_library.enums.ItemStatus.COOKING)
                .count();
        long onHoldItems = allOrderedItems.stream()
                .filter(item -> item.getItemStatus() == com.gulfnet.shared_library.enums.ItemStatus.ON_HOLD)
                .count();
        
        // Count combos by status
        long pushedCombos = allOrderedCombos.stream()
                .filter(combo -> combo.getItemStatus() == com.gulfnet.shared_library.enums.ItemStatus.PUSHED)
                .count();
        long servedCombos = allOrderedCombos.stream()
                .filter(combo -> combo.getItemStatus() == com.gulfnet.shared_library.enums.ItemStatus.SERVED)
                .count();
        long canceledCombos = allOrderedCombos.stream()
                .filter(combo -> combo.getItemStatus() == com.gulfnet.shared_library.enums.ItemStatus.CANCELED)
                .count();
        long cookingCombos = allOrderedCombos.stream()
                .filter(combo -> combo.getItemStatus() == com.gulfnet.shared_library.enums.ItemStatus.COOKING)
                .count();
        long onHoldCombos = allOrderedCombos.stream()
                .filter(combo -> combo.getItemStatus() == com.gulfnet.shared_library.enums.ItemStatus.ON_HOLD)
                .count();
        
        // Combined counts
        long totalPushed = pushedItems + pushedCombos;
        long totalServed = servedItems + servedCombos;
        long totalCanceled = canceledItems + canceledCombos;
        long totalCooking = cookingItems + cookingCombos;
        long totalOnHold = onHoldItems + onHoldCombos;
        
        // Rule 1: If all items/combos are CANCELED, order status is CANCELED
        if (totalCanceled == totalEntities) {
            return OrderStatus.CANCELED;
        }
        
        // Rule 2: If all items/combos are SERVED, order status is SERVED
        if (totalServed == totalEntities) {
            return OrderStatus.SERVED;
        }
        
        // Rule 2a: If all non-cancelled items are SERVED, order status is SERVED
        long nonCanceledItems = totalItems - canceledItems;
        long nonCanceledCombos = totalCombos - canceledCombos;
        long totalNonCanceled = nonCanceledItems + nonCanceledCombos;
        if (totalNonCanceled > 0 && totalServed == totalNonCanceled) {
            return OrderStatus.SERVED;
        }
        
        // Rule 2b: If some items are SERVED and remaining non-cancelled items are only ON_HOLD (or SERVED), order status is SERVED
        // This handles the case: some SERVED, some CANCELLED, some ON_HOLD -> order should be SERVED
        if (totalServed > 0 && totalNonCanceled > 0) {
            // Check if all non-cancelled items/combos are either SERVED or ON_HOLD
            long nonCanceledNonServedNonOnHold = totalEntities - totalCanceled - totalServed - totalOnHold;
            if (nonCanceledNonServedNonOnHold == 0 && totalServed > 0) {
                // All non-cancelled items are either SERVED or ON_HOLD, and at least one is SERVED
                return OrderStatus.SERVED;
            }
        }
        
        // Rule 3: If ANY item/combo is COOKING, order status is IN_PROGRESS (highest priority after CANCELED/SERVED)
        if (totalCooking > 0) {
            return OrderStatus.IN_PROGRESS;
        }
        
        // Rule 4: If all items/combos are PUSHED (and none are COOKING), order status is PUSHED
        if (totalPushed == totalEntities) {
            return OrderStatus.PUSHED;
        }
        
        // Rule 5: If any item/combo is in other statuses (DELAYED, READY, or mixed PUSHED with others), order status is IN_PROGRESS
        // Note: ON_HOLD is now handled separately in Rule 2b
        long otherStatusEntities = totalEntities - totalPushed - totalServed - totalCanceled - totalCooking - totalOnHold;
        if (otherStatusEntities > 0 || totalPushed > 0) {
            return OrderStatus.IN_PROGRESS;
        }
        
        // Default fallback
        return OrderStatus.PUSHED;
    }

    private PaymentSystemType getChainPaymentTypeForCancellationPolicy() {
        try {
            RestaurantChainConfigCache config = getRestaurantChainConfig();
            return config != null ? config.getPaymentType() : null;
        } catch (Exception e) {
            log.warn("Failed to resolve chain payment type for cancellation policy: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Same rule as item {@code cancelItemWithoutDeduction}: skip monetary adjustment only when
     * transaction was {@link TransactionStatus#COMPLETED} and order is takeaway or chain is prepaid.
     */
    private boolean shouldSkipOrderAmountAdjustmentOnCancellation(Order order, TransactionStatus transactionStatusBeforeCancel) {
        if (order == null) {
            return false;
        }
        return CancellationAmountPolicy.shouldSkipOrderAmountAdjustmentOnCancellation(
                order.getOrderType(), getChainPaymentTypeForCancellationPolicy(), transactionStatusBeforeCancel);
    }

    /**
     * Recalculate order totals after order cancellation (all items canceled)
     * Sets all amounts to zero
     */
    private void recalculateOrderAfterOrderCancellation(Order order, Locale userLocale, User updatedByUser, OffsetDateTime updatedAt) {
        UUID orderId = order.getId();
        log.info("Recalculating order {} after order cancellation (all items canceled)", orderId);
        
        try {
            // CRITICAL: Preserve original audit fields and cancellation review info before any operations
            // This prevents creating a new order record with wrong createdBy/createdAt
            OffsetDateTime originalCreatedAt = order.getCreatedAt();
            User originalCreatedBy = order.getCreatedBy();
            String originalOrderNumber = order.getOrderNumber();
            OffsetDateTime cancellationReviewedAt = order.getCancellationReviewedAt();
            User cancellationReviewedBy = order.getCancellationReviewedBy();
            String cancellationComments = order.getCancellationComments();
            OrderStatus orderStatus = order.getOrderStatus();
            RequestStatus cancellationRequestStatus = order.getCancellationRequestStatus();
            
            // Work directly with the managed entity - no need to clear or refresh
            // This preserves lazy-loaded relationships and prevents LazyInitializationException
            // The entity is already in the persistence context and up-to-date after flush()
            Order freshOrder = order;
            
            // CRITICAL: Restore original audit fields to prevent new record creation
            // These fields are updatable=false, but we need to ensure they're set correctly
            // to prevent JPA from treating this as a new entity
            if (originalCreatedAt != null) {
                freshOrder.setCreatedAt(originalCreatedAt);
            }
            if (originalCreatedBy != null) {
                freshOrder.setCreatedBy(originalCreatedBy);
            }
            if (originalOrderNumber != null) {
                freshOrder.setOrderNumber(originalOrderNumber);
            }
            
            // Restore cancellation review information
            if (cancellationReviewedAt != null) {
                freshOrder.setCancellationReviewedAt(cancellationReviewedAt);
            }
            if (cancellationReviewedBy != null) {
                freshOrder.setCancellationReviewedBy(cancellationReviewedBy);
            }
            if (cancellationComments != null) {
                freshOrder.setCancellationComments(cancellationComments);
            }
            if (orderStatus != null) {
                freshOrder.setOrderStatus(orderStatus);
            }
            if (cancellationRequestStatus != null) {
                freshOrder.setCancellationRequestStatus(cancellationRequestStatus);
            }
            
            // Update audit fields
            freshOrder.setUpdatedAt(updatedAt);
            freshOrder.setUpdatedBy(updatedByUser);
            
            CancellationAmountPolicy.resetOrderMonetaryTotalsForFullCancellation(freshOrder);
            
            // Save updated order (this will UPDATE the existing entity, not create a new one)
            orderRepository.save(freshOrder);
            orderRepository.flush();
            
            log.info("Order {} recalculated successfully after order cancellation. All amounts set to zero.", orderId);
            
        } catch (Exception e) {
            log.error("Error recalculating order {} after order cancellation: {}", orderId, e.getMessage(), e);
            throw new RuntimeException("Failed to recalculate order after order cancellation", e);
        }
    }

    private java.math.RoundingMode resolveChainMoneyDivideRounding(com.gulfnet.shared_library.enums.RoundingMode chainPolicy) {
        com.gulfnet.shared_library.enums.RoundingMode effective = chainPolicy != null
                ? chainPolicy
                : CurrencyFormatter.getDefaultRoundingPolicy();
        return CurrencyFormatter.resolveRoundingMode(effective);
    }

    private BigDecimal calculateChargeAmountUnformatted(BigDecimal baseAmount, BigDecimal value,
                                                        ChargeType type, java.math.RoundingMode divideRounding) {
        if (type == null) {
            type = ChargeType.PERCENT;
        }
        if (type == ChargeType.PERCENT) {
            return baseAmount.multiply(value).divide(BigDecimal.valueOf(100), 10, divideRounding);
        }
        return value;
    }

    /**
     * Helper method to calculate order discount amount
     */
    private BigDecimal calculateOrderDiscountAmount(Discount discount, BigDecimal subTotal, String currency,
                                                    com.gulfnet.shared_library.enums.RoundingMode chainRoundingPolicy) {
        if (discount == null || discount.getDiscountType() == null) {
            return BigDecimal.ZERO;
        }

        java.math.RoundingMode divideRm = resolveChainMoneyDivideRounding(chainRoundingPolicy);

        BigDecimal discountAmount = BigDecimal.ZERO;

        if (discount.getDiscountType() == DiscountType.PERCENT) {
            discountAmount = subTotal.multiply(discount.getValue())
                    .divide(BigDecimal.valueOf(100), 10, divideRm);
        } else if (discount.getDiscountType() == DiscountType.FLAT) {
            discountAmount = discount.getValue();
        }

        return CurrencyFormatter.formatAmount(discountAmount, currency, chainRoundingPolicy);
    }

    /**
     * Recalculates tax, charges, taxable split, and total using the same rules as restaurant-management
     * {@code OrderPricingServiceImpl} (unformatted charge bases, format + reconcile tax and taxable lines,
     * chain {@link com.gulfnet.shared_library.enums.RoundingMode} via {@link CurrencyFormatter}).
     */
    private OrderCalculationResult recalculateOrderTotals(
            RestaurantChainConfigCache config,
            BigDecimal subtotalAfterDiscount,
            OrderType orderType,
            BigDecimal additionalDiscountValue,
            DiscountType additionalDiscountType,
            String currency,
            List<OrderedItem> orderedItems,
            List<OrderedCombo> orderedCombos) {

        com.gulfnet.shared_library.enums.RoundingMode roundingPolicy =
                config != null ? config.getRoundingPolicy() : null;
        java.math.RoundingMode divideRm = resolveChainMoneyDivideRounding(roundingPolicy);

        BigDecimal alcoholicSubtotal = BigDecimal.ZERO;
        BigDecimal nonAlcoholicSubtotal = BigDecimal.ZERO;

        if (orderedItems != null) {
            for (OrderedItem orderedItem : orderedItems) {
                if (orderedItem == null) {
                    continue;
                }

                BigDecimal itemAmount = orderedItem.getTotalDiscountedItemAmount() != null
                        ? orderedItem.getTotalDiscountedItemAmount()
                        : (orderedItem.getTotalItemAmount() != null ? orderedItem.getTotalItemAmount() : BigDecimal.ZERO);

                com.gulfnet.shared_library.enums.AlcoholType alcoholType = orderedItem.getAlcoholType();
                if (alcoholType == null && orderedItem.getItem() != null) {
                    alcoholType = orderedItem.getItem().getAlcoholType();
                }

                if (alcoholType == com.gulfnet.shared_library.enums.AlcoholType.ALCOHOLIC) {
                    alcoholicSubtotal = alcoholicSubtotal.add(itemAmount);
                } else {
                    nonAlcoholicSubtotal = nonAlcoholicSubtotal.add(itemAmount);
                }
            }
        }

        BigDecimal combosSubTotal = BigDecimal.ZERO;
        if (orderedCombos != null) {
            for (OrderedCombo orderedCombo : orderedCombos) {
                if (orderedCombo == null) {
                    continue;
                }
                BigDecimal comboAmount = orderedCombo.getTotalComboAmount() != null
                        ? orderedCombo.getTotalComboAmount()
                        : (orderedCombo.getPrice() != null ? orderedCombo.getPrice() : BigDecimal.ZERO);
                combosSubTotal = combosSubTotal.add(comboAmount);
            }
        }

        BigDecimal splitTotal = alcoholicSubtotal.add(nonAlcoholicSubtotal);

        BigDecimal itemsOnlyDiscountedSubtotal = splitTotal;
        BigDecimal fullSubtotal = splitTotal.add(combosSubTotal);
        if (fullSubtotal.compareTo(BigDecimal.ZERO) > 0 && splitTotal.compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal discountRatio = subtotalAfterDiscount.divide(fullSubtotal, 10, divideRm);
            itemsOnlyDiscountedSubtotal = splitTotal.multiply(discountRatio);
        }

        if (splitTotal.compareTo(BigDecimal.ZERO) > 0 && itemsOnlyDiscountedSubtotal.compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal scaleFactor = itemsOnlyDiscountedSubtotal.divide(splitTotal, 10, divideRm);
            alcoholicSubtotal = alcoholicSubtotal.multiply(scaleFactor);
            nonAlcoholicSubtotal = nonAlcoholicSubtotal.multiply(scaleFactor);
        } else {
            alcoholicSubtotal = BigDecimal.ZERO;
            nonAlcoholicSubtotal = itemsOnlyDiscountedSubtotal;
        }

        BigDecimal unformattedServiceChargeAmount = BigDecimal.ZERO;
        BigDecimal unformattedPackingChargeAmount = BigDecimal.ZERO;
        BigDecimal serviceChargeAmount = BigDecimal.ZERO;
        BigDecimal packingChargeAmount = BigDecimal.ZERO;

        if (config != null && orderType != null) {
            if (orderType == OrderType.DINE_IN) {
                AccountSettingsDto.ServiceChargesForDineIn service = config.getServiceChargesForDineIn();
                if (service != null) {
                    unformattedServiceChargeAmount = calculateChargeAmountUnformatted(
                            subtotalAfterDiscount,
                            BigDecimal.valueOf(service.getValue()),
                            service.getType(),
                            divideRm);
                    serviceChargeAmount = CurrencyFormatter.formatAmount(unformattedServiceChargeAmount, currency, roundingPolicy);
                }
            } else if (orderType == OrderType.TAKEAWAY) {
                if (config.isIncludePackingChargesForTakeaway() && config.getPackingChargesForTakeaway() != null) {
                    AccountSettingsDto.PackingChargesForTakeaway packing = config.getPackingChargesForTakeaway();
                    unformattedPackingChargeAmount = calculateChargeAmountUnformatted(
                            subtotalAfterDiscount,
                            BigDecimal.valueOf(packing.getValue()),
                            packing.getType(),
                            divideRm);
                    packingChargeAmount = CurrencyFormatter.formatAmount(unformattedPackingChargeAmount, currency, roundingPolicy);
                }
            }
        }

        BigDecimal unformattedChargeForRatio = orderType == OrderType.DINE_IN
                ? unformattedServiceChargeAmount
                : unformattedPackingChargeAmount;

        BigDecimal chargeToAlcoholic = BigDecimal.ZERO;
        BigDecimal chargeToNonAlcoholic = BigDecimal.ZERO;
        BigDecimal denomForChargeSplit = alcoholicSubtotal.add(nonAlcoholicSubtotal);
        if (unformattedChargeForRatio.compareTo(BigDecimal.ZERO) > 0 && denomForChargeSplit.compareTo(BigDecimal.ZERO) > 0) {
            chargeToAlcoholic = unformattedChargeForRatio.multiply(alcoholicSubtotal)
                    .divide(denomForChargeSplit, 20, divideRm);
            chargeToNonAlcoholic = unformattedChargeForRatio.subtract(chargeToAlcoholic);
        }

        BigDecimal alcoholicTaxBase = alcoholicSubtotal.add(chargeToAlcoholic);
        BigDecimal nonAlcoholicTaxBase = nonAlcoholicSubtotal.add(chargeToNonAlcoholic);

        BigDecimal totalTaxableBaseFormatted = CurrencyFormatter.formatAmount(
                alcoholicTaxBase.add(nonAlcoholicTaxBase), currency, roundingPolicy);
        BigDecimal alcoholicTaxableAmount = CurrencyFormatter.formatAmount(alcoholicTaxBase, currency, roundingPolicy);
        BigDecimal nonAlcoholicTaxableAmount = totalTaxableBaseFormatted.subtract(alcoholicTaxableAmount);

        BigDecimal taxAmount = BigDecimal.ZERO;
        BigDecimal alcoholicTaxAmount = BigDecimal.ZERO;
        BigDecimal nonAlcoholicTaxAmount = BigDecimal.ZERO;

        AccountSettingsDto.TaxSetup.TaxCharge alcoholicTaxCharge = null;
        AccountSettingsDto.TaxSetup.TaxCharge nonAlcoholicTaxCharge = null;

        if (config != null && config.getTaxSetup() != null && orderType != null) {
            AccountSettingsDto.TaxSetup taxSetup = config.getTaxSetup();
            if (orderType == OrderType.DINE_IN && taxSetup.getDineIn() != null) {
                alcoholicTaxCharge = taxSetup.getDineIn().getAlcoholic();
                nonAlcoholicTaxCharge = taxSetup.getDineIn().getNonAlcoholic();
            } else if (orderType == OrderType.TAKEAWAY && taxSetup.getTakeAway() != null) {
                alcoholicTaxCharge = taxSetup.getTakeAway().getAlcoholic();
                nonAlcoholicTaxCharge = taxSetup.getTakeAway().getNonAlcoholic();
            }

            BigDecimal alcoholicTaxUnformatted = BigDecimal.ZERO;
            BigDecimal nonAlcoholicTaxUnformatted = BigDecimal.ZERO;

            if (alcoholicTaxCharge != null && alcoholicTaxBase.compareTo(BigDecimal.ZERO) > 0) {
                alcoholicTaxUnformatted = calculateChargeAmountUnformatted(
                        alcoholicTaxBase,
                        BigDecimal.valueOf(alcoholicTaxCharge.getValue()),
                        alcoholicTaxCharge.getType(),
                        divideRm);
            }
            if (nonAlcoholicTaxCharge != null && nonAlcoholicTaxBase.compareTo(BigDecimal.ZERO) > 0) {
                nonAlcoholicTaxUnformatted = calculateChargeAmountUnformatted(
                        nonAlcoholicTaxBase,
                        BigDecimal.valueOf(nonAlcoholicTaxCharge.getValue()),
                        nonAlcoholicTaxCharge.getType(),
                        divideRm);
            }

            alcoholicTaxAmount = CurrencyFormatter.formatAmount(alcoholicTaxUnformatted, currency, roundingPolicy);
            nonAlcoholicTaxAmount = CurrencyFormatter.formatAmount(nonAlcoholicTaxUnformatted, currency, roundingPolicy);

            if (alcoholicTaxCharge != null && nonAlcoholicTaxCharge != null
                    && alcoholicTaxCharge.getType() == ChargeType.PERCENT
                    && nonAlcoholicTaxCharge.getType() == ChargeType.PERCENT
                    && alcoholicTaxCharge.getValue() != 0
                    && nonAlcoholicTaxCharge.getValue() != 0) {
                BigDecimal alcoholicTaxableDerived = alcoholicTaxUnformatted
                        .multiply(BigDecimal.valueOf(100))
                        .divide(BigDecimal.valueOf(alcoholicTaxCharge.getValue()), 10, divideRm);
                alcoholicTaxableAmount = CurrencyFormatter.formatAmount(alcoholicTaxableDerived, currency, roundingPolicy);
                nonAlcoholicTaxableAmount = totalTaxableBaseFormatted.subtract(alcoholicTaxableAmount);
            }

            taxAmount = alcoholicTaxAmount.add(nonAlcoholicTaxAmount);
        }

        BigDecimal totalBeforeAdditionalDiscount = CurrencyFormatter.formatAmount(
                subtotalAfterDiscount.add(taxAmount).add(serviceChargeAmount).add(packingChargeAmount),
                currency,
                roundingPolicy);

        BigDecimal additionalDiscountSavings = BigDecimal.ZERO;
        if (additionalDiscountValue != null && additionalDiscountType != null) {
            if (additionalDiscountType == DiscountType.PERCENT) {
                additionalDiscountSavings = CurrencyFormatter.formatAmount(
                        totalBeforeAdditionalDiscount.multiply(additionalDiscountValue)
                                .divide(BigDecimal.valueOf(100), 10, divideRm),
                        currency,
                        roundingPolicy);
            } else if (additionalDiscountType == DiscountType.FLAT) {
                additionalDiscountSavings = CurrencyFormatter.formatAmount(additionalDiscountValue, currency, roundingPolicy);
            }
        }

        BigDecimal totalAmount = CurrencyFormatter.formatAmount(
                totalBeforeAdditionalDiscount.subtract(additionalDiscountSavings),
                currency,
                roundingPolicy);

        if (totalAmount.compareTo(BigDecimal.ZERO) < 0) {
            totalAmount = BigDecimal.ZERO;
        }

        return new OrderCalculationResult(
                subtotalAfterDiscount,
                BigDecimal.ZERO,
                taxAmount,
                alcoholicTaxAmount,
                nonAlcoholicTaxAmount,
                alcoholicTaxableAmount,
                nonAlcoholicTaxableAmount,
                serviceChargeAmount,
                packingChargeAmount,
                additionalDiscountSavings,
                totalAmount
        );
    }

    /**
     * Helper class for order calculation result
     */
    private static class OrderCalculationResult {
        private final BigDecimal subTotal;
        private final BigDecimal orderDiscountSavings;
        private final BigDecimal taxAmount;
        private final BigDecimal alcoholicTaxAmount;
        private final BigDecimal nonAlcoholicTaxAmount;
        private final BigDecimal alcoholicTaxableAmount;
        private final BigDecimal nonAlcoholicTaxableAmount;
        private final BigDecimal serviceChargeAmount;
        private final BigDecimal packingChargeAmount;
        private final BigDecimal additionalDiscountSavings;
        private final BigDecimal totalAmount;

        public OrderCalculationResult(BigDecimal subTotal, BigDecimal orderDiscountSavings,
                                     BigDecimal taxAmount, BigDecimal alcoholicTaxAmount,
                                     BigDecimal nonAlcoholicTaxAmount,
                                     BigDecimal alcoholicTaxableAmount, BigDecimal nonAlcoholicTaxableAmount,
                                     BigDecimal serviceChargeAmount,
                                     BigDecimal packingChargeAmount, BigDecimal additionalDiscountSavings,
                                     BigDecimal totalAmount) {
            this.subTotal = subTotal;
            this.orderDiscountSavings = orderDiscountSavings;
            this.taxAmount = taxAmount;
            this.alcoholicTaxAmount = alcoholicTaxAmount;
            this.nonAlcoholicTaxAmount = nonAlcoholicTaxAmount;
            this.alcoholicTaxableAmount = alcoholicTaxableAmount;
            this.nonAlcoholicTaxableAmount = nonAlcoholicTaxableAmount;
            this.serviceChargeAmount = serviceChargeAmount;
            this.packingChargeAmount = packingChargeAmount;
            this.additionalDiscountSavings = additionalDiscountSavings;
            this.totalAmount = totalAmount;
        }

        public BigDecimal getSubTotal() { return subTotal; }
        public BigDecimal getOrderDiscountSavings() { return orderDiscountSavings; }
        public BigDecimal getTaxAmount() { return taxAmount; }
        public BigDecimal getAlcoholicTaxAmount() { return alcoholicTaxAmount; }
        public BigDecimal getNonAlcoholicTaxAmount() { return nonAlcoholicTaxAmount; }
        public BigDecimal getAlcoholicTaxableAmount() { return alcoholicTaxableAmount; }
        public BigDecimal getNonAlcoholicTaxableAmount() { return nonAlcoholicTaxableAmount; }
        public BigDecimal getServiceChargeAmount() { return serviceChargeAmount; }
        public BigDecimal getPackingChargeAmount() { return packingChargeAmount; }
        public BigDecimal getAdditionalDiscountSavings() { return additionalDiscountSavings; }
        public BigDecimal getTotalAmount() { return totalAmount; }
    }

    /**
     * Fetch restaurant chain config from restaurant-management API
     * Returns cached config or fetches from API
     */
    private RestaurantChainConfigCache getRestaurantChainConfig() {
        try {
            String url = restaurantManagementBaseUrl + "/api/v1/restaurantchain/config";
            log.debug("Fetching restaurant chain config from (WebClient): {}", url);

            org.springframework.core.ParameterizedTypeReference<ResponseDto<RestaurantChainDto<RestaurantChainResponse>>> typeRef =
                    new org.springframework.core.ParameterizedTypeReference<ResponseDto<RestaurantChainDto<RestaurantChainResponse>>>() {};

            ResponseDto<RestaurantChainDto<RestaurantChainResponse>> response =
                    webClientBuilder.build()
                            .get()
                            .uri(url)
                            .retrieve()
                            .bodyToMono(typeRef)
                            .block();

            if (response != null && response.getData() != null) {
                RestaurantChainDto<RestaurantChainResponse> dto = response.getData();
                RestaurantChainResponse chainResponse = dto.getRestaurantChain();
                AccountSettingsDto accountSettings = dto.getAccountSettings();

                if (chainResponse != null && accountSettings != null) {
                    return new RestaurantChainConfigCache(
                            chainResponse.getCurrency(),
                            accountSettings.getTaxSetup(),
                            accountSettings.getServiceChargesForDineIn(),
                            accountSettings.getPackingChargesForTakeaway(),
                            accountSettings.isIncludePackingChargesForTakeaway(),
                            chainResponse.getPaymentType(),
                            chainResponse.getRoundingMode()
                    );
                }
            }
        } catch (Exception e) {
            log.warn("Failed to fetch restaurant chain config from API via WebClient: {}", e.getMessage());
        }

        // Return default values if API call fails
        return new RestaurantChainConfigCache("¥", null, null, null, false, null, null);
    }

    /**
     * Cache class for restaurant chain config
     */
    private static class RestaurantChainConfigCache {
        private final String currency;
        private final AccountSettingsDto.TaxSetup taxSetup;
        private final AccountSettingsDto.ServiceChargesForDineIn serviceChargesForDineIn;
        private final AccountSettingsDto.PackingChargesForTakeaway packingChargesForTakeaway;
        private final boolean includePackingChargesForTakeaway;
        private final com.gulfnet.shared_library.enums.PaymentSystemType paymentType;
        private final com.gulfnet.shared_library.enums.RoundingMode roundingPolicy;

        public RestaurantChainConfigCache(String currency, AccountSettingsDto.TaxSetup taxSetup,
                                         AccountSettingsDto.ServiceChargesForDineIn serviceChargesForDineIn,
                                         AccountSettingsDto.PackingChargesForTakeaway packingChargesForTakeaway,
                                         boolean includePackingChargesForTakeaway,
                                         com.gulfnet.shared_library.enums.PaymentSystemType paymentType,
                                         com.gulfnet.shared_library.enums.RoundingMode roundingPolicy) {
            this.currency = currency;
            this.taxSetup = taxSetup;
            this.serviceChargesForDineIn = serviceChargesForDineIn;
            this.packingChargesForTakeaway = packingChargesForTakeaway;
            this.includePackingChargesForTakeaway = includePackingChargesForTakeaway;
            this.paymentType = paymentType;
            this.roundingPolicy = roundingPolicy;
        }

        public String getCurrency() { return currency; }
        public AccountSettingsDto.TaxSetup getTaxSetup() { return taxSetup; }
        public AccountSettingsDto.ServiceChargesForDineIn getServiceChargesForDineIn() { return serviceChargesForDineIn; }
        public AccountSettingsDto.PackingChargesForTakeaway getPackingChargesForTakeaway() { return packingChargesForTakeaway; }
        public boolean isIncludePackingChargesForTakeaway() { return includePackingChargesForTakeaway; }
        public com.gulfnet.shared_library.enums.PaymentSystemType getPaymentType() { return paymentType; }
        public com.gulfnet.shared_library.enums.RoundingMode getRoundingPolicy() { return roundingPolicy; }
    }

    /**
     * Get currency from restaurant chain config, with local default handling.
     * Expects the caller to pass a config fetched once per request/operation.
     */
    private String getCurrencyFromConfig(RestaurantChainConfigCache config) {
        String currency = (config != null) ? config.getCurrency() : null;
        return (currency != null && !currency.trim().isEmpty()) ? currency : "¥";
    }
    
    /**
     * Get tax rate from restaurant chain config API.
     * Expects the caller to pass a config fetched once per request/operation.
     */
    private int getTaxRateFromConfig(RestaurantChainConfigCache config, OrderType orderType) {
        if (config == null) {
            // Default tax rate if config is not available
            return 5;
        }
        AccountSettingsDto.TaxSetup taxSetup = config.getTaxSetup();
        
        if (taxSetup != null) {
            if (orderType == OrderType.DINE_IN) {
                if (taxSetup.getDineIn() != null) {
                    // Return average of alcoholic and non-alcoholic values (for PERCENT type)
                    // For FLAT type, return 0 as this method is used for percentage calculations
                    double alcoholicValue = taxSetup.getDineIn().getAlcoholic() != null && 
                                          taxSetup.getDineIn().getAlcoholic().getType() == com.gulfnet.shared_library.enums.ChargeType.PERCENT
                                          ? taxSetup.getDineIn().getAlcoholic().getValue() : 0;
                    double nonAlcoholicValue = taxSetup.getDineIn().getNonAlcoholic() != null && 
                                             taxSetup.getDineIn().getNonAlcoholic().getType() == com.gulfnet.shared_library.enums.ChargeType.PERCENT
                                             ? taxSetup.getDineIn().getNonAlcoholic().getValue() : 0;
                    return (int) ((alcoholicValue + nonAlcoholicValue) / 2);
                }
            } else {
                if (taxSetup.getTakeAway() != null) {
                    // Return average of alcoholic and non-alcoholic values (for PERCENT type)
                    double alcoholicValue = taxSetup.getTakeAway().getAlcoholic() != null && 
                                          taxSetup.getTakeAway().getAlcoholic().getType() == com.gulfnet.shared_library.enums.ChargeType.PERCENT
                                          ? taxSetup.getTakeAway().getAlcoholic().getValue() : 0;
                    double nonAlcoholicValue = taxSetup.getTakeAway().getNonAlcoholic() != null && 
                                             taxSetup.getTakeAway().getNonAlcoholic().getType() == com.gulfnet.shared_library.enums.ChargeType.PERCENT
                                             ? taxSetup.getTakeAway().getNonAlcoholic().getValue() : 0;
                    return (int) ((alcoholicValue + nonAlcoholicValue) / 2);
                }
            }
        }
        
        // Default tax rate
        return 5;
    }

    /**
     * Get service charge rate from restaurant chain config API.
     * Expects the caller to pass a config fetched once per request/operation.
     */
    private int getServiceChargeRateFromConfig(RestaurantChainConfigCache config, OrderType orderType) {
        if (config == null) {
            // Default service charge rate if config is not available
            return 5;
        }
        
            if (orderType == OrderType.DINE_IN) {
            AccountSettingsDto.ServiceChargesForDineIn serviceChargesForDineIn = config.getServiceChargesForDineIn();
            if (serviceChargesForDineIn != null) {
                    // Return value for PERCENT type, 0 for FLAT type (as this method is for percentage calculations)
                return serviceChargesForDineIn.getType() == com.gulfnet.shared_library.enums.ChargeType.PERCENT
                       ? (int) serviceChargesForDineIn.getValue() : 0;
                }
            } else {
                // No service charge for takeaway - packaging charges are handled separately
                return 0;
        }
        
        // Default service charge rate
        return 5;
    }

    /**
     * Get packing charge from restaurant chain config API.
     * Expects the caller to pass a config fetched once per request/operation.
     */
    private double getPackingChargeFromConfig(RestaurantChainConfigCache config, OrderType orderType) {
        if (orderType == OrderType.TAKEAWAY) {
            if (config != null && config.getPackingChargesForTakeaway() != null) {
                // Return value for PERCENT type (used as percentage), value for FLAT type (used as flat amount)
                // This method is used in percentage calculations, so for FLAT we return 0
                return config.getPackingChargesForTakeaway().getType() == com.gulfnet.shared_library.enums.ChargeType.PERCENT
                       ? config.getPackingChargesForTakeaway().getValue() : 0.0;
            }
        }
        
        // Default packing charge
        return 0.0;
    }

    /**
     * Check if packing charge should be included.
     * Expects the caller to pass a config fetched once per request/operation.
     */
    private boolean shouldIncludePackingCharge(RestaurantChainConfigCache config, OrderType orderType) {
        if (orderType == OrderType.TAKEAWAY) {
            if (config != null) {
                return config.isIncludePackingChargesForTakeaway();
            }
        }
        
        // Default: include for takeaway
        return orderType == OrderType.TAKEAWAY;
    }

    @Override
    @Transactional
    public ResponseDto<DeviceTokenResponse> updateDeviceToken(UUID userId, UpdateDeviceTokenRequest request, String updaterId) {
        Locale userLocale = LocaleContextHolder.getLocale();

        // Find the user
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        messageUtil.getMessage("user.not.found", userLocale)));

        if (Boolean.TRUE.equals(user.getIsDeleted())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    messageUtil.getMessage("user.update.error.deleted", userLocale));
        }

        // Validate updater exists
        User updater = userRepository.findById(UUID.fromString(updaterId))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        messageUtil.getMessage("user.not.found", userLocale)));

        // Update device token information
        user.setDeviceToken(request.getDeviceToken());
        if (request.getDeviceType() != null) {
            user.setDeviceType(request.getDeviceType());
        }
        if (request.getAppType() != null) {
            user.setAppType(request.getAppType());
        }
        user.setUpdatedAt(OffsetDateTime.now(ZoneOffset.UTC));
        user.setUpdatedBy(updater);

        userRepository.save(user);

        // Build response
        DeviceTokenResponse response = DeviceTokenResponse.builder()
                .deviceToken(user.getDeviceToken())
                .deviceType(user.getDeviceType())
                .appType(user.getAppType())
                .isUpdated(true)
                .build();

        return ResponseDto.<DeviceTokenResponse>builder()
                .message(messageUtil.getMessage("user.device.token.updated.success", userLocale))
                .data(response)
                .build();
    }

    // ==================== NOTIFICATION HELPER METHODS ====================
    
    /**
     * Save notification for profile update request approval/decline
     */
    private void saveProfileUpdateRequestNotification(User requester, RequestStatus action, User manager, Locale userLocale, 
                                                      String comments, User user) {
        try {
            // Ensure requester is a managed entity
            User managedRequester = userRepository.findById(requester.getId())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, 
                            "Requester not found: " + requester.getId()));
            
            // Ensure manager is a managed entity
            User managedManager = userRepository.findById(manager.getId())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, 
                            "Manager not found: " + manager.getId()));
            
            String notificationType = action == RequestStatus.APPROVED 
                    ? "PROFILE_UPDATE_REQUEST_APPROVED" 
                    : "PROFILE_UPDATE_REQUEST_DECLINED";
            
            String userName = user.getFirstName() + " " + user.getLastName();
            String commentsText = comments != null ? comments : "";
            
            String titleKey = action == RequestStatus.APPROVED
                    ? "notification.profile.update.request.approved.title"
                    : "notification.profile.update.request.declined.title";
            String bodyKey = action == RequestStatus.APPROVED
                    ? "notification.profile.update.request.approved.body"
                    : "notification.profile.update.request.declined.body";
            
            Locale loc = localeForRecipient(managedRequester, userLocale);
            String title = messageUtil.getMessage(titleKey, loc);
            String message = messageUtil.getMessage(bodyKey, loc, userName, commentsText);
            
            java.util.Map<String, String> requestData = new java.util.HashMap<>();
            requestData.put("requestId", managedRequester.getId().toString());
            requestData.put("userId", managedRequester.getId().toString());
            
            Notification notification = Notification.builder()
                    .user(managedRequester)
                    .title(title)
                    .type(notificationType)
                    .message(message)
                    .bodyKey(bodyKey)
                    .bodyArgs(serializeBodyArgs(userName, commentsText))
                    .additionalData(serializeRequestData(requestData))
                    .createdBy(managedManager)
                    .read(false)
                    .createdAt(OffsetDateTime.now(ZoneOffset.UTC))
                    .build();
            
            Notification savedNotification = notificationRepository.saveAndFlush(notification);
            log.info("Successfully saved profile update request notification - ID: {}, type: {}, requester: {}", 
                    savedNotification.getId(), notificationType, requester.getId());
            
            // Publish notification to RabbitMQ for FCM processing
            try {
                notificationPublisherService.publishNotification(savedNotification, managedRequester);
            } catch (Exception e) {
                log.error("Failed to publish notification to RabbitMQ: {}", e.getMessage(), e);
            }
        } catch (Exception e) {
            log.error("Failed to save profile update request notification: {}", e.getMessage(), e);
        }
    }
    
    /**
     * Save notification for additional discount request approval/decline
     */
    private void saveAdditionalDiscountRequestNotification(User requester, RequestStatus action, String orderNumber, UUID orderId, User manager, String comments, Locale userLocale) {
        try {
            Locale loc = localeForRecipient(requester, userLocale);
            String notificationType = action == RequestStatus.APPROVED 
                    ? "ADDITIONAL_DISCOUNT_REQUEST_APPROVED" 
                    : "ADDITIONAL_DISCOUNT_REQUEST_DECLINED";
            String title = action == RequestStatus.APPROVED
                    ? messageUtil.getMessage("notification.additional.discount.request.approved.title", loc)
                    : messageUtil.getMessage("notification.additional.discount.request.declined.title", loc);
            String message = action == RequestStatus.APPROVED
                    ? messageUtil.getMessage("notification.additional.discount.request.approved", loc, orderNumber)
                    : messageUtil.getMessage("notification.additional.discount.request.declined", loc, orderNumber);
            
            String bodyKey = action == RequestStatus.APPROVED
                    ? "notification.additional.discount.request.approved"
                    : "notification.additional.discount.request.declined";
            
            java.util.Map<String, String> requestData = new java.util.HashMap<>();
            if (orderId != null) {
                requestData.put("requestId", orderId.toString());
                requestData.put("orderId", orderId.toString());
            }
            if (orderNumber != null) {
                requestData.put("orderNumber", orderNumber);
            }
            
            Notification notification = Notification.builder()
                    .user(requester)
                    .title(title)
                    .type(notificationType)
                    .message(message)
                    .bodyKey(bodyKey)
                    .bodyArgs(serializeBodyArgs(orderNumber))
                    .additionalData(serializeRequestData(requestData))
                    .createdBy(manager)
                    .read(false)
                    .createdAt(OffsetDateTime.now(ZoneOffset.UTC))
                    .build();
            
            Notification savedNotification = notificationRepository.save(notification);
            log.info("Saved notification for additional discount request {} for user {}", action, requester.getId());
            
            // Publish notification to RabbitMQ for FCM processing
            try {
                notificationPublisherService.publishNotification(savedNotification, requester);
            } catch (Exception e) {
                log.error("Failed to publish notification to RabbitMQ: {}", e.getMessage(), e);
            }
            
            // Send WebSocket notification for cashiers (Windows app doesn't support FCM)
            // Note: NotificationService is in restaurant-management module, so it may not be available
            // If cross-module dependency is not possible, consider using a message queue or event-based approach
            try {
                // Only send WebSocket notification if requester is a cashier
                if (isCashier(requester)) {
                    sendCashierDiscountNotification(orderId, orderNumber, requester, action == RequestStatus.APPROVED, comments, userLocale);
                    log.info("Sent WebSocket notification for additional discount request {} to cashier {}", action, requester.getId());
                } else {
                    log.debug("Requester {} is not a cashier, skipping WebSocket notification", requester.getId());
                }
            } catch (Exception e) {
                log.error("Failed to send WebSocket notification for discount request to cashier {}: {}", requester.getId(), e.getMessage(), e);
                // Don't rethrow - notification is already saved to DB, WebSocket is just for real-time updates
            }
        } catch (Exception e) {
            log.error("Failed to save additional discount request notification: {}", e.getMessage(), e);
        }
    }
    
    /**
     * Check if a user is a cashier
     */
    private boolean isCashier(User user) {
        if (user == null || user.getRoleId() == null) {
            return false;
        }
        try {
            Optional<Role> role = roleRepository.findById(user.getRoleId());
            return role.isPresent() && "CASHIER".equals(role.get().getName());
        } catch (Exception e) {
            log.debug("Failed to check if user is cashier: {}", e.getMessage());
            return false;
        }
    }
    
    /**
     * Send WebSocket notification for cashiers using reflection or RabbitMQ
     * Supports both cancellation and refund request decisions
     */
    private void sendCashierWebSocketNotification(String methodName, Transaction transaction, User cashier, 
                                                  boolean isApproved, String comments, Locale locale) {
        if (!isCashier(cashier)) {
            log.debug("User {} is not a cashier, skipping WebSocket notification", cashier.getId());
            return;
        }

        // Try to use NotificationService directly (if available in same application context)
        Object wsNotifier = resolveNotificationService();
        if (wsNotifier != null) {
            try {
                log.info("Sending {} request decision notification to cashier {} for transaction {} (approved: {})", 
                        methodName, cashier.getId(), transaction.getId(), isApproved);
                
                java.lang.reflect.Method method = wsNotifier.getClass().getMethod(methodName, 
                        Transaction.class, User.class, boolean.class, String.class, Locale.class);
                method.invoke(wsNotifier, transaction, cashier, isApproved, comments, locale);
                log.info("Successfully sent {} request decision notification to cashier {} for transaction {}", 
                        methodName, cashier.getId(), transaction.getId());
                return; // Successfully sent via NotificationService
            } catch (NoSuchMethodException e) {
                log.error("Method {} not found in NotificationService: {}", methodName, e.getMessage());
            } catch (Exception e) {
                log.error("Failed to send {} notification via reflection: {}", methodName, e.getMessage(), e);
            }
        }

        // Fallback: Publish to RabbitMQ for restaurant-management to consume and send WebSocket notification
        try {
            // Determine request type based on method name
            if ("notifyRefundRequestDecision".equals(methodName)) {
                publishRefundNotificationToRabbitMQ(transaction, cashier, isApproved, comments, locale);
                log.info("Published refund request decision notification to RabbitMQ for cashier {} and transaction {}", 
                        cashier.getId(), transaction.getId());
            } else {
                // Default to cancellation (for notifyCancellationRequestDecisionForCashier)
                publishCancellationNotificationToRabbitMQ(transaction, cashier, isApproved, comments, locale);
                log.info("Published cancellation request decision notification to RabbitMQ for cashier {} and transaction {}", 
                        cashier.getId(), transaction.getId());
            }
        } catch (Exception e) {
            log.error("Failed to publish notification to RabbitMQ: {}", e.getMessage(), e);
        }
    }
    
    /**
     * Publish cancellation notification to RabbitMQ for restaurant-management to consume and send WebSocket notification
     */
    private void publishCancellationNotificationToRabbitMQ(Transaction transaction, User cashier, boolean isApproved, String comments, Locale locale) {
        try {
            org.springframework.amqp.rabbit.core.RabbitTemplate rabbitTemplate = 
                    applicationContext.getBean(org.springframework.amqp.rabbit.core.RabbitTemplate.class);
            
            if (rabbitTemplate == null) {
                log.warn("RabbitTemplate not available, cannot publish cancellation notification");
                return;
            }

            String notificationType = isApproved ? "CANCELLATION_APPROVED" : "CANCELLATION_REJECTED";
            
            Map<String, Object> message = new HashMap<>();
            message.put("requestType", "CANCELLATION_REQUEST");
            message.put("type", "cancellation_request_decision");
            message.put("notificationType", notificationType);
            message.put("transactionId", transaction.getId().toString());
            message.put("transactionNumber", transaction.getTransactionNumber() != null ? transaction.getTransactionNumber() : "");
            message.put("cashierId", cashier.getId().toString());
            message.put("isApproved", isApproved);
            message.put("approved", isApproved);
            message.put("comments", comments != null ? comments : "");
            message.put("locale", locale != null ? locale.toString() : "en");
            message.put("timestamp", java.time.LocalDateTime.now(java.time.ZoneOffset.UTC)
                    .format(java.time.format.DateTimeFormatter.ISO_LOCAL_DATE_TIME));
            
            // Add transaction data
            Map<String, Object> transactionData = new HashMap<>();
            transactionData.put("transactionId", transaction.getId().toString());
            transactionData.put("transactionNumber", transaction.getTransactionNumber() != null ? transaction.getTransactionNumber() : "");
            if (transaction.getOrder() != null) {
                transactionData.put("orderId", transaction.getOrder().getId().toString());
                transactionData.put("orderNumber", transaction.getOrder().getOrderNumber());
            }
            if (transaction.getRestaurant() != null) {
                transactionData.put("restaurantId", transaction.getRestaurant().getId().toString());
            }
            message.put("transactionData", transactionData);
            
            // Publish to the same exchange that restaurant-management uses
            rabbitTemplate.convertAndSend(
                    "websocket.topic.exchange",
                    "request.decision.cancellation",  // Use unified routing key
                    message
            );
            
            log.info("Published cancellation request decision notification to RabbitMQ - Transaction: {}, Cashier: {}, Approved: {}", 
                    transaction.getId(), cashier.getId(), isApproved);
        } catch (Exception e) {
            log.error("Failed to publish cancellation notification to RabbitMQ: {}", e.getMessage(), e);
        }
    }
    
    /**
     * Publish refund notification to RabbitMQ for restaurant-management to consume and send WebSocket notification
     */
    private void publishRefundNotificationToRabbitMQ(Transaction transaction, User cashier, boolean isApproved, String comments, Locale locale) {
        try {
            org.springframework.amqp.rabbit.core.RabbitTemplate rabbitTemplate = 
                    applicationContext.getBean(org.springframework.amqp.rabbit.core.RabbitTemplate.class);
            
            if (rabbitTemplate == null) {
                log.warn("RabbitTemplate not available, cannot publish refund notification");
                return;
            }

            String notificationType = isApproved ? "REFUND_REQUEST_APPROVED" : "REFUND_REQUEST_DECLINED";
            
            Map<String, Object> message = new HashMap<>();
            message.put("requestType", "REFUND_REQUEST");
            message.put("type", "refund_request_decision");
            message.put("notificationType", notificationType);
            message.put("transactionId", transaction.getId().toString());
            message.put("transactionNumber", transaction.getTransactionNumber() != null ? transaction.getTransactionNumber() : "");
            message.put("cashierId", cashier.getId().toString());
            message.put("isApproved", isApproved);
            message.put("approved", isApproved);
            message.put("comments", comments != null ? comments : "");
            message.put("locale", locale != null ? locale.toString() : "en");
            message.put("timestamp", java.time.LocalDateTime.now(java.time.ZoneOffset.UTC)
                    .format(java.time.format.DateTimeFormatter.ISO_LOCAL_DATE_TIME));
            
            // Add transaction data
            Map<String, Object> transactionData = new HashMap<>();
            transactionData.put("transactionId", transaction.getId().toString());
            transactionData.put("transactionNumber", transaction.getTransactionNumber() != null ? transaction.getTransactionNumber() : "");
            if (transaction.getOrder() != null) {
                transactionData.put("orderId", transaction.getOrder().getId().toString());
                transactionData.put("orderNumber", transaction.getOrder().getOrderNumber());
            }
            if (transaction.getRestaurant() != null) {
                transactionData.put("restaurantId", transaction.getRestaurant().getId().toString());
            }
            message.put("transactionData", transactionData);
            
            // Publish to the same exchange that restaurant-management uses
            rabbitTemplate.convertAndSend(
                    "websocket.topic.exchange",
                    "request.decision.refund",  // Use unified routing key
                    message
            );
            
            log.info("Published refund request decision notification to RabbitMQ - Transaction: {}, Cashier: {}, Approved: {}", 
                    transaction.getId(), cashier.getId(), isApproved);
        } catch (Exception e) {
            log.error("Failed to publish refund notification to RabbitMQ: {}", e.getMessage(), e);
        }
    }
    
    /**
     * Send transaction cancellation notification for waiters using reflection (same pattern as item/combo cancellation)
     * This follows the same approach as notifyCancellationDecision for ordered items/combos
     */
    private void sendWaiterTransactionCancellationNotification(Transaction transaction, User waiter, Locale locale) {
        if (waiter == null || notificationService == null) {
            if (notificationService == null) {
                log.debug("NotificationService not available, cannot send waiter notification");
            }
            return;
        }
        try {
            java.lang.reflect.Method method = notificationService.getClass().getMethod(
                    "notifyTransactionCancelledForWaiter", 
                    Transaction.class, User.class, Locale.class);
            method.invoke(notificationService, transaction, waiter, locale);
        } catch (Exception e) {
            log.debug("Failed to send WebSocket notification to waiter via reflection: {}", e.getMessage());
        }
    }
    
    /**
     * Send item cancellation notification for cashiers/waiters using reflection or RabbitMQ
     */
    private void sendItemCancellationNotification(OrderedItem orderedItem, User requester, 
                                                 boolean isApproved, String comments, Locale locale) {
        if (requester == null) {
            log.debug("Requester is null, skipping item cancellation notification");
            return;
        }

        // Try to use NotificationService directly (if available in same application context)
        Object wsNotifier = resolveNotificationService();
        if (wsNotifier != null) {
            try {
                log.info("Sending item cancellation request decision notification to requester {} for orderedItem {} (approved: {})", 
                        requester.getId(), orderedItem.getId(), isApproved);
                
                java.lang.reflect.Method method = wsNotifier.getClass().getMethod("notifyCancellationDecision",
                        OrderedItem.class, User.class, boolean.class, String.class, Locale.class);
                method.invoke(wsNotifier, orderedItem, requester, isApproved, comments, locale);
                log.info("Successfully sent item cancellation request decision notification to requester {} for orderedItem {}", 
                        requester.getId(), orderedItem.getId());
                return; // Successfully sent via NotificationService
            } catch (NoSuchMethodException e) {
                log.error("Method notifyCancellationDecision not found in NotificationService: {}", e.getMessage());
            } catch (Exception e) {
                log.error("Failed to send item cancellation notification via reflection: {}", e.getMessage(), e);
            }
        }

        // Fallback: Publish to RabbitMQ for restaurant-management to consume and send WebSocket notification
        try {
            publishItemCancellationNotificationToRabbitMQ(orderedItem, requester, isApproved, comments, locale);
            log.info("Published item cancellation request decision notification to RabbitMQ for requester {} and orderedItem {}", 
                    requester.getId(), orderedItem.getId());
        } catch (Exception e) {
            log.error("Failed to publish item cancellation notification to RabbitMQ: {}", e.getMessage(), e);
        }
    }
    
    /**
     * Publish item cancellation notification to RabbitMQ for restaurant-management to consume and send WebSocket notification
     */
    private void publishItemCancellationNotificationToRabbitMQ(OrderedItem orderedItem, User requester, 
                                                               boolean isApproved, String comments, Locale locale) {
        try {
            org.springframework.amqp.rabbit.core.RabbitTemplate rabbitTemplate = 
                    applicationContext.getBean(org.springframework.amqp.rabbit.core.RabbitTemplate.class);
            
            if (rabbitTemplate == null) {
                log.warn("RabbitTemplate not available, cannot publish item cancellation notification");
                return;
            }

            String notificationType = isApproved ? "CANCELLATION_APPROVED" : "CANCELLATION_REJECTED";
            
            Map<String, Object> message = new HashMap<>();
            message.put("requestType", "ITEM_CANCELLATION_REQUEST");
            message.put("type", "item_cancellation_request_decision");
            message.put("notificationType", notificationType);
            message.put("orderedItemId", orderedItem.getId().toString());
            message.put("requesterId", requester.getId().toString());
            message.put("isApproved", isApproved);
            message.put("approved", isApproved);
            message.put("comments", comments != null ? comments : "");
            message.put("locale", locale != null ? locale.toString() : "en");
            message.put("timestamp", java.time.LocalDateTime.now(java.time.ZoneOffset.UTC)
                    .format(java.time.format.DateTimeFormatter.ISO_LOCAL_DATE_TIME));
            
            // Add ordered item data
            Map<String, Object> itemData = new HashMap<>();
            itemData.put("orderedItemId", orderedItem.getId().toString());
            if (orderedItem.getOrder() != null) {
                itemData.put("orderId", orderedItem.getOrder().getId().toString());
                itemData.put("orderNumber", orderedItem.getOrder().getOrderNumber());
            }
            if (orderedItem.getItem() != null) {
                itemData.put("itemId", orderedItem.getItem().getId().toString());
            }
            message.put("itemData", itemData);
            
            // Publish to the same exchange that restaurant-management uses
            rabbitTemplate.convertAndSend(
                    "websocket.topic.exchange",
                    "request.decision.item_cancellation",  // Use unified routing key
                    message
            );
            
            log.info("Published item cancellation request decision notification to RabbitMQ - OrderedItem: {}, Requester: {}, Approved: {}", 
                    orderedItem.getId(), requester.getId(), isApproved);
        } catch (Exception e) {
            log.error("Failed to publish item cancellation notification to RabbitMQ: {}", e.getMessage(), e);
        }
    }

    /**
     * Send combo cancellation notification for cashiers/waiters using reflection or RabbitMQ.
     * Mirrors item cancellation decision flow.
     */
    private void sendComboCancellationNotification(OrderedCombo orderedCombo, User requester,
                                                   boolean isApproved, String comments, Locale locale) {
        if (requester == null) {
            log.debug("Requester is null, skipping combo cancellation notification");
            return;
        }

        // Try to use NotificationService directly (if available in same application context)
        Object wsNotifier = resolveNotificationService();
        if (wsNotifier != null) {
            try {
                log.info("Sending combo cancellation request decision notification to requester {} for orderedCombo {} (approved: {})",
                        requester.getId(), orderedCombo.getId(), isApproved);

                java.lang.reflect.Method method = wsNotifier.getClass().getMethod("notifyComboCancellationDecision",
                        OrderedCombo.class, User.class, boolean.class, String.class, Locale.class);
                method.invoke(wsNotifier, orderedCombo, requester, isApproved, comments, locale);
                log.info("Successfully sent combo cancellation request decision notification to requester {} for orderedCombo {}",
                        requester.getId(), orderedCombo.getId());
                return; // Successfully sent via NotificationService
            } catch (NoSuchMethodException e) {
                log.error("Method notifyComboCancellationDecision not found in NotificationService: {}", e.getMessage());
            } catch (Exception e) {
                log.error("Failed to send combo cancellation notification via reflection: {}", e.getMessage(), e);
            }
        }

        // Fallback: Publish to RabbitMQ for restaurant-management to consume and send WebSocket notification
        try {
            publishComboCancellationNotificationToRabbitMQ(orderedCombo, requester, isApproved, comments, locale);
            log.info("Published combo cancellation request decision notification to RabbitMQ for requester {} and orderedCombo {}",
                    requester.getId(), orderedCombo.getId());
        } catch (Exception e) {
            log.error("Failed to publish combo cancellation notification to RabbitMQ: {}", e.getMessage(), e);
        }
    }

    /**
     * Publish combo cancellation notification to RabbitMQ for restaurant-management to consume and send WebSocket notification.
     */
    private void publishComboCancellationNotificationToRabbitMQ(OrderedCombo orderedCombo, User requester,
                                                                boolean isApproved, String comments, Locale locale) {
        try {
            org.springframework.amqp.rabbit.core.RabbitTemplate rabbitTemplate =
                    applicationContext.getBean(org.springframework.amqp.rabbit.core.RabbitTemplate.class);

            if (rabbitTemplate == null) {
                log.warn("RabbitTemplate not available, cannot publish combo cancellation notification");
                return;
            }

            String notificationType = isApproved ? "CANCELLATION_APPROVED" : "CANCELLATION_REJECTED";

            Map<String, Object> message = new HashMap<>();
            message.put("requestType", "COMBO_CANCELLATION_REQUEST");
            message.put("type", "combo_cancellation_request_decision");
            message.put("notificationType", notificationType);
            message.put("orderedComboId", orderedCombo.getId().toString());
            message.put("requesterId", requester.getId().toString());
            message.put("isApproved", isApproved);
            message.put("approved", isApproved);
            message.put("comments", comments != null ? comments : "");
            message.put("locale", locale != null ? locale.toString() : "en");
            message.put("timestamp", java.time.LocalDateTime.now(java.time.ZoneOffset.UTC)
                    .format(java.time.format.DateTimeFormatter.ISO_LOCAL_DATE_TIME));

            Map<String, Object> comboData = new HashMap<>();
            comboData.put("orderedComboId", orderedCombo.getId().toString());
            if (orderedCombo.getOrder() != null) {
                comboData.put("orderId", orderedCombo.getOrder().getId().toString());
                comboData.put("orderNumber", orderedCombo.getOrder().getOrderNumber());
            }
            if (orderedCombo.getCombo() != null) {
                comboData.put("comboId", orderedCombo.getCombo().getComboId().toString());
            }
            message.put("comboData", comboData);

            rabbitTemplate.convertAndSend(
                    "websocket.topic.exchange",
                    "request.decision.combo_cancellation",
                    message
            );

            log.info("Published combo cancellation request decision notification to RabbitMQ - OrderedCombo: {}, Requester: {}, Approved: {}",
                    orderedCombo.getId(), requester.getId(), isApproved);
        } catch (Exception e) {
            log.error("Failed to publish combo cancellation notification to RabbitMQ: {}", e.getMessage(), e);
        }
    }
    
    /**
     * Publish order cancellation notification to RabbitMQ for restaurant-management to consume and send WebSocket notification (includes KDS notification)
     */
    private void publishOrderCancellationNotificationToRabbitMQ(Order order, User requester, 
                                                               boolean isApproved, String comments, Locale locale) {
        try {
            org.springframework.amqp.rabbit.core.RabbitTemplate rabbitTemplate = 
                    applicationContext.getBean(org.springframework.amqp.rabbit.core.RabbitTemplate.class);
            
            if (rabbitTemplate == null) {
                log.warn("RabbitTemplate not available, cannot publish order cancellation notification");
                return;
            }

            String notificationType = isApproved ? "CANCELLATION_APPROVED" : "CANCELLATION_REJECTED";
            
            Map<String, Object> message = new HashMap<>();
            message.put("requestType", "ORDER_CANCELLATION_REQUEST");
            message.put("type", "order_cancellation_request_decision");
            message.put("notificationType", notificationType);
            message.put("orderId", order.getId().toString());
            message.put("orderNumber", order.getOrderNumber() != null ? order.getOrderNumber() : "");
            message.put("requesterId", requester.getId().toString());
            message.put("isApproved", isApproved);
            message.put("approved", isApproved);
            message.put("comments", comments != null ? comments : "");
            message.put("locale", locale != null ? locale.toString() : "en");
            message.put("timestamp", java.time.LocalDateTime.now(java.time.ZoneOffset.UTC)
                    .format(java.time.format.DateTimeFormatter.ISO_LOCAL_DATE_TIME));
            
            // Add order data
            Map<String, Object> orderData = new HashMap<>();
            orderData.put("orderId", order.getId().toString());
            orderData.put("orderNumber", order.getOrderNumber() != null ? order.getOrderNumber() : "");
            if (order.getRestaurant() != null) {
                orderData.put("restaurantId", order.getRestaurant().getId().toString());
            }
            if (order.getRestaurantTable() != null) {
                orderData.put("tableId", order.getRestaurantTable().getId().toString());
                if (order.getRestaurantTable().getTableOrder() != null) {
                    orderData.put("tableNumber", order.getRestaurantTable().getTableOrder().toString());
                }
            }
            if (order.getWaiter() != null) {
                orderData.put("waiterId", order.getWaiter().getId().toString());
            }
            message.put("orderData", orderData);
            
            // Publish to the same exchange that restaurant-management uses
            rabbitTemplate.convertAndSend(
                    "websocket.topic.exchange",
                    "request.decision.order_cancellation",  // Use unified routing key
                    message
            );
            
            log.info("Published order cancellation request decision notification to RabbitMQ - Order: {}, Requester: {}, Approved: {}", 
                    order.getId(), requester.getId(), isApproved);
        } catch (Exception e) {
            log.error("Failed to publish order cancellation notification to RabbitMQ: {}", e.getMessage(), e);
        }
    }
    
    /**
     * Send discount request decision notification for cashiers using reflection or RabbitMQ
     */
    private void sendCashierDiscountNotification(UUID orderId, String orderNumber, User cashier, boolean isApproved, String comments, Locale locale) {
        if (!isCashier(cashier)) {
            log.debug("User {} is not a cashier, skipping discount notification", cashier.getId());
            return;
        }
        if (orderId == null) {
            log.warn("Order id is null (orderNumber {}), cannot notify cashier", orderNumber);
            return;
        }

        // Try to use NotificationService directly (if available in same application context)
        Object wsNotifier = resolveNotificationService();
        if (wsNotifier != null) {
            try {
                Optional<Order> orderOpt = orderRepository.findById(orderId);
                if (orderOpt.isEmpty()) {
                    log.warn("Order not found for id {} (orderNumber {}), cannot notify cashier", orderId, orderNumber);
                    return;
                }

                Order order = orderOpt.get();
                log.info("Sending additional discount request decision notification to cashier {} for order {} (approved: {})", 
                        cashier.getId(), orderNumber, isApproved);
                
                java.lang.reflect.Method method = wsNotifier.getClass().getMethod("notifyDiscountRequestDecision",
                        Order.class, User.class, boolean.class, String.class, Locale.class);
                method.invoke(wsNotifier, order, cashier, isApproved, comments, locale);
                log.info("Successfully sent additional discount request decision notification to cashier {} for order {}", 
                        cashier.getId(), orderNumber);
                return; // Successfully sent via NotificationService
            } catch (NoSuchMethodException e) {
                log.error("Method notifyDiscountRequestDecision not found in NotificationService: {}", e.getMessage());
            } catch (Exception e) {
                log.error("Failed to send discount notification via reflection: {}", e.getMessage(), e);
            }
        }

        // Fallback: Publish to RabbitMQ for restaurant-management to consume and send WebSocket notification
        try {
            Optional<Order> orderOpt = orderRepository.findById(orderId);
            if (orderOpt.isEmpty()) {
                log.warn("Order not found for id {} (orderNumber {}), cannot notify cashier", orderId, orderNumber);
                return;
            }

            Order order = orderOpt.get();
            publishDiscountNotificationToRabbitMQ(order, cashier, isApproved, comments, locale);
            log.info("Published additional discount request decision notification to RabbitMQ for cashier {} and order {}", 
                    cashier.getId(), orderNumber);
        } catch (Exception e) {
            log.error("Failed to publish discount notification to RabbitMQ: {}", e.getMessage(), e);
        }
    }
    
    /**
     * Publish discount notification to RabbitMQ for restaurant-management to consume and send WebSocket notification
     */
    private void publishDiscountNotificationToRabbitMQ(Order order, User cashier, boolean isApproved, String comments, Locale locale) {
        try {
            org.springframework.amqp.rabbit.core.RabbitTemplate rabbitTemplate = 
                    applicationContext.getBean(org.springframework.amqp.rabbit.core.RabbitTemplate.class);
            
            if (rabbitTemplate == null) {
                log.warn("RabbitTemplate not available, cannot publish discount notification");
                return;
            }

            String notificationType = isApproved ? "ADDITIONAL_DISCOUNT_REQUEST_APPROVED" : "ADDITIONAL_DISCOUNT_REQUEST_DECLINED";
            
            Map<String, Object> message = new HashMap<>();
            message.put("type", "discount_request_decision");
            message.put("notificationType", notificationType);
            message.put("orderId", order.getId().toString());
            message.put("orderNumber", order.getOrderNumber());
            message.put("cashierId", cashier.getId().toString());
            message.put("isApproved", isApproved);
            message.put("approved", isApproved);
            message.put("comments", comments != null ? comments : "");
            message.put("locale", locale != null ? locale.toString() : "en");
            message.put("timestamp", java.time.LocalDateTime.now(java.time.ZoneOffset.UTC)
                    .format(java.time.format.DateTimeFormatter.ISO_LOCAL_DATE_TIME));
            
            // Add order data
            Map<String, Object> orderData = new HashMap<>();
            orderData.put("orderId", order.getId().toString());
            orderData.put("orderNumber", order.getOrderNumber());
            if (order.getRestaurant() != null) {
                orderData.put("restaurantId", order.getRestaurant().getId().toString());
            }
            message.put("orderData", orderData);
            
            // Publish to the same exchange that restaurant-management uses
            // Use the same exchange name as defined in restaurant-management
            rabbitTemplate.convertAndSend(
                    "websocket.topic.exchange",
                    "discount.request.decision",
                    message
            );
            
            log.info("Published discount request decision notification to RabbitMQ - Order: {}, Cashier: {}, Approved: {}", 
                    order.getOrderNumber(), cashier.getId(), isApproved);
        } catch (Exception e) {
            log.error("Failed to publish discount notification to RabbitMQ: {}", e.getMessage(), e);
        }
    }

    /**
     * Publish profile update notification to RabbitMQ for restaurant-management to consume and send WebSocket notification
     */
    private void publishProfileUpdateNotificationToRabbitMQ(User user, User requester, User manager,
                                                           boolean isApproved, String comments, Locale locale) {
        try {
            org.springframework.amqp.rabbit.core.RabbitTemplate rabbitTemplate = 
                    applicationContext.getBean(org.springframework.amqp.rabbit.core.RabbitTemplate.class);
            
            if (rabbitTemplate == null) {
                log.warn("RabbitTemplate not available, cannot publish profile update notification");
                return;
            }

            String notificationType = isApproved ? "PROFILE_UPDATE_REQUEST_APPROVED" : "PROFILE_UPDATE_REQUEST_DECLINED";
            
            Map<String, Object> message = new HashMap<>();
            message.put("requestType", "PROFILE_UPDATE_REQUEST");
            message.put("type", "profile_update_request_decision");
            message.put("notificationType", notificationType);
            message.put("userId", user.getId().toString());
            message.put("requesterId", requester.getId().toString());
            message.put("isApproved", isApproved);
            message.put("approved", isApproved);
            message.put("comments", comments != null ? comments : "");
            message.put("locale", locale != null ? locale.toString() : "en");
            message.put("timestamp", java.time.LocalDateTime.now(java.time.ZoneOffset.UTC)
                    .format(java.time.format.DateTimeFormatter.ISO_LOCAL_DATE_TIME));
            
            // Add manager ID if available (for createdBy field in notification)
            if (manager != null) {
                message.put("managerId", manager.getId().toString());
            }
            
            // Add user data
            Map<String, Object> userData = new HashMap<>();
            userData.put("userId", user.getId().toString());
            userData.put("userCode", user.getUserCode() != null ? user.getUserCode() : "");
            userData.put("firstName", user.getFirstName() != null ? user.getFirstName() : "");
            userData.put("lastName", user.getLastName() != null ? user.getLastName() : "");
            userData.put("email", user.getEmail() != null ? user.getEmail() : "");
            if (user.getRestaurantId() != null) {
                userData.put("restaurantId", user.getRestaurantId().toString());
            }
            if (user.getRoleId() != null) {
                userData.put("roleId", user.getRoleId().toString());
            }
            message.put("userData", userData);
            
            // Publish to the same exchange that restaurant-management uses
            rabbitTemplate.convertAndSend(
                    "websocket.topic.exchange",
                    "request.decision.profile_update",  // Use unified routing key
                    message
            );
            
            log.info("Published profile update request decision notification to RabbitMQ - User: {}, Requester: {}, Approved: {}", 
                    user.getId(), requester.getId(), isApproved);
        } catch (Exception e) {
            log.error("Failed to publish profile update notification to RabbitMQ: {}", e.getMessage(), e);
        }
    }
    
    /**
     * Publish profile updated directly notification to RabbitMQ for restaurant-management to consume
     * This is used as a fallback when NotificationService is not available
     */
    private void publishProfileUpdatedDirectlyNotificationToRabbitMQ(User employee, User updater, Locale locale) {
        try {
            org.springframework.amqp.rabbit.core.RabbitTemplate rabbitTemplate = 
                    applicationContext.getBean(org.springframework.amqp.rabbit.core.RabbitTemplate.class);
            
            if (rabbitTemplate == null) {
                log.warn("RabbitTemplate not available, cannot publish profile updated directly notification");
                return;
            }

            Map<String, Object> message = new HashMap<>();
            message.put("type", "profile_updated_directly");
            message.put("notificationType", "PROFILE_UPDATED_DIRECTLY");
            message.put("userId", employee.getId().toString());
            message.put("locale", locale != null ? locale.toString() : "en");
            message.put("timestamp", java.time.LocalDateTime.now(java.time.ZoneOffset.UTC)
                    .format(java.time.format.DateTimeFormatter.ISO_LOCAL_DATE_TIME));
            
            // Add updater information
            if (updater != null) {
                message.put("updaterId", updater.getId().toString());
                String updaterName = (updater.getFirstName() != null ? updater.getFirstName() : "") + 
                        " " + (updater.getLastName() != null ? updater.getLastName() : "");
                message.put("updaterName", updaterName.trim());
            }
            
            // Add employee user data
            Map<String, Object> userData = new HashMap<>();
            userData.put("userId", employee.getId().toString());
            userData.put("userCode", employee.getUserCode() != null ? employee.getUserCode() : "");
            userData.put("firstName", employee.getFirstName() != null ? employee.getFirstName() : "");
            userData.put("lastName", employee.getLastName() != null ? employee.getLastName() : "");
            userData.put("email", employee.getEmail() != null ? employee.getEmail() : "");
            if (employee.getRestaurantId() != null) {
                userData.put("restaurantId", employee.getRestaurantId().toString());
            }
            if (employee.getRoleId() != null) {
                userData.put("roleId", employee.getRoleId().toString());
            }
            message.put("userData", userData);
            
            // Publish to the same exchange that restaurant-management uses
            rabbitTemplate.convertAndSend(
                    "websocket.topic.exchange",
                    "profile.updated.directly",  // Routing key for profile updated directly
                    message
            );
            
            log.info("Published profile updated directly notification to RabbitMQ - Employee: {}, Updater: {}", 
                    employee.getId(), updater != null ? updater.getId() : "unknown");
        } catch (Exception e) {
            log.error("Failed to publish profile updated directly notification to RabbitMQ: {}", e.getMessage(), e);
        }
    }

    /**
     * Notifies restaurant-management (after DB commit) to delete scheduled email reports this user created
     * for {@code oldRestaurantId} when they are reassigned or unassigned.
     */
    private void publishUserLeftRestaurantEmailSchedulesAfterCommit(UUID userId, UUID oldRestaurantId) {
        if (userId == null || oldRestaurantId == null) {
            return;
        }
        Map<String, Object> message = new HashMap<>();
        message.put("userId", userId.toString());
        message.put("oldRestaurantId", oldRestaurantId.toString());
        publishEmailScheduleCleanupAfterCommit(
                RabbitMQConfig.USER_LEFT_RESTAURANT_EMAIL_SCHEDULES_ROUTING_KEY,
                message,
                "user left restaurant email-schedule cleanup for user " + userId + " restaurant " + oldRestaurantId);
    }

    /**
     * Notifies restaurant-management (after DB commit) to delete all scheduled email reports created by this user.
     */
    private void publishUserDeletedEmailSchedulesAfterCommit(UUID userId) {
        if (userId == null) {
            return;
        }
        Map<String, Object> message = new HashMap<>();
        message.put("userId", userId.toString());
        publishEmailScheduleCleanupAfterCommit(
                RabbitMQConfig.USER_DELETED_EMAIL_SCHEDULES_ROUTING_KEY,
                message,
                "user deleted email-schedule cleanup for user " + userId);
    }

    private void publishEmailScheduleCleanupAfterCommit(String routingKey, Map<String, Object> message, String logLabel) {
        Runnable publish = () -> {
            try {
                rabbitTemplate.convertAndSend(RabbitMQConfig.NOTIFICATION_TOPIC_EXCHANGE, routingKey, message);
                log.info("Published {}", logLabel);
            } catch (Exception e) {
                log.error("Failed to publish {}: {}", logLabel, e.getMessage(), e);
            }
        };
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    publish.run();
                }
            });
        } else {
            publish.run();
        }
    }
    
    /**
     * Resolve NotificationService bean lazily to avoid hard dependency on restaurant-management module.
     */
    private Object resolveNotificationService() {
        if (notificationService != null) {
            return notificationService;
        }
        try {
            notificationService = applicationContext.getBean("notificationServiceImpl");
        } catch (Exception e) {
            log.debug("NotificationService bean not available: {}", e.getMessage());
        }
        return notificationService;
    }

    /**
     * Cached reference to RestaurantAlertEvaluationService (lazily resolved from restaurant-management module).
     */
    private Object restaurantAlertEvaluationService;

    /**
     * Resolve RestaurantAlertEvaluationService bean lazily to avoid hard dependency on restaurant-management module.
     */
    private Object resolveAlertEvaluationService() {
        if (restaurantAlertEvaluationService != null) {
            return restaurantAlertEvaluationService;
        }
        try {
            restaurantAlertEvaluationService = applicationContext.getBean("restaurantAlertEvaluationService");
        } catch (Exception e) {
            log.debug("RestaurantAlertEvaluationService bean not available: {}", e.getMessage());
        }
        return restaurantAlertEvaluationService;
    }

    /**
     * Trigger real-time HQ alert evaluation after the current transaction commits.
     * Uses TransactionSynchronizationManager to register an afterCommit callback so the
     * alert evaluation (which runs in REQUIRES_NEW) can see the committed data.
     *
     * @param restaurant The restaurant to evaluate alerts for
     * @param locale     The locale for alert messages
     * @param context    A descriptive context string for logging (e.g. "refund approval", "transaction cancellation approval")
     */
    private void triggerAlertEvaluationAfterCommit(Restaurant restaurant, Locale locale, String context) {
        if (restaurant == null) {
            log.warn("Cannot trigger alert evaluation: restaurant is null (context: {})", context);
            return;
        }

        Object alertService = resolveAlertEvaluationService();
        if (alertService == null) {
            log.warn("RestaurantAlertEvaluationService not available - skipping alert evaluation after {}", context);
            return;
        }

        final Restaurant finalRestaurant = restaurant;
        final Locale finalLocale = locale;

        if (org.springframework.transaction.support.TransactionSynchronizationManager.isActualTransactionActive()) {
            org.springframework.transaction.support.TransactionSynchronizationManager.registerSynchronization(
                    new org.springframework.transaction.support.TransactionSynchronization() {
                        @Override
                        public void afterCommit() {
                            try {
                                log.info("🔔 Triggering alert evaluation for restaurant: {} after {} commit",
                                        finalRestaurant.getRestaurantCode(), context);
                                java.lang.reflect.Method method = alertService.getClass().getMethod(
                                        "evaluateRestaurantAlertsRealtime",
                                        Restaurant.class,
                                        Locale.class
                                );
                                method.invoke(alertService, finalRestaurant, finalLocale);
                                log.info("✅ Alert evaluation completed for restaurant: {} after {} commit",
                                        finalRestaurant.getRestaurantCode(), context);
                            } catch (Exception e) {
                                log.error("❌ Failed to evaluate real-time alerts after {} commit: {}",
                                        context, e.getMessage(), e);
                            }
                        }
                    }
            );
            log.info("📋 Registered alert evaluation to run after {} commit for restaurant: {}",
                    context, restaurant.getRestaurantCode());
        } else {
            // No active transaction - safe to run immediately
            try {
                log.info("🔔 Triggering alert evaluation for restaurant: {} after {} (no active transaction)",
                        restaurant.getRestaurantCode(), context);
                java.lang.reflect.Method method = alertService.getClass().getMethod(
                        "evaluateRestaurantAlertsRealtime",
                        Restaurant.class,
                        Locale.class
                );
                method.invoke(alertService, finalRestaurant, finalLocale);
                log.info("✅ Alert evaluation completed for restaurant: {} after {} (no active transaction)",
                        restaurant.getRestaurantCode(), context);
            } catch (Exception e) {
                log.error("❌ Failed to evaluate real-time alerts after {} (no active transaction): {}",
                        context, e.getMessage(), e);
            }
        }
    }
    
    /**
     * Helper method to get the message key prefix based on request category.
     */
    private String getRequestCategoryMessageKeyPrefix(String requestCategory) {
        if (requestCategory == null) {
            return "request";
        }
        switch (requestCategory) {
            case "ITEM_CANCELLATION":
                return "item.cancellation.request";
            case "COMBO_CANCELLATION":
                return "combo.cancellation.request";
            case "ORDER_CANCELLATION":
                return "order.cancellation.request";
            case "TRANSACTION_CANCELLATION":
                return "transaction.cancellation.request";
            default:
                return "request";
        }
    }
    
    /**
     * Send WebSocket notification to restaurant item-status topic for cancellation request approval/decline decisions.
     * Uses SimpMessagingTemplate directly (similar to OrderServiceImpl) for real-time WebSocket notifications.
     */
    private void sendItemStatusWebSocketRequestDecisionNotification(UUID restaurantId,
                                                                    RequestStatus action,
                                                                    String requestCategory,
                                                                    UUID referenceId,
                                                                    Locale locale) {
        if (restaurantId == null || action == null) {
            log.debug("Skipping WebSocket notification for item-status: restaurantId={}, action={}", restaurantId, action);
            return;
        }
        
        if (messagingTemplate == null) {
            log.debug("SimpMessagingTemplate is not available, will only publish to RabbitMQ. restaurantId={}, category={}, decision={}", 
                    restaurantId, requestCategory, action.name());
        }
        
        try {
            String topic = "/topic/restaurant/" + restaurantId + "/item-status";
            
            Map<String, Object> data = new HashMap<>();
            data.put("restaurantId", restaurantId.toString());
            data.put("requestCategory", requestCategory);
            data.put("referenceId", referenceId != null ? referenceId.toString() : null);
            data.put("decision", action.name());
            data.put("notificationType", "REQUEST_DECISION");
            data.put("timestamp", LocalDateTime.now(ZoneOffset.UTC).format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));

            String messageKeyPrefix = getRequestCategoryMessageKeyPrefix(requestCategory);
            String messageKey = action == RequestStatus.APPROVED
                    ? messageKeyPrefix + ".approved"
                    : messageKeyPrefix + ".declined";
            String message = messageUtil.getMessage(messageKey, locale);

            
            // Send WebSocket notification directly using SimpMessagingTemplate (like OrderServiceImpl)
            if (messagingTemplate != null) {
                StatusEventMessage eventMessage = StatusEventMessage.builder()
                        .message(message)
                        .notificationType("REQUEST_DECISION")
                        .itemId(referenceId != null ? referenceId.toString() : null)
                        .status(action.name())
                        .data(data)
                        .build();
                messagingTemplate.convertAndSend(topic, eventMessage);
                log.info("Sent WebSocket notification to item-status topic: category={}, decision={}, restaurantId={}", 
                        requestCategory, action.name(), restaurantId);
            }
            
            // Also publish to RabbitMQ for integration service to log (similar to OrderServiceImpl)
            if (rabbitTemplate != null) {
                try {
                    Map<String, Object> wsMessage = new HashMap<>();
                    wsMessage.put("topic", topic);
                    wsMessage.put("message", message);
                    wsMessage.put("notificationType", "REQUEST_DECISION");
                    wsMessage.put("status", action.name());
                    wsMessage.put("data", data);
                    wsMessage.put("timestamp", LocalDateTime.now(ZoneOffset.UTC).format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
                    wsMessage.put("type", "websocket_notification");
                    
                    rabbitTemplate.convertAndSend(NOTIFICATION_TOPIC_EXCHANGE, NOTIFICATION_ROUTING_KEY, wsMessage);
                    log.info("Published request decision WebSocket message to RabbitMQ for item-status: topic={}, category={}, decision={}",
                            topic, requestCategory, action.name());
                } catch (AmqpException e) {
                    // RabbitMQ connection errors should not fail the main transaction
                    log.warn("Failed to publish to RabbitMQ for item-status notification (non-critical): {}", e.getMessage());
                }
            }
        } catch (Exception e) {
            log.error("Failed to send WebSocket notification for item-status request decision: category={}, decision={}, error={}", 
                    requestCategory, action.name(), e.getMessage(), e);
        }
    }

    /**
     * Broadcast profile update request decision to a restaurant-scoped user-update topic.
     * Similar to item-status request-decision broadcasts, this enables waiter/cashier dashboards
     * to refresh employee lists without relying on requester-specific notification channels.
     */
    private void sendUserUpdateWebSocketProfileUpdateDecisionNotification(UUID restaurantId,
                                                                          RequestStatus action,
                                                                          UUID userId,
                                                                          Locale locale) {
        if (restaurantId == null || action == null || userId == null) {
            log.debug("Skipping WebSocket notification for user-update: restaurantId={}, action={}, userId={}",
                    restaurantId, action, userId);
            return;
        }

        if (messagingTemplate == null) {
            log.debug("SimpMessagingTemplate is not available, will only publish to RabbitMQ. restaurantId={}, decision={}, userId={}",
                    restaurantId, action.name(), userId);
        }

        try {
            String topic = "/topic/restaurant/" + restaurantId + "/user-update";

            Map<String, Object> data = new HashMap<>();
            data.put("restaurantId", restaurantId.toString());
            data.put("userId", userId.toString());
            data.put("decision", action.name());
            data.put("notificationType", "REQUEST_DECISION");
            data.put("timestamp", LocalDateTime.now(ZoneOffset.UTC).format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));

            String messageKey = action == RequestStatus.APPROVED
                    ? "user.profile.update.request.approved"
                    : "user.profile.update.request.declined";
            String message = messageUtil.getMessage(messageKey, locale);

            if (messagingTemplate != null) {
                StatusEventMessage eventMessage = StatusEventMessage.builder()
                        .message(message)
                        .notificationType("REQUEST_DECISION")
                        .itemId(userId.toString())
                        .status(action.name())
                        .data(data)
                        .build();
                messagingTemplate.convertAndSend(topic, eventMessage);
                log.info("Sent WebSocket notification to user-update topic: decision={}, restaurantId={}, userId={}",
                        action.name(), restaurantId, userId);
            }

            // Also publish to RabbitMQ for integration service to log (non-critical)
            if (rabbitTemplate != null) {
                try {
                    Map<String, Object> wsMessage = new HashMap<>();
                    wsMessage.put("topic", topic);
                    wsMessage.put("message", message);
                    wsMessage.put("notificationType", "REQUEST_DECISION");
                    wsMessage.put("status", action.name());
                    wsMessage.put("data", data);
                    wsMessage.put("timestamp", LocalDateTime.now(ZoneOffset.UTC).format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
                    wsMessage.put("type", "websocket_notification");

                    rabbitTemplate.convertAndSend(NOTIFICATION_TOPIC_EXCHANGE, NOTIFICATION_ROUTING_KEY, wsMessage);
                    log.info("Published WebSocket message to RabbitMQ for user-update: topic={}, decision={}",
                            topic, action.name());
                } catch (AmqpException e) {
                    log.warn("Failed to publish to RabbitMQ for user-update notification (non-critical): {}", e.getMessage());
                }
            }
        } catch (Exception e) {
            log.error("Failed to send WebSocket notification for user-update profile update decision: decision={}, userId={}, error={}",
                    action.name(), userId, e.getMessage(), e);
        }
    }
    
    /**
     * Send WebSocket notification to restaurant order-status topic for cancellation request approval/decline decisions.
     * Uses SimpMessagingTemplate directly (similar to OrderServiceImpl) for real-time WebSocket notifications.
     */
    private void sendOrderStatusWebSocketRequestDecisionNotification(UUID restaurantId,
                                                                     RequestStatus action,
                                                                     String requestCategory,
                                                                     UUID referenceId,
                                                                     Locale locale) {
        if (restaurantId == null || action == null) {
            log.debug("Skipping WebSocket notification for order-status: restaurantId={}, action={}", restaurantId, action);
            return;
        }
        
        if (messagingTemplate == null) {
            log.debug("SimpMessagingTemplate is not available, will only publish to RabbitMQ. restaurantId={}, category={}, decision={}", 
                    restaurantId, requestCategory, action.name());
        }
        
        try {
            String topic = "/topic/restaurant/" + restaurantId + "/order-status";
            
            Map<String, Object> data = new HashMap<>();
            data.put("restaurantId", restaurantId.toString());
            data.put("requestCategory", requestCategory);
            data.put("referenceId", referenceId != null ? referenceId.toString() : null);
            data.put("decision", action.name());
            data.put("notificationType", "REQUEST_DECISION");
            data.put("timestamp", LocalDateTime.now(ZoneOffset.UTC).format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
            

            String messageKeyPrefix = getRequestCategoryMessageKeyPrefix(requestCategory);
            String messageKey = action == RequestStatus.APPROVED
                    ? messageKeyPrefix + ".approved"
                    : messageKeyPrefix + ".declined";
            String message = messageUtil.getMessage(messageKey, locale);


            
            // Send WebSocket notification directly using SimpMessagingTemplate (like OrderServiceImpl)
            if (messagingTemplate != null) {
                StatusEventMessage eventMessage = StatusEventMessage.builder()
                        .message(message)
                        .notificationType("REQUEST_DECISION")
                        .orderId(referenceId != null ? referenceId.toString() : null)
                        .status(action.name())
                        .data(data)
                        .build();
                messagingTemplate.convertAndSend(topic, eventMessage);
                log.info("Sent WebSocket notification to order-status topic: category={}, decision={}, restaurantId={}", 
                        requestCategory, action.name(), restaurantId);
            }
            
            // Also publish to RabbitMQ for integration service to log (similar to OrderServiceImpl)
            if (rabbitTemplate != null) {
                try {
                    Map<String, Object> wsMessage = new HashMap<>();
                    wsMessage.put("topic", topic);
                    wsMessage.put("message", message);
                    wsMessage.put("notificationType", "REQUEST_DECISION");
                    wsMessage.put("status", action.name());
                    wsMessage.put("data", data);
                    wsMessage.put("timestamp", LocalDateTime.now(ZoneOffset.UTC).format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
                    wsMessage.put("type", "websocket_notification");
                    
                    rabbitTemplate.convertAndSend(NOTIFICATION_TOPIC_EXCHANGE, NOTIFICATION_ROUTING_KEY, wsMessage);
                    log.info("Published request decision WebSocket message to RabbitMQ for order-status: topic={}, category={}, decision={}",
                            topic, requestCategory, action.name());
                } catch (AmqpException e) {
                    // RabbitMQ connection errors should not fail the main transaction
                    log.warn("Failed to publish to RabbitMQ for order-status notification (non-critical): {}", e.getMessage());
                }
            }
        } catch (Exception e) {
            log.error("Failed to send WebSocket notification for order-status request decision: category={}, decision={}, error={}", 
                    requestCategory, action.name(), e.getMessage(), e);
        }
    }
    
    /**
     * Send WebSocket notification to restaurant transaction-status topic for cancellation request approval/decline decisions.
     * Uses SimpMessagingTemplate directly (similar to OrderServiceImpl) for real-time WebSocket notifications.
     */
    private void sendTransactionStatusWebSocketRequestDecisionNotification(UUID restaurantId,
                                                                           RequestStatus action,
                                                                           String requestCategory,
                                                                           UUID referenceId,
                                                                           Locale locale) {
        if (restaurantId == null || action == null) {
            log.debug("Skipping WebSocket notification for transaction-status: restaurantId={}, action={}", restaurantId, action);
            return;
        }
        
        if (messagingTemplate == null) {
            log.debug("SimpMessagingTemplate is not available, will only publish to RabbitMQ. restaurantId={}, category={}, decision={}", 
                    restaurantId, requestCategory, action.name());
        }
        
        try {
            String topic = "/topic/restaurant/" + restaurantId + "/transaction-status";
            
            Map<String, Object> data = new HashMap<>();
            data.put("restaurantId", restaurantId.toString());
            data.put("requestCategory", requestCategory);
            data.put("referenceId", referenceId != null ? referenceId.toString() : null);
            data.put("decision", action.name());
            data.put("notificationType", "REQUEST_DECISION");
            data.put("timestamp", LocalDateTime.now(ZoneOffset.UTC).format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
            

            String messageKeyPrefix = getRequestCategoryMessageKeyPrefix(requestCategory);
            String messageKey = action == RequestStatus.APPROVED
                    ? messageKeyPrefix + ".approved"
                    : messageKeyPrefix + ".declined";
            String message = messageUtil.getMessage(messageKey, locale);

           
            
            // Send WebSocket notification directly using SimpMessagingTemplate (like OrderServiceImpl)
            if (messagingTemplate != null) {
                StatusEventMessage eventMessage = StatusEventMessage.builder()
                        .message(message)
                        .notificationType("REQUEST_DECISION")
                        .status(action.name())
                        .data(data)
                        .build();
                messagingTemplate.convertAndSend(topic, eventMessage);
                log.info("Sent WebSocket notification to transaction-status topic: category={}, decision={}, restaurantId={}", 
                        requestCategory, action.name(), restaurantId);
            }
            
            // Also publish to RabbitMQ for integration service to log (similar to OrderServiceImpl)
            if (rabbitTemplate != null) {
                try {
                    Map<String, Object> wsMessage = new HashMap<>();
                    wsMessage.put("topic", topic);
                    wsMessage.put("message", message);
                    wsMessage.put("notificationType", "REQUEST_DECISION");
                    wsMessage.put("status", action.name());
                    wsMessage.put("data", data);
                    wsMessage.put("timestamp", LocalDateTime.now(ZoneOffset.UTC).format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
                    wsMessage.put("type", "websocket_notification");
                    
                    rabbitTemplate.convertAndSend(NOTIFICATION_TOPIC_EXCHANGE, NOTIFICATION_ROUTING_KEY, wsMessage);
                    log.info("Published request decision WebSocket message to RabbitMQ for transaction-status: topic={}, category={}, decision={}",
                            topic, requestCategory, action.name());
                } catch (AmqpException e) {
                    // RabbitMQ connection errors should not fail the main transaction
                    log.warn("Failed to publish to RabbitMQ for transaction-status notification (non-critical): {}", e.getMessage());
                }
            }
        } catch (Exception e) {
            log.error("Failed to send WebSocket notification for transaction-status request decision: category={}, decision={}, error={}", 
                    requestCategory, action.name(), e.getMessage(), e);
        }
    }
    
    /**
     * Save notification for table/section request approval/decline
     */
    private void saveTableSectionRequestNotification(User requester, RequestStatus action, String entityType, UUID entityId, User approver, Locale userLocale) {
        try {
            Locale loc = localeForRecipient(requester, userLocale);
            String notificationType = action == RequestStatus.APPROVED 
                    ? "TABLE_SECTION_REQUEST_APPROVED" 
                    : "TABLE_SECTION_REQUEST_DECLINED";
            String bodyKey = action == RequestStatus.APPROVED
                    ? "notification.table.section.request.approved"
                    : "notification.table.section.request.declined";
            String title = messageUtil.getMessage(bodyKey, loc);
            String message = messageUtil.getMessage(bodyKey, loc, entityType);
            
            java.util.Map<String, String> requestData = new java.util.HashMap<>();
            if (entityId != null) {
                requestData.put("requestId", entityId.toString());
                if ("Table".equals(entityType)) {
                    requestData.put("tableId", entityId.toString());
                } else if ("Section".equals(entityType)) {
                    requestData.put("sectionId", entityId.toString());
                }
            }
            
            Notification notification = Notification.builder()
                    .user(requester)
                    .title(title)
                    .type(notificationType)
                    .message(message)
                    .bodyKey(bodyKey)
                    .bodyArgs(serializeBodyArgs(entityType))
                    .additionalData(serializeRequestData(requestData))
                    .createdBy(approver)
                    .read(false)
                    .createdAt(OffsetDateTime.now(ZoneOffset.UTC))
                    .build();
            
            Notification savedNotification = notificationRepository.save(notification);
            log.info("Saved notification for table/section request {} for user {}", action, requester.getId());
            
            // Publish notification to RabbitMQ for FCM processing
            try {
                notificationPublisherService.publishNotification(savedNotification, requester);
            } catch (Exception e) {
                log.error("Failed to publish notification to RabbitMQ: {}", e.getMessage(), e);
            }
        } catch (Exception e) {
            log.error("Failed to save table/section request notification: {}", e.getMessage(), e);
        }
    }
    
    /**
     * Save notification for item cancellation request approval/decline
     */
    private void saveItemCancellationRequestNotification(User requester, RequestStatus action, OrderedItem orderedItem, User manager, Locale userLocale) {
        try {
            Locale loc = localeForRecipient(requester, userLocale);
            log.info("=== ITEM CANCELLATION REQUEST FCM NOTIFICATION - START ===");
            log.info("=== ITEM CANCELLATION REQUEST FCM NOTIFICATION - Action: {}, Requester ID: {}, Requester Name: {} {}, Manager ID: {}, Manager Name: {} {} ===",
                    action, 
                    requester != null ? requester.getId() : "NULL",
                    requester != null ? requester.getFirstName() : "NULL",
                    requester != null ? requester.getLastName() : "NULL",
                    manager != null ? manager.getId() : "NULL",
                    manager != null ? manager.getFirstName() : "NULL",
                    manager != null ? manager.getLastName() : "NULL");
            
            String notificationType = action == RequestStatus.APPROVED 
                    ? "ITEM_CANCELLATION_REQUEST_APPROVED" 
                    : "ITEM_CANCELLATION_REQUEST_DECLINED";
            String title = action == RequestStatus.APPROVED 
                    ? messageUtil.getMessage("notification.cancellation.approved.title", loc)
                    : messageUtil.getMessage("notification.cancellation.rejected.title", loc);
            
            log.info("=== ITEM CANCELLATION REQUEST FCM NOTIFICATION - Notification Type: {}, Title: {} ===", notificationType, title);
            
            String itemName = "Item";
            try {
                if (orderedItem.getItem() != null) {
                    org.hibernate.Hibernate.initialize(orderedItem.getItem());
                    log.info("=== ITEM CANCELLATION REQUEST FCM NOTIFICATION - Item ID: {} ===", orderedItem.getItem().getId());
                    if (orderedItem.getItem().getTranslations() != null) {
                        org.hibernate.Hibernate.initialize(orderedItem.getItem().getTranslations());
                    }
                    itemName = resolveLocalizedItemName(orderedItem.getItem(), loc != null ? loc : userLocale);
                    log.info("=== ITEM CANCELLATION REQUEST FCM NOTIFICATION - Item Name: {} ===", itemName);
                } else {
                    log.warn("=== ITEM CANCELLATION REQUEST FCM NOTIFICATION - OrderedItem has no Item entity ===");
                }
            } catch (Exception e) {
                log.warn("=== ITEM CANCELLATION REQUEST FCM NOTIFICATION - Could not get item name for notification: {} ===", e.getMessage());
            }
            
            // Get table code
            String tableCode = "";
            try {
                if (orderedItem.getOrder() != null && orderedItem.getOrder().getId() != null) {
                    log.info("=== ITEM CANCELLATION REQUEST FCM NOTIFICATION - Order ID: {} ===", orderedItem.getOrder().getId());
                    java.util.Optional<String> tableCodeOpt = orderRepository.findTableCodeByOrderId(orderedItem.getOrder().getId());
                    if (tableCodeOpt.isPresent()) {
                        tableCode = tableCodeOpt.get();
                        log.info("=== ITEM CANCELLATION REQUEST FCM NOTIFICATION - Table Code: {} ===", tableCode);
                    } else if (orderedItem.getOrder().getRestaurantTable() != null) {
                        // Fallback to table order number if code not available
                        Integer tableOrder = orderedItem.getOrder().getRestaurantTable().getTableOrder();
                        tableCode = tableOrder != null ? tableOrder.toString() : "";
                        log.info("=== ITEM CANCELLATION REQUEST FCM NOTIFICATION - Table Order (fallback): {} ===", tableCode);
                    }
                } else {
                    log.warn("=== ITEM CANCELLATION REQUEST FCM NOTIFICATION - OrderedItem has no Order entity ===");
                }
            } catch (Exception e) {
                log.warn("=== ITEM CANCELLATION REQUEST FCM NOTIFICATION - Could not get table code for item cancellation notification: {} ===", e.getMessage());
            }
            
            // Log ordered item details
            log.info("=== ITEM CANCELLATION REQUEST FCM NOTIFICATION - OrderedItem ID: {}, Quantity: {}, Price: {}, Item Status: {} ===",
                    orderedItem.getId(), orderedItem.getQuantity(), orderedItem.getPrice(), orderedItem.getItemStatus());
            
            // Use same format as order cancellation: "Cancellation request for item "{0}" at table {1} has been approved. {2}"
            String bodyKey = action == RequestStatus.APPROVED
                    ? "notification.cancellation.approved.body"
                    : "notification.cancellation.rejected.body";
            String message = messageUtil.getMessage(bodyKey, loc, itemName, tableCode, "");
            
            log.info("=== ITEM CANCELLATION REQUEST FCM NOTIFICATION - Notification Message: {} ===", message);
            
            // Log requester device token status
            String deviceToken = requester != null ? requester.getDeviceToken() : null;
            log.info("=== ITEM CANCELLATION REQUEST FCM NOTIFICATION - Requester Device Token: {} ===",
                    deviceToken != null && !deviceToken.trim().isEmpty() 
                            ? "PRESENT (length: " + deviceToken.length() + ")" 
                            : "MISSING or EMPTY");
            
            java.util.Map<String, String> requestData = new java.util.HashMap<>();
            requestData.put("requestId", orderedItem.getId().toString());
            requestData.put("orderedItemId", orderedItem.getId().toString());
            requestData.put("orderId", orderedItem.getOrder() != null && orderedItem.getOrder().getId() != null ? orderedItem.getOrder().getId().toString() : null);
            
            Notification notification = Notification.builder()
                    .user(requester)
                    .title(title)
                    .type(notificationType)
                    .message(message)
                    .bodyKey(bodyKey)
                    .bodyArgs(serializeBodyArgs(itemName, tableCode, ""))
                    .additionalData(serializeRequestData(requestData))
                    .createdBy(manager)
                    .read(false)
                    .createdAt(OffsetDateTime.now(ZoneOffset.UTC))
                    .build();
            
            Notification savedNotification = notificationRepository.save(notification);
            log.info("=== ITEM CANCELLATION REQUEST FCM NOTIFICATION - Saved notification to database. Notification ID: {} ===", savedNotification.getId());
            
            // Publish notification to RabbitMQ for FCM processing
            try {
                log.info("=== ITEM CANCELLATION REQUEST FCM NOTIFICATION - Publishing to RabbitMQ for FCM processing ===");
                notificationPublisherService.publishNotification(savedNotification, requester);
                log.info("=== ITEM CANCELLATION REQUEST FCM NOTIFICATION - Successfully published to RabbitMQ ===");
            } catch (Exception e) {
                log.error("=== ITEM CANCELLATION REQUEST FCM NOTIFICATION - Failed to publish notification to RabbitMQ: {} ===", e.getMessage(), e);
            }
            
            log.info("=== ITEM CANCELLATION REQUEST FCM NOTIFICATION - END ===");
        } catch (Exception e) {
            log.error("=== ITEM CANCELLATION REQUEST FCM NOTIFICATION - Failed to save item cancellation request notification: {} ===", e.getMessage(), e);
        }
    }
    
    /**
     * Save notification for combo cancellation request approval/decline
     */
    private void saveComboCancellationRequestNotification(User requester, RequestStatus action, OrderedCombo orderedCombo, User manager, Locale userLocale) {
        try {
            Locale loc = localeForRecipient(requester, userLocale);
            String notificationType = action == RequestStatus.APPROVED 
                    ? "COMBO_CANCELLATION_REQUEST_APPROVED" 
                    : "COMBO_CANCELLATION_REQUEST_DECLINED";
            String title = action == RequestStatus.APPROVED 
                    ? messageUtil.getMessage("notification.item.cancellation.approved.title", loc)
                    : messageUtil.getMessage("notification.item.cancellation.declined.title", loc);
            
            String comboName = "Combo";
            try {
                if (orderedCombo.getCombo() != null) {
                    org.hibernate.Hibernate.initialize(orderedCombo.getCombo());
                    if (orderedCombo.getCombo().getTranslations() != null) {
                        org.hibernate.Hibernate.initialize(orderedCombo.getCombo().getTranslations());
                    }
                    comboName = resolveLocalizedComboName(orderedCombo.getCombo(), loc != null ? loc : userLocale);
                }
            } catch (Exception e) {
                log.warn("Could not get combo name for notification: {}", e.getMessage());
            }
            
            // Get table code
            String tableCode = "";
            try {
                if (orderedCombo.getOrder() != null && orderedCombo.getOrder().getId() != null) {
                    java.util.Optional<String> tableCodeOpt = orderRepository.findTableCodeByOrderId(orderedCombo.getOrder().getId());
                    if (tableCodeOpt.isPresent()) {
                        tableCode = tableCodeOpt.get();
                    } else if (orderedCombo.getOrder().getRestaurantTable() != null) {
                        // Fallback to table order number if code not available
                        Integer tableOrder = orderedCombo.getOrder().getRestaurantTable().getTableOrder();
                        tableCode = tableOrder != null ? tableOrder.toString() : "";
                    }
                }
            } catch (Exception e) {
                log.warn("Could not get table code for combo cancellation notification: {}", e.getMessage());
            }
            
            // Use same format as order cancellation: "Cancellation request for combo "{0}" at table {1} has been approved. {2}"
            String bodyKey = action == RequestStatus.APPROVED
                    ? "notification.cancellation.approved.body"
                    : "notification.cancellation.rejected.body";
            String message = messageUtil.getMessage(bodyKey, loc, comboName, tableCode, "");
            
            java.util.Map<String, String> requestData = new java.util.HashMap<>();
            requestData.put("requestId", orderedCombo.getId().toString());
            requestData.put("orderedComboId", orderedCombo.getId().toString());
            requestData.put("orderId", orderedCombo.getOrder() != null && orderedCombo.getOrder().getId() != null ? orderedCombo.getOrder().getId().toString() : null);
            
            Notification notification = Notification.builder()
                    .user(requester)
                    .title(title)
                    .type(notificationType)
                    .message(message)
                    .bodyKey(bodyKey)
                    .bodyArgs(serializeBodyArgs(comboName, tableCode, ""))
                    .additionalData(serializeRequestData(requestData))
                    .createdBy(manager)
                    .read(false)
                    .createdAt(OffsetDateTime.now(ZoneOffset.UTC))
                    .build();
            
            Notification savedNotification = notificationRepository.save(notification);
            log.info("Saved notification for combo cancellation request {} for user {}", action, requester.getId());
            
            // Publish notification to RabbitMQ for FCM processing
            try {
                notificationPublisherService.publishNotification(savedNotification, requester);
            } catch (Exception e) {
                log.error("Failed to publish notification to RabbitMQ: {}", e.getMessage(), e);
            }
        } catch (Exception e) {
            log.error("Failed to save combo cancellation request notification: {}", e.getMessage(), e);
        }
    }

    /**
     * Save notification for order cancellation request approval/decline
     */
    private void saveOrderCancellationRequestNotification(User requester, RequestStatus action, String orderNumber, UUID orderId, User manager, Locale userLocale) {
        try {
            Locale loc = localeForRecipient(requester, userLocale);
            String notificationType = action == RequestStatus.APPROVED 
                    ? "CANCELLATION_APPROVED" 
                    : "CANCELLATION_REJECTED";
            String title = action == RequestStatus.APPROVED 
                    ? messageUtil.getMessage("notification.cancellation.approved.title", loc)
                    : messageUtil.getMessage("notification.cancellation.rejected.title", loc);
            String bodyKey = action == RequestStatus.APPROVED
                    ? "notification.cancellation.approved.body"
                    : "notification.cancellation.rejected.body";
            String message = messageUtil.getMessage(bodyKey, loc, orderNumber, "", "");
            
            java.util.Map<String, String> requestData = new java.util.HashMap<>();
            requestData.put("requestId", orderId != null ? orderId.toString() : null);
            requestData.put("orderId", orderId != null ? orderId.toString() : null);
            requestData.put("orderNumber", orderNumber);
            
            Notification notification = Notification.builder()
                    .user(requester)
                    .title(title)
                    .type(notificationType)
                    .message(message)
                    .bodyKey(bodyKey)
                    .bodyArgs(serializeBodyArgs(orderNumber, "", ""))
                    .additionalData(serializeRequestData(requestData))
                    .createdBy(manager)
                    .read(false)
                    .createdAt(OffsetDateTime.now(ZoneOffset.UTC))
                    .build();
            
            Notification savedNotification = notificationRepository.save(notification);
            log.info("Saved notification for order cancellation request {} for user {}", action, requester.getId());
            
            // Publish notification to RabbitMQ for FCM processing
            try {
                notificationPublisherService.publishNotification(savedNotification, requester);
            } catch (Exception e) {
                log.error("Failed to publish notification to RabbitMQ: {}", e.getMessage(), e);
            }
        } catch (Exception e) {
            log.error("Failed to save order cancellation request notification: {}", e.getMessage(), e);
        }
    }
    
    /**
     * Build OrderCancellationRequestResponse from Order entity
     */
    private OrderCancellationRequestResponse buildOrderCancellationRequestResponse(Order order, Locale userLocale) {
        // Initialize lazy-loaded relationships to ensure they're available
        // This is a defensive measure since order may be fetched with or without relationships
        try {
            if (order.getRestaurant() != null) {
                org.hibernate.Hibernate.initialize(order.getRestaurant());
                if (order.getRestaurant().getTranslations() != null) {
                    org.hibernate.Hibernate.initialize(order.getRestaurant().getTranslations());
                }
            }
            if (order.getCancellationRequestedBy() != null) {
                org.hibernate.Hibernate.initialize(order.getCancellationRequestedBy());
            }
            if (order.getCancellationReviewedBy() != null) {
                org.hibernate.Hibernate.initialize(order.getCancellationReviewedBy());
            }
            if (order.getRestaurantTable() != null) {
                org.hibernate.Hibernate.initialize(order.getRestaurantTable());
            }
        } catch (Exception e) {
            log.warn("Could not initialize lazy relationships for order {}: {}", order.getId(), e.getMessage());
            // Continue anyway - relationships may already be loaded
        }
        
        String cancellationReason = null;
        if (order.getCancellationRequestData() != null) {
            try {
                ObjectMapper objectMapper = new ObjectMapper();
                com.gulfnet.shared_library.model.request.OrderCancellationRequestDto requestDto = objectMapper.readValue(
                        order.getCancellationRequestData(), com.gulfnet.shared_library.model.request.OrderCancellationRequestDto.class);
                cancellationReason = requestDto.getCancellationReason();
            } catch (JsonProcessingException e) {
                log.warn("Failed to parse cancellation request data for order {}: {}", order.getId(), e.getMessage());
            }
        }
        
        String requestedByName = null;
        String requestedByRole = null;
        if (order.getCancellationRequestedBy() != null && order.getCancellationRequestedBy().getRoleId() != null) {
            requestedByName = order.getCancellationRequestedBy().getFirstName() + " " + 
                    order.getCancellationRequestedBy().getLastName();
            Optional<Role> requesterRole = roleRepository.findById(order.getCancellationRequestedBy().getRoleId());
            if (requesterRole.isPresent()) {
                requestedByRole = requesterRole.get().getName();
            }
        }
        
        String reviewedByName = null;
        if (order.getCancellationReviewedBy() != null) {
            reviewedByName = order.getCancellationReviewedBy().getFirstName() + " " + 
                    order.getCancellationReviewedBy().getLastName();
        }

        // Best-effort: attach transactionNumber when a Transaction exists for this order.
        // Without this, unified request-details will always return null even if the transaction table has the value.
        String transactionNumber = null;
        try {
            transactionNumber = transactionRepository.findByOrderId(order.getId())
                    .map(com.gulfnet.shared_library.entity.Transaction::getTransactionNumber)
                    .orElse(null);
        } catch (Exception e) {
            log.debug("Could not fetch transactionNumber for order {}: {}", order.getId(), e.getMessage());
        }
        
        // Get restaurant name
        String restaurantName = null;
        UUID restaurantId = null;
        if (order.getRestaurant() != null) {
            restaurantId = order.getRestaurant().getId();
            if (order.getRestaurant().getTranslations() != null && !order.getRestaurant().getTranslations().isEmpty()) {
                String userLanguage = userLocale.getLanguage();
                restaurantName = order.getRestaurant().getTranslations().stream()
                        .filter(t -> t.getLanguageCode() != null && t.getLanguageCode().startsWith(userLanguage))
                        .findFirst()
                        .map(com.gulfnet.shared_library.entity.RestaurantTranslation::getName)
                        .orElse(order.getRestaurant().getTranslations().get(0).getName());
            } else {
                restaurantName = order.getRestaurant().getRestaurantCode() != null ? order.getRestaurant().getRestaurantCode() : "Restaurant";
            }
        }
        
        return OrderCancellationRequestResponse.builder()
                .orderId(order.getId())
                .orderNumber(order.getOrderNumber())
                .transactionNumber(transactionNumber)
                .currentOrderStatus(order.getOrderStatus())
                .cancellationReason(cancellationReason)
                .requestStatus(order.getCancellationRequestStatus())
                .requestedAt(order.getCancellationRequestedAt() != null ? order.getCancellationRequestedAt().toLocalDateTime() : null)
                .requestedBy(order.getCancellationRequestedBy() != null ? order.getCancellationRequestedBy().getId() : null)
                .requestedByName(requestedByName)
                .requestedByRole(requestedByRole)
                .reviewedAt(order.getCancellationReviewedAt() != null ? ((OffsetDateTime) order.getCancellationReviewedAt()).toLocalDateTime() : null)
                .reviewedBy(order.getCancellationReviewedBy() != null ? order.getCancellationReviewedBy().getId() : null)
                .reviewedByName(reviewedByName)
                .comments(order.getCancellationComments())
                .restaurantId(restaurantId)
                .restaurantName(restaurantName)
                .tableId(order.getRestaurantTable() != null ? order.getRestaurantTable().getId() : null)
                .tableName(order.getRestaurantTable() != null ? order.getRestaurantTable().getTableOrder().toString() : null)
                .totalAmount(order.getTotalAmount())
                .build();
    }

    /**
     * Helper method to extract requestType from request data JSON
     * Returns the requestType value if found, null otherwise
     */
    private String getRequestTypeFromData(String requestDataJson) {
        if (requestDataJson == null || requestDataJson.trim().isEmpty()) {
            return null;
        }
        try {
            ObjectMapper objectMapper = new ObjectMapper();
            Map<String, Object> requestData = objectMapper.readValue(requestDataJson, Map.class);
            return (String) requestData.get("requestType");
        } catch (Exception e) {
            log.warn("Error parsing request data to extract requestType: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Helper method to convert request type slug ID to display name
     * Supports backward compatibility by accepting both slug IDs and display names
     */
    private static final Map<String, String> REQUEST_TYPE_SLUG_TO_KEY_MAP = Map.of(
            "profile-update", "request.type.profile.update",
            "additional-discount", "request.type.additional.discount",
            "table-section", "request.type.table.section",
            "refund", "request.type.refund",
            "item-cancellation", "request.type.item.cancellation",
            "transaction-cancellation", "request.type.transaction.cancellation",
            "order-cancellation", "request.type.order.cancellation",
            "shift-discrepancy", "request.type.shift.discrepancy"
    );

    private String convertRequestTypeSlugToDisplayName(String slugOrName, Locale locale) {
        if (slugOrName == null || slugOrName.trim().isEmpty()) {
            return null;
        }
        String messageKey = REQUEST_TYPE_SLUG_TO_KEY_MAP.get(slugOrName.trim().toLowerCase());
        return messageKey != null ? messageUtil.getMessage(messageKey, locale) : null;
    }

    @Override
    @Transactional(readOnly = true)
    public ResponseDto<RequestTypeListResponse> getAllRequestTypes() {
        Locale userLocale = LocaleContextHolder.getLocale();
        
        log.info("Fetching all request types");
        
        List<RequestTypeResponse> requestTypes = List.of(
            RequestTypeResponse.builder()
                .id("profile-update")
                .name("Profile Update Request")
                .build(),
            RequestTypeResponse.builder()
                .id("additional-discount")
                .name("Additional Discount Request")
                .build(),
            RequestTypeResponse.builder()
                .id("refund")
                .name("Refund Request")
                .build(),
            RequestTypeResponse.builder()
                .id("item-cancellation")
                .name("Item Cancellation Request")
                .build(),
            RequestTypeResponse.builder()
                .id("transaction-cancellation")
                .name("Transaction Cancellation Request")
                .build(),
            RequestTypeResponse.builder()
                .id("order-cancellation")
                .name("Order Cancellation Request")
                .build(),
            RequestTypeResponse.builder()
                .id("shift-discrepancy")
                .name("Shift Discrepancy Request")
                .build()
        );
        
        RequestTypeListResponse requestTypeListResponse = RequestTypeListResponse.builder()
                .requestTypes(requestTypes)
                .count((long) requestTypes.size())
                .total((long) requestTypes.size())
                .metaData(null)
                .build();
        
        return ResponseDto.<RequestTypeListResponse>builder()
                .message(messageUtil.getMessage("request.type.fetch.success", userLocale))
                .data(requestTypeListResponse)
                .build();
    }

    @Override
    public ResponseDto<EmailAvailabilityResponse> checkEmailAvailability(EmailAvailabilityRequest request, String locale) {
        Locale userLocale = locale != null ? Locale.forLanguageTag(locale) : LocaleContextHolder.getLocale();
        
        log.info("Checking email availability for: {} (excluding userId: {})", request.getEmail(), request.getUserId());
        
        // Check if email already exists
        boolean emailExists;
        if (request.getUserId() != null) {
            // For update scenario: exclude the current user from the check
            // existsByEmailExcludingUser already uses case-insensitive check
            emailExists = userRepository.existsByEmailExcludingUser(request.getEmail(), request.getUserId());
        } else {
            // For create scenario: check if email exists for any user (case-insensitive)
            emailExists = userRepository.existsByEmailIgnoreCase(request.getEmail());
        }
        
        if (emailExists) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    messageUtil.getMessage("email.availability.already.exists", userLocale, request.getEmail()));
        }
        
        // Email is available - return simple response
        return ResponseDto.<EmailAvailabilityResponse>builder()
                .data(EmailAvailabilityResponse.builder()
                        .email(request.getEmail())
                        .message(messageUtil.getMessage("email.availability.available", userLocale))
                        .build())
                .build();
    }

    @Override
    @Transactional
    public ResponseDto<Void> restoreUsers(List<UUID> ids, String userId) {
        Locale userLocale = LocaleContextHolder.getLocale();
        
        // Find user for updatedBy
        User updater = userRepository.findById(UUID.fromString(userId))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        messageUtil.getMessage("user.not.found", userLocale, userId)));
        
        // Find all users by IDs
        List<User> users = userRepository.findAllById(ids);
        
        if (users.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                    messageUtil.getMessage("user.not.found", userLocale));
        }
        
        // Filter only deleted users and restore them
        List<User> deletedUsers = users.stream()
                .filter(u -> Boolean.TRUE.equals(u.getIsDeleted()))
                .collect(Collectors.toList());
        
        if (deletedUsers.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    messageUtil.getMessage("user.restore.error.not.deleted", userLocale));
        }
        
        // Restore all deleted users
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        for (User user : deletedUsers) {
            user.setIsDeleted(false);
            if (EntityStatus.INACTIVE.equals(user.getStatus())) {
                user.setStatus(EntityStatus.ACTIVE);
            }
            user.setDeletedReason(null); // Clear deleted reason when restoring
            user.setUpdatedAt(now);
            user.setUpdatedBy(updater);
        }
        
        userRepository.saveAll(deletedUsers);
        
        return ResponseDto.<Void>builder()
            .message(messageUtil.getMessage("user.restore.success", userLocale))
            .build();
    }

    /**
     * Helper method to automatically assign a user to the default KDS for their restaurant
     * This is called when:
     * 1. A new user with KDS role is registered with a restaurant
     * 2. An existing user with KDS role is assigned/changed to a restaurant
     */
    private void assignUserToDefaultKds(User user, User actionUser, Locale userLocale) {
        try {
            // Check if user has restaurant assigned
            if (user.getRestaurantId() == null) {
                log.debug("User {} has no restaurant assigned, skipping default KDS assignment", user.getId());
                return;
            }

            // Find default KDS for the restaurant
            List<com.gulfnet.shared_library.entity.Kds> defaultKdsList = 
                    kdsRepository.findByRestaurantIdAndIsDefaultTrueAndIsDeletedFalse(user.getRestaurantId());

            if (defaultKdsList.isEmpty()) {
                log.info("No default KDS found for restaurant {}, skipping auto-assignment for user {}", 
                        user.getRestaurantId(), user.getId());
                return;
            }

            // Get the first default KDS (should only be one)
            com.gulfnet.shared_library.entity.Kds defaultKds = defaultKdsList.get(0);

            // Check if user is already assigned to this KDS
            boolean alreadyAssigned = kdsConfigurationRepository.existsByUserIdAndKdsId(user.getId(), defaultKds.getId());
            if (alreadyAssigned) {
                log.debug("User {} is already assigned to default KDS {}, skipping", user.getId(), defaultKds.getId());
                return;
            }

            // Get device code from KDS (can be null if not yet initialized)
            String deviceCode = defaultKds.getDeviceCode();

            // Create KDS configuration
            OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
            com.gulfnet.shared_library.entity.KdsConfiguration configuration = 
                    com.gulfnet.shared_library.entity.KdsConfiguration.builder()
                            .user(user)
                            .kds(defaultKds)
                            .deviceCode(deviceCode)
                            .createdAt(now)
                            .createdBy(actionUser)
                            .updatedAt(now)
                            .updatedBy(actionUser)
                            .build();

            kdsConfigurationRepository.save(configuration);
            log.info("Automatically assigned user {} to default KDS {} for restaurant {}", 
                    user.getId(), defaultKds.getId(), user.getRestaurantId());

        } catch (Exception e) {
            log.error("Error assigning user {} to default KDS: {}", user.getId(), e.getMessage(), e);
            // Don't throw exception - this is a convenience feature, shouldn't break user creation/update
        }
    }

    /**
     * Process refund approval by creating Refund and RefundItem entities from Transaction.requestData
     */
    private void processRefundApproval(Transaction transaction, User manager, OffsetDateTime now, Locale userLocale) {
        try {
            ObjectMapper objectMapper = new ObjectMapper();
            Map<String, Object> requestData = objectMapper.readValue(transaction.getRequestData(), Map.class);
            
            // Parse refund type
            RefundType refundType = RefundType.valueOf((String) requestData.get("refundType"));
            
            // Parse amounts
            BigDecimal totalRefundAmount = requestData.containsKey("totalRefundAmount") 
                    ? new BigDecimal(((Number) requestData.get("totalRefundAmount")).toString())
                    : BigDecimal.ZERO;
            BigDecimal subtotalRefundAmount = requestData.containsKey("subtotalRefundAmount") 
                    ? new BigDecimal(((Number) requestData.get("subtotalRefundAmount")).toString())
                    : BigDecimal.ZERO;
            BigDecimal taxRefundAmount = requestData.containsKey("taxRefundAmount") 
                    ? new BigDecimal(((Number) requestData.get("taxRefundAmount")).toString())
                    : BigDecimal.ZERO;
            BigDecimal serviceChargeRefundAmount = requestData.containsKey("serviceChargeRefundAmount") 
                    ? new BigDecimal(((Number) requestData.get("serviceChargeRefundAmount")).toString())
                    : BigDecimal.ZERO;
            BigDecimal packingChargeRefundAmount = requestData.containsKey("packingChargeRefundAmount") 
                    ? new BigDecimal(((Number) requestData.get("packingChargeRefundAmount")).toString())
                    : BigDecimal.ZERO;
            BigDecimal discountRefundAmount = requestData.containsKey("discountRefundAmount") 
                    ? new BigDecimal(((Number) requestData.get("discountRefundAmount")).toString())
                    : BigDecimal.ZERO;
            BigDecimal additionalDiscountRefundAmount = requestData.containsKey("additionalDiscountRefundAmount") 
                    ? new BigDecimal(((Number) requestData.get("additionalDiscountRefundAmount")).toString())
                    : BigDecimal.ZERO;
            BigDecimal alcoholicTaxRefundAmount = requestData.containsKey("alcoholicTaxRefundAmount") 
                    ? new BigDecimal(((Number) requestData.get("alcoholicTaxRefundAmount")).toString())
                    : BigDecimal.ZERO;
            BigDecimal nonAlcoholicTaxRefundAmount = requestData.containsKey("nonAlcoholicTaxRefundAmount") 
                    ? new BigDecimal(((Number) requestData.get("nonAlcoholicTaxRefundAmount")).toString())
                    : BigDecimal.ZERO;
            
            // Generate refund number
            String refundNumber = generateRefundNumber(transaction.getRestaurant());
            
            // Create Refund entity
            Refund refund = Refund.builder()
                    .transaction(transaction)
                    .refundNumber(refundNumber)
                    .refundType(refundType)
                    .refundReason((String) requestData.get("refundReason"))
                    .refundMethod(transaction.getPaymentMethod())
                    .totalRefundAmount(totalRefundAmount)
                    .subtotalRefundAmount(subtotalRefundAmount)
                    .taxRefundAmount(taxRefundAmount)
                    .alcoholicTaxRefundAmount(alcoholicTaxRefundAmount)
                    .nonAlcoholicTaxRefundAmount(nonAlcoholicTaxRefundAmount)
                    .serviceChargeRefundAmount(serviceChargeRefundAmount)
                    .packingChargeRefundAmount(packingChargeRefundAmount)
                    .discountRefundAmount(discountRefundAmount)
                    .additionalDiscountRefundAmount(additionalDiscountRefundAmount)
                    .createdAt(now)
                    .updatedAt(now)
                    .build();
            
            refund = refundRepository.save(refund);
            
            // Create RefundItem entities from requestData
            List<RefundItem> refundItemsToSave = new ArrayList<>();
            
            // Parse orderedItems
            if (requestData.containsKey("orderedItems")) {
                List<Map<String, Object>> orderedItemsJson = (List<Map<String, Object>>) requestData.get("orderedItems");
                for (Map<String, Object> itemJson : orderedItemsJson) {
                    UUID orderedItemId = UUID.fromString((String) itemJson.get("orderedItemId"));
                    Integer quantity = ((Number) itemJson.get("refundQuantity")).intValue();
                    BigDecimal refundAmount = new BigDecimal(((Number) itemJson.get("refundAmount")).toString());
                    
                    OrderedItem orderedItem = orderedItemRepository.findById(orderedItemId)
                            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                                    messageUtil.getMessage("ordered.item.not.found", userLocale)));
                    
                    refundItemsToSave.add(RefundItem.builder()
                            .refund(refund)
                            .orderedItem(orderedItem)
                            .orderedCombo(null)
                            .quantity(quantity)
                            .refundAmount(refundAmount)
                            .build());
                }
            }
            
            // Parse orderedCombos
            if (requestData.containsKey("orderedCombos")) {
                List<Map<String, Object>> orderedCombosJson = (List<Map<String, Object>>) requestData.get("orderedCombos");
                for (Map<String, Object> comboJson : orderedCombosJson) {
                    UUID orderedComboId = UUID.fromString((String) comboJson.get("orderedComboId"));
                    Integer quantity = ((Number) comboJson.get("refundQuantity")).intValue();
                    BigDecimal refundAmount = new BigDecimal(((Number) comboJson.get("refundAmount")).toString());
                    
                    OrderedCombo orderedCombo = orderedComboRepository.findById(orderedComboId)
                            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                                    messageUtil.getMessage("ordered.combo.not.found", userLocale)));
                    
                    refundItemsToSave.add(RefundItem.builder()
                            .refund(refund)
                            .orderedItem(null)
                            .orderedCombo(orderedCombo)
                            .quantity(quantity)
                            .refundAmount(refundAmount)
                            .build());
                }
            }
            
            if (!refundItemsToSave.isEmpty()) {
                refundItemRepository.saveAll(refundItemsToSave);
            }
            
            // Update transaction request status (transaction status should remain COMPLETED)
            transaction.setRequestStatus(RequestStatus.APPROVED);
            // Transaction status should not change - it should remain COMPLETED
            transaction.setUpdatedAt(now);
            transactionRepository.save(transaction);
            
            // ==================== REAL-TIME HQ ALERT EVALUATION ====================
            // Check if refund percentage threshold is breached after this refund approval.
            // Must run AFTER transaction commits so the REQUIRES_NEW alert transaction can see the data.
            try {
                Restaurant alertRestaurant = transaction.getRestaurant();
                if (alertRestaurant != null) {
                    triggerAlertEvaluationAfterCommit(alertRestaurant, userLocale, "refund approval");
                }
            } catch (Exception e) {
                log.error("Failed to trigger alert evaluation after refund approval: {}", e.getMessage(), e);
            }
            
        } catch (JsonProcessingException e) {
            log.error("Error parsing refund request data: {}", e.getMessage());
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    messageUtil.getMessage("refund.request.error", userLocale));
        }
    }

    /**
     * Generates a unique refund number similar to transaction number format
     * Format: {restaurantCode}-REF-{yyyyMMdd-HHmmss}-{4-digit-random}
     */
    private String generateRefundNumber(Restaurant restaurant) {
        String restaurantCode = restaurant.getRestaurantCode();
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        String timestamp = now.format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
        String random = String.format("%04d", (int) (Math.random() * 10000));
        
        return String.format("%s-REF-%s-%s", restaurantCode, timestamp, random);
    }

    /**
     * Find all active (logged-in) managers for a restaurant
     * Active managers are those who have a valid LoginAudit entry (logged in)
     * This method uses batch querying to avoid N+1 query problems
     */
    private List<User> findActiveManagersForRestaurant(UUID restaurantId) {
        try {
            // Find MANAGER role
            Optional<Role> managerRoleOpt = roleRepository.findByName("MANAGER");
            if (managerRoleOpt.isEmpty()) {
                // Try case-insensitive search
                List<Role> allRoles = roleRepository.findAll();
                managerRoleOpt = allRoles.stream()
                        .filter(r -> r.getName() != null && "MANAGER".equalsIgnoreCase(r.getName()))
                        .findFirst();
            }
            
            if (managerRoleOpt.isEmpty()) {
                log.warn("MANAGER role not found in database");
                return new ArrayList<>();
            }
            
            UUID managerRoleId = managerRoleOpt.get().getId();
            
            // Get all managers for the restaurant
            List<User> managers = userRepository.findAllByRestaurantIdAndRoleIdAndIsDeletedFalse(restaurantId, managerRoleId)
                    .stream()
                    .filter(u -> u.getStatus() == EntityStatus.ACTIVE)
                    .collect(Collectors.toList());
            
            if (managers.isEmpty()) {
                log.debug("No active managers found for restaurant {}", restaurantId);
                return new ArrayList<>();
            }
            
            // Batch fetch all login audits for managers in one query (fixes N+1 problem)
            List<UUID> managerIds = managers.stream()
                    .map(User::getId)
                    .collect(Collectors.toList());
            
            // Fetch login audits in batch (empty list check handled by repository)
            List<LoginAudit> loginAudits = managerIds.isEmpty() 
                    ? new ArrayList<>() 
                    : loginAuditRepository.findAllByUserIds(managerIds);
            Set<UUID> activeManagerIds = loginAudits.stream()
                    .map(la -> la.getUser() != null ? la.getUser().getId() : null)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toSet());
            
            // Filter to only those with active sessions (logged in)
            List<User> activeManagers = managers.stream()
                    .filter(manager -> activeManagerIds.contains(manager.getId()))
                    .collect(Collectors.toList());
            
            log.debug("Found {} active (logged-in) managers out of {} total managers for restaurant {}", 
                    activeManagers.size(), managers.size(), restaurantId);
            
            return activeManagers;
        } catch (Exception e) {
            log.error("Error finding active managers for restaurant {}: {}", restaurantId, e.getMessage(), e);
            return new ArrayList<>();
        }
    }

    /**
     * Notify all active managers about request resolution via WebSocket
     * This ensures all logged-in managers are aware when one manager responds to a request
     */
    private void notifyManagersAboutRequestResolution(List<User> activeManagers, User resolvingManager, 
            Object requestEntity, String requestType, boolean isApproved, String comments, Locale userLocale) {
        if (activeManagers == null || activeManagers.isEmpty()) {
            log.debug("No active managers to notify about request resolution");
            return;
        }
        
        // Filter out the manager who resolved the request (they already know)
        List<User> otherManagers = activeManagers.stream()
                .filter(manager -> !manager.getId().equals(resolvingManager.getId()))
                .collect(Collectors.toList());
        
        if (otherManagers.isEmpty()) {
            log.debug("No other active managers to notify (only resolving manager is active)");
            return;
        }
        
        log.info("Notifying {} other active manager(s) about {} request resolution by manager {}", 
                otherManagers.size(), requestType, resolvingManager.getId());
        
        try {
            // Build request identifier
            String requestIdentifier = "";
            if (requestEntity instanceof User) {
                User user = (User) requestEntity;
                requestIdentifier = user.getUserCode() != null ? user.getUserCode() : user.getId().toString();
            } else if (requestEntity instanceof Order) {
                Order order = (Order) requestEntity;
                requestIdentifier = order.getOrderNumber() != null ? order.getOrderNumber() : order.getId().toString();
            } else if (requestEntity instanceof Transaction) {
                Transaction transaction = (Transaction) requestEntity;
                // Use transaction number if available, otherwise use ID to avoid circular reference in toString()
                requestIdentifier = transaction.getTransactionNumber() != null && !transaction.getTransactionNumber().trim().isEmpty()
                        ? transaction.getTransactionNumber()
                        : transaction.getId().toString();
            } else {
                // For other types, try to get ID safely without calling toString() which might cause circular references
                try {
                    java.lang.reflect.Method getIdMethod = requestEntity.getClass().getMethod("getId");
                    Object id = getIdMethod.invoke(requestEntity);
                    requestIdentifier = id != null ? id.toString() : requestEntity.getClass().getSimpleName();
                } catch (Exception e) {
                    // Fallback: use class name instead of toString() to avoid circular references
                    requestIdentifier = requestEntity.getClass().getSimpleName();
                }
            }
            
            // Send WebSocket notification using NotificationService
            if (notificationService != null) {
                try {
                    java.lang.reflect.Method method = notificationService.getClass().getMethod(
                            "notifyManagersAboutRequestResolution",
                            List.class, User.class, String.class, String.class, boolean.class, String.class, Locale.class);
                    method.invoke(notificationService, otherManagers, resolvingManager, requestType, 
                            requestIdentifier, isApproved, comments, userLocale);
                    log.info("Successfully notified {} managers about {} request resolution", 
                            otherManagers.size(), requestType);
                } catch (NoSuchMethodException e) {
                    log.warn("notifyManagersAboutRequestResolution method not found in NotificationService: {}", e.getMessage());
                } catch (Exception e) {
                    log.error("Failed to send WebSocket notification to managers about request resolution: {}", e.getMessage(), e);
                }
            } else {
                log.debug("NotificationService not available, skipping WebSocket notification to managers");
            }
        } catch (Exception e) {
            log.error("Error notifying managers about request resolution: {}", e.getMessage(), e);
        }
    }

    /**
     * Reset password for waiter when assigned to restaurant and send credentials to manager
     * Note: This method should only be called when we've already verified the user WAS a WAITER (oldRole)
     */
    private void resetWaiterPasswordOnRestaurantAssignment(User waiter, UUID restaurantId, Locale userLocale, Role oldRole) {
        try {
            // Verify oldRole is WAITER (defensive check, but should already be verified by caller)
            if (oldRole == null || !"WAITER".equals(oldRole.getName())) {
                log.warn("resetWaiterPasswordOnRestaurantAssignment called for non-waiter user {}. Skipping password reset.", waiter.getUserCode());
                return;
            }
            
            // Check if manager exists for the restaurant
            Optional<Role> managerRoleOpt = roleRepository.findByName("MANAGER");
            if (managerRoleOpt.isEmpty()) {
                log.warn("MANAGER role not found in database. Cannot reset waiter password.");
                return;
            }
            
            UUID managerRoleId = managerRoleOpt.get().getId();
            List<User> managers = userRepository.findAllByRestaurantIdAndRoleIdAndIsDeletedFalse(
                    restaurantId, managerRoleId);
            
            if (managers.isEmpty()) {
                log.warn("No managers found for restaurant {}. Cannot send waiter credentials.", restaurantId);
                return;
            }
            
            // Generate new password
            String newPassword = PasswordGeneratorUtil.generatePassword(12);
            
            // Update waiter's password
            waiter.setPassword(passwordEncoder.encode(newPassword));
            waiter.setUpdatedAt(OffsetDateTime.now(ZoneOffset.UTC));
            userRepository.save(waiter);
            
            // Invalidate all sessions for the waiter
            loginAuditRepository.deleteByUser_Id(waiter.getId());
            
            log.info("Reset password for waiter {} assigned to restaurant {}", waiter.getUserCode(), restaurantId);
            
            // Collect unique manager emails (deduplicate)
            Set<String> uniqueManagerEmails = new LinkedHashSet<>();
            int duplicateCount = 0;
            for (User manager : managers) {
                if (manager.getEmail() != null && !manager.getEmail().trim().isEmpty()) {
                    boolean wasNew = uniqueManagerEmails.add(manager.getEmail());
                    if (!wasNew) {
                        duplicateCount++;
                        log.debug("Duplicate email detected for manager {}: {}", manager.getId(), manager.getEmail());
                    }
                }
            }
            
            if (duplicateCount > 0) {
                log.info("Found {} manager(s) for restaurant {}. Filtered {} duplicate email(s).", 
                        managers.size(), restaurantId, duplicateCount);
            }
            
            // Send email to all unique manager emails
            boolean emailSent = false;
            for (User manager : managers) {
                String managerEmail = manager.getEmail();
                if (managerEmail == null || managerEmail.trim().isEmpty()) {
                    continue;
                }
                try {
                    Locale managerLocale = resolvePreferredLocale(manager, userLocale);
                    String subject = messageUtil.getMessage("waiter.restaurant.assignment.email.subject", managerLocale);
                    String htmlBody = buildWaiterAssignmentEmailBody(waiter, newPassword, managerLocale);
                    emailSender.sendEmail(managerEmail, subject, htmlBody);
                    log.info("Sent waiter assignment credentials to manager: {}", managerEmail);
                    emailSent = true;
                } catch (Exception e) {
                    log.error("Failed to send waiter assignment email to manager {}: {}", 
                            managerEmail, e.getMessage(), e);
                }
            }
            
            // Fallback to default email if no manager emails were sent
            if (!emailSent) {
                String defaultEmail = emailProperties.getEmail();
                if (defaultEmail != null && !defaultEmail.trim().isEmpty()) {
                    try {
                        Locale waiterLocale = resolvePreferredLocale(waiter, userLocale);
                        String subject = messageUtil.getMessage("waiter.restaurant.assignment.email.subject", waiterLocale);
                        String htmlBody = buildWaiterAssignmentEmailBody(waiter, newPassword, waiterLocale);
                        emailSender.sendEmail(defaultEmail, subject, htmlBody);
                        log.info("Sent waiter assignment credentials to default email: {}", defaultEmail);
                    } catch (Exception e) {
                        log.error("Failed to send waiter assignment email to default email: {}", e.getMessage(), e);
                    }
                }
            }
            
        } catch (Exception e) {
            log.error("Failed to reset waiter password on restaurant assignment: {}", e.getMessage(), e);
            // Don't fail the user update if password reset fails
        }
    }

    /**
     * Build HTML email body for waiter restaurant assignment
     */
    private String buildWaiterAssignmentEmailBody(User waiter, String newPassword, Locale userLocale) {
        // Use an email-client friendly "card" layout (table-based) with inline styles.
        // Localization keys remain unchanged.
        String firstName = waiter.getFirstName() != null ? waiter.getFirstName() : "";
        String lastName = waiter.getLastName() != null ? waiter.getLastName() : "";
        boolean isJapanese = userLocale != null && "ja".equalsIgnoreCase(userLocale.getLanguage());
        String safeWaiterName = isJapanese
                ? ((lastName + " " + firstName).trim())
                : ((firstName + " " + lastName).trim());

        return ""
                + "<!DOCTYPE html>"
                + "<html>"
                + "<body style=\"margin:0;padding:16px 0;background:#f3f4f6;font-family:Arial,Helvetica,sans-serif;\">"
                + "<table role=\"presentation\" width=\"100%\" cellpadding=\"0\" cellspacing=\"0\" style=\"border-collapse:collapse;\">"
                + "<tr>"
                + "<td align=\"center\">"
                + "<table role=\"presentation\" width=\"600\" cellpadding=\"0\" cellspacing=\"0\" "
                + "style=\"max-width:600px;width:100%;background:#ffffff;border-radius:14px;"
                + "border:1px solid #e5e7eb;overflow:hidden;\">"
                + "<tr>"
                + "<td style=\"background:#2563eb;height:10px;\">&nbsp;</td>"
                + "</tr>"
                + "<tr>"
                + "<td style=\"padding:20px 24px 8px 24px;\">"
                + "<div style=\"font-size:18px;color:#111827;font-weight:700;line-height:24px;\">"
                + messageUtil.getMessage("user.registration.email.manager.greeting", userLocale)
                + "</div>"
                + "</td>"
                + "</tr>"
                + "<tr>"
                + "<td style=\"padding:0 24px 24px 24px;\">"
                + "<table role=\"presentation\" width=\"100%\" cellpadding=\"0\" cellspacing=\"0\" "
                + "style=\"background:#f9fafb;border:1px solid #e5e7eb;border-radius:12px;\">"
                + "<tr>"
                + "<td style=\"padding:14px 16px;font-size:14px;color:#111827;line-height:22px;"
                + "word-break:break-word;overflow-wrap:anywhere;\">"
                + "<p style=\"margin:0 0 12px;\">A waiter has been assigned to your restaurant:</p>"
                + "<table role=\"presentation\" width=\"100%\" cellpadding=\"0\" cellspacing=\"0\" style=\"border-collapse:collapse;\">"
                + "<tr>"
                + "<td style=\"padding:4px 0;color:#6b7280;font-size:13px;font-weight:700;\">Name:</td>"
                + "<td align=\"right\" style=\"padding:4px 0;color:#111827;font-size:13px;font-weight:700;\">"
                + (safeWaiterName.isEmpty() ? (waiter.getUserCode() != null ? waiter.getUserCode() : "") : safeWaiterName)
                + "</td>"
                + "</tr>"
                + "<tr>"
                + "<td style=\"padding:4px 0;color:#6b7280;font-size:13px;font-weight:700;\">User Code:</td>"
                + "<td align=\"right\" style=\"padding:4px 0;color:#111827;font-size:13px;font-weight:700;\">"
                + (waiter.getUserCode() != null ? waiter.getUserCode() : "")
                + "</td>"
                + "</tr>"
                + "<tr>"
                + "<td style=\"padding:4px 0;color:#6b7280;font-size:13px;font-weight:700;\">New Password:</td>"
                + "<td align=\"right\" style=\"padding:4px 0;color:#111827;font-size:13px;font-weight:700;font-family:Courier New,Courier,monospace;\">"
                + (newPassword != null ? newPassword : "")
                + "</td>"
                + "</tr>"
                + "</table>"
                + "<p style=\"margin:14px 0 0;\">Please share these credentials with the waiter.</p>"
                + "<p style=\"margin:14px 0 0;\">"
                + messageUtil.getMessage("user.registration.email.regards", userLocale) + "<br>"
                + messageUtil.getMessage("user.registration.email.team", userLocale) + "</p>"
                + "</td>"
                + "</tr>"
                + "</table>"
                + "</td>"
                + "</tr>"
                + "</table>"
                + "</td>"
                + "</tr>"
                + "</table>"
                + "</body>"
                + "</html>";
    }

    private void sendEmailChangeNotification(User user, String newEmail, Locale userLocale, String updaterRole) {
        if (newEmail == null || newEmail.isEmpty()) {
            return;
        }
        try {
            Locale emailLocale = resolvePreferredLocale(user, userLocale);
            String firstName = user.getFirstName() != null ? user.getFirstName() : "";
            String lastName = user.getLastName() != null ? user.getLastName() : "";
            boolean isJapanese = emailLocale != null && "ja".equalsIgnoreCase(emailLocale.getLanguage());
            String userName = isJapanese
                    ? ((lastName + " " + firstName).trim())
                    : ((firstName + " " + lastName).trim());
            String subject = messageUtil.getMessage("email.change.subject", emailLocale);
            String bodyKey = "MANAGER".equalsIgnoreCase(updaterRole)
                    ? "email.change.body.manager"
                    : "email.change.body";
            String body = messageUtil.getMessage(bodyKey, emailLocale, userName, newEmail);

            String htmlBody = "<html><body>"
                    + "<p>" + body + "</p>"
                    + "<p>" + messageUtil.getMessage("user.registration.email.regards", emailLocale) + "<br>"
                    + messageUtil.getMessage("user.registration.email.team", emailLocale) + "</p>"
                    + "</body></html>";

            emailSender.sendEmail(newEmail, subject, htmlBody);
            log.info("Email sent successfully to new email address: {} for user: {} by {}",
                    newEmail, user.getId(), updaterRole);
        } catch (Exception e) {
            log.error("Failed to send email to new email address {} for user {}: {}",
                    newEmail, user.getId(), e.getMessage(), e);
        }
    }

    private Locale resolvePreferredLocale(User user, Locale fallbackLocale) {
        if (user != null && user.getLanguageCode() != null && !user.getLanguageCode().trim().isEmpty()) {
            return Locale.forLanguageTag(user.getLanguageCode().trim());
        }
        return fallbackLocale != null ? fallbackLocale : Locale.ENGLISH;
    }

    private String resolveCashDrawerNameForUserService(
            com.gulfnet.shared_library.entity.CashDrawer drawer,
            Locale userLocale) {
        if (drawer == null) {
            return null;
        }
        List<CashDrawerTranslation> list =
                cashDrawerTranslationRepository.findAllByCashDrawer_IdOrderByLanguageCodeAsc(drawer.getId());
        String name = CashDrawerTranslationUtil.resolveName(list, userLocale != null ? userLocale : Locale.ENGLISH);
        return name.isEmpty() ? null : name;
    }

    /**
     * Helper method to get shift name from translations
     */
    private String getShiftNameFromShift(Shift shift, String preferredLocale) {
        if (shift == null) {
            return null;
        }
        
        List<ShiftTranslation> translations = shiftTranslationRepository.findAllByShiftId(shift.getId());
        if (translations == null || translations.isEmpty()) {
            return "";
        }
        
        String defaultLanguage = localizationProperties.getLanguages() != null && !localizationProperties.getLanguages().isEmpty()
                ? localizationProperties.getLanguages().get(0) : "en";
        
        Optional<ShiftTranslation> translation = TranslationUtils.pickPreferredOrFromList(
                translations,
                preferredLocale,
                localizationProperties.getLanguages(),
                ShiftTranslation::getLanguageCode
        );
        
        if (translation.isPresent()) {
            return translation.get().getName();
        }
        
        // Fallback to default language
        if (defaultLanguage != null) {
            Optional<ShiftTranslation> defaultTranslation = translations.stream()
                    .filter(t -> defaultLanguage.equalsIgnoreCase(t.getLanguageCode()))
                    .findFirst();
            if (defaultTranslation.isPresent()) {
                return defaultTranslation.get().getName();
            }
        }
        
        // Last resort: return first available translation
        return translations.get(0).getName();
    }


    /**
     * Same rule as restaurant-management notifications: prefer recipient {@link User#getLanguageCode()},
     * then request locale, then English.
     */
    private static Locale localeForRecipient(User recipient, Locale requestLocale) {
        if (recipient != null && recipient.getLanguageCode() != null && !recipient.getLanguageCode().trim().isEmpty()) {
            return Locale.forLanguageTag(recipient.getLanguageCode().trim());
        }
        if (requestLocale != null) {
            return requestLocale;
        }
        return Locale.ENGLISH;
    }

    private String resolveRestaurantNameForManagerNotification(Restaurant restaurant, Locale loc) {
        String restaurantName = restaurant.getRestaurantCode() != null ? restaurant.getRestaurantCode() : "";
        try {
            if (restaurant.getTranslations() != null && !restaurant.getTranslations().isEmpty()) {
                String language = loc != null ? loc.getLanguage() : "en";
                restaurantName = restaurant.getTranslations().stream()
                        .filter(t -> t.getLanguageCode() != null && t.getLanguageCode().startsWith(language))
                        .findFirst()
                        .map(com.gulfnet.shared_library.entity.RestaurantTranslation::getName)
                        .orElse(restaurant.getTranslations().get(0).getName());
            }
        } catch (Exception e) {
            log.debug("Could not access restaurant translations: {}", e.getMessage());
        }
        if (restaurantName == null || restaurantName.trim().isEmpty()) {
            restaurantName = restaurant.getRestaurantCode() != null ? restaurant.getRestaurantCode() : "Restaurant";
        }
        return restaurantName;
    }

    private UUID parseUserIdOrThrow(String userId, Locale userLocale) {
        try {
            return UUID.fromString(userId);
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    messageUtil.getMessage("user.not.found", userLocale));
        }
    }

    private User loadApproverOrThrow(UUID approverId, Locale userLocale) {
        return userRepository.findById(approverId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        messageUtil.getMessage("user.not.found", userLocale)));
    }

    private static String serializeBodyArgs(Object... args) {
        if (args == null || args.length == 0) return null;
        try {
            return new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(
                    java.util.Arrays.stream(args).map(a -> a != null ? a.toString() : "").toArray(String[]::new));
        } catch (Exception e) {
            return null;
        }
    }

    /** Serializes request metadata for notification additional_data (for navigation to request details). */
    private static String serializeRequestData(java.util.Map<String, String> data) {
        if (data == null || data.isEmpty()) return null;
        try {
            return new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(data);
        } catch (Exception e) {
            return null;
        }
    }

}
