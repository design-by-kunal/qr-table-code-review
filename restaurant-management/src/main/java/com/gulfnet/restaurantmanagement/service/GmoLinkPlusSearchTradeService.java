package com.gulfnet.restaurantmanagement.service;

import java.util.Optional;

/**
 * GMO PG protocol API {@code SearchTrade.idPass} for LinkType Plus card trades.
 */
public interface GmoLinkPlusSearchTradeService {

    /**
     * Looks up trade handles by GMO {@code OrderID} (Link Plus order id).
     */
    Optional<GmoLinkPlusTradeLookup> searchTradeByOrderId(String gmoOrderId);

    record GmoLinkPlusTradeLookup(String accessId, String accessPass, String status, String gmoOrderId) {}
}
