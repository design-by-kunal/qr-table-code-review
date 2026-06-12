package com.gulfnet.restaurantmanagement.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * GMO PG LinkType Plus JSON API ({@code GetLinkplusUrlPayment.json}) for hosted credit card checkout.
 */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "gmo.link-plus")
public class GmoLinkPlusProperties {

    /** Bound from {@code gmo.link-plus.payment-url} (env {@code GMO_LINK_PLUS_PAYMENT_URL}); no default in code. */
    private String paymentUrl;

    /** Bound from {@code gmo.link-plus.shop-id} (env {@code GMO_LINK_PLUS_SHOP_ID}). */
    private String shopId;

    /** Bound from {@code gmo.link-plus.shop-pass} (env {@code GMO_LINK_PLUS_SHOP_PASS}). */
    private String shopPass;

    /** Bound from {@code gmo.link-plus.config-id} (env {@code GMO_LINK_PLUS_CONFIG_ID}). */
    private String configId;

    /** Bound from {@code gmo.link-plus.alter-tran-url} (env {@code GMO_LINK_PLUS_ALTER_TRAN_URL}). */
    private String alterTranUrl;

    /**
     * Minutes until GMO rejects opening the hosted checkout ({@code transaction.PaymentExpireDate}, Japan time).
     * <p>
     * Bound from {@code gmo.link-plus.payment-expires-minutes} (env {@code GMO_LINK_PLUS_PAYMENT_EXPIRES_MINUTES} via
     * {@code application.properties}). {@code 0} = do not send PaymentExpireDate (shop/GMO defaults apply).
     * </p>
     */
    private int paymentExpiresMinutes;

    public boolean isConfigured() {
        return hasText(paymentUrl) && hasText(shopId) && hasText(shopPass) && hasText(configId);
    }

    /** Shop credentials + AlterTran URL (explicit or derived from payment URL). */
    public boolean isAlterTranConfigured() {
        return hasText(shopId) && hasText(shopPass) && hasText(resolveAlterTranUrl());
    }

    public String resolveAlterTranUrl() {
        if (hasText(alterTranUrl)) {
            return alterTranUrl.trim();
        }
        String payment = paymentUrl != null ? paymentUrl.trim() : "";
        if (!payment.isEmpty()) {
            int paymentSegment = payment.indexOf("/payment/");
            if (paymentSegment >= 0) {
                return payment.substring(0, paymentSegment + "/payment/".length()) + "AlterTran.idPass";
            }
            int lastSlash = payment.lastIndexOf('/');
            if (lastSlash >= 0) {
                return payment.substring(0, lastSlash + 1) + "AlterTran.idPass";
            }
        }
        return "";
    }

    private static boolean hasText(String s) {
        return s != null && !s.isBlank();
    }
}
