package com.gulfnet.usermanagement.config;

import org.springframework.amqp.core.TopicExchange;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * RabbitMQ Configuration for User Management Service
 * Configures exchanges to publish notification messages to integration service.
 * <p>
 * Common RabbitMQ infrastructure beans (message converter, {@link org.springframework.amqp.rabbit.core.RabbitTemplate})
 * are provided by {@code com.gulfnet.shared_library.config.RabbitMQCommonConfig}.
 */
@Configuration
public class RabbitMQConfig {

    public static final String NOTIFICATION_TOPIC_EXCHANGE = "websocket.topic.exchange";
    public static final String NOTIFICATION_ROUTING_KEY = "websocket.topic.messages";

    /**
     * Published when a user's restaurant assignment changes away from a restaurant; restaurant-management
     * removes that user's scheduled email reports for the previous restaurant.
     */
    public static final String USER_LEFT_RESTAURANT_EMAIL_SCHEDULES_ROUTING_KEY = "user.left.restaurant.email.schedules";

    /**
     * Published when a user account is soft-deleted; restaurant-management removes all scheduled email
     * reports created by that user (restaurant, restaurant group, or HQ-wide).
     */
    public static final String USER_DELETED_EMAIL_SCHEDULES_ROUTING_KEY = "user.deleted.email.schedules";

    /**
     * Set on payloads when this service already broadcast via {@code SimpMessagingTemplate};
     * restaurant-management's {@code WebSocketMessageForwarder} skips re-send to avoid duplicate toasts.
     */
    public static final String WEBSOCKET_MSG_SUPPRESS_LOCAL_FORWARD = "suppressLocalForward";

    /**
     * Create topic exchange for notification messages.
     */
    @Bean
    public TopicExchange notificationTopicExchange() {
        return new TopicExchange(NOTIFICATION_TOPIC_EXCHANGE);
    }
}

