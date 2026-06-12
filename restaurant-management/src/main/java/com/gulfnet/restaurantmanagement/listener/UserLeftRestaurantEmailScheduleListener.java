package com.gulfnet.restaurantmanagement.listener;

import com.gulfnet.restaurantmanagement.service.EmailScheduleService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.UUID;

/**
 * When a user is reassigned or unassigned from a restaurant in user-management, removes any
 * scheduled email reports they created for that restaurant (DB + Quartz).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class UserLeftRestaurantEmailScheduleListener {

    private static final String FIELD_USER_ID = "userId";
    private static final String FIELD_OLD_RESTAURANT_ID = "oldRestaurantId";

    private final EmailScheduleService emailScheduleService;

    @RabbitListener(queues = "user.left.restaurant.email.schedules.queue")
    public void handleUserLeftRestaurant(Map<String, Object> message) {
        try {
            log.info("Received user left restaurant email-schedule cleanup message: {}", message);
            String userIdStr = message.get(FIELD_USER_ID) != null ? message.get(FIELD_USER_ID).toString() : null;
            String oldRestaurantIdStr = message.get(FIELD_OLD_RESTAURANT_ID) != null
                    ? message.get(FIELD_OLD_RESTAURANT_ID).toString() : null;
            if (userIdStr == null || oldRestaurantIdStr == null) {
                log.warn("Invalid message: missing userId or oldRestaurantId. Message: {}", message);
                return;
            }
            UUID userId = UUID.fromString(userIdStr);
            UUID oldRestaurantId = UUID.fromString(oldRestaurantIdStr);
            emailScheduleService.deleteSchedulesForUserAndRestaurant(userId, oldRestaurantId);
        } catch (Exception e) {
            log.error("Failed to process user left restaurant email-schedule cleanup: {}", e.getMessage(), e);
            // Let container handle retry/DLQ; do not ack a failed cleanup silently.
            throw e;
        }
    }
}
