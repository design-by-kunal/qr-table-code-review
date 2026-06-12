package com.gulfnet.restaurantmanagement.service.impl;

import com.gulfnet.restaurantmanagement.job.RestaurantMenuScheduleJob;
import com.gulfnet.shared_library.entity.CategoryItemMapping;
import com.gulfnet.shared_library.entity.Menu;
import com.gulfnet.shared_library.entity.Restaurant;
import com.gulfnet.shared_library.entity.RestaurantGroupMenuId;
import com.gulfnet.shared_library.entity.RestaurantGroupMenuMapping;
import com.gulfnet.shared_library.entity.RestaurantItemAvailability;
import com.gulfnet.shared_library.entity.RestaurantMenuId;
import com.gulfnet.shared_library.entity.RestaurantMenuMapping;
import com.gulfnet.shared_library.entity.User;
import com.gulfnet.shared_library.enums.MenuStatus;
import com.gulfnet.shared_library.enums.RestaurantMenuMappingStatus;
import com.gulfnet.shared_library.repository.CategoryItemMappingRepository;
import com.gulfnet.shared_library.repository.MenuRepository;
import com.gulfnet.shared_library.repository.PriceOverrideMappingRepository;
import com.gulfnet.shared_library.repository.PriceOverrideRepository;
import com.gulfnet.shared_library.repository.RestaurantGroupMenuMappingRepository;
import com.gulfnet.shared_library.repository.RestaurantItemAvailabilityRepository;
import com.gulfnet.shared_library.repository.RestaurantMenuMappingRepository;
import com.gulfnet.shared_library.repository.UserRepository;
import com.gulfnet.shared_library.entity.PriceOverride;
import com.gulfnet.shared_library.entity.PriceOverrideMapping;
import com.gulfnet.shared_library.util.EmailSender;
import com.gulfnet.shared_library.model.request.ScheduleMenuRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.quartz.*;
import org.quartz.impl.matchers.GroupMatcher;
import org.springframework.context.ApplicationContext;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class MenuPublishAsyncService {

    private final MenuRepository menuRepository;
    private final UserRepository userRepository;
    private final RestaurantGroupMenuMappingRepository groupMenuMappingRepository;
    private final RestaurantMenuMappingRepository restaurantMenuMappingRepository;
    private final CategoryItemMappingRepository categoryItemMappingRepository;
    private final RestaurantItemAvailabilityRepository restaurantItemAvailabilityRepository;
    private final PriceOverrideMappingRepository priceOverrideMappingRepository;
    private final PriceOverrideRepository priceOverrideRepository;
    private final com.gulfnet.shared_library.repository.RestaurantDiscountMappingRepository restaurantDiscountMappingRepository;
    private final com.gulfnet.shared_library.repository.RestaurantPromotionMappingRepository restaurantPromotionMappingRepository;
    private final EmailSender emailSender;
    private final ApplicationContext applicationContext;
    private final Scheduler scheduler;

    /**
     * Result class to hold data from synchronous mapping transfer for async tasks
     */
    public static class MappingTransferResult {
        public final Set<UUID> affectedRestaurantIds;
        public final Set<UUID> allTransferredRestaurantIds;
        public final Map<OffsetDateTime, List<UUID>> scheduledRestaurantsByTime;

        public MappingTransferResult(Set<UUID> affectedRestaurantIds, 
                                    Set<UUID> allTransferredRestaurantIds,
                                    Map<OffsetDateTime, List<UUID>> scheduledRestaurantsByTime) {
            this.affectedRestaurantIds = affectedRestaurantIds;
            this.allTransferredRestaurantIds = allTransferredRestaurantIds;
            this.scheduledRestaurantsByTime = scheduledRestaurantsByTime;
        }
    }

    /**
     * Synchronously transfers restaurant and group mappings from previously published menus to the new menu.
     * This ensures mappings are available immediately after publish.
     * 
     * @param menuId The newly published menu ID
     * @param userId The user ID performing the publish
     * @return MappingTransferResult containing data needed for async tasks
     */
    @Transactional
    public MappingTransferResult transferRestaurantMappingsSynchronously(UUID menuId, String userId) {
        Menu menu = menuRepository.findById(menuId)
                .orElseThrow(() -> new IllegalStateException("Menu not found for mapping transfer: " + menuId));

        UUID menuMasterId = menu.getMenuMasterId();
        List<Menu> previouslyPublishedMenus =
                menuRepository.findByMenuMasterIdAndStatusAndIsDeletedFalseOrderByVersionDesc(
                        menuMasterId, MenuStatus.PUBLISHED);

        User currentUser = userRepository.findById(UUID.fromString(userId))
                .orElseThrow(() -> new IllegalStateException("User not found for mapping transfer: " + userId));

        // Collect restaurants impacted to notify managers later
        Set<UUID> affectedRestaurantIds = new HashSet<>();
        // Collect all restaurant IDs that are being transferred (for price override cleanup)
        Set<UUID> allTransferredRestaurantIds = new HashSet<>();
        // Collect restaurants that were SCHEDULED grouped by scheduled time to re-schedule for the new menu
        Map<OffsetDateTime, List<UUID>> scheduledRestaurantsByTime = new HashMap<>();

        // Archive all previously published versions and transfer their mappings to the new menu
        for (Menu publishedMenu : previouslyPublishedMenus) {
            if (publishedMenu.getId().equals(menuId)) {
                // Skip the menu we just published
                continue;
            }

            // Transfer restaurant group mappings from archived menu to new menu
            List<RestaurantGroupMenuMapping> groupMappings =
                    groupMenuMappingRepository.findById_MenuId(publishedMenu.getId());
            for (RestaurantGroupMenuMapping groupMapping : groupMappings) {
                // Create new mapping for the current menu
                RestaurantGroupMenuMapping newGroupMapping = RestaurantGroupMenuMapping.builder()
                        .id(new RestaurantGroupMenuId(groupMapping.getRestaurantGroup().getId(), menuId))
                        .restaurantGroup(groupMapping.getRestaurantGroup())
                        .menu(menu)
                        .build();
                groupMenuMappingRepository.save(newGroupMapping);

                // Delete the old mapping
                groupMenuMappingRepository.delete(groupMapping);
                log.info("Transferred restaurant group mapping from archived menu {} to new menu {}",
                        publishedMenu.getId(), menuId);
            }

            // Transfer restaurant mappings from archived menu to new menu
            List<RestaurantMenuMapping> restaurantMappings =
                    restaurantMenuMappingRepository.findById_MenuId(publishedMenu.getId());

            for (RestaurantMenuMapping restaurantMapping : restaurantMappings) {
                UUID restaurantId = restaurantMapping.getRestaurant().getId();
                allTransferredRestaurantIds.add(restaurantId);
                RestaurantMenuMappingStatus previousStatus = restaurantMapping.getStatus();

                // Create new mapping for the current menu
                RestaurantMenuMapping newRestaurantMapping = RestaurantMenuMapping.builder()
                        .id(new RestaurantMenuId(restaurantId, menuId))
                        .restaurant(restaurantMapping.getRestaurant())
                        .menu(menu)
                        .status(previousStatus)
                        .build();

                // Preserve scheduled publish time or set appropriately based on previous status
                if (previousStatus == RestaurantMenuMappingStatus.SCHEDULED) {
                    newRestaurantMapping.setScheduledPublishTime(restaurantMapping.getScheduledPublishTime());
                    // Queue for re-scheduling under the new menu version
                    if (restaurantMapping.getScheduledPublishTime() != null) {
                        scheduledRestaurantsByTime
                                .computeIfAbsent(restaurantMapping.getScheduledPublishTime(), k -> new ArrayList<>())
                                .add(restaurantId);
                    }
                } else if (previousStatus == RestaurantMenuMappingStatus.LIVE) {
                    newRestaurantMapping.setScheduledPublishTime(OffsetDateTime.now(ZoneOffset.UTC));
                } else {
                    newRestaurantMapping.setScheduledPublishTime(null);
                }
                restaurantMenuMappingRepository.save(newRestaurantMapping);

                // Track affected restaurants for email notification (only those previously LIVE)
                if (previousStatus == RestaurantMenuMappingStatus.LIVE) {
                    affectedRestaurantIds.add(restaurantId);
                }

                // Delete the old mapping
                restaurantMenuMappingRepository.delete(restaurantMapping);
                log.info("Transferred restaurant mapping from archived menu {} to new menu {}",
                        publishedMenu.getId(), menuId);
                
                // Delete all restaurant-discount mappings for this restaurant
                List<com.gulfnet.shared_library.entity.RestaurantDiscountMapping> restaurantDiscountMappings = 
                    restaurantDiscountMappingRepository.findById_RestaurantId(restaurantId);
                
                if (restaurantDiscountMappings != null && !restaurantDiscountMappings.isEmpty()) {
                    restaurantDiscountMappingRepository.deleteAll(restaurantDiscountMappings);
                    log.info("Deleted {} restaurant-discount mappings for restaurant {} during menu transfer",
                        restaurantDiscountMappings.size(), restaurantId);
                }

                // Delete all restaurant-promotion mappings for this restaurant
                List<com.gulfnet.shared_library.entity.RestaurantPromotionMapping> restaurantPromotionMappings = 
                    restaurantPromotionMappingRepository.findById_RestaurantId(restaurantId);
                
                if (restaurantPromotionMappings != null && !restaurantPromotionMappings.isEmpty()) {
                    restaurantPromotionMappingRepository.deleteAll(restaurantPromotionMappings);
                    log.info("Deleted {} restaurant-promotion mappings for restaurant {} during menu transfer",
                        restaurantPromotionMappings.size(), restaurantId);
                }
            }

            // Archive the previously published menu
            publishedMenu.setStatus(MenuStatus.ARCHIVED);
            publishedMenu.setUpdatedAt(OffsetDateTime.now(ZoneOffset.UTC));
            publishedMenu.setUpdatedBy(currentUser);
            menuRepository.save(publishedMenu);
            log.info("Archived previously published menu version: {}", publishedMenu.getId());

            // Cancel any existing Quartz jobs for the archived menu
            try {
                cancelScheduledJobsForMenu(publishedMenu.getId());
            } catch (SchedulerException e) {
                log.error("Failed to cancel scheduled jobs for archived menu {}: {}",
                        publishedMenu.getId(), e.getMessage());
            }
        }

        return new MappingTransferResult(affectedRestaurantIds, allTransferredRestaurantIds, scheduledRestaurantsByTime);
    }

    /**
     * Runs heavy post-publish tasks asynchronously:
     * - Create/remove availability
     * - Reschedule Quartz jobs
     * - Send notifications
     * Note: Restaurant/group mappings are transferred synchronously before this is called
     */
    @Async
    public void runPostPublishTasks(UUID menuId, String userId, String locale, MappingTransferResult transferResult) {
        try {
            Menu menu = menuRepository.findById(menuId)
                    .orElseThrow(() -> new IllegalStateException("Menu not found for async publish: " + menuId));

            User currentUser = userRepository.findById(UUID.fromString(userId))
                    .orElseThrow(() -> new IllegalStateException("User not found for async publish: " + userId));

            // Use data from synchronous mapping transfer
            Set<UUID> affectedRestaurantIds = transferResult.affectedRestaurantIds;
            Set<UUID> allTransferredRestaurantIds = transferResult.allTransferredRestaurantIds;
            Map<OffsetDateTime, List<UUID>> scheduledRestaurantsByTime = transferResult.scheduledRestaurantsByTime;

            // Preload category item mappings for new menu once
            List<CategoryItemMapping> newMenuCategoryItemMappings =
                    categoryItemMappingRepository.findByMenuCategoryMappingMenuId(menuId);

            // Get all previously published menus to get old menu category item mappings
            UUID menuMasterId = menu.getMenuMasterId();
            List<Menu> previouslyPublishedMenus =
                    menuRepository.findByMenuMasterIdAndStatusAndIsDeletedFalseOrderByVersionDesc(
                            menuMasterId, MenuStatus.ARCHIVED);

            // Create availability for new menu items and remove availability for old menu items
            for (UUID restaurantId : allTransferredRestaurantIds) {
                createAvailabilitySafe(restaurantId, newMenuCategoryItemMappings, currentUser.getId(), menu.getId());
                removeOldMenuAvailabilitySafe(restaurantId, menuId, previouslyPublishedMenus);
            }

            // Delete price override mappings and soft delete price overrides for transferred restaurants
            deletePriceOverridesForTransferredRestaurants(allTransferredRestaurantIds, currentUser);

            // External I/O (Quartz + notifications/email) must not hold a DB transaction open.
            runAfterCommit(() -> {
                // Re-schedule Quartz jobs for restaurants that were scheduled on previous menu
                rescheduleQuartzJobsForNewMenu(scheduledRestaurantsByTime, menu);

                // Notify restaurant managers about menu going live for their restaurants
                sendMenuLiveNotifications(menu, affectedRestaurantIds, locale);
            });

        } catch (Exception ex) {
            log.error("Async post-publish tasks failed for menu {}: {}", menuId, ex.getMessage(), ex);
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

    // ==================== EXTRACTED HELPER METHODS ====================

    /**
     * Creates availability for a restaurant's menu items, logging errors without propagating.
     */
    private void createAvailabilitySafe(UUID restaurantId, List<CategoryItemMapping> newMenuCategoryItemMappings,
                                        UUID userId, UUID menuId) {
        try {
            createAvailabilityForRestaurantMenuMappingOptimized(restaurantId, newMenuCategoryItemMappings, userId);
        } catch (Exception e) {
            log.error("Failed to create availability for restaurant {} and menu {}: {}",
                    restaurantId, menuId, e.getMessage());
        }
    }

    /**
     * Removes availability for old menu items for a given restaurant, logging errors without propagating.
     */
    private void removeOldMenuAvailabilitySafe(UUID restaurantId, UUID currentMenuId,
                                                List<Menu> previouslyPublishedMenus) {
        for (Menu publishedMenu : previouslyPublishedMenus) {
            if (publishedMenu.getId().equals(currentMenuId)) {
                continue; // Skip the current menu
            }
            try {
                List<CategoryItemMapping> oldMenuCategoryItemMappings =
                        categoryItemMappingRepository.findByMenuCategoryMappingMenuId(publishedMenu.getId());
                removeAvailabilityForRestaurantMenuMappingOptimized(restaurantId, oldMenuCategoryItemMappings);
            } catch (Exception e) {
                log.error("Failed to remove availability for restaurant {} and old menu {}: {}",
                        restaurantId, publishedMenu.getId(), e.getMessage());
            }
        }
    }

    /**
     * Deletes price override mappings and soft-deletes price overrides for transferred restaurants.
     */
    private void deletePriceOverridesForTransferredRestaurants(Set<UUID> allTransferredRestaurantIds, User currentUser) {
        if (allTransferredRestaurantIds.isEmpty()) {
            return;
        }
        try {
            List<UUID> restaurantIdList = new ArrayList<>(allTransferredRestaurantIds);
            List<PriceOverrideMapping> priceOverrideMappings =
                    priceOverrideMappingRepository.findByRestaurantIdIn(restaurantIdList);

            if (!priceOverrideMappings.isEmpty()) {
                // Get distinct price overrides from the mappings
                Set<UUID> priceOverrideIds = priceOverrideMappings.stream()
                        .map(mapping -> mapping.getPriceOverride().getId())
                        .collect(Collectors.toSet());

                // Delete all price override mappings
                priceOverrideMappingRepository.deleteAll(priceOverrideMappings);
                log.info("Deleted {} price override mappings for {} restaurants",
                        priceOverrideMappings.size(), allTransferredRestaurantIds.size());

                // Soft delete all price overrides
                List<PriceOverride> priceOverrides = priceOverrideRepository.findAllById(priceOverrideIds);
                for (PriceOverride priceOverride : priceOverrides) {
                    if (!Boolean.TRUE.equals(priceOverride.getIsDeleted())) {
                        priceOverride.setIsDeleted(true);
                        priceOverride.setUpdatedAt(OffsetDateTime.now(ZoneOffset.UTC));
                        priceOverride.setUpdatedBy(currentUser);
                    }
                }
                priceOverrideRepository.saveAll(priceOverrides);
                log.info("Soft deleted {} price overrides for transferred restaurants",
                        priceOverrides.size());
            }
        } catch (Exception e) {
            log.error("Failed to delete price override mappings and soft delete price overrides for restaurants {}: {}",
                    allTransferredRestaurantIds, e.getMessage(), e);
        }
    }

    /**
     * Re-schedules Quartz jobs for restaurants that were scheduled on a previous menu version.
     */
    private void rescheduleQuartzJobsForNewMenu(Map<OffsetDateTime, List<UUID>> scheduledRestaurantsByTime, Menu menu) {
        if (scheduledRestaurantsByTime.isEmpty()) {
            return;
        }
        for (Map.Entry<OffsetDateTime, List<UUID>> entry : scheduledRestaurantsByTime.entrySet()) {
            try {
                ScheduleMenuRequest scheduleRequest = ScheduleMenuRequest.builder()
                        .menuId(menu.getId())
                        .restaurantIds(entry.getValue())
                        .schedulePublishTime(entry.getKey())
                        .build();
                scheduleRestaurantMenuJob(scheduleRequest);
                log.info("Re-scheduled {} restaurants for new menu {} at {}",
                        entry.getValue().size(), menu.getId(), entry.getKey());
            } catch (Exception e) {
                log.error("Failed to re-schedule restaurants {} for new menu {} at {}: {}",
                        entry.getValue(), menu.getId(), entry.getKey(), e.getMessage());
            }
        }
    }

    /**
     * Sends email and push/in-app notifications to restaurant managers about the menu going live.
     *
     * @param locale publish flow locale string (e.g. from API), used as fallback for manager notification copy
     */
    private void sendMenuLiveNotifications(Menu menu, Set<UUID> affectedRestaurantIds, String locale) {
        try {
            menu.getTranslations().size(); // initialize lazy collection if needed
            if (!affectedRestaurantIds.isEmpty()) {
                Locale notificationLocale = Locale.forLanguageTag(
                        locale != null && !locale.isBlank() ? locale.trim() : "en");
                RestaurantMenuScheduleJob.sendRestaurantMenuLiveNotification(
                        menu, affectedRestaurantIds, emailSender, applicationContext, notificationLocale);
            }
        } catch (Exception e) {
            log.error("Failed to send manager notifications for menu {}: {}",
                    menu.getId(), e.getMessage());
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

        // Delete all fetched availability records
        for (RestaurantItemAvailability availability : existingAvailabilities) {
            restaurantItemAvailabilityRepository.delete(availability);
        }
    }

    /**
     * Schedules a one-time Quartz job to publish a menu to a set of restaurants at the requested time.
     * <p>
     * Uses UTC for consistent scheduling and validates the scheduled publish time is in the future.
     * The scheduled job stores {@code menuId} and a comma-separated {@code restaurantIds} list in the JobDataMap.
     * </p>
     *
     * @param request scheduling request containing menu id, restaurant ids, and the publish timestamp
     * @throws IllegalArgumentException if {@code request.schedulePublishTime} is before "now" (UTC)
     * @throws SchedulerException if Quartz fails to schedule the job/trigger
     */
    private void scheduleRestaurantMenuJob(ScheduleMenuRequest request) throws SchedulerException {
        // Use UTC timezone for consistent scheduling
        ZoneOffset utcOffset = ZoneOffset.UTC;

        // Validate that the scheduled time is in the future
        OffsetDateTime now = OffsetDateTime.now(utcOffset);
        if (request.getSchedulePublishTime().isBefore(now)) {
            throw new IllegalArgumentException("Scheduled time must be in the future. Current time: " + now +
                    ", Scheduled time: " + request.getSchedulePublishTime());
        }

        // Create job detail
        JobDetail jobDetail = JobBuilder.newJob(RestaurantMenuScheduleJob.class)
                .withIdentity("restaurant-menu-schedule-" + request.getMenuId() + "-" + System.currentTimeMillis())
                .usingJobData("menuId", request.getMenuId().toString())
                .usingJobData("restaurantIds", String.join(",", request.getRestaurantIds().stream()
                        .map(UUID::toString)
                        .toList()))
                .build();

        // Convert OffsetDateTime to Date using UTC timezone
        Date scheduledDate = Date.from(request.getSchedulePublishTime().toInstant());

        // Create trigger
        Trigger trigger = TriggerBuilder.newTrigger()
                .withIdentity("restaurant-menu-trigger-" + request.getMenuId() + "-" + System.currentTimeMillis())
                .startAt(scheduledDate)
                .build();

        // Schedule the job
        scheduler.scheduleJob(jobDetail, trigger);
    }

    /**
     * Cancels any Quartz jobs associated with the given menu id.
     * <p>
     * Iterates across all Quartz job groups and deletes jobs whose JobDataMap contains a {@code menuId}
     * matching the provided id (case-insensitive string comparison).
     * </p>
     *
     * @param menuId menu identifier whose scheduled publish jobs should be removed
     * @throws SchedulerException if Quartz job discovery or deletion fails
     */
    private void cancelScheduledJobsForMenu(UUID menuId) throws SchedulerException {
        for (String groupName : scheduler.getJobGroupNames()) {
            for (JobKey jobKey : scheduler.getJobKeys(GroupMatcher.jobGroupEquals(groupName))) {
                JobDetail jobDetail = scheduler.getJobDetail(jobKey);
                if (jobDetail != null && jobDetail.getJobDataMap() != null) {
                    String jobMenuId = jobDetail.getJobDataMap().getString("menuId");
                    if (jobMenuId != null && jobMenuId.equalsIgnoreCase(menuId.toString())) {
                        scheduler.deleteJob(jobKey);
                        log.info("Deleted Quartz job {} for archived menu {}", jobKey, menuId);
                    }
                }
            }
        }
    }
}


