package com.gulfnet.integrationmanagement.config;

import org.springframework.amqp.core.*;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * RabbitMQ Configuration for Integration Management Service.
 * <p>
 * Configures queues and exchanges to receive WebSocket messages.
 * Common RabbitMQ infrastructure beans (message converter, {@link org.springframework.amqp.rabbit.core.RabbitTemplate})
 * are provided by {@code com.gulfnet.shared_library.config.RabbitMQCommonConfig}.
 */
@Configuration
public class RabbitMQConfig {

    public static final String WEBSOCKET_TOPIC_EXCHANGE = "websocket.topic.exchange";
    public static final String WEBSOCKET_QUEUE = "websocket.topic.messages";
    public static final String WEBSOCKET_ROUTING_KEY = "websocket.topic.*";
    public static final String LIST_REFRESH_ROUTING_KEY = "websocket.list.refresh";

    /**
     * Create topic exchange for WebSocket messages.
     */
    @Bean
    public TopicExchange websocketTopicExchange() {
        return new TopicExchange(WEBSOCKET_TOPIC_EXCHANGE);
    }

    /**
     * Create queue for WebSocket messages.
     */
    @Bean
    public Queue websocketQueue() {
        return QueueBuilder.durable(WEBSOCKET_QUEUE).build();
    }

    /**
     * Bind queue to exchange with routing key.
     */
    @Bean
    public Binding websocketBinding() {
        return BindingBuilder
                .bind(websocketQueue())
                .to(websocketTopicExchange())
                .with(WEBSOCKET_ROUTING_KEY);
    }
}

