package com.gulfnet.restaurantmanagement.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Placeholder {@code transaction.cashier_id} for customer-hosted card payments.
 * {@code payment_initiator_type=1} distinguishes customer self-pay from cashier-initiated ({@code 0}).
 */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "payment.online-card")
public class OnlineCardPaymentProperties {

    /** UUID written to {@code transaction.cashier_id}; no {@code users} row required. */
    private String userId;

    /** Display label for logs/documentation only. */
    private String userName;

    public boolean isConfigured() {
        return userId != null && !userId.isBlank();
    }
}
