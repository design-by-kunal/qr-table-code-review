package com.gulfnet.restaurantmanagement.service;

import com.gulfnet.restaurantmanagement.job.UnusedSessionExpiryJob;
import com.gulfnet.shared_library.enums.DayOfWeek;
import com.gulfnet.shared_library.repository.RestaurantOperatingHoursRepository;
import com.gulfnet.shared_library.repository.RestaurantRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.quartz.CronScheduleBuilder;
import org.quartz.CronTrigger;
import org.quartz.JobBuilder;
import org.quartz.JobDetail;
import org.quartz.JobKey;
import org.quartz.Scheduler;
import org.quartz.SchedulerException;
import org.quartz.TriggerBuilder;
import org.quartz.TriggerKey;
import org.quartz.impl.matchers.GroupMatcher;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.EnumSet;
import java.util.Optional;
import java.util.Set;
import java.util.TimeZone;
import java.util.UUID;

/**
 * Registers per-restaurant, per-day-of-week Quartz jobs that fire at operating-hours close
 * plus {@code restaurant.chain.operatingHoursExtendHoursAfterClose}.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UnusedSessionExpiryScheduleService {

    static final String JOB_GROUP = "unused-session-expiry";
    public static final String JOB_DATA_RESTAURANT_ID = "restaurantId";
    private static final String JOB_NAME_PREFIX = "unused-session-expiry-";

    private static final Set<DayOfWeek> SCHEDULABLE_DAYS = EnumSet.of(
            DayOfWeek.SUNDAY,
            DayOfWeek.MONDAY,
            DayOfWeek.TUESDAY,
            DayOfWeek.WEDNESDAY,
            DayOfWeek.THURSDAY,
            DayOfWeek.FRIDAY,
            DayOfWeek.SATURDAY);

    private final Scheduler scheduler;
    private final OperatingHoursCutoffService operatingHoursCutoffService;
    private final RestaurantOperatingHoursRepository restaurantOperatingHoursRepository;
    private final RestaurantRepository restaurantRepository;

    @EventListener(ApplicationReadyEvent.class)
    public void syncAllRestaurantsOnStartup() throws SchedulerException {
        removeLegacyGlobalPollingJob();
        int page = 0;
        int scheduled = 0;
        Page<UUID> ids;
        do {
            ids = restaurantRepository.findAllActiveIds(PageRequest.of(page, 100));
            for (UUID restaurantId : ids.getContent()) {
                try {
                    scheduleForRestaurant(restaurantId);
                    scheduled++;
                } catch (SchedulerException e) {
                    log.error("Failed to schedule unused session expiry for restaurant {}", restaurantId, e);
                }
            }
            page++;
        } while (ids.hasNext());
        log.info("Synced unused session expiry Quartz jobs for {} restaurant(s)", scheduled);
    }

    /**
     * (Re)schedules weekly cutoff jobs for the restaurant. Call after operating hours are created or updated.
     */
    public void scheduleForRestaurant(UUID restaurantId) throws SchedulerException {
        cancelJobsForRestaurant(restaurantId);

        if (restaurantOperatingHoursRepository.findByRestaurant_Id(restaurantId).isEmpty()) {
            log.debug("No operating hours for restaurant {}, skipping unused session expiry jobs", restaurantId);
            return;
        }

        for (DayOfWeek dayOfWeek : SCHEDULABLE_DAYS) {
            if (operatingHoursCutoffService.findLatestClosingTime(restaurantId, dayOfWeek).isEmpty()) {
                continue;
            }
            LocalDate referenceDate = referenceDateForDayOfWeek(dayOfWeek);
            Optional<OffsetDateTime> cutoff =
                    operatingHoursCutoffService.resolveCutoffInstant(restaurantId, referenceDate);
            if (cutoff.isEmpty()) {
                continue;
            }
            Optional<String> cron = buildWeeklyCronAtCutoff(cutoff.get());
            if (cron.isEmpty()) {
                continue;
            }
            scheduleJob(restaurantId, dayOfWeek, cron.get(), cutoff.get());
        }
    }

    public void cancelJobsForRestaurant(UUID restaurantId) throws SchedulerException {
        String prefix = JOB_NAME_PREFIX + restaurantId + "-";
        for (JobKey jobKey : scheduler.getJobKeys(GroupMatcher.jobGroupEquals(JOB_GROUP))) {
            if (jobKey.getName().startsWith(prefix)) {
                scheduler.deleteJob(jobKey);
                log.debug("Deleted unused session expiry job {}", jobKey);
            }
        }
    }

    private void scheduleJob(UUID restaurantId, DayOfWeek dayOfWeek, String cronExpression, OffsetDateTime cutoff)
            throws SchedulerException {
        String jobName = JOB_NAME_PREFIX + restaurantId + "-" + dayOfWeek.name();
        String triggerName = jobName + "-trigger";
        JobKey jobKey = JobKey.jobKey(jobName, JOB_GROUP);
        TriggerKey triggerKey = TriggerKey.triggerKey(triggerName, JOB_GROUP);

        JobDetail jobDetail = JobBuilder.newJob(UnusedSessionExpiryJob.class)
                .withIdentity(jobKey)
                .usingJobData(JOB_DATA_RESTAURANT_ID, restaurantId.toString())
                .storeDurably(false)
                .build();

        CronTrigger trigger = TriggerBuilder.newTrigger()
                .withIdentity(triggerKey)
                .forJob(jobDetail)
                .withSchedule(CronScheduleBuilder.cronSchedule(cronExpression)
                        .inTimeZone(TimeZone.getTimeZone("UTC"))
                        .withMisfireHandlingInstructionFireAndProceed())
                .build();

        if (scheduler.checkExists(jobKey)) {
            scheduler.rescheduleJob(triggerKey, trigger);
        } else {
            scheduler.scheduleJob(jobDetail, trigger);
        }
        log.info(
                "Scheduled unused session expiry for restaurant {} on {} at {} UTC (cron: {})",
                restaurantId,
                dayOfWeek,
                cutoff,
                cronExpression);
    }

    /**
     * Builds a weekly Quartz cron for the instant when the cutoff fires (may be the day after closing).
     */
    Optional<String> buildWeeklyCronAtCutoff(OffsetDateTime cutoff) {
        java.time.DayOfWeek fireDay = cutoff.getDayOfWeek();
        int quartzDow = fireDay.getValue() % 7 + 1;
        return Optional.of(String.format(
                "%d %d %d ? * %d",
                cutoff.getSecond(),
                cutoff.getMinute(),
                cutoff.getHour(),
                quartzDow));
    }

    private void removeLegacyGlobalPollingJob() throws SchedulerException {
        JobKey legacyJob = JobKey.jobKey("unused-session-expiry", "system-jobs");
        if (scheduler.checkExists(legacyJob)) {
            scheduler.deleteJob(legacyJob);
            log.info("Removed legacy global unused-session-expiry polling job");
        }
    }

    private static LocalDate referenceDateForDayOfWeek(DayOfWeek dayOfWeek) {
        java.time.DayOfWeek javaDow = java.time.DayOfWeek.valueOf(dayOfWeek.name());
        LocalDate date = LocalDate.now(ZoneOffset.UTC);
        while (date.getDayOfWeek() != javaDow) {
            date = date.plusDays(1);
        }
        return date;
    }
}
