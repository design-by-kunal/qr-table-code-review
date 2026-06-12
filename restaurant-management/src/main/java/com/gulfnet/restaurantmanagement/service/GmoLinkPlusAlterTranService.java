package com.gulfnet.restaurantmanagement.service;

import com.gulfnet.shared_library.entity.Transaction;

import java.math.BigDecimal;
import java.util.Locale;

/**
 * GMO PG protocol {@code AlterTran.idPass} for LinkType Plus card returns.
 */
public interface GmoLinkPlusAlterTranService {

    /**
     * Submits {@code JobCd=RETURN} for the trade. Throws if GMO returns {@code ErrCode}.
     * Success/failure for bookkeeping is confirmed via GMO result notification ({@code Status=RETURN}).
     */
    void submitCardReturn(Transaction transaction, BigDecimal amount, Locale locale);
}
