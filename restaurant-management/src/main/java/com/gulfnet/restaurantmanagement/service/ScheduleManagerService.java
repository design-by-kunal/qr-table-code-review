package com.gulfnet.restaurantmanagement.service;

import com.gulfnet.restaurantmanagement.job.ScheduledEmailReportJob;
import com.gulfnet.shared_library.entity.EmailSchedule;
import com.gulfnet.shared_library.enums.ScheduleFrequency;
import lombok.extern.slf4j.Slf4j;
import org.quartz.*;
import org.quartz.impl.matchers.GroupMatcher;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.time.OffsetTime;
import java.time.ZoneOffset;
import java.util.UUID;

@Slf4j
@Service
public class ScheduleManagerService {

    private static final String EMAIL_SCHEDULES_GROUP = "email-schedules";

    @Autowired
    private Scheduler scheduler;

    /**
     * Builds a cron expression based on the schedule frequency and scheduled time
     * Cron expression format: second minute hour day month day-of-week
     */
    public String buildCronExpression(EmailSchedule schedule) {
        ScheduleFrequency frequency = schedule.getFrequency();
        OffsetTime scheduledTime = schedule.getScheduledTime();
        
        // Use scheduled time if provided, otherwise default to midnight UTC
        int hour = scheduledTime != null ? scheduledTime.getHour() : 0;
        int minute = scheduledTime != null ? scheduledTime.getMinute() : 0;
        int second = scheduledTime != null ? scheduledTime.getSecond() : 0;

        switch (frequency) {
            case DAILY:
                // Run every day at the specified time (UTC)
                return String.format("%d %d %d * * ?", second, minute, hour);

            case WEEKLY:
                // Run on a specific day of week at the specified time UTC (1=Sunday, 2=Monday, ..., 7=Saturday)
                // Quartz uses: 1=Sunday, 2=Monday, ..., 7=Saturday
                Integer dayOfWeek = schedule.getScheduledDay();
                if (dayOfWeek == null || dayOfWeek < 1 || dayOfWeek > 7) {
                    throw new IllegalArgumentException("scheduledDay must be between 1-7 for WEEKLY frequency");
                }
                return String.format("%d %d %d ? * %d", second, minute, hour, dayOfWeek);

            case MONTHLY:
                // Always run on the 1st day of month at the specified time UTC
                return String.format("%d %d %d 1 * ?", second, minute, hour);

            default:
                throw new IllegalArgumentException("Unsupported frequency: " + frequency);
        }
    }

    /**
     * Calculates the next execution time based on the schedule and scheduled time
     * All times are in UTC timezone
     */
    public OffsetDateTime calculateNextExecutionTime(EmailSchedule schedule) {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        ScheduleFrequency frequency = schedule.getFrequency();
        OffsetTime scheduledTime = schedule.getScheduledTime();
        
        // Use scheduled time if provided, otherwise default to midnight UTC
        int hour = scheduledTime != null ? scheduledTime.getHour() : 0;
        int minute = scheduledTime != null ? scheduledTime.getMinute() : 0;
        int second = scheduledTime != null ? scheduledTime.getSecond() : 0;

        OffsetDateTime nextExecution;

        switch (frequency) {
            case DAILY:
                // Next execution is today or tomorrow at the specified time UTC
                OffsetDateTime todayAtScheduledTime = now.toLocalDate()
                        .atTime(hour, minute, second)
                        .atOffset(ZoneOffset.UTC);
                
                if (now.isBefore(todayAtScheduledTime)) {
                    // Today's scheduled time hasn't passed yet
                    nextExecution = todayAtScheduledTime;
                } else {
                    // Today's scheduled time has passed, schedule for tomorrow
                    nextExecution = now.toLocalDate().plusDays(1)
                            .atTime(hour, minute, second)
                            .atOffset(ZoneOffset.UTC);
                }
                break;

            case WEEKLY:
                Integer dayOfWeek = schedule.getScheduledDay();
                if (dayOfWeek == null || dayOfWeek < 1 || dayOfWeek > 7) {
                    throw new IllegalArgumentException("scheduledDay must be between 1-7 for WEEKLY frequency");
                }
                // Convert to Java DayOfWeek (1=Monday, 7=Sunday)
                // Our system: 1=Sunday, 2=Monday, ..., 7=Saturday
                // Java DayOfWeek: 1=Monday, 2=Tuesday, ..., 7=Sunday
                // Correct mapping: System 1 (Sunday) → Java 7, System 2 (Monday) → Java 1, etc.
                int javaDayOfWeek = (dayOfWeek == 1) ? 7 : dayOfWeek - 1;
                
                // Find next occurrence of the day at the specified time UTC
                int currentDayOfWeek = now.getDayOfWeek().getValue();
                int daysUntilNext = (javaDayOfWeek - currentDayOfWeek + 7) % 7;
                
                if (daysUntilNext == 0) {
                    // Same day of week - check if time hasn't passed yet
                    OffsetDateTime todayAtTime = now.toLocalDate()
                            .atTime(hour, minute, second)
                            .atOffset(ZoneOffset.UTC);
                    if (now.isBefore(todayAtTime)) {
                        nextExecution = todayAtTime;
                    } else {
                        // Time already passed, schedule for next week
                        nextExecution = now.toLocalDate().plusDays(7)
                                .atTime(hour, minute, second)
                                .atOffset(ZoneOffset.UTC);
                    }
                } else {
                    nextExecution = now.toLocalDate().plusDays(daysUntilNext)
                            .atTime(hour, minute, second)
                            .atOffset(ZoneOffset.UTC);
                }
                break;

            case MONTHLY:
                // Always on the 1st day of next month at the specified time UTC
                nextExecution = now.toLocalDate().withDayOfMonth(1).plusMonths(1)
                        .atTime(hour, minute, second)
                        .atOffset(ZoneOffset.UTC);
                break;

            default:
                throw new IllegalArgumentException("Unsupported frequency: " + frequency);
        }

        return nextExecution;
    }

    /**
     * Creates a Quartz job for the email schedule
     * All times are in UTC timezone
     */
    public void createQuartzJob(EmailSchedule schedule) throws SchedulerException {
        String jobKey = "email-schedule-" + schedule.getId().toString();
        String triggerKey = "email-schedule-trigger-" + schedule.getId().toString();

        // Create job detail
        JobDetail jobDetail = JobBuilder.newJob(ScheduledEmailReportJob.class)
                .withIdentity(jobKey, EMAIL_SCHEDULES_GROUP)
                .usingJobData("scheduleId", schedule.getId().toString())
                .storeDurably(false)
                .build();

        // Build cron expression using scheduled time
        String cronExpression = buildCronExpression(schedule);

        // Create cron trigger with UTC timezone
        CronTrigger trigger = TriggerBuilder.newTrigger()
                .withIdentity(triggerKey, EMAIL_SCHEDULES_GROUP)
                .withSchedule(CronScheduleBuilder.cronSchedule(cronExpression)
                        .inTimeZone(java.util.TimeZone.getTimeZone("UTC")) // Explicitly set UTC timezone
                        .withMisfireHandlingInstructionFireAndProceed())
                .build();

        // Schedule the job
        scheduler.scheduleJob(jobDetail, trigger);
        log.info("Created Quartz job for email schedule {} with cron expression: {} (scheduled time: {})", 
                schedule.getId(), cronExpression, schedule.getScheduledTime());
    }

    /**
     * Deletes a Quartz job by job key
     */
    public void deleteQuartzJob(String quartzJobKey) throws SchedulerException {
        if (quartzJobKey == null || quartzJobKey.isEmpty()) {
            log.warn("Quartz job key is null or empty, skipping deletion");
            return;
        }

        try {
            // The quartzJobKey is stored as "email-schedule-{uuid}"
            // Extract the job name and group
            JobKey jobKey = new JobKey(quartzJobKey, EMAIL_SCHEDULES_GROUP);
            
            if (scheduler.checkExists(jobKey)) {
                scheduler.deleteJob(jobKey);
                log.info("Successfully deleted Quartz job: {}", jobKey);
            } else {
                log.warn("Quartz job not found: {}", jobKey);
            }
        } catch (SchedulerException e) {
            log.error("Error deleting Quartz job: {}", quartzJobKey, e);
            throw e;
        }
    }

    /**
     * Deletes a Quartz job by schedule ID (fallback method)
     * Searches for the job by scheduleId in job data map
     */
    public void deleteQuartzJobByScheduleId(UUID scheduleId) throws SchedulerException {
        try {
            // Search for jobs in the email-schedules group
            for (JobKey jobKey : scheduler.getJobKeys(GroupMatcher.jobGroupEquals(EMAIL_SCHEDULES_GROUP))) {
                if (tryDeleteJobForSchedule(jobKey, scheduleId)) {
                    return; // Job found and deleted, exit
                }
            }
            log.warn("No Quartz job found for schedule ID: {}", scheduleId);
        } catch (SchedulerException e) {
            log.error("Error searching for Quartz job by schedule ID: {}", scheduleId, e);
            throw e;
        }
    }

    /**
     * Attempts to delete a Quartz job if its scheduleId matches the given schedule ID.
     *
     * @return true if the job was found and deleted, false otherwise
     */
    private boolean tryDeleteJobForSchedule(JobKey jobKey, UUID scheduleId) {
        try {
            JobDetail jobDetail = scheduler.getJobDetail(jobKey);
            if (jobDetail != null && jobDetail.getJobDataMap() != null) {
                String jobScheduleId = jobDetail.getJobDataMap().getString("scheduleId");
                if (jobScheduleId != null && jobScheduleId.equalsIgnoreCase(scheduleId.toString())) {
                    scheduler.deleteJob(jobKey);
                    log.info("Deleted Quartz job {} for schedule {}", jobKey, scheduleId);
                    return true;
                }
            }
        } catch (SchedulerException e) {
            log.warn("Failed to check/delete job {} for schedule {}: {}", jobKey, scheduleId, e.getMessage());
        }
        return false;
    }
}
