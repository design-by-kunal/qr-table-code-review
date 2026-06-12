package com.gulfnet.shared_library.enums;

import com.fasterxml.jackson.annotation.JsonCreator;

public enum AppType {
    HQADMIN,
    CASHIER,
    WAITER,
    MANAGER,
    KDS;

    /**
     * Custom factory to allow more flexible JSON values, e.g. "HQ_ADMIN" or "hqadmin".
     * We normalize by removing underscores and uppercasing before matching enum names.
     */
    @JsonCreator
    public static AppType fromString(String value) {
        if (value == null) {
            return null;
        }

        String normalized = value.replace("_", "").toUpperCase();
        for (AppType type : AppType.values()) {
            if (type.name().equals(normalized)) {
                return type;
            }
        }

        throw new IllegalArgumentException("Invalid AppType: " + value);
    }
}


