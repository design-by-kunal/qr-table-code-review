package com.gulfnet.restaurantmanagement.job;

import com.gulfnet.shared_library.entity.PriceOverride;
import com.gulfnet.shared_library.entity.PriceOverrideMapping;
import com.gulfnet.shared_library.entity.PriceOverrideTranslation;
import com.gulfnet.shared_library.entity.Restaurant;
import com.gulfnet.shared_library.entity.User;
import com.gulfnet.shared_library.enums.ActionType;
import com.gulfnet.shared_library.enums.PriceOverrideStatus;
import com.gulfnet.shared_library.repository.PriceOverrideRepository;
import com.gulfnet.shared_library.repository.PriceOverrideMappingRepository;
import com.gulfnet.shared_library.repository.PriceOverrideTranslationRepository;
import com.gulfnet.restaurantmanagement.service.AuditTrailService;
import com.gulfnet.restaurantmanagement.service.OrderNotificationService;
import com.gulfnet.restaurantmanagement.util.MessageUtil;
import com.gulfnet.shared_library.model.request.StatusEventMessage;
import lombok.extern.slf4j.Slf4j;
import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Quartz Job to activate a specific price override when validFrom time arrives
 * This is a one-time job scheduled for a specific price override's validFrom date
 */
@Slf4j
@Component
public class PriceOverrideActivationJob implements Job {

    @Autowired
    private ApplicationContext applicationContext;

    /**
     * Executes the scheduled price override activation job.
     * Activates a price override when its validFrom time arrives, creates audit trail, and sends WebSocket notifications.
     *
     * @param context the Quartz job execution context containing price override ID
     * @throws JobExecutionException if the job execution fails
     */
    @Override
    @Transactional
    public void execute(JobExecutionContext context) throws JobExecutionException {
        try {
            // Get price override ID from job data
            String priceOverrideIdStr = context.getJobDetail().getJobDataMap().getString("priceOverrideId");
            UUID priceOverrideId = UUID.fromString(priceOverrideIdStr);
            
            log.info("=== EXECUTING PRICE OVERRIDE ACTIVATION JOB ===");
            log.info("Job execution time: {}", LocalDateTime.now(ZoneOffset.UTC));
            log.info("Scheduled fire time: {}", context.getScheduledFireTime());
            log.info("Actual fire time: {}", context.getFireTime());
            log.info("Price override ID: {}", priceOverrideId);
            
            // Get repository from Spring context
            PriceOverrideRepository priceOverrideRepository = applicationContext.getBean(PriceOverrideRepository.class);
            
            // Find price override
            PriceOverride priceOverride = priceOverrideRepository.findById(priceOverrideId)
                    .orElseThrow(() -> new JobExecutionException("Price override not found: " + priceOverrideId));
            
            if (shouldSkipActivation(priceOverride, priceOverrideId)) {
                return;
            }
            
            // Attempt activation
            boolean wasActivated = attemptActivation(priceOverride, priceOverrideId, priceOverrideRepository);
            
            // Create audit trail and send websocket notification if price override was activated
            if (wasActivated) {
                createAuditTrailForActivation(priceOverride, priceOverrideId);
            }
            
            log.info("Price override activation job completed successfully at {}", LocalDateTime.now(ZoneOffset.UTC));
            log.info("=== PRICE OVERRIDE ACTIVATION JOB COMPLETED ===");
            
        } catch (Exception e) {
            log.error("Error executing price override activation job", e);
            throw new JobExecutionException("Failed to execute price override activation job", e);
        }
    }

    /**
     * Checks if the price override should be skipped (deleted, no validFrom, or already LIVE).
     */
    private boolean shouldSkipActivation(PriceOverride priceOverride, UUID priceOverrideId) {
        if (Boolean.TRUE.equals(priceOverride.getIsDeleted())) {
            log.warn("Price override {} is deleted, skipping activation", priceOverrideId);
            return true;
        }
        if (priceOverride.getValidFrom() == null) {
            log.warn("Price override {} has no validFrom time, cannot activate", priceOverrideId);
            return true;
        }
        if (priceOverride.getStatus() == PriceOverrideStatus.LIVE) {
            log.info("Price override {} is already LIVE, no action needed", priceOverrideId);
            return true;
        }
        return false;
    }

    /**
     * Attempts to activate the price override based on time tolerance.
     * @return true if the price override was activated.
     */
    private boolean attemptActivation(PriceOverride priceOverride, UUID priceOverrideId,
                                       PriceOverrideRepository priceOverrideRepository) {
        OffsetDateTime nowUtc = OffsetDateTime.now(ZoneOffset.UTC);
        OffsetDateTime activationTime = priceOverride.getValidFrom();

        log.info("Activation time: {}", activationTime);
        log.info("Current time: {}", nowUtc);
        long secondsDifference = java.time.Duration.between(activationTime, nowUtc).getSeconds();
        log.info("Time difference: {} seconds", secondsDifference);

        // Allow a small window (5 minutes) for clock skew or slight delays
        if (secondsDifference >= -300) {
            activatePriceOverride(priceOverride, priceOverrideId, priceOverrideRepository);
            return true;
        }

        log.warn("Activation job ran too early. Current: {}, Expected: {}, Difference: {} seconds",
                nowUtc, activationTime, secondsDifference);

        // Still try to activate if we're close (within 10 minutes)
        if (secondsDifference >= -600) {
            log.info("Attempting activation anyway (within tolerance window)");
            activatePriceOverride(priceOverride, priceOverrideId, priceOverrideRepository);
            log.info("✓ Price override {} activated (within tolerance)", priceOverrideId);
            return true;
        }

        return false;
    }

    private void activatePriceOverride(PriceOverride priceOverride, UUID priceOverrideId,
                                        PriceOverrideRepository priceOverrideRepository) {
        priceOverride.setStatus(PriceOverrideStatus.LIVE);
        priceOverrideRepository.save(priceOverride);
        priceOverrideRepository.flush(); // Ensure immediate persistence
        log.info("✓ Price override {} successfully activated at {}", priceOverrideId, priceOverride.getValidFrom());
        log.info("✓ Price override status changed to: {}", priceOverride.getStatus());
    }

    /**
     * Creates an audit trail entry for the price override activation and sends websocket notification.
     */
    private void createAuditTrailForActivation(PriceOverride priceOverride, UUID priceOverrideId) {
        try {
            AuditTrailService auditTrailService = applicationContext.getBean(AuditTrailService.class);
            PriceOverrideTranslationRepository translationRepository = applicationContext.getBean(PriceOverrideTranslationRepository.class);
            PriceOverrideMappingRepository mappingRepository = applicationContext.getBean(PriceOverrideMappingRepository.class);
            
            // Get price override translations to get the name
            List<PriceOverrideTranslation> translations = translationRepository.findByPriceOverrideId(priceOverrideId);
            String overrideName = translations.isEmpty() ? "No translations" : translations.get(0).getName();
            
            // Get restaurant and menu from first mapping for audit trail and websocket notification
            Restaurant restaurant = null;
            UUID restaurantId = null;
            UUID menuId = null;
            List<PriceOverrideMapping> mappings = mappingRepository.findByPriceOverrideId(priceOverrideId);
            if (!mappings.isEmpty()) {
                PriceOverrideMapping firstMapping = mappings.get(0);
                restaurant = firstMapping.getRestaurant();
                restaurantId = restaurant != null ? restaurant.getId() : null;
                menuId = firstMapping.getMenu() != null ? firstMapping.getMenu().getId() : null;
            }
            
            // Get the user who last updated the price override
            User user = priceOverride.getUpdatedBy() != null ? priceOverride.getUpdatedBy() : priceOverride.getCreatedBy();
            
            if (user != null) {
                auditTrailService.createAuditTrail(
                    user,
                    ActionType.PRICE_OVERRIDE_ACTIVATE,
                    restaurant,
                    null, // status - will default to NA for non-request actions
                    null, // ipAddress - not available in job context
                    null, // userAgent - not available in job context
                    priceOverride.getId(),
                    "PRICE_OVERRIDE",
                    "Price Override activated: " + overrideName
                );
                log.info("✓ Audit trail created for price override activation");
            } else {
                log.warn("Could not create audit trail: no user found for price override {}", priceOverrideId);
            }
            
            // Send websocket notification
            sendActivationWebSocketNotification(priceOverride, restaurantId, menuId);
        } catch (Exception e) {
            log.error("Failed to create audit trail for price override activation: {}", e.getMessage());
            // Don't break activation flow if audit trail fails
        }
    }

    /**
     * Sends a WebSocket notification for the price override activation.
     */
    private void sendActivationWebSocketNotification(PriceOverride priceOverride, UUID restaurantId, UUID menuId) {
        try {
            MessageUtil messageUtil = applicationContext.getBean(MessageUtil.class);
            SimpMessagingTemplate messagingTemplate = applicationContext.getBean(SimpMessagingTemplate.class);
            OrderNotificationService orderNotificationService = applicationContext.getBean(OrderNotificationService.class);
            
            // Use default locale (en) for job context
            Locale userLocale = Locale.forLanguageTag("en");
            
            String topic = "/topic/item-price";
            Map<String, Object> priceOverrideData = new HashMap<>();
            priceOverrideData.put("priceOverrideId", priceOverride.getId().toString());
            priceOverrideData.put("restaurantId", restaurantId != null ? restaurantId.toString() : null);
            priceOverrideData.put("menuId", menuId != null ? menuId.toString() : null);
            priceOverrideData.put("overrideLevel", priceOverride.getOverrideLevel() != null ? priceOverride.getOverrideLevel().toString() : null);
            priceOverrideData.put("overrideType", priceOverride.getOverrideType() != null ? priceOverride.getOverrideType().toString() : null);
            priceOverrideData.put("overrideValue", priceOverride.getOverrideValue());
            priceOverrideData.put("status", priceOverride.getStatus() != null ? priceOverride.getStatus().toString() : null);
            priceOverrideData.put("action", "ACTIVATE");
            priceOverrideData.put("notificationType", "PRICE_OVERRIDE_ACTIVATE");
            priceOverrideData.put("timestamp", LocalDateTime.now(ZoneOffset.UTC).format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
            
            StatusEventMessage eventMessage = StatusEventMessage.builder()
                    .message(messageUtil.getMessage("price.override.schedule.update.success", userLocale))
                    .notificationType("PRICE_OVERRIDE_ACTIVATE")
                    .data(priceOverrideData)
                    .build();
            
            // Send directly to WebSocket clients
            if (messagingTemplate != null) {
                messagingTemplate.convertAndSend(topic, eventMessage);
                log.info("[Notification][WebSocket] broadcast topic={} notificationType=PRICE_OVERRIDE_ACTIVATE priceOverrideId={}, restaurantId={}",
                        topic, priceOverride.getId(), restaurantId);
            }
            
            // Also publish to RabbitMQ for integration service to log
            if (orderNotificationService != null) {
                orderNotificationService.publishToRabbitMQ(topic, eventMessage);
                log.info("[Notification][FCM] rabbitPublish payloadWsTopic={} notificationType=PRICE_OVERRIDE_ACTIVATE priceOverrideId={}",
                        topic, priceOverride.getId());
            }
        } catch (Exception e) {
            log.error("Failed to send WebSocket notification for price override activation: {}", e.getMessage(), e);
            // Don't break activation flow if websocket notification fails
        }
    }
}
