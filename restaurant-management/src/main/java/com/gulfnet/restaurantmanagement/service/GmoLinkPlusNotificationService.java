package com.gulfnet.restaurantmanagement.service;

import java.util.Map;

/**
 * Handles GMO PG <strong>result notification</strong> (server-to-server) for LinkType Plus card payments.
 * <p>
 * Response contract: plain body {@code "0"} when processed (GMO should not retry), {@code "1"} to request retry.
 */
public interface GmoLinkPlusNotificationService {

    /**
     * @param form GMO POST body as {@code application/x-www-form-urlencoded} parameters (case-insensitive keys)
     * @return {@code "0"} or {@code "1"}
     */
    String processResultNotification(Map<String, String> form);
}
