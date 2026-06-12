package com.gulfnet.restaurantmanagement.job;

import com.gulfnet.shared_library.entity.PriceOverride;
import com.gulfnet.shared_library.enums.PriceOverrideStatus;
import com.gulfnet.shared_library.repository.PriceOverrideRepository;
import lombok.extern.slf4j.Slf4j;
import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

/**
 * Quartz Job to deactivate a specific price override when validTo time passes
 * This is a one-time job scheduled for a specific price override's validTo date
 */
@Slf4j
@Component
public class PriceOverrideDeactivationJob implements Job {

    @Autowired
    private ApplicationContext applicationContext;

    /**
     * Quartz entry point that deactivates a single {@link PriceOverride} once its {@code validTo} timestamp is reached.
     * <p>
     * Expects {@code priceOverrideId} to be present in the job data map. If the price override exists, is not deleted,
     * and the current UTC time is at or after {@code validTo}, the status is transitioned from {@link PriceOverrideStatus#LIVE}
     * to {@link PriceOverrideStatus#UNSCHEDULED} and persisted.
     * </p>
     *
     * @param context Quartz execution context (must contain {@code priceOverrideId})
     * @throws JobExecutionException when the target override cannot be loaded or the deactivation flow fails
     */
    @Override
    @Transactional
    public void execute(JobExecutionContext context) throws JobExecutionException {
        try {
            // Get price override ID from job data
            String priceOverrideIdStr = context.getJobDetail().getJobDataMap().getString("priceOverrideId");
            UUID priceOverrideId = UUID.fromString(priceOverrideIdStr);
            
            log.info("=== EXECUTING PRICE OVERRIDE DEACTIVATION JOB ===");
            log.info("Job execution time: {}", LocalDateTime.now(ZoneOffset.UTC));
            log.info("Scheduled fire time: {}", context.getScheduledFireTime());
            log.info("Actual fire time: {}", context.getFireTime());
            log.info("Price override ID: {}", priceOverrideId);
            
            // Get repository from Spring context
            PriceOverrideRepository priceOverrideRepository = applicationContext.getBean(PriceOverrideRepository.class);
            
            // Find price override
            PriceOverride priceOverride = priceOverrideRepository.findById(priceOverrideId)
                    .orElseThrow(() -> new JobExecutionException("Price override not found: " + priceOverrideId));
            
            if (Boolean.TRUE.equals(priceOverride.getIsDeleted())) {
                log.warn("Price override {} is deleted, skipping deactivation", priceOverrideId);
                return;
            }
            
            // Use validTo directly as deactivation time
            java.time.OffsetDateTime nowUtc = java.time.OffsetDateTime.now(java.time.ZoneOffset.UTC);
            java.time.OffsetDateTime deactivationTime = priceOverride.getValidTo();
            
            if (deactivationTime == null) {
                log.warn("Price override {} has no validTo time, cannot deactivate", priceOverrideId);
                return;
            }
            
            log.info("Deactivation time: {}", deactivationTime);
            log.info("Current time: {}", nowUtc);
            
            // If we're at or past deactivation time, set status to UNSCHEDULED
            if ((nowUtc.isAfter(deactivationTime) || nowUtc.equals(deactivationTime)) 
                    && priceOverride.getStatus() == PriceOverrideStatus.LIVE) {
                priceOverride.setStatus(PriceOverrideStatus.UNSCHEDULED);
                priceOverrideRepository.save(priceOverride);
                log.info("Price override {} deactivated at {} - status set to UNSCHEDULED", 
                    priceOverrideId, deactivationTime);
            } else if (priceOverride.getStatus() == PriceOverrideStatus.UNSCHEDULED) {
                log.info("Price override {} already UNSCHEDULED", priceOverrideId);
            } else {
                log.warn("Deactivation job ran too early. Current: {}, Expected: {}, Current status: {}", 
                    nowUtc, deactivationTime, priceOverride.getStatus());
            }
            
            log.info("Price override deactivation job completed successfully at {}", LocalDateTime.now(ZoneOffset.UTC));
            log.info("=== PRICE OVERRIDE DEACTIVATION JOB COMPLETED ===");
            
        } catch (Exception e) {
            log.error("Error executing price override deactivation job", e);
            throw new JobExecutionException("Failed to execute price override deactivation job", e);
        }
    }
}

