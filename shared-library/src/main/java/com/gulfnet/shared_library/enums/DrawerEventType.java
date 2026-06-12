package com.gulfnet.shared_library.enums;

public enum DrawerEventType {
    OPENING_BALANCE,    // Starting cash when shift begins
    SALE_INFLOW,        // Cash received from customer sale
    SALE_REFUND,        // Cash returned to customer for refund
    MANUAL_DEPOSIT,     // Cash added (cashier enters)
    MANUAL_WITHDRAWAL,  // Cash removed (cashier enters)
    CLOSING_BALANCE,    // Ending cash count when shift closes
    ADJUSTMENT_APPROVED,  // Adjustment request approved
    ADJUSTMENT_PENDING,  // Adjustment request pending approval
    ADJUSTMENT_REJECTED  // Adjustment request rejected
}

