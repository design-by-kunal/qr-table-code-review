package com.gulfnet.restaurantmanagement.job;

import com.gulfnet.restaurantmanagement.service.UnusedSessionExpiryScheduleService;
import com.gulfnet.restaurantmanagement.service.UnusedSessionExpiryService;
import lombok.extern.slf4j.Slf4j;
import org.quartz.DisallowConcurrentExecution;
import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Quartz job fired at a restaurant's operating-hours cutoff (close + extend hours).
 * Expires active sessions with no orders for that restaurant.
 */
@Slf4j
@Component
@DisallowConcurrentExecution
public class UnusedSessionExpiryJob implements Job {

    @Autowired
    private ApplicationContext applicationContext;

    @Override
    public void execute(JobExecutionContext context) throws JobExecutionException {
        String restaurantIdStr = context.getJobDetail().getJobDataMap()
                .getString(UnusedSessionExpiryScheduleService.JOB_DATA_RESTAURANT_ID);
        if (restaurantIdStr == null || restaurantIdStr.isBlank()) {
            throw new JobExecutionException("Missing restaurantId in job data");
        }
        UUID restaurantId = UUID.fromString(restaurantIdStr);
        try {
            log.info(
                    "Unused session expiry Quartz job for restaurant {} at scheduled fire time {}",
                    restaurantId,
                    context.getScheduledFireTime());
            UnusedSessionExpiryService service = applicationContext.getBean(UnusedSessionExpiryService.class);
            int expired = service.expireUnusedSessionsForRestaurant(restaurantId);
            log.info("Unused session expiry completed for restaurant {}, expired {} session(s)", restaurantId, expired);
        } catch (Exception e) {
            log.error("Unused session expiry Quartz job failed for restaurant {}", restaurantId, e);
            throw new JobExecutionException("Failed to expire unused sessions for restaurant " + restaurantId, e);
        }
    }
}
