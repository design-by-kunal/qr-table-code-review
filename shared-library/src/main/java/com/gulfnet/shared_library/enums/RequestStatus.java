package com.gulfnet.shared_library.enums;

public enum RequestStatus {
    NONE,
    OPEN,
    APPROVED,
    DECLINED,
    NA  // Not Applicable - for non-request type actions (LOGIN, LOGOUT, PAYMENT, etc.)
}
