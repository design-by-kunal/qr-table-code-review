package com.gulfnet.restaurantmanagement.config;

import org.springframework.amqp.core.*;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * RabbitMQ Configuration for Restaurant Management Service.
 * <p>
 * Configures exchanges, queues and bindings for WebSocket-related messages.
 * Common RabbitMQ infrastructure beans (message converter, {@link org.springframework.amqp.rabbit.core.RabbitTemplate})
 * are provided by {@code com.gulfnet.shared_library.config.RabbitMQCommonConfig}.
 */
@Configuration
public class RabbitMQConfig {

    public static final String WEBSOCKET_TOPIC_EXCHANGE = "websocket.topic.exchange";
    public static final String WEBSOCKET_ROUTING_KEY = "websocket.topic.messages";

    /**
     * Set on RabbitMQ payloads when restaurant-management already broadcast the event via STOMP.
     * Prevents {@link com.gulfnet.restaurantmanagement.listener.WebSocketMessageForwarder} from
     * re-sending the same message to WebSocket clients (duplicate toasts for cashiers).
     */
    public static final String WEBSOCKET_MSG_SUPPRESS_LOCAL_FORWARD = "suppressLocalForward";

    /**
     * Create topic exchange for WebSocket messages.
     */
    @Bean
    public TopicExchange websocketTopicExchange() {
        return new TopicExchange(WEBSOCKET_TOPIC_EXCHANGE);
    }

    /**
     * Unified queue for all request decision messages from user-management
     * Supports: discount, cancellation, refund, profile update requests
     */
    @Bean
    public Queue requestDecisionQueue() {
        return QueueBuilder.durable("request.decision.queue").build();
    }

    /**
     * Binding for unified request decision queue
     */
    @Bean
    public Binding requestDecisionBinding() {
        return BindingBuilder
                .bind(requestDecisionQueue())
                .to(websocketTopicExchange())
                .with("request.decision.*");
    }

    /**
     * Legacy queue for discount request decision messages (for backward compatibility)
     * @deprecated Use requestDecisionQueue instead
     */
    @Deprecated
    @SuppressWarnings("java:S1133") // Kept for backward compatibility during migration
    @Bean
    public Queue discountRequestDecisionQueue() {
        return QueueBuilder.durable("discount.request.decision.queue").build();
    }

    /**
     * Binding for legacy discount request decision queue (for backward compatibility)
     * @deprecated Use requestDecisionBinding instead
     */
    @Deprecated
    @SuppressWarnings("java:S1133") // Kept for backward compatibility during migration
    @Bean
    public Binding discountRequestDecisionBinding() {
        return BindingBuilder
                .bind(discountRequestDecisionQueue())
                .to(websocketTopicExchange())
                .with("discount.request.decision");
    }

    /**
     * Queue for consuming WebSocket messages from user-management
     * This allows restaurant-management to forward messages to WebSocket clients
     * Uses separate queue from integration-management so both services receive all messages
     */
    @Bean
    public Queue websocketTopicMessagesQueue() {
        return QueueBuilder.durable("websocket.topic.messages.restaurant").build();
    }

    /**
     * Binding for WebSocket topic messages queue
     * Consumes messages published by user-management to websocket.topic.exchange
     * Uses routing key pattern to match all websocket.topic.* messages
     */
    @Bean
    public Binding websocketTopicMessagesBinding() {
        return BindingBuilder
                .bind(websocketTopicMessagesQueue())
                .to(websocketTopicExchange())
                .with("websocket.topic.*");
    }

    /**
     * Queue for profile updated directly notifications from user-management
     * This allows restaurant-management to receive and process profile update notifications
     */
    @Bean
    public Queue profileUpdatedDirectlyQueue() {
        return QueueBuilder.durable("profile.updated.directly.queue").build();
    }

    /**
     * Binding for profile updated directly queue
     * Consumes messages published by user-management to websocket.topic.exchange
     * with routing key "profile.updated.directly"
     */
    @Bean
    public Binding profileUpdatedDirectlyBinding() {
        return BindingBuilder
                .bind(profileUpdatedDirectlyQueue())
                .to(websocketTopicExchange())
                .with("profile.updated.directly");
    }

    /**
     * Queue for employee assigned to restaurant notifications from user-management
     * This allows restaurant-management to notify managers when HQ Admin assigns
     * a new employee directly to a restaurant during registration.
     */
    @Bean
    public Queue employeeAssignedToRestaurantQueue() {
        return QueueBuilder.durable("employee.assigned.to.restaurant.queue").build();
    }

    /**
     * Binding for employee assigned to restaurant queue
     * Consumes messages published by user-management to websocket.topic.exchange
     * with routing key "employee.assigned.to.restaurant"
     */
    @Bean
    public Binding employeeAssignedToRestaurantBinding() {
        return BindingBuilder
                .bind(employeeAssignedToRestaurantQueue())
                .to(websocketTopicExchange())
                .with("employee.assigned.to.restaurant");
    }

    /**
     * Queue for list refresh events from integration service
     * This allows restaurant-management to receive list refresh triggers after FCM notifications are sent
     */
    @Bean
    public Queue listRefreshQueue() {
        return QueueBuilder.durable("websocket.list.refresh.queue").build();
    }

    /**
     * Binding for list refresh queue
     * Consumes messages published by integration-management to websocket.topic.exchange
     * with routing key "websocket.list.refresh"
     */
    @Bean
    public Binding listRefreshBinding() {
        return BindingBuilder
                .bind(listRefreshQueue())
                .to(websocketTopicExchange())
                .with("websocket.list.refresh");
    }

    /**
     * When a user leaves a restaurant (reassignment or unassign), user-management publishes here so we can
     * remove their scheduled report jobs for that restaurant and delete Quartz triggers.
     */
    @Bean
    public Queue userLeftRestaurantEmailSchedulesQueue() {
        return QueueBuilder.durable("user.left.restaurant.email.schedules.queue").build();
    }

    @Bean
    public Binding userLeftRestaurantEmailSchedulesBinding() {
        return BindingBuilder
                .bind(userLeftRestaurantEmailSchedulesQueue())
                .to(websocketTopicExchange())
                .with("user.left.restaurant.email.schedules");
    }

    /**
     * When a user is soft-deleted, user-management publishes here so we remove all their scheduled report jobs.
     */
    @Bean
    public Queue userDeletedEmailSchedulesQueue() {
        return QueueBuilder.durable("user.deleted.email.schedules.queue").build();
    }

    @Bean
    public Binding userDeletedEmailSchedulesBinding() {
        return BindingBuilder
                .bind(userDeletedEmailSchedulesQueue())
                .to(websocketTopicExchange())
                .with("user.deleted.email.schedules");
    }
}

