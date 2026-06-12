package com.gulfnet.shared_library.model.response.dto;

import com.gulfnet.shared_library.enums.AlcoholType;

import java.math.BigDecimal;

/**
 * Represents an effective (scaled) item amount inside a combo for tax breakdown purposes.
 * Amount is already multiplied by combo quantity (i.e., total amount contributed by this combo item).
 */
public class ComboTaxItem {
    private final BigDecimal amount;
    private final AlcoholType alcoholType;

    public ComboTaxItem(BigDecimal amount, AlcoholType alcoholType) {
        this.amount = amount;
        this.alcoholType = alcoholType;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public AlcoholType getAlcoholType() {
        return alcoholType;
    }
}

