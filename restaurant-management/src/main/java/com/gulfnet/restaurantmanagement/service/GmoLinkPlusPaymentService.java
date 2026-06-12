package com.gulfnet.restaurantmanagement.service;

import java.math.BigDecimal;
import java.util.Locale;

/**
 * Creates GMO LinkType Plus hosted checkout URLs.
 */
public interface GmoLinkPlusPaymentService {

    boolean isConfigured();

    /**
     * Calls {@code GetLinkplusUrlPayment.json} and returns the checkout {@code LinkUrl}.
     *
     * @param gmoOrderId   {@code transaction.OrderID} (max 27 chars, from {@link com.gulfnet.shared_library.entity.Order#getGmoLinkOrderId()})
     * @param amount       payment amount in major currency units (e.g. JPY); sent as integer to GMO
     * @param tax          tax portion (may be zero)
     * @param retUrl       return URL (LinkType Plus naming)
     * @param completeUrl  completion return URL
     * @param cancelUrl    cancel return URL
     * @param resultSkipFlag {@code "0"} or {@code "1"}; when null, defaults to {@code "1"}
     * @param displayLocale  locale for GMO hosted checkout UI; mapped to {@code displaysetting.Lang}
     *                       ({@code ja}, {@code en}, {@code zh} per LinkType Plus docs). When null, {@code ja} is used.
     * @return HTTPS checkout URL for the customer
     */
    String createHostedCheckoutUrl(String gmoOrderId,
                                   BigDecimal amount,
                                   BigDecimal tax,
                                   String retUrl,
                                   String completeUrl,
                                   String cancelUrl,
                                   String resultSkipFlag,
                                   Locale displayLocale);
}
