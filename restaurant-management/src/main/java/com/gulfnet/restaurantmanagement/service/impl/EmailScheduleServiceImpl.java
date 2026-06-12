package com.gulfnet.restaurantmanagement.service.impl;

import com.gulfnet.restaurantmanagement.service.EmailScheduleService;
import com.gulfnet.restaurantmanagement.service.ReportsService;
import com.gulfnet.restaurantmanagement.service.ScheduleManagerService;
import com.gulfnet.restaurantmanagement.util.MessageUtil;
import com.gulfnet.shared_library.entity.EmailSchedule;
import com.gulfnet.shared_library.entity.EmailScheduleTranslation;
import com.gulfnet.shared_library.entity.Restaurant;
import com.gulfnet.shared_library.entity.RestaurantGroup;
import com.gulfnet.shared_library.entity.User;
import com.gulfnet.shared_library.enums.ReportType;
import com.gulfnet.shared_library.enums.ScheduleFrequency;
import com.gulfnet.shared_library.model.request.CreateEmailScheduleRequest;
import com.gulfnet.shared_library.model.response.dto.EmailScheduleListResponse;
import com.gulfnet.shared_library.model.response.dto.EmailScheduleResponse;
import com.gulfnet.shared_library.model.response.dto.EmailScheduleTranslationDto;
import com.gulfnet.shared_library.model.response.dto.PaginationMetaData;
import com.gulfnet.shared_library.model.response.dto.ResponseDto;
import com.gulfnet.shared_library.repository.EmailScheduleRepository;
import com.gulfnet.shared_library.repository.EmailScheduleTranslationRepository;
import com.gulfnet.shared_library.repository.RestaurantGroupRepository;
import com.gulfnet.shared_library.repository.RestaurantRepository;
import com.gulfnet.shared_library.repository.UserRepository;
import com.gulfnet.shared_library.util.EmailSender;
import com.gulfnet.shared_library.util.LocaleSortUtil;
import com.gulfnet.restaurantmanagement.config.LocalizationProperties;
import lombok.extern.slf4j.Slf4j;
import org.quartz.SchedulerException;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.server.ResponseStatusException;

import java.time.OffsetDateTime;
import java.time.OffsetTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
public class EmailScheduleServiceImpl implements EmailScheduleService {

    // Role names
    private static final String ROLE_MANAGER = "MANAGER";
    private static final String ROLE_HQ_ADMIN = "HQ_ADMIN";

    // Quartz job key prefix
    private static final String QUARTZ_JOB_KEY_PREFIX = "email-schedule-";

    // Sort field names
    private static final String SORT_FIELD_SCHEDULE_NAME = "scheduleName";
    private static final String SORT_FIELD_NAME = "name";
    private static final String SORT_FIELD_CREATED_AT = "createdAt";
    private static final String SORT_DIRECTION_ASC = "ASC";

    // Message keys
    private static final String MSG_USER_NOT_FOUND = "user.not.found";
    private static final String MSG_RESTAURANT_NOT_FOUND = "restaurant.not.found";
    private static final String MSG_RESTAURANT_GROUP_NOT_FOUND = "restaurant.group.not.found";
    private static final String MSG_EMAIL_SCHEDULE_UNAUTHORIZED = "email.schedule.unauthorized";
    private static final String MSG_EMAIL_SCHEDULE_NOT_FOUND = "email.schedule.not.found";
    private static final String MSG_EMAIL_SCHEDULE_TRANSLATION_REQUIRED = "email.schedule.translation.required";
    private static final String MSG_EMAIL_SCHEDULE_CREATOR_EMAIL_REQUIRED = "employee.profile.email.required";
    private static final String MSG_EMAIL_SCHEDULE_RESTAURANT_REQUIRED = "email.schedule.restaurant.required";
    private static final String MSG_EMAIL_SCHEDULE_UNAUTHORIZED_RESTAURANT = "email.schedule.unauthorized.restaurant";
    private static final String MSG_EMAIL_SCHEDULE_QUARTZ_JOB_CREATION_FAILED = "email.schedule.quartz.job.creation.failed";
    private static final String MSG_EMAIL_SCHEDULE_CREATED_SUCCESS = "email.schedule.created.success";
    private static final String MSG_EMAIL_SCHEDULE_DELETED_SUCCESS = "email.schedule.deleted.success";
    private static final String MSG_EMAIL_SCHEDULE_LIST_SUCCESS = "email.schedule.list.success";
    private static final String MSG_EMAIL_SCHEDULE_DAY_REQUIRED = "email.schedule.day.required";
    private static final String MSG_EMAIL_SCHEDULE_DAY_INVALID_WEEKLY = "email.schedule.day.invalid.weekly";
    private static final String MSG_RESTAURANT_NOT_IN_GROUP = "restaurant.not.in.group";

    private final EmailScheduleRepository emailScheduleRepository;
    private final UserRepository userRepository;
    private final RestaurantRepository restaurantRepository;
    private final RestaurantGroupRepository restaurantGroupRepository;
    private final ScheduleManagerService scheduleManagerService;
    private final MessageUtil messageUtil;
    private final EmailScheduleTranslationRepository emailScheduleTranslationRepository;
    private final LocalizationProperties localizationProperties;

    /**
     * Constructs an EmailScheduleServiceImpl with all required dependencies.
     * Initializes repositories, services, and configuration properties for email schedule management.
     *
     * @param emailScheduleRepository repository for email schedule entities
     * @param userRepository repository for user entities
     * @param restaurantRepository repository for restaurant entities
     * @param restaurantGroupRepository repository for restaurant group entities
     * @param scheduleManagerService service for managing Quartz scheduler jobs
     * @param messageUtil utility for localized message retrieval
     * @param reportsService service for generating reports (lazy-loaded to avoid circular dependencies)
     * @param emailSender service for sending emails
     * @param emailScheduleTranslationRepository repository for email schedule translations
     * @param localizationProperties configuration properties for supported languages
     */
    public EmailScheduleServiceImpl(
            EmailScheduleRepository emailScheduleRepository,
            UserRepository userRepository,
            RestaurantRepository restaurantRepository,
            RestaurantGroupRepository restaurantGroupRepository,
            ScheduleManagerService scheduleManagerService,
            MessageUtil messageUtil,
            @Lazy ReportsService reportsService,
            EmailSender emailSender,
            EmailScheduleTranslationRepository emailScheduleTranslationRepository,
            LocalizationProperties localizationProperties) {
        this.emailScheduleRepository = emailScheduleRepository;
        this.userRepository = userRepository;
        this.restaurantRepository = restaurantRepository;
        this.restaurantGroupRepository = restaurantGroupRepository;
        this.scheduleManagerService = scheduleManagerService;
        this.messageUtil = messageUtil;
        this.emailScheduleTranslationRepository = emailScheduleTranslationRepository;
        this.localizationProperties = localizationProperties;
    }

    /**
     * Creates a new email schedule with translations and Quartz job.
     * Validates user permissions, translations, frequency/day settings, and restaurant access.
     * Creates the schedule entity, saves translations, and registers a Quartz job for execution.
     *
     * @param request     the email schedule creation request with all schedule details
     * @param creatorId   the ID of the user creating the schedule
     * @param creatorRole the role of the user creating the schedule (MANAGER or HQ_ADMIN)
     * @param locale      the locale code for localized error messages
     * @return ResponseDto containing the created email schedule response
     * @throws ResponseStatusException if validation fails, user not found, or Quartz job creation fails
     */
    @Override
    @Transactional
    public ResponseDto<EmailScheduleResponse> createSchedule(
            CreateEmailScheduleRequest request,
            String creatorId,
            String creatorRole,
            String locale) {
        Locale userLocale = LocaleContextHolder.getLocale();
        log.info("Creating email schedule: {} by user: {} with role: {}", request.getScheduleName(), creatorId, creatorRole);

        validateUserPermissions(creatorRole, userLocale);
        validateTranslations(request.getTranslations(), userLocale);
        User creator = getAndValidateCreator(creatorId, userLocale);
        validateFrequencyAndDay(request.getFrequency(), request.getScheduledDay(), userLocale);
        
        Integer scheduledDay = normalizeScheduledDay(request.getFrequency(), request.getScheduledDay());
        RestaurantAccess restaurantAccess = validateAndGetRestaurantAccess(request, creator, creatorRole, userLocale);
        NormalizedDateTime normalizedDateTime = normalizeDateTime(request);
        String baseScheduleName = deriveScheduleName(request.getTranslations(), request.getScheduleName());

        EmailSchedule emailSchedule = createAndSaveEmailSchedule(
                request, creator, restaurantAccess, normalizedDateTime, scheduledDay, baseScheduleName);
        
        saveTranslations(request.getTranslations(), emailSchedule, userLocale);
        createQuartzJobWithCleanup(emailSchedule, userLocale);

        EmailScheduleResponse response = buildEmailScheduleResponse(emailSchedule, userLocale.getLanguage());
        return ResponseDto.<EmailScheduleResponse>builder()
                .message(messageUtil.getMessage(MSG_EMAIL_SCHEDULE_CREATED_SUCCESS, userLocale))
                .data(response)
                .build();
    }

    /**
     * Deletes an email schedule and its associated Quartz job.
     * Managers can only delete their own schedules or schedules for their restaurant.
     * HQ_ADMIN can delete any schedule. Deletes translations before deleting the schedule entity.
     *
     * @param scheduleId  the UUID of the schedule to delete
     * @param deleterId  the ID of the user deleting the schedule
     * @param deleterRole the role of the user deleting the schedule (MANAGER or HQ_ADMIN)
     * @param locale      the locale code for localized error messages
     * @return ResponseDto with success message
     * @throws ResponseStatusException if schedule not found, user not found, or unauthorized access
     */
    @Override
    @Transactional
    public ResponseDto<Void> deleteSchedule(
            UUID scheduleId,
            String deleterId,
            String deleterRole,
            String locale) {
        Locale userLocale = LocaleContextHolder.getLocale();

        log.info("Deleting email schedule: {} by user: {} with role: {}", scheduleId, deleterId, deleterRole);

        // Validate user permissions
        validateUserPermissions(deleterRole, userLocale);

        // Get schedule
        EmailSchedule schedule = emailScheduleRepository.findById(scheduleId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        messageUtil.getMessage(MSG_EMAIL_SCHEDULE_NOT_FOUND, userLocale)));

        // Validate user can delete this schedule
        User deleter = userRepository.findById(UUID.fromString(deleterId))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        messageUtil.getMessage(MSG_USER_NOT_FOUND, userLocale)));

        // Manager can only delete their own schedules or schedules for their restaurant
        if (ROLE_MANAGER.equalsIgnoreCase(deleterRole) &&
                !schedule.getCreatedBy().getId().equals(deleter.getId()) &&
                (schedule.getRestaurant() == null || !schedule.getRestaurant().getId().equals(deleter.getRestaurantId()))) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    messageUtil.getMessage(MSG_EMAIL_SCHEDULE_UNAUTHORIZED, userLocale));
        }
        // HQ_ADMIN can delete any schedule

        deleteScheduleEntityAndQuartzJob(schedule);
        log.info("Successfully deleted email schedule: {}", scheduleId);

        return ResponseDto.<Void>builder()
                .message(messageUtil.getMessage(MSG_EMAIL_SCHEDULE_DELETED_SUCCESS, userLocale))
                .build();
    }

    /**
     * Removes Quartz job, translations, and DB row for a schedule (no auth checks).
     */
    private void deleteScheduleEntityAndQuartzJob(EmailSchedule schedule) {
        UUID scheduleId = schedule.getId();
        // Important: don't delete Quartz jobs while the DB transaction is open.
        // If Quartz (or its DB locks) blocks, we'd hold a JDBC connection "idle in transaction" and exhaust Hikari.
        String quartzJobKey = schedule.getQuartzJobKey();

        List<EmailScheduleTranslation> translations =
                emailScheduleTranslationRepository.findAllByScheduleId(scheduleId);
        if (translations != null && !translations.isEmpty()) {
            emailScheduleTranslationRepository.deleteAll(translations);
            log.info("Deleted {} translations for schedule: {}", translations.size(), scheduleId);
        }

        emailScheduleRepository.delete(schedule);

        runAfterCommit(() -> deleteQuartzJobBestEffort(scheduleId, quartzJobKey));
    }

    private void deleteQuartzJobBestEffort(UUID scheduleId, String quartzJobKey) {
        boolean jobDeleted = false;
        if (quartzJobKey != null && !quartzJobKey.isEmpty()) {
            try {
                scheduleManagerService.deleteQuartzJob(quartzJobKey);
                jobDeleted = true;
                log.info("Successfully deleted Quartz job using stored key: {}", quartzJobKey);
            } catch (SchedulerException e) {
                log.warn("Failed to delete Quartz job using stored key: {}, trying fallback method", quartzJobKey, e);
            }
        }
        if (!jobDeleted) {
            try {
                scheduleManagerService.deleteQuartzJobByScheduleId(scheduleId);
                log.info("Successfully deleted Quartz job using scheduleId fallback: {}", scheduleId);
            } catch (SchedulerException e) {
                log.error("Failed to delete Quartz job for schedule: {} using fallback method", scheduleId, e);
            }
        }
    }

    private void runAfterCommit(Runnable r) {
        if (r == null) {
            return;
        }
        try {
            if (!TransactionSynchronizationManager.isActualTransactionActive()) {
                r.run();
                return;
            }
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    try {
                        r.run();
                    } catch (Exception e) {
                        log.warn("afterCommit action failed: {}", e.getMessage(), e);
                    }
                }
            });
        } catch (Exception e) {
            log.warn("Failed to register afterCommit action: {}", e.getMessage(), e);
            try {
                r.run();
            } catch (Exception ex) {
                log.warn("Fallback immediate afterCommit action failed: {}", ex.getMessage(), ex);
            }
        }
    }

    @Override
    @Transactional
    public void deleteSchedulesForUserAndRestaurant(UUID userId, UUID restaurantId) {
        if (userId == null || restaurantId == null) {
            return;
        }
        deleteScheduleList(
                emailScheduleRepository.findByCreatedBy_IdAndRestaurant_Id(userId, restaurantId),
                "user " + userId + " left restaurant " + restaurantId);
    }

    @Override
    @Transactional
    public void deleteSchedulesForUser(UUID userId) {
        if (userId == null) {
            return;
        }
        deleteScheduleList(
                emailScheduleRepository.findByCreatedBy_Id(userId),
                "user " + userId + " was deleted");
    }

    private void deleteScheduleList(List<EmailSchedule> schedules, String reason) {
        if (schedules == null || schedules.isEmpty()) {
            log.debug("No email schedules to remove ({})", reason);
            return;
        }
        for (EmailSchedule schedule : schedules) {
            try {
                deleteScheduleEntityAndQuartzJob(schedule);
                log.info("Removed email schedule {} ({})", schedule.getId(), reason);
            } catch (Exception e) {
                log.error("Failed to delete email schedule {} ({}): {}", schedule.getId(), reason, e.getMessage(), e);
            }
        }
    }

    /**
     * Retrieves a paginated and filterable list of email schedules.
     * Managers can only see their own schedules for their restaurant.
     * HQ_ADMIN can filter by restaurant, restaurant group, or see all schedules created by them.
     * Supports sorting by schedule name (with locale-aware sorting) or creation date.
     *
     * @param requesterId       the ID of the user requesting the schedules
     * @param requesterRole     the role of the requester (MANAGER or HQ_ADMIN)
     * @param restaurantId      optional filter by restaurant ID (HQ_ADMIN only)
     * @param restaurantGroupId optional filter by restaurant group ID (HQ_ADMIN only)
     * @param reportType        optional filter by report type
     * @param frequency         optional filter by schedule frequency
     * @param sortBy            field to sort by (scheduleName or createdAt)
     * @param sortDirection     sort direction (ASC or DESC)
     * @param page              page number for pagination
     * @param size              page size for pagination
     * @param locale            locale code for localized responses and sorting
     * @return ResponseDto containing paginated list of email schedules
     * @throws ResponseStatusException if user not found, restaurant not in group, or validation fails
     */
    @Override
    @Transactional(readOnly = true)
    public ResponseDto<EmailScheduleListResponse> getAllSchedules(
            String requesterId,
            String requesterRole,
            UUID restaurantId,
            UUID restaurantGroupId,
            ReportType reportType,
            ScheduleFrequency frequency,
            String sortBy,
            String sortDirection,
            Integer page,
            Integer size,
            String locale) {
        Locale userLocale = LocaleContextHolder.getLocale();

        log.info("Getting email schedules for user: {} with role: {}, restaurantId: {}, restaurantGroupId: {}, reportType: {}, frequency: {}, sortBy: {}, sortDirection: {}", 
                requesterId, requesterRole, restaurantId, restaurantGroupId, reportType, frequency, sortBy, sortDirection);

        // Validate user permissions
        validateUserPermissions(requesterRole, userLocale);

        User requester = userRepository.findById(UUID.fromString(requesterId))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        messageUtil.getMessage(MSG_USER_NOT_FOUND, userLocale)));

        // Determine filter parameters
        // Always filter by createdBy to show only schedules created by the calling user
        UUID filterRestaurantId = null;
        UUID filterRestaurantGroupId = null;
        UUID filterCreatedById = requester.getId(); // Always filter by the calling user

        if (ROLE_MANAGER.equalsIgnoreCase(requesterRole)) {
            // Manager can only see their own schedules for their restaurant
            filterRestaurantId = requester.getRestaurantId();
        } else if (ROLE_HQ_ADMIN.equalsIgnoreCase(requesterRole)) {
            // HQ_ADMIN filtering logic:
            // - If both restaurantId and restaurantGroupId are provided: validate restaurant belongs to group, then filter by restaurantId
            // - If only restaurantGroupId is provided: filter by restaurantGroupId
            // - If only restaurantId is provided: filter by restaurantId
            // - If neither is provided: show all schedules created by the user
            if (restaurantId != null && restaurantGroupId != null) {
                // Both provided: validate that restaurant belongs to the restaurant group
                Restaurant restaurant = restaurantRepository.findById(restaurantId)
                        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                                messageUtil.getMessage(MSG_RESTAURANT_NOT_FOUND, userLocale)));
                
                if (restaurant.getRestaurantGroup() == null || 
                    !restaurant.getRestaurantGroup().getId().equals(restaurantGroupId)) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                            messageUtil.getMessage(MSG_RESTAURANT_NOT_IN_GROUP, userLocale));
                }
                
                // Both provided: filter by BOTH restaurantId AND restaurantGroupId to ensure exact match
                // This ensures we only get schedules for the specific restaurant in that group
                filterRestaurantId = restaurantId;
                filterRestaurantGroupId = restaurantGroupId; // Keep restaurantGroupId filter to ensure exact match
            } else if (restaurantGroupId != null) {
                // Only restaurantGroupId provided: filter by restaurantGroupId
                filterRestaurantGroupId = restaurantGroupId;
            } else if (restaurantId != null) {
                // Only restaurantId provided: filter by restaurantId
                filterRestaurantId = restaurantId;
            }
            // If neither is provided, both filters remain null (show all schedules created by user)
        }

        // Fetch all schedules without pagination (for in-memory sorting like other listing APIs)
        Pageable pageable = PageRequest.of(0, Integer.MAX_VALUE); // Fetch all for sorting
        
        log.debug("Filtering schedules with restaurantId: {}, restaurantGroupId: {}, reportType: {}, frequency: {}, createdById: {}", 
                filterRestaurantId, filterRestaurantGroupId, reportType, frequency, filterCreatedById);
        
        Page<EmailSchedule> schedulePage = emailScheduleRepository.findAllActiveSchedules(
                filterRestaurantId, filterRestaurantGroupId, reportType, frequency, filterCreatedById, pageable);
        
        log.debug("Found {} schedules matching filters", schedulePage.getTotalElements());

        // Convert to response DTOs
        String localeString = userLocale.getLanguage();
        List<EmailScheduleResponse> scheduleResponses = schedulePage.getContent().stream()
                .map(schedule -> buildEmailScheduleResponse(schedule, localeString))
                .collect(Collectors.toList());

        // Apply sorting using shared library method (following other listing APIs pattern)
        // Only support sorting by scheduleName and createdAt
        String sortField = (sortBy != null && !sortBy.isBlank()) ? sortBy : SORT_FIELD_SCHEDULE_NAME;
        Sort.Direction sortDir = (sortDirection != null && sortDirection.equalsIgnoreCase(SORT_DIRECTION_ASC)) 
                ? Sort.Direction.ASC 
                : Sort.Direction.DESC;
        
        if (SORT_FIELD_CREATED_AT.equalsIgnoreCase(sortField)) {
            Comparator<EmailScheduleResponse> comp = Comparator.comparing(
                    EmailScheduleResponse::getCreatedAt,
                    Comparator.nullsLast(Comparator.naturalOrder()));
            if (sortDir == Sort.Direction.DESC) comp = comp.reversed();
            scheduleResponses.sort(comp);
        } else {
            // scheduleName, "name", or any unknown field: sort by display name via LocaleSortUtil
            LocaleContextHolder.setLocale(userLocale);
            LocaleSortUtil.sortName(scheduleResponses, SORT_FIELD_NAME, sortDir);
        }

        // Apply pagination to sorted results (following other listing APIs pattern)
        boolean noPaging = (page == null || size == null || page <= 0 || size <= 0);
        int pageNumber = (page != null ? page : 1) - 1;
        if (pageNumber < 0) pageNumber = 0;
        int pageSize = size != null ? size : Integer.MAX_VALUE;
        if (pageSize < 1) pageSize = Integer.MAX_VALUE;
        
        long totalRecords = scheduleResponses.size();
        int fromIndex = pageNumber * pageSize;
        int toIndex = Math.min(fromIndex + pageSize, scheduleResponses.size());
        List<EmailScheduleResponse> paginatedResponses;
        if (fromIndex >= scheduleResponses.size()) {
            paginatedResponses = List.of();
        } else {
            paginatedResponses = scheduleResponses.subList(fromIndex, toIndex);
        }

        // Build pagination metadata (always create, following standard listing API pattern)
        int totalPages = noPaging ? 1 : (int) Math.ceil((double) totalRecords / pageSize);
        int actualPage = noPaging ? 1 : (pageNumber + 1);
        int actualSize = noPaging ? (int) totalRecords : pageSize;

        PaginationMetaData paginationMetaData = PaginationMetaData.builder()
                .page(actualPage)
                .size(actualSize)
                .totalRecords(totalRecords)
                .totalPages(totalPages)
                .build();


        // Build response following standard listing API structure (same as items listing)
        EmailScheduleListResponse listResponse = EmailScheduleListResponse.builder()
                .schedules(paginatedResponses)
                .count((long) paginatedResponses.size())
                .total(totalRecords)
                .metaData(paginationMetaData)
                .build();


        return ResponseDto.<EmailScheduleListResponse>builder()
                .message(messageUtil.getMessage(MSG_EMAIL_SCHEDULE_LIST_SUCCESS, userLocale))
                .data(listResponse)
                .build();
    }

    /**
     * Synchronizes all active email schedules with the Quartz scheduler.
     * Creates missing Quartz jobs for schedules that don't have a job key.
     * This method is typically called during application startup or maintenance.
     */
    @Override
    @Transactional
    public void syncAllSchedules() {
        log.info("Syncing all email schedules with Quartz scheduler");

        List<EmailSchedule> activeSchedules = emailScheduleRepository.findAllByIsActiveTrue();

        for (EmailSchedule schedule : activeSchedules) {
            try {
                // Check if Quartz job exists
                if (schedule.getQuartzJobKey() == null || schedule.getQuartzJobKey().isEmpty()) {
                    // Create missing Quartz job
                    scheduleManagerService.createQuartzJob(schedule);
                    String jobKey = QUARTZ_JOB_KEY_PREFIX + schedule.getId().toString();
                    schedule.setQuartzJobKey(jobKey);
                    emailScheduleRepository.save(schedule);
                    log.info("Created missing Quartz job for schedule: {}", schedule.getId());
                }
            } catch (SchedulerException e) {
                log.error("Failed to sync schedule: {}", schedule.getId(), e);
            }
        }

        log.info("Completed syncing {} email schedules", activeSchedules.size());
    }

    // Helper methods

    private void validateTranslations(List<EmailScheduleTranslationDto> translations, Locale locale) {
        boolean hasValidTranslation = translations != null &&
                translations.stream().anyMatch(t -> t.getName() != null && !t.getName().trim().isEmpty());
        if (!hasValidTranslation) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    messageUtil.getMessage(MSG_EMAIL_SCHEDULE_TRANSLATION_REQUIRED, locale));
        }
    }

    private User getAndValidateCreator(String creatorId, Locale locale) {
        User creator = userRepository.findById(UUID.fromString(creatorId))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        messageUtil.getMessage(MSG_USER_NOT_FOUND, locale)));
        if (creator.getEmail() == null || creator.getEmail().trim().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    messageUtil.getMessage(MSG_EMAIL_SCHEDULE_CREATOR_EMAIL_REQUIRED, locale));
        }
        return creator;
    }

    private Integer normalizeScheduledDay(ScheduleFrequency frequency, Integer scheduledDay) {
        if (frequency == ScheduleFrequency.MONTHLY) {
            return 1;
        } else if (frequency == ScheduleFrequency.DAILY) {
            return null; // DAILY frequency doesn't need scheduledDay
        } else {
            // WEEKLY frequency - scheduledDay should be provided (validated earlier)
            return scheduledDay;
        }
    }

    private static class RestaurantAccess {
        final Restaurant restaurant;
        final RestaurantGroup restaurantGroup;

        RestaurantAccess(Restaurant restaurant, RestaurantGroup restaurantGroup) {
            this.restaurant = restaurant;
            this.restaurantGroup = restaurantGroup;
        }
    }

    /**
     * Validates and retrieves restaurant access information based on user role and request.
     * Managers must provide a restaurant ID and it must match their assigned restaurant.
     * HQ_ADMIN can optionally provide restaurant or restaurant group IDs.
     *
     * @param request     the email schedule creation request
     * @param creator     the user creating the schedule
     * @param creatorRole the role of the creator (MANAGER or HQ_ADMIN)
     * @param locale      locale for localized error messages
     * @return RestaurantAccess containing restaurant and restaurant group (may be null)
     * @throws ResponseStatusException if restaurant not found, unauthorized access, or validation fails
     */
    private RestaurantAccess validateAndGetRestaurantAccess(
            CreateEmailScheduleRequest request, User creator, String creatorRole, Locale locale) {
        Restaurant restaurant = null;
        RestaurantGroup restaurantGroup = null;

        if (ROLE_MANAGER.equalsIgnoreCase(creatorRole)) {
            if (request.getRestaurantId() == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        messageUtil.getMessage(MSG_EMAIL_SCHEDULE_RESTAURANT_REQUIRED, locale));
            }
            restaurant = restaurantRepository.findById(request.getRestaurantId())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                            messageUtil.getMessage(MSG_RESTAURANT_NOT_FOUND, locale)));
            if (creator.getRestaurantId() == null || !creator.getRestaurantId().equals(request.getRestaurantId())) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                        messageUtil.getMessage(MSG_EMAIL_SCHEDULE_UNAUTHORIZED_RESTAURANT, locale));
            }
        } else if (ROLE_HQ_ADMIN.equalsIgnoreCase(creatorRole)) {
            if (request.getRestaurantId() != null) {
                restaurant = restaurantRepository.findById(request.getRestaurantId())
                        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                                messageUtil.getMessage(MSG_RESTAURANT_NOT_FOUND, locale)));
            }
            if (request.getRestaurantGroupId() != null) {
                restaurantGroup = restaurantGroupRepository.findById(request.getRestaurantGroupId())
                        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                                messageUtil.getMessage(MSG_RESTAURANT_GROUP_NOT_FOUND, locale)));
            }
        }
        return new RestaurantAccess(restaurant, restaurantGroup);
    }

    private static class NormalizedDateTime {
        final OffsetDateTime startDate;
        final OffsetDateTime endDate;
        final OffsetTime scheduledTime;

        NormalizedDateTime(OffsetDateTime startDate, OffsetDateTime endDate, OffsetTime scheduledTime) {
            this.startDate = startDate;
            this.endDate = endDate;
            this.scheduledTime = scheduledTime;
        }
    }

    private NormalizedDateTime normalizeDateTime(CreateEmailScheduleRequest request) {
        OffsetDateTime normalizedStartDate = request.getStartDate() != null
                ? request.getStartDate().withOffsetSameInstant(ZoneOffset.UTC) : null;
        OffsetDateTime normalizedEndDate = request.getEndDate() != null
                ? request.getEndDate().withOffsetSameInstant(ZoneOffset.UTC) : null;
        OffsetTime scheduledTimeUtc = request.getScheduledTime() != null
                ? request.getScheduledTime().withOffsetSameInstant(ZoneOffset.UTC)
                : OffsetTime.of(0, 0, 0, 0, ZoneOffset.UTC);
        return new NormalizedDateTime(normalizedStartDate, normalizedEndDate, scheduledTimeUtc);
    }

    private String deriveScheduleName(List<EmailScheduleTranslationDto> translations, String defaultName) {
        if (translations == null || translations.isEmpty()) {
            return defaultName;
        }
        return translations.stream()
                .filter(t -> t != null && t.getName() != null && !t.getName().trim().isEmpty())
                .findFirst()
                .map(EmailScheduleTranslationDto::getName)
                .orElse(defaultName);
    }

    /**
     * Creates and saves an email schedule entity with all required fields.
     * Calculates the next execution time based on schedule frequency and time settings.
     *
     * @param request              the email schedule creation request
     * @param creator              the user creating the schedule
     * @param restaurantAccess     restaurant and restaurant group access information
     * @param normalizedDateTime   normalized date/time values in UTC
     * @param scheduledDay         the scheduled day (1-7 for weekly, 1 for monthly)
     * @param baseScheduleName     the base name for the schedule
     * @return the created and saved EmailSchedule entity
     */
    private EmailSchedule createAndSaveEmailSchedule(
            CreateEmailScheduleRequest request,
            User creator,
            RestaurantAccess restaurantAccess,
            NormalizedDateTime normalizedDateTime,
            Integer scheduledDay,
            String baseScheduleName) {
        
        EmailSchedule tempSchedule = EmailSchedule.builder()
                .frequency(request.getFrequency())
                .scheduledTime(normalizedDateTime.scheduledTime)
                .scheduledDay(scheduledDay)
                .build();

        OffsetDateTime nextExecutionAt = scheduleManagerService.calculateNextExecutionTime(tempSchedule);

        EmailSchedule emailSchedule = EmailSchedule.builder()
                .scheduleName(baseScheduleName)
                .reportType(request.getReportType())
                .frequency(request.getFrequency())
                .scheduledTime(normalizedDateTime.scheduledTime)
                .scheduledDay(scheduledDay)
                .restaurant(restaurantAccess.restaurant)
                .restaurantGroup(restaurantAccess.restaurantGroup)
                .recipientEmail(creator.getEmail())
                .isActive(true)
                .createdBy(creator)
                .nextExecutionAt(nextExecutionAt)
                .period(request.getPeriod())
                .startDate(normalizedDateTime.startDate)
                .endDate(normalizedDateTime.endDate)
                .build();

        emailSchedule = emailScheduleRepository.save(emailSchedule);
        emailScheduleRepository.flush();
        return emailSchedule;
    }

    /**
     * Saves email schedule translations to the database.
     * Validates that translations have non-empty names and language codes.
     * If translation saving fails, deletes the schedule entity to maintain data consistency.
     *
     * @param translations  list of translation DTOs to save
     * @param emailSchedule the email schedule entity to associate translations with
     * @param locale        locale for localized error messages
     * @throws ResponseStatusException if translation saving fails
     */
    private void saveTranslations(
            List<EmailScheduleTranslationDto> translations, EmailSchedule emailSchedule, Locale locale) {
        if (translations == null || translations.isEmpty()) {
            return;
        }

        List<EmailScheduleTranslation> translationEntities = new ArrayList<>();
        for (EmailScheduleTranslationDto translationDto : translations) {
            if (translationDto.getName() != null && !translationDto.getName().trim().isEmpty() &&
                    translationDto.getLanguageCode() != null && !translationDto.getLanguageCode().trim().isEmpty()) {
                EmailScheduleTranslation translation = EmailScheduleTranslation.builder()
                        .name(translationDto.getName().trim())
                        .languageCode(translationDto.getLanguageCode().trim())
                        .emailSchedule(emailSchedule)
                        .build();
                translationEntities.add(translation);
            } else {
                log.warn("Skipping translation with missing name or languageCode for schedule: {}", emailSchedule.getId());
            }
        }

        if (!translationEntities.isEmpty()) {
            try {
                emailScheduleTranslationRepository.saveAll(translationEntities);
                log.info("Saved {} translations for email schedule: {}", translationEntities.size(), emailSchedule.getId());
            } catch (Exception e) {
                log.error("Failed to save translations for email schedule: {}. Error: {}", emailSchedule.getId(), e.getMessage(), e);
                emailScheduleRepository.delete(emailSchedule);
                throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                        "Failed to save email schedule translations: " + e.getMessage());
            }
        }
    }

    /**
     * Creates a Quartz job for the email schedule and stores the job key.
     * If job creation fails, cleans up the schedule and translations to maintain data consistency.
     *
     * @param emailSchedule the email schedule to create a Quartz job for
     * @param locale        locale for localized error messages
     * @throws ResponseStatusException if Quartz job creation fails
     */
    private void createQuartzJobWithCleanup(EmailSchedule emailSchedule, Locale locale) {
        try {
            scheduleManagerService.createQuartzJob(emailSchedule);
            String jobKey = QUARTZ_JOB_KEY_PREFIX + emailSchedule.getId().toString();
            emailSchedule.setQuartzJobKey(jobKey);
            emailScheduleRepository.save(emailSchedule);
        } catch (SchedulerException e) {
            log.error("Failed to create Quartz job for schedule: {}", emailSchedule.getId(), e);
            cleanupFailedSchedule(emailSchedule);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    messageUtil.getMessage(MSG_EMAIL_SCHEDULE_QUARTZ_JOB_CREATION_FAILED, locale));
        }
    }

    private void cleanupFailedSchedule(EmailSchedule emailSchedule) {
        List<EmailScheduleTranslation> existingTranslations =
                emailScheduleTranslationRepository.findAllByScheduleId(emailSchedule.getId());
        if (existingTranslations != null && !existingTranslations.isEmpty()) {
            emailScheduleTranslationRepository.deleteAll(existingTranslations);
            log.info("Deleted {} translations for failed schedule: {}", existingTranslations.size(), emailSchedule.getId());
        }
        emailScheduleRepository.delete(emailSchedule);
    }

    private void validateUserPermissions(String role, Locale locale) {
        if (role == null || (!role.equalsIgnoreCase(ROLE_MANAGER) && !role.equalsIgnoreCase(ROLE_HQ_ADMIN))) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    messageUtil.getMessage(MSG_EMAIL_SCHEDULE_UNAUTHORIZED, locale));
        }
    }

    /**
     * Validates schedule frequency and scheduled day settings.
     * For weekly schedules, validates that scheduledDay is between 1 and 7.
     * For monthly schedules, scheduledDay is always normalized to 1.
     *
     * @param frequency    the schedule frequency (WEEKLY or MONTHLY)
     * @param scheduledDay the scheduled day (1-7 for weekly, ignored for monthly)
     * @param locale       locale for localized error messages
     * @throws ResponseStatusException if validation fails
     */
    private void validateFrequencyAndDay(ScheduleFrequency frequency, Integer scheduledDay, Locale locale) {
        if (frequency == ScheduleFrequency.WEEKLY) {
            if (scheduledDay == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        messageUtil.getMessage(MSG_EMAIL_SCHEDULE_DAY_REQUIRED, locale));
            }

            if (scheduledDay < 1 || scheduledDay > 7) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        messageUtil.getMessage(MSG_EMAIL_SCHEDULE_DAY_INVALID_WEEKLY, locale));
            }
        }
        // MONTHLY always uses day 1, so no validation needed for scheduledDay
    }

    /**
     * Builds an EmailScheduleResponse DTO from an EmailSchedule entity.
     * Loads translations and applies locale-based fallback logic for schedule name.
     * Returns the first matching translation or falls back to supported languages in order.
     *
     * @param schedule the email schedule entity to convert
     * @param locale   the locale code for selecting translations
     * @return EmailScheduleResponse DTO with all schedule details and translations
     */
    private EmailScheduleResponse buildEmailScheduleResponse(EmailSchedule schedule, String locale) {
        // Load translations for this schedule
        List<EmailScheduleTranslation> translations =
                emailScheduleTranslationRepository.findAllByScheduleId(schedule.getId());

        List<EmailScheduleTranslationDto> translationDtos = new ArrayList<>();
        if (translations != null && !translations.isEmpty()) {
            // Try exact match first
            EmailScheduleTranslation exactMatch = translations.stream()
                    .filter(t -> t.getLanguageCode() != null && t.getLanguageCode().equalsIgnoreCase(locale))
                    .findFirst()
                    .orElse(null);
            
            if (exactMatch != null) {
                // Use exact match
                translationDtos.add(EmailScheduleTranslationDto.builder()
                        .languageCode(exactMatch.getLanguageCode())
                        .name(exactMatch.getName())
                        .build());
            } else {
                // Apply fallback if exact match not found
                // Get ordered languages from application.properties (excluding requested locale)
                List<String> fallbackLanguages = localizationProperties.getLanguages().stream()
                        .filter(lang -> lang != null && !lang.equalsIgnoreCase(locale))
                        .collect(Collectors.toList());
                
                // Iterate through fallback languages in order from properties file
                EmailScheduleTranslation fallbackTranslation = null;
                for (String fallbackLang : fallbackLanguages) {
                    fallbackTranslation = translations.stream()
                            .filter(t -> t.getLanguageCode() != null && t.getLanguageCode().equalsIgnoreCase(fallbackLang))
                            .findFirst()
                            .orElse(null);
                    if (fallbackTranslation != null) {
                        break; // Found a translation, stop searching
                    }
                }
                
                if (fallbackTranslation != null) {
                    translationDtos.add(EmailScheduleTranslationDto.builder()
                            .languageCode(fallbackTranslation.getLanguageCode())
                            .name(fallbackTranslation.getName())
                            .build());
                }
            }
        }

        return EmailScheduleResponse.builder()
                .id(schedule.getId())
                .reportType(schedule.getReportType())
                .frequency(schedule.getFrequency())
                .scheduledTime(schedule.getScheduledTime())
                .scheduledDay(schedule.getScheduledDay())
                .restaurantId(schedule.getRestaurant() != null ? schedule.getRestaurant().getId() : null)
                .restaurantName(schedule.getRestaurant() != null && schedule.getRestaurant().getTranslations() != null
                        && !schedule.getRestaurant().getTranslations().isEmpty()
                        ? schedule.getRestaurant().getTranslations().get(0).getName() : null)
                .restaurantGroupId(schedule.getRestaurantGroup() != null ? schedule.getRestaurantGroup().getId() : null)
                .restaurantGroupName(schedule.getRestaurantGroup() != null && schedule.getRestaurantGroup().getTranslations() != null
                        && !schedule.getRestaurantGroup().getTranslations().isEmpty()
                        ? schedule.getRestaurantGroup().getTranslations().get(0).getName() : null)
                .recipientEmail(schedule.getRecipientEmail())
                .isActive(schedule.getIsActive())
                .createdById(schedule.getCreatedBy() != null ? schedule.getCreatedBy().getId() : null)
                .createdByName(schedule.getCreatedBy() != null
                        ? schedule.getCreatedBy().getFirstName() + " " + schedule.getCreatedBy().getLastName() : null)
                .createdAt(schedule.getCreatedAt() != null ? schedule.getCreatedAt().toLocalDateTime() : null)
                .updatedById(schedule.getUpdatedBy() != null ? schedule.getUpdatedBy().getId() : null)
                .updatedByName(schedule.getUpdatedBy() != null
                        ? schedule.getUpdatedBy().getFirstName() + " " + schedule.getUpdatedBy().getLastName() : null)
                .updatedAt(schedule.getUpdatedAt() != null ? schedule.getUpdatedAt().toLocalDateTime() : null)
                .lastExecutedAt(schedule.getLastExecutedAt())
                .nextExecutionAt(schedule.getNextExecutionAt())
                .quartzJobKey(schedule.getQuartzJobKey())
                .period(schedule.getPeriod())
                .startDate(schedule.getStartDate())
                .endDate(schedule.getEndDate())
                .translations(translationDtos)
                .build();
    }

    // NOTE:
    // The service used to send an "initial report email" immediately on schedule creation.
    // That behavior has been removed so emails are sent only when the Quartz job fires.

    // (initial-email helper methods removed)
}

