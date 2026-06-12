package com.gulfnet.shared_library.enums;

/**
 * Type of refund being processed.
 *
 * FULL    - Entire transaction/order is refunded.
 * PARTIAL - Only specific items/quantities from the transaction are refunded.
 */
public enum RefundType {
    FULL,
    PARTIAL
}


