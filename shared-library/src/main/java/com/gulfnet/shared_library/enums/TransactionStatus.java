package com.gulfnet.shared_library.enums;

import com.fasterxml.jackson.annotation.JsonAlias;

public enum TransactionStatus {
    OPEN, PENDING, COMPLETED, REFUNDED, PARTIALLY_REFUNDED,

    /** Serialized as {@code CANCELED}; {@code CANCELLED} is accepted on deserialize for client compatibility. */
    @JsonAlias("CANCELLED")
    CANCELED
}
