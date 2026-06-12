package com.gulfnet.restaurantmanagement.listener;

import com.gulfnet.restaurantmanagement.service.EmailScheduleService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.UUID;

/**
 * When a user is soft-deleted in user-management, removes all scheduled email reports they created
 * (DB + Quartz), including restaurant-group-level HQ schedules.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class UserDeletedEmailScheduleListener {

    private static final String FIELD_USER_ID = "userId";

    private final EmailScheduleService emailScheduleService;

    @RabbitListener(queues = "user.deleted.email.schedules.queue")
    public void handleUserDeleted(Map<String, Object> message) {
        try {
            log.info("Received user deleted email-schedule cleanup message: {}", message);
            String userIdStr = message.get(FIELD_USER_ID) != null ? message.get(FIELD_USER_ID).toString() : null;
            if (userIdStr == null) {
                log.warn("Invalid message: missing userId. Message: {}", message);
                return;
            }
            UUID userId = UUID.fromString(userIdStr);
            emailScheduleService.deleteSchedulesForUser(userId);
        } catch (Exception e) {
            log.error("Failed to process user deleted email-schedule cleanup: {}", e.getMessage(), e);
            throw e;
        }
    }
}
